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

import static android.app.admin.DevicePolicyIdentifiers.SCREEN_CAPTURE_DISABLED_POLICY;
import static android.app.admin.TargetUser.GLOBAL_USER_ID;
import static android.devicepolicy.cts.utils.PolicyEngineUtils.TRUE_MORE_RESTRICTIVE;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApps;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.app.admin.NoArgsPolicyKey;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.stats.devicepolicy.EventId;

import androidx.test.InstrumentationRegistry;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.EnsureUnlocked;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.policies.ScreenCaptureDisabled;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
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

import java.time.Duration;

@RunWith(BedsteadJUnit4.class)
public final class ScreenCaptureDisabledTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);
    private static final TestApp sTestApp =
            testApps(sDeviceState).query().whereActivities().isNotEmpty().get();

    /** See {@code DevicePolicyManager#POLICY_DISABLE_SCREEN_CAPTURE} */
    private static final String POLICY_DISABLE_SCREEN_CAPTURE = "policy_disable_screen_capture";

    /**
     * see {@code DevicePolicyManager.EXTRA_RESTRICTION}
     */
    private static final String EXTRA_RESTRICTION = "android.app.extra.RESTRICTION";

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_false_works() {
        dpc(sDeviceState).devicePolicyManager()
                .setScreenCaptureDisabled(dpc(sDeviceState).componentName(), false);

        assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null)).isFalse();
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_false_checkWithDPC_works() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);

            assertThat(dpc(sDeviceState).devicePolicyManager().getScreenCaptureDisabled(
                    dpc(sDeviceState).componentName())).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @CannotSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_false_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager()
                        .setScreenCaptureDisabled(dpc(sDeviceState).componentName(), false));
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_true_works() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(
                    /* admin= */ null)).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_true_checkWithDPC_works() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(dpc(sDeviceState).devicePolicyManager().getScreenCaptureDisabled(
                    dpc(sDeviceState).componentName())).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @PolicyDoesNotApplyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    public void setScreenCaptureDisabled_true_doesNotApply() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */
                    null)).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @PolicyDoesNotApplyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureScreenIsOn
    @EnsureUnlocked
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_true_screenCaptureNoRedactionOrNull() {
        Assume.assumeTrue("Requires showing an activity",
                TestApis.users().instrumented().canShowActivities());
        try {

            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(takeScreenshotExpectingNoRedactionOrNull()).isFalse();

        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureScreenIsOn
    @EnsureUnlocked
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_true_screenCaptureRedactedOrNull() {
        Assume.assumeTrue("Requires showing an activity",
                TestApis.users().instrumented().canShowActivities());
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(takeScreenshotExpectingRedactionOrNull()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureScreenIsOn
    @EnsureUnlocked
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_false_screenCaptureNoRedactionOrNull() {
        Assume.assumeTrue("Requires showing an activity",
                TestApis.users().instrumented().canShowActivities());
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);

            assertThat(takeScreenshotExpectingNoRedactionOrNull()).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_true_metricsLogged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_SCREEN_CAPTURE_DISABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            dpc(sDeviceState).packageName())
                    .whereBoolean().isTrue()).wasLogged();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setScreenCaptureDisabled")
    public void setScreenCaptureDisabled_false_metricsLogged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_SCREEN_CAPTURE_DISABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            dpc(sDeviceState).packageName())
                    .whereBoolean().isFalse()).wasLogged();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    public void getDevicePolicyState_setScreenCaptureDisabled_returnsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    // TODO: enable after adding the broadcast receiver to relevant test apps.
