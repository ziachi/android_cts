/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.security.cts;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.os.Binder;
import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_49738 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 370840874)
    public void testPocCVE_2024_49738() {
        try {
            final String descriptor = "cve_2024_49738_new";
            final Parcel parcel = Parcel.obtain();
            parcel.writeStrongBinder(new Binder("cve_2024_49738_old"));
            parcel.setDataPosition(0);

            // Try to overwrite 'cve_2024_49738_old' Binder object
            try {
                parcel.writeStrongBinder(new Binder(descriptor));
            } catch (Exception exception) {
                // Ignore exception thrown with fix
            }

            // Fail if the Binder object is overwritten
            parcel.setDataPosition(0);
            assertWithMessage(
                            "Device is vulnerable to b/370840874. Overwriting objects in a parcel"
                                    + " can lead to memory corruption")
                    .that(parcel.readStrongBinder().getInterfaceDescriptor().contains(descriptor))
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
