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

package android.security.cts.BUG_321707289;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.sts.common.SystemUtil.DEFAULT_POLL_TIME_MS;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {

    @Test
    public void testPocBUG_321707289() {
        try {
            // Check if the helper-app's 'PocAccessibilityService' was connected successfully
            // and no exception is caught in 'PocAccessibilityService' in 'test-app'
            final Context context = getApplicationContext();
            SharedPreferences sharedPreferences =
                    context.getSharedPreferences(
                            context.getString(R.string.sharedPrefsName), Context.MODE_PRIVATE);
            String msgException =
                    sharedPreferences.getString(context.getString(R.string.msgException), null);
            assume().withMessage(msgException).that(msgException).isNull();

            // Check if 'helperAppKey' is received in 'sharedPreferences'
            assume().withMessage(
                            "Did not receive any value in sharedPreferences from"
                                    + " PocNotificationListenerService in helper-app")
                    .that(
                            sharedPreferences.getString(
                                    context.getString(R.string.helperAppKey), null))
                    .isNotNull();

            // Without fix, 'PocNotificationListenerService' binds successfully due to which value
            // received from 'PocNotificationListenerService' in 'test-app' is 'fail'
            assertWithMessage("This device is vulnerable to b/321707289")
                    .that(
                            poll(
                                    () ->
                                            sharedPreferences
                                                    .getString(
                                                            context.getString(R.string.testAppKey),
                                                            "pass")
                                                    .contains(context.getString(R.string.fail)),
                                    DEFAULT_POLL_TIME_MS,
                                    10000 /* maxPollingTime */))
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
