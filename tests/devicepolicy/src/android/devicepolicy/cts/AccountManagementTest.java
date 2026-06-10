/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyIdentifiers.ACCOUNT_MANAGEMENT_DISABLED_POLICY;
import static android.app.admin.TargetUser.LOCAL_USER_ID;
import static android.devicepolicy.cts.utils.PolicyEngineUtils.TRUE_MORE_RESTRICTIVE;
import static android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS;

import static com.android.bedstead.accounts.AccountsDeviceStateExtensionsKt.account;
import static com.android.bedstead.accounts.AccountsDeviceStateExtensionsKt.accounts;
import static com.android.bedstead.accounts.annotations.EnsureHasAccountAuthenticator.ENSURE_HAS_ACCOUNT_AUTHENTICATOR_PRIORITY;
import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.OperationCanceledException;
import android.app.admin.AccountTypePolicyKey;
import android.app.admin.DevicePolicyManager;
import android.app.admin.PolicyState;
import android.app.admin.PolicyUpdateResult;
import android.content.Context;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.os.Bundle;
import android.os.UserManager;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.accounts.annotations.EnsureHasAccount;
import com.android.bedstead.accounts.annotations.EnsureHasAccountAuthenticator;
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.policies.AccountManagement;
import com.android.bedstead.harrier.policies.DisallowModifyAccounts;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.accounts.AccountReference;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(BedsteadJUnit4.class)
@EnsureHasAccountAuthenticator
public final class AccountManagementTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);
    private static final AccountManager sLocalAccountManager =
            sContext.getSystemService(AccountManager.class);

    private static final String ACCOUNT_TYPE = "android.devicepolicy.cts";
    private static final String AUTH_TOKEN_TYPE = "testAuthTokenType";
    private static final String[] REQUIRED_FEATURES =
            new String[]{"testRequiredFeature1", "testRequiredFeature2"};
    private static final String REQUIRED_FEATURES_STR = "testRequiredFeature1;testRequiredFeature2";


    private AccountManager mAccountManager;

    @Before
    public void setUp() {
        mAccountManager = sContext.getSystemService(AccountManager.class);
    }

    // TODO: Fill out PolicyApplies and PolicyDoesNotApply tests

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void getAccountTypesWithManagementDisabled_emptyByDefault() {
        assertThat(dpc(sDeviceState).devicePolicyManager().getAccountTypesWithManagementDisabled())
                .isEmpty();
    }

    @Postsubmit(reason = "new test")
    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = AccountManagement.class, includeNonDeviceAdminStates = false)
    public void setAccountTypesWithManagementDisabled_invalidAdmin_throwsException() {
        Exception exception = assertThrows(Exception.class, () ->
                dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                        dpc(sDeviceState).componentName(),
                        accounts(sDeviceState).accountType(), /* disabled= */ false));

        assertTrue("Expected OperationCanceledException or SecurityException to be thrown",
                (exception instanceof OperationCanceledException)
                        || (exception instanceof SecurityException));
    }

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_disableAccountType_works() {
        try {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(),
                    accounts(sDeviceState).accountType(),
                    /* disabled= */ true);

            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().contains(
                    accounts(sDeviceState).accountType());
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(),
                    accounts(sDeviceState).accountType(), /* disabled= */ false);
        }
    }

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_addSameAccountTypeTwice_presentOnlyOnce() {
        try {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(),
                    accounts(sDeviceState).accountType(),
                    /* disabled= */ true);
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(),
                    accounts(sDeviceState).accountType(),
                    /* disabled= */ true);

            assertThat(
                    Arrays.stream(dpc(sDeviceState).devicePolicyManager()
                                    .getAccountTypesWithManagementDisabled())
                            .filter(s -> s.equals(accounts(sDeviceState).accountType()))
                            .count()).isEqualTo(1);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(),
                    accounts(sDeviceState).accountType(),
                    /* disabled= */ false);
        }
    }

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_disableThenEnable_notDisabled() {
        dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                dpc(sDeviceState).componentName(),
                accounts(sDeviceState).accountType(),
                /* disabled= */ true);
        dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                dpc(sDeviceState).componentName(),
                accounts(sDeviceState).accountType(),
                /* disabled= */ false);

        assertThat(dpc(sDeviceState).devicePolicyManager().getAccountTypesWithManagementDisabled())
                .asList().doesNotContain(
                        accounts(sDeviceState).accountType());
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    // Not passing for permission based caller as AccountManagerService is special casing DO/PO
    public void addAccount_fromDpcWithAccountManagementDisabled_accountAdded()
            throws Exception {
        Assume.assumeTrue("Only makes sense on DPC user",
                TestApis.users().instrumented().equals(dpc(sDeviceState).user()));
        try {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(),
                    accounts(sDeviceState).accountType(),
                    /* disabled= */ true);

            // Management is disabled, but the DO/PO is still allowed to use the APIs

            try (AccountReference account = TestApis.accounts().wrap(
                            dpc(sDeviceState).user(),
                            dpc(sDeviceState).accountManager())
                    .addAccount()
                    .type(accounts(sDeviceState).accountType())
                    .add()) {
                assertThat(accounts(sDeviceState).allAccounts()).contains(account);
            }
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(),
                    accounts(sDeviceState).accountType(),
                    /* disabled= */ false);
        }
    }

    // TODO: Rewrite DISALLOW_MODIFY_ACCOUNTS tests using new user restriction test structure

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisallowModifyAccounts.class)
    // Not passing for permission based caller as AccountManagerService is special casing DO/PO
    public void addAccount_fromDpcWithDisallowModifyAccountsRestriction_accountAdded()
            throws Exception {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_MODIFY_ACCOUNTS);

            // Management is disabled, but the DO/PO is still allowed to use the APIs
            try (AccountReference account = TestApis.accounts().wrap(
                            dpc(sDeviceState).user(),
                            dpc(sDeviceState).accountManager())
                    .addAccount()
                    .type(accounts(sDeviceState).accountType())
                    .add()) {
                assertThat(accounts(sDeviceState).allAccounts()).contains(account);
            }
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_MODIFY_ACCOUNTS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisallowModifyAccounts.class)
    // Not passing for permission based caller as AccountManagerService is special casing DO/PO
    public void removeAccount_fromDpcWithDisallowModifyAccountsRestriction_accountRemoved()
            throws Exception {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_MODIFY_ACCOUNTS);

            // Management is disabled, but the DO/PO is still allowed to use the APIs
            AccountReference account = TestApis.accounts().wrap(
                            dpc(sDeviceState).user(), dpc(sDeviceState).accountManager())
                    .addAccount()
                    .type(accounts(sDeviceState).accountType())
                    .add();

            Bundle result = dpc(sDeviceState).accountManager().removeAccount(
                            account.account(),
                            /* activity= */ null,
                            /* callback= */  null,
                            /* handler= */ null)
                    .getResult();

            assertThat(result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT)).isTrue();
            assertThat(accounts(sDeviceState).allAccounts()).doesNotContain(account);
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_MODIFY_ACCOUNTS);
        }
    }

    @PolicyAppliesTest(policy = DisallowModifyAccounts.class)
    public void addAccount_withDisallowModifyAccountsRestriction_throwsException() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), UserManager.DISALLOW_MODIFY_ACCOUNTS);

            assertThrows(OperationCanceledException.class, () ->
                    mAccountManager.addAccount(
                            accounts(sDeviceState).accountType(),
                            /* authTokenType= */ null,
                            /* requiredFeatures= */ null,
                            /* addAccountOptions= */ null,
                            /* activity= */ null,
                            /* callback= */ null,
                            /* handler= */ null).getResult());
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), UserManager.DISALLOW_MODIFY_ACCOUNTS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DisallowModifyAccounts.class)
    @EnsureDoesNotHaveUserRestriction(CommonUserRestrictions.DISALLOW_MODIFY_ACCOUNTS)
    public void removeAccount_withDisallowModifyAccountsRestriction_throwsException()
            throws Exception {
        AccountReference account = null;
        try {
            account = accounts(sDeviceState).addAccount().add();

            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), UserManager.DISALLOW_MODIFY_ACCOUNTS);

            NeneException e = assertThrows(NeneException.class, account::remove);
            assertThat(e).hasCauseThat().isInstanceOf(OperationCanceledException.class);
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), UserManager.DISALLOW_MODIFY_ACCOUNTS);

            if (account != null) {
                account.remove();
            }
        }
    }

    @PolicyAppliesTest(policy = AccountManagement.class)
    public void addAccount_withAccountManagementDisabled_throwsException() {
        try {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(),
                    accounts(sDeviceState).accountType(),
                    /* disabled= */ true);

            assertThrows(Exception.class, () ->
                    accounts(sDeviceState).addAccount().add());
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(),
                    accounts(sDeviceState).accountType(),
                    /* disabled= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(
            policy = AccountManagement.class,
            // Make sure @EnsureHasAccounts is invoked after annotations that subsequently call
            // @EnsureHasNoAccounts (which otherwise removes the account created by
            // @EnsureHasAccounts before it's expected).
            priority = ENSURE_HAS_ACCOUNT_AUTHENTICATOR_PRIORITY - 1)
    @EnsureHasAccount
    @EnsureDoesNotHaveUserRestriction(CommonUserRestrictions.DISALLOW_MODIFY_ACCOUNTS)
    public void removeAccount_withAccountManagementDisabled_throwsException() {
        try {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(),
                    account(sDeviceState).type(),
                    /* disabled= */ true);

            NeneException e = assertThrows(NeneException.class, () ->
                    account(sDeviceState).remove());
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(),
                    accounts(sDeviceState).accountType(),
                    /* disabled= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_accountTypeTooLong_throws() {
        // String too long for account type, cannot be serialized correctly
        String badAccountType = new String(new char[100000]).replace('\0', 'A');
        assertThrows(IllegalArgumentException.class, () ->
                dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                        dpc(sDeviceState).componentName(), badAccountType, /* disabled= */ false));
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @PolicyAppliesTest(policy = AccountManagement.class)
    public void getDevicePolicyState_setAccountManagementDisabled_returnsPolicy() {
        try {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(), accounts(sDeviceState).accountType(),
                    /* disabled= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            accounts(sDeviceState).accountType()),
                    TestApis.users().instrumented().userHandle());

            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(), accounts(sDeviceState).accountType(),
                    /* disabled= */ false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled"})
    // TODO: enable after adding the broadcast receiver to relevant test apps.
//    @PolicyAppliesTest(policy = AccountManagement.class)
    @EnsureHasDeviceOwner(isPrimary = true)
    public void policyUpdateReceiver_setAccountManagementDisabled_receivedPolicySetBroadcast() {
        try {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(), accounts(sDeviceState).accountType(),
                    /* disabled= */ true);

            PolicySetResultUtils.assertPolicySetResultReceived(
                    sDeviceState,
                    ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                    PolicyUpdateResult.RESULT_POLICY_SET, LOCAL_USER_ID, new Bundle());
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(), accounts(sDeviceState).accountType(),
                    /* disabled= */ false);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled",
            "android.app.admin.DevicePolicyManager#getDevicePolicyState"})
    @CanSetPolicyTest(policy = AccountManagement.class, singleTestOnly = true)
    public void getDevicePolicyState_setAccountManagementDisabled_returnsCorrectResolutionMechanism() {
        try {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(), accounts(sDeviceState).accountType(),
                    /* disabled= */ true);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            accounts(sDeviceState).accountType()),
                    TestApis.users().instrumented().userHandle());

            assertThat(PolicyEngineUtils.getMostRestrictiveBooleanMechanism(policyState)
                    .getMostToLeastRestrictiveValues()).isEqualTo(TRUE_MORE_RESTRICTIVE);
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(), accounts(sDeviceState).accountType(),
                    /* disabled= */ false);
        }
    }

    @Ignore("b/312605194 Ignore until test failure is root caused")
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = AccountManagement.class)
    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#setAccountManagementDisabled",
            "android.app.admin.DevicePolicyManager#getAccountTypesWithManagementDisabled"})
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    public void setAccountManagementDisabled_policyMigration_works() {
        try {
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "false");
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(),
                    accounts(sDeviceState).accountType(),
                    /* disabled= */ true);

            sLocalDevicePolicyManager.triggerDevicePolicyEngineMigration(true);
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, "true");

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new AccountTypePolicyKey(
                            ACCOUNT_MANAGEMENT_DISABLED_POLICY,
                            accounts(sDeviceState).accountType()),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThat(dpc(sDeviceState).devicePolicyManager()
                    .getAccountTypesWithManagementDisabled()).asList().contains(
                    accounts(sDeviceState).accountType());
            assertThrows(Exception.class, () ->
                    accounts(sDeviceState).addAccount().add());
        } finally {
            dpc(sDeviceState).devicePolicyManager().setAccountManagementDisabled(
                    dpc(sDeviceState).componentName(),
                    accounts(sDeviceState).accountType(),
                    /* disabled= */ false);
//            TestApis.flags().set(
//                    NAMESPACE_DEVICE_POLICY_MANAGER, ENABLE_DEVICE_POLICY_ENGINE_FLAG, null);
        }
    }

    @ApiTest(apis = {"android.app.admin.AccountManager#startAddAccountSession"})
    @Test
    public void startAddAccountSession_isLogged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            AccountManagerFuture<Bundle> future =
                    sLocalAccountManager.startAddAccountSession(
                            accounts(sDeviceState).accountType(),
                            AUTH_TOKEN_TYPE,
                            REQUIRED_FEATURES,
                            /* options= */ null,
                            /* activity= */ null,
                            /* callback= */ null,
                            /* handler= */ null);
            waitForFutureDone(future);

            // asserting for an empty admin package name, since we do not have an
            // admin here.
            assertThat(metrics.query()
                    .whereAdminPackageName().isEqualTo("")
                    .whereType().isEqualTo(EventId.ADD_ACCOUNT_VALUE)
                    .whereStrings().contains(accounts(sDeviceState).accountType(),
                            ACCOUNT_TYPE, AUTH_TOKEN_TYPE,
                            REQUIRED_FEATURES_STR)
            ).wasLogged();
        }
    }

    private static void waitForFutureDone(AccountManagerFuture<Bundle> future) {
        while (!future.isDone()) {
            // we make sure the task is completed before asserting.
        }
    }
}
