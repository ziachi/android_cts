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
package com.android.bedstead.testapps

import android.app.AppOpsManager
import android.content.pm.PackageManager
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsureInstrumented
import com.android.bedstead.harrier.annotations.EnsureTestAppDoesNotHavePermission
import com.android.bedstead.harrier.annotations.EnsureTestAppHasAppOp
import com.android.bedstead.harrier.annotations.EnsureTestAppHasPermission
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled
import com.android.bedstead.harrier.annotations.InstrumentationComponent
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser
import com.android.bedstead.multiuser.secondaryUser
import com.android.bedstead.nene.TestApis.packages
import com.android.bedstead.nene.appops.AppOpsMode
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.permissions.CommonPermissions
import com.android.bedstead.testapp.NotFoundException
import com.android.bedstead.testapp.TestApp
import com.android.queryable.annotations.Query
import com.android.queryable.annotations.StringQuery
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class TestAppsAnnotationExecutorTest {

    // We run this test twice to ensure that teardown doesn't change behaviour
    @Test
    fun testApps_testAppsAreAvailableToMultipleTests_1() {
        assertThat(
            deviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_PACKAGE_NAME).get()
        ).isNotNull()
    }

    @Test
    fun testApps_testAppsAreAvailableToMultipleTests_2() {
        assertThat(
            deviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_PACKAGE_NAME).get()
        ).isNotNull()
    }

    // We run this test twice to ensure that teardown doesn't change behaviour
    @Test
    fun testApps_staticTestAppsAreNotReleased_1() {
        assertThrows(NotFoundException::class.java) {
            deviceState.testApps()
                .query()
                .wherePackageName()
                .isEqualTo(TEST_APP_USED_IN_FIELD_NAME)
                .get()
        }
    }

    @Test
    fun testApps_staticTestAppsAreNotReleased_2() {
        assertThrows(NotFoundException::class.java) {
            deviceState.testApps()
                .query()
                .wherePackageName()
                .isEqualTo(TEST_APP_USED_IN_FIELD_NAME)
                .get()
        }
    }

    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @Test
    fun ensureTestAppInstalledAnnotation_testAppIsInstalled() {
        assertThat(packages().find(TEST_APP_PACKAGE_NAME).installedOnUser()).isTrue()
    }

    @EnsureHasSecondaryUser
    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME)),
        onUser = UserType.SECONDARY_USER
    )
    @Test
    fun ensureTestAppInstalledAnnotation_testAppIsInstalledOnCorrectUser() {
        assertThat(
            packages()
                .find(TEST_APP_PACKAGE_NAME)
                .installedOnUser(deviceState.secondaryUser())
        ).isTrue()
    }

    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @Test
    fun testApp_returnsTestApp() {
        assertThat(deviceState.testApp().packageName()).isEqualTo(TEST_APP_PACKAGE_NAME)
    }

    @Test
    fun testApp_noHarrierManagedTestApp_throwsException() {
        deviceState.testApps().any().install().use {
            assertThrows(NeneException::class.java) {
                deviceState.testApp()
            }
        }
    }

    @EnsureTestAppInstalled(
        key = "testApp1",
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @EnsureTestAppInstalled(
        key = "testApp2",
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME2))
    )
    @Test
    fun testApp_withKey_returnsCorrectTestApp() {
        assertThat(
            deviceState.testApp("testApp1").packageName()
        ).isEqualTo(TEST_APP_PACKAGE_NAME)
        assertThat(
            deviceState.testApp("testApp2").packageName()
        ).isEqualTo(TEST_APP_PACKAGE_NAME2)
    }

    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @EnsureTestAppHasPermission(CommonPermissions.READ_CONTACTS)
    @Test
    fun ensureTestAppHasPermissionAnnotation_testAppHasPermission() {
        assertThat(
            deviceState.testApp()
                .context()
                .checkSelfPermission(CommonPermissions.READ_CONTACTS)
        ).isEqualTo(PackageManager.PERMISSION_GRANTED)
    }

    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @EnsureTestAppDoesNotHavePermission(CommonPermissions.READ_CONTACTS)
    @Test
    fun ensureTestAppDoesNotHavePermissionAnnotation_testAppDoesNotHavePermission() {
        assertThat(
            deviceState.testApp()
                .context()
                .checkSelfPermission(CommonPermissions.READ_CONTACTS)
        ).isNotEqualTo(PackageManager.PERMISSION_GRANTED)
    }

    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @EnsureTestAppHasAppOp(AppOpsManager.OPSTR_START_FOREGROUND)
    @Test
    fun ensureTestAppHasAppOpAnnotation_testAppHasAppOp() {
        assertThat(
            deviceState.testApp().testApp().pkg().appOps()[AppOpsManager.OPSTR_START_FOREGROUND]
        ).isEqualTo(AppOpsMode.ALLOWED)
    }

    @Test
    @EnsureTestAppInstalled(
        query = Query(packageName = StringQuery(isEqualTo = TEST_APP_PACKAGE_NAME))
    )
    @EnsureInstrumented(
        [InstrumentationComponent(
            packageName = TEST_APP_PACKAGE_NAME,
            runnerClass = "androidx.test.runner.AndroidJUnitRunner"
        )]
    )
    fun ensureTestAppInstrumented_testAppIsInstrumented() {
        // This test does not assert anything. But will run successfully only when the test app
        // given by [TEST_APP_PACKAGE_NAME] is successfully instrumented.
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        // Expects that this package name matches an actual test app
        private const val TEST_APP_PACKAGE_NAME: String = "com.android.bedstead.testapp.LockTaskApp"
        private const val TEST_APP_PACKAGE_NAME2: String = "com.android.bedstead.testapp.SmsApp"
        private const val TEST_APP_USED_IN_FIELD_NAME: String =
            "com.android.bedstead.testapp.NotEmptyTestApp"

        // This is not used but is depended on by testApps_staticTestAppsAreNotReleased_1 and
        // testApps_staticTestAppsAreNotReleased_2 which test that this testapp isn't released
        private val sTestApp: TestApp = deviceState.testApps().query()
            .wherePackageName().isEqualTo(TEST_APP_USED_IN_FIELD_NAME).get()
    }
}
