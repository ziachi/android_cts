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

import java.util.Objects;

/**
 * Hook for recording potential API calls.
 */
public final class DeviceMethodCallHook {

    // Used by method calls that are not triggered in the main test process, e.g. install another
    // apk and do some actions in that apk.
    private static final DeviceApiMapperHelperRule sHelpRule =
            new DeviceApiMapperHelperRule(true);

    private static OnBeforeCallListener sListener = null;

    /** Add the given listener when the test starts. */
    public static void addOnBeforeCallListener(OnBeforeCallListener listener) {
        sListener = Objects.requireNonNull(listener);
    }

    /** Clear the given listener when the test ends. */
    public static void removeOnBeforeCallListener() {
        sListener = null;
    }

    /** Called by ASM-generated code. */
    public static void onBeforeCall(String callerClass, String callerMethod, int opcode,
            String methodOwner, String methodName, String descriptor, Object receiverObject) {
        // Log APIs called by non-test classes, e.g. android activities.
        if (sListener == null) {
            sHelpRule.logMethodCall(
                    callerClass, callerMethod, opcode,
                    methodOwner, methodName, descriptor, receiverObject);
        } else {
            sListener.onBeforeCall(callerClass, callerMethod, opcode,
                    methodOwner, methodName, descriptor, receiverObject);
        }
    }
}
