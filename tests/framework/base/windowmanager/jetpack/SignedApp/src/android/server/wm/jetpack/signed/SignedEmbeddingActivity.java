/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.server.wm.jetpack.signed;

import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.getWindowExtensions;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.EMBEDDED_ACTIVITY_ID;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.assumeActivityEmbeddingSupportedDevice;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createSplitPairRuleBuilder;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityCrossUidInSplit;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.EXTRA_EMBED_ACTIVITY;
import static android.server.wm.jetpack.utils.WindowManagerJetpackTestBase.EXTRA_SPLIT_RATIO;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.server.wm.jetpack.extensions.util.TestValueCountConsumer;
import android.server.wm.jetpack.utils.TestActivityKnownEmbeddingCerts;

import androidx.window.extensions.embedding.ActivityEmbeddingComponent;
import androidx.window.extensions.embedding.ActivityStack;
import androidx.window.extensions.embedding.SplitAttributes;
import androidx.window.extensions.embedding.SplitInfo;
import androidx.window.extensions.embedding.SplitPairRule;

import org.junit.AssumptionViolatedException;

import java.util.Collections;
import java.util.List;

/**
 * A test activity that attempts to embed {@link TestActivityKnownEmbeddingCerts} when created.
 * The app it belongs to is signed with a certificate that is recognized by the target app as a
 * trusted embedding host.
 */
public class SignedEmbeddingActivity extends Activity {

    private TestValueCountConsumer<List<SplitInfo>> mSplitInfoConsumer;

    private TestValueCountConsumer<List<ActivityStack>> mActivityStackCallback;

    private ActivityEmbeddingComponent mActivityEmbeddingComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getIntent().getBooleanExtra(EXTRA_EMBED_ACTIVITY, false)) {
            startActivityInSplit();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.getBooleanExtra(EXTRA_EMBED_ACTIVITY, false)) {
            startActivityInSplit();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mActivityEmbeddingComponent == null) {
            return;
        }

        if (mSplitInfoConsumer != null) {
            mActivityEmbeddingComponent.clearSplitInfoCallback();
        }

        if (mActivityStackCallback != null) {
            mActivityEmbeddingComponent.unregisterActivityStackCallback(mActivityStackCallback);
        }
    }

    void startActivityInSplit() {
        try {
            assumeActivityEmbeddingSupportedDevice();
            mActivityEmbeddingComponent = getWindowExtensions().getActivityEmbeddingComponent();
        } catch (AssumptionViolatedException e) {
            // Embedding not supported
            finish();
            return;
        }

        mSplitInfoConsumer = new TestValueCountConsumer<>();
        mActivityEmbeddingComponent.setSplitInfoCallback(mSplitInfoConsumer);
        SplitAttributes.SplitType splitType = new SplitAttributes.SplitType.RatioSplitType(
                getIntent().getFloatExtra(EXTRA_SPLIT_RATIO, 0.5f));

        SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityActivityPredicate */,
                activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(new SplitAttributes.Builder()
                        .setSplitType(splitType).build())
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        mActivityStackCallback = new TestValueCountConsumer<>();
        mActivityEmbeddingComponent.registerActivityStackCallback(
                Runnable::run, mActivityStackCallback);

        // Launch an activity from a different UID that recognizes this package's signature and
        // verify that it is split with this activity.
        startActivityCrossUidInSplit(
                this,
                new ComponentName(
                        "android.server.wm.jetpack",
                        "android.server.wm.jetpack.utils.TestActivityKnownEmbeddingCerts"),
                splitPairRule,
                mSplitInfoConsumer,
                mActivityStackCallback,
                EMBEDDED_ACTIVITY_ID,
                false /* verify */);
    }
}
