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
package com.android.bedstead.accounts

import com.android.bedstead.accounts.annotations.EnsureHasAccount
import com.android.bedstead.accounts.annotations.EnsureHasAccountAuthenticator
import com.android.bedstead.accounts.annotations.EnsureHasAccounts
import com.android.bedstead.accounts.annotations.EnsureHasNoAccounts
import com.android.bedstead.harrier.AnnotationExecutor
import com.android.bedstead.harrier.BedsteadServiceLocator

/**
 * [AnnotationExecutor] for accounts annotations
 */
@Suppress("unused")
class AccountsAnnotationExecutor(locator: BedsteadServiceLocator) : AnnotationExecutor {

    private val accountsComponent: AccountsComponent by locator

    override fun applyAnnotation(annotation: Annotation): Unit = annotation.run {
        when (this) {
            is EnsureHasAccount -> accountsComponent.ensureHasAccount(onUser, key, features)
            is EnsureHasAccounts -> accountsComponent.ensureHasAccounts(value)
            is EnsureHasNoAccounts -> accountsComponent.ensureHasNoAccounts(
                onUser,
                allowPreCreatedAccounts,
                failureMode
            )

            is EnsureHasAccountAuthenticator ->
                accountsComponent.ensureHasAccountAuthenticator(onUser)
        }
    }
}
