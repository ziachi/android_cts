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

import android.content.Intent
import android.os.Bundle
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
class SettingsPreferenceMetadataTest {

    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val intent = Intent(Intent.ACTION_VIEW).setPackage("test")

    private val metadata: SettingsPreferenceMetadata
        get() = SettingsPreferenceMetadata.Builder("screenKey", "key")
            .setTitle("title")
            .setSummary("summary")
            .setReadPermissions(listOf("readPermission"))
            .setWritePermissions(listOf("writePermission"))
            .setEnabled(true)
            .setAvailable(true)
            .setWritable(true)
            .setRestricted(true)
            .setWriteSensitivity(SettingsPreferenceMetadata.EXPECT_POST_CONFIRMATION)
            .setLaunchIntent(intent)
            .setExtras(Bundle().apply { putString("bKey", "bValue") })
            .build()

    @Test
    fun buildMetadata_allFieldsSet() {
        with(metadata) {
            assertThat(key).isEqualTo("key")
            assertThat(screenKey).isEqualTo("screenKey")
            assertThat(title!!).isEqualTo("title")
            assertThat(summary!!).isEqualTo("summary")
            assertThat(readPermissions).isEqualTo(listOf("readPermission"))
            assertThat(writePermissions).isEqualTo(listOf("writePermission"))
            assertThat(isEnabled).isTrue()
            assertThat(isAvailable).isTrue()
            assertThat(isWritable).isTrue()
            assertThat(isRestricted).isTrue()
            assertThat(writeSensitivity)
                .isEqualTo(SettingsPreferenceMetadata.EXPECT_POST_CONFIRMATION)
            assertThat(launchIntent!!).isEqualTo(intent)
            assertThat(extras.getString("bKey")!!).isEqualTo("bValue")
        }
    }

    @Test
    fun buildMetadata_deeplinkSensitivity_allFieldsSet() {
        val deeplinkOnlyMetadata = SettingsPreferenceMetadata.Builder("screenKey", "key")
            .setTitle("title")
            .setSummary("summary")
            .setReadPermissions(listOf("readPermission"))
            .setWritePermissions(listOf("writePermission"))
            .setEnabled(true)
            .setAvailable(true)
            .setWritable(true)
            .setRestricted(true)
            .setWriteSensitivity(SettingsPreferenceMetadata.DEEPLINK_ONLY)
            .setLaunchIntent(intent)
            .setExtras(Bundle().apply { putString("bKey", "bValue") })
            .build()
        with(deeplinkOnlyMetadata) {
            assertThat(key).isEqualTo("key")
            assertThat(screenKey).isEqualTo("screenKey")
            assertThat(title!!).isEqualTo("title")
            assertThat(summary!!).isEqualTo("summary")
            assertThat(readPermissions).isEqualTo(listOf("readPermission"))
            assertThat(writePermissions).isEqualTo(listOf("writePermission"))
            assertThat(isEnabled).isTrue()
            assertThat(isAvailable).isTrue()
            assertThat(isWritable).isTrue()
            assertThat(isRestricted).isTrue()
            assertThat(writeSensitivity)
                .isEqualTo(SettingsPreferenceMetadata.DEEPLINK_ONLY)
            assertThat(launchIntent!!).isEqualTo(intent)
            assertThat(extras.getString("bKey")!!).isEqualTo("bValue")
        }
    }

    @Test
    fun buildMetadata_excludedSensitivity_nullLaunchIntent() {
        val md = SettingsPreferenceMetadata.Builder("screenKey", "key")
            .setLaunchIntent(intent)
            .setWriteSensitivity(SettingsPreferenceMetadata.NO_DIRECT_ACCESS)
            .build()
        assertThat(md.writeSensitivity).isEqualTo(SettingsPreferenceMetadata.NO_DIRECT_ACCESS)
        assertThat(md.launchIntent).isNull()
    }

    @Test
    fun buildMetadata_fromParcelable() {
        val parcel = Parcel.obtain()
        metadata.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val new = SettingsPreferenceMetadata.CREATOR.createFromParcel(parcel)

        with(new) {
            assertThat(key).isEqualTo("key")
            assertThat(screenKey).isEqualTo("screenKey")
            assertThat(title!!).isEqualTo("title")
            assertThat(summary!!).isEqualTo("summary")
            assertThat(readPermissions).isEqualTo(listOf("readPermission"))
            assertThat(writePermissions).isEqualTo(listOf("writePermission"))
            assertThat(isEnabled).isTrue()
            assertThat(isAvailable).isTrue()
            assertThat(isWritable).isTrue()
            assertThat(isRestricted).isTrue()
            assertThat(writeSensitivity)
                .isEqualTo(SettingsPreferenceMetadata.EXPECT_POST_CONFIRMATION)
            assertThat(launchIntent!!.toUri(0)).isEqualTo(intent.toUri(0))
            assertThat(extras.getString("bKey")!!).isEqualTo("bValue")
        }
    }
}
