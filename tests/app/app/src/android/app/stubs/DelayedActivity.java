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

package android.app.stubs;

import static android.content.Intent.EXTRA_REMOTE_CALLBACK;
import static android.content.Intent.EXTRA_RETURN_RESULT;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteCallback;

/** Test being starting an activity and verifying the state of the MessageQueue. */
public class DelayedActivity extends Activity {
    public static final String ACTION_DELAYED_ACTIVITY_RESULT =
            "android.app.DelayedActivity.RESULT";
    private static final String TAG = "DelayedActivity";

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        RemoteCallback remoteCallback =
                getIntent().getParcelableExtra(EXTRA_REMOTE_CALLBACK, RemoteCallback.class);

        if (remoteCallback != null) {
            Bundle result = new Bundle();
            boolean isPending =
                    ((DelayedApplication) getApplication()).mHasPendingMessageInQueue.get();
            result.putInt(EXTRA_RETURN_RESULT, isPending ? RESULT_OK : RESULT_CANCELED);
            remoteCallback.sendResult(result);
        }
    }
}
