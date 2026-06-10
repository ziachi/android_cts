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
package com.android.bedstead.testapps

import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled
import com.android.bedstead.testapp.TestAppInstance
import com.android.bedstead.testapp.TestAppProvider

/**
 * Get a [TestAppProvider] which is cleared between tests.
 *
 * Note that you must still manage the test apps manually. To have the infrastructure
 * automatically remove test apps use the [EnsureTestAppInstalled] annotation.
 */
fun DeviceState.testApps(): TestAppProvider =
    getDependency(TestAppsComponent::class.java).testAppProvider

/**
 * Get a test app installed with @EnsureTestAppInstalled with no key.
 */
fun DeviceState.testApp(): TestAppInstance = testApp(EnsureTestAppInstalled.DEFAULT_KEY)

/**
 * Get a test app installed with `@EnsureTestAppInstalled` with the given key.
 */
fun DeviceState.testApp(key: String): TestAppInstance =
    getDependency(TestAppsComponent::class.java).testApp(key)
