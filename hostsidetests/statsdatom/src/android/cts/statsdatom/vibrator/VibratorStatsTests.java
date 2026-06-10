/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.cts.statsdatom.vibrator;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import static java.util.stream.Collectors.toList;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.os.vibrator.Flags;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.VibrationReported;
import com.android.os.AtomsProto.VibratorStateChanged;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

/** Statsd atoms tests done via app for vibrator atoms. */
@NonApiTest(
        exemptionReasons = {},
        justification = "METRIC")
@RunWith(DeviceJUnit4ClassRunner.class)
public class VibratorStatsTests extends BaseHostJUnit4Test implements IBuildReceiver {
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
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
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
    public void testVibratorStateChanged() throws Exception {
        assumeTrue(isVibratorSupportedOnDevice());

        List<Integer> expectedStates = Arrays.asList(
                VibratorStateChanged.State.ON_VALUE, VibratorStateChanged.State.OFF_VALUE);
        int expectedTimeBetweenStates = 300; // Assertion on 300/2 <= actual_wait <= 5*300

        List<EventMetricData> data = runVibratorDeviceTests(
                Atom.VIBRATOR_STATE_CHANGED_FIELD_NUMBER, "testOneShotVibration");

        assertValuesOccurredInOrder(
                expectedStates, expectedTimeBetweenStates, data,
                atom -> atom.getVibratorStateChanged().getState().getNumber());
    }

    @Test
    public void testVibrationReportedSingleVibration() throws Exception {
        assumeTrue(isVibratorSupportedOnDevice());

        List<EventMetricData> data = runVibratorDeviceTests(
                Atom.VIBRATION_REPORTED_FIELD_NUMBER, "testOneShotVibration");

        assertSingleValueOccurred(VibrationReported.VibrationType.SINGLE_VALUE, data,
                atom -> atom.getVibrationReported().getVibrationType().getNumber());
    }

    @Test
    public void testVibrationReportedRepeatedVibration() throws Exception {
        assumeTrue(isVibratorSupportedOnDevice());

        List<EventMetricData> data = runVibratorDeviceTests(
                Atom.VIBRATION_REPORTED_FIELD_NUMBER, "testRepeatedWaveformVibration");

        assertSingleValueOccurred(VibrationReported.VibrationType.REPEATED_VALUE, data,
                atom -> atom.getVibrationReported().getVibrationType().getNumber());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_PRIMITIVE_COMPOSITION_ABSOLUTE_DELAY)
    public void testVibrationReportedComposedVibrationWithFeatureFlagDisabled() throws Exception {
        assumeTrue(isVibratorSupportedOnDevice());

        List<EventMetricData> data = runVibratorDeviceTests(
                Atom.VIBRATION_REPORTED_FIELD_NUMBER, "testComposedTickThenClickVibration");

        assertSingleValueOccurred(VibrationReported.VibrationType.SINGLE_VALUE, data,
                atom -> atom.getVibrationReported().getVibrationType().getNumber());
        assertSingleValueOccurred(/* expectedValue= */ 1, data,
                atom -> atom.getVibrationReported().getHalComposeCount());
        assertSingleValueOccurred(/* expectedValue= */ 2, data,
                atom -> atom.getVibrationReported().getHalCompositionSize());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PRIMITIVE_COMPOSITION_ABSOLUTE_DELAY)
    public void testVibrationReportedComposedVibration() throws Exception {
        assumeTrue(isTickAndClickPrimitivesSupportedOnDevice());

        List<EventMetricData> data =
                runVibratorDeviceTests(
                        Atom.VIBRATION_REPORTED_FIELD_NUMBER, "testComposedTickThenClickVibration");

        assertSingleValueOccurred(
                VibrationReported.VibrationType.SINGLE_VALUE,
                data,
                atom -> atom.getVibrationReported().getVibrationType().getNumber());
        assertSingleValueOccurred(
                /* expectedValue= */ 1,
                data,
                atom -> atom.getVibrationReported().getHalComposeCount());
        assertSingleValueOccurred(
                /* expectedValue= */ 2,
                data,
                atom -> atom.getVibrationReported().getHalCompositionSize());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PRIMITIVE_COMPOSITION_ABSOLUTE_DELAY)
    public void testVibrationReportedComposedVibration_noSupportForPrimitives() throws Exception {
        assumeTrue(isVibratorSupportedOnDevice());
        assumeFalse(isTickAndClickPrimitivesSupportedOnDevice());

        List<EventMetricData> data =
                runVibratorDeviceTests(
                        Atom.VIBRATION_REPORTED_FIELD_NUMBER, "testComposedTickThenClickVibration");

        assertSingleValueOccurred(
                VibrationReported.VibrationType.SINGLE_VALUE,
                data,
                atom -> atom.getVibrationReported().getVibrationType().getNumber());
        assertSingleValueOccurred(
                /* expectedValue= */ 0,
                data,
                atom -> atom.getVibrationReported().getHalComposeCount());
        assertSingleValueOccurred(
                /* expectedValue= */ 0,
                data,
                atom -> atom.getVibrationReported().getHalCompositionSize());
    }

    @Test
    public void testVibrationReportedPredefinedVibration() throws Exception {
        if (!isVibratorSupportedOnDevice()) {
            return;
        }

        List<EventMetricData> data = runVibratorDeviceTests(
                Atom.VIBRATION_REPORTED_FIELD_NUMBER, "testPredefinedClickVibration");

        assertSingleValueOccurred(VibrationReported.VibrationType.SINGLE_VALUE, data,
                atom -> atom.getVibrationReported().getVibrationType().getNumber());
        assertSingleValueOccurred(/* expectedValue= */ 1, data,
                atom -> atom.getVibrationReported().getHalPerformCount());
    }

    private boolean isVibratorSupportedOnDevice() throws Exception {
        return DeviceUtils.checkDeviceFor(getDevice(), "checkVibratorSupported");
    }

    private boolean isTickAndClickPrimitivesSupportedOnDevice() throws Exception {
        return DeviceUtils.checkDeviceFor(
                getDevice(), "checkVibratorPrimitivesTickAndClickAreSupported");
    }

    private List<EventMetricData> runVibratorDeviceTests(int atomTag, String testMethodName)
            throws Exception {
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /* useUidAttributionChain= */ true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".VibratorTests", testMethodName);

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Sorted list of events in order in which they occurred.
        return ReportUtils.getEventMetricDataList(getDevice());
    }

    private void assertValuesOccurredInOrder(List<Integer> expectedValues,
            int expectedTimeBetweenStates, List<EventMetricData> data,
            Function<Atom, Integer> valueFromAtomFn) {
        AtomTestUtils.assertStatesOccurredInOrder(
                expectedValues.stream().map(Arrays::asList).map(HashSet::new).collect(toList()),
                data, expectedTimeBetweenStates, valueFromAtomFn);
    }

    private void assertSingleValueOccurred(int expectedValue,
            List<EventMetricData> data, Function<Atom, Integer> valueFromAtomFn) {
        AtomTestUtils.assertStatesOccurredInOrder(
                Arrays.asList(new HashSet<>(Arrays.asList(expectedValue))),
                data, /* wait= */ 0, valueFromAtomFn);
    }
}
