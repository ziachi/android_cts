/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.os.connectivity.cts;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.os.connectivity.CellularBatteryStats;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

public class CellularBatteryStatsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @RequiresFlagsEnabled(
            com.android.server.power.optimization.Flags.FLAG_STREAMLINED_CONNECTIVITY_BATTERY_STATS)
    @Test
    public void parceling() throws Throwable {
        CellularBatteryStats stats = new CellularBatteryStats(
                100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L, 900L, 1000L,
                new long[]{2000, 3000},
                new long[]{4000, 5000},
                new long[]{6000, 7000, 8000, 9000, 10000},
                11000L);

        Parcel out = Parcel.obtain();
        stats.writeToParcel(out, 0);
        byte[] bytes = out.marshall();

        Parcel in = Parcel.obtain();
        in.unmarshall(bytes, 0, bytes.length);
        in.setDataPosition(0);

        CellularBatteryStats actual = CellularBatteryStats.CREATOR.createFromParcel(in);
        assertThat(actual.getLoggingDurationMillis()).isEqualTo(100);
        assertThat(actual.getKernelActiveTimeMillis()).isEqualTo(200);
        assertThat(actual.getNumPacketsTx()).isEqualTo(300);
        assertThat(actual.getNumBytesTx()).isEqualTo(400);
        assertThat(actual.getNumPacketsRx()).isEqualTo(500);
        assertThat(actual.getNumBytesRx()).isEqualTo(600);
        assertThat(actual.getSleepTimeMillis()).isEqualTo(700);
        assertThat(actual.getIdleTimeMillis()).isEqualTo(800);
        assertThat(actual.getRxTimeMillis()).isEqualTo(900);
        assertThat(actual.getEnergyConsumedMaMillis()).isEqualTo(1000);
        assertThat(actual.getTimeInRatMicros(0)).isEqualTo(2000);
        assertThat(actual.getTimeInRatMicros(1)).isEqualTo(3000);
        assertThat(actual.getTimeInRxSignalStrengthLevelMicros(0)).isEqualTo(4000);
        assertThat(actual.getTimeInRxSignalStrengthLevelMicros(1)).isEqualTo(5000);
        assertThat(actual.getTxTimeMillis(0)).isEqualTo(6000);
        assertThat(actual.getTxTimeMillis(1)).isEqualTo(7000);
        assertThat(actual.getTxTimeMillis(2)).isEqualTo(8000);
        assertThat(actual.getTxTimeMillis(3)).isEqualTo(9000);
        assertThat(actual.getTxTimeMillis(4)).isEqualTo(10000);
        assertThat(actual.getMonitoredRailChargeConsumedMaMillis()).isEqualTo(11000);
    }
}
