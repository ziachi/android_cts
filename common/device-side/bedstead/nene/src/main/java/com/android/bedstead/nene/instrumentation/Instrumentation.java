/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.bedstead.nene.instrumentation;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.util.Log;
import com.android.bedstead.nene.context.Context;
import com.android.bedstead.nene.exceptions.NeneException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/** Test APIs related to Instrumentation. */
public final class Instrumentation {
    public static final Instrumentation sInstance = new Instrumentation();
    private static final String TAG = "Instrumentation";
    private static final Integer BROADCAST_RECEIVE_TIMEOUT_IN_SECONDS = 60;

    private Instrumentation() {

    }

    /** Interact with instrumentation arguments. */
    public Arguments arguments() {
        return Arguments.sInstance;
    }

    /**
     * This method starts the instrumentation process for a list of {@link InstrumentationComponents}.
     * It optionally registers a broadcast receiver to listen for custom broadcasts from instrumented
     * process and blocks for [BROADCAST_RECEIVE_TIMEOUT_IN_SECONDS] seconds.
     *
     * @param components A list of {@link InstrumentationComponents} to be used for instrumentation.
     * @throws NeneException if any instrumentation process fails or if the broadcast waiting times
     *     out.
     */
    public void startInstrumentation(List<InstrumentationComponents> components) {
        android.content.Context context = Context.sInstance.instrumentationContext();
        Bundle bundle = new Bundle();
        Set<String> broadcastActions = new HashSet<>();
        int broadcastActionCount = 0;

        // Collect all unique broadcast actions and count them
        for (InstrumentationComponents component : components) {
            if (!component.getBroadcastReceiverIntentAction().isEmpty()) {
                broadcastActions.add(component.getBroadcastReceiverIntentAction());
                broadcastActionCount++;
            }
        }

        CountDownLatch latch = new CountDownLatch(broadcastActionCount);
        BroadcastReceiver receiver = null;
        if (!broadcastActions.isEmpty()) {
            // Register BroadcastReceiver
            IntentFilter filter = new IntentFilter();
            for (String action : broadcastActions) {
                filter.addAction(action);
            }

            receiver =
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(android.content.Context context, Intent intent) {
                            if (intent != null && broadcastActions.contains(intent.getAction())) {
                                Log.i(TAG, "Broadcast received: " + intent.getAction());
                                broadcastActions.remove(intent.getAction());
                                latch.countDown();
                            }
                        }
                    };

            if (VERSION.SDK_INT >= VERSION_CODES.S) {
                context
                        .getApplicationContext()
                        .registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED);
            } else {
                context.getApplicationContext().registerReceiver(receiver, filter);
            }
        }

        for (InstrumentationComponents component : components) {
            ComponentName instrumentationComponent =
                    new ComponentName(component.getPackageName(), component.getRunnerClass());
            boolean success =
                    context.startInstrumentation(instrumentationComponent, /* profileFile= */ null, bundle);
            if (!success) {
                throw new NeneException(
                        "Failed to start instrumentation for package "
                                + component.getPackageName()
                                + " with runner "
                                + component.getRunnerClass());
            }
        }

        try {
            // Wait for broadcasts to be received
            if (!latch.await(BROADCAST_RECEIVE_TIMEOUT_IN_SECONDS, SECONDS)) {
                throw new NeneException("Timeout waiting for broadcasts from instrumented processes");
            }
        } catch (InterruptedException e) {
            throw new NeneException("Interrupted while waiting for broadcasts instrumented processes", e);
        } finally {
            if (receiver != null) {
                context.getApplicationContext().unregisterReceiver(receiver);
            }
        }
    }
}
