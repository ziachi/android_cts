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

package android.server.wm.activity;

import static android.server.wm.WindowManagerState.STATE_RESUMED;

import android.platform.test.annotations.Presubmit;
import android.server.wm.overlay.Components;

import androidx.annotation.NonNull;

import org.junit.Test;

/**
 * Executes the same tests as ActivityRecordInputSinkTests to ensure compatibility with target SDK
 * 35 and below. Opt-in is not required for cross-uid pass-through touches.
 *
 * Build/Install/Run:
 * atest CtsWindowManagerSdk35TestCases:ActivityRecordInputSinkTestsSdk35
 */
@Presubmit
public class ActivityRecordInputSinkTestsSdk35 extends ActivityRecordInputSinkTestsBase {
    private static final String APP_SELF = "android.server.wm.cts.testsdk35";

    @Override
    @NonNull
    String getAppSelf() {
        return APP_SELF;
    }

    @Test
    public void testOverlappingActivityInSameTaskDifferentUid_compat_AllowsTouches() {
        launchActivity(mTestActivity);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInSameTask(OVERLAY_IN_DIFFERENT_UID);
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        mWmState.assertActivityDisplayed(OVERLAY_IN_DIFFERENT_UID);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @Test
    public void testOverlappingActivityInSameTaskTrampolineDifferentUid_compat_AllowsTouches() {
        launchActivity(mTestActivity);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInSameTask(TRAMPOLINE_DIFFERENT_UID,
                Components.TrampolineActivity.buildTrampolineExtra(OVERLAY_IN_DIFFERENT_UID));
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        mWmState.waitAndAssertActivityRemoved(TRAMPOLINE_DIFFERENT_UID);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }
}
