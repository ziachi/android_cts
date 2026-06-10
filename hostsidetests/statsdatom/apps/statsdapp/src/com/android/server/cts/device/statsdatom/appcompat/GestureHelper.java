/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.cts.device.statsdatom.appcompat;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;

public class GestureHelper {
    private static final String TAG = "GestureHelper";
    private static final long REGULAR_CLICK_LENGTH = 100L;
    private final UiAutomation mUiAutomation;
    private long mDownTime;

    public GestureHelper(Instrumentation instrumentation) {
        mUiAutomation = instrumentation.getUiAutomation();
    }

    /**
     * Injects a series of {@link MotionEvent}s to simulate tapping.
     */
    public boolean click(int x, int y, int times) {
        Log.d(TAG, String.format("Clicking on (%d, %d) %d times", x, y, times));
        for (int i = 0; i <= times; i++) {
            // If already tapped, inject delay in between movements.
            if (times > 0) {
                SystemClock.sleep(50L);
            }
            if (!touchDown(x, y)) {
                return false;
            }
            // Delay before releasing tap.
            SystemClock.sleep(REGULAR_CLICK_LENGTH);
            if (!touchUp(x, y)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Wait for all window container animations and surface operations to complete.
     */
    public void waitForAnimation() {
        mUiAutomation.syncInputTransactions();
    }

    private boolean touchDown(int x, int y) {
        mDownTime = SystemClock.uptimeMillis();
        MotionEvent event = getMotionEvent(mDownTime, mDownTime, MotionEvent.ACTION_DOWN, x, y);
        return injectEventSync(event);
    }

    private boolean touchUp(int x, int y) {
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = getMotionEvent(mDownTime, eventTime, MotionEvent.ACTION_UP, x, y);
        mDownTime = 0;
        return injectEventSync(event);
    }

    private static MotionEvent getMotionEvent(long downTime, long eventTime, int action,
            float x, float y) {

        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_FINGER;

        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.pressure = 1;
        coords.size = 1;
        coords.x = x;
        coords.y = y;

        return MotionEvent.obtain(downTime, eventTime, action, 1,
                new MotionEvent.PointerProperties[] { properties },
                new MotionEvent.PointerCoords[] { coords },
                0, 0, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    private boolean injectEventSync(InputEvent event) {
        return mUiAutomation.injectInputEvent(event, true);
    }
}
