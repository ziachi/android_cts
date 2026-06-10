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

import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.assertValidSplit;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertNotResumed;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumed;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumedAndFillsTask;
import static android.server.wm.jetpack.utils.TestActivityLauncher.KEY_ACTIVITY_ID;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.CliIntentExtra;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.window.extensions.embedding.ActivityEmbeddingComponent;
import androidx.window.extensions.embedding.SplitPlaceholderRule;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the placeholders functionality within Activity Embedding on secondary
 * display. An activity can provide a {@link SplitPlaceholderRule} to the
 * {@link ActivityEmbeddingComponent} which will enable the activity to launch directly into a split
 * with the placeholder activity it is configured to launch with.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:MultiDisplayActivityEmbeddingPlaceholderTests
 */
@Presubmit
public class MultiDisplayActivityEmbeddingPlaceholderTests
        extends ActivityEmbeddingPlaceholderTests {

    private final VirtualDisplaySession mSession =
            new ActivityManagerTestBase.VirtualDisplaySession();
    private final MultiDisplayTestHelper mTestHelper =
            new MultiDisplayTestHelper(mSession);

    @Before
    @Override
    public void setUp() throws Exception {
        assumeTrue(supportsMultiDisplay());

        mTestHelper.setUpTestDisplay();
        super.setUp();
    }

    @After
    @Override
    public void tearDown() {
        mTestHelper.releaseDisplay();
    }

    @Override
    @Nullable
    public Integer getLaunchingDisplayId() {
        return mTestHelper.getSecondaryDisplayId();
    }

    /**
     * Verifies the behavior to launch placeholder when activity launches on the secondary display.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.embedding.SplitPlaceholderRule#matchesActivity",
            "androidx.window.extensions.embedding.SplitPlaceholderRule#matchesIntent",
            "androidx.window.extensions.embedding.SplitPlaceholderRule#checkParentMetrics"
    })
    @Test
    public void testPlaceholderLaunchOnSecondaryDisplay() {
        // Resize the secondary display to 1.2 * display size in case the secondary display size
        // matches the default one.
        mWmState.computeState();
        final Rect mainDisplayBounds = mWmState.getDisplay(getMainDisplayId()).getBounds();
        mReportedDisplayMetrics.setSize(new Size((int) (mainDisplayBounds.width() * 1.2),
                (int) (mainDisplayBounds.height() * 1.2)));
        final Rect secondaryDisplayTaskBounds = getTaskBounds(mTestHelper.getSecondaryDisplayId());
        final int secondaryDisplayTaskWidth = secondaryDisplayTaskBounds.width();
        final int secondaryDisplayTaskHeight = secondaryDisplayTaskBounds.height();

        // Set embedding rules with the parent window metrics only allowing side-by-side
        // activities on a task bounds on secondary display.
        final SplitPlaceholderRule splitPlaceholderRule =
                new SplitPlaceholderRuleBuilderWithDefaults(PRIMARY_ACTIVITY_ID,
                        PLACEHOLDER_ACTIVITY_ID)
                        .setParentWindowMetrics(windowMetrics ->
                                windowMetrics.getBounds().width() >= secondaryDisplayTaskWidth
                                        && windowMetrics.getBounds().height()
                                        >= secondaryDisplayTaskHeight)
                        .build();
        mActivityEmbeddingComponent.setEmbeddingRules(
                Collections.singleton(splitPlaceholderRule));

        // Launch activity on the default display and verify that it fills the task and that
        // a placeholder activity is not launched
        final Activity primaryActivityOnMainDisplay = startFullScreenActivityNewTask(
                TestActivityWithId.class, PRIMARY_ACTIVITY_ID, getMainDisplayId());

        waitAndAssertResumedAndFillsTask(primaryActivityOnMainDisplay);
        waitAndAssertNotResumed(PLACEHOLDER_ACTIVITY_ID);

        final int secondaryDisplayId = mTestHelper.getSecondaryDisplayId();
        launchActivityOnDisplay(primaryActivityOnMainDisplay.getComponentName(), secondaryDisplayId,
                CliIntentExtra.extraString(KEY_ACTIVITY_ID, PRIMARY_ACTIVITY_ID));
        final Activity primaryActivityOnSecondaryDisplay = getResumedActivityById(
                PRIMARY_ACTIVITY_ID, secondaryDisplayId);

        // Verify that the placeholder activity is launched into a split with the primary
        // activity
        waitAndAssertResumed(PLACEHOLDER_ACTIVITY_ID);
        final Activity placeholderActivity = getResumedActivityById(PLACEHOLDER_ACTIVITY_ID);
        assertValidSplit(primaryActivityOnSecondaryDisplay, placeholderActivity,
                splitPlaceholderRule);
    }

    /**
     * Verifies the behavior to launch placeholder when activity launches on the default display.
     */
    @ApiTest(apis = {
            "androidx.window.extensions.embedding.SplitPlaceholderRule#matchesActivity",
            "androidx.window.extensions.embedding.SplitPlaceholderRule#matchesIntent",
            "androidx.window.extensions.embedding.SplitPlaceholderRule#checkParentMetrics"
    })
    @Test
    public void testPlaceholderLaunchOnDefaultDisplay() {
        // Resize the secondary display to 0.8 * display size in case the secondary display size
        // matches the default one.
        mWmState.computeState();
        final Rect mainDisplayBounds = mWmState.getDisplay(getMainDisplayId()).getBounds();
        mReportedDisplayMetrics.setSize(new Size((int) (mainDisplayBounds.width() * 0.8),
                (int) (mainDisplayBounds.height() * 0.8)));
        final Rect defaultDisplayTaskBounds = getTaskBounds(getMainDisplayId());
        final int defaultDisplayTaskWidth = defaultDisplayTaskBounds.width();
        final int defaultDisplayTaskHeight = defaultDisplayTaskBounds.height();

        // Set embedding rules with the parent window metrics only allowing side-by-side
        // activities on a task bounds on the default display.
        final SplitPlaceholderRule splitPlaceholderRule =
                new SplitPlaceholderRuleBuilderWithDefaults(PRIMARY_ACTIVITY_ID,
                        PLACEHOLDER_ACTIVITY_ID)
                        .setParentWindowMetrics(windowMetrics ->
                                windowMetrics.getBounds().width() >= defaultDisplayTaskWidth
                                        && windowMetrics.getBounds().height()
                                        >= defaultDisplayTaskHeight)
                        .build();
        mActivityEmbeddingComponent.setEmbeddingRules(
                Collections.singleton(splitPlaceholderRule));

        // Launch activity on the secondary display and verify that it fills the task and that
        // a placeholder activity is not launched
        final Activity primaryActivityOnSecondaryDisplay = startFullScreenActivityNewTask(
                TestActivityWithId.class, PRIMARY_ACTIVITY_ID, mTestHelper.getSecondaryDisplayId());

        waitAndAssertResumedAndFillsTask(primaryActivityOnSecondaryDisplay);
        waitAndAssertNotResumed(PLACEHOLDER_ACTIVITY_ID);

        final int displayId = getMainDisplayId();
        launchActivityOnDisplay(primaryActivityOnSecondaryDisplay.getComponentName(), displayId,
                CliIntentExtra.extraString(KEY_ACTIVITY_ID, PRIMARY_ACTIVITY_ID));
        final Activity primaryActivityOnMainDisplay = getResumedActivityById(PRIMARY_ACTIVITY_ID,
                displayId);

        // Verify that the placeholder activity is launched into a split with the primary
        // activity
        waitAndAssertResumed(PLACEHOLDER_ACTIVITY_ID);
        final Activity placeholderActivity = getResumedActivityById(PLACEHOLDER_ACTIVITY_ID);
        assertValidSplit(primaryActivityOnMainDisplay, placeholderActivity, splitPlaceholderRule);
    }
}
