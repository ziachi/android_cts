/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.autofillservice.cts.credman;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.credentials.GetCredentialResponse;
import androidx.credentials.PasswordCredential;
import androidx.credentials.provider.PendingIntentHandler;

/**
 * Activity that gets started from credential pending intent.
 */
public class CtsGetPasswordActivity extends Activity {

    private static final String TAG = "CtsGetPasswordActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate()");
        Intent intent = this.getIntent();
        String username = intent.getStringExtra("username");
        if (username != null) {
            Log.i(TAG, "Found username. Returning password credential to the caller.");
            Intent result = new Intent();
            PasswordCredential credential = new PasswordCredential(username, "defaultPassword");
            PendingIntentHandler.setGetCredentialResponse(
                    result, new GetCredentialResponse(credential));
            setResult(Activity.RESULT_OK, result);
        }
        this.finish();
    }
}
