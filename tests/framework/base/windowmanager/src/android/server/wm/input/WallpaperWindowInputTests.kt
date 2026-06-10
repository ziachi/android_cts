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
package android.server.wm.input

import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.platform.test.annotations.Presubmit
import android.server.wm.ActivityManagerTestBase
import android.server.wm.CliIntentExtra
import android.server.wm.CtsWindowInfoUtils.waitForWindowOnTop
import android.server.wm.TestJournalProvider
import android.server.wm.annotation.Group2
import android.server.wm.app.Components
import android.server.wm.app.Components.TestInteractiveLiveWallpaperKeys
import android.view.Display.DEFAULT_DISPLAY
import android.view.InputDevice
import android.view.MotionEvent
import android.window.WindowInfosListenerForTest
import com.android.cts.input.UinputTouchDevice
import com.android.cts.input.UinputTouchScreen
import com.android.cts.input.inputeventmatchers.withCoords
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.cts.input.inputeventmatchers.withSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Ensure moving windows and tapping is done synchronously.
 *
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceInput:WallpaperWindowInputTests
 */
@Presubmit
@Group2
class WallpaperWindowInputTests : ActivityManagerTestBase() {
    private lateinit var touchScreen: UinputTouchScreen

    @Before
    fun setup() {
        val wallpaperManager = WallpaperManager.getInstance(mContext)
        assumeTrue("Device does not support wallpapers", wallpaperManager.isWallpaperSupported)
        assumeTrue(
            "Device does not support live wallpapers",
            mContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER)
        )
        val displayManager =
            mInstrumentation.context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(DEFAULT_DISPLAY)!!
        touchScreen = UinputTouchScreen(mInstrumentation, display)
    }

    @After
    fun tearDown() {
        if (this::touchScreen.isInitialized) {
            touchScreen.close()
        }
    }

    private fun checkWallpaperEvent(enableWallpaperTouch: Boolean) {
        val wallpaperSession = createManagedChangeWallpaperSession()
        wallpaperSession.setWallpaperComponent(Components.TEST_INTERACTIVE_LIVE_WALLPAPER_SERVICE)

        launchActivity(
            Components.WALLPAPER_TARGET_ACTIVITY,
            CliIntentExtra.extraBool(
                Components.WallpaperTargetActivity.EXTRA_ENABLE_WALLPAPER_TOUCH,
                enableWallpaperTouch
            )
        )
        val found = waitForWindowOnTop(5.seconds.toJavaDuration())
            { windowInfo: WindowInfosListenerForTest.WindowInfo ->
                windowInfo.name.contains(Components.WALLPAPER_TARGET_ACTIVITY.className)
            }
        assertTrue(found)
        TestJournalProvider.TestJournalContainer.start()
        val task = mWmState.getTaskByActivity(Components.WALLPAPER_TARGET_ACTIVITY)
        val bounds = task.bounds
        val xOnScreen = bounds.width() / 2
        val yOnScreen = bounds.height() / 2
        val pointer = touchScreen.touchDown(xOnScreen, yOnScreen)

        val event = waitForMotionEventFromTestJournal(2.seconds)
        /**
         * If the wallpaper touches are enabled, we should receive the motion event. Otherwise, the
         * motion event should not reach the wallpaper.
         */
        if (enableWallpaperTouch) {
            assertThat(event, allOf(
                withMotionAction(MotionEvent.ACTION_DOWN),
                withCoords(Point(xOnScreen, yOnScreen), UinputTouchDevice.TOUCH_COORDINATE_EPSILON),
                withSource(InputDevice.SOURCE_TOUCHSCREEN)
            ))
        } else {
            assertNull(event)
        }
        pointer.lift()
    }

    @Test
    fun testShowWallpaper_withTouchEnabled() {
        checkWallpaperEvent(enableWallpaperTouch = true)
    }

    @Test
    fun testShowWallpaper_withWallpaperTouchDisabled() {
        checkWallpaperEvent(enableWallpaperTouch = false)
    }

    private fun waitForMotionEventFromTestJournal(timeout: Duration): MotionEvent? {
        var remainingTime = timeout
        while (remainingTime > 0.milliseconds) {
            val event = getMotionEventFromTestJournal()
            if (event != null) {
                return event
            }
            try {
                Thread.sleep(TIME_SLICE.inWholeMilliseconds)
            } catch (e: InterruptedException) {
                fail("unexpected InterruptedException")
            }
            remainingTime -= TIME_SLICE
        }
        return null
    }

    private fun getMotionEventFromTestJournal(): MotionEvent? {
        var event: MotionEvent? = null
        val journal =
            TestJournalProvider.TestJournalContainer.get(TestInteractiveLiveWallpaperKeys.COMPONENT)
        TestJournalProvider.TestJournalContainer.withThreadSafeAccess {
            if (journal.extras.containsKey(
                    TestInteractiveLiveWallpaperKeys.LAST_RECEIVED_MOTION_EVENT
                )
            ) {
                event =
                    journal.extras.getParcelable(
                        TestInteractiveLiveWallpaperKeys.LAST_RECEIVED_MOTION_EVENT,
                        MotionEvent::class.java
                    )
            }
        }
        return event
    }

    companion object {
        private const val TAG = "WallpaperWindowInputTests"
        private val TIME_SLICE = 50.milliseconds
    }
}
