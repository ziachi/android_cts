/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telecom.cts.screeningtestapp;

import static android.telecom.TelecomManager.EXTRA_DISCONNECT_CAUSE;
import static android.telecom.TelecomManager.EXTRA_HANDLE;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CtsPostCallActivity extends Activity {
    private static final String TAG = CtsPostCallActivity.class.getSimpleName();
    private static final String ACTION_POST_CALL = "android.telecom.action.POST_CALL";
    private static final int DEFAULT_DISCONNECT_CAUSE = -1;
    private static final long TEST_TIMEOUT = 5000;

    private static Uri cachedHandle;
    private static int cachedDisconnectCause;
    private static CountDownLatch sLatch = new CountDownLatch(1);

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        final Intent intent = getIntent();
        Log.i(TAG, "onCreate: intent= " + intent);
        final String action = intent != null ? intent.getAction() : null;
        if (ACTION_POST_CALL.equals(action)) {
            cachedHandle = intent.getParcelableExtra(EXTRA_HANDLE);
            cachedDisconnectCause = intent
                    .getIntExtra(EXTRA_DISCONNECT_CAUSE, DEFAULT_DISCONNECT_CAUSE);
            sLatch.countDown();
            //  The activity should be immediately destroyed after the latch is decremented.
            //  Otherwise, the activity will fail to be created again when another test is
            //  executed leading to flake.
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart: Activity becoming visible");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume: Activity in foreground and interactive");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause: Activity losing focus");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop: Activity no longer visible");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy: Activity is being destroyed");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "onRestart: Activity restarting after being stopped");
    }

    public static Uri getCachedHandle() {
        return cachedHandle;
    }

    public static int getCachedDisconnectCause() {
        return cachedDisconnectCause;
    }

    public static void resetPostCallActivity() {
        Log.i(TAG, "resetPostCallActivity:");
        sLatch = new CountDownLatch(1);
        cachedHandle = null;
        cachedDisconnectCause = DEFAULT_DISCONNECT_CAUSE;
    }

    public static boolean waitForActivity() {
        Log.i(TAG, "waitForActivity:");
        try {
            return sLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }
}
