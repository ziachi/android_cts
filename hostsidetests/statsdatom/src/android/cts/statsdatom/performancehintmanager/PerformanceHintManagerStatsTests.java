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

package android.cts.statsdatom.performancehintmanager;

import static android.adpf.atom.common.ADPFAtomTestConstants.CONTENT_KEY_RESULT_TIDS;
import static android.adpf.atom.common.ADPFAtomTestConstants.CONTENT_KEY_UID;

import static com.android.server.power.hint.Flags.FLAG_ADPF_SESSION_TAG;
import static com.android.server.power.hint.Flags.FLAG_POWERHINT_THREAD_CLEANUP;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.os.adpf.ADPFSystemComponentInfo;
import com.android.os.adpf.AdpfExtensionAtoms;
import com.android.os.adpf.AdpfHintSessionTidCleanup;
import com.android.os.adpf.AdpfSessionSnapshot;
import com.android.os.adpf.AdpfSessionTag;
import com.android.os.adpf.FmqStatus;
import com.android.os.adpf.PerformanceHintSessionReported;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
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

import java.util.Arrays;
import java.util.List;

/**
 * Test for Performance Hint Manager stats.
 * This test is mainly to test ADPF data collection
 *
 *  <p>Build/Install/Run:
 *  atest CtsStatsdAtomHostTestCases:PerformanceHintManagerStatsTests
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class PerformanceHintManagerStatsTests extends BaseHostJUnit4Test implements IBuildReceiver {
    private static final String DEVICE_TEST_PKG = "com.android.server.cts.device.statsdatom";
    private static final String DEVICE_TEST_CLASS = ".PerformanceHintManagerTests";
    private static final String ADPF_ATOM_APP_PKG = "android.adpf.atom.app";
    private static final String ADPF_ATOM_APP2_PKG = "android.adpf.atom.app2";
    private static final String ADPF_ATOM_APP_APK = "CtsStatsdAdpfApp.apk";
    private static final String ADPF_ATOM_APP2_APK = "CtsStatsdAdpfApp2.apk";

    private static final int SESSION_TAG_APP = AdpfSessionTag.APP_VALUE;
    private static final int SESSION_TAG_GAME = AdpfSessionTag.GAME_VALUE;
    private static final int SESSION_TAG_HWUI = AdpfSessionTag.HWUI_VALUE;

    private IBuildInfo mCtsBuild;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Before
    public void setUp() throws Exception {
        checkSupportedHardware();
        checkVirtualDevice();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.installTestApp(getDevice(), ADPF_ATOM_APP_APK, ADPF_ATOM_APP_PKG, mCtsBuild);
        DeviceUtils.installTestApp(getDevice(), ADPF_ATOM_APP2_APK, ADPF_ATOM_APP2_PKG, mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), ADPF_ATOM_APP_PKG);
        DeviceUtils.uninstallTestApp(getDevice(), ADPF_ATOM_APP2_PKG);
    }

    private void checkSupportedHardware() throws DeviceNotAvailableException {
        String features = getDevice().executeShellCommand("pm list features");
        assumeTrue(
                "Unsupported hardware features: " + features,
                !features.contains("android.hardware.type.television")
                        && !features.contains("android.hardware.type.watch")
                        && !features.contains("android.hardware.type.automotive"));
    }

    private String getProperty(String prop) throws Exception {
        return getDevice().executeShellCommand("getprop " + prop).replace("\n", "");
    }

    private void checkVirtualDevice() throws Exception {
        String device = getProperty("ro.product.device");
        String model = getProperty("ro.product.model");
        String name = getProperty("ro.product.name");
        String qemu = getProperty("ro.boot.qemu");
        String hardware = getProperty("ro.hardware");
        boolean isVirtual =
                device.startsWith("vsoc_")
                        || model.startsWith("Cuttlefish ")
                        || name.startsWith("cf_")
                        || name.startsWith("aosp_cf_")
                        || qemu.equals("1")
                        || hardware.contains("goldfish")
                        || hardware.contains("ranchu")
                        || hardware.contains("cutf_cvm")
                        || hardware.contains("starfish");
        assumeFalse("Test is skipped on virtual device ", isVirtual);
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Test
    @RequiresFlagsDisabled(FLAG_ADPF_SESSION_TAG)
    public void testCreateHintSessionStatsdApp() throws Exception {
        final int androidSApiLevel = 31; // android.os.Build.VERSION_CODES.S
        final int firstApiLevel = Integer.parseInt(
                DeviceUtils.getProperty(getDevice(), "ro.product.first_api_level"));
        final long testTargetDuration = 12345678L;
        final String testMethod = "testCreateHintSession";
        final TestDescription desc = TestDescription.fromString(
                DEVICE_TEST_PKG + DEVICE_TEST_CLASS + "#" + testMethod);
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PERFORMANCE_HINT_SESSION_REPORTED_FIELD_NUMBER);
        TestRunResult testRunResult = DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(),
                DEVICE_TEST_CLASS, testMethod);

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        TestResult result = testRunResult.getTestResults().get(desc);
        assertNotNull(result);
        TestStatus status = result.getStatus();
        assumeFalse(status == TestStatus.ASSUMPTION_FAILURE);
        assertThat(status).isEqualTo(TestStatus.PASSED);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        if (firstApiLevel < androidSApiLevel && data.size() == 0) {
            // test requirement does not meet, the device does not support
            // ADPF hint session, skipping the test
            return;
        }

        assertThat(data.size()).isAtLeast(1);
        boolean found = false;
        for (StatsLog.EventMetricData event : data) {
            PerformanceHintSessionReported a0 =
                    event.getAtom().getPerformanceHintSessionReported();
            if (a0.getTargetDurationNs() == testTargetDuration) {
                found = true;
                assertThat(a0.getPackageUid()).isGreaterThan(10000);  // Not a system service UID.
                assertThat(a0.getTidCount()).isEqualTo(1);
                assertThat(a0.getSessionTag().getNumber()).isEqualTo(SESSION_TAG_APP);
            }
        }
        if (!found) {
            fail("Failed to find an event data belonging to the test process in data: " + data);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ADPF_SESSION_TAG)
    public void testCreateHintSessionStatsdGame() throws Exception {
        final int androidSApiLevel = 31; // android.os.Build.VERSION_CODES.S
        final int firstApiLevel = Integer.parseInt(
                DeviceUtils.getProperty(getDevice(), "ro.product.first_api_level"));
        final long testTargetDuration = 12345678L;
        final int sessionTagGame = AdpfSessionTag.GAME_VALUE;
        final String testMethod = "testCreateHintSession";
        final TestDescription desc = TestDescription.fromString(
                DEVICE_TEST_PKG + DEVICE_TEST_CLASS + "#" + testMethod);
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PERFORMANCE_HINT_SESSION_REPORTED_FIELD_NUMBER);
        TestRunResult testRunResult = DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(),
                DEVICE_TEST_CLASS, testMethod);

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        TestResult result = testRunResult.getTestResults().get(desc);
        assertNotNull(result);
        TestStatus status = result.getStatus();
        assumeFalse(status == TestStatus.ASSUMPTION_FAILURE);
        assertThat(status).isEqualTo(TestStatus.PASSED);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        if (firstApiLevel < androidSApiLevel && data.size() == 0) {
            // test requirement does not meet, the device does not support
            // ADPF hint session, skipping the test
            return;
        }

        assertThat(data.size()).isAtLeast(1);
        boolean found = false;
        for (StatsLog.EventMetricData event : data) {
            PerformanceHintSessionReported a0 =
                    event.getAtom().getPerformanceHintSessionReported();
            if (a0.getTargetDurationNs() == testTargetDuration) {
                found = true;
                assertThat(a0.getPackageUid()).isGreaterThan(10000);  // Not a system service UID.
                assertThat(a0.getTidCount()).isEqualTo(1);
                assertThat(a0.getSessionTag().getNumber()).isEqualTo(sessionTagGame);
            }
        }
        if (!found) {
            fail("Failed to find an event data belonging to the test process in data: " + data);
        }
    }

    @Test
    public void testAdpfSystemComponentStatsd() throws Exception {
        final boolean isSurfaceFlingerCpuHintEnabled = Boolean.parseBoolean(
                DeviceUtils.getProperty(getDevice(), "debug.sf.enable_adpf_cpu_hint"));
        final boolean isHwuiHintEnabled = Boolean.parseBoolean(
                DeviceUtils.getProperty(getDevice(), "debug.hwui.use_hint_manager"));
        final int fmqOtherStatus = FmqStatus.OTHER_STATUS_VALUE;
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.ADPF_SYSTEM_COMPONENT_INFO_FIELD_NUMBER);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());
        assertThat(data.size()).isAtLeast(1);
        ADPFSystemComponentInfo a0 = data.get(0).getAdpfSystemComponentInfo();
        assertThat(a0.getSurfaceflingerCpuHintEnabled()).isEqualTo(isSurfaceFlingerCpuHintEnabled);
        assertThat(a0.getHwuiHintEnabled()).isEqualTo(isHwuiHintEnabled);
        assertThat(a0.getFmqSupported().getNumber()).isNotEqualTo(fmqOtherStatus);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_POWERHINT_THREAD_CLEANUP)
    public void testAdpfHintSessionTidCleanupIsPushed() throws Exception {
        final int apiLevel = Integer.parseInt(
                DeviceUtils.getProperty(getDevice(), ("ro.vendor.api_level")));
        final int minLevel = 34;
        assumeTrue("Test is only enforced on vendor API level >= " + minLevel
                        + " while test device at = " + apiLevel, apiLevel >= minLevel);

        final String testMethod = "testAdpfTidCleanup";
        final TestDescription desc = TestDescription.fromString(
                DEVICE_TEST_PKG + DEVICE_TEST_CLASS + "#" + testMethod);
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AdpfExtensionAtoms.ADPF_HINT_SESSION_TID_CLEANUP_FIELD_NUMBER);
        TestRunResult testRunResult = DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(),
                DEVICE_TEST_CLASS, testMethod);
        RunUtil.getDefault().sleep(5 * AtomTestUtils.WAIT_TIME_LONG);
        TestResult result = testRunResult.getTestResults().get(desc);
        assertNotNull(result);
        TestStatus status = result.getStatus();
        assumeFalse(status == TestStatus.ASSUMPTION_FAILURE);
        assertThat(status).isEqualTo(TestStatus.PASSED);
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        AdpfExtensionAtoms.registerAllExtensions(registry);
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        assertFalse(data.isEmpty());
        String tidsStr = result.getMetrics().get(CONTENT_KEY_RESULT_TIDS);
        int uid = Integer.parseInt(result.getMetrics().get(CONTENT_KEY_UID));
        List<Integer> tids = Arrays.stream(tidsStr.split(",")).map(Integer::parseInt).toList();
        boolean found = false;
        for (StatsLog.EventMetricData event : data) {
            if (event.getAtom().hasExtension(AdpfExtensionAtoms.adpfHintSessionTidCleanup)) {
                AdpfHintSessionTidCleanup a0 = event.getAtom().getExtension(
                        AdpfExtensionAtoms.adpfHintSessionTidCleanup);
                assertNotNull(tidsStr);
                if (a0.getUid() == uid) {
                    assertThat(a0.getMaxInvalidTidCount()).isAtLeast(tids.size());
                    assertThat(a0.getTotalTidCount()).isAtLeast(tids.size());
                    assertThat(a0.getTotalInvalidTidCount()).isAtLeast(tids.size());
                    assertThat(a0.getMaxDurationUs()).isGreaterThan(0);
                    assertThat(a0.getTotalDurationUs()).isGreaterThan(0);
                    assertThat(a0.getSessionCount()).isAtLeast(1);
                    found = true;
                }
            }
        }
        if (!found) {
            fail("Failed to find an event data belonging to the test process in data: " + data);
        }
    }

    private class ExpectedSnapshotResults {
        public long testTargetDuration;
        public boolean shouldFindAppSession;
        public boolean shouldFindGameSession;
        public boolean shouldFindHwuiSession;
        public int minTidCount;
        public int minConcurrentSession;
        public int minConcurrentAppSession;
        public int minPowerEfficientSession;
        ExpectedSnapshotResults(long testTargetDuration, boolean shouldFindAppSession,
                boolean shouldFindGameSession, boolean shouldFindHwuiSession,
                int minTidCount, int minConcurrentSession,
                int minConcurrentAppSession, int minPowerEfficientSession) {
            this.testTargetDuration = testTargetDuration;
            this.shouldFindAppSession = shouldFindAppSession;
            this.shouldFindGameSession = shouldFindGameSession;
            this.shouldFindHwuiSession = shouldFindHwuiSession;
            this.minTidCount = minTidCount;
            this.minConcurrentSession = minConcurrentSession;
            this.minConcurrentAppSession = minConcurrentAppSession;
            this.minPowerEfficientSession = minPowerEfficientSession;
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ADPF_SESSION_TAG)
    public void testAdpfSessionSnapshotTwoAppsOnThenRestore() throws Exception {
        final String testMethod = "testAdpfSessionSnapshotTwoAppsOn";
        final TestDescription desc = TestDescription.fromString(
                DEVICE_TEST_PKG + DEVICE_TEST_CLASS + "#" + testMethod);
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AdpfExtensionAtoms.ADPF_SESSION_SNAPSHOT_FIELD_NUMBER);
        TestRunResult testRunResult = DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(),
                DEVICE_TEST_CLASS, testMethod);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        // Trigger atom pull
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);


        TestResult result = testRunResult.getTestResults().get(desc);
        assertNotNull(result);
        TestStatus status = result.getStatus();
        assumeFalse(status == TestStatus.ASSUMPTION_FAILURE);
        assertThat(status).isEqualTo(TestStatus.PASSED);

        ExpectedSnapshotResults expectedSnapshotResults =
                new ExpectedSnapshotResults(12345678L,
                        /* shouldFindAppSession */ true,
                        /* shouldFindGameSession */true,
                        /* shouldFindHwuiSession */ true,
                        /* minTidCount */ 1,
                        /* minConcurrentSession */ 1,
                        /* minConcurrentAppSession */ 3,
                        /* minPowerEfficientSession */ 0);

        checkPulledSessionSnapshots(expectedSnapshotResults);

        // After the first pull here we test if the snapshots are restored correctly
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        // Trigger atom pull one more
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        checkPulledSessionSnapshots(expectedSnapshotResults);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ADPF_SESSION_TAG)
    public void testAdpfSessionSnapshotTwoAppsOnKillOneThenRestore() throws Exception {
        final String testMethod = "testAdpfSessionSnapshotTwoAppsOnKillOne";
        final TestDescription desc = TestDescription.fromString(
                DEVICE_TEST_PKG + DEVICE_TEST_CLASS + "#" + testMethod);
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AdpfExtensionAtoms.ADPF_SESSION_SNAPSHOT_FIELD_NUMBER);
        TestRunResult testRunResult = DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(),
                DEVICE_TEST_CLASS, testMethod);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        // Trigger atom pull
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);


        TestResult result = testRunResult.getTestResults().get(desc);
        assertNotNull(result);
        TestStatus status = result.getStatus();
        assumeFalse(status == TestStatus.ASSUMPTION_FAILURE);
        assertThat(status).isEqualTo(TestStatus.PASSED);

        ExpectedSnapshotResults expectedSnapshotResults =
                new ExpectedSnapshotResults(12345678L,
                        /* shouldFindAppSession */ true,
                        /* shouldFindGameSession */true,
                        /* shouldFindHwuiSession */ true,
                        /* minTidCount */ 1,
                        /* minConcurrentSession */ 1,
                        /* minConcurrentAppSession */ 3,
                        /* minPowerEfficientSession */ 0);

        // Here even the app has been killed, snapshot should still record that the number of
        // maximum concurrent session is greater than 0, precisely 1.
        checkPulledSessionSnapshots(expectedSnapshotResults);

        // Here we validate that the killed app is not restored back to the snapshot
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        // Trigger atom pull one more
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // update expected snapshot results that we don't expect to see an APP session.
        expectedSnapshotResults.shouldFindAppSession = false;

        checkPulledSessionSnapshots(expectedSnapshotResults);
    }

    private void checkPulledSessionSnapshots(ExpectedSnapshotResults expectedSnapshotResults)
            throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        AdpfExtensionAtoms.registerAllExtensions(registry);

        List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice(),
                registry, false);
        assertFalse(data.isEmpty());
        boolean foundApp = false;
        boolean foundGame = false;
        boolean foundHwui = false;

        for (AtomsProto.Atom atom : data) {
            if (atom.hasExtension(AdpfExtensionAtoms.adpfSessionSnapshot)) {
                AdpfSessionSnapshot a0 = atom.getExtension(
                        AdpfExtensionAtoms.adpfSessionSnapshot);
                int sessionTag = a0.getSessionTag().getNumber();
                if (a0.getTargetDurationNsList()
                        .contains(expectedSnapshotResults.testTargetDuration)) {
                    if (sessionTag == SESSION_TAG_APP) {
                        foundApp = true;
                        assertThat(a0.getMaxConcurrentSession())
                                .isAtLeast(expectedSnapshotResults.minConcurrentAppSession);
                    } else if (sessionTag == SESSION_TAG_GAME) {
                        foundGame = true;
                    }
                    assertThat(a0.getUid()).isGreaterThan(10000); // Not a system service UID.
                    checkSnapshotCommonData(a0, expectedSnapshotResults);
                } else if (sessionTag == SESSION_TAG_HWUI) {
                    foundHwui = true;
                    assertNotNull(a0.getTargetDurationNsList());
                    checkSnapshotCommonData(a0, expectedSnapshotResults);
                }
            }
        }

        final boolean isHwuiHintEnabled = Boolean.parseBoolean(
                DeviceUtils.getProperty(getDevice(), "debug.hwui.use_hint_manager"));
        if (foundApp != expectedSnapshotResults.shouldFindAppSession) {
            fail("Failed to find an APP session snapshot in: " + data);
        }
        if (foundGame != expectedSnapshotResults.shouldFindGameSession) {
            fail("Failed to find a Game session snapshot in: " + data);
        }
        if ((foundHwui != expectedSnapshotResults.shouldFindHwuiSession) && isHwuiHintEnabled) {
            fail("Failed to find a HWUI session snapshot in: " + data);
        }
    }

    private void checkSnapshotCommonData(AdpfSessionSnapshot snapshot,
            ExpectedSnapshotResults expectedSnapshotResults) {
        assertThat(snapshot.getMaxConcurrentSession())
                .isAtLeast(expectedSnapshotResults.minConcurrentSession);
        assertThat(snapshot.getMaxTidCount()).isAtLeast(expectedSnapshotResults.minTidCount);
        assertThat(snapshot.getNumPowerEfficientSession())
                .isAtLeast(expectedSnapshotResults.minPowerEfficientSession);
    }
}
