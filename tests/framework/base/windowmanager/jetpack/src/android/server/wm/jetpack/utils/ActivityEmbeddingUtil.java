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

package android.server.wm.jetpack.utils;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.assumeExtensionSupportedDevice;
import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.getExtensionWindowLayoutInfo;
import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.getWindowExtensions;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.getActivityBounds;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.getResumedActivityById;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.isActivityResumed;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.startActivityFromActivity;
import static android.util.TypedValue.COMPLEX_UNIT_DIP;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static java.util.Objects.requireNonNull;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.server.wm.WindowManagerStateHelper;
import android.server.wm.jetpack.extensions.util.TestValueCountConsumer;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.window.extensions.core.util.function.Predicate;
import androidx.window.extensions.embedding.ActivityEmbeddingComponent;
import androidx.window.extensions.embedding.ActivityStack;
import androidx.window.extensions.embedding.DividerAttributes;
import androidx.window.extensions.embedding.SplitAttributes;
import androidx.window.extensions.embedding.SplitAttributes.LayoutDirection;
import androidx.window.extensions.embedding.SplitAttributes.SplitType;
import androidx.window.extensions.embedding.SplitInfo;
import androidx.window.extensions.embedding.SplitPairRule;
import androidx.window.extensions.embedding.SplitRule;
import androidx.window.extensions.layout.FoldingFeature;
import androidx.window.extensions.layout.WindowLayoutInfo;

import com.android.compatibility.common.util.PollingCheck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Utility class for activity embedding tests. */
public class ActivityEmbeddingUtil {

    public static final String TAG = "ActivityEmbeddingTests";
    public static final long WAIT_FOR_LIFECYCLE_TIMEOUT_MS = 3000L * HW_TIMEOUT_MULTIPLIER;
    public static final long WAIT_FOR_COLD_LAUNCH_TIMEOUT_MS = 5000L * HW_TIMEOUT_MULTIPLIER;
    public static final SplitAttributes DEFAULT_SPLIT_ATTRS = new SplitAttributes.Builder().build();

    public static final SplitAttributes EXPAND_SPLIT_ATTRS = new SplitAttributes.Builder()
            .setSplitType(new SplitType.ExpandContainersSplitType()).build();

    public static final SplitAttributes HINGE_SPLIT_ATTRS = new SplitAttributes.Builder()
            .setSplitType(new SplitType.HingeSplitType(SplitType.RatioSplitType.splitEqually()))
            .build();

    public static final String EMBEDDED_ACTIVITY_ID = "embedded_activity_id";

    private static final long WAIT_PERIOD = 500;

