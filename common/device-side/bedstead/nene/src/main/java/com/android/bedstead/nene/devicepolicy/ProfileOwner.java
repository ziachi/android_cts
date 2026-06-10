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

import static android.cts.testapisreflection.TestApisReflectionKt.setProfileOwnerOnOrganizationOwnedDevice;
import static android.cts.testapisreflection.TestApisReflectionKt.forceRemoveActiveAdmin;
import static android.cts.testapisreflection.TestApisReflectionKt.isRemovingAdmin;
import static android.cts.testapisreflection.TestApisReflectionKt.setDeviceOwnerType;
import static android.os.Build.VERSION_CODES.R;
import static android.os.Build.VERSION_CODES.TIRAMISU;

import static com.android.bedstead.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;

import android.annotation.TargetApi;
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
 * A reference to a Profile Owner.
 */
public final class ProfileOwner extends DevicePolicyController {

    ProfileOwner(UserReference user,
            Package pkg,
            ComponentName componentName) {
        super(user, pkg, componentName);
    }

    /** Returns whether the current profile is organization owned. */
    @TargetApi(R)
    public boolean isOrganizationOwned() {
        if (!Versions.meetsMinimumSdkVersionRequirement(R)) {
            return false;
        }

        DevicePolicyManager devicePolicyManager =
                TestApis.context().androidContextAsUser(mUser).getSystemService(
                        DevicePolicyManager.class);
        return devicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile();
    }

    /** Sets whether the current profile is organization owned. */
    @TargetApi(TIRAMISU)
    public void setIsOrganizationOwned(boolean isOrganizationOwned) {
        if (isOrganizationOwned() == isOrganizationOwned) {
            return; // Nothing to do
        }

        Versions.requireMinimumVersion(TIRAMISU);

        DevicePolicyManager devicePolicyManager =
                TestApis.context().androidContextAsUser(mUser).getSystemService(
                        DevicePolicyManager.class);

        UserReference user = TestApis.users().system();
        boolean userSetupComplete = user.getSetupComplete();
        try {
            user.setSetupComplete(false);

            try (PermissionContext p = TestApis.permissions().withPermission(
                    MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
                setProfileOwnerOnOrganizationOwnedDevice(
                        devicePolicyManager, mComponentName, isOrganizationOwned);
            }
        } finally {
            user.setSetupComplete(userSetupComplete);
        }
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
                    && TEST_APP_APP_COMPONENT_FACTORY.equals(mPackage.appComponentFactory())
                    && user().parent() == null) {
                removeTestApp();
            } else {
                throw e;
            }
        }

        Poll.forValue("Profile Owner",
                () -> TestApis.devicePolicy().getProfileOwner(mUser))
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
                throw new NeneException("Error removing profile owner " + this, e);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("ProfileOwner{");
        stringBuilder.append("user=").append(user());
        stringBuilder.append(", package=").append(pkg());
        stringBuilder.append(", componentName=").append(componentName());
        stringBuilder.append("}");

        return stringBuilder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProfileOwner)) {
            return false;
        }

        ProfileOwner other = (ProfileOwner) obj;

        return Objects.equals(other.mUser, mUser)
                && Objects.equals(other.mPackage, mPackage)
                && Objects.equals(other.mComponentName, mComponentName);
    }
}
