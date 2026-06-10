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

package android.app.cts.broadcasts;

import static com.google.common.truth.Truth.assertThat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import com.android.server.am.Flags;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BroadcastReceiverRegistrationTest extends BaseBroadcastTest {
    private static final String TAG = "BroadcastReceiverRegistrationTest";

    private static final String LOCAL_BROADCAST_ACTION =
            "com.android.app.cts.broadcasts.action.LOCAL";

    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testPriorityValueUnchanged() {
        testPriorityValue(10 /* registeredPriority */, 10 /* expectedPriority */);
    }

    @RequiresFlagsEnabled(Flags.FLAG_RESTRICT_PRIORITY_VALUES)
    @Test
    public void testPriorityValueRestricted_withSystemHighPriority() {
        testPriorityValue(IntentFilter.SYSTEM_HIGH_PRIORITY, IntentFilter.SYSTEM_HIGH_PRIORITY - 1);
    }

    @RequiresFlagsEnabled(Flags.FLAG_RESTRICT_PRIORITY_VALUES)
    @Test
    public void testPriorityValueRestricted_withSystemLowPriority() {
        testPriorityValue(IntentFilter.SYSTEM_LOW_PRIORITY, IntentFilter.SYSTEM_LOW_PRIORITY + 1);
    }

    @Test
    public void testReceiverOrder() throws Exception {
        final CompletableFuture<Long> lowPriorityReceiverFuture =
                registerReceiverToCaptureReceiptTimestamp(LOCAL_BROADCAST_ACTION, 0);
        final CompletableFuture<Long> highPriorityReceiverFuture =
                registerReceiverToCaptureReceiptTimestamp(LOCAL_BROADCAST_ACTION, 20);

        final Intent intent = new Intent(LOCAL_BROADCAST_ACTION)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .setPackage(mContext.getPackageName());
        mContext.sendBroadcast(intent);

        final long highPriorityReceiverReceiptTimeMs = highPriorityReceiverFuture.get(
                BROADCAST_RECEIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        final long lowPriorityReceiverReceiptTimeMs = lowPriorityReceiverFuture.get(
                BROADCAST_RECEIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertThat(highPriorityReceiverReceiptTimeMs)
                .isLessThan(lowPriorityReceiverReceiptTimeMs);
    }

    private CompletableFuture<Long> registerReceiverToCaptureReceiptTimestamp(String action,
            int priority) {
        final CompletableFuture<Long> completableFuture = new CompletableFuture<>();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Received intent: " + intent);
                if (action.equals(intent.getAction())) {
                    completableFuture.complete(SystemClock.elapsedRealtime());
                    // Add a small delay to ensure the receivers have different receipt timestamps.
                    SystemClock.sleep(10);
                }
                mContext.unregisterReceiver(this);
            }
        };
        final IntentFilter filter = new IntentFilter(action);
        filter.setPriority(priority);
        mContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        return completableFuture;
    }

    private void testPriorityValue(int registeredPriority, int expectedPriority) {
        final IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.setPriority(registeredPriority);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Ignore
            }
        };
        mContext.registerReceiver(receiver, filter);
        final List<IntentFilter> filters = mContext.getRegisteredIntentFilters(receiver);
        assertThat(filters).hasSize(1);
        assertThat(filters.get(0).getPriority()).isEqualTo(expectedPriority);
    }
}
