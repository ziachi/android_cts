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

package android.os.cts;

import static android.os.vibrator.Flags.FLAG_VENDOR_VIBRATION_EFFECTS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.os.vibrator.VendorVibrationSession;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Tests for {@link VendorVibrationSession} and callbacks.
 */
@RunWith(Parameterized.class)
@RequiresFlagsEnabled(FLAG_VENDOR_VIBRATION_EFFECTS)
public class VendorVibrationSessionTest {
    @Rule(order = 0)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1)
    public final AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    android.Manifest.permission.ACCESS_VIBRATOR_STATE,
                    android.Manifest.permission.START_VIBRATION_SESSIONS,
                    android.Manifest.permission.VIBRATE_VENDOR_EFFECTS,
                    android.Manifest.permission.WRITE_SETTINGS);

    @Rule(order = 2)
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    /**
     *  Provides the vibrator accessed with the given vibrator ID, at the time of test running.
     *  A vibratorId of -1 indicates to use the system default vibrator.
     */
    private interface VibratorProvider {
        Vibrator getVibrator();
    }

    private static void addTestParameter(List<Object[]> data, String testLabel,
            VibratorProvider vibratorProvider) {
        data.add(new Object[]{ testLabel, vibratorProvider });
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        // Test params are Name,Vibrator pairs. All vibrators on the system should conform to this
        // test.
        ArrayList<Object[]> data = new ArrayList<>();
        // These vibrators should be identical, but verify both APIs explicitly.
        addTestParameter(data, "systemVibrator",
                () -> InstrumentationRegistry.getInstrumentation().getContext()
                        .getSystemService(Vibrator.class));
        // VibratorManager also presents getDefaultVibrator, but in VibratorManagerTest
        // it is asserted that the Vibrator system service and getDefaultVibrator are
        // the same object, so we don't test it twice here.

        VibratorManager vibratorManager = InstrumentationRegistry.getInstrumentation().getContext()
                .getSystemService(VibratorManager.class);
        for (int vibratorId : vibratorManager.getVibratorIds()) {
            addTestParameter(data, "vibratorId:" + vibratorId,
                    () -> InstrumentationRegistry.getInstrumentation().getContext()
                            .getSystemService(VibratorManager.class).getVibrator(vibratorId));
        }
        return data;
    }

    private static final long CALLBACK_TIMEOUT_MILLIS = 5000;
    private static final VibrationAttributes TOUCH_ATTRIBUTES =
            new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build();
    private static final VibrationAttributes COMMUNICATION_REQUEST_ATTRIBUTES =
            new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_COMMUNICATION_REQUEST)
                    .build();

    private final Vibrator mVibrator;
    private final Executor mExecutor;
    private final List<CancellationSignal> mCancellationSignals = new ArrayList<>();
    private final List<TestCallback> mPendingCallbacks = new ArrayList<>();

    /**
     * This listener is used for test helper methods like asserting it starts/stops vibrating.
     * It's not strongly required that the interactions with this mock are validated by all tests.
     */
    @Mock
    private Vibrator.OnVibratorStateChangedListener mStateListener;

    // Test label is needed for execution history
    public VendorVibrationSessionTest(String testLabel, VibratorProvider vibratorProvider) {
        mVibrator = vibratorProvider.getVibrator();
        mExecutor = Executors.newSingleThreadExecutor();
        assertThat(mVibrator).isNotNull();
    }

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Settings.System.putInt(context.getContentResolver(), Settings.System.VIBRATE_ON, 1);

        mVibrator.addVibratorStateListener(mStateListener);
        // Adding a listener to the Vibrator should trigger the callback once with the current
        // vibrator state, so reset mocks to clear it for tests.
        assertVibratorStateChangesTo(false);
        clearInvocations(mStateListener);
    }

    @After
    public void cleanUp() throws Exception {
        for (CancellationSignal signal : mCancellationSignals) {
            // Cancel any pending session request, even if session was not started yet.
            signal.cancel();
        }
        for (TestCallback callback : mPendingCallbacks) {
            // Wait for all pending session requests to be finished.
            callback.waitToBeFinished(CALLBACK_TIMEOUT_MILLIS);
        }

        // Clearing invocations so we can use this listener to wait for the vibrator to
        // asynchronously cancel the ongoing vibration, if any was left pending by a test.
        clearInvocations(mStateListener);
        mVibrator.cancel();

        // Wait for cancel to take effect, if device is still vibrating.
        if (mVibrator.isVibrating()) {
            assertVibratorStateChangesTo(false);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.os.Vibrator#areVendorSessionsSupported",
            "android.os.Vibrator#startVendorSession",
            "android.os.vibrator.VendorVibrationSession.Callback#onStarted",
    })
    public void testDeviceWithoutVibrator_returnsUnsupportedStatus() throws Exception {
        assumeFalse(mVibrator.hasVibrator());

        TestCallback callback = startSession(TOUCH_ATTRIBUTES);
        assertSessionNeverStarted(callback, VendorVibrationSession.STATUS_UNSUPPORTED);
    }

  @Test
    @ApiTest(apis = {
            "android.os.Vibrator#areVendorSessionsSupported",
            "android.os.Vibrator#startVendorSession",
            "android.os.vibrator.VendorVibrationSession.Callback#onStarted",
    })
    public void testVendorSessionsNotSupported_returnsUnsupportedStatus() throws Exception {
        assumeFalse(mVibrator.areVendorSessionsSupported());

        TestCallback callback = startSession(TOUCH_ATTRIBUTES);
        assertSessionNeverStarted(callback, VendorVibrationSession.STATUS_UNSUPPORTED);
    }

    @Test
    @ApiTest(apis = {
            "android.os.Vibrator#areVendorSessionsSupported",
            "android.os.Vibrator#startVendorSession",
            "android.os.vibrator.VendorVibrationSession#close",
            "android.os.vibrator.VendorVibrationSession.Callback#onStarted",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinishing",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinished",
    })
    public void testStartThenEndSession() throws Exception {
        assumeTrue(mVibrator.areVendorSessionsSupported());

        TestCallback callback = startSession(TOUCH_ATTRIBUTES);
        VendorVibrationSession session = assertSessionRunning(callback);

        session.close();
        assertSessionFinishingThenFinished(callback, VendorVibrationSession.STATUS_SUCCESS);
    }

    @Test
    @ApiTest(apis = {
            "android.os.Vibrator#areVendorSessionsSupported",
            "android.os.Vibrator#startVendorSession",
            "android.os.vibrator.VendorVibrationSession#cancel",
            "android.os.vibrator.VendorVibrationSession.Callback#onStarted",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinishing",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinished",
    })
    public void testStartThenCancelSession() throws Exception {
        assumeTrue(mVibrator.areVendorSessionsSupported());

        TestCallback callback = startSession(TOUCH_ATTRIBUTES);
        VendorVibrationSession session = assertSessionRunning(callback);

        session.cancel();
        assertSessionFinishingThenFinished(callback, VendorVibrationSession.STATUS_CANCELED);
    }

    @Test
    @ApiTest(apis = {
            "android.os.Vibrator#areVendorSessionsSupported",
            "android.os.Vibrator#startVendorSession",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinished",
    })
    public void testStartSessionThenSendCancelSignal() throws Exception {
        assumeTrue(mVibrator.areVendorSessionsSupported());

        CancellationSignal cancellationSignal = new CancellationSignal();
        TestCallback callback = startSession(TOUCH_ATTRIBUTES, cancellationSignal);
        cancellationSignal.cancel();

        // Cannot assert if onStarted() and onFinishing() were triggered because the session might
        // be cancelled by the signal before it starts.
        assertSessionFinished(callback, VendorVibrationSession.STATUS_CANCELED);
    }

    @Test
    @ApiTest(apis = {
            "android.os.Vibrator#areVendorSessionsSupported",
            "android.os.Vibrator#startVendorSession",
            "android.os.vibrator.VendorVibrationSession#close",
            "android.os.vibrator.VendorVibrationSession.Callback#onStarted",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinishing",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinished",
    })
    public void testStartSecondSessionEndsFirst() throws Exception {
        assumeTrue(mVibrator.areVendorSessionsSupported());

        TestCallback firstCallback = startSession(TOUCH_ATTRIBUTES);
        assertSessionRunning(firstCallback);

        TestCallback secondCallback = startSession(COMMUNICATION_REQUEST_ATTRIBUTES);
        assertSessionRunning(secondCallback);

        // First session started, so expect it to be notified by onFinishing()
        assertSessionFinishingThenFinished(firstCallback, VendorVibrationSession.STATUS_CANCELED);
    }

    @Test
    @ApiTest(apis = {
            "android.os.Vibrator#areVendorSessionsSupported",
            "android.os.Vibrator#startVendorSession",
            "android.os.vibrator.VendorVibrationSession#vibrate",
            "android.os.vibrator.VendorVibrationSession#close",
            "android.os.vibrator.VendorVibrationSession.Callback#onStarted",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinishing",
            "android.os.vibrator.VendorVibrationSession.Callback#onFinished",
    })
    public void testSessionVibrate() throws Exception {
        assumeTrue(mVibrator.areVendorSessionsSupported());

        TestCallback callback = startSession(TOUCH_ATTRIBUTES);
        VendorVibrationSession session = assertSessionRunning(callback);

        session.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE), "r");
        assertVibratorStateChangesTo(true);

        session.close();
        assertSessionFinishingThenFinished(callback, VendorVibrationSession.STATUS_SUCCESS);
        assertVibratorStateChangesTo(false);
    }

    private TestCallback startSession(VibrationAttributes attrs) {
        return startSession(attrs, /* cancellationSignal= */ null);
    }

    private TestCallback startSession(VibrationAttributes attrs,
            CancellationSignal cancellationSignal) {
        if (cancellationSignal == null) {
            cancellationSignal = new CancellationSignal();
        }
        TestCallback callback = new TestCallback();
        mVibrator.startVendorSession(attrs, "reason", cancellationSignal, mExecutor, callback);
        mCancellationSignals.add(cancellationSignal);
        mPendingCallbacks.add(callback);
        return callback;
    }

    private void assertVibratorStateChangesTo(boolean expected) {
        if (mVibrator.hasVibrator()) {
            verify(mStateListener,
                    timeout(CALLBACK_TIMEOUT_MILLIS).atLeastOnce().description(
                            "Vibrator expected to turn " + (expected ? "on" : "off")))
                    .onVibratorStateChanged(eq(expected));
        }
    }

    private VendorVibrationSession assertSessionRunning(TestCallback callback)
            throws InterruptedException {
        VendorVibrationSession session = callback.waitForSession(CALLBACK_TIMEOUT_MILLIS);
        assertWithMessage("Expected session running, got status " + callback.mStatus)
                .that(callback.isFinished()).isFalse();
        assertWithMessage("Expected session running, got notified for onFinishing()")
                .that(callback.wasNotifiedFinishing()).isFalse();
        assertWithMessage("Expected session running, no notification for onStarted")
                .that(session).isNotNull();
        return session;
    }

    private void assertSessionNeverStarted(TestCallback callback, int expectedStatus)
            throws InterruptedException {
        assertSessionFinished(callback, expectedStatus);
        assertWithMessage("Unexpected session started").that(callback.mSession).isNull();
        assertWithMessage("Unexpected notification for onFinishing() on session that never started")
                .that(callback.wasNotifiedFinishing()).isFalse();
    }

    private void assertSessionFinishingThenFinished(TestCallback callback, int expectedStatus)
            throws InterruptedException {
        assertSessionFinished(callback, expectedStatus);
        assertWithMessage("Missing notification for onFinishing()")
                .that(callback.wasNotifiedFinishing()).isTrue();
    }

    private void assertSessionFinished(TestCallback callback, int expectedStatus)
            throws InterruptedException {
        assertWithMessage("Missing notification for onFinished()")
                .that(callback.waitToBeFinished(CALLBACK_TIMEOUT_MILLIS)).isTrue();
        assertThat(callback.mStatus).isEqualTo(expectedStatus);
    }

    /** Test implementation for {@link VendorVibrationSession.Callback}. */
    private static final class TestCallback implements VendorVibrationSession.Callback {

        private VendorVibrationSession mSession;
        private boolean mNotifiedFinishing;
        private int mStatus = VendorVibrationSession.STATUS_UNKNOWN;

        @Override
        public synchronized void onStarted(@NonNull VendorVibrationSession session) {
            mSession = session;
            notifyAll();
        }

        @Override
        public synchronized void onFinishing() {
            mNotifiedFinishing = true;
            notifyAll();
        }

        @Override
        public synchronized void onFinished(int status) {
            mStatus = status;
            notifyAll();
        }

        synchronized boolean wasNotifiedFinishing() {
            return mNotifiedFinishing;
        }

        synchronized boolean isFinished() {
            return mStatus != VendorVibrationSession.STATUS_UNKNOWN;
        }

        synchronized VendorVibrationSession waitForSession(long timeoutMs)
                throws InterruptedException {
            long now = SystemClock.elapsedRealtime();
            long deadline = now + timeoutMs;
            while (true) {
                if (mSession != null) {
                    return mSession;
                }
                if (isFinished() || now >= deadline) {
                    return null;
                }
                wait(deadline - now);
                now = SystemClock.elapsedRealtime();
            }
        }

        synchronized boolean waitToBeFinished(long timeoutMs) throws InterruptedException {
            long now = SystemClock.elapsedRealtime();
            long deadline = now + timeoutMs;
            while (true) {
                if (isFinished()) {
                    return true;
                }
                if (now >= deadline) {
                    return false;
                }
                wait(deadline - now);
                now = SystemClock.elapsedRealtime();
            }
        }
    }
}
