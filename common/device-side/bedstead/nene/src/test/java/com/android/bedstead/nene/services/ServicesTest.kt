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

package com.android.bedstead.nene.services

import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.exceptions.NeneException
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ServicesTest {

    @Test
    fun availableService_serviceIsAvailable_returnsTrue() {
        assertThat(TestApis.services().serviceIsAvailable(AVAILABLE_SERVICE)).isTrue();
    }

    @Test
    fun unavailableService_serviceIsAvailable_throws() {
        val exception = assertThrows(NeneException::class.java) {
            TestApis.services().serviceIsAvailable(UNAVAILABLE_SERVICE)
        }
        assertThat(exception).hasMessageThat().contains("Unknown service $UNAVAILABLE_SERVICE")
    }

    private companion object {
        /** See [Context.DEVICE_POLICY_SERVICE]. */
        private const val AVAILABLE_SERVICE = "device_policy"
        /** See [Context.ACTIVITY_TASK_SERVICE]. */
        private const val UNAVAILABLE_SERVICE = "activity_task"
    }

}