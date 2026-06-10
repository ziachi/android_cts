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

package android.credentials.cts.testcore;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.DeviceConfig;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.DeviceConfigStateManager;

/**
 * Helper class for Credential Manager Cts tests
 */
public class CtsCredentialManagerUtils {
    public static final String DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER =
            "enable_credential_manager";

    /** Whether the device has the watch feature or not **/
    public static boolean isWatch(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    /** Whether the device has the auto feature or not **/
    public static boolean isAuto(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /** Whether the device has the tv feature or not **/
    public static boolean isTv(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    /**
     * Enable the main credential manager feature. If this is off, any underlying changes for
     * autofill-credentialManager integrations are off.
     */
    public static void enableCredentialManagerDeviceConfigFlag(@NonNull Context context) {
        setCredentialManagerFeature(context, true);
    }

    public static void disableCredentialManagerDeviceFeature(@NonNull Context context) {
        setCredentialManagerFeature(context, false);
    }

    /** Enable Credential Manager related autofill changes */
    public static void setCredentialManagerFeature(@NonNull Context context, boolean enabled) {
        setDeviceConfig(context, DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER, enabled);
    }

    /** Set device config to set flag values. */
    public static void setDeviceConfig(
            @NonNull Context context, @NonNull String feature, boolean value) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_CREDENTIAL, feature);
        setDeviceConfig(deviceConfigStateManager, String.valueOf(value));
    }

    /** Set device config. */
    public static void setDeviceConfig(
            @NonNull DeviceConfigStateManager deviceConfigStateManager, @Nullable String value) {
        final String previousValue = deviceConfigStateManager.get();
        if (TextUtils.isEmpty(value) && TextUtils.isEmpty(previousValue)
                || TextUtils.equals(previousValue, value)) {
            return;
        }
        deviceConfigStateManager.set(value);
    }
}
