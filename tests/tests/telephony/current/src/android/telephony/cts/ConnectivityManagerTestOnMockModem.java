/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_FET;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Test MockModemService interfaces. */
public class ConnectivityManagerTestOnMockModem extends MockModemTestBase {
    private static final String TAG = "ConnectivityManagerTestOnMockModem";
    private static final int TIMEOUT_NETWORK_VALIDATION = 20000;
    private static final int TIMEOUT_ACTIVATE_NETWORK = 20000;
    private static final int WAIT_MSEC = 500;
    private static final int NETWORK_AVAILABLE_SEC = 60;
    private static boolean sIsValidate;
    private static boolean sIsOnAvailable;
    private static Network sDefaultNetwork;
    private static Object sIsValidateLock = new Object();
    private static Object sIsOnAvailableLock = new Object();
    private static CMNetworkCallback sNetworkCallback;
    private static SubscriptionManager sSubscriptionManager;
    private static ConnectivityManager sConnectivityManager;
    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final String BOOT_ALLOW_MOCK_MODEM_PROPERTY = "ro.boot.radio.allow_mock_modem";
    private static final boolean DEBUG = !"user".equals(Build.TYPE);
    private static final String RESOURCE_PACKAGE_NAME = "android";
    @SuppressWarnings("StaticAssignmentOfThrowable")
    private static AssertionError sInitError = null;
    private static final String APN_SETTINGS_URL = "content://telephony/carriers";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_APN = "apn";
    private static final String COLUMN_TYPE = "type";
    private static final String MCC_MNC_TWN_CHT = "46692";
    private static final String MCC_MNC_TWN_FET = "46601";


    private static class CMNetworkCallback extends NetworkCallback {
        final CountDownLatch mNetworkLatch = new CountDownLatch(1);

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
            sDefaultNetwork = network;
            Log.d(
                    TAG,
                    "Network capabilities changed. network: "
                            + network
                            + ", NetworkCapabilities: "
                            + nc);

