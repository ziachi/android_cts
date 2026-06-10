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

package android.security.cts.advancedprotection;

import static android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.security.Flags;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_AAPM_FEATURE_DISABLE_INSTALL_UNKNOWN_SOURCES)
public class DisallowInstallUnknownSourcesTest extends BaseAdvancedProtectionTest {
    private UserManager mUserManager;

    @Override
    @Before
    public void setup() {
        super.setup();
        mUserManager = mInstrumentation.getContext().getSystemService(UserManager.class);
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#getAdvancedProtectionFeatures",
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES"
    })
    @Test
    public void testGetFeatures() {
        assertEquals(
                "The Disallow Install Unknown Sources feature is not in the feature list",
                1,
                mManager.getAdvancedProtectionFeatures().stream()
                        .filter(
                                feature ->
                                        feature.getId()
                                                == FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES)
                        .count());
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#setAdvancedProtectionEnabled"})
    @Test
    public void testEnableProtection() throws InterruptedException {
        setAdvancedProtectionEnabled(true);
        assertTrue("The DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY restriction is not set",
                mUserManager.hasUserRestriction(DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY));
    }

    @ApiTest(apis = {
            "android.security.advancedprotection.AdvancedProtectionManager"
                    + "#setAdvancedProtectionEnabled"})
    @Test
    public void testDisableProtection() throws InterruptedException {
        setAdvancedProtectionEnabled(false);
        assertFalse("The DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY restriction is set",
                mUserManager.hasUserRestriction(DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY));
    }
}
