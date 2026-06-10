/*
 * Copyright 2024 The Android Open Source Project
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

import android.companion.virtualdevice.flags.Flags
import android.hardware.input.VirtualRotaryEncoder
import android.hardware.input.VirtualRotaryEncoderScrollEvent
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.InputEvent
import androidx.test.filters.SmallTest
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Test
import org.junit.runner.RunWith

@RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_ROTARY)
@SmallTest
@RunWith(JUnitParamsRunner::class)
class VirtualRotaryEncoderTest : VirtualDeviceTestCase() {
    private lateinit var mVirtualRotary: VirtualRotaryEncoder

    override fun onSetUpVirtualInputDevice() {
        mVirtualRotary = VirtualInputDeviceCreator.createAndPrepareRotary(
            mVirtualDevice,
            DEVICE_NAME, mVirtualDisplay.display
        ).device
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_ROTARY)
    @RequiresFlagsDisabled(Flags.FLAG_HIGH_RESOLUTION_SCROLL)
    @Test
    @Parameters(method = "allScrollValues")
    fun sendScrollEvent(scrollAmount: Float) {
        verifyScrollEvent(scrollAmount)
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_ROTARY, Flags.FLAG_HIGH_RESOLUTION_SCROLL)
    @Test
    @Parameters(method = "allHighResScrollValues")
    fun sendHighResScrollEvent(scrollAmount: Float) {
        verifyScrollEvent(scrollAmount)
    }

    private fun verifyScrollEvent(scrollAmount: Float) {
        mVirtualRotary.sendScrollEvent(
            VirtualRotaryEncoderScrollEvent.Builder()
                .setScrollAmount(scrollAmount)
                .build()
        )
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createRotaryEvent(scrollAmount)
            )
        )
    }

    private fun allScrollValues(): Array<Float> = arrayOf(
        -1f,
        1f,
    )

    private fun allHighResScrollValues(): Array<Float> = arrayOf(
        0.1f,
        0.5f,
        1f,
        -0.1f,
        -0.5f,
        -1f,
    )

    companion object {
        private const val DEVICE_NAME = "CtsVirtualRotaryEncoderTestDevice"
    }
}
