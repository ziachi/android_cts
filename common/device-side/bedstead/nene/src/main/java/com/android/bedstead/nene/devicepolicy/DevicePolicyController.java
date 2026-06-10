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

import static android.cts.testapisreflection.TestApisReflectionKt.isRemovingAdmin;
import static com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.BlockingBroadcastReceiver;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.Retry;
import com.android.bedstead.permissions.PermissionContext;
import com.android.bedstead.testapisreflection.TestApisConstants;

import java.time.Duration;
import java.util.Objects;

/**
 * A reference to either a Device Owner or a Profile Owner.
 */
public abstract class DevicePolicyController implements AutoCloseable {

    protected static final String TEST_APP_APP_COMPONENT_FACTORY =
            "com.android.bedstead.testapp.TestAppAppComponentFactory";

    protected final UserReference mUser;
    protected final Package mPackage;
    protected final ComponentName mComponentName;

    DevicePolicyController(UserReference user, Package pkg, ComponentName componentName) {
        if (user == null || pkg == null || componentName == null) {
            throw new NullPointerException();
        }

        mUser = user;
        mPackage = pkg;
        mComponentName = componentName;
    }

    /**
     * Get the {@link UserReference} which this device policy controller is installed into.
     */
    public UserReference user() {
        return mUser;
    }

    /**
     * Get the {@link Package} of the device policy controller.
     */
    public Package pkg() {
        return mPackage;
    }

    /**
     * Get the {@link ComponentName} of the {@link DeviceAdminReceiver} for this device policy
     * controller.
     */
    public ComponentName componentName() {
        return mComponentName;
    }

    /**
     * Remove this device policy controller.
     */
    public abstract void remove();

    /**
     * Remove test app.
     *
     * Special case for removing TestApp DPCs - this works even when not testOnly
     * but not on profiles
     */
    protected void removeTestApp() {
        // Special case for removing TestApp DPCs - this works even when not testOnly
        Intent intent = new Intent(TestApisConstants.ACTION_DISABLE_SELF);
        intent.setComponent(new ComponentName(pkg().packageName(),
                "com.android.bedstead.testapp.TestAppBroadcastController"));
        Context context = TestApis.context().androidContextAsUser(mUser);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            // If the profile isn't ready then the broadcast won't be sent and the profile owner
            // will not be removed. So we can retry until the broadcast has been dealt with.
            Retry.logic(() -> {
                com.android.bedstead.nene.utils.BlockingBroadcastReceiver
                        b = new BlockingBroadcastReceiver(
                        TestApis.context().instrumentedContext());

                context.sendOrderedBroadcast(
                        intent, /* receiverPermission= */ null, b, /* scheduler= */
                        null, /* initialCode= */
                        Activity.RESULT_CANCELED, /* initialData= */ null, /* initialExtras= */
                        null);

                b.awaitForBroadcastOrFail(Duration.ofSeconds(30).toMillis());
                assertThat(b.getResultCode()).isEqualTo(Activity.RESULT_OK);
            }).timeout(Duration.ofMinutes(5)).runAndWrapException();

            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);

            Poll.forValue(() -> isRemovingAdmin(dpm, mComponentName, mUser.id()))
                    .toNotBeEqualTo(true)
                    .timeout(Duration.ofMinutes(5))
                    .errorOnFail()
                    .await();
        }

    }

    /**
     * Check if DPC is active
     */
    @Experimental
    public boolean isActive() {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            DevicePolicyManager devicePolicyManager =
                    TestApis.context().androidContextAsUser(mUser)
                            .getSystemService(DevicePolicyManager.class);

            return devicePolicyManager.isAdminActive(mComponentName);
        }
    }

    /**
     * Check if DPC has granted the specified policy.
     */
    @Experimental
    public boolean hasGrantedPolicy(int policy) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            DevicePolicyManager devicePolicyManager =
                    TestApis.context().androidContextAsUser(mUser)
                            .getSystemService(DevicePolicyManager.class);

            return devicePolicyManager.hasGrantedPolicy(mComponentName, policy);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mUser)
                + Objects.hashCode(mPackage)
                + Objects.hashCode(mComponentName);
    }

    /** See {@link #remove}. */
    @Override
    public void close() {
        remove();
    }
}
