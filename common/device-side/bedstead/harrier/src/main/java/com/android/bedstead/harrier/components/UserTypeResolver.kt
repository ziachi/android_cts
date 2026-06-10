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

import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.UserType.ADDITIONAL_USER
import com.android.bedstead.harrier.UserType.ADMIN_USER
import com.android.bedstead.harrier.UserType.ANY
import com.android.bedstead.harrier.UserType.CLONE_PROFILE
import com.android.bedstead.harrier.UserType.CURRENT_USER
import com.android.bedstead.harrier.UserType.DPC_USER
import com.android.bedstead.harrier.UserType.INITIAL_USER
import com.android.bedstead.harrier.UserType.INSTRUMENTED_USER
import com.android.bedstead.harrier.UserType.PRIMARY_USER
import com.android.bedstead.harrier.UserType.PRIVATE_PROFILE
import com.android.bedstead.harrier.UserType.SECONDARY_USER
import com.android.bedstead.harrier.UserType.SYSTEM_USER
import com.android.bedstead.harrier.UserType.TV_PROFILE
import com.android.bedstead.harrier.UserType.WORK_PROFILE
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.users.UserReference

/**
 * An interface to create the appropriate [UserReference] for the [UserType]
 */
interface IUserTypeResolver {
    fun toUser(userType: UserType): UserReference
}

/**
 * A tool to create the appropriate [UserReference] for the [UserType]
 */
class UserTypeResolver(locator: BedsteadServiceLocator) {

    private val multiUserResolver: IUserTypeResolver by lazy {
        locator.get("com.android.bedstead.multiuser.MultiUserUserTypeResolver")
    }
    private val enterpriseResolver: IUserTypeResolver by lazy {
        locator.get("com.android.bedstead.enterprise.EnterpriseUserTypeResolver")
    }

    /**
     * Creates appropriate [UserReference] for the [UserType]
     */
    @Suppress("DEPRECATION")
    fun toUser(userType: UserType): UserReference {
        return when (userType) {
            SYSTEM_USER -> users().system()
            INSTRUMENTED_USER -> users().instrumented()
            CURRENT_USER -> users().current()
            PRIMARY_USER -> users().primary()
            INITIAL_USER -> users().initial()
            ADMIN_USER -> users().admin()
            WORK_PROFILE, DPC_USER -> enterpriseResolver.toUser(userType)
            SECONDARY_USER, TV_PROFILE, ADDITIONAL_USER, CLONE_PROFILE, PRIVATE_PROFILE
                -> multiUserResolver.toUser(userType)

            ANY -> throw IllegalStateException("ANY UserType can not be used here")
        }
    }

    /**
     * Calls the provided [action] with the appropriate [UserReference]s for the [UserType].
     * It will be called for all users if [userType] is [ANY].
     */
    inline fun toUser(userType: UserType, crossinline action: (UserReference) -> Unit ) {
        if (userType == ANY) {
            users().all().forEach(action)
        } else {
            action(toUser(userType))
        }
    }
}
