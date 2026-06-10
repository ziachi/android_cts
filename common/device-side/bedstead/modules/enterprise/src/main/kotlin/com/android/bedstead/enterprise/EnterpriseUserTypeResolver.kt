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
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.UserType.DPC_USER
import com.android.bedstead.harrier.UserType.WORK_PROFILE
import com.android.bedstead.harrier.components.IUserTypeResolver
import com.android.bedstead.nene.users.UserReference

/**
 * [IUserTypeResolver] implementation for types supported by enterprise module
 */
@Suppress("unused")
class EnterpriseUserTypeResolver(locator: BedsteadServiceLocator) : IUserTypeResolver {

    private val enterpriseComponent: EnterpriseComponent by locator

    /**
     * Creates appropriate [UserReference] for the [UserType]
     */
    override fun toUser(userType: UserType): UserReference {
        return when (userType) {
            WORK_PROFILE -> enterpriseComponent.workProfile()
            DPC_USER -> enterpriseComponent.dpc().user()
            else -> throw IllegalStateException("$userType isn't supported by $this")
        }
    }
}
