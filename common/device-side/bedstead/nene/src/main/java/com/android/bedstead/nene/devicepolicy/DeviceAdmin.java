/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.cts.testapisreflection.TestApisReflectionKt.forceRemoveActiveAdmin;
import static com.android.bedstead.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.permissions.PermissionContext;

import java.util.Objects;

/**
 * A reference to a (non-DPC) Device Admin.
 */
public final class DeviceAdmin extends DevicePolicyController {

    DeviceAdmin(
            UserReference user,
            Package pkg,
            ComponentName componentName) {
        super(user, pkg, componentName);
    }

    public static DeviceAdmin of(ComponentName componentName) {
        return DeviceAdmin.of(componentName.getPackageName(), componentName);
    }

    public static DeviceAdmin of(String pkg, ComponentName componentName) {
        return new DeviceAdmin(TestApis.users().instrumented(),
                TestApis.packages().find(pkg), componentName);
    }

    public static DeviceAdmin of(Package pkg, ComponentName componentName) {
        return new DeviceAdmin(TestApis.users().instrumented(), pkg, componentName);
    }

    public static DeviceAdmin of(UserReference user, Package pkg, ComponentName componentName) {
        return new DeviceAdmin(user, pkg, componentName);
    }

    /**
     * Remove device admin.
     */
    @Override
    public void remove() {
        DevicePolicyManager devicePolicyManager =
                TestApis.context().androidContextAsUser(mUser).getSystemService(
                        DevicePolicyManager.class);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            forceRemoveActiveAdmin(devicePolicyManager, mComponentName, mUser.id());
        } catch (SecurityException e) {
            if (e.getMessage().contains("Attempt to remove non-test admin")
                    && TEST_APP_APP_COMPONENT_FACTORY.equals(mPackage.appComponentFactory())) {
                removeTestApp();
            } else {
                throw e;
            }
        }

        // Make sure this device admin is removed from the list of active admins for the
        // associated user.
        Poll.forValue("Device Admins", () ->
                        TestApis.devicePolicy().getActiveAdmins(mUser))
                .toMeet(deviceAdmins ->
                        deviceAdmins == null || deviceAdmins.stream().noneMatch(
                                deviceAdmin ->
                                        deviceAdmin.componentName().equals(
                                                mComponentName)))
                .errorOnFail()
                .await();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("DeviceAdmin{");
        stringBuilder.append("user=").append(user());
        stringBuilder.append(", package=").append(pkg());
        stringBuilder.append(", componentName=").append(componentName());
        stringBuilder.append("}");

        return stringBuilder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DeviceAdmin)) {
            return false;
        }

        DeviceAdmin other = (DeviceAdmin) obj;

        return Objects.equals(other.mUser, mUser)
                && Objects.equals(other.mPackage, mPackage)
                && Objects.equals(other.mComponentName, mComponentName);
    }
}