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

package com.android.compatibility.common.util;

import static android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER;
import static android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Utility for starting the blocked number service.
 */
public class BlockedNumberUtil {

    private static final int ASYNC_TIMEOUT = 10000;
    private static final String TAG = "BlockedNumberUtil";
    private static ContentResolver mContentResolver;
    private static Handler mHandler = new Handler(Looper.getMainLooper());

    private BlockedNumberUtil() {}

    /** Insert a phone number into the blocked number provider and returns the resulting Uri. */
    public static Uri insertBlockedNumber(Context context, String phoneNumber) {
        Log.v(TAG, "insertBlockedNumber: " + phoneNumber);
        setUp(context);

        CountDownLatch blockedNumberLatch = getBlockedNumberLatch(context);
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_ORIGINAL_NUMBER, phoneNumber);
        Uri uri = mContentResolver.insert(CONTENT_URI, cv);

        // Wait for the content provider to be updated.
        try {
            if (!blockedNumberLatch.await(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Timed out waiting for blocked number update");
                return null;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for blocked number update");
            return null;
        }
        return uri;
    }

    /** Remove a number from the blocked number provider and returns the number of rows deleted. */
    public static int deleteBlockedNumber(Context context, Uri uri) {
        Log.v(TAG, "deleteBlockedNumber: " + uri);
        setUp(context);
        return mContentResolver.delete(uri, null, null);
    }

    /** Temporarily allow background service and get the ContentResolver. */
    private static void setUp(Context context) {
        // Temporarily allow background service
        SystemUtil.runShellCommand("cmd deviceidle tempwhitelist " + context.getPackageName());
        mContentResolver = context.getContentResolver();
    }

    private static CountDownLatch getBlockedNumberLatch(Context context) {
        CountDownLatch changeLatch = new CountDownLatch(1);
        context.getContentResolver().registerContentObserver(
                CONTENT_URI, true,
                new ContentObserver(mHandler) {
                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        context.getContentResolver().unregisterContentObserver(this);
                        changeLatch.countDown();
                        super.onChange(selfChange);
                    }
                });
        return changeLatch;
    }
}
