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

package com.android.bedstead.remotedpc;

import android.content.ComponentName;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceAdmin;
import com.android.bedstead.nene.devicepolicy.DevicePolicyController;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppProvider;
import com.android.bedstead.testapp.TestAppQueryBuilder;

import java.util.Set;
import java.util.stream.Collectors;

/** Entry point to RemoteDeviceAdmin. */
public final class RemoteDeviceAdmin extends RemotePolicyManager {

    private static final String DEFAULT_KEY = "remoteDeviceAdmin";
    public static final String REMOTE_DEVICE_ADMIN_APP_PACKAGE_NAME =
            "com.android.cts.RemoteDeviceAdmin";
    private static final String LOG_TAG = "RemoteDeviceAdmin";

    private final DevicePolicyController mDevicePolicyController;

    private final String mKey;

    public RemoteDeviceAdmin(TestApp testApp, DevicePolicyController devicePolicyController) {
        super(testApp, devicePolicyController == null ? null : devicePolicyController.user());
        mDevicePolicyController = devicePolicyController;
        mKey = DEFAULT_KEY;
    }

    public RemoteDeviceAdmin(String key, TestApp testApp,
            DevicePolicyController devicePolicyController) {
        super(testApp, devicePolicyController == null ? null : devicePolicyController.user());
        mDevicePolicyController = devicePolicyController;
        mKey = key;
    }

    /** Get the unique key of the RemoteDeviceAdmin. */
    public String key() {
        return mKey;
    }

    /**
     * Set device admin on instrumented user.
     *
     * This will return the existing {@link RemoteDeviceAdmin} if there is already one installed,
     * otherwise a new {@link RemoteDeviceAdmin} will be installed and returned.
     */
    public static RemoteDeviceAdmin setAsDeviceAdmin() {
        return setAsDeviceAdmin(DEFAULT_KEY, TestApis.users().instrumented(),
                new TestAppProvider().query()
                        .allowInternalBedsteadTestApps()
                        .wherePackageName()
                        .isEqualTo(REMOTE_DEVICE_ADMIN_APP_PACKAGE_NAME));
    }

    /**
     * Set device admin on the instrumented user that matches the specified query.
     *
     * This will return the existing {@link RemoteDeviceAdmin} if there is already one installed,
     * otherwise a new {@link RemoteDeviceAdmin} will be installed and returned.
     */
    public static RemoteDeviceAdmin setAsDeviceAdmin(UserReference user,
            TestAppQueryBuilder deviceAdminQuery) {
        // We make sure that the query has RemoteDeviceAdmin filter specified,
        // this is useful for the case where the user calls the method directly
        // and does not specify the required filter.
        deviceAdminQuery = addRemoteDeviceAdminPackageFilter(deviceAdminQuery);

        return setAsDeviceAdmin(DEFAULT_KEY, user, deviceAdminQuery);
    }

    /**
     * Set device admin on user.
     *
     * This will return the existing {@link RemoteDeviceAdmin} if there is already one installed,
     * otherwise a new {@link RemoteDeviceAdmin} will be installed and returned.
     */
    public static RemoteDeviceAdmin setAsDeviceAdmin(UserReference user) {
        return setAsDeviceAdmin(DEFAULT_KEY, user, new TestAppProvider().query()
                .allowInternalBedsteadTestApps()
                .wherePackageName()
                .isEqualTo(REMOTE_DEVICE_ADMIN_APP_PACKAGE_NAME));
    }

    /**
     * Set device admin with the specified key on the default user.
     *
     * This will return the existing {@link RemoteDeviceAdmin} if there is already one installed,
     * otherwise a new {@link RemoteDeviceAdmin} will be installed and returned.
     */
    private static RemoteDeviceAdmin setAsDeviceAdmin(String key, UserReference user,
            TestAppQueryBuilder deviceAdminQuery) {
        RemoteDeviceAdmin remoteDeviceAdmin = fetchRemoteDeviceAdmin(user, deviceAdminQuery);
        if (remoteDeviceAdmin != null) {
            Log.i(LOG_TAG, "RemoteDeviceAdmin already exists as an active admin, reusing.");
            return remoteDeviceAdmin;
        }

        removeAllRemoteDeviceAdmins(user);

        TestApp testApp = deviceAdminQuery.get();
        testApp.install(user);

        Log.i(LOG_TAG, "Installing RemoteDeviceAdmin app: " + testApp.packageName()
                + " with policies: " + testApp.policies());
        ComponentName componentName =
                new ComponentName(testApp.packageName(),
                        testApp.packageName() + ".DeviceAdminReceiver");
        DeviceAdmin deviceAdmin = TestApis.devicePolicy().setActiveAdmin(user, componentName);
        return new RemoteDeviceAdmin(key, testApp, deviceAdmin);
    }

