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

import static android.app.admin.flags.Flags.FLAG_ALLOW_QUERYING_PROFILE_TYPE;
import static android.content.pm.CrossProfileApps.ACTION_CAN_INTERACT_ACROSS_PROFILES_CHANGED;
import static android.provider.Settings.ACTION_MANAGE_CROSS_PROFILE_ACCESS;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.profileOwner;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;
import static com.android.bedstead.harrier.UserType.ADDITIONAL_USER;
import static com.android.bedstead.harrier.UserType.CLONE_PROFILE;
import static com.android.bedstead.harrier.UserType.INITIAL_USER;
import static com.android.bedstead.harrier.UserType.PRIVATE_PROFILE;
import static com.android.bedstead.harrier.UserType.WORK_PROFILE;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.multiuser.MultiUserDeviceStateExtensionsKt.otherUser;
import static com.android.bedstead.nene.types.OptionalBoolean.TRUE;
import static com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_PROFILES;
import static com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.bedstead.permissions.CommonPermissions.START_CROSS_PROFILE_ACTIVITIES;
import static com.android.bedstead.testapps.TestAppsDeviceStateExtensionsKt.testApps;
import static com.android.eventlib.truth.EventLogsSubject.assertThat;
import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.ActivityOptions;
import android.app.AppOpsManager;
import android.app.admin.RemoteDevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.CrossProfileApps;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.stats.devicepolicy.EventId;

import androidx.test.core.app.ApplicationProvider;

