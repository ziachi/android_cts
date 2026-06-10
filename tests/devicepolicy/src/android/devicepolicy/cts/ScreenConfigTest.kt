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

package android.devicepolicy.cts

import android.content.pm.PackageManager.FEATURE_DEVICE_ADMIN
import android.content.pm.PackageManager.FEATURE_MANAGED_USERS
import android.os.UserManager
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
import android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest
import com.android.bedstead.enterprise.dpc
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.policies.DisallowScreenConfigRestrictions
import com.android.bedstead.harrier.policies.SetSystemSetting
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.utils.Assert.assertThrows
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Tests for {@link DevicePolicyManager#setSystemSetting},
 * {@link UserManager#DISALLOW_CONFIG_SCREEN_TIMEOUT} and
 * {@link UserManager#DISALLOW_CONFIG_CONFIG_BRIGHTNESS}
 */
@RunWith(BedsteadJUnit4::class)
@RequireFeature(FEATURE_DEVICE_ADMIN)
@RequireFeature(FEATURE_MANAGED_USERS)
class ScreenConfigTest {

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setSystemSetting"])
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = [SetSystemSetting::class])
    fun setScreenBrightness_succeeds() {
        val mode = TestApis.settings().system().getInt(
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        )
        val brightness = TestApis.settings().system().getInt(Settings.System.SCREEN_BRIGHTNESS, 100)
        try {
            TestApis.settings().system().putInt(
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            deviceState.dpc().devicePolicyManager().setSystemSetting(
                deviceState.dpc().componentName(),
                Settings.System.SCREEN_BRIGHTNESS,
                "200"
            )

            assertThat(TestApis.settings().system().getInt(Settings.System.SCREEN_BRIGHTNESS, -1))
                    .isEqualTo(200)
        } finally {
            TestApis.settings().system().putInt(Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
            TestApis.settings().system().putInt(Settings.System.SCREEN_BRIGHTNESS, brightness)
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setSystemSettings"])
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = [SetSystemSetting::class])
    fun setScreenBrightnessModeToAutomatic_succeeds() {
        val mode = TestApis.settings().system().getInt(
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        )
        try {
            deviceState.dpc().devicePolicyManager().setSystemSetting(
                deviceState.dpc().componentName(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                SCREEN_BRIGHTNESS_MODE_AUTOMATIC.toString()
            )

            assertThat(TestApis.settings().system().getInt(
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                -1
            ))
                    .isEqualTo(SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        } finally {
            TestApis.settings().system().putInt(Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
        }
    }
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setSystemSettings"])
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = [SetSystemSetting::class])
    fun setScreenBrightnessModeToManual_succeeds() {
        val mode = TestApis.settings().system().getInt(
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        )
        try {
            deviceState.dpc().devicePolicyManager().setSystemSetting(
                deviceState.dpc().componentName(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                SCREEN_BRIGHTNESS_MODE_MANUAL.toString()
            )

            assertThat(TestApis.settings().system().getInt(
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                -1
            ))
                    .isEqualTo(SCREEN_BRIGHTNESS_MODE_MANUAL)
        } finally {
            TestApis.settings().system().putInt(Settings.System.SCREEN_BRIGHTNESS_MODE, mode)
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setSystemSettings"])
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = [SetSystemSetting::class])
    fun setScreenOffTimeout_succeeds() {
        val timeout = TestApis.settings().system().getInt(
            Settings.System.SCREEN_OFF_TIMEOUT,
            0
        )
        try {
            deviceState.dpc().devicePolicyManager().setSystemSetting(
                deviceState.dpc().componentName(),
                Settings.System.SCREEN_OFF_TIMEOUT,
                "43000"
            )

            assertThat(TestApis.settings().system().getInt(Settings.System.SCREEN_OFF_TIMEOUT, -1))
                    .isEqualTo(43_000)
        } finally {
            TestApis.settings().system().putInt(Settings.System.SCREEN_OFF_TIMEOUT, timeout)
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setSystemSettings"])
    @CannotSetPolicyTest(policy = [SetSystemSetting::class], includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    fun setSystemSetting_notAllowed_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().setSystemSetting(
                deviceState.dpc().componentName(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                SCREEN_BRIGHTNESS_MODE_AUTOMATIC.toString()
            )
        }
    }

    @ApiTest(apis = ["android.os.UserManager#DISALLOW_CONFIG_BRIGHTNESS"])
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = [DisallowScreenConfigRestrictions::class])
    fun disallowConfigBrightness_canSetRestriction() {
        try {
            deviceState.dpc().devicePolicyManager().addUserRestriction(
                deviceState.dpc().componentName(),
                UserManager.DISALLOW_CONFIG_BRIGHTNESS
            )
            assertThat(
                TestApis.devicePolicy().userRestrictions().isSet(
                    UserManager.DISALLOW_CONFIG_BRIGHTNESS
                )
            ).isTrue()
        } finally {
            deviceState.dpc().devicePolicyManager().clearUserRestriction(
                deviceState.dpc().componentName(),
                UserManager.DISALLOW_CONFIG_BRIGHTNESS
            )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#DISALLOW_CONFIG_BRIGHTNESS"])
    @CannotSetPolicyTest(policy = [DisallowScreenConfigRestrictions::class])
    @Postsubmit(reason = "new test")
    fun setDisallowConfigBrightness_notAllowed_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().addUserRestriction(
                deviceState.dpc().componentName(),
                UserManager.DISALLOW_CONFIG_BRIGHTNESS
            )
        }
    }

    @ApiTest(apis = ["android.os.UserManager#DISALLOW_CONFIG_SCREEN_TIMEOUT"])
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = [DisallowScreenConfigRestrictions::class])
    fun disallowConfigScreenTimeout_canSetRestriction() {
        try {
            deviceState.dpc().devicePolicyManager().addUserRestriction(
                deviceState.dpc().componentName(),
                UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT
            )
            assertThat(
                TestApis.devicePolicy().userRestrictions()
                    .isSet(UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT)
            ).isTrue()
        } finally {
            deviceState.dpc().devicePolicyManager().clearUserRestriction(
                deviceState.dpc().componentName(),
                UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT
            )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#DISALLOW_CONFIG_SCREEN_TIMEOUT"])
    @CannotSetPolicyTest(policy = [DisallowScreenConfigRestrictions::class])
    @Postsubmit(reason = "new test")
    fun setDisallowConfigScreenTimeout_notAllowed_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().addUserRestriction(
                deviceState.dpc().componentName(),
                UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT
            )
        }
    }

    companion object {
        @JvmField
        @ClassRule
        val deviceState = DeviceState()
    }
    @JvmField
    @Rule
    val mCheckFlagsRule = RuleChain
            .outerRule(DeviceFlagsValueProvider.createCheckFlagsRule())
            .around(deviceState)
}
