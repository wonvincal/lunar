// EI4CommandClient.cpp : Defines the entry point for the console application.
//

#include <signal.h>
#include <string>
#include <iostream>
#include <map>
#include <unistd.h>
#include <atomic>
#include <pthread.h>

#include "EI6/EndRecovery.h"
#include "EI6/const_def.h"
#include "EI6/ExchangePlatform.h"
#include "EI6/Exchange.h"
#include "EI6/ProductClass.h"
#include "EI6/Product.h"
#include "EI6/TradingPhase.h"
#include "EI6/Market.h"
#include "EI6/MarketDepth.h"
#include "EI6/ExecutionReport.h"
#include "EI6/CreateStrategyReport.h"
#include "EI6/OrderCancelReject.h"
#include "EI6/EnterQuoteResponse.h"
#include "EI6/CancelQuoteResponse.h"
#include "EI6/QuoteSideReport.h"
#include "EI6/SessionStateEvent.h"
#include "EI6/NewOrderSingle.h"
#include "EI6/OrderCancelRequest.h"

#include <dlfcn.h>
#include <stdio.h> 
#include <string.h> 
#include <ctype.h> 
#include <assert.h> 
#include <functional>

#include "com_lunar_marketdata_hkex_CtpMduApi.h"
#include "com_lunar_message_io_sbe/MessageHeader.hpp"
#include "com_lunar_message_io_sbe/OrderBookSnapshotSbe.hpp"
#include "com_lunar_message_io_sbe/MarketDataTradeSbe.hpp"
#include "com_lunar_message_io_sbe/MarketStatsSbe.hpp"

#include <time.h>
#include <iomanip>

using namespace com_lunar_message_io_sbe;

// Global jni variables
JavaVM* g_vm;
JNIEnv* g_env;
jobject g_obj;
jclass g_objClass;
JNIEnv* g_rEnv = 0;

// Java callbacks
jmethodID g_onMarketDataCallback;
jmethodID g_onTradeCallback;
jmethodID g_onStatsCallback;
jmethodID g_onTradingPhaseCallback;
jmethodID g_onSessionStateCallback;

bool stop = false;


struct CommandLineParameter
{
	std::string lConfigFile;
	int lDebugLevel;
	std::string lLogFileDir;
	std::string lLogFileName;
	std::string lLogFileSize;
};


void SignalStop(int)
{
	std::cout << "[INFO] Stop event received" << std::endl;
	stop = true;
}

void setSignal()
{
	::signal(SIGINT, &SignalStop);
	::signal(SIGTERM, &SignalStop);

	::signal(SIGQUIT, &SignalStop);
	::signal(SIGKILL, &SignalStop);
}

typedef void (*received_cb)(void* ud, const void* buf, unsigned int size);
typedef void (*send_func)(void* connector, const void* buf, unsigned int size);
typedef void* (*load_func)(const char* config_file_name, received_cb received, void* ud);
typedef void (*unload_func)(void* connector);

void received(void* ud, const void* data, unsigned int size);
void process(char* data, unsigned int size);
void produce(const void* data, unsigned int size);
void* consume(void* arg);

struct BulkTrade {
    int64_t secSid;
    int totalTradeQty;
    int numTrades;
    int64_t lastDateTime;
    int64_t lastReceivedTime;
    int lastTriggerSeqNum;
    int lastTradePrice;

    inline void clear(){
        secSid = 0;
        totalTradeQty = 0;
        numTrades = 0;
        lastTriggerSeqNum = 0;
        lastTradePrice = 0;
    }

} g_bulkTrade;

const int BULK_PER_TRADE_LIMIT = 1000000;

void* g_dllHandle = 0;
send_func g_send;
load_func g_load ;
unload_func g_unload;
void* g_connector = 0;

const int MAX_FAST_SEC_SID = 100000;
std::map<std::string, int64_t> g_securities;
std::map<int64_t, int> g_seqNums;
int g_fastSeqNums[MAX_FAST_SEC_SID];
int64_t g_fastSecurities[MAX_FAST_SEC_SID];

