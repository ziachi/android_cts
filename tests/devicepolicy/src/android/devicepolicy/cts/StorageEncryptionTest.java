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

package android.devicepolicy.cts;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.RequireRunOnAdditionalUser;
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser;
import com.android.bedstead.harrier.annotations.RequireStorageEncryptionSupported;
import com.android.bedstead.harrier.annotations.RequireStorageEncryptionUnsupported;
import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.harrier.policies.StorageEncryption;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class StorageEncryptionTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final int ENCRYPTION_STATUS_UNSUPPORTED = 0;
    private static final int ENCRYPTION_STATUS_INACTIVE = 1;
    private static final int ENCRYPTION_STATUS_ACTIVE = 3;

    @CanSetPolicyTest(policy = StorageEncryption.class)
    @RequireRunOnSystemUser
    @RequireStorageEncryptionSupported
    public void setStorageEncryption_runOnSystemUser_enable_isEnabled() {
        try {
            assertThat(dpc(sDeviceState).devicePolicyManager().setStorageEncryption(
                    dpc(sDeviceState).componentName(), /* encrypt= */ true))
                    .isEqualTo(ENCRYPTION_STATUS_ACTIVE);

            assertThat(dpc(sDeviceState).devicePolicyManager().getStorageEncryption(
                    dpc(sDeviceState).componentName())).isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setStorageEncryption(
                    dpc(sDeviceState).componentName(), /* encrypt= */ false);
        }
    }

    @CanSetPolicyTest(policy = StorageEncryption.class)
    @RequireRunOnAdditionalUser
    public void setStorageEncryption_runOnNonSystemUser_enable_isNotSupported() {
        try {
            assertThat(dpc(sDeviceState).devicePolicyManager().setStorageEncryption(
                    dpc(sDeviceState).componentName(), /* encrypt= */ true))
                    .isEqualTo(ENCRYPTION_STATUS_UNSUPPORTED);

            assertThat(dpc(sDeviceState).devicePolicyManager().getStorageEncryption(
                    dpc(sDeviceState).componentName())).isFalse();
        } finally {
            dpc(sDeviceState).devicePolicyManager().setStorageEncryption(
                    dpc(sDeviceState).componentName(), /* encrypt= */ false);
        }
    }

    @CanSetPolicyTest(policy = StorageEncryption.class)
    @RequireRunOnSystemUser
    @RequireStorageEncryptionSupported
    public void setStorageEncryption_runOnSystemUser_disable_isDisabled() {
        assertThat(dpc(sDeviceState).devicePolicyManager().setStorageEncryption(
                dpc(sDeviceState).componentName(), /* encrypt= */ false))
                .isEqualTo(ENCRYPTION_STATUS_INACTIVE);

        assertThat(dpc(sDeviceState).devicePolicyManager().getStorageEncryption(
                dpc(sDeviceState).componentName())).isFalse();
    }

    @CanSetPolicyTest(policy = StorageEncryption.class)
    @RequireRunOnSystemUser
    @RequireStorageEncryptionUnsupported
    public void setStorageEncryption_runOnSystemUser_isNotSupported_isDisabled() {
        assertThat(dpc(sDeviceState).devicePolicyManager().getStorageEncryption(
                dpc(sDeviceState).componentName())).isFalse();
    }

    @CanSetPolicyTest(policy = StorageEncryption.class)
    @RequireRunOnAdditionalUser
    public void setStorageEncryption_runOnNonSystemUser_disable_isNotSupported() {
        assertThat(dpc(sDeviceState).devicePolicyManager().setStorageEncryption(
                dpc(sDeviceState).componentName(), /* encrypt= */ false))
                .isEqualTo(ENCRYPTION_STATUS_UNSUPPORTED);

        assertThat(dpc(sDeviceState).devicePolicyManager().getStorageEncryption(
                dpc(sDeviceState).componentName())).isFalse();
    }

}
