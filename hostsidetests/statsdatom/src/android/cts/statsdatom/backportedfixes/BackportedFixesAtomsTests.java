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

package android.cts.statsdatom.backportedfixes;


import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.os.BackportedFixStatus;
import android.os.statsd.backportedfixes.BackportedFixesExtensionAtoms;
import android.os.statsd.backportedfixes.BackportedFixesExtensionAtoms.BackportedFixStatusReported;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.ddmlib.testrunner.TestResult;
import com.android.os.AtomsProto;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.GeneratedMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Test for BackportedFixes atoms.
 *
 *  <p>Build/Install/Run:
 *  atest CtsStatsdAtomHostTestCases:BackportedFixesAtomsTests
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class BackportedFixesAtomsTests extends BaseHostJUnit4Test implements IBuildReceiver {

    @Rule(order = 1)
    public final TestName name = new TestName();

    @Rule(order = 2)
    public final CheckFlagsRule checkFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    private IBuildInfo mCtsBuild;
    private static final String TEST_PKG = "com.android.server.cts.device.statsdatom";
    private static final String TEST_CLASS = ".BackportedFixesTests";

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
    @RequiresFlagsEnabled(android.os.Flags.FLAG_API_FOR_BACKPORTED_FIXES)
    public void getBackportedFixStatus_alwaysFixed() throws Exception {
        // Upload config to statsd
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                BackportedFixesExtensionAtoms.BACKPORTED_FIX_STATUS_REPORTED_FIELD_NUMBER);


        TestRunResult testRunResult =
                DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS, name.getMethodName());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        TestResult.TestStatus status = getDeviceTestStatus(testRunResult);
        assertThat(status).isEqualTo(TestResult.TestStatus.PASSED);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        BackportedFixesExtensionAtoms.registerAllExtensions(registry);
        BackportedFixStatusReported
                reported = getOnlyAtomFromDevice(registry,
                BackportedFixesExtensionAtoms.backportedFixStatusReported);

        assertThat(reported).isEqualTo(BackportedFixStatusReported.newBuilder()
                .setUid(DeviceUtils.getAppUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG))
                .setId(1)   // Known issue 350037023 has an alias of 1
                .setStatus(BackportedFixStatus.BACKPORTED_FIX_STATUS_FIXED)
                .build());
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_API_FOR_BACKPORTED_FIXES)
    public void getBackportedFixStatus_neverFixed() throws Exception {
        // Upload config to statsd
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                BackportedFixesExtensionAtoms.BACKPORTED_FIX_STATUS_REPORTED_FIELD_NUMBER);


        TestRunResult testRunResult =
                DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS, name.getMethodName());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        TestResult.TestStatus status = getDeviceTestStatus(testRunResult);
        assertThat(status).isEqualTo(TestResult.TestStatus.PASSED);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        BackportedFixesExtensionAtoms.registerAllExtensions(registry);
        BackportedFixStatusReported
                reported = getOnlyAtomFromDevice(registry,
                BackportedFixesExtensionAtoms.backportedFixStatusReported);

        assertThat(reported).isEqualTo(BackportedFixStatusReported.newBuilder()
                .setUid(DeviceUtils.getAppUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG))
                .setId(3)  // Known issue 350037348 has an alias of 3
                .setStatus(BackportedFixStatus.BACKPORTED_FIX_STATUS_UNKNOWN)
                .build());
    }

    private <T> T getOnlyAtomFromDevice(ExtensionRegistry registry,
            GeneratedMessage.GeneratedExtension<AtomsProto.Atom, T> extension)
            throws Exception {
        // Get the atoms logged by the device interactions
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(), registry);
        assertThat(data).hasSize(1);
        return data.getFirst().getAtom().getExtension(extension);

    }

    private TestResult.TestStatus getDeviceTestStatus(TestRunResult testRunResult) {
        final TestDescription desc =
                TestDescription.fromString(TEST_PKG + TEST_CLASS + "#" + name.getMethodName());
        return testRunResult.getTestResults().get(desc).getStatus();
    }
}
