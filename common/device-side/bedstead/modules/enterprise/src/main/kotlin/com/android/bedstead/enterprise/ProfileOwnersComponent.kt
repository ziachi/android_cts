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

import com.android.bedstead.accounts.AccountsComponent
import com.android.bedstead.enterprise.annotations.EnsureHasNoProfileOwner
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwner
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.harrier.components.UserTypeResolver
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.devicepolicy.DevicePolicyController
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.Versions
import com.android.bedstead.remotedpc.RemoteDpc
import com.android.bedstead.remotedpc.RemoteDpcUsingParentInstance
import com.android.bedstead.testapp.TestApp
import com.android.bedstead.testapp.TestAppProvider
import com.android.bedstead.testapp.TestAppQueryBuilder
import com.android.bedstead.testapps.TestAppsComponent

/**
 * Manages profile owners for device state tests.
 *
 * @param locator provides access to other dependencies.
 */
class ProfileOwnersComponent(locator: BedsteadServiceLocator) : DeviceStateComponent {

    private val userTypeResolver: UserTypeResolver by locator
    private val deviceOwnerComponent: DeviceOwnerComponent by locator
    private val enterpriseComponent: EnterpriseComponent by locator
    private val testAppsComponent: TestAppsComponent by locator
    private val userRestrictionsComponent: UserRestrictionsComponent by locator
    private val accountsComponent: AccountsComponent by locator
    private val profileOwners: MutableMap<UserReference, DevicePolicyController?> = HashMap()
    private val changedProfileOwners: MutableMap<UserReference, DevicePolicyController?> = HashMap()

    /**
     * Gets the existing profile owner instance for the given [user].
     */
    fun getExistingProfileOwner(user: UserReference) = profileOwners[user]

    /**
     * See [EnsureHasNoProfileOwner]
     */
    fun ensureHasNoProfileOwner(userType: UserType) {
        ensureHasNoProfileOwner(userTypeResolver.toUser(userType))
    }

    /**
     * See [EnsureHasProfileOwner]
     */
    fun ensureHasProfileOwner(annotation: EnsureHasProfileOwner) {
        // TODO(scottjonathan): Should support non-remotedpc profile owner
        //  (default to remotedpc)
        annotation.apply {
            val user: UserReference = userTypeResolver.toUser(onUser)
            ensureHasProfileOwner(
                user,
                isPrimary,
                useParentInstance,
                affiliationIds.toSet(),
                key,
                TestAppProvider().query(dpc)
            )
        }
    }

