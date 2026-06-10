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

package com.android.compatibility.common.util.concurrentuser;

import static com.android.compatibility.common.util.concurrentuser.ConcurrentUserActivityUtils.BROADCAST_ACTION_TRIGGER;
import static com.android.compatibility.common.util.concurrentuser.ConcurrentUserActivityUtils.KEY_BUNDLE;
import static com.android.compatibility.common.util.concurrentuser.ConcurrentUserActivityUtils.KEY_CALLBACK;
import static com.android.compatibility.common.util.concurrentuser.ConcurrentUserActivityUtils.KEY_USER_ID;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.util.Log;

import androidx.annotation.WorkerThread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A test {@link Activity} with capability of cross-user connections.
 *
 * There are two users involved in concurrent user testing: the initiator user (such as user 10 on
 * AAOS) and the responder user (such as user 11 on AAOS). The test runs as the initiator user,
 * and it can communicate with the same testing app running as the responder user through
 * {@link ConcurrentUserActivityUtils#sendBundleAndWaitForReply} and {@link #onBundleReceived}.
 * This activity can be launched by testing framework as the initiator user, or by
 * {@link ConcurrentUserActivityUtils#launchActivityAsUserSync} as the responder user.
 */
public abstract class ConcurrentUserActivityBase extends Activity {

    private static final String TAG = ConcurrentUserActivityBase.class.getSimpleName();

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    // This field is accessed on the main thread only.
    private boolean mReceiverRegistered;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle extras = intent.getExtras();
            if (extras == null) {
                Log.e(TAG, "No extras in the Broadcast");
                return;
            }
            Bundle receivedBundle = extras.getBundle(KEY_BUNDLE);
            if (receivedBundle == null) {
                Log.e(TAG, "Empty Bundle in the Broadcast");
                return;
            }
            RemoteCallback callback = intent.getParcelableExtra(KEY_CALLBACK);
            if (callback == null) {
                Log.e(TAG, "Null callback in the Broadcast");
                return;
            }
            mExecutor.execute(() -> {
                Bundle replyBundle = onBundleReceived(receivedBundle);
                callback.sendResult(replyBundle);
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RemoteCallback callback = getIntent().getParcelableExtra(KEY_CALLBACK);
        if (callback == null) {
            // This activity is launched as the initiator user, so nothing needs to be done.
            return;
        }
        // This activity is launched as the responder user, so it needs a BroadcastReceiver to
        // receive Broadcasts from the initiator app.
        registerReceiver(mBroadcastReceiver, new IntentFilter(BROADCAST_ACTION_TRIGGER),
                RECEIVER_EXPORTED);
        mReceiverRegistered = true;
        Bundle reply = new Bundle();
        reply.putInt(KEY_USER_ID, android.os.Process.myUserHandle().getIdentifier());
        callback.sendResult(reply);
    }

    @Override
    protected void onDestroy() {
        if (mReceiverRegistered) {
            unregisterReceiver(mBroadcastReceiver);
        }
        mExecutor.shutdown();
        super.onDestroy();
    }

    /**
     * Invoked (on a worker thread) when this Activity (running as the responder user) receives a
     * {@code bundle} from the initiator user. Returns a Bundle to reply.
     */
    @WorkerThread
    protected abstract Bundle onBundleReceived(Bundle bundle);
}
