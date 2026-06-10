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

import com.android.bedstead.enterprise.annotations.EnterprisePolicy
import com.android.bedstead.enterprise.annotations.MostImportantCoexistenceTest
import com.android.bedstead.enterprise.annotations.MostRestrictiveCoexistenceTest
import com.android.bedstead.enterprise.annotations.RequireHasPolicyExemptApps
import com.android.bedstead.harrier.AnnotationExecutorUtil
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.nene.TestApis
import com.android.bedstead.testapps.TestAppsComponent

fun RequireHasPolicyExemptApps.logic() {
    AnnotationExecutorUtil.checkFailOrSkip(
        "OEM does not define any policy-exempt apps",
        TestApis.devicePolicy().getPolicyExemptApps().isNotEmpty(),
        failureMode
    )
}

fun MostImportantCoexistenceTest.logic(
    mDeviceOwnerComponent: DeviceOwnerComponent,
    testAppsComponent: TestAppsComponent
) {
    testAppsComponent.addRemoteDpcTestApp(
        MostImportantCoexistenceTest.MORE_IMPORTANT,
        mDeviceOwnerComponent.deviceOwner()
    )

    val policies: Array<EnterprisePolicy> =
        policy.java.getAnnotationsByType(EnterprisePolicy::class.java)

    check(policies[0].permissions.size == 1) {
        ("Cannot use MostImportantCoexistenceTest for policies which have" +
                " 0 or 2+ permissions")
    }

    val permission = arrayOf(policies[0].permissions[0].appliedWith)

    testAppsComponent.ensureTestAppHasPermission(
        MostImportantCoexistenceTest.MORE_IMPORTANT,
        permission,
        minVersion = 0,
        Int.MAX_VALUE,
        FailureMode.SKIP
    )
    testAppsComponent.ensureTestAppHasPermission(
        MostImportantCoexistenceTest.LESS_IMPORTANT,
        permission,
        minVersion = 0,
        Int.MAX_VALUE,
        FailureMode.SKIP
    )
}
fun MostRestrictiveCoexistenceTest.logic(testAppsComponent: TestAppsComponent) {
    val policies: Array<EnterprisePolicy> =
        policy.java.getAnnotationsByType(EnterprisePolicy::class.java)
    check(policies[0].permissions.size == 1) {
        ("Cannot use MostRestrictiveCoexistenceTest for policies which have " +
                "0 or 2+ permissions")
    }

    val permission = arrayOf(policies[0].permissions[0].appliedWith)

    testAppsComponent.ensureTestAppHasPermission(
        MostRestrictiveCoexistenceTest.DPC_1,
        permission,
        minVersion = 0,
        Int.MAX_VALUE,
        FailureMode.SKIP
    )
    testAppsComponent.ensureTestAppHasPermission(
        MostRestrictiveCoexistenceTest.DPC_2,
        permission,
        minVersion = 0,
        Int.MAX_VALUE,
        FailureMode.SKIP
    )
}
