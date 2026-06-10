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

import android.os.BadParcelableException;
import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;
import android.view.inputmethod.InputMethodSubtypeArray;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_49721 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 354682735)
    public void testPocCVE_2024_49721() {
        try {
            // Create parcel having negative mCount.
            final Parcel mismatchParcel = Parcel.obtain();
            mismatchParcel.writeInt(-1 /* mCount */);
            mismatchParcel.setDataPosition(0);

            // Call the vulnerable function with a malicious parcel.
            // A 'BadParcelableException' is thrown if a check is present on the parcel.
            InputMethodSubtypeArray inputMethodSubtypeArray = null;
            try {
                inputMethodSubtypeArray = new InputMethodSubtypeArray(mismatchParcel);
            } catch (BadParcelableException e) {
                return;
            }

            // Without Fix, 'mCount' is set to '-1' due to missing check.
            assertWithMessage(
                            "Device is vulnerable to b/354682735 !! Check on mCount"
                                    + " is missing")
                    .that(inputMethodSubtypeArray.getCount())
                    .isNotEqualTo(-1);
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
