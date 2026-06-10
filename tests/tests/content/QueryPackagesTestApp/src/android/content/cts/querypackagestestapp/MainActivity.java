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
package android.content.cts.querypackagestestapp;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.util.Log;

public class MainActivity extends Activity {
    private static final String EXTRA_REMOTE_CALLBACK = "extra_remote_callback";
    private static final String EXTRA_REMOTE_CALLBACK_RESULT = "extra_remote_callback_result";
    private static final String EMPTY_APP_PACKAGE_NAME = "android.content.cts.emptytestapp";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RemoteCallback remoteCallback = getIntent().getParcelableExtra(EXTRA_REMOTE_CALLBACK);
        if (remoteCallback != null) {
            ApplicationInfo info;
            try {
                info = getPackageManager().getApplicationInfo(
                        EMPTY_APP_PACKAGE_NAME,
                        PackageManager.MATCH_UNINSTALLED_PACKAGES);
            } catch (PackageManager.NameNotFoundException e) {
                info = null;
            }
            Log.i("MainActivity", "info = " + info);
            Bundle bundle = new Bundle();
            bundle.putString(EXTRA_REMOTE_CALLBACK_RESULT,
                    info == null ? "NOT FOUND" : info.packageName);
            remoteCallback.sendResult(bundle);
        }
        finish();
    }
}
