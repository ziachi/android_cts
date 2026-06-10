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
import static android.bluetooth.BluetoothA2dp.DYNAMIC_BUFFER_SUPPORT_A2DP_SOFTWARE_ENCODING;
import static android.bluetooth.BluetoothA2dp.DYNAMIC_BUFFER_SUPPORT_NONE;
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
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecType;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
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
import java.util.Collection;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothA2dpTest {
    private static final String TAG = BluetoothA2dpTest.class.getSimpleName();

    @Mock private BluetoothProfile.ServiceListener mListener;

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final BluetoothDevice mDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private BluetoothA2dp mService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH));
        assumeTrue(BluetoothProperties.isProfileA2dpSourceEnabled().orElse(false));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        assertThat(mAdapter.getProfileProxy(mContext, mListener, BluetoothProfile.A2DP)).isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(BluetoothProfile.A2DP), captor.capture());
        mService = (BluetoothA2dp) captor.getValue();
        assertThat(mService).isNotNull();

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    @After
    public void tearDown() {
        mAdapter.closeProfileProxy(BluetoothProfile.A2DP, mService);
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void closeProfileProxy() {
        mAdapter.closeProfileProxy(BluetoothProfile.A2DP, mService);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.A2DP));
    }

    @Test
    public void closeProfileProxy_onDifferentAdapter() {
        Context context = mContext.createAttributionContext("test");
        BluetoothAdapter adapter = context.getSystemService(BluetoothManager.class).getAdapter();

        assertThat(mAdapter).isNotEqualTo(adapter);

        adapter.closeProfileProxy(BluetoothProfile.A2DP, mService);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.A2DP));
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
    public void isA2dpPlaying() {
        assertThat(mService.isA2dpPlaying(mDevice)).isFalse();
    }

    @Test
    public void getSupportedCodecTypes() {
        assertThrows(SecurityException.class, () -> mService.getSupportedCodecTypes());

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        Collection<BluetoothCodecType> supportedCodecTypes = mService.getSupportedCodecTypes();
        assertThat(supportedCodecTypes).isNotNull();

        // getSupportedCodecTypes must not return null objects.
        for (BluetoothCodecType codecType : supportedCodecTypes) {
            assertThat(codecType).isNotNull();
        }

        // Supported codecs must contain at least the mandatory SBC codec.
        assertThat(supportedCodecTypes)
                .contains(
                        BluetoothCodecType.createFromType(
                                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC));
    }

    @Test
    public void getCodecStatus() {
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(mService.getCodecStatus(mDevice)).isNull();
        assertThrows(IllegalArgumentException.class, () -> mService.getCodecStatus(null));
    }

    @Test
    public void setCodecConfigPreference() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mService.setCodecConfigPreference(null, null));
    }

    @Test
    public void setOptionalCodecsEnabled() {
        assertThrows(
                IllegalArgumentException.class, () -> mService.setOptionalCodecsEnabled(null, 0));

        mUiAutomation.dropShellPermissionIdentity();
        mService.setOptionalCodecsEnabled(mDevice, BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN);
        mService.setOptionalCodecsEnabled(mDevice, BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED);
        mService.setOptionalCodecsEnabled(mDevice, BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED);
    }

    @Test
    public void getConnectionPolicy() {
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        // Verify returns false when invalid input is given
        assertThat(mService.getConnectionPolicy(null)).isEqualTo(CONNECTION_POLICY_FORBIDDEN);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.getConnectionPolicy(mDevice)).isEqualTo(CONNECTION_POLICY_FORBIDDEN);
    }

    @Test
    public void setConnectionPolicy() {
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Verify returns false when invalid input is given
        assertThat(mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_UNKNOWN)).isFalse();
        assertThat(mService.setConnectionPolicy(null, CONNECTION_POLICY_ALLOWED)).isFalse();

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_FORBIDDEN)).isFalse();
    }

    @Test
    public void getDynamicBufferSupport() {
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        int dynamicBufferSupport = mService.getDynamicBufferSupport();
        assertThat(dynamicBufferSupport).isAtLeast(DYNAMIC_BUFFER_SUPPORT_NONE);
        assertThat(dynamicBufferSupport).isAtMost(DYNAMIC_BUFFER_SUPPORT_A2DP_SOFTWARE_ENCODING);

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns DYNAMIC_BUFFER_SUPPORT_NONE if bluetooth is not enabled
        assertThat(mService.getDynamicBufferSupport()).isEqualTo(DYNAMIC_BUFFER_SUPPORT_NONE);
    }

    @Test
    public void getBufferConstraints() {
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertThat(mService.getBufferConstraints()).isNotNull();
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        // Verify returns null if bluetooth is not enabled
        assertThat(mService.getBufferConstraints()).isNull();
    }

    @Test
    public void setBufferLengthMillis() {
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        int sourceCodecTypeAAC = 1;

        assertThat(mService.setBufferLengthMillis(sourceCodecTypeAAC, 0)).isTrue();
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        // Verify returns null if bluetooth is not enabled
        assertThat(mService.setBufferLengthMillis(sourceCodecTypeAAC, 0)).isFalse();
    }

    @Test
    public void optionalCodecs() {
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(mService.isOptionalCodecsEnabled(mDevice)).isEqualTo(-1);
        assertThat(mService.isOptionalCodecsSupported(mDevice)).isEqualTo(-1);

        mService.enableOptionalCodecs(mDevice);
        // Device is not in state machine so should not be enabled
        assertThat(mService.isOptionalCodecsEnabled(mDevice)).isEqualTo(-1);

        mService.disableOptionalCodecs(mDevice);
        assertThat(mService.isOptionalCodecsEnabled(mDevice)).isEqualTo(-1);

        assertThrows(IllegalArgumentException.class, () -> mService.isOptionalCodecsEnabled(null));
        assertThrows(
                IllegalArgumentException.class, () -> mService.isOptionalCodecsSupported(null));
        assertThrows(IllegalArgumentException.class, () -> mService.enableOptionalCodecs(null));
        assertThrows(IllegalArgumentException.class, () -> mService.disableOptionalCodecs(null));
    }

    @Test
    public void setAvrcpAbsoluteVolume() {
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mService.setAvrcpAbsoluteVolume(0); // No crash should occurs
    }
}
