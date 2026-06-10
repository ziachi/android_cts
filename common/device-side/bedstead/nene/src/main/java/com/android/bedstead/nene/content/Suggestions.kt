/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.bedstead.nene.content

import android.app.contentsuggestions.ContentSuggestionsManager
import android.cts.testapisreflection.*
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.exceptions.AdbException
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.packages.ComponentReference
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.Poll
import com.android.bedstead.nene.utils.ShellCommand
import com.android.bedstead.nene.utils.UndoableContext
import com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL
import com.android.bedstead.permissions.CommonPermissions.MANAGE_CONTENT_SUGGESTIONS
import com.google.errorprone.annotations.CanIgnoreReturnValue
import java.time.Duration

/**
 * Helper methods related to content suggestions.
 */
object Suggestions {

    private val contentSuggestionsManager =
            TestApis.context().instrumentedContext().getSystemService<ContentSuggestionsManager>(
                    ContentSuggestionsManager::class.java)!!
    private const val TEMPORARY_SERVICE_DURATION_MS = 3600000 // 1 hour

    @CanIgnoreReturnValue
    @JvmOverloads
    fun setDefaultServiceEnabled(
        user: UserReference = TestApis.users().instrumented(),
        value: Boolean
    ): UndoableContext {
        val currentValue = defaultServiceEnabled(user)
        if (currentValue == value) {
            // Nothing to do
            return UndoableContext.EMPTY
        }
        TestApis.permissions().withPermission(
            INTERACT_ACROSS_USERS_FULL,
            MANAGE_CONTENT_SUGGESTIONS
        ).use {
            contentSuggestionsManager.setDefaultServiceEnabled(user.id(), value)
            Poll.forValue { defaultServiceEnabled(user) }
                .toBeEqualTo(value)
                .timeout(Duration.ofSeconds(2))
                .errorOnFail()
                .await()
        }
        return UndoableContext {
            setDefaultServiceEnabled(user, currentValue)
        }
    }

    @JvmOverloads
    fun defaultServiceEnabled(user: UserReference = TestApis.users().instrumented()): Boolean {
        return try {
            ShellCommand.builder("cmd content_suggestions")
                    .addOperand("get default-service-enabled")
                    .addOperand(user.id())
                    .executeAndParseOutput { s: String -> s.contains("true") }
        } catch (e: AdbException) {
            throw NeneException("Error checking default-service-enabled", e)
        }
    }

    @CanIgnoreReturnValue
    @JvmOverloads
    fun setTemporaryService(user: UserReference, component: ComponentReference): UndoableContext {
        TestApis.permissions().withPermission(MANAGE_CONTENT_SUGGESTIONS).use {
            contentSuggestionsManager.setTemporaryService(user.id(), component.flattenToString(),
                    TEMPORARY_SERVICE_DURATION_MS)
        }
        return UndoableContext(
                Runnable {
                    TestApis.permissions().withPermission(MANAGE_CONTENT_SUGGESTIONS).use {
                        TestApis.content().suggestions().clearTemporaryService(user)
                    }
                })
    }

    @JvmOverloads
    fun clearTemporaryService(user: UserReference = TestApis.users().instrumented()) {
        TestApis.permissions().withPermission(MANAGE_CONTENT_SUGGESTIONS).use {
            contentSuggestionsManager.resetTemporaryService(user.id())
        }
    }
}
