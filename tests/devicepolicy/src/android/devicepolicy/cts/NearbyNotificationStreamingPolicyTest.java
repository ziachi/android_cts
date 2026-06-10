/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.Manifest.permission.READ_NEARBY_STREAMING_POLICY;
import static android.content.pm.PackageManager.FEATURE_DEVICE_ADMIN;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.multiuser.MultiUserDeviceStateExtensionsKt.secondaryUser;
import static com.android.bedstead.nene.devicepolicy.DevicePolicy.NearbyNotificationStreamingPolicy.NotManaged;
import static com.android.bedstead.nene.devicepolicy.DevicePolicy.NearbyNotificationStreamingPolicy.SameManagedAccountOnly;
import static com.android.bedstead.nene.types.OptionalBoolean.TRUE;
import static com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.RemoteDevicePolicyManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.multiuser.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureHasNoDpc;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnProfileOwnerProfileWithNoDeviceOwner;
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnSystemDeviceOwnerUser;
import com.android.bedstead.harrier.policies.GetNearbyNotificationStreamingPolicy;
import com.android.bedstead.harrier.policies.SetNearbyNotificationStreamingPolicy;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DevicePolicy;
import com.android.bedstead.permissions.PermissionContext;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.xts.root.annotations.RequireRootInstrumentation;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
@RequireFeature(FEATURE_DEVICE_ADMIN)
public class NearbyNotificationStreamingPolicyTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @IncludeRunOnProfileOwnerProfileWithNoDeviceOwner
    @IncludeRunOnSystemDeviceOwnerUser
    public void getNearbyNotificationStreamingPolicy_defaultToSameManagedAccountOnly() {
        assertThat(TestApis.devicePolicy().getNearbyNotificationStreamingPolicy()).isEqualTo(SameManagedAccountOnly);
    }

    @EnsureHasNoDpc
    public void getNearbyNotificationStreamingPolicy_defaultToUnmanaged() {
        assertThat(TestApis.devicePolicy().getNearbyNotificationStreamingPolicy()).isEqualTo(NotManaged);
    }

    @CannotSetPolicyTest(policy = GetNearbyNotificationStreamingPolicy.class)
    @RequireRootInstrumentation(reason = "removal of permission from testapp")
    public void getNearbyNotificationStreamingPolicy_policyIsNotAllowedToBeSet_throwsException() {
        assertThrows(SecurityException.class, () -> dpc(sDeviceState).devicePolicyManager().getNearbyNotificationStreamingPolicy());
    }

    @PolicyAppliesTest(policy = SetNearbyNotificationStreamingPolicy.class)
    public void setNearbyNotificationStreamingPolicy_policyApplied_works() {
        RemoteDevicePolicyManager dpm = dpc(sDeviceState).devicePolicyManager();
        int originalPolicy = dpm.getNearbyNotificationStreamingPolicy();

        dpm.setNearbyNotificationStreamingPolicy(DevicePolicyManager.NEARBY_STREAMING_DISABLED);

        try {
            assertThat(dpm.getNearbyNotificationStreamingPolicy())
                    .isEqualTo(DevicePolicyManager.NEARBY_STREAMING_DISABLED);
        } finally {
            dpm.setNearbyNotificationStreamingPolicy(originalPolicy);
        }
    }

    @PolicyDoesNotApplyTest(policy = SetNearbyNotificationStreamingPolicy.class)
    @EnsureHasPermission(READ_NEARBY_STREAMING_POLICY)
    public void setNearbyNotificationStreamingPolicy_policyApplied_otherUsersUnaffected() {
        RemoteDevicePolicyManager dpm = dpc(sDeviceState).devicePolicyManager();
        var originalLocalPolicy = TestApis.devicePolicy().getNearbyNotificationStreamingPolicy();
        int originalPolicy = dpm.getNearbyNotificationStreamingPolicy();

        dpm.setNearbyNotificationStreamingPolicy(DevicePolicyManager.NEARBY_STREAMING_DISABLED);

        try {
            assertThat(TestApis.devicePolicy().getNearbyNotificationStreamingPolicy())
                    .isEqualTo(originalLocalPolicy);
        } finally {
            dpm.setNearbyNotificationStreamingPolicy(originalPolicy);
        }
    }

    @CannotSetPolicyTest(policy = SetNearbyNotificationStreamingPolicy.class)
    public void setNearbyNotificationStreamingPolicy_policyIsNotAllowedToBeSet_throwsException() {
        RemoteDevicePolicyManager dpm = dpc(sDeviceState).devicePolicyManager();

        assertThrows(SecurityException.class, () ->
                dpm.setNearbyNotificationStreamingPolicy(
                        DevicePolicyManager.NEARBY_STREAMING_DISABLED));
    }

    @Postsubmit(reason = "new test")
    @PolicyDoesNotApplyTest(policy = SetNearbyNotificationStreamingPolicy.class)
    @EnsureHasPermission(READ_NEARBY_STREAMING_POLICY)
    public void setNearbyNotificationStreamingPolicy_setEnabled_doesNotApply() {
        RemoteDevicePolicyManager dpm = dpc(sDeviceState).devicePolicyManager();
        int originalPolicy = dpm.getNearbyNotificationStreamingPolicy();

        dpm.setNearbyNotificationStreamingPolicy(DevicePolicyManager.NEARBY_STREAMING_ENABLED);

        try {
            assertThat(
                    TestApis.devicePolicy().getNearbyNotificationStreamingPolicy()).isNotEqualTo(
                    DevicePolicy.NearbyNotificationStreamingPolicy.Enabled);
        } finally {
            dpm.setNearbyNotificationStreamingPolicy(originalPolicy);
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasSecondaryUser(installInstrumentedApp = TRUE)
    @EnsureHasPermission(READ_NEARBY_STREAMING_POLICY)
    @EnsureDoesNotHavePermission({INTERACT_ACROSS_USERS, INTERACT_ACROSS_USERS_FULL})
    public void getNearbyNotificationStreamingPolicy_calledAcrossUsers_throwsException() {
        try (PermissionContext p = TestApis.permissions()
                .withPermission(INTERACT_ACROSS_USERS_FULL)) {
            DevicePolicyManager dpm = TestApis.context()
                    .instrumentedContextAsUser(secondaryUser(sDeviceState))
                    .getSystemService(DevicePolicyManager.class);

            assertThrows(SecurityException.class, () -> dpm.getNearbyNotificationStreamingPolicy());
        }
    }
}
