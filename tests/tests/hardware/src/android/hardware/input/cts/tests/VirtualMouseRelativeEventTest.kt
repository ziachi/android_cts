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

import android.hardware.input.VirtualMouseRelativeEvent
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VirtualMouseRelativeEventTest {

    @Test
    fun parcelAndUnparcel_matches() {
        val x = 100f
        val y = 4f
        val eventTimeNanos = 5000L
        val originalEvent: VirtualMouseRelativeEvent = VirtualMouseRelativeEvent.Builder()
            .setRelativeX(x)
            .setRelativeY(y)
            .setEventTimeNanos(eventTimeNanos)
            .build()
        val parcel: Parcel = Parcel.obtain()
        val flags = 0
        originalEvent.writeToParcel(parcel, flags)
        parcel.setDataPosition(0)
        val recreatedEvent: VirtualMouseRelativeEvent =
            VirtualMouseRelativeEvent.CREATOR.createFromParcel(parcel)
        assertWithMessage("Recreated event has different x")
            .that(originalEvent.relativeX)
            .isEqualTo(recreatedEvent.relativeX)
        assertWithMessage("Recreated event has different y")
            .that(originalEvent.relativeY)
            .isEqualTo(recreatedEvent.relativeY)
        assertWithMessage("Recreated event has different event time")
            .that(originalEvent.eventTimeNanos)
            .isEqualTo(recreatedEvent.eventTimeNanos)
    }

    @Test
    fun relativeEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualMouseRelativeEvent.Builder()
                .setRelativeX(50f)
                .setRelativeY(10f)
                .setEventTimeNanos(-10L)
                .build()
        }
    }

    @Test
    fun relativeEvent_valid_created() {
        val x = -50f
        val y = 83f
        val eventTimeNanos = 5000L
        val event: VirtualMouseRelativeEvent = VirtualMouseRelativeEvent.Builder()
            .setRelativeX(x)
            .setRelativeY(y)
            .setEventTimeNanos(eventTimeNanos)
            .build()
        assertWithMessage("Incorrect x value").that(event.relativeX).isEqualTo(x)
        assertWithMessage("Incorrect y value").that(event.relativeY).isEqualTo(y)
        assertWithMessage("Incorrect event time").that(event.eventTimeNanos)
            .isEqualTo(eventTimeNanos)
    }
}
