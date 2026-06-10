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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.server.wm.ActivityLauncher.KEY_ACTIVITY_TYPE;
import static android.server.wm.ActivityLauncher.KEY_DISPLAY_ID;
import static android.server.wm.ActivityLauncher.KEY_INTENT_EXTRAS;
import static android.server.wm.ActivityLauncher.KEY_INTENT_FLAGS;
import static android.server.wm.ActivityLauncher.KEY_LAUNCH_ACTIVITY;
import static android.server.wm.ActivityLauncher.KEY_LAUNCH_TASK_BEHIND;
import static android.server.wm.ActivityLauncher.KEY_LAUNCH_TO_SIDE;
import static android.server.wm.ActivityLauncher.KEY_MULTIPLE_INSTANCES;
import static android.server.wm.ActivityLauncher.KEY_MULTIPLE_TASK;
import static android.server.wm.ActivityLauncher.KEY_NEW_TASK;
import static android.server.wm.ActivityLauncher.KEY_RANDOM_DATA;
import static android.server.wm.ActivityLauncher.KEY_REORDER_TO_FRONT;
import static android.server.wm.ActivityLauncher.KEY_SUPPRESS_EXCEPTIONS;
import static android.server.wm.ActivityLauncher.KEY_TARGET_COMPONENT;
import static android.server.wm.ActivityLauncher.KEY_TASK_DISPLAY_AREA_FEATURE_ID;
import static android.server.wm.ActivityLauncher.KEY_USE_APPLICATION_CONTEXT;
import static android.server.wm.ActivityLauncher.KEY_WINDOWING_MODE;
import static android.server.wm.ActivityLauncher.launchActivityFromExtras;
import static android.server.wm.CommandSession.KEY_FORWARD;
import static android.server.wm.ComponentNameUtils.getActivityName;
import static android.server.wm.ShellCommandHelper.executeShellCommand;
import static android.server.wm.app.Components.LAUNCHING_ACTIVITY;
import static android.server.wm.app.Components.LaunchingActivity.KEY_FINISH_BEFORE_LAUNCH;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.view.Display.INVALID_DISPLAY;
import static android.window.DisplayAreaOrganizer.FEATURE_UNDEFINED;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Consumer;

public class LaunchActivityBuilder implements CommandSession.LaunchProxy {
    @NonNull
    private final WindowManagerStateHelper mAmWmState;

    // The activity to be launched.
    @NonNull
    private ComponentName mTargetActivity = TEST_ACTIVITY;

    private boolean mUseApplicationContext;
    private boolean mToSide;
    private boolean mRandomData;
    private boolean mNewTask;
    private boolean mMultipleTask;
    private boolean mAllowMultipleInstances = true;
    private boolean mLaunchTaskBehind;
    private boolean mFinishBeforeLaunch;
    private int mDisplayId = INVALID_DISPLAY;
    private int mWindowingMode = -1;
    private int mActivityType = ACTIVITY_TYPE_UNDEFINED;

    // A proxy activity that launches other activities including mTargetActivityName.
    @NonNull
    private ComponentName mLaunchingActivity = LAUNCHING_ACTIVITY;

    private boolean mReorderToFront;
    private boolean mWaitForLaunched;
    private boolean mSuppressExceptions;
    private boolean mWithShellPermission;

    // Use of the following variables indicates that a broadcast receiver should be used instead
    // of a launching activity.
    @Nullable
    private ComponentName mBroadcastReceiver;
    @Nullable
    private String mBroadcastReceiverAction;
    private int mIntentFlags;
    @Nullable
    private Bundle mExtras;
    @Nullable
    private CommandSession.LaunchInjector mLaunchInjector;
    @Nullable
    private CommandSession.ActivitySessionClient mActivitySessionClient;
    private int mLaunchTaskDisplayAreaFeatureId = FEATURE_UNDEFINED;

    private enum LauncherType {
        INSTRUMENTATION,
        LAUNCHING_ACTIVITY,
        BROADCAST_RECEIVER,
    }

    @NonNull
    private LauncherType mLauncherType = LauncherType.LAUNCHING_ACTIVITY;

    public LaunchActivityBuilder(@NonNull WindowManagerStateHelper amWmState) {
        mAmWmState = amWmState;
        mWaitForLaunched = true;
        mWithShellPermission = true;
    }

