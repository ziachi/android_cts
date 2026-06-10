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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.Bundle;
import android.server.wm.WindowManagerTestBase;
import android.view.View;
import android.view.WindowInsets;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WindowPolicyTestBase extends WindowManagerTestBase {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestActivity.sStyleIdList.clear();
        TestActivity.sLayoutInDisplayCutoutMode =
                TestActivity.LAYOUT_IN_DISPLAY_CUTOUT_MODE_UNSPECIFIED;
    }

    static <T extends TestActivity> TestActivity startActivitySync(Class<T> activityClass) {
        final TestActivity activity = startActivity(activityClass);
        activity.waitForLayout();
        return activity;
    }

    static void runOnMainSync(Runnable runnable) {
        getInstrumentation().runOnMainSync(runnable);
    }

    public static class TestActivity extends FocusableActivity {

        private static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_UNSPECIFIED = -1;

        static ArrayList<Integer> sStyleIdList = new ArrayList<>();
        static int sLayoutInDisplayCutoutMode = -1;

        private ContentView mContentView;
        private final CountDownLatch mLayoutLatch = new CountDownLatch(1);

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            for (int i = sStyleIdList.size() - 1; i >= 0; i--) {
                getTheme().applyStyle(sStyleIdList.get(i), true /* force */);
            }
            if (sLayoutInDisplayCutoutMode != LAYOUT_IN_DISPLAY_CUTOUT_MODE_UNSPECIFIED) {
                getWindow().getAttributes().layoutInDisplayCutoutMode = sLayoutInDisplayCutoutMode;
            }
            mContentView = new ContentView(this);
            mContentView.getViewTreeObserver().addOnGlobalLayoutListener(mLayoutLatch::countDown);
            setContentView(mContentView);
        }

        public ContentView getContentView() {
            return mContentView;
        }

        private void waitForLayout() {
            final String errorMessage = "Unable to wait for layout.";
            try {
                assertTrue(errorMessage, mLayoutLatch.await(3, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                fail(errorMessage);
            }
        }

        static class ContentView extends View {

            private WindowInsets mWindowInsets;

            private ContentView(Context context) {
                super(context);
            }

            public WindowInsets getWindowInsets() {
                return mWindowInsets;
            }

            @Override
            public WindowInsets onApplyWindowInsets(WindowInsets insets) {
                mWindowInsets = insets;
                return super.onApplyWindowInsets(insets);
            }
        }
    }
}
