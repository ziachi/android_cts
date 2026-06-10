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

package android.devicepolicy.cts

import android.app.admin.DevicePolicyManager.APP_FUNCTIONS_DISABLED
import android.app.admin.DevicePolicyManager.APP_FUNCTIONS_DISABLED_CROSS_PROFILE
import android.app.admin.DevicePolicyManager.APP_FUNCTIONS_NOT_CONTROLLED_BY_POLICY
import android.app.appfunctions.flags.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest
import com.android.bedstead.enterprise.dpc
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.policies.AppFunctionsPolicy
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows

/** CTS tests for AppFunctions device policy management. */
@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_MANAGER)
class AppFunctionPolicyTest {

    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#getAppFunctionsPolicy"])
    @CanSetPolicyTest(policy = [AppFunctionsPolicy::class])
    fun getAppFunctionPolicy_defaultValue() {
        val remoteDpm = deviceState.dpc().devicePolicyManager()

        val actualPolicy = remoteDpm.getAppFunctionsPolicy()

        assertThat(actualPolicy).isEqualTo(APP_FUNCTIONS_NOT_CONTROLLED_BY_POLICY)
    }

    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#getAppFunctionsPolicy"])
    @CannotSetPolicyTest(policy = [AppFunctionsPolicy::class])
    fun getAppFunctionPolicy_notAllowed_throwsException() {
        val remoteDpm = deviceState.dpc().devicePolicyManager()

        assertThrows(SecurityException::class.java) {
            remoteDpm.getAppFunctionsPolicy()
        }
    }

    @Test
    @ApiTest(
        apis =
            [
                "android.app.admin.DevicePolicyManager#getAppFunctionsPolicy",
                "android.app.admin.DevicePolicyManager#setAppFunctionsPolicy",
            ]
    )
    @PolicyAppliesTest(policy = [AppFunctionsPolicy::class])
    fun updateAppFunctionPolicy_enabled_policySet() {
        val remoteDpm = deviceState.dpc().devicePolicyManager()
        val originalPolicy = remoteDpm.getAppFunctionsPolicy()

        try {
            remoteDpm.setAppFunctionsPolicy(APP_FUNCTIONS_DISABLED)

            val actualPolicy = remoteDpm.getAppFunctionsPolicy()

            assertThat(actualPolicy).isEqualTo(APP_FUNCTIONS_DISABLED)
        } finally {
            remoteDpm.setAppFunctionsPolicy(originalPolicy)
        }
    }

    @Test
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#setAppFunctionsPolicy"])
    @CannotSetPolicyTest(policy = [AppFunctionsPolicy::class])
    fun setAppFunctionPolicy_notAllowed_throwsException() {
        val remoteDpm = deviceState.dpc().devicePolicyManager()

        assertThrows(SecurityException::class.java) {
            remoteDpm.setAppFunctionsPolicy(APP_FUNCTIONS_DISABLED_CROSS_PROFILE)
        }
    }

    companion object {
        @Rule @ClassRule @JvmField val deviceState = DeviceState()
    }
}