import com.android.activitycontext.ActivityContext;
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.CrossUserTest;
import com.android.bedstead.harrier.annotations.PermissionTest;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.UserPair;
import com.android.bedstead.harrier.annotations.UserTest;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.multiuser.annotations.EnsureHasNoProfile;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.appops.AppOpsMode;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.packages.ProcessReference;
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;
import com.android.eventlib.EventLogs;
import com.android.eventlib.events.activities.ActivityCreatedEvent;
import com.android.eventlib.events.activities.ActivityEvents;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class CrossProfileAppsTest {

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final CrossProfileApps sCrossProfileApps =
            sContext.getSystemService(CrossProfileApps.class);

    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final TestRule mCheckFlagsRule = RuleChain
            .outerRule(DeviceFlagsValueProvider.createCheckFlagsRule())
            .around(sDeviceState);

    private static final TestApp sCrossProfileTestApp = testApps(sDeviceState).query()
            .whereCrossProfile().isTrue()
            // TODO: Add query for a receiver for ACTION_CAN_INTERACT_ACROSS_PROFILES_CHANGED
            .wherePermissions().contains("android.permission.INTERACT_ACROSS_PROFILES").get();
    private static final TestApp sNonCrossProfileTestApp = testApps(sDeviceState).query()
            .wherePermissions().doesNotContain("android.permission.INTERACT_ACROSS_PROFILES").get();
    private static final TestApp sTestAppWithMainActivity = testApps(sDeviceState).query()
            .whereActivities().contains(
                    activity().where().intentFilters().contains(
                            intentFilter().where().actions().contains(Intent.ACTION_MAIN))
            ).get();
    private static final TestApp sTestAppWithActivity = sTestAppWithMainActivity;

    // TODO(b/191637162): When we have permissions in test apps we won't need to use the
    //  instrumented app for this
    private static final ComponentName MAIN_ACTIVITY =
            new ComponentName(sContext, "android.devicepolicy.cts.MainActivity");
    private static final ComponentName NOT_EXPORTED_ACTIVITY =
            new ComponentName(sContext, "android.devicepolicy.cts.NotExportedMainActivity");
    private static final ComponentName NOT_MAIN_ACTIVITY =
            new ComponentName(sContext, "android.devicepolicy.cts.NotMainActivity");

    @Before
    @After
    public void cleanupOtherUsers() {
        // As these tests start this package on other users, we should kill all processes on other
        // users for this package

        Package pkg = TestApis.packages().instrumented();
        pkg.runningProcesses().stream()
                .filter(p -> !p.user().equals(TestApis.users().instrumented()))
                .forEach(ProcessReference::kill);
    }

    @CrossUserTest({
            @UserPair(from = INITIAL_USER, to = INITIAL_USER),
            @UserPair(from = INITIAL_USER, to = ADDITIONAL_USER),
            @UserPair(from = WORK_PROFILE, to = ADDITIONAL_USER),
            @UserPair(from = ADDITIONAL_USER, to = WORK_PROFILE)
    })
    @Postsubmit(reason = "new test")
    public void getTargetUserProfiles_doesNotContainOtherUser() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).doesNotContain(otherUser(sDeviceState).userHandle());
    }

    @CrossUserTest({
            @UserPair(from = WORK_PROFILE, to = INITIAL_USER),
            @UserPair(from = INITIAL_USER, to = WORK_PROFILE)
    })    @Postsubmit(reason = "new test")
    public void getTargetUserProfiles_containsOtherUser() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).contains(otherUser(sDeviceState).userHandle());
    }

    @CrossUserTest({
            @UserPair(from = WORK_PROFILE, to = INITIAL_USER),
            @UserPair(from = INITIAL_USER, to = WORK_PROFILE)
    })
    @Postsubmit(reason = "new test")
    public void getTargetUserProfiles_appNotInstalledInOtherUser_doesNotContainOtherUser() {
        TestApis.packages().instrumented().uninstall(otherUser(sDeviceState));

        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).doesNotContain(otherUser(sDeviceState).userHandle());
    }

    @Postsubmit(reason = "new test")
    @UserTest({INITIAL_USER, WORK_PROFILE})
    public void getTargetUserProfiles_logged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sCrossProfileApps.getTargetUserProfiles();

            assertThat(metrics.query()
                    .whereType().isEqualTo(
                            EventId.CROSS_PROFILE_APPS_GET_TARGET_USER_PROFILES_VALUE)
                    .whereStrings().contains(sContext.getPackageName())
            ).wasLogged();
        }
    }

    @Test
    @CrossUserTest({
            @UserPair(from = INITIAL_USER, to = INITIAL_USER),
            @UserPair(from = INITIAL_USER, to = ADDITIONAL_USER),
            @UserPair(from = WORK_PROFILE, to = ADDITIONAL_USER),
            @UserPair(from = ADDITIONAL_USER, to = WORK_PROFILE),
            @UserPair(from = WORK_PROFILE, to = WORK_PROFILE),
    })
    @RequiresFlagsEnabled(FLAG_ALLOW_QUERYING_PROFILE_TYPE)
    @ApiTest(apis = {"android.content.pm.CrossProfileApps#isProfile(UserHandle)"})
    @Postsubmit(reason = "new test")
    public void isProfile_targetIsInvalid_throwsSecurityException() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.isProfile(otherUser(sDeviceState).userHandle());
        });
    }

    @Test
    @CrossUserTest({
            @UserPair(from = INITIAL_USER, to = WORK_PROFILE),
            @UserPair(from = CLONE_PROFILE, to = WORK_PROFILE),
            @UserPair(from = WORK_PROFILE, to = CLONE_PROFILE),
            @UserPair(from = INITIAL_USER, to = CLONE_PROFILE),
    })
    @RequiresFlagsEnabled(FLAG_ALLOW_QUERYING_PROFILE_TYPE)
    @ApiTest(apis = {"android.content.pm.CrossProfileApps#isProfile(UserHandle)"})
    @Postsubmit(reason = "new test")
    public void isProfile_profile_returnsTrue() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        assertThat(sCrossProfileApps.isProfile(otherUser(sDeviceState).userHandle())).isTrue();
    }

    @Test
    @CrossUserTest({
            @UserPair(from = WORK_PROFILE, to = INITIAL_USER),
            @UserPair(from = CLONE_PROFILE, to = INITIAL_USER),
    })
    @RequiresFlagsEnabled(FLAG_ALLOW_QUERYING_PROFILE_TYPE)
    @ApiTest(apis = {"android.content.pm.CrossProfileApps#isProfile(UserHandle)"})
    @Postsubmit(reason = "new test")
    public void isProfile_notProfile_returnsFalse() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        assertThat(sCrossProfileApps.isProfile(otherUser(sDeviceState).userHandle())).isFalse();
    }

    @Test
    @CrossUserTest({
            @UserPair(from = INITIAL_USER, to = INITIAL_USER),
            @UserPair(from = INITIAL_USER, to = ADDITIONAL_USER),
            @UserPair(from = WORK_PROFILE, to = ADDITIONAL_USER),
            @UserPair(from = ADDITIONAL_USER, to = WORK_PROFILE),
            @UserPair(from = WORK_PROFILE, to = WORK_PROFILE),
    })
    @RequiresFlagsEnabled(FLAG_ALLOW_QUERYING_PROFILE_TYPE)
    @ApiTest(apis = {"android.content.pm.CrossProfileApps#isManagedProfile(UserHandle)"})
    @Postsubmit(reason = "new test")
    public void isManagedProfile_targetIsInvalid_throwsSecurityException() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.isManagedProfile(otherUser(sDeviceState).userHandle());
        });
    }

    @Test
    @CrossUserTest({
            @UserPair(from = INITIAL_USER, to = WORK_PROFILE),
            @UserPair(from = CLONE_PROFILE, to = WORK_PROFILE),
    })
    @RequiresFlagsEnabled(FLAG_ALLOW_QUERYING_PROFILE_TYPE)
    @ApiTest(apis = {"android.content.pm.CrossProfileApps#isManagedProfile(UserHandle)"})
    @Postsubmit(reason = "new test")
    public void isManagedProfile_managedProfile_returnsTrue() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        assertThat(sCrossProfileApps.isManagedProfile(otherUser(sDeviceState).userHandle()))
                .isTrue();
    }

    @Test
    @CrossUserTest({
            @UserPair(from = WORK_PROFILE, to = INITIAL_USER),
            @UserPair(from = WORK_PROFILE, to = CLONE_PROFILE),
            @UserPair(from = INITIAL_USER, to = CLONE_PROFILE),
            @UserPair(from = CLONE_PROFILE, to = INITIAL_USER),
    })
    @RequiresFlagsEnabled(FLAG_ALLOW_QUERYING_PROFILE_TYPE)
    @ApiTest(apis = {"android.content.pm.CrossProfileApps#isManagedProfile(UserHandle)"})
    @Postsubmit(reason = "new test")
    public void isManagedProfile_notManagedProfile_returnsFalse() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        assertThat(sCrossProfileApps.isManagedProfile(otherUser(sDeviceState).userHandle()))
                .isFalse();
    }

    @CrossUserTest({
            @UserPair(from = WORK_PROFILE, to = INITIAL_USER),
            @UserPair(from = INITIAL_USER, to = WORK_PROFILE)
    })
    @Postsubmit(reason = "new test")
    public void startMainActivity_launches() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        sCrossProfileApps.startMainActivity(MAIN_ACTIVITY, otherUser(sDeviceState).userHandle());

        assertThat(
                ActivityEvents.forActivity(MAIN_ACTIVITY, otherUser(sDeviceState))
                        .activityCreated()
        ).eventOccurred();
    }

    @CrossUserTest({
            @UserPair(from = WORK_PROFILE, to = INITIAL_USER),
            @UserPair(from = INITIAL_USER, to = WORK_PROFILE)
    })
    @Postsubmit(reason = "new test")
    public void startMainActivity_logged() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sCrossProfileApps.startMainActivity(MAIN_ACTIVITY,
                    otherUser(sDeviceState).userHandle());

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.CROSS_PROFILE_APPS_START_ACTIVITY_AS_USER_VALUE)
                    .whereStrings().contains(sContext.getPackageName())
            ).wasLogged();
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @Postsubmit(reason = "new test")
    public void startMainActivity_activityNotExported_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.startMainActivity(
                    NOT_EXPORTED_ACTIVITY, workProfile(sDeviceState).userHandle());
        });
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @Postsubmit(reason = "new test")
    public void startMainActivity_activityNotMain_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.startMainActivity(
                    NOT_MAIN_ACTIVITY, workProfile(sDeviceState).userHandle());
        });
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @Postsubmit(reason = "new test")
    public void startMainActivity_activityIncorrectPackage_throwsSecurityException() {
        try (TestAppInstance instance =
                     sTestAppWithMainActivity.install(workProfile(sDeviceState))) {

            TestAppActivityReference activity = instance.activities().query()
                            .whereActivity().exported().isTrue()
                            .whereActivity().intentFilters().contains(
                                    intentFilter().where().actions().contains(
                                            Intent.ACTION_MAIN
                                    )
                            )
                    .get();

            assertThrows(SecurityException.class, () -> {
                sCrossProfileApps.startMainActivity(
                        activity.component().componentName(),
                        workProfile(sDeviceState).userHandle());
            });
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_noComponent_throwsException() throws Exception {
        Intent intent = new Intent();
        intent.setAction("test");

        ActivityContext.runWithContext(activity ->
                assertThrows(NullPointerException.class, () ->
                        sCrossProfileApps.startActivity(
                                intent, workProfile(sDeviceState).userHandle(), activity)));
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_differentPackage_throwsException() throws Exception {
        try (TestAppInstance testAppInstance =
                     sTestAppWithActivity.install(workProfile(sDeviceState))) {
            TestAppActivityReference targetActivity = testAppInstance.activities().any();
            Intent intent = new Intent();
            intent.setComponent(targetActivity.component().componentName());

            ActivityContext.runWithContext(activity ->
                    assertThrows(SecurityException.class, () ->
                            sCrossProfileApps.startActivity(
                                    intent, workProfile(sDeviceState).userHandle(), activity)));
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @Postsubmit(reason = "new test")
    public void startActivity_byComponent_differentPackage_throwsException() throws Exception {
        try (TestAppInstance testAppInstance =
                     sTestAppWithActivity.install(workProfile(sDeviceState))) {
            TestAppActivityReference targetActivity = testAppInstance.activities().any();

            ActivityContext.runWithContext(activity ->
                    assertThrows(SecurityException.class, () ->
                            sCrossProfileApps.startActivity(
                                    targetActivity.component().componentName(),
                                    workProfile(sDeviceState).userHandle())));
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @EnsureDoesNotHavePermission({
            INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS,
            INTERACT_ACROSS_USERS_FULL, START_CROSS_PROFILE_ACTIVITIES})
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_withoutPermissions_throwsException() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        ActivityContext.runWithContext(activity ->
                assertThrows(SecurityException.class, () ->
                        sCrossProfileApps.startActivity(
                                intent, workProfile(sDeviceState).userHandle(), activity)));
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @EnsureDoesNotHavePermission({
            INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS,
            INTERACT_ACROSS_USERS_FULL, START_CROSS_PROFILE_ACTIVITIES})
    @Postsubmit(reason = "new test")
    public void startActivity_byComponent_withoutPermissions_throwsException() throws Exception {
        ActivityContext.runWithContext(activity ->
                assertThrows(SecurityException.class, () ->
                        sCrossProfileApps.startActivity(
                                NOT_MAIN_ACTIVITY, workProfile(sDeviceState).userHandle())));
    }

    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @PermissionTest({
            INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS,
            INTERACT_ACROSS_USERS_FULL})
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_withPermission_startsActivity() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        ActivityContext.runWithContext(activity -> {
            sCrossProfileApps.startActivity(
                    intent, workProfile(sDeviceState).userHandle(), activity);
        });

        assertThat(
                ActivityEvents.forActivity(NOT_MAIN_ACTIVITY, workProfile(sDeviceState))
                        .activityCreated()
        ).eventOccurred();
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(START_CROSS_PROFILE_ACTIVITIES)
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_withCrossProfileActivitiesPermission_throwsException()
            throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        ActivityContext.runWithContext(activity -> {
            assertThrows(SecurityException.class, () -> sCrossProfileApps.startActivity(
                    intent, workProfile(sDeviceState).userHandle(), activity));
        });
    }

    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @PermissionTest({
            INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS,
            INTERACT_ACROSS_USERS_FULL, START_CROSS_PROFILE_ACTIVITIES})
    @Postsubmit(reason = "new test")
    public void startActivity_byComponent_withPermission_startsActivity()
            throws Exception {
        ActivityContext.runWithContext(activity -> {
            sCrossProfileApps.startActivity(
                    NOT_MAIN_ACTIVITY, workProfile(sDeviceState).userHandle());
        });

        assertThat(
                ActivityEvents.forActivity(NOT_MAIN_ACTIVITY, workProfile(sDeviceState))
                        .activityCreated()
        ).eventOccurred();
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_PROFILES)
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_withOptionsBundle_startsActivity()
            throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        ActivityContext.runWithContext(activity -> {
            sCrossProfileApps.startActivity(
                    intent, workProfile(sDeviceState).userHandle(), activity,
                    ActivityOptions.makeBasic().toBundle());
        });

        assertThat(
                ActivityEvents.forActivity(NOT_MAIN_ACTIVITY, workProfile(sDeviceState))
                        .activityCreated()
        ).eventOccurred();
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_PROFILES)
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_notExported_startsActivity()
            throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_EXPORTED_ACTIVITY);

        ActivityContext.runWithContext(activity -> {
            sCrossProfileApps.startActivity(
                    intent, workProfile(sDeviceState).userHandle(), activity);
        });

        assertThat(
                ActivityEvents.forActivity(NOT_EXPORTED_ACTIVITY, workProfile(sDeviceState))
                        .activityCreated()
        ).eventOccurred();
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_PROFILES)
    @Postsubmit(reason = "new test")
    public void startActivity_byComponent_notExported_throwsException()
            throws Exception {
        ActivityContext.runWithContext(activity -> {
            assertThrows(SecurityException.class, () -> sCrossProfileApps.startActivity(
                    NOT_EXPORTED_ACTIVITY, workProfile(sDeviceState).userHandle()));
        });
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_PROFILES)
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_sameTaskByDefault() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        int originalTaskId = ActivityContext.getWithContext(activity -> {
            sCrossProfileApps.startActivity(
                    intent, workProfile(sDeviceState).userHandle(), activity);

            return activity.getTaskId();
        });

        ActivityCreatedEvent event =
                ActivityEvents.forActivity(NOT_MAIN_ACTIVITY, workProfile(sDeviceState))
                        .activityCreated().waitForEvent();
        assertThat(event.taskId()).isEqualTo(originalTaskId);
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(START_CROSS_PROFILE_ACTIVITIES)
    @Postsubmit(reason = "new test")
    public void startMainActivity_byComponent_nullActivity_newTask() throws Exception {
        int originalTaskId = ActivityContext.getWithContext(activity -> {
            sCrossProfileApps.startMainActivity(
                    MAIN_ACTIVITY,
                    workProfile(sDeviceState).userHandle(),
                    /* callingActivity */ null,
                    /* options */ null);

            return activity.getTaskId();
        });

        ActivityCreatedEvent event =
                ActivityEvents.forActivity(MAIN_ACTIVITY, workProfile(sDeviceState))
                        .activityCreated().waitForEvent();
        assertThat(event.taskId()).isNotEqualTo(originalTaskId);
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(START_CROSS_PROFILE_ACTIVITIES)
    @Postsubmit(reason = "new test")
    public void startMainActivity_byComponent_setsActivity_sameTask() throws Exception {
        int originalTaskId = ActivityContext.getWithContext(activity -> {
            sCrossProfileApps.startMainActivity(
                    MAIN_ACTIVITY,
                    workProfile(sDeviceState).userHandle(),
                    activity,
                    /* options */ null);

            return activity.getTaskId();
        });

        ActivityCreatedEvent event =
                ActivityEvents.forActivity(MAIN_ACTIVITY, workProfile(sDeviceState))
                        .activityCreated().waitForEvent();
        assertThat(event.taskId()).isEqualTo(originalTaskId);
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(START_CROSS_PROFILE_ACTIVITIES)
    @Postsubmit(reason = "new test")
    public void startNonMainActivity_byComponent_nullActivity_newTask() throws Exception {
        int originalTaskId = ActivityContext.getWithContext(activity -> {
            sCrossProfileApps.startActivity(
                    NOT_MAIN_ACTIVITY,
                    workProfile(sDeviceState).userHandle(),
                    /* callingActivity */ null,
                    /* options */ null);

            return activity.getTaskId();
        });

        ActivityCreatedEvent event =
                ActivityEvents.forActivity(NOT_MAIN_ACTIVITY, workProfile(sDeviceState))
                        .activityCreated().waitForEvent();
        assertThat(event.taskId()).isNotEqualTo(originalTaskId);
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(START_CROSS_PROFILE_ACTIVITIES)
    @Postsubmit(reason = "new test")
    public void startNonMainActivity_byComponent_setsActivity_sameTask() throws Exception {
        int originalTaskId = ActivityContext.getWithContext(activity -> {
            sCrossProfileApps.startActivity(
                    NOT_MAIN_ACTIVITY,
                    workProfile(sDeviceState).userHandle(),
                    activity,
                    /* options */ null);

            return activity.getTaskId();
        });

        ActivityCreatedEvent event =
                ActivityEvents.forActivity(NOT_MAIN_ACTIVITY, workProfile(sDeviceState))
                        .activityCreated().waitForEvent();
        assertThat(event.taskId()).isEqualTo(originalTaskId);
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_PROFILES)
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_logged() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            ActivityContext.runWithContext(activity ->
                    sCrossProfileApps.startActivity(
                    intent, workProfile(sDeviceState).userHandle(), activity));

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.START_ACTIVITY_BY_INTENT_VALUE)
                    .whereStrings().contains(sContext.getPackageName())
                    .whereBoolean().isFalse() // Not from work profile
            ).wasLogged();
        }
    }

    @Test
    @RequireRunOnWorkProfile(installInstrumentedAppInParent = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_PROFILES)
    @Postsubmit(reason = "new test")
    public void startActivity_byIntent_fromWorkProfile_logged() throws Exception {
        Intent intent = new Intent();
        intent.setComponent(NOT_MAIN_ACTIVITY);

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            ActivityContext.runWithContext(activity ->
                    sCrossProfileApps.startActivity(
                            intent, sDeviceState.initialUser().userHandle(), activity));

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.START_ACTIVITY_BY_INTENT_VALUE)
                    .whereStrings().contains(sContext.getPackageName())
                    .whereBoolean().isTrue() // From work profile
            ).wasLogged();
        }
    }

    @Test
    @CrossUserTest({
            @UserPair(from = INITIAL_USER, to = INITIAL_USER),
            @UserPair(from = INITIAL_USER, to = ADDITIONAL_USER),
            @UserPair(from = WORK_PROFILE, to = ADDITIONAL_USER),
            @UserPair(from = ADDITIONAL_USER, to = WORK_PROFILE)
    })
    public void
            startMainActivity_targetIsInvalid_throwsSecurityException() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        assertThrows(SecurityException.class,
                () -> sCrossProfileApps.startMainActivity(
                        MAIN_ACTIVITY, otherUser(sDeviceState).userHandle()));
    }

    @Test
    @CrossUserTest({
            @UserPair(from = INITIAL_USER, to = INITIAL_USER),
            @UserPair(from = INITIAL_USER, to = ADDITIONAL_USER),
            @UserPair(from = WORK_PROFILE, to = ADDITIONAL_USER),
            @UserPair(from = ADDITIONAL_USER, to = WORK_PROFILE)
    })
    public void getProfileSwitchingLabel_targetIsInvalid_throwsSecurityException() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingLabel(otherUser(sDeviceState).userHandle());
        });
    }


    @Test
    @CrossUserTest({
            @UserPair(from = WORK_PROFILE, to = INITIAL_USER),
            @UserPair(from = INITIAL_USER, to = WORK_PROFILE)
    })
    public void getProfileSwitchingLabel_targetIsValid_notNull() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        assertThat(sCrossProfileApps.getProfileSwitchingLabel(
                otherUser(sDeviceState).userHandle())).isNotNull();
    }

    @Test
    @CrossUserTest({
            @UserPair(from = INITIAL_USER, to = INITIAL_USER),
            @UserPair(from = INITIAL_USER, to = ADDITIONAL_USER),
            @UserPair(from = WORK_PROFILE, to = ADDITIONAL_USER),
            @UserPair(from = ADDITIONAL_USER, to = WORK_PROFILE)
    })
    public void getProfileSwitchingLabelIconDrawable_targetIsInvalid_throwsSecurityException() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.getProfileSwitchingIconDrawable(
                    otherUser(sDeviceState).userHandle());
        });
    }

    @Test
    @CrossUserTest({
            @UserPair(from = WORK_PROFILE, to = INITIAL_USER),
            @UserPair(from = INITIAL_USER, to = WORK_PROFILE)
    })
    public void getProfileSwitchingIconDrawable_targetIsValid_notNull() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        assertThat(sCrossProfileApps.getProfileSwitchingIconDrawable(
                otherUser(sDeviceState).userHandle())).isNotNull();
    }

    @Test
    @CrossUserTest({
            @UserPair(from = INITIAL_USER, to = WORK_PROFILE),
            @UserPair(from = WORK_PROFILE, to = INITIAL_USER)
    })
    public void canRequestInteractAcrossProfiles_hasValidTarget_returnsTrue()
            throws Exception {
        RemoteDevicePolicyManager profileOwner = profileOwner(sDeviceState, WORK_PROFILE)
                .devicePolicyManager();
        try (TestAppInstance currentApp = sCrossProfileTestApp.install();
             TestAppInstance otherApp = sCrossProfileTestApp.install(otherUser(sDeviceState))) {
            profileOwner.setCrossProfilePackages(
                    profileOwner(sDeviceState, WORK_PROFILE).componentName(),
                    Set.of(sCrossProfileTestApp.packageName()));

            assertThat(currentApp.crossProfileApps().canRequestInteractAcrossProfiles()).isTrue();
        }
    }

    @Test
    @EnsureHasNoProfile
    @RequireRunOnInitialUser
    public void canRequestInteractAcrossProfiles_noOtherProfiles_returnsFalse()
            throws Exception {
        try (TestAppInstance personalApp = sCrossProfileTestApp.install(
                sDeviceState.initialUser())) {

            assertThat(personalApp.crossProfileApps().canRequestInteractAcrossProfiles()).isFalse();
        }
    }

    @Test
    @EnsureHasWorkProfile
    @RequireRunOnInitialUser
    public void canRequestInteractAcrossProfiles_packageNotInAllowList_returnsTrue()
            throws Exception {
        RemoteDevicePolicyManager profileOwner = profileOwner(sDeviceState, WORK_PROFILE)
                .devicePolicyManager();
        try (TestAppInstance personalApp = sCrossProfileTestApp.install(
                sDeviceState.initialUser());
             TestAppInstance workApp = sCrossProfileTestApp.install(
                workProfile(sDeviceState))) {
            profileOwner.setCrossProfilePackages(
                    profileOwner(sDeviceState, WORK_PROFILE).componentName(),
                    Collections.emptySet());

            assertThat(personalApp.crossProfileApps().canRequestInteractAcrossProfiles()).isTrue();
        }
    }

    @Test
    @EnsureHasWorkProfile
    @RequireRunOnInitialUser
    public void canRequestInteractAcrossProfiles_packageNotInstalledInPersonalProfile_returnsTrue()
            throws Exception {
        RemoteDevicePolicyManager profileOwner = profileOwner(sDeviceState, WORK_PROFILE)
                .devicePolicyManager();
        try (TestAppInstance workApp = sCrossProfileTestApp.install(
                workProfile(sDeviceState))) {
            profileOwner.setCrossProfilePackages(
                    profileOwner(sDeviceState, WORK_PROFILE).componentName(),
                    Set.of(sCrossProfileTestApp.packageName()));

            assertThat(workApp.crossProfileApps().canRequestInteractAcrossProfiles()).isTrue();
        }
    }

    @Test
    @EnsureHasWorkProfile
    @RequireRunOnInitialUser
    public void canRequestInteractAcrossProfiles_packageNotInstalledInWorkProfile_returnsTrue()
            throws Exception {
        RemoteDevicePolicyManager profileOwner = profileOwner(sDeviceState, WORK_PROFILE)
                .devicePolicyManager();
        try (TestAppInstance personalApp = sCrossProfileTestApp.install(
                sDeviceState.initialUser())) {
            profileOwner.setCrossProfilePackages(
                    profileOwner(sDeviceState, WORK_PROFILE).componentName(),
                    Set.of(sCrossProfileTestApp.packageName()));

            assertThat(personalApp.crossProfileApps().canRequestInteractAcrossProfiles()).isTrue();
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    public void canRequestInteractAcrossProfiles_permissionNotRequested_returnsFalse()
            throws Exception {
        RemoteDevicePolicyManager profileOwner = profileOwner(sDeviceState, WORK_PROFILE)
                .devicePolicyManager();
        try (TestAppInstance personalApp = sNonCrossProfileTestApp.install(
                sDeviceState.initialUser());
             TestAppInstance workApp = sNonCrossProfileTestApp.install(
                workProfile(sDeviceState))) {
            profileOwner.setCrossProfilePackages(
                    profileOwner(sDeviceState, WORK_PROFILE).componentName(),
                    Set.of(sCrossProfileTestApp.packageName()));

            assertThat(personalApp.crossProfileApps().canRequestInteractAcrossProfiles()).isFalse();
        }
    }

    // TODO(b/199148889): add require INTERACT_ACROSS_PROFILE permission for the dpc.
    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    public void canRequestInteractAcrossProfiles_profileOwner_returnsFalse()
            throws Exception {
        RemoteDevicePolicyManager profileOwner = profileOwner(sDeviceState, WORK_PROFILE)
                .devicePolicyManager();
        profileOwner.setCrossProfilePackages(
                profileOwner(sDeviceState, WORK_PROFILE).componentName(),
                Set.of(profileOwner(sDeviceState, WORK_PROFILE).componentName().getPackageName()));

        assertThat(
                profileOwner(sDeviceState, WORK_PROFILE).crossProfileApps()
                        .canRequestInteractAcrossProfiles()
        ).isFalse();
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    public void canInteractAcrossProfiles_appOpIsSetOnAllProfiles_returnsTrue() {
        try (TestAppInstance primaryApp = sCrossProfileTestApp.install();
             TestAppInstance workApp = sCrossProfileTestApp.install(workProfile(sDeviceState))) {
            sCrossProfileTestApp.pkg().appOps().set(
                    AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES, AppOpsMode.ALLOWED);
            sCrossProfileTestApp.pkg().appOps(workProfile(sDeviceState)).set(
                    AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES,
                    AppOpsMode.ALLOWED);

            assertThat(primaryApp.crossProfileApps().canInteractAcrossProfiles()).isTrue();
            assertThat(workApp.crossProfileApps().canInteractAcrossProfiles()).isTrue();
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    public void canInteractAcrossProfiles_appOpDisabledOnCaller_returnsFalse() {
        try (TestAppInstance primaryApp = sCrossProfileTestApp.install();
             TestAppInstance workApp = sCrossProfileTestApp.install(workProfile(sDeviceState))) {
            sCrossProfileTestApp.pkg().appOps().set(
                    AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES, AppOpsMode.DEFAULT);
            sCrossProfileTestApp.pkg().appOps(workProfile(sDeviceState)).set(
                    AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES,
                    AppOpsMode.ALLOWED);

            assertThat(primaryApp.crossProfileApps().canInteractAcrossProfiles()).isFalse();
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    public void canInteractAcrossProfiles_appOpDisabledOnOtherProfile_returnsFalse() {
        try (TestAppInstance primaryApp = sCrossProfileTestApp.install();
             TestAppInstance workApp = sCrossProfileTestApp.install(workProfile(sDeviceState))) {
            sCrossProfileTestApp.pkg().appOps().set(
                    AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES, AppOpsMode.ALLOWED);
            sCrossProfileTestApp.pkg().appOps(workProfile(sDeviceState)).set(
                    AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES,
                    AppOpsMode.DEFAULT);

            assertThat(primaryApp.crossProfileApps().canInteractAcrossProfiles()).isFalse();
        }
    }

    @Test
    @RequireRunOnInitialUser
    public void canInteractAcrossProfiles_noOtherProfile_returnsFalse() {
        try (TestAppInstance primaryApp = sCrossProfileTestApp.install()) {
            sCrossProfileTestApp.pkg().appOps().set(
                    AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES, AppOpsMode.ALLOWED);

            assertThat(primaryApp.crossProfileApps().canInteractAcrossProfiles()).isFalse();
        }
    }



    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @PermissionTest({
            INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS, INTERACT_ACROSS_USERS_FULL})
    // TODO(b/191637162): When we can adopt permissions for testapps, we can use testapps here
    public void canInteractAcrossProfiles_permissionIsSet_returnsTrue() {
        TestApis.packages().instrumented().appOps(workProfile(sDeviceState)).set(
                AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES,
                AppOpsMode.ALLOWED);

        assertThat(sCrossProfileApps.canInteractAcrossProfiles()).isTrue();
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasNoProfile
    public void createRequestInteractAcrossProfilesIntent_canNotRequest_throwsException() {
        try (TestAppInstance primaryApp = sCrossProfileTestApp.install()) {
            assertThrows(SecurityException.class,
                    () -> primaryApp.crossProfileApps()
                            .createRequestInteractAcrossProfilesIntent());
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    public void createRequestInteractAcrossProfilesIntent_canRequest_returnsIntent() {
        try (TestAppInstance primaryApp = sCrossProfileTestApp.install();
             TestAppInstance workApp = sCrossProfileTestApp.install(workProfile(sDeviceState))) {
            sCrossProfileTestApp.pkg().appOps().set(
                    AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES, AppOpsMode.ALLOWED);
            sCrossProfileTestApp.pkg().appOps(workProfile(sDeviceState)).set(
                    AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES,
                    AppOpsMode.ALLOWED);

            Intent intent = primaryApp.crossProfileApps()
                    .createRequestInteractAcrossProfilesIntent();

            assertThat(intent.getAction()).isEqualTo(ACTION_MANAGE_CROSS_PROFILE_ACCESS);
            assertThat(intent.getData().getSchemeSpecificPart())
                    .isEqualTo(sCrossProfileTestApp.packageName());
        }
    }

    @Test
    @RequireRunOnWorkProfile
    public void setCrossProfilePackages_packageRemoved_packageReceivesBroadcast() {
        try (TestAppInstance parentTestApp = sCrossProfileTestApp.install(TestApis.users().instrumented().parent());
            TestAppInstance workTestApp = sCrossProfileTestApp.install()) {
            dpc(sDeviceState).devicePolicyManager()
                    .setCrossProfilePackages(
                            dpc(sDeviceState).componentName(), Set.of(sCrossProfileTestApp.packageName()));
            parentTestApp.appOps().setPermission(INTERACT_ACROSS_PROFILES, AppOpsMode.ALLOWED);
            workTestApp.appOps().setPermission(INTERACT_ACROSS_PROFILES, AppOpsMode.ALLOWED);
            EventLogs.resetLogs(); // Ignore any broadcasts related to setting the appOps

            dpc(sDeviceState).devicePolicyManager()
                    .setCrossProfilePackages(
                            dpc(sDeviceState).componentName(), Set.of());

            assertThat(parentTestApp.events().broadcastReceived()
                    .whereIntent().action().isEqualTo(ACTION_CAN_INTERACT_ACROSS_PROFILES_CHANGED))
                    .eventOccurred();
            assertThat(workTestApp.events().broadcastReceived()
                    .whereIntent().action().isEqualTo(ACTION_CAN_INTERACT_ACROSS_PROFILES_CHANGED))
                    .eventOccurred();
        }
    }

    @CrossUserTest({@UserPair(from = INITIAL_USER, to = PRIVATE_PROFILE),
            @UserPair(from = WORK_PROFILE, to = PRIVATE_PROFILE),
            @UserPair(from = CLONE_PROFILE, to = PRIVATE_PROFILE)})
    @Postsubmit(reason = "new test")
    @RequireFlagsEnabled({android.os.Flags.FLAG_ALLOW_PRIVATE_PROFILE,
            android.multiuser.Flags.FLAG_ENABLE_HIDING_PROFILES,
            android.multiuser.Flags.FLAG_ENABLE_PRIVATE_SPACE_FEATURES})
    public void getTargetUserProfiles_excludeHiddenProfiles() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        List<UserHandle> targetProfiles = sCrossProfileApps.getTargetUserProfiles();

        assertThat(targetProfiles).doesNotContain(otherUser(sDeviceState).userHandle());
    }

    @CrossUserTest({@UserPair(from = INITIAL_USER, to = PRIVATE_PROFILE),
            @UserPair(from = WORK_PROFILE, to = PRIVATE_PROFILE),
            @UserPair(from = CLONE_PROFILE, to = PRIVATE_PROFILE)})
    @Postsubmit(reason = "new test")
    @RequireFlagsEnabled({android.os.Flags.FLAG_ALLOW_PRIVATE_PROFILE,
            android.multiuser.Flags.FLAG_ENABLE_HIDING_PROFILES,
            android.multiuser.Flags.FLAG_ENABLE_PRIVATE_SPACE_FEATURES})
    public void startMainActivity_hiddenProfile_throwsException() {
        TestApis.packages().instrumented().installExisting(otherUser(sDeviceState));

        assertThrows(SecurityException.class, () -> {
            sCrossProfileApps.startMainActivity(MAIN_ACTIVITY,
                    otherUser(sDeviceState).userHandle());
        });
    }

    @CrossUserTest(@UserPair(from = INITIAL_USER, to = PRIVATE_PROFILE))
    @Postsubmit(reason = "new test")
    @RequireFlagsEnabled({android.os.Flags.FLAG_ALLOW_PRIVATE_PROFILE,
            android.multiuser.Flags.FLAG_ENABLE_HIDING_PROFILES,
            android.multiuser.Flags.FLAG_ENABLE_PRIVATE_SPACE_FEATURES})
    public void canInteractAcrossProfiles_initialUserToHiddenProfile_returnsFalse() {
        try (TestAppInstance primaryApp = sCrossProfileTestApp.install()) {
            sCrossProfileTestApp.pkg().appOps().set(AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES,
                    AppOpsMode.ALLOWED);

            assertThat(primaryApp.crossProfileApps().canInteractAcrossProfiles()).isFalse();
        }
    }
}