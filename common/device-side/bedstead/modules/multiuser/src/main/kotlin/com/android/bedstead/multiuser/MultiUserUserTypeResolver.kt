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

import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.UserType.ADDITIONAL_USER
import com.android.bedstead.harrier.UserType.CLONE_PROFILE
import com.android.bedstead.harrier.UserType.PRIVATE_PROFILE
import com.android.bedstead.harrier.UserType.SECONDARY_USER
import com.android.bedstead.harrier.UserType.TV_PROFILE
import com.android.bedstead.harrier.components.IUserTypeResolver
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME

/**
 * [IUserTypeResolver] implementation for types supported by multi-user module
 */
@Suppress("unused")
class MultiUserUserTypeResolver(locator: BedsteadServiceLocator) : IUserTypeResolver {

    private val usersComponent: UsersComponent by locator

    /**
     * Creates appropriate [UserReference] for the [UserType]
     */
    override fun toUser(userType: UserType): UserReference {
        return when (userType) {
            SECONDARY_USER -> usersComponent.user(SECONDARY_USER_TYPE_NAME)
            TV_PROFILE -> usersComponent.tvProfile()
            ADDITIONAL_USER -> usersComponent.additionalUser()
            CLONE_PROFILE -> usersComponent.cloneProfile()
            PRIVATE_PROFILE -> usersComponent.privateProfile()
            else -> throw IllegalStateException("$userType isn't supported by $this")
        }
    }
}
