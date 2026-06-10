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
import static android.bluetooth.le.ChannelSoundingParams.CS_SECURITY_LEVEL_TWO;
import static android.bluetooth.le.ChannelSoundingParams.LOCATION_TYPE_OUTDOOR;
import static android.bluetooth.le.ChannelSoundingParams.SIGHT_TYPE_LINE_OF_SIGHT;
import static android.bluetooth.le.DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI;
import static android.bluetooth.le.DistanceMeasurementParams.REPORT_FREQUENCY_HIGH;
import static android.bluetooth.le.DistanceMeasurementParams.REPORT_FREQUENCY_LOW;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ChannelSoundingParams;
import android.bluetooth.le.DistanceMeasurementParams;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;
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

@RunWith(AndroidJUnit4.class)
public class DistanceMeasurementParamsTest {
    private Context mContext;
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mDevice;

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

        mDevice = mAdapter.getRemoteDevice("11:22:33:44:55:66");
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
            DistanceMeasurementParams params =
                    new DistanceMeasurementParams.Builder(mDevice).build();
            params.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            DistanceMeasurementParams paramsFromParcel =
                    DistanceMeasurementParams.CREATOR.createFromParcel(parcel);
            assertParamsEquals(params, paramsFromParcel);
        } finally {
            parcel.recycle();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void defaultParameters() {
        DistanceMeasurementParams params = new DistanceMeasurementParams.Builder(mDevice).build();
        assertThat(params.getDurationSeconds())
                .isEqualTo(DistanceMeasurementParams.getDefaultDurationSeconds());
        assertThat(params.getFrequency()).isEqualTo(REPORT_FREQUENCY_LOW);
        assertThat(params.getMethodId()).isEqualTo(DISTANCE_MEASUREMENT_METHOD_RSSI);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetDevice() {
        DistanceMeasurementParams params = new DistanceMeasurementParams.Builder(mDevice).build();
        assertThat(params.getDevice()).isEqualTo(mDevice);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetDurationSeconds() {
        DistanceMeasurementParams params =
                new DistanceMeasurementParams.Builder(mDevice).setDurationSeconds(120).build();
        assertThat(params.getDurationSeconds()).isEqualTo(120);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetFrequency() {
        DistanceMeasurementParams params =
                new DistanceMeasurementParams.Builder(mDevice)
                        .setFrequency(REPORT_FREQUENCY_HIGH)
                        .build();
        assertThat(params.getFrequency()).isEqualTo(REPORT_FREQUENCY_HIGH);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetMethodId() {
        DistanceMeasurementParams params =
                new DistanceMeasurementParams.Builder(mDevice)
                        .setMethodId(DISTANCE_MEASUREMENT_METHOD_RSSI)
                        .build();
        assertThat(params.getMethodId()).isEqualTo(DISTANCE_MEASUREMENT_METHOD_RSSI);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetChannelSoundingParams() {
        ChannelSoundingParams csParams =
                new ChannelSoundingParams.Builder().setSightType(SIGHT_TYPE_LINE_OF_SIGHT).build();
        DistanceMeasurementParams params =
                new DistanceMeasurementParams.Builder(mDevice)
                        .setChannelSoundingParams(csParams)
                        .build();
        assertThat(params.getChannelSoundingParams()).isEqualTo(csParams);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void readWriteParcelForCs() {
        Parcel parcel = Parcel.obtain();
        ChannelSoundingParams csParams =
                new ChannelSoundingParams.Builder()
                        .setSightType(SIGHT_TYPE_LINE_OF_SIGHT)
                        .setLocationType(LOCATION_TYPE_OUTDOOR)
                        .setCsSecurityLevel(CS_SECURITY_LEVEL_TWO)
                        .build();
        DistanceMeasurementParams params =
                new DistanceMeasurementParams.Builder(mDevice)
                        .setChannelSoundingParams(csParams)
                        .build();
        params.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DistanceMeasurementParams paramsFromParcel =
                DistanceMeasurementParams.CREATOR.createFromParcel(parcel);
        ChannelSoundingParams csParamsFromParcel = paramsFromParcel.getChannelSoundingParams();
        assertThat(csParamsFromParcel.getSightType()).isEqualTo(csParams.getSightType());
        assertThat(csParamsFromParcel.getLocationType()).isEqualTo(csParams.getLocationType());
        assertThat(csParamsFromParcel.getCsSecurityLevel())
                .isEqualTo(csParams.getCsSecurityLevel());
    }

    private void assertParamsEquals(DistanceMeasurementParams p, DistanceMeasurementParams other) {
        assertThat(p).isNotNull();
        assertThat(other).isNotNull();

        assertThat(other.getDevice()).isEqualTo(p.getDevice());
        assertThat(other.getDurationSeconds()).isEqualTo(p.getDurationSeconds());
        assertThat(other.getFrequency()).isEqualTo(p.getFrequency());
        assertThat(other.getMethodId()).isEqualTo(p.getMethodId());
    }
}
