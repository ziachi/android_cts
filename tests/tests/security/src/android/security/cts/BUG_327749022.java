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

package android.security.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.UserSettings.NAMESPACE_GLOBAL;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.sts.common.DumpsysUtils.isActivityVisible;
import static com.android.sts.common.SystemUtil.poll;
import static com.android.sts.common.SystemUtil.withSetting;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BUG_327749022 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 327749022)
    public void testPocBUG_327749022() {
        final Instrumentation instrumentation = getInstrumentation();
        final Context context = instrumentation.getContext();

        assume().withMessage(
                        "Skipping test: Watch allows limited preferences of Settings during OOBE")
                .that(isWatch(context))
                .isFalse();

        try {
            // Set the 'DEVICE_PROVISIONED' as 'false' to reproduce the issue.
            try (AutoCloseable withDeviceInProvisionState =
                    withSetting(
                            instrumentation,
                            NAMESPACE_GLOBAL,
                            Settings.Global.DEVICE_PROVISIONED,
                            "0" /* false */)) {
                // Ensure that 'SettingsHomepageActivity' is not visible on the screen.
                runShellCommand("input keyevent KEYCODE_HOME");
                final Intent intentToLaunchSettings =
                        new Intent(Settings.ACTION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                final String settingsHomepageActivity =
                        intentToLaunchSettings
                                .resolveActivity(context.getPackageManager())
                                .getClassName();
                assume().withMessage(
                                "'SettingsHomepageActivity' activity is still visible on screen")
                        .that(isActivityVisible(settingsHomepageActivity))
                        .isFalse();

                // Launch 'SettingsHomepageActivity'.
                context.startActivity(intentToLaunchSettings);

                // Without fix, the 'SettingsHomepageActivity' launches.
                // With fix, If the device is in provisioning state, the 'SettingsHomepageActivity'
                // should not launch.
                assertWithMessage("Device is vulnerable to b/327749022!!")
                        .that(poll(() -> isActivityVisible(settingsHomepageActivity)))
                        .isFalse();
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private boolean isWatch(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);

    }
}
