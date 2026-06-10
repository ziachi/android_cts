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

import android.hardware.input.VirtualMouseScrollEvent
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VirtualMouseScrollEventTest {

    @Test
    fun parcelAndUnparcel_matches() {
        val x = 0.5f
        val y = -0.2f
        val eventTimeNanos = 5000L
        val originalEvent: VirtualMouseScrollEvent = VirtualMouseScrollEvent.Builder()
            .setXAxisMovement(x)
            .setYAxisMovement(y)
            .setEventTimeNanos(eventTimeNanos)
            .build()
        val parcel: Parcel = Parcel.obtain()
        val flags = 0
        originalEvent.writeToParcel(parcel, flags)
        parcel.setDataPosition(0)
        val recreatedEvent: VirtualMouseScrollEvent =
            VirtualMouseScrollEvent.CREATOR.createFromParcel(parcel)
        assertWithMessage("Recreated event has different x")
            .that(originalEvent.xAxisMovement)
            .isEqualTo(recreatedEvent.xAxisMovement)
        assertWithMessage("Recreated event has different y")
            .that(originalEvent.yAxisMovement)
            .isEqualTo(recreatedEvent.yAxisMovement)
        assertWithMessage("Recreated event has different event time")
            .that(originalEvent.eventTimeNanos)
            .isEqualTo(recreatedEvent.eventTimeNanos)
    }

    @Test
    fun scrollEvent_xOutOfRange_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualMouseScrollEvent.Builder()
                .setXAxisMovement(1.5f)
                .setYAxisMovement(1.0f)
        }
    }

    @Test
    fun scrollEvent_yOutOfRange_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualMouseScrollEvent.Builder()
                .setXAxisMovement(0.5f)
                .setYAxisMovement(1.1f)
        }
    }

    @Test
    fun scrollEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualMouseScrollEvent.Builder()
                .setXAxisMovement(-1f)
                .setYAxisMovement(1f)
                .setEventTimeNanos(-10L)
        }
    }

    @Test
    fun scrollEvent_valid_created() {
        val x = -1f
        val y = 1f
        val eventTimeNanos = 5000L
        val event: VirtualMouseScrollEvent = VirtualMouseScrollEvent.Builder()
            .setXAxisMovement(x)
            .setYAxisMovement(y)
            .setEventTimeNanos(eventTimeNanos)
            .build()
        assertWithMessage("Incorrect x value").that(event.xAxisMovement).isEqualTo(x)
        assertWithMessage("Incorrect y value").that(event.yAxisMovement).isEqualTo(y)
        assertWithMessage("Incorrect event time").that(event.eventTimeNanos)
            .isEqualTo(eventTimeNanos)
    }
}
