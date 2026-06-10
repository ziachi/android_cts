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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import android.car.cts.utils.VehiclePropertyUtils;
import android.car.feature.Flags;
import android.car.hardware.property.EvChargingConnectorType;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class EvChargingConnectorTypeTest extends AbstractCarLessTestCase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    /**
     * Test for {@link EvChargingConnectorType#toString()} is B vehicle properties flag is disabled
     */
    @RequiresFlagsDisabled(Flags.FLAG_ANDROID_B_VEHICLE_PROPERTIES)
    @Test
    public void testToStringAndroidBPropertiesDisabled() {
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.UNKNOWN))
                .isEqualTo("UNKNOWN");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.IEC_TYPE_1_AC))
                .isEqualTo("IEC_TYPE_1_AC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.IEC_TYPE_2_AC))
                .isEqualTo("IEC_TYPE_2_AC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.IEC_TYPE_3_AC))
                .isEqualTo("IEC_TYPE_3_AC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.IEC_TYPE_4_DC))
                .isEqualTo("IEC_TYPE_4_DC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.IEC_TYPE_1_CCS_DC))
                .isEqualTo("IEC_TYPE_1_CCS_DC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.IEC_TYPE_2_CCS_DC))
                .isEqualTo("IEC_TYPE_2_CCS_DC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.TESLA_HPWC))
                .isEqualTo("TESLA_HPWC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.TESLA_ROADSTER))
                .isEqualTo("TESLA_ROADSTER");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.TESLA_SUPERCHARGER))
                .isEqualTo("TESLA_SUPERCHARGER");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.GBT_AC))
                .isEqualTo("GBT_AC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.GBT_DC))
                .isEqualTo("GBT_DC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.OTHER))
                .isEqualTo("OTHER");
        assertThat(EvChargingConnectorType.toString(0x999)).isEqualTo("0x999");
    }

    /**
     * Test for {@link EvChargingConnectorType#toString()} is B vehicle properties flag is enabled
     */
    @RequiresFlagsEnabled(Flags.FLAG_ANDROID_B_VEHICLE_PROPERTIES)
    @Test
    public void testToStringAndroidBPropertiesEnabled() {
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.UNKNOWN))
                .isEqualTo("UNKNOWN");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.IEC_TYPE_1_AC))
                .isEqualTo("IEC_TYPE_1_AC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.IEC_TYPE_2_AC))
                .isEqualTo("IEC_TYPE_2_AC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.IEC_TYPE_3_AC))
                .isEqualTo("IEC_TYPE_3_AC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.IEC_TYPE_4_DC))
                .isEqualTo("IEC_TYPE_4_DC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.IEC_TYPE_1_CCS_DC))
                .isEqualTo("IEC_TYPE_1_CCS_DC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.IEC_TYPE_2_CCS_DC))
                .isEqualTo("IEC_TYPE_2_CCS_DC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.SAE_J3400_AC))
                .isEqualTo("SAE_J3400_AC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.TESLA_ROADSTER))
                .isEqualTo("TESLA_ROADSTER");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.SAE_J3400_DC))
                .isEqualTo("SAE_J3400_DC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.GBT_AC))
                .isEqualTo("GBT_AC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.GBT_DC))
                .isEqualTo("GBT_DC");
        assertThat(EvChargingConnectorType.toString(EvChargingConnectorType.OTHER))
                .isEqualTo("OTHER");
        assertThat(EvChargingConnectorType.toString(0x999)).isEqualTo("0x999");
    }

    /**
     * Test if all system properties have a mapped string value.
     */
    @Test
    public void testAllConnectorTypesAreMappedInToString() {
        List<Integer> connectorTypes =
                VehiclePropertyUtils.getIntegersFromDataEnums(EvChargingConnectorType.class);
        for (int connectorType : connectorTypes) {
            String propertyString = EvChargingConnectorType.toString(connectorType);
            assertThat(propertyString.startsWith("0x")).isFalse();
        }
    }
}
