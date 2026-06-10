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

import android.app.admin.DevicePolicyManager.ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_ALLOWED
import android.app.admin.DevicePolicyManager.ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_DISALLOWED
import com.android.bedstead.accounts.annotations.EnsureHasAccount
import com.android.bedstead.accounts.annotations.EnsureHasAccountAuthenticator
import com.android.bedstead.accounts.annotations.EnsureHasAccounts
import com.android.bedstead.accounts.annotations.EnsureHasNoAccounts
import com.android.bedstead.harrier.AnnotationExecutorUtil.failOrSkip
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.harrier.components.UserTypeResolver
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.accounts.AccountReference
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.remoteaccountauthenticator.RemoteAccountAuthenticator
import com.android.bedstead.remoteaccountauthenticator.RemoteAccountAuthenticator.REMOTE_ACCOUNT_AUTHENTICATOR_TEST_APP
import com.android.bedstead.testapps.TestAppsComponent
import com.google.errorprone.annotations.CanIgnoreReturnValue

/**
 * Contains logic specific to accounts for Bedstead tests using [DeviceState] rule
 *
 * @param locator provides access to other dependencies.
 */
class AccountsComponent(locator: BedsteadServiceLocator) : DeviceStateComponent {

    private val createdAccounts: MutableSet<AccountReference> = mutableSetOf()
    private val accounts: MutableMap<String, AccountReference> = mutableMapOf()
    private val accountAuthenticators:
            MutableMap<UserReference, RemoteAccountAuthenticator> = mutableMapOf()
    private val testAppsComponent: TestAppsComponent by locator
    private val userTypeResolver: UserTypeResolver by locator

    /**
     * Get the default account defined with [EnsureHasAccount].
     */
    fun account(): AccountReference {
        return account(EnsureHasAccount.DEFAULT_ACCOUNT_KEY)
    }

    /**
     * Get the account defined with [EnsureHasAccount] with a given key.
     */
    fun account(key: String): AccountReference {
        return accounts[key] ?: throw IllegalStateException("No account for key $key")
    }

    /**
     * Access harrier-managed accounts on the instrumented user.
     */
    fun accounts(): RemoteAccountAuthenticator {
        return accounts(users().instrumented())
    }

    /**
     * Access harrier-managed accounts on the given user.
     */
    fun accounts(user: UserType): RemoteAccountAuthenticator {
        return accounts(userTypeResolver.toUser(user))
    }

    /**
     * Access harrier-managed accounts on the given user.
     */
    fun accounts(user: UserReference): RemoteAccountAuthenticator {
        return accountAuthenticators[user] ?: throw IllegalStateException(
            "No Harrier-Managed account authenticator on user $user. " +
                    "Did you use @EnsureHasAccountAuthenticator or @EnsureHasAccount?"
        )
    }

    /**
     * See [EnsureHasAccountAuthenticator]
     */
    fun ensureHasAccountAuthenticator(onUser: UserType) {
        val user: UserReference = userTypeResolver.toUser(onUser)
        // We don't use .install() so we can rely on the default testapp sharing/uninstall logic
        testAppsComponent.ensureTestAppInstalled(
            REMOTE_ACCOUNT_AUTHENTICATOR_TEST_APP,
            user
        )

        accountAuthenticators[user] = RemoteAccountAuthenticator.install(user)
    }

    /**
     * See [EnsureHasAccounts]
     */
    fun ensureHasAccounts(accounts: Array<EnsureHasAccount>) {
        val ignoredAccounts: MutableSet<AccountReference> = mutableSetOf()

        accounts.forEach {
            ignoredAccounts.add(
                ensureHasAccount(it.onUser, it.key, it.features, ignoredAccounts)
            )
        }
    }

    /**
     * See [EnsureHasAccount]
     */
    @CanIgnoreReturnValue
    fun ensureHasAccount(
        onUser: UserType,
        key: String,
        features: Array<String>,
        ignoredAccounts: Set<AccountReference> = emptySet()
    ): AccountReference {
        ensureHasAccountAuthenticator(onUser)

        val account = accounts(onUser).allAccounts().firstOrNull {
            !ignoredAccounts.contains(it)
        }

        if (account != null) {
            accounts(onUser).setFeatures(account, features.toSet())
            accounts[key] = account
            devicePolicy().calculateHasIncompatibleAccounts()
            return account
        }

        val createdAccount = accounts(onUser).addAccount()
            .features(features.toSet())
            .add()
        createdAccounts.add(createdAccount)
        accounts[key] = createdAccount
        devicePolicy().calculateHasIncompatibleAccounts()
        return createdAccount
    }

    /**
     * See [EnsureHasNoAccounts]
     */
    fun ensureHasNoAccounts(
        userType: UserType,
        allowPreCreatedAccounts: Boolean,
        failureMode: FailureMode
    ) {
        if (userType == UserType.ANY) {
            users().all().forEach { user ->
                ensureHasNoAccounts(user, allowPreCreatedAccounts, failureMode)
            }
        } else {
            ensureHasNoAccounts(
                userTypeResolver.toUser(userType),
                allowPreCreatedAccounts,
                failureMode
            )
        }
    }

    /**
     * See [EnsureHasNoAccounts]
     */
    fun ensureHasNoAccounts(
        user: UserReference,
        allowPreCreatedAccounts: Boolean,
        failureMode: FailureMode
    ) {
        if (REMOTE_ACCOUNT_AUTHENTICATOR_TEST_APP.pkg().installedOnUser(user)) {
            user.start() // The user has to be started to remove accounts
            RemoteAccountAuthenticator.install(user).allAccounts().forEach {
                it.remove()
            }
        }

        var accounts = TestApis.accounts().all(user)

        // If allowPreCreatedAccounts is enabled, that means it's okay to have
        // pre created accounts on the device.
        // Now to EnsureHasNoAccounts we will only check that there are no non-pre created accounts.
        // Non pre created accounts either have ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_DISALLOWED
        // or do not have ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_ALLOWED
        if (allowPreCreatedAccounts) {
            accounts = accounts.filter {
                !it.hasFeature(ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_ALLOWED) ||
                        it.hasFeature(ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_DISALLOWED)
            }.toSet()
        }

        if (accounts.isNotEmpty()) {
            failOrSkip(
                "Expected no user created accounts on user $user" +
                        " but there was some that could not be removed.",
                failureMode
            )
        }

        devicePolicy().calculateHasIncompatibleAccounts()
    }

    override fun teardownNonShareableState() {
        accounts.clear()
        accountAuthenticators.clear()
    }

    override fun teardownShareableState() {
        if (createdAccounts.isNotEmpty()) {
            createdAccounts.forEach {
                it.remove()
            }
            devicePolicy().calculateHasIncompatibleAccounts()
        }
    }
}
