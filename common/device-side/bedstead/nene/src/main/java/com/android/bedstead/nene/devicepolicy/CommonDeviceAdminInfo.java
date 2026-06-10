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

package com.android.bedstead.nene.devicepolicy;

/**
 * Common components for DeviceAdmin.
 */
public final class CommonDeviceAdminInfo {

    private CommonDeviceAdminInfo() {
    }

    /**
     * see {@code android.app.admin.DeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD}
     */
    public static final int USES_POLICY_LIMIT_PASSWORD = 0;

    /**
     * see {@code android.app.admin.DeviceAdminInfo.USES_POLICY_WATCH_LOGIN}
     */
    public static final int USES_POLICY_WATCH_LOGIN = 1;

    /**
     * see {@code android.app.admin.DeviceAdminInfo.USES_POLICY_RESET_PASSWORD}
     */
    public static final int USES_POLICY_RESET_PASSWORD = 2;

    /**
     * see {@code android.app.admin.DeviceAdminInfo.USES_POLICY_FORCE_LOCK}
     */
    public static final int USES_POLICY_FORCE_LOCK = 3;

    /**
     * see {@code android.app.admin.DeviceAdminInfo.USES_POLICY_WIPE_DATA}
     */
    public static final int USES_POLICY_WIPE_DATA = 4;

    /**
     * see {@code android.app.admin.DeviceAdminInfo.USES_POLICY_SETS_GLOBAL_PROXY}
     */
    public static final int USES_POLICY_SETS_GLOBAL_PROXY = 5;

    /**
     * see {@code android.app.admin.DeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD}
     */
    public static final int USES_POLICY_EXPIRE_PASSWORD = 6;

    /**
     * see {@code android.app.admin.DeviceAdminInfo.USES_ENCRYPTED_STORAGE}
     */
    public static final int USES_ENCRYPTED_STORAGE = 7;

    /**
     * see {@code android.app.admin.DeviceAdminInfo.USES_POLICY_DISABLE_CAMERA}
     */
    public static final int USES_POLICY_DISABLE_CAMERA = 8;

    /**
     * see {@code android.app.admin.DeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES}
     */
    public static final int USES_POLICY_DISABLE_KEYGUARD_FEATURES = 9;
}

