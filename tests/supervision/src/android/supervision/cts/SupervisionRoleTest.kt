/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.supervision.cts

import android.Manifest.permission.MANAGE_ROLE_HOLDERS
import android.Manifest.permission.QUERY_USERS
import android.app.supervision.SupervisionManager
import android.app.supervision.flags.Flags
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser
import com.android.bedstead.multiuser.annotations.EnsureHasNoAdditionalUser
import com.android.bedstead.multiuser.annotations.RequireMultiUserSupport
import com.android.bedstead.nene.TestApis
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.xts.root.annotations.RequireRootInstrumentation
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(Flags.FLAG_SUPERVISION_MANAGER_APIS)
class SupervisionRoleTest {

    @Test
    @ApiTest(
        apis = ["android.app.supervision.SupervisionManager#shouldAllowBypassingSupervisionRoleQualification"]
    )
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS, QUERY_USERS)
    @EnsureHasNoAdditionalUser
    @RequireMultiUserSupport
    @RequireRootInstrumentation(reason = "Use of MANAGE_ROLE_HOLDERS")
    fun shouldAllowBypassingSupervisionRoleQualification_noAdditionalUsers_returnsTrue() {
        setSupervisionEnabled(false)

        assertThat(supervisionManager.shouldAllowBypassingSupervisionRoleQualification()).isTrue()
    }

    @Test
    @ApiTest(
        apis = ["android.app.supervision.SupervisionManager#shouldAllowBypassingSupervisionRoleQualification"]
    )
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS, QUERY_USERS)
    @EnsureCanAddUser
    @EnsureHasNoAdditionalUser
    @RequireMultiUserSupport
    @RequireRootInstrumentation(reason = "Use of MANAGE_ROLE_HOLDERS")
    fun shouldAllowBypassingSupervisionRoleQualification_withTestUsers_returnsTrue() {
        setSupervisionEnabled(false)

        assertThat(supervisionManager.shouldAllowBypassingSupervisionRoleQualification()).isTrue()

        TestApis.users().createUser().forTesting(true).create().use { user ->
            assertThat(supervisionManager.shouldAllowBypassingSupervisionRoleQualification())
                    .isTrue()
        }
    }

    @Test
    @ApiTest(
        apis = ["android.app.supervision.SupervisionManager#shouldAllowBypassingSupervisionRoleQualification"]
    )
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS, QUERY_USERS)
    @EnsureCanAddUser
    @EnsureHasNoAdditionalUser
    @RequireMultiUserSupport
    @RequireRootInstrumentation(reason = "Use of MANAGE_ROLE_HOLDERS")
    fun shouldAllowBypassingSupervisionRoleQualification_withNonTestUsers_returnsFalse() {
        setSupervisionEnabled(false)

        TestApis.users().createUser().forTesting(false).create().use { user ->
            assertThat(supervisionManager.shouldAllowBypassingSupervisionRoleQualification())
                    .isFalse()
        }
    }

    @Test
    @ApiTest(
        apis = ["android.app.supervision.SupervisionManager#shouldAllowBypassingSupervisionRoleQualification"]
    )
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS, QUERY_USERS)
    @EnsureHasNoAdditionalUser
    @RequireMultiUserSupport
    @RequireRootInstrumentation(reason = "Use of MANAGE_ROLE_HOLDERS")
    fun shouldAllowBypassingSupervisionRoleQualification_supervisionEnabled_noAdditionalUsers_returnsFalse() {
        setSupervisionEnabled(true)

        assertThat(supervisionManager.shouldAllowBypassingSupervisionRoleQualification()).isFalse()
    }

    @Test
    @ApiTest(
        apis = ["android.app.supervision.SupervisionManager#shouldAllowBypassingSupervisionRoleQualification"]
    )
    @EnsureDoesNotHavePermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoAdditionalUser
    @EnsureHasPermission(QUERY_USERS)
    @RequireMultiUserSupport
    fun createConfirmSupervisionCredentialsIntent_noPermission_throwsException() {
        setSupervisionEnabled(false)

        assertThrows(SecurityException::class.java) {
            supervisionManager.shouldAllowBypassingSupervisionRoleQualification()
        }
    }

    private fun setSupervisionEnabled(enabled: Boolean) {
        supervisionManager.setSupervisionEnabled(enabled)
        assertThat(supervisionManager.isSupervisionEnabled()).isEqualTo(enabled)
    }

    companion object {
        @[JvmField ClassRule Rule]
        val deviceState = DeviceState()

        private val context = TestApis.context().instrumentedContext()
        private val supervisionManager = context.getSystemService(SupervisionManager::class.java)
    }
}
