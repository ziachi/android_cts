/*
 * Copyright 2024 The Android Open Source Project
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

package android.hardware.cts;

import static android.hardware.flags.Flags.FLAG_LUTS_API;

import static org.junit.Assert.assertTrue;

import android.hardware.DisplayLuts;
import android.hardware.LutProperties;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@ApiTest(apis = {"android.hardware.DisplayLuts#set"})
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DisplayLutsTest {
    private static final String TAG = "DisplayLutsTest";
    private DisplayLuts mDisplayLuts;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @After
    public void tearDown() throws Throwable {
        mDisplayLuts = null;
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LUTS_API)
    public void addOneDimensionLut() {
        mDisplayLuts = new DisplayLuts();
        DisplayLuts.Entry entry = new DisplayLuts.Entry(
                new float[]{1, 1, 1, 1, 1, 1, 1}, LutProperties.ONE_DIMENSION,
                LutProperties.SAMPLING_KEY_RGB);
        mDisplayLuts.set(entry);
    }

    @Test(expected = IllegalArgumentException.class)
    @RequiresFlagsEnabled(FLAG_LUTS_API)
    public void addEmpty1DLut() {
        mDisplayLuts = new DisplayLuts();
        DisplayLuts.Entry entry = new DisplayLuts.Entry(
                null,
                LutProperties.ONE_DIMENSION,
                LutProperties.SAMPLING_KEY_RGB);
        mDisplayLuts.set(entry);
    }

    @Test(expected = IllegalArgumentException.class)
    @RequiresFlagsEnabled(FLAG_LUTS_API)
    public void addTooLargeLut() {
        mDisplayLuts = new DisplayLuts();
        DisplayLuts.Entry entry = new DisplayLuts.Entry(
                new float[100001],
                LutProperties.ONE_DIMENSION,
                LutProperties.SAMPLING_KEY_RGB);
        mDisplayLuts.set(entry);
    }

    @Test(expected = IllegalArgumentException.class)
    @RequiresFlagsEnabled(FLAG_LUTS_API)
    public void addTooSmall3DLut() {
        mDisplayLuts = new DisplayLuts();
        DisplayLuts.Entry entry = new DisplayLuts.Entry(
                new float[]{1, 1, 1},
                LutProperties.THREE_DIMENSION,
                LutProperties.SAMPLING_KEY_RGB);
        mDisplayLuts.set(entry);
    }

    @Test(expected = IllegalArgumentException.class)
    @RequiresFlagsEnabled(FLAG_LUTS_API)
    public void addLutsWrongOrder() {
        mDisplayLuts = new DisplayLuts();
        DisplayLuts.Entry entry1 = new DisplayLuts.Entry(
                new float[]{1, 1, 1, 1, 1, 1, 1, 1,
                            1, 1, 1, 1, 1, 1, 1, 1,
                            1, 1, 1, 1, 1, 1, 1, 1},
                LutProperties.THREE_DIMENSION,
                LutProperties.SAMPLING_KEY_RGB);
        DisplayLuts.Entry entry2 = new DisplayLuts.Entry(
                    new float[]{1, 1, 1, 1, 1, 1, 1},
                    LutProperties.ONE_DIMENSION,
                    LutProperties.SAMPLING_KEY_MAX_RGB);
        mDisplayLuts.set(entry1, entry2);
    }

    @Test(expected = IllegalArgumentException.class)
    @RequiresFlagsEnabled(FLAG_LUTS_API)
    public void addLutsWrong3DLuts() {
        mDisplayLuts = new DisplayLuts();
        DisplayLuts.Entry entry = new DisplayLuts.Entry(
                new float[]{1, 1, 1, 1, 1, 1, 1,
                            1, 1, 1, 1, 1, 1, 1,
                            1, 1, 1, 1, 1, 1, 1},
                LutProperties.THREE_DIMENSION,
                LutProperties.SAMPLING_KEY_RGB);
        mDisplayLuts.set(entry);
    }

    @RequiresFlagsEnabled(FLAG_LUTS_API)
    public void addMultipleLuts() {
        mDisplayLuts = new DisplayLuts();
        float[] buffer = new float[] {1, 1, 1, 1, 1, 1, 1, 1};
        DisplayLuts.Entry entry1 =
                new DisplayLuts.Entry(
                        buffer, LutProperties.ONE_DIMENSION, LutProperties.SAMPLING_KEY_RGB);
        DisplayLuts.Entry entry2 =
                new DisplayLuts.Entry(
                        buffer, LutProperties.ONE_DIMENSION, LutProperties.SAMPLING_KEY_MAX_RGB);
        mDisplayLuts.set(entry1);
        mDisplayLuts.set(entry2);
        assertTrue(entry1.getSamplingKey() == LutProperties.SAMPLING_KEY_RGB);
        assertTrue(entry1.getDimension() == LutProperties.ONE_DIMENSION);
        assertTrue(entry1.getBuffer() == buffer);
        assertTrue(entry2.getSamplingKey() == LutProperties.SAMPLING_KEY_MAX_RGB);
    }
}
