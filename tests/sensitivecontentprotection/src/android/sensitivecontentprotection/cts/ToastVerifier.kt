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
package android.sensitivecontentprotection.cts

import android.server.wm.WindowManagerState
import android.server.wm.WindowManagerStateHelper
import android.view.WindowManager.LayoutParams.TYPE_TOAST
import com.android.compatibility.common.util.SystemUtil.eventually
import com.google.common.truth.Truth

class ToastVerifier {
    companion object {
        fun verifyToastShowsAndGoes() {
            // Toasts may take a while to show up on slower devices.
             eventually { Truth.assertThat(waitForToast()).isTrue() }
            // Toasts using Toast.LENGTH_LONG tends to not dismiss before the
            // WindowManagerStateHelper().waitFor timeout
            eventually { Truth.assertThat(waitForNoToast()).isTrue() }
        }

        fun verifyToastDoesNotShow() {
            // TODO: Find a way to avoid the 5 second polling here.
            Truth.assertThat(waitForToast()).isFalse()
        }

        private fun waitForToast(): Boolean {
            return WindowManagerStateHelper().waitFor({ state: WindowManagerState ->
                state.findFirstWindowWithType(TYPE_TOAST) != null
            }, "Toast")
        }

        fun waitForNoToast(): Boolean {
            return WindowManagerStateHelper().waitFor({ state: WindowManagerState ->
                state.findFirstWindowWithType(TYPE_TOAST) == null
            }, "Toast")
        }
    }
}
