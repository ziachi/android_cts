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

package android.systemhealth.cts;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests native ASystemHealth APIs.
 */
@RunWith(AndroidJUnit4.class)
public class NativeSystemHealthTest {
    private static final String TAG = "NativeSystemHealthTest";

    private static native String nativeTestGetMaxCpuHeadroomTidsSize();

    private static native String nativeTestGetCpuHeadroomDefault();

    private static native String nativeTestGetCpuHeadroomAverage();

    private static native String nativeTestGetCpuHeadroomCustomWindow();

    private static native String nativeTestGetCpuHeadroomCustomTids();

    private static native String nativeTestGetGpuHeadroomDefault();

    private static native String nativeTestGetGpuHeadroomAverage();

    private static native String nativeTestGetGpuHeadroomCustomWindow();

    private void checkResult(String res, String methodName) {
        if ("Unsupported".equals(res)) {
            assumeTrue(methodName + " is not supported", false);
        }
        if (res != null) {
            fail(res);
        }
    }

    @Before
    public void setUp() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.DEVICE_POWER);
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testGetMaxCpuHeadroomTidsSize() throws Exception {
        String res = nativeTestGetMaxCpuHeadroomTidsSize();
        checkResult(res, "GetMaxCpuHeadroomTidsSize");
    }

    @Test
    public void testGetCpuHeadroom_default() throws Exception {
        String res = nativeTestGetCpuHeadroomDefault();
        checkResult(res, "GetCpuHeadroom_default");
    }

    @Test
    public void testGetCpuHeadroom_average() throws Exception {
        String res = nativeTestGetCpuHeadroomAverage();
        checkResult(res, "GetCpuHeadroom_average");
    }

    @Test
    public void testGetCpuHeadroom_customWindow() throws Exception {
        String res = nativeTestGetCpuHeadroomCustomWindow();
        checkResult(res, "GetCpuHeadroom_customWindow");
    }

    @Test
    public void testGetCpuHeadroom_customTids() throws Exception {
        String res = nativeTestGetCpuHeadroomCustomTids();
        checkResult(res, "GetCpuHeadroom_customTids");
    }

    @Test
    public void testGetGpuHeadroom_default() throws Exception {
        String res = nativeTestGetGpuHeadroomDefault();
        checkResult(res, "GetGpuHeadroom_default");
    }

    @Test
    public void testGetGpuHeadroom_average() throws Exception {
        String res = nativeTestGetGpuHeadroomAverage();
        checkResult(res, "GetGpuHeadroom_average");
    }

    @Test
    public void testGetGpuHeadroom_customWindow() throws Exception {
        String res = nativeTestGetGpuHeadroomCustomWindow();
        checkResult(res, "GetGpuHeadroom_customWindow");
    }

    static {
        System.loadLibrary("cts_systemhealth_jni");
    }
}
