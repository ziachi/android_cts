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

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionManager.EnabledState
import android.app.appfunctions.AppFunctionRuntimeMetadata
import android.app.appfunctions.AppFunctionStaticMetadataHelper
import android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_NAMESPACE
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appfunctions.cts.AppSearchUtils.collectAllSearchResults
import android.app.appsearch.GenericDocument
import android.app.appsearch.GlobalSearchSessionShim
import android.app.appsearch.SearchResultsShim
import android.app.appsearch.SearchSpec
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl
import android.content.Context
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object AppFunctionUtils {
    /** Executes an app function and waits for the response. */
    suspend fun executeAppFunctionAndWait(
        manager: AppFunctionManager,
        request: ExecuteAppFunctionRequest,
    ): Result<ExecuteAppFunctionResponse> {
        return suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            manager.executeAppFunction(
                request,
                Runnable::run,
                cancellationSignal,
                object : OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException> {
                    override fun onResult(result: ExecuteAppFunctionResponse) {
                        continuation.resume(Result.success(result))
                    }

                    override fun onError(e: AppFunctionException) {
                        continuation.resume(Result.failure(e))
                    }
                },
            )
        }
    }

    /** Sets the enabled state of an app function. */
    suspend fun setAppFunctionEnabled(
        manager: AppFunctionManager,
        functionIdentifier: String,
        @EnabledState state: Int,
    ): Unit = suspendCancellableCoroutine { continuation ->
        manager.setAppFunctionEnabled(
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

    /** Gets all the static metadata packages. */
    fun getAllStaticMetadataPackages(context: Context? = null) =
        searchStaticMetadata(context).map { it.getPropertyString(PROPERTY_PACKAGE_NAME) }.toSet()

    /** Gets all the runtime metadata packages. */
    fun getAllRuntimeMetadataPackages(context: Context? = null) =
        searchRuntimeMetadata(context).map { it.getPropertyString(PROPERTY_PACKAGE_NAME) }.toSet()

    private fun searchStaticMetadata(context: Context? = null): List<GenericDocument> {
        val globalSearchSession = getGlobalSearchSession(context)

        val searchResults: SearchResultsShim =
            globalSearchSession.search(
                "",
                SearchSpec.Builder()
                    .addFilterNamespaces(APP_FUNCTION_STATIC_NAMESPACE)
                    .addFilterPackageNames(APP_FUNCTION_INDEXER_PACKAGE)
                    .addFilterSchemas(AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE)
                    .setVerbatimSearchEnabled(true)
                    .build(),
            )
        return collectAllSearchResults(searchResults)
    }

    private fun searchRuntimeMetadata(context: Context? = null): List<GenericDocument> {
        val globalSearchSession = getGlobalSearchSession(context)

        val searchResults: SearchResultsShim =
            globalSearchSession.search(
                "",
                SearchSpec.Builder()
                    .addFilterNamespaces(AppFunctionRuntimeMetadata.APP_FUNCTION_RUNTIME_NAMESPACE)
                    .addFilterSchemas(AppFunctionRuntimeMetadata.RUNTIME_SCHEMA_TYPE)
                    .setVerbatimSearchEnabled(true)
                    .build(),
            )
        return collectAllSearchResults(searchResults)
    }

    private fun getGlobalSearchSession(context: Context? = null): GlobalSearchSessionShim {
        return if (context == null) {
            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync().get()
        } else {
            GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync(context).get()
        }
    }

    private const val PROPERTY_PACKAGE_NAME = "packageName"
    private const val APP_FUNCTION_INDEXER_PACKAGE = "android"
}
