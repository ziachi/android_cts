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

import static android.content.pm.PackageManager.FEATURE_TELEPHONY_EUICC;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;
import static com.android.bedstead.nene.utils.Assert.assertThrows;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.content.Intent;
import android.telephony.euicc.DownloadableSubscription;
import android.telephony.euicc.EuiccManager;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireTelephonySupport;
import com.android.bedstead.harrier.policies.EmbeddedSubscription;
import com.android.bedstead.harrier.policies.EmbeddedSubscriptionSwitchAfterDownload;
import com.android.bedstead.nene.utils.BlockingPendingIntent;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
@RequireTelephonySupport
@RequireFeature(FEATURE_TELEPHONY_EUICC)
public final class EmbeddedSubscriptionsTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    public static final Duration SUBSCRIPTION_DOWNLOAD_WAIT_TIME = Duration.ofSeconds(120);

    // TODO(b/325267476): Figure out how to test the download operation as that requires
    //                    contacting a server

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#getSubscriptionIds"})
    @CanSetPolicyTest(policy = EmbeddedSubscription.class)
    @Postsubmit(reason = "new test")
    @Test
    public void getSubscriptionIds_initiallyEmpty() {
        Set<Integer> managedSubscriptions =
                dpc(sDeviceState).devicePolicyManager().getSubscriptionIds();
        assertThat(managedSubscriptions).isEmpty();
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#getSubscriptionIds")
    @CannotSetPolicyTest(policy = EmbeddedSubscription.class)
    @Postsubmit(reason = "new test")
    @Test
    public void getSubscriptionIds_noPermission_throws() throws Exception {
        assertThrows(SecurityException.class,
                () -> dpc(sDeviceState).devicePolicyManager().getSubscriptionIds());
    }

    @ApiTest(apis = "android.telephony.euicc.EuiccManager#downloadSubscription")
    @CannotSetPolicyTest(policy = EmbeddedSubscriptionSwitchAfterDownload.class,
            includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @Test
    public void downloadSubscription_withSwitchAfterDownloadAsTrue_failsAsNotAllowed() {
        assumeTrue("Test requires embedded subscriptions to be enabled on the device",
                dpc(sDeviceState).euiccManager().isEnabled());
        BlockingPendingIntent blockingPendingIntent = BlockingPendingIntent.getBroadcast();
        DownloadableSubscription downloadableSubscription =
                DownloadableSubscription.forActivationCode("");

        dpc(sDeviceState).euiccManager().downloadSubscription(
                downloadableSubscription, /*switchAfterDownload*/true,
                blockingPendingIntent.pendingIntent());

        Intent intent = blockingPendingIntent.await(SUBSCRIPTION_DOWNLOAD_WAIT_TIME.toMillis());
        assertThat(
                intent.hasExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE)).isFalse();
        assertThat(intent.hasExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE)).isFalse();
    }
}
