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
package com.android.bedstead.enterprise

import android.os.Build
import com.android.bedstead.accounts.AccountsComponent
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.harrier.AnnotationExecutorUtil
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.multiuser.UsersComponent
import com.android.bedstead.nene.TestApis.devicePolicy
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.devicepolicy.DeviceOwner
import com.android.bedstead.nene.devicepolicy.DeviceOwnerType
import com.android.bedstead.nene.devicepolicy.DevicePolicyController
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.Versions
import com.android.bedstead.remotedpc.RemoteDpc
import com.android.bedstead.testapp.TestAppProvider
import com.android.bedstead.testapp.TestAppQueryBuilder
import com.android.bedstead.testapps.TestAppsComponent
import java.util.stream.Collectors
import org.junit.AssumptionViolatedException

/**
 * Manages device owner for device state tests.
 *
 * @param locator provides access to other dependencies.
 */
class DeviceOwnerComponent(locator: BedsteadServiceLocator) : DeviceStateComponent {

    private val accountsComponent: AccountsComponent by locator
    private val usersComponent: UsersComponent by locator
    private val enterpriseComponent: EnterpriseComponent by locator
    private val profileOwnersComponent: ProfileOwnersComponent by locator
    private val testAppComponent: TestAppsComponent by locator
    private var hasChangedDeviceOwner = false
    private var originalDeviceOwner: DevicePolicyController? = null
    private var originalDeviceOwnerType: Int? = null
    private var hasChangedDeviceOwnerType = false
    private var deviceOwner: DevicePolicyController? = null

    /**
     * Gets currently saved device owner
     */
    fun getDeviceOwner() = deviceOwner

    private fun recordDeviceOwner() {
        originalDeviceOwner = devicePolicy().getDeviceOwner()
        originalDeviceOwnerType = (originalDeviceOwner as? DeviceOwner)?.getType()
    }

    /**
     * See [EnsureHasNoDeviceOwner]
     */
    fun ensureHasNoDeviceOwner() {
        val currentDeviceOwner = devicePolicy().getDeviceOwner() ?: return
        if (!hasChangedDeviceOwner) {
            recordDeviceOwner()
            hasChangedDeviceOwner = true
            hasChangedDeviceOwnerType = true
        }
        deviceOwner = null
        currentDeviceOwner.remove()
    }

