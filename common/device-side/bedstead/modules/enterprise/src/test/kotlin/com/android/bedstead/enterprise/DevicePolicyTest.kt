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
import android.os.UserManager
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceAdmin
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoProfileOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoWorkProfile
import com.android.bedstead.enterprise.annotations.EnsureHasProfileOwner
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.AfterClass
import com.android.bedstead.harrier.annotations.BeforeClass
import com.android.bedstead.harrier.annotations.RequireSdkVersion
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.TestApis.packages
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.devicepolicy.DeviceAdmin
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.users.UserType
import com.android.bedstead.nene.utils.Assert.assertThrows
import com.android.bedstead.remotedpc.RemoteDeviceAdmin
import com.android.bedstead.testapp.TestApp
import com.android.bedstead.testapps.testApps
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class DevicePolicyTest {


    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    fun setProfileOwner_profileOwnerIsSet() {
        val profile = users().createUser()
            .parent(sUser)
            .type(users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
            .createAndStart()
        sTestApp.install(profile)

        val profileOwner =
            devicePolicy().setProfileOwner(profile, DPC_COMPONENT_NAME)

        try {
            assertThat(devicePolicy().getProfileOwner(profile)).isEqualTo(profileOwner)
        } finally {
            profile.remove()
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    @Ignore("Now we retry on this error so this test takes too long")
    fun setProfileOwner_profileOwnerIsAlreadySet_throwsException() {
        val profile = users().createUser()
            .parent(sUser)
            .type(users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
            .createAndStart()
        try {
            sTestApp.install(profile)

            devicePolicy().setProfileOwner(profile, DPC_COMPONENT_NAME)

            assertThrows(NeneException::class.java) {
                devicePolicy().setProfileOwner(profile, DPC_COMPONENT_NAME)
            }
        } finally {
            profile.remove()
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoWorkProfile
    fun setProfileOwner_componentNameNotInstalled_throwsException() {
        val profile = users().createUser()
            .parent(sUser)
            .type(users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
            .createAndStart()
        try {
            assertThrows(NeneException::class.java) {
                devicePolicy().setProfileOwner(profile, NON_EXISTING_DPC_COMPONENT_NAME)
            }
        } finally {
            profile.remove()
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    fun setProfileOwner_componentNameIsNotDPC_throwsException() {
        assertThrows(NeneException::class.java) {
            devicePolicy().setProfileOwner(
                sUser,
                NOT_DPC_COMPONENT_NAME
            )
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    fun setProfileOwner_userDoesNotExist_throwsException() {
        assertThrows(NeneException::class.java) {
            devicePolicy().setProfileOwner(
                NON_EXISTENT_USER,
                DPC_COMPONENT_NAME
            )
        }
    }

    @EnsureHasNoWorkProfile
    @EnsureHasNoDeviceOwner
    @Test
    fun profileOwner_returnsProfileOwner() {
        val profile = users().createUser()
            .parent(sUser)
            .type(
                users()
                    .supportedType(UserType.MANAGED_PROFILE_TYPE_NAME)
            )
            .createAndStart()
        try {
            sTestApp.install(profile)

            val profileOwner =
                devicePolicy()
                    .setProfileOwner(profile, DPC_COMPONENT_NAME)

            assertThat(devicePolicy().getProfileOwner(profile))
                .isEqualTo(profileOwner)
        } finally {
            profile.remove()
        }
    }

    @EnsureHasNoWorkProfile
    @EnsureHasNoDeviceOwner
    @Test
    fun profileOwner_noProfileOwner_returnsNull() {
        val profile = users().createUser()
            .parent(sUser)
            .type(
                users()
                    .supportedType(UserType.MANAGED_PROFILE_TYPE_NAME)
            )
            .createAndStart()

        try {
            assertThat(devicePolicy().getProfileOwner(profile)).isNull()
        } finally {
            profile.remove()
        }
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    fun setDeviceOwner_deviceOwnerIsSet() {
        val deviceOwner = devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME)

        try {
            assertThat(devicePolicy().getDeviceOwner()).isEqualTo(deviceOwner)
        } finally {
            deviceOwner.remove()
        }
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasDeviceOwner
    fun setDeviceOwner_deviceOwnerIsAlreadySet_throwsException() {
        assertThrows(NeneException::class.java) {
            devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME)
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    fun setDeviceOwner_componentNameNotInstalled_throwsException() {
        try {
            assertThrows(NeneException::class.java) {
                devicePolicy().setDeviceOwner(NON_EXISTING_DPC_COMPONENT_NAME)
            }
        } finally {
            sTestApp.install(sUser)
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    fun setDeviceOwner_componentNameIsNotDPC_throwsException() {
        assertThrows(NeneException::class.java) {
            devicePolicy().setDeviceOwner(NOT_DPC_COMPONENT_NAME)
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    fun setDeviceOwner_preS_userAlreadyOnDevice_throwsException() {
        assertThrows(NeneException::class.java) {
            devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME)
        }
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    fun setDeviceOwner_sPlus_userAlreadyOnDevice_deviceOwnerIsSet() {
        val deviceOwner = devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME)

        try {
            assertThat(devicePolicy().getDeviceOwner()).isNotNull()
        } finally {
            deviceOwner.remove()
        }
    }

    @EnsureHasDeviceOwner
    @Test
    fun deviceOwner_returnsDeviceOwner() {
        assertThat(devicePolicy().getDeviceOwner()).isNotNull()
    }

    @EnsureHasNoDeviceOwner
    @Test
    fun deviceOwner_noDeviceOwner_returnsNull() {
        assertThat(devicePolicy().getDeviceOwner()).isNull()
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    fun profileOwner_autoclose_removesProfileOwner() {
        devicePolicy().setProfileOwner(sUser, DPC_COMPONENT_NAME).use { }
        assertThat(devicePolicy().getProfileOwner(sUser)).isNull()
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    fun deviceOwner_autoclose_removesDeviceOwner() {
        assertThat(
            packages()
                .find("com.android.bedstead.testapp.DeviceAdminTestApp")
                .isInstalled
        )
            .isTrue()
        devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME).use { }
        assertThat(devicePolicy().getDeviceOwner()).isNull()
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    fun setDeviceOwner_recentlyUnsetProfileOwner_sets() {
        devicePolicy().setProfileOwner(sUser, DPC_COMPONENT_NAME).remove()

        devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME)

        assertThat(devicePolicy().getDeviceOwner()).isNotNull()
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    fun setDeviceOwner_recentlyUnsetDeviceOwner_sets() {
        devicePolicy()
            .setDeviceOwner(DPC_COMPONENT_NAME)
            .remove()

        devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME)

        assertThat(devicePolicy().getDeviceOwner()).isNotNull()
    }

    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    fun setProfileOwner_recentlyUnsetProfileOwner_sets() {
        devicePolicy().setProfileOwner(sUser, DPC_COMPONENT_NAME).remove()

        devicePolicy().setProfileOwner(sUser, DPC_COMPONENT_NAME)

        assertThat(devicePolicy().getProfileOwner(sUser)).isNotNull()
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoDeviceOwner
    @EnsureHasNoProfileOwner
    fun setProfileOwner_recentlyUnsetDeviceOwner_sets() {
        devicePolicy().setDeviceOwner(DPC_COMPONENT_NAME)
            .remove()

        devicePolicy().setProfileOwner(sUser, DPC_COMPONENT_NAME)

        assertThat(devicePolicy().getProfileOwner(sUser)).isNotNull()
    }

    @Test
    fun userRestrictions_withUserHandle_returnsObject() {
        assertThat(
            devicePolicy().userRestrictions(
                users().instrumented().userHandle()
            )
        ).isNotNull()
    }

    @Test
    fun userRestrictions_withUserReference_returnsObject() {
        assertThat(
            devicePolicy().userRestrictions(
                users().instrumented()
            )
        ).isNotNull()
    }

//    @Test
//    fun setUserRestriction_userRestrictionIsSet() {
//        devicePolicy().userRestrictions().set(USER_RESTRICTION).use { r ->
//            assertThat(
//                TestApis.devicePolicy().userRestrictions().isSet(USER_RESTRICTION)
//            ).isTrue()
//        }
//    }
//
//    @Test
//    fun unsetUserRestriction_userRestrictionIsNotSet() {
//        devicePolicy().userRestrictions().set(USER_RESTRICTION).use { r ->
//            TestApis.devicePolicy().userRestrictions().unset(USER_RESTRICTION).use { r2 ->
//                assertThat(TestApis.devicePolicy().userRestrictions().isSet(USER_RESTRICTION))
//                    .isFalse()
//            }
//        }
//    }

    @Test
    @EnsureHasProfileOwner(isPrimary = true)
    fun isSet_userRestrictionIsSet_returnsTrue() {
        val originalIsSet: Boolean =
            sDeviceState.dpc()
                .devicePolicyManager()
                .getUserRestrictions(
                    sDeviceState.dpc().componentName()
                )
                .getBoolean(USER_RESTRICTION,  /*default= */false)
        try {
            sDeviceState.dpc()
                .devicePolicyManager()
                .addUserRestriction(
                    sDeviceState.dpc().componentName(),
                    USER_RESTRICTION
                )

            assertThat(
                devicePolicy()
                    .userRestrictions()
                    .isSet(USER_RESTRICTION)
            ).isTrue()
        } finally {
            if (!originalIsSet) {
                sDeviceState.dpc()
                    .devicePolicyManager()
                    .clearUserRestriction(
                        sDeviceState.dpc().componentName(),
                        USER_RESTRICTION
                    )
            }
        }
    }


    @Test
    @EnsureHasProfileOwner(isPrimary = true)
    fun isSet_userRestrictionIsNotSet_returnsFalse() {
        val originalIsSet: Boolean =
            sDeviceState.dpc()
                .devicePolicyManager()
                .getUserRestrictions(
                    sDeviceState.dpc().componentName()
                )
                .getBoolean(USER_RESTRICTION,  /*default= */false)
        try {
            sDeviceState.dpc()
                .devicePolicyManager()
                .clearUserRestriction(
                    sDeviceState.dpc().componentName(),
                    USER_RESTRICTION
                )

            assertThat(
                devicePolicy()
                    .userRestrictions()
                    .isSet(USER_RESTRICTION)
            ).isFalse()
        } finally {
            if (!originalIsSet) {
                sDeviceState.dpc()
                    .devicePolicyManager()
                    .addUserRestriction(
                        sDeviceState.dpc().componentName(),
                        USER_RESTRICTION
                    )
            }
        }
    }

    @Test
    @EnsureHasProfileOwner(
        isPrimary = true,
        onUser = com.android.bedstead.harrier.UserType.ADDITIONAL_USER
    )
    @EnsureHasAdditionalUser
    fun isSet_userRestrictionIsSet_differentUser_returnsTrue() {
        val originalIsSet: Boolean =
            sDeviceState.dpc()
                .devicePolicyManager()
                .getUserRestrictions(
                    sDeviceState.dpc().componentName()
                )
                .getBoolean(
                    USER_RESTRICTION,
                    /*default= */ false
                )
        try {
            sDeviceState.dpc()
                .devicePolicyManager()
                .addUserRestriction(
                    sDeviceState.dpc().componentName(),
                    USER_RESTRICTION
                )

            assertThat(
                devicePolicy()
                    .userRestrictions(sDeviceState.additionalUser())
                    .isSet(USER_RESTRICTION)
            ).isTrue()
        } finally {
            if (!originalIsSet) {
                sDeviceState.dpc()
                    .devicePolicyManager()
                    .clearUserRestriction(
                        sDeviceState.dpc().componentName(),
                        USER_RESTRICTION
                    )
            }
        }
    }

    @Test
    @EnsureHasAdditionalUser
    @EnsureHasProfileOwner(
        isPrimary = true,
        onUser = com.android.bedstead.harrier.UserType.ADDITIONAL_USER
    )
    fun isSet_userRestrictionIsNotSet_differentUser_returnsFalse() {
        val originalIsSet: Boolean =
            sDeviceState.dpc()
                .devicePolicyManager()
                .getUserRestrictions(
                    sDeviceState.dpc().componentName()
                )
                .getBoolean(
                    USER_RESTRICTION,
                    /*default= */ false
                )
        try {
            sDeviceState.dpc()
                .devicePolicyManager()
                .clearUserRestriction(
                    sDeviceState.dpc().componentName(),
                    USER_RESTRICTION
                )

            assertThat(
                devicePolicy()
                    .userRestrictions(sDeviceState.additionalUser())
                    .isSet(USER_RESTRICTION)
            ).isFalse()
        } finally {
            if (!originalIsSet) {
                sDeviceState.dpc()
                    .devicePolicyManager()
                    .addUserRestriction(
                        sDeviceState.dpc().componentName(),
                        USER_RESTRICTION
                    )
            }
        }
    }

    @Test
    fun dump_dumpsState() {
        assertThat(devicePolicy().dump()).isNotEmpty()
    }

    @EnsureHasDeviceAdmin
    @Test
    fun getDeviceAdmins_returnsDeviceAdmin() {
        val deviceAdmins = devicePolicy().getActiveAdmins()

        assertThat(
            deviceAdmins
                .any { admin: DeviceAdmin -> isRemoteDeviceAdmin(admin.componentName()) })
            .isTrue()
    }

    @EnsureHasDeviceAdmin
    @Test
    fun remove_deviceAdmin_isRemoved() {
        val remoteDeviceAdmin = RemoteDeviceAdmin.fetchRemoteDeviceAdmin(sUser)

        remoteDeviceAdmin.devicePolicyController().remove()

        val activeAdmins = devicePolicy().getActiveAdmins()
        assertThat(
            activeAdmins
                .none { admin: DeviceAdmin -> admin.componentName() == remoteDeviceAdmin.componentName() })
            .isTrue()
    }

    private fun isRemoteDeviceAdmin(componentName: ComponentName?): Boolean {
        return componentName != null && componentName.packageName.startsWith(
            REMOTE_DEVICE_ADMIN_APP_PACKAGE_PREFIX
        ) && componentName.className == componentName.packageName + ".DeviceAdminReceiver"
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val sDeviceState: DeviceState = DeviceState()

        //  TODO(180478924): We shouldn't need to hardcode this
        private const val DEVICE_ADMIN_TESTAPP_PACKAGE_NAME =
            "com.android.bedstead.testapp.DeviceAdminTestApp"
        private val NON_EXISTING_DPC_COMPONENT_NAME =
            ComponentName("com.a.package", "com.a.package.Receiver")
        private val DPC_COMPONENT_NAME = ComponentName(
            DEVICE_ADMIN_TESTAPP_PACKAGE_NAME,
            "com.android.bedstead.testapp.DeviceAdminTestApp.DeviceAdminReceiver"
        )
        private const val REMOTE_DEVICE_ADMIN_APP_PACKAGE_PREFIX =
            "com.android.cts.RemoteDeviceAdmin"
        private val NOT_DPC_COMPONENT_NAME = ComponentName(
            DEVICE_ADMIN_TESTAPP_PACKAGE_NAME,
            "incorrect.class.name"
        )
        private val sUser: UserReference = users().instrumented()
        private val NON_EXISTENT_USER: UserReference = users().find(99999)

        private const val USER_RESTRICTION = UserManager.DISALLOW_AUTOFILL

        lateinit var sTestApp: TestApp

        @BeforeClass
        @JvmStatic
        fun setupClass() {
            sTestApp = sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(DEVICE_ADMIN_TESTAPP_PACKAGE_NAME)
                .get()

            sTestApp.install()
            if (sUser != users().system()) {
                // We're going to set the device owner on the system user
                sTestApp.install(users().system())
            }
        }

        @AfterClass
        @JvmStatic
        fun teardownClass() {
            sTestApp.uninstallFromAllUsers()
        }
    }
}
