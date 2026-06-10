/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.ENSURE_VERIFY_APPS;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.policies.EnsureVerifyApps;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class PackagesTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @CannotSetPolicyTest(policy = EnsureVerifyApps.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#ENSURE_VERIFY_APPS")
    public void setUserRestriction_ensureVerifyApps_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                        dpc(sDeviceState).componentName(), ENSURE_VERIFY_APPS));
    }

    @PolicyAppliesTest(policy = EnsureVerifyApps.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#ENSURE_VERIFY_APPS")
    public void setUserRestriction_ensureVerifyApps_isSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), ENSURE_VERIFY_APPS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(ENSURE_VERIFY_APPS))
                    .isTrue();
        } finally {
            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), ENSURE_VERIFY_APPS);
        }
    }

    @PolicyDoesNotApplyTest(policy = EnsureVerifyApps.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#ENSURE_VERIFY_APPS")
    public void setUserRestriction_ensureVerifyApps_isNotSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), ENSURE_VERIFY_APPS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(ENSURE_VERIFY_APPS))
                    .isFalse();
        } finally {

            dpc(sDeviceState).devicePolicyManager().clearUserRestriction(
                    dpc(sDeviceState).componentName(), ENSURE_VERIFY_APPS);
        }
    }

    // TODO: Add (interactive?) test of ENSURE_VERIFY_APPS behaviour
}
