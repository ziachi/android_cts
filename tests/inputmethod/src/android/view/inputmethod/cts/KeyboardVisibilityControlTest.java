/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.inputmethodservice.InputMethodService.FINISH_INPUT_NO_FALLBACK_CONNECTION;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowInsets.Type.ime;
import static android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
import static android.view.inputmethod.InputMethodManager.CLEAR_SHOW_FORCED_FLAG_WHEN_LEAVING;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeInvisible;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible;
import static android.view.inputmethod.cts.util.TestUtils.getOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.runOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.runOnMainSyncWithRethrowing;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.eventMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEventWithKeyValue;
import static com.android.cts.mockime.ImeEventStreamTestUtils.hideSoftInputMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.showSoftInputMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.waitForInputViewLayoutStable;
import static com.android.cts.mockime.ImeEventStreamTestUtils.withDescription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.compat.CompatChanges;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeInstant;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.CtsWindowInfoUtils;
import android.server.wm.WindowManagerState;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.Flags;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.AutoCloseableWrapper;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.MockTestActivityUtil;
import android.view.inputmethod.cts.util.RequireImeCompatFlagRule;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.TestActivity2;
import android.view.inputmethod.cts.util.TestUtils;
import android.view.inputmethod.cts.util.TestWebView;
import android.view.inputmethod.cts.util.UnlockScreenRule;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.bedstead.multiuser.annotations.RequireNotVisibleBackgroundUsers;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UserHelper;
import com.android.cts.input.UinputTouchScreen;
import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeEventStreamTestUtils.DescribedPredicate;
import com.android.cts.mockime.ImeLayoutInfo;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@MediumTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class KeyboardVisibilityControlTest extends EndToEndImeTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    private static final String TAG = KeyboardVisibilityControlTest.class.getSimpleName();
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(6);
    private static final long START_INPUT_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    private static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(1);
    private static final long LAYOUT_STABLE_THRESHOLD = TimeUnit.SECONDS.toMillis(3);

    private static final int NEW_KEYBOARD_HEIGHT = 400;
    private static final PreBackPressProcedure NO_OP_PRE_BACK_PRESS_PROCEDURE =
            (instrumentation, editorRef) -> {};

    private static final String DISABLE_AUTO_ROTATE_CMD =
            "settings put system accelerometer_rotation 0";
    private static final String ENABLE_AUTO_ROTATE_CMD =
            "settings put system accelerometer_rotation 1";

    private static final String FIXED_TO_USER_ROTATION_CMD = "cmd window fixed-to-user-rotation";


    @Rule
    public final UnlockScreenRule mUnlockScreenRule = new UnlockScreenRule();
    @Rule
    public final RequireImeCompatFlagRule mRequireImeCompatFlagRule = new RequireImeCompatFlagRule(
            FINISH_INPUT_NO_FALLBACK_CONNECTION, true);

    private Instrumentation mInstrumentation;

    private TestActivity mTestActivity;

    private final UserHelper mUserHelper = new UserHelper();

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    private static DescribedPredicate<ImeEvent> onFinishInputViewMatcher(boolean expectedFinishingInput) {
        Predicate<ImeEvent> matcher = event -> {
            if (!TextUtils.equals("onFinishInputView", event.getEventName())) {
                return false;
            }
            final boolean finishingInput = event.getArguments().getBoolean("finishingInput");
            return finishingInput == expectedFinishingInput;
        };
        return withDescription("onFinishInputView(finishingInput=" + expectedFinishingInput + ")",
                matcher);
    }

    private Pair<EditText, EditText> launchTestActivity(@NonNull String focusedMarker,
            @NonNull String nonFocusedMarker) {
        final AtomicReference<EditText> focusedEditTextRef = new AtomicReference<>();
        final AtomicReference<EditText> nonFocusedEditTextRef = new AtomicReference<>();
        final var activityStarter =
                new TestActivity.Starter().withDisplayId(mUserHelper.getMainDisplayId());
        mTestActivity = activityStarter.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText focusedEditText = new EditText(activity);
            focusedEditText.setHint("focused editText");
            focusedEditText.setPrivateImeOptions(focusedMarker);
            focusedEditText.requestFocus();
            focusedEditTextRef.set(focusedEditText);
            layout.addView(focusedEditText);

            final EditText nonFocusedEditText = new EditText(activity);
            nonFocusedEditText.setPrivateImeOptions(nonFocusedMarker);
            nonFocusedEditText.setHint("target editText");
            nonFocusedEditTextRef.set(nonFocusedEditText);
            layout.addView(nonFocusedEditText);
            return layout;
        }, TestActivity.class);
        return new Pair<>(focusedEditTextRef.get(), nonFocusedEditTextRef.get());
    }

    private EditText launchTestActivity(@NonNull String marker) {
        return launchTestActivity(marker, getTestMarker(NON_FOCUSED_EDIT_TEXT_TAG)).first;
    }

    private EditText launchTestActivity2(@NonNull String marker) {
        final AtomicReference<EditText> focusedEditTextRef = new AtomicReference<>();
        mTestActivity = new TestActivity.Starter().startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText focusedEditText = new EditText(activity);
            focusedEditText.setHint("focused editText");
            focusedEditText.setPrivateImeOptions(marker);
            focusedEditText.requestFocus();
            focusedEditTextRef.set(focusedEditText);
            layout.addView(focusedEditText);
            return layout;
        }, TestActivity2.class);
        return focusedEditTextRef.get();
    }

    @Test
    public void testBasicShowHideSoftInput() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker(FOCUSED_EDIT_TEXT_TAG);
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            final InputMethodManager imm = mTestActivity.getSystemService(InputMethodManager.class);
            assertTrue("hasActiveInputConnection() must return true if the View has IME focus",
                    getOnMainSync(() -> imm.hasActiveInputConnection(editText)));

            // Test showSoftInput() flow
            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText, 0)));

            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Test hideSoftInputFromWindow() flow
            assertTrue("hideSoftInputFromWindow must success if the View has IME focus",
                    getOnMainSync(() -> imm.hideSoftInputFromWindow(editText.getWindowToken(), 0)));

            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @FunctionalInterface
    private interface PreBackPressProcedure {
        void run(
                Instrumentation instrumentation,
                AtomicReference<EditText> editorRef) throws Exception;
    }

    private void verifyHideImeBackPressed(
            boolean appRequestsBackCallback, boolean imeRequestsBackCallback,
            @NonNull PreBackPressProcedure preBackPressProcedure) throws Exception {
        final Instrumentation instrumentation = mInstrumentation;
        final Context context = instrumentation.getTargetContext();

        // Whether 'OnBackInvokedCallback' or 'onBackPressed' (legacy back) is used is defined by
        // the 'enableOnBackInvokedCallback' flag in the Application manifest.
        // Registering a callback is only authorized if the flag is set to true. Since the
        // WindowOnBackDispatcher is created at the same time as the ViewRootImpl, for test purpose,
        // we need to manually set the flag on ApplicationInfo before the window is created which
        // happens during the MockIme creation and TestActivity creation.
        final boolean onBackCallbackEnabled =
                context.getApplicationInfo().isOnBackInvokedCallbackEnabled();

        try (MockImeSession imeSession = MockImeSession.create(
                instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder()
                        .setOnBackCallbackEnabled(imeRequestsBackCallback)
        )) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker(FOCUSED_EDIT_TEXT_TAG);
            final AtomicInteger backCallbackInvocationCount = new AtomicInteger();

            if (appRequestsBackCallback) {
                context.getApplicationInfo().setEnableOnBackInvokedCallback(true);
            }

            final EditText editText = launchTestActivity(marker);
            final AtomicReference<EditText> editorRef = new AtomicReference<>();
            editorRef.set(editText);
            final TestActivity testActivity = (TestActivity) editText.getContext();

            if (appRequestsBackCallback) {
                testActivity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                            backCallbackInvocationCount.getAndIncrement();
                        });
            } else {
                testActivity.setIgnoreBackKey(true);
            }

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            final InputMethodManager imm = testActivity.getSystemService(InputMethodManager.class);
            assertTrue("hasActiveInputConnection() must return true if the View has IME focus",
                    getOnMainSync(() -> imm.hasActiveInputConnection(editText)));

            // Test showSoftInput() flow
            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText, 0)));

            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            preBackPressProcedure.run(instrumentation, editorRef);

            // Pressing back key, expect soft-keyboard will become invisible.
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);

            if (appRequestsBackCallback) {
                // Verify that IME callback is removed after IME is hidden.
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                assertEquals(1, backCallbackInvocationCount.get());
            }
        } finally {
            context.getApplicationInfo().setEnableOnBackInvokedCallback(onBackCallbackEnabled);
        }
    }

    @Test
    public void testHideImeAfterBackPressed_legacyAppLegacyIme() throws Exception {
        verifyHideImeBackPressed(false /* appRequestsBackCallback */,
                false /* imeRequestsBackCallback */,
                (instrumentation, editorRef) -> {} /* pre back press procedure */);
    }

    @Test
    public void testHideImeAfterBackPressed_migratedAppLegacyIme() throws Exception {
        verifyHideImeBackPressed(true /* appRequestsBackCallback */,
                false /* imeRequestsBackCallback */,
                NO_OP_PRE_BACK_PRESS_PROCEDURE);
    }

    @Test
    public void testHideImeAfterBackPressed_migratedAppMigratedIme() throws Exception {
        verifyHideImeBackPressed(true /* appRequestsBackCallback */,
                true /* imeRequestsBackCallback */,
                NO_OP_PRE_BACK_PRESS_PROCEDURE);
    }

    @Test
    public void testHideImeAfterBackPressed_legacyAppMigratedIme() throws Exception {
        verifyHideImeBackPressed(true /* appRequestsBackCallback */,
                true /* imeRequestsBackCallback */,
                NO_OP_PRE_BACK_PRESS_PROCEDURE);
    }

    @RequireNotVisibleBackgroundUsers(reason =
            "Background visible user devices (primarily Android auto) currently doesn't support "
            + "per display interactiveness. So when the screen Off event is sent, "
            + "PowerManager#IsInteractive is still true while driver screen is off as passenger "
            + "screens are on. It also doesn't trigger the code path related to global "
            + "wakefulness in power manager. The test will be enabled once per display "
            + "interactiveness is supported and power manager to IME communication is enabled on "
            + "partial interactiveness. relevant bugs: b/330610015 b/366045308 b/366037029")
    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @Test
    public void testHideImeAfterBackPressed_ScreenOffOn() throws Exception {
        verifyHideImeBackPressed(true /* appRequestsBackCallback */,
                true /* imeRequestsBackCallback */,
                (instrumentation, editorRef) -> {
                    TestUtils.turnScreenOff();
                    TestUtils.waitOnMainUntil(
                            () -> ((TestActivity) editorRef.get().getContext()).isPaused(),
                            TIMEOUT);
                    TestUtils.turnScreenOn();
                    TestUtils.unlockScreen();
                    TestUtils.waitOnMainUntil(
                            () -> !((TestActivity) editorRef.get().getContext()).isPaused(),
                            TIMEOUT);
                    // Before testing the back procedure, ensure the test activity has the window
                    // focus and the IME visible after screen-on.
                    TestUtils.waitOnMainUntil(editorRef.get()::hasWindowFocus, TIMEOUT);
                    expectImeVisible(TIMEOUT);
                } /* pre back press procedure */);
    }

    @Test
    public void testHideImeAfterBackPressed_rootViewChanges() throws Exception {
        verifyHideImeBackPressed(true /* appRequestsBackCallback */,
                true /* imeRequestsBackCallback */,
                (instrumentation, editorRef) -> {
                    AutoCloseableWrapper<Dialog> dialogWrapper =
                            createDialogWrapper(editorRef.get());
                    instrumentation.waitForIdleSync();
                    // Verify IME became invisible when the Dialog is shown.
                    // Note: the IME is partially visible behind dimmed layer and it won't match
                    // the screenshot.
                    expectImeInvisible(NOT_EXPECT_TIMEOUT);

                    runOnMainSync(() -> dialogWrapper.get().dismiss());
                    // Verify IME became visible when the Dialog has dismissed.
                    expectImeVisible(TIMEOUT);
                } /* pre back press procedure */);
    }

    @Test
    public void testShowHideSoftInputShouldBeIgnoredOnNonFocusedView() throws Exception {
        final InputMethodManager imm = mInstrumentation
                .getTargetContext().getSystemService(InputMethodManager.class);

        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String focusedMarker = getTestMarker(FOCUSED_EDIT_TEXT_TAG);
            final String nonFocusedMarker = getTestMarker(NON_FOCUSED_EDIT_TEXT_TAG);
            final Pair<EditText, EditText> editTextPair =
                    launchTestActivity(focusedMarker, nonFocusedMarker);
            final EditText nonFocusedEditText = editTextPair.second;

            expectEvent(stream, editorMatcher("onStartInput", focusedMarker), TIMEOUT);

            expectImeInvisible(TIMEOUT);
            assertFalse("hasActiveInputConnection() must return false if the View does not have IME"
                            + " focus",
                    getOnMainSync(() -> imm.hasActiveInputConnection(nonFocusedEditText)));
            assertFalse("showSoftInput must fail if the View does not have IME focus",
                    getOnMainSync(() -> imm.showSoftInput(nonFocusedEditText, 0)));
            notExpectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);

            getOnMainSync(() -> imm.hideSoftInputFromWindow(
                    nonFocusedEditText.getWindowToken(), 0));
            // IME was never shown, so there should be no hideSoftInput.
            notExpectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    public void testToggleSoftInput() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker(FOCUSED_EDIT_TEXT_TAG);
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            // Test toggleSoftInputFromWindow() flow
            final InputMethodManager imm = mTestActivity.getSystemService(InputMethodManager.class);
            runOnMainSync(() -> imm.toggleSoftInputFromWindow(editText.getWindowToken(), 0, 0));

            expectEvent(stream.copy(), showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream.copy(), editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Calling toggleSoftInputFromWindow() must hide the IME.
            runOnMainSync(() -> imm.toggleSoftInputFromWindow(editText.getWindowToken(), 0, 0));

            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    @FlakyTest(bugId = 294840051)
    public void testShowHideKeyboardOnWebView() throws Exception {
        final PackageManager pm =
                mInstrumentation.getContext().getPackageManager();
        assumeTrue(pm.hasSystemFeature("android.software.webview"));

        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            final UiObject2 inputTextField = TestWebView.launchTestWebViewActivity(
                    TIMEOUT, marker);
            assertNotNull("Editor must exists on WebView", inputTextField);
            expectImeInvisible(TIMEOUT);

            inputTextField.click();
            expectEvent(stream.copy(), showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);
        }
    }

    @Test
    @FlakyTest(detail = "slow test")
    public void testShowHideKeyboardWithInterval() throws Exception {
        final InputMethodManager imm = mInstrumentation
                .getTargetContext().getSystemService(InputMethodManager.class);

        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker(FOCUSED_EDIT_TEXT_TAG);
            final EditText editText = launchTestActivity(marker);
            expectImeInvisible(TIMEOUT);

            runOnMainSync(() -> imm.showSoftInput(editText, 0));
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Intervals = 10, 20, 30, ..., 100, 150, 200, ...
            final List<Integer> intervals = new ArrayList<>();
            for (int i = 10; i < 100; i += 10) intervals.add(i);
            for (int i = 100; i < 500; i += 50) intervals.add(i);
            // Regression test for b/221483132.
            // WindowInsetsController tries to clean up IME window after IME hide animation is done.
            // Makes sure that IMM#showSoftInput during IME hide animation cancels the cleanup.
            for (int intervalMillis : intervals) {
                runOnMainSync(() -> imm.hideSoftInputFromWindow(editText.getWindowToken(), 0));
                SystemClock.sleep(intervalMillis);
                runOnMainSync(() -> imm.showSoftInput(editText, 0));
                expectImeVisible(TIMEOUT, "IME should be visible. Interval = " + intervalMillis);
            }
        }
    }

    /**
     * Verifies that a hideSoftInputFromWindow call just after a showSoftInput call can succeed.
     *
     * <p>This is a regression test for Bug 21727232.</p>
     */
    @Test
    public void testShowHideKeyboardImmediately() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker(FOCUSED_EDIT_TEXT_TAG);
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final var imm = mTestActivity.getSystemService(InputMethodManager.class);
            assertTrue("hasActiveInputConnection() must return true if the View has IME focus",
                    getOnMainSync(() -> imm.hasActiveInputConnection(editText)));

            // Issue a command with no effect to wait until MockIme becomes idle
            expectCommand(stream, imeSession.callGetWindowLayoutInfo(), TIMEOUT);
            // Copy event stream to check assertion starting from the start of the stream
            notExpectEvent(imeSession.openEventStream(), editorMatcher("onStartInputView", marker),
                    0);

            // Test calling showSoftInput() immediately followed by hideSoftInputFromWindow()
            runOnMainSyncWithRethrowing(() -> {
                assertTrue("showSoftInput must success if the View has IME focus",
                        imm.showSoftInput(editText, 0 /* flags */));
                assertTrue("hideSoftInputFromWindow must success when called right"
                                + " after showSoftInput",
                        imm.hideSoftInputFromWindow(editText.getWindowToken(), 0 /* flags */));
            });

            // Assert showSoftInput() flow was observed
            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);

            // Assert hideSoftInputFromWindow() flow was observed
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
        }
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)
    public void testShowSoftInputWithShowForcedFlagWhenAppIsLeaving() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            // Launch a simple test activity
            final TestActivity testActivity = TestActivity.startSync(activity -> {
                activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                return new LinearLayout(activity);
            });
            TestUtils.waitOnMainUntil(testActivity::hasWindowFocus, TIMEOUT);

            // Launch a test editor activity
            final String marker = getTestMarker();
            final AtomicReference<EditText> ediTextRef = new AtomicReference<>();
            final TestActivity testEditorActivity =
                    new TestActivity.Starter().asNewTask().startSync(activity -> {
                        final LinearLayout layout = new LinearLayout(activity);
                        layout.setOrientation(LinearLayout.VERTICAL);

                        final EditText focusedEditText = new EditText(activity);
                        focusedEditText.setHint("focused editText");
                        focusedEditText.setPrivateImeOptions(marker);
                        focusedEditText.requestFocus();
                        layout.addView(focusedEditText);
                        ediTextRef.set(focusedEditText);
                        return layout;
                    }, TestActivity.class);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), NOT_EXPECT_TIMEOUT);
            expectImeInvisible(TIMEOUT);

            final InputMethodManager imm = testActivity.getSystemService(InputMethodManager.class);
            assertTrue("hasActiveInputConnection() must return true if the View has IME focus",
                    getOnMainSync(() -> imm.hasActiveInputConnection(ediTextRef.get())));

            // Test showSoftInput() flow with adding SHOW_FORCED flag
            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() ->
                            imm.showSoftInput(ediTextRef.get(), InputMethodManager.SHOW_FORCED)));

            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Finish testEditorActivity
            runOnMainSync(testEditorActivity::finish);

            // Verify soft-keyboard will not visible when enabling the platform compat flag to
            // clear SHOW_FOCED flag. Otherwise, keeping the legacy behavior of SHOW_FOCED that
            // soft-keyboard remains visible if there is no explicit hiding request.
            if (isClearShowForcedFlagEnabled(testActivity.getPackageName())) {
                notExpectEvent(stream, eventMatcher("showSoftInput"),
                        NOT_EXPECT_TIMEOUT);
                expectImeInvisible(TIMEOUT);
            } else {
                expectEvent(stream, eventMatcher("showSoftInput"), TIMEOUT);
                expectImeVisible(TIMEOUT);
            }
        }
    }

    @Test
    public void testFloatingImeHideKeyboardAfterBackPressed() throws Exception {
        final Instrumentation instrumentation = mInstrumentation;

        // Initial MockIme with floating IME settings.
        try (MockImeSession imeSession = MockImeSession.create(
                instrumentation.getContext(), instrumentation.getUiAutomation(),
                getFloatingImeSettings(Color.BLACK))) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker(FOCUSED_EDIT_TEXT_TAG);
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            final InputMethodManager imm = mTestActivity.getSystemService(InputMethodManager.class);
            assertTrue("hasActiveInputConnection() must return true if the View has IME focus",
                    getOnMainSync(() -> imm.hasActiveInputConnection(editText)));

            // Test showSoftInput() flow
            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText, 0)));

            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Pressing back key, expect soft-keyboard will become invisible.
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    public void testImeVisibilityWhenDismissingDialogWithImeFocused() throws Exception {
        final Instrumentation instrumentation = mInstrumentation;
        try (MockImeSession imeSession = MockImeSession.create(
                instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            // Launch a simple test activity
            final TestActivity testActivity =
                    new TestActivity.Starter()
                            .withWindowingMode(WINDOWING_MODE_FULLSCREEN)
                            .startSync(LinearLayout::new, TestActivity.class);

            // Launch a dialog
            final String marker = getTestMarker();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            final AtomicReference<AlertDialog> dialogRef = new AtomicReference<>();
            TestUtils.runOnMainSync(() -> {
                final EditText editText = new EditText(testActivity);
                editText.setHint("focused editText");
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                final AlertDialog dialog = new AlertDialog.Builder(testActivity)
                        .setView(editText)
                        .create();
                final WindowInsetsController.OnControllableInsetsChangedListener listener =
                        new WindowInsetsController.OnControllableInsetsChangedListener() {
                            @Override
                            public void onControllableInsetsChanged(
                                    @NonNull WindowInsetsController controller, int typeMask) {
                                if ((typeMask & ime()) != 0) {
                                    editText.getWindowInsetsController()
                                            .removeOnControllableInsetsChangedListener(this);
                                    editText.getWindowInsetsController().show(ime());
                                }
                            }
                        };
                dialog.show();
                editText.getWindowInsetsController().addOnControllableInsetsChangedListener(
                        listener);
                editTextRef.set(editText);
                dialogRef.set(dialog);
            });
            TestUtils.waitOnMainUntil(() -> dialogRef.get().isShowing()
                    && editTextRef.get().hasFocus(), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, eventMatcher("showSoftInput"), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Hide keyboard and dismiss dialog.
            TestUtils.runOnMainSync(() -> {
                editTextRef.get().getWindowInsetsController().hide(ime());
                dialogRef.get().dismiss();
            });

            // Expect onFinishInput called and keyboard should hide successfully.
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);

            // onWindowVisibilityChanged event can be out of sequence. Creating
            // a copy of the ImeEventStream to handle this event.
            final ImeEventStream streamCopy = stream.copy();

            // Expect fallback input connection started and keyboard invisible after activity
            // focused unless avoidable keyboard startup is desired,
            // in which case, no fallback will be started.
            if (!isPreventImeStartup()) {
                final ImeEvent onStart = expectEvent(stream, eventMatcher("onStartInput"), TIMEOUT);
                assertTrue(onStart.getEnterState().hasFallbackInputConnection());
            }
            TestUtils.waitOnMainUntil(testActivity::hasWindowFocus, TIMEOUT);
            expectEventWithKeyValue(streamCopy, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @RequireNotVisibleBackgroundUsers(reason =
            "Background visible user devices (primarily Android auto) currently doesn't support "
            + "per display interactiveness. So when the screen Off event is sent, "
            + "PowerManager#IsInteractive is still true while driver screen is off as passenger "
            + "screens are on. It also doesn't trigger the code path related to global "
            + "wakefulness in power manager. The test will be enabled once per display "
            + "interactiveness is supported and power manager to IME communication is enabled on "
            + "partial interactiveness. relevant bugs: b/330610015 b/366045308 b/366037029")
    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @Test
    public void testImeState_Unspecified_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_UNSPECIFIED);
    }

    @RequireNotVisibleBackgroundUsers(reason =
            "Background visible user devices (primarily Android auto) currently doesn't support "
            + "per display interactiveness. So when the screen Off event is sent, "
            + "PowerManager#IsInteractive is still true while driver screen is off as passenger "
            + "screens are on. It also doesn't trigger the code path related to global "
            + "wakefulness in power manager. The test will be enabled once per display "
            + "interactiveness is supported and power manager to IME communication is enabled on "
            + "partial interactiveness. relevant bugs: b/330610015 b/366045308 b/366037029")
    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @Test
    public void testImeState_Visible_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_VISIBLE);
    }

    @RequireNotVisibleBackgroundUsers(reason =
            "Background visible user devices (primarily Android auto) currently doesn't support "
            + "per display interactiveness. So when the screen Off event is sent, "
            + "PowerManager#IsInteractive is still true while driver screen is off as passenger "
            + "screens are on. It also doesn't trigger the code path related to global "
            + "wakefulness in power manager. The test will be enabled once per display "
            + "interactiveness is supported and power manager to IME communication is enabled on "
            + "partial interactiveness. relevant bugs: b/330610015 b/366045308 b/366037029")
    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @Test
    public void testImeState_AlwaysVisible_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    @RequireNotVisibleBackgroundUsers(reason =
            "Background visible user devices (primarily Android auto) currently doesn't support "
            + "per display interactiveness. So when the screen Off event is sent, "
            + "PowerManager#IsInteractive is still true while driver screen is off as passenger "
            + "screens are on. It also doesn't trigger the code path related to global "
            + "wakefulness in power manager. The test will be enabled once per display "
            + "interactiveness is supported and power manager to IME communication is enabled on "
            + "partial interactiveness. relevant bugs: b/330610015 b/366045308 b/366037029")
    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @Test
    public void testImeState_Hidden_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_HIDDEN);
    }

    @RequireNotVisibleBackgroundUsers(reason =
            "Background visible user devices (primarily Android auto) currently doesn't support "
            + "per display interactiveness. So when the screen Off event is sent, "
            + "PowerManager#IsInteractive is still true while driver screen is off as passenger "
            + "screens are on. It also doesn't trigger the code path related to global "
            + "wakefulness in power manager. The test will be enabled once per display "
            + "interactiveness is supported and power manager to IME communication is enabled on "
            + "partial interactiveness. relevant bugs: b/330610015 b/366045308 b/366037029")
    @AppModeFull(reason = "KeyguardManager is not accessible from instant apps")
    @Test
    public void testImeState_AlwaysHidden_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void runImeDoesntReshowAfterKeyguardTest(int softInputState) throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            // Launch a simple test activity
            final TestActivity testActivity =
                    new TestActivity.Starter()
                            .withWindowingMode(WINDOWING_MODE_FULLSCREEN)
                            .startSync(LinearLayout::new, TestActivity.class);

            // Launch a dialog and show keyboard
            final String marker = getTestMarker();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            final AtomicReference<AlertDialog> dialogRef = new AtomicReference<>();
            TestUtils.runOnMainSync(() -> {
                final EditText editText = new EditText(testActivity);
                editText.setHint("focused editText");
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                final AlertDialog dialog = new AlertDialog.Builder(testActivity)
                        .setView(editText)
                        .create();
                dialog.getWindow().setSoftInputMode(softInputState);
                // Tracking onFocusChange callback for debugging purpose.
                editText.setOnFocusChangeListener((v, hasFocus) -> {
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "Editor " + editText + " hasFocus=" + hasFocus, new Throwable());
                    }
                });
                dialog.show();
                editText.getWindowInsetsController().show(ime());
                editTextRef.set(editText);
                dialogRef.set(dialog);
            });

            try (AutoCloseableWrapper<AlertDialog> dialogCloseWrapper = AutoCloseableWrapper.create(
                    dialogRef.get(), dialog -> TestUtils.runOnMainSync(dialog::dismiss))) {
                TestUtils.waitOnMainUntil(() -> dialogRef.get().isShowing()
                        && editTextRef.get().hasFocus(), TIMEOUT);
                expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
                expectEvent(stream, eventMatcher("showSoftInput"), TIMEOUT);
                // Copy the event stream to verify both events in case expectEvent missed the
                // event verification if the actual event sequence has flipped.
                expectEvent(stream.copy(), editorMatcher("onStartInputView", marker), TIMEOUT);
                expectEventWithKeyValue(stream.copy(), "onWindowVisibilityChanged", "visible",
                        View.VISIBLE, TIMEOUT);
                expectImeVisible(TIMEOUT);

                TestUtils.turnScreenOff();
                // Clear editor focus after screen-off
                TestUtils.runOnMainSync(editTextRef.get()::clearFocus);

                TestUtils.waitOnMainUntil(() -> editTextRef.get().getWindowVisibility() != VISIBLE,
                        TIMEOUT);
                expectEvent(stream, onFinishInputViewMatcher(true), TIMEOUT);
                if (imeSession.isFinishInputNoFallbackConnectionEnabled()) {
                    // When IME enabled the new app compat behavior to finish input without fallback
                    // input connection when device interactive state changed,
                    // we expect onFinishInput happens without any additional fallback input
                    // connection started and no showShowSoftInput requested.
                    expectEvent(stream, eventMatcher("onFinishInput"),
                            TIMEOUT);
                    notExpectEvent(stream, eventMatcher("showSoftInput"),
                            NOT_EXPECT_TIMEOUT);
                } else {
                    // For legacy IME, the fallback input connection will started after screen-off.
                    expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
                    expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
                    // Expect showSoftInput comes when system notify InsetsController to apply
                    // show IME insets after IME input target updated.
                    expectEvent(stream, eventMatcher("showSoftInput"),
                            TIMEOUT);
                    notExpectEvent(stream, hideSoftInputMatcher(), NOT_EXPECT_TIMEOUT);
                }

                // Verify IME will invisible after device unlocked
                TestUtils.turnScreenOn();
                TestUtils.unlockScreen();
                // Expect hideSoftInput will called by IMMS when the same window
                // focused since the editText view focus has been cleared.
                TestUtils.waitOnMainUntil(() -> editTextRef.get().hasWindowFocus()
                        && !editTextRef.get().hasFocus(), TIMEOUT);
                expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
                if (!imeSession.isFinishInputNoFallbackConnectionEnabled()) {
                    expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
                }
                expectImeInvisible(TIMEOUT);
            }
        }
    }

    @AppModeFull
    @Test
    public void testImeVisibilityWhenImeTransitionBetweenActivities_Full() throws Exception {
        runImeVisibilityWhenImeTransitionBetweenActivities(false /* instant */);
    }

    @AppModeInstant
    @Test
    public void testImeVisibilityWhenImeTransitionBetweenActivities_Instant() throws Exception {
        runImeVisibilityWhenImeTransitionBetweenActivities(true /* instant */);
    }

    @AppModeFull
    @Test
    public void testImeInvisibleWhenForceStopPkgProcess_Full() throws Exception {
        runImeVisibilityTestWhenForceStopPackage(false /* instant */);
    }

    @AppModeInstant
    @Test
    public void testImeInvisibleWhenForceStopPkgProcess_Instant() throws Exception {
        runImeVisibilityTestWhenForceStopPackage(true /* instant */);
    }

    @Test
    public void testRestoreImeVisibility() throws Exception {
        // TODO(b/226110728): Remove after we can send ime restore signal to DisplayAreaOrganizer.
        assumeFalse(isImeOrganized(DEFAULT_DISPLAY));
        runRestoreImeVisibility(TestSoftInputMode.UNCHANGED_WITH_BACKWARD_NAV, true);
    }

    @Test
    public void testRestoreImeVisibility_noRestoreForAlwaysHidden() throws Exception {
        runRestoreImeVisibility(TestSoftInputMode.ALWAYS_HIDDEN_WITH_BACKWARD_NAV, false);
    }

    @Test
    public void testRestoreImeVisibility_noRestoreForHiddenWithForwardNav() throws Exception {
        runRestoreImeVisibility(TestSoftInputMode.HIDDEN_WITH_FORWARD_NAV, false);
    }

    /**
     * Test case for Bug 225028378.
     *
     * <p>This test ensures that showing a non-ime-focusable {@link PopupWindow} with
     * {@link PopupWindow#INPUT_METHOD_NOT_NEEDED} will be on top of the IME.</p>
     */
    @Test
    public void testNonImeFocusablePopupWindow_onTopOfIme() throws Exception {
        final Instrumentation instrumentation = mInstrumentation;
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            final AtomicReference<EditText> editorRef = new AtomicReference<>();
            new TestActivity.Starter().withWindowingMode(
                    WINDOWING_MODE_FULLSCREEN).startSync(activity -> {
                        final LinearLayout layout = new LinearLayout(activity);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setGravity(Gravity.BOTTOM);
                        final EditText editText = new EditText(activity);
                        editorRef.set(editText);
                        editText.setHint("focused editText");
                        editText.setPrivateImeOptions(marker);
                        editText.requestFocus();
                        layout.addView(editText);
                        return layout;
                    }, TestActivity.class);
            // Show IME.
            runOnMainSync(() -> editorRef.get().getContext().getSystemService(
                    InputMethodManager.class).showSoftInput(editorRef.get(), 0));

            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Create then show a Dialog.
            try (AutoCloseableWrapper<Dialog> dialogWrapper =
                         createDialogWrapper(editorRef.get())) {
                instrumentation.waitForIdleSync();
                // Verify IME became invisible when the Dialog is shown.
                // Note: the IME is partially visible behind dimmed layer and it won't match
                // the screenshot.
                expectImeInvisible(NOT_EXPECT_TIMEOUT);

                runOnMainSync(() -> dialogWrapper.get().dismiss());
                // Verify IME became visible when the Dialog has dismissed.
                expectImeVisible(TIMEOUT);
            }
        }
    }

    /**
     * Test case for Bug 228766370.
     *
     * <p>This test ensures that IME will visible on an ime-focusable overlay window when another
     * activity behind the overlay that requests to show IME. <p/>
     */
    @Test
    public void testImeVisibleOnImeFocusableOverlay() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder()
                        .setInputViewHeight(NEW_KEYBOARD_HEIGHT)
                        .setDrawsBehindNavBar(true))) {
            final ImeEventStream stream = imeSession.openEventStream();
            TestActivity testActivity = TestActivity.startSync(activity -> {
                final View view = new View(activity);
                view.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
                return view;
            });

            // Show an overlay with "IME Focusable" (NOT_FOCUSABLE | ALT_FOCUSABLE_IM) flags.
            runOnMainSync(() -> SystemUtil.runWithShellPermissionIdentity(() ->
                    testActivity.showOverlayWindow(true /* imeFocusable */)));
            mInstrumentation.waitForIdleSync();

            // Start a next activity to expect IME should visible on top of the overlay.
            final String marker = getTestMarker();
            final AtomicReference<EditText> editorRef = new AtomicReference<>();
            new TestActivity.Starter().asNewTask().startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setGravity(Gravity.BOTTOM);
                final EditText editText = new EditText(activity);
                editorRef.set(editText);
                editText.setHint("focused editText");
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                layout.addView(editText);
                return layout;
            }, TestActivity.class);
            // Show IME.
            runOnMainSync(() -> editorRef.get().getContext().getSystemService(
                    InputMethodManager.class).showSoftInput(editorRef.get(), 0));

            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);
        }
    }

    private enum TestSoftInputMode {
        UNCHANGED_WITH_BACKWARD_NAV,
        ALWAYS_HIDDEN_WITH_BACKWARD_NAV,
        HIDDEN_WITH_FORWARD_NAV
    }

    private void runRestoreImeVisibility(TestSoftInputMode mode, boolean expectImeVisible)
            throws Exception {
        final Instrumentation instrumentation = mInstrumentation;
        final WindowManager wm = instrumentation.getContext().getSystemService(WindowManager.class);
        // As restoring IME visibility behavior is only available when TaskSnapshot mechanism
        // enabled, skip the test when TaskSnapshot is not supported.
        assumeTrue("Restoring IME visibility not available when TaskSnapshot unsupported",
                wm.isTaskSnapshotSupported());

        try (MockImeSession imeSession = MockImeSession.create(
                instrumentation.getContext(), instrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String markerForActivity1 = getTestMarker(FIRST_EDIT_TEXT_TAG);
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            // Launch a test activity with focusing editText to show keyboard
            new TestActivity.Starter().withWindowingMode(
                    WINDOWING_MODE_FULLSCREEN).startSync(activity -> {
                        final LinearLayout layout = new LinearLayout(activity);
                        final EditText editText = new EditText(activity);
                        editTextRef.set(editText);
                        editText.setHint("focused editText");
                        editText.setPrivateImeOptions(markerForActivity1);
                        editText.requestFocus();
                        layout.addView(editText);
                        activity.getWindow().getDecorView().getWindowInsetsController().show(ime());
                        if (mode == TestSoftInputMode.ALWAYS_HIDDEN_WITH_BACKWARD_NAV) {
                            activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                        }
                        return layout;
                    }, TestActivity.class);

            expectEvent(stream, editorMatcher("onStartInput", markerForActivity1), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", markerForActivity1), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Launch another app task activity to hide keyboard
            new TestActivity.Starter().asNewTask().withWindowingMode(
                    WINDOWING_MODE_FULLSCREEN).startSync(activity -> {
                        activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                        return new LinearLayout(activity);
                    }, TestActivity.class);
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);

            if (mode == TestSoftInputMode.HIDDEN_WITH_FORWARD_NAV) {
                // Start new TestActivity on the same task with STATE_HIDDEN softInputMode.
                final String markerForActivity2 = getTestMarker(SECOND_EDIT_TEXT_TAG);
                new TestActivity.Starter().asSameTaskAndClearTop().withWindowingMode(
                        WINDOWING_MODE_FULLSCREEN).startSync(activity -> {
                            final LinearLayout layout = new LinearLayout(activity);
                            final EditText editText = new EditText(activity);
                            editText.setHint("focused editText");
                            editText.setPrivateImeOptions(markerForActivity2);
                            editText.requestFocus();
                            layout.addView(editText);
                            activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_HIDDEN);
                            return layout;
                        }, TestActivity.class);
                expectEvent(stream, editorMatcher("onStartInput", markerForActivity2), TIMEOUT);
            } else {
                // Press back key to back to the first test activity
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                expectEvent(stream, editorMatcher("onStartInput", markerForActivity1), TIMEOUT);
            }

            // Expect the IME visibility according to expectImeVisible
            // The expected result could be:
            //  1) The system can restore the IME visibility to show IME up when navigated back to
            //     the original app task, even the IME is hidden when switching to the next task.
            //  2) The system won't restore the IME visibility in some softInputMode cases.
            if (expectImeVisible) {
                expectImeVisible(TIMEOUT);
            } else {
                expectImeInvisible(TIMEOUT);
            }
        }
    }

    private void runImeVisibilityWhenImeTransitionBetweenActivities(boolean instant)
            throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder()
                        .setInputViewHeight(NEW_KEYBOARD_HEIGHT)
                        .setDrawsBehindNavBar(true))) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();

            AtomicReference<EditText> editTextRef = new AtomicReference<>();
            // Launch test activity with focusing editor
            final TestActivity testActivity =
                    new TestActivity.Starter().withWindowingMode(
                            WINDOWING_MODE_FULLSCREEN).startSync(activity -> {
                                final LinearLayout layout = new LinearLayout(activity);
                                layout.setOrientation(LinearLayout.VERTICAL);
                                layout.setGravity(Gravity.BOTTOM);
                                final EditText editText = new EditText(activity);
                                editTextRef.set(editText);
                                editText.setHint("focused editText");
                                editText.setPrivateImeOptions(marker);
                                editText.requestFocus();
                                layout.addView(editText);
                                final View decorView = activity.getWindow().getDecorView();
                                decorView.setFitsSystemWindows(true);
                                decorView.getWindowInsetsController().show(ime());
                                return layout;
                            }, TestActivity.class);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, eventMatcher("showSoftInput"), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            WindowInsets initialRootWindowInsets =
                    testActivity.getWindow().getDecorView().getRootWindowInsets();

            // Launch another test activity from another process with popup dialog.
            MockTestActivityUtil.launchSync(instant, TIMEOUT,
                    Map.of(MockTestActivityUtil.EXTRA_KEY_SHOW_DIALOG, "true"));
            BySelector dialogSelector = By.clazz(AlertDialog.class).depth(0);
            UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
            assertNotNull(uiDevice.wait(Until.hasObject(dialogSelector), TIMEOUT));

            // Dismiss dialog and back to original test activity
            MockTestActivityUtil.sendBroadcastAction(MockTestActivityUtil.EXTRA_DISMISS_DIALOG,
                    mUserHelper.getUserId());

            // Verify keyboard visibility should aligned with IME insets visibility.
            TestUtils.waitOnMainUntil(
                    () -> testActivity.getWindow().getDecorView().getVisibility() == VISIBLE
                            && testActivity.getWindow().getDecorView().hasWindowFocus(), TIMEOUT);
            // Wait for layout being stable in case insets visibility might not align with the
            // input view visibility.
            waitForInputViewLayoutStable(stream, LAYOUT_STABLE_THRESHOLD);

            if (initialRootWindowInsets.isVisible(WindowInsets.Type.ime())) {
                expectImeVisible(TIMEOUT);
            } else {
                expectImeInvisible(TIMEOUT);
            }
            assertEquals(initialRootWindowInsets.getInsets(WindowInsets.Type.ime()),
                    testActivity.getWindow().getDecorView().getRootWindowInsets().getInsets(
                            WindowInsets.Type.ime()));
        }
    }

    private void runImeVisibilityTestWhenForceStopPackage(boolean instant) throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();

            // Make sure that MockIme isn't shown in the initial state.
            final ImeLayoutInfo lastLayout =
                    waitForInputViewLayoutStable(stream, LAYOUT_STABLE_THRESHOLD);
            assertNull(lastLayout);
            expectImeInvisible(TIMEOUT);
            // Flush all the events happened before launching the test Activity.
            stream.skipAll();

            // Launch test activity with focusing an editor from remote process and expect the
            // IME is visible.
            try (AutoCloseable closable = MockTestActivityUtil.launchSync(
                    instant, TIMEOUT,
                    Map.of(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, marker))) {
                expectEvent(stream, editorMatcher("onStartInput", marker), START_INPUT_TIMEOUT);
                expectImeInvisible(TIMEOUT);

                // Request showSoftInput, expect the request is valid and soft-keyboard visible.
                MockTestActivityUtil.sendBroadcastAction(
                        MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT, mUserHelper.getUserId());
                expectEvent(stream, eventMatcher("showSoftInput"), TIMEOUT);
                expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
                expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                        View.VISIBLE, TIMEOUT);
                expectImeVisible(TIMEOUT);

                // Force stop test app package, and then expect IME should be invisible after the
                // remote process stopped by forceStopPackage.
                MockTestActivityUtil.forceStopPackage();
                expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
                expectImeInvisible(TIMEOUT);
            }
        }
    }

    /**
     * Test case for Bug 254624767.
     *
     * <p>This test ensures that when the app requests to show/hide IME according to the IME insets
     * visibility with {@link WindowInsets#isVisible(int)} during switching apps, verify the system
     * dispatches the IME insets visibility to the app correctly during that time. </p>
     */
    @Test
    public void testImeInsetsInvisibleAfterBackingFromImeHiddenActivity() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker(FOCUSED_EDIT_TEXT_TAG);

            // Launch the first activity
            final EditText editText = launchTestActivity(marker);
            AtomicReference<CountDownLatch> imeInsetsHiddenLatchRef = new AtomicReference<>();
            expectEvent(stream, editorMatcher("onStartInput", marker), START_INPUT_TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            TestUtils.runOnMainSync(() -> {
                        // Show IME with WindowInsets API and register onApplyWindowInsetsListener
                        editText.getRootView().setOnApplyWindowInsetsListener(
                                (v, insets) -> {
                                    if (!insets.isVisible(WindowInsets.Type.ime())) {
                                        if (imeInsetsHiddenLatchRef.get() != null) {
                                            imeInsetsHiddenLatchRef.get().countDown();
                                        }
                                    }
                                    return v.onApplyWindowInsets(insets);
                                });
                        editText.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
                            // Test scenario: emulate the issue app implements to show IME when
                            // focusing back if the last requested IME insets visibility is true.
                            // And hides IME with clearing the editor focus when the app focus-out.
                            if (hasFocus) {
                                final boolean hasImeShown =
                                        editText.getRootWindowInsets().isVisible(ime());
                                if (hasImeShown) {
                                    editText.getWindowInsetsController().show(ime());
                                }
                            } else {
                                editText.getWindowInsetsController().hide(ime());
                                editText.clearFocus();
                            }
                        });
                        editText.getWindowInsetsController().show(ime());
                    }
            );
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Launch the second test activity with expecting to hide the IME.
            final TestActivity secondActivity = new TestActivity.Starter()
                    .asNewTask().startSync(activity -> {
                        activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                        return new LinearLayout(activity);
                    }, TestActivity.class);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);

            // Back to first activity
            imeInsetsHiddenLatchRef.set(new CountDownLatch(1));
            runOnMainSync(secondActivity::onBackPressed);

            // Verify the visibility of IME insets should be hidden in onApplyWindowInsets and
            // expect the first activity hides the IME according to the received IME insets
            // visibility when backing from the second activity.
            imeInsetsHiddenLatchRef.get().await(5, TimeUnit.SECONDS);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), NOT_EXPECT_TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    /**
     * Test case for Bug 256739702.
     *
     * <p>This test ensures that when "android:screenSize|Orientation" is not set and the keyboard
     * is shown implicitly in fullscreen mode, it will not disappear after the user rotates screen.
     */
    @Test
    public void testRotateScreenWithKeyboardShownImplicitly() throws Exception {
        // Test only when both portrait and landscape mode are supported.
        final PackageManager pm = mInstrumentation.getTargetContext().getPackageManager();
        assumeTrue(pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT));
        assumeTrue(pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE));
        final boolean isFixedToUserRotation =
                "enabled".equals(SystemUtil.runShellCommand(FIXED_TO_USER_ROTATION_CMD).trim());
        assumeFalse("Device shouldn't have fixed rotation.", isFixedToUserRotation);

        final InputMethodManager imm = mInstrumentation
                .getTargetContext().getSystemService(InputMethodManager.class);
        // Disable auto-rotate screen and set the screen orientation to portrait mode.
        setAutoRotateScreen(false);
        final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
        uiDevice.setOrientationPortrait();
        mInstrumentation.waitForIdleSync();

        // Set FullscreenModePolicy as OS_DEFAULT to call the original
        // InputMethodService#onEvaluateFullscreenMode()
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder().setFullscreenModePolicy(
                        ImeSettings.FullscreenModePolicy.OS_DEFAULT))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            // TestActivity2 is identical to TestActivity but doesn't handle any configChanges.
            final EditText editText = launchTestActivity2(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);
            assertTrue("hasActiveInputConnection() must return true if the View has IME focus",
                    getOnMainSync(() -> imm.hasActiveInputConnection(editText)));
            assumeFalse("onEvaluateFullscreenMode() should be false for portrait",
                    expectCommand(
                            stream, imeSession.callGetOnEvaluateFullscreenMode(), TIMEOUT)
                            .getReturnBooleanValue());

            // Call ShowSoftInput() implicitly
            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText,
                            InputMethodManager.SHOW_IMPLICIT)));

            expectEvent(stream, showSoftInputMatcher(0), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Rotate screen to landscape.
            uiDevice.setOrientationLandscape();
            mInstrumentation.waitForIdleSync();
            expectImeVisible(TIMEOUT);
            assertTrue("IME should be in fullscreen mode",
                    getOnMainSync(() -> imm.isFullscreenMode()));
        } finally {
            setAutoRotateScreen(true);
            uiDevice.setOrientationNatural();
        }
    }

    /**
     * Test case for Bug 222064495.
     *
     * <p>Test that the IME should be hidden when the IME layering target overlay with
     * "NOT_FOCUSABLE | ALT_FOCUSABLE_IM" flags popup during pressing the recents key to the
     * overview screen.</b>
     */
    @RequireNotVisibleBackgroundUsers(reason =
            "To pass on secondary user on secondary display, this test should use UinputKeyboard "
            + "instead of `mInstrumentation.sendKeyDownUpSync`. b/368974455 will allow "
            + "UinputKeyboard to send KeyEvents to specific displays.")
    @Test
    public void testImeHiddenWhenImeLayeringTargetDelayedToShowInAppSwitch() throws Exception {
        assumeTrue(hasRecentsScreen());

        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();

            final TestActivity testActivity = TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity);
                layout.addView(editText);
                editText.setHint("focused editText");
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                activity.getWindow().getDecorView().getWindowInsetsController().show(ime());
                return layout;
            });

            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Intentionally showing an overlay with "IME Focusable" (NOT_FOCUSABLE |
            // ALT_FOCUSABLE_IM) flags during pressing the recents key to the overview screen.
            testActivity.getWindow().getDecorView().postDelayed(
                    () -> SystemUtil.runWithShellPermissionIdentity(() ->
                    testActivity.showOverlayWindow(true /* imeFocusable */)), 100);
            // TODO(b/368974455): Use UinputKeyboard instead.
            mInstrumentation.sendKeyDownUpSync(
                    KeyEvent.KEYCODE_RECENT_APPS);

            // Expect the IME should hidden by the IME not attachable on the activity when the
            // overlay popup.
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);
        } finally {
            // Back to home to clean up states after the test finished.
            // TODO(b/368974455): Use UinputKeyboard instead.
            UiDevice.getInstance(mInstrumentation).pressHome();
        }
    }

    /**
     * Test the IME visibility when in split-screen mode, switching the focus to the app task with
     * {@link WindowManager.LayoutParams#SOFT_INPUT_STATE_HIDDEN} flag from the app showing the
     * IME will expect to be hidden.
     */
    @Test
    public void testImeHiddenWhenFocusToAppWithStateHiddenFlagInMultiWindowMode() throws Exception {
        assumeTrue(TestUtils.supportsSplitScreenMultiWindow());

        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();

            // Launch an editor activity to be on the split primary task.
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            final TestActivity splitPrimaryActivity = TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                final EditText editText = new EditText(activity);
                editTextRef.set(editText);
                layout.addView(editText);
                editText.setHint("focused editText");
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                return layout;
            });
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), NOT_EXPECT_TIMEOUT);
            expectImeInvisible(TIMEOUT);

            // Launch another activity with SOFT_INPUT_STATE_HIDDEN flag to be on the split
            // secondary task, expect the IME won't receive onStartInputView and invisible.
            final TestActivity splitSecondaryActivity = new TestActivity.Starter()
                    .asMultipleTask()
                    .withAdditionalFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                    .startSync(splitPrimaryActivity, activity -> {
                        activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_HIDDEN);
                        return new LinearLayout(activity);
                    }, TestActivity2.class);
            notExpectEvent(stream, eventMatcher("onStartInputView"),
                    NOT_EXPECT_TIMEOUT);
            expectImeInvisible(TIMEOUT);

            final var editText = editTextRef.get();
            final var display = editText.getContext().getDisplay();
            /*
             * Since this test relies on window focus with multiple windows involved, we need to
             * use a global method of emulating touch that goes through the entire pipeline. This
             * ensures that the window manager is aware of the tap that occurred, and provides
             * window focus to the tapped window.
             */
            try (var touch = new UinputTouchScreen(mInstrumentation, display)) {
                // Tap the editor on the split primary task to focus the window and show the IME.
                touch.tapOnViewCenter(editText);
                // TODO(b/280797309): The first tap sends the IME show request before the
                //  input focus changes, so we have to wait for that and tap again.
                TestUtils.waitOnMainUntil(editText::hasWindowFocus, TIMEOUT);
                touch.tapOnViewCenter(editText);

                expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
                expectImeVisible(TIMEOUT);

                // Tap the split secondary task to switch focus and expect the IME will be hidden.
                touch.tapOnViewCenter(splitSecondaryActivity.getWindow().getDecorView());
            }
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    /**
     * Test case for Bug 226689544.
     *
     * Test to verify that the IME is visible, after the following actions:
     * 1. Open primary activity.
     * 2. Open second activity from the first activity in split screen.
     * 3. Open and show a dialog with editText in the second activity.
     * 4. Focus/click on first activity, then click on the editText.
     */
    @Test
    @FlakyTest
    public void testIMEVisibleInSplitScreenAfterGainingFocus() throws Exception {
        assumeTrue(TestUtils.supportsSplitScreenMultiWindow());

        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();

            final TestActivity splitPrimaryActivity = TestActivity.startSync(LinearLayout::new);

            final AtomicReference<AlertDialog> dialogRef = new AtomicReference<>();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            try {
                // Launch another activity with SOFT_INPUT_STATE_UNCHANGED flag to be on the split
                // secondary task as well as on its dialog, so that the IME is not visible by
                // default on large screens, when showing the dialog.
                new TestActivity.Starter()
                        .asMultipleTask()
                        .withAdditionalFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                        .startSync(splitPrimaryActivity, activity -> {
                            activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_UNCHANGED);

                            final EditText editText = new EditText(activity);
                            editText.setHint("focused editText");
                            editText.setPrivateImeOptions(marker);
                            editText.requestFocus();
                            final AlertDialog dialog = new AlertDialog.Builder(activity)
                                    .setTitle("DialogWithEditText")
                                    .setCancelable(false)
                                    .setView(editText)
                                    .create();
                            dialog.getWindow().setSoftInputMode(SOFT_INPUT_STATE_UNCHANGED);
                            dialog.show();
                            dialogRef.set(dialog);
                            editTextRef.set(editText);
                            return new LinearLayout(activity);
                        }, TestActivity2.class);

                View decor = splitPrimaryActivity.getWindow().getDecorView();
                CountDownLatch latch = new CountDownLatch(1);
                ViewTreeObserver observer = decor.getViewTreeObserver();
                observer.addOnDrawListener(() -> {
                    if (splitPrimaryActivity.isInMultiWindowMode()) {
                        // check activity in multi-window mode after relayoutWindow.
                        latch.countDown();
                    }
                });

                latch.await(LAYOUT_STABLE_THRESHOLD, TimeUnit.MILLISECONDS);

                final var editText = editTextRef.get();
                final var display = editText.getContext().getDisplay();
                /*
                 * Since this test relies on window focus with multiple windows involved, we need
                 * to use a global method of emulating touch that goes through the entire pipeline.
                 * This ensures that the window manager is aware of the tap that occurred, and
                 * provides window focus to the tapped window.
                 */
                try (var touch = new UinputTouchScreen(mInstrumentation, display)) {
                    // Tap on the first activity to change focus
                    touch.tapOnViewCenter(splitPrimaryActivity.getWindow().getDecorView());

                    notExpectEvent(stream, eventMatcher("onStartInputView"),
                            NOT_EXPECT_TIMEOUT);
                    expectImeInvisible(TIMEOUT);

                    // Tap on the edit text in the split dialog to show the IME.
                    touch.tapOnViewCenter(editText);
                    // TODO(b/280797309): The first tap sends the IME show request before the
                    //  input focus changes, so we have to wait for that and tap again.
                    TestUtils.waitOnMainUntil(editText::hasWindowFocus, TIMEOUT);
                    touch.tapOnViewCenter(editText);
                    // wait on event to make sure touch event is injected.
                    expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
                    expectImeVisible(TIMEOUT);
                }
            } finally {
                // dismiss dialog, in case it wasn't closed properly
                if (dialogRef.get() != null) {
                    dialogRef.get().dismiss();
                }
            }
        }
    }

    /**
     * A regression Test for Bug 283342812
     *
     * 1. Open primary activity.
     * 2. Open second activity with 2 editText views from the first activity in split screen.
     * 3. Focus the 1st editor and invoke {@link WindowInsetsController#show} to make IME visible.
     * 4. Press the back key to make IME invisible.
     * 5. Focus the 2nd editor and invoke {@link WindowInsetsController#show} to make IME visible.
     * 6. Finish the primary activity to exit split screen mode.
     * 7. Test step 3-5 again to ensure it passes after exiting split screen mode.
     */
    @Test
    @FlakyTest
    public void testIMEVisibleInSplitScreenWithWindowInsetsApi() throws Throwable {
        assumeTrue(TestUtils.supportsSplitScreenMultiWindow());

        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final TestActivity splitPrimaryActivity = TestActivity.startSync(LinearLayout::new);

            // Launch another test activity in split-screen with 2 editor views
            final AtomicReference<EditText> editText1Ref = new AtomicReference<>();
            final AtomicReference<EditText> editText2Ref = new AtomicReference<>();
            final String editText1Marker = getTestMarker(FIRST_EDIT_TEXT_TAG);
            final String editText2Marker = getTestMarker(SECOND_EDIT_TEXT_TAG);
            final TestActivity testActivity2 = new TestActivity.Starter()
                    .asMultipleTask()
                    .withAdditionalFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                    .startSync(splitPrimaryActivity, activity -> {
                        LinearLayout layout = new LinearLayout(activity);
                        activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN);

                        final EditText editText1 = new EditText(activity);
                        editText1.setHint("This is editText1");
                        editText1.setPrivateImeOptions(editText1Marker);
                        editText1Ref.set(editText1);

                        final EditText editText2 = new EditText(activity);
                        editText2.setHint("This is editText2");
                        editText2.setPrivateImeOptions(editText2Marker);
                        editText2Ref.set(editText2);

                        layout.addView(editText1);
                        layout.addView(editText2);
                        return layout;
                    }, TestActivity2.class);

            notExpectEvent(stream, eventMatcher("onStartInputView"),
                    NOT_EXPECT_TIMEOUT);
            expectImeInvisible(TIMEOUT);

            final var display = editText1Ref.get().getContext().getDisplay();
            /*
             * Since this test relies on window focus with multiple windows involved, we need to
             * use a global method of emulating touch that goes through the entire pipeline. This
             * ensures that the window manager is aware of the tap that occurred, and provides
             * window focus to the tapped window.
             */
            try (var touch = new UinputTouchScreen(mInstrumentation, display)) {
                // Tap on the test activity to change focus
                touch.tapOnViewCenter(testActivity2.getWindow().getDecorView());

                ThrowingRunnable testProcedureForTestActivity2 = () -> {
                    // Focus the 1st editor and show the IME with WindowInsets API.
                    testActivity2.runOnUiThread(() -> {
                        editText1Ref.get().requestFocus();
                        editText1Ref.get()
                                .getWindowInsetsController().show(WindowInsets.Type.ime());
                    });
                    expectEvent(stream, editorMatcher("onStartInputView", editText1Marker),
                            TIMEOUT);
                    expectImeVisible(TIMEOUT);

                    // Press the back key to make the IME invisible.
                    mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                    expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
                    expectImeInvisible(TIMEOUT);

                    assertTrue("TestActivity2 is still focused after IME is hidden",
                            testActivity2.hasWindowFocus());

                    // Focus the 2nd editor and show the IME with WindowInsets API.
                    testActivity2.runOnUiThread(() -> {
                        editText2Ref.get().requestFocus();
                        editText2Ref.get()
                                .getWindowInsetsController().show(WindowInsets.Type.ime());
                    });
                    expectEvent(stream, editorMatcher("onStartInputView", editText2Marker),
                            TIMEOUT);
                    expectImeVisible(TIMEOUT);
                };
                testProcedureForTestActivity2.run();

                // Finish the primary activity to exit split-screen mode.
                splitPrimaryActivity.runOnUiThread(splitPrimaryActivity::finish);
                TestUtils.waitOnMainUntil(() -> {
                    final View decorView = testActivity2.getWindow().getDecorView();
                    return decorView.hasWindowFocus() && decorView.getVisibility() == VISIBLE;
                }, TIMEOUT, "Activity should visible & focused when exiting split-screen mode");

                // Rerun the test procedure to ensure it passes after exiting split-screen mode.
                testProcedureForTestActivity2.run();
            }
        }
    }

    /**
     * A regression Test for Bug 226033399.
     *
     * <p>This test verifies that the keyboard remains visible when a notification comes.
     * This test runs only when the screen is big enough. If the screen is small, it may make sense
     * to dismiss the keyboard while a notification is being displayed. We also skip this test on
     * non-phone, non-tablet form factors.
     */
    // Instant apps cannot post notification.
    @AppModeFull
    @Test
    public void testIMEVisibleWhenNotificationComes() throws Throwable {
        final Context targetContext = mInstrumentation.getTargetContext();
        final PackageManager pm = targetContext.getPackageManager();
        // Exclude major known non-phone, non-tablet form factors which have different notification
        // UIs.
        assumeFalse(pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY));
        assumeFalse(pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        assumeFalse(pm.hasSystemFeature(PackageManager.FEATURE_WATCH));
        final NotificationManager notificationManager =
                targetContext.getSystemService(NotificationManager.class);
        assumeNotNull(notificationManager);
        // Usually notification permission should be auto-granted by the test runner.
        // Skip the test if notification is disabled for some other reason.
        assumeTrue(notificationManager.areNotificationsEnabled());

        final InputMethodManager imm = targetContext.getSystemService(InputMethodManager.class);
        final int notificationId = 12345;
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);

            // Skip the test if the screen size is small.
            final int smallestScreenWidthDp =
                    editText.getContext().getResources().getConfiguration().smallestScreenWidthDp;
            Log.d(TAG, "smallestScreenWidthDp = " + smallestScreenWidthDp);
            assumeTrue(smallestScreenWidthDp >= 400);

            // 1. Show keyboard.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText, 0)));
            expectImeVisible(TIMEOUT);

            // 2. Post a notification and verify that the keyboard is still visible.
            final NotificationChannel channel = new NotificationChannel("test" /* id */,
                    "Test Channel" /* name */, NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
            final String notificationTitle = "notification-" + marker;
            notificationManager.notify(
                    notificationId,
                    new Notification.Builder(targetContext, channel.getId())
                            .setContentTitle(notificationTitle)
                            .setContentText("testIMEVisibleWhenNotificationComes")
                            .setSmallIcon(android.R.drawable.ic_info)
                            .build());
            UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
            // Wait until the notification is visible. If TIMEOUT has passed and the notification
            // is not visible, it's fine - the keyboard should remain visible in that case too.
            uiDevice.wait(Until.hasObject(By.text(notificationTitle)), TIMEOUT);
            expectImeVisible(TIMEOUT);

            // 3. Dismiss the notification and verify that the keyboard is still visible.
            notificationManager.cancel(notificationId);
            uiDevice.wait(Until.gone(By.text(notificationTitle)), TIMEOUT);
            expectImeVisible(TIMEOUT);
        } finally {
            // Make sure to dismiss the notification even if the test failed.
            notificationManager.cancel(notificationId);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)
    public void testQuickDoubleSwipeBackHidesImeAndSendsEventToApp_OnBackInvokedCallbackEnabled()
            throws Exception {
        testQuickDoubleSwipeBackHidesImeAndSendsEventToApp(true);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REFACTOR_INSETS_CONTROLLER)
    public void testQuickDoubleSwipeBackHidesImeAndSendsEventToApp_OnBackInvokedCallbackDisabled()
            throws Exception {
        testQuickDoubleSwipeBackHidesImeAndSendsEventToApp(false);
    }

    /**
     * A regression test for Bug 375986921.
     *
     * This test verifies that invoking a key back event twice quickly after each other will hide
     * the IME first and then go back to the previous activity.
     */
    private void testQuickDoubleSwipeBackHidesImeAndSendsEventToApp(
            boolean onBackInvokedCallbackEnabled) throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(),
                mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();

            mInstrumentation.getTargetContext().getApplicationInfo().setEnableOnBackInvokedCallback(
                    onBackInvokedCallbackEnabled);

            // Launch an editor activity to be on the split primary task.
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            final TestActivity testActivity = TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                final EditText editText = new EditText(activity);
                editTextRef.set(editText);
                layout.addView(editText);
                editText.setHint("focused editText");
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                return layout;
            });
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // Show IME and make sure it has window focus
            TestUtils.runOnMainSync(() -> {
                editTextRef.get().requestFocus();
                editTextRef.get().getWindowInsetsController().show(WindowInsets.Type.ime());
            });
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);
            assertTrue(testActivity.hasWindowFocus());

            // First back event should hide the IME, the second should be ignored by the IME and
            // close the activity.
            mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            CtsWindowInfoUtils.waitForStableWindowGeometry(
                    Duration.ofMillis(LAYOUT_STABLE_THRESHOLD));
            mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            mInstrumentation.waitForIdleSync();

            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible", View.GONE,
                    TIMEOUT);

            // Make sure the activity was stopped by the second back key event.
            try {
                TestUtils.waitOnMainUntil(testActivity::isStopped, TIMEOUT);
            } catch (TimeoutException e) {
                throw new AssertionError("Activity should have been stopped", e);
            }
        }
    }


    private void setAutoRotateScreen(boolean enable) {
        try {
            final Instrumentation instrumentation = mInstrumentation;
            SystemUtil.runShellCommand(instrumentation, enable ? ENABLE_AUTO_ROTATE_CMD :
                    DISABLE_AUTO_ROTATE_CMD);
            instrumentation.waitForIdleSync();
        } catch (IOException io) {
            fail("Couldn't enable/disable auto-rotate screen");
        }
    }

    private static ImeSettings.Builder getFloatingImeSettings(@ColorInt int navigationBarColor) {
        final ImeSettings.Builder builder = new ImeSettings.Builder();
        builder.setWindowFlags(0, FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        // As documented, Window#setNavigationBarColor() is actually ignored when the IME window
        // does not have FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS.  We are calling setNavigationBarColor()
        // to ensure it.
        builder.setNavigationBarColor(navigationBarColor);
        return builder;
    }

    /**
     * Whether enabling a compatibility flag to clear {@link InputMethodManager#SHOW_FORCED} flag
     * for the given {@code packageName} of the app when it's leaving.
     *
     * @return {@code true} if the compatibility flag is enabled.
     */
    private static boolean isClearShowForcedFlagEnabled(String packageName) {
        AtomicBoolean result = new AtomicBoolean();
        runWithShellPermissionIdentity(() -> result.set(
                CompatChanges.isChangeEnabled(CLEAR_SHOW_FORCED_FLAG_WHEN_LEAVING, packageName,
                        UserHandle.CURRENT)));
        return result.get();
    }

    /** Whether the IME DisplayArea is organized by WM Shell. */
    private static boolean isImeOrganized(int displayId) {
        final WindowManagerState wmState = new WindowManagerState();
        wmState.computeState();
        WindowManagerState.DisplayArea imeContainer =  wmState.getImeContainer(displayId);
        assertNotNull("ImeContainer not found for display id: " + displayId, imeContainer);
        return imeContainer.isOrganized();
    }

    private static AutoCloseableWrapper<Dialog> createDialogWrapper(@NonNull EditText editor) {
        return AutoCloseableWrapper.create(
                TestUtils.getOnMainSync(() -> {
                    final Dialog dialog = new Dialog(editor.getContext());
                    final TextView textView = new TextView(editor.getContext());
                    textView.setText("Dialog");
                    dialog.setContentView(textView);

                    // Dim the background.
                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
                    lp.copyFrom(dialog.getWindow().getAttributes());
                    lp.width = MATCH_PARENT;
                    lp.height = MATCH_PARENT;
                    lp.flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                    lp.dimAmount = 0.3f;
                    dialog.getWindow().setAttributes(lp);
                    dialog.show();
                    return dialog;
                }), dialog -> TestUtils.runOnMainSync(dialog::dismiss));
    }

    /**
     * Whether the device has supported the recents screen.
     */
    private boolean hasRecentsScreen() {
        try {
            Context context = mInstrumentation.getContext();
            return context.getResources().getBoolean(
                    Resources.getSystem().getIdentifier("config_hasRecents", "bool", "android"));
        } catch (Resources.NotFoundException e) {
            return false;
        }
    }
}
