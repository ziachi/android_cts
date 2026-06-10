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

package com.android.cts.devicepolicy;

import static com.android.tradefed.util.CommandStatus.SUCCESS;

import static org.junit.Assert.fail;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.concurrent.TimeUnit;

/** Rule to wait for device to become responsive to avoid cascading failures. */
public class DeviceResponsivenessCheckerRule implements TestRule {

    private final BaseDevicePolicyTest mTest;

    public DeviceResponsivenessCheckerRule(BaseDevicePolicyTest test) {
        mTest = test;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                CLog.i("Evaluating");
                boolean responsive = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                while (System.nanoTime() < deadline) {
                    ITestDevice device = mTest.getDevice();
                    if (device == null) {
                        CLog.i("Device is null");
                    } else {
                        CommandResult result = device.executeShellV2Command("pm list features");
                        if (SUCCESS.equals(result.getStatus())) {
                            responsive = true;
                            break;
                        } else {
                            CLog.i("Device is not responsive");
                        }
                    }
                    Thread.sleep(1000); // 1 second
                }

                if (!responsive) {
                    fail("Timed out waiting for a responsive device");
                } else {
                    CLog.i("Device is responsive, proceeding to test...");
                }

                base.evaluate();
            }
        };
    }
}
