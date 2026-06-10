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
package android.hardware.input.cts.tests

import android.Manifest
import android.companion.virtual.VirtualDeviceManager
import android.companion.virtualdevice.flags.Flags
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.virtualdevice.cts.common.VirtualDeviceRule
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(JUnitParamsRunner::class)
class VirtualInputDeviceGenericTest {
    @get:Rule
    val mRule: VirtualDeviceRule = VirtualDeviceRule.withAdditionalPermissions(
        Manifest.permission.INJECT_EVENTS
    )

    private lateinit var mVirtualDevice: VirtualDeviceManager.VirtualDevice
    private lateinit var mDisplayManager: DisplayManager
    private lateinit var mInputManager: InputManager

    fun interface VirtualInputDeviceFactory<T : Closeable> {
        fun create(
            virtualDevice: VirtualDeviceManager.VirtualDevice,
            name: String,
            display: Display
        ): VirtualInputDeviceCreator.InputDeviceHolder<T>
    }

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        mDisplayManager = context.getSystemService(DisplayManager::class.java)
        mInputManager = context.getSystemService(InputManager::class.java)
        mVirtualDevice = mRule.createManagedVirtualDevice()
    }

    private fun allInputDevices(): List<VirtualInputDeviceFactory<*>> {
        val deviceFactories =
            mutableListOf<VirtualInputDeviceFactory<*>>(
                VirtualInputDeviceFactory(VirtualInputDeviceCreator::createAndPrepareDpad),
                VirtualInputDeviceFactory(VirtualInputDeviceCreator::createAndPrepareKeyboard),
                VirtualInputDeviceFactory(VirtualInputDeviceCreator::createAndPrepareMouse),
                VirtualInputDeviceFactory(
                    VirtualInputDeviceCreator::createAndPrepareTouchscreen
                ),
                VirtualInputDeviceFactory(
                    VirtualInputDeviceCreator::createAndPrepareNavigationTouchpad
                ),
                VirtualInputDeviceFactory(VirtualInputDeviceCreator::createAndPrepareStylus),
            )
        if (Flags.virtualRotary()) {
            deviceFactories.add(
                VirtualInputDeviceFactory(VirtualInputDeviceCreator::createAndPrepareRotary)
            )
        }
        return deviceFactories
    }

    @Parameters(method = "allInputDevices")
    @Test
    @Throws(Exception::class)
    fun close_multipleCallsSucceed(factory: VirtualInputDeviceFactory<*>) {
        val display: VirtualDisplay = mRule.createManagedVirtualDisplay(
            mVirtualDevice,
            VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder()
        )!!
        val inputDevice: Closeable =
            factory.create(mVirtualDevice, DEVICE_NAME, display.display).device
        inputDevice.close()
        inputDevice.close()
        inputDevice.close()
    }

    @Parameters(method = "allInputDevices")
    @Test
    @Throws(Exception::class)
    fun close_removesInputDevice(factory: VirtualInputDeviceFactory<*>) {
        val display: VirtualDisplay = mRule.createManagedVirtualDisplay(
            mVirtualDevice,
            VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder()
        )!!
        val deviceHolder: VirtualInputDeviceCreator.InputDeviceHolder<*> =
            factory.create(mVirtualDevice, DEVICE_NAME, display.display)
        InputDeviceRemovedWaiter(mInputManager, deviceHolder.deviceId).use { waiter ->
            deviceHolder.close()
            assertThat(waiter.awaitDeviceRemoval()).isTrue()
        }
    }

    @Parameters(method = "allInputDevices")
    @Test
    @Throws(Exception::class)
    fun closeVirtualDevice_removesInputDevice(factory: VirtualInputDeviceFactory<*>) {
        val display: VirtualDisplay = mRule.createManagedVirtualDisplay(
            mVirtualDevice,
            VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder()
        )!!
        val deviceHolder: VirtualInputDeviceCreator.InputDeviceHolder<*> =
            factory.create(mVirtualDevice, DEVICE_NAME, display.display)
        InputDeviceRemovedWaiter(mInputManager, deviceHolder.deviceId).use { waiter ->
            mVirtualDevice.close()
            assertThat(waiter.awaitDeviceRemoval()).isTrue()
        }
    }

    @Parameters(method = "allInputDevices")
    @Test
    fun createVirtualInputDevice_duplicateName_throwsException(
        factory: VirtualInputDeviceFactory<*>
    ) {
        val display: VirtualDisplay = mRule.createManagedVirtualDisplay(
            mVirtualDevice,
            VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder()
        )!!
        factory.create(mVirtualDevice, DEVICE_NAME, display.display)
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(mVirtualDevice, DEVICE_NAME, display.display)
        }
    }

    @Parameters(method = "allInputDevices")
    @Test
    fun createVirtualInputDevice_untrustedDisplay_throwsException(
        factory: VirtualInputDeviceFactory<*>
    ) {
        val display: VirtualDisplay = mRule.createManagedVirtualDisplayWithFlags(
            mVirtualDevice,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                    or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        )!!
        mRule.runWithoutPermissions {
            assertThrows(SecurityException::class.java) {
                factory.create(mVirtualDevice, DEVICE_NAME, display.display)
            }
        }
    }

    @Parameters(method = "allInputDevices")
    @Test
    fun createVirtualInputDevice_defaultDisplay_throwsException(
        factory: VirtualInputDeviceFactory<*>
    ) {
        val display: Display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY)
        mRule.runWithoutPermissions {
            assertThrows(SecurityException::class.java) {
                factory.create(mVirtualDevice, DEVICE_NAME, display)
            }
        }
    }

    @Parameters(method = "allInputDevices")
    @Test
    fun createVirtualInputDevice_unownedDisplay_throwsException(
        factory: VirtualInputDeviceFactory<*>
    ) {
        val unownedDisplay: VirtualDisplay = mRule.createManagedUnownedVirtualDisplayWithFlags(
            DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
                    or DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
        )!!
        mRule.runWithoutPermissions {
            assertThrows(SecurityException::class.java) {
                factory.create(mVirtualDevice, DEVICE_NAME, unownedDisplay.display)
            }
        }
    }

    @Parameters(method = "allInputDevices")
    @Test
    fun createVirtualInputDevice_defaultDisplay_injectEvents_succeeds(
        factory: VirtualInputDeviceFactory<*>
    ) {
        val display: Display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY)
        assertThat(
            factory.create(
                mVirtualDevice,
                DEVICE_NAME,
                display
            )
        )
            .isNotNull()
    }

    @Parameters(method = "allInputDevices")
    @Test
    fun createVirtualInputDevice_unownedVirtualDisplay_injectEvents_succeeds(
        factory: VirtualInputDeviceFactory<*>
    ) {
        val unownedDisplay: VirtualDisplay = mRule.createManagedUnownedVirtualDisplayWithFlags(
            DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
        )!!
        assertThat(
            factory.create(
                mVirtualDevice,
                DEVICE_NAME,
                unownedDisplay.getDisplay()
           )
        )
            .isNotNull()
    }

    /** Utility to verify that an input device with a given ID has been removed.  */
    private class InputDeviceRemovedWaiter(
        private val mInputManager: InputManager,
        private val mDeviceId: Int
    ) : InputManager.InputDeviceListener, AutoCloseable {
        private val mLatch = CountDownLatch(1)

        init {
            mInputManager.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
        }

        override fun onInputDeviceAdded(deviceId: Int) {
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            if (deviceId == mDeviceId) {
                mLatch.countDown()
            }
        }

        override fun onInputDeviceChanged(deviceId: Int) {
        }

        override fun close() {
            mInputManager.unregisterInputDeviceListener(this)
        }

        @Throws(InterruptedException::class)
        fun awaitDeviceRemoval(): Boolean {
            return mLatch.await(3, TimeUnit.SECONDS)
        }
    }

    companion object {
        private const val DEVICE_NAME = "CtsVirtualGenericTestDevice"
    }
}
