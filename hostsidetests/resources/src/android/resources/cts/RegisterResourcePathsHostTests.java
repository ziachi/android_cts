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

package android.resources.cts;

import android.content.res.Flags;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test class testing different scenarios of WebView behavior after WebView registered and
 * initialized within an App.
 * The test methods in this class should be run one-to-one to a separate device side app to ensure
 * we don't run the device tests in the same process (since WebView can only be loaded into a
 * process once - after that it will reuse the same WebView provider). Currently, tradefed does
 * not provide a way of restarting a process for a new test case.
 * When adding a new test here - remember to add the corresponding device-side test to a single
 * separate package.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class RegisterResourcePathsHostTests extends BaseHostJUnit4Test {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);
    private static final String DEVICE_TEST_PKG1 = "android.resources.cts.registerresourcepaths1";
    private static final String DEVICE_TEST_PKG2 = "android.resources.cts.registerresourcepaths2";
    private static final String DEVICE_TEST_CLASS = "RegisterResourcePathsTest";

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REGISTER_RESOURCE_PATHS)
    public void testWebViewInitialization() throws Exception {
        runDeviceTests(DEVICE_TEST_PKG1, DEVICE_TEST_PKG1 + "." + DEVICE_TEST_CLASS,
                "testWebViewInitialization");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_REGISTER_RESOURCE_PATHS)
    public void testWebViewInitializeOnBackGroundThread() throws Exception {
        runDeviceTests(DEVICE_TEST_PKG2, DEVICE_TEST_PKG2 + "." + DEVICE_TEST_CLASS,
                "testWebViewInitializeOnBackGroundThread");
    }

}

