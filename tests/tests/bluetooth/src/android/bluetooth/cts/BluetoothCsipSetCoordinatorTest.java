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
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCsipSetCoordinator;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.content.Context;
import android.sysprop.BluetoothProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class BluetoothCsipSetCoordinatorTest {
    private static final String TAG = BluetoothCsipSetCoordinatorTest.class.getSimpleName();

    @Mock private BluetoothCsipSetCoordinator.ClientLockCallback mCallback;
    @Mock private BluetoothProfile.ServiceListener mListener;

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);
    private static final Duration CALLBACK_TIMEOUT = Duration.ofMillis(500);

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final BluetoothDevice mDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final Executor mExecutor = mContext.getMainExecutor();

    private BluetoothCsipSetCoordinator mService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        assumeTrue(SdkLevel.isAtLeastT());
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH_LE));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        assumeTrue(BluetoothProperties.isProfileBapUnicastClientEnabled().orElse(false));
        assertThat(mAdapter.isLeAudioSupported()).isEqualTo(BluetoothStatusCodes.FEATURE_SUPPORTED);

        assumeTrue(BluetoothProperties.isProfileCsipSetCoordinatorEnabled().orElse(false));

        assertThat(
                        mAdapter.getProfileProxy(
                                mContext, mListener, BluetoothProfile.CSIP_SET_COORDINATOR))
                .isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(BluetoothProfile.CSIP_SET_COORDINATOR), captor.capture());
        mService = (BluetoothCsipSetCoordinator) captor.getValue();
        assertThat(mService).isNotNull();

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @After
    public void tearDown() {
        mAdapter.closeProfileProxy(BluetoothProfile.CSIP_SET_COORDINATOR, mService);
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void closeProfileProxy() {
        mService.close();
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.CSIP_SET_COORDINATOR));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectedDevices() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getConnectedDevices()).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getDevicesMatchingConnectionStates(null)).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectionState() {
        assertThat(mService.getConnectionState(mDevice)).isEqualTo(STATE_DISCONNECTED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getGroupUuidMapByDevice() {
        TestUtils.dropPermissionAsShellUid();
        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mService.getGroupUuidMapByDevice(mDevice));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        assertThat(mService.getGroupUuidMapByDevice(mDevice)).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void lockUnlockGroup() {
        int groupId = 1;
        // Verify parameter
        assertThrows(
                NullPointerException.class, () -> mService.lockGroup(groupId, null, mCallback));
        assertThrows(
                NullPointerException.class, () -> mService.lockGroup(groupId, mExecutor, null));

        TestUtils.dropPermissionAsShellUid();
        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class, () -> mService.lockGroup(groupId, mExecutor, mCallback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Lock group
        boolean isLocked = false;
        int opStatus = BluetoothStatusCodes.ERROR_CSIP_INVALID_GROUP_ID;
        mService.lockGroup(groupId, mExecutor, mCallback);

        verify(mCallback, timeout(CALLBACK_TIMEOUT.toMillis()))
                .onGroupLockSet(groupId, opStatus, isLocked);

        long uuidLsb = 0x01;
        long uuidMsb = 0x01;
        UUID uuid = new UUID(uuidMsb, uuidLsb);
        mService.unlockGroup(uuid);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getAllGroupIds() {
        TestUtils.dropPermissionAsShellUid();
        assertThrows(SecurityException.class, () -> mService.getAllGroupIds(BluetoothUuid.CAP));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.getAllGroupIds(null)).isEmpty();
    }

    @Test
    public void setGetConnectionPolicy() {
        Permissions.enforceEachPermissions(
                () -> mService.getConnectionPolicy(mDevice),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));
        Permissions.enforceEachPermissions(
                () -> mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_FORBIDDEN),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            assertThat(mService.getConnectionPolicy(null)).isEqualTo(CONNECTION_POLICY_FORBIDDEN);
            assertThat(mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_FORBIDDEN)).isTrue();
        }
    }
}
