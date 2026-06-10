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

import com.android.bedstead.contentsuggestions.annotations.EnsureDefaultContentSuggestionsServiceDisabled
import com.android.bedstead.contentsuggestions.annotations.EnsureDefaultContentSuggestionsServiceEnabled
import com.android.bedstead.contentsuggestions.annotations.EnsureHasTestContentSuggestionsService
import com.android.bedstead.harrier.AnnotationExecutor
import com.android.bedstead.harrier.BedsteadServiceLocator

/**
 * [AnnotationExecutor] for content suggestions annotations
 */
@Suppress("unused")
class ContentSuggestionsAnnotationExecutor(locator: BedsteadServiceLocator) : AnnotationExecutor {

    private val contentSuggestionsComponent: ContentSuggestionsComponent by locator

    override fun applyAnnotation(annotation: Annotation): Unit = annotation.run {
        when (this) {
            is EnsureDefaultContentSuggestionsServiceEnabled ->
                contentSuggestionsComponent.ensureDefaultContentSuggestionsServiceEnabled(
                    user = onUser,
                    enabled = true
                )

            is EnsureDefaultContentSuggestionsServiceDisabled ->
                contentSuggestionsComponent.ensureDefaultContentSuggestionsServiceEnabled(
                    user = onUser,
                    enabled = false
                )

            is EnsureHasTestContentSuggestionsService ->
                contentSuggestionsComponent.ensureHasTestContentSuggestionsService(onUser)
        }
    }
}
