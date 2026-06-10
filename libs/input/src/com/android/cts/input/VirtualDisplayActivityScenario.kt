/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.cts.input

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE
import android.hardware.display.VirtualDisplay
import android.hardware.display.VirtualDisplayConfig
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.server.wm.WindowManagerStateHelper
import android.util.Size
import android.view.Surface
import android.virtualdevice.cts.common.VirtualDeviceRule
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.android.cts.input.VirtualDisplayActivityScenario.Companion.CAPTURE_SECURE_VIDEO_OUTPUT
import com.android.cts.input.VirtualDisplayActivityScenario.Companion.DENSITY
import com.android.cts.input.VirtualDisplayActivityScenario.Companion.TAG
import com.android.cts.input.VirtualDisplayActivityScenario.Companion.VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT
import com.android.cts.input.VirtualDisplayActivityScenario.Companion.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
import com.android.cts.input.VirtualDisplayActivityScenario.Companion.VIRTUAL_DISPLAY_FLAG_TRUSTED
import com.android.cts.input.VirtualDisplayActivityScenario.Companion.VIRTUAL_DISPLAY_NAME
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import junit.framework.AssertionFailedError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.rules.ExternalResource
import org.junit.rules.TestName

interface VirtualDisplayActivityScenario<A : Activity> {
    companion object {
        const val TAG = "VirtualDisplayActivityScenario"
        const val VIRTUAL_DISPLAY_NAME = "CtsVirtualDisplay"
        const val CAPTURE_SECURE_VIDEO_OUTPUT = "android.permission.CAPTURE_SECURE_VIDEO_OUTPUT"
        const val DEFAULT_WIDTH = 480
        const val DEFAULT_HEIGHT = 800
        const val DENSITY = 160
        const val ORIENTATION_0 = Surface.ROTATION_0
        const val ORIENTATION_90 = Surface.ROTATION_90
        const val ORIENTATION_180 = Surface.ROTATION_180
        const val ORIENTATION_270 = Surface.ROTATION_270

        /** See [DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH].  */
        const val VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 shl 6

        /** See [DisplayManager.VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT].  */
        const val VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT = 1 shl 7

        /** See [DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED].  */
        const val VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 shl 10
    }

    var virtualDisplay: VirtualDisplay
    var activity: A
    val displayId: Int get() = virtualDisplay.display.displayId

    /**
     * This is a helper methods for tests to make assertions with the display rotated to the given
     * orientation.
     *
     * @param orientation The orientation to which the display should be rotated.
     * @param runnable The function to run with the display is in the given orientation.
     */
    fun runInDisplayOrientation(orientation: Int, runnable: () -> Unit)

    /**
     * Retrieves a Bitmap screenshot from the internal ImageReader this virtual display writes to.
     *
     * <p>Currently only supports screenshots in the RGBA_8888.
     */
    fun getScreenshot(): Bitmap?

    /**
     * A [VirtualDisplayActivityScenario] that can be used within a test function.
     */
    class AutoClose<A : Activity> private constructor(
        private val impl: Impl<A>
    ) : AutoCloseable, VirtualDisplayActivityScenario<A> by impl {

        companion object {
            inline operator fun <reified A : Activity> invoke(
                testName: TestName,
                useSecureDisplay: Boolean = false,
                size: Size = Size(DEFAULT_WIDTH, DEFAULT_HEIGHT),
            ): AutoClose<A> = AutoClose(
                A::class.java,
                testName,
                useSecureDisplay,
                size,
            )
        }

        constructor(
            type: Class<A>,
            testName: TestName,
            useSecureDisplay: Boolean,
            size: Size
        ) : this(Impl(type, testName, useSecureDisplay, size))

        init {
            impl.start()
        }

        override fun close() {
            impl.stop()
        }
    }

    /**
     * A [VirtualDisplayActivityScenario] that can be used as a test rule.
     */
    class Rule<A : Activity> private constructor(
        private val impl: Impl<A>
    ) : ExternalResource(), VirtualDisplayActivityScenario<A> by impl {

        companion object {
            inline operator fun <reified A : Activity> invoke(
                testName: TestName,
                useSecureDisplay: Boolean = false,
                size: Size = Size(DEFAULT_WIDTH, DEFAULT_HEIGHT),
                virtualDeviceRule: VirtualDeviceRule? = null,
            ): Rule<A> = Rule(
                A::class.java,
                testName,
                useSecureDisplay,
                size,
                virtualDeviceRule,
            )
        }

        constructor(
            type: Class<A>,
            testName: TestName,
            useSecureDisplay: Boolean,
            size: Size,
            virtualDeviceRule: VirtualDeviceRule? = null,
        ) : this(Impl(type, testName, useSecureDisplay, size, virtualDeviceRule))

        override fun before() {
            impl.start()
        }

        override fun after() {
            impl.stop()
        }
    }
}

/**
 * The private implementation of a [VirtualDisplayActivityScenario].
 */
