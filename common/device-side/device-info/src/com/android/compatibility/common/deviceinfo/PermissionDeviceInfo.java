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

package com.android.compatibility.common.deviceinfo;

import android.util.Log;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.DeviceInfoStore;
import com.android.compatibility.common.util.SystemUtil;
import com.android.modules.utils.build.SdkLevel;

import java.io.IOException;

/**
 * PermissionDeviceInfo collector.
 * <p>
 * Information about permissions in the system, independent of per-package information which is
 * collected in {@link PackageDeviceInfo}.
 */
public class PermissionDeviceInfo extends DeviceInfo {

    private static final String LOG_TAG = "PermissionDeviceInfo";

    private static final String NAME = "name";
    private static final String PACKAGES = "packages";
    private static final String PARTITION = "partition";
    private static final String PERMISSIONS = "permissions";
    private static final String SIGNATURE_PERMISSION_ALLOWLIST_ENABLED =
        "signature_permission_allowlist_enabled";
    private static final String SIGNATURE_PERMISSION_ALLOWLISTS = "signature_permission_allowlists";

    @Override
    protected void collectDeviceInfo(@NonNull DeviceInfoStore store) throws Exception {
        if (SdkLevel.isAtLeastV()) {
            collectSignaturePermissionAllowlists(store);
            collectSignaturePermissionAllowlistEnabled(store);
        }
    }

    private static void collectSignaturePermissionAllowlists(@NonNull DeviceInfoStore store)
            throws IOException {
        store.startArray(SIGNATURE_PERMISSION_ALLOWLISTS);
        collectSignaturePermissionAllowlist(store, "system");
        collectSignaturePermissionAllowlist(store, "vendor");
        collectSignaturePermissionAllowlist(store, "product");
        collectSignaturePermissionAllowlist(store, "system-ext");
        collectSignaturePermissionAllowlist(store, "apex");
        store.endArray(); // "signature_permission_allowlists"
    }

    private static void collectSignaturePermissionAllowlist(@NonNull DeviceInfoStore store,
            @NonNull String partition) throws IOException {
        final String output = SystemUtil.runShellCommandOrThrow(
                "pm get-signature-permission-allowlist " + partition).trim();
        store.startGroup();
        store.addResult(PARTITION, partition);
        store.startArray(PACKAGES);
        boolean isFirstPackage = true;
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.startsWith("Package: ")) {
                if (isFirstPackage) {
                    isFirstPackage = false;
                } else {
                    store.endArray(); // "permissions"
                    store.endGroup();
                }
                String packageName = line.substring("Package: ".length());
                store.startGroup();
                store.addResult(NAME, packageName);
                store.startArray(PERMISSIONS);
            } else if (line.startsWith("Permission: ")) {
                String permissionName = line.substring("Permission: ".length());
                store.startGroup();
                store.addResult(NAME, permissionName);
                store.endGroup();
            } else if (line.isEmpty()) {
                // Ignored.
            } else {
                throw new IllegalArgumentException("Unknown line: " + line);
            }
        }
        if (!isFirstPackage) {
            store.endArray(); // "permissions"
            store.endGroup();
        }
        store.endArray(); // "packages"
        store.endGroup();
    }

    private static void collectSignaturePermissionAllowlistEnabled(@NonNull DeviceInfoStore store)
            throws IOException {
        Boolean enabled;
        if (SdkLevel.isAtLeastB()) {
            enabled = FeatureFlagUtils.isFeatureFlagEnabled("android.permission.flags",
                    "signature_permission_allowlist_enabled");
        } else {
            enabled = FeatureFlagUtils.isFeatureFlagEnabledOnV(
                    "permissions/android.permission.flags.signature_permission_allowlist_enabled",
                    "system");
        }
        if (enabled == null) {
            Log.e(LOG_TAG, "Failed to check whether signature permission allowlist is enabled");
            return;
        }
        store.addResult(SIGNATURE_PERMISSION_ALLOWLIST_ENABLED, enabled);
    }
}
