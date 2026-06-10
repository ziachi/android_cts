/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.server.wm.activity;

import static android.server.wm.WindowManagerState.STATE_PAUSED;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.overlay.Components.TranslucentFloatingActivity.ACTION_FINISH;
import static android.server.wm.overlay.Components.TranslucentFloatingActivity.EXTRA_FADE_EXIT;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityOptions;
import android.content.Intent;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.overlay.Components;

import androidx.annotation.NonNull;

import org.junit.Test;

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceActivity:ActivityRecordInputSinkTests
 */
@Presubmit
public class ActivityRecordInputSinkTests extends ActivityRecordInputSinkTestsBase {

    private static final String APP_SELF = "android.server.wm.cts";

    @Override
    @NonNull
    String getAppSelf() {
        return APP_SELF;
    }

    @Test
    public void testOverlappingActivityInNewTask_BlocksTouches() {
        launchActivity(mTestActivity);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInNewTask(mOverlayInSameUid);
        mWmState.waitAndAssertActivityState(mOverlayInSameUid, STATE_RESUMED);
        touchButtonsAndAssert(false /*expectTouchesToReachActivity*/);

        mContext.sendBroadcast(new Intent(Components.TranslucentFloatingActivity.ACTION_FINISH));
        mWmState.waitAndAssertActivityRemoved(mOverlayInSameUid);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @Test
    public void testOverlappingActivityInSameTaskSameUid_DoesNotBlocksTouches() {
        launchActivity(mTestActivity);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInSameTask(mOverlayInSameUid);
        mWmState.waitAndAssertActivityState(mOverlayInSameUid, STATE_RESUMED);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @RequiresFlagsDisabled(com.android.window.flags.Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN)
    @Test
    public void testOverlappingActivityInSameTaskDifferentUid_DoesNotBlocksTouches() {
        launchActivity(mTestActivity);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInSameTask(OVERLAY_IN_DIFFERENT_UID);
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        mWmState.assertActivityDisplayed(OVERLAY_IN_DIFFERENT_UID);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @RequiresFlagsEnabled(com.android.window.flags.Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN)
    @Test
    public void testOverlappingActivityInSameTaskDifferentUidNoOptIn_BlocksTouches() {
        launchActivity(mTestActivity);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInSameTask(OVERLAY_IN_DIFFERENT_UID);
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        mWmState.assertActivityDisplayed(OVERLAY_IN_DIFFERENT_UID);
        touchButtonsAndAssert(false /*expectTouchesToReachActivity*/);
    }

    @RequiresFlagsEnabled(com.android.window.flags.Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN)
    @Test
    public void testOverlappingActivityInSameTaskDifferentUidOptIn_AllowsTouches() {
        launchActivity(mTestActivity);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setAllowPassThroughOnTouchOutside(/* allowPassThrough */ true);
        launchActivityInSameTask(OVERLAY_IN_DIFFERENT_UID, /* extras */ null, options.toBundle());
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        mWmState.assertActivityDisplayed(OVERLAY_IN_DIFFERENT_UID);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @RequiresFlagsDisabled(com.android.window.flags.Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN)
    @Test
    public void testOverlappingActivityInSameTaskTrampolineDifferentUid_DoesNotBlockTouches() {
        launchActivity(mTestActivity);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInSameTask(TRAMPOLINE_DIFFERENT_UID,
                Components.TrampolineActivity.buildTrampolineExtra(OVERLAY_IN_DIFFERENT_UID));
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        mWmState.waitAndAssertActivityRemoved(TRAMPOLINE_DIFFERENT_UID);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @RequiresFlagsEnabled(com.android.window.flags.Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN)
    @Test
    public void testOverlappingActivityInSameTaskTrampolineDifferentUidNoOptIn_BlocksTouches() {
        launchActivity(mTestActivity);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInSameTask(TRAMPOLINE_DIFFERENT_UID,
                Components.TrampolineActivity.buildTrampolineExtra(OVERLAY_IN_DIFFERENT_UID));
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        mWmState.waitAndAssertActivityRemoved(TRAMPOLINE_DIFFERENT_UID);
        touchButtonsAndAssert(false /*expectTouchesToReachActivity*/);
    }

    @RequiresFlagsEnabled(com.android.window.flags.Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN)
    @Test
    public void testOverlappingActivityInSameTaskTrampolineDifferentUidOptIn_AllowsTouches() {
        launchActivity(mTestActivity);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setAllowPassThroughOnTouchOutside(/* allowPassThrough */ true);
        launchActivityInSameTask(TRAMPOLINE_DIFFERENT_UID,
                Components.TrampolineActivity.buildTrampolineExtra(OVERLAY_IN_DIFFERENT_UID),
                options.toBundle());
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        mWmState.waitAndAssertActivityRemoved(TRAMPOLINE_DIFFERENT_UID);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @Test
    public void testOverlappingActivitySandwich_BlocksTouches() {
        final Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(TRAMPOLINE_DIFFERENT_UID);
        intent.replaceExtras(Components.TrampolineActivity.buildTrampolineExtra(mTestActivity,
                OVERLAY_IN_DIFFERENT_UID));
        mContext.startActivity(intent);

        mWmState.waitAndAssertActivityState(mTestActivity, STATE_PAUSED);
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        touchButtonsAndAssert(false /*expectTouchesToReachActivity*/);

        mContext.sendBroadcast(new Intent(Components.TranslucentFloatingActivity.ACTION_FINISH));
        mWmState.waitAndAssertActivityRemoved(OVERLAY_IN_DIFFERENT_UID);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @Test
    public void testOverlappingActivitySandwichDuringAnimation_DoesNotBlockTouches() {
        final Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(TRAMPOLINE_DIFFERENT_UID);
        intent.replaceExtras(Components.TrampolineActivity.buildTrampolineExtra(mTestActivity,
                OVERLAY_IN_DIFFERENT_UID));
        mContext.startActivity(intent);

        mWmState.waitAndAssertActivityState(mTestActivity, STATE_PAUSED);
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        touchButtonsAndAssert(false /* expectTouchesToReachActivity */);

        final int displayId = mWmState.getTaskByActivity(OVERLAY_IN_DIFFERENT_UID).mDisplayId;
        mContext.sendBroadcast(new Intent(ACTION_FINISH).putExtra(EXTRA_FADE_EXIT, true));
        assertThat(mWmState.waitForAppTransitionRunningOnDisplay(displayId)).isTrue();
        assertThat(mWmState.waitForAppTransitionIdleOnDisplay(displayId)).isTrue();
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/, false /* waitForAnimation */);
    }
}
