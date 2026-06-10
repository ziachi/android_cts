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

package android.packageinstaller.install.cts

import android.app.Activity.RESULT_CANCELED
import android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED
import android.content.pm.PackageInstaller.STATUS_SUCCESS
import android.platform.test.annotations.AppModeFull
import android.platform.test.rule.ScreenRecordRule.ScreenRecord
import com.android.compatibility.common.util.AppOpsUtils
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class contains tests that install an app using PackageInstaller Session APIs and
 * require user interaction to proceed with the installation.
 */
@AppModeFull(reason = "Instant apps cannot create installer sessions")
@RunWith(TestParameterInjector::class)
@ScreenRecord
class SessionTestWithPia : PackageInstallerTestBase() {

    /**
     * Check that we can install an app via a package-installer session
     */
    @Test
    fun confirmInstallation() {
        val installation = startInstallationViaSession(needFuture = true)!!
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        val result = getInstallSessionResult()
        assertEquals(STATUS_SUCCESS, result.status)
        assertEquals(false, result.preapproval)
        assertInstalled()

        // Even when the install succeeds the install confirm dialog returns 'canceled'
        assertEquals(RESULT_CANCELED, installation.get(GLOBAL_TIMEOUT, TimeUnit.MILLISECONDS))

        assertTrue(AppOpsUtils.allowedOperationLogged(context.packageName, APP_OP_STR))
    }

    /**
     * Install an app via a package-installer session, but then cancel it when the package installer
     * pops open.
     */
    @Test
    fun cancelInstallation() {
        val installation = startInstallationViaSession(needFuture = true)!!
        clickInstallerUIButton(CANCEL_BUTTON_ID)

        // Install should have been aborted
        val result = getInstallSessionResult()
        assertEquals(STATUS_FAILURE_ABORTED, result.status)
        assertEquals(false, result.preapproval)
        assertEquals(RESULT_CANCELED, installation.get(GLOBAL_TIMEOUT, TimeUnit.MILLISECONDS))
        assertNotInstalled()
    }
}
