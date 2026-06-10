/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.cts.input

import android.provider.Settings
import android.provider.Settings.Global.HIDE_ERROR_DIALOGS
import android.server.wm.settings.SettingsSession
import org.junit.rules.TestWatcher
import org.junit.runner.Description

private class HideErrorDialogsSession : SettingsSession<Int>(
        Settings.Global.getUriFor(HIDE_ERROR_DIALOGS), // uri
        Settings.Global::getInt, // getter
        Settings.Global::putInt, // setter
    )

/**
 * A test rule that enables error dialog (like ANR)
 */
class ShowErrorDialogsRule : TestWatcher() {

    companion object {
        private val TAG = "ShowErrorDialogs"
    }

    private val hideErrorDialogsSession = HideErrorDialogsSession()

    override fun starting(description: Description?) {
        hideErrorDialogsSession.set(0) // Don't hide error dialogs
    }

    override fun finished(description: Description?) {
        hideErrorDialogsSession.close()
    }
}
