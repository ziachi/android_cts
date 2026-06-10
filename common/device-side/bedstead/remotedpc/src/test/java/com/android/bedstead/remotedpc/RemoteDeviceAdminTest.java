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

import static com.android.bedstead.multiuser.MultiUserDeviceStateExtensionsKt.additionalUser;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceAdmin;
import com.android.bedstead.nene.users.UserReference;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public final class RemoteDeviceAdminTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String REMOTE_DEVICE_ADMIN_APP_PACKAGE_NAME =
            "com.android.cts.RemoteDeviceAdmin";

    @Test
    public void setAsDeviceAdmin_getActiveAdmins_returnsRemoteDeviceAdmin() {
        RemoteDeviceAdmin remoteDeviceAdmin = null;
        try {
            remoteDeviceAdmin = RemoteDeviceAdmin.setAsDeviceAdmin();

            Set<DeviceAdmin> deviceAdmins = TestApis.devicePolicy().getActiveAdmins()
                    .stream().map(a -> DeviceAdmin.of(a.pkg(), a.componentName()))
                    .collect(Collectors.toUnmodifiableSet());

            assertThat(
                    deviceAdmins.stream()
                            .anyMatch(da -> da.componentName().getPackageName().startsWith(
                                    REMOTE_DEVICE_ADMIN_APP_PACKAGE_NAME)))
                    .isTrue();
        } finally {
            if (remoteDeviceAdmin != null) {
                remoteDeviceAdmin.devicePolicyController().remove();
            }
        }
    }

    @EnsureHasAdditionalUser
    @Test
    public void setAsDeviceAdmin_specifyUser_getActiveAdmins_returnsRemoteDeviceAdmin() {
        RemoteDeviceAdmin remoteDeviceAdmin = null;
        try {
            UserReference additionalUser = additionalUser(sDeviceState);
            remoteDeviceAdmin = RemoteDeviceAdmin.setAsDeviceAdmin(additionalUser);

            Set<DeviceAdmin> deviceAdmins =
                    TestApis.devicePolicy().getActiveAdmins(additionalUser)
                            .stream().map(a -> DeviceAdmin.of(a.pkg(), a.componentName()))
                            .collect(Collectors.toUnmodifiableSet());

            assertThat(
                    deviceAdmins.stream()
                            .anyMatch(da -> da.componentName().getPackageName().startsWith(
                                    REMOTE_DEVICE_ADMIN_APP_PACKAGE_NAME)))
                    .isTrue();
        } finally {
            if (remoteDeviceAdmin != null) {
                remoteDeviceAdmin.devicePolicyController().remove();
            }
        }
    }

    @Test
    public void setAsDeviceAdmin_deviceAdminAlreadyExists_returnExistingDeviceAdmin() {
        RemoteDeviceAdmin existingRemoteDeviceAdmin = null;
        RemoteDeviceAdmin newRemoteDeviceAdmin = null;
        try {
            existingRemoteDeviceAdmin = RemoteDeviceAdmin.setAsDeviceAdmin();
            newRemoteDeviceAdmin = RemoteDeviceAdmin.setAsDeviceAdmin();

            assertThat(newRemoteDeviceAdmin).isEqualTo(existingRemoteDeviceAdmin);
        } finally {
            if (existingRemoteDeviceAdmin != null) {
                existingRemoteDeviceAdmin.devicePolicyController().remove();
            }

            if (newRemoteDeviceAdmin != null) {
                newRemoteDeviceAdmin.devicePolicyController().remove();
            }
        }
    }
}