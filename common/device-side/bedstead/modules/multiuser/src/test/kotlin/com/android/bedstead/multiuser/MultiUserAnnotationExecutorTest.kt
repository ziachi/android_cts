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

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.EnsureHasNoSecondaryUser
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasCloneProfile
import com.android.bedstead.multiuser.annotations.EnsureHasNoAdditionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasNoCloneProfile
import com.android.bedstead.multiuser.annotations.EnsureHasNoPrivateProfile
import com.android.bedstead.multiuser.annotations.EnsureHasNoTvProfile
import com.android.bedstead.multiuser.annotations.EnsureHasPrivateProfile
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser
import com.android.bedstead.multiuser.annotations.EnsureHasTvProfile
import com.android.bedstead.multiuser.annotations.OtherUser
import com.android.bedstead.multiuser.annotations.RequireGuestUserIsEphemeral
import com.android.bedstead.multiuser.annotations.RequireGuestUserIsNotEphemeral
import com.android.bedstead.multiuser.annotations.RequireHasMainUser
import com.android.bedstead.multiuser.annotations.RequireHeadlessSystemUserMode
import com.android.bedstead.multiuser.annotations.RequireNotHeadlessSystemUserMode
import com.android.bedstead.multiuser.annotations.RequireNotVisibleBackgroundUsers
import com.android.bedstead.multiuser.annotations.RequireNotVisibleBackgroundUsersOnDefaultDisplay
import com.android.bedstead.multiuser.annotations.RequireRunNotOnSecondaryUser
import com.android.bedstead.multiuser.annotations.RequireRunNotOnVisibleBackgroundNonProfileUser
import com.android.bedstead.multiuser.annotations.RequireRunOnCloneProfile
import com.android.bedstead.multiuser.annotations.RequireRunOnPrimaryUser
import com.android.bedstead.multiuser.annotations.RequireRunOnPrivateProfile
import com.android.bedstead.multiuser.annotations.RequireRunOnSecondaryUser
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser
import com.android.bedstead.multiuser.annotations.RequireRunOnTvProfile
import com.android.bedstead.multiuser.annotations.RequireRunOnVisibleBackgroundNonProfileUser
import com.android.bedstead.multiuser.annotations.RequireUserSupported
import com.android.bedstead.multiuser.annotations.RequireVisibleBackgroundUsers
import com.android.bedstead.multiuser.annotations.RequireVisibleBackgroundUsersOnDefaultDisplay
import com.android.bedstead.nene.TestApis.resources
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.types.OptionalBoolean
import com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME
import com.android.bedstead.nene.users.UserType.SYSTEM_USER_TYPE_NAME
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class MultiUserAnnotationExecutorTest {

    @EnsureCanAddUser(number = 2)
    @Test
    fun ensureCanAddUser_canAddUsers() {
        users().createUser().create().use { _ ->
            users().createUser().create().use { _ -> }
        }
    }

    @RequireGuestUserIsEphemeral
    @Test
    fun requireGuestUserIsEphemeral_guestUserIsEphemeral() {
        assertThat(resources().system().getBoolean("config_guestUserEphemeral")).isTrue()
    }

    @RequireGuestUserIsNotEphemeral
    @Test
    fun requireGuestUserIsNotEphemeral_guestUserIsNotEphemeral() {
        assertThat(resources().system().getBoolean("config_guestUserEphemeral")).isFalse()
    }

    @Test
    @RequireHasMainUser(reason = "Test")
    fun requireHasMainUser_hasMainUser() {
        assertThat(users().main()).isNotNull()
    }

    @Test
    @EnsureHasTvProfile
    fun tvProfile_tvProfileProvided_returnsTvProfile() {
        assertThat(deviceState.tvProfile()).isNotNull()
    }

    @Test
    @RequireRunOnTvProfile
    fun tvProfile_runningOnTvProfile_returnsCurrentProfile() {
        assertThat(deviceState.tvProfile()).isEqualTo(users().instrumented())
    }

    @Test
    @EnsureHasNoTvProfile
    fun tvProfile_noTvProfile_throwsException() {
        Assert.assertThrows(IllegalStateException::class.java) {
            deviceState.tvProfile()
        }
    }

    @Test
    @RequireUserSupported(TV_PROFILE_TYPE_NAME)
    @EnsureHasNoTvProfile
    fun tvProfile_createdTvProfile_throwsException() {
        users().createUser()
            .parent(users().instrumented())
            .type(users().supportedType(TV_PROFILE_TYPE_NAME))
            .create().use { _ ->
                Assert.assertThrows(IllegalStateException::class.java) {
                    deviceState.tvProfile()
                }
            }
    }

    @Test
    @EnsureHasTvProfile
    fun ensureHasTvProfileAnnotation_tvProfileExists() {
        assertThat(
            users().findProfileOfType(
                users().supportedType(TV_PROFILE_TYPE_NAME),
                users().instrumented()
            )
        ).isNotNull()
    }

    // TODO(scottjonathan): test the installTestApp argument
    // TODO(scottjonathan): When supported, test the forUser argument

    @Test
    @RequireUserSupported(TV_PROFILE_TYPE_NAME)
    @EnsureHasNoTvProfile
    fun ensureHasNoTvProfileAnnotation_tvProfileDoesNotExist() {
        assertThat(
            users().findProfileOfType(
                users().supportedType(TV_PROFILE_TYPE_NAME),
                users().instrumented()
            )
        ).isNull()
    }

    @Test
    @EnsureHasCloneProfile
    fun cloneProfile_cloneProfileProvided_returnsCloneProfile() {
        assertThat(deviceState.cloneProfile()).isNotNull()
    }

    @Test
    @EnsureHasCloneProfile
    fun ensureHasCloneProfileAnnotation_cloneProfileExists() {
        assertThat(
            users().findProfileOfType(
                users().supportedType(CLONE_PROFILE_TYPE_NAME),
                users().instrumented()
            )
        ).isNotNull()
    }

    @Test
    @EnsureHasNoCloneProfile
    fun ensureHasNoCloneProfileAnnotation_cloneProfileDoesNotExists() {
        assertThat(
            users().findProfileOfType(
                users().supportedType(CLONE_PROFILE_TYPE_NAME),
                users().instrumented()
            )
        ).isNull()
    }

    @Test
    @RequireRunOnCloneProfile
    fun cloneProfile_runningOnCloneProfile_returnsCurrentProfile() {
        assertThat(deviceState.cloneProfile()).isEqualTo(users().instrumented())
    }

    @Test
    @RequireRunOnCloneProfile
    fun requireRunOnCloneProfileAnnotation_isRunningOnCloneProfile() {
        assertThat(users().instrumented().type().name()).isEqualTo(CLONE_PROFILE_TYPE_NAME)
    }

    @Test
    @EnsureHasPrivateProfile
    fun privateProfile_privateProfileProvided_returnsPrivateProfile() {
        assertThat(deviceState.privateProfile()).isNotNull()
    }

    @Test
    @EnsureHasPrivateProfile
    fun ensureHasPrivateProfileAnnotation_privateProfileExists() {
        assertThat(
            users().findProfileOfType(
                users().supportedType(PRIVATE_PROFILE_TYPE_NAME),
                users().instrumented()
            )
        ).isNotNull()
    }

    @Test
    @EnsureHasNoPrivateProfile
    fun ensureHasNoPrivateProfileAnnotation_privateProfileDoesNotExists() {
        assertThat(
            users().findProfileOfType(
                users().supportedType(PRIVATE_PROFILE_TYPE_NAME),
                users().instrumented()
            )
        ).isNull()
    }

    @Test
    @RequireRunOnPrivateProfile
    fun privateProfile_runningOnPrivateProfile_returnsCurrentProfile() {
        assertThat(deviceState.privateProfile()).isEqualTo(users().instrumented())
    }

    @Test
    @RequireRunOnPrivateProfile
    fun requireRunOnPrivateProfileAnnotation_isRunningOnPrivateProfile() {
        assertThat(users().instrumented().type().name()).isEqualTo(PRIVATE_PROFILE_TYPE_NAME)
    }

    @Test
    @EnsureHasSecondaryUser
    fun secondaryUser_secondaryUserProvided_returnsSecondaryUser() {
        assertThat(deviceState.secondaryUser()).isNotNull()
    }

    @Test
    @EnsureHasSecondaryUser
    fun user_userProvided_returnUser() {
        assertThat(deviceState.user(SECONDARY_USER_TYPE_NAME)).isNotNull()
    }

    @Test
    @RequireRunOnSecondaryUser
    fun secondaryUser_runningOnSecondaryUser_returnsCurrentUser() {
        assertThat(deviceState.secondaryUser()).isEqualTo(
            users().instrumented()
        )
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoSecondaryUser
    fun secondaryUser_noSecondaryUser_throwsException() {
        Assert.assertThrows(IllegalStateException::class.java) {
            deviceState.secondaryUser()
        }
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoSecondaryUser
    fun secondaryUser_createdSecondaryUser_throwsException() {
        users().createUser()
            .type(users().supportedType(SECONDARY_USER_TYPE_NAME))
            .create().use { _ ->
                Assert.assertThrows(IllegalStateException::class.java) {
                    deviceState.secondaryUser()
                }
            }
    }

    @Test
    @EnsureHasSecondaryUser
    fun ensureHasSecondaryUserAnnotation_secondaryUserExists() {
        assertThat(
            users().findUsersOfType(users().supportedType(SECONDARY_USER_TYPE_NAME))
        ).isNotEmpty()
    }

    // TODO(scottjonathan): test the installTestApp argument
    // TODO(scottjonathan): Test the forUser argument

    @Test
    @RequireRunOnSystemUser
    @EnsureHasNoSecondaryUser
    fun ensureHasNoSecondaryUserAnnotation_secondaryUserDoesNotExist() {
        assertThat(
            users().findUserOfType(users().supportedType(SECONDARY_USER_TYPE_NAME))
        ).isNull()
    }

    @Test
    @RequireRunOnSecondaryUser
    fun requireRunOnSecondaryUserAnnotation_isRunningOnSecondaryUser() {
        assertThat(users().instrumented().type().name()).isEqualTo(SECONDARY_USER_TYPE_NAME)
    }

    // NOTE: this test must be manually run, as Test Bedstead doesn't support the
    // secondary_user_on_secondary_display metadata (for example, running
    //   atest --user-type secondary_user_on_secondary_display bedstead-multiuser-test:com.android.bedstead
    //   .multiuser
    //   .MultiUserAnnotationExecutorTest
    //   #requireRunOnVisibleBackgroundNonProfileUserAnnotation_instrumentedUserIsVisibleBackgroundNonProfileUser
    // would assumption-fail, even though the module is not annotated to support it). So, you need
    // to manually execute steps like:
    //   adb shell pm create-user TestUser // id 42
    //   adb shell am start-user -w --display 2 42
    //   adb shell pm install-existing --user 42  com.android.bedstead.multiuser.test
    //   adb shell am instrument --user 42 -e class com.android.bedstead.multiuser
    //   .MultiUserAnnotationExecutorTest
    //   #requireRunOnVisibleBackgroundNonProfileUserAnnotation_instrumentedUserIsVisibleBackgroundNonProfileUser -w com.android.bedstead.multiuser.test/androidx.test.runner.AndroidJUnitRunner
    @Test
    @RequireRunOnVisibleBackgroundNonProfileUser
    fun requireRunOnVisibleBackgroundNonProfileUserAnnotation_instrumentedUserIsVisibleBackgroundNonProfileUser() {
        val user = users().instrumented()

        assertWithMessage("%s is visible bg user", user).that(
            user.isVisibleBagroundNonProfileUser
        ).isTrue()
    }

    @Test
    @RequireRunNotOnVisibleBackgroundNonProfileUser
    fun requireRunNotOnVisibleBackgroundNonProfileUserAnnotation_instrumentedUserIsNotVisibleBackgroundNonProfileUser() {
        val user = users().instrumented()

        assertWithMessage("%s is visible bg user", user).that(
            user.isVisibleBagroundNonProfileUser
        ).isFalse()
    }

    @Test
    @RequireRunOnPrimaryUser
    fun requireRunOnPrimaryUserAnnotation_isRunningOnPrimaryUser() {
        assertThat(users().instrumented().type().name()).isEqualTo(SYSTEM_USER_TYPE_NAME)
    }

    @Test
    @RequireRunOnTvProfile
    fun requireRunOnTvProfileAnnotation_isRunningOnTvProfile() {
        assertThat(users().instrumented().type().name()).isEqualTo(TV_PROFILE_TYPE_NAME)
    }

    @Test
    @RequireRunOnInitialUser
    fun requireRunOnUser_isCurrentUser() {
        assertThat(users().current()).isEqualTo(deviceState.initialUser())
    }

    @Test
    @RequireRunOnInitialUser(switchedToUser = OptionalBoolean.FALSE)
    fun requireRunOnUser_specifyNotSwitchedToUser_isNotCurrentUser() {
        assertThat(users().current()).isNotEqualTo(deviceState.initialUser())
    }

    @Test
    @RequireRunNotOnSecondaryUser
    fun requireRunNotOnSecondaryUser_currentUserIsNotSecondary() {
        assertThat(users().current().type().name()).isNotEqualTo(SECONDARY_USER_TYPE_NAME)
    }

    @Test
    @RequireRunNotOnSecondaryUser
    fun requireRunNotOnSecondaryUser_instrumentedUserIsNotSecondary() {
        assertThat(users().instrumented().type().name()).isNotEqualTo(SECONDARY_USER_TYPE_NAME)
    }

    @Test
    @EnsureHasAdditionalUser(
        switchedToUser = OptionalBoolean.FALSE
    ) // We don't test the default as it's ANY
    fun ensureHasUser_specifyIsNotSwitchedToUser_isNotCurrentUser() {
        assertThat(users().current()).isNotEqualTo(deviceState.additionalUser())
    }

    @Test
    @EnsureHasAdditionalUser(switchedToUser = OptionalBoolean.TRUE)
    fun ensureHasUser_specifySwitchedToUser_isCurrentUser() {
        assertThat(users().current()).isEqualTo(deviceState.additionalUser())
    }

    @Test
    @EnsureHasAdditionalUser
    fun ensureHasAdditionalUser_hasAdditionalUser() {
        assertThat(deviceState.additionalUser()).isNotNull()
    }

    @Test
    @EnsureHasNoAdditionalUser
    fun ensureHasNoAdditionalUser_doesNotHaveAdditionalUser() {
        Assert.assertThrows(IllegalStateException::class.java) {
            deviceState.additionalUser()
        }
    }

    @Test
    @RequireNotHeadlessSystemUserMode(reason = "Test")
    fun requireNotHeadlessSystemUserModeAnnotation_notHeadlessSystemUserMode() {
        assertThat(users().isHeadlessSystemUserMode).isFalse()
    }

    @Test
    @RequireHeadlessSystemUserMode(reason = "Test")
    fun requireHeadlessSystemUserModeAnnotation_isHeadlessSystemUserMode() {
        assertThat(users().isHeadlessSystemUserMode).isTrue()
    }

    @Test
    @RequireVisibleBackgroundUsers(reason = "Test")
    fun requireVisibleBackgroundUsersAnnotation_supported() {
        assertThat(users().isVisibleBackgroundUsersSupported).isTrue()
    }

    @Test
    @RequireNotVisibleBackgroundUsers(reason = "Test")
    fun requireNotVisibleBackgroundUsersAnnotation_notSupported() {
        assertThat(users().isVisibleBackgroundUsersSupported).isFalse()
    }

    @Test
    @RequireVisibleBackgroundUsersOnDefaultDisplay(reason = "Test")
    fun requireVisibleBackgroundUsersOnDefaultDisplayAnnotation_supported() {
        assertThat(users().isVisibleBackgroundUsersOnDefaultDisplaySupported).isTrue()
    }

    @Test
    @RequireNotVisibleBackgroundUsersOnDefaultDisplay(reason = "Test")
    fun requireNotVisibleBackgroundUsersOnDefaultDisplayAnnotation_notSupported() {
        assertThat(users().isVisibleBackgroundUsersOnDefaultDisplaySupported).isFalse()
    }

    @Test
    @EnsureHasSecondaryUser
    @OtherUser(UserType.SECONDARY_USER)
    fun otherUserAnnotation_otherUserReturnsCorrectType() {
        assertThat(deviceState.otherUser()).isEqualTo(deviceState.secondaryUser())
    }

    @Test
    fun otherUser_noOtherUserSpecified_throwsException() {
        Assert.assertThrows(IllegalStateException::class.java) {
            deviceState.otherUser()
        }
    }

    @RequireRunOnSystemUser(switchedToUser = OptionalBoolean.ANY)
    @Test
    fun requireRunOnAnnotation_switchedToAny_switches() {
        assertThat(users().instrumented()).isEqualTo(users().current())
    }

    @EnsureHasAdditionalUser(switchedToUser = OptionalBoolean.TRUE)
    @RequireRunOnSystemUser(switchedToUser = OptionalBoolean.ANY)
    @Test
    fun requireRunOnAnnotation_switchedToAny_AnotherAnnotationSwitches_doesNotSwitch() {
        assertThat(users().instrumented()).isNotEqualTo(users().current())
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()

        private const val TV_PROFILE_TYPE_NAME: String = "com.android.tv.profile"
        private const val CLONE_PROFILE_TYPE_NAME: String = "android.os.usertype.profile.CLONE"
        private const val PRIVATE_PROFILE_TYPE_NAME: String = "android.os.usertype.profile.PRIVATE"
    }
}
