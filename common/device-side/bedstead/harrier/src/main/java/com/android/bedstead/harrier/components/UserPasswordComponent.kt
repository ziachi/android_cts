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
package com.android.bedstead.harrier.components

import android.util.Log
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.Defaults
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet
import com.android.bedstead.harrier.annotations.EnsurePasswordSet
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.users.UserReference

/**
 * Contains logic specific to user passwords for Bedstead tests using [DeviceState] rule
 *
 * @param locator provides access to other dependencies.
 */
class UserPasswordComponent(locator: BedsteadServiceLocator) : DeviceStateComponent {

    private val mUsersSetPasswords: MutableList<UserReference> = mutableListOf()
    private val userTypeResolver: UserTypeResolver by locator

    /**
     * See [EnsurePasswordSet]
     */
    fun ensurePasswordSet(forUser: UserType, password: String) {
        userTypeResolver.toUser(forUser) {
            if (it.hasLockCredential() && it.lockCredentialEquals(password)) {
                return@toUser
            }
            try {
                it.setPassword(password)
            } catch (e: NeneException) {
                throw AssertionError("Require password set but error when setting " +
                        "password on user " + it, e)
            }
            mUsersSetPasswords.add(it)
        }
    }

    /**
     * See [EnsurePasswordNotSet]
     */
    fun ensurePasswordNotSet(forUser: UserType) {
        userTypeResolver.toUser(forUser) {
            if (!it.hasLockCredential()) {
                return@toUser
            }
            if (mUsersSetPasswords.contains(it)) {
                try {
                    it.clearPassword()
                } catch (e: NeneException) {
                    Log.e(LOG_TAG, "Error clearing password", e)
                }
            }
            if (!it.hasLockCredential()) {
                return@toUser
            }
            try {
                it.clearPassword(Defaults.DEFAULT_PASSWORD)
            } catch (exception: NeneException) {
                throw AssertionError(
                        "Test requires user " + it + " does not have a password. " +
                                "Password is set and is not DEFAULT_PASSWORD.",
                        exception
                )
            }
            mUsersSetPasswords.remove(it)
        }
    }

    override fun teardownShareableState() {
        mUsersSetPasswords.forEach {
            try {
                it.clearPassword()
            } catch (exception: NeneException) {
                Log.w(LOG_TAG, "Error clearing password", exception)
            }
        }
        mUsersSetPasswords.clear()
    }

    companion object {
        private const val LOG_TAG = "UserPasswordComponent"
    }
}
