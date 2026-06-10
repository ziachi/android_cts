/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.app.appops.cts

import android.app.AppOpsManager
import android.app.AppOpsManager.OPSTR_READ_CONTACTS
import android.app.AppOpsManager.OPSTR_RESERVED_FOR_TESTING
import android.app.AppOpsManager.OP_FLAGS_ALL
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_RESULT_RECEIVER
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import android.os.RemoteCallback
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.AsbSecurityTest
import androidx.test.filters.FlakyTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.lang.Thread.sleep
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

private const val APK_PATH = "/data/local/tmp/cts/appops/"

private const val APP_PKG = "android.app.appops.cts.apptoblame"

private const val ATTRIBUTION_1 = "attribution1"
private const val ATTRIBUTION_2 = "attribution2"
private const val ATTRIBUTION_3 = "attribution3"
private const val ATTRIBUTION_4 = "attribution4"
private const val ATTRIBUTION_5 = "attribution5"
private const val ATTRIBUTION_6 = "attribution6"
private const val ATTRIBUTION_7 = "attribution7"

@AppModeFull(reason = "Test relies on seeing other apps. Instant apps can't see other apps")
class AttributionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val uiAutomation = instrumentation.getUiAutomation()
    private val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
    private val appUid by lazy { context.packageManager.getPackageUid(APP_PKG, 0) }

    private fun installApk(apk: String) {
        val result = runCommand(
            "pm install --user ${context.userId} -r --force-queryable $APK_PATH$apk")
        assertThat(result.trim()).isEqualTo("Success")
    }

    @Before
    fun resetTestApp() {
        runCommand("pm uninstall --user ${context.userId} $APP_PKG")
        installApk("CtsAppToBlame1.apk")
        // We need to wait for the package installation broadcast to reach AppOpsService to update
        // uidState. Can remove this once b/376345874 is fixed.
        sleep(1000)
    }

    private fun noteForAttribution(attribution: String) {
        // Make sure note times as distinct
        sleep(1)

        runWithShellPermissionIdentity {
            appOpsManager.noteOp(OPSTR_RESERVED_FOR_TESTING, appUid, APP_PKG, attribution, null)
        }
    }

    @Test
    @FlakyTest
    fun manifestReceiverTagging() {
        val PKG = "android.app.appops.cts.appwithreceiverattribution"

        installApk("CtsAppWithReceiverAttribution.apk")
        val uid = context.packageManager.getPackageUid(PKG, 0)

        val intent = Intent("ACTION_TEST")
        intent.setComponent(ComponentName.createRelative(PKG, ".TestReceiver"))
        intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND)

        runWithShellPermissionIdentity {
            uiAutomation.grantRuntimePermission(PKG, android.Manifest.permission.READ_CONTACTS)
            appOpsManager.noteOp(OPSTR_READ_CONTACTS, uid, PKG, ATTRIBUTION_1, null)
            appOpsManager.noteOp(OPSTR_READ_CONTACTS, uid, PKG, ATTRIBUTION_2, null)
            appOpsManager.noteOp(OPSTR_READ_CONTACTS, uid, PKG, ATTRIBUTION_3, null)
        }

        sleep(50)
        val before = getOpEntry(uid, PKG, OPSTR_READ_CONTACTS)!!
        context.sendBroadcast(intent, android.Manifest.permission.READ_CONTACTS)
        sleep(50)

        eventually {
            // 1 and 2 should be attributed for the broadcast, 3 should not.
            val after = getOpEntry(uid, PKG, OPSTR_READ_CONTACTS)!!
            assertThat(after.attributedOpEntries[ATTRIBUTION_1]!!
                    .getLastAccessTime(OP_FLAGS_ALL))
                    .isNotEqualTo(before.attributedOpEntries[ATTRIBUTION_1]!!
                            .getLastAccessTime(OP_FLAGS_ALL))
            assertThat(after.attributedOpEntries[ATTRIBUTION_2]!!
                    .getLastAccessTime(OP_FLAGS_ALL))
                    .isNotEqualTo(before.attributedOpEntries[ATTRIBUTION_2]!!
                            .getLastAccessTime(OP_FLAGS_ALL))
            assertThat(after.attributedOpEntries[ATTRIBUTION_3]!!
                    .getLastAccessTime(OP_FLAGS_ALL))
                    .isEqualTo(before.attributedOpEntries[ATTRIBUTION_3]!!
                            .getLastAccessTime(OP_FLAGS_ALL))
        }
        runCommand("pm uninstall --user ${context.userId} $PKG")
    }

    @Test
    fun inheritNotedAppOpsOnUpgrade() {
        noteForAttribution(ATTRIBUTION_1)
        noteForAttribution(ATTRIBUTION_2)
        noteForAttribution(ATTRIBUTION_3)
        noteForAttribution(ATTRIBUTION_4)
        noteForAttribution(ATTRIBUTION_5)

        val beforeUpdate = getOpEntry(appUid, APP_PKG, OPSTR_RESERVED_FOR_TESTING)!!
        installApk("CtsAppToBlame2.apk")

        eventually {
            val afterUpdate = getOpEntry(appUid, APP_PKG, OPSTR_RESERVED_FOR_TESTING)!!

            // Attribution 1 is unchanged
            assertThat(afterUpdate.attributedOpEntries[ATTRIBUTION_1]!!
                    .getLastAccessTime(OP_FLAGS_ALL))
                    .isEqualTo(beforeUpdate.attributedOpEntries[ATTRIBUTION_1]!!
                            .getLastAccessTime(OP_FLAGS_ALL))

            // Attribution 3 disappeared (i.e. was added into "null" attribution)
            assertThat(afterUpdate.attributedOpEntries[null]!!.getLastAccessTime(OP_FLAGS_ALL))
                    .isEqualTo(beforeUpdate.attributedOpEntries[ATTRIBUTION_3]!!
                            .getLastAccessTime(OP_FLAGS_ALL))

            // Attribution 6 inherits from attribution 2
            assertThat(afterUpdate.attributedOpEntries[ATTRIBUTION_6]!!
                    .getLastAccessTime(OP_FLAGS_ALL))
                    .isEqualTo(beforeUpdate.attributedOpEntries[ATTRIBUTION_2]!!
                            .getLastAccessTime(OP_FLAGS_ALL))

            // Attribution 7 inherits from attribution 4 and 5. 5 was noted after 4, hence 4 is
            // removed
            assertThat(afterUpdate.attributedOpEntries[ATTRIBUTION_7]!!
                    .getLastAccessTime(OP_FLAGS_ALL))
                    .isEqualTo(beforeUpdate.attributedOpEntries[ATTRIBUTION_5]!!
                            .getLastAccessTime(OP_FLAGS_ALL))
        }
    }

    @Test
    @Ignore
    fun canUseUndeclaredAttributionTagButTreatedAsNull() {
        noteForAttribution("invalid attribution tag")
        val opEntry = getOpEntry(appUid, APP_PKG, OPSTR_RESERVED_FOR_TESTING)!!
        assertThat(opEntry.attributedOpEntries["invalid attribution tag"]).isNull()
    }

    @Test
    fun canUseUndeclaredAttributionTagIfChangeForBlameeIsDisabled() {
        noteForAttribution("invalid attribution tag")
    }

    @Test(expected = AssertionError::class)
    fun cannotInheritFromSelf() {
        installApk("AppWithAttributionInheritingFromSelf.apk")
    }

    @Test(expected = AssertionError::class)
    fun noDuplicateAttributions() {
        installApk("AppWithDuplicateAttribution.apk")
    }

    @Test(expected = AssertionError::class)
    fun cannotInheritFromExisting() {
        installApk("AppWithAttributionInheritingFromExisting.apk")
    }

    @Test(expected = AssertionError::class)
    fun cannotInheritFromSameAsOther() {
        installApk("AppWithAttributionInheritingFromSameAsOther.apk")
    }

    @Test(expected = AssertionError::class)
    fun cannotUseVeryLongAttributionTags() {
        installApk("AppWithLongAttributionTag.apk")
    }

    @AsbSecurityTest(cveBugId = [304983146])
    @Test(expected = AssertionError::class)
    fun cannotUseTooManyAttributions() {
        installApk("AppWithTooManyAttributions.apk")
    }

    @AsbSecurityTest(cveBugId = [304983146])
    @Test
    fun noteProxyOpDoesNotPersistInvalidAttribution() {
        noteProxyOpForAttribution("invalid", ATTRIBUTION_1)
        assertThat(getPersistedAttribution(ATTRIBUTION_1))
                .isNull()
    }

    @AsbSecurityTest(cveBugId = [304983146])
    @Test
    fun noteProxyOpPersistsValidAttribution() {
        noteProxyOpForAttribution(ATTRIBUTION_2, ATTRIBUTION_2)
        assertThat(getPersistedAttribution(ATTRIBUTION_2))
                .isEqualTo(ATTRIBUTION_2)
    }

    @AsbSecurityTest(cveBugId = [304983146])
    @Test
    fun startProxyOpDoesNotPersistInvalidAttribution() {
        startProxyOpForAttribution("invalid", ATTRIBUTION_1)
        assertThat(getPersistedAttribution(ATTRIBUTION_1))
                .isNull()
    }

    @AsbSecurityTest(cveBugId = [304983146])
    @Test
    fun startProxyOpPersistsValidAttribution() {
        startProxyOpForAttribution(ATTRIBUTION_2, ATTRIBUTION_2)
        assertThat(getPersistedAttribution(ATTRIBUTION_2))
                .isEqualTo(ATTRIBUTION_2)
    }

    @AsbSecurityTest(cveBugId = [372678095])
    @Test
    fun noteOpWithTooManyAttributionTags() {
        val packageName = "android.app.appops.cts.appthatexploitsattributiontags"

        installApk("AppThatExploitsAttributionTags.apk")
        sleep(1000)

        val future = CompletableFuture<Boolean>()
        val callback = RemoteCallback { result: Bundle? -> future.complete(true) }
        val intent = Intent().setComponent(
            ComponentName(
                packageName,
                "$packageName.AppOpNoteActivity"
            )
        )
            .putExtra(EXTRA_RESULT_RECEIVER, callback)
            .setFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)

        context.startActivity(intent)

        assertThat(future.get(20, TimeUnit.SECONDS)).isTrue()

        val uid = context.packageManager.getPackageUid(packageName, 0)
        try {
            runWithShellPermissionIdentity {
                val packageOps = appOpsManager.getOpsForPackage(
                    uid, packageName,
                    AppOpsManager.OPSTR_COARSE_LOCATION,
                    AppOpsManager.OPSTR_FINE_LOCATION,
                    AppOpsManager.OPSTR_GPS,
                    AppOpsManager.OPSTR_VIBRATE,
                    AppOpsManager.OPSTR_READ_CONTACTS,
                    AppOpsManager.OPSTR_WRITE_CONTACTS,
                    AppOpsManager.OPSTR_READ_CALL_LOG,
                    AppOpsManager.OPSTR_WRITE_CALL_LOG,
                    AppOpsManager.OPSTR_READ_CALENDAR,
                )

                assertThat(packageOps).isNotEmpty()
            }
        } finally {
            runCommand("pm uninstall --user ${context.userId} $packageName")
        }
    }

    private fun noteProxyOpForAttribution(attributionForContextCreation: String, attributionForNoteOp: String) {
        val ctx = context.createAttributionContext(attributionForContextCreation)
        val appOpsManager = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        appOpsManager.noteProxyOp(OPSTR_RESERVED_FOR_TESTING, APP_PKG, appUid, attributionForNoteOp, "message")
    }

    private fun startProxyOpForAttribution(attributionForContextCreation: String, attributionForNoteOp: String) {
        val ctx = context.createAttributionContext(attributionForContextCreation)
        val appOpsManager = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        appOpsManager.startProxyOp(OPSTR_RESERVED_FOR_TESTING, appUid, APP_PKG, attributionForNoteOp,"message")
    }

    private fun getPersistedAttribution(attribution: String) : String? {
        val entry = getOpEntry(appUid, APP_PKG, OPSTR_RESERVED_FOR_TESTING) as AppOpsManager.OpEntry
        return entry.attributedOpEntries[attribution]?.getLastProxyInfo(OP_FLAGS_ALL)?.attributionTag
    }
}
