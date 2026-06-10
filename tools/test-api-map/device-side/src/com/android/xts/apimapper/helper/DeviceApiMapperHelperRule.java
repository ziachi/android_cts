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

import static com.android.xts.apimapper.helper.DeviceMethodCallStats.getPackageClass;

import android.util.Log;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.HashSet;
import java.util.Set;

/**
 * A device-side junit rule used by the code generated via the apimapper tool.
 */
public final class DeviceApiMapperHelperRule implements TestRule {

    public static final String CALL_STATS_PREFIX = "#callstats:";
    public static final String CALL_LOG_PREFIX = "#call:";
    private static final String TAG = "ApiMapperHelperRule";

    private final Set<String> mLogs = new HashSet<>();
    private final boolean mLogCalls;
    private final DeviceMethodCallStats mStats = new DeviceMethodCallStats();

    private final OnBeforeCallListener mListener =
            (callerClass, callerMethod, opcode, methodOwner, methodName, descriptor, callerEntity)
                    -> {
                // Print out the method call.
                logMethodCall(
                        callerClass,
                        callerMethod,
                        opcode,
                        methodOwner,
                        methodName,
                        descriptor,
                        callerEntity
                );
                String className = callerEntity == null
                        ? methodOwner : callerEntity.getClass().getName();
                // Record the method call.
                mStats.onMethodCalled(className, methodName, descriptor);
            };

    public DeviceApiMapperHelperRule() {
        this(false);
    }

    public DeviceApiMapperHelperRule(boolean logCalls) {
        mLogCalls = logCalls;
    }

    /** Log the test information before test starts. */
    public void onBeforeTest(String className, String methodName) {
        log("Device Test started: %s#%s", className, methodName);
        startCollectingApis();
    }

    /** Log the test information after test ends. */
    public void onAfterTest(String className, String methodName) {
        finishCollectingApis(className, methodName);
        log("Device Test finished: %s#%s", className, methodName);
    }

    /** Print out the method call information to the logcat. */
    public void logMethodCall(
            String callerClass,
            String callerMethod,
            int opcode,
            String methodOwner,
            String methodName,
            String descriptor,
            Object callerEntity
    ) {
        if (!mLogCalls) {
            return;
        }
        String realClass = callerEntity == null ? methodOwner : callerEntity.getClass().getName();
        String apiSignature = String.format(
                "%s:%s:%s",
                getPackageClass(realClass),
                methodName,
                descriptor
        );
        if (mLogs.contains(apiSignature)) {
            return;
        }
        mLogs.add(apiSignature);
        log("%s %s:%s:%s:%s:%s", CALL_LOG_PREFIX, callerClass, callerMethod,
                getPackageClass(realClass), methodName, descriptor);
    }

    @Override
    public Statement apply(Statement base, Description desc) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (!desc.isSuite()) {
                    onBeforeTest(desc.getTestClass().getName(), desc.getMethodName());
                }
                try {
                    base.evaluate();
                } finally {
                    if (!desc.isSuite()) {
                        onAfterTest(desc.getTestClass().getName(), desc.getMethodName());
                    }
                }
            }
        };
    }

    @FormatMethod
    private void log(@FormatString String format, Object... args) {
        Log.i(TAG, String.format(format, args));
    }

    private void startCollectingApis() {
        DeviceMethodCallHook.addOnBeforeCallListener(mListener);
        mStats.clear();
    }

    private void finishCollectingApis(String className, String methodName) {
        DeviceMethodCallHook.removeOnBeforeCallListener();
        mStats.dump(TAG, CALL_STATS_PREFIX, className, methodName);
    }
}
