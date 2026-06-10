/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.cts.statsdatom.powermanager;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.os.WakeLockLevelEnum;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.WakelockStateChanged;
import com.android.os.AttributionNode;
import com.android.os.adpf.AdpfExtensionAtoms;
import com.android.server.power.feature.flags.Flags;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.result.TestDescription;
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
import java.util.Collections;
import java.util.List;

/**
 * Test for Power Manager stats.
 *
 * <p>Build/Install/Run:
 * atest CtsStatsdAtomHostTestCases:PowerManagerStatsTests
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class PowerManagerStatsTests extends BaseHostJUnit4Test implements IBuildReceiver {
    private static final String DEVICE_TEST_PKG = "com.android.server.cts.device.statsdatom";
    private static final String DEVICE_TEST_CLASS = ".PowerManagerTests";
    private static final String ADPF_ATOM_APP_PKG = "com.android.server.cts.device.statsdatom";
    private static final String ADPF_ATOM_APP_APK = "CtsStatsdAdpfApp.apk";

    private IBuildInfo mCtsBuild;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Before
    public void setUp() throws Exception {
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.installTestApp(getDevice(), ADPF_ATOM_APP_APK, ADPF_ATOM_APP_PKG, mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), ADPF_ATOM_APP_APK);
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    private static <T> List<T> listOf(T... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    private WakelockStateChanged buildForUid(WakelockStateChanged.Builder builder, int uid) {
        return builder.clone().addAttributionNode(AttributionNode.newBuilder().setUid(uid)).build();
    }

    private WakelockStateChanged buildForUid(WakelockStateChanged.Builder builder, int[] uids) {

        WakelockStateChanged.Builder clonedBuilder = builder.clone();
        for (int uid : uids) {
            clonedBuilder.addAttributionNode(AttributionNode.newBuilder().setUid(uid));
        }
        return clonedBuilder.build();
    }

    @Test
    public void testAcquireModifyAndReleasedWakelockIsPushed() throws Exception {
        int atomId = AtomsProto.Atom.WAKELOCK_STATE_CHANGED_FIELD_NUMBER;

        String testMethod = "testAcquireModifyAndReleasedWakelock";
        TestDescription desc = TestDescription.fromString(
                DEVICE_TEST_PKG + DEVICE_TEST_CLASS + "#" + testMethod);
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomId);
        TestRunResult testRunResult = DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(),
                DEVICE_TEST_CLASS, testMethod);

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        TestStatus status = testRunResult.getTestResults().get(desc).getStatus();
        assertThat(status).isEqualTo(TestStatus.PASSED);
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        AdpfExtensionAtoms.registerAllExtensions(registry);
        List<WakelockStateChanged> wList =
                ReportUtils.getEventMetricDataList(getDevice(), registry)
                        .stream()
                        .map(eventMetricData -> eventMetricData.getAtom().getWakelockStateChanged())
                        .filter(wakelockStateChanged
                                -> wakelockStateChanged.getTag().equals("TestWakelockForCts"))
                        .toList();
        assertThat(wList.size()).isEqualTo(8);

        // The UID of the process acquiring the wakelock varies so read it here.
        int testProcessUid = wList.get(0).getAttributionNode(0).getUid();

        WakelockStateChanged.Builder baseBuilder = WakelockStateChanged.newBuilder()
                                                   .setTag("TestWakelockForCts")
                                                   .setType(WakeLockLevelEnum.PARTIAL_WAKE_LOCK);
        WakelockStateChanged.Builder acquireBuilder = baseBuilder.clone()
                                                      .setState(WakelockStateChanged.State.ACQUIRE);
        WakelockStateChanged.Builder releaseBuilder = baseBuilder.clone()
                                                      .setState(WakelockStateChanged.State.RELEASE);

        assertThat(wList.get(0))
                .comparingExpectedFieldsOnly()
                .isEqualTo(buildForUid(acquireBuilder, testProcessUid));

        assertThat(wList.subList(1, 4))
                .comparingExpectedFieldsOnly()
                .containsExactlyElementsIn(listOf(
                        buildForUid(releaseBuilder, testProcessUid),
                        buildForUid(acquireBuilder, 1010),
                        buildForUid(acquireBuilder, 2010)));

        assertThat(wList.subList(4, 7))
                .comparingExpectedFieldsOnly()
                .containsExactlyElementsIn(listOf(
                        buildForUid(releaseBuilder, 1010),
                        buildForUid(releaseBuilder, 2010),
                        buildForUid(acquireBuilder, 3010)));

        assertThat(wList.get(7))
                .comparingExpectedFieldsOnly()
                .isEqualTo(buildForUid(releaseBuilder, 3010));
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_WAKELOCK_ATTRIBUTION_VIA_WORKCHAIN})
    public void test_updateWakelockUids_releasesOldAndAcquiresNewUids() throws Exception {
        int atomId = AtomsProto.Atom.WAKELOCK_STATE_CHANGED_FIELD_NUMBER;

        String testMethod = "testUpdateWakelockUids";
        TestDescription desc =
                TestDescription.fromString(DEVICE_TEST_PKG + DEVICE_TEST_CLASS + "#" + testMethod);
        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG, atomId);
        TestRunResult testRunResult =
                DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), DEVICE_TEST_CLASS, testMethod);

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        TestStatus status = testRunResult.getTestResults().get(desc).getStatus();
        assertThat(status).isEqualTo(TestStatus.PASSED);
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        AdpfExtensionAtoms.registerAllExtensions(registry);
        List<WakelockStateChanged> wakelockStateChangedList =
                ReportUtils.getEventMetricDataList(getDevice(), registry).stream()
                        .map(eventMetricData -> eventMetricData.getAtom().getWakelockStateChanged())
                        .filter(
                                wakelockStateChanged ->
                                        wakelockStateChanged
                                                .getTag()
                                                .equals("cts:TEST_UPDATE_WAKELOCK_UIDS"))
                        .toList();
        assertThat(wakelockStateChangedList.size()).isEqualTo(8);

        // The UID of the process acquiring the wakelock varies so read it here.
        int testProcessUid = wakelockStateChangedList.get(0).getAttributionNode(0).getUid();

        WakelockStateChanged.Builder baseBuilder =
                WakelockStateChanged.newBuilder()
                        .setTag("cts:TEST_UPDATE_WAKELOCK_UIDS")
                        .setType(WakeLockLevelEnum.PARTIAL_WAKE_LOCK);
        WakelockStateChanged.Builder acquireBuilder =
                baseBuilder.clone().setState(WakelockStateChanged.State.ACQUIRE);
        WakelockStateChanged.Builder releaseBuilder =
                baseBuilder.clone().setState(WakelockStateChanged.State.RELEASE);

        assertThat(wakelockStateChangedList.get(0))
                .comparingExpectedFieldsOnly()
                .isEqualTo(buildForUid(acquireBuilder, testProcessUid));

        assertThat(wakelockStateChangedList.subList(1, 3))
                .comparingExpectedFieldsOnly()
                .containsExactlyElementsIn(
                        listOf(
                                buildForUid(releaseBuilder, testProcessUid),
                                buildForUid(acquireBuilder, new int[] {1010, testProcessUid})));

        assertThat(wakelockStateChangedList.subList(3, 6))
                .comparingExpectedFieldsOnly()
                .containsExactlyElementsIn(
                        listOf(
                                buildForUid(releaseBuilder, new int[] {1010, testProcessUid}),
                                buildForUid(acquireBuilder, new int[] {1020, testProcessUid}),
                                buildForUid(acquireBuilder, new int[] {1030, testProcessUid})));

        assertThat(wakelockStateChangedList.subList(6, 8))
                .comparingExpectedFieldsOnly()
                .containsExactlyElementsIn(
                        listOf(
                                buildForUid(releaseBuilder, new int[] {1020, testProcessUid}),
                                buildForUid(releaseBuilder, new int[] {1030, testProcessUid})));
    }
}
