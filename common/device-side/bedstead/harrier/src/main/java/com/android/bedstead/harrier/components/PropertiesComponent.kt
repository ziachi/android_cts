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
import com.android.bedstead.harrier.annotations.EnsurePropertySet
import com.android.bedstead.nene.TestApis.properties
import kotlin.collections.set

/**
 * Contains logic specific to properties for Bedstead tests using [DeviceState] rule
 */
class PropertiesComponent : DeviceStateComponent {

    private val originalProperties: MutableMap<String, String> = mutableMapOf()

    /**
     * See [EnsurePropertySet]
     */
    fun ensurePropertySet(key: String, value: String) {
        if (!originalProperties.containsKey(key)) {
            originalProperties[key] = properties().get(key) ?: ""
        }
        properties().set(key, value)
    }

    override fun teardownShareableState() {
        originalProperties.forEach {
            properties().set(it.key, it.value)
        }
        originalProperties.clear()
    }
}
