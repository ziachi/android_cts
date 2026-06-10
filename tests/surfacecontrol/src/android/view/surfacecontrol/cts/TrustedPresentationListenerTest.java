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

package android.view.surfacecontrol.cts;

import static android.server.wm.ActivityManagerTestBase.createFullscreenActivityScenarioRule;
import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.graphics.Color;
import android.os.Binder;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.CtsWindowInfoUtils;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.window.TrustedPresentationThresholds;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.window.flags.Flags;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Presubmit
public class TrustedPresentationListenerTest {
    private static final String TAG = "TrustedPresentationListenerTest";
    private static final int STABILITY_REQUIREMENT_MS = 500;
    private static final long WAIT_TIME_MS = HW_TIMEOUT_MULTIPLIER * 4000L;

    private static final float FRACTION_VISIBLE = 0.1f;


    private TrustedPresentationThresholds mThresholds = new TrustedPresentationThresholds(
            1 /* minAlpha */, FRACTION_VISIBLE, STABILITY_REQUIREMENT_MS);

    @Rule
    public TestName mName = new TestName();

    @Rule
    public ActivityScenarioRule<TestActivity> mActivityRule = createFullscreenActivityScenarioRule(
            TestActivity.class);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private TestActivity mActivity;

    private SurfaceControlViewHost.SurfacePackage mSurfacePackage = null;

