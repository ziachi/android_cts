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

import android.hardware.input.VirtualMouseButtonEvent
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VirtualMouseButtonEventTest {

    @Test
    fun parcelAndUnparcel_matches() {
        val originalEvent: VirtualMouseButtonEvent = VirtualMouseButtonEvent.Builder()
            .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
            .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
            .setEventTimeNanos(5000L)
            .build()
        val parcel: Parcel = Parcel.obtain()
        val flags = 0
        originalEvent.writeToParcel(parcel, flags)
        parcel.setDataPosition(0)
        val recreatedEvent: VirtualMouseButtonEvent =
            VirtualMouseButtonEvent.CREATOR.createFromParcel(parcel)
        assertWithMessage("Recreated event has different action")
            .that(originalEvent.action)
            .isEqualTo(recreatedEvent.action)
        assertWithMessage("Recreated event has different button code")
            .that(originalEvent.buttonCode)
            .isEqualTo(recreatedEvent.buttonCode)
        assertWithMessage("Recreated event has different event time")
            .that(originalEvent.eventTimeNanos)
            .isEqualTo(recreatedEvent.eventTimeNanos)
    }

    @Test
    fun buttonEvent_emptyBuilder_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualMouseButtonEvent.Builder().build()
        }
    }

    @Test
    fun buttonEvent_noButtonCode_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE).build()
        }
    }

    @Test
    fun buttonEvent_noAction_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualMouseButtonEvent.Builder()
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_BACK).build()
        }
    }

    @Test
    fun buttonEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_BACK)
                .setEventTimeNanos(-10L)
                .build()
        }
    }

    @Test
    fun buttonEvent_valid_created() {
        val eventTimeNanos = 5000L
        val event: VirtualMouseButtonEvent = VirtualMouseButtonEvent.Builder()
            .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
            .setButtonCode(VirtualMouseButtonEvent.BUTTON_BACK)
            .setEventTimeNanos(eventTimeNanos)
            .build()
        assertWithMessage("Incorrect button code").that(event.buttonCode).isEqualTo(
            VirtualMouseButtonEvent.BUTTON_BACK
        )
        assertWithMessage("Incorrect action").that(event.action).isEqualTo(
            VirtualMouseButtonEvent.ACTION_BUTTON_PRESS
        )
        assertWithMessage("Incorrect event time").that(event.eventTimeNanos).isEqualTo(
            eventTimeNanos
        )
    }
}
