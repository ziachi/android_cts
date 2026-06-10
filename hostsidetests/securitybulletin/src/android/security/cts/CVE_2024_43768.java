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
import static com.android.sts.common.NativePocStatusAsserter.assertNotVulnerableExitCode;

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.Ghidra;
import com.android.sts.common.NativePoc;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2024_43768 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 349678452)
    public void testPocCVE_2024_43768() {
        try {
            // Get function offsets.
            final String libraryPath = "/system/lib/libhwui.so";
            final List<String> functionNames =
                    List.of("_ZN12_GLOBAL__N_115skia_alloc_funcIjEEPvS1_T_S2_.*");
            String functionOffsets =
                    getFunctionOffsetsAsCmdLineArgs(
                            new Ghidra(this), new File(libraryPath), functionNames);

            // Check for vulnerability
            final String binaryName = "CVE-2024-43768";
            NativePoc.builder()
                    .pocName(binaryName)
                    .bitness(ONLY32)
                    .args(libraryPath, functionOffsets)
                    .asserter(assertNotVulnerableExitCode())
                    .build()
                    .run(this);
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
