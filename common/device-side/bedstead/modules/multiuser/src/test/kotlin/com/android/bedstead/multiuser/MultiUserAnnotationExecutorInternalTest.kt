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

import com.android.bedstead.harrier.DeviceStateTester
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.utils.Assert.assertThrows
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Use this class to create tests that validate the behavior of
 * [com.android.bedstead.harrier.DeviceState] components, related to [MultiUserAnnotationExecutor]
 * take a look at [com.android.bedstead.harrier.DeviceStateInternalTest] for example tests
 * and more detailed docs
 */
@RunWith(JUnit4::class)
class MultiUserAnnotationExecutorInternalTest {

    private val mDeviceState = DeviceStateTester()

    @After
    fun tearDown() {
        mDeviceState.tearDown()
    }

    @Test
    fun ensureCanAddUser_usersExceedsDeviceLimit_throwsException() {
        assertThrows(NeneException::class.java) {
            mDeviceState
                    .stepName("EnsureCanAddUser")
                    .apply(listOf(EnsureCanAddUser(9999, failureMode = FailureMode.FAIL)))
        }
    }
}
