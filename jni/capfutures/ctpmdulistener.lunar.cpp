// EI4CommandClient.cpp : Defines the entry point for the console application.
//

#include <signal.h>
#include <string>
#include <iostream>
#include <map>
#include <unistd.h>
#include <math.h>
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

#include <dlfcn.h>
#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <assert.h>
#include <functional>

#include <time.h>
#include <iomanip>

typedef void (*received_cb)(void* ud, const void* buf, unsigned int size);
typedef void (*send_func)(void* connector, const void* buf, unsigned int size);
typedef void* (*load_func)(const char* config_file_name, received_cb received, void* ud);
typedef void (*unload_func)(void* connector);

void received(void* ud, const void* data, unsigned int size);
void process(unsigned int sourceId, char* data, unsigned int size);
void produce(unsigned int sourceId, const void* data, unsigned int size);
void* consume(void* arg);

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

bool g_isRecovery[2];

#define PACKET_SIZE 512
#define PACKET_SIZE_SHIFT 9
#define RING_BUFFER_LENGTH 16384
#define RING_BUFFER_SIZE 8388608
#define RING_BUFFER_MASK 8388607
char* g_ringBuffer = 0;
std::atomic<unsigned int> g_ringBufferWriteCount(0);
std::atomic<unsigned int> g_ringBufferWrittenCount(0);
unsigned int g_ringBufferReadCursor = 0;
std::atomic<unsigned int> g_ringBufferCount(0);
std::atomic<unsigned int> g_pendingWrites(0);
bool canConsume = true;
pthread_t consumerThread;

static unsigned int OMDC = 0;
static unsigned int OMDD = 1;

void OnLoad()
{
    memset(g_secInfoMap, 0, sizeof(uint64_t) * NUM_FAST_SEC_SID * NUM_STRUCT_FIELDS);
}

int initialize(const char* cConnectorFile, const char* cOmdcConfigFile, const char* cOmddConfigFile) {
    std::cout << "[INFO] Finding Java class com/lunar/marketdata/hkex/CtpMduApi..." << std::endl;
    std::cout << "[INFO] Linking to dll " << cConnectorFile << "..." << std::endl;
    g_dllHandle = dlopen(cConnectorFile, RTLD_LAZY);
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
    g_ringBufferWriteCount.store(0);
    g_ringBufferWrittenCount.store(0);
    g_ringBufferReadCursor = 0;
    g_ringBufferCount.store(0);
    g_pendingWrites.store(0);

    canConsume = true;
    pthread_create(&consumerThread, NULL, consume, NULL);

    g_connectors[OMDC] = 0;
    g_isRecovery[OMDC] = true;
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

    g_connectors[OMDD] = 0;
    g_isRecovery[OMDD] = true;
    if (strlen(cOmddConfigFile) > 0) {
        std::cout << "[INFO] Initializing omdd connector with config file " << cOmddConfigFile << "..." << std::endl;
        g_connectors[OMDD] = g_load(cOmddConfigFile, received, &OMDD);
        if (!g_connectors[OMDD]) {
            std::cerr << "[ERROR] Cannot load omdd connector..." << std::endl;
            dlclose(g_dllHandle);
            return -1;
        }
    }
    else {
        std::cout << "[INFO] No config file specified for omdd connector..." << std::endl;
    }

    g_triggerSeqNum = 0;

    return 0;
}

int registerSecurity(long secSid, const char* cProductKey) {
    std::string pk(cProductKey);
    std::cout << "registering " << secSid << " to " << pk << std::endl;

    // assumme our secSid == security symbol for omdc
    if (secSid < NUM_OMDC_SEC_SID) {
        memset(g_secInfoMap[secSid], 0, sizeof(uint64_t) * NUM_STRUCT_FIELDS);
        g_secInfoMap[secSid][STRUCT_SID_INDEX] = secSid;
    }

    return 0;
}

int registerHsiFutures(long secSid, const char* cProductKey) {
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

    return 0;
}

int registerHsceiFutures(long secSid, const char* cProductKey) {
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
    return 0;
}

