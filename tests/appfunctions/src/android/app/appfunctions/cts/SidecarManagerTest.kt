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
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionManager.EnabledState
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.cts.AppFunctionUtils.executeAppFunctionAndWait
import android.app.appfunctions.cts.AppFunctionUtils.setAppFunctionEnabled
import android.app.appfunctions.flags.Flags
import android.app.appfunctions.testutils.CtsTestUtil.runWithShellPermission
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForOperationCancellation
import android.app.appfunctions.testutils.TestAppFunctionServiceLifecycleReceiver.waitForServiceOnDestroy
import android.app.appsearch.GenericDocument
import android.content.Context
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.core.os.asOutcomeReceiver
import androidx.test.core.app.ApplicationProvider
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.multiuser.annotations.parameterized.IncludeRunOnPrimaryUser
import com.android.bedstead.multiuser.annotations.parameterized.IncludeRunOnSecondaryUser
import com.android.compatibility.common.util.ApiTest
import com.android.extensions.appfunctions.AppFunctionException as SidecarAppFunctionException
import com.android.extensions.appfunctions.AppFunctionManager as SidecarAppFunctionManager
import com.android.extensions.appfunctions.ExecuteAppFunctionRequest as SidecarExecuteAppFunctionRequest
import com.android.extensions.appfunctions.ExecuteAppFunctionResponse as SidecarExecuteAppFunctionResponse
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_MANAGER)
class SidecarManagerTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var mManager: AppFunctionManager

    @Before
    fun setup() = doBlocking {
        TestAppFunctionServiceLifecycleReceiver.reset()
        val manager = context.getSystemService(AppFunctionManager::class.java)
        assumeNotNull(manager)
        mManager = manager
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

    @ApiTest(apis = ["com.android.extensions.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_sidecarManager_platformAppFunctionService_success() = doBlocking {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                SidecarExecuteAppFunctionRequest.Builder(CURRENT_PKG, "add")
                    .setParameters(parameters)
                    .build()

            val response = sidecarExecuteFunction(context, request)

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
    }

    @ApiTest(apis = ["com.android.extensions.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_sidecarManager_verifyCallingPackageFromRequest() = doBlocking {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val request = SidecarExecuteAppFunctionRequest.Builder(CURRENT_PKG, "noOp").build()

            val response = sidecarExecuteFunction(context, request)

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
    }

    @ApiTest(apis = ["com.android.extensions.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_cancellationSignalReceived_unbind() = doBlocking {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                SidecarExecuteAppFunctionRequest.Builder(CURRENT_PKG, "longRunningFunction")
                    .setParameters(parameters)
                    .build()

            val cancellationSignal = CancellationSignal()
            val blockingQueue = LinkedBlockingQueue<SidecarExecuteAppFunctionResponse>()
            SidecarAppFunctionManager(context).executeAppFunction(
                request,
                context.mainExecutor,
                cancellationSignal,
            ) { response: SidecarExecuteAppFunctionResponse ->
                blockingQueue.add(response)
            }
            cancellationSignal.cancel()

            assertCancelListenerTriggered()
            assertThat(blockingQueue).isEmpty()
            assertServiceDestroyed()
        }
    }

    @ApiTest(apis = ["com.android.extensions.appfunctions.AppFunctionManager#executeAppFunction"])
    @Test
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_sidecarManager_sidecarAppFunctionService_success() = doBlocking {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                SidecarExecuteAppFunctionRequest.Builder(TEST_SIDECAR_HELPER_PKG, "add")
                    .setParameters(parameters)
                    .build()

            val response = sidecarExecuteFunction(context, request)

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
    @EnsureHasNoDeviceOwner
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    @Throws(Exception::class)
    fun executeAppFunction_platformManager_sidecarAppFunctionService_success() = doBlocking {
        runWithShellPermission(EXECUTE_APP_FUNCTIONS_PERMISSION) {
            val parameters: GenericDocument =
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyLong("a", 1)
                    .setPropertyLong("b", 2)
                    .build()
            val request =
                ExecuteAppFunctionRequest.Builder(TEST_SIDECAR_HELPER_PKG, "add")
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

    @ApiTest(apis = ["com.android.extensions.appfunctions.AppFunctionManager#isAppFunctionEnabled"])
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun isAppFunctionEnabled_sidecar() = doBlocking {
        assertThat(sidecarIsAppFunctionEnabled(context, CURRENT_PKG, "add")).isTrue()
    }

    @ApiTest(
        apis = ["com.android.extensions.appfunctions.AppFunctionManager#setAppFUnctionEnabled"]
    )
    @Test
    @IncludeRunOnSecondaryUser
    @IncludeRunOnPrimaryUser
    fun setAppFunctionEnabled_sidecar() = doBlocking {
        val functionUnderTest = "add"
        assertThat(sidecarIsAppFunctionEnabled(context, functionUnderTest)).isTrue()
        sidecarSetAppFunctionEnabled(
            context,
            functionUnderTest,
            AppFunctionManager.APP_FUNCTION_STATE_DISABLED,
        )

        assertThat(sidecarIsAppFunctionEnabled(context, CURRENT_PKG, functionUnderTest)).isFalse()
    }

    private companion object {
        @JvmField @ClassRule @Rule val sDeviceState: DeviceState = DeviceState()

        const val TEST_SIDECAR_HELPER_PKG: String = "android.app.appfunctions.cts.helper.sidecar"
        const val TEST_HELPER_PKG: String = "android.app.appfunctions.cts.helper"
        const val CURRENT_PKG: String = "android.app.appfunctions.cts"
        const val EXECUTE_APP_FUNCTIONS_PERMISSION =
            Manifest.permission.EXECUTE_APP_FUNCTIONS
        const val LONG_TIMEOUT_SECOND: Long = 5

        suspend fun sidecarExecuteFunction(
            context: Context,
            request: SidecarExecuteAppFunctionRequest,
            cancellationSignal: CancellationSignal = CancellationSignal(),
        ): Result<SidecarExecuteAppFunctionResponse> {
            return suspendCancellableCoroutine { continuation ->
                SidecarAppFunctionManager(context)
                    .executeAppFunction(
                        request,
                        Runnable::run,
                        cancellationSignal,
                        object :
                            OutcomeReceiver<
                                SidecarExecuteAppFunctionResponse,
                                SidecarAppFunctionException,
                            > {
                            override fun onResult(result: SidecarExecuteAppFunctionResponse) {
                                continuation.resume(Result.success(result))
                            }

                            override fun onError(e: SidecarAppFunctionException) {
                                continuation.resume(Result.failure(e))
                            }
                        },
                    )
            }
        }

        fun assertCancelListenerTriggered() {
            assertThat(waitForOperationCancellation(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS)).isTrue()
        }

        suspend fun sidecarIsAppFunctionEnabled(
            context: Context,
            functionIdentifier: String,
        ): Boolean = suspendCancellableCoroutine { continuation ->
            SidecarAppFunctionManager(context)
                .isAppFunctionEnabled(
                    functionIdentifier,
                    Runnable::run,
                    continuation.asOutcomeReceiver(),
                )
        }

        suspend fun sidecarIsAppFunctionEnabled(
            context: Context,
            targetPackage: String,
            functionIdentifier: String,
        ): Boolean = suspendCancellableCoroutine { continuation ->
            SidecarAppFunctionManager(context)
                .isAppFunctionEnabled(
                    functionIdentifier,
                    targetPackage,
                    Runnable::run,
                    continuation.asOutcomeReceiver(),
                )
        }

        suspend fun sidecarSetAppFunctionEnabled(
            context: Context,
            functionIdentifier: String,
            @EnabledState state: Int,
        ): Unit = suspendCancellableCoroutine { continuation ->
            SidecarAppFunctionManager(context)
                .setAppFunctionEnabled(
                    functionIdentifier,
                    state,
                    Runnable::run,
                    object : OutcomeReceiver<Void, Exception> {
                        override fun onResult(result: Void?) {
                            continuation.resume(Unit)
                        }

                        override fun onError(error: Exception) {
                            continuation.resumeWithException(error)
                        }
                    },
                )
        }

        /** Verifies that the service is unbound by asserting the service was destroyed. */
        @Throws(InterruptedException::class)
        fun assertServiceDestroyed() {
            assertThat(waitForServiceOnDestroy(LONG_TIMEOUT_SECOND, TimeUnit.SECONDS)).isTrue()
        }
    }
}

private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
