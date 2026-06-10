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

package android.security.cts.overlay_request_manage_credentials;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class PocActivity extends Activity {
    private int mExpectedConfigOrientation;
    private static final String KEY_EXPECTED_ORIENTATION = "expectedConfigOrientation";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            if (savedInstanceState != null) {
                mExpectedConfigOrientation = savedInstanceState.getInt(KEY_EXPECTED_ORIENTATION);
            } else {
                mExpectedConfigOrientation = ORIENTATION_LANDSCAPE;
                int requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE;
                if (getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE) {
                    requestedOrientation = SCREEN_ORIENTATION_PORTRAIT;
                    mExpectedConfigOrientation = ORIENTATION_PORTRAIT;
                }
                setRequestedOrientation(requestedOrientation);
            }

            if (getResources().getConfiguration().orientation == mExpectedConfigOrientation) {
                sendBroadcast(
                        new Intent(getString(R.string.broadcastAction))
                                .putExtra(getString(R.string.keyPocActivity), true)
                                .setPackage(getPackageName()));
            }
        } catch (Exception e) {
            notifyExceptionToDeviceTest(e);
        }
    }

    protected void onSaveInstanceState(Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
            outState.putInt(KEY_EXPECTED_ORIENTATION, mExpectedConfigOrientation);
        } catch (Exception e) {
            notifyExceptionToDeviceTest(e);
        }
    }

    private void notifyExceptionToDeviceTest(Exception e) {
        try {
            // Send a broadcast to cause assumption failure in DeviceTest
            sendBroadcast(
                    new Intent(getString(R.string.broadcastAction))
                            .putExtra(getString(R.string.keyException), e)
                            .putExtra(getString(R.string.keyPocActivity), true)
                            .setPackage(getPackageName()));
        } catch (Exception ex) {
            // ignore
        }
    }
}
