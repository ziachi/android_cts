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

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.remotedpc.RemoteDeviceAdmin
import com.android.bedstead.remotedpc.RemoteDevicePolicyManagerRoleHolder
import com.android.bedstead.remotedpc.RemoteDpc
import com.android.bedstead.remotedpc.RemotePolicyManager
import com.google.errorprone.annotations.CanIgnoreReturnValue

private fun DeviceState.enterpriseComponent(): EnterpriseComponent =
    getDependency(EnterpriseComponent::class.java)

/**
 * Get the [UserReference] of the work profile for the initial user.
 *
 * If the current user is a work profile, then the current user will be returned.
 *
 * This should only be used to get work profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed work profile
 */
@CanIgnoreReturnValue
fun DeviceState.workProfile(): UserReference = enterpriseComponent().workProfile()

/**
 * Get the [UserReference] of the work profile.
 *
 * This should only be used to get work profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed work profile for the given user
 */
fun DeviceState.workProfile(forUser: UserType): UserReference =
    enterpriseComponent().workProfile(forUser)

/**
 * Get the [UserReference] of the work profile.
 *
 * This should only be used to get work profiles managed by Harrier (using either the
 * annotations or calls to the [DeviceState] class.
 *
 * @throws IllegalStateException if there is no harrier-managed work profile for the given user
 */
fun DeviceState.workProfile(forUser: UserReference): UserReference =
    enterpriseComponent().workProfile(forUser)

/**
 * Behaves like [.dpc] except that when running on a delegate, this will return
 * the delegating DPC not the delegate.
 */
fun DeviceState.dpcOnly(): RemotePolicyManager = enterpriseComponent().dpcOnly()

/**
 * Get the most appropriate [RemotePolicyManager] instance for the device state.
 *
 * This method should only be used by tests which are annotated with any of:
 * [PolicyAppliesTest]
 * [PolicyDoesNotApplyTest]
 * [CanSetPolicyTest]
 * [CannotSetPolicyTest]
 *
 * This may be a DPC, a delegate, a device admin, or a normal app with or without given
 * permissions.
 *
 * If no policy manager is set as "primary" for the device state, then this method will first
 * check for a profile owner in the current user, or else check for a device owner.
 *
 * If no Harrier-managed profile owner or device owner exists, an exception will be thrown.
 *
 * If the profile owner or device owner is not a RemoteDPC then an exception will be thrown.
 */
fun DeviceState.dpc(): RemotePolicyManager = enterpriseComponent().dpc()

/**
 * Get the Device Policy Management Role Holder.
 */
fun DeviceState.dpmRoleHolder(): RemoteDevicePolicyManagerRoleHolder =
    enterpriseComponent().dpmRoleHolder()

/**
 * Get the [RemoteDpc] for the device owner controlled by Harrier.
 *
 * If no Harrier-managed device owner exists, an exception will be thrown.
 *
 * If the device owner is not a RemoteDPC then an exception will be thrown
 */
@CanIgnoreReturnValue
fun DeviceState.deviceOwner(): RemoteDpc =
    getDependency(DeviceOwnerComponent::class.java).deviceOwner()

/**
 * Get the [RemoteDpc] for the profile owner on the current user controlled by Harrier.
 *
 * If no Harrier-managed profile owner exists, an exception will be thrown.
 *
 * If the profile owner is not a RemoteDPC then an exception will be thrown.
 */
@CanIgnoreReturnValue
fun DeviceState.profileOwner(): RemoteDpc = profileOwner(UserType.INSTRUMENTED_USER)

/**
 * Get the [RemoteDpc] for the profile owner on the given user controlled by Harrier.
 *
 * If no Harrier-managed profile owner exists, an exception will be thrown.
 *
 * If the profile owner is not a RemoteDPC then an exception will be thrown.
 */
fun DeviceState.profileOwner(onUser: UserType): RemoteDpc =
    getDependency(ProfileOwnersComponent::class.java).profileOwner(onUser)

/**
 * Get the [RemoteDpc] for the profile owner on the given user controlled by Harrier.
 *
 * If no Harrier-managed profile owner exists, an exception will be thrown.
 *
 * If the profile owner is not a RemoteDPC then an exception will be thrown.
 */
fun DeviceState.profileOwner(onUser: UserReference): RemoteDpc =
    getDependency(ProfileOwnersComponent::class.java).profileOwner(onUser)

/**
 * Get the [RemoteDeviceAdmin] for the device admin set using
 * `EnsureHasDeviceAdmin` without specifying a custom key.
 *
 * If no Harrier-managed device admin exists, an exception will be thrown.
 */
@CanIgnoreReturnValue
fun DeviceState.deviceAdmin(): RemoteDeviceAdmin =
    getDependency(DeviceAdminComponent::class.java).deviceAdmin()

/**
 * Get the [RemoteDeviceAdmin] for the device admin with the specified key set on the
 * user and controlled by Harrier.
 *
 * If no Harrier-managed device admin exists for the given key, an exception will be thrown.
 */
fun DeviceState.deviceAdmin(key: String): RemoteDeviceAdmin =
    getDependency(DeviceAdminComponent::class.java).deviceAdmin(key)
