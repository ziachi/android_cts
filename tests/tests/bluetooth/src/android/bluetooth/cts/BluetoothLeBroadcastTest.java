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
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;
import static android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcast;
import android.bluetooth.BluetoothLeBroadcastSettings;
import android.bluetooth.BluetoothLeBroadcastSubgroupSettings;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
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
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeBroadcastTest {
    private static final String TAG = BluetoothLeBroadcastTest.class.getSimpleName();

    @Mock private BluetoothProfile.ServiceListener mListener;
    @Captor private ArgumentCaptor<Integer> mBroadcastId;

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);
    private static final Duration BROADCAST_CALLBACK_TIMEOUT = Duration.ofMillis(500);

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final Executor mExecutor = mContext.getMainExecutor();

    private BluetoothLeBroadcast mService;

    @Mock private BluetoothLeBroadcast.Callback mCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        assumeTrue(SdkLevel.isAtLeastT());
        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH_LE));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        assumeTrue(mAdapter.isLeAudioBroadcastSourceSupported() == FEATURE_SUPPORTED);
        assertThat(BluetoothProperties.isProfileBapBroadcastSourceEnabled().orElse(false)).isTrue();

        assertThat(
                        mAdapter.getProfileProxy(
                                mContext, mListener, BluetoothProfile.LE_AUDIO_BROADCAST))
                .isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(BluetoothProfile.LE_AUDIO_BROADCAST), captor.capture());
        mService = (BluetoothLeBroadcast) captor.getValue();
        assertThat(mService).isNotNull();

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @After
    public void tearDown() {
        mAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO_BROADCAST, mService);
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void closeProfileProxy() {
        mService.close();
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.LE_AUDIO_BROADCAST));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectedDevices() {
        // Verify if asserts as Broadcaster is not connection-oriented profile
        assertThrows(UnsupportedOperationException.class, () -> mService.getConnectedDevices());
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        // Verify if asserts as Broadcaster is not connection-oriented profile
        assertThrows(
                UnsupportedOperationException.class,
                () -> mService.getDevicesMatchingConnectionStates(null));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectionState() {
        // Verify if asserts as Broadcaster is not connection-oriented profile
        assertThrows(UnsupportedOperationException.class, () -> mService.getConnectionState(null));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void registerUnregisterCallback() {
        // Verify invalid parameters
        assertThrows(NullPointerException.class, () -> mService.registerCallback(null, mCallback));
        assertThrows(NullPointerException.class, () -> mService.registerCallback(mExecutor, null));
        assertThrows(NullPointerException.class, () -> mService.unregisterCallback(null));

        // Verify valid parameters
        mService.registerCallback(mExecutor, mCallback);
        mService.unregisterCallback(mCallback);
    }

    @Test
    // CTS doesn't run with a compatible remote device.
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakeCallbackCoverage() {
        mCallback.onBroadcastMetadataChanged(0, null);
        mCallback.onBroadcastStartFailed(0);
        mCallback.onBroadcastStopFailed(0);
        mCallback.onBroadcastUpdateFailed(0, 0);
        mCallback.onBroadcastUpdated(0, 0);
        mCallback.onPlaybackStarted(0, 0);
        mCallback.onPlaybackStopped(0, 0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void registerCallbackNoPermission() {
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class, () -> mService.registerCallback(mExecutor, mCallback));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void startBroadcast() {
        BluetoothLeAudioContentMetadata.Builder contentMetadataBuilder =
                new BluetoothLeAudioContentMetadata.Builder();
        byte[] broadcastCode = {1, 2, 3, 4, 5, 6};

        // Verifies that it throws exception when no callback is registered
        assertThrows(
                IllegalStateException.class,
                () -> mService.startBroadcast(contentMetadataBuilder.build(), broadcastCode));
        mService.registerCallback(mExecutor, mCallback);

        mService.startBroadcast(contentMetadataBuilder.build(), broadcastCode);
        verify(mCallback, timeout(BROADCAST_CALLBACK_TIMEOUT.toMillis()))
                .onBroadcastStarted(
                        eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST), mBroadcastId.capture());

        mService.stopBroadcast(mBroadcastId.getValue());
        verify(mCallback, timeout(BROADCAST_CALLBACK_TIMEOUT.toMillis()))
                .onBroadcastStopped(eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST), anyInt());

        mService.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void startBroadcastWithoutPrivilegedPermission() {
        BluetoothLeAudioContentMetadata.Builder contentMetadataBuilder =
                new BluetoothLeAudioContentMetadata.Builder();
        byte[] broadcastCode = {1, 2, 3, 4, 5, 6};
        mService.registerCallback(mExecutor, mCallback);

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mService.startBroadcast(contentMetadataBuilder.build(), broadcastCode));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mService.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void startBroadcastGroup() {
        BluetoothLeBroadcastSettings.Builder broadcastSettingsBuilder =
                new BluetoothLeBroadcastSettings.Builder();
        BluetoothLeBroadcastSubgroupSettings[] subgroupSettings =
                new BluetoothLeBroadcastSubgroupSettings[] {createBroadcastSubgroupSettings()};
        for (BluetoothLeBroadcastSubgroupSettings setting : subgroupSettings) {
            broadcastSettingsBuilder.addSubgroupSettings(setting);
        }
        // Verifies that it throws exception when no callback is registered
        assertThrows(
                IllegalStateException.class,
                () -> mService.startBroadcast(broadcastSettingsBuilder.build()));
        mService.registerCallback(mExecutor, mCallback);

        mService.startBroadcast(broadcastSettingsBuilder.build());
        verify(mCallback, timeout(BROADCAST_CALLBACK_TIMEOUT.toMillis()))
                .onBroadcastStarted(
                        eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST), mBroadcastId.capture());

        mService.stopBroadcast(mBroadcastId.getValue());
        verify(mCallback, timeout(BROADCAST_CALLBACK_TIMEOUT.toMillis()))
                .onBroadcastStopped(eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST), anyInt());
        mService.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void startBroadcastGroupWithoutPrivilegedPermission() {
        BluetoothLeBroadcastSettings.Builder broadcastSettingsBuilder =
                new BluetoothLeBroadcastSettings.Builder();
        BluetoothLeBroadcastSubgroupSettings[] subgroupSettings =
                new BluetoothLeBroadcastSubgroupSettings[] {createBroadcastSubgroupSettings()};
        for (BluetoothLeBroadcastSubgroupSettings setting : subgroupSettings) {
            broadcastSettingsBuilder.addSubgroupSettings(setting);
        }
        mService.registerCallback(mExecutor, mCallback);

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mService.startBroadcast(broadcastSettingsBuilder.build()));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mService.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void updateBroadcast() {
        BluetoothLeAudioContentMetadata.Builder contentMetadataBuilder =
                new BluetoothLeAudioContentMetadata.Builder();
        // Verifies that it throws exception when no callback is registered
        assertThrows(
                IllegalStateException.class,
                () -> mService.updateBroadcast(1, contentMetadataBuilder.build()));
        mService.registerCallback(mExecutor, mCallback);

        mService.updateBroadcast(1, contentMetadataBuilder.build());
        mService.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void updateBroadcastWithoutPrivilegedPermission() {
        BluetoothLeAudioContentMetadata.Builder contentMetadataBuilder =
                new BluetoothLeAudioContentMetadata.Builder();
        mService.registerCallback(mExecutor, mCallback);

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mService.updateBroadcast(1, contentMetadataBuilder.build()));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mService.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void updateBroadcastGroup() {
        BluetoothLeBroadcastSettings.Builder broadcastSettingsBuilder =
                new BluetoothLeBroadcastSettings.Builder();
        BluetoothLeBroadcastSubgroupSettings[] subgroupSettings =
                new BluetoothLeBroadcastSubgroupSettings[] {createBroadcastSubgroupSettings()};
        for (BluetoothLeBroadcastSubgroupSettings setting : subgroupSettings) {
            broadcastSettingsBuilder.addSubgroupSettings(setting);
        }
        // Verifies that it throws exception when no callback is registered
        assertThrows(
                IllegalStateException.class,
                () -> mService.updateBroadcast(1, broadcastSettingsBuilder.build()));
        mService.registerCallback(mExecutor, mCallback);

        mService.updateBroadcast(1, broadcastSettingsBuilder.build());
        mService.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void updateBroadcastGroupWithoutPrivilegedPermission() {
        BluetoothLeBroadcastSettings.Builder broadcastSettingsBuilder =
                new BluetoothLeBroadcastSettings.Builder();
        BluetoothLeBroadcastSubgroupSettings[] subgroupSettings =
                new BluetoothLeBroadcastSubgroupSettings[] {createBroadcastSubgroupSettings()};
        for (BluetoothLeBroadcastSubgroupSettings setting : subgroupSettings) {
            broadcastSettingsBuilder.addSubgroupSettings(setting);
        }
        mService.registerCallback(mExecutor, mCallback);

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(
                SecurityException.class,
                () -> mService.updateBroadcast(1, broadcastSettingsBuilder.build()));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mService.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void stopBroadcast() {
        // Verifies that it throws exception when no callback is registered
        assertThrows(IllegalStateException.class, () -> mService.stopBroadcast(1));
        mService.registerCallback(mExecutor, mCallback);

        mService.stopBroadcast(1);

        mService.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void stopBroadcastWithoutPrivilegedPermission() {
        mService.registerCallback(mExecutor, mCallback);

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mService.stopBroadcast(1));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mService.unregisterCallback(mCallback);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void isPlaying() {
        assertThat(mService.isPlaying(1)).isFalse();
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void isPlayingWithoutPrivilegedPermission() {
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mService.isPlaying(1));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getAllBroadcastMetadata() {
        assertThat(mService.getAllBroadcastMetadata()).isEmpty();
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getAllBroadcastMetadataWithoutPrivilegedPermission() {
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mService.getAllBroadcastMetadata());
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getMaximumNumberOfBroadcasts() {
        assertThat(mService.getMaximumNumberOfBroadcasts()).isEqualTo(1);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getMaximumNumberOfBroadcastsWithoutPrivilegedPermission() {
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mService.getMaximumNumberOfBroadcasts());
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getMaximumStreamsPerBroadcast() {
        assertThat(mService.getMaximumStreamsPerBroadcast()).isEqualTo(1);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getMaximumStreamsPerBroadcastWithoutPrivilegedPermission() {
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mService.getMaximumStreamsPerBroadcast());
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getMaximumSubgroupsPerBroadcast() {
        assertThat(mService.getMaximumSubgroupsPerBroadcast()).isEqualTo(1);
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getMaximumSubgroupsPerBroadcastWithoutPrivilegedPermission() {
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () -> mService.getMaximumSubgroupsPerBroadcast());
    }

    static BluetoothLeBroadcastSubgroupSettings createBroadcastSubgroupSettings() {
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo("Test")
                        .setLanguage("deu") // German language code in ISO 639-3
                        .build();
        BluetoothLeBroadcastSubgroupSettings.Builder builder =
                new BluetoothLeBroadcastSubgroupSettings.Builder()
                        .setPreferredQuality(BluetoothLeBroadcastSubgroupSettings.QUALITY_STANDARD)
                        .setContentMetadata(contentMetadata);
        return builder.build();
    }
}