    /**
     * Get the {@link RemoteDeviceAdmin} controller for the given {@link DevicePolicyController}.
     */
    public static RemoteDeviceAdmin forDevicePolicyController(DevicePolicyController controller) {
        if (controller == null) {
            throw new NullPointerException();
        }

        if (isRemoteDeviceAdmin(controller)) {
            TestApp remoteDeviceAdminTestApp = new TestAppProvider().query()
                    .allowInternalBedsteadTestApps()
                    .wherePackageName().startsWith(controller.componentName().getPackageName())
                    .get();

            return new RemoteDeviceAdmin(remoteDeviceAdminTestApp, controller);
        }

        throw new IllegalStateException("DevicePolicyController is not a RemoteDeviceAdmin: "
                + controller);
    }

    /**
     * Get the {@link DevicePolicyController} for this instance of RemoteDeviceAdmin.
     */
    public DevicePolicyController devicePolicyController() {
        return mDevicePolicyController;
    }

    /**
     * Check if {@link DevicePolicyController} is a {@link RemoteDeviceAdmin}
     */
    public static boolean isRemoteDeviceAdmin(DevicePolicyController controller) {
        return controller != null
                && controller.componentName().getPackageName().startsWith(
                REMOTE_DEVICE_ADMIN_APP_PACKAGE_NAME)
                && controller.componentName().getClassName().equals(
                controller.componentName().getPackageName() + ".DeviceAdminReceiver");
    }

    /**
     * Get the RemoteDeviceAdmin from the list of active admins for the user.
     */
    public static RemoteDeviceAdmin fetchRemoteDeviceAdmin(UserReference user) {
        TestAppQueryBuilder remoteDeviceAdminQuery = new TestAppProvider().query()
                .allowInternalBedsteadTestApps()
                .wherePackageName().startsWith(REMOTE_DEVICE_ADMIN_APP_PACKAGE_NAME);
        return fetchRemoteDeviceAdmin(user, remoteDeviceAdminQuery);
    }

    public static Set<RemoteDeviceAdmin> fetchAllRemoteDeviceAdmins(UserReference user) {
        Set<DeviceAdmin> activeAdmins = TestApis.devicePolicy().getActiveAdmins(user)
                .stream().map(a -> DeviceAdmin.of(user, a.pkg(), a.componentName()))
                .collect(Collectors.toUnmodifiableSet());
        return activeAdmins.stream()
                .filter(RemoteDeviceAdmin::isRemoteDeviceAdmin)
                .map(RemoteDeviceAdmin::forDevicePolicyController)
                .collect(Collectors.toSet());
    }

    /**
     * Get the RemoteDeviceAdmin that matches the query (if exists) from the list of active admins
     * for the instrumented user.
     */
    @Nullable
    public static RemoteDeviceAdmin fetchRemoteDeviceAdmin(TestAppQueryBuilder deviceAdminQuery) {
        return fetchRemoteDeviceAdmin(TestApis.users().instrumented(), deviceAdminQuery);
    }

    /**
     * Get the RemoteDeviceAdmin that matches the query (if exists) from the list of active admins
     * for the user.
     */
    @Nullable
    public static RemoteDeviceAdmin fetchRemoteDeviceAdmin(UserReference user,
            TestAppQueryBuilder deviceAdminQuery) {
        Set<DeviceAdmin> activeAdmins = TestApis.devicePolicy().getActiveAdmins(user)
                .stream().map(a -> DeviceAdmin.of(user, a.pkg(), a.componentName()))
                .collect(Collectors.toUnmodifiableSet());
        if (activeAdmins.isEmpty()) {
            return null;
        }

        return activeAdmins.stream()
                .filter(RemoteDeviceAdmin::isRemoteDeviceAdmin)
                .map(RemoteDeviceAdmin::forDevicePolicyController)
                .filter((remoteDeviceAdmin) ->
                        deviceAdminQuery.matches(remoteDeviceAdmin.testApp()))
                .findFirst().orElse(null);
    }

    @Override
    public ComponentName componentName() {
        return mDevicePolicyController.componentName();
    }

    private static void removeAllRemoteDeviceAdmins(UserReference user) {
        Set<RemoteDeviceAdmin> remoteDeviceAdmins = fetchAllRemoteDeviceAdmins(user);
        if (remoteDeviceAdmins == null) {
            return;
        }

        for (RemoteDeviceAdmin remoteDeviceAdmin : remoteDeviceAdmins) {
            remoteDeviceAdmin.devicePolicyController().remove();
        }
    }

    private static TestAppQueryBuilder addRemoteDeviceAdminPackageFilter(
            TestAppQueryBuilder dpcQuery) {
        return dpcQuery.wherePackageName()
                .startsWith(REMOTE_DEVICE_ADMIN_APP_PACKAGE_NAME)
                .allowInternalBedsteadTestApps();
    }

    @Override
    public int hashCode() {
        return mDevicePolicyController.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RemoteDeviceAdmin)) {
            return false;
        }

        RemoteDeviceAdmin other = (RemoteDeviceAdmin) obj;
        return other.mDevicePolicyController.equals(mDevicePolicyController);
    }

    @Override
    public String toString() {
        return "RemoteDeviceAdmin{"
                + "devicePolicyController=" + mDevicePolicyController
                + ", testApp=" + super.toString()
                + '}';
    }
}
