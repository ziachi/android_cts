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

package android.app.appfunctions.cts

import android.Manifest
import android.app.admin.DevicePolicyManager.APP_FUNCTIONS_DISABLED
import android.app.admin.DevicePolicyManager.APP_FUNCTIONS_DISABLED_CROSS_PROFILE
import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.cts.AppFunctionUtils.executeAppFunctionAndWait
import android.app.appfunctions.cts.AppFunctionUtils.getAllRuntimeMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.getAllStaticMetadataPackages
import android.app.appfunctions.cts.AppFunctionUtils.setAppFunctionEnabled
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.CtsTestUtil.retryAssert
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForOperationCancellation
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForServiceOnCreate
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForServiceOnDestroy
import android.app.appsearch.GenericDocument
import android.content.Context
import android.os.CancellationSignal
import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.core.os.asOutcomeReceiver
import androidx.test.core.app.ApplicationProvider
import com.android.bedstead.enterprise.annotations.EnsureHasDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest
import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile
import com.android.bedstead.enterprise.dpc
import com.android.bedstead.enterprise.workProfile
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.Postsubmit
import com.android.bedstead.harrier.policies.AppFunctionsPolicy
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser
import com.android.bedstead.multiuser.annotations.parameterized.IncludeRunOnPrimaryUser
import com.android.bedstead.multiuser.annotations.parameterized.IncludeRunOnSecondaryUser
import com.android.bedstead.multiuser.secondaryUser
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.users.UserReference
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.SystemUtil
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlin.test.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_MANAGER)
class AppFunctionManagerTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val setCancellationTimeoutRule: DeviceConfigStateChangerRule =
        DeviceConfigStateChangerRule(
            context,
            "appfunctions",
            "execute_app_function_cancellation_timeout_millis",
            "3000",
        )

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var mManager: AppFunctionManager

    @Before
    fun setup() = doBlocking {
        TestAppFunctionServiceLifecycleReceiver.reset()
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        mManager = manager
        retryAssert {
            // Doing containsAtLeast instead of containsExactly here in case there app preloaded
            // apps having app functions.
            assertThat(getAllStaticMetadataPackages())
                .containsAtLeast(CURRENT_PKG, TEST_HELPER_PKG, TEST_SIDECAR_HELPER_PKG)
            // required permission because runtime metadata is only visible to owner package
            runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
                assertThat(getAllRuntimeMetadataPackages())
                    .containsAtLeast(CURRENT_PKG, TEST_HELPER_PKG, TEST_SIDECAR_HELPER_PKG)
            }
        }
    }

    @Before
    @After
    fun resetEnabledStatus() = doBlocking {
        setAppFunctionEnabled(mManager, "add", AppFunctionManager.APP_FUNCTION_STATE_DEFAULT)
        setAppFunctionEnabled(
            mManager,
            "add_disabledByDefault",
            AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
        )
    }

    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun checkManagerNotNull() {
        assertThat(mManager).isNotNull()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_failed_uncaughtClientExceptionMethod() =
        executeAppFunction_failed_uncaughtClientException_nonParam()

    /**
     * Same as the previous testcase, excluding Bedstead's enterprise annotations (unsupported in
     * host-side tests). Invoked by the host-side logging tests.
     */
    @Test
    @Throws(Exception::class)
    fun executeAppFunction_failed_uncaughtClientException_nonParam() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(
            CURRENT_PKG,
            "uncaughtClientException"
        ).build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_INVALID_ARGUMENT)
        assertThat(response.appFunctionException().errorMessage)
            .isEqualTo("Function does not exist")
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_onlyInvokeCallbackOnce() {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add_invokeCallbackTwice")
                .setParameters(parameters)
                .build()
        val blockingQueue = LinkedBlockingQueue<ExecuteAppFunctionResponse>()

        mManager.executeAppFunction(request, context.mainExecutor, CancellationSignal()) {
            e: ExecuteAppFunctionResponse ->
            blockingQueue.add(e)
        }

        val response = requireNotNull(blockingQueue.poll(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS))
        assertThat(
                response.resultDocument.getPropertyLong(
                    ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE
                )
            )
            .isEqualTo(3)

        // Each callback can only be invoked once.
        assertThat(blockingQueue.poll(SHORT_TIMEOUT_SECOND, TimeUnit.SECONDS)).isNull()
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_verifyCallingPackageFromRequest() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "noOp").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isTrue()
        assertThat(
                response
                    .getOrNull()!!
                    .resultDocument
                    .getPropertyString("TEST_PROPERTY_CALLING_PACKAGE")
            )
            .isEqualTo(CURRENT_PKG)
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_verifyPackageVisibilityFromRequest() = doBlocking {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val request = ExecuteAppFunctionRequest.Builder(TEST_HELPER_PKG, "noOp").build()

            val response = executeAppFunctionAndWait(mManager, request)

            assertThat(response.isSuccess).isTrue()
            assertThat(
                    response
                        .getOrNull()!!
                        .resultDocument
                        .getPropertyString("TEST_PROPERTY_CALLING_PACKAGE")
                )
                .isEqualTo(CURRENT_PKG)
            assertThat(
                    response
                        .getOrNull()!!
                        .resultDocument
                        .getPropertyBoolean("TEST_PROPERTY_HAS_CALLER_VISIBILITY")
                )
                .isEqualTo(true)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_crossUser_success() = executeAppFunction_crossUser_success_nonParam()

    /**
     * Same as the previous testcase, excluding Bedstead's enterprise annotations (unsupported in
     * host-side tests). Invoked by the host-side logging tests.
     */
    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasSecondaryUser
    @Throws(Exception::class)
    fun executeAppFunction_crossUser_success_nonParam() = doBlocking {
        runWithShellPermission(
            INTERACT_ACROSS_USERS_FULL_PERMISSION,
            EXECUTE_APP_FUNCTIONS_PERMISSION,
        ) {
            val secondaryUser = sDeviceState.secondaryUser()
            installExistingPackageAsUser(CURRENT_PKG, secondaryUser)
            retryAssert {
                assertThat(
                        getAllStaticMetadataPackages(
                            context.createContextAsUser(secondaryUser.userHandle(), 0)
                        )
                    )
                    .contains(CURRENT_PKG)
                assertThat(
                        getAllRuntimeMetadataPackages(
                            context.createContextAsUser(secondaryUser.userHandle(), 0)
                        )
                    )
                    .contains(CURRENT_PKG)
            }
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            mManager =
                context
                    .createContextAsUser(secondaryUser.userHandle(), 0)
                    .getSystemService(AppFunctionManager::class.java)
            val request =
                ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add")
                    .setParameters(parameters)
                    .build()

            val response = executeAppFunctionAndWait(mManager, request)

            assertThat(response.isSuccess).isTrue()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_crossUser_cannotInteractAcrossUser_fail() = doBlocking {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            assertFailsWith<SecurityException>() {
                val secondaryUser = sDeviceState.secondaryUser()
                installExistingPackageAsUser(CURRENT_PKG, secondaryUser)
                retryAssert {
                    assertThat(
                            getAllStaticMetadataPackages(
                                context.createContextAsUser(secondaryUser.userHandle(), 0)
                            )
                        )
                        .contains(CURRENT_PKG)
                    assertThat(
                            getAllRuntimeMetadataPackages(
                                context.createContextAsUser(secondaryUser.userHandle(), 0)
                            )
                        )
                        .contains(CURRENT_PKG)
                }
                mManager =
                    context
                        .createContextAsUser(secondaryUser.userHandle(), 0)
                        .getSystemService(AppFunctionManager::class.java)
            }
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_platformManager_platformAppFunctionService_success() =
        executeAppFunction_platformManager_platformAppFunctionService_success_nonParam()

    /**
     * Same as the previous testcase, excluding Bedstead's enterprise annotations (unsupported in
     * host-side tests). Invoked by the host-side logging tests.
     */
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_platformManager_platformAppFunctionService_success_nonParam() =
        doBlocking {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add").setParameters(parameters).build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isTrue()
        assertThat(
                response
                    .getOrNull()!!
                    .resultDocument
                    .getPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
            )
            .isEqualTo(3)
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_otherNonExistingTargetPackage() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder("other.package", "add").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        // Apps without the permission can only invoke functions from themselves.
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_DENIED)
        assertThat(response.appFunctionException().errorMessage)
            .endsWith("does not have permission to execute the appfunction")
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_otherExistingTargetPackage() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(TEST_HELPER_PKG, "someMethod").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_DENIED)
        // The error message from this and executeAppFunction_otherNonExistingOtherPackage must be
        // kept in sync. This verifies that a caller cannot tell whether a package is installed or
        // not by comparing the error messages.
        assertThat(response.appFunctionException().errorMessage)
            .endsWith("does not have permission to execute the appfunction")
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_throwsException() = executeAppFunction_throwsException_nonParam()

    /**
     * Same as the previous testcase, excluding Bedstead's enterprise annotations (unsupported in
     * host-side tests). Invoked by the host-side logging tests.
     */
    @Test
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_throwsException_nonParam() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "throwException").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_APP_UNKNOWN_ERROR)
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_onRemoteProcessKilled() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "kill").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_APP_UNKNOWN_ERROR)
        // The process that the service was just crashed. Validate the service is not created again.
        TestAppFunctionServiceLifecycleReceiver.reset()
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_success_async() = doBlocking {
        val parameters =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "addAsync")
                .setParameters(parameters)
                .build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isTrue()
        assertThat(
                response
                    .getOrNull()!!
                    .resultDocument
                    .getPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
            )
            .isEqualTo(3)
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_emptyPackage() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder("", "noOp").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_INVALID_ARGUMENT)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @RequireRunOnWorkProfile
    @EnsureHasNoDeviceOwner
    @Postsubmit(reason = "new test")
    @Throws(Exception::class)
    fun executeAppFunction_runInManagedProfileUnrestricted_success() = doBlocking {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add").setParameters(parameters).build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isTrue()
        assertThat(
                response
                    .getOrNull()!!
                    .resultDocument
                    .getPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
            )
            .isEqualTo(3)
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @RequireRunOnWorkProfile
    @EnsureHasNoDeviceOwner
    @PolicyAppliesTest(policy = [AppFunctionsPolicy::class])
    @Throws(Exception::class)
    fun executeAppFunction_runInManagedProfileRestricted_fail() = doBlocking {
        runWithShellPermission(
            // Permission required to create context as user.
            INTERACT_ACROSS_USERS_FULL_PERMISSION
        ) {
            val workProfileUser = sDeviceState.workProfile()
            val remoteDpm = sDeviceState.dpc().devicePolicyManager()
            val originalPolicy = remoteDpm.getAppFunctionsPolicy()
            try {
                remoteDpm.setAppFunctionsPolicy(APP_FUNCTIONS_DISABLED)
                assertThat(remoteDpm.getAppFunctionsPolicy()).isEqualTo(APP_FUNCTIONS_DISABLED)
                installExistingPackageAsUser(CURRENT_PKG, workProfileUser)
                retryAssert {
                    assertThat(
                            getAllStaticMetadataPackages(
                                context.createContextAsUser(workProfileUser.userHandle(), 0)
                            )
                        )
                        .contains(CURRENT_PKG)
                    assertThat(
                            getAllRuntimeMetadataPackages(
                                context.createContextAsUser(workProfileUser.userHandle(), 0)
                            )
                        )
                        .contains(CURRENT_PKG)
                }
                mManager =
                    context
                        .createContextAsUser(workProfileUser.userHandle(), 0)
                        .getSystemService(AppFunctionManager::class.java)
                val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add").build()

                val response = executeAppFunctionAndWait(mManager, request)

                assertThat(response.isSuccess).isFalse()
                assertThat(response.appFunctionException().errorCode)
                    .isEqualTo(AppFunctionException.ERROR_ENTERPRISE_POLICY_DISALLOWED)
            } finally {
                remoteDpm.setAppFunctionsPolicy(originalPolicy)
            }
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasWorkProfile
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_crossUser_targetWorkProfileUnrestricted_success() = doBlocking {
        runWithShellPermission(
            INTERACT_ACROSS_USERS_FULL_PERMISSION,
            EXECUTE_APP_FUNCTIONS_PERMISSION,
        ) {
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val workProfileUser = sDeviceState.workProfile()
            installExistingPackageAsUser(CURRENT_PKG, workProfileUser)
            retryAssert {
                assertThat(
                        getAllStaticMetadataPackages(
                            context.createContextAsUser(workProfileUser.userHandle(), 0)
                        )
                    )
                    .contains(CURRENT_PKG)
                assertThat(
                        getAllRuntimeMetadataPackages(
                            context.createContextAsUser(workProfileUser.userHandle(), 0)
                        )
                    )
                    .contains(CURRENT_PKG)
            }
            mManager =
                context
                    .createContextAsUser(workProfileUser.userHandle(), 0)
                    .getSystemService(AppFunctionManager::class.java)
            val request =
                ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add")
                    .setParameters(parameters)
                    .build()

            val response = executeAppFunctionAndWait(mManager, request)

            assertThat(response.isSuccess).isTrue()
            assertThat(
                    response
                        .getOrNull()!!
                        .resultDocument
                        .getPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
                )
                .isEqualTo(3)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasAdditionalUser
    @PolicyAppliesTest(policy = [AppFunctionsPolicy::class])
    @Throws(Exception::class)
    fun executeAppFunction_crossUser_targetWorkProfileRestricted_fail() = doBlocking {
        assumeFalse(TestApis.users().instrumented() == sDeviceState.additionalUser())
        runWithShellPermission(
            INTERACT_ACROSS_USERS_FULL_PERMISSION,
            EXECUTE_APP_FUNCTIONS_PERMISSION,
        ) {
            val additionalUser = sDeviceState.additionalUser()
            val remoteDpm = sDeviceState.dpc().devicePolicyManager()
            val originalPolicy = remoteDpm.getAppFunctionsPolicy()
            try {
                remoteDpm.setAppFunctionsPolicy(APP_FUNCTIONS_DISABLED_CROSS_PROFILE)
                assertThat(remoteDpm.getAppFunctionsPolicy())
                    .isEqualTo(APP_FUNCTIONS_DISABLED_CROSS_PROFILE)
                installExistingPackageAsUser(CURRENT_PKG, additionalUser)
                retryAssert {
                    assertThat(
                            getAllStaticMetadataPackages(
                                context.createContextAsUser(additionalUser.userHandle(), 0)
                            )
                        )
                        .contains(CURRENT_PKG)
                    assertThat(
                            getAllRuntimeMetadataPackages(
                                context.createContextAsUser(additionalUser.userHandle(), 0)
                            )
                        )
                        .contains(CURRENT_PKG)
                }
                mManager =
                    context
                        .createContextAsUser(additionalUser.userHandle(), 0)
                        .getSystemService(AppFunctionManager::class.java)
                val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add").build()

                val response = executeAppFunctionAndWait(mManager, request)

                assertThat(response.isSuccess).isFalse()
                assertThat(response.appFunctionException().errorCode)
                    .isEqualTo(AppFunctionException.ERROR_ENTERPRISE_POLICY_DISALLOWED)
            } finally {
                remoteDpm.setAppFunctionsPolicy(originalPolicy)
            }
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_disabledByDefault_fail() = doBlocking {
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add_disabledByDefault").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_DISABLED)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_disabledInRuntime_fail() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add").build()
        setAppFunctionEnabled(mManager, "add", AppFunctionManager.APP_FUNCTION_STATE_DISABLED)

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isFalse()
        assertThat(response.appFunctionException().errorCode)
            .isEqualTo(AppFunctionException.ERROR_DISABLED)
        assertServiceWasNotCreated()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_hasManagedProfileRunInPersonalProfile_success() = doBlocking {
        val request = ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "noOp").build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isTrue()
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_deviceOwnerUnrestricted_success() = doBlocking {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add").setParameters(parameters).build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isTrue()
        assertThat(
                response
                    .getOrNull()!!
                    .resultDocument
                    .getPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
            )
            .isEqualTo(3)
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasDeviceOwner
    @Throws(Exception::class)
    @PolicyAppliesTest(policy = [AppFunctionsPolicy::class])
    fun executeAppFunction_deviceOwnerRestricted_fail() = doBlocking {
        val remoteDpm = sDeviceState.dpc().devicePolicyManager()
        val originalPolicy = remoteDpm.getAppFunctionsPolicy()
        try {
            remoteDpm.setAppFunctionsPolicy(APP_FUNCTIONS_DISABLED)
            assertThat(remoteDpm.getAppFunctionsPolicy()).isEqualTo(APP_FUNCTIONS_DISABLED)
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add")
                    .setParameters(parameters)
                    .build()

            val response = executeAppFunctionAndWait(mManager, request)

            assertThat(response.isSuccess).isFalse()
            assertThat(response.appFunctionException().errorCode)
                .isEqualTo(AppFunctionException.ERROR_ENTERPRISE_POLICY_DISALLOWED)
            assertServiceWasNotCreated()
        } finally {
            remoteDpm.setAppFunctionsPolicy(originalPolicy)
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunction_largeTransactionSuccess() = doBlocking {
        val largeByteArray = ByteArray(1024 * 1024 + 100)
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyLong("a", 1)
                .setPropertyLong("b", 2)
                .setPropertyBytes("unused", largeByteArray)
                .build()

        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add").setParameters(parameters).build()

        val response = executeAppFunctionAndWait(mManager, request)

        assertThat(response.isSuccess).isTrue()
        assertThat(
                response
                    .getOrNull()!!
                    .resultDocument
                    .getPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
            )
            .isEqualTo(3)
        assertServiceDestroyed()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_withExecuteAppFunctionPermission_restrictCallersWithExecuteAppFunctionsFalse_success() =
        doBlocking {
            runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
                val parameters: GenericDocument =
                    GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                        .setPropertyLong("a", 1)
                        .setPropertyLong("b", 2)
                        .build()
                val request =
                    ExecuteAppFunctionRequest.Builder(
                            TEST_HELPER_PKG,
                            "addWithRestrictCallersWithExecuteAppFunctionsFalse",
                        )
                        .setParameters(parameters)
                        .build()

                val response = executeAppFunctionAndWait(mManager, request)

                assertThat(response.isSuccess).isTrue()
                assertThat(
                        response
                            .getOrNull()!!
                            .resultDocument
                            .getPropertyLong(ExecuteAppFunctionResponse.PROPERTY_RETURN_VALUE)
                    )
                    .isEqualTo(3)
            }
        }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_withExecuteAppFunctionPermission_functionMetadataNotFound_failsWithInvalidArgument() =
        doBlocking {
            runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
                val request =
                    ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "random_function").build()

                val response = executeAppFunctionAndWait(mManager, request)

                assertThat(response.isSuccess).isFalse()
                assertThat(response.appFunctionException().errorCode)
                    .isEqualTo(AppFunctionException.ERROR_FUNCTION_NOT_FOUND)
                assertThat(response.appFunctionException().errorMessage)
                    .contains("App function not found.")
            }
        }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_withExecuteAppFunctionPermission_functionMetadataNotFound_failsWithAppSearchException() =
        doBlocking {
            runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
                val request =
                    ExecuteAppFunctionRequest.Builder(TEST_HELPER_PKG, "random_function").build()

                val response = executeAppFunctionAndWait(mManager, request)

                assertThat(response.isSuccess).isFalse()
                assertThat(response.appFunctionException().errorCode)
                    .isEqualTo(AppFunctionException.ERROR_FUNCTION_NOT_FOUND)
            }
        }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_cancellationSignal_cancelled_unbind() {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "").build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "longRunningFunction")
                .setParameters(parameters)
                .build()
        val cancellationSignal = CancellationSignal()
        val blockingQueue = LinkedBlockingQueue<ExecuteAppFunctionResponse>()
        mManager.executeAppFunction(request, context.mainExecutor, cancellationSignal) {
            e: ExecuteAppFunctionResponse ->
            blockingQueue.add(e)
        }

        cancellationSignal.cancel()

        assertCancelListenerTriggered()
        assertServiceDestroyed()
        assertThat(blockingQueue).isEmpty()
    }

    @Throws(InterruptedException::class)
    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun executeAppFunction_cancellationSignal_cancellationTimedOut_unbind() {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "").build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "notInvokeCallback")
                .setParameters(parameters)
                .build()
        val cancellationSignal = CancellationSignal()
        val blockingQueue = LinkedBlockingQueue<ExecuteAppFunctionResponse>()
        mManager.executeAppFunction(request, context.mainExecutor, cancellationSignal) {
            e: ExecuteAppFunctionResponse ->
            blockingQueue.add(e)
        }

        cancellationSignal.cancel()

        assertCancelListenerTriggered()
        assertServiceDestroyed()
        assertThat(blockingQueue).isEmpty()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun isAppFunctionEnabled_functionDefaultEnabled() = doBlocking {
        assertThat(isAppFunctionEnabled("add")).isTrue()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_functionDefaultDisabled() = doBlocking {
        assertThat(isAppFunctionEnabled(functionIdentifier = "add_disabledByDefault")).isFalse()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_functionNotExist() = doBlocking {
        assertFailsWith<IllegalArgumentException>("function not found") {
            isAppFunctionEnabled(functionIdentifier = "notExist")
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_otherPackage_noPermission() = doBlocking {
        assertFailsWith<IllegalArgumentException>("function not found") {
            isAppFunctionEnabled(TEST_HELPER_PKG, functionIdentifier = "add_disabledByDefault")
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    fun isAppFunctionEnabled_otherPackage_hasExecuteAppFunctionPermission() = doBlocking {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            assertThat(isAppFunctionEnabled(TEST_HELPER_PKG, functionIdentifier = "add")).isTrue()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#setAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun setAppFunctionEnabled_functionDefaultEnabled() = doBlocking {
        val functionUnderTest = "add"
        // Check if the function is enabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()
        // Disable the function
        setAppFunctionEnabled(
            mManager,
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
        )
        // Confirm that the function is disabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isFalse()
        // Reset the enabled bit
        setAppFunctionEnabled(
            mManager,
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
        )
        // Confirm that the function is now enabled (default)
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()

        // Manually set the enabled bit to true
        setAppFunctionEnabled(
            mManager,
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
        )
        // Confirm that the function is still enabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#setAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun setAppFunctionEnabled_functionDefaultDisabled() = doBlocking {
        val functionUnderTest = "add_disabledByDefault"
        // Confirm that the function is disabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isFalse()
        // Enable the function
        setAppFunctionEnabled(
            mManager,
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
        )
        // Confirm that the function is enabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()
        // Reset the enabled bit
        setAppFunctionEnabled(
            mManager,
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_DEFAULT,
        )
        // Confirm that the function is now enabled (default)
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isFalse()

        // Manually set the enabled bit to true
        setAppFunctionEnabled(
            mManager,
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_ENABLED,
        )
        // Confirm that the function is still enabled
        assertThat(isAppFunctionEnabled(CURRENT_PKG, functionUnderTest)).isTrue()
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#setAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun setAppFunctionEnabled_functionNotExist() = doBlocking {
        val functionUnderTest = "notExist"

        assertFailsWith<IllegalArgumentException>("does not exist") {
            setAppFunctionEnabled(
                mManager,
                functionUnderTest,
                AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
            )
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunctionWithoutPermission_processStateIsNotBfgs() = doBlocking {
        val parameters: GenericDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "").build()
        val request =
            ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "runForever")
                .setParameters(parameters)
                .build()

        val cancellationSignal = CancellationSignal()
        try {
            mManager.executeAppFunction(request, Runnable::run, cancellationSignal) {}
            waitForServiceOnCreate(SHORT_TIMEOUT_SECOND, TimeUnit.SECONDS)
            assertProcessState(isBfgs = false)
        } finally {
            cancellationSignal.cancel()
        }
    }

    @ApiTest(apis = ["android.app.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @EnsureHasNoDeviceOwner
    @Throws(Exception::class)
    fun executeAppFunctionWithPermission_processStateIsBfgs() = doBlocking {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "").build()
            val request =
                ExecuteAppFunctionRequest.Builder(CURRENT_PKG, "runForever")
                    .setParameters(parameters)
                    .build()

            val cancellationSignal = CancellationSignal()
            try {
                mManager.executeAppFunction(request, Runnable::run, cancellationSignal) {}
                waitForServiceOnCreate(SHORT_TIMEOUT_SECOND, TimeUnit.SECONDS)
                assertProcessState(isBfgs = true)
            } finally {
                cancellationSignal.cancel()
            }
        }
    }

    private fun assertCancelListenerTriggered() {
        assertThat(waitForOperationCancellation(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS)).isTrue()
    }

    private suspend fun isAppFunctionEnabled(functionIdentifier: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            mManager.isAppFunctionEnabled(
                functionIdentifier,
                context.mainExecutor,
                continuation.asOutcomeReceiver(),
            )
        }

    private suspend fun isAppFunctionEnabled(
        targetPackage: String,
        functionIdentifier: String,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        mManager.isAppFunctionEnabled(
            functionIdentifier,
            targetPackage,
            context.mainExecutor,
            continuation.asOutcomeReceiver(),
        )
    }

    /** Verifies that the service is unbound by asserting the service was destroyed. */
    @Throws(InterruptedException::class)
    private fun assertServiceDestroyed() {
        assertThat(waitForServiceOnDestroy(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS)).isTrue()
    }

    /** Verifies that the service has never been created. */
    @Throws(InterruptedException::class)
    private fun assertServiceWasNotCreated() {
        assertThat(waitForServiceOnCreate(SHORT_TIMEOUT_SECOND, TimeUnit.SECONDS)).isFalse()
    }

    private fun installExistingPackageAsUser(packageName: String, user: UserReference) {
        val userId = user.id()
        assertThat(SystemUtil.runShellCommand("pm install-existing --user $userId $packageName"))
            .isEqualTo("Package $packageName installed for user: $userId\n")
    }

    /**
     * Asserts the state of the process running AppFunctionService is 'bfgs', i.e. bound foreground
     * service, or not.
     */
    private fun assertProcessState(isBfgs: Boolean) {
        val output = SystemUtil.runShellCommand("dumpsys activity lru")
        for (line in output.lines()) {
            if (
                line.contains("android.app.appfunctions.cts:appfunctions/u${UserHandle.myUserId()}")
            ) {
                if (isBfgs) {
                    assertThat(line).contains("BFGS")
                } else {
                    assertThat(line).doesNotContain("BFGS")
                }
                return
            }
        }
        fail(
            "Cannot find android.app.appfunctions.cts:appfunctions/u${UserHandle.myUserId()}" +
                " from dumpsys activity lru"
        )
    }

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()

        const val TEST_SIDECAR_HELPER_PKG: String = "android.app.appfunctions.cts.helper.sidecar"
        const val TEST_HELPER_PKG: String = "android.app.appfunctions.cts.helper"
        const val CURRENT_PKG: String = "android.app.appfunctions.cts"
        const val SHORT_TIMEOUT_SECOND: Long = 1
        const val LONG_TIMEOUT_SECOND: Long = 20
        const val EXECUTE_APP_FUNCTIONS_PERMISSION = Manifest.permission.EXECUTE_APP_FUNCTIONS
        const val INTERACT_ACROSS_USERS_PERMISSION = Manifest.permission.INTERACT_ACROSS_USERS
        const val INTERACT_ACROSS_USERS_FULL_PERMISSION =
            Manifest.permission.INTERACT_ACROSS_USERS_FULL
    }
}

private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

fun <T> Result<T>.appFunctionException(): AppFunctionException =
    exceptionOrNull() as AppFunctionException
