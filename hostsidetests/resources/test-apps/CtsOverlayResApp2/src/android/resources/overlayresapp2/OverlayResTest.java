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

package android.resources.cts.overlayresapp2;

import static com.android.cts.overlay.target.Utils.setOverlayEnabled;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.content.res.Resources;

import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class OverlayResTest {
    private static final String OVERLAY_PACKAGE = "android.resources.cts.overlayres2.rro";

    // Default timeout value
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);
    private static final String TEST_OVERLAY_STRING_VALUE = "RRO Test String";
    private static final String TEST_STRING_VALUE = "Test String";
    private static final int TEST_OVERLAY_COLOR_VALUE = 0xFFFFFFFF;
    private static final int TEST_COLOR_VALUE = 0xFF000000;
    private OverlayResActivity mActivity;

    @Rule
    public ActivityScenarioRule<OverlayResActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(OverlayResActivity.class);

    @Before
    public void setup() {
        mActivityScenarioRule.getScenario().onActivity(activity -> {
            assertThat(activity).isNotNull();
            mActivity = activity;
        });
    }

    @Test
    public void overlayRes_onConfigurationChanged() throws Exception {
        final CountDownLatch latch1 = new CountDownLatch(1);
        mActivity.setConfigurationChangedCallback(() -> {
            Resources r = mActivity.getApplicationContext().getResources();
            assertThat(r.getString(R.string.test_string)).isEqualTo(TEST_OVERLAY_STRING_VALUE);
            assertThat(r.getColor(R.color.test_color)).isEqualTo(TEST_OVERLAY_COLOR_VALUE);
            latch1.countDown();
        });

        setOverlayEnabled(OVERLAY_PACKAGE, true /* enabled */);

        if (!latch1.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for enabling the OverlayResAppRRO.apk, "
                    + "onConfigurationChanged() has not been invoked.");
        }

        final CountDownLatch latch2 = new CountDownLatch(1);
        mActivity.setConfigurationChangedCallback(() -> {
            Resources r = mActivity.getApplicationContext().getResources();
            assertThat(r.getString(R.string.test_string)).isEqualTo(TEST_STRING_VALUE);
            assertThat(r.getColor(R.color.test_color)).isEqualTo(TEST_COLOR_VALUE);
            latch2.countDown();
        });

        setOverlayEnabled(OVERLAY_PACKAGE, false /* disabled */);

        if (!latch2.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Fail to wait configuration changes for disabling the OverlayResAppRRO.apk, "
                    + "onConfigurationChanged() has not been invoked.");
        }
    }
}
