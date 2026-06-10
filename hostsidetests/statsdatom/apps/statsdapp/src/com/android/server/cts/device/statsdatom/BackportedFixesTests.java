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

package com.android.server.cts.device.statsdatom;

import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.truth.Truth;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BackportedFixesTests {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_API_FOR_BACKPORTED_FIXES)
    public void getBackportedFixStatus_alwaysFixed() {
        // Known issue 350037023 has an alias of 1
        Truth.assertThat(Build.getBackportedFixStatus(1L)).isEqualTo(
                Build.BACKPORTED_FIX_STATUS_FIXED);
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_API_FOR_BACKPORTED_FIXES)
    public void getBackportedFixStatus_neverFixed() {
        // Known issue 350037348 has an alias of 3
        Truth.assertThat(Build.getBackportedFixStatus(3L)).isEqualTo(
                Build.BACKPORTED_FIX_STATUS_UNKNOWN);
    }


}
