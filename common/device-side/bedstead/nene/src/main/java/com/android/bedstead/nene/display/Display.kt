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

package com.android.bedstead.nene.display

import android.app.UiModeManager
import android.view.Surface
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.bedstead.nene.TestApis
import com.android.bedstead.permissions.CommonPermissions.MODIFY_DAY_NIGHT_MODE

/**
 * Access Test APIs related to device display settings.
 */
fun TestApis.display() = Display

/**
 * Helper methods related to the display settings of device.
 */
object Display {

    private const val USER_ROTATION_KEY = "user_rotation"

    /**
     * Enables and disables the dark mode of the device depending on [displayTheme].
     */
    fun setDisplayTheme(displayTheme: DisplayProperties.Theme) {
        TestApis.permissions().withPermission(MODIFY_DAY_NIGHT_MODE).use {
            TestApis.context().androidContextAsUser(TestApis.users().system()).getSystemService(
                UiModeManager::class.java)?.nightMode = getUiModeNightValue(displayTheme)
        }
    }

    /**
     * Gets the device display theme. The values could be one of [DisplayProperties.Theme].
     */
    fun getDisplayTheme(): DisplayProperties.Theme {
        TestApis.permissions().withPermission(MODIFY_DAY_NIGHT_MODE).use {
            val uiMode = TestApis.context().androidContextAsUser(
                TestApis.users().system()).getSystemService(
                UiModeManager::class.java)?.nightMode ?: error("Unable to fetch nightMode value")
            return when (uiMode) {
                UiModeManager.MODE_NIGHT_NO -> DisplayProperties.Theme.LIGHT
                UiModeManager.MODE_NIGHT_YES -> DisplayProperties.Theme.DARK
                else -> throw Exception("Unsupported display theme value $uiMode")
            }
        }
    }

    /**
     * Sets the screen orientation of the device.
     */
    fun setScreenOrientation(orientation: DisplayProperties.ScreenOrientation) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        when (orientation) {
            DisplayProperties.ScreenOrientation.PORTRAIT -> device.setOrientationPortrait()
            DisplayProperties.ScreenOrientation.LANDSCAPE -> device.setOrientationLandscape()
        }
    }

    /**
     * Gets the screen orientation of the device.
     */
    fun getScreenOrientation(): DisplayProperties.ScreenOrientation {
        val userRotationValue = TestApis.settings().system().getInt(USER_ROTATION_KEY)

        return when (userRotationValue) {
            Surface.ROTATION_0 -> DisplayProperties.ScreenOrientation.PORTRAIT
            Surface.ROTATION_90 -> DisplayProperties.ScreenOrientation.LANDSCAPE
            else -> error("Unsupported user_rotation value $userRotationValue")
        }
    }

    private fun getUiModeNightValue(displayTheme: DisplayProperties.Theme): Int {
        return when (displayTheme) {
            DisplayProperties.Theme.DARK -> UiModeManager.MODE_NIGHT_YES
            DisplayProperties.Theme.LIGHT -> UiModeManager.MODE_NIGHT_NO
        }
    }
}
