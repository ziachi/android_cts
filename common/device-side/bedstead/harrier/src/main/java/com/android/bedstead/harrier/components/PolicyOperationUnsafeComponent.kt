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
package com.android.bedstead.harrier.components

import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.annotations.EnsurePolicyOperationUnsafe
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.devicepolicy.CommonDevicePolicy

/**
 * Handles [EnsurePolicyOperationUnsafe] for Bedstead tests using [DeviceState] rule
 */
class PolicyOperationUnsafeComponent : DeviceStateComponent {

    private var nextSafetyOperationSet = false

    /**
     * See [EnsurePolicyOperationUnsafe]
     */
    fun ensurePolicyOperationUnsafe(
        operation: CommonDevicePolicy.DevicePolicyOperation,
        reason: CommonDevicePolicy.OperationSafetyReason
    ) {
        nextSafetyOperationSet = true
        devicePolicy().setNextOperationSafety(operation, reason)
        nextSafetyOperationSet = true
        devicePolicy().setNextOperationSafety(operation, reason)
    }

    override fun teardownNonShareableState() {
        if (nextSafetyOperationSet) {
            ensurePolicyOperationUnsafe(
                CommonDevicePolicy.DevicePolicyOperation.OPERATION_NONE,
                CommonDevicePolicy.OperationSafetyReason.OPERATION_SAFETY_REASON_NONE
            )
            nextSafetyOperationSet = false

            /*
            OneTimeSafetyChecker operates under following conditions:
                - stores previously set OneTimeSafetyChecker as its field
                - operates under 10s timeout, after which the previous OneTimeSafetyChecker is set
            Due to those conditions, it is hard to be sure about the checker's state at any given
            moment. Setting it to OPERATION_NONE during teardown may easily be undermined by
            another OneTimeSafetyChecker set earlier in a test, that has just timed out and now sets
            its previous OTSC as a current one, resulting in a faulty state for at least a couple of
            seconds.
            Provided timeout ensures that both setNextOperationSafety calls invoked during
            teardown (and any other that could have been called before teardown) will timeout,
            which will result in stable checker's state without any risk for it to change in the
            following tests.
             */
            Thread.sleep(10_500L)
        }
    }
}
