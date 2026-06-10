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
package com.android.cts.verifier.nfc.hcef;

import android.content.Intent;
import android.nfc.cardemulation.HostApduService;
import android.nfc.cardemulation.PollingFrame;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

public class PollingLoopService extends HostApduService {
    public static final String POLLING_FRAME_ACTION =
            "com.android.cts.verifier.nfc.hcef.POLLING_FRAME_ACTION";
    public static final String POLLING_FRAME_EXTRA = "POLLING_FRAME_EXTRA";

    @Override
    public byte[] processCommandApdu(byte[] arg0, Bundle arg1) {
        return null;
    }

    @Override
    public void processPollingFrames(List<PollingFrame> frames) {
        Intent pollingFrameIntent = new Intent(POLLING_FRAME_ACTION);
        pollingFrameIntent.putParcelableArrayListExtra(
                POLLING_FRAME_EXTRA, new ArrayList<PollingFrame>(frames));
        sendBroadcast(pollingFrameIntent);
    }

    @Override
    public void onDeactivated(int reason) {}
}
