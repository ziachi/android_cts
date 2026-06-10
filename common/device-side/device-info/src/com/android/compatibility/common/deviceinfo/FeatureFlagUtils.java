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

package com.android.compatibility.common.deviceinfo;

import android.os.Build;
import android.os.flagging.AconfigPackage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.compatibility.common.util.SystemUtil;

/**
 * Utility methods for collecting feature flag information.
 */
public final class FeatureFlagUtils {

    private FeatureFlagUtils() {}

    /**
     * Check if a feature flag is enabled.
     *
     * @param packageName the name of the aconfig package
     * @param flagName the name of the feature flag
     *
     * @return whether the feature flag is enabled, or {@code null} if unknown
     */
    @Nullable
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    public static Boolean isFeatureFlagEnabled(@NonNull String packageName,
            @NonNull String flagName) {
        AconfigPackage aconfigPackage = AconfigPackage.load(packageName);
        boolean defaultTrueValue = aconfigPackage.getBooleanFlagValue(flagName, true);
        boolean defaultFalseValue = aconfigPackage.getBooleanFlagValue(flagName, false);
        if (defaultTrueValue != defaultFalseValue) {
            return null;
        }
        return defaultFalseValue;
    }

    /**
     * Check if a feature flag is enabled on V, before we had the {@link AconfigPackage} API.
     *
     * @param featureFlagName the name of the feature flag
     * @param mountPoint the mount point to check (e.g. {@code "system"}), or
     *                   {@code "device_config"} for device config
     *
     * @return whether the feature flag is enabled, or {@code null} if unknown
     */
    @Nullable
    public static Boolean isFeatureFlagEnabledOnV(@NonNull String featureFlagName,
            @NonNull String mountPoint) {
        final String[] lines = SystemUtil.runShellCommand("printflags").trim().split("\n");
        String featureFlag = "";
        for (String line : lines) {
            if (line.contains(featureFlagName)) {
                featureFlag = line;
                break;
            }
        }
        if (featureFlag.contains("ENABLED (" + mountPoint + ")")) {
            return true;
        } else if (featureFlag.contains("DISABLED (" + mountPoint + ")")) {
            return false;
        } else {
            return null;
        }
    }
}
