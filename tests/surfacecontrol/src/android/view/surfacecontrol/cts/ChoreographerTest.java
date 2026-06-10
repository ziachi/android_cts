/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.view.surfacecontrol.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ChoreographerTest {
    private static final String TAG = ChoreographerTest.class.getSimpleName();
    private static final long NOMINAL_VSYNC_PERIOD = 16;
    private static final long DELAY_PERIOD = NOMINAL_VSYNC_PERIOD * 5;
    private static final long NANOS_PER_MS = 1000000;
    private static final long WAIT_TIMEOUT_S = 5;
    private static final Object TOKEN = new Object();

    private Choreographer mChoreographer;

    @UiThreadTest
    @Before
    public void setup() {
        mChoreographer = Choreographer.getInstance();
    }

    @Test
    public void testFrameDelay() {
        assertTrue(Choreographer.getFrameDelay() > 0);

        long oldFrameDelay = Choreographer.getFrameDelay();
        long newFrameDelay = oldFrameDelay * 2;
        Choreographer.setFrameDelay(newFrameDelay);
        assertEquals(newFrameDelay, Choreographer.getFrameDelay());

        Choreographer.setFrameDelay(oldFrameDelay);
    }

    @Test
    public void testPostCallbackWithoutDelay() {
        final Runnable addedCallback1 = mock(Runnable.class);
        final Runnable addedCallback2 = mock(Runnable.class);
        final Runnable removedCallback = mock(Runnable.class);
        try {
            // Add and remove a few callbacks.
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, addedCallback1, null);
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, addedCallback2, null);
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, null);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, null);

            // We expect the remaining callbacks to have been invoked once.
            verify(addedCallback1, timeout(NOMINAL_VSYNC_PERIOD * 30).times(1)).run();
            verify(addedCallback2, timeout(NOMINAL_VSYNC_PERIOD * 30).times(1)).run();
            verifyNoMoreInteractions(removedCallback);

            // If we post a callback again, then it should be invoked again.
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, addedCallback1, null);

            verify(addedCallback1, timeout(NOMINAL_VSYNC_PERIOD * 30).times(2)).run();
            verify(addedCallback2, times(1)).run();
            verifyNoMoreInteractions(removedCallback);

            // If the token matches, the the callback should be removed.
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, addedCallback1, null);
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, TOKEN);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, null, TOKEN);
            verify(addedCallback1, timeout(NOMINAL_VSYNC_PERIOD * 30).times(3)).run();
            verifyNoMoreInteractions(removedCallback);

            // If the action and token matches, then the callback should be removed.
            // If only the token matches, then the callback should not be removed.
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, addedCallback1, TOKEN);
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, TOKEN);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, TOKEN);
            verify(addedCallback1, timeout(NOMINAL_VSYNC_PERIOD * 30).times(4)).run();
            verifyNoMoreInteractions(removedCallback);
        } finally {
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, addedCallback1, null);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, addedCallback2, null);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, null);
        }
    }

    @Test
    public void testPostCallbackWithDelay() {
        final Runnable addedCallback = mock(Runnable.class);
        final Runnable removedCallback = mock(Runnable.class);
        try {
            // Add and remove a few callbacks.
            mChoreographer.postCallbackDelayed(
                    Choreographer.CALLBACK_ANIMATION, addedCallback, null, DELAY_PERIOD);
            mChoreographer.postCallbackDelayed(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, null, DELAY_PERIOD);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, null);

            // Sleep for a couple of frames.
            SystemClock.sleep(NOMINAL_VSYNC_PERIOD * 3);

            // The callbacks should not have been invoked yet because of the delay.
            verifyNoMoreInteractions(addedCallback);
            verifyNoMoreInteractions(removedCallback);

            // We expect the remaining callbacks to have been invoked.
            verify(addedCallback, timeout(DELAY_PERIOD * 3).times(1)).run();
            verifyNoMoreInteractions(removedCallback);

            // If the token matches, the the callback should be removed.
            mChoreographer.postCallbackDelayed(
                    Choreographer.CALLBACK_ANIMATION, addedCallback, null, DELAY_PERIOD);
            mChoreographer.postCallbackDelayed(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, TOKEN, DELAY_PERIOD);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, null, TOKEN);
            verify(addedCallback, timeout(DELAY_PERIOD * 3).times(2)).run();
            verifyNoMoreInteractions(removedCallback);

            // If the action and token matches, then the callback should be removed.
            // If only the token matches, then the callback should not be removed.
            mChoreographer.postCallbackDelayed(
                    Choreographer.CALLBACK_ANIMATION, addedCallback, TOKEN, DELAY_PERIOD);
            mChoreographer.postCallbackDelayed(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, TOKEN, DELAY_PERIOD);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, TOKEN);
            verify(addedCallback, timeout(DELAY_PERIOD * 3).times(3)).run();
            verifyNoMoreInteractions(removedCallback);
        } finally {
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, addedCallback, null);
            mChoreographer.removeCallbacks(
                    Choreographer.CALLBACK_ANIMATION, removedCallback, null);
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPostNullCallback() {
        mChoreographer.postCallback(Choreographer.CALLBACK_ANIMATION, null, TOKEN);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPostNullCallbackDelayed() {
        mChoreographer.postCallbackDelayed( Choreographer.CALLBACK_ANIMATION, null, TOKEN,
                DELAY_PERIOD);
    }

    @Test
    public void testPostFrameCallbackWithoutDelay() {
        final Choreographer.FrameCallback addedFrameCallback1 =
                mock(Choreographer.FrameCallback.class);
        final Choreographer.FrameCallback addedFrameCallback2 =
                mock(Choreographer.FrameCallback.class);
        final Choreographer.FrameCallback removedFrameCallback =
                mock(Choreographer.FrameCallback.class);
        try {
            // Add and remove a few callbacks.
            long postTimeNanos = System.nanoTime();
            mChoreographer.postFrameCallback(addedFrameCallback1);
            mChoreographer.postFrameCallback(addedFrameCallback2);
            mChoreographer.postFrameCallback(removedFrameCallback);
            mChoreographer.removeFrameCallback(removedFrameCallback);

            // We expect the remaining callbacks to have been invoked once.
            ArgumentCaptor<Long> frameTimeNanosCaptor1 = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Long> frameTimeNanosCaptor2 = ArgumentCaptor.forClass(Long.class);
            verify(addedFrameCallback1, timeout(NOMINAL_VSYNC_PERIOD * 10).times(1))
                    .doFrame(frameTimeNanosCaptor1.capture());
            verify(addedFrameCallback2, times(1)).doFrame(frameTimeNanosCaptor2.capture());
            verifyNoMoreInteractions(removedFrameCallback);

            assertTimeDeltaLessThan(frameTimeNanosCaptor1.getValue() - postTimeNanos,
                    NOMINAL_VSYNC_PERIOD * 10 * NANOS_PER_MS);
            assertTimeDeltaLessThan(frameTimeNanosCaptor2.getValue() - postTimeNanos,
                    NOMINAL_VSYNC_PERIOD * 10 * NANOS_PER_MS);
            assertTimeDeltaLessThan(
                    Math.abs(frameTimeNanosCaptor2.getValue() - frameTimeNanosCaptor1.getValue()),
                    NOMINAL_VSYNC_PERIOD * NANOS_PER_MS);

            // If we post a callback again, then it should be invoked again.
            postTimeNanos = System.nanoTime();
            mChoreographer.postFrameCallback(addedFrameCallback1);

            verify(addedFrameCallback1, timeout(NOMINAL_VSYNC_PERIOD * 10).times(2))
                    .doFrame(frameTimeNanosCaptor1.capture());
            verify(addedFrameCallback2, times(1)).doFrame(frameTimeNanosCaptor2.capture());
            verifyNoMoreInteractions(removedFrameCallback);
            assertTimeDeltaLessThan(frameTimeNanosCaptor1.getAllValues().get(1) - postTimeNanos,
                    NOMINAL_VSYNC_PERIOD * 10 * NANOS_PER_MS);
        } finally {
            mChoreographer.removeFrameCallback(addedFrameCallback1);
            mChoreographer.removeFrameCallback(addedFrameCallback2);
            mChoreographer.removeFrameCallback(removedFrameCallback);
        }
    }

    @Test
    public void testPostFrameCallbackWithDelay() {
        final Choreographer.FrameCallback addedFrameCallback =
                mock(Choreographer.FrameCallback.class);
        final Choreographer.FrameCallback removedFrameCallback =
                mock(Choreographer.FrameCallback.class);
        try {
            // Add and remove a few callbacks.
            long postTimeNanos = System.nanoTime();
            mChoreographer.postFrameCallbackDelayed(addedFrameCallback, DELAY_PERIOD);
            mChoreographer.postFrameCallbackDelayed(removedFrameCallback, DELAY_PERIOD);
            mChoreographer.removeFrameCallback(removedFrameCallback);

            // Sleep for a couple of frames.
            SystemClock.sleep(NOMINAL_VSYNC_PERIOD * 3);

            // The callbacks should not have been invoked yet because of the delay.
            verifyNoMoreInteractions(addedFrameCallback);
            verifyNoMoreInteractions(removedFrameCallback);

            // We expect the remaining callbacks to have been invoked.
            ArgumentCaptor<Long> frameTimeNanosCaptor = ArgumentCaptor.forClass(Long.class);
            verify(addedFrameCallback, timeout(DELAY_PERIOD * 3).times(1))
                    .doFrame(frameTimeNanosCaptor.capture());
            verifyNoMoreInteractions(removedFrameCallback);
            assertTimeDeltaLessThan(frameTimeNanosCaptor.getValue() - postTimeNanos,
                    (NOMINAL_VSYNC_PERIOD * 10 + DELAY_PERIOD) * NANOS_PER_MS);
        } finally {
            mChoreographer.removeFrameCallback(addedFrameCallback);
            mChoreographer.removeFrameCallback(removedFrameCallback);
        }
    }

    private void assertTimeDeltaLessThan(long deltaNanos, long thresholdNanos) {
        if (deltaNanos >= thresholdNanos) {
            fail("Expected time delta less than " + thresholdNanos + " nanos, actually "
                    + " was " + deltaNanos + " nanos.");
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPostNullFrameCallback() {
        mChoreographer.postFrameCallback(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testPostNullFrameCallbackDelayed() {
        mChoreographer.postFrameCallbackDelayed(null, DELAY_PERIOD);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRemoveNullFrameCallback() {
        mChoreographer.removeFrameCallback(null);
    }

    private class BasicVsyncCallback implements Choreographer.VsyncCallback {
        final long mPostTimeNanos = System.nanoTime();
        long mFrameTimeNanos;
        CountDownLatch mCallbackComplete;

        // Save status of assertions passing and error messages to pass to test thread to assert.
        boolean mAssertionPassed = true;
        String mErrorMessages = "";

        BasicVsyncCallback() {
            reset();
        }

        void reset() {
            mCallbackComplete = new CountDownLatch(1);
            mAssertionPassed = true;
            mErrorMessages = "";
        }

        boolean conditionPasses(String error, boolean conditionPassed) {
            if (!conditionPassed) {
                mAssertionPassed = false;
                mErrorMessages = (mErrorMessages.isEmpty() ? "" : "\n") + mErrorMessages + error;
                return mAssertionPassed;
            }
            return true;
        }

        @Override
        public void onVsync(Choreographer.FrameData frameData) {
            mFrameTimeNanos = frameData.getFrameTimeNanos();
            conditionPasses("Callback invoked more than once!", mCallbackComplete.getCount() == 1);
            mCallbackComplete.countDown();
        }
    }

    @Test
    public void testPostVsyncCallbackWithoutDelay() {
        final BasicVsyncCallback addedCallback1 = new BasicVsyncCallback();
        final BasicVsyncCallback addedCallback2 = new BasicVsyncCallback();
        final Choreographer.VsyncCallback removedCallback = mock(
                Choreographer.VsyncCallback.class);

        // Add and remove a few callbacks.
        long postTimeNanos = System.nanoTime();
        mChoreographer.postVsyncCallback(addedCallback1);
        mChoreographer.postVsyncCallback(addedCallback2);
        mChoreographer.postVsyncCallback(removedCallback);
        mChoreographer.removeVsyncCallback(removedCallback);

        // We expect the remaining callbacks to have been invoked once.
        awaitCountDown(addedCallback1.mCallbackComplete);
        assertTrue(addedCallback1.mErrorMessages, addedCallback1.mAssertionPassed);
        awaitCountDown(addedCallback2.mCallbackComplete);
        assertTrue(addedCallback2.mErrorMessages, addedCallback2.mAssertionPassed);
        verifyNoMoreInteractions(removedCallback);

        assertTimeDeltaLessThan(addedCallback1.mFrameTimeNanos - postTimeNanos,
                NOMINAL_VSYNC_PERIOD * 10 * NANOS_PER_MS);
        assertTimeDeltaLessThan(addedCallback2.mFrameTimeNanos - postTimeNanos,
                NOMINAL_VSYNC_PERIOD * 10 * NANOS_PER_MS);

        // If we post a callback again, then it should be invoked again.
        addedCallback1.reset();
        postTimeNanos = System.nanoTime();
        mChoreographer.postVsyncCallback(addedCallback1);

        awaitCountDown(addedCallback1.mCallbackComplete);
        assertTrue(addedCallback1.mErrorMessages, addedCallback1.mAssertionPassed);
        verifyNoMoreInteractions(removedCallback);
        assertTimeDeltaLessThan(addedCallback1.mFrameTimeNanos - postTimeNanos,
                NOMINAL_VSYNC_PERIOD * 10 * NANOS_PER_MS);
        // Callback #2 is not invoked a second time so the time delta is not checked here.
    }

    @Test
    public void testPostVsyncCallbackFrameDataPreferredFrameTimelineValid() {
        BasicVsyncCallback callback = new BasicVsyncCallback() {
            @Override
            public void onVsync(Choreographer.FrameData frameData) {
                if (!conditionPasses("Number of frame timelines should be greater than 0",
                            frameData.getFrameTimelines().length > 0)) {
                    mCallbackComplete.countDown();
                    return;
                }

                Set<Choreographer.FrameTimeline> frameTimelines =
                        Set.of(frameData.getFrameTimelines());
                conditionPasses("Preferred frame timeline is not included in frame timelines",
                        frameTimelines.contains(frameData.getPreferredFrameTimeline()));
                mCallbackComplete.countDown();
            }
        };
        mChoreographer.postVsyncCallback(callback);
        awaitCountDown(callback.mCallbackComplete);
        assertTrue(callback.mErrorMessages, callback.mAssertionPassed);
    }

    @Test
    public void testPostVsyncCallbackFrameDataVsyncIdValid() {
        BasicVsyncCallback callback = new BasicVsyncCallback() {
            @Override
            public void onVsync(Choreographer.FrameData frameData) {
                if (!conditionPasses("Number of frame timelines should be greater than 0",
                            frameData.getFrameTimelines().length > 0)) {
                    mCallbackComplete.countDown();
                    return;
                }
                HashSet<Long> pastVsyncIds = new HashSet();
                for (Choreographer.FrameTimeline frameTimeline : frameData.getFrameTimelines()) {
                    long vsyncId = frameTimeline.getVsyncId();
                    if (!conditionPasses(
                                "Invalid vsync ID " + vsyncId + " on index " + pastVsyncIds.size(),
                                vsyncId > 0)
                            || !conditionPasses(
                                    "Vsync ID should be unique", !pastVsyncIds.contains(vsyncId))) {
                        break;
                    }
                    pastVsyncIds.add(vsyncId);
                }
                mCallbackComplete.countDown();
            }
        };
        mChoreographer.postVsyncCallback(callback);
        awaitCountDown(callback.mCallbackComplete);
        assertTrue(callback.mErrorMessages, callback.mAssertionPassed);
    }

    @Test
    public void testPostVsyncCallbackFrameDataDeadlineInFuture() {
        // Run multiple attempts in case the system calls the choreographer callback too late, which
        // may cause the frame time to be later than the frame deadline.
        StringBuilder error = new StringBuilder("");
        for (int i = 0; i < 5; i++) {
            BasicVsyncCallback callback = new BasicVsyncCallback() {
                @Override
                public void onVsync(Choreographer.FrameData frameData) {
                    if (!conditionPasses("Number of frame timelines "
                                        + frameData.getFrameTimelines().length
                                        + " should be greater than 0",
                                frameData.getFrameTimelines().length > 0)) {
                        mCallbackComplete.countDown();
                        return;
                    }

                    long previousDeadline = 0;
                    for (Choreographer.FrameTimeline frameTimeline :
                            frameData.getFrameTimelines()) {
                        long deadline = frameTimeline.getDeadlineNanos();
                        if (!conditionPasses("Deadline " + deadline + " must be after start time "
                                            + mPostTimeNanos,
                                    deadline > mPostTimeNanos)) {
                            break;
                        }
                        mAssertionPassed =
                                mAssertionPassed && deadline > frameData.getFrameTimeNanos();
                        if (!conditionPasses("Deadline " + deadline
                                            + " must be after the previous frame deadline "
                                            + previousDeadline,
                                    deadline > previousDeadline)) {
                            break;
                        }
                        previousDeadline = deadline;
                    }
                    mCallbackComplete.countDown();
                }
            };
            mChoreographer.postVsyncCallback(callback);
            awaitCountDown(callback.mCallbackComplete);
            if (callback.mAssertionPassed) {
                return; // Pass
            } else {
                error.append("Attempt " + i + ": "
                        + (callback.mErrorMessages.isEmpty() ? "Deadline must be after frame time"
                                                             : callback.mErrorMessages)
                        + ".\n");
            }
        }
        fail(error.toString());
    }

    @Test
    public void testPostVsyncCallbackFrameDataExpectedPresentationTimeInFuture() {
        BasicVsyncCallback callback = new BasicVsyncCallback() {
            @Override
            public void onVsync(Choreographer.FrameData frameData) {
                if (!conditionPasses("Number of frame timelines should be greater than 0",
                            frameData.getFrameTimelines().length > 0)) {
                    mCallbackComplete.countDown();
                    return;
                }
                long lastValue = frameData.getFrameTimeNanos();
                for (Choreographer.FrameTimeline frameTimeline : frameData.getFrameTimelines()) {
                    long expectedPresentationTime =
                            frameTimeline.getExpectedPresentationTimeNanos();
                    if (!conditionPasses("Expected presentation time must be after start time",
                                expectedPresentationTime > mPostTimeNanos)
                            || !conditionPasses(
                                    "Expected presentation time must be after frame time",
                                    expectedPresentationTime > frameData.getFrameTimeNanos())
                            || !conditionPasses("Expected presentation time must be after the "
                                                + "previous frame expected "
                                            + "presentation time",
                                    expectedPresentationTime > lastValue)) {
                        break;
                    }
                    lastValue = expectedPresentationTime;
                }
                mCallbackComplete.countDown();
            }
        };

        mChoreographer.postVsyncCallback(callback);
        awaitCountDown(callback.mCallbackComplete);
        assertTrue(callback.mErrorMessages, callback.mAssertionPassed);
    }

    @Test
    public void testPostVsyncCallbackFrameDelayed() {
        final long sleepTimeMs = NOMINAL_VSYNC_PERIOD * 3;

        final BasicVsyncCallback addedCallback = new BasicVsyncCallback() {
            static final long FRAME_INTERVAL = NOMINAL_VSYNC_PERIOD * NANOS_PER_MS;

            @Override
            public void onVsync(Choreographer.FrameData frameData) {
                if (!conditionPasses("Frame time should be later",
                            frameData.getFrameTimeNanos() > mPostTimeNanos
                                            + sleepTimeMs * NANOS_PER_MS - FRAME_INTERVAL)
                        || !conditionPasses("Deadline should be after frame time",
                                frameData.getPreferredFrameTimeline().getDeadlineNanos()
                                        > frameData.getFrameTimeNanos())) {
                    // Intentionally empty, if-statement is only to short circuit on first failing
                    // statement, if any.
                }
                mCallbackComplete.countDown();
            }
        };

        mChoreographer.postVsyncCallback(addedCallback);

        // The callback is posted and pending. Wait using the handler which is using the same UI
        // thread as Choreographer, so that the thread is busy and the frame delayed logic can
        // run for test.
        Looper looper = Looper.getMainLooper();
        Handler handler = new Handler(looper);
        handler.post(new Runnable(){
            @Override
            public void run() {
                SystemClock.sleep(sleepTimeMs);
                Log.d(TAG, "Slept for ms: " + sleepTimeMs);
            }
        });

        awaitCountDown(addedCallback.mCallbackComplete);
    }

    /** For testing an exception is thrown if accessing data outside of onVsync(). */
    private class BadVsyncCallback implements Choreographer.VsyncCallback {
        CountDownLatch mCallbackComplete = new CountDownLatch(1);
        Choreographer.FrameData mFrameData;
        Choreographer.FrameTimeline mFrameTimeline;

        @Override
        public void onVsync(Choreographer.FrameData frameData) {
            mFrameData = frameData;
            mFrameTimeline = frameData.getPreferredFrameTimeline();
            mCallbackComplete.countDown();
        }
    }

    @Test
    public void testFrameDataAccessOutsideCallbackThrows() {
        final BadVsyncCallback addedCallback = new BadVsyncCallback();
        mChoreographer.postVsyncCallback(addedCallback);

        awaitCountDown(addedCallback.mCallbackComplete);

        assertThrows(
                IllegalStateException.class, () -> addedCallback.mFrameData.getFrameTimeNanos());
        assertThrows(
                IllegalStateException.class, () -> addedCallback.mFrameData.getFrameTimelines());
        assertThrows(IllegalStateException.class,
                () -> addedCallback.mFrameData.getPreferredFrameTimeline());
        assertThrows(IllegalStateException.class, () -> addedCallback.mFrameTimeline.getVsyncId());
        assertThrows(IllegalStateException.class,
                () -> addedCallback.mFrameTimeline.getExpectedPresentationTimeNanos());
        assertThrows(
                IllegalStateException.class, () -> addedCallback.mFrameTimeline.getDeadlineNanos());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPostNullVsyncCallback() {
        mChoreographer.postVsyncCallback(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveNullVsyncCallback() {
        mChoreographer.removeVsyncCallback(null);
    }

    private void awaitCountDown(CountDownLatch countDownLatch) {
        try {
            if (!countDownLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS)) {
                fail("Test timed out!");
            }
        } catch (InterruptedException ex) {
            throw new AssertionError("Test interrupted", ex);
        }
    }
}
