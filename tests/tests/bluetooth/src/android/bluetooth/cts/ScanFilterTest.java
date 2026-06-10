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

import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAssignedNumbers.OrganizationId;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.TransportBlockFilter;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.os.ParcelUuid;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Unit test cases for Bluetooth LE scan filters.
 *
 * <p>To run this test, use adb shell am instrument -e class 'android.bluetooth.ScanFilterTest' -w
 * 'com.android.bluetooth.tests/android.bluetooth.BluetoothTestRunner'
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ScanFilterTest {

    private static final String LOCAL_NAME = "Ped";
    private static final String DEVICE_MAC = "01:02:03:04:05:AB";
    private static final String DEVICE_MAC_RANDOM_STATIC = "C1:02:03:04:05:AB";
    private static final String DEVICE_MAC_RANDOM_RESOLVABLE = "41:02:03:04:05:AB";
    private static final String DEVICE_MAC_RANDOM_NON_RESOLVABLE = "01:02:03:04:05:AB";

    private static final String UUID1 = "0000110a-0000-1000-8000-00805f9b34fb";
    private static final String UUID2 = "0000110b-0000-1000-8000-00805f9b34fb";
    private static final String UUID3 = "0000110c-0000-1000-8000-00805f9b34fb";
    private static final int AD_TYPE_RESOLVABLE_SET_IDENTIFIER = 0x2e;

    private ScanResult mScanResult;
    private ScanFilter.Builder mFilterBuilder;
    private BluetoothAdapter mBluetoothAdapter;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Assume.assumeTrue(TestUtils.isBleSupported(context));

        byte[] scanRecord =
                new byte[] {
                    0x02,
                    0x01,
                    0x1a, // advertising flags
                    0x05,
                    0x02,
                    0x0b,
                    0x11,
                    0x0a,
                    0x11, // 16 bit service uuids
                    0x04,
                    0x09,
                    0x50,
                    0x65,
                    0x64, // setName
                    0x02,
                    0x0A,
                    (byte) 0xec, // tx power level
                    0x05,
                    0x16,
                    0x0b,
                    0x11,
                    0x50,
                    0x64, // service data
                    0x05,
                    (byte) 0xff,
                    (byte) 0xe0,
                    0x00,
                    0x02,
                    0x15, // manufacturer specific data
                    0x05,
                    0x14,
                    0x0c,
                    0x11,
                    0x0a,
                    0x11, // 16 bit service solicitation uuids
                    0x03,
                    0x50,
                    0x01,
                    0x02, // an unknown data type won't cause trouble
                    0x07,
                    0x2E,
                    0x01,
                    0x02,
                    0x03,
                    0x04,
                    0x05,
                    0x06 // resolvable set identifier
                };

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Bluetooth is not supported
            assertThat(
                            context.getPackageManager()
                                    .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
                    .isFalse();
        } else {
            assertThat(
                            context.getPackageManager()
                                    .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
                    .isTrue();
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(DEVICE_MAC);
            mScanResult =
                    new ScanResult(
                            device, TestUtils.parseScanRecord(scanRecord), -10, 1397545200000000L);
            mFilterBuilder = new ScanFilter.Builder();
            TestUtils.adoptPermissionAsShellUid(BLUETOOTH_PRIVILEGED);
            assertThat(BlockingBluetoothAdapter.enable()).isTrue();
        }
    }

    @After
    public void tearDown() {
        if (mFilterBuilder != null) {
            TestUtils.dropPermissionAsShellUid();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setNameFilter() {
        ScanFilter filter = mFilterBuilder.setDeviceName(LOCAL_NAME).build();
        assertThat(filter.getDeviceName()).isEqualTo(LOCAL_NAME);
        assertThat(filter.matches(mScanResult)).isTrue();

        filter = mFilterBuilder.setDeviceName("Pem").build();
        assertThat(filter.matches(mScanResult)).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void deviceAddressFilter() {
        ScanFilter filter = mFilterBuilder.setDeviceAddress(DEVICE_MAC).build();
        assertThat(filter.getDeviceAddress()).isEqualTo(DEVICE_MAC);
        assertThat(filter.matches(mScanResult)).isTrue();

        filter = mFilterBuilder.setDeviceAddress("11:22:33:44:55:66").build();
        assertThat(filter.matches(mScanResult)).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setDeviceAddressForTypeAndIrk() {
        byte[] irk = new byte[ScanFilter.Builder.LEN_IRK_OCTETS];
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mFilterBuilder.setDeviceAddress(
                                "AA:INVALID", BluetoothDevice.ADDRESS_TYPE_PUBLIC, irk));

        byte[] invalidIrkLength = new byte[0];
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mFilterBuilder.setDeviceAddress(
                                DEVICE_MAC, BluetoothDevice.ADDRESS_TYPE_PUBLIC, invalidIrkLength));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mFilterBuilder.setDeviceAddress(
                                DEVICE_MAC, BluetoothDevice.ADDRESS_TYPE_UNKNOWN, irk));

        assertThrows(
                NullPointerException.class,
                () ->
                        mFilterBuilder.setDeviceAddress(
                                null, BluetoothDevice.ADDRESS_TYPE_UNKNOWN, irk));

        ScanFilter filter =
                mFilterBuilder
                        .setDeviceAddress(DEVICE_MAC, BluetoothDevice.ADDRESS_TYPE_PUBLIC, irk)
                        .build();
        assertThat(filter.getAddressType()).isEqualTo(BluetoothDevice.ADDRESS_TYPE_PUBLIC);
        assertThat(filter.getIrk()).isEqualTo(irk);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mFilterBuilder.setDeviceAddress(
                                DEVICE_MAC_RANDOM_RESOLVABLE,
                                BluetoothDevice.ADDRESS_TYPE_RANDOM,
                                irk));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mFilterBuilder.setDeviceAddress(
                                DEVICE_MAC_RANDOM_NON_RESOLVABLE,
                                BluetoothDevice.ADDRESS_TYPE_RANDOM,
                                irk));

        filter =
                mFilterBuilder
                        .setDeviceAddress(
                                DEVICE_MAC_RANDOM_STATIC, BluetoothDevice.ADDRESS_TYPE_RANDOM, irk)
                        .build();
        assertThat(filter.getAddressType()).isEqualTo(BluetoothDevice.ADDRESS_TYPE_RANDOM);
        assertThat(filter.getIrk()).isEqualTo(irk);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setServiceUuidFilter() {
        ScanFilter filter = mFilterBuilder.setServiceUuid(ParcelUuid.fromString(UUID1)).build();
        assertThat(filter.getServiceUuid().toString()).isEqualTo(UUID1);
        assertThat(filter.matches(mScanResult)).isTrue();

        filter = mFilterBuilder.setServiceUuid(ParcelUuid.fromString(UUID3)).build();
        assertThat(filter.getServiceUuid().toString()).isEqualTo(UUID3);
        assertThat(filter.matches(mScanResult)).isFalse();

        ParcelUuid mask = ParcelUuid.fromString("FFFFFFF0-FFFF-FFFF-FFFF-FFFFFFFFFFFF");
        filter = mFilterBuilder.setServiceUuid(ParcelUuid.fromString(UUID3), mask).build();
        assertThat(filter.getServiceUuidMask().toString()).isEqualTo(mask.toString());
        assertThat(filter.matches(mScanResult)).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setServiceSolicitationUuidFilter() {
        ScanFilter filter =
                mFilterBuilder.setServiceSolicitationUuid(ParcelUuid.fromString(UUID1)).build();
        assertThat(filter.getServiceSolicitationUuid().toString()).isEqualTo(UUID1);
        assertThat(filter.matches(mScanResult)).isTrue();

        filter = mFilterBuilder.setServiceSolicitationUuid(ParcelUuid.fromString(UUID2)).build();
        assertThat(filter.getServiceSolicitationUuid().toString()).isEqualTo(UUID2);
        assertThat(filter.matches(mScanResult)).isFalse();

        ParcelUuid mask = ParcelUuid.fromString("FFFFFFF0-FFFF-FFFF-FFFF-FFFFFFFFFFFF");
        filter =
                mFilterBuilder
                        .setServiceSolicitationUuid(ParcelUuid.fromString(UUID3), mask)
                        .build();
        assertThat(filter.getServiceSolicitationUuidMask().toString()).isEqualTo(mask.toString());
        assertThat(filter.matches(mScanResult)).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setServiceDataFilter() {
        byte[] setServiceData = new byte[] {0x50, 0x64};
        ParcelUuid serviceDataUuid = ParcelUuid.fromString(UUID2);
        ScanFilter filter = mFilterBuilder.setServiceData(serviceDataUuid, setServiceData).build();
        assertThat(filter.getServiceDataUuid()).isEqualTo(serviceDataUuid);
        assertThat(filter.matches(mScanResult)).isTrue();

        byte[] emptyData = new byte[0];
        filter = mFilterBuilder.setServiceData(serviceDataUuid, emptyData).build();
        assertThat(filter.matches(mScanResult)).isTrue();

        byte[] prefixData = new byte[] {0x50};
        filter = mFilterBuilder.setServiceData(serviceDataUuid, prefixData).build();
        assertThat(filter.matches(mScanResult)).isTrue();

        byte[] nonMatchData = new byte[] {0x51, 0x64};
        byte[] mask = new byte[] {(byte) 0x00, (byte) 0xFF};
        filter = mFilterBuilder.setServiceData(serviceDataUuid, nonMatchData, mask).build();
        assertThat(filter.getServiceData()).isEqualTo(nonMatchData);
        assertThat(filter.getServiceDataMask()).isEqualTo(mask);
        assertThat(filter.matches(mScanResult)).isTrue();

        filter = mFilterBuilder.setServiceData(serviceDataUuid, nonMatchData).build();
        assertThat(filter.matches(mScanResult)).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setManufacturerSpecificData() {
        byte[] manufacturerData = new byte[] {0x02, 0x15};
        int manufacturerId = 0xE0;
        ScanFilter filter =
                mFilterBuilder.setManufacturerData(manufacturerId, manufacturerData).build();
        assertThat(filter.getManufacturerId()).isEqualTo(manufacturerId);
        assertThat(filter.getManufacturerData()).isEqualTo(manufacturerData);
        assertThat(filter.matches(mScanResult)).isTrue();

        byte[] emptyData = new byte[0];
        filter = mFilterBuilder.setManufacturerData(manufacturerId, emptyData).build();
        assertThat(filter.matches(mScanResult)).isTrue();

        byte[] prefixData = new byte[] {0x02};
        filter = mFilterBuilder.setManufacturerData(manufacturerId, prefixData).build();
        assertThat(filter.matches(mScanResult)).isTrue();

        // Test data mask
        byte[] nonMatchData = new byte[] {0x02, 0x14};
        filter = mFilterBuilder.setManufacturerData(manufacturerId, nonMatchData).build();
        assertThat(filter.matches(mScanResult)).isFalse();
        byte[] mask = new byte[] {(byte) 0xFF, (byte) 0x00};
        filter = mFilterBuilder.setManufacturerData(manufacturerId, nonMatchData, mask).build();
        assertThat(filter.getManufacturerId()).isEqualTo(manufacturerId);
        assertThat(filter.getManufacturerData()).isEqualTo(nonMatchData);
        assertThat(filter.getManufacturerDataMask()).isEqualTo(mask);
        assertThat(filter.matches(mScanResult)).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setAdvertisingDataTypeWithData() {
        byte[] adData = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
        byte[] adDataMask = {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
        };
        ScanFilter filter =
                mFilterBuilder
                        .setAdvertisingDataTypeWithData(
                                AD_TYPE_RESOLVABLE_SET_IDENTIFIER, adData, adDataMask)
                        .build();
        assertThat(filter.getAdvertisingDataType()).isEqualTo(AD_TYPE_RESOLVABLE_SET_IDENTIFIER);
        assertThat(filter.getAdvertisingData()).isEqualTo(adData);
        assertThat(filter.getAdvertisingDataMask()).isEqualTo(adDataMask);
        assertThat(filter.matches(mScanResult)).isTrue();
        filter = mFilterBuilder.setAdvertisingDataTypeWithData(0x01, adData, adDataMask).build();
        assertThat(filter.matches(mScanResult)).isFalse();
        byte[] nonMatchAdData = {0x01, 0x02, 0x04, 0x04, 0x05, 0x06};
        filter =
                mFilterBuilder
                        .setAdvertisingDataTypeWithData(
                                AD_TYPE_RESOLVABLE_SET_IDENTIFIER, nonMatchAdData, adDataMask)
                        .build();
        assertThat(filter.matches(mScanResult)).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void readWriteParcel() {
        ScanFilter filter = mFilterBuilder.build();
        testReadWriteParcelForFilter(filter);

        filter = mFilterBuilder.setDeviceName(LOCAL_NAME).build();
        testReadWriteParcelForFilter(filter);

        filter = mFilterBuilder.setDeviceAddress("11:22:33:44:55:66").build();
        testReadWriteParcelForFilter(filter);

        filter = mFilterBuilder.setServiceUuid(ParcelUuid.fromString(UUID3)).build();
        testReadWriteParcelForFilter(filter);

        filter =
                mFilterBuilder
                        .setServiceUuid(
                                ParcelUuid.fromString(UUID3),
                                ParcelUuid.fromString("FFFFFFF0-FFFF-FFFF-FFFF-FFFFFFFFFFFF"))
                        .build();
        testReadWriteParcelForFilter(filter);

        byte[] serviceData = new byte[] {0x50, 0x64};

        ParcelUuid serviceDataUuid = ParcelUuid.fromString(UUID2);
        filter = mFilterBuilder.setServiceData(serviceDataUuid, serviceData).build();
        testReadWriteParcelForFilter(filter);

        filter = mFilterBuilder.setServiceData(serviceDataUuid, new byte[0]).build();
        testReadWriteParcelForFilter(filter);

        byte[] serviceDataMask = new byte[] {(byte) 0xFF, (byte) 0xFF};
        filter =
                mFilterBuilder
                        .setServiceData(serviceDataUuid, serviceData, serviceDataMask)
                        .build();
        testReadWriteParcelForFilter(filter);

        byte[] manufacturerData = new byte[] {0x02, 0x15};
        int manufacturerId = 0xE0;
        filter = mFilterBuilder.setManufacturerData(manufacturerId, manufacturerData).build();
        testReadWriteParcelForFilter(filter);

        filter = mFilterBuilder.setServiceData(serviceDataUuid, new byte[0]).build();
        testReadWriteParcelForFilter(filter);

        byte[] manufacturerDataMask = new byte[] {(byte) 0xFF, (byte) 0xFF};
        filter =
                mFilterBuilder
                        .setManufacturerData(manufacturerId, manufacturerData, manufacturerDataMask)
                        .build();
        testReadWriteParcelForFilter(filter);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void describeContents() {
        final int expected = 0;
        assertThat(new ScanFilter.Builder().build().describeContents()).isEqualTo(expected);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void builderSetTransportBlockFilter() {
        final int orgId = OrganizationId.BLUETOOTH_SIG;
        final int tdsFlag = 0x2;
        final int tdsFlagMask = 0b11;
        final byte[] transportData = new byte[] {0x42, 0x43};
        final byte[] transportDataMask = new byte[] {0x44, 0x45};

        TransportBlockFilter transportBlockFilter =
                new TransportBlockFilter.Builder(orgId)
                        .setTdsFlags(tdsFlag, tdsFlagMask)
                        .setTransportData(transportData, transportDataMask)
                        .build();

        Permissions.enforceEachPermissions(
                () -> mBluetoothAdapter.getOffloadedTransportDiscoveryDataScanSupported(),
                List.of(BLUETOOTH_SCAN, BLUETOOTH_PRIVILEGED));

        try (var p = Permissions.withPermissions(BLUETOOTH_SCAN, BLUETOOTH_PRIVILEGED)) {
            if (mBluetoothAdapter.getOffloadedTransportDiscoveryDataScanSupported()
                    != FEATURE_SUPPORTED) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mFilterBuilder.setTransportBlockFilter(transportBlockFilter));
                // Ignore test when device does not support the feature
                Assume.assumeTrue(false);
                return;
            }
        }

        Permissions.enforceEachPermissions(
                () -> mFilterBuilder.setTransportBlockFilter(transportBlockFilter),
                List.of(BLUETOOTH_SCAN, BLUETOOTH_PRIVILEGED));

        final ScanFilter filter;
        try (var p = Permissions.withPermissions(BLUETOOTH_SCAN, BLUETOOTH_PRIVILEGED)) {
            filter = mFilterBuilder.setTransportBlockFilter(transportBlockFilter).build();
        }

        final TransportBlockFilter returnedTransportBlockFilter = filter.getTransportBlockFilter();
        assertThat(returnedTransportBlockFilter).isNotNull();
        assertThat(returnedTransportBlockFilter.getOrgId()).isEqualTo(orgId);
        assertThat(returnedTransportBlockFilter.getTdsFlags()).isEqualTo(tdsFlag);
        assertThat(returnedTransportBlockFilter.getTdsFlagsMask()).isEqualTo(tdsFlagMask);
        assertThat(returnedTransportBlockFilter.getTransportData()).isEqualTo(transportData);
        assertThat(returnedTransportBlockFilter.getTransportDataMask())
                .isEqualTo(transportDataMask);
    }

    private void testReadWriteParcelForFilter(ScanFilter filter) {
        Parcel parcel = Parcel.obtain();
        filter.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ScanFilter filterFromParcel = ScanFilter.CREATOR.createFromParcel(parcel);
        assertThat(filterFromParcel).isEqualTo(filter);
    }
}
