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

package android.security.cts.CVE_2021_0472;

import android.app.Activity;
import android.content.Intent;

public class PocActivity extends Activity {

    @Override
    protected void onResume() {
        Intent intent = null;
        try {
            super.onResume();
            intent = new Intent(CVE_2021_0472.CVE_2021_0472_ACTION);

            // Send taskId of this activity to be used in CVE_2021_0472.java using broadcast
            sendBroadcast(
                    intent.putExtra(CVE_2021_0472.TASK_ID, getTaskId())
                            .putExtra(CVE_2021_0472.EXCEPTION_MSG_KEY, (Exception) null));
        } catch (Exception e) {
            try {
                if (intent != null) {
                    sendBroadcast(intent.putExtra(CVE_2021_0472.EXCEPTION_MSG_KEY, e));
                }
            } catch (Exception ignored) {
                // Ignoring unintended exceptions.
            }
        }
    }
}
