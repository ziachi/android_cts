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

package android.companion.cts.core

import android.companion.AssociationRequest
import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.companion.BluetoothDeviceFilter
import android.companion.Flags
import android.companion.cts.common.assertEmpty
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test [android.companion.AssociationRequest.Builder].
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:AssociationRequestBuilderTest
 */
@RunWith(AndroidJUnit4::class)
class AssociationRequestBuilderTest {

    @Test
    fun test_defaultValues() {
        val request = AssociationRequest.Builder()
                .build()

        request.apply {
            assertNull(deviceProfile)
            assertNull(displayName)

            assertNotNull(deviceFilters)
            assertEmpty(deviceFilters)

            assertFalse(isSelfManaged)
            assertFalse(isForceConfirmation)
            assertFalse(isSingleDevice)
            assertNull(deviceIcon)
        }
    }

    @Test
    fun test_setters() {
        val deviceFilterA = createBluetoothDeviceFilter("00:00:00:00:00:AA")
        val deviceFilterB = createBluetoothDeviceFilter("00:00:00:00:00:BB")
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val deviceIcon = Icon.createWithBitmap(bitmap)

        val request = AssociationRequest.Builder().apply {
            setDeviceProfile(DEVICE_PROFILE_WATCH)
            setDisplayName(DISPLAY_NAME)
            setSelfManaged(true)
            setForceConfirmation(true)
            setSingleDevice(true)
            addDeviceFilter(deviceFilterA)
            addDeviceFilter(deviceFilterB)
            if (Flags.associationDeviceIcon()) {
                setDeviceIcon(deviceIcon)
            }
        }.build()

        request.apply {
            assertEquals(actual = deviceProfile, expected = DEVICE_PROFILE_WATCH)
            assertEquals(actual = displayName, expected = DISPLAY_NAME)
            assertContentEquals(
                    actual = deviceFilters,
                    expected = listOf(deviceFilterA, deviceFilterB)
            )
            assertTrue(isSelfManaged)
            assertTrue(isForceConfirmation)
            assertTrue(isSingleDevice)
            if (Flags.associationDeviceIcon()) {
                assertNotNull(deviceIcon)
            }
        }
    }

    @Test
    fun test_selfManaged_require_displayName() {
        assertFailsWith<IllegalStateException> {
            AssociationRequest.Builder()
                    .setSelfManaged(true)
                    .build()
        }
    }

    companion object {
        private const val DISPLAY_NAME = "My Device"
    }
}

private fun createBluetoothDeviceFilter(address: String) = BluetoothDeviceFilter.Builder()
        .setAddress(address)
        .build()
