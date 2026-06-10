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
package android.devicepolicy.cts

import android.app.admin.DevicePolicyManager
import android.app.admin.SecurityLog
import android.app.admin.SecurityLog.SecurityEvent
import android.os.SystemClock
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwner
import com.android.bedstead.enterprise.dpc
import com.android.bedstead.enterprise.dpcOnly
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasNoAdditionalUser
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.SlowApiTest
import com.android.bedstead.harrier.policies.GlobalSecurityLogging
import com.android.bedstead.harrier.policies.SecurityLogging
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.permissions.CommonPermissions
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.BlockingCallback
import com.android.eventlib.truth.EventLogsSubject.assertThat
import com.google.common.truth.Truth.assertThat
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
class SecurityLoggingTest {
    @CannotSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled"])
    fun setSecurityLoggingEnabled_notPermitted_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled",
            "android.app.admin.DevicePolicyManager#isSecurityLoggingEnabled"]
    )
    fun setSecurityLoggingEnabled_true_securityLoggingIsEnabled() {
        val originalSecurityLoggingEnabled = deviceState.dpc()
            .devicePolicyManager().isSecurityLoggingEnabled(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            assertThat(
                deviceState.dpc().devicePolicyManager().isSecurityLoggingEnabled(
                    deviceState.dpc().componentName()
                )
            ).isTrue()
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                originalSecurityLoggingEnabled
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled",
            "android.app.admin.DevicePolicyManager#isSecurityLoggingEnabled"]
    )
    fun setSecurityLoggingEnabled_false_securityLoggingIsNotEnabled() {
        val originalSecurityLoggingEnabled = deviceState.dpc()
            .devicePolicyManager().isSecurityLoggingEnabled(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
            assertThat(
                deviceState.dpc().devicePolicyManager().isSecurityLoggingEnabled(
                    deviceState.dpc().componentName()
                )
            ).isFalse()
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                originalSecurityLoggingEnabled
            )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isSecurityLoggingEnabled"])
    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    fun isSecurityLoggingEnabled_notPermitted_throwsException() {
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().isSecurityLoggingEnabled(
                deviceState.dpc().componentName()
            )
        }
    }

    @CannotSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveSecurityLogs"])
    fun retrieveSecurityLogs_notPermitted_throwsException() {
        ensureNoAdditionalFullUsers()
        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                deviceState.dpc().componentName()
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
        apis = ["android.app.admin.DeviceAdminReceiver#onSecurityLogsAvailable",
        "android.app.admin.DevicePolicyManager#retrieveSecurityLogs"]
    )
    fun retrieveSecurityLogs_logsCanBeFetchedAfterOnSecurityLogsAvailableCallback() {
        ensureNoAdditionalFullUsers()

        val dpc = deviceState.dpc()

        try {
            dpc.devicePolicyManager().setSecurityLoggingEnabled(dpc.componentName(), true)

            TestApis.devicePolicy().forceSecurityLogs()
            assertThat(dpc.events().securityLogsAvailable()).eventOccurred()

            val logs = dpc.devicePolicyManager().retrieveSecurityLogs(dpc.componentName())
            assertThat(logs).isNotNull()
            assertThat(logs.stream().filter { e -> e.tag == SecurityLog.TAG_LOGGING_STARTED })
                    .isNotNull()
        } finally {
            dpc.devicePolicyManager().setSecurityLoggingEnabled(dpc.componentName(), false)
        }
    }

    // TODO: Add test that logs are filtered for non-device-owner
    // TODO: Add test for rate limiting

    @CannotSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_notPermitted_throwsException() {
        ensureNoAdditionalFullUsers()

        assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                deviceState.dpc().componentName()
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_doesNotThrow() {
        ensureNoAdditionalFullUsers()
        val originalSecurityLoggingEnabled = deviceState.dpc().devicePolicyManager()
            .isSecurityLoggingEnabled(deviceState.dpc().componentName())
        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )

            // Nothing to assert as this can be null on some devices
            deviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                deviceState.dpc().componentName()
            )
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                originalSecurityLoggingEnabled
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class], singleTestOnly = true)
    @EnsureHasAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveSecurityLogs"])
    fun retrieveSecurityLogs_unaffiliatedAdditionalUser_throwsException() {
        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            assertThrows(SecurityException::class.java) {
                deviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                    deviceState.dpc().componentName()
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @EnsureHasAdditionalUser
    @EnsureHasProfileOwner(onUser = UserType.ADDITIONAL_USER, affiliationIds = ["affiliated"])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveSecurityLogs"])
    fun retrieveSecurityLogs_affiliatedAdditionalUser_doesNotThrowException() {
        // TODO(273474964): Move into infra
        TestApis.users().all().stream()
            .filter { u: UserReference ->
                (u != TestApis.users().instrumented() &&
                        u != TestApis.users().system() &&
                        u != deviceState.additionalUser() &&
                        u != TestApis.users().current() &&
                        !u.isMain)
            }
            .forEach { obj: UserReference -> obj.remove() }
        val affiliationIds: MutableSet<String> = HashSet(
            deviceState.dpcOnly().devicePolicyManager()
                .getAffiliationIds(deviceState.dpcOnly().componentName())
        )
        affiliationIds.add("affiliated")
        deviceState.dpcOnly().devicePolicyManager().setAffiliationIds(
            deviceState.dpc().componentName(),
            affiliationIds
        )

        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            deviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                deviceState.dpc().componentName()
            )
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @EnsureHasNoAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveSecurityLogs"])
    fun retrieveSecurityLogs_noAdditionalUser_doesNotThrowException() {
        ensureNoAdditionalFullUsers()

        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            deviceState.dpc().devicePolicyManager().retrieveSecurityLogs(
                deviceState.dpc().componentName()
            )
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class])
    @EnsureHasAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_unaffiliatedAdditionalUser_throwsException() {
        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            assertThrows(SecurityException::class.java) {
                deviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                    deviceState.dpc().componentName()
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @EnsureHasAdditionalUser
    @EnsureHasProfileOwner(onUser = UserType.ADDITIONAL_USER, affiliationIds = ["affiliated"])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_affiliatedAdditionalUser_doesNotThrowException() {
        // TODO(273474964): Move into infra
        TestApis.users().all().stream()
            .filter { u: UserReference ->
                (u != TestApis.users().instrumented() &&
                        u != TestApis.users().system() &&
                        u != deviceState.additionalUser() &&
                        u != TestApis.users().current())
            }
            .forEach { obj: UserReference -> obj.remove() }
        val affiliationIds: MutableSet<String> = HashSet(
            deviceState.dpcOnly().devicePolicyManager()
                .getAffiliationIds(deviceState.dpcOnly().componentName())
        )
        affiliationIds.add("affiliated")
        deviceState.dpcOnly().devicePolicyManager().setAffiliationIds(
            deviceState.dpc().componentName(),
            affiliationIds
        )

        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            deviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                deviceState.dpc().componentName()
            )
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @EnsureHasNoAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrievePreRebootSecurityLogs"])
    fun retrievePreRebootSecurityLogs_noAdditionalUser_doesNotThrowException() {
        ensureNoAdditionalFullUsers()

        try {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            deviceState.dpc().devicePolicyManager().retrievePreRebootSecurityLogs(
                deviceState.dpc().componentName()
            )
        } finally {
            deviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    /**
     * Here and below - audit logging is only available on corporate owned devices, i.e. in the same
     * configurations when security logging is enabled, so this is a PolicyTest.
     */
    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setAuditLogEnabled",
                "android.app.admin.DevicePolicyManager#isAuditLogEnabled"]
    )
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    fun setAuditLogEnabled_withPermission_works() {
        ensureNoAdditionalFullUsers()

        localDpm.setAuditLogEnabled(true)
        try {
            assertThat(localDpm.isAuditLogEnabled()).isTrue()
        } finally {
            localDpm.setAuditLogEnabled(false)
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setAuditLogEnabled",
                "android.app.admin.DevicePolicyManager#isAuditLogEnabled"]
    )
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    fun setAuditLogEnabled_true_false_isFalse() {
        ensureNoAdditionalFullUsers()
        localDpm.setAuditLogEnabled(true)

        localDpm.setAuditLogEnabled(false)

        assertThat(localDpm.isAuditLogEnabled()).isFalse()
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setAuditLogEnabled"]
    )
    @EnsureDoesNotHavePermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    fun setAuditLogEnabled_withoutPermission_throws() {
        ensureNoAdditionalFullUsers()

        try {
            assertThrows(SecurityException::class.java) {
                localDpm.setAuditLogEnabled(true)
            }
        } finally {
            try {
                localDpm.setAuditLogEnabled(false)
            } catch (e: Exception) {
                // ignored - the policy shouldn't be set in the first place.
            }
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setAuditLogEnabled",
                "android.app.admin.DevicePolicyManager#setAuditLogEventCallback"]
    )
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    fun setAuditLogEventCallback_callbackInvokedInitially() {
        ensureNoAdditionalFullUsers()

        localDpm.setAuditLogEnabled(true)
        try {
            val callback = SecurityEventCallback()
            localDpm.setAuditLogEventCallback(executor, callback)

            val events = callback.await()
            assertThat(events.stream().filter { e ->
                e.tag == SecurityLog.TAG_LOGGING_STARTED
            }).isNotNull()
        } finally {
            localDpm.setAuditLogEnabled(false)
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setAuditLogEnabled",
                "android.app.admin.DevicePolicyManager#setAuditLogEventCallback"]
    )
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    fun setAuditLogEventCallback_callbackInvokedFurther() {
        ensureNoAdditionalFullUsers()

        localDpm.setAuditLogEnabled(true)
        try {
            val callback = QueueingEventCallback()
            localDpm.setAuditLogEventCallback(executor, callback)

            TestApis.devicePolicy().forceSecurityLogs()

            // Wait for any events.
            assertThat(callback.poll({true})).isNotNull()

            val eventPredicate = makeUniqueEvent()

            assertThat(callback.poll(eventPredicate)).isNotNull()
        } finally {
            localDpm.setAuditLogEnabled(false)
        }
    }

    @SlowApiTest("callback is invoked once a minute, this test waits for 2m to ensure it is not.")
    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setAuditLogEnabled",
                "android.app.admin.DevicePolicyManager#setAuditLogEventCallback"]
    )
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    fun setAuditLogEventCallback_auditLoggingDisabled_notInvoked() {
        ensureNoAdditionalFullUsers()

        localDpm.setAuditLogEnabled(true)
        try {
            val callback = QueueingEventCallback()
            localDpm.setAuditLogEventCallback(executor, callback)

            // Disable audit logging
            localDpm.setAuditLogEnabled(false)

            // Generate new event
            val eventPredicate = makeUniqueEvent()

            // We can't force logs since logging is not enabled, will need longer timeout.
            assertThat(callback.poll(eventPredicate, TimeUnit.MINUTES.toMillis(2))).isNull()
        } finally {
            localDpm.setAuditLogEnabled(false)
        }
    }

    @SlowApiTest("Test needs to wait to ensure the callback is not invoked.")
    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setAuditLogEnabled",
                "android.app.admin.DevicePolicyManager#setAuditLogEventCallback"]
    )
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    fun setAuditLogEventCallback_auditLoggingDisabled_withSecurityLoggingEnabled_notInvoked() {
        ensureNoAdditionalFullUsers()

        val dpc = deviceState.dpc()
        val remoteDpm = dpc.devicePolicyManager()
        val who = dpc.componentName()

        localDpm.setAuditLogEnabled(true)
        try {
            remoteDpm.setSecurityLoggingEnabled(who, true)
            val callback = QueueingEventCallback()
            localDpm.setAuditLogEventCallback(executor, callback)

            // Disable audit logging
            localDpm.setAuditLogEnabled(false)

            // Generate new event
            val eventPredicate = makeUniqueEvent()
            TestApis.devicePolicy().forceSecurityLogs()

            // Should timeout
            assertThat(callback.poll(eventPredicate)).isNull()
        } finally {
            localDpm.setAuditLogEnabled(false)
            remoteDpm.setSecurityLoggingEnabled(who, false)
        }
    }

    @SlowApiTest("Test needs to wait to ensure the callback is not invoked.")
    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setAuditLogEnabled",
                "android.app.admin.DevicePolicyManager#setAuditLogEventCallback"]
    )
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    fun setAuditLogEventCallback_callbackCleared_notInvoked() {
        ensureNoAdditionalFullUsers()

        localDpm.setAuditLogEnabled(true)
        try {
            val callback = QueueingEventCallback()
            localDpm.setAuditLogEventCallback(executor, callback)
            localDpm.clearAuditLogEventCallback()

            val eventPredicate = makeUniqueEvent()
            TestApis.devicePolicy().forceSecurityLogs()

            // Should timeout
            assertThat(callback.poll(eventPredicate)).isNull()
        } finally {
            localDpm.setAuditLogEnabled(false)
        }
    }

    @SlowApiTest("Test needs to wait to ensure the callback is not invoked.")
    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setAuditLogEnabled",
                "android.app.admin.DevicePolicyManager#setAuditLogEventCallback"]
    )
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    fun setAuditLogEventCallback_callbackReplaced_onlyNewCallbackInvoked() {
        ensureNoAdditionalFullUsers()

        localDpm.setAuditLogEnabled(true)
        try {
            val callback1 = QueueingEventCallback()
            localDpm.setAuditLogEventCallback(executor, callback1)

            val callback2 = QueueingEventCallback()
            localDpm.setAuditLogEventCallback(executor, callback2)

            val eventPredicate = makeUniqueEvent()
            TestApis.devicePolicy().forceSecurityLogs()

            // Should timeout on the old callback, but invoke the new one.
            assertThat(callback1.poll(eventPredicate)).isNull()
            assertThat(callback2.poll(eventPredicate)).isNotNull()
        } finally {
            localDpm.setAuditLogEnabled(false)
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled",
                "android.app.admin.DevicePolicyManager#retrieveSecurityLogs",
                "android.app.admin.DevicePolicyManager#setAuditLogEnabled",
                "android.app.admin.DevicePolicyManager#setAuditLogEventCallback"]
    )
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    fun mixedLogging_securityAndAuditLoggingWorkTogether() {
        ensureNoAdditionalFullUsers()

        val dpc = deviceState.dpc()
        val remoteDpm = dpc.devicePolicyManager()
        val who = dpc.componentName()

        try {
            // Enable both mechanisms
            remoteDpm.setSecurityLoggingEnabled(who, true)
            localDpm.setAuditLogEnabled(true)
            val callback = QueueingEventCallback()
            localDpm.setAuditLogEventCallback(executor, callback)

            // Generate new event
            val eventPredicate = makeUniqueEvent()

            // Verify that the new event is available in audit logging API.
            TestApis.devicePolicy().forceSecurityLogs()
            assertThat(callback.poll(eventPredicate)).isNotNull()

            // Verify that the new event is available in security logging API.
            assertThat(dpc.events().securityLogsAvailable()).eventOccurred()
            val logs = dpc.devicePolicyManager().retrieveSecurityLogs(dpc.componentName())
            assertThat(logs).isNotNull()
            assertThat(logs.stream().filter(eventPredicate)).isNotNull()
        } finally {
            localDpm.setAuditLogEnabled(false)
            remoteDpm.setSecurityLoggingEnabled(who, false)
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled",
                "android.app.admin.DevicePolicyManager#setAuditLogEnabled",
                "android.app.admin.DevicePolicyManager#setAuditLogEventCallback"]
    )
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    fun mixedLogging_auditLoggingWorksAfterSecurityLoggingDisabled() {
        ensureNoAdditionalFullUsers()

        val dpc = deviceState.dpc()
        val remoteDpm = dpc.devicePolicyManager()
        val who = dpc.componentName()

        try {
            // Enable both mechanisms
            remoteDpm.setSecurityLoggingEnabled(who, true)
            localDpm.setAuditLogEnabled(true)
            val callback = QueueingEventCallback()
            localDpm.setAuditLogEventCallback(executor, callback)

            TestApis.devicePolicy().forceSecurityLogs()

            // Ensure we get the first callback:
            assertThat(callback.poll({it.tag == SecurityLog.TAG_LOGGING_STARTED})).isNotNull()

            // Disable security logging
            remoteDpm.setSecurityLoggingEnabled(who, false)

            // Generate new event
            val eventPredicate = makeUniqueEvent()

            // Verify that new event is still delivered via audit logging API.
            TestApis.devicePolicy().forceSecurityLogs()
            assertThat(callback.poll(eventPredicate)).isNotNull()
        } finally {
            localDpm.setAuditLogEnabled(false)
            remoteDpm.setSecurityLoggingEnabled(who, false)
        }
    }

    @CanSetPolicyTest(policy = [GlobalSecurityLogging::class, SecurityLogging::class])
    @Postsubmit(reason = "new test")
    @ApiTest(
            apis = ["android.app.admin.DevicePolicyManager#setSecurityLoggingEnabled",
                "android.app.admin.DevicePolicyManager#retrieveSecurityLogs",
                "android.app.admin.DevicePolicyManager#setAuditLogEnabled"]
    )
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    fun mixedLogging_securityLoggingWorksAfterAuditLoggingDisabled() {
        ensureNoAdditionalFullUsers()

        val dpc = deviceState.dpc()
        val remoteDpm = dpc.devicePolicyManager()
        val who = dpc.componentName()

        try {
            // Enable both mechanisms
            localDpm.setAuditLogEnabled(true)
            remoteDpm.setSecurityLoggingEnabled(who, true)

            // Ensure security logging is working initially.
            TestApis.devicePolicy().forceSecurityLogs()
            assertThat(dpc.events().securityLogsAvailable().poll()).isNotNull()

            // Disable audit logging
            localDpm.setAuditLogEnabled(false)

            // Generate new event
            val eventPredicate = makeUniqueEvent()

            // Verify that new event is still delivered via security logging API.
            TestApis.devicePolicy().forceSecurityLogs()
            assertThat(dpc.events().securityLogsAvailable().poll()).isNotNull()
            val logs = dpc.devicePolicyManager().retrieveSecurityLogs(dpc.componentName())
            assertThat(logs).isNotNull()
            assertThat(logs.stream().filter(eventPredicate)).isNotNull()
        } finally {
            localDpm.setAuditLogEnabled(false)
            remoteDpm.setSecurityLoggingEnabled(who, false)
        }
    }

    private fun SecurityEvent.stringAt(idx: Int): String {
        val dataArray = this.data as Array<*>
        return dataArray[idx] as String
    }

    /**
     * Emits a unique event into security log (if enabled) and returns a predicate to match it.
     */
    private fun makeUniqueEvent(): (SecurityEvent) -> Boolean {
        // Unique alias for a keystore key.
        val alias = "${this::class.qualifiedName}.key_${System.nanoTime()}"

        // Key generation emits TAG_KEY_GENERATED event.
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).build()
        val keyPair = with(KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")) {
            initialize(spec)
            generateKeyPair()
        }
        assertThat(keyPair).isNotNull()

        // Remove key pair from keystore.
        with(KeyStore.getInstance("AndroidKeyStore")) {
            load(null)
            deleteEntry(alias)
        }

        return {
            it.tag == SecurityLog.TAG_KEY_GENERATED && it.stringAt(KEY_ALIAS_INDEX) == alias
        }
    }

    private fun ensureNoAdditionalFullUsers() {
        // TODO(273474964): Move into infra
        try {
            TestApis.users().all().stream()
                .filter { u: UserReference ->
                    (u != TestApis.users().instrumented() &&
                            u != TestApis.users().system() &&
                            u != TestApis.users()
                        .current() && // We can't remove the profile of the instrumented user for
                            // the run on parent profile tests. But the profiles of other users
                            // will be removed when the full-user is removed anyway.
                            !u.isProfile)
                }
                .forEach { obj: UserReference -> obj.remove() }
        } catch (e: NeneException) {
            // Happens when we can't remove a user
            throw NeneException(
                "Error when removing user. Instrumented user is " +
                        TestApis.users().instrumented() + ", current user is " +
                        TestApis.users().current() + ", system user is " +
                        TestApis.users().system(),
                e
            )
        }
    }

    /**
     * Callback that stores the list of events with which it is invoked.
     */
    private class SecurityEventCallback : BlockingCallback<List<SecurityEvent>>(),
            Consumer<List<SecurityEvent>> {
        override fun accept(events: List<SecurityEvent>) {
            callbackTriggered(events)
        }
    }

    /**
     * Callback that flattens all events it gets into a queue and allows querying.
     * TODO(b/328738574): replace with BlockingCallback.
     */
    private class QueueingEventCallback : Consumer<List<SecurityEvent>> {

        private val queue: BlockingQueue<SecurityEvent> = LinkedBlockingQueue()

        override fun accept(items: List<SecurityEvent>) {
            items.forEach{ queue.offer(it) }
        }

        fun poll(
                predicate: (SecurityEvent) -> Boolean,
                timeoutMs: Long = TimeUnit.MINUTES.toMillis(1)
        ): SecurityEvent? {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (SystemClock.elapsedRealtime() < deadline) {
                queue.poll(
                    deadline - SystemClock.elapsedRealtime(),
                    TimeUnit.MILLISECONDS
                )?.let { event ->
                    if (predicate(event)) {
                        return event
                    }
                }
            }

            return null
        }
    }

    companion object {
        @JvmField
        @ClassRule
        val deviceState = DeviceState()

        val context = TestApis.context().instrumentedContext()
        val executor = Executors.newSingleThreadExecutor()
        var localDpm = context.getSystemService(DevicePolicyManager::class.java)!!

        private const val KEY_ALIAS_INDEX = 1
    }

    @JvmField
    @Rule
    val mCheckFlagsRule = RuleChain
            .outerRule(DeviceFlagsValueProvider.createCheckFlagsRule())
            .around(deviceState)
}
