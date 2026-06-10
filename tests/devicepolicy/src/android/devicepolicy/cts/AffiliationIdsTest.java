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
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.admin.RemoteDevicePolicyManager;
import android.content.ComponentName;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.policies.AffiliationIds;
import com.android.bedstead.remotedpc.RemotePolicyManager;
import com.android.modules.utils.ModifiedUtf8;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.testng.Assert;

import java.io.UTFDataFormatException;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class AffiliationIdsTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private ComponentName mAdmin;
    private RemoteDevicePolicyManager mDpm;

    public static final int MAX_MSG_LEN_IN_BYTES = 65535;

    @Before
    public void setUp() {
        RemotePolicyManager dpc = dpc(sDeviceState);
        mAdmin = dpc.componentName();
        mDpm = dpc.devicePolicyManager();
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AffiliationIds.class)
    public void setAffiliationIds_idTooLong_throws() {
        String badId = "🙃".repeat(30000);

        // This ID has less than 65k *characters*,
        assertThat(badId.length()).isAtMost(MAX_MSG_LEN_IN_BYTES);
        // but more than 65k *bytes*.
        assertThat(countBytes(badId)).isAtLeast(MAX_MSG_LEN_IN_BYTES);

        // String too long for id, cannot be serialized correctly
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> mDpm.setAffiliationIds(mAdmin, Set.of(badId)));
    }

    private long countBytes(String s) {
        try {
            return ModifiedUtf8.countBytes(s, /* throw error */ false);
        } catch (UTFDataFormatException e) {
            // This error should never be thrown.
            assertWithMessage("Unexpected error thrown").fail();
            return 0;
        }
    }
}
