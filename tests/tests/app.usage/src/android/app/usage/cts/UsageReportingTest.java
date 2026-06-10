/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package android.app.usage.cts;

import static android.server.wm.SplitScreenActivityUtils.supportsSplitScreenMultiWindow;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import static java.util.Objects.requireNonNull;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.server.wm.LaunchActivityBuilder;
import android.server.wm.LockScreenSession;
import android.server.wm.NestedShellPermission;
import android.server.wm.SplitScreenActivityUtils;
import android.server.wm.TestTaskOrganizer;
import android.server.wm.UiDeviceUtils;
import android.server.wm.WindowManagerStateHelper;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.RequireNotVisibleBackgroundUsers;
import com.android.compatibility.common.util.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test the UsageStats API around usage reporting against tokens
 * Run test: atest CtsUsageStatsTestCases:UsageReportingTest
 */
@AppModeFull
public class UsageReportingTest {

    private static final String TARGET_PACKAGE = Activities.class.getPackageName();
    private static final String TOKEN_0 = "SuperSecretToken";
    private static final String TOKEN_1 = "AnotherSecretToken";
    private static final String FULL_TOKEN_0 = TARGET_PACKAGE + "/" + TOKEN_0;
    private static final String FULL_TOKEN_1 = TARGET_PACKAGE + "/" + TOKEN_1;

    private static final ComponentName ACTIVITY_ONE_COMPONENT =
            new ComponentName(TARGET_PACKAGE, Activities.ActivityOne.class.getName());
    private static final ComponentName ACTIVITY_TWO_COMPONENT =
            new ComponentName(TARGET_PACKAGE, Activities.ActivityTwo.class.getName());

    private static final String DEVICE_SLEEP_COMMAND = "input keyevent KEYCODE_SLEEP";

    private static final int ASSERT_TIMEOUT_SECONDS = 5; // 5 seconds

