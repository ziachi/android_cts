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
class MetadataResultTest {

    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val metadata1: SettingsPreferenceMetadata
        get() = SettingsPreferenceMetadata.Builder("screenKey", "prefKey")
            .setTitle("title")
            .setEnabled(true)
            .build()
    private val metadata2: SettingsPreferenceMetadata
        get() = SettingsPreferenceMetadata.Builder("screenKey2", "prefKey2")
            .setTitle("title2")
            .setEnabled(false)
            .build()

    @Test
    fun buildMetadataResult_failureResult_fieldEmpty() {
        val req = MetadataResult.Builder(MetadataResult.RESULT_UNSUPPORTED).build()

        assertThat(req.resultCode).isEqualTo(MetadataResult.RESULT_UNSUPPORTED)
        assertThat(req.metadataList).isEmpty()
    }

    @Test
    fun buildMetadataResult_fieldsSet() {
        val req = MetadataResult.Builder(MetadataResult.RESULT_OK)
            .setMetadataList(listOf(metadata1, metadata2))
            .build()

        assertThat(req.resultCode).isEqualTo(MetadataResult.RESULT_OK)
        with(req.metadataList[0]) {
            assertThat(screenKey).isEqualTo("screenKey")
            assertThat(key).isEqualTo("prefKey")
            assertThat(title!!).isEqualTo("title")
            assertThat(isEnabled).isTrue()
        }
        with(req.metadataList[1]) {
            assertThat(screenKey).isEqualTo("screenKey2")
            assertThat(key).isEqualTo("prefKey2")
            assertThat(title!!).isEqualTo("title2")
            assertThat(isEnabled).isFalse()
        }
    }

    @Test
    fun buildMetadataResult_fromParcelable() {
        val old = MetadataResult.Builder(MetadataResult.RESULT_OK)
            .setMetadataList(listOf(metadata1, metadata2))
            .build()

        val parcel = Parcel.obtain()
        old.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val new = MetadataResult.CREATOR.createFromParcel(parcel)

        assertThat(new.resultCode).isEqualTo(MetadataResult.RESULT_OK)
        with(new.metadataList[0]) {
            assertThat(screenKey).isEqualTo("screenKey")
            assertThat(key).isEqualTo("prefKey")
            assertThat(title!!).isEqualTo("title")
            assertThat(isEnabled).isTrue()
        }
        with(new.metadataList[1]) {
            assertThat(screenKey).isEqualTo("screenKey2")
            assertThat(key).isEqualTo("prefKey2")
            assertThat(title!!).isEqualTo("title2")
            assertThat(isEnabled).isFalse()
        }
    }
}
