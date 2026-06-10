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

package android.view.inputmethod.cts;

import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible;
import static android.view.inputmethod.cts.util.TestUtils.runOnMainSync;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.eventMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import android.app.AlertDialog;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.TestUtils;
import android.view.inputmethod.cts.util.UnlockScreenRule;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImePackageNames;
import com.android.cts.mockime.MockImeSession;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@MediumTest
public final class ImeSwitchingTest extends EndToEndImeTestBase {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    @Rule
    public final UnlockScreenRule mUnlockScreenRule = new UnlockScreenRule();

    @Test
    public void testSwitchingIme() throws Exception {
        testWithActivityAndTwoImes((session1, session2, editText, marker) -> {
            final var stream1 = session1.openEventStream();
            final var stream2 = session2.openEventStream();

            // Make sure that MockIme2 eventually starts the input connection.
            expectEvent(stream2, editorMatcher("onStartInput", marker), TIMEOUT);

            // Then switch to MockIme1
            stream1.skipAll();
            expectCommand(stream2, session2.callSwitchInputMethod(session1.getImeId()), TIMEOUT);
            expectEvent(stream2, eventMatcher("onDestroy"), TIMEOUT);

            expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
            expectEvent(stream1, editorMatcher("onStartInput", marker), TIMEOUT);
        });
    }

    /**
     * Test if IMEs remain to be visible after switching to other IMEs.
     *
     * <p>Regression test for Bug 152876819.</p>
     */
    @Test
    public void testImeRemainsVisibleAfterSwitchingIme() throws Exception {
        testWithActivityAndTwoImes((session1, session2, editText, marker) -> {
            final var stream1 = session1.openEventStream();
            final var stream2 = session2.openEventStream();

            // Make sure that MockIme2 eventually becomes visible
            expectEvent(stream2, editorMatcher("onStartInput", marker), TIMEOUT);
            runOnMainSync(() -> editText
                    .getContext()
                    .getSystemService(InputMethodManager.class)
                    .showSoftInput(editText, 0));
            expectEvent(stream2, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Then switch to MockIme1
            stream1.skipAll();
            expectCommand(stream2, session2.callSwitchInputMethod(session1.getImeId()), TIMEOUT);
            expectEvent(stream2, eventMatcher("onDestroy"), TIMEOUT);

            // Make sure that MockIme1 eventually becomes visible
            expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
            expectEvent(stream1, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream1, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);
        });
    }

    /**
     * Verifies that the current IME is unbound and destroyed immediately after switching to
     * different IME, even if the current client doesn't have input focus.
     */
    @Test
    public void testImeUnboundAfterSwitchingWithoutInputFocus() throws Exception {
        testWithActivityAndTwoImes((session1, session2, editText, marker) -> {
            final var stream1 = session1.openEventStream();
            final var stream2 = session2.openEventStream();

            // Make sure that MockIme2 eventually becomes visible
            expectEvent(stream2, editorMatcher("onStartInput", marker), TIMEOUT);
            runOnMainSync(() -> editText
                    .getContext()
                    .getSystemService(InputMethodManager.class)
                    .showSoftInput(editText, 0));
            expectEvent(stream2, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            final AtomicReference<AlertDialog> alertDialogRef = new AtomicReference<>();
            runOnMainSync(() -> {
                final var dialog = new AlertDialog.Builder(editText.getContext()).create();
                dialog.show();
                alertDialogRef.set(dialog);
            });

            TestUtils.waitOnMainUntil(() -> !editText.hasWindowFocus(), TIMEOUT,
                    "Test activity shouldn't be focused");

            // Then switch to MockIme1
            stream1.skipAll();
            expectCommand(stream2, session2.callSwitchInputMethod(session1.getImeId()), TIMEOUT);
            // MockIme2 should be destroyed immediately after switching, even when the
            // current client doesn't have input focus.
            expectEvent(stream2, eventMatcher("onDestroy"), TIMEOUT);

            // Dismiss dialog to give input focus back to the client, so we can bind
            // and start the new IME.
            alertDialogRef.get().dismiss();

            // Make sure that MockIme1 eventually becomes visible
            expectEvent(stream1, eventMatcher("onCreate"), TIMEOUT);
            expectEvent(stream1, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream1, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);
        });
    }

    /**
     * Starts the test activity with MockIme1 and MockIme2 enabled, and MockIme2 selected as the
     * current IME, and then runs the given test code.
     *
     * @param testRunnable the test code to run.
     */
    private void testWithActivityAndTwoImes(@NonNull TestRunnable testRunnable) throws Exception {
        final var instrumentation = InstrumentationRegistry.getInstrumentation();
        try (var session1 = MockImeSession.create(
                instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder()
                        .setSuppressSetIme(true));
                var session2 = MockImeSession.create(
                        instrumentation.getContext(),
                        instrumentation.getUiAutomation(),
                     new ImeSettings.Builder()
                             .setMockImePackageName(MockImePackageNames.MockIme2)
                             .setSuppressResetIme(true))) {
            final String marker = getTestMarker();

            // Launch an Activity that shows up an IME.
            final var editTextRef = new AtomicReference<EditText>();
            TestActivity.startSync(activity -> {
                final var layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final var editText = new EditText(activity);
                editText.setPrivateImeOptions(marker);
                editText.setHint("editText");
                editText.requestFocus();
                layout.addView(editText);

                editTextRef.set(editText);
                return layout;
            });

            testRunnable.run(session1, session2, editTextRef.get(), marker);
        }
    }

    /**
     * A functional interface representing the code to test with two IMEs and an EditText
     * from the launched activity.
     */
    @FunctionalInterface
    private interface TestRunnable {

        /**
         * Runs the test code with the given IMEs and EditText reference.
         *
         * @param session1 the first mock IME session.
         * @param session2 the second mock IME session.
         * @param editText the EditText from the launched test activity.
         * @param marker   the EditText test marker.
         */
        void run(@NonNull MockImeSession session1, @NonNull MockImeSession session2,
                @NonNull EditText editText, @NonNull String marker) throws Exception;
    }
}