    @NonNull
    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();
    @NonNull
    private Instrumentation mInstrumentation;
    @NonNull
    private UiDevice mUiDevice;
    @NonNull
    private Context mContext;
    @NonNull
    private UsageStatsManager mUsageStatsManager;
    @NonNull
    private TestTaskOrganizer mTaskOrganizer;
    @NonNull
    private SplitScreenActivityUtils mSplitScreenActivityUtils;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mUiDevice = UiDevice.getInstance(mInstrumentation);
        mContext = mInstrumentation.getContext();
        mUsageStatsManager = requireNonNull(mContext.getSystemService(UsageStatsManager.class));
        UiDeviceUtils.wakeUpAndUnlock(mContext);
        NestedShellPermission.run(() -> {
            // TaskOrganizer ctor requires MANAGE_ACTIVITY_TASKS permission
            mTaskOrganizer = new TestTaskOrganizer();
        });
        mSplitScreenActivityUtils = new SplitScreenActivityUtils(mWmState, mTaskOrganizer);
    }

    @After
    public void tearDown() {
        if (mTaskOrganizer != null) {
            mTaskOrganizer.unregisterOrganizerIfNeeded();
        }
        Activities.sStartedActivities.clear();
    }

    @Test
    public void testUsageStartAndStopReporting() throws Exception {
        launchActivity(ACTIVITY_ONE_COMPONENT);

        final Activity activity;
        synchronized (Activities.sStartedActivities) {
            activity = Activities.sStartedActivities.valueAt(0);
        }

        mUsageStatsManager.reportUsageStart(activity, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);

        mUsageStatsManager.reportUsageStop(activity, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, false);


        mUsageStatsManager.reportUsageStart(activity, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);

        mUsageStatsManager.reportUsageStop(activity, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, false);
    }

    @Test
    public void testUsagePastReporting() throws Exception {
        launchActivity(ACTIVITY_ONE_COMPONENT);

        final Activity activity;
        synchronized (Activities.sStartedActivities) {
            activity = Activities.sStartedActivities.valueAt(0);
        }

        mUsageStatsManager.reportUsageStart(activity, TOKEN_0, 100);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);

        mUsageStatsManager.reportUsageStop(activity, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, false);
    }

    @Test
    @RequireNotVisibleBackgroundUsers(reason = "KEYCODE_SLEEP doesn't support visible background"
            + " user")
    public void testUsageReportingMissingStop() throws Exception {
        // TODO(b/330610015): This test should be re-enabled once PowerManager#isInteractive
        //  is fixed on form factors with visible background user.
        assumeFalse(isAutomotiveWithVisibleBackgroundUser());

        launchActivity(ACTIVITY_ONE_COMPONENT);

        final Activity activity;
        synchronized (Activities.sStartedActivities) {
            activity = Activities.sStartedActivities.valueAt(0);
        }

        mUsageStatsManager.reportUsageStart(activity, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);

        // Send the device to sleep to get onStop called for the token reporting activities.
        mUiDevice.executeShellCommand(DEVICE_SLEEP_COMMAND);
        Thread.sleep(1000);

        assertAppOrTokenUsed(FULL_TOKEN_0, false);
    }

    @Test
    public void testExceptionOnRepeatReport() throws Exception {
        launchActivity(ACTIVITY_ONE_COMPONENT);

        final Activity activity;
        synchronized (Activities.sStartedActivities) {
            activity = Activities.sStartedActivities.valueAt(0);
        }

        mUsageStatsManager.reportUsageStart(activity, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);

        try {
            mUsageStatsManager.reportUsageStart(activity, TOKEN_0);
            fail("Should have thrown an IllegalArgumentException for double reporting start");
        } catch (IllegalArgumentException iae) {
            //Expected exception
        }
        assertAppOrTokenUsed(FULL_TOKEN_0, true);

        mUsageStatsManager.reportUsageStop(activity, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, false);


        try {
            mUsageStatsManager.reportUsageStop(activity, TOKEN_0);
            fail("Should have thrown an IllegalArgumentException for double reporting stop");
        } catch (IllegalArgumentException iae) {
            //Expected exception
        }

        // One more cycle of reporting just to make sure there was no underflow
        mUsageStatsManager.reportUsageStart(activity, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);

        mUsageStatsManager.reportUsageStop(activity, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, false);
    }

    @Test
    public void testMultipleTokenUsageReporting() throws Exception {
        launchActivity(ACTIVITY_ONE_COMPONENT);

        final Activity activity;
        synchronized (Activities.sStartedActivities) {
            activity = Activities.sStartedActivities.valueAt(0);
        }

        mUsageStatsManager.reportUsageStart(activity, TOKEN_0);
        mUsageStatsManager.reportUsageStart(activity, TOKEN_1);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);
        assertAppOrTokenUsed(FULL_TOKEN_1, true);

        mUsageStatsManager.reportUsageStop(activity, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, false);
        assertAppOrTokenUsed(FULL_TOKEN_1, true);

        mUsageStatsManager.reportUsageStop(activity, TOKEN_1);
        assertAppOrTokenUsed(FULL_TOKEN_0, false);
        assertAppOrTokenUsed(FULL_TOKEN_1, false);
    }

    @Test
    @RequireNotVisibleBackgroundUsers(reason = "KEYCODE_SLEEP doesn't support visible background"
            + " user")
    public void testMultipleTokenMissingStop() throws Exception {
        // TODO(b/330610015): This test should be re-enabled once PowerManager#isInteractive
        //  is fixed on form factors with visible background user.
        assumeFalse(isAutomotiveWithVisibleBackgroundUser());

        launchActivity(ACTIVITY_ONE_COMPONENT);

        final Activity activity;
        synchronized (Activities.sStartedActivities) {
            activity = Activities.sStartedActivities.valueAt(0);
        }

        mUsageStatsManager.reportUsageStart(activity, TOKEN_0);
        mUsageStatsManager.reportUsageStart(activity, TOKEN_1);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);
        assertAppOrTokenUsed(FULL_TOKEN_1, true);

        // Send the device to sleep to get onStop called for the token reporting activities.
        mUiDevice.executeShellCommand(DEVICE_SLEEP_COMMAND);
        Thread.sleep(1000);

        assertAppOrTokenUsed(FULL_TOKEN_0, false);
        assertAppOrTokenUsed(FULL_TOKEN_1, false);
    }

    @Test
    public void testSplitscreenUsageReporting() throws Exception {
        assumeTrue("Skipping test: no multi-window support",
                supportsSplitScreenMultiWindow(mContext));

        mSplitScreenActivityUtils.launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(ACTIVITY_ONE_COMPONENT),
                getLaunchActivityBuilder().setTargetActivity(ACTIVITY_TWO_COMPONENT));
        Thread.sleep(500);

        final Activity activity0;
        final Activity activity1;
        synchronized (Activities.sStartedActivities) {
            activity0 = Activities.sStartedActivities.valueAt(0);
            activity1 = Activities.sStartedActivities.valueAt(1);
        }

        mUsageStatsManager.reportUsageStart(activity0, TOKEN_0);
        mUsageStatsManager.reportUsageStart(activity1, TOKEN_1);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);
        assertAppOrTokenUsed(FULL_TOKEN_1, true);

        mUsageStatsManager.reportUsageStop(activity0, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, false);
        assertAppOrTokenUsed(FULL_TOKEN_1, true);

        mUsageStatsManager.reportUsageStop(activity1, TOKEN_1);
        assertAppOrTokenUsed(FULL_TOKEN_0, false);
        assertAppOrTokenUsed(FULL_TOKEN_1, false);
    }

    @Test
    public void testSplitscreenSameToken() throws Exception {
        assumeTrue("Skipping test: no multi-window support",
                supportsSplitScreenMultiWindow(mContext));

        mSplitScreenActivityUtils.launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(ACTIVITY_ONE_COMPONENT),
                getLaunchActivityBuilder().setTargetActivity(ACTIVITY_TWO_COMPONENT));
        Thread.sleep(500);

        final Activity activity0;
        final Activity activity1;
        synchronized (Activities.sStartedActivities) {
            activity0 = Activities.sStartedActivities.valueAt(0);
            activity1 = Activities.sStartedActivities.valueAt(1);
        }

        mUsageStatsManager.reportUsageStart(activity0, TOKEN_0);
        mUsageStatsManager.reportUsageStart(activity1, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);

        mUsageStatsManager.reportUsageStop(activity0, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);

        mUsageStatsManager.reportUsageStop(activity1, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, false);
    }

    @Test
    public void testSplitscreenSameTokenOneMissedStop() throws Exception {
        assumeTrue("Skipping test: no multi-window support",
                supportsSplitScreenMultiWindow(mContext));

        mSplitScreenActivityUtils.launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(ACTIVITY_ONE_COMPONENT),
                getLaunchActivityBuilder().setTargetActivity(ACTIVITY_TWO_COMPONENT));
        Thread.sleep(500);

        final Activity activity0;
        final Activity activity1;
        synchronized (Activities.sStartedActivities) {
            activity0 = Activities.sStartedActivities.valueAt(0);
            activity1 = Activities.sStartedActivities.valueAt(1);
        }

        mUsageStatsManager.reportUsageStart(activity0, TOKEN_0);
        mUsageStatsManager.reportUsageStart(activity1, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);

        mUsageStatsManager.reportUsageStop(activity0, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);

        // Send the device to keyguard to get onStop called for the token reporting activities.
        try (LockScreenSession lockScreenSession =
                     new LockScreenSession(mInstrumentation, mWmState)) {
            lockScreenSession.gotoKeyguard();
            Thread.sleep(1000);
            assertAppOrTokenUsed(FULL_TOKEN_0, false);
        }
    }

    @Test
    public void testSplitscreenSameTokenTwoMissedStop() throws Exception {
        assumeTrue("Skipping test: no multi-window support",
                supportsSplitScreenMultiWindow(mContext));

        mSplitScreenActivityUtils.launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(ACTIVITY_ONE_COMPONENT),
                getLaunchActivityBuilder().setTargetActivity(ACTIVITY_TWO_COMPONENT));
        Thread.sleep(500);

        final Activity activity0;
        final Activity activity1;
        synchronized (Activities.sStartedActivities) {
            activity0 = Activities.sStartedActivities.valueAt(0);
            activity1 = Activities.sStartedActivities.valueAt(1);
        }

        mUsageStatsManager.reportUsageStart(activity0, TOKEN_0);
        mUsageStatsManager.reportUsageStart(activity1, TOKEN_0);
        assertAppOrTokenUsed(FULL_TOKEN_0, true);

        // Send the device to keyguard to get onStop called for the token reporting activities.
        try (LockScreenSession lockScreenSession =
                     new LockScreenSession(mInstrumentation, mWmState)) {
            lockScreenSession.gotoKeyguard();
            Thread.sleep(1000);
            assertAppOrTokenUsed(FULL_TOKEN_0, false);
        }
    }

    private void assertAppOrTokenUsed(String entity, boolean expected) throws Exception {
        final String failMessage;
        if (expected) {
            failMessage = entity + " not found in list of active activities and tokens";
        } else {
            failMessage = entity + " found in list of active activities and tokens";
        }

        TestUtils.waitUntil(failMessage, ASSERT_TIMEOUT_SECONDS, () -> {
            final String activeUsages =
                    mUiDevice.executeShellCommand("dumpsys usagestats apptimelimit actives");
            final String[] actives = activeUsages.split("\n");
            boolean found = false;

            for (String active : actives) {
                if (active.equals(entity)) {
                    found = true;
                    break;
                }
            }
            return found == expected;
        });
    }

    @NonNull
    private LaunchActivityBuilder getLaunchActivityBuilder() {
        return new LaunchActivityBuilder(mWmState);
    }

    private void launchActivity(@NonNull ComponentName componentName) {
        getLaunchActivityBuilder()
                .setLaunchingActivity(componentName)
                .setWaitForLaunched(true)
                .execute();
    }

    private boolean isAutomotiveWithVisibleBackgroundUser() {
        PackageManager packageManager = mContext.getPackageManager();
        UserManager userManager = mContext.getSystemService(UserManager.class);
        return packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                && userManager.isVisibleBackgroundUsersSupported();
    }

}