    /**
     * See [EnsureHasDeviceOwner]
     */
    fun ensureHasDeviceOwner(
        failureMode: FailureMode = FailureMode.FAIL,
        isPrimary: Boolean = false,
        headlessDeviceOwnerType: EnsureHasDeviceOwner.HeadlessDeviceOwnerType =
            EnsureHasDeviceOwner.HeadlessDeviceOwnerType.NONE,
        affiliationIds: MutableSet<String> = mutableSetOf(),
        type: Int = DeviceOwnerType.DEFAULT,
        key: String = EnsureHasDeviceOwner.DEFAULT_KEY,
        dpcQuery: TestAppQueryBuilder = TestAppProvider().query()
    ) {
        // TODO(scottjonathan): Should support non-remotedpc device owner (default to remotedpc)
        var dpcQueryMutable = dpcQuery
        dpcQueryMutable.applyAnnotation(
            testAppComponent.additionalQueryParameters.getOrDefault(key, null)
        )
        if (dpcQueryMutable.isEmptyQuery) {
            dpcQueryMutable = TestAppProvider()
                    .query()
                    .wherePackageName()
                    .isEqualTo(RemoteDpc.REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX)
        }
        if (headlessDeviceOwnerType == EnsureHasDeviceOwner.HeadlessDeviceOwnerType.AFFILIATED &&
            users().isHeadlessSystemUserMode()) {
            affiliationIds.add("DEFAULT_AFFILIATED") // To ensure headless PO + DO are affiliated
        }
        val deviceOwnerUser: UserReference? =
            if (
                headlessDeviceOwnerType == EnsureHasDeviceOwner.HeadlessDeviceOwnerType.SINGLE_USER
            ) {
                users().main()
            } else {
                users().system()
            }
        if (isPrimary && enterpriseComponent.primaryPolicyManager != null &&
            deviceOwnerUser != enterpriseComponent.primaryPolicyManager?.user()
        ) {
            throw IllegalStateException(
                "Only one DPC can be marked as primary per test " +
                        "(current primary is $enterpriseComponent.mPrimaryPolicyManager)"
            )
        }
        val currentDeviceOwner = devicePolicy().getDeviceOwner()

        // if current device owner matches query, keep it as it is
        if (RemoteDpc.matchesRemoteDpcQuery(currentDeviceOwner, dpcQueryMutable)) {
            deviceOwner = currentDeviceOwner
        } else {
            // if there is no device owner, or current device owner is not a remote dpc
            val instrumentedUser = users().instrumented()
            if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
                // Prior to S we can't set device owner if there are other users on the device
                if (instrumentedUser.id() != 0) {
                    // If we're not on the system user we can't reach the required state
                    throw AssumptionViolatedException(
                        "Can't set Device Owner when running on non-system-user" +
                                " on this version of Android"
                    )
                }
                for (u: UserReference in users().all()) {
                    if ((u == instrumentedUser)) {
                        // Can't remove the user we're running on
                        continue
                    }
                    try {
                        usersComponent.removeAndRecordUser(u)
                    } catch (e: NeneException) {
                        AnnotationExecutorUtil.failOrSkip(
                            "Error removing user to prepare for DeviceOwner: $e",
                            failureMode
                        )
                    }
                }
            }
            removeAllNonTestUsers(failureMode)

            if (Versions.meetsMinimumSdkVersionRequirement(Versions.U)) {
                accountsComponent.ensureHasNoAccounts(
                    UserType.ANY,
                    allowPreCreatedAccounts = true,
                    FailureMode.FAIL
                )
            } else {
                // Prior to U this only checked the system user
                accountsComponent.ensureHasNoAccounts(
                    UserType.SYSTEM_USER,
                    allowPreCreatedAccounts = true,
                    FailureMode.FAIL
                )
            }
            if (deviceOwnerUser != null) {
                profileOwnersComponent.ensureHasNoProfileOwner(deviceOwnerUser)
            }
            if (!hasChangedDeviceOwner) {
                recordDeviceOwner()
                hasChangedDeviceOwner = true
                hasChangedDeviceOwnerType = true
            }
            deviceOwner = RemoteDpc.setAsDeviceOwner(dpcQueryMutable,
                deviceOwnerUser).devicePolicyController()
        }
        if (isPrimary) {
            enterpriseComponent.primaryPolicyManager =
                RemoteDpc.forDevicePolicyController(deviceOwner)
        }
        val deviceOwnerType = (deviceOwner as DeviceOwner).getType()
        if (deviceOwnerType != type) {
            if (!hasChangedDeviceOwnerType) {
                originalDeviceOwnerType = deviceOwnerType
                hasChangedDeviceOwnerType = true
            }
            (deviceOwner as DeviceOwner).setType(type)
        }
        if (type != DeviceOwnerType.FINANCED) {
            // API is not allowed to be called by a financed device owner.
            val remoteDpcForDeviceOwner =
                RemoteDpc.forDevicePolicyController(deviceOwner)
            remoteDpcForDeviceOwner.devicePolicyManager().setAffiliationIds(
                remoteDpcForDeviceOwner.componentName(),
                affiliationIds
            )
        }
        if ((headlessDeviceOwnerType == EnsureHasDeviceOwner.HeadlessDeviceOwnerType.AFFILIATED &&
                    users().isHeadlessSystemUserMode())) {
            // To simulate "affiliated" headless mode - we must also set the profile owner on the
            // initial user
            profileOwnersComponent.ensureHasProfileOwner(
                users().initial(),
                isPrimary = false,
                useParentInstance = false,
                affiliationIds = affiliationIds,
                key = key,
                dpcQuery = dpcQueryMutable,
                resolvedDpcTestApp = RemoteDpc
                        .forDevicePolicyController(deviceOwner)
                        .testApp()
            )
        }
    }

    private fun removeAllNonTestUsers(failureMode: FailureMode) {
        // We must remove all non-test users on all devices though
        // (except for the first 1 if headless and always the system user)
        var allowedNonTestUsers = if (users().isHeadlessSystemUserMode()) 1 else 0
        val instrumented = users().instrumented()
        for (u: UserReference in users()
                .all()
                .stream()
                .sorted(Comparator.comparing { u: Any -> (u == instrumented) }.reversed())
                .collect(Collectors.toList())
        ) {
            if (u.isSystem) {
                continue
            }
            if (u.isForTesting()) {
                continue
            }
            if (allowedNonTestUsers > 0) {
                allowedNonTestUsers--
                continue
            }
            if ((u == instrumented)) {
                // From U+ we limit non-for-testing users when setting device owner.
                if (Versions.meetsMinimumSdkVersionRequirement(Versions.U)) {
                    throw IllegalStateException(
                        "Cannot set Device Owner when running on a " +
                            "non-for-testing secondary user ($u)"
                    )
                } else {
                    continue
                }
            }
            try {
                usersComponent.removeAndRecordUser(u)
            } catch (e: NeneException) {
                AnnotationExecutorUtil.failOrSkip(
                    "Error removing user to prepare for DeviceOwner: $e",
                    failureMode
                )
            }
        }
    }

    override fun teardownShareableState() {
        if (hasChangedDeviceOwner) {
            val originalDeviceOwnerCopy = originalDeviceOwner
            if (originalDeviceOwnerCopy == null) {
                deviceOwner?.remove()
            } else if (originalDeviceOwner != deviceOwner) {
                deviceOwner?.remove()
                profileOwnersComponent.ensureHasNoProfileOwner(users().system())
                devicePolicy().setDeviceOwner(originalDeviceOwnerCopy.componentName())
            }
            originalDeviceOwnerType?.let {
                if (originalDeviceOwnerCopy != null) {
                    (originalDeviceOwnerCopy as DeviceOwner).setType(it)
                }
            }

            hasChangedDeviceOwner = false
            originalDeviceOwner = null
            hasChangedDeviceOwnerType = false
            originalDeviceOwnerType = null
        } else {
            // Device owner type changed but the device owner is the same.
            if (hasChangedDeviceOwnerType) {
                originalDeviceOwnerType?.let {
                    (deviceOwner as DeviceOwner).setType(it)
                }
                hasChangedDeviceOwnerType = false
                originalDeviceOwnerType = null
            }
        }
    }

    /**
     * See [com.android.bedstead.harrier.DeviceState.deviceOwner]
     */
    fun deviceOwner(): RemoteDpc {
        checkNotNull(deviceOwner) {
            ("No Harrier-managed device owner. This method should " +
                    "only be used when Harrier was used to set the Device Owner.")
        }
        check(RemoteDpc.isRemoteDpc(deviceOwner)) {
            ("The device owner is not a RemoteDPC." +
                    " You must use Nene to query for this device owner.")
        }

        return RemoteDpc.forDevicePolicyController(deviceOwner)
    }
}
