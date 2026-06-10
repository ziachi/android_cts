/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.view.inspector.cts;

import static org.junit.Assert.assertEquals;

import android.Manifest;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.View;
import android.view.WindowManager;
import android.view.cts.CtsActivity;
import android.view.inspector.WindowInspector;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Tests for {@link WindowInspector}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class WindowInspectorTest {

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityScenarioRule<CtsActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(CtsActivity.class);

    private final List<Consumer<List<View>>> mListeners = new ArrayList<>();

    @After
    public void tearDown() {
        for (Consumer<List<View>> listener : mListeners) {
            WindowInspector.removeGlobalWindowViewsListener(listener);
        }
    }

    @Test
    public void testGetGlobalWindowViews() {
        mActivityScenarioRule
                .getScenario()
                .onActivity(
                        (activity) -> {
                            List<View> views = WindowInspector.getGlobalWindowViews();
                            assertEquals(
                                    "Only the activity window view is present", 1, views.size());

                            View view = views.getFirst();
                            assertEquals(
                                    "The activity window view is the decor view",
                                    view,
                                    activity.getWindow().getDecorView());
                        });
    }

    /**
     * Tests that when a listener is added the current value of {@link
     * WindowInspector#getGlobalWindowViews()} is reported.
     *
     * @throws InterruptedException when interrupted.
     */
    @Test
    @RequiresFlagsEnabled(android.view.flags.Flags.FLAG_ROOT_VIEW_CHANGED_LISTENER)
    public void testAddRootViewListener_returnsRootView() throws InterruptedException {
        final RootViewCollector collector = new RootViewCollector(1);
        addListenerToWindowInspector(Runnable::run, collector);

        final List<View> expected = WindowInspector.getGlobalWindowViews();
        collector.waitForElements();

        final List<List<View>> elements = collector.getElements();

        assertEquals(1, elements.size());
        assertEquals(expected, elements.getFirst());
    }

    /**
     * Tests that when a listener is added a second time then the operation is ignored.
     *
     * @throws InterruptedException when interrupted.
     */
    @Test
    @RequiresFlagsEnabled(android.view.flags.Flags.FLAG_ROOT_VIEW_CHANGED_LISTENER)
    public void testAddRootViewListener_doesNotDoubleRegisterListener()
            throws InterruptedException {
        final RootViewCollector collector = new RootViewCollector(1);
        addListenerToWindowInspector(Runnable::run, collector);
        addListenerToWindowInspector(Runnable::run, collector);

        final List<View> expected = WindowInspector.getGlobalWindowViews();
        collector.waitForElements();

        final List<List<View>> elements = collector.getElements();

        assertEquals(1, elements.size());
        assertEquals(expected, elements.getFirst());
    }

    /**
     * Tests that when a {@link View} is added through {@link WindowManager} the new view is
     * reported to a listener.
     *
     * @throws InterruptedException when interrupted.
     */
    @Test
    @RequiresFlagsEnabled(android.view.flags.Flags.FLAG_ROOT_VIEW_CHANGED_LISTENER)
    public void testAddedViewIsReported() throws InterruptedException {
        final RootViewCollector collector = new RootViewCollector(2);
        addListenerToWindowInspector(Runnable::run, collector);
        final List<View> expected = new ArrayList<>();
        final List<View> decorView = new ArrayList<>();

        mActivityScenarioRule
                .getScenario()
                .onActivity(
                        (activity) -> {
                            expected.add(activity.getWindow().getDecorView());
                            decorView.add(activity.getWindow().getDecorView());

                            WindowManager windowManager = activity.getWindowManager();
                            View view = new View(activity.getApplicationContext());
                            WindowManager.LayoutParams layoutParams =
                                    new WindowManager.LayoutParams();
                            windowManager.addView(view, layoutParams);

                            expected.add(view);
                        });

        collector.waitForElements();

        final List<List<View>> elements = collector.getElements();

        assertEquals(2, elements.size());
        assertEquals(decorView, elements.getFirst());
        assertEquals(expected, elements.get(1));
    }

    /**
     * Tests that when a {@link View} is removed through {@link WindowManager} then the listener is
     * updated with the {@link View} removed.
     *
     * @throws InterruptedException when interrupted.
     */
    @Test
    @RequiresFlagsEnabled(android.view.flags.Flags.FLAG_ROOT_VIEW_CHANGED_LISTENER)
    public void testRemovedViewIsReported() throws InterruptedException {
        final RootViewCollector collector = new RootViewCollector(3);
        addListenerToWindowInspector(Runnable::run, collector);
        final List<View> allViews = new ArrayList<>();
        final List<View> decorViewList = new ArrayList<>();

        mActivityScenarioRule
                .getScenario()
                .onActivity(
                        (activity) -> {
                            allViews.add(activity.getWindow().getDecorView());
                            decorViewList.add(activity.getWindow().getDecorView());

                            WindowManager windowManager = activity.getWindowManager();
                            View view = new View(activity.getApplicationContext());
                            WindowManager.LayoutParams layoutParams =
                                    new WindowManager.LayoutParams();
                            windowManager.addView(view, layoutParams);

                            allViews.add(view);
                        });
        mActivityScenarioRule
                .getScenario()
                .onActivity(
                        (activity) -> {
                            List<View> rootViews = WindowInspector.getGlobalWindowViews();

                            List<View> viewsToRemove = new ArrayList<>();
                            View activityView = activity.getWindow().getDecorView();
                            for (View v : rootViews) {
                                if (!activityView.equals(v)) {
                                    viewsToRemove.add(v);
                                }
                            }

                            for (View v : viewsToRemove) {
                                activity.getWindowManager().removeView(v);
                            }
                        });

        collector.waitForElements();

        final List<List<View>> elements = collector.getElements();

        assertEquals(3, elements.size());
        assertEquals(decorViewList, elements.getFirst());
        assertEquals(allViews, elements.get(1));
        assertEquals(decorViewList, elements.getLast());
    }

    private void addListenerToWindowInspector(
            @NonNull Executor executor, @NonNull Consumer<List<View>> consumer) {
        WindowInspector.addGlobalWindowViewsListener(executor, consumer);
        mListeners.add(consumer);
    }

    private static final class RootViewCollector implements Consumer<List<View>> {
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private final List<List<View>> mElements = new ArrayList<>();

        private final CountDownLatch mCountDownLatch;

        RootViewCollector(int expectedValueCount) {
            mCountDownLatch = new CountDownLatch(expectedValueCount);
        }

        @Override
        public void accept(@NonNull List<View> views) {
            synchronized (mLock) {
                mElements.add(Objects.requireNonNull(views));
                mCountDownLatch.countDown();
            }
        }

        public boolean waitForElements() throws InterruptedException {
            return mCountDownLatch.await(3, TimeUnit.SECONDS);
        }

        public List<List<View>> getElements() {
            synchronized (mLock) {
                return new ArrayList<>(mElements);
            }
        }
    }
}
