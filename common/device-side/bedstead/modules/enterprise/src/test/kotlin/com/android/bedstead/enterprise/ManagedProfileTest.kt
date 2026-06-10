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

import android.os.Build
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoWorkProfile
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.users.UserType
import com.android.bedstead.nene.utils.Assert.assertThrows
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class ManagedProfileTest {

    private val mManagedProfileType = TestApis
        .users()
        .supportedType(UserType.MANAGED_PROFILE_TYPE_NAME)

    // We don't want to test the exact list of any specific device, so we check that it returns
    // some known types which will exist on the emulators (used for presubmit tests).
    @Test
    fun supportedTypes_containsManagedProfile() {
        val managedProfileUserType = TestApis
            .users()
            .supportedTypes()
            .first { ut: UserType -> ut.name() == UserType.MANAGED_PROFILE_TYPE_NAME }

        assertThat(
            managedProfileUserType.baseType()
        ).containsExactly(UserType.BaseType.PROFILE)
        assertThat(managedProfileUserType.enabled()).isTrue()
        assertThat(managedProfileUserType.maxAllowed()).isEqualTo(MAX_MANAGED_PROFILES)
        assertThat(
            managedProfileUserType.maxAllowedPerParent()
        ).isEqualTo(MAX_MANAGED_PROFILES_PER_PARENT)
    }

    @Test
    fun supportedType_validType_returnsType() {
        val managedProfileUserType = TestApis
            .users()
            .supportedType(UserType.MANAGED_PROFILE_TYPE_NAME)

        assertThat(
            managedProfileUserType!!.baseType()
        ).containsExactly(UserType.BaseType.PROFILE)
        assertThat(managedProfileUserType.enabled()).isTrue()
        assertThat(
            managedProfileUserType.maxAllowed()
        ).isEqualTo(MAX_MANAGED_PROFILES)
        assertThat(
            managedProfileUserType.maxAllowedPerParent()
        ).isEqualTo(MAX_MANAGED_PROFILES_PER_PARENT)
    }

    @Test
    @EnsureHasNoDeviceOwner // Device Owners can disable managed profiles
    @EnsureHasNoWorkProfile
    @EnsureCanAddUser
    fun createUser_specifiesManagedProfileUserType_createsUser() {
        val personalUser = TestApis.users().instrumented()
        val user = TestApis.users()
            .createUser()
            .type(mManagedProfileType)
            .parent(personalUser)
            .create()

        try {
            assertThat(user.exists()).isTrue()
        } finally {
            user.remove()
        }
    }

    @Test
    @EnsureCanAddUser
    fun createUser_androidLessThanS_createsManagedProfileNotOnSystemUser_throwsException() {
        Assume.assumeTrue(
            "After Android S, managed profiles may be a profile of a non-system user",
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        )
        val nonSystemUser = TestApis.users().createUser().create()
        try {
            val userBuilder = TestApis.users().createUser()
                .type(mManagedProfileType)
                .parent(nonSystemUser)

            assertThrows(NeneException::class.java) {
                userBuilder.create()
            }
        } finally {
            nonSystemUser.remove()
        }
    }

    companion object {
        private const val MAX_MANAGED_PROFILES = UserType.UNLIMITED
        private const val MAX_MANAGED_PROFILES_PER_PARENT = 1
    }
}
