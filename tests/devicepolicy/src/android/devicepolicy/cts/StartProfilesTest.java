/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.devicepolicy.cts;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;
import static com.android.bedstead.multiuser.MultiUserDeviceStateExtensionsKt.secondaryUser;
import static com.android.bedstead.multiuser.MultiUserDeviceStateExtensionsKt.tvProfile;
import static com.android.bedstead.permissions.CommonPermissions.CREATE_USERS;
import static com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.multiuser.annotations.EnsureHasTvProfile;
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.SlowApiTest;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.BlockingBroadcastReceiver;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.Function;

@RunWith(BedsteadJUnit4.class)
public final class StartProfilesTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final UserManager sUserManager = sContext.getSystemService(UserManager.class);
    private static final ActivityManager sActivityManager =
            sContext.getSystemService(ActivityManager.class);

    private static final int START_PROFILE_BROADCAST_TIMEOUT = 480_000; // 8 minutes

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private Function<Intent, Boolean> userIsEqual(UserReference user) {
        return (intent) -> user.userHandle().equals(intent.getParcelableExtra(Intent.EXTRA_USER));
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, CREATE_USERS})
    public void startProfile_returnsTrue() {
        workProfile(sDeviceState).stop();

        assertThat(sActivityManager.startProfile(workProfile(sDeviceState).userHandle())).isTrue();
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, CREATE_USERS})
    @SlowApiTest("Start profile broadcasts can take a long time")
    public void startProfile_broadcastIsReceived_profileIsStarted() {
        try (BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                Intent.ACTION_PROFILE_INACCESSIBLE, userIsEqual(workProfile(sDeviceState)))) {
            workProfile(sDeviceState).stop();
        }

        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                Intent.ACTION_PROFILE_ACCESSIBLE,
                userIsEqual(workProfile(sDeviceState)));
        sActivityManager.startProfile(workProfile(sDeviceState).userHandle());

        broadcastReceiver.awaitForBroadcastOrFail(START_PROFILE_BROADCAST_TIMEOUT);

        assertThat(sUserManager.isUserRunning(workProfile(sDeviceState).userHandle())).isTrue();
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @Postsubmit(reason = "b/181207615 flaky")
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, CREATE_USERS})
    public void stopProfile_returnsTrue() {
        assertThat(sActivityManager.stopProfile(workProfile(sDeviceState).userHandle())).isTrue();
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @Postsubmit(reason = "b/181207615 flaky")
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, CREATE_USERS})
    public void stopProfile_profileIsStopped() {
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                Intent.ACTION_PROFILE_INACCESSIBLE, userIsEqual(workProfile(sDeviceState)));

        sActivityManager.stopProfile(workProfile(sDeviceState).userHandle());
        broadcastReceiver.awaitForBroadcastOrFail();

        assertThat(
                sUserManager.isUserRunning(workProfile(sDeviceState).userHandle())).isFalse();
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @Postsubmit(reason = "b/181207615 flaky")
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, CREATE_USERS})
    public void startUser_immediatelyAfterStopped_profileIsStarted() {
        try (BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                Intent.ACTION_PROFILE_INACCESSIBLE, userIsEqual(workProfile(sDeviceState)))) {
            sActivityManager.stopProfile(workProfile(sDeviceState).userHandle());
        }

        // start profile as soon as ACTION_PROFILE_INACCESSIBLE is received
        // verify that ACTION_PROFILE_ACCESSIBLE is received if profile is re-started
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                Intent.ACTION_PROFILE_ACCESSIBLE, userIsEqual(workProfile(sDeviceState)));
        sActivityManager.startProfile(workProfile(sDeviceState).userHandle());
        Intent broadcast = broadcastReceiver.awaitForBroadcast();

        assertWithMessage("Expected to receive ACTION_PROFILE_ACCESSIBLE broadcast").that(
                broadcast).isNotNull();
        assertThat(
                sUserManager.isUserRunning(workProfile(sDeviceState).userHandle())).isTrue();
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @Postsubmit(reason = "b/181207615 flaky")
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, CREATE_USERS})
    public void startUser_userIsStopping_profileIsStarted() {
        workProfile(sDeviceState).start();

        // stop and restart profile without waiting for ACTION_PROFILE_INACCESSIBLE broadcast
        sActivityManager.stopProfile(workProfile(sDeviceState).userHandle());
        sActivityManager.startProfile(workProfile(sDeviceState).userHandle());

        Poll.forValue("user running",
                        () -> sUserManager.isUserRunning(workProfile(sDeviceState).userHandle()))
                .toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @EnsureDoesNotHavePermission({INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, CREATE_USERS})
    @Postsubmit(reason = "b/181207615 flaky")
    public void startProfile_withoutPermission_throwsException() {
        assertThrows(SecurityException.class,
                () -> sActivityManager.startProfile(workProfile(sDeviceState).userHandle()));
    }

    @Test
    @RequireFeature(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @EnsureDoesNotHavePermission({INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, CREATE_USERS})
    @Postsubmit(reason = "b/181207615 flaky")
    public void stopProfile_withoutPermission_throwsException() {
        assertThrows(SecurityException.class,
                () -> sActivityManager.stopProfile(workProfile(sDeviceState).userHandle()));
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasSecondaryUser
    @Postsubmit(reason = "b/181207615 flaky")
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, CREATE_USERS})
    public void startProfile_startingFullUser_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> sActivityManager.startProfile(secondaryUser(sDeviceState).userHandle()));
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasSecondaryUser
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, CREATE_USERS})
    @Postsubmit(reason = "b/181207615 flaky")
    public void stopProfile_stoppingFullUser_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> sActivityManager.stopProfile(secondaryUser(sDeviceState).userHandle()));
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasTvProfile
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, CREATE_USERS})
    @Postsubmit(reason = "b/181207615 flaky")
    public void startProfile_tvProfile_profileIsStarted() {
        try (BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                Intent.ACTION_PROFILE_INACCESSIBLE, userIsEqual(tvProfile(sDeviceState)))) {
            tvProfile(sDeviceState).stop();
        }

        try (BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                Intent.ACTION_PROFILE_ACCESSIBLE, userIsEqual(tvProfile(sDeviceState)))) {
            assertThat(
                    sActivityManager.startProfile(tvProfile(sDeviceState).userHandle())).isTrue();
        }

        assertThat(sUserManager.isUserRunning(tvProfile(sDeviceState).userHandle())).isTrue();
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasTvProfile
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, CREATE_USERS})
    @Postsubmit(reason = "b/181207615 flaky")
    public void stopProfile_tvProfile_profileIsStopped() {
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                Intent.ACTION_PROFILE_INACCESSIBLE, userIsEqual(tvProfile(sDeviceState)));

        assertThat(
                sActivityManager.stopProfile(tvProfile(sDeviceState).userHandle())).isTrue();
        broadcastReceiver.awaitForBroadcast();

        assertThat(sUserManager.isUserRunning(tvProfile(sDeviceState).userHandle())).isFalse();
    }
}