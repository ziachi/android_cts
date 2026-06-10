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

package android.server.wm.jetpack.embedding;

import static android.server.wm.jetpack.embedding.MultiDisplayTestHelper.createLandscapeLargeScreenSimulatedDisplay;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRule;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplitAttributes;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.verifyStandaloneActivityStackIfNeeded;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumed;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumedAndFillsTask;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndGetTaskBounds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.WindowManagerState;
import android.server.wm.jetpack.utils.TestActivityWithId2;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.core.util.function.Consumer;
import androidx.window.extensions.embedding.ActivityEmbeddingComponent;
import androidx.window.extensions.embedding.EmbeddedActivityWindowInfo;
import androidx.window.extensions.embedding.SplitPairRule;

import com.android.compatibility.common.util.ApiTest;

import com.google.common.collect.Sets;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

/**
 * Tests for the ActivityEmbedding implementation about {@link EmbeddedActivityWindowInfo}.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:EmbeddedActivityWindowInfoTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class EmbeddedActivityWindowInfoTests extends ActivityEmbeddingTestBase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    /**
     * Tests the returned value from
     * {@link ActivityEmbeddingComponent#getEmbeddedActivityWindowInfo} is correct for non-embedded
     * activity.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.ActivityEmbeddingComponent#getEmbeddedActivityWindowInfo"
    })
    @Test
    public void testGetEmbeddedActivityWindowInfo_nonEmbeddedActivity() {
        final Activity nonEmbeddedActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        waitAndAssertResumed(nonEmbeddedActivity);

        mInstrumentation.runOnMainSync(() -> {
            final EmbeddedActivityWindowInfo info = mActivityEmbeddingComponent
                    .getEmbeddedActivityWindowInfo(nonEmbeddedActivity);
            assertEmbeddedActivityWindowInfo(info, nonEmbeddedActivity, false /* isEmbedded */);
        });
    }

    /**
     * Tests the returned value from
     * {@link ActivityEmbeddingComponent#getEmbeddedActivityWindowInfo} is correct for embedded
     * activities.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.ActivityEmbeddingComponent#getEmbeddedActivityWindowInfo"
    })
    @Test
    public void testGetEmbeddedActivityWindowInfo_embeddedActivity() {
        final SplitPairRule splitPairRule = createWildcardSplitPairRule();
        mActivityEmbeddingComponent.setEmbeddingRules(Sets.newHashSet(splitPairRule));

        // Launch two activities into a split
        final Activity primaryActivity =
                startFullScreenActivityNewTask(TestConfigChangeHandlingActivity.class);
        final Activity secondaryActivity =
                startActivityAndVerifySplitAttributes(
                        primaryActivity,
                        TestActivityWithId2.class,
                        splitPairRule,
                        "secondaryActivity" /* secondActivityId */,
                        mSplitInfoConsumer,
                        mActivityStackCallback);

        mInstrumentation.runOnMainSync(() -> {
            final EmbeddedActivityWindowInfo primaryInfo = mActivityEmbeddingComponent
                    .getEmbeddedActivityWindowInfo(primaryActivity);
            final EmbeddedActivityWindowInfo secondaryInfo = mActivityEmbeddingComponent
                    .getEmbeddedActivityWindowInfo(secondaryActivity);

            assertEmbeddedActivityWindowInfo(primaryInfo, primaryActivity, true /* isEmbedded */);
            assertEmbeddedActivityWindowInfo(
                    secondaryInfo, secondaryActivity, true /* isEmbedded */);
        });
    }

    /**
     * Tests the returned value from
     * {@link ActivityEmbeddingComponent#getEmbeddedActivityWindowInfo} is correct for embedded
     * activities.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.ActivityEmbeddingComponent#getEmbeddedActivityWindowInfo"
    })
    @Test
    public void testGetEmbeddedActivityWindowInfo_embeddedActivity_secondaryDisplay() {
        assumeTrue(supportsMultiDisplay());

        final WindowManagerState.DisplayContent secondaryDisplay =
                createLandscapeLargeScreenSimulatedDisplay(createManagedVirtualDisplaySession());
        final SplitPairRule splitPairRule = createWildcardSplitPairRule();
        mActivityEmbeddingComponent.setEmbeddingRules(Sets.newHashSet(splitPairRule));

        // Launch two activities into a split
        final Activity primaryActivity =
                startFullScreenActivityNewTask(
                        TestConfigChangeHandlingActivity.class,
                        null /* activityId */,
                        secondaryDisplay.mId);
        final Activity secondaryActivity =
                startActivityAndVerifySplitAttributes(
                        primaryActivity,
                        TestActivityWithId2.class,
                        splitPairRule,
                        "secondaryActivity" /* secondActivityId */,
                        mSplitInfoConsumer,
                        mActivityStackCallback);

        mInstrumentation.runOnMainSync(() -> {
            final EmbeddedActivityWindowInfo primaryInfo = mActivityEmbeddingComponent
                    .getEmbeddedActivityWindowInfo(primaryActivity);
            final EmbeddedActivityWindowInfo secondaryInfo = mActivityEmbeddingComponent
                    .getEmbeddedActivityWindowInfo(secondaryActivity);

            assertEmbeddedActivityWindowInfo(primaryInfo, primaryActivity, true /* isEmbedded */);
            assertEmbeddedActivityWindowInfo(
                    secondaryInfo, secondaryActivity, true /* isEmbedded */);
        });
    }

    /**
     * Tests that the
     * {@link ActivityEmbeddingComponent#setEmbeddedActivityWindowInfoCallback} callback will be
     * triggered whenever the {@link EmbeddedActivityWindowInfo} is changed.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.ActivityEmbeddingComponent"
                    + "#setEmbeddedActivityWindowInfoCallback",
            "androidx.window.extensions.ActivityEmbeddingComponent"
                    + "#clearEmbeddedActivityWindowInfoCallback"
    })
    @Test
    public void testEmbeddedActivityWindowInfoCallback() {
        final TestWindowInfoChangeListener listener = new TestWindowInfoChangeListener();
        mActivityEmbeddingComponent.setEmbeddedActivityWindowInfoCallback(Runnable::run, listener);
        final SplitPairRule splitPairRule = createWildcardSplitPairRule();
        mActivityEmbeddingComponent.setEmbeddingRules(Sets.newHashSet(splitPairRule));

        // Report info when activity is launched.
        final Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        waitAndAssertResumed(primaryActivity);

        mInstrumentation.runOnMainSync(
                () -> {
                    final EmbeddedActivityWindowInfo nonEmbeddedInfo =
                            listener.getLastReportedInfo(primaryActivity);
                    assertEmbeddedActivityWindowInfo(
                            nonEmbeddedInfo, primaryActivity, false /* isEmbedded */);
                });

        // Report info when activity enters split.
        final Activity secondaryActivity =
                startActivityAndVerifySplitAttributes(
                        primaryActivity,
                        TestActivityWithId2.class,
                        splitPairRule,
                        "secondaryActivity" /* secondActivityId */,
                        mSplitInfoConsumer,
                        mActivityStackCallback);

        mInstrumentation.runOnMainSync(() -> {
            final EmbeddedActivityWindowInfo primaryInfo = listener
                    .getLastReportedInfo(primaryActivity);
            final EmbeddedActivityWindowInfo secondaryInfo = listener
                    .getLastReportedInfo(secondaryActivity);

            assertEmbeddedActivityWindowInfo(primaryInfo, primaryActivity, true /* isEmbedded */);
            assertEmbeddedActivityWindowInfo(
                    secondaryInfo, secondaryActivity, true /* isEmbedded */);
        });

        // Report info when the primary activity exit split. Not update anymore for the secondary
        // activity that has been finished.
        final EmbeddedActivityWindowInfo lastSecondaryInfo = listener.getLastReportedInfo(
                secondaryActivity);
        secondaryActivity.finish();

        waitAndAssertResumedAndFillsTask(primaryActivity);
        verifyStandaloneActivityStackIfNeeded(mActivityStackCallback, primaryActivity);

        mInstrumentation.runOnMainSync(() -> {
            final EmbeddedActivityWindowInfo primaryInfo2 = listener
                    .getLastReportedInfo(primaryActivity);
            final EmbeddedActivityWindowInfo secondaryInfo2 = listener
                    .getLastReportedInfo(secondaryActivity);
            assertEmbeddedActivityWindowInfo(primaryInfo2, primaryActivity, false /* isEmbedded */);
            assertEquals(lastSecondaryInfo, secondaryInfo2);
        });

        // No more update after #clearEmbeddedActivityWindowInfoCallback.
        final EmbeddedActivityWindowInfo lastPrimaryInfo =
                listener.getLastReportedInfo(primaryActivity);
        mActivityEmbeddingComponent.clearEmbeddedActivityWindowInfoCallback();
        // The last split state is back to fullscreen. Clear queue to wait for the new split update.
        mSplitInfoConsumer.clearQueue();
        mActivityStackCallback.clearQueue();
        final Activity secondaryActivity2 =
                startActivityAndVerifySplitAttributes(
                        primaryActivity,
                        TestActivityWithId2.class,
                        splitPairRule,
                        "secondaryActivity" /* secondActivityId */,
                        mSplitInfoConsumer,
                        mActivityStackCallback);

        mInstrumentation.runOnMainSync(() -> {
            assertEquals(lastPrimaryInfo, listener.getLastReportedInfo(primaryActivity));
            assertNull(listener.getLastReportedInfo(secondaryActivity2));
        });
    }

    /**
     * Tests that the
     * {@link ActivityEmbeddingComponent#setEmbeddedActivityWindowInfoCallback} callback will be
     * triggered whenever the {@link EmbeddedActivityWindowInfo} is changed.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.ActivityEmbeddingComponent"
                    + "#setEmbeddedActivityWindowInfoCallback",
            "androidx.window.extensions.ActivityEmbeddingComponent"
                    + "#clearEmbeddedActivityWindowInfoCallback"
    })
    @Test
    public void testEmbeddedActivityWindowInfoCallbackOnSecondaryDisplay() {
        assumeTrue(supportsMultiDisplay());

        final WindowManagerState.DisplayContent secondaryDisplay =
                createLandscapeLargeScreenSimulatedDisplay(createManagedVirtualDisplaySession());
        final TestWindowInfoChangeListener listener = new TestWindowInfoChangeListener();
        mActivityEmbeddingComponent.setEmbeddedActivityWindowInfoCallback(Runnable::run, listener);
        final SplitPairRule splitPairRule = createWildcardSplitPairRule();
        mActivityEmbeddingComponent.setEmbeddingRules(Sets.newHashSet(splitPairRule));

        // Report info when activity is launched.
        final Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class, null /* activityId */,
                secondaryDisplay.mId);
        waitAndAssertResumed(primaryActivity);

        mInstrumentation.runOnMainSync(
                () -> {
                    final EmbeddedActivityWindowInfo nonEmbeddedInfo =
                            listener.getLastReportedInfo(primaryActivity);
                    assertEmbeddedActivityWindowInfo(
                            nonEmbeddedInfo, primaryActivity, false /* isEmbedded */);
                });

        // Report info when activity enters split.
        final Activity secondaryActivity =
                startActivityAndVerifySplitAttributes(
                        primaryActivity,
                        TestActivityWithId2.class,
                        splitPairRule,
                        "secondaryActivity" /* secondActivityId */,
                        mSplitInfoConsumer,
                        mActivityStackCallback);

        mInstrumentation.runOnMainSync(() -> {
            final EmbeddedActivityWindowInfo primaryInfo = listener
                    .getLastReportedInfo(primaryActivity);
            final EmbeddedActivityWindowInfo secondaryInfo = listener
                    .getLastReportedInfo(secondaryActivity);

            assertEmbeddedActivityWindowInfo(primaryInfo, primaryActivity, true /* isEmbedded */);
            assertEmbeddedActivityWindowInfo(
                    secondaryInfo, secondaryActivity, true /* isEmbedded */);
        });
    }

    private void assertEmbeddedActivityWindowInfo(@Nullable EmbeddedActivityWindowInfo info,
            @NonNull Activity activity, boolean isEmbedded) {
        assertNotNull(info);

        final Rect taskBounds = waitAndGetTaskBounds(activity, true /* shouldWaitForResume */);
        final Rect activityStackBounds = getActivityBounds(activity);

        final String errorMessage = "Expected value: \nisEmbedded=" + isEmbedded
                + "\nactivity=" + activity
                + "\nactivityStackBounds=" + activityStackBounds
                + "\ntaskBounds=" + taskBounds
                + "\nActual value: " + info;

        assertEquals(errorMessage, isEmbedded, info.isEmbedded());
        assertEquals(errorMessage, activity, info.getActivity());
        assertEquals(errorMessage, activityStackBounds, info.getActivityStackBounds());
        assertEquals(errorMessage, taskBounds, info.getTaskBounds());
    }

    private static class TestWindowInfoChangeListener implements
            Consumer<EmbeddedActivityWindowInfo> {

        private final Map<Activity, EmbeddedActivityWindowInfo> mLastReportedInfos =
                new ArrayMap<>();

        @Override
        public void accept(EmbeddedActivityWindowInfo embeddedActivityWindowInfo) {
            final Activity activity = embeddedActivityWindowInfo.getActivity();
            mLastReportedInfos.put(activity, embeddedActivityWindowInfo);
        }

        @Nullable
        EmbeddedActivityWindowInfo getLastReportedInfo(@NonNull Activity activity) {
            return mLastReportedInfos.get(activity);
        }
    }
}
