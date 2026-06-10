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
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_ALLOWED;
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;
import static android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastAssistant;
import android.bluetooth.BluetoothLeBroadcastChannel;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastSubgroup;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.sysprop.BluetoothProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeBroadcastAssistantTest {
    private static final String TAG = BluetoothLeBroadcastAssistantTest.class.getSimpleName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock private BluetoothProfile.ServiceListener mListener;
    @Mock private BluetoothLeBroadcastAssistant.Callback mCallback;

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);
    private static final Duration START_SEARCH_TIMEOUT = Duration.ofMillis(100);
    private static final Duration ADD_SOURCE_TIMEOUT = Duration.ofMillis(100);

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final BluetoothDevice mDevice = mAdapter.getRemoteDevice("EF:11:22:AA:BB:CC");
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final Executor mExecutor = mContext.getMainExecutor();

    private BluetoothLeBroadcastAssistant mService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        assumeTrue(SdkLevel.isAtLeastT());
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH_LE));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        assumeTrue(mAdapter.isLeAudioBroadcastAssistantSupported() == FEATURE_SUPPORTED);
        assertThat(BluetoothProperties.isProfileBapBroadcastAssistEnabled().orElse(false)).isTrue();

        assertThat(
                        mAdapter.getProfileProxy(
                                mContext, mListener, BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT))
                .isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(
                        eq(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT), captor.capture());
        mService = (BluetoothLeBroadcastAssistant) captor.getValue();
        assertThat(mService).isNotNull();

        TestUtils.adoptPermissionAsShellUid(
                BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED, BLUETOOTH_SCAN);
    }

    @After
    public void tearDown() {
        mAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT, mService);
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void closeProfileProxy() {
        mService.close();
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void addSource() {
        BluetoothLeAudioContentMetadata publicBroadcastMetadata =
                new BluetoothLeAudioContentMetadata.Builder().setProgramInfo("Test").build();

        BluetoothLeBroadcastMetadata metadata =
                new BluetoothLeBroadcastMetadata.Builder()
                        .setEncrypted(false)
                        .setPublicBroadcast(false)
                        .setBroadcastName("Test")
                        .setSourceDevice(mDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                        .setSourceAdvertisingSid(1234)
                        .setBroadcastId(42)
                        .setBroadcastCode(null)
                        .setPaSyncInterval(100)
                        .setPresentationDelayMicros(345)
                        .setAudioConfigQuality(1)
                        .addSubgroup(createBroadcastSubgroup())
                        .setPublicBroadcastMetadata(publicBroadcastMetadata)
                        .build();

        // Verifies that it throws exception when no callback is registered
        assertThrows(
                IllegalStateException.class, () -> mService.addSource(mDevice, metadata, true));

        mService.registerCallback(mExecutor, mCallback);

        // Verify that exceptions is thrown when sink or source is null
        assertThrows(NullPointerException.class, () -> mService.addSource(mDevice, null, true));
        assertThrows(NullPointerException.class, () -> mService.addSource(null, metadata, true));
        assertThrows(NullPointerException.class, () -> mService.addSource(null, null, true));

        // Verify that adding source without scanned/local broadcaster will fail
        mService.addSource(mDevice, metadata, true);
        verify(mCallback, timeout(ADD_SOURCE_TIMEOUT.toMillis()))
                .onSourceAddFailed(mDevice, metadata, BluetoothStatusCodes.ERROR_BAD_PARAMETERS);

        // Verify that removing null source device will throw exception
        assertThrows(NullPointerException.class, () -> mService.removeSource(null, 0));

        // Verify that removing unknown device will fail
        mService.removeSource(mDevice, 0);
        verify(mCallback, timeout(ADD_SOURCE_TIMEOUT.toMillis()))
                .onSourceRemoveFailed(mDevice, 0, BluetoothStatusCodes.ERROR_REMOTE_LINK_ERROR);

        // Do not forget to unregister callbacks
        mService.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getAllSources() {
        // Verify implementation throws exception when input is null
        assertThrows(NullPointerException.class, () -> mService.getAllSources(null));

        // Verify returns empty list if a device is not connected
        assertThat(mService.getAllSources(mDevice)).isEmpty();

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getAllSources(mDevice)).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void setConnectionPolicy() {
        // Verify that it returns unknown for an unknown test device
        assertThat(mService.getConnectionPolicy(mDevice)).isEqualTo(CONNECTION_POLICY_UNKNOWN);

        // Verify that it returns true even for an unknown test device
        assertThat(mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_ALLOWED)).isTrue();

        // Verify that it returns the same value we set before
        assertThat(mService.getConnectionPolicy(mDevice)).isEqualTo(CONNECTION_POLICY_ALLOWED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getMaximumSourceCapacity() {
        // Verifies that it returns 0 for an unknown test device
        assertThat(mService.getMaximumSourceCapacity(mDevice)).isEqualTo(0);

        // Verifies that it throws exception when input is null
        assertThrows(NullPointerException.class, () -> mService.getMaximumSourceCapacity(null));
    }

    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_BROADCAST_API_GET_LOCAL_METADATA)
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getSourceMetadata() {
        int testSourceId = 1;

        // Verifies permissions
        Permissions.enforceEachPermissions(
                () -> mService.getSourceMetadata(mDevice, testSourceId),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));

        // Verifies that it throws exception when input is null
        assertThrows(
                NullPointerException.class, () -> mService.getSourceMetadata(null, testSourceId));

        // Source id expect in range [0, 255]
        assertThrows(IllegalArgumentException.class, () -> mService.getSourceMetadata(mDevice, -1));
        assertThrows(
                IllegalArgumentException.class, () -> mService.getSourceMetadata(mDevice, 256));

        mService.getSourceMetadata(mDevice, testSourceId);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void isSearchInProgress() {
        // Verify that it returns false when search is not in progress
        assertThat(mService.isSearchInProgress()).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void modifySource() {
        BluetoothLeBroadcastMetadata metadata =
                new BluetoothLeBroadcastMetadata.Builder()
                        .setEncrypted(false)
                        .setPublicBroadcast(false)
                        .setBroadcastName("Test")
                        .setSourceDevice(mDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                        .setSourceAdvertisingSid(1234)
                        .setBroadcastId(42)
                        .setBroadcastCode(null)
                        .setPaSyncInterval(100)
                        .setPresentationDelayMicros(345)
                        .addSubgroup(createBroadcastSubgroup())
                        .build();

        // Verifies that it throws exception when callback is not registered
        assertThrows(
                IllegalStateException.class, () -> mService.modifySource(mDevice, 0, metadata));

        mService.registerCallback(mExecutor, mCallback);

        // Verifies that it throws exception when argument is null
        assertThrows(NullPointerException.class, () -> mService.modifySource(null, 0, null));
        assertThrows(NullPointerException.class, () -> mService.modifySource(mDevice, 0, null));
        assertThrows(NullPointerException.class, () -> mService.modifySource(null, 0, metadata));

        // Verify failure callback when test device is not connected
        mService.modifySource(mDevice, 0, metadata);
        verify(mCallback, timeout(ADD_SOURCE_TIMEOUT.toMillis()))
                .onSourceModifyFailed(mDevice, 0, BluetoothStatusCodes.ERROR_REMOTE_LINK_ERROR);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void registerCallback() {
        // Verify parameter
        assertThrows(NullPointerException.class, () -> mService.registerCallback(null, mCallback));
        assertThrows(NullPointerException.class, () -> mService.registerCallback(mExecutor, null));
        assertThrows(NullPointerException.class, () -> mService.unregisterCallback(null));

        // Verify that register and unregister callback will not cause any crush issues
        mService.registerCallback(mExecutor, mCallback);
        mService.unregisterCallback(mCallback);
    }

    @Test
    // CTS doesn't run with a compatible remote device.
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakeCallbackCoverage() {
        mCallback.onReceiveStateChanged(null, 0, null);
        mCallback.onSearchStartFailed(0);
        mCallback.onSearchStopFailed(0);
        mCallback.onSearchStopped(0);
        mCallback.onSourceAdded(null, 0, 0);
        mCallback.onSourceFound(null);
        mCallback.onSourceLost(0);
        mCallback.onSourceModified(null, 0, 0);
        mCallback.onSourceRemoved(null, 0, 0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void startSearchingForSources() {
        // Verifies that it throws exception when no callback is registered
        assertThrows(
                IllegalStateException.class,
                () -> mService.startSearchingForSources(Collections.emptyList()));

        mService.registerCallback(mExecutor, mCallback);

        // Verifies that it throws exception when filter is null
        assertThrows(NullPointerException.class, () -> mService.startSearchingForSources(null));

        // Verify that starting search triggers callback with the right reason
        mService.startSearchingForSources(Collections.emptyList());
        verify(mCallback, timeout(START_SEARCH_TIMEOUT.toMillis()))
                .onSearchStarted(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);

        // Verify search state is right
        assertThat(mService.isSearchInProgress()).isTrue();

        // Verify that stopping search triggers the callback with the right reason
        mService.stopSearchingForSources();
        verify(mCallback, timeout(START_SEARCH_TIMEOUT.toMillis()))
                .onSearchStarted(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);

        // Verify search state is right
        assertThat(mService.isSearchInProgress()).isFalse();

        // Do not forget to unregister callbacks
        mService.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectedDevices() {
        // Verify returns empty list if no broadcast assistant device is connected
        assertThat(mService.getConnectedDevices()).isEmpty();

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getConnectedDevices()).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        int[] states = {STATE_CONNECTED};

        // Verify returns empty list if no broadcast assistant device is connected
        assertThat(mService.getDevicesMatchingConnectionStates(states)).isEmpty();

        // Verify exception is thrown when null input is given
        assertThrows(
                NullPointerException.class,
                () -> mService.getDevicesMatchingConnectionStates(null));

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns empty list if bluetooth is not enabled
        assertThat(mService.getDevicesMatchingConnectionStates(states)).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectionState() {
        // Verify exception is thrown when null input is given
        assertThrows(NullPointerException.class, () -> mService.getConnectionState(null));

        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();

        // Verify returns false if bluetooth is not enabled
        assertThat(mService.getConnectionState(mDevice)).isEqualTo(STATE_DISCONNECTED);
    }

    static BluetoothLeBroadcastSubgroup createBroadcastSubgroup() {
        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(0x01) // FRONT_LEFT
                        .setSampleRate(0x01 << 6) // SAMPLE_RATE_44100
                        .setFrameDuration(0x01 << 1) // FRAME_DURATION_10000
                        .setOctetsPerFrame(100) // OCTETS_PER_FRAME
                        .build();
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo("Test")
                        .setLanguage("deu") // German language code in ISO 639-3
                        .build();
        BluetoothLeBroadcastSubgroup.Builder builder =
                new BluetoothLeBroadcastSubgroup.Builder()
                        .setCodecId(42)
                        .setCodecSpecificConfig(codecMetadata)
                        .setContentMetadata(contentMetadata);
        BluetoothLeAudioCodecConfigMetadata emptyMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder().build();
        BluetoothLeBroadcastChannel channel =
                new BluetoothLeBroadcastChannel.Builder()
                        .setChannelIndex(42)
                        .setSelected(true)
                        .setCodecMetadata(emptyMetadata)
                        .build();
        builder.addChannel(channel);
        return builder.build();
    }
}
