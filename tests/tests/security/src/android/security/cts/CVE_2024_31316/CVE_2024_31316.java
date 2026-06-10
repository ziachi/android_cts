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

package android.security.cts.CVE_2024_31316;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.sts.common.DumpsysUtils.isActivityVisible;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.accounts.ChooseTypeAndAccountActivity;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_31316 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 321941232)
    public void testPocCVE_2024_31316() {
        try {
            // Ensure that the 'ChooseLockPassword' is not already visible
            runShellCommand("input keyevent KEYCODE_HOME");
            final Context context = getApplicationContext();
            final String chooseLockPasswordActivity =
                    String.format(
                            "%s.password.ChooseLockPassword",
                            queryPkgName(context, Settings.ACTION_SETTINGS));
            assume().withMessage("'ChooseLockPassword' activity is still visible on screen")
                    .that(isActivityVisible(chooseLockPasswordActivity))
                    .isFalse();

            // Launch 'ChooseTypeAndAccountActivity' activity
            context.startActivity(
                    new Intent()
                            .setClassName(
                                    queryPkgName(context, Intent.ACTION_REBOOT),
                                    ChooseTypeAndAccountActivity.class.getName())
                            .putExtra(
                                    ChooseTypeAndAccountActivity
                                            .EXTRA_ALLOWABLE_ACCOUNT_TYPES_STRING_ARRAY,
                                    new String[] {context.getPackageName()})
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            // Vulnerability causes an unexported activity 'ChooseLockPassword'
            // to launch.
            assertWithMessage("Device is vulnerable to b/321941232!!")
                    .that(poll(() -> isActivityVisible(chooseLockPasswordActivity)))
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private String queryPkgName(Context context, String actionString) {
        return new Intent(actionString)
                .resolveActivity(context.getPackageManager())
                .getPackageName();
    }
}
