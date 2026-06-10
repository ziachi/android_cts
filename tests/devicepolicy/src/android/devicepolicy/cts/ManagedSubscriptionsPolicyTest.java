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

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.content.pm.PackageManager.FEATURE_TELEPHONY;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpmRoleHolder;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.profileOwner;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;
import static com.android.bedstead.harrier.UserType.WORK_PROFILE;

import static com.google.common.truth.Truth.assertThat;
import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedSubscriptionsPolicy;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureGlobalSettingSet;
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureHasDevicePolicyManagerRoleHolder;
import com.android.bedstead.enterprise.annotations.EnsureHasNoDpc;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.AllowWorkProfileTelephonyForNonDPMRH;
import com.android.bedstead.harrier.policies.ManagedSubscriptions;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.permissions.PermissionContext;
import com.android.bedstead.remotedpc.RemoteDevicePolicyManagerRoleHolder;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RequireFeature(FEATURE_TELEPHONY)
@RunWith(BedsteadJUnit4.class)
public final class ManagedSubscriptionsPolicyTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final PackageManager sPackageManager = sContext.getPackageManager();

    private static final String ENABLE_WORK_PROFILE_TELEPHONY_FLAG =
            "enable_work_profile_telephony";

    @EnsureGlobalSettingSet(
            key = Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value =
            "1")
    @PolicyAppliesTest(policy = ManagedSubscriptions.class)
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setManagedSubscriptionsPolicy"})
    @Postsubmit(reason = "new test")
    @Ignore("TODO(298915118): Enable after work telephony requirements are re-added")
    public void setManagedSubscriptionsPolicy_works() {
        try {
            dpc(sDeviceState)
                    .devicePolicyManager()
                    .setManagedSubscriptionsPolicy(
                            new ManagedSubscriptionsPolicy(
                                    ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS));
            DevicePolicyManager sLocalDevicePolicyManager =
                    TestApis.context()
                            .instrumentedContext()
                            .getSystemService(DevicePolicyManager.class);

            assertThat(sLocalDevicePolicyManager.getManagedSubscriptionsPolicy().getPolicyType())
                    .isEqualTo(ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS);
        } finally {
            dpc(sDeviceState)
                    .devicePolicyManager()
                    .setManagedSubscriptionsPolicy(
                            new ManagedSubscriptionsPolicy(
                                    ManagedSubscriptionsPolicy.TYPE_ALL_PERSONAL_SUBSCRIPTIONS));
        }
    }

    @EnsureGlobalSettingSet(
            key = Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value =
            "1")
    @PolicyAppliesTest(policy = ManagedSubscriptions.class)
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#getManagedSubscriptionPolicy"})
    @Ignore("TODO(263556964): Enable after we have a way to reset the policy to default for a dpc")
    @Postsubmit(reason = "new test")
    public void getManagedSubscriptionPolicy_policyNotSet_returnsDefaultPolicy() {
        assertThat(
                dpc(sDeviceState)
                        .devicePolicyManager()
                        .getManagedSubscriptionsPolicy()
                        .getPolicyType())
                .isEqualTo(ManagedSubscriptionsPolicy.TYPE_ALL_PERSONAL_SUBSCRIPTIONS);
    }

    @EnsureGlobalSettingSet(
            key = Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value =
            "1")
    @CannotSetPolicyTest(policy = ManagedSubscriptions.class)
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setManagedSubscriptionsPolicy"})
    @Postsubmit(reason = "new test")
    @Ignore("TODO(298915118): Enable after work telephony requirements are re-added")
    public void setManagedSubscriptionsPolicy_invalidAdmin_fails() {
        assertThrows(
                SecurityException.class, () -> dpc(sDeviceState)
                        .devicePolicyManager()
                        .setManagedSubscriptionsPolicy(
                                new ManagedSubscriptionsPolicy(
                                        ManagedSubscriptionsPolicy
                                                .TYPE_ALL_MANAGED_SUBSCRIPTIONS)));
    }

    @EnsureGlobalSettingSet(
            key = Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value =
            "1")
    @EnsureHasNoDpc
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setManagedSubscriptionsPolicy"})
    @Postsubmit(reason = "new test")
    @Test
    @Ignore("TODO(298915118): Enable after work telephony requirements are re-added")
    public void setManagedSubscriptionsPolicy_policySet_oemDialerAndSmsAppInstalledInWorkProfile() {
        try (RemoteDpc dpc = RemoteDpc.createWorkProfile()) {
            ProfileOwner profileOwner = (ProfileOwner) dpc.devicePolicyController();
            profileOwner.setIsOrganizationOwned(true);
            UserHandle managedProfileUserHandle = dpc.user().userHandle();

            dpc.devicePolicyManager().setManagedSubscriptionsPolicy(new ManagedSubscriptionsPolicy(
                    ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS));

            assertThat(TestApis.packages().oemDefaultDialerApp().installedOnUser(
                    managedProfileUserHandle)).isTrue();
            assertThat(TestApis.packages().oemDefaultSmsApp().installedOnUser(
                    managedProfileUserHandle)).isTrue();
        }
    }

    @EnsureGlobalSettingSet(
            key = Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value =
            "1")
    @PolicyAppliesTest(policy = ManagedSubscriptions.class)
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setManagedSubscriptionsPolicy"})
    @Postsubmit(reason = "new test")
    @Test
    @Ignore("TODO(298915118): Enable after work telephony requirements are re-added")
    public void setManagedSubscriptionsPolicy_callAndSmsIntent_resolvesToWorkProfileApps()
            throws InterruptedException {
        dpc(sDeviceState).devicePolicyManager().setManagedSubscriptionsPolicy(
                new ManagedSubscriptionsPolicy(
                        ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS));

        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            List<ResolveInfo> dialIntentActivities = sPackageManager.queryIntentActivitiesAsUser(
                    new Intent(Intent.ACTION_DIAL).addCategory(
                            Intent.CATEGORY_DEFAULT).setData(
                            Uri.parse("tel:5555")), PackageManager.ResolveInfoFlags.of(0),
                    workProfile(sDeviceState).parent().id());
            List<ResolveInfo> smsIntentActivities = sPackageManager.queryIntentActivitiesAsUser(
                    new Intent(Intent.ACTION_SENDTO).addCategory(
                            Intent.CATEGORY_DEFAULT).setData(
                            Uri.parse("smsto:5555")), PackageManager.ResolveInfoFlags.of(0),
                    workProfile(sDeviceState).parent().id());

            assertThat(dialIntentActivities).hasSize(1);
            assertThat(
                    dialIntentActivities.get(0).isCrossProfileIntentForwarderActivity()).isTrue();
            assertThat(smsIntentActivities).hasSize(1);
            assertThat(smsIntentActivities.get(0).isCrossProfileIntentForwarderActivity()).isTrue();
        }
    }

    @EnsureGlobalSettingSet(
            key = Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value =
            "0")
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ManagedSubscriptions.class)
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setManagedSubscriptionsPolicy"})
    @Test
    @Ignore("TODO(298915118): Enable after work telephony requirements are re-added")
    public void setManagedSubscriptionsPolicy_notEnabledForNonDPMRoleHolders_throwsException()
            throws InterruptedException {
        assertThrows(
                UnsupportedOperationException.class, () -> dpc(sDeviceState)
                        .devicePolicyManager()
                        .setManagedSubscriptionsPolicy(
                                new ManagedSubscriptionsPolicy(
                                        ManagedSubscriptionsPolicy
                                                .TYPE_ALL_MANAGED_SUBSCRIPTIONS)));
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setManagedSubscriptionsPolicy"})
    @EnsureGlobalSettingSet(
            key = Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value =
            "1")
    @EnsureHasDevicePolicyManagerRoleHolder()
    @EnsureHasWorkProfile(isOrganizationOwned = true)
    @Postsubmit(reason = "new test")
    @Test
    @Ignore("TODO(298915118): Enable after work telephony requirements are re-added")
    public void setManagedSubscriptionsPolicy_enabledForNonDMPRoleHolders_works()
            throws InterruptedException {
        try {
            profileOwner(sDeviceState, WORK_PROFILE)
                    .devicePolicyManager().setManagedSubscriptionsPolicy(
                    new ManagedSubscriptionsPolicy(
                            ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS));

            assertThat(
                    profileOwner(sDeviceState, WORK_PROFILE)
                        .devicePolicyManager().getManagedSubscriptionsPolicy()
                        .getPolicyType()).isEqualTo(
                            ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS);
        } finally {
            profileOwner(sDeviceState, WORK_PROFILE)
                    .devicePolicyManager().setManagedSubscriptionsPolicy(
                    new ManagedSubscriptionsPolicy(
                            ManagedSubscriptionsPolicy.TYPE_ALL_PERSONAL_SUBSCRIPTIONS));
        }
    }

    @EnsureGlobalSettingSet(key =
            Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value = "0")
    @Postsubmit(reason = "new test")
    @RequireRunOnWorkProfile
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setManagedSubscriptionsPolicy"})
    @Test
    @Ignore("TODO(298915118): Enable after work telephony requirements are re-added")
    public void setManagedSubscriptionsPolicy_fromDPMRoleHolder_works()
            throws InterruptedException {
        ProfileOwner profileOwner = TestApis.devicePolicy().setProfileOwner(
                workProfile(sDeviceState),
                new ComponentName(RemoteDevicePolicyManagerRoleHolder.sTestApp.packageName(),
                        "com.android.DevicePolicyManagerRoleHolder.DeviceAdminReceiver"));
        profileOwner.setIsOrganizationOwned(true);
        try {
            dpmRoleHolder(sDeviceState).devicePolicyManager().setManagedSubscriptionsPolicy(
                    new ManagedSubscriptionsPolicy(
                            ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS));

            assertThat(
                    dpmRoleHolder(sDeviceState).devicePolicyManager()
                            .getManagedSubscriptionsPolicy().getPolicyType()).isEqualTo(
                                 ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS);
        } finally {
            dpmRoleHolder(sDeviceState).devicePolicyManager().setManagedSubscriptionsPolicy(
                    new ManagedSubscriptionsPolicy(
                            ManagedSubscriptionsPolicy.TYPE_ALL_PERSONAL_SUBSCRIPTIONS));
            profileOwner.remove();
        }
    }

    @CanSetPolicyTest(policy = AllowWorkProfileTelephonyForNonDPMRH.class)
    @Postsubmit(reason = "new test")
    @Test
    @Ignore("TODO(298915118): Enable after work telephony requirements are re-added")
    public void setGlobalSetting_allowWorkProfileTelephonyForNonDMPRH_fromDPMRH_works() {
        try {
            dpmRoleHolder(sDeviceState).devicePolicyManager().setGlobalSetting(null,
                    Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, "1");

            assertThat(TestApis.settings().global().getString(
                    Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS))
                    .isEqualTo("1");
        } finally {
            dpmRoleHolder(sDeviceState).devicePolicyManager().setGlobalSetting(null,
                    Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, "0");
        }
    }

    @CannotSetPolicyTest(policy = AllowWorkProfileTelephonyForNonDPMRH.class)
    @EnsureGlobalSettingSet(key =
            Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value = "0")
    @Postsubmit(reason = "new test")
    @Test
    @Ignore("TODO(298915118): Enable after work telephony requirements are re-added")
    public void setGlobalSetting_allowWorkProfileTelephonyForNonDMPRH_fromNonDPMRH_fails() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().setGlobalSetting(null,
                        Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS,
                        "1"));
    }
}
