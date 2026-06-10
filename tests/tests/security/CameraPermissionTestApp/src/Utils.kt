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

package android.security.cts.camera.open

import android.content.Intent
import android.security.cts.camera.open.lib.IntentKeys
import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation

private val TAG = OpenCameraActivity::class.java.simpleName

fun CancellableContinuation<Intent>.tryOrResume(
    keys: IntentKeys,
    result: Intent,
    method: String,
    callback: () -> Unit
) {
  try {
    callback()
  } catch (e: Exception) {
    Log.e(TAG, "${method}: Received exception: ${e.exceptionString}")
    if (isActive) {
      resume(result.apply { putException(keys, e) })
    }
  }
}

fun Intent.putException(keys: IntentKeys, e: Exception) {
  if (!hasExtra(keys.exception)) {
    putExtra(keys.exception, e.exceptionString)
  }
}

val Exception.exceptionString: String
  get() = "${this::class.java.simpleName}/${message}"
