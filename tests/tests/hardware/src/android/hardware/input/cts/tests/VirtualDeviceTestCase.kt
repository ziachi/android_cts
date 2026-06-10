/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.hardware.input.cts.tests

import android.app.ActivityOptions
import android.companion.virtual.VirtualDeviceManager
import android.graphics.Point
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.server.wm.WindowManagerStateHelper
import android.virtualdevice.cts.common.VirtualDeviceRule
import org.junit.Rule

abstract class VirtualDeviceTestCase : InputTestCase() {
    @get:Rule
    var mRule: VirtualDeviceRule = VirtualDeviceRule.createDefault()

    lateinit var mVirtualDevice: VirtualDeviceManager.VirtualDevice
    lateinit var mVirtualDisplay: VirtualDisplay

    public override fun onBeforeLaunchActivity() {
        mVirtualDevice = mRule.createManagedVirtualDevice()
        mVirtualDisplay = mRule.createManagedVirtualDisplay(
            mVirtualDevice, VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder()
        )!!
        mRule.assumeActivityLaunchSupported(mVirtualDisplay.display.displayId)
    }

    public override fun onSetUp() {
        onSetUpVirtualInputDevice()
        // Wait for any pending transitions
        val windowManagerStateHelper = WindowManagerStateHelper()
        windowManagerStateHelper.waitForAppTransitionIdleOnDisplay(mTestActivity.displayId)
        mInstrumentation.uiAutomation.syncInputTransactions()
    }

    public override fun onTearDown() {
        if (mTestActivity != null) {
            mTestActivity.finish()
        }
    }

    abstract fun onSetUpVirtualInputDevice()

    public override fun getActivityOptions(): Bundle? {
        return ActivityOptions.makeBasic()
            .setLaunchDisplayId(mVirtualDisplay.display.displayId)
            .toBundle()
    }

    fun getActivityCenter(): Point {
        val view = mTestActivity.window.decorView
        val xy = IntArray(2)
        view.getLocationOnScreen(xy)

        return Point(xy[0] + view.width / 2, xy[1] + view.height / 2)
    }
}
