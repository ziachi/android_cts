/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.bedstead.nene.resources

import android.content.res.Resources

/**
 * Wrapper for `android.content.res.Resources`.
 */
open class ResourcesWrapper(private val sResources: Resources) {
    /**
     * Get resource identifier.
     *
     * See [android.content.res.Resources.getIdentifier].
     */
    fun getIdentifier(configName: String, defType: String?, defPackage: String?): Int =
        sResources.getIdentifier(configName, defType, defPackage)

    /**
     * Get string resource through identifier.
     *
     * See [android.content.res.Resources.getString].
     */
    fun getString(id: Int) = sResources.getString(id)

    /**
     * Get string resource through identifier.
     *
     * See [android.content.res.Resources.getIdentifier],
     * [android.content.res.Resources.getString].
     */
    @JvmOverloads
    fun getString(configName: String, defType: String?, defPackage: String? = DEF_PACKAGE): String =
        sResources.getString(getIdentifier(configName, defType, defPackage))

    /**
     * Get bool resource through identifier.
     *
     * See [android.content.res.Resources.getBoolean].
     */
    fun getBoolean(id: Int) = sResources.getBoolean(id)

    /**
     * Get bool resource through identifier.
     *
     * See [android.content.res.Resources.getIdentifier],
     * [android.content.res.Resources.getBoolean].
     */
    @JvmOverloads
    fun getBoolean(configName: String, defPackage: String? = DEF_PACKAGE) =
        getBoolean(getIdentifier(configName, "bool", defPackage))

    /**
     * Get integer resource through identifier.
     *
     * See [android.content.res.Resources.getInteger].
     */
    fun getInteger(id: Int) = sResources.getInteger(id)

    /**
     * Get integer resource through identifier.
     *
     * See [android.content.res.Resources.getIdentifier],
     * [android.content.res.Resources.getInteger].
     */
    @JvmOverloads
    fun getInteger(configName: String, defPackage: String? = DEF_PACKAGE) =
        getInteger(getIdentifier(configName, "integer", defPackage))
}

private const val DEF_PACKAGE = "android"