    @NonNull
    public static SplitPairRule createWildcardSplitPairRule(boolean shouldClearTop) {
        // Build the split pair rule
        return createSplitPairRuleBuilder(
                // Any activity be split with any activity
                activityActivityPair -> true,
                // Any activity can launch any split intent
                activityIntentPair -> true,
                // Allow any parent bounds to show the split containers side by side
                windowMetrics -> true)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS)
                .setShouldClearTop(shouldClearTop)
                .build();
    }

    @NonNull
    public static SplitPairRule createWildcardSplitPairRuleWithPrimaryActivityClass(
            Class<? extends Activity> activityClass, boolean shouldClearTop) {
        return createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(activityClass,
                shouldClearTop).build();
    }

    @NonNull
    public static SplitPairRule.Builder createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(
            Class<? extends Activity> activityClass, boolean shouldClearTop) {
        // Build the split pair rule
        return createSplitPairRuleBuilder(
                // The specified activity be split any activity
                activityActivityPair -> activityActivityPair.first.getClass().equals(activityClass),
                // The specified activity can launch any split intent
                activityIntentPair -> activityIntentPair.first.getClass().equals(activityClass),
                // Allow any parent bounds to show the split containers side by side
                windowMetrics -> true)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS)
                .setShouldClearTop(shouldClearTop);
    }

    @NonNull
    public static SplitPairRule createWildcardSplitPairRule() {
        return createWildcardSplitPairRule(false /* shouldClearTop */);
    }

    /**
     * A wrapper to create {@link SplitPairRule} builder with extensions core functional interface
     * to prevent ambiguous issue when using lambda expressions.
     */
    @NonNull
    public static SplitPairRule.Builder createSplitPairRuleBuilder(
            @NonNull Predicate<Pair<Activity, Activity>> activitiesPairPredicate,
            @NonNull Predicate<Pair<Activity, Intent>> activityIntentPairPredicate,
            @NonNull Predicate<WindowMetrics> windowMetricsPredicate) {
        return new SplitPairRule.Builder(activitiesPairPredicate, activityIntentPairPredicate,
                windowMetricsPredicate);
    }

    public static TestActivity startActivityAndVerifyNotSplit(
            @NonNull Activity activityLaunchingFrom) {
        final String secondActivityId = "secondActivityId";
        // Launch second activity
        startActivityFromActivity(activityLaunchingFrom, TestActivityWithId.class,
                secondActivityId);
        // Verify both activities are in the correct lifecycle state
        waitAndAssertResumed(secondActivityId);
        assertFalse(isActivityResumed(activityLaunchingFrom));
        TestActivity secondActivity = getResumedActivityById(secondActivityId);
        // Verify the second activity is not split with the first
        waitAndAssertResumedAndFillsTask(secondActivity);
        return secondActivity;
    }

    /**
     * Starts an {@link Activity} and verifies the split states.
     *
     * @param activityLaunchingFrom the primary {@link Activity} to launch the secondary
     * @param secondActivityClass the class of the secondary {@link Activity}
     * @param secondaryActivityId the {@code String} ID of the secondary {@link Activity}
     * @param expectedCallbackCount the expected count from {@code splitInfoConsumer}
     * @param splitInfoConsumer the {@link SplitInfo} callback
     * @param activityStackCallback the {@link ActivityStack} callback. It could be {@code null} if
     *     {@link ActivityEmbeddingComponent#registerActivityStackCallback} is not supported or we
     *     don't want to verify {@link ActivityStack}.
     * @return the launched secondary {@link Activity}
     */
    @NonNull
    public static Activity startActivityAndVerifySplitAttributes(
            @NonNull Activity activityLaunchingFrom,
            @NonNull Activity expectedPrimaryActivity,
            @NonNull Class<? extends Activity> secondActivityClass,
            @NonNull SplitAttributes splitAttributes,
            @NonNull String secondaryActivityId,
            int expectedCallbackCount,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer,
            @Nullable TestValueCountConsumer<List<ActivityStack>> activityStackCallback) {
        // Set the expected callback count
        splitInfoConsumer.setCount(expectedCallbackCount);

        // Start second activity
        startActivityFromActivity(activityLaunchingFrom, secondActivityClass, secondaryActivityId);

        // Wait for secondary activity to be resumed and verify that the newly sent split info
        // contains the secondary activity.
        waitAndAssertResumed(secondaryActivityId);
        final Activity secondaryActivity = getResumedActivityById(secondaryActivityId);

        assertSplitPairIsCorrect(
                expectedPrimaryActivity,
                secondaryActivity,
                splitAttributes,
                splitInfoConsumer,
                activityStackCallback);

        // Return second activity for easy access in calling method
        return secondaryActivity;
    }

    /**
     * Assert the split pair is correct.
     *
     * @param activityStackCallback if not {@code null}, check {@link ActivityStack activityStacks}
     *     is expected. Otherwise, don't verify {@code activityStacks}.
     */
    public static void assertSplitPairIsCorrect(
            @NonNull Activity expectedPrimaryActivity,
            @NonNull Activity secondaryActivity,
            @NonNull SplitAttributes splitAttributes,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer,
            @Nullable TestValueCountConsumer<List<ActivityStack>> activityStackCallback) {
        // A split info callback should occur after the new activity is launched because the split
        // states have changed.
        List<SplitInfo> activeSplitStates;
        try {
            activeSplitStates = splitInfoConsumer.waitAndGet();
        } catch (InterruptedException e) {
            throw new AssertionError("startActivityAndVerifySplitAttributes()", e);
        }
        assertNotNull("Active Split States cannot be null.", activeSplitStates);

        assertSplitInfoTopSplitIsCorrect(activeSplitStates, expectedPrimaryActivity,
                secondaryActivity, splitAttributes);
        assertValidSplit(expectedPrimaryActivity, secondaryActivity, splitAttributes);
        verifyActivityStacksIfNeeded(
                activityStackCallback, expectedPrimaryActivity, secondaryActivity);
    }

    private static void verifyActivityStacksIfNeeded(
            @Nullable TestValueCountConsumer<List<ActivityStack>> activityStackCallback,
            @NonNull Activity primaryActivity,
            @Nullable Activity secondaryActivity) {
        final List<ActivityStack> activityStacks = getLastActivityStacks(activityStackCallback);
        if (activityStacks == null) {
            return;
        }

        assertActivityStacksIsCorrect(activityStacks, primaryActivity, secondaryActivity);
    }

    @Nullable
    private static List<ActivityStack> getLastActivityStacks(
            @Nullable TestValueCountConsumer<List<ActivityStack>> activityStackCallback) {
        if (activityStackCallback == null) {
            return null;
        }
        try {
            return activityStackCallback.waitAndGet();
        } catch (InterruptedException e) {
            throw new AssertionError("getLastActivityStacks()", e);
        }
    }

    /**
     * Starts an {@link Activity} from {@code activityLaunchingFrom} and verifies there's no {@link
     * SplitInfo} callback.
     *
     * @param activityLaunchingFrom the {@link Activity} to launch a new {@link Activity}
     * @param secondActivityClass the secondary {@link Activity} class
     * @param secondaryActivityId the ID of the secondary {@link Activity}
     * @param splitInfoConsumer the {@link SplitInfo} callback
     * @throws InterruptedException if {@link TestValueCountConsumer#waitAndGet()} throws the
     *     exception
     */
    public static void startActivityAndVerifyNoCallback(
            @NonNull Activity activityLaunchingFrom,
            @NonNull Class<? extends Activity> secondActivityClass,
            @NonNull String secondaryActivityId,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer)
            throws InterruptedException {
        // We expect the actual count to be 0. Set to 1 to trigger the timeout and verify no calls.
        splitInfoConsumer.setCount(1);

        // Start second activity
        startActivityFromActivity(activityLaunchingFrom, secondActivityClass, secondaryActivityId);

        // A split info callback should occur after the new activity is launched because the split
        // states have changed.
        List<SplitInfo> activeSplitStates = splitInfoConsumer.waitAndGet();
        assertNull("Received SplitInfo value but did not expect none.", activeSplitStates);
    }

    /**
     * Starts an {@link Activity} and verifies the split states.
     *
     * @param activityLaunchingFrom the primary {@link Activity} to launch the secondary
     * @param secondActivityClass the class of the secondary {@link Activity}
     * @param secondaryActivityId the {@code String} ID of the secondary {@link Activity}
     * @param expectedCallbackCount the expected count from {@code splitInfoConsumer}
     * @param splitInfoConsumer the {@link SplitInfo} callback
     * @param activityStackCallback the {@link ActivityStack} callback. It could be {@code null} if
     *     we don't want to verify {@link ActivityStack}.
     * @return the launched secondary {@link Activity}
     */
    @NonNull
    public static Activity startActivityAndVerifySplitAttributes(
            @NonNull Activity activityLaunchingFrom,
            @NonNull Activity expectedPrimaryActivity,
            @NonNull Class<? extends Activity> secondActivityClass,
            @NonNull SplitRule splitRule,
            @NonNull String secondaryActivityId,
            int expectedCallbackCount,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer,
            @Nullable TestValueCountConsumer<List<ActivityStack>> activityStackCallback) {
        return startActivityAndVerifySplitAttributes(
                activityLaunchingFrom,
                expectedPrimaryActivity,
                secondActivityClass,
                splitRule.getDefaultSplitAttributes(),
                secondaryActivityId,
                expectedCallbackCount,
                splitInfoConsumer,
                activityStackCallback);
    }

    /**
     * Starts an {@link Activity} and verifies the split states.
     *
     * @param primaryActivity the primary {@link Activity} to launch the secondary
     * @param secondActivityClass the class of the secondary {@link Activity}
     * @param splitPairRule the rule that matches the split pair
     * @param secondActivityId the {@code String} ID of the secondary {@link Activity}
     * @param splitInfoConsumer the {@link SplitInfo} callback
     * @param activityStackCallback the {@link ActivityStack} callback. It could be {@code null} if
     *     {@link ActivityEmbeddingComponent#registerActivityStackCallback} is not supported or we
     *     don't want to verify {@link ActivityStack}.
     * @return the launched secondary {@link Activity}
     */
    @NonNull
    public static Activity startActivityAndVerifySplitAttributes(
            @NonNull Activity primaryActivity,
            @NonNull Class<? extends Activity> secondActivityClass,
            @NonNull SplitPairRule splitPairRule,
            @NonNull String secondActivityId,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer,
            @Nullable TestValueCountConsumer<List<ActivityStack>> activityStackCallback) {
        return startActivityAndVerifySplitAttributes(
                primaryActivity,
                primaryActivity,
                secondActivityClass,
                splitPairRule,
                secondActivityId,
                1 /* expectedCallbackCount */,
                splitInfoConsumer,
                activityStackCallback);
    }

    /**
     * Attempts to start an activity from a different UID into a split, verifies that a new split is
     * active.
     */
    public static void startActivityCrossUidInSplit(
            @NonNull Activity primaryActivity,
            @NonNull ComponentName secondActivityComponent,
            @NonNull SplitPairRule splitPairRule,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer,
            @Nullable TestValueCountConsumer<List<ActivityStack>> activityStackCallback,
            @NonNull String secondActivityId,
            boolean verifySplitState) {
        startActivityFromActivity(primaryActivity, secondActivityComponent, secondActivityId,
                Bundle.EMPTY);
        if (!verifySplitState) {
            return;
        }

        // Get updated split info
        splitInfoConsumer.setCount(1);
        List<SplitInfo> activeSplitStates;
        try {
            activeSplitStates = splitInfoConsumer.waitAndGet();
        } catch (InterruptedException e) {
            throw new AssertionError("startActivityCrossUidInSplit()", e);
        }
        assertNotNull(activeSplitStates);
        assertFalse(activeSplitStates.isEmpty());
        // Verify that the primary activity is on top of the primary stack
        SplitInfo topSplit = activeSplitStates.get(activeSplitStates.size() - 1);
        List<Activity> primaryStackActivities = topSplit.getPrimaryActivityStack()
                .getActivities();
        assertEquals(primaryActivity,
                primaryStackActivities.get(primaryStackActivities.size() - 1));
        // Verify that the secondary stack is reported as empty to developers
        assertTrue(topSplit.getSecondaryActivityStack().getActivities().isEmpty());

        assertValidSplit(primaryActivity, null /* secondaryActivity */,
                splitPairRule);

        verifyActivityStacksIfNeeded(
                activityStackCallback, primaryActivity, null /* secondaryActivity */);
    }

    /**
     * Attempts to start an activity from a different UID into a split, verifies that activity
     * did not start on splitContainer successfully and no new split is active.
     */
    public static void startActivityCrossUidInSplit_expectFail(@NonNull Activity primaryActivity,
            @NonNull ComponentName secondActivityComponent,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer) {
        startActivityFromActivity(primaryActivity, secondActivityComponent, "secondActivityId",
                    Bundle.EMPTY);

        // No split should be active, primary activity should be covered by the new one.
        assertNoSplit(primaryActivity, splitInfoConsumer);
    }

    /**
     * Asserts that there is no split with the provided primary activity.
     */
    public static void assertNoSplit(@NonNull Activity primaryActivity,
            @NonNull TestValueCountConsumer<List<SplitInfo>> splitInfoConsumer) {
        waitForVisible(primaryActivity, false /* visible */);
        List<SplitInfo> activeSplitStates = splitInfoConsumer.getLastReportedValue();
        assertTrue(activeSplitStates == null || activeSplitStates.isEmpty());
    }

    @Nullable
    public static Activity getSecondActivity(@Nullable List<SplitInfo> activeSplitStates,
            @NonNull Activity primaryActivity, @NonNull String secondaryClassId) {
        if (activeSplitStates == null) {
            Log.d(TAG, "Null split states");
            return null;
        }
        Log.d(TAG, "Active split states: " + activeSplitStates);
        for (SplitInfo splitInfo : activeSplitStates) {
            // Find the split info whose top activity in the primary container is the primary
            // activity we are looking for
            Activity primaryContainerTopActivity = getPrimaryStackTopActivity(splitInfo);
            if (primaryActivity.equals(primaryContainerTopActivity)) {
                Activity secondActivity = getSecondaryStackTopActivity(splitInfo);
                // See if this activity is the secondary activity we expect
                if (secondActivity != null && secondActivity instanceof TestActivityWithId
                        && secondaryClassId.equals(((TestActivityWithId) secondActivity).getId())) {
                    return secondActivity;
                }
            }
        }
        Log.d(TAG, "Second activity was not found: " + secondaryClassId);
        return null;
    }

    /**
     * Waits for and verifies a valid split. Can accept a null secondary activity if it belongs to
     * a different process, in which case it will only verify the primary one.
     */
    public static void assertValidSplit(@NonNull Activity primaryActivity,
            @Nullable Activity secondaryActivity, @NonNull SplitRule splitRule) {
        assertValidSplit(primaryActivity, secondaryActivity, splitRule.getDefaultSplitAttributes());
    }

    /**
     * Similar to {@link #assertValidSplit(Activity, Activity, SplitRule)}, but verifies
     * {@link SplitAttributes} instead of {@link SplitRule#getDefaultSplitAttributes}.
     */
    public static void assertValidSplit(@NonNull Activity primaryActivity,
            @Nullable Activity secondaryActivity, @NonNull SplitAttributes splitAttributes) {
        final boolean shouldExpandContainers = splitAttributes.getSplitType()
                instanceof SplitType.ExpandContainersSplitType;
        final List<Activity> resumedActivities = new ArrayList<>(2);
        if (secondaryActivity == null) {
            resumedActivities.add(primaryActivity);
        } else if (shouldExpandContainers) {
            resumedActivities.add(secondaryActivity);
        } else {
            resumedActivities.add(primaryActivity);
            resumedActivities.add(secondaryActivity);
        }
        waitAndAssertResumed(resumedActivities);

        final Pair<Rect, Rect> expectedBoundsPair = getExpectedBoundsPair(
                shouldExpandContainers ? requireNonNull(secondaryActivity) : primaryActivity,
                splitAttributes);

        final ActivityEmbeddingComponent activityEmbeddingComponent = getWindowExtensions()
                .getActivityEmbeddingComponent();

        // Verify that both activities are embedded and that the bounds are correct
        if (!shouldExpandContainers) {
            // If the split pair is stacked, ignore to check the bounds because the primary activity
            // may have been occluded and the latest configuration may not be received.
            waitForActivityBoundsEquals(primaryActivity, expectedBoundsPair.first);
            assertTrue(activityEmbeddingComponent.isActivityEmbedded(primaryActivity));
        }
        if (secondaryActivity != null) {
            waitForActivityBoundsEquals(secondaryActivity, expectedBoundsPair.second);
            assertEquals(!shouldExpandContainers,
                    activityEmbeddingComponent.isActivityEmbedded(secondaryActivity));
        }
    }

    /**
     * Waits for the activity specified in {@code activityId} to be in resumed state and verifies
     * if it fills the task.
     */
    public static void waitAndAssertResumedAndFillsTask(@NonNull String activityId) {
        waitAndAssertResumed(activityId);
        final Activity activity = getResumedActivityById(activityId);
        final Rect taskBounds = waitAndGetTaskBounds(activity, false /* shouldWaitForResume */);
        PollingCheck.waitFor(WAIT_FOR_LIFECYCLE_TIMEOUT_MS, () ->
                getActivityBounds(activity).equals(taskBounds));
        assertEquals(taskBounds, getActivityBounds(activity));
    }

    /** Waits for the {@code activity} to be in resumed state and verifies if it fills the task. */
    public static void waitAndAssertResumedAndFillsTask(@NonNull Activity activity) {
        final Rect taskBounds = waitAndGetTaskBounds(activity, true /* shouldWaitForResume */);
        PollingCheck.waitFor(WAIT_FOR_LIFECYCLE_TIMEOUT_MS, () ->
                getActivityBounds(activity).equals(taskBounds));
        assertEquals(taskBounds, getActivityBounds(activity));
    }

    /**
     * Verifies whether the value reported from {@code activityStackCallback} is expected.
     *
     * @param activity the {@link Activity} that must contain in the {@link ActivityStack}
     */
    public static void verifyStandaloneActivityStackIfNeeded(
            @Nullable TestValueCountConsumer<List<ActivityStack>> activityStackCallback,
            @NonNull Activity activity) {
        final List<ActivityStack> activityStacks = getLastActivityStacks(activityStackCallback);
        if (activityStacks != null) {
            assertActivityStackContainsActivity(activityStacks, activity);
        }
    }

    @NonNull
    public static Rect waitAndGetTaskBounds(@NonNull Activity activity,
                                            boolean shouldWaitForResume) {
        final WindowManagerStateHelper wmState = new WindowManagerStateHelper();
        final ComponentName activityName = activity.getComponentName();
        wmState.waitForValidState(activityName);
        if (shouldWaitForResume) {
            wmState.waitAndAssertActivityState(activityName, STATE_RESUMED);
        }
        return wmState.getTaskByActivity(activityName).getBounds();
    }

    /** Waits until the bounds of the activity matches the given bounds. */
    public static void waitForActivityBoundsEquals(@NonNull Activity activity,
            @NonNull Rect bounds) {
        PollingCheck.waitFor(WAIT_FOR_LIFECYCLE_TIMEOUT_MS,
                () -> getActivityBounds(activity).equals(bounds),
                "Expected bounds: " + bounds + ", actual bounds:" + getActivityBounds(activity));
    }

    private static boolean waitForResumed(
            @NonNull List<Activity> activityList) {
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < WAIT_FOR_LIFECYCLE_TIMEOUT_MS) {
            boolean allActivitiesResumed = true;
            for (Activity activity : activityList) {
                allActivitiesResumed &= WindowManagerJetpackTestBase.isActivityResumed(activity);
                if (!allActivitiesResumed) {
                    break;
                }
            }
            if (allActivitiesResumed) {
                return true;
            }
            waitAndLog("resumed:" + activityList);
        }
        return false;
    }

    private static boolean waitForResumed(@NonNull String activityId) {
        return waitForResumed(activityId, WAIT_FOR_LIFECYCLE_TIMEOUT_MS);
    }

    private static boolean waitForResumed(@NonNull String activityId, long timeout) {
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeout) {
            if (getResumedActivityById(activityId) != null) {
                return true;
            }
            waitAndLog("resumed:" + activityId);
        }
        return false;
    }

    private static boolean waitForResumed(@NonNull Activity activity) {
        return waitForResumed(Arrays.asList(activity));
    }

    /**
     * Similar to #waitAndAssertResumed, but with a longer timeout, since it may include a cold
     * launch which involves process startup and application initialization.
     */
    public static void waitAndAssertColdLaunch(@NonNull String activityId) {
        assertTrue(
                "Activity with id=" + activityId + " should be resumed",
                waitForResumed(activityId, WAIT_FOR_COLD_LAUNCH_TIMEOUT_MS));
    }

    public static void waitAndAssertResumed(@NonNull String activityId) {
        assertTrue("Activity with id=" + activityId + " should be resumed",
                waitForResumed(activityId));
    }

    public static void waitAndAssertResumed(@NonNull Activity activity) {
        assertTrue(activity + " should be resumed", waitForResumed(activity));
    }

    public static void waitAndAssertResumed(@NonNull List<Activity> activityList) {
        assertTrue("All activities in this list should be resumed:" + activityList,
                waitForResumed(activityList));
    }

    public static void waitAndAssertNotResumed(@NonNull String activityId) {
        assertFalse("Activity with id=" + activityId + " should not be resumed",
                waitForResumed(activityId));
    }

    public static boolean waitForVisible(@NonNull Activity activity, boolean visible) {
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < WAIT_FOR_LIFECYCLE_TIMEOUT_MS) {
            if (WindowManagerJetpackTestBase.isActivityVisible(activity) == visible) {
                return true;
            }
            waitAndLog("visible:" + visible + " on " + activity);
        }
        return false;
    }

    public static void waitAndAssertVisible(@NonNull Activity activity) {
        assertTrue(activity + " should be visible",
                waitForVisible(activity, true /* visible */));
    }

    public static void waitAndAssertNotVisible(@NonNull Activity activity) {
        assertTrue(activity + " should not be visible",
                waitForVisible(activity, false /* visible */));
    }

    private static boolean waitForFinishing(@NonNull Activity activity) {
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < WAIT_FOR_LIFECYCLE_TIMEOUT_MS) {
            if (activity.isFinishing()) {
                return true;
            }
            waitAndLog("finishing:" + activity);
        }
        return activity.isFinishing();
    }

    public static void waitAndAssertFinishing(@NonNull Activity activity) {
        assertTrue(activity + " should be finishing", waitForFinishing(activity));
    }

    private static void waitAndLog(String reason) {
        Log.d(TAG, "** Waiting for " + reason);
        SystemClock.sleep(WAIT_PERIOD);
    }

    @Nullable
    public static Activity getPrimaryStackTopActivity(SplitInfo splitInfo) {
        List<Activity> primaryActivityStack = splitInfo.getPrimaryActivityStack().getActivities();
        if (primaryActivityStack.isEmpty()) {
            return null;
        }
        return primaryActivityStack.get(primaryActivityStack.size() - 1);
    }

    @Nullable
    public static Activity getSecondaryStackTopActivity(SplitInfo splitInfo) {
        List<Activity> secondaryActivityStack = splitInfo.getSecondaryActivityStack()
                .getActivities();
        if (secondaryActivityStack.isEmpty()) {
            return null;
        }
        return secondaryActivityStack.get(secondaryActivityStack.size() - 1);
    }

    /** Returns the expected bounds of the primary and secondary containers */
    @NonNull
    private static Pair<Rect, Rect> getExpectedBoundsPair(@NonNull Activity activity,
            @NonNull SplitAttributes splitAttributes) {
        SplitType splitType = splitAttributes.getSplitType();

        final Rect parentTaskBounds = waitAndGetTaskBounds(activity,
                false /* shouldWaitForResume */);
        if (splitType instanceof SplitType.ExpandContainersSplitType) {
            return new Pair<>(new Rect(parentTaskBounds), new Rect(parentTaskBounds));
        }

        int layoutDir = (splitAttributes.getLayoutDirection() == LayoutDirection.LOCALE)
                ? activity.getResources().getConfiguration().getLayoutDirection()
                : splitAttributes.getLayoutDirection();
        final boolean isPrimaryRightOrBottomContainer = isPrimaryRightOrBottomContainer(layoutDir);

        FoldingFeature foldingFeature;
        try {
            foldingFeature = getFoldingFeature(getExtensionWindowLayoutInfo(activity));
        } catch (InterruptedException e) {
            foldingFeature = null;
        }
        if (splitType instanceof SplitAttributes.SplitType.HingeSplitType) {
            if (shouldSplitByHinge(foldingFeature, splitAttributes)) {
                // The split pair should be split by hinge if there's exactly one hinge
                // at the current device state.
                final Rect hingeArea = foldingFeature.getBounds();
                final Rect leftContainer = new Rect(parentTaskBounds.left, parentTaskBounds.top,
                        hingeArea.left, parentTaskBounds.bottom);
                final Rect topContainer = new Rect(parentTaskBounds.left, parentTaskBounds.top,
                        parentTaskBounds.right, hingeArea.top);
                final Rect rightContainer = new Rect(hingeArea.right, parentTaskBounds.top,
                        parentTaskBounds.right, parentTaskBounds.bottom);
                final Rect bottomContainer = new Rect(parentTaskBounds.left, hingeArea.bottom,
                        parentTaskBounds.right, parentTaskBounds.bottom);
                switch (layoutDir) {
                    case LayoutDirection.LEFT_TO_RIGHT: {
                        return new Pair<>(leftContainer, rightContainer);
                    }
                    case LayoutDirection.RIGHT_TO_LEFT: {
                        return new Pair<>(rightContainer, leftContainer);
                    }
                    case LayoutDirection.TOP_TO_BOTTOM: {
                        return new Pair<>(topContainer, bottomContainer);
                    }
                    case LayoutDirection.BOTTOM_TO_TOP: {
                        return new Pair<>(bottomContainer, topContainer);
                    }
                    default:
                        throw new UnsupportedOperationException("Unsupported layout direction: "
                                + layoutDir);
                }
            } else {
                splitType = ((SplitType.HingeSplitType) splitType).getFallbackSplitType();
            }
        }

        assertTrue("The SplitType must be RatioSplitType",
                splitType instanceof SplitType.RatioSplitType);

        float splitRatio = ((SplitType.RatioSplitType) splitType).getRatio();
        // Normalize the split ratio so that parent start + (parent dimension * split ratio) is
        // always the position of the split divider in the parent.
        if (isPrimaryRightOrBottomContainer) {
            splitRatio = 1 - splitRatio;
        }

        // Calculate the container bounds
        final boolean isHorizontal = isHorizontal(layoutDir);
        final int dividerOffsetLeftOrTop = getBoundsOffsetForDivider(
                activity, splitAttributes, true /* isLeftOrTop */);
        final int dividerOffsetRightOrBottom = getBoundsOffsetForDivider(
                activity, splitAttributes, false /* isLeftOrTop */);
        final Rect leftOrTopContainerBounds = isHorizontal
                ? new Rect(
                        parentTaskBounds.left,
                        parentTaskBounds.top,
                        parentTaskBounds.right,
                        (int) (parentTaskBounds.top + parentTaskBounds.height() * splitRatio)
                                + dividerOffsetLeftOrTop
                ) : new Rect(
                        parentTaskBounds.left,
                        parentTaskBounds.top,
                        (int) (parentTaskBounds.left + parentTaskBounds.width() * splitRatio)
                                + dividerOffsetLeftOrTop,
                        parentTaskBounds.bottom);

        final Rect rightOrBottomContainerBounds = isHorizontal
                ? new Rect(
                        parentTaskBounds.left,
                        (int) (parentTaskBounds.top + parentTaskBounds.height() * splitRatio)
                                + dividerOffsetRightOrBottom,
                        parentTaskBounds.right,
                        parentTaskBounds.bottom
                ) : new Rect(
                        (int) (parentTaskBounds.left + parentTaskBounds.width() * splitRatio)
                                + dividerOffsetRightOrBottom,
                        parentTaskBounds.top,
                        parentTaskBounds.right,
                        parentTaskBounds.bottom);

        // Assign the primary and secondary bounds depending on layout direction
        if (isPrimaryRightOrBottomContainer) {
            return new Pair<>(rightOrBottomContainerBounds, leftOrTopContainerBounds);
        } else {
            return new Pair<>(leftOrTopContainerBounds, rightOrBottomContainerBounds);
        }
    }

    private static int getBoundsOffsetForDivider(
            @NonNull Activity activity,
            @NonNull SplitAttributes splitAttributes,
            boolean isLeftOrTop) {
        final DividerAttributes dividerAttributes = splitAttributes.getDividerAttributes();
        if (dividerAttributes == null) {
            return 0;
        }
        final int dividerWidthPx = (int) TypedValue.applyDimension(
                COMPLEX_UNIT_DIP, dividerAttributes.getWidthDp(),
                activity.getResources().getDisplayMetrics());
        final SplitType splitType = splitAttributes.getSplitType();

        if (splitType instanceof SplitType.ExpandContainersSplitType) {
            // No divider offset is needed for the ExpandContainersSplitType.
            return 0;
        }
        int primaryOffset;
        if (splitType instanceof final SplitType.RatioSplitType splitRatio) {
            primaryOffset = (int) (dividerWidthPx * splitRatio.getRatio());
        } else {
            primaryOffset = dividerWidthPx / 2;
        }
        final int secondaryOffset = dividerWidthPx - primaryOffset;
        return isLeftOrTop ? -primaryOffset : secondaryOffset;
    }

    private static boolean isHorizontal(int layoutDirection) {
        switch (layoutDirection) {
            case LayoutDirection.TOP_TO_BOTTOM:
            case LayoutDirection.BOTTOM_TO_TOP:
                return true;
            default :
                return false;
        }
    }

    /** Indicates that whether the primary container is at right or bottom or not. */
    private static boolean isPrimaryRightOrBottomContainer(int layoutDirection) {
        switch (layoutDirection) {
            case LayoutDirection.RIGHT_TO_LEFT:
            case LayoutDirection.BOTTOM_TO_TOP:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns the folding feature if there is exact one in {@link WindowLayoutInfo}. Returns
     * {@code null}, otherwise.
     */
    @Nullable
    private static FoldingFeature getFoldingFeature(@Nullable WindowLayoutInfo windowLayoutInfo) {
        if (windowLayoutInfo == null) {
            return null;
        }

        List<FoldingFeature> foldingFeatures = windowLayoutInfo.getDisplayFeatures()
                .stream().filter(feature -> feature instanceof FoldingFeature)
                .map(feature -> (FoldingFeature) feature)
                .toList();

        // Cannot be followed by hinge if there's no or more than one hinges.
        if (foldingFeatures.size() != 1) {
            return null;
        }
        return foldingFeatures.get(0);
    }

    private static boolean shouldSplitByHinge(
            @Nullable FoldingFeature foldingFeature, @NonNull SplitAttributes splitAttributes) {
        // Don't need to check if SplitType is not HingeSplitType
        if (!(splitAttributes.getSplitType() instanceof SplitAttributes.SplitType.HingeSplitType)) {
            return false;
        }

        // Can't split by hinge because there's zero or multiple hinges.
        if (foldingFeature == null) {
            return false;
        }

        final Rect hingeArea = foldingFeature.getBounds();

        // Hinge orientation should match SplitAttributes layoutDirection.
        return (hingeArea.width() > hingeArea.height())
                == ActivityEmbeddingUtil.isHorizontal(splitAttributes.getLayoutDirection());
    }

    /** Assumes that WM Extensions - Activity Embedding feature is enabled on the device. */
    public static void assumeActivityEmbeddingSupportedDevice() {
        assumeExtensionSupportedDevice();
        // Devices are required to enable Activity Embedding with WM Extensions, unless the
        // app's targetSDK is smaller than Android 15.
        assertNotNull(
                "Device with WM Extensions must support ActivityEmbedding",
                getWindowExtensions().getActivityEmbeddingComponent());
    }

    private static void assertSplitInfoTopSplitIsCorrect(
            @NonNull List<SplitInfo> splitInfoList,
            @NonNull Activity primaryActivity,
            @NonNull Activity secondaryActivity,
            @NonNull SplitAttributes splitAttributes) {
        assertFalse("Split info callback should not be empty", splitInfoList.isEmpty());
        final SplitInfo topSplit = splitInfoList.get(splitInfoList.size() - 1);
        assertEquals(
                "Expect primary activity to match the top of the primary stack",
                primaryActivity,
                getPrimaryStackTopActivity(topSplit));
        assertEquals(
                "Expect secondary activity to match the top of the secondary stack",
                secondaryActivity,
                getSecondaryStackTopActivity(topSplit));
        assertEquals(splitAttributes, topSplit.getSplitAttributes());
    }

    private static void assertActivityStacksIsCorrect(
            @NonNull List<ActivityStack> activityStacks,
            @NonNull Activity primaryActivity,
            @Nullable Activity secondaryActivity) {
        assertActivityStackContainsActivity(activityStacks, primaryActivity);

        if (secondaryActivity != null) {
            final ActivityStack secondaryActivityStack = activityStacks.getLast();
            assertTrue(
                    "Secondary ActivityStack should contain "
                            + secondaryActivity
                            + ", but was"
                            + activityStacks,
                    secondaryActivityStack.getActivities().contains(secondaryActivity));
        }
    }

    private static void assertActivityStackContainsActivity(
            @NonNull List<ActivityStack> activityStacks, @NonNull Activity activity) {
        final List<ActivityStack> filteredActivityStacks =
                activityStacks.stream()
                        .filter(activityStack -> activityStack.getActivities().contains(activity))
                        .toList();
        assertEquals(
                "There must exactly one ActivityStack containing Activity:"
                        + activity
                        + ", but was "
                        + filteredActivityStacks,
                1,
                filteredActivityStacks.size());
    }
}
