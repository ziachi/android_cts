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

package android.service.settings.preferences

import android.os.Parcel
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.flags.Flags.FLAG_SETTINGS_CATALYST
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@RequiresFlagsEnabled(FLAG_SETTINGS_CATALYST)
class SettingsPreferenceValueTest {

    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun buildSettingsPreferenceValue_booleanFieldSet() {
        val preferenceValue = SettingsPreferenceValue.Builder(SettingsPreferenceValue.TYPE_BOOLEAN)
            .setBooleanValue(true)
            .build()

        assertThat(preferenceValue.type).isEqualTo(SettingsPreferenceValue.TYPE_BOOLEAN)
        assertThat(preferenceValue.booleanValue).isTrue()
    }

    @Test
    fun buildSettingsPreferenceValue_IntFieldSet() {
        val preferenceValue = SettingsPreferenceValue.Builder(SettingsPreferenceValue.TYPE_INT)
            .setIntValue(25)
            .build()

        assertThat(preferenceValue.type).isEqualTo(SettingsPreferenceValue.TYPE_INT)
        assertThat(preferenceValue.intValue).isEqualTo(25)
    }

    @Test
    fun buildSettingsPreferenceValue_LongFieldSet() {
        val preferenceValue = SettingsPreferenceValue.Builder(SettingsPreferenceValue.TYPE_LONG)
            .setLongValue(50L)
            .build()

        assertThat(preferenceValue.type).isEqualTo(SettingsPreferenceValue.TYPE_LONG)
        assertThat(preferenceValue.longValue).isEqualTo(50L)
    }

    @Test
    fun buildSettingsPreferenceValue_DoubleFieldSet() {
        val preferenceValue = SettingsPreferenceValue.Builder(SettingsPreferenceValue.TYPE_DOUBLE)
            .setDoubleValue(50f.toDouble())
            .build()

        assertThat(preferenceValue.type).isEqualTo(SettingsPreferenceValue.TYPE_DOUBLE)
        assertThat(preferenceValue.doubleValue).isEqualTo(50f.toDouble())
    }

    @Test
    fun buildSettingsPreferenceValue_StringFieldSet() {
        val preferenceValue = SettingsPreferenceValue.Builder(SettingsPreferenceValue.TYPE_STRING)
            .setStringValue("50")
            .build()

        assertThat(preferenceValue.type).isEqualTo(SettingsPreferenceValue.TYPE_STRING)
        assertThat(preferenceValue.stringValue!!).isEqualTo("50")
    }

    @Test
    fun buildSettingsPreferenceValue_fromParcelable() {
        val parcel = Parcel.obtain()
        val original = SettingsPreferenceValue.Builder(SettingsPreferenceValue.TYPE_STRING)
            .setStringValue("string value")
            .build()

        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val new = SettingsPreferenceValue.CREATOR.createFromParcel(parcel)

        assertThat(new.type).isEqualTo(SettingsPreferenceValue.TYPE_STRING)
        assertThat(new.stringValue!!).isEqualTo("string value")
    }
}
