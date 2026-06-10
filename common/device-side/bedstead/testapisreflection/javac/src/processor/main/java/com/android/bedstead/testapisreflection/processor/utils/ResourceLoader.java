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

package com.android.bedstead.testapisreflection.processor.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

/** A loader providing methods to read data from resources */
public final class ResourceLoader {

    /**
     * Reads a new line separated list of strings from a resource file.
     *
     * @param sourcePath path to the resource file
     * @return a flow of lines in the file that are already trimmed and has blank lines filtered out
     */
    public static ImmutableList<String> load(String sourcePath) {
        try {
            return ImmutableList.sortedCopyOf(Set.copyOf(Arrays.asList(Resources.toString(
                    ResourceLoader.class.getResource(sourcePath),
                    StandardCharsets.UTF_8).split("\n"))));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read file", e);
        }
    }
}
