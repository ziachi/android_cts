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

package android.gamemanager.cts.util;

import static android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static org.junit.Assert.assertEquals;

import android.app.GameManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;

/** Utilities for game features testing. */
public final class TestUtil {
    private static final String TAG = "GameTestUtil";

    // When an app is installed, some propagation work of the configuration will
    // be set up asynchronously, hence it is recommended to put the thread into sleep
    // to wait for the propagation finishes for a few hundred milliseconds.
    /** Installs an APK with the given {@code apkPath}. */
    public static void installPackage(@NonNull String apkPath) {
        assertEquals("Success", runShellCommand("pm install --force-queryable -t " + apkPath));
    }

    /** Uninstalls a package with the {@code packageName}. */
    public static void uninstallPackage(@NonNull String packageName) {
        runShellCommand("pm uninstall " + packageName);
    }

    /** Returns {@code true} if Game features need to be tested on the current device. */
    public static boolean shouldTestGameFeatures(Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return true;
        }
        if (context.getApplicationInfo().targetSdkVersion <= VANILLA_ICE_CREAM) {
            Log.d(TAG, "Skipping game test because of target SDK <= V.");
            return false;
        }
        if (context.getSystemService(GameManager.class) == null) {
            Log.d(TAG, "Skipping game test because there's no GameManager published.");
            return false;
        }
        return true;
    }
}
