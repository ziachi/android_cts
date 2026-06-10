/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.virtualdevice.cts.sensor;

import static android.hardware.SensorAdditionalInfo.TYPE_UNTRACKED_DELAY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.sensor.VirtualSensorAdditionalInfo;
import android.companion.virtualdevice.flags.Flags;
import android.hardware.SensorAdditionalInfo;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_SENSOR_ADDITIONAL_INFO)
public class VirtualSensorAdditionalInfoTest {

    private static final float[] VALUES_1 = new float[] {1.2f, 3.4f};
    private static final float[] VALUES_2 = new float[] {5.6f, 7.8f};

    @Rule
    public CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void parcelAndUnparcel_matches() {
        final VirtualSensorAdditionalInfo original =
                new VirtualSensorAdditionalInfo.Builder(TYPE_UNTRACKED_DELAY)
                        .addValues(VALUES_1)
                        .addValues(VALUES_2)
                        .build();
        assertThat(original.getType()).isEqualTo(TYPE_UNTRACKED_DELAY);
        assertThat(original.getValues()).containsExactly(VALUES_1, VALUES_2);

        final Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, /* flags= */ 0);
        parcel.setDataPosition(0);
        final VirtualSensorAdditionalInfo recreated =
                VirtualSensorAdditionalInfo.CREATOR.createFromParcel(parcel);
        assertThat(recreated.getType()).isEqualTo(original.getType());
        assertThat(recreated.getValues()).hasSize(original.getValues().size());
        for (int i = 0; i < recreated.getValues().size(); ++i) {
            assertThat(recreated.getValues().get(i)).isEqualTo(original.getValues().get(i));
        }
    }

    @Test
    public void unsupportedType_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new VirtualSensorAdditionalInfo.Builder(SensorAdditionalInfo.TYPE_FRAME_BEGIN));
    }

    @Test
    public void missingValues_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new VirtualSensorAdditionalInfo.Builder(TYPE_UNTRACKED_DELAY).build());
    }

    @Test
    public void valueSizeMismatch_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new VirtualSensorAdditionalInfo.Builder(TYPE_UNTRACKED_DELAY)
                        .addValues(VALUES_1)
                        .addValues(new float[] {9.0f}));
    }

    @Test
    public void invalidValueSize_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new VirtualSensorAdditionalInfo.Builder(TYPE_UNTRACKED_DELAY)
                        .addValues(new float[] {9.0f}));
        assertThrows(IllegalArgumentException.class, () ->
                new VirtualSensorAdditionalInfo.Builder(TYPE_UNTRACKED_DELAY)
                        .addValues(new float[] {1.0f, 2.0f, 3.0f}));
    }
}
