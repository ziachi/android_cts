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
import static android.Manifest.permission.MODIFY_PHONE_STATE;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_ALLOWED;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.content.Context;
import android.sysprop.BluetoothProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothHeadsetTest {
    private static final String TAG = BluetoothHeadsetTest.class.getSimpleName();

    @Mock private BluetoothProfile.ServiceListener mListener;

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final BluetoothDevice mDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private BluetoothHeadset mService;
    private boolean mHasBluetooth;
    private boolean mIsHeadsetSupported;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH));
        assumeTrue(BluetoothProperties.isProfileHfpAgEnabled().orElse(false));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        assertThat(mAdapter.getProfileProxy(mContext, mListener, BluetoothProfile.HEADSET))
                .isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(BluetoothProfile.HEADSET), captor.capture());
        mService = (BluetoothHeadset) captor.getValue();
        assertThat(mService).isNotNull();

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    @After
    public void tearDown() {
        mAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mService);
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void closeProfileProxy() {
        mAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mService);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.HEADSET));
    }

    @Test
    public void getConnectedDevices() {
        assertThat(mService.getConnectedDevices()).isEmpty();
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThat(mService.getDevicesMatchingConnectionStates(new int[] {STATE_CONNECTED}))
                .isEmpty();
    }

    @Test
    public void getConnectionState() {
        assertThat(mService.getConnectionState(mDevice)).isEqualTo(STATE_DISCONNECTED);
    }

    @Test
    public void isAudioConnected() {
        assertThat(mService.isAudioConnected(mDevice)).isFalse();
        assertThat(mService.isAudioConnected(null)).isFalse();

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.isAudioConnected(mDevice)).isFalse();
    }

    @Test
    public void isNoiseReductionSupported() {
        assertThat(mService.isNoiseReductionSupported(mDevice)).isFalse();
        assertThat(mService.isNoiseReductionSupported(null)).isFalse();

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.isNoiseReductionSupported(mDevice)).isFalse();
    }

    @Test
    public void isVoiceRecognitionSupported() {
        assertThat(mService.isVoiceRecognitionSupported(mDevice)).isFalse();
        assertThat(mService.isVoiceRecognitionSupported(null)).isFalse();

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.isVoiceRecognitionSupported(mDevice)).isFalse();
    }

    @Test
    public void sendVendorSpecificResultCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mService.sendVendorSpecificResultCode(mDevice, null, null));

        assertThat(mService.sendVendorSpecificResultCode(mDevice, "", "")).isFalse();
        assertThat(mService.sendVendorSpecificResultCode(null, "", "")).isFalse();

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.sendVendorSpecificResultCode(mDevice, "", "")).isFalse();
    }

    @Test
    public void connect() {
        // Verify returns false when invalid input is given
        assertThat(mService.connect(null)).isFalse();

        // Verify it returns false for a device that has CONNECTION_POLICY_FORBIDDEN
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, MODIFY_PHONE_STATE);
        assertThat(mService.connect(mDevice)).isFalse();

        // Verify returns false if bluetooth is not enabled
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.connect(mDevice)).isFalse();
    }

    @Test
    public void disconnect() {
        // Verify returns false when invalid input is given
        assertThat(mService.disconnect(null)).isFalse();

        // Verify it returns false for a device that has CONNECTION_POLICY_FORBIDDEN
        assertThat(mService.disconnect(mDevice)).isFalse();

        // Verify returns false if bluetooth is not enabled
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.disconnect(mDevice)).isFalse();
    }

    @Test
    public void getConnectionPolicy() {
        // Verify returns false when invalid input is given
        assertThat(mService.getConnectionPolicy(null)).isEqualTo(CONNECTION_POLICY_FORBIDDEN);

        // Verify returns CONNECTION_POLICY_FORBIDDEN if bluetooth is not enabled
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.getConnectionPolicy(mDevice)).isEqualTo(CONNECTION_POLICY_FORBIDDEN);
    }

    @Test
    public void setConnectionPolicy() {
        // Verify returns false when invalid input is given
        assertThat(mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_UNKNOWN)).isFalse();
        assertThat(mService.setConnectionPolicy(null, CONNECTION_POLICY_ALLOWED)).isFalse();

        // Verify returns false if bluetooth is not enabled
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_FORBIDDEN)).isFalse();
    }

    @Test
    public void getAudioState() {
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThrows(NullPointerException.class, () -> mService.getAudioState(null));

        assertThat(mService.getAudioState(mDevice))
                .isEqualTo(BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
    }

    @Test
    public void connectAudio() {
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(mService.connectAudio()).isEqualTo(BluetoothStatusCodes.ERROR_NO_ACTIVE_DEVICES);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.connectAudio())
                .isEqualTo(BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND);
    }

    @Test
    public void disconnectAudio() {
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(mService.disconnectAudio())
                .isEqualTo(BluetoothStatusCodes.ERROR_NO_ACTIVE_DEVICES);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.disconnectAudio())
                .isEqualTo(BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND);
    }

    @Test
    public void startScoUsingVirtualVoiceCall() {
        mUiAutomation.adoptShellPermissionIdentity(
                BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED, MODIFY_PHONE_STATE);

        assertThat(mService.startScoUsingVirtualVoiceCall()).isFalse();
    }

    @Test
    public void stopScoUsingVirtualVoiceCall() {
        mUiAutomation.adoptShellPermissionIdentity(
                BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED, MODIFY_PHONE_STATE);

        assertThat(mService.stopScoUsingVirtualVoiceCall()).isFalse();
    }

    @Test
    public void isInbandRingingEnabled() {
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        assertThat(mService.isInbandRingingEnabled()).isFalse();
    }

    @Test
    public void setGetAudioRouteAllowed() {
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(mService.setAudioRouteAllowed(true)).isEqualTo(BluetoothStatusCodes.SUCCESS);
        assertThat(mService.getAudioRouteAllowed()).isEqualTo(BluetoothStatusCodes.ALLOWED);

        assertThat(mService.setAudioRouteAllowed(false)).isEqualTo(BluetoothStatusCodes.SUCCESS);
        assertThat(mService.getAudioRouteAllowed()).isEqualTo(BluetoothStatusCodes.NOT_ALLOWED);
    }
}
