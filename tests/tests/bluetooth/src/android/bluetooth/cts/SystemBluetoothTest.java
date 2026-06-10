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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.Manifest.permission.BLUETOOTH_SCAN;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.provider.Settings;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class SystemBluetoothTest {
    @Rule public final Expect expect = Expect.create();
    private static final String TAG = SystemBluetoothTest.class.getSimpleName();

    private static final Duration OOB_TIMEOUT = Duration.ofSeconds(1);
    private static final long DEFAULT_DISCOVERY_TIMEOUT_MS = 12800;
    private static final int DISCOVERY_START_TIMEOUT = 500;

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    @Before
    public void setUp() throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();
    }

    @After
    public void tearDown() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    /** Test enable/disable silence mode and check whether the device is in correct state. */
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void silenceMode() {
        BluetoothDevice device = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertThat(device.setSilenceMode(true)).isTrue();
        assertThat(device.isInSilenceMode()).isFalse();

        assertThat(device.setSilenceMode(false)).isTrue();
        assertThat(device.isInSilenceMode()).isFalse();
    }

    /**
     * Test whether the metadata would be stored in Bluetooth storage successfully, also test
     * whether OnMetadataChangedListener would callback correct values when metadata is changed..
     */
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetMetadata() {
        final byte[] testByteData = "Test Data".getBytes();
        final BluetoothDevice device = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        final Executor executor =
                new Executor() {
                    @Override
                    public void execute(Runnable command) {
                        command.run();
                    }
                };
        BluetoothAdapter.OnMetadataChangedListener listener =
                mock(BluetoothAdapter.OnMetadataChangedListener.class);

        assertThat(mAdapter.addOnMetadataChangedListener(device, executor, listener)).isTrue();
        // prevent register device twice
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.addOnMetadataChangedListener(device, executor, listener));

        assertThat(device.setMetadata(BluetoothDevice.METADATA_MANUFACTURER_NAME, testByteData))
                .isTrue();
        assertThat(device.getMetadata(BluetoothDevice.METADATA_MANUFACTURER_NAME))
                .isEqualTo(testByteData);

        verify(listener, timeout(1_000))
                .onMetadataChanged(
                        eq(device),
                        eq(BluetoothDevice.METADATA_MANUFACTURER_NAME),
                        eq(testByteData));

        assertThat(mAdapter.removeOnMetadataChangedListener(device, listener)).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void discoveryEndMillis() {
        boolean recoverOffState = false;
        try {
            if (!TestUtils.isLocationOn(mContext)) {
                recoverOffState = true;
                TestUtils.enableLocation(mContext);
                mUiAutomation.grantRuntimePermission(
                        "android.bluetooth.cts", android.Manifest.permission.ACCESS_FINE_LOCATION);
            }

            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

            BroadcastReceiver mockReceiver = mock(BroadcastReceiver.class);
            mContext.registerReceiver(mockReceiver, filter);

            try (var p = Permissions.withPermissions(BLUETOOTH_SCAN)) {
                mAdapter.startDiscovery();
                // Wait for any of ACTION_DISCOVERY_STARTED intent, while holding BLUETOOTH_SCAN
                verify(mockReceiver, timeout(DISCOVERY_START_TIMEOUT)).onReceive(any(), any());
            }

            long discoveryEndTime = mAdapter.getDiscoveryEndMillis();
            long currentTime = System.currentTimeMillis();
            assertThat(discoveryEndTime > currentTime).isTrue();
            assertThat(discoveryEndTime - currentTime < DEFAULT_DISCOVERY_TIMEOUT_MS).isTrue();

            mContext.unregisterReceiver(mockReceiver);
        } finally {
            if (recoverOffState) {
                TestUtils.disableLocation(mContext);
            }
        }
    }

    /**
     * Tests whether the static function BluetoothUuid#containsAnyUuid properly identifies whether
     * the ParcelUuid arrays have at least one common element.
     */
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void containsAnyUuid() {
        ParcelUuid[] deviceAUuids =
                new ParcelUuid[] {
                    BluetoothUuid.A2DP_SOURCE,
                    BluetoothUuid.HFP,
                    BluetoothUuid.ADV_AUDIO_DIST,
                    BluetoothUuid.AVRCP_CONTROLLER,
                    BluetoothUuid.BASE_UUID,
                    BluetoothUuid.HID,
                    BluetoothUuid.HEARING_AID
                };
        ParcelUuid[] deviceBUuids =
                new ParcelUuid[] {
                    BluetoothUuid.A2DP_SINK,
                    BluetoothUuid.BNEP,
                    BluetoothUuid.AVRCP_TARGET,
                    BluetoothUuid.HFP_AG,
                    BluetoothUuid.HOGP,
                    BluetoothUuid.HSP_AG
                };
        ParcelUuid[] deviceCUuids =
                new ParcelUuid[] {
                    BluetoothUuid.HSP,
                    BluetoothUuid.MAP,
                    BluetoothUuid.MAS,
                    BluetoothUuid.MNS,
                    BluetoothUuid.NAP,
                    BluetoothUuid.OBEX_OBJECT_PUSH,
                    BluetoothUuid.PANU,
                    BluetoothUuid.PBAP_PCE,
                    BluetoothUuid.PBAP_PSE,
                    BluetoothUuid.SAP,
                    BluetoothUuid.A2DP_SOURCE
                };
        expect.that(BluetoothUuid.containsAnyUuid(null, null)).isTrue();
        expect.that(BluetoothUuid.containsAnyUuid(new ParcelUuid[] {}, null)).isTrue();
        expect.that(BluetoothUuid.containsAnyUuid(null, new ParcelUuid[] {})).isTrue();
        expect.that(BluetoothUuid.containsAnyUuid(null, deviceAUuids)).isFalse();
        expect.that(BluetoothUuid.containsAnyUuid(deviceAUuids, null)).isFalse();
        expect.that(BluetoothUuid.containsAnyUuid(deviceAUuids, deviceBUuids)).isFalse();
        expect.that(BluetoothUuid.containsAnyUuid(deviceAUuids, deviceCUuids)).isTrue();
        expect.that(BluetoothUuid.containsAnyUuid(deviceBUuids, deviceBUuids)).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void parseUuidFrom() {
        byte[] uuid16 = new byte[] {0x0B, 0x11};
        assertThat(BluetoothUuid.parseUuidFrom(uuid16)).isEqualTo(BluetoothUuid.A2DP_SINK);

        byte[] uuid32 = new byte[] {(byte) 0xF0, (byte) 0xFD, 0x00, 0x00};
        assertThat(BluetoothUuid.parseUuidFrom(uuid32)).isEqualTo(BluetoothUuid.HEARING_AID);

        byte[] uuid128 =
                new byte[] {
                    (byte) 0xFB,
                    0x34,
                    (byte) 0x9B,
                    0x5F,
                    (byte) 0x80,
                    0x00,
                    0x00,
                    (byte) 0x80,
                    0x00,
                    0x10,
                    0x00,
                    0x00,
                    0x1F,
                    0x11,
                    0x00,
                    0x00
                };
        assertThat(BluetoothUuid.parseUuidFrom(uuid128)).isEqualTo(BluetoothUuid.HFP_AG);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void canBondWithoutDialog() {
        // Verify the method returns false on a device that doesn't meet the criteria
        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertThat(testDevice.canBondWithoutDialog()).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void bleOnlyMode() {
        assumeTrue(TestUtils.isBleSupported(mContext));

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(BlockingBluetoothAdapter.enableBLE(false)).isFalse();

        try {
            assertThat(BlockingBluetoothAdapter.enableBLE(true)).isTrue();
            assertThat(BlockingBluetoothAdapter.disableBLE()).isTrue();
        } finally {
            // Tests are running with the scan disabled (See AndroidTest.xml)
            Settings.Global.putInt(
                    mContext.getContentResolver(), Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE, 0);
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetOwnAddressType() {
        assumeTrue(TestUtils.isBleSupported(mContext));

        AdvertisingSetParameters.Builder paramsBuilder = new AdvertisingSetParameters.Builder();
        assertThat(paramsBuilder.setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT))
                .isEqualTo(paramsBuilder);

        assertThat(paramsBuilder.build().getOwnAddressType())
                .isEqualTo(AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT);

        assertThat(paramsBuilder.setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_PUBLIC))
                .isEqualTo(paramsBuilder);
        assertThat(paramsBuilder.build().getOwnAddressType())
                .isEqualTo(AdvertisingSetParameters.ADDRESS_TYPE_PUBLIC);

        assertThat(paramsBuilder.setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_RANDOM))
                .isEqualTo(paramsBuilder);
        assertThat(paramsBuilder.build().getOwnAddressType())
                .isEqualTo(AdvertisingSetParameters.ADDRESS_TYPE_RANDOM);

        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();

        assertThat(settingsBuilder.setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT))
                .isEqualTo(settingsBuilder);
        assertThat(settingsBuilder.build().getOwnAddressType())
                .isEqualTo(AdvertisingSetParameters.ADDRESS_TYPE_DEFAULT);

        assertThat(settingsBuilder.setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_PUBLIC))
                .isEqualTo(settingsBuilder);
        assertThat(settingsBuilder.build().getOwnAddressType())
                .isEqualTo(AdvertisingSetParameters.ADDRESS_TYPE_PUBLIC);

        assertThat(settingsBuilder.setOwnAddressType(AdvertisingSetParameters.ADDRESS_TYPE_RANDOM))
                .isEqualTo(settingsBuilder);
        assertThat(settingsBuilder.build().getOwnAddressType())
                .isEqualTo(AdvertisingSetParameters.ADDRESS_TYPE_RANDOM);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getSupportedProfiles() {
        assertThat(mAdapter.getSupportedProfiles()).isNotNull();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void enableNoAutoConnect() {
        // Assert that when Bluetooth is already enabled, the method immediately returns true
        assertThat(mAdapter.enableNoAutoConnect()).isTrue();
    }

    private boolean isBluetoothPersistedOff() {
        // A value of "0" in Settings.Global.BLUETOOTH_ON means the OFF state was persisted
        return (Settings.Global.getInt(
                        mContext.getContentResolver(), Settings.Global.BLUETOOTH_ON, -1)
                == 0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void disableBluetoothPersistFalse() {
        assertThat(BlockingBluetoothAdapter.disable(/* persist= */ false)).isTrue();
        assertThat(isBluetoothPersistedOff()).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void disableBluetoothPersistTrue() {
        assertThat(BlockingBluetoothAdapter.disable(/* persist= */ true)).isTrue();
        assertThat(isBluetoothPersistedOff()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setLowLatencyAudioAllowed() {
        BluetoothDevice device = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertThat(device.setLowLatencyAudioAllowed(true)).isTrue();
        assertThat(device.setLowLatencyAudioAllowed(false)).isTrue();

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(device.setLowLatencyAudioAllowed(true)).isFalse();
        assertThat(device.setLowLatencyAudioAllowed(false)).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void generateLocalOobData() {
        Executor executor =
                new Executor() {
                    @Override
                    public void execute(Runnable command) {
                        command.run();
                    }
                };
        BluetoothAdapter.OobDataCallback callback = mock(BluetoothAdapter.OobDataCallback.class);

        // Invalid transport
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mAdapter.generateLocalOobData(
                                BluetoothDevice.TRANSPORT_AUTO, executor, callback));

        // Null callback
        assertThrows(
                NullPointerException.class,
                () ->
                        mAdapter.generateLocalOobData(
                                BluetoothDevice.TRANSPORT_BREDR, executor, null));

        mAdapter.generateLocalOobData(BluetoothDevice.TRANSPORT_BREDR, executor, callback);
        verify(callback, timeout(OOB_TIMEOUT.toMillis())).onOobData(anyInt(), any());

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        mAdapter.generateLocalOobData(BluetoothDevice.TRANSPORT_BREDR, executor, callback);
        verify(callback, timeout(OOB_TIMEOUT.toMillis()))
                .onError(eq(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setScanMode() {

        assertThrows(IllegalArgumentException.class, () -> mAdapter.setScanMode(0));

        /* TODO(rahulsabnis): Fix the callback system so these work as intended
        assertThat(mAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_NONE))
                .isEqualTo(BluetoothStatusCodes.SUCCESS);
        assertThat(mAdapter.getScanMode()).isEqualTo(BluetoothAdapter.SCAN_MODE_NONE);
        assertThat(mAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE))
                .isEqualTo(BluetoothStatusCodes.SUCCESS);
        assertThat(mAdapter.getScanMode()).isEqualTo(BluetoothAdapter.SCAN_MODE_CONNECTABLE);

        assertThat(mAdapter.setDiscoverableTimeout(Duration.ofSeconds(1)))
                .isEqualTo(BluetoothStatusCodes.SUCCESS);
        assertThat(mAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE))
                .isEqualTo(BluetoothStatusCodes.SUCCESS);
        assertThat(mAdapter.getScanMode())
                .isEqualTo(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertThat(mAdapter.getScanMode()).isEqualTo(BluetoothAdapter.SCAN_MODE_CONNECTABLE);
        */

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mAdapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE))
                .isEqualTo(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED);
    }
}
