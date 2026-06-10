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

package android.app.cts;

import static android.app.stubs.LocalForegroundServiceMedia.ACTION_START_FGSM_RESULT;
import static android.os.SystemClock.sleep;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.server.am.ActiveServices.MEDIA_FGS_STATE_TRANSITION;

import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.compat.CompatChanges;
import android.app.compat.PackageOverride;
import android.app.cts.android.app.cts.tools.WaitForBroadcast;
import android.app.cts.android.app.cts.tools.WatchUidRunner;
import android.app.stubs.CommandReceiver;
import android.app.stubs.LocalForegroundServiceMedia;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.SystemUtil;
import com.android.media.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class ActivityManagerNotifyMediaFGSTypeTest {
    private static final String TAG = ActivityManagerNotifyMediaFGSTypeTest.class.getName();
    private static final String STUB_PACKAGE_NAME = "android.app.stubs";
    private static final String PACKAGE_NAME_APP1 = "com.android.app1";
    private static final int WAITFOR_MSEC = 10000;
    private static final int USER_ENGAGED_TIMEOUT_MSEC = 2000;
    private static final String USER_ENGAGED_TIMEOUT_KEY =
            "media_session_temp_user_engaged_duration_ms";
    private static final long PLAY_TIMEOUT_MS = 1000;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    private Context mContext;
    private Context mTargetContext;
    private Instrumentation mInstrumentation;
    private ActivityManager mActivityManager;

    private final DeviceConfigStateHelper mMediaDeviceConfig =
            new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_MEDIA);
     @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mTargetContext = mInstrumentation.getTargetContext();
        mActivityManager = mContext.getSystemService(ActivityManager.class);

        cleanUp();
        CtsAppTestUtils.turnScreenOn(mInstrumentation, mContext);

        // Configure short timeout for test execution and verify it's set.
        mMediaDeviceConfig.set(
                USER_ENGAGED_TIMEOUT_KEY, Integer.toString(USER_ENGAGED_TIMEOUT_MSEC));
        sleep(500); // Let the setting propagate.
        final String dumpLines = runShellCommand("dumpsys media_session");
        final String expectedLine =
                String.format("%s: [cur: %d", USER_ENGAGED_TIMEOUT_KEY, USER_ENGAGED_TIMEOUT_MSEC);
        assertTrue(
                "Failed to configure temp user engaged timeout", dumpLines.contains(expectedLine));

        // Ensure we can remote control media sessions from the test.
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MEDIA_CONTENT_CONTROL,
                        Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG,
                        Manifest.permission.OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD);

        setCompatOverride(MEDIA_FGS_STATE_TRANSITION, true);
    }

    @After
    public void tearDown() throws Exception {
        cleanUp();
        mMediaDeviceConfig.restoreOriginalValues();
    }

    private void cleanUp() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
                    CompatChanges.removePackageOverrides(
                            PACKAGE_NAME_APP1, Set.of(MEDIA_FGS_STATE_TRANSITION));
                });
        // Make sure we are in Home screen.
        mInstrumentation.getUiAutomation().performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_HOME);
    }

    private int setupMediaForegroundService() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);
        try {
            WaitForBroadcast waiter = new WaitForBroadcast(mTargetContext);

            // Put APP1 in TOP state.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP);

            // Start the media foreground service in APP1.
            waiter.prepare(ACTION_START_FGSM_RESULT);
            Bundle bundle = new Bundle();
            bundle.putInt(LocalForegroundServiceMedia.EXTRA_FOREGROUND_SERVICE_TYPE,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE_MEDIA, PACKAGE_NAME_APP1,
                    PACKAGE_NAME_APP1, 0, bundle);

            // Stop the activity.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
            Intent resultIntent = waiter.doWait(WAITFOR_MSEC);
            return resultIntent.getIntExtra(LocalForegroundServiceMedia.FGSM_NOTIFICATION_ID, -1);
        } finally {
            uid1Watcher.finish();
        }
    }

    private void cleanUpMediaForegroundService() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);
        try {
            // Stop the media foreground service in APP1.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE_MEDIA, PACKAGE_NAME_APP1,
                    PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY);
        } finally {
            uid1Watcher.finish();
        }
    }

    private void setupMediaService() throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);
        try {
            // Put APP1 in TOP state.
            CommandReceiver.sendCommand(
                    mContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1,
                    PACKAGE_NAME_APP1,
                    0,
                    null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP);

            // Start the media service in APP1.
            Bundle extras =
                    LocalForegroundServiceMedia.newCommand(
                            LocalForegroundServiceMedia.COMMAND_START_NO_FOREGROUND);
            CommandReceiver.sendCommand(
                    mContext,
                    CommandReceiver.COMMAND_START_SERVICE_MEDIA,
                    PACKAGE_NAME_APP1,
                    PACKAGE_NAME_APP1,
                    0,
                    extras);

            // Stop the activity.
            CommandReceiver.sendCommand(
                    mContext,
                    CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1,
                    PACKAGE_NAME_APP1,
                    0,
                    null);

            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        } finally {
            uid1Watcher.finish();
        }
    }

    private MediaController getMediaControllerForActiveSession() {
        MediaSessionManager mediaSessionManager =
                mTargetContext.getSystemService(MediaSessionManager.class);
        List<MediaController> mediaControllers = mediaSessionManager.getActiveSessions(null);
        for (MediaController controller : mediaControllers) {
            if (PACKAGE_NAME_APP1.equals(controller.getPackageName())) {
                return controller;
            }
        }
        return null;
    }

    private static void setCompatOverride(long changeId, boolean enable) {
        var override = Map.of(changeId, new PackageOverride.Builder().setEnabled(enable).build());
        CompatChanges.putPackageOverrides(PACKAGE_NAME_APP1, override);
    }

    // This test tests activity manager internal API to set media foreground service inactive.
    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testNotifyInactiveMediaForegroundServiceInternal() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);
        // start a media fgs
        final int notificationId = setupMediaForegroundService();
        assertTrue("Failed to start media foreground service with notification",
                notificationId > 0);
        runShellCommand(mInstrumentation,
                String.format("am set-media-foreground-service inactive --user %d %s %d",
                        mContext.getUserId(), PACKAGE_NAME_APP1, notificationId));

        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        cleanUpMediaForegroundService();
        uid1Watcher.finish();
    }

    // This test tests activity manager internal API to set media foreground service
    // inactive/active.
    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testNotifyMediaServiceInternal() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);
        // start a media fgs
        final int notificationId = setupMediaForegroundService();
        assertTrue("Failed to start media foreground service with notification",
                notificationId > 0);
        runShellCommand(mInstrumentation,
                String.format("am set-media-foreground-service inactive --user %d %s %d",
                        mContext.getUserId(), PACKAGE_NAME_APP1, notificationId));

        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        runShellCommand(mInstrumentation,
                String.format("am set-media-foreground-service active --user %d %s %d",
                        mContext.getUserId(), PACKAGE_NAME_APP1, notificationId));
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
        cleanUpMediaForegroundService();
        uid1Watcher.finish();
    }

    @Test(expected = IllegalStateException.class)
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testNotifyMediaServiceAfterStopForegroundInternal() throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);
        WaitForBroadcast waiter = new WaitForBroadcast(mTargetContext);
        // start a media fgs
        final int notificationId = setupMediaForegroundService();
        assertTrue(
                "Failed to start media foreground service with notification", notificationId > 0);
        waiter.prepare(ACTION_START_FGSM_RESULT);
        Bundle extras =
                LocalForegroundServiceMedia.newCommand(
                        LocalForegroundServiceMedia.COMMAND_STOP_FOREGROUND_REMOVE_NOTIFICATION);
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_SERVICE_MEDIA,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                extras);
        waiter.doWait(WAITFOR_MSEC);
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        runShellCommand(
                mInstrumentation,
                String.format(
                        "am set-media-foreground-service active --user %d %s %d",
                        mContext.getUserId(), PACKAGE_NAME_APP1, notificationId));
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
        cleanUpMediaForegroundService();
        uid1Watcher.finish();
    }

    // This test tests activity manager internal API to set media foreground service active
    // with compat change disabled.
    @Test(expected = IllegalStateException.class)
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testNotifyInactiveMediaForegroundServiceInternalWithDisableCompatChanges()
            throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);
        // disable compat change.
        setCompatOverride(MEDIA_FGS_STATE_TRANSITION, false);
        // start a media fgs
        final int notificationId = setupMediaForegroundService();
        assertTrue(
                "Failed to start media foreground service with notification", notificationId > 0);
        runShellCommand(
                mInstrumentation,
                String.format(
                        "am set-media-foreground-service inactive --user %d %s %d",
                        mContext.getUserId(), PACKAGE_NAME_APP1, notificationId));

        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        cleanUpMediaForegroundService();
        uid1Watcher.finish();
    }

    // This test tests activity manager internal API to set media foreground service
    // inactive/active with compat change disabled.
    @Test(expected = IllegalStateException.class)
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testNotifyMediaServiceInternalWithDisableCompatChanges() throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);
        // start a media fgs
        final int notificationId = setupMediaForegroundService();
        assertTrue(
                "Failed to start media foreground service with notification", notificationId > 0);
        runShellCommand(
                mInstrumentation,
                String.format(
                        "am set-media-foreground-service inactive --user %d %s %d",
                        mContext.getUserId(), PACKAGE_NAME_APP1, notificationId));

        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        // disable compat change.
        setCompatOverride(MEDIA_FGS_STATE_TRANSITION, false);
        runShellCommand(
                mInstrumentation,
                String.format(
                        "am set-media-foreground-service active --user %d %s %d",
                        mContext.getUserId(), PACKAGE_NAME_APP1, notificationId));
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
        cleanUpMediaForegroundService();
        uid1Watcher.finish();
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testAppInBgWithUserEngagedMediaSessionIsInBg() throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);

        setupMediaService();
        // Post media style notification.
        Bundle extras =
                LocalForegroundServiceMedia.newCommand(
                        LocalForegroundServiceMedia.COMMAND_POST_MEDIA_NOTIFICATION);
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_SERVICE_MEDIA,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                extras);

        // Move media session to user engaged state.
        extras =
                LocalForegroundServiceMedia.newCommand(
                        LocalForegroundServiceMedia.COMMAND_PLAY_MEDIA);
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_SERVICE_MEDIA,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                extras);
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void
            testAppInBgWithActivePlayingMediaSessionWithMediaControllerAndNoNotificationIsStillInBg()
                    throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);

        setupMediaService();
        MediaController controller = getMediaControllerForActiveSession();
        controller.getTransportControls().play();
        sleep(PLAY_TIMEOUT_MS);
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testAppInFgWithActivePlayingMediaSessionAndNotificationGoesToBgIsStillInBg()
            throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);

        setupMediaService();
        // Put APP1 in TOP state.
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_ACTIVITY,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                null);
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP);
        // Post media style notification.
        Bundle extras =
                LocalForegroundServiceMedia.newCommand(
                        LocalForegroundServiceMedia.COMMAND_POST_MEDIA_NOTIFICATION);
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_SERVICE_MEDIA,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                extras);

        // Move media session to user engaged state.
        extras =
                LocalForegroundServiceMedia.newCommand(
                        LocalForegroundServiceMedia.COMMAND_PLAY_MEDIA);
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_SERVICE_MEDIA,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                extras);

        // Make sure we are in Home screen.
        mInstrumentation
                .getUiAutomation()
                .performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);

        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void
            testAppInFgWithActivePlayingMediaSessionWithMediaControllerAndNotificationGoesToFgs()
                    throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);

        setupMediaForegroundService();
        // Put APP1 in TOP state.
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_ACTIVITY,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                null);
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP);

        MediaController controller = getMediaControllerForActiveSession();
        controller.getTransportControls().play();
        sleep(PLAY_TIMEOUT_MS);
        // Make sure we are in Home screen.
        mInstrumentation
                .getUiAutomation()
                .performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);

        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testAppInFgWithActiveStoppedMediaSessionAndNotificationGoesToBgIsStillInBg()
            throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);

        setupMediaService();
        // Put APP1 in TOP state.
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_ACTIVITY,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                null);
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP);
        // Post media style notification.
        Bundle extras =
                LocalForegroundServiceMedia.newCommand(
                        LocalForegroundServiceMedia.COMMAND_POST_MEDIA_NOTIFICATION);
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_SERVICE_MEDIA,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                extras);

        // Move media session to user engaged state.
        extras =
                LocalForegroundServiceMedia.newCommand(
                        LocalForegroundServiceMedia.COMMAND_PLAY_MEDIA);
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_SERVICE_MEDIA,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                extras);

        // Stop media session.
        extras =
                LocalForegroundServiceMedia.newCommand(
                        LocalForegroundServiceMedia.COMMAND_STOP_MEDIA);
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_SERVICE_MEDIA,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                extras);

        // Make sure we are in Home screen.
        mInstrumentation
                .getUiAutomation()
                .performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);

        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void
            testAppInFgWithActiveStoppedMediaSessionWithMediaControllerAndNotificationGoesToBgIsStillInBg()
                    throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);

        setupMediaService();
        // Put APP1 in TOP state.
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_ACTIVITY,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                null);
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP);
        // Post media style notification.
        Bundle extras =
                LocalForegroundServiceMedia.newCommand(
                        LocalForegroundServiceMedia.COMMAND_POST_MEDIA_NOTIFICATION);
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_SERVICE_MEDIA,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                extras);

        // Move media session to user engaged state.
        MediaController controller = getMediaControllerForActiveSession();
        controller.getTransportControls().play();
        sleep(PLAY_TIMEOUT_MS);

        // Stop media session.
        controller.getTransportControls().stop();

        // Make sure we are in Home screen.
        mInstrumentation
                .getUiAutomation()
                .performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);

        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void
            testAppInBgWithActivePlayingMediaSessionWithMediaControllerAndNotificationGoesToFgs()
                    throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);

        // Start the media service in foreground state.
        final int notificationId = setupMediaForegroundService();
        assertTrue(
                "Failed to start media foreground service with notification", notificationId > 0);

        // Set the service inactive for test case.
        runShellCommand(
                mInstrumentation,
                String.format(
                        "am set-media-foreground-service inactive --user %d %s %d",
                        mContext.getUserId(), PACKAGE_NAME_APP1, notificationId));
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        // Get the controller and press play.
        MediaController controller = getMediaControllerForActiveSession();
        controller.getTransportControls().play();
        sleep(PLAY_TIMEOUT_MS);
        // Check if service moves to fgs.
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testAppInBgWithUserDisengagedMediaSessionAndNotificationGoesToBg()
            throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);

        // Start the media service in foreground state.
        final int notificationId = setupMediaForegroundService();
        assertTrue(
                "Failed to start media foreground service with notification", notificationId > 0);
        // Get the controller and press play.
        MediaController controller = getMediaControllerForActiveSession();
        controller.getTransportControls().play();
        sleep(PLAY_TIMEOUT_MS);
        // Transition session to user disengaged.
        controller.getTransportControls().pause();
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testAppInBgWithStoppedMediaSessionAndNotificationGoesToBg() throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);

        // Start the media service in foreground state.
        final int notificationId = setupMediaForegroundService();
        assertTrue(
                "Failed to start media foreground service with notification", notificationId > 0);
        // Get the controller and press play.
        MediaController controller = getMediaControllerForActiveSession();
        controller.getTransportControls().play();
        sleep(PLAY_TIMEOUT_MS);
        // Transition session to user disengaged.
        controller.getTransportControls().stop();
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testAppInBgWithNonActiveMediaSessionAndNotificationGoesToBg() throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);
        WaitForBroadcast waiter = new WaitForBroadcast(mTargetContext);
        // Start the media service in foreground state.
        final int notificationId = setupMediaForegroundService();
        assertTrue(
                "Failed to start media foreground service with notification", notificationId > 0);
        // Get the controller and press play.
        MediaController controller = getMediaControllerForActiveSession();
        controller.getTransportControls().play();
        // Play media for some time and then deactivate the media session.
        sleep(PLAY_TIMEOUT_MS);
        waiter.prepare(ACTION_START_FGSM_RESULT);
        Bundle extras =
                LocalForegroundServiceMedia.newCommand(
                        LocalForegroundServiceMedia.COMMAND_DEACTIVATE_MEDIA_SESSION);
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_SERVICE_MEDIA,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                extras);
        waiter.doWait(WAITFOR_MSEC);
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testAppInBgWithActivePlayingMediaSessionAndNotificationReleaseItsSessionGoesToBg()
            throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);
        WaitForBroadcast waiter = new WaitForBroadcast(mTargetContext);
        // Start the media service in foreground state.
        final int notificationId = setupMediaForegroundService();
        assertTrue(
                "Failed to start media foreground service with notification", notificationId > 0);
        // Get the controller and press play.
        MediaController controller = getMediaControllerForActiveSession();
        controller.getTransportControls().play();
        // Play media for some time and then release the media session.
        sleep(PLAY_TIMEOUT_MS);
        waiter.prepare(ACTION_START_FGSM_RESULT);
        Bundle extras =
                LocalForegroundServiceMedia.newCommand(
                        LocalForegroundServiceMedia.COMMAND_RELEASE_MEDIA_SESSION);
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_SERVICE_MEDIA,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                extras);
        waiter.doWait(WAITFOR_MSEC);
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void
            testAppInBgWithNonActivePlayingMediaSessionWithMediaControllerAndNotificationIsStillInBg()
                    throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);
        WaitForBroadcast waiter = new WaitForBroadcast(mTargetContext);
        // Start the media service in foreground state.
        final int notificationId = setupMediaForegroundService();
        assertTrue(
                "Failed to start media foreground service with notification", notificationId > 0);
        // Get the controller for active session before deactivating.
        MediaController controller = getMediaControllerForActiveSession();
        // Wait for some time and deactivate the media session.
        sleep(PLAY_TIMEOUT_MS);
        waiter.prepare(ACTION_START_FGSM_RESULT);
        Bundle extras =
                LocalForegroundServiceMedia.newCommand(
                        LocalForegroundServiceMedia.COMMAND_DEACTIVATE_MEDIA_SESSION);
        CommandReceiver.sendCommand(
                mContext,
                CommandReceiver.COMMAND_START_SERVICE_MEDIA,
                PACKAGE_NAME_APP1,
                PACKAGE_NAME_APP1,
                0,
                extras);
        waiter.doWait(WAITFOR_MSEC);
        // Press play for deactivated media session.
        controller.getTransportControls().play();
        sleep(PLAY_TIMEOUT_MS);
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testAppInBgWithActiveMediaSessionMultiplePlayPauseStopCycle() throws Exception {
        ApplicationInfo app1Info =
                mContext.getPackageManager().getApplicationInfo(PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher =
                new WatchUidRunner(mInstrumentation, app1Info.uid, WAITFOR_MSEC);
        // Start the media service in foreground state.
        final int notificationId = setupMediaForegroundService();
        assertTrue(
                "Failed to start media foreground service with notification", notificationId > 0);
        // Get the controller for active session before deactivating.
        MediaController controller = getMediaControllerForActiveSession();
        controller.getTransportControls().play();
        sleep(PLAY_TIMEOUT_MS);
        // Configure temp user engaged timeout.
        mMediaDeviceConfig.set(
                USER_ENGAGED_TIMEOUT_KEY, Integer.toString(USER_ENGAGED_TIMEOUT_MSEC));
        // Verify if timeout is set.
        final String dumpLines = runShellCommand("dumpsys media_session");
        final String expectedLine =
                String.format("%s: [cur: %d", USER_ENGAGED_TIMEOUT_KEY, USER_ENGAGED_TIMEOUT_MSEC);
        assertTrue(
                "Failed to configure temp user engaged timeout", dumpLines.contains(expectedLine));

        controller.getTransportControls().stop();
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        controller.getTransportControls().play();
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);

        controller.getTransportControls().pause();
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        controller.getTransportControls().play();
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);

        controller.getTransportControls().stop();
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        controller.getTransportControls().play();
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);

        controller.getTransportControls().pause();
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        controller.getTransportControls().play();
        uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
    }
}
