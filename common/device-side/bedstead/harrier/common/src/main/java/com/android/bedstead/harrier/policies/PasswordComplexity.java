/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_DPM_ROLE_HOLDER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_FINANCED_DEVICE_OWNER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_PARENT_INSTANCE_OF_PROFILE_OWNER_PROFILE;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_TO_OWN_USER;

import com.android.bedstead.enterprise.annotations.EnterprisePolicy;

/**
 * Policies around getting the current password's complexity.
 *
 * <p>This is used by methods such as
 * {@code DevicePolicyManager#getPasswordComplexity()}
 *
 * <p> Financed DO also has access to this policy by being considered having
 * MANAGE_DEVICE_POLICY_LOCK_CREDENTIALS permission. This is surfaced in Android V since
 * {@code getPasswordComplexity} is fully migrated to permission-based, so we include
 * it here as well.
 *
 */
@EnterprisePolicy(dpc = {APPLIED_BY_DEVICE_OWNER | APPLIED_BY_PROFILE_OWNER
        | APPLIED_BY_PARENT_INSTANCE_OF_PROFILE_OWNER_PROFILE | APPLIES_TO_OWN_USER,
        APPLIED_BY_DPM_ROLE_HOLDER | APPLIED_BY_FINANCED_DEVICE_OWNER | APPLIES_TO_OWN_USER}
)
//TODO: enable once unmanaged_mode_migration is enabled permanently (b/342873200)
//        permissions = @EnterprisePolicy.Permission(
//                appliedWith = MANAGE_DEVICE_POLICY_LOCK_CREDENTIALS, appliesTo = APPLIES_TO_OWN_USER))
public final class PasswordComplexity {
}
