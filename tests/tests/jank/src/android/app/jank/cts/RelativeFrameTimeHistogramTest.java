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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.jank.RelativeFrameTimeHistogram;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.core.app.ActivityScenario;
import androidx.test.runner.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class RelativeFrameTimeHistogramTest {

    private static ActivityScenario<BasicActivity> sEmptyActivityActivityScenario;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @BeforeClass
    public static void classSetup() {
        sEmptyActivityActivityScenario = ActivityScenario.launch(BasicActivity.class);
    }

    @AfterClass
    public static void classTearDown() {
        sEmptyActivityActivityScenario.close();
    }

    @Test
    @RequiresFlagsEnabled(android.app.jank.Flags.FLAG_DETAILED_APP_JANK_METRICS_API)
    public void addFrameTime_inputExceedsMaxBucket_PlaceInMaxBucket() {
        RelativeFrameTimeHistogram relativeFrameTimeHistogram = new RelativeFrameTimeHistogram();
        int[] counterBuckets = relativeFrameTimeHistogram.getBucketCounters();
        assertEquals(0, counterBuckets[counterBuckets.length - 1]);

        // 1000 ms is the highest expected relative frame time, anything higher than that is added
        // to the Int.MAX bucket.
        relativeFrameTimeHistogram.addRelativeFrameTimeMillis(100000);
        counterBuckets = relativeFrameTimeHistogram.getBucketCounters();

        assertEquals(1, counterBuckets[counterBuckets.length - 1]);
    }

    @Test
    @RequiresFlagsEnabled(android.app.jank.Flags.FLAG_DETAILED_APP_JANK_METRICS_API)
    public void addFrameTime_inputLessThanMinBucket_PlacedInMinBucket() {
        RelativeFrameTimeHistogram relativeFrameTimeHistogram = new RelativeFrameTimeHistogram();
        int[] counterBuckets = relativeFrameTimeHistogram.getBucketCounters();

        assertEquals(0, counterBuckets[0]);

        // -200 is the lowest expected relative frame time, anything lower than that should be
        // placed in the Int.MIN bucket.
        relativeFrameTimeHistogram.addRelativeFrameTimeMillis(-100000);
        counterBuckets = relativeFrameTimeHistogram.getBucketCounters();

        assertEquals(1, counterBuckets[0]);
    }

    @Test
    @RequiresFlagsEnabled(android.app.jank.Flags.FLAG_DETAILED_APP_JANK_METRICS_API)
    public void addFrameTime_countsPlacedInCorrectBuckets() {
        RelativeFrameTimeHistogram relativeFrameTimeHistogram = new RelativeFrameTimeHistogram();
        int[] bucketBoundaries = relativeFrameTimeHistogram.getBucketEndpointsMillis();

        // exclude the endpoint for INT.MAX_VALUE
        for (int i = 0; i < bucketBoundaries.length - 1; i++) {
            relativeFrameTimeHistogram.addRelativeFrameTimeMillis(bucketBoundaries[i]);
        }
        int[] counterBuckets = relativeFrameTimeHistogram.getBucketCounters();

        assertTrue(Arrays.stream(counterBuckets).allMatch(value -> value == 1));
    }

    @Test
    @RequiresFlagsEnabled(android.app.jank.Flags.FLAG_DETAILED_APP_JANK_METRICS_API)
    public void relativeFrameTimeHistogram_bucketsReturnedAreCopies() {
        RelativeFrameTimeHistogram relativeFrameTimeHistogram = new RelativeFrameTimeHistogram();

        int[] bucketEndpointsInitial = relativeFrameTimeHistogram.getBucketEndpointsMillis();
        int[] bucketEndpointsSecond = relativeFrameTimeHistogram.getBucketEndpointsMillis();

        assertFalse(bucketEndpointsSecond == bucketEndpointsInitial);
    }
}
