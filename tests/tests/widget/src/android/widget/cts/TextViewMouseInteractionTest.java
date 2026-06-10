/*
 * Copyright (C) 2008 The Android Open Source Project
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


import static android.content.pm.PackageManager.FEATURE_TOUCHSCREEN;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.allOf;

import android.Manifest;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.ActionMode;
import android.view.InputDevice;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.UserHelper;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextViewMouseInteractionTest {
    private static final String CUSTOM_FLOATING_TOOLBAR_LABEL = "@B(DE";

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityScenarioRule<TextViewMouseInteractionActivity> rule = new ActivityScenarioRule<>(
            TextViewMouseInteractionActivity.class);

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private static final UiDevice sDevice = UiDevice.getInstance(sInstrumentation);
    private UserHelper mUserHelper;
    private TextView mTextView;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(
                ApplicationProvider.getApplicationContext().getPackageManager()
                        .hasSystemFeature(FEATURE_TOUCHSCREEN));
        sDevice.wakeUp();
        dismissKeyguard();
        closeSystemDialog();
        mUserHelper = new UserHelper(sInstrumentation.getTargetContext());
    }

    private void dismissKeyguard() {
        ShellUtils.runShellCommand("wm dismiss-keyguard");
    }

    private static void closeSystemDialog() {
        ShellUtils.runShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS");
    }

    ActionMode.Callback mTestCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_floating_toolbar, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {

        }
    };

    @Test
    @ApiTest(apis = {"android.widget.TextView#setCustomSelectionActionModeCallback"})
    public void testFloatingToolbarByTouch() {
        final String text = "Android";
        rule.getScenario().onActivity(activity -> {
            final TextView textView = activity.findViewById(R.id.textView);
            textView.setTextIsSelectable(true);
            textView.setCustomSelectionActionModeCallback(mTestCallback);
            textView.setText(text);
            mTextView = textView;
        });

        onView(allOf(withId(R.id.textView), withText(text))).check(matches(isDisplayed()));
        emulateLongClickOnViewCenter(mTextView, InputDevice.SOURCE_TOUCHSCREEN);

        assertThat(sDevice.hasObject(By.text(CUSTOM_FLOATING_TOOLBAR_LABEL))).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.widget.TextView#setCustomSelectionActionModeCallback"})
    public void testFloatingToolbarByMouse() {
        final String text = "Android";
        rule.getScenario().onActivity(activity -> {
            final TextView textView = activity.findViewById(R.id.textView);
            textView.setTextIsSelectable(true);
            textView.setCustomSelectionActionModeCallback(mTestCallback);
            textView.setText(text);
            mTextView = textView;
        });

        onView(allOf(withId(R.id.textView), withText(text))).check(matches(isDisplayed()));
        emulateLongClickOnViewCenter(mTextView, InputDevice.SOURCE_MOUSE);

        assertThat(sDevice.hasObject(By.text(CUSTOM_FLOATING_TOOLBAR_LABEL))).isFalse();
    }

    /**
     * Emulates a long click on the center of the given TextView.
     *
     * The espresso library does not support multiple displays. Therefore, we need to rely on
     * Instrumentation#sendPointerSync to handle the motion events for multi-display environments.
     */
    private void emulateLongClickOnViewCenter(TextView textView, int inputSource) {
        final int[] viewOnScreenXY = new int[2];
        textView.getLocationOnScreen(viewOnScreenXY);
        final int xOnScreen = viewOnScreenXY[0] + textView.getWidth() / 2;
        final int yOnScreen = viewOnScreenXY[1] + textView.getHeight() / 2;

        injectMotionEvent(MotionEvent.ACTION_DOWN, xOnScreen, yOnScreen, inputSource);
        SystemClock.sleep((long)(ViewConfiguration.getLongPressTimeout() * 1.5f));
        injectMotionEvent(MotionEvent.ACTION_UP, xOnScreen, yOnScreen, inputSource);

        sInstrumentation.waitForIdleSync();
    }

    private void injectMotionEvent(int action, int x, int y, int inputSource) {
        final long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, /* metaState= */ 0);
        event.setSource(inputSource);
        event.setButtonState(MotionEvent.BUTTON_PRIMARY);
        mUserHelper.injectDisplayIdIfNeeded(event);
        sInstrumentation.sendPointerSync(event);
        event.recycle();
    }
}