    /**
     * Sets whether the activity should be launched to side in split-screen.
     *
     * @param toSide {@code true} if the activity should be launched to the side,
     *               {@code false} otherwise.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_LAUNCH_TO_SIDE
     */
    @NonNull
    public LaunchActivityBuilder setToSide(boolean toSide) {
        mToSide = toSide;
        return this;
    }

    /**
     * Sets whether random data should be included in the launch intent to be different from other
     * launch intents.
     *
     * @param randomData {@code true} if random data should be included, {@code false} otherwise.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_RANDOM_DATA
     */
    @NonNull
    public LaunchActivityBuilder setRandomData(boolean randomData) {
        mRandomData = randomData;
        return this;
    }

    /**
     * Sets whether launch intent should have {@link Intent#FLAG_ACTIVITY_NEW_TASK}.
     *
     * @param newTask {@code true} if the launch intent should have
     *                {@link Intent#FLAG_ACTIVITY_NEW_TASK}, {@code false} otherwise.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_NEW_TASK
     */
    @NonNull
    public LaunchActivityBuilder setNewTask(boolean newTask) {
        mNewTask = newTask;
        return this;
    }

    /**
     * Sets whether launch intent should have {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK}.
     *
     * @param multipleTask {@code true} if the launch intent should have
     *                     {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK}, {@code false} otherwise.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_MULTIPLE_TASK
     */
    @NonNull
    public LaunchActivityBuilder setMultipleTask(boolean multipleTask) {
        mMultipleTask = multipleTask;
        return this;
    }

    /**
     * Sets whether need to automatically applies {@link Intent#FLAG_ACTIVITY_NEW_TASK} and
     * {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK} to the intent when target display id set.
     *
     * @param allowMultipleInstances {@code true} if multiple instances are allowed,
     *                               {@code false} otherwise.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_MULTIPLE_INSTANCES
     */
    @NonNull
    public LaunchActivityBuilder allowMultipleInstances(boolean allowMultipleInstances) {
        mAllowMultipleInstances = allowMultipleInstances;
        return this;
    }

    /**
     * Sets if the launch task without presented to user.
     *
     * @param launchTaskBehind {@code true} if the launch task without presented to user,
     *                         {@code false} otherwise.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityOptions#makeTaskLaunchBehind()
     */
    @NonNull
    public LaunchActivityBuilder setLaunchTaskBehind(boolean launchTaskBehind) {
        mLaunchTaskBehind = launchTaskBehind;
        return this;
    }

    /**
     * Sets whether launch intent should have {@link Intent#FLAG_ACTIVITY_REORDER_TO_FRONT}.
     *
     * @param reorderToFront {@code true} if the launch intent should have
     *                       {@link Intent#FLAG_ACTIVITY_REORDER_TO_FRONT},
     *                       {@code false} otherwise.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_REORDER_TO_FRONT
     */
    @NonNull
    public LaunchActivityBuilder setReorderToFront(boolean reorderToFront) {
        mReorderToFront = reorderToFront;
        return this;
    }

    /**
     * Sets whether to use the application context when launching the activity.
     *
     * @param useApplicationContext {@code true} if the application context should be used,
     *                              {@code false} otherwise.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_USE_APPLICATION_CONTEXT
     */
    @NonNull
    public LaunchActivityBuilder setUseApplicationContext(boolean useApplicationContext) {
        mUseApplicationContext = useApplicationContext;
        return this;
    }

    /**
     * Sets whether the launching activity should be finished before launching the target activity.
     *
     * @param finishBeforeLaunch {@code true} if the launching activity should be finished before
     *                           launch, {@code false} otherwise.
     * @return this LaunchActivityBuilder instance for chaining
     * @see android.server.wm.app.LaunchingActivity
     */
    @NonNull
    public LaunchActivityBuilder setFinishBeforeLaunch(boolean finishBeforeLaunch) {
        mFinishBeforeLaunch = finishBeforeLaunch;
        return this;
    }

    @NonNull
    public ComponentName getTargetActivity() {
        return mTargetActivity;
    }

    public boolean isTargetActivityTranslucent() {
        return mAmWmState.isActivityTranslucent(mTargetActivity);
    }

    /**
     * Sets the target activity to be launched.
     *
     * @param targetActivity the component name of the target activity.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_TARGET_COMPONENT
     */
    @NonNull
    public LaunchActivityBuilder setTargetActivity(@NonNull ComponentName targetActivity) {
        mTargetActivity = targetActivity;
        return this;
    }

