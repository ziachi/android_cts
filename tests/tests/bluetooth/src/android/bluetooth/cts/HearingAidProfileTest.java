/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTING;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTING;
import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;
import static android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE;
import static android.content.pm.PackageManager.FEATURE_LEANBACK;
import static android.content.pm.PackageManager.FEATURE_TELEVISION;
import static android.content.pm.PackageManager.FEATURE_WATCH;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothHearingAid.AdvertisementServiceData;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Parcel;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.List;

/** Unit test cases for {@link BluetoothHearingAid}. */
@RunWith(AndroidJUnit4.class)
public class HearingAidProfileTest {
    private static final String TAG = HearingAidProfileTest.class.getSimpleName();

    @Mock private BluetoothProfile.ServiceListener mListener;

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);
    private static final Duration WAIT_FOR_INTENT_TIMEOUT = Duration.ofSeconds(1);
    private static List<Integer> sValidConnectionStates =
            List.of(STATE_CONNECTING, STATE_CONNECTED, STATE_DISCONNECTED, STATE_DISCONNECTING);

    private static final AdvertisementServiceData sAdvertisementData;

    static {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(0b110); // CSIP supported, MODE_BINAURAL, SIDE_LEFT
        parcel.writeInt(1);
        parcel.setDataPosition(0);
        sAdvertisementData = AdvertisementServiceData.CREATOR.createFromParcel(parcel);
    }

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final BluetoothDevice mDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private BluetoothHearingAid mService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH_LE));
        boolean isAshaEnabledByDefault =
                !(mContext.getPackageManager().hasSystemFeature(FEATURE_AUTOMOTIVE)
                        || mContext.getPackageManager().hasSystemFeature(FEATURE_WATCH)
                        || mContext.getPackageManager().hasSystemFeature(FEATURE_TELEVISION)
                        || mContext.getPackageManager().hasSystemFeature(FEATURE_LEANBACK));
        assumeTrue(
                BluetoothProperties.isProfileAshaCentralEnabled().orElse(isAshaEnabledByDefault));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        assertThat(mAdapter.getProfileProxy(mContext, mListener, BluetoothProfile.HEARING_AID))
                .isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(BluetoothProfile.HEARING_AID), captor.capture());
        mService = (BluetoothHearingAid) captor.getValue();
        assertThat(mService).isNotNull();

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    @After
    public void tearDown() {
        mAdapter.closeProfileProxy(BluetoothProfile.HEARING_AID, mService);
        mUiAutomation.dropShellPermissionIdentity();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void closeProfileProxy() {
        mAdapter.closeProfileProxy(BluetoothProfile.HEARING_AID, mService);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.HEARING_AID));
    }

    /** Basic test case to make sure that a fictional device is disconnected. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getConnectionState() {
        assertThat(mService.getConnectionState(mDevice)).isEqualTo(STATE_DISCONNECTED);
    }

    /**
     * Basic test case to make sure that a fictional device throw a SecurityException when setting
     * volume.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void setVolume() {
        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mService.setVolume(42));
    }

    /** Basic test case to make sure that a fictional device is unknown side. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getDeviceSide() {
        assertThat(mService.getDeviceSide(mDevice)).isEqualTo(BluetoothHearingAid.SIDE_UNKNOWN);
    }

    /** Basic test case to make sure that a fictional device is unknown mode. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getDeviceMode() {
        assertThat(mService.getDeviceMode(mDevice)).isEqualTo(BluetoothHearingAid.MODE_UNKNOWN);
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data is
     * null.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getAdvertisementServiceData() {
        Permissions.enforceEachPermissions(
                () -> mService.getAdvertisementServiceData(mDevice),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_SCAN));
        try (var p = Permissions.withPermissions(BLUETOOTH_SCAN, BLUETOOTH_PRIVILEGED)) {
            assertThat(mService.getAdvertisementServiceData(mDevice)).isNull();
        }
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data's mode
     * is unknown.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getAdvertisementDeviceMode() {
        assertThat(sAdvertisementData.getDeviceMode()).isEqualTo(BluetoothHearingAid.MODE_BINAURAL);
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data's side
     * is unknown.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getAdvertisementDeviceSide() {
        assertThat(sAdvertisementData.getDeviceSide()).isEqualTo(BluetoothHearingAid.SIDE_LEFT);
    }

    /**
     * Basic test case to make sure that a fictional device's truncated HiSyncId is the expected
     * value.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getTruncatedHiSyncId() {
        assertThat(sAdvertisementData.getTruncatedHiSyncId()).isEqualTo(1);
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data's CSIP
     * is not supported.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void isCsipSupported() {
        assertThat(sAdvertisementData.isCsipSupported()).isTrue();
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data's CSIP
     * is not supported.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void isLikelyPairOfBluetoothHearingAid() {
        // Create another fake advertisement data
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(0b111); // CSIP supported, MODE_BINAURAL, SIDE_RIGHT
        parcel.writeInt(1);
        parcel.setDataPosition(0);
        AdvertisementServiceData dataOtherSide =
                AdvertisementServiceData.CREATOR.createFromParcel(parcel);
        assertThat(dataOtherSide).isNotNull();

        // two devices should be a pair
        assertThat(sAdvertisementData.isInPairWith(dataOtherSide)).isTrue();
    }

    /** Basic test case to get the list of connected Hearing Aid devices. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getConnectedDevices() {
        List<BluetoothDevice> deviceList = mService.getConnectedDevices();
        Log.d(TAG, "getConnectedDevices(): size=" + deviceList.size());
        for (BluetoothDevice device : deviceList) {
            assertThat(mService.getConnectionState(device)).isIn(sValidConnectionStates);
        }
    }

    /**
     * Basic test case to get the list of matching Hearing Aid devices for each of the 4 connection
     * states.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getDevicesMatchingConnectionStates() {
        for (int connectionState : sValidConnectionStates) {
            List<BluetoothDevice> deviceList =
                    mService.getDevicesMatchingConnectionStates(new int[] {connectionState});
            assertThat(deviceList).isNotNull();
            for (BluetoothDevice device : deviceList) {
                assertWithMessage("Mismatched connection state for device=" + device)
                        .that(mService.getConnectionState(device))
                        .isEqualTo(connectionState);
            }
        }
    }

    /**
     * Test case to make sure that if the connection changed intent is called, the parameters and
     * device are correct.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getConnectionStateChangedIntent() {
        List<BluetoothDevice> bondedDeviceList =
                mService.getDevicesMatchingConnectionStates(
                        sValidConnectionStates.stream().mapToInt(Integer::intValue).toArray());

        int numDevices = bondedDeviceList.size();
        assumeTrue(numDevices > 0);

        BroadcastReceiver mockReceiver = mock(BroadcastReceiver.class);
        mContext.registerReceiver(
                mockReceiver,
                new IntentFilter(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED));
        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        try {
            assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
            assertThat(BlockingBluetoothAdapter.enable()).isTrue();

            verify(mockReceiver, timeout(WAIT_FOR_INTENT_TIMEOUT.toMillis()).times(numDevices))
                    .onReceive(any(), captor.capture());
        } finally {
            mContext.unregisterReceiver(mockReceiver);
        }

        for (Intent intent : captor.getAllValues()) {
            assertThat(intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1))
                    .isIn(sValidConnectionStates);
            assertThat(intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1))
                    .isIn(sValidConnectionStates);
            assertThat((BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE))
                    .isIn(bondedDeviceList);
        }
    }

    @Test
    public void setGetConnectionPolicy() {
        Permissions.enforceEachPermissions(
                () -> mService.getConnectionPolicy(mDevice),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));
        Permissions.enforceEachPermissions(
                () -> mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_FORBIDDEN),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));
    }
}
