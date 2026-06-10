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

package com.android.server.cts.device.statsdatom;


import static org.junit.Assume.assumeFalse;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.util.regex.Pattern;

public class MediaProjectionTests {
    private static final String TAG = "MediaProjectionTests";

    private static final Long TIMEOUT = 5000L;
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String ACCEPT_RESOURCE_ID = "android:id/button1";
    private static final String CANCEL_RESOURCE_ID = "android:id/button2";
    private static final String MEDIA_PROJECTION_CONSENT_DIALOG =
            SYSTEM_UI_PACKAGE + ":id/screen_share_permission_dialog";

    // Builds from 24Q3 and earlier will have screen_share_mode_spinner, while builds from
    // 24Q4 onwards will have screen_share_mode_options, so need to check both options here
    private static final Pattern SCREEN_SHARE_OPTIONS_RES_PATTERN =
            Pattern.compile(SYSTEM_UI_PACKAGE + ":id/screen_share_mode_(options|spinner)");

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final UiDevice mDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private static String sSingleAppString;

    public static class MediaProjectionActivity extends Activity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            MediaProjectionManager service = getSystemService(MediaProjectionManager.class);
            startActivityForResult(service.createScreenCaptureIntent(), 0);
        }
    }

    @Rule
    public ActivityTestRule<MediaProjectionActivity> mActivityRule =
            new ActivityTestRule<>(MediaProjectionActivity.class, false, false);


    /** Get relevant text strings from SysUI Resources */
    @BeforeClass
    public static void setUp() throws PackageManager.NameNotFoundException {
        Resources sysUiResources;
        sysUiResources = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager().getResourcesForApplication(SYSTEM_UI_PACKAGE);

        final String singleAppResName = "screen_share_permission_dialog_option_single_app";

        int singleAppResId = sysUiResources.getIdentifier(
                singleAppResName, "string", SYSTEM_UI_PACKAGE);

        sSingleAppString = sysUiResources.getString(singleAppResId);
    }

    @After
    public void tearDown() {
        mActivityRule.finishActivity();
    }

    @Test
    public void testMediaProjectionPermissionDialogCancel() {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));

        mActivityRule.launchActivity(null);
        mDevice.waitForIdle();

        UiObject2 consentDialog = mDevice.wait(
                Until.findObject(By.res(MEDIA_PROJECTION_CONSENT_DIALOG)), TIMEOUT);
        UiObject2 cancelButton =
                consentDialog.scrollUntil(
                        Direction.DOWN, Until.findObject(By.res(CANCEL_RESOURCE_ID)));
        mDevice.waitForIdle();
        cancelButton.click();
        mDevice.wait(Until.gone(By.res(MEDIA_PROJECTION_CONSENT_DIALOG)), TIMEOUT);
    }

    @Test
    public void testMediaProjectionShowAppSelector() {
        assumeFalse(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));

        mActivityRule.launchActivity(null);
        mDevice.waitForIdle();

        // OEMs aren't guaranteed to support partial screenshare, so we only attempt
        // to reach the app selector if possible, and end the test prematurely if it isn't
        boolean hasModeSpinner = mDevice.hasObject(By.res(SCREEN_SHARE_OPTIONS_RES_PATTERN));
        if (!hasModeSpinner) {
            Log.i(TAG, "Unable to find a screen share mode spinner");
            return;
        }

        UiObject2 modeSpinner =
                mDevice.wait(Until.findObject(By.res(SCREEN_SHARE_OPTIONS_RES_PATTERN)), TIMEOUT);
        modeSpinner.click();

        boolean hasSingleAppOption = mDevice.hasObject(By.text(sSingleAppString));
        if (!hasSingleAppOption) {
            Log.i(TAG, "Unable to find single app option in spinner");
            return;
        }

        UiObject2 singleAppOption =
                mDevice.wait(Until.findObject(By.text(sSingleAppString)), TIMEOUT);
        singleAppOption.click();

        // Go to app selector page
        UiObject2 consentDialog = mDevice.wait(
                Until.findObject(By.res(MEDIA_PROJECTION_CONSENT_DIALOG)), TIMEOUT);
        UiObject2 startRecordingButton =
                consentDialog.scrollUntil(
                        Direction.DOWN, Until.findObject(By.res(ACCEPT_RESOURCE_ID)));
        startRecordingButton.click();
        mDevice.wait(Until.gone(By.res(MEDIA_PROJECTION_CONSENT_DIALOG)), TIMEOUT);
    }
}
