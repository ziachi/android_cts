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

import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.multiuser.MultiUserToEnterpriseMediator
import com.android.bedstead.nene.users.UserReference

/**
 * Allows to execute Enterprise methods from Multi-user when the module is loaded
 */
@Suppress("unused")
class MultiUserToEnterpriseMediatorImpl(
    locator: BedsteadServiceLocator
) : MultiUserToEnterpriseMediator {

    private val userRestrictionsComponent: UserRestrictionsComponent by locator
    private val profileOwnersComponent: ProfileOwnersComponent by locator
    private val deviceOwnerComponent: DeviceOwnerComponent by locator

    override fun ensureDoesNotHaveUserRestriction(restriction: String, onUser: UserReference?) {
        userRestrictionsComponent.ensureDoesNotHaveUserRestriction(restriction, onUser)
    }

    override fun ensureDoesNotHaveUserRestriction(restriction: String) {
        userRestrictionsComponent.ensureDoesNotHaveUserRestriction(restriction)
    }

    override fun ensureHasNoProfileOwner(user: UserReference) {
        profileOwnersComponent.ensureHasNoProfileOwner(user)
    }

    override fun ensureHasNoDeviceOwner() {
        deviceOwnerComponent.ensureHasNoDeviceOwner()
    }
}
