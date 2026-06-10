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
package android.devicepolicy.cts

import android.app.admin.DnsEvent
import android.app.admin.NetworkEvent
import android.os.SystemClock
import android.stats.devicepolicy.EventId
import android.util.Log
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwner
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest
import com.android.bedstead.enterprise.dpc
import com.android.bedstead.enterprise.dpcOnly
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType.ADDITIONAL_USER
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.policies.GlobalNetworkLogging
import com.android.bedstead.harrier.policies.NetworkLogging
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder
import com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasNoAdditionalUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.permissions.CommonPermissions
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.time.Duration
import org.junit.Assume
import org.junit.ClassRule
import org.junit.Rule
import org.junit.runner.RunWith
import org.testng.Assert

// These tests currently only cover checking that the appropriate methods are callable. They should
// be replaced with more complete tests once the other network logging tests are ready to be
// migrated to the new infrastructure
@RunWith(BedsteadJUnit4::class)
class NetworkLoggingTest {
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isNetworkLoggingEnabled"])
    @CannotSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @Postsubmit(reason = "new test")
    fun isNetworkLoggingEnabled_notAllowed_throwsException() {
        Assert.assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager()
                .isNetworkLoggingEnabled(deviceState.dpc().componentName())
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isNetworkLoggingEnabled"])
    @CanSetPolicyTest(policy = [NetworkLogging::class])
    @Postsubmit(reason = "new test")
    fun isNetworkLoggingEnabled_networkLoggingIsEnabled_returnsTrue() {
        isNetworkLoggingEnabled_networkLoggingIsEnabled_returnsTrue_impl()
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isNetworkLoggingEnabled"])
    @EnsureHasNoAdditionalUser
    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class])
    @Postsubmit(reason = "new test")
    fun isNetworkLoggingEnabled_networkLoggingIsEnabled_returnsTrue_global() {
        ensureNoUnaffiliatedAdditionalUsers()
        isNetworkLoggingEnabled_networkLoggingIsEnabled_returnsTrue_impl()
    }

    private fun isNetworkLoggingEnabled_networkLoggingIsEnabled_returnsTrue_impl() {
        try {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            Truth.assertThat(
                deviceState.dpc().devicePolicyManager().isNetworkLoggingEnabled(
                    deviceState.dpc().componentName()
                )
            ).isTrue()
        } finally {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isNetworkLoggingEnabled"])
    @CanSetPolicyTest(policy = [NetworkLogging::class])
    @Postsubmit(reason = "new test")
    fun isNetworkLoggingEnabled_networkLoggingIsNotEnabled_returnsFalse() {
        isNetworkLoggingEnabled_networkLoggingIsNotEnabled_returnsFalse_impl()
    }

    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#isNetworkLoggingEnabled"])
    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class])
    @Postsubmit(reason = "new test")
    fun isNetworkLoggingEnabled_networkLoggingIsNotEnabled_returnsFalse_global() {
        ensureNoUnaffiliatedAdditionalUsers()
        isNetworkLoggingEnabled_networkLoggingIsNotEnabled_returnsFalse_impl()
    }

