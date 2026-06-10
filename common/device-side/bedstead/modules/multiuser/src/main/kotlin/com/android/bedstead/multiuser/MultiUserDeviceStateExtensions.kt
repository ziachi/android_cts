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

import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.nene.users.UserReference

private fun DeviceState.usersComponent(): UsersComponent = getDependency(UsersComponent::class.java)

/**
 * Get a user of the given type.
 *
 * This should only be used to get users managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed user of the correct type
 */
fun DeviceState.user(userType: String): UserReference {
    return usersComponent().user(userType)
}

/**
 * Get the [UserReference] of the profile for the current user.
 *
 * If the current user is a profile of the correct type, then the current user will be
 * returned.
 *
 * This should only be used to get profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed profile
 */
fun DeviceState.profile(profileType: String): UserReference {
    return usersComponent().profile(profileType, UserType.INSTRUMENTED_USER)
}

/**
 * Get the [UserReference] of the profile of the given type for the given user.
 *
 * This should only be used to get profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed profile for the given user
 */
fun DeviceState.profile(profileType: String, forUser: UserType): UserReference {
    return usersComponent().profile(profileType, forUser)
}

/**
 * Get the [UserReference] of the tv profile for the current user
 *
 * This should only be used to get tv profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed tv profile
 */
fun DeviceState.tvProfile(): UserReference {
    return usersComponent().tvProfile()
}

/**
 * Get the [UserReference] of the tv profile.
 *
 * This should only be used to get tv profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed tv profile
 */
fun DeviceState.tvProfile(forUser: UserType): UserReference {
    return usersComponent().tvProfile(forUser)
}

/**
 * Get the [UserReference] of the tv profile.
 *
 * This should only be used to get tv profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed tv profile
 */
fun DeviceState.tvProfile(forUser: UserReference): UserReference {
    return usersComponent().tvProfile(forUser)
}

/**
 * Get the [UserReference] of the clone profile for the current user
 *
 *
 * This should only be used to get clone profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed clone profile
 */
fun DeviceState.cloneProfile(): UserReference {
    return usersComponent().cloneProfile()
}

/**
 * Get the [UserReference] of the clone profile.
 *
 * This should only be used to get clone profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed clone profile
 */
fun DeviceState.cloneProfile(forUser: UserType): UserReference {
    return usersComponent().cloneProfile(forUser)
}

/**
 * Get the [UserReference] of the clone profile.
 *
 * This should only be used to get clone profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed clone profile
 */
fun DeviceState.cloneProfile(forUser: UserReference): UserReference {
    return usersComponent().cloneProfile(forUser)
}

/**
 * Get the [UserReference] of the private profile for the current user
 *
 * This should only be used to get private profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed private profile
 */
fun DeviceState.privateProfile(): UserReference {
    return usersComponent().privateProfile()
}

/**
 * Get the [UserReference] of the private profile.
 *
 * This should only be used to get private profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed private profile
 */
fun DeviceState.privateProfile(forUser: UserType): UserReference {
    return usersComponent().privateProfile(forUser)
}

/**
 * Get the [UserReference] of the private profile.
 *
 * This should only be used to get private profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed private profile
 */
fun DeviceState.privateProfile(forUser: UserReference): UserReference {
    return usersComponent().privateProfile(forUser)
}

/**
 * Get a secondary user.
 *
 * This should only be used to get secondary users managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed secondary user
 */
fun DeviceState.secondaryUser(): UserReference {
    return usersComponent().user(
        com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME
    )
}

/**
 * Gets the user marked as "other" by use of the `@OtherUser` annotation.
 *
 * @throws IllegalStateException if there is no "other" user
 */
fun DeviceState.otherUser(): UserReference {
    return usersComponent().otherUser()
}

/**
 * Returns the additional user specified by annotation
 */
fun DeviceState.additionalUser(): UserReference {
    return usersComponent().additionalUser()
}
