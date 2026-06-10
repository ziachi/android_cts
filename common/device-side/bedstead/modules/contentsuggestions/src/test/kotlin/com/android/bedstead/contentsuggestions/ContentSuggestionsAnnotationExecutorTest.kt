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

import android.app.contentsuggestions.ContentSuggestionsManager
import com.android.bedstead.contentsuggestions.annotations.EnsureDefaultContentSuggestionsServiceDisabled
import com.android.bedstead.contentsuggestions.annotations.EnsureDefaultContentSuggestionsServiceEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.RequireSystemServiceAvailable
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.nene.TestApis.content
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class ContentSuggestionsAnnotationExecutorTest {

    @RequireSystemServiceAvailable(ContentSuggestionsManager::class)
    @EnsureDefaultContentSuggestionsServiceDisabled
    @Test
    fun ensureDefaultContentSuggestionsServiceDisabledAnnotation_defaultContentSuggestionsServiceIsDisabled() {
        assertThat(content().suggestions().defaultServiceEnabled()).isFalse()
    }

    @RequireSystemServiceAvailable(ContentSuggestionsManager::class)
    @EnsureDefaultContentSuggestionsServiceEnabled
    @Test
    fun ensureDefaultContentSuggestionsServiceEnabledAnnotation_defaultContentSuggestionsServiceIsEnabled() {
        assertThat(content().suggestions().defaultServiceEnabled()).isTrue()
    }

    @EnsureHasAdditionalUser
    @EnsureDefaultContentSuggestionsServiceEnabled(onUser = UserType.ADDITIONAL_USER)
    @Test
    fun ensureDefaultContentSuggestionsServiceEnabledAnnotation_onDifferentUser_defaultContentSuggestionsServiceIsEnabled() {
        assertThat(
            content().suggestions().defaultServiceEnabled(deviceState.additionalUser())
        ).isTrue()
    }

    // TODO(b/366175813) fix Bedstead to make this test green
    @EnsureHasAdditionalUser
    @EnsureDefaultContentSuggestionsServiceDisabled(onUser = UserType.ADDITIONAL_USER)
    @Test
    fun ensureDefaultContentSuggestionsServiceDisabledAnnotation_onDifferentUser_defaultContentSuggestionsServiceIsDisabled() {
        assertThat(
            content().suggestions().defaultServiceEnabled(deviceState.additionalUser())
        ).isFalse()
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }
}
