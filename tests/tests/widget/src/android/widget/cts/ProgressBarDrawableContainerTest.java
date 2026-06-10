/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.widget.cts;

import static org.junit.Assert.assertEquals;

import android.Manifest;
import android.app.Activity;
import android.graphics.drawable.DrawableContainer;
import android.widget.ProgressBar;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ProgressBarDrawableContainerTest {
    private Activity mActivity;

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<RadioGroupCtsActivity> mActivityRule =
            new ActivityTestRule<>(RadioGroupCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @UiThreadTest
    @Test
    public void testMutate() {
        DrawableContainer d = (DrawableContainer) new ProgressBar(
                mActivity).getIndeterminateDrawable().mutate();

        boolean mirrored = d.isAutoMirrored();
        d.setAutoMirrored(!mirrored);

        ProgressBar newBar = new ProgressBar(mActivity);
        DrawableContainer d2 = (DrawableContainer) newBar.getIndeterminateDrawable();
        boolean newMirrored = d2.isAutoMirrored();
        assertEquals(newMirrored, mirrored);
    }

}
