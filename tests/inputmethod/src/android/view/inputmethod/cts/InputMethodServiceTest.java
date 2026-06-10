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

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.inputmethodservice.InputMethodService.DISALLOW_INPUT_METHOD_INTERFACE_OVERRIDE;
import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.assumeExtensionSupportedDevice;
import static android.view.WindowInsets.Type.ime;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
import static android.view.inputmethod.cts.util.ConstantsUtils.DISAPPROVE_IME_PACKAGE_NAME;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeInvisible;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible;
import static android.view.inputmethod.cts.util.TestUtils.getOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.injectKeyEvent;
import static android.view.inputmethod.cts.util.TestUtils.runOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.waitOnMainUntil;

import static com.android.cts.mockime.ImeEventStreamTestUtils.EventFilterMode.CHECK_EXIT_EVENT_ONLY;
import static com.android.cts.mockime.ImeEventStreamTestUtils.WindowLayoutInfoParcelable;
import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.eventMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEventWithKeyValue;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.showSoftInputMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.verificationMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.withDescription;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.DisplayMetricsSession;
import android.text.TextUtils;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorBoundsInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.Flags;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.TextAppearanceInfo;
import android.view.inputmethod.cts.disapproveime.DisapproveInputMethodService;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.TestActivity2;
import android.view.inputmethod.cts.util.TestUtils;
import android.view.inputmethod.cts.util.TestWebView;
import android.view.inputmethod.cts.util.UnlockScreenRule;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;
import androidx.test.uiautomator.UiObject2;
import androidx.window.extensions.layout.DisplayFeature;
import androidx.window.extensions.layout.WindowLayoutInfo;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.GestureNavSwitchHelper;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.mockime.ImeCommand;
import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeEventStreamTestUtils.DescribedPredicate;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Tests for {@link InputMethodService} methods.
 *
 * Build/Install/Run:
 * atest CtsInputMethodTestCases:InputMethodServiceTest
 */
@MediumTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class InputMethodServiceTest extends EndToEndImeTestBase {
    private static final String TAG = "InputMethodServiceTest";
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(20);
    private static final long EXPECTED_TIMEOUT = TimeUnit.SECONDS.toMillis(2);

    private static final String OTHER_IME_ID = "com.android.cts.spellcheckingime/.SpellCheckingIme";

    private static final String ERASE_FONT_SCALE_CMD = "settings delete system font_scale";
    // 1.2 is an arbitrary value.
    private static final String PUT_FONT_SCALE_CMD = "settings put system font_scale 1.2";

    /**
     * System property for disabling the IME navigation bar, matches the key
     * from {@link InputMethodService}.
     */
    private static final String PROP_CAN_RENDER_GESTURAL_NAV_BUTTONS =
            "persist.sys.ime.can_render_gestural_nav_buttons";

    @Rule
    public final UnlockScreenRule mUnlockScreenRule = new UnlockScreenRule();
    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final GestureNavSwitchHelper mGestureNavSwitchHelper = new GestureNavSwitchHelper();

    private final String mMarker = getTestMarker();

    private Instrumentation mInstrumentation;

    private static DescribedPredicate<ImeEvent> backKeyDownMatcher(boolean expectedReturnValue) {
        return withDescription("onKeyDown(KEYCODE_BACK) = " + expectedReturnValue, event -> {
            if (!TextUtils.equals("onKeyDown", event.getEventName())) {
                return false;
            }
            final int keyCode = event.getArguments().getInt("keyCode");
            if (keyCode != KeyEvent.KEYCODE_BACK) {
                return false;
            }
            return event.getReturnBooleanValue() == expectedReturnValue;
        });
    }

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    private TestActivity createTestActivity(int windowFlags) {
        return TestActivity.startSync(activity -> createLayout(windowFlags, activity));
    }

    private TestActivity createTestActivity2(int windowFlags) {
        return new TestActivity.Starter().startSync(activity -> createLayout(windowFlags, activity),
                TestActivity2.class);
    }

    private LinearLayout createLayout(final int windowFlags, final Activity activity) {
        final LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);

        final EditText editText = new EditText(activity);
        editText.setText("Editable");
        editText.setPrivateImeOptions(mMarker);
        layout.addView(editText);
        editText.requestFocus();

        activity.getWindow().setSoftInputMode(windowFlags);
        return layout;
    }


    @Test
    public void verifyLayoutInflaterContext() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, editorMatcher("onStartInputView", mMarker), TIMEOUT);

            final ImeCommand command = imeSession.verifyLayoutInflaterContext();
            assertTrue("InputMethodService.getLayoutInflater().getContext() must be equal to"
                    + " InputMethodService.this",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
        }
    }

    @Test
    public void testSwitchInputMethod_verifiesEnabledState() throws Exception {
        assumeFalse(isPreventImeStartup());
        SystemUtil.runShellCommandOrThrow("ime disable " + OTHER_IME_ID);
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            expectEvent(stream, eventMatcher("onStartInput"), TIMEOUT);

            final ImeCommand cmd = imeSession.callSwitchInputMethod(OTHER_IME_ID);
            final ImeEvent event = expectCommand(stream, cmd, TIMEOUT);
            assertTrue("should be exception result, but wasn't" + event,
                    event.isExceptionReturnValue());
            // Should be IllegalStateException, but CompletableFuture converts to RuntimeException
            assertTrue("should be RuntimeException, but wasn't: "
                            + event.getReturnExceptionValue(),
                    event.getReturnExceptionValue() instanceof RuntimeException);
            assertTrue(
                    "should contain 'not enabled' but didn't: " + event.getReturnExceptionValue(),
                    event.getReturnExceptionValue().getMessage().contains("not enabled"));
        }
    }
    @Test
    public void testSwitchInputMethodWithSubtype_verifiesEnabledState() throws Exception {
        assumeFalse(isPreventImeStartup());
        SystemUtil.runShellCommand("ime disable " + OTHER_IME_ID);
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            expectEvent(stream, eventMatcher("onStartInput"), TIMEOUT);

            final ImeCommand cmd = imeSession.callSwitchInputMethod(OTHER_IME_ID, null);
            final ImeEvent event = expectCommand(stream, cmd, TIMEOUT);
            assertTrue("should be exception result, but wasn't" + event,
                    event.isExceptionReturnValue());
            // Should be IllegalStateException, but CompletableFuture converts to RuntimeException
            assertTrue("should be RuntimeException, but wasn't: "
                            + event.getReturnExceptionValue(),
                    event.getReturnExceptionValue() instanceof RuntimeException);
            assertTrue(
                    "should contain 'not enabled' but didn't: " + event.getReturnExceptionValue(),
                    event.getReturnExceptionValue().getMessage().contains("not enabled"));
        }
    }

    private void verifyImeConsumesBackButton(int backDisposition) throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final TestActivity testActivity = createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, editorMatcher("onStartInputView", mMarker), TIMEOUT);

            final ImeCommand command = imeSession.callSetBackDisposition(backDisposition);
            expectCommand(stream, command, TIMEOUT);

            testActivity.setIgnoreBackKey(true);
            assertEquals(0,
                    (long) getOnMainSync(() -> testActivity.getOnBackPressedCallCount()));
            mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);

            expectEvent(stream, backKeyDownMatcher(true), CHECK_EXIT_EVENT_ONLY, TIMEOUT);

            // Make sure TestActivity#onBackPressed() is NOT called.
            try {
                waitOnMainUntil(() -> testActivity.getOnBackPressedCallCount() > 0,
                        EXPECTED_TIMEOUT);
                fail("Activity#onBackPressed() should not be called");
            } catch (TimeoutException e) {
                // This is fine.  We actually expect timeout.
            }
        }
    }

    @Test
    public void testSetBackDispositionDefault() throws Exception {
        verifyImeConsumesBackButton(InputMethodService.BACK_DISPOSITION_DEFAULT);
    }

    @Test
    public void testSetBackDispositionWillNotDismiss() throws Exception {
        verifyImeConsumesBackButton(InputMethodService.BACK_DISPOSITION_WILL_NOT_DISMISS);
    }

    @Test
    public void testSetBackDispositionWillDismiss() throws Exception {
        verifyImeConsumesBackButton(InputMethodService.BACK_DISPOSITION_WILL_DISMISS);
    }

    @Test
    public void testSetBackDispositionAdjustNothing() throws Exception {
        verifyImeConsumesBackButton(InputMethodService.BACK_DISPOSITION_ADJUST_NOTHING);
    }

    @Test
    public void testRequestHideSelf() throws Exception {
        assumeNotHideNavBarForKeyboardOrAutomotive();

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, editorMatcher("onStartInputView", mMarker), TIMEOUT);

            expectImeVisible(TIMEOUT);

            imeSession.callRequestHideSelf(0);
            expectEvent(stream, eventMatcher("hideSoftInput"), TIMEOUT);
            expectEvent(stream, eventMatcher("onFinishInputView"), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);

            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    @FlakyTest(detail = "slow test")
    public void testRequestShowSelf() throws Exception {
        assumeNotHideNavBarForKeyboardOrAutomotive();

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            notExpectEvent(
                    stream, editorMatcher("onStartInputView", mMarker), TIMEOUT);

            expectImeInvisible(TIMEOUT);

            imeSession.callRequestShowSelf(0);
            expectEvent(stream, eventMatcher("showSoftInput"), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", mMarker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);

            expectImeVisible(TIMEOUT);
        }
    }

    @FlakyTest(bugId = 210680326)
    @Test
    public void testHandlesConfigChanges() throws Exception {
        assumeNotHideNavBarForKeyboardOrAutomotive();

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            // Case 1: Activity handles configChanges="fontScale"
            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, editorMatcher("onStartInput", mMarker), TIMEOUT);
            expectEvent(stream, eventMatcher("showSoftInput"), TIMEOUT);
            // MockIme handles fontScale. Make sure changing fontScale doesn't restart IME.
            enableFontScale();
            expectImeVisible(TIMEOUT);
            // Make sure IME was not restarted.
            notExpectEvent(stream, eventMatcher("onCreate"),
                    EXPECTED_TIMEOUT);
            if (!Flags.refactorInsetsController()) {
                // With the flag enabled, the IME is redrawn when the font scale changes.
                notExpectEvent(stream, showSoftInputMatcher(0),
                        EXPECTED_TIMEOUT);
            }

            eraseFontScale();

            // Case 2: Activity *doesn't* handle configChanges="fontScale" and restarts.
            createTestActivity2(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, editorMatcher("onStartInput", mMarker), TIMEOUT);
            // MockIme handles fontScale. Make sure changing fontScale doesn't restart IME.
            enableFontScale();
            expectImeVisible(TIMEOUT);
            // Make sure IME was not restarted.
            notExpectEvent(stream, eventMatcher("onCreate"),
                    EXPECTED_TIMEOUT);
        } finally {
            eraseFontScale();
        }
    }

    /**
     * Font scale is a global configuration.
     * This function will apply font scale changes.
     */
    private void enableFontScale() {
        try {
            final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            SystemUtil.runShellCommand(instrumentation, PUT_FONT_SCALE_CMD);
            instrumentation.waitForIdleSync();
        } catch (IOException io) {
            fail("Couldn't apply font scale.");
        }
    }

    /**
     * Font scale is a global configuration.
     * This function will apply font scale changes.
     */
    private void eraseFontScale() {
        try {
            final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            SystemUtil.runShellCommand(instrumentation, ERASE_FONT_SCALE_CMD);
            instrumentation.waitForIdleSync();
        } catch (IOException io) {
            fail("Couldn't apply font scale.");
        }
    }

    private static void assertSynthesizedSoftwareKeyEvent(KeyEvent keyEvent, int expectedAction,
            int expectedKeyCode, long expectedEventTimeBefore, long expectedEventTimeAfter) {
        if (keyEvent.getEventTime() < expectedEventTimeBefore
                || expectedEventTimeAfter < keyEvent.getEventTime()) {
            fail(String.format("EventTime must be within [%d, %d],"
                            + " which was %d", expectedEventTimeBefore, expectedEventTimeAfter,
                    keyEvent.getEventTime()));
        }
        assertEquals(expectedAction, keyEvent.getAction());
        assertEquals(expectedKeyCode, keyEvent.getKeyCode());
        assertEquals(KeyCharacterMap.VIRTUAL_KEYBOARD, keyEvent.getDeviceId());
        assertEquals(0, keyEvent.getScanCode());
        assertEquals(0, keyEvent.getRepeatCount());
        assertEquals(0, keyEvent.getRepeatCount());
        final int mustHaveFlags = KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;
        final int mustNotHaveFlags = KeyEvent.FLAG_FROM_SYSTEM;
        if ((keyEvent.getFlags() & mustHaveFlags) == 0
                || (keyEvent.getFlags() & mustNotHaveFlags) != 0) {
            fail(String.format("Flags must have FLAG_SOFT_KEYBOARD|"
                    + "FLAG_KEEP_TOUCH_MODE and must not have FLAG_FROM_SYSTEM, "
                    + "which was 0x%08X", keyEvent.getFlags()));
        }
    }

    /**
     * Test compatibility requirements of {@link InputMethodService#sendDownUpKeyEvents(int)}.
     */
    @Test
    public void testSendDownUpKeyEvents() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final AtomicReference<ArrayList<KeyEvent>> keyEventsRef = new AtomicReference<>();
            final String marker = "testSendDownUpKeyEvents/" + SystemClock.elapsedRealtimeNanos();

            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final ArrayList<KeyEvent> keyEvents = new ArrayList<>();
                keyEventsRef.set(keyEvents);
                final EditText editText = new EditText(activity) {
                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
                        return new InputConnectionWrapper(
                                super.onCreateInputConnection(editorInfo), false) {
                            /**
                             * {@inheritDoc}
                             */
                            @Override
                            public boolean sendKeyEvent(KeyEvent event) {
                                keyEvents.add(event);
                                return super.sendKeyEvent(event);
                            }
                        };
                    }
                };
                editText.setPrivateImeOptions(marker);
                layout.addView(editText);
                editText.requestFocus();
                return layout;
            });

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // Make sure that InputConnection#sendKeyEvent() has never been called yet.
            assertTrue(TestUtils.getOnMainSync(
                    () -> new ArrayList<>(keyEventsRef.get())).isEmpty());

            final int expectedKeyCode = KeyEvent.KEYCODE_0;
            final long uptimeStart = SystemClock.uptimeMillis();
            expectCommand(stream, imeSession.callSendDownUpKeyEvents(expectedKeyCode), TIMEOUT);
            final long uptimeEnd = SystemClock.uptimeMillis();

            final ArrayList<KeyEvent> keyEvents = TestUtils.getOnMainSync(
                    () -> new ArrayList<>(keyEventsRef.get()));

            // Check KeyEvent objects.
            assertNotNull(keyEvents);
            assertEquals(2, keyEvents.size());
            assertSynthesizedSoftwareKeyEvent(keyEvents.get(0), KeyEvent.ACTION_DOWN,
                    expectedKeyCode, uptimeStart, uptimeEnd);
            assertSynthesizedSoftwareKeyEvent(keyEvents.get(1), KeyEvent.ACTION_UP,
                    expectedKeyCode, uptimeStart, uptimeEnd);
            final Bundle arguments = expectEvent(stream,
                    eventMatcher("onUpdateSelection"),
                    TIMEOUT).getArguments();
            expectOnUpdateSelectionArguments(arguments, 0, 0, 1, 1, -1, -1);
        }
    }

    /**
     * Ensure that {@link InputConnection#requestCursorUpdates(int)} works for the built-in
     * {@link EditText} and {@link InputMethodService#onUpdateCursorAnchorInfo(CursorAnchorInfo)}
     * will be called back.
     */
    @Test
    public void testOnUpdateCursorAnchorInfo() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final String marker =
                    "testOnUpdateCursorAnchorInfo()/" + SystemClock.elapsedRealtimeNanos();

            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            final AtomicInteger requestCursorUpdatesCallCount = new AtomicInteger();
            final AtomicInteger requestCursorUpdatesWithFilterCallCount = new AtomicInteger();
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity) {
                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                        final InputConnection original = super.onCreateInputConnection(outAttrs);
                        return new InputConnectionWrapper(original, false) {
                            @Override
                            public boolean requestCursorUpdates(int cursorUpdateMode) {
                                if ((cursorUpdateMode & InputConnection.CURSOR_UPDATE_IMMEDIATE)
                                        != 0) {
                                    requestCursorUpdatesCallCount.incrementAndGet();
                                    return true;
                                }
                                return false;
                            }

                            @Override
                            public boolean requestCursorUpdates(
                                    int cursorUpdateMode, int cursorUpdateFilter) {
                                requestCursorUpdatesWithFilterCallCount.incrementAndGet();
                                return requestCursorUpdates(cursorUpdateMode | cursorUpdateFilter);
                            }
                        };
                    }
                };
                editTextRef.set(editText);
                editText.setPrivateImeOptions(marker);
                layout.addView(editText);
                editText.requestFocus();
                return layout;
            });
            final EditText editText = editTextRef.get();

            final ImeEventStream stream = imeSession.openEventStream();
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // Make sure that InputConnection#requestCursorUpdates() returns true.
            assertTrue(expectCommand(stream,
                    imeSession.callRequestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE),
                    TIMEOUT).getReturnBooleanValue());

            // Also make sure that requestCursorUpdates() actually gets called only once.
            assertEquals(1, requestCursorUpdatesCallCount.get());

            final CursorAnchorInfo originalCursorAnchorInfo = new CursorAnchorInfo.Builder()
                    .setMatrix(new Matrix())
                    .setInsertionMarkerLocation(3.0f, 4.0f, 5.0f, 6.0f, 0)
                    .setSelectionRange(7, 8)
                    .build();

            runOnMainSync(() -> editText.getContext().getSystemService(InputMethodManager.class)
                    .updateCursorAnchorInfo(editText, originalCursorAnchorInfo));

            final CursorAnchorInfo receivedCursorAnchorInfo = expectEvent(stream,
                    eventMatcher("onUpdateCursorAnchorInfo"),
                    TIMEOUT).getArguments().getParcelable("cursorAnchorInfo");
            assertNotNull(receivedCursorAnchorInfo);
            assertEquals(receivedCursorAnchorInfo, originalCursorAnchorInfo);

            requestCursorUpdatesCallCount.set(0);
            // Request Cursor updates with Filter
            // Make sure that InputConnection#requestCursorUpdates() returns true with data filter.
            assertTrue(expectCommand(stream,
                    imeSession.callRequestCursorUpdates(
                            InputConnection.CURSOR_UPDATE_IMMEDIATE
                            | InputConnection.CURSOR_UPDATE_FILTER_EDITOR_BOUNDS
                            | InputConnection.CURSOR_UPDATE_FILTER_CHARACTER_BOUNDS
                            | InputConnection.CURSOR_UPDATE_FILTER_INSERTION_MARKER
                            | InputConnection.CURSOR_UPDATE_FILTER_VISIBLE_LINE_BOUNDS
                            | InputConnection.CURSOR_UPDATE_FILTER_TEXT_APPEARANCE),
                    TIMEOUT).getReturnBooleanValue());

            // Also make sure that requestCursorUpdates() actually gets called only once.
            assertEquals(1, requestCursorUpdatesCallCount.get());

            EditorBoundsInfo.Builder builder = new EditorBoundsInfo.Builder();
            builder.setEditorBounds(new RectF(0f, 1f, 2f, 3f));
            final CursorAnchorInfo originalCursorAnchorInfo1 = new CursorAnchorInfo.Builder()
                    .setMatrix(new Matrix())
                    .setEditorBoundsInfo(builder.build())
                    .addVisibleLineBounds(1f, 2f, 3f, 5f)
                    .setTextAppearanceInfo(new TextAppearanceInfo.Builder().build())
                    .build();

            runOnMainSync(() -> editText.getContext().getSystemService(InputMethodManager.class)
                    .updateCursorAnchorInfo(editText, originalCursorAnchorInfo1));

            final CursorAnchorInfo receivedCursorAnchorInfo1 = expectEvent(stream,
                    eventMatcher("onUpdateCursorAnchorInfo"),
                    TIMEOUT).getArguments().getParcelable("cursorAnchorInfo");
            assertNotNull(receivedCursorAnchorInfo1);
            assertEquals(receivedCursorAnchorInfo1, originalCursorAnchorInfo1);

            requestCursorUpdatesCallCount.set(0);
            requestCursorUpdatesWithFilterCallCount.set(0);
            // Request Cursor updates with Mode and Filter
            // Make sure that InputConnection#requestCursorUpdates() returns true with mode and
            // data filter.
            builder = new EditorBoundsInfo.Builder();
            builder.setEditorBounds(new RectF(1f, 1f, 2f, 3f));
            final CursorAnchorInfo originalCursorAnchorInfo2 = new CursorAnchorInfo.Builder()
                    .setMatrix(new Matrix())
                    .setEditorBoundsInfo(builder.build())
                    .addVisibleLineBounds(1f, 2f, 3f, 4f)
                    .setTextAppearanceInfo(new TextAppearanceInfo.Builder().build())
                    .build();
            assertTrue(expectCommand(stream,
                    imeSession.callRequestCursorUpdates(
                            InputConnection.CURSOR_UPDATE_IMMEDIATE,
                                    InputConnection.CURSOR_UPDATE_FILTER_EDITOR_BOUNDS
                                    | InputConnection.CURSOR_UPDATE_FILTER_CHARACTER_BOUNDS
                                    | InputConnection.CURSOR_UPDATE_FILTER_INSERTION_MARKER
                                    | InputConnection.CURSOR_UPDATE_FILTER_VISIBLE_LINE_BOUNDS
                                    | InputConnection.CURSOR_UPDATE_FILTER_TEXT_APPEARANCE),
                    TIMEOUT).getReturnBooleanValue());

            // Make sure that requestCursorUpdates() actually gets called only once.
            assertEquals(1, requestCursorUpdatesCallCount.get());
            assertEquals(1, requestCursorUpdatesWithFilterCallCount.get());
            runOnMainSync(() -> editText.getContext().getSystemService(InputMethodManager.class)
                    .updateCursorAnchorInfo(editText, originalCursorAnchorInfo2));

            final CursorAnchorInfo receivedCursorAnchorInfo2 = expectEvent(stream,
                    eventMatcher("onUpdateCursorAnchorInfo"),
                    TIMEOUT).getArguments().getParcelable("cursorAnchorInfo");
            assertNotNull(receivedCursorAnchorInfo2);
            assertEquals(receivedCursorAnchorInfo2, originalCursorAnchorInfo2);
        }
    }

    /** Test that no exception is thrown when {@link InputMethodService#getDisplay()} is called */
    @Test
    public void testGetDisplay() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(), mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder().setVerifyUiContextApisInOnCreate(true))) {
            ensureImeRunning();
            final ImeEventStream stream = imeSession.openEventStream();

            // Verify if getDisplay doesn't throw exception before InputMethodService's
            // initialization.
            assertTrue(expectEvent(stream, verificationMatcher("getDisplay"),
                    CHECK_EXIT_EVENT_ONLY, TIMEOUT).getReturnBooleanValue());
            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);

            expectEvent(stream, editorMatcher("onStartInput", mMarker), TIMEOUT);
            // Verify if getDisplay doesn't throw exception
            assertTrue(expectCommand(stream, imeSession.callVerifyGetDisplay(), TIMEOUT)
                    .getReturnBooleanValue());
        }
    }

    /** Test the cursor position of {@link EditText} is correct after typing on another activity. */
    @Test
    public void testCursorAfterLaunchAnotherActivity() throws Exception {
        final AtomicReference<EditText> firstEditTextRef = new AtomicReference<>();
        final int newCursorOffset = 5;
        final String initialText = "Initial";
        final String firstCommitMsg = "First";
        final String secondCommitMsg = "Second";

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final String marker =
                    "testCursorAfterLaunchAnotherActivity()/" + SystemClock.elapsedRealtimeNanos();

            // Launch first test activity
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity);
                editText.setPrivateImeOptions(marker);
                editText.setSingleLine(false);
                firstEditTextRef.set(editText);
                editText.setText(initialText);
                layout.addView(editText);
                editText.requestFocus();
                return layout;
            });

            final EditText firstEditText = firstEditTextRef.get();
            final ImeEventStream stream = imeSession.openEventStream();

            // Verify onStartInput when first activity launch
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final ImeCommand commit = imeSession.callCommitText(firstCommitMsg, 1);
            expectCommand(stream, commit, TIMEOUT);
            TestUtils.waitOnMainUntil(
                    () -> TextUtils.equals(
                            firstEditText.getText(), initialText + firstCommitMsg), TIMEOUT);

            // Get current position
            int originalSelectionStart = firstEditText.getSelectionStart();
            int originalSelectionEnd = firstEditText.getSelectionEnd();

            assertEquals(initialText.length() + firstCommitMsg.length(), originalSelectionStart);
            assertEquals(initialText.length() + firstCommitMsg.length(), originalSelectionEnd);

            // Launch second test activity
            final Intent intent = new Intent()
                    .setAction(Intent.ACTION_MAIN)
                    .setClass(InstrumentationRegistry.getInstrumentation().getContext(),
                            TestActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            TestActivity secondActivity = (TestActivity) InstrumentationRegistry
                    .getInstrumentation().startActivitySync(intent);

            // Verify onStartInput when second activity launch
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // Commit some messages on second activity
            final ImeCommand secondCommit = imeSession.callCommitText(secondCommitMsg, 1);
            expectCommand(stream, secondCommit, TIMEOUT);

            // Back to first activity
            runOnMainSync(secondActivity::onBackPressed);

            // Make sure TestActivity#onBackPressed() is called.
            TestUtils.waitOnMainUntil(() -> secondActivity.getOnBackPressedCallCount() > 0,
                    TIMEOUT, "Activity#onBackPressed() should be called");

            TestUtils.runOnMainSync(firstEditText::requestFocus);

            // Verify onStartInput when first activity launch
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // Update cursor to a new position
            int newCursorPosition = originalSelectionStart - newCursorOffset;
            final ImeCommand setSelection =
                    imeSession.callSetSelection(newCursorPosition, newCursorPosition);
            expectCommand(stream, setSelection, TIMEOUT);

            // Commit to first activity again
            final ImeCommand commitFirstAgain = imeSession.callCommitText(firstCommitMsg, 1);
            expectCommand(stream, commitFirstAgain, TIMEOUT);
            TestUtils.waitOnMainUntil(
                    () -> TextUtils.equals(firstEditText.getText(), "InitialFirstFirst"), TIMEOUT);

            // get new position
            int newSelectionStart = firstEditText.getSelectionStart();
            int newSelectionEnd = firstEditText.getSelectionEnd();

            assertEquals(newSelectionStart, newCursorPosition + firstCommitMsg.length());
            assertEquals(newSelectionEnd, newCursorPosition + firstCommitMsg.length());
        }
    }

    @Test
    public void testBatchEdit_commitAndSetComposingRegion_textView() throws Exception {
        getCommitAndSetComposingRegionTest(TIMEOUT,
                "testBatchEdit_commitAndSetComposingRegion_textView/")
                .setTestTextView(true)
                .runTest();
    }

    @FlakyTest(bugId = 300314534)
    @Test
    public void testBatchEdit_commitAndSetComposingRegion_webView() throws Exception {
        assumeTrue(hasFeatureWebView());

        getCommitAndSetComposingRegionTest(TIMEOUT,
                "testBatchEdit_commitAndSetComposingRegion_webView/")
                .setTestTextView(false)
                .runTest();
    }

    @Test
    public void testBatchEdit_commitSpaceThenSetComposingRegion_textView() throws Exception {
        getCommitSpaceAndSetComposingRegionTest(TIMEOUT,
                "testBatchEdit_commitSpaceThenSetComposingRegion_textView/")
                .setTestTextView(true)
                .runTest();
    }

    @Test
    @FlakyTest(bugId = 294840051)
    public void testBatchEdit_commitSpaceThenSetComposingRegion_webView() throws Exception {
        assumeTrue(hasFeatureWebView());

        getCommitSpaceAndSetComposingRegionTest(TIMEOUT,
                "testBatchEdit_commitSpaceThenSetComposingRegion_webView/")
                .setTestTextView(false)
                .runTest();
    }

    @Test
    public void testBatchEdit_getCommitSpaceAndSetComposingRegionTestInSelectionTest_textView()
            throws Exception {
        getCommitSpaceAndSetComposingRegionInSelectionTest(TIMEOUT,
                "testBatchEdit_getCommitSpaceAndSetComposingRegionTestInSelectionTest_textView/")
                .setTestTextView(true)
                .runTest();
    }

    @FlakyTest(bugId = 333155542)
    @Test
    public void testBatchEdit_getCommitSpaceAndSetComposingRegionTestInSelectionTest_webView()
            throws Exception {
        assumeTrue(hasFeatureWebView());

        getCommitSpaceAndSetComposingRegionInSelectionTest(TIMEOUT,
                "testBatchEdit_getCommitSpaceAndSetComposingRegionTestInSelectionTest_webView/")
                .setTestTextView(false)
                .runTest();
    }

    private boolean hasFeatureWebView() {
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_WEBVIEW);
    }

    @Test
    public void testImeVisibleAfterRotation() throws Exception {
        assumeNotHideNavBarForKeyboardOrAutomotive();

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final Activity activity = createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, editorMatcher("onStartInput", mMarker), TIMEOUT);
            final int initialOrientation = activity.getRequestedOrientation();
            try {
                activity.setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);
                mInstrumentation.waitForIdleSync();
                expectImeVisible(TIMEOUT);

                activity.setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
                mInstrumentation.waitForIdleSync();
                expectImeVisible(TIMEOUT);
            } finally {
                if (initialOrientation != SCREEN_ORIENTATION_PORTRAIT) {
                    activity.setRequestedOrientation(initialOrientation);
                }
            }
        }
    }

    /**
     * Starts a {@link MockImeSession} and verifies MockIme receives {@link WindowLayoutInfo}
     * updates. Trigger Configuration changes by modifying the DisplaySession where MockIME window
     * is located, then verify Bounds from MockIME window and {@link DisplayFeature} from
     * WindowLayoutInfo updates observe the same changes to the hinge location.
     * Here we use {@link WindowLayoutInfoParcelable} to pass {@link WindowLayoutInfo} values
     * between this test process and the MockIME process.
     */
    @Test
    @ApiTest(apis = {
            "androidx.window.extensions.layout.WindowLayoutComponent#addWindowLayoutInfoListener"})
    public void testImeListensToWindowLayoutInfo() throws Exception {
        assumeExtensionSupportedDevice();

        final double resizeRatio = 0.8;
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder().setWindowLayoutInfoCallbackEnabled(true))) {

            final ImeEventStream stream = imeSession.openEventStream();
            TestActivity activity = createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            assertThat(expectEvent(stream, verificationMatcher("windowLayoutComponentLoaded"),
                    CHECK_EXIT_EVENT_ONLY, TIMEOUT).getReturnBooleanValue()).isTrue();
            final Display display = activity.getDisplay();
            assertThat(display).isNotNull();

            final int displayId = display.getDisplayId();
            try (DisplayMetricsSession displaySession = new DisplayMetricsSession(displayId)) {
                // MockIME has registered addWindowLayoutInfo, it should be emitting the
                // current location of hinge now.
                WindowLayoutInfoParcelable windowLayoutInit = verifyReceivedWindowLayout(stream);
                assertThat(windowLayoutInit).isNotNull();
                final List<DisplayFeature> featuresInit = windowLayoutInit.getDisplayFeatures();
                assertThat(featuresInit).isNotNull();

                // Skip the test if the device doesn't support hinges.
                assume().that(featuresInit).isNotEmpty();

                final Rect windowBoundsInit = featuresInit.get(0).getBounds();
                expectEvent(stream, editorMatcher("onStartInput", mMarker), TIMEOUT);
                expectEvent(stream, eventMatcher("showSoftInput"), TIMEOUT);

                // After IME is shown, get the bounds of IME.
                final Rect imeBoundsInit = expectCommand(stream,
                        imeSession.callGetCurrentWindowMetricsBounds(), TIMEOUT)
                        .getReturnParcelableValue();

                // Contain first part of the test in a try-block so that the display session
                // could be restored for the remaining testsuite even if something fails.
                try {
                    // Shrink the entire display 20% smaller.
                    displaySession.changeDisplayMetrics(resizeRatio /* sizeRatio */,
                            1.0 /* densityRatio */);

                    // onConfigurationChanged on WM side triggers a new calculation for
                    // hinge location.
                    final WindowLayoutInfoParcelable windowLayoutResized =
                            verifyReceivedWindowLayout(stream);

                    // Expect to receive same number of display features in WindowLayoutInfo.
                    final List<DisplayFeature> featuresResized =
                            windowLayoutResized.getDisplayFeatures();
                    assertThat(featuresResized).hasSize(featuresInit.size());

                    final Rect windowBoundsResized = featuresResized.get(0).getBounds();
                    final Rect imeBoundsResized = expectCommand(stream,
                            imeSession.callGetCurrentWindowMetricsBounds(), TIMEOUT)
                            .getReturnParcelableValue();

                    final StringBuilder errorMessage = new StringBuilder();
                    final Function<Function<Rect, Integer>, Boolean> inSameRatio = getSize -> {
                        final boolean windowResizedCorrectly = isResizedWithRatio(getSize,
                                windowBoundsInit, windowBoundsResized, resizeRatio, errorMessage);
                        final boolean imeResizedCorrectly = isResizedWithRatio(getSize,
                                imeBoundsInit, imeBoundsResized, resizeRatio, errorMessage);
                        return windowResizedCorrectly && imeResizedCorrectly;
                    };
                    final boolean widthsChangedInSameRatio = inSameRatio.apply(Rect::width);
                    final boolean heightsChangedInSameRatio = inSameRatio.apply(Rect::height);

                    // Expect the hinge dimension to shrink in exactly one direction, the actual
                    // dimension depends on device implementation. Observe hinge dimensions from
                    // IME configuration bounds and from WindowLayoutInfo.
                    assertWithMessage(
                            "Expected either width or height to change proportionally.\n"
                                    + " widthsChangedInSameRatio: "
                                    + widthsChangedInSameRatio + "\n"
                                    + " heightsChangedInSameRatio: "
                                    + heightsChangedInSameRatio + "\n"
                                    + " Resize ratio: " + String.format("%.1f", resizeRatio) + "\n"
                                    + " Initial window bounds: " + windowBoundsInit + "\n"
                                    + " Resized window bounds: " + windowBoundsResized + "\n"
                                    + " Initial IME bounds: " + imeBoundsInit + "\n"
                                    + " Resized IME bounds: " + imeBoundsResized + "\n"
                                    + " Details:" + errorMessage)
                            .that(widthsChangedInSameRatio || heightsChangedInSameRatio)
                            .isTrue();
                } finally {
                    // Restore Display to original size.
                    displaySession.restoreDisplayMetrics();
                }

                final WindowLayoutInfoParcelable restored = verifyReceivedWindowLayout(stream);
                final List<DisplayFeature> features = restored.getDisplayFeatures();
                assertThat(features).isNotEmpty();
                assertThat(features.get(0).getBounds()).isEqualTo(windowBoundsInit);

                final Rect imeBoundsRestored = expectCommand(stream,
                        imeSession.callGetCurrentWindowMetricsBounds(), TIMEOUT)
                        .getReturnParcelableValue();
                assertThat(imeBoundsRestored).isEqualTo(imeBoundsInit);
            }
        }
    }

    /** Verify if {@link InputMethodService#isUiContext()} returns {@code true}. */
    @Test
    public void testIsUiContext() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(), mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder().setVerifyUiContextApisInOnCreate(true))) {
            ensureImeRunning();
            final ImeEventStream stream = imeSession.openEventStream();

            // Verify if InputMethodService#isUiContext returns true in #onCreate
            assertTrue(expectEvent(stream, verificationMatcher("isUiContext"),
                    CHECK_EXIT_EVENT_ONLY, TIMEOUT).getReturnBooleanValue());
            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);

            expectEvent(stream, editorMatcher("onStartInput", mMarker), TIMEOUT);
            // Verify if InputMethodService#isUiContext returns true
            assertTrue(expectCommand(stream, imeSession.callVerifyIsUiContext(), TIMEOUT)
                    .getReturnBooleanValue());
        }
    }

    @Test
    public void testNoConfigurationChangedOnStartInput() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                mInstrumentation.getContext(), mInstrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);

            final ImeEventStream forkedStream = stream.copy();
            expectEvent(stream, editorMatcher("onStartInput", mMarker), TIMEOUT);
            // Verify if InputMethodService#isUiContext returns true
            notExpectEvent(forkedStream, eventMatcher("onConfigurationChanged"), EXPECTED_TIMEOUT);
        }
    }

    @Test
    public void testShowSoftInput_whenAllImesDisabled() {
        final InputMethodManager inputManager =
                mInstrumentation.getTargetContext().getSystemService(InputMethodManager.class);
        assertNotNull(inputManager);
        final List<InputMethodInfo> enabledImes = inputManager.getEnabledInputMethodList();

        try {
            // disable all IMEs
            for (InputMethodInfo ime : enabledImes) {
                SystemUtil.runShellCommand("ime disable " + ime.getId());
            }

            // start a test activity and expect it not to crash
            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        } finally {
            // restore all previous IMEs
            SystemUtil.runShellCommand("ime reset");
        }
    }

    @Test
    public void testImeOverrideSessionInterface_throwLinkageError() {
        SystemUtil.runCommandAndPrintOnLogcat(TAG, "am compat enable "
                + DISALLOW_INPUT_METHOD_INTERFACE_OVERRIDE + " " + DISAPPROVE_IME_PACKAGE_NAME);

        final Context context = mInstrumentation.getContext();
        final CountDownLatch serviceCreateLatch = new CountDownLatch(1);
        final DisapproveInputMethodService.DisapproveImeCallback disapproveImeCallback =
                hasLinkageError -> {
                    if (!hasLinkageError) {
                        fail("Should throw a LinkageError.");
                    }
                    serviceCreateLatch.countDown();
                };

        try {
            DisapproveInputMethodService.setCallback(disapproveImeCallback);
            mServiceRule.bindService(new Intent(context, DisapproveInputMethodService.class));
        } catch (TimeoutException e) {
            fail("DisapproveInputMethodService binding timeout.");
        }

        try {
            boolean result = serviceCreateLatch.await(EXPECTED_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!result) {
                fail("Timeout before receiving the result.");
            }
        } catch (InterruptedException ignored) {
        } finally {
            SystemUtil.runCommandAndPrintOnLogcat(TAG, "am compat reset "
                    + DISALLOW_INPUT_METHOD_INTERFACE_OVERRIDE + " " + DISAPPROVE_IME_PACKAGE_NAME);
        }
    }

    /**
     * Verifies that requesting to hide the IME caption bar does not lead
     * to any undesired behaviour (e.g. crashing, hiding the IME when it was visible, etc.).
     */
    @Test
    public void testRequestHideImeCaptionBar() throws Exception {
        assumeNotHideNavBarForKeyboardOrAutomotive();

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, editorMatcher("onStartInput", mMarker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            expectCommand(stream, imeSession.callSetImeCaptionBarVisible(false), TIMEOUT);
            expectImeVisible(TIMEOUT);
        }
    }

    /**
     * Verifies that requesting to hide the IME caption bar and then show it again does not lead
     * to any undesired behaviour (e.g. crashing, hiding the IME when it was visible, etc.).
     */
    @Test
    public void testRequestHideThenShowImeCaptionBar() throws Exception {
        assumeNotHideNavBarForKeyboardOrAutomotive();

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, editorMatcher("onStartInput", mMarker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            expectCommand(stream, imeSession.callSetImeCaptionBarVisible(false), TIMEOUT);
            expectImeVisible(TIMEOUT);

            expectCommand(stream, imeSession.callSetImeCaptionBarVisible(true), TIMEOUT);
            expectImeVisible(TIMEOUT);
        }
    }

    /**
     * Checks that the IME insets are at least as big as the IME navigation bar (when visible),
     * even if the IME overrides the insets, or gives an empty input view.
     */
    @Test
    public void testImeNavigationBarInsets() throws Exception {
        runImeNavigationBarTest(false /* useFullscreenMode */);
    }

    /**
     * Checks that the IME insets are not modified to be at least as big as the IME navigation bar,
     * when the IME is using fullscreen mode.
     */
    @Test
    public void testImeNavigationBarInsets_FullscreenMode() throws Exception {
        runImeNavigationBarTest(true /* useFullscreenMode */);
    }

    @Test
    @ApiTest(apis = {
            "android.inputmethodservice.InputMethodService#onShouldVerifyKeyEvent"})
    @RequiresFlagsEnabled(Flags.FLAG_VERIFY_KEY_EVENT)
    public void testOnShouldVerifyKeyEvent() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final int injectedKeyCode = KeyEvent.KEYCODE_1;
            injectKeyEvent(injectedKeyCode, mInstrumentation);

            final Bundle arguments = expectEvent(stream,
                    eventMatcher("onShouldVerifyKeyEvent"),
                    TIMEOUT).getArguments();
            KeyEvent receivedEvent = arguments.getParcelable("keyEvent", KeyEvent.class);
            assertNotNull(receivedEvent);
            assertEquals(receivedEvent.getKeyCode(), injectedKeyCode);
        }
    }

    /**
     * Test implementation for checking that the IME insets are at least as big as the IME
     * navigation bar (when visible). When using fullscreen mode, the IME requesting app should
     * receive zero IME insets.
     *
     * @param useFullscreenMode whether the IME should use the fullscreen mode.
     */
    private void runImeNavigationBarTest(boolean useFullscreenMode) throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder()
                        .setZeroInsets(true)
                        .setDrawsBehindNavBar(true)
                        .setFullscreenModePolicy(
                                useFullscreenMode
                                        ? ImeSettings.FullscreenModePolicy.FORCE_FULLSCREEN
                                        : ImeSettings.FullscreenModePolicy.NO_FULLSCREEN))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final var activity = createTestActivity(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            final var decorView = activity.getWindow().getDecorView();
            int imeHeight = decorView.getRootWindowInsets().getInsets(ime()).bottom;

            assertEquals(0, imeHeight);

            imeSession.callRequestShowSelf(0 /* flags */);

            PollingCheck.waitFor(TIMEOUT, () -> decorView.getRootWindowInsets().isVisible(ime()));

            imeHeight = decorView.getRootWindowInsets().getInsets(ime()).bottom;
            final boolean isFullscreen = expectCommand(stream,
                    imeSession.callGetOnEvaluateFullscreenMode(), TIMEOUT)
                    .getReturnBooleanValue();
            assertEquals(isFullscreen, useFullscreenMode);
            if (isFullscreen) {
                // In Fullscreen mode the IME doesn't provide any insets.
                assertEquals("Height of ime: " + imeHeight + " should be zero in fullscreen mode",
                        0, imeHeight);
            } else {
                final int imeNavBarHeight = expectCommand(stream,
                        imeSession.callGetImeCaptionBarHeight(), TIMEOUT)
                        .getReturnIntegerValue();
                assertTrue("Height of ime: " + imeHeight + " should be at least as big as"
                                + " the height of the IME navigation bar: " + imeNavBarHeight,
                        imeHeight >= imeNavBarHeight);
            }
        }
    }

    /**
     * Verifies that the custom IME Switcher button is requested visible in gesture navigation mode,
     * when the IME navigation bar is hidden.
     */
    @RequiresFlagsEnabled({
            Flags.FLAG_IME_SWITCHER_REVAMP_API,
            Flags.FLAG_DISALLOW_DISABLING_IME_NAVIGATION_BAR
    })
    @Test
    public void testOnCustomImeSwitcherButtonRequestedVisible_gestureNav() throws Exception {
        assumeTrue("Device must support the navigation bar",
                mGestureNavSwitchHelper.hasNavigationBar());

        assertTrue("Gesture navigation mode overlay should be installed",
                mGestureNavSwitchHelper.hasGestureNavOverlay());
        assertTrue("Three button navigation mode overlay should be installed",
                mGestureNavSwitchHelper.hasThreeButtonNavOverlay());

        assumeFalse("Device is not configured to hide the navigation bar for IME",
                isHideNavBarForKeyboard());
        assumeTrue("IME navigation bar is enabled", isImeNavigationBarEnabled());

        try (var ignored = mGestureNavSwitchHelper.withGestureNavigationMode();
                var imeSession = MockImeSession.create(
                        InstrumentationRegistry.getInstrumentation().getContext(),
                        InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {
            final var stream = imeSession.openEventStream();

            assumeTrue("IME Switcher button should be shown at the start of the test",
                    imeSession.shouldShowImeSwitcherButtonForTest());

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, eventMatcher("onStartInput"), TIMEOUT);
            notExpectEvent(stream, eventMatcher("onCustomImeSwitcherButtonRequestedVisible"),
                    EXPECTED_TIMEOUT);

            expectCommand(stream, imeSession.callSetImeCaptionBarVisible(false), TIMEOUT);
            final var requestedVisibleNavHidden = expectEvent(stream,
                    eventMatcher("onCustomImeSwitcherButtonRequestedVisible"), TIMEOUT);
            assertWithMessage("Custom IME Switcher requested visible when IME nav bar hidden")
                    .that(requestedVisibleNavHidden.getArguments().getBoolean("visible"))
                    .isTrue();

            try (var ignored1 = mGestureNavSwitchHelper.withThreeButtonNavigationMode()) {
                final var requestedVisibleThreeButton = expectEvent(stream,
                        eventMatcher("onCustomImeSwitcherButtonRequestedVisible"), TIMEOUT);
                assertWithMessage("Custom IME Switcher requested hidden when switching"
                        + " to three button nav")
                        .that(requestedVisibleThreeButton.getArguments().getBoolean("visible"))
                        .isFalse();
            }

            final var requestedVisibleGestureNav = expectEvent(stream,
                    eventMatcher("onCustomImeSwitcherButtonRequestedVisible"), TIMEOUT);
            assertWithMessage("Custom IME Switcher requested visible when switching"
                    + " back to gesture nav")
                    .that(requestedVisibleGestureNav.getArguments().getBoolean("visible"))
                    .isTrue();

            expectCommand(stream, imeSession.callSetImeCaptionBarVisible(true), TIMEOUT);
            final var requestedVisibleNavShown = expectEvent(stream,
                    eventMatcher("onCustomImeSwitcherButtonRequestedVisible"), TIMEOUT);
            assertWithMessage("Custom IME Switcher requested hidden when IME nav bar visible")
                    .that(requestedVisibleNavShown.getArguments().getBoolean("visible"))
                    .isFalse();
        }
    }

    /**
     * Verifies that the custom IME Switcher button is never requested visible in three button
     * navigation mode.
     */
    @RequiresFlagsEnabled(Flags.FLAG_IME_SWITCHER_REVAMP_API)
    @Test
    public void testOnCustomImeSwitcherButtonRequestedVisible_threeButtonNav() throws Exception {
        assumeTrue("Device must support the navigation bar",
                mGestureNavSwitchHelper.hasNavigationBar());

        assertTrue("Three button navigation mode overlay should be installed",
                mGestureNavSwitchHelper.hasThreeButtonNavOverlay());

        assumeFalse("Device is not configured to hide the navigation bar for IME",
                isHideNavBarForKeyboard());

        try (var ignored = mGestureNavSwitchHelper.withThreeButtonNavigationMode();
                var imeSession = MockImeSession.create(
                        InstrumentationRegistry.getInstrumentation().getContext(),
                        InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                        new ImeSettings.Builder())) {
            final var stream = imeSession.openEventStream();

            assumeTrue("IME Switcher button should be shown at the start of the test",
                    imeSession.shouldShowImeSwitcherButtonForTest());

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, eventMatcher("onStartInput"), TIMEOUT);
            notExpectEvent(stream, eventMatcher("onCustomImeSwitcherButtonRequestedVisible"),
                    EXPECTED_TIMEOUT);

            expectCommand(stream, imeSession.callSetImeCaptionBarVisible(false), TIMEOUT);
            notExpectEvent(stream, eventMatcher("onCustomImeSwitcherButtonRequestedVisible"),
                    EXPECTED_TIMEOUT);

            expectCommand(stream, imeSession.callSetImeCaptionBarVisible(true), TIMEOUT);
            notExpectEvent(stream, eventMatcher("onCustomImeSwitcherButtonRequestedVisible"),
                    EXPECTED_TIMEOUT);
        }
    }

    /**
     * Verifies that the custom IME Switcher button is requested visible in three button
     * navigation mode if {@code config_hideNavBarForKeyboard} is set, but not requested visible
     * in gesture navigation mode.
     */
    @RequiresFlagsEnabled({
            Flags.FLAG_IME_SWITCHER_REVAMP_API,
            Flags.FLAG_DISALLOW_DISABLING_IME_NAVIGATION_BAR
    })
    @Test
    public void testOnCustomImeSwitcherButtonRequestedVisible_hideNavBarForKeyboard()
            throws Exception {
        assumeTrue("Device must support the navigation bar",
                mGestureNavSwitchHelper.hasNavigationBar());

        assertTrue("Gesture navigation mode overlay should be installed",
                mGestureNavSwitchHelper.hasGestureNavOverlay());
        assertTrue("Three button navigation mode overlay should be installed",
                mGestureNavSwitchHelper.hasThreeButtonNavOverlay());

        assumeTrue("Device is configured to hide the navigation bar for IME",
                isHideNavBarForKeyboard());
        assumeTrue("IME navigation bar is enabled", isImeNavigationBarEnabled());

        try (var ignored = mGestureNavSwitchHelper.withGestureNavigationMode();
                var imeSession = MockImeSession.create(
                        InstrumentationRegistry.getInstrumentation().getContext(),
                        InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                        new ImeSettings.Builder())) {
            final var stream = imeSession.openEventStream();

            assumeTrue("IME Switcher button should be shown at the start of the test",
                    imeSession.shouldShowImeSwitcherButtonForTest());

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, eventMatcher("onStartInput"), TIMEOUT);
            notExpectEvent(stream, eventMatcher("onCustomImeSwitcherButtonRequestedVisible"),
                    EXPECTED_TIMEOUT);

            try (var ignored1 = mGestureNavSwitchHelper.withThreeButtonNavigationMode()) {
                final var requestedVisibleThreeButton = expectEvent(stream,
                        eventMatcher("onCustomImeSwitcherButtonRequestedVisible"), TIMEOUT);
                assertWithMessage("Custom IME Switcher requested visible when switching"
                        + " to three button nav with config_hideNavBarForKeyboard")
                        .that(requestedVisibleThreeButton.getArguments().getBoolean("visible"))
                        .isTrue();
            }

            final var requestedVisibleGestureNav = expectEvent(stream,
                    eventMatcher("onCustomImeSwitcherButtonRequestedVisible"), TIMEOUT);
            assertWithMessage("Custom IME Switcher requested visible when switching"
                    + " back to gesture nav")
                    .that(requestedVisibleGestureNav.getArguments().getBoolean("visible"))
                    .isFalse();
        }
    }

    /**
     * Verifies that the custom IME Switcher button is not requested visible if the IME navigation
     * bar is disabled. In gesture navigation mode with the IME navigation bar disabled, the system
     * navigation bar will show the IME navigation bar buttons.
     */
    @RequiresFlagsEnabled(Flags.FLAG_IME_SWITCHER_REVAMP_API)
    @RequiresFlagsDisabled(Flags.FLAG_DISALLOW_DISABLING_IME_NAVIGATION_BAR)
    @Test
    public void testOnCustomImeSwitcherButtonRequestedVisible_imeNavigationBarDisabled()
            throws Exception {
        assumeTrue("Device must support the navigation bar",
                mGestureNavSwitchHelper.hasNavigationBar());

        assertTrue("Gesture navigation mode overlay should be installed",
                mGestureNavSwitchHelper.hasGestureNavOverlay());
        assertTrue("Three button navigation mode overlay should be installed",
                mGestureNavSwitchHelper.hasThreeButtonNavOverlay());

        assumeFalse("Device is not configured to hide the navigation bar for IME",
                isHideNavBarForKeyboard());
        assumeFalse("IME navigation bar is not enabled", isImeNavigationBarEnabled());

        try (var ignored = mGestureNavSwitchHelper.withThreeButtonNavigationMode();
                var imeSession = MockImeSession.create(
                        InstrumentationRegistry.getInstrumentation().getContext(),
                        InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                        new ImeSettings.Builder())) {
            final var stream = imeSession.openEventStream();

            assumeTrue("IME Switcher button should be shown at the start of the test",
                    imeSession.shouldShowImeSwitcherButtonForTest());

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, eventMatcher("onStartInput"), TIMEOUT);
            notExpectEvent(stream, eventMatcher("onCustomImeSwitcherButtonRequestedVisible"),
                    EXPECTED_TIMEOUT);

            try (var ignored1 = mGestureNavSwitchHelper.withGestureNavigationMode()) {
                // IME nav bar buttons are shown in system navigation bar as it is visible and the
                // IME nav bar is disabled.
                notExpectEvent(stream, eventMatcher("onCustomImeSwitcherButtonRequestedVisible"),
                        EXPECTED_TIMEOUT);
            }

            notExpectEvent(stream, eventMatcher("onCustomImeSwitcherButtonRequestedVisible"),
                    EXPECTED_TIMEOUT);
        }
    }

    /**
     * Verifies that the custom IME Switcher button is requested visible if the IME navigation bar
     * is disabled and {@code config_hideNavBarForKeyboard} is set. In gesture navigation
     * mode with the IME navigation bar disabled, the system navigation bar would show the IME
     * navigation bar buttons, however the bar itself is also not visible.
     */
    @RequiresFlagsEnabled(Flags.FLAG_IME_SWITCHER_REVAMP_API)
    @RequiresFlagsDisabled(Flags.FLAG_DISALLOW_DISABLING_IME_NAVIGATION_BAR)
    @Test
    public void testOnCustomImeSwitcherButtonRequestedVisible_imeNavigationBarDisabled_hideNavBarForKeyboard()
            throws Exception {
        assumeTrue("Device must support the navigation bar",
                mGestureNavSwitchHelper.hasNavigationBar());

        assertTrue("Gesture navigation mode overlay should be installed",
                mGestureNavSwitchHelper.hasGestureNavOverlay());
        assertTrue("Three button navigation mode overlay should be installed",
                mGestureNavSwitchHelper.hasThreeButtonNavOverlay());

        assumeTrue("Device is configured to hide the navigation bar for IME",
                isHideNavBarForKeyboard());
        assumeFalse("IME navigation bar is not enabled", isImeNavigationBarEnabled());

        try (var ignored = mGestureNavSwitchHelper.withThreeButtonNavigationMode();
                var imeSession = MockImeSession.create(
                        InstrumentationRegistry.getInstrumentation().getContext(),
                        InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                        new ImeSettings.Builder())) {
            final var stream = imeSession.openEventStream();

            assumeTrue("IME Switcher button should be shown at the start of the test",
                    imeSession.shouldShowImeSwitcherButtonForTest());

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            // onCustomImeSwitcherButtonRequestedVisible happens before onStartInput as the IME is
            // created directly in the required state for this.
            final var requestedVisible = expectEvent(stream,
                    eventMatcher("onCustomImeSwitcherButtonRequestedVisible"), TIMEOUT);
            assertWithMessage("Custom IME Switcher requested visible")
                    .that(requestedVisible.getArguments().getBoolean("visible"))
                    .isTrue();

            expectEvent(stream, eventMatcher("onStartInput"), TIMEOUT);

            try (var ignored1 = mGestureNavSwitchHelper.withGestureNavigationMode()) {
                notExpectEvent(stream, eventMatcher("onCustomImeSwitcherButtonRequestedVisible"),
                        EXPECTED_TIMEOUT);
            }

            notExpectEvent(stream, eventMatcher("onCustomImeSwitcherButtonRequestedVisible"),
                    EXPECTED_TIMEOUT);
        }
    }

    /** Explicitly start-up the IME process if it would have been prevented. */
    protected void ensureImeRunning() {
        if (isPreventImeStartup()) {
            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    /** Whether the IME navigation bar is enabled, defaults to {@code true}. */
    private static boolean isImeNavigationBarEnabled() {
        return SystemProperties.getBoolean(PROP_CAN_RENDER_GESTURAL_NAV_BUTTONS, true);
    }

    /** Whether the navigation bar will be automatically hidden when the IME is shown. */
    private static boolean isHideNavBarForKeyboard() {
        final var resources = InstrumentationRegistry.getInstrumentation().getContext()
                .getResources();
        return resources.getBoolean(
                resources.getIdentifier("config_hideNavBarForKeyboard", "bool", "android"));
    }

    /**
     * Assumes either the navigation bar will not be automatically hidden when the IME is shown,
     * or the device is automotive.
     *
     * <p>The config for hiding the nav bar is only handled on automotive. On other devices, this
     * leads to drawing the IME under the navigation bar, breaking {@code expectImeVisible}. This
     * should be fixed by handling the config on SystemUI to hide the navigation bar, not just the
     * insets.
     */
    private static void assumeNotHideNavBarForKeyboardOrAutomotive() {
        final var pm = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager();
        assumeTrue("Device is not configured to hide the navigation bar for IME or is Automotive",
                !isHideNavBarForKeyboard()
                        || pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
    }

    /** Test case for committing and setting composing region after cursor. */
    private static UpdateSelectionTest getCommitAndSetComposingRegionTest(
            long timeout, String makerPrefix) throws Exception {
        UpdateSelectionTest test = new UpdateSelectionTest(timeout, makerPrefix) {
            @Override
            public void testMethodImpl() throws Exception {
                // "abc|"
                expectCommand(stream, imeSession.callCommitText("abc", 1), timeout);
                verifyText("abc", 3, 3);
                final Bundle arguments1 = expectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        timeout).getArguments();
                expectOnUpdateSelectionArguments(arguments1, 0, 0, 3, 3, -1, -1);
                notExpectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        EXPECTED_TIMEOUT);

                // "|abc"
                expectCommand(stream, imeSession.callSetSelection(0, 0), timeout);
                verifyText("abc", 0, 0);
                final Bundle arguments2 = expectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        timeout).getArguments();
                expectOnUpdateSelectionArguments(arguments2, 3, 3, 0, 0, -1, -1);
                notExpectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        EXPECTED_TIMEOUT);

                // "Back |abc"
                //        ---
                expectCommand(stream, imeSession.callBeginBatchEdit(), timeout);
                expectCommand(stream, imeSession.callCommitText("Back ", 1), timeout);
                expectCommand(stream, imeSession.callSetComposingRegion(5, 8), timeout);
                expectCommand(stream, imeSession.callEndBatchEdit(), timeout);
                verifyText("Back abc", 5, 5);
                final Bundle arguments3 = expectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        timeout).getArguments();
                expectOnUpdateSelectionArguments(arguments3, 0, 0, 5, 5, 5, 8);
                notExpectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        EXPECTED_TIMEOUT);
            }
        };
        return test;
    }

    /** Test case for committing space and setting composing region after cursor. */
    private static UpdateSelectionTest getCommitSpaceAndSetComposingRegionTest(
            long timeout, String makerPrefix) throws Exception {
        UpdateSelectionTest test = new UpdateSelectionTest(timeout, makerPrefix) {
            @Override
            public void testMethodImpl() throws Exception {
                // "Hello|"
                //  -----
                expectCommand(stream, imeSession.callSetComposingText("Hello", 1), timeout);
                verifyText("Hello", 5, 5);
                final Bundle arguments1 = expectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        timeout).getArguments();
                expectOnUpdateSelectionArguments(arguments1, 0, 0, 5, 5, 0, 5);
                notExpectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        EXPECTED_TIMEOUT);

                // "|Hello"
                //   -----
                expectCommand(stream, imeSession.callSetSelection(0, 0), timeout);
                verifyText("Hello", 0, 0);
                final Bundle arguments2 = expectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        timeout).getArguments();
                expectOnUpdateSelectionArguments(arguments2, 5, 5, 0, 0, 0, 5);
                notExpectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        EXPECTED_TIMEOUT);

                // " |Hello"
                //    -----
                expectCommand(stream, imeSession.callBeginBatchEdit(), timeout);
                expectCommand(stream, imeSession.callFinishComposingText(), timeout);
                expectCommand(stream, imeSession.callCommitText(" ", 1), timeout);
                expectCommand(stream, imeSession.callSetComposingRegion(1, 6), timeout);
                expectCommand(stream, imeSession.callEndBatchEdit(), timeout);

                verifyText(" Hello", 1, 1);
                final Bundle arguments3 = expectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        timeout).getArguments();
                expectOnUpdateSelectionArguments(arguments3, 0, 0, 1, 1, 1, 6);
                notExpectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        EXPECTED_TIMEOUT);
            }
        };
        return test;
    }

    /**
     * Test case for committing space in the middle of selection and setting composing region after
     * cursor.
     */
    private static UpdateSelectionTest getCommitSpaceAndSetComposingRegionInSelectionTest(
            long timeout, String makerPrefix) throws Exception {
        UpdateSelectionTest test = new UpdateSelectionTest(timeout, makerPrefix) {
            @Override
            public void testMethodImpl() throws Exception {
                // "2005abc|"
                expectCommand(stream, imeSession.callCommitText("2005abc", 1), timeout);
                verifyText("2005abc", 7, 7);
                final Bundle arguments1 = expectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        timeout).getArguments();
                expectOnUpdateSelectionArguments(arguments1, 0, 0, 7, 7, -1, -1);
                notExpectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        EXPECTED_TIMEOUT);

                // "2005|abc"
                expectCommand(stream, imeSession.callSetSelection(4, 4), timeout);
                verifyText("2005abc", 4, 4);
                final Bundle arguments2 = expectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        timeout).getArguments();
                expectOnUpdateSelectionArguments(arguments2, 7, 7, 4, 4, -1, -1);
                notExpectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        EXPECTED_TIMEOUT);

                // "2005 |abc"
                //        ---
                expectCommand(stream, imeSession.callBeginBatchEdit(), timeout);
                expectCommand(stream, imeSession.callCommitText(" ", 1), timeout);
                expectCommand(stream, imeSession.callSetComposingRegion(5, 8), timeout);
                expectCommand(stream, imeSession.callEndBatchEdit(), timeout);

                verifyText("2005 abc", 5, 5);
                final Bundle arguments3 = expectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        timeout).getArguments();
                expectOnUpdateSelectionArguments(arguments3, 4, 4, 5, 5, 5, 8);
                notExpectEvent(stream,
                        eventMatcher("onUpdateSelection"),
                        EXPECTED_TIMEOUT);
            }
        };
        return test;
    }

    private static void expectOnUpdateSelectionArguments(Bundle arguments,
            int expectedOldSelStart, int expectedOldSelEnd, int expectedNewSelStart,
            int expectedNewSelEnd, int expectedCandidateStart, int expectedCandidateEnd) {
        assertEquals(expectedOldSelStart, arguments.getInt("oldSelStart"));
        assertEquals(expectedOldSelEnd, arguments.getInt("oldSelEnd"));
        assertEquals(expectedNewSelStart, arguments.getInt("newSelStart"));
        assertEquals(expectedNewSelEnd, arguments.getInt("newSelEnd"));
        assertEquals(expectedCandidateStart, arguments.getInt("candidatesStart"));
        assertEquals(expectedCandidateEnd, arguments.getInt("candidatesEnd"));
    }

    /**
     * Helper class for wrapping tests for {@link android.widget.TextView} and @{@link WebView}
     * relates to batch edit and update selection change.
     */
    private abstract static class UpdateSelectionTest {
        private final long mTimeout;
        private final String mMaker;
        private final AtomicReference<EditText> mEditTextRef = new AtomicReference<>();
        private final AtomicReference<UiObject2> mInputTextFieldRef = new AtomicReference<>();

        public final MockImeSession imeSession;
        public final ImeEventStream stream;

        // True if testing TextView, otherwise test WebView
        private boolean mIsTestingTextView;

        UpdateSelectionTest(long timeout, String makerPrefix) throws Exception {
            this.mTimeout = timeout;
            this.mMaker = makerPrefix + SystemClock.elapsedRealtimeNanos();
            imeSession = MockImeSession.create(
                    InstrumentationRegistry.getInstrumentation().getContext(),
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    new ImeSettings.Builder());
            stream = imeSession.openEventStream();
        }

        /**
         * Runs the real test logic, which would test onStartInput event first, then test the logic
         * in {@link #testMethodImpl()}.
         *
         * @throws Exception if timeout or assert fails
         */
        public void runTest() throws Exception {
            if (mIsTestingTextView) {
                TestActivity.startSync(activity -> {
                    final LinearLayout layout = new LinearLayout(activity);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    final EditText editText = new EditText(activity);
                    layout.addView(editText);
                    editText.requestFocus();
                    editText.setPrivateImeOptions(mMaker);
                    mEditTextRef.set(editText);
                    return layout;
                });
                assertNotNull(mEditTextRef.get());
            } else {
                final UiObject2 inputTextField = TestWebView.launchTestWebViewActivity(
                        mTimeout, mMaker);
                assertNotNull("Editor must exists on WebView", inputTextField);
                mInputTextFieldRef.set(inputTextField);
                inputTextField.click();
            }
            expectEvent(stream, editorMatcher("onStartInput", mMaker), TIMEOUT);

            // Code for testing input connection logic.
            testMethodImpl();
        }

        /**
         * Test method to be overridden by implementation class.
         */
        public abstract void testMethodImpl() throws Exception;

        /**
         * Verifies text and selection range in the edit text if this is running tests for TextView;
         * otherwise verifies the text (no selection) in the WebView.
         * @param expectedText expected text in the TextView or WebView
         * @param selStart expected start position of the selection in the TextView; will be ignored
         *                 for WebView
         * @param selEnd expected end position of the selection in the WebView; will be ignored for
         *               WebView
         * @throws Exception if timeout or assert fails
         */
        public void verifyText(String expectedText, int selStart, int selEnd) throws Exception {
            if (mIsTestingTextView) {
                EditText editText = mEditTextRef.get();
                assertNotNull(editText);
                waitOnMainUntil(()->
                        expectedText.equals(editText.getText().toString())
                                && selStart == editText.getSelectionStart()
                                && selEnd == editText.getSelectionEnd(), mTimeout);
            } else {
                UiObject2 inputTextField = mInputTextFieldRef.get();
                assertNotNull(inputTextField);
                waitOnMainUntil(()-> expectedText.equals(inputTextField.getText()), mTimeout);
            }
        }

        public UpdateSelectionTest setTestTextView(boolean isTestingTextView) {
            this.mIsTestingTextView = isTestingTextView;
            return this;
        }
    }

    private static WindowLayoutInfoParcelable verifyReceivedWindowLayout(ImeEventStream stream)
            throws TimeoutException {
        final ImeEvent imeEvent = expectEvent(stream,
                eventMatcher("getWindowLayoutInfo"),
                TIMEOUT);
        return imeEvent.getArguments()
                .getParcelable("WindowLayoutInfo", WindowLayoutInfoParcelable.class);
    }

    /**
     * Checks if the resizing of a rectangle maintains a specified aspect ratio.
     * <p>
     * This function compares the initial and resized dimensions of a rectangle, using a provided
     * function ({@code getSize}) to extract the relevant dimension (e.g., width or height).
     * It determines if the resized dimension matches the expected value, calculated as the
     * initial dimension multiplied by the given ratio.
     * <p>
     * If the aspect ratio is not maintained, an error message detailing the discrepancy is
     * appended to the {@code outErrorMessage} StringBuilder.
     *
     * @param getSize a function that extracts the relevant dimension (width or height) from a Rect.
     * @param initial the initial Rect before resizing.
     * @param resized the Rect after resizing.
     * @param ratio the expected resize ratio (e.g., 0.8 for a 20% decrease).
     * @param outErrorMessage a StringBuilder to which an error message is appended if the aspect
     *                        ratio is not maintained.
     * @return {@code true} if the resize maintains the specified ratio, {@code false} otherwise.
     */
    private static boolean isResizedWithRatio(@NonNull Function<Rect, Integer> getSize,
            @NonNull Rect initial, @NonNull Rect resized, double ratio,
            @NonNull StringBuilder outErrorMessage) {
        // Align with the rounding approach in DisplayMetricsSession#changeDisplayMetrics.
        final int expected = (int) (getSize.apply(initial) * ratio);
        final int actual = getSize.apply(resized);
        final boolean isCorrect = (expected == actual);
        if (!isCorrect) {
            outErrorMessage
                    .append("\n  isResizedWithRatio(initial=")
                    .append(initial)
                    .append(", resized=")
                    .append(resized)
                    .append(", ratio=")
                    .append(ratio)
                    .append("):\n    expected size: ")
                    .append(expected)
                    .append(" but was: ")
                    .append(actual);
        }
        return isCorrect;
    }
}