int close() {
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

inline void writeOrderBookSbe(uint64_t* obCache) {
    uint32_t obCacheIdx = STRUCT_ASKDEPTHSIZE_INDEX;
    uint32_t depthSize = obCache[obCacheIdx++];
    std::cout << " ask depth (" << depthSize << ") ";
    for (uint32_t idx = 0; idx < depthSize; idx++) {
        int price = obCache[obCacheIdx++];
        uint64_t quantity = obCache[obCacheIdx++];
        std::cout << " " << idx << "[" << quantity << "@" << price << "]";
    }

    obCacheIdx = STRUCT_BIDDEPTHSIZE_INDEX;
    depthSize = obCache[obCacheIdx++];
    std::cout << " bid depth (" << depthSize << ") ";
    for (uint32_t idx = 0; idx < depthSize; idx++) {
        int price = obCache[obCacheIdx++];
        uint64_t quantity = obCache[obCacheIdx++];
        std::cout << " " << idx << "[" << quantity << "@" << price << "]";
    }
    obCacheIdx = STRUCT_ASKDEPTHSIZE_INDEX;
    depthSize = obCache[obCacheIdx++];
    std::cout << std::endl;
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
                }
                else if (dataType == 'S') {
                    time_t t = tv.tv_sec;
                    struct tm* n = gmtime(&t);
                    int64_t receivedTime =  (int64_t)((n->tm_hour + 8) * 3600 + n->tm_min * 60 + n->tm_sec) * 1000000000L + (int64_t)tv.tv_nsec;
#ifdef _TRACELOG
                    std::cout << "[INFO] " << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." <<  std::setfill('0') << std::setw(9) << tv.tv_nsec;
                    std::cout << " Received stats for " <<  secSid << " " << msg.to_string() << std::endl;
#endif
                }
            }
            else {
                time_t t = tv.tv_sec;
                struct tm* n = gmtime(&t);
                int64_t receivedTime =  (int64_t)((n->tm_hour + 8) * 3600 + n->tm_min * 60 + n->tm_sec) * 1000000000L + (int64_t)tv.tv_nsec;

                secInfo[STRUCT_SEQNUM_INDEX] = ++seqNum;

#ifdef _TRACELOG
                std::cout << "[INFO] " << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." <<  std::setfill('0') << std::setw(9) << tv.tv_nsec;
                std::cout << " Received market data for " <<  secSid << ":" << seqNum << " " << msg.to_string() << std::endl;
#endif
                writeOrderBookSbe(secInfo);
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
            }
        }
    }
    else if(type == EI6::DataType::EnumSessionStateEvent){
        EI6::SessionStateEvent msg(data, size);
        if (msg.has_SessionState()) {
            int sessionState = msg.has_SessionState() ? msg.get_SessionState() : 0;
            std::cout << "[INFO] Received session state for " << sessionState << "..." << std::endl;
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
    msg_type type = *(msg_type const*)data;

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
}

void produce(unsigned int sourceId, const void* data, unsigned int size) {
    g_pendingWrites++;
    while (g_ringBufferCount >= RING_BUFFER_LENGTH - g_pendingWrites) {
        // busy spin - i don't expect the code to reach here often
    }
    unsigned int writeCount = g_ringBufferWriteCount++;
    char* cursor = g_ringBuffer + (((writeCount) << PACKET_SIZE_SHIFT) & RING_BUFFER_MASK);
    memcpy(cursor, &sourceId, sizeof(unsigned int));
    cursor+=sizeof(unsigned int);
    memcpy(cursor, &size, sizeof(unsigned int));
    cursor+=sizeof(unsigned int);
    memcpy(cursor, data, size);
    unsigned int writtenCount = g_ringBufferWrittenCount++;
    int numNewEntries = 1 + writtenCount - writeCount;
    if (numNewEntries > 0) {
        int count = g_ringBufferCount.fetch_add(numNewEntries);
        g_pendingWrites.fetch_add(-numNewEntries);
    }
}

void* consume(void* arg) {
    struct timeval tv;
    unsigned int size = 0;

    while (canConsume) {
        if (g_ringBufferCount > 0) {
            unsigned int sourceId;
            char* cursor = g_ringBuffer + g_ringBufferReadCursor;
            memcpy(&sourceId, cursor, sizeof(unsigned int));
            cursor+=sizeof(unsigned int);
            memcpy(&size, cursor, sizeof(unsigned int));
            cursor+=sizeof(unsigned int);
            process(sourceId, cursor, size);
            g_ringBufferReadCursor = (g_ringBufferReadCursor + PACKET_SIZE) & RING_BUFFER_MASK;
            g_ringBufferCount--;
        }
    }

    return 0;
}


int main(int argc, char* argv[]) {
    char const* so_file = "./libmduapi.so";
    char const* config_file_omdc = "conf.omdc";
    char const* config_file_omdd = "conf.omdd";
    OnLoad();
    for (int i = 0; i < 10000; i++) {
        registerSecurity(i, "i");
    }
    registerHsiFutures(200000, "HSIJ7");
    registerHsceiFutures(200001, "HHIJ7");
    initialize(so_file, config_file_omdc, config_file_omdd);
    pause();
    close();
}
