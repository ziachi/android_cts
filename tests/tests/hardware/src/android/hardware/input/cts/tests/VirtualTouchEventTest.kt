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

import android.hardware.input.VirtualTouchEvent
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VirtualTouchEventTest {
    @Test
    fun parcelAndUnparcel_matches() {
        val x = 50f
        val y = 800f
        val pointerId = 1
        val pressure = 0.5f
        val majorAxisSize = 10f
        val eventTimeNanos = 5000L
        val originalEvent: VirtualTouchEvent = VirtualTouchEvent.Builder()
            .setAction(VirtualTouchEvent.ACTION_DOWN)
            .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
            .setX(x)
            .setY(y)
            .setPointerId(pointerId)
            .setPressure(pressure)
            .setMajorAxisSize(majorAxisSize)
            .setEventTimeNanos(eventTimeNanos)
            .build()
        val parcel: Parcel = Parcel.obtain()
        val flags = 0
        originalEvent.writeToParcel(parcel, flags)
        parcel.setDataPosition(0)
        val recreatedEvent: VirtualTouchEvent = VirtualTouchEvent.CREATOR.createFromParcel(parcel)
        assertWithMessage("Recreated event has different action")
            .that(originalEvent.action)
            .isEqualTo(recreatedEvent.action)
        assertWithMessage("Recreated event has different tool type")
            .that(originalEvent.toolType).isEqualTo(recreatedEvent.toolType)
        assertWithMessage("Recreated event has different x").that(originalEvent.x)
            .isEqualTo(recreatedEvent.x)
        assertWithMessage("Recreated event has different y").that(originalEvent.y)
            .isEqualTo(recreatedEvent.y)
        assertWithMessage("Recreated event has different pointer id")
            .that(originalEvent.pointerId).isEqualTo(recreatedEvent.pointerId)
        assertWithMessage("Recreated event has different pressure")
            .that(originalEvent.pressure).isEqualTo(recreatedEvent.pressure)
        assertWithMessage("Recreated event has different major axis size")
            .that(originalEvent.majorAxisSize)
            .isEqualTo(recreatedEvent.majorAxisSize)
        assertWithMessage("Recreated event has different event time")
            .that(originalEvent.eventTimeNanos)
            .isEqualTo(recreatedEvent.eventTimeNanos)
    }

    @Test
    fun touchEvent_emptyBuilder_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchEvent.Builder()
                .build()
        }
    }

    @Test
    fun touchEvent_noAction_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchEvent.Builder()
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setX(0f)
                .setY(1f)
                .setPointerId(1)
                .build()
        }
    }

    @Test
    fun touchEvent_noPointerId_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setX(0f)
                .setY(1f)
                .build()
        }
    }

    @Test
    fun touchEvent_noToolType_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setX(0f)
                .setY(1f)
                .setPointerId(1)
                .build()
        }
    }

    @Test
    fun touchEvent_noX_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setY(1f)
                .setPointerId(1)
                .build()
        }
    }

    @Test
    fun touchEvent_noY_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setX(0f)
                .setPointerId(1)
                .build()
        }
    }

    @Test
    fun touchEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setX(0f)
                .setY(1f)
                .setPointerId(1)
                .setEventTimeNanos(-5L)
                .build()
        }
    }

    @Test
    fun touchEvent_cancelUsedImproperly_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_CANCEL)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setX(0f)
                .setY(1f)
                .setPointerId(1)
                .build()
        }
    }

    @Test
    fun touchEvent_palmUsedImproperly_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_MOVE)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_PALM)
                .setX(0f)
                .setY(1f)
                .setPointerId(1)
                .build()
        }
    }

    @Test
    fun touchEvent_palmAndCancelUsedProperly() {
        val x = 0f
        val y = 1f
        val pointerId = 1
        val pressure = 0.5f
        val majorAxisSize = 10f
        val event: VirtualTouchEvent = VirtualTouchEvent.Builder()
            .setAction(VirtualTouchEvent.ACTION_CANCEL)
            .setToolType(VirtualTouchEvent.TOOL_TYPE_PALM)
            .setX(x)
            .setY(y)
            .setPointerId(pointerId)
            .setPressure(pressure)
            .setMajorAxisSize(majorAxisSize)
            .build()
        assertWithMessage("Incorrect action").that(event.action).isEqualTo(
            VirtualTouchEvent.ACTION_CANCEL
        )
        assertWithMessage("Incorrect tool type").that(event.toolType).isEqualTo(
            VirtualTouchEvent.TOOL_TYPE_PALM
        )
        assertWithMessage("Incorrect x").that(event.x).isEqualTo(x)
        assertWithMessage("Incorrect y").that(event.y).isEqualTo(y)
        assertWithMessage("Incorrect pointer id").that(event.pointerId).isEqualTo(pointerId)
        assertWithMessage("Incorrect pressure").that(event.pressure).isEqualTo(pressure)
        assertWithMessage("Incorrect major axis size").that(event.majorAxisSize).isEqualTo(
            majorAxisSize
        )
    }

    @Test
    fun touchEvent_valid_created() {
        val x = 0f
        val y = 1f
        val pointerId = 1
        val eventTimeNanos = 5000L
        val event: VirtualTouchEvent = VirtualTouchEvent.Builder()
            .setAction(VirtualTouchEvent.ACTION_DOWN)
            .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
            .setX(x)
            .setY(y)
            .setPointerId(pointerId)
            .setEventTimeNanos(eventTimeNanos)
            .build()
        assertWithMessage("Incorrect action").that(event.action).isEqualTo(
            VirtualTouchEvent.ACTION_DOWN
        )
        assertWithMessage("Incorrect tool type").that(event.toolType).isEqualTo(
            VirtualTouchEvent.TOOL_TYPE_FINGER
        )
        assertWithMessage("Incorrect x").that(event.x).isEqualTo(x)
        assertWithMessage("Incorrect y").that(event.y).isEqualTo(y)
        assertWithMessage("Incorrect pointer id").that(event.pointerId).isEqualTo(pointerId)
        assertWithMessage("Incorrect event time").that(event.eventTimeNanos)
            .isEqualTo(eventTimeNanos)
    }

    @Test
    fun touchEvent_validWithPressureAndAxis_created() {
        val x = 0f
        val y = 1f
        val pointerId = 1
        val pressure = 0.5f
        val majorAxisSize = 10f
        val event: VirtualTouchEvent = VirtualTouchEvent.Builder()
            .setAction(VirtualTouchEvent.ACTION_DOWN)
            .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
            .setX(x)
            .setY(y)
            .setPointerId(pointerId)
            .setPressure(pressure)
            .setMajorAxisSize(majorAxisSize)
            .build()
        assertWithMessage("Incorrect action").that(event.action).isEqualTo(
            VirtualTouchEvent.ACTION_DOWN
        )
        assertWithMessage("Incorrect tool type").that(event.toolType).isEqualTo(
            VirtualTouchEvent.TOOL_TYPE_FINGER
        )
        assertWithMessage("Incorrect x").that(event.x).isEqualTo(x)
        assertWithMessage("Incorrect y").that(event.y).isEqualTo(y)
        assertWithMessage("Incorrect pointer id").that(event.pointerId).isEqualTo(pointerId)
        assertWithMessage("Incorrect pressure").that(event.pressure).isEqualTo(pressure)
        assertWithMessage("Incorrect major axis size").that(event.majorAxisSize).isEqualTo(
            majorAxisSize
        )
    }
}
