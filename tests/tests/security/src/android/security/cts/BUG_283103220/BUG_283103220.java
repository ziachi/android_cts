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

package android.security.cts.BUG_283103220;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class BUG_283103220 extends StsExtraBusinessLogicTestCase {
    static final String BUG_283103220_ACTION = "bug_283103220_action";
    static final String BUG_283103220_EXCEPTION = "bug_283103220_exception";
    static final String BUG_283103220_VULNERABLE_METHODS = "bug_283103220_vulnerableMethods";

    private Exception mException = null;

    @Test
    @AsbSecurityTest(cveBugId = 283103220)
    public void testPocBUG_283103220() {
        try {
            final Context context = getApplicationContext();

            // Check if the device support picture-in-picture mode
            assume().withMessage(
                            "Skipping CTS test for BUG-283103220!"
                                    + " The device doesn't support picture-in-picture mode!")
                    .that(
                            context.getPackageManager()
                                    .hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
                    .isTrue();

            // Register a receiver to receive any update from other app components.
            // Using the flag 'RECEIVER_EXPORTED' for API level 33 and above since it's
            // required for API 34 and above and available only from API level 33 onwards
            // and 0 otherwise.
            final int requiredFlag =
                    Build.VERSION.SDK_INT >= 33 /* TIRAMISU */
                            ? (int) Context.class.getField("RECEIVER_EXPORTED").get(context)
                            : 0;

            // Register broadcast receiver to receive broadcast from 'PipActivity'
            final CompletableFuture<String> waitOnPipActivity = new CompletableFuture<String>();
            context.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            try {
                                if (intent.hasExtra(BUG_283103220_EXCEPTION)) {
                                    mException =
                                            (Exception)
                                                    getSerializableExtra(
                                                            intent,
                                                            BUG_283103220_EXCEPTION,
                                                            Exception.class);
                                }
                                if (intent.hasExtra(BUG_283103220_VULNERABLE_METHODS)) {
                                    waitOnPipActivity.complete(
                                            intent.getStringExtra(
                                                    BUG_283103220_VULNERABLE_METHODS));
                                }
                            } catch (Exception ignore) {
                                // Ignore unintented exceptions
                            }
                        }
                    } /* receiver */,
                    new IntentFilter(BUG_283103220_ACTION) /* filter */,
                    requiredFlag /* flags */);

            // Start 'PipActivity' to reproduce the vulnerability
            context.startActivity(
                    new Intent(context, PipActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            final String vulnerableMethods =
                    waitOnPipActivity.get(10L /* timeout */, TimeUnit.SECONDS /* unit */);
            assume().withMessage("Caught an exception in 'PipActivity' !!")
                    .that(mException)
                    .isNull();
            assertWithMessage(
                            String.format(
                                    "Device is vulnerable to b/283103220 !! setAspectRatio of PiP"
                                        + " feature can cause foreground restriction bypass due to"
                                        + " : Fix is not present in %s",
                                    vulnerableMethods))
                    .that(vulnerableMethods)
                    .isNull();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private Object getSerializableExtra(Intent intent, String key, Class valueClass)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        if (Build.VERSION.SDK_INT >= 33 /* TIRAMISU */) {
            return Intent.class
                    .getDeclaredMethod("getSerializableExtra", String.class, Class.class)
                    .invoke(intent, key, valueClass);
        }
        return Intent.class
                .getDeclaredMethod("getSerializableExtra", String.class)
                .invoke(intent, key);
    }
}
