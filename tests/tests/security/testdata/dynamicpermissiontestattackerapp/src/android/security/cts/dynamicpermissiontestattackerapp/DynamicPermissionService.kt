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

package android.security.cts.usepermission

import android.app.Service
import android.content.Intent
import android.content.pm.PermissionInfo
import android.os.IBinder
import android.security.cts.dynamicpermissiontestattackerapp.IDynamicPermissionService

class DynamicPermissionService : Service() {
    private val binder = object : IDynamicPermissionService.Stub() {
        override fun removePermission(permissionName: String) {
            packageManager.removePermission(permissionName)
        }

        override fun addPermission(
            permissionName: String,
            packageName: String,
            protectionLevel: Int,
            group: String?
        ) {
            val permissionInfo = PermissionInfo().apply {
                name = permissionName
                nonLocalizedLabel = permissionName
                this.packageName = packageName
                this.protectionLevel = protectionLevel
                this.group = group
            }
            packageManager.addPermission(permissionInfo)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder
}
