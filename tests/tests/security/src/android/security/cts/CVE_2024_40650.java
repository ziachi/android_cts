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

import static android.Manifest.permission.CHANGE_WIFI_STATE;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.sts.common.DumpsysUtils.isActivityVisible;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.AsbSecurityTest;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_40650 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 293199910)
    public void testPocCVE_2024_40650() {
        try {
            final Instrumentation instrumentation = getInstrumentation();
            final Context context = instrumentation.getContext();
            final Intent intent =
                    new Intent("com.android.settings.WIFI_DIALOG")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            final ComponentName componentName = intent.resolveActivity(context.getPackageManager());
            final String settingPackageName = componentName.getPackageName();
            final Context settingsContext =
                    context.createPackageContext(settingPackageName, 0 /* defFlag */);

            // Fetch value of 'maxLength' field declared in 'wifi_item_edit_content' of 'styles.xml'
            // of Settings.apk
            // If 'styles.xml' doesn't have 'maxLength' field, default value is considered.
            final int maxLengthAllowed =
                    settingsContext
                            .obtainStyledAttributes(
                                    settingsContext
                                            .getResources()
                                            .getIdentifier(
                                                    "wifi_item_edit_content" /* name */,
                                                    "style" /* defType */,
                                                    settingPackageName /* defPackage */),
                                    new int[] {android.R.attr.maxLength /* resId */} /* attrs */)
                            .getInt(0 /* index */, 500 /* defValue */);

            // Launch wifi dialog activity. 'CHANGE_WIFI_STATE' permission is required to launch
            // 'WIFI_DIALOG'.
            runWithShellPermissionIdentity(
                    () -> {
                        context.startActivity(intent);
                    },
                    CHANGE_WIFI_STATE /* permission */);

            // Wait for activity to be launched
            assume().withMessage("Unable to launch wi-fi dialog Activity !!")
                    .that(poll(() -> isActivityVisible(componentName.flattenToString())))
                    .isTrue();

            // Fetch UI Object corresponding to 'ssid'.
            final UiObject2 uiObject =
                    UiDevice.getInstance(instrumentation)
                            .findObject(
                                    By.res(String.format("%s:id/ssid", settingPackageName))
                                            .clazz("android.widget.EditText"));
            assume().withMessage("UI Object corresponding to 'ssid' not found.")
                    .that(uiObject)
                    .isNotNull();

            // Set the text field of uiobject corresponding to 'ssid' with a large string
            uiObject.setText(
                    new Random()
                            .ints('A' /* randomNumberOrigin */, 'Z' + 1 /* randomNumberBound */)
                            .limit(10000 /* maxSize */)
                            .collect(
                                    StringBuilder::new /* supplier */,
                                    StringBuilder::appendCodePoint /* accumulator */,
                                    StringBuilder::append /* combiner */)
                            .toString());

            // Without fix, the large string gets set to 'ssid' field.
            assertWithMessage(
                            "Device is vulnerable to b/293199910 !! Factory Reset Protection can"
                                    + " be bypassed.")
                    .that(uiObject.getText().length() > maxLengthAllowed)
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
