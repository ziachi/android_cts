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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.bluetooth.le.PeriodicAdvertisingParameters;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PeriodicAdvertisingParametersTest {

    // Values copied over from PeriodicAdvertisingParameters class.
    private static final int INTERVAL_MIN = 80;
    private static final int INTERVAL_MAX = 65519;

    @Before
    public void setUp() {
        Assume.assumeTrue(
                TestUtils.isBleSupported(
                        InstrumentationRegistry.getInstrumentation().getContext()));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createFromParcel() {
        final Parcel parcel = Parcel.obtain();
        try {
            PeriodicAdvertisingParameters params =
                    new PeriodicAdvertisingParameters.Builder().build();
            params.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            PeriodicAdvertisingParameters paramsFromParcel =
                    PeriodicAdvertisingParameters.CREATOR.createFromParcel(parcel);
            assertParamsEquals(params, paramsFromParcel);
        } finally {
            parcel.recycle();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void defaultParameters() {
        PeriodicAdvertisingParameters params = new PeriodicAdvertisingParameters.Builder().build();
        assertThat(params.getIncludeTxPower()).isFalse();
        assertThat(params.getInterval()).isEqualTo(INTERVAL_MAX);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void includeTxPower() {
        PeriodicAdvertisingParameters params =
                new PeriodicAdvertisingParameters.Builder().setIncludeTxPower(true).build();
        assertThat(params.getIncludeTxPower()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void intervalWithInvalidValues() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PeriodicAdvertisingParameters.Builder()
                                .setInterval(INTERVAL_MIN - 1)
                                .build());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PeriodicAdvertisingParameters.Builder()
                                .setInterval(INTERVAL_MAX + 1)
                                .build());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void interval() {
        PeriodicAdvertisingParameters params =
                new PeriodicAdvertisingParameters.Builder().setInterval(INTERVAL_MIN).build();
        assertThat(params.getInterval()).isEqualTo(INTERVAL_MIN);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void describeContents() {
        PeriodicAdvertisingParameters params = new PeriodicAdvertisingParameters.Builder().build();
        assertThat(params.describeContents()).isEqualTo(0);
    }

    private void assertParamsEquals(
            PeriodicAdvertisingParameters p, PeriodicAdvertisingParameters other) {
        assertThat(p).isNotNull();
        assertThat(other).isNotNull();

        assertThat(other.getIncludeTxPower()).isEqualTo(p.getIncludeTxPower());
        assertThat(other.getInterval()).isEqualTo(p.getInterval());
    }
}
