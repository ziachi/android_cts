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
package com.android.bedstead.multiuser

import android.os.Process
import android.os.UserHandle
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsureHasNoSecondaryUser
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser
import com.android.bedstead.multiuser.annotations.EnsureHasCloneProfile
import com.android.bedstead.multiuser.annotations.EnsureHasNoCloneProfile
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser
import com.android.bedstead.multiuser.annotations.RequireHeadlessSystemUserMode
import com.android.bedstead.multiuser.annotations.RequireRunNotOnSecondaryUser
import com.android.bedstead.multiuser.annotations.RequireRunOnPrimaryUser
import com.android.bedstead.multiuser.annotations.RequireRunOnSecondaryUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.types.OptionalBoolean
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.users.UserType
import com.android.bedstead.nene.utils.Poll
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class UsersTest {

    private val mSecondaryUserType = TestApis
        .users()
        .supportedType(UserType.SECONDARY_USER_TYPE_NAME)
    private val mCloneProfileType = TestApis
        .users()
        .supportedType(UserType.CLONE_PROFILE_TYPE_NAME)
    private val mInstrumentedUser = TestApis
        .users()
        .instrumented()

    @Test
    fun supportedTypes_containsSystemUser() {
        val systemUserType = TestApis
                .users()
                .supportedTypes()
                .first { ut: UserType -> ut.name() == UserType.SYSTEM_USER_TYPE_NAME }

        assertThat(systemUserType.baseType())
                .containsExactly(UserType.BaseType.SYSTEM, UserType.BaseType.FULL)
        assertThat(systemUserType.enabled()).isTrue()
        assertThat(systemUserType.maxAllowed()).isEqualTo(MAX_SYSTEM_USERS)
        assertThat(
                systemUserType.maxAllowedPerParent()
        ).isEqualTo(MAX_SYSTEM_USERS_PER_PARENT)
    }

    @Test
    fun supportedType_invalidType_returnsNull() {
        assertThat(TestApis.users().supportedType(INVALID_TYPE_NAME)).isNull()
    }

    @Test
    @EnsureCanAddUser
    fun all_containsCreatedUser() {
        val user = TestApis.users().createUser().create()

        try {
            assertThat(TestApis.users().all()).contains(user)
        } finally {
            user.remove()
        }
    }

    @Test
    @EnsureCanAddUser(number = 2)
    fun all_userAddedSinceLastCallToUsers_containsNewUser() {
        val user = TestApis.users().createUser().create()
        TestApis.users().all()
        val user2 = TestApis.users().createUser().create()

        try {
            assertThat(TestApis.users().all()).contains(user2)
        } finally {
            user.remove()
            user2.remove()
        }
    }

    @Test
    @EnsureCanAddUser
    fun all_userRemovedSinceLastCallToUsers_doesNotContainRemovedUser() {
        val user = TestApis.users().createUser().create()

        user.remove()

        assertThat(TestApis.users().all()).doesNotContain(user)
    }

    @Test
    @EnsureCanAddUser
    fun find_userExists_returnsUserReference() {
        val user = TestApis.users().createUser().create()

        try {
            assertThat(TestApis.users().find(user.id())).isEqualTo(user)
        } finally {
            user.remove()
        }
    }

    @Test
    fun find_userDoesNotExist_returnsUserReference() {
        assertThat(TestApis.users().find(NON_EXISTING_USER_ID)).isNotNull()
    }

    @Test
    fun find_fromUserHandle_referencesCorrectId() {
        assertThat(TestApis.users().find(UserHandle.of(USER_ID)).id()).isEqualTo(USER_ID)
    }

    @Test
    fun find_constructedReferenceReferencesCorrectId() {
        assertThat(TestApis.users().find(USER_ID).id()).isEqualTo(USER_ID)
    }

    @Test
    @EnsureCanAddUser
    fun createUser_additionalSystemUser_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.users().createUser()
                    .type(TestApis.users().supportedType(UserType.SYSTEM_USER_TYPE_NAME))
                    .create()
        }
    }

    @Test
    @EnsureCanAddUser
    fun createUser_userIsCreated_andIsNotEphemeralOrGuest() {
        val user = TestApis.users().createUser().create()

        try {
            assertThat(user.exists()).isTrue()
            assertThat(user.isEphemeral).isFalse()
            assertThat(user.isGuest).isFalse()
        } finally {
            user.remove()
        }
    }

    @Test
    @EnsureCanAddUser
    fun createUser_createdUserHasCorrectName() {
        val userReference = TestApis
                .users()
                .createUser()
                .name(USER_NAME)
                .create()

        try {
            assertThat(userReference.name()).isEqualTo(USER_NAME)
        } finally {
            userReference.remove()
        }
    }

    @Test
    @EnsureCanAddUser
    fun createUser_createdUserHasCorrectTypeName() {
        val userReference = TestApis.users().createUser()
                .type(mSecondaryUserType)
                .create()

        try {
            assertThat(userReference.type()).isEqualTo(mSecondaryUserType)
        } finally {
            userReference.remove()
        }
    }

    @Test
    @EnsureCanAddUser
    fun createUser_specifiesNullStringUserType_throwsException() {
        val userBuilder = TestApis.users().createUser()

        assertThrows(NullPointerException::class.java) {
            userBuilder.type(null as String?)
        }
    }

    @Test
    @EnsureCanAddUser
    fun createUser_specifiesNullUserType_throwsException() {
        val userBuilder = TestApis.users().createUser()

        assertThrows(NullPointerException::class.java) {
            userBuilder.type(null as UserType?)
        }
    }

    @Test
    @EnsureCanAddUser
    fun createUser_specifiesSystemUserType_throwsException() {
        val type = TestApis.users().supportedType(UserType.SYSTEM_USER_TYPE_NAME)
        val userBuilder = TestApis.users().createUser()
                .type(type)

        assertThrows(NeneException::class.java) { userBuilder.create() }
    }

    @Test
    @EnsureCanAddUser
    fun createUser_specifiesSecondaryUserType_createsUser() {
        val user = TestApis.users().createUser().type(mSecondaryUserType).create()

        try {
            assertThat(user.exists()).isTrue()
        } finally {
            user.remove()
        }
    }

    @Test
    @EnsureHasNoCloneProfile
    @EnsureCanAddUser
    fun createUser_createsProfile_parentIsSet() {
        val personalUser = TestApis.users().instrumented()
        val user = TestApis
                .users()
                .createUser()
                .type(mCloneProfileType)
                .parent(personalUser)
                .create()

        try {
            assertThat(user.parent())
                    .isEqualTo(TestApis.users().instrumented())
        } finally {
            user.remove()
        }
    }

    @Test
    @EnsureCanAddUser
    fun createUser_specifiesParentOnNonProfileType_throwsException() {
        val systemUser = TestApis.users().system()
        val userBuilder = TestApis.users().createUser()
                .type(mSecondaryUserType).parent(systemUser)

        assertThrows(NeneException::class.java) { userBuilder.create() }
    }

    @Test
    @EnsureCanAddUser
    fun createUser_specifiesProfileTypeWithoutParent_throwsException() {
        val userBuilder = TestApis.users().createUser().type(mCloneProfileType)

        assertThrows(NeneException::class.java) { userBuilder.create() }
    }

    @Test
    @EnsureCanAddUser
    fun createAndStart_isStarted() {
        var user: UserReference? = null
        try {
            user = TestApis.users().createUser().name(USER_NAME).createAndStart()

            assertThat(user.isUnlocked()).isTrue()
        } finally {
            user?.remove()
        }
    }

    @Test
    fun system_hasId0() {
        assertThat(TestApis.users().system().id()).isEqualTo(0)
    }

    @Test
    fun instrumented_hasCurrentProcessId() {
        assertThat(TestApis.users().instrumented().id())
                .isEqualTo(Process.myUserHandle().identifier)
    }

    @Test
    @EnsureHasNoSecondaryUser
    fun findUsersOfType_noMatching_returnsEmptySet() {
        assertThat(TestApis.users().findUsersOfType(mSecondaryUserType)).isEmpty()
    }

    @Test
    fun findUsersOfType_nullType_throwsException() {
        assertThrows(NullPointerException::class.java) {
            TestApis.users().findUsersOfType(null)
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @Ignore(
            "TODO: Re-enable when harrier .secondaryUser() only" +
                    " returns the harrier-managed secondary user"
    )
    @EnsureCanAddUser
    fun findUsersOfType_returnsUsers() {
        TestApis.users().createUser().create().use { additionalUser ->

            assertThat(TestApis.users().findUsersOfType(mSecondaryUserType))
                    .containsExactly(sDeviceState.secondaryUser(), additionalUser)
        }
    }

    @Test
    fun findUsersOfType_profileType_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.users().findUsersOfType(mCloneProfileType)
        }
    }

    @Test
    @EnsureHasNoSecondaryUser
    fun findUserOfType_noMatching_returnsNull() {
        assertThat(TestApis.users().findUserOfType(mSecondaryUserType)).isNull()
    }

    @Test
    fun findUserOfType_nullType_throwsException() {
        assertThrows(NullPointerException::class.java) {
            TestApis.users().findUserOfType(null)
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @EnsureCanAddUser
    fun findUserOfType_multipleMatchingUsers_throwsException() {
        TestApis.users().createUser().create().use { _ ->

            assertThrows(NeneException::class.java) {
                TestApis.users().findUserOfType(mSecondaryUserType)
            }
        }
    }

    @Test
    @EnsureHasSecondaryUser
    fun findUserOfType_oneMatchingUser_returnsUser() {
        val users = TestApis.users().findUsersOfType(mSecondaryUserType)
        val i: Iterator<UserReference> = users.iterator()
        i.next() // Skip the first one so we leave one
        while (i.hasNext()) {
            i.next().remove()
        }

        assertThat(TestApis.users().findUserOfType(mSecondaryUserType)).isNotNull()
    }

    @Test
    fun findUserOfType_profileType_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.users().findUserOfType(mCloneProfileType)
        }
    }

    @Test
    @EnsureHasNoCloneProfile
    fun findProfilesOfType_noMatching_returnsEmptySet() {
        assertThat(
                TestApis.users().findProfilesOfType(mCloneProfileType, mInstrumentedUser)
        ).isEmpty()
    }

    @Test
    fun findProfilesOfType_nullType_throwsException() {
        assertThrows(NullPointerException::class.java) {
            TestApis.users().findProfilesOfType(null, mInstrumentedUser)
        }
    }

    @Test
    fun findProfilesOfType_nullParent_throwsException() {
        assertThrows(NullPointerException::class.java) {
            TestApis.users().findProfilesOfType(mCloneProfileType, null)
        }
    }

    // TODO(scottjonathan): Once we have profiles which support more than one instance, test this
    @Test
    @EnsureHasNoCloneProfile
    fun findProfileOfType_noMatching_returnsNull() {
        assertThat(
                TestApis.users().findProfileOfType(mCloneProfileType, mInstrumentedUser)
        ).isNull()
    }

    @Test
    fun findProfilesOfType_nonProfileType_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.users().findProfilesOfType(mSecondaryUserType, mInstrumentedUser)
        }
    }

    @Test
    fun findProfileOfType_nullType_throwsException() {
        assertThrows(NullPointerException::class.java) {
            TestApis.users().findProfileOfType(null, mInstrumentedUser)
        }
    }

    @Test
    fun findProfileOfType_nonProfileType_throwsException() {
        assertThrows(NeneException::class.java) {
            TestApis.users().findProfileOfType(mSecondaryUserType, mInstrumentedUser)
        }
    }

    @Test
    fun findProfileOfType_nullParent_throwsException() {
        assertThrows(NullPointerException::class.java) {
            TestApis.users().findProfileOfType(mCloneProfileType, null)
        }
    }

    @Test // TODO(scottjonathan): This should have a way of specifying exactly 1
    @EnsureHasCloneProfile
    fun findProfileOfType_oneMatchingUser_returnsUser() {
        assertThat(
                TestApis.users().findProfileOfType(mCloneProfileType, mInstrumentedUser)
        ).isNotNull()
    }

    @Test
    fun nonExisting_userDoesNotExist() {
        val userReference = TestApis.users().nonExisting()

        assertThat(userReference.exists()).isFalse()
    }

    @Test
    @EnsureHasSecondaryUser(switchedToUser = OptionalBoolean.TRUE)
    fun currentUser_secondaryUser_returnsCurrentUser() {
        assertThat(TestApis.users().current()).isEqualTo(sDeviceState.secondaryUser())
    }

    @Test
    @RequireRunOnPrimaryUser(switchedToUser = OptionalBoolean.TRUE)
    fun currentUser_primaryUser_returnsCurrentUser() {
        assertThat(TestApis.users().current()).isEqualTo(sDeviceState.primaryUser())
    }

    @Test
    @RequireRunNotOnSecondaryUser
    @EnsureHasSecondaryUser
    @RequireHeadlessSystemUserMode(reason = "stopBgUsersOnSwitch is only for headless")
    @Throws(Exception::class)
    fun switch_hasSetStopBgUsersOnSwitch_stopsUser() {
        try {
            sDeviceState.secondaryUser().switchTo()
            TestApis.users().setStopBgUsersOnSwitch(OptionalBoolean.TRUE)
            TestApis.users().system().switchTo()
            Poll.forValue("Secondary user running") {
                sDeviceState.secondaryUser().isRunning()
            }
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await()

            assertThat(sDeviceState.secondaryUser().isRunning()).isFalse()
        } finally {
            sDeviceState.secondaryUser().start()
            TestApis.users().setStopBgUsersOnSwitch(OptionalBoolean.ANY)
        }
    }

    @Test
    @RequireRunOnSecondaryUser
    fun switch_hasSetStopBgUsersOnSwitchFalse_doesNotStopUser() {
        try {
            TestApis.users().setStopBgUsersOnSwitch(OptionalBoolean.FALSE)
            TestApis.users().system().switchTo()
            assertThat(sDeviceState.secondaryUser().isRunning()).isTrue()
        } finally {
            TestApis.users().setStopBgUsersOnSwitch(OptionalBoolean.ANY)
            sDeviceState.secondaryUser().start()
            sDeviceState.secondaryUser().switchTo()
        }
    }

    @Test
    @EnsureCanAddUser
    fun createEphemeralUser() {
        TestApis.users()
                .createUser()
                .ephemeral(true)
                .create().use { user ->

                    assertThat(user.isEphemeral).isTrue()
                }
    }

    @Test
    @EnsureCanAddUser
    fun createGuestUser() {
        TestApis.users()
                .createUser()
                .type(UserType.USER_TYPE_FULL_GUEST)
                .create().use { user ->

                    assertThat(user.isGuest).isTrue()
                }
    }

    companion object {
        private const val MAX_SYSTEM_USERS = 1
        private const val MAX_SYSTEM_USERS_PER_PARENT = UserType.UNLIMITED
        private const val INVALID_TYPE_NAME = "invalidTypeName"
        private const val NON_EXISTING_USER_ID = 10000
        private const val USER_ID = NON_EXISTING_USER_ID
        private const val USER_NAME = "userName"

        @ClassRule
        @Rule
        @JvmField
        val sDeviceState = DeviceState()
    }
}
