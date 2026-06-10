/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.TransportBlock;
import android.bluetooth.le.TransportDiscoveryData;
import android.os.Parcel;
import android.os.ParcelUuid;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test cases for {@link AdvertiseData}.
 *
 * <p>To run the test, use adb shell am instrument -e class 'android.bluetooth.le.AdvertiseDataTest'
 * -w 'com.android.bluetooth.tests/android.bluetooth.BluetoothTestRunner'
 */
@RunWith(AndroidJUnit4.class)
public class AdvertiseDataTest {

    private AdvertiseData.Builder mAdvertiseDataBuilder;

    @Before
    public void setUp() {
        Assume.assumeTrue(
                TestUtils.isBleSupported(
                        InstrumentationRegistry.getInstrumentation().getTargetContext()));
        mAdvertiseDataBuilder = new AdvertiseData.Builder();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void emptyData() {
        Parcel parcel = Parcel.obtain();
        AdvertiseData data = mAdvertiseDataBuilder.build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel = AdvertiseData.CREATOR.createFromParcel(parcel);
        assertThat(dataFromParcel).isEqualTo(data);
        assertThat(dataFromParcel.getIncludeDeviceName()).isFalse();
        assertThat(dataFromParcel.getIncludeTxPowerLevel()).isFalse();
        assertThat(dataFromParcel.getManufacturerSpecificData().size()).isEqualTo(0);
        assertThat(dataFromParcel.getServiceData()).isEmpty();
        assertThat(dataFromParcel.getServiceUuids()).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void emptyServiceUuid() {
        Parcel parcel = Parcel.obtain();
        AdvertiseData data = mAdvertiseDataBuilder.setIncludeDeviceName(true).build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel = AdvertiseData.CREATOR.createFromParcel(parcel);
        assertThat(dataFromParcel).isEqualTo(data);
        assertThat(dataFromParcel.getIncludeDeviceName()).isTrue();
        assertThat(dataFromParcel.getServiceUuids()).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void emptyManufacturerData() {
        Parcel parcel = Parcel.obtain();
        int manufacturerId = 50;
        byte[] manufacturerData = new byte[0];
        AdvertiseData data =
                mAdvertiseDataBuilder
                        .setIncludeDeviceName(true)
                        .addManufacturerData(manufacturerId, manufacturerData)
                        .build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel = AdvertiseData.CREATOR.createFromParcel(parcel);
        assertThat(dataFromParcel).isEqualTo(data);
        assertThat(dataFromParcel.getManufacturerSpecificData().get(manufacturerId)).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void emptyServiceData() {
        Parcel parcel = Parcel.obtain();
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        byte[] serviceData = new byte[0];
        AdvertiseData data =
                mAdvertiseDataBuilder
                        .setIncludeDeviceName(true)
                        .addServiceData(uuid, serviceData)
                        .build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel = AdvertiseData.CREATOR.createFromParcel(parcel);
        assertThat(dataFromParcel).isEqualTo(data);
        assertThat(dataFromParcel.getServiceData().get(uuid)).isEmpty();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void serviceUuid() {
        Parcel parcel = Parcel.obtain();
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        ParcelUuid uuid2 = ParcelUuid.fromString("0000110B-0000-1000-8000-00805F9B34FB");

        AdvertiseData data =
                mAdvertiseDataBuilder
                        .setIncludeDeviceName(true)
                        .addServiceUuid(uuid)
                        .addServiceUuid(uuid2)
                        .build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel = AdvertiseData.CREATOR.createFromParcel(parcel);
        assertThat(dataFromParcel).isEqualTo(data);
        assertThat(dataFromParcel.getServiceUuids()).contains(uuid);
        assertThat(dataFromParcel.getServiceUuids()).contains(uuid2);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void serviceSolicitationUuids() {
        AdvertiseData emptyData = mAdvertiseDataBuilder.build();
        assertThat(emptyData.getServiceSolicitationUuids()).isEmpty();

        Parcel parcel = Parcel.obtain();
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        ParcelUuid uuid2 = ParcelUuid.fromString("0000110B-0000-1000-8000-00805F9B34FB");

        AdvertiseData data =
                mAdvertiseDataBuilder
                        .setIncludeDeviceName(true)
                        .addServiceSolicitationUuid(uuid)
                        .addServiceSolicitationUuid(uuid2)
                        .build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel = AdvertiseData.CREATOR.createFromParcel(parcel);
        assertThat(dataFromParcel).isEqualTo(data);
        assertThat(dataFromParcel.getServiceSolicitationUuids()).contains(uuid);
        assertThat(dataFromParcel.getServiceSolicitationUuids()).contains(uuid2);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void manufacturerData() {
        Parcel parcel = Parcel.obtain();
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        ParcelUuid uuid2 = ParcelUuid.fromString("0000110B-0000-1000-8000-00805F9B34FB");

        int manufacturerId = 50;
        byte[] manufacturerData = new byte[] {(byte) 0xF0, 0x00, 0x02, 0x15};
        AdvertiseData data =
                mAdvertiseDataBuilder
                        .setIncludeDeviceName(true)
                        .addServiceUuid(uuid)
                        .addServiceUuid(uuid2)
                        .addManufacturerData(manufacturerId, manufacturerData)
                        .build();

        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel = AdvertiseData.CREATOR.createFromParcel(parcel);
        assertThat(dataFromParcel).isEqualTo(data);
        assertThat(dataFromParcel.getManufacturerSpecificData().get(manufacturerId))
                .isEqualTo(manufacturerData);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void serviceData() {
        Parcel parcel = Parcel.obtain();
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        byte[] serviceData = new byte[] {(byte) 0xF0, 0x00, 0x02, 0x15};
        AdvertiseData data =
                mAdvertiseDataBuilder
                        .setIncludeDeviceName(true)
                        .addServiceData(uuid, serviceData)
                        .build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel = AdvertiseData.CREATOR.createFromParcel(parcel);
        assertThat(dataFromParcel).isEqualTo(data);
        assertThat(dataFromParcel.getServiceData().get(uuid)).isEqualTo(serviceData);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void transportDiscoveryData() {
        Parcel parcel = Parcel.obtain();
        ParcelUuid uuid = ParcelUuid.fromString("0000110A-0000-1000-8000-00805F9B34FB");
        List<TransportBlock> transportBlocks = new ArrayList();
        transportBlocks.add(
                new TransportBlock(1, 0, 4, new byte[] {(byte) 0xF0, 0x00, 0x02, 0x15}));
        TransportDiscoveryData discoveryData = new TransportDiscoveryData(0, transportBlocks);
        AdvertiseData data =
                mAdvertiseDataBuilder
                        .setIncludeDeviceName(true)
                        .addTransportDiscoveryData(discoveryData)
                        .build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel = AdvertiseData.CREATOR.createFromParcel(parcel);

        assertThat(dataFromParcel.getTransportDiscoveryData().get(0).getTransportDataType())
                .isEqualTo(discoveryData.getTransportDataType());

        assertThat(
                        dataFromParcel
                                .getTransportDiscoveryData()
                                .get(0)
                                .getTransportBlocks()
                                .get(0)
                                .getOrgId())
                .isEqualTo(discoveryData.getTransportBlocks().get(0).getOrgId());

        assertThat(
                        dataFromParcel
                                .getTransportDiscoveryData()
                                .get(0)
                                .getTransportBlocks()
                                .get(0)
                                .getTdsFlags())
                .isEqualTo(discoveryData.getTransportBlocks().get(0).getTdsFlags());

        assertThat(
                        dataFromParcel
                                .getTransportDiscoveryData()
                                .get(0)
                                .getTransportBlocks()
                                .get(0)
                                .totalBytes())
                .isEqualTo(discoveryData.getTransportBlocks().get(0).totalBytes());

        assertThat(dataFromParcel.getTransportDiscoveryData()).containsExactly(discoveryData);

        assertThat(dataFromParcel).isEqualTo(data);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void includeTxPower() {
        Parcel parcel = Parcel.obtain();
        AdvertiseData data = mAdvertiseDataBuilder.setIncludeTxPowerLevel(true).build();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AdvertiseData dataFromParcel = AdvertiseData.CREATOR.createFromParcel(parcel);
        assertThat(dataFromParcel.getIncludeTxPowerLevel()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void describeContents() {
        AdvertiseData data = new AdvertiseData.Builder().build();
        assertThat(data.describeContents()).isEqualTo(0);
    }
}
