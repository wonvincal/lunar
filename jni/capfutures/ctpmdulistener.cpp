#include <dlfcn.h>
#include <ctype.h>
#include <string.h>
#include <assert.h>
#include <stdio.h>
#include <iostream>
#include <iomanip>
#include <unistd.h>
#include <math.h>
#include <sys/time.h>
#include <pthread.h>
#include <atomic>
#include <map>
#include <bitset>
#include <climits>
#include <thread>

#include "EI6/const_def.h"
#include "EI6/ExchangePlatform.h"
#include "EI6/Exchange.h"
#include "EI6/ProductClass.h"
#include "EI6/Product.h"
#include "EI6/TradingPhase.h"
#include "EI6/Market.h"
#include "EI6/SessionStateEvent.h"
#include "EI6/EndRecovery.h"
#include "EI6/ExchangeRawMessage.h"
#ifdef EMBEDDED_MDU
#include "EI6/packed_stream.h"
#endif

typedef void(*received_cb)(void* ud, const void* buf, unsigned int size);
typedef void(*send_func)(void* gateway, const void* buf, unsigned int size);
typedef void* (*load_func)(const char* config_file_name, received_cb received, void* ud); //return instance of gateway
typedef void(*unload_func)(void* gateway);

int64_t seqNum = -1;
int64_t orderBookId = -1;

inline int decimalToInt(const EI6::Decimal64& decimal) {
	// in most (all) cases that I see, exp is 3 which is exactly what we want
	return  decimal.GetExponent() == -3 ? decimal.GetMantissa() : decimal.GetMantissa() * pow(10, 3 + decimal.GetExponent());
	//static int pow10[4] = {1, 10, 100, 1000};
	//int exp = 3 + decimal.GetExponent();
	//return exp >= 0 ? decimal.GetMantissa() * pow10[exp] : decimal.GetMantissa() * pow(10, exp);
}

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

struct timeval g_tv;

#define MAX_OMDC_SECURITIES 100000
std::map<uint64_t, char*> m_futures;

std::map<uint64_t, uint64_t*> m_orderBookCaches;

static int OMDC = 1;
static int OMDD = 2;

struct MessageHeader {
    uint16_t msgSize;
    uint16_t msgType;
} __attribute__((packed));

struct BrokerQueueDetail {
    uint16_t item;
    char type;
    char filler;
} __attribute__((packed));

struct BrokerQueue {
    uint16_t msgSize;
    uint16_t msgType;
    uint32_t secCode;
    uint8_t itemCount;
    uint16_t side;
    char bqMoreFlag;
    BrokerQueueDetail items[80]; // exchange sends up to 40 broker numbers. there is also one item per level, while I observe a maximum of 20 levels, the max items should be 40 + 20 = 60. but i don't see the 20 number in the spec, so assume max one unique level per broker number...
} __attribute__((packed));

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

void printOrderBook(uint64_t* obCache) {
    uint32_t obCacheIdx = 0;
    uint32_t depthSize = obCache[obCacheIdx++];
    for (uint32_t idx = 0; idx < 5; idx++) {
        if (idx < depthSize) {
            int price = obCache[obCacheIdx++];
            uint64_t quantity = obCache[obCacheIdx++];
            obCacheIdx++;
            if (price > 0 && quantity >= 0) {
                std::cout << price << "," << quantity << ",";
                continue;
            }
        }
        std::cout << ",,";
    }
    obCacheIdx = 16;
    depthSize = obCache[obCacheIdx++];
    for (uint32_t idx = 0; idx < 5; idx++) {
        if (idx < depthSize) {
            int price = obCache[obCacheIdx++];
            uint64_t quantity = obCache[obCacheIdx++];
            obCacheIdx++;
            if (price > 0 && quantity >= 0) {
                std::cout << price << "," << quantity << ",";
                continue;
            }
        }
        std::cout << ",,";
    }
}

void printNumberOfOrders(uint64_t* obCache) {
    uint32_t obCacheIdx = 0;
    uint32_t depthSize = obCache[obCacheIdx++];
    for (uint32_t idx = 0; idx < 5; idx++) {
        if (idx < depthSize) {
            obCacheIdx += 2;
            int numOfOrders = obCache[obCacheIdx++];
            if (numOfOrders > 0) {
                std::cout << numOfOrders << ",";
                continue;
            }
        }
        std::cout << ",";
    }
    obCacheIdx = 16;
    depthSize = obCache[obCacheIdx++];
    for (uint32_t idx = 0; idx < 5; idx++) {
        if (idx < depthSize) {
            obCacheIdx += 2;
            int numOfOrders = obCache[obCacheIdx++];
            if (numOfOrders > 0) {
                std::cout << numOfOrders << ",";
                continue;
            }
        }
        std::cout << ",";
    }
}


