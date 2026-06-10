/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UserHelper;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

// Note: this test must not extend AbstractCarTestCase. See b/328536639.
public final class CarRotaryImeTest {

    private static final String TAG = CarRotaryImeTest.class.getSimpleName();
    private static final long POLLING_CHECK_TIMEOUT_MILLIS = 10000L;

    private static final ComponentName ROTARY_SERVICE_COMPONENT_NAME =
            ComponentName.unflattenFromString("com.android.car.rotary/.RotaryService");

    private static final UiAutomation sUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation(
                    UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final AccessibilityManager mAccessibilityManager =
            mContext.getSystemService(AccessibilityManager.class);
    private final InputMethodManager mInputMethodManager =
            mContext.getSystemService(InputMethodManager.class);

    @Before
    public void setUp() {
        UserHelper userHelper = new UserHelper(mContext);

        // Skipping for visible background users as Accessibility Framework which RotaryService
        // relies on does not support visible background users.
        assumeFalse("Not supported on visible background user",
                userHelper.isVisibleBackgroundUser());

        assumeHasRotaryService();

        // Wait for RotaryService to be recreated and running in case it was killed by other tests
        // using UiAutomation.
        // When it is running, the dumpsys result is like:
        // SERVICE com.android.car.rotary/.RotaryService 898025b pid=2101 user=10
        // Otherwise, the dumpsys result is like:
        // SERVICE com.android.car.rotary/.RotaryService 19f3b7e pid=(not running))
        PollingCheck.waitFor(POLLING_CHECK_TIMEOUT_MILLIS,
                () -> dumpRotaryService().contains("user"),
                "RotaryService is not running yet");
    }

    /**
     * Tests that, if a rotary input method is specified via the {@code rotary_input_method} string
     * resource, it's the component name of an existing IME.
     */
    @Test
    public void rotaryInputMethodValidIfSpecified() {
        String rotaryInputMethod = getStringValueFromDumpsys("rotaryInputMethod");
        assumeTrue("Rotary input method not specified, skipping test",
                !rotaryInputMethod.isEmpty());

        assertWithMessage("isValidIme(" + rotaryInputMethod + ")")
                .that(isValidIme(rotaryInputMethod)).isTrue();
    }

    /**
     * Tests that, if a rotary input method is specified via the {@code rotary_input_method} string
     * resource and is not empty, the rotary IME must be different from the touch IME.
     */
    @Test
    public void rotaryImeNotTouchIme() {
        String rotaryInputMethod = getStringValueFromDumpsys("rotaryInputMethod");
        assumeTrue("Rotary input method not specified, skipping test",
                !rotaryInputMethod.isEmpty());

        String defaultTouchInputMethod = getStringValueFromDumpsys("defaultTouchInputMethod");
        assertWithMessage("rotary IME(" + rotaryInputMethod + ") must be different"
                + "from default touch IME(" + defaultTouchInputMethod + ")")
                .that(rotaryInputMethod.equals(defaultTouchInputMethod)).isFalse();

        String touchInputMethod = getStringValueFromDumpsys("touchInputMethod");
        assertWithMessage("rotary IME(" + rotaryInputMethod + ") must be different"
                + "from touch IME(" + touchInputMethod + ")")
                .that(rotaryInputMethod.equals(touchInputMethod)).isFalse();
    }

    /**
     * Tests that, if a rotary input method is specified via the {@code rotary_input_method} string
     * resource and is not empty, when it is in rotary mode, the current IME must be the rotary IME.
     */
    @Test
    public void rotaryImeInRotaryMode() {
        String rotaryInputMethod = getStringValueFromDumpsys("rotaryInputMethod");
        assumeTrue("Rotary input method not specified, skipping test",
                !rotaryInputMethod.isEmpty());

        ensureInRotaryMode();

        String currentInput = mInputMethodManager.getCurrentInputMethodInfo().getComponent()
                .flattenToShortString();

        assertWithMessage("In rotary mode, the current IME should be the rotary"
                + " IME(" + rotaryInputMethod + "), but was (" + currentInput + ")")
                .that(rotaryInputMethod.equals(currentInput)).isTrue();
    }

    /**
     * Tests that, if a rotary input method is specified via the {@code rotary_input_method} string
     * resource and is not empty, when it is not in rotary mode, the current IME must not be the
     * rotary IME.
     */
    @Test
    public void rotaryImeNotInTouchMode() {
        String rotaryInputMethod = getStringValueFromDumpsys("rotaryInputMethod");
        assumeTrue("Rotary input method not specified, skipping test",
                !rotaryInputMethod.isEmpty());

        ensureInTouchMode();

        String currentInput = mInputMethodManager.getCurrentInputMethodInfo().getComponent()
                .flattenToShortString();
        assertWithMessage("In touch mode, the current IME should not be the"
                + " rotary IME(" + rotaryInputMethod + "), but was rotary IME")
                .that(rotaryInputMethod.equals(currentInput)).isFalse();
    }

    /**
     * The default touch input method must be specified via the {@code default_touch_input_method}
     * string resource, and it must be the component name of an existing IME.
     */
    @Test
    public void defaultTouchInputMethodSpecifiedAndValid() {
        String defaultTouchInputMethod = getStringValueFromDumpsys("defaultTouchInputMethod");

        assertWithMessage("defaultTouchInputMethod").that(defaultTouchInputMethod).isNotEmpty();
        assertWithMessage("isValidIme(" + defaultTouchInputMethod + ")")
                .that(isValidIme(defaultTouchInputMethod)).isTrue();
    }

    // TODO(b/327507413): switch to proto-based dumpsys.
    private static String getStringValueFromDumpsys(String key) {
        String dumpsysOutput = dumpRotaryService();
        // When RotaryService is running, dumpsys output contains string like:
        // SERVICE com.android.car.rotary/.RotaryService a4d5db3 pid=20152 user=10
        //  Client:
        //    {
        //      rotationAcceleration2xMs=40
        //      rotaryInputMethod=com.android.car.rotaryime/.RotaryIme
        //      defaultTouchInputMethod=com.google.android.apps.automotive.inputmethod/
        //      .InputMethodService"
        // When it is not running, dumpsys output contains string like:
        // SERVICE com.android.car.rotary/.RotaryService a4d5db3 pid=(not running))
        if (dumpsysOutput.contains(key)) {
            int startIndex = dumpsysOutput.indexOf(key) + key.length() + 1;
            int endIndex = dumpsysOutput.indexOf('\n', startIndex);
            String value = dumpsysOutput.substring(startIndex, endIndex);
            if (!"null".equals(value)) {
                return value;
            }
        }
        Log.e(TAG, "dumpsysOutput: " + dumpsysOutput);
        return "";
    }

    private static String dumpRotaryService() {
        try {
            return SystemUtil.runShellCommand(sUiAutomation, "dumpsys activity service "
                    + ROTARY_SERVICE_COMPONENT_NAME.flattenToShortString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void ensureInRotaryMode() {
        // Inject a KeyEvent (nudge_up) to ensure it is in rotary mode.
        sUiAutomation.executeShellCommand("cmd car_service inject-key 280");
        PollingCheck.waitFor(POLLING_CHECK_TIMEOUT_MILLIS,
                () -> getStringValueFromDumpsys("inRotaryMode").equals("true"),
                "It should be in rotary mode after injecting KeyEvent");
    }

    private static void ensureInTouchMode() {
        // Inject a touch event to ensure it is in touch mode.
        sUiAutomation.executeShellCommand("input tap 0 0");
        PollingCheck.waitFor(POLLING_CHECK_TIMEOUT_MILLIS,
                () -> getStringValueFromDumpsys("inRotaryMode").equals("false"),
                "It should be in touch mode after injecting touch event");
    }

    private void assumeHasRotaryService() {
        assumeTrue("Rotary service not enabled; skipping test",
                mAccessibilityManager.getInstalledAccessibilityServiceList().stream().anyMatch(
                        accessibilityServiceInfo ->
                                ROTARY_SERVICE_COMPONENT_NAME.equals(
                                        accessibilityServiceInfo.getComponentName())));
    }

    /** Returns whether {@code flattenedComponentName} is an installed input method. */
    private boolean isValidIme(@NonNull String flattenedComponentName) {
        ComponentName componentName = ComponentName.unflattenFromString(flattenedComponentName);
        return mInputMethodManager.getInputMethodList().stream()
                .map(inputMethodInfo -> inputMethodInfo.getComponent())
                .anyMatch(inputMethodComponent -> inputMethodComponent.equals(componentName));
    }
}
