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

import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsureSecureSettingSet
import com.android.bedstead.nene.TestApis.settings
import com.android.bedstead.nene.users.UserReference

/**
 * Contains logic specific to secure settings for Bedstead tests using [DeviceState] rule
 *
 * @param locator provides access to other dependencies.
 */
class SecureSettingsComponent(locator: BedsteadServiceLocator) : DeviceStateComponent {

    private val userTypeResolver: UserTypeResolver by locator
    private val originalSecureSettings:
            MutableMap<UserReference, MutableMap<String, String?>> = mutableMapOf()

    /**
     * See [EnsureSecureSettingSet]
     */
    fun ensureSecureSettingSet(user: UserType, key: String, value: String) {
        ensureSecureSettingSet(userTypeResolver.toUser(user), key, value)
    }

    private fun ensureSecureSettingSet(user: UserReference, key: String, value: String) {
        if (!originalSecureSettings.containsKey(user)) {
            originalSecureSettings[user] = mutableMapOf()
        }
        if (!originalSecureSettings[user]!!.containsKey(key)) {
            originalSecureSettings[user]!![key] = settings().secure().getString(user, key)
        }
        settings().secure().putString(user, key, value)
    }

    override fun teardownShareableState() {
        originalSecureSettings.forEach { usersSettings ->
            usersSettings.value.forEach {
                settings().secure().putString(usersSettings.key, it.key, it.value)
            }
        }
        originalSecureSettings.clear()
    }
}
