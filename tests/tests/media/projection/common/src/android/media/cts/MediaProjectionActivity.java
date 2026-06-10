/*
 * Copyright (C) 2009 The Android Open Source Project
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
package android.media.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertTrue;

import android.annotation.NonNull;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.UiAutomatorUtils2;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

// This is a partial copy of android.view.cts.surfacevalidator.CapturedActivity.
// Common code should be move in a shared library

/** Start this activity to retrieve a MediaProjection through waitForMediaProjection() */
public class MediaProjectionActivity extends Activity {
    private static final int PERMISSION_CODE = 1;
    private static final int PERMISSION_DIALOG_WAIT_MS = 1000;
    private static final String TAG = "MediaProjectionActivity";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    // Builds from 24Q3 and earlier will have screen_share_mode_spinner, while builds from
    // 24Q4 onwards will have screen_share_mode_options, so need to check both options here
    private static final String SCREEN_SHARE_OPTIONS_REGEX =
            SYSTEM_UI_PACKAGE + ":id/screen_share_mode_(options|spinner)";

    // Extra used to specify a foreground service to use for the MediaProjection session.
    // If unset MediaProjection will default to the LocalMediaProjectionService implementation.
    public static final String EXTRA_FOREGROUND_SERVICE_CLASS = "extra_foreground_service_class";
    public static final String EXTRA_MP_CONFIG = "extra_mp_config";
    public static final String EXTRA_LAUNCH_COOKIE = "extra_launch_cookie";
    public static final String ACCEPT_RESOURCE_ID = "android:id/button1";
    public static final String CANCEL_RESOURCE_ID = "android:id/button2";
    public static final Pattern SCREEN_SHARE_OPTIONS_RES_PATTERN =
            Pattern.compile(SCREEN_SHARE_OPTIONS_REGEX);
    public static final String ENTIRE_SCREEN_STRING_RES_NAME =
            "screen_share_permission_dialog_option_entire_screen";
    public static final String SINGLE_APP_STRING_RES_NAME =
            "screen_share_permission_dialog_option_single_app";

    private boolean mHandleActivityResult = false;

    private MediaProjectionManager mProjectionManager;
    private MediaProjection mMediaProjection;
    private CountDownLatch mCountDownLatch;
    private boolean mProjectionServiceBound;

    private int mResultCode;
    private Intent mResultData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // UI automator need the screen ON in dismissPermissionDialog()
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mProjectionManager = getSystemService(MediaProjectionManager.class);
        mCountDownLatch = new CountDownLatch(1);

