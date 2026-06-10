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
import com.android.bedstead.enterprise.annotations.EnsureDoesNotHaveUserRestriction
import com.android.bedstead.enterprise.annotations.EnsureHasUserRestriction
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.components.UserTypeResolver
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.exceptions.AdbException
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.Tags.hasTag
import com.android.bedstead.remotedpc.RemotePolicyManager
import com.google.errorprone.annotations.CanIgnoreReturnValue
import org.junit.AssumptionViolatedException

/**
 * Contains logic specific to user restrictions for Bedstead tests using [DeviceState] rule
 *
 * @param locator provides access to other dependencies.
 */
class UserRestrictionsComponent(locator: BedsteadServiceLocator) : DeviceStateComponent {

    private val userTypeResolver: UserTypeResolver by locator
    private val mProfileOwnersComponent: ProfileOwnersComponent by locator
    private val mDeviceOwnerComponent: DeviceOwnerComponent by locator
    private val mAddedUserRestrictions: MutableMap<UserReference, MutableSet<String>> =
        mutableMapOf()
    private val mRemovedUserRestrictions: MutableMap<UserReference, MutableSet<String>> =
        mutableMapOf()

    /**
     * See [EnsureHasUserRestriction]
     */
    fun ensureHasUserRestriction(restriction: String, onUser: UserType) {
        ensureHasUserRestriction(restriction, userTypeResolver.toUser(onUser))
    }

    private fun ensureHasUserRestriction(restriction: String, onUser: UserReference) {
        if (devicePolicy().userRestrictions(onUser).isSet(restriction)) {
            return
        }

        // TODO use TestApis.root().testUsesAdbRoot when this is modularised
        val shouldRunAsRoot = hasTag("adb-root")
        if (shouldRunAsRoot) {
            Log.i(LOG_TAG, "Trying to set user restriction as root.")
            try {
                devicePolicy().userRestrictions(onUser)[restriction] = true
            } catch (e: AdbException) {
                Log.i(
                    LOG_TAG,
                    "Unable to set user restriction as root, trying to set using heuristics."
                )
                trySetUserRestriction(onUser, restriction)
            }
        } else {
            trySetUserRestriction(onUser, restriction)
        }
        if (!devicePolicy().userRestrictions(onUser).isSet(restriction)) {
            val message = "Infra cannot set user restriction $restriction" +
                    if (!shouldRunAsRoot) {
                        ". Please add RequireAdbRoot to enable root capabilities."
                    } else {
                        ""
                    }
            throw AssumptionViolatedException(message)
        }
        if (mRemovedUserRestrictions[onUser]?.remove(restriction) != true) {
            if (!mAddedUserRestrictions.containsKey(onUser)) {
                mAddedUserRestrictions[onUser] = HashSet()
            }
            mAddedUserRestrictions[onUser]!!.add(restriction)
        }
        if (!devicePolicy().userRestrictions(onUser).isSet(restriction)) {
            throw NeneException("Error setting user restriction $restriction")
        }
    }

    private fun trySetUserRestriction(onUser: UserReference, restriction: String) {
        Log.i(LOG_TAG, "Trying to set user restriction using heuristics.")
        var hasSet = false
        if (onUser == users().system()) {
            hasSet = trySetUserRestrictionWithDeviceOwner(restriction)
        }
        if (!hasSet) {
            hasSet = trySetUserRestrictionWithProfileOwner(onUser, restriction)
        }
        if (!hasSet && onUser != users().system()) {
            trySetUserRestrictionWithDeviceOwner(restriction)
        }
    }

    @CanIgnoreReturnValue
    private fun trySetUserRestrictionWithDeviceOwner(restriction: String): Boolean {
        mDeviceOwnerComponent.ensureHasDeviceOwner()
        val dpc: RemotePolicyManager = mDeviceOwnerComponent.deviceOwner()
        try {
            dpc.devicePolicyManager().addUserRestriction(dpc.componentName(), restriction)
        } catch (e: SecurityException) {
            if (e.message!!.contains("cannot set user restriction")) {
                return false
            }
            throw e
        }
        return true
    }

    @CanIgnoreReturnValue
    private fun trySetUserRestrictionWithProfileOwner(
        onUser: UserReference,
        restriction: String
    ): Boolean {
        mProfileOwnersComponent.ensureHasProfileOwner(user = onUser)
        val dpc: RemotePolicyManager = mProfileOwnersComponent.profileOwner(onUser)
        try {
            dpc.devicePolicyManager().addUserRestriction(dpc.componentName(), restriction)
        } catch (e: SecurityException) {
            if (e.message!!.contains("cannot set user restriction")) {
                return false
            }
            throw e
        }
        return true
    }

