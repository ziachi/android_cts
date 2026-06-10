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

import static android.os.UserManager.DISALLOW_PRINTING;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.dpc;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;

import com.android.activitycontext.ActivityContext;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.enterprise.annotations.EnsureHasUserRestriction;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.policies.DisallowPrinting;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
// If the device doesn't support printing then as long as the user restriction doesn't throw an
// exception when setting - we can assume it's fine
@RequireFeature("android.software.print")
public final class PrintingTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String PRINT_NAME = "print";
    private static final PrintDocumentAdapter PRINT_DOCUMENT_ADAPTER = new PrintDocumentAdapter() {
        @Override
        public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                CancellationSignal cancellationSignal, LayoutResultCallback callback,
                Bundle extras) {

        }

        @Override
        public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                CancellationSignal cancellationSignal, WriteResultCallback callback) {

        }
    };

    @CannotSetPolicyTest(policy = DisallowPrinting.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_PRINTING")
    public void addUserRestriction_disallowPrinting_cannotSet_throwsException() {
        try {
            assertThrows(SecurityException.class,
                    () -> dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                            dpc(sDeviceState).componentName(), DISALLOW_PRINTING));
        } finally {
            try {
                dpc(sDeviceState).devicePolicyManager()
                        .clearUserRestriction(dpc(sDeviceState).componentName(),
                                DISALLOW_PRINTING);
            } catch (Exception e) {
                // Expected
            }
        }
    }

    @PolicyAppliesTest(policy = DisallowPrinting.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_PRINTING")
    public void addUserRestriction_disallowPrinting_isSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_PRINTING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_PRINTING))
                    .isTrue();
        } finally {
            try {
                dpc(sDeviceState).devicePolicyManager()
                        .clearUserRestriction(dpc(sDeviceState).componentName(),
                                DISALLOW_PRINTING);
            } catch (Exception e) {
                // Expected
            }
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowPrinting.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_PRINTING")
    public void addUserRestriction_disallowPrinting_isNotSet() {
        try {
            dpc(sDeviceState).devicePolicyManager().addUserRestriction(
                    dpc(sDeviceState).componentName(), DISALLOW_PRINTING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_PRINTING))
                    .isFalse();
        } finally {
            try {
                dpc(sDeviceState).devicePolicyManager()
                        .clearUserRestriction(dpc(sDeviceState).componentName(),
                                DISALLOW_PRINTING);
            } catch (Exception e) {
                // Expected
            }
        }
    }

    @EnsureHasUserRestriction(DISALLOW_PRINTING)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_PRINTING")
    public void print_disallowPrintingIsSet_returnsNull() throws Exception {
        PrintJob printJob = ActivityContext.getWithContext(
                (ctx) -> ctx.getSystemService(PrintManager.class)
                        .print(PRINT_NAME, PRINT_DOCUMENT_ADAPTER, /* attributes= */ null));

        assertThat(printJob).isNull();
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_PRINTING)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_PRINTING")
    public void print_disallowPrintingIsNotSet_doesNotReturnNull() throws Exception {
        PrintJob printJob = ActivityContext.getWithContext(
                (ctx) -> ctx.getSystemService(PrintManager.class)
                        .print(PRINT_NAME, PRINT_DOCUMENT_ADAPTER, /* attributes= */ null));

        assertThat(printJob).isNotNull();
    }
}
