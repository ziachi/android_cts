/*
 * Copyright (C) 2025 The Android Open Source Project
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
package android.contextualsearch.caller

import android.app.Activity
import android.app.contextualsearch.ContextualSearchManager
import android.os.Bundle

/**
 * This activity is used to test Contextual Search Manager Service interactions with activities
 * from a separate app.
 */
class CallerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(ContextualSearchManager::class.java).startContextualSearch()
        finish()
    }

    companion object {
        private val TAG: String = "CallerActivity"
    }
}
