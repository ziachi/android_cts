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

package com.android.server.cts.device.statsdatom.appcompat;


import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.server.cts.device.statsdatom.StatsdCtsForegroundActivity.ACTION_LONG_SLEEP_WHILE_TOP;
import static com.android.server.cts.device.statsdatom.StatsdCtsForegroundActivity.KEY_ACTION;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.Settings;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.DisableAnimationRule;
import com.android.compatibility.common.util.NonApiTest;
import com.android.server.cts.device.statsdatom.MinAspectRatioPortraitActivity;
import com.android.server.cts.device.statsdatom.StatsdCtsMinAspectRatioPortraitActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class AppCompatTests {
    private static final String TAG = "AppCompatTests";
    private static final long FIND_TIMEOUT = 5000L;
    private static final int BOUNDS_OFFSET = 155;
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String TEST_PKG = "android.server.wm.allowuseraspectratiooverrideoptin";
    private final UiDevice mDevice =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private final GestureHelper mGestureHelper =
            new GestureHelper(InstrumentationRegistry.getInstrumentation());

    @ClassRule
    public static DisableAnimationRule sDisableAnimationRule = new DisableAnimationRule();

    @Before
    public void setUp() throws RemoteException {
        mDevice.setOrientationNatural();
    }

    @After
    public void tearDown() throws RemoteException {
        mDevice.setOrientationNatural();
    }

    /**
     * Helper test to trigger size compat mode to log SizeCompatRestartButtonEventReported atoms.
     */
    @NonApiTest(exemptionReasons = {}, justification = "METRIC")
    @Test
    public void testClickSizeCompatRestartButton() throws RemoteException {
        final Context context = getApplicationContext();
        final Intent intent = new Intent(context, MinAspectRatioPortraitActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        mDevice.wait(Until.hasObject(By.clazz(MinAspectRatioPortraitActivity.class)), FIND_TIMEOUT);

        mDevice.setOrientationLeft();
        mDevice.waitForIdle();

        UiObject2 sizeCompatRestartButton = mDevice.wait(Until.findObject(
                By.res(SYSTEM_UI_PACKAGE, "size_compat_restart_button")), FIND_TIMEOUT);
        // Can't enforce existence of size compat restart button, exit early.
        assumeNotNull(sizeCompatRestartButton);
        sizeCompatRestartButton.click();
        mDevice.waitForIdle();

        UiObject2 confirmRestart = mDevice.wait(Until.findObject(
                By.res(SYSTEM_UI_PACKAGE, "letterbox_restart_dialog_restart_button")),
                FIND_TIMEOUT);
        // If confirm button is not found, restart dialog must have been disabled in configuration.
        // Exit early as click metric for restart button is already triggered.
        assumeNotNull(confirmRestart);
        confirmRestart.click();
    }

    /**
     * Helper test to reposition app horizontally to log LetterboxPositionChanged atoms.
     */
    @NonApiTest(exemptionReasons = {}, justification = "METRIC")
    @Test
    public void testHorizontalReachability() throws RemoteException {
        final int deviceHeight = mDevice.getDisplayHeight();
        int deviceWidth = mDevice.getDisplayWidth();
        int y = deviceHeight / 2;
        final Context context = getApplicationContext();
        final Intent intent = new Intent(context, StatsdCtsMinAspectRatioPortraitActivity.class);
        intent.putExtra(KEY_ACTION, ACTION_LONG_SLEEP_WHILE_TOP);
        ActivityScenario<StatsdCtsMinAspectRatioPortraitActivity> scenario =
                ActivityScenario.launch(intent);
        mDevice.waitForIdle();

        AtomicReference<Rect> atomicBounds = new AtomicReference<>(new Rect());
        scenario.onActivity(a -> atomicBounds.set(
                a.getResources().getConfiguration().windowConfiguration.getBounds()));
        final Rect bounds = atomicBounds.get();
        if (bounds.width() >= deviceWidth - BOUNDS_OFFSET * 2) {
            mDevice.setOrientationLeft();
            mDevice.waitForIdle();
            y = deviceWidth / 2;
            deviceWidth = deviceHeight;
        }

        // Reposition to right.
        mGestureHelper.click(deviceWidth - BOUNDS_OFFSET, y, 2);
        mGestureHelper.waitForAnimation();

        // Reposition back to center.
        mGestureHelper.click(BOUNDS_OFFSET, y, 2);
        mGestureHelper.waitForAnimation();

        // Reposition to left.
        mGestureHelper.click(BOUNDS_OFFSET, y, 2);
        mGestureHelper.waitForAnimation();

        // Reposition back to center.
        mGestureHelper.click(deviceWidth - BOUNDS_OFFSET, y, 2);
        mGestureHelper.waitForAnimation();
    }

    /**
     * Helper test to reposition app vertically to log LetterboxPositionChanged atoms.
     */
    @NonApiTest(exemptionReasons = {}, justification = "METRIC")
    @Test
    public void testVerticalReachability() throws RemoteException {
        final int deviceWidth = mDevice.getDisplayWidth();
        int deviceHeight = mDevice.getDisplayHeight();
        int x = deviceWidth / 2;
        final Context context = getApplicationContext();
        final Intent intent = new Intent(context, StatsdCtsMinAspectRatioPortraitActivity.class);
        intent.putExtra(KEY_ACTION, ACTION_LONG_SLEEP_WHILE_TOP);
        final ActivityScenario<StatsdCtsMinAspectRatioPortraitActivity> scenario =
                ActivityScenario.launch(intent);

        scenario.onActivity(a -> a.setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE));
        mDevice.waitForIdle();

        AtomicReference<Rect> atomicBounds = new AtomicReference<>(new Rect());
        scenario.onActivity(a -> atomicBounds.set(
                a.getResources().getConfiguration().windowConfiguration.getBounds()));
        final Rect bounds = atomicBounds.get();
        if (bounds.height() >= deviceHeight - BOUNDS_OFFSET * 2) {
            mDevice.setOrientationLeft();
            mDevice.waitForIdle();
            x = deviceHeight / 2;
            deviceHeight = deviceWidth;
        }

        // Reposition to top.
        assertTrue(mGestureHelper.click(x, BOUNDS_OFFSET, 2));
        mGestureHelper.waitForAnimation();

        // Reposition back to center.
        assertTrue(mGestureHelper.click(x, deviceHeight - BOUNDS_OFFSET, 2));
        mGestureHelper.waitForAnimation();

        // Reposition to bottom.
        assertTrue(mGestureHelper.click(x, deviceHeight - BOUNDS_OFFSET, 2));
        mGestureHelper.waitForAnimation();

        // Reposition back to center.
        assertTrue(mGestureHelper.click(x, BOUNDS_OFFSET, 2));
        mGestureHelper.waitForAnimation();
    }

    /**
     * Helper test to select user aspect ratio options in Settings to log atoms.
     */
    @NonApiTest(exemptionReasons = {}, justification = "METRIC")
    @Test
    public void testUserAspectRatioOptions() {
        final Context context = getApplicationContext();
        final Intent intent = new Intent(Settings.ACTION_MANAGE_USER_ASPECT_RATIO_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // Use a different package than the current test package as clicking on the options
        // will force stop the package.
        final Uri packageUri = Uri.parse("package:" + TEST_PKG);
        intent.setData(packageUri);
        context.startActivity(intent);
        mDevice.waitForIdle();

        do {
            clickAllCheckableButtons();
            scrollPageDown();
        } while (mDevice.wait(Until.gone(By.checked(true)), FIND_TIMEOUT));
    }

    private void clickAllCheckableButtons() {
        List<UiObject2> objects = mDevice.wait(Until.findObjects(By.checkable(true)),
                FIND_TIMEOUT);
        if (objects != null) {
            for (int i = objects.size() - 1; i >= 0; i--) {
                objects.get(i).click();
            }
        }
    }

    private void scrollPageDown() {
        List<UiObject2> pages = mDevice.wait(Until.findObjects(By.scrollable(true)), FIND_TIMEOUT);
        if (pages != null) {
            for (int i = 0; i < pages.size(); i++) {
                pages.get(i).scroll(Direction.DOWN, 1.0f);
            }
        }
    }
}
