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
package android.content.cts.packagechangedtestapp.shareduserid;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteCallback;
import android.text.TextUtils;
import android.util.Log;

public class MainActivity extends Activity {
    private static final String TAG = "shareduserid.MainActivity";
    private static final String EXTRA_REMOTE_CALLBACK = "extra_remote_callback";
    private static final String EXTRA_REMOTE_CALLBACK_RESULT = "extra_remote_callback_result";
    private static final String EXTRA_TEST_COMPONENT_NAME = "extra_test_component_name";

    private BroadcastReceiver mPackageChangedReceiver;
    private Handler mHandler;
    private TimeoutRunnable mTimeoutRunnable;

    @Override
    public void onResume() {
        super.onResume();

        mHandler = new Handler(getMainLooper());
        final RemoteCallback remoteCallback = getIntent().getParcelableExtra(EXTRA_REMOTE_CALLBACK);
        if (remoteCallback != null) {
            final ComponentName componentName = getIntent().getParcelableExtra(
                    EXTRA_TEST_COMPONENT_NAME);
            if (componentName == null) {
                return;
            }
            mTimeoutRunnable = new TimeoutRunnable(remoteCallback);
            mHandler.postDelayed(mTimeoutRunnable, 5 * 1000);
            startWatchingPackageChanged(remoteCallback, componentName);
        }
    }

    void startWatchingPackageChanged(RemoteCallback remoteCallback, ComponentName componentName) {
        mPackageChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "onReceive action=" + intent.getAction());
                if (TextUtils.equals(intent.getAction(), Intent.ACTION_PACKAGE_CHANGED)) {
                    final Bundle bundle = intent.getExtras();
                    if (bundle != null && TextUtils.equals(
                            bundle.getString(Intent.EXTRA_CHANGED_COMPONENT_NAME),
                            componentName.getClassName())) {
                        mHandler.removeCallbacks(mTimeoutRunnable);
                        stopWatchingPackageChanged();
                        Log.d(TAG, "receive the right component changed");
                        final Bundle result = new Bundle();
                        result.putString(EXTRA_REMOTE_CALLBACK_RESULT,
                                "RECEIVE PACKAGE CHANGED BROADCAST");
                        remoteCallback.sendResult(result);
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(mPackageChangedReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    void stopWatchingPackageChanged() {
        unregisterReceiver(mPackageChangedReceiver);
    }

    private class TimeoutRunnable implements Runnable {
        private final RemoteCallback mRemoteCallback;

        TimeoutRunnable(RemoteCallback remoteCallback) {
            this.mRemoteCallback = remoteCallback;
        }

        @Override
        public void run() {
            Log.d(TAG, "timeout to receive package changed broadcast");
            stopWatchingPackageChanged();
            final Bundle bundle = new Bundle();
            bundle.putString(EXTRA_REMOTE_CALLBACK_RESULT, "NOT RECEIVE PACKAGE CHANGED BROADCAST");
            mRemoteCallback.sendResult(bundle);
        }
    }
}
