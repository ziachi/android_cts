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

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.bluetooth.BluetoothLeAudioCodecStatus;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class BluetoothLeAudioCodecStatusTest {
    private static final BluetoothLeAudioCodecConfig LC3_16KHZ_CONFIG =
            new BluetoothLeAudioCodecConfig.Builder()
                    .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                    .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_16000)
                    .build();
    private static final BluetoothLeAudioCodecConfig LC3_48KHZ_CONFIG =
            new BluetoothLeAudioCodecConfig.Builder()
                    .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                    .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_48000)
                    .build();

    private static final BluetoothLeAudioCodecConfig LC3_48KHZ_16KHZ_CONFIG =
            new BluetoothLeAudioCodecConfig.Builder()
                    .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                    .setSampleRate(
                            BluetoothLeAudioCodecConfig.SAMPLE_RATE_48000
                                    | BluetoothLeAudioCodecConfig.SAMPLE_RATE_16000)
                    .build();
    private static final List<BluetoothLeAudioCodecConfig> INPUT_CAPABILITIES_CONFIG =
            List.of(LC3_48KHZ_16KHZ_CONFIG);

    private static final List<BluetoothLeAudioCodecConfig> OUTPUT_CAPABILITIES_CONFIG =
            List.of(LC3_48KHZ_16KHZ_CONFIG);

    private static final List<BluetoothLeAudioCodecConfig> INPUT_SELECTABLE_CONFIG =
            List.of(LC3_16KHZ_CONFIG);

    private static final List<BluetoothLeAudioCodecConfig> OUTPUT_SELECTABLE_CONFIG =
            List.of(LC3_48KHZ_16KHZ_CONFIG);

    private static final BluetoothLeAudioCodecStatus LE_CODEC_STATUS =
            new BluetoothLeAudioCodecStatus(
                    LC3_16KHZ_CONFIG,
                    LC3_48KHZ_CONFIG,
                    INPUT_CAPABILITIES_CONFIG,
                    OUTPUT_CAPABILITIES_CONFIG,
                    INPUT_SELECTABLE_CONFIG,
                    OUTPUT_SELECTABLE_CONFIG);

    @Before
    public void setUp() {
        Assume.assumeTrue(
                TestUtils.isBleSupported(
                        InstrumentationRegistry.getInstrumentation().getContext()));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getInputCodecConfig() {
        assertThat(LE_CODEC_STATUS.getInputCodecConfig()).isEqualTo(LC3_16KHZ_CONFIG);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getOutputCodecConfig() {
        assertThat(LE_CODEC_STATUS.getOutputCodecConfig()).isEqualTo(LC3_48KHZ_CONFIG);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getInputCodecLocalCapabilities() {
        assertThat(LE_CODEC_STATUS.getInputCodecLocalCapabilities())
                .isEqualTo(INPUT_CAPABILITIES_CONFIG);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getOutputCodecLocalCapabilities() {
        assertThat(LE_CODEC_STATUS.getOutputCodecLocalCapabilities())
                .isEqualTo(OUTPUT_CAPABILITIES_CONFIG);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getInputCodecSelectableCapabilities() {
        assertThat(LE_CODEC_STATUS.getInputCodecSelectableCapabilities())
                .isEqualTo(INPUT_SELECTABLE_CONFIG);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getOutputCodecSelectableCapabilities() {
        assertThat(LE_CODEC_STATUS.getOutputCodecSelectableCapabilities())
                .isEqualTo(OUTPUT_SELECTABLE_CONFIG);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isInputCodecConfigSelectable() {
        assertThat(LE_CODEC_STATUS.isInputCodecConfigSelectable(LC3_16KHZ_CONFIG)).isTrue();
        assertThat(LE_CODEC_STATUS.isInputCodecConfigSelectable(LC3_48KHZ_CONFIG)).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isOutputCodecConfigSelectable() {
        assertThat(LE_CODEC_STATUS.isOutputCodecConfigSelectable(LC3_16KHZ_CONFIG)).isTrue();
        assertThat(LE_CODEC_STATUS.isOutputCodecConfigSelectable(LC3_48KHZ_CONFIG)).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void describeContents() {
        assertThat(LE_CODEC_STATUS.describeContents()).isEqualTo(0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void readWriteParcel() {
        Parcel parcel = Parcel.obtain();
        LE_CODEC_STATUS.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BluetoothLeAudioCodecStatus codecStatusFromParcel =
                BluetoothLeAudioCodecStatus.CREATOR.createFromParcel(parcel);
        assertThat(codecStatusFromParcel.getInputCodecConfig()).isEqualTo(LC3_16KHZ_CONFIG);
        assertThat(codecStatusFromParcel.getOutputCodecConfig()).isEqualTo(LC3_48KHZ_CONFIG);
        assertThat(codecStatusFromParcel.getInputCodecLocalCapabilities())
                .isEqualTo(INPUT_CAPABILITIES_CONFIG);
        assertThat(codecStatusFromParcel.getOutputCodecLocalCapabilities())
                .isEqualTo(OUTPUT_CAPABILITIES_CONFIG);
        assertThat(codecStatusFromParcel.getInputCodecSelectableCapabilities())
                .isEqualTo(INPUT_SELECTABLE_CONFIG);
        assertThat(codecStatusFromParcel.getOutputCodecSelectableCapabilities())
                .isEqualTo(OUTPUT_SELECTABLE_CONFIG);
    }
}
