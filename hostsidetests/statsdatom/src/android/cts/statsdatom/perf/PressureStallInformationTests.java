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

package android.cts.statsdatom.perf;

import static com.android.server.stats.Flags.FLAG_ADD_PRESSURE_STALL_INFORMATION_PULLER;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.os.AtomsProto;
import com.android.os.performance.PerformanceExtensionAtoms;
import com.android.tradefed.build.IBuildInfo;
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

import java.util.HashSet;
import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class PressureStallInformationTests extends BaseHostJUnit4Test implements IBuildReceiver {
    private IBuildInfo mCtsBuild;
    private ExtensionRegistry mRegistry;

    private static final int PSI_RESOURCE_NUMBER = 3;

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

        mRegistry = ExtensionRegistry.newInstance();
        PerformanceExtensionAtoms.registerAllExtensions(mRegistry);
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
    @RequiresFlagsEnabled(FLAG_ADD_PRESSURE_STALL_INFORMATION_PULLER)
    public void testPressureStallInformation() throws Exception {
        List<AtomsProto.Atom> atoms = pullPSIAsGaugeMetric();
        assertThat(atoms).hasSize(3);
        HashSet<Integer> pulledPsiResources = new HashSet<>();
        for (int i = 0; i < PSI_RESOURCE_NUMBER; i++) {
            PerformanceExtensionAtoms.PressureStallInformation psi = atoms.get(i).getExtension(
                    PerformanceExtensionAtoms.pressureStallInformation);
            assertThat(psi.getPsiResource().getNumber()).isAtLeast(1);
            pulledPsiResources.add(psi.getPsiResource().getNumber());
            assertThat(psi.getSomeAvg10SPct()).isAtLeast((float) 0.0);
            assertThat(psi.getSomeAvg60SPct()).isAtLeast((float) 0.0);
            assertThat(psi.getSomeAvg300SPct()).isAtLeast((float) 0.0);
            assertThat(psi.getSomeTotalUsec()).isAtLeast((0L));
            assertThat(psi.getFullAvg10SPct()).isAtLeast((float) 0.0);
            assertThat(psi.getFullAvg60SPct()).isAtLeast((float) 0.0);
            assertThat(psi.getFullAvg300SPct()).isAtLeast((float) 0.0);
            assertThat(psi.getFullTotalUsec()).isAtLeast((0L));
        }
        assertThat(pulledPsiResources.size()).isEqualTo(PSI_RESOURCE_NUMBER);
    }

    /** Returns PSI atoms pulled as a simple gauge metric while test app is running. */
    private List<AtomsProto.Atom> pullPSIAsGaugeMetric() throws Exception {
        // Get PSI as a simple gauge metric.
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PerformanceExtensionAtoms.PRESSURE_STALL_INFORMATION_FIELD_NUMBER);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        return ReportUtils.getGaugeMetricAtoms(getDevice(), mRegistry, false);
    }

}