char* g_bufferOrder;
char* g_bufferTrade;
char* g_bufferStats;
int g_bufferSize;
int g_bookDepth;
int g_senderSinkId;
int g_triggerSeqNum;

MessageHeader g_messageHeaderOrder;
MessageHeader g_messageHeaderTrade;
MessageHeader g_messageHeaderStats;

OrderBookSnapshotSbe g_orderBookSnapshotSbe;
TriggerInfo* g_orderBookTriggerInfo;
sbe_uint64_t g_orderBookPosition;

MarketDataTradeSbe g_marketDataTradeSbe;
TriggerInfo* g_tradeTriggerInfo;

MarketStatsSbe g_marketStatsSbe;
TriggerInfo* g_statsTriggerInfo;

bool g_isRecovery = true;

#define PACKET_SIZE 512
#define RING_BUFFER_LENGTH 16384
#define RING_BUFFER_SIZE 8388608
#define RING_BUFFER_MASK 8388607
char* g_ringBuffer = 0;
unsigned int g_ringBufferWriteCursor = 0;
unsigned int g_ringBufferReadCursor = 0;
std::atomic<unsigned int> g_ringBufferCount(0);
bool canConsume = true;
pthread_t consumerThread;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved)
{
    g_vm = jvm;

    for (int i = 0; i < MAX_FAST_SEC_SID; i++){
        g_fastSecurities[i] = -1;
        g_fastSeqNums[i] = 0;
    }

    return JNI_VERSION_1_8;
}

