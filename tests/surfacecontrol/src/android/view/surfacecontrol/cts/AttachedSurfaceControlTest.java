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
package android.view.surfacecontrol.cts;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowOnTop;
import static android.view.SurfaceControl.JankData.JANK_APPLICATION;
import static android.view.cts.surfacevalidator.BitmapPixelChecker.validateScreenshot;

import static androidx.test.core.app.ActivityScenario.launch;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.IgnoreOrientationRequestSession;
import android.server.wm.WindowManagerStateHelper;
import android.util.Log;
import android.view.AttachedSurfaceControl;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.cts.surfacevalidator.BitmapPixelChecker;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.window.flags.Flags;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

@SmallTest
public class AttachedSurfaceControlTest {
    private static final String TAG = "AttachedSurfaceControlTest";
    private IgnoreOrientationRequestSession mOrientationSession;
    private WindowManagerStateHelper mWmState;

    private static final long WAIT_TIMEOUT_S = 5L * HW_TIMEOUT_MULTIPLIER;

    @Rule
    public TestName mName = new TestName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();


    private static class TransformHintListener implements
            AttachedSurfaceControl.OnBufferTransformHintChangedListener {
        Activity activity;
        int expectedOrientation;
        CountDownLatch latch = new CountDownLatch(1);
        IntConsumer hintConsumer;

        TransformHintListener(Activity activity, int expectedOrientation,
                IntConsumer hintConsumer) {
            this.activity = activity;
            this.expectedOrientation = expectedOrientation;
            this.hintConsumer = hintConsumer;
        }

        @Override
        public void onBufferTransformHintChanged(int hint) {
            int orientation = activity.getResources().getConfiguration().orientation;
            Log.d(TAG, "onBufferTransformHintChanged: orientation actual=" + orientation
                    + " expected=" + expectedOrientation + " transformHint=" + hint);
            Assert.assertEquals("Failed to switch orientation hint=" + hint, orientation,
                    expectedOrientation);

            // Check the callback value matches the call to get the transform hint.
            int actualTransformHint =
                    activity.getWindow().getRootSurfaceControl().getBufferTransformHint();
            Assert.assertEquals(
                    "Callback " + hint + " doesn't match transform hint=" + actualTransformHint,
                    hint,
                    actualTransformHint);
            hintConsumer.accept(hint);
            latch.countDown();
            activity.getWindow().getRootSurfaceControl()
                    .removeOnBufferTransformHintChangedListener(this);
        }
    }

    @Before
    public void setup() throws InterruptedException {
        mOrientationSession = new IgnoreOrientationRequestSession(false /* enable */);
        mWmState = new WindowManagerStateHelper();
    }

