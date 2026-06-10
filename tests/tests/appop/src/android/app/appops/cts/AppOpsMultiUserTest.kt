package android.app.appops.cts

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.permission.flags.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val APK_PATH = "/data/local/tmp/cts/appops/"
private const val APK = "AppInstalledOnMultipleUsers.apk"
private const val SHARED_UID_APK1 = "SharedUidTestApp1.apk"
private const val SHARED_UID_APK2 = "SharedUidTestApp2.apk"

private const val PKG = "android.app.appops.cts.apponmultipleusers"
private const val SHARED_UID_PKG1 = "android.app.appops.cts.shareduid1"
private const val SHARED_UID_PKG2 = "android.app.appops.cts.shareduid2"

private const val APPOPS_UPDATE_WAIT_PERIOD = 2000L

@AppModeFull
class AppOpsMultiUserTest {

    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager: PackageManager = context.packageManager
    private val userManager: UserManager = context.getSystemService(UserManager::class.java)!!
    private val appOpsManager: AppOpsManager = context.getSystemService(AppOpsManager::class.java)!!
    private val activityManager: ActivityManager =
        context.getSystemService(ActivityManager::class.java)!!

    private val preExistingUsers: MutableList<UserInfo> = mutableListOf()
    private val newUsers: MutableList<UserInfo> = mutableListOf()

    private fun installApkForAllUsers(apk: String) {
        val result = runCommand("pm install --user all -r --force-queryable $APK_PATH$apk")
        Assert.assertEquals("Success", result.trim())
    }

    private fun uninstallPackageForAllUsers(pkg: String) {
        runCommand("pm uninstall --user all $pkg")
    }

    @Before
    fun setUp() {
        Assume.assumeTrue(UserManager.supportsMultipleUsers())
        SystemUtil.runWithShellPermissionIdentity {
            preExistingUsers.addAll(userManager.users)
        }
        SystemUtil.runShellCommandOrThrow("pm create-user test-user")
        SystemUtil.runWithShellPermissionIdentity {
            newUsers.addAll(userManager.users)
            newUsers.removeIf { newUser ->
                preExistingUsers.any { filterUser ->
                    newUser.id == filterUser.id
                }
            }
        }
        // Some users aren't in running state in the secondary_user option test.
        // The AppOpsService doesn't receive ACTION_PACKAGE_ADDED for that user.
        // TODO(b/376345874) this workaround can be removed once PackageMonitor is used.
        SystemUtil.runWithShellPermissionIdentity {
            preExistingUsers.removeAll { userInfo ->
                !activityManager.isUserRunning(userInfo.id)
            }
        }
    }

    @After
    fun tearDown() {
        newUsers.forEach {
            SystemUtil.runShellCommandOrThrow("pm remove-user ${it.id}")
        }

        uninstallPackageForAllUsers(PKG)
        uninstallPackageForAllUsers(SHARED_UID_PKG1)
        uninstallPackageForAllUsers(SHARED_UID_PKG2)

        SystemUtil.runShellCommandOrThrow("cmd appops reset --user ${context.userId}")
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RUNTIME_PERMISSION_APPOPS_MAPPING_ENABLED)
    fun testUninstallDoesntAffectOtherUsers() {
        installApkForAllUsers(APK)

        runWithShellPermissionIdentity {
            preExistingUsers.forEach {
                val uid = packageManager.getPackageUidAsUser(PKG, it.id)
                eventually {
                    appOpsManager.setMode(
                        AppOpsManager.OPSTR_RESERVED_FOR_TESTING,
                        uid,
                        PKG,
                        AppOpsManager.MODE_IGNORED
                    )

                    val mode = appOpsManager.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_RESERVED_FOR_TESTING,
                        uid,
                        PKG
                    )
                    Assert.assertEquals(AppOpsManager.MODE_IGNORED, mode)
                }
            }
        }

        // Uninstall the package from the test users and ensure the other users aren't affected
        newUsers.forEach {
            SystemUtil.runShellCommandOrThrow("pm uninstall --user ${it.id} $PKG")
        }

        runWithShellPermissionIdentity {
            monitorAssertionRemains {
                preExistingUsers.forEach {
                    val uid = packageManager.getPackageUidAsUser(PKG, it.id)
                    val mode = appOpsManager.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_RESERVED_FOR_TESTING,
                        uid,
                        PKG
                    )
                    Assert.assertEquals(AppOpsManager.MODE_IGNORED, mode)
                }
            }
        }
    }

    /**
     * Tests unintuitive behavior with the UID_REMOVED broadcast. If multiple sharing UID packages
     * are installed across multiple users then when one is uninstalled for one user then
     * the UID_REMOVED broadcast is dispatched for its UID, this caused AppOpsService to drop
     * state when the whole UID hadn't been fully removed.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DONT_REMOVE_EXISTING_UID_STATES)
    fun testSharedUidWithMultipleUsers() {
        installApkForAllUsers(SHARED_UID_APK1)
        installApkForAllUsers(SHARED_UID_APK2)

        val user = Process.myUserHandle()
        val testUid = packageManager.getPackageUid(SHARED_UID_PKG1, 0)

        eventually {
            // Current implementation of AppOpsService may receive package added broadcast late
            // TODO(b/376345874): Move to PackageMonitor and remove this eventually block
            runWithShellPermissionIdentity {
                val mode = appOpsManager.noteOpNoThrow(
                    AppOpsManager.OPSTR_RESERVED_FOR_TESTING,
                    testUid,
                    SHARED_UID_PKG1
                )
                Assert.assertEquals(AppOpsManager.MODE_ALLOWED, mode)
            }
        }

        SystemUtil.runShellCommandOrThrow("pm uninstall --user ${user.identifier} $SHARED_UID_PKG2")
        SystemUtil.waitForBroadcasts()

        runWithShellPermissionIdentity {
            monitorAssertionRemains {
                val mode = appOpsManager.noteOpNoThrow(
                    AppOpsManager.OPSTR_RESERVED_FOR_TESTING,
                    testUid,
                    SHARED_UID_PKG1
                )
                // Failed case saw MODE_ERRORED since state was dropped after uninstall
                Assert.assertEquals(AppOpsManager.MODE_ALLOWED, mode)
            }
        }
    }

    /**
     * Check the assertion for some time in the case the fail condition is delayed. This is used
     * to check that the condition doesn't hold for some time.
     */
    private fun monitorAssertionRemains(assertion: () -> Unit) {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < APPOPS_UPDATE_WAIT_PERIOD) {
            assertion.invoke()
        }
    }
}
