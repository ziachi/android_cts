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

package android.settings.cts;

import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;

/**
 * Utility methods for Settings tests
 */
public final class SettingsTestUtils {

    private SettingsTestUtils() {}

    /**
     * Checks whether the device is wear
     */
    public static boolean isWatch() {
        return InstrumentationRegistry.getTargetContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    /**
     * Checks whether the device is automotive
     */
    public static boolean isAutomotive() {
        return InstrumentationRegistry.getTargetContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /**
     * Checks whether the device is television
     */
    public static boolean isTelevision() {
        return InstrumentationRegistry.getTargetContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }
}
