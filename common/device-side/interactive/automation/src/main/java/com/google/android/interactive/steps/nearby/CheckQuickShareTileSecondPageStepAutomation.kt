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

package com.google.android.interactive.steps.nearby

import android.platform.uiautomatorhelpers.DeviceHelpers
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import com.android.bedstead.nene.TestApis
import com.android.interactive.Automation
import com.android.interactive.annotations.AutomationFor
import java.time.Duration

@AutomationFor("com.google.android.interactive.steps.nearby.CheckQuickShareTileSecondPageStep")
class CheckQuickShareTileSecondPageStepAutomation : Automation<Boolean> {
    override fun automate(): Boolean {
        // Wait 6 seconds for the tile to appear.
        return try {
            TestApis.ui().device().openQuickSettings()
            TestApis.ui().device().waitForIdle()
            val qsPager =
                UiScrollable(UiSelector().resourceId("com.android.systemui:id/qs_pager"))
                    .setAsHorizontalList()
            qsPager.scrollForward()
            DeviceHelpers.waitForObj(By.text("Quick Share"), Duration.ofMillis(TIMEOUT))
            true
        } catch (e: IllegalStateException) {
            false
        }  finally {
            TestApis.ui().device().pressHome()
        }
    }

    companion object {
        // The timeout in milliseconds.
        private const val TIMEOUT = 6000L
    }
}
