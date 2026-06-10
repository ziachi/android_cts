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

import static android.telephony.CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI;
import static android.telephony.CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST;
import static android.telephony.SecurityAlgorithmUpdate.CONNECTION_EVENT_CS_SIGNALLING_GSM;
import static android.telephony.SecurityAlgorithmUpdate.SECURITY_ALGORITHM_A50;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_US_FI;

import static androidx.test.InstrumentationRegistry.getContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.CellularIdentifierDisclosure;
import android.telephony.Rlog;
import android.telephony.SecurityAlgorithmUpdate;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.mockmodem.MockModemManager;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class CellularSecurityCallbackTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    public static final long WAIT_TIME = 1000;

    private static final int SLOT_ID_0 = 0;
    private static final String TAG = "CellularSecurityTelephonyCallbackTest";
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static MockModemManager sMockModemManager;
    private static TelephonyManager sTelephonyManager;

    private final Executor mSimpleExecutor = Runnable::run;
    private final Object mLock = new Object();
    private boolean mOnSecurityAlgorithmsChangedCalled;
    private boolean mOnCellularIdentifierDisclosedChangedCalled;
    private PackageManager mPackageManager;

    @Before
    public void setUp() throws Exception {
        mPackageManager = getContext().getPackageManager();
        assumeTrue("Skipping test that requires FEATURE_TELEPHONY",
                mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));

        sTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        try {
            sTelephonyManager.getHalVersion(TelephonyManager.HAL_SERVICE_RADIO);
        } catch (IllegalStateException e) {
            assumeNoException("Skipping tests because Telephony service is null", e);
        }

        MockModemManager.enforceMockModemDeveloperSetting();
        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService());
    }

    /**
     * Cleanup resources after all tests.
     * @throws Exception exception
     */
    @AfterClass
    public static void afterAllTests() throws Exception {
        logd(TAG, "afterAllTests");
        sTelephonyManager = null;
        if (sMockModemManager != null) {
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_US_FI, false);
            assertTrue(sMockModemManager.disconnectMockModemService());
            sMockModemManager = null;
        }
    }

    private void registerTelephonyCallbackWithPermission(@NonNull TelephonyCallback callback) {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                (tm) -> tm.registerTelephonyCallback(mSimpleExecutor, callback));
    }

    private void unRegisterTelephonyCallback(boolean condition,
                                             @NonNull TelephonyCallback callback) throws Exception {
        synchronized (mLock) {
            condition = false;
            sTelephonyManager.unregisterTelephonyCallback(callback);
            mLock.wait(WAIT_TIME);

            assertFalse(condition);
        }
    }

    private static void logd(@NonNull String tag, @NonNull String log) {
        Rlog.d(tag, log);
    }

    private SecurityAlgorithmsListener mSecurityAlgorithmsListener;

    private class SecurityAlgorithmsListener extends TelephonyCallback
            implements TelephonyCallback.SecurityAlgorithmsListener {
        @Override
        public void onSecurityAlgorithmsChanged(SecurityAlgorithmUpdate update) {
            synchronized (mLock) {
                mOnSecurityAlgorithmsChangedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SECURITY_ALGORITHMS_UPDATE_INDICATIONS)
    @AppModeNonSdkSandbox(reason = "SDK sandboxes do not have permissions to register the callback")
    public void testOnSecurityAlgorithmsChangedListener() throws Throwable {
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));
        // Inserting a SIM is necessary otherwise mockmodem will crash
        assertTrue(sMockModemManager.insertSimCard(SLOT_ID_0, MOCK_SIM_PROFILE_ID_US_FI));
        // Timeout required after inserting a SIM to prevent the test from flakiness
        TimeUnit.MILLISECONDS.sleep(TIMEOUT);
        // Enter service ...
        sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_US_FI, true);

        assertFalse(mOnSecurityAlgorithmsChangedCalled);
        mSecurityAlgorithmsListener = new SecurityAlgorithmsListener();

        registerTelephonyCallbackWithPermission(mSecurityAlgorithmsListener);
        SecurityAlgorithmUpdate update = new SecurityAlgorithmUpdate(
                CONNECTION_EVENT_CS_SIGNALLING_GSM,
                SECURITY_ALGORITHM_A50,
                SECURITY_ALGORITHM_A50,
                false);

        sMockModemManager.unsolSecurityAlgorithmsUpdated(SLOT_ID_0, update);
        synchronized (mLock) {
            while (!mOnSecurityAlgorithmsChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }
        assertTrue(mOnSecurityAlgorithmsChangedCalled);

        // Leave service
        sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_US_FI, false);
        // Remove the SIM
        assertTrue(sMockModemManager.removeSimCard(SLOT_ID_0));
        // Unregister callback
        unRegisterTelephonyCallback(mOnSecurityAlgorithmsChangedCalled,
                mSecurityAlgorithmsListener);
    }

    private CellularIdentifierDisclosedListener mCellularIdentifierDisclosedListener;

    private class CellularIdentifierDisclosedListener extends TelephonyCallback
            implements TelephonyCallback.CellularIdentifierDisclosedListener {
        @Override
        public void onCellularIdentifierDisclosedChanged(CellularIdentifierDisclosure disclosure) {
            synchronized (mLock) {
                mOnCellularIdentifierDisclosedChangedCalled = true;
                mLock.notify();
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CELLULAR_IDENTIFIER_DISCLOSURE_INDICATIONS)
    @AppModeNonSdkSandbox(reason = "SDK sandboxes do not have permissions to register the callback")
    public void testOnCellularIdentifierDisclosedChangedListener() throws Throwable {
        assumeTrue(mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS));

        // Inserting a SIM is necessary otherwise mockmodem will crash
        assertTrue(sMockModemManager.insertSimCard(SLOT_ID_0, MOCK_SIM_PROFILE_ID_US_FI));
        // Timeout required after inserting a SIM to prevent the test from flakiness
        TimeUnit.MILLISECONDS.sleep(TIMEOUT);
        // Enter service ...

        assertFalse(mOnCellularIdentifierDisclosedChangedCalled);
        mCellularIdentifierDisclosedListener = new CellularIdentifierDisclosedListener();
        registerTelephonyCallbackWithPermission(mCellularIdentifierDisclosedListener);

        // Create Identifier here
        CellularIdentifierDisclosure disclosure = new CellularIdentifierDisclosure(
                NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                CELLULAR_IDENTIFIER_IMSI,
                "001001",
                false);

        sMockModemManager.unsolCellularIdentifierDisclosed(SLOT_ID_0, disclosure);
        synchronized (mLock) {
            while (!mOnCellularIdentifierDisclosedChangedCalled) {
                mLock.wait(WAIT_TIME);
            }
        }
        assertTrue(mOnCellularIdentifierDisclosedChangedCalled);

        // Leave service
        sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_US_FI, false);
        // Remove the SIM
        assertTrue(sMockModemManager.removeSimCard(SLOT_ID_0));
        // Unregister callback
        unRegisterTelephonyCallback(mOnCellularIdentifierDisclosedChangedCalled,
                mCellularIdentifierDisclosedListener);
    }
}

