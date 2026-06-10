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

import static android.Manifest.permission.READ_CALENDAR;
import static android.app.admin.DevicePolicyIdentifiers.ACCOUNT_MANAGEMENT_DISABLED_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.APPLICATION_HIDDEN_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.APPLICATION_RESTRICTIONS_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.AUTO_TIMEZONE_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.KEYGUARD_DISABLED_FEATURES_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.LOCK_TASK_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.MEMORY_TAGGING_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.PACKAGE_UNINSTALL_BLOCKED_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.PERMISSION_GRANT_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.PERMITTED_INPUT_METHODS_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.PERSISTENT_PREFERRED_ACTIVITY_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.PERSONAL_APPS_SUSPENDED_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.RESET_PASSWORD_TOKEN_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.SCREEN_CAPTURE_DISABLED_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.USER_CONTROL_DISABLED_PACKAGES_POLICY;
import static android.app.admin.DevicePolicyIdentifiers.getIdentifierForUserRestriction;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
import static android.app.admin.FlagUnion.FLAG_UNION;
import static android.app.admin.TargetUser.GLOBAL_USER_ID;
import static android.app.admin.TargetUser.LOCAL_USER_ID;
import static android.app.role.RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT;
import static android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS;
import static android.os.UserManager.DISALLOW_WIFI_DIRECT;

