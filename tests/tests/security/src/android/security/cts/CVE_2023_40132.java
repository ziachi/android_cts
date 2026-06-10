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

import static android.Manifest.permission.WRITE_SETTINGS;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeNoException;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_40132 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 205837340)
    @Test
    public void testCVE_2023_40132() {
        try {
            // Set a default ringtone using an uri
            final Context context = getApplicationContext();
            final Uri testUri = Uri.parse("content://test_uri");
            runWithShellPermissionIdentity(
                    () -> {
                        RingtoneManager.setActualDefaultRingtoneUri(
                                context, RingtoneManager.TYPE_RINGTONE, testUri);
                    },
                    WRITE_SETTINGS);

            // Without fix "testUri" will be set as default ringtone and
            // "getActualDefaultRingtoneUri()" returns "testUri".
            // With fix "testUri" will not be set as default ringtone
            assertWithMessage("Device is vulnarable to b/205837340")
                    .that(testUri)
                    .isNotEqualTo(
                            RingtoneManager.getActualDefaultRingtoneUri(
                                    context, RingtoneManager.TYPE_RINGTONE));
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
