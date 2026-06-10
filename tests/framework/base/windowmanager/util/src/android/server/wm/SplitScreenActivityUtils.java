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

package android.server.wm;

import static android.server.wm.StateLogger.log;
import static android.view.Display.DEFAULT_DISPLAY;

import static java.util.Objects.requireNonNull;

import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

import androidx.annotation.NonNull;

public class SplitScreenActivityUtils {
    @NonNull
    private final WindowManagerStateHelper mWmState;
    @NonNull
    private final TestTaskOrganizer mTaskOrganizer;

    public SplitScreenActivityUtils(@NonNull WindowManagerStateHelper wmState,
            @NonNull TestTaskOrganizer organizer) {
        mWmState = wmState;
        mTaskOrganizer = organizer;
    }

    /** Returns true if the default display supports split screen multi-window. */
    public static boolean supportsSplitScreenMultiWindow(@NonNull Context context) {
        final DisplayManager displayManager =
                requireNonNull(context.getSystemService(DisplayManager.class));
        final Display defaultDisplay = displayManager.getDisplay(DEFAULT_DISPLAY);
        final Context displayContext = context.createDisplayContext(defaultDisplay);
        return ActivityTaskManager.supportsSplitScreenMultiWindow(displayContext);
    }

    /** Puts the specified activity into the primary split of the split screen. */
    public void putActivityInPrimarySplit(@NonNull ComponentName activityName) {
        final int taskId = mWmState.getTaskByActivity(activityName).getTaskId();
        mTaskOrganizer.putTaskInSplitPrimary(taskId);
        mWmState.waitForValidState(activityName);
    }

    /** Puts the specified activity into the secondary split of the split screen. */
    public void putActivityInSecondarySplit(@NonNull ComponentName activityName) {
        final int taskId = mWmState.getTaskByActivity(activityName).getTaskId();
        mTaskOrganizer.putTaskInSplitSecondary(taskId);
        mWmState.waitForValidState(activityName);
    }

    /**
     * Launches {@code primaryActivity} into split-screen primary windowing mode
     * and {@code secondaryActivity} to the side in split-screen secondary windowing mode.
     */
    public void launchActivitiesInSplitScreen(@NonNull LaunchActivityBuilder primaryActivity,
            @NonNull LaunchActivityBuilder secondaryActivity) {
        // Launch split-screen primary.
        primaryActivity
                .setUseInstrumentation()
                .setWaitForLaunched(true)
                .execute();

        final int primaryTaskId =
                mWmState.getTaskByActivity(primaryActivity.getTargetActivity()).getTaskId();
        mTaskOrganizer.putTaskInSplitPrimary(primaryTaskId);

        // Launch split-screen secondary
        secondaryActivity
                .setUseInstrumentation()
                .setWaitForLaunched(true)
                .setNewTask(true)
                .setMultipleTask(true)
                .execute();

        final int secondaryTaskId =
                mWmState.getTaskByActivity(secondaryActivity.getTargetActivity()).getTaskId();
        mTaskOrganizer.putTaskInSplitSecondary(secondaryTaskId);
        mWmState.computeState(primaryActivity.getTargetActivity(),
                secondaryActivity.getTargetActivity());
        log("launchActivitiesInSplitScreen(), primaryTaskId=" + primaryTaskId
                + ", secondaryTaskId=" + secondaryTaskId);
    }

    /**
     * Moves the task of {@code primaryActivity} into split-screen primary and the task of
     * {@code secondaryActivity} to the side in split-screen secondary.
     */
    public void moveActivitiesToSplitScreen(@NonNull ComponentName primaryActivity,
            @NonNull ComponentName secondaryActivity) {
        final int primaryTaskId = mWmState.getTaskByActivity(primaryActivity).getTaskId();
        mTaskOrganizer.putTaskInSplitPrimary(primaryTaskId);

        final int secondaryTaskId = mWmState.getTaskByActivity(secondaryActivity).getTaskId();
        mTaskOrganizer.putTaskInSplitSecondary(secondaryTaskId);

        mWmState.computeState(primaryActivity, secondaryActivity);
        log("moveActivitiesToSplitScreen(), primaryTaskId=" + primaryTaskId
                + ", secondaryTaskId=" + secondaryTaskId);
    }

    /** Dismisses split-screen mode, optionally keeping the primary activity on top. */
    public void dismissSplitScreen(boolean primaryOnTop) {
        mTaskOrganizer.dismissSplitScreen(primaryOnTop);
    }
}