JNIEXPORT jint JNICALL Java_com_lunar_marketdata_hkex_CtpMduApi_initialize(JNIEnv* env, jobject obj, jstring connectorFile, jstring configFile, jint senderSinkId, jint bookDepth, jobject byteBufferOrder, jobject byteBufferTrade, jobject byteBufferStats, jint bufferSize) {
    g_env = env;
    g_obj = env->NewGlobalRef(obj);
    std::cout << "[INFO] Finding Java class com/lunar/marketdata/hkex/CtpMduApi..." << std::endl;
    g_objClass = env->FindClass("com/lunar/marketdata/hkex/CtpMduApi");
    if (!g_objClass) {
        std::cerr << "[ERROR] Java class CtpMduApi not found..." << std::endl;
        return -1;
    }
    std::cout << "[INFO] Finding Java method onMarketData..." << std::endl;
    g_onMarketDataCallback = env->GetMethodID(g_objClass, "onMarketData", "(JI)I");
    if (!g_onMarketDataCallback) {
        std::cerr << "[ERROR] Java method onMarketData not found..." << std::endl;
        return -1;
    }
    std::cout << "[INFO] Finding Java method onTrade..." << std::endl;
    g_onTradeCallback = env->GetMethodID(g_objClass, "onTrade", "(JI)I");
    if (!g_onTradeCallback) {
        std::cerr << "[ERROR] Java method onTrade not found..." << std::endl;
        return -1;
    }
    std::cout << "[INFO] Finding Java method onMarketStats..." << std::endl;
    g_onStatsCallback = env->GetMethodID(g_objClass, "onMarketStats", "(JI)I");
    if (!g_onStatsCallback) {
        std::cerr << "[ERROR] Java method onMarketStats not found..." << std::endl;
        return -1;
    }
    std::cout << "[INFO] Finding Java method onTradingPhase..." << std::endl;
    g_onTradingPhaseCallback = env->GetMethodID(g_objClass, "onTradingPhase", "(I)I");
    if (!g_onTradingPhaseCallback) {
        std::cerr << "[ERROR] Java method onTradingPhase not found..." << std::endl;
        return -1;
    }
    std::cout << "[INFO] Finding Java method onSessionState..." << std::endl;
    g_onSessionStateCallback = env->GetMethodID(g_objClass, "onSessionState", "(I)I");
    if (!g_onSessionStateCallback) {
        std::cerr << "[ERROR] Java method onSessionState not found..." << std::endl;
        return -1;
    }

    const char* cConnectorFile = env->GetStringUTFChars(connectorFile, 0);
    std::cout << "[INFO] Linking to dll " << cConnectorFile << "..." << std::endl;
    g_dllHandle = dlopen(cConnectorFile, RTLD_LAZY);
    env->ReleaseStringUTFChars(connectorFile, cConnectorFile);
    if (!g_dllHandle) {
        std::cerr << "[ERROR] Cannot initialize dll..." << std::endl;
        return -1;
    }

    std::cout << "[INFO] Linking to dll functions..." << std::endl;
    g_send = (send_func)dlsym(g_dllHandle, "ei6_send");
    g_load = (load_func)dlsym(g_dllHandle, "ei6_load");
    g_unload = (unload_func)dlsym(g_dllHandle, "ei6_unload");
    if (!g_send || !g_load || !g_unload) {
        std::cerr << "[ERROR] Cannot load dll functions..." << std::endl;
        dlclose(g_dllHandle);
        return -1;
    }

    // initialize ring buffer for processing market data
    g_ringBuffer = new char[RING_BUFFER_SIZE];
    g_ringBufferWriteCursor = 0;
    g_ringBufferReadCursor = 0;
    g_ringBufferCount.store(0);
    canConsume = true;
    pthread_create(&consumerThread, NULL, consume, NULL);

    g_isRecovery = true;
    void* userData = NULL;
    const char* cConfigFile = env->GetStringUTFChars(configFile, 0);
    std::cout << "[INFO] Initializing connector with config file " << cConfigFile << "..." << std::endl;
    g_connector = g_load(cConfigFile, received, userData); 
    env->ReleaseStringUTFChars(configFile, cConfigFile);

    if (!g_connector) {
        std::cerr << "[ERROR] Cannot load connector..." << std::endl;
        dlclose(g_dllHandle);
        return -1;
    }

    g_senderSinkId = senderSinkId;
    g_bookDepth = bookDepth;
    g_bufferOrder = (char*)env->GetDirectBufferAddress(byteBufferOrder);
    g_bufferTrade = (char*)env->GetDirectBufferAddress(byteBufferTrade);
    g_bufferStats = (char*)env->GetDirectBufferAddress(byteBufferStats);
    g_bufferSize = bufferSize;
    g_triggerSeqNum = 0;
    std::cout << "[INFO] Setting up for book depth " << g_bookDepth << "..." << std::endl;

    g_messageHeaderTrade.wrap(g_bufferTrade, 0, MarketDataTradeSbe::sbeSchemaVersion(), g_bufferSize).blockLength(MarketDataTradeSbe::sbeBlockLength()).templateId(MarketDataTradeSbe::sbeTemplateId())
        .schemaId(MarketDataTradeSbe::sbeSchemaId()).version(MarketDataTradeSbe::sbeSchemaVersion());
    g_marketDataTradeSbe.wrapForEncode(g_bufferTrade, g_messageHeaderTrade.size(), g_bufferSize);
    g_tradeTriggerInfo = &g_marketDataTradeSbe.triggerInfo();

    g_messageHeaderOrder.wrap(g_bufferOrder, 0, OrderBookSnapshotSbe::sbeSchemaVersion(), g_bufferSize).blockLength(OrderBookSnapshotSbe::sbeBlockLength()).templateId(OrderBookSnapshotSbe::sbeTemplateId())
        .schemaId(OrderBookSnapshotSbe::sbeSchemaId()).version(OrderBookSnapshotSbe::sbeSchemaVersion());
    g_orderBookPosition = g_orderBookSnapshotSbe.wrapForEncode(g_bufferOrder, g_messageHeaderOrder.size(), g_bufferSize).position();
    g_orderBookTriggerInfo = &g_orderBookSnapshotSbe.triggerInfo();

    g_messageHeaderStats.wrap(g_bufferStats, 0, MarketStatsSbe::sbeSchemaVersion(), g_bufferSize).blockLength(MarketStatsSbe::sbeBlockLength()).templateId(MarketStatsSbe::sbeTemplateId())
        .schemaId(MarketStatsSbe::sbeSchemaId()).version(MarketStatsSbe::sbeSchemaVersion());
    g_marketStatsSbe.wrapForEncode(g_bufferStats, g_messageHeaderStats.size(), g_bufferSize);
    g_statsTriggerInfo = &g_marketStatsSbe.triggerInfo();

    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_marketdata_hkex_CtpMduApi_registerSecurity(JNIEnv* env, jobject obj, jlong secSid, jstring productKey) {
    const char* cProductKey = env->GetStringUTFChars(productKey, 0);
    std::string pk(cProductKey);
    std::cout << "registering " << secSid << " to " << pk << std::endl;
    g_securities[pk] = secSid;
    g_seqNums[secSid] = 0;

    if (secSid >= 0 && secSid < MAX_FAST_SEC_SID){
        g_fastSecurities[(int)secSid] = secSid;
        g_fastSeqNums[(int)secSid] = 0;
    }
    env->ReleaseStringUTFChars(productKey, cProductKey);
    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_marketdata_hkex_CtpMduApi_close(JNIEnv* env, jobject obj) {
    if (g_connector) {
        g_unload(g_connector);
        g_connector = 0;
    }
    if (g_dllHandle) {
        dlclose(g_dllHandle);
        g_dllHandle = 0;
    }

    canConsume = false;
    pthread_join(consumerThread, NULL);
    delete [] g_ringBuffer;

    g_env->DeleteGlobalRef(g_obj);

    // leak here - should call DetachCurrentThread, but we don't have access to the "Current Thread" to call this...
    g_rEnv = 0;

    g_securities.clear();
    g_seqNums.clear();
    g_triggerSeqNum = 0;

    for (int i = 0; i < MAX_FAST_SEC_SID; i++){
        g_fastSecurities[i] = -1;
        g_fastSeqNums[i] = 0;
    }
    return 0;
}

inline int decimalToInt(const EI6::Decimal64& decimal) {
    // in most (all) cases that I see, exp is 3 which is exactly what we want
    return  decimal.GetExponent() == -3 ? decimal.GetMantissa() : decimal.GetMantissa() * pow(10, 3 + decimal.GetExponent());
    //static int pow10[4] = {1, 10, 100, 1000};
    //int exp = 3 + decimal.GetExponent();
    //return exp >= 0 ? decimal.GetMantissa() * pow10[exp] : decimal.GetMantissa() * pow(10, exp);
}

inline int strToPosInt(const char* str) {
    int result = 0;
    while (*str) {
        if (*str >= '0' && *str <= '9') {
            result = result * 10 + (*str - '0');
            str++;
        }
        else {
            return -1;
        }
    }
    return result;
}

void sendBulkTrade(int64_t secSid){
    g_marketDataTradeSbe
        .secSid(secSid)
        .tradeTime(g_bulkTrade.lastReceivedTime)
        .tradeType(TradeType::Value::AUTOMATCH_NORMAL)
        .price(g_bulkTrade.lastTradePrice)
        .quantity(g_bulkTrade.totalTradeQty)
        .numActualTrades(g_bulkTrade.numTrades)
        .isRecovery(BooleanType::FALSE);
    g_tradeTriggerInfo->triggeredBy(g_senderSinkId).triggerSeqNum(g_bulkTrade.lastTriggerSeqNum).nanoOfDay(g_bulkTrade.lastDateTime);
    g_messageHeaderTrade.payloadLength(g_marketDataTradeSbe.size());
    g_rEnv->CallObjectMethod(g_obj, g_onTradeCallback, secSid, g_messageHeaderTrade.size() + g_marketDataTradeSbe.size());
    g_bulkTrade.clear();
}

void process(char* data, unsigned int size) {
    //messages from ei6 connector
    typedef EI6::DataType::Type  msg_type; //ei6 head file: const_def
    msg_type type = *(msg_type const*)data;

    if(type == EI6::DataType::EnumMarket){
        int64_t dateTime = 0;

        // When both dateTime and productID are available
        // data[ DataType, 1 byte ][ BitMap, 3 bytes ][ DateTime (1 byte for type, 8 bytes of UInt64) ][ Product ID (1 byte for type, 1 byte for length, N bytes) ]
        // offset to first char of productID starts at 15
        if (data[1] & 3 == 3) {
            // disable market depth parsing for trades and stats
            if (data[3] & 2) {
                data[3] = data[3] & 0x3F;
            }

            // Extract ProductID from binary buffer directly
            bool fastLookup = true;
            int64_t secSid = -1;
            int rawSecSid = strToPosInt(data + 15);
            if (rawSecSid > 0 && rawSecSid < MAX_FAST_SEC_SID){
                // Check to see if this has been registered
                secSid = g_fastSecurities[rawSecSid];
                if (secSid < 0){
                    return;
                }
            }

            // Check to see if this has been registered
            EI6::Market msg(data, size, false);
            if (secSid < 0){
                fastLookup = false;
                std::map<std::string, int64_t>::iterator iSecSid = g_securities.find(msg.get_ProductID());
                if (iSecSid == g_securities.end()) {
                    return;
                }
                std::cout << "[ERROR] Using slow lookup for " << msg.get_ProductID() << std::endl;
                secSid = iSecSid->second;
            }

            struct timespec tv;
            clock_gettime(CLOCK_REALTIME, &tv);
    
            EI6::DateTime64 dateTime64 = msg.get_DateTime();
            dateTime = (int64_t)((dateTime64.GetHour() + 8) * 3600 + dateTime64.GetMinute() * 60 + dateTime64.GetSecond()) * 1000000000L + (int64_t)(dateTime64.GetMillisecond()) * 1000000L + (int64_t)(dateTime64.GetMicrosecond()) * 1000L + (int64_t)dateTime64.GetNanosecond();

            if (msg.has_ExchangePlatformLastTradeStatus()) {
                if (!g_isRecovery) {
                    char dataType = msg.get_ExchangePlatformLastTradeStatus()[0];
                    if (dataType == 'N') {
                        time_t t = tv.tv_sec;
                        struct tm* n = gmtime(&t);
                        int64_t receivedTime =  (int64_t)((n->tm_hour + 8) * 3600 + n->tm_min * 60 + n->tm_sec) * 1000000000L + (int64_t)tv.tv_nsec;
#ifdef _TRACELOG                            
                        std::cout << "[INFO] " << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." <<  std::setfill('0') << std::setw(9) << tv.tv_nsec;
                        std::cout << " Received trade for " <<  secSid << " " << msg.to_string() << std::endl;
#endif
                        TradeType::Value tradeType = TradeType::Value::AUTOMATCH_NORMAL;
                        if (msg.has_LastTradePrice()) {
                            if (msg.has_ExchangePlatformLastTradeType() && msg.get_ExchangePlatformLastTradeType()[0] == '0' || strcmp(msg.get_ExchangePlatformLastTradeType(), "100") == 0) {
                                int lastTradePrice = decimalToInt(msg.get_LastTradePrice());
                                if (secSid != g_bulkTrade.secSid){
                                    if (g_bulkTrade.totalTradeQty != 0){
                                        //std::cout << "[ERROR] Possibly market depth is missing for " << g_bulkTrade.secSid << std::endl;
                                        sendBulkTrade(g_bulkTrade.secSid);
                                    }
                                }
                                else if (g_bulkTrade.lastTradePrice != lastTradePrice){
                                    // Send previous bulked trade
                                    sendBulkTrade(secSid);
                                }

                                // Don't bulk trade if quantity >= BULK_PER_TRADE_LIMIT, send it right away
                                if (msg.get_LastTradeQuantity() >= BULK_PER_TRADE_LIMIT){
                                    // Send previous bulked trade
                                    if (g_bulkTrade.totalTradeQty != 0){
                                        sendBulkTrade(secSid);
                                    }

                                    // Send this trade
                                    g_marketDataTradeSbe
                                        .secSid(secSid)
                                        .tradeTime(receivedTime)
                                        .tradeType(TradeType::Value::AUTOMATCH_NORMAL)
                                        .price(lastTradePrice)
                                        .quantity(msg.get_LastTradeQuantity())
                                        .numActualTrades(1)
                                        .isRecovery(BooleanType::FALSE);
                                    g_tradeTriggerInfo->triggeredBy(g_senderSinkId).triggerSeqNum(g_triggerSeqNum++).nanoOfDay(dateTime);
                                    g_messageHeaderTrade.payloadLength(g_marketDataTradeSbe.size());
                                    g_rEnv->CallObjectMethod(g_obj, g_onTradeCallback, secSid, g_messageHeaderTrade.size() + g_marketDataTradeSbe.size());

                                }
                                else {
                                    // Store trade info
                                    g_bulkTrade.secSid = secSid;
                                    g_bulkTrade.totalTradeQty += msg.get_LastTradeQuantity();
                                    g_bulkTrade.numTrades++;
                                    g_bulkTrade.lastDateTime = dateTime;
                                    g_bulkTrade.lastTriggerSeqNum = g_triggerSeqNum++;
                                    g_bulkTrade.lastTradePrice = lastTradePrice;
                                    g_bulkTrade.lastReceivedTime = receivedTime;
                                }
                            }
                        }
                    }
                    else if (dataType == 'S') {
                        time_t t = tv.tv_sec;
                        struct tm* n = gmtime(&t);
                        int64_t receivedTime =  (int64_t)((n->tm_hour + 8) * 3600 + n->tm_min * 60 + n->tm_sec) * 1000000000L + (int64_t)tv.tv_nsec;
#ifdef _TRACELOG
                        std::cout << "[INFO] " << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." <<  std::setfill('0') << std::setw(9) << tv.tv_nsec;
                        std::cout << " Received stats for " <<  secSid << " " << msg.to_string() << std::endl;
#endif
                        int volume = msg.has_TotalTradeQuantity() ? msg.get_TotalTradeQuantity() : 0; // use volume as turnover as well for now
                        g_marketStatsSbe.secSid(secSid).transactTime(receivedTime).
                            open(msg.has_OpeningTradePrice () ? decimalToInt(msg.get_OpeningTradePrice()) : 0).high(msg.has_HighTradePrice() ? decimalToInt(msg.get_HighTradePrice()) : 0).
                            low(msg.has_LowTradePrice() ? decimalToInt(msg.get_LowTradePrice()) : 0).close(msg.has_ClosingTradePrice() ? decimalToInt(msg.get_ClosingTradePrice()) : 0).
                            volume(volume).turnover(volume).isRecovery(g_isRecovery ? BooleanType::TRUE : BooleanType::FALSE);
                        g_statsTriggerInfo->triggeredBy(g_senderSinkId).triggerSeqNum(g_triggerSeqNum++).nanoOfDay(dateTime);
                        g_messageHeaderStats.payloadLength(g_marketStatsSbe.size());
                        g_rEnv->CallObjectMethod(g_obj, g_onStatsCallback, secSid, g_messageHeaderStats.size() + g_marketStatsSbe.size());
                    }
                }
            }
            else {
                // Send previous bulked trade
                if (g_bulkTrade.totalTradeQty != 0){
                    //if (g_bulkTrade.secSid != secSid){
                    //    std::cout << "[ERROR] Received market depth for " << secSid << " now.  Possibly market depth is missing for " << g_bulkTrade.secSid << std::endl;
                    //}
                    sendBulkTrade(g_bulkTrade.secSid);
                }

                time_t t = tv.tv_sec;
                struct tm* n = gmtime(&t);
                int64_t receivedTime =  (int64_t)((n->tm_hour + 8) * 3600 + n->tm_min * 60 + n->tm_sec) * 1000000000L + (int64_t)tv.tv_nsec;

                int seqNum;
                if (fastLookup){
                    seqNum = g_fastSeqNums[(int)secSid] + 1;
                    g_fastSeqNums[(int)secSid] = seqNum;
                }
                else {
                    std::cout << "[ERROR] Slow lookup at 2" << std::endl;
                    std::map<int64_t, int>::iterator iSeqNum = g_seqNums.find(secSid);
                    seqNum = iSeqNum->second + 1;
                    iSeqNum->second = seqNum;
                }

                g_orderBookSnapshotSbe.position(g_orderBookPosition);
                g_orderBookSnapshotSbe.secSid(secSid).transactTime(receivedTime).seqNum(seqNum).isRecovery(g_isRecovery ? BooleanType::TRUE : BooleanType::FALSE);
                g_orderBookTriggerInfo->triggeredBy(g_senderSinkId).triggerSeqNum(g_triggerSeqNum++).nanoOfDay(dateTime);
#ifdef _TRACELOG
                std::cout << "[INFO] " << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." <<  std::setfill('0') << std::setw(9) << tv.tv_nsec;
                std::cout << " Received market data for " <<  secSid << ":" << seqNum << " " << msg.to_string() << std::endl;
#endif
                if (msg.has_Depth()) {
                    int depthSize = msg.get_Depth().size();
                    OrderBookSnapshotSbe::AskDepth& askEncoder = g_orderBookSnapshotSbe.askDepthCount(depthSize);
                    std::vector<EI6::MarketDepth> depth = msg.get_Depth();
#ifdef _TRACELOG
                    std::cout << "Ask: ";
#endif
                    for (int idx = 0; idx < depthSize; idx++) {
                        EI6::MarketDepth& level =  depth[idx];
                        EI6::MarketDepthEntry const* askSideEntry = level.GetSellMarketDepthEntry();
                        if (askSideEntry->HasPrice() && askSideEntry->HasQuantity()) {
                            askEncoder.next().price(decimalToInt(askSideEntry->GetPrice())).quantity(askSideEntry->GetQuantity()).tickLevel(0);
#ifdef _TRACELOG
                            std::cout << " " << idx << "[" << askSideEntry->GetQuantity() << "@" << decimalToInt(askSideEntry->GetPrice()) << "]";
#endif
                        }
                        else {
                            askEncoder.next().price(0).quantity(0).tickLevel(0);
                        }
                    }
#ifdef _TRACELOG
                    std::cout << "Bid: ";
#endif
                    OrderBookSnapshotSbe::BidDepth& bidEncoder = g_orderBookSnapshotSbe.bidDepthCount(depthSize);
                    for (int idx = 0; idx < depthSize; idx++) {
                        EI6::MarketDepth& level =  depth[idx];
                        EI6::MarketDepthEntry const* bidSideEntry = level.GetBuyMarketDepthEntry();
                        if (bidSideEntry->HasPrice() && bidSideEntry->HasQuantity()) {
                            bidEncoder.next().price(decimalToInt(bidSideEntry->GetPrice())).quantity(bidSideEntry->GetQuantity()).tickLevel(0);
#ifdef _TRACELOG
                            std::cout << " " << idx << "[" << bidSideEntry->GetQuantity() << "@" << decimalToInt(bidSideEntry->GetPrice()) << "]";
#endif
                        }
                        else {
                            bidEncoder.next().price(0).quantity(0).tickLevel(0);
                        }
                    }
                }
#ifdef _TRACELOG                        
                std::cout << std::endl;
#endif                        
                g_messageHeaderOrder.payloadLength(g_orderBookSnapshotSbe.size());
                g_rEnv->CallObjectMethod(g_obj, g_onMarketDataCallback, secSid, g_messageHeaderOrder.size() + g_orderBookSnapshotSbe.size());
            }
        }
    }
    else if(type == EI6::DataType::EnumTradingPhase){
        EI6::TradingPhase msg(data, size);
        std::cout << "[INFO] Received trading phase for " <<  msg.to_string() << "..." << std::endl;
        if (msg.has_TradingPhaseCode()) {
            g_rEnv->CallObjectMethod(g_obj, g_onTradingPhaseCallback, msg.get_TradingPhaseCode());
        }
    }
    else if(type == EI6::DataType::EnumSessionStateEvent){
        EI6::SessionStateEvent msg(data, size);
        if (msg.has_SessionState()) {
            jint sessionState = msg.has_SessionState() ? msg.get_SessionState() : 0;
            std::cout << "[INFO] Received session state for " << sessionState << "..." << std::endl;
            g_rEnv->CallObjectMethod(g_obj, g_onSessionStateCallback, sessionState);
        }
    }
    else if(type == EI6::DataType::EnumEndRecovery){
        EI6::EndRecovery msg(data, size);
        std::cout << "[INFO] Received end recovery " << msg.to_string() << std::endl;
        g_isRecovery = false;
    }
    //else if(type == EI6::DataType::EnumExchangePlatform){
    //    EI6::ExchangePlatform msg(data, size);
    //}
    //else if(type == EI6::DataType::EnumExchange){
    //    EI6::Exchange msg(data, size);
    //}
    //else if(type == EI6::DataType::EnumProductClass){
    //    EI6::ProductClass msg(data, size);
    //}
    //else if(type == EI6::DataType::EnumProduct){
    //    EI6::Product msg(data, size);
    //}
    //else
    //{
    //assert(0);
    //}
}

void received(void* ud, const void* data, unsigned int size) {
    typedef EI6::DataType::Type  msg_type; //ei6 head file: const_def
    msg_type type = *(msg_type const*)data;

    if (type == EI6::DataType::EnumMarket) {
        produce(data, size);
    }
    else if(type == EI6::DataType::EnumTradingPhase) {
        produce(data, size);
    }
    else if(type == EI6::DataType::EnumSessionStateEvent) {
        produce(data, size);
    }
    else if(type == EI6::DataType::EnumEndRecovery) {
        produce(data, size);
    }
}

void produce(const void* data, unsigned int size) {
    while (g_ringBufferCount == RING_BUFFER_LENGTH) {
        // busy spin - i don't expect the code to reach here often
    }
    char* cursor = g_ringBuffer + g_ringBufferWriteCursor;
    memcpy(cursor, &size, sizeof(unsigned int));
    cursor+=sizeof(unsigned int);
    memcpy(cursor, data, size);
    g_ringBufferWriteCursor = (g_ringBufferWriteCursor + PACKET_SIZE) & RING_BUFFER_MASK;
    g_ringBufferCount++;
}

void* consume(void* arg) {
    struct timeval tv;
    unsigned int size = 0;

    if (g_rEnv == 0) {
        int getEnvStat = g_vm->GetEnv((void**)&g_rEnv, JNI_VERSION_1_8);
        if (getEnvStat == JNI_EDETACHED) {
            std::cout << "[INFO] Attachinng environment..." << std::endl;
            if (g_vm->AttachCurrentThread((void**)&g_rEnv, NULL) != 0) {
                std::cerr << "[ERROR] Cannot attach env to callback thread..." << std::endl;
                return 0;
            }
        }
        else if (getEnvStat != JNI_OK) {
            std::cerr << "[ERROR] Cannot get env for callback thread..." << std::endl;
            return 0;
        }
    }

    while (canConsume) {
        if (g_ringBufferCount > 0) {
            char* cursor = g_ringBuffer + g_ringBufferReadCursor;
            memcpy(&size, cursor, sizeof(unsigned int));
            cursor+=sizeof(unsigned int);
            process(cursor, size);
            g_ringBufferReadCursor = (g_ringBufferReadCursor + PACKET_SIZE) & RING_BUFFER_MASK;
            g_ringBufferCount--;
        }
    }
    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_marketdata_hkex_CtpMduApi_requestSnapshot(JNIEnv* env, jobject obj, jlong secSid) {
    return 0;
}

JNIEXPORT jlong JNICALL Java_com_lunar_marketdata_hkex_CtpMduApi_getNanoOfDay(JNIEnv *, jobject) {
    struct timespec tv;
    clock_gettime(CLOCK_REALTIME, &tv);
    time_t t = tv.tv_sec;
    struct tm* n = gmtime(&t);
    int64_t receivedTime =  (int64_t)(((n->tm_hour + 8) % 24) * 3600 + n->tm_min * 60 + n->tm_sec) * 1000000000L + (int64_t)tv.tv_nsec;
    return receivedTime;
}

