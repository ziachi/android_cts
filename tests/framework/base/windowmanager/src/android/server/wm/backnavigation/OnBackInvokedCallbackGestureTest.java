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

package android.server.wm.backnavigation;

import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT;
import static android.window.OnBackInvokedDispatcher.PRIORITY_SYSTEM_NAVIGATION_OBSERVER;

import static com.android.window.flags.Flags.FLAG_PREDICTIVE_BACK_PRIORITY_SYSTEM_NAVIGATION_OBSERVER;
import static com.android.window.flags.Flags.FLAG_PREDICTIVE_BACK_SWIPE_EDGE_NONE_API;
import static com.android.window.flags.Flags.FLAG_PREDICTIVE_BACK_SYSTEM_OVERRIDE_CALLBACK;
import static com.android.window.flags.Flags.FLAG_PREDICTIVE_BACK_TIMESTAMP_API;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.TouchHelper;
import android.view.KeyEvent;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.SystemOnBackInvokedCallbacks;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Integration test for back navigation
 */
public class OnBackInvokedCallbackGestureTest extends ActivityManagerTestBase {
    private static final int PROGRESS_SWIPE_STEPS = 10;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Instrumentation mInstrumentation;
    private UiDevice mUiDevice;
    private BackInvocationTracker mTracker = new BackInvocationTracker();
    private BackNavigationActivity mActivity;
    private TouchHelper.SwipeSession mTouchSwipeSession;

    private final OnBackAnimationCallback mAnimationCallback = new OnBackAnimationCallback() {
        @Override
        public void onBackStarted(@NonNull BackEvent e) {
            mTracker.trackBackStarted(e);
        }

        @Override
        public void onBackInvoked() {
            mTracker.trackBackInvoked();
        }

        @Override
        public void onBackCancelled() {
            mTracker.trackBackCancelled();
        }

        @Override
        public void onBackProgressed(@NonNull BackEvent e) {
            mTracker.trackBackProgressed(e);
        }
    };

    @Before
    public void setup() throws Exception {
        super.setUp();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mUiDevice = UiDevice.getInstance(mInstrumentation);
        enableAndAssumeGestureNavigationMode();

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
        mTracker.reset();

        final TestActivitySession<BackNavigationActivity> activitySession =
                createManagedTestActivitySession();
        activitySession.launchTestActivityOnDisplaySync(
                BackNavigationActivity.class, DEFAULT_DISPLAY);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        mInstrumentation.getUiAutomation().syncInputTransactions();

        mActivity = activitySession.getActivity();
        mTouchSwipeSession = new TouchHelper.SwipeSession(DEFAULT_DISPLAY, true, false);
    }

    @After
    public void teardown() {
        if (mTouchSwipeSession != null && mTouchSwipeSession.isSwipe()) {
            mTouchSwipeSession.finishSwipe();
        }
    }

    private Supplier<String> progressFailMessageSupplier() {
        return () -> {
            final BackEvent lastEvent = mTracker.mProgressEvents.getLast();
            final int eventCount = mTracker.mProgressEvents.size();
            if (lastEvent != null) {
                return "Did not receive onProgress, event count ="
                        + eventCount
                        + ", "
                        + "last event="
                        + lastEvent;
            } else {
                return "Did not receive onProgress at all!";
            }
        };
    }

    @Test
    @RequiresFlagsDisabled(FLAG_PREDICTIVE_BACK_TIMESTAMP_API)
    public void invokesCallback_invoked_preTimestampApi() throws InterruptedException {
        registerBackCallback(mActivity, mAnimationCallback, PRIORITY_DEFAULT);
        int midHeight = mUiDevice.getDisplayHeight() / 2;
        int midWidth = mUiDevice.getDisplayWidth() / 2;

        mTouchSwipeSession.beginSwipe(0, midHeight);
        // Back start event shouldn't be sent until the edge swipe threshold is crossed.
        assertNotInvoked(mTracker.mStartLatch);

        mTouchSwipeSession.continueSwipe(midWidth, midHeight, PROGRESS_SWIPE_STEPS);
        assertInvoked(mTracker.mStartLatch);
        assertEquals(BackEvent.EDGE_LEFT, mTracker.mOnBackStartedEvent.getSwipeEdge());
        assertInvoked(mTracker.mProgressLatch, progressFailMessageSupplier());
        assertNotInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);
        List<BackEvent> events = mTracker.mProgressEvents;
        assertTrue(events.size() > 0);
        for (int i = 0; i < events.size() - 1; i++) {
            // Check that progress events report increasing progress values.
            // TODO(b/258817762): Verify more once the progress clamping behavior is implemented.
            BackEvent event = events.get(i);
            assertTrue(event.getProgress() <= events.get(i + 1).getProgress());
            assertTrue(event.getTouchX() <= events.get(i + 1).getTouchX());
            assertEquals(midHeight, midHeight, event.getTouchY());
            assertEquals(BackEvent.EDGE_LEFT, event.getSwipeEdge());
        }

