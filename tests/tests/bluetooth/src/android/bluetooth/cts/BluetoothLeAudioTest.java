/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.CddTest;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class BluetoothLeAudioTest {
    private static final String TAG = BluetoothLeAudioTest.class.getSimpleName();

    @Rule public final CheckFlagsRule mFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock private BluetoothLeAudio.Callback mCallback;
    @Mock private BluetoothProfile.ServiceListener mListener;

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final BluetoothDevice mDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final Executor mExecutor = mContext.getMainExecutor();

    private BluetoothLeAudio mService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        assumeTrue(SdkLevel.isAtLeastT());
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH_LE));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        assumeTrue(mAdapter.isLeAudioSupported() == BluetoothStatusCodes.FEATURE_SUPPORTED);

        assertThat(mAdapter.getProfileProxy(mContext, mListener, BluetoothProfile.LE_AUDIO))
                .isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(BluetoothProfile.LE_AUDIO), captor.capture());
        mService = (BluetoothLeAudio) captor.getValue();
        assertThat(mService).isNotNull();

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @After
    public void tearDown() {
        mAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO, mService);
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void closeProfileProxy() {
        mService.close();
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.LE_AUDIO));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getConnectedDevices() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getConnectedDevices()).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getDevicesMatchingConnectionStates(null)).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getConnectionState() {
        // Verify returns false when invalid input is given
        assertThat(mService.getConnectionState(null)).isEqualTo(STATE_DISCONNECTED);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.getConnectionState(mDevice)).isEqualTo(STATE_DISCONNECTED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_MONO_LOCATION_ERRATA_API)
    public void getAudioLocation() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.getAudioLocation(mDevice))
                .isEqualTo(BluetoothLeAudio.AUDIO_LOCATION_UNKNOWN);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isInbandRingtoneEnabled() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.isInbandRingtoneEnabled(BluetoothLeAudio.GROUP_ID_INVALID)).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetConnectionPolicy() {
        assertThat(mService.setConnectionPolicy(null, 0)).isFalse();
        assertThat(mService.getConnectionPolicy(null)).isEqualTo(CONNECTION_POLICY_FORBIDDEN);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void registerCallbackNoPermission() {
        TestUtils.dropPermissionAsShellUid();
        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class, () -> mService.registerCallback(mExecutor, mCallback));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void registerUnregisterCallback() {
        // Verify parameter
        assertThrows(NullPointerException.class, () -> mService.registerCallback(null, mCallback));
        assertThrows(NullPointerException.class, () -> mService.registerCallback(mExecutor, null));
        assertThrows(NullPointerException.class, () -> mService.unregisterCallback(null));

        // Test success register unregister
        mService.registerCallback(mExecutor, mCallback);
        mService.unregisterCallback(mCallback);
    }

    @Test
    // CTS doesn't run with a compatible remote device.
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @SuppressWarnings("DirectInvocationOnMock")
    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_BROADCAST_API_MANAGE_PRIMARY_GROUP)
    public void fakeCallbackCoverage() {
        mCallback.onBroadcastToUnicastFallbackGroupChanged(0);
        mCallback.onCodecConfigChanged(0, null);
        mCallback.onGroupNodeAdded(null, 0);
        mCallback.onGroupNodeRemoved(null, 0);
        mCallback.onGroupStatusChanged(0, 0);
        mCallback.onGroupStreamStatusChanged(0, 0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getConnectedGroupLeadDevice() {
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        int groupId = 1;

        // Verify returns null for unknown group id
        assertThat(mService.getConnectedGroupLeadDevice(groupId)).isNull();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setVolume() {
        mService.setVolume(42);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getCodecStatus() {
        assertThat(mService.getCodecStatus(0)).isNull();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setCodecConfigPreference() {
        BluetoothLeAudioCodecConfig codecConfig =
                new BluetoothLeAudioCodecConfig.Builder()
                        .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                        .setCodecPriority(0)
                        .build();

        assertThrows(
                NullPointerException.class, () -> mService.setCodecConfigPreference(0, null, null));

        mService.setCodecConfigPreference(0, codecConfig, codecConfig);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1"})
    @Test
    public void getGroupId() {
        try {
            TestUtils.dropPermissionAsShellUid();
            TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
            mService.getGroupId(mDevice);
        } finally {
            TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_BROADCAST_API_MANAGE_PRIMARY_GROUP)
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void broadcastToUnicastFallbackGroup() {
        int groupId = 1;

        Permissions.enforceEachPermissions(
                () -> {
                    mService.setBroadcastToUnicastFallbackGroup(groupId);
                    return null;
                },
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));

        Permissions.enforceEachPermissions(
                () -> mService.getBroadcastToUnicastFallbackGroup(),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));

        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            mService.setBroadcastToUnicastFallbackGroup(groupId);

            /* There is no such group - verify if it's not updated */
            assertThat(mService.getBroadcastToUnicastFallbackGroup())
                    .isEqualTo(BluetoothLeAudio.GROUP_ID_INVALID);
        }
    }
}
