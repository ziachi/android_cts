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

package com.android.bedstead.harrier.policies;

import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_GLOBALLY;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.CANNOT_BE_APPLIED_BY_ROLE_HOLDER;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_THREAD_NETWORK;

import com.android.bedstead.enterprise.annotations.EnterprisePolicy;

/**
 * Policy related to setting {@code DISALLOW_THREAD_NETWORK}.
*/
@EnterprisePolicy(
    dpc = { APPLIED_BY_DEVICE_OWNER
            | APPLIED_BY_PARENT_INSTANCE_OF_ORGANIZATIONAL_OWNED_PROFILE_OWNER_PROFILE
            | APPLIES_GLOBALLY
            | CANNOT_BE_APPLIED_BY_ROLE_HOLDER }
// We need to split this into two policies because the user restriction can only be set using
// the global method when using the permission.
    /*permissions = @EnterprisePolicy.Permission(
        appliedWith = MANAGE_DEVICE_POLICY_THREAD_NETWORK,
        appliesTo = APPLIES_GLOBALLY) */)
public final class DisallowThreadNetwork {
}
