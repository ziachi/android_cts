/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.bedstead.permissions

import android.Manifest.permission
import android.app.AppOpsManager
import android.content.pm.PackageManager
import android.os.Build
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateTester
import com.android.bedstead.harrier.annotations.RequireSdkVersion
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.TestApis.context
import com.android.bedstead.nene.TestApis.permissions
import com.android.bedstead.permissions.annotations.EnsureDoesNotHaveAppOp
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasAppOp
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class PermissionsAnnotationExecutorTest {
    @Test
    @EnsureHasPermission(TEST_PERMISSION_1)
    @RequireSdkVersion(
        min = Build.VERSION_CODES.R,
        reason = "Used permissions not available prior to R"
    )
    fun ensureHasPermission_permissionIsGranted() {
        assertThat(context().instrumentedContext().checkSelfPermission(TEST_PERMISSION_1))
                .isEqualTo(PackageManager.PERMISSION_GRANTED)
    }

    @Test
    @EnsureHasPermission(TEST_PERMISSION_1, TEST_PERMISSION_2)
    fun ensureHasPermission_multiplePermissions_permissionsAreGranted() {
        assertThat(context().instrumentedContext().checkSelfPermission(TEST_PERMISSION_1))
                .isEqualTo(PackageManager.PERMISSION_GRANTED)
        assertThat(context().instrumentedContext().checkSelfPermission(TEST_PERMISSION_2))
                .isEqualTo(PackageManager.PERMISSION_GRANTED)
    }

    @Test
    @EnsureDoesNotHavePermission(TEST_PERMISSION_1)
    fun ensureDoesNotHavePermission_permissionIsDenied() {
        assertThat(context().instrumentedContext().checkSelfPermission(TEST_PERMISSION_1))
                .isEqualTo(PackageManager.PERMISSION_DENIED)
    }

    @Test
    @EnsureDoesNotHavePermission(TEST_PERMISSION_1, TEST_PERMISSION_2)
    fun ensureDoesNotHavePermission_multiplePermissions_permissionsAreDenied() {
        assertThat(context().instrumentedContext().checkSelfPermission(TEST_PERMISSION_1))
                .isEqualTo(PackageManager.PERMISSION_DENIED)
        assertThat(context().instrumentedContext().checkSelfPermission(TEST_PERMISSION_2))
                .isEqualTo(PackageManager.PERMISSION_DENIED)
    }

    @Test
    @EnsureHasPermission(TEST_PERMISSION_1)
    @EnsureDoesNotHavePermission(TEST_PERMISSION_2)
    @RequireSdkVersion(
        min = Build.VERSION_CODES.R,
        reason = "Used permissions not available prior to R"
    )
    fun ensureHasPermissionAndDoesNotHavePermission_permissionsAreCorrect() {
        assertThat(context().instrumentedContext().checkSelfPermission(TEST_PERMISSION_1))
                .isEqualTo(PackageManager.PERMISSION_GRANTED)
        assertThat(context().instrumentedContext().checkSelfPermission(TEST_PERMISSION_2))
                .isEqualTo(PackageManager.PERMISSION_DENIED)
    }

    @EnsureHasAppOp(APP_OP)
    fun ensureHasAppOpAnnotation_appOpIsAllowed() {
        assertThat(permissions().hasAppOpAllowed(APP_OP)).isTrue()
    }

    @Test
    @EnsureDoesNotHaveAppOp(APP_OP)
    fun ensureDoesNotHaveAppOpAnnotation_appOpIsNotAllowed() {
        assertThat(permissions().hasAppOpAllowed(APP_OP)).isFalse()
    }

    @Test
    fun testThrowsSecurityException_usePermissionsAnnotationExecutor_dumpsToLogcat() {
        DeviceStateTester().use { deviceState ->
            TestApis.logcat()
                    .listen {
                        it.contains("SecurityException when using PermissionsAnnotationExecutor")
                    }
                    .use { logcat ->

                        try {
                            deviceState.stepName("testWhichThrowsSecurityException")
                                    .apply(listOf(
                                        UsesAnnotationExecutor(UsesAnnotationExecutor.PERMISSIONS)
                                    )) {
                                        throw SecurityException("Testing")
                                    }
                            Assert.fail("Expected SecurityException")
                        } catch (e: SecurityException) {
                            // We expect the test to fail with a SecurityException
                        }

                        assertThat(logcat.awaitMatch()).isNotNull()
                    }
        }
    }

    companion object {
        private const val APP_OP = AppOpsManager.OPSTR_FINE_LOCATION
        private const val TEST_PERMISSION_1 = permission.INTERACT_ACROSS_PROFILES
        private const val TEST_PERMISSION_2 = permission.INTERACT_ACROSS_USERS_FULL

        @ClassRule
        @Rule
        @JvmField
        val deviceState = DeviceState()
    }
}
