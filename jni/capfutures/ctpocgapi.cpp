// EI4CommandClient.cpp : Defines the entry point for the console application.
//

#include <signal.h>
#include <string>
#include <iostream>
#include <map>
#include <unistd.h>

#include "EI6/EndRecovery.h"
#include "EI6/const_def.h"
#include "EI6/ExchangePlatform.h"
#include "EI6/Exchange.h"
#include "EI6/ProductClass.h"
#include "EI6/Product.h"
#include "EI6/TradingPhase.h"
#include "EI6/Market.h"
#include "EI6/ExecutionReport.h"
#include "EI6/CreateStrategyReport.h"
#include "EI6/OrderCancelReject.h"
#include "EI6/EnterQuoteResponse.h"
#include "EI6/CancelQuoteResponse.h"
#include "EI6/QuoteSideReport.h"
#include "EI6/SessionStateEvent.h"
#include "EI6/NewOrderSingle.h"
#include "EI6/OrderCancelRequest.h"
#include "EI6/packed_stream.h"

#include <dlfcn.h>
#include <stdio.h> 
#include <string.h> 
#include <ctype.h> 
#include <assert.h> 
#include <math.h>

#include "com_lunar_order_hkex_ocg_CtpOcgApi.h"

#include <sys/time.h>
#include <iomanip>

#define MAX_SECURITY_SID 99999
#define MAX_SID_DIGITS 5
#define MAX_NUM_ORDERS 10000
#define MAX_ORDER_ID_DIGITS 7

// Global jni variables
JavaVM* g_vm;
JNIEnv* g_env;
jobject g_obj;
jclass g_objClass;
int g_numCallbackThreads = 0;

// Java callbacks
jmethodID g_onExecutionReportCallback;
jmethodID g_onSessionStateCallback;
jmethodID g_onEndRecoveryCallback;

bool stop = false;

class JEnvThread {
private:
    JNIEnv* m_env = NULL;
    int m_threadId = 0;

public:
    JEnvThread() {
        m_threadId = g_numCallbackThreads++;
        g_vm->AttachCurrentThread((void**)&m_env, NULL);
    }

    ~JEnvThread() {
        g_vm->DetachCurrentThread();
    }

    inline JNIEnv* getEnv() {
        return m_env;
    }

    inline int getThreadId() const {
        return m_threadId;
    }
};

std::map<pthread_t, JEnvThread*> g_envThreads;

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

void* g_dllHandle = 0;
send_func g_send;
load_func g_load ;
unload_func g_unload;
void* g_connector = 0;

char* g_user = 0;
char* g_account = 0;

int g_year;
int g_month;
int g_day;

bool g_warmup = false;

char g_securitiesBySid[MAX_SECURITY_SID + 1][MAX_SID_DIGITS + 1];
char g_preAllocatedClientOrderIds[MAX_NUM_ORDERS][MAX_ORDER_ID_DIGITS + 1];

struct MessageProcessingInfo {
    struct timeval start;
    struct timeval end;
};

struct MessageProcessingInfo g_orderExecInfo[MAX_NUM_ORDERS];
int g_startClientOrderId = 0;

