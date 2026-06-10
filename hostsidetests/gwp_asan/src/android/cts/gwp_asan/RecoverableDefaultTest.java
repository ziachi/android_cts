/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.cts.gwp_asan;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class RecoverableDefaultTest extends GwpAsanBaseTest {
    private static final String PROCESS_SAMPLING_SYSPROP = "libc.debug.gwp_asan.process_sampling.";

    protected String getTestApk() {
        return "CtsGwpAsanDefault.apk";
    }

    protected String getTestNameSuffix() {
        return "Recoverable";
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // Recoverable mode uses process sampling, which only enables GWP-ASan a fraction of the
        // time (to preserve system-wide memory overhead). Make sure that our test app doesn't use
        // process sampling, and enables GWP-ASan, when requested, every time. Note: We don't set
        // the property on the ":gwp_asan_disabled" subprocess, because libc will enable GWP-ASan
        // because a GWP-ASan property was overwritten.
        mDevice.setProperty(PROCESS_SAMPLING_SYSPROP + TEST_PKG, "1");
        mDevice.setProperty(PROCESS_SAMPLING_SYSPROP + TEST_PKG + ":gwp_asan_enabled", "1");
        mDevice.setProperty(PROCESS_SAMPLING_SYSPROP + TEST_PKG + ":gwp_asan_default", "1");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mDevice.setProperty(PROCESS_SAMPLING_SYSPROP + TEST_PKG, "");
        mDevice.setProperty(PROCESS_SAMPLING_SYSPROP + TEST_PKG + ":gwp_asan_enabled", "");
        mDevice.setProperty(PROCESS_SAMPLING_SYSPROP + TEST_PKG + ":gwp_asan_default", "");
    }
}
