/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.server.wm;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;

import static junit.framework.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UserHelper;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A session to enable/disable the feature to ignore
 * {@link android.app.Activity#setRequestedOrientation(int)}
 */
public class IgnoreOrientationRequestSession implements AutoCloseable {
    private static final String TAG = "IgnoreOrientationRequestSession";
    private static final String WM_SET_IGNORE_ORIENTATION_REQUEST =
            "wm set-ignore-orientation-request ";
    private static final String WM_GET_IGNORE_ORIENTATION_REQUEST =
            "wm get-ignore-orientation-request ";
    private static final Pattern IGNORE_ORIENTATION_REQUEST_PATTERN =
            Pattern.compile("ignoreOrientationRequest (true|false) for displayId=\\d+");

    private static int sDisplayId = INVALID_DISPLAY;

    final boolean mInitialIgnoreOrientationRequest;

    public IgnoreOrientationRequestSession(boolean enable) {
        mInitialIgnoreOrientationRequest = getIgnoreOrientationRequest();

        final int displayId = getDisplayId();
        Log.i(TAG, "Set enable=" + enable + " initialState=" + mInitialIgnoreOrientationRequest
                + " displayId=" + displayId);
        executeShellCommand(WM_SET_IGNORE_ORIENTATION_REQUEST + "-d " + displayId + " "
                + (enable ? "true" : "false"));
    }

    @Override
    public void close() {
        executeShellCommand(WM_SET_IGNORE_ORIENTATION_REQUEST + "-d " + getDisplayId() + " "
                + mInitialIgnoreOrientationRequest);
    }

    /** Gets whether ignore-orientation-request is enabled on default display. */
    public static boolean getIgnoreOrientationRequest() {
        final Matcher matcher = IGNORE_ORIENTATION_REQUEST_PATTERN.matcher(
                executeShellCommand(WM_GET_IGNORE_ORIENTATION_REQUEST + "-d " + getDisplayId()));
        assertTrue("get-ignore-orientation-request should match pattern", matcher.find());
        return Boolean.parseBoolean(matcher.group(1));
    }

    /** Resets ignore-orientation-request to default behavior. */
    public static void resetIgnoreOrientationRequest() {
        Log.i(TAG, executeShellCommand(WM_SET_IGNORE_ORIENTATION_REQUEST + "-d " + getDisplayId()
                + " reset"));
    }

    private static int getDisplayId() {
        if (sDisplayId == INVALID_DISPLAY) {
            sDisplayId = DEFAULT_DISPLAY;
            final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            final PackageManager pm = context.getPackageManager();
            final boolean isAutomotive = pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
            if (isAutomotive) {
                final UserHelper userHelper = new UserHelper(context);
                sDisplayId = userHelper.getMainDisplayId();
            }
        }
        return sDisplayId;
    }

    private static String executeShellCommand(String command) {
        try {
            return SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                    command);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
