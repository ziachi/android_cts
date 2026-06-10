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

package android.devicepolicy.cts

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.RemoteAccountManager
import android.app.admin.flags.Flags.FLAG_SPLIT_CREATE_MANAGED_PROFILE_ENABLED
import android.os.UserHandle
import com.android.bedstead.accounts.accounts
import com.android.bedstead.accounts.annotations.EnsureHasAccountAuthenticator
import com.android.bedstead.enterprise.annotations.EnsureHasDevicePolicyManagerRoleHolder
import com.android.bedstead.enterprise.dpmRoleHolder
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser
import com.android.bedstead.multiuser.secondaryUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.permissions.CommonPermissions.COPY_ACCOUNTS
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.remoteaccountauthenticator.RemoteAccountAuthenticator
import com.android.compatibility.common.util.ApiTest
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequireFlagsEnabled(FLAG_SPLIT_CREATE_MANAGED_PROFILE_ENABLED)
@EnsureHasAccountAuthenticator
class AccountMigrationTest {

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.accounts.AccountManager#removeAccount"])
    fun removeAccount_hasRemoveAccountsPermission_removesAccount() {
        val previousNumberOfAccounts = sDeviceState.accounts().allAccounts().size
        val account = addAccount()

        val result = sDeviceState.dpmRoleHolder().accountManager().removeAccount(
                account,
            null,
            null,
            null
        ).result

        assertThat(result).isNotNull()
        assertThat(result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT)).isTrue()
        assertThat(sDeviceState.accounts().allAccounts().size).isEqualTo(previousNumberOfAccounts)
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasSecondaryUser
    @EnsureHasAccountAuthenticator(onUser = UserType.SECONDARY_USER)
    @ApiTest(apis = ["android.accounts.AccountManager#copyAccountToUser"])
    fun copyAccountToUser_hasCopyAccountsPermission_copiesAccountToOtherUser() {
        val account = addAccount()

        val result = sDeviceState.dpmRoleHolder().accountManager().copyAccountToUser(
                account,
            sDeviceState.initialUser().userHandle(),
                sDeviceState.secondaryUser().userHandle(),
            null,
            null
        ).result

        assertThat(result).isTrue()
        assertThat(
            sDeviceState.accounts(sDeviceState.secondaryUser())
                .containsAccount(account)
        ).isTrue()
    }

    @Test
    @EnsureDoesNotHavePermission(COPY_ACCOUNTS)
    @EnsureHasSecondaryUser
    @EnsureHasAccountAuthenticator(onUser = UserType.SECONDARY_USER)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = ["android.accounts.AccountManager#copyAccountToUser"])
    fun copyAccountToUser_noPermission_throwsSecurityException() {
        val account = addAccount()

        assertThrows(SecurityException::class.java) {
            sLocalAccountManager.copyAccountToUser(
                    account,
                sDeviceState.initialUser().userHandle(),
                    sDeviceState.secondaryUser().userHandle(),
                null,
                null
            ).result
        }
    }

    @Test
    @EnsureHasDevicePolicyManagerRoleHolder
    @EnsureHasSecondaryUser
    @EnsureHasAccountAuthenticator(onUser = UserType.SECONDARY_USER)
    fun migrateAccount_hasCopyAccountsAndRemoveAccountsPermission_migratesAccountToOtherUser() {
        val previousNumberOfAccounts = sDeviceState.accounts().allAccounts().size
        val account = addAccount()

        sDeviceState.dpmRoleHolder().accountManager().migrateAccount(
                account,
            sDeviceState.secondaryUser().userHandle()
        )

        assertThat(sDeviceState.accounts().allAccounts().size).isEqualTo(previousNumberOfAccounts)
        assertThat(
            sDeviceState.accounts(sDeviceState.secondaryUser())
                .containsAccount(account)
        ).isTrue()
    }

    private fun RemoteAccountManager.migrateAccount(account: Account, userHandle: UserHandle) {
        copyAccountToUser(
                account,
            sDeviceState.initialUser().userHandle(),
                userHandle,
            null,
            null
        ).result
        removeAccount(account, null, null, null).result
    }

    private fun RemoteAccountAuthenticator.containsAccount(account: Account): Boolean {
        for (accountReference in allAccounts()) {
            if (accountReference.account() == account) {
                return true
            }
        }
        return false
    }

    private fun addAccount() = sDeviceState.accounts().addAccount().add().account()

    companion object {
        @ClassRule
        @Rule
        @JvmField
        val sDeviceState = DeviceState()
        private val sLocalAccountManager = TestApis
                .context().instrumentedContext()
                .getSystemService(AccountManager::class.java)!!
    }
}
