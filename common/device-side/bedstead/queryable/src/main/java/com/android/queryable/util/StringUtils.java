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

package com.android.queryable.util;

/** Utilities for {@link String}*/
public final class StringUtils {

    private StringUtils() {}

    /** Check whether the string is null or empty. */
    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /** Ensure string ends with specified char. */
    public static String ensureEndsWith(String str, char ch) {
        if (str.charAt(str.length() - 1) == ch) {
            return str;
        }
        return str + ch;
    }

}
