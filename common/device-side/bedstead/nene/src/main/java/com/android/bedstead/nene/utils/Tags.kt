/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.bedstead.nene.utils

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Collection of [String] values which can be set and queried across Bedstead.
 *
 * Each module may have its own Tags object which contains tags specific to that module.
 */
object Tags {
    /** Set when a test includes a DeviceState ClassRule.  */
    const val USES_DEVICESTATE = "uses_devicestate"

    /** Set when a test involves watching for notifications.  */
    const val USES_NOTIFICATIONS = "uses_notifications"

    private val sTags = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /**
     * `true` if the tag has been added.
     */
    @JvmStatic
    fun hasTag(tag: String): Boolean {
        return sTags.contains(tag)
    }

    /**
     * Clear all added tags.
     */
    @JvmStatic
    fun clearTags() {
        sTags.clear()
    }

    /**
     * Add a tag.
     */
    @JvmStatic
    fun addTag(tag: String) {
        sTags.add(tag)
    }
}
