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

package android.packageinstaller.install.cts

import android.os.UserManager
import android.text.TextUtils
import com.android.compatibility.common.util.SystemUtil
import org.junit.After
import org.junit.Assume
import org.junit.Before

open class UpdateOwnershipEnforcementTestBase : PackageInstallerTestBase() {

    companion object {
        const val TEST_INSTALLER_APK_NAME = "CtsEmptyInstallerApp.apk"
        const val TEST_INSTALLER_APK_PACKAGE_NAME = "android.packageinstaller.emptyinstaller.cts"
    }

    private var isUpdateOwnershipEnforcementAvailable: String? = null

    private fun assumeRunOnPrimaryUser(): String {
        val um = instrumentation.targetContext.getSystemService(UserManager::class.java)
        val userType = um.userType
        android.util.Log.d(TAG, "userType = $userType")
        Assume.assumeTrue(
            "Don't support to run the test cases in a profile.",
            TextUtils.equals(userType, UserManager.USER_TYPE_FULL_SYSTEM)
        )
        return userType
    }

    /**
     * Make sure the feature flag of update ownership enforcement is available.
     */
    @Before
    fun setUpdateOwnershipEnforcementAvailable() {
        SystemUtil.callWithShellPermissionIdentity { assumeRunOnPrimaryUser() }
        isUpdateOwnershipEnforcementAvailable =
                getDeviceProperty(PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE)
        setDeviceProperty(PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE, "true")
    }

    /**
     * Install the test installer package.
     */
    @Before
    fun installTestInstaller() {
        installPackage(TEST_INSTALLER_APK_NAME)
    }

    /**
     * Restore the status of update ownership enforcement.
     */
    @After
    fun recoverUpdateOwnershipEnforcement() {
        setDeviceProperty(
                PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE,
                isUpdateOwnershipEnforcementAvailable
        )
    }

    /**
     * Uninstall the test installer package.
     */
    @After
    fun uninstallTestInstaller() {
        uninstallTestPackage()
        uninstallPackage(TEST_INSTALLER_APK_PACKAGE_NAME)
    }

    /**
     * Clean up all sessions created by this test.
     */
    @After
    fun cleanUpSessions() {
        pi.mySessions.forEach {
            try {
                pi.abandonSession(it.sessionId)
            } catch (ignored: Exception) {
            }
        }
    }
}
