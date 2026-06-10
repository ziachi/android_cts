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
package android.hardware.input.cts.tests

import android.hardware.input.VirtualStylusMotionEvent
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VirtualStylusMotionEventTest {

    @Test
    fun parcelAndUnparcel_matches() {
        val x = 50
        val y = 800
        val pressure = 10
        val tiltX = 10
        val tiltY = 20
        val eventTimeNanos = 5000L
        val originalEvent: VirtualStylusMotionEvent = VirtualStylusMotionEvent.Builder()
            .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
            .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
            .setX(x)
            .setY(y)
            .setPressure(pressure)
            .setTiltX(tiltX)
            .setTiltY(tiltY)
            .setEventTimeNanos(eventTimeNanos)
            .build()

        val parcel: Parcel = Parcel.obtain()
        val flags = 0
        originalEvent.writeToParcel(parcel, flags)
        parcel.setDataPosition(0)
        val recreatedEvent: VirtualStylusMotionEvent =
            VirtualStylusMotionEvent.CREATOR.createFromParcel(parcel)

        assertWithMessage("Recreated event has different action")
            .that(originalEvent.action).isEqualTo(recreatedEvent.action)
        assertWithMessage("Recreated event has different tool type")
            .that(originalEvent.toolType).isEqualTo(recreatedEvent.toolType)
        assertWithMessage("Recreated event has different x")
            .that(originalEvent.x).isEqualTo(recreatedEvent.x)
        assertWithMessage("Recreated event has different y")
            .that(originalEvent.y).isEqualTo(recreatedEvent.y)
        assertWithMessage("Recreated event has different x-axis tilt")
            .that(originalEvent.tiltX).isEqualTo(recreatedEvent.tiltX)
        assertWithMessage("Recreated event has different y-axis tilt")
            .that(originalEvent.tiltY).isEqualTo(recreatedEvent.tiltY)
        assertWithMessage("Recreated event has different pressure")
            .that(originalEvent.pressure).isEqualTo(recreatedEvent.pressure)
        assertWithMessage("Recreated event has different event time")
            .that(originalEvent.eventTimeNanos).isEqualTo(recreatedEvent.eventTimeNanos)
    }

    @Test
    fun stylusMotionEvent_emptyBuilder_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusMotionEvent.Builder().build()
        }
    }

    @Test
    fun stylusMotionEvent_noAction_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setX(0)
                .setY(1)
                .build()
        }
    }

    @Test
    fun stylusMotionEvent_invalidAction_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(100)
                .setX(0)
                .setY(1)
                .build()
        }
    }

    @Test
    fun stylusMotionEvent_invalidToolType_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusMotionEvent.Builder()
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setToolType(100)
                .setX(0)
                .setY(1)
                .build()
        }
    }

    @Test
    fun stylusMotionEvent_noX_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setY(5)
                .build()
        }
    }

    @Test
    fun stylusMotionEvent_noY_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(5)
                .build()
        }
    }

    @Test
    fun stylusMotionEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setEventTimeNanos(-10L)
                .build()
        }
    }

    @Test
    fun stylusMotionEvent_invalidPressure_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setPressure(-10)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setPressure(300)
                .build()
        }
    }

    @Test
    fun stylusMotionEvent_invalidTiltX_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setTiltX(-100)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setTiltX(100)
                .build()
        }
    }

    @Test
    fun stylusMotionEvent_invalidTiltY_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setTiltY(-100)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusMotionEvent.Builder()
                .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(0)
                .setY(1)
                .setTiltY(100)
                .build()
        }
    }

    @Test
    fun stylusMotionEvent_validWithoutPressureAndTilt_created() {
        val x = 50
        val y = 800
        val eventTimeNanos = 5000L
        val event: VirtualStylusMotionEvent = VirtualStylusMotionEvent.Builder()
            .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
            .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
            .setX(x)
            .setY(y)
            .setEventTimeNanos(eventTimeNanos)
            .build()
        assertWithMessage("Incorrect action").that(event.action).isEqualTo(
            VirtualStylusMotionEvent.ACTION_DOWN
        )
        assertWithMessage("Incorrect tool type").that(event.toolType).isEqualTo(
            VirtualStylusMotionEvent.TOOL_TYPE_STYLUS
        )
        assertWithMessage("Incorrect x").that(event.x).isEqualTo(x)
        assertWithMessage("Incorrect y").that(event.y).isEqualTo(y)
        assertWithMessage("Incorrect event time").that(event.eventTimeNanos)
            .isEqualTo(eventTimeNanos)
    }

    @Test
    fun stylusMotionEvent_validWithPressureAndTilt_created() {
        val x = 50
        val y = 800
        val pressure = 10
        val tiltX = 10
        val tiltY = 20
        val eventTimeNanos = 5000L
        val event: VirtualStylusMotionEvent = VirtualStylusMotionEvent.Builder()
            .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
            .setToolType(VirtualStylusMotionEvent.TOOL_TYPE_STYLUS)
            .setX(x)
            .setY(y)
            .setPressure(pressure)
            .setTiltX(tiltX)
            .setTiltY(tiltY)
            .setEventTimeNanos(eventTimeNanos)
            .build()
        assertWithMessage("Incorrect action").that(event.action).isEqualTo(
            VirtualStylusMotionEvent.ACTION_DOWN
        )
        assertWithMessage("Incorrect tool type").that(event.toolType).isEqualTo(
            VirtualStylusMotionEvent.TOOL_TYPE_STYLUS
        )
        assertWithMessage("Incorrect x").that(event.x).isEqualTo(x)
        assertWithMessage("Incorrect y").that(event.y).isEqualTo(y)
        assertWithMessage("Incorrect x-axis tilt").that(event.tiltX).isEqualTo(tiltX)
        assertWithMessage("Incorrect y-axis tilt").that(event.tiltY).isEqualTo(tiltY)
        assertWithMessage("Incorrect pressure").that(event.pressure).isEqualTo(pressure)
        assertWithMessage("Incorrect event time").that(event.eventTimeNanos)
            .isEqualTo(eventTimeNanos)
    }
}
