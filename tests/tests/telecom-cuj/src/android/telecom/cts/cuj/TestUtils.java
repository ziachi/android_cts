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

package android.telecom.cts.cuj;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.Log;

public class TestUtils {
    static final String TAG = "TelecomCtsCujTests";

    public static boolean hasTelephonyFeature(Context context) {
        final PackageManager pm = context.getPackageManager();
        return (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) && pm.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_CALLING));
    }

    /**
     * @param context the context
     * @return {@code true} if the device supports a dialer on it, {@code false} otherwise.
     */
    public static boolean hasDialerRole(Context context) {
        final RoleManager rm = context.getSystemService(RoleManager.class);
        return (rm.isRoleAvailable(RoleManager.ROLE_DIALER));
    }

    /**
     * Gets the {@link com.android.internal.R.bool#config_ringtoneVibrationSettingsSupported} value.
     * @return {@code true} If the device supports ringtone vibration settings.
     */
    public static boolean isRingtoneVibrationSupported(Context context) {
        try {
            int resId = Resources.getSystem().getIdentifier(
                    "config_ringtoneVibrationSettingsSupported", "bool", "android");
            return context.getResources().getBoolean(resId);
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "Unable to read system resource " + e.getMessage());
            return false;
        }
    }
}
