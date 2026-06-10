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

import android.util.Log
import com.android.bedstead.accounts.AccountsComponent
import com.android.bedstead.enterprise.annotations.EnsureHasDelegate
import com.android.bedstead.enterprise.annotations.EnsureHasDevicePolicyManagerRoleHolder
import com.android.bedstead.enterprise.annotations.EnsureHasNoDelegate
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.annotations.EnsureTestAppInstalledAsPrimaryDPC
import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.harrier.components.UserTypeResolver
import com.android.bedstead.multiuser.UsersComponent
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.devicepolicy.ProfileOwner
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.remotedpc.RemoteDelegate
import com.android.bedstead.remotedpc.RemoteDevicePolicyManagerRoleHolder
import com.android.bedstead.remotedpc.RemoteDpc
import com.android.bedstead.remotedpc.RemotePolicyManager
import com.android.bedstead.remotedpc.RemoteTestApp
import com.android.bedstead.testapp.TestAppInstance
import com.android.bedstead.testapp.TestAppProvider
import com.android.bedstead.testapps.TestAppsComponent

/**
 * Contains Enterprise specific logic for device state tests.
 *
 * @param locator provides access to other dependencies.
 */
class EnterpriseComponent(locator: BedsteadServiceLocator) : DeviceStateComponent {

    private val deviceOwnerComponent: DeviceOwnerComponent by locator
    private val profileOwnersComponent: ProfileOwnersComponent by locator
    private val testAppsComponent: TestAppsComponent by locator
    private val usersComponent: UsersComponent by locator
    private val accountsComponent: AccountsComponent by locator
    private val userTypeResolver: UserTypeResolver by locator
    private var devicePolicyManagerRoleHolder: RemoteDevicePolicyManagerRoleHolder? = null
    private var delegateDpc: RemotePolicyManager? = null
    var primaryPolicyManager: RemotePolicyManager? = null

    /**
     * See [EnsureHasDelegate]
     */
    fun ensureHasDelegate(annotation: EnsureHasDelegate) {
        val dpc: RemotePolicyManager = getDeviceAdmin(annotation.admin)
        val specifiesAdminType = annotation.admin != EnsureHasDelegate.AdminType.PRIMARY
        val currentPrimaryPolicyManagerIsNotDelegator =
            primaryPolicyManager != dpc
        check(
            !annotation.isPrimary || primaryPolicyManager == null ||
                    (!specifiesAdminType && !currentPrimaryPolicyManagerIsNotDelegator)
        ) {
            "Only one DPC can be marked as primary per test " +
                    "(current primary is $primaryPolicyManager)"
        }
        testAppsComponent.ensureTestAppInstalled(
            EnsureHasDelegate.DELEGATE_KEY,
            RemoteDelegate.sTestApp,
            dpc.user()
        )
        val delegate = RemoteDelegate(RemoteDelegate.sTestApp, dpc().user())
        dpc.devicePolicyManager().setDelegatedScopes(
            dpc.componentName(),
            delegate.packageName(),
            annotation.scopes.toList()
        )
        if (annotation.isPrimary) {
            delegateDpc = dpc
            primaryPolicyManager = delegate
        }
    }

    /**
     * See [EnsureHasDevicePolicyManagerRoleHolder]
     */
    fun ensureHasDevicePolicyManagerRoleHolder(onUser: UserType, isPrimary: Boolean) {
        val user: UserReference = userTypeResolver.toUser(onUser)
        accountsComponent.ensureHasNoAccounts(
            UserType.ANY,
            allowPreCreatedAccounts = true,
            FailureMode.FAIL
        )
        testAppsComponent.ensureTestAppInstalled(RemoteDevicePolicyManagerRoleHolder.sTestApp, user)
        devicePolicy().setDevicePolicyManagementRoleHolder(
            RemoteDevicePolicyManagerRoleHolder.sTestApp.pkg(),
            user
        )
        devicePolicyManagerRoleHolder = RemoteDevicePolicyManagerRoleHolder(
            RemoteDevicePolicyManagerRoleHolder.sTestApp, user)
        if (isPrimary) {
            // We will override the existing primary
            if (primaryPolicyManager != null) {
                Log.i(
                    LOG_TAG,
                    "Overriding primary policy manager $primaryPolicyManager" +
                            " with $devicePolicyManagerRoleHolder"
                )
            }
            primaryPolicyManager = devicePolicyManagerRoleHolder
        }
    }

    override fun teardownShareableState() {
        devicePolicyManagerRoleHolder?.let {
            devicePolicy().unsetDevicePolicyManagementRoleHolder(
                it.testApp().pkg(),
                it.user()
            )
            devicePolicyManagerRoleHolder = null
        }
    }

    override fun teardownNonShareableState() {
        delegateDpc = null
        primaryPolicyManager = null
    }

    /**
     * See [com.android.bedstead.harrier.DeviceState.dpmRoleHolder]
     */
    fun dpmRoleHolder(): RemoteDevicePolicyManagerRoleHolder {
        return checkNotNull(devicePolicyManagerRoleHolder) {
            "No Harrier-managed device policy manager role holder."
        }
    }

    private fun getDeviceAdmin(adminType: EnsureHasDelegate.AdminType): RemotePolicyManager {
        return when (adminType) {
            EnsureHasDelegate.AdminType.DEVICE_OWNER -> deviceOwnerComponent.deviceOwner()
            EnsureHasDelegate.AdminType.PROFILE_OWNER -> profileOwnersComponent.profileOwner()
            EnsureHasDelegate.AdminType.PRIMARY -> dpc()
        }
    }

