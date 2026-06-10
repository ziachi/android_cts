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
package android.app.role.cts

import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.platform.test.annotations.AsbSecurityTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.android.sts.common.util.StsExtraBusinessLogicTestCase
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests that the assistant role is not cleared on force stop. */
@RunWith(AndroidJUnit4::class)
class ForceStopAssistantTest : StsExtraBusinessLogicTestCase() {
    private var assistantRoleHolder: String? = null

    @Before
    fun setUp() {
        assistantRoleHolder = getRoleHolders(RoleManager.ROLE_ASSISTANT).firstOrNull()
        installPackage(APP_APK_PATH);
    }

    @After
    fun tearDown() {
        uninstallPackage(APP_PACKAGE_NAME);
        assistantRoleHolder?.let { addRoleHolder(RoleManager.ROLE_ASSISTANT, it) }
    }

    @AsbSecurityTest(cveBugId = [191743558])
    @SdkSuppress(
        minSdkVersion = Build.VERSION_CODES.S,
        maxSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    )
    @Test
    fun assistantRoleIsNotClearedOnForceStop() {
        assumeTrue(roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT))
        addRoleHolder(RoleManager.ROLE_ASSISTANT, APP_PACKAGE_NAME)
        assertThat(getRoleHolders(RoleManager.ROLE_ASSISTANT)).containsExactly(APP_PACKAGE_NAME)
        SystemUtil.runWithShellPermissionIdentity {
            activityManager.forceStopPackage(APP_PACKAGE_NAME)
        }
        // The clear role holder call is async and there's no way to detect it's not happening, so
        // we have to always wait for a sufficient amount of time.
        Thread.sleep(10_000)
        assertThat(getRoleHolders(RoleManager.ROLE_ASSISTANT)).containsExactly(APP_PACKAGE_NAME)
    }

    private fun installPackage(apkPath: String) {
        SystemUtil.runShellCommandOrThrow("pm install -r --user ${user.identifier} $apkPath")
    }

    private fun uninstallPackage(packageName: String) {
        SystemUtil.runShellCommand("pm uninstall --user ${user.identifier} $packageName")
    }

    private fun getRoleHolders(roleName: String): List<String> {
        return SystemUtil.callWithShellPermissionIdentity { roleManager.getRoleHolders(roleName) }
    }

    private fun addRoleHolder(roleName: String, packageName: String) {
        val executor = context.mainExecutor
        val future = CallbackFuture()
        SystemUtil.runWithShellPermissionIdentity {
            roleManager.addRoleHolderAsUser(roleName, packageName, 0, user, executor, future)
        }
        assertThat(future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue()
    }

    private class CallbackFuture : CompletableFuture<Boolean>(), Consumer<Boolean> {
        override fun accept(successful: Boolean) {
            complete(successful)
        }
    }

    companion object {
        private const val TIMEOUT_MILLIS = 15 * 1000L
        private const val APP_PACKAGE_NAME = "android.security.cts.assistantresettestapp"
        private const val APP_APK_PATH =
            "/data/local/tmp/cts/security/ForceStopAssistantTestApp.apk"

        private val user = Process.myUserHandle()
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val activityManager = context.getSystemService(ActivityManager::class.java)
        private val roleManager = context.getSystemService(RoleManager::class.java)
    }
}
