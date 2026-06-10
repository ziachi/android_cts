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

import static com.android.sts.common.SystemUtil.withSetting;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_40677 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 327748846)
    public void testPocCVE_2024_40677() {
        try {
            final Instrumentation instrumentation = getInstrumentation();
            try (AutoCloseable withDeviceInProvisioningState =
                    withSetting(
                            instrumentation,
                            "global" /* namespace */,
                            Settings.Global.DEVICE_PROVISIONED /* key */,
                            "0" /* value */)) {
                // Ensure that 'AdvancedPowerUsageDetail' fragment is not visible on the screen
                final Context context = instrumentation.getContext();
                final String packageName = context.getPackageName();
                final String settingsPackageName =
                        new Intent(Settings.ACTION_SETTINGS)
                                .resolveActivity(context.getPackageManager())
                                .getPackageName();
                final BySelector selector =
                        By.res(String.format("%s:id/entity_header_title", settingsPackageName))
                                .text(
                                        Pattern.compile(
                                                String.format("^.*%s.*$", packageName) /* regex */,
                                                Pattern.CASE_INSENSITIVE));
                final UiDevice uiDevice = UiDevice.getInstance(instrumentation);
                assume().withMessage("'AdvancedPowerUsageDetail' fragment is visible on screen")
                        .that(uiDevice.hasObject(selector))
                        .isFalse();

                // Launch 'AdvancedPowerUsageDetailActivity' which internally launches
                // 'AdvancedPowerUsageDetail' fragment
                context.startActivity(
                        new Intent()
                                .setComponent(
                                        ComponentName.createRelative(
                                                settingsPackageName,
                                                ".fuelgauge.AdvancedPowerUsageDetailActivity"))
                                .setData(Uri.parse(packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

                // Without fix, the 'AdvancedPowerUsageDetail' fragment gets launched
                // With fix, if the device is in provisioning state, the 'AdvancedPowerUsageDetail'
                // fragment does not get launched
                // UiObject gets detected in ~1 second, keeping 10 seconds considering slower
                // execution of devices
                assertWithMessage(
                                "Device is vulnerable to b/327748846 !! Factory Reset Protection"
                                    + " can be bypassed via AdvancedPowerUsageDetail fragment of"
                                    + " the Settings app")
                        .that(uiDevice.wait(Until.findObject(selector), 10_000L /* timeout */))
                        .isNull();
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
