//
// Created by Shrek on 2018/7/21.
//

// This file binds the native image utility code to the Java class
// which exposes them.

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "rgb2yuv.h"
#include "yuv2rgb.h"

#define IMAGEUTILS_METHOD(METHOD_NAME) Java_com_hci_shrek_lipcmd_1v01_ImageUtils_##METHOD_NAME

#ifdef __cplusplus
extern "C" {
#endif

//JNIEXPORT void JNICALL convertYUV420SPToARGB8888(
//    JNIEnv* env, jclass clazz, jbyteArray input, jintArray output,
//    jint width, jint height, jboolean halfSize);

JNIEXPORT void JNICALL IMAGEUTILS_METHOD(convertYUV420ToARGB8888)(
    JNIEnv* env, jclass clazz, jbyteArray y, jbyteArray u, jbyteArray v,
    jintArray output, jint width, jint height, jint y_row_stride,
    jint uv_row_stride, jint uv_pixel_stride, jboolean halfSize);

JNIEXPORT jstring JNICALL IMAGEUTILS_METHOD(stringFromJNI)(JNIEnv* env, jclass clazz);

//JNIEXPORT void JNICALL convertYUV420SPToRGB565(
//    JNIEnv* env, jclass clazz, jbyteArray input, jbyteArray output, jint width,
//    jint height);
//
//JNIEXPORT void JNICALL convertARGB8888ToYUV420SP(
//    JNIEnv* env, jclass clazz, jintArray input, jbyteArray output,
//    jint width, jint height);
//
//JNIEXPORT void JNICALL convertRGB565ToYUV420SP(
//    JNIEnv* env, jclass clazz, jbyteArray input, jbyteArray output,
//    jint width, jint height);

#ifdef __cplusplus
}
#endif

//JNIEXPORT void JNICALL convertYUV420SPToARGB8888(
//    JNIEnv* env, jclass clazz, jbyteArray input, jintArray output,
//    jint width, jint height, jboolean halfSize) {
//  jboolean inputCopy = JNI_FALSE;
//  jbyte* const i = env->GetByteArrayElements(input, &inputCopy);
//
//  jboolean outputCopy = JNI_FALSE;
//  jint* const o = env->GetIntArrayElements(output, &outputCopy);
//
//  if (halfSize) {
//    ConvertYUV420SPToARGB8888HalfSize(reinterpret_cast<uint8_t*>(i),
//                                      reinterpret_cast<uint32_t*>(o), width,
//                                      height);
//  } else {
//    ConvertYUV420SPToARGB8888(reinterpret_cast<uint8_t*>(i),
//                              reinterpret_cast<uint8_t*>(i) + width * height,
//                              reinterpret_cast<uint32_t*>(o), width, height);
//  }
//
//  env->ReleaseByteArrayElements(input, i, JNI_ABORT);
//  env->ReleaseIntArrayElements(output, o, 0);
//}

JNIEXPORT void JNICALL IMAGEUTILS_METHOD(convertYUV420ToARGB8888)(
    JNIEnv* env, jclass clazz, jbyteArray y, jbyteArray u, jbyteArray v,
    jintArray output, jint width, jint height, jint y_row_stride,
    jint uv_row_stride, jint uv_pixel_stride, jboolean halfSize) {
  jboolean inputCopy = JNI_FALSE;
  jbyte* const y_buff = env->GetByteArrayElements(y, &inputCopy);
  jboolean outputCopy = JNI_FALSE;
  jint* const o = env->GetIntArrayElements(output, &outputCopy);

  if (halfSize) {
    ConvertYUV420SPToARGB8888HalfSize(reinterpret_cast<uint8_t*>(y_buff),
                                      reinterpret_cast<uint32_t*>(o), width,
                                      height);
  } else {
    jbyte* const u_buff = env->GetByteArrayElements(u, &inputCopy);
    jbyte* const v_buff = env->GetByteArrayElements(v, &inputCopy);

    ConvertYUV420ToARGB8888(
        reinterpret_cast<uint8_t*>(y_buff), reinterpret_cast<uint8_t*>(u_buff),
        reinterpret_cast<uint8_t*>(v_buff), reinterpret_cast<uint32_t*>(o),
        width, height, y_row_stride, uv_row_stride, uv_pixel_stride);

    env->ReleaseByteArrayElements(u, u_buff, JNI_ABORT);
    env->ReleaseByteArrayElements(v, v_buff, JNI_ABORT);
  }

  env->ReleaseByteArrayElements(y, y_buff, JNI_ABORT);
  env->ReleaseIntArrayElements(output, o, 0);
}

