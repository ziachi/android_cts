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

import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.accounts.annotations.EnsureHasAccount
import com.android.bedstead.nene.accounts.AccountReference
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.remoteaccountauthenticator.RemoteAccountAuthenticator

private fun DeviceState.accountsComponent() = getDependency(AccountsComponent::class.java)

/**
 * Access harrier-managed accounts on the instrumented user.
 */
fun DeviceState.accounts(): RemoteAccountAuthenticator = accountsComponent().accounts()

/**
 * Access harrier-managed accounts on the given user.
 */
fun DeviceState.accounts(user: UserType): RemoteAccountAuthenticator =
    accountsComponent().accounts(user)

/**
 * Access harrier-managed accounts on the given user.
 */
fun DeviceState.accounts(user: UserReference): RemoteAccountAuthenticator =
    accountsComponent().accounts(user)

/**
 * Get the default account defined with [EnsureHasAccount].
 */
fun DeviceState.account(): AccountReference = accountsComponent().account()

/**
 * Get the account defined with [EnsureHasAccount] with a given key.
 */
fun DeviceState.account(key: String): AccountReference = accountsComponent().account(key)
