/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CarrierSignalTest {
    private class TestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mIntentFuture.complete(intent);
        }
    }

    @Rule
    public final RequiredFeatureRule mTelephonyRequiredRule =
            new RequiredFeatureRule(PackageManager.FEATURE_TELEPHONY);

    private static final String LOG_TAG = "CarrierSignalTest";
    private static final int TEST_TIMEOUT_MILLIS = 5000;
    private static final int NETWORK_TIMEOUT = 1000;
    private Context mContext;
    private CarrierConfigManager mCarrierConfigManager;
    private int mTestSub;
    private CompletableFuture<Intent> mIntentFuture = new CompletableFuture<>();
    private final TestReceiver mReceiver = new TestReceiver();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mCarrierConfigManager = mContext.getSystemService(CarrierConfigManager.class);
        mTestSub = SubscriptionManager.getDefaultSubscriptionId();

        String[] carrierConfigData =
                new String[] {
                    new ComponentName(mContext.getPackageName(), mReceiver.getClass().getName())
                                    .flattenToString()
                            + ":"
                            // add more actions here as tests increase.
                            + String.join(",", TelephonyManager.ACTION_CARRIER_SIGNAL_RESET)
                };
        PersistableBundle b = new PersistableBundle();
        b.putStringArray(
                CarrierConfigManager.KEY_CARRIER_APP_NO_WAKE_SIGNAL_CONFIG_STRING_ARRAY,
                carrierConfigData);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mCarrierConfigManager, (cm) -> cm.overrideConfig(mTestSub, b));
        // We have no way of knowing when CarrierSignalAgent processes this broadcast, so sleep
        // and hope for the best.
        Thread.sleep(1000);
    }

    @After
    public void tearDown() {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                mCarrierConfigManager, (cm) -> cm.overrideConfig(mTestSub, null));
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                cm, x -> x.setAirplaneMode(false));
        waitForCampingNetwork(TEST_TIMEOUT_MILLIS);
        try {
            mContext.unregisterReceiver(mReceiver);
        } catch (Throwable t) { }
    }

    @Test
    public void testResetBroadcast() throws Exception {
        mIntentFuture = new CompletableFuture<>();
        mContext.registerReceiver(
                mReceiver, new IntentFilter(TelephonyManager.ACTION_CARRIER_SIGNAL_RESET));

        // Enable airplane mode to force the reset action
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                cm, x -> x.setAirplaneMode(true));

        Intent receivedIntent = mIntentFuture.get(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(
                mTestSub,
                receivedIntent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1));

        assertTrue(
                "still has in-service network after airplane mode",
                waitForLosingNetwork(TEST_TIMEOUT_MILLIS));
    }

    protected boolean waitForLosingNetwork(long timeout) {
        long startTime = SystemClock.uptimeMillis();
        while (true) {
            if (!isCamped()) {
                return true;
            }
            if ((SystemClock.uptimeMillis() - startTime) > timeout) {
                Log.d(LOG_TAG, "waitForLosingNetwork is timed out: " + timeout);
                return false;
            }
            Log.d(LOG_TAG, "waitForLosingNetwork...");
            SystemClock.sleep(NETWORK_TIMEOUT);
        }
    }

    protected boolean waitForCampingNetwork(long timeout) {
        long startTime = SystemClock.uptimeMillis();
        while (true) {
            if (isCamped()) {
                return true;
            }
            if ((SystemClock.uptimeMillis() - startTime) > timeout) {
                Log.d(LOG_TAG, "waitForCampingNetwork is timed out: " + timeout);
                return false;
            }
            Log.d(LOG_TAG, "waitForCampingNetwork...");
            SystemClock.sleep(NETWORK_TIMEOUT);
        }
    }

    private boolean isCamped() {
        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        ServiceState ss =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        tm, telephonyManager -> telephonyManager.getServiceState());
        if (ss == null) return false;
        if (ss.getState() == ServiceState.STATE_EMERGENCY_ONLY) return true;
        List<NetworkRegistrationInfo> nris = ss.getNetworkRegistrationInfoList();
        for (NetworkRegistrationInfo nri : nris) {
            if (nri.getTransportType() != AccessNetworkConstants.TRANSPORT_TYPE_WWAN) continue;
            if (nri.isRegistered()) return true;
        }
        return false;
    }
}
