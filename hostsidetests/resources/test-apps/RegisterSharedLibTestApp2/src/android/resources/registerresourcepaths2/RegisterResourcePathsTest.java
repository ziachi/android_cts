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

package android.resources.cts.registerresourcepaths2;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.webkit.WebSettings;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;

import com.android.compatibility.common.util.NullWebViewUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RegisterResourcePathsTest {
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private static final String TAG = "RegisterSharedLibTest";

    @Before
    public void setUp() {
        startBackgroundThread();
    }

    @After
    public void tearDown() {
        stopBackgroundThread();
    }

    @Test
    @UiThreadTest
    public void testWebViewInitializeOnBackGroundThread() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            assumeTrue("WebView is not available on the device.", false);
        }

        final Context context = InstrumentationRegistry.getContext();

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    // This would trigger WebView init behind scene and post tasks on main thread.
                    // It could help to verify that the resources are accessible from the main
                    // thread if it returns without process crashing.
                    final String userAgent = WebSettings.getDefaultUserAgent(context);
                    Assert.assertNotEquals("", userAgent);
                } catch (Exception e) {
                    Assert.fail("Exception occurred during WebView initialization: "
                            + e.getMessage());
                }
            }
        });
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("WebViewBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted exception thrown while stopping WebViewBackground thread.");
        }
    }
}
