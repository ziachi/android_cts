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

package android.server.wm.window;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.graphics.Insets;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowInsets;

import org.junit.Test;

public class WindowPolicyTestsSdk35 extends WindowPolicyTestBase {

    @Test
    public void testOptOutEdgeToEdgeAppBounds() {
        TestActivity.sLayoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        TestActivity activity = startActivitySync(OptOutEdgeToEdgeActivity.class);
        runOnMainSync(() -> {
            final WindowInsets windowInsets =
                    activity.getWindow().getDecorView().getRootWindowInsets();
            final Insets insets = windowInsets.getInsets(
                    WindowInsets.Type.displayCutout() | WindowInsets.Type.navigationBars());
            final Rect expectedBounds = new Rect(
                    activity.getResources().getConfiguration().windowConfiguration.getBounds());
            expectedBounds.inset(insets);
            assertEquals(
                    "The bounds must exclude display cutout and navigation bar area.",
                    expectedBounds,
                    activity.getResources().getConfiguration().windowConfiguration.getAppBounds());
        });
    }

    @Test
    public void testOptOutEdgeToEdgeDisplayMetrics() {
        TestActivity.sLayoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        TestActivity activity = startActivitySync(OptOutEdgeToEdgeActivity.class);
        runOnMainSync(() -> {
            final WindowInsets windowInsets =
                    activity.getWindow().getDecorView().getRootWindowInsets();
            final Insets insets = windowInsets.getInsets(
                    WindowInsets.Type.displayCutout() | WindowInsets.Type.navigationBars());
            final Rect expectedBounds = new Rect(
                    activity.getResources().getConfiguration().windowConfiguration.getBounds());
            expectedBounds.inset(insets);
            final Display display = activity.getDisplay();
            assertNotNull(display);
            final DisplayMetrics displayMetrics = new DisplayMetrics();
            display.getMetrics(displayMetrics);
            assertEquals(
                    "The width must exclude display cutout and navigation bar area.",
                    expectedBounds.width(),
                    displayMetrics.widthPixels);
            assertEquals(
                    "The height must exclude display cutout and navigation bar area.",
                    expectedBounds.height(),
                    displayMetrics.heightPixels);
        });
    }

    public static class OptOutEdgeToEdgeActivity extends TestActivity {
    }
}
