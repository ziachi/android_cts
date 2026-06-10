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

package android.telephony.satellite.cts;

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;

import static junit.framework.Assert.assertNotNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.cts.AsyncSmsMessageListener;
import android.telephony.cts.SmsReceiverHelper;
import android.telephony.cts.util.DefaultSmsAppHelper;
import android.telephony.cts.util.TelephonyUtils;
import android.util.Base64;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class CarrierRoamingSatelliteSmsTest extends CarrierRoamingSatelliteTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "CarrierRoamingSatelliteSmsTest";
    private static final String SMS_SEND_ACTION = "CTS_SMS_SEND_ACTION";
    private static final String TEST_EMERGENCY_NUMBER = "+14154255486";
    private static final String TEST_DEST_ADDR = "1234567890";
    private static final String EXPECTED_RECEIVED_MESSAGE = "foo5";
    private static final String RECEIVED_MESSAGE = "B5EhYBMDIPgEC5FhBWKFkPEAAEGQQlGDUooE5ve7Bg==";
    @SuppressWarnings("StaticAssignmentOfThrowable")
    private static AssertionError sInitError = null;

    /**
     * Setup before all tests.
     * @throws Exception
     */
    @BeforeClass
    @SuppressWarnings("StaticAssignmentOfThrowable")
    public static void beforeAllTests() throws Exception {
        logd(TAG, "beforeAllTests");

        if (!shouldTestSatelliteWithMockService()) return;

        try {
            beforeAllTestsBase();
            insertSatelliteEnabledSim(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT);
        } catch (AssertionError e) {
            sInitError = e;
        }
    }

    /**
     * Cleanup resources after all tests.
     * @throws Exception
     */
    @AfterClass
    public static void afterAllTests() throws Exception {
        logd(TAG, "afterAllTests");

        if (!shouldTestSatelliteWithMockService()) return;

        removeSatelliteEnabledSim(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT);
        afterAllTestsBase();
    }

    @Before
    public void setUp() throws Exception {
        logd(TAG, "setUp()");

        if (sInitError != null) throw sInitError;
        assumeTrue(shouldTestSatelliteWithMockService());
        assumeTrue(getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_MESSAGING));

        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG, "tearDown()");
        if (!shouldTestSatelliteWithMockService()) return;
    }

    @Test
    public void testSendMessage() throws Exception {
        logd(TAG, "testSendMessage");

        // Test non-default SMS app
        sendMessage(TEST_DEST_ADDR, Activity.RESULT_OK);

        // Test default SMS app
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        sendMessage(TEST_DEST_ADDR, Activity.RESULT_OK);
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @Test
    public void testReceiveMessage() throws Exception {
        logd(TAG, "testReceiveMessage");

        // Test non-default SMS app
        receiveMessage();

        // Test default SMS app
        DefaultSmsAppHelper.ensureDefaultSmsApp();
        receiveMessage();
        DefaultSmsAppHelper.stopBeingDefaultSmsApp();
    }

    @Test
    public void testSendEmergencySms() throws Exception {
        logd(TAG, "testSendEmergencySms");

        TelephonyUtils.addTestEmergencyNumber(
                InstrumentationRegistry.getInstrumentation(), TEST_EMERGENCY_NUMBER);
        try {
            sendMessage(TEST_EMERGENCY_NUMBER, Activity.RESULT_OK);

            // Test default SMS app
            DefaultSmsAppHelper.ensureDefaultSmsApp();
            sendMessage(TEST_EMERGENCY_NUMBER, Activity.RESULT_OK);
            DefaultSmsAppHelper.stopBeingDefaultSmsApp();
        } finally {
            TelephonyUtils.removeTestEmergencyNumber(
                    InstrumentationRegistry.getInstrumentation(), TEST_EMERGENCY_NUMBER);
        }
    }

    @Test
    public void testSendSmsAutomaticallyOnSatelliteConnected() throws Exception {
        logd(TAG, "testSendSmsAutomaticallyOnSatelliteConnected");

        sendMessageAutomaticallyAfterSatelliteConnectionIsRestored(TEST_DEST_ADDR);
    }

    @Test
    public void testSendEmergencySmsAutomaticallyOnSatelliteConnected() throws Exception {
        logd(TAG, "testSendEmergencySmsAutomaticallyOnSatelliteConnected");

        TelephonyUtils.addTestEmergencyNumber(
                InstrumentationRegistry.getInstrumentation(), TEST_EMERGENCY_NUMBER);
        try {
            sendMessageAutomaticallyAfterSatelliteConnectionIsRestored(TEST_EMERGENCY_NUMBER);
        } finally {
            TelephonyUtils.removeTestEmergencyNumber(
                    InstrumentationRegistry.getInstrumentation(), TEST_EMERGENCY_NUMBER);
        }
    }

    @Test
    public void testSendSmsImsEnabled() throws Exception {
        logd(TAG, "testSendSmsImsEnabled");

        beforeAllTestBaseForIms();

        try {
            sendMessageImsEnabled(TEST_DEST_ADDR);
        } finally {
            afterAllTestBaseForIms();
        }
    }

    @Test
    public void testReceiveSmsImsEnabled() throws Exception {
        logd(TAG, "testReceiveSmsImsEnabled");

        beforeAllTestBaseForIms();

        try {
            receiveMessageImsEnabled();
        } finally {
            afterAllTestBaseForIms();
        }
    }

    @Test
    public void testSendEmergencySmsImsEnabled() throws Exception {
        logd(TAG, "testSendEmergencySmsImsEnabled");

        beforeAllTestBaseForIms();
        TelephonyUtils.addTestEmergencyNumber(InstrumentationRegistry.getInstrumentation(),
                TEST_EMERGENCY_NUMBER);

        try {
            sendMessageImsEnabled(TEST_EMERGENCY_NUMBER);
        } finally {
            TelephonyUtils.removeTestEmergencyNumber(InstrumentationRegistry.getInstrumentation(),
                    TEST_EMERGENCY_NUMBER);
            afterAllTestBaseForIms();
        }
    }

    private void sendMessage(String destAddr, int resultCode) throws Exception {
        logd(TAG, "sendMessage destAddr:" + destAddr + ", resultCode:" + resultCode);

        // Register receiver
        SmsMmsBroadcastReceiver sendReceiver = new SmsMmsBroadcastReceiver();
        sendReceiver.setAction(SMS_SEND_ACTION);
        getContext().registerReceiver(sendReceiver, new IntentFilter(sendReceiver.getAction()),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        Intent sendIntent = new Intent(SMS_SEND_ACTION).setPackage(getContext().getPackageName());
        PendingIntent sendPendingIntent = PendingIntent.getBroadcast(getContext(), 0,
                sendIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);

        try {
            getSmsManager().sendTextMessage(destAddr, null,
                    String.valueOf(SystemClock.elapsedRealtimeNanos()), sendPendingIntent,
                    null);

            assertTrue(sendReceiver.waitForBroadcast(1));
            assertEquals(resultCode, sendReceiver.getResultCode());
        } finally {
            getContext().unregisterReceiver(sendReceiver);
        }
    }

    private void receiveMessage() throws Exception {
        logd(TAG, "receiveMessage()");

        // Register receiver
        SmsMmsBroadcastReceiver receivedReceiver = new SmsMmsBroadcastReceiver();
        receivedReceiver.setAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
        getContext().registerReceiver(receivedReceiver,
                new IntentFilter(receivedReceiver.getAction()),
                Context.RECEIVER_EXPORTED_UNAUDITED);

        assertTrue(sMockModemManager.triggerIncomingSms(SLOT_ID_0));

        try {
            assertTrue(receivedReceiver.waitForBroadcast(1));
        } finally {
            getContext().unregisterReceiver(receivedReceiver);
        }
    }

    private void sendMessageImsEnabled(String destAddr) throws Exception {
        logd(TAG, "sendMessageImsEnabled destAddr:" + destAddr);

        // Register receiver
        SmsMmsBroadcastReceiver sendReceiver = new SmsMmsBroadcastReceiver();
        sendReceiver.setAction(SMS_SEND_ACTION);
        getContext().registerReceiver(sendReceiver,  new IntentFilter(sendReceiver.getAction()),
                Context.RECEIVER_EXPORTED_UNAUDITED);
        PendingIntent sendPendingIntent = SmsReceiverHelper.getMessageSentPendingIntent(
                InstrumentationRegistry.getInstrumentation().getTargetContext());

        getSmsManager().sendTextMessage(destAddr, null,
                String.valueOf(SystemClock.elapsedRealtimeNanos()), sendPendingIntent, null);

        assertTrue(sServiceConnector.getCarrierService().getMmTelFeature()
                .getSmsImplementation().waitForMessageSentLatchSuccess());
        Intent intent = AsyncSmsMessageListener.getInstance().waitForMessageSentIntent(
                (int) TIMEOUT);
        assertNotNull(intent);
        assertEquals(Activity.RESULT_OK, intent.getIntExtra(SmsReceiverHelper.EXTRA_RESULT_CODE,
                Activity.RESULT_CANCELED));
    }

    private void receiveMessageImsEnabled() throws Exception {
        logd(TAG, "receiveMessageImsEnabled()");

        // Message received
        sServiceConnector.getCarrierService().getMmTelFeature().getSmsImplementation()
                .receiveSmsWaitForAcknowledge(123456789, SmsMessage.FORMAT_3GPP,
                        Base64.decode(RECEIVED_MESSAGE, Base64.DEFAULT));

        // Wait for SMS received intent and ensure it is correct.
        String receivedMessage = AsyncSmsMessageListener.getInstance()
                .waitForSmsMessage((int) TIMEOUT);
        Assert.assertEquals(EXPECTED_RECEIVED_MESSAGE, receivedMessage);
    }

    private void sendMessageAutomaticallyAfterSatelliteConnectionIsRestored(String destAddr)
            throws Exception {
        logd(TAG, "sendMessageAutomaticallyAfterSatelliteConnectionIsRestored");

        // Loose the satellite network.
        ServiceStateListenerTest serviceStateListener = new ServiceStateListenerTest();
        serviceStateListener.clearServiceStateChanges();
        sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(),
                serviceStateListener);
        sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT, false);
        assertTrue(serviceStateListener.waitUntilNonTerrestrialNetworkDisconnected());

        try {
            // Send sms which will fail as there is no satellite network.
            sendMessage(destAddr, SmsManager.RESULT_ERROR_NO_SERVICE);
        } finally {
            // Get satellite network and sms should be sent automatically.
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT, true);
            assertTrue(serviceStateListener.waitUntilNonTerrestrialNetworkConnected());
        }

        // Since the message app triggers automatic send, send it again here.
        sendMessage(destAddr, Activity.RESULT_OK);
    }
}
