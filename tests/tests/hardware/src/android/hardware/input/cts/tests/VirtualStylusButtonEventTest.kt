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

import android.hardware.input.VirtualStylusButtonEvent
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VirtualStylusButtonEventTest {

    @Test
    fun parcelAndUnparcel_matches() {
        val originalEvent: VirtualStylusButtonEvent = VirtualStylusButtonEvent.Builder()
            .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_PRESS)
            .setButtonCode(VirtualStylusButtonEvent.BUTTON_PRIMARY)
            .setEventTimeNanos(5000L)
            .build()

        val parcel: Parcel = Parcel.obtain()
        val flags = 0
        originalEvent.writeToParcel(parcel, flags)
        parcel.setDataPosition(0)
        val recreatedEvent: VirtualStylusButtonEvent =
            VirtualStylusButtonEvent.CREATOR.createFromParcel(parcel)

        assertWithMessage("Recreated event has different action")
            .that(originalEvent.action).isEqualTo(recreatedEvent.action)
        assertWithMessage("Recreated event has different button code")
            .that(originalEvent.buttonCode).isEqualTo(recreatedEvent.buttonCode)
        assertWithMessage("Recreated event has different event time")
            .that(originalEvent.eventTimeNanos).isEqualTo(recreatedEvent.eventTimeNanos)
    }

    @Test
    fun stylusButtonEvent_emptyBuilder_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusButtonEvent.Builder().build()
        }
    }

    @Test
    fun stylusButtonEvent_noButtonCode_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_RELEASE).build()
        }
    }

    @Test
    fun stylusButtonEvent_noAction_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusButtonEvent.Builder()
                .setButtonCode(VirtualStylusButtonEvent.BUTTON_SECONDARY).build()
        }
    }

    @Test
    fun stylusButtonEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(VirtualStylusButtonEvent.BUTTON_PRIMARY)
                .setEventTimeNanos(-10L)
                .build()
        }
    }

    @Test
    fun stylusButtonEvent_valid_created() {
        val eventTimeNanos = 5000L
        val event: VirtualStylusButtonEvent = VirtualStylusButtonEvent.Builder()
            .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_PRESS)
            .setButtonCode(VirtualStylusButtonEvent.BUTTON_PRIMARY)
            .setEventTimeNanos(eventTimeNanos)
            .build()
        assertWithMessage("Incorrect button code").that(event.buttonCode).isEqualTo(
            VirtualStylusButtonEvent.BUTTON_PRIMARY
        )
        assertWithMessage("Incorrect action").that(event.action).isEqualTo(
            VirtualStylusButtonEvent.ACTION_BUTTON_PRESS
        )
        assertWithMessage("Incorrect event time").that(event.eventTimeNanos)
            .isEqualTo(eventTimeNanos)
    }
}
