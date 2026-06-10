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

import static android.Manifest.permission.MANAGE_COMPANION_DEVICES;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeNoException;

import android.content.Context;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_31318 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 313428840)
    public void testPocCVE_2024_31318() {
        try {
            // Define arguments required to invoke 'shellCommand()' of 'ServiceManager'
            final Context context = getApplicationContext();
            final String mockMacAddress = "00:00:00:00:00:00";
            final String packageName = context.getPackageName();
            final String userId = Integer.toString(context.getUserId());
            final String[] command = {"associate", userId, packageName, mockMacAddress};
            final FileDescriptor fd = new FileInputStream("/dev/null").getFD();

            // Invoke 'shellCommand()' of 'ServiceManager' to reproduce the vulnerability
            // which internally invokes 'onShellCommand()' of 'Binder'
            try {
                runWithShellPermissionIdentity(
                        () -> {
                            ServiceManager.getService(Context.COMPANION_DEVICE_SERVICE)
                                    .shellCommand(
                                            fd /* in */,
                                            fd /* out */,
                                            fd /* err */,
                                            command /* args */,
                                            null /* callback */,
                                            new ResultReceiver(null) /* resultReceiver */);
                        },
                        MANAGE_COMPANION_DEVICES);
            } catch (RuntimeException runtimeException) {
                // With fix, 'onShellCommand()' of 'Binder' is invoked, where the necessary
                // permissions of the caller are checeked which leads to a unique
                // 'SecurityException' being thrown with a unique message.
                final Throwable exception = runtimeException.getCause();
                final String exceptionMessage = exception.getMessage();
                if ((exception instanceof SecurityException)
                        && exceptionMessage != null
                        && exceptionMessage.contains("Shell commands are only callable by ADB")) {
                    return;
                }
                throw runtimeException;
            }

            // Without fix, 'onShellCommand()' of 'Binder' is overridden in
            // 'CompanionDeviceShellCommand', which directly calls internal APIs without permission
            // check. Executing shell command "cmd companiondevice list <userId>" returns output
            // which contains 'packageName' along with 'mockMacAddress'.
            assertWithMessage(
                            "Device is vulnerable to b/313428840 !! Missing permission checks in"
                                    + " CompanionDeviceShellCommand.java")
                    .that(runShellCommand(String.format("cmd companiondevice list %s", userId)))
                    .doesNotContainMatch(
                            Pattern.compile(
                                    String.format("(?=.*%s)(?=.*%s)", packageName, mockMacAddress),
                                    Pattern.CASE_INSENSITIVE));
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
