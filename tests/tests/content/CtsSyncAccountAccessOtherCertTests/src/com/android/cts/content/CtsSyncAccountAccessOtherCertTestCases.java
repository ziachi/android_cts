/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.content;

import static com.android.cts.content.Utils.ALWAYS_SYNCABLE_AUTHORITY;
import static com.android.cts.content.Utils.SYNC_TIMEOUT_MILLIS;
import static com.android.cts.content.Utils.allowSyncAdapterRunInBackgroundAndDataInBackground;
import static com.android.cts.content.Utils.disallowSyncAdapterRunInBackgroundAndDataInBackground;
import static com.android.cts.content.Utils.getUiDevice;
import static com.android.cts.content.Utils.hasDataConnection;
import static com.android.cts.content.Utils.hasNotificationSupport;
import static com.android.cts.content.Utils.isWatch;
import static com.android.cts.content.Utils.requestSync;
import static com.android.cts.content.Utils.withAccount;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncRequest;
import android.content.res.Configuration;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

/**
 * Tests whether a sync adapter can access accounts.
 */
@Ignore("This will be re-enabled upon further investigation (b/406640881)")
@RunWith(AndroidJUnit4.class)
public final class CtsSyncAccountAccessOtherCertTestCases {
    private static final long UI_TIMEOUT_MILLIS = 5000; // 5 sec
    private static final String LOG_TAG =
            CtsSyncAccountAccessOtherCertTestCases.class.getSimpleName();

    private static final Pattern PERMISSION_REQUESTED = Pattern.compile(
            "Permission Requested.*|Permission requested.*");
    private static final Pattern ALLOW_SYNC = Pattern.compile("ALLOW|Allow");

    @Rule
    public final TestRule mFlakyTestRule = new FlakyTestRule(3);

    @Rule
    public final ActivityTestRule<StubActivity> activity = new ActivityTestRule(StubActivity.class);

    @Before
    public void setUp() throws Exception {
        allowSyncAdapterRunInBackgroundAndDataInBackground();
    }

    @After
    public void tearDown() throws Exception {
        disallowSyncAdapterRunInBackgroundAndDataInBackground();
    }

    @Test
    public void testAccountAccess_otherCertAsAuthenticatorCanNotSeeAccount() throws Exception {
        assumeTrue("Device requires a data connection", hasDataConnection());
        assumeTrue("Device requires notification support", hasNotificationSupport());
        assumeFalse("Device cannot run in VR", isRunningInVR());
        assumeFalse("Device cannot be a watch", isWatch());

        // If running in a test harness, Account Manager never denies access to an account.
        // Hence, the permission request will not trigger. b/72114924
        assumeFalse("Device cannot be running in a test harness",
                ActivityManager.isRunningInUserTestHarness());

        UiDevice uiDevice = getUiDevice();
        try (AutoCloseable ignored = withAccount(activity.getActivity())) {
            AbstractThreadedSyncAdapter mockAdapter = AlwaysSyncableSyncService.getInstance(
                    activity.getActivity()).setNewDelegate();

            SyncRequest request = requestSync(ALWAYS_SYNCABLE_AUTHORITY);
            Log.i(LOG_TAG, "Sync requested " + request);

            Thread.sleep(SYNC_TIMEOUT_MILLIS);
            verify(mockAdapter, never()).onPerformSync(any(), any(), any(), any(), any());
            Log.i(LOG_TAG, "Did not get onPerformSync");

            uiDevice.openNotification();
            UiObject2 permissionRequest = uiDevice.wait(
                    Until.findObject(By.text(PERMISSION_REQUESTED)), UI_TIMEOUT_MILLIS);
            if (permissionRequest == null) {
                UiObject2 scrollable = uiDevice.findObject(By.scrollable(true));
                if (scrollable != null) {
                    permissionRequest = scrollable.scrollUntil(
                            Direction.DOWN, Until.findObject(By.text(PERMISSION_REQUESTED)));
                }
            }
            assumeTrue("Couldn't find permission request to allow sync", permissionRequest != null);
            permissionRequest.click();

            UiObject2 allowSyncButton = uiDevice.wait(
                    Until.findObject(By.text(ALLOW_SYNC)), UI_TIMEOUT_MILLIS);
            assumeTrue("Couldn't find button to allow sync", allowSyncButton != null);
            allowSyncButton.click();

            ContentResolver.requestSync(request);
            verify(mockAdapter, timeout(SYNC_TIMEOUT_MILLIS))
                    .onPerformSync(any(), any(), any(), any(), any());
            Log.i(LOG_TAG, "Got onPerformSync");
        }
    }

    private boolean isRunningInVR() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return ((context.getResources().getConfiguration().uiMode &
                 Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_VR_HEADSET);
    }
}
