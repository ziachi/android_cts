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
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastChannel;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastSubgroup;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeBroadcastMetadataTest {
    private static final String TEST_MAC_ADDRESS = "00:11:22:33:44:55";
    private static final int TEST_BROADCAST_ID = 42;
    private static final int TEST_ADVERTISER_SID = 1234;
    private static final int TEST_PA_SYNC_INTERVAL = 100;
    private static final int TEST_PRESENTATION_DELAY_MS = 345;
    private static final int TEST_AUDIO_QUALITY_STANDARD = 0x1 << 0;
    private static final int TEST_RSSI_DBM = -40;
    private static final String TEST_BROADCAST_NAME = "TEST";

    private static final int TEST_CODEC_ID = 42;

    // For BluetoothLeAudioCodecConfigMetadata
    private static final long TEST_AUDIO_LOCATION_FRONT_LEFT = 0x01;
    private static final int TEST_SAMPLE_RATE_44100 = 0x01 << 6;
    private static final int TEST_FRAME_DURATION_10000 = 0x01 << 1;
    private static final int TEST_OCTETS_PER_FRAME = 100;

    // For BluetoothLeAudioContentMetadata
    private static final String TEST_PROGRAM_INFO = "Test";
    // German language code in ISO 639-3
    private static final String TEST_LANGUAGE = "deu";

    private Context mContext;
    private BluetoothAdapter mAdapter;
    private boolean mIsBroadcastSourceSupported;
    private boolean mIsBroadcastAssistantSupported;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();

        mIsBroadcastAssistantSupported =
                mAdapter.isLeAudioBroadcastAssistantSupported() == FEATURE_SUPPORTED;
        if (mIsBroadcastAssistantSupported) {
            assertThat(TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT))
                    .isTrue();
            boolean isBroadcastAssistantEnabledInConfig =
                    TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
        }

        mIsBroadcastSourceSupported =
                mAdapter.isLeAudioBroadcastSourceSupported() == FEATURE_SUPPORTED;
        if (mIsBroadcastSourceSupported) {
            assertThat(TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST)).isTrue();
        }

        Assume.assumeTrue(mIsBroadcastAssistantSupported || mIsBroadcastSourceSupported);
    }

    @After
    public void tearDown() {
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2", "7.4.3/C-9-1"})
    @Test
    public void createMetadataFromBuilder() {
        BluetoothDevice testDevice =
                mAdapter.getRemoteLeDevice(TEST_MAC_ADDRESS, BluetoothDevice.ADDRESS_TYPE_RANDOM);
        BluetoothLeAudioContentMetadata publicBroadcastMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO)
                        .build();
        BluetoothLeBroadcastSubgroup subgroup = createBroadcastSubgroup();
        BluetoothLeBroadcastMetadata.Builder builder =
                new BluetoothLeBroadcastMetadata.Builder()
                        .setEncrypted(false)
                        .setPublicBroadcast(false)
                        .setBroadcastName(TEST_BROADCAST_NAME)
                        .setSourceDevice(testDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                        .setSourceAdvertisingSid(TEST_ADVERTISER_SID)
                        .setBroadcastId(TEST_BROADCAST_ID)
                        .setBroadcastCode(null)
                        .setPaSyncInterval(TEST_PA_SYNC_INTERVAL)
                        .setPresentationDelayMicros(TEST_PRESENTATION_DELAY_MS)
                        .setAudioConfigQuality(TEST_AUDIO_QUALITY_STANDARD)
                        .setPublicBroadcastMetadata(publicBroadcastMetadata)
                        .addSubgroup(subgroup);
        BluetoothLeBroadcastMetadata metadata = builder.build();

        assertThat(metadata.isEncrypted()).isFalse();
        assertThat(metadata.isPublicBroadcast()).isFalse();
        assertThat(metadata.getBroadcastName()).isEqualTo(TEST_BROADCAST_NAME);
        assertThat(metadata.getSourceDevice()).isEqualTo(testDevice);
        assertThat(metadata.getSourceAddressType()).isEqualTo(BluetoothDevice.ADDRESS_TYPE_RANDOM);
        assertThat(metadata.getSourceAdvertisingSid()).isEqualTo(TEST_ADVERTISER_SID);
        assertThat(metadata.getBroadcastId()).isEqualTo(TEST_BROADCAST_ID);
        assertThat(metadata.getBroadcastCode()).isNull();
        assertThat(metadata.getPaSyncInterval()).isEqualTo(TEST_PA_SYNC_INTERVAL);
        assertThat(metadata.getPresentationDelayMicros()).isEqualTo(TEST_PRESENTATION_DELAY_MS);
        assertThat(metadata.getAudioConfigQuality()).isEqualTo(TEST_AUDIO_QUALITY_STANDARD);
        assertThat(metadata.getPublicBroadcastMetadata()).isEqualTo(publicBroadcastMetadata);
        assertThat(metadata.getSubgroups()).containsExactly(subgroup);
        builder.clearSubgroup();
        // builder expect at least one subgroup
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2", "7.4.3/C-9-1"})
    @Test
    public void createMetadataFromCopy() {
        BluetoothDevice testDevice =
                mAdapter.getRemoteLeDevice(TEST_MAC_ADDRESS, BluetoothDevice.ADDRESS_TYPE_RANDOM);
        BluetoothLeAudioContentMetadata publicBroadcastMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO)
                        .build();
        BluetoothLeBroadcastSubgroup subgroup = createBroadcastSubgroup();
        BluetoothLeBroadcastMetadata metadata =
                new BluetoothLeBroadcastMetadata.Builder()
                        .setEncrypted(false)
                        .setPublicBroadcast(false)
                        .setBroadcastName(TEST_BROADCAST_NAME)
                        .setSourceDevice(testDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                        .setSourceAdvertisingSid(TEST_ADVERTISER_SID)
                        .setBroadcastId(TEST_BROADCAST_ID)
                        .setBroadcastCode(null)
                        .setPaSyncInterval(TEST_PA_SYNC_INTERVAL)
                        .setPresentationDelayMicros(TEST_PRESENTATION_DELAY_MS)
                        .setAudioConfigQuality(TEST_AUDIO_QUALITY_STANDARD)
                        .setPublicBroadcastMetadata(publicBroadcastMetadata)
                        .addSubgroup(subgroup)
                        .build();

        BluetoothLeBroadcastMetadata metadataCopy =
                new BluetoothLeBroadcastMetadata.Builder(metadata).build();
        assertThat(metadataCopy.isEncrypted()).isFalse();
        assertThat(metadataCopy.isPublicBroadcast()).isFalse();
        assertThat(metadataCopy.getBroadcastName()).isEqualTo(TEST_BROADCAST_NAME);
        assertThat(metadataCopy.getSourceDevice()).isEqualTo(testDevice);
        assertThat(metadataCopy.getSourceAddressType())
                .isEqualTo(BluetoothDevice.ADDRESS_TYPE_RANDOM);
        assertThat(metadataCopy.getSourceAdvertisingSid()).isEqualTo(TEST_ADVERTISER_SID);
        assertThat(metadataCopy.getBroadcastId()).isEqualTo(TEST_BROADCAST_ID);
        assertThat(metadataCopy.getBroadcastCode()).isNull();
        assertThat(metadataCopy.getPaSyncInterval()).isEqualTo(TEST_PA_SYNC_INTERVAL);
        assertThat(metadataCopy.getPresentationDelayMicros()).isEqualTo(TEST_PRESENTATION_DELAY_MS);
        assertThat(metadataCopy.getAudioConfigQuality()).isEqualTo(TEST_AUDIO_QUALITY_STANDARD);
        assertThat(metadataCopy.getPublicBroadcastMetadata()).isEqualTo(publicBroadcastMetadata);
        assertThat(metadataCopy.getSubgroups()).containsExactly(subgroup);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2", "7.4.3/C-9-1"})
    @Test
    public void createMetadataFromBuilderAndCheckRssi() {
        final int testRssiInvalidMin = -128;
        final int testRssiInvalidMax = 128;
        BluetoothDevice testDevice =
                mAdapter.getRemoteLeDevice(TEST_MAC_ADDRESS, BluetoothDevice.ADDRESS_TYPE_RANDOM);
        BluetoothLeBroadcastMetadata.Builder builder =
                new BluetoothLeBroadcastMetadata.Builder()
                        .setSourceDevice(testDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                        .addSubgroup(createBroadcastSubgroup());

        // validate RSSI is unknown if not set
        BluetoothLeBroadcastMetadata metadata = builder.build();
        assertThat(metadata.getRssi()).isEqualTo(BluetoothLeBroadcastMetadata.RSSI_UNKNOWN);

        // builder expect rssi is in range [-127, 127]
        assertThrows(IllegalArgumentException.class, () -> builder.setRssi(testRssiInvalidMin));
        assertThrows(IllegalArgumentException.class, () -> builder.setRssi(testRssiInvalidMax));

        builder.setRssi(TEST_RSSI_DBM);
        metadata = builder.build();
        assertThat(metadata.getRssi()).isEqualTo(TEST_RSSI_DBM);
    }

    static BluetoothLeBroadcastSubgroup createBroadcastSubgroup() {
        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(TEST_AUDIO_LOCATION_FRONT_LEFT)
                        .setSampleRate(TEST_SAMPLE_RATE_44100)
                        .setFrameDuration(TEST_FRAME_DURATION_10000)
                        .setOctetsPerFrame(TEST_OCTETS_PER_FRAME)
                        .build();
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO)
                        .setLanguage(TEST_LANGUAGE)
                        .build();
        BluetoothLeBroadcastChannel channel =
                new BluetoothLeBroadcastChannel.Builder()
                        .setChannelIndex(42)
                        .setSelected(true)
                        .setCodecMetadata(new BluetoothLeAudioCodecConfigMetadata.Builder().build())
                        .build();
        return new BluetoothLeBroadcastSubgroup.Builder()
                .setCodecId(TEST_CODEC_ID)
                .setCodecSpecificConfig(codecMetadata)
                .setContentMetadata(contentMetadata)
                .addChannel(channel)
                .build();
    }
}
