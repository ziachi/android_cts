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

package android.display.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.UiDeviceUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Display;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.server.display.feature.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Tests that applications can receive display events correctly.
 */
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class DisplayEventTest extends TestBase {
    private static final float RR_FLOAT_DELTA = 0.01f;
    private static final String TAG = "DisplayEventTest";

    private static final int MESSAGE_CALLBACK = 1;

    private static final long TEST_FAILURE_TIMEOUT_MSEC = 5000;

    private static final int DISPLAY_ADDED = 1;
    private static final int DISPLAY_CHANGED = 2;
    private static final int DISPLAY_REMOVED = 3;

    private final Object mLock = new Object();

    private Instrumentation mInstrumentation;
    private Context mContext;
    private DisplayManager mDisplayManager;

    private PowerManager mPowerManager;

    private Display mDisplay;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            Manifest.permission.OVERRIDE_DISPLAY_MODE_REQUESTS,
            Manifest.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE,
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);


    @Rule
    public ActivityScenarioRule<DisplayEventPropertyChangeActivity> mActivityRule =
            new ActivityScenarioRule<>(DisplayEventPropertyChangeActivity.class);

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Messenger mMessenger;
    private final LinkedBlockingQueue<Pair<Integer, Integer>> mExpectations =
            new LinkedBlockingQueue<>();
    private DisplayManager.DisplayListener mDisplayListener;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mPowerManager = mContext.getSystemService(PowerManager.class);
        mDisplay = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        mHandlerThread = new HandlerThread("handler");
        mHandlerThread.start();
        mHandler = new TestHandler(mHandlerThread.getLooper());
        mMessenger = new Messenger(mHandler);
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quitSafely();
        if (mDisplayListener != null) {
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_LISTENER_PERFORMANCE_IMPROVEMENTS)
    public void testDisplayStateChangedEvent() {
        registerDisplayListener((int) DisplayManager.EVENT_TYPE_DISPLAY_STATE);

        // Change the display state
        switchDisplayState();

        // Validate the event was received
        waitDisplayEvent(Display.DEFAULT_DISPLAY, DISPLAY_CHANGED);

        // Change the display state
        switchDisplayState();

        // Validate the event was received
        waitDisplayEvent(Display.DEFAULT_DISPLAY, DISPLAY_CHANGED);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_LISTENER_PERFORMANCE_IMPROVEMENTS)
    public void testDisplayRefreshRateChangedEvent() throws InterruptedException {
        assumeTrue(notInConcurrentDisplayState());
        registerDisplayListener((int) DisplayManager.EVENT_TYPE_DISPLAY_REFRESH_RATE);

        switchRefreshRate();

        waitDisplayEvent(Display.DEFAULT_DISPLAY, DISPLAY_CHANGED);
    }

    @Test
    public void testNoDisplayRefreshRateChangedEvent() throws InterruptedException {
        assumeTrue(notInConcurrentDisplayState());
        registerDisplayListener((int) DisplayManager.EVENT_TYPE_DISPLAY_CHANGED);

        switchRefreshRate();

        assertNoDisplayEventEmitted();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_DELAY_IMPLICIT_RR_REGISTRATION_UNTIL_RR_ACCESSED)
    public void test_displayRrChangedEvent_delayImplicitRegistrationUntilRrAccessedDisabled()
            throws InterruptedException {
        assumeTrue(notInConcurrentDisplayState());
        registerDisplayListener();

        switchRefreshRate();

        waitDisplayEvent(Display.DEFAULT_DISPLAY, DISPLAY_CHANGED);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DELAY_IMPLICIT_RR_REGISTRATION_UNTIL_RR_ACCESSED)
    public void test_noDisplayRrChangedEvent_delayImplicitRegistrationUntilRrAccessedEnabled()
            throws InterruptedException {
        assumeTrue(notInConcurrentDisplayState());

        // Reset the implicit RR callbacks registration
        mDisplayManager.resetImplicitRefreshRateCallbackStatus();

        registerDisplayListener();
        switchRefreshRate();
        assertNoDisplayEventEmitted();

        // This tells DisplayManager that the client is interested in refresh rate data, so register
        // them for refresh rate change callbacks
        mDisplay.getRefreshRate();
        switchRefreshRate();
        waitDisplayEvent(Display.DEFAULT_DISPLAY, DISPLAY_CHANGED);
    }

    boolean notInConcurrentDisplayState() {
        long invalidDisplayStatesCount = Arrays.stream(mDisplayManager.getDisplays())
                .filter(display -> (display.getDisplayId() == Display.DEFAULT_DISPLAY
                        && display.getState() != Display.STATE_ON)
                        || (display.getDisplayId() != Display.DEFAULT_DISPLAY
                        && display.getState() == Display.STATE_ON))
                .count();
        return invalidDisplayStatesCount == 0;
    }

    private void registerDisplayListener(int eventFlagMask) {
        initDisplayListener();
        mDisplayManager.registerDisplayListener(
                mContext.getMainExecutor(), eventFlagMask, mDisplayListener);
    }

    private void initDisplayListener() {
        mDisplayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                callback(displayId, DISPLAY_ADDED);
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                callback(displayId, DISPLAY_REMOVED);
            }

            @Override
            public void onDisplayChanged(int displayId) {
                callback(displayId, DISPLAY_CHANGED);
            }
        };
    }

    private void registerDisplayListener() {
        initDisplayListener();
        mDisplayManager.registerDisplayListener(
                mDisplayListener, new Handler(Looper.getMainLooper()));
    }

    /**
     * Add the received display event from the test activity to the queue
     *
     * @param event The corresponding display event
     */
    private void addDisplayEvent(int displayId, int event) {
        Log.d(TAG, "Received " + displayId + " " + event);
        mExpectations.offer(new Pair<>(displayId, event));
    }

    /**
     * Wait for the expected display event from the test activity
     *
     * @param expect The expected display event
     */
    private void waitDisplayEvent(int displayId, int expect) {
        while (true) {
            try {
                Pair<Integer, Integer> expectedPair = new Pair<>(displayId, expect);
                Pair<Integer, Integer> event =
                        mExpectations.poll(TEST_FAILURE_TIMEOUT_MSEC, TimeUnit.MILLISECONDS);
                assertNotNull(event);
                if (expectedPair.equals(event)) {
                    return;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Validates that no events are emitted */
    private void assertNoDisplayEventEmitted() {
        try {
            Pair<Integer, Integer> event =
                    mExpectations.poll(TEST_FAILURE_TIMEOUT_MSEC, TimeUnit.MILLISECONDS);
            assertNull(event);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Flushes all the display events received soo far */
    private void flushDisplayEventsQueue() {
        mExpectations.clear();
    }

    private void switchDisplayState() {
        if (!mPowerManager.isInteractive()) {
            UiDeviceUtils.pressWakeupButton();
        } else {
            UiDeviceUtils.pressSleepButton();
        }
    }

    private void switchRefreshRate() throws InterruptedException {
        UiDeviceUtils.pressSleepButton();
        UiDeviceUtils.pressWakeupButton();
        int mInitialMatchContentFrameRate =
                toSwitchingType(mDisplayManager.getMatchContentFrameRateUserPreference());
        setRefreshRateSwitchingType(DisplayManager.SWITCHING_TYPE_NONE, true);
        flushDisplayEventsQueue();

        int alternateRefreshRateModeId = getAlternateRefreshRateModeId();
        mActivityRule.getScenario().onActivity(activity -> {
            activity.setModeId(alternateRefreshRateModeId);
        });

        setRefreshRateSwitchingType(mInitialMatchContentFrameRate, false);
    }

    private void setRefreshRateSwitchingType(int switchingType, boolean appRequestedMode)
            throws InterruptedException {
        mDisplayManager.setRefreshRateSwitchingType(switchingType);
        mDisplayManager.setShouldAlwaysRespectAppRequestedMode(appRequestedMode);

        // Wait for DisplayModeDirector to notify sf about the changes to the switching type
        Thread.sleep(2000);
    }

    private int getAlternateRefreshRateModeId() {
        int refreshRateModeId = mDisplay.getMode().getModeId();
        boolean supportsMultipleRefreshRates = false;
        for (Display.Mode mode : mDisplay.getSupportedModes()) {
            if (mode.getModeId() == mDisplay.getMode().getModeId()) {
                continue;
            }

            if (mode.getPhysicalHeight() != mDisplay.getMode().getPhysicalHeight()) {
                continue;
            }

            if (mode.getPhysicalWidth() != mDisplay.getMode().getPhysicalWidth()) {
                continue;
            }

            if (mode.isSynthetic()) {
                continue;
            }

            if (!floatEquals(mode.getRefreshRate(), mDisplay.getMode().getRefreshRate(),
                    RR_FLOAT_DELTA)) {
                supportsMultipleRefreshRates = true;
                refreshRateModeId = mode.getModeId();
            }
        }
        assumeTrue(supportsMultipleRefreshRates);
        return refreshRateModeId;
    }

    private boolean floatEquals(float f1, float f2, float delta) {
        return Math.abs(f1 - f2) <= delta;
    }

    private static int toSwitchingType(int matchContentFrameRateUserPreference) {
        switch (matchContentFrameRateUserPreference) {
            case DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER:
                return DisplayManager.SWITCHING_TYPE_NONE;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY:
                return DisplayManager.SWITCHING_TYPE_WITHIN_GROUPS;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS:
                return DisplayManager.SWITCHING_TYPE_ACROSS_AND_WITHIN_GROUPS;
            default:
                return -1;
        }
    }

    private class TestHandler extends Handler {
        TestHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_CALLBACK:
                    synchronized (mLock) {
                        addDisplayEvent(msg.arg1, msg.arg2);
                    }
                    break;
                default:
                    fail("Unexpected value: " + msg.what);
                    break;
            }
        }
    }

    private void callback(int displayId, int event) {
        try {
            Message msg = Message.obtain();
            msg.what = MESSAGE_CALLBACK;
            msg.arg1 = displayId;
            msg.arg2 = event;
            Log.d(TAG, "Msg " + msg.arg1 + " " + msg.arg2);
            mMessenger.send(msg);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}
