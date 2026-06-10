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

package android.media.projection.cts.helper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Trampoline Activity used for testing (phone) call related features */
public class CallHelperActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, CallHelperService.class).setAction(getIntent().getAction()));
        if (CallHelperService.CALL_HELPER_STOP_CALL.equals(getIntent().getAction())) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, CallHelperService.class));
        super.onDestroy();
    }
}
