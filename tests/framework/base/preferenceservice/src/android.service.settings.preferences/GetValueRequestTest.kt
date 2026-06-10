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
class GetValueRequestTest {

    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test(expected = IllegalArgumentException::class)
    fun buildGetValueRequest_emptyKey_shouldCrash() {
        GetValueRequest.Builder("screenKey", "").build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildGetValueRequest_emptyScreenKey_shouldCrash() {
        GetValueRequest.Builder("", "prefKey").build()
    }

    @Test
    fun buildGetValueRequest_allFieldsSet() {
        val req = GetValueRequest.Builder("screenKey", "prefKey").build()

        assertThat(req.screenKey).isEqualTo("screenKey")
        assertThat(req.preferenceKey).isEqualTo("prefKey")
    }

    @Test
    fun buildGetValueRequest_fromParcelable() {
        val old = GetValueRequest.Builder("screenKey", "prefKey").build()

        val parcel = Parcel.obtain()
        old.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val new = GetValueRequest.CREATOR.createFromParcel(parcel)

        assertThat(new.screenKey).isEqualTo("screenKey")
        assertThat(new.preferenceKey).isEqualTo("prefKey")
    }
}
