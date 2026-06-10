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

import android.companion.virtualdevice.flags.Flags
import android.hardware.input.VirtualDpadConfig
import android.hardware.input.VirtualKeyboardConfig
import android.hardware.input.VirtualMouseConfig
import android.hardware.input.VirtualNavigationTouchpadConfig
import android.hardware.input.VirtualRotaryEncoderConfig
import android.hardware.input.VirtualStylusConfig
import android.hardware.input.VirtualTouchscreenConfig
import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VirtualInputDeviceConfigTest {
    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private fun createVirtualDpadConfig(): VirtualDpadConfig {
        return VirtualDpadConfig.Builder()
            .setInputDeviceName(DEVICE_NAME)
            .setVendorId(VENDOR_ID)
            .setProductId(PRODUCT_ID)
            .setAssociatedDisplayId(DISPLAY_ID)
            .build()
    }

    private fun createVirtualKeyboardConfig(): VirtualKeyboardConfig {
        return VirtualKeyboardConfig.Builder()
            .setInputDeviceName(DEVICE_NAME)
            .setVendorId(VENDOR_ID)
            .setProductId(PRODUCT_ID)
            .setAssociatedDisplayId(DISPLAY_ID)
            .setLanguageTag(VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG)
            .setLayoutType(VirtualKeyboardConfig.DEFAULT_LAYOUT_TYPE)
            .build()
    }

    private fun createVirtualMouseConfig(): VirtualMouseConfig {
        return VirtualMouseConfig.Builder()
            .setInputDeviceName(DEVICE_NAME)
            .setVendorId(VENDOR_ID)
            .setProductId(PRODUCT_ID)
            .setAssociatedDisplayId(DISPLAY_ID)
            .build()
    }

    private fun createVirtualTouchscreenConfig(): VirtualTouchscreenConfig {
        return VirtualTouchscreenConfig.Builder(WIDTH, HEIGHT)
            .setInputDeviceName(DEVICE_NAME)
            .setVendorId(VENDOR_ID)
            .setProductId(PRODUCT_ID)
            .setAssociatedDisplayId(DISPLAY_ID)
            .build()
    }

    private fun createVirtualStylusConfig(): VirtualStylusConfig {
        return VirtualStylusConfig.Builder(WIDTH, HEIGHT)
            .setInputDeviceName(DEVICE_NAME)
            .setVendorId(VENDOR_ID)
            .setProductId(PRODUCT_ID)
            .setAssociatedDisplayId(DISPLAY_ID)
            .build()
    }

    private fun createVirtualRotaryEncoderConfig(): VirtualRotaryEncoderConfig {
        return VirtualRotaryEncoderConfig.Builder()
            .setInputDeviceName(DEVICE_NAME)
            .setVendorId(VENDOR_ID)
            .setProductId(PRODUCT_ID)
            .setAssociatedDisplayId(DISPLAY_ID)
            .build()
    }

    private fun createVirtualNavigationTouchpadConfig(): VirtualNavigationTouchpadConfig {
        return VirtualNavigationTouchpadConfig.Builder(WIDTH, HEIGHT)
            .setInputDeviceName(DEVICE_NAME)
            .setVendorId(VENDOR_ID)
            .setProductId(PRODUCT_ID)
            .setAssociatedDisplayId(DISPLAY_ID)
            .build()
    }

    @Test
    fun testConstructorAndGetters_virtualDpadConfig() {
        val config: VirtualDpadConfig = createVirtualDpadConfig()
        Truth.assertThat(config.inputDeviceName).isEqualTo(DEVICE_NAME)
        Truth.assertThat(config.vendorId).isEqualTo(VENDOR_ID)
        Truth.assertThat(config.productId).isEqualTo(PRODUCT_ID)
        Truth.assertThat(config.associatedDisplayId).isEqualTo(DISPLAY_ID)
    }

    @Test
    fun testParcel_virtualDpadConfig() {
        val config: VirtualDpadConfig = createVirtualDpadConfig()
        val parcel: Parcel = Parcel.obtain()
        config.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val configFromParcel: VirtualDpadConfig = VirtualDpadConfig.CREATOR.createFromParcel(parcel)
        Truth.assertThat(configFromParcel.inputDeviceName).isEqualTo(config.inputDeviceName)
        Truth.assertThat(configFromParcel.vendorId).isEqualTo(config.vendorId)
        Truth.assertThat(configFromParcel.productId).isEqualTo(config.productId)
        Truth.assertThat(configFromParcel.associatedDisplayId).isEqualTo(config.associatedDisplayId)
    }

    @Test
    fun testConstructorAndGetters_virtualKeyboardConfig() {
        val config: VirtualKeyboardConfig = createVirtualKeyboardConfig()
        Truth.assertThat(config.inputDeviceName).isEqualTo(DEVICE_NAME)
        Truth.assertThat(config.vendorId).isEqualTo(VENDOR_ID)
        Truth.assertThat(config.productId).isEqualTo(PRODUCT_ID)
        Truth.assertThat(config.associatedDisplayId).isEqualTo(DISPLAY_ID)
        Truth.assertThat(config.languageTag).isEqualTo(VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG)
        Truth.assertThat(config.layoutType).isEqualTo(VirtualKeyboardConfig.DEFAULT_LAYOUT_TYPE)
    }

    @Test
    fun testParcel_virtualKeyboardConfig() {
        val config: VirtualKeyboardConfig = createVirtualKeyboardConfig()
        val parcel: Parcel = Parcel.obtain()
        config.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val configFromParcel: VirtualKeyboardConfig =
            VirtualKeyboardConfig.CREATOR.createFromParcel(parcel)
        Truth.assertThat(configFromParcel.inputDeviceName).isEqualTo(config.inputDeviceName)
        Truth.assertThat(configFromParcel.vendorId).isEqualTo(config.vendorId)
        Truth.assertThat(configFromParcel.productId).isEqualTo(config.productId)
        Truth.assertThat(configFromParcel.associatedDisplayId).isEqualTo(config.associatedDisplayId)
        Truth.assertThat(configFromParcel.languageTag).isEqualTo(config.languageTag)
        Truth.assertThat(configFromParcel.layoutType).isEqualTo(config.layoutType)
    }

    @Test
    fun testBuilder_virtualKeyboardConfig_defaultValuedUsed() {
        // TODO(b/262924887): Add end-to-end tests for selecting virtual keyboard layout.
        val config: VirtualKeyboardConfig = VirtualKeyboardConfig.Builder()
            .setInputDeviceName(DEVICE_NAME)
            .setVendorId(VENDOR_ID)
            .setProductId(PRODUCT_ID)
            .setAssociatedDisplayId(DISPLAY_ID)
            .build()
        Truth.assertThat(config.languageTag).isEqualTo(VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG)
        Truth.assertThat(config.layoutType).isEqualTo(VirtualKeyboardConfig.DEFAULT_LAYOUT_TYPE)
    }

    @Test
    fun testBuilder_wellFormedTags_noException() {
        val builder: VirtualKeyboardConfig.Builder = VirtualKeyboardConfig.Builder()

        val wellFormedTag1 = "ru-Cyrl"
        builder.setLanguageTag(wellFormedTag1)

        val wellFormedTag2 = "es-Latn-419"
        builder.setLanguageTag(wellFormedTag2)

        val wellFormedTag3 = "zh-CN"
        builder.setLanguageTag(wellFormedTag3)

        val wellFormedTag4 = "pt-Latn-PT"
        builder.setLanguageTag(wellFormedTag4)
    }

    @Test
    fun testConstructorAndGetters_virtualMouseConfig() {
        val config: VirtualMouseConfig = createVirtualMouseConfig()
        Truth.assertThat(config.inputDeviceName).isEqualTo(DEVICE_NAME)
        Truth.assertThat(config.vendorId).isEqualTo(VENDOR_ID)
        Truth.assertThat(config.productId).isEqualTo(PRODUCT_ID)
        Truth.assertThat(config.associatedDisplayId).isEqualTo(DISPLAY_ID)
    }

    @Test
    fun testParcel_virtualMouseConfig() {
        val config: VirtualMouseConfig = createVirtualMouseConfig()
        val parcel: Parcel = Parcel.obtain()
        config.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val configFromParcel: VirtualMouseConfig =
            VirtualMouseConfig.CREATOR.createFromParcel(parcel)
        Truth.assertThat(configFromParcel.inputDeviceName).isEqualTo(config.inputDeviceName)
        Truth.assertThat(configFromParcel.vendorId).isEqualTo(config.vendorId)
        Truth.assertThat(configFromParcel.productId).isEqualTo(config.productId)
        Truth.assertThat(configFromParcel.associatedDisplayId).isEqualTo(config.associatedDisplayId)
    }

    @Test
    fun testConstructorAndGetters_virtualTouchscreenConfig() {
        val config: VirtualTouchscreenConfig = createVirtualTouchscreenConfig()
        Truth.assertThat(config.inputDeviceName).isEqualTo(DEVICE_NAME)
        Truth.assertThat(config.vendorId).isEqualTo(VENDOR_ID)
        Truth.assertThat(config.productId).isEqualTo(PRODUCT_ID)
        Truth.assertThat(config.associatedDisplayId).isEqualTo(DISPLAY_ID)
        Truth.assertThat(config.width).isEqualTo(WIDTH)
        Truth.assertThat(config.height).isEqualTo(HEIGHT)
    }

    @Test
    fun testParcel_virtualTouchscreenConfig() {
        val config: VirtualTouchscreenConfig = createVirtualTouchscreenConfig()
        val parcel: Parcel = Parcel.obtain()
        config.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val configFromParcel: VirtualTouchscreenConfig =
            VirtualTouchscreenConfig.CREATOR.createFromParcel(parcel)
        Truth.assertThat(configFromParcel.inputDeviceName).isEqualTo(config.inputDeviceName)
        Truth.assertThat(configFromParcel.vendorId).isEqualTo(config.vendorId)
        Truth.assertThat(configFromParcel.productId).isEqualTo(config.productId)
        Truth.assertThat(configFromParcel.associatedDisplayId).isEqualTo(config.associatedDisplayId)
        Truth.assertThat(configFromParcel.width).isEqualTo(config.width)
        Truth.assertThat(configFromParcel.height).isEqualTo(config.height)
    }

    @Test
    fun testConstructorAndGetters_virtualStylusConfig() {
        val config: VirtualStylusConfig = createVirtualStylusConfig()
        Truth.assertThat(config.inputDeviceName).isEqualTo(DEVICE_NAME)
        Truth.assertThat(config.vendorId).isEqualTo(VENDOR_ID)
        Truth.assertThat(config.productId).isEqualTo(PRODUCT_ID)
        Truth.assertThat(config.associatedDisplayId).isEqualTo(DISPLAY_ID)
        Truth.assertThat(config.width).isEqualTo(WIDTH)
        Truth.assertThat(config.height).isEqualTo(HEIGHT)
    }

    @Test
    fun testParcel_virtualStylusConfig() {
        val config: VirtualStylusConfig = createVirtualStylusConfig()
        val parcel: Parcel = Parcel.obtain()
        config.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val configFromParcel: VirtualStylusConfig =
            VirtualStylusConfig.CREATOR.createFromParcel(parcel)
        Truth.assertThat(configFromParcel.inputDeviceName).isEqualTo(config.inputDeviceName)
        Truth.assertThat(configFromParcel.vendorId).isEqualTo(config.vendorId)
        Truth.assertThat(configFromParcel.productId).isEqualTo(config.productId)
        Truth.assertThat(configFromParcel.associatedDisplayId).isEqualTo(config.associatedDisplayId)
        Truth.assertThat(configFromParcel.width).isEqualTo(config.width)
        Truth.assertThat(configFromParcel.height).isEqualTo(config.height)
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_ROTARY)
    @Test
    fun testConstructorAndGetters_virtualRotaryConfig() {
        val config: VirtualRotaryEncoderConfig = createVirtualRotaryEncoderConfig()
        Truth.assertThat(config.inputDeviceName).isEqualTo(DEVICE_NAME)
        Truth.assertThat(config.vendorId).isEqualTo(VENDOR_ID)
        Truth.assertThat(config.productId).isEqualTo(PRODUCT_ID)
        Truth.assertThat(config.associatedDisplayId).isEqualTo(DISPLAY_ID)
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_ROTARY)
    @Test
    fun testParcel_virtualRotaryConfig() {
        val config: VirtualRotaryEncoderConfig = createVirtualRotaryEncoderConfig()
        val parcel: Parcel = Parcel.obtain()
        config.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val configFromParcel: VirtualRotaryEncoderConfig =
            VirtualRotaryEncoderConfig.CREATOR.createFromParcel(parcel)
        Truth.assertThat(configFromParcel.inputDeviceName).isEqualTo(config.inputDeviceName)
        Truth.assertThat(configFromParcel.vendorId).isEqualTo(config.vendorId)
        Truth.assertThat(configFromParcel.productId).isEqualTo(config.productId)
        Truth.assertThat(configFromParcel.associatedDisplayId).isEqualTo(config.associatedDisplayId)
    }

    @Test
    fun testConstructorAndGetters_virtualNavigationTouchpadConfig() {
        val config: VirtualNavigationTouchpadConfig = createVirtualNavigationTouchpadConfig()
        Truth.assertThat(config.inputDeviceName).isEqualTo(DEVICE_NAME)
        Truth.assertThat(config.vendorId).isEqualTo(VENDOR_ID)
        Truth.assertThat(config.productId).isEqualTo(PRODUCT_ID)
        Truth.assertThat(config.associatedDisplayId).isEqualTo(DISPLAY_ID)
        Truth.assertThat(config.width).isEqualTo(WIDTH)
        Truth.assertThat(config.height).isEqualTo(HEIGHT)
    }

    @Test
    fun testParcel_virtualNavigationTouchpadConfig() {
        val config: VirtualNavigationTouchpadConfig = createVirtualNavigationTouchpadConfig()
        val parcel: Parcel = Parcel.obtain()
        config.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val configFromParcel: VirtualNavigationTouchpadConfig =
            VirtualNavigationTouchpadConfig.CREATOR.createFromParcel(parcel)
        Truth.assertThat(configFromParcel.inputDeviceName).isEqualTo(config.inputDeviceName)
        Truth.assertThat(configFromParcel.vendorId).isEqualTo(config.vendorId)
        Truth.assertThat(configFromParcel.productId).isEqualTo(config.productId)
        Truth.assertThat(configFromParcel.associatedDisplayId).isEqualTo(config.associatedDisplayId)
        Truth.assertThat(configFromParcel.width).isEqualTo(config.width)
        Truth.assertThat(configFromParcel.height).isEqualTo(config.height)
    }

    @Test
    fun virtualDpadConfig_missingName_throwsException() {
        assertThrows(NullPointerException::class.java) {
            VirtualDpadConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .build()
        }
    }

    @Test
    fun virtualDpadConfig_nameLengthExceedsLimit_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualDpadConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualDpadConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(UTF8_DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
    }

    @Test
    fun virtualDpadConfig_missingDisplayId_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualKeyboardConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setInputDeviceName(DEVICE_NAME)
                .build()
        }
    }

    @Test
    fun virtualKeyboardConfig_missingName_throwsException() {
        assertThrows(NullPointerException::class.java) {
            VirtualKeyboardConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .build()
        }
    }

    @Test
    fun virtualKeyboardConfig_nameLengthExceedsLimit_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualKeyboardConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualKeyboardConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(UTF8_DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
    }

    @Test
    fun virtualKeyboardConfig_missingDisplayId_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualKeyboardConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setInputDeviceName(DEVICE_NAME)
                .build()
        }
    }

    @Test
    fun virtualMouseConfig_missingName_throwsException() {
        assertThrows(NullPointerException::class.java) {
            VirtualMouseConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .build()
        }
    }

    @Test
    fun virtualMouseConfig_nameLengthExceedsLimit_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualMouseConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualMouseConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(UTF8_DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
    }

    @Test
    fun virtualMouseConfig_missingDisplayId_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualMouseConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setInputDeviceName(DEVICE_NAME)
                .build()
        }
    }

    @Test
    fun virtualNavigationTouchpadConfig_missingName_throwsException() {
        assertThrows(NullPointerException::class.java) {
            VirtualNavigationTouchpadConfig.Builder(WIDTH, HEIGHT)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .build()
        }
    }

    @Test
    fun virtualNavigationTouchpadConfig_nameLengthExceedsLimit_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualNavigationTouchpadConfig.Builder(WIDTH, HEIGHT)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualNavigationTouchpadConfig.Builder(WIDTH, HEIGHT)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(UTF8_DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
    }

    @Test
    fun virtualNavigationTouchpadConfig_missingDisplayId_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualNavigationTouchpadConfig.Builder(WIDTH, HEIGHT)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setInputDeviceName(DEVICE_NAME)
                .build()
        }
    }

    @Test
    fun virtualNavigationTouchpadConfig_invalidDimensions_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualNavigationTouchpadConfig.Builder(WIDTH, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualNavigationTouchpadConfig.Builder(0, HEIGHT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualNavigationTouchpadConfig.Builder(0, 0)
        }
    }

    @Test
    fun virtualTouchscreenConfig_missingName_throwsException() {
        assertThrows(NullPointerException::class.java) {
            VirtualTouchscreenConfig.Builder(WIDTH, HEIGHT)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .build()
        }
    }

    @Test
    fun virtualTouchscreenConfig_nameLengthExceedsLimit_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchscreenConfig.Builder(WIDTH, HEIGHT)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchscreenConfig.Builder(WIDTH, HEIGHT)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(UTF8_DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
    }

    @Test
    fun virtualTouchscreenConfig_missingDisplayId_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchscreenConfig.Builder(WIDTH, HEIGHT)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setInputDeviceName(DEVICE_NAME)
                .build()
        }
    }

    @Test
    fun virtualTouchscreenConfig_invalidDimensions_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchscreenConfig.Builder(WIDTH, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchscreenConfig.Builder(0, HEIGHT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualTouchscreenConfig.Builder(0, 0)
        }
    }

    @Test
    fun virtualStylusConfig_missingName_throwsException() {
        assertThrows(NullPointerException::class.java) {
            VirtualStylusConfig.Builder(WIDTH, HEIGHT)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .build()
        }
    }

    @Test
    fun virtualStylusConfig_nameLengthExceedsLimit_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusConfig.Builder(WIDTH, HEIGHT)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusConfig.Builder(WIDTH, HEIGHT)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(UTF8_DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
    }

    @Test
    fun virtualStylusConfig_missingDisplayId_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusConfig.Builder(WIDTH, HEIGHT)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setInputDeviceName(DEVICE_NAME)
                .build()
        }
    }

    @Test
    fun virtualStylusConfig_invalidDimensions_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusConfig.Builder(WIDTH, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusConfig.Builder(0, HEIGHT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualStylusConfig.Builder(0, 0)
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_ROTARY)
    @Test
    fun virtualRotaryConfig_missingName_throwsException() {
        assertThrows(NullPointerException::class.java) {
            VirtualRotaryEncoderConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .build()
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_ROTARY)
    @Test
    fun virtualRotaryConfig_nameLengthExceedsLimit_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualRotaryEncoderConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            VirtualRotaryEncoderConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setAssociatedDisplayId(DISPLAY_ID)
                .setInputDeviceName(UTF8_DEVICE_NAME_THAT_IS_TOO_LONG)
                .build()
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_ROTARY)
    @Test
    fun virtualRotaryConfig_missingDisplayId_throwsException() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtualRotaryEncoderConfig.Builder()
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .setInputDeviceName(DEVICE_NAME)
                .build()
        }
    }

    companion object {
        private const val PRODUCT_ID = 1
        private const val VENDOR_ID = 1
        private const val DEVICE_NAME = "VirtualTestDevice"
        private const val DEVICE_NAME_THAT_IS_TOO_LONG =
            "The way to dusty death. Out, out, brief candle." +
                    "Life’s but a walking shadow, a poor player"

        // Has only 39 characters but is 109 bytes as utf-8
        private const val UTF8_DEVICE_NAME_THAT_IS_TOO_LONG =
            "░▄▄▄▄░\n" +
                    "▀▀▄██►\n" +
                    "▀▀███►\n" +
                    "░▀███►░█►\n" +
                    "▒▄████▀▀"

        private const val DISPLAY_ID = 2
        private const val WIDTH = 600
        private const val HEIGHT = 800
    }
}
