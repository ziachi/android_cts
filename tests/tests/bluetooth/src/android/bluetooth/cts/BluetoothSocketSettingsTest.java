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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothSocketException;
import android.bluetooth.BluetoothSocketSettings;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.ApiLevelUtil;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Test for Bluetooth Socket Settings {@link BluetoothSocketSettings}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothSocketSettingsTest {
    private static final String TAG = BluetoothSocketSettingsTest.class.getSimpleName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule public final Expect expect = Expect.create();

    private static final String TEST_SERVICE_NAME = "Test";
    private static final int TEST_SOCKET_TYPE = BluetoothSocket.TYPE_RFCOMM;
    private static final UUID TEST_UUID = UUID.fromString("0000110a-0000-1000-8000-00805f9b34fb");
    private static final String FAKE_DEVICE_ADDRESS = "00:11:22:AA:BB:CC";
    private static final int FAKE_PSM = 128;
    private static final String TEST_SOCKET_NAME = "TestOffloadSocket";
    private static final long TEST_HUB_ID = 1;
    private static final long TEST_ENDPOINT_ID = 2;
    private static final int TEST_MAX_RX_PACKET_SIZE = 2048;
    private BluetoothDevice mFakeDevice;
    private static final BluetoothAdapter sAdapter = BlockingBluetoothAdapter.getAdapter();

    @Before
    public void setUp() {
        TestUtils.dropPermissionAsShellUid();
        assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.VANILLA_ICE_CREAM));
        assumeTrue(
                TestUtils.isBleSupported(
                        InstrumentationRegistry.getInstrumentation().getContext()));
        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        mFakeDevice = sAdapter.getRemoteDevice(FAKE_DEVICE_ADDRESS);
    }

    /* Helper utilities */
    private void createServerSocketUsingSettings(BluetoothSocketSettings settings)
            throws IOException {
        assertThrows(SecurityException.class, () -> sAdapter.listenUsingSocketSettings(settings));
        final BluetoothServerSocket serverSocket;
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            serverSocket = sAdapter.listenUsingSocketSettings(settings);
        }
        assertThat(serverSocket).isNotNull();
        serverSocket.close();
    }

    private void createServerSocketUsingSettings(
            BluetoothSocketSettings settings, List<String> requiredPermissions) throws IOException {
        Permissions.enforceEachPermissions(
                () -> {
                    try {
                        sAdapter.listenUsingSocketSettings(settings);
                        return true;
                    } catch (IOException e) {
                        return false;
                    }
                },
                requiredPermissions);
        final BluetoothServerSocket serverSocket;
        try (var p = Permissions.withPermissions(requiredPermissions.toArray(new String[0]))) {
            serverSocket = sAdapter.listenUsingSocketSettings(settings);
        }
        assertThat(serverSocket).isNotNull();
        serverSocket.close();
    }

    private void createClientSocketUsingSettings(BluetoothSocketSettings settings)
            throws IOException {
        final BluetoothSocket socket;
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            socket = mFakeDevice.createUsingSocketSettings(settings);
        }
        assertThat(socket).isNotNull();
        // should be in disconnected state
        assertThat(socket.isConnected()).isFalse();
        socket.close();
    }

    private BluetoothSocket getClientSocketUsingSettings(BluetoothSocketSettings settings)
            throws IOException {
        final BluetoothSocket socket;
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            socket = mFakeDevice.createUsingSocketSettings(settings);
        }
        assertThat(socket).isNotNull();
        return socket;
    }

    private boolean isLeCocSocketOffloadSupported() {
        boolean result;
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            result = sAdapter.isLeCocSocketOffloadSupported();
        }
        return result;
    }

    private boolean isRfcommSocketOffloadSupported() {
        boolean result;
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            result = sAdapter.isRfcommSocketOffloadSupported();
        }
        return result;
    }

    /* BluetoothSocketSettings interface related tests */
    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createBluetoothSocketSettingsFromBuilder() {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(TEST_SOCKET_TYPE)
                        .setRfcommServiceName(TEST_SERVICE_NAME)
                        .setRfcommUuid(TEST_UUID);

        BluetoothSocketSettings settings = builder.build();
        expect.that(settings.getSocketType()).isEqualTo(TEST_SOCKET_TYPE);
        expect.that(settings.getRfcommServiceName()).isEqualTo(TEST_SERVICE_NAME);
        expect.that(settings.getRfcommUuid()).isEqualTo(TEST_UUID);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createBluetoothLeCoCSocketSettingsFromBuilder() {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_LE)
                        .setL2capPsm(FAKE_PSM);

        BluetoothSocketSettings settings = builder.build();
        expect.that(settings.getSocketType()).isEqualTo(BluetoothSocket.TYPE_LE);
        expect.that(settings.getL2capPsm()).isEqualTo(FAKE_PSM);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createBluetoothOffloadSocketSettingsFromBuilder() {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_LE)
                        .setEncryptionRequired(false)
                        .setAuthenticationRequired(false)
                        .setDataPath(BluetoothSocketSettings.DATA_PATH_HARDWARE_OFFLOAD)
                        .setSocketName(TEST_SOCKET_NAME)
                        .setHubId(TEST_HUB_ID)
                        .setEndpointId(TEST_ENDPOINT_ID)
                        .setRequestedMaximumPacketSize(TEST_MAX_RX_PACKET_SIZE);

        BluetoothSocketSettings settings = builder.build();
        expect.that(settings.getSocketType()).isEqualTo(BluetoothSocket.TYPE_LE);
        expect.that(settings.isEncryptionRequired()).isFalse();
        expect.that(settings.isAuthenticationRequired()).isFalse();
        expect.that(settings.getDataPath())
                .isEqualTo(BluetoothSocketSettings.DATA_PATH_HARDWARE_OFFLOAD);
        expect.that(settings.getSocketName()).isEqualTo(TEST_SOCKET_NAME);
        expect.that(settings.getHubId()).isEqualTo(TEST_HUB_ID);
        expect.that(settings.getEndpointId()).isEqualTo(TEST_ENDPOINT_ID);
        expect.that(settings.getRequestedMaximumPacketSize()).isEqualTo(TEST_MAX_RX_PACKET_SIZE);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void illegalArgumentsToBuilder() {
        BluetoothSocketSettings.Builder builder = new BluetoothSocketSettings.Builder();

        // No support for sockets of TYPE_L2CAP
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.setSocketType(BluetoothSocket.TYPE_L2CAP)
                                .setL2capPsm(FAKE_PSM)
                                .build());

        // No support for sockets of TYPE_SCO
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.setSocketType(BluetoothSocket.TYPE_SCO)
                                .setL2capPsm(FAKE_PSM)
                                .build());

        // Building Socket settings of TYPE_RFCOMM with L2CAP psm is not allowed
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.setSocketType(BluetoothSocket.TYPE_RFCOMM)
                                .setL2capPsm(FAKE_PSM)
                                .build());

        // Building Socket settings of TYPE_LE with Rfcomm UUID is not allowed
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.setSocketType(BluetoothSocket.TYPE_LE)
                                .setRfcommUuid(TEST_UUID)
                                .build());

        // Building Socket settings of TYPE_LE with L2CAP PSM not in the valid range (128 to 255)
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.setSocketType(BluetoothSocket.TYPE_LE).setL2capPsm(0).build());
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void illegalArgumentsForOffloadSocketToBuilder() {
        BluetoothSocketSettings.Builder builder = new BluetoothSocketSettings.Builder();
        // Building Socket settings of DATA_PATH_HARDWARE_OFFLOAD requires to set hub ID and
        // endpoint ID
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.setSocketType(BluetoothSocket.TYPE_LE)
                                .setDataPath(BluetoothSocketSettings.DATA_PATH_HARDWARE_OFFLOAD)
                                .setHubId(TEST_HUB_ID)
                                .build());

        // Building Socket settings of DATA_PATH_HARDWARE_OFFLOAD with max packet size not in the
        // valid range. The valid max packet size should not be smaller than 0.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.setSocketType(BluetoothSocket.TYPE_LE)
                                .setDataPath(BluetoothSocketSettings.DATA_PATH_HARDWARE_OFFLOAD)
                                .setHubId(TEST_HUB_ID)
                                .setEndpointId(TEST_ENDPOINT_ID)
                                .setRequestedMaximumPacketSize(-1)
                                .build());
    }

    /* Server socket creation related tests : BluetoothAdapter#listenUsingSocketSettings*/
    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createListeningInsecureRfcommSocket() throws IOException {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_RFCOMM)
                        .setEncryptionRequired(false)
                        .setAuthenticationRequired(false)
                        .setRfcommUuid(TEST_UUID);
        BluetoothSocketSettings settings = builder.build();
        createServerSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createListeningEncryptOnlyRfcommSocket() throws IOException {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_RFCOMM)
                        .setEncryptionRequired(true)
                        .setAuthenticationRequired(false)
                        .setRfcommServiceName(TEST_SERVICE_NAME)
                        .setRfcommUuid(TEST_UUID);
        BluetoothSocketSettings settings = builder.build();
        createServerSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createListeningEncryptedAndAuthenticatedRfcommSocket() throws IOException {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_RFCOMM)
                        .setEncryptionRequired(true)
                        .setAuthenticationRequired(true)
                        .setRfcommServiceName(TEST_SERVICE_NAME)
                        .setRfcommUuid(TEST_UUID);
        BluetoothSocketSettings settings = builder.build();
        createServerSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createListeningInsecureLeCocSocket() throws IOException {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_LE)
                        .setEncryptionRequired(false)
                        .setAuthenticationRequired(false);

        BluetoothSocketSettings settings = builder.build();
        createServerSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createListeningEncryptOnlyLeCocSocket() throws IOException {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_LE)
                        .setEncryptionRequired(true)
                        .setAuthenticationRequired(false);

        BluetoothSocketSettings settings = builder.build();
        createServerSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createListeningEncryptedAndAuthenticatedLeCocSocket() throws IOException {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_LE)
                        .setEncryptionRequired(true)
                        .setAuthenticationRequired(true);

        BluetoothSocketSettings settings = builder.build();
        createServerSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createListeningInsecureRfcommOffloadSocket() throws IOException {
        assumeTrue(isRfcommSocketOffloadSupported());
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_RFCOMM)
                        .setEncryptionRequired(false)
                        .setAuthenticationRequired(false)
                        .setRfcommUuid(TEST_UUID)
                        .setDataPath(BluetoothSocketSettings.DATA_PATH_HARDWARE_OFFLOAD)
                        .setSocketName(TEST_SOCKET_NAME)
                        .setHubId(TEST_HUB_ID)
                        .setEndpointId(TEST_ENDPOINT_ID)
                        .setRequestedMaximumPacketSize(TEST_MAX_RX_PACKET_SIZE);

        BluetoothSocketSettings settings = builder.build();
        createServerSocketUsingSettings(settings, List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createListeningInsecureLeCocOffloadSocket() throws IOException {
        assumeTrue(isLeCocSocketOffloadSupported());
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_LE)
                        .setEncryptionRequired(false)
                        .setAuthenticationRequired(false)
                        .setDataPath(BluetoothSocketSettings.DATA_PATH_HARDWARE_OFFLOAD)
                        .setSocketName(TEST_SOCKET_NAME)
                        .setHubId(TEST_HUB_ID)
                        .setEndpointId(TEST_ENDPOINT_ID)
                        .setRequestedMaximumPacketSize(TEST_MAX_RX_PACKET_SIZE);

        BluetoothSocketSettings settings = builder.build();
        createServerSocketUsingSettings(settings, List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));
    }

    /* Client socket creation related tests : BluetoothDevice#createUsingSocketSettings */
    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createClientInsecureRfcommSocket() throws IOException {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_RFCOMM)
                        .setEncryptionRequired(false)
                        .setAuthenticationRequired(false)
                        .setRfcommServiceName(TEST_SERVICE_NAME)
                        .setRfcommUuid(TEST_UUID);
        BluetoothSocketSettings settings = builder.build();
        createClientSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createClientEncryptOnlyRfcommSocket() throws IOException {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_RFCOMM)
                        .setEncryptionRequired(true)
                        .setAuthenticationRequired(false)
                        .setRfcommServiceName(TEST_SERVICE_NAME)
                        .setRfcommUuid(TEST_UUID);
        BluetoothSocketSettings settings = builder.build();
        createClientSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createClientEncryptedAndAuthenticatedRfcommSocket() throws IOException {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_RFCOMM)
                        .setEncryptionRequired(true)
                        .setAuthenticationRequired(true)
                        .setRfcommServiceName(TEST_SERVICE_NAME)
                        .setRfcommUuid(TEST_UUID);
        BluetoothSocketSettings settings = builder.build();
        createClientSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createClientInsecureLeCocSocket() throws IOException {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_LE)
                        .setEncryptionRequired(false)
                        .setL2capPsm(FAKE_PSM)
                        .setAuthenticationRequired(false);

        BluetoothSocketSettings settings = builder.build();
        createClientSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createClientEncryptOnlyLeCocSocket() throws IOException {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_LE)
                        .setEncryptionRequired(true)
                        .setL2capPsm(FAKE_PSM)
                        .setAuthenticationRequired(false);

        BluetoothSocketSettings settings = builder.build();
        createClientSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createClientEncryptedAndAuthenticatedLeCocSocket() throws IOException {
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_LE)
                        .setEncryptionRequired(true)
                        .setL2capPsm(FAKE_PSM)
                        .setAuthenticationRequired(true);

        BluetoothSocketSettings settings = builder.build();
        createClientSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createClientInsecureRfcommOffloadSocket() throws IOException {
        assumeTrue(isRfcommSocketOffloadSupported());
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_RFCOMM)
                        .setEncryptionRequired(false)
                        .setAuthenticationRequired(false)
                        .setRfcommServiceName(TEST_SERVICE_NAME)
                        .setRfcommUuid(TEST_UUID)
                        .setDataPath(BluetoothSocketSettings.DATA_PATH_HARDWARE_OFFLOAD)
                        .setSocketName(TEST_SOCKET_NAME)
                        .setHubId(TEST_HUB_ID)
                        .setEndpointId(TEST_ENDPOINT_ID)
                        .setRequestedMaximumPacketSize(TEST_MAX_RX_PACKET_SIZE);

        BluetoothSocketSettings settings = builder.build();
        createClientSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void createClientInsecureLeCocOffloadSocket() throws IOException {
        assumeTrue(isLeCocSocketOffloadSupported());
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_LE)
                        .setEncryptionRequired(false)
                        .setL2capPsm(FAKE_PSM)
                        .setAuthenticationRequired(false)
                        .setDataPath(BluetoothSocketSettings.DATA_PATH_HARDWARE_OFFLOAD)
                        .setSocketName(TEST_SOCKET_NAME)
                        .setHubId(TEST_HUB_ID)
                        .setEndpointId(TEST_ENDPOINT_ID)
                        .setRequestedMaximumPacketSize(TEST_MAX_RX_PACKET_SIZE);

        BluetoothSocketSettings settings = builder.build();
        createClientSocketUsingSettings(settings);
    }

    @RequiresFlagsEnabled(Flags.FLAG_SOCKET_SETTINGS_API)
    @Test
    public void testRetrievingSocketIdOnDisconnectedSocket() throws IOException {
        assumeTrue(isLeCocSocketOffloadSupported());
        BluetoothSocketSettings.Builder builder =
                new BluetoothSocketSettings.Builder()
                        .setSocketType(BluetoothSocket.TYPE_LE)
                        .setEncryptionRequired(false)
                        .setL2capPsm(FAKE_PSM)
                        .setAuthenticationRequired(false)
                        .setDataPath(BluetoothSocketSettings.DATA_PATH_HARDWARE_OFFLOAD)
                        .setSocketName(TEST_SOCKET_NAME)
                        .setHubId(TEST_HUB_ID)
                        .setEndpointId(TEST_ENDPOINT_ID)
                        .setRequestedMaximumPacketSize(TEST_MAX_RX_PACKET_SIZE);

        BluetoothSocketSettings settings = builder.build();
        BluetoothSocket sock = getClientSocketUsingSettings(settings);
        assertThrows(BluetoothSocketException.class, () -> sock.getSocketId());
        sock.close();
    }
}
