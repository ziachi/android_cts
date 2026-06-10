/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.mediav2.cts;

import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.compatibility.common.util.ModuleSpecificTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "No interaction with system server")
@RunWith(AndroidJUnit4.class)
public class Stub {
    private static final String TAG = "Stub";

    @Test
    @FrameworkSpecificTest
    public void testFrameworkSpecificStub() throws Exception {
        Log.d(TAG, "FrameworkSpecific tests are non-empty");
    }

    @Test
    @ModuleSpecificTest
    public void testModuleSpecificStub() throws Exception {
        Log.d(TAG, "ModuleSpecific tests are non-empty");
    }

}

