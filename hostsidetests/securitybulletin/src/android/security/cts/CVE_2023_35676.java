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

package android.security.cts;

import static com.android.sts.common.CommandUtil.runAndCheck;

import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_35676 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 278720336)
    @Test
    public void testPocCVE_2023_35676() {
        try {
            // Install the test app
            installPackage("CVE-2023-35676.apk");

            // Setting 'ContentSuggestionService' as the temporary content suggestion service
            final ITestDevice device = getDevice();
            final String testPkg = "android.security.cts.CVE_2023_35676";
            final CommandResult result =
                    runAndCheck(
                            device,
                            String.format(
                                    "cmd content_suggestions set temporary-service %d"
                                            + " %s/%s.ContentSuggestionsService %d ",
                                    device.getCurrentUser(),
                                    testPkg,
                                    testPkg,
                                    10000 /* Timeout for the temporary service */));
            assume().withMessage(
                            String.format(
                                    "Unable to set Content suggestion service for user. Error: %s ",
                                    result.getStderr()))
                    .that(CommandStatus.SUCCESS.equals(result.getStatus()))
                    .isTrue();

            // Run the test "testCVE_2023_35676"
            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testCVE_2023_35676");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
