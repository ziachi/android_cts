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
import android.content.Context
import android.content.pm.Flags
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.os.Process
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
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
import com.android.bedstead.permissions.PermissionContext
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.bedstead.testapp.TestApp
import com.android.bedstead.testapp.TestAppInstance
import com.android.bedstead.testapp.TestAppPermissionContext
import com.android.bedstead.testapp.TestAppProvider
import com.android.compatibility.common.util.SystemUtil
import java.io.File
import kotlin.test.assertNotEquals
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@EnsureHasSecondaryUser
@RequireNotHeadlessSystemUserMode(reason = "Need one content provider installed in user with ID 0")
class ContentProviderMultiUserTests {
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

    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val uiDevice = UiDevice.getInstance(instrumentation)

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        @JvmStatic
        val testAppProvider = TestAppProvider()

        val testApp: TestApp = testAppProvider.query()
            .wherePermissions().contains(Manifest.permission.INTERACT_ACROSS_USERS)
            .get()
    }

    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

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

    @RequireRunOnInitialUser
    @Test
    fun testAccessFromInitialUser() {
        val ctx = instrumentation.context
        val pm = ctx.packageManager
        assertNotNull(pm.resolveContentProvider(INITIAL_USER_CP_AUTHORITY, 0))
    }

    @RequireRunOnSecondaryUser
    @Test
    fun testAccessFromSecondaryUser() {
        val ctx = instrumentation.context
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
    @RequireRunOnSecondaryUser
    @Test
    fun testCrossUserAccess() {
        val ctx = instrumentation.context
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

    @RequireRunOnSecondaryUser
    @Test
    fun testCrossUserAccessNoPermission() {
        val ctx = instrumentation.context
        val pm = ctx.packageManager
        assertThrows(SecurityException::class.java) {
            pm.resolveContentProvider(SECONDARY_USER_CP_AUTHORITY, 0)
        }
    }

    /**
     * Tests whether calling resolveContentProviderForUid with the required permission does not
     * throw an exception. We don't care about getting the correct result here.
     */
    @RequireRunOnInitialUser
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_UID_BASED_PROVIDER_LOOKUP)
    fun testResolvingForUid() {
        val ctx = instrumentation.context
        val pm = ctx.packageManager

        TestApis.permissions().withPermission(Manifest.permission.RESOLVE_COMPONENT_FOR_UID).use {
            assertNull(
                pm.resolveContentProviderForUid(
                    INITIAL_USER_CP_AUTHORITY,
                    PackageManager.ComponentInfoFlags.of(0),
                    -1
                )
            )
        }

        TestApis.permissions().withoutPermission(Manifest.permission.RESOLVE_COMPONENT_FOR_UID)
            .use {
                assertThrows(SecurityException::class.java) {
                    pm.resolveContentProviderForUid(
                        INITIAL_USER_CP_AUTHORITY,
                        PackageManager.ComponentInfoFlags.of(0),
                        -1
                    )
                }
            }
    }

    /**
     * Test whether calling resolveContentProviderForUid with both the test and the testApp having
     * cross-user permission resolves to the correct ContentProvider.
     */
    @EnsureHasPermission(Manifest.permission.RESOLVE_COMPONENT_FOR_UID)
    @RequireRunOnInitialUser
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_UID_BASED_PROVIDER_LOOKUP)
    fun testResolveForUid_CrossUserPermissionHolders_All() {
        val ctx = instrumentation.context

        testApp.install(secondaryUser).use { testAppInst ->
            val pInfo: ProviderInfo? = resolveContentProviderForUid(
                context = ctx,
                authority = INITIAL_USER_CP_AUTHORITY,
                filterUid = getAppUid(ctx, testAppInst),
                testAppPermissionContext = testAppPermissionContextFactory(true, testAppInst),
                testPermissionContext = testPermissionContextFactory(true)
            )
            assertNotNull(pInfo)
            assertEquals(INITIAL_USER_PROVIDER_PKG_NAME, pInfo?.packageName)
        }
    }

    /**
     * Test whether calling resolveContentProviderForUid with only the test having cross-user
     * permission throws a SecurityException.
     */
    @EnsureHasPermission(Manifest.permission.RESOLVE_COMPONENT_FOR_UID)
    @RequireRunOnInitialUser
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_UID_BASED_PROVIDER_LOOKUP)
    fun testResolveForUid_CrossUserPermissionHolder_TestOnly() {
        val ctx = instrumentation.context

        testApp.install(secondaryUser).use { testAppInst ->
            assertThrows(SecurityException::class.java) {
                resolveContentProviderForUid(
                    context = ctx,
                    authority = INITIAL_USER_CP_AUTHORITY,
                    filterUid = getAppUid(ctx, testAppInst),
                    testAppPermissionContext = testAppPermissionContextFactory(false, testAppInst),
                    testPermissionContext = testPermissionContextFactory(true)
                )
            }
        }
    }

    /**
     * Test whether calling resolveContentProviderForUid with only the testApp having cross-user
     * permission throws a SecurityException.
     */
    @EnsureHasPermission(Manifest.permission.RESOLVE_COMPONENT_FOR_UID)
    @RequireRunOnInitialUser
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_UID_BASED_PROVIDER_LOOKUP)
    fun testResolveForUid_CrossUserPermissionHolder_TestAppOnly() {
        val ctx = instrumentation.context

        testApp.install(secondaryUser).use { testAppInst ->
            assertThrows(SecurityException::class.java) {
                resolveContentProviderForUid(
                    context = ctx,
                    authority = INITIAL_USER_CP_AUTHORITY,
                    filterUid = getAppUid(ctx, testAppInst),
                    testAppPermissionContext = testAppPermissionContextFactory(true, testAppInst),
                    testPermissionContext = testPermissionContextFactory(false)
                )
            }
        }
    }

    /**
     * Test whether calling resolveContentProviderForUid with neither the test nor the testApp
     * having cross-user permission throws a SecurityException.
     */
    @EnsureHasPermission(Manifest.permission.RESOLVE_COMPONENT_FOR_UID)
    @RequireRunOnInitialUser
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_UID_BASED_PROVIDER_LOOKUP)
    fun testResolveForUid_CrossUserPermissionHolder_None() {
        val ctx = instrumentation.context

        testApp.install(secondaryUser).use { testAppInst ->
            assertThrows(SecurityException::class.java) {
                resolveContentProviderForUid(
                    context = ctx,
                    authority = INITIAL_USER_CP_AUTHORITY,
                    filterUid = getAppUid(ctx, testAppInst),
                    testAppPermissionContext = testAppPermissionContextFactory(false, testAppInst),
                    testPermissionContext = testPermissionContextFactory(false)
                )
            }
        }
    }

    private fun getAppUid(ctx: Context, testAppInst: TestAppInstance): Int {
        var filterUid = Process.INVALID_UID
        TestApis.permissions().withPermission(Manifest.permission.INTERACT_ACROSS_USERS)
            .use {
                try {
                    filterUid = ctx.packageManager.getPackageUidAsUser(
                        testAppInst.packageName(),
                        PackageManager.PackageInfoFlags.of(0),
                        testAppInst.user().id()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error: ", e)
                    fail("Could not get UID for testApp ${testAppInst.packageName()}")
                }
            }
        assertNotEquals(Process.INVALID_UID, filterUid)
        return filterUid
    }

    private fun resolveContentProviderForUid(
        context: Context?,
        authority: String,
        filterUid: Int,
        testAppPermissionContext: TestAppPermissionContext?,
        testPermissionContext: PermissionContext,
    ): ProviderInfo? {
        return testAppPermissionContext.use {
            testPermissionContext.use {
                context?.packageManager?.resolveContentProviderForUid(
                    authority,
                    PackageManager.ComponentInfoFlags.of(0),
                    filterUid
                )
            }
        }
    }

    private fun testAppPermissionContextFactory(
        grantCrossUserPerm: Boolean,
        testAppInst: TestAppInstance,
    ): TestAppPermissionContext {
        return if (grantCrossUserPerm) {
            testAppInst.permissions().withPermission(Manifest.permission.INTERACT_ACROSS_USERS)
        } else {
            testAppInst.permissions().withoutPermission(Manifest.permission.INTERACT_ACROSS_USERS)
        }
    }

    private fun testPermissionContextFactory(grantCrossUserPerm: Boolean): PermissionContext {
        return if (grantCrossUserPerm) {
            TestApis.permissions().withPermission(Manifest.permission.INTERACT_ACROSS_USERS)
        } else {
            TestApis.permissions().withoutPermission(Manifest.permission.INTERACT_ACROSS_USERS)
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
