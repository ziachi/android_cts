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

package android.resources.cts;

import android.content.res.Flags;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class CtsHandleConfigChangeHostTests extends BaseHostJUnit4Test {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);
    private static final String DEVICE_TEST_PKG1 = "android.resources.cts.overlayresapp";
    private static final String DEVICE_TEST_PKG2 = "android.resources.cts.overlayresapp2";
    private static final String DEVICE_TEST_CLASS = "OverlayResTest";

    private static final String FRAMEWORK_RRO_APK = "CtsOverlayFrameworkResRRO.apk";

    private void installFrameworkRroOrSkip() {
        try {
            installPackage(FRAMEWORK_RRO_APK);
        } catch (Exception e) {
            Assume.assumeNoException("Can't install the framework overlay", e);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_ALL_CONFIG_CHANGES)
    public void testOverlayRes() throws Exception {
        runDeviceTests(DEVICE_TEST_PKG1, DEVICE_TEST_PKG1 + "." + DEVICE_TEST_CLASS,
                "overlayRes_onConfigurationChanged");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_ALL_CONFIG_CHANGES)
    public void testOverlayFrameworkRes() throws Exception {
        installFrameworkRroOrSkip();
        runDeviceTests(DEVICE_TEST_PKG1, DEVICE_TEST_PKG1 + "." + DEVICE_TEST_CLASS,
                "overlayFrameworkRes_onConfigurationChanged");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_ALL_CONFIG_CHANGES)
    public void testOverlayFullLayout() throws Exception {
        runDeviceTests(DEVICE_TEST_PKG1, DEVICE_TEST_PKG1 + "." + DEVICE_TEST_CLASS,
                "overlayFullLayout_onConfigurationChanged");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_ALL_CONFIG_CHANGES)
    public void testOverlayLayoutRes() throws Exception {
        runDeviceTests(DEVICE_TEST_PKG1, DEVICE_TEST_PKG1 + "." + DEVICE_TEST_CLASS,
                "overlayLayoutRes_onConfigurationChanged");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_ALL_CONFIG_CHANGES)
    public void testOverlayLayoutFrameworkRes() throws Exception {
        installFrameworkRroOrSkip();
        runDeviceTests(DEVICE_TEST_PKG1, DEVICE_TEST_PKG1 + "." + DEVICE_TEST_CLASS,
                "overlayLayoutFrameworkRes_onConfigurationChanged");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HANDLE_ALL_CONFIG_CHANGES)
    public void testOverlayRes2() throws Exception {
        runDeviceTests(DEVICE_TEST_PKG2, DEVICE_TEST_PKG2 + "." + DEVICE_TEST_CLASS,
                "overlayRes_onConfigurationChanged");
    }

}
