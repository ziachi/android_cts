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

import static org.junit.Assert.assertThrows;

import android.bluetooth.le.ScanSettings;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for Bluetooth LE {@link ScanSettings}. */
@RunWith(AndroidJUnit4.class)
public class ScanSettingsTest {

    @Before
    public void setUp() {
        Assume.assumeTrue(
                TestUtils.isBleSupported(
                        InstrumentationRegistry.getInstrumentation().getContext()));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void defaultSettings() {
        ScanSettings settings = new ScanSettings.Builder().build();
        assertThat(settings.getCallbackType()).isEqualTo(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);
        assertThat(settings.getScanMode()).isEqualTo(ScanSettings.SCAN_MODE_LOW_POWER);
        assertThat(settings.getScanResultType()).isEqualTo(0);
        assertThat(settings.getReportDelayMillis()).isEqualTo(0);
        assertThat(settings.getLegacy()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void builderSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();

        // setScanMode boundary check
        assertThrows(
                "Check boundary of ScanSettings.Builder.setScanMode argument",
                IllegalArgumentException.class,
                () -> builder.setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC - 1));
        assertThrows(
                "Check boundary of ScanSettings.Builder.setScanMode argument",
                IllegalArgumentException.class,
                () -> builder.setScanMode(6)); // 6 = ScanSettings.SCAN_MODE_SCREEN_OFF_BALANCED + 1

        // setCallbackType boundary check
        assertThrows(
                "Check boundary of ScanSettings.Builder.setCallbackType argument",
                IllegalArgumentException.class,
                () -> builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES - 1));
        assertThrows(
                "Check boundary of ScanSettings.Builder.setCallbackType argument",
                IllegalArgumentException.class,
                () -> builder.setCallbackType(ScanSettings.CALLBACK_TYPE_MATCH_LOST + 1));

        // setScanResultType boundary check
        assertThrows(
                "Check boundary of ScanSettings.Builder.setScanResultType argument",
                IllegalArgumentException.class,
                () -> builder.setScanResultType(ScanSettings.SCAN_RESULT_TYPE_FULL - 1));
        assertThrows(
                "Check boundary of ScanSettings.Builder.setScanResultType argument",
                IllegalArgumentException.class,
                () -> builder.setScanResultType(ScanSettings.SCAN_RESULT_TYPE_ABBREVIATED + 1));

        assertThrows(
                "Check boundary of ScanSettings.Builder.setReportDelay argument",
                IllegalArgumentException.class,
                () -> builder.setReportDelay(-1));

        // setNumOfMatches boundary check
        assertThrows(
                "Check boundary of ScanSettings.Builder.setNumOfMatches argument",
                IllegalArgumentException.class,
                () -> builder.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT - 1));
        assertThrows(
                "Check boundary of ScanSettings.Builder.setNumOfMatches argument",
                IllegalArgumentException.class,
                () -> builder.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT + 1));

        // setMatchMode boundary check
        assertThrows(
                "Check boundary of ScanSettings.Builder.setMatchMode argument",
                IllegalArgumentException.class,
                () -> builder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE - 1));
        assertThrows(
                "Check boundary of ScanSettings.Builder.setMatchMode argument",
                IllegalArgumentException.class,
                () -> builder.setMatchMode(ScanSettings.MATCH_MODE_STICKY + 1));

        int cbType = ScanSettings.CALLBACK_TYPE_MATCH_LOST | ScanSettings.CALLBACK_TYPE_FIRST_MATCH;

        ScanSettings settings =
                builder.setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                        .setCallbackType(cbType)
                        .setScanResultType(ScanSettings.SCAN_RESULT_TYPE_ABBREVIATED)
                        .setReportDelay(0xDEAD)
                        .setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT)
                        .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                        .setLegacy(false)
                        .setPhy(0xCAFE)
                        .build();

        assertThat(settings.getScanMode()).isEqualTo(ScanSettings.SCAN_MODE_BALANCED);
        assertThat(settings.getCallbackType()).isEqualTo(cbType);
        assertThat(settings.getScanResultType())
                .isEqualTo(ScanSettings.SCAN_RESULT_TYPE_ABBREVIATED);
        assertThat(settings.getReportDelayMillis()).isEqualTo(0xDEAD);
        assertThat(settings.getLegacy()).isFalse();
        assertThat(settings.getPhy()).isEqualTo(0xCAFE);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void describeContents() {
        ScanSettings settings = new ScanSettings.Builder().build();
        assertThat(settings.describeContents()).isEqualTo(0);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @SmallTest
    @Test
    public void readWriteParcel() {
        final long reportDelayMillis = 60 * 1000;
        Parcel parcel = Parcel.obtain();
        ScanSettings settings =
                new ScanSettings.Builder()
                        .setReportDelay(reportDelayMillis)
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                        .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                        .build();
        settings.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ScanSettings settingsFromParcel = ScanSettings.CREATOR.createFromParcel(parcel);
        assertThat(settingsFromParcel.getReportDelayMillis()).isEqualTo(reportDelayMillis);
        assertThat(settings.getScanMode()).isEqualTo(ScanSettings.SCAN_MODE_LOW_LATENCY);
    }
}
