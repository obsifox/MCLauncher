#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "mlcbridge"

static jstring to_jstring(JNIEnv *env, const char *s) {
    return (*env)->NewStringUTF(env, s);
}

JNIEXPORT void JNICALL
Java_com_mclauncher_core_NativeBridge_setEnv(JNIEnv *env, jclass clazz, jstring key, jstring value) {
    const char *k = (*env)->GetStringUTFChars(env, key, NULL);
    const char *v = (*env)->GetStringUTFChars(env, value, NULL);
    if (k != NULL && v != NULL) {
        setenv(k, v, 1);
    }
    if (k != NULL) (*env)->ReleaseStringUTFChars(env, key, k);
    if (v != NULL) (*env)->ReleaseStringUTFChars(env, value, v);
}

JNIEXPORT jstring JNICALL
Java_com_mclauncher_core_NativeBridge_getEnv(JNIEnv *env, jclass clazz, jstring key) {
    const char *k = (*env)->GetStringUTFChars(env, key, NULL);
    const char *v = k != NULL ? getenv(k) : NULL;
    jstring result = to_jstring(env, v != NULL ? v : "");
    if (k != NULL) (*env)->ReleaseStringUTFChars(env, key, k);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_mclauncher_core_NativeBridge_getAbiCode(JNIEnv *env, jclass clazz) {
#if defined(__aarch64__)
    return 64;
#elif defined(__x86_64__)
    return 65;
#else
    return 32;
#endif
}

JNIEXPORT void JNICALL
Java_com_mclauncher_core_NativeBridge_logNative(JNIEnv *env, jclass clazz, jstring message) {
    const char *m = (*env)->GetStringUTFChars(env, message, NULL);
    if (m != NULL) {
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s", m);
        (*env)->ReleaseStringUTFChars(env, message, m);
    }
}