        mTouchSwipeSession.finishSwipe();
        assertInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PREDICTIVE_BACK_TIMESTAMP_API)
    public void invokesCallback_invoked() throws InterruptedException {
        registerBackCallback(mActivity, mAnimationCallback, PRIORITY_DEFAULT);
        int midHeight = mUiDevice.getDisplayHeight() / 2;
        int midWidth = mUiDevice.getDisplayWidth() / 2;

        mTouchSwipeSession.beginSwipe(0, midHeight);
        // Back start event shouldn't be sent until the edge swipe threshold is crossed.
        assertNotInvoked(mTracker.mStartLatch);

        mTouchSwipeSession.continueSwipe(midWidth, midHeight, PROGRESS_SWIPE_STEPS);
        assertInvoked(mTracker.mStartLatch);
        assertInvoked(mTracker.mProgressLatch, progressFailMessageSupplier());
        assertNotInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);
        List<BackEvent> events = mTracker.mProgressEvents;
        assertTrue(events.size() > 0);
        for (int i = 0; i < events.size() - 1; i++) {
            // Check that progress events report increasing progress and frameTime values.
            BackEvent event = events.get(i);
            assertTrue(event.getProgress() <= events.get(i + 1).getProgress());
            assertTrue(event.getTouchX() <= events.get(i + 1).getTouchX());
            assertTrue("frame time must be monotonically increasing",
                    event.getFrameTimeMillis() <= events.get(i + 1).getFrameTimeMillis());
            assertTrue("frame time must be >= 0", event.getFrameTimeMillis() >= 0);
            assertEquals(midHeight, midHeight, event.getTouchY());
            assertEquals(BackEvent.EDGE_LEFT, event.getSwipeEdge());
        }

        mTouchSwipeSession.finishSwipe();
        assertInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);
    }

    @Test
    public void invokesCallback_cancelled() throws InterruptedException {
        registerBackCallback(mActivity, mAnimationCallback, PRIORITY_DEFAULT);
        int midHeight = mUiDevice.getDisplayHeight() / 2;
        int midWidth = mUiDevice.getDisplayWidth() / 2;

        mTouchSwipeSession.beginSwipe(0, midHeight);
        mTouchSwipeSession.continueSwipe(midWidth, midHeight, PROGRESS_SWIPE_STEPS);
        assertInvoked(mTracker.mProgressLatch, progressFailMessageSupplier());

        mTracker.reset();
        mTracker.mIsCancelRequested = true;
        mTouchSwipeSession.cancelSwipe();

        assertInvoked(mTracker.mCancelLatch);
        assertNotInvoked(mTracker.mInvokeLatch);
        assertInvoked(mTracker.mCancelProgressLatch);
        List<BackEvent> events = mTracker.mProgressEvents;
        assertTrue(events.size() > 0);
        assertTrue(events.get(events.size() - 1).getProgress() == 0);
    }

    @Test
    @RequiresFlagsDisabled(FLAG_PREDICTIVE_BACK_SWIPE_EDGE_NONE_API)
    public void invokesCallbackInButtonsNav_invoked_preEdgeNoneApi() throws InterruptedException {
        registerBackCallback(mActivity, mAnimationCallback, PRIORITY_DEFAULT);
        long downTime = TouchHelper.injectKeyActionDown(KeyEvent.KEYCODE_BACK,
                /* longpress = */ false,
                /* sync = */ true);

        assertInvoked(mTracker.mStartLatch);
        assertNotInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);
        assertTrue(mActivity.mOnUserInteractionCalled);

        TouchHelper.injectKeyActionUp(KeyEvent.KEYCODE_BACK,
                /* downTime = */ downTime,
                /* cancelled = */ false,
                /* sync = */ true);

        assertInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mCancelLatch);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PREDICTIVE_BACK_SWIPE_EDGE_NONE_API)
    public void invokesCallbackInButtonsNav_invoked() throws InterruptedException {
        registerBackCallback(mActivity, mAnimationCallback, PRIORITY_DEFAULT);
        long downTime = TouchHelper.injectKeyActionDown(KeyEvent.KEYCODE_BACK,
                /* longpress = */ false,
                /* sync = */ true);

        assertInvoked(mTracker.mStartLatch);
        assertEquals(BackEvent.EDGE_NONE, mTracker.mOnBackStartedEvent.getSwipeEdge());
        assertNotInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);
        assertTrue(mActivity.mOnUserInteractionCalled);

        TouchHelper.injectKeyActionUp(KeyEvent.KEYCODE_BACK,
                /* downTime = */ downTime,
                /* cancelled = */ false,
                /* sync = */ true);

        assertInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mCancelLatch);
    }

    @Test
    public void invokesCallbackInButtonsNav_cancelled() throws InterruptedException {
        registerBackCallback(mActivity, mAnimationCallback, PRIORITY_DEFAULT);
        long downTime = TouchHelper.injectKeyActionDown(KeyEvent.KEYCODE_BACK,
                /* longpress = */ false,
                /* sync = */ true);

        TouchHelper.injectKeyActionUp(KeyEvent.KEYCODE_BACK,
                /* downTime = */ downTime,
                /* cancelled = */ true,
                /* sync = */ true);

        assertTrue(mActivity.mOnUserInteractionCalled);
        assertInvoked(mTracker.mCancelLatch);
        assertNotInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mInvokeLatch);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PREDICTIVE_BACK_PRIORITY_SYSTEM_NAVIGATION_OBSERVER)
    public void invokesObserverCallback_invoked() throws InterruptedException {
        registerBackCallback(mActivity, mAnimationCallback, PRIORITY_SYSTEM_NAVIGATION_OBSERVER);
        int midHeight = mUiDevice.getDisplayHeight() / 2;
        int midWidth = mUiDevice.getDisplayWidth() / 2;

        mTouchSwipeSession.beginSwipe(0, midHeight);
        mTouchSwipeSession.continueSwipe(midWidth, midHeight, PROGRESS_SWIPE_STEPS);

        // Assert that observer callback does not receive start and progress events during the
        // gesture
        assertNotInvoked(mTracker.mStartLatch);
        assertNotInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);

        mTouchSwipeSession.finishSwipe();
        assertInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PREDICTIVE_BACK_PRIORITY_SYSTEM_NAVIGATION_OBSERVER)
    public void invokesObserverCallbackInButtonsNav_invoked() throws InterruptedException {
        registerBackCallback(mActivity, mAnimationCallback, PRIORITY_SYSTEM_NAVIGATION_OBSERVER);
        long downTime = TouchHelper.injectKeyActionDown(KeyEvent.KEYCODE_BACK,
                /* longpress = */ false,
                /* sync = */ true);

        assertNotInvoked(mTracker.mStartLatch);
        assertNotInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);

        assertTrue(mActivity.mOnUserInteractionCalled);

        TouchHelper.injectKeyActionUp(KeyEvent.KEYCODE_BACK,
                /* downTime = */ downTime,
                /* cancelled = */ false,
                /* sync = */ true);

        assertInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mStartLatch);
        assertNotInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mCancelLatch);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PREDICTIVE_BACK_PRIORITY_SYSTEM_NAVIGATION_OBSERVER,
            FLAG_PREDICTIVE_BACK_SYSTEM_OVERRIDE_CALLBACK})
    public void invokesSystemOverrideObserverCallback_invoked()
            throws InterruptedException {
        registerBackCallback(mActivity, mAnimationCallback, PRIORITY_SYSTEM_NAVIGATION_OBSERVER);
        // The override system callback can trigger navigation observer.
        registerBackCallback(mActivity, SystemOnBackInvokedCallbacks
                        .moveTaskToBackCallback(mActivity), PRIORITY_DEFAULT);
        int midHeight = mUiDevice.getDisplayHeight() / 2;
        int midWidth = mUiDevice.getDisplayWidth() / 2;

        mTouchSwipeSession.beginSwipe(0, midHeight);
        mTouchSwipeSession.continueSwipe(midWidth, midHeight, PROGRESS_SWIPE_STEPS);

        // Assert that observer callback does not receive start and progress events during the
        // gesture
        assertNotInvoked(mTracker.mStartLatch);
        assertNotInvoked(mTracker.mProgressLatch);
        assertNotInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);

        final ComponentName activityName = mActivity.getComponentName();
        mTouchSwipeSession.finishSwipe();
        assertInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);
        mWmState.waitAndAssertActivityState(activityName, STATE_STOPPED);
    }

    @Test
    public void ignoresKeyCodeBackDuringDispatch() throws InterruptedException {
        registerBackCallback(mActivity, mAnimationCallback, PRIORITY_DEFAULT);
        int midHeight = mUiDevice.getDisplayHeight() / 2;
        int midWidth = mUiDevice.getDisplayWidth() / 2;

        mTouchSwipeSession.beginSwipe(0, midHeight);
        mTouchSwipeSession.continueSwipe(midWidth, midHeight, PROGRESS_SWIPE_STEPS);
        assertInvoked(mTracker.mStartLatch);
        assertInvoked(mTracker.mProgressLatch, progressFailMessageSupplier());
        waitForIdle();
        mTracker.reset();
        TouchHelper.injectKey(KeyEvent.KEYCODE_BACK, false /* longpress */, true /* sync */);
        waitForIdle();
        // Make sure the KEYCODE_BACKs don't invoke callbacks.
        assertNotInvoked(mTracker.mInvokeLatch);
        assertNotInvoked(mTracker.mCancelLatch);
    }

    @Test
    @RequiresFlagsDisabled(FLAG_PREDICTIVE_BACK_TIMESTAMP_API)
    public void constructsEvent_preTimestampApi() {
        final float x = 200;
        final float y = 300;
        final float progress = 0.5f;
        final int swipeEdge = BackEvent.EDGE_RIGHT;
        BackEvent event = new BackEvent(x, y, progress, swipeEdge);
        assertEquals(x, event.getTouchX());
        assertEquals(y, event.getTouchY());
        assertEquals(progress, event.getProgress());
        assertEquals(swipeEdge, event.getSwipeEdge());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PREDICTIVE_BACK_TIMESTAMP_API)
    public void constructsEvent() {
        final float x = 200;
        final float y = 300;
        final float progress = 0.5f;
        final int swipeEdge = BackEvent.EDGE_RIGHT;
        final long frameTimeMillis = 1234567;
        BackEvent event = new BackEvent(x, y, progress, swipeEdge, frameTimeMillis);
        assertEquals(x, event.getTouchX());
        assertEquals(y, event.getTouchY());
        assertEquals(progress, event.getProgress());
        assertEquals(swipeEdge, event.getSwipeEdge());
        assertEquals(frameTimeMillis, event.getFrameTimeMillis());
    }

    private void assertInvoked(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
    }

    private void assertInvoked(CountDownLatch latch, Supplier<String> failMessage)
            throws InterruptedException {
        if (!latch.await(1000, TimeUnit.MILLISECONDS)) {
            fail(failMessage.get());
        }
    }

    private void assertNotInvoked(CountDownLatch latch) {
        assertTrue(latch.getCount() >= 1);
    }

    private void registerBackCallback(BackNavigationActivity activity,
            OnBackInvokedCallback callback, int priority) {
        CountDownLatch backRegisteredLatch = new CountDownLatch(1);
        activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                priority, callback);
        backRegisteredLatch.countDown();
        try {
            if (!backRegisteredLatch.await(100, TimeUnit.MILLISECONDS)) {
                fail("Back callback was not registered on the Activity thread. This might be "
                        + "an error with the test itself.");
            }
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    /** Helper class to track {@link android.window.OnBackAnimationCallback} invocations. */
    static class BackInvocationTracker {
        private CountDownLatch mStartLatch;
        private CountDownLatch mInvokeLatch;
        private CountDownLatch mProgressLatch;
        private CountDownLatch mCancelLatch;
        private CountDownLatch mCancelProgressLatch;
        private boolean mIsCancelRequested = false;
        private final ArrayList<BackEvent> mProgressEvents = new ArrayList<>();
        private BackEvent mOnBackStartedEvent;

        private void reset() {
            mStartLatch = new CountDownLatch(1);
            mInvokeLatch = new CountDownLatch(1);
            mProgressLatch = new CountDownLatch(1);
            mCancelLatch = new CountDownLatch(1);
            mCancelProgressLatch = new CountDownLatch(1);
            mIsCancelRequested = false;
            mProgressEvents.clear();
            mOnBackStartedEvent = null;
        }

        private void trackBackStarted(BackEvent e) {
            mStartLatch.countDown();
            mOnBackStartedEvent = e;
        }

        private void trackBackProgressed(BackEvent e) {
            mProgressEvents.add(e);
            if (mIsCancelRequested && 0 == e.getProgress()) {
                // Ensure the progress could reach to 0 for cancel animation.
                mCancelProgressLatch.countDown();
            } else if (!mIsCancelRequested && e.getProgress() > 0.2f) {
                mProgressLatch.countDown();
            }
        }

        private void trackBackCancelled() {
            mCancelLatch.countDown();
        }

        private void trackBackInvoked() {
            mInvokeLatch.countDown();
        }
    }
}
