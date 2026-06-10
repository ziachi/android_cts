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

package com.example.helloworld;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.ArraySet;

import java.util.ArrayList;

/**
 * Receiver of the mime group tests
 */
public class MimeGroupReceiver extends BroadcastReceiver {

    private static final String ACTION_UPDATE_MIME_GROUP_REQUEST =
            "com.example.helloworld.UPDATE_MIME_GROUP_REQUEST";
    private static final String ACTION_UPDATE_MIME_GROUP_RESPONSE =
            "com.example.helloworld.UPDATE_MIME_GROUP_RESPONSE";
    private static final String EXTRA_MIME_GROUP = "EXTRA_MIME_GROUP";
    private static final String EXTRA_MIME_TYPES = "EXTRA_MIME_TYPES";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_UPDATE_MIME_GROUP_REQUEST.equals(intent.getAction())) {
            return;
        }

        final String mimeGroup = intent.getStringExtra(EXTRA_MIME_GROUP);
        final ArrayList<String> mimeTypes = intent.getStringArrayListExtra(EXTRA_MIME_TYPES);

        context.getPackageManager().setMimeGroup(mimeGroup, new ArraySet<>(mimeTypes));

        final Intent response = new Intent(ACTION_UPDATE_MIME_GROUP_RESPONSE);
        response.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcast(response);
    }
}
