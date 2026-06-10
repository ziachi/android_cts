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

package android.car.app.cts;

import static android.Manifest.permission.CREATE_USERS;
import static android.car.CarOccupantZoneManager.INVALID_USER_ID;
import static android.car.cts.utils.ShellPermissionUtils.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.SyncResultCallback;
import android.car.test.PermissionsCheckerRule;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.car.user.CarUserManager;
import android.car.user.UserCreationRequest;
import android.car.user.UserCreationResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.Display;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SmallTest
public class MultiUserMultiDisplayWindowSecurityTest {
    private static final long TIMEOUT_MS = 5_000L;
    private static final long USER_CREATION_TIMEOUT_MS = 20_000L;

    private static final String EXTRA_DISPLAY_ID_TO_SHOW_OVERLAY =
            "android.car.app.cts.DISPLAY_ID_TO_SHOW_OVERLAY";
    private static final ComponentName COMPONENT_SDK22_OVERLAY_WINDOW_TEST_ACTIVITY =
            ComponentName.createRelative("android.car.app.cts.overlaywindowappsdk22",
                    ".OverlayWindowTestActivitySdk22");
    private static final ComponentName COMPONENT_DEPRECATED_SDK_TEST_ACTIVITY =
            ComponentName.createRelative("android.car.app.cts.deprecatedsdk", ".MainActivity");

    private static final String PREFIX_NEW_USER_NAME = "newTestUser";

    @Rule
    public final PermissionsCheckerRule mPermissionsCheckerRule = new PermissionsCheckerRule();

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final ActivityManager mActivityManager = mContext.getSystemService(
            ActivityManager.class);
    private final UserManager mUserManager = mContext.getSystemService(UserManager.class);
    private final List<Integer> mUsersToRemove = new ArrayList<>();
    private CarUserManager mCarUserManager;
    private CarOccupantZoneManager mCarOccupantZoneManager;
    private UiDevice mDevice;
    private List<Integer> mUnassignedDisplays;

    @Before
    public void setUp() throws Exception {
        assumeTrue("Skipping test: Device doesn't support visible background users",
                mUserManager.isVisibleBackgroundUsersSupported());

        Car car = Car.createCar(mContext);
        mCarUserManager = car.getCarManager(CarUserManager.class);
        mCarOccupantZoneManager = car.getCarManager(CarOccupantZoneManager.class);
        mUnassignedDisplays = getUnassignedDisplaysForUser(UserHandle.myUserId());
        assumeTrue("There should be at least one unassigned display.",
                mUnassignedDisplays.size() > 0);

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mDevice.wakeUp();
    }

    @After
    public void tearDown() throws Exception {
        stopTestPackage(COMPONENT_DEPRECATED_SDK_TEST_ACTIVITY);
        stopTestPackage(COMPONENT_SDK22_OVERLAY_WINDOW_TEST_ACTIVITY);
        cleanUpTestUsers();
    }

    @Test
    public void testDeprecatedSdkVersionDialog() {
        mContext.startActivity(createIntent(COMPONENT_DEPRECATED_SDK_TEST_ACTIVITY));

        String windowName = COMPONENT_DEPRECATED_SDK_TEST_ACTIVITY.getPackageName();
        BySelector appWarningDialogSelector = By.pkg("android").text(windowName);
        assumeTrue(mDevice.wait(Until.hasObject(appWarningDialogSelector), TIMEOUT_MS));

        UiObject2 appWarningUiObject = mDevice.findObject(appWarningDialogSelector);
        for (int unassignedDisplayId : mUnassignedDisplays) {
            assertWithMessage("AppWarning dialog must not be displayed on the unassigned display.")
                    .that(appWarningUiObject.getDisplayId() == unassignedDisplayId)
                    .isFalse();
        }

        // Go back to dismiss the app warning dialog.
        mDevice.pressBack();
    }

    @Test
    @EnsureHasPermission({CREATE_USERS})
    public void testShowOverlayWindowOnDisplayAssignedToDifferentUser() throws Exception {
        // UserPicker has the flag to hide non-system overlay windows when it is visible.
        // (@see WindowManager.LayoutParams#SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS)
        // To ensure the test is performed normally, assign each display to a user,
        // leading to the dismissal of UserPicker.
        ensureAllDisplaysAreAssignedToUser();
        // Just pick the first one.
        int displayAssignedToDifferentUser = mUnassignedDisplays.get(0);
        Intent intent = createIntent(COMPONENT_SDK22_OVERLAY_WINDOW_TEST_ACTIVITY);
        intent.putExtra(EXTRA_DISPLAY_ID_TO_SHOW_OVERLAY, displayAssignedToDifferentUser);

        mContext.startActivity(intent);
        String packageName = COMPONENT_SDK22_OVERLAY_WINDOW_TEST_ACTIVITY.getPackageName();
        // Wait for the app to appear
        mDevice.wait(Until.hasObject(By.pkg(packageName).depth(0)), TIMEOUT_MS);

        BySelector overlayWindowSelector = By.text(packageName)
                .displayId(displayAssignedToDifferentUser).depth(0);
        assertWithMessage("Should not display the overlay window on a display assigned to a user"
                + " other than user %s", UserHandle.myUserId())
                .that(mDevice.hasObject(overlayWindowSelector))
                .isFalse();
    }

