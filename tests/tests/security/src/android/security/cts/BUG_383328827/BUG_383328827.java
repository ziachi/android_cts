/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.security.cts.bug_383328827;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BUG_383328827 extends StsExtraBusinessLogicTestCase {

    private static final String TAG = "Bug_383328827";

    @Test
    @AsbSecurityTest(cveBugId = 383328827)
    public void testPocBug_383328827() {
        try {
            final Context context = InstrumentationRegistry.getInstrumentation().getContext();

            Intent pocIntent = new Intent(context, PocActivity.class);
            pocIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(pocIntent);

            String pocOutput = PocActivity.capturedOutput;
            boolean isEmpty = pocOutput.trim().isEmpty();

            assertWithMessage("Vulnerable to b/383328827. App should not have dump permissions")
                    .that(isEmpty)
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
