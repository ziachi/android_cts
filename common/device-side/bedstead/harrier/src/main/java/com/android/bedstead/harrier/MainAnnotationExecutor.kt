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

import com.android.bedstead.harrier.annotations.EnsureGlobalSettingSet
import com.android.bedstead.harrier.annotations.EnsureInstrumented
import com.android.bedstead.harrier.annotations.EnsureNoPackageRespondsToIntent
import com.android.bedstead.harrier.annotations.EnsurePackageNotInstalled
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet
import com.android.bedstead.harrier.annotations.EnsurePasswordSet
import com.android.bedstead.harrier.annotations.EnsurePolicyOperationUnsafe
import com.android.bedstead.harrier.annotations.EnsurePropertySet
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn
import com.android.bedstead.harrier.annotations.EnsureSecureSettingSet
import com.android.bedstead.harrier.annotations.EnsureUnlocked
import com.android.bedstead.harrier.annotations.EnsureUsingDisplayTheme
import com.android.bedstead.harrier.annotations.EnsureUsingScreenOrientation
import com.android.bedstead.harrier.annotations.EnsureWifiDisabled
import com.android.bedstead.harrier.annotations.EnsureWifiEnabled
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature
import com.android.bedstead.harrier.annotations.RequireFactoryResetProtectionPolicySupported
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.RequireHasDefaultBrowser
import com.android.bedstead.harrier.annotations.RequireInstantApp
import com.android.bedstead.harrier.annotations.RequireLowRamDevice
import com.android.bedstead.harrier.annotations.RequireMinimumAdvertisedRamDevice
import com.android.bedstead.harrier.annotations.RequireNoPackageRespondsToIntent
import com.android.bedstead.harrier.annotations.RequireNotInstantApp
import com.android.bedstead.harrier.annotations.RequireNotLowRamDevice
import com.android.bedstead.harrier.annotations.RequirePackageInstalled
import com.android.bedstead.harrier.annotations.RequirePackageNotInstalled
import com.android.bedstead.harrier.annotations.RequirePackageRespondsToIntent
import com.android.bedstead.harrier.annotations.RequireQuickSettingsSupport
import com.android.bedstead.harrier.annotations.RequireResourcesBooleanValue
import com.android.bedstead.harrier.annotations.RequireResourcesIntegerValue
import com.android.bedstead.harrier.annotations.RequireStorageEncryptionSupported
import com.android.bedstead.harrier.annotations.RequireStorageEncryptionUnsupported
import com.android.bedstead.harrier.annotations.RequireSystemServiceAvailable
import com.android.bedstead.harrier.annotations.RequireTargetSdkVersion
import com.android.bedstead.harrier.annotations.RequireTelephonySupport
import com.android.bedstead.harrier.annotations.RequireUsbDataSignalingCanBeDisabled
import com.android.bedstead.harrier.annotations.TestTag
import com.android.bedstead.harrier.components.DisplayThemeComponent
import com.android.bedstead.harrier.components.GlobalSettingsComponent
import com.android.bedstead.harrier.components.InstrumentationComponent
import com.android.bedstead.harrier.components.PolicyOperationUnsafeComponent
import com.android.bedstead.harrier.components.PropertiesComponent
import com.android.bedstead.harrier.components.ScreenOrientationComponent
import com.android.bedstead.harrier.components.SecureSettingsComponent
import com.android.bedstead.harrier.components.UserPasswordComponent
import com.android.bedstead.harrier.components.UserTypeResolver
import com.android.bedstead.harrier.components.WifiComponent

/**
 * [AnnotationExecutor] for annotations that don't belong to specific modules
 */
@Suppress("unused")
class MainAnnotationExecutor(locator: BedsteadServiceLocator) : AnnotationExecutor {

    private val userPasswordComponent: UserPasswordComponent by locator
    private val wifiComponent: WifiComponent by locator
    private val userTypeResolver: UserTypeResolver by locator
    private val globalSettingsComponent: GlobalSettingsComponent by locator
    private val secureSettingsComponent: SecureSettingsComponent by locator
    private val propertiesComponent: PropertiesComponent by locator
    private val displayThemeComponent: DisplayThemeComponent by locator
    private val screenOrientationComponent: ScreenOrientationComponent by locator
    private val policyOperationUnsafeComponent: PolicyOperationUnsafeComponent by locator
    private val instrumentationComponent: InstrumentationComponent by locator

    override fun applyAnnotation(annotation: Annotation): Unit = annotation.run {
        when (this) {
            is RequireLowRamDevice -> logic()
            is RequireMinimumAdvertisedRamDevice -> logic()
            is RequireNotLowRamDevice -> logic()
            is EnsureScreenIsOn -> logic()
            is EnsureUnlocked -> logic()
            is RequireFeature -> logic()
            is RequireDoesNotHaveFeature -> logic()
            is RequireStorageEncryptionSupported -> logic()
            is RequireStorageEncryptionUnsupported -> logic()
            is RequireFactoryResetProtectionPolicySupported -> logic()
            is RequireResourcesBooleanValue -> logic()
            is RequireResourcesIntegerValue -> logic()
            is RequireUsbDataSignalingCanBeDisabled -> logic()
            is RequireInstantApp -> logic()
            is RequireNotInstantApp -> logic()
            is RequireSystemServiceAvailable -> logic()
            is RequireTargetSdkVersion -> logic()
            is RequirePackageInstalled -> logic(userTypeResolver)
            is RequirePackageNotInstalled -> logic(userTypeResolver)
            is EnsurePackageNotInstalled -> logic(userTypeResolver)
            is RequireQuickSettingsSupport -> logic()
            is RequireHasDefaultBrowser -> logic(userTypeResolver)
            is RequireTelephonySupport -> logic()
            is EnsureNoPackageRespondsToIntent -> logic(userTypeResolver)
            is RequireNoPackageRespondsToIntent -> logic(userTypeResolver)
            is RequirePackageRespondsToIntent -> logic(userTypeResolver)
            is EnsureWifiEnabled -> wifiComponent.ensureWifiEnabled()
            is EnsureWifiDisabled -> wifiComponent.ensureWifiDisabled()
            is EnsureGlobalSettingSet -> globalSettingsComponent.ensureGlobalSettingSet(key, value)
            is EnsurePropertySet -> propertiesComponent.ensurePropertySet(key, value)
            is EnsureUsingDisplayTheme -> displayThemeComponent.ensureUsingDisplayTheme(theme)
            is EnsurePolicyOperationUnsafe ->
                policyOperationUnsafeComponent.ensurePolicyOperationUnsafe(operation, reason)

            is EnsureSecureSettingSet ->
                secureSettingsComponent.ensureSecureSettingSet(onUser, key, value)

            is EnsureUsingScreenOrientation ->
                screenOrientationComponent.ensureUsingScreenOrientation(orientation)

            is EnsurePasswordSet -> userPasswordComponent.ensurePasswordSet(forUser, password)
            is EnsurePasswordNotSet -> userPasswordComponent.ensurePasswordNotSet(forUser)
            is TestTag -> logic()

            is EnsureInstrumented -> instrumentationComponent.ensureInstrumented(this)
        }
    }
}