void buildOrderBook(const EI6::Market& msg, uint64_t* obCache) {
    int obCacheIdx = 0;
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
                obCache[obCacheIdx++] = 1;
            }
            else {
                obCache[obCacheIdx++] = -1;
                obCache[obCacheIdx++] = -1;
                obCache[obCacheIdx++] = -1;
            }
        }
    }
    obCacheIdx = 16;
    if (msg.has_AskOrderbook()) {
        orderbook = msg.get_AskOrderbook();
        depthSize = orderbook.count;
        obCache[obCacheIdx++] = depthSize;
        for (uint32_t idx = 0; idx < depthSize; idx++) {
            EI6::MarketDepthEntry const* askSideEntry =  orderbook.data + idx;
            if (askSideEntry->HasPrice() && askSideEntry->HasQuantity() && askSideEntry->GetPrice() > 0) {
                obCache[obCacheIdx++] = decimalToInt(askSideEntry->GetPrice());
                obCache[obCacheIdx++] = askSideEntry->GetQuantity();
                obCache[obCacheIdx++] = 1;
            }
            else {
                obCache[obCacheIdx++] = -1;
                obCache[obCacheIdx++] = -1;
                obCache[obCacheIdx++] = -1;
            }
        }
    }
}

void process(const struct timeval& tv, char* data, unsigned int size) {
    typedef EI6::DataType::Type  msg_type; //ei6 head file: const_def
    msg_type type = *(msg_type const*)data;
    if (type == EI6::DataType::EnumMarket) {
        //{
        //    EI6::Market msg(data, size);
        //    std::cout <<  msg.to_string() << std::endl;
        //    for (int i = 0; i < size; i++)
        //        std::cout << std::bitset<CHAR_BIT>(data[i]);
        //    std::cout << std::endl;
        //}

        EI6::Market msg(data, size);
        EI6::DateTime64 dateTime64 = msg.get_DateTime();
        const char* productId = msg.get_ProductID();
        uint64_t secSid = strToSecSid(msg.get_ProductID());
        bool isOmdc = true;
        if (secSid > MAX_OMDC_SECURITIES) {
            std::map<uint64_t, char*>::iterator iSecSid = m_futures.find(secSid);
            if (iSecSid == m_futures.end()) {
                return;
            }
            productId = iSecSid->second;
            isOmdc = false;
        }
        if (secSid == 0) {
            return;
        }

        uint64_t* obCache = NULL;
        std::map<uint64_t, uint64_t*>::iterator iObCache = m_orderBookCaches.find(secSid);
        if (iObCache == m_orderBookCaches.end()) {
            // depth size: 5
            // bid depth size + (bid price + bid quantity + order size)[5] + ask depth size + (ask price + ask quantity + order size)[5]
            // 1 + 3 * 5 + 1 + 3 * 5 = 1 + 15 + 1 + 15 = 32
            obCache = new uint64_t[32];
            memset(obCache, 0, sizeof(uint64_t)*32);
            m_orderBookCaches[secSid] = obCache;
        }
        else {
            obCache = iObCache->second;
        }
        buildOrderBook(msg, obCache);
        if (msg.has_ExchangePlatformLastTradeStatus()) {
            if (msg.get_ExchangePlatformLastTradeStatus()[0] == 'N') {
                if (msg.has_LastTradePrice()) {
                    if (msg.has_ExchangePlatformLastTradeType() && (isOmdc ? (msg.get_ExchangePlatformLastTradeType()[0] == '0' || strcmp(msg.get_ExchangePlatformLastTradeType(), "100") == 0) : (msg.get_ExchangePlatformLastTradeType()[0] == '1'))) {
                        time_t t = tv.tv_sec;
                        struct tm* n = gmtime(&t);
                        int tradePrice = decimalToInt(msg.get_LastTradePrice());
                        std::cout << n->tm_year + 1900 << "-" << std::setfill('0') << std::setw(2) << n->tm_mon + 1 << "-" << std::setfill('0') << std::setw(2) << n->tm_mday << " " << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." << std::setfill('0') << std::setw(6) << tv.tv_usec << ",";
                        std::cout << productId << "," << "Trade,," << tradePrice << "," << msg.get_LastTradeQuantity() << "," << 0 << ",";
                        printOrderBook(obCache);
                        int bestBid = obCache[0] > 0 ? obCache[1] : -1;
                        char side = tradePrice > bestBid ? 'A' : 'B';
                        std::cout << ",," << side << "," << ++seqNum << "," << orderBookId << ",";
                        printNumberOfOrders(obCache);
                        std::cout << dateTime64.GetYear() << "-" << std::setfill('0') << std::setw(2) << dateTime64.GetMonth() << "-" << std::setfill('0') << std::setw(2) << dateTime64.GetDay() << " " << std::setfill('0') << std::setw(2) << dateTime64.GetHour() + 8 << ":" << std::setfill('0') << std::setw(2) << dateTime64.GetMinute() << ":" << std::setfill('0') << std::setw(2) << dateTime64.GetSecond() << "." << std::setfill('0') << std::setw(6) << (int64_t)(dateTime64.GetMillisecond()) * 1000L + (int64_t)(dateTime64.GetMicrosecond()) << std::endl;
                    }
                }
            }
        }
        else if (!msg.has_IsRealtime() || msg.get_IsRealtime()) {
            time_t t = tv.tv_sec;
            struct tm* n = gmtime(&t);
            std::cout << n->tm_year + 1900 << "-" << std::setfill('0') << std::setw(2) << n->tm_mon + 1 << "-" << std::setfill('0') << std::setw(2) << n->tm_mday << " " << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." << std::setfill('0') << std::setw(6) << tv.tv_usec << ",";
            std::cout << productId << "," << "MarketDepth,,,,,";
            printOrderBook(obCache);
            std::cout << ",,," << ++seqNum << "," << ++orderBookId << ",";
            printNumberOfOrders(obCache);
            std::cout << dateTime64.GetYear() << "-" << std::setfill('0') << std::setw(2) << dateTime64.GetMonth() << "-" << std::setfill('0') << std::setw(2) << dateTime64.GetDay() << " " << std::setfill('0') << std::setw(2) << dateTime64.GetHour() + 8 << ":" << std::setfill('0') << std::setw(2) << dateTime64.GetMinute() << ":" << std::setfill('0') << std::setw(2) << dateTime64.GetSecond() << "." << std::setfill('0') << std::setw(6) << (int64_t)(dateTime64.GetMillisecond()) * 1000L + (int64_t)(dateTime64.GetMicrosecond()) << std::endl;
        }
    }
    else if (type == EI6::DataType::EnumProduct) {
        EI6::Product msg(data, size);
        if (msg.has_ProductClassID() && (strcmp(msg.get_ProductClassID(), "HSIFUT") == 0 || strcmp(msg.get_ProductClassID(), "HHIFUT") == 0)) {
            uint64_t secSid = strToSecSid(msg.get_ProductID());
            if (secSid > 0) {
                int size = strlen(msg.get_SymbolID()) + 1;
                char* buffer = new char[size];
                strncpy(buffer, msg.get_SymbolID(), size);
                m_futures[secSid] = buffer;
            }
        }
    }
    else if (type == EI6::DataType::EnumExchangeRawMessage) {
        EI6::ExchangeRawMessage msg(data, size);
        char const* rawData = msg.get_RawMessage().data;
        uint32_t rawSize = msg.get_RawMessage().count;

        if (rawSize >= sizeof(MessageHeader)) {
            MessageHeader header;
            memcpy(&header, rawData, sizeof(MessageHeader));
            if (header.msgType == 54) {
                BrokerQueue brokerQueue;
                memcpy(&brokerQueue, rawData, rawSize);
                time_t t = tv.tv_sec;
                struct tm* n = gmtime(&t);
                std::cout << n->tm_year + 1900 << "-" << std::setfill('0') << std::setw(2) << n->tm_mon + 1 << "-" << std::setfill('0') << std::setw(2) << n->tm_mday << " " << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." << std::setfill('0') << std::setw(6) << tv.tv_usec << ",";
                std::cout << brokerQueue.secCode << "," << "BrokerQueue,,,,,";
                for (int idx = 0; idx < 10; idx++) {
                    std::cout << ",,";
                }
                if (brokerQueue.side == 1) {
                    std::cout << "B";
                    for (int i = 0; i < brokerQueue.itemCount; i++) {
                        if (brokerQueue.items[i].type == 'B') {
                            std::cout << " " << brokerQueue.items[i].item;
                        }
                        else if (brokerQueue.items[i].item != 0) {
                            std::cout << " (" << brokerQueue.items[i].item << ")";
                        }
                    }
                    std::cout << ",,";
                }
                else if (brokerQueue.side == 2) {
                    std::cout << ",A";
                    for (int i = 0; i < brokerQueue.itemCount; i++) {
                        if (brokerQueue.items[i].type == 'B') {
                            std::cout << " " << brokerQueue.items[i].item;
                        }
                        else if (brokerQueue.items[i].item != 0) {
                            std::cout << " (" << brokerQueue.items[i].item << ")";
                        }
                    }
                    std::cout << ",";
                }
                std::cout << "," << ++seqNum << "," << orderBookId << ",,,,,,,,,,,";
                std::cout << n->tm_year + 1900 << "-" << std::setfill('0') << std::setw(2) << n->tm_mon + 1 << "-" << std::setfill('0') << std::setw(2) << n->tm_mday << " " << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." << std::setfill('0') << std::setw(6) << tv.tv_usec << std::endl;
            }
        }
    }
}

