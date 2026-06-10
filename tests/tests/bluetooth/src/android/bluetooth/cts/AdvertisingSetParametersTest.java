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

import static android.bluetooth.BluetoothDevice.ADDRESS_TYPE_RANDOM;
import static android.bluetooth.BluetoothDevice.PHY_LE_1M;
import static android.bluetooth.BluetoothDevice.PHY_LE_2M;
import static android.bluetooth.BluetoothDevice.PHY_LE_CODED;
import static android.bluetooth.le.AdvertisingSetParameters.INTERVAL_LOW;
import static android.bluetooth.le.AdvertisingSetParameters.INTERVAL_MAX;
import static android.bluetooth.le.AdvertisingSetParameters.INTERVAL_MEDIUM;
import static android.bluetooth.le.AdvertisingSetParameters.INTERVAL_MIN;
import static android.bluetooth.le.AdvertisingSetParameters.TX_POWER_MAX;
import static android.bluetooth.le.AdvertisingSetParameters.TX_POWER_MEDIUM;
import static android.bluetooth.le.AdvertisingSetParameters.TX_POWER_MIN;

import static com.android.bluetooth.flags.Flags.FLAG_DIRECTED_ADVERTISING_API;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.bluetooth.le.AdvertisingSetParameters;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AdvertisingSetParametersTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        Assume.assumeTrue(
                TestUtils.isBleSupported(
                        InstrumentationRegistry.getInstrumentation().getTargetContext()));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createFromParcel() {
        final Parcel parcel = Parcel.obtain();
        try {
            AdvertisingSetParameters params = new AdvertisingSetParameters.Builder().build();
            params.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            AdvertisingSetParameters paramsFromParcel =
                    AdvertisingSetParameters.CREATOR.createFromParcel(parcel);
            assertParamsEquals(params, paramsFromParcel);
        } finally {
            parcel.recycle();
        }
    }

    @RequiresFlagsEnabled(FLAG_DIRECTED_ADVERTISING_API)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createFromParcelForDirectedAdvertising() {
        final Parcel parcel = Parcel.obtain();
        try {
            AdvertisingSetParameters params =
                    new AdvertisingSetParameters.Builder()
                            .setConnectable(true)
                            .setLegacyMode(true)
                            .setDirected(true)
                            .setHighDutyCycle(true)
                            .setPeerAddress("00:01:02:03:04:05")
                            .setPeerAddressType(ADDRESS_TYPE_RANDOM)
                            .build();
            params.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            AdvertisingSetParameters paramsFromParcel =
                    AdvertisingSetParameters.CREATOR.createFromParcel(parcel);
            assertThat(paramsFromParcel.isDirected()).isTrue();
            assertThat(paramsFromParcel.isHighDutyCycle()).isTrue();
            assertThat(paramsFromParcel.getPeerAddress()).isEqualTo("00:01:02:03:04:05");
            assertThat(paramsFromParcel.getPeerAddressType()).isEqualTo(ADDRESS_TYPE_RANDOM);
        } finally {
            parcel.recycle();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void defaultParameters() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder().build();

        assertThat(params.isConnectable()).isFalse();
        assertThat(params.isDiscoverable()).isTrue();
        assertThat(params.isScannable()).isFalse();
        assertThat(params.isLegacy()).isFalse();
        assertThat(params.isAnonymous()).isFalse();
        assertThat(params.includeTxPower()).isFalse();
        assertThat(params.getPrimaryPhy()).isEqualTo(PHY_LE_1M);
        assertThat(params.getSecondaryPhy()).isEqualTo(PHY_LE_1M);
        assertThat(params.getInterval()).isEqualTo(INTERVAL_LOW);
        assertThat(params.getTxPowerLevel()).isEqualTo(TX_POWER_MEDIUM);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isConnectable() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setConnectable(true).build();
        assertThat(params.isConnectable()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isDiscoverable() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setDiscoverable(false).build();
        assertThat(params.isDiscoverable()).isFalse();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isScannable() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setScannable(true).build();
        assertThat(params.isScannable()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isLegacyMode() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setLegacyMode(true).build();
        assertThat(params.isLegacy()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void includeTxPower() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setIncludeTxPower(true).build();
        assertThat(params.includeTxPower()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setPrimaryPhyWithInvalidValue() {
        // Set invalid value
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdvertisingSetParameters.Builder().setPrimaryPhy(PHY_LE_2M));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setPrimaryPhyWithLE1M() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setPrimaryPhy(PHY_LE_1M).build();
        assertThat(params.getPrimaryPhy()).isEqualTo(PHY_LE_1M);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setPrimaryPhyWithLECoded() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setPrimaryPhy(PHY_LE_CODED).build();
        assertThat(params.getPrimaryPhy()).isEqualTo(PHY_LE_CODED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setSecondaryPhyWithInvalidValue() {
        int INVALID_SECONDARY_PHY = -1;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdvertisingSetParameters.Builder()
                                .setSecondaryPhy(INVALID_SECONDARY_PHY));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setSecondaryPhyWithLE1M() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setSecondaryPhy(PHY_LE_1M).build();
        assertThat(params.getSecondaryPhy()).isEqualTo(PHY_LE_1M);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setSecondaryPhyWithLE2M() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setSecondaryPhy(PHY_LE_2M).build();
        assertThat(params.getSecondaryPhy()).isEqualTo(PHY_LE_2M);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setSecondaryPhyWithLECoded() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setSecondaryPhy(PHY_LE_CODED).build();
        assertThat(params.getSecondaryPhy()).isEqualTo(PHY_LE_CODED);
    }

    @RequiresFlagsEnabled(FLAG_DIRECTED_ADVERTISING_API)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setDirected() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder()
                        .setDirected(true)
                        .setPeerAddress("00:01:02:03:04:05")
                        .build();
        assertThat(params.isDirected()).isTrue();
    }

    @RequiresFlagsEnabled(FLAG_DIRECTED_ADVERTISING_API)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setHighDutyCycle() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder()
                        .setConnectable(true)
                        .setLegacyMode(true)
                        .setDirected(true)
                        .setHighDutyCycle(true)
                        .setPeerAddress("00:01:02:03:04:05")
                        .build();
        assertThat(params.isDirected()).isTrue();
    }

    @RequiresFlagsEnabled(FLAG_DIRECTED_ADVERTISING_API)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setPeerAddress() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setPeerAddress("00:01:02:03:04:05").build();
        assertThat(params.getPeerAddress()).isEqualTo("00:01:02:03:04:05");
    }

    @RequiresFlagsEnabled(FLAG_DIRECTED_ADVERTISING_API)
    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setPeerAddressType() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder()
                        .setPeerAddressType(ADDRESS_TYPE_RANDOM)
                        .build();
        assertThat(params.getPeerAddressType()).isEqualTo(ADDRESS_TYPE_RANDOM);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void intervalWithInvalidValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdvertisingSetParameters.Builder().setInterval(INTERVAL_MIN - 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdvertisingSetParameters.Builder().setInterval(INTERVAL_MAX + 1));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void interval() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setInterval(INTERVAL_MEDIUM).build();
        assertThat(params.getInterval()).isEqualTo(INTERVAL_MEDIUM);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void txPowerLevelWithInvalidValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdvertisingSetParameters.Builder().setTxPowerLevel(TX_POWER_MIN - 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdvertisingSetParameters.Builder().setTxPowerLevel(TX_POWER_MAX + 1));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void txPowerLevel() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setTxPowerLevel(TX_POWER_MEDIUM).build();
        assertThat(params.getTxPowerLevel()).isEqualTo(TX_POWER_MEDIUM);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isAnonymous() {
        AdvertisingSetParameters params =
                new AdvertisingSetParameters.Builder().setAnonymous(true).build();
        assertThat(params.isAnonymous()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void describeContents() {
        AdvertisingSetParameters params = new AdvertisingSetParameters.Builder().build();
        assertThat(params.describeContents()).isEqualTo(0);
    }

    private void assertParamsEquals(AdvertisingSetParameters p, AdvertisingSetParameters other) {
        assertThat(p).isNotNull();
        assertThat(other).isNotNull();

        assertThat(p.isConnectable()).isEqualTo(other.isConnectable());
        assertThat(p.isDiscoverable()).isEqualTo(other.isDiscoverable());
        assertThat(p.isScannable()).isEqualTo(other.isScannable());
        assertThat(p.isLegacy()).isEqualTo(other.isLegacy());
        assertThat(p.isAnonymous()).isEqualTo(other.isAnonymous());
        assertThat(p.includeTxPower()).isEqualTo(other.includeTxPower());
        assertThat(p.getPrimaryPhy()).isEqualTo(other.getPrimaryPhy());
        assertThat(p.getSecondaryPhy()).isEqualTo(other.getSecondaryPhy());
        assertThat(p.getInterval()).isEqualTo(other.getInterval());
        assertThat(p.getTxPowerLevel()).isEqualTo(other.getTxPowerLevel());
    }
}