    /**
     * Sets the display ID on which the activity should be launched. Adding this automatically
     * applies {@link Intent#FLAG_ACTIVITY_NEW_TASK} and {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK}
     * to the intent.
     *
     * @param id the ID of the display.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_DISPLAY_ID
     */
    @NonNull
    public LaunchActivityBuilder setDisplayId(int id) {
        mDisplayId = id;
        return this;
    }

    /**
     * Sets the requested windowing mode.
     *
     * @param windowingMode the requested windowing mode.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_WINDOWING_MODE
     */
    @NonNull
    public LaunchActivityBuilder setWindowingMode(int windowingMode) {
        mWindowingMode = windowingMode;
        return this;
    }

    /**
     * Sets the target activity type where activity should be launched as.
     *
     * @param type the target activity type.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_ACTIVITY_TYPE
     */
    @NonNull
    public LaunchActivityBuilder setActivityType(int type) {
        mActivityType = type;
        return this;
    }

    /**
     * Sets the launching activity that will be used to launch the target activity.
     *
     * @param launchingActivity The component name of the launching activity.
     * @return this LaunchActivityBuilder instance for chaining.
     */
    @NonNull
    public LaunchActivityBuilder setLaunchingActivity(@NonNull ComponentName launchingActivity) {
        mLaunchingActivity = launchingActivity;
        mLauncherType = LauncherType.LAUNCHING_ACTIVITY;
        return this;
    }

    /**
     * Sets whether to wait for the launched activity to be in a valid state.
     *
     * @param shouldWait {@code true} if the builder should wait, {@code false} otherwise.
     * @return this LaunchActivityBuilder instance for chaining.
     */
    @NonNull
    public LaunchActivityBuilder setWaitForLaunched(boolean shouldWait) {
        mWaitForLaunched = shouldWait;
        return this;
    }

    /**
     * Sets the TaskDisplayArea feature ID in which the activity should be launched in.
     *
     * @param launchTaskDisplayAreaFeatureId the TaskDisplayArea feature ID.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_TASK_DISPLAY_AREA_FEATURE_ID
     */
    @NonNull
    public LaunchActivityBuilder setLaunchTaskDisplayAreaFeatureId(
            int launchTaskDisplayAreaFeatureId) {
        mLaunchTaskDisplayAreaFeatureId = launchTaskDisplayAreaFeatureId;
        return this;
    }

    /**
     * Uses broadcast receiver as a launchpad for activities.
     *
     * @param broadcastReceiver the component name of the broadcast receiver.
     * @param broadcastAction   the action of the broadcast.
     * @return this LaunchActivityBuilder instance for chaining.
     */
    @NonNull
    public LaunchActivityBuilder setUseBroadcastReceiver(
            @Nullable final ComponentName broadcastReceiver,
            @Nullable final String broadcastAction) {
        mBroadcastReceiver = broadcastReceiver;
        mBroadcastReceiverAction = broadcastAction;
        mLauncherType = LauncherType.BROADCAST_RECEIVER;
        return this;
    }

    /**
     * Uses {@link android.app.Instrumentation} as a launchpad for activities.
     *
     * @return this LaunchActivityBuilder instance for chaining.
     */
    @NonNull
    public LaunchActivityBuilder setUseInstrumentation() {
        mLauncherType = LauncherType.INSTRUMENTATION;
        // Calling startActivity() from outside of an Activity context requires the
        // FLAG_ACTIVITY_NEW_TASK flag.
        setNewTask(true);
        return this;
    }

    /**
     * Sets whether exceptions during launch other then {@link SecurityException} should be
     * suppressed. A {@link SecurityException} is never thrown, it's always written to logs.
     *
     * @param suppress {@code true} to suppress exceptions, {@code false} otherwise.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_SUPPRESS_EXCEPTIONS
     */
    @NonNull
    public LaunchActivityBuilder setSuppressExceptions(boolean suppress) {
        mSuppressExceptions = suppress;
        return this;
    }

    /**
     * Sets whether the launch should be performed with shell permissions.
     *
     * @param withShellPermission {@code true} if the launch should be performed with shell
     *                            permissions, {@code false} otherwise.
     * @return this LaunchActivityBuilder instance for chaining.
     */
    @NonNull
    public LaunchActivityBuilder setWithShellPermission(boolean withShellPermission) {
        mWithShellPermission = withShellPermission;
        return this;
    }

