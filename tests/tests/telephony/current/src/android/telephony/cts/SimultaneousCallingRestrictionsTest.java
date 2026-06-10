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

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_FET;

import static com.android.internal.telephony.RILConstants.RIL_REQUEST_RADIO_POWER;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.ImsService;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.cts.ImsServiceConnector;
import android.telephony.ims.cts.ImsUtils;
import android.telephony.ims.cts.TestImsService;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.mockmodem.MockModemManager;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_SIMULTANEOUS_CALLING_INDICATIONS)
public class SimultaneousCallingRestrictionsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    private static ImsServiceConnector sServiceConnectorSlot0;
    private static ImsServiceConnector sServiceConnectorSlot1;
    private static TelephonyManager sTelephonyManager;
    private static TelecomManager sTelecomManager;
    private static MockModemManager sMockModemManager;
    private static SimultaneousCallingListener sSimultaneousCallingListener;
    private static List<PhoneAccountHandle> sCallCapablePhoneAccounts;
    private static UiAutomation sUiAutomation;
    private static boolean sIsMultiSimDevice;
    private static boolean sIsMockModemAllowed;
    private static Throwable sCapturedSetupThrowable;
    private static boolean sFeatureEnabled;
    private static int sTestSubSlot0 = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static int sTestSubSlot1 = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final String BOOT_ALLOW_MOCK_MODEM_PROPERTY = "ro.boot.radio.allow_mock_modem";
    private static final boolean DEBUG = !"user".equals(Build.TYPE);
    public static final int TEST_TIMEOUT_MS = 5000;
    private static final int TEST_SLOT_0 = 0;
    private static final int TEST_SLOT_1 = 1;
    private static final String TAG = "SimultaneousCallingRestrictionsTest";
    private static final int IMS_REGI_TECH_LTE = ImsRegistrationImplBase.REGISTRATION_TECH_LTE;
    private static final int IMS_REGI_TECH_IWLAN = ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;

    private static class SimultaneousCallingListener extends TelephonyCallback implements
            TelephonyCallback.SimultaneousCellularCallingSupportListener {
        private Set<Integer> mSimultaneousCallingSubIds = new HashSet<>(2);

        @Override
        public void onSimultaneousCellularCallingSubscriptionsChanged(
                @NonNull Set<Integer> simultaneousCallingSubscriptionIds) {
            Log.d(TAG, "onSimultaneousCellularCallingSubscriptionsChanged from ["
                    + mSimultaneousCallingSubIds + "]to[" + simultaneousCallingSubscriptionIds
                    + "]");
            mSimultaneousCallingSubIds.clear();
            mSimultaneousCallingSubIds = simultaneousCallingSubscriptionIds;
        }

        public Set<Integer> getSimultaneousCallingSubIds() {
            return mSimultaneousCallingSubIds;
        }
    }

    // NOTE: BeforeClass can NOT throw exceptions
    @BeforeClass
    public static void beforeAllTests() {
        // @Rule doesn't support skipping @BeforeClass, so we need to do this manually so we
        // can skip setting up the mock modem if not needed.
        sFeatureEnabled = Flags.simultaneousCallingIndications();
        if (!ImsUtils.shouldTestTelephony()) {
            Log.d(TAG, "beforeAllTests: Telephony Feature is not enabled on this device. ");
            return;
        }
        if (!sFeatureEnabled) {
            Log.d(TAG, "beforeAllTests: Simultaneous Calling is not enabled on this device ");
            return;
        }
        Log.d(TAG, "beforeAllTests: begin");
        // Configure the MockModem:
        sTelephonyManager = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);
        sIsMultiSimDevice = isMultiSim(sTelephonyManager);
        if (!sIsMultiSimDevice) {
            Log.d(TAG, "beforeAllTests: Device is not multi-SIM, skipping all tests.");
            return;
        }
        // We can not throw exceptions here - instead capture and throw in @Before
        sIsMockModemAllowed = isMockModemAllowed();
        if (!sIsMockModemAllowed) {
            Log.w(TAG, "beforeAllTests: Mock modem is not allowed - skipping");
            return;
        }
        sUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        // We can not actually throw anything from @BeforeClass, because it can cause undefined
        // behavior - instead, we should catch it here and rethrow in @Before and fail the
        // associated @Tests.
        try {
            sMockModemManager = new MockModemManager();
            assertNotNull(sMockModemManager);
            assertTrue(sMockModemManager.connectMockModemService());
            sMockModemManager.insertSimCard(TEST_SLOT_0, MOCK_SIM_PROFILE_ID_TWN_CHT);
            waitForSimStateReadyOrTimeout(TEST_SLOT_0);
            sMockModemManager.insertSimCard(TEST_SLOT_1, MOCK_SIM_PROFILE_ID_TWN_FET);
            waitForSimStateReadyOrTimeout(TEST_SLOT_1);
            sTestSubSlot0 = waitForActiveSubIdOrTimeout(TEST_SLOT_0);
            sTestSubSlot1 = waitForActiveSubIdOrTimeout(TEST_SLOT_1);

            // Cache the list of call capable phone accounts after both SIMs have been added:
            sTelecomManager = (TelecomManager) getContext()
                    .getSystemService(Context.TELECOM_SERVICE);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelecomManager, tm -> {
                updateCallCapablePhAcctsAfterSubAdded(sTestSubSlot0, tm);
                updateCallCapablePhAcctsAfterSubAdded(sTestSubSlot1, tm);
                sCallCapablePhoneAccounts.removeIf(h -> !h.getComponentName().getShortClassName()
                        .equals("com.android.services.telephony.TelephonyConnectionService"));

            });
            sSimultaneousCallingListener = registerNewSimultaneousCallingListener();

            if (!ImsUtils.shouldTestImsService()) {
                Log.d(TAG, "beforeAllTests: IMS feature not supported, skipping IMS setup.");
                return;
            }
            sServiceConnectorSlot0 = new ImsServiceConnector(
                    InstrumentationRegistry.getInstrumentation());
            sServiceConnectorSlot1 = new ImsServiceConnector(
                    InstrumentationRegistry.getInstrumentation());
            // Remove all live ImsServices until after these tests are done
            sServiceConnectorSlot0.clearAllActiveImsServices(TEST_SLOT_0);
            sServiceConnectorSlot1.clearAllActiveImsServices(TEST_SLOT_1);
        } catch (Throwable th) {
            sCapturedSetupThrowable = th;
        }
    }

    // NOTE: AfterClass can NOT throw Exceptions.
    @AfterClass
    public static void afterAllTests() {
        if (!ImsUtils.shouldTestTelephony() || !sIsMultiSimDevice || !sFeatureEnabled
                || !sIsMockModemAllowed) {
            Log.d(TAG, "afterAllTests: Skipping - previous assumption failures");
            return;
        }
        Log.d(TAG, "afterAllTests");

        // Restore all ImsService configurations that existed before the test:
        try {
            if (sServiceConnectorSlot0 != null) {
                sServiceConnectorSlot0.disconnectServices();
            }
            if (sServiceConnectorSlot1 != null) {
                sServiceConnectorSlot1.disconnectServices();
            }
        } catch (Exception e) {
            Log.w(TAG, "afterAllTests, IMS couldn't be torn down: " + e);
        }
        sServiceConnectorSlot0 = null;
        sServiceConnectorSlot1 = null;

        if (sSimultaneousCallingListener != null) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                    (tm) -> tm.unregisterTelephonyCallback(sSimultaneousCallingListener));
        }

        sCallCapablePhoneAccounts = null;

        // Rebind all interfaces which is binding to MockModemService to default:
        if (sMockModemManager == null) {
            Log.w(TAG, "afterAllTests: MockModemManager is null!");
            return;
        }
        try {
            // Remove the SIMs:
            sMockModemManager.removeSimCard(TEST_SLOT_0);
            sMockModemManager.removeSimCard(TEST_SLOT_1);
        } catch (Exception e) {
            Log.w(TAG, "afterAllTests, MockModem couldn't remove SIMs: " + e);
        }
        try {
            // Reset the modified error response of RIL_REQUEST_RADIO_POWER to the original behavior
            // and -1 means to disable the modified mechanism in MockModem:
            sMockModemManager.forceErrorResponse(0, RIL_REQUEST_RADIO_POWER, -1);
            if (!sMockModemManager.disconnectMockModemService()) {
                Log.w(TAG, "afterAllTests: disconnectMockModemService did not return"
                        + " successfully!");
            }
        } catch (Exception e) {
            Log.w(TAG, "afterAllTests, MockModem couldn't be torn down: " + e);
        }
        sMockModemManager = null;
    }

    @Before
    public void beforeTest() throws Throwable {
        if (!ImsUtils.shouldTestImsService() || !sIsMultiSimDevice) {
            return;
        }
        Log.d(TAG, "beforeTest");
        if (sCapturedSetupThrowable != null) {
            // Throw the captured error from @BeforeClass, which will print the stack trace and
            // fail this test.
            throw sCapturedSetupThrowable;
        }
        if (!sIsMockModemAllowed) {
            fail("!! Enable Mock Modem before running this test !! "
                    + "Developer options => Allow Mock Modem");
        }
        if (sTelephonyManager.getSimState(TEST_SLOT_0) != TelephonyManager.SIM_STATE_READY
                || sTelephonyManager.getSimState(TEST_SLOT_1) != TelephonyManager.SIM_STATE_READY
        ) {
            fail("This test requires that there are two SIMs in the device!");
        }
        // Correctness check: ensure that the subscription hasn't changed between tests.
        int subId_0 = SubscriptionManager.getSubscriptionId(TEST_SLOT_0);
        if (subId_0 != sTestSubSlot0) {
            fail("The found subId " + subId_0 + " does not match the test sub id " + sTestSubSlot0);
        }
        int subId_1 = SubscriptionManager.getSubscriptionId(TEST_SLOT_1);
        if (subId_1 != sTestSubSlot1) {
            fail("The found subId " + subId_1 + " does not match the test sub id " + sTestSubSlot1);
        }
    }

    @After
    public void afterTest() throws Exception {
        if (!ImsUtils.shouldTestImsService() || !sIsMultiSimDevice) {
            return;
        }
        Log.d(TAG, "afterTest");

        // Unbind the ImsService after the test completes.
        if (sServiceConnectorSlot0 != null) {
            sServiceConnectorSlot0.disconnectCarrierImsService();
            sServiceConnectorSlot0.disconnectDeviceImsService();
        }
        if (sServiceConnectorSlot1 != null) {
            sServiceConnectorSlot1.disconnectCarrierImsService();
            sServiceConnectorSlot1.disconnectDeviceImsService();
        }
    }

    /**
     * Test the case where the modem reports that cellular simultaneous calling is supported and
     * ensure that the framework marks the subIds as simultaneous calling supported.
     */
    @Test
    public void testCellularDSDASupported_IMSNotRegistered() throws Throwable {
        Log.d(TAG, "testCellularDSDASupported_SimultaneousCallingEnabled");
        assumeTrue("Skip test: Not test on single SIM device", sIsMultiSimDevice);
        assumeTrue("Skip test: FEATURE_TELEPHONY not setup",
                ImsUtils.shouldTestTelephony());

        // Set the enabled logical slots to be returned from the modem:
        setSimultaneousCallingEnabledLogicalSlots(new int[]{TEST_SLOT_0, TEST_SLOT_1});

        try {
            verifyCellularSimultaneousCallingSupport(true, sSimultaneousCallingListener);
            verifySimultaneousCallingRestrictions(true);
        } finally {
            // Reset an empty array as the enabled logical slots to be returned from the modem:
            setSimultaneousCallingEnabledLogicalSlots(new int[]{});
        }
    }

    /**
     * Test the case where the modem reports that cellular simultaneous calling is not supported and
     * ensure that the framework marks the subIds as not simultaneous calling supported.
     */
    @Test
    public void testCellularDSDANotSupported_IMSNotRegistered() throws Throwable {
        Log.d(TAG, "testCellularDSDASupported_SimultaneousCallingEnabled");
        assumeTrue("Skip test: Not test on single SIM device", sIsMultiSimDevice);
        assumeTrue("Skip test: FEATURE_TELEPHONY not setup",
                ImsUtils.shouldTestTelephony());

        // Set an empty array as the enabled logical slots to be returned from the modem:
        setSimultaneousCallingEnabledLogicalSlots(new int[]{});

        verifyCellularSimultaneousCallingSupport(false, sSimultaneousCallingListener);
        verifySimultaneousCallingRestrictions(false);
    }

    /**
     * Test that when IMS is registered over WWAN & cellular simultaneous calling is supported that
     * the framework marks simultaneous calling as enabled.
     */
    @Test
    public void testCellularDSDASupported_ImsRegisteredWWAN() throws Exception {
        Log.d(TAG, "testImsRegisteredWWANCellularDSDASupported_SimultaneousCallingEnabled");
        assumeTrue("Skip test: Not test on single SIM device", sIsMultiSimDevice);
        assumeTrue("Skip test: ImsService and/or FEATURE_TELEPHONY are not setup",
                ImsUtils.shouldTestImsService());

        // Set the enabled logical slots to be returned from the modem:
        setSimultaneousCallingEnabledLogicalSlots(new int[]{TEST_SLOT_0, TEST_SLOT_1});

        Pair<RegistrationManager.RegistrationCallback,
                LinkedBlockingQueue<ImsRegistrationAttributes>> result_0 = null;
        Pair<RegistrationManager.RegistrationCallback,
                LinkedBlockingQueue<ImsRegistrationAttributes>> result_1 = null;

        try {
            // Ensure IMS for Sub 0 starts unregistered:
            result_0 = attachCarrierImsServiceAndSetUnregistered();

            // Ensure IMS for Sub 1 starts unregistered:
            result_1 = attachDeviceImsServiceAndSetUnregistered();

            verifyCellularSimultaneousCallingSupport(true,
                    sSimultaneousCallingListener);
            // Register IMS via WWAN for both subs and then verify that DSDA is enabled via
            // cellular:
            registerImsForBothSubsAndVerifyAttributes(IMS_REGI_TECH_LTE, IMS_REGI_TECH_LTE,
                    result_0.second, result_1.second);
            verifySimultaneousCallingRestrictions(true);
        } finally {
            // Reset an empty array as the enabled logical slots to be returned from the modem:
            setSimultaneousCallingEnabledLogicalSlots(new int[]{});
            // Unregister IMS callbacks if they were registered successfully:
            if (result_0 != null) {
                unregisterImsCallback(result_0.first, sTestSubSlot0);
            }
            if (result_1 != null) {
                unregisterImsCallback(result_1.first, sTestSubSlot1);
            }
        }
    }

    /**
     * Test that when IMS is registered over WWAN and cellular simultaneous calling is not enabled,
     * the framework marks simultaneous calling as disabled.
     */
    @Test
    public void testCellularDSDANotSupported_ImsRegisteredWWAN() throws Exception {
        Log.d(TAG, "testImsRegisteredWWAN_SimultaneousCallingDisabled");
        assumeTrue("Skip test: Not test on single SIM device", sIsMultiSimDevice);
        assumeTrue("Skip test: ImsService and/or FEATURE_TELEPHONY are not setup",
                ImsUtils.shouldTestImsService());

        Pair<RegistrationManager.RegistrationCallback,
                LinkedBlockingQueue<ImsRegistrationAttributes>> result_0 = null;
        Pair<RegistrationManager.RegistrationCallback,
                LinkedBlockingQueue<ImsRegistrationAttributes>> result_1 = null;

        try {
            // Ensure IMS for Sub 0 starts unregistered:
            result_0 = attachCarrierImsServiceAndSetUnregistered();

            // Ensure IMS for Sub 1 starts unregistered:
            result_1 = attachDeviceImsServiceAndSetUnregistered();

            verifyCellularSimultaneousCallingSupport(false, sSimultaneousCallingListener);
            // Register IMS via WWAN for both subs and then verify that DSDA is disabled:
            registerImsForBothSubsAndVerifyAttributes(IMS_REGI_TECH_LTE, IMS_REGI_TECH_LTE,
                    result_0.second, result_1.second);
            verifySimultaneousCallingRestrictions(false);
        } finally {
            if (result_0 != null) {
                unregisterImsCallback(result_0.first, sTestSubSlot0);
            }
            if (result_1 != null) {
                unregisterImsCallback(result_1.first, sTestSubSlot1);
            }
        }
    }

    /**
     * Test that when IMS is registered over WLAN, the framework marks simultaneous calling as
     * enabled.
     */
    @Test
    @Ignore("b/404456701")
    public void testImsRegisteredWLAN() throws Exception {
        Log.d(TAG, "testImsRegisteredWLAN");
        assumeTrue("Skip test: Not test on single SIM device", sIsMultiSimDevice);
        assumeTrue("Skip test: ImsService and/or FEATURE_TELEPHONY are not setup",
                ImsUtils.shouldTestImsService());

        Pair<RegistrationManager.RegistrationCallback,
                LinkedBlockingQueue<ImsRegistrationAttributes>> result_0 = null;
        Pair<RegistrationManager.RegistrationCallback,
                LinkedBlockingQueue<ImsRegistrationAttributes>> result_1 = null;

        try {
            // Ensure IMS for Sub 0 starts unregistered:
            result_0 = attachCarrierImsServiceAndSetUnregistered();

            // Ensure IMS for Sub 1 starts unregistered:
            result_1 = attachDeviceImsServiceAndSetUnregistered();

            verifyCellularSimultaneousCallingSupport(false,
                    sSimultaneousCallingListener);
            // Register IMS via WWAN for both subs:
            registerImsForBothSubsAndVerifyAttributes(IMS_REGI_TECH_IWLAN, IMS_REGI_TECH_IWLAN,
                    result_0.second, result_1.second);
            waitUntilPhAccountDsdaRestrictionsSetOrTimeout();

            // verify that DSDA is enabled via IMS even though it is disabled via cellular:
            verifySimultaneousCallingRestrictions(true);
        } finally {
            if (result_0 != null) {
                unregisterImsCallback(result_0.first, sTestSubSlot0);
            }
            if (result_1 != null) {
                unregisterImsCallback(result_1.first, sTestSubSlot1);
            }
        }
    }

    private static int waitForActiveSubIdOrTimeout(int phoneId) throws Exception {
        assertTrue("Timed out waiting for valid active subId. Current subId=["
                        + getActiveSubId(phoneId) + "] for slot=[" + phoneId + "].",
                ImsUtils.retryUntilTrue(() -> getActiveSubId(phoneId) >= 0, TEST_TIMEOUT_MS, 50));
        return getActiveSubId(phoneId);
    }

    private static void waitForSimStateReadyOrTimeout(int phoneId) throws Exception {
        assertTrue("Timed out waiting for SIM_STATE_READY. Current sim state=["
                        + sTelephonyManager.getSimState(phoneId) + "] for slot=[" + phoneId + "].",
                ImsUtils.retryUntilTrue(() -> (sTelephonyManager.getSimState(phoneId)
                        == TelephonyManager.SIM_STATE_READY), TEST_TIMEOUT_MS, 50));
    }

    private static void updateCallCapablePhAcctsAfterSubAdded(int subId, TelecomManager tm) {
        try {
            assertTrue("Timed out waiting for subId=[" + subId + "] to be added to "
                    + "sCallCapablePhoneAccounts.", ImsUtils.retryUntilTrue(() ->
                    updateCallCapablePhAcctsAndCheckForSubId(subId, tm), TEST_TIMEOUT_MS, 50));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean updateCallCapablePhAcctsAndCheckForSubId(int subId, TelecomManager tm) {
        sCallCapablePhoneAccounts = tm.getCallCapablePhoneAccounts();
        for (PhoneAccountHandle accountHandle : sCallCapablePhoneAccounts) {
            if (accountHandle.getId().equals(String.valueOf(subId))) {
                return true;
            }
        }
        return false;
    }

    private Pair<RegistrationManager.RegistrationCallback,
            LinkedBlockingQueue<ImsRegistrationAttributes>>
            attachCarrierImsServiceAndSetUnregistered() throws Exception {
        // Setup IMS Service:
        triggerFrameworkConnectToCarrierImsService(sServiceConnectorSlot0, TEST_SLOT_0);
        // Move IMS state to deregistered:
        sServiceConnectorSlot0.getCarrierService().getImsRegistration().onDeregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""));
        // Register IMS callbacks
        LinkedBlockingQueue<ImsRegistrationAttributes> mRegQueue =
                new LinkedBlockingQueue<>();
        LinkedBlockingQueue<ImsReasonInfo> mDeregQueue =
                new LinkedBlockingQueue<>();
        RegistrationManager.RegistrationCallback callback =
                createImsRegistrationCallback(mRegQueue, mDeregQueue);
        registerImsCallbackAndWaitForImsUnregister(sTestSubSlot0, callback, mDeregQueue);
        return new Pair<>(callback, mRegQueue);
    }

    private Pair<RegistrationManager.RegistrationCallback,
            LinkedBlockingQueue<ImsRegistrationAttributes>>
            attachDeviceImsServiceAndSetUnregistered() throws Exception {
        triggerFrameworkConnectToDeviceImsService(sServiceConnectorSlot1, TEST_SLOT_1);
        sServiceConnectorSlot1.getExternalService().onDeregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED,
                        ImsReasonInfo.CODE_UNSPECIFIED, ""));
        // Wait for IMS to be setup and unregistered for sServiceConnector_1:
        assertTrue("ImsService state is ready, but STATE_READY is not reported.",
                ImsUtils.retryUntilTrue(() -> (getFeatureState(sTestSubSlot1)
                        == ImsFeature.STATE_READY), TEST_TIMEOUT_MS, 50));
        LinkedBlockingQueue<ImsRegistrationAttributes> mRegQueue =
                new LinkedBlockingQueue<>();
        LinkedBlockingQueue<ImsReasonInfo> mDeregQueue =
                new LinkedBlockingQueue<>();
        RegistrationManager.RegistrationCallback callback =
                createImsRegistrationCallback(mRegQueue, mDeregQueue);
        registerImsCallbackAndWaitForImsUnregister(sTestSubSlot1, callback, mDeregQueue);
        return new Pair<>(callback, mRegQueue);
    }

    private static Integer getFeatureState(int testSub) throws Exception {
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        assertNotNull(imsManager);
        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(testSub);
        LinkedBlockingQueue<Integer> state = new LinkedBlockingQueue<>(1);
        ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(mmTelManager,
                (m) -> m.getFeatureState(Runnable::run, state::offer), ImsException.class);
        return state.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void registerImsForBothSubsAndVerifyAttributes(int regTechSlot0, int regTechSlot1,
            LinkedBlockingQueue<ImsRegistrationAttributes> regQueueSlot0,
            LinkedBlockingQueue<ImsRegistrationAttributes> regQueueSlot1) throws Exception {

        int expectedTransportType_0 = getExpectedTransportType(regTechSlot0);
        int expectedTransportType_1 = getExpectedTransportType(regTechSlot1);

        // IMS Registered for Sub 0:
        sServiceConnectorSlot0.getCarrierService().getImsRegistration().onRegistered(regTechSlot0);
        waitForAttributesAndVerify(regTechSlot0, regQueueSlot0, expectedTransportType_0, 0);

        // IMS Registered for Sub 1:
        sServiceConnectorSlot1.getExternalService().onRegistered(regTechSlot1);
        waitForAttributesAndVerify(regTechSlot1, regQueueSlot1, expectedTransportType_1, 0);
    }

    private int getExpectedTransportType(int imsRegTech) {
        int expectedTransportType = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
        switch (imsRegTech) {
            case IMS_REGI_TECH_IWLAN ->
                    expectedTransportType = AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
            case IMS_REGI_TECH_LTE ->
                    expectedTransportType = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
        }
        return expectedTransportType;
    }

    private void registerImsCallbackAndWaitForImsUnregister(int subId,
            RegistrationManager.RegistrationCallback callback,
            LinkedBlockingQueue<ImsReasonInfo> deRegQueue) throws Exception {
        registerImsRegistrationCallback(subId, callback);
        ImsReasonInfo deregResult = waitForResult(deRegQueue);
        assertNotNull(deregResult);
        assertEquals(ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED, deregResult.getCode());
    }

    /**
     * Due to race conditions between the subId getting set and the ImsService coming up,
     * registering callbacks can sometimes spuriously cause ImsExceptions.
     * Poll every second if this condition occurs for up to 5 seconds.
     */
    private void registerImsRegistrationCallback(int subId,
            RegistrationManager.RegistrationCallback callback) throws Exception {
        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        assertNotNull(imsManager);
        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(subId);
        try {
            sUiAutomation.adoptShellPermissionIdentity();
            assertTrue("Failed to register for IMS registration", ImsUtils.retryUntilTrue(() -> {
                boolean result;
                try {
                    mmTelManager.registerImsRegistrationCallback(getContext().getMainExecutor(),
                            callback);
                    result = true;
                } catch (ImsException e) {
                    result = false;
                    Log.w(TAG, "pollRegisterImsRegistrationCallback: failed to register:" + e);
                }
                return result;
            }, TEST_TIMEOUT_MS, 5));
        } finally {
            sUiAutomation.dropShellPermissionIdentity();
        }
    }

    private RegistrationManager.RegistrationCallback createImsRegistrationCallback(
            LinkedBlockingQueue<ImsRegistrationAttributes> regQueue,
            LinkedBlockingQueue<ImsReasonInfo> deRegQueue) {
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
                    @Override
                    public void onRegistered(ImsRegistrationAttributes attributes) {
                        regQueue.offer(attributes);
                    }

                    @Override
                    public void onRegistering(ImsRegistrationAttributes attributes) {
                        regQueue.offer(attributes);
                    }

                    @Override
                    public void onUnregistered(ImsReasonInfo info) {
                        deRegQueue.offer(info);
                    }
                };
        return callback;
    }

    private static SimultaneousCallingListener registerNewSimultaneousCallingListener() {
        // Configure and register a new SimultaneousCallingListener:
        SimultaneousCallingListener listener = new SimultaneousCallingListener();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                (tm) -> tm.registerTelephonyCallback(getContext().getMainExecutor(), listener),
                "android.permission.READ_PRIVILEGED_PHONE_STATE");
        return listener;
    }

    private void waitUntilPhAccountDsdaRestrictionsSetOrTimeout() throws Exception {
        assertTrue("Phone accounts simultaneous calling restrictions were not updated.",
                ImsUtils.retryUntilTrue(() -> isDsdaAccountRestrictionsSet(getDsdaPhoneAccounts()),
                        TEST_TIMEOUT_MS, 10));
    }

    /**
     * @return true when DSDA is enabled and the cached PSTN PhoneAccount simultaneous calling
     * restrictions contain each other's accont handles, false if DSDA is not enabled.
     */
    private boolean isDsdaAccountRestrictionsSet(Pair<PhoneAccount, PhoneAccount> accts) {
        return accts.first.hasSimultaneousCallingRestriction()
                && accts.second.hasSimultaneousCallingRestriction()
                && accts.first.getSimultaneousCallingRestriction()
                        .contains(accts.second.getAccountHandle())
                && accts.second.getSimultaneousCallingRestriction()
                        .contains(accts.first.getAccountHandle());
    }

    private Pair<PhoneAccount, PhoneAccount> getDsdaPhoneAccounts() {
        List<PhoneAccount> dsdaAccts = ShellIdentityUtils.invokeMethodWithShellPermissions(
                sTelecomManager, tm -> sCallCapablePhoneAccounts.stream()
                 .filter(handle -> handle.getId().equals(String.valueOf(sTestSubSlot0))
                         || handle.getId().equals(String.valueOf(sTestSubSlot1)))
                 .map(tm::getPhoneAccount)
                 .collect(Collectors.toList()));
        assertEquals("Unexpected number of DSDS accts:" + dsdaAccts, 2,
                dsdaAccts.size());
        return new Pair<>(dsdaAccts.get(0), dsdaAccts.get(1));
    }

    private void verifySimultaneousCallingRestrictions(boolean simultaneousCallingEnabled) {
        Pair<PhoneAccount, PhoneAccount> accts = getDsdaPhoneAccounts();
        if (simultaneousCallingEnabled) {
            // Check that the simultaneous calling restrictions were set for each phone account:
            assertTrue(accts.first.hasSimultaneousCallingRestriction());
            assertTrue(accts.second.hasSimultaneousCallingRestriction());
            assertEquals(1, accts.first.getSimultaneousCallingRestriction().size());
            assertEquals(1, accts.second.getSimultaneousCallingRestriction().size());
            Assert.assertTrue(accts.first.getSimultaneousCallingRestriction().contains(
                    accts.second.getAccountHandle()));
            Assert.assertTrue(accts.second.getSimultaneousCallingRestriction().contains(
                    accts.first.getAccountHandle()));
        } else {
            // Check that simultaneous calling is disabled for both phone accounts:
            assertTrue(accts.first.hasSimultaneousCallingRestriction());
            assertTrue(accts.second.hasSimultaneousCallingRestriction());
            assertEquals(0, accts.first.getSimultaneousCallingRestriction().size());
            assertEquals(0, accts.second.getSimultaneousCallingRestriction().size());
        }
    }

    private void verifyCellularSimultaneousCallingSupport(
            boolean cellularSimultaneousCallingSupported,
            SimultaneousCallingListener listener) throws Exception {
        if (cellularSimultaneousCallingSupported) {
            // Check that the expected cellular supported slots have been reported by the modem:
            Set<Integer> expectedSimultaneousCallingSubIds = new HashSet<>();
            expectedSimultaneousCallingSubIds.add(sTestSubSlot0);
            expectedSimultaneousCallingSubIds.add(sTestSubSlot1);
            assertTrue("Never received cellular simultaneous calling subId update",
                    ImsUtils.retryUntilTrue(() -> expectedSimultaneousCallingSubIds.equals(
                    listener.getSimultaneousCallingSubIds()), TEST_TIMEOUT_MS, 5));
        } else {
            assertTrue("Unexpected simultaneous calling subIds reported ",
                    ImsUtils.retryUntilTrue(() -> listener.getSimultaneousCallingSubIds().isEmpty(),
                            TEST_TIMEOUT_MS, 5));
            // Check that the modem reported no sub IDs support cellular simultaneous calling:
            assertEquals(0, listener.getSimultaneousCallingSubIds().size());
        }
    }

    private static boolean isMockModemAllowed() {
        // Always allow for debug builds
        if (DEBUG) return true;
        boolean isAllowed = SystemProperties.getBoolean(ALLOW_MOCK_MODEM_PROPERTY, false);
        boolean isAllowedForBoot =
                SystemProperties.getBoolean(BOOT_ALLOW_MOCK_MODEM_PROPERTY, false);
        // Check for developer settings for user build.
        return isAllowed || isAllowedForBoot;
    }

    private void unregisterImsCallback(RegistrationManager.RegistrationCallback callback,
            int testSub) {
        try {
            sUiAutomation.adoptShellPermissionIdentity();
            ImsManager imsManager = getContext().getSystemService(ImsManager.class);
            assertNotNull(imsManager);
            ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(testSub);
            mmTelManager.unregisterImsRegistrationCallback(callback);
        } finally {
            sUiAutomation.dropShellPermissionIdentity();
        }
    }

    private static int getActiveSubId(int phoneId) {
        int[] allSubs;
        try {
            sUiAutomation.adoptShellPermissionIdentity(
                    "android.permission.READ_PRIVILEGED_PHONE_STATE");
            allSubs = getContext().getSystemService(SubscriptionManager.class)
                    .getActiveSubscriptionIdList();
        } finally {
            sUiAutomation.dropShellPermissionIdentity();
        }
        assertNotNull("Couldn't resolve subIds", allSubs);
        int subsLength = allSubs.length;
        return (phoneId < subsLength) ? allSubs[phoneId] : -1;
    }

    private void setSimultaneousCallingEnabledLogicalSlots(int[] enabledLogicalSlots)
            throws Exception {
        sMockModemManager.setSimulCallingEnabledLogicalSlots(TEST_SLOT_0, enabledLogicalSlots);
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    private static boolean isMultiSim(TelephonyManager tm) {
        return tm != null && tm.getActiveModemCount() > 1;
    }

    private <T> T waitForResult(LinkedBlockingQueue<T> queue) throws Exception {
        return queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void triggerFrameworkConnectToCarrierImsService(ImsServiceConnector serviceConnector,
            int slotId) throws Exception {
        Log.i(TAG, "triggerFrameworkConnectToCarrierImsService: slotId = " + slotId);

        // Add the simultaneous calling capability to the ImsService.
        assertTrue(serviceConnector.connectCarrierImsServiceLocally());
        serviceConnector.getCarrierService().addCapabilities(
                ImsService.CAPABILITY_SUPPORTS_SIMULTANEOUS_CALLING);

        // Connect to the ImsService with the MmTel feature.
        assertTrue(serviceConnector.triggerFrameworkConnectionToCarrierImsService(
                new ImsFeatureConfiguration.Builder()
                        .addFeature(slotId, ImsFeature.FEATURE_MMTEL)
                        .build()));
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        assertTrue("Did not receive createMmTelFeature", serviceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_CREATE_MMTEL));
        assertTrue("Did not receive MmTelFeature#onReady", serviceConnector.getCarrierService()
                .waitForLatchCountdown(TestImsService.LATCH_MMTEL_READY));
        assertNotNull("ImsService created, but ImsService#createMmTelFeature was not called!",
                serviceConnector.getCarrierService().getMmTelFeature());
        int serviceSlot = serviceConnector.getCarrierService().getMmTelFeature().getSlotIndex();
        assertEquals("The slot specified for the test (" + slotId + ") does not match the "
                        + "assigned slot (" + serviceSlot + "+ for the associated MmTelFeature",
                slotId, serviceSlot);
    }

    private void triggerFrameworkConnectToDeviceImsService(ImsServiceConnector serviceConnector,
            int slotId) throws Exception {
        Log.i(TAG, "triggerFrameworkConnectToDeviceImsService: slotId = " + slotId);

        // Connect to Device the ImsService with the MmTel feature and simultaneous call cap.
        assertTrue(serviceConnector.connectDeviceImsService(
                ImsService.CAPABILITY_SUPPORTS_SIMULTANEOUS_CALLING,
                new ImsFeatureConfiguration.Builder()
                .addFeature(slotId, ImsFeature.FEATURE_MMTEL)
                .build()));
        //First MMTEL feature is created on device ImsService.
        assertTrue(serviceConnector.getExternalService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL));
        assertTrue("Device ImsService created, but TestDeviceImsService#createMmTelFeature was "
                + "not called!", serviceConnector.getExternalService().isMmTelFeatureCreated());
    }

    private void waitForAttributesAndVerify(int tech, LinkedBlockingQueue<ImsRegistrationAttributes>
            attrQueue, int expectedTransport, int expectedAttrFlags) throws Exception {
        ImsRegistrationAttributes attrResult = waitForResult(attrQueue);
        assertNotNull(attrResult);
        assertEquals(tech, attrResult.getRegistrationTechnology());
        assertEquals(expectedTransport, attrResult.getTransportType());
        assertEquals(expectedAttrFlags, attrResult.getAttributeFlags());
    }
}
