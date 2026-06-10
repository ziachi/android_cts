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

package com.google.android.interactive.steps.qrscan

import androidx.test.uiautomator.UiObject2
import com.android.bedstead.nene.TestApis
import com.android.interactive.Automation
import com.android.interactive.Nothing
import com.android.interactive.annotations.AutomationFor
import com.google.android.interactive.helpers.QuickSettingsHelper

/**
 * Automation for LaunchQrScannerFromQuickSettingsStep.
 *
 * This launches the QR code scanner from the Quick settings UI using UI automation.
 */
@AutomationFor("com.google.android.interactive.steps.qrscan.LaunchQrScannerFromQuickSettingsStep")
class LaunchQrScannerFromQuickSettingsStepAutomation : Automation<Nothing> {

    override fun automate(): Nothing {
        var tile: UiObject2? = findQrTile(CURRENT_TILE_NAME)
        if (tile == null) {
            tile = findQrTile(OLD_TILE_NAME)
        }
        if (tile == null) {
            TestApis.ui().device().pressHome()
            throw IllegalStateException(
                "QR code scanner tile not found among active tiles in Quick settings. " +
                        "Try manually by searching the QR code scanner in extra tiles and " +
                        "add it to active Quick setting tiles."
            )
        }
        // Launch the tile.
        tile.click()

        return Nothing.NOTHING
    }

    private fun findQrTile(label: String): UiObject2? {
        // Press Home to close quick settings UI (if already open)
        TestApis.ui().device().pressHome()

        // Find the Quick setting tile with given label.
        return QuickSettingsHelper.findTileWithLabel(label)
    }

    private companion object {
        const val OLD_TILE_NAME = "Scan QR code"
        const val CURRENT_TILE_NAME = "QR code scanner"
    }
}
