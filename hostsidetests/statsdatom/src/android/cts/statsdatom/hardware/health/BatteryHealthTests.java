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

package android.cts.statsdatom.hardware.health;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.hardware.health.BatteryExtensionAtoms;
import com.android.os.hardware.health.BatteryHealth;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import com.google.common.collect.Range;
import com.google.protobuf.ExtensionRegistry;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

public class BatteryHealthTests extends DeviceTestCase implements IBuildReceiver {
    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testBatteryHealthAtomValid() throws Exception {
        List<AtomsProto.Atom> atoms = pullBatteryHealthAsGaugeMetric();

        assertThat(atoms.size()).isEqualTo(1);

        BatteryHealth bh = atoms.get(0).getExtension(BatteryExtensionAtoms.batteryHealth);

        int manufacturing_date = bh.getBatteryManufacturingDate();
        Calendar manufacturing_cal = intYYYYMMDDToCalendar(manufacturing_date);
        assertThat(manufacturing_cal.get(Calendar.DAY_OF_WEEK)).isEqualTo(Calendar.MONDAY);

        int first_usage_date = bh.getBatteryFirstUsageDate();
        Calendar first_usage_cal = intYYYYMMDDToCalendar(first_usage_date);
        assertThat(first_usage_cal.get(Calendar.DAY_OF_WEEK)).isEqualTo(Calendar.MONDAY);

        assertThat(bh.getBatteryStateOfHealth()).isIn(Range.closed(0, 100));

        assertThat(bh.getBatterySerialNumberHash()).isIn(Range.closed(0, 0xFF));
    }

    private List<AtomsProto.Atom> pullBatteryHealthAsGaugeMetric() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                BatteryExtensionAtoms.BATTERY_HEALTH_FIELD_NUMBER);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        BatteryExtensionAtoms.registerAllExtensions(registry);

        List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice(), registry, false);

        return atoms;
    }

    private Calendar intYYYYMMDDToCalendar(int date) {
        int year = date / 10000;
        int month = date / 100 % 100;
        int day = date % 100;

        return new GregorianCalendar(year, month - 1, day); // Months are indexed 0-11
    }
}
