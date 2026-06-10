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
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.nene.TestApis.accounts
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class AccountsAnnotationExecutorTest {

    @EnsureHasAccountAuthenticator
    @Test
    fun ensureHasAccountAuthenticatorAnnotation_accountAuthenticatorIsInstalled() {
        assertThat(
            deviceState
                .accounts()
                .testApp()
                .pkg()
                .installedOnUser()
        ).isTrue()
    }

    @Test
    @EnsureHasAdditionalUser
    @EnsureHasAccountAuthenticator(onUser = UserType.ADDITIONAL_USER)
    fun ensureHasAccountAuthenticatorAnnotation_differentUser_accountAuthenticatorIsInstalledOnDifferentUser() {
        assertThat(
            deviceState
                .accounts(deviceState.additionalUser())
                .testApp()
                .pkg()
                .installedOnUser(deviceState.additionalUser())
        ).isTrue()
    }

    @EnsureHasAccount
    @Test
    fun ensureHasAccountAnnotation_accountExists() {
        assertThat(deviceState.accounts().allAccounts()).isNotEmpty()
    }

    @EnsureHasAccount
    @Test
    fun account_returnsAccount() {
        assertThat(deviceState.account()).isNotNull()
    }

    @EnsureHasAccount(key = "testKey")
    @Test
    fun account_withKey_returnsAccount() {
        assertThat(deviceState.account("testKey")).isNotNull()
    }

    @EnsureHasNoAccounts
    @Test
    fun ensureHasNoAccountsAnnotation_hasNoAccounts() {
        assertThat(accounts().all()).isEmpty()
    }

    @EnsureHasAccounts(EnsureHasAccount(), EnsureHasAccount())
    @Test
    fun ensureHasAccountsAnnotation_hasMultipleAccounts() {
        assertThat(deviceState.accounts().allAccounts().size).isGreaterThan(1)
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }
}
