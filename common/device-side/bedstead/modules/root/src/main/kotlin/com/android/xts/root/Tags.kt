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

package com.android.xts.root

object Tags {
    /**
     * Set when ADB has root capabilities.
     *
     * Note that this will only be set in tests which declare their use of ADB root using
     * [com.android.xts.root.annotations.RequireAdbRoot].
     */
    val ADB_ROOT = "adb-root"

    /**
     * Set when the test is instrumented as the root UID.
     *
     * Note that this will only be set in tests which declare their use of root instrumentation using
     * [com.android.xts.root.annotations.RequireRootInstrumentation].
     */
    val ROOT_INSTRUMENTATION = "root-instrumentation"
}
