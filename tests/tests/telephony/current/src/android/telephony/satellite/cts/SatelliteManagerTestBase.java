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

import static android.telephony.satellite.SatelliteManager.DATAGRAM_TYPE_UNKNOWN;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.SatelliteReceiver;
import android.telephony.cts.TelephonyManagerTest.ServiceStateRadioStateListener;
import android.telephony.satellite.EarfcnRange;
import android.telephony.satellite.EnableRequestAttributes;
import android.telephony.satellite.NtnSignalStrength;
import android.telephony.satellite.NtnSignalStrengthCallback;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteAccessConfiguration;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteCapabilitiesCallback;
import android.telephony.satellite.SatelliteCommunicationAccessStateCallback;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteDatagramCallback;
import android.telephony.satellite.SatelliteDisallowedReasonsCallback;
import android.telephony.satellite.SatelliteInfo;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.SatelliteModemStateCallback;
import android.telephony.satellite.SatellitePosition;
import android.telephony.satellite.SatelliteProvisionStateCallback;
import android.telephony.satellite.SatelliteSubscriberInfo;
import android.telephony.satellite.SatelliteSubscriberProvisionStatus;
import android.telephony.satellite.SatelliteTransmissionUpdateCallback;
import android.telephony.satellite.SelectedNbIotSatelliteSubscriptionCallback;
import android.telephony.satellite.SystemSelectionSpecifier;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.uwb.UwbManager;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SatelliteManagerTestBase {
    protected static String TAG = "SatelliteManagerTestBase";

    protected static final String TOKEN = "TEST_TOKEN";
    protected static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    public static final int RADIO_HAL_VERSION_2_3 = makeRadioVersion(2, 3);

    /**
     * Since SST sets waiting time up to 10 seconds for the power off radio, the timer waiting for
     * radio power state change should be greater than 10 seconds.
     */
    protected static final long EXTERNAL_DEPENDENT_TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    protected static SatelliteManager sSatelliteManager;
    protected static TelephonyManager sTelephonyManager = null;

    protected UwbManager mUwbManager = null;
    protected NfcAdapter mNfcAdapter = null;
    protected BluetoothAdapter mBluetoothAdapter = null;
    protected WifiManager mWifiManager = null;

    protected static void beforeAllTestsBase() {
        sSatelliteManager = getContext().getSystemService(SatelliteManager.class);
        sTelephonyManager = getContext().getSystemService(TelephonyManager.class);
        turnRadioOn();
    }

    protected static void afterAllTestsBase() {
        sSatelliteManager = null;
        sTelephonyManager = null;
    }

    protected static boolean shouldTestSatellite() {
        if (!getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SATELLITE)) {
            logd("Skipping tests because FEATURE_TELEPHONY_SATELLITE is not available");
            return false;
        }
        try {
            getContext().getSystemService(TelephonyManager.class)
                    .getHalVersion(TelephonyManager.HAL_SERVICE_RADIO);
        } catch (IllegalStateException e) {
            logd("Skipping tests because Telephony service is null, exception=" + e);
            return false;
        }
        return true;
    }

    protected static boolean shouldTestSatelliteWithMockService() {
        if (!getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY)) {
            logd("Skipping tests because FEATURE_TELEPHONY is not available");
            return false;
        }
        if (!getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SATELLITE)) {
            // Satellite test against mock service should pass on satellite-less devices, but it's
            // still too flaky.
            logd("Skipping tests because FEATURE_TELEPHONY_SATELLITE is not available");
            return false;
        }
        try {
            getContext().getSystemService(TelephonyManager.class)
                    .getHalVersion(TelephonyManager.HAL_SERVICE_RADIO);
        } catch (IllegalStateException e) {
            logd("Skipping tests because Telephony service is null, exception=" + e);
            return false;
        }
        return true;
    }

    protected static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    protected static void grantSatellitePermission() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION);
    }

    protected static void revokeSatellitePermission() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    protected static void grantSatelliteAndReadBasicPhoneStatePermissions() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION,
                        Manifest.permission.READ_BASIC_PHONE_STATE);
    }

    protected static class SatelliteTransmissionUpdateCallbackTest implements
            SatelliteTransmissionUpdateCallback {

        protected static final class DatagramStateChangeArgument {
            protected int state;
            protected int pendingCount;
            protected int errorCode;
            // Sending datagram type, should not be used as a comparison for the equals().
            // Because receiving case there is no datagram type.
            protected int datagramType;

            DatagramStateChangeArgument(int state, int pendingCount, int errorCode) {
                this.state = state;
                this.pendingCount = pendingCount;
                this.errorCode = errorCode;
                this.datagramType = DATAGRAM_TYPE_UNKNOWN;
            }

            DatagramStateChangeArgument(int datagramType, int state, int pendingCount, int errorCode) {
                this.datagramType = datagramType;
                this.state = state;
                this.pendingCount = pendingCount;
                this.errorCode = errorCode;
            }

            @Override
            public boolean equals(Object other) {
                if (this == other) return true;
                if (other == null || getClass() != other.getClass()) return false;
                DatagramStateChangeArgument that = (DatagramStateChangeArgument) other;
                return state == that.state
                        && pendingCount  == that.pendingCount
                        && errorCode == that.errorCode;
            }

            @Override
            public String toString() {
                return ("state: " + state + " pendingCount: " + pendingCount
                        + " errorCode: " + errorCode);
            }
        }

        public PointingInfo mPointingInfo;
        private final Semaphore mPositionChangeSemaphore = new Semaphore(0);
        private List<DatagramStateChangeArgument> mSendDatagramStateChanges = new ArrayList<>();
        private final Object mSendDatagramStateChangesLock = new Object();
        private final Semaphore mSendSemaphore = new Semaphore(0);
        private List<DatagramStateChangeArgument> mReceiveDatagramStateChanges = new ArrayList<>();
        private final Object mReceiveDatagramStateChangesLock = new Object();
        private final Semaphore mReceiveSemaphore = new Semaphore(0);
        private final Object mSendDatagramRequestedLock = new Object();
        private final Semaphore mSendDatagramRequestedSemaphore = new Semaphore(0);
        @SatelliteManager.DatagramType
        private List<Integer> mSendDatagramRequestedList = new ArrayList<>();

        @Override
        public void onSatellitePositionChanged(PointingInfo pointingInfo) {
            logd("onSatellitePositionChanged: pointingInfo=" + pointingInfo);
            mPointingInfo = pointingInfo;

            try {
                mPositionChangeSemaphore.release();
            } catch (Exception e) {
                loge("onSatellitePositionChanged: Got exception, ex=" + e);
            }
        }

        @Override
        public void onSendDatagramStateChanged(int state, int sendPendingCount, int errorCode) {
            logd("onSendDatagramStateChanged: state=" + state + ", sendPendingCount="
                    + sendPendingCount + ", errorCode=" + errorCode);
            // Implementation moved to onSendDatagramStateChanged(int, int, int, int)
            // This callback is called only for backward compatibility after calling
            // onSendDatagramStateChanged(int, int, int, int). This call flows is guaranteed by
            // SatelliteManager#startTransmissionUpdates().
        }

        @Override
        public void onSendDatagramStateChanged(
                int datagramType, int state, int sendPendingCount, int errorCode) {
            logd("onSendDatagramStateChanged:datagramType=" + datagramType + ", state=" + state
                    + ", sendPendingCount=" + sendPendingCount + ", errorCode=" + errorCode);
            synchronized (mSendDatagramStateChangesLock) {
                mSendDatagramStateChanges.add(new DatagramStateChangeArgument(datagramType,
                        state, sendPendingCount, errorCode));
            }

            try {
                mSendSemaphore.release();
            } catch (Exception e) {
                loge("onSendDatagramStateChanged: Got exception, ex=" + e);
            }
        }

        @Override
        public void onReceiveDatagramStateChanged(
                int state, int receivePendingCount, int errorCode) {
            logd("onReceiveDatagramStateChanged: state=" + state + ", "
                    + "receivePendingCount=" + receivePendingCount + ", errorCode=" + errorCode);
            synchronized (mReceiveDatagramStateChangesLock) {
                mReceiveDatagramStateChanges.add(new DatagramStateChangeArgument(state,
                        receivePendingCount, errorCode));
            }

            try {
                mReceiveSemaphore.release();
            } catch (Exception e) {
                loge("onReceiveDatagramStateChanged: Got exception, ex=" + e);
            }
        }

        @Override
        public void onSendDatagramRequested(int datagramType) {
            logd("onSendDatagramRequested: datagramType=" + datagramType);
            synchronized (mSendDatagramRequestedLock) {
                mSendDatagramRequestedList.add(datagramType);
            }

            try {
                mSendDatagramRequestedSemaphore.release();
            } catch (Exception e) {
                loge("onSendDatagramRequested: Got exception, ex=" + e);
            }
        }

        public boolean waitUntilOnSatellitePositionChanged(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mPositionChangeSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatellitePositionChanged() callback");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("SatelliteTransmissionUpdateCallback "
                            + "waitUntilOnSatellitePositionChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public boolean waitUntilOnSendDatagramStateChanged(int expectedNumberOfEvents) {
            logd(
                    "waitUntilOnSendDatagramStateChanged expectedNumberOfEvents:"
                            + expectedNumberOfEvents);
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSendSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSendDatagramStateChanged() callback");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("SatelliteTransmissionUpdateCallback "
                            + "waitUntilOnSendDatagramStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public boolean waitUntilOnReceiveDatagramStateChanged(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mReceiveSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onReceiveDatagramStateChanged()");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("SatelliteTransmissionUpdateCallback "
                            + "waitUntilOnReceiveDatagramStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public boolean waitUntilOnSendDatagramRequested(int expectedNumberOfEvents) {
            logd("waitUntilOnSendDatagramRequested expectedNumberOfEvents:"
                    + expectedNumberOfEvents);
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSendDatagramRequestedSemaphore.tryAcquire(
                            TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSendDatagramRequested() callback");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("SatelliteTransmissionUpdateCallback "
                            + "waitUntilOnSendDatagramRequested: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void clearPointingInfo() {
            mPointingInfo = null;
            mPositionChangeSemaphore.drainPermits();
        }

        public void clearSendDatagramStateChanges() {
            synchronized (mSendDatagramStateChangesLock) {
                logd("clearSendDatagramStateChanges");
                mSendDatagramStateChanges.clear();
                mSendSemaphore.drainPermits();
            }
        }

        public void clearReceiveDatagramStateChanges() {
            synchronized (mReceiveDatagramStateChangesLock) {
                logd("clearReceiveDatagramStateChanges");
                mReceiveDatagramStateChanges.clear();
                mReceiveSemaphore.drainPermits();
            }
        }

        public void clearSendDatagramRequested() {
            synchronized (mSendDatagramRequestedLock) {
                logd("clearSendDatagramRequested");
                mSendDatagramRequestedList.clear();
                mSendDatagramRequestedSemaphore.drainPermits();
            }
        }

        @Nullable
        public DatagramStateChangeArgument getSendDatagramStateChange(int index) {
            synchronized (mSendDatagramStateChangesLock) {
                if (index < mSendDatagramStateChanges.size()) {
                    return mSendDatagramStateChanges.get(index);
                } else {
                    Log.e(TAG, "getSendDatagramStateChange: invalid index= " + index
                            + " mSendDatagramStateChanges.size= "
                            + mSendDatagramStateChanges.size());
                    return null;
                }
            }
        }

        @Nullable
        public DatagramStateChangeArgument getReceiveDatagramStateChange(int index) {
            synchronized (mReceiveDatagramStateChangesLock) {
                if (index < mReceiveDatagramStateChanges.size()) {
                    return mReceiveDatagramStateChanges.get(index);
                } else {
                    Log.e(TAG, "getReceiveDatagramStateChange: invalid index= " + index
                            + " mReceiveDatagramStateChanges.size= "
                            + mReceiveDatagramStateChanges.size());
                    return null;
                }
            }
        }

        public int getNumOfSendDatagramStateChanges() {
            synchronized (mSendDatagramStateChangesLock) {
                logd("getNumOfSendDatagramStateChanges size:" + mSendDatagramStateChanges.size());
                return mSendDatagramStateChanges.size();
            }
        }

        public int getNumOfReceiveDatagramStateChanges() {
            synchronized (mReceiveDatagramStateChangesLock) {
                return mReceiveDatagramStateChanges.size();
            }
        }

        @SatelliteManager.DatagramType
        public int getSendDatagramRequestedType(int index) {
            synchronized (mSendDatagramRequestedLock) {
                if (index < mSendDatagramRequestedList.size()) {
                    return mSendDatagramRequestedList.get(index);
                } else {
                    Log.e(TAG, "getSendDatagramRequestedType: invalid index= " + index
                            + " mSendDatagramRequestedList.size= "
                            + mSendDatagramRequestedList.size());
                    return DATAGRAM_TYPE_UNKNOWN;
                }
            }
        }

        public int getNumOfSendDatagramRequestedChanges() {
            synchronized (mSendDatagramRequestedLock) {
                return mSendDatagramRequestedList.size();
            }
        }
    }

    protected static class SatelliteProvisionStateCallbackTest implements
            SatelliteProvisionStateCallback {
        public boolean isProvisioned = false;
        private List<Boolean> mProvisionedStates = new ArrayList<>();
        private final Object mProvisionedStatesLock = new Object();
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteProvisionStateChanged(boolean provisioned) {
            logd("onSatelliteProvisionStateChanged: provisioned=" + provisioned);
            isProvisioned = provisioned;
            synchronized (mProvisionedStatesLock) {
                mProvisionedStates.add(provisioned);
            }
            try {
                mSemaphore.release();
            } catch (Exception ex) {
                loge("onSatelliteProvisionStateChanged: Got exception, ex=" + ex);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatelliteProvisionStateChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onSatelliteProvisionStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void clearProvisionedStates() {
            synchronized (mProvisionedStatesLock) {
                mProvisionedStates.clear();
                mSemaphore.drainPermits();
            }
        }

        public int getTotalCountOfProvisionedStates() {
            synchronized (mProvisionedStatesLock) {
                return mProvisionedStates.size();
            }
        }

        public boolean getProvisionedState(int index) {
            synchronized (mProvisionedStatesLock) {
                if (index < mProvisionedStates.size()) {
                    return mProvisionedStates.get(index);
                }
            }
            loge("getProvisionedState: invalid index=" + index);
            return false;
        }
    }

    protected static class SatelliteSubscriptionProvisionStateChangedTest implements
            SatelliteProvisionStateCallback {
        private List<SatelliteSubscriberProvisionStatus> mResultList = new ArrayList<>();
        private final Object mProvisionedStatesLock = new Object();
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteProvisionStateChanged(boolean provisioned) {
            logd("onSatelliteProvisionStateChanged: provisioned=" + provisioned);
        }

        @Override
        public void onSatelliteSubscriptionProvisionStateChanged(
                List<SatelliteSubscriberProvisionStatus> list) {
            logd("onSatelliteSubscriptionProvisionStateChanged:" + list);
            synchronized (mProvisionedStatesLock) {
                mResultList = list;
            }
            try {
                mSemaphore.release();
            } catch (Exception ex) {
                loge("onSatelliteSubscriptionProvisionStateChanged: Got exception, ex=" + ex);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatelliteSubscriptionProvisionStateChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onSatelliteSubscriptionProvisionStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void clearProvisionedStates() {
            synchronized (mProvisionedStatesLock) {
                mResultList.clear();
                mSemaphore.drainPermits();
            }
        }

        public List<SatelliteSubscriberProvisionStatus> getResultList() {
            synchronized (mProvisionedStatesLock) {
                return mResultList;
            }
        }
    }

    protected static class SatelliteModemStateCallbackTest implements SatelliteModemStateCallback {
        public int modemState = SatelliteManager.SATELLITE_MODEM_STATE_OFF;
        private List<Integer> mModemStates = new ArrayList<>();
        private final Object mModemStatesLock = new Object();
        private final Semaphore mSemaphore = new Semaphore(0);
        private final Semaphore mModemOffSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteModemStateChanged(int state) {
            Log.d(TAG, "onSatelliteModemStateChanged: state=" + state);
            modemState = state;
            synchronized (mModemStatesLock) {
                mModemStates.add(state);
            }
            try {
                mSemaphore.release();
            } catch (Exception ex) {
                Log.e(TAG, "onSatelliteModemStateChanged: Got exception, ex=" + ex);
            }

            if (state == SatelliteManager.SATELLITE_MODEM_STATE_OFF) {
                try {
                    mModemOffSemaphore.release();
                } catch (Exception ex) {
                    Log.e(TAG, "onSatelliteModemStateChanged: Got exception in "
                            + "releasing mModemOffSemaphore, ex=" + ex);
                }
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        Log.e(TAG, "Timeout to receive onSatelliteModemStateChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "onSatelliteModemStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public boolean waitUntilModemOff() {
            try {
                if (!mModemOffSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout to receive satellite modem off event");
                    return false;
                }
            } catch (Exception ex) {
                Log.e(TAG, "Waiting for satellite modem off event: Got exception=" + ex);
                return false;
            }
            return true;
        }

        public boolean waitUntilModemOff(long timeoutMillis) {
            try {
                if (!mModemOffSemaphore.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout to receive satellite modem off event");
                    return false;
                }
            } catch (Exception ex) {
                Log.e(TAG, "Waiting for satellite modem off event: Got exception=" + ex);
                return false;
            }
            return true;
        }

        public void clearModemStates() {
            synchronized (mModemStatesLock) {
                Log.d(TAG, "onSatelliteModemStateChanged: clearModemStates");
                mModemStates.clear();
                mSemaphore.drainPermits();
                mModemOffSemaphore.drainPermits();
            }
        }

        public int getModemState(int index) {
            synchronized (mModemStatesLock) {
                if (index < mModemStates.size()) {
                    return mModemStates.get(index);
                } else {
                    Log.e(TAG, "getModemState: invalid index=" + index
                            + ", mModemStates.size=" + mModemStates.size());
                    return -1;
                }
            }
        }

        public int getTotalCountOfModemStates() {
            synchronized (mModemStatesLock) {
                return mModemStates.size();
            }
        }
    }

    protected static class SatelliteDatagramCallbackTest implements SatelliteDatagramCallback {
        public SatelliteDatagram mDatagram;
        public final List<SatelliteDatagram> mDatagramList = new ArrayList<>();
        public long mDatagramId;
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteDatagramReceived(long datagramId, SatelliteDatagram datagram,
                int pendingCount, Consumer<Void> callback) {
            logd("onSatelliteDatagramReceived: datagramId=" + datagramId + ", datagram="
                    + datagram + ", pendingCount=" + pendingCount);
            mDatagram = datagram;
            mDatagramList.add(datagram);
            mDatagramId = datagramId;
            if (callback != null) {
                logd("onSatelliteDatagramReceived: callback.accept() datagramId=" + datagramId);
                callback.accept(null);
            } else {
                logd("onSatelliteDatagramReceived: callback is null datagramId=" + datagramId);
            }

            try {
                mSemaphore.release();
            } catch (Exception e) {
                loge("onSatelliteDatagramReceived: Got exception, ex=" + e);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatelliteDatagramReceived");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onSatelliteDatagramReceived: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }
    }

    protected static class NtnSignalStrengthCallbackTest implements NtnSignalStrengthCallback {
        public NtnSignalStrength mNtnSignalStrength;
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onNtnSignalStrengthChanged(@NonNull NtnSignalStrength ntnSignalStrength) {
            logd("onNtnSignalStrengthChanged: ntnSignalStrength=" + ntnSignalStrength);
            mNtnSignalStrength = new NtnSignalStrength(ntnSignalStrength);

            try {
                mSemaphore.release();
            } catch (Exception e) {
                loge("onNtnSignalStrengthChanged: Got exception, ex=" + e);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onNtnSignalStrengthChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onNtnSignalStrengthChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void drainPermits() {
            mSemaphore.drainPermits();
        }
    }

    protected static class SatelliteCapabilitiesCallbackTest implements
            SatelliteCapabilitiesCallback {
        public SatelliteCapabilities mSatelliteCapabilities;
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteCapabilitiesChanged(
                @NonNull SatelliteCapabilities satelliteCapabilities) {
            logd("onSatelliteCapabilitiesChanged: satelliteCapabilities=" + satelliteCapabilities);
            mSatelliteCapabilities = satelliteCapabilities;

            try {
                mSemaphore.release();
            } catch (Exception e) {
                loge("onSatelliteCapabilitiesChanged: Got exception, ex=" + e);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatelliteCapabilitiesChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onSatelliteCapabilitiesChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }
    }

    protected static class SelectedNbIotSatelliteSubscriptionCallbackTest implements
            SelectedNbIotSatelliteSubscriptionCallback {
        public int mSelectedSubId;
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSelectedNbIotSatelliteSubscriptionChanged(int selectedSubId) {
            logd("onSelectedNbIotSatelliteSubscriptionChanged: selectedSubId=" + selectedSubId);
            mSelectedSubId = selectedSubId;

            try {
                mSemaphore.release();
            } catch (Exception e) {
                loge("onSelectedNbIotSatelliteSubscriptionChanged: Got exception, ex=" + e);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSelectedNbIotSatelliteSubscriptionChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onSelectedNbIotSatelliteSubscriptionChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void drainPermits() {
            mSemaphore.drainPermits();
        }
    }

    protected static class SatelliteSupportedStateCallbackTest implements Consumer<Boolean> {
        public boolean isSupported = false;
        private List<Boolean> mSupportedStates = new ArrayList<>();
        private final Object mSupportedStatesLock = new Object();
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void accept(Boolean supported) {
            logd("onSatelliteSupportedStateChanged: supported=" + supported);
            isSupported = supported;
            synchronized (mSupportedStatesLock) {
                mSupportedStates.add(supported);
            }
            try {
                mSemaphore.release();
            } catch (Exception ex) {
                loge("onSatelliteSupportedStateChanged: Got exception, ex=" + ex);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatelliteSupportedStateChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onSatelliteSupportedStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void clearSupportedStates() {
            synchronized (mSupportedStatesLock) {
                mSupportedStates.clear();
                mSemaphore.drainPermits();
            }
        }

        public int getTotalCountOfSupportedStates() {
            synchronized (mSupportedStatesLock) {
                return mSupportedStates.size();
            }
        }

        public Boolean getSupportedState(int index) {
            synchronized (mSupportedStatesLock) {
                if (index < mSupportedStates.size()) {
                    return mSupportedStates.get(index);
                }
            }
            loge("getSupportedState: invalid index=" + index);
            return null;
        }
    }

    protected static class SatelliteCommunicationAccessStateCallbackTest
            implements SatelliteCommunicationAccessStateCallback {
        public boolean isAllowed = false;
        @Nullable
        private SatelliteAccessConfiguration mSatelliteAccessConfiguration;
        private final Semaphore mSemaphore = new Semaphore(0);
        private final Semaphore mSatelliteAccessConfigurationChangedSemaphore = new Semaphore(0);

        @Override
        public void onAccessAllowedStateChanged(boolean allowed) {
            logd("onAccessAllowedStateChanged: isAllowed=" + allowed);
            isAllowed = allowed;

            try {
                mSemaphore.release();
            } catch (Exception e) {
                loge("onAccessAllowedStateChanged: Got exception, ex=" + e);
            }
        }

        @Override
        public void onAccessConfigurationChanged(
                @Nullable SatelliteAccessConfiguration satelliteAccessConfiguration) {
            logd(
                    "onAccessConfigurationChanged: satelliteAccessConfiguration="
                            + satelliteAccessConfiguration);
            mSatelliteAccessConfiguration = satelliteAccessConfiguration;

            try {
                mSatelliteAccessConfigurationChangedSemaphore.release();
            } catch (Exception e) {
                loge("onAccessConfigurationChanged: Got exception, ex=" + e);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onAccessAllowedStateChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onAccessAllowedStateChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public boolean waitUntilSatelliteAccessConfigurationChangedEvent(
                int expectedNumberOfEvents, long timeOutMilliSec) {
            logd("waitUntilSatelliteAccessConfigurationChangedEvent");
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSatelliteAccessConfigurationChangedSemaphore.tryAcquire(timeOutMilliSec,
                            TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive "
                                + "waitUntilSatelliteAccessConfigurationChangedEvent");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("waitUntilSatelliteAccessConfigurationChangedEvent: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void drainPermits() {
            mSemaphore.drainPermits();
            mSatelliteAccessConfigurationChangedSemaphore.drainPermits();
        }

        @Nullable
        public SatelliteAccessConfiguration getSatelliteAccessConfiguration() {
            return mSatelliteAccessConfiguration;
        }
    }

    protected static class SatelliteDisallowedReasonsCallbackTest
            implements SatelliteDisallowedReasonsCallback {
        private int[] mSatelliteDisabledReasons;
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteDisallowedReasonsChanged(@NonNull int[] disallowedReasons) {
            logd("onSatelliteDisallowedReasonsChanged: disallowedReasons="
                     + Arrays.toString(disallowedReasons));
            mSatelliteDisabledReasons = disallowedReasons;
            try {
                mSemaphore.release();
            } catch (Exception e) {
                loge("onSatelliteDisallowedReasonsChanged: Got exception, ex=" + e);
            }
        }

        public boolean waitUntilResult(int expectedNumberOfEvents) {
            for (int i = 0; i < expectedNumberOfEvents; i++) {
                try {
                    if (!mSemaphore.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        loge("Timeout to receive onSatelliteDisallowedReasonsChanged");
                        return false;
                    }
                } catch (Exception ex) {
                    loge("onSatelliteDisallowedReasonsChanged: Got exception=" + ex);
                    return false;
                }
            }
            return true;
        }

        public void drainPermits() {
            mSemaphore.drainPermits();
        }

        public boolean hasSatelliteDisabledReason(int reason) {
            return Arrays.stream(mSatelliteDisabledReasons).anyMatch(i -> i == reason);
        }
    }

    protected static class SatelliteModeRadiosUpdater extends ContentObserver implements
            AutoCloseable {
        private final Context mContext;
        private final Semaphore mSemaphore = new Semaphore(0);
        private String mExpectedSatelliteModeRadios = "";
        private final Object mLock = new Object();

        public SatelliteModeRadiosUpdater(Context context) {
            super(new Handler(Looper.getMainLooper()));
            mContext = context;
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.SATELLITE_MODE_RADIOS), false, this);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION,
                            Manifest.permission.WRITE_SECURE_SETTINGS,
                            Manifest.permission.NETWORK_SETTINGS,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                            Manifest.permission.UWB_PRIVILEGED);
        }

        @Override
        public void onChange(boolean selfChange) {
            String newSatelliteModeRadios = Settings.Global.getString(
                    mContext.getContentResolver(), Settings.Global.SATELLITE_MODE_RADIOS);
            synchronized (mLock) {
                if (TextUtils.equals(mExpectedSatelliteModeRadios, newSatelliteModeRadios)) {
                    logd("SatelliteModeRadiosUpdater: onChange, newSatelliteModeRadios="
                            + newSatelliteModeRadios);
                    try {
                        mSemaphore.release();
                    } catch (Exception ex) {
                        loge("SatelliteModeRadiosUpdater: onChange, ex=" + ex);
                    }
                }
            }
        }

        @Override
        public void close() throws Exception {
            mContext.getContentResolver().unregisterContentObserver(this);
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        public boolean setSatelliteModeRadios(String expectedSatelliteModeRadios) {
            logd("setSatelliteModeRadios: expectedSatelliteModeRadios="
                    + expectedSatelliteModeRadios);
            String originalSatelliteModeRadios =  Settings.Global.getString(
                    mContext.getContentResolver(), Settings.Global.SATELLITE_MODE_RADIOS);
            if (TextUtils.equals(expectedSatelliteModeRadios, originalSatelliteModeRadios)) {
                logd("setSatelliteModeRadios: satellite radios mode is already as expected");
                return true;
            }

            setExpectedSatelliteModeRadios(expectedSatelliteModeRadios);
            clearSemaphorePermits();
            Settings.Global.putString(mContext.getContentResolver(),
                    Settings.Global.SATELLITE_MODE_RADIOS, expectedSatelliteModeRadios);
            return waitForModeChanged();
        }

        private void clearSemaphorePermits() {
            mSemaphore.drainPermits();
        }

        private boolean waitForModeChanged() {
            logd("SatelliteModeRadiosUpdater: waitForModeChanged start");
            try {
                if (!mSemaphore.tryAcquire(EXTERNAL_DEPENDENT_TIMEOUT, TimeUnit.MILLISECONDS)) {
                    loge("SatelliteModeRadiosUpdater: Timeout to wait for mode changed");
                    return false;
                }
            } catch (InterruptedException e) {
                loge("SatelliteModeRadiosUpdater: waitForModeChanged, e=" + e);
                return false;
            }
            return true;
        }

        private void setExpectedSatelliteModeRadios(String expectedSatelliteModeRadios) {
            synchronized (mLock) {
                mExpectedSatelliteModeRadios = expectedSatelliteModeRadios;
            }
            logd("SatelliteModeRadiosUpdater: mExpectedSatelliteModeRadios="
                    + mExpectedSatelliteModeRadios);
        }
    }

    protected static boolean provisionSatellite() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        String mText = "This is test provision data.";
        byte[] testProvisionData = mText.getBytes();

        sSatelliteManager.provisionService(
                TOKEN, testProvisionData, null, getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            loge("provisionSatellite ex=" + ex);
            return false;
        }
        if (errorCode == null || errorCode != SatelliteManager.SATELLITE_RESULT_SUCCESS) {
            loge("provisionSatellite failed with errorCode=" + errorCode);
            return false;
        }
        return true;
    }

    protected static boolean deprovisionSatellite() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        sSatelliteManager.deprovisionService(
                TOKEN, getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            loge("deprovisionSatellite ex=" + ex);
            return false;
        }
        if (errorCode == null || errorCode != SatelliteManager.SATELLITE_RESULT_SUCCESS) {
            loge("deprovisionSatellite failed with errorCode=" + errorCode);
            return false;
        }
        return true;
    }

    protected static boolean isSatelliteProvisioned() {
        final AtomicReference<Boolean> provisioned = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        logd("isSatelliteProvisioned: result=" + result);
                        provisioned.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("isSatelliteProvisioned: onError, exception=" + exception);
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsProvisioned(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            loge("isSatelliteProvisioned ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isProvisioned = provisioned.get();
        if (error == null) {
            logd("isSatelliteProvisioned isProvisioned=" + isProvisioned);
            assertNotNull(isProvisioned);
            return isProvisioned;
        } else {
            assertNull(isProvisioned);
            logd("isSatelliteProvisioned error=" + error);
            return false;
        }
    }

    protected static boolean isSatelliteEnabled() {
        logd("isSatelliteEnabled");

        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        logd("isSatelliteEnabled: satellite enablement result: " + result);
                        enabled.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd(
                                "isSatelliteEnabled: satellite enablement error: "
                                        + exception.getErrorCode());
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        logd("isSatelliteEnabled: querying satellite enable state");
        sSatelliteManager.requestIsEnabled(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            loge("isSatelliteEnabled ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isEnabled = enabled.get();
        if (error == null) {
            assertNotNull(isEnabled);
            return isEnabled;
        } else {
            assertNull(isEnabled);
            logd("isSatelliteEnabled error=" + error);
            return false;
        }
    }

    protected static boolean isSatelliteDemoModeEnabled() {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        enabled.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsDemoModeEnabled(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            loge("isSatelliteDemoModeEnabled ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isEnabled = enabled.get();
        if (error == null) {
            assertNotNull(isEnabled);
            return isEnabled;
        } else {
            assertNull(isEnabled);
            logd("isSatelliteEnabled error=" + error);
            return false;
        }
    }

    protected static void requestSatelliteEnabled(boolean enabled) {
        logd("requestSatelliteEnabled: enabled=" + enabled);
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        logd(
                "requestSatelliteEnabled: requesting satellite to be "
                        + (enabled ? "enabled" : "disabled"));
        sSatelliteManager.requestEnabled(new EnableRequestAttributes.Builder(enabled).build(),
                getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestSatelliteEnabled failed with ex=" + ex);
            return;
        }
        logd("requestSatelliteEnabled: errorCode=" + errorCode);
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, (long) errorCode);
    }

    protected static void requestSatelliteEnabled(boolean enabled, boolean emergency) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestEnabled(
                new EnableRequestAttributes.Builder(enabled)
                        .setEmergencyMode(emergency)
                        .build(),
                getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestSatelliteEnabled failed with ex=" + ex);
            return;
        }
        logd("requestSatelliteEnabled: errorCode=" + errorCode);
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, (long) errorCode);
    }

    protected static void requestSatelliteEnabled(boolean enabled, long timeoutMillis) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestEnabled(new EnableRequestAttributes.Builder(enabled).build(),
                getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestSatelliteEnabled failed with ex=" + ex);
            return;
        }
        logd("requestSatelliteEnabled: errorCode=" + errorCode);
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, (long) errorCode);
    }

    protected static int requestSatelliteEnabledWithResult(boolean enabled, long timeoutMillis) {
        logd(
                "requestSatelliteEnabledWithResult: enabled="
                        + enabled
                        + ", timeoutMillis="
                        + timeoutMillis);
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestEnabled(new EnableRequestAttributes.Builder(enabled).build(),
                getContext().getMainExecutor(), error::offer);
        Integer errorCode = null;
        try {
            errorCode = error.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestSatelliteEnabled failed with ex=" + ex);
        }
        logd("requestSatelliteEnabledWithResult: errorCode=" + errorCode);
        assertNotNull(errorCode);
        return errorCode;
    }

    protected static void requestSatelliteEnabledForDemoMode(boolean enabled) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestEnabled(
                new EnableRequestAttributes.Builder(enabled)
                        .setDemoMode(true)
                        .setEmergencyMode(true)
                        .build(),
                getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestSatelliteEnabled failed with ex=" + ex);
            return;
        }
        logd("requestSatelliteEnabledForDemoMode: errorCode=" + errorCode);
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_RESULT_SUCCESS, (long) errorCode);
    }

    protected static void requestSatelliteEnabled(boolean enabled, boolean demoEnabled,
            int expectedError) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestEnabled(
                new EnableRequestAttributes.Builder(enabled).setDemoMode(demoEnabled).build(),
                getContext().getMainExecutor(), error::offer);
        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("requestSatelliteEnabled failed with ex=" + ex);
            return;
        }
        logd("requestSatelliteEnabled: errorCode=" + errorCode);
        assertNotNull(errorCode);
        assertEquals(expectedError, (long) errorCode);
    }

    protected static void verifyEmergencyMode(boolean expectedEmergencyMode) {
        final AtomicReference<Boolean> emergency = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        emergency.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsEmergencyModeEnabled(getContext().getMainExecutor(),
                receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Got InterruptedException for requestIsEmergencyModeEnabled, ex=" + ex);
        }

        Integer error = errorCode.get();
        Boolean isEmergency = emergency.get();
        if (error == null) {
            logd("verifyEmergencyMode isEmergency=" + isEmergency);
            assertNotNull(isEmergency);
            assertEquals(expectedEmergencyMode, isEmergency);
        } else {
            fail("Got error for requestIsEmergencyModeEnabled, error=" + error);
        }
    }

    protected static void verifyDemoMode(boolean expectedDemoMode) {
        final AtomicReference<Boolean> demoMode = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        demoMode.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsDemoModeEnabled(getContext().getMainExecutor(),
                receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            fail("Got InterruptedException for requestIsEmergencyModeEnabled, ex=" + ex);
        }

        Integer error = errorCode.get();
        Boolean isDemoModeEnabled = demoMode.get();
        if (error == null) {
            logd("verifyDemoMode isDemoModeEnabled=" + isDemoModeEnabled);
            assertNotNull(isDemoModeEnabled);
            assertEquals(expectedDemoMode, isDemoModeEnabled);
        } else {
            fail("Got error for requestIsEmergencyModeEnabled, error=" + error);
        }
    }

    protected static LinkedBlockingQueue<Integer> requestSatelliteEnabledWithoutWaitingForResult(
            boolean enabled, boolean demoMode, boolean emergency) {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        sSatelliteManager.requestEnabled(new EnableRequestAttributes.Builder(enabled)
                        .setDemoMode(demoMode)
                        .setEmergencyMode(emergency)
                        .build(),
                getContext().getMainExecutor(), error::offer);
        return error;
    }

    protected static void assertResult(LinkedBlockingQueue<Integer> result, int expectedError) {
        Integer errorCode;
        try {
            errorCode = result.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("assertResult failed with ex=" + ex);
            return;
        }
        logd("assertResult: errorCode=" + errorCode);
        assertNotNull(errorCode);
        assertEquals(expectedError, (long) errorCode);
    }

    protected static boolean isSatelliteSupported() {
        final AtomicReference<Boolean> supported = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        supported.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestIsSupported(getContext().getMainExecutor(),
                receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            loge("isSatelliteSupported ex=" + ex);
            return false;
        }

        Integer error = errorCode.get();
        Boolean isSupported = supported.get();
        if (error == null) {
            assertNotNull(isSupported);
            logd("isSatelliteSupported isSupported=" + isSupported);
            return isSupported;
        } else {
            assertNull(isSupported);
            logd("isSatelliteSupported error=" + error);
            return false;
        }
    }

    protected static void turnRadioOff() {
        ServiceStateRadioStateListener callback = new ServiceStateRadioStateListener(
                sTelephonyManager.getServiceState(), sTelephonyManager.getRadioPowerState());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                tm -> tm.registerTelephonyCallback(Runnable::run, callback));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                tm -> tm.requestRadioPowerOffForReason(TelephonyManager.RADIO_POWER_REASON_USER),
                android.Manifest.permission.MODIFY_PHONE_STATE);
        callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_OFF);
    }

    protected static void turnRadioOn() {
        ServiceStateRadioStateListener callback = new ServiceStateRadioStateListener(
                sTelephonyManager.getServiceState(), sTelephonyManager.getRadioPowerState());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                tm -> tm.registerTelephonyCallback(Runnable::run, callback));
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(sTelephonyManager,
                tm -> tm.clearRadioPowerOffForReason(TelephonyManager.RADIO_POWER_REASON_USER),
                android.Manifest.permission.MODIFY_PHONE_STATE);
        callback.waitForRadioStateIntent(TelephonyManager.RADIO_POWER_ON);
    }

    protected class UwbAdapterStateCallback implements UwbManager.AdapterStateCallback {
        private final Semaphore mUwbSemaphore = new Semaphore(0);
        private final Object mUwbExpectedStateLock = new Object();
        private boolean mUwbExpectedState = false;

        public String toString(int state) {
            switch (state) {
                case UwbManager.AdapterStateCallback.STATE_DISABLED:
                    return "Disabled";

                case UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE:
                    return "Inactive";

                case UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE:
                    return "Active";

                default:
                    return "";
            }
        }

        @Override
        public void onStateChanged(int state, int reason) {
            logd("UwbAdapterStateCallback onStateChanged() called, state = " + toString(state));
            logd("Adapter state changed reason " + String.valueOf(reason));

            synchronized (mUwbExpectedStateLock) {
                if (mUwbExpectedState == mUwbManager.isUwbEnabled()) {
                    try {
                        mUwbSemaphore.release();
                    } catch (Exception e) {
                        loge("UwbAdapterStateCallback onStateChanged(): Got exception, ex=" + e);
                    }
                }
            }
        }

        public boolean waitUntilOnUwbStateChanged() {
            synchronized (mUwbExpectedStateLock) {
                if (mUwbExpectedState == mUwbManager.isUwbEnabled()) {
                    return true;
                }
            }

            try {
                if (!mUwbSemaphore.tryAcquire(EXTERNAL_DEPENDENT_TIMEOUT,
                        TimeUnit.MILLISECONDS)) {
                    loge("UwbAdapterStateCallback Timeout to receive "
                            + "onStateChanged() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("UwbAdapterStateCallback waitUntilOnUwbStateChanged: Got exception=" + ex);
                return false;
            }
            return true;
        }

        public void setUwbExpectedState(boolean expectedState) {
            synchronized (mUwbExpectedStateLock) {
                mUwbExpectedState = expectedState;
                mUwbSemaphore.drainPermits();
            }
        }
    }

    protected class BTWifiNFCStateReceiver extends BroadcastReceiver {
        private final Semaphore mBTSemaphore = new Semaphore(0);
        private final Object mBTExpectedStateLock = new Object();
        private boolean mBTExpectedState = false;

        private final Semaphore mNfcSemaphore = new Semaphore(0);
        private final Object mNfcExpectedStateLock = new Object();
        private boolean mNfcExpectedState = false;

        private final Semaphore mWifiSemaphore = new Semaphore(0);
        private final Object mWifiExpectedStateLock = new Object();
        private boolean mWifiExpectedState = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) {
                logd("BTWifiNFCStateReceiver NULL action for intent " + intent);
                return;
            }
            logd("BTWifiNFCStateReceiver onReceive: action = " + action);

            switch (action) {
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR);
                    logd("Bluetooth state updated to " + btState);

                    synchronized (mBTExpectedStateLock) {
                        if (mBTExpectedState == mBluetoothAdapter.isEnabled()) {
                            try {
                                mBTSemaphore.release();
                            } catch (Exception e) {
                                loge("BTWifiNFCStateReceiver onReceive(): Got exception, ex=" + e);
                            }
                        }
                    }
                    break;

                case NfcAdapter.ACTION_ADAPTER_STATE_CHANGED:
                    int nfcState = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1);
                    logd("Nfc state updated to " + nfcState);

                    synchronized (mNfcExpectedStateLock) {
                        if (mNfcExpectedState == mNfcAdapter.isEnabled()) {
                            try {
                                mNfcSemaphore.release();
                            } catch (Exception e) {
                                loge("BTWifiNFCStateReceiver onReceive(): Got exception, ex=" + e);
                            }
                        }
                    }
                    break;

                case WifiManager.WIFI_STATE_CHANGED_ACTION:
                    int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                            WifiManager.WIFI_STATE_UNKNOWN);
                    logd("Wifi state updated to " + wifiState);

                    synchronized (mWifiExpectedStateLock) {
                        if (mWifiExpectedState == mWifiManager.isWifiEnabled()) {
                            try {
                                mWifiSemaphore.release();
                            } catch (Exception e) {
                                loge("BTWifiNFCStateReceiver onReceive(): Got exception, ex=" + e);
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        public boolean waitUntilOnBTStateChanged() {
            logd("waitUntilOnBTStateChanged");
            synchronized (mBTExpectedStateLock) {
                if (mBTExpectedState == mBluetoothAdapter.isEnabled()) {
                    return true;
                }
            }

            try {
                if (!mBTSemaphore.tryAcquire(EXTERNAL_DEPENDENT_TIMEOUT,
                        TimeUnit.MILLISECONDS)) {
                    loge("BTWifiNFCStateReceiver waitUntilOnBTStateChanged: "
                            + "Timeout to receive onStateChanged() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("BTWifiNFCStateReceiver waitUntilOnBTStateChanged: Got exception=" + ex);
                return false;
            }
            return true;
        }

        public boolean waitUntilOnNfcStateChanged() {
            synchronized (mNfcExpectedStateLock) {
                if (mNfcExpectedState == mNfcAdapter.isEnabled()) {
                    return true;
                }
            }

            try {
                if (!mNfcSemaphore.tryAcquire(EXTERNAL_DEPENDENT_TIMEOUT,
                        TimeUnit.MILLISECONDS)) {
                    loge("BTWifiNFCStateReceiver waitUntilOnNfcStateChanged: "
                            + "Timeout to receive onStateChanged() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("BTWifiNFCStateReceiver waitUntilOnNfcStateChanged: Got exception=" + ex);
                return false;
            }
            return true;
        }

        public boolean waitUntilOnWifiStateChanged() {
            synchronized (mWifiExpectedStateLock) {
                if (mWifiExpectedState == mWifiManager.isWifiEnabled()) {
                    return true;
                }
            }

            try {
                if (!mWifiSemaphore.tryAcquire(EXTERNAL_DEPENDENT_TIMEOUT,
                        TimeUnit.MILLISECONDS)) {
                    loge("BTWifiNFCStateReceiver waitUntilOnWifiStateChanged: "
                            + "Timeout to receive onStateChanged() callback");
                    return false;
                }
            } catch (Exception ex) {
                loge("BTWifiNFCStateReceiver waitUntilOnWifiStateChanged: Got exception=" + ex);
                return false;
            }
            return true;
        }

        public void setBTExpectedState(boolean expectedState) {
            synchronized (mBTExpectedStateLock) {
                mBTExpectedState = expectedState;
                mBTSemaphore.drainPermits();
            }
        }

        public void setWifiExpectedState(boolean expectedState) {
            synchronized (mWifiExpectedStateLock) {
                mWifiExpectedState = expectedState;
                mWifiSemaphore.drainPermits();
            }
        }

        public void setNfcExpectedState(boolean expectedState) {
            synchronized (mNfcExpectedStateLock) {
                mNfcExpectedState = expectedState;
                mNfcSemaphore.drainPermits();
            }
        }
    }

    protected static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    protected static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }

    protected static void assertSatelliteEnabledInSettings(boolean enabled) {
        int satelliteModeEnabled = Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.SATELLITE_MODE_ENABLED, 0);
        if (enabled) {
            assertEquals(satelliteModeEnabled, 1);
        } else {
            assertEquals(satelliteModeEnabled, 0);
        }
        logd("requestSatelliteEnabled: " + enabled
                + " : satelliteModeEnabled from settings: " + satelliteModeEnabled);
    }

    protected static void waitFor(long timeoutMillis) {
        Object delayTimeout = new Object();
        synchronized (delayTimeout) {
            try {
                delayTimeout.wait(timeoutMillis);
            } catch (InterruptedException ex) {
                // Ignore the exception
                logd("waitFor: delayTimeout ex=" + ex);
            }
        }
    }

    // Get default active subscription ID.
    protected static int getActiveSubIDForCarrierSatelliteTest() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> infos = ShellIdentityUtils.invokeMethodWithShellPermissions(sm,
                SubscriptionManager::getActiveSubscriptionInfoList);

        int defaultSubId = SubscriptionManager.getDefaultVoiceSubscriptionId();
        if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && isSubIdInInfoList(infos, defaultSubId)) {
            return defaultSubId;
        }

        defaultSubId = SubscriptionManager.getDefaultSubscriptionId();
        if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && isSubIdInInfoList(infos, defaultSubId)) {
            return defaultSubId;
        }

        // Couldn't resolve a default. We can try to resolve a default using the active
        // subscriptions.
        if (!infos.isEmpty()) {
            return infos.get(0).getSubscriptionId();
        }
        loge("getActiveSubIDForCarrierSatelliteTest: use invalid subscription ID");
        // There must be at least one active subscription.
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    protected static int getNtnOnlySubscriptionId() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SubscriptionManager sm = context.getSystemService(SubscriptionManager.class);
        List<SubscriptionInfo> infoList = ShellIdentityUtils.invokeMethodWithShellPermissions(sm,
                SubscriptionManager::getAllSubscriptionInfoList);

        int subId = infoList.stream()
                .filter(info -> info.isOnlyNonTerrestrialNetwork())
                .mapToInt(SubscriptionInfo::getSubscriptionId)
                .findFirst()
                .orElse(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID && !infoList.isEmpty()) {
            subId = infoList.get(0).getSubscriptionId();
        }
        logd("getNtnOnlySubscriptionId: subId=" + subId);
        return subId;
    }

    private static boolean isSubIdInInfoList(List<SubscriptionInfo> infos, int subId) {
        return infos.stream().anyMatch(info -> info.getSubscriptionId() == subId);
    }

    protected static Pair<List<SatelliteSubscriberProvisionStatus>, Integer>
            requestSatelliteSubscriberProvisionStatus() {
        final AtomicReference<List<SatelliteSubscriberProvisionStatus>> list =
                new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<List<SatelliteSubscriberProvisionStatus>,
                SatelliteManager.SatelliteException>
                receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(List<SatelliteSubscriberProvisionStatus> result) {
                        list.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestSatelliteSubscriberProvisionStatus(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            loge("requestSatelliteSubscriberProvisionStatus ex=" + ex);
            return null;
        }

        Integer error = errorCode.get();
        if (error == null) {
            assertTrue(list.get().size() > 0);
            return new Pair<>(list.get(), error);
        } else {
            assertFalse(list.get().size() > 0);
            return null;
        }
    }

    protected static Pair<Integer, Integer> requestSelectedNbIotSatelliteSubscriptionId() {
        final AtomicReference<Integer> selectedSatelliteSubscriptionId =
                new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Integer, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Integer result) {
                        logd("requestSelectedNbIotSatelliteSubscriptionId.onResult: result=" +
                                result);
                        selectedSatelliteSubscriptionId.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("requestSelectedNbIotSatelliteSubscriptionId.onError: onError="
                                + exception.getErrorCode());
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestSelectedNbIotSatelliteSubscriptionId(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        return new Pair<>(selectedSatelliteSubscriptionId.get(), errorCode.get());
    }

    protected static Pair<CharSequence, Integer> requestSatelliteDisplayName() {
        final AtomicReference<CharSequence> displayNameForSubscription = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<CharSequence, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(CharSequence result) {
                        logd("requestSatelliteDisplayName.onResult: result=" +
                                result);
                        displayNameForSubscription.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("requestSatelliteDisplayName.onError: onError="
                                + exception);
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.requestSatelliteDisplayName(
                getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        return new Pair<>(displayNameForSubscription.get(), errorCode.get());
    }

    protected static Pair<Boolean, Integer> provisionSatellite(List<SatelliteSubscriberInfo> list) {
        final AtomicReference<Boolean> requestResult = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Void, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Void result) {
                        logd("provisionSatellite: onResult");
                        requestResult.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("provisionSatellite: onError: onError=" + exception);
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.provisionSatellite(list, getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        return new Pair<>(requestResult.get(), errorCode.get());
    }

    protected static Pair<Boolean, Integer> deprovisionSatellite(
            List<SatelliteSubscriberInfo> list) {
        final AtomicReference<Boolean> requestResult = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Void, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Void result) {
                        logd("deprovisionSatellite: onResult");
                        requestResult.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        logd("deprovisionSatellite: onError: onError=" + exception);
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        sSatelliteManager.deprovisionSatellite(list, getContext().getMainExecutor(), receiver);
        try {
            assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
        return new Pair<>(requestResult.get(), errorCode.get());
    }

    @NonNull
    protected static PersistableBundle getConfigForSubId(Context context, int subId, String key) {
        PersistableBundle config = null;
        CarrierConfigManager carrierConfigManager = context.getSystemService(
                CarrierConfigManager.class);
        if (carrierConfigManager != null) {
            config = carrierConfigManager.getConfigForSubId(subId, key);
        }
        if (config == null || config.isEmpty()) {
            config = CarrierConfigManager.getDefaultConfig();
        }
        return config;
    }

    protected void setDefaultSmsSubId(Context context, int subId) {
        SubscriptionManager subscriptionManager = context.getSystemService(
                SubscriptionManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(subscriptionManager, (sm) ->
                        sm.setDefaultSmsSubId(subId),
                android.Manifest.permission.MODIFY_PHONE_STATE);
    }

    protected static class SatelliteReceiverTest extends BroadcastReceiver {
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onReceive(Context context, Intent intent) {
            if (SatelliteReceiver.TEST_INTENT.equals(intent.getAction())) {
                logd("SatelliteReceiverTest: receive the "
                        + SatelliteManager.ACTION_SATELLITE_SUBSCRIBER_ID_LIST_CHANGED);
                mSemaphore.release();
            }
        }

        public void clearQueue() {
            logd("SatelliteReceiverTest: clearQueue");
            mSemaphore.drainPermits();
        }

        boolean waitForReceive() {
            try {
                if (!mSemaphore.tryAcquire(TimeUnit.SECONDS.toMillis(65), TimeUnit.MILLISECONDS)) {
                    logd("SatelliteReceiverTest: Timeout to receive");
                    return false;
                }
            } catch (Exception ex) {
                logd("SatelliteReceiverTest: waitForReceive: Got exception=" + ex);
                return false;
            }
            return true;
        }
    }

    protected List<SatelliteAccessConfiguration> getExpectedSatelliteConfiguration() {
        UUID uuid1 = UUID.fromString("0db0312f-d73f-444d-b99b-a893dfb42edf");
        SatellitePosition satellitePosition1 = new SatellitePosition(-150.3, 35786000);
        List<Integer> bandList1 = new ArrayList<>(List.of(259, 260));
        EarfcnRange earfcnRange1 = new EarfcnRange(3000, 4300);
        List<Integer> tagIdList1 = new ArrayList<>(List.of(6, 7, 8));

        SatelliteInfo satelliteInfo1 = new SatelliteInfo(uuid1, satellitePosition1, bandList1,
                new ArrayList<>(List.of(earfcnRange1)));

        SatelliteAccessConfiguration configuration1 = new SatelliteAccessConfiguration(
                new ArrayList<>(List.of(satelliteInfo1)), tagIdList1);

        UUID uuid2 = UUID.fromString("1dec24f8-9223-4196-ad7a-a03002db7af7");
        SatellitePosition satellitePosition2 = new SatellitePosition(15.5, 35786000);
        List<Integer> bandList2 = new ArrayList<>(List.of(257, 258));
        EarfcnRange earfcnRange2 = new EarfcnRange(3200, 3200);
        List<Integer> tagIdList2 = new ArrayList<>(List.of(9, 10, 11));

        SatelliteInfo satelliteInfo2 = new SatelliteInfo(uuid2, satellitePosition2, bandList2,
                new ArrayList<>(List.of(earfcnRange2)));

        SatelliteAccessConfiguration configuration2 = new SatelliteAccessConfiguration(
                new ArrayList<>(List.of(satelliteInfo2)), tagIdList2);

        UUID uuid3 = UUID.fromString("f60cb479-d85b-4f4e-b050-cc428f5eb4a4");
        SatellitePosition satellitePosition3 = new SatellitePosition(-150, 35786000);
        List<Integer> bandList3 = new ArrayList<>(List.of(259, 260));
        EarfcnRange earfcnRange3 = new EarfcnRange(3300, 3400);
        List<Integer> tagIdList3 = new ArrayList<>(List.of(12, 13, 14));

        SatelliteInfo satelliteInfo3 = new SatelliteInfo(uuid3, satellitePosition3, bandList3,
                new ArrayList<>(List.of(earfcnRange3)));

        SatelliteAccessConfiguration configuration3 = new SatelliteAccessConfiguration(
                new ArrayList<>(List.of(satelliteInfo3)), tagIdList3);

        UUID uuid4 = UUID.fromString("c5837d96-9585-46aa-8dd0-a974583737fb");
        SatellitePosition satellitePosition4 = new SatellitePosition(-155, 35786000);
        List<Integer> bandList4 = new ArrayList<>(List.of(261, 262));
        EarfcnRange earfcnRange4 = new EarfcnRange(3500, 3600);
        List<Integer> tagIdList4 = new ArrayList<>(List.of(15, 16, 17));

        SatelliteInfo satelliteInfo4 = new SatelliteInfo(uuid4, satellitePosition4, bandList4,
                new ArrayList<>(List.of(earfcnRange4)));

        SatelliteAccessConfiguration configuration4 = new SatelliteAccessConfiguration(
                new ArrayList<>(List.of(satelliteInfo4)), tagIdList4);

        UUID uuid5 = UUID.fromString("6ef2a128-0477-4271-895f-dc4a221d2b23");
        SatellitePosition satellitePosition5 = new SatellitePosition(-66, 35786000);
        List<Integer> bandList5 = new ArrayList<>(List.of(263, 264));
        EarfcnRange earfcnRange5 = new EarfcnRange(3700, 3800);
        List<Integer> tagIdList5 = new ArrayList<>(List.of(18, 19, 20));

        SatelliteInfo satelliteInfo5 = new SatelliteInfo(uuid5, satellitePosition5, bandList5,
                new ArrayList<>(List.of(earfcnRange5)));

        SatelliteAccessConfiguration configuration5 = new SatelliteAccessConfiguration(
                new ArrayList<>(List.of(satelliteInfo5)), tagIdList5);

        return new ArrayList<>(
                List.of(configuration1, configuration2, configuration3, configuration4,
                        configuration5));
    }

    protected void verifySatelliteAccessConfiguration(
            @NonNull SatelliteAccessConfiguration expectedConfiguration,
            @NonNull SystemSelectionSpecifier actualSystemSelectionSpecifier) {

        List<SatelliteInfo> expectedSatelliteInfos =
                expectedConfiguration.getSatelliteInfos();
        List<Integer> expectedBandList = new ArrayList<>();
        List<Integer> expectedEarfcnList = new ArrayList<>();
        for (SatelliteInfo expectedSatelliteInfo : expectedSatelliteInfos) {
            expectedBandList.addAll(expectedSatelliteInfo.getBands());
            List<EarfcnRange> earfcnRangeList = expectedSatelliteInfo.getEarfcnRanges();
            earfcnRangeList.stream().flatMapToInt(
                    earfcnRange -> IntStream.of(earfcnRange.getStartEarfcn(),
                            earfcnRange.getEndEarfcn())).boxed().forEach(expectedEarfcnList::add);
        }

        List<Integer> actualBandList = Arrays.stream(actualSystemSelectionSpecifier.getBands())
                .boxed().collect(Collectors.toList());

        List<Integer> actualEarfcnList = Arrays.stream(actualSystemSelectionSpecifier.getEarfcns())
                .boxed().collect(Collectors.toList());

        SatelliteInfo[] expectedSatelliteInfoArray =
                expectedConfiguration.getSatelliteInfos().toArray(new SatelliteInfo[0]);
        SatelliteInfo[] actualSatelliteInfoArray =
                actualSystemSelectionSpecifier.getSatelliteInfos().toArray(new SatelliteInfo[0]);

        List<Integer> expectedTagIdList = expectedConfiguration.getTagIds();
        List<Integer> actualTagIdList = Arrays.stream(actualSystemSelectionSpecifier.getTagIds())
                .boxed().collect(Collectors.toList());

        assertEquals(expectedBandList, actualBandList);
        assertEquals(expectedEarfcnList, actualEarfcnList);
        assertArrayEquals(expectedSatelliteInfoArray, actualSatelliteInfoArray);
        assertEquals(expectedTagIdList, actualTagIdList);
    }

    /** Get HAL version for the given HAL service. */
    public int getHalVersion(int halService) {
        Pair<Integer, Integer> halVersion = sTelephonyManager.getHalVersion(halService);
        return makeRadioVersion(halVersion.first, halVersion.second);
    }

    private static int makeRadioVersion(int major, int minor) {
        if (major < 0 || minor < 0) return 0;
        return major * 100 + minor;
    }
}
