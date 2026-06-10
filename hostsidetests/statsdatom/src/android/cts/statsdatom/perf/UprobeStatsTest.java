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

package android.cts.statsdatom.perf;

import static android.uprobestats.mainline.flags.Flags.FLAG_ENABLE_UPROBESTATS;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.compatibility.common.util.CpuFeatures;
import com.android.internal.os.StatsdConfigProto;
import com.android.internal.os.StatsdConfigProto.Alert;
import com.android.internal.os.StatsdConfigProto.FieldMatcher;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.internal.os.StatsdConfigProto.Subscription;
import com.android.internal.os.StatsdConfigProto.UprobestatsDetails;
import com.android.internal.os.StatsdConfigProto.ValueMetric;
import com.android.os.AtomsProto.AppBreadcrumbReported;
import com.android.os.AtomsProto.Atom;
import com.android.os.framework.FrameworkExtensionAtoms;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import uprobestats.protos.Config.UprobestatsConfig;

/**
 * Statsd atom tests for ProcessState and ProcessAssociation.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class UprobeStatsTest extends BaseHostJUnit4Test implements IBuildReceiver {
    private static final String ACTION_SHOW_APPLICATION_OVERLAY = "action.show_application_overlay";
    private static final int APP_BREADCRUMB_REPORTED_MATCH_START_ID = 1;
    private static final int METRIC_ID = 8;
    private static final int ALERT_ID = 29754810;
    private static final int SUBSCRIPTION_ID = 29796753;
    private static final int PROBING_DURATION_SECS = 10;

    private IBuildInfo mCtsBuild;
    private ExtensionRegistry mRegistry;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Before
    public void setUp() throws Exception {
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        getDevice().enableAdbRoot();
        getDevice().executeShellCommand("killall uprobestats");

        mRegistry = ExtensionRegistry.newInstance();
        FrameworkExtensionAtoms.registerAllExtensions(mRegistry);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_UPROBESTATS)
    public void testUpdateDeviceIdleTempAllowlist() throws Exception {
        if (!CpuFeatures.isArm64(getDevice())) {
            return;
        }
        try (AutoCloseable c = DeviceUtils.withActivity(
                getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", ACTION_SHOW_APPLICATION_OVERLAY)) {
            UprobestatsConfig uprobeStatsConfig = UprobestatsConfig.newBuilder().addTasks(
                    UprobestatsConfig.Task.newBuilder().addProbeConfigs(
                        UprobestatsConfig.Task.ProbeConfig.newBuilder()
                            .setBpfName(
                                "prog_ProcessManagement_uprobe_update_device_idle_temp_allowlist")
                            .addFilePaths("/system/framework/oat/arm64/services.odex")
                            .setMethodSignature("void com.android.server.am."
                                + "ActivityManagerService$LocalService."
                                + "updateDeviceIdleTempAllowlist"
                                + "(int[], int, boolean, long, int, int, java.lang.String, int)")
                            .setFullyQualifiedClassName(
                                "com.android.server.am.ActivityManagerService$LocalService")
                            .setMethodName("updateDeviceIdleTempAllowlist")
                            .addFullyQualifiedParameters("int[]")
                            .addFullyQualifiedParameters("int")
                            .addFullyQualifiedParameters("boolean")
                            .addFullyQualifiedParameters("long")
                            .addFullyQualifiedParameters("int")
                            .addFullyQualifiedParameters("int")
                            .addFullyQualifiedParameters("java.lang.String")
                            .addFullyQualifiedParameters("int")
                            .build()
                        )
                    .addBpfMaps("map_ProcessManagement_update_device_idle_temp_allowlist_records")
                    .setTargetProcessName("system_server")
                    .setDurationSeconds(PROBING_DURATION_SECS)
                    .setStatsdLoggingConfig(
                            UprobestatsConfig.Task.StatsdLoggingConfig.newBuilder()
                                    .setAtomId(940)
                                    .build()
                    ).build()
                ).build();


            StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(
                    DeviceUtils.STATSD_ATOM_TEST_PKG);
            ConfigUtils.addEventMetric(config, 940);
            config.addSubscription(
                Subscription.newBuilder()
                    .setId(SUBSCRIPTION_ID)
                    .setRuleType(Subscription.RuleType.ALERT)
                    .setRuleId(ALERT_ID)
                    .setUprobestatsDetails(
                        UprobestatsDetails.newBuilder().setConfig(
                            uprobeStatsConfig.toByteString())))
                .addValueMetric(
                        ValueMetric.newBuilder()
                                .setId(METRIC_ID)
                                .setWhat(APP_BREADCRUMB_REPORTED_MATCH_START_ID)
                                .setBucket(StatsdConfigProto.TimeUnit.ONE_MINUTE)
                                // Get the label field's value:
                                .setValueField(
                                        FieldMatcher.newBuilder()
                                                .setField(Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER)
                                                .addChild(
                                                        FieldMatcher.newBuilder().setField(
                                                                    AppBreadcrumbReported
                                                                            .LABEL_FIELD_NUMBER))))
                .addAtomMatcher(
                        StatsdConfigProto.AtomMatcher.newBuilder()
                                .setId(APP_BREADCRUMB_REPORTED_MATCH_START_ID)
                                .setSimpleAtomMatcher(
                                        StatsdConfigProto.SimpleAtomMatcher.newBuilder()
                                                .setAtomId(
                                                        Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER)
                                                .addFieldValueMatcher(
                                                        ConfigUtils.createFvm(
                                                                        AppBreadcrumbReported
                                                                                .STATE_FIELD_NUMBER)
                                                                .setEqInt(
                                                                        AppBreadcrumbReported.State
                                                                                .START
                                                                                .getNumber()))))
                .addAlert(
                        Alert.newBuilder()
                                .setId(ALERT_ID)
                                .setMetricId(METRIC_ID)
                                .setNumBuckets(4)
                                .setRefractoryPeriodSecs(0)
                                .setTriggerIfSumGt(0))
                    .addNoReportMetric(METRIC_ID);

            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG * 2);
            ConfigUtils.uploadConfig(getDevice(), config);
            AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
            waitForUprobeStats(20, TimeUnit.SECONDS);
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
            getDevice().executeShellCommand("cmd deviceidle tempwhitelist com.google.android.tts");
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
            waitForCondition(() -> {
                try {
                    return ReportUtils.getEventMetricDataList(getDevice(), mRegistry).stream()
                    .map(a -> a.getAtom().getExtension(
                        FrameworkExtensionAtoms.deviceIdleTempAllowlistUpdated))
                    .filter(a -> a.getChangingUid() > 0)
                    .findFirst()
                    .orElse(null) != null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, 20, TimeUnit.SECONDS);

            // Ensure that the uprobestats process terminates.
            waitForCondition(() -> {
                try {
                    return getDevice().executeShellCommand("pidof uprobestats").length() == 0;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, PROBING_DURATION_SECS + AtomTestUtils.WAIT_TIME_LONG / 1000, TimeUnit.SECONDS);
        }
    }

    /**
     * Waits for the uprobestats process to start
     */
    public void waitForUprobeStats(long timeout, TimeUnit unit)
            throws TimeoutException, DeviceNotAvailableException {
        waitForCondition(() -> {
            try {
                return getDevice().executeShellCommand("pidof uprobestats").length() > 0;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, timeout, unit);
    }

    /**
     * Waits for a certain condition to become true.
     */
    public void waitForCondition(Supplier<Boolean> condition, long timeout, TimeUnit unit)
            throws TimeoutException, DeviceNotAvailableException {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = unit.toMillis(timeout);

        while (true) {
            // Calculate remaining time
            long elapsedTime = System.currentTimeMillis() - startTime;
            long remainingTime = timeoutMillis - elapsedTime;

            if (remainingTime <= 0) {
                throw new TimeoutException();
            }

            if (condition.get().booleanValue()) {
                break;
            }

            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        }
    }
}
