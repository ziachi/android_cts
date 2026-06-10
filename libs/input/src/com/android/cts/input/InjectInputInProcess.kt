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

package com.android.cts.input.injectinputinprocess

import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.android.cts.input.MotionEventBuilder
import com.android.cts.input.PointerBuilder
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import org.junit.Assert.fail

/**
 * Click on the provided view.
 * May be invoked on the test thread, or on the UI thread. This is only possible to use when the
 * test is running in the same process as the Android application.
 *
 * @param view the view on which to click.
 * @param x the x location where to click, relative to the View's top left corner
 * @param y the y location where to click, relative to the View's top left corner
 */
fun clickOnView(view: View, x: Int, y: Int) {
    val clickTask = FutureTask {
        clickOnViewOnUiThread(view, x, y)
    }
    // If we are already on UI thread, then execute the code directly. Otherwise, post the click
    // task and wait for it to complete.
    if (Looper.getMainLooper().isCurrentThread) {
        clickTask.run()
    } else {
        view.post(clickTask)
    }

    try {
        clickTask.get() // this will block until FutureTask completes on the main thread
    } catch (e: InterruptedException) {
        fail("Interrupted while waiting for the click to be processed: $e" )
    } catch (e: ExecutionException) {
        fail("Execution failed while waiting for the click to be processed: $e" )
    }
}

/**
 * Click on the center of the provided view.
 * May be invoked on the test thread, or on the UI thread. This is only possible to use when the
 * test is running in the same process as the Android application.
 *
 * @param view the view on which to click.
 */
fun clickOnViewCenter(view: View) {
    return clickOnView(view, view.width / 2, view.height / 2)
}

private fun getViewLocationInWindow(view: View): Pair<Int, Int> {
    val xy = IntArray(2)
    view.getLocationInWindow(xy)
    return Pair(xy[0], xy[1])
}

private fun clickOnViewOnUiThread(view: View, x: Int, y: Int) {
    val (viewX, viewY) = getViewLocationInWindow(view)
    val clickX = viewX + x
    val clickY = viewY + y
    val event = MotionEventBuilder(
        MotionEvent.ACTION_DOWN,
        InputDevice.SOURCE_TOUCHSCREEN
    ).pointer(
        PointerBuilder(0, MotionEvent.TOOL_TYPE_FINGER).x(clickX.toFloat()).y(clickY.toFloat())
    ).build()

    view.dispatchTouchEvent(event)
    event.action = MotionEvent.ACTION_UP
    view.dispatchTouchEvent(event)
}
