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

import static android.os.UserManager.DISALLOW_CELLULAR_2G;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.security.Flags;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_AAPM_FEATURE_DISABLE_CELLULAR_2G)
public class DisallowCellular2GTest extends BaseAdvancedProtectionTest {
    private UserManager mUserManager;
    private PackageManager mPackageManager;

    @Override
    @Before
    public void setup() {
        super.setup();

        mUserManager = mInstrumentation.getContext().getSystemService(UserManager.class);
        mPackageManager = mInstrumentation.getContext().getPackageManager();

        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE,
                        Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE,
                        Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
    }

    private boolean isAvailable() {
        return mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    private long getNumFeatures() {
        return mManager.getAdvancedProtectionFeatures().stream()
                .filter(feature -> feature.getId() == FEATURE_ID_DISALLOW_CELLULAR_2G)
                .count();
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#getAdvancedProtectionFeatures",
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#FEATURE_ID_DISALLOW_CELLULAR_2G"
            })
    @Test
    public void testGetFeatures_cellularAvailable() {
        assumeTrue(isAvailable());

        assertEquals(
                "The Disallow Cellular 2G feature is not in the feature list", 1, getNumFeatures());
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#getAdvancedProtectionFeatures",
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#FEATURE_ID_DISALLOW_CELLULAR_2G"
            })
    @Test
    public void testGetFeatures_cellularUnavailable() {
        assumeFalse(isAvailable());

        assertEquals(
                "The Disallow Cellular 2G feature should not be in the feature list",
                0,
                getNumFeatures());
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    public void testEnableProtection() throws InterruptedException {
        assumeTrue(isAvailable());

        setAdvancedProtectionEnabled(true);

        assertTrue(
                "The DISALLOW_CELLULAR_2G restriction is not set",
                mUserManager.hasUserRestriction(DISALLOW_CELLULAR_2G));
    }

    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#setAdvancedProtectionEnabled"
            })
    @Test
    public void testDisableProtection() throws InterruptedException {
        assumeTrue(isAvailable());

        setAdvancedProtectionEnabled(false);

        assertFalse(
                "The DISALLOW_CELLULAR_2G restriction is set",
                mUserManager.hasUserRestriction(DISALLOW_CELLULAR_2G));
    }
}
