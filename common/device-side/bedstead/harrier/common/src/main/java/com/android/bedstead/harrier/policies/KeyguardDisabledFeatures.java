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

import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FACE;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_IRIS;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_REMOTE_INPUT;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS;

import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_USER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_TO_OWN_USER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.CANNOT_BE_APPLIED_BY_ROLE_HOLDER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.INHERITABLE;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_KEYGUARD;

import com.android.bedstead.enterprise.annotations.EnterprisePolicy;
import com.android.bedstead.harrier.PolicyArguments;

import java.util.Set;

/**
 * Policy for keyguard disabled features
 * <p/>
 * See {@code DevicePolicyManager#setKeyguardDisabledFeatures(ComponentName, int)} for more
 * details.
 */
@EnterprisePolicy(
        dpc = APPLIED_BY_DEVICE_OWNER | APPLIED_BY_PROFILE_OWNER_USER
                | CANNOT_BE_APPLIED_BY_ROLE_HOLDER
                | APPLIES_TO_OWN_USER | INHERITABLE,
        permissions = @EnterprisePolicy.Permission(
                appliedWith = MANAGE_DEVICE_POLICY_KEYGUARD, appliesTo = APPLIES_TO_OWN_USER))
public final class KeyguardDisabledFeatures extends PolicyArguments<Integer> {

    @Override
    public Set<Integer> validArguments() {
        return Set.of(
                KEYGUARD_DISABLE_SECURE_CAMERA,
                KEYGUARD_DISABLE_SECURE_NOTIFICATIONS,
                KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS,
                KEYGUARD_DISABLE_TRUST_AGENTS,
                KEYGUARD_DISABLE_FINGERPRINT,
                KEYGUARD_DISABLE_REMOTE_INPUT,
                KEYGUARD_DISABLE_FACE,
                KEYGUARD_DISABLE_IRIS);
    }
}
