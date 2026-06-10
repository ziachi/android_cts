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

package android.cts.statsdatom.media.projection;


import static android.cts.statsdatom.lib.DeviceUtils.FEATURE_WATCH;

import static com.android.os.framework.FrameworkExtensionAtoms.MEDIA_PROJECTION_STATE_CHANGED_FIELD_NUMBER;
import static com.android.os.framework.FrameworkExtensionAtoms.MEDIA_PROJECTION_TARGET_CHANGED_FIELD_NUMBER;
import static com.android.os.framework.FrameworkExtensionAtoms.MediaProjectionStateChanged.MediaProjectionState.MEDIA_PROJECTION_STATE_APP_SELECTOR_DISPLAYED;
import static com.android.os.framework.FrameworkExtensionAtoms.MediaProjectionStateChanged.MediaProjectionState.MEDIA_PROJECTION_STATE_CANCELLED;
import static com.android.os.framework.FrameworkExtensionAtoms.MediaProjectionStateChanged.MediaProjectionState.MEDIA_PROJECTION_STATE_CAPTURING_IN_PROGRESS;
import static com.android.os.framework.FrameworkExtensionAtoms.MediaProjectionStateChanged.MediaProjectionState.MEDIA_PROJECTION_STATE_INITIATED;
import static com.android.os.framework.FrameworkExtensionAtoms.MediaProjectionStateChanged.MediaProjectionState.MEDIA_PROJECTION_STATE_PERMISSION_REQUEST_DISPLAYED;
import static com.android.os.framework.FrameworkExtensionAtoms.MediaProjectionStateChanged.MediaProjectionState.MEDIA_PROJECTION_STATE_STOPPED;
import static com.android.os.framework.FrameworkExtensionAtoms.MediaProjectionTargetChanged.TargetChangeType.TARGET_CHANGE_BOUNDS;
import static com.android.os.framework.FrameworkExtensionAtoms.MediaProjectionTargetChanged.TargetChangeType.TARGET_CHANGE_WINDOWING_MODE;
import static com.android.os.framework.FrameworkExtensionAtoms.MediaProjectionTargetChanged.TargetType.TARGET_TYPE_DISPLAY;
import static com.android.os.framework.FrameworkExtensionAtoms.MediaProjectionTargetChanged.WindowingMode.WINDOWING_MODE_FULLSCREEN;
import static com.android.os.framework.FrameworkExtensionAtoms.mediaProjectionStateChanged;
import static com.android.os.framework.FrameworkExtensionAtoms.mediaProjectionTargetChanged;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.ddmlib.testrunner.TestResult;
import com.android.os.StatsLog.EventMetricData;
import com.android.os.framework.FrameworkExtensionAtoms;
import com.android.os.framework.FrameworkExtensionAtoms.MediaProjectionTargetChanged;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import com.google.common.truth.IterableSubject;
import com.google.protobuf.ExtensionRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Test for MediaProjection atoms.
 *
 *  <p>Build/Install/Run:
 *  atest CtsStatsdAtomHostTestCases:MediaProjectionAtomsTest
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class MediaProjectionAtomsTests extends BaseHostJUnit4Test implements IBuildReceiver {
    private IBuildInfo mCtsBuild;
    private static final String TEST_APK = "CtsMediaProjectionTestCases.apk";
    private static final String TEST_PKG = "android.media.projection.cts";

    @Before
    public void setUp() throws Exception {
        assumeFalse(DeviceUtils.hasFeature(getDevice(), FEATURE_WATCH));
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.turnScreenOn(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.installTestApp(getDevice(), TEST_APK, TEST_PKG, mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), TEST_PKG);
        DeviceUtils.uninstallStatsdTestApp(getDevice());
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Test
    public void testMediaProjectionStateChanged_stoppedCapture() throws Exception {
        // Upload config to statsd
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                MEDIA_PROJECTION_STATE_CHANGED_FIELD_NUMBER);

        // Run an external CTS (CtsMediaProjectionTestCases#testCallbackOnStop) to generate
        // device interactions that cause MediaProjectionStateChanged atoms to be logged
        final String testClass = ".MediaProjectionTest";
        final String testMethod = "testCallbackOnStop";
        final TestDescription desc =
                TestDescription.fromString(TEST_PKG + testClass + "#" + testMethod);

        TestRunResult testRunResult =
                DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, testClass, testMethod);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Check that CTS passed
        TestResult.TestStatus status = testRunResult.getTestResults().get(desc).getStatus();
        assertThat(status).isEqualTo(TestResult.TestStatus.PASSED);

        // Get the atoms logged by the device interactions
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        FrameworkExtensionAtoms.registerAllExtensions(registry);
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(), registry);

        // Check the expected MediaProjectionStateChanged atoms were logged in the expected order
        assertThatMediaProjectionState(data)
                .containsExactly(
                        MEDIA_PROJECTION_STATE_INITIATED,
                        MEDIA_PROJECTION_STATE_PERMISSION_REQUEST_DISPLAYED,
                        MEDIA_PROJECTION_STATE_CAPTURING_IN_PROGRESS,
                        MEDIA_PROJECTION_STATE_STOPPED)
                .inOrder();
    }

    @Test
    public void testMediaProjectionTargetChanged_stoppedCapture() throws Exception {
        // Upload config to statsd
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                MEDIA_PROJECTION_TARGET_CHANGED_FIELD_NUMBER);

        // Run an external CTS (CtsMediaProjectionTestCases#testCallbackOnStop) to generate
        // device interactions that cause MediaProjectionTargetChanged atoms to be logged
        final String testClass = ".MediaProjectionTest";
        final String testMethod = "testCallbackOnStop";
        final TestDescription desc =
                TestDescription.fromString(TEST_PKG + testClass + "#" + testMethod);

        TestRunResult testRunResult =
                DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, testClass, testMethod);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Check that CTS passed
        TestResult.TestStatus status = testRunResult.getTestResults().get(desc).getStatus();
        assertThat(status).isEqualTo(TestResult.TestStatus.PASSED);

        // Get the atoms logged by the device interactions
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        FrameworkExtensionAtoms.registerAllExtensions(registry);
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(), registry);

        // Check the expected MediaProjectionTargetChanged atoms were logged in the expected order
        assertThat(data.size()).isEqualTo(2);

        MediaProjectionTargetChanged a0 =
                data.get(0).getAtom().getExtension(mediaProjectionTargetChanged);
        assertThat(a0.getTargetType()).isEqualTo(TARGET_TYPE_DISPLAY);

        MediaProjectionTargetChanged a1 =
                data.get(1).getAtom().getExtension(mediaProjectionTargetChanged);
        assertThat(a1.getTargetType()).isEqualTo(TARGET_TYPE_DISPLAY);
        assertThat(a1.getTargetChangeType()).isEqualTo(TARGET_CHANGE_WINDOWING_MODE);
        assertThat(a1.getTargetWindowingMode()).isEqualTo(WINDOWING_MODE_FULLSCREEN);

        assertThat(a1.getCenterX()).isEqualTo(a0.getCenterX());
        assertThat(a1.getCenterY()).isEqualTo(a0.getCenterY());
        assertThat(a1.getWidth()).isEqualTo(a0.getWidth());
        assertThat(a1.getHeight()).isEqualTo(a0.getHeight());
    }

    @Test
    public void testMediaProjectionStateChanged_setupCancelled() throws Exception {
        // Upload config to statsd
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                MEDIA_PROJECTION_STATE_CHANGED_FIELD_NUMBER);

        // Run an local test (MediaProjectionTests#testMediaProjectionPermissionDialogCancel) to
        // generate device interactions that cause MediaProjectionStateChanged atoms to be logged
        final String testClass = ".MediaProjectionTests";
        final String testMethod = "testMediaProjectionPermissionDialogCancel";

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), testClass, testMethod);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Get the atoms logged by the device interactions
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        FrameworkExtensionAtoms.registerAllExtensions(registry);
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(), registry);

        // Check the expected MediaProjectionStateChanged atoms were logged in the expected order
        assertThatMediaProjectionState(data)
                .containsExactly(
                        MEDIA_PROJECTION_STATE_INITIATED,
                        MEDIA_PROJECTION_STATE_PERMISSION_REQUEST_DISPLAYED,
                        MEDIA_PROJECTION_STATE_CANCELLED)
                .inOrder();
    }

    @Test
    public void testMediaProjectionStateChanged_appSelectorShown() throws Exception {
        final String testClass = ".MediaProjectionTests";
        final String testMethod2 = "testMediaProjectionShowAppSelector";

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                MEDIA_PROJECTION_STATE_CHANGED_FIELD_NUMBER);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), testClass, testMethod2);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        FrameworkExtensionAtoms.registerAllExtensions(registry);
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(), registry);

        // In the case where an OEMs doesn't support partial screenshare, this atom won't be logged,
        // so we only assert on it being emitted conditionally.
        if (data.size() <= 2) {
            assertThatMediaProjectionState(data)
                    .containsExactly(
                            MEDIA_PROJECTION_STATE_INITIATED,
                            MEDIA_PROJECTION_STATE_PERMISSION_REQUEST_DISPLAYED)
                    .inOrder();
        } else {
            assertThatMediaProjectionState(data)
                    .containsExactly(
                            MEDIA_PROJECTION_STATE_INITIATED,
                            MEDIA_PROJECTION_STATE_PERMISSION_REQUEST_DISPLAYED,
                            MEDIA_PROJECTION_STATE_APP_SELECTOR_DISPLAYED)
                    .inOrder();
        }
    }

    private static IterableSubject assertThatMediaProjectionState(List<EventMetricData> data) {
        return assertThat(
                data.stream()
                        .map(
                                (e) ->
                                        e.getAtom()
                                                .getExtension(mediaProjectionStateChanged)
                                                .getState())
                        .toList());
    }
}
