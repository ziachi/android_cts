/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.bedstead.harrier.annotations

import com.android.bedstead.harrier.AnnotationExecutor

/**
 * Annotation to apply to an annotation outside of Harrier to indicate it should be processed
 * with a particular [AnnotationExecutor].
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class UsesAnnotationExecutor(

    /**
     * The fully qualified name of the [AnnotationExecutor] to use when parsing this annotation.
     *
     * This works even for cases where the annotation needs to be defined in a separate target
     * to the executor, for example where the annotation cannot be defined in an Android target.
     */
    val value: String
) {
    companion object {
        const val PERMISSIONS = "com.android.bedstead.permissions.PermissionsAnnotationExecutor"
        const val ROOT = "com.android.xts.root.RootAnnotationExecutor"
        const val INTERACTIVE = "com.android.interactive.InteractiveAnnotationExecutor"
        const val FLAGS = "com.android.bedstead.flags.FlagsAnnotationExecutor"
        const val MULTI_USER = "com.android.bedstead.multiuser.MultiUserAnnotationExecutor"
        const val ENTERPRISE = "com.android.bedstead.enterprise.EnterpriseAnnotationExecutor"
        const val MAIN = "com.android.bedstead.harrier.MainAnnotationExecutor"
        const val TEST_APPS = "com.android.bedstead.testapps.TestAppsAnnotationExecutor"
        const val ACCOUNTS = "com.android.bedstead.accounts.AccountsAnnotationExecutor"
        const val CONTENT_SUGGESTIONS =
            "com.android.bedstead.contentsuggestions.ContentSuggestionsAnnotationExecutor"
        const val BLUETOOTH = "com.android.bedstead.bluetooth.BluetoothAnnotationExecutor"
    }
}
