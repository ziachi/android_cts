/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.security.cts;

import static com.android.sts.common.GhidraFunctionOffsets.getFunctionOffsetsAsCmdLineArgs;
import static com.android.sts.common.NativePoc.Bitness.ONLY32;
import static com.android.sts.common.NativePoc.Bitness.ONLY64;
import static com.android.sts.common.NativePocCrashAsserter.assertNoCrash;

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.Ghidra;
import com.android.sts.common.NativePoc;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.sts.common.util.TombstoneUtils;
import com.android.sts.common.util.TombstoneUtils.Config.BacktraceFilterPattern;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2024_43091 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 344620577)
    public void testPocCVE_2024_43091() {
        try {
            final ITestDevice device = getDevice();
            final String libraryName = "libhwui.so";
            final String abi = device.getProperty("ro.product.cpu.abi");
            final String libraryPath =
                    "/system/lib" + (abi.contains("x86_64") || abi.contains("arm64") ? "64" : "");

            // Get function offsets.
            final List<String> functionNames = List.of("_ZNK18SkEmbossMaskFilter10filterMask*");
            String functionOffsets =
                    getFunctionOffsetsAsCmdLineArgs(
                            new Ghidra(this),
                            new File(libraryPath + "/" + libraryName),
                            functionNames);

            // Configure Tombstone
            final String binaryName = "CVE-2024-43091";
            final TombstoneUtils.Config crashConfig =
                    new TombstoneUtils.Config()
                            .setProcessPatterns(binaryName)
                            .setBacktraceIncludes(
                                    new BacktraceFilterPattern("libhwui.so", "filterMask"));

            // Check for vulnerability
            NativePoc.builder()
                    .pocName(binaryName)
                    .bitness(libraryPath.contains("lib64") ? ONLY64 : ONLY32)
                    .args(libraryPath + "/" + libraryName, functionOffsets)
                    .asserter(assertNoCrash(crashConfig))
                    .build()
                    .run(this);
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
