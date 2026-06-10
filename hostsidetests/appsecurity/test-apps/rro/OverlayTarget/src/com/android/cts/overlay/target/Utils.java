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

package com.android.cts.overlay.target;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.os.UserHandle;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import java.util.concurrent.TimeUnit;

public class Utils {
    // Overlay states
    private static final String STATE_DISABLED = "STATE_DISABLED";
    private static final String STATE_ENABLED = "STATE_ENABLED";
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);

    /**
     * A util function that is commonly used in app overlay scenarios to enable or disable a RRO.
     */
    public static void setOverlayEnabled(String overlayPackage, boolean enabled)
            throws Exception {
        final String current = getStateForOverlay(overlayPackage);
        final String expected = enabled ? STATE_ENABLED : STATE_DISABLED;
        assertThat(current).isNotEqualTo(expected);
        SystemUtil.runShellCommand("cmd overlay "
                + (enabled ? "enable" : "disable")
                + " --user current "
                + overlayPackage);
        PollingCheck.check("Fail to wait overlay enabled state " + expected
                        + " for " + overlayPackage, TIMEOUT_MS,
                () -> expected.equals(getStateForOverlay(overlayPackage)));
    }

    private static String getStateForOverlay(String overlayPackage) {
        final String errorMsg = "Fail to parse the state of overlay package " + overlayPackage;
        final String result = SystemUtil.runShellCommand("cmd overlay dump");
        final String overlayPackageForCurrentUser = overlayPackage + ":" + UserHandle.myUserId();
        final int startIndex = result.indexOf(overlayPackageForCurrentUser);
        assertWithMessage(errorMsg).that(startIndex).isAtLeast(0);

        final int endIndex = result.indexOf('}', startIndex);
        assertWithMessage(errorMsg).that(endIndex).isGreaterThan(startIndex);

        final int stateIndex = result.indexOf("mState", startIndex);
        assertWithMessage(errorMsg).that(startIndex).isLessThan(stateIndex);
        assertWithMessage(errorMsg).that(stateIndex).isLessThan(endIndex);

        final int colonIndex = result.indexOf(':', stateIndex);
        assertWithMessage(errorMsg).that(stateIndex).isLessThan(colonIndex);
        assertWithMessage(errorMsg).that(colonIndex).isLessThan(endIndex);

        final int endLineIndex = result.indexOf('\n', colonIndex);
        assertWithMessage(errorMsg).that(colonIndex).isLessThan(endLineIndex);
        assertWithMessage(errorMsg).that(endLineIndex).isLessThan(endIndex);

        return result.substring(colonIndex + 2, endLineIndex);
    }
}
