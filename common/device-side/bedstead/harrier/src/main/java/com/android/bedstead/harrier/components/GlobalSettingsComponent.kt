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
import com.android.bedstead.harrier.annotations.EnsureGlobalSettingSet
import com.android.bedstead.nene.TestApis.settings

/**
 * Contains logic specific to global settings for Bedstead tests using [DeviceState] rule
 */
class GlobalSettingsComponent : DeviceStateComponent {

    private val originalGlobalSettings: MutableMap<String, String?> = mutableMapOf()

    /**
     * See [EnsureGlobalSettingSet]
     */
    fun ensureGlobalSettingSet(key: String, value: String) {
        if (!originalGlobalSettings.containsKey(key)) {
            originalGlobalSettings[key] = settings().global().getString(key)
        }
        settings().global().putString(key, value)
    }

    override fun teardownShareableState() {
        originalGlobalSettings.forEach {
            settings().global().putString(it.key, it.value)
        }
        originalGlobalSettings.clear()
    }
}
