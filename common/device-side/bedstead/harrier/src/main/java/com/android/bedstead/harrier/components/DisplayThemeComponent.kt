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
package com.android.bedstead.harrier.components

import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.annotations.EnsureUsingDisplayTheme
import com.android.bedstead.nene.display.Display.getDisplayTheme
import com.android.bedstead.nene.display.Display.setDisplayTheme
import com.android.bedstead.nene.display.DisplayProperties

/**
 * Contains logic specific to display theme for Bedstead tests using [DeviceState] rule
 */
class DisplayThemeComponent : DeviceStateComponent {

    private var mOriginalDisplayTheme: DisplayProperties.Theme? = null

    /**
     * See [EnsureUsingDisplayTheme]
     */
    fun ensureUsingDisplayTheme(theme: DisplayProperties.Theme) {
        if (mOriginalDisplayTheme == null) {
            mOriginalDisplayTheme = getDisplayTheme()
        }
        setDisplayTheme(theme)
    }

    override fun teardownNonShareableState() {
        // TODO(b/329570492): Support sharing of theme in bedstead across tests
        mOriginalDisplayTheme?.let {
            setDisplayTheme(it)
            mOriginalDisplayTheme = null
        }
    }
}
