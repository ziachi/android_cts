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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.app.ApplicationStartInfo;
import android.app.Flags;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;

import com.google.errorprone.annotations.FormatMethod;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public final class ActivityManagerAppStartInfoTest {
    private static final String TAG = ActivityManagerAppStartInfoTest.class.getSimpleName();

    // Begin section: keep in sync with {@link ApiTestActivity}
    private static final String REQUEST_KEY_ACTION = "action";
    private static final String REQUEST_KEY_TIMESTAMP_KEY_FIRST = "timestamp_key_first";
    private static final String REQUEST_KEY_TIMESTAMP_VALUE_FIRST = "timestamp_value_first";
    private static final String REQUEST_KEY_TIMESTAMP_KEY_LAST = "timestamp_key_last";
    private static final String REQUEST_KEY_TIMESTAMP_VALUE_LAST = "timestamp_value_last";

    private static final int REQUEST_VALUE_QUERY_START = 1;
    private static final int REQUEST_VALUE_ADD_TIMESTAMP = 2;
    private static final int REQUEST_VALUE_LISTENER_ADD_ONE = 3;
    private static final int REQUEST_VALUE_LISTENER_ADD_MULTIPLE = 4;
    private static final int REQUEST_VALUE_LISTENER_ADD_REMOVE = 5;
    private static final int REQUEST_VALUE_CRASH = 6;

    private static final String REPLY_ACTION_COMPLETE =
            "com.android.cts.startinfoapp.ACTION_COMPLETE";

    private static final String REPLY_EXTRA_STATUS_KEY = "status";

    private static final int REPLY_EXTRA_SUCCESS_VALUE = 1;
    //private static final int REPLY_EXTRA_FAILURE_VALUE = 2;

    private static final int REPLY_STATUS_NONE = -1;
    // End section: keep in sync with {@link ApiTestActivity}

    private static final String STUB_APK =
            "/data/local/tmp/cts/content/CtsAppStartInfoApp.apk";
    private static final String STUB_PACKAGE_NAME = "com.android.cts.startinfoapp";
    private static final String SIMPLE_ACTIVITY = ".ApiTestActivity";

    private static final int FIRST_TIMESTAMP_KEY =
            ApplicationStartInfo.START_TIMESTAMP_RESERVED_RANGE_DEVELOPER_START;
    private static final int LAST_TIMESTAMP_KEY =
            ApplicationStartInfo.START_TIMESTAMP_RESERVED_RANGE_DEVELOPER;

    private static final int MAX_WAITS_FOR_START = 20;
    private static final int WAIT_FOR_START_MS = 400;

    // Return states of the ResultReceiverFilter.
    public static final int RESULT_PASS = 1;
    public static final int RESULT_FAIL = 2;
    public static final int RESULT_TIMEOUT = 3;

    private Context mContext;
    private Instrumentation mInstrumentation;
    private ActivityManager mActivityManager;
    private PackageManager mPackageManager;

    private int mStubPackageUid;
    private int mTestRunningUserId;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mPackageManager = mContext.getPackageManager();
        mTestRunningUserId = UserHandle.getUserId(Process.myUid());

        executeShellCmd("pm install --user %d -r --force-queryable " + STUB_APK,
                mTestRunningUserId);

        mStubPackageUid = mPackageManager.getPackageUid(STUB_PACKAGE_NAME, 0);
    }

    @After
    public void tearDown() throws Exception {
        executeShellCmd("am force-stop --user %d " + STUB_PACKAGE_NAME, mTestRunningUserId);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_START_INFO)
    public void testLauncherStart() throws Exception {
        clearHistoricalStartInfo();

        Intent intent =
                mPackageManager.getLaunchIntentForPackage(STUB_PACKAGE_NAME);
        mContext.startActivity(intent);

        ApplicationStartInfo info = waitForAppStart();

        verify(info, STUB_PACKAGE_NAME, STUB_PACKAGE_NAME, intent,
                ApplicationStartInfo.START_REASON_LAUNCHER,
                ApplicationStartInfo.START_TYPE_COLD,
                ApplicationStartInfo.LAUNCH_MODE_STANDARD,
                ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN,
                ApplicationStartInfo.START_COMPONENT_ACTIVITY);

        verifyIds(info, 0, mStubPackageUid, mStubPackageUid, mStubPackageUid);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_START_INFO)
    public void testActivityStart() throws Exception {
        clearHistoricalStartInfo();

        executeShellCmd("am start --user %d -n " + STUB_PACKAGE_NAME + "/" + STUB_PACKAGE_NAME
                + SIMPLE_ACTIVITY, mTestRunningUserId);

        ApplicationStartInfo info = waitForAppStart();

        Intent intent = new Intent();
        intent.setComponent(ComponentName.createRelative(STUB_PACKAGE_NAME,
                SIMPLE_ACTIVITY));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        verify(info, STUB_PACKAGE_NAME, STUB_PACKAGE_NAME, intent,
                ApplicationStartInfo.START_REASON_START_ACTIVITY,
                ApplicationStartInfo.START_TYPE_COLD,
                ApplicationStartInfo.LAUNCH_MODE_STANDARD,
                ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN,
                ApplicationStartInfo.START_COMPONENT_ACTIVITY);

        verifyIds(info, 0, mStubPackageUid, mStubPackageUid, mStubPackageUid);
    }

    /** Test that the wasForceStopped state is accurate in force stopped case. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_START_INFO)
    public void testWasForceStopped() throws Exception {
        clearHistoricalStartInfo();

        // Start the test app and wait for it to complete
        executeShellCmd("am start --user %d -n " + STUB_PACKAGE_NAME + "/" + STUB_PACKAGE_NAME
                + SIMPLE_ACTIVITY, mTestRunningUserId);
        waitForAppStart();

        // Now force stop the app
        executeShellCmd("am force-stop --user %d " + STUB_PACKAGE_NAME, mTestRunningUserId);

        // Clear records again, we don't want to check the previous one.
        clearHistoricalStartInfo();

        // Start the app again
        executeShellCmd("am start --user %d -n " + STUB_PACKAGE_NAME + "/" + STUB_PACKAGE_NAME
                + SIMPLE_ACTIVITY, mTestRunningUserId);

        // Obtain the start record and confirm it shows having been force stopped
        ApplicationStartInfo info = waitForAppStart();
        assertTrue(info.wasForceStopped());
    }

    /** Test that the wasForceStopped state is accurate in not force stopped case. */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_START_INFO)
    public void testWasNotForceStopped() throws Exception {
        clearHistoricalStartInfo();

        // Start the test app and wait for it to complete
        executeShellCmd("am start --user %d -n " + STUB_PACKAGE_NAME + "/" + STUB_PACKAGE_NAME
                + SIMPLE_ACTIVITY, mTestRunningUserId);
        waitForAppStart();

        // Now force stop the app
        executeShellCmd("am force-stop --user %d " + STUB_PACKAGE_NAME, mTestRunningUserId);

        // Clear records again, we don't want to check the previous one here.
        clearHistoricalStartInfo();

        // Start the app with flag to immediately exit
        executeShellCmd("am start --user %d -n %s/%s%s --ei %s %d",
                mTestRunningUserId, // test running user ID
                STUB_PACKAGE_NAME, STUB_PACKAGE_NAME, SIMPLE_ACTIVITY, // package/activity to start
                REQUEST_KEY_ACTION, REQUEST_VALUE_CRASH); // action to perform
        sleep(1000);

        // Clear records again, we don't want to check the previous one.
        clearHistoricalStartInfo();

        // Start the app again
        executeShellCmd("am start --user %d -n " + STUB_PACKAGE_NAME + "/" + STUB_PACKAGE_NAME
                + SIMPLE_ACTIVITY, mTestRunningUserId);

        // Obtain the start record and confirm it shows having not been force stopped
        ApplicationStartInfo info = waitForAppStart();
        assertFalse(info.wasForceStopped());
    }

    /**
     * Start an app and make sure its record exists, then verify
     * the record is removed when the app is uninstalled.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_START_INFO)
    public void testAppRemoved() throws Exception {
        testActivityStart();

        executeShellCmd("pm uninstall --user %d " + STUB_PACKAGE_NAME, mTestRunningUserId);

        List<ApplicationStartInfo> list =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        STUB_PACKAGE_NAME, 1,
                        mActivityManager::getExternalHistoricalProcessStartReasons,
                        android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 0);
    }

    /**
     * Test querying the startup of the process we're currently in.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_START_INFO)
    public void testQueryThisProcess() throws Exception {
        clearHistoricalStartInfo();

        ResultReceiverFilter receiver = new ResultReceiverFilter(REPLY_ACTION_COMPLETE, 1);

        // Start the app and have it query its own start info record.
        executeShellCmd("am start --user %d -n %s/%s%s --ei %s %d",
                mTestRunningUserId, // test running user ID
                STUB_PACKAGE_NAME, STUB_PACKAGE_NAME, SIMPLE_ACTIVITY, // package/activity to start
                REQUEST_KEY_ACTION, REQUEST_VALUE_QUERY_START); // action to perform

        // Wait for complete callback
        assertEquals(RESULT_PASS, receiver.waitForActivity());
        receiver.close();

        // Confirm that the app confirmed that it successfully obtained record.
        assertEquals(1, receiver.mIntents.size());

        Bundle extras = receiver.mIntents.get(0).getExtras();
        assertNotNull(extras);

        int status = extras.getInt(REPLY_EXTRA_STATUS_KEY, -1);
        assertEquals(REPLY_EXTRA_SUCCESS_VALUE, status);
    }

    /**
     * Test adding timestamps and verify that the timestamps that were added are still there on a
     * subsequent query.
     *
     * Timestamp is created by test runner process and provided to test app to add to start record
     * as apps can only add timestamps to their own starts. The subsequent query is performed here
     * in the test app as querying records can be done from other processes and querying the process
     * itself is not being tested here.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_START_INFO)
    public void testAddingTimestamps() throws Exception {
        clearHistoricalStartInfo();

        final long timestampFirst = System.nanoTime();
        final long timestampLast = timestampFirst + 1000L;
        ResultReceiverFilter receiver = new ResultReceiverFilter(REPLY_ACTION_COMPLETE, 1);

        // Start the app and have it add the provided timestamp to its start record.
        executeShellCmd("am start --user %d -n %s/%s%s "
                        + "--ei %s %d --ei %s %d --el %s %d --ei %s %d --el %s %d",
                mTestRunningUserId, // test running user ID
                STUB_PACKAGE_NAME, STUB_PACKAGE_NAME, SIMPLE_ACTIVITY, // package/activity to start
                REQUEST_KEY_ACTION, REQUEST_VALUE_ADD_TIMESTAMP, // action to perform
                REQUEST_KEY_TIMESTAMP_KEY_FIRST, FIRST_TIMESTAMP_KEY, // first timestamp key
                REQUEST_KEY_TIMESTAMP_VALUE_FIRST, timestampFirst, // first timestamp value
                REQUEST_KEY_TIMESTAMP_KEY_LAST, LAST_TIMESTAMP_KEY, // last timestamp key
                REQUEST_KEY_TIMESTAMP_VALUE_LAST, timestampLast); // last timestamp value

        // Wait for complete callback
        assertEquals(RESULT_PASS, receiver.waitForActivity());
        receiver.close();

        // Get the most recent app start
        ApplicationStartInfo info = waitForAppStart();
        assertNotNull(info);

        // Verify that the timestamps are retrievable and they're the same
        // when we pull them back out.
        Map<Integer, Long> timestamps = info.getStartupTimestamps();
        long timestampFirstFromInfo = timestamps.get(FIRST_TIMESTAMP_KEY);
        long timestampLastFromInfo = timestamps.get(LAST_TIMESTAMP_KEY);

        assertEquals(timestampFirst, timestampFirstFromInfo);
        assertEquals(timestampLast, timestampLastFromInfo);
    }

    /**
     * Test that registered listeners are triggered when AppStartInfo is complete.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_START_INFO)
    public void testTriggerListeners() throws Exception {
        clearHistoricalStartInfo();

        ResultReceiverFilter receiver = new ResultReceiverFilter(REPLY_ACTION_COMPLETE, 1);

        executeShellCmd("am start --user %d -n %s/%s%s --ei %s %d",
                mTestRunningUserId, // test running user ID
                STUB_PACKAGE_NAME, STUB_PACKAGE_NAME, SIMPLE_ACTIVITY, // package/activity to start
                REQUEST_KEY_ACTION, REQUEST_VALUE_LISTENER_ADD_ONE); // action to perform

        // Wait for complete callback
        assertEquals(RESULT_PASS, receiver.waitForActivity());
        receiver.close();

        // Confirm that the app confirmed that it successfully received a callback.
        assertEquals(1, receiver.mIntents.size());

        Bundle extras = receiver.mIntents.get(0).getExtras();
        assertNotNull(extras);

        int status = extras.getInt(REPLY_EXTRA_STATUS_KEY, REPLY_STATUS_NONE);
        assertEquals(REPLY_EXTRA_SUCCESS_VALUE, status);
    }

    /**
     * Test that multiple registered listeners are triggered when AppStartInfo is complete.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_START_INFO)
    public void testTriggerMultipleListeners() throws Exception {
        clearHistoricalStartInfo();

        ResultReceiverFilter receiver = new ResultReceiverFilter(REPLY_ACTION_COMPLETE, 2);

        executeShellCmd("am start --user %d -n %s/%s%s --ei %s %d",
                mTestRunningUserId, // test running user ID
                STUB_PACKAGE_NAME, STUB_PACKAGE_NAME, SIMPLE_ACTIVITY, // package/activity to start
                REQUEST_KEY_ACTION,
                REQUEST_VALUE_LISTENER_ADD_MULTIPLE); // action to perform

        // Wait for complete callback
        assertEquals(RESULT_PASS, receiver.waitForActivity());
        receiver.close();

        // Confirm that the app confirmed that it successfully received a callback.
        assertEquals(2, receiver.mIntents.size());

        Bundle extras = receiver.mIntents.get(0).getExtras();
        assertNotNull(extras);

        int status = extras.getInt(REPLY_EXTRA_STATUS_KEY, REPLY_STATUS_NONE);
        assertEquals(REPLY_EXTRA_SUCCESS_VALUE, status);

        extras = receiver.mIntents.get(1).getExtras();
        assertNotNull(extras);

        status = extras.getInt(REPLY_EXTRA_STATUS_KEY, REPLY_STATUS_NONE);
        assertEquals(REPLY_EXTRA_SUCCESS_VALUE, status);
    }

    /**
     * Test that a removed listener is not triggered when AppStartInfo is complete.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_START_INFO)
    public void testRemoveListener() throws Exception {
        clearHistoricalStartInfo();

        ResultReceiverFilter receiver = new ResultReceiverFilter(REPLY_ACTION_COMPLETE, 2);

        executeShellCmd("am start --user %d -n %s/%s%s --ei %s %d",
                mTestRunningUserId, // test running user ID
                STUB_PACKAGE_NAME, STUB_PACKAGE_NAME, SIMPLE_ACTIVITY, // package/activity to start
                REQUEST_KEY_ACTION, REQUEST_VALUE_LISTENER_ADD_REMOVE); // action to perform

        // Wait for timeout callback to ensure the broadcast was only sent once for the remaining
        // listener. If we get a complete result this means that the removed listener was triggered.
        assertEquals(RESULT_TIMEOUT, receiver.waitForActivity());
        receiver.close();

        // Confirm that the app confirmed that it successfully received a callback on the not
        // removed listener, and did not receive one on the removed listener.
        assertEquals(1, receiver.mIntents.size());

        Bundle extras = receiver.mIntents.get(0).getExtras();
        assertNotNull(extras);

        int status = extras.getInt(REPLY_EXTRA_STATUS_KEY, REPLY_STATUS_NONE);
        assertEquals(REPLY_EXTRA_SUCCESS_VALUE, status);
    }

    private void clearHistoricalStartInfo() throws Exception {
        executeShellCmd("am clear-start-info --user all " + STUB_PACKAGE_NAME);
    }

    /** Query the app start info object until it indicates the startup is complete. */
    private ApplicationStartInfo waitForAppStart() {
        List<ApplicationStartInfo> list;

        for (int i = 0; i < MAX_WAITS_FOR_START; i++) {
            list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    STUB_PACKAGE_NAME, 1,
                    mActivityManager::getExternalHistoricalProcessStartReasons,
                    android.Manifest.permission.DUMP);

            if (list != null && list.size() == 1
                    && list.get(0).getStartupState()
                        == ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN) {
                return list.get(0);
            }
            sleep(WAIT_FOR_START_MS);
        }

        fail("The app didn't finish starting in time.");
        return null;
    }

    private void sleep(long timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
        }
    }

    @FormatMethod
    private String executeShellCmd(String cmdFormat, Object... args) throws Exception {
        String cmd = String.format(cmdFormat, args);
        String result = SystemUtil.runShellCommand(mInstrumentation, cmd);
        Log.d(TAG, String.format("Output for '%s': %s", cmd, result));
        return result;
    }

    private void verifyIds(ApplicationStartInfo info,
            int pid, int realUid, int packageUid, int definingUid) {
        assertNotNull(info);

        assertEquals(pid, info.getPid());
        assertEquals(realUid, info.getRealUid());
        assertEquals(definingUid, info.getDefiningUid());
        assertEquals(packageUid, info.getPackageUid());
    }

    /**
     * Verify that the info matches the passed state.
     * Null arguments are skipped in verification.
     */
    private void verify(ApplicationStartInfo info,
            String packageName, String processName, Intent intent,
            int reason, int startType, int launchMode, int startupState, int startComponent) {
        assertNotNull(info);

        if (packageName != null) {
            assertTrue(packageName.equals(info.getPackageName()));
        }

        if (processName != null) {
            assertTrue(processName.equals(info.getProcessName()));
        }

        if (intent != null) {
            assertTrue(intent.filterEquals(info.getIntent()));
        }

        assertEquals(reason, info.getReason());
        assertEquals(startType, info.getStartType());
        assertEquals(launchMode, info.getLaunchMode());
        assertEquals(startupState, info.getStartupState());

        if (android.app.Flags.appStartInfoComponent()) {
            assertEquals(startComponent, info.getStartComponent());
        }

        // Check that the appropriate timestamps exist based on the startup state
        // and that they're in the right order.
        Map<Integer, Long> timestamps = info.getStartupTimestamps();
        if (startupState == ApplicationStartInfo.STARTUP_STATE_STARTED) {
            Long launchTimestamp = timestamps.get(ApplicationStartInfo.START_TIMESTAMP_LAUNCH);
            assertTrue(launchTimestamp != null);
            assertTrue(launchTimestamp > 0);
        }

        if (startupState == ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN) {
            Long launchTimestamp = timestamps.get(ApplicationStartInfo.START_TIMESTAMP_LAUNCH);
            assertTrue(launchTimestamp != null);
            assertTrue(launchTimestamp > 0);

            Long bindApplicationTimestamp = timestamps.get(
                    ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION);
            assertTrue(bindApplicationTimestamp != null);
            assertTrue(bindApplicationTimestamp > 0);

            assertTrue(launchTimestamp < bindApplicationTimestamp);

            // TODO(287153617): Add support for START_TIMESTAMP_APPLICATION_ONCREATE
            // and START_TIMESTAMP_FIRST_FRAME
        }
    }

    private class ResultReceiverFilter extends BroadcastReceiver {
        private String mActivityToFilter;
        private int mResult = RESULT_TIMEOUT;
        private int mResultsToWaitFor;
        private static final int TIMEOUT_IN_MS = 5000;
        List<Intent> mIntents = new ArrayList<Intent>();

        // Create the filter with the intent to look for.
        ResultReceiverFilter(String activityToFilter, int resultsToWaitFor) {
            mActivityToFilter = activityToFilter;
            mResultsToWaitFor = resultsToWaitFor;
            IntentFilter filter = new IntentFilter();
            filter.addAction(mActivityToFilter);
            mInstrumentation.getTargetContext().registerReceiver(this, filter,
                    Context.RECEIVER_EXPORTED);
        }

        // Turn off the filter.
        public void close() {
            mInstrumentation.getTargetContext().unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(mActivityToFilter)) {
                synchronized (this) {
                    mIntents.add(intent);
                    if (mIntents.size() >= mResultsToWaitFor) {
                        mResult = RESULT_PASS;
                        notifyAll();
                    }
                }
            }
        }

        public int waitForActivity() throws Exception {
            AmUtils.waitForBroadcastBarrier();
            synchronized (this) {
                try {
                    wait(TIMEOUT_IN_MS);
                } catch (InterruptedException e) {
                }
            }
            return mResult;
        }
    }
}
