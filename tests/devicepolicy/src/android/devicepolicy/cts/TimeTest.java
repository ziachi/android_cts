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

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_DATE_TIME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.RemoteDevicePolicyManager;
import android.app.admin.flags.Flags;
import android.content.ComponentName;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.enterprise.annotations.EnsureHasUserRestriction;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.policies.AutoTimeEnabled;
import com.android.bedstead.harrier.policies.AutoTimeRequired;
import com.android.bedstead.harrier.policies.AutoTimeZoneEnabled;
import com.android.bedstead.harrier.policies.DisallowConfigDateTime;
import com.android.bedstead.harrier.policies.Time;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;
import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.annotations.Interactive;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.TimeZone;

@RunWith(BedsteadJUnit4.class)
public final class TimeTest {
    private static final long MILLIS_SINCE_EPOCH = 1660000000000l;
    private static final String TIMEZONE = "Singapore";

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @PolicyAppliesTest(policy = AutoTimeRequired.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeRequired")
    public void setAutoTimeRequired_false_setsAutoTimeNotRequired() {
        boolean originalValue = dpc(sDeviceState).devicePolicyManager().getAutoTimeRequired();

        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeRequired(
                    dpc(sDeviceState).componentName(), false);

            assertThat(TestApis.devicePolicy().autoTimeRequired()).isFalse();

        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeRequired(
                    dpc(sDeviceState).componentName(), originalValue);
        }
    }

