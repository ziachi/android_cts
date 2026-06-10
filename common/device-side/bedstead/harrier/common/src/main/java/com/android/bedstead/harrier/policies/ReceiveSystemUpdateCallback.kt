package com.android.bedstead.harrier.policies

import com.android.bedstead.enterprise.annotations.EnterprisePolicy
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_DEVICE_OWNER
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_PROFILE
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_TO_OWN_USER
import com.android.bedstead.enterprise.annotations.EnterprisePolicy.APPLIES_TO_PARENT
import com.android.bedstead.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_QUERY_SYSTEM_UPDATES

/**
 * Policy for receiving a callback when a system update is available.
 */
@EnterprisePolicy(
    dpc = [APPLIED_BY_DEVICE_OWNER or APPLIED_BY_PROFILE_OWNER_USER_WITH_NO_DO
             or APPLIES_TO_OWN_USER,
           APPLIED_BY_PROFILE_OWNER_PROFILE or APPLIES_TO_PARENT])
// We need to split the "querying directly" tests from the "received update" tests as the permission
// only enables the former
//    permissions = [EnterprisePolicy.Permission(
//        appliedWith = MANAGE_DEVICE_POLICY_QUERY_SYSTEM_UPDATES,
//        appliesTo = APPLIES_TO_OWN_USER
//    )])
class ReceiveSystemUpdateCallback {
}
