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

import static android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE;
import static android.content.Intent.ACTION_MANAGED_PROFILE_REMOVED;
import static android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpmRoleHolder;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;
import static com.android.bedstead.harrier.UserType.ADDITIONAL_USER;
import static com.android.bedstead.harrier.UserType.ANY;
import static com.android.bedstead.harrier.UserType.SYSTEM_USER;
import static com.android.bedstead.harrier.UserType.WORK_PROFILE;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_ROLE_HOLDERS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedProfileProvisioningParams;
import android.app.admin.ProvisioningException;
import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;

import com.android.bedstead.deviceadminapp.DeviceAdminApp;
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.EnsureHasDevicePolicyManagerRoleHolder;
import com.android.bedstead.enterprise.annotations.EnsureHasNoDpc;
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.accounts.annotations.EnsureHasAccount;
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.accounts.annotations.EnsureHasNoAccounts;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.multiuser.annotations.RequireMultiUserSupport;
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.compatibility.common.util.CddTest;
import com.android.eventlib.truth.EventLogsSubject;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

// TODO(b/228016400): replace usages of createAndProvisionManagedProfile with a nene API
@RunWith(BedsteadJUnit4.class)
public class DevicePolicyManagementRoleHolderTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final ComponentName DEVICE_ADMIN_COMPONENT_NAME =
            DeviceAdminApp.deviceAdminComponentName(sContext);
    private static final String PROFILE_OWNER_NAME = "testDeviceAdmin";
    private static final ManagedProfileProvisioningParams MANAGED_PROFILE_PROVISIONING_PARAMS =
            createManagedProfileProvisioningParamsBuilder().build();
    private static final String MANAGED_USER_NAME = "managed user name";

    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    private static final String FEATURE_ALLOW =
            "android.account.DEVICE_OR_PROFILE_OWNER_ALLOWED";

    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @Test
    @CddTest(requirements = {"3.9.4/C-3-1"})
    public void createAndProvisionManagedProfile_roleHolderIsInWorkProfile()
            throws ProvisioningException {
        try (UserReference profile = UserReference.of(
                sDevicePolicyManager.createAndProvisionManagedProfile(
                        MANAGED_PROFILE_PROVISIONING_PARAMS))) {
            Poll.forValue(() -> TestApis.packages().installedForUser(profile))
                    .toMeet(packages -> packages.contains(
                            Package.of(dpmRoleHolder(sDeviceState).packageName())))
                    .errorOnFail("Role holder package not installed on the managed profile.")
                    .await();
            }
    }

    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasDeviceOwner(isPrimary = true)
    @EnsureCanAddUser
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = SYSTEM_USER)
    @Test
    @CddTest(requirements = {"3.9.4/C-3-1"})
    public void createAndManageUser_roleHolderIsInManagedUser() {
        try (UserReference userReference = UserReference.of(
                dpc(sDeviceState).devicePolicyManager().createAndManageUser(
                        dpc(sDeviceState).componentName(),
                        MANAGED_USER_NAME,
                        dpc(sDeviceState).componentName(),
                        /* adminExtras= */ null,
                        /* flags= */ 0))) {
            Poll.forValue(() -> TestApis.packages().installedForUser(userReference))
                    .toMeet(packages -> packages.contains(Package.of(
                            dpmRoleHolder(sDeviceState).packageName())))
                    .errorOnFail("Role holder package not installed on the managed user.")
                    .await();
        }
    }

    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @Test
    public void profileRemoved_roleHolderReceivesBroadcast() throws Exception {
        UserHandle profile = sDevicePolicyManager.createAndProvisionManagedProfile(
                MANAGED_PROFILE_PROVISIONING_PARAMS);

        TestApis.users().find(profile).remove();

        EventLogsSubject.assertThat(dpmRoleHolder(sDeviceState).events().broadcastReceived()
                        .whereIntent().action().isEqualTo(ACTION_MANAGED_PROFILE_REMOVED))
                .eventOccurred();
    }

    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @Test
    public void profileEntersQuietMode_roleHolderReceivesBroadcast() throws Exception {
        try (UserReference profile = UserReference.of(
                sDevicePolicyManager.createAndProvisionManagedProfile(
                        MANAGED_PROFILE_PROVISIONING_PARAMS))) {
            profile.setQuietMode(true);

            EventLogsSubject.assertThat(dpmRoleHolder(sDeviceState).events().broadcastReceived()
                            .whereIntent().action().isEqualTo(ACTION_MANAGED_PROFILE_UNAVAILABLE))
                    .eventOccurred();
        }
    }

    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @Test
    public void profileStarted_roleHolderReceivesBroadcast() throws Exception {
        try (UserReference profile = UserReference.of(
                sDevicePolicyManager.createAndProvisionManagedProfile(
                        MANAGED_PROFILE_PROVISIONING_PARAMS))) {
            profile.setQuietMode(true);

            profile.setQuietMode(false);

            EventLogsSubject.assertThat(dpmRoleHolder(sDeviceState).events().broadcastReceived()
                            .whereIntent().action().isEqualTo(ACTION_MANAGED_PROFILE_AVAILABLE))
                    .eventOccurred();
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoDpc
    @EnsureHasNoAccounts(onUser = ANY)
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_noUsersAndAccounts_returnsTrue()
            throws Exception {
        // We don't want to reset the cache too early in case the account state hasn't been cached
        Poll.forValue("shouldAllowBypassingDevicePolicyManagementRoleQualification", () -> {
                    TestApis.devicePolicy().resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState();
                    return sDevicePolicyManager.shouldAllowBypassingDevicePolicyManagementRoleQualification();
                }).toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoDpc
    @RequireMultiUserSupport
    @EnsureHasNoAccounts(onUser = ANY)
    @EnsureCanAddUser
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_withNonTestUsers_returnsFalse()
            throws Exception {
        TestApis.devicePolicy().resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState();
        try (UserReference user = TestApis.users().createUser()
                .name("shouldAllowBypassingDevicePolicyManagementRoleQualification_withNonTestUsers_returnsFalse")
                .forTesting(false)
                .create()) {
            assertThat(
                    sDevicePolicyManager
                            .shouldAllowBypassingDevicePolicyManagementRoleQualification())
                    .isFalse();
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoDpc
    @RequireMultiUserSupport
    @EnsureHasNoAccounts(onUser = ANY)
    @EnsureCanAddUser
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_withTestUsers_returnsTrue()
            throws Exception {
        TestApis.devicePolicy().resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState();
        try (UserReference user = TestApis.users().createUser()
                .forTesting(true)
                .create()) {
            assertThat(
                    sDevicePolicyManager
                            .shouldAllowBypassingDevicePolicyManagementRoleQualification())
                    .isTrue();
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasAccount(onUser = ADDITIONAL_USER, features = {})
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_withNonAllowedAccounts_returnsFalse()
            throws Exception {
        // We don't want to reset the cache too early in case the account state hasn't been cached - REMOVE THIS ONCE ADD ACOCUNT AND REMOVE ACCOUNT IS BLOCKING CORRECTLY
        Poll.forValue("shouldAllowBypassingDevicePolicyManagementRoleQualification", () -> {
            TestApis.devicePolicy().resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState();
            return sDevicePolicyManager.shouldAllowBypassingDevicePolicyManagementRoleQualification();
        }).toBeEqualTo(false)
          .errorOnFail()
          .await();
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasAdditionalUser
    @EnsureHasNoDpc
    @EnsureHasNoAccounts // TODO: Specify no accounts that don't match the account we actually want
    @EnsureHasAccount(onUser = ADDITIONAL_USER, features = FEATURE_ALLOW)
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_withAllowedAccounts_returnsTrue()
            throws Exception {
        // We don't want to reset the cache too early in case the account state hasn't been cached
        Poll.forValue("shouldAllowBypassingDevicePolicyManagementRoleQualification", () -> {
                    TestApis.devicePolicy().resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState();
                    return sDevicePolicyManager.shouldAllowBypassingDevicePolicyManagementRoleQualification();
                }).toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureDoesNotHavePermission(MANAGE_ROLE_HOLDERS)
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_withoutRequiredPermission_throwsSecurityException() {
        TestApis.devicePolicy().resetShouldAllowBypassingDevicePolicyManagementRoleQualificationState();

        assertThrows(SecurityException.class, () ->
                sDevicePolicyManager.shouldAllowBypassingDevicePolicyManagementRoleQualification());
    }

    private static ManagedProfileProvisioningParams.Builder
            createManagedProfileProvisioningParamsBuilder() {
        return new ManagedProfileProvisioningParams.Builder(
                DEVICE_ADMIN_COMPONENT_NAME,
                PROFILE_OWNER_NAME);
    }

    /**
     * Verify that a non-preinstalled DMRH can be uninstalled when there is no management.
     */
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @Test
    public void uninstallAllowedForNonPreinstalledDmrhWhenUnmanaged() {
        dpmRoleHolder(sDeviceState).pkg().uninstall(TestApis.users().instrumented());
        assertThat(dpmRoleHolder(sDeviceState).pkg().installedOnUser()).isFalse();
    }

    /**
     * Verify that DMRH can't be uninstalled from managed user.
     */
    @Postsubmit(reason = "new test")
    @EnsureHasDeviceOwner
    @EnsureHasDevicePolicyManagerRoleHolder
    @Test
    public void uninstallNotAllowedForNonPreinstalledDmrhWhenManaged() {
        assertThrows(NeneException.class, () ->
                dpmRoleHolder(sDeviceState).pkg().uninstall(TestApis.users().instrumented()));
        assertThat(dpmRoleHolder(sDeviceState).pkg().installedOnUser()).isTrue();
    }

    /**
     * Verifies that a non-preinstalled DMRH on the work profile can be uninstalled from the
     * personal user.
     * If the DMRH is preinstalled, the behaviour will be different that the ability for the
     * personal side to "uninstall updates" is blocked. Ideally we want to test this scenario as
     * well but getting a preinstalled DMRH in CTS is not possible right now.
     */
    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = WORK_PROFILE)
    @Test
    public void workProfileDmrhCanBeUninstalledFromPersonal() {
        Package dmrhPackage = dpmRoleHolder(sDeviceState).pkg();

        if (!dmrhPackage.installedOnUser()) {
            dmrhPackage.installExisting(TestApis.users().instrumented());
        }

        dpmRoleHolder(sDeviceState).pkg().uninstall(TestApis.users().instrumented());
        assertThat(dpmRoleHolder(sDeviceState).pkg().installedOnUser()).isFalse();
    }

    /**
     * Verifies that a non-preinstalled DMRH on the work profile can't be uninstalled from the
     * profile.
     */
    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = WORK_PROFILE)
    @Test
    public void workProfileDmrhCantBeUninstalledFromWork() {
        assertThrows(NeneException.class, () ->
                dpmRoleHolder(sDeviceState).pkg().uninstall(workProfile(sDeviceState)));
        assertThat(dpmRoleHolder(sDeviceState).pkg().installedOnUser(workProfile(sDeviceState)))
                .isTrue();
    }
}