import static com.android.bedstead.accounts.AccountsDeviceStateExtensionsKt.accounts;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.deviceOwner;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpmRoleHolder;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApps;
import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.ActivityManager;
import android.app.admin.AccountTypePolicyKey;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyState;
import android.app.admin.DpcAuthority;
import android.app.admin.EnforcingAdmin;
import android.app.admin.FlagUnion;
import android.app.admin.IntentFilterPolicyKey;
import android.app.admin.LockTaskPolicy;
import android.app.admin.MostRecent;
import android.app.admin.MostRestrictive;
import android.app.admin.NoArgsPolicyKey;
import android.app.admin.PackagePermissionPolicyKey;
import android.app.admin.PackagePolicyKey;
import android.app.admin.PolicyKey;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateReceiver;
import android.app.admin.PolicyUpdateResult;
import android.app.admin.TopPriority;
import android.app.admin.UserRestrictionPolicyKey;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.devicepolicy.cts.utils.BundleUtils;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.bedstead.accounts.annotations.EnsureHasAccountAuthenticator;
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.EnsureHasDevicePolicyManagerRoleHolder;
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.inputmethods.InputMethod;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.permissions.PermissionContext;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public final class DeviceManagementCoexistenceTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String PACKAGE_NAME = "com.android.package.test";

    private static final String GRANTABLE_PERMISSION = READ_CALENDAR;

    private static final byte[] TOKEN = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes();

    private static String FINANCED_DEVICE_CONTROLLER_ROLE =
            "android.app.role.SYSTEM_FINANCED_DEVICE_CONTROLLER";

    private static final List<Boolean> TRUE_MORE_RESTRICTIVE = List.of(true, false);

    private static final String LOCAL_USER_RESTRICTION = DISALLOW_MODIFY_ACCOUNTS;

    private static final String GLOBAL_USER_RESTRICTION = DISALLOW_WIFI_DIRECT;

    private static final int LOCK_TASK_FEATURES = LOCK_TASK_FEATURE_HOME;

    private static final int KEYGUARD_DISABLED_FEATURE = KEYGUARD_DISABLE_SECURE_CAMERA;

    private static final List<String> NON_SYSTEM_INPUT_METHOD_PACKAGES =
            TestApis.inputMethods().installedInputMethods().stream()
                    .map(InputMethod::pkg)
                    .filter(p -> !p.hasSystemFlag())
                    .map(Package::packageName)
                    .collect(Collectors.toList());
    static {
        NON_SYSTEM_INPUT_METHOD_PACKAGES.add("packageName");
    }

    private static final Package SYSTEM_PACKAGE =
            TestApis.packages().find("com.android.keychain");


    private static final TestApp sTestApp = testApps(sDeviceState).query()
            .whereActivities().contains(
                    activity().where().exported().isTrue()
            ).get();

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/273496614: enable once we enable unicorn APIs")
    public void getDevicePolicyState_autoTimezoneSet_returnsPolicy() {
        boolean originalValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeZoneEnabled(dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new NoArgsPolicyKey(AUTO_TIMEZONE_POLICY),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), originalValue);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/278710449")
    public void getDevicePolicyState_permissionGrantStateSet_returnsPolicy() {
        int existingGrantState = dpc(sDeviceState).devicePolicyManager()
                .getPermissionGrantState(dpc(sDeviceState).componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setPermissionGrantState(
                            dpc(sDeviceState).componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new PackagePermissionPolicyKey(
                            PERMISSION_GRANT_POLICY,
                            sTestApp.packageName(),
                            GRANTABLE_PERMISSION),
                    dpc(sDeviceState).user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(
                    PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setPermissionGrantState(
                    dpc(sDeviceState).componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/273496614: enable once we enable unicorn APIs")
    public void getDevicePolicyState_appRestrictionsSet_returnsPolicy() {
        Bundle originalApplicationRestrictions =
                dpc(sDeviceState).devicePolicyManager()
                        .getApplicationRestrictions(
                                dpc(sDeviceState).componentName(), sTestApp.packageName());
        Bundle bundle = BundleUtils.createBundle(
                "getDevicePolicyState_appRestrictionsSet_returnsPolicy");
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setApplicationRestrictions(
                            dpc(sDeviceState).componentName(), sTestApp.packageName(),
                            bundle);

            PolicyState<Bundle> policyState = getBundlePolicyState(
                    new PackagePolicyKey(
                            APPLICATION_RESTRICTIONS_POLICY,
                            sTestApp.packageName()),
                    dpc(sDeviceState).user().userHandle());

            // app restrictions is a non-coexistable policy, so should not have a resolved policy.
            assertThat(policyState.getCurrentResolvedPolicy()).isNull();
            Bundle returnedBundle = policyState.getPoliciesSetByAdmins().get(
                    new EnforcingAdmin(dpc(sDeviceState).packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            dpc(sDeviceState).user().userHandle()));
            assertThat(returnedBundle).isNotNull();
            BundleUtils.assertEqualToBundle(
                    "getDevicePolicyState_appRestrictionsSet_returnsPolicy",
                    returnedBundle);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationRestrictions(
                    dpc(sDeviceState).componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Ignore("If ResetPasswordWithTokenTest for managed profile is executed before device owner "
            + "and primary user profile owner tests, password reset token would have been disabled "
            + "for the primary user, disabling this test until this gets fixed.")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void getDevicePolicyState_resetPasswordTokenSet_returnsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setResetPasswordToken(
                    dpc(sDeviceState).componentName(), TOKEN);

            PolicyState<Long> policyState = getLongPolicyState(
                    new NoArgsPolicyKey(RESET_PASSWORD_TOKEN_POLICY),
                    dpc(sDeviceState).user().userHandle());

            // reset password token is a non-coexistable policy, so should not have a resolved
            // policy.
            assertThat(policyState.getCurrentResolvedPolicy()).isNull();
            Long token = policyState.getPoliciesSetByAdmins().get(
                    new EnforcingAdmin(dpc(sDeviceState).packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            dpc(sDeviceState).user().userHandle()));
            assertThat(token).isNotNull();
            assertThat(token).isNotEqualTo(0);
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearResetPasswordToken(dpc(sDeviceState).componentName());
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getDevicePolicyState")
    public void getDevicePolicyState_addUserRestriction_returnsPolicy() {
        boolean hasRestrictionOriginally = dpc(sDeviceState)
                .userManager().hasUserRestriction(LOCAL_USER_RESTRICTION);
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), LOCAL_USER_RESTRICTION);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                            LOCAL_USER_RESTRICTION),
                    dpc(sDeviceState).user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            if (!hasRestrictionOriginally) {
                dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                        dpc(sDeviceState).componentName(), LOCAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("addUserRestrictionGlobally is no longer callable from DPCs, should change it to a "
            + "permission based test.")
    public void getDevicePolicyState_addUserRestrictionGlobally_returnsPolicy() {
        boolean hasRestrictionOriginally = dpc(sDeviceState)
                .userManager().hasUserRestriction(GLOBAL_USER_RESTRICTION);
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestrictionGlobally(
                    GLOBAL_USER_RESTRICTION);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(GLOBAL_USER_RESTRICTION),
                            GLOBAL_USER_RESTRICTION),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            if (!hasRestrictionOriginally) {
                dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                        dpc(sDeviceState).componentName(), GLOBAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/273496614: enable once we enable unicorn APIs")
    public void getDevicePolicyState_setKeyguardDisabledFeatures_returnsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setKeyguardDisabledFeatures(
                    dpc(sDeviceState).componentName(), KEYGUARD_DISABLED_FEATURE);

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new NoArgsPolicyKey(
                            KEYGUARD_DISABLED_FEATURES_POLICY),
                    dpc(sDeviceState).user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(KEYGUARD_DISABLED_FEATURE);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setKeyguardDisabledFeatures(
                    dpc(sDeviceState).componentName(), KEYGUARD_DISABLE_FEATURES_NONE);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasWorkProfile(isOrganizationOwned = true, dpcIsPrimary = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getDevicePolicyState")
    public void getDevicePolicyState_setPersonalAppsSuspended_returnsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setPersonalAppsSuspended(
                    dpc(sDeviceState).componentName(), true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new NoArgsPolicyKey(PERSONAL_APPS_SUSPENDED_POLICY),
                    dpc(sDeviceState).user().parent().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setPersonalAppsSuspended(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/273496614: enable once we enable unicorn APIs")
    public void getDevicePolicyState_autoTimezone_returnsCorrectResolutionMechanism() {
        boolean originalValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeZoneEnabled(dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new NoArgsPolicyKey(AUTO_TIMEZONE_POLICY),
                    UserHandle.ALL);

            assertThat(getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);

        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), originalValue);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/278710449")
    public void getDevicePolicyState_permissionGrantState_returnsCorrectResolutionMechanism() {
        int existingGrantState = dpc(sDeviceState).devicePolicyManager()
                .getPermissionGrantState(dpc(sDeviceState).componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setPermissionGrantState(
                            dpc(sDeviceState).componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new PackagePermissionPolicyKey(
                            PERMISSION_GRANT_POLICY,
                            sTestApp.packageName(),
                            GRANTABLE_PERMISSION),
                    dpc(sDeviceState).user().userHandle());

            assertThat(getMostRestrictiveIntegerMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(
                    List.of(PERMISSION_GRANT_STATE_DENIED,
                            PERMISSION_GRANT_STATE_GRANTED,
                            PERMISSION_GRANT_STATE_DEFAULT));
        } finally {
            dpc(sDeviceState).devicePolicyManager().setPermissionGrantState(
                    dpc(sDeviceState).componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getDevicePolicyState")
    public void getDevicePolicyState_addUserRestriction_returnsCorrectResolutionMechanism() {
        boolean hasRestrictionOriginally = dpc(sDeviceState)
                .userManager().hasUserRestriction(LOCAL_USER_RESTRICTION);
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), LOCAL_USER_RESTRICTION);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                            LOCAL_USER_RESTRICTION),
                    dpc(sDeviceState).user().userHandle());

            assertThat(getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);
        } finally {
            if (!hasRestrictionOriginally) {
                dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                        dpc(sDeviceState).componentName(), LOCAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/273496614: enable once we enable unicorn APIs")
    public void getDevicePolicyState_setKeyguardDisabledFeatures_returnsCorrectResolutionMechanism() {
        try {
            dpc(sDeviceState).devicePolicyManager().setKeyguardDisabledFeatures(
                    dpc(sDeviceState).componentName(), KEYGUARD_DISABLED_FEATURE);

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new NoArgsPolicyKey(
                            KEYGUARD_DISABLED_FEATURES_POLICY),
                    dpc(sDeviceState).user().userHandle());

            assertThat(getFlagUnionMechanism(policyState)).isEqualTo(FLAG_UNION);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setKeyguardDisabledFeatures(
                    dpc(sDeviceState).componentName(), KEYGUARD_DISABLE_FEATURES_NONE);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasWorkProfile(isOrganizationOwned = true, dpcIsPrimary = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getDevicePolicyState")
    public void getDevicePolicyState_setPersonalAppsSuspended_returnsCorrectResolutionMechanism() {
        try {
            dpc(sDeviceState).devicePolicyManager().setPersonalAppsSuspended(
                    dpc(sDeviceState).componentName(), true);

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new NoArgsPolicyKey(PERSONAL_APPS_SUSPENDED_POLICY),
                    dpc(sDeviceState).user().parent().userHandle());

            assertThat(getMostRecentBooleanMechanism(policyState))
                    .isEqualTo(MostRecent.MOST_RECENT);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setPersonalAppsSuspended(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/273496614: enable once we enable unicorn APIs")
    public void policyUpdateReceiver_autoTimezoneSet_receivedPolicySetBroadcast() {
        boolean originalValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeZoneEnabled(dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), true);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState, AUTO_TIMEZONE_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET,
                    GLOBAL_USER_ID, new Bundle());
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), originalValue);
        }
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/278710449")
    public void policyUpdateReceiver_permissionGrantStateSet_receivedPolicySetBroadcast() {
        Bundle bundle = new Bundle();
        bundle.putString(PolicyUpdateReceiver.EXTRA_PACKAGE_NAME, sTestApp.packageName());
        bundle.putString(PolicyUpdateReceiver.EXTRA_PERMISSION_NAME, GRANTABLE_PERMISSION);
        int existingGrantState = dpc(sDeviceState).devicePolicyManager()
                .getPermissionGrantState(dpc(sDeviceState).componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setPermissionGrantState(
                            dpc(sDeviceState).componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    PERMISSION_GRANT_POLICY, PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID,
                    bundle);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setPermissionGrantState(
                    dpc(sDeviceState).componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.PolicyUpdateReceiver#ACTION_DEVICE_POLICY_SET_RESULT")
    public void policyUpdateReceiver_addUserRestriction_receivedPolicySetBroadcast() {
        boolean hasRestrictionOriginally = dpc(sDeviceState)
                .userManager().hasUserRestriction(LOCAL_USER_RESTRICTION);
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), LOCAL_USER_RESTRICTION);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, new Bundle());
        } finally {
            if (!hasRestrictionOriginally) {
                dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                        dpc(sDeviceState).componentName(), LOCAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @ApiTest(apis = "android.app.admin.PolicyUpdateReceiver#ACTION_DEVICE_POLICY_SET_RESULT")
    @RequiresFlagsEnabled(android.app.admin.flags.Flags.FLAG_SET_MTE_POLICY_COEXISTENCE)
    public void policyUpdateReceiver_setMemoryTaggingPolicy_receivedPolicySetBroadcast() {
        assumeTrue(isMteAvailable());
        int mtePolicy = dpc(sDeviceState).devicePolicyManager().getMtePolicy();
        try {
            dpc(sDeviceState).devicePolicyManager().setMtePolicy(DevicePolicyManager.MTE_ENABLED);

            PolicySetResultUtils.assertPolicySetResultReceived(
                    sDeviceState,
                    MEMORY_TAGGING_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET,
                    GLOBAL_USER_ID,
                    new Bundle());
        } finally {
            dpc(sDeviceState).devicePolicyManager().setMtePolicy(mtePolicy);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("addUserRestrictionGlobally is no longer callable from DPCs, should change it to a "
            + "permission based test.")
    public void policyUpdateReceiver_addUserRestrictionGlobally_receivedPolicySetBroadcast() {
        boolean hasRestrictionOriginally = dpc(sDeviceState)
                .userManager().hasUserRestriction(GLOBAL_USER_RESTRICTION);
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestrictionGlobally(
                    GLOBAL_USER_RESTRICTION);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    getIdentifierForUserRestriction(GLOBAL_USER_RESTRICTION),
                    PolicyUpdateResult.RESULT_POLICY_SET, GLOBAL_USER_ID, new Bundle());
        } finally {
            if (!hasRestrictionOriginally) {
                dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                        dpc(sDeviceState).componentName(), GLOBAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getDevicePolicyState")
    public void devicePolicyState_getPoliciesForAllUsers_returnsPolicies() {
        boolean originalAutoTimeZoneValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeZoneEnabled(dpc(sDeviceState).componentName());
        boolean hasRestrictionOriginally = dpc(sDeviceState)
                .userManager().hasUserRestriction(LOCAL_USER_RESTRICTION);
        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), true);
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), LOCAL_USER_RESTRICTION);

            try {
                DevicePolicyState state =
                        dpmRoleHolder(sDeviceState).devicePolicyManager().getDevicePolicyState();
                Map<UserHandle, Map<PolicyKey, PolicyState<?>>> policies =
                        state.getPoliciesForAllUsers();

                // TODO(b/273496614): enable once we enable unicorn APIs")
//                PolicyState<Boolean> autoTimezonePolicy =
//                        (PolicyState<Boolean>) (policies.get(UserHandle.ALL)
//                                .get(new NoArgsPolicyKey(AUTO_TIMEZONE_POLICY)));
                PolicyState<Boolean> userRestrictionPolicy =
                        (PolicyState<Boolean>) (policies.get(dpc(sDeviceState).user().userHandle())
                                .get(new UserRestrictionPolicyKey(
                                        getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                                        LOCAL_USER_RESTRICTION)));
                // TODO(b/273496614): enable once we enable unicorn APIs")
//                assertThat(autoTimezonePolicy.getCurrentResolvedPolicy()).isTrue();
                assertThat(userRestrictionPolicy.getCurrentResolvedPolicy()).isTrue();
            } catch (ClassCastException e) {
                fail("Returned policy is not of type Boolean: " + e);
            }
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), originalAutoTimeZoneValue);
            if (!hasRestrictionOriginally) {
                dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                        dpc(sDeviceState).componentName(), GLOBAL_USER_RESTRICTION);
            }
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/273496614: enable once we enable unicorn APIs")
    public void policyUpdateReceiver_setKeyguardDisabledFeatures_receivedPolicySetBroadcast() {
        try {
            dpc(sDeviceState).devicePolicyManager().setKeyguardDisabledFeatures(
                    dpc(sDeviceState).componentName(), KEYGUARD_DISABLED_FEATURE);

            PolicySetResultUtils.assertPolicySetResultReceived(
                    sDeviceState,
                    KEYGUARD_DISABLED_FEATURES_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, new Bundle());
        } finally {
            dpc(sDeviceState).devicePolicyManager().setKeyguardDisabledFeatures(
                    dpc(sDeviceState).componentName(), KEYGUARD_DISABLE_FEATURES_NONE);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void autoTimezoneSet_serialisation_loadsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), true);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new NoArgsPolicyKey(AUTO_TIMEZONE_POLICY),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void permissionGrantStateSet_serialisation_loadsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setPermissionGrantState(
                            dpc(sDeviceState).componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new PackagePermissionPolicyKey(
                            PERMISSION_GRANT_POLICY,
                            sTestApp.packageName(),
                            GRANTABLE_PERMISSION),
                    dpc(sDeviceState).user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(
                    PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setPermissionGrantState(
                    dpc(sDeviceState).componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void lockTaskPolicySet_serialisation_loadsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setLockTaskPackages(
                            dpc(sDeviceState).componentName(), new String[]{PACKAGE_NAME});
            dpc(sDeviceState).devicePolicyManager()
                    .setLockTaskFeatures(dpc(sDeviceState).componentName(), LOCK_TASK_FEATURES);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<LockTaskPolicy> policyState = getLockTaskPolicyState(
                    new NoArgsPolicyKey(LOCK_TASK_POLICY),
                    dpc(sDeviceState).user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy().getPackages())
                    .containsExactly(PACKAGE_NAME);
            assertThat(policyState.getCurrentResolvedPolicy().getFlags())
                    .isEqualTo(LOCK_TASK_FEATURES);
        } finally {
            dpc(sDeviceState).devicePolicyManager()
                    .setLockTaskPackages(dpc(sDeviceState).componentName(), new String[]{});
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void userControlDisabledPackagesSet_serialisation_loadsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(),
                    Arrays.asList(sTestApp.packageName()));

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Set<String>> policyState = getStringSetPolicyState(
                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).containsExactly(
                    sTestApp.packageName());
        } finally {
            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(),
                    new ArrayList<>());
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void uninstallBlockedSet_serialisation_loadsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setUninstallBlocked(
                    dpc(sDeviceState).componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY,
                            sTestApp.packageName()),
                    dpc(sDeviceState).user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setUninstallBlocked(
                    dpc(sDeviceState).componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void persistentPreferredActivitySet_serialisation_loadsPolicy() {
        try {
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
            dpc(sDeviceState).devicePolicyManager().addPersistentPreferredActivity(
                    dpc(sDeviceState).componentName(),
                    intentFilter,
                    dpc(sDeviceState).componentName());

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<ComponentName> policyState = getComponentNamePolicyState(
                    new IntentFilterPolicyKey(
                            PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                            intentFilter),
                    dpc(sDeviceState).user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(
                    dpc(sDeviceState).componentName());
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearPackagePersistentPreferredActivities(
                    dpc(sDeviceState).componentName(),
                    dpc(sDeviceState).packageName());
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void appRestrictionsSet_serialisation_loadsPolicy() {
        Bundle bundle = BundleUtils.createBundle(
                "appRestrictionsSet_serialisation_loadsPolicy");
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setApplicationRestrictions(
                            dpc(sDeviceState).componentName(), sTestApp.packageName(),
                            bundle);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Bundle> policyState = getBundlePolicyState(
                    new PackagePolicyKey(
                            APPLICATION_RESTRICTIONS_POLICY,
                            sTestApp.packageName()),
                    dpc(sDeviceState).user().userHandle());

            // app restrictions is a non-coexistable policy, so should not have a resolved policy.
            assertThat(policyState.getCurrentResolvedPolicy()).isNull();
            Bundle returnedBundle = policyState.getPoliciesSetByAdmins().get(
                    new EnforcingAdmin(dpc(sDeviceState).packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            dpc(sDeviceState).user().userHandle()));
            assertThat(returnedBundle).isNotNull();
            BundleUtils.assertEqualToBundle(
                    "appRestrictionsSet_serialisation_loadsPolicy",
                    returnedBundle);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationRestrictions(
                    dpc(sDeviceState).componentName(),
                    sTestApp.packageName(), new Bundle());
        }
    }

    @Ignore("If ResetPasswordWithTokenTest for managed profile is executed before device owner " +
            "and primary user profile owner tests, password reset token would have been " +
            "disabled for the primary user, disabling this test until this gets fixed.")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void resetPasswordTokenSet_serialisation_loadsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setResetPasswordToken(
                    dpc(sDeviceState).componentName(), TOKEN);

            PolicyState<Long> policyState = getLongPolicyState(
                    new NoArgsPolicyKey(RESET_PASSWORD_TOKEN_POLICY),
                    dpc(sDeviceState).user().userHandle());

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            // reset password token is a non-coexistable policy, so should not have a resolved
            // policy.
            assertThat(policyState.getCurrentResolvedPolicy()).isNull();
            Long token = policyState.getPoliciesSetByAdmins().get(
                    new EnforcingAdmin(dpc(sDeviceState).packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            dpc(sDeviceState).user().userHandle()));
            assertThat(token).isNotNull();
            assertThat(token).isNotEqualTo(0);
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearResetPasswordToken(
                    dpc(sDeviceState).componentName());
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void addUserRestriction_serialisation_loadsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), LOCAL_USER_RESTRICTION);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                            LOCAL_USER_RESTRICTION),
                    dpc(sDeviceState).user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), LOCAL_USER_RESTRICTION);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk, also DPCs are no longer "
            + "allowed to call addUserRestrictionGlobally")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void addUserRestrictionGlobally_serialisation_loadsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestrictionGlobally(
                    GLOBAL_USER_RESTRICTION);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(GLOBAL_USER_RESTRICTION),
                            GLOBAL_USER_RESTRICTION),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), GLOBAL_USER_RESTRICTION);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    public void setKeyguardDisabledFeatures_serialisation_loadsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setKeyguardDisabledFeatures(
                    dpc(sDeviceState).componentName(), KEYGUARD_DISABLED_FEATURE);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Integer> policyState = getIntegerPolicyState(
                    new NoArgsPolicyKey(
                            KEYGUARD_DISABLED_FEATURES_POLICY),
                    dpc(sDeviceState).user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(KEYGUARD_DISABLED_FEATURE);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setKeyguardDisabledFeatures(
                    dpc(sDeviceState).componentName(), KEYGUARD_DISABLE_FEATURES_NONE);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @EnsureHasAccountAuthenticator
    public void setAccountManagementDisabled_serialisation_loadsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(), accounts(sDeviceState).accountType(),
                    /* disabled= */ true);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            accounts(sDeviceState).accountType()),
                    dpc(sDeviceState).user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(true);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(), accounts(sDeviceState).accountType(),
                    /* disabled= */ false);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/277071699: add test API to trigger reloading from disk")
    public void setPermittedInputMethods_serialisation_loadsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setPermittedInputMethods(
                    dpc(sDeviceState).componentName(), NON_SYSTEM_INPUT_METHOD_PACKAGES);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Set<String>> policyState = getStringSetPolicyState(
                    new NoArgsPolicyKey(PERMITTED_INPUT_METHODS_POLICY),
                    dpc(sDeviceState).user().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isEqualTo(
                    new HashSet<>(NON_SYSTEM_INPUT_METHOD_PACKAGES));
        } finally {
            dpc(sDeviceState).devicePolicyManager().setPermittedInputMethods(
                    dpc(sDeviceState).componentName(), /* packages= */ null);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/277071699: add test API to trigger reloading from disk")
    public void setScreenCaptureDisabled_serialisation_loadsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), true);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    UserHandle.ALL);

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasWorkProfile(isOrganizationOwned = true, dpcIsPrimary = true)
    @Postsubmit(reason = "new test")
    @Ignore("b/277071699: add test API to trigger reloading from disk")
    public void setPersonalAppsSuspended_serialisation_loadsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setPersonalAppsSuspended(
                    dpc(sDeviceState).componentName(), true);

            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            PolicyState<Boolean> policyState = getBooleanPolicyState(
                    new NoArgsPolicyKey(PERSONAL_APPS_SUSPENDED_POLICY),
                    dpc(sDeviceState).user().parent().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setPersonalAppsSuspended(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @Ignore("b/277071699: add test API to trigger reloading from disk")
    @Test
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @EnsureHasAccountAuthenticator
    public void multiplePoliciesSet_serialisation_loadsPolicies() {
        try {
            // Policy Setting
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), true);
            dpc(sDeviceState).devicePolicyManager()
                    .setPermissionGrantState(
                            dpc(sDeviceState).componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);
            dpc(sDeviceState).devicePolicyManager()
                    .setLockTaskPackages(
                            dpc(sDeviceState).componentName(), new String[]{PACKAGE_NAME});
            dpc(sDeviceState).devicePolicyManager()
                    .setLockTaskFeatures(dpc(sDeviceState).componentName(), LOCK_TASK_FEATURES);
            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(),
                    Arrays.asList(sTestApp.packageName()));
            dpc(sDeviceState).devicePolicyManager().setUninstallBlocked(
                    dpc(sDeviceState).componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);
            dpc(sDeviceState).devicePolicyManager().setResetPasswordToken(
                    dpc(sDeviceState).componentName(), TOKEN);
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), LOCAL_USER_RESTRICTION);
            // DPCs can no longer call addUserRestrictionGlobally