    /**
     * See [EnsureDoesNotHaveUserRestriction]
     */
    fun ensureDoesNotHaveUserRestriction(restriction: String, onUser: UserReference?) {
        if (!devicePolicy().userRestrictions(onUser!!).isSet(restriction)) {
            return
        }

        // TODO use TestApis.root().testUsesAdbRoot when this is modularised
        val shouldRunAsRoot = hasTag("adb-root")
        if (shouldRunAsRoot) {
            Log.i(LOG_TAG, "Trying to clear user restriction as root.")
            try {
                devicePolicy().userRestrictions(onUser)[restriction] = false
            } catch (e: AdbException) {
                Log.i(
                    LOG_TAG,
                    "Unable to clear user restriction as root, trying to clear using" +
                            " heuristics.",
                    e
                )
                tryClearUserRestriction(onUser, restriction)
            }
        } else {
            tryClearUserRestriction(onUser, restriction)
        }
        if (devicePolicy().userRestrictions(onUser).isSet(restriction)) {
            val message =
                "Infra cannot remove user restriction $restriction" +
                        if (shouldRunAsRoot) {
                            ""
                        } else {
                            ". If this test requires capabilities only available on devices " +
                                    "where adb has root, add @RequireAdbRoot to the test."
                        }
            throw AssumptionViolatedException(message)
        }
        if (mAddedUserRestrictions[onUser]?.remove(restriction) != true) {
            if (!mRemovedUserRestrictions.containsKey(onUser)) {
                mRemovedUserRestrictions[onUser] = mutableSetOf()
            }
            mRemovedUserRestrictions[onUser]!!.add(restriction)
        }
    }

    private fun tryClearUserRestriction(onUser: UserReference, restriction: String) {
        if (restriction == CommonUserRestrictions.DISALLOW_ADD_MANAGED_PROFILE) {
            // Special case - set by the system whenever there is a Device Owner
            mDeviceOwnerComponent.ensureHasNoDeviceOwner()
        } else if (restriction == CommonUserRestrictions.DISALLOW_ADD_CLONE_PROFILE) {
            // Special case - set by the system whenever there is a Device Owner
            mDeviceOwnerComponent.ensureHasNoDeviceOwner()
        } else if (restriction == CommonUserRestrictions.DISALLOW_ADD_PRIVATE_PROFILE) {
            // Special case - set by the system whenever there is a Device Owner
            mDeviceOwnerComponent.ensureHasNoDeviceOwner()
        } else if (restriction == CommonUserRestrictions.DISALLOW_ADD_USER) {
            // Special case - set by the system whenever there is a Device Owner or
            // organization-owned profile owner
            mDeviceOwnerComponent.ensureHasNoDeviceOwner()
            val orgOwnedProfileOwner = devicePolicy().organizationOwnedProfileOwner
            if (orgOwnedProfileOwner != null) {
                mProfileOwnersComponent.ensureHasNoProfileOwner(orgOwnedProfileOwner.user())
                return
            }
        }
        var hasCleared = !devicePolicy().userRestrictions(onUser).isSet(restriction)
        if (!hasCleared && onUser == users().system()) {
            hasCleared = tryClearUserRestrictionWithDeviceOwner(restriction)
        }
        if (!hasCleared) {
            hasCleared = tryClearUserRestrictionWithProfileOwner(onUser, restriction)
        }
        if (!hasCleared && onUser != users().system()) {
            tryClearUserRestrictionWithDeviceOwner(restriction)
        }
    }

    override fun teardownNonShareableState() {
        mAddedUserRestrictions.toMap().forEach {
            it.value.toList().forEach { restriction ->
                // below function modifies both collections that we iterate over
                ensureDoesNotHaveUserRestriction(restriction, it.key)
            }
        }

        mRemovedUserRestrictions.toMap().forEach {
            it.value.toList().forEach { restriction ->
                // below function modifies both collections that we iterate over
                ensureHasUserRestriction(restriction, it.key)
            }
        }

        mAddedUserRestrictions.clear()
        mRemovedUserRestrictions.clear()
    }

    @CanIgnoreReturnValue
    private fun tryClearUserRestrictionWithDeviceOwner(restriction: String): Boolean {
        mDeviceOwnerComponent.ensureHasDeviceOwner()
        val dpc: RemotePolicyManager = mDeviceOwnerComponent.deviceOwner()
        try {
            dpc.devicePolicyManager().clearUserRestriction(dpc.componentName(), restriction)
        } catch (e: SecurityException) {
            if (e.message!!.contains("cannot set user restriction")) {
                return false
            }
            throw e
        }
        return true
    }

    @CanIgnoreReturnValue
    private fun tryClearUserRestrictionWithProfileOwner(
        onUser: UserReference,
        restriction: String
    ): Boolean {
        mProfileOwnersComponent.ensureHasProfileOwner(user = onUser)
        val dpc: RemotePolicyManager = mProfileOwnersComponent.profileOwner(onUser)
        try {
            dpc.devicePolicyManager().clearUserRestriction(dpc.componentName(), restriction)
        } catch (e: SecurityException) {
            if (e.message!!.contains("cannot set user restriction")) {
                return false
            }
            throw e
        }
        return true
    }

    /**
     * See [EnsureDoesNotHaveUserRestriction]
     */
    fun ensureDoesNotHaveUserRestriction(restriction: String, onUser: UserType = UserType.ANY) {
        if (onUser == UserType.ANY) {
            for (userReference in users().all()) {
                ensureDoesNotHaveUserRestriction(restriction, userReference)
            }
            return
        }
        ensureDoesNotHaveUserRestriction(restriction, userTypeResolver.toUser(onUser))
    }

    companion object {
        private const val LOG_TAG = "UserRestrictionsComponent"
    }
}