    private void supportRotationCheck() {
        PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        boolean supportsRotation = pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT)
                && pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE);
        mWmState.computeState();
        final boolean isFixedToUserRotation = mWmState.isFixedToUserRotation();
        Assume.assumeTrue(supportsRotation && !isFixedToUserRotation);
    }

    @After
    public void teardown() {
        if (mOrientationSession != null) {
            mOrientationSession.close();
        }
    }

    @Test
    public void testOnBufferTransformHintChangedListener() throws InterruptedException {
        supportRotationCheck();

        try (ActivityScenario<?> scenario = launch(HandleConfigurationActivity.class)) {
            Activity activity = awaitActivityStart(scenario);

            final int[] transformHintResult = new int[2];
            final CountDownLatch[] firstCallback = new CountDownLatch[1];
            final CountDownLatch[] secondCallback = new CountDownLatch[1];
            mWmState.computeState();
            assumeFalse("Skipping test: display area is ignoring orientation request",
                    mWmState.isTaskDisplayAreaIgnoringOrientationRequest(
                            activity.getComponentName()));
            int requestedOrientation = getRequestedOrientation(activity);
            TransformHintListener listener = new TransformHintListener(activity,
                    requestedOrientation, hint -> transformHintResult[0] = hint);
            firstCallback[0] = listener.latch;
            activity.getWindow().getRootSurfaceControl()
                    .addOnBufferTransformHintChangedListener(listener);
            setRequestedOrientation(activity, requestedOrientation);
            // Check we get a callback since the orientation has changed and we expect transform
            // hint to change.
            Assert.assertTrue(firstCallback[0].await(10, TimeUnit.SECONDS));

            requestedOrientation = getRequestedOrientation(activity);
            TransformHintListener secondListener = new TransformHintListener(activity,
                    requestedOrientation, hint -> transformHintResult[1] = hint);
            secondCallback[0] = secondListener.latch;
            activity.getWindow().getRootSurfaceControl()
                    .addOnBufferTransformHintChangedListener(secondListener);
            setRequestedOrientation(activity, requestedOrientation);
            // Check we get a callback since the orientation has changed and we expect transform
            // hint to change.
            Assert.assertTrue(secondCallback[0].await(10, TimeUnit.SECONDS));

            // If the app orientation was changed, we should get a different transform hint
            Assert.assertNotEquals(transformHintResult[0], transformHintResult[1]);
        }
    }

    private int getRequestedOrientation(Activity activity) {
        int currentOrientation = activity.getResources().getConfiguration().orientation;
        return currentOrientation == ORIENTATION_LANDSCAPE ? ORIENTATION_PORTRAIT
                : ORIENTATION_LANDSCAPE;
    }

    private void setRequestedOrientation(Activity activity,
            /* @Configuration.Orientation */ int requestedOrientation) {
        /* @ActivityInfo.ScreenOrientation */
        Log.d(TAG, "setRequestedOrientation: requestedOrientation=" + requestedOrientation);
        int screenOrientation =
                requestedOrientation == ORIENTATION_LANDSCAPE
                        ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        activity.setRequestedOrientation(screenOrientation);
    }

    @Test
    public void testOnBufferTransformHintChangesFromLandToSea() throws InterruptedException {
        supportRotationCheck();

        try (ActivityScenario<?> scenario = launch(HandleConfigurationActivity.class)) {
            Activity activity = awaitActivityStart(scenario);

            final int[] transformHintResult = new int[2];
            final CountDownLatch[] firstCallback = new CountDownLatch[1];
            final CountDownLatch[] secondCallback = new CountDownLatch[1];
            mWmState.computeState();
            assumeFalse("Skipping test: display area is ignoring orientation request",
                    mWmState.isTaskDisplayAreaIgnoringOrientationRequest(
                            activity.getComponentName()));
            if (activity.getResources().getConfiguration().orientation
                    != ORIENTATION_LANDSCAPE) {
                Log.d(TAG, "Request landscape orientation");
                TransformHintListener listener = new TransformHintListener(activity,
                        ORIENTATION_LANDSCAPE, hint -> {
                    transformHintResult[0] = hint;
                    Log.d(TAG, "firstListener fired with hint =" + hint);
                });
                firstCallback[0] = listener.latch;
                activity.getWindow().getRootSurfaceControl()
                        .addOnBufferTransformHintChangedListener(listener);
                setRequestedOrientation(activity, ORIENTATION_LANDSCAPE);
                Assert.assertTrue(firstCallback[0].await(10, TimeUnit.SECONDS));
            } else {
                transformHintResult[0] =
                        activity.getWindow().getRootSurfaceControl().getBufferTransformHint();
                Log.d(TAG, "Skipped request landscape orientation: hint=" + transformHintResult[0]);
            }

            TransformHintListener secondListener = new TransformHintListener(activity,
                    ORIENTATION_LANDSCAPE, hint -> {
                transformHintResult[1] = hint;
                Log.d(TAG, "secondListener fired with hint =" + hint);
            });
            secondCallback[0] = secondListener.latch;
            activity.getWindow().getRootSurfaceControl()
                    .addOnBufferTransformHintChangedListener(secondListener);
            Log.d(TAG, "Requesting reverse landscape");
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);

            Assert.assertTrue(secondCallback[0].await(10, TimeUnit.SECONDS));
            Assert.assertNotEquals(transformHintResult[0], transformHintResult[1]);
        }
    }

    private static class GreenAnchorViewWithInsets extends View {
        SurfaceControl mSurfaceControl;
        final Surface mSurface;

        private final Rect mChildBoundingInsets;

        private final CountDownLatch mDrawCompleteLatch = new CountDownLatch(1);

        private boolean mChildScAttached;

        GreenAnchorViewWithInsets(Context c, Rect insets) {
            super(c, null, 0, 0);
            mSurfaceControl = new SurfaceControl.Builder()
                    .setName("SurfaceAnchorView")
                    .setBufferSize(100, 100)
                    .build();
            mSurface = new Surface(mSurfaceControl);
            Canvas canvas = mSurface.lockHardwareCanvas();
            canvas.drawColor(Color.GREEN);
            mSurface.unlockCanvasAndPost(canvas);
            mChildBoundingInsets = insets;

            getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    attachChildSc();
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attachChildSc();
        }

        private void attachChildSc() {
            if (mChildScAttached) {
                return;
            }
            // This should be called even if buildReparentTransaction fails the first time since
            // the second call will come from preDrawListener which is called after bounding insets
            // are updated in VRI.
            getRootSurfaceControl().setChildBoundingInsets(mChildBoundingInsets);

            SurfaceControl.Transaction t =
                    getRootSurfaceControl().buildReparentTransaction(mSurfaceControl);

            if (t == null) {
                // TODO (b/286406553) SurfaceControl was not yet setup. Wait until the draw request
                // to attach since the SurfaceControl will be created by that point. This can be
                // cleaned up when the bug is fixed.
                return;
            }

            t.setLayer(mSurfaceControl, 1).setVisibility(mSurfaceControl, true);
            t.addTransactionCommittedListener(Runnable::run, mDrawCompleteLatch::countDown);
            getRootSurfaceControl().applyTransactionOnDraw(t);
            mChildScAttached = true;
        }

        @Override
        protected void onDetachedFromWindow() {
            new SurfaceControl.Transaction().reparent(mSurfaceControl, null).apply();
            mSurfaceControl.release();
            mSurface.release();
            mChildScAttached = false;

            super.onDetachedFromWindow();
        }

        public void waitForDrawn() {
            try {
                assertTrue("Failed to wait for frame to draw",
                        mDrawCompleteLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                fail();
            }
        }
    }

    @Test
    public void testCropWithChildBoundingInsets() throws Throwable {
        try (ActivityScenario<TestActivity> scenario = launch(TestActivity.class)) {
            final GreenAnchorViewWithInsets[] view = new GreenAnchorViewWithInsets[1];
            Activity activity = awaitActivityStart(scenario, a -> {
                FrameLayout parentLayout = a.getParentLayout();
                GreenAnchorViewWithInsets anchorView = new GreenAnchorViewWithInsets(a,
                        new Rect(0, 10, 0, 0));
                parentLayout.addView(anchorView,
                        new FrameLayout.LayoutParams(100, 100, Gravity.LEFT | Gravity.TOP));

                view[0] = anchorView;
            });

            view[0].waitForDrawn();
            // Do not include system insets because the child SC is not laid out in the system
            // insets
            validateScreenshot(mName, activity,
                    new BitmapPixelChecker(Color.GREEN, new Rect(0, 10, 100, 100)),
                    9000 /* expectedMatchingPixels */, Insets.NONE);
        }
    }

    private static class ScvhSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
        CountDownLatch mReadyLatch = new CountDownLatch(1);
        SurfaceControlViewHost mScvh;
        final View mView;

        ScvhSurfaceView(Context context, View view) {
            super(context);
            getHolder().addCallback(this);
            mView = view;
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
            mView.getViewTreeObserver().addOnWindowAttachListener(
                    new ViewTreeObserver.OnWindowAttachListener() {
                        @Override
                        public void onWindowAttached() {
                            mReadyLatch.countDown();
                        }

                        @Override
                        public void onWindowDetached() {
                        }
                    });
            mScvh = new SurfaceControlViewHost(getContext(), getDisplay(), getHostToken());
            mScvh.setView(mView, getWidth(), getHeight());
            setChildSurfacePackage(mScvh.getSurfacePackage());
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {

        }

        public void waitForReady() throws InterruptedException {
            assertTrue("Failed to wait for ScvhSurfaceView to get added",
                    mReadyLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER)
    public void testGetHostToken() throws Throwable {
        try (ActivityScenario<TestActivity> scenario = launch(TestActivity.class)) {
            final ScvhSurfaceView[] scvhSurfaceView = new ScvhSurfaceView[1];
            final View[] view = new View[1];
            Activity activity = awaitActivityStart(scenario, a -> {
                view[0] = new View(a);
                FrameLayout parentLayout = a.getParentLayout();
                scvhSurfaceView[0] = new ScvhSurfaceView(a, view[0]);
                parentLayout.addView(scvhSurfaceView[0]);
            });

            final AttachedSurfaceControl attachedSurfaceControl =
                    scvhSurfaceView[0].getRootSurfaceControl();
            assertThat(attachedSurfaceControl.getInputTransferToken())
                    .isNotEqualTo(null);
        }
    }

    /**
     * Ensure the synced transaction is applied even if the view isn't visible and won't draw a
     * frame.
     */
    @Test
    public void testSyncTransactionViewNotVisible() throws Throwable {
        try (ActivityScenario<TestActivity> scenario = launch(TestActivity.class)) {
            final ScvhSurfaceView[] scvhSurfaceView = new ScvhSurfaceView[1];
            final View[] view = new View[1];
            Activity activity = awaitActivityStart(scenario, a -> {
                view[0] = new View(a);
                FrameLayout parentLayout = a.getParentLayout();
                scvhSurfaceView[0] = new ScvhSurfaceView(a, view[0]);
                parentLayout.addView(scvhSurfaceView[0],
                        new FrameLayout.LayoutParams(100, 100, Gravity.LEFT | Gravity.TOP));
            });

            scvhSurfaceView[0].waitForReady();

            CountDownLatch committedLatch = new CountDownLatch(1);
            activity.runOnUiThread(() -> {
                view[0].setVisibility(View.INVISIBLE);
                SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
                transaction.addTransactionCommittedListener(Runnable::run,
                        committedLatch::countDown);
                view[0].getRootSurfaceControl().applyTransactionOnDraw(transaction);
            });

            assertTrue("Failed to receive transaction committed callback for scvh with no view",
                    committedLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));
        }
    }

    /**
     * Ensure the synced transaction is applied even if there was nothing new to draw
     */
    @Test
    public void testSyncTransactionNothingToDraw() throws Throwable {
        try (ActivityScenario<TestActivity> scenario = launch(TestActivity.class)) {
            final View[] view = new View[1];
            Activity activity = awaitActivityStart(scenario, a -> {
                view[0] = new View(a);
                FrameLayout parentLayout = a.getParentLayout();
                parentLayout.addView(view[0],
                        new FrameLayout.LayoutParams(100, 100, Gravity.LEFT | Gravity.TOP));
            });

            CountDownLatch committedLatch = new CountDownLatch(1);
            activity.runOnUiThread(() -> {
                SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
                transaction.addTransactionCommittedListener(Runnable::run,
                        committedLatch::countDown);
                view[0].getRootSurfaceControl().applyTransactionOnDraw(transaction);
                view[0].requestLayout();
            });

            assertTrue("Failed to receive transaction committed callback for scvh with no view",
                    committedLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS));
        }
    }

    /**
     * Tests the jank classification API.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_JANK_API)
    public void testRegisterOnJankDataListener() throws Throwable {
        try (ActivityScenario<TestActivity> scenario = launch(TestActivity.class)) {
            TestActivity activity = awaitActivityStart(scenario);

            final AttachedSurfaceControl asc = activity.getWindow().getRootSurfaceControl();
            final CountDownLatch animEnd = new CountDownLatch(1);
            final CountDownLatch dataReceived = new CountDownLatch(1);
            final int[] jankCount = new int[] { 0 };

            SurfaceControl.OnJankDataListenerRegistration listenerRegistration =
                    asc.registerOnJankDataListener(activity.getMainExecutor(), data -> {
                        for (SurfaceControl.JankData frame : data) {
                            assertWithMessage("durations should be positive")
                                    .that(frame.getScheduledAppFrameTimeNanos())
                                    .isGreaterThan(0);
                            assertWithMessage("durations should be positive")
                                    .that(frame.getActualAppFrameTimeNanos())
                                    .isGreaterThan(0);
                            if ((frame.getJankType() & JANK_APPLICATION) != 0) {
                                assertWithMessage("missed frame timeline mismatch")
                                        .that(frame.getActualAppFrameTimeNanos())
                                        .isGreaterThan(frame.getScheduledAppFrameTimeNanos());
                                jankCount[0]++;
                            }
                        }
                        dataReceived.countDown();
                    });

            activity.runOnUiThread(() -> activity.startJankyAnimation(animEnd));

            assertWithMessage("Failed to wait for animation to end")
                    .that(animEnd.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS))
                    .isTrue();

            listenerRegistration.flush();

            assertWithMessage("Failed to receive any jank data callbacks")
                    .that(dataReceived.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS))
                    .isTrue();

            listenerRegistration.removeAfter(0);

            assertWithMessage("No frames were marked as janky")
                    .that(jankCount[0])
                    .isGreaterThan(0);
        }
    }

    private static <A extends Activity> A awaitActivityStart(ActivityScenario<A> scenario)
            throws InterruptedException {
        return awaitActivityStart(scenario, null);
    }

    private static <A extends Activity> A awaitActivityStart(ActivityScenario<A> scenario,
            ActivityScenario.ActivityAction<A> action) throws InterruptedException {
        CountDownLatch activityReady = new CountDownLatch(1);
        Activity[] activity = new Activity[1];
        scenario.onActivity(a -> {
            activity[0] = a;
            activityReady.countDown();
        });

        assertWithMessage("Failed to wait for activity to start")
                .that(activityReady.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS))
                .isTrue();

        waitForWindowOnTop(activity[0].getWindow());

        if (action != null) {
            scenario.onActivity(action);
        }

        return (A) activity[0];
    }
}