    /**
     * Sets the activity session client to be used for launching the activity.
     *
     * @param sessionClient the activity session client, or {@code null} if not using a session
     *                      client.
     * @return this LaunchActivityBuilder instance for chaining.
     */
    @NonNull
    public LaunchActivityBuilder setActivitySessionClient(
            @Nullable CommandSession.ActivitySessionClient sessionClient) {
        mActivitySessionClient = sessionClient;
        return this;
    }

    @Override
    public boolean shouldWaitForLaunched() {
        return mWaitForLaunched;
    }

    /**
     * Sets additional flags for the launch intent.
     *
     * @param flags the intent flags to be added.
     * @return this LaunchActivityBuilder instance for chaining.
     * @see ActivityLauncher#KEY_INTENT_FLAGS
     */
    @NonNull
    public LaunchActivityBuilder setIntentFlags(int flags) {
        mIntentFlags = flags;
        return this;
    }

    /**
     * Sets extras for the launch intent.
     *
     * @param extrasConsumer a consumer that will be called to populate the extras bundle,
     *                       or {@code null} to do nothing.
     * @return this LaunchActivityBuilder instance for chaining.
     */
    @NonNull
    public LaunchActivityBuilder setIntentExtra(@Nullable Consumer<Bundle> extrasConsumer) {
        if (extrasConsumer != null) {
            mExtras = new Bundle();
            extrasConsumer.accept(mExtras);
        }
        return this;
    }

    @Override
    @Nullable
    public Bundle getExtras() {
        return mExtras;
    }

    /**
     * Sets the launch injector to be used for customizing the launch parameter.
     *
     * @param injector the launch injector to use, or {@code null} if no customization is needed.
     * @see CommandSession.LaunchInjector
     */
    @Override
    public void setLaunchInjector(@Nullable CommandSession.LaunchInjector injector) {
        mLaunchInjector = injector;
    }

    @Override
    public void execute() {
        if (mActivitySessionClient != null) {
            final CommandSession.ActivitySessionClient client = mActivitySessionClient;
            // Clear the session client so its startActivity can call the real execute().
            mActivitySessionClient = null;
            client.startActivity(this);
            return;
        }
        switch (mLauncherType) {
            case INSTRUMENTATION:
                if (mWithShellPermission) {
                    NestedShellPermission.run(this::launchUsingInstrumentation);
                } else {
                    launchUsingInstrumentation();
                }
                break;
            case LAUNCHING_ACTIVITY:
            case BROADCAST_RECEIVER:
                launchUsingShellCommand();
        }

        if (mWaitForLaunched) {
            mAmWmState.waitForValidState(mTargetActivity);
        }
    }

    /** Launches an activity using instrumentation. */
    private void launchUsingInstrumentation() {
        final Bundle b = new Bundle();
        b.putBoolean(KEY_LAUNCH_ACTIVITY, true);
        b.putBoolean(KEY_LAUNCH_TO_SIDE, mToSide);
        b.putBoolean(KEY_RANDOM_DATA, mRandomData);
        b.putBoolean(KEY_NEW_TASK, mNewTask);
        b.putBoolean(KEY_MULTIPLE_TASK, mMultipleTask);
        b.putBoolean(KEY_MULTIPLE_INSTANCES, mAllowMultipleInstances);
        b.putBoolean(KEY_LAUNCH_TASK_BEHIND, mLaunchTaskBehind);
        b.putBoolean(KEY_REORDER_TO_FRONT, mReorderToFront);
        b.putInt(KEY_DISPLAY_ID, mDisplayId);
        b.putInt(KEY_WINDOWING_MODE, mWindowingMode);
        b.putInt(KEY_ACTIVITY_TYPE, mActivityType);
        b.putBoolean(KEY_USE_APPLICATION_CONTEXT, mUseApplicationContext);
        b.putString(KEY_TARGET_COMPONENT, getActivityName(mTargetActivity));
        b.putBoolean(KEY_SUPPRESS_EXCEPTIONS, mSuppressExceptions);
        b.putInt(KEY_INTENT_FLAGS, mIntentFlags);
        b.putBundle(KEY_INTENT_EXTRAS, getExtras());
        b.putInt(KEY_TASK_DISPLAY_AREA_FEATURE_ID, mLaunchTaskDisplayAreaFeatureId);
        final Context context = getInstrumentation().getContext();
        launchActivityFromExtras(context, b, mLaunchInjector);
    }

