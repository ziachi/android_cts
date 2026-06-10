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

package android.app.jank.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.app.jank.AppJankStats;
import android.app.jank.RelativeFrameTimeHistogram;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.UUID;

public class AppJankStatsTest {
    public static final int WAIT_FOR_TIMEOUT_MS = 5000;

    private String mWidgetCategory = AppJankStats.WIDGET_CATEGORY_SCROLL;
    private String mWidgetState = AppJankStats.WIDGET_STATE_SCROLLING;
    private String mWidgetId = UUID.randomUUID().toString();

    private int mAppUid = 100;
    private int mJankyFrameCount = 25;
    private int mTotalFrameCount = 100;
    private RelativeFrameTimeHistogram mFrameTimeHistogram = new RelativeFrameTimeHistogram();
    private String mNavigationComponent = "Navigation Destination Name";

    private UiDevice mDevice;
    private Instrumentation mInstrumentation;
    private ActivityTestRule<BasicActivity> mBasicActivityActivityTestRule =
            new ActivityTestRule<>(BasicActivity.class, false, false);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(mInstrumentation);
    }

    @Test
    public void appJankStats_confirmCorrectAssignments() {
        AppJankStats appJankStats =
                new AppJankStats(
                        mAppUid,
                        mWidgetId,
                        mNavigationComponent,
                        mWidgetCategory,
                        mWidgetState,
                        mTotalFrameCount,
                        mJankyFrameCount,
                        mFrameTimeHistogram);

        assertEquals(appJankStats.getUid(), mAppUid);
        assertEquals(appJankStats.getWidgetId(), mWidgetId);
        assertEquals(appJankStats.getWidgetCategory(), mWidgetCategory);
        assertEquals(appJankStats.getWidgetState(), mWidgetState);
        assertEquals(appJankStats.getTotalFrameCount(), mTotalFrameCount);
        assertEquals(appJankStats.getJankyFrameCount(), mJankyFrameCount);
        assertTrue(appJankStats.getRelativeFrameTimeHistogram() == mFrameTimeHistogram);
        assertEquals(appJankStats.getNavigationComponent(), mNavigationComponent);
    }

    /**
     * This test confirms that calling the reportAppJankStats API from a View type does not cause
     * any issues. The API doesn't return a value, so the test simply checks that the call doesn't
     * break anything.
     */
    @Test
    public void appJankStats_confirmReportingFlow() {
        BasicActivity basicActivity = mBasicActivityActivityTestRule.launchActivity(null);
        mDevice.waitForIdle(WAIT_FOR_TIMEOUT_MS);

        String successMsg = "Jank Stats Reported";
        AppJankStats appJankStats =
                new AppJankStats(
                        mAppUid,
                        mWidgetId,
                        mNavigationComponent,
                        mWidgetCategory,
                        mWidgetState,
                        mTotalFrameCount,
                        mJankyFrameCount,
                        mFrameTimeHistogram);

        basicActivity.reportAppJankStats(appJankStats, successMsg);

        UiObject2 result = mDevice.wait(Until.findObject(By.text(successMsg)), WAIT_FOR_TIMEOUT_MS);
        assertNotNull(result);
    }
}
