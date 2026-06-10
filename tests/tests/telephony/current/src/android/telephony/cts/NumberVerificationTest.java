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

package android.telephony.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.pm.PackageManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.NumberVerificationCallback;
import android.telephony.PhoneNumberRange;
import android.telephony.TelephonyManager;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.internal.telephony.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link TelephonyManager#requestNumberVerification(PhoneNumberRange, long, Executor,
 * NumberVerificationCallback)}. Note that there are also GTS tests that handle this functionality
 * extensively. Those were added prior to the time when we could do tests like this in CTS. New
 * tests should be added here.
 */
@RunWith(JUnit4.class)
public class NumberVerificationTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int TEST_TIMEOUT = 3000;
    private static final String COMMAND_BASE = "cmd phone numverify ";
    private static final String FAKE_CALL = "fake-call ";

    // A sentinel value pushed onto the verified phone number pipe if verification failed.
    private static final String VERIFICATION_FAILED = "verification-failed";
    private static final String VERIFICATION_FAILED_NO_SIM = "verification-failed-no-sim";

    /**
     * Verifies that a UK phone number a carrier reports such as 07445032046 gets matched to a range
     * with the +44 country code.
     *
     * @throws Exception
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ROBUST_NUMBER_VERIFICATION)
    public void testNumberVerificationUk() throws Exception {
        // Only run this test on devices with Telephony calling.
        assumeTrue(
                InstrumentationRegistry.getContext()
                        .getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING));

        TelephonyManager tm =
                InstrumentationRegistry.getContext().getSystemService(TelephonyManager.class);

        PhoneNumberRange testRange = new PhoneNumberRange("44", "7445", "000000", "999999");

        LinkedBlockingQueue<String> phoneNumberPipe = new LinkedBlockingQueue<>();
        // This is kinda janky; when we call requestVerification, the callback is going to stick
        // around past the end of this test.  This means it is possible for the callback to trigger
        // during subsequent test runs.  Clearing the override package at the end of the test will
        // likely mitigate that, but just to be safe we'll make sure to disable the callback when we
        // are done using it.
        boolean[] running = new boolean[1];
        running[0] = true;
        String testNumber = "07445032046";

        NumberVerificationCallback callback =
                new NumberVerificationCallback() {
                    @Override
                    public void onCallReceived(String phoneNumber) {
                        if (running[0]) {
                            phoneNumberPipe.offer(phoneNumber);
                            running[0] = false;
                        }
                    }

                    @Override
                    public void onVerificationFailed(int reason) {
                        if (running[0]) {
                            if (reason == NumberVerificationCallback.REASON_NETWORK_NOT_AVAILABLE) {
                                // Offer up sentinel because if we call fail() here, we could crash
                                // the test runner.
                                phoneNumberPipe.offer(VERIFICATION_FAILED_NO_SIM);
                            } else {
                                // Offer up sentinel because if we call fail() here, we could crash
                                // the test runner.
                                phoneNumberPipe.offer(VERIFICATION_FAILED);
                            }
                            running[0] = false;
                        }
                    }
                };

        try {
            // Make sure we can request validation from the cts app.
            SystemUtil.runShellCommand(
                    InstrumentationRegistry.getInstrumentation(),
                    COMMAND_BASE
                            + "override-package "
                            + NumberVerificationTest.class.getPackageName());

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    tm,
                    (dtm) ->
                            dtm.requestNumberVerification(
                                    testRange,
                                    TEST_TIMEOUT,
                                    InstrumentationRegistry.getInstrumentation()
                                            .getTargetContext()
                                            .getMainExecutor(),
                                    callback),
                    android.Manifest.permission.MODIFY_PHONE_STATE);
            SystemUtil.runShellCommand(
                    InstrumentationRegistry.getInstrumentation(),
                    COMMAND_BASE + FAKE_CALL + testNumber + " GB");
            String receivedNumber = null;
            try {
                receivedNumber = phoneNumberPipe.poll(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                running[0] = false;
            }
            if (VERIFICATION_FAILED_NO_SIM.equals(receivedNumber)) {
                fail("This test must be run with a voice-capable SIM card inserted.");
            } else if (VERIFICATION_FAILED.equals(receivedNumber)) {
                fail("Verification should not fail.");
            }
            assertEquals(testNumber, receivedNumber);
        } finally {
            // Always clear so we're not persisting the cts package as the test verification pkg.
            SystemUtil.runShellCommand(
                    InstrumentationRegistry.getInstrumentation(),
                    COMMAND_BASE + "override-package");
        }
    }
}
