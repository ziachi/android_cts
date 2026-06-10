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

package android.credentials.cts;

import static android.credentials.cts.testcore.CtsCredentialManagerUtils.enableCredentialManagerDeviceConfigFlag;
import static android.credentials.cts.testcore.CtsCredentialManagerUtils.isWatch;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.credentials.CredentialManager;
import android.credentials.cts.testcore.CtsCredentialManagerUtils;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.core.os.BuildCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class CtsCredentialManagerDeviceTest {
    private static final String TAG = "CtsCredentialManagerDeviceTest";

    private CredentialManager mCredentialManager;
    private final Context mContext = getInstrumentation().getContext();

    @BeforeClass
    public static void setUpClass() {
        Log.i(TAG, "Skipping all tests in the file if we are not on the right SDK level...");
        assumeTrue("VERSION.SDK_INT=" + Build.VERSION.SDK_INT, BuildCompat.isAtLeastU());
    }

    @Before
    public void setUpTest() {
        Log.i(TAG, "Skipping all tests in the file if we are not on the right device type...");
        assumeFalse("Skipping tests: Wear does not support CredentialManager yet",
                isWatch(mContext));
        assumeFalse("Skipping test: Auto does not support CredentialManager yet",
                CtsCredentialManagerUtils.isAuto(mContext));
        assumeFalse("Skipping test: Tv does not support CredentialManager yet",
                CtsCredentialManagerUtils.isTv(mContext));

        Log.i(TAG, "Enabling CredentialManager flags as well...");
        enableCredentialManagerDeviceConfigFlag(mContext);

        mCredentialManager = mContext.getSystemService(CredentialManager.class);
    }

    @CddTest(requirements = {"9.8.14"})
    @Test
    public void testCredentialManager_shouldExist() {
        assertThat(mCredentialManager).isNotNull();
    }
}
