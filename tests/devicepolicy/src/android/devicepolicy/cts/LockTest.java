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

import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.flags.Flags;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet;
import com.android.bedstead.harrier.annotations.EnsurePasswordSet;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.policies.LockNow;
import com.android.bedstead.harrier.policies.MaximumTimeToLock;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
@RequireFeature("android.software.secure_lock_screen")
public class LockTest {

    private static final long TIMEOUT = 10000;

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);
    private static final KeyguardManager sLocalKeyguardManager =
            TestApis.context().instrumentedContext().getSystemService(KeyguardManager.class);

    @CannotSetPolicyTest(policy = LockNow.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#lockNow")
    public void lockNow_notPermitted_throwsException() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(dpc(sDeviceState).componentName() != null || Flags.lockNowCoexistence());

        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().lockNow());
    }

    @PolicyAppliesTest(policy = LockNow.class)
    @Postsubmit(reason = "New test")
    @EnsurePasswordNotSet
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#lockNow")
    public void lockNow_logsMetric() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(dpc(sDeviceState).componentName() != null || Flags.lockNowCoexistence());

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().lockNow(/* flags= */ 0);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.LOCK_NOW_VALUE)
                    .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
                    .whereInteger().isEqualTo(0)
            ).wasLogged();
        }
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordNotSet
    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = LockNow.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#lockNow")
    public void lockNow_noPasswordSet_turnsScreenOff() throws Exception {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(dpc(sDeviceState).componentName() != null || Flags.lockNowCoexistence());

        Assume.assumeFalse("LockNow on profile won't turn off screen",
                dpc(sDeviceState).user().isProfile());
        dpc(sDeviceState).devicePolicyManager().lockNow();

        Poll.forValue("isScreenOn", () -> TestApis.device().isScreenOn())
                .toBeEqualTo(false)
                .errorOnFail()
                .await();
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordNotSet
    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = LockNow.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#lockNow")
    public void lockNow_automotive_noPasswordSet_doesNotTurnScreenOff() throws Exception {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(dpc(sDeviceState).componentName() != null || Flags.lockNowCoexistence());

        dpc(sDeviceState).devicePolicyManager().lockNow();

        assertThat(TestApis.device().isScreenOn()).isTrue();
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = LockNow.class)
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#lockNow")
    public void lockNow_passwordSet_locksDevice() throws Exception {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(dpc(sDeviceState).componentName() != null || Flags.lockNowCoexistence());

        dpc(sDeviceState).devicePolicyManager().lockNow();

        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    @CannotSetPolicyTest(policy = MaximumTimeToLock.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setMaximumTimeToLock")
    public void setMaximumTimeToLock_notPermitted_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager()
                        .setMaximumTimeToLock(dpc(sDeviceState).componentName(), TIMEOUT));
    }

    @PolicyAppliesTest(policy = MaximumTimeToLock.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setMaximumTimeToLock",
            "android.app.admin.DevicePolicyManager#getMaximumTimeToLock"})
    @Ignore // Incorrect logic
    public void setMaximumTimeToLock_maximumTimeToLockIsSet() {
        long originalTimeout = dpc(sDeviceState).devicePolicyManager()
                .getMaximumTimeToLock(dpc(sDeviceState).componentName());

        assertThat(TestApis.devicePolicy().getMaximumTimeToLock()).isEqualTo(TIMEOUT);

        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setMaximumTimeToLock(dpc(sDeviceState).componentName(), TIMEOUT);

        } finally {
            dpc(sDeviceState).devicePolicyManager().setMaximumTimeToLock(
                    dpc(sDeviceState).componentName(), originalTimeout);
        }
    }

    @PolicyDoesNotApplyTest(policy = MaximumTimeToLock.class)
    @Postsubmit(reason = "New test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setMaximumTimeToLock",
            "android.app.admin.DevicePolicyManager#getMaximumTimeToLock"})
    public void setMaximumTimeToLock_doesNotApply_maximumTimeToLockIsNotSet() {
        long originalTimeout = dpc(sDeviceState).devicePolicyManager()
                .getMaximumTimeToLock(dpc(sDeviceState).componentName());

        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setMaximumTimeToLock(dpc(sDeviceState).componentName(), TIMEOUT);

            assertThat(TestApis.devicePolicy().getMaximumTimeToLock()).isNotEqualTo(TIMEOUT);

        } finally {
            dpc(sDeviceState).devicePolicyManager().setMaximumTimeToLock(
                    dpc(sDeviceState).componentName(), originalTimeout);
        }
    }
}