    @Before
    public void setup() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
        mDefaultListener = new Listener(1);
    }

    @After
    public void tearDown() {
        if (mSurfacePackage != null) {
            new SurfaceControl.Transaction()
                    .reparent(mSurfacePackage.getSurfaceControl(), null).apply();
            mSurfacePackage.release();
        }
    }

    private class Listener implements Consumer<Boolean> {
        CountDownLatch mReceivedResultsLatch;
        final List<Boolean> mResults = Collections.synchronizedList(new ArrayList<>());

        Listener(int numExpectedResults) {
            mReceivedResultsLatch = new CountDownLatch(numExpectedResults);
        }

        @Override
        public void accept(Boolean inTrustedPresentationState) {
            Log.d(TAG, "onTrustedPresentationChanged " + inTrustedPresentationState);
            mResults.add(inTrustedPresentationState);
            mReceivedResultsLatch.countDown();
        }

        void waitForResults() {
            if (!TrustedPresentationListenerTest.wait(mReceivedResultsLatch, WAIT_TIME_MS)) {
                try {
                    CtsWindowInfoUtils.dumpWindowsOnScreen(TAG, "test " + mName.getMethodName());
                } catch (InterruptedException e) {
                    Log.d(TAG, "Couldn't dump windows", e);
                }
                Assert.fail(
                        "Timed out waiting for results mReceivedResultsLatch.count="
                                + mReceivedResultsLatch.getCount()
                                + " mResults="
                                + mResults);
            }
        }
    }

    private Listener mDefaultListener;

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW)
    public void testAddTrustedPresentationListenerOnWindow() {
        WindowManager windowManager = mActivity.getSystemService(WindowManager.class);
        windowManager.registerTrustedPresentationListener(
                mActivity.getWindow().getDecorView().getWindowToken(), mThresholds, Runnable::run,
                mDefaultListener);
        assertResults(mDefaultListener, List.of(true));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW)
    public void testRemoveTrustedPresentationListenerOnWindow() throws InterruptedException {
        WindowManager windowManager = mActivity.getSystemService(WindowManager.class);
        windowManager.registerTrustedPresentationListener(
                mActivity.getWindow().getDecorView().getWindowToken(), mThresholds, Runnable::run,
                mDefaultListener);
        assertResults(mDefaultListener, List.of(true));
        // reset the latch
        mDefaultListener.mReceivedResultsLatch = new CountDownLatch(1);

        windowManager.unregisterTrustedPresentationListener(mDefaultListener);
        // Ensure we waited the full time and never received a notify on the result from the
        // callback.
        assertFalse(
                "Should never have received a callback",
                wait(mDefaultListener.mReceivedResultsLatch, WAIT_TIME_MS));
        // Ensure we waited the full time and never received a notify on the result from the
        // callback.
        // results shouldn't have changed.
        assertEquals(mDefaultListener.mResults, List.of(true));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW)
    public void testRemovingUnknownListenerIsANoop() {
        WindowManager windowManager = mActivity.getSystemService(WindowManager.class);
        assertNotNull(windowManager);
        windowManager.unregisterTrustedPresentationListener(mDefaultListener);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW)
    public void testAddDuplicateListenerUpdatesThresholds() throws InterruptedException {
        Binder nonExistentWindow = new Binder();
        WindowManager windowManager = mActivity.getSystemService(WindowManager.class);
        windowManager.registerTrustedPresentationListener(
                nonExistentWindow, mThresholds,
                Runnable::run, mDefaultListener);

        // Ensure we waited the full time and never received a notify on the result from the
        // callback.
        assertFalse(
                "Should never have received a callback",
                wait(mDefaultListener.mReceivedResultsLatch, WAIT_TIME_MS));

        windowManager.registerTrustedPresentationListener(
                mActivity.getWindow().getDecorView().getWindowToken(), mThresholds,
                Runnable::run, mDefaultListener);
        assertResults(mDefaultListener, List.of(true));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW)
    public void testAddDuplicateThresholds() {
        var listener1 = new Listener(1 /*numExpectedResults*/);
        WindowManager windowManager = mActivity.getSystemService(WindowManager.class);
        windowManager.registerTrustedPresentationListener(
                mActivity.getWindow().getDecorView().getWindowToken(),
                mThresholds,
                Runnable::run,
                listener1);

        var listener2 = new Listener(1 /*numExpectedResults*/);
        windowManager.registerTrustedPresentationListener(
                mActivity.getWindow().getDecorView().getWindowToken(),
                mThresholds,
                Runnable::run,
                listener2);

        assertResults(listener1, List.of(true));
        assertResults(listener2, List.of(true));
    }

    private void waitForViewAttach(View view) {
        final CountDownLatch viewAttached = new CountDownLatch(1);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                viewAttached.countDown();
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {

            }
        });
        try {
            viewAttached.await(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (!wait(viewAttached, 2000 /* waitTimeMs */)) {
            fail("Couldn't attach view=" + view);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW)
    public void testAddListenerToScvh() {
        WindowManager windowManager = mActivity.getSystemService(WindowManager.class);
        var hostSurfaceView = new SurfaceView(mActivity);
        hostSurfaceView.setZOrderOnTop(true);
        var embeddedView = new View(mActivity);
        embeddedView.setBackgroundColor(Color.GREEN);
        mActivityRule.getScenario().onActivity(activity -> {
            activity.setContentView(hostSurfaceView);
            var scvh = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(),
                    hostSurfaceView.getHostToken());
            mSurfacePackage = scvh.getSurfacePackage();
            scvh.setView(embeddedView, mActivity.getWindow().getDecorView().getWidth(),
                    mActivity.getWindow().getDecorView().getHeight());
            hostSurfaceView.setChildSurfacePackage(mSurfacePackage);
        });

        waitForViewAttach(embeddedView);
        windowManager.registerTrustedPresentationListener(
                embeddedView.getWindowToken(), mThresholds, Runnable::run, mDefaultListener);

        assertResults(mDefaultListener, List.of(true));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW)
    public void testTrustedPresentationThresholdGetters() {
        float alpha = 0.5f;
        float fractionRendered = 0.9f;
        int stabilityRequirementMs = 20;
        TrustedPresentationThresholds thresholds = new TrustedPresentationThresholds(alpha,
                fractionRendered, stabilityRequirementMs);
        Assert.assertEquals(alpha, thresholds.getMinAlpha());
        Assert.assertEquals(fractionRendered, thresholds.getMinFractionRendered());
        Assert.assertEquals(stabilityRequirementMs, thresholds.getStabilityRequirementMillis());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW)
    public void testEquals() {
        float alpha = 0.5f;
        float fractionRendered = 0.9f;
        int stabilityRequirementMs = 20;
        TrustedPresentationThresholds thresholdsA = new TrustedPresentationThresholds(alpha,
                fractionRendered, stabilityRequirementMs);
        TrustedPresentationThresholds thresholdsB =
                new TrustedPresentationThresholds(alpha, fractionRendered, stabilityRequirementMs);
        Assert.assertEquals(thresholdsA, thresholdsB);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW)
    public void testInvisibleWindowsDoesNotOcclude() {
        WindowManager windowManager = mActivity.getSystemService(WindowManager.class);
        var hostSurfaceView = new SurfaceView(mActivity);
        hostSurfaceView.setZOrderOnTop(true);
        var embeddedView = new View(mActivity);
        embeddedView.setBackgroundColor(Color.GREEN);
        mActivityRule
                .getScenario()
                .onActivity(
                        activity -> {
                            activity.setContentView(hostSurfaceView);
                            var scvh =
                                    new SurfaceControlViewHost(
                                            mActivity,
                                            mActivity.getDisplay(),
                                            hostSurfaceView.getHostToken());
                            mSurfacePackage = scvh.getSurfacePackage();
                            scvh.setView(
                                    embeddedView,
                                    mActivity.getWindow().getDecorView().getWidth(),
                                    mActivity.getWindow().getDecorView().getHeight());
                            hostSurfaceView.setChildSurfacePackage(mSurfacePackage);
                        });

        waitForViewAttach(embeddedView);
        Log.d(TAG, "Embedded window added");

        // at this point the main window should be occluded.

        // make the occluding surface invisible
        new SurfaceControl.Transaction().setAlpha(mSurfacePackage.getSurfaceControl(), 0f).apply();

        var listener = new Listener(1 /*numExpectedResults*/);
        windowManager.registerTrustedPresentationListener(
                mActivity.getWindow().getDecorView().getWindowToken(),
                mThresholds,
                Runnable::run,
                listener);
        assertResults(listener, List.of(true));
    }

    static boolean wait(CountDownLatch latch, long waitTimeMs) {
        while (true) {
            long now = SystemClock.uptimeMillis();
            try {
                return latch.await(waitTimeMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                long elapsedTime = SystemClock.uptimeMillis() - now;
                waitTimeMs = Math.max(0, waitTimeMs - elapsedTime);
            }
        }

    }

    private void assertResults(Listener listener, List<Boolean> expectedResults) {
        listener.waitForResults();
        assertEquals(expectedResults.toArray(), listener.mResults.toArray());
    }

    public static class TestActivity extends Activity {
    }
}