    /**
     * See [EnsureHasProfileOwner]
     */
    fun ensureHasProfileOwner(
        user: UserReference,
        isPrimary: Boolean = false,
        useParentInstance: Boolean = false,
        affiliationIds: Set<String>? = emptySet(),
        key: String? = EnsureTestAppInstalled.DEFAULT_KEY,
        dpcQuery: TestAppQueryBuilder = TestAppProvider().query(),
        resolvedDpcTestApp: TestApp? = null
    ) {
        var dpcQueryMutable = dpcQuery
        dpcQueryMutable.applyAnnotation(
            testAppsComponent.additionalQueryParameters.getOrDefault(key, null)
        )
        if (dpcQueryMutable.isEmptyQuery) {
            dpcQueryMutable = TestAppProvider()
                    .query()
                    .wherePackageName()
                    .isEqualTo(RemoteDpc.REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX)
        }
        check(
            !isPrimary || enterpriseComponent.primaryPolicyManager == null ||
                    user == enterpriseComponent.primaryPolicyManager?.user()
        ) { "Only one DPC can be marked as primary per test" }
        val currentProfileOwner = devicePolicy().getProfileOwner(user)
        val currentDeviceOwner = devicePolicy().getDeviceOwner()
        if (currentDeviceOwner != null && currentDeviceOwner.user() == user) {
            // Can't have DO and PO on the same user
            deviceOwnerComponent.ensureHasNoDeviceOwner()
        }
        if (RemoteDpc.matchesRemoteDpcQuery(currentProfileOwner, dpcQueryMutable)) {
            profileOwners[user] = currentProfileOwner
        } else {
            if (user.parent() != null) {
                userRestrictionsComponent.ensureDoesNotHaveUserRestriction(
                    CommonUserRestrictions.DISALLOW_ADD_MANAGED_PROFILE,
                    user.parent()
                )
            }
            if (!changedProfileOwners.containsKey(user)) {
                changedProfileOwners[user] = currentProfileOwner
            }
            accountsComponent.ensureHasNoAccounts(
                user,
                allowPreCreatedAccounts = true,
                FailureMode.FAIL
            )
            if (resolvedDpcTestApp != null) {
                profileOwners[user] =
                    RemoteDpc.setAsProfileOwner(user, resolvedDpcTestApp)
                            .devicePolicyController()
            } else {
                profileOwners[user] =
                    RemoteDpc.setAsProfileOwner(user, dpcQueryMutable).devicePolicyController()
            }
        }
        if (Versions.meetsMinimumSdkVersionRequirement(Versions.U)) {
            accountsComponent.ensureHasNoAccounts(
                user,
                allowPreCreatedAccounts = true,
                FailureMode.FAIL
            )
        } else {
            // Prior to U this incorrectly checked the system user
            accountsComponent.ensureHasNoAccounts(
                UserType.SYSTEM_USER,
                allowPreCreatedAccounts = true,
                FailureMode.FAIL
            )
        }
        if (isPrimary) {
            enterpriseComponent.primaryPolicyManager = if (useParentInstance) {
                RemoteDpcUsingParentInstance(
                    RemoteDpc.forDevicePolicyController(profileOwners[user])
                )
            } else {
                RemoteDpc.forDevicePolicyController(profileOwners[user])
            }
        }
        if (affiliationIds != null) {
            val profileOwner: RemoteDpc = profileOwner(user)
            profileOwner.devicePolicyManager()
                    .setAffiliationIds(profileOwner.componentName(), affiliationIds)
        }
    }

    /**
     * Removes existing profile owner on the device for the specified [user]
     */
    fun ensureHasNoProfileOwner(user: UserReference) {
        val currentProfileOwner = devicePolicy().getProfileOwner(user) ?: return
        if (!changedProfileOwners.containsKey(user)) {
            changedProfileOwners[user] = currentProfileOwner
        }
        devicePolicy().getProfileOwner(user)!!.remove()
        profileOwners.remove(user)
    }

    override fun teardownShareableState() {
        changedProfileOwners.forEach { (key, value) ->
            val currentProfileOwner = devicePolicy().getProfileOwner(key)
            if (currentProfileOwner != value) {
                currentProfileOwner?.remove()
                if (value != null) {
                    devicePolicy().setProfileOwner(key, value.componentName())
                }
            }
        }
        changedProfileOwners.clear()
    }

    /**
     * See [DeviceState.profileOwner]
     */
    fun profileOwner(): RemoteDpc {
        return profileOwner(UserType.INSTRUMENTED_USER)
    }

    /**
     * See [DeviceState.profileOwner]
     */
    fun profileOwner(onUser: UserType): RemoteDpc {
        return profileOwner(userTypeResolver.toUser(onUser))
    }

    /**
     * See [DeviceState.profileOwner]
     */
    fun profileOwner(onUser: UserReference): RemoteDpc {
        check(profileOwners.containsKey(onUser)) {
            ("No Harrier-managed profile owner. This method should " +
                    "only be used when Harrier was used to set the Profile Owner.")
        }
        val profileOwner = profileOwners[onUser]
        check(RemoteDpc.isRemoteDpc(profileOwner)) {
            ("The profile owner is not a RemoteDPC." +
                    " You must use Nene to query for this profile owner.")
        }
        return RemoteDpc.forDevicePolicyController(profileOwner)
    }
}
