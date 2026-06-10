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

import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.car.feature.Flags.FLAG_MULTI_DISPLAY_BRIGHTNESS_CONTROL;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;

import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Display;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DisplayBrightnessTest extends AbstractCarTestCase {

    private CarOccupantZoneManager mCarOccupantZoneManager;
    private static final float TEST_BRIGHTNESS = 0.345f;
    private static final String TAG = DisplayBrightnessTest.class.getSimpleName();

    @Before
    public void setUp() {
        mCarOccupantZoneManager = getCar().getCarManager(CarOccupantZoneManager.class);
        assertThat(mCarOccupantZoneManager).isNotNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_MULTI_DISPLAY_BRIGHTNESS_CONTROL)
    public void testSetDisplayBrightness_fullSecondaryUserMustNotAffectOtherUser()
            throws Exception {
        Map<Integer, OccupantZoneInfo> occupantZoneInfoByDisplayId = new ArrayMap<>();
        var driverDisplay = getDriverZoneMainDisplay(occupantZoneInfoByDisplayId);
        assertWithMessage("Main display for driver zone").that(driverDisplay).isNotNull();
        assumeFalse(
                "Driver display is virtual display, skip test because it might share"
                        + " the same physical display with other zones",
                driverDisplay.getType() == Display.TYPE_VIRTUAL);
        int driverDisplayId = driverDisplay.getDisplayId();

        var passengerDisplays = getPassengerZoneMainDisplays(occupantZoneInfoByDisplayId);

        assumeFalse("No passenger displays available", passengerDisplays.size() == 0);

        ArrayMap<Display, Float> brightnessForEachDisplay = new ArrayMap<>();
        brightnessForEachDisplay.put(driverDisplay, getDisplayBrightness(driverDisplayId));

        for (Display passengerDisplay : passengerDisplays) {
            int displayId = passengerDisplay.getDisplayId();
            brightnessForEachDisplay.put(passengerDisplay, getDisplayBrightness(displayId));
        }

        // Try changing display brightness for each zone. Other zone's display brightness must
        // not be affected.
        for (int i = 0; i < brightnessForEachDisplay.size(); i++) {
            Display display = brightnessForEachDisplay.keyAt(i);
            if (display.getType() == Display.TYPE_VIRTUAL) {
                continue;
            }
            Float originalBrightness = brightnessForEachDisplay.valueAt(i);
            if (originalBrightness.isNaN()) {
                continue;
            }
            int displayId = display.getDisplayId();
            if (displayId == driverDisplayId) {
                continue;
            }
            setDisplayBrightness(displayId, TEST_BRIGHTNESS);
            try {
                for (int j = 0; j < brightnessForEachDisplay.size(); j++) {
                    Display otherDisplay = brightnessForEachDisplay.keyAt(j);
                    int otherDisplayId = otherDisplay.getDisplayId();
                    if (otherDisplayId == displayId) {
                        continue;
                    }
                    assertWithMessage(
                                    "Display brightness change for display Id: "
                                            + displayId
                                            + " for occupant zone: "
                                            + occupantZoneInfoByDisplayId.get(displayId)
                                            + " must not affect display brightness for display Id: "
                                            + otherDisplayId
                                            + " for occupant zone: "
                                            + occupantZoneInfoByDisplayId.get(otherDisplayId))
                            .that(getDisplayBrightness(otherDisplayId))
                            .isEqualTo(brightnessForEachDisplay.valueAt(j));
                }
            } finally {
                // Restore display brightness to the original value.
                setDisplayBrightness(displayId, originalBrightness);
            }
        }
    }

    private Float getDisplayBrightness(int displayId) {
        String[] cmdResult =
                runShellCommand("cmd car_service get-display-brightness " + displayId).split(": ");
        if (cmdResult.length != 2) {
            Log.w(TAG, "Failed to get display brightness for display Id: " + displayId);
            return Float.NaN;
        }
        try {
            Float brightness = Float.valueOf(cmdResult[1]);
            return brightness;
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    private void setDisplayBrightness(int displayId, Float brightness) throws InterruptedException {
        runShellCommand("cmd car_service set-display-brightness " + displayId + " " + brightness);
        // Sleep for 1s to make sure the brightness change takes effect.
        Thread.sleep(1000);
    }

    private Display getDriverZoneMainDisplay(
            Map<Integer, OccupantZoneInfo> outOccupantZoneInfoByDisplayId) {
        var zones =
                mCarOccupantZoneManager.getAllOccupantZones().stream()
                        .filter(o -> o.occupantType == OCCUPANT_TYPE_DRIVER)
                        .toList();
        assertWithMessage("Expected occupant zones to contain the driver occupant zone")
                .that(zones)
                .hasSize(1);
        var driverZone = zones.get(0);
        var display =
                mCarOccupantZoneManager.getDisplayForOccupant(
                        driverZone, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        outOccupantZoneInfoByDisplayId.put(display.getDisplayId(), driverZone);
        return display;
    }

    private List<Display> getPassengerZoneMainDisplays(
            Map<Integer, OccupantZoneInfo> outOccupantZoneInfoByDisplayId) {
        var zones =
                mCarOccupantZoneManager.getAllOccupantZones().stream()
                        .filter(
                                o ->
                                        o.occupantType != OCCUPANT_TYPE_DRIVER
                                                && mCarOccupantZoneManager.getUserForOccupant(o)
                                                        != UserHandle.USER_NULL)
                        .toList();
        List<Display> displays = new ArrayList<>();
        for (var passengerZone : zones) {
            var display =
                    mCarOccupantZoneManager.getDisplayForOccupant(
                            passengerZone, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
            if (display != null) {
                displays.add(display);
            }
            outOccupantZoneInfoByDisplayId.put(display.getDisplayId(), passengerZone);
        }
        return displays;
    }
}
