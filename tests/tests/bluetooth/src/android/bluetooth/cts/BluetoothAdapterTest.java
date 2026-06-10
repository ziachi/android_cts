/*
 * Copyright (C) 2009 The Android Open Source Project
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
import static android.bluetooth.BluetoothAdapter.BT_SNOOP_LOG_MODE_DISABLED;
import static android.bluetooth.BluetoothAdapter.BT_SNOOP_LOG_MODE_FILTERED;
import static android.bluetooth.BluetoothAdapter.BT_SNOOP_LOG_MODE_FULL;
import static android.bluetooth.BluetoothDevice.ADDRESS_TYPE_PUBLIC;
import static android.bluetooth.BluetoothDevice.ADDRESS_TYPE_RANDOM;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTING;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTING;

import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothQualityReport;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.ApiLevelUtil;

import com.google.common.collect.Range;

import org.hamcrest.Matcher;
import org.hamcrest.core.AllOf;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.hamcrest.MockitoHamcrest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Very basic test, just of the static methods of {@link BluetoothAdapter}. */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class BluetoothAdapterTest {
    private static final String TAG = BluetoothAdapterTest.class.getSimpleName();

    private static final String ENABLE_DUAL_MODE_AUDIO = "persist.bluetooth.enable_dual_mode_audio";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final boolean mHasBluetooth =
            mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);

    private BluetoothAdapter mAdapter;

    @Before
    public void setUp() {
        if (mHasBluetooth) {
            mAdapter = BlockingBluetoothAdapter.getAdapter();
            assertThat(mAdapter).isNotNull();
            assertThat(BlockingBluetoothAdapter.enable()).isTrue();
        }
    }

    @Test
    public void getDefaultAdapter() {
        /*
         * Note: If the target doesn't support Bluetooth at all, then
         * this method should return null.
         */
        if (mHasBluetooth) {
            assertThat(BluetoothAdapter.getDefaultAdapter()).isNotNull();
        } else {
            assertThat(BluetoothAdapter.getDefaultAdapter()).isNull();
        }
    }

    @Test
    public void checkBluetoothAddress() {
        // Can't be null.
        assertThat(BluetoothAdapter.checkBluetoothAddress(null)).isFalse();

        // Must be 17 characters long.
        assertThat(BluetoothAdapter.checkBluetoothAddress("")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("0")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:0")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:0")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:00:")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:00:0")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:00:00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:0")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:00:")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:00:0")).isFalse();

        // Must have colons between octets.
        assertThat(BluetoothAdapter.checkBluetoothAddress("00x00:00:00:00:00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00.00:00:00:00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:00-00:00:00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:00:00900:00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:00?00")).isFalse();

        // Hex letters must be uppercase.
        assertThat(BluetoothAdapter.checkBluetoothAddress("a0:00:00:00:00:00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("0b:00:00:00:00:00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:c0:00:00:00:00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:0d:00:00:00:00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:e0:00:00:00")).isFalse();
        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:0f:00:00:00")).isFalse();

        assertThat(BluetoothAdapter.checkBluetoothAddress("00:00:00:00:00:00")).isTrue();
        assertThat(BluetoothAdapter.checkBluetoothAddress("12:34:56:78:9A:BC")).isTrue();
        assertThat(BluetoothAdapter.checkBluetoothAddress("DE:F0:FE:DC:B8:76")).isTrue();
    }

    @Test
    public void enableDisable() {
        assumeTrue(mHasBluetooth);

        for (int i = 0; i < 5; i++) {
            assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
            assertThat(BlockingBluetoothAdapter.enable()).isTrue();
        }
    }

    @Test
    public void getAddress() {
        assumeTrue(mHasBluetooth);

        assertThrows(SecurityException.class, () -> mAdapter.getAddress());

        String address;
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            address = mAdapter.getAddress();
        }

        assertThat(BluetoothAdapter.checkBluetoothAddress(address)).isTrue();
    }

    @Test
    public void setName_getName() {
        assumeTrue(mHasBluetooth);
        final Duration setNameTimeout = Duration.ofSeconds(5);
        final String genericName = "Generic Device 1";

        assertThrows(SecurityException.class, () -> mAdapter.setName("The name"));
        assertThrows(SecurityException.class, () -> mAdapter.getName());

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        BroadcastReceiver mockReceiver = mock(BroadcastReceiver.class);
        mContext.registerReceiver(mockReceiver, filter);

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            String originalName = mAdapter.getName();
            assertThat(originalName).isNotNull();

            // Check renaming the adapter
            assertThat(mAdapter.setName(genericName)).isTrue();
            verifyIntentReceived(
                    mockReceiver,
                    setNameTimeout,
                    hasAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED),
                    hasExtra(BluetoothAdapter.EXTRA_LOCAL_NAME, genericName));
            assertThat(mAdapter.getName()).isEqualTo(genericName);

            // Check setting adapter back to original name
            assertThat(mAdapter.setName(originalName)).isTrue();
            verifyIntentReceived(
                    mockReceiver,
                    setNameTimeout,
                    hasAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED),
                    hasExtra(BluetoothAdapter.EXTRA_LOCAL_NAME, originalName));
            assertThat(mAdapter.getName()).isEqualTo(originalName);
        }
    }

    @Test
    public void getBondedDevices() {
        assumeTrue(mHasBluetooth);

        assertThrows(SecurityException.class, () -> mAdapter.getBondedDevices());

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            assertThat(mAdapter.getBondedDevices()).isNotNull();
        }

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mAdapter.getBondedDevices()).isEmpty();
    }

    @Test
    public void getProfileConnectionState() {
        assumeTrue(mHasBluetooth);

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            assertThat(mAdapter.getProfileConnectionState(BluetoothProfile.A2DP))
                    .isEqualTo(BluetoothAdapter.STATE_DISCONNECTED);
        }
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            assertThat(mAdapter.getProfileConnectionState(BluetoothProfile.A2DP))
                    .isEqualTo(BluetoothAdapter.STATE_DISCONNECTED);
        }
        // getProfileConnectionState is caching it's return value and cts test doesn't know how to
        // deal with it
        // assertThrows(SecurityException.class,
        //         () -> mAdapter.getProfileConnectionState(BluetoothProfile.A2DP));
    }

    @Test
    public void getRemoteDevice() {
        assumeTrue(mHasBluetooth);

        // getRemoteDevice() should work even with Bluetooth disabled
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // test bad addresses
        assertThrows(IllegalArgumentException.class, () -> mAdapter.getRemoteDevice((String) null));
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.getRemoteDevice("00:00:00:00:00:00:00:00"));
        assertThrows(IllegalArgumentException.class, () -> mAdapter.getRemoteDevice((byte[]) null));
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.getRemoteDevice(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00}));

        // test success
        BluetoothDevice device = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertThat(device.getAddress()).isEqualTo("00:11:22:AA:BB:CC");
        device = mAdapter.getRemoteDevice(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06});
        assertThat(device.getAddress()).isEqualTo("01:02:03:04:05:06");
    }

    @Test
    public void getRemoteLeDevice() {
        assumeTrue(mHasBluetooth);

        // getRemoteLeDevice() should work even with Bluetooth disabled
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // test bad addresses
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.getRemoteLeDevice((String) null, ADDRESS_TYPE_PUBLIC));
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.getRemoteLeDevice("01:02:03:04:05:06:07:08", ADDRESS_TYPE_PUBLIC));
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.getRemoteLeDevice("01:02:03:04:05", ADDRESS_TYPE_PUBLIC));
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.getRemoteLeDevice("00:01:02:03:04:05", ADDRESS_TYPE_RANDOM + 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.getRemoteLeDevice("00:01:02:03:04:05", ADDRESS_TYPE_PUBLIC - 1));

        // test success
        assertThat(
                        mAdapter.getRemoteLeDevice("00:11:22:AA:BB:CC", ADDRESS_TYPE_PUBLIC)
                                .getAddress())
                .isEqualTo("00:11:22:AA:BB:CC");
        assertThat(
                        mAdapter.getRemoteLeDevice("01:02:03:04:05:06", ADDRESS_TYPE_RANDOM)
                                .getAddress())
                .isEqualTo("01:02:03:04:05:06");
    }

    @Test
    public void isLeAudioSupported() throws IOException {
        assumeTrue(mHasBluetooth);

        assertThat(mAdapter.isLeAudioSupported()).isNotEqualTo(BluetoothStatusCodes.ERROR_UNKNOWN);
    }

    @Test
    public void isLeAudioBroadcastSourceSupported() throws IOException {
        assumeTrue(mHasBluetooth);

        assertThat(mAdapter.isLeAudioBroadcastSourceSupported())
                .isNotEqualTo(BluetoothStatusCodes.ERROR_UNKNOWN);
    }

    @Test
    public void isLeAudioBroadcastAssistantSupported() throws IOException {
        assumeTrue(mHasBluetooth);

        assertThat(mAdapter.isLeAudioBroadcastAssistantSupported())
                .isNotEqualTo(BluetoothStatusCodes.ERROR_UNKNOWN);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void isLeCocSocketOffloadSupported() {
        assumeTrue(mHasBluetooth);

        assertThrows(SecurityException.class, () -> mAdapter.isLeCocSocketOffloadSupported());

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThat(mAdapter.isLeCocSocketOffloadSupported()).isAnyOf(true, false);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void isRfcommSocketOffloadSupported() {
        assumeTrue(mHasBluetooth);

        assertThrows(SecurityException.class, () -> mAdapter.isRfcommSocketOffloadSupported());

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThat(mAdapter.isRfcommSocketOffloadSupported()).isAnyOf(true, false);
        }
    }

    @Test
    public void isDistanceMeasurementSupported() throws IOException {
        assumeTrue(mHasBluetooth);

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            assertThat(mAdapter.isDistanceMeasurementSupported())
                    .isNotEqualTo(BluetoothStatusCodes.ERROR_UNKNOWN);
        }
    }

    @Test
    public void getMaxConnectedAudioDevices() {
        assumeTrue(mHasBluetooth);
        assertThrows(SecurityException.class, () -> mAdapter.getMaxConnectedAudioDevices());

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            // Range defined in com.android.bluetooth.btservice.AdapterProperties
            assertThat(mAdapter.getMaxConnectedAudioDevices()).isIn(Range.closed(1, 5));
        }
    }

    @Test
    public void listenUsingRfcommWithServiceRecord() throws IOException {
        assumeTrue(mHasBluetooth);

        BluetoothServerSocket socket;
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            socket = mAdapter.listenUsingRfcommWithServiceRecord("test", UUID.randomUUID());
        }
        assertThat(socket).isNotNull();
        socket.close();

        assertThrows(
                SecurityException.class,
                () -> mAdapter.listenUsingRfcommWithServiceRecord("test", UUID.randomUUID()));
    }

    @Test
    public void discoverableTimeout() {
        assumeTrue(mHasBluetooth);

        Duration minutes = Duration.ofMinutes(2);

        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.setDiscoverableTimeout(Duration.ofDays(25000)));
        Permissions.enforceEachPermissions(
                () -> mAdapter.setDiscoverableTimeout(minutes),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_SCAN));
        try (var p = Permissions.withPermissions(BLUETOOTH_SCAN, BLUETOOTH_PRIVILEGED)) {
            assertThat(mAdapter.setDiscoverableTimeout(minutes))
                    .isEqualTo(BluetoothStatusCodes.SUCCESS);
            assertThat(mAdapter.getDiscoverableTimeout()).isEqualTo(minutes);
        }
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mAdapter.getDiscoverableTimeout()).isNull();
        assertThat(mAdapter.setDiscoverableTimeout(minutes))
                .isEqualTo(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED);
    }

    @Test
    public void getConnectionState() {
        assumeTrue(mHasBluetooth);

        // Verify return value if Bluetooth is not enabled
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mAdapter.getConnectionState()).isEqualTo(STATE_DISCONNECTED);
    }

    @Test
    public void getMostRecentlyConnectedDevices() {
        assumeTrue(mHasBluetooth);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mAdapter.getMostRecentlyConnectedDevices());

        // Verify return value if Bluetooth is not enabled
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mAdapter.getMostRecentlyConnectedDevices()).isEmpty();
    }

    @Test
    public void getUuids() {
        assumeTrue(mHasBluetooth);

        assertThrows(SecurityException.class, () -> mAdapter.getUuidsList());

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            assertThat(mAdapter.getUuidsList()).isNotNull();
        }

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mAdapter.getUuidsList()).isEmpty();
    }

    @Test
    public void nameForState() {
        assertThat(BluetoothAdapter.nameForState(BluetoothAdapter.STATE_ON)).isEqualTo("ON");
        assertThat(BluetoothAdapter.nameForState(BluetoothAdapter.STATE_OFF)).isEqualTo("OFF");
        assertThat(BluetoothAdapter.nameForState(BluetoothAdapter.STATE_TURNING_ON))
                .isEqualTo("TURNING_ON");
        assertThat(BluetoothAdapter.nameForState(BluetoothAdapter.STATE_TURNING_OFF))
                .isEqualTo("TURNING_OFF");

        assertThat(BluetoothAdapter.nameForState(BluetoothAdapter.STATE_BLE_ON))
                .isEqualTo("BLE_ON");

        // Check value before state range
        for (int state = 0; state < BluetoothAdapter.STATE_OFF; state++) {
            assertThat(BluetoothAdapter.nameForState(state)).isEqualTo("?!?!? (" + state + ")");
        }
        // Check value after state range (skip TURNING_OFF)
        for (int state = BluetoothAdapter.STATE_BLE_ON + 2; state < 100; state++) {
            assertThat(BluetoothAdapter.nameForState(state)).isEqualTo("?!?!? (" + state + ")");
        }
    }

    @Test
    public void BluetoothConnectionCallback_disconnectReasonText() {
        assertThat(
                        BluetoothAdapter.BluetoothConnectionCallback.disconnectReasonToString(
                                BluetoothStatusCodes.ERROR_UNKNOWN))
                .isEqualTo("Reason unknown");
    }

    @Test
    public void registerBluetoothConnectionCallback() {
        assumeTrue(mHasBluetooth);

        Executor executor = mock(Executor.class);
        BluetoothAdapter.BluetoothConnectionCallback callback =
                new BluetoothAdapter.BluetoothConnectionCallback() {};

        // placeholder call for coverage
        callback.onDeviceConnected(null);
        callback.onDeviceDisconnected(null, BluetoothStatusCodes.ERROR_UNKNOWN);

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            assertThat(mAdapter.registerBluetoothConnectionCallback(executor, callback)).isTrue();
            assertThat(mAdapter.unregisterBluetoothConnectionCallback(callback)).isTrue();
        }
    }

    @Test
    public void requestControllerActivityEnergyInfo() {
        assumeTrue(mHasBluetooth);

        Executor executor = mock(Executor.class);
        BluetoothAdapter.OnBluetoothActivityEnergyInfoCallback callback =
                mock(BluetoothAdapter.OnBluetoothActivityEnergyInfoCallback.class);

        assertThrows(
                NullPointerException.class,
                () -> mAdapter.requestControllerActivityEnergyInfo(null, callback));
        assertThrows(
                NullPointerException.class,
                () -> mAdapter.requestControllerActivityEnergyInfo(executor, null));
    }

    // CTS doesn't run with a compatible remote device.
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakeActivityEnergyInfoCallbackCoverage() {
        BluetoothAdapter.OnBluetoothActivityEnergyInfoCallback callback =
                mock(BluetoothAdapter.OnBluetoothActivityEnergyInfoCallback.class);

        callback.onBluetoothActivityEnergyInfoAvailable(null);
        callback.onBluetoothActivityEnergyInfoError(0);
    }

    @Test
    public void clearBluetooth() {
        assumeTrue(mHasBluetooth);

        Permissions.enforceEachPermissions(
                () -> mAdapter.clearBluetooth(), List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        // Verify throws RuntimeException when trying to save sysprop for later (permission denied)
        assertThrows(RuntimeException.class, () -> mAdapter.clearBluetooth());
    }

    private void assertConnectionStateName(int connectionState, String name) {
        assertThat(BluetoothProfile.getConnectionStateName(connectionState)).isEqualTo(name);
    }

    @Test
    public void BluetoothProfile_getConnectionStateName() {
        assumeTrue(mHasBluetooth);

        assertConnectionStateName(STATE_DISCONNECTED, "STATE_DISCONNECTED");
        assertConnectionStateName(STATE_CONNECTED, "STATE_CONNECTED");
        assertConnectionStateName(STATE_CONNECTING, "STATE_CONNECTING");
        assertConnectionStateName(STATE_CONNECTED, "STATE_CONNECTED");
        assertConnectionStateName(STATE_DISCONNECTING, "STATE_DISCONNECTING");
        assertConnectionStateName(STATE_DISCONNECTING + 1, "STATE_UNKNOWN");
    }

    private void assertProfileName(int profile, String name) {
        assertThat(BluetoothProfile.getProfileName(profile)).isEqualTo(name);
    }

    @Test
    public void BluetoothProfile_getProfileName() {
        assertProfileName(BluetoothProfile.HEADSET, "HEADSET");
        assertProfileName(BluetoothProfile.A2DP, "A2DP");
        assertProfileName(BluetoothProfile.HID_HOST, "HID_HOST");
        assertProfileName(BluetoothProfile.PAN, "PAN");
        assertProfileName(BluetoothProfile.PBAP, "PBAP");
        assertProfileName(BluetoothProfile.GATT, "GATT");
        assertProfileName(BluetoothProfile.GATT_SERVER, "GATT_SERVER");
        assertProfileName(BluetoothProfile.MAP, "MAP");
        assertProfileName(BluetoothProfile.SAP, "SAP");
        assertProfileName(BluetoothProfile.A2DP_SINK, "A2DP_SINK");
        assertProfileName(BluetoothProfile.AVRCP_CONTROLLER, "AVRCP_CONTROLLER");
        assertProfileName(BluetoothProfile.HEADSET_CLIENT, "HEADSET_CLIENT");
        assertProfileName(BluetoothProfile.PBAP_CLIENT, "PBAP_CLIENT");
        assertProfileName(BluetoothProfile.MAP_CLIENT, "MAP_CLIENT");
        assertProfileName(BluetoothProfile.HID_DEVICE, "HID_DEVICE");
        assertProfileName(BluetoothProfile.OPP, "OPP");
        assertProfileName(BluetoothProfile.HEARING_AID, "HEARING_AID");
        assertProfileName(BluetoothProfile.LE_AUDIO, "LE_AUDIO");
        assertProfileName(BluetoothProfile.HAP_CLIENT, "HAP_CLIENT");

        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            return;
        }

        assertProfileName(BluetoothProfile.VOLUME_CONTROL, "VOLUME_CONTROL");
        assertProfileName(BluetoothProfile.CSIP_SET_COORDINATOR, "CSIP_SET_COORDINATOR");
        assertProfileName(BluetoothProfile.LE_AUDIO_BROADCAST, "LE_AUDIO_BROADCAST");
        assertProfileName(
                BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT, "LE_AUDIO_BROADCAST_ASSISTANT");
    }

    @Test
    public void autoOnApi() {
        assumeTrue(mHasBluetooth);

        assertThrows(SecurityException.class, () -> mAdapter.isAutoOnSupported());
        assertThrows(SecurityException.class, () -> mAdapter.isAutoOnEnabled());
        assertThrows(SecurityException.class, () -> mAdapter.setAutoOnEnabled(false));

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            // Not all devices support the auto on feature
            assumeTrue(mAdapter.isAutoOnSupported());

            mAdapter.setAutoOnEnabled(false);
            assertThat(mAdapter.isAutoOnEnabled()).isFalse();

            mAdapter.setAutoOnEnabled(true);
            assertThat(mAdapter.isAutoOnEnabled()).isTrue();
        }
    }

    @Test
    public void getSetBluetoothHciSnoopLoggingMode() {
        assumeTrue(mHasBluetooth);

        assertThrows(
                SecurityException.class,
                () -> mAdapter.setBluetoothHciSnoopLoggingMode(BT_SNOOP_LOG_MODE_FULL));
        assertThrows(SecurityException.class, () -> mAdapter.getBluetoothHciSnoopLoggingMode());

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> mAdapter.setBluetoothHciSnoopLoggingMode(-1));

            assertThat(mAdapter.setBluetoothHciSnoopLoggingMode(BT_SNOOP_LOG_MODE_FULL))
                    .isEqualTo(BluetoothStatusCodes.SUCCESS);
            assertThat(mAdapter.getBluetoothHciSnoopLoggingMode())
                    .isEqualTo(BT_SNOOP_LOG_MODE_FULL);

            assertThat(mAdapter.setBluetoothHciSnoopLoggingMode(BT_SNOOP_LOG_MODE_FILTERED))
                    .isEqualTo(BluetoothStatusCodes.SUCCESS);
            assertThat(mAdapter.getBluetoothHciSnoopLoggingMode())
                    .isEqualTo(BT_SNOOP_LOG_MODE_FILTERED);

            assertThat(mAdapter.setBluetoothHciSnoopLoggingMode(BT_SNOOP_LOG_MODE_DISABLED))
                    .isEqualTo(BluetoothStatusCodes.SUCCESS);
            assertThat(mAdapter.getBluetoothHciSnoopLoggingMode())
                    .isEqualTo(BT_SNOOP_LOG_MODE_DISABLED);
        }
    }

    @Test
    public void setPreferredAudioProfiles_getPreferredAudioProfiles() {
        assumeTrue(mHasBluetooth);

        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);

        Bundle preferences = new Bundle();
        preferences.putInt(BluetoothAdapter.AUDIO_MODE_OUTPUT_ONLY, BluetoothProfile.HEADSET);

        // Test invalid input
        assertThrows(
                NullPointerException.class, () -> mAdapter.setPreferredAudioProfiles(device, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.setPreferredAudioProfiles(device, preferences));
        assertThrows(NullPointerException.class, () -> mAdapter.getPreferredAudioProfiles(null));

        preferences.putInt(BluetoothAdapter.AUDIO_MODE_OUTPUT_ONLY, BluetoothProfile.HID_HOST);
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.setPreferredAudioProfiles(device, preferences));

        preferences.putInt(BluetoothAdapter.AUDIO_MODE_OUTPUT_ONLY, BluetoothProfile.LE_AUDIO);
        preferences.putInt(BluetoothAdapter.AUDIO_MODE_DUPLEX, BluetoothProfile.A2DP);
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.setPreferredAudioProfiles(device, preferences));

        preferences.putInt(BluetoothAdapter.AUDIO_MODE_DUPLEX, BluetoothProfile.GATT);
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.setPreferredAudioProfiles(device, preferences));

        preferences.putInt(BluetoothAdapter.AUDIO_MODE_DUPLEX, BluetoothProfile.HEADSET);

        assertThrows(
                NullPointerException.class,
                () -> mAdapter.setPreferredAudioProfiles(null, preferences));

        // Check what happens when the device is not bonded
        assertThat(mAdapter.getPreferredAudioProfiles(device).isEmpty()).isTrue();
        assertThat(mAdapter.setPreferredAudioProfiles(device, preferences))
                .isEqualTo(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED);
    }

    @Test
    public void preferredAudioProfileCallbacks() {
        assumeTrue(mHasBluetooth);

        Executor executor = mContext.getMainExecutor();
        BluetoothAdapter.PreferredAudioProfilesChangedCallback callback =
                mock(BluetoothAdapter.PreferredAudioProfilesChangedCallback.class);

        assertThrows(
                NullPointerException.class,
                () -> mAdapter.registerPreferredAudioProfilesChangedCallback(null, callback));
        assertThrows(
                NullPointerException.class,
                () -> mAdapter.registerPreferredAudioProfilesChangedCallback(executor, null));
        assertThrows(
                NullPointerException.class,
                () -> mAdapter.unregisterPreferredAudioProfilesChangedCallback(null));

        Permissions.enforceEachPermissions(
                () -> mAdapter.registerPreferredAudioProfilesChangedCallback(executor, callback),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.unregisterPreferredAudioProfilesChangedCallback(callback));

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            if (SystemProperties.getBoolean(ENABLE_DUAL_MODE_AUDIO, false)) {
                assertThat(
                                mAdapter.registerPreferredAudioProfilesChangedCallback(
                                        executor, callback))
                        .isEqualTo(BluetoothStatusCodes.SUCCESS);
                assertThat(mAdapter.unregisterPreferredAudioProfilesChangedCallback(callback))
                        .isEqualTo(BluetoothStatusCodes.SUCCESS);
            } else {
                assertThat(
                                mAdapter.registerPreferredAudioProfilesChangedCallback(
                                        executor, callback))
                        .isEqualTo(BluetoothStatusCodes.FEATURE_NOT_SUPPORTED);
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mAdapter.unregisterPreferredAudioProfilesChangedCallback(callback));
            }
        }
    }

    // CTS doesn't run with a compatible remote device.
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakePreferredAudioProfilesCallbackCoverage() {
        BluetoothAdapter.PreferredAudioProfilesChangedCallback callback =
                mock(BluetoothAdapter.PreferredAudioProfilesChangedCallback.class);
        callback.onPreferredAudioProfilesChanged(null, null, 0);
    }

    @Test
    public void bluetoothQualityReportReadyCallbacks() {
        assumeTrue(mHasBluetooth);

        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);

        Executor executor = mContext.getMainExecutor();
        BluetoothAdapter.BluetoothQualityReportReadyCallback callback =
                mock(BluetoothAdapter.BluetoothQualityReportReadyCallback.class);

        BluetoothQualityReport bqr =
                BluetoothQualityReportTest.getBqr(BluetoothQualityReport.QUALITY_REPORT_ID_MONITOR);

        assertThrows(
                NullPointerException.class,
                () -> mAdapter.registerBluetoothQualityReportReadyCallback(null, callback));
        assertThrows(
                NullPointerException.class,
                () -> mAdapter.registerBluetoothQualityReportReadyCallback(executor, null));
        assertThrows(
                NullPointerException.class,
                () -> mAdapter.unregisterBluetoothQualityReportReadyCallback(null));

        Permissions.enforceEachPermissions(
                () -> mAdapter.registerBluetoothQualityReportReadyCallback(executor, callback),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));
        assertThrows(
                IllegalArgumentException.class,
                () -> mAdapter.unregisterBluetoothQualityReportReadyCallback(callback));

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            assertThat(mAdapter.registerBluetoothQualityReportReadyCallback(executor, callback))
                    .isEqualTo(BluetoothStatusCodes.SUCCESS);
            assertThat(mAdapter.unregisterBluetoothQualityReportReadyCallback(callback))
                    .isEqualTo(BluetoothStatusCodes.SUCCESS);
        }
    }

    // CTS doesn't run with a compatible remote device.
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakeQualityReportCallbackCoverage() {
        BluetoothAdapter.BluetoothQualityReportReadyCallback callback =
                mock(BluetoothAdapter.BluetoothQualityReportReadyCallback.class);
        callback.onBluetoothQualityReportReady(null, null, 0);
    }

    @Test
    public void notifyActiveDeviceChangeApplied() {
        assumeTrue(mHasBluetooth);

        String deviceAddress = "00:11:22:AA:BB:CC";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);

        assertThrows(
                NullPointerException.class, () -> mAdapter.notifyActiveDeviceChangeApplied(null));

        assertThat(mAdapter.notifyActiveDeviceChangeApplied(device))
                .isEqualTo(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ALLOWED);
    }

    @Test
    public void getAdapterProxy() {
        assumeTrue(mHasBluetooth);
        BluetoothProfile.ServiceListener listener = mock(BluetoothProfile.ServiceListener.class);

        assertThat(mAdapter.getProfileProxy(null, listener, BluetoothProfile.A2DP)).isFalse();
        assertThat(mAdapter.getProfileProxy(mContext, null, BluetoothProfile.A2DP)).isFalse();
        assertThat(mAdapter.getProfileProxy(mContext, listener, 99)).isFalse();
    }

    private void verifyIntentReceived(
            BroadcastReceiver receiver, Duration timeout, Matcher<Intent>... matchers) {
        verify(receiver, timeout(timeout.toMillis()))
                .onReceive(any(), MockitoHamcrest.argThat(AllOf.allOf(matchers)));
    }
}
