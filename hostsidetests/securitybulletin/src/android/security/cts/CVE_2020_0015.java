/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2020_0015 extends NonRootSecurityTestCase {

    @AppModeFull
    @AsbSecurityTest(cveBugId = 139017101)
    @Test
    public void testPocCVE_2020_0015() {
        try {
            // Install the PoC and invoke 'DeviceTest'.
            final String testPkg = "android.security.cts.CVE_2020_0015";
            installPackage("CVE-2020-0015.apk", "-g");
            final boolean testResult =
                    runDeviceTests(new DeviceTestRunOptions(testPkg).setCheckResults(false));

            // Check if any unexpected issue was observed.
            final TestRunResult testRunResult = getLastDeviceRunResults();
            assume().withMessage(
                            String.format(
                                    "DeviceTest did not run properly. Message: %s",
                                    testRunResult.getRunFailureMessage()))
                    .that(testRunResult.isRunFailure())
                    .isFalse();

            // If 'DeviceTest' executed properly, Pass/Fail the test.
            assertThat(testResult).isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
