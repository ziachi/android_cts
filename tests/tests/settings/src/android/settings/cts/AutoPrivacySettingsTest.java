/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.settings.cts;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Condition;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.bedstead.multiuser.annotations.RequireRunNotOnVisibleBackgroundNonProfileUser;
import com.android.compatibility.common.util.CddTest;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AutoPrivacySettingsTest {
    private static final int TIMEOUT_MS = 10000;
    private static final String ACTION_SETTINGS = "android.settings.SETTINGS";
    private static final String PRIVACY = "Privacy";
    private static final String MICROPHONE = "Microphone";
    private static final String USE_MICROPHONE = "Use microphone";
    private static final String MICROPHONE_ACCESS = "Microphone access";
    private static final String[] EXPECTED_MICROPHONE_ENABLED_SETTINGS = {
            USE_MICROPHONE, "Recently accessed", "Manage microphone permissions"};
    private static final String[] EXPECTED_MICROPHONE_ENABLED_SETTINGS_V2 = {
            MICROPHONE_ACCESS, "Recently accessed", "Manage microphone permissions"};

    // For the camera privacy setting test
    private static final String CAMERA = "Camera";

    private static final String CAMERA_ACCESS = "Camera access";
    private static final String INFOTAINMENT_APPS = "Infotainment apps";
    private static final String USE_CAMERA = "Use camera";
    private static final String[] EXPECTED_CAMERA_ENABLED_ITEMS_V1 = {
            USE_CAMERA, "Recently accessed", "Manage camera permissions"};
    private static final String[] EXPECTED_CAMERA_ENABLED_ITEMS_V2 = {
            CAMERA_ACCESS, "Recently accessed", "Manage camera permissions"};

    // To support dual panes in AAOS S
    private static final int MAX_NUM_SCROLLABLES = 2;

    private final Context mContext = InstrumentationRegistry.getContext();
    private final UiDevice mDevice = UiDevice.getInstance(getInstrumentation());
    private int mDisplayId;

    private Condition mSearchCondition = new Condition<UiDevice, Boolean>() {

        @Override
        public Boolean apply(UiDevice device) {
            return device.findObjects(By.clazz(RecyclerView.class)).size() > 1;
        }
    };

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();


    @Before
    public void setUp() {
        assumeFalse("Skipping test: Requirements only apply to Auto",
                !mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        UserManager userManager = mContext.getSystemService(UserManager.class);
        mDisplayId = userManager.getMainDisplayIdAssignedToUser();
    }

    /**
     * MUST provide a user affordance to do microphone toggle in the following location:Settings >
     * Privacy.
     *
     * This test is not enabled on visible background users.
     */
    @CddTest(requirement = "9.8.2/A-1-3")
    @Test
    @RequiresFlagsEnabled(com.android.car.settings.Flags.FLAG_MICROPHONE_PRIVACY_UPDATES)
    @RequireRunNotOnVisibleBackgroundNonProfileUser
    public void testPrivacyMicrophoneSettings() throws Exception {
        PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            // Skip this test if the system does not have a microphone.
            return;
        }
        goHome();

        launchActionSettings();
        mDevice.waitForIdle();

        UiObject2 privacyObj = assertScrollToAndFind(PRIVACY);
        privacyObj.click();
        mDevice.waitForIdle();

        UiObject2 micObj = assertScrollToAndFind(MICROPHONE);
        micObj.click();
        mDevice.waitForIdle();

        // verify state when mic is enabled
        disableCameraMicPrivacy();
        if (SdkLevel.isAtLeastV()) {
            for (String setting : EXPECTED_MICROPHONE_ENABLED_SETTINGS_V2) {
                assertScrollToAndFind(setting);
            }
            UiObject2 micAccessObj = scrollToText(MICROPHONE_ACCESS);
            micAccessObj.click();
            mDevice.waitForIdle();
            assertScrollToAndFind(INFOTAINMENT_APPS);
        } else {
            for (String setting : EXPECTED_MICROPHONE_ENABLED_SETTINGS) {
                assertScrollToAndFind(setting);
            }
        }

        goHome();
    }

    /**
     * MUST provide a user affordance to do camera toggle in the following location:Settings >
     * Privacy.
     *
     * This test is not enabled on visible background users
     */
    @CddTest(requirement = "9.8.2/A-2-3")
    @Test
    @RequiresFlagsEnabled(com.android.internal.camera.flags.Flags.FLAG_CAMERA_PRIVACY_ALLOWLIST)
    @RequireRunNotOnVisibleBackgroundNonProfileUser
    public void testPrivacyCameraSettings() throws Exception {
        assumeFalse(
                "Skipping test: Enabling/Disabling Camera is not supported in Wear",
                SettingsTestUtils.isWatch());

        PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            // Skip this test if the system does not have a camera.
            return;
        }

        goHome();

        launchActionSettings();
        mDevice.waitForIdle();

        UiObject2 privacyObj = assertScrollToAndFind(PRIVACY);
        privacyObj.click();
        mDevice.waitForIdle();

        UiObject2 camObj = assertScrollToAndFind(CAMERA);
        camObj.click();
        mDevice.waitForIdle();

        // verify state when camera is enabled
        disableCameraMicPrivacy();
        if (SdkLevel.isAtLeastV()) {
            for (String item : EXPECTED_CAMERA_ENABLED_ITEMS_V2) {
                assertScrollToAndFind(item);
            }
            UiObject2 camAccessObj = scrollToText(CAMERA_ACCESS);
            camAccessObj.click();
            mDevice.waitForIdle();
            assertScrollToAndFind(INFOTAINMENT_APPS);
        } else {
            for (String item : EXPECTED_CAMERA_ENABLED_ITEMS_V1) {
                assertScrollToAndFind(item);
            }
        }
        goHome();
    }

    /**
     * Find the specified text.
     */
    private UiObject2 assertFind(String text) {
        UiObject2 obj = mDevice.findObject(By.text(text).displayId(mDisplayId));
        assertNotNull("Failed to find '" + text + "'.", obj);
        return obj;
    }

    /**
     * Scroll to find the specified text.
     */
    private UiObject2 assertScrollToAndFind(String text) {
        scrollToText(text);
        return assertFind(text);
    }

    /**
     * Scroll to text, which might be at the bottom of a scrollable list.
     */
    @Nullable
    private UiObject2 scrollToText(String text) {
        UiObject2 foundObject = null;
        // RecyclerViews take longer to load and aren't found even after waiting for idle
        mDevice.wait(mSearchCondition, TIMEOUT_MS);
        // Car-ui-lib replaces settings recyclerviews dynamically so cannot find by resource
        List<UiObject2> recyclerViews =
                mDevice.findObjects(By.clazz(RecyclerView.class).displayId(mDisplayId));
        for (UiObject2 recyclerView : recyclerViews) {
            // Make sure reclerview starts at the top
            recyclerView.scroll(Direction.UP, 1);
            recyclerView.scrollUntil(Direction.DOWN, Until.findObject(By.textContains(text)));
            foundObject = mDevice.findObject(By.text(text).displayId(mDisplayId));
            if (foundObject != null) {
                // No need to look at other recyclerviews.
                break;
            }
        }

        mDevice.waitForIdle();
        return foundObject;
    }


    /**
     * Launch the action settings screen.
     */
    private void launchActionSettings() {
        final Intent intent = new Intent(ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        mContext.startActivityAsUser(intent, mContext.getUser());
        // wait for settings UI to come into focus, test is auto specific
        mDevice.wait(
            Until.hasObject(
                By.res("com.android.car.settings:id/car_settings_activity_wrapper")
                .focused(true)),
              10000L);
    }

    private void goHome() {
        final Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivityAsUser(home, mContext.getUser());
        mDevice.waitForIdle();
    }

    private void disableCameraMicPrivacy() {
        BySelector switchSelector = By.clazz(Switch.class);
        UiObject2 switchButton = mDevice.findObject(switchSelector);
        if (switchButton != null && !switchButton.isChecked()) {
            switchButton.click();
            mDevice.waitForIdle();
            switchButton.wait(Until.checked(true), TIMEOUT_MS);
        }
    }
}
