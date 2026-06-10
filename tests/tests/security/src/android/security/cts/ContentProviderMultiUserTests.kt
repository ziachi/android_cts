/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.packageinstaller.contentprovider.cts.multiuser

import android.Manifest
import android.platform.test.annotations.AsbSecurityTest
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser
import com.android.bedstead.multiuser.annotations.RequireNotHeadlessSystemUserMode
import com.android.bedstead.multiuser.annotations.RequireRunOnSecondaryUser
import com.android.bedstead.multiuser.secondaryUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.users.UserReference
import com.android.compatibility.common.util.SystemUtil
import com.android.sts.common.util.StsExtraBusinessLogicTestCase
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@EnsureHasSecondaryUser
@RequireNotHeadlessSystemUserMode(reason = "Need one content provider installed in user with ID 0")
class ContentProviderMultiUserTests: StsExtraBusinessLogicTestCase() {
    val TAG: String = "ContentProviderMultiUserTests"

    val INITIAL_USER_CP_AUTHORITY: String = "android.packageinstaller.multiusercontentprovider"
    val SECONDARY_USER_CP_AUTHORITY: String = "0@android.packageinstaller.multiusercontentprovider"

    val BASE_PATH: String = "/data/local/tmp/cts/contentprovider/"
    val INITIAL_USER_PROVIDER_APK_NAME: String = "CtsMultiuserContentProviderInitial.apk"
    val SECONDARY_USER_PROVIDER_APK_NAME: String = "CtsMultiuserContentProviderOther.apk"

    val INITIAL_USER_PROVIDER_PKG_NAME: String =
        "android.packageinstaller.multiusercontentprovider.initial"
    val SECONDARY_USER_PROVIDER_PKG_NAME: String =
        "android.packageinstaller.multiusercontentprovider.other"

    lateinit var initialUser: UserReference
    lateinit var secondaryUser: UserReference

    val inst = InstrumentationRegistry.getInstrumentation()
    val uiDevice = UiDevice.getInstance(inst)

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }

    @Before
    fun setup() {
        initialUser = TestApis.users().initial()
        secondaryUser = deviceState.secondaryUser()

        installPackage(INITIAL_USER_PROVIDER_APK_NAME, initialUser.id())
        installPackage(SECONDARY_USER_PROVIDER_APK_NAME, secondaryUser.id())
    }

    @After
    fun tearDown() {
        uninstallPkg(INITIAL_USER_PROVIDER_PKG_NAME)
        uninstallPkg(SECONDARY_USER_PROVIDER_PKG_NAME)
    }

    @AsbSecurityTest(cveBugId = [350456241])
    @RequireRunOnInitialUser
    @Test
    fun testAccessFromInitialUser() {
        val ctx = inst.context
        val pm = ctx.packageManager
        assertNotNull(pm.resolveContentProvider(INITIAL_USER_CP_AUTHORITY, 0))
    }

    @AsbSecurityTest(cveBugId = [350456241])
    @RequireRunOnSecondaryUser
    @Test
    fun testAccessFromSecondaryUser() {
        val ctx = inst.context
        val pm = ctx.packageManager
        val pInfo = pm.resolveContentProvider(INITIAL_USER_CP_AUTHORITY, 0)
        // We assertNull here since the content provider with authority INITIAL_USER_CP_AUTHORITY
        // is not installed in secondary user.
        assertNull("Unexpected provider with authority: ${pInfo?.authority} found", pInfo)
    }

    /**
     * Tests whether userId and authority are correctly separated while fetching a content provider.
     * Resolving authority like `0@com.example` should return a CP with authority `com.example`
     * from user 0
     */
    @AsbSecurityTest(cveBugId = [350456241])
    @RequireRunOnSecondaryUser
    @Test
    fun testCrossUserAccess() {
        val ctx = inst.context
        val pm = ctx.packageManager
        SystemUtil.runWithShellPermissionIdentity(
            {
                val pInfo = pm.resolveContentProvider(SECONDARY_USER_CP_AUTHORITY, 0)
                assertNotNull(pInfo)
                assertEquals(INITIAL_USER_PROVIDER_PKG_NAME, pInfo?.packageName)
            },
            Manifest.permission.INTERACT_ACROSS_USERS
        )
    }

    @AsbSecurityTest(cveBugId = [350456241])
    @RequireRunOnSecondaryUser
    @Test
    fun testCrossUserAccessNoPermission() {
        val ctx = inst.context
        val pm = ctx.packageManager
        assertThrows(SecurityException::class.java) {
            pm.resolveContentProvider(SECONDARY_USER_CP_AUTHORITY, 0)
        }
    }

    private fun installPackage(apkName: String, userId: Int) {
        Log.d(TAG, "installPackage(): apkName=$apkName, userId='$userId'")
        assertEquals(
            "Success\n",
            uiDevice.executeShellCommand(
                "pm install --user $userId " + File(BASE_PATH, apkName).canonicalPath
            )
        )
    }

    private fun uninstallPkg(packageName: String) {
        uiDevice.executeShellCommand("pm uninstall $packageName")
    }
}