void received(void* ud, const void* data, unsigned int size) {
    typedef EI6::DataType::Type msg_type; //ei6 head file: const_def
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
    int market_id = *(int*)ud;
    switch (type) {
        case EI6::DataType::EnumMarket:
        case EI6::DataType::EnumExchangeRawMessage:
        case EI6::DataType::EnumProduct:    
            gettimeofday(&g_tv, NULL);
            while (g_ringBufferCount == RING_BUFFER_LENGTH) {
                // busy spin - i don't expect the code to reach here often
            }
            char* cursor = g_ringBuffer + g_ringBufferWriteCursor;
            memcpy(cursor, &g_tv, sizeof(struct timeval));
            cursor+=sizeof(struct timeval);
            memcpy(cursor, &size, sizeof(unsigned int));
            cursor+=sizeof(unsigned int);
            memcpy(cursor, data, size);
            g_ringBufferWriteCursor = (g_ringBufferWriteCursor + PACKET_SIZE) & RING_BUFFER_MASK;
            g_ringBufferCount++;
            break;
    }
#ifdef EMBEDDED_MDU
    if (isDirect) {
        delete pkt;
    }
#endif
}

void* consume(void* arg) {
    struct timeval tv;
    unsigned int size = 0;
    while (canConsume) {
        if (g_ringBufferCount > 0) {
            char* cursor = g_ringBuffer + g_ringBufferReadCursor;
            memcpy(&tv, cursor, sizeof(struct timeval));
            cursor+=sizeof(struct timeval);
            memcpy(&size, cursor, sizeof(unsigned int));
            cursor+=sizeof(unsigned int);
            process(tv, cursor, size);
            g_ringBufferReadCursor = (g_ringBufferReadCursor + PACKET_SIZE) & RING_BUFFER_MASK;
            g_ringBufferCount--;
        }
    }
    return 0;
}

