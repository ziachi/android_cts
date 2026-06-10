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

package com.android.bedstead.nene.utils;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.android.bedstead.nene.TestApis;

import java.util.UUID;

/**
 * Provider of a blocking version of {@link PendingIntent}.
 */
public class BlockingPendingIntent implements AutoCloseable {

    private PendingIntent mPendingIntent;
    private BlockingBroadcastReceiver mBlockingBroadcastReceiver;

    private BlockingPendingIntent() {
    }

    /** Create and register a {@link BlockingPendingIntent}. */
    public static BlockingPendingIntent getBroadcast() {
        BlockingPendingIntent blockingPendingIntent = new BlockingPendingIntent();
        blockingPendingIntent.register();
        return blockingPendingIntent;
    }

    private void register() {
        String intentAction = UUID.randomUUID().toString();
        mPendingIntent = PendingIntent.getBroadcast(TestApis.context().instrumentedContext(), 0,
                new Intent(intentAction), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        mBlockingBroadcastReceiver = new BlockingBroadcastReceiver(
                TestApis.context().instrumentedContext());
        TestApis.context().instrumentedContext().registerReceiver(mBlockingBroadcastReceiver,
                new IntentFilter(intentAction), Context.RECEIVER_EXPORTED);
    }

    /**
     * Wait until the broadcast and return the received broadcast intent. {@code null} is returned
     * if no broadcast with expected action is received within the given timeout.
     */
    public Intent await(long timeoutMillis) {
        return mBlockingBroadcastReceiver.awaitForBroadcast(timeoutMillis);
    }

    /** Get the pending intent. */
    public PendingIntent pendingIntent() {
        return mPendingIntent;
    }

    @Override
    public void close() throws Exception {
        if (mBlockingBroadcastReceiver != null) {
            mBlockingBroadcastReceiver.unregisterQuietly();
        }
    }
}
