/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.seccomp.cts;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that collects test results from test package android.seccomp.cts.app.
 *
 * When this test builds, it also builds a support APK containing
 * {@link android.seccomp.cts.app.SeccompDeviceTest}, the results of which are
 * collected from the hostside and reported accordingly.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class SeccompHostJUnit4DeviceTest extends BaseHostJUnit4Test {

    private static final String TEST_PKG = "android.seccomp.cts.app";
    private static final String TEST_CLASS = TEST_PKG + "." + "SeccompDeviceTest";
    private static final String TEST_APP = "CtsSeccompDeviceApp.apk";

    private static final String TEST_CTS_SYSCALL_BLOCKED = "testCTSSyscallBlocked";
    private static final String TEST_CTS_SYSCALL_ALLOWED = "testCTSSyscallAllowed";
    private static final String TEST_CTS_SYSCALL_APP_ZYGOTE = "testAppZygoteSyscalls";

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testCTSSyscallBlocked() throws Exception {
        Assert.assertTrue(runDeviceTests(TEST_PKG, TEST_CLASS, TEST_CTS_SYSCALL_BLOCKED));
    }

    @Test
    public void testCTSSyscallAllowed() throws Exception {
        Assert.assertTrue(runDeviceTests(TEST_PKG, TEST_CLASS, TEST_CTS_SYSCALL_ALLOWED));
    }

    @Test
    public void testAppZygoteSyscalls() throws Exception {
        // To speed up this test, the app zygote preload code repeatedly forks and
        // tries to execute a system call that is either allowed or denied by seccomp;
        // the resulting crashes will not bring down the app zygote itself, but the
        // overall time to complete the test can take several minutes on slow devices;
        // this means that the app zygote preload phase will be blocked for all this time,
        // preventing other stuff from starting.
        // Extend the timeout for the test.
        try {
            getDevice().executeShellCommand("am set-app-zygote-preload-timeout 500000");
            Assert.assertTrue(runDeviceTests(TEST_PKG, TEST_CLASS, TEST_CTS_SYSCALL_APP_ZYGOTE));
        } finally {
            getDevice().executeShellCommand("am set-app-zygote-preload-timeout 15000");
        }
    }

    @After
    public void tearDown() throws Exception {
    }

}
