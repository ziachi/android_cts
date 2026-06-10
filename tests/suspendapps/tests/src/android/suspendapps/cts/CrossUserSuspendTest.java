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

package android.suspendapps.cts;

import static android.content.Intent.ACTION_PACKAGE_UNSUSPENDED_MANUALLY;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;
import static com.android.bedstead.nene.types.OptionalBoolean.TRUE;
import static com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.bedstead.permissions.CommonPermissions.SUSPEND_APPS;
import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApps;
import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.flags.Flags;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.LargeTest;

import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.BlockingBroadcastReceiver;
import com.android.bedstead.permissions.PermissionContext;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
@LargeTest
@RequiresFlagsEnabled(Flags.FLAG_CROSS_USER_SUSPENSION_ENABLED_RO)
public class CrossUserSuspendTest {
    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final TestRule mCheckFlagsRule = RuleChain
            .outerRule(DeviceFlagsValueProvider.createCheckFlagsRule())
            .around(sDeviceState);

    private static final Context sContext = TestApis.context().instrumentedContext();

    private static final TestApp sTestApp = testApps(sDeviceState).query()
            .whereActivities()
            .contains(activity().where().exported().isTrue())
            .get();

    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @RequireRunOnInitialUser
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, SUSPEND_APPS})
    @Test
    public void suspendProfileActivityFromPrimary_verifyUnsuspendedBroadcast() throws Exception {
        UserReference workProfile = workProfile(sDeviceState);
        try (TestAppInstance instance = sTestApp.install(workProfile);
                BlockingBroadcastReceiver broadcastReceiver =
                        sDeviceState.registerBroadcastReceiver(
                                ACTION_PACKAGE_UNSUSPENDED_MANUALLY)) {
            PackageManager profilePackageManager =
                    TestApis.context().instrumentedContextAsUser(workProfile).getPackageManager();

            profilePackageManager.setPackagesSuspended(
                    new String[]{sTestApp.packageName()}, true /* suspended */,
                    null /* appExtras */, null /* launcherExtras */,
                    DialogTests.makeDialogInfoWithUnsuspendButton());

            assertThat(sContext.getPackageManager().isPackageSuspendedForUser(
                    sTestApp.packageName(), workProfile.id())).isTrue();

            TestAppActivityReference activityReference =
                    instance.activities().query().whereActivity().exported().isTrue().get();
            Intent intent = new Intent()
                    .addFlags(FLAG_ACTIVITY_NEW_TASK)
                    .setComponent(activityReference.component().componentName());
            sContext.startActivityAsUser(intent, new Bundle(), workProfile.userHandle());

            DialogTests.verifyDialogAndPressUnsuspend(sContext, TestApis.ui().device());

            broadcastReceiver.awaitForBroadcastOrFail();
        }
    }

    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @RequireRunOnInitialUser
    @EnsureHasPermission(SUSPEND_APPS)
    @EnsureDoesNotHavePermission(INTERACT_ACROSS_USERS_FULL)
    @Test
    public void suspendProfileActivityFromPrimary_withoutCrossUserFull_throws() throws Exception {
        UserReference workProfile = workProfile(sDeviceState);
        try (TestAppInstance ignored = sTestApp.install(workProfile)) {
            PackageManager profilePackageManager;
            try (PermissionContext ignoredToo = TestApis.permissions()
                    .withPermission(INTERACT_ACROSS_USERS_FULL)) {
                profilePackageManager = TestApis.context()
                        .instrumentedContextAsUser(workProfile).getPackageManager();
            }

            assertThrows(SecurityException.class,
                    () -> profilePackageManager.setPackagesSuspended(
                            new String[]{sTestApp.packageName()}, true /* suspended */,
                            null /* appExtras */, null /* launcherExtras */,
                            DialogTests.makeDialogInfoWithUnsuspendButton()));
        }
    }
}
