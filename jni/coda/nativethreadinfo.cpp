#include <jni.h>
#include <syscall.h>
#include <unistd.h>
#include "com_lunar_util_NativeThreadInfo.h"

JNIEXPORT jint JNICALL Java_com_lunar_util_NativeThreadInfo_getTid(JNIEnv *env, jclass obj) {
    jint tid = syscall(__NR_gettid);
    return tid;
}

