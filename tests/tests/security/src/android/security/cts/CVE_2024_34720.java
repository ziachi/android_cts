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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.net.LocalSocket;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_34720 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 319081336)
    @Test
    public void testPocCVE_2024_34720() {
        try {
            Context context = getApplicationContext();
            Constructor<?> appZygoteConnection =
                    context.getClassLoader()
                            .loadClass("com.android.internal.os.AppZygoteInit$AppZygoteConnection")
                            .getDeclaredConstructor(LocalSocket.class, String.class);
            appZygoteConnection.setAccessible(true);
            try {
                // With fix, a ZygoteSecurityException is thrown since the UID is non system
                appZygoteConnection.newInstance(new LocalSocket(), "cve_2024_34720_abi_list");
            } catch (Exception e) {
                if (e.getCause().toString().contains("ZygoteSecurityException")) {
                    return;
                }
                throw e;
            }

            // Fail the test if instance of ZygoteConnection gets created.
            assertWithMessage(
                            "Device is vulnerable to b/319081336, arbitrary code can be injected"
                                    + " into other app's Zygote.")
                    .fail();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
