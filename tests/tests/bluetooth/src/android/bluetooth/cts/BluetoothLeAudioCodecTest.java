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

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BluetoothLeAudioCodecTest {
    private int[] mCodecTypeArray =
            new int[] {
                BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3,
                BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_INVALID,
            };

    private int[] mCodecPriorityArray =
            new int[] {
                BluetoothLeAudioCodecConfig.CODEC_PRIORITY_DISABLED,
                BluetoothLeAudioCodecConfig.CODEC_PRIORITY_DEFAULT,
                BluetoothLeAudioCodecConfig.CODEC_PRIORITY_HIGHEST
            };

    private int[] mSampleRateArray =
            new int[] {
                BluetoothLeAudioCodecConfig.SAMPLE_RATE_NONE,
                BluetoothLeAudioCodecConfig.SAMPLE_RATE_8000,
                BluetoothLeAudioCodecConfig.SAMPLE_RATE_16000,
                BluetoothLeAudioCodecConfig.SAMPLE_RATE_24000,
                BluetoothLeAudioCodecConfig.SAMPLE_RATE_32000,
                BluetoothLeAudioCodecConfig.SAMPLE_RATE_44100,
                BluetoothLeAudioCodecConfig.SAMPLE_RATE_48000
            };

    private int[] mBitsPerSampleArray =
            new int[] {
                BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_NONE,
                BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_16,
                BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_24,
                BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_32
            };

    private int[] mChannelCountArray =
            new int[] {
                BluetoothLeAudioCodecConfig.CHANNEL_COUNT_NONE,
                BluetoothLeAudioCodecConfig.CHANNEL_COUNT_1,
                BluetoothLeAudioCodecConfig.CHANNEL_COUNT_2
            };

    private int[] mFrameDurationArray =
            new int[] {
                BluetoothLeAudioCodecConfig.FRAME_DURATION_NONE,
                BluetoothLeAudioCodecConfig.FRAME_DURATION_7500,
                BluetoothLeAudioCodecConfig.FRAME_DURATION_10000
            };

    @Before
    public void setUp() {
        Assume.assumeTrue(
                TestUtils.isBleSupported(
                        InstrumentationRegistry.getInstrumentation().getContext()));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getCodecNameAndType() {
        for (int codecIdx = 0; codecIdx < mCodecTypeArray.length; codecIdx++) {
            int codecType = mCodecTypeArray[codecIdx];

            BluetoothLeAudioCodecConfig leAudioCodecConfig =
                    new BluetoothLeAudioCodecConfig.Builder().setCodecType(codecType).build();

            if (codecType == BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3) {
                assertThat(leAudioCodecConfig.getCodecName()).isEqualTo("LC3");
            }
            if (codecType == BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_INVALID) {
                assertThat(leAudioCodecConfig.getCodecName()).isEqualTo("INVALID CODEC");
            }

            assertThat(leAudioCodecConfig.getCodecType()).isEqualTo(codecType);
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getCodecPriority() {
        for (int priorityIdx = 0; priorityIdx < mCodecPriorityArray.length; priorityIdx++) {
            int codecPriority = mCodecPriorityArray[priorityIdx];

            BluetoothLeAudioCodecConfig leAudioCodecConfig =
                    new BluetoothLeAudioCodecConfig.Builder()
                            .setCodecPriority(codecPriority)
                            .build();

            assertThat(leAudioCodecConfig.getCodecPriority()).isEqualTo(codecPriority);
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getSampleRate() {
        for (int sampleRateIdx = 0; sampleRateIdx < mSampleRateArray.length; sampleRateIdx++) {
            int sampleRate = mSampleRateArray[sampleRateIdx];

            BluetoothLeAudioCodecConfig leAudioCodecConfig =
                    new BluetoothLeAudioCodecConfig.Builder().setSampleRate(sampleRate).build();

            assertThat(leAudioCodecConfig.getSampleRate()).isEqualTo(sampleRate);
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getBitsPerSample() {
        for (int bitsPerSampleIdx = 0;
                bitsPerSampleIdx < mBitsPerSampleArray.length;
                bitsPerSampleIdx++) {
            int bitsPerSample = mBitsPerSampleArray[bitsPerSampleIdx];

            BluetoothLeAudioCodecConfig leAudioCodecConfig =
                    new BluetoothLeAudioCodecConfig.Builder()
                            .setBitsPerSample(bitsPerSampleIdx)
                            .build();

            assertThat(leAudioCodecConfig.getBitsPerSample()).isEqualTo(bitsPerSampleIdx);
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getChannelCount() {
        for (int channelCountIdx = 0;
                channelCountIdx < mChannelCountArray.length;
                channelCountIdx++) {
            int channelCount = mChannelCountArray[channelCountIdx];

            BluetoothLeAudioCodecConfig leAudioCodecConfig =
                    new BluetoothLeAudioCodecConfig.Builder().setChannelCount(channelCount).build();

            assertThat(leAudioCodecConfig.getChannelCount()).isEqualTo(channelCount);
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getFrameDuration() {
        for (int frameDurationIdx = 0;
                frameDurationIdx < mFrameDurationArray.length;
                frameDurationIdx++) {
            int frameDuration = mFrameDurationArray[frameDurationIdx];

            BluetoothLeAudioCodecConfig leAudioCodecConfig =
                    new BluetoothLeAudioCodecConfig.Builder()
                            .setFrameDuration(frameDurationIdx)
                            .build();

            assertThat(leAudioCodecConfig.getFrameDuration()).isEqualTo(frameDuration);
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getOctetsPerFrame() {
        final int octetsPerFrame = 100;
        BluetoothLeAudioCodecConfig leAudioCodecConfig =
                new BluetoothLeAudioCodecConfig.Builder().setOctetsPerFrame(octetsPerFrame).build();

        assertThat(leAudioCodecConfig.getOctetsPerFrame()).isEqualTo(octetsPerFrame);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getMinOctetsPerFrame() {
        final int minOctetsPerFrame = 100;
        BluetoothLeAudioCodecConfig leAudioCodecConfig =
                new BluetoothLeAudioCodecConfig.Builder()
                        .setMinOctetsPerFrame(minOctetsPerFrame)
                        .build();

        assertThat(leAudioCodecConfig.getMinOctetsPerFrame()).isEqualTo(minOctetsPerFrame);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getMaxOctetsPerFrame() {
        final int maxOctetsPerFrame = 100;
        BluetoothLeAudioCodecConfig leAudioCodecConfig =
                new BluetoothLeAudioCodecConfig.Builder()
                        .setMaxOctetsPerFrame(maxOctetsPerFrame)
                        .build();

        assertThat(leAudioCodecConfig.getMaxOctetsPerFrame()).isEqualTo(maxOctetsPerFrame);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void describeContents() {
        BluetoothLeAudioCodecConfig leAudioCodecConfig =
                new BluetoothLeAudioCodecConfig.Builder().build();
        assertThat(leAudioCodecConfig.describeContents()).isEqualTo(0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void readWriteParcel() {
        final int octetsPerFrame = 100;
        Parcel parcel = Parcel.obtain();
        BluetoothLeAudioCodecConfig leAudioCodecConfig =
                new BluetoothLeAudioCodecConfig.Builder()
                        .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                        .setCodecPriority(BluetoothLeAudioCodecConfig.CODEC_PRIORITY_HIGHEST)
                        .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_24000)
                        .setBitsPerSample(BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_24)
                        .setChannelCount(BluetoothLeAudioCodecConfig.CHANNEL_COUNT_2)
                        .setFrameDuration(BluetoothLeAudioCodecConfig.FRAME_DURATION_7500)
                        .setOctetsPerFrame(octetsPerFrame)
                        .build();
        leAudioCodecConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BluetoothLeAudioCodecConfig leAudioCodecConfigFromParcel =
                BluetoothLeAudioCodecConfig.CREATOR.createFromParcel(parcel);
        assertThat(leAudioCodecConfigFromParcel.getCodecType())
                .isEqualTo(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3);
        assertThat(leAudioCodecConfigFromParcel.getCodecPriority())
                .isEqualTo(BluetoothLeAudioCodecConfig.CODEC_PRIORITY_HIGHEST);
        assertThat(leAudioCodecConfigFromParcel.getSampleRate())
                .isEqualTo(BluetoothLeAudioCodecConfig.SAMPLE_RATE_24000);
        assertThat(leAudioCodecConfigFromParcel.getBitsPerSample())
                .isEqualTo(BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_24);
        assertThat(leAudioCodecConfigFromParcel.getChannelCount())
                .isEqualTo(BluetoothLeAudioCodecConfig.CHANNEL_COUNT_2);
        assertThat(leAudioCodecConfigFromParcel.getFrameDuration())
                .isEqualTo(BluetoothLeAudioCodecConfig.FRAME_DURATION_7500);
        assertThat(leAudioCodecConfigFromParcel.getOctetsPerFrame()).isEqualTo(octetsPerFrame);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void builderWithExistingObject() {
        final int octetsPerFrame = 100;
        final int minOctetsPerFrame = 50;
        final int maxOctetsPerFrame = 150;
        BluetoothLeAudioCodecConfig oriLeAudioCodecConfig =
                new BluetoothLeAudioCodecConfig.Builder()
                        .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                        .setCodecPriority(BluetoothLeAudioCodecConfig.CODEC_PRIORITY_HIGHEST)
                        .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_24000)
                        .setBitsPerSample(BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_24)
                        .setChannelCount(BluetoothLeAudioCodecConfig.CHANNEL_COUNT_2)
                        .setFrameDuration(BluetoothLeAudioCodecConfig.FRAME_DURATION_7500)
                        .setOctetsPerFrame(octetsPerFrame)
                        .setMinOctetsPerFrame(minOctetsPerFrame)
                        .setMaxOctetsPerFrame(maxOctetsPerFrame)
                        .build();
        BluetoothLeAudioCodecConfig toBuilderCodecConfig =
                new BluetoothLeAudioCodecConfig.Builder(oriLeAudioCodecConfig).build();
        assertThat(toBuilderCodecConfig.getCodecType())
                .isEqualTo(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3);
        assertThat(toBuilderCodecConfig.getCodecPriority())
                .isEqualTo(BluetoothLeAudioCodecConfig.CODEC_PRIORITY_HIGHEST);
        assertThat(toBuilderCodecConfig.getSampleRate())
                .isEqualTo(BluetoothLeAudioCodecConfig.SAMPLE_RATE_24000);
        assertThat(toBuilderCodecConfig.getBitsPerSample())
                .isEqualTo(BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_24);
        assertThat(toBuilderCodecConfig.getChannelCount())
                .isEqualTo(BluetoothLeAudioCodecConfig.CHANNEL_COUNT_2);
        assertThat(toBuilderCodecConfig.getFrameDuration())
                .isEqualTo(BluetoothLeAudioCodecConfig.FRAME_DURATION_7500);
        assertThat(toBuilderCodecConfig.getOctetsPerFrame()).isEqualTo(octetsPerFrame);
        assertThat(toBuilderCodecConfig.getMinOctetsPerFrame()).isEqualTo(minOctetsPerFrame);
        assertThat(toBuilderCodecConfig.getMaxOctetsPerFrame()).isEqualTo(maxOctetsPerFrame);
    }
}
