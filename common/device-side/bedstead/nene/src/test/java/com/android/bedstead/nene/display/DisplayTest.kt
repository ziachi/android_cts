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

package com.android.bedstead.nene.display

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.TestApis
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class DisplayTest {

    @Test
    fun setScreenOrientation_landscape_orientationIsSet() {
        TestApis.display().setScreenOrientation(DisplayProperties.ScreenOrientation.LANDSCAPE)

        assertThat(
            TestApis.display().getScreenOrientation()
        ).isEqualTo(DisplayProperties.ScreenOrientation.LANDSCAPE)
    }

    @Test
    fun setScreenOrientation_portrait_orientationIsSet() {
        TestApis.display().setScreenOrientation(DisplayProperties.ScreenOrientation.PORTRAIT)

        assertThat(
            TestApis.display().getScreenOrientation()
        ).isEqualTo(DisplayProperties.ScreenOrientation.PORTRAIT)
    }

    @Test
    fun setDisplayTheme_setDark_themeIsSet() {
        TestApis.display().setDisplayTheme(DisplayProperties.Theme.DARK)

        assertThat(TestApis.display().getDisplayTheme()).isEqualTo(DisplayProperties.Theme.DARK)
    }

    @Test
    fun setDisplayTheme_setLight_themeIsSet() {
        TestApis.display().setDisplayTheme(DisplayProperties.Theme.LIGHT)

        assertThat(TestApis.display().getDisplayTheme()).isEqualTo(DisplayProperties.Theme.LIGHT)
    }

    @Test
    fun display_returnsInstance() {
        assertThat(TestApis.display()).isNotNull()
    }

    @Test
    fun display_multipleCalls_returnsSameInstance() {
        assertThat(TestApis.display()).isEqualTo(TestApis.display())
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }
}