private class Impl<A : Activity>(
    val type: Class<A>,
    val testName: TestName,
    val useSecureDisplay: Boolean,
    val size: Size,
    // If provided, creates the VirtualDisplay using VDM instead of DisplayManager.
    // TODO(b/366492484): Remove reliance on VDM when we achieve feature parity between VDM
    //   and display + input APIs.
    val virtualDeviceRule: VirtualDeviceRule? = null,
) : VirtualDisplayActivityScenario<A> {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var reader: ImageReader

    override lateinit var virtualDisplay: VirtualDisplay
    override lateinit var activity: A

    /** Set up the virtual display and start the activity A on that display. */
    fun start() {
        assumeTrue(supportsMultiDisplay())
        reader = ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, 2)

        if (virtualDeviceRule != null) {
            createDisplayUsingVirtualDeviceManager()
        } else {
            createDisplay()
        }

        val bundle = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
        val intent =
            Intent(Intent.ACTION_VIEW).setClass(instrumentation.targetContext, type)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        SystemUtil.runWithShellPermissionIdentity({
            @Suppress("UNCHECKED_CAST")
            activity = instrumentation.startActivitySync(intent, bundle) as A
        }, Manifest.permission.INTERNAL_SYSTEM_WINDOW)
        waitUntilActivityReadyForInput()

        assertEquals(
            "Ensure the activity is launched on the virtual display", displayId, activity.displayId
        )
        assertEquals(
            "Ensure the activity is configured with the same density as the virtual display",
             DENSITY, activity.resources.displayMetrics.densityDpi
        )
    }

    /** Clean up the resources related to the virtual display and the test activity. */
    fun stop() {
        if (!supportsMultiDisplay()) {
            return
        }
        releaseDisplay()
        activity.finish()
    }

    override fun runInDisplayOrientation(orientation: Int, runnable: () -> Unit) {
        val initialUserRotation =
            SystemUtil.runShellCommandOrThrow("wm user-rotation -d $displayId")!!
        SystemUtil.runShellCommandOrThrow("wm user-rotation -d $displayId lock $orientation")
        waitUntilActivityReadyForInput()

        try {
            runnable()
        } finally {
            SystemUtil.runShellCommandOrThrow(
                "wm user-rotation -d $displayId $initialUserRotation"
            )
        }
    }

    private fun supportsMultiDisplay(): Boolean {
        return instrumentation.targetContext.packageManager
            .hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)
    }

    private fun createDisplay() {
        val displayManager =
            instrumentation.targetContext.getSystemService(DisplayManager::class.java)

        val displayCreated = CountDownLatch(1)
        displayManager.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                displayCreated.countDown()
                displayManager.unregisterDisplayListener(this)
            }
        }, Handler(Looper.getMainLooper()))

        val runWithPermissions =
            if (useSecureDisplay) this::runWithSecureDisplayPermission else ::defaultRunner
        val flags = VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH or
                VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT or
                (if (useSecureDisplay) VIRTUAL_DISPLAY_FLAG_SECURE else 0)

        runWithPermissions {
            virtualDisplay = displayManager.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME, size.width, size.height, DENSITY, reader.surface, flags
            )
        }
        assertTrue(displayCreated.await(5, TimeUnit.SECONDS))
    }

    private fun createDisplayUsingVirtualDeviceManager() {
        val runWithPermissions =
            if (useSecureDisplay) this::runWithSecureDisplayPermission else ::defaultRunner
        val flags = VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH or
                VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT or
                VIRTUAL_DISPLAY_FLAG_TRUSTED or
                (if (useSecureDisplay) VIRTUAL_DISPLAY_FLAG_SECURE else 0)

        runWithPermissions {
            val virtualDevice = virtualDeviceRule!!.defaultVirtualDevice
            virtualDevice.setShowPointerIcon(true)
            virtualDisplay =
                virtualDevice.createVirtualDisplay(
                    VirtualDisplayConfig.Builder(
                        VIRTUAL_DISPLAY_NAME,
                        size.width,
                        size.height,
                        DENSITY,
                    )
                        .setSurface(reader.surface)
                        .setFlags(flags)
                        .build(),
                    /*executor*/
                    null,
                    /*callback*/
                    null,
                ) ?: throw AssertionFailedError("Failed to create virtual display")
        }
    }

    /**
     * NOTE: Running a test with the [CAPTURE_SECURE_VIDEO_OUTPUT] permission requires the test
     * suite to be run as root.
     */
    private fun runWithSecureDisplayPermission(command: () -> Unit) {
        instrumentation.uiAutomation.addOverridePermissionState(
            Process.myUid(),
            CAPTURE_SECURE_VIDEO_OUTPUT,
            PERMISSION_GRANTED
        )
        try {
            return command()
        } finally {
            instrumentation.uiAutomation.clearOverridePermissionStates(Process.myUid())
        }
    }

    private fun releaseDisplay() {
        virtualDisplay.release()
        reader.close()
    }

    private fun waitUntilActivityReadyForInput() {
        // If we requested an orientation change, just waiting for the window to be visible is
        // not sufficient. We should first wait for the transitions to stop, and the for app's
        // UI thread to process them before making sure the window is visible.
        WindowManagerStateHelper().waitUntilActivityReadyForInputInjection(
            activity,
            instrumentation,
            TAG,
            "test: ${testName.methodName}, virtualDisplayId=$displayId"
        )
    }

    override fun getScreenshot(): Bitmap? {
        val image = reader.acquireNextImage()
        if (image == null || image.format != PixelFormat.RGBA_8888) {
            return null
        }
        val buffer = image.planes[0].getBuffer()
        val pixelStrideBytes: Int = image.planes[0].getPixelStride()
        val rowStrideBytes: Int = image.planes[0].getRowStride()
        val pixelBytesPerRow = pixelStrideBytes * image.width
        val rowPaddingBytes = rowStrideBytes - pixelBytesPerRow

        // Remove the row padding bytes from the buffer before converting to a Bitmap
        val trimmedBuffer = ByteBuffer.allocate(buffer.remaining())
        buffer.rewind()
        while (buffer.hasRemaining()) {
            for (i in 0 until pixelBytesPerRow) {
                trimmedBuffer.put(buffer.get())
            }
            // Skip the padding bytes
            buffer.position(buffer.position() + rowPaddingBytes)
        }
        trimmedBuffer.flip() // Prepare the trimmed buffer for reading

        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(trimmedBuffer)
        return bitmap
    }
}

private fun defaultRunner(command: () -> Unit) = command()