//            dpc(sDeviceState).devicePolicyManager().addUserRestrictionGlobally(
//                    GLOBAL_USER_RESTRICTION);
            dpc(sDeviceState).devicePolicyManager().setKeyguardDisabledFeatures(
                    dpc(sDeviceState).componentName(), KEYGUARD_DISABLED_FEATURE);
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
            dpc(sDeviceState).devicePolicyManager().addPersistentPreferredActivity(
                    dpc(sDeviceState).componentName(),
                    intentFilter,
                    dpc(sDeviceState).componentName());
            Bundle bundle = BundleUtils.createBundle(
                    "appRestrictionsSet_serialisation_loadsPolicy");
            dpc(sDeviceState).devicePolicyManager()
                    .setApplicationRestrictions(
                            dpc(sDeviceState).componentName(), sTestApp.packageName(),
                            bundle);
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(), accounts(sDeviceState).accountType(),
                    /* disabled= */ true);

            // Reloading policies from disk
            // TODO(b/277071699): Add test API to trigger reloading from disk. Currently I've tested
            //  this locally by triggering the loading in DPM#getDevicePolicyState in my local
            //  build.

            // Getting policy states
            PolicyState<Boolean> autoTimezonePolicy = getBooleanPolicyState(
                    new NoArgsPolicyKey(AUTO_TIMEZONE_POLICY),
                    UserHandle.ALL);
            PolicyState<Integer> permissionGrantStatePolicy = getIntegerPolicyState(
                    new PackagePermissionPolicyKey(
                            PERMISSION_GRANT_POLICY,
                            sTestApp.packageName(),
                            GRANTABLE_PERMISSION),
                    dpc(sDeviceState).user().userHandle());
            PolicyState<LockTaskPolicy> lockTaskPolicy = getLockTaskPolicyState(
                    new NoArgsPolicyKey(LOCK_TASK_POLICY),
                    dpc(sDeviceState).user().userHandle());
            PolicyState<Set<String>> userControlDisabledPackagesPolicy = getStringSetPolicyState(
                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                    UserHandle.ALL);
            PolicyState<Boolean> packageUninstallBlockedPolicy = getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY,
                            sTestApp.packageName()),
                    dpc(sDeviceState).user().userHandle());
            PolicyState<Long> resetPasswordTokenPolicy = getLongPolicyState(
                    new NoArgsPolicyKey(RESET_PASSWORD_TOKEN_POLICY),
                    dpc(sDeviceState).user().userHandle());
            PolicyState<Boolean> userRestrictionPolicy = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                            LOCAL_USER_RESTRICTION),
                    dpc(sDeviceState).user().userHandle());
            PolicyState<Boolean> globalUserRestrictionPolicy = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(GLOBAL_USER_RESTRICTION),
                            GLOBAL_USER_RESTRICTION),
                    UserHandle.ALL);
            PolicyState<Integer> keyguardDisabledPolicy = getIntegerPolicyState(
                    new NoArgsPolicyKey(
                            KEYGUARD_DISABLED_FEATURES_POLICY),
                    dpc(sDeviceState).user().userHandle());
            PolicyState<ComponentName> persistentPreferredActivityPolicy =
                    getComponentNamePolicyState(
                            new IntentFilterPolicyKey(
                                    PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                    intentFilter),
                            dpc(sDeviceState).user().userHandle());
            PolicyState<Bundle> applicationRestrictionsPolicy = getBundlePolicyState(
                    new PackagePolicyKey(
                            APPLICATION_RESTRICTIONS_POLICY,
                            sTestApp.packageName()),
                    dpc(sDeviceState).user().userHandle());
            PolicyState<Boolean> accountManagementDisabledPolicy = getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            accounts(sDeviceState).accountType()),
                    dpc(sDeviceState).user().userHandle());
            // Asserting policies loaded correctly
            // TODO(b/273496614): enable once we enable unicorn APIs")