    /**
     * See [com.android.bedstead.harrier.DeviceState.dpc]
     */
    fun dpc(): RemotePolicyManager {
        primaryPolicyManager?.let {
            return it
        }
        val profileOwner = profileOwnersComponent.getExistingProfileOwner(users().instrumented())
        if (profileOwner != null) {
            if (RemoteDpc.isRemoteDpc(profileOwner)) {
                return RemoteDpc.forDevicePolicyController(profileOwner)
            }
        }
        deviceOwnerComponent.getDeviceOwner()?.let {
            if (RemoteDpc.isRemoteDpc(it)) {
                return RemoteDpc.forDevicePolicyController(it)
            }
        }
        throw IllegalStateException(
            "No Harrier-managed profile owner or device owner. " +
                    "Ensure you have set up the DPC using bedstead annotations."
        )
    }

    /**
     * See [EnsureHasNoDelegate]
     */
    fun ensureHasNoDelegate(adminType: EnsureHasNoDelegate.AdminType) {
        if (adminType == EnsureHasNoDelegate.AdminType.ANY) {
            for (user in users().all()) {
                testAppsComponent.ensureTestAppNotInstalled(RemoteDelegate.sTestApp, user)
            }
            return
        }
        val dpc: RemotePolicyManager = when (adminType) {
            EnsureHasNoDelegate.AdminType.PRIMARY -> primaryPolicyManager!!
            EnsureHasNoDelegate.AdminType.DEVICE_OWNER -> deviceOwnerComponent.deviceOwner()
            EnsureHasNoDelegate.AdminType.PROFILE_OWNER -> profileOwnersComponent.profileOwner()
            else -> throw IllegalStateException("Unknown Admin Type $adminType")
        }
        testAppsComponent.ensureTestAppNotInstalled(RemoteDelegate.sTestApp, dpc.user())
    }

    /**
     * See [com.android.bedstead.harrier.DeviceState.dpcOnly]
     */
    fun dpcOnly(): RemotePolicyManager {
        if (primaryPolicyManager != null) {
            if (primaryPolicyManager!!.isDelegate) {
                return delegateDpc!!
            }
        }

        return dpc()
    }

    /**
     * See [EnsureHasWorkProfile]
     */
    fun ensureHasWorkProfile(annotation: EnsureHasWorkProfile) {
        annotation.logic()
    }

    private fun EnsureHasWorkProfile.logic() {
        val forUserReference: UserReference = userTypeResolver.toUser(forUser)
        val profile = usersComponent.ensureHasProfile(
            profileType = EnsureHasWorkProfile.PROFILE_TYPE,
            forUserReference,
            isQuietModeEnabled,
            installInstrumentedApp
        )
        profileOwnersComponent.ensureHasProfileOwner(
            profile,
            dpcIsPrimary,
            useParentInstanceOfDpc,
            affiliationIds = null,
            dpcKey,
            TestAppProvider().query(dpc)
        )
        usersComponent.ensureSwitchedToUser(switchedToParentUser, forUserReference)

        if (isOrganizationOwned) {
            // It doesn't make sense to have COPE + DO
            deviceOwnerComponent.ensureHasNoDeviceOwner()
        }

        val profileOwner = profileOwnersComponent
            .profileOwner(workProfile(forUser))
            .devicePolicyController() as ProfileOwner
        profileOwner.setIsOrganizationOwned(isOrganizationOwned)
    }

    /**
     * See [RequireRunOnWorkProfile]
     */
    fun requireRunOnWorkProfile(annotation: RequireRunOnWorkProfile) {
        annotation.logic()
    }

    private fun RequireRunOnWorkProfile.logic() {
        val instrumentedUser = usersComponent.requireRunOnProfile(
            userType = RequireRunOnWorkProfile.PROFILE_TYPE,
            installInstrumentedAppInParent
        )

        profileOwnersComponent.ensureHasProfileOwner(
            instrumentedUser,
            dpcIsPrimary,
            useParentInstance = false,
            affiliationIds.toSet(),
            dpcKey,
            TestAppProvider().query(dpc)
        )
        usersComponent.ensureSwitchedToUser(switchedToParentUser, instrumentedUser.parent()!!)

        if (isOrganizationOwned) {
            // It doesn't make sense to have COPE + DO
            deviceOwnerComponent.ensureHasNoDeviceOwner()
        }

        val profileOwner = profileOwnersComponent
                .profileOwner(workProfile())
                .devicePolicyController() as ProfileOwner
        profileOwner.setIsOrganizationOwned(isOrganizationOwned)
    }

    /**
     * See [com.android.bedstead.harrier.DeviceState.workProfile]
     */
    fun workProfile(): UserReference {
        return workProfile(forUser = UserType.INITIAL_USER)
    }

    /**
     * See [com.android.bedstead.harrier.DeviceState.workProfile]
     */
    fun workProfile(forUser: UserType): UserReference {
        return workProfile(userTypeResolver.toUser(forUser))
    }

    /**
     * See [com.android.bedstead.harrier.DeviceState.workProfile]
     */
    fun workProfile(forUser: UserReference): UserReference {
        return usersComponent.profile(
            com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME,
            forUser
        )
    }

    fun ensureTestAppInstalledAsPrimaryDPC(annotation: EnsureTestAppInstalledAsPrimaryDPC) {
        val testAppInstance: TestAppInstance? = testAppsComponent.ensureTestAppInstalled(
            annotation.key,
            annotation.query,
            userTypeResolver.toUser(annotation.onUser)
        )

        check(primaryPolicyManager == null) {
            ("Only one DPC can be marked as primary per test (current primary is " +
                    primaryPolicyManager + ")")
        }
        primaryPolicyManager = RemoteTestApp(testAppInstance)
    }

    companion object {
        const val LOG_TAG = "EnterpriseComponent"
    }
}
