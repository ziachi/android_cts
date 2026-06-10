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

package com.android.cts.host.broadcasts;

public class Constants {
    public static final String TEST_PKG = "com.android.cts.device.broadcasts";
    public static final String TEST_CLASS = TEST_PKG + ".BroadcastStatsTest";

    public static final String RECEIVER_PKG = "com.android.cts.device.broadcasts.receiver";

    public static final String TEST_BROADCAST_ACTION =
            "com.android.cts.device.broadcasts.RECEIVE";

    public static final int BROADCAST_PROCESSING_TIME_MS = 10;

    public static final int BROADCAST_FINISH_TIMEOUT_MS = 5000;
}
