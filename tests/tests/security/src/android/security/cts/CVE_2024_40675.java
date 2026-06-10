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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Intent;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URISyntaxException;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_40675 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 318683126)
    public void testPocCVE_2024_40675() {
        try {
            try {
                // The new format for Intent URI is "intent:#Intent;key=value;end"
                // Parse the invalid URI with missing 'end' to reproduce the vulnerability
                Intent.parseUri("endend:#Intent;S.CVE_2024_40675=" /* uri */, 0 /* flag */);
            } catch (URISyntaxException uriSyntaxException) {
                // With fix, a 'URISyntaxException' is raised with a unique message
                if (Pattern.compile("uri end not found" /* regex */, Pattern.CASE_INSENSITIVE)
                        .matcher(uriSyntaxException.getMessage())
                        .find()) {
                    return;
                }
                throw uriSyntaxException;
            }

            // Without fix, the URI gets parsed without raising any exceptions
            assertWithMessage(
                            "Device is vulnerable to b/318683126 !! Malicious Uri can cause"
                                    + " local persistent DoS caused by system services")
                    .fail();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
