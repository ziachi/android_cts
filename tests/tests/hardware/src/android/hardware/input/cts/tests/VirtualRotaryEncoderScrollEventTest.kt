/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.companion.virtualdevice.flags.Flags
import android.hardware.input.VirtualRotaryEncoderScrollEvent
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_ROTARY)
@SmallTest
@RunWith(AndroidJUnit4::class)
class VirtualRotaryEncoderScrollEventTest {
    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun parcelAndUnparcel_matches() {
        val scrollAmount = 0.5f
        val eventTimeNanos = 5000L
        val originalEvent: VirtualRotaryEncoderScrollEvent =
            VirtualRotaryEncoderScrollEvent.Builder()
                .setScrollAmount(scrollAmount)
                .setEventTimeNanos(eventTimeNanos)
                .build()
        val parcel: Parcel = Parcel.obtain()
        val flags = 0
        originalEvent.writeToParcel(parcel, flags)
        parcel.setDataPosition(0)
        val recreatedEvent: VirtualRotaryEncoderScrollEvent =
            VirtualRotaryEncoderScrollEvent.CREATOR.createFromParcel(parcel)
        assertWithMessage("Recreated event has different scroll amount")
            .that(originalEvent.scrollAmount)
            .isEqualTo(recreatedEvent.scrollAmount)
        assertWithMessage("Recreated event has different event time")
            .that(originalEvent.eventTimeNanos)
            .isEqualTo(recreatedEvent.eventTimeNanos)
    }

    @Test
    fun scrollEvent_scrollAmountOutOfRange_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualRotaryEncoderScrollEvent.Builder().setScrollAmount(1.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualRotaryEncoderScrollEvent.Builder().setScrollAmount(-1.1f)
        }
    }

    @Test
    fun scrollEvent_invalidEventTime_throwsIae() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualRotaryEncoderScrollEvent.Builder()
                .setScrollAmount(1.0f)
                .setEventTimeNanos(-10L)
        }
    }

    @Test
    fun scrollEvent_valid_created() {
        val scrollAmount = 1.0f
        val eventTimeNanos = 5000L
        val event: VirtualRotaryEncoderScrollEvent = VirtualRotaryEncoderScrollEvent.Builder()
            .setScrollAmount(scrollAmount)
            .setEventTimeNanos(eventTimeNanos)
            .build()
        assertWithMessage("Incorrect scroll direction")
            .that(event.scrollAmount)
            .isEqualTo(scrollAmount)
        assertWithMessage("Incorrect event time")
            .that(event.eventTimeNanos)
            .isEqualTo(eventTimeNanos)
    }
}
