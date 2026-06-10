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
package android.app.cts.fgstimeouttest;

import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.ACTIVITY;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.FGS0;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.FGS1;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.FGS2;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.HELPER_PACKAGE;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.TAG;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.flattenComponentName;
import static android.app.nano.AppProtoEnums.PROCESS_STATE_TOP;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.KeyEvent.KEYCODE_HOME;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import static com.google.common.truth.Truth.assertThat;

import android.app.Flags;
import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Service;
import android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper;
import android.app.cts.fgstimeouttesthelper.FgsTimeoutMessage;
import android.app.cts.fgstimeouttesthelper.FgsTimeoutMessageReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.UserManager;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.server.am.nano.ServiceRecordProto;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the {@link Service#onTimeout(int, int)} API.
 */
@Presubmit
public class FgsTimeoutTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    protected static final Context sContext = FgsTimeoutHelper.sContext;
    public static DeviceConfigStateHelper sDeviceConfig;

    public static final long WAIT_TIMEOUT = 10_000;

    /**
     * Timeout for FGS used throughout this test.
     * It's shorter than the default value to speed up the test.
     */
    public static final long SHORTENED_TIMEOUT = 5_000;

    @BeforeClass
    public static void setUpClass() throws Exception {
        sDeviceConfig = new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER);
        ShellUtils.runShellCommand("cmd device_config set_sync_disabled_for_tests until_reboot");

        updateDeviceConfig("media_processing_fgs_timeout_duration", SHORTENED_TIMEOUT);
        updateDeviceConfig("data_sync_fgs_timeout_duration", SHORTENED_TIMEOUT);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        sDeviceConfig.close();
    }

    @Before
    public void setUp() throws Exception {
        startActivityToResetLimits();
    }

    @After
    public void tearDown() throws Exception {
        forceStopHelperApps();
        // Drop any pending messages
        CallProvider.clearMessageQueue();
    }

    /**
     * Start MEDIA_PROCESSING FGS, and make sure the timeout callback is called.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testTimeout() {
        final long serviceStartTime = SystemClock.uptimeMillis();
        // Start the service
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        final int startId = waitForMethodCall(FGS0, "onStartCommand").getServiceStartId();
        assertFgsRunning(FGS0);

        // Wait for onTimeout()
        FgsTimeoutMessage m = waitForMethodCall(FGS0, "onTimeout");
        assertThat(m.getServiceStartId()).isEqualTo(startId);
        assertThat(m.getFgsType()).isEqualTo(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);

        // Timeout should happen after SHORTENED_TIMEOUT
        assertThat(m.getTimestamp()).isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
        assertFgsRunning(FGS0);

        // Stop the service
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Start a MEDIA_PROCESSING fgs, using Context.startService, not Context.startForegroundService.
     * Then make sure onTimeout() is called.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testTimeout_startService() {
        final long serviceStartTime = SystemClock.uptimeMillis();
        // Start the service (using startService)
        FgsTimeoutMessageReceiver.sendMessage(newMessage()
                .setComponentName(FGS0)
                .setDoCallStartService(true)
                .setDoCallStartForeground(true)
                .setFgsType(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
                .setStartCommandResult(Service.START_NOT_STICKY));
        waitForAckMessage();
        final int startId = waitForMethodCall(FGS0, "onStartCommand").getServiceStartId();
        assertFgsRunning(FGS0);

        // Wait for onTimeout()
        FgsTimeoutMessage m = waitForMethodCall(FGS0, "onTimeout");
        assertThat(m.getServiceStartId()).isEqualTo(startId);
        assertThat(m.getFgsType()).isEqualTo(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);

        // Timeout should happen after SHORTENED_TIMEOUT.
        assertThat(m.getTimestamp()).isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
        assertFgsRunning(FGS0);

        // Stop the service.
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Same as {@link #testTimeout}, but with another "normal" FGS running. The result should
     * be the same.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testTimeout_withParallelNormalFgs() {
        final long serviceStartTime = SystemClock.uptimeMillis();
        // Start a time-restricted fgs
        startForegroundService(FGS2, FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        final int startId = waitForMethodCall(FGS2, "onStartCommand").getServiceStartId();
        assertFgsRunning(FGS2);

        // Start a non-time-restricted fgs after 1 second
        SystemClock.sleep(1000);
        startForegroundService(FGS1, FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        waitForMethodCall(FGS1, "onStartCommand");
        assertFgsRunning(FGS1);

        // Wait for the timeout
        FgsTimeoutMessage m = waitForMethodCall(FGS2, "onTimeout");
        assertThat(m.getServiceStartId()).isEqualTo(startId);
        assertThat(m.getFgsType()).isEqualTo(FOREGROUND_SERVICE_TYPE_DATA_SYNC);

        // Timeout should happen after SHORTENED_TIMEOUT.
        assertThat(m.getTimestamp()).isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
        assertFgsRunning(FGS1);

        // Stop both services.
        sContext.stopService(new Intent().setComponent(FGS2));
        waitForMethodCall(FGS2, "onDestroy");
        assertServiceNotRunning(FGS2);

        sContext.stopService(new Intent().setComponent(FGS1));
        waitForMethodCall(FGS1, "onDestroy");
        assertServiceNotRunning(FGS1);

        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Test timeout is calculated correctly when running multiple FGS of the same type in parallel.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testTimeout_withParallelTimeRestrictedFgs() {
        final long firstServiceStartTime = SystemClock.uptimeMillis();
        // Start a time-restricted FGS
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        waitForMethodCall(FGS0, "onStartCommand");
        assertFgsRunning(FGS0);

        // Start another FGS of the same type after 1 second
        SystemClock.sleep(1000);
        final long secondServiceStartTime = SystemClock.uptimeMillis();
        startForegroundService(FGS1, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        final int startId = waitForMethodCall(FGS1, "onStartCommand").getServiceStartId();
        assertFgsRunning(FGS1);

        // Stop the first service after another second
        SystemClock.sleep(1000);
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);
        assertFgsRunning(FGS1);

        // Wait for the timeout
        FgsTimeoutMessage m = waitForMethodCall(FGS1, "onTimeout");
        assertThat(m.getServiceStartId()).isEqualTo(startId);

        // Timeout should have happened at the original stop time.
        assertThat(m.getTimestamp()).isAtLeast(firstServiceStartTime + SHORTENED_TIMEOUT);
        // Timeout should not have been calculated from the start time of the second service.
        // (plus some buffer for any potential slow lock acquisitions)
        assertThat(m.getTimestamp()).isAtMost(secondServiceStartTime + SHORTENED_TIMEOUT + 100);
        assertFgsRunning(FGS1);

        // Stop the second service.
        sContext.stopService(new Intent().setComponent(FGS1));
        waitForMethodCall(FGS1, "onDestroy");
        assertServiceNotRunning(FGS1);

        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Make sure, if a media_processing fgs doesn't stop, the app crashes.
     */
    @Test
    @RequiresFlagsEnabled({Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK,
                           Flags.FLAG_ENABLE_FGS_TIMEOUT_CRASH_BEHAVIOR})
    public void testCrash() throws Exception {
        final int crashExtraTimeout = 5000;
        updateDeviceConfig("fgs_crash_extra_wait_duration", crashExtraTimeout);

        try {
            // Start the service
            startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
            waitForMethodCall(FGS0, "onStartCommand");
            assertFgsRunning(FGS0);

            // Wait for onTimeout()
            waitForMethodCall(FGS0, "onTimeout");
            assertFgsRunning(FGS0);

            // Wait for the crash + some extra
            SystemClock.sleep(crashExtraTimeout + 1000);
            assertServiceNotRunning(FGS0);

            CallProvider.clearMessageQueue();
        } finally {
            resetDeviceConfig("fgs_crash_extra_wait_duration");
        }
    }

    /**
     * Start MEDIA_PROCESSING FGS and stop it, and make sure nothing throws,
     * and the service's onDestroy() gets called.
     * - Start FGS0 (in the helper app) with the MEDIA_PROCESSING FGS
     * - Stop it with Context.stopService().
     * - Wait until the timeout time and make sure the timeout callback won't be called.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testStop_stopService() {
        // Start the service
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        waitForMethodCall(FGS0, "onStartCommand");
        assertFgsRunning(FGS0);

        // Stop the service
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        // Wait for the timeout + extra duration
        SystemClock.sleep(SHORTENED_TIMEOUT + 5000);
        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)
        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Start MEDIA_PROCESSING FGS and stop it, and make sure nothing throws,
     * and the service's onDestroy() gets called.
     * - Start FGS0 (in the helper app) with the MEDIA_PROCESSING FGS
     * - Stop it with Service.stopSelf().
     * - Wait until the timeout time and make sure the timeout callback won't be called.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testStop_stopSelf() {
        // Start the service
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        waitForMethodCall(FGS0, "onStartCommand");
        assertFgsRunning(FGS0);

        // Stop the service, using Service.stopSelf()
        FgsTimeoutMessageReceiver
                .sendMessage(newMessage().setDoCallStopSelf(true).setComponentName(FGS0));
        waitForAckMessage();
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        // Wait for the timeout + extra duration
        SystemClock.sleep(SHORTENED_TIMEOUT + 5000);
        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)
        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Make sure a normal FGS *can* be started, if an app has a time-restricted FGS running.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testStartService_fromTimeRestrictedFgs() {
        // Start a time-restricted FGS
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        waitForMethodCall(FGS0, "onStartCommand");
        assertFgsRunning(FGS0);

        // Start another FGS, that's not time-restricted
        startForegroundService(FGS1, FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        waitForMethodCall(FGS1, "onStartCommand");
        assertFgsRunning(FGS1);

        // Stop the services.
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        sContext.stopService(new Intent().setComponent(FGS1));
        waitForMethodCall(FGS1, "onDestroy");
        assertServiceNotRunning(FGS1);

        // Wait for the timeout + extra duration
        SystemClock.sleep(SHORTENED_TIMEOUT + 5000);
        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)
        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Change the FGS type from a time-restricted fgs to a non-time-restricted type.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testTypeChange_fromTimeRestrictedToNormal() {
        // Start a time-restricted FGS
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        waitForMethodCall(FGS0, "onStartCommand");

        // Verify the FGS type
        ServiceRecordProto sr = assertFgsRunning(FGS0);
        assertThat(sr.foreground.foregroundServiceType)
                .isEqualTo(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);

        // Change the FGS type to SPECIAL_USE.
        SystemClock.sleep(1000);
        FgsTimeoutMessageReceiver.sendMessage(newMessage()
                .setComponentName(FGS0)
                .setDoCallStartForeground(true)
                .setFgsType(FOREGROUND_SERVICE_TYPE_SPECIAL_USE));
        waitForAckMessage();

        // The FGS type should be different now.
        sr = assertFgsRunning(FGS0);
        assertThat(sr.foreground.foregroundServiceType)
                .isEqualTo(FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        // Change the FGS type back to MEDIA_PROCESSING again.
        SystemClock.sleep(1000);
        FgsTimeoutMessageReceiver.sendMessage(newMessage()
                .setComponentName(FGS0)
                .setDoCallStartForeground(true)
                .setFgsType(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING));
        waitForAckMessage();

        // Verify the FGS type
        sr = assertFgsRunning(FGS0);
        assertThat(sr.foreground.foregroundServiceType)
                .isEqualTo(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);

        // Stop the service
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        // Wait for the timeout + extra duration
        SystemClock.sleep(SHORTENED_TIMEOUT + 5000);
        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)
        CallProvider.ensureNoMoreMessages();
    }

    /**
     * Start MEDIA_PROCESSING FGS, and make sure the timeout callback is called.
     * Attempt to start another MEDIA_PROCESSING FGS, exception should be thrown since
     * time limit has already been exhausted.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testStartService_throwsExceptionAfterTimeout() {
        final long serviceStartTime = SystemClock.uptimeMillis();
        // Start the service
        startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        final int startId = waitForMethodCall(FGS0, "onStartCommand").getServiceStartId();
        assertFgsRunning(FGS0);

        // Wait for onTimeout()
        FgsTimeoutMessage m = waitForMethodCall(FGS0, "onTimeout");
        assertThat(m.getServiceStartId()).isEqualTo(startId);
        assertThat(m.getFgsType()).isEqualTo(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);

        // Timeout should happen after SHORTENED_TIMEOUT
        assertThat(m.getTimestamp()).isAtLeast(serviceStartTime + SHORTENED_TIMEOUT);
        assertFgsRunning(FGS0);

        // Let the helper app call Context.startForegroundService, which should fail.
        FgsTimeoutMessageReceiver.sendMessage(newMessage()
                .setDoCallStartForeground(true)
                .setFgsType(FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
                .setComponentName(FGS0)
                .setExpectedExceptionClass(ForegroundServiceStartNotAllowedException.class));

        // It should have failed.
        FgsTimeoutMessage m2 = waitForException();
        assertThat(m2.getActualExceptionClass())
                .isEqualTo(ForegroundServiceStartNotAllowedException.class.getName());

        // Stop the service
        sContext.stopService(new Intent().setComponent(FGS0));
        waitForMethodCall(FGS0, "onDestroy");
        assertServiceNotRunning(FGS0);

        CallProvider.ensureNoMoreMessages();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testNoTimeout_whenFlagDisabled() {
        // Start the service
        startForegroundService(FGS2, FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        waitForMethodCall(FGS2, "onStartCommand");
        assertFgsRunning(FGS2);

        // Wait for the timeout + extra duration
        SystemClock.sleep(SHORTENED_TIMEOUT + 5000);
        // Make sure onTimeout() didn't happen. (If it did, onTimeout() would send a message,
        // which would break the below ensureNoMoreMessages().)
        CallProvider.ensureNoMoreMessages();

        // Stop the service
        sContext.stopService(new Intent().setComponent(FGS2));
        waitForMethodCall(FGS2, "onDestroy");
        assertServiceNotRunning(FGS2);

        CallProvider.ensureNoMoreMessages();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_FGS_TIMEOUT_CRASH_BEHAVIOR)
    public void testNoCrash_whenFlagDisabled() throws Exception {
        final int crashExtraTimeout = 5000;
        updateDeviceConfig("fgs_crash_extra_wait_duration", crashExtraTimeout);

        try {
            // Start the service
            startForegroundService(FGS0, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
            waitForMethodCall(FGS0, "onStartCommand");
            assertFgsRunning(FGS0);

            // Wait for onTimeout()
            waitForMethodCall(FGS0, "onTimeout");
            assertFgsRunning(FGS0);

            // Wait for the crash timeout + some extra - no crash should occur
            SystemClock.sleep(crashExtraTimeout + 5000);
            assertFgsRunning(FGS0);

            // Stop the service
            sContext.stopService(new Intent().setComponent(FGS0));
            waitForMethodCall(FGS0, "onDestroy");
            assertServiceNotRunning(FGS0);

            CallProvider.ensureNoMoreMessages();
        } finally {
            resetDeviceConfig("fgs_crash_extra_wait_duration");
        }
    }

    private static void updateDeviceConfig(String key, long value) throws Exception {
        Log.d(TAG, "updateDeviceConfig: setting " + key + " to " + value);
        sDeviceConfig.set(key, String.valueOf(value));

        waitUntil("`dumpsys activity settings` didn't update", () -> {
            final String dumpsys = ShellUtils.runShellCommand("dumpsys activity settings");

            // Look each line, rather than just doing a contains() check, so we can print
            // the current value.
            for (String line : dumpsys.split("\\n", -1)) {
                if (!line.contains(" " + key + "=")) {
                    continue;
                }
                Log.d(TAG, "Current config: " + line);
                if (line.endsWith("=" + value)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static void resetDeviceConfig(String key) throws Exception {
        Log.d(TAG, "resetDeviceConfig: resetting " + key);
        sDeviceConfig.reset(key);
    }

    private void startActivityToResetLimits() throws Exception {
        // Start an activity to bring app into TOP and reset previous time limit records.
        sContext.startActivity(new Intent()
                                .setComponent(ACTIVITY)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForMethodCall(ACTIVITY, "onCreate");

        // Wait until the procstate becomes TOP.
        waitUntil("Procstate is not TOP", () ->
                DumpProtoUtils.getProcessProcState(FgsTimeoutHelper.HELPER_PACKAGE).mProcState
                        == PROCESS_STATE_TOP);
        assertHelperPackageProcState(PROCESS_STATE_TOP);

        // Press the home button to send the app to the background.
        final UserManager userManager = sContext.getSystemService(UserManager.class);
        final int displayId = userManager.isVisibleBackgroundUsersSupported()
                ? userManager.getMainDisplayIdAssignedToUser()
                : DEFAULT_DISPLAY;
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                String.format("input -d %d keyevent %d", displayId, KEYCODE_HOME));

        SystemClock.sleep(1000); // Wait for oomadj to kick in.
    }

    private void assertHelperPackageProcState(int procState) {
        final DumpProtoUtils.ProcStateInfo expected = new DumpProtoUtils.ProcStateInfo();
        expected.mProcState = procState;

        final DumpProtoUtils.ProcStateInfo actual =
                DumpProtoUtils.getProcessProcState(FgsTimeoutHelper.HELPER_PACKAGE);

        assertThat(actual.toString()).isEqualTo(expected.toString());
    }

    private static void ensureHelperAppNotRunning() throws Exception {
        // Wait until the process is actually gone.
        // We need it because 1) kill is async and 2) the ack is sent before the kill anyway
        waitUntil("Process still running",
                () -> !DumpProtoUtils.processExists(FGS0.getPackageName()));
    }

    /**
     * Force-stop the helper app. It'll also remove the test app from temp-allowlist.
     */
    private static void forceStopHelperApps() throws Exception {
        SystemUtil.runShellCommand("am force-stop " + HELPER_PACKAGE);
        ensureHelperAppNotRunning();
    }

    private static FgsTimeoutMessage newMessage() {
        return new FgsTimeoutMessage();
    }

    private static FgsTimeoutMessage waitForNextMessage() {
        return CallProvider.waitForNextMessage(WAIT_TIMEOUT);
    }

    public static void waitForAckMessage() {
        CallProvider.waitForAckMessage(WAIT_TIMEOUT);
    }

    public static void startForegroundService(ComponentName cn, int fgsTypes) {
        Log.i(TAG, "startForegroundService: Starting " + cn
                + " types=0x" + Integer.toHexString(fgsTypes));
        FgsTimeoutMessage startMessage = newMessage()
                .setComponentName(cn)
                .setDoCallStartForeground(true)
                .setFgsType(fgsTypes)
                .setStartCommandResult(Service.START_NOT_STICKY);

        // Actual intent to start the FGS.
        Intent i = new Intent().setComponent(cn);
        FgsTimeoutHelper.setMessage(i, startMessage);

        sContext.startForegroundService(i);
    }

    public static FgsTimeoutMessage waitForMethodCall(ComponentName cn, String methodName) {
        Log.i(TAG, "waitForMethodCall: waiting for " + methodName + " from " + cn);
        FgsTimeoutMessage m = waitForNextMessage();

        String expected = flattenComponentName(cn) + "." + methodName;
        if (m.getMethodName() == null) {
            Assert.fail("Waited for " + expected + " but received: " + m);
        }
        assertThat(flattenComponentName(m.getComponentName()) + "." + m.getMethodName())
                    .isEqualTo(expected);
        return m;
    }

    public static FgsTimeoutMessage waitForException() {
        FgsTimeoutMessage m = waitForNextMessage();
        if (m.getActualExceptionClass() != null) {
            return m;
        }
        Assert.fail("Expected an exception message, but received: " + m);
        return m;
    }

    /**
     * Make sure a specified FGS is running.
     */
    public static ServiceRecordProto assertFgsRunning(ComponentName cn) {
        final ServiceRecordProto srp = DumpProtoUtils.findServiceRecord(cn);
        if (srp == null) {
            Assert.fail("Service " + cn + " is not running");
        }
        if (srp.foreground == null) {
            Assert.fail("Service " + cn + " is running, but is not an FGS");
        }

        return srp;
    }

    public static void assertServiceNotRunning(ComponentName cn) {
        final ServiceRecordProto srp = DumpProtoUtils.findServiceRecord(cn);
        assertThat(srp).isNull();
    }
}
