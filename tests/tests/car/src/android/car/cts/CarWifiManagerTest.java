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

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.car.Car;
import android.car.feature.Flags;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.car.wifi.CarWifiManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.wifi.CarWifiDumpProto;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ProtoUtils;

import org.junit.Before;
import org.junit.Test;

@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public final class CarWifiManagerTest extends AbstractCarTestCase {

    private static final String CMD_DUMPSYS_WIFI_PROTO =
            "dumpsys car_service --services CarWifiService --proto";

    private CarWifiManager mCarWifiManager;

    @Before
    public void setUp() throws Exception {
        mCarWifiManager = (CarWifiManager) getCar()
                .getCarManager(Car.CAR_WIFI_SERVICE);
    }

    @Test
    @ApiTest(apis = {"android.car.wifi.CarWifiManager#canControlPersistTetheringSettings"})
    @EnsureHasPermission(Car.PERMISSION_READ_PERSIST_TETHERING_SETTINGS)
    @RequiresFlagsEnabled({Flags.FLAG_PERSIST_AP_SETTINGS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    public void testCanControlPersistTetheringSettings_withCapability_returnsTrue()
            throws Exception {
        assumeTrue("Skipping test: tethering capability disabled",
                isPersistTetheringCapabilityEnabled());

        expectWithMessage("Can control persist tethering settings").that(
                mCarWifiManager.canControlPersistTetheringSettings()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.car.wifi.CarWifiManager#canControlPersistTetheringSettings"})
    @EnsureHasPermission(Car.PERMISSION_READ_PERSIST_TETHERING_SETTINGS)
    @RequiresFlagsEnabled({Flags.FLAG_PERSIST_AP_SETTINGS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    public void testCanControlPersistTetheringSettings_noCapability_returnsFalse()
            throws Exception {
        assumeFalse("Skipping test: tethering capability enabled",
                isPersistTetheringCapabilityEnabled());

        expectWithMessage("Can control persist tethering settings").that(
                mCarWifiManager.canControlPersistTetheringSettings()).isFalse();
    }

    private boolean isPersistTetheringCapabilityEnabled() throws Exception {
        CarWifiDumpProto dump = ProtoUtils.getProto(
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                CarWifiDumpProto.class, CMD_DUMPSYS_WIFI_PROTO);

        return dump.getPersistTetheringCapabilitiesEnabled();
    }
}
