/*
 * Copyright 2023 The Android Open Source Project
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
package android.hardware.input.cts.virtualcreators

import android.companion.virtual.VirtualDeviceManager
import android.hardware.input.InputManager
import android.hardware.input.VirtualDpad
import android.hardware.input.VirtualDpadConfig
import android.hardware.input.VirtualKeyboard
import android.hardware.input.VirtualKeyboardConfig
import android.hardware.input.VirtualMouse
import android.hardware.input.VirtualMouseConfig
import android.hardware.input.VirtualNavigationTouchpad
import android.hardware.input.VirtualNavigationTouchpadConfig
import android.hardware.input.VirtualRotaryEncoder
import android.hardware.input.VirtualRotaryEncoderConfig
import android.hardware.input.VirtualStylus
import android.hardware.input.VirtualStylusConfig
import android.hardware.input.VirtualTouchscreen
import android.hardware.input.VirtualTouchscreenConfig
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * Static utilities for creating virtual input devices.
 */
object VirtualInputDeviceCreator {
    const val PRODUCT_ID: Int = 1
    const val VENDOR_ID: Int = 1

    private fun <T : Closeable?> prepareInputDevice(
        deviceCreator: Supplier<T>
    ): InputDeviceHolder<T> {
        return prepareInputDevice(deviceCreator, languageTag = null, layoutType = null)
    }

    private fun <T : Closeable?> prepareInputDevice(
        deviceCreator: Supplier<T>,
        languageTag: String?,
        layoutType: String?
    ): InputDeviceHolder<T> {
        val inputManager: InputManager =
            InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSystemService(InputManager::class.java)
        try {
            InputDeviceAddedWaiter(inputManager, languageTag, layoutType).use { waiter ->
                return InputDeviceHolder(deviceCreator.get(), waiter.await())
            }
        } catch (e: InterruptedException) {
            throw AssertionError("Virtual input device setup was interrupted", e)
        }
    }

    fun createAndPrepareTouchscreen(
        virtualDevice: VirtualDeviceManager.VirtualDevice,
        name: String,
        display: Display
    ): InputDeviceHolder<VirtualTouchscreen> = prepareInputDevice {
        virtualDevice.createVirtualTouchscreen(
            VirtualTouchscreenConfig.Builder(
                display.mode.physicalWidth,
                display.mode.physicalHeight
            )
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setInputDeviceName(name)
                .setAssociatedDisplayId(display.displayId)
                .build()
        )
    }

    fun createAndPrepareStylus(
        virtualDevice: VirtualDeviceManager.VirtualDevice,
        name: String,
        display: Display
    ): InputDeviceHolder<VirtualStylus> = prepareInputDevice {
        virtualDevice.createVirtualStylus(
            VirtualStylusConfig.Builder(
                display.mode.physicalWidth,
                display.mode.physicalHeight
            )
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setInputDeviceName(name)
                .setAssociatedDisplayId(display.displayId)
                .build()
        )
    }

    fun createAndPrepareMouse(
        virtualDevice: VirtualDeviceManager.VirtualDevice,
        name: String,
        display: Display
    ): InputDeviceHolder<VirtualMouse> = prepareInputDevice {
        virtualDevice.createVirtualMouse(
            VirtualMouseConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setInputDeviceName(name)
                .setAssociatedDisplayId(display.displayId)
                .build()
        )
    }

    fun createAndPrepareRotary(
        virtualDevice: VirtualDeviceManager.VirtualDevice,
        name: String,
        display: Display
    ): InputDeviceHolder<VirtualRotaryEncoder> = prepareInputDevice {
        virtualDevice.createVirtualRotaryEncoder(
            VirtualRotaryEncoderConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setInputDeviceName(name)
                .setAssociatedDisplayId(display.displayId)
                .build()
        )
    }

    fun createAndPrepareKeyboard(
        virtualDevice: VirtualDeviceManager.VirtualDevice,
        name: String,
        display: Display,
        languageTag: String = VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG,
        layoutType: String = VirtualKeyboardConfig.DEFAULT_LAYOUT_TYPE
    ): InputDeviceHolder<VirtualKeyboard> =
        prepareInputDevice({
            virtualDevice.createVirtualKeyboard(
                VirtualKeyboardConfig.Builder()
                    .setVendorId(VENDOR_ID)
                    .setProductId(PRODUCT_ID)
                    .setInputDeviceName(name)
                    .setAssociatedDisplayId(display.displayId)
                    .setLanguageTag(languageTag)
                    .setLayoutType(layoutType)
                    .build()
            )
        }, languageTag, layoutType)

    fun createAndPrepareDpad(
        virtualDevice: VirtualDeviceManager.VirtualDevice,
        name: String,
        display: Display
    ): InputDeviceHolder<VirtualDpad> = prepareInputDevice {
        virtualDevice.createVirtualDpad(
            VirtualDpadConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setInputDeviceName(name)
                .setAssociatedDisplayId(display.displayId)
                .build()
        )
    }

    fun createAndPrepareNavigationTouchpad(
        virtualDevice: VirtualDeviceManager.VirtualDevice,
        name: String,
        display: Display,
        touchpadWidth: Int = display.mode.physicalWidth,
        touchpadHeight: Int = display.mode.physicalHeight
    ): InputDeviceHolder<VirtualNavigationTouchpad> = prepareInputDevice {
        virtualDevice.createVirtualNavigationTouchpad(
            VirtualNavigationTouchpadConfig.Builder(touchpadWidth, touchpadHeight)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setInputDeviceName(name)
                .setAssociatedDisplayId(display.displayId)
                .build()
        )
    }

    /** Holds a virtual input device along with its input device ID.  */
    class InputDeviceHolder<T : Closeable?>(val device: T, val deviceId: Int) : Closeable {
        @Throws(IOException::class)
        override fun close() {
            device!!.close()
        }
    }

    /** Utility to verify that an input device with a given parameters has been created.  */
    private class InputDeviceAddedWaiter(
        private val mInputManager: InputManager,
        private val mLanguageTag: String?,
        private val mLayoutType: String?
    ) : InputManager.InputDeviceListener, AutoCloseable {
        private val mLatch = CountDownLatch(1)
        private var mDeviceId = 0

        init {
            mInputManager.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
        }

        override fun onInputDeviceAdded(deviceId: Int) {
            onInputDeviceChanged(deviceId)
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            val device = mInputManager.getInputDevice(deviceId)
            if (device != null &&
                device.productId == PRODUCT_ID &&
                device.vendorId == VENDOR_ID &&
                mLanguageTag == device.keyboardLanguageTag &&
                mLayoutType == device.keyboardLayoutType) {
                mDeviceId = deviceId
                mLatch.countDown()
            }
        }

        override fun close() {
            mInputManager.unregisterInputDeviceListener(this)
        }

        /** Returns the device ID of the newly added input device.  */
        @Throws(InterruptedException::class)
        fun await(): Int {
            Truth.assertThat(mLatch.await(3, TimeUnit.SECONDS)).isTrue()
            return mDeviceId
        }
    }
}
