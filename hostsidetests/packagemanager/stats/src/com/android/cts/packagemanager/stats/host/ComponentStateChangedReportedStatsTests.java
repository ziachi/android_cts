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

package com.android.cts.packagemanager.stats.host;

import static android.content.pm.Flags.FLAG_COMPONENT_STATE_CHANGED_METRICS;

import static com.android.os.packagemanager.ComponentStateChangedReported.ComponentState.COMPONENT_STATE_DEFAULT;
import static com.android.os.packagemanager.ComponentStateChangedReported.ComponentState.COMPONENT_STATE_DISABLED;
import static com.android.os.packagemanager.ComponentStateChangedReported.ComponentState.COMPONENT_STATE_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.os.StatsLog;
import com.android.os.packagemanager.ComponentStateChangedReported;
import com.android.os.packagemanager.PackagemanagerExtensionAtoms;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for ComponentStateChangedReported logging.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class ComponentStateChangedReportedStatsTests extends BaseHostJUnit4Test {
    private static final String TEST_INSTALL_APK = "CtsStatsdAtomTestComponentStateApp.apk";
    private static final String TEST_INSTALL_PACKAGE =
            "com.android.cts.packagemanager.stats.testcomponentstateapp";
    private static final String HELPER_PACKAGE = "com.android.cts.packagemanager.stats.device";
    private static final String HELPER_CLASS =
            HELPER_PACKAGE + ".ComponentStateChangedReportedStatsTestsHelper";
    private static final String TEST_METHOD_SET_APPLICATION_ENABLED_SETTING =
            "testSetApplicationEnabledSetting";
    private static final String TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_FOR_LAUNCHER_ACTIVITY =
            "testSetComponentEnabledSettingForLauncherActivity";
    private static final String TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_FOR_NO_LAUNCHER_ACTIVITY =
            "testSetComponentEnabledSettingForNoLauncherActivity";
    private static final String TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_ENABLED_THEN_DISABLED =
            "testSetComponentEnabledSettingEnabledThenDisabled";
    private static final String TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_TWO_LAUNCHER_ACTIVITIES =
            "testComponentStateChangedReportedForTwoDifferentStateLauncherActivities";
    private static final String TEST_METHOD_SET_APPLICATION_ENABLED_THEN_DISABLED =
            "testComponentStateChangedReportedEnabledThenDisabledWholeApp";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    @Before
    public void setUp() throws Exception {
        installPackage("CtsStatsdAtomApp.apk");
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_INSTALL_PACKAGE);
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @RequiresFlagsEnabled(FLAG_COMPONENT_STATE_CHANGED_METRICS)
    @Test
    public void testComponentStateChangedReportedForWholeApp() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.COMPONENT_STATE_CHANGED_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        installPackage(TEST_INSTALL_APK);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();

        // Run test in CTS package
        runDeviceTests(getDevice(), HELPER_PACKAGE, HELPER_CLASS,
                TEST_METHOD_SET_APPLICATION_ENABLED_SETTING);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        data = retrieveEventMetricDataChangeFromTestComponentStateApp(data);
        assertThat(data.isEmpty()).isFalse();

        ComponentStateChangedReported atom = data.get(0).getAtom().getExtension(
                PackagemanagerExtensionAtoms.componentStateChangedReported);
        assertThat(atom.getUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), TEST_INSTALL_PACKAGE));
        assertThat(atom.getComponentOldState()).isEqualTo(COMPONENT_STATE_DEFAULT);
        assertThat(atom.getComponentNewState()).isEqualTo(COMPONENT_STATE_ENABLED);
        assertThat(atom.getIsLauncher()).isFalse();
        assertThat(atom.getIsForWholeApp()).isTrue();
        assertThat(atom.getCallingUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));
    }

    @RequiresFlagsEnabled(FLAG_COMPONENT_STATE_CHANGED_METRICS)
    @Test
    public void testComponentStateChangedReportedForLauncherActivity() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.COMPONENT_STATE_CHANGED_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        installPackage(TEST_INSTALL_APK);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();

        // Run test in CTS package
        runDeviceTests(getDevice(), HELPER_PACKAGE, HELPER_CLASS,
                TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_FOR_LAUNCHER_ACTIVITY);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        data = retrieveEventMetricDataChangeFromTestComponentStateApp(data);
        assertThat(data.isEmpty()).isFalse();

        ComponentStateChangedReported atom = data.get(0).getAtom().getExtension(
                PackagemanagerExtensionAtoms.componentStateChangedReported);
        assertThat(atom.getUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), TEST_INSTALL_PACKAGE));
        assertThat(atom.getComponentOldState()).isEqualTo(COMPONENT_STATE_DEFAULT);
        assertThat(atom.getComponentNewState()).isEqualTo(COMPONENT_STATE_ENABLED);
        assertThat(atom.getIsLauncher()).isTrue();
        assertThat(atom.getIsForWholeApp()).isFalse();
        assertThat(atom.getCallingUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));
    }

    @RequiresFlagsEnabled(FLAG_COMPONENT_STATE_CHANGED_METRICS)
    @Test
    public void testComponentStateChangedReportedForNoLauncherActivity() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.COMPONENT_STATE_CHANGED_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        installPackage(TEST_INSTALL_APK);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();

        // Run test in CTS package
        runDeviceTests(getDevice(), HELPER_PACKAGE, HELPER_CLASS,
                TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_FOR_NO_LAUNCHER_ACTIVITY);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        data = retrieveEventMetricDataChangeFromTestComponentStateApp(data);
        assertThat(data.isEmpty()).isFalse();

        ComponentStateChangedReported atom = data.get(0).getAtom().getExtension(
                PackagemanagerExtensionAtoms.componentStateChangedReported);
        assertThat(atom.getUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), TEST_INSTALL_PACKAGE));
        assertThat(atom.getComponentOldState()).isEqualTo(COMPONENT_STATE_DEFAULT);
        assertThat(atom.getComponentNewState()).isEqualTo(COMPONENT_STATE_ENABLED);
        assertThat(atom.getIsLauncher()).isFalse();
        assertThat(atom.getIsForWholeApp()).isFalse();
        assertThat(atom.getCallingUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));
    }

    @RequiresFlagsEnabled(FLAG_COMPONENT_STATE_CHANGED_METRICS)
    @Test
    public void testComponentStateChangedReportedEnabledThenDisabled() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.COMPONENT_STATE_CHANGED_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        installPackage(TEST_INSTALL_APK);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();

        // Run test in CTS package
        runDeviceTests(getDevice(), HELPER_PACKAGE, HELPER_CLASS,
                TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_ENABLED_THEN_DISABLED);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        data = retrieveEventMetricDataChangeFromTestComponentStateApp(data);
        assertThat(data.size()).isEqualTo(2);

        ComponentStateChangedReported atom1 = data.get(0).getAtom().getExtension(
                PackagemanagerExtensionAtoms.componentStateChangedReported);
        assertThat(atom1.getUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), TEST_INSTALL_PACKAGE));
        assertThat(atom1.getComponentOldState()).isEqualTo(COMPONENT_STATE_DEFAULT);
        assertThat(atom1.getComponentNewState()).isEqualTo(COMPONENT_STATE_ENABLED);
        assertThat(atom1.getIsLauncher()).isFalse();
        assertThat(atom1.getIsForWholeApp()).isFalse();
        assertThat(atom1.getCallingUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));

        ComponentStateChangedReported atom2 = data.get(1).getAtom().getExtension(
                PackagemanagerExtensionAtoms.componentStateChangedReported);
        assertThat(atom2.getUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), TEST_INSTALL_PACKAGE));
        assertThat(atom2.getComponentOldState()).isEqualTo(COMPONENT_STATE_ENABLED);
        assertThat(atom2.getComponentNewState()).isEqualTo(COMPONENT_STATE_DISABLED);
        assertThat(atom2.getIsLauncher()).isFalse();
        assertThat(atom2.getIsForWholeApp()).isFalse();
        assertThat(atom2.getCallingUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));
    }

    @RequiresFlagsEnabled(FLAG_COMPONENT_STATE_CHANGED_METRICS)
    @Test
    public void testComponentStateChangedReportedEnabledThenDisabledWholeApp() throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.COMPONENT_STATE_CHANGED_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        installPackage(TEST_INSTALL_APK);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();

        // Run test in CTS package
        runDeviceTests(getDevice(), HELPER_PACKAGE, HELPER_CLASS,
                TEST_METHOD_SET_APPLICATION_ENABLED_THEN_DISABLED);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        data = retrieveEventMetricDataChangeFromTestComponentStateApp(data);
        assertThat(data.size()).isEqualTo(2);

        ComponentStateChangedReported atom1 = data.get(0).getAtom().getExtension(
                PackagemanagerExtensionAtoms.componentStateChangedReported);
        assertThat(atom1.getUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), TEST_INSTALL_PACKAGE));
        assertThat(atom1.getComponentOldState()).isEqualTo(COMPONENT_STATE_DEFAULT);
        assertThat(atom1.getComponentNewState()).isEqualTo(COMPONENT_STATE_ENABLED);
        assertThat(atom1.getIsLauncher()).isFalse();
        assertThat(atom1.getIsForWholeApp()).isTrue();
        assertThat(atom1.getCallingUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));

        ComponentStateChangedReported atom2 = data.get(1).getAtom().getExtension(
                PackagemanagerExtensionAtoms.componentStateChangedReported);
        assertThat(atom2.getUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), TEST_INSTALL_PACKAGE));
        assertThat(atom2.getComponentOldState()).isEqualTo(COMPONENT_STATE_ENABLED);
        assertThat(atom2.getComponentNewState()).isEqualTo(COMPONENT_STATE_DISABLED);
        assertThat(atom2.getIsLauncher()).isFalse();
        assertThat(atom2.getIsForWholeApp()).isTrue();
        assertThat(atom2.getCallingUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));
    }

    @RequiresFlagsEnabled(FLAG_COMPONENT_STATE_CHANGED_METRICS)
    @Test
    public void testComponentStateChangedReportedForTwoDifferentStateLauncherActivities()
            throws Throwable {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                PackagemanagerExtensionAtoms.COMPONENT_STATE_CHANGED_REPORTED_FIELD_NUMBER);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        PackagemanagerExtensionAtoms.registerAllExtensions(registry);

        installPackage(TEST_INSTALL_APK);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();

        // Run test in CTS package
        runDeviceTests(getDevice(), HELPER_PACKAGE, HELPER_CLASS,
                TEST_METHOD_SET_COMPONENT_ENABLED_SETTING_TWO_LAUNCHER_ACTIVITIES);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(),
                registry);
        data = retrieveEventMetricDataChangeFromTestComponentStateApp(data);
        assertThat(data.size()).isEqualTo(2);

        ComponentStateChangedReported atom1 = data.get(0).getAtom().getExtension(
                PackagemanagerExtensionAtoms.componentStateChangedReported);
        assertThat(atom1.getUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), TEST_INSTALL_PACKAGE));
        assertThat(atom1.getComponentOldState()).isEqualTo(COMPONENT_STATE_DEFAULT);
        assertThat(atom1.getComponentNewState()).isEqualTo(COMPONENT_STATE_DISABLED);
        assertThat(atom1.getIsLauncher()).isTrue();
        assertThat(atom1.getIsForWholeApp()).isFalse();
        assertThat(atom1.getCallingUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));

        ComponentStateChangedReported atom2 = data.get(1).getAtom().getExtension(
                PackagemanagerExtensionAtoms.componentStateChangedReported);
        assertThat(atom2.getUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), TEST_INSTALL_PACKAGE));
        assertThat(atom2.getComponentOldState()).isEqualTo(COMPONENT_STATE_DEFAULT);
        assertThat(atom2.getComponentNewState()).isEqualTo(COMPONENT_STATE_ENABLED);
        assertThat(atom2.getIsLauncher()).isTrue();
        assertThat(atom2.getIsForWholeApp()).isFalse();
        assertThat(atom2.getCallingUid()).isEqualTo(
                PackageManagerStatsTestsBase.getAppUid(getDevice(), HELPER_PACKAGE));
    }

    List<StatsLog.EventMetricData> retrieveEventMetricDataChangeFromTestComponentStateApp(
            List<StatsLog.EventMetricData> eventMetricData) throws Exception {
        List<StatsLog.EventMetricData> dataList = new ArrayList<>();
        if (eventMetricData == null || eventMetricData.size() == 0) {
            return dataList;
        }
        for (int i = 0; i < eventMetricData.size(); i++) {
            ComponentStateChangedReported atom = eventMetricData.get(i).getAtom().getExtension(
                    PackagemanagerExtensionAtoms.componentStateChangedReported);
            if (atom != null && atom.getUid() == PackageManagerStatsTestsBase.getAppUid(getDevice(),
                    TEST_INSTALL_PACKAGE)) {
                dataList.add(eventMetricData.get(i));
            }
        }
        return dataList;
    }
}
