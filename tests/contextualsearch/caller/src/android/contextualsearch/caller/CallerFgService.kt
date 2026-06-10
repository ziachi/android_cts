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

import android.app.Service
import android.app.contextualsearch.ContextualSearchManager
import android.content.Intent
import android.os.IBinder
import com.android.compatibility.common.util.BroadcastMessenger

/**
 * This service is used to test Contextual Search Manager Service interactions with services
 * from a separate app.
 */
class CallerFgService : Service() {

    public override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val ctx = getApplicationContext()
        var result = ContextualSearchMessage.RESULT_OK
        try {
            ctx.getSystemService(ContextualSearchManager::class.java)
                .startContextualSearch()
        } catch (exception: Exception) {
            result = ContextualSearchMessage.RESULT_EXCEPTION
        }
        BroadcastMessenger.send(ctx, ContextualSearchMessage.TAG, ContextualSearchMessage(result))
        return super.onStartCommand(intent, flags, startId)
    }

    public override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        val TAG: String = "CallerFgService"
    }
}
