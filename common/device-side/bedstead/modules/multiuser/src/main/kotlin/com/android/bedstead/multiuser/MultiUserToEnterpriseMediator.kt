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
package com.android.bedstead.multiuser

import com.android.bedstead.nene.users.UserReference

/**
 * Allows to execute Enterprise methods from Multi-user when the module is loaded
 */
interface MultiUserToEnterpriseMediator {

    /**
     * Ensures a given user does not have the restriction set
     */
    fun ensureDoesNotHaveUserRestriction(restriction: String, onUser: UserReference?)

    /**
     * Ensures a given user does not have the restriction set
     */
    fun ensureDoesNotHaveUserRestriction(restriction: String)

    /**
     * Removes existing profile owner on the device for the specified [user]
     */
    fun ensureHasNoProfileOwner(user: UserReference)

    /**
     * Ensures there is no device owner on the device
     */
    fun ensureHasNoDeviceOwner()
}
