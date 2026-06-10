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

package com.android.bedstead.onboarding.extensions

import com.android.onboarding.bedsteadonboarding.OnboardingTestsRule
import com.android.bedstead.harrier.DeviceState

object OnboardingTestsRuleProvider {
    val onboardingTestsRule: OnboardingTestsRule by lazy { OnboardingTestsRule() }
}

/**
 * Returns a singleton instance of [OnboardingTestsRule].
 */
fun DeviceState.onboarding(): OnboardingTestsRule {
    return OnboardingTestsRuleProvider.onboardingTestsRule
}

/**
 * Sends request to teardown [OnboardingTestsRule].
 */
fun DeviceState.teardownExternalRule() {
    onboarding().teardown()
}
