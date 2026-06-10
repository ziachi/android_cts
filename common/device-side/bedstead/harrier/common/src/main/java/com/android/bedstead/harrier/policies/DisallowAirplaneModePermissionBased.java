/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_GLOBALLY;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_AIRPLANE_MODE;

import com.android.bedstead.enterprise.annotations.EnterprisePolicy;

/**
 * Policy related to setting {@code DISALLOW_AIRPLANE_MODE}
 */
@EnterprisePolicy(permissions = @EnterprisePolicy.Permission(
                appliedWith = MANAGE_DEVICE_POLICY_AIRPLANE_MODE, appliesTo = APPLIES_GLOBALLY))
// TODO(b/278869421): Add support for cross-user permissions.
public final class DisallowAirplaneModePermissionBased {
}
