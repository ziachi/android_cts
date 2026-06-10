/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.server.wm.cts;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
import static android.car.CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER;
import static android.view.Display.INVALID_DISPLAY;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityOptions;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.test.PermissionsCheckerRule;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.Display;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MultiUserMultiDisplayPolicyTest {
    private static final String TAG = MultiUserMultiDisplayPolicyTest.class.getSimpleName();
    private static final long ACTIVITY_WAIT_TIMEOUT_MS = 10_000L;

    @Rule
    public final PermissionsCheckerRule mPermissionsCheckerRule = new PermissionsCheckerRule();

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    private UserManager mUserManager;
    private CarOccupantZoneManager mCarOccupantZoneManager;

    private OccupantZoneInfo mMyZone;

    @Before
    public void setUp() {
        mUserManager = mContext.getSystemService(UserManager.class);
        assumeTrue(mUserManager.isVisibleBackgroundUsersSupported());

        Car car = Car.createCar(mContext);
        mCarOccupantZoneManager = car.getCarManager(CarOccupantZoneManager.class);
        mMyZone = mCarOccupantZoneManager.getMyOccupantZone();
    }

    @Test
    public void testDriverCanLaunchActivityInDriverDisplay() throws Exception {
        assumeTrue("Must be the driver", mMyZone.occupantType == OCCUPANT_TYPE_DRIVER);

        var display = mCarOccupantZoneManager.getDisplayForOccupant(mMyZone, DISPLAY_TYPE_MAIN);
        assertWithMessage("Driver display").that(display).isNotNull();

        int displayId = display.getDisplayId();
        var intent = new Intent(mContext, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<TestActivity> activityScenario = ActivityScenario.launch(intent,
                ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())) {
            var activityReady = new CountDownLatch(1);
            activityScenario.onActivity(a -> activityReady.countDown());
            assertWithMessage("Waited for TestActivity to start on the driver display.").that(
                    activityReady.await(ACTIVITY_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        }
    }

    @Test
    public void testPassengerCanLaunchActivityInPassengerDisplay() throws Exception {
        assumeTrue("Must be a passenger", mMyZone.occupantType != OCCUPANT_TYPE_DRIVER);

        var display = mCarOccupantZoneManager.getDisplayForOccupant(mMyZone, DISPLAY_TYPE_MAIN);
        assertWithMessage("Passenger display").that(display).isNotNull();

        int displayId = display.getDisplayId();
        var intent = new Intent(mContext, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<TestActivity> activityScenario = ActivityScenario.launch(intent,
                ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())) {
            var activityReady = new CountDownLatch(1);
            activityScenario.onActivity(a -> activityReady.countDown());
            assertWithMessage("Waited for TestActivity to start on the passenger display "
                    + displayId + " for user " + UserHandle.myUserId() + ".").that(
                    activityReady.await(ACTIVITY_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        }
    }

    @Test
    public void testDriverCanNotLaunchActivityInPassengerDisplay() {
        assumeTrue("Must be the driver", mMyZone.occupantType == OCCUPANT_TYPE_DRIVER);
        assumeTrue("No passenger zone", mCarOccupantZoneManager.hasPassengerZones());

        var passengerDisplays = getPassengerDisplays();
        assertWithMessage("Need a passenger display").that(passengerDisplays).isNotEmpty();

        // Just pick the first one.
        int passengerDisplayId = passengerDisplays.get(0).getDisplayId();
        var intent = new Intent(mContext, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertThrows(SecurityException.class, () -> ActivityScenario.launch(intent,
                ActivityOptions.makeBasic().setLaunchDisplayId(passengerDisplayId).toBundle()));
    }

    @Test
    @EnsureHasPermission(Car.ACCESS_PRIVATE_DISPLAY_ID)
    public void testPassengerCanNotLaunchActivityInDriverDisplay() {
        assumeTrue("Must be a passenger", mMyZone.occupantType != OCCUPANT_TYPE_DRIVER);
        assumeTrue("No driver zone", mCarOccupantZoneManager.hasDriverZone());

        int driverDisplayId = mCarOccupantZoneManager.getDisplayIdForDriver(DISPLAY_TYPE_MAIN);
        assertWithMessage("Driver display").that(driverDisplayId).isNotEqualTo(INVALID_DISPLAY);

        var intent = new Intent(mContext, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertThrows(SecurityException.class, () -> ActivityScenario.launch(intent,
                ActivityOptions.makeBasic().setLaunchDisplayId(driverDisplayId).toBundle()));
    }

    @Test
    public void testPassengerCanNotLaunchActivityInOtherPassengerDisplay() {
        assumeTrue("Must be a passenger", mMyZone.occupantType != OCCUPANT_TYPE_DRIVER);

        var displays = getPassengerDisplays();
        assumeTrue("Need at least two passenger displays", displays.size() >= 2);

        var myDisplay = mCarOccupantZoneManager.getDisplayForOccupant(mMyZone, DISPLAY_TYPE_MAIN);
        assertWithMessage("Passenger display").that(myDisplay).isNotNull();

        int myDisplayId = myDisplay.getDisplayId();
        var otherPassengerDisplay = displays.stream().filter(d -> d.getDisplayId() != myDisplayId)
                .findFirst().orElse(null);
        assertWithMessage("Other passenger display").that(otherPassengerDisplay).isNotNull();

        int otherPassengerDisplayId = otherPassengerDisplay.getDisplayId();
        var intent = new Intent(mContext, TestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        assertThrows(SecurityException.class, () -> ActivityScenario.launch(intent,
                ActivityOptions.makeBasic().setLaunchDisplayId(otherPassengerDisplayId)
                        .toBundle()));
    }

    private List<Display> getPassengerDisplays() {
        return mCarOccupantZoneManager.getAllOccupantZones().stream().filter(
                o -> o.occupantType != OCCUPANT_TYPE_DRIVER).map(
                        o -> mCarOccupantZoneManager.getDisplayForOccupant(o, DISPLAY_TYPE_MAIN))
                                .filter(Objects::nonNull).toList();
    }

    public static class TestActivity extends Activity {

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            TextView tv = new TextView(this);
            tv.setText(TAG);

            setContentView(tv);
        }
    }
}
