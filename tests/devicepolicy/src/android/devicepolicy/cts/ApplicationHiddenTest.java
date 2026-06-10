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

import static android.app.admin.DevicePolicyIdentifiers.APPLICATION_HIDDEN_POLICY;
import static android.app.admin.TargetUser.LOCAL_USER_ID;
import static android.content.Intent.ACTION_PACKAGE_ADDED;
import static android.content.Intent.ACTION_PACKAGE_REMOVED;
import static android.devicepolicy.cts.utils.PolicyEngineUtils.TRUE_MORE_RESTRICTIVE;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpcOnly;
import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.app.admin.PackagePolicyKey;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateResult;
import android.content.Intent;
import android.content.IntentFilter;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.os.Bundle;
import android.stats.devicepolicy.EventId;
import android.util.Log;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.enterprise.annotations.RequireHasPolicyExemptApps;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.policies.ApplicationHidden;
import com.android.bedstead.harrier.policies.ApplicationHiddenSystemOnly;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.utils.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.function.Function;

@RunWith(BedsteadJUnit4.class)
public class ApplicationHiddenTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String LOG_TAG = ApplicationHiddenTest.class.getName();

    private static final Package SYSTEM_PACKAGE =
            TestApis.packages().find("com.android.cts.ctsshim");
    private static final Package NON_EXISTING_PACKAGE =
            TestApis.packages().find("non.existing.package");

    // TODO: All references to isApplicationHidden and setApplicationHidden which are not part of
    //  the "act" step of the test should run through a Nene API, once those APIs are permission
    //  accessible

    private static final IntentFilter sPackageAddedIntentFilter = new IntentFilter();
    private static final IntentFilter sPackageRemovedIntentFilter = new IntentFilter();
    static {
        sPackageAddedIntentFilter.addAction(ACTION_PACKAGE_ADDED);
        sPackageRemovedIntentFilter.addAction(ACTION_PACKAGE_REMOVED);
        sPackageAddedIntentFilter.addDataScheme("package");
        sPackageRemovedIntentFilter.addDataScheme("package");
    }

    @Before
    public void ensureSystemPackageInstalled() {
        SYSTEM_PACKAGE.installExisting(TestApis.users().instrumented());
        try {
            SYSTEM_PACKAGE.installExisting(dpc(sDeviceState).user());
        } catch (Exception e) {
            // expected for non DPC states
        }
    }

    @CanSetPolicyTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class})
    public void isApplicationHidden_systemApp_isHidden_returnsTrue() {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    true);

            assertThat(dpc(sDeviceState).devicePolicyManager().isApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName())).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    false);
        }
    }

    @CanSetPolicyTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class})
    public void isApplicationHidden_systemApp_isNotHidden_returnsFalse() {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    false);

            assertThat(dpc(sDeviceState).devicePolicyManager().isApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName())).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    false);
        }
    }

    @CanSetPolicyTest(policy = ApplicationHiddenSystemOnly.class)
    @EnsureTestAppInstalled
    public void isApplicationHidden_notSystemApp_throwsException() {
        // Could be SecurityException or IllegalArgumentException
        assertThrows(Exception.class, () -> dpc(sDeviceState).devicePolicyManager()
                .isApplicationHidden(dpc(sDeviceState).componentName(),
                        testApp(sDeviceState).packageName()));
    }

    @CannotSetPolicyTest(policy = ApplicationHidden.class)
    @EnsureTestAppInstalled
    public void isApplicationHidden_notPermitted_throwsException() {
        // Could be SecurityException or IllegalArgumentException
        assertThrows(Exception.class, () -> dpc(sDeviceState).devicePolicyManager()
                .isApplicationHidden(dpc(sDeviceState).componentName(),
                        testApp(sDeviceState).packageName()));
    }

    @CanSetPolicyTest(policy = ApplicationHidden.class)
    @EnsureTestAppInstalled
    public void isApplicationHidden_notSystemApp_isHidden_returnsTrue() {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    true);

            assertThat(dpc(sDeviceState).devicePolicyManager().isApplicationHidden(
                    dpc(sDeviceState).componentName(),
                    testApp(sDeviceState).packageName())).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    false);
        }
    }

    @CanSetPolicyTest(policy = ApplicationHidden.class)
    @EnsureTestAppInstalled
    public void isApplicationHidden_notSystemApp_isNotHidden_returnsFalse() {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    false);

            assertThat(dpc(sDeviceState).devicePolicyManager().isApplicationHidden(
                    dpc(sDeviceState).componentName(),
                    testApp(sDeviceState).packageName())).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    false);
        }
    }

    @PolicyAppliesTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class})
    public void setApplicationHidden_systemApp_true_hidesApplication() throws Exception {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    false);

            try (BlockingBroadcastReceiver broadcastReceiver =
                         sDeviceState.registerBroadcastReceiverForAllUsers(
                                 sPackageRemovedIntentFilter,
                                 isSchemeSpecificPart(SYSTEM_PACKAGE.packageName()))) {
                boolean result = dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                        dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                        true);

                assertThat(result).isTrue();
                broadcastReceiver.awaitForBroadcastOrFail();
            }

            assertThat(SYSTEM_PACKAGE.installedOnUser()).isFalse();
            assertThat(SYSTEM_PACKAGE.exists()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    false);
        }
    }

    @PolicyDoesNotApplyTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class})
    public void setApplicationHidden_systemApp_true_applicationIsNotHidden() throws Exception {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    true);

            assertThat(SYSTEM_PACKAGE.installedOnUser()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    false);
        }
    }

    @PolicyAppliesTest(policy = ApplicationHidden.class)
    @EnsureTestAppInstalled
    public void setApplicationHidden_nonSystemApp_true_hidesApplication() throws Exception {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    false);

            try (BlockingBroadcastReceiver broadcastReceiver =
                         sDeviceState.registerBroadcastReceiverForAllUsers(
                                 sPackageRemovedIntentFilter,
                                 isSchemeSpecificPart(testApp(sDeviceState).packageName()))) {

                boolean result = dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                        dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                        true);

                assertThat(result).isTrue();
                broadcastReceiver.awaitForBroadcastOrFail();
            }

            assertThat(testApp(sDeviceState).testApp().pkg().installedOnUser()).isFalse();
            assertThat(testApp(sDeviceState).testApp().pkg().exists()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    false);
        }
    }

    @PolicyAppliesTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class})
    public void setApplicationHidden_systemApp_false_unHidesApplication() throws Exception {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    true);

            try (BlockingBroadcastReceiver broadcastReceiver =
                         sDeviceState.registerBroadcastReceiverForAllUsers(
                                 sPackageAddedIntentFilter,
                                 isSchemeSpecificPart(SYSTEM_PACKAGE.packageName()))) {

                boolean result = dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                        dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                        false);

                assertThat(result).isTrue();
                broadcastReceiver.awaitForBroadcastOrFail();
            }

            assertThat(SYSTEM_PACKAGE.installedOnUser()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    false);
        }
    }

    @PolicyAppliesTest(policy = ApplicationHidden.class)
    @EnsureTestAppInstalled
    public void setApplicationHidden_nonSystemApp_false_unHidesApplication() throws Exception {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    true);

            try (BlockingBroadcastReceiver broadcastReceiver =
                         sDeviceState.registerBroadcastReceiverForAllUsers(
                                 sPackageAddedIntentFilter,
                                 isSchemeSpecificPart(testApp(sDeviceState).packageName()))) {

                boolean result = dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                        dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                        false);

                assertThat(result).isTrue();
                broadcastReceiver.awaitForBroadcastOrFail();
            }

            assertThat(testApp(sDeviceState).testApp().pkg().installedOnUser()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    false);
        }
    }

    @CanSetPolicyTest(policy = ApplicationHidden.class)
    @RequireHasPolicyExemptApps
    public void setApplicationHidden_nonSystemApp_policyExempt_doesNotHideApplication() throws Exception {
        Set<String> policyExemptApps = TestApis.devicePolicy().getPolicyExemptApps();

        for (String packageName : policyExemptApps) {
            if (!TestApis.packages().find(packageName).installedOnUser()) {
                Log.i(LOG_TAG, "Skipping " + packageName + " as not installed on user");
                continue;
            }
            try {
                boolean result = dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                        dpc(sDeviceState).componentName(), packageName,
                        true);

                assertWithMessage(
                        "Should return false when trying to hide policy exempt app " + packageName)
                        .that(result).isFalse();
                assertWithMessage(
                        "Policy exempt app " + packageName + " should appear as installed")
                        .that(TestApis.packages().find(packageName).installedOnUser()).isTrue();
            } finally {
                dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                        dpc(sDeviceState).componentName(), packageName,
                        false);
            }
        }
    }

    @CannotSetPolicyTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class}
            , includeNonDeviceAdminStates = false)
    public void setApplicationHidden_systemApp_notAllowed_throwsException() {
        try {
            assertThrows(SecurityException.class,
                    () -> dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                            dpc(sDeviceState).componentName(),
                            SYSTEM_PACKAGE.packageName(), true));
        } finally {
            try {
                dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                        dpc(sDeviceState).componentName(),
                        SYSTEM_PACKAGE.packageName(), false);
            } catch (SecurityException ex) {
                // Expected on successful tests
            }
        }
    }

    @CanSetPolicyTest(policy = ApplicationHiddenSystemOnly.class)
    @EnsureTestAppInstalled
    public void setApplicationHidden_nonSystemApp_throwsException() {
        try {
            // Could be SecurityException or IllegalArgumentException
            assertThrows(Exception.class,
                    () -> dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                            dpc(sDeviceState).componentName(),
                            testApp(sDeviceState).packageName(), true));
        } finally {
            try {
                dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                                dpc(sDeviceState).componentName(),
                                testApp(sDeviceState).packageName(), false);
            } catch (Exception ex) {
                // Expected on success case
            }
        }
    }

    @CanSetPolicyTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class})
    public void setApplicationHidden_true_logsEvent() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    true);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_APPLICATION_HIDDEN_VALUE)
                    .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
                    .whereBoolean().isEqualTo(dpc(sDeviceState).isDelegate())
                    .whereStrings().contains(SYSTEM_PACKAGE.packageName())
                    .whereStrings().contains("hidden")
                    .whereStrings().contains(dpc(sDeviceState).isParentInstance() ? "calledFromParent" : "notCalledFromParent")
            ).wasLogged();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    false);
        }
    }

    @CanSetPolicyTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class})
    public void setApplicationHidden_false_logsEvent() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    false);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                            .whereType().isEqualTo(EventId.SET_APPLICATION_HIDDEN_VALUE)
                            .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
                            .whereBoolean().isEqualTo(dpc(sDeviceState).isDelegate())
                            .whereStrings().contains(SYSTEM_PACKAGE.packageName())
                            .whereStrings().contains("not_hidden")
                            .whereStrings().contains(dpc(sDeviceState).isParentInstance() ? "calledFromParent" : "notCalledFromParent")
            ).wasLogged();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), SYSTEM_PACKAGE.packageName(),
                    false);
        }
    }

    @CanSetPolicyTest(policy = {ApplicationHidden.class})
    public void setApplicationHidden_notInstalledPackage_returnsFalse() {
        try {
            boolean result = dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), NON_EXISTING_PACKAGE.packageName(),
                    true);

            assertThat(result).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), NON_EXISTING_PACKAGE.packageName(),
                    false);
        }
    }

    @CanSetPolicyTest(policy = {ApplicationHidden.class})
    @Ignore // No longer applicable for non-admins - need to add a permission/exemption
    public void setApplicationHidden_deviceAdmin_returnsFalse() {
        try {
            boolean result = dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), dpcOnly(sDeviceState).packageName(),
                    true);

            assertThat(result).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), dpcOnly(sDeviceState).packageName(),
                    false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationHidden",
            "android.app.admin.DevicePolicyManager#isApplicationHidden",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @PolicyAppliesTest(policy = ApplicationHidden.class)
    @EnsureTestAppInstalled
    public void getDevicePolicyState_setApplicationHidden_returnsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    /* applicationHidden= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new PackagePolicyKey(
                            APPLICATION_HIDDEN_POLICY, testApp(sDeviceState).packageName()),
                    TestApis.users().instrumented().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    /* applicationHidden= */ false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationHidden",
            "android.app.admin.DevicePolicyManager#isApplicationHidden"})
    // TODO: enable after adding the broadcast receiver to relevant test apps.
