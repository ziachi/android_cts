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
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoDpc
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.BeforeClass
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.devicepolicy.DeviceOwner
import com.android.bedstead.nene.devicepolicy.DeviceOwnerType
import com.android.bedstead.remotedpc.RemoteDpc
import com.android.bedstead.testapp.TestApp
import com.android.bedstead.testapps.testApps
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@EnsureHasDeviceOwner
class DeviceOwnerTest {

    @Test
    fun user_returnsUser() {
        Truth.assertThat(mDeviceOwner.user()).isEqualTo(users().system())
    }

    @Test
    fun pkg_returnsPackage() {
        Truth.assertThat(mDeviceOwner.pkg().packageName())
            .isEqualTo(DPC_COMPONENT_NAME.packageName)
    }

    @Test
    fun componentName_returnsComponentName() {
        Truth.assertThat(mDeviceOwner.componentName()).isEqualTo(DPC_COMPONENT_NAME)
    }

    @Test
    fun remove_removesDeviceOwner() {
        mDeviceOwner.remove()

        Truth.assertThat(devicePolicy().getDeviceOwner()).isNull()
    }

    @Test
    fun setDeviceOwnerType_setsDeviceOwnerType() {
        mDeviceOwner.type = DeviceOwnerType.FINANCED

        Truth.assertThat(mDeviceOwner.type).isEqualTo(DeviceOwnerType.FINANCED)
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDpc
    fun remove_nonTestOnlyDpc_removesDeviceOwner() {
        sNonTestOnlyDpc.install().use {
            val deviceOwner = devicePolicy()
                .setDeviceOwner(NON_TEST_ONLY_DPC_COMPONENT_NAME)
            deviceOwner.remove()
            Truth.assertThat(devicePolicy().getDeviceOwner()).isNull()
        }
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDpc
    fun setAndRemoveDeviceOwnerRepeatedly_doesNotThrowError() {
        sNonTestOnlyDpc.install().use {
            repeat(100) {
                val deviceOwner = devicePolicy()
                    .setDeviceOwner(NON_TEST_ONLY_DPC_COMPONENT_NAME)
                deviceOwner.remove()
            }
        }
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val sDeviceState = DeviceState()

        private val DPC_COMPONENT_NAME = ComponentName(
            RemoteDpc.REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX,
            "com.android.bedstead.testapp.BaseTestAppDeviceAdminReceiver"
        )
        private val sNonTestOnlyDpc: TestApp = sDeviceState.testApps().query()
            .whereIsDeviceAdmin().isTrue()
            .whereTestOnly().isFalse()
            .get()
        private val NON_TEST_ONLY_DPC_COMPONENT_NAME = ComponentName(
            sNonTestOnlyDpc.packageName(),
            "com.android.bedstead.testapp.DeviceAdminTestApp.DeviceAdminReceiver"
        )

        private lateinit var mDeviceOwner: DeviceOwner

        @BeforeClass
        @JvmStatic
        fun setupClass() {
            mDeviceOwner = devicePolicy().getDeviceOwner()!!
        }
    }
}