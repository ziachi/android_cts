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

import static com.android.cts.input.EvdevInputEventCodes.KEY_1;
import static com.android.cts.input.EvdevInputEventCodes.KEY_2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.Manifest;
import android.app.Instrumentation;
import android.hardware.input.InputManager;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.WindowUtil;
import com.android.cts.input.UinputKeyboard;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * CTS test cases for multi device key events verification.
 * This test utilize uinput command line tool to create multiple test devices, and configure the
 * virtual device to have keys need to be tested. The JSON format input for device configuration
 * and EV_KEY injection will be created directly from this test for uinput command.
 * The test cases will inject evdev events from different virtual input devices and verify the
 * received key events to verify the device Id, repeat count to be expected, as well as the key
 * repeat behavior is consistently meeting expectations with multi devices.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputDeviceMultiDeviceKeyEventTest {
    private static final String TAG = "InputDeviceMultiDeviceKeyEventTest";
    private static final String LABEL_PREFIX = "KEYCODE_";
    private static final int GOOGLE_VENDOR_ID = 0x18d1;
    private static final int GOOGLE_VIRTUAL_KEYBOARD_ID = 0x001f;
    private static final int NUM_DEVICES = 2;
    private static final int POLL_EVENT_TIMEOUT_SECONDS = 1;
    private static final int RETRY_COUNT = 10;

    private Instrumentation mInstrumentation;
    private InputManager mInputManager;
    private UinputKeyboard[] mUinputKeyboards = new UinputKeyboard[NUM_DEVICES];
    private int[] mInputManagerDeviceIds = new int[NUM_DEVICES];

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
        for (int i = 0; i < NUM_DEVICES; i++) {
            final int jsonDeviceId = i + 1;
            mUinputKeyboards[i] =
                    new UinputKeyboard(
                            mInstrumentation,
                            List.of("KEY_1", "KEY_2"),
                            GOOGLE_VIRTUAL_KEYBOARD_ID + jsonDeviceId);
        }

        mInputManager = mInstrumentation.getContext().getSystemService(InputManager.class);
        final int[] inputDeviceIds = mInputManager.getInputDeviceIds();
        for (int inputDeviceId : inputDeviceIds) {
            final InputDevice inputDevice = mInputManager.getInputDevice(inputDeviceId);
            final int index = inputDevice.getProductId() - GOOGLE_VIRTUAL_KEYBOARD_ID - 1;
            if (inputDevice.getVendorId() == GOOGLE_VENDOR_ID
                    && index >= 0 && index < NUM_DEVICES) {
                mInputManagerDeviceIds[index] = inputDeviceId;
            }
        }
    }

    @After
    public void tearDown() {
        for (int i = 0; i < NUM_DEVICES; i++) {
            if (mUinputKeyboards[i] != null) {
                mUinputKeyboards[i].close();
            }
        }
    }

    /**
     * Get a KeyEvent from event queue or timeout.
     * The test activity instance may change in the middle, calling getKeyEvent with the old
     * activity instance will get timed out when test activity instance changed. Rather than
     * doing a long wait for timeout with same activity instance, break the polling into a number
     * of retries and each time of retry call the ActivityTestRule.getActivity for current activity
     * instance to avoid the test failure because of polling the old activity instance get timed
     * out consequently failed the test.
     *
     * @param retryCount The times to retry get KeyEvent from test activity.
     *
     * @return KeyEvent delivered to test activity, null if timeout.
     */
    private KeyEvent getKeyEvent(int retryCount) {
        for (int i = 0; i < retryCount; i++) {
            KeyEvent event = mActivityRule.getActivity().getKeyEvent(POLL_EVENT_TIMEOUT_SECONDS);
            if (event != null) {
                return event;
            }
        }
        return null;
    }

    private void assertNoKeyEvent() {
        assertNull(getKeyEvent(1 /* retryCount */));
    }

    /**
     * Asserts that the application received a {@link android.view.KeyEvent} with the given
     * metadata.
     *
     * If other KeyEvents are received by the application prior to the expected KeyEvent, or no
     * KeyEvents are received within a reasonable amount of time, then this will throw an
     * {@link AssertionError}.
     *
     * Only action, source, keyCode and metaState are being compared.
     */
    private void assertReceivedKeyEvent(@NonNull KeyEvent expectedKeyEvent) {
        assertNotEquals(expectedKeyEvent.getKeyCode(), KeyEvent.KEYCODE_UNKNOWN);

        KeyEvent receivedKeyEvent = getKeyEvent(RETRY_COUNT);
        String log = "Expected " + expectedKeyEvent + " Received " + receivedKeyEvent;
        assertNotNull(log, receivedKeyEvent);
        assertEquals("DeviceId: " + log, expectedKeyEvent.getDeviceId(),
                receivedKeyEvent.getDeviceId());
        assertEquals("Action: " + log, expectedKeyEvent.getAction(),
                receivedKeyEvent.getAction());
        assertEquals("Source: " + log, expectedKeyEvent.getSource(),
                receivedKeyEvent.getSource());
        assertEquals("KeyCode: " + log, expectedKeyEvent.getKeyCode(),
                receivedKeyEvent.getKeyCode());
        assertEquals("RepeatCount: " + log, expectedKeyEvent.getRepeatCount(),
                receivedKeyEvent.getRepeatCount());
    }

    /**
     * Generate a key event from the key label and action.
     * @param action KeyEvent.ACTION_DOWN or KeyEvent.ACTION_UP
     * @param label Key label from key layout mapping definition
     * @return KeyEvent expected to receive
     */
    private KeyEvent generateKeyEvent(int deviceId, int action, String label, int repeat) {
        int source = InputDevice.SOURCE_KEYBOARD;
        int keyCode = KeyEvent.keyCodeFromString(LABEL_PREFIX + label);
        // We will only check select fields of the KeyEvent. Times are not checked.
        KeyEvent event = new KeyEvent(/* downTime */ 0, /* eventTime */ 0, action, keyCode,
                repeat, /* metaState */ 0, mInputManagerDeviceIds[deviceId], /* scanCode */ 0,
                /* flags */ 0, source);

        return event;
    }

    /**
     * Simulate pressing a key.
     * @param evKeyCode The key scan code
     */
    private void pressKeyDown(int deviceId, int evKeyCode) {
        mUinputKeyboards[deviceId].injectKeyDown(evKeyCode);
    }

    /**
     * Simulate releasing a key.
     * @param evKeyCode The key scan code
     */
    private void pressKeyUp(int deviceId, int evKeyCode) {
        mUinputKeyboards[deviceId].injectKeyUp(evKeyCode);
    }

    private void assertKeyRepeat(int deviceId, String label, int repeat, int count) {
        for (int i = 0; i < count; i++) {
            KeyEvent expectedDownEvent = generateKeyEvent(deviceId,
                    KeyEvent.ACTION_DOWN, label, repeat + i);
            assertReceivedKeyEvent(expectedDownEvent);
        }
    }

    private void assertKeyUp(int deviceId, String label) {
        KeyEvent expectedUpEvent = generateKeyEvent(deviceId,
                KeyEvent.ACTION_UP, label, /* repeat */ 0);
        assertReceivedKeyEvent(expectedUpEvent);
    }

    @Test
    public void testReceivesKeyRepeatFromTwoDevices() {
        final String keyOne = "1";
        // Press the key from device 0
        pressKeyDown(/* deviceId */ 0, KEY_1);
        // KeyDown repeat driven by device 0
        assertKeyRepeat(/* deviceId */ 0, keyOne, /* repeat */ 0, /* count */ 10);
        // Press the key from device 1
        pressKeyDown(/* deviceId */ 1, KEY_1);
        // KeyDown repeat driven by device 1
        assertKeyRepeat(/* deviceId */ 1, keyOne, /* repeat */ 0, /* count */ 10);
    }

    @Test
    public void testReceivesKeyRepeatOnTwoKeysFromTwoDevices() {
        final String keyOne = "1";
        final String keyTwo = "2";
        // Press the key 1 from device 0
        pressKeyDown(/* deviceId */ 0, KEY_1);
        // KeyDown repeat driven by device 0
        assertKeyRepeat(/* deviceId */ 0, keyOne, /* repeat */ 0, /* count */ 10);

        // Press the key 2 from device 1
        pressKeyDown(/* deviceId */ 1, KEY_2);
        // KeyDown repeat driven by device 1
        assertKeyRepeat(/* deviceId */ 1, keyTwo, /* repeat */ 0, /* count */ 10);

        // Release the key 2 from device 1
        // Generate expected key up event and verify
        pressKeyUp(/* deviceId */ 1, KEY_2);
        assertKeyUp(/* deviceId */ 1, keyTwo);

        // No key repeating anymore.
        assertNoKeyEvent();

        // Release the key 1 from device 0
        // Generate expected key up event and verify
        pressKeyUp(/* deviceId */ 0, KEY_1);
        assertKeyUp(/* deviceId */ 0, keyOne);
    }

    @Test
    public void testKeyRepeatAfterStaleDeviceKeyUp() {
        final String keyOne = "1";
        // Press the key from device 0
        pressKeyDown(/* deviceId */ 0, KEY_1);
        // KeyDown repeat driven by device 0
        assertKeyRepeat(/* deviceId */ 0, keyOne, /* repeat */ 0, /* count */ 10);

        // Press the key from device 1
        pressKeyDown(/* deviceId */ 1, KEY_1);
        // KeyDown repeat driven by device 1
        assertKeyRepeat(/* deviceId */ 1, keyOne, /* repeat */ 0, /* count */ 10);

        // Release the key from device 0
        // Generate expected key up event and verify
        pressKeyUp(/* deviceId */ 0, KEY_1);
        assertKeyUp(/* deviceId */ 0, keyOne);

        // KeyDown kept repeating by device 1
        assertKeyRepeat(/* deviceId */ 1, keyOne, /* repeat */ 10, /* count */ 10);

        // Release the key from device 1
        // Generate expected key up event and verify
        pressKeyUp(/* deviceId */ 1, KEY_1);
        assertKeyUp(/* deviceId */ 1, keyOne);
    }

    @Test
    public void testKeyRepeatStopsAfterRepeatingKeyUp() {
        final String keyOne = "1";
        // Press the key from device 0
        pressKeyDown(/* deviceId */ 0, KEY_1);
        // KeyDown repeat driven by device 0
        assertKeyRepeat(/* deviceId */ 0, keyOne, /* repeat */ 0, /* count */ 10);

        // Press the key from device 1
        pressKeyDown(/* deviceId */ 1, KEY_1);
        // KeyDown repeat driven by device 1
        assertKeyRepeat(/* deviceId */ 1, keyOne, /* repeat */ 0, /* count */ 10);

        // Release the key from device 1
        // Generate expected key up event and verify
        pressKeyUp(/* deviceId */ 1, KEY_1);
        assertKeyUp(/* deviceId */ 1, keyOne);

        // No key repeating anymore.
        assertNoKeyEvent();

        // Release the key from device 0
        // Generate expected key up event and verify
        pressKeyUp(/* deviceId */ 0, KEY_1);
        assertKeyUp(/* deviceId */ 0, keyOne);
    }

}