    /** Builds and executes a shell command to launch an activity. */
    private void launchUsingShellCommand() {
        final StringBuilder commandBuilder = new StringBuilder();
        if (mBroadcastReceiver != null && mBroadcastReceiverAction != null) {
            // Use broadcast receiver to launch the target.
            commandBuilder.append("am broadcast -a ").append(mBroadcastReceiverAction)
                    .append(" -p ").append(mBroadcastReceiver.getPackageName())
                    // Include stopped packages
                    .append(" -f 0x00000020");
        } else {
            // If new task flag isn't set the windowing mode of launcher activity will be the
            // windowing mode of the target activity, so we need to launch launcher activity in
            // it.
            String amStartCmd =
                    (mWindowingMode == -1 || mNewTask)
                            ? ActivityManagerTestBase.getAmStartCmd(mLaunchingActivity)
                            : ActivityManagerTestBase.getAmStartCmd(mLaunchingActivity, mDisplayId)
                                    + " --windowingMode " + mWindowingMode;
            // Use launching activity to launch the target.
            commandBuilder.append(amStartCmd)
                    .append(" -f 0x20000020");
        }

        // Add user for which activity needs to be started
        commandBuilder.append(" --user ").append(Process.myUserHandle().getIdentifier());

        // Add a flag to ensure we actually mean to launch an activity.
        commandBuilder.append(" --ez " + KEY_LAUNCH_ACTIVITY + " true");

        if (mToSide) {
            commandBuilder.append(" --ez " + KEY_LAUNCH_TO_SIDE + " true");
        }
        if (mRandomData) {
            commandBuilder.append(" --ez " + KEY_RANDOM_DATA + " true");
        }
        if (mNewTask) {
            commandBuilder.append(" --ez " + KEY_NEW_TASK + " true");
        }
        if (mMultipleTask) {
            commandBuilder.append(" --ez " + KEY_MULTIPLE_TASK + " true");
        }
        if (mAllowMultipleInstances) {
            commandBuilder.append(" --ez " + KEY_MULTIPLE_INSTANCES + " true");
        }
        if (mReorderToFront) {
            commandBuilder.append(" --ez " + KEY_REORDER_TO_FRONT + " true");
        }
        if (mFinishBeforeLaunch) {
            commandBuilder.append(" --ez " + KEY_FINISH_BEFORE_LAUNCH + " true");
        }
        if (mDisplayId != INVALID_DISPLAY) {
            commandBuilder.append(" --ei " + KEY_DISPLAY_ID + " ").append(mDisplayId);
        }
        if (mWindowingMode != -1) {
            commandBuilder.append(" --ei " + KEY_WINDOWING_MODE + " ").append(mWindowingMode);
        }
        if (mActivityType != ACTIVITY_TYPE_UNDEFINED) {
            commandBuilder.append(" --ei " + KEY_ACTIVITY_TYPE + " ").append(mActivityType);
        }

        if (mUseApplicationContext) {
            commandBuilder.append(" --ez " + KEY_USE_APPLICATION_CONTEXT + " true");
        }

        if (mTargetActivity != null) {
            // {@link ActivityLauncher} parses this extra string by
            // {@link ComponentName#unflattenFromString(String)}.
            commandBuilder.append(" --es " + KEY_TARGET_COMPONENT + " ")
                    .append(getActivityName(mTargetActivity));
        }

        if (mSuppressExceptions) {
            commandBuilder.append(" --ez " + KEY_SUPPRESS_EXCEPTIONS + " true");
        }

        if (mIntentFlags != 0) {
            commandBuilder.append(" --ei " + KEY_INTENT_FLAGS + " ").append(mIntentFlags);
        }

        if (mLaunchTaskDisplayAreaFeatureId != FEATURE_UNDEFINED) {
            commandBuilder.append(" --task-display-area-feature-id ")
                    .append(mLaunchTaskDisplayAreaFeatureId);
            commandBuilder.append(" --ei " + KEY_TASK_DISPLAY_AREA_FEATURE_ID + " ")
                    .append(mLaunchTaskDisplayAreaFeatureId);
        }

        if (mLaunchInjector != null) {
            commandBuilder.append(" --ez " + KEY_FORWARD + " true");
            mLaunchInjector.setupShellCommand(commandBuilder);
        }
        executeShellCommand(commandBuilder.toString());
    }
}
