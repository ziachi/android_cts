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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.sts.common.DumpsysUtils.getParsedDumpsys;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_31314 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 304290201)
    @Test
    public void testPocCVE_2024_31314() {
        try {
            final Context context = getApplicationContext();
            final ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);

            // Using DEFAULT_MAX_SHORTCUTS_PER_ACTIVITY since REPORT_USAGE_BUFFER_SIZE is not
            // accessible. DEFAULT_MAX_SHORTCUTS_PER_ACTIVITY has to always be greater than
            // REPORT_USAGE_BUFFER_SIZE.
            final int maxShortcutsPerActivity = shortcutManager.getMaxShortcutCountPerActivity();
            final String shortcutName = "CVE_2024_31314_shortcut_";

            // Try to push more than REPORT_USAGE_BUFFER_SIZE shortcuts at once
            for (int idx = 1; idx <= maxShortcutsPerActivity; ++idx) {
                shortcutManager.pushDynamicShortcut(
                        new ShortcutInfo.Builder(context, shortcutName + idx)
                                .setShortLabel(shortcutName + "label_" + idx)
                                .setIntent(new Intent(Intent.ACTION_INSERT))
                                .build());
            }

            // Without fix, more than REPORT_USAGE_BUFFER_SIZE usagestats events of type
            // SHORTCUT_INVOCATION will be invoked
            assertWithMessage(
                            "Device is vulnerable to b/304290201, calls to reportShortcutUsed are"
                                    + " not throttled.")
                    .that(
                            getParsedDumpsys(
                                            "usagestats",
                                            Pattern.compile(
                                                    shortcutName + maxShortcutsPerActivity,
                                                    CASE_INSENSITIVE))
                                    .find())
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
