/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.app.admin.DevicePolicyIdentifiers.getIdentifierForUserRestriction;
import static android.app.admin.TargetUser.GLOBAL_USER_ID;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.deviceOwner;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_AIRPLANE_MODE;
import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.DpcAuthority;
import android.app.admin.EnforcingAdmin;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateResult;
import android.app.admin.UserRestrictionPolicyKey;
import android.content.Context;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.EnsureHasNoDpc;
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.policies.DisallowAirplaneMode;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.permissions.CommonPermissions;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.compatibility.common.util.ApiTest;
import com.android.xts.root.annotations.RequireRootInstrumentation;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests should only be added to this class if there is nowhere else they could reasonably
 * go.
 */
@RunWith(BedsteadJUnit4.class)
public final class DevicePolicyManagerTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    private static final String MANAGE_PROFILE_AND_DEVICE_OWNERS =
            "android.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS";
    private static final String MANAGE_DEVICE_ADMINS = "android.permission.MANAGE_DEVICE_ADMINS";

    private static final int NO_LIMIT = -1;

    @EnsureHasDeviceOwner
    @EnsureDoesNotHavePermission(MANAGE_DEVICE_ADMINS)
    @Test
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#removeActiveAdmin")
    public void removeActiveAdmin_adminPassedDoesNotBelongToCaller_throwsException() {
        assertThrows(SecurityException.class, () -> sDevicePolicyManager.removeActiveAdmin(
                deviceOwner(sDeviceState).componentName()));
    }

    @EnsureHasDeviceOwner
    @EnsureHasPermission(MANAGE_DEVICE_ADMINS)
    @Test
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#removeActiveAdmin")
    public void removeActiveAdmin_adminPassedDoesNotBelongToCaller_manageDeviceAdminsPermission_noException() {
        sDevicePolicyManager.removeActiveAdmin(
                deviceOwner(sDeviceState).componentName());
    }

    @Test
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getDevicePolicyManagementRoleHolderPackage")
    public void getDeviceManagerRoleHolderPackageName_doesNotCrash() {
        sDevicePolicyManager.getDevicePolicyManagementRoleHolderPackage();
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasNoDpc
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPolicyManagedProfiles")
    public void getPolicyManagedProfiles_noManagedProfiles_returnsEmptyList() {
        assertThat(sDevicePolicyManager.getPolicyManagedProfiles(
                TestApis.context().instrumentationContext().getUser())).isEmpty();
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasWorkProfile
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPolicyManagedProfiles")
    public void getPolicyManagedProfiles_hasWorkProfile_returnsWorkProfileUser() {
        assertThat(sDevicePolicyManager.getPolicyManagedProfiles(
                TestApis.context().instrumentationContext().getUser()))
                .containsExactly(workProfile(sDeviceState).userHandle());
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasNoDpc
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPolicyManagedProfiles")
    public void getPolicyManagedProfiles_hasManagedProfileNoProfileOwner_returnsEmptyList() {
        try (UserReference user = TestApis.users().createUser().type(MANAGED_PROFILE_TYPE_NAME)
                .parent(TestApis.users().instrumented()).create()) {
            assertThat(sDevicePolicyManager.getPolicyManagedProfiles(
                    TestApis.context().instrumentationContext().getUser()))
                    .isEmpty();
        }
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasNoDpc
    @EnsureDoesNotHavePermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getPolicyManagedProfiles")
    public void getPolicyManagedProfiles_noPermission_returnsEmptyList() {
        assertThrows(SecurityException.class, () -> sDevicePolicyManager.getPolicyManagedProfiles(
                TestApis.context().instrumentationContext().getUser()));
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMaxPolicyStorageLimit")
    public void setMaxPolicyStorageLimit_setsLimit() {
        int currentLimit = sDevicePolicyManager.getMaxPolicyStorageLimit();
        try {
            sDevicePolicyManager.setMaxPolicyStorageLimit(NO_LIMIT);

            assertThat(sDevicePolicyManager.getMaxPolicyStorageLimit()).isEqualTo(NO_LIMIT);

        } finally {
            sDevicePolicyManager.setMaxPolicyStorageLimit(currentLimit);
        }
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasPermission({
            CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS,
            CommonPermissions.MANAGE_DEVICE_POLICY_STORAGE_LIMIT})
    @EnsureHasDeviceOwner
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMaxPolicyStorageLimit")
    @CanSetPolicyTest(policy = DisallowAirplaneMode.class)
    @RequireRootInstrumentation(reason = "Use of root only test API "
            + "DPM#forceSetMaxPolicyStorageLimit")
    public void setMaxPolicyStorageLimit_limitReached_doesNotSetPolicy() {
        int currentLimit = sDevicePolicyManager.getMaxPolicyStorageLimit();
        try {
            // Set the limit to the current size of policies set by the admin. setting any new
            // policy should hit the size limit.
            int newLimit = TestApis.devicePolicy().getPolicySizeForAdmin(
                    new EnforcingAdmin(
                            dpc(sDeviceState).packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            dpc(sDeviceState).user().userHandle()));
            TestApis.devicePolicy().setMaxPolicySize(newLimit);
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_AIRPLANE_MODE);

           dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                   dpc(sDeviceState).componentName(), DISALLOW_AIRPLANE_MODE);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    getIdentifierForUserRestriction(DISALLOW_AIRPLANE_MODE),
                    PolicyUpdateResult.RESULT_FAILURE_STORAGE_LIMIT_REACHED, GLOBAL_USER_ID,
                    new Bundle());
            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(DISALLOW_AIRPLANE_MODE),
                            DISALLOW_AIRPLANE_MODE),
                    UserHandle.ALL);
            assertThat(policyState == null || policyState.getCurrentResolvedPolicy() == null)
                    .isTrue();

        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_AIRPLANE_MODE);
            sDevicePolicyManager.setMaxPolicyStorageLimit(currentLimit);
        }
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasDeviceOwner
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMaxPolicyStorageLimit")
    @CanSetPolicyTest(policy = DisallowAirplaneMode.class)
    @RequireRootInstrumentation(reason = "Use of root only test API "
            + "DPM#forceSetMaxPolicyStorageLimit")
    public void setMaxPolicyStorageLimit_policySet_sizeIncreases() {
        int currentLimit = sDevicePolicyManager.getMaxPolicyStorageLimit();
        try {
            EnforcingAdmin admin = new EnforcingAdmin(
                    dpc(sDeviceState).packageName(),
                    DpcAuthority.DPC_AUTHORITY,
                    dpc(sDeviceState).user().userHandle(),
                    dpc(sDeviceState).componentName());
            int currentSize = TestApis.devicePolicy().getPolicySizeForAdmin(admin);
            TestApis.devicePolicy().setMaxPolicySize(NO_LIMIT);

            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_AIRPLANE_MODE);

            assertThat(TestApis.devicePolicy().getPolicySizeForAdmin(admin))
                    .isGreaterThan(currentSize);

        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_AIRPLANE_MODE);
            sDevicePolicyManager.setMaxPolicyStorageLimit(currentLimit);
        }
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasDeviceOwner
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMaxPolicyStorageLimit")
    @CanSetPolicyTest(policy = DisallowAirplaneMode.class)
    @RequireRootInstrumentation(reason = "Use of root only test API "
            + "DPM#forceSetMaxPolicyStorageLimit")
    public void setMaxPolicyStorageLimit_policySetThenUnset_sizeResets() {
        int currentLimit = sDevicePolicyManager.getMaxPolicyStorageLimit();
        try {
            EnforcingAdmin admin = new EnforcingAdmin(
                    dpc(sDeviceState).packageName(),
                    DpcAuthority.DPC_AUTHORITY,
                    dpc(sDeviceState).user().userHandle(),
                    dpc(sDeviceState).componentName());
            int currentSize = TestApis.devicePolicy().getPolicySizeForAdmin(admin);
            TestApis.devicePolicy().setMaxPolicySize(NO_LIMIT);

            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_AIRPLANE_MODE);
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_AIRPLANE_MODE);

            assertThat(TestApis.devicePolicy().getPolicySizeForAdmin(admin)).isEqualTo(currentSize);

        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_AIRPLANE_MODE);
            sDevicePolicyManager.setMaxPolicyStorageLimit(currentLimit);
        }
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasDeviceOwner
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMaxPolicyStorageLimit")
    @CanSetPolicyTest(policy = DisallowAirplaneMode.class)
    @RequireRootInstrumentation(reason = "Use of root only test API "
            + "DPM#forceSetMaxPolicyStorageLimit")
    public void setMaxPolicyStorageLimit_setSamePolicyTwice_sizeDoesNotIncrease() {
        int currentLimit = sDevicePolicyManager.getMaxPolicyStorageLimit();
        try {
            EnforcingAdmin admin = new EnforcingAdmin(
                    dpc(sDeviceState).packageName(),
                    DpcAuthority.DPC_AUTHORITY,
                    dpc(sDeviceState).user().userHandle(),
                    dpc(sDeviceState).componentName());
            TestApis.devicePolicy().setMaxPolicySize(NO_LIMIT);
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_AIRPLANE_MODE);
            int currentSize = TestApis.devicePolicy().getPolicySizeForAdmin(admin);

            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_AIRPLANE_MODE);

            assertThat(TestApis.devicePolicy().getPolicySizeForAdmin(admin)).isEqualTo(currentSize);

        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_AIRPLANE_MODE);
            sDevicePolicyManager.setMaxPolicyStorageLimit(currentLimit);
        }
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureDoesNotHavePermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMaxPolicyStorageLimit")
    public void setMaxPolicyStorageLimit_noPermission_throwsException() {
        assertThrows(
                SecurityException.class, () -> sDevicePolicyManager.setMaxPolicyStorageLimit(-1));
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureDoesNotHavePermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getMaxPolicyStorageLimit")
    public void getMaxPolicyStorageLimit_noPermission_throwsException() {
        assertThrows(
                SecurityException.class, () -> sDevicePolicyManager.getMaxPolicyStorageLimit());
    }

    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRootInstrumentation(reason = "Use of root only test API "
            + "DPM#forceSetMaxPolicyStorageLimit")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMaxPolicyStorageLimit")
    public void getMaxPolicyStorageLimit_getsLimit() {
        int currentLimit = sDevicePolicyManager.getMaxPolicyStorageLimit();
        try {
            int newLimit = 10;
            TestApis.devicePolicy().setMaxPolicySize(newLimit);

            assertThat(sDevicePolicyManager.getMaxPolicyStorageLimit()).isEqualTo(newLimit);

        } finally {
            sDevicePolicyManager.setMaxPolicyStorageLimit(currentLimit);
        }
    }

}