    private fun isNetworkLoggingEnabled_networkLoggingIsNotEnabled_returnsFalse_impl() {
        deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
            deviceState.dpc().componentName(),
            false
        )
        Truth.assertThat(
            deviceState.dpc().devicePolicyManager().isNetworkLoggingEnabled(
                deviceState.dpc().componentName()
            )
        ).isFalse()
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = [NetworkLogging::class])
    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#setNetworkLoggingEnabled",
            "android.app.admin.DeviceAdminReceiver#onNetworkLogsAvailable",
            "android.app.admin.DelegatedAdminReceiver#onNetworkLogsAvailable",
            "android.app.admin.DevicePolicyManager#retrieveNetworkLogs"]
    )
    fun networkLogging_logsContainDnsEvents() {
        networkLogging_logsContainDnsEvents_impl()
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = [GlobalNetworkLogging::class])
    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#setNetworkLoggingEnabled",
            "android.app.admin.DeviceAdminReceiver#onNetworkLogsAvailable",
            "android.app.admin.DelegatedAdminReceiver#onNetworkLogsAvailable",
            "android.app.admin.DevicePolicyManager#retrieveNetworkLogs"]
    )
    fun networkLogging_logsContainDnsEvents_global() {
        ensureNoUnaffiliatedAdditionalUsers()
        networkLogging_logsContainDnsEvents_impl()
    }

    private fun networkLogging_logsContainDnsEvents_impl() {
        try {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            Log.d(TAG, "Enabled logging")

            val hostList = uniqueHostList()

            for (host in hostList) {
                connectToWebsite(host)
            }

            val logs = getLogs()

            Truth.assertThat(logs).isNotEmpty()
            Truth.assertThat(logs.filterIsInstance<DnsEvent>().map{it.hostname})
                .containsAtLeastElementsIn(hostList)
        } finally {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    private fun getLogs(): List<NetworkEvent> {
        // Generate a unique network event to be used as a marker meaning that all the previous
        // events have been fetched
        val markerHost = addUniqueSubdomain("example.com")
        Log.d(TAG, "Marker host: $markerHost")
        connectToWebsite(markerHost)

        var batchToken: Long = -1
        val result = ArrayList<NetworkEvent>()
        val deadline = SystemClock.elapsedRealtime() + Duration.ofMinutes(2).toMillis()

        while (SystemClock.elapsedRealtime() < deadline) {
            Log.d(TAG, "Forcing network logs")
            TestApis.devicePolicy().forceNetworkLogs()
            Log.d(TAG, "Waiting for batch token")
            val nextBatchToken = waitForBatchToken()

            if (nextBatchToken == batchToken) {
                Log.d(TAG, "Got the same token $batchToken, waiting...")
                Thread.sleep(5000)
                continue
            } else {
                batchToken = nextBatchToken
            }

            Log.d(TAG, "Retrieving batch with token: $batchToken")
            val batch = deviceState.dpc().devicePolicyManager().retrieveNetworkLogs(
                deviceState.dpc().componentName(),
                batchToken
            )

            if (batch != null) {
                batch.forEach {
                    if (it is DnsEvent) {
                        Log.d(TAG, "Got DnsEvent for ${it.hostname}")
                    } else {
                        Log.d(TAG, "Got non-dns event")
                    }
                }

                Log.d(TAG, "Got ${batch.size} new events")
                result.addAll(batch)

                // If marker is found, we are done.
                if (batch.any { it is DnsEvent && it.hostname == markerHost }) {
                    Log.d(TAG, "Got all logs")
                    return result
                }
            } else {
                Log.d(TAG, "Null batch")
            }
        }
        throw AssertionError("Timed out waiting for logs")
    }

    private fun waitForBatchToken(): Long {
        return try {
            if (deviceState.dpc().isDelegate) {
                deviceState.dpc().events().delegateNetworkLogsAvailable()
                    .waitForEvent().batchToken()
            } else {
                deviceState.dpc().events().networkLogsAvailable().waitForEvent().batchToken()
            }
        } catch (e: AssertionError) {
            // Collect relevant logs
            throw AssertionError(
                "Error receiving batch token. Relevant logs: " +
                        TestApis.logcat().dump { l: String ->
                    l.contains("NetworkLoggingHandler") ||
                            l.contains("sendDeviceOwnerOrProfileOwnerCommand")
                },
                e
            )
        }
    }

    private fun connectToWebsite(server: String) {
        TestApis.permissions().withPermission(CommonPermissions.INTERNET).use { p ->
            val url = URL("http://$server")
            val urlConnection = url.openConnection() as HttpURLConnection
            try {
                urlConnection.connectTimeout = 2000
                urlConnection.readTimeout = 2000
                Log.d(TAG, "Trying to connect to host: $server")
                urlConnection.responseCode
            } catch (_: UnknownHostException) {
                // Ignored - we only need to make a DNS request, it doesn't have to succeed.
            } finally {
                urlConnection.disconnect()
            }
        }
    }

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setNetworkLoggingEnabled"])
    fun setNetworkLoggingEnabled_notAllowed_throwsException() {
        Assert.assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager()
                .setNetworkLoggingEnabled(deviceState.dpc().componentName(), true)
        }
    }

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveNetworkLogs"])
    fun retrieveNetworkLogs_notAllowed_throwsException() {
        Assert.assertThrows(SecurityException::class.java) {
            deviceState.dpc().devicePolicyManager()
                .retrieveNetworkLogs(
                    deviceState.dpc().componentName(),
                    0 // batch token
                )
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setNetworkLoggingEnabled"])
    fun setNetworkLoggingEnabled_true_logsMetrics() {
        try {
            EnterpriseMetricsRecorder.create().use { metrics ->
                deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                    deviceState.dpc().componentName(),
                    true
                )

                MetricQueryBuilderSubject.assertThat(
                    metrics.query()
                        .whereType().isEqualTo(EventId.SET_NETWORK_LOGGING_ENABLED_VALUE)
                        .whereAdminPackageName().isEqualTo(
                            deviceState.dpc().packageName()
                        )
                        .whereBoolean().isEqualTo(deviceState.dpc().isDelegate)
                        .whereInteger().isEqualTo(1) // Enabled
                ).wasLogged()
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setNetworkLoggingEnabled"])
    fun setNetworkLoggingEnabled_false_logsMetrics() {
        deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
            deviceState.dpc().componentName(),
            true
        )

        EnterpriseMetricsRecorder.create().use { metrics ->
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )

            MetricQueryBuilderSubject.assertThat(
                metrics.query()
                    .whereType().isEqualTo(EventId.SET_NETWORK_LOGGING_ENABLED_VALUE)
                    .whereAdminPackageName().isEqualTo(
                        deviceState.dpc().packageName()
                    )
                    .whereBoolean().isEqualTo(deviceState.dpc().isDelegate)
                    .whereInteger().isEqualTo(0) // Disabled
            ).wasLogged()
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [NetworkLogging::class])
    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#retrieveNetworkLogs",
        "android.app.admin.DeviceAdminReceiver#onNetworkLogsAvailable",
        "android.app.admin.DelegatedAdminReceiver#onNetworkLogsAvailable"]
    )
    fun retrieveNetworkLogs_logsMetrics() {
        retrieveNetworkLogs_logsMetrics_impl()
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class])
    @ApiTest(
        apis = ["android.app.admin.DevicePolicyManager#retrieveNetworkLogs",
            "android.app.admin.DeviceAdminReceiver#onNetworkLogsAvailable",
            "android.app.admin.DelegatedAdminReceiver#onNetworkLogsAvailable"]
    )
    fun retrieveNetworkLogs_logsMetrics_global() {
        ensureNoUnaffiliatedAdditionalUsers()
        retrieveNetworkLogs_logsMetrics_impl()
    }

    private fun retrieveNetworkLogs_logsMetrics_impl() {
        try {
            EnterpriseMetricsRecorder.create().use { metrics ->
                deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                    deviceState.dpc().componentName(),
                    true
                )

                val hostList = uniqueHostList()

                for (host in hostList) {
                    connectToWebsite(host)
                }
                TestApis.devicePolicy().forceNetworkLogs()
                val batchToken = waitForBatchToken()
                deviceState.dpc().devicePolicyManager().retrieveNetworkLogs(
                    deviceState.dpc().componentName(),
                    batchToken
                )

                MetricQueryBuilderSubject.assertThat(
                    metrics.query()
                        .whereType().isEqualTo(EventId.RETRIEVE_NETWORK_LOGS_VALUE)
                        .whereAdminPackageName().isEqualTo(
                            deviceState.dpc().packageName()
                        )
                        .whereBoolean().isEqualTo(deviceState.dpc().isDelegate)
                ).wasLogged()
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class])
    @EnsureHasAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveNetworkLogs"])
    fun retrieveNetworkLogs_unaffiliatedAdditionalUser_throwsException() {
        try {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )

            Assert.assertThrows(SecurityException::class.java) {
                deviceState.dpc().devicePolicyManager().retrieveNetworkLogs(
                    deviceState.dpc().componentName(),
                    0 // batch token
                )
            }
        } finally {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    @CanSetPolicyTest(policy = [GlobalNetworkLogging::class, NetworkLogging::class])
    @EnsureHasAdditionalUser
    @EnsureHasProfileOwner(onUser = ADDITIONAL_USER, affiliationIds = ["affiliated"])
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#retrieveNetworkLogs"])
    fun retrieveNetworkLogs_affiliatedAdditionalUser_doesNotThrowException() {
        // TODO(273474964): Move into infra
        TestApis.users().all().stream()
            .filter {
                (it != TestApis.users().instrumented() &&
                        it != TestApis.users().system() &&
                        it != deviceState.additionalUser() &&
                        it != TestApis.users().current())
            }
            .forEach { it.remove() }

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
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(),
                true
            )
            deviceState.dpc().devicePolicyManager().retrieveNetworkLogs(
                deviceState.dpc().componentName(),
                0 // batch token
            )
        } finally {
            deviceState.dpc().devicePolicyManager().setNetworkLoggingEnabled(
                deviceState.dpc().componentName(),
                false
            )
        }
    }

    private fun ensureNoUnaffiliatedAdditionalUsers() {
        // TODO(273474964): Move into infra

        // We need to skip tests on an unaffiliated user - this should be expressible in
        // annotation so it doesn't generate the incorrect test

        Assume.assumeTrue(
            "Cannot run on an unaffiliate user",
            TestApis.devicePolicy().isAffiliated()
        )

        TestApis.users().all().stream()
            .filter { u: UserReference ->
                (u != TestApis.users().instrumented() &&
                        u != TestApis.users().system() &&
                        u != TestApis.users().current())
            }
            .forEach { obj: UserReference -> {
                Log.d(TAG, "Removing user $obj")
                obj.remove()
            }}
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        var counter: Int = 0

        private fun addUniqueSubdomain(host: String): String =
            "host${SystemClock.elapsedRealtimeNanos()}-${counter++}.$host"

        private fun uniqueHostList(): List<String> = DOMAINS.map { addUniqueSubdomain(it) }

        private val DOMAINS = arrayOf(
            "example.edu",
            "google.co.jp",
            "google.fr",
            "google.com.br",
            "google.com.tr",
            "google.co.uk",
            "google.de"
        )

        const val TAG = "NetworkLoggingTest"
    }
}
