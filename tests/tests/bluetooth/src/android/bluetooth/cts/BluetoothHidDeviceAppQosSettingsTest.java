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

import android.bluetooth.BluetoothHidDeviceAppQosSettings;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothHidDeviceAppQosSettingsTest {
    private final int TEST_SERVICE_TYPE = BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT;
    private final int TEST_TOKEN_RATE = 800;
    private final int TEST_TOKEN_BUCKET_SIZE = 9;
    private final int TEST_PEAK_BANDWIDTH = 10;
    private final int TEST_LATENCY = 11250;
    private final int TEST_DELAY_VARIATION = BluetoothHidDeviceAppQosSettings.MAX;

    @Test
    public void allMethods() {
        BluetoothHidDeviceAppQosSettings bluetoothHidDeviceAppQosSettings =
                new BluetoothHidDeviceAppQosSettings(
                        BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                        TEST_TOKEN_RATE,
                        TEST_TOKEN_BUCKET_SIZE,
                        TEST_PEAK_BANDWIDTH,
                        TEST_LATENCY,
                        TEST_DELAY_VARIATION);
        assertThat(bluetoothHidDeviceAppQosSettings.getServiceType()).isEqualTo(TEST_SERVICE_TYPE);
        assertThat(bluetoothHidDeviceAppQosSettings.getLatency()).isEqualTo(TEST_LATENCY);
        assertThat(bluetoothHidDeviceAppQosSettings.getTokenRate()).isEqualTo(TEST_TOKEN_RATE);
        assertThat(bluetoothHidDeviceAppQosSettings.getPeakBandwidth())
                .isEqualTo(TEST_PEAK_BANDWIDTH);
        assertThat(bluetoothHidDeviceAppQosSettings.getDelayVariation())
                .isEqualTo(TEST_DELAY_VARIATION);
        assertThat(bluetoothHidDeviceAppQosSettings.getTokenBucketSize())
                .isEqualTo(TEST_TOKEN_BUCKET_SIZE);
    }
}
