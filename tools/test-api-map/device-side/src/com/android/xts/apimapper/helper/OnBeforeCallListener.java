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

package com.android.xts.apimapper.helper;

/**
 * Interface for operations before a method call.
 */
public interface OnBeforeCallListener {

    /**
     * Actions taken before a method call.
     *
     * @param callerClass The class name of the caller.
     * @param callerMethod The method name of the caller.
     * @param opcode The opcode of the call.
     * @param methodOwner The class name of the called method.
     * @param methodName The method name of the callee.
     * @param descriptor The parameter description of the callee.
     * @param callerEntity The entity object of the caller. Null for non-virtual/interface calls.
     */
    void onBeforeCall(String callerClass, String callerMethod, int opcode, String methodOwner,
            String methodName, String descriptor, Object callerEntity);
}
