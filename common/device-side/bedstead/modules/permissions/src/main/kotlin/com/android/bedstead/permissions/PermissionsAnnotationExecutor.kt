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
package com.android.bedstead.permissions

import android.os.Build.VERSION
import android.util.Log
import com.android.bedstead.harrier.AnnotationExecutor
import com.android.bedstead.harrier.AnnotationExecutorUtil
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.nene.TestApis.packages
import com.android.bedstead.nene.TestApis.permissions
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.utils.Tags.hasTag
import com.android.bedstead.nene.utils.Versions
import com.android.bedstead.permissions.annotations.EnsureCanGetPermission
import com.android.bedstead.permissions.annotations.EnsureDoesNotHaveAppOp
import com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermission
import com.android.bedstead.permissions.annotations.EnsureHasAppOp
import com.android.bedstead.permissions.annotations.EnsureHasPermission

/**
 * [AnnotationExecutor] for permissions annotations
 */
@Suppress("unused")
class PermissionsAnnotationExecutor : AnnotationExecutor, DeviceStateComponent {

    private var permissionContext: PermissionContextImpl? = null

    private fun getPermissionContext(): PermissionContextImpl {
        return permissionContext ?: permissions().createNonBlockingPermissionContext().also {
            permissionContext = it
        }
    }

    override fun applyAnnotation(annotation: Annotation) {
        when (annotation) {
            is EnsureHasPermission -> {
                if (!Versions.meetsSdkVersionRequirements(
                        annotation.minVersion,
                        annotation.maxVersion
                    )) {
                    Log.d(
                        LOG_TAG,
                        "Version " + VERSION.SDK_INT + " does not need to get permission " +
                                annotation.value.contentToString()
                    )
                    return
                }
                try {
                    for (permission in annotation.value) {
                        ensureCanGetPermission(permission)
                    }
                    withPermission(*annotation.value)
                } catch (e: NeneException) {
                    AnnotationExecutorUtil.failOrSkip(
                        "Error getting permission: $e",
                        annotation.failureMode
                    )
                }
            }

            is EnsureDoesNotHavePermission -> {
                try {
                    withoutPermission(*annotation.value)
                } catch (e: NeneException) {
                    AnnotationExecutorUtil.failOrSkip(
                        "Error denying permission: $e",
                        annotation.failureMode
                    )
                }
            }

            is EnsureCanGetPermission -> {
                if (!Versions.meetsSdkVersionRequirements(
                        annotation.minVersion,
                        annotation.maxVersion
                    )) {
                    Log.d(
                        LOG_TAG,
                        "Version " + VERSION.SDK_INT + " does not need to get permissions " +
                                annotation.value.contentToString()
                    )
                    return
                }
                for (permission: String in annotation.value) {
                    ensureCanGetPermission(permission)
                }
            }

            is EnsureHasAppOp -> {
                if (!Versions.meetsSdkVersionRequirements(
                        annotation.minVersion,
                        annotation.maxVersion
                    )) {
                    Log.d(
                        LOG_TAG,
                        "Version ${VERSION.SDK_INT} does not need to get appOp ${annotation.value}"
                    )
                    return
                }
                try {
                    withAppOp(annotation.value)
                } catch (e: NeneException) {
                    AnnotationExecutorUtil.failOrSkip(
                        "Error getting appOp: $e",
                        annotation.failureMode
                    )
                }
            }

            is EnsureDoesNotHaveAppOp -> {
                try {
                    withoutAppOp(annotation.value)
                } catch (e: NeneException) {
                    AnnotationExecutorUtil.failOrSkip(
                        "Error denying appOp: $e",
                        annotation.failureMode
                    )
                }
            }
        }
    }

    private fun withAppOp(vararg appOp: String) {
        getPermissionContext().withAppOp(*appOp)
    }

    private fun withoutAppOp(vararg appOp: String) {
        getPermissionContext().withoutAppOp(*appOp)
    }

    private fun withPermission(vararg permission: String) {
        getPermissionContext().withPermission(*permission)
    }

    private fun withoutPermission(vararg permission: String) {
        requireNotInstantApp("Uses withoutPermission", FailureMode.SKIP)
        getPermissionContext().withoutPermission(*permission)
    }

    private fun ensureCanGetPermission(permission: String) {
        // TODO: replace with dependency on bedstead-root when properly modularised
        if (hasTag("root-instrumentation") &&
            Versions.meetsMinimumSdkVersionRequirement(Versions.V)
        ) {
            return // If we're rooted we're always able to get permissions
        }
        if (permissions().usablePermissions().contains(permission)) {
            return
        }
        if (packages().instrumented().isInstantApp) {
            // Instant Apps aren't able to know the permissions of shell so we can't know
            // if we can adopt it - we'll assume we can adopt and log
            Log.i(
                LOG_TAG,
                "Assuming we can get permission " + permission +
                        " as running on instant app"
            )
            return
        }
        permissions().throwPermissionException(
            "Can not get required permission",
            permission
        )
    }

    // TODO: Move this into packages module
    private fun requireNotInstantApp(reason: String, failureMode: FailureMode) {
        AnnotationExecutorUtil.checkFailOrSkip(
            "Test does not run as an instant-app: $reason",
            !packages().instrumented().isInstantApp,
            failureMode
        )
    }

    override fun teardownNonShareableState() {
        permissionContext?.let {
            it.close()
            permissionContext = null
        }
        PermissionContextImpl.closeAllContexts()
    }

    override fun onTestFailed(exception: Throwable) {
        if (exception is SecurityException ||
            exception.stackTraceToString().contains("SecurityException")) {
            // Commonly we have SecurityExceptions when there are missing permissions.

            Log.i(
                LOG_TAG,
                "SecurityException when using PermissionsAnnotationExecutor. Permission state: " +
                        permissions().dump()
            )
        }
    }

    companion object {
        private const val LOG_TAG = "PermissionsAnnotationExecutor"
    }
}
