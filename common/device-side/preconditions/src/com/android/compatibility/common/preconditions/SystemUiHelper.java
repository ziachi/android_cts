/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.common.preconditions;

import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * SystemUiHelper is used to check SystemUI related status such as whether or not a
 * traditional status bar is supported on this device platform.
 */
public class SystemUiHelper {
    /**
     * This helper returns true if the device's system UI does not have a traditional android
     * system status bar.  E.g. Android Automotive, WearOS, etc.
     */
    public static boolean hasNoTraditionalStatusBar(Context context) {
        PackageManager packageManager = context.getPackageManager();
        boolean isTelevision = packageManager != null
                && (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION));

        boolean isWatch = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_WATCH);

        return isTelevision || isWatch || Boolean.getBoolean(
                "persist.sysui.nostatusbar");

    }

    /**
     * Checks whether the device has non-overlapping multitasking feature enabled.
     *
     * When this is true, we expect the Task to not occlude other Task below it,
     * which means both Tasks can be resumed and visible.
     */
    public static boolean isNonOverlappingMultiWindowMode(Activity activity) {
        if (!activity.isInMultiWindowMode()) {
            return false;
        }
        Context context = activity.getApplicationContext();
        if (context.getPackageManager().hasSystemFeature(/* PackageManager
        .FEATURE_CAR_SPLITSCREEN_MULTITASKING */
                "android.software.car.splitscreen_multitasking")
                && context.getPackageManager().hasSystemFeature(FEATURE_AUTOMOTIVE)) {
            // Automotive SplitScreen Multitasking devices overlap the windows.
            return false;
        }
        return true;
    }
}