    private List<Integer> getUnassignedDisplaysForUser(int userId) {
        List<CarOccupantZoneManager.OccupantZoneInfo> zonelist =
                mCarOccupantZoneManager.getAllOccupantZones();
        List<Integer> displayList = new ArrayList<>();
        for (CarOccupantZoneManager.OccupantZoneInfo zone : zonelist) {
            int userForOccupantZone = mCarOccupantZoneManager.getUserForOccupant(zone);
            if (userForOccupantZone == userId) {
                // Skip the assigned zone.
                continue;
            }
            Display display = mCarOccupantZoneManager.getDisplayForOccupant(zone,
                    CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
            if (display != null) {
                displayList.add(display.getDisplayId());
            }
        }
        return displayList;
    }

    private void ensureAllDisplaysAreAssignedToUser() throws Exception {
        String passengerLauncherPkg = getPassengerLauncherPackageName();
        for (int displayId : mUnassignedDisplays) {
            int userId = mCarOccupantZoneManager.getUserForDisplayId(displayId);
            if (userId != INVALID_USER_ID) {
                // A user is already assigned to the display
                continue;
            }
            userId = createNewUser(displayId);
            mUsersToRemove.add(userId);
            // Skip the setup wizard and make sure the passenger home shows up.
            setUserSetupComplete(userId);
            startUserOnDisplay(userId, displayId);
            waitForPassengerLauncherOnDisplay(passengerLauncherPkg, displayId);
        }
    }

    private int createNewUser(int displayId) throws Exception {
        String userName = new StringBuilder(PREFIX_NEW_USER_NAME).append(displayId).toString();
        SyncResultCallback<UserCreationResult> userCreationResultCallback =
                new SyncResultCallback<>();

        mCarUserManager.createUser(new UserCreationRequest.Builder().setName(userName).build(),
                Runnable::run, userCreationResultCallback);
        UserCreationResult result = userCreationResultCallback.get(USER_CREATION_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        UserHandle user = result.getUser();
        assertThat(user).isNotNull();
        return user.getIdentifier();
    }

    private static Intent createIntent(ComponentName activity) {
        Intent intent = new Intent();
        intent.setComponent(activity);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private static void setUserSetupComplete(int userId) {
        ShellUtils.runShellCommand("settings put --user %d secure user_setup_complete 1", userId);
        assertWithMessage("User setup complete").that(
                ShellUtils.runShellCommand("settings get --user %d secure user_setup_complete",
                        userId)).isEqualTo("1");
    }

    private static void deleteUserSetupComplete(int userId) {
        assertWithMessage("User setup complete setting deleted").that(
                ShellUtils.runShellCommand("settings delete --user %d secure user_setup_complete",
                        userId)).contains("Deleted 1 rows");
    }

    private static void startUserOnDisplay(int userId, int displayId) {
        assertWithMessage("User started").that(
                ShellUtils.runShellCommand("am start-user -w --display %d %d", displayId, userId))
                .contains("Success: user started on display " + displayId);
    }

    private void waitForPassengerLauncherOnDisplay(String passengerLauncherPkg, int displayId) {
        mDevice.wait(Until.hasObject(By.pkg(passengerLauncherPkg).displayId(displayId).depth(0)),
                TIMEOUT_MS);
    }

    private void cleanUpTestUsers() {
        if (mUsersToRemove.isEmpty()) {
            return;
        }
        for (int userId : mUsersToRemove) {
            deleteUserSetupComplete(userId);
            stopUser(userId);
            removeUser(userId);
        }
        mUsersToRemove.clear();
    }

    private static void stopUser(int userId) {
        ShellUtils.runShellCommand("am stop-user -w -f %d", userId);
    }

    private static void removeUser(int userId) {
        assertWithMessage("User removed").that(
                ShellUtils.runShellCommand("pm remove-user --wait %d",
                        userId)).contains("Success: removed user");
    }

    private void stopTestPackage(ComponentName activityName) {
        runWithShellPermissionIdentity(() -> mActivityManager.forceStopPackage(
                activityName.getPackageName()));
    }

    private String getPassengerLauncherPackageName() {
        Intent queryIntent = new Intent(Intent.ACTION_MAIN);
        queryIntent.addCategory(Intent.CATEGORY_SECONDARY_HOME);
        List<ResolveInfo> resolveInfo = mContext.getPackageManager()
                .queryIntentActivities(queryIntent, /* flags= */ 0);
        assertThat(resolveInfo).isNotNull();
        assertThat(resolveInfo).isNotEmpty();
        return resolveInfo.get(0).activityInfo.packageName;
    }
}
