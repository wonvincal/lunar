// EI4CommandClient.cpp : Defines the entry point for the console application.
//

#include <signal.h>
#include <string>
#include <iostream>
#include <map>
#include <unistd.h>
#include <atomic>
#include <pthread.h>

#include "EI6/const_def.h"
#include "EI6/ExchangePlatform.h"
#include "EI6/Exchange.h"
#include "EI6/ProductClass.h"
#include "EI6/Product.h"
#include "EI6/TradingPhase.h"
#include "EI6/Market.h"
#include "EI6/SessionStateEvent.h"
#include "EI6/EndRecovery.h"
#ifdef EMBEDDED_MDU
#include "EI6/packed_stream.h"
#endif

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

typedef void (*received_cb)(void* ud, const void* buf, unsigned int size);
typedef void (*send_func)(void* connector, const void* buf, unsigned int size);
typedef void* (*load_func)(const char* config_file_name, received_cb received, void* ud);
typedef void (*unload_func)(void* connector);

void received(void* ud, const void* data, unsigned int size);
void process(unsigned int sourceId, char* data, unsigned int size);
void produce(unsigned int sourceId, const void* data, unsigned int size);
void* consume(void* arg);

struct BulkTrade {
    uint64_t secSid;
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
void* g_connectors[2];

// Pure uint64_t structure...
// sid 1
// seq number: 1
// depth size: 5
// bid depth size + (bid price + bid quantity)[5] + ask depth size + (ask price + ask quantity)[5]
// 1 + 1 + 1 + 2 * 5 + 1 + 2 * 5 = 3 + 10 + 1 + 10 = 25
const int NUM_STRUCT_FIELDS = 25;
const int STRUCT_SID_INDEX = 0;
const int STRUCT_SEQNUM_INDEX = 1;
const int STRUCT_BIDDEPTHSIZE_INDEX = 2;
const int STRUCT_ASKDEPTHSIZE_INDEX = 13;

const int NUM_OMDC_SEC_SID = 100000;
const int HSI_FUTURES_INDEX = NUM_OMDC_SEC_SID;
const int HSCEI_FUTURES_INDEX = HSI_FUTURES_INDEX + 1;
const int NUM_FAST_SEC_SID = HSCEI_FUTURES_INDEX + 1;
uint64_t g_secInfoMap[NUM_FAST_SEC_SID][NUM_STRUCT_FIELDS];

uint64_t g_hsiFutId = 0;
uint64_t g_hsceiFutId = 0;

uint64_t g_hsiFutSecSid = 0;
uint64_t g_hsceiFutSecSid = 0;

char* g_hsiFutSymbol = 0;
char* g_hsceiFutSymbol = 0;

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

bool g_isRecovery[2];

#define PACKET_SIZE 512
#define PACKET_SIZE_SHIFT 9
#define RING_BUFFER_LENGTH 16384
#define RING_BUFFER_SIZE 8388608
#define RING_BUFFER_MASK 8388607
char* g_ringBuffer = 0;
unsigned int g_ringBufferWriteCursor = 0;
unsigned int g_ringBufferReadCursor = 0;
std::atomic<unsigned int> g_ringBufferCount(0);
bool canConsume = true;
pthread_t consumerThread;

// TODO: bad variable name. the snapshotBuffer does not contain snapshots. it just contain the secSid to send snapshots for
#define SNAPSHOT_BUFFER_SIZE 131072 
#define SNAPSHOT_BUFFER_MASK 131071
uint64_t* g_snapshotBuffer = 0;
unsigned int g_snapshotBufferWriteCursor = 0;
unsigned int g_snapshotBufferReadCursor = 0;
std::atomic<unsigned int> g_snapshotBufferCount(0);

static unsigned int OMDC = 0;
static unsigned int OMDD = 1;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved)
{
    g_vm = jvm;
    memset(g_secInfoMap, 0, sizeof(uint64_t) * NUM_FAST_SEC_SID * NUM_STRUCT_FIELDS);

    return JNI_VERSION_1_8;
}

