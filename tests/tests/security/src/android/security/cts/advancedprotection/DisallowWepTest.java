/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_WEP;

import android.platform.test.annotations.RequiresFlagsEnabled;
import androidx.test.runner.AndroidJUnit4;
import com.android.compatibility.common.util.ApiTest;
import com.android.wifi.flags.Flags;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_WEP_DISABLED_IN_APM)
public class DisallowWepTest extends BaseAdvancedProtectionTest {
    @ApiTest(
            apis = {
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#getAdvancedProtectionFeatures",
                "android.security.advancedprotection.AdvancedProtectionManager"
                        + "#FEATURE_ID_DISALLOW_WEP"
            })
    @Test
    public void testGetFeatures() {
        Assert.assertEquals(
                "The Disallow WEP feature is not in the feature list",
                1,
                mManager.getAdvancedProtectionFeatures().stream()
                        .filter(feature -> feature.getId() == FEATURE_ID_DISALLOW_WEP)
                        .count());
    }
}