            if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                Log.d(
                        TAG,
                        "Network capabilities changed. network: "
                                + network
                                + " ,validation: Pass!");
                synchronized (sIsValidateLock) {
                    sIsValidate = true;
                    sIsValidateLock.notify();
                }
            } else {
                Log.d(
                        TAG,
                        "Network capabilities changed. network: "
                                + network
                                + " ,validation: Fail!");
                synchronized (sIsValidateLock) {
                    sIsValidate = false;
                }
            }
        }

        @Override
        public void onLost(Network network) {
            sDefaultNetwork = network;
            Log.d(TAG, "onLost(): network: " + network);
            synchronized (sIsOnAvailableLock) {
                sIsOnAvailable = false;
            }
        }

        @Override
        public void onAvailable(Network network) {
            sDefaultNetwork = network;
            Log.d(TAG, "onAvailable(): network: " + network);
            synchronized (sIsOnAvailableLock) {
                sIsOnAvailable = true;
                mNetworkLatch.countDown();
            }
        }

        public void awaitNetwork() throws InterruptedException {
            Log.d(TAG, "awaitNetwork(): " +  NETWORK_AVAILABLE_SEC + " sec");
            mNetworkLatch.await(NETWORK_AVAILABLE_SEC, TimeUnit.SECONDS);
        }

        @Override
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {}
    }

    @BeforeClass
    @SuppressWarnings("StaticAssignmentOfThrowable")
    public static void beforeAllTests() throws Exception {
        TimeUnit.SECONDS.sleep(10);
        if (!MockModemTestBase.beforeAllTestsCheck()) return;

        sConnectivityManager =
                (ConnectivityManager) getContext().getSystemService(ConnectivityManager.class);

        sSubscriptionManager =
                InstrumentationRegistry.getInstrumentation().getContext()
                        .getSystemService(SubscriptionManager.class);

        registerNetworkCallback();

        Network activeNetwork = sConnectivityManager.getActiveNetwork();
        NetworkCapabilities nc;
        if (activeNetwork == null) {
            sInitError = new AssertionError("This test requires there is an active network. "
                    + "But the active network is null.");
            return;
        }

        nc = sConnectivityManager.getNetworkCapabilities(activeNetwork);

        if (!nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            sInitError = new AssertionError(
                    "This test requires there is a transport type with TRANSPORT_CELLULAR.");
            return;
        }

        if (!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            sInitError = new AssertionError("This test requires there is a network capabilities"
                    + " with NET_CAPABILITY_INTERNET.");
            return;
        }

        if (!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            sInitError = new AssertionError("This test requires there is a network capabilities"
                            + " with NET_CAPABILITY_VALIDATED.");
            return;
        }

        unregisterNetworkCallback();

        MockModemTestBase.createMockModemAndConnectToService();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        MockModemTestBase.afterAllTestsBase();
    }

    @Before
    public void beforeTest() {
        super.beforeTest();
        if (sInitError != null) throw sInitError;
        registerNetworkCallback();
    }

    @After
    public void afterTest() {
        super.afterTest();
        // unregister the network call back
        if (sNetworkCallback != null) {
            unregisterNetworkCallback();
        }
    }


    private static boolean hasApns(String mccmnc) {
        Uri uri = Uri.parse(APN_SETTINGS_URL);

        // Query the database using a ContentResolver
        String[] projection = new String[]{COLUMN_ID, COLUMN_NAME, COLUMN_APN, COLUMN_TYPE};
        String selection = "numeric = ?"; // Filter by mccmnc
        String[] selectionArgs = new String[]{mccmnc}; // Provide the mccmnc as an argument
        int count = 0;
        adoptShellPermissionIdentity();
        try (Cursor cursor =
                InstrumentationRegistry.getInstrumentation().getContext().getContentResolver()
                     .query(uri, projection, selection, selectionArgs, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getCount();
                Log.i(TAG, "Carrier count for " + mccmnc + ": " + count);
                do {
                    int idIndex = cursor.getColumnIndex(COLUMN_ID);
                    int nameIndex = cursor.getColumnIndex(COLUMN_NAME);
                    int typeIndex = cursor.getColumnIndex(COLUMN_TYPE);
                    int apnIndex = cursor.getColumnIndex(COLUMN_APN);

                    if (idIndex != -1 && nameIndex != -1 && typeIndex != -1 && apnIndex != -1) {
                        int id = cursor.getInt(idIndex);
                        String name = cursor.getString(nameIndex);
                        String type = cursor.getString(typeIndex);
                        String apn = cursor.getString(apnIndex);
                        Log.d(TAG, "ID: " + id + ", Name: " + name + ", Apn: "
                                                            + apn + ", Type: " + type);
                    }
                } while (cursor.moveToNext());
            } else {
                Log.i(TAG, "No results found for carrier " + mccmnc);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error querying carriers table: " + mccmnc + ": " + e.getMessage());
        } finally {
            dropShellPermissionIdentity();
        }
        return count > 0;
    }

    /** Allows test app to run as shell UID to acquire privileged permissions */
    public static void adoptShellPermissionIdentity() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();
    }

    /** Disallow test app to run as shell UID to acquire privileged permissions */
    public static void dropShellPermissionIdentity() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private int getRegState(int domain) {
        int reg;

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PHONE_STATE");

        ServiceState ss = sTelephonyManager.getServiceState();
        assertNotNull(ss);

        NetworkRegistrationInfo nri =
                ss.getNetworkRegistrationInfo(domain, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertNotNull(nri);

        reg = nri.getRegistrationState();
        Log.d(TAG, "SS: " + nri.registrationStateToString(reg));

        return reg;
    }

    private static synchronized boolean getNetworkValidated() {
        Log.d(TAG, "getNetworkValidated: " + sIsValidate);
        return sIsValidate;
    }

    private static synchronized boolean getNetworkOnAvailable() {
        Log.d(TAG, "getNetworkOnAvailable: " + sIsOnAvailable);
        return sIsOnAvailable;
    }

    private static synchronized Network getDefaultNetwork() {
        Log.d(TAG, "getDefaultNetwork: enter ");
        return sDefaultNetwork;
    }

    private static void registerNetworkCallback() {
        sNetworkCallback = new CMNetworkCallback();
        try {
            sConnectivityManager.registerNetworkCallback(
                    new NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build(),
                    sNetworkCallback);
            Log.d(TAG, "registered networkCallback");
        } catch (RuntimeException e) {
            Log.e(TAG, "Exception during registerNetworkCallback():" + e);
        }
    }

    private static void unregisterNetworkCallback() {
        try {
            sConnectivityManager.unregisterNetworkCallback(sNetworkCallback);
            Log.d(TAG, "unregisterNetworkCallback");
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException during unregisterNetworkCallback(): ", e);
        } finally {
            sNetworkCallback = null;
        }
    }

    @Test
    @ApiTest(
            apis = {
                "android.net.ConnectivityManager.NetworkCallback#onCapabilitiesChanged",
                "android.net.ConnectivityManager.NetworkCallback#onAvailable"
            })
    public void testNetworkValidated() throws Throwable {
        Log.d(TAG, "ConnectivityManagerTestOnMockModem#testNetworkValidated");
        assumeTrue(hasApns(MCC_MNC_TWN_CHT));
        assumeTrue(isSimHotSwapCapable());

        int slotId = 0;

        // Insert a SIM
        sMockModemManager.insertSimCard(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT);

        // Enter Service
        Log.d(TAG, "testNetworkValidated: Enter Service");
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, true);

        // Get the list of available subscriptions
        List<SubscriptionInfo> subscriptionInfoList =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        sSubscriptionManager, (sm) -> sm.getActiveSubscriptionInfoList());

        Log.d(TAG, "subscriptionInfoList: " + subscriptionInfoList);

        for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity("android.permission.MODIFY_PHONE_STATE");
            if (slotId == subscriptionInfo.getSimSlotIndex()) {
                sSubscriptionManager.setDefaultDataSubId(subscriptionInfo.getSubscriptionId());
                sTelephonyManager.setDataEnabled(subscriptionInfo.getSubscriptionId(), true);
                Log.d(TAG, "Set Data on slot: " + slotId);
            }
        }

        // make sure the network is available
        sNetworkCallback.awaitNetwork();
        assertTrue(getNetworkOnAvailable());

        // make sure the network is validated
        sConnectivityManager.reportNetworkConnectivity(getDefaultNetwork(), false);
        waitForExpectedValidationState(true, TIMEOUT_NETWORK_VALIDATION);
        assertTrue(getNetworkValidated());

        // Leave Service
        Log.d(TAG, "testNetworkValidated: Leave Service");
        sMockModemManager.changeNetworkService(slotId, MOCK_SIM_PROFILE_ID_TWN_CHT, false);

        // Remove the SIM
        sMockModemManager.removeSimCard(slotId);

        waitForNullActiveNetwork(TIMEOUT_ACTIVATE_NETWORK);
        assertNull(sConnectivityManager.getActiveNetwork());
    }

    private static void waitForExpectedValidationState(boolean validated, long timeout)
            throws InterruptedException {
        Log.d(
                TAG,
                "Wait For Expected ValidationState: expected: "
                        + validated
                        + ", timeout: "
                        + timeout
                        + "ms");
        synchronized (sIsValidateLock) {
            long expectedTimeout = System.currentTimeMillis() + timeout;
            boolean expectedResult = validated;
            while (System.currentTimeMillis() < expectedTimeout
                    && getNetworkValidated() != expectedResult) {
                sIsValidateLock.wait(WAIT_MSEC);
            }
        }
    }

    @Test
    public void testDDSChange() throws Throwable {
        Log.d(TAG, "ConnectivityManagerTestOnMockModem#testDDSChange");
        assumeTrue("Skip test: Not test on single SIM device", sIsMultiSimDevice);
        assumeTrue(hasApns(MCC_MNC_TWN_CHT));
        assumeTrue(hasApns(MCC_MNC_TWN_FET));

        int slotId_0 = 0;
        int slotId_1 = 1;

        // Insert a SIM
        sMockModemManager.insertSimCard(slotId_0, MOCK_SIM_PROFILE_ID_TWN_CHT);
        sMockModemManager.insertSimCard(slotId_1, MOCK_SIM_PROFILE_ID_TWN_FET);

        // Enter Service
        Log.d(TAG, "testDsdsServiceStateChange: Enter Service");
        sMockModemManager.changeNetworkService(slotId_0, MOCK_SIM_PROFILE_ID_TWN_CHT, true);
        sMockModemManager.changeNetworkService(slotId_1, MOCK_SIM_PROFILE_ID_TWN_FET, true);

        for (int slotIndex = 0; slotIndex < 2; slotIndex++) {
            boolean isDataEnabled = sTelephonyManager.getDataEnabled(slotIndex);
            Log.d(TAG, "Data enabled status for SIM slot " + slotIndex + ":  " + isDataEnabled);
        }

        String packageName = getContext().getPackageName();
        Log.d(TAG, "packageName: " + packageName);

        // Get the list of available subscriptions
        List<SubscriptionInfo> subscriptionInfoList =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        sSubscriptionManager, (sm) -> sm.getActiveSubscriptionInfoList());

        Log.d(TAG, "subscriptionInfoList: " + subscriptionInfoList);

        // Loop through the subscriptions to get the phone id
        // Set the data enable by phone id
        for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity("android.permission.MODIFY_PHONE_STATE");
            int slotId = subscriptionInfo.getSimSlotIndex();
            sSubscriptionManager.setDefaultDataSubId(subscriptionInfo.getSubscriptionId());
            sTelephonyManager.setDataEnabled(subscriptionInfo.getSubscriptionId(), true);
            Log.d(TAG, "Set Data on slot: " + slotId);
            // make sure the network is available
            sNetworkCallback.awaitNetwork();
            Log.d(TAG, "Check Data : " + getNetworkOnAvailable());
            assertTrue(getNetworkOnAvailable());
        }

        // Leave Service
        Log.d(TAG, "testDsdsServiceStateChange: Leave Service");
        sMockModemManager.changeNetworkService(slotId_0, MOCK_SIM_PROFILE_ID_TWN_CHT, false);
        sMockModemManager.changeNetworkService(slotId_1, MOCK_SIM_PROFILE_ID_TWN_FET, false);

        // Remove the SIM
        sMockModemManager.removeSimCard(slotId_0);
        sMockModemManager.removeSimCard(slotId_1);

        waitForNullActiveNetwork(TIMEOUT_ACTIVATE_NETWORK);
        assertNull(sConnectivityManager.getActiveNetwork());
    }

    private static void waitForNullActiveNetwork(long timeout)
            throws InterruptedException {
        Log.d(
                TAG,
                "Wait For Null ActiveNetwork: "
                        + "timeout: "
                        + timeout
                        + " ms");
        long expectedTimeout = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < expectedTimeout
                && sConnectivityManager.getActiveNetwork() != null) {
            TimeUnit.SECONDS.sleep(1);
            Log.d(TAG, "ActiveNetwork: " + sConnectivityManager.getActiveNetwork());
        }
    }
}