JNIEXPORT jint JNICALL Java_com_lunar_marketdata_hkex_CtpMduApi_initialize(JNIEnv* env, jobject obj, jstring connectorFile, jstring omdcConfigFile, jstring omddConfigFile, jint senderSinkId, jint bookDepth, jobject byteBufferOrder, jobject byteBufferTrade, jobject byteBufferStats, jint bufferSize) {
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


    g_snapshotBuffer = new uint64_t[SNAPSHOT_BUFFER_SIZE];
    g_snapshotBufferWriteCursor = 0;
    g_snapshotBufferReadCursor = 0;
    g_snapshotBufferCount.store(0);

    canConsume = true;
    pthread_create(&consumerThread, NULL, consume, NULL);

    g_connectors[OMDC] = 0;
#ifdef EMBEDDED_MDU
    g_isRecovery[OMDC] = false;
#else
    g_isRecovery[OMDC] = true;
#endif
    const char* cOmdcConfigFile = env->GetStringUTFChars(omdcConfigFile, 0);
    if (strlen(cOmdcConfigFile) > 0) {
        std::cout << "[INFO] Initializing omdc connector with config file " << cOmdcConfigFile << "..." << std::endl;
        g_connectors[OMDC] = g_load(cOmdcConfigFile, received, &OMDC);
        if (!g_connectors[OMDC]) {
            std::cerr << "[ERROR] Cannot load omdc connector..." << std::endl;
            dlclose(g_dllHandle);
            return -1;
        }
    }
    else {
        std::cout << "[INFO] No config file specified for omdc connector..." << std::endl;
    }
    env->ReleaseStringUTFChars(omdcConfigFile, cOmdcConfigFile);

    g_connectors[OMDD] = 0;
#ifdef EMBEDDED_MDU
    g_isRecovery[OMDD] = false;
#else
    g_isRecovery[OMDD] = true;
#endif
    const char* cOmddConfigFile = env->GetStringUTFChars(omddConfigFile, 0);
    if (strlen(cOmddConfigFile) > 0) {
        //THIS RELEASE DOES NOT SUPPORT OMDD
        //std::cout << "[INFO] Initializing omdd connector with config file " << cOmddConfigFile << "..." << std::endl;
        //g_connectors[OMDD] = g_load(cOmddConfigFile, received, &OMDD);
        //if (!g_connectors[OMDD]) {
            std::cerr << "[ERROR] Cannot load omdd connector..." << std::endl;
            dlclose(g_dllHandle);
            return -1;
        //}
    }
    else {
        std::cout << "[INFO] No config file specified for omdd connector..." << std::endl;
    }
    env->ReleaseStringUTFChars(omddConfigFile, cOmddConfigFile);    

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

    // assumme our secSid == security symbol for omdc
    if (secSid < NUM_OMDC_SEC_SID) {
        memset(g_secInfoMap[secSid], 0, sizeof(uint64_t) * NUM_STRUCT_FIELDS);
        g_secInfoMap[secSid][STRUCT_SID_INDEX] = secSid;
    }

    env->ReleaseStringUTFChars(productKey, cProductKey);
    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_marketdata_hkex_CtpMduApi_registerHsiFutures(JNIEnv* env, jobject obj, jlong secSid, jstring productKey) {
    const char* cProductKey = env->GetStringUTFChars(productKey, 0);
    std::string pk(cProductKey);
    std::cout << "registering " << secSid << " to " << pk << std::endl;

    if (g_hsiFutSymbol != 0) {
        delete [] g_hsiFutSymbol;
    }
    g_hsiFutSymbol = new char[pk.length()];
    strcpy(g_hsiFutSymbol, pk.c_str());

    memset(g_secInfoMap[HSI_FUTURES_INDEX], 0, sizeof(uint64_t) * NUM_STRUCT_FIELDS);
    g_secInfoMap[HSI_FUTURES_INDEX][STRUCT_SID_INDEX] = secSid;
    g_hsiFutSecSid = secSid;

    env->ReleaseStringUTFChars(productKey, cProductKey);
    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_marketdata_hkex_CtpMduApi_registerHsceiFutures(JNIEnv* env, jobject obj, jlong secSid, jstring productKey) {
    const char* cProductKey = env->GetStringUTFChars(productKey, 0);
    std::string pk(cProductKey);
    std::cout << "registering " << secSid << " to " << pk << std::endl;

    if (g_hsceiFutSymbol != 0) {
        delete [] g_hsceiFutSymbol;
    }
    g_hsceiFutSymbol = new char[pk.length()];
    strcpy(g_hsceiFutSymbol, pk.c_str());

    memset(g_secInfoMap[HSCEI_FUTURES_INDEX], 0, sizeof(uint64_t) * NUM_STRUCT_FIELDS);
    g_secInfoMap[HSCEI_FUTURES_INDEX][STRUCT_SID_INDEX] = secSid;
    g_hsceiFutSecSid = secSid;

    env->ReleaseStringUTFChars(productKey, cProductKey);
    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_marketdata_hkex_CtpMduApi_close(JNIEnv* env, jobject obj) {
    if (g_connectors[OMDC]) {
        g_unload(g_connectors[OMDC]);
        g_connectors[OMDC] = 0;
    }
    if (g_connectors[OMDD]) {
        g_unload(g_connectors[OMDD]);
        g_connectors[OMDD] = 0;
    }
    if (g_dllHandle) {
        dlclose(g_dllHandle);
        g_dllHandle = 0;
    }

    canConsume = false;
    pthread_join(consumerThread, NULL);
    delete [] g_ringBuffer;
    delete [] g_snapshotBuffer;

    g_env->DeleteGlobalRef(g_obj);

    g_triggerSeqNum = 0;

    memset(g_secInfoMap, 0, sizeof(uint64_t) * NUM_FAST_SEC_SID * NUM_STRUCT_FIELDS);

    if (g_hsiFutSymbol != 0) {
        delete [] g_hsiFutSymbol;
        g_hsiFutSymbol = 0;
    }
    if (g_hsceiFutSymbol != 0) {
        delete [] g_hsceiFutSymbol;
        g_hsceiFutSymbol = 0;
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

inline uint64_t strToSecSid(const char* str) {
    uint64_t result = 0;
    while (*str) {
        if (*str >= '0' && *str <= '9') {
            result = result * 10 + (*str - '0');
            str++;
        }
        else if (*str == '_') {
            str++;
        }
        else {
            return 0;
        }
    }
    return result;
}

inline uint64_t fixstrToSecSid(const char* str, int size) {
    uint64_t result = 0;
    for (int i = 0; i < size; i++) {
        if (*str >= '0' && *str <= '9') {
            result = result * 10 + (*str - '0');
            str++;
        }
        else if (*str == '_') {
            str++;
        }
        else if (*str) {
            return 0;
        }
        else {
            break;
        }
    }
    return result;
}

inline void sendBulkTrade(uint64_t secSid){
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

inline void writeOrderBookSbe(uint64_t* obCache) {
    uint32_t obCacheIdx = STRUCT_ASKDEPTHSIZE_INDEX;
    uint32_t depthSize = obCache[obCacheIdx++];
#ifdef _TRACELOG
    std::cout << " ask depth (" << depthSize << ") ";
#endif
    OrderBookSnapshotSbe::AskDepth& askEncoder = g_orderBookSnapshotSbe.askDepthCount(depthSize);
    for (uint32_t idx = 0; idx < depthSize; idx++) {
        int price = obCache[obCacheIdx++];
        uint64_t quantity = obCache[obCacheIdx++];
        askEncoder.next().price(price).quantity(quantity).tickLevel(0);
#ifdef _TRACELOG
        std::cout << " " << idx << "[" << quantity << "@" << price << "]";
#endif
    }

    obCacheIdx = STRUCT_BIDDEPTHSIZE_INDEX;
    depthSize = obCache[obCacheIdx++];
#ifdef _TRACELOG
    std::cout << " bid depth (" << depthSize << ") ";
#endif
    OrderBookSnapshotSbe::BidDepth& bidEncoder = g_orderBookSnapshotSbe.bidDepthCount(depthSize);
    for (uint32_t idx = 0; idx < depthSize; idx++) {
        int price = obCache[obCacheIdx++];
        uint64_t quantity = obCache[obCacheIdx++];
        bidEncoder.next().price(price).quantity(quantity).tickLevel(0);
#ifdef _TRACELOG
        std::cout << " " << idx << "[" << quantity << "@" << price << "]";
#endif
    }
    obCacheIdx = STRUCT_ASKDEPTHSIZE_INDEX;
    depthSize = obCache[obCacheIdx++];
#ifdef _TRACELOG
    std::cout << std::endl;
#endif
    g_messageHeaderOrder.payloadLength(g_orderBookSnapshotSbe.size());
}

inline void buildOrderBook(const EI6::Market& msg, uint64_t* obCache) {
    int obCacheIdx = STRUCT_BIDDEPTHSIZE_INDEX;
    EI6::orderbook_view orderbook;
    uint32_t depthSize = 0;
    if (msg.has_BidOrderbook()) {
        orderbook = msg.get_BidOrderbook();
        depthSize = orderbook.count;
        obCache[obCacheIdx++] = depthSize;
        for (uint32_t idx = 0; idx < depthSize; idx++) {
            EI6::MarketDepthEntry const* bidSideEntry =  orderbook.data + idx;
            if (bidSideEntry->HasPrice() && bidSideEntry->HasQuantity() && bidSideEntry->GetPrice() > 0) {
                obCache[obCacheIdx++] = decimalToInt(bidSideEntry->GetPrice());
                obCache[obCacheIdx++] = bidSideEntry->GetQuantity();
            }
            else {
                obCache[obCacheIdx++] = 0;
                obCache[obCacheIdx++] = 0;
            }
        }
    }
    obCacheIdx = STRUCT_ASKDEPTHSIZE_INDEX;
    if (msg.has_AskOrderbook()) {
        orderbook = msg.get_AskOrderbook();
        depthSize = orderbook.count;
        obCache[obCacheIdx++] = depthSize;
        for (uint32_t idx = 0; idx < depthSize; idx++) {
            EI6::MarketDepthEntry const* askSideEntry =  orderbook.data + idx;
            if (askSideEntry->HasPrice() && askSideEntry->HasQuantity() && askSideEntry->GetPrice() > 0) {
                obCache[obCacheIdx++] = decimalToInt(askSideEntry->GetPrice());
                obCache[obCacheIdx++] = askSideEntry->GetQuantity();
            }
            else {
                obCache[obCacheIdx++] = 0;
                obCache[obCacheIdx++] = 0;
            }
        }
    }
}

void process(unsigned int sourceId, char* data, unsigned int size) {
    //messages from ei6 connector
    typedef EI6::DataType::Type  msg_type; //ei6 head file: const_def
    msg_type type = *(msg_type const*)data;

    if(type == EI6::DataType::EnumMarket){
        // Extract ProductID from binary buffer directly
        uint64_t* secInfo = 0;
        uint64_t secSid = 0;

        int idSize = (int)data[2] & 31;
        uint64_t rawSecSid = fixstrToSecSid(data + 3, idSize);
        if (rawSecSid > 0) {
            if (rawSecSid < NUM_OMDC_SEC_SID) {
                secInfo = g_secInfoMap[rawSecSid];
            }
            else if (rawSecSid == g_hsiFutId) {
                secInfo = g_secInfoMap[HSI_FUTURES_INDEX];
            }
            else if (rawSecSid == g_hsceiFutId) {
                secInfo = g_secInfoMap[HSCEI_FUTURES_INDEX];
            }
        }
        if (secInfo == 0)
            return;
        secSid = secInfo[STRUCT_SID_INDEX];
        if (secSid == 0)
            return;

        EI6::Market msg(data, size);
        int64_t dateTime = 0;
        // When both dateTime and productID are available
        if (msg.has_ProductID() && msg.has_DateTime()) {
            uint64_t seqNum = secInfo[STRUCT_SEQNUM_INDEX];

            buildOrderBook(msg, secInfo);

            struct timespec tv;
            clock_gettime(CLOCK_REALTIME, &tv);

            EI6::DateTime64 dateTime64 = msg.get_DateTime();
            dateTime = (int64_t)((dateTime64.GetHour() + 8) * 3600 + dateTime64.GetMinute() * 60 + dateTime64.GetSecond()) * 1000000000L + (int64_t)(dateTime64.GetMillisecond()) * 1000000L + (int64_t)(dateTime64.GetMicrosecond()) * 1000L + (int64_t)dateTime64.GetNanosecond();

            if (msg.has_ExchangePlatformLastTradeStatus()) {
                char dataType = msg.get_ExchangePlatformLastTradeStatus()[0];
                if (dataType == 'N' && !g_isRecovery[sourceId]) {
                    time_t t = tv.tv_sec;
                    struct tm* n = gmtime(&t);
                    int64_t receivedTime =  (int64_t)((n->tm_hour + 8) * 3600 + n->tm_min * 60 + n->tm_sec) * 1000000000L + (int64_t)tv.tv_nsec;
#ifdef _TRACELOG
                    std::cout << "[INFO] " << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." <<  std::setfill('0') << std::setw(9) << tv.tv_nsec;
                    std::cout << " Received trade for " <<  secSid << " " << msg.to_string() << std::endl;
#endif
                    TradeType::Value tradeType = TradeType::Value::AUTOMATCH_NORMAL;
                    if (msg.has_LastTradePrice()) {
                        if (msg.has_ExchangePlatformLastTradeType() && (msg.get_ExchangePlatformLastTradeType()[0] == '0' || strcmp(msg.get_ExchangePlatformLastTradeType(), "100") == 0)) {
                            int lastTradePrice = decimalToInt(msg.get_LastTradePrice());
                            if (secSid != g_bulkTrade.secSid){
                                if (g_bulkTrade.totalTradeQty != 0){
                                    //not an error, could be ticks from another channel for another security
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
                        volume(volume).turnover(volume).isRecovery(g_isRecovery[sourceId] ? BooleanType::TRUE : BooleanType::FALSE);
                    g_statsTriggerInfo->triggeredBy(g_senderSinkId).triggerSeqNum(g_triggerSeqNum++).nanoOfDay(dateTime);
                    g_messageHeaderStats.payloadLength(g_marketStatsSbe.size());
                    g_rEnv->CallObjectMethod(g_obj, g_onStatsCallback, secSid, g_messageHeaderStats.size() + g_marketStatsSbe.size());
                }
            }
            else {
                // Send previous bulked trade
                if (g_bulkTrade.totalTradeQty != 0){
                    //if (g_bulkTrade.secSid != secSid) {
                    //    std::cout << "[ERROR] Received market depth for " << secSid << " now.  Possibly market depth is missing for " << g_bulkTrade.secSid << std::endl;
                    //}
                    sendBulkTrade(g_bulkTrade.secSid);
                }

                time_t t = tv.tv_sec;
                struct tm* n = gmtime(&t);
                int64_t receivedTime =  (int64_t)((n->tm_hour + 8) * 3600 + n->tm_min * 60 + n->tm_sec) * 1000000000L + (int64_t)tv.tv_nsec;

                secInfo[STRUCT_SEQNUM_INDEX] = ++seqNum;

                g_orderBookSnapshotSbe.position(g_orderBookPosition);
                g_orderBookSnapshotSbe.secSid(secSid).transactTime(receivedTime).seqNum(seqNum).isRecovery((g_isRecovery[sourceId] || (msg.has_IsRealtime() && !msg.get_IsRealtime())) ? BooleanType::TRUE : BooleanType::FALSE);
                g_orderBookTriggerInfo->triggeredBy(g_senderSinkId).triggerSeqNum(g_triggerSeqNum++).nanoOfDay(dateTime);
#ifdef _TRACELOG
                std::cout << "[INFO] " << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." <<  std::setfill('0') << std::setw(9) << tv.tv_nsec;
                std::cout << " Received market data for " <<  secSid << ":" << seqNum << " " << msg.to_string() << std::endl;
#endif
                writeOrderBookSbe(secInfo);
                g_rEnv->CallObjectMethod(g_obj, g_onMarketDataCallback, secSid, g_messageHeaderOrder.size() + g_orderBookSnapshotSbe.size());
            }
        }
    }
    else if(type == EI6::DataType::EnumTradingPhase){
        if (sourceId == OMDC) {
            EI6::TradingPhase msg(data, size);
    #ifdef _TRACELOG
            std::cout << "[INFO] Received trading phase for " << msg.to_string() << "..." << std::endl;
    #endif
            if (msg.has_TradingPhaseCode() && msg.has_ID() && strcmp(msg.get_ID(), "MAIN") == 0) {
                std::cout << "[INFO] Received trading phase for " << msg.to_string() << "..." << std::endl;
                g_rEnv->CallObjectMethod(g_obj, g_onTradingPhaseCallback, msg.get_TradingPhaseCode());
            }
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
    else if(type == EI6::DataType::EnumProduct){
        EI6::Product msg(data, size);
        if (msg.has_SymbolID() && msg.has_ProductID()) {
            if (g_hsiFutSymbol != 0 && strcmp(msg.get_SymbolID(), g_hsiFutSymbol) == 0) {
                g_hsiFutId = strToSecSid(msg.get_ProductID());
                std::cout << "[INFO] Received mdu product id for HSI futures " << g_hsiFutId << "..." << std::endl;
            }
            else if (g_hsceiFutSymbol != 0 && strcmp(msg.get_SymbolID(), g_hsceiFutSymbol) == 0) {
                g_hsceiFutId = strToSecSid(msg.get_ProductID());
                std::cout << "[INFO] Received mdu product id for HSCEI futures " << g_hsceiFutId << "..." << std::endl;
            }
        }
    }
    else if(type == EI6::DataType::EnumEndRecovery){
        EI6::EndRecovery msg(data, size);
        std::cout << "[INFO] Received end recovery " << msg.to_string() << std::endl;
        g_isRecovery[sourceId] = false;
    }
}

void received(void* ud, const void* data, unsigned int size) {
    typedef EI6::DataType::Type  msg_type; //ei6 head file: const_def
#ifdef EMBEDDED_MDU
    packed_stream* pkt = (packed_stream*)(data);
    msg_type type = pkt->type_;
    bool isDirect = false;
    if (pkt->version_ == direct_packing) {
        isDirect = true;
        data = pkt->data();
        size = pkt->size();
    }
#else
    msg_type type = *(msg_type const*)data;
#endif

    switch (type) {
        case EI6::DataType::EnumMarket:
        case EI6::DataType::EnumTradingPhase:
        case EI6::DataType::EnumSessionStateEvent:
        case EI6::DataType::EnumEndRecovery:
        case EI6::DataType::EnumProduct:
            unsigned int sourceId = *(unsigned int*)(ud);
            produce(sourceId, data, size);
            break;
    }
#ifdef EMBEDDED_MDU
    if (isDirect) {
        delete pkt;
    }
#endif
}

void produce(unsigned int sourceId, const void* data, unsigned int size) {
    while (g_ringBufferCount == RING_BUFFER_LENGTH) {
        // busy spin - i don't expect the code to reach here often
    }
    char* cursor = g_ringBuffer + g_ringBufferWriteCursor;
    memcpy(cursor, &sourceId, sizeof(unsigned int));
    cursor+=sizeof(unsigned int);
    memcpy(cursor, &size, sizeof(unsigned int));
    cursor+=sizeof(unsigned int);
    memcpy(cursor, data, size);
    g_ringBufferWriteCursor = (g_ringBufferWriteCursor + PACKET_SIZE) & RING_BUFFER_MASK;
    g_ringBufferCount++;
}

JNIEXPORT jint JNICALL Java_com_lunar_marketdata_hkex_CtpMduApi_requestSnapshot(JNIEnv* env, jobject obj, jlong secSid) {
    while (g_snapshotBufferCount == SNAPSHOT_BUFFER_SIZE) {
        // busy spin - i don't expect the code to ever reach here unless we have too many subscriptions requests at the same time
    }
    uint64_t* cursor = g_snapshotBuffer + g_snapshotBufferWriteCursor;
    *cursor = secSid;
    g_snapshotBufferWriteCursor = (g_snapshotBufferWriteCursor + 1) & SNAPSHOT_BUFFER_MASK;
    g_snapshotBufferCount++;
    return 0;
}

void sendSnapshot(const uint64_t& secSid) {
    if (secSid < 1) {
        return;
    }
    uint64_t* secInfo = NULL;
    if (secSid < NUM_OMDC_SEC_SID) {
       secInfo = g_secInfoMap[secSid];
    }
    else if (secSid == g_hsiFutSecSid) {
        secInfo = g_secInfoMap[HSI_FUTURES_INDEX];
    }
    else if (secSid == g_hsceiFutSecSid) {
        secInfo = g_secInfoMap[HSCEI_FUTURES_INDEX];
    }
    if (secInfo == NULL)
        return;
    struct timespec tv;
    clock_gettime(CLOCK_REALTIME, &tv);
    time_t t = tv.tv_sec;
    struct tm* n = gmtime(&t);
    int64_t dateTime =  (int64_t)(((n->tm_hour + 8) % 24) * 3600 + n->tm_min * 60 + n->tm_sec) * 1000000000L + (int64_t)tv.tv_nsec;

    uint64_t seqNum = secInfo[STRUCT_SEQNUM_INDEX];
    secInfo[STRUCT_SEQNUM_INDEX] = ++seqNum;
    g_orderBookSnapshotSbe.position(g_orderBookPosition);
    g_orderBookSnapshotSbe.secSid(secSid).transactTime(dateTime).seqNum(seqNum).isRecovery(BooleanType::TRUE);
    g_orderBookTriggerInfo->triggeredBy(g_senderSinkId).triggerSeqNum(g_triggerSeqNum++).nanoOfDay(dateTime);
#ifdef _TRACELOG
    std::cout << "[INFO] " << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." <<  std::setfill('0') << std::setw(9) << tv.tv_nsec;
    std::cout << " Sending snapshot market data for " <<  secSid << ":" << seqNum << std::endl;
#endif
    writeOrderBookSbe(secInfo);
    g_rEnv->CallObjectMethod(g_obj, g_onMarketDataCallback, secSid, g_messageHeaderOrder.size() + g_orderBookSnapshotSbe.size());
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
            unsigned int sourceId;
            char* cursor = g_ringBuffer + g_ringBufferReadCursor;
            memcpy(&sourceId, cursor, sizeof(unsigned int));
            cursor+=sizeof(unsigned int);
            memcpy(&size, cursor, sizeof(unsigned int));
            cursor+=sizeof(unsigned int);
            try {
                process(sourceId, cursor, size);
            }
            catch (const std::exception& e) {
                std::cerr << "[ERROR] Error processing market data message" << std::endl;
            }
            g_ringBufferReadCursor = (g_ringBufferReadCursor + PACKET_SIZE) & RING_BUFFER_MASK;
            g_ringBufferCount--;
        }
        if (g_snapshotBufferCount > 0) {
            uint64_t* cursor = g_snapshotBuffer + g_snapshotBufferReadCursor;
            uint64_t secSid = *cursor;
            sendSnapshot(secSid);
            g_snapshotBufferReadCursor = (g_snapshotBufferReadCursor + 1) & SNAPSHOT_BUFFER_MASK;
            g_snapshotBufferCount--;
        }
    }

    g_vm->DetachCurrentThread();
    g_rEnv = 0;
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
