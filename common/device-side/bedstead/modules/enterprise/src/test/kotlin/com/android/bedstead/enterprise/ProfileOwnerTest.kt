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

import android.content.ComponentName
import android.os.Build
import com.android.bedstead.enterprise.annotations.EnsureHasNoDpc
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwner
import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser
import com.android.bedstead.harrier.annotations.RequireSdkVersion
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser
import com.android.bedstead.multiuser.annotations.RequireRunNotOnSecondaryUser
import com.android.bedstead.multiuser.secondaryUser
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.devicepolicy.ProfileOwner
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.remotedpc.RemoteDpc
import com.android.bedstead.testapp.TestApp
import com.android.bedstead.testapps.testApps
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class ProfileOwnerTest {


    private val mProfile: UserReference by lazy {
        users().instrumented()
    }

    @Test
    @EnsureHasProfileOwner
    fun user_returnsUser() {
        assertThat(sDeviceState.profileOwner().devicePolicyController().user()).isEqualTo(
            mProfile
        )
    }

    @Test
    @EnsureHasProfileOwner
    fun pkg_returnsPackage() {
        assertThat(sDeviceState.profileOwner().devicePolicyController().pkg()).isNotNull()
    }

    @Test
    @EnsureHasProfileOwner
    fun componentName_returnsComponentName() {
        assertThat(sDeviceState.profileOwner().devicePolicyController().componentName()).isEqualTo(
            DPC_COMPONENT_NAME
        )
    }

    @Test
    @EnsureHasProfileOwner
    fun remove_removesProfileOwner() {
        sDeviceState.profileOwner().devicePolicyController().remove()
        try {
            assertThat(devicePolicy().getProfileOwner(mProfile)).isNull()
        } finally {
            devicePolicy().setProfileOwner(mProfile, DPC_COMPONENT_NAME)
        }
    }

    @Test
    @EnsureHasNoDpc
    fun remove_nonTestOnlyDpc_removesProfileOwner() {
        sNonTestOnlyDpc.install().use {
            val profileOwner = devicePolicy().setProfileOwner(
                users().instrumented(), NON_TEST_ONLY_DPC_COMPONENT_NAME
            )
            profileOwner.remove()
            assertThat(devicePolicy().getProfileOwner()).isNull()
        }
    }

    @Test
    @EnsureHasNoDpc
    @RequireRunOnInitialUser
    fun setAndRemoveProfileOwnerRepeatedly_doesNotThrowError() {
        users().createUser().createAndStart().use {
            sNonTestOnlyDpc.install().use {
                repeat(100) {
                    val profileOwner = devicePolicy().setProfileOwner(
                        users().instrumented(), NON_TEST_ONLY_DPC_COMPONENT_NAME
                    )
                    profileOwner.remove()
                }
            }
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    fun remove_onOtherUser_removesProfileOwner() {
        devicePolicy().getProfileOwner(sDeviceState.secondaryUser())?.remove()

        assertThat(devicePolicy().getProfileOwner(sDeviceState.secondaryUser())).isNull()
    }

    @Test
    @RequireRunOnWorkProfile
    fun remove_onWorkProfile_testDpc_removesProfileOwner() {
        devicePolicy().getProfileOwner()!!.remove()

        assertThat(devicePolicy().getProfileOwner()).isNull()
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.TIRAMISU)
    @RequireRunOnWorkProfile
    fun setIsOrganizationOwned_becomesOrganizationOwned() {
        val profileOwner =
            sDeviceState.profileOwner(sDeviceState.workProfile())
                .devicePolicyController() as ProfileOwner

        profileOwner.setIsOrganizationOwned(true)

        assertThat(profileOwner.isOrganizationOwned).isTrue()
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.TIRAMISU)
    @RequireRunOnWorkProfile
    fun unsetIsOrganizationOwned_becomesNotOrganizationOwned() {
        val profileOwner =
            sDeviceState.profileOwner(sDeviceState.workProfile())
                .devicePolicyController() as ProfileOwner
        profileOwner.setIsOrganizationOwned(true)

        profileOwner.setIsOrganizationOwned(false)

        assertThat(profileOwner.isOrganizationOwned).isFalse()
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val sDeviceState: DeviceState = DeviceState()

        private val DPC_COMPONENT_NAME = ComponentName(
            RemoteDpc.REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX,
            "com.android.bedstead.testapp.BaseTestAppDeviceAdminReceiver"
        )
        private val sNonTestOnlyDpc: TestApp =
            sDeviceState.testApps()
                .query()
                .whereIsDeviceAdmin()
                .isTrue()
                .whereTestOnly()
                .isFalse()
                .get()
        private val NON_TEST_ONLY_DPC_COMPONENT_NAME = ComponentName(
            sNonTestOnlyDpc.packageName(),
            "com.android.bedstead.testapp.DeviceAdminTestApp.DeviceAdminReceiver"
        )
    }
}
