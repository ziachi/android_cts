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
package com.android.bedstead.contentsuggestions

import com.android.bedstead.contentsuggestions.annotations.EnsureDefaultContentSuggestionsServiceEnabled
import com.android.bedstead.contentsuggestions.annotations.EnsureHasTestContentSuggestionsService
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.components.UserTypeResolver
import com.android.bedstead.nene.TestApis.content
import com.android.bedstead.nene.packages.ComponentReference
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.testapp.TestApp
import com.android.bedstead.testapps.TestAppsComponent

/**
 * Contains logic specific to content suggestions for Bedstead tests using [DeviceState] rule
 *
 * @param locator provides access to other dependencies.
 */
class ContentSuggestionsComponent(locator: BedsteadServiceLocator) : DeviceStateComponent {

    private val userTypeResolver: UserTypeResolver by locator
    private val testAppsComponent: TestAppsComponent by locator
    private val mOriginalDefaultContentSuggestionsServiceEnabled:
            MutableMap<UserReference, Boolean> = mutableMapOf()
    private val mTemporaryContentSuggestionsServiceSet: MutableSet<UserReference> = mutableSetOf()

    private val mContentSuggestionsService = ComponentReference.unflattenFromString(
        "com.android.ContentTestApp/.ContentSuggestionsService"
    )

    private val mContentTestApp: TestApp by lazy {
        testAppsComponent.testAppProvider
                .query()
                .wherePackageName()
                .isEqualTo("com.android.ContentTestApp")
                .get()
    }

    /**
     * See [EnsureHasTestContentSuggestionsService]
     */
    fun ensureHasTestContentSuggestionsService(user: UserType) {
        ensureHasTestContentSuggestionsService(userTypeResolver.toUser(user))
    }

    private fun ensureHasTestContentSuggestionsService(user: UserReference) {
        ensureDefaultContentSuggestionsServiceEnabled(user, enabled = false)
        testAppsComponent.ensureTestAppInstalled("content", mContentTestApp, user)
        mTemporaryContentSuggestionsServiceSet.add(user)
        content().suggestions().setTemporaryService(user, mContentSuggestionsService)
    }

    /**
     * See [EnsureDefaultContentSuggestionsServiceEnabled]
     */
    fun ensureDefaultContentSuggestionsServiceEnabled(user: UserType, enabled: Boolean) {
        ensureDefaultContentSuggestionsServiceEnabled(
            userTypeResolver.toUser(user),
            enabled
        )
    }

    private fun ensureDefaultContentSuggestionsServiceEnabled(
        user: UserReference,
        enabled: Boolean
    ) {
        val currentValue = content().suggestions().defaultServiceEnabled(user)
        if (currentValue == enabled) {
            return
        }
        if (!mOriginalDefaultContentSuggestionsServiceEnabled.containsKey(user)) {
            mOriginalDefaultContentSuggestionsServiceEnabled[user] = currentValue
        }
        content().suggestions().setDefaultServiceEnabled(user, value = enabled)
    }

    override fun teardownShareableState() {
        mOriginalDefaultContentSuggestionsServiceEnabled.forEach {
            content().suggestions().setDefaultServiceEnabled(it.key, it.value)
        }
        mOriginalDefaultContentSuggestionsServiceEnabled.clear()

        mTemporaryContentSuggestionsServiceSet.forEach {
            content().suggestions().clearTemporaryService(it)
        }
        mTemporaryContentSuggestionsServiceSet.clear()
    }
}
