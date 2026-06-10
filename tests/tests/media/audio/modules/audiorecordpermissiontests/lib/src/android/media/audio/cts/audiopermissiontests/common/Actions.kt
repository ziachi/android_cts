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

import android.content.AttributionSource
import android.content.Intent
import android.os.IBinder

// Inbound messages
const val ACTION_START_RECORD = ".ACTION_START_RECORD"
const val ACTION_STOP_RECORD = ".ACTION_STOP_RECORD"
const val ACTION_FINISH_RECORD = ".ACTION_FINISH_RECORD"
const val ACTION_START_FOREGROUND = ".ACTION_START_FOREGROUND"
const val ACTION_STOP_FOREGROUND = ".ACTION_STOP_FOREGROUND"
const val ACTION_TEARDOWN = ".ACTION_TEARDOWN"
const val ACTION_FINISH_ACTIVITY = ".ACTION_FINISH_ACTIVITY"
const val ACTION_REQUEST_ATTRIBUTION = ".ACTION_REQUEST_ATTRIBUTION"
const val ACTION_BOUNCE_FOREGROUND = ".ACTION_BOUNCE_FOREGROUND"
// Outbound messages
const val ACTION_BEGAN_RECEIVE_AUDIO = ".ACTION_BEGAN_RECEIVE_AUDIO"
const val ACTION_BEGAN_RECEIVE_SILENCE = ".ACTION_BEGAN_RECEIVE_SILENCE"
const val ACTION_TEARDOWN_FINISHED = ".ACTION_TEARDOWN_FINISHED"
const val ACTION_ACTIVITY_STARTED = ".ACTION_ACTIVITY_STARTED"
const val ACTION_ACTIVITY_FINISHED = ".ACTION_ACTIVITY_FINISHED"
const val ACTION_SEND_ATTRIBUTION = ".ACTION_SEND_ATTRIBUTION"
const val ACTION_RECORD_STARTED = ".ACTION_RECORD_STARTED"
const val ACTION_RECORD_STOPPED = ".ACTION_RECORD_STOPPED"
const val ACTION_RECORD_FINISHED = ".ACTION_RECORD_FINISHED"
// Extras
const val EXTRA_ATTRIBUTION = "EXTRA_ATTRIBUTION"
const val EXTRA_ATTRIBUTION_UID = "EXTRA_ATTRIBUTION_UID"
const val EXTRA_ATTRIBUTION_PID = "EXTRA_ATTRIBUTION_PID"
const val EXTRA_ATTRIBUTION_PACKAGE = "EXTRA_ATTRIBUTION_PACKAGE"
const val EXTRA_ATTRIBUTION_TOKEN = "EXTRA_ATTRIBUTION_TOKEN"
const val EXTRA_IS_FOREGROUND = "EXTRA_IS_FOREGROUND"
const val EXTRA_RECORD_ID = "EXTRA_RECORD_ID"
const val EXTRA_CAP_OVERRIDE = "EXTRA_CAP_OVERRIDE"

// Test instrumentation package
const val TARGET_PACKAGE = "android.media.audio.cts.audiopermissiontests"
