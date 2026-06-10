/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.opengl.cts;

import android.app.Instrumentation;
import android.content.Intent;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that gets a list of EGL configurations and tries to use each one in a GLSurfaceView.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class EglConfigTest {
    private final String TAG = this.getClass().getSimpleName();

    private Instrumentation mInstrumentation;

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            android.Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<EglConfigCtsActivity> mActivityRule =
            new ActivityTestRule<>(EglConfigCtsActivity.class, false, false);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @Test
    public void testEglConfigs() throws Exception {
        Intent intent = new Intent(InstrumentationRegistry.getTargetContext(),
                EglConfigCtsActivity.class);
        EglConfigCtsActivity activity = mActivityRule.launchActivity(intent);
        activity.waitToFinishTesting();
        activity.finish();
        mInstrumentation.waitForIdleSync();
    }
}
