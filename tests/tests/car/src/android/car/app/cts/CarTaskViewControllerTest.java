/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.app.cts;


import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
import static android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.KeyEvent.KEYCODE_HOME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.car.app.CarTaskViewController;
import android.car.app.CarTaskViewControllerCallback;
import android.car.app.ControlledRemoteCarTaskView;
import android.car.app.ControlledRemoteCarTaskViewCallback;
import android.car.app.ControlledRemoteCarTaskViewConfig;
import android.car.cts.R;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.NonApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UserHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class CarTaskViewControllerTest {

    private static final String TAG = CarTaskViewControllerTest.class.getSimpleName();
    private static final long QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE_MS = 5000;
    private static final long WAIT_FOR_LAUNCHER_TIME_MS = 2000;

    private final Instrumentation mInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();
    private final Context mTargetContext = mInstrumentation.getTargetContext();
    private final ComponentName mTestActivity =
            new ComponentName(mTargetContext, TestActivity.class);
    private final UiAutomation mUiAutomation = mInstrumentation.getUiAutomation();
    private final UiDevice mUiDevice = UiDevice.getInstance(mInstrumentation);

    private TestCarTaskViewControllerCallback mCallback;
    private TestActivity mHostActivity;
    private CarActivityManager mCarActivityManager;
    private int[] mTmpLocation = new int[2];
    private int mTmpWidth;
    private int mTmpHeight;
    private int mMyDisplayId;


    @Before
    public void setUp() throws Exception {
        Car car = Car.createCar(mContext);
        mUiAutomation.adoptShellPermissionIdentity(
                // for CAM.getCarTaskViewController
                Manifest.permission.INTERACT_ACROSS_USERS,
                Car.PERMISSION_MANAGE_CAR_SYSTEM_UI);

        mCarActivityManager =
                (CarActivityManager) car.getCarManager(Car.CAR_ACTIVITY_SERVICE);
        assertThat(mCarActivityManager).isNotNull();

        assumeTrue(mCarActivityManager.isCarSystemUIProxyRegistered());
        UserHelper userHelper = new UserHelper(mContext);
        mMyDisplayId = userHelper.getMainDisplayId();
        SystemUtil.runShellCommandOrThrow(
                String.format("input -d %d keyevent %d", mMyDisplayId, KEYCODE_HOME));
        Thread.sleep(WAIT_FOR_LAUNCHER_TIME_MS);
        Intent startIntent = Intent.makeMainActivity(mTestActivity)
                .addFlags(FLAG_ACTIVITY_NEW_TASK);
        mHostActivity = (TestActivity) mInstrumentation.startActivitySync(
                startIntent, /* option */ null);
        mCallback = new TestCarTaskViewControllerCallback();
        mTargetContext.getMainExecutor().execute(() ->
                // Main thread required so that activity manager can setPrivateFlags on the window.
                mCarActivityManager.getCarTaskViewController(mHostActivity,
                        mTargetContext.getMainExecutor(),
                        mCallback)
        );
        PollingCheck.waitFor(() -> mCallback.mCarTaskViewController != null,
                "Failed to get the CarTaskViewController");
    }

    @After
    public void tearDown() throws Exception {
        if (mHostActivity != null) {
            mHostActivity.finishAndRemoveTask();
            if (!mHostActivity.waitForDestroyed()) {
                throw new RuntimeException(
                        "Test activity is not destroyed! Please increase the timeout"
                                + " QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE_MS");
            }
            mHostActivity = null;
        }
        if (EmbeddedTestActivity1.sInstance != null) {
            EmbeddedTestActivity1.sInstance.finishAndRemoveTask();
            if (!EmbeddedTestActivity1.sInstance.waitForDestroyed()) {
                throw new RuntimeException(
                        "Test activity is not destroyed! Please increase the timeout"
                                + " QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE_MS");
            }
            EmbeddedTestActivity1.sInstance = null;
        }
        if (EmbeddedTestActivity2.sInstance != null) {
            EmbeddedTestActivity2.sInstance.finishAndRemoveTask();
            if (!EmbeddedTestActivity2.sInstance.waitForDestroyed()) {
                throw new RuntimeException(
                        "Test activity is not destroyed! Please increase the timeout"
                                + " QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE_MS");
            }
            EmbeddedTestActivity2.sInstance = null;
        }
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @ApiTest(apis = {
            "android.car.app.ControlledRemoteCarTaskViewConfig.Builder#setActivityIntent",
            "android.car.app.ControlledRemoteCarTaskViewCallback#onTaskAppeared",
            "android.car.app.ControlledRemoteCarTaskViewCallback#onTaskViewCreated",
            "android.car.app.ControlledRemoteCarTaskViewCallback#onTaskViewInitialized"})
    public void createControlledRemoteCarTaskView_startsTheTask() {
        // Act
        CarTaskViewTestHolder taskViewHolder =
                createControlledTaskView(/* parentId= */ R.id.top_container,
                        EmbeddedTestActivity1.createLaunchIntent(mTargetContext)
                                .addFlags(FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));

        // Assert
        assertThat(taskViewHolder.mCurrentTask.baseActivity.getClassName())
                .isEqualTo(EmbeddedTestActivity1.class.getName());
        // To check if ControlledRemoteCarTaskView can preserve FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS.
        assertThat(taskViewHolder.mCurrentTask.baseIntent.getFlags()
                & FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .isEqualTo(FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    @Test
    @ApiTest(apis = {
            "android.car.app.ControlledRemoteCarTaskView#isInitialized",
            "android.car.app.ControlledRemoteCarTaskView#getTaskInfo"
    })
    public void createMultipleControlledRemoteCarTaskView_startsTheTask() {
        // Act
        CarTaskViewTestHolder taskViewCallback =
                createControlledTaskView(/* parentId= */ R.id.top_container,
                        EmbeddedTestActivity1.createLaunchIntent(mTargetContext));
        CarTaskViewTestHolder taskViewCallback2 =
                createControlledTaskView(/* parentId= */ R.id.bottom_container,
                        EmbeddedTestActivity2.createLaunchIntent(mTargetContext));


        // Assert
        assertThat(taskViewCallback.mCurrentTask.baseActivity.getClassName())
                .isEqualTo(EmbeddedTestActivity1.class.getName());
        assertThat(taskViewCallback.mCurrentTask.isVisible()).isTrue();
        assertThat(EmbeddedTestActivity1.sInstance.getUserId())
                .isEqualTo(mTargetContext.getUserId());
        assertThat(taskViewCallback.mTaskView.isInitialized()).isTrue();
        assertThat(taskViewCallback.mTaskView.getTaskInfo()).isNotNull();

        assertThat(taskViewCallback2.mCurrentTask.baseActivity.getClassName())
                .isEqualTo(EmbeddedTestActivity2.class.getName());
        assertThat(taskViewCallback2.mCurrentTask.isVisible()).isTrue();
        assertThat(EmbeddedTestActivity2.sInstance.getUserId())
                .isEqualTo(mTargetContext.getUserId());
        assertThat(taskViewCallback2.mTaskView.isInitialized()).isTrue();
        assertThat(taskViewCallback2.mTaskView.getTaskInfo()).isNotNull();
    }

    @Test
    @ApiTest(apis = {"android.car.app.ControlledRemoteCarTaskViewCallback#onTaskViewReleased"})
    public void multipleControlledCarTaskView_released_whenHostDestroyed() throws Exception {
        // Arrange
        CarTaskViewTestHolder taskViewCallback =
                createControlledTaskView(/* parentId= */ R.id.top_container,
                        EmbeddedTestActivity1.createLaunchIntent(mTargetContext));
        CarTaskViewTestHolder taskViewCallback2 =
                createControlledTaskView(/* parentId= */ R.id.bottom_container,
                        EmbeddedTestActivity2.createLaunchIntent(mTargetContext));

        // Act
        mHostActivity.finishAndRemoveTask();
        mHostActivity.waitForDestroyed();

        // Assert
        PollingCheck.waitFor(() -> taskViewCallback.mTaskViewReleased, "TaskView not released");
        assertThat(taskViewCallback.mTaskViewReleased).isTrue();
        assertThat(EmbeddedTestActivity1.sInstance.waitForDestroyed()).isTrue();
        assertThat(taskViewCallback.mTaskView.getTaskInfo()).isNull();

        PollingCheck.waitFor(() -> taskViewCallback2.mTaskViewReleased, "TaskView not released");
        assertThat(taskViewCallback2.mTaskViewReleased).isTrue();
        assertThat(EmbeddedTestActivity2.sInstance.waitForDestroyed()).isTrue();
        assertThat(taskViewCallback2.mTaskView.getTaskInfo()).isNull();
    }

    @Test
    @ApiTest(apis = {"android.car.app.ControlledRemoteCarTaskView#release"})
    public void releaseControlledCarTaskView_releasesTaskView() throws Exception {
        // Arrange
        CarTaskViewTestHolder taskViewCallback =
                createControlledTaskView(/* parentId= */ R.id.top_container,
                        EmbeddedTestActivity1.createLaunchIntent(mTargetContext));
        CarTaskViewTestHolder taskViewCallback2 =
                createControlledTaskView(/* parentId= */ R.id.bottom_container,
                        EmbeddedTestActivity2.createLaunchIntent(mTargetContext));

        // Act
        taskViewCallback2.mTaskView.release();

        // Assert
        PollingCheck.waitFor(() -> taskViewCallback2.mTaskViewReleased, "TaskView not released");
        assertThat(taskViewCallback2.mTaskViewReleased).isTrue();
        assertThat(EmbeddedTestActivity2.sInstance.waitForDestroyed()).isTrue();
        assertThat(taskViewCallback2.mTaskView.getTaskInfo()).isNull();

        assertThat(taskViewCallback.mTaskViewReleased).isFalse();
        assertThat(EmbeddedTestActivity1.sInstance.waitForDestroyed()).isFalse();
        assertThat(taskViewCallback.mTaskView.getTaskInfo()).isNotNull();
    }

    @Test
    @ApiTest(apis = {
            "android.car.app.ControlledRemoteCarTaskViewCallback#onTaskVanished(RunningTaskInfo)"})
    @FlakyTest(bugId = 300182719)
    public void controlledRemoteCarTaskView_autoRestartDisabled_doesNotRestartTask_whenKilled()
            throws Exception {
        // Arrange
        CarTaskViewTestHolder taskViewHolder =
                createControlledTaskView(/* parentId= */ R.id.top_container,
                        new ControlledRemoteCarTaskViewConfig.Builder()
                                .setActivityIntent(
                                        EmbeddedTestActivity1.createLaunchIntent(mTargetContext))
                                .setShouldAutoRestartOnTaskRemoval(false)
                                .build());
        PollingCheck.waitFor(() -> EmbeddedTestActivity1.sInstance != null,
                "EmbeddedActivity not created");

        // Act
        EmbeddedTestActivity1.sInstance.finishAndRemoveTask();

        // Assert
        PollingCheck.waitFor(() -> taskViewHolder.mCurrentTask == null,
                "onTaskVanished not received");
        assertThat(taskViewHolder.mCurrentTask).isNull();
    }

    @Test
    @ApiTest(apis = {
            "android.car.app.ControlledRemoteCarTaskViewCallback#onTaskAppeared(RunningTaskInfo)"})
    public void controlledRemoteCarTaskView_autoRestartEnabled_restartsTheTask_whenKilled()
            throws Exception {
        // Arrange
        CarTaskViewTestHolder taskViewHolder =
                createControlledTaskView(/* parentId= */ R.id.top_container,
                        new ControlledRemoteCarTaskViewConfig.Builder()
                                .setActivityIntent(
                                        EmbeddedTestActivity1.createLaunchIntent(mTargetContext))
                                .setShouldAutoRestartOnTaskRemoval(true)
                                .build());
        int previousTaskId = taskViewHolder.mCurrentTask.taskId;
        PollingCheck.waitFor(() -> EmbeddedTestActivity1.sInstance != null,
                "EmbeddedActivity not created");

        // Act
        EmbeddedTestActivity1.sInstance.finishAndRemoveTask();
        PollingCheck.waitFor(() -> taskViewHolder.mNumTimesOnTaskVanished == 1,
                "onTaskVanished not received");

        // Assert
        PollingCheck.waitFor(() -> taskViewHolder.mCurrentTask != null,
                "onTaskAppeared not received");
        assertThat(previousTaskId).isNotEqualTo(taskViewHolder.mCurrentTask.taskId);
        assertThat(taskViewHolder.mCurrentTask.baseActivity.getClassName())
                .isEqualTo(EmbeddedTestActivity1.class.getName());

    }

    @Test
    @ApiTest(apis = {"android.car.app.CarTaskViewController#showEmbeddedTasks",
            "android.car.app.ControlledRemoteCarTaskView#showEmbeddedTask"})
    public void multipleControlledRemoteCarTaskView_bringsEmbeddedTaskToTop_whenActivityResumed() {
        // Arrange
        CarTaskViewTestHolder taskViewCallback =
                createControlledTaskView(/* parentId= */ R.id.top_container,
                        EmbeddedTestActivity1.createLaunchIntent(mTargetContext));
        CarTaskViewTestHolder taskViewCallback2 =
                createControlledTaskView(/* parentId= */ R.id.bottom_container,
                        EmbeddedTestActivity2.createLaunchIntent(mTargetContext));
        PollingCheck.waitFor(() -> EmbeddedTestActivity1.sInstance != null,
                "EmbeddedTestActivity1 not started");
        PollingCheck.waitFor(() -> EmbeddedTestActivity2.sInstance != null,
                "EmbeddedTestActivity2 not started");

        // Act
        // Start a temporary activity so that host no longer remains at the top of wm stack.
        CarActivityManagerTest.TestActivity temporaryActivity =
                (CarActivityManagerTest.TestActivity) mInstrumentation.startActivitySync(
                        Intent.makeMainActivity(new ComponentName(mTargetContext,
                                CarActivityManagerTest.TestActivity.class))
                                .addFlags(FLAG_ACTIVITY_NEW_TASK), /* option */ null);
        PollingCheck.waitFor(() -> mHostActivity.mIsInStoppedState);
        // Finish the temporary activity to bring the host back to the top of the wm stack.
        temporaryActivity.finishAndRemoveTask();

        // Assert
        PollingCheck.waitFor(() -> !mHostActivity.mIsInStoppedState);
        PollingCheck.waitFor(() -> taskViewCallback.mCurrentTask.isVisible(),
                "TaskView task is invsibible");
        assertThat(taskViewCallback.mCurrentTask.baseActivity.getClassName())
                .isEqualTo(EmbeddedTestActivity1.class.getName());
        assertThat(taskViewCallback.mCurrentTask.isVisible()).isTrue();

        PollingCheck.waitFor(() -> taskViewCallback2.mCurrentTask.isVisible(),
                "TaskView2 task is invisible");
        assertThat(taskViewCallback2.mCurrentTask.baseActivity.getClassName())
                .isEqualTo(EmbeddedTestActivity2.class.getName());
        assertThat(taskViewCallback2.mCurrentTask.isVisible()).isTrue();
    }

    private Point getTaskViewCenterOnScreen(ControlledRemoteCarTaskView taskView) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        taskView.post(() -> {
            taskView.getLocationOnScreen(mTmpLocation);
            mTmpHeight = taskView.getMeasuredHeight();
            mTmpWidth = taskView.getMeasuredWidth();
            latch.countDown();
        });
        latch.await(QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE_MS, TimeUnit.MILLISECONDS);
        return new Point(mTmpLocation[0] + mTmpWidth / 2, mTmpLocation[1] + mTmpHeight / 4);
    }

    @Test
    @NonApiTest(exemptionReasons = {}, justification = "No CDD Requirement")
    public void remoteCarTaskView_receivesTouchInput() throws Exception {
        // Arrange
        CarTaskViewTestHolder carTaskViewHolder =
                createControlledTaskView(/* parentId= */ R.id.top_container,
                        EmbeddedTestActivity1.createLaunchIntent(mTargetContext));
        PollingCheck.waitFor(() -> EmbeddedTestActivity1.sInstance != null
                        && EmbeddedTestActivity1.sInstance.mIsResumed,
                "EmbeddedTestActivity1 is not running.");
        mUiDevice.waitForIdle(QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE_MS);

        // EmbeddedTestActivity is on the upper part of the screen.
        Point p = getTaskViewCenterOnScreen(carTaskViewHolder.mTaskView);
        SystemUtil.runShellCommandOrThrow(
                String.format("input -d %d tap %d %d", mMyDisplayId, p.x, p.y));
        PollingCheck.waitFor(() -> EmbeddedTestActivity1.sInstance.mLastReceivedTouchEvent != null,
                "Embedded activity didn't receive the touch event.");

        // Assert
        assertThat(EmbeddedTestActivity1.sInstance.mLastReceivedTouchEvent.getRawX())
                .isEqualTo(p.x);
        assertThat(EmbeddedTestActivity1.sInstance.mLastReceivedTouchEvent.getRawY())
                .isEqualTo(p.y);
    }

    private CarTaskViewTestHolder createControlledTaskView(int parentId,
            ControlledRemoteCarTaskViewConfig config) {
        CarTaskViewTestHolder taskViewCallback = new CarTaskViewTestHolder();
        mCallback.mCarTaskViewController.createControlledRemoteCarTaskView(
                config,
                mTargetContext.getMainExecutor(),
                taskViewCallback
        );

        PollingCheck.waitFor(() -> taskViewCallback.mTaskViewCreated);
        mTargetContext.getMainExecutor().execute(() -> {
            FrameLayout parent = mHostActivity.findViewById(parentId);
            parent.addView(taskViewCallback.mTaskView);
        });
        PollingCheck.waitFor(() -> taskViewCallback.mTaskViewInitialized,
                "TaskView not initialized");
        PollingCheck.waitFor(() -> taskViewCallback.mCurrentTask != null,
                "onTaskAppeared not called");
        return taskViewCallback;
    }

    private CarTaskViewTestHolder createControlledTaskView(int parentId, Intent intent) {
        return createControlledTaskView(parentId, new ControlledRemoteCarTaskViewConfig.Builder()
                .setActivityIntent(intent)
                .build());
    }

    public static final class EmbeddedTestActivity1 extends Activity {
        private static EmbeddedTestActivity1 sInstance;
        private final CountDownLatch mDestroyed = new CountDownLatch(1);
        private MotionEvent mLastReceivedTouchEvent;
        private boolean mIsResumed;

        public static Intent createLaunchIntent(Context context) {
            Intent intent = new Intent(context, EmbeddedTestActivity1.class);
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_LAUNCH_ADJACENT);
            return intent;
        }

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            sInstance = this;
            getWindow().getDecorView().setBackgroundColor(Color.BLUE);
        }

        @Override
        protected void onResume() {
            super.onResume();
            mIsResumed = true;
        }

        @Override
        protected void onPause() {
            super.onPause();
            mIsResumed = false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            mLastReceivedTouchEvent = MotionEvent.obtain(event);
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDestroy() {
            Log.d(TAG, "EmbeddedTestActivity1#onDestroy()");
            super.onDestroy();
            mDestroyed.countDown();
        }

        private boolean waitForDestroyed() throws InterruptedException {
            return mDestroyed.await(
                    QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE_MS, TimeUnit.MILLISECONDS);
        }
    }

    public static final class EmbeddedTestActivity2 extends Activity {
        private static EmbeddedTestActivity2 sInstance;
        private final CountDownLatch mDestroyed = new CountDownLatch(1);

        public static Intent createLaunchIntent(Context context) {
            Intent intent = new Intent(context, EmbeddedTestActivity2.class);
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_LAUNCH_ADJACENT);
            return intent;
        }

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            sInstance = this;
            getWindow().getDecorView().setBackgroundColor(Color.GREEN);
        }

        @Override
        protected void onDestroy() {
            Log.d(TAG, "EmbeddedTestActivity2#onDestroy()");
            super.onDestroy();
            mDestroyed.countDown();
        }

        private boolean waitForDestroyed() throws InterruptedException {
            return mDestroyed.await(
                    QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE_MS, TimeUnit.MILLISECONDS);
        }
    }

    public static final class TestCarTaskViewControllerCallback
            implements CarTaskViewControllerCallback {
        private boolean mConnected = false;
        private CarTaskViewController mCarTaskViewController;

        @Override
        public void onConnected(@NonNull CarTaskViewController carTaskViewController) {
            mConnected = true;
            mCarTaskViewController = carTaskViewController;
        }

        @Override
        public void onDisconnected(@NonNull CarTaskViewController carTaskViewController) {
            mConnected = false;
            mCarTaskViewController = null;
        }

        public boolean isConnected() {
            return mConnected;
        }
    }

    public static final class CarTaskViewTestHolder implements
            ControlledRemoteCarTaskViewCallback {
        private boolean mTaskViewCreated = false;
        private boolean mTaskViewInitialized = false;
        private boolean mTaskViewReleased = false;
        private ActivityManager.RunningTaskInfo mCurrentTask;
        private ControlledRemoteCarTaskView mTaskView;
        private int mNumTimesOnTaskVanished = 0;

        @Override
        public void onTaskViewCreated(@NonNull ControlledRemoteCarTaskView taskView) {
            mTaskViewCreated = true;
            mTaskView = taskView;
        }

        @Override
        public void onTaskViewInitialized() {
            mTaskViewInitialized = true;
        }

        @Override
        public void onTaskViewReleased() {
            mTaskViewReleased = true;
        }

        @Override
        public void onTaskAppeared(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
            mCurrentTask = taskInfo;
        }

        @Override
        public void onTaskInfoChanged(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
            if (mCurrentTask == null && mNumTimesOnTaskVanished > 0) {
                // The task is already vanished, skip handling it at all
                return;
            }
            mCurrentTask = taskInfo;
        }

        @Override
        public void onTaskVanished(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
            mCurrentTask = null;
            mNumTimesOnTaskVanished++;
        }
    }

    public static final class TestActivity extends Activity {
        private final CountDownLatch mDestroyed = new CountDownLatch(1);
        private boolean mIsInStoppedState = false;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.car_task_view_test_activity);
        }

        @Override
        protected void onStart() {
            super.onStart();
            mIsInStoppedState = false;
        }

        @Override
        protected void onStop() {
            super.onStop();
            mIsInStoppedState = true;
        }

        @Override
        protected void onDestroy() {
            Log.d(TAG, "TestActivity#onDestroy()");
            super.onDestroy();
            mDestroyed.countDown();
        }

        private boolean waitForDestroyed() throws InterruptedException {
            return mDestroyed.await(
                    QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE_MS, TimeUnit.MILLISECONDS);
        }
    }
}

