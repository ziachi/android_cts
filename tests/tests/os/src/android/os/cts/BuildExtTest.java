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

package android.os.cts;

import static android.os.cts.BuildTest.runTestCpuAbi32;
import static android.os.cts.BuildTest.runTestCpuAbi64;
import static android.os.cts.BuildTest.runTestCpuAbiCommon;

import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;

import org.junit.Rule;
import org.junit.Test;

/**
 * CTS for the {@link Build} class.
 *
 * This class contains tests that do require a {@link RavenwoodRule}. See {@link BuildTest}
 * for more details.
 */
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class BuildExtTest {
    @Rule
    public RavenwoodRule mRavenwood = new RavenwoodRule();

    /**
     * Verify that the values of the various CPU ABI fields are consistent.
     */
    @Test
    @AppModeFull(reason = "Instant apps cannot access APIs")
    @DisabledOnRavenwood(reason = "No shell commands")
    public void testCpuAbi() throws Exception {
        runTestCpuAbiCommon();
        if (android.os.Process.is64Bit()) {
            runTestCpuAbi64();
        } else {
            runTestCpuAbi32();
        }
    }
}
