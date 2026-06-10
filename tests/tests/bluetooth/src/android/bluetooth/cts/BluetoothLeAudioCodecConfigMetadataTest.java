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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeAudioCodecConfigMetadataTest {
    private static final long TEST_AUDIO_LOCATION_FRONT_LEFT = 0x01;
    private static final int TEST_SAMPLE_RATE_44100 = 0x01 << 6;
    private static final int TEST_FRAME_DURATION_10000 = 0x01 << 1;
    private static final int TEST_OCTETS_PER_FRAME = 100;

    // See Page 5 of Generic Audio assigned number specification
    private static final byte[] TEST_METADATA_BYTES = {
        // length = 0x05, type = 0x03, value = 0x00000001 (front left)
        0x05,
        0x03,
        0x01,
        0x00,
        0x00,
        0x00,
        // length = 0x02, type = 0x01, value = 0x07 (44100 hz)
        0x02,
        0x01,
        0x07,
        // length = 0x02, type = 0x02, value = 0x01 (10 ms)
        0x02,
        0x02,
        0x01,
        // length = 0x03, type = 0x04, value = 0x64 (100)
        0x03,
        0x04,
        0x64,
        0x00
    };

    private Context mContext;
    private BluetoothAdapter mAdapter;
    private boolean mIsBroadcastSourceSupported;
    private boolean mIsBroadcastAssistantSupported;

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
        mAdapter = null;
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createCodecConfigMetadataFromBuilder() {
        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(TEST_AUDIO_LOCATION_FRONT_LEFT)
                        .setSampleRate(TEST_SAMPLE_RATE_44100)
                        .setFrameDuration(TEST_FRAME_DURATION_10000)
                        .setOctetsPerFrame(TEST_OCTETS_PER_FRAME)
                        .build();
        assertThat(codecMetadata.getAudioLocation()).isEqualTo(TEST_AUDIO_LOCATION_FRONT_LEFT);
        assertThat(codecMetadata.getSampleRate()).isEqualTo(TEST_SAMPLE_RATE_44100);
        assertThat(codecMetadata.getFrameDuration()).isEqualTo(TEST_FRAME_DURATION_10000);
        assertThat(codecMetadata.getOctetsPerFrame()).isEqualTo(TEST_OCTETS_PER_FRAME);
        // TODO: Implement implicit LTV byte conversion in the API class
        // assertThat(codecMetadata.getRawMetadata()).isEqualTo(TEST_METADATA_BYTES);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createCodecConfigMetadataFromCopy() {
        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(TEST_AUDIO_LOCATION_FRONT_LEFT)
                        .setSampleRate(TEST_SAMPLE_RATE_44100)
                        .setFrameDuration(TEST_FRAME_DURATION_10000)
                        .setOctetsPerFrame(TEST_OCTETS_PER_FRAME)
                        .build();
        BluetoothLeAudioCodecConfigMetadata codecMetadataCopy =
                new BluetoothLeAudioCodecConfigMetadata.Builder(codecMetadata).build();
        assertThat(codecMetadataCopy).isEqualTo(codecMetadata);
        assertThat(codecMetadataCopy.getAudioLocation()).isEqualTo(TEST_AUDIO_LOCATION_FRONT_LEFT);
        assertThat(codecMetadataCopy.getSampleRate()).isEqualTo(TEST_SAMPLE_RATE_44100);
        assertThat(codecMetadataCopy.getFrameDuration()).isEqualTo(TEST_FRAME_DURATION_10000);
        assertThat(codecMetadataCopy.getOctetsPerFrame()).isEqualTo(TEST_OCTETS_PER_FRAME);
        assertThat(codecMetadataCopy.getRawMetadata()).isEqualTo(codecMetadata.getRawMetadata());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createCodecConfigMetadataFromBytes() {
        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                BluetoothLeAudioCodecConfigMetadata.fromRawBytes(TEST_METADATA_BYTES);
        byte[] metadataBytes = codecMetadata.getRawMetadata();
        assertThat(metadataBytes).isEqualTo(TEST_METADATA_BYTES);
        assertThat(codecMetadata.getAudioLocation()).isEqualTo(TEST_AUDIO_LOCATION_FRONT_LEFT);
        assertThat(codecMetadata.getSampleRate()).isEqualTo(TEST_SAMPLE_RATE_44100);
        assertThat(codecMetadata.getFrameDuration()).isEqualTo(TEST_FRAME_DURATION_10000);
        assertThat(codecMetadata.getOctetsPerFrame()).isEqualTo(TEST_OCTETS_PER_FRAME);
    }
}
