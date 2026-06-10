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

package android.telephony.cts;

import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RADIO_POWER;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.TelephonyManager;
import android.telephony.mockmodem.MockModemManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

/**
 * Base class for test cases performed on MockModem.
 */
public class MockModemTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "MockModemTestBase";
    private static final String RESOURCE_PACKAGE_NAME = "android";

    protected static final boolean VDBG = true;

    protected static MockModemManager sMockModemManager;
    protected static TelephonyManager sTelephonyManager;
    protected static boolean sIsMultiSimDevice;

    protected static boolean beforeAllTestsCheck() throws Exception {
        if (VDBG) Log.d(TAG, "beforeAllTests()");

        if (!hasTelephonyFeature()) {
            Log.d(TAG, "Skipping test that requires FEATURE_TELEPHONY");
            return false;
        }
        MockModemManager.enforceMockModemDeveloperSetting();
        sTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        sIsMultiSimDevice = isMultiSim(sTelephonyManager);

        return true;
    }

    protected static void createMockModemAndConnectToService() throws Exception {
        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService());
    }

    protected static boolean afterAllTestsBase() throws Exception {
        if (VDBG) Log.d(TAG, "afterAllTestsBase()");

        if (!hasTelephonyFeature()) {
            return false;
        }

        // Rebind all interfaces which is binding to MockModemService to default.
        assertNotNull(sMockModemManager);
        // Reset the modified error response of RIL_REQUEST_RADIO_POWER to the original behavior
        // and -1 means to disable the modifed mechanism in mock modem
        sMockModemManager.forceErrorResponse(0, RIL_REQUEST_RADIO_POWER, -1);
        assertTrue(sMockModemManager.disconnectMockModemService());
        sMockModemManager = null;

        return true;
    }

    @Before
    public void beforeTest() {
        if (VDBG) Log.d(TAG, "beforeTest");
        assumeTrue(hasTelephonyFeature());
    }

    @After
    public void afterTest() {
        if (VDBG) Log.d(TAG, "afterTest");
    }

    protected static boolean hasTelephonyFeature() {
        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return false;
        }
        return true;
    }

    protected static boolean hasTelephonyFeature(String featureName) {
        final PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(featureName)) {
            return false;
        }
        return true;
    }

    protected static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    protected static boolean isMultiSim(TelephonyManager tm) {
        return tm != null && tm.getPhoneCount() > 1;
    }

    protected static boolean isSimHotSwapCapable() {
        boolean isSimHotSwapCapable = false;
        int resourceId =
                getContext()
                        .getResources()
                        .getIdentifier("config_hotswapCapable", "bool", RESOURCE_PACKAGE_NAME);

        if (resourceId > 0) {
            isSimHotSwapCapable = getContext().getResources().getBoolean(resourceId);
        } else {
            Log.d(TAG, "Fail to get the resource Id, using default.");
        }

        Log.d(TAG, "isSimHotSwapCapable = " + (isSimHotSwapCapable ? "true" : "false"));

        return isSimHotSwapCapable;
    }
}
