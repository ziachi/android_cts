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

package src.android.provider.cts.media.modern;

import static android.scopedstorage.cts.lib.TestUtils.forceStopApp;

import static androidx.test.InstrumentationRegistry.getContext;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import com.android.cts.install.lib.TestApp;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MediaStoreTestUtils {
    public static final String QUERY_TYPE = "tests.mediaprovider.modern.queryType";
    public static final String MEDIASTORE_MARK_MEDIA_AS_FAV_API_CALL =
            "tests.mediaprovider.modern.mark.media.as.fav.api.call";
    public static final String IS_CALL_SUCCESSFUL = "tests.mediaprovider.modern.is.call.successful";
    public static final String FAV_API_EXCEPTION =
            "tests.mediaprovider.modern.is.call.fav.api.exception";
    public static final String FAV_API_URI = "tests.mediaprovider.modern.is.call.fav.api.uri";
    public static final String FAV_API_VALUE = "tests.mediaprovider.modern.is.call.fav.api.value";
    public static final String MEDIA_PROVIDER_INTENT_EXCEPTION =
            "tests.mediaprovider.modern.media.provider.intent.exception";
    private static final long POLLING_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(20);

    /**
     * Makes the given {@code testApp} call {@link MediaStore#markAsFavorite API}.
     */
    public static Bundle markIsFavoriteStatus(TestApp testApp, Uri uri, boolean value)
            throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Bundle[] bundle = new Bundle[1];
        final Exception[] exception = new Exception[1];
        exception[0] = null;

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(MEDIA_PROVIDER_INTENT_EXCEPTION)) {
                    exception[0] = (Exception) (intent.getSerializableExtra(
                            MEDIA_PROVIDER_INTENT_EXCEPTION));
                } else {
                    bundle[0] = intent.getExtras();
                }
                latch.countDown();
            }
        };

        final String packageName = testApp.getPackageName();
        forceStopApp(packageName);

        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.putExtra(FAV_API_URI, uri);
        intent.putExtra(FAV_API_VALUE, value);

        launchTestApp(testApp, MEDIASTORE_MARK_MEDIA_AS_FAV_API_CALL, broadcastReceiver, latch,
                intent);

        if (exception[0] != null) {
            throw exception[0];
        }

        return bundle[0];
    }

    private static void launchTestApp(com.android.cts.install.lib.TestApp testApp,
                                      String actionName, BroadcastReceiver broadcastReceiver,
                                      CountDownLatch latch, Intent intent)
            throws InterruptedException, TimeoutException {

        // Register broadcast receiver
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(actionName);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        getContext().registerReceiver(broadcastReceiver, intentFilter,
                Context.RECEIVER_EXPORTED_UNAUDITED);

        // Launch the test app.
        intent.setPackage(testApp.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(QUERY_TYPE, actionName);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        getContext().startActivity(intent);
        if (!latch.await(POLLING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            final String errorMessage = "Timed out while waiting to receive " + actionName
                    + " intent from " + testApp.getPackageName();
            throw new TimeoutException(errorMessage);
        }
        getContext().unregisterReceiver(broadcastReceiver);
    }
}
