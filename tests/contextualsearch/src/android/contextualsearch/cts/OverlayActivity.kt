/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *            http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.contextualsearch.cts

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.CountDownLatch

open class OverlayActivity : Activity() {

    private var overlayView: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WATCHER?.instance = this
        WATCHER?.created?.countDown()
    }

    override fun onResume() {
        super.onResume()
        WATCHER?.resumed?.countDown()
    }

    override fun finish() {
        overlayView?.let { windowManager.removeView(it) }
        super.finish()
    }

    fun addSecureFlag() {
        runOnUiThread { window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    fun addSecureOverlay() {
        runOnUiThread {
            overlayView = LinearLayout(this)
            overlayView?.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

            val textView = TextView(this)
            textView.text = "I am secure!"
            overlayView?.addView(textView)

            val windowParams =
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_SECURE
                )

            windowManager.addView(overlayView, windowParams)
        }
    }

    class Watcher {
        val created: CountDownLatch = CountDownLatch(1)
        val resumed: CountDownLatch = CountDownLatch(1)
        var instance: OverlayActivity? = null
    }

    companion object {
        private val TAG = OverlayActivity::class.java.simpleName
        var WATCHER: Watcher? = null
            set(value) {
                if (field != null) {
                    if (value != null) {
                        throw IllegalStateException("WATCHER already set. Cannot set again.")
                    } else {
                        Log.d(TAG, "clearing WATCHER.")
                    }
                }
                field = value
            }
    }
}