//            assertThat(autoTimezonePolicy.getCurrentResolvedPolicy()).isTrue();
            // TODO(b/278710449): uncomment once bug is fixed
//            assertThat(permissionGrantStatePolicy.getCurrentResolvedPolicy()).isEqualTo(
//                    PERMISSION_GRANT_STATE_GRANTED);
            assertThat(lockTaskPolicy.getCurrentResolvedPolicy().getPackages())
                    .containsExactly(PACKAGE_NAME);
            assertThat(lockTaskPolicy.getCurrentResolvedPolicy().getFlags())
                    .isEqualTo(LOCK_TASK_FEATURES);
            assertThat(userControlDisabledPackagesPolicy.getCurrentResolvedPolicy())
                    .containsExactly(sTestApp.packageName());
            assertThat(packageUninstallBlockedPolicy.getCurrentResolvedPolicy()).isTrue();
            // reset password token is a non-coexistable policy, so should not have a resolved
            // policy.
            assertThat(resetPasswordTokenPolicy.getCurrentResolvedPolicy()).isNull();
            Long token = resetPasswordTokenPolicy.getPoliciesSetByAdmins().get(
                    new EnforcingAdmin(dpc(sDeviceState).packageName(),
                            DpcAuthority.DPC_AUTHORITY,
                            dpc(sDeviceState).user().userHandle()));
            assertThat(token).isNotNull();
            assertThat(token).isNotEqualTo(0);
            assertThat(userRestrictionPolicy.getCurrentResolvedPolicy()).isTrue();
