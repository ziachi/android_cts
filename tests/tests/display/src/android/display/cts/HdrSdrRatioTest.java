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

package android.display.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Display;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.server.display.feature.flags.Flags;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class HdrSdrRatioTest extends TestBase {
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    private DisplayManager mDisplayManager;

    private Display[] mDisplays;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public ActivityTestRule<HdrContentActivity> mHdrContentActivity =
            new ActivityTestRule<>(
                    HdrContentActivity.class,
                    /* initialTouchMode= */ false,
                    /* launchActivity= */ false);

    @Before
    public void setUp() {
        mDisplayManager = sContext.getSystemService(DisplayManager.class);
        mDisplays = mDisplayManager.getDisplays();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HIGHEST_HDR_SDR_RATIO_API)
    public void testGetHighestHdrSdrRatio_DisplaysSupportingHdr() {
        for (Display display : mDisplays) {
            if (display.isHdrSdrRatioAvailable()) {
                assertTrue(display.getHighestHdrSdrRatio() > 1);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HIGHEST_HDR_SDR_RATIO_API)
    public void testGetHighestHdrSdrRatio_DisplaysNotSupportingHdr() {
        for (Display display : mDisplays) {
            if (!display.isHdrSdrRatioAvailable()) {
                assertEquals(1, display.getHighestHdrSdrRatio(), /* delta= */ 0);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HIGHEST_HDR_SDR_RATIO_API)
    @Parameters({"0.0001", "0.001", "0.01", "0.1", "0.5", "1.0"})
    public void testGetHighestHdrSdrRatio_HdrContentOnScreen(float brightness) {
        runShellCommand("cmd display set-brightness " + brightness);
        launchActivity(mHdrContentActivity);
        for (Display display : mDisplays) {
            if (display.isHdrSdrRatioAvailable()) {
                assertTrue(display.getHdrSdrRatio() <= display.getHighestHdrSdrRatio());
            }
        }
    }
}
