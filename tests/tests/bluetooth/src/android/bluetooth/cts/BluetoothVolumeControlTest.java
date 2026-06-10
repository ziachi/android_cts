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
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_ALLOWED;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.AudioInputControl;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothVolumeControl;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.content.Context;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.sysprop.BluetoothProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class BluetoothVolumeControlTest {
    private static final String TAG = BluetoothVolumeControlTest.class.getSimpleName();

    @Mock private BluetoothVolumeControl.Callback mCallback;
    @Mock private BluetoothProfile.ServiceListener mListener;

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final BluetoothDevice mDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final Executor mExecutor = mContext.getMainExecutor();

    private BluetoothVolumeControl mService;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH));

        /* If Le Audio is supported then Volume Control shall be supported */
        assertThat(
                        !BluetoothProperties.isProfileBapUnicastClientEnabled().orElse(false)
                                || BluetoothProperties.isProfileVcpControllerEnabled()
                                        .orElse(false))
                .isTrue();
        assumeTrue(BluetoothProperties.isProfileVcpControllerEnabled().orElse(false));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        assertThat(mAdapter.getProfileProxy(mContext, mListener, BluetoothProfile.VOLUME_CONTROL))
                .isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(BluetoothProfile.VOLUME_CONTROL), captor.capture());
        mService = (BluetoothVolumeControl) captor.getValue();
        assertThat(mService).isNotNull();

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @After
    public void tearDown() {
        mAdapter.closeProfileProxy(BluetoothProfile.VOLUME_CONTROL, mService);
        TestUtils.dropPermissionAsShellUid();
    }

    @Test
    public void closeProfileProxy() {
        mService.close();
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.VOLUME_CONTROL));
    }

    @Test
    public void getConnectedDevices() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getConnectedDevices()).isEmpty();
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getDevicesMatchingConnectionStates(null)).isEmpty();
    }

    @Test
    public void registerUnregisterCallback() {
        // Verify parameter
        assertThrows(NullPointerException.class, () -> mService.registerCallback(null, mCallback));
        assertThrows(NullPointerException.class, () -> mService.registerCallback(mExecutor, null));
        assertThrows(NullPointerException.class, () -> mService.unregisterCallback(null));

        // Test success register unregister
        mService.registerCallback(mExecutor, mCallback);
        mService.unregisterCallback(mCallback);

        TestUtils.dropPermissionAsShellUid();
        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class, () -> mService.registerCallback(mExecutor, mCallback));
    }

    @Test
    // CTS doesn't run with a compatible remote device.
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakeCallbackCoverage() {
        mCallback.onVolumeOffsetChanged(null, 0);
        mCallback.onVolumeOffsetChanged(null, 0, 0);
        mCallback.onVolumeOffsetAudioLocationChanged(null, 0, 0);
        mCallback.onVolumeOffsetAudioDescriptionChanged(null, 0, "foo");
        mCallback.onDeviceVolumeChanged(null, 0);
    }

    @Test
    public void setVolumeOffset() {
        mService.setVolumeOffset(mDevice, 0);

        enforceConnectAndPrivileged(() -> mService.setVolumeOffset(mDevice, 0));
        enforceConnectAndPrivileged(() -> mService.setVolumeOffset(mDevice, 0, 0));
    }

    @Test
    public void setDeviceVolume() {
        int testVolume = 43;

        mService.setDeviceVolume(mDevice, testVolume, true);
        mService.setDeviceVolume(mDevice, testVolume, false);

        // volume expect in range [0, 255]
        assertThrows(
                IllegalArgumentException.class, () -> mService.setDeviceVolume(mDevice, -1, true));
        assertThrows(
                IllegalArgumentException.class, () -> mService.setDeviceVolume(mDevice, 256, true));

        enforceConnectAndPrivileged(() -> mService.setDeviceVolume(mDevice, testVolume, true));
    }

    @Test
    public void isVolumeOffsetAvailable() {
        enforceConnectAndPrivileged(() -> mService.isVolumeOffsetAvailable(mDevice));

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        assertThat(mService.isVolumeOffsetAvailable(mDevice)).isFalse();
    }

    @Test
    public void getNumberOfVolumeOffsetInstances() {
        enforceConnectAndPrivileged(() -> mService.getNumberOfVolumeOffsetInstances(mDevice));

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns 0 if bluetooth is not enabled
        assertThat(mService.getNumberOfVolumeOffsetInstances(mDevice)).isEqualTo(0);
    }

    @Test
    public void getConnectionState() {
        // Verify returns false when invalid input is given
        assertThat(mService.getConnectionState(null)).isEqualTo(STATE_DISCONNECTED);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.getConnectionState(mDevice)).isEqualTo(STATE_DISCONNECTED);
    }

    @Test
    public void getConnectionPolicy() {
        // Verify returns false when invalid input is given
        assertThat(mService.getConnectionPolicy(null)).isEqualTo(CONNECTION_POLICY_FORBIDDEN);

        enforceConnectAndPrivileged(() -> mService.getConnectionPolicy(mDevice));

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.getConnectionPolicy(mDevice)).isEqualTo(CONNECTION_POLICY_FORBIDDEN);
    }

    @Test
    public void setConnectionPolicy() {
        // Verify returns false when invalid input is given
        assertThat(mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_UNKNOWN)).isFalse();
        assertThat(mService.setConnectionPolicy(null, CONNECTION_POLICY_ALLOWED)).isFalse();

        enforceConnectAndPrivileged(
                () -> mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_ALLOWED));
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_FORBIDDEN)).isFalse();
    }

    @Test
    public void getAudioInputControlServices() {
        assertThrows(NullPointerException.class, () -> mService.getAudioInputControlServices(null));

        Permissions.enforceEachPermissions(
                () -> mService.getAudioInputControlServices(mDevice),
                List.of(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED));

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            assertThat(mService.getAudioInputControlServices(mDevice)).isNotNull();
        }
    }

    // CTS doesn't run with a compatible remote device.
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakeAicsCallbackCoverage() {
        AudioInputControl aics = mock(AudioInputControl.class);
        aics.registerCallback(null, null);
        aics.unregisterCallback(null);
        aics.getAudioInputType();
        aics.getGainSettingUnit();
        aics.getGainSettingMin();
        aics.getGainSettingMax();
        aics.getDescription();
        aics.isDescriptionWritable();
        aics.setDescription(null);
        aics.getAudioInputStatus();
        aics.getGainSetting();
        aics.setGainSetting(0);
        aics.getGainMode();
        aics.setGainMode(0);
        aics.getMute();
        aics.setMute(0);
        AudioInputControl.AudioInputCallback aicsCallback =
                mock(AudioInputControl.AudioInputCallback.class);
        aicsCallback.onDescriptionChanged(null);
        aicsCallback.onAudioInputStatusChanged(0);
        aicsCallback.onGainSettingChanged(0);
        aicsCallback.onSetGainSettingFailed();
        aicsCallback.onMuteChanged(0);
        aicsCallback.onSetMuteFailed();
        aicsCallback.onGainModeChanged(0);
        aicsCallback.onSetGainModeFailed();
    }

    private void enforceConnectAndPrivileged(ThrowingRunnable runnable) {
        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
        assertThrows(SecurityException.class, runnable);

        // Verify throws SecurityException without permission.BLUETOOTH_CONNECT
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_PRIVILEGED);
        assertThrows(SecurityException.class, runnable);
    }
}
