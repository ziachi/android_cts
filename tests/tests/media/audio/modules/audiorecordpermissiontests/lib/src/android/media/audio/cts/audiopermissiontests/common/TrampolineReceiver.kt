/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.audio.cts.audiopermissiontests.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log

/**
 * Recv which starts a FGS.
 */
open class TrampolineReceiver : BroadcastReceiver() {
    val TAG = getAppName() + "TrampolineReceiver"
    val PREFIX = "android.media.audio.cts."+ getAppName()
    val SERVICE_NAME = ".RecordService";

    open fun getAppName() : String {
        return "Base"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i(TAG, "Received ${intent}")
        when (intent?.action) {
            PREFIX + ACTION_BOUNCE_FOREGROUND -> {
                Log.i(TAG, "Bouncing FGS")
                val packageName = context!!.getPackageName()
                Intent()
                    .setClassName(packageName, packageName + SERVICE_NAME)
                    .setAction(packageName + ACTION_START_FOREGROUND)
                    // no mic caps
                    .putExtra(EXTRA_CAP_OVERRIDE, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                .let {
                    context.getApplicationContext()
                        .startForegroundService(it);
                }
            }
        }
    }
}
