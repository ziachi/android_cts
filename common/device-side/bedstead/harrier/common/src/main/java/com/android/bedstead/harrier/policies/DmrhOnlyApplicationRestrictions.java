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

import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_DPM_ROLE_HOLDER;
import static com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_TO_OWN_USER;

import com.android.bedstead.enterprise.annotations.EnterprisePolicy;

/**
 * setting application restrictions as a DMRH. Restrictions set by DMRH does not show up in
 * {@link android.os.UserManager#getApplicationRestrictions}, only in
 * {@link android.content.RestrictionsManager#getApplicationRestrictionsPerAdmin()}.
 *
 * <p>This is used by methods such as
 * {@code DevicePolicyManager#setApplicationRestrictions(ComponentName, String, Bundle)} and
 * {@code DevicePolicyManager#getApplicationRestrictions(ComponentName, String)}.
 */
@EnterprisePolicy(
        dpc = {APPLIED_BY_DPM_ROLE_HOLDER | APPLIES_TO_OWN_USER}
        )
public final class DmrhOnlyApplicationRestrictions {
}
