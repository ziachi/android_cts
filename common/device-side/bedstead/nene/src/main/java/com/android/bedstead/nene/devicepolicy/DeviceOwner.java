/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static android.cts.testapisreflection.TestApisReflectionKt.getDeviceOwnerType;
import static android.cts.testapisreflection.TestApisReflectionKt.isRemovingAdmin;
import static android.cts.testapisreflection.TestApisReflectionKt.setDeviceOwnerType;

import static com.android.bedstead.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.Build;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.bedstead.nene.utils.Versions;
import com.android.bedstead.permissions.PermissionContext;

import java.util.Objects;

/**
 * A reference to a Device Owner.
 */
public final class DeviceOwner extends DevicePolicyController {

    DeviceOwner(UserReference user,
            Package pkg,
            ComponentName componentName) {
        super(user, pkg, componentName);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("DeviceOwner{");
        stringBuilder.append("user=").append(user());
        stringBuilder.append(", package=").append(pkg());
        stringBuilder.append(", componentName=").append(componentName());
        stringBuilder.append("}");

        return stringBuilder.toString();
    }

    @Override
    public void remove() {
        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)
                || TestApis.packages().instrumented().isInstantApp()) {
            removePreS();
            return;
        }

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

        Poll.forValue("Device Owner", () -> TestApis.devicePolicy().getDeviceOwner())
                .toBeNull()
                .errorOnFail().await();
    }

    private void removePreS() {
        try {
            ShellCommand.builderForUser(mUser, "dpm remove-active-admin")
                    .addOperand(componentName().flattenToShortString())
                    .validate(ShellCommandUtils::startsWithSuccess)
                    .execute();
        } catch (AdbException e) {
            if (TEST_APP_APP_COMPONENT_FACTORY.equals(mPackage.appComponentFactory())
                    && user().parent() == null) {
                // We can't see why it failed so we'll try the test app version
                removeTestApp();
            } else {
                throw new NeneException("Error removing device owner " + this, e);
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DeviceOwner)) {
            return false;
        }

        DeviceOwner other = (DeviceOwner) obj;

        return Objects.equals(other.mUser, mUser)
                && Objects.equals(other.mPackage, mPackage)
                && Objects.equals(other.mComponentName, mComponentName);
    }

    /**
     * Sets the type of device owner that is on the device.
     *
     * @param deviceOwnerType The type of device owner that is managing the device which can be
     *                        {@link DeviceOwnerType#DEFAULT} as a default device owner or
     *                        {@link DeviceOwnerType#FINANCED} as a financed device owner.
     * @throws IllegalArgumentException If the device owner type is not one of
     *                                  {@link DeviceOwnerType#DEFAULT} or {@link
     *                                  DeviceOwnerType#FINANCED}.
     * @throws NeneException            When the device owner type fails to be set.
     */
    public void setType(int deviceOwnerType) {
        if (!isValidDeviceOwnerType(deviceOwnerType)) {
            throw new IllegalArgumentException("Device owner type provided is not valid: "
                    + deviceOwnerType);
        }

        DevicePolicyManager devicePolicyManager =
                TestApis.context().androidContextAsUser(mUser).getSystemService(
                        DevicePolicyManager.class);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            setDeviceOwnerType(devicePolicyManager, mComponentName, deviceOwnerType);
        } catch (IllegalStateException e) {
            throw new NeneException("Failed to set the device owner type", e);
        }

        assertThat(getType()).isEqualTo(deviceOwnerType);
    }

    /**
     * Returns the device owner type set by {@link #setType(int)}. If it is not set, then
     * {@link DeviceOwnerType#DEFAULT} is returned instead.
     *
     * @throws NeneException         If retrieving the device owner type fails.
     * @throws IllegalStateException If the device owner type returned is not one of {@link
     *                               DeviceOwnerType#DEFAULT} or
     *                               {@link DeviceOwnerType#FINANCED}.
     */
    public int getType() {
        int deviceOwnerType;

        DevicePolicyManager devicePolicyManager =
                TestApis.context().androidContextAsUser(mUser).getSystemService(
                        DevicePolicyManager.class);

        try {
            deviceOwnerType = getDeviceOwnerType(devicePolicyManager, mComponentName);
        } catch (IllegalStateException e) {
            throw new NeneException("Failed to retrieve the device owner type", e);
        }

        if (!isValidDeviceOwnerType(deviceOwnerType)) {
            throw new IllegalStateException("Device owner type returned is not valid: "
                    + deviceOwnerType);
        }
        return deviceOwnerType;
    }

    private boolean isValidDeviceOwnerType(int deviceOwnerType) {
        return (deviceOwnerType == DeviceOwnerType.DEFAULT)
                || (deviceOwnerType == DeviceOwnerType.FINANCED);
    }
}