//    @PolicyAppliesTest(policy = ApplicationHidden.class)
    @EnsureHasDeviceOwner(isPrimary = true)
    @EnsureTestAppInstalled
    public void policyUpdateReceiver_setApplicationHidden_receivedPolicySetBroadcast() {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    /* applicationHidden= */ true);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    APPLICATION_HIDDEN_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, new Bundle());
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    /* applicationHidden= */ false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setApplicationHidden",
            "android.app.admin.DevicePolicyManager#isApplicationHidden",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @CanSetPolicyTest(policy = ApplicationHidden.class, singleTestOnly = true)
    @EnsureTestAppInstalled
    public void getDevicePolicyState_setApplicationHidden_returnsCorrectResolutionMechanism() {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    /* applicationHidden= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new PackagePolicyKey(
                            APPLICATION_HIDDEN_POLICY, testApp(sDeviceState).packageName()),
                    TestApis.users().instrumented().userHandle());

            assertThat(PolicyEngineUtils.getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), testApp(sDeviceState).packageName(),
                    /* applicationHidden= */ false);
        }
    }

    @CanSetPolicyTest(policy = {ApplicationHidden.class})
    @Ignore // Currently failing for admins as well, but also longer applicable for non-admins -
    // need to add a permission/exemption
    public void setApplicationHidden_deviceAdmin_notAddedToDevicePolicyState() {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), dpcOnly(sDeviceState).packageName(),
                    true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new PackagePolicyKey(
                            APPLICATION_HIDDEN_POLICY, dpcOnly(sDeviceState).packageName()),
                    TestApis.users().instrumented().userHandle());

            assertThat(policyState).isNull();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), dpcOnly(sDeviceState).packageName(),
                    false);
        }
    }

    @CanSetPolicyTest(policy = {ApplicationHidden.class})
    @Ignore
    public void setApplicationHidden_notInstalledPackage_notAddedToDevicePolicyState() {
        try {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), NON_EXISTING_PACKAGE.packageName(),
                    true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new PackagePolicyKey(
                            APPLICATION_HIDDEN_POLICY, NON_EXISTING_PACKAGE.packageName()),
                    TestApis.users().instrumented().userHandle());

            assertThat(policyState).isNull();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setApplicationHidden(
                    dpc(sDeviceState).componentName(), NON_EXISTING_PACKAGE.packageName(),
                    false);
        }
    }

    private Function<Intent, Boolean> isSchemeSpecificPart(String part) {
        return (intent) -> intent.getData() != null
                && intent.getData().getSchemeSpecificPart().equals(part);
    }
}
