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

import static com.android.sts.common.DumpsysUtils.isActivityVisible;
import static com.android.sts.common.SystemUtil.poll;
import static com.android.sts.common.SystemUtil.withSetting;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_40672 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 327645387)
    public void testPocCVE_2024_40672() {
        try {
            // Fetch the name of SECURE_FRP_MODE setting using Settings.Global or Setting.Secure
            String namespace = "global";
            String frpModeSettingName = fetchFrpModeSettingName(Global.class);
            if (frpModeSettingName == null) {
                namespace = "secure";
                frpModeSettingName = fetchFrpModeSettingName(Secure.class);
            }
            assume().withMessage("Unable to retrieve SECURE_FRP_MODE setting's name")
                    .that(frpModeSettingName)
                    .isNotNull();

            // Set DUT into FRP mode using SECURE_FRP_MODE
            final Instrumentation instrumentation = getInstrumentation();
            try (AutoCloseable withFrpMode =
                    withSetting(instrumentation, namespace, frpModeSettingName, "1" /*value */)) {

                // Launch 'ChooserActivity'
                final Context context = instrumentation.getContext();
                Intent chooserIntent =
                        Intent.createChooser(new Intent(), "CVE_2024_40672" /*title */)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooserIntent);

                // Fail if ChooserActivity is visible in FRP mode
                final String chooserActivity =
                        chooserIntent
                                .resolveActivity(context.getPackageManager())
                                .flattenToString();
                assertWithMessage(
                                "Device is vulnerable to b/327645387!! Chooser activity is"
                                        + " accessible in FRP mode")
                        .that(poll(() -> isActivityVisible(chooserActivity)))
                        .isFalse();
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private String fetchFrpModeSettingName(Class<?> clazz) {
        try {
            return clazz.getDeclaredField("SECURE_FRP_MODE").get(null).toString();
        } catch (Exception e) {
            // Field not found
            return null;
        }
    }
}
