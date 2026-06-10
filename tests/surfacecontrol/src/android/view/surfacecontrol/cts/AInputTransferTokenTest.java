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

package android.view.surfacecontrol.cts;

import static android.view.cts.util.AInputTransferTokenUtils.nAInputTransferTokenFromJava;
import static android.view.cts.util.AInputTransferTokenUtils.nAInputTransferTokenRelease;
import static android.view.cts.util.AInputTransferTokenUtils.nAInputTransferTokenToJava;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertNotEquals;

import android.platform.test.annotations.Presubmit;
import android.window.InputTransferToken;

import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@Presubmit
public class AInputTransferTokenTest {
    @Rule
    public ActivityScenarioRule<TestActivity> mActivityRule =
            new ActivityScenarioRule<>(TestActivity.class);

    private TestActivity mActivity;

    @Before
    public void setUp() throws InterruptedException {
        mActivityRule.getScenario().onActivity(a -> mActivity = a);
    }

    @Test
    public void testAInputTransferToken_fromToJava() {
        InputTransferToken token =
                mActivity.getWindow().getRootSurfaceControl().getInputTransferToken();
        long nativeToken = nAInputTransferTokenFromJava(token);
        assertNotEquals("Received invalid native token", 0, nativeToken);
        InputTransferToken fromNative = nAInputTransferTokenToJava(nativeToken);
        assertEquals("Token from Java and native don't match", token, fromNative);
        nAInputTransferTokenRelease(nativeToken);
    }
}
