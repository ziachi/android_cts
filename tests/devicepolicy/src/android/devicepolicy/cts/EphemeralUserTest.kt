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

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser
import com.android.bedstead.multiuser.annotations.RequireGuestUserIsEphemeral
import com.android.bedstead.multiuser.annotations.RequireGuestUserIsNotEphemeral
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.users.UserType
import com.android.bedstead.nene.utils.Poll
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for ephemeral users
 */
@RunWith(BedsteadJUnit4::class)
class EphemeralUserTest {

    @Test
    @EnsureCanAddUser
    fun ephemeralUser_removedAfterStop() {
        TestApis.users()
                .createUser()
                .ephemeral(true)
                .create().use { user ->
                    user.start()

                    user.stop()

                    Poll.forValue { user.exists() }
                            .toBeEqualTo(false)
                            .errorOnFail()
                            .await()
                }
    }

    @Test
    @RequireGuestUserIsNotEphemeral
    @EnsureCanAddUser
    fun guestUser_isNotEphemeral() {
        TestApis.users()
                .createUser()
                .type(UserType.USER_TYPE_FULL_GUEST)
                .create().use { user ->

                    assertThat(user.isEphemeral).isFalse()
                }
    }

    @Test
    @RequireGuestUserIsEphemeral
    @EnsureCanAddUser
    fun guestUser_isEphemeral() {
        TestApis.users()
                .createUser()
                .type(UserType.USER_TYPE_FULL_GUEST)
                .create().use { user ->

                    assertThat(user.isEphemeral).isTrue()
                }
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }
}
