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
package com.android.bedstead.enterprise

import com.android.bedstead.enterprise.annotations.EnsureDoesNotHaveUserRestriction
import com.android.bedstead.enterprise.annotations.EnsureHasDelegate
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceAdmin
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasDevicePolicyManagerRoleHolder
import com.android.bedstead.enterprise.annotations.EnsureHasNoDelegate
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoProfileOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoTestDeviceAdmin
import com.android.bedstead.enterprise.annotations.EnsureHasNoWorkProfile
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwner
import com.android.bedstead.enterprise.annotations.EnsureHasUserRestriction
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.annotations.EnsureTestAppInstalledAsPrimaryDPC
import com.android.bedstead.enterprise.annotations.MostImportantCoexistenceTest
import com.android.bedstead.enterprise.annotations.MostRestrictiveCoexistenceTest
import com.android.bedstead.enterprise.annotations.RequireHasPolicyExemptApps
import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile
import com.android.bedstead.harrier.AnnotationExecutor
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.multiuser.UsersComponent
import com.android.bedstead.testapp.TestAppProvider
import com.android.bedstead.testapps.TestAppsComponent

/**
 * [AnnotationExecutor] for enterprise annotations
 */
@Suppress("unused")
class EnterpriseAnnotationExecutor(locator: BedsteadServiceLocator) : AnnotationExecutor {

    private val enterpriseComponent: EnterpriseComponent by locator
    private val deviceOwnerComponent: DeviceOwnerComponent by locator
    private val profileOwnersComponent: ProfileOwnersComponent by locator
    private val deviceAdminComponent: DeviceAdminComponent by locator
    private val testAppsComponent: TestAppsComponent by locator
    private val usersComponent: UsersComponent by locator
    private val userRestrictions: UserRestrictionsComponent by locator

    override fun applyAnnotation(annotation: Annotation): Unit = annotation.run {
        when (this) {
            is EnsureHasDelegate -> enterpriseComponent.ensureHasDelegate(this)
            is EnsureHasDevicePolicyManagerRoleHolder ->
                enterpriseComponent.ensureHasDevicePolicyManagerRoleHolder(onUser, isPrimary)

            is EnsureHasDeviceOwner ->
                deviceOwnerComponent.ensureHasDeviceOwner(
                    failureMode,
                    isPrimary,
                    headlessDeviceOwnerType,
                    affiliationIds.toHashSet(),
                    type,
                    key,
                    TestAppProvider().query(dpc)
                )

            is EnsureHasNoDelegate -> enterpriseComponent.ensureHasNoDelegate(admin)
            is EnsureHasNoDeviceOwner -> deviceOwnerComponent.ensureHasNoDeviceOwner()
            is EnsureHasNoProfileOwner -> profileOwnersComponent.ensureHasNoProfileOwner(onUser)
            is EnsureHasProfileOwner -> profileOwnersComponent.ensureHasProfileOwner(this)
            is EnsureHasDeviceAdmin -> deviceAdminComponent.ensureHasDeviceAdmin(
                key,
                onUser,
                isPrimary,
                TestAppProvider().query(dpc)
            )

            is EnsureHasNoTestDeviceAdmin -> deviceAdminComponent.ensureHasNoTestDeviceAdmin(onUser)
            is RequireHasPolicyExemptApps -> logic()
            is MostImportantCoexistenceTest -> logic(deviceOwnerComponent, testAppsComponent)
            is MostRestrictiveCoexistenceTest -> logic(testAppsComponent)
            is EnsureHasWorkProfile -> enterpriseComponent.ensureHasWorkProfile(this)
            is RequireRunOnWorkProfile -> enterpriseComponent.requireRunOnWorkProfile(this)
            is EnsureHasNoWorkProfile -> usersComponent.ensureHasNoProfile(
                EnsureHasNoWorkProfile.PROFILE_TYPE,
                forUser
            )

            is EnsureTestAppInstalledAsPrimaryDPC ->
                enterpriseComponent.ensureTestAppInstalledAsPrimaryDPC(this)

            is EnsureHasUserRestriction -> userRestrictions.ensureHasUserRestriction(value, onUser)
            is EnsureDoesNotHaveUserRestriction ->
                userRestrictions.ensureDoesNotHaveUserRestriction(value, onUser)
        }
    }
}