    @PolicyAppliesTest(policy = AutoTimeRequired.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeRequired")
    public void setAutoTimeRequired_true_setsAutoTimeRequired() {
        boolean originalValue = dpc(sDeviceState).devicePolicyManager().getAutoTimeRequired();

        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeRequired(
                    dpc(sDeviceState).componentName(), true);

            assertThat(TestApis.devicePolicy().autoTimeRequired()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeRequired(
                    dpc(sDeviceState).componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = AutoTimeRequired.class, includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeRequired")
    public void setAutoTimeRequired_notAllowed_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().setAutoTimeRequired(
                        dpc(sDeviceState).componentName(), true));
    }

    @Postsubmit(reason = "New test")
    @PolicyDoesNotApplyTest(policy = AutoTimeRequired.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeRequired")
    public void setAutoTimeRequired_true_policyDoesNotApply_autoTimeIsNotRequired() {
        boolean originalValue = dpc(sDeviceState).devicePolicyManager().getAutoTimeRequired();

        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeRequired(
                    dpc(sDeviceState).componentName(), true);

            assertThat(TestApis.devicePolicy().autoTimeRequired()).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeRequired(
                    dpc(sDeviceState).componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = AutoTimeRequired.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeRequired")
    public void setAutoTimeRequired_true_logsEvent() {
        boolean originalValue = dpc(sDeviceState).devicePolicyManager().getAutoTimeRequired();
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeRequired(
                    dpc(sDeviceState).componentName(), true);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_AUTO_TIME_REQUIRED_VALUE)
                    .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
                    .whereBoolean().isTrue()).wasLogged();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeRequired(
                    dpc(sDeviceState).componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = AutoTimeRequired.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeRequired")
    public void setAutoTimeRequired_false_logsEvent() {
        boolean originalValue = dpc(sDeviceState).devicePolicyManager().getAutoTimeRequired();
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeRequired(
                    dpc(sDeviceState).componentName(), false);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_AUTO_TIME_REQUIRED_VALUE)
                    .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
                    .whereBoolean().isFalse()).wasLogged();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeRequired(
                    dpc(sDeviceState).componentName(), originalValue);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeEnabled")
    public void setAutoTimeEnabled_true_logsEvent() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null || Flags.setAutoTimeEnabledCoexistence());

        boolean originalValue = dpc(sDeviceState).devicePolicyManager().getAutoTimeEnabled(
                dpc(sDeviceState).componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                    dpc(sDeviceState).componentName(), true);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_AUTO_TIME_VALUE)
                    .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
                    .whereBoolean().isTrue()).wasLogged();
        } finally {
            if (Flags.setAutoTimeEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager()
                        .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(
                        Settings.Global.AUTO_TIME, originalValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                        dpc(sDeviceState).componentName(), originalValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeEnabled")
    public void setAutoTimeEnabled_false_logsEvent() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null || Flags.setAutoTimeEnabledCoexistence());

        boolean originalValue = dpc(sDeviceState).devicePolicyManager().getAutoTimeEnabled(
                dpc(sDeviceState).componentName());

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                    dpc(sDeviceState).componentName(), false);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_AUTO_TIME_VALUE)
                    .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
                    .whereBoolean().isFalse()).wasLogged();
        } finally {
            if (Flags.setAutoTimeEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager()
                        .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(
                        Settings.Global.AUTO_TIME, originalValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                        dpc(sDeviceState).componentName(), originalValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeEnabled")
    public void setAutoTimeEnabled_true_autoTimeIsEnabled() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null || Flags.setAutoTimeEnabledCoexistence());

        boolean originalValue = dpc(sDeviceState).devicePolicyManager().getAutoTimeEnabled(
                dpc(sDeviceState).componentName());

        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(TestApis.settings().global()
                    .getInt(Settings.Global.AUTO_TIME, 0)).isEqualTo(1);
        } finally {
            if (Flags.setAutoTimeEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager()
                        .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(
                        Settings.Global.AUTO_TIME, originalValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                        dpc(sDeviceState).componentName(), originalValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeEnabled")
    public void setAutoTimeEnabled_false_autoTimeIsNotEnabled() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null || Flags.setAutoTimeEnabledCoexistence());

        boolean originalValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeEnabled(dpc(sDeviceState).componentName());

        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                    dpc(sDeviceState).componentName(), false);

            assertThat(TestApis.settings().global().getInt(
                    Settings.Global.AUTO_TIME, 0)).isEqualTo(0);
        } finally {
            if (Flags.setAutoTimeEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager()
                        .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(
                        Settings.Global.AUTO_TIME, originalValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                        dpc(sDeviceState).componentName(), originalValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeEnabled")
    public void getAutoTimeEnabled_returnsAutoTimeEnabled() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null || Flags.setAutoTimeEnabledCoexistence());

        boolean originalValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeEnabled(dpc(sDeviceState).componentName());

        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .getAutoTimeEnabled(dpc(sDeviceState).componentName())).isEqualTo(true);
        } finally {
            if (Flags.setAutoTimeEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager()
                        .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(
                        Settings.Global.AUTO_TIME, originalValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                        dpc(sDeviceState).componentName(), originalValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = AutoTimeEnabled.class, includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeEnabled")
    public void setAutoTimeEnabled_notAllowed_throwsException() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null || Flags.setAutoTimeEnabledCoexistence());

        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager()
                        .setAutoTimeEnabled(dpc(sDeviceState).componentName(), true));
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = AutoTimeEnabled.class, includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#getAutoTimeEnabled")
    public void getAutoTimeEnabled_notAllowed_throwsException() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(
                dpc(sDeviceState).componentName() != null || Flags.setAutoTimeEnabledCoexistence());

        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager()
                        .getAutoTimeEnabled(dpc(sDeviceState).componentName()));
    }

    @Postsubmit(reason = "New test")
    @RequiresFlagsEnabled(Flags.FLAG_SET_AUTO_TIME_ENABLED_COEXISTENCE)
    @PolicyAppliesTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimePolicy")
    public void setAutoTimePolicy_enabled_autoTimeIsEnabled() {
        int originalSetting =
                TestApis.settings().global().getInt(Settings.Global.AUTO_TIME, 0);
        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimePolicy(
                    DevicePolicyManager.AUTO_TIME_ENABLED);

            assertThat(TestApis.settings().global()
                    .getInt(Settings.Global.AUTO_TIME, 0)).isEqualTo(1);
        } finally {
            dpc(sDeviceState).devicePolicyManager()
                    .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
            TestApis.settings().global().putInt(Settings.Global.AUTO_TIME, originalSetting);
        }
    }

    @Postsubmit(reason = "New test")
    @RequiresFlagsEnabled(Flags.FLAG_SET_AUTO_TIME_ENABLED_COEXISTENCE)
    @PolicyAppliesTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimePolicy")
    public void setAutoTimePolicy_disabled_autoTimeIsNotEnabled() {
        int originalSetting =
                TestApis.settings().global().getInt(Settings.Global.AUTO_TIME, 0);
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_DISABLED);

            assertThat(TestApis.settings().global().getInt(
                    Settings.Global.AUTO_TIME, 0)).isEqualTo(0);
        } finally {
            dpc(sDeviceState).devicePolicyManager()
                    .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
            TestApis.settings().global().putInt(Settings.Global.AUTO_TIME, originalSetting);
        }
    }

    @Postsubmit(reason = "New test")
    @RequiresFlagsEnabled(Flags.FLAG_SET_AUTO_TIME_ENABLED_COEXISTENCE)
    @PolicyAppliesTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimePolicy")
    public void setAutoTimePolicy_notControlled_autoTimePolicyIsNotControlled() {
        int originalSetting =
                TestApis.settings().global().getInt(Settings.Global.AUTO_TIME, 0);
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);

            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .getAutoTimePolicy())
                    .isEqualTo(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
        } finally {
            dpc(sDeviceState).devicePolicyManager()
                    .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
            TestApis.settings().global().putInt(Settings.Global.AUTO_TIME, originalSetting);
        }
    }

    @Postsubmit(reason = "New test")
    @RequiresFlagsEnabled(Flags.FLAG_SET_AUTO_TIME_ENABLED_COEXISTENCE)
    @PolicyAppliesTest(policy = AutoTimeEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimePolicy")
    public void getAutoTimePolicy_returnsAutoTimeEnabled() {
        int originalSetting =
                TestApis.settings().global().getInt(Settings.Global.AUTO_TIME, 0);
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_ENABLED);

            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .getAutoTimePolicy())
                    .isEqualTo(DevicePolicyManager.AUTO_TIME_ENABLED);
        } finally {
            dpc(sDeviceState).devicePolicyManager()
                    .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
            TestApis.settings().global().putInt(Settings.Global.AUTO_TIME, originalSetting);
        }
    }

    @Postsubmit(reason = "New test")
    @RequiresFlagsEnabled(Flags.FLAG_SET_AUTO_TIME_ENABLED_COEXISTENCE)
    @CannotSetPolicyTest(policy = AutoTimeEnabled.class, includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimePolicy")
    public void setAutoTimePolicy_notAllowed_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager()
                        .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_ENABLED));
    }

    @Postsubmit(reason = "New test")
    @RequiresFlagsEnabled(Flags.FLAG_SET_AUTO_TIME_ENABLED_COEXISTENCE)
    @CannotSetPolicyTest(policy = AutoTimeEnabled.class, includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#getAutoTimePolicy")
    public void getAutoTimePolicy_notAllowed_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().getAutoTimePolicy());
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = AutoTimeZoneEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZoneEnabled")
    public void setAutoTimeZoneEnabled_true_autoTimeZoneIsEnabled() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(dpc(sDeviceState).componentName() != null
                || Flags.setAutoTimeZoneEnabledCoexistence());

        boolean originalValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeZoneEnabled(dpc(sDeviceState).componentName());

        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(TestApis.settings().global().getInt(
                    Settings.Global.AUTO_TIME_ZONE, 0)).isEqualTo(1);
        } finally {
            if (Flags.setAutoTimeZoneEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZonePolicy(
                        DevicePolicyManager.AUTO_TIME_ZONE_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(
                        Settings.Global.AUTO_TIME_ZONE, originalValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                        dpc(sDeviceState).componentName(), originalValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = AutoTimeZoneEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZoneEnabled")
    public void setAutoTimeZoneEnabled_false_autoTimeZoneIsNotEnabled() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(dpc(sDeviceState).componentName() != null
                || Flags.setAutoTimeZoneEnabledCoexistence());

        boolean originalValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeZoneEnabled(dpc(sDeviceState).componentName());

        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), false);

            assertThat(TestApis.settings().global().getInt(
                    Settings.Global.AUTO_TIME_ZONE, 0)).isEqualTo(0);
        } finally {
            if (Flags.setAutoTimeZoneEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZonePolicy(
                        DevicePolicyManager.AUTO_TIME_ZONE_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(
                        Settings.Global.AUTO_TIME_ZONE, originalValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                        dpc(sDeviceState).componentName(), originalValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = AutoTimeZoneEnabled.class, includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZoneEnabled")
    public void setAutoTimeZoneEnabled_notAllowed_throwsException() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(dpc(sDeviceState).componentName() != null
                || Flags.setAutoTimeZoneEnabledCoexistence());

        assertThrows(SecurityException.class, () -> dpc(sDeviceState).devicePolicyManager()
                .setAutoTimeZoneEnabled(dpc(sDeviceState).componentName(), true));
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = AutoTimeZoneEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZoneEnabled")
    public void setAutoTimeZoneEnabled_true_logsEvent() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(dpc(sDeviceState).componentName() != null
                || Flags.setAutoTimeZoneEnabledCoexistence());

        boolean originalValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeZoneEnabled(dpc(sDeviceState).componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), true);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_AUTO_TIME_ZONE_VALUE)
                    .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
                    .whereBoolean().isTrue()).wasLogged();
        } finally {
            if (Flags.setAutoTimeZoneEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZonePolicy(
                        DevicePolicyManager.AUTO_TIME_ZONE_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(
                        Settings.Global.AUTO_TIME_ZONE, originalValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                        dpc(sDeviceState).componentName(), originalValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = AutoTimeZoneEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZoneEnabled")
    public void setAutoTimeZoneEnabled_false_logsEvent() {
        // TODO(b/371032678): Remove assumption after flag rollout.
        assumeTrue(dpc(sDeviceState).componentName() != null
                || Flags.setAutoTimeZoneEnabledCoexistence());

        boolean originalValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeZoneEnabled(dpc(sDeviceState).componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), false);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_AUTO_TIME_ZONE_VALUE)
                    .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
                    .whereBoolean().isFalse()).wasLogged();
        } finally {
            if (Flags.setAutoTimeZoneEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZonePolicy(
                        DevicePolicyManager.AUTO_TIME_ZONE_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(
                        Settings.Global.AUTO_TIME_ZONE, originalValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                        dpc(sDeviceState).componentName(), originalValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @RequiresFlagsEnabled(Flags.FLAG_SET_AUTO_TIME_ZONE_ENABLED_COEXISTENCE)
    @PolicyAppliesTest(policy = AutoTimeZoneEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZonePolicy")
    public void setAutoTimeZonePolicy_enabled_autoTimeZoneIsEnabled() {
        int originalPolicy = dpc(sDeviceState).devicePolicyManager().getAutoTimeZonePolicy();
        int originalSetting =
                TestApis.settings().global().getInt(Settings.Global.AUTO_TIME_ZONE, 0);
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setAutoTimeZonePolicy(DevicePolicyManager.AUTO_TIME_ZONE_ENABLED);

            assertThat(TestApis.settings().global().getInt(
                    Settings.Global.AUTO_TIME_ZONE, 0)).isEqualTo(1);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZonePolicy(originalPolicy);
            TestApis.settings().global().putInt(Settings.Global.AUTO_TIME_ZONE, originalSetting);
        }
    }

    @Postsubmit(reason = "New test")
    @RequiresFlagsEnabled(Flags.FLAG_SET_AUTO_TIME_ZONE_ENABLED_COEXISTENCE)
    @PolicyAppliesTest(policy = AutoTimeZoneEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZonePolicy")
    public void setAutoTimeZonePolicy_disabled_autoTimeZoneIsNotEnabled() {
        int originalPolicy = dpc(sDeviceState).devicePolicyManager().getAutoTimeZonePolicy();
        int originalSetting =
                TestApis.settings().global().getInt(Settings.Global.AUTO_TIME_ZONE, 0);
        try {
            dpc(sDeviceState).devicePolicyManager()
                    .setAutoTimeZonePolicy(DevicePolicyManager.AUTO_TIME_ZONE_DISABLED);

            assertThat(TestApis.settings().global().getInt(
                    Settings.Global.AUTO_TIME_ZONE, 0)).isEqualTo(0);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZonePolicy(originalPolicy);
            TestApis.settings().global().putInt(Settings.Global.AUTO_TIME_ZONE, originalSetting);
        }
    }

    @Postsubmit(reason = "New test")
    @RequiresFlagsEnabled(Flags.FLAG_SET_AUTO_TIME_ZONE_ENABLED_COEXISTENCE)
    @PolicyAppliesTest(policy = AutoTimeZoneEnabled.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZonePolicy")
    public void setAutoTimeZonePolicy_notControlled_autoTimeZoneIsNotControlled() {
        int originalValue = dpc(sDeviceState).devicePolicyManager().getAutoTimeZonePolicy();
        int originalSetting =
                TestApis.settings().global().getInt(Settings.Global.AUTO_TIME_ZONE, 0);

        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZonePolicy(
                    DevicePolicyManager.AUTO_TIME_ZONE_NOT_CONTROLLED_BY_POLICY);

            assertThat(dpc(sDeviceState).devicePolicyManager().getAutoTimeZonePolicy())
                    .isEqualTo(DevicePolicyManager.AUTO_TIME_ZONE_NOT_CONTROLLED_BY_POLICY);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZonePolicy(originalValue);
            TestApis.settings().global().putInt(Settings.Global.AUTO_TIME_ZONE, originalSetting);
        }
    }

    @Postsubmit(reason = "New test")
    @RequiresFlagsEnabled(Flags.FLAG_SET_AUTO_TIME_ZONE_ENABLED_COEXISTENCE)
    @CannotSetPolicyTest(policy = AutoTimeZoneEnabled.class, includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setAutoTimeZonePolicy")
    public void setAutoTimeZonePolicy_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () -> dpc(sDeviceState).devicePolicyManager()
                .setAutoTimeZonePolicy(DevicePolicyManager.AUTO_TIME_ZONE_ENABLED));
    }

    // TODO: Add test of ACTION_TIME_CHANGED broadcast
    // TODO: Add test of ACTION_TIMEZONE_CHANGED broadcast

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = Time.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTime")
    public void setTime_timeIsSet() {
        boolean originalAutoTimeEnabledValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeEnabled(dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                    dpc(sDeviceState).componentName(), false);

            boolean returnValue = dpc(sDeviceState).devicePolicyManager()
                    .setTime(dpc(sDeviceState).componentName(), MILLIS_SINCE_EPOCH);

            assertThat(returnValue).isTrue();

            long currentTime = System.currentTimeMillis();
            long differenceSeconds = (currentTime - MILLIS_SINCE_EPOCH) / 1000;

            assertThat(differenceSeconds).isLessThan(120); // Within 2 minutes
        } finally {
            if (Flags.setAutoTimeEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager()
                        .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(
                        Settings.Global.AUTO_TIME, originalAutoTimeEnabledValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                        dpc(sDeviceState).componentName(), originalAutoTimeEnabledValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyDoesNotApplyTest(policy = Time.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTime")
    public void setTime_doesNotApply_timeIsNotSet() {
        boolean originalAutoTimeEnabledValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeEnabled(dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                    dpc(sDeviceState).componentName(), false);

            boolean returnValue = dpc(sDeviceState).devicePolicyManager()
                    .setTime(dpc(sDeviceState).componentName(), MILLIS_SINCE_EPOCH);

            assertThat(returnValue).isTrue();
            assertThat(System.currentTimeMillis()).isNotEqualTo(MILLIS_SINCE_EPOCH);
        } finally {
            if (Flags.setAutoTimeEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager()
                        .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(
                        Settings.Global.AUTO_TIME, originalAutoTimeEnabledValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                        dpc(sDeviceState).componentName(), originalAutoTimeEnabledValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = Time.class, singleTestOnly = true)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTime")
    public void setTime_autoTimeIsEnabled_returnsFalse() {
        boolean originalAutoTimeEnabledValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeEnabled(dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                    dpc(sDeviceState).componentName(), true);

            boolean returnValue = dpc(sDeviceState).devicePolicyManager()
                    .setTime(dpc(sDeviceState).componentName(), MILLIS_SINCE_EPOCH);

            assertThat(returnValue).isFalse();
            assertThat(System.currentTimeMillis()).isNotEqualTo(MILLIS_SINCE_EPOCH);
        } finally {
            if (Flags.setAutoTimeEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager()
                        .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(
                        Settings.Global.AUTO_TIME, originalAutoTimeEnabledValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                        dpc(sDeviceState).componentName(), originalAutoTimeEnabledValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = Time.class, includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTime")
    public void setTime_notAllowed_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager()
                        .setTime(dpc(sDeviceState).componentName(), MILLIS_SINCE_EPOCH));
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = Time.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTime")
    public void setTime_logsEvent() {
        boolean originalAutoTimeEnabledValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeEnabled(dpc(sDeviceState).componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                    dpc(sDeviceState).componentName(), false);

            dpc(sDeviceState).devicePolicyManager()
                    .setTime(dpc(sDeviceState).componentName(), MILLIS_SINCE_EPOCH);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_TIME_VALUE)
                    .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
            ).wasLogged();
        } finally {
            if (Flags.setAutoTimeEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager()
                        .setAutoTimePolicy(DevicePolicyManager.AUTO_TIME_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(
                        Settings.Global.AUTO_TIME, originalAutoTimeEnabledValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeEnabled(
                        dpc(sDeviceState).componentName(), originalAutoTimeEnabledValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = com.android.bedstead.harrier.policies.TimeZone.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTimeZone")
    public void setTimeZone_timeZoneIsSet() {
        boolean originalAutoTimeZoneEnabledValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeZoneEnabled(dpc(sDeviceState).componentName());
        try {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), false);

            boolean returnValue = dpc(sDeviceState).devicePolicyManager()
                    .setTimeZone(dpc(sDeviceState).componentName(), TIMEZONE);

            assertThat(returnValue).isTrue();
            Poll.forValue("timezone ID", () -> TimeZone.getDefault().getID())
                    .toBeEqualTo(TIMEZONE).errorOnFail().await();
        } finally {
            if (Flags.setAutoTimeZoneEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZonePolicy(
                        DevicePolicyManager.AUTO_TIME_ZONE_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(Settings.Global.AUTO_TIME_ZONE,
                        originalAutoTimeZoneEnabledValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                        dpc(sDeviceState).componentName(), originalAutoTimeZoneEnabledValue);
            }
        }
    }

    // TODO(234609037): Once these APIs are accessible via permissions, this should be moved to Nene
    private void setAutoTimeZoneEnabled(RemoteDevicePolicyManager dpm,
            ComponentName componentName, boolean enabled) {
        dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                componentName, enabled);

        Poll.forValue("autoTimeZoneEnabled",
                () -> dpm.getAutoTimeZoneEnabled(componentName))
                .toBeEqualTo(enabled)
                .errorOnFail()
                .await();
    }

    @Postsubmit(reason = "New test")
    @PolicyDoesNotApplyTest(policy = com.android.bedstead.harrier.policies.TimeZone.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTimeZone")
    public void setTimeZone_doesNotApply_timeZoneIsNotSet() {
        boolean originalAutoTimeZoneEnabledValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeZoneEnabled(dpc(sDeviceState).componentName());
        try {
            setAutoTimeZoneEnabled(dpc(sDeviceState).devicePolicyManager(),
                    dpc(sDeviceState).componentName(), false);

            boolean returnValue = dpc(sDeviceState).devicePolicyManager()
                    .setTimeZone(dpc(sDeviceState).componentName(), TIMEZONE);

            assertThat(returnValue).isTrue();
            assertThat(TimeZone.getDefault().getDisplayName()).isNotEqualTo(TIMEZONE);
        } finally {
            if (Flags.setAutoTimeZoneEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZonePolicy(
                        DevicePolicyManager.AUTO_TIME_ZONE_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(Settings.Global.AUTO_TIME_ZONE,
                        originalAutoTimeZoneEnabledValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                        dpc(sDeviceState).componentName(), originalAutoTimeZoneEnabledValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = com.android.bedstead.harrier.policies.TimeZone.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTimeZone")
    public void setTimeZone_autoTimeZoneIsEnabled_returnsFalse() {
        boolean originalAutoTimeZoneEnabledValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeZoneEnabled(dpc(sDeviceState).componentName());
        try {
            setAutoTimeZoneEnabled(dpc(sDeviceState).devicePolicyManager(),
                    dpc(sDeviceState).componentName(), true);

            boolean returnValue = dpc(sDeviceState).devicePolicyManager()
                    .setTimeZone(dpc(sDeviceState).componentName(), TIMEZONE);

            assertThat(returnValue).isFalse();
            assertThat(TimeZone.getDefault().getDisplayName()).isNotEqualTo(TIMEZONE);
        } finally {
            if (Flags.setAutoTimeZoneEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZonePolicy(
                        DevicePolicyManager.AUTO_TIME_ZONE_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(Settings.Global.AUTO_TIME_ZONE,
                        originalAutoTimeZoneEnabledValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                        dpc(sDeviceState).componentName(), originalAutoTimeZoneEnabledValue);
            }
        }
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = com.android.bedstead.harrier.policies.TimeZone.class,
            includeNonDeviceAdminStates = false)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTimeZone")
    public void setTimeZone_notAllowed_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager()
                        .setTimeZone(dpc(sDeviceState).componentName(), TIMEZONE));
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = Time.class)
    @ApiTest(apis = "android.app.manager.DevicePolicyManager#setTimeZone")
    public void setTimeZone_logsEvent() {
        boolean originalAutoTimeZoneEnabledValue = dpc(sDeviceState).devicePolicyManager()
                .getAutoTimeZoneEnabled(dpc(sDeviceState).componentName());
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                    dpc(sDeviceState).componentName(), false);

            dpc(sDeviceState).devicePolicyManager()
                    .setTimeZone(dpc(sDeviceState).componentName(), TIMEZONE);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_TIME_ZONE_VALUE)
                    .whereAdminPackageName().isEqualTo(dpc(sDeviceState).packageName())
            ).wasLogged();
        } finally {
            if (Flags.setAutoTimeZoneEnabledCoexistence()) {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZonePolicy(
                        DevicePolicyManager.AUTO_TIME_ZONE_NOT_CONTROLLED_BY_POLICY);
                TestApis.settings().global().putInt(Settings.Global.AUTO_TIME_ZONE,
                        originalAutoTimeZoneEnabledValue ? 1 : 0);
            } else {
                dpc(sDeviceState).devicePolicyManager().setAutoTimeZoneEnabled(
                        dpc(sDeviceState).componentName(), originalAutoTimeZoneEnabledValue);
            }
        }
    }

    @CannotSetPolicyTest(policy = DisallowConfigDateTime.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DATE_TIME")
    public void setUserRestriction_disallowConfigDateTime_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                        dpc(sDeviceState).componentName(), DISALLOW_CONFIG_DATE_TIME));
    }

    @PolicyAppliesTest(policy = DisallowConfigDateTime.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DATE_TIME")
    public void setUserRestriction_disallowConfigDateTime_isSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CONFIG_DATE_TIME);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_DATE_TIME))
                    .isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CONFIG_DATE_TIME);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigDateTime.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DATE_TIME")
    public void setUserRestriction_disallowConfigDateTime_isNotSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CONFIG_DATE_TIME);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_DATE_TIME))
                    .isFalse();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_CONFIG_DATE_TIME);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_DATE_TIME)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DATE_TIME")
    public void disallowConfigDateTimeIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_CONFIG_DATE_TIME)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_DATE_TIME")
    public void disallowConfigDateTimeIsSet_todo() throws Exception {
        // TODO: Test
    }
}