/**
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.accessibilityservice.cts.utils;

import static android.accessibility.cts.common.ShellCommandBuilder.execShellCommand;
import static android.accessibilityservice.cts.utils.AsyncUtils.DEFAULT_TIMEOUT_MS;
import static android.accessibilityservice.cts.utils.CtsTestUtils.isAutomotive;
import static android.content.pm.PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS;
import static android.os.UserHandle.USER_ALL;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.TestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Utilities useful when launching an activity to make sure it's all the way on the screen
 * before we start testing it.
 */
public class ActivityLaunchUtils {
    private static final String LOG_TAG = "ActivityLaunchUtils";
    private static final String AM_START_HOME_ACTIVITY_COMMAND =
            "am start -a android.intent.action.MAIN -c android.intent.category.HOME";
    // Close the system dialogs for all users
    public static final String AM_BROADCAST_CLOSE_SYSTEM_DIALOG_COMMAND =
            "am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS --user " + USER_ALL;
    public static final String INPUT_KEYEVENT_KEYCODE_BACK =
            "input keyevent KEYCODE_BACK";
    public static final String INPUT_KEYEVENT_KEYCODE_MENU =
            "input keyevent KEYCODE_MENU";

    // Precision when asserting the launched activity bounds equals the reported a11y window bounds.
    private static final int BOUNDS_PRECISION_PX = 1;

    // Using a static variable so it can be used in lambdas. Not preserving state in it.
    private static Activity mTempActivity;

    public static <T extends Activity> T launchActivityAndWaitForItToBeOnscreen(
            Instrumentation instrumentation, UiAutomation uiAutomation,
            ActivityTestRule<T> rule) throws Exception {
        ActivityLauncher activityLauncher = new ActivityLauncher() {
            @Override
            Activity launchActivity() {
                return rule.launchActivity(null);
            }
        };
        return launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen(instrumentation,
                uiAutomation, activityLauncher, Display.DEFAULT_DISPLAY);
    }

