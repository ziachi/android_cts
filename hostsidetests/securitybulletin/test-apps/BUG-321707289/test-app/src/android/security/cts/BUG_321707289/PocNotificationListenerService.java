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

package android.security.cts.BUG_321707289;

import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;

public class PocNotificationListenerService extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        try {
            setSharedPreference(getString(R.string.testAppKey), getString(R.string.fail));
        } catch (Exception e) {
            try {
                String exceptionMessage = e.getMessage() != null ? e.getMessage() : "";
                setSharedPreference(getString(R.string.msgException), exceptionMessage);
            } catch (Exception ignored) {
                // Ignore exceptions here
            }
        }
    }

    private void setSharedPreference(String key, String value) throws Exception {
        // Set shared preference
        SharedPreferences sh =
                getSharedPreferences(getString(R.string.sharedPrefsName), MODE_PRIVATE);
        SharedPreferences.Editor edit = sh.edit();
        edit.putString(key, value);
        edit.commit();
    }
}
