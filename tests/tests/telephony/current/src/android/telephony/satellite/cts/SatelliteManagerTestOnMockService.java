/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.telephony.satellite.cts;

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;
import static android.telephony.satellite.SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_ENTITLEMENT;
import static android.telephony.satellite.SatelliteManager.SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DISALLOWED_REASON_UNSUPPORTED_DEFAULT_MSG_APP;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_ACCESS_BARRED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_DISABLE_IN_PROGRESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_ENABLE_IN_PROGRESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_LOCATION_DISABLED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_MODEM_ERROR;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_NO_RESOURCES;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_ABORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_IN_PROGRESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SUCCESS;

import static com.android.internal.telephony.satellite.SatelliteController.TIMEOUT_TYPE_DEMO_POINTING_ALIGNED_DURATION_MILLIS;
import static com.android.internal.telephony.satellite.SatelliteController.TIMEOUT_TYPE_DEMO_POINTING_NOT_ALIGNED_DURATION_MILLIS;
import static com.android.internal.telephony.satellite.SatelliteController.TIMEOUT_TYPE_EVALUATE_ESOS_PROFILES_PRIORITIZATION_DURATION_MILLIS;
import static com.android.internal.telephony.satellite.SatelliteController.TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.radio.RadioError;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.SatelliteReceiver;
import android.telephony.cts.TelephonyManagerTest.ServiceStateRadioStateListener;
import android.telephony.mockmodem.MockModemManager;
import android.telephony.satellite.AntennaDirection;
import android.telephony.satellite.AntennaPosition;
import android.telephony.satellite.EarfcnRange;
import android.telephony.satellite.NtnSignalStrength;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteAccessConfiguration;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteInfo;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatellitePosition;
import android.telephony.satellite.SatelliteSessionStats;
import android.telephony.satellite.SatelliteStateChangeListener;
import android.telephony.satellite.SatelliteSubscriberInfo;
import android.telephony.satellite.SatelliteSubscriberProvisionStatus;
import android.telephony.satellite.SystemSelectionSpecifier;
import android.telephony.satellite.stub.NTRadioTechnology;
import android.telephony.satellite.stub.SatelliteModemState;
import android.telephony.satellite.stub.SatelliteResult;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.uwb.UwbManager;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.LocationUtils;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.SmsApplication;
import com.android.internal.telephony.flags.Flags;
import com.android.internal.telephony.satellite.DatagramController;
import com.android.internal.telephony.satellite.SatelliteServiceUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SatelliteManagerTestOnMockService extends SatelliteManagerTestBase {
    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final boolean DEBUG = !"user".equals(Build.TYPE);
    private static final long TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS = 100;
    private static final long TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS = 60 * 10 * 1000;
    private static final long TEST_SATELLITE_DEVICE_ALIGN_TIMEOUT_MILLIS = 100;
    private static final long TEST_SATELLITE_DEVICE_ALIGN_FOREVER_TIMEOUT_MILLIS = 100000;
    private static final long TEST_DATAGRAM_DELAY_IN_DEMO_MODE_TIMEOUT_MILLIS = 100;
    private static final long TEST_DATAGRAM_DELAY_IN_DEMO_MODE_TIMEOUT_LONG_MILLIS = 1000;
    private static final long WAIT_FOREVER_TIMEOUT_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final long MAX_WAIT_FOR_STATE_CHANGED_SECONDS = 5;

    private static MockSatelliteServiceManager sMockSatelliteServiceManager;

    /* SatelliteCapabilities constant indicating that the radio technology is proprietary. */
    private static final Set<Integer> SUPPORTED_RADIO_TECHNOLOGIES;

    static {
        SUPPORTED_RADIO_TECHNOLOGIES = new HashSet<>();
        SUPPORTED_RADIO_TECHNOLOGIES.add(SatelliteManager.NT_RADIO_TECHNOLOGY_PROPRIETARY);
    }

    /* SatelliteCapabilities constant indicating that pointing to satellite is required. */
    private static final boolean POINTING_TO_SATELLITE_REQUIRED = true;
    /* SatelliteCapabilities constant indicating the maximum number of characters per datagram. */
    private static final int MAX_BYTES_PER_DATAGRAM = 339;
    /* SatelliteCapabilites constant antenna position map received from satellite modem. */
    private static final Map<Integer, AntennaPosition> ANTENNA_POSITION_MAP;

    static {
        ANTENNA_POSITION_MAP = new HashMap<>();
        ANTENNA_POSITION_MAP.put(SatelliteManager.DISPLAY_MODE_OPENED,
                new AntennaPosition(new AntennaDirection(1, 1, 1),
                        SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT));
        ANTENNA_POSITION_MAP.put(SatelliteManager.DISPLAY_MODE_CLOSED,
                new AntennaPosition(new AntennaDirection(2, 2, 2),
                        SatelliteManager.DEVICE_HOLD_POSITION_LANDSCAPE_LEFT));
    }

    // The test data is stored at
    // vendor/google/services/ConfigUpdater/assets/cts_data/telephony_config_update/
    private static final String TEST_V14_CONFIG_DATA_CONTENT_LOCAL_URI =
            "file:///cts_test_06122024-test-v14-telephony_config.pb";
    private static final String TEST_V14_CONFIG_DATA_METADATA_LOCAL_URI =
            "file:///cts_test_06122024-test-v14-telephony_config-metadata.txt";
    private static final String TEST_V15_CONFIG_DATA_CONTENT_LOCAL_URI =
            "file:///cts_test_01212025-test-v15-telephony_config.pb";
    private static final String TEST_V15_CONFIG_DATA_METADATA_LOCAL_URI =
            "file:///cts_test_01212025-test-v15-telephony_config-metadata.txt";

    private static CarrierConfigReceiver sCarrierConfigReceiver;

    private static final int SUB_ID = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
    private static final String OVERRIDING_COUNTRY_CODES = "US";
    private static final String SATELLITE_COUNTRY_CODES = "US,UK,CA";
    private static final String SATELLITE_S2_FILE = "google_us_san_sat_s2.dat";
    private static final String SATELLITE_S2_FILE_WITH_CONFIG_ID =
            "google_us_san_mtv_sat_s2.dat";
    private static final String SATELLITE_ACCESS_CONFIGURATION_FILE =
            "satellite_access_config.json";
    private static final String TEST_PROVIDER = LocationManager.FUSED_PROVIDER;
    private static final float LOCATION_ACCURACY = 95;
    private static LocationManager sLocationManager;

    BTWifiNFCStateReceiver mBTWifiNFCSateReceiver = null;
    UwbAdapterStateCallback mUwbAdapterStateCallback = null;
    private String mTestSatelliteModeRadios = null;
    boolean mBTInitState = false;
    boolean mWifiInitState = false;
    boolean mNfcInitState = false;
    boolean mUwbInitState = false;
    @SuppressWarnings("StaticAssignmentOfThrowable")
    static AssertionError sInitError = null;

    // Latch to prevent race condition between mIsEnabled state change and verification
    private CountDownLatch mIsEnabledStateChangedLatch;
    private boolean mIsEnabled;

    public class TestSatelliteStateChangeListener implements SatelliteStateChangeListener {
        @Override
        public void onEnabledStateChanged(boolean isEnabled) {
            final boolean isEnabledStateChanged = isEnabled != mIsEnabled;
            mIsEnabled = isEnabled;
            if (mIsEnabledStateChangedLatch != null && mIsEnabledStateChangedLatch.getCount() > 0
                    && isEnabledStateChanged) {
                mIsEnabledStateChangedLatch.countDown();
            }
        }
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @BeforeClass
    public static void beforeAllTests() {
        logd("beforeAllTests");

        if (!shouldTestSatelliteWithMockService()) return;

        beforeAllTestsBase();
        if (!shouldTestSatellite()) {
            // FEATURE_TELEPHONY_SATELLITE is missing, so let's set up mock SatelliteManager.
            sSatelliteManager = new SatelliteManager(getContext());
        }
        try {
            MockModemManager.enforceMockModemDeveloperSetting();
        } catch (Exception e) {
            sInitError = new AssertionError("enforceMockModemDeveloperSetting failed", e);
            return;
        }

        grantSatellitePermission();

        sMockSatelliteServiceManager = new MockSatelliteServiceManager(
                InstrumentationRegistry.getInstrumentation());
        setUpSatelliteAccessAllowed();
        try {
            setupMockSatelliteService();
        } catch (AssertionError e) {
            sInitError = e;
            return;
        }

        sCarrierConfigReceiver = new CarrierConfigReceiver(SUB_ID);
        sLocationManager = getContext().getSystemService(LocationManager.class);

        sMockSatelliteServiceManager.setDatagramControllerBooleanConfig(false,
                DatagramController.BOOLEAN_TYPE_WAIT_FOR_DEVICE_ALIGNMENT_IN_DEMO_DATAGRAM, true);
        sNtnOnlySubId = getNtnOnlySubscriptionId();
        assumeTrue(sNtnOnlySubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        setUpNtnOnlySubscription();

        revokeSatellitePermission();
    }

    private static void setupMockSatelliteService() {
        assertTrue(sMockSatelliteServiceManager.connectSatelliteService());

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        int count = 0;
        while (sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback)
                != SatelliteManager.SATELLITE_RESULT_SUCCESS
                && count < 10) {
            count++;
            waitFor(500);
        }
        assertTrue(callback.waitUntilResult(1));
        if (callback.modemState == SatelliteManager.SATELLITE_MODEM_STATE_OFF) {
            waitFor(2000);
        } else {
            assertTrue(callback.waitUntilModemOff(EXTERNAL_DEPENDENT_TIMEOUT));
        }
        sSatelliteManager.unregisterForModemStateChanged(callback);

        assertTrue(isSatelliteSupported());
    }

    @AfterClass
    public static void afterAllTests() {
        logd("afterAllTests");
        if (sInitError != null) return;
        if (!shouldTestSatelliteWithMockService()) return;

        grantSatellitePermission();

        sMockSatelliteServiceManager.setDatagramControllerBooleanConfig(true,
                DatagramController.BOOLEAN_TYPE_WAIT_FOR_DEVICE_ALIGNMENT_IN_DEMO_DATAGRAM, false);

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        if (isSatelliteEnabled()) {
            logd("Disable satellite");
            // Disable satellite modem to clean up all pending resources and reset telephony states.
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
        }

        assertTrue(sMockSatelliteServiceManager.restoreSatelliteServicePackageName());
        waitFor(2000);
        sSatelliteManager.unregisterForModemStateChanged(callback);
        resetSatelliteAccessControlOverlayConfigs();
        resetSatelliteAccessForNtnOnlySubscription();
        restoreDefaultSmsAppSupportForNtnOnlySubscription();
        restoreNtnOnlySubscription();
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache(
                        "cache_clear_and_not_allowed"));
        afterAllTestsBase();
        sMockSatelliteServiceManager = null;
        sCarrierConfigReceiver = null;
        revokeSatellitePermission();
    }

    @Before
    public void setUp() throws Exception {
        logd("setUp");
        if (sInitError != null) throw sInitError;
        assumeTrue(shouldTestSatelliteWithMockService());
        assumeTrue(sMockSatelliteServiceManager != null);

        assertTrue(sMockSatelliteServiceManager.setSatelliteIgnoreCellularServiceState(true));
        assertTrue(sMockSatelliteServiceManager.setSatelliteTnScanningSupport(false, false, true));
        assertTrue(sMockSatelliteServiceManager.setSupportDisableSatelliteWhileEnableInProgress(
                false, true));

        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        sMockSatelliteServiceManager.setWaitToSend(false);
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        sMockSatelliteServiceManager.mIsPointingUiOverridden = false;

        // Bypass geofence by enforcing SatelliteAccessController to use on-device data with
        // mock location
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("cache_allowed"));
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false, null, null, null, 0));
        assertTrue(
                sMockSatelliteServiceManager.setSatelliteAccessControlOverlayConfigs(
                        false, true, SATELLITE_S2_FILE, TimeUnit.MINUTES.toNanos(60), "US", null));

        // Set location provider and current location to Google San Diego office
        registerTestLocationProvider();
        setTestProviderLocation(32.909808231041644, -117.18185788819781);
        verifyIsSatelliteAllowed(true);

        // Initialize radio state
        mBTInitState = false;
        mWifiInitState = false;
        mNfcInitState = false;
        mUwbInitState = false;
        mTestSatelliteModeRadios = "";

        SatelliteModeRadiosUpdater satelliteRadiosModeUpdater =
                new SatelliteModeRadiosUpdater(getContext());
        assertTrue(satelliteRadiosModeUpdater.setSatelliteModeRadios(""));
        setUpNtnOnlySubscription();

        grantSatellitePermission();
        if (!isSatelliteEnabled()) {
            logd("Enable satellite");

            SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            int i = 0;
            while (requestSatelliteEnabledWithResult(true, EXTERNAL_DEPENDENT_TIMEOUT)
                    != SatelliteManager.SATELLITE_RESULT_SUCCESS && i < 3) {
                waitFor(500);
                i++;
            }

            assertTrue(callback.waitUntilResult(1));
            assertTrue(isSatelliteEnabled());
            sSatelliteManager.unregisterForModemStateChanged(callback);
            // Set initial mIsEnabled to match the actual satellite state
            mIsEnabled = true;
            mIsEnabledStateChangedLatch = new CountDownLatch(1);
        }
        logd("Satellite enabled");

        revokeSatellitePermission();
    }

    @After
    public void tearDown() {
        logd("tearDown");
        if (!shouldTestSatelliteWithMockService()) return;
        assumeTrue(sMockSatelliteServiceManager != null);
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        sMockSatelliteServiceManager.setWaitToSend(false);
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();

        assertTrue(sMockSatelliteServiceManager.setSatelliteIgnoreCellularServiceState(false));
        assertTrue(sMockSatelliteServiceManager.setSatelliteTnScanningSupport(true, false, false));
        assertTrue(sMockSatelliteServiceManager.setSupportDisableSatelliteWhileEnableInProgress(
                true, false));

        // Move satellite to off state to clean up all pending resources
        // and reset telephony states.
        moveSatelliteToOffState();

        grantSatellitePermission();
        sMockSatelliteServiceManager.restoreSatellitePointingUiClassName();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        sMockSatelliteServiceManager.clearListeningEnabledList();
        revokeSatellitePermission();
        sMockSatelliteServiceManager.mIsPointingUiOverridden = false;
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SATELLITE_STATE_CHANGE_LISTENER)
    public void testServiceIsPublicAccessible() {
        if (!shouldTestSatellite()) {
            return;
        }
        SatelliteManager satelliteManager = (SatelliteManager) getContext().getSystemService(
                Context.SATELLITE_SERVICE);
        assertThat(satelliteManager).isNotNull();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SATELLITE_STATE_CHANGE_LISTENER)
    public void testRegisterStateChangeListener_unregisterNotRegistered_noOp() {
        if (!shouldTestSatelliteWithMockService()) {
            return;
        }
        SatelliteStateChangeListener listener = new TestSatelliteStateChangeListener();
        // listener is not registered, unregistering is no-op
        sSatelliteManager.unregisterStateChangeListener(listener);
    }

    @Ignore("b/405225616 - Need to fix and re-enable it.")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SATELLITE_STATE_CHANGE_LISTENER)
    public void testRegisterStateChangeListener_withReadPhoneStatePermission_noThrows() {
        if (!shouldTestSatelliteWithMockService()) {
            return;
        }

        // READ_PHONE_STATE has been granted for this test suite in AndroidManifest
        assertThat(getContext().checkSelfPermission(Manifest.permission.READ_PHONE_STATE))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        SatelliteStateChangeListener listener = new TestSatelliteStateChangeListener();

        try {
            sSatelliteManager.registerStateChangeListener(getContext().getMainExecutor(), listener);
        } finally {
            sSatelliteManager.unregisterStateChangeListener(listener);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SATELLITE_STATE_CHANGE_LISTENER)
    public void testStateChangeListener_onRegistration_getNotified() {
        if (!shouldTestSatelliteWithMockService()) {
            return;
        }

        assertThat(mIsEnabled).isTrue();
        SatelliteStateChangeListener listener = new TestSatelliteStateChangeListener();
        try {
            grantSatelliteAndReadBasicPhoneStatePermissions();
            requestSatelliteEnabled(false);
            assertThat(isSatelliteEnabled()).isFalse();

            sSatelliteManager.registerStateChangeListener(getContext().getMainExecutor(), listener);

            assertIsEnabledState(true /* expectedIsEnabledStateChanged */,
                    false /* expectedIsEnabledState */);
        } finally {
            // Clean up
            sSatelliteManager.unregisterStateChangeListener(listener);
            requestSatelliteEnabled(true);
            revokeSatellitePermission();
        }
    }

    @Ignore("b/403572869 - This test is flaky. Need to fix and re-enable it.")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SATELLITE_STATE_CHANGE_LISTENER)
    public void testStateChangeListener_duringRegistration_getNotified() {
        if (!shouldTestSatelliteWithMockService()) {
            return;
        }

        assertThat(mIsEnabled).isTrue();
        SatelliteStateChangeListener listener = new TestSatelliteStateChangeListener();
        try {
            grantSatelliteAndReadBasicPhoneStatePermissions();
            sSatelliteManager.registerStateChangeListener(getContext().getMainExecutor(), listener);

            requestSatelliteEnabled(false);
            assertThat(isSatelliteEnabled()).isFalse();

            assertIsEnabledState(true /* expectedIsEnabledStateChanged */,
                    false /* expectedIsEnabledState */);
        } finally {
            sSatelliteManager.unregisterStateChangeListener(listener);
            requestSatelliteEnabled(true);
            revokeSatellitePermission();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SATELLITE_STATE_CHANGE_LISTENER)
    public void testStateChangeListener_afterRegistration_notNotified() {
        if (!shouldTestSatelliteWithMockService()) {
            return;
        }

        assertThat(mIsEnabled).isTrue();
        SatelliteStateChangeListener listener = new TestSatelliteStateChangeListener();
        try {
            grantSatelliteAndReadBasicPhoneStatePermissions();
            sSatelliteManager.registerStateChangeListener(getContext().getMainExecutor(), listener);
            sSatelliteManager.unregisterStateChangeListener(listener);

            requestSatelliteEnabled(false);
            assertThat(isSatelliteEnabled()).isFalse();

            assertIsEnabledState(false /* expectedIsEnabledStateChanged */,
                    true /* expectedIsEnabledState */);
        } finally {
            // Clean up. If listener has been unregistered, redo it is a no-op
            sSatelliteManager.unregisterStateChangeListener(listener);
            requestSatelliteEnabled(true);
            revokeSatellitePermission();
        }
    }

    @Test
    public void testProvisionSatelliteService() {
        logd("testProvisionSatelliteService: start");

        grantSatellitePermission();
        try {
            LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
            SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                    new SatelliteProvisionStateCallbackTest();
            long registerError = sSatelliteManager.registerForProvisionStateChanged(
                    getContext().getMainExecutor(), satelliteProvisionStateCallback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);
            assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));

            if (isSatelliteProvisioned()) {
                logd("testProvisionSatelliteService: dreprovision the device");
                deprovisionSatelliteForDevice();
                assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
                assertFalse(satelliteProvisionStateCallback.isProvisioned);
            }

            logd("testProvisionSatelliteService: successfully provision");
            satelliteProvisionStateCallback.clearProvisionedStates();
            assertTrue(provisionSatellite());
            assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
            assertTrue(satelliteProvisionStateCallback.isProvisioned);

            logd("testProvisionSatelliteService: successfully deprovision");
            satelliteProvisionStateCallback.clearProvisionedStates();
            assertTrue(deprovisionSatellite());
            assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
            assertFalse(satelliteProvisionStateCallback.isProvisioned);

            logd("testProvisionSatelliteService: provision and cancel");
            satelliteProvisionStateCallback.clearProvisionedStates();
            CancellationSignal cancellationSignal = new CancellationSignal();
            String mText = "This is test provision data.";
            byte[] testProvisionData = mText.getBytes();
            sSatelliteManager.provisionService(TOKEN, testProvisionData, cancellationSignal,
                    getContext().getMainExecutor(), error::offer);

            Integer errorCode;
            try {
                errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
                cancellationSignal.cancel();
            } catch (InterruptedException ex) {
                fail("testProvisionSatelliteService: Got InterruptedException ex=" + ex);
                return;
            }
            assertNotNull(errorCode);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, (long) errorCode);

            // Provision succeeded and then got canceled - deprovisioned
            assertTrue(satelliteProvisionStateCallback.waitUntilResult(2));
            assertEquals(2, satelliteProvisionStateCallback.getTotalCountOfProvisionedStates());
            assertTrue(satelliteProvisionStateCallback.getProvisionedState(0));
            assertFalse(satelliteProvisionStateCallback.getProvisionedState(1));
            assertFalse(satelliteProvisionStateCallback.isProvisioned);
            assertFalse(isSatelliteProvisioned());
        } finally {
            revokeSatellitePermission();
        }
    }

    @Test
    public void testPointingUICrashHandling() {
        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());
        assertTrue(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.overrideExternalSatellitePointingUiClassName());
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();

        // Start Pointing UI app
        sendSatelliteDatagramSuccess(false, false);

        // Forcefully stop the Pointing UI app
        sMockSatelliteServiceManager.clearStopPointingUiActivity();
        assertTrue(sMockSatelliteServiceManager.stopExternalMockPointingUi());
        assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStopped(1));
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        // Check if the Pointing UI app restarted
        assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStarted(1));

        // Kill the Pointing UI app multiple times and check if it is restarted everytime
        for (int i = 0; i < 10; i++) {
            sMockSatelliteServiceManager.clearStopPointingUiActivity();
            // Forcefully stop the Pointing UI app again
            assertTrue(sMockSatelliteServiceManager.stopExternalMockPointingUi());
            assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStopped(1));
            sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
            // Check if the Pointing UI app has restarted
            assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStarted(1));
        }
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());
    }

    @Test
    @Ignore
    public void testSatelliteRequestEnabled() {
        assumeTrue(sMockSatelliteServiceManager != null);
        grantSatellitePermission();

        /*
         * When the LocationManager is disabled :
         * 1) Set location inside or outside of geofence
         * 2) Check both requestSatelliteEnabled and requestIsCommunicationAllowedForCurrentLocation
         *  - result is SATELLITE_RESULT_LOCATION_DISABLED
         */
        // LocationManager is disabled
        sLocationManager.setLocationEnabledForUser(false, Process.myUserHandle());

        // Set current location inside of the geofence data (San Diego office)
        setTestProviderLocation(32.909808231041644, -117.18185788819781);
        verifySatelliteNotAllowedErrorReason(SATELLITE_RESULT_LOCATION_DISABLED);

        int result = requestSatelliteEnabledWithResult(true, TIMEOUT);
        assertEquals(SatelliteManager.SATELLITE_RESULT_LOCATION_DISABLED, result);

        // Set current location outside of the geofence data (Bangalore office)
        setTestProviderLocation(12.997138153769894, 77.66099948612018);
        verifySatelliteNotAllowedErrorReason(SATELLITE_RESULT_LOCATION_DISABLED);

        result = requestSatelliteEnabledWithResult(true, TIMEOUT);
        assertEquals(SatelliteManager.SATELLITE_RESULT_LOCATION_DISABLED, result);

        /*
         * When the LocationManager is enabled and cache is valid :
         * 1) Set location outside of geofence
         * - The result of requestIsCommunicationAllowedForCurrentLocation is true due to cache
         * - The result of requestSatelliteEnabled is SATELLITE_RESULT_ACCESS_BARRED
         * 2) Check the result of requestIsCommunicationAllowedForCurrentLocation once more
         * - Since the cache is updated as false in
         */
        // LocationManager is disabled and cache is valid
        sLocationManager.setLocationEnabledForUser(true, Process.myUserHandle());
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("cache_allowed"));

        // Set current location outside of the geofence data (Bangalore office)
        setTestProviderLocation(12.997138153769894, 77.66099948612018);
        verifyIsSatelliteAllowed(true);

        result = requestSatelliteEnabledWithResult(true, TIMEOUT);
        assertEquals(SATELLITE_RESULT_ACCESS_BARRED, result);

        verifyIsSatelliteAllowed(false);
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemStateChanged() {
        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        boolean originalEnabledState = isSatelliteEnabled();
        boolean registerCallback = false;
        if (originalEnabledState) {
            registerCallback = true;

            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(false);

            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }
        if (!registerCallback) {
            long registerResult = sSatelliteManager
                    .registerForModemStateChanged(getContext().getMainExecutor(),
                            callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        }

        assertTrue(sMockSatelliteServiceManager.connectSatelliteGatewayService());
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        requestSatelliteEnabled(true);

        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceConnected(1));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());

        SatelliteModemStateCallbackTest
                callback1 = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager
                .registerForModemStateChanged(getContext().getMainExecutor(), callback1);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback1.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback1.modemState);
        sSatelliteManager.unregisterForModemStateChanged(callback);

        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS));

        // Verify state transitions: IDLE -> TRANSFERRING -> LISTENING -> IDLE
        sendSatelliteDatagramWithSuccessfulResult(callback1, true);

        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS));

        // Move to LISTENING state
        sendSatelliteDatagramWithSuccessfulResult(callback1, false);

        // Verify state transitions: LISTENING -> TRANSFERRING -> LISTENING
        receiveSatelliteDatagramWithSuccessfulResult(callback1);

        // Verify state transitions: LISTENING -> TRANSFERRING -> IDLE
        sendSatelliteDatagramWithFailedResult(callback1);

        // Move to LISTENING state
        sendSatelliteDatagramWithSuccessfulResult(callback1, false);

        // Verify state transitions: LISTENING -> TRANSFERRING -> IDLE
        receiveSatelliteDatagramWithFailedResult(callback1);

        callback1.clearModemStates();
        requestSatelliteEnabled(false);
        assertTrue(callback1.waitUntilModemOff());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback1.modemState);
        assertFalse(isSatelliteEnabled());
        assertTrue(
                sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceDisconnected(1));

        sSatelliteManager.unregisterForModemStateChanged(callback1);
        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(0));
        assertTrue(sMockSatelliteServiceManager.restoreSatelliteGatewayServicePackageName());

        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemStateChangedForNbIot() {
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);

        try {
            grantSatellitePermission();
            assertTrue(isSatelliteProvisioned());

            SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
            boolean originalEnabledState = isSatelliteEnabled();
            boolean registerCallback = false;
            if (originalEnabledState) {
                registerCallback = true;

                long registerResult = sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
                assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
                assertTrue(callback.waitUntilResult(1));

                requestSatelliteEnabled(false);

                assertTrue(callback.waitUntilModemOff());
                assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
                assertFalse(isSatelliteEnabled());
                callback.clearModemStates();
            }
            if (!registerCallback) {
                long registerResult = sSatelliteManager
                        .registerForModemStateChanged(getContext().getMainExecutor(),
                                callback);
                assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
                assertTrue(callback.waitUntilResult(1));
                assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            }

            assertTrue(sMockSatelliteServiceManager.connectSatelliteGatewayService());
            assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
            sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                    callback.getModemState(1));
            assertTrue(isSatelliteEnabled());
            assertTrue(
                    sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceConnected(1));
            assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());

            callback.clearModemStates();
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(1));
            assertFalse(isSatelliteEnabled());

            assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                    TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS));

            // Verify state transitions: OFF -> ENABLING_SATELLITE -> NOT_CONNECTED -> IDLE
            callback.clearModemStates();
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(3));
            assertTrue(isSatelliteEnabled());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                    callback.getModemState(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                    callback.getModemState(2));

            callback.clearModemStates();
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(1));
            assertFalse(isSatelliteEnabled());

            assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                    TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS));

            callback.clearModemStates();
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                    callback.getModemState(1));
            assertTrue(isSatelliteEnabled());

            assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                    TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS));

            // Verify state transitions when sending: NOT_CONNECTED -> CONNECTED -> TRANSFERRING
            // -> CONNECTED -> IDLE
            sMockSatelliteServiceManager.clearListeningEnabledList();
            callback.clearModemStates();
            sendDatagramWithoutResponse();
            verifyNbIotStateTransitionsWithSendingOnConnected(callback, true);

            // Verify state transitions when receiving: IDLE -> NOT_CONNECTED -> CONNECTED
            // -> TRANSFERRING -> CONNECTED -> IDLE
            verifyNbIotStateTransitionsWithReceivingOnIdle(callback, true);

            // TODO (b/399426859): Re-enable this test once the bug is fixed.
            // Verify no state transition on IDLE state
            // verifyNbIotStateTransitionsWithTransferringFailureOnIdle(callback);

            // Verify state transition: IDLE -> NOT_CONNECTED -> POWER_OFF
            verifyNbIotStateTransitionsWithSendingAborted(callback);

            // Verify state transitions: POWER_OFF -> NOT_CONNECTED
            callback.clearModemStates();
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                    callback.getModemState(1));
            assertTrue(isSatelliteEnabled());

            assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                    TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS));

            // Verify state transitions when sending: NOT_CONNECTED -> CONNECTED -> TRANSFERRING
            // -> CONNECTED
            sMockSatelliteServiceManager.clearListeningEnabledList();
            callback.clearModemStates();
            sendDatagramWithoutResponse();
            verifyNbIotStateTransitionsWithSendingOnConnected(callback, false);

            // Verify state transitions: CONNECTED -> POWER_OFF
            callback.clearModemStates();
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(1));
            assertFalse(isSatelliteEnabled());
            assertTrue(
                    sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceDisconnected(
                            1));

            // Verify state transitions: POWER_OFF -> NOT_CONNECTED
            callback.clearModemStates();
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                    callback.getModemState(1));
            assertTrue(isSatelliteEnabled());

            // Move to CONNECTED state
            callback.clearModemStates();
            sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                    SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, callback.modemState);

            // Verify state transitions: CONNECTED -> TRANSFERRING -> CONNECTED
            verifyNbIotStateTransitionsWithReceivingOnConnected(callback);

            sSatelliteManager.unregisterForModemStateChanged(callback);
            assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(0));
            assertTrue(sMockSatelliteServiceManager.restoreSatelliteGatewayServicePackageName());
            updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);
        } finally {
            revokeSatellitePermission();
        }
    }

    @Test
    public void testSendKeepAliveDatagramInNotConnectedState() {
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);

        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        boolean originalEnabledState = isSatelliteEnabled();
        boolean registerCallback = false;
        if (originalEnabledState) {
            registerCallback = true;

            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(false);

            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }
        if (!registerCallback) {
            long registerResult = sSatelliteManager
                    .registerForModemStateChanged(getContext().getMainExecutor(),
                            callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        }

        assertTrue(sMockSatelliteServiceManager.connectSatelliteGatewayService());
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                callback.getModemState(1));
        assertTrue(isSatelliteEnabled());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceConnected(1));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());

        SatelliteTransmissionUpdateCallbackTest datagramCallback = startTransmissionUpdates();
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        LinkedBlockingQueue<Integer> sosResultListener = new LinkedBlockingQueue<>(1);
        LinkedBlockingQueue<Integer> keepAliveResultListener = new LinkedBlockingQueue<>(1);
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        // Send SOS satellite datagram
        datagramCallback.clearSendDatagramRequested();
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                sosResultListener::offer);

        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED
        assertTrue(datagramCallback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(datagramCallback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(datagramCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertTrue(datagramCallback.waitUntilOnSendDatagramRequested(1));
        assertEquals(1, datagramCallback.getNumOfSendDatagramRequestedChanges());
        assertEquals(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagramCallback.getSendDatagramRequestedType(0));

        // Send keepAlive satellite datagram
        datagramCallback.clearSendDatagramStateChanges();
        datagramCallback.clearSendDatagramRequested();
        callback.clearModemStates();
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_KEEP_ALIVE,
                datagram, true, getContext().getMainExecutor(),
                keepAliveResultListener::offer);
        assertTrue(datagramCallback.waitUntilOnSendDatagramRequested(1));
        assertEquals(1, datagramCallback.getNumOfSendDatagramRequestedChanges());
        assertEquals(SatelliteManager.DATAGRAM_TYPE_KEEP_ALIVE,
                datagramCallback.getSendDatagramRequestedType(0));

        // Modem state state should not be updated
        assertFalse(callback.waitUntilResult(1));
        // WAITING_FOR_CONNECTED will be broadcasted again after sending the keepAlive
        // datagram
        assertTrue(datagramCallback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(datagramCallback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(datagramCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        Integer errorCode;
        try {
            errorCode = keepAliveResultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Move satellite to CONNECTED state
        datagramCallback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);

        // The SOS datagram should be sent
        int expectedNumberOfEvents = 3;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED,
                callback.getModemState(2));

        // Expected datagram transfer state transitions: WAITING_FOR_CONNECTED -> SENDING
        // -> SEND_SUCCESS -> IDLE
        assertTrue(datagramCallback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(datagramCallback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
        assertThat(datagramCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(datagramCallback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(datagramCallback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        sSatelliteManager.stopTransmissionUpdates(datagramCallback, getContext().getMainExecutor(),
                keepAliveResultListener::offer);
        sSatelliteManager.unregisterForModemStateChanged(callback);
        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(0));
        assertTrue(sMockSatelliteServiceManager.restoreSatelliteGatewayServicePackageName());
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);
        revokeSatellitePermission();
    }

    private void sendDatagramWithoutResponse() {
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        transmissionUpdateCallback.clearSendDatagramRequested();
        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramRequested(1));
        assertEquals(1, transmissionUpdateCallback.getNumOfSendDatagramRequestedChanges());
        assertEquals(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                transmissionUpdateCallback.getSendDatagramRequestedType(0));

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("sendDatagramWithoutResponse: Got InterruptedException in waiting"
                    + " for the sendDatagram result code, ex=" + ex);
            return;
        }
        assertNull(errorCode);

        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithSendingOnConnected(
            @NonNull SatelliteModemStateCallbackTest callback, boolean moveToIdleState) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);
        callback.clearModemStates();

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        // Move satellite to CONNECTED state
        sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);

        int expectedNumberOfEvents = moveToIdleState ? 4 : 3;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED,
                callback.getModemState(2));
        if (moveToIdleState) {
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                    callback.getModemState(3));
        }

        // Expected datagram transfer state transitions: WAITING_FOR_CONNECTED -> SENDING
        // -> SEND_SUCCESS -> IDLE
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithTransferringFailureOnIdle(
            @NonNull SatelliteModemStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        // Test sending failure
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        callback.clearModemStates();
        sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_DATAGRAM_WAIT_FOR_CONNECTED_STATE, 1000);
        // Return failure for the request to disable cellular scanning when exiting IDLE state.
        sMockSatelliteServiceManager.setEnableCellularScanningErrorCode(
                SatelliteManager.SATELLITE_RESULT_SERVICE_ERROR);
        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        assertFalse(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED -> FAILED
        // -> IDLE.
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        1, SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Datagram wait for connected state timer should have timed out and the send request should
        // have been aborted.
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("verifyNbIotStateTransitionsWithTransferringFailureOnIdle: Got "
                    + "InterruptedException in waiting for the sendDatagram result code"
                    + ", ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE, (long) errorCode);

        // Test receiving failure
        resultListener.clear();
        sMockSatelliteServiceManager.setEnableCellularScanningErrorCode(
                SatelliteResult.SATELLITE_RESULT_ERROR);
        callback.clearModemStates();
        sSatelliteManager.pollPendingDatagrams(getContext().getMainExecutor(),
                resultListener::offer);

        assertFalse(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(3);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED,
                        0, SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Datagram wait for connected state timer should have timed out and the poll request should
        // have been aborted.
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("verifyNbIotStateTransitionsWithTransferringFailureOnIdle: Got "
                    + "InterruptedException in waiting for the pollPendingDatagrams result"
                    + " code, ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE, (long) errorCode);

        sMockSatelliteServiceManager.setEnableCellularScanningErrorCode(
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(true,
                DatagramController.TIMEOUT_TYPE_DATAGRAM_WAIT_FOR_CONNECTED_STATE, 0);
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithSendingAborted(
            @NonNull SatelliteModemStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        callback.clearModemStates();
        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("verifyNbIotStateTransitionsWithSendingAborted: Got InterruptedException"
                    + " in waiting for the sendDatagram result code, ex=" + ex);
            return;
        }
        assertNull(errorCode);

        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);

        // Turn off satellite modem. The send request should be aborted.
        callback.clearModemStates();
        sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_OFF);

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("verifyNbIotStateTransitionsWithSendingAborted: Got InterruptedException"
                    + " in waiting for the sendDatagram result code, ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SATELLITE_RESULT_REQUEST_ABORTED, (long) errorCode);

        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED -> FAILED
        // -> IDLE.
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        1, SATELLITE_RESULT_REQUEST_ABORTED));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithReceivingOnIdle(
            @NonNull SatelliteModemStateCallbackTest callback, boolean moveToIdleState) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        // Verify state transitions: IDLE -> NOT_CONNECTED
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearPollPendingDatagramPermits();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertFalse(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);
        // Expected datagram transfer state transitions: IDLE -> WAITING_FOR_CONNECTED
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(1);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Verify state transitions: NOT_CONNECTED -> CONNECTED -> TRANSFERRING
        callback.clearModemStates();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(1));
        // Telephony should send the request pollPendingDatagrams to modem
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Expected datagram transfer state transitions: WAITING_FOR_CONNECTED -> RECEIVING
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(1);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        verifyNbIotStateTransitionsWithDatagramReceivedOnTransferring(
                callback, moveToIdleState, transmissionUpdateCallback);
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void verifyNbIotStateTransitionsWithDatagramReceivedOnTransferring(
            @NonNull SatelliteModemStateCallbackTest callback, boolean moveToIdleState,
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback) {
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING, callback.modemState);

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();

        callback.clearModemStates();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);

        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        int expectedNumberOfEvents = moveToIdleState ? 2 : 1;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED,
                callback.getModemState(0));
        if (moveToIdleState) {
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                    callback.getModemState(1));
        }

        // Expected datagram transfer state transitions: RECEIVING -> RECEIVE_SUCCESS -> IDLE
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(2));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(2);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
    }

    private void verifyNbIotStateTransitionsWithReceivingOnConnected(
            @NonNull SatelliteModemStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, callback.modemState);

        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();

        // Verify state transitions: CONNECTED -> TRANSFERRING -> CONNECTED
        callback.clearModemStates();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);

        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        int expectedNumberOfEvents = 2;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED,
                callback.getModemState(1));

        // Expected datagram transfer state transitions: IDLE -> RECEIVE_SUCCESS -> IDLE
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(2));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(2);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    @Test
    public void testSatelliteEnableErrorHandling() {
        assumeTrue(sTelephonyManager != null);

        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        boolean originalEnabledState = isSatelliteEnabled();
        boolean registerCallback = false;
        if (originalEnabledState) {
            registerCallback = true;

            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(false);

            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }
        if (!registerCallback) {
            long registerResult = sSatelliteManager
                    .registerForModemStateChanged(getContext().getMainExecutor(),
                            callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        }

        callback.clearModemStates();
        requestSatelliteEnabled(true, true, SatelliteManager.SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());

        requestSatelliteEnabled(true, true, SatelliteManager.SATELLITE_RESULT_SUCCESS);
        if (Flags.carrierRoamingNbIotNtn()) {
            requestSatelliteEnabled(
                    true, false, SatelliteManager.SATELLITE_RESULT_SUCCESS);
        } else {
            requestSatelliteEnabled(
                    true, false, SatelliteManager.SATELLITE_RESULT_INVALID_ARGUMENTS);
        }

        callback.clearModemStates();
        turnRadioOff();
        grantSatellitePermission();
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(1));
        assertFalse(isSatelliteEnabled());

        // Cannot turn on satellite when radio is OFF
        requestSatelliteEnabled(true, true, SatelliteManager.SATELLITE_RESULT_INVALID_MODEM_STATE);
        requestSatelliteEnabled(false);

        turnRadioOn();
        grantSatellitePermission();
        assertFalse(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(1));
        assertFalse(isSatelliteEnabled());

        callback.clearModemStates();
        requestSatelliteEnabled(true, true, SatelliteManager.SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());

        callback.clearModemStates();
        requestSatelliteEnabled(false);
        assertTrue(callback.waitUntilModemOff());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        assertFalse(isSatelliteEnabled());

        sSatelliteManager.unregisterForModemStateChanged(callback);
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteDatagramReceivedAck() {
        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);

        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        // Compute next received datagramId using current ID and verify it is correct.
        long nextDatagramId = ((satelliteDatagramCallback.mDatagramId + 1)
                % DatagramController.MAX_DATAGRAM_ID);
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertThat(satelliteDatagramCallback.mDatagramId).isEqualTo(nextDatagramId);

        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
        revokeSatellitePermission();
    }

    @Ignore("b/377926997 - This test is failing due to the recent change in capabilities.")
    @Test
    public void testRequestSatelliteCapabilities() {
        logd("testRequestSatelliteCapabilities");
        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());

        final AtomicReference<SatelliteCapabilities> capabilities = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<SatelliteCapabilities, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(SatelliteCapabilities result) {
                        logd("testRequestSatelliteCapabilities: onResult");
                        capabilities.set(result);

                        assertNotNull(result);
                        assertNotNull(result.getSupportedRadioTechnologies());
                        assertThat(SUPPORTED_RADIO_TECHNOLOGIES)
                                .isEqualTo(result.getSupportedRadioTechnologies());
                        assertThat(POINTING_TO_SATELLITE_REQUIRED)
                                .isEqualTo(result.isPointingRequired());
                        assertThat(MAX_BYTES_PER_DATAGRAM)
                                .isEqualTo(result.getMaxBytesPerOutgoingDatagram());
                        assertNotNull(result.getAntennaPositionMap());
                        assertThat(ANTENNA_POSITION_MAP).isEqualTo(result.getAntennaPositionMap());
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("testRequestSatelliteCapabilities: onError");
                        errorCode.set(exception.getErrorCode());
                    }
                };

        sMockSatelliteServiceManager.setSupportedRadioTechnologies(
                new int[]{NTRadioTechnology.PROPRIETARY});
        sSatelliteManager.requestCapabilities(getContext().getMainExecutor(), receiver);

        revokeSatellitePermission();
    }

    @Test
    public void testSendSatelliteDatagram_success() {
        logd("testSendSatelliteDatagram_success");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        for (int i = 0; i < 5; i++) {
            logd("testSendSatelliteDatagram_success: moveToSendingState");
            assertTrue(isSatelliteEnabled());
            moveToSendingState();

            logd("testSendSatelliteDatagram_success: Disable satellite");
            SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                    startTransmissionUpdates();
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();

            // Datagram transfer state should change from SENDING to FAILED and then IDLE.
            assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(2));
            assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(2);
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                            1, SATELLITE_RESULT_REQUEST_ABORTED));
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            stopTransmissionUpdates(transmissionUpdateCallback);

            logd("testSendSatelliteDatagram_success: Enable satellite");
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(2));
            assertTrue(isSatelliteEnabled());
            sSatelliteManager.unregisterForModemStateChanged(callback);

            logd("testSendSatelliteDatagram_success: sendSatelliteDatagramSuccess");
            sendSatelliteDatagramSuccess(true, true);
        }
        revokeSatellitePermission();
    }

    @Test
    public void testSendSatelliteDatagram_failure() {
        logd("testSendSatelliteDatagram_failure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_failure: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        // Send satellite datagram
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_ERROR);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_failure: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_ERROR);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) SENDING to SENDING_FAILED
         * 2) SENDING_FAILED to IDLE
         */
        int expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(expectedNumOfEvents);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        0, SatelliteManager.SATELLITE_RESULT_ERROR));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testSendMultipleSatelliteDatagrams_success() {
        logd("testSendMultipleSatelliteDatagrams_success");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        // Wait to process datagrams so that datagrams are added to pending list.
        sMockSatelliteServiceManager.setWaitToSend(true);

        // Send three datagrams to observe how pendingCount is updated
        // after processing one datagram at a time.
        callback.clearSendDatagramRequested();
        LinkedBlockingQueue<Integer> resultListener1 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener1::offer);
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertTrue(callback.waitUntilOnSendDatagramRequested(1));
        assertEquals(1, callback.getNumOfSendDatagramRequestedChanges());
        assertEquals(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                callback.getSendDatagramRequestedType(0));

        callback.clearSendDatagramStateChanges();
        callback.clearSendDatagramRequested();
        LinkedBlockingQueue<Integer> resultListener2 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener2::offer);
        assertTrue(callback.waitUntilOnSendDatagramRequested(1));
        assertEquals(1, callback.getNumOfSendDatagramRequestedChanges());
        assertEquals(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                callback.getSendDatagramRequestedType(0));

        callback.clearSendDatagramRequested();
        LinkedBlockingQueue<Integer> resultListener3 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener3::offer);
        assertTrue(callback.waitUntilOnSendDatagramRequested(1));
        assertEquals(1, callback.getNumOfSendDatagramRequestedChanges());
        assertEquals(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                callback.getSendDatagramRequestedType(0));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send first datagram: SENDING to SENDING_SUCCESS
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());

        try {
            errorCode = resultListener1.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        int expectedNumOfEvents = 1;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));

        // Pending count is 2 as there are 2 datagrams to be sent.
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        2, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send second datagram: SENDING to SENDING_SUCCESS
        // callback.clearSendDatagramStateChanges();
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());

        try {
            errorCode = resultListener2.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        expectedNumOfEvents = 2;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        2, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send third datagram: SENDING - SENDING_SUCCESS - IDLE
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());

        try {
            errorCode = resultListener3.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getSendDatagramStateChange(3)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(4)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(5)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.setWaitToSend(false);
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testSendMultipleSatelliteDatagrams_failure() {
        logd("testSendMultipleSatelliteDatagrams_failure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_failure: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        // Wait to process datagrams so that datagrams are added to pending list.
        sMockSatelliteServiceManager.setWaitToSend(true);

        // Send three datagrams to observe how pendingCount is updated
        // after processing one datagram at a time.
        LinkedBlockingQueue<Integer> resultListener1 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener1::offer);
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        LinkedBlockingQueue<Integer> resultListener2 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener2::offer);
        LinkedBlockingQueue<Integer> resultListener3 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener3::offer);

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Set error and send first datagram: SENDING to SENDING_FAILED
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_ERROR);
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());

        try {
            errorCode = resultListener1.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_ERROR);

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());
        try {
            errorCode = resultListener2.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);


        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());
        try {
            errorCode = resultListener3.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendMultipleSatelliteDatagrams_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        assertTrue(callback.waitUntilOnSendDatagramStateChanged(5));
        // Pending count is 2 as there are 2 datagrams to be sent.
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        2, SatelliteManager.SATELLITE_RESULT_ERROR));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        2, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(3)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(4)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.setWaitToSend(false);
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        revokeSatellitePermission();
    }


    @Test
    public void testReceiveSatelliteDatagram() {
        logd("testReceiveSatelliteDatagram");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        for (int i = 0; i < 5; i++) {
            logd("testReceiveSatelliteDatagram: moveToReceivingState");
            assertTrue(isSatelliteEnabled());
            moveToReceivingState();

            logd("testReceiveSatelliteDatagram: Disable satellite");
            SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                    startTransmissionUpdates();
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();

            // Datagram transfer state should change from RECEIVING to IDLE.
            assertTrue(transmissionUpdateCallback
                    .waitUntilOnReceiveDatagramStateChanged(2));
            assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                    .isEqualTo(2);
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED,
                            0, SATELLITE_RESULT_REQUEST_ABORTED));
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            stopTransmissionUpdates(transmissionUpdateCallback);

            logd("testReceiveSatelliteDatagram: Enable satellite");
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(1));
            assertTrue(isSatelliteEnabled());
            sSatelliteManager.unregisterForModemStateChanged(callback);

            logd("testReceiveSatelliteDatagram: receiveSatelliteDatagramSuccess");
            receiveSatelliteDatagramSuccess();
        }
        revokeSatellitePermission();
    }

    @Test
    public void testReceiveMultipleSatelliteDatagrams() {
        logd("testReceiveMultipleSatelliteDatagrams");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveMultipleSatelliteDatagrams: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.clearPollPendingDatagramPermits();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();

        // Wait for the first datagram to be polled.
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Receive first datagram: Datagram state changes from RECEIVING to RECEIVE_SUCCESS
        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        sMockSatelliteServiceManager.clearPollPendingDatagramPermits();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 2);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());
        int expectedNumOfEvents = 2;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(expectedNumOfEvents));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        2, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Wait for the second datagram to be polled.
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Receive second datagram: Datagram state changes from RECEIVING to RECEIVE_SUCCESS
        sMockSatelliteServiceManager.clearPollPendingDatagramPermits();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 1);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());
        expectedNumOfEvents = 2;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(2));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        2, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(3)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Wait for the third datagram to be polled.
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Receive third datagram: Datagram state changes from RECEIVING - RECEIVE_SUCCESS - IDLE
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());
        expectedNumOfEvents = 3;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(4)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(5)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(6)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
        revokeSatellitePermission();
    }

    @Test
    public void testReceiveSatellitePositionUpdate() {
        logd("testReceiveSatellitePositionUpdate");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveSatellitePositionUpdate: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        transmissionUpdateCallback.clearPointingInfo();
        android.telephony.satellite.stub.PointingInfo pointingInfo =
                new android.telephony.satellite.stub.PointingInfo();
        pointingInfo.satelliteAzimuth = 10.5f;
        pointingInfo.satelliteElevation = 30.23f;
        PointingInfo expectedPointingInfo = new PointingInfo(10.5f, 30.23f);
        sMockSatelliteServiceManager.sendOnSatellitePositionChanged(pointingInfo);
        assertTrue(transmissionUpdateCallback.waitUntilOnSatellitePositionChanged(1));
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteAzimuthDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteAzimuthDegrees());
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteElevationDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteElevationDegrees());

        transmissionUpdateCallback.clearPointingInfo();
        sSatelliteManager.stopTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testReceiveMultipleSatellitePositionUpdates() {
        logd("testReceiveMultipleSatellitePositionUpdates");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveMultipleSatellitePositionUpdates: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        // Receive first position update
        transmissionUpdateCallback.clearPointingInfo();
        android.telephony.satellite.stub.PointingInfo pointingInfo =
                new android.telephony.satellite.stub.PointingInfo();
        pointingInfo.satelliteAzimuth = 10.5f;
        pointingInfo.satelliteElevation = 30.23f;
        PointingInfo expectedPointingInfo = new PointingInfo(10.5f, 30.23f);
        sMockSatelliteServiceManager.sendOnSatellitePositionChanged(pointingInfo);
        assertTrue(transmissionUpdateCallback.waitUntilOnSatellitePositionChanged(1));
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteAzimuthDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteAzimuthDegrees());
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteElevationDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteElevationDegrees());

        // Receive second position update
        transmissionUpdateCallback.clearPointingInfo();
        pointingInfo.satelliteAzimuth = 100;
        pointingInfo.satelliteElevation = 120;
        expectedPointingInfo = new PointingInfo(100, 120);
        sMockSatelliteServiceManager.sendOnSatellitePositionChanged(pointingInfo);
        assertTrue(transmissionUpdateCallback.waitUntilOnSatellitePositionChanged(1));
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteAzimuthDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteAzimuthDegrees());
        assertThat(transmissionUpdateCallback.mPointingInfo.getSatelliteElevationDegrees())
                .isEqualTo(expectedPointingInfo.getSatelliteElevationDegrees());

        transmissionUpdateCallback.clearPointingInfo();
        sSatelliteManager.stopTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        revokeSatellitePermission();
    }

    @Test
    public void testSendAndReceiveSatelliteDatagram_DemoMode_success() {
        logd("testSendSatelliteDatagram_DemoMode_success");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());

        String mText = "This is a test datagram message from user";
        for (int i = 0; i < 5; i++) {
            logd("testSendSatelliteDatagram_DemoMode_success: moveToSendingState");
            assertTrue(isSatelliteEnabled());
            moveToSendingState();

            logd("testSendSatelliteDatagram_DemoMode_success: Disable satellite");
            SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
            sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), stateCallback);
            assertTrue(stateCallback.waitUntilResult(1));

            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                    startTransmissionUpdates();
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
            stateCallback.clearModemStates();

            // Datagram transfer state should change from SENDING to FAILED and then IDLE.
            assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(2));
            assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(2);
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                            1, SATELLITE_RESULT_REQUEST_ABORTED));
            assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            stopTransmissionUpdates(transmissionUpdateCallback);

            logd("testSendSatelliteDatagram_DemoMode_success: Enable satellite");
            stateCallback.clearModemStates();
            requestSatelliteEnabledForDemoMode(true);
            assertTrue(stateCallback.waitUntilResult(2));
            assertTrue(isSatelliteEnabled());
            assertTrue(getIsEmergency());
            sSatelliteManager.unregisterForModemStateChanged(stateCallback);

            logd("testSendSatelliteDatagram_DemoMode_success: sendSatelliteDatagramSuccess");
            assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                    DatagramController.TIMEOUT_TYPE_DATAGRAM_DELAY_IN_DEMO_MODE,
                    TEST_DATAGRAM_DELAY_IN_DEMO_MODE_TIMEOUT_LONG_MILLIS));
            sendSatelliteDatagramDemoModeSuccess(mText);

            // Automatically triggering pollPendingSatelliteDatagrams after successfully sending
            // a callback back to sendSatelliteDatagram for demo mode
            sSatelliteManager.setDeviceAlignedWithSatellite(true);
            transmissionUpdateCallback = startTransmissionUpdates();
            SatelliteDatagramCallbackTest datagramCallback = new SatelliteDatagramCallbackTest();
            assertTrue(SatelliteManager.SATELLITE_RESULT_SUCCESS
                    == sSatelliteManager.registerForIncomingDatagram(getContext().getMainExecutor(),
                    datagramCallback));

            // Because pending count is 0, datagram transfer state changes from
            // IDLE -> RECEIVING -> RECEIVE_SUCCESS -> IDLE.
            int expectedNumOfEvents = 3;
            assertTrue(transmissionUpdateCallback
                    .waitUntilOnReceiveDatagramStateChanged(expectedNumOfEvents));
            assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                    .isEqualTo(expectedNumOfEvents);
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
            assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2)).isEqualTo(
                    new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                            0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

            datagramCallback.waitUntilResult(1);
            // Because demo mode is on, the received datagram should be the same as the
            // last sent datagram
            datagramCallback.waitUntilResult(1);
            assertTrue(Arrays.equals(
                    datagramCallback.mDatagram.getSatelliteDatagram(), mText.getBytes()));
            sSatelliteManager.unregisterForIncomingDatagram(datagramCallback);
            transmissionUpdateCallback.clearReceiveDatagramStateChanges();
            stopTransmissionUpdates(transmissionUpdateCallback);
        }

        sSatelliteManager.setDeviceAlignedWithSatellite(false);
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(true,
                DatagramController.TIMEOUT_TYPE_DATAGRAM_DELAY_IN_DEMO_MODE, 0));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());
        revokeSatellitePermission();
    }

    @Test
    public void testSendAndReceiveMultipleSatelliteDatagrams_DemoMode_success() {
        logd("testSendAndReceiveMultipleSatelliteDatagrams_DemoMode_success");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        assertTrue(sMockSatelliteServiceManager.setShouldSendDatagramToModemInDemoMode(false));

        // Enable demo mode
        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));
        requestSatelliteEnabled(false);
        assertTrue(stateCallback.waitUntilModemOff());
        assertFalse(isSatelliteEnabled());
        stateCallback.clearModemStates();
        requestSatelliteEnabledForDemoMode(true);
        assertTrue(stateCallback.waitUntilResult(2));
        assertTrue(isSatelliteEnabled());
        sSatelliteManager.setDeviceAlignedWithSatellite(true);

        SatelliteDatagramCallbackTest datagramCallback = new SatelliteDatagramCallbackTest();
        assertTrue(SatelliteManager.SATELLITE_RESULT_SUCCESS
                == sSatelliteManager.registerForIncomingDatagram(getContext().getMainExecutor(),
                datagramCallback));

        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_DATAGRAM_DELAY_IN_DEMO_MODE,
                TEST_DATAGRAM_DELAY_IN_DEMO_MODE_TIMEOUT_LONG_MILLIS));

        String[] datagramContentArr = new String[5];
        LinkedBlockingQueue<Integer>[] resultListenerArr = new LinkedBlockingQueue[5];
        for (int i = 0; i < 5; i++) {
            datagramContentArr[i] = "This is a test message " + i;
            resultListenerArr[i] = new LinkedBlockingQueue<>(1);
        }

        // Send satellite datagrams
        for (int i = 0; i < 5; i++) {
            SatelliteDatagram datagram = new SatelliteDatagram(datagramContentArr[i].getBytes());
            sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                    datagram, true, getContext().getMainExecutor(),
                    resultListenerArr[i]::offer);
        }

        // Wait for the results of the send requests
        for (int i = 0; i < 5; i++) {
            Integer errorCode;
            try {
                errorCode = resultListenerArr[i].poll(TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                fail("testSendAndReceiveMultipleSatelliteDatagrams_DemoMode_success: Got "
                        + "InterruptedException in waiting for the result of datagram " + i);
                return;
            }
            assertNotNull(errorCode);
            assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);
        }

        // Wait for the loop-back datagrams
        assertTrue(datagramCallback.waitUntilResult(5));
        assertEquals(5, datagramCallback.mDatagramList.size());

        // Verify the content of the loop-back datagrams
        for (int i = 0; i < 5; i++) {
            assertTrue(Arrays.equals(datagramContentArr[i].getBytes(),
                    datagramCallback.mDatagramList.get(i).getSatelliteDatagram()));
        }

        sSatelliteManager.unregisterForIncomingDatagram(datagramCallback);
        sSatelliteManager.setDeviceAlignedWithSatellite(false);
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(true,
                DatagramController.TIMEOUT_TYPE_DATAGRAM_DELAY_IN_DEMO_MODE, 0));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());
        requestSatelliteEnabled(false);
        assertTrue(stateCallback.waitUntilModemOff());
        revokeSatellitePermission();
    }

    @Test
    public void testSendSatelliteDatagram_DemoMode_failure() {
        logd("testSendSatelliteDatagram_DemoMode_failure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));

        // Enable satellite with demo mode on
        if (isSatelliteEnabled()) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            stateCallback.clearModemStates();
        }
        requestSatelliteEnabledForDemoMode(true);
        assertTrue(stateCallback.waitUntilResult(2));
        assertTrue(isSatelliteEnabled());
        assertTrue(getIsEmergency());
        assertTrue(sMockSatelliteServiceManager.setShouldSendDatagramToModemInDemoMode(true));

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_failure: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        // Send satellite datagram
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_ERROR);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_failure: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_ERROR);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         * 2) SENDING to SENDING_FAILED
         * 3) SENDING_FAILED to IDLE
         */
        int expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(expectedNumOfEvents);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        0, SatelliteManager.SATELLITE_RESULT_ERROR));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        sSatelliteManager.unregisterForModemStateChanged(stateCallback);
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModeRadios() {
        logd("testSatelliteModeRadios: start");
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION,
                        Manifest.permission.WRITE_SECURE_SETTINGS,
                        Manifest.permission.UWB_PRIVILEGED);
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));

        boolean originalEnabledState = isSatelliteEnabled();
        if (originalEnabledState) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            assertSatelliteEnabledInSettings(false);
            stateCallback.clearModemStates();
        }

        // Get satellite mode radios
        String originalSatelliteModeRadios =
                Settings.Global.getString(
                        getContext().getContentResolver(), Settings.Global.SATELLITE_MODE_RADIOS);
        logd("originalSatelliteModeRadios: " + originalSatelliteModeRadios);
        SatelliteModeRadiosUpdater satelliteRadiosModeUpdater =
                new SatelliteModeRadiosUpdater(getContext());

        try {
            identifyRadiosSensitiveToSatelliteMode();
            mTestSatelliteModeRadios = "";
            logd("test satelliteModeRadios: " + mTestSatelliteModeRadios);
            assertTrue(satelliteRadiosModeUpdater.setSatelliteModeRadios(mTestSatelliteModeRadios));

            // Enable Satellite and check whether all radios are disabled
            requestSatelliteEnabled(true, EXTERNAL_DEPENDENT_TIMEOUT);
            assertTrue(stateCallback.waitUntilResult(1));
            assertSatelliteEnabledInSettings(true);
            assertTrue(areAllRadiosDisabled());

            // Disable satellite and check whether all radios are set to their initial state
            setRadioExpectedState();
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilResult(1));
            assertSatelliteEnabledInSettings(false);
            assertTrue(areAllRadiosResetToInitialState());
        } finally {
            // Restore original satellite mode radios
            logd("restore original satellite mode radios");
            assertTrue(satelliteRadiosModeUpdater.setSatelliteModeRadios(
                    originalSatelliteModeRadios));
            sSatelliteManager.unregisterForModemStateChanged(stateCallback);
            unregisterSatelliteModeRadios();
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSatelliteModeRadios_noRadiosSensitiveToSatelliteMode() {
        logd("testSatelliteModeRadios_noRadiosSensitiveToSatelliteMode: start");
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION,
                        Manifest.permission.WRITE_SECURE_SETTINGS,
                        Manifest.permission.UWB_PRIVILEGED);
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));

        boolean originalEnabledState = isSatelliteEnabled();
        if (originalEnabledState) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            assertSatelliteEnabledInSettings(false);
            stateCallback.clearModemStates();
        }

        // Get satellite mode radios
        String originalSatelliteModeRadios =
                Settings.Global.getString(
                        getContext().getContentResolver(), Settings.Global.SATELLITE_MODE_RADIOS);
        logd("originalSatelliteModeRadios: " + originalSatelliteModeRadios);
        SatelliteModeRadiosUpdater satelliteRadiosModeUpdater =
                new SatelliteModeRadiosUpdater(getContext());

        try {
            mTestSatelliteModeRadios = "";
            logd("test satelliteModeRadios: " + mTestSatelliteModeRadios);
            assertTrue(satelliteRadiosModeUpdater.setSatelliteModeRadios(mTestSatelliteModeRadios));

            // Enable Satellite and check whether all radios are disabled
            setRadioExpectedState();
            requestSatelliteEnabled(true, EXTERNAL_DEPENDENT_TIMEOUT);
            assertTrue(stateCallback.waitUntilResult(1));
            assertSatelliteEnabledInSettings(true);
            assertTrue(areAllRadiosDisabled());
            assertTrue(areAllRadiosResetToInitialState());

            // Disable satellite and check whether all radios are set to their initial state
            setRadioExpectedState();
            stateCallback.clearModemStates();
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            assertSatelliteEnabledInSettings(false);
            assertTrue(areAllRadiosResetToInitialState());
            stateCallback.clearModemStates();
        } finally {
            // Restore original satellite mode radios
            logd("restore original satellite mode radios");
            assertTrue(satelliteRadiosModeUpdater.setSatelliteModeRadios(
                    originalSatelliteModeRadios));
            sSatelliteManager.unregisterForModemStateChanged(stateCallback);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSatelliteModeRadiosWithAirplaneMode() throws Exception {
        logd("testSatelliteModeRadiosWithAirplaneMode: start");
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION,
                        Manifest.permission.WRITE_SECURE_SETTINGS,
                        Manifest.permission.NETWORK_SETTINGS,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                        Manifest.permission.UWB_PRIVILEGED);
        assertTrue(isSatelliteProvisioned());

        ServiceStateRadioStateListener callback = new ServiceStateRadioStateListener(
                sTelephonyManager.getServiceState(), sTelephonyManager.getRadioPowerState());
        sTelephonyManager.registerTelephonyCallback(Runnable::run, callback);
        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));

        boolean originalEnabledState = isSatelliteEnabled();
        if (originalEnabledState) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
            stateCallback.clearModemStates();
        }

        ConnectivityManager connectivityManager =
                getContext().getSystemService(ConnectivityManager.class);

        // Get original satellite mode radios and original airplane mode
        String originalSatelliteModeRadios =
                Settings.Global.getString(
                        getContext().getContentResolver(), Settings.Global.SATELLITE_MODE_RADIOS);
        logd("originalSatelliteModeRadios: " + originalSatelliteModeRadios);
        boolean originalAirplaneMode = Settings.Global.getInt(
                getContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON) != 0;
        SatelliteModeRadiosUpdater satelliteModeRadiosUpdater =
                new SatelliteModeRadiosUpdater(getContext());

        try {
            identifyRadiosSensitiveToSatelliteMode();
            mTestSatelliteModeRadios = "";
            logd("test satelliteModeRadios: " + mTestSatelliteModeRadios);
            assertTrue(satelliteModeRadiosUpdater.setSatelliteModeRadios(mTestSatelliteModeRadios));

            // Enable Satellite and check whether all radios are disabled
            requestSatelliteEnabled(true, EXTERNAL_DEPENDENT_TIMEOUT);
            assertTrue(stateCallback.waitUntilResult(1));
            assertTrue(isSatelliteEnabled());
            assertSatelliteEnabledInSettings(true);
            assertTrue(areAllRadiosDisabled());

            // Enable airplane mode, check whether all radios are disabled and
            // also satellite mode is disabled
            connectivityManager.setAirplaneMode(true);
            // Wait for telephony radio power off
            callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_OFF);
            // Wait for satellite mode state changed
            assertTrue(stateCallback.waitUntilResult(1));
            assertFalse(isSatelliteEnabled());
            assertSatelliteEnabledInSettings(false);
            assertTrue(areAllRadiosDisabled());

            // Disable airplane mode, check whether all radios are set to their initial state
            setRadioExpectedState();
            connectivityManager.setAirplaneMode(false);
            callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_ON);
            assertTrue(areAllRadiosResetToInitialState());
        } finally {
            // Restore original satellite mode radios
            logd("restore original satellite mode radios and original airplane mode");
            connectivityManager.setAirplaneMode(originalAirplaneMode);
            callback.waitForRadioStateIntent(originalAirplaneMode
                    ? TelephonyManager.RADIO_POWER_OFF : TelephonyManager.RADIO_POWER_ON);
            assertTrue(satelliteModeRadiosUpdater.setSatelliteModeRadios(
                    originalSatelliteModeRadios));
            sTelephonyManager.unregisterTelephonyCallback(callback);
            sSatelliteManager.unregisterForModemStateChanged(stateCallback);
            unregisterSatelliteModeRadios();
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSendSatelliteDatagram_DemoMode_not_Aligned() {
        logd("testSendSatelliteDatagram_DemoMode_not_Aligned");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));
        assertTrue(sMockSatelliteServiceManager.setShouldSendDatagramToModemInDemoMode(true));
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        // Enable satellite with demo mode on
        if (isSatelliteEnabled()) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            stateCallback.clearModemStates();
        }
        requestSatelliteEnabledForDemoMode(true);
        assertTrue(stateCallback.waitUntilResult(2));
        assertTrue(isSatelliteEnabled());
        assertTrue(getIsEmergency());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in "
                    + "waiting for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        // Send satellite datagram and satellite is not aligned.
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_ALIGN, TEST_SATELLITE_DEVICE_ALIGN_TIMEOUT_MILLIS));
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sSatelliteManager.setDeviceAlignedWithSatellite(false);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in"
                    + " waiting for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         * 2) SENDING to SENDING_FAILED
         * 3) SENDING_FAILED to IDLE
         */
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(3);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        0, SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Move to sending state and wait for satellite alignment forever
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_ALIGN,
                TEST_SATELLITE_DEVICE_ALIGN_FOREVER_TIMEOUT_MILLIS));
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        // No response for the request sendDatagram received
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in"
                    + " waiting for the sendDatagram result code");
            return;
        }
        assertNull(errorCode);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         */
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));
        callback.clearSendDatagramStateChanges();
        sSatelliteManager.setDeviceAlignedWithSatellite(true);

        // Satellite is aligned now. We should get the response of the request
        // sendSatelliteDatagrams.
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in"
                    + " waiting for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) SENDING to SEND_SUCCESS
         * 2) SEND_SUCCESS to IDLE
         */
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(2));
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // Move to sending state and wait for satellite alignment forever again
        sSatelliteManager.setDeviceAlignedWithSatellite(false);
        callback.clearSendDatagramStateChanges();
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        // No response for the request sendDatagram received
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in"
                    + " waiting for the sendDatagram result code");
            return;
        }
        assertNull(errorCode);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         */
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        stateCallback.clearModemStates();
        requestSatelliteEnabled(false);
        assertTrue(stateCallback.waitUntilModemOff());
        stateCallback.clearModemStates();

        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in"
                    + " waiting for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SATELLITE_RESULT_REQUEST_ABORTED);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) SENDING to SENDING_FAILED
         * 2) SENDING_FAILED to IDLE
         */
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(2));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(2);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        1, SATELLITE_RESULT_REQUEST_ABORTED));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);

        // Restore satellite device align time out to default value.
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(true,
                DatagramController.TIMEOUT_TYPE_ALIGN, 0));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());
        sSatelliteManager.unregisterForModemStateChanged(stateCallback);
        revokeSatellitePermission();
    }

    @Test
    public void testReceiveSatelliteDatagram_DemoMode_not_Aligned() {
        logd("testReceiveSatelliteDatagram_DemoMode_not_Aligned");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());

        // Request enable satellite with demo mode on
        if (isSatelliteEnabled()) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            stateCallback.clearModemStates();
        }
        requestSatelliteEnabledForDemoMode(true);
        assertTrue(stateCallback.waitUntilResult(2));
        assertTrue(isSatelliteEnabled());
        assertTrue(getIsEmergency());

        sSatelliteManager.setDeviceAlignedWithSatellite(true);
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_DATAGRAM_DELAY_IN_DEMO_MODE,
                TEST_DATAGRAM_DELAY_IN_DEMO_MODE_TIMEOUT_LONG_MILLIS));

        // Send satellite datagram to compare with the received datagram in demo mode
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveSatelliteDatagram_DemoMode_not_Aligned: Got InterruptedException in "
                    + "waiting for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        Log.d(TAG, "testReceiveSatelliteDatagram_DemoMode_not_Aligned: sendDatagram "
                + "errorCode=" + errorCode);

        // Test poll pending satellite datagram for demo mode while it is not aligned
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sSatelliteManager.setDeviceAlignedWithSatellite(false);
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_ALIGN, TEST_SATELLITE_DEVICE_ALIGN_TIMEOUT_MILLIS));

        // Datagram transfer state should change from RECEIVING to IDLE.
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(3));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED,
                        0, SatelliteManager.SATELLITE_RESULT_NOT_REACHABLE));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        requestSatelliteEnabled(false);
        assertTrue(stateCallback.waitUntilModemOff());
        stopTransmissionUpdates(transmissionUpdateCallback);
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(true,
                DatagramController.TIMEOUT_TYPE_ALIGN, 0));
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(true,
                DatagramController.TIMEOUT_TYPE_DATAGRAM_DELAY_IN_DEMO_MODE, 0));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());
        sSatelliteManager.unregisterForModemStateChanged(stateCallback);
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemBusy_modemSendingDatagram_pollingFailure() {
        logd("testSatelliteModemBusy_modemSendingDatagram_pollingFailure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        // Wait to process datagrams so that datagrams are added to pending list
        // and modem is busy sending datagrams
        sMockSatelliteServiceManager.setWaitToSend(true);

        LinkedBlockingQueue<Integer> sendResultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                sendResultListener::offer);

        LinkedBlockingQueue<Integer> pollResultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.pollPendingDatagrams(getContext().getMainExecutor(),
                pollResultListener::offer);

        Integer errorCode;
        try {
            errorCode = pollResultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemBusy_modemSendingDatagram_pollingFailure: Got "
                    + "InterruptedException in waiting for the pollPendingDatagrams "
                    + "result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_MODEM_BUSY);

        // Send datagram successfully to bring sending state back to IDLE.
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sMockSatelliteServiceManager.setWaitToSend(false);
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemBusy_modemPollingDatagrams_pollingFailure() {
        logd("testSatelliteModemBusy_modemPollingDatagrams_pollingFailure");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> pollResultListener1 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.pollPendingDatagrams(getContext().getMainExecutor(),
                pollResultListener1::offer);

        // As we already got one polling request, this second polling request would fail
        LinkedBlockingQueue<Integer> pollResultListener2 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.pollPendingDatagrams(getContext().getMainExecutor(),
                pollResultListener2::offer);

        Integer errorCode;
        try {
            errorCode = pollResultListener2.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemBusy_modemSendingDatagram_pollingFailure: Got "
                    + "InterruptedException in waiting for the pollPendingDatagrams "
                    + "result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_MODEM_BUSY);

        // Receive one datagram successfully to bring receiving state back to IDLE.
        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemBusy_modemPollingDatagram_sendingDelayed() {
        logd("testSatelliteModemBusy_modemPollingDatagram_sendingDelayed");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemBusy_modemPollingDatagram_sendingDelayed: "
                    + "Got InterruptedException in waiting for the "
                    + "startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        transmissionUpdateCallback.clearSendDatagramStateChanges();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();

        LinkedBlockingQueue<Integer> pollResultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.pollPendingDatagrams(getContext().getMainExecutor(),
                pollResultListener::offer);
        // Datagram transfer state changes from IDLE -> RECEIVING.
        assertSingleReceiveDatagramStateChanged(transmissionUpdateCallback,
                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                0, SatelliteManager.SATELLITE_RESULT_SUCCESS);

        LinkedBlockingQueue<Integer> sendResultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                sendResultListener::offer);
        // Sending datagram will be delayed as modem is in RECEIVING state
        assertFalse(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        // As pending count is 0, datagram transfer state changes from
        // RECEIVING -> RECEIVE_SUCCESS -> IDLE.
        int expectedNumOfEvents = 2;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(expectedNumOfEvents));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        // As polling is completed, now modem will start sending datagrams
        expectedNumOfEvents = 3;
        assertTrue(transmissionUpdateCallback.
                waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        transmissionUpdateCallback.clearSendDatagramStateChanges();
        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.stopTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
        revokeSatellitePermission();
    }

    @Ignore("b/399928350 - Need to fix and re-enable this test.")
    @Test
    public void testRebindToSatelliteService() {
        grantSatellitePermission();
        assertTrue(isSatelliteSupported());

        assertTrue(sMockSatelliteServiceManager.connectExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteServiceConnected(1));

        // Forcefully stop the external satellite service.
        assertTrue(sMockSatelliteServiceManager.stopExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager
                .waitForExternalSatelliteServiceDisconnected(1));

        // Reconnect CTS to the external satellite service.
        assertTrue(sMockSatelliteServiceManager.setupExternalSatelliteService());
        // Telephony should rebind to the external satellite service after the binding died.
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteServiceConnected(1));

        // Restore original binding states
        sMockSatelliteServiceManager.resetSatelliteService();
        assertTrue(sMockSatelliteServiceManager.connectSatelliteService());

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        assertTrue(callback.waitUntilModemOff(EXTERNAL_DEPENDENT_TIMEOUT));
        callback.clearModemStates();

        // Telephony will disable satellite whenever the vendor service is connected.
        // This will interfer the below tests and make the test flaky.
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.stopExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager
                .waitForExternalSatelliteServiceDisconnected(1));
        sSatelliteManager.unregisterForModemStateChanged(callback);

        revokeSatellitePermission();
    }

    @Ignore("b/377927857 - This test is flaky.")
    @Test
    public void testRebindToSatelliteGatewayService() {
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        if (isSatelliteEnabled()) {
            callback.clearModemStates();
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(1));
            assertFalse(isSatelliteEnabled());
        }

        assertTrue(sMockSatelliteServiceManager.connectExternalSatelliteGatewayService());
        callback.clearModemStates();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceConnected(1));

        // Forcefully stop the external satellite gateway service.
        assertTrue(sMockSatelliteServiceManager.stopExternalSatelliteGatewayService());
        assertTrue(sMockSatelliteServiceManager
                .waitForExternalSatelliteGatewayServiceDisconnected(1));

        // Reconnect CTS to the external satellite gateway service.
        assertTrue(sMockSatelliteServiceManager.setupExternalSatelliteGatewayService());
        // Telephony should rebind to the external satellite gateway service after the binding died.
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteGatewayServiceConnected(1));

        sSatelliteManager.unregisterForModemStateChanged(callback);
        assertTrue(sMockSatelliteServiceManager.restoreSatelliteGatewayServicePackageName());

        revokeSatellitePermission();
    }

    @Ignore("b/399928350 - Need to fix and re-enable this test.")
    @Test
    public void testSatelliteAttachEnabledForCarrier() {

        logd("testSatelliteAttachEnabledForCarrier");
        grantSatellitePermission();
        beforeSatelliteForCarrierTest();
        @SatelliteManager.SatelliteResult int expectedSuccess =
                SatelliteManager.SATELLITE_RESULT_SUCCESS;
        @SatelliteManager.SatelliteResult int expectedError;

        List<String> allPlmnListBeforeCarrierConfigOverride = getCarrierPlmnList();

        /* Test when satellite is not supported in the carrier config */
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, false);

        overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);
        requestSatelliteAttachEnabledForCarrier(true, SatelliteManager.SATELLITE_RESULT_SUCCESS);

        Pair<Boolean, Integer> pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(true, pair.first.booleanValue());
        assertNull(pair.second);

        /* Test when satellite is supported in the carrier config */
        setSatelliteErrorBasedOnHalVersion(expectedSuccess);
        bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        PersistableBundle plmnBundle = new PersistableBundle();
        int[] intArray1 = {3, 5};
        int[] intArray2 = {3};
        plmnBundle.putIntArray("123411", intArray1);
        plmnBundle.putIntArray("123412", intArray2);
        bundle.putPersistableBundle(
                CarrierConfigManager.KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                plmnBundle);
        overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);

        ArrayList<String> expectedCarrierPlmnList = new ArrayList<>();
        expectedCarrierPlmnList.add("123411");
        expectedCarrierPlmnList.add("123412");
        assertTrue(waitForEventOnSetSatellitePlmn(1));

        List<String> carrierPlmnList = getCarrierPlmnList();
        assertNotNull(carrierPlmnList);
        assertEquals(expectedCarrierPlmnList, carrierPlmnList);
        List<String> satellitePlmnListFromOverlayConfig =
                sMockSatelliteServiceManager.getPlmnListFromOverlayConfig();
        List<String> expectedAllSatellitePlmnList =
                SatelliteServiceUtils.mergeStrLists(
                        carrierPlmnList,
                        satellitePlmnListFromOverlayConfig,
                        allPlmnListBeforeCarrierConfigOverride);
        List<String> allSatellitePlmnList = getAllSatellitePlmnList();
        assertNotNull(allSatellitePlmnList);
        boolean listsAreEqual =
                expectedAllSatellitePlmnList.containsAll(allSatellitePlmnList)
                        && allSatellitePlmnList.containsAll(expectedAllSatellitePlmnList);
        assertTrue(listsAreEqual);

        requestSatelliteAttachEnabledForCarrier(true, expectedSuccess);

        pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(true, pair.first.booleanValue());
        assertNull(pair.second);

        /* Test when satellite is supported, and requested satellite disabled */
        requestSatelliteAttachEnabledForCarrier(false, expectedSuccess);
        assertEquals(false, getIsSatelliteEnabledForCarrierFromMockService());
        pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(false, pair.first.booleanValue());
        assertNull(pair.second);

        /* Test when satellite is supported, but modem returns INVALID_MODEM_STATE */
        expectedError = SatelliteManager.SATELLITE_RESULT_INVALID_MODEM_STATE;
        setSatelliteErrorBasedOnHalVersion(expectedError);
        requestSatelliteAttachEnabledForCarrier(true, expectedError);

        pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(true, pair.first.booleanValue());
        assertNull(pair.second);

        /* Test when satellite is supported, and requested satellite disabled */
        expectedError = SatelliteManager.SATELLITE_RESULT_SUCCESS;
        requestSatelliteAttachEnabledForCarrier(false, expectedError);
        assertEquals(false, getIsSatelliteEnabledForCarrierFromMockService());
        pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(false, pair.first.booleanValue());
        assertNull(pair.second);

        /* Test when satellite is supported, but modem returns RADIO_NOT_AVAILABLE */
        expectedError = SatelliteManager.SATELLITE_RESULT_RADIO_NOT_AVAILABLE;
        setSatelliteErrorBasedOnHalVersion(expectedError);
        requestSatelliteAttachEnabledForCarrier(true, expectedError);

        pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(true, pair.first.booleanValue());
        assertNull(pair.second);

        afterSatelliteForCarrierTest();
        revokeSatellitePermission();
    }

    @Ignore("b/399928350 - Need to fix and re-enable this test.")
    @Test
    public void testSatelliteAttachRestrictionForCarrier() {
        logd("testSatelliteAttachRestrictionForCarrier");
        grantSatellitePermission();
        beforeSatelliteForCarrierTest();
        clearSatelliteEnabledForCarrier();
        @SatelliteManager.SatelliteResult int expectedSuccess =
                SatelliteManager.SATELLITE_RESULT_SUCCESS;

        /* Test when satellite is supported but there is a restriction reason */
        setSatelliteErrorBasedOnHalVersion(expectedSuccess);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);
        int restrictionReason = SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION;
        requestAddSatelliteAttachRestrictionForCarrier(restrictionReason,
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        verifySatelliteAttachRestrictionForCarrier(restrictionReason, true);
        requestSatelliteAttachEnabledForCarrier(true, expectedSuccess);
        Pair<Boolean, Integer> pair = requestIsSatelliteAttachEnabledForCarrier();
        assertEquals(true, pair.first.booleanValue());
        assertNull(pair.second);
        assertEquals(false, getIsSatelliteEnabledForCarrierFromMockService());

        /* If the restriction reason 'GEOLOCATION' is removed and the restriction reason is
           empty, re-evaluate and trigger enable/disable again */
        requestRemoveSatelliteAttachRestrictionForCarrier(restrictionReason,
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        verifySatelliteAttachRestrictionForCarrier(restrictionReason, false);
        assertEquals(true, getIsSatelliteEnabledForCarrierFromMockService());

        /* If the restriction reason 'GEOLOCATION' is added and the restriction reason becomes
           'GEOLOCATION', re-evaluate and trigger enable/disable again */
        requestAddSatelliteAttachRestrictionForCarrier(restrictionReason,
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        verifySatelliteAttachRestrictionForCarrier(restrictionReason, true);
        assertEquals(false, getIsSatelliteEnabledForCarrierFromMockService());

        /* If the restriction reason 'ENTITLEMENT' is added and the restriction reasons become
           ‘GEOLOCATION’ and ‘ENTITLEMENT.’ re-evaluate and trigger enable/disable again */
        restrictionReason = SATELLITE_COMMUNICATION_RESTRICTION_REASON_ENTITLEMENT;
        requestAddSatelliteAttachRestrictionForCarrier(restrictionReason,
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        verifySatelliteAttachRestrictionForCarrier(restrictionReason, true);
        assertEquals(false, getIsSatelliteEnabledForCarrierFromMockService());

        /* If the restriction reason 'ENTITLEMENT' is removed and the restriction reason becomes
           ‘GEOLOCATION’, re-evaluate and trigger enable/disable again */
        requestRemoveSatelliteAttachRestrictionForCarrier(restrictionReason,
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        restrictionReason = SATELLITE_COMMUNICATION_RESTRICTION_REASON_GEOLOCATION;
        verifySatelliteAttachRestrictionForCarrier(restrictionReason, true);
        assertEquals(false, getIsSatelliteEnabledForCarrierFromMockService());

        /* If the restriction reason 'GEOLOCATION' is removed and the restriction reason becomes
            empty, re-evaluate and trigger enable/disable again */
        requestRemoveSatelliteAttachRestrictionForCarrier(restrictionReason,
                SatelliteManager.SATELLITE_RESULT_SUCCESS);
        verifySatelliteAttachRestrictionForCarrier(restrictionReason, false);
        assertEquals(true, getIsSatelliteEnabledForCarrierFromMockService());

        afterSatelliteForCarrierTest();
        revokeSatellitePermission();
    }

    @Test
    public void testNtnSignalStrength() {
        logd("testNtnSignalStrength: start");
        grantSatellitePermission();

        NtnSignalStrengthCallbackTest ntnSignalStrengthCallbackTest =
                new NtnSignalStrengthCallbackTest();

        /* register callback for non-terrestrial network signal strength changed event */
        sSatelliteManager.registerForNtnSignalStrengthChanged(getContext().getMainExecutor(),
                ntnSignalStrengthCallbackTest);

        @NtnSignalStrength.NtnSignalStrengthLevel int expectedLevel =
                NtnSignalStrength.NTN_SIGNAL_STRENGTH_NONE;
        @SatelliteManager.SatelliteResult int expectedError;
        setSatelliteError(SatelliteManager.SATELLITE_RESULT_SUCCESS);
        setNtnSignalStrength(expectedLevel);
        Pair<NtnSignalStrength, Integer> pairResult = requestNtnSignalStrength();
        assertEquals(expectedLevel, pairResult.first.getLevel());
        assertNull(pairResult.second);

        expectedLevel = NtnSignalStrength.NTN_SIGNAL_STRENGTH_GOOD;
        expectedError = SATELLITE_RESULT_MODEM_ERROR;
        setSatelliteError(expectedError);
        setNtnSignalStrength(expectedLevel);
        pairResult = requestNtnSignalStrength();
        assertNull(pairResult.first);
        assertEquals(expectedError, pairResult.second.intValue());

        expectedLevel = NtnSignalStrength.NTN_SIGNAL_STRENGTH_GOOD;
        expectedError = SatelliteManager.SATELLITE_RESULT_SUCCESS;
        setSatelliteError(expectedError);
        setNtnSignalStrength(expectedLevel);
        pairResult = requestNtnSignalStrength();
        assertEquals(expectedLevel, pairResult.first.getLevel());
        assertNull(pairResult.second);

        /* As non-terrestrial network signal strength is cached in framework, simple set won't
        affect cached value */
        expectedLevel = NtnSignalStrength.NTN_SIGNAL_STRENGTH_GREAT;
        setNtnSignalStrength(expectedLevel);
        pairResult = requestNtnSignalStrength();
        assertNotEquals(expectedLevel, pairResult.first.getLevel());
        assertNull(pairResult.second);

        /* Cache will be updated when non-terrestrial network signal strength changed event comes */
        ntnSignalStrengthCallbackTest.drainPermits();
        sendOnNtnSignalStrengthChanged(expectedLevel);
        assertTrue(ntnSignalStrengthCallbackTest.waitUntilResult(1));
        pairResult = requestNtnSignalStrength();
        assertEquals(expectedLevel, pairResult.first.getLevel());
        assertNull(pairResult.second);
        assertEquals(expectedLevel, ntnSignalStrengthCallbackTest.mNtnSignalStrength.getLevel());

        ntnSignalStrengthCallbackTest.drainPermits();
        expectedLevel = NtnSignalStrength.NTN_SIGNAL_STRENGTH_MODERATE;
        sendOnNtnSignalStrengthChanged(expectedLevel);
        assertTrue(ntnSignalStrengthCallbackTest.waitUntilResult(1));
        pairResult = requestNtnSignalStrength();
        assertEquals(expectedLevel, pairResult.first.getLevel());
        assertNull(pairResult.second);
        assertEquals(expectedLevel, ntnSignalStrengthCallbackTest.mNtnSignalStrength.getLevel());

        /* Initialize the non-terrestrial signal strength cache in the framework */
        ntnSignalStrengthCallbackTest.drainPermits();
        expectedLevel = NtnSignalStrength.NTN_SIGNAL_STRENGTH_NONE;
        sendOnNtnSignalStrengthChanged(expectedLevel);
        assertTrue(ntnSignalStrengthCallbackTest.waitUntilResult(1));
        pairResult = requestNtnSignalStrength();
        assertEquals(expectedLevel, pairResult.first.getLevel());
        assertNull(pairResult.second);
        assertEquals(expectedLevel, ntnSignalStrengthCallbackTest.mNtnSignalStrength.getLevel());

        /* unregister non-terrestrial network signal strength changed event callback */
        sSatelliteManager.unregisterForNtnSignalStrengthChanged(ntnSignalStrengthCallbackTest);

        revokeSatellitePermission();
    }

    @Ignore("b/377926997 - This test is failing due to the recent change in capabilities.")
    @Test
    public void testRegisterForCapabilitiesChanged() {
        logd("testRegisterForCapabilitiesChanged: start");
        grantSatellitePermission();

        android.telephony.satellite.stub.SatelliteCapabilities capabilities =
                new android.telephony.satellite.stub.SatelliteCapabilities();
        int[] supportedRadioTechnologies =
                new int[]{android.telephony.satellite.stub.NTRadioTechnology.NB_IOT_NTN};
        capabilities.supportedRadioTechnologies = supportedRadioTechnologies;
        int[] antennaPositionKeys = new int[]{
                SatelliteManager.DISPLAY_MODE_OPENED, SatelliteManager.DISPLAY_MODE_CLOSED};
        AntennaPosition[] antennaPositionValues =
                new AntennaPosition[] {
                    new AntennaPosition(
                            new AntennaDirection(1, 1, 1),
                            SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT),
                    new AntennaPosition(
                            new AntennaDirection(2, 2, 2),
                            SatelliteManager.DEVICE_HOLD_POSITION_LANDSCAPE_LEFT)
                };

        capabilities.isPointingRequired = POINTING_TO_SATELLITE_REQUIRED;
        capabilities.maxBytesPerOutgoingDatagram = MAX_BYTES_PER_DATAGRAM;
        capabilities.antennaPositionKeys = antennaPositionKeys;
        capabilities.antennaPositionValues = antennaPositionValues;
        SatelliteCapabilities frameworkCapabilities =
                SatelliteServiceUtils.fromSatelliteCapabilities(capabilities);
        SatelliteCapabilitiesCallbackTest satelliteCapabilitiesCallbackTest =
                new SatelliteCapabilitiesCallbackTest();

        /* register callback for satellite capabilities changed event */
        @SatelliteManager.SatelliteResult int registerError =
                sSatelliteManager.registerForCapabilitiesChanged(
                        getContext().getMainExecutor(), satelliteCapabilitiesCallbackTest);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);

        /* Verify whether capability changed event has received */
        sendOnSatelliteCapabilitiesChanged(capabilities);
        assertTrue(satelliteCapabilitiesCallbackTest.waitUntilResult(1));
        assertTrue(frameworkCapabilities
                .equals(satelliteCapabilitiesCallbackTest.mSatelliteCapabilities));

        /* Verify whether notified and requested capabilities are equal */
        Pair<SatelliteCapabilities, Integer> pairResult = requestSatelliteCapabilities();
        assertTrue(frameworkCapabilities.equals(pairResult.first));
        assertNull(pairResult.second);

        /* datagram size has changed */
        capabilities.maxBytesPerOutgoingDatagram = MAX_BYTES_PER_DATAGRAM + 1;
        frameworkCapabilities = SatelliteServiceUtils.fromSatelliteCapabilities(capabilities);

        /* Verify changed capabilities are reflected */
        sendOnSatelliteCapabilitiesChanged(capabilities);
        assertTrue(satelliteCapabilitiesCallbackTest.waitUntilResult(1));
        assertTrue(frameworkCapabilities
                .equals(satelliteCapabilitiesCallbackTest.mSatelliteCapabilities));

        pairResult = requestSatelliteCapabilities();
        assertTrue(frameworkCapabilities.equals(pairResult.first));
        assertNull(pairResult.second);

        /* Initialize Radio technology */
        supportedRadioTechnologies =
                new int[]{android.telephony.satellite.stub.NTRadioTechnology.PROPRIETARY};
        capabilities.supportedRadioTechnologies = supportedRadioTechnologies;
        sendOnSatelliteCapabilitiesChanged(capabilities);
        /* unregister non-terrestrial network signal strength changed event callback */
        sSatelliteManager.unregisterForCapabilitiesChanged(
                satelliteCapabilitiesCallbackTest);

        revokeSatellitePermission();
    }

    @Test
    public void testRegisterForSelectedNbIotSatelliteSubscriptionChanged() {
        logd("testRegisterForSelectedNbIotSatelliteSubscriptionChanged: start");
        grantSatellitePermission();

        SelectedNbIotSatelliteSubscriptionCallbackTest
                selectedNbIotSatelliteSubscriptionCallbackTest =
                        new SelectedNbIotSatelliteSubscriptionCallbackTest();

        /* register callback for satellite subscription id changed event */
        @SatelliteManager.SatelliteResult int registerError =
                sSatelliteManager.registerForSelectedNbIotSatelliteSubscriptionChanged(
                        getContext().getMainExecutor(),
                        selectedNbIotSatelliteSubscriptionCallbackTest);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);

        /* Wait for the callback to be called */
        assertTrue(selectedNbIotSatelliteSubscriptionCallbackTest.waitUntilResult(1));

        /* Verify whether notified and requested subscription are equal */
        Pair<Integer, Integer> pairResult = requestSelectedNbIotSatelliteSubscriptionId();
        assertEquals(selectedNbIotSatelliteSubscriptionCallbackTest.mSelectedSubId,
                (long) pairResult.first);
        assertNull(pairResult.second);

        /* unregister */
        sSatelliteManager.unregisterForSelectedNbIotSatelliteSubscriptionChanged(
                selectedNbIotSatelliteSubscriptionCallbackTest);

        revokeSatellitePermission();
    }

    @Test
    public void testSendSatelliteDatagram_DemoMode_WithDeviceConfig() {
        logd("testSendSatelliteDatagram_DemoMode_WithDeviceConfig");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));

        // Enable satellite with demo mode on
        if (isSatelliteEnabled()) {
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            stateCallback.clearModemStates();
        }
        requestSatelliteEnabledForDemoMode(true);
        assertTrue(stateCallback.waitUntilResult(2));
        assertTrue(isSatelliteEnabled());
        assertTrue(getIsEmergency());
        assertTrue(sMockSatelliteServiceManager.setShouldSendDatagramToModemInDemoMode(false));

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_WithDeviceConfig: Got InterruptedException "
                    + "in waiting for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        // Send satellite datagram
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_DATAGRAM_DELAY_IN_DEMO_MODE,
                TEST_DATAGRAM_DELAY_IN_DEMO_MODE_TIMEOUT_MILLIS));
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.setErrorCode(SatelliteManager.SATELLITE_RESULT_SUCCESS);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);
        sSatelliteManager.setDeviceAlignedWithSatellite(true);
        // Satellite datagram does not send to satellite modem.
        sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(0);
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_DemoMode_WithDeviceConfig: Got InterruptedException "
                    + "in waiting for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         * 2) SENDING to SEND_SUCCESS
         * 3) SEND_SUCCESS to IDLE
         */
        int expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(expectedNumOfEvents);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
        sSatelliteManager.unregisterForModemStateChanged(stateCallback);
        assertTrue(sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(true,
                DatagramController.TIMEOUT_TYPE_DATAGRAM_DELAY_IN_DEMO_MODE, 0));
        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteAccessControl() {
        grantSatellitePermission();
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache(
                        "cache_clear_and_not_allowed"));
        SatelliteCommunicationAccessStateCallbackTest allowStatecallback =
                new SatelliteCommunicationAccessStateCallbackTest();
        long registerResultAllowState =
                sSatelliteManager.registerForCommunicationAccessStateChanged(
                        getContext().getMainExecutor(), allowStatecallback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResultAllowState);
        assertTrue(allowStatecallback.waitUntilResult(1));
        assertFalse(allowStatecallback.isAllowed);

        /*
        // Test access controller using cached country codes
        assertTrue(sMockSatelliteServiceManager.setSatelliteAccessControlOverlayConfigs(
                false, true, null, 0, SATELLITE_COUNTRY_CODES));

        verifyIsSatelliteAllowed(true);

        // Allowed case
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false, null, null, "US",
                SystemClock.elapsedRealtimeNanos()));
        verifyIsSatelliteAllowed(true);
        assertTrue(allowStatecallback.waitUntilResult(0));

        // Disallowed case
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false, null, null, "IN",
                SystemClock.elapsedRealtimeNanos()));
        verifyIsSatelliteAllowed(false);
        assertTrue(allowStatecallback.waitUntilResult(1));
        assertFalse(allowStatecallback.isAllowed);
        */

        // Test access controller using on-device data
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false, null, null, null, 0));
        assertTrue(sMockSatelliteServiceManager.setSatelliteAccessControlOverlayConfigs(
                false, true, SATELLITE_S2_FILE, TimeUnit.MINUTES.toNanos(0), "US", null));
        registerTestLocationProvider();

        // Set current location to Google San Diego office
        setTestProviderLocation(32.909808231041644, -117.18185788819781);
        verifyIsSatelliteAllowed(true);
        assertTrue(allowStatecallback.waitUntilResult(1));
        assertTrue(allowStatecallback.isAllowed);

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        callback.clearModemStates();
        if (!isSatelliteEnabled()) {
            requestSatelliteEnabled(true);
            assertTrue(callback.waitUntilResult(2));
            assertEquals(2, callback.getTotalCountOfModemStates());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                    callback.getModemState(0));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.getModemState(1));
            assertTrue(isSatelliteEnabled());
            callback.clearModemStates();
        }

        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("clear_cache_only"));

        // Set current location to Google Bangalore office
        setTestProviderLocation(12.994021769576554, 12.994021769576554);
        verifyIsSatelliteAllowed(false);
        assertTrue(allowStatecallback.waitUntilResult(1));
        assertFalse(allowStatecallback.isAllowed);

        // Since satellite is not allowed at the current location, satellite should be disabled
        assertTrue(callback.waitUntilModemOff());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        assertFalse(isSatelliteEnabled());

        // Restore satellite access allowed
        setUpSatelliteAccessAllowed();
        revokeSatellitePermission();
        unregisterTestLocationProvider();
    }

    void verifySatelliteAllowedAndEnabledForLocation(double lat, double lng, String countryCode) {
        logd(
                "verifySatelliteAllowedAndEnabledForLocation: verifying if satellite is allowed and"
                        + " enabled for location: lat="
                        + lat
                        + ", lng="
                        + lng
                        + ", country code="
                        + countryCode);

        // setup permission
        grantSatellitePermission();

        // set given lat, lng location
        logd("verifySatelliteAllowedAndEnabledForLocation: setting test provider location");
        setTestProviderLocation(lat, lng);

        // set given country code as network country code
        logd("verifySatelliteAllowedAndEnabledForLocation: setting country code: " + countryCode);
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false, countryCode, null, null, 0));

        logd("verifySatelliteAllowedAndEnabledForLocation: Clearing satellite allowed cache");
        assertTrue(
                sMockSatelliteServiceManager
                        .setIsSatelliteCommunicationAllowedForCurrentLocationCache(
                                "clear_cache_only"));

        // verify satellite is allowed
        logd("verifySatelliteAllowedAndEnabledForLocation: verify satellite is allowed");
        verifyIsSatelliteAllowed(true);
    }

    void verifySatelliteNotAllowedAndNotEnabledForLocation(
            double lat, double lng, String countryCode) {
        logd(
                "verifySatelliteNotAllowedAndNotEnabledForLocation: verifying if satellite is not"
                        + " allowed and not enabled for location: lat="
                        + lat
                        + ", lng="
                        + lng
                        + ", country code="
                        + countryCode);

        // setup permission
        grantSatellitePermission();

        // set give lat, lng location
        logd("verifySatelliteNotAllowedAndNotEnabledForLocation: setting test provider location");
        setTestProviderLocation(lat, lng);

        // set given country code as network country code
        logd(
                "verifySatelliteNotAllowedAndNotEnabledForLocation: setting country code: "
                        + countryCode);
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false, countryCode, null, null, 0));

        logd("verifySatelliteNotAllowedAndNotEnabledForLocation: Clearing satellite allowed cache");
        assertTrue(
                sMockSatelliteServiceManager
                        .setIsSatelliteCommunicationAllowedForCurrentLocationCache(
                                "clear_cache_only"));

        // verify satellite is not allowed
        logd("verifySatelliteNotAllowedAndNotEnabledForLocation: verify satellite is not allowed");
        verifyIsSatelliteAllowed(false);
    }

    private void performSatelliteConfigUpdate(String contentUrl, String metadataUrl)
            throws Exception {
        logd(
                "performSatelliteConfigUpdate: contentUrl: "
                        + contentUrl
                        + ", metadataUrl: "
                        + metadataUrl);

        Intent intent =
                new Intent("com.google.android.configupdater.TelephonyConfigUpdate.UPDATE_CONFIG");
        intent.setPackage("com.google.android.configupdater");
        intent.putExtra("CONTENT_URL", contentUrl);
        intent.putExtra("METADATA_URL", metadataUrl);

        // Send the broadcast
        logd("performSatelliteConfigUpdate: Firing broadcast to trigger satellite config update");
        getContext().sendBroadcast(intent);

        logd("performSatelliteConfigUpdate: Sleeping for satellite config to be applied");
        // Wait for the config to be applied (3 seconds)
        Thread.sleep(3000);
    }

    @Ignore("b/399900477 - Need to fix the test and re-enable it.")
    @Test
    public void testSatelliteAccessControlWithSatelliteConfigOta() throws Exception {
        logd("testSatelliteAccessControlWithSatelliteConfigOta");

        // get rid of the overriden test satellite configs, as we are going
        // to use actual on-device and ota'd satellite configs in this test
        resetSatelliteAccessControlOverlayConfigs();

        // setup permission
        grantSatellitePermission();

        // reset satellite allowance state
        logd("testSatelliteAccessControlWithSatelliteConfigOta: reset satellite allowance state");
        assertTrue(
                sMockSatelliteServiceManager
                        .setIsSatelliteCommunicationAllowedForCurrentLocationCache(
                                "cache_clear_and_not_allowed"));

        double latUs = 37.7749, lngUs = -122.4194;
        String countryCodeUs = "US";
        double latKr = 37.5665, lngKr = 126.9780;
        String countryCodeKr = "KR";
        double latTw = 25.034, lngTw = 121.565;
        String countryCodeTw = "TW";

        // register test location provider
        logd("testSatelliteAccessControlWithSatelliteConfigOta: register test location provider");
        registerTestLocationProvider();

        // Check satellite allowance for on device satellite config:
        // US - allowed; KR - not allowed; TW - not allowed;
        logd(
                "testSatelliteAccessControlWithSatelliteConfigOta: checking satellite allowance for"
                        + " on device satellite config");
        verifySatelliteAllowedAndEnabledForLocation(latUs, lngUs, countryCodeUs);
        verifySatelliteNotAllowedAndNotEnabledForLocation(latKr, lngKr, countryCodeKr);
        verifySatelliteNotAllowedAndNotEnabledForLocation(latTw, lngTw, countryCodeTw);

        // perform OTA to setup v15 satellite config data
        logd("testSatelliteAccessControlWithSatelliteConfigOta: Perform v15 config update");
        performSatelliteConfigUpdate(
                "https://www.gstatic.com/android/config_update/satelliteConfigDataTest/01212025"
                        + "-test-v15-telephony_config.pb",
                "https://www.gstatic.com/android/config_update/satelliteConfigDataTest/01212025"
                        + "-test-v15-telephony_config-metadata.txt");

        // Check satellite allowance and enabled status for v15 satellite config:
        // US - allowed; KR - not allowed; TW - not allowed;
        logd(
                "testSatelliteAccessControlWithSatelliteConfigOta: checking satellite allowance for"
                        + " v15 satellite config");
        verifySatelliteAllowedAndEnabledForLocation(latUs, lngUs, countryCodeUs);
        verifySatelliteNotAllowedAndNotEnabledForLocation(latKr, lngKr, countryCodeKr);
        verifySatelliteNotAllowedAndNotEnabledForLocation(latTw, lngTw, countryCodeTw);

        // perform OTA to setup v16 satellite config data
        logd("testSatelliteAccessControlWithSatelliteConfigOta: Perform v16 config update");
        performSatelliteConfigUpdate(
                "https://www.gstatic.com/android/config_update/satelliteConfigDataTest/01212025"
                        + "-test-v16-telephony_config.pb",
                "https://www.gstatic.com/android/config_update/satelliteConfigDataTest/01212025"
                        + "-test-v16-telephony_config-metadata.txt");

        // Check satellite allowance and enabled status for v16 satellite config:
        // US - allowed; KR - allowed; TW - allowed;
        logd(
                "testSatelliteAccessControlWithSatelliteConfigOta: checking satellite allowance for"
                        + " v16 satellite config");
        verifySatelliteAllowedAndEnabledForLocation(latUs, lngUs, countryCodeUs);
        verifySatelliteAllowedAndEnabledForLocation(latKr, lngKr, countryCodeKr);
        verifySatelliteAllowedAndEnabledForLocation(latTw, lngTw, countryCodeTw);

        // perform OTA to setup v17 satellite config data
        logd("testSatelliteAccessControlWithSatelliteConfigOta: Perform v17 config update");
        performSatelliteConfigUpdate(
                "https://www.gstatic.com/android/config_update/satelliteConfigDataTest/01212025"
                        + "-test-v17-telephony_config.pb",
                "https://www.gstatic.com/android/config_update/satelliteConfigDataTest/01212025"
                        + "-test-v17-telephony_config-metadata.txt");

        // Check satellite allowance and enabled status for v17 satellite config:
        // US - allowed; KR - not allowed; TW - not allowed;
        logd(
                "testSatelliteAccessControlWithSatelliteConfigOta: checking satellite allowance for"
                        + " v17 satellite config");
        verifySatelliteAllowedAndEnabledForLocation(latUs, lngUs, countryCodeUs);
        verifySatelliteNotAllowedAndNotEnabledForLocation(latKr, lngKr, countryCodeKr);
        verifySatelliteNotAllowedAndNotEnabledForLocation(latTw, lngTw, countryCodeTw);

        revokeSatellitePermission();
        unregisterTestLocationProvider();
    }

    @Ignore("b/402543255 - Need to fix the test and re-enable it.")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testSatelliteAccessControl_UpdateSelectionChannel() {
        final long timeOut = TimeUnit.SECONDS.toMillis(1);
        grantSatellitePermission();
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache(
                        "cache_clear_and_not_allowed"));
        SatelliteCommunicationAccessStateCallbackTest allowStateCallback =
                new SatelliteCommunicationAccessStateCallbackTest();
        long registerResultAllowState =
                sSatelliteManager.registerForCommunicationAccessStateChanged(
                        getContext().getMainExecutor(), allowStateCallback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResultAllowState);
        assertTrue(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        assertNull(allowStateCallback.getSatelliteAccessConfiguration());
        allowStateCallback.drainPermits();
        Pair<SatelliteAccessConfiguration, Integer> resultReceiver =
                requestSatelliteAccessConfigurationForCurrentLocation();
        SatelliteAccessConfiguration queriedSatelliteAccessConfiguration = resultReceiver.first;
        assertNull(queriedSatelliteAccessConfiguration);
        assertEquals(SATELLITE_RESULT_NO_RESOURCES, (int) resultReceiver.second);

        // Test access controller using on-device data
        assertTrue(sMockSatelliteServiceManager.setSatelliteAccessControlOverlayConfigs(false, true,
                SATELLITE_S2_FILE_WITH_CONFIG_ID, TimeUnit.MINUTES.toNanos(10), "US",
                SATELLITE_ACCESS_CONFIGURATION_FILE));
        registerTestLocationProvider();
        grantSatellitePermission();
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
        }

        // Set current location to Google San Diego office
        setTestProviderLocation(32.909808231041644, -117.18185788819781);
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("clear_cache_only"));
        verifyIsSatelliteAllowed(true);
        assertTrue(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        SatelliteAccessConfiguration notifiedSatelliteAccessConfiguration =
                allowStateCallback.getSatelliteAccessConfiguration();
        assertNotNull(notifiedSatelliteAccessConfiguration);
        resultReceiver = requestSatelliteAccessConfigurationForCurrentLocation();
        queriedSatelliteAccessConfiguration = resultReceiver.first;
        assertNotNull(queriedSatelliteAccessConfiguration);

        // Trigger updateSystemSelectionChannels by enabling satellite.
        assertFalse(isSatelliteEnabled());
        allowStateCallback.drainPermits();
        requestSatelliteEnabled(true);
        assertTrue(isSatelliteEnabled());
        assertFalse(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));

        // Verify system selection info is correct
        // Use first configuration for San-Diego Office
        SatelliteAccessConfiguration expectedConfiguration =
                getExpectedSatelliteConfiguration().getFirst();
        // Verify notified satellite access configuration has same value with expected.
        assertEquals(expectedConfiguration, notifiedSatelliteAccessConfiguration);
        // Verify return value for requestSatelliteAccessConfigurationForCurrentLocation has same
        // value with expected.
        assertEquals(expectedConfiguration, queriedSatelliteAccessConfiguration);
        // Verify modem received satellite access configuration has same value with expected.
        SystemSelectionSpecifier actualSystemSelectionSpecifier =
                sMockSatelliteServiceManager.getSystemSelectionChannels().getFirst();
        verifySatelliteAccessConfiguration(expectedConfiguration, actualSystemSelectionSpecifier);

        // Set current location to Google MTV office
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("clear_cache_only"));
        setTestProviderLocation(37.422570063203494, -122.08560860200116);
        verifyIsSatelliteAllowed(true);
        assertTrue(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        notifiedSatelliteAccessConfiguration = allowStateCallback.getSatelliteAccessConfiguration();
        assertNotNull(notifiedSatelliteAccessConfiguration);
        resultReceiver = requestSatelliteAccessConfigurationForCurrentLocation();
        queriedSatelliteAccessConfiguration = resultReceiver.first;
        assertNotNull(queriedSatelliteAccessConfiguration);

        assertTrue(isSatelliteEnabled());
        allowStateCallback.drainPermits();
        requestSatelliteEnabled(false);
        assertTrue(callback.waitUntilModemOff());
        assertFalse(isSatelliteEnabled());
        assertFalse(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));

        // Trigger updateSystemSelectionChannels() by enabling satellite.
        requestSatelliteEnabled(true);
        assertTrue(isSatelliteEnabled());

        // Verify system selection info is correct
        // Use second configuration for MTV Office
        expectedConfiguration = getExpectedSatelliteConfiguration().get(1);
        // Verify notified satellite access configuration has same value with expected.
        assertEquals(expectedConfiguration, notifiedSatelliteAccessConfiguration);
        // Verify return value for requestSatelliteAccessConfigurationForCurrentLocation has same
        // value with expected.
        assertEquals(expectedConfiguration, queriedSatelliteAccessConfiguration);
        // Verify modem received satellite access configuration has same value with expected.
        actualSystemSelectionSpecifier =
                sMockSatelliteServiceManager.getSystemSelectionChannels().getFirst();
        verifySatelliteAccessConfiguration(expectedConfiguration, actualSystemSelectionSpecifier);

        // Set current location to Hawaii
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("clear_cache_only"));
        setTestProviderLocation(19.50817482973673, -154.89161639216186);
        verifyIsSatelliteAllowed(true);
        assertTrue(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        notifiedSatelliteAccessConfiguration = allowStateCallback.getSatelliteAccessConfiguration();
        assertNotNull(notifiedSatelliteAccessConfiguration);

        assertTrue(isSatelliteEnabled());
        allowStateCallback.drainPermits();
        requestSatelliteEnabled(false);
        assertTrue(callback.waitUntilModemOff());
        assertFalse(isSatelliteEnabled());
        assertFalse(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));

        // Trigger updateSystemSelectionChannels() by enabling satellite.
        requestSatelliteEnabled(true);
        assertTrue(isSatelliteEnabled());

        // Verify system selection info is correct
        // Use 3rd configuration for Hawaii
        expectedConfiguration = getExpectedSatelliteConfiguration().get(2);
        // Verify notified satellite access configuration has same value with expected.
        assertEquals(expectedConfiguration, notifiedSatelliteAccessConfiguration);
        // Verify modem received satellite access configuration has same value with expected.
        actualSystemSelectionSpecifier =
                sMockSatelliteServiceManager.getSystemSelectionChannels().getFirst();
        verifySatelliteAccessConfiguration(expectedConfiguration, actualSystemSelectionSpecifier);

        // Set current location to Alaska
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("clear_cache_only"));
        setTestProviderLocation(61.21729700371326, -149.89469126029147);
        verifyIsSatelliteAllowed(true);
        assertTrue(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        notifiedSatelliteAccessConfiguration = allowStateCallback.getSatelliteAccessConfiguration();
        assertNotNull(notifiedSatelliteAccessConfiguration);
        resultReceiver = requestSatelliteAccessConfigurationForCurrentLocation();
        queriedSatelliteAccessConfiguration = resultReceiver.first;
        assertNotNull(queriedSatelliteAccessConfiguration);

        assertTrue(isSatelliteEnabled());
        allowStateCallback.drainPermits();
        requestSatelliteEnabled(false);
        assertTrue(callback.waitUntilModemOff());
        assertFalse(isSatelliteEnabled());
        assertFalse(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));

        // Trigger updateSystemSelectionChannels() by enabling satellite.
        requestSatelliteEnabled(true);
        assertTrue(isSatelliteEnabled());

        // Verify system selection info is correct
        // Use 4th configuration for Alaska
        expectedConfiguration = getExpectedSatelliteConfiguration().get(3);
        // Verify notified satellite access configuration has same value with expected.
        assertEquals(expectedConfiguration, notifiedSatelliteAccessConfiguration);
        // Verify return value for requestSatelliteAccessConfigurationForCurrentLocation has same
        // value with expected.
        assertEquals(expectedConfiguration, queriedSatelliteAccessConfiguration);
        // Verify modem received satellite access configuration has same value with expected.
        actualSystemSelectionSpecifier =
                sMockSatelliteServiceManager.getSystemSelectionChannels().getFirst();
        verifySatelliteAccessConfiguration(expectedConfiguration, actualSystemSelectionSpecifier);

        // Set current location to Puerto Rico
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("clear_cache_only"));
        setTestProviderLocation(18.466531136579068, -66.11359552551347);
        verifyIsSatelliteAllowed(true);
        assertTrue(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        notifiedSatelliteAccessConfiguration = allowStateCallback.getSatelliteAccessConfiguration();
        assertNotNull(notifiedSatelliteAccessConfiguration);
        resultReceiver = requestSatelliteAccessConfigurationForCurrentLocation();
        queriedSatelliteAccessConfiguration = resultReceiver.first;
        assertNotNull(queriedSatelliteAccessConfiguration);

        assertTrue(isSatelliteEnabled());
        allowStateCallback.drainPermits();
        requestSatelliteEnabled(false);
        assertTrue(callback.waitUntilModemOff());
        assertFalse(isSatelliteEnabled());
        assertFalse(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));

        // Trigger updateSystemSelectionChannels() by enabling satellite.
        requestSatelliteEnabled(true);
        assertTrue(isSatelliteEnabled());

        // Verify system selection info is correct
        // Use 5th configuration for Puerto Rico
        expectedConfiguration = getExpectedSatelliteConfiguration().get(4);
        // Verify notified satellite access configuration has same value with expected.
        assertEquals(expectedConfiguration, notifiedSatelliteAccessConfiguration);
        // Verify return value for requestSatelliteAccessConfigurationForCurrentLocation has same
        // value with expected.
        assertEquals(expectedConfiguration, queriedSatelliteAccessConfiguration);
        // Verify modem received satellite access configuration has same value with expected.
        actualSystemSelectionSpecifier =
                sMockSatelliteServiceManager.getSystemSelectionChannels().getFirst();
        verifySatelliteAccessConfiguration(expectedConfiguration, actualSystemSelectionSpecifier);

        // Move location to not to support Satellite area.
        // Set current location to Google Bangalore office
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("clear_cache_only"));
        setTestProviderLocation(12.994021769576554, 12.994021769576554);
        verifyIsSatelliteAllowed(false);
        assertTrue(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        notifiedSatelliteAccessConfiguration = allowStateCallback.getSatelliteAccessConfiguration();
        // Those location where it does not have config id should return null.
        assertNull(notifiedSatelliteAccessConfiguration);

        assertTrue(isSatelliteEnabled());
        allowStateCallback.drainPermits();
        requestSatelliteEnabled(false);
        assertTrue(callback.waitUntilModemOff());
        assertFalse(isSatelliteEnabled());
        assertFalse(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        assertEquals(SATELLITE_RESULT_ACCESS_BARRED,
                requestSatelliteEnabledWithResult(true, TIMEOUT));
        assertFalse(isSatelliteEnabled());
        assertFalse(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));

        // Even though satellite is not allowed at the current location, disabling satellite should
        // succeed
        requestSatelliteEnabled(false);
        assertFalse(isSatelliteEnabled());
        assertFalse(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));

        // Restore satellite access allowed
        setUpSatelliteAccessAllowed();
        revokeSatellitePermission();
        unregisterTestLocationProvider();
    }

    // The test data is stored at vendor/google/services/ConfigUpdater/assets/cts_data
    // /telephony_config_update/cts_test_01212025-test-v15-telephony_config.pb.
    // Refer to b/390075624#comment22 for more details.
    private SatelliteAccessConfiguration getV15TestConfigForUs() {
        // SatellitePosition
        SatellitePosition position = new SatellitePosition(-101.3, 35786.0);

        // EarfcnRange List
        List<EarfcnRange> earfcnRanges = new ArrayList<>();
        earfcnRanges.add(new EarfcnRange(229011, 229011));
        earfcnRanges.add(new EarfcnRange(229013, 229013));
        earfcnRanges.add(new EarfcnRange(229015, 229015));
        earfcnRanges.add(new EarfcnRange(229017, 229017));

        // bands List
        List<Integer> bands = List.of(255);

        // SatelliteInfo
        SatelliteInfo satelliteInfo =
                new SatelliteInfo(
                        UUID.fromString("c9d78ffa-ffa5-4d41-a81b-34693b33b496"),
                        position,
                        bands,
                        earfcnRanges);

        // SatelliteInfo List
        List<SatelliteInfo> satelliteInfoList = List.of(satelliteInfo);

        // tagIds List
        List<Integer> tagIds = List.of(11, 1001);

        // create SatelliteAccessConfiguration
        SatelliteAccessConfiguration verificationConfigForUs =
                new SatelliteAccessConfiguration(satelliteInfoList, tagIds);
        logd("getV15TestConfigForUs: " + verificationConfigForUs);
        return verificationConfigForUs;
    }

    @Ignore("b/404901907 - Need to fix and re-enable this test.")
    @Test
    public void testSatelliteAccessControllerLoadSatelliteAccessData() throws Exception {
        logd("testSatelliteAccessControllerLoadSatelliteAccessData");

        // Get rid of the overridden test satellite configs, as we are going
        // to use actual on-device and ota'd satellite configs in this test
        resetSatelliteAccessControlOverlayConfigs();

        grantSatellitePermission();

        logd(
                "testSatelliteAccessControllerLoadSatelliteAccessData: reset satellite allowance"
                        + " state");
        assertTrue(
                sMockSatelliteServiceManager
                        .setIsSatelliteCommunicationAllowedForCurrentLocationCache(
                                "cache_allowed"));

        final long timeOut = TimeUnit.SECONDS.toMillis(5);
        SatelliteCommunicationAccessStateCallbackTest allowStateCallback =
                new SatelliteCommunicationAccessStateCallbackTest();
        long registerResultAllowState =
                sSatelliteManager.registerForCommunicationAccessStateChanged(
                        getContext().getMainExecutor(), allowStateCallback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResultAllowState);

        logd(
                "testSatelliteAccessControllerLoadSatelliteAccessData: override the config data "
                        + "version so that the new config data can be accepted by Telephony");
        assertTrue(sMockSatelliteServiceManager.overrideConfigDataVersion(false, 0));

        logd(
                "testSatelliteAccessControllerLoadSatelliteAccessData:"
                        + "request Telephony to download config data v14 which only support KR"
                        + "also not supporting satellite access configuration");
        assertTrue(
                sMockSatelliteServiceManager.updateTelephonyConfig(
                        TEST_V14_CONFIG_DATA_CONTENT_LOCAL_URI,
                        TEST_V14_CONFIG_DATA_METADATA_LOCAL_URI));
        waitFor(1000);

        assertTrue(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        SatelliteAccessConfiguration notifiedSatelliteAccessConfiguration =
                allowStateCallback.getSatelliteAccessConfiguration();
        assertNull(notifiedSatelliteAccessConfiguration);
        verifyIsSatelliteAllowed(false);

        double latUs = 37.7749, lngUs = -122.4194;
        double latKr = 37.5665, lngKr = 126.9780;
        String countryCodeUs = "US";
        String countryCodeKr = "KR";

        logd("testSatelliteAccessControllerLoadSatelliteAccessData: check KR");
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false, "KR", null, null, 0));
        verifySatelliteAllowedAndEnabledForLocation(latKr, lngKr, countryCodeKr);

        logd("testSatelliteAccessControllerLoadSatelliteAccessData: check US");
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false, "US", null, null, 0));
        verifySatelliteNotAllowedAndNotEnabledForLocation(latUs, lngUs, countryCodeUs);

        logd("testSatelliteAccessControllerLoadSatelliteAccessData: restore the original data");
        assertTrue(sMockSatelliteServiceManager.overrideConfigDataVersion(true, 0));

        Pair<SatelliteAccessConfiguration, Integer> resultReceiver =
                requestSatelliteAccessConfigurationForCurrentLocation();
        SatelliteAccessConfiguration queriedSatelliteAccessConfiguration = resultReceiver.first;
        assertNull(queriedSatelliteAccessConfiguration);
        assertEquals(SATELLITE_RESULT_NO_RESOURCES, (int) resultReceiver.second);

        logd(
                "testSatelliteAccessControllerLoadSatelliteAccessData: override the config data "
                        + "version so that the new config data can be accepted by Telephony");
        assertTrue(sMockSatelliteServiceManager.overrideConfigDataVersion(false, 0));

        logd(
                "testSatelliteAccessControllerLoadSatelliteAccessData:"
                        + "request Telephony to download config data v15 support only US");
        assertTrue(
                sMockSatelliteServiceManager.updateTelephonyConfig(
                        TEST_V15_CONFIG_DATA_CONTENT_LOCAL_URI,
                        TEST_V15_CONFIG_DATA_METADATA_LOCAL_URI));
        waitFor(1000);

        logd("wait for callback for V15");
        assertTrue(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        notifiedSatelliteAccessConfiguration = allowStateCallback.getSatelliteAccessConfiguration();
        assertNotNull(notifiedSatelliteAccessConfiguration);
        assertEquals(getV15TestConfigForUs(), notifiedSatelliteAccessConfiguration);

        verifyIsSatelliteAllowed(true);

        logd("testSatelliteAccessControllerLoadSatelliteAccessData: set KR");
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false, "KR", null, null, 0));
        verifySatelliteNotAllowedAndNotEnabledForLocation(latKr, lngKr, countryCodeKr);

        logd("testSatelliteAccessControllerLoadSatelliteAccessData: set US");
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false, "US", null, null, 0));
        verifySatelliteAllowedAndEnabledForLocation(latUs, lngUs, countryCodeUs);

        resultReceiver = requestSatelliteAccessConfigurationForCurrentLocation();
        queriedSatelliteAccessConfiguration = resultReceiver.first;
        logd(
                "testSatelliteAccessControllerLoadSatelliteAccessData:"
                        + " queriedSatelliteAccessConfiguration= "
                        + queriedSatelliteAccessConfiguration);
        assertNotNull(queriedSatelliteAccessConfiguration);
        assertEquals(getV15TestConfigForUs(), notifiedSatelliteAccessConfiguration);
        assertNull(resultReceiver.second);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testSatelliteAccessControl_UpdateSelectionChannel_BackwardCompatibility() {
        final long timeOut = TimeUnit.SECONDS.toMillis(1);
        grantSatellitePermission();
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache(
                        "cache_clear_and_not_allowed"));
        SatelliteCommunicationAccessStateCallbackTest allowStateCallback =
                new SatelliteCommunicationAccessStateCallbackTest();
        long registerResultAllowState =
                sSatelliteManager.registerForCommunicationAccessStateChanged(
                        getContext().getMainExecutor(), allowStateCallback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResultAllowState);
        assertTrue(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        assertNull(allowStateCallback.getSatelliteAccessConfiguration());
        allowStateCallback.drainPermits();
        Pair<SatelliteAccessConfiguration, Integer> resultReceiver =
                requestSatelliteAccessConfigurationForCurrentLocation();
        SatelliteAccessConfiguration queriedSatelliteAccessConfiguration = resultReceiver.first;
        assertNull(queriedSatelliteAccessConfiguration);
        assertEquals(SATELLITE_RESULT_NO_RESOURCES, (int) resultReceiver.second);

        // Test access controller using on-device data with old format geofence data. no satellite
        // access configuration file.
        assertTrue(sMockSatelliteServiceManager.setSatelliteAccessControlOverlayConfigs(false, true,
                SATELLITE_S2_FILE, TimeUnit.MINUTES.toNanos(10), "US", null));
        registerTestLocationProvider();
        grantSatellitePermission();
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
        }

        allowStateCallback.drainPermits();
        // Set current location to Google San Diego office
        setTestProviderLocation(32.909808231041644, -117.18185788819781);
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("clear_cache_only"));
        // Satellite is allowed but no satellite access configuration is available.
        verifyIsSatelliteAllowed(true);
        assertFalse(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        SatelliteAccessConfiguration notifiedSatelliteAccessConfiguration =
                allowStateCallback.getSatelliteAccessConfiguration();
        assertNull(notifiedSatelliteAccessConfiguration);
        resultReceiver = requestSatelliteAccessConfigurationForCurrentLocation();
        queriedSatelliteAccessConfiguration = resultReceiver.first;
        assertNull(queriedSatelliteAccessConfiguration);

        assertFalse(isSatelliteEnabled());
        requestSatelliteEnabled(true);
        assertTrue(isSatelliteEnabled());

        callback.clearModemStates();
        // Set current location to Hawaii, where is not supported from old geofence data.
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("clear_cache_only"));
        setTestProviderLocation(19.50817482973673, -154.89161639216186);
        verifyIsSatelliteAllowed(false);
        // Notification with null object comes as region changed from allowed to not allowed.
        assertTrue(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        notifiedSatelliteAccessConfiguration = allowStateCallback.getSatelliteAccessConfiguration();
        assertNull(notifiedSatelliteAccessConfiguration);
        resultReceiver = requestSatelliteAccessConfigurationForCurrentLocation();
        queriedSatelliteAccessConfiguration = resultReceiver.first;
        assertNull(queriedSatelliteAccessConfiguration);

        // Satellite is disabled because satellite is not allowed for current region.
        assertTrue(callback.waitUntilModemOff());
        assertFalse(isSatelliteEnabled());

        // Enable will fail because satellite is not allowed
        int result = requestSatelliteEnabledWithResult(true, TIMEOUT);
        assertEquals(SATELLITE_RESULT_ACCESS_BARRED, result);

        allowStateCallback.drainPermits();
        // Set current location to Alaska, where is not supported from old geofence data.
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("clear_cache_only"));
        setTestProviderLocation(61.21729700371326, -149.89469126029147);
        verifyIsSatelliteAllowed(false);
        assertFalse(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        notifiedSatelliteAccessConfiguration = allowStateCallback.getSatelliteAccessConfiguration();
        assertNull(notifiedSatelliteAccessConfiguration);
        resultReceiver = requestSatelliteAccessConfigurationForCurrentLocation();
        queriedSatelliteAccessConfiguration = resultReceiver.first;
        assertNull(queriedSatelliteAccessConfiguration);

        // Enabling satellite will fail because satellite is not allowed for current region.
        result = requestSatelliteEnabledWithResult(true, TIMEOUT);
        assertEquals(SATELLITE_RESULT_ACCESS_BARRED, result);

        // Moved to supported region again, current location to Google San Diego office
        setTestProviderLocation(32.909808231041644, -117.18185788819781);
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("clear_cache_only"));
        // Satellite is allowed but no satellite access configuration is available.
        verifyIsSatelliteAllowed(true);
        requestSatelliteEnabled(true);
        assertTrue(isSatelliteEnabled());

        allowStateCallback.drainPermits();
        callback.clearModemStates();
        // Set current location to Puerto Rico, where is not supported from old geofence data.
        assertTrue(sMockSatelliteServiceManager
                .setIsSatelliteCommunicationAllowedForCurrentLocationCache("clear_cache_only"));
        setTestProviderLocation(18.466531136579068, -66.11359552551347);
        verifyIsSatelliteAllowed(false);
        // Notification with null configuration comes as region changed from allowed to not allowed
        assertTrue(
                allowStateCallback.waitUntilSatelliteAccessConfigurationChangedEvent(1, timeOut));
        notifiedSatelliteAccessConfiguration = allowStateCallback.getSatelliteAccessConfiguration();
        assertNull(notifiedSatelliteAccessConfiguration);
        resultReceiver = requestSatelliteAccessConfigurationForCurrentLocation();
        queriedSatelliteAccessConfiguration = resultReceiver.first;
        assertNull(queriedSatelliteAccessConfiguration);

        // Satellite is disabled because satellite is not allowed for current region.
        assertTrue(callback.waitUntilModemOff());
        assertFalse(isSatelliteEnabled());

        // Restore satellite access allowed
        setUpSatelliteAccessAllowed();
        revokeSatellitePermission();
        unregisterTestLocationProvider();
    }

    @Ignore("b/399928350 - Need to fix and re-enable this test.")
    @Test
    public void testGetSatellitePlmnsForCarrier() {
        logd("testGetAggregateSatellitePlmnListForCarrier");
        grantSatellitePermission();
        beforeSatelliteForCarrierTest();
        @SatelliteManager.SatelliteResult int expectedSuccess =
                SatelliteManager.SATELLITE_RESULT_SUCCESS;

        List<String> allSatellitePlmnListBeforeCarrierConfigOverride = getAllSatellitePlmnList();

        /* Test when satellite is supported in the carrier config */
        setSatelliteErrorBasedOnHalVersion(expectedSuccess);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(
                CarrierConfigManager.KEY_SATELLITE_ATTACH_SUPPORTED_BOOL, true);
        PersistableBundle plmnBundle = new PersistableBundle();
        int[] intArray1 = {3, 5};
        int[] intArray2 = {3};
        plmnBundle.putIntArray("123411", intArray1);
        plmnBundle.putIntArray("123412", intArray2);
        bundle.putPersistableBundle(
                CarrierConfigManager.KEY_CARRIER_SUPPORTED_SATELLITE_SERVICES_PER_PROVIDER_BUNDLE,
                plmnBundle);
        overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);

        ArrayList<String> expectedCarrierPlmnList = new ArrayList<>();
        expectedCarrierPlmnList.add("123411");
        expectedCarrierPlmnList.add("123412");
        assertTrue(waitForEventOnSetSatellitePlmn(1));
        List<String> carrierPlmnList = getCarrierPlmnList();
        assertNotNull(carrierPlmnList);
        assertEquals(expectedCarrierPlmnList, carrierPlmnList);

        List<String> aggregatedPlmnList = sSatelliteManager.getSatellitePlmnsForCarrier(
                sTestSubIDForCarrierSatellite);
        assertEquals(expectedCarrierPlmnList, aggregatedPlmnList);

        List<String> satellitePlmnListFromOverlayConfig =
                sMockSatelliteServiceManager.getPlmnListFromOverlayConfig();
        List<String> expectedAllSatellitePlmnList =
                SatelliteServiceUtils.mergeStrLists(
                        carrierPlmnList,
                        satellitePlmnListFromOverlayConfig,
                        allSatellitePlmnListBeforeCarrierConfigOverride);
        List<String> allSatellitePlmnList = getAllSatellitePlmnList();

        assertNotNull(allSatellitePlmnList);
        boolean listsAreEqual =
                expectedAllSatellitePlmnList.containsAll(allSatellitePlmnList)
                        && allSatellitePlmnList.containsAll(expectedAllSatellitePlmnList);
        assertTrue(listsAreEqual);

        afterSatelliteForCarrierTest();
        revokeSatellitePermission();
    }

    @Test
    public void testSendSatelliteDatagrams_timeout() {
        logd("testSendSatelliteDatagrams_timeout");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagrams_timeout: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();

        // Wait to process datagrams so that the send request will time out.
        sMockSatelliteServiceManager.setWaitToSend(true);
        // Override the sending timeout duration to 1 second
        sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(false,
                DatagramController.TIMEOUT_TYPE_WAIT_FOR_DATAGRAM_SENDING_RESPONSE, 1000);

        LinkedBlockingQueue<Integer> resultListener1 = new LinkedBlockingQueue<>(1);
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener1::offer);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        assertTrue(callback.waitUntilOnSendDatagramStateChanged(3));
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                        1, SatelliteManager.SATELLITE_RESULT_MODEM_TIMEOUT));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        try {
            errorCode = resultListener1.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagrams_timeout: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_MODEM_TIMEOUT);

        // Respond to the first send request
        callback.clearSendDatagramStateChanges();
        assertTrue(sMockSatelliteServiceManager.sendSavedDatagram());

        // Telephony should ignore the response
        assertFalse(callback.waitUntilOnSendDatagramStateChanged(1));

        // Restore the timeout duration
        sMockSatelliteServiceManager.setDatagramControllerTimeoutDuration(true,
                DatagramController.TIMEOUT_TYPE_WAIT_FOR_DATAGRAM_SENDING_RESPONSE, 0);
    }

    @Test
    public void testRequestSatelliteEnabled_timeout() {
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_timeout: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_timeout: disabling satellite...");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondTelephony(false);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 100));

        // Time out to enable satellite
        logd("testRequestSatelliteEnabled_timeout: enabling satellite...");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        int result = requestSatelliteEnabledWithResult(true, TIMEOUT);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertEquals(SatelliteManager.SATELLITE_RESULT_MODEM_TIMEOUT, result);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(1));
        assertFalse(isSatelliteEnabled());

        // Respond to the above enable request. Telephony should ignore the event.
        logd("testRequestSatelliteEnabled_timeout: Responding the enabling request...");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertFalse(callback.waitUntilResult(1));
        assertFalse(isSatelliteEnabled());

        // Restore the original states
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));

        // Successfully enable satellite
        logd("testRequestSatelliteEnabled_timeout: enabling satellite...");
        callback.clearModemStates();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.getModemState(1));

        sMockSatelliteServiceManager.setShouldRespondTelephony(false);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 500));

        // Time out to disable satellite. Telephony should respond SATELLITE_RESULT_MODEM_TIMEOUT to
        // clients and stay in SATELLITE_MODEM_STATE_OUT_OF_SERVICE as satellite disable request
        // failed.
        logd("testRequestSatelliteEnabled_timeout: disabling satellite...");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        result = requestSatelliteEnabledWithResult(false, TIMEOUT);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertEquals(SatelliteManager.SATELLITE_RESULT_MODEM_TIMEOUT, result);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.modemState);
        assertTrue(isSatelliteEnabled());

        // Respond to the above disable request. Telephony should ignore the event.
        logd("testRequestSatelliteEnabled_timeout: Responding the disabling request...");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(false,
                MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertFalse(callback.waitUntilResult(1));
        assertTrue(isSatelliteEnabled());

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_EnableDisable_NoResponseForEnable() {
        /*
         * Test scenario:
         * 1) Enable request
         * 2) Disable request
         * 3) Response from modem for the disable request. Note that there is no response for the
         *    enable request from modem
         * 4) Satellite should move to OFF state and the enable request should be aborted
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_EnableDisable_NoResponseForEnable: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_EnableDisable_NoResponseForEnable: disabling "
                    + "satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_EnableDisable_NoResponseForEnable: enabling "
                + "satellite... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> enableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Restore the original states
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));

        // Successfully disable satellite while enabling is in progress. No response for enable
        // request from modem
        logd("testRequestSatelliteEnabled_EnableDisable_NoResponseForEnable: disabling "
                + "satellite... (3)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        requestSatelliteEnabled(false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(1));
        assertFalse(isSatelliteEnabled());
        // The enable request right before the disable request should have been aborted
        assertResult(enableResult, SATELLITE_RESULT_REQUEST_ABORTED);

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_EnableDisable_SuccessfulResponseForEnable() {
        /*
         * Test scenario:
         * 1) Enable request
         * 2) Disable request
         * 3) Successful response from modem for the enable request
         * 4) Successful response from modem for the disable request
         * 5) Satellite should move to OFF state
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_EnableDisable_SuccessfulResponseForEnable: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_EnableDisable_SuccessfulResponseForEnable: disabling"
                    + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_EnableDisable_SuccessfulResponseForEnable: enabling"
                + " satellite... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> enableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));

        // Disable satellite while enabling is in progress
        logd("testRequestSatelliteEnabled_EnableDisable_SuccessfulResponseForEnable: disabling"
                + " satellite... (3)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));

        // Send a successful response for the enable request
        logd("testRequestSatelliteEnabled_EnableDisable_SuccessfulResponseForEnable: responding to"
                + " the enable request... (4)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE));
        assertResult(enableResult, SATELLITE_RESULT_SUCCESS);

        // Send a successful response for the disable request
        logd("testRequestSatelliteEnabled_EnableDisable_SuccessfulResponseForEnable: responding to"
                + " the disable request... (5)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(false,
                SatelliteModemState.SATELLITE_MODEM_STATE_OFF));
        assertResult(disableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_EnableDisable_LateResponseForEnable() {
        /*
         * Test scenario:
         * 1) Enable request
         * 2) Disable request
         * 3) Successful response from modem for the disable request
         * 4) Satellite should move to OFF state and the enable request should be aborted
         * 5) Successful response from modem for the enable request
         * 6) Framework should ignore the response from modem for the enable request and stay at
         *    OFF state
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_EnableDisable_LateResponseForEnable: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_EnableDisable_LateResponseForEnable: disabling"
                    + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_EnableDisable_LateResponseForEnable: enabling"
                + " satellite... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> enableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));

        // Disable satellite while enabling is in progress
        logd("testRequestSatelliteEnabled_EnableDisable_LateResponseForEnable: disabling"
                + " satellite... (3)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));

        // Send a successful response for the disable request
        logd("testRequestSatelliteEnabled_EnableDisable_LateResponseForEnable: responding to"
                + " the disable request... (4)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(false,
                SatelliteModemState.SATELLITE_MODEM_STATE_OFF));
        assertResult(disableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(0));
        assertFalse(isSatelliteEnabled());
        // The enable request right before the disable request should have been aborted
        assertResult(enableResult, SATELLITE_RESULT_REQUEST_ABORTED);

        // Send a successful response for the enable request
        logd("testRequestSatelliteEnabled_EnableDisable_LateResponseForEnable: responding to"
                + " the enable request... (5)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertFalse(isSatelliteEnabled());

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_EnableDisable_FailureResponseForEnable() {
        /*
         * Test scenario:
         * 1) Enable request
         * 2) Satellite should move to ENABLING state
         * 3) Disable request
         * 4) Satellite should move to DISABLING state
         * 5) Failure response from modem for the enable request
         * 6) Response from modem for the disable request
         * 7) Satellite should move to OFF state
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_EnableDisable_FailureResponseForEnable: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_EnableDisable_FailureResponseForEnable: disabling"
                    + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_EnableDisable_FailureResponseForEnable: enabling"
                + " satellite... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> enableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Disable satellite while enabling is in progress
        logd("testRequestSatelliteEnabled_EnableDisable_FailureResponseForEnable: disabling"
                + " satellite... (3)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));

        // Send a failure response for the enable request
        logd("testRequestSatelliteEnabled_EnableDisable_FailureResponseForEnable: responding to"
                + " the enable request... (4)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_REQUEST_ABORTED);
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertResult(enableResult, SATELLITE_RESULT_REQUEST_ABORTED);

        // Send a successful response for the disable request
        logd("testRequestSatelliteEnabled_EnableDisable_FailureResponseForEnable: responding to"
                + " the disable request... (5)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(false,
                SatelliteModemState.SATELLITE_MODEM_STATE_OFF));
        assertResult(disableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    public void testRequestSatelliteEnabled_EnableDisable_FeatureDisabled() {
        /*
         * Test scenario:
         * 1) Enable request
         * 2) Satellite should move to ENABLING state
         * 3) Disable request
         * 4) Telephony should return SATELLITE_RESULT_ENABLE_IN_PROGRESS since the feature flag
         *    is not enabled.
         * 5) Successful response from modem for the enable request
         * 6) Satellite should move to NOT_CONNECTED state
         */
        if (Flags.carrierRoamingNbIotNtn()) return;

        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_EnableDisable_FeatureDisabled: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_EnableDisable_FeatureDisabled: disabling"
                    + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_EnableDisable_FeatureDisabled: enabling"
                + " satellite... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> enableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Disable satellite while enabling is in progress
        logd("testRequestSatelliteEnabled_EnableDisable_FeatureDisabled: disabling"
                + " satellite... (3)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertResult(disableResult, SATELLITE_RESULT_ENABLE_IN_PROGRESS);

        // Send a successful response for the enable request
        logd("testRequestSatelliteEnabled_EnableDisable_FeatureDisabled: responding to"
                + " the enable request... (4)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE));
        assertResult(enableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(0));

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse() {
        /*
         * Test scenario:
         * 1) Enable request with demo mode
         * 2) Satellite should move to ENABLING state
         * 3) Enable request with P2P mode
         * 4) Successful response from modem for the first enable request
         * 5) Satellite should move to NOT_CONNECTED state and in demo mode
         * 6) Successful response from modem for the second enable request
         * 7) Satellite should stay at NOT_CONNECTED state and in P2P mode
         */
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse: disabling"
                    + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse: enabling"
                + " satellite with demo mode... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> firstEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, true, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Change to real mode while enabling demo mode is in progress
        logd("testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse: updating to real mode"
                + " ... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Send a successful response for the first enable request
        logd("testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse: responding to"
                + " the first enable request... (4)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE));
        assertResult(firstEnableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(0));
        assertTrue(isSatelliteEnabled());
        verifyDemoMode(true);
        // The second enable request should be pushed to modem now
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));

        // Send a successful response for the second enable request
        logd("testRequestSatelliteEnabled_OffToDemoToP2p_SuccessfulResponse: responding to"
                + " the second enable request... (5)");
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertResult(secondEnableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(isSatelliteEnabled());
        verifyDemoMode(false);

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_OffToDemoToP2p_FailureResponse() {
        /*
         * Test scenario:
         * 1) Enable request with demo mode
         * 2) Satellite should move to ENABLING state
         * 3) Enable request with P2P mode
         * 4) Failure response from modem for the first enable request
         * 5) Satellite should move to OFF state and the second enable request should be aborted
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_OffToDemoToP2p_FailureResponse: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_OffToDemoToP2p_FailureResponse: disabling"
                    + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_OffToDemoToP2p_FailureResponse: enabling"
                + " satellite with demo mode... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> firstEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, true, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Change to real mode while enabling demo mode is in progress
        logd("testRequestSatelliteEnabled_OffToDemoToP2p_FailureResponse: updating to real mode"
                + " ... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Send a failure response for the first enable request
        logd("testRequestSatelliteEnabled_OffToDemoToP2p_FailureResponse: responding to"
                + " the first enable request... (4)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_MODEM_ERROR);
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertResult(firstEnableResult, SATELLITE_RESULT_MODEM_ERROR);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(0));
        assertResult(secondEnableResult, SATELLITE_RESULT_REQUEST_ABORTED);
        assertFalse(isSatelliteEnabled());

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse() {
        /*
         * Test scenario:
         * 1) Enable request with P2P mode
         * 2) Satellite should move to ENABLING state
         * 3) Enable request with emergency mode
         * 4) Successful response from modem for the first enable request
         * 5) Satellite should move to NOT_CONNECTED state and in P2P mode
         * 6) Successful response from modem for the second enable request
         * 7) Satellite should stay at NOT_CONNECTED state and in emergency mode
         */
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse: disabling"
                    + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse: enabling"
                + " satellite for P2P SMS... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> firstEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Change to real mode while enabling demo mode is in progress
        logd("testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse: updating to "
                + "emergency mode... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, true);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Send a successful response for the first enable request
        logd("testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse: responding to"
                + " the first enable request... (4)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE));
        assertResult(firstEnableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(0));
        assertTrue(isSatelliteEnabled());
        verifyEmergencyMode(false);
        // The second enable request should be pushed to modem now
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));

        // Send a successful response for the second enable request
        logd("testRequestSatelliteEnabled_OffToP2pToEmergency_SuccessfulResponse: responding to"
                + " the second enable request... (5)");
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertResult(secondEnableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(isSatelliteEnabled());
        verifyEmergencyMode(true);

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse() {
        /*
         * Test scenario:
         * 1) Enable request with P2P mode
         * 2) Satellite should move to ENABLING state
         * 3) Enable request with emergency mode
         * 4) Successful response from modem for the first enable request
         * 5) Satellite should move to NOT_CONNECTED state and in P2P mode
         * 6) Failure response from modem for the second enable request
         * 7) Satellite should stay at NOT_CONNECTED state and in P2P mode
         */
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse: disabling"
                    + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse: enabling"
                + " satellite for P2P SMS... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> firstEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Change to real mode while enabling demo mode is in progress
        logd("testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse: updating to "
                + "emergency mode... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, true);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Send a successful response for the first enable request
        logd("testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse: responding to"
                + " the first enable request... (4)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE));
        assertResult(firstEnableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(0));
        assertTrue(isSatelliteEnabled());
        verifyEmergencyMode(false);
        // The second enable request should be pushed to modem now
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));

        // Send a failure response for the second enable request
        logd("testRequestSatelliteEnabled_OffToP2pToEmergency_FailureResponse: responding to"
                + " the second enable request... (5)");
        sMockSatelliteServiceManager.setErrorCode(
                SatelliteResult.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertResult(secondEnableResult, SATELLITE_RESULT_REQUEST_NOT_SUPPORTED);
        assertTrue(isSatelliteEnabled());
        verifyEmergencyMode(false);

        // Restore the original states
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_DemoToP2p_SuccessfulResponse() {
        /*
         * Test scenario:
         * 1) Enable request with demo mode
         * 2) Satellite should move to ENABLING state
         * 3) Successful response from modem for the first enable request
         * 4) Satellite should move to NOT_CONNECTED state and in demo mode
         * 5) Enable request with P2P mode
         * 6) Satellite should move to ENABLING state
         * 7) Successful response from modem for the second enable request
         * 8) Satellite should move to NOT_CONNECTED state and in P2P mode
         */
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_DemoToP2p_SuccessfulResponse: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_DemoToP2p_SuccessfulResponse: disabling"
                    + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        // Enable satellite with demo mode
        logd("testRequestSatelliteEnabled_DemoToP2p_SuccessfulResponse: enabling"
                + " satellite with demo mode... (2)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        requestSatelliteEnabled(true, true, SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());
        verifyDemoMode(true);

        // Change to P2P mode
        logd("testRequestSatelliteEnabled_DemoToP2p_SuccessfulResponse: updating to real mode"
                + " ... (3)");
        callback.clearModemStates();
        requestSatelliteEnabled(true, false, SATELLITE_RESULT_SUCCESS);
        verifyDemoMode(false);

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_P2pToEmergency_SuccessfulResponse() {
        /*
         * Test scenario:
         * 1) Enable request with P2P mode
         * 2) Satellite should move to ENABLING state
         * 3) Successful response from modem for the first enable request
         * 4) Satellite should move to NOT_CONNECTED state and in P2P mode
         * 5) Enable request with emergency mode
         * 6) Satellite should move to ENABLING state
         * 7) Successful response from modem for the second enable request
         * 8) Satellite should move to NOT_CONNECTED state and in emergency mode
         */
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_P2pToEmergency_SuccessfulResponse: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_P2pToEmergency_SuccessfulResponse: disabling"
                    + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        // Enable satellite with P2P mode
        logd("testRequestSatelliteEnabled_P2pToEmergency_SuccessfulResponse: enabling"
                + " satellite with P2P mode... (2)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        requestSatelliteEnabled(true, false, SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());
        verifyEmergencyMode(false);

        // Change to emergency mode
        logd("testRequestSatelliteEnabled_P2pToEmergency_SuccessfulResponse: updating to emergency"
                + " mode... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, true);
        assertResult(secondEnableResult, SATELLITE_RESULT_SUCCESS);
        verifyEmergencyMode(true);

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_OffToDemoToP2pToOff_SuccessfulResponseForEnable() {
        /*
         * Test scenario:
         * 1) Enable request with demo mode
         * 2) Satellite should move to ENABLING state
         * 3) Enable request with P2P mode
         * 4) Disable request
         * 5) Satellite should move to DISABLING state
         * 6) Successful response from modem for the first enable request
         * 7) The second enable request is aborted
         * 8) Successful response from modem for the disable request
         * 9) Satellite should move to OFF state
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_SuccessfulResponseForEnable: "
                + "starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_SuccessfulResponseForEnable: "
                    + "disabling satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_SuccessfulResponseForEnable: enabling"
                + " satellite with demo mode... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> firstEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, true, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Change to real mode while enabling demo mode is in progress
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_SuccessfulResponseForEnable: "
                + "updating to real mode ... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Disable satellite while enabling and enable attributes updating are in progress
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_SuccessfulResponseForEnable: "
                + "disabling satellite... (4)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));

        // Send a successful response for the first enable request
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_SuccessfulResponseForEnable: "
                + "responding to the first enable request... (5)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE));
        assertResult(firstEnableResult, SATELLITE_RESULT_SUCCESS);
        verifyDemoMode(true);
        assertResult(secondEnableResult, SATELLITE_RESULT_REQUEST_ABORTED);

        // Send a successful response for the disable request
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_SuccessfulResponseForEnable: "
                + "responding to the disable request... (6)");
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(false,
                SatelliteModemState.SATELLITE_MODEM_STATE_OFF));
        assertResult(disableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_OffToDemoToP2pToOff_NoResponseForEnable() {
        /*
         * Test scenario:
         * 1) Enable request with demo mode
         * 2) Enable request with P2P mode
         * 3) Disable request
         * 4) Successful response from modem for the disable request
         * 5) Satellite should move to OFF state and the two enable requests are aborted
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_NoResponseForEnable: "
                + "starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_NoResponseForEnable: "
                    + "disabling satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_NoResponseForEnable: enabling"
                + " satellite with demo mode... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> firstEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, true, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Change to real mode while enabling demo mode is in progress
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_NoResponseForEnable: "
                + "updating to real mode ... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Disable satellite while enabling and enable attributes updating are in progress
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_NoResponseForEnable: disabling"
                + " satellite... (4)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));

        // Send a successful response for the disable request
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_NoResponseForEnable: "
                + "responding to the disable request... (5)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(false,
                SatelliteModemState.SATELLITE_MODEM_STATE_OFF));
        assertResult(disableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(0));
        assertFalse(isSatelliteEnabled());
        assertResult(firstEnableResult, SATELLITE_RESULT_REQUEST_ABORTED);
        assertResult(secondEnableResult, SATELLITE_RESULT_REQUEST_ABORTED);

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable() {
        /*
         * Test scenario:
         * 1) Enable request with demo mode
         * 2) Satellite should move to ENABLING state
         * 3) Enable request with P2P mode
         * 4) Disable request
         * 5) Satellite should move to DISABLING state
         * 6) Failure response from modem for the disable request
         * 7) Satellite should move back to ENABLING state
         * 8) Successful response for the first enable request
         * 9) Satellite should move to NOT_CONNECTED state and in demo mode
         * 10) Successful response for the second enable request
         * 11) Satellite should stay at NOT_CONNECTED state and in P2P mode
         */
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: "
                + "starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: "
                    + "disabling satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: enabling"
                + " satellite with demo mode... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> firstEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, true, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Change to real mode while enabling demo mode is in progress
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: "
                + "updating to real mode ... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Disable satellite while enabling and enable attributes updating are in progress
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: disabling"
                + " satellite... (4)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));

        // Send a failure response for the disable request
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: "
                + "responding to the disable request... (5)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_NO_RESOURCES);
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(false,
                MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertResult(disableResult, SATELLITE_RESULT_NO_RESOURCES);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));

        // Send a successful response for the first enable request
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: "
                + "responding to the first enable request... (6)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                SatelliteModemState.SATELLITE_MODEM_STATE_OUT_OF_SERVICE));
        assertResult(firstEnableResult, SATELLITE_RESULT_SUCCESS);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(0));
        verifyDemoMode(true);
        // The enable attributes update request should be pushed to modem now
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));

        // Send a successful response for the second enable request
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_FailureResponseForDisable: "
                + "responding to the second enable request... (6)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(true,
                MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertResult(secondEnableResult, SATELLITE_RESULT_SUCCESS);
        verifyDemoMode(false);

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);
        revokeSatellitePermission();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_OffToDemoToP2pToOff_ModemOff_FailureResponseForDisable() {
        /*
         * Test scenario:
         * 1) Enable request with demo mode
         * 2) Satellite should move to ENABLING state
         * 3) Enable request with P2P mode
         * 4) Disable request
         * 5) Satellite should move to DISABLING state
         * 6) Modem report OFF state
         * 7) Failure response from modem for the disable request
         * 8) Satellite should move to OFF state and the enable requests are aborted
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_ModemOff_FailureResponseForDisable: "
                + "starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));
        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_ModemOff_"
                    + "FailureResponseForDisable: disabling satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_ModemOff_FailureResponseForDisable: "
                + "enabling satellite with demo mode... (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> firstEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, true, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Change to real mode while enabling demo mode is in progress
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_ModemOff_FailureResponseForDisable: "
                + "updating to real mode ... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Disable satellite while enabling and enable attributes updating are in progress
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_ModemOff_FailureResponseForDisable: "
                + "disabling satellite... (4)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));

        // Send a failure response for the disable request
        logd("testRequestSatelliteEnabled_OffToDemoToP2pToOff_ModemOff_FailureResponseForDisable: "
                + "responding to the disable request... (5)");
        callback.clearModemStates();
        // Send the OFF state before sending the failure response for the disable request
        sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteModemState.SATELLITE_MODEM_STATE_OFF);
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_NO_RESOURCES);
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(false,
                MockSatelliteService.NOT_UPDATED_SATELLITE_MODEM_STATE));
        assertResult(disableResult, SATELLITE_RESULT_NO_RESOURCES);
        assertResult(firstEnableResult, SATELLITE_RESULT_REQUEST_ABORTED);
        assertResult(secondEnableResult, SATELLITE_RESULT_REQUEST_ABORTED);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        // Satellite modem state should be reverted back to ENABLING state due to the failure of
        // the disable request.
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        // Satellite modem state should be changed to OFF state due to the OFF state report from
        // modem.
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF,
                callback.getModemState(1));
        assertFalse(isSatelliteEnabled());

        // Restore the original states
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        revokeSatellitePermission();
    }

    @Ignore("b/405228198 - Need to fix and re-enable this test.")
    @Test
    public void testRequestSatelliteEnabled_ModemCrashDuringDisable() {
        /*
         * Test scenario:
         * 1) Send disable request to modem
         * 2) Satellite should move to DISABLING state
         * 3) Modem crash before responding to framework
         * 4) Modem come back up
         * 5) Framework abort the request and move to OFF state
         */
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_ModemCrashDuringDisable: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to disabling state
        logd("testRequestSatelliteEnabled_ModemCrashDuringDisable: disabling satellite (1)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));
        assertTrue(isSatelliteEnabled());

        // Mocking modem crash scenario
        logd("testRequestSatelliteEnabled_ModemCrashDuringDisable: mocking modem crash (2)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.connectExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteServiceConnected(1));

        // The disable request should be aborted
        assertResult(disableResult, SATELLITE_RESULT_MODEM_ERROR);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Restore original binding state
        logd("testRequestSatelliteEnabled_ModemCrashDuringDisable: restoring mock satellite "
                + "service (3)");
        sMockSatelliteServiceManager.resetSatelliteService();
        assertTrue(sMockSatelliteServiceManager.connectSatelliteService());
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));

        // Telephony will disable satellite when vendor service is connected. This will
        // interfere with the below test and make the test flaky.
        // Enable satellite should succeed
        logd("testRequestSatelliteEnabled_ModemCrashDuringDisable: enabling satellite (4)");
        callback.clearModemStates();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.stopExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager
                .waitForExternalSatelliteServiceDisconnected(1));
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);
        revokeSatellitePermission();
    }

    @Ignore("b/405228198 - Need to fix and re-enable this test.")
    @Test
    public void testRequestSatelliteEnabled_ModemCrashDuringEnable() {
        /*
         * Test scenario:
         * 1) Send enable request to modem
         * 2) Satellite should move to ENABLING state
         * 3) Modem crash before responding to framework
         * 4) Modem come back up
         * 5) Framework abort the request and move to OFF state
         */
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_ModemCrashDuringEnable: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_ModemCrashDuringEnable: disabling satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_ModemCrashDuringEnable: enabling satellite (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> enableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));

        // Mocking modem crash scenario
        logd("testRequestSatelliteEnabled_ModemCrashDuringEnable: mocking modem crash (3)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.connectExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteServiceConnected(1));

        // The enable request should be aborted
        assertResult(enableResult, SATELLITE_RESULT_MODEM_ERROR);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Restore original binding state
        logd("testRequestSatelliteEnabled_ModemCrashDuringEnable: restoring mock satellite "
                + "service (4)");
        sMockSatelliteServiceManager.resetSatelliteService();
        assertTrue(sMockSatelliteServiceManager.connectSatelliteService());
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));

        // Enable satellite should succeed
        logd("testRequestSatelliteEnabled_ModemCrashDuringEnable: enabling satellite (5)");
        callback.clearModemStates();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.stopExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager
                .waitForExternalSatelliteServiceDisconnected(1));
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);
        revokeSatellitePermission();
    }

    @Ignore("b/405228198 - Need to fix and re-enable this test.")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable() {
        /*
         * Test scenario:
         * 1) Send enable request to modem with demo mode
         * 2) Send enable request to modem with P2P mode
         * 3) Send a disable request to modem
         * 4) Modem crash before responding to framework
         * 5) Modem come back up
         * 6) Framework abort all requests and move to OFF state
         */
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        if (isSatelliteEnabled()) {
            logd("testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: disabling"
                    + " satellite... (1)");
            requestSatelliteEnabled(false);
            assertTrue(callback.waitUntilModemOff());
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
            callback.clearModemStates();
        }

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to enabling state
        logd("testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: enabling"
                + " satellite (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> firtEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, true, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));

        // Change to real mode while enabling demo mode is in progress
        logd("testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: "
                + "updating to real mode ... (3)");
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> secondEnableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        // Wait for some time to make sure SatelliteController receive the second request
        waitFor(500);

        // Disable satellite while enabling and enable attributes updating are in progress
        logd("testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: disabling"
                + " satellite... (4)");
        callback.clearModemStates();
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));

        // Mocking modem crash scenario
        logd("testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: mocking modem"
                + " crash (5)");
        callback.clearModemStates();
        assertTrue(sMockSatelliteServiceManager.connectExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager.waitForRemoteSatelliteServiceConnected(1));

        // All requests should be aborted
        assertResult(firtEnableResult, SATELLITE_RESULT_MODEM_ERROR);
        assertResult(secondEnableResult, SATELLITE_RESULT_MODEM_ERROR);
        assertResult(disableResult, SATELLITE_RESULT_MODEM_ERROR);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.getModemState(0));
        assertFalse(isSatelliteEnabled());

        // Restore original binding state
        logd("testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: restoring mock  "
                + "satellite service (6)");
        sMockSatelliteServiceManager.resetSatelliteService();
        assertTrue(sMockSatelliteServiceManager.connectSatelliteService());
        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));

        // Enable satellite should succeed
        logd("testRequestSatelliteEnabled_ModemCrashDuringEnableEnableDisable: enabling"
                + " satellite (7)");
        callback.clearModemStates();
        requestSatelliteEnabled(true);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE,
                callback.getModemState(0));
        assertEquals(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED, callback.getModemState(1));
        assertTrue(isSatelliteEnabled());

        assertTrue(sMockSatelliteServiceManager.stopExternalSatelliteService());
        assertTrue(sMockSatelliteServiceManager
                .waitForExternalSatelliteServiceDisconnected(1));
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);
        revokeSatellitePermission();
    }

    @Test
    public void testRequestSatelliteEnabled_DisableEnable() {
        /*
         * Test scenario:
         * 1) Send disable request
         * 2) Send enable request
         * 3) The enable request is rejected
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_DisableEnable: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to disabling state
        logd("testRequestSatelliteEnabled_DisableEnable: disabling satellite (1)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));
        assertTrue(isSatelliteEnabled());

        // Send an enable request
        logd("testRequestSatelliteEnabled_DisableEnable: enabling satellite (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> enableResult =
                requestSatelliteEnabledWithoutWaitingForResult(true, false, false);
        assertResult(enableResult, SATELLITE_RESULT_DISABLE_IN_PROGRESS);

        // Send a successful response for the disable request
        logd("testRequestSatelliteEnabled_DisableEnable: responding to the disable request (3)");
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(
                false, SatelliteModemState.SATELLITE_MODEM_STATE_OFF));
        assertResult(disableResult, SATELLITE_RESULT_SUCCESS);
        assertFalse(isSatelliteEnabled());

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();

        revokeSatellitePermission();
    }

    @Test
    public void testRequestSatelliteEnabled_DisableDisable() {
        /*
         * Test scenario:
         * 1) Send disable request
         * 2) Send another disable request
         * 3) The second disable request is rejected
         */
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());

        logd("testRequestSatelliteEnabled_DisableDisable: starting...");
        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(false);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, WAIT_FOREVER_TIMEOUT_MILLIS));

        // Move to disabling state
        logd("testRequestSatelliteEnabled_DisableDisable: disabling satellite (1)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> disableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnRequestSatelliteEnabled(1));
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE,
                callback.getModemState(0));
        assertTrue(isSatelliteEnabled());

        // Send another disable request
        logd("testRequestSatelliteEnabled_DisableDisable: disabling satellite (2)");
        sMockSatelliteServiceManager.clearRequestSatelliteEnabledPermits();
        callback.clearModemStates();
        LinkedBlockingQueue<Integer> enableResult =
                requestSatelliteEnabledWithoutWaitingForResult(false, false, false);
        assertResult(enableResult, SATELLITE_RESULT_REQUEST_IN_PROGRESS);

        // Send a successful response for the disable request
        logd("testRequestSatelliteEnabled_DisableDisable: responding to the disable request (3)");
        assertTrue(sMockSatelliteServiceManager.respondToRequestSatelliteEnabled(
                false, SatelliteModemState.SATELLITE_MODEM_STATE_OFF));
        assertResult(disableResult, SATELLITE_RESULT_SUCCESS);
        assertFalse(isSatelliteEnabled());

        sMockSatelliteServiceManager.setShouldRespondEnableRequest(true);
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_WAIT_FOR_SATELLITE_ENABLING_RESPONSE, 0));
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.clearSatelliteEnableRequestQueues();

        revokeSatellitePermission();
    }


    @Test
    public void testRegisterForSupportedStateChanged() {
        logd("testRegisterForSupportedStateChanged: start");
        grantSatellitePermission();

        /* Backup satellite supported state */
        final Pair<Boolean, Integer> originalSupportState = requestIsSatelliteSupported();

        SatelliteSupportedStateCallbackTest satelliteSupportedStateCallbackTest =
                new SatelliteSupportedStateCallbackTest();

        /* Register callback for satellite supported state changed event */
        @SatelliteManager.SatelliteResult int registerError =
                sSatelliteManager.registerForSupportedStateChanged(
                        getContext().getMainExecutor(), satelliteSupportedStateCallbackTest);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);

        /* Verify redundant report is ignored */
        sendOnSatelliteSupportedStateChanged(true);
        assertFalse(satelliteSupportedStateCallbackTest.waitUntilResult(1));

        /* Satellite is unsupported */
        sendOnSatelliteSupportedStateChanged(false);
        assertTrue(satelliteSupportedStateCallbackTest.waitUntilResult(1));
        assertFalse(satelliteSupportedStateCallbackTest.isSupported);

        /* Verify satellite is disabled */
        Pair<Boolean, Integer> pairResult = requestIsSatelliteSupported();
        assertFalse(pairResult.first);
        assertNull(pairResult.second);

        /* Verify redundant report is ignored */
        sendOnSatelliteSupportedStateChanged(false);
        assertFalse(satelliteSupportedStateCallbackTest.waitUntilResult(1));

        /* Verify whether satellite support changed event has received */
        sendOnSatelliteSupportedStateChanged(true);
        assertTrue(satelliteSupportedStateCallbackTest.waitUntilResult(1));
        assertTrue(satelliteSupportedStateCallbackTest.isSupported);

        /* Verify whether notified and requested capabilities are equal */
        pairResult = requestIsSatelliteSupported();
        assertTrue(pairResult.first);
        assertNull(pairResult.second);

        /* Verify redundant report is ignored */
        sendOnSatelliteSupportedStateChanged(true);
        assertFalse(satelliteSupportedStateCallbackTest.waitUntilResult(1));

        /* Restore initial satellite support state */
        sendOnSatelliteSupportedStateChanged(originalSupportState.first);
        satelliteSupportedStateCallbackTest.clearSupportedStates();

        sSatelliteManager.unregisterForSupportedStateChanged(satelliteSupportedStateCallbackTest);
        revokeSatellitePermission();
    }

    @Test
    public void testDemoSimulator() {
        logd("testDemoSimulator: start");
        updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.NB_IOT_NTN}, true);

        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());
        assertTrue(isSatelliteEnabled());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));

        NtnSignalStrengthCallbackTest ntnSignalStrengthCallback =
                new NtnSignalStrengthCallbackTest();
        /* register callback for non-terrestrial network signal strength changed event */
        sSatelliteManager.registerForNtnSignalStrengthChanged(getContext().getMainExecutor(),
                ntnSignalStrengthCallback);

        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_DEMO_POINTING_ALIGNED_DURATION_MILLIS, 5));
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_DEMO_POINTING_NOT_ALIGNED_DURATION_MILLIS, 10));

        try {
            logd("testDemoSimulator: Disable satellite");
            requestSatelliteEnabled(false);
            assertTrue(stateCallback.waitUntilModemOff());
            assertFalse(isSatelliteEnabled());
            stateCallback.clearModemStates();

            logd("testDemoSimulator: Enable satellite for demo mode");
            stateCallback.clearModemStates();
            ntnSignalStrengthCallback.drainPermits();
            requestSatelliteEnabledForDemoMode(true);
            assertTrue(stateCallback.waitUntilResult(2));
            assertTrue(isSatelliteEnabled());
            assertTrue(ntnSignalStrengthCallback.waitUntilResult(1));
            assertEquals(NtnSignalStrength.NTN_SIGNAL_STRENGTH_NONE,
                    ntnSignalStrengthCallback.mNtnSignalStrength.getLevel());

            logd("testDemoSimulator: Set device aligned with satellite");
            stateCallback.clearModemStates();
            ntnSignalStrengthCallback.drainPermits();
            sSatelliteManager.setDeviceAlignedWithSatellite(true);
            assertTrue(stateCallback.waitUntilResult(1));
            assertTrue(ntnSignalStrengthCallback.waitUntilResult(1));
            assertEquals(NtnSignalStrength.NTN_SIGNAL_STRENGTH_MODERATE,
                    ntnSignalStrengthCallback.mNtnSignalStrength.getLevel());

            logd("testDemoSimulator: Set device not aligned with satellite");
            stateCallback.clearModemStates();
            ntnSignalStrengthCallback.drainPermits();
            sSatelliteManager.setDeviceAlignedWithSatellite(false);
            assertTrue(stateCallback.waitUntilResult(1));
            assertTrue(ntnSignalStrengthCallback.waitUntilResult(1));
            assertEquals(NtnSignalStrength.NTN_SIGNAL_STRENGTH_NONE,
                    ntnSignalStrengthCallback.mNtnSignalStrength.getLevel());

            logd("testDemoSimulator: Disable satellite for demo mode");
            stateCallback.clearModemStates();
            requestSatelliteEnabledForDemoMode(false);
            assertTrue(stateCallback.waitUntilResult(2));
            assertFalse(isSatelliteEnabled());
        } finally {
            assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                    true, TIMEOUT_TYPE_DEMO_POINTING_ALIGNED_DURATION_MILLIS, 0));
            assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(
                    true, TIMEOUT_TYPE_DEMO_POINTING_NOT_ALIGNED_DURATION_MILLIS, 0));

            sSatelliteManager.unregisterForNtnSignalStrengthChanged(ntnSignalStrengthCallback);
            sSatelliteManager.unregisterForModemStateChanged(stateCallback);
            updateSupportedRadioTechnologies(new int[]{NTRadioTechnology.PROPRIETARY}, false);
            revokeSatellitePermission();
        }
    }

    @Test
    public void testRequestSessionStats() {
        logd("testRequestSessionStats: start");
        grantSatellitePermission();
        assertTrue(isSatelliteProvisioned());
        assertTrue(isSatelliteEnabled());

        SatelliteModemStateCallbackTest stateCallback = new SatelliteModemStateCallbackTest();
        sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), stateCallback);
        assertTrue(stateCallback.waitUntilResult(1));

        try {
            SatelliteSessionStats sessionStats = new SatelliteSessionStats.Builder()
                    .setCountOfSuccessfulUserMessages(0)
                    .setCountOfUnsuccessfulUserMessages(0)
                    .setCountOfTimedOutUserMessagesWaitingForConnection(0)
                    .setCountOfTimedOutUserMessagesWaitingForAck(0)
                    .setCountOfUserMessagesInQueueToBeSent(0)
                    .build();
            Pair<SatelliteSessionStats, Integer> result = requestSessionStats();
            assertEquals(sessionStats, result.first);

            sessionStats = new SatelliteSessionStats.Builder()
                    .setCountOfSuccessfulUserMessages(5)
                    .setCountOfUnsuccessfulUserMessages(2)
                    .setCountOfTimedOutUserMessagesWaitingForConnection(0)
                    .setCountOfTimedOutUserMessagesWaitingForAck(0)
                    .setCountOfUserMessagesInQueueToBeSent(4)
                    .build();

            for (int i = 0; i < 5; i++) {
                sendSatelliteDatagramSuccess(true, true);
            }

            LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
            String mText = "This is a test datagram message from user";
            SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

            for (int i = 0; i < 2; i++) {
                stateCallback.clearModemStates();
                sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_ERROR);
                sSatelliteManager.sendDatagram(
                        SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                        getContext().getMainExecutor(), resultListener::offer);

                Integer errorCode;
                try {
                    errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    fail("testSendSatelliteDatagram_failure: Got InterruptedException in waiting"
                            + " for the sendSatelliteDatagram result code");
                    return;
                }
                assertNotNull(errorCode);
                assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_ERROR);
            }

            // Wait to process datagrams so that datagrams are added to pending list.
            sMockSatelliteServiceManager.setWaitToSend(true);
            for (int i = 0; i < 4; i++) {
                // Send 4 user messages
                sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                        datagram, true, getContext().getMainExecutor(),
                        resultListener::offer);
            }

            // Send 1 keep alive message
            // This should be ignored and not be included in pending datagrams count
            sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_KEEP_ALIVE,
                    datagram, true, getContext().getMainExecutor(),
                    resultListener::offer);

            result = requestSessionStats();
            assertEquals(sessionStats, result.first);

            sMockSatelliteServiceManager.setWaitToSend(false);
        } finally {
            revokeSatellitePermission();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteSubscriberProvisionStatus() {
        logd("testRequestSatelliteSubscriberProvisionStatus:");
        grantSatellitePermission();
        try {
            Pair<List<SatelliteSubscriberProvisionStatus>, Integer> pairResult =
                    requestSatelliteSubscriberProvisionStatus();
            if (pairResult == null) {
                fail("requestSatelliteSubscriberProvisionStatus "
                        + "List<SatelliteSubscriberProvisionStatus> null");
            }
            for (SatelliteSubscriberProvisionStatus status : pairResult.first) {
                SatelliteSubscriberInfo info = status.getSatelliteSubscriberInfo();
                // Check SubscriberIdType is the
                // SatelliteSubscriberInfo.SUBSCRIBER_ID_TYPE_IMSI_MSISDN
                if (info.getSubscriptionId() == sTestSubIDForCarrierSatellite) {
                    assertEquals(
                            SatelliteSubscriberInfo.SUBSCRIBER_ID_TYPE_IMSI_MSISDN,
                            info.getSubscriberIdType());
                }
            }
        } finally {
            revokeSatellitePermission();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSelectedNbIotSatelliteSubscriptionId() {
        logd("testRequestSelectedNbIotSatelliteSubscriptionId:");
        grantSatellitePermission();
        try {
            Pair<Integer, Integer> pairResult =
                    requestSelectedNbIotSatelliteSubscriptionId();
            if (pairResult == null) {
                fail("requestSelectedNbIotSatelliteSubscriptionId: null");
            }
            assertNotEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID, (long) pairResult.first);
        } finally {
            revokeSatellitePermission();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testRequestSatelliteDisplayName() {
        logd("testRequestSatelliteDisplayName: sEsosSubId=" + sEsosSubId);
        grantSatellitePermission();
        try {
            Pair<CharSequence, Integer> pairResult = requestSatelliteDisplayName();
            if (pairResult == null) {
                fail("requestSatelliteDisplayName: null");
            }
            assertNull(pairResult.second);
            if (TextUtils.isEmpty(pairResult.first)) {
                assumeTrue(sEsosSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);

                String displayName = "Satellite";
                PersistableBundle bundle = new PersistableBundle();
                bundle.putString(
                        CarrierConfigManager.KEY_SATELLITE_DISPLAY_NAME_STRING, displayName);
                overrideCarrierConfig(sEsosSubId, bundle);

                pairResult = requestSatelliteDisplayName();
                if (pairResult == null) {
                    fail("requestSatelliteDisplayName: null");
                }
                assertEquals(displayName, pairResult.first);
                assertNull(pairResult.second);
            }
        } finally {
            revokeSatellitePermission();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testSatelliteSubscriptionProvisionStateChanged() {
        logd("testSatelliteSubscriptionProvisionStateChanged:");
        assumeTrue(sTestSubIDForCarrierSatellite != SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        SatelliteSubscriptionProvisionStateChangedTest callback =
                registerSubscriberIdProvisionCallback();
        assertTrue(provisionSatelliteForSubscriberId(callback));

        afterSubscriberIdTest(callback);
    }

    @Ignore("b/405223241 - This test is flaky. Need to fix and re-enable it.")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testReceiveIntentActionSatelliteSubscriberIdListChangedAfterCarrierConfigChanged()
            throws Exception {
        logd("testReceiveIntentActionSatelliteSubscriberIdListChangedAfterCarrierConfigChanged:");
        sTestSubIDForCarrierSatellite = getActiveSubIDForCarrierSatelliteTest();
        SatelliteReceiverTest receiver = setUpSatelliteReceiverTest();
        Context context = getContext();
        grantSatellitePermission();
        try {
            // ACTION_SATELLITE_SUBSCRIBER_ID_LIST_CHANGED is not sent when satellite is enabled.
            if (isSatelliteEnabled()) {
                logd("Disable satellite");
                SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
                long registerResult = sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
                assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
                assertTrue(callback.waitUntilResult(1));

                requestSatelliteEnabled(false);
                assertTrue(callback.waitUntilModemOff());
                assertFalse(isSatelliteEnabled());

                sSatelliteManager.unregisterForModemStateChanged(callback);
            }

            receiver.clearQueue();
            // Check if the ACTION_SATELLITE_SUBSCRIBER_ID_LIST_CHANGED intent is sent by reading
            // carrier config KEY_SATELLITE_ESOS_SUPPORTED_BOOL value and setting the opposite
            // value.
            boolean eSosSupported = getConfigForSubId(context, sTestSubIDForCarrierSatellite,
                    CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL).getBoolean(
                    CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL, false);
            PersistableBundle bundle = new PersistableBundle();
            bundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL,
                    !eSosSupported);
            overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);
            assertTrue(receiver.waitForReceive());
        } finally {
            resetSatelliteReceiverTest(context, receiver);
            revokeSatellitePermission();
        }
    }

    @Ignore("b/405223241 - This test is flaky. Need to fix and re-enable it.")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testReceiveIntentActionSatelliteSubscriberIdListChangedAfterDefaultSmsSubIdChanged()
            throws Exception {
        assumeTrue(getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_MESSAGING));

        logd("testReceiveIntentActionSatelliteSubscriberIdListChangedAfterDefaultSmsSubIdChanged:");
        SatelliteReceiverTest receiver = setUpSatelliteReceiverTest();
        Context context = getContext();
        SubscriptionManager subscriptionManager = context.getSystemService(
                SubscriptionManager.class);
        int defaultSmsSubId = subscriptionManager.getDefaultSmsSubscriptionId();
        grantSatellitePermission();
        try {
            // ACTION_SATELLITE_SUBSCRIBER_ID_LIST_CHANGED is not sent when satellite is enabled.
            if (isSatelliteEnabled()) {
                logd("Disable satellite");
                SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
                long registerResult = sSatelliteManager.registerForModemStateChanged(
                        getContext().getMainExecutor(), callback);
                assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
                assertTrue(callback.waitUntilResult(1));

                requestSatelliteEnabled(false);
                assertTrue(callback.waitUntilModemOff());
                assertFalse(isSatelliteEnabled());

                sSatelliteManager.unregisterForModemStateChanged(callback);
            }

            boolean eSosSupported = getConfigForSubId(context, sTestSubIDForCarrierSatellite,
                    CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL).getBoolean(
                    CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL, false);
            // Set carrier config KEY_SATELLITE_ESOS_SUPPORTED_BOOL to true.
            if (!eSosSupported) {
                PersistableBundle bundle = new PersistableBundle();
                bundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL, true);
                overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);
            }
            receiver.clearQueue();
            // When the default SMS subId changes, check if the
            // ACTION_SATELLITE_SUBSCRIBER_ID_LIST_CHANGED intent has been sent.
            setDefaultSmsSubId(context, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            assertTrue(receiver.waitForReceive());
        } finally {
            resetSatelliteReceiverTest(context, receiver);
            setDefaultSmsSubId(context, defaultSmsSubId);
            revokeSatellitePermission();
        }
    }

    @Ignore("b/405223241 - This test is flaky. Need to fix and re-enable it.")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testReceiveIntentAfterChangedToOnlyHaveIsNtnOnlyCase() throws Exception {
        logd("testReceiveIntentAfterChangedToOnlyHaveIsNtnOnlyCase:");
        Context context = getContext();
        SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> subscriptionInfoList = sm.getAllSubscriptionInfoList();
        boolean isNtnOnlySimExist = false;
        for (SubscriptionInfo info : subscriptionInfoList) {
            if (info.isOnlyNonTerrestrialNetwork()) {
                isNtnOnlySimExist = true;
            }
        }

        if (isNtnOnlySimExist) {
            SatelliteReceiverTest receiver = setUpSatelliteReceiverTest();
            grantSatellitePermission();
            try {
                boolean eSosSupported = getConfigForSubId(context, sTestSubIDForCarrierSatellite,
                        CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL).getBoolean(
                        CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL, false);
                PersistableBundle bundle = new PersistableBundle();
                // Set carrier config KEY_SATELLITE_ESOS_SUPPORTED_BOOL to true.
                if (!eSosSupported) {
                    bundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL, true);
                    overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);
                }
                // When only is ntn only sim exists for supporting the satellite, check if the
                // ACTION_SATELLITE_SUBSCRIBER_ID_LIST_CHANGED intent has been sent.
                receiver.clearQueue();
                bundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL, false);
                overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);
                assertTrue(receiver.waitForReceive());
            } finally {
                resetSatelliteReceiverTest(context, receiver);
                revokeSatellitePermission();
            }
        }
    }

    private static SatelliteSubscriptionProvisionStateChangedTest registerSubscriberIdProvisionCallback() {
        logd("getSubscriberIdProvisionCallback");
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_EVALUATE_ESOS_PROFILES_PRIORITIZATION_DURATION_MILLIS, 5));
        /* Test when this carrier is supported ESOS in the carrier config */
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_SATELLITE_ESOS_SUPPORTED_BOOL, true);
        overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);

        grantSatellitePermission();
        SatelliteSubscriptionProvisionStateChangedTest callback =
                new SatelliteSubscriptionProvisionStateChangedTest();
        long registerError = sSatelliteManager.registerForProvisionStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);

        return callback;
    }

    private static void afterSubscriberIdTest(
            SatelliteSubscriptionProvisionStateChangedTest callback) {
        if (callback != null) {
            sSatelliteManager.unregisterForProvisionStateChanged(callback);
        }
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_EVALUATE_ESOS_PROFILES_PRIORITIZATION_DURATION_MILLIS, 0));
        revokeSatellitePermission();
    }

    private static List<SatelliteSubscriberInfo> getSatelliteSubscriberInfoList(
            boolean provisioned) {
        List<SatelliteSubscriberInfo> list = new ArrayList<>();
        Pair<List<SatelliteSubscriberProvisionStatus>, Integer> pairResult =
                requestSatelliteSubscriberProvisionStatus();
        if (pairResult == null) {
            return list;
        }
        for (SatelliteSubscriberProvisionStatus status : pairResult.first) {
            SatelliteSubscriberInfo info = status.getSatelliteSubscriberInfo();
            if (provisioned == status.isProvisioned()) {
                list.add(info);
            }
        }
        return list;
    }

    private void deprovisionSatelliteForSubscriberId(
            SatelliteSubscriptionProvisionStateChangedTest callback) {
        List<SatelliteSubscriberInfo> requestDeprovisionSubscriberId =
                getSatelliteSubscriberInfoList(true);
        if (requestDeprovisionSubscriberId.size() == 0) {
            logd("already deprovision this subscriberId");
            return;
        }
        callback.clearProvisionedStates();
        Pair<Boolean, Integer> pairResult = deprovisionSatellite(requestDeprovisionSubscriberId);
        assertTrue(callback.waitUntilResult(1));
        assertTrue(pairResult.first);
        assertFalse(callback.getResultList().get(0).isProvisioned());

        // Request deprovisioning with the same SatelliteSubscriberInfo that was previously
        // requested, and verify that onSatelliteSubscriptionProvisionStateChanged is not called.
        pairResult = deprovisionSatellite(requestDeprovisionSubscriberId);
        assertFalse(callback.waitUntilResult(1));
        assertTrue(pairResult.first);
        callback.clearProvisionedStates();
    }

    private boolean provisionSatelliteForSubscriberId(
            SatelliteSubscriptionProvisionStateChangedTest callback) {
        Pair<List<SatelliteSubscriberProvisionStatus>, Integer> pairResult =
                requestSatelliteSubscriberProvisionStatus();
        if (pairResult == null) {
            fail("requestSatelliteSubscriberProvisionStatus "
                    + "List<SatelliteSubscriberProvisionStatus> null");
            return false;
        }
        if (pairResult.first.size() > 0) {
            // Get the not provisioned subscriberId List.
            List<SatelliteSubscriberInfo> notProvisionedSubscriberList =
                    getSatelliteSubscriberInfoList(false);
            // If all subscriberIds already provisioned then trigger deprovision.
            if (notProvisionedSubscriberList.size() == 0) {
                deprovisionSatelliteForSubscriberId(callback);
            }

            // Get the not provisioned subscriberId List again.
            notProvisionedSubscriberList = getSatelliteSubscriberInfoList(false);
            // Request provisioning with SatelliteSubscriberInfo that has not been provisioned
            // before, and verify that onSatelliteSubscriptionProvisionStateChanged is called.
            if (notProvisionedSubscriberList.size() > 0) {
                Pair<Boolean, Integer> pairResultForProvisionSatellite = provisionSatellite(
                        notProvisionedSubscriberList);
                assertTrue(callback.waitUntilResult(1));
                assertTrue(pairResultForProvisionSatellite.first);
                assertTrue(callback.getResultList().get(0).isProvisioned());

                // Request provisioning with the same SatelliteSubscriberInfo that was previously
                // requested, and verify that onSatelliteSubscriptionProvisionStateChanged is not
                // called.
                pairResultForProvisionSatellite = provisionSatellite(
                        notProvisionedSubscriberList);
                assertFalse(callback.waitUntilResult(1));
                assertTrue(pairResultForProvisionSatellite.first);
            } else {
                logd("provisionSatelliteForSubscriberId: no provisioning list");
                return false;
            }
        }

        logd("provisionSatelliteForSubscriberId true");
        return true;
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN)
    public void testDeprovisionSatellite() {
        logd("testDeprovisionSatellite:");
        sTestSubIDForCarrierSatellite = getActiveSubIDForCarrierSatelliteTest();
        assumeTrue(sTestSubIDForCarrierSatellite != SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        SatelliteSubscriptionProvisionStateChangedTest callback =
                registerSubscriberIdProvisionCallback();
        assertTrue(provisionSatelliteForSubscriberId(callback));
        deprovisionSatelliteForSubscriberId(callback);

        afterSubscriberIdTest(callback);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SATELLITE_25Q4_APIS)
    public void testGetSatelliteDataSupportMode() {
        logd("testGetSatelliteDataSupportMode");

        if (!shouldTestSatellite()) return;
        grantSatellitePermission();

        if (Flags.carrierRoamingNbIotNtn()) {
            sTestSubIDForCarrierSatellite = getActiveSubIDForCarrierSatelliteTest();
            logd("sub_id:" + sTestSubIDForCarrierSatellite);
            if (sTestSubIDForCarrierSatellite == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return;
            }

            // Update available services with data for the carrier sub id
            PersistableBundle bundle = new PersistableBundle();
            int[] defaultSupportedServices = {2, 3, 6};
            bundle.putIntArray(
                    CarrierConfigManager.KEY_CARRIER_ROAMING_SATELLITE_DEFAULT_SERVICES_INT_ARRAY,
                    defaultSupportedServices);
            // With data mode: restricted
            bundle.putInt(CarrierConfigManager.KEY_SATELLITE_DATA_SUPPORT_MODE_INT,
                    SatelliteManager.SATELLITE_DATA_SUPPORT_RESTRICTED);
            overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);

            assertEquals(SatelliteManager.SATELLITE_DATA_SUPPORT_RESTRICTED,
                    sSatelliteManager.getSatelliteDataSupportMode(sTestSubIDForCarrierSatellite));

            // With data mode: constrained
            bundle.putInt(CarrierConfigManager.KEY_SATELLITE_DATA_SUPPORT_MODE_INT,
                    SatelliteManager.SATELLITE_DATA_SUPPORT_CONSTRAINED);
            overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);
            assertEquals(SatelliteManager.SATELLITE_DATA_SUPPORT_CONSTRAINED,
                    sSatelliteManager.getSatelliteDataSupportMode(sTestSubIDForCarrierSatellite));

            // With data mode: UnConstrained
            bundle.putInt(CarrierConfigManager.KEY_SATELLITE_DATA_SUPPORT_MODE_INT,
                    SatelliteManager.SATELLITE_DATA_SUPPORT_UNCONSTRAINED);
            overrideCarrierConfig(sTestSubIDForCarrierSatellite, bundle);
            assertEquals(SatelliteManager.SATELLITE_DATA_SUPPORT_UNCONSTRAINED,
                    sSatelliteManager.getSatelliteDataSupportMode(sTestSubIDForCarrierSatellite));
        }
    }

    /*
     * Before calling this function, caller need to make sure the modem is in LISTENING or IDLE
     * state.
     */
    private void sendSatelliteDatagramWithSuccessfulResult(
            SatelliteModemStateCallbackTest callback, boolean verifyListenToIdleTransition) {
        if (callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_LISTENING
                && callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_IDLE) {
            fail("sendSatelliteDatagramWithSuccessfulResult: wrong modem state="
                    + callback.modemState);
            return;
        }

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemStateChanged: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, (long) errorCode);

        /*
         * Modem state should have the following transitions:
         * 1) IDLE to TRANSFERRING.
         * 2) TRANSFERRING to LISTENING.
         * 3) LISTENING to IDLE.
         *
         * When verifyListenToIdleTransition is true, we expect the above 3 state transitions.
         * Otherwise, we expect only the first 2 transitions since satellite is still in LISTENING
         * state (timeout duration is long when verifyListenToIdleTransition is false).
         */
        int expectedNumberOfEvents = verifyListenToIdleTransition ? 3 : 2;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING,
                callback.getModemState(1));

        /*
         * On entering LISTENING state, we expect one event of EventOnSatelliteListeningEnabled with
         * value true. On exiting LISTENING state, we expect one event of
         * EventOnSatelliteListeningEnabled with value false.
         *
         * When verifyListenToIdleTransition is true, we expect satellite entering and then exiting
         * LISTENING state. Otherwise, we expect satellite entering and staying at LISTENING state.
         */
        expectedNumberOfEvents = verifyListenToIdleTransition ? 2 : 1;
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(
                expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents,
                sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertTrue(sMockSatelliteServiceManager.getListeningEnabled(0));

        if (verifyListenToIdleTransition) {
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                    callback.getModemState(2));
            assertFalse(sMockSatelliteServiceManager.getListeningEnabled(1));
        }
        sMockSatelliteServiceManager.clearListeningEnabledList();
    }

    /*
     * Before calling this function, caller need to make sure the modem is in LISTENING or IDLE
     * state.
     */
    private void sendSatelliteDatagramWithFailedResult(SatelliteModemStateCallbackTest callback) {
        if (callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_LISTENING
                && callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_IDLE) {
            fail("sendSatelliteDatagramWithFailedResult: wrong modem state=" + callback.modemState);
            return;
        }
        boolean isFirstStateListening =
                (callback.modemState == SatelliteManager.SATELLITE_MODEM_STATE_LISTENING);

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_ERROR);
        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sSatelliteManager.sendDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemStateChanged: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_ERROR, (long) errorCode);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                callback.getModemState(1));

        if (isFirstStateListening) {
            assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
            assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
            assertFalse(sMockSatelliteServiceManager.getListeningEnabled(0));
        }
        sMockSatelliteServiceManager.clearListeningEnabledList();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
    }

    /*
     * Before calling this function, caller need to make sure the modem is in LISTENING state.
     */
    private void receiveSatelliteDatagramWithSuccessfulResult(
            SatelliteModemStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING, callback.modemState);

        // TODO (b/275086547): remove the below registerForIncomingDatagram command when the bug
        // is resolved.
        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
        assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertFalse(sMockSatelliteServiceManager.getListeningEnabled(0));

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING,
                callback.getModemState(0));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
        assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertTrue(sMockSatelliteServiceManager.getListeningEnabled(0));
        sMockSatelliteServiceManager.clearListeningEnabledList();

        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
    }

    /*
     * Before calling this function, caller need to make sure the modem is in LISTENING state.
     */
    private void receiveSatelliteDatagramWithFailedResult(
            SatelliteModemStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING, callback.modemState);

        // TODO (b/275086547): remove the below registerForIncomingDatagram command when the bug
        // is resolved.
        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_ERROR);
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                callback.getModemState(1));

        /*
         * On entering LISTENING state, we expect one event of EventOnSatelliteListeningEnabled with
         * value true. On exiting LISTENING state, we expect one event of
         * EventOnSatelliteListeningEnabled with value false.
         *
         * At the beginning of this function, satellite is in LISTENING state. It then transitions
         * to TRANSFERRING state. Thus, we expect one event of EventOnSatelliteListeningEnabled with
         * value false.
         */
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
        assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertFalse(sMockSatelliteServiceManager.getListeningEnabled(0));

        sMockSatelliteServiceManager.clearListeningEnabledList();
        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
        sMockSatelliteServiceManager.setErrorCode(SatelliteResult.SATELLITE_RESULT_SUCCESS);
    }

    private void moveToSendingState() {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest callback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, callback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("moveToSendingState: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        // Send satellite datagram
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        callback.clearSendDatagramStateChanges();
        sMockSatelliteServiceManager.setShouldRespondTelephony(false);
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));

        // Send datagram transfer state should move from IDLE to SENDING.
        assertSingleSendDatagramStateChanged(callback,
                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                1, SatelliteManager.SATELLITE_RESULT_SUCCESS);

        sMockSatelliteServiceManager.setShouldRespondTelephony(true);
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
    }

    private void sendSatelliteDatagramSuccess(
            boolean shouldOverridePointingUiClassName, boolean needFullScreenForPointingUi) {
        SatelliteTransmissionUpdateCallbackTest callback = startTransmissionUpdates();

        // Send satellite datagram
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        callback.clearSendDatagramStateChanges();
        callback.clearSendDatagramRequested();
        if (shouldOverridePointingUiClassName) {
            assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        }
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, needFullScreenForPointingUi, getContext().getMainExecutor(),
                resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));
        assertTrue(callback.waitUntilOnSendDatagramRequested(1));
        assertEquals(1, callback.getNumOfSendDatagramRequestedChanges());
        assertEquals(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                callback.getSendDatagramRequestedType(0));

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         * 2) SENDING to SENDING_SUCCESS
         * 3) SENDING_SUCCESS to IDLE
         */
        int expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(expectedNumOfEvents);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStarted(1));
        if (shouldOverridePointingUiClassName) {
            assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());
        }

        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
    }

    private void sendSatelliteDatagramDemoModeSuccess(String sampleText) {
        SatelliteTransmissionUpdateCallbackTest callback = startTransmissionUpdates();

        // Send satellite datagram
        SatelliteDatagram datagram = new SatelliteDatagram(sampleText.getBytes());
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        callback.clearSendDatagramStateChanges();
        callback.clearSendDatagramRequested();
        assertTrue(sMockSatelliteServiceManager.overrideSatellitePointingUiClassName());
        sMockSatelliteServiceManager.clearMockPointingUiActivityStatusChanges();
        sMockSatelliteServiceManager.clearSentSatelliteDatagramInfo();
        sSatelliteManager.setDeviceAlignedWithSatellite(true);
        assertTrue(sMockSatelliteServiceManager.setShouldSendDatagramToModemInDemoMode(true));
        sSatelliteManager.sendDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                datagram, true, getContext().getMainExecutor(),
                resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSendSatelliteDatagram_success: Got InterruptedException in waiting"
                    + " for the sendDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSendSatelliteDatagram(1));
        assertTrue(callback.waitUntilOnSendDatagramRequested(1));
        assertEquals(1, callback.getNumOfSendDatagramRequestedChanges());
        assertEquals(SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                callback.getSendDatagramRequestedType(0));

        /*
         * Send datagram transfer state should have the following transitions:
         * 1) IDLE to SENDING
         * 2) SENDING to SENDING_SUCCESS
         * 3) SENDING_SUCCESS to IDLE
         */
        int expectedNumOfEvents = 3;
        assertTrue(callback.waitUntilOnSendDatagramStateChanged(expectedNumOfEvents));
        assertThat(callback.getNumOfSendDatagramStateChanges()).isEqualTo(expectedNumOfEvents);
        assertThat(callback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                        1, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(callback.getSendDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertTrue(sMockSatelliteServiceManager.waitForEventMockPointingUiActivityStarted(1));
        assertTrue(sMockSatelliteServiceManager.restoreSatellitePointingUiClassName());

        sSatelliteManager.setDeviceAlignedWithSatellite(false);
        callback.clearSendDatagramStateChanges();
        sSatelliteManager.stopTransmissionUpdates(callback, getContext().getMainExecutor(),
                resultListener::offer);
    }

    private void moveToReceivingState() {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testReceiveSatelliteDatagram: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Datagram transfer state changes from IDLE to RECEIVING.
        assertSingleReceiveDatagramStateChanged(transmissionUpdateCallback,
                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                0, SatelliteManager.SATELLITE_RESULT_SUCCESS);

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        stopTransmissionUpdates(transmissionUpdateCallback);
    }

    private void receiveSatelliteDatagramSuccess() {
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                startTransmissionUpdates();

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForIncomingDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        // Receive one datagram
        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        // As pending count is 0, datagram transfer state changes from
        // IDLE -> RECEIVING -> RECEIVE_SUCCESS -> IDLE.
        int expectedNumOfEvents = 3;
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(expectedNumOfEvents));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(expectedNumOfEvents);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(1)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(2)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                        0, SatelliteManager.SATELLITE_RESULT_SUCCESS));

        transmissionUpdateCallback.clearReceiveDatagramStateChanges();
        stopTransmissionUpdates(transmissionUpdateCallback);
        sSatelliteManager.unregisterForIncomingDatagram(satelliteDatagramCallback);
    }

    private SatelliteTransmissionUpdateCallbackTest startTransmissionUpdates() {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback =
                new SatelliteTransmissionUpdateCallbackTest();
        sSatelliteManager.startTransmissionUpdates(getContext().getMainExecutor(),
                resultListener::offer, transmissionUpdateCallback);
        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("SatelliteTransmissionUpdateCallbackTest: Got InterruptedException in waiting"
                    + " for the startSatelliteTransmissionUpdates result code");
            return null;
        }
        assertNotNull(errorCode);
        assertThat(errorCode).isEqualTo(SatelliteManager.SATELLITE_RESULT_SUCCESS);
        return transmissionUpdateCallback;
    }

    private void stopTransmissionUpdates(
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback) {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.stopTransmissionUpdates(transmissionUpdateCallback,
                getContext().getMainExecutor(), resultListener::offer);
    }

    private void assertSingleSendDatagramStateChanged(
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback,
            int expectedTransferState, int expectedPendingCount, int expectedErrorCode) {
        assertTrue(transmissionUpdateCallback.waitUntilOnSendDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfSendDatagramStateChanges()).isEqualTo(1);
        assertThat(transmissionUpdateCallback.getSendDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        expectedTransferState, expectedPendingCount, expectedErrorCode));
    }

    private void assertSingleReceiveDatagramStateChanged(
            SatelliteTransmissionUpdateCallbackTest transmissionUpdateCallback,
            int expectedTransferState, int expectedPendingCount, int expectedErrorCode) {
        assertTrue(transmissionUpdateCallback
                .waitUntilOnReceiveDatagramStateChanged(1));
        assertThat(transmissionUpdateCallback.getNumOfReceiveDatagramStateChanges())
                .isEqualTo(1);
        assertThat(transmissionUpdateCallback.getReceiveDatagramStateChange(0)).isEqualTo(
                new SatelliteTransmissionUpdateCallbackTest.DatagramStateChangeArgument(
                        expectedTransferState, expectedPendingCount, expectedErrorCode));
    }

    private void identifyRadiosSensitiveToSatelliteMode() {
        PackageManager packageManager = getContext().getPackageManager();
        List<String> satelliteModeRadiosList = new ArrayList<>();
        mBTWifiNFCSateReceiver = new BTWifiNFCStateReceiver();
        mUwbAdapterStateCallback = new UwbAdapterStateCallback();
        IntentFilter radioStateIntentFilter = new IntentFilter();

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            satelliteModeRadiosList.add(Settings.Global.RADIO_WIFI);
            mWifiManager = getContext().getSystemService(WifiManager.class);
            mWifiInitState = mWifiManager.isWifiEnabled();
            radioStateIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        }

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            satelliteModeRadiosList.add(Settings.Global.RADIO_BLUETOOTH);
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mBTInitState = mBluetoothAdapter.isEnabled();
            radioStateIntentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        }

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)) {
            satelliteModeRadiosList.add(Settings.Global.RADIO_NFC);
            mNfcAdapter = NfcAdapter.getDefaultAdapter(getContext().getApplicationContext());
            mNfcInitState = mNfcAdapter.isEnabled();
            radioStateIntentFilter.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        }
        getContext().registerReceiver(mBTWifiNFCSateReceiver, radioStateIntentFilter);

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)) {
            satelliteModeRadiosList.add(Settings.Global.RADIO_UWB);
            mUwbManager = getContext().getSystemService(UwbManager.class);
            mUwbInitState = mUwbManager.isUwbEnabled();
            mUwbManager.registerAdapterStateCallback(getContext().getMainExecutor(),
                    mUwbAdapterStateCallback);
        }

        mTestSatelliteModeRadios = String.join(",", satelliteModeRadiosList);
    }

    private boolean isRadioSatelliteModeSensitive(String radio) {
        return mTestSatelliteModeRadios.contains(radio);
    }

    private boolean areAllRadiosDisabled() {
        logd("areAllRadiosDisabled");
        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_WIFI)) {
            assertFalse(mWifiManager.isWifiEnabled());
        }

        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_UWB)) {
            assertFalse(mUwbManager.isUwbEnabled());
        }

        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_NFC)) {
            assertFalse(mNfcAdapter.isEnabled());
        }

        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_BLUETOOTH)) {
            assertFalse(mBluetoothAdapter.isEnabled());
        }

        return true;
    }

    private boolean areAllRadiosResetToInitialState() {
        logd("areAllRadiosResetToInitialState");

        if (mBTWifiNFCSateReceiver != null
                && isRadioSatelliteModeSensitive(Settings.Global.RADIO_WIFI)) {
            assertTrue(mBTWifiNFCSateReceiver.waitUntilOnWifiStateChanged());
        }

        if (mBTWifiNFCSateReceiver != null
                && isRadioSatelliteModeSensitive(Settings.Global.RADIO_NFC)) {
            assertTrue(mBTWifiNFCSateReceiver.waitUntilOnNfcStateChanged());
        }

        if (mUwbAdapterStateCallback != null
                && isRadioSatelliteModeSensitive(Settings.Global.RADIO_UWB)) {
            assertTrue(mUwbAdapterStateCallback.waitUntilOnUwbStateChanged());
        }

        if (mBTWifiNFCSateReceiver != null
                && isRadioSatelliteModeSensitive(Settings.Global.RADIO_BLUETOOTH)) {
            assertTrue(mBTWifiNFCSateReceiver.waitUntilOnBTStateChanged());
        }

        return true;
    }

    private void setRadioExpectedState() {
        // Set expected state of all radios to their initial states
        if (mBTWifiNFCSateReceiver != null) {
            mBTWifiNFCSateReceiver.setBTExpectedState(mBTInitState);
            mBTWifiNFCSateReceiver.setWifiExpectedState(mWifiInitState);
            mBTWifiNFCSateReceiver.setNfcExpectedState(mNfcInitState);
        }

        if (mUwbAdapterStateCallback != null) {
            mUwbAdapterStateCallback.setUwbExpectedState(mUwbInitState);
        }
    }

    private void unregisterSatelliteModeRadios() {
        getContext().unregisterReceiver(mBTWifiNFCSateReceiver);

        if (isRadioSatelliteModeSensitive(Settings.Global.RADIO_UWB)) {
            mUwbManager.unregisterAdapterStateCallback(mUwbAdapterStateCallback);
        }
    }

    private void requestSatelliteAttachEnabledForCarrier(boolean isEnable,
            int expectedResult) {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestAttachEnabledForCarrier(sTestSubIDForCarrierSatellite,
                isEnable, getContext().getMainExecutor(), resultListener::offer);
        Integer result;
        try {
            result = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestAttachEnabledForCarrier failed with ex=" + ex);
            return;
        }
        assertNotNull(result);
        assertEquals(expectedResult, (int) result);
    }

    private void requestAddSatelliteAttachRestrictionForCarrier(int reason, int expectedResult) {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.addAttachRestrictionForCarrier(sTestSubIDForCarrierSatellite,
                reason, getContext().getMainExecutor(), resultListener::offer);
        Integer result;
        try {
            result = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestAddSatelliteAttachRestrictionForCarrier failed with ex=" + ex);
            return;
        }
        assertNotNull(result);
        assertEquals(expectedResult, (int) result);
    }

    private void verifySatelliteAttachRestrictionForCarrier(int reason, boolean isReasonExpected) {
        Set<Integer> restrictionReasons = sSatelliteManager
                .getAttachRestrictionReasonsForCarrier(sTestSubIDForCarrierSatellite);
        assertNotNull(restrictionReasons);
        if (isReasonExpected) {
            assertTrue(restrictionReasons.contains(reason));
        } else {
            assertFalse(restrictionReasons.contains(reason));
        }
    }

    private void requestRemoveSatelliteAttachRestrictionForCarrier(int reason, int expectedResult) {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        sSatelliteManager.removeAttachRestrictionForCarrier(sTestSubIDForCarrierSatellite,
                reason, getContext().getMainExecutor(), resultListener::offer);
        Integer result;
        try {
            result = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestRemoveSatelliteAttachRestrictionForCarrier failed with ex=" + ex);
            return;
        }
        assertNotNull(result);
        assertEquals(expectedResult, (int) result);
    }

    private Pair<Boolean, Integer> requestIsSatelliteAttachEnabledForCarrier() {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> callback = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        logd("onResult: result=" + result);
                        enabled.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("onError: onError=" + exception);
                        callback.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsAttachEnabledForCarrier(sTestSubIDForCarrierSatellite,
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        return new Pair<>(enabled.get(), callback.get());
    }

    private Pair<NtnSignalStrength, Integer> requestNtnSignalStrength() {
        final AtomicReference<NtnSignalStrength> ntnSignalStrength = new AtomicReference<>();
        final AtomicReference<Integer> callback = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<NtnSignalStrength, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(NtnSignalStrength result) {
                        logd("onResult: result=" + result);
                        ntnSignalStrength.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("onError: onError=" + exception);
                        callback.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestNtnSignalStrength(getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        return new Pair<>(ntnSignalStrength.get(), callback.get());
    }

    private Pair<SatelliteCapabilities, Integer> requestSatelliteCapabilities() {
        final AtomicReference<SatelliteCapabilities> SatelliteCapabilities =
                new AtomicReference<>();
        final AtomicReference<Integer> callback = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<SatelliteCapabilities, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(SatelliteCapabilities result) {
                        logd("onResult: result=" + result);
                        SatelliteCapabilities.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("onError: onError=" + exception);
                        callback.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestCapabilities(getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        return new Pair<>(SatelliteCapabilities.get(), callback.get());
    }

    private Pair<SatelliteSessionStats, Integer> requestSessionStats() {
        AtomicReference<SatelliteSessionStats> sessionStats = new AtomicReference<>();
        AtomicReference<Integer> callback = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<SatelliteSessionStats, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<SatelliteSessionStats, SatelliteManager.SatelliteException>() {
                    @Override
                    public void onResult(SatelliteSessionStats result) {
                        logd("requestSessionStats onResult:" + result);
                        sessionStats.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("requestSessionStats onError:" + exception);
                        callback.set(exception.getErrorCode());
                        latch.countDown();
                    }

                };

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MODIFY_PHONE_STATE,
                        Manifest.permission.PACKAGE_USAGE_STATS);
        try {
            sSatelliteManager.requestSessionStats(getContext().getMainExecutor(), receiver);
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
            grantSatellitePermission();
        }

        return new Pair<>(sessionStats.get(), callback.get());
    }

    private Pair<Boolean, Integer> requestIsSatelliteSupported() {
        final AtomicReference<Boolean> supported = new AtomicReference<>();
        final AtomicReference<Integer> callback = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        logd("requestIsSatelliteSupported.onResult: result=" + result);
                        supported.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("requestIsSatelliteSupported.onError: onError="
                                + exception.getErrorCode());
                        callback.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsSupported(getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        return new Pair<>(supported.get(), callback.get());
    }

    private Pair<SatelliteAccessConfiguration,
            Integer> requestSatelliteAccessConfigurationForCurrentLocation() {
        final AtomicReference<SatelliteAccessConfiguration> satelliteAccessConfiguration =
                new AtomicReference<>();
        final AtomicReference<Integer> callback = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<SatelliteAccessConfiguration, SatelliteManager.SatelliteException>
                receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(SatelliteAccessConfiguration result) {
                        logd("requestSatelliteAccessConfigurationForCurrentLocation: result="
                                + result);
                        satelliteAccessConfiguration.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("requestSatelliteAccessConfigurationForCurrentLocation: onError="
                                + exception.getErrorCode());
                        callback.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestSatelliteAccessConfigurationForCurrentLocation(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        return new Pair<>(satelliteAccessConfiguration.get(), callback.get());
    }

    private abstract static class BaseReceiver extends BroadcastReceiver {
        protected CountDownLatch mLatch = new CountDownLatch(1);

        void clearQueue() {
            mLatch = new CountDownLatch(1);
        }

        void waitForChanged() throws Exception {
            mLatch.await(5000, TimeUnit.MILLISECONDS);
        }
    }

    private static class CarrierConfigReceiver extends BaseReceiver {
        private final int mSubId;

        CarrierConfigReceiver(int subId) {
            mSubId = subId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(intent.getAction())) {
                int subId = intent.getIntExtra(CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX, -1);
                if (mSubId == subId) {
                    mLatch.countDown();
                }
            }
        }
    }

    private static void overrideCarrierConfig(int subId, PersistableBundle bundle) {
        logd("overrideCarrierConfig: subId=" + subId + ", bundle=" + bundle);
        try {
            CarrierConfigManager carrierConfigManager = InstrumentationRegistry.getInstrumentation()
                    .getContext().getSystemService(CarrierConfigManager.class);
            sCarrierConfigReceiver.clearQueue();
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(carrierConfigManager,
                    (m) -> m.overrideConfig(subId, bundle));
            sCarrierConfigReceiver.waitForChanged();
        } catch (Exception ex) {
            loge("overrideCarrierConfig(), ex=" + ex);
        } finally {
            grantSatellitePermission();
        }
    }

    private void setSatelliteError(@SatelliteManager.SatelliteResult int error) {
        @RadioError int satelliteError;

        switch (error) {
            case SatelliteManager.SATELLITE_RESULT_SUCCESS:
                satelliteError = SatelliteResult.SATELLITE_RESULT_SUCCESS;
                break;
            case SatelliteManager.SATELLITE_RESULT_INVALID_MODEM_STATE:
                satelliteError = SatelliteResult.SATELLITE_RESULT_INVALID_MODEM_STATE;
                break;
            case SATELLITE_RESULT_MODEM_ERROR:
                satelliteError = SatelliteResult.SATELLITE_RESULT_MODEM_ERROR;
                break;
            case SatelliteManager.SATELLITE_RESULT_RADIO_NOT_AVAILABLE:
                satelliteError = SatelliteResult.SATELLITE_RESULT_RADIO_NOT_AVAILABLE;
                break;
            case SATELLITE_RESULT_REQUEST_NOT_SUPPORTED:
            default:
                satelliteError = SatelliteResult.SATELLITE_RESULT_REQUEST_NOT_SUPPORTED;
                break;
        }

        sMockSatelliteServiceManager.setErrorCode(satelliteError);
    }

    private void setSatelliteErrorBasedOnHalVersion(@SatelliteResult int error) {
        if (getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) < RADIO_HAL_VERSION_2_3) {
            setSatelliteError(error);
            return;
        }

        @RadioError int satelliteError;
        switch (error) {
            case SatelliteManager.SATELLITE_RESULT_SUCCESS:
                satelliteError = RadioError.NONE;
                break;
            case SatelliteManager.SATELLITE_RESULT_INVALID_MODEM_STATE:
                satelliteError = RadioError.INVALID_MODEM_STATE;
                break;
            case SATELLITE_RESULT_MODEM_ERROR:
                satelliteError = RadioError.MODEM_ERR;
                break;
            case SatelliteManager.SATELLITE_RESULT_RADIO_NOT_AVAILABLE:
                satelliteError = RadioError.RADIO_NOT_AVAILABLE;
                break;
            case SATELLITE_RESULT_REQUEST_NOT_SUPPORTED:
                satelliteError = RadioError.REQUEST_NOT_SUPPORTED;
                break;
            default:
                satelliteError = RadioError.GENERIC_FAILURE;
                break;
        }

        if (sMockModemManager != null) {
            sMockModemManager.setSatelliteErrorCode(SLOT_ID_0, satelliteError);
        }
    }

    private void setNtnSignalStrength(
            @NtnSignalStrength.NtnSignalStrengthLevel int ntnSignalStrengthLevel) {
        sMockSatelliteServiceManager.setNtnSignalStrength(toHAL(ntnSignalStrengthLevel));
    }

    private void sendOnNtnSignalStrengthChanged(
            @NtnSignalStrength.NtnSignalStrengthLevel int ntnSignalStrengthLevel) {
        sMockSatelliteServiceManager.sendOnNtnSignalStrengthChanged(toHAL(ntnSignalStrengthLevel));
    }

    private void sendOnSatelliteCapabilitiesChanged(
            android.telephony.satellite.stub.SatelliteCapabilities satelliteCapabilities) {
        sMockSatelliteServiceManager.sendOnSatelliteCapabilitiesChanged(satelliteCapabilities);
    }

    private void sendOnSatelliteSupportedStateChanged(boolean supported) {
        sMockSatelliteServiceManager.sendOnSatelliteSupportedStateChanged(supported);
    }

    @Nullable
    private android.telephony.satellite.stub.NtnSignalStrength toHAL(
            @NtnSignalStrength.NtnSignalStrengthLevel int signalStrengthLevelFromFramework) {
        android.telephony.satellite.stub.NtnSignalStrength ntnSignalStrength =
                new android.telephony.satellite.stub.NtnSignalStrength();

        switch (signalStrengthLevelFromFramework) {
            case NtnSignalStrength.NTN_SIGNAL_STRENGTH_NONE:
                ntnSignalStrength.signalStrengthLevel = android.telephony.satellite.stub
                        .NtnSignalStrengthLevel.NTN_SIGNAL_STRENGTH_NONE;
                break;
            case NtnSignalStrength.NTN_SIGNAL_STRENGTH_POOR:
                ntnSignalStrength.signalStrengthLevel = android.telephony.satellite.stub
                        .NtnSignalStrengthLevel.NTN_SIGNAL_STRENGTH_POOR;
                break;
            case NtnSignalStrength.NTN_SIGNAL_STRENGTH_MODERATE:
                ntnSignalStrength.signalStrengthLevel = android.telephony.satellite.stub
                        .NtnSignalStrengthLevel.NTN_SIGNAL_STRENGTH_MODERATE;
                break;
            case NtnSignalStrength.NTN_SIGNAL_STRENGTH_GOOD:
                ntnSignalStrength.signalStrengthLevel = android.telephony.satellite.stub
                        .NtnSignalStrengthLevel.NTN_SIGNAL_STRENGTH_GOOD;
                break;
            case NtnSignalStrength.NTN_SIGNAL_STRENGTH_GREAT:
                ntnSignalStrength.signalStrengthLevel = android.telephony.satellite.stub
                        .NtnSignalStrengthLevel.NTN_SIGNAL_STRENGTH_GREAT;
                break;
            default:
                ntnSignalStrength.signalStrengthLevel = android.telephony.satellite.stub
                        .NtnSignalStrengthLevel.NTN_SIGNAL_STRENGTH_NONE;
                break;
        }
        return ntnSignalStrength;
    }

    private boolean getIsSatelliteEnabledForCarrierFromMockService() {
        if (getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) < RADIO_HAL_VERSION_2_3) {
            Boolean receivedResult = sMockSatelliteServiceManager.getIsSatelliteEnabledForCarrier();
            return receivedResult != null ? receivedResult : false;
        }

        return sMockModemManager.getIsSatelliteEnabledForCarrier(SLOT_ID_0);
    }

    private boolean getIsEmergency() {
        Boolean receivedResult = sMockSatelliteServiceManager.getIsEmergency();
        return receivedResult != null ? receivedResult : false;
    }

    private void clearSatelliteEnabledForCarrier() {
        if (getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) < RADIO_HAL_VERSION_2_3) {
            sMockSatelliteServiceManager.clearSatelliteEnabledForCarrier();
            return;
        }
        sMockModemManager.clearSatelliteEnabledForCarrier(SLOT_ID_0);
    }


    static int sTestSubIDForCarrierSatellite;
    static int sNtnOnlySubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    static int sEsosSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    static SubscriptionManager sSubscriptionManager;
    static boolean sPreviousSatelliteAttachEnabled;
    static boolean sOriginalNtnOnlyState;
    static boolean sPreviousESOSSupported;
    static boolean sPreviousESOSSupportedOfNtnOnlySub;
    static String[] sPreviousSupportedMsgApps;

    static final int SLOT_ID_0 = 0;
    static final int SLOT_ID_1 = 1;
    static MockModemManager sMockModemManager;

    private void beforeSatelliteForCarrierTest() {
        try {
            MockModemManager.enforceMockModemDeveloperSetting();
            sMockModemManager = new MockModemManager();
            assertNotNull(sMockModemManager);
            assertTrue(sMockModemManager.connectMockModemService());

            assertTrue(sMockModemManager.insertSimCard(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT));
            TimeUnit.MILLISECONDS.sleep(TIMEOUT);
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT, true);
        } catch (Exception e) {
            loge("beforeSatelliteForCarrierTest: exception=" + e);
        }

        sTestSubIDForCarrierSatellite = getActiveSubIDForCarrierSatelliteTest();
        sSubscriptionManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(SubscriptionManager.class);
        // Get the default subscription values for COLUMN_SATELLITE_ATTACH_ENABLED_FOR_CARRIER.
        sPreviousSatelliteAttachEnabled =
                sSubscriptionManager.getBooleanSubscriptionProperty(sTestSubIDForCarrierSatellite,
                        SubscriptionManager.SATELLITE_ATTACH_ENABLED_FOR_CARRIER,
                        false,
                        getContext());
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            // Set user Setting as false
            sSubscriptionManager.setSubscriptionProperty(sTestSubIDForCarrierSatellite,
                    SubscriptionManager.SATELLITE_ATTACH_ENABLED_FOR_CARRIER, String.valueOf(0));
        } finally {
            ui.dropShellPermissionIdentity();
        }
    }

    private static void enableNtnOnlySubscription() {
        assumeTrue(sNtnOnlySubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        sSubscriptionManager = InstrumentationRegistry.getInstrumentation()
                .getContext().getSystemService(SubscriptionManager.class);
        sOriginalNtnOnlyState =
                sSubscriptionManager.getBooleanSubscriptionProperty(sNtnOnlySubId,
                        SubscriptionManager.IS_ONLY_NTN,
                        false,
                        getContext());
        logd("enableNtnOnlySubscription: sOriginalNtnOnlyState="
                        + sOriginalNtnOnlyState
                        + ", sNtnOnlySubId="
                        + sNtnOnlySubId);
        if (sOriginalNtnOnlyState) {
            logd("enableNtnOnlySubscription: subId=" + sNtnOnlySubId + " is already NTN only");
            return;
        }

        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            sSubscriptionManager.setSubscriptionProperty(sNtnOnlySubId,
                    SubscriptionManager.IS_ONLY_NTN, String.valueOf(1));
        } finally {
            ui.dropShellPermissionIdentity();
        }
        waitForNtnOnlySubscriptionAvailable();
    }

    private void afterSatelliteForCarrierTest() {
        // Set user Setting value to previous one.
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            sSubscriptionManager.setSubscriptionProperty(sTestSubIDForCarrierSatellite,
                    SubscriptionManager.SATELLITE_ATTACH_ENABLED_FOR_CARRIER,
                    sPreviousSatelliteAttachEnabled ? "1" : "0");

            // Leave service
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT, false);
            // Remove the SIM
            sMockModemManager.removeSimCard(SLOT_ID_0);
            if (sMockModemManager != null) {
                assertTrue(sMockModemManager.disconnectMockModemService());
                sMockModemManager = null;
            }
        } catch (Exception e) {
            loge("afterSatelliteForCarrierTest: exception=" + e);
        } finally {
            ui.dropShellPermissionIdentity();
            sTestSubIDForCarrierSatellite = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
    }

    private static void restoreNtnOnlySubscription() {
        logd("restoreNtnOnlySubscription: sOriginalNtnOnlyState="
                        + sOriginalNtnOnlyState
                        + ", sNtnOnlySubId="
                        + sNtnOnlySubId);
        if (sNtnOnlySubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            logd("restoreNtnOnlySubscription: no need to restore");
            return;
        }

        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            ui.adoptShellPermissionIdentity();
            sSubscriptionManager.setSubscriptionProperty(sNtnOnlySubId,
                    SubscriptionManager.IS_ONLY_NTN,
                    sOriginalNtnOnlyState ? "1" : "0");
        } finally {
            ui.dropShellPermissionIdentity();
        }
    }

    private void updateSupportedRadioTechnologies(
            @NonNull int[] supportedRadioTechnologies, boolean needSetUp) {
        logd("updateSupportedRadioTechnologies: supportedRadioTechnologies="
                + supportedRadioTechnologies[0]);
        grantSatellitePermission();

        SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
        long registerResult = sSatelliteManager.registerForModemStateChanged(
                getContext().getMainExecutor(), callback);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
        assertTrue(callback.waitUntilResult(1));

        assertTrue(sMockSatelliteServiceManager.restoreSatelliteServicePackageName());
        waitFor(2000);
        sSatelliteManager.unregisterForModemStateChanged(callback);
        sMockSatelliteServiceManager.setSupportedRadioTechnologies(supportedRadioTechnologies);
        try {
            setupMockSatelliteService();
            if (needSetUp) {
                setUp();
            }
        } catch (Exception e) {
            loge("Fail to set up mock satellite service after updating supported radio "
                    + "technologies, e=" + e);
        }

        revokeSatellitePermission();
    }

    private Pair<Boolean, Integer> requestIsCommunicationAllowedForCurrentLocation() {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> callback = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        logd("isSatelliteAllowed.onResult: result=" + result);
                        enabled.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("isSatelliteAllowed.onError: onError=" + exception);
                        callback.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsCommunicationAllowedForCurrentLocation(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail("isSatelliteAllowed: ex=" + e);
        }
        return new Pair<>(enabled.get(), callback.get());
    }

    private void verifyIsSatelliteAllowed(boolean allowed) {
        grantSatellitePermission();
        logd("verifyIsSatelliteAllowed: calling requestIsCommunicationAllowedForCurrentLocation");
        Pair<Boolean, Integer> result =
                requestIsCommunicationAllowedForCurrentLocation();
        logd(
                "verifyIsSatelliteAllowed: result of"
                        + " requestIsCommunicationAllowedForCurrentLocation: "
                        + result.first
                        + ", "
                        + result.second);
        assertNotNull(result.first);
        assertEquals(allowed, result.first);
    }

    private void verifySatelliteNotAllowedErrorReason(int expectedError) {
        grantSatellitePermission();
        logd(
                "verifySatelliteNotAllowedErrorReason: calling"
                        + " requestIsCommunicationAllowedForCurrentLocation");
        Pair<Boolean, Integer> result =
                requestIsCommunicationAllowedForCurrentLocation();
        logd(
                "verifySatelliteNotAllowedErrorReason: result of"
                        + " requestIsCommunicationAllowedForCurrentLocation: "
                        + result.first
                        + ", "
                        + result.second);
        assertNotNull(result.second);
        assertEquals(expectedError, (int) result.second);
    }

    private static void registerTestLocationProvider() {
        requestMockLocationPermission(true);
        sLocationManager.setLocationEnabledForUser(true, Process.myUserHandle());
        sLocationManager.addTestProvider(TEST_PROVIDER,
                new ProviderProperties.Builder().build());
        sLocationManager.setTestProviderEnabled(TEST_PROVIDER, true);
    }

    private static void unregisterTestLocationProvider() {
        requestMockLocationPermission(true);
        sLocationManager.removeTestProvider(TEST_PROVIDER);
        requestMockLocationPermission(false);
    }

    private void setTestProviderLocation(double latitude, double longitude) {
        logd(
                "setTestProviderLocation: setting test provider location to: latitude="
                        + latitude
                        + ", longitude="
                        + longitude);
        requestMockLocationPermission(true);
        Location loc = LocationUtils.createLocation(
                TEST_PROVIDER, latitude, longitude, LOCATION_ACCURACY);
        logd("setTestProviderLocation: loc=" + loc);
        sLocationManager.setTestProviderLocation(TEST_PROVIDER, loc);
    }

    private static void requestMockLocationPermission(boolean allowed) {
        AppOpsManager aom = getContext().getSystemService(AppOpsManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(aom, (appOpsMan) -> appOpsMan
                .setUidMode(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(),
                        allowed ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_ERRORED));
    }

    private static void setUpSatelliteAccessAllowed() {
        logd("setUpSatelliteAccessAllowed...");
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(false,
                OVERRIDING_COUNTRY_CODES, null, null, 0));
        assertTrue(sMockSatelliteServiceManager.setSatelliteAccessControlOverlayConfigs(
                false, true, null, 0, SATELLITE_COUNTRY_CODES, null));
    }

    private static void resetSatelliteAccessControlOverlayConfigs() {
        logd("resetSatelliteAccessControlOverlayConfigs");
        assertTrue(sMockSatelliteServiceManager.setCountryCodes(true, null, null, null, 0));
        assertTrue(sMockSatelliteServiceManager.setSatelliteAccessControlOverlayConfigs(
                true, true, null, 0, null, null));
    }

    private static SatelliteReceiverTest setUpSatelliteReceiverTest() {
        SatelliteReceiverTest receiver = new SatelliteReceiverTest();
        sTestSubIDForCarrierSatellite = getActiveSubIDForCarrierSatelliteTest();
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_EVALUATE_ESOS_PROFILES_PRIORITIZATION_DURATION_MILLIS, 5));
        Context context = getContext();
        assertTrue(sMockSatelliteServiceManager.setSatelliteSubscriberIdListChangedIntentComponent(
                "package"));
        assertTrue(sMockSatelliteServiceManager.setSatelliteSubscriberIdListChangedIntentComponent(
                "class"));
        context.registerReceiver(receiver, new IntentFilter(SatelliteReceiver.TEST_INTENT),
                Context.RECEIVER_EXPORTED);
        return receiver;
    }

    private static void resetSatelliteReceiverTest(Context context, SatelliteReceiverTest receiver) {
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_EVALUATE_ESOS_PROFILES_PRIORITIZATION_DURATION_MILLIS, 0));
        assertTrue(sMockSatelliteServiceManager
                .setSatelliteSubscriberIdListChangedIntentComponent("reset"));
        context.unregisterReceiver(receiver);
    }

    private static void waitForNtnOnlySubscriptionAvailable() {
        Context context = getContext();
        SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
        int i = 0;
        while (i < 10) {
            List<SubscriptionInfo> subscriptionInfoList = sm.getAllSubscriptionInfoList();
            for (SubscriptionInfo info : subscriptionInfoList) {
                if (info.isOnlyNonTerrestrialNetwork()) {
                    logd("waitForNtnOnlySubscriptionAvailable: NTN only subscription  " + info
                            + " is available");
                    return;
                }
            }
            i++;
            waitFor(500);
        }
        fail("NTN only subscription is not available");
    }

    private static void overrideSatelliteAccessForNtnOnlySubscription() {
        assumeTrue(sNtnOnlySubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        // Check if device has selected a binding satellite subscription
        grantSatellitePermission();
        Pair<Integer, Integer> selectedSatelliteSubIdPairResult =
                requestSelectedNbIotSatelliteSubscriptionId();
        boolean shouldWaitForSelectedSatelliteSubChanged =
            (selectedSatelliteSubIdPairResult.first == null
                || selectedSatelliteSubIdPairResult.first
                == SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        // Register callback for satellite subscription id changed event
        SelectedNbIotSatelliteSubscriptionCallbackTest
        selectedNbIotSatelliteSubscriptionCallbackTest =
                new SelectedNbIotSatelliteSubscriptionCallbackTest();
        long registerError =
                sSatelliteManager.registerForSelectedNbIotSatelliteSubscriptionChanged(
                        getContext().getMainExecutor(),
                        selectedNbIotSatelliteSubscriptionCallbackTest);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerError);
        assertTrue(selectedNbIotSatelliteSubscriptionCallbackTest.waitUntilResult(1));
        selectedNbIotSatelliteSubscriptionCallbackTest.drainPermits();

        String subIdListStr = String.valueOf(sNtnOnlySubId);
        logd("overrideSatelliteAccessForNtnOnlySubscription: subIdListStr=" + subIdListStr);
        assertTrue(sMockSatelliteServiceManager.setSatelliteAccessAllowedForSubscriptions(
                subIdListStr));

        if (shouldWaitForSelectedSatelliteSubChanged)  {
            // Overrding satellite access should trigger the selected satellite subscription
            // changed event.
            assertTrue(selectedNbIotSatelliteSubscriptionCallbackTest.waitUntilResult(1));
            logd("overrideSatelliteAccessForNtnOnlySubscription: selectedSatelliteSubId="
                    + selectedNbIotSatelliteSubscriptionCallbackTest.mSelectedSubId);
            assertNotEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                    selectedNbIotSatelliteSubscriptionCallbackTest.mSelectedSubId);
        }
    }

    private static void resetSatelliteAccessForNtnOnlySubscription() {
        logd("resetSatelliteAccessForNtnOnlySubscription");
        assertTrue(sMockSatelliteServiceManager.setSatelliteAccessAllowedForSubscriptions(null));
    }

    private static void enableDefaultSmsAppSupportForNtnOnlySubscription() {
        // Assume that binding satellite subscription is already selected before this step
        assumeTrue(sNtnOnlySubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        logd("enableDefaultSmsAppSupportForNtnOnlySubscription");
        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(false,
                TIMEOUT_TYPE_EVALUATE_ESOS_PROFILES_PRIORITIZATION_DURATION_MILLIS, 5));

        sPreviousSupportedMsgApps = getConfigForSubId(getContext(), sNtnOnlySubId,
            CarrierConfigManager.KEY_SATELLITE_SUPPORTED_MSG_APPS_STRING_ARRAY)
            .getStringArray(
                CarrierConfigManager.KEY_SATELLITE_SUPPORTED_MSG_APPS_STRING_ARRAY);

        String defaultSmsApp = null;
        ComponentName defaultSmsAppComp =
                SmsApplication.getDefaultSmsApplication(getContext(), false);
        if (defaultSmsAppComp != null) {
            defaultSmsApp = defaultSmsAppComp.getPackageName();
        }
        logd("enableDefaultSmsAppSupportForNtnOnlySubscription: defaultSmsApp=" + defaultSmsApp
                 + ", sPreviousSupportedMsgApps=" + (sPreviousSupportedMsgApps == null
                     ? "null" : Arrays.toString(sPreviousSupportedMsgApps)));

        int existingLength =
                sPreviousSupportedMsgApps == null ? 0 : sPreviousSupportedMsgApps.length;
        int newLength = existingLength;
        boolean isDefaultSmsAppSupported = false;
        if (defaultSmsApp != null) {
            if (sPreviousSupportedMsgApps == null
                    || !containString(sPreviousSupportedMsgApps, defaultSmsApp)) {
                newLength++;
            } else {
                logd("enableDefaultSmsAppSupportForNtnOnlySubscription: defaultSmsApp="
                        + defaultSmsApp + " is already supported");
                isDefaultSmsAppSupported = true;
            }
        } else {
            fail("Device does not have a default SMS app");
            return;
        }

        String[] newSupportedMsgApps = new String[newLength];
        if (existingLength > 0) {
            System.arraycopy(sPreviousSupportedMsgApps, 0, newSupportedMsgApps, 0,
                    sPreviousSupportedMsgApps.length);
        }
        if (newLength > existingLength) {
            newSupportedMsgApps[newSupportedMsgApps.length - 1] = defaultSmsApp;
        }
        logd("enableDefaultSmsAppSupportForNtnOnlySubscription: newSupportedMsgApps="
                 + Arrays.toString(newSupportedMsgApps));

        SatelliteDisallowedReasonsCallbackTest callback =
                registerForSatelliteDisallowedReasonsChanged();
        boolean hasUnsupportedDefaultMsgAppDisallowedReason = callback.hasSatelliteDisabledReason(
                SATELLITE_DISALLOWED_REASON_UNSUPPORTED_DEFAULT_MSG_APP);
        callback.drainPermits();

        if (!isDefaultSmsAppSupported || hasUnsupportedDefaultMsgAppDisallowedReason) {
            logd("enableDefaultSmsAppSupportForNtnOnlySubscription: updating default SMS app...");

            PersistableBundle bundle = new PersistableBundle();
            bundle.putStringArray(CarrierConfigManager.KEY_SATELLITE_SUPPORTED_MSG_APPS_STRING_ARRAY,
                    newSupportedMsgApps);
            overrideCarrierConfig(sNtnOnlySubId, bundle);

            if (hasUnsupportedDefaultMsgAppDisallowedReason) {
                assertTrue(callback.waitUntilResult(1));
                assertFalse(callback.hasSatelliteDisabledReason(
                                SATELLITE_DISALLOWED_REASON_UNSUPPORTED_DEFAULT_MSG_APP));
            }
        } else {
            logd("enableDefaultSmsAppSupportForNtnOnlySubscription: no need to update default SMS app");
        }
    }

    private static boolean containString(String[] strArray, String str) {
        for (String element : strArray) {
            if (TextUtils.equals(element, str)) {
                return true;
            }
        }
        return false;
    }

    private static void restoreDefaultSmsAppSupportForNtnOnlySubscription() {
        logd("restoreDefaultSmsAppSupportForNtnOnlySubscription");
        if (sNtnOnlySubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            logd("restoreDefaultSmsAppSupportForNtnOnlySubscription: no need to restore");
            return;
        }

        PersistableBundle bundle = new PersistableBundle();
        bundle.putStringArray(CarrierConfigManager.KEY_SATELLITE_SUPPORTED_MSG_APPS_STRING_ARRAY,
                sPreviousSupportedMsgApps);
        overrideCarrierConfig(sNtnOnlySubId, bundle);

        assertTrue(sMockSatelliteServiceManager.setSatelliteControllerTimeoutDuration(true,
                TIMEOUT_TYPE_EVALUATE_ESOS_PROFILES_PRIORITIZATION_DURATION_MILLIS, 0));
    }

    private static void setUpNtnOnlySubscription() {
        logd("setUpNtnOnlySubscription");
        assumeTrue(sNtnOnlySubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        enableNtnOnlySubscription();
        overrideSatelliteAccessForNtnOnlySubscription();
        if (!isSatelliteProvisioned()) {
            logd("setUpNtnOnlySubscription: Provision satellite");
            assertTrue(provisionSatellite());
        } else {
            logd("setUpNtnOnlySubscription: Satellite already provisioned");
        }
        // Binding satellite subscription need to be selected before this step
        enableDefaultSmsAppSupportForNtnOnlySubscription();
    }

    private static SatelliteDisallowedReasonsCallbackTest
            registerForSatelliteDisallowedReasonsChanged() {
        SatelliteDisallowedReasonsCallbackTest callback =
                new SatelliteDisallowedReasonsCallbackTest();
        sSatelliteManager.registerForSatelliteDisallowedReasonsChanged(
                getContext().getMainExecutor(), callback);
        assertTrue(callback.waitUntilResult(1));
        return callback;
    }

    private static void deprovisionSatelliteForDevice() {
        List<SatelliteSubscriberInfo> provisionedSubscriberList =
                getSatelliteSubscriberInfoList(true);
        if (provisionedSubscriberList.size() == 0) {
            logd("Device is not provisioned");
            return;
        }
        Pair<Boolean, Integer> pairResult = deprovisionSatellite(provisionedSubscriberList);
        assertNotNull(pairResult);
        assertTrue(pairResult.first);
    }

    private void assertIsEnabledState(boolean expectedIsEnabledStateChanged,
            boolean expectedIsEnabledState) {
        try {
            final boolean isEnabledStateChanged = mIsEnabledStateChangedLatch.await(
                    MAX_WAIT_FOR_STATE_CHANGED_SECONDS, TimeUnit.SECONDS);

            assertThat(isEnabledStateChanged).isEqualTo(expectedIsEnabledStateChanged);
            assertThat(mIsEnabled).isEqualTo(expectedIsEnabledState);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("InterruptedException while waiting for state change.");
        }
    }

    private boolean waitForEventOnSetSatellitePlmn(int expectedNumOfEvents) {
        if (getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) < RADIO_HAL_VERSION_2_3) {
            return sMockSatelliteServiceManager.waitForEventOnSetSatellitePlmn(expectedNumOfEvents);
        }

        return sMockModemManager.waitForEventOnSetSatellitePlmn(expectedNumOfEvents);
    }

    @Nullable
    List<String> getCarrierPlmnList() {
        if (getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) < RADIO_HAL_VERSION_2_3) {
            return sMockSatelliteServiceManager.getCarrierPlmnList();
        }

        return sMockModemManager.getCarrierPlmnList(SLOT_ID_0);
    }

    @Nullable
    List<String> getAllSatellitePlmnList() {
        if (getHalVersion(TelephonyManager.HAL_SERVICE_NETWORK) < RADIO_HAL_VERSION_2_3) {
            return sMockSatelliteServiceManager.getAllSatellitePlmnList();
        }

        return sMockModemManager.getAllSatellitePlmnList(SLOT_ID_0);
    }

    private static void moveSatelliteToOffState() {
        grantSatellitePermission();
        if (isSatelliteEnabled()) {
            SatelliteModemStateCallbackTest callback = new SatelliteModemStateCallbackTest();
            long registerResult = sSatelliteManager.registerForModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, registerResult);
            assertTrue(callback.waitUntilResult(1));

            logd("moveSatelliteToOffState: Moving satellite to off state");
            callback.clearModemStates();
            sMockSatelliteServiceManager.sendOnSatelliteModemStateChanged(
                SatelliteModemState.SATELLITE_MODEM_STATE_OFF);
            assertTrue(callback.waitUntilModemOff());
            sSatelliteManager.unregisterForModemStateChanged(callback);
        } else {
            logd("moveSatelliteToOffState: Satellite is already off");
        }
    }
}