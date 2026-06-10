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

import android.hardware.input.VirtualKeyEvent
import android.os.Parcel
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VirtualKeyEventTest {
    @Test
    fun parcelAndUnparcel_matches() {
        val originalEvent: VirtualKeyEvent = VirtualKeyEvent.Builder()
            .setAction(VirtualKeyEvent.ACTION_DOWN)
            .setKeyCode(KeyEvent.KEYCODE_ENTER)
            .setEventTimeNanos(5000L)
            .build()
        val parcel: Parcel = Parcel.obtain()
        val flags = 0
        originalEvent.writeToParcel(parcel, flags)
        parcel.setDataPosition(0)
        val recreatedEvent: VirtualKeyEvent =
            VirtualKeyEvent.CREATOR.createFromParcel(parcel)
        assertWithMessage("Recreated event has different action")
            .that(originalEvent.action)
            .isEqualTo(recreatedEvent.action)
        assertWithMessage("Recreated event has different key code")
            .that(originalEvent.keyCode)
            .isEqualTo(recreatedEvent.keyCode)
        assertWithMessage("Recreated event has different event time")
            .that(originalEvent.eventTimeNanos)
            .isEqualTo(recreatedEvent.eventTimeNanos)
    }

    @Test
    fun keyEvent_emptyBuilder_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualKeyEvent.Builder().build()
        }
    }

    @Test
    fun keyEvent_noKeyCode_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualKeyEvent.Builder().setAction(VirtualKeyEvent.ACTION_DOWN).build()
        }
    }

    @Test
    fun keyEvent_noAction_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualKeyEvent.Builder().setKeyCode(KeyEvent.KEYCODE_A).build()
        }
    }

    @Test
    fun keyEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualKeyEvent.Builder()
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .setKeyCode(KeyEvent.KEYCODE_A)
                .setEventTimeNanos(-3L)
                .build()
        }
    }

    @Test
    fun keyEvent_valid_created() {
        val eventTimeNanos = 5000L
        val event: VirtualKeyEvent = VirtualKeyEvent.Builder()
            .setAction(VirtualKeyEvent.ACTION_DOWN)
            .setKeyCode(KeyEvent.KEYCODE_A)
            .setEventTimeNanos(eventTimeNanos)
            .build()
        assertWithMessage("Incorrect key code").that(event.keyCode).isEqualTo(
            KeyEvent.KEYCODE_A
        )
        assertWithMessage("Incorrect action").that(event.action).isEqualTo(
            VirtualKeyEvent.ACTION_DOWN
        )
        assertWithMessage("Incorrect event time").that(event.eventTimeNanos).isEqualTo(
            eventTimeNanos
        )
    }
}
