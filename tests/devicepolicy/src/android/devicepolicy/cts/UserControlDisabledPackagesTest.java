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

import static android.app.AppOpsManager.OPSTR_RUN_ANY_IN_BACKGROUND;
import static android.app.admin.DevicePolicyIdentifiers.USER_CONTROL_DISABLED_PACKAGES_POLICY;
import static android.app.admin.TargetUser.GLOBAL_USER_ID;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_EXEMPTED;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_NEVER;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.nene.appops.AppOpsMode.ALLOWED;
import static com.android.bedstead.nene.appops.AppOpsMode.IGNORED;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApps;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.Manifest.permission;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.NoArgsPolicyKey;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateResult;
import android.app.admin.RemoteDevicePolicyManager;
import android.app.admin.StringSetUnion;
import android.app.admin.flags.Flags;
import android.content.ComponentName;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.os.Bundle;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.policies.UserControlDisabledPackages;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class UserControlDisabledPackagesTest {
    private static final String TAG = "UserControlDisabledPackagesTest";

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp =
            testApps(sDeviceState).query().whereActivities().isNotEmpty().get();

    private static final ActivityManager sActivityManager =
            TestApis.context().instrumentedContext().getSystemService(ActivityManager.class);

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext()
                    .getSystemService(DevicePolicyManager.class);

    private static final String PACKAGE_NAME = "com.android.foo.bar.baz";

    @CannotSetPolicyTest(policy = UserControlDisabledPackages.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    public void setUserControlDisabledPackages_notPermitted_throwsException() {
        assertThrows(SecurityException.class, () -> {
            dpc(sDeviceState).devicePolicyManager()
                    .setUserControlDisabledPackages(dpc(sDeviceState).componentName(),
                            List.of(PACKAGE_NAME));
        });
    }

    @CanSetPolicyTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "New test")
    public void setUserControlDisabledPackages_verifyMetricIsLogged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(), List.of(PACKAGE_NAME));

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_USER_CONTROL_DISABLED_PACKAGES_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            dpc(sDeviceState).packageName())
                    .whereStrings().contains(PACKAGE_NAME)).wasLogged();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(), List.of());
        }
    }

    @CanSetPolicyTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setUserControlDisabledPackages_toOneProtectedPackage() {
        dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                dpc(sDeviceState).componentName(), List.of(PACKAGE_NAME));
        try {
            assertThat(dpc(sDeviceState).devicePolicyManager().getUserControlDisabledPackages(
                    dpc(sDeviceState).componentName()))
                    .contains(PACKAGE_NAME);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(), List.of());
        }
    }

    @CannotSetPolicyTest(policy = UserControlDisabledPackages.class)
    public void setUserControlDisabledPackages_notAllowedToSetProtectedPackages_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                        dpc(sDeviceState).componentName(), List.of()));
    }

    @CannotSetPolicyTest(policy = UserControlDisabledPackages.class)
    public void
    getUserControlDisabledPackages_notAllowedToRetrieveProtectedPackages_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().getUserControlDisabledPackages(
                        dpc(sDeviceState).componentName()));
    }

    @EnsureHasPermission(value = permission.FORCE_STOP_PACKAGES)
    @PolicyAppliesTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setUserControlDisabledPackages_launchActivity_verifyPackageNotStopped()
            throws Exception {
        Assume.assumeTrue("Needs to launch activity",
                TestApis.users().instrumented().canShowActivities());
        String testAppPackageName = sTestApp.packageName();

        try (TestAppInstance instance = sTestApp.install()) {
            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(), List.of(testAppPackageName));
            try {

                instance.activities().any().start();
                int processIdBeforeStopping = instance.process().pid();

                sActivityManager.forceStopPackage(testAppPackageName);

                assertPackageNotStopped(sTestApp.pkg(), processIdBeforeStopping);
            } finally {
                dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                        dpc(sDeviceState).componentName(), List.of());
            }
        }
    }

    @EnsureHasPermission(value = permission.FORCE_STOP_PACKAGES)
    @PolicyDoesNotApplyTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "new test")
    public void setUserControlDisabledPackages_launchActivity_verifyPackageStopped()
            throws Exception {
        Assume.assumeTrue("Needs to launch activity",
                TestApis.users().instrumented().canShowActivities());
        String testAppPackageName = sTestApp.packageName();

        try (TestAppInstance instance = sTestApp.install()) {
            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(), List.of(testAppPackageName));
            try {
                instance.activities().any().start();
                int processIdBeforeStopping = instance.process().pid();

                sActivityManager.forceStopPackage(testAppPackageName);

                assertPackageStopped(sTestApp.pkg(), processIdBeforeStopping);
            } finally {
                dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                        dpc(sDeviceState).componentName(), List.of());
            }
        }
    }

    @PolicyAppliesTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setUserControlDisabledPackages_bgUsageAllowed() {
        try (TestAppInstance testApp = sTestApp.install()) {
            // Take away background usage app op.
            testApp.appOps().set(OPSTR_RUN_ANY_IN_BACKGROUND, IGNORED);

            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(), List.of(sTestApp.packageName()));

            try {
                // Background usage should be allowed again.
                assertThat(sTestApp.pkg().appOps().get(OPSTR_RUN_ANY_IN_BACKGROUND))
                        .isEqualTo(ALLOWED);
            } finally {
                dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                        dpc(sDeviceState).componentName(), List.of());
            }
        }
    }

    @PolicyAppliesTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationExemption"})
    public void setUserControlDisabledPackages_exemptFromStandbyBuckets() {
        try (TestAppInstance testApp = sTestApp.install()) {
            // Put the app into a very restrictive bucket.
            testApp.pkg().setAppStandbyBucket(STANDBY_BUCKET_NEVER);

            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(), List.of(sTestApp.packageName()));

            try {
                // The app should soon be in exempted bucket
                Poll.forValue(() -> testApp.pkg().getAppStandbyBucket())
                        .toMeet(bucket -> bucket == STANDBY_BUCKET_EXEMPTED)
                        .errorOnFail("The app wasn't put into exempt bucket.")
                        .await();
            } finally {
                dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                        dpc(sDeviceState).componentName(), List.of());
            }
        }
    }

    /**
     * Ensures that if the app was force stopped or never started, FLAG_STOPPED gets cleared.
     */
    @PolicyAppliesTest(policy = UserControlDisabledPackages.class)
    @RequireFlagsEnabled(Flags.FLAG_DISALLOW_USER_CONTROL_STOPPED_STATE_FIX)
    public void setUserControlDisabledPackages_clearsStoppedState() throws Exception {
        RemoteDevicePolicyManager dpcDpm = dpc(sDeviceState).devicePolicyManager();
        ComponentName dpcAdmin = dpc(sDeviceState).componentName();

        try (TestAppInstance testApp = sTestApp.install()) {
            testApp.pkg().forceStop();
            // If the app gets started spuriously somehow, the test is invalid.
            Assume.assumeTrue("App didn't get into stopped state", testApp.pkg().isStopped());

            dpcDpm.setUserControlDisabledPackages(dpcAdmin, List.of(testApp.packageName()));
            try {
                assertThat(testApp.pkg().isStopped()).isFalse();
            } finally {
                dpcDpm.setUserControlDisabledPackages(dpcAdmin, List.of());
            }
        }
    }

    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUserControlDisabledPackages",
            "android.app.admin.DevicePolicyManager#setUserControlDisabledPackages",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @PolicyAppliesTest(policy = UserControlDisabledPackages.class)
    public void getDevicePolicyState_setUserControlDisabledPackages_returnsPolicy() {
        String testAppPackageName = sTestApp.packageName();
        try (TestAppInstance instance = sTestApp.install()) {
            try {
                dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                        dpc(sDeviceState).componentName(), List.of(testAppPackageName));

                PolicyState<Set<String>> policyState = PolicyEngineUtils.getStringSetPolicyState(
                        new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                        TestApis.users().instrumented().userHandle());

                assertThat(policyState.getCurrentResolvedPolicy()).contains(
                        sTestApp.packageName());
            } finally {
                dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                        dpc(sDeviceState).componentName(), List.of());
            }
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUserControlDisabledPackages"})
    // TODO: enable after adding the broadcast receiver to relevant test apps.
