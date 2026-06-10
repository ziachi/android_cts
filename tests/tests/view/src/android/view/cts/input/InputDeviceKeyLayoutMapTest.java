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

package android.view.cts.input;

import static android.view.InputDevice.SOURCE_KEYBOARD;

import static com.android.cts.input.EvdevInputEventCodes.EV_KEY;
import static com.android.cts.input.EvdevInputEventCodes.EV_KEY_PRESS;
import static com.android.cts.input.EvdevInputEventCodes.EV_KEY_RELEASE;
import static com.android.cts.input.EvdevInputEventCodes.EV_SYN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Display;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.cts.R;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.UserHelper;
import com.android.compatibility.common.util.WindowUtil;
import com.android.cts.input.ConfigurationItem;
import com.android.cts.input.InputJsonParser;
import com.android.cts.input.UinputDevice;
import com.android.cts.input.UinputRegisterCommand;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CTS test case for generic.kl key layout mapping. This test utilize uinput command line tool to
 * create a test device, and configure the virtual device to have all keys need to be tested. The
 * JSON format input for device configuration and EV_KEY injection will be created directly from
 * this test for uinput command. Keep res/raw/Generic.kl in sync with
 * framework/base/data/keyboards/Generic.kl, this file will be loaded and parsed in this test,
 * looping through all key labels and the corresponding EV_KEY code, injecting the EV_KEY_RELEASE
 * and EV_KEY_PRESS event to uinput, then verify the KeyEvent delivered to test application view.
 * Except meta control keys and special keys not delivered to apps, all key codes in generic.kl will
 * be verified.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputDeviceKeyLayoutMapTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    private static final String LABEL_PREFIX = "KEYCODE_";
    private static final int DEVICE_ID = 1;
    private static final int GOOGLE_VENDOR_ID = 0x18d1;
    private static final int GOOGLE_VIRTUAL_KEYBOARD_ID = 0x001f;
    private static final int POLL_EVENT_TIMEOUT_SECONDS = 5;

    private static final Set<String> NEW_KEYS = new HashSet<>(Arrays.asList(
            "DICTATE",
            "NEW",
            "CLOSE",
            "DO_NOT_DISTURB",
            "PRINT",
            "LOCK",
            "FULLSCREEN",
            "F13",
            "F14",
            "F15",
            "F16",
            "F17",
            "F18",
            "F19",
            "F20",
            "F21",
            "F22",
            "F23",
            "F24"
    ));

    private static final Set<String> EXCLUDED_KEYS = new HashSet<>(Arrays.asList(
            // Meta control keys.
            "META_LEFT",
            "META_RIGHT",
            // KeyEvents not delivered to apps.
            "APP_SWITCH",
            "ASSIST",
            "BACK",
            "BRIGHTNESS_DOWN",
            "BRIGHTNESS_UP",
            "HOME",
            "KEYBOARD_BACKLIGHT_DOWN",
            "KEYBOARD_BACKLIGHT_TOGGLE",
            "KEYBOARD_BACKLIGHT_UP",
            "LANGUAGE_SWITCH",
            "MACRO_1",
            "MACRO_2",
            "MACRO_3",
            "MACRO_4",
            "MUTE",
            "NOTIFICATION",
            "POWER",
            "RECENT_APPS",
            "SCREENSHOT",
            "SEARCH",
            "SLEEP",
            "SOFT_SLEEP",
            "STYLUS_BUTTON_TERTIARY",
            "STYLUS_BUTTON_PRIMARY",
            "STYLUS_BUTTON_SECONDARY",
            "SYSRQ",
            "WAKEUP",
            "VOICE_ASSIST",
            // Keys that cause the test activity to lose focus
            "CALCULATOR",
            "CALENDAR",
            "CONTACTS",
            "ENVELOPE",
            "EXPLORER",
            "MUSIC"
    ));

    // List of key codes that are not supported for visible background users by PhoneWindowManager.
    private static final Set<String> KEYS_UNSUPPORTED_FOR_VISIBLE_BACKGROUND_USERS =
            new HashSet<>(Arrays.asList(
                    "CALL",
                    "END_CALL"
    ));

    private Map<String, Integer> mKeyLayout;
    private Instrumentation mInstrumentation;
    private UinputDevice mUinputDevice;
    private InputJsonParser mParser;
    private WindowManager mWindowManager;
    private UserHelper mUserHelper;
    private boolean mIsLeanback;
    private boolean mVolumeKeysHandledInWindowManager;

    private static native Map<String, Integer> nativeLoadKeyLayout(String genericKeyLayout);

    static {
        System.loadLibrary("ctsview_jni");
    }

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<InputDeviceKeyLayoutMapTestActivity> mActivityRule =
            new ActivityTestRule<>(InputDeviceKeyLayoutMapTestActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        WindowUtil.waitForFocus(mActivityRule.getActivity());
        Context context = mInstrumentation.getTargetContext();
        mParser = new InputJsonParser(context);
        mWindowManager = context.getSystemService(WindowManager.class);
        mIsLeanback = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        mVolumeKeysHandledInWindowManager = context.getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_handleVolumeKeysInWindowManager",
                        "bool", "android"));
        mKeyLayout = nativeLoadKeyLayout(mParser.readRegisterCommand(R.raw.Generic));
        mUserHelper = new UserHelper(context);
        // For a visible background user, use the assigned main display.
        // Otherwise, use the default display by passing null.
        Display display = mUserHelper.isVisibleBackgroundUser()
                ? context.getSystemService(DisplayManager.class)
                        .getDisplay(mUserHelper.getMainDisplayId())
                : null;
        mUinputDevice = new UinputDevice(
                mInstrumentation, SOURCE_KEYBOARD, createDeviceRegisterCommand(), display);
    }

    @After
    public void tearDown() {
        if (mUinputDevice != null) {
            mUinputDevice.close();
        }
    }

    /**
     * Get a KeyEvent from event queue or timeout.
     *
     * @return KeyEvent delivered to test activity, null if timeout.
     */
    private KeyEvent getKeyEvent() {
        return mActivityRule.getActivity().getKeyEvent(POLL_EVENT_TIMEOUT_SECONDS);
    }

    private void assertReceivedKeyEvent(int action, int keyCode) {
        KeyEvent receivedKeyEvent = getKeyEvent();
        assertNotNull("Did not receive " + KeyEvent.keyCodeToString(keyCode), receivedKeyEvent);
        assertEquals(action, receivedKeyEvent.getAction());
        assertEquals(keyCode, receivedKeyEvent.getKeyCode());
    }

    private UinputRegisterCommand createDeviceRegisterCommand() {
        List<ConfigurationItem> configurationItems = Arrays.asList(
                new ConfigurationItem("UI_SET_EVBIT", List.of("EV_KEY")),
                new ConfigurationItem("UI_SET_KEYBIT", mKeyLayout.values().stream().toList())
        );

        return new UinputRegisterCommand(
                DEVICE_ID,
                "Virtual All Buttons Device (Test)",
                GOOGLE_VENDOR_ID,
                GOOGLE_VIRTUAL_KEYBOARD_ID,
                "bluetooth",
                "bluetooth:1",
                configurationItems,
                Map.of(),
                /* ffEffectsMax= */ null
        );
    }

    /**
     * Simulate pressing a key.
     * @param evKeyCode The key scan code
     */
    private void pressKey(int evKeyCode) {
        int[] evCodesDown = new int[] {EV_KEY, evKeyCode, EV_KEY_PRESS, EV_SYN, 0, 0};
        mUinputDevice.injectEvents(Arrays.toString(evCodesDown));

        int[] evCodesUp = new int[] {EV_KEY, evKeyCode, EV_KEY_RELEASE, EV_SYN, 0, 0};
        mUinputDevice.injectEvents(Arrays.toString(evCodesUp));
    }

    /**
     * Whether one key code is a volume key code.
     * @param keyCode The key code
     */
    private static boolean isVolumeKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    /**
     * Whether one key code should be forwarded to apps.
     * @param keyCode The key code
     */
    private boolean isForwardedToApps(int keyCode) {
        if (mWindowManager.isGlobalKey(keyCode)) {
            return false;
        }
        if (isVolumeKey(keyCode) && (mIsLeanback || mVolumeKeysHandledInWindowManager)) {
            return false;
        }
        return true;
    }

    @Test
    public void testLayoutKeyEvents() {
        for (Map.Entry<String, Integer> entry : mKeyLayout.entrySet()) {
            if (EXCLUDED_KEYS.contains(entry.getKey()) || NEW_KEYS.contains(entry.getKey())) {
                continue;
            }
            // Exclude keys that PhoneWindowManager does not support for visible background users.
            if (mUserHelper.isVisibleBackgroundUser()
                    && KEYS_UNSUPPORTED_FOR_VISIBLE_BACKGROUND_USERS.contains(entry.getKey())) {
                continue;
            }

            String label = LABEL_PREFIX + entry.getKey();
            final int evKey = entry.getValue();
            final int keyCode = KeyEvent.keyCodeFromString(label);

            if (!isForwardedToApps(keyCode)) {
                continue;
            }

            pressKey(evKey);
            assertReceivedKeyEvent(KeyEvent.ACTION_DOWN, keyCode);
            assertReceivedKeyEvent(KeyEvent.ACTION_UP, keyCode);
        }
    }

    @Test
    @RequiresFlagsEnabled(com.android.hardware.input.Flags.FLAG_ENABLE_NEW_25Q2_KEYCODES)
    public void testNewKeycodes() {
        for (Map.Entry<String, Integer> entry : mKeyLayout.entrySet()) {
            if (!NEW_KEYS.contains(entry.getKey())) {
                continue;
            }

            String label = LABEL_PREFIX + entry.getKey();
            final int evKey = entry.getValue();
            final int keyCode = KeyEvent.keyCodeFromString(label);

            if (!isForwardedToApps(keyCode) || keyCode == KeyEvent.KEYCODE_LOCK
                    || keyCode == KeyEvent.KEYCODE_FULLSCREEN
                    || keyCode == KeyEvent.KEYCODE_DO_NOT_DISTURB) {
                continue;
            }

            pressKey(evKey);
            assertReceivedKeyEvent(KeyEvent.ACTION_DOWN, keyCode);
            assertReceivedKeyEvent(KeyEvent.ACTION_UP, keyCode);
        }
    }

}