//    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @EnsureHasDeviceOwner(isPrimary = true)
    public void policyUpdateReceiver_setScreenCaptureDisabled_receivedPolicySetBroadcast() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ true);

            PolicySetResultUtils.assertPolicySetResultReceived(
                    sDeviceState,
                    SCREEN_CAPTURE_DISABLED_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, GLOBAL_USER_ID, new Bundle());
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class, singleTestOnly = true)
    public void getDevicePolicyState_setScreenCaptureDisabled_returnsCorrectResolutionMechanism() {
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());

            assertThat(PolicyEngineUtils.getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ false);
        }
    }

    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    @Postsubmit(reason = "new test")
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled",
            "android.app.admin.DevicePolicyManager#getScreenCaptureDisabled"})
    @Ignore // need to restore with some root-only capability to force migration
    public void setScreenCaptureDisabled_policyMigration_works() {
        try {
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "false");
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ true);

            sLocalDevicePolicyManager.triggerDevicePolicyEngineMigration(true);
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "true");

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new NoArgsPolicyKey(SCREEN_CAPTURE_DISABLED_POLICY),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThat(
                    sLocalDevicePolicyManager.getScreenCaptureDisabled(/* admin= */ null)).isTrue();

        } finally {
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "false");
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ false);
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "true");
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ false);
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, null);
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.app.admin.DevicePolicyManager#POLICY_DISABLE_SCREEN_CAPTURE"})
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    public void createAdminSupportIntent_disallowScreenCapture_createsIntent() {
        boolean originalScreenCaptureDisabledStatus = dpc(sDeviceState).devicePolicyManager()
                .getScreenCaptureDisabled(dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ true);

            Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                    POLICY_DISABLE_SCREEN_CAPTURE);

            assertThat(intent.getStringExtra(EXTRA_RESTRICTION)).isEqualTo(
                    POLICY_DISABLE_SCREEN_CAPTURE);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), originalScreenCaptureDisabledStatus
            );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.app.admin.DevicePolicyManager#POLICY_DISABLE_SCREEN_CAPTURE"})
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = ScreenCaptureDisabled.class)
    public void createAdminSupportIntent_allowScreenCapture_doesNotCreate() {
        boolean originalScreenCaptureDisabledStatus = dpc(sDeviceState).devicePolicyManager()
                .getScreenCaptureDisabled(dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ false);

            Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                    POLICY_DISABLE_SCREEN_CAPTURE);

            assertThat(intent).isNull();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), originalScreenCaptureDisabledStatus
            );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    public void setScreenCaptureDisabled_true_wasLogged() {
        boolean originalScreenCaptureDisabledStatus = dpc(sDeviceState).devicePolicyManager()
                .getScreenCaptureDisabled(dpc(sDeviceState).componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            boolean isParentInstance = dpc(sDeviceState).isParentInstance();

            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ true);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_SCREEN_CAPTURE_DISABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
                    .whereBoolean().isEqualTo(true))
                    .wasLogged();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), originalScreenCaptureDisabledStatus
            );
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setScreenCaptureDisabled"})
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = ScreenCaptureDisabled.class)
    public void setScreenCaptureDisabled_false_wasLogged() {
        boolean originalScreenCaptureDisabledStatus = dpc(sDeviceState).devicePolicyManager()
                .getScreenCaptureDisabled(dpc(sDeviceState).componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            boolean isParentInstance = dpc(sDeviceState).isParentInstance();

            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), /* disabled= */ false);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_SCREEN_CAPTURE_DISABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
                    .whereBoolean().isEqualTo(false))
                    .wasLogged();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setScreenCaptureDisabled(
                    dpc(sDeviceState).componentName(), originalScreenCaptureDisabledStatus
            );
        }
    }

    private boolean takeScreenshotExpectingRedactionOrNull() {
        try (TestAppInstance testApp = sTestApp.install()) {
            // We show an activity on the current user, which should be redacted if the screen
            // capture disabled policy is applying to this user.
            testApp.activities().any().start();
            return Poll.forValue(
                    () -> checkScreenshotIsRedactedOrNull(sUiAutomation.takeScreenshot())).timeout(
                    Duration.ofMinutes(2)).toBeEqualTo(true).await();
        }
    }

    private boolean takeScreenshotExpectingNoRedactionOrNull() {
        try (TestAppInstance testApp = sTestApp.install()) {
            // We show an activity on the current user, which should be redacted if the screen
            // capture disabled policy is applying to this user.
            testApp.activities().any().start();
            return Poll.forValue(
                    () -> checkScreenshotIsRedactedOrNull(sUiAutomation.takeScreenshot())).timeout(
                    Duration.ofMinutes(2)).toBeEqualTo(false).await();
        }
    }

    private boolean checkScreenshotIsRedactedOrNull(Bitmap screenshot) {
        if (screenshot == null) {
            return true;
        }
        int width = screenshot.getWidth();
        int height = screenshot.getHeight();

        // Getting pixels of only the middle part(from y  = height/4 to 3/4(height)) of the
        // screenshot to check(screenshot is redacted) for only the middle part of the screen,
        // as there could be notifications in the top part and white line(navigation bar) at bottom
        // which are included in the screenshot and are not redacted(black). It's not perfect, but
        // seems best option to avoid any flakiness at this point.
        int len = width * (height / 2);
        int[] pixels = new int[len];
        screenshot.getPixels(pixels, 0, width, 0, height / 4, width, height / 2);

        for (int i = 0; i < len; ++i) {
            // Skip some pixels from the right to accommodate for the edge panel(present on
            // some devices) which will not be redacted in the screenshot.
            if ((i % width) /* X-position */ > (width - 34)) {
                // skipping edge panel
                continue;
            }
            if (!(pixels[i] == Color.BLACK || (
                    (pixels[i] == Color.TRANSPARENT || pixels[i] == Color.WHITE)
                            && isAutomotive()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAutomotive() {
        return TestApis.context().instrumentedContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

}
