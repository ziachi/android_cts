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

package android.cts.statsdatom.batterystats;

import static com.android.os.framework.FrameworkExtensionAtoms.BATTERY_USAGE_STATS_PER_UID_FIELD_NUMBER;
import static com.android.os.framework.FrameworkExtensionAtoms.batteryUsageStatsPerUid;
import static com.android.server.power.optimization.Flags.FLAG_ADD_BATTERY_USAGE_STATS_SLICE_ATOM;
import static com.android.server.power.optimization.Flags.FLAG_DISABLE_COMPOSITE_BATTERY_USAGE_STATS_ATOMS;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.os.PowerComponentEnum;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.internal.os.StatsdConfigProto;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.BatteryUsageStatsAtomsProto;
import com.android.os.AtomsProto.BatteryUsageStatsAtomsProto.BatteryConsumerData;
import com.android.os.framework.FrameworkExtensionAtoms;
import com.android.os.framework.FrameworkExtensionAtoms.BatteryUsageStatsPerUid;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
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

import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class BatteryUsageStatsTests extends BaseHostJUnit4Test implements IBuildReceiver {

    private static final String DEVICE_CONFIG_NAMESPACE = "backstage_power";
    private static final String MIN_CONSUMED_POWER_THRESHOLD_KEY = "min_consumed_power_threshold";

    private IBuildInfo mCtsBuild;

    private String mMinConsumedPowerThreshold = "0.0";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Before
    public void setUp() throws Exception {
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);

        try {
            mMinConsumedPowerThreshold =
                    DeviceUtils.getDeviceConfigFeature(
                            getDevice(), DEVICE_CONFIG_NAMESPACE, MIN_CONSUMED_POWER_THRESHOLD_KEY);
            DeviceUtils.putDeviceConfigFeature(
                    getDevice(), DEVICE_CONFIG_NAMESPACE, MIN_CONSUMED_POWER_THRESHOLD_KEY, "0.0");
        } catch (Exception e) {
            CLog.d("MIN_CONSUMED_POWER_THRESHOLD_KEY is not setup. " + e);
        }
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        try {
            DeviceUtils.putDeviceConfigFeature(
                    getDevice(),
                    DEVICE_CONFIG_NAMESPACE,
                    MIN_CONSUMED_POWER_THRESHOLD_KEY,
                    mMinConsumedPowerThreshold);
        } catch (Exception e) {
            CLog.d("MIN_CONSUMED_POWER_THRESHOLD_KEY reset error. " + e);
        }
    }

    @Test
    @RequiresFlagsDisabled(FLAG_DISABLE_COMPOSITE_BATTERY_USAGE_STATS_ATOMS)
    public void testBatteryUsageStatsSinceReset() throws Exception {
        if (!hasBattery()) {
            return;
        }

        runBatteryUsageStatsAtomTest(
                AtomsProto.Atom.BATTERY_USAGE_STATS_SINCE_RESET_FIELD_NUMBER,
                atoms -> {
                    for (final AtomsProto.Atom atom : atoms) {
                        final BatteryUsageStatsAtomsProto batteryUsageStats =
                                atom.getBatteryUsageStatsSinceReset().getBatteryUsageStats();
                        assertBatteryUsageStatsAtom(batteryUsageStats);
                    }
                });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ADD_BATTERY_USAGE_STATS_SLICE_ATOM)
    public void testBatteryUsageStatsPerUid() throws Exception {
        if (!hasBattery()) {
            return;
        }

        runBatteryUsageStatsAtomTest(
                BATTERY_USAGE_STATS_PER_UID_FIELD_NUMBER,
                atoms -> {
                    assertBatteryUsageStatsPerUidAtoms(atoms);
                });
    }

    @FunctionalInterface
    private interface CheckedConsumer<T> {
        void accept(T t) throws Exception;
    }

    private void runBatteryUsageStatsAtomTest(
            int atomFieldNumber, CheckedConsumer<List<AtomsProto.Atom>> atomsValidationFunc)
            throws Exception {
        unplugDevice();
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        try {
            DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                    "StatsdCtsForegroundActivity", "action", "action.drain_power", 5_000);
        } finally {
            plugInDevice();
        }

        StatsdConfigProto.StatsdConfig.Builder config =
                ConfigUtils.createConfigBuilder(DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.addGaugeMetric(config, atomFieldNumber);
        ConfigUtils.uploadConfig(getDevice(), config);

        // Trigger atom pull.
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        FrameworkExtensionAtoms.registerAllExtensions(registry);

        final List<AtomsProto.Atom> atoms =
                ReportUtils.getGaugeMetricAtoms(getDevice(), registry, false);
        assertThat(atoms.size()).isAtLeast(1);
        atomsValidationFunc.accept(atoms);
    }

    private void assertBatteryUsageStatsAtom(BatteryUsageStatsAtomsProto batteryUsageStats)
            throws Exception {
        assertThat(batteryUsageStats.getSessionEndMillis()).isGreaterThan(
                getDeviceTimeMs() - 5 * 60 * 1000);
        assertThat(batteryUsageStats.getSessionDurationMillis()).isGreaterThan(0);

        final BatteryConsumerData deviceBatteryConsumer =
                batteryUsageStats.getDeviceBatteryConsumer();
        assertThat(deviceBatteryConsumer.getTotalConsumedPowerDeciCoulombs()).isAtLeast(0);
        for (BatteryConsumerData.PowerComponentUsage powerComponent :
                deviceBatteryConsumer.getPowerComponentsList()) {
            assertThat(powerComponent.getDurationMillis()).isAtLeast(0);
            assertThat(powerComponent.getPowerDeciCoulombs()).isAtLeast(0);
        }

        boolean hasAppData = false;
        final List<BatteryUsageStatsAtomsProto.UidBatteryConsumer> uidBatteryConsumers =
                batteryUsageStats.getUidBatteryConsumersList();
        for (BatteryUsageStatsAtomsProto.UidBatteryConsumer consumer : uidBatteryConsumers) {
            if (consumer.getBatteryConsumerData().getTotalConsumedPowerDeciCoulombs() > 0
                    || consumer.getTimeInForegroundMillis() > 0
                    || consumer.getTimeInBackgroundMillis() > 0) {
                hasAppData = true;
                break;
            }
        }

        assertThat(hasAppData).isTrue();

        final int testUid = DeviceUtils.getStatsdTestAppUid(getDevice());
        BatteryUsageStatsAtomsProto.UidBatteryConsumer testConsumer = null;
        for (BatteryUsageStatsAtomsProto.UidBatteryConsumer consumer : uidBatteryConsumers) {
            if (consumer.getUid() == testUid) {
                testConsumer = consumer;
                break;
            }
        }

        // If the test app consumed a measurable amount of power, the break-down
        // by process state should also be present in the atom.
        if (testConsumer != null) {
            long consumedByCpu = 0;
            for (BatteryConsumerData.PowerComponentUsage pcu :
                    testConsumer.getBatteryConsumerData().getPowerComponentsList()) {
                if (pcu.getComponent() == PowerComponentEnum.POWER_COMPONENT_CPU.getNumber()) {
                    consumedByCpu = pcu.getPowerDeciCoulombs();
                    break;
                }
            }
            if (consumedByCpu > 10) {
                boolean hasProcStateData = false;
                for (int i = 0; i < testConsumer.getBatteryConsumerData().getSlicesCount(); i++) {
                    final BatteryConsumerData.PowerComponentUsageSlice slice =
                            testConsumer.getBatteryConsumerData().getSlices(i);
                    if (slice.getProcessState()
                            != BatteryConsumerData.PowerComponentUsageSlice.ProcessState.UNSPECIFIED
                            && (slice.getPowerComponent().getPowerDeciCoulombs() > 0
                                || slice.getPowerComponent().getDurationMillis() > 0)) {
                        hasProcStateData = true;
                        break;
                    }
                }

                assertThat(hasProcStateData).isTrue();
            }
        }
    }

    private void assertBatteryUsageStatsPerUidAtoms(List<AtomsProto.Atom> atoms) throws Exception {

        boolean hasAppData = false;

        final int testUid = DeviceUtils.getStatsdTestAppUid(getDevice());

        CLog.d("assertBatteryUsageStatsPerUid testUid = " + testUid);

        float testUidConsumedByCpu = 0;

        for (final AtomsProto.Atom atom : atoms) {
            final BatteryUsageStatsPerUid batteryUsageStats =
                    atom.getExtension(batteryUsageStatsPerUid);

            assertThat(batteryUsageStats.getSessionEndMillis())
                    .isGreaterThan(getDeviceTimeMs() - 5 * 60 * 1000);
            assertThat(batteryUsageStats.getSessionDurationMillis()).isGreaterThan(0);

            assertThat(batteryUsageStats.getTotalConsumedPowerMah()).isAtLeast(0);
            assertThat(batteryUsageStats.getDurationMillis() >= 0
                    || batteryUsageStats.getConsumedPowerMah() != 0).isTrue();

            if (batteryUsageStats.getUid() != -1) {
                if (batteryUsageStats.getTimeInStateMillis() > 0) {
                    hasAppData = true;
                }

                if (batteryUsageStats.getUid() == testUid) {
                    if (batteryUsageStats.getPowerComponentName().toLowerCase().equals("cpu")) {
                        testUidConsumedByCpu = batteryUsageStats.getConsumedPowerMah();
                    }
                }
            }
        }

        if (testUidConsumedByCpu > 0.27f) {
            // If the test app consumed a measurable amount of power, the break-down
            // by process state should also be present in the atom.
            boolean hasProcStateData = false;
            for (final AtomsProto.Atom atom : atoms) {
                final BatteryUsageStatsPerUid batteryUsageStats =
                        atom.getExtension(batteryUsageStatsPerUid);

                if (batteryUsageStats.getUid() != testUid) {
                    continue;
                }
                if (batteryUsageStats.getProcState()
                                != BatteryUsageStatsPerUid.ProcessState.UNSPECIFIED
                        && (batteryUsageStats.getConsumedPowerMah() > 0
                                || batteryUsageStats.getDurationMillis() > 0)) {
                    hasProcStateData = true;
                }
            }
            assertThat(hasProcStateData).isTrue();
        }

        assertThat(hasAppData).isTrue();
    }

    private boolean hasBattery() throws DeviceNotAvailableException  {
        final String batteryinfo = getDevice().executeShellCommand("dumpsys battery");
        return batteryinfo.contains("present: true");
    }

    private void unplugDevice() throws Exception {
        DeviceUtils.unplugDevice(getDevice());
        getDevice().executeShellCommand("dumpsys battery suspend_input");
        getDevice().executeShellCommand("dumpsys batterystats --reset");
    }

    private void plugInDevice() throws Exception {
        DeviceUtils.resetBatteryStatus(getDevice());
    }

    private long getDeviceTimeMs() throws Exception {
        final String timeMs = getDevice().executeShellCommand("date +%s%3N");
        return Long.parseLong(timeMs.trim());
    }
}
