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
package com.android.bedstead.harrier

import android.app.ActivityManager
import android.app.contentsuggestions.ContentSuggestionsManager
import android.app.role.RoleManager
import android.provider.Settings
import com.android.bedstead.harrier.annotations.EnsureDemoMode
import com.android.bedstead.harrier.annotations.EnsureGlobalSettingSet
import com.android.bedstead.harrier.annotations.EnsureNotDemoMode
import com.android.bedstead.harrier.annotations.EnsurePackageNotInstalled
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet
import com.android.bedstead.harrier.annotations.EnsurePropertySet
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn
import com.android.bedstead.harrier.annotations.EnsureSecureSettingSet
import com.android.bedstead.harrier.annotations.EnsureUsingDisplayTheme
import com.android.bedstead.harrier.annotations.EnsureUsingScreenOrientation
import com.android.bedstead.harrier.annotations.EnsureWifiDisabled
import com.android.bedstead.harrier.annotations.EnsureWifiEnabled
import com.android.bedstead.harrier.annotations.EnsureWillNotTakeQuickBugReports
import com.android.bedstead.harrier.annotations.EnsureWillTakeQuickBugReports
import com.android.bedstead.harrier.annotations.RequireAospBuild
import com.android.bedstead.harrier.annotations.RequireCnGmsBuild
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.RequireGmsBuild
import com.android.bedstead.harrier.annotations.RequireHasDefaultBrowser
import com.android.bedstead.harrier.annotations.RequireInstantApp
import com.android.bedstead.harrier.annotations.RequireLowRamDevice
import com.android.bedstead.harrier.annotations.RequireMinimumAdvertisedRamDevice
import com.android.bedstead.harrier.annotations.RequireNotCnGmsBuild
import com.android.bedstead.harrier.annotations.RequireNotInstantApp
import com.android.bedstead.harrier.annotations.RequireNotLowRamDevice
import com.android.bedstead.harrier.annotations.RequirePackageInstalled
import com.android.bedstead.harrier.annotations.RequirePackageNotInstalled
import com.android.bedstead.harrier.annotations.RequireResourcesBooleanValue
import com.android.bedstead.harrier.annotations.RequireResourcesIntegerValue
import com.android.bedstead.harrier.annotations.RequireSystemServiceAvailable
import com.android.bedstead.harrier.annotations.TestTag
import com.android.bedstead.harrier.annotations.parameterized.IncludeDarkMode
import com.android.bedstead.harrier.annotations.parameterized.IncludeLandscapeOrientation
import com.android.bedstead.harrier.annotations.parameterized.IncludeLightMode
import com.android.bedstead.harrier.annotations.parameterized.IncludePortraitOrientation
import com.android.bedstead.nene.TestApis.bugReports
import com.android.bedstead.nene.TestApis.context
import com.android.bedstead.nene.TestApis.device
import com.android.bedstead.nene.TestApis.packages
import com.android.bedstead.nene.TestApis.properties
import com.android.bedstead.nene.TestApis.resources
import com.android.bedstead.nene.TestApis.roles
import com.android.bedstead.nene.TestApis.settings
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.TestApis.wifi
import com.android.bedstead.nene.display.Display.getDisplayTheme
import com.android.bedstead.nene.display.Display.getScreenOrientation
import com.android.bedstead.nene.display.DisplayProperties
import com.android.bedstead.nene.utils.Tags.hasTag
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class MainAnnotationExecutorTest {

    @Test
    @RequirePackageInstalled(value = RequireAospBuild.GMS_CORE_PACKAGE, onUser = UserType.ANY)
    fun requirePackageInstalledAnnotation_anyUser_packageIsInstalled() {
        assertThat(
            packages().find(RequireAospBuild.GMS_CORE_PACKAGE).installedOnUsers()
        ).isNotEmpty()
    }

    @Test
    @RequirePackageInstalled(RequireAospBuild.GMS_CORE_PACKAGE)
    fun requirePackageInstalledAnnotation_currentUser_packageIsInstalled() {
        assertThat(
            packages().find(RequireAospBuild.GMS_CORE_PACKAGE).installedOnUser()
        ).isTrue()
    }

    @Test
    @RequirePackageNotInstalled(value = RequireAospBuild.GMS_CORE_PACKAGE, onUser = UserType.ANY)
    fun requirePackageNotInstalledAnnotation_anyUser_packageIsNotInstalled() {
        assertThat(
            packages().find(RequireAospBuild.GMS_CORE_PACKAGE).installedOnUsers()
        ).isEmpty()
    }

    @Test
    @RequirePackageNotInstalled(RequireAospBuild.GMS_CORE_PACKAGE)
    fun requirePackageNotInstalledAnnotation_currentUser_packageIsNotInstalled() {
        val pkg = packages().find(RequireAospBuild.GMS_CORE_PACKAGE)

        assertThat(pkg.installedOnUser()).isFalse()
    }

    @Test
    @EnsurePackageNotInstalled(value = RequireAospBuild.GMS_CORE_PACKAGE, onUser = UserType.ANY)
    // TODO(b/367300481): Restore this with a package which can be uninstalled
    @Ignore("this package can't be uninstalled")
    fun ensurePackageNotInstalledAnnotation_anyUser_packageIsNotInstalled() {
        assertThat(
            packages().find(RequireAospBuild.GMS_CORE_PACKAGE).installedOnUsers()
        ).isEmpty()
    }

    @Test
    @EnsurePackageNotInstalled(RequireAospBuild.GMS_CORE_PACKAGE)
    // TODO(b/367300481): Restore this with a package which can be uninstalled
    @Ignore("this package can't be uninstalled")
    fun ensurePackageNotInstalledAnnotation_currentUser_packageIsNotInstalled() {
        val pkg = packages().find(RequireAospBuild.GMS_CORE_PACKAGE)

        assertThat(pkg.installedOnUser()).isFalse()
    }

    @Test
    @RequireAospBuild
    fun requireAospBuildAnnotation_isRunningOnAospBuild() {
        assertThat(packages().find(RequireAospBuild.GMS_CORE_PACKAGE).exists()).isFalse()
    }

    @Test
    @RequireGmsBuild
    fun requireGmsBuildAnnotation_isRunningOnGmsbuild() {
        assertThat(packages().find(RequireAospBuild.GMS_CORE_PACKAGE).exists()).isTrue()
    }

    @Test
    @RequireCnGmsBuild
    fun requireCnGmsBuildAnnotation_isRunningOnCnGmsBuild() {
        assertThat(
            packages().features()
        ).contains(RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE)
    }

    @Test
    @RequireNotCnGmsBuild
    fun requireNotCnGmsBuildAnnotation_isNotRunningOnCnGmsBuild() {
        assertThat(
            packages().features()
        ).doesNotContain(RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE)
    }

    @Test
    @RequireFeature(RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE)
    fun requireHasFeatureAnnotation_doesNotHaveFeature() {
        assertThat(
            packages().features()
        ).contains(RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE)
    }

    @Test
    @RequireDoesNotHaveFeature(RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE)
    fun requireDoesNotHaveFeatureAnnotation_doesNotHaveFeature() {
        assertThat(
            packages().features()
        ).doesNotContain(RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE)
    }

    @Test
    @RequireLowRamDevice(reason = "Test")
    fun requireLowRamDeviceAnnotation_isLowRamDevice() {
        assertThat(
            context()
                .instrumentedContext()
                .getSystemService(ActivityManager::class.java)
                .isLowRamDevice
        ).isTrue()
    }

    @Test
    @RequireMinimumAdvertisedRamDevice(ramDeviceSize = 4_000_000_000L, reason = "Test")
    fun requireMinimumAdvertisedRamDeviceAnnotation_isMinimumAdvertisedRamDevice() {
        val memoryInfo = ActivityManager.MemoryInfo()
        context().instrumentedContext()
            .getSystemService(ActivityManager::class.java)!!
            .getMemoryInfo(memoryInfo)
        assertThat(memoryInfo.advertisedMem >= 4_000_000_000L).isTrue()
    }

    @Test
    @RequireNotLowRamDevice(reason = "Test")
    fun requireNotLowRamDeviceAnnotation_isNotLowRamDevice() {
        assertThat(
            context()
                .instrumentedContext()
                .getSystemService(ActivityManager::class.java)
                .isLowRamDevice
        ).isFalse()
    }

    @Test
    @TestTag("TestTag")
    fun testTagAnnotation_testTagIsSet() {
        assertThat(hasTag("TestTag")).isTrue()
    }

    @Test
    @EnsureScreenIsOn
    fun ensureScreenIsOnAnnotation_screenIsOn() {
        assertThat(device().isScreenOn).isTrue()
    }

    @Test
    @EnsurePasswordNotSet
    fun requirePasswordNotSetAnnotation_passwordNotSet() {
        assertThat(users().instrumented().hasLockCredential()).isFalse()
    }

    // TODO(b/300218365): Test that settings are returned to their original values in teardown.

    @EnsureSecureSettingSet(key = "testSecureSetting", value = "testValue")
    @Test
    fun ensureSecureSettingSetAnnotation_secureSettingIsSet() {
        assertThat(
            settings().secure().getString("testSecureSetting")
        ).isEqualTo("testValue")
    }

    @EnsureGlobalSettingSet(key = "testGlobalSetting", value = "testValue")
    @Test
    fun ensureGlobalSettingSetAnnotation_globalSettingIsSet() {
        assertThat(
            settings().global().getString("testGlobalSetting")
        ).isEqualTo("testValue")
    }

    @EnsureDemoMode
    @Test
    fun ensureDemoModeAnnotation_deviceIsInDemoMode() {
        assertThat(
            settings().global().getInt(Settings.Global.DEVICE_DEMO_MODE)
        ).isEqualTo(1)
    }

    @EnsureNotDemoMode
    @Test
    fun ensureNotDemoModeAnnotation_deviceIsNotInDemoMode() {
        assertThat(
            settings().global().getInt(Settings.Global.DEVICE_DEMO_MODE)
        ).isEqualTo(0)
    }

    @RequireInstantApp(reason = "Testing RequireInstantApp")
    @Test
    fun requireInstantAppAnnotation_isInstantApp() {
        assertThat(packages().instrumented().isInstantApp).isTrue()
    }

    @RequireNotInstantApp(reason = "Testing RequireNotInstantApp")
    @Test
    fun requireNotInstantAppAnnotation_isNotInstantApp() {
        assertThat(packages().instrumented().isInstantApp).isFalse()
    }

    @EnsureWifiEnabled
    @Test
    fun ensureWifiEnabledAnnotation_wifiIsEnabled() {
        assertThat(wifi().isEnabled).isTrue()
    }

    @EnsureWifiDisabled
    @Test
    fun ensureWifiDisabledAnnotation_wifiIsNotEnabled() {
        assertThat(wifi().isEnabled).isFalse()
    }

    @RequireSystemServiceAvailable(ContentSuggestionsManager::class)
    @Test
    fun requireSystemServiceAvailable_systemServiceIsAvailable() {
        assertThat(
            context().instrumentedContext().getSystemService(ContentSuggestionsManager::class.java)
        ).isNotNull()
    }

    @Test
    @RequireHasDefaultBrowser
    fun requireHasDefaultBrowser_roleBrowserIsNotEmpty() {
        assertThat(roles().getRoleHolders(RoleManager.ROLE_BROWSER)).isNotEmpty()
    }

    @Test
    @EnsurePropertySet(key = "dumpstate.key", value = "value")
    fun ensurePropertySetAnnotation_propertyIsSet() {
        assertThat(properties().get("dumpstate.key")).isEqualTo("value")
    }

    @Test
    @EnsureWillTakeQuickBugReports
    fun ensureWillTakeQuickBugReportsAnnotation_willTakeQuickBugReports() {
        assertThat(bugReports().willTakeQuickBugReports()).isTrue()
    }

    @Test
    @EnsureWillNotTakeQuickBugReports
    fun ensureWillNotTakeQuickBugReportsAnnotation_willNotTakeQuickBugReports() {
        assertThat(bugReports().willTakeQuickBugReports()).isFalse()
    }

    @RequireResourcesBooleanValue(configName = "config_enableMultiUserUI", requiredValue = true)
    @Test
    fun requireResourcesBooleanValueIsTrue_resourceValueIsTrue() {
        assertThat(
            resources().system().getBoolean("config_enableMultiUserUI")
        ).isTrue()
    }

    @RequireResourcesBooleanValue(configName = "config_enableMultiUserUI", requiredValue = false)
    @Test
    fun requireResourcesBooleanValueIsFalse_resourceValueIsFalse() {
        assertThat(
            resources().system().getBoolean("config_enableMultiUserUI")
        ).isFalse()
    }

    @RequireResourcesIntegerValue(configName = "config_hsumBootStrategy", requiredValue = 0)
    @Test
    fun requireResourcesIntegerValueIsTrue_resourceValueIs0() {
        assertThat(
            resources().system().getInteger("config_hsumBootStrategy")
        ).isEqualTo(0)
    }

    @RequireResourcesIntegerValue(configName = "config_hsumBootStrategy", requiredValue = 1)
    @Test
    fun requireResourcesIntegerValueIsTrue_resourceValueIs1() {
        assertThat(
            resources().system().getInteger("config_hsumBootStrategy")
        ).isEqualTo(1)
    }

    @Test
    @EnsureUsingScreenOrientation(orientation = DisplayProperties.ScreenOrientation.LANDSCAPE)
    fun ensureUsingScreenOrientation_landscape_orientationIsSet() {
        assertThat(getScreenOrientation()).isEqualTo(DisplayProperties.ScreenOrientation.LANDSCAPE)
    }

    @Test
    @EnsureUsingScreenOrientation(orientation = DisplayProperties.ScreenOrientation.PORTRAIT)
    fun ensureUsingScreenOrientation_portrait_orientationIsSet() {
        assertThat(getScreenOrientation()).isEqualTo(DisplayProperties.ScreenOrientation.PORTRAIT)
    }

    @Test
    @EnsureUsingDisplayTheme(theme = DisplayProperties.Theme.DARK)
    fun ensureUsingDisplayTheme_setDark_themeIsSet() {
        assertThat(getDisplayTheme()).isEqualTo(DisplayProperties.Theme.DARK)
    }

    @Test
    @EnsureUsingDisplayTheme(theme = DisplayProperties.Theme.LIGHT)
    fun ensureUsingDisplayTheme_setLight_themeIsSet() {
        assertThat(getDisplayTheme()).isEqualTo(DisplayProperties.Theme.LIGHT)
    }

    @Test
    @IncludeLandscapeOrientation
    fun includeRunOnLandscapeOrientationDevice_orientationIsSet() {
        assertThat(getScreenOrientation()).isEqualTo(DisplayProperties.ScreenOrientation.LANDSCAPE)
    }

    @Test
    @IncludePortraitOrientation
    fun includeRunOnPortraitOrientationDevice_orientationIsSet() {
        assertThat(getScreenOrientation()).isEqualTo(DisplayProperties.ScreenOrientation.PORTRAIT)
    }

    @Test
    @IncludeDarkMode
    fun includeRunOnDarkModeDevice_themeIsSet() {
        assertThat(getDisplayTheme()).isEqualTo(DisplayProperties.Theme.DARK)
    }

    @Test
    @IncludeLightMode
    fun includeRunOnLightModeDevice_themeIsSet() {
        assertThat(getDisplayTheme()).isEqualTo(DisplayProperties.Theme.LIGHT)
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }
}