JNIEXPORT jstring JNICALL IMAGEUTILS_METHOD(stringFromJNI)(JNIEnv* env, jclass clazz)
{
#if defined(__arm__)
#if defined(__ARM_ARCH_7A__)
#if defined(__ARM_NEON__)
#if defined(__ARM_PCS_VFP)
#define ABI "armeabi-v7a/NEON (hard-float)"
#else
#define ABI "armeabi-v7a/NEON"
#endif
#else
    #if defined(__ARM_PCS_VFP)
        #define ABI "armeabi-v7a (hard-float)"
      #else
        #define ABI "armeabi-v7a"
      #endif
#endif
#else
#define ABI "armeabi"
#endif
#elif defined(__i386__)
    #define ABI "x86"
#elif defined(__x86_64__)
#define ABI "x86_64"
#elif defined(__mips64)  /* mips64el-* toolchain defines __mips__ too */
#define ABI "mips64"
#elif defined(__mips__)
#define ABI "mips"
#elif defined(__aarch64__)
#define ABI "arm64-v8a"
#else
#define ABI "unknown"
#endif

    return env->NewStringUTF("Hello from JNI !  Compiled with ABI " ABI ".");
}

//JNIEXPORT void JNICALL convertYUV420SPToRGB565(
//    JNIEnv* env, jclass clazz, jbyteArray input, jbyteArray output, jint width,
//    jint height) {
//  jboolean inputCopy = JNI_FALSE;
//  jbyte* const i = env->GetByteArrayElements(input, &inputCopy);
//
//  jboolean outputCopy = JNI_FALSE;
//  jbyte* const o = env->GetByteArrayElements(output, &outputCopy);
//
//  ConvertYUV420SPToRGB565(reinterpret_cast<uint8_t*>(i),
//                          reinterpret_cast<uint16_t*>(o), width, height);
//
//  env->ReleaseByteArrayElements(input, i, JNI_ABORT);
//  env->ReleaseByteArrayElements(output, o, 0);
//}
//
//JNIEXPORT void JNICALL convertARGB8888ToYUV420SP(
//    JNIEnv* env, jclass clazz, jintArray input, jbyteArray output,
//    jint width, jint height) {
//  jboolean inputCopy = JNI_FALSE;
//  jint* const i = env->GetIntArrayElements(input, &inputCopy);
//
//  jboolean outputCopy = JNI_FALSE;
//  jbyte* const o = env->GetByteArrayElements(output, &outputCopy);
//
//  ConvertARGB8888ToYUV420SP(reinterpret_cast<uint32_t*>(i),
//                            reinterpret_cast<uint8_t*>(o), width, height);
//
//  env->ReleaseIntArrayElements(input, i, JNI_ABORT);
//  env->ReleaseByteArrayElements(output, o, 0);
//}
//
//JNIEXPORT void JNICALL convertRGB565ToYUV420SP(
//    JNIEnv* env, jclass clazz, jbyteArray input, jbyteArray output,
//    jint width, jint height) {
//  jboolean inputCopy = JNI_FALSE;
//  jbyte* const i = env->GetByteArrayElements(input, &inputCopy);
//
//  jboolean outputCopy = JNI_FALSE;
//  jbyte* const o = env->GetByteArrayElements(output, &outputCopy);
//
//  ConvertRGB565ToYUV420SP(reinterpret_cast<uint16_t*>(i),
//                          reinterpret_cast<uint8_t*>(o), width, height);
//
//  env->ReleaseByteArrayElements(input, i, JNI_ABORT);
//  env->ReleaseByteArrayElements(output, o, 0);
//}