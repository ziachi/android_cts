/*
 * Copyright (C) 2022 The Android Open Source Project
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


import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_BACKUP;
import static com.android.bedstead.permissions.CommonPermissions.BACKUP;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.fail;

import android.app.admin.RemoteDevicePolicyManager;
import android.app.admin.SecurityLog;
import android.app.admin.SecurityLog.SecurityEvent;
import android.app.backup.BackupManager;
import android.content.ComponentName;
import android.content.Context;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnumTestParameter;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.policies.Backup;
import com.android.bedstead.harrier.policies.BackupAppliesToParent;
import com.android.bedstead.harrier.policies.SecurityLoggingDelegation;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

@RunWith(BedsteadJUnit4.class)
@RequireFeature(FEATURE_BACKUP)
public final class BackupTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final BackupManager sLocalBackupManager = new BackupManager(sContext);

    @PolicyAppliesTest(policy = Backup.class)
    @EnsureHasPermission(BACKUP)
    @Postsubmit(reason = "new test")
    public void isBackupEnabled_default_returnsFalse() {
        assertThat(sLocalBackupManager.isBackupEnabled()).isFalse();
    }

    @PolicyAppliesTest(policy = Backup.class)
    @Postsubmit(reason = "new test")
    public void isBackupServiceEnabled_default_returnsFalse() {
        assertThat(dpc(sDeviceState).devicePolicyManager().isBackupServiceEnabled(
                dpc(sDeviceState).componentName())).isFalse();
    }

    @PolicyAppliesTest(policy = Backup.class)
    @Postsubmit(reason = "new test")
    @EnsureHasPermission(BACKUP)
    public void setBackupServiceEnabled_true_setsBackupServiceEnabled() {
        assumeFalse("Logic is special cased on headless system user",
                TestApis.users().instrumented().type()
                        .name().equals("android.os.usertype.system.HEADLESS"));

        try {
            dpc(sDeviceState).devicePolicyManager().setBackupServiceEnabled(
                    dpc(sDeviceState).componentName(), true);

            Poll.forValue("DPC isBackupServiceEnabled",
                    () -> dpc(sDeviceState).devicePolicyManager().isBackupServiceEnabled(
                            dpc(sDeviceState).componentName()))
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
            assertThat(sLocalBackupManager
                    .isBackupServiceActive(TestApis.users().instrumented().userHandle())).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setBackupServiceEnabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @PolicyAppliesTest(policy = Backup.class)
    @Postsubmit(reason = "new test")
    @EnsureHasPermission(BACKUP)
    public void setBackupServiceEnabled_false_setsBackupServiceNotEnabled() {
        try {
            dpc(sDeviceState).devicePolicyManager().setBackupServiceEnabled(
                    dpc(sDeviceState).componentName(), false);


            Poll.forValue("DPC isBackupServiceEnabled",
                            () -> dpc(sDeviceState).devicePolicyManager().isBackupServiceEnabled(
                                    dpc(sDeviceState).componentName()))
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();
            assertThat(sLocalBackupManager
                    .isBackupServiceActive(TestApis.users().instrumented().userHandle())).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setBackupServiceEnabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @PolicyDoesNotApplyTest(policy = BackupAppliesToParent.class)
    @Postsubmit(reason = "new test")
    @EnsureHasPermission(BACKUP)
    public void setBackupServiceEnabled_doesNotApply_doesNotSetBackupServiceEnabled() {
        try {
            dpc(sDeviceState).devicePolicyManager().setBackupServiceEnabled(
                    dpc(sDeviceState).componentName(), true);

            assertThat(sLocalBackupManager
                    .isBackupServiceActive(TestApis.users().instrumented().userHandle())).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setBackupServiceEnabled(
                    dpc(sDeviceState).componentName(), false);
        }
    }

    @CannotSetPolicyTest(policy = Backup.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void setBackupServiceEnabled_cannotSetPolicy_throwsException() {
        assertThrows(SecurityException.class, () -> {
            dpc(sDeviceState).devicePolicyManager().setBackupServiceEnabled(
                    dpc(sDeviceState).componentName(), true);
        });
    }

    @CannotSetPolicyTest(policy = Backup.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void isBackupServiceEnabled_cannotSetPolicy_throwsException() {
        assertThrows(SecurityException.class, () -> {
            dpc(sDeviceState).devicePolicyManager().isBackupServiceEnabled(
                    dpc(sDeviceState).componentName());
        });
    }

    @CanSetPolicyTest(policy = Backup.class)
    @Postsubmit(reason = "new test")
    public void isBackupServiceEnabled_canSetPolicy_doesNotThrow() {
        dpc(sDeviceState).devicePolicyManager().isBackupServiceEnabled(
                dpc(sDeviceState).componentName());
    }

    public enum BackupServiceState {
        ENABLED(true, 1),
        DISABLED(false, 0);

        BackupServiceState(boolean enabled, int loggedValue) {
            this.enabled = enabled;
            this.loggedValue = loggedValue;
        }

        public final boolean enabled;
        public final int loggedValue;
    }

    /** Positive test for SecurityLog#TAG_BACKUP_SERVICE_TOGGLED */
    @CanSetPolicyTest(policyIntersection = {Backup.class,
            SecurityLoggingDelegation.class})
    @ApiTest(apis = {"android.app.admin.SecurityLog#TAG_BACKUP_SERVICE_TOGGLED"})
    @Postsubmit(reason = "new test")
    public void setBackupServiceEnabled_SecurityLogEventsEmitted(
            @EnumTestParameter(BackupServiceState.class) BackupServiceState param)
            throws Exception {
        // Timestamp to filter out events from previous tests if any.
        long testStartTimeNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());

        ensureNoAdditionalUsers();
        var dpm = dpc(sDeviceState).devicePolicyManager();
        var who = dpc(sDeviceState).componentName();

        boolean savedState = dpm.isBackupServiceEnabled(who);
        try {
            // Start with backup enabled
            dpm.setBackupServiceEnabled(who, true);
            // Flush any existing security logs
            dpm.setSecurityLoggingEnabled(who, false);
            dpm.setSecurityLoggingEnabled(who, true);

            // Setting backup service state and check security log
            dpm.setBackupServiceEnabled(who, param.enabled);

            verifySecurityEventLogged(dpm, who,
                    /* filter */
                    e -> e.getTag() == SecurityLog.TAG_BACKUP_SERVICE_TOGGLED
                            && e.getTimeNanos() >= testStartTimeNanos,
                    /* assertion */
                    e -> {
                        assertThat(e.getStringData(0)).isEqualTo(who.getPackageName());
                        assertThat(e.getIntegerData(1)).isEqualTo(dpc(sDeviceState).user().id());
                        assertThat(e.getIntegerData(2)).isEqualTo(param.loggedValue);
                    });
        } finally {
            dpm.setSecurityLoggingEnabled(who, false);
            dpm.setBackupServiceEnabled(who, savedState);
        }
    }

    private static void verifySecurityEventLogged(RemoteDevicePolicyManager dpm, ComponentName who,
            Predicate<SecurityEvent> filter, Consumer<SecurityEvent> assertion) {
        // Retry once in case the first time the event didn't reach logd buffer.
        for (int i = 0; i < 2; i++) {
            TestApis.devicePolicy().forceSecurityLogs();

            var events = dpm.retrieveSecurityLogs(who);
            if (events == null) continue;

            var filteredEvents = events.stream().filter(filter).toList();
            if (filteredEvents.isEmpty()) continue;

            assertWithMessage("More than one event found").that(filteredEvents).hasSize(1);
            assertion.accept(filteredEvents.get(0));
            return;
        }
        fail("Wasn't able to find matching event");
    }

    private void ensureNoAdditionalUsers() {
        // TODO(273474964): Move into infra
        try {
            TestApis.users().all().stream().filter(u -> (u != TestApis.users().instrumented()
                    && u != TestApis.users().system()
                    && u != TestApis.users().current() // We can't remove the profile of
                    // the instrumented user for the run on parent profile tests. But the profiles
                    // of other users will be removed when the full-user is removed anyway.
//                    && !u.isProfile() - temporarily disabled as this would cause failures if there was a clone profile on the device
            )).forEach(UserReference::remove);
        } catch (NeneException e) {
            // Happens when we can't remove a user
            throw new NeneException(
                    "Error when removing user. Instrumented user is "
                            + TestApis.users().instrumented() + ", current user is "
                            + TestApis.users().current() + ", system user is "
                            + TestApis.users().system(), e
            );
        }
    }
}
