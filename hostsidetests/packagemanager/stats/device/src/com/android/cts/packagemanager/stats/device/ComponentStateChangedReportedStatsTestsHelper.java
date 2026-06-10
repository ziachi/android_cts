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

package com.android.cts.packagemanager.stats.device;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

import static org.junit.Assert.assertEquals;

import android.content.ComponentName;
import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Test;

/**
 * Helper class for ComponentStateChangedReported logging.
 */
public class ComponentStateChangedReportedStatsTestsHelper {
    private static final String TEST_COMPONENT_STATE_APP_PACKAGE_NAME =
            "com.android.cts.packagemanager.stats.testcomponentstateapp";
    private static final String FAKE_LAUNCHER_ACTIVITY_NAME =
            TEST_COMPONENT_STATE_APP_PACKAGE_NAME + ".FakeLauncherActivity";
    private static final String FAKE_NO_LAUNCHER_ACTIVITY_NAME =
            TEST_COMPONENT_STATE_APP_PACKAGE_NAME + ".FakeNoLauncherActivity";
    private static final String FAKE_DEFAULT_ENABLED_LAUNCHER_ACTIVITY_NAME =
            TEST_COMPONENT_STATE_APP_PACKAGE_NAME + ".FakeDefaultEnabledLauncherActivity";
    private PackageManager mPackageManager;

    @Before
    public void setup() throws Exception {
        mPackageManager =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
    }

    @Test
    public void testSetApplicationEnabledSetting() {
        SystemUtil.runWithShellPermissionIdentity(() ->
                mPackageManager.setApplicationEnabledSetting(TEST_COMPONENT_STATE_APP_PACKAGE_NAME,
                        COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP));
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED,
                mPackageManager.getApplicationEnabledSetting(
                        TEST_COMPONENT_STATE_APP_PACKAGE_NAME));
    }

    @Test
    public void testSetComponentEnabledSettingForLauncherActivity() {
        ComponentName componentName = new ComponentName(TEST_COMPONENT_STATE_APP_PACKAGE_NAME,
                FAKE_LAUNCHER_ACTIVITY_NAME);
        SystemUtil.runWithShellPermissionIdentity(() ->
                mPackageManager.setComponentEnabledSetting(componentName,
                        COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP));
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED,
                mPackageManager.getComponentEnabledSetting(componentName));
    }

    @Test
    public void testSetComponentEnabledSettingForNoLauncherActivity() {
        ComponentName componentName = new ComponentName(TEST_COMPONENT_STATE_APP_PACKAGE_NAME,
                FAKE_NO_LAUNCHER_ACTIVITY_NAME);
        SystemUtil.runWithShellPermissionIdentity(() ->
                mPackageManager.setComponentEnabledSetting(componentName,
                        COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP));
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED,
                mPackageManager.getComponentEnabledSetting(componentName));
    }

    @Test
    public void testSetComponentEnabledSettingEnabledThenDisabled() {
        ComponentName componentName = new ComponentName(TEST_COMPONENT_STATE_APP_PACKAGE_NAME,
                FAKE_NO_LAUNCHER_ACTIVITY_NAME);
        SystemUtil.runWithShellPermissionIdentity(() ->
                mPackageManager.setComponentEnabledSetting(componentName,
                        COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP));
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED,
                mPackageManager.getComponentEnabledSetting(componentName));

        SystemUtil.runWithShellPermissionIdentity(() ->
                mPackageManager.setComponentEnabledSetting(componentName,
                        COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP));
        assertEquals(COMPONENT_ENABLED_STATE_DISABLED,
                mPackageManager.getComponentEnabledSetting(componentName));
    }

    @Test
    public void testComponentStateChangedReportedEnabledThenDisabledWholeApp() {
        SystemUtil.runWithShellPermissionIdentity(() ->
                mPackageManager.setApplicationEnabledSetting(TEST_COMPONENT_STATE_APP_PACKAGE_NAME,
                        COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP));
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED,
                mPackageManager.getApplicationEnabledSetting(
                        TEST_COMPONENT_STATE_APP_PACKAGE_NAME));

        SystemUtil.runWithShellPermissionIdentity(() ->
                mPackageManager.setApplicationEnabledSetting(TEST_COMPONENT_STATE_APP_PACKAGE_NAME,
                        COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP));
        assertEquals(COMPONENT_ENABLED_STATE_DISABLED,
                mPackageManager.getApplicationEnabledSetting(
                        TEST_COMPONENT_STATE_APP_PACKAGE_NAME));
    }

    @Test
    public void testComponentStateChangedReportedForTwoDifferentStateLauncherActivities() {
        ComponentName firstComponentName = new ComponentName(TEST_COMPONENT_STATE_APP_PACKAGE_NAME,
                FAKE_DEFAULT_ENABLED_LAUNCHER_ACTIVITY_NAME);

        SystemUtil.runWithShellPermissionIdentity(() ->
                mPackageManager.setComponentEnabledSetting(firstComponentName,
                        COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP));
        assertEquals(COMPONENT_ENABLED_STATE_DISABLED,
                mPackageManager.getComponentEnabledSetting(firstComponentName));

        ComponentName secondComponentName = new ComponentName(TEST_COMPONENT_STATE_APP_PACKAGE_NAME,
                FAKE_LAUNCHER_ACTIVITY_NAME);

        SystemUtil.runWithShellPermissionIdentity(() ->
                mPackageManager.setComponentEnabledSetting(secondComponentName,
                        COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP));
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED,
                mPackageManager.getComponentEnabledSetting(secondComponentName));
    }
}
