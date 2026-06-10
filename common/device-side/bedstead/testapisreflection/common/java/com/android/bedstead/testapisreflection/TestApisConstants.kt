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

package com.android.bedstead.testapisreflection

/**
 * Declare any constants marked as @TestApi here.
 *
 * This class exists so these constants are accessible in environments where TestApis are not
 * accessible for e.g. g3.
 */
object TestApisConstants {
    /** See [android.app.ActivityManager#STOP_USER_ON_SWITCH_DEFAULT] */
    const val STOP_USER_ON_SWITCH_DEFAULT = -1

    /** See [android.app.ActivityManager#STOP_USER_ON_SWITCH_TRUE] */
    const val STOP_USER_ON_SWITCH_TRUE = 1

    /** See [android.app.ActivityManager#STOP_USER_ON_SWITCH_FALSE] */
    const val STOP_USER_ON_SWITCH_FALSE = 0

    /** See [android.credentials.CredentialManager#PROVIDER_FILTER_ALL_PROVIDERS] */
    const val PROVIDER_FILTER_ALL_PROVIDERS = 0

    /** See [android.credentials.CredentialManager#PROVIDER_FILTER_ALL_PROVIDERS] */
    const val PROVIDER_FILTER_SYSTEM_PROVIDERS_ONLY = 1

    /** See [android.credentials.CredentialManager#PROVIDER_FILTER_ALL_PROVIDERS] */
    const val PROVIDER_FILTER_USER_PROVIDERS_ONLY = 2

    /** See [android.content.Context#PROVIDER_FILTER_ALL_PROVIDERS] */
    const val RECEIVER_EXPORTED_UNAUDITED = 0x2

    /** See [com.android.compatibility.common.util.enterprise.DeviceAdminReceiverUtils.ACTION_DISABLE_SELF ] */
    const val ACTION_DISABLE_SELF = "disable_self"
}