        startActivityForResult(createRequestIntent(), PERMISSION_CODE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mProjectionServiceBound) {
            mProjectionServiceBound = false;
        }
    }

    protected Intent getScreenCaptureIntent() {
        return mProjectionManager.createScreenCaptureIntent();
    }

    private Intent createRequestIntent() {
        if (getIntent().hasExtra(EXTRA_MP_CONFIG)) {
            MediaProjectionConfig config =
                    getIntent().getParcelableExtra(EXTRA_MP_CONFIG, MediaProjectionConfig.class);
            return mProjectionManager.createScreenCaptureIntent(config);
        }
        if (getIntent().hasExtra(EXTRA_LAUNCH_COOKIE)) {
            ActivityOptions.LaunchCookie launchCookie =
                    getIntent()
                            .getParcelableExtra(
                                    EXTRA_LAUNCH_COOKIE, ActivityOptions.LaunchCookie.class);
            return mProjectionManager.createScreenCaptureIntent(launchCookie);
        }

        return mProjectionManager.createScreenCaptureIntent();
    }

    /**
     * Request to start a foreground service with type "mediaProjection", it's free to run in either
     * the same process or a different process in the package; passing a messenger object to send
     * signal back when the foreground service is up.
     */
    private void startMediaProjectionService() {
        ForegroundServiceUtil.requestStartForegroundService(
                this, getForegroundServiceComponentName(), this::createMediaProjection, null);
    }

    /**
     * @return the Intent result from navigating the consent dialogs
     */
    public Intent getResultData() {
        return mResultData;
    }

    /**
     * @return The component name of the foreground service for this test.
     */
    private ComponentName getForegroundServiceComponentName() {
        if (getIntent().hasExtra(EXTRA_FOREGROUND_SERVICE_CLASS)) {
            String fgsClass = getIntent().getStringExtra(EXTRA_FOREGROUND_SERVICE_CLASS);
            return new ComponentName(this, fgsClass);
        }

        return new ComponentName(this, android.media.cts.LocalMediaProjectionService.class);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Only handle onActivityResult if the caller actually tries to start
        if (!mHandleActivityResult) {
            return;
        }

        if (requestCode != PERMISSION_CODE) {
            throw new IllegalStateException("Unknown request code: " + requestCode);
        }
        if (resultCode != RESULT_OK) {
            throw new IllegalStateException("User denied screen sharing permission");
        }
        Log.d(TAG, "onActivityResult");
        mResultCode = resultCode;
        mResultData = data;
        startMediaProjectionService();
    }

    private void createMediaProjection() {
        mMediaProjection = mProjectionManager.getMediaProjection(mResultCode, mResultData);
        mCountDownLatch.countDown();
    }

    /** Perform the steps required to pass the MediaProjection consent flow */
    public MediaProjection waitForMediaProjection() throws InterruptedException {
        mHandleActivityResult = true;
        final long timeOutMs = 10000;
        final int retryCount = 5;
        int count = 0;
        // Sometimes system decides to rotate the permission activity to another orientation
        // right after showing it. This results in: uiautomation thinks that accept button appears,
        // we successfully click it in terms of uiautomation, but nothing happens,
        // because permission activity is already recreated.
        // Thus, we try to click that button multiple times.
        do {
            assertTrue("Can't get the permission", count <= retryCount);
            dismissPermissionDialog(
                    /* isWatch= */ getPackageManager()
                            .hasSystemFeature(PackageManager.FEATURE_WATCH),
                    getResourceString(this, ENTIRE_SCREEN_STRING_RES_NAME));
            count++;
        } while (!mCountDownLatch.await(timeOutMs, TimeUnit.MILLISECONDS));
        return mMediaProjection;
    }

    /** The permission dialog will be auto-opened by the activity - find it and accept */
    public static void dismissPermissionDialog(
            boolean isWatch, @Nullable String entireScreenString) {
        // Ensure the device is initialized before interacting with any UI elements.
        UiDevice.getInstance(getInstrumentation());
        if (entireScreenString != null && !isWatch) {
            // if not testing on a watch device, then we need to select the entire screen option
            // before pressing "Start recording" button. This is because single app capture is
            // not supported on watches.
            if (!selectEntireScreenOption(entireScreenString)) {
                Log.e(TAG, "Couldn't select entire screen option");
            }
        }
        pressStartRecording();
    }

    @Nullable
    private static UiObject2 findUiObject(BySelector selector, UiSelector uiSelector) {
        // Check if the View can be found on the current screen.
        UiObject2 obj = waitForObject(selector);

        // If the View is not found on the current screen. Try scrolling around to find it.
        if (obj == null) {
            Log.w(TAG, "Couldn't find " + selector + ", now scrolling to it.");
            scrollToGivenResource(uiSelector);
            obj = waitForObject(selector);
        }
        if (obj == null) {
            Log.w(TAG, "Still couldn't find " + selector + ", now scrolling screen height.");
            try {
                obj = UiAutomatorUtils2.waitFindObjectOrNull(selector);
            } catch (UiObjectNotFoundException e) {
                Log.e(TAG, "Error in looking for " + selector, e);
            }
        }

        if (obj == null) {
            Log.e(TAG, "Unable to find " + selector);
        }

        return obj;
    }

    private static boolean selectEntireScreenOption(String entireScreenString) {
        UiObject2 optionSelector =
                findUiObject(
                        By.res(SCREEN_SHARE_OPTIONS_RES_PATTERN),
                        new UiSelector().resourceIdMatches(SCREEN_SHARE_OPTIONS_REGEX));
        if (optionSelector == null) {
            Log.e(
                    TAG,
                    "Couldn't find option selector to select projection mode, "
                            + "even after scrolling");
            return false;
        }
        optionSelector.click();

        UiDevice.getInstance(getInstrumentation())
                .waitForWindowUpdate(null, PERMISSION_DIALOG_WAIT_MS);
        UiObject2 entireScreenOption = waitForObject(By.text(entireScreenString));
        if (entireScreenOption == null) {
            Log.e(TAG, "Couldn't find entire screen option");
            return false;
        }
        entireScreenOption.click();
        return true;
    }

    /** Returns the string for the drop down option to capture the entire screen. */
    @Nullable
    public static String getResourceString(@NonNull Context context, String resName) {
        Resources sysUiResources;
        try {
            sysUiResources =
                    context.getPackageManager().getResourcesForApplication(SYSTEM_UI_PACKAGE);
        } catch (NameNotFoundException e) {
            return null;
        }
        int resourceId =
                sysUiResources.getIdentifier(resName, /* defType= */ "string", SYSTEM_UI_PACKAGE);
        if (resourceId == 0) {
            // Resource id not found
            return null;
        }
        return sysUiResources.getString(resourceId);
    }

    private static void pressStartRecording() {
        // May need to scroll down to the start button on small screen devices.
        UiObject2 startRecordingButton =
                findUiObject(
                        By.res(ACCEPT_RESOURCE_ID),
                        new UiSelector().resourceId(ACCEPT_RESOURCE_ID));
        if (startRecordingButton != null) {
            startRecordingButton.click();
        }
    }

    /** When testing on a small screen device, scrolls to a given UI element. */
    private static void scrollToGivenResource(UiSelector uiSelector) {
        // Scroll down the dialog; on a device with a small screen the elements may not be visible.
        final UiScrollable scrollable = new UiScrollable(new UiSelector().scrollable(true));
        try {
            if (!scrollable.scrollIntoView(uiSelector)) {
                Log.e(TAG, "Didn't find " + uiSelector + " when scrolling");
                return;
            }
            Log.d(TAG, "We finished scrolling down to the ui element " + uiSelector);
        } catch (UiObjectNotFoundException e) {
            Log.d(TAG, "There was no scrolling (UI may not be scrollable");
        }
    }

    private static UiObject2 waitForObject(BySelector selector) {
        UiDevice uiDevice = UiDevice.getInstance(getInstrumentation());
        return uiDevice.wait(Until.findObject(selector), PERMISSION_DIALOG_WAIT_MS);
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();
    }
}
