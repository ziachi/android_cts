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
public class NonRecoverableTest extends GwpAsanBaseTest {
    private static final String RECOVERABLE_SYSPROP = "libc.debug.gwp_asan.recoverable.";

    protected String getTestApk() {
        return "CtsGwpAsanEnabled.apk";
    }

    protected String getTestNameSuffix() {
        return "NonRecoverable";
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        // Android 15+ uses recoverable GWP-ASan for all apps. This test suite expects crashes, so
        // let's disable the recovery.
        mDevice.setProperty(RECOVERABLE_SYSPROP + TEST_PKG, "false");
        mDevice.setProperty(RECOVERABLE_SYSPROP + TEST_PKG + ":gwp_asan_enabled", "false");
        mDevice.setProperty(RECOVERABLE_SYSPROP + TEST_PKG + ":gwp_asan_default", "false");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mDevice.setProperty(RECOVERABLE_SYSPROP + TEST_PKG, "");
        mDevice.setProperty(RECOVERABLE_SYSPROP + TEST_PKG + ":gwp_asan_enabled", "");
        mDevice.setProperty(RECOVERABLE_SYSPROP + TEST_PKG + ":gwp_asan_default", "");
    }
}
