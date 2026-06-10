/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.car.Car.CAR_PROPERTY_SIMULATION_SERVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.car.Car;
import android.car.cts.utils.BuildUtils;
import android.car.feature.Flags;
import android.car.hardware.property.CarPropertySimulationManager;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;

@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public final class CarPropertySimulationManagerTest extends AbstractCarTestCase {
    private static final String TAG = CarPropertySimulationManagerTest.class.getSimpleName();
    private CarPropertySimulationManager mCarPropertySimulationManager;

    @Before
    public void setUp() throws Exception {
        mCarPropertySimulationManager =
                (CarPropertySimulationManager)
                        getCar().getCarManager(CAR_PROPERTY_SIMULATION_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAR_PROPERTY_SIMULATION)
    @ApiTest(apis = {"android.car.Car#getCarManager"})
    @EnsureHasPermission(Car.PERMISSION_RECORD_VEHICLE_PROPERTIES)
    public void testGetCarSimulationManager_nullForUserBuild() {
        assumeTrue(BuildUtils.isUserBuild());
        assertThat(mCarPropertySimulationManager).isNull();
    }
}
