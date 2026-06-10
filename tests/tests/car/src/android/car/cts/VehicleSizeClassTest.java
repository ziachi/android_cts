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

package android.car.cts;

import static android.car.feature.Flags.FLAG_ANDROID_B_VEHICLE_PROPERTIES;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.cts.utils.VehiclePropertyUtils;
import android.car.hardware.property.VehicleSizeClass;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class VehicleSizeClassTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_B_VEHICLE_PROPERTIES)
    public void testToString() {
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_TWO_SEATER))
                .isEqualTo("EPA_TWO_SEATER");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_MINICOMPACT))
                .isEqualTo("EPA_MINICOMPACT");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_SUBCOMPACT))
                .isEqualTo("EPA_SUBCOMPACT");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_COMPACT))
                .isEqualTo("EPA_COMPACT");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_MIDSIZE))
                .isEqualTo("EPA_MIDSIZE");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_LARGE))
                .isEqualTo("EPA_LARGE");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_SMALL_STATION_WAGON))
                .isEqualTo("EPA_SMALL_STATION_WAGON");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_MIDSIZE_STATION_WAGON))
                .isEqualTo("EPA_MIDSIZE_STATION_WAGON");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_LARGE_STATION_WAGON))
                .isEqualTo("EPA_LARGE_STATION_WAGON");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_SMALL_PICKUP_TRUCK))
                .isEqualTo("EPA_SMALL_PICKUP_TRUCK");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_STANDARD_PICKUP_TRUCK))
                .isEqualTo("EPA_STANDARD_PICKUP_TRUCK");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_VAN))
                .isEqualTo("EPA_VAN");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_MINIVAN))
                .isEqualTo("EPA_MINIVAN");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_SMALL_SUV))
                .isEqualTo("EPA_SMALL_SUV");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EPA_STANDARD_SUV))
                .isEqualTo("EPA_STANDARD_SUV");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EU_A_SEGMENT))
                .isEqualTo("EU_A_SEGMENT");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EU_B_SEGMENT))
                .isEqualTo("EU_B_SEGMENT");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EU_C_SEGMENT))
                .isEqualTo("EU_C_SEGMENT");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EU_D_SEGMENT))
                .isEqualTo("EU_D_SEGMENT");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EU_E_SEGMENT))
                .isEqualTo("EU_E_SEGMENT");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EU_F_SEGMENT))
                .isEqualTo("EU_F_SEGMENT");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EU_J_SEGMENT))
                .isEqualTo("EU_J_SEGMENT");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EU_M_SEGMENT))
                .isEqualTo("EU_M_SEGMENT");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.EU_S_SEGMENT))
                .isEqualTo("EU_S_SEGMENT");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.JPN_KEI))
                .isEqualTo("JPN_KEI");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.JPN_SMALL_SIZE))
                .isEqualTo("JPN_SMALL_SIZE");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.JPN_NORMAL_SIZE))
                .isEqualTo("JPN_NORMAL_SIZE");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.US_GVWR_CLASS_1_CV))
                .isEqualTo("US_GVWR_CLASS_1_CV");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.US_GVWR_CLASS_2_CV))
                .isEqualTo("US_GVWR_CLASS_2_CV");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.US_GVWR_CLASS_3_CV))
                .isEqualTo("US_GVWR_CLASS_3_CV");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.US_GVWR_CLASS_4_CV))
                .isEqualTo("US_GVWR_CLASS_4_CV");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.US_GVWR_CLASS_5_CV))
                .isEqualTo("US_GVWR_CLASS_5_CV");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.US_GVWR_CLASS_6_CV))
                .isEqualTo("US_GVWR_CLASS_6_CV");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.US_GVWR_CLASS_7_CV))
                .isEqualTo("US_GVWR_CLASS_7_CV");
        assertThat(VehicleSizeClass.toString(
                VehicleSizeClass.US_GVWR_CLASS_8_CV))
                .isEqualTo("US_GVWR_CLASS_8_CV");
        assertThat(VehicleSizeClass.toString(35)).isEqualTo("0x23");
        assertThat(VehicleSizeClass.toString(12)).isEqualTo("0xc");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_B_VEHICLE_PROPERTIES)
    public void testAllVehicleSizeClassesAreMappedInToString() {
        List<Integer> vehicleSizeClasses =
                VehiclePropertyUtils.getIntegersFromDataEnums(VehicleSizeClass.class);
        for (Integer vehicleSizeClass : vehicleSizeClasses) {
            String vehicleSizeClassestring = VehicleSizeClass.toString(
                    vehicleSizeClass);
            assertWithMessage("%s starts with 0x", vehicleSizeClassestring).that(
                    vehicleSizeClassestring.startsWith("0x")).isFalse();
        }
    }
}
