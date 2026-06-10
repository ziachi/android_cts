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

import android.bluetooth.BluetoothHidDeviceAppSdpSettings;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test cases for {@link BluetoothHidDeviceAppSdpSettings}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothHidDeviceAppSdpSettingsTest {
    @Test
    public void getters() {
        String name = "test-name";
        String description = "test-description";
        String provider = "test-provider";
        byte subclass = 1;
        byte[] descriptors = new byte[] {10};
        BluetoothHidDeviceAppSdpSettings settings =
                new BluetoothHidDeviceAppSdpSettings(
                        name, description, provider, subclass, descriptors);
        assertThat(settings.getName()).isEqualTo(name);
        assertThat(settings.getDescription()).isEqualTo(description);
        assertThat(settings.getProvider()).isEqualTo(provider);
        assertThat(settings.getSubclass()).isEqualTo(subclass);
        assertThat(settings.getDescriptors()).isEqualTo(descriptors);
    }
}
