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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHapClient;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.content.Context;
import android.sysprop.BluetoothProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
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
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothHapClientTest {
    private static final String TAG = BluetoothHapClientTest.class.getSimpleName();

    @Mock private BluetoothProfile.ServiceListener mListener;

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final BluetoothDevice mDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    private BluetoothHapClient mService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        assumeTrue(SdkLevel.isAtLeastT());
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH_LE));
        assumeTrue(BluetoothProperties.isProfileHapClientEnabled().orElse(false));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        assertThat(mAdapter.getProfileProxy(mContext, mListener, BluetoothProfile.HAP_CLIENT))
                .isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(BluetoothProfile.HAP_CLIENT), captor.capture());
        mService = (BluetoothHapClient) captor.getValue();
        assertThat(mService).isNotNull();

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @After
    public void tearDown() throws Exception {
        mAdapter.closeProfileProxy(BluetoothProfile.HAP_CLIENT, mService);
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void closeProfileProxy() {
        mService.close();
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.HAP_CLIENT));
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
        assertThat(mService.getConnectionState(null)).isEqualTo(STATE_DISCONNECTED);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        assertThat(mService.getConnectionState(mDevice)).isEqualTo(STATE_DISCONNECTED);
    }

    /** Verify getHapGroup() return -1 if Bluetooth is disabled. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getHapGroup() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        assertThat(mService.getHapGroup(mDevice)).isEqualTo(-1);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getActivePresetIndex() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns 0 if bluetooth is not enabled
        assertThat(mService.getActivePresetIndex(mDevice)).isEqualTo(0);
    }

    /** Verify getActivePresetInfo() return null if Bluetooth is disabled. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getActivePresetInfo() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        assertThat(mService.getActivePresetInfo(mDevice)).isNull();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void selectPreset() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.selectPreset(mDevice, 1);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void selectPresetForGroup() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.selectPresetForGroup(1, 1);
    }

    /** Verify switchToNextPreset() will not cause exception when Bluetooth is disabled. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void switchToNextPreset() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.switchToNextPreset(mDevice);
    }

    /** Verify switchToNextPresetForGroup() will not cause exception when Bluetooth is disabled. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void switchToNextPresetForGroup() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.switchToNextPresetForGroup(1);
    }

    /** Verify switchToPreviousPreset() doesn't cause exception when Bluetooth is disabled. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void switchToPreviousPreset() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.switchToPreviousPreset(mDevice);
    }

    /**
     * Verify switchToPreviousPresetForGroup() doesn't cause exception when Bluetooth is disabled.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void switchToPreviousPresetForGroup() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.switchToPreviousPresetForGroup(1);
    }

    /** Verify switchToNextPresetForGroup() doesn't cause exception when Bluetooth is disabled. */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getPresetInfo() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns null if bluetooth is not enabled
        assertThat(mService.getPresetInfo(mDevice, 1)).isNull();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getAllPresetInfo() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getAllPresetInfo(mDevice)).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void setPresetName() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.setPresetName(mDevice, 1, "New Name");
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void setPresetNameForGroup() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        mService.setPresetNameForGroup(1, 1, "New Name");
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void setGetConnectionPolicy() {
        assertThrows(NullPointerException.class, () -> mService.setConnectionPolicy(null, 0));
        assertThat(mService.getConnectionPolicy(null)).isEqualTo(CONNECTION_POLICY_FORBIDDEN);

        assertThat(mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_FORBIDDEN)).isTrue();

        TestUtils.dropPermissionAsShellUid();
        assertThrows(
                SecurityException.class,
                () -> mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_FORBIDDEN));
        assertThrows(SecurityException.class, () -> mService.getConnectionPolicy(mDevice));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void registerUnregisterCallback() {
        Executor executor = mContext.getMainExecutor();

        BluetoothHapClient.Callback mockCallback = mock(BluetoothHapClient.Callback.class);

        // Verify parameter
        assertThrows(
                NullPointerException.class, () -> mService.registerCallback(null, mockCallback));
        assertThrows(NullPointerException.class, () -> mService.registerCallback(executor, null));
        assertThrows(NullPointerException.class, () -> mService.unregisterCallback(null));

        // Verify valid parameters
        mService.registerCallback(executor, mockCallback);
        mService.unregisterCallback(mockCallback);

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class, () -> mService.registerCallback(executor, mockCallback));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void registerCallbackNoPermission() {
        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        BluetoothHapClient.Callback mockCallback = mock(BluetoothHapClient.Callback.class);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mService.registerCallback(mContext.getMainExecutor(), mockCallback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @Test
    // CTS doesn't run with a compatible remote device.
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakeCallbackCoverage() {
        BluetoothHapClient.Callback mockCallback = mock(BluetoothHapClient.Callback.class);

        mockCallback.onPresetInfoChanged(null, null, 0);
        mockCallback.onPresetSelected(null, 0, 0);
        mockCallback.onPresetSelectionFailed(null, 0);
        mockCallback.onPresetSelectionForGroupFailed(0, 0);
        mockCallback.onSetPresetNameFailed(null, 0);
        mockCallback.onSetPresetNameForGroupFailed(0, 0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getHearingAidType() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns 0x00 if bluetooth is not enabled
        assertThat(mService.getHearingAidType(mDevice)).isEqualTo(0x00);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void supportsSynchronizedPresets() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.supportsSynchronizedPresets(mDevice)).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void supportsIndependentPresets() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.supportsIndependentPresets(mDevice)).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void supportsDynamicPresets() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.supportsDynamicPresets(mDevice)).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void supportsWritablePresets() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.supportsWritablePresets(mDevice)).isFalse();
    }
}
