/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.bluetooth.flags.Flags.FLAG_CHANNEL_SOUNDING_25Q2_APIS;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.DistanceMeasurementMethod;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

@RunWith(AndroidJUnit4.class)
public class DistanceMeasurementMethodTest {
    private Context mContext;
    private BluetoothAdapter mAdapter;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        Assume.assumeTrue(mAdapter.isDistanceMeasurementSupported() == FEATURE_SUPPORTED);
    }

    @After
    public void tearDown() {
        TestUtils.dropPermissionAsShellUid();
        mAdapter = null;
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createFromParcel() {
        final Parcel parcel = Parcel.obtain();
        try {
            DistanceMeasurementMethod method =
                    new DistanceMeasurementMethod.Builder(
                                    DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                            .setAzimuthAngleSupported(true)
                            .setAltitudeAngleSupported(true)
                            .build();
            method.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            DistanceMeasurementMethod methodFromParcel =
                    DistanceMeasurementMethod.CREATOR.createFromParcel(parcel);
            assertMethodEquals(method, methodFromParcel);
        } finally {
            parcel.recycle();
        }
    }

    @RequiresFlagsEnabled(FLAG_CHANNEL_SOUNDING_25Q2_APIS)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createFromParcelForMethodId() {
        final Parcel parcel = Parcel.obtain();
        try {
            DistanceMeasurementMethod method =
                    new DistanceMeasurementMethod.Builder(
                                    DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                            .build();
            method.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            DistanceMeasurementMethod methodFromParcel =
                    DistanceMeasurementMethod.CREATOR.createFromParcel(parcel);
            assertThat(methodFromParcel.getMethodId()).isEqualTo(method.getMethodId());
        } finally {
            parcel.recycle();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetId() {
        DistanceMeasurementMethod method =
                new DistanceMeasurementMethod.Builder(
                                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                        .build();
        assertThat(method.getId())
                .isEqualTo(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
    }

    @RequiresFlagsEnabled(FLAG_CHANNEL_SOUNDING_25Q2_APIS)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetMethodId() {
        DistanceMeasurementMethod method =
                new DistanceMeasurementMethod.Builder(
                                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                        .build();
        assertThat(method.getMethodId())
                .isEqualTo(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isAzimuthAngleSupported() {
        DistanceMeasurementMethod method =
                new DistanceMeasurementMethod.Builder(
                                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                        .setAzimuthAngleSupported(true)
                        .build();
        assertThat(method.isAzimuthAngleSupported()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isAltitudeAngleSupported() {
        DistanceMeasurementMethod method =
                new DistanceMeasurementMethod.Builder(
                                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                        .setAltitudeAngleSupported(true)
                        .build();
        assertThat(method.isAltitudeAngleSupported()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void validHashCode() {
        DistanceMeasurementMethod method =
                new DistanceMeasurementMethod.Builder(
                                DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                        .setAltitudeAngleSupported(true)
                        .build();
        assertThat(method.hashCode())
                .isEqualTo(
                        Objects.hash(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI));
    }

    private void assertMethodEquals(DistanceMeasurementMethod p, DistanceMeasurementMethod other) {
        assertThat(p).isNotNull();
        assertThat(other).isNotNull();

        assertThat(p.getId()).isEqualTo(other.getId());
        assertThat(p.isAzimuthAngleSupported()).isEqualTo(other.isAzimuthAngleSupported());
        assertThat(p.isAltitudeAngleSupported()).isEqualTo(other.isAltitudeAngleSupported());
    }
}
