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

package com.android.bedstead.testapisreflection.ksp.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion

/** A loader providing methods to read data from resources */
class ResourceLoader {
    /**
     * Reads a new line separated list of strings from a resource file.
     *
     * @param sourcePath path to the resource file
     * @return a flow of lines in the file that are already trimmed and has blank lines filtered out
     */
    fun loadListFromResources(sourcePath: String): Flow<String> {
        val reader =
            this::class.java.getResourceAsStream(sourcePath)?.bufferedReader()
                ?: error("Resource $sourcePath not found")
        return reader
            .lineSequence()
            .asFlow()
            .onCompletion { reader.close() }
            .flowOn(Dispatchers.IO)
            .map(String::trim)
            .filterNot(String::isEmpty)
    }
}