void  dump_memory(
FILE*  f,
void const * bytes, size_t len
)
{
  static char const* const hex = "0123456789abcdef";
  unsigned char* p = (unsigned char*)bytes;
  for(size_t r = len >> 3; r > 0; --r)
  {
    fprintf(f, "0x%p %c%c %c%c %c%c %c%c %c%c %c%c %c%c %c%c  |  %c%c%c%c%c%c%c%c\n", p,
      hex[(p[0] & 0xF0) >> 4],  hex[p[0] & 0xF],
      hex[(p[1] & 0xF0) >> 4],  hex[p[1] & 0xF],
      hex[(p[2] & 0xF0) >> 4],  hex[p[2] & 0xF],
      hex[(p[3] & 0xF0) >> 4],  hex[p[3] & 0xF],
      hex[(p[4] & 0xF0) >> 4],  hex[p[4] & 0xF],
      hex[(p[5] & 0xF0) >> 4],  hex[p[5] & 0xF],
      hex[(p[6] & 0xF0) >> 4],  hex[p[6] & 0xF],
      hex[(p[7] & 0xF0) >> 4],  hex[p[7] & 0xF],
      (isprint(p[0]) ? (char)p[0] : '.' ),
      (isprint(p[1]) ? (char)p[1] : '.' ),
      (isprint(p[2]) ? (char)p[2] : '.' ),
      (isprint(p[3]) ? (char)p[3] : '.' ),
      (isprint(p[4]) ? (char)p[4] : '.' ),
      (isprint(p[5]) ? (char)p[5] : '.' ),
      (isprint(p[6]) ? (char)p[6] : '.' ),
      (isprint(p[7]) ? (char)p[7] : '.' )
    );
    p += 8;
  }
  size_t n = len & 0x7;
  if(n){
    size_t tmp = n;
    fprintf(f, "0x%p", p);
    for(; n > 0; --n) {
      fprintf(f, " %c%c",   hex[(*p & 0xF0) >> 4],  hex[*p & 0xF]);
      ++p;
    }
    fprintf(f, "  |  ");
    p -= tmp;
    n = tmp;
    for(; n > 0; --n){
      fprintf(f, "%c",  (isprint(*p) ? (char)*p : '.' ));
      ++p;
    }
    fprintf(f, "\n");
  }

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

inline int decimalToInt(const EI6::Decimal64& decimal) {
    // in most (all) cases that I see, exp is 3 which is exactly what we want
    return  decimal.GetExponent() == -3 ? decimal.GetMantissa() : decimal.GetMantissa() * pow(10, 3 + decimal.GetExponent());
    //static int pow10[4] = {1, 10, 100, 1000};
    //int exp = 3 + decimal.GetExponent();
    //return exp >= 0 ? decimal.GetMantissa() * pow10[exp] : decimal.GetMantissa() * pow(10, exp);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved)
{
    g_vm = jvm;
    return JNI_VERSION_1_8;
}


void buildSecuritiesMap() {
    std::cout << "[INFO] Building securities map..." << std::endl;
    for (int i = 0; i <= MAX_SECURITY_SID; i++) {
        sprintf(g_securitiesBySid[i], "%d", i);
    }
}

void buildClientOrderIds(int startClientOrderId) {
    std::cout << "[INFO] Building client order ids..." << std::endl;
    for (int i = 0; i < MAX_NUM_ORDERS; i++) {
        sprintf(g_preAllocatedClientOrderIds[i], "%d", (startClientOrderId + i));
    }
}

JNIEXPORT jint JNICALL Java_com_lunar_order_hkex_ocg_CtpOcgApi_initialize(JNIEnv* env, jobject obj, jstring connectorFile, jstring configFile, jstring user, jstring account, jint date, jint startClientOrderId) {
    const char* tmpStr = env->GetStringUTFChars(user, 0);
    g_user = new char[strlen(tmpStr) + 1];
    strcpy(g_user, tmpStr);
    env->ReleaseStringUTFChars(user, tmpStr);

    tmpStr = env->GetStringUTFChars(account, 0);
    g_account = new char[strlen(tmpStr) + 1];
    strcpy(g_account, tmpStr);
    env->ReleaseStringUTFChars(account, tmpStr);

    int iDate = date;
    g_day = iDate % 100;
    iDate = iDate - g_day;
    g_year = iDate / 10000;
    g_month = (iDate - g_year * 10000) / 100;

    std::cout << "[INFO] Setting up for user " << g_user << " and account " << g_account << " for year " << g_year << " month " << g_month << " day " << g_day << "..." << std::endl;
    g_startClientOrderId = startClientOrderId;
    std::cout << "[INFO] Start client order id: " << g_startClientOrderId << std::endl;
    for (int i = 0; i < MAX_NUM_ORDERS; i++){
        g_orderExecInfo[i].start.tv_sec = 0;
        g_orderExecInfo[i].start.tv_usec = 0;
        g_orderExecInfo[i].end.tv_sec = 0;
        g_orderExecInfo[i].end.tv_usec = 0;
    }

    buildSecuritiesMap();
    buildClientOrderIds(g_startClientOrderId);

    if (g_warmup) {
        return 0;
    }

    g_env = env;
    g_obj = env->NewGlobalRef(obj);
    std::cout << "[INFO] Finding Java class com/lunar/order/hkex/ocg/CtpOcgApi..." << std::endl;
    g_objClass = env->FindClass("com/lunar/order/hkex/ocg/CtpOcgApi");
    if (!g_objClass) {
        std::cerr << "[ERROR] Java class CapitalFuture not found..." << std::endl;
        return -1;
    }
    std::cout << "[INFO] Finding Java method onExecutionReport..." << std::endl;
    g_onExecutionReportCallback = env->GetMethodID(g_objClass, "onExecutionReport", "(IILjava/lang/String;JIICJIIIIILjava/lang/String;IILjava/lang/String;Z)I");
    if (!g_onExecutionReportCallback) {
        std::cerr << "[ERROR] Java method onExecutionReport not found..." << std::endl;
        return -1;
    }
    std::cout << "[INFO] Finding Java method onSessionState..." << std::endl;
    g_onSessionStateCallback = env->GetMethodID(g_objClass, "onSessionState", "(II)I");
    if (!g_onSessionStateCallback) {
        std::cerr << "[ERROR] Java method onSessionState not found..." << std::endl;
        return -1;
    }
    std::cout << "[INFO] Finding Java method onEndRecovery..." << std::endl;
    g_onEndRecoveryCallback = env->GetMethodID(g_objClass, "onEndRecovery", "(I)I");
    if (!g_onEndRecoveryCallback) {
        std::cerr << "[ERROR] Java method onEndRecovery not found..." << std::endl;
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

    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_order_hkex_ocg_CtpOcgApi_close(JNIEnv* env, jobject obj) {
    if (g_account) {
        delete [] g_account;
        g_account = 0;
    }
    if (g_user) {
        delete [] g_user;
        g_user = 0;
    }
    
    if (g_warmup) {
        return 0;
    }

    if (g_connector) {
        g_unload(g_connector);
        g_connector = 0;
    }
    if (g_dllHandle) {
        dlclose(g_dllHandle);
        g_dllHandle = 0;
    }

    g_env->DeleteGlobalRef(g_obj);

    return 0;
}

EI6::DateTime64 convertTime(const int64_t& nanoOfDay) {
    unsigned int milli = nanoOfDay % 1000000000L;
    unsigned int sec = nanoOfDay / 1000000000L;
    milli = milli / 1000000;
    unsigned int hour = sec / 3600;
    sec = sec - hour * 3600;
    unsigned int min = sec / 60;
    sec = sec - min * 60;
    return EI6::DateTime64(g_year, g_month, g_day, hour, min, sec, milli);
}

JNIEXPORT jint JNICALL Java_com_lunar_order_hkex_ocg_CtpOcgApi_setMode(JNIEnv* env, jobject obj, jboolean warmup) {
    g_warmup = warmup;
    return 0;
}


JNIEXPORT jint JNICALL Java_com_lunar_order_hkex_ocg_CtpOcgApi_endRecovery(JNIEnv* env, jobject obj, jlong nanoOfDay) {
    if (g_warmup)
        return 0;
    EI6::EndRecovery request;
    request.set_DateTime(convertTime(nanoOfDay));

    std::cout << "[INFO] Sending end recovery request..." << std::endl;
    g_send(g_connector, request.data(), request.size());
    std::cout << "[INFO] End recovery done!" << std::endl;

    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_order_hkex_ocg_CtpOcgApi_addOrder(JNIEnv* env, jobject obj, jlong nanoOfDay, jint clientOrderId, jlong secSid, jint side, jint orderType, jint timeInForce, jint price, jint quantity, jboolean isEnhanced) {
    if (secSid < 0 || secSid > MAX_SECURITY_SID) {    
        std::cout << "Cannot find security with sid " << secSid << std::endl;
        return -1;
    }

    const char* cSecurity = g_securitiesBySid[secSid];

    EI6::Decimal64 decPrice = EI6::Decimal64(price, -3);

    EI6::NewOrderSingle request;

    request.set_BuySellCode(side);
    request.set_OrderTypeCode(orderType);
    request.set_TimeInForceCode(timeInForce);
    if(EI6::OrderTypeCode::MARKET != orderType)
        request.set_OrderPrice(decPrice);

    request.set_OrderQuantity(quantity);
    request.set_ProductID(cSecurity);
    request.set_Account(g_account);
    request.set_DateTime(convertTime(nanoOfDay));

    if (!isEnhanced) {
        request.set_Text("\0011090=1\001");
    }

    if (!g_warmup){
        int clientOrderIdIndex = clientOrderId - g_startClientOrderId;
        if (clientOrderIdIndex >= 0 && clientOrderIdIndex < MAX_NUM_ORDERS){
            const char *cClientOrderId = g_preAllocatedClientOrderIds[clientOrderIdIndex];
            request.set_ClientOrderID(cClientOrderId);
            gettimeofday(&(g_orderExecInfo[clientOrderIdIndex].start),NULL);
            g_send(g_connector, request.data(), request.size());
            gettimeofday(&(g_orderExecInfo[clientOrderIdIndex].end),NULL);
        }
        else {
            // If clientOrderId is out of our predefined range, we won't collect any performance data
            const char *cClientOrderId = std::to_string(clientOrderId).c_str();
            request.set_ClientOrderID(cClientOrderId);
            g_send(g_connector, request.data(), request.size());
        }
    }

    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_order_hkex_ocg_CtpOcgApi_cancelOrder(JNIEnv* env, jobject obj, jlong nanoOfDay, jstring clientOrderId, jlong secSid, jint side) {
    if (secSid < 0 || secSid > MAX_SECURITY_SID) {
        std::cout << "Cannot find security with sid " << secSid << std::endl;
        return -1;
    }
    const char* cSecurity = g_securitiesBySid[secSid];
    const char* cClientOrderId = env->GetStringUTFChars(clientOrderId, 0);

    EI6::OrderCancelRequest request;
    request.set_ClientOrderID(cClientOrderId);
    request.set_BuySellCode(side);
    request.set_ProductID(cSecurity);
    request.set_DateTime(convertTime(nanoOfDay));

    if (!g_warmup) {
        std::cout << "[INFO] Sending cancel" << cClientOrderId << "..." << std::endl;
        g_send(g_connector, request.data(), request.size());
        std::cout << "[INFO] Cancel sent!" << std::endl;
    }
    env->ReleaseStringUTFChars(clientOrderId, cClientOrderId);
    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_order_hkex_ocg_CtpOcgApi_massCancelAll(JNIEnv* env, jobject obj, jlong nanoOfDay) {
    EI6::OrderCancelRequest request;
    std::string lText("\001");
    lText.append("1101=7");
    lText.append(1, '\001');
    request.set_Text(lText.c_str());
    request.set_DateTime(convertTime(nanoOfDay));

    if (!g_warmup) {
        std::cout << "[INFO] Sending mass cancel" << "..." << std::endl;
        g_send(g_connector, request.data(), request.size());
        std::cout << "[INFO] Mass cancel sent!" << std::endl;
    }
    return 0;
}

JNIEXPORT jint JNICALL Java_com_lunar_order_hkex_ocg_CtpOcgApi_massCancelBySecurity(JNIEnv* env, jobject obj, jlong nanoOfDay, jlong secSid) {
    if (secSid < 0 || secSid > MAX_SECURITY_SID) {
        std::cout << "Cannot find security with sid " << secSid << std::endl;
        return -1;
    }
    const char* cSecurity = g_securitiesBySid[secSid];

    EI6::OrderCancelRequest request;
    request.set_ProductID(cSecurity);
    request.set_DateTime(convertTime(nanoOfDay));

    if (!g_warmup) {
        std::cout << "[INFO] Sending mass cancel for " << cSecurity << "..." << std::endl;
        g_send(g_connector, request.data(), request.size());
        std::cout << "[INFO] Mass cancel for security sent!" << std::endl;
    }
    return 0;

}

JNIEXPORT jint JNICALL Java_com_lunar_order_hkex_ocg_CtpOcgApi_massCancelBySide(JNIEnv* env, jobject obj, jlong nanoOfDay, jint side) {
    EI6::OrderCancelRequest request;
    request.set_BuySellCode(side);
    request.set_DateTime(convertTime(nanoOfDay));

    if (!g_warmup) {
        std::cout << "[INFO] Sending mass cancel for side " << side << "..." << std::endl;
        g_send(g_connector, request.data(), request.size());
        std::cout << "[INFO] Mass cancel for side sent!" << std::endl;
    }
    return 0;

}

JNIEXPORT void JNICALL Java_com_lunar_order_hkex_ocg_CtpOcgApi_displayOrderInfo(JNIEnv * env, jobject obj, jint clientOrderId){
    int index = clientOrderId - g_startClientOrderId;
    if (index >= 0 && index < MAX_NUM_ORDERS){
        struct MessageProcessingInfo& info = g_orderExecInfo[index];
        time_t t = info.start.tv_sec;
        struct tm* n = gmtime(&t);
        std::cout << "[INFO] Sending order " << clientOrderId << " at ";
        std::cout << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." <<  std::setfill('0') << std::setw(6) << info.start.tv_usec << std::endl;

        t = info.end.tv_sec;
        n = gmtime(&t);
        std::cout << "[INFO] Order " << clientOrderId << " sent at ";
        std::cout << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." <<  std::setfill('0') << std::setw(6) << info.end.tv_usec << std::endl;
    }
}

JNIEXPORT void JNICALL Java_com_lunar_order_hkex_ocg_CtpOcgApi_displayAllOrderInfo(JNIEnv *env, jobject obj){
    std::cout << "Display all order info" << std::endl;
    for (int i = 0; i < MAX_NUM_ORDERS; i++){
        struct MessageProcessingInfo& info = g_orderExecInfo[i];
        time_t t = info.start.tv_sec;
        if (t != 0){
            struct tm* n = gmtime(&t);
            std::cout << "[INFO] Sending order " << (i + g_startClientOrderId) << " at ";
            std::cout << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." <<  std::setfill('0') << std::setw(6) << info.start.tv_usec << std::endl;

            t = info.end.tv_sec;
            n = gmtime(&t);
            std::cout << "[INFO] Order " << (i + g_startClientOrderId) << " sent at ";
            std::cout << std::setfill('0') << std::setw(2) << n->tm_hour + 8 << ":" << std::setfill('0') << std::setw(2) << n->tm_min << ":" << std::setfill('0') << std::setw(2) << n->tm_sec << "." <<  std::setfill('0') << std::setw(6) << info.end.tv_usec << std::endl;
        }
    }
}

void received(void* ud, const void* data, unsigned int size){
    struct timeval receivetv;
    gettimeofday(&receivetv, NULL);
    pthread_t pthreadId = pthread_self();
    std::map<pthread_t, JEnvThread*>::iterator iEnvThread = g_envThreads.find(pthreadId);
    JEnvThread* envThread;
    if (iEnvThread == g_envThreads.end()) {
        envThread = new JEnvThread();
        g_envThreads[pthreadId] = envThread;
    }
    else {
        envThread = iEnvThread->second;
    }
    JNIEnv* jenv = envThread->getEnv();
    if (jenv == 0) {
        std::cerr << "[ERROR] Cannot get env for callback thread..." << std::endl;
        return;
    }

    //std::cout << "Received message..." << std::endl;
    //dump_memory(stdout, data, size);

    //messages from ei6 connector
    typedef EI6::DataType::Type msg_type; //ei6 head file: const_def
    msg_type type;
    if (size) {
        type = *(msg_type const*)data;
    }
    else {
        type = ((packed_stream*)data)->type_;
    }
    if(type == EI6::DataType::EnumExecutionReport){
        EI6::ExecutionReport msg(data, size);
        if(!msg.has_InSyncFlag()) {
            if (msg.has_ClientOrderID()) {
                int clientId = strToPosInt(msg.get_ClientOrderID());
                if (clientId != -1) {
                    jstring jExchangeOrderId = msg.has_ExchangePlatformOrderID() ? jenv->NewStringUTF(msg.get_ExchangePlatformOrderID()) : 0;
                    jstring jErrorText = 0;
                    if (msg.has_ErrorText()) {
                        jErrorText = jenv->NewStringUTF(msg.get_ErrorText());
                    }
                    jlong dateTime = 0;
                    if (msg.has_DateTime()) {
                        EI6::DateTime64 dateTime64 = msg.get_DateTime();
                        dateTime = (int64_t)((dateTime64.GetHour() + 8) * 60 * 60 + dateTime64.GetMinute() * 60 + dateTime64.GetSecond()) * 1000000000L + (int64_t)(dateTime64.GetMillisecond()) * 1000000L + (int64_t)(dateTime64.GetMicrosecond()) * 1000L + (int64_t)dateTime64.GetNanosecond();
                    }
                    jint execTypeCode = msg.has_ExecTypeCode() ? msg.get_ExecTypeCode() : 0;
                    jint execTransTypeCode = msg.has_ExecTransTypeCode() ? msg.get_ExecTransTypeCode() : 0;
                    jchar orderStatusCode = msg.has_OrderStatusCode() ? msg.get_OrderStatusCode() : '0';
                    jlong secSid = -1;
                    if (msg.has_ProductID()) {
                        secSid = strToPosInt(msg.get_ProductID());
                    }
                    else {
                        std::cout << "[WARN] Execution report has no product id..." << std::endl;
                    }
                    jint orderTotalTradeQuantity = msg.has_OrderTotalTradeQuantity() ? msg.get_OrderTotalTradeQuantity() : 0;
                    jint leavesQuantity = msg.has_LeavesQuantity() ? msg.get_LeavesQuantity() : 0;
                    jint orderPrice = msg.has_OrderPrice() ? decimalToInt(msg.get_OrderPrice()) : 0;
                    jint orderQuantity = msg.has_OrderQuantity() ? msg.get_OrderQuantity() : 0;
                    jstring jExecutionId = msg.has_ExecutionID() ? jenv->NewStringUTF(msg.get_ExecutionID()) : 0;
                    jint tradeQuantity = msg.has_TradeQuantity() ? msg.get_TradeQuantity() : 0;
                    jint tradePrice = msg.has_TradePrice() ? decimalToInt(msg.get_TradePrice()) : 0;
                    jboolean inSyncFlag = msg.has_InSyncFlag() ? msg.get_InSyncFlag() : 0;
                    jint buySellCode = msg.has_BuySellCode() ? msg.get_BuySellCode() : 0;
                    jenv->CallObjectMethod(g_obj, g_onExecutionReportCallback, envThread->getThreadId(), clientId, jExchangeOrderId, dateTime, execTypeCode, execTransTypeCode, orderStatusCode, secSid, buySellCode, orderPrice, orderQuantity, orderTotalTradeQuantity, leavesQuantity, jExecutionId, tradeQuantity, tradePrice, jErrorText, inSyncFlag);
                }
                else {
                    std::cout << "[ERROR] Failed to handle execution report " << msg.get_ClientOrderID() << std::endl;
                }
            }
            else {
                std::cout << "[ERROR] Received an execution report with no client order id..." << std::endl;
            }
        }
        else if (msg.get_InSyncFlag()) {
            std::cout << "[INFO] Received in sync flag..." << std::endl;
            jenv->CallObjectMethod(g_obj, g_onEndRecoveryCallback, envThread->getThreadId());
        }
    }
    else if(type == EI6::DataType::EnumSessionStateEvent){
        EI6::SessionStateEvent msg(data, size);
        jint sessionState = msg.has_SessionState() ? msg.get_SessionState() : 0;
        std::cout << "[INFO] Received session state for " << sessionState << "..." << std::endl;
        jenv->CallObjectMethod(g_obj, g_onSessionStateCallback, envThread->getThreadId(), sessionState);
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
    //else if(type == EI6::DataType::EnumTradingPhase){
    //    EI6::TradingPhase msg(data, size);
    //}
    //else if(type == EI6::DataType::EnumMarket){
    //    EI6::Market msg(data, size);
    //}
    //else if(type == EI6::DataType::EnumCreateStrategyReport){
    //    EI6::CreateStrategyReport msg(data, size);
    //}
    //else if(type == EI6::DataType::EnumOrderCancelReject){
    //    EI6::OrderCancelReject msg(data, size);
    //}
    //else if(type == EI6::DataType::EnumEnterQuoteResponse){
    //    EI6::EnterQuoteResponse msg(data, size);
    //}
    //else if(type == EI6::DataType::EnumCancelQuoteResponse){
    //    EI6::CancelQuoteResponse msg(data, size);
    //}
    //else if(type == EI6::DataType::EnumQuoteSideReport){
    //    EI6::QuoteSideReport msg(data, size);
    //}
    //else if(type == EI6::DataType::EnumEndRecovery){
    //    EI6::EndRecovery msg(data, size);
    //}
    //else
    //{
    //assert(0);
    //}
}

