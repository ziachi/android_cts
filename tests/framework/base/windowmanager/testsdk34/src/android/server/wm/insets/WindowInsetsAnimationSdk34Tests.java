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

package android.server.wm.insets;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowInsets.Type.systemBars;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowInsetsAnimationTestBase;
import android.view.WindowInsetsAnimation;

import org.junit.Before;
import org.junit.Test;

/**
 * Test whether {@link WindowInsetsAnimation.Callback} are properly dispatched to views.
 *
 * <p>Build/Install/Run: atest CtsWindowManagerSdk34TestCases:WindowInsetsAnimationSdk34Tests
 */
@Presubmit
@android.server.wm.annotation.Group2
public class WindowInsetsAnimationSdk34Tests extends WindowInsetsAnimationTestBase {

    @Before
    public void setup() throws Exception {
        super.setUp();
        mActivity =
                startActivity(TestActivity.class, DEFAULT_DISPLAY, true, WINDOWING_MODE_FULLSCREEN);
        mRootView = mActivity.getWindow().getDecorView();
        assumeTrue(hasWindowInsets(mRootView, systemBars()));
        assumeFalse(isCar() && remoteInsetsControllerControlsSystemBars());
    }

    @Test
    public void testAnimationCallbacks_consumedByDecor() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            mActivity.getWindow().setDecorFitsSystemWindows(true);
                            mRootView.getWindowInsetsController().hide(systemBars());
                        });

        getWmState()
                .waitFor(
                        state -> !state.isWindowVisible("StatusBar"),
                        "Waiting for status bar to be hidden");
        assertFalse(getWmState().isWindowVisible("StatusBar"));

        verifyNoMoreInteractions(mActivity.mCallback);
    }
}
