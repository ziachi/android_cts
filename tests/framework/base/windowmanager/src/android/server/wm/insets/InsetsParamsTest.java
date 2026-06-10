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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.ActivityOptions;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.MultiDisplayTestBase;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerStateHelper;
import android.server.wm.insets.DecorInsetTestsBase.TestActivity;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Test provided insets via WindowManager.InsetParams
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceInsets:InsetsParamsTest
 */
@RequiresFlagsEnabled(Flags.FLAG_STATUS_BAR_AND_INSETS)
public class InsetsParamsTest extends MultiDisplayTestBase {

    private static final String INSET_PROVIDER_NAME = "android.server.wm.insets.INSET_PROVIDER";
    private static final int STATUS_BAR_HEIGHT = 80;
    private static final Insets PROVIDED_INSETS = Insets.of(0, STATUS_BAR_HEIGHT, 0, 0);
    private static final int PROVIDED_INSETS_TYPE = WindowInsets.Type.statusBars();

    @Rule
    public CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testInsetsParams_withoutInsetsSize() {
        WindowManager.InsetsParams insetsParams =
                new WindowManager.InsetsParams(PROVIDED_INSETS_TYPE);
        assertThat(insetsParams.getType()).isEqualTo(PROVIDED_INSETS_TYPE);
        assertThat(insetsParams.getInsetsSize()).isNull();
    }

    @Test
    public void testInsetsParams_withInsetsSize() {
        WindowManager.InsetsParams insetsParams =
                new WindowManager.InsetsParams(PROVIDED_INSETS_TYPE).setInsetsSize(PROVIDED_INSETS);
        assertThat(insetsParams.getType()).isEqualTo(PROVIDED_INSETS_TYPE);
        assertThat(insetsParams.getInsetsSize()).isEqualTo(PROVIDED_INSETS);
    }

    @Test
    public void testInsetsParams_providesInsets() throws Exception {
        // Inject insets on a new display so we don't mess with any existing insets.
        assumeTrue(supportsMultiDisplay());
        WindowManagerState.DisplayContent displayContent = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true)
                .setOwnContentOnly(true)
                .createDisplay();

        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display display = displayManager.getDisplay(displayContent.mId);
        Context displayContext = mContext.createDisplayContext(display);

        addStatusBar(displayContext);

        WindowInsets actualInsets = getActualInsets(displayContext);
        assertThat(actualInsets.isVisible(PROVIDED_INSETS_TYPE)).isTrue();
        assertThat(actualInsets.getInsets(PROVIDED_INSETS_TYPE)).isEqualTo(PROVIDED_INSETS);
    }

    private static void addStatusBar(Context displayContext) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                STATUS_BAR_HEIGHT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR,
                /* _flags= */ 0,
                PixelFormat.TRANSLUCENT);
        lp.setTitle(INSET_PROVIDER_NAME);
        lp.packageName = displayContext.getPackageName();
        lp.gravity = Gravity.TOP;
        lp.setFitInsetsTypes(0);
        lp.setInsetsParams(List.of(
                new WindowManager.InsetsParams(PROVIDED_INSETS_TYPE)
                        .setInsetsSize(PROVIDED_INSETS)));

        WindowManager windowManager = displayContext.getSystemService(WindowManager.class);
        View insetsProvider = new View(displayContext);
        getInstrumentation().runOnMainSync(() ->
                SystemUtil.runWithShellPermissionIdentity(() ->
                        windowManager.addView(insetsProvider, lp)));

        new WindowManagerStateHelper().assertWindowDisplayed(INSET_PROVIDER_NAME);
    }

    private static WindowInsets getActualInsets(Context displayContext) throws Exception {
        TestActivity activity = (TestActivity) getInstrumentation().startActivitySync(
                new Intent(displayContext, TestActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ActivityOptions.makeBasic()
                        .setLaunchDisplayId(displayContext.getDisplayId()).toBundle());
        activity.mLaidOut.await(4, TimeUnit.SECONDS);
        return activity.mLastDecorInsets;
    }
}
