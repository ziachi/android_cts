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

package android.packageinstaller.criticaluserjourney.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.UserManager;
import android.text.TextUtils;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;

/**
 * The test base to test PackageInstaller update ownership CUJs.
 */
public class UpdateOwnershipTestBase extends InstallationTestBase {

    private static final String PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE =
            "is_update_ownership_enforcement_available";

    private String mIsUpdateOwnershipEnforcementAvailable = null;

    private String assumeRunOnPrimaryUser() {
        UserManager um = getContext().getSystemService(UserManager.class);
        String userType = um.getUserType();
        android.util.Log.d(TAG, um.getUserType());
        Assume.assumeTrue("Don't support to run the test cases in a profile.",
                TextUtils.equals(userType, UserManager.USER_TYPE_FULL_SYSTEM));
        return userType;
    }

    @Before
    @Override
    public void setup() throws Exception {
        SystemUtil.callWithShellPermissionIdentity(this::assumeRunOnPrimaryUser);

        super.setup();

        mIsUpdateOwnershipEnforcementAvailable =
                getPackageManagerDeviceProperty(PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE);
        setPackageManagerDeviceProperty(PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE, "true");
        assertThat(Boolean.parseBoolean(getPackageManagerDeviceProperty(
                PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE))).isEqualTo(true);

        installTestPackageWithUpdateOwnership();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        setPackageManagerDeviceProperty(PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE,
                mIsUpdateOwnershipEnforcementAvailable);
        super.tearDown();
    }
}
