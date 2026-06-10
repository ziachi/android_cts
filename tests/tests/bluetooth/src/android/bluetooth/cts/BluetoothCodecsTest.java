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

import static android.bluetooth.BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC;
import static android.bluetooth.BluetoothCodecConfig.SOURCE_CODEC_TYPE_INVALID;
import static android.bluetooth.BluetoothCodecConfig.SOURCE_CODEC_TYPE_LC3;
import static android.bluetooth.BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC;
import static android.bluetooth.BluetoothCodecType.CODEC_ID_AAC;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothCodecType;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothCodecsTest {
    private static final String TAG = BluetoothCodecsTest.class.getSimpleName();

    // Codec configs: A and B are same; C is different
    private static final BluetoothCodecConfig config_A =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                    1000,
                    2000,
                    3000,
                    4000);

    private static final BluetoothCodecConfig config_B =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                    1000,
                    2000,
                    3000,
                    4000);

    private static final BluetoothCodecConfig config_C =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                    1000,
                    2000,
                    3000,
                    4000);

    // Local capabilities: A and B are same; C is different
    private static final BluetoothCodecConfig local_capability1_A =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100 | BluetoothCodecConfig.SAMPLE_RATE_48000,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO
                            | BluetoothCodecConfig.CHANNEL_MODE_MONO,
                    1000,
                    2000,
                    3000,
                    4000);

    private static final BluetoothCodecConfig local_capability1_B =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100 | BluetoothCodecConfig.SAMPLE_RATE_48000,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO
                            | BluetoothCodecConfig.CHANNEL_MODE_MONO,
                    1000,
                    2000,
                    3000,
                    4000);

    private static final BluetoothCodecConfig local_capability1_C =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100 | BluetoothCodecConfig.SAMPLE_RATE_48000,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                    1000,
                    2000,
                    3000,
                    4000);

    private static final BluetoothCodecConfig local_capability2_A =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100 | BluetoothCodecConfig.SAMPLE_RATE_48000,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO
                            | BluetoothCodecConfig.CHANNEL_MODE_MONO,
                    1000,
                    2000,
                    3000,
                    4000);

    private static final BluetoothCodecConfig local_capability2_B =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100 | BluetoothCodecConfig.SAMPLE_RATE_48000,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO
                            | BluetoothCodecConfig.CHANNEL_MODE_MONO,
                    1000,
                    2000,
                    3000,
                    4000);

    private static final BluetoothCodecConfig local_capability2_C =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100 | BluetoothCodecConfig.SAMPLE_RATE_48000,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                    1000,
                    2000,
                    3000,
                    4000);

    // Selectable capabilities: A and B are same; C is different
    private static final BluetoothCodecConfig selectable_capability1_A =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO
                            | BluetoothCodecConfig.CHANNEL_MODE_MONO,
                    1000,
                    2000,
                    3000,
                    4000);

    private static final BluetoothCodecConfig selectable_capability1_B =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO
                            | BluetoothCodecConfig.CHANNEL_MODE_MONO,
                    1000,
                    2000,
                    3000,
                    4000);

    private static final BluetoothCodecConfig selectable_capability1_C =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                    1000,
                    2000,
                    3000,
                    4000);

    private static final BluetoothCodecConfig selectable_capability2_A =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO
                            | BluetoothCodecConfig.CHANNEL_MODE_MONO,
                    1000,
                    2000,
                    3000,
                    4000);

    private static final BluetoothCodecConfig selectable_capability2_B =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO
                            | BluetoothCodecConfig.CHANNEL_MODE_MONO,
                    1000,
                    2000,
                    3000,
                    4000);

    private static final BluetoothCodecConfig selectable_capability2_C =
            buildBluetoothCodecConfig(
                    BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
                    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
                    BluetoothCodecConfig.SAMPLE_RATE_44100,
                    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                    BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                    1000,
                    2000,
                    3000,
                    4000);

    private static final List<BluetoothCodecConfig> LOCAL_CAPABILITY_A =
            List.of(local_capability1_A, local_capability2_A);

    private static final List<BluetoothCodecConfig> LOCAL_CAPABILITY_B =
            List.of(local_capability1_B, local_capability2_B);

    private static final List<BluetoothCodecConfig> LOCAL_CAPABILITY_C =
            List.of(local_capability1_C, local_capability2_C);

    private static final List<BluetoothCodecConfig> SELECTABLE_CAPABILITY_A =
            List.of(selectable_capability1_A, selectable_capability2_A);

    private static final List<BluetoothCodecConfig> SELECTABLE_CAPABILITY_B =
            List.of(selectable_capability1_B, selectable_capability2_B);

    private static final List<BluetoothCodecConfig> SELECTABLE_CAPABILITY_C =
            List.of(selectable_capability1_C, selectable_capability2_C);

    private static final BluetoothCodecStatus bcs_A =
            new BluetoothCodecStatus.Builder()
                    .setCodecConfig(config_A)
                    .setCodecsLocalCapabilities(LOCAL_CAPABILITY_A)
                    .setCodecsSelectableCapabilities(SELECTABLE_CAPABILITY_A)
                    .build();
    private static final BluetoothCodecStatus bcs_B =
            new BluetoothCodecStatus.Builder()
                    .setCodecConfig(config_B)
                    .setCodecsLocalCapabilities(LOCAL_CAPABILITY_B)
                    .setCodecsSelectableCapabilities(SELECTABLE_CAPABILITY_B)
                    .build();
    private static final BluetoothCodecStatus bcs_C =
            new BluetoothCodecStatus.Builder()
                    .setCodecConfig(config_C)
                    .setCodecsLocalCapabilities(LOCAL_CAPABILITY_C)
                    .setCodecsSelectableCapabilities(SELECTABLE_CAPABILITY_C)
                    .build();

    @Test
    public void bluetoothCodecStatusBuilder() {
        BluetoothCodecStatus builderConfig =
                new BluetoothCodecStatus.Builder()
                        .setCodecConfig(config_A)
                        .setCodecsLocalCapabilities(LOCAL_CAPABILITY_B)
                        .setCodecsSelectableCapabilities(SELECTABLE_CAPABILITY_C)
                        .build();

        assertThat(builderConfig.getCodecConfig()).isEqualTo(config_A);
        assertThat(builderConfig.getCodecsLocalCapabilities()).isEqualTo(LOCAL_CAPABILITY_B);
        assertThat(builderConfig.getCodecsSelectableCapabilities())
                .isEqualTo(SELECTABLE_CAPABILITY_C);
    }

    @Test
    public void bluetoothCodecConfigBuilder() {
        BluetoothCodecConfig builder =
                new BluetoothCodecConfig.Builder()
                        .setCodecType(config_A.getCodecType())
                        .setCodecPriority(config_A.getCodecPriority())
                        .setSampleRate(config_A.getSampleRate())
                        .setBitsPerSample(config_A.getBitsPerSample())
                        .setChannelMode(config_A.getChannelMode())
                        .setCodecSpecific1(config_A.getCodecSpecific1())
                        .setCodecSpecific2(config_A.getCodecSpecific2())
                        .setCodecSpecific3(config_A.getCodecSpecific3())
                        .setCodecSpecific4(config_A.getCodecSpecific4())
                        .build();

        assertThat(builder).isEqualTo(config_A);
        assertThat(builder.isMandatoryCodec()).isTrue();
    }

    @Test
    public void bluetoothCodecConfigExtendedBuilder() {
        // Test that setExtendedCodecType has the same effect
        // as setCodecType.
        BluetoothCodecConfig builder =
                new BluetoothCodecConfig.Builder()
                        .setExtendedCodecType(config_A.getExtendedCodecType())
                        .setCodecPriority(config_A.getCodecPriority())
                        .setSampleRate(config_A.getSampleRate())
                        .setBitsPerSample(config_A.getBitsPerSample())
                        .setChannelMode(config_A.getChannelMode())
                        .setCodecSpecific1(config_A.getCodecSpecific1())
                        .setCodecSpecific2(config_A.getCodecSpecific2())
                        .setCodecSpecific3(config_A.getCodecSpecific3())
                        .setCodecSpecific4(config_A.getCodecSpecific4())
                        .build();

        assertThat(builder).isEqualTo(config_A);
        assertThat(builder.isMandatoryCodec()).isTrue();
    }

    @Test
    public void getCodecConfig() {
        assertThat(bcs_A.getCodecConfig()).isEqualTo(config_A);
        assertThat(bcs_A.getCodecConfig()).isEqualTo(config_B);
        assertThat(bcs_A.getCodecConfig()).isNotEqualTo(config_C);
    }

    @Test
    public void codecsCapabilities() {
        assertThat(bcs_A.getCodecsLocalCapabilities()).isEqualTo(LOCAL_CAPABILITY_A);
        assertThat(bcs_A.getCodecsLocalCapabilities()).isEqualTo(LOCAL_CAPABILITY_B);
        assertThat(bcs_A.getCodecsLocalCapabilities()).isNotEqualTo(LOCAL_CAPABILITY_C);

        assertThat(bcs_A.getCodecsSelectableCapabilities()).isEqualTo(SELECTABLE_CAPABILITY_A);
        assertThat(bcs_A.getCodecsSelectableCapabilities()).isEqualTo(SELECTABLE_CAPABILITY_B);
        assertThat(bcs_A.getCodecsSelectableCapabilities()).isNotEqualTo(SELECTABLE_CAPABILITY_C);
    }

    @Test
    public void isCodecConfigSelectable() {
        assertThat(bcs_A.isCodecConfigSelectable(null)).isFalse();
        assertThat(bcs_A.isCodecConfigSelectable(selectable_capability1_C)).isTrue();
        assertThat(bcs_A.isCodecConfigSelectable(selectable_capability2_C)).isTrue();

        // Not selectable due to multiple channel modes
        assertThat(bcs_A.isCodecConfigSelectable(selectable_capability1_A)).isFalse();
        assertThat(bcs_A.isCodecConfigSelectable(selectable_capability1_B)).isFalse();
        assertThat(bcs_A.isCodecConfigSelectable(selectable_capability2_A)).isFalse();
        assertThat(bcs_A.isCodecConfigSelectable(selectable_capability2_B)).isFalse();
    }

    @Test
    public void codecType_createFromType() {
        BluetoothCodecType c = BluetoothCodecType.createFromType(SOURCE_CODEC_TYPE_AAC);
        assertThat(c).isNotNull();

        assertThat(BluetoothCodecType.createFromType(SOURCE_CODEC_TYPE_LC3)).isNull();
        assertThat(BluetoothCodecType.createFromType(SOURCE_CODEC_TYPE_INVALID)).isNull();
    }

    @Test
    public void codecType_getCodecId() {
        BluetoothCodecType c = BluetoothCodecType.createFromType(SOURCE_CODEC_TYPE_AAC);
        assertThat(c.getCodecId()).isEqualTo(CODEC_ID_AAC);
    }

    @Test
    public void codecType_getCodecName() {
        BluetoothCodecType c = BluetoothCodecType.createFromType(SOURCE_CODEC_TYPE_AAC);
        assertThat(c.getCodecName()).isEqualTo("AAC");
    }

    @Test
    public void codecType_isMandatoryCodec() {
        BluetoothCodecType cA = BluetoothCodecType.createFromType(SOURCE_CODEC_TYPE_AAC);
        BluetoothCodecType cB = BluetoothCodecType.createFromType(SOURCE_CODEC_TYPE_SBC);
        assertThat(cA.isMandatoryCodec()).isFalse();
        assertThat(cB.isMandatoryCodec()).isTrue();
    }

    private static BluetoothCodecConfig buildBluetoothCodecConfig(
            int sourceCodecType,
            int codecPriority,
            int sampleRate,
            int bitsPerSample,
            int channelMode,
            long codecSpecific1,
            long codecSpecific2,
            long codecSpecific3,
            long codecSpecific4) {
        return new BluetoothCodecConfig.Builder()
                .setCodecType(sourceCodecType)
                .setCodecPriority(codecPriority)
                .setSampleRate(sampleRate)
                .setBitsPerSample(bitsPerSample)
                .setChannelMode(channelMode)
                .setCodecSpecific1(codecSpecific1)
                .setCodecSpecific2(codecSpecific2)
                .setCodecSpecific3(codecSpecific3)
                .setCodecSpecific4(codecSpecific4)
                .build();
    }
}
