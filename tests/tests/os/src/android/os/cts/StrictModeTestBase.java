/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.os.StrictMode;
import android.os.StrictMode.ViolationInfo;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Test base class for {@link StrictMode} */
public class StrictModeTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int VIOLATION_TIMEOUT_IN_SECOND = 5;
    private static final int NO_VIOLATION_TIMEOUT_IN_SECOND = 2;

    private StrictMode.ThreadPolicy mThreadPolicy;
    private StrictMode.VmPolicy mVmPolicy;

    static Context getContext() {
        return ApplicationProvider.getApplicationContext();
    }

    @Before
    public void setUp() {
        mThreadPolicy = StrictMode.getThreadPolicy();
        mVmPolicy = StrictMode.getVmPolicy();
    }

    @After
    public void tearDown() {
        StrictMode.setThreadPolicy(mThreadPolicy);
        StrictMode.setVmPolicy(mVmPolicy);
    }

    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    static void assertViolation(String expected, StrictModeBalTest.ThrowingRunnable r)
            throws Exception {
        inspectViolation(
                r,
                info -> assertThat(info.getStackTrace()).contains(expected),
                VIOLATION_TIMEOUT_IN_SECOND);
    }

    static void assertNoViolation(StrictModeBalTest.ThrowingRunnable r) throws Exception {
        inspectViolation(
                r,
                info -> assertWithMessage("Unexpected violation").that(info).isNull(),
                NO_VIOLATION_TIMEOUT_IN_SECOND);
    }

    static void inspectViolation(
            StrictModeBalTest.ThrowingRunnable violating, Consumer<ViolationInfo> consume)
            throws Exception {
        inspectViolation(violating, consume, VIOLATION_TIMEOUT_IN_SECOND);
    }

    static void inspectViolation(
            StrictModeBalTest.ThrowingRunnable violating,
            Consumer<ViolationInfo> consume,
            int timeout)
            throws Exception {
        final LinkedBlockingQueue<ViolationInfo> violations = new LinkedBlockingQueue<>();
        StrictMode.setViolationLogger(violations::add);

        try {
            violating.run();
            consume.accept(violations.poll(timeout, TimeUnit.SECONDS));
        } finally {
            StrictMode.setViolationLogger(null);
        }
    }
}
