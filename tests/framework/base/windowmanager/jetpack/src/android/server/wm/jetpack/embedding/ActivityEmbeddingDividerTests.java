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

import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createSplitPairRuleBuilder;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplitAttributes;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.graphics.Color;
import android.platform.test.annotations.Presubmit;
import android.server.wm.jetpack.utils.TestActivityWithId;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.core.util.function.Function;
import androidx.window.extensions.embedding.DividerAttributes;
import androidx.window.extensions.embedding.SplitAttributes;
import androidx.window.extensions.embedding.SplitAttributesCalculatorParams;
import androidx.window.extensions.embedding.SplitInfo;
import androidx.window.extensions.embedding.SplitPairRule;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

/**
 * Tests for the {@link androidx.window.extensions} implementation with {@link DividerAttributes}
 * configuration.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingDividerTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingDividerTests extends ActivityEmbeddingTestBase {
    private static final String ACTIVITY_A_ID = "activityA";
    private static final String ACTIVITY_B_ID = "activityB";
    private static final String ACTIVITY_C_ID = "activityC";
    private static final String ACTIVITY_D_ID = "activityD";
    private static final String RULE_TAG = "testRule";

    /** Verifies setting fixed {@link DividerAttributes}. */
    @ApiTest(apis = {
            "androidx.window.extensions.embedding.DividerAttributes.Builder#build",
            "androidx.window.extensions.embedding.SplitAttributes#setDividerAttributes",
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent#setEmbeddingRules",
    })
    @Test
    public void testSetDividerAttributes_fixed() {
        final DividerAttributes dividerAttributes =
                new DividerAttributes.Builder(DividerAttributes.DIVIDER_TYPE_FIXED)
                        .setWidthDp(10)
                        .setDividerColor(Color.GREEN)
                        .build();

        final SplitAttributes attributes =
                new SplitAttributes.Builder()
                        .setDividerAttributes(dividerAttributes)
                        .build();

        // Create a split rule for activity A and activity B where the split ratio is 0.5.
        final SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true, /* activityIntentPairPredicate */
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(attributes)
                .setTag(RULE_TAG)
                .build();

        // Register the split pair rule.
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch the activity A and B split and verify that the split pair matches
        // defaultSplitAttributes.
        final Activity activityA = startFullScreenActivityNewTask(TestActivityWithId.class,
                ACTIVITY_A_ID);
        startActivityAndVerifySplitAttributes(
                activityA,
                TestActivityWithId.class,
                splitPairRule,
                ACTIVITY_B_ID,
                mSplitInfoConsumer,
                mActivityStackCallback);
    }

    /** Verifies setting draggable {@link DividerAttributes}. */
    @ApiTest(apis = {
            "androidx.window.extensions.embedding.DividerAttributes.Builder#build",
            "androidx.window.extensions.embedding.SplitAttributes#setDividerAttributes",
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent#setEmbeddingRules",
    })
    @Test
    public void testSetDividerAttributes_draggable() {
        final DividerAttributes dividerAttributes =
                new DividerAttributes.Builder(DividerAttributes.DIVIDER_TYPE_DRAGGABLE)
                        .setWidthDp(10)
                        .setDividerColor(Color.GREEN)
                        .setDraggingToFullscreenAllowed(true)
                        .setPrimaryMinRatio(0.3f)
                        .setPrimaryMaxRatio(0.7f)
                        .build();

        final SplitAttributes attributes =
                new SplitAttributes.Builder()
                        .setDividerAttributes(dividerAttributes)
                        .build();

        // Create a split rule for activity A and activity B where the split ratio is 0.5.
        final SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true, /* activityIntentPairPredicate */
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(attributes)
                .setTag(RULE_TAG)
                .build();

        // Register the split pair rule.
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch the activity A and B split and verify that the split pair matches
        // defaultSplitAttributes.
        final Activity activityA = startFullScreenActivityNewTask(TestActivityWithId.class,
                ACTIVITY_A_ID);
        startActivityAndVerifySplitAttributes(
                activityA,
                TestActivityWithId.class,
                splitPairRule,
                ACTIVITY_B_ID,
                mSplitInfoConsumer,
                mActivityStackCallback);
    }

    /** Verifies setting {@link DividerAttributes} with default values. */
    @ApiTest(apis = {
            "androidx.window.extensions.embedding.DividerAttributes.Builder#build",
            "androidx.window.extensions.embedding.SplitAttributes#setDividerAttributes",
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent#setEmbeddingRules",
    })
    @Test
    public void testSetDividerAttributes_draggableWithDefaultValues() {
        final DividerAttributes dividerAttributes =
                new DividerAttributes.Builder(DividerAttributes.DIVIDER_TYPE_DRAGGABLE).build();

        final SplitAttributes attributes =
                new SplitAttributes.Builder()
                        .setDividerAttributes(dividerAttributes)
                        .build();

        // Create a split rule for activity A and activity B where the split ratio is 0.5.
        final SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true, /* activityIntentPairPredicate */
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(attributes)
                .setTag(RULE_TAG)
                .build();

        // Register the split pair rule.
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch the activity A and B split and verify that the split pair matches
        // defaultSplitAttributes.
        final Activity activityA = startFullScreenActivityNewTask(TestActivityWithId.class,
                ACTIVITY_A_ID);

        // Set the expected callback count
        mSplitInfoConsumer.setCount(1);

        // Start second activity
        startActivityFromActivity(activityA, TestActivityWithId.class, ACTIVITY_B_ID);
        waitAndAssertResumed(ACTIVITY_B_ID);

        final DividerAttributes currentDividerAttributes = waitAndGetDividerAttributes();

        // Divider type should be same as configured.
        assertEquals(DividerAttributes.DIVIDER_TYPE_DRAGGABLE,
                currentDividerAttributes.getDividerType());

        // Dragging to fullscreen should be disabled by default.
        assertFalse(currentDividerAttributes.isDraggingToFullscreenAllowed());

        // The system should provide default values to these unset fields.
        assertNotEquals(DividerAttributes.WIDTH_SYSTEM_DEFAULT,
                currentDividerAttributes.getWidthDp());
        assertNotEquals(DividerAttributes.RATIO_SYSTEM_DEFAULT,
                currentDividerAttributes.getPrimaryMinRatio());
        assertNotEquals(DividerAttributes.RATIO_SYSTEM_DEFAULT,
                currentDividerAttributes.getPrimaryMaxRatio());
    }

    /**
     * Verifies setting {@link DividerAttributes} with the split calculator.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.embedding.DividerAttributes.Builder#build",
            "androidx.window.extensions.embedding.SplitAttributes#setDividerAttributes",
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent#setEmbeddingRules",
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent"
                    + "#setSplitAttributesCalculator",
    })
    @Test
    public void testSetDividerAttributes_splitCalculator() {
        final DividerAttributes dividerAttributes1 =
                new DividerAttributes.Builder(DividerAttributes.DIVIDER_TYPE_DRAGGABLE)
                        .setWidthDp(10)
                        .setDividerColor(Color.GREEN)
                        .setDraggingToFullscreenAllowed(true)
                        .setPrimaryMinRatio(0.3f)
                        .setPrimaryMaxRatio(0.7f)
                        .build();

        final SplitAttributes attributes =
                new SplitAttributes.Builder()
                        .setDividerAttributes(dividerAttributes1)
                        .build();

        // Create a split rule for activity A and activity B where the split ratio is 0.5.
        final SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true, /* activityIntentPairPredicate */
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(attributes)
                .setTag(RULE_TAG)
                .build();

        // Register the split pair rule.
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch the activity A and B split and verify that the split pair matches
        // defaultSplitAttributes.
        final Activity activityA = startFullScreenActivityNewTask(TestActivityWithId.class,
                ACTIVITY_A_ID);
        startActivityAndVerifySplitAttributes(
                activityA,
                TestActivityWithId.class,
                splitPairRule,
                ACTIVITY_B_ID,
                mSplitInfoConsumer,
                mActivityStackCallback);

        // Choose a split attributes that different from the default one.
        final DividerAttributes dividerAttributes2 =
                new DividerAttributes.Builder(DividerAttributes.DIVIDER_TYPE_DRAGGABLE)
                        .setWidthDp(20)
                        .setDividerColor(Color.BLUE)
                        .setDraggingToFullscreenAllowed(false)
                        .setPrimaryMinRatio(0.2f)
                        .setPrimaryMaxRatio(0.8f)
                        .build();
        final SplitAttributes customizedSplitAttributes = new SplitAttributes.Builder()
                .setDividerAttributes(dividerAttributes2)
                .build();
        final Function<SplitAttributesCalculatorParams, SplitAttributes> calculator = params -> {
            // Only make the customized split attributes apply to the split rule with test tag in
            // case other tests are affected.
            if (RULE_TAG.equals(params.getSplitRuleTag())) {
                return customizedSplitAttributes;
            }
            return params.getDefaultSplitAttributes();
        };
        mActivityEmbeddingComponent.setSplitAttributesCalculator(calculator);

        // Start another Activity to trigger the calculator function, and verify if the split pair
        // matches the customized split attributes.
        startActivityAndVerifySplitAttributes(
                activityA,
                activityA, /* expectedPrimaryActivity */
                TestActivityWithId.class,
                customizedSplitAttributes,
                ACTIVITY_C_ID,
                1 /* expectedCallbackCount */,
                mSplitInfoConsumer,
                mActivityStackCallback);

        // Clear the calculator function, then the split pair should align the default split pair
        // rule behavior.
        mActivityEmbeddingComponent.clearSplitAttributesCalculator();

        // Launch Activity D to apply the change.
        startActivityAndVerifySplitAttributes(
                activityA,
                activityA, /* expectedPrimaryActivity */
                TestActivityWithId.class,
                splitPairRule,
                ACTIVITY_D_ID,
                1, /* expectedCallbackCount */
                mSplitInfoConsumer,
                mActivityStackCallback);
    }

    @NonNull
    private DividerAttributes waitAndGetDividerAttributes() {
        final List<SplitInfo> splitStates;
        try {
            splitStates = mSplitInfoConsumer.waitAndGet();
        } catch (InterruptedException e) {
            throw new AssertionError("mSplitInfoConsumer.waitAndGet() failed", e);
        }
        assertNotNull("Active Split States cannot be null.", splitStates);

        final SplitInfo splitInfo = splitStates.get(0);
        assertNotNull("SplitInfo cannot be null.", splitInfo);

        final DividerAttributes dividerAttributes =
                splitInfo.getSplitAttributes().getDividerAttributes();
        assertNotNull("DividerAttributes cannot be null.", dividerAttributes);

        return dividerAttributes;
    }
}
