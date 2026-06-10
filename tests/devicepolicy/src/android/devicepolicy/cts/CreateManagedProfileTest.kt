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

import android.app.admin.DevicePolicyManager
import android.app.admin.ManagedProfileProvisioningParams
import android.app.admin.ProvisioningException
import android.app.admin.flags.Flags.FLAG_SPLIT_CREATE_MANAGED_PROFILE_ENABLED
import com.android.bedstead.deviceadminapp.DeviceAdminApp
import com.android.bedstead.enterprise.annotations.EnsureHasDevicePolicyManagerRoleHolder
import com.android.bedstead.enterprise.annotations.EnsureHasNoDpc
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.dpmRoleHolder
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.appops.AppOpsMode
import com.android.bedstead.nene.devicepolicy.DeviceAdmin
import com.android.bedstead.nene.packages.CommonPackages.FEATURE_DEVICE_ADMIN
import com.android.bedstead.nene.packages.CommonPackages.FEATURE_MANAGED_USERS
import com.android.bedstead.nene.packages.Package
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.users.UserType
import com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_PROFILES
import com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_USERS
import com.android.bedstead.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.stream.Collectors
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert.assertThrows

@RunWith(BedsteadJUnit4::class)
@RequireFeature(FEATURE_DEVICE_ADMIN)
@RequireFeature(FEATURE_MANAGED_USERS)
@RequireFlagsEnabled(FLAG_SPLIT_CREATE_MANAGED_PROFILE_ENABLED)
class CreateManagedProfileTest {

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createManagedProfile"])
    fun createManagedProfile_createsUserWithManagedProfileType() {
        UserReference.of(
            deviceState.dpmRoleHolder().devicePolicyManager().createManagedProfile(
                MANAGED_PROFILE_PARAMS
            )
        ).use { profile ->
            assertThat(profile.type()).isEqualTo(
                TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME)
            )
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createManagedProfile"])
    fun createManagedProfile_setsProfileOwnerActiveAdmin() {
        UserReference.of(
            deviceState.dpmRoleHolder().devicePolicyManager().createManagedProfile(
                MANAGED_PROFILE_PARAMS
            )
        ).use { profile ->
            assertThat(TestApis.devicePolicy().getActiveAdmins(profile))
                    .containsExactly(DeviceAdmin.of(DEVICE_ADMIN_COMPONENT_NAME))
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createManagedProfile"])
    fun createManagedProfile_removesNonRequiredAppsFromProfile() {
        UserReference.of(
            deviceState.dpmRoleHolder().devicePolicyManager().createManagedProfile(
                MANAGED_PROFILE_PARAMS
            )
        ).use { profile ->
            assertThat(getNonRequiredAppsForUser(profile)).isEmpty()
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    @EnsureHasDevicePolicyManagerRoleHolder
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createManagedProfile"])
    fun createManagedProfile_setsCrossProfilePackages() {
        UserReference.of(
            deviceState.dpmRoleHolder().devicePolicyManager().createManagedProfile(
                MANAGED_PROFILE_PARAMS
            )
        ).use { profile ->
            val defaultPackages = TestApis.devicePolicy().defaultCrossProfilePackages().stream()
                    .filter { it.canConfigureInteractAcrossProfiles() }
                    .filter { it.isInstalled }
                    .collect(Collectors.toSet())
            for (crossProfilePackage in defaultPackages) {
                assertWithMessage(
                        "Checking crossprofilepackage : $crossProfilePackage on parent"
                ).that(
                        crossProfilePackage.appOps()[INTERACT_ACROSS_PROFILES]
                ).isEqualTo(AppOpsMode.ALLOWED)
                assertWithMessage(
                        "Checking crossprofilepackage : $crossProfilePackage on profile"
                ).that(
                        crossProfilePackage.appOps(profile)[INTERACT_ACROSS_PROFILES]
                ).isEqualTo(AppOpsMode.ALLOWED)
            }
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    @EnsureHasDevicePolicyManagerRoleHolder
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createManagedProfile"])
    fun createManagedProfile_withExistingProfile_preconditionFails() {
        val exception = Assert.assertThrows(ProvisioningException::class.java) {
            deviceState.dpmRoleHolder().devicePolicyManager().createManagedProfile(
                MANAGED_PROFILE_PARAMS
            )
        }
        assertThat(exception.provisioningError)
                .isEqualTo(ProvisioningException.ERROR_PRE_CONDITION_FAILED)
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureDoesNotHavePermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#createManagedProfile"])
    fun createManagedProfile_noPermission_throwsSecurityException() {
        assertThrows(SecurityException::class.java) {
            localDevicePolicyManager.createManagedProfile(MANAGED_PROFILE_PARAMS)
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureDoesNotHavePermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasAdditionalUser
    @ApiTest(apis = ["android.app.admin.DevicePolicyManager#finalizeCreateManagedProfile"])
    fun finalizeCreateManagedProfile_noPermission_throwsSecurityException() {
        assertThrows(SecurityException::class.java) {
            localDevicePolicyManager.finalizeCreateManagedProfile(
                    MANAGED_PROFILE_PARAMS,
                deviceState.additionalUser().userHandle()
            )
        }
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        private val context = TestApis.context().instrumentedContext()
        private val localDevicePolicyManager = context.getSystemService(
            DevicePolicyManager::class.java
        )!!

        private const val PROFILE_OWNER_NAME = "testDeviceAdmin"
        private val DEVICE_ADMIN_COMPONENT_NAME = DeviceAdminApp.deviceAdminComponentName(context)
        private val MANAGED_PROFILE_PARAMS = createManagedProfileProvisioningParamsBuilder().build()
        private fun createManagedProfileProvisioningParamsBuilder():
                ManagedProfileProvisioningParams.Builder {
            return ManagedProfileProvisioningParams.Builder(
                    DEVICE_ADMIN_COMPONENT_NAME,
                    PROFILE_OWNER_NAME
            )
        }

        private fun getNonRequiredAppsForUser(user: UserReference): Collection<Package> {
            TestApis.permissions().withPermission(
                MANAGE_PROFILE_AND_DEVICE_OWNERS,
                INTERACT_ACROSS_USERS
            ).use {
                val nonRequiredApps = localDevicePolicyManager.getDisallowedSystemApps(
                        DEVICE_ADMIN_COMPONENT_NAME,
                        user.id(),
                        DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE
                )
                val nonRequiredAppsInProfile = TestApis.packages().installedForUser(user)
                nonRequiredAppsInProfile.retainAll(
                        nonRequiredApps.stream().map { TestApis.packages().find(it) }
                                .collect(Collectors.toSet())
                )
                return nonRequiredAppsInProfile
            }
        }
    }
}
