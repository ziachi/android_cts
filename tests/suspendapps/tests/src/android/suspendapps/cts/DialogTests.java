/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.suspendapps.cts;

import static android.content.pm.SuspendDialogInfo.BUTTON_ACTION_UNSUSPEND;
import static android.suspendapps.cts.Constants.TEST_APP_PACKAGE_NAME;
import static android.suspendapps.cts.SuspendTestUtils.createExtras;
import static android.suspendapps.cts.SuspendTestUtils.startTestAppActivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.SuspendDialogInfo;
import android.os.Bundle;
import android.platform.test.rule.ScreenRecordRule;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class DialogTests {
    private static final String TAG = "DialogTests";
    private static final String TEST_APP_LABEL = "Suspend Test App";
    private static final long UI_TIMEOUT_MS = 20 * 1000L;

    /** Used to poll for the intents sent by the system to this package */
    static final SynchronousQueue<Intent> sIncomingIntent = new SynchronousQueue<>();

    @Rule
    public final ScreenRecordRule mScreenRecordRule = new ScreenRecordRule();

    private Context mContext;
    private TestAppInterface mTestAppInterface;
    private UiDevice mUiDevice;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mTestAppInterface = new TestAppInterface(mContext);
        turnScreenOn();
        SuspendTestUtils.unsuspendAll();
    }

    private void turnScreenOn() throws Exception {
        if (!mUiDevice.isScreenOn()) {
            mUiDevice.wakeUp();
        }
        SystemUtil.runShellCommandForNoOutput("wm dismiss-keyguard");
        mUiDevice.pressHome();
    }

    @Test
    @ScreenRecordRule.ScreenRecord
    public void testInterceptorActivity_unsuspend() throws Exception {
        final SuspendDialogInfo dialogInfo = makeDialogInfoWithUnsuspendButton();
        SuspendTestUtils.suspend(null, null, dialogInfo);
        // Ensure test app's activity is stopped before proceeding.
        assertTrue(mTestAppInterface.awaitTestActivityStop());

        final Bundle extrasForStart = new Bundle(createExtras("unsuspend", 90, "sval", 0.9));
        startTestAppActivity(extrasForStart);
        // Test activity should not start.
        assertNull("Test activity started while suspended",
                mTestAppInterface.awaitTestActivityStart(5_000));

        verifyDialogAndPressUnsuspend(mContext, mUiDevice);

        final Intent incomingIntent = sIncomingIntent.poll(30, TimeUnit.SECONDS);
        assertNotNull(incomingIntent);
        assertEquals(Intent.ACTION_PACKAGE_UNSUSPENDED_MANUALLY, incomingIntent.getAction());
        assertEquals("Did not receive correct unsuspended package name", TEST_APP_PACKAGE_NAME,
                incomingIntent.getStringExtra(Intent.EXTRA_PACKAGE_NAME));

        final Intent activityIntent = mTestAppInterface.awaitTestActivityStart();
        assertNotNull("Test activity did not start on neutral button tap", activityIntent);
        // TODO(b/237707107): Verify that activityIntent has the expected extras.
        assertFalse("Test package still suspended", mTestAppInterface.isTestAppSuspended());
    }

    public static SuspendDialogInfo makeDialogInfoWithUnsuspendButton() {
        return new SuspendDialogInfo.Builder()
                .setIcon(R.drawable.ic_settings)
                .setTitle(R.string.dialog_title)
                .setMessage(R.string.dialog_message)
                .setNeutralButtonText(R.string.unsuspend_button_text)
                .setNeutralButtonAction(BUTTON_ACTION_UNSUSPEND)
                .build();
    }

    public static void verifyDialogAndPressUnsuspend(Context context, UiDevice uiDevice)
            throws Exception {
        final String expectedTitle = context.getResources().getString(R.string.dialog_title);
        final String expectedButtonText = context.getResources().getString(
                R.string.unsuspend_button_text);

        assertNotNull("Given dialog title \"" + expectedTitle + "\" not shown",
                findObject(uiDevice, By.text(expectedTitle)));

        // Sometimes, button texts can have styles that override case (e.g. b/134033532)
        final Pattern buttonTextIgnoreCase = Pattern.compile(Pattern.quote(expectedButtonText),
                Pattern.CASE_INSENSITIVE);
        UiObject2 unsuspendButton = findObject(uiDevice,
                By.clickable(true).text(buttonTextIgnoreCase));
        assertNotNull("\"" + expectedButtonText + "\" button not shown", unsuspendButton);

        // Tapping on the neutral button should:
        // 1. Tell the suspending package that the test app was unsuspended
        // 2. Launch the previously intercepted intent
        // 3. Unsuspend the test app
        unsuspendButton.click();
    }

    @Test
    public void testInterceptorActivity_moreDetails() throws Exception {
        final SuspendDialogInfo dialogInfo = new SuspendDialogInfo.Builder()
                .setIcon(R.drawable.ic_settings)
                .setTitle(R.string.dialog_title)
                .setMessage(R.string.dialog_message)
                .setNeutralButtonText(R.string.more_details_button_text)
                .build();
        SuspendTestUtils.suspend(null, null, dialogInfo);
        // Ensure test app's activity is stopped before proceeding.
        assertTrue(mTestAppInterface.awaitTestActivityStop());

        startTestAppActivity(null);
        // Test activity should not start.
        assertNull("Test activity started while suspended",
                mTestAppInterface.awaitTestActivityStart(5_000));

        // The dialog should have correct specifications
        final String expectedTitle = mContext.getResources().getString(R.string.dialog_title);
        final String expectedMessage = mContext.getResources().getString(R.string.dialog_message,
                TEST_APP_LABEL);
        final String expectedButtonText = mContext.getResources().getString(
                R.string.more_details_button_text);

        assertNotNull("Given dialog title: " + expectedTitle + " not shown",
                findObject(mUiDevice, By.text(expectedTitle)));
        assertNotNull("Given dialog message: " + expectedMessage + " not shown",
                findObject(mUiDevice, By.text(expectedMessage)));
        // Sometimes, button texts can have styles that override case (e.g. b/134033532)
        final Pattern buttonTextIgnoreCase = Pattern.compile(Pattern.quote(expectedButtonText),
                Pattern.CASE_INSENSITIVE);
        final UiObject2 moreDetailsButton = findObject(mUiDevice,
                By.clickable(true).text(buttonTextIgnoreCase));
        assertNotNull(expectedButtonText + " button not shown", moreDetailsButton);

        // Tapping on the neutral button should start the correct intent.
        moreDetailsButton.click();
        final Intent incomingIntent = sIncomingIntent.poll(30, TimeUnit.SECONDS);
        assertNotNull(incomingIntent);
        assertEquals(Intent.ACTION_SHOW_SUSPENDED_APP_DETAILS, incomingIntent.getAction());
        assertEquals("Wrong package name sent with " + Intent.ACTION_SHOW_SUSPENDED_APP_DETAILS,
                TEST_APP_PACKAGE_NAME, incomingIntent.getStringExtra(Intent.EXTRA_PACKAGE_NAME));
    }

    @Test
    public void testInterceptorActivity_strings() throws Exception {
        // The dialog should have correct specifications
        final String expectedTitle = "Test Dialog Title";
        final String expectedMessage = "This is a test message";
        final String expectedButtonText = "Test button";

        final SuspendDialogInfo dialogInfo = new SuspendDialogInfo.Builder()
                .setIcon(R.drawable.ic_settings)
                .setTitle(expectedTitle)
                .setMessage(expectedMessage)
                .setNeutralButtonText(expectedButtonText)
                .build();
        SuspendTestUtils.suspend(null, null, dialogInfo);
        // Ensure test app's activity is stopped before proceeding.
        assertTrue(mTestAppInterface.awaitTestActivityStop());

        startTestAppActivity(null);
        // Test activity should not start.
        assertNull("Test activity started while suspended",
                mTestAppInterface.awaitTestActivityStart(5_000));


        assertNotNull("Given dialog title: " + expectedTitle + " not shown",
                findObject(mUiDevice, By.text(expectedTitle)));
        assertNotNull("Given dialog message: " + expectedMessage + " not shown",
                findObject(mUiDevice, By.text(expectedMessage)));
        // Sometimes, button texts can have styles that override case (e.g. b/134033532)
        final Pattern buttonTextIgnoreCase = Pattern.compile(Pattern.quote(expectedButtonText),
                Pattern.CASE_INSENSITIVE);
        final UiObject2 moreDetailsButton = findObject(mUiDevice,
                By.clickable(true).text(buttonTextIgnoreCase));
        assertNotNull(expectedButtonText + " button not shown", moreDetailsButton);
    }

    /**
     * Find the UiObject2 with the {@code bySelector} via {@code uidDevice}.
     */
    @Nullable
    public static UiObject2 findObject(UiDevice uiDevice, BySelector bySelector)
            throws Exception {
        uiDevice.waitForIdle();

        UiObject2 object = null;
        long endTime = System.currentTimeMillis() + UI_TIMEOUT_MS;
        while (endTime > System.currentTimeMillis()) {
            try {
                uiDevice.waitForIdle();
                object = uiDevice.wait(Until.findObject(bySelector), /* timeout= */ 2 * 1000);
                if (object != null) {
                    Log.d(TAG, "Found bounds: " + object.getVisibleBounds()
                            + " of object: " + bySelector + ", text: " + object.getText()
                            + " package: " + object.getApplicationPackage() + ", enabled: "
                            + object.isEnabled() + ", clickable: " + object.isClickable()
                            + ", contentDescription: " + object.getContentDescription()
                            + ", resourceName: " + object.getResourceName() + ", visibleCenter: "
                            + object.getVisibleCenter());
                    return object;
                } else {
                    // Maybe the screen is small. Scroll forward.
                    new UiScrollable(new UiSelector().scrollable(true)).scrollForward();
                }
            } catch (Exception ignored) {
                // do nothing
            }
        }

        // dump window hierarchy for debug
        if (object == null) {
            dumpWindowHierarchy(uiDevice);
        }

        return object;
    }

    /**
     * Dump current window hierarchy to help debug UI
     */
    public static void dumpWindowHierarchy(UiDevice uiDevice)
            throws InterruptedException, IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        uiDevice.dumpWindowHierarchy(outputStream);
        String windowHierarchy = outputStream.toString(StandardCharsets.UTF_8);

        Log.w(TAG, "Window hierarchy:");
        for (String line : windowHierarchy.split("\n")) {
            Thread.sleep(10);
            Log.w(TAG, line);
        }
    }

    @After
    public void tearDown() {
        if (mTestAppInterface != null) {
            mTestAppInterface.disconnect();
        }
        mUiDevice.pressBack();
        mUiDevice.pressHome();
    }
}
