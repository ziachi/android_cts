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

import android.Manifest.permission.MANAGE_USERS
import android.Manifest.permission.QUERY_USERS
import android.app.supervision.SupervisionManager
import android.app.supervision.flags.Flags
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
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
class SupervisionStateTest {

    @Test
    @ApiTest(apis = ["android.app.supervision.SupervisionManager#isSupervisionEnabled"])
    @EnsureHasPermission(MANAGE_USERS)
    @RequireRootInstrumentation(reason = "Use of MANAGE_USERS")
    fun isSupervisionEnabled_hasManageUsersPermission_returnsCurrentSupervisionState() {
        supervisionManager.setSupervisionEnabled(true)
        assertThat(supervisionManager.isSupervisionEnabled()).isTrue()

        supervisionManager.setSupervisionEnabled(false)
        assertThat(supervisionManager.isSupervisionEnabled()).isFalse()
    }

    @Test
    @ApiTest(apis = ["android.app.supervision.SupervisionManager#isSupervisionEnabled"])
    @EnsureHasPermission(QUERY_USERS)
    fun isSupervisionEnabled_hasQueryUsersPermission_returnsCurrentSupervisionState() {
        supervisionManager.setSupervisionEnabled(true)
        assertThat(supervisionManager.isSupervisionEnabled()).isTrue()

        supervisionManager.setSupervisionEnabled(false)
        assertThat(supervisionManager.isSupervisionEnabled()).isFalse()
    }

    @Test
    @ApiTest(apis = ["android.app.supervision.SupervisionManager#isSupervisionEnabled"])
    @EnsureDoesNotHavePermission(MANAGE_USERS, QUERY_USERS)
    fun isSupervisionEnabled_noPermission_throwsException() {
        assertThrows(SecurityException::class.java) {
            supervisionManager.isSupervisionEnabled()
        }
    }

    companion object {
        @[JvmField ClassRule Rule]
        val deviceState = DeviceState()

        private val context = TestApis.context().instrumentedContext()
        private val supervisionManager = context.getSystemService(SupervisionManager::class.java)
    }
}
