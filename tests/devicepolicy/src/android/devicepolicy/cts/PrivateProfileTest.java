/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.devicepolicy.cts;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_ADD_PRIVATE_PROFILE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.os.UserManager;

import com.android.bedstead.enterprise.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner;
import com.android.bedstead.enterprise.annotations.EnsureHasUserRestriction;
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.policies.DisallowAddPrivateProfile;
import com.android.bedstead.multiuser.annotations.EnsureHasNoPrivateProfile;
import com.android.bedstead.multiuser.annotations.RequireMultiUserSupport;
import com.android.bedstead.multiuser.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.multiuser.annotations.RequirePrivateSpaceSupported;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RequireNotHeadlessSystemUserMode(reason = "Private profiles are disabled on HSUM.")
@RunWith(BedsteadJUnit4.class)
// TODO(b/297332594): Migrate this test class to private space test module once added.
public final class PrivateProfileTest {
    @ClassRule
    @Rule
    public static DeviceState sDeviceState = new DeviceState();

    /**
     * Test creation of private profile should not be allowed when device owner is set.
     */
    @Test
    @EnsureHasDeviceOwner
    @RequireRunOnInitialUser
    @RequireMultiUserSupport
    @EnsureHasNoPrivateProfile
    public void hasDeviceOwner_disallowAddPrivateProfileIsSet() {
        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ADD_PRIVATE_PROFILE)).isTrue();
    }

    /**
     * Test creation of private profile should be allowed on devices with a work profile.
     */
    @Test
    @EnsureHasWorkProfile
    @EnsureHasNoDeviceOwner
    @EnsureHasNoPrivateProfile
    @RequireRunOnInitialUser
    @RequireMultiUserSupport
    public void hasWorkProfile_disallowAddPrivateProfileIsNotSet() {
        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ADD_PRIVATE_PROFILE)).isFalse();
    }

    /**
     * Test creation of private profile should be allowed on organization-owned devices.
     */
    @Test
    @EnsureHasWorkProfile(isOrganizationOwned = true)
    @EnsureHasNoDeviceOwner
    @EnsureHasNoPrivateProfile
    @RequireRunOnInitialUser
    @RequireMultiUserSupport
    public void hasOrganizationOwnedWorkProfile_disallowAddPrivateProfileIsNotSet() {
        assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ADD_PRIVATE_PROFILE)).isFalse();
    }


    @ApiTest(apis = "android.os.UserManager#DISALLOW_ADD_PRIVATE_PROFILE")
    @Test
    @PolicyAppliesTest(policy = DisallowAddPrivateProfile.class)
    public void addUserRestriction_restrictionApplies() {
        ComponentName admin = dpc(sDeviceState).componentName();
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(admin,
                    DISALLOW_ADD_PRIVATE_PROFILE);

            assertThat(TestApis.devicePolicy().userRestrictions()
                    .isSet(DISALLOW_ADD_PRIVATE_PROFILE)).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(admin,
                    DISALLOW_ADD_PRIVATE_PROFILE);
        }
    }

    /**
     * Test creation of private profile should be allowed when when the restriction is not set.
     */
    @Test
    @RequirePrivateSpaceSupported
    @EnsureHasNoDeviceOwner
    @EnsureHasNoPrivateProfile
    @RequireRunOnInitialUser
    @RequireMultiUserSupport
    @EnsureDoesNotHaveUserRestriction(DISALLOW_ADD_PRIVATE_PROFILE)
    public void addPrivateProfile_disallowAddPrivateProfileIsNotSet_addsPrivateProfile() {
        try (UserReference privateProfile = createPrivateProfile()) {
            assertThat(privateProfile.exists()).isTrue();
        }
    }

    /**
     * Test creation of private profile should not be allowed when when the restriction is set.
     */
    @Test
    @RequirePrivateSpaceSupported
    @EnsureHasNoDeviceOwner
    @EnsureHasNoPrivateProfile
    @RequireRunOnInitialUser
    @RequireMultiUserSupport
    @EnsureHasUserRestriction(DISALLOW_ADD_PRIVATE_PROFILE)
    public void addPrivateProfile_disallowAddPrivateProfileIsSet_throwsException() {
        assertThrows(NeneException.class, () -> createPrivateProfile());
    }

    private UserReference createPrivateProfile() {
        return TestApis.users().createUser()
                .parent(TestApis.users().instrumented())
                .type(TestApis.users().supportedType(UserManager.USER_TYPE_PROFILE_PRIVATE))
                .create();
    }
}