    /**
     * Launches an activity on a specified display and waits for it to be onscreen.
     *
     * <p>This method creates an intent to launch the specified activity class on the given display
     * ID. It ensures that the activity is launched in a new task and clears any existing task that
     * may be associated with the activity. The method also handles shell permissions required for
     * launching the activity.</p>
     *
     * @param instrumentation The instrumentation instance used to start the activity.
     * @param uiAutomation The UiAutomation instance used to manage UI interactions.
     * @param clazz The class of the activity to be launched.
     * @param displayId The ID of the display on which the activity should be launched.
     * @return An instance of the launched activity.
     * @throws Exception If there is an error launching the activity or waiting for it.
     */
    public static <T extends Activity> T launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen(
            Instrumentation instrumentation, UiAutomation uiAutomation, Class<T> clazz,
            int displayId) throws Exception {
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);
        final Intent intent = new Intent(instrumentation.getTargetContext(), clazz);
        // Add clear task because this activity may on other display.
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);

        ActivityLauncher activityLauncher = new ActivityLauncher() {
            @Override
            Activity launchActivity() {
                uiAutomation.adoptShellPermissionIdentity();
                try {
                    return instrumentation.startActivitySync(intent, options.toBundle());
                } finally {
                    uiAutomation.dropShellPermissionIdentity();
                }
            }
        };
        return launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen(instrumentation,
                uiAutomation, activityLauncher, displayId);
    }

    public static CharSequence getActivityTitle(
            Instrumentation instrumentation, Activity activity) {
        final StringBuilder titleBuilder = new StringBuilder();
        instrumentation.runOnMainSync(() -> titleBuilder.append(activity.getTitle()));
        return titleBuilder;
    }

    public static AccessibilityWindowInfo findWindowByTitle(
            UiAutomation uiAutomation, CharSequence title) {
        final List<AccessibilityWindowInfo> windows = uiAutomation.getWindows();
        return findWindowByTitleWithList(title, windows);
    }

    public static AccessibilityWindowInfo findWindowByTitleAndDisplay(
            UiAutomation uiAutomation, CharSequence title, int displayId) {
        final SparseArray<List<AccessibilityWindowInfo>> allWindows =
                uiAutomation.getWindowsOnAllDisplays();
        final List<AccessibilityWindowInfo> windowsOfDisplay = allWindows.get(displayId);
        return findWindowByTitleWithList(title, windowsOfDisplay);
    }

    public static void homeScreenOrBust(Context context, UiAutomation uiAutomation) {
        wakeUpOrBust(context, uiAutomation);
        if (context.getPackageManager().isInstantApp()) return;
        if (isHomeScreenShowing(context, uiAutomation)) return;
        final AccessibilityServiceInfo serviceInfo = uiAutomation.getServiceInfo();
        final int enabledFlags = serviceInfo.flags;
        // Make sure we could query windows.
        serviceInfo.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        uiAutomation.setServiceInfo(serviceInfo);
        try {
            KeyguardManager keyguardManager = context.getSystemService(KeyguardManager.class);
            if (keyguardManager != null) {
                TestUtils.waitUntil("Screen is unlocked",
                        (int) DEFAULT_TIMEOUT_MS / 1000,
                        () -> {
                            if (!keyguardManager.isKeyguardLocked()) {
                                return true;
                            }
                            execShellCommand(uiAutomation, INPUT_KEYEVENT_KEYCODE_MENU);
                            return false;
                        });
            }
            execShellCommand(uiAutomation, AM_START_HOME_ACTIVITY_COMMAND);
            execShellCommand(uiAutomation, AM_BROADCAST_CLOSE_SYSTEM_DIALOG_COMMAND);
            execShellCommand(uiAutomation, INPUT_KEYEVENT_KEYCODE_BACK);
            TestUtils.waitUntil("Home screen is showing",
                    (int) DEFAULT_TIMEOUT_MS / 1000,
                    () -> {
                        if (isHomeScreenShowing(context, uiAutomation)) {
                            return true;
                        }
                        // Attempt to close any newly-appeared system dialogs which can prevent the
                        // home screen activity from becoming visible, active, and focused.
                        execShellCommand(uiAutomation, AM_BROADCAST_CLOSE_SYSTEM_DIALOG_COMMAND);
                        execShellCommand(uiAutomation, INPUT_KEYEVENT_KEYCODE_BACK);
                        execShellCommand(uiAutomation, AM_START_HOME_ACTIVITY_COMMAND);
                        return false;
                    });
        } catch (Exception error) {
            Log.e(LOG_TAG, "Timed out looking for home screen. Dumping window list");
            final List<AccessibilityWindowInfo> windows = uiAutomation.getWindows();
            if (windows == null) {
                Log.e(LOG_TAG, "Window list is null");
            } else if (windows.isEmpty()) {
                Log.e(LOG_TAG, "Window list is empty");
            } else {
                for (AccessibilityWindowInfo window : windows) {
                    Log.e(LOG_TAG, window.toString());
                }
            }

            fail("Unable to reach home screen");
        } finally {
            serviceInfo.flags = enabledFlags;
            uiAutomation.setServiceInfo(serviceInfo);
        }
    }

    public static boolean supportsMultiDisplay(Context context) {
        return context.getPackageManager().hasSystemFeature(
                FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS);
    }

    public static boolean isHomeScreenShowing(Context context, UiAutomation uiAutomation) {
        final List<AccessibilityWindowInfo> windows = uiAutomation.getWindows();
        final PackageManager packageManager = context.getPackageManager();
        final List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        final boolean isAuto = isAutomotive(context);

        // Look for an active focused window with a package name that matches
        // the default home screen.
        for (AccessibilityWindowInfo window : windows) {
            if (!isAuto) {
                // Auto does not set its home screen app as active+focused, so only non-auto
                // devices enforce that the home screen is active+focused.
                if (!window.isActive() || !window.isFocused()) {
                    continue;
                }
            }
            final AccessibilityNodeInfo root = window.getRoot();
            if (root != null) {
                final CharSequence packageName = root.getPackageName();
                if (packageName != null) {
                    for (ResolveInfo resolveInfo : resolveInfos) {
                        if ((resolveInfo.activityInfo != null)
                                && packageName.equals(resolveInfo.activityInfo.packageName)) {
                            return true;
                        }
                    }
                }
            }
        }
        // List unexpected package names of default home screen that invoking ResolverActivity
        final CharSequence homePackageNames = resolveInfos.stream()
                .map(r -> r.activityInfo).filter(Objects::nonNull)
                .map(a -> a.packageName).collect(Collectors.joining(", "));
        Log.v(LOG_TAG, "No window matched with package names of home screen: " + homePackageNames);
        return false;
    }

    private static void wakeUpOrBust(Context context, UiAutomation uiAutomation) {
        final long deadlineUptimeMillis = SystemClock.uptimeMillis() + DEFAULT_TIMEOUT_MS;
        final PowerManager powerManager = context.getSystemService(PowerManager.class);
        do {
            if (powerManager.isInteractive()) {
                Log.d(LOG_TAG, "Device is interactive");
                return;
            }

            Log.d(LOG_TAG, "Sending wakeup keycode");
            final long eventTime = SystemClock.uptimeMillis();
            uiAutomation.injectInputEvent(
                    new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_WAKEUP, 0 /* repeat */, 0 /* metastate */,
                            KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /* scancode */, 0 /* flags */,
                            InputDevice.SOURCE_KEYBOARD), true /* sync */);
            uiAutomation.injectInputEvent(
                    new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP,
                            KeyEvent.KEYCODE_WAKEUP, 0 /* repeat */, 0 /* metastate */,
                            KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /* scancode */, 0 /* flags */,
                            InputDevice.SOURCE_KEYBOARD), true /* sync */);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
        } while (SystemClock.uptimeMillis() < deadlineUptimeMillis);
        fail("Unable to wake up screen");
    }

    private static <T extends Activity> T launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen(
            Instrumentation instrumentation, UiAutomation uiAutomation,
            ActivityLauncher activityLauncher, int displayId) throws Exception {
        final int[] location = new int[2];
        final StringBuilder activityPackage = new StringBuilder();
        final Rect bounds = new Rect();
        final StringBuilder activityTitle = new StringBuilder();
        final StringBuilder timeoutExceptionRecords = new StringBuilder();
        // Make sure we get window events, so we'll know when the window appears
        AccessibilityServiceInfo info = uiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        uiAutomation.setServiceInfo(info);
        // There is no any window on virtual display even doing GLOBAL_ACTION_HOME, so only
        // checking the home screen for default display.
        if (displayId == Display.DEFAULT_DISPLAY) {
            homeScreenOrBust(instrumentation.getContext(), uiAutomation);
        }

        try {
            final AccessibilityEvent awaitedEvent = uiAutomation.executeAndWaitForEvent(
                    () -> {
                        mTempActivity = activityLauncher.launchActivity();
                        instrumentation.runOnMainSync(() -> {
                            mTempActivity.getWindow().getDecorView().getLocationOnScreen(location);
                            activityPackage.append(mTempActivity.getPackageName());
                        });
                        instrumentation.waitForIdleSync();
                        activityTitle.append(getActivityTitle(instrumentation, mTempActivity));
                    },
                    (event) -> {
                        final AccessibilityWindowInfo window =
                                findWindowByTitleAndDisplay(uiAutomation, activityTitle, displayId);
                        if (window == null || window.getRoot() == null
                                // Ignore the active & focused check for virtual displays,
                                // which don't get focused on launch.
                                || (displayId == Display.DEFAULT_DISPLAY
                                    && (!window.isActive() || !window.isFocused()))) {
                            // Attempt to close any system dialogs which can prevent the launched
                            // activity from becoming visible, active, and focused.
                            execShellCommand(uiAutomation,
                                    AM_BROADCAST_CLOSE_SYSTEM_DIALOG_COMMAND);
                            return false;
                        }

                        window.getBoundsInScreen(bounds);
                        mTempActivity.getWindow().getDecorView().getLocationOnScreen(location);

                        // Stores the related information including event, location and window
                        // as a timeout exception record.
                        timeoutExceptionRecords.append(String.format("{Received event: %s \n"
                                        + "Window location: %s \nA11y window: %s}\n",
                                event, Arrays.toString(location), window));

                        return (!bounds.isEmpty())
                                && Math.abs(bounds.left - location[0]) <= BOUNDS_PRECISION_PX
                                && Math.abs(bounds.top - location[1]) <= BOUNDS_PRECISION_PX;
                    }, DEFAULT_TIMEOUT_MS);
            assertNotNull(awaitedEvent);
        } catch (TimeoutException timeout) {
            throw new TimeoutException(timeout.getMessage() + "\n\nTimeout exception records : \n"
                    + timeoutExceptionRecords);
        }
        instrumentation.waitForIdleSync();
        return (T) mTempActivity;
    }

    public static AccessibilityWindowInfo findWindowByTitleWithList(CharSequence title,
            List<AccessibilityWindowInfo> windows) {
        AccessibilityWindowInfo returnValue = null;
        if (windows != null && windows.size() > 0) {
            for (int i = 0; i < windows.size(); i++) {
                final AccessibilityWindowInfo window = windows.get(i);
                if (TextUtils.equals(title, window.getTitle())) {
                    returnValue = window;
                } else {
                    window.recycle();
                }
            }
        }
        return returnValue;
    }

    private static abstract class ActivityLauncher {
        abstract Activity launchActivity();
    }
}