//            assertThat(globalUserRestrictionPolicy.getCurrentResolvedPolicy()).isTrue();
            // TODO(b/273496614): enable once we enable unicorn APIs")
//            assertThat(keyguardDisabledPolicy.getCurrentResolvedPolicy()).isEqualTo(
//                    KEYGUARD_DISABLED_FEATURE);
            assertThat(persistentPreferredActivityPolicy.getCurrentResolvedPolicy()).isEqualTo(
                    dpc(sDeviceState).componentName());
            // TODO(b/273496614): enable once we enable unicorn APIs")
            // app restrictions is a non-coexistable policy, so should not have a resolved policy.
//            assertThat(applicationRestrictionsPolicy.getCurrentResolvedPolicy()).isNull();
//            Bundle returnedBundle = applicationRestrictionsPolicy.getPoliciesSetByAdmins().get(
//                    new EnforcingAdmin(dpc(sDeviceState).packageName(),
//                            DpcAuthority.DPC_AUTHORITY,
//                            dpc(sDeviceState).user().userHandle()));
//            assertThat(returnedBundle).isNotNull();
//            BundleUtils.assertEqualToBundle(
//                    "appRestrictionsSet_serialisation_loadsPolicy",
//                    returnedBundle);
            assertThat(accountManagementDisabledPolicy.getCurrentResolvedPolicy()).isTrue();

        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), false);
            dpc(sDeviceState).devicePolicyManager().setPermissionGrantState(
                    dpc(sDeviceState).componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);
            dpc(sDeviceState).devicePolicyManager()
                    .setLockTaskPackages(dpc(sDeviceState).componentName(), new String[]{});
            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(),
                    new ArrayList<>());
            dpc(sDeviceState).devicePolicyManager().setUninstallBlocked(
                    dpc(sDeviceState).componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false);
            dpc(sDeviceState).devicePolicyManager().clearResetPasswordToken(
                    dpc(sDeviceState).componentName());
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), LOCAL_USER_RESTRICTION);
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), GLOBAL_USER_RESTRICTION);
            dpc(sDeviceState).devicePolicyManager().setKeyguardDisabledFeatures(
                    dpc(sDeviceState).componentName(), KEYGUARD_DISABLE_FEATURES_NONE);
            dpc(sDeviceState).devicePolicyManager().clearPackagePersistentPreferredActivities(
                    dpc(sDeviceState).componentName(),
                    dpc(sDeviceState).packageName());
            dpc(sDeviceState).devicePolicyManager().setApplicationRestrictions(
                    dpc(sDeviceState).componentName(),
                    sTestApp.packageName(), new Bundle());
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(), accounts(sDeviceState).accountType(),
                    /* disabled= */ false);
        }
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @Postsubmit(reason = "new test")
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasPermission(value = Manifest.permission.FORCE_STOP_PACKAGES)
    @EnsureHasAccountAuthenticator
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#clearDeviceOwnerApp")
    public void multiplePoliciesSet_dpcRemoved_removesPolicies() throws Exception {
        try (TestAppInstance mTestApp = sTestApp.install()) {
            // Set policies
            dpc(sDeviceState).devicePolicyManager()
                    .setLockTaskPackages(
                            dpc(sDeviceState).componentName(), new String[]{PACKAGE_NAME});
            dpc(sDeviceState).devicePolicyManager()
                    .setLockTaskFeatures(dpc(sDeviceState).componentName(), LOCK_TASK_FEATURES);
            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(),
                    Arrays.asList(sTestApp.packageName()));
            dpc(sDeviceState).devicePolicyManager().setUninstallBlocked(
                    dpc(sDeviceState).componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), LOCAL_USER_RESTRICTION);
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
            dpc(sDeviceState).devicePolicyManager().addPersistentPreferredActivity(
                    dpc(sDeviceState).componentName(),
                    intentFilter,
                    dpc(sDeviceState).componentName());
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(), accounts(sDeviceState).accountType(),
                    /* disabled= */ true);
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(),
                    SYSTEM_PACKAGE.packageName(),
                    /* applicationHidden= */ true);


            // Remove DPC - we know it is a device owner due to @EnsureHasDeviceOwner
            deviceOwner(sDeviceState).remove();

            // Get policies from policy engine
            PolicyState<LockTaskPolicy> lockTaskPolicy = getLockTaskPolicyState(
                    new NoArgsPolicyKey(LOCK_TASK_POLICY),
                    dpc(sDeviceState).user().userHandle());
            PolicyState<Set<String>> userControlDisabledPackagesPolicy = getStringSetPolicyState(
                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                    UserHandle.ALL);
            PolicyState<Boolean> packageUninstallBlockedPolicy = getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY,
                            sTestApp.packageName()),
                    dpc(sDeviceState).user().userHandle());
            PolicyState<Boolean> userRestrictionPolicy = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                            LOCAL_USER_RESTRICTION),
                    dpc(sDeviceState).user().userHandle());
            PolicyState<ComponentName> persistentPreferredActivityPolicy =
                    getComponentNamePolicyState(
                            new IntentFilterPolicyKey(
                                    PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                    intentFilter),
                            dpc(sDeviceState).user().userHandle());
            PolicyState<Boolean> accountManagementDisabledPolicy = getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            accounts(sDeviceState).accountType()),
                    dpc(sDeviceState).user().userHandle());
            // Assert policies removed from policy engine
            assertThat(lockTaskPolicy).isNull();
            assertThat(userControlDisabledPackagesPolicy).isNull();
            assertThat(packageUninstallBlockedPolicy).isNull();
            assertThat(userRestrictionPolicy).isNull();
            assertThat(persistentPreferredActivityPolicy).isNull();
            assertThat(accountManagementDisabledPolicy).isNull();
            // Assert policies not enforced
            assertThat(TestApis.context().instrumentedContext()
                    .getSystemService(DevicePolicyManager.class)
                    .isLockTaskPermitted(PACKAGE_NAME)).isFalse();
            mTestApp.activities().query().whereActivity().exported().isTrue().get().start();
            int processIdBeforeStopping = mTestApp.process().pid();
            TestApis.context().instrumentedContext().getSystemService(ActivityManager.class)
                    .forceStopPackage(mTestApp.packageName());
            assertPackageStopped(
                    sTestApp.pkg(), processIdBeforeStopping);
            assertThat(TestApis.devicePolicy().userRestrictions().isSet(LOCAL_USER_RESTRICTION))
                    .isFalse();

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new PackagePolicyKey(
                            APPLICATION_HIDDEN_POLICY, SYSTEM_PACKAGE.packageName()),
                    TestApis.users().instrumented().userHandle());
            if (policyState != null) {
                assertThat(policyState.getCurrentResolvedPolicy()).isFalse();
            }
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasDevicePolicyManagerRoleHolder(onUser = UserType.SYSTEM_USER)
    @EnsureHasPermission(value = Manifest.permission.FORCE_STOP_PACKAGES)
    @EnsureHasAccountAuthenticator
    @Ignore("Lots of hacking required to run this test! Need to grant the policy permissions to"
            + "the DMRH, change the platform to remove the required permission for "
            + "getDevicePolicyState, remove the device lock role only limitation from the role "
            + "removal logic.")
    public void multiplePoliciesSet_roleRemoved_removesPolicies() throws Exception {
        TestAppInstance mTestApp = sTestApp.install();

        // Set policies
        dpmRoleHolder(sDeviceState).devicePolicyManager()
                .setLockTaskPackages(null, new String[]{PACKAGE_NAME});
        dpmRoleHolder(sDeviceState).devicePolicyManager()
                .setLockTaskFeatures(null, LOCK_TASK_FEATURES);
        dpmRoleHolder(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                null,
                Arrays.asList(sTestApp.packageName()));
        dpmRoleHolder(sDeviceState).devicePolicyManager().setUninstallBlocked(
                null,
                sTestApp.packageName(), /* uninstallBlocked= */ true);
        dpmRoleHolder(sDeviceState).devicePolicyManager().addUserRestriction(
                null, LOCAL_USER_RESTRICTION);
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
        dpmRoleHolder(sDeviceState).devicePolicyManager().addPersistentPreferredActivity(
                null,
                intentFilter,
                new ComponentName(dpmRoleHolder(sDeviceState).packageName(), "class"));
        dpmRoleHolder(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                null, accounts(sDeviceState).accountType(),
                /* disabled= */ true);


        // Remove role holder
        dpmRoleHolder(sDeviceState).testApp().pkg()
                .removeAsRoleHolder(ROLE_DEVICE_POLICY_MANAGEMENT);

        try (PermissionContext p = TestApis.permissions().withPermission(
            MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            // Get policies from policy engine
            PolicyState<LockTaskPolicy> lockTaskPolicy = getLockTaskPolicyState(
                    new NoArgsPolicyKey(LOCK_TASK_POLICY),
                    dpmRoleHolder(sDeviceState).user().userHandle());
            PolicyState<Set<String>> userControlDisabledPackagesPolicy = getStringSetPolicyState(
                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                    UserHandle.ALL);
            PolicyState<Boolean> packageUninstallBlockedPolicy = getBooleanPolicyState(
                    new PackagePolicyKey(
                            PACKAGE_UNINSTALL_BLOCKED_POLICY,
                            sTestApp.packageName()),
                    dpmRoleHolder(sDeviceState).user().userHandle());
            PolicyState<Boolean> userRestrictionPolicy = getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(LOCAL_USER_RESTRICTION),
                            LOCAL_USER_RESTRICTION),
                    dpmRoleHolder(sDeviceState).user().userHandle());
            PolicyState<ComponentName> persistentPreferredActivityPolicy =
                    getComponentNamePolicyState(
                            new IntentFilterPolicyKey(
                                    PERSISTENT_PREFERRED_ACTIVITY_POLICY,
                                    intentFilter),
                            dpmRoleHolder(sDeviceState).user().userHandle());
            PolicyState<Boolean> accountManagementDisabledPolicy = getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            accounts(sDeviceState).accountType()),
                    dpmRoleHolder(sDeviceState).user().userHandle());
            // Assert policies removed from policy engine
            assertThat(lockTaskPolicy).isNull();
            assertThat(userControlDisabledPackagesPolicy).isNull();
            assertThat(packageUninstallBlockedPolicy).isNull();
            assertThat(userRestrictionPolicy).isNull();
            assertThat(persistentPreferredActivityPolicy).isNull();
            assertThat(accountManagementDisabledPolicy).isNull();
            // Assert policies not enforced
            assertThat(TestApis.context().instrumentedContext()
                    .getSystemService(DevicePolicyManager.class)
                    .isLockTaskPermitted(PACKAGE_NAME)).isFalse();
            mTestApp.activities().query().whereActivity().exported().isTrue().get().start();
            int processIdBeforeStopping = mTestApp.process().pid();
            TestApis.context().instrumentedContext().getSystemService(ActivityManager.class)
                    .forceStopPackage(mTestApp.packageName());
            assertPackageStopped(
                    sTestApp.pkg(), processIdBeforeStopping);
            assertThat(TestApis.devicePolicy().userRestrictions().isSet(LOCAL_USER_RESTRICTION))
                    .isFalse();
        }
    }

    private void assertPackageStopped(Package pkg, int processIdBeforeStopping) throws Exception {
        Poll.forValue("Package " + pkg + " stopped",
                        () -> isProcessRunning(pkg, processIdBeforeStopping))
                .toBeEqualTo(false)
                .errorOnFail()
                .await();
    }

    private boolean isProcessRunning(Package pkg, int processIdBeforeStopping)
            throws Exception {
        return pkg.runningProcesses().stream().anyMatch(p -> p.pid() == processIdBeforeStopping);
    }

    private PolicyState<Long> getLongPolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    dpmRoleHolder(sDeviceState).devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Long>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Long: " + e);
            return null;
        }
    }

    private PolicyState<Bundle> getBundlePolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    dpmRoleHolder(sDeviceState).devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Bundle>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Bundle: " + e);
            return null;
        }
    }

    private PolicyState<Boolean> getBooleanPolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    dpmRoleHolder(sDeviceState).devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Boolean>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Boolean: " + e);
            return null;
        }
    }

    static PolicyState<Set<String>> getStringSetPolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    dpmRoleHolder(sDeviceState).devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Set<String>>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Set<String>: " + e);
            return null;
        }
    }

    private PolicyState<LockTaskPolicy> getLockTaskPolicyState(
            PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    dpmRoleHolder(sDeviceState).devicePolicyManager().getDevicePolicyState();
            return (PolicyState<LockTaskPolicy>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type LockTaskPolicy: " + e);
            return null;
        }
    }

    private PolicyState<Integer> getIntegerPolicyState(PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    dpmRoleHolder(sDeviceState).devicePolicyManager().getDevicePolicyState();
            return (PolicyState<Integer>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Integer: " + e);
            return null;
        }
    }

    private PolicyState<ComponentName> getComponentNamePolicyState(
            PolicyKey policyKey, UserHandle user) {
        try {
            DevicePolicyState state =
                    dpmRoleHolder(sDeviceState).devicePolicyManager().getDevicePolicyState();
            return (PolicyState<ComponentName>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type ComponentName: " + e);
            return null;
        }
    }

    private MostRestrictive<Boolean> getMostRestrictiveBooleanMechanism(
            PolicyState<Boolean> policyState) {
        try {
            return (MostRestrictive<Boolean>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type MostRestrictive<Boolean>: " + e);
            return null;
        }
    }

    private MostRestrictive<Integer> getMostRestrictiveIntegerMechanism(
            PolicyState<Integer> policyState) {
        try {
            return (MostRestrictive<Integer>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type MostRestrictive<Integer>: " + e);
            return null;
        }
    }

    private TopPriority<?> getTopPriorityMechanism(PolicyState<?> policyState) {
        try {
            return (TopPriority<?>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type TopPriority<>: " + e);
            return null;
        }
    }

    private FlagUnion getFlagUnionMechanism(
            PolicyState<Integer> policyState) {
        try {
            return (FlagUnion) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type FlagUnion: " + e);
            return null;
        }
    }

    private MostRecent<Set<String>> getMostRecentStringSetMechanism(
            PolicyState<Set<String>> policyState) {
        try {
            return (MostRecent<Set<String>>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type MostRecent<Set<String>>: " + e);
            return null;
        }
    }

    private MostRecent<Boolean> getMostRecentBooleanMechanism(
            PolicyState<Boolean> policyState) {
        try {
            return (MostRecent<Boolean>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type MostRecent<Boolean>: " + e);
            return null;
        }
    }

    private boolean isMteAvailable() {
        final String mteDpmSystemProperty = "ro.arm64.memtag.bootctl_device_policy_manager";
        final String mteSettingsSystemProperty = "ro.arm64.memtag.bootctl_settings_toggle";

        return SystemProperties.getBoolean(
                mteDpmSystemProperty,
                SystemProperties.getBoolean(mteSettingsSystemProperty, false));
    }
}