//    @PolicyAppliesTest(policy = UserControlDisabledPackages.class)
    @EnsureHasDeviceOwner(isPrimary = true)
    public void policyUpdateReceiver_setUserControlDisabledPackages_receivedPolicySetBroadcast() {
        String testAppPackageName = sTestApp.packageName();
        try (TestAppInstance instance = sTestApp.install()) {
            try {
                dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                        dpc(sDeviceState).componentName(), List.of(testAppPackageName));

                PolicySetResultUtils.assertPolicySetResultReceived(
                        sDeviceState,
                        USER_CONTROL_DISABLED_PACKAGES_POLICY,
                        PolicyUpdateResult.RESULT_POLICY_SET, GLOBAL_USER_ID, new Bundle());
            } finally {
                dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                        dpc(sDeviceState).componentName(), List.of());
            }
        }
    }

    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUserControlDisabledPackages",
            "android.app.admin.DevicePolicyManager#setUserControlDisabledPackages",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @CanSetPolicyTest(policy = UserControlDisabledPackages.class, singleTestOnly = true)
    public void getDevicePolicyState_setUserControlDisabledPackages_returnsCorrectResolutionMechanism() {
        String testAppPackageName = sTestApp.packageName();
        try (TestAppInstance instance = sTestApp.install()) {
            try {
                dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                        dpc(sDeviceState).componentName(), List.of(testAppPackageName));

                PolicyState<Set<String>> policyState = PolicyEngineUtils.getStringSetPolicyState(
                        new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                        TestApis.users().instrumented().userHandle());

                assertThat(policyState.getResolutionMechanism()).isEqualTo(
                        StringSetUnion.STRING_SET_UNION);
            } finally {
                dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                        dpc(sDeviceState).componentName(), List.of());
            }
        }
    }

    @PolicyAppliesTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setUserControlDisabledPackages",
            "android.app.admin.DevicePolicyManager#getUserControlDisabledPackages"})
    @Ignore // need to restore with some root-only capability to force migration
    public void setUserControlDisabledPackages_policyMigration_works() {
//        TestApis.flags().set(
//                NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "false");
        try {
            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(), List.of(PACKAGE_NAME));

            sLocalDevicePolicyManager.triggerDevicePolicyEngineMigration(true);
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "true");

            PolicyState<Set<String>> policyState = PolicyEngineUtils.getStringSetPolicyState(
                    new NoArgsPolicyKey(USER_CONTROL_DISABLED_PACKAGES_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).contains(
                    PACKAGE_NAME);
            assertThat(dpc(sDeviceState).devicePolicyManager().getUserControlDisabledPackages(
                    dpc(sDeviceState).componentName()))
                    .contains(PACKAGE_NAME);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setUserControlDisabledPackages(
                    dpc(sDeviceState).componentName(), List.of());
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, null);
        }
    }

    private void assertPackageStopped(Package pkg, int processIdBeforeStopping)
            throws Exception {
        Poll.forValue("Package " + pkg + " stopped",
                        () -> isProcessRunning(pkg, processIdBeforeStopping))
                .toBeEqualTo(false)
                .errorOnFail()
                .await();
    }

    private void assertPackageNotStopped(Package pkg, int processIdBeforeStopping)
            throws Exception {
        assertWithMessage("Package %s stopped", pkg)
                .that(isProcessRunning(pkg, processIdBeforeStopping)).isTrue();
    }

    private boolean isProcessRunning(Package pkg, int processIdBeforeStopping) throws Exception {
        return pkg.runningProcesses().stream().anyMatch(p -> p.pid() == processIdBeforeStopping);
    }
}
