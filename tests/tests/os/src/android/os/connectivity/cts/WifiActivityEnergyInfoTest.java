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
import android.os.connectivity.WifiActivityEnergyInfo;

import org.junit.Test;

public class WifiActivityEnergyInfoTest {

    @Test
    public void parceling() {
        WifiActivityEnergyInfo info = new WifiActivityEnergyInfo(100,
                WifiActivityEnergyInfo.STACK_STATE_STATE_ACTIVE, 200, 300, 400, 500);

        Parcel out = Parcel.obtain();
        info.writeToParcel(out, 0);
        byte[] bytes = out.marshall();

        Parcel in = Parcel.obtain();
        in.unmarshall(bytes, 0, bytes.length);
        in.setDataPosition(0);

        WifiActivityEnergyInfo actual = WifiActivityEnergyInfo.CREATOR.createFromParcel(in);
        assertThat(actual.getTimeSinceBootMillis()).isEqualTo(100);
        assertThat(actual.getStackState()).isEqualTo(
                WifiActivityEnergyInfo.STACK_STATE_STATE_ACTIVE);
        assertThat(actual.getControllerTxDurationMillis()).isEqualTo(200);
        assertThat(actual.getControllerRxDurationMillis()).isEqualTo(300);
        assertThat(actual.getControllerScanDurationMillis()).isEqualTo(400);
        assertThat(actual.getControllerIdleDurationMillis()).isEqualTo(500);
    }
}
