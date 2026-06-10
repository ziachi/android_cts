/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <jni.h>
#include <sys/wait.h>

#include <cstdint>
#include <thread>

#ifdef __aarch64__

static bool global = true;

extern "C" JNIEXPORT jboolean JNICALL
Java_android_cts_tagging_memtagapp_TaggingTest_isStackMteOn(JNIEnv*) {
  alignas(16) int x = 0;
  void* p = reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(&x) + (1UL << 57));
  void* p_cpy = p;
  __builtin_arm_stg(p);
  p = __builtin_arm_ldg(p);
  __builtin_arm_stg(&x);
  return p == p_cpy;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_android_cts_tagging_memtagapp_TaggingTest_isStackMteOnThread(JNIEnv*) {
  bool ok = false;
  std::thread th([&ok] {
    ok = Java_android_cts_tagging_memtagapp_TaggingTest_isStackMteOn(nullptr);
  });
  th.join();
  return ok;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_android_cts_tagging_memtagapp_TaggingTest_hasMteTls(JNIEnv*) {
  void** dst;
  __asm__("mrs %0, TPIDR_EL0" : "=r"(dst) :);
  // -3 is TLS_SLOT_STACK_MTE
  return dst[-3] != nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_android_cts_tagging_memtagapp_TaggingTest_hasMteTlsThread(JNIEnv*) {
  bool ok = false;
  std::thread th([&ok] {
    ok = Java_android_cts_tagging_memtagapp_TaggingTest_hasMteTls(nullptr);
  });
  th.join();
  return ok;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_android_cts_tagging_memtagapp_TaggingTest_hasMteGlobalTag(JNIEnv*) {
  return (reinterpret_cast<uintptr_t>(&global) >> 56) != 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_android_cts_tagging_memtagapp_TaggingTest_mteGlobalTagCorrect(JNIEnv*) {
  void* cpy = &global;
  return __builtin_arm_ldg(cpy) == &global;
}

#endif