void initialize() {
    g_ringBuffer = new char[RING_BUFFER_SIZE];
    g_ringBufferWriteCursor = 0;
    g_ringBufferReadCursor = 0;
    g_ringBufferCount.store(0);
    canConsume = true;
    pthread_create(&consumerThread, NULL, consume, NULL);
}

void close() {
    canConsume = false;
    pthread_join(consumerThread, NULL);
    delete [] g_ringBuffer;
}

int main(int argc, char* argv[]) {
	char const* so_file = "./libmduapi.so";
	char const* config_file = "conf";
	void* dll_handle = dlopen(so_file, RTLD_LAZY);
	if (!dll_handle) {
		fprintf(stderr, "%s\n", dlerror());
		return -1;
	}
	send_func send;
	*(void**)(&send) = dlsym(dll_handle, "ei6_send");
	load_func load;
	*(void**)&load = dlsym(dll_handle, "ei6_load");
	unload_func unload;
	*(void**)&unload = dlsym(dll_handle, "ei6_unload");

    initialize();
	std::cout << "DateTime,RIC,Type,Id,Price,Volume,Qualifiers,L1-BidPrice,L1-BidSize,L2-BidPrice,L2-BidSize,L3-BidPrice,L3-BidSize,L4-BidPrice,L4-BidSize,L5-BidPrice,L5-BidSize,L1-AskPrice,L1-AskSize,L2-AskPrice,L2-AskSize,L3-AskPrice,L3-AskSize,L4-AskPrice,L4-AskSize,L5-AskPrice,L5-AskSize,Bid_BrokerQ,Ask_BrokerQ,Side,SeqNum,OrderBookId,L1-BidNum,L2-BidNum,L3-BidNum,L4-BidNum,L5-BidNum,L1-AskNum,L2-AskNum,L3-AskNum,L4-AskNum,L5-AskNum,SourceTime" << std::endl;
	void* gateway = load(config_file, received, 0);
    pause();
	unload(gateway);
	dlclose(dll_handle);
    close();
    for (std::map<uint64_t, char*>::iterator i = m_futures.begin(); i != m_futures.end(); i++) {
        delete [] i->second;
    }
	return 0;
}
