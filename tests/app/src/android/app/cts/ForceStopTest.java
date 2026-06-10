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
package android.app.cts;

import static android.app.Flags.FLAG_APP_START_INFO;
import static android.app.Flags.FLAG_USE_APP_INFO_NOT_LAUNCHED;
import static android.content.pm.Flags.FLAG_STAY_STOPPED;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.server.am.Flags.FLAG_AVOID_RESOLVING_TYPE;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.app.ApplicationStartInfo;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.stubs.BootReceiver;
import android.app.stubs.CommandReceiver;
import android.app.stubs.ISecondary;
import android.app.stubs.SimpleActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.Flags;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.AmUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@Presubmit
public final class ForceStopTest {

    // A simple test activity from another package.
    private static final String APP_PACKAGE = "com.android.app1";
    private static final String APP_APK = "/data/local/tmp/cts/apps/CtsAppTestStubsApp1.apk";
    private static final String APP_ACTIVITY = "android.app.stubs.SimpleActivity";
    private static final String APP_PROVIDER_PACKAGE = "com.android.app.cts.provider";

    private static final long DELAY_MILLIS = 10_000;
    private static final long SHORT_DELAY_MILLIS = 1_000;

    private Context mTargetContext;
    private ActivityManager mActivityManager;
    private PackageManager mPackageManager;
    private Instrumentation mInstrumentation;

    private long mTimestampMs;

    private int mUnstoppedReason = -3;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mTargetContext = mInstrumentation.getTargetContext();
        mActivityManager = mInstrumentation.getContext().getSystemService(ActivityManager.class);
        mPackageManager = mInstrumentation.getContext().getPackageManager();

        AmUtils.waitForBroadcastBarrier();
    }

    private Intent createSimpleActivityIntent() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.setPackage(APP_PACKAGE);
        intent.setClassName(APP_PACKAGE, APP_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private ActivityReceiverFilter forceStopAndStartSimpleActivity(Intent intent) throws Exception {
        // Ensure that there are no remaining component records of the test app package.
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(intent.getPackage()));
        ActivityReceiverFilter appStartedReceiver = new ActivityReceiverFilter(
                SimpleActivity.ACTION_ACTIVITY_STARTED);
        // Start an activity of another APK.
        mTargetContext.startActivity(intent);
        assertTrue(appStartedReceiver.waitForActivity());
        return appStartedReceiver;
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STAY_STOPPED)
    public void testPackageStoppedState() throws Exception {
        final Intent intent = createSimpleActivityIntent();
        final String packageName = intent.getPackage();
        forceStopAndStartSimpleActivity(intent);

        assertFalse("Package " + packageName + " shouldn't be in the stopped state",
                mPackageManager.isPackageStopped(packageName));

        // Force-stop it again
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));
        assertTrue("Package " + packageName + " should be in the stopped state",
                mPackageManager.isPackageStopped(packageName));
    }

    @Test
    public void testPackageRestartedBroadcast() throws Exception {
        final Intent intent = createSimpleActivityIntent();
        final String packageName = intent.getPackage();

        // Setup to receive broadcasts about stopped state
        final ConditionVariable gotRestarted = new ConditionVariable();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                final Uri uri = intent.getData();
                final String pkg = uri != null ? uri.getSchemeSpecificPart() : null;
                if (Intent.ACTION_PACKAGE_RESTARTED.equals(action)
                        && packageName.equals(pkg)) {
                    mTimestampMs = intent.getLongExtra(Intent.EXTRA_TIME, 0L);
                    gotRestarted.open();
                }
            }
        };

        mTimestampMs = 0;
        final long preStopTimestampMs = SystemClock.elapsedRealtime();

        final IntentFilter filter = new IntentFilter();
        filter.addDataScheme("package");
        filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
        mTargetContext.registerReceiver(receiver, filter);

        forceStopAndStartSimpleActivity(intent);

        // Force-stop it again
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));

        if (!gotRestarted.block(DELAY_MILLIS)) {
            fail("Didn't get ACTION_PACKAGE_RESTARTED");
        }
        if (Flags.stayStopped()) {
            assertTrue("EXTRA_TIME " + mTimestampMs + " not after " + preStopTimestampMs,
                    mTimestampMs >= preStopTimestampMs);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STAY_STOPPED)
    public void testPackageUnstoppedBroadcast() throws Exception {
        final Intent intent = createSimpleActivityIntent();
        final String packageName = intent.getPackage();

        // Setup to receive broadcasts about stopped state
        final ConditionVariable gotUnstopped = new ConditionVariable();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                final Uri uri = intent.getData();
                final String pkg = uri != null ? uri.getSchemeSpecificPart() : null;
                if (Intent.ACTION_PACKAGE_UNSTOPPED.equals(action)
                        && packageName.equals(pkg)) {
                    mTimestampMs = intent.getLongExtra(Intent.EXTRA_TIME, 0L);
                    gotUnstopped.open();
                }
            }
        };

        mTimestampMs = 0;
        final long preUnstopTimestampMs = SystemClock.elapsedRealtime();

        final IntentFilter filter = new IntentFilter();
        filter.addDataScheme("package");
        filter.addAction(Intent.ACTION_PACKAGE_UNSTOPPED);
        mTargetContext.registerReceiver(receiver, filter);

        forceStopAndStartSimpleActivity(intent);

        assertTrue("EXTRA_TIME " + mTimestampMs + " not after " + preUnstopTimestampMs,
                mTimestampMs >= preUnstopTimestampMs);

        if (!gotUnstopped.block(DELAY_MILLIS)) {
            fail("Didn't get ACTION_PACKAGE_UNSTOPPED");
        }

        // Force-stop it again to clean up
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STAY_STOPPED)
    public void testBootCompletedBroadcasts_activity() throws Exception {
        final Intent intent = createSimpleActivityIntent();

        final ConditionVariable gotLockedBoot = new ConditionVariable();
        final ConditionVariable gotBoot = new ConditionVariable();
        final ConditionVariable gotActivityStarted = new ConditionVariable();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (BootReceiver.ACTION_BOOT_COMPLETED_RECEIVED.equals(action)) {
                    final String extraAction = intent.getStringExtra(
                            BootReceiver.EXTRA_BOOT_COMPLETED_ACTION);
                    if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(extraAction)) {
                        gotLockedBoot.open();
                    } else if (Intent.ACTION_BOOT_COMPLETED.equals(extraAction)) {
                        gotBoot.open();
                    }
                } else if (SimpleActivity.ACTION_ACTIVITY_STARTED.equals(action)) {
                    gotActivityStarted.open();
                }
            }
        };
        final IntentFilter filter = new IntentFilter();
        filter.addAction(BootReceiver.ACTION_BOOT_COMPLETED_RECEIVED);
        filter.addAction(SimpleActivity.ACTION_ACTIVITY_STARTED);
        mTargetContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);

        mTargetContext.startActivity(intent);

        assertTrue("Activity didn't start", gotActivityStarted.block(DELAY_MILLIS));

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));

        mTargetContext.startActivity(intent);

        assertTrue("Didn't get LOCKED_BOOT_COMPLETED", gotLockedBoot.block(DELAY_MILLIS));
        assertTrue("Didn't get BOOT_COMPLETED", gotBoot.block(DELAY_MILLIS));

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    // Verifies that no BOOT_COMPLETED broadcasts are received on first launch for given action
    private void verifyNoBootCompletedBroadcastsGeneric(Runnable r) throws Exception {
        // Re-install the app to reset the notLaunched package state
        executeShellCommand("pm uninstall " + APP_PACKAGE);
        executeShellCommand("pm install -r --force-queryable " + APP_APK);

        final ConditionVariable gotLockedBoot = new ConditionVariable();
        final ConditionVariable gotBoot = new ConditionVariable();
        final ConditionVariable gotAppStarted = new ConditionVariable();

        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (BootReceiver.ACTION_BOOT_COMPLETED_RECEIVED.equals(action)) {
                    final String extraAction = intent.getStringExtra(
                            BootReceiver.EXTRA_BOOT_COMPLETED_ACTION);
                    if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(extraAction)) {
                        gotLockedBoot.open();
                    } else if (Intent.ACTION_BOOT_COMPLETED.equals(extraAction)) {
                        gotBoot.open();
                    }
                }
            }
        };
        final IntentFilter filter = new IntentFilter();
        filter.addAction(BootReceiver.ACTION_BOOT_COMPLETED_RECEIVED);
        filter.addAction(SimpleActivity.ACTION_ACTIVITY_STARTED);
        mTargetContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);

        r.run();

        CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                CommandReceiver.COMMAND_EMPTY, APP_PACKAGE, APP_PACKAGE,
                0, null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        gotAppStarted.open();
                    }
                });

        assertTrue("App didn't start", gotAppStarted.block(DELAY_MILLIS));

        assertFalse("Got unexpected LOCKED_BOOT_COMPLETED", gotLockedBoot.block(DELAY_MILLIS));
        assertFalse("Got unexpected BOOT_COMPLETED", gotBoot.block(SHORT_DELAY_MILLIS));

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    /**
     * Verifies no BOOT_COMPLETED broadcast on first launch for an activity start.
     */
    @Test
    @RequiresFlagsEnabled(FLAG_USE_APP_INFO_NOT_LAUNCHED)
    public void testNoBootCompletedBroadcastsOnFirstLaunch_activity() throws Exception {
        verifyNoBootCompletedBroadcastsGeneric(() -> {
            final Intent intent = createSimpleActivityIntent();
            mTargetContext.startActivity(intent);
        });
    }

    /**
     * Verifies no BOOT_COMPLETED broadcast on first launch for a broadcast.
     */
    @Test
    @RequiresFlagsEnabled(FLAG_USE_APP_INFO_NOT_LAUNCHED)
    public void testNoBootCompletedBroadcastsOnFirstLaunch_broadcast() throws Exception {
        verifyNoBootCompletedBroadcastsGeneric(() -> {
            CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                    CommandReceiver.COMMAND_EMPTY, APP_PACKAGE, APP_PACKAGE,
                    0, null, null);
        });
    }

    /**
     * Verifies no BOOT_COMPLETED broadcast on first launch for a service binding.
     */
    @Test
    @RequiresFlagsEnabled(FLAG_USE_APP_INFO_NOT_LAUNCHED)
    public void testNoBootCompletedBroadcastsOnFirstLaunch_bindService() throws Exception {
        verifyNoBootCompletedBroadcastsGeneric(() -> {
            int startReason = getStartReasonFromAppPackageService();
            assertNotEquals("ForceStop reason should not be returned, should be -ve",
                    ApplicationStartInfo.START_REASON_SERVICE, startReason);
        });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STAY_STOPPED)
    public void testBootCompletedBroadcasts_broadcast() throws Exception {
        final ConditionVariable gotLockedBoot = new ConditionVariable();
        final ConditionVariable gotBoot = new ConditionVariable();
        final ConditionVariable appStarted = new ConditionVariable();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (BootReceiver.ACTION_BOOT_COMPLETED_RECEIVED.equals(action)) {
                    final String extraAction = intent.getStringExtra(
                            BootReceiver.EXTRA_BOOT_COMPLETED_ACTION);
                    if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(extraAction)) {
                        gotLockedBoot.open();
                    } else if (Intent.ACTION_BOOT_COMPLETED.equals(extraAction)) {
                        gotBoot.open();
                    }
                }
            }
        };
        CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                CommandReceiver.COMMAND_EMPTY, APP_PACKAGE, APP_PACKAGE,
                0, null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        appStarted.open();
                    }
                });

        assertTrue("App didn't start", appStarted.block(DELAY_MILLIS));

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));

        AmUtils.waitForBroadcastBarrier();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(BootReceiver.ACTION_BOOT_COMPLETED_RECEIVED);
        mTargetContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);

        CommandReceiver.sendCommand(mTargetContext,
                CommandReceiver.COMMAND_EMPTY, APP_PACKAGE, APP_PACKAGE,
                0, null);

        assertTrue("Didn't get LOCKED_BOOT_COMPLETED", gotLockedBoot.block(DELAY_MILLIS));
        assertTrue("Didn't get BOOT_COMPLETED", gotBoot.block(DELAY_MILLIS));

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    private void clearHistoricalStartInfo() throws Exception {
        executeShellCommand("am clear-start-info --user all " + APP_PACKAGE);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_STAY_STOPPED, FLAG_APP_START_INFO})
    public void testApplicationStartInfoWasForceStopped_bindService() throws Exception {
        clearHistoricalStartInfo();
        // Check bindService after a force-stop
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
        int startReason = getStartReasonFromAppPackageService();
        assertEquals("ForceStop reason is not SERVICE",
                ApplicationStartInfo.START_REASON_SERVICE, startReason);

        clearHistoricalStartInfo();
        // Check bindService after stop-app
        executeShellCommand("am stop-app --user " + mTargetContext.getUserId() + " " + APP_PACKAGE);
        startReason = getStartReasonFromAppPackageService();
        assertNotEquals("ForceStop reason should not be returned, should be -ve",
                ApplicationStartInfo.START_REASON_SERVICE, startReason);

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_STAY_STOPPED, FLAG_APP_START_INFO})
    public void testApplicationStartInfoWasForceStopped_activity() throws Exception {
        clearHistoricalStartInfo();

        // Trigger an app start via service binding
        final int firstStartReason = getStartReasonFromAppPackageService();

        // Force-stop the app
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));

        final Intent intent = createSimpleActivityIntent();

        final ConditionVariable gotActivityStarted = new ConditionVariable();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (SimpleActivity.ACTION_ACTIVITY_STARTED.equals(action)) {
                    gotActivityStarted.open();
                }
            }
        };
        final IntentFilter filter = new IntentFilter();
        filter.addAction(SimpleActivity.ACTION_ACTIVITY_STARTED);
        mTargetContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);

        // Check startActivity after a force-stop
        mTargetContext.startActivity(intent);
        assertTrue("Activity didn't start", gotActivityStarted.block(DELAY_MILLIS));

        final int startReason = getStartReasonFromAppPackageService();
        assertEquals("ForceStop reason is not ACTIVITY",
                ApplicationStartInfo.START_REASON_START_ACTIVITY, startReason);

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    /**
     * Returns the start reason only if the app was force-stopped earlier, else returns -ve.
     */
    private int getStartReasonFromAppPackageService() {
        final ConditionVariable serviceConnected = new ConditionVariable();

        Intent serviceIntent = new Intent("android.app.stubs.ISecondaryMain");
        serviceIntent.setPackage(APP_PACKAGE);
        mUnstoppedReason = -2;
        mTargetContext.bindService(serviceIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                try {
                    mUnstoppedReason =
                            (ISecondary.Stub.asInterface(service)).getWasForceStoppedReason();
                } catch (RemoteException re) {
                }
                serviceConnected.open();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        }, Context.BIND_AUTO_CREATE);

        assertTrue("Couldn't connect to android.app.stubs.ISecondaryMain",
                serviceConnected.block(DELAY_MILLIS));
        return mUnstoppedReason;
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STAY_STOPPED)
    public void testPendingIntentCancellation() throws Exception {
        final PendingIntent pendingIntent = triggerPendingIntentCreation(APP_PACKAGE);
        assertNotNull(pendingIntent);

        final ConditionVariable pendingIntentCancelled = new ConditionVariable();
        pendingIntent.addCancelListener(mTargetContext.getMainExecutor(), pi -> {
            if (pendingIntent.equals(pi)) {
                pendingIntentCancelled.open();
            }
        });

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
        assertTrue("Package " + APP_PACKAGE + " should be in the stopped state",
                mPackageManager.isPackageStopped(APP_PACKAGE));

        // Verify that pending intent gets cancelled when the app that created it is force-stopped.
        assertTrue("Did not receive PendingIntent cancellation callback",
                pendingIntentCancelled.block(DELAY_MILLIS));
        assertThrows(CanceledException.class, () -> pendingIntent.send());

        // Trigger the PendingIntent creation to verify the app can create new PendingIntents
        // as usual.
        final PendingIntent pendingIntent2 = triggerPendingIntentCreation(APP_PACKAGE);
        assertNotNull(pendingIntent2);

        // Force-stop it again to clean up
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    @Test
    @RequiresFlagsDisabled(FLAG_STAY_STOPPED)
    public void testPendingIntentRetained() throws Exception {
        final PendingIntent pendingIntent = triggerPendingIntentCreation(APP_PACKAGE);
        assertNotNull(pendingIntent);

        final ConditionVariable pendingIntentCancelled = new ConditionVariable();
        pendingIntent.addCancelListener(mTargetContext.getMainExecutor(), pi -> {
            if (pendingIntent.equals(pi)) {
                pendingIntentCancelled.open();
            }
        });

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
        assertTrue("Package " + APP_PACKAGE + " should be in the stopped state",
                mPackageManager.isPackageStopped(APP_PACKAGE));

        // Verify that pending intent does not get cancelled when the app that created it
        // is force-stopped.
        assertFalse("Received PendingIntent cancellation callback",
                pendingIntentCancelled.block(DELAY_MILLIS));
        // Trigger pendingIntent to verify there is no exception thrown.
        pendingIntent.send();

        // Trigger the PendingIntent creation to verify the app can create new PendingIntents
        // as usual.
        final PendingIntent pendingIntent2 = triggerPendingIntentCreation(APP_PACKAGE);
        assertNotNull(pendingIntent2);

        // Force-stop it again to clean up
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(APP_PACKAGE));
    }

    @RequiresFlagsEnabled(FLAG_AVOID_RESOLVING_TYPE)
    @Test
    public void testStickyBroadcastDispatch() throws Exception {
        final String pkg = APP_PROVIDER_PACKAGE;
        final IntentFilter intentFilter = triggerStickyBroadcastDispatch(pkg);
        assertNotNull(intentFilter);

        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(pkg));
        assertTrue("Package " + pkg + " should be in the stopped state",
                mPackageManager.isPackageStopped(pkg));

        // Register a receiver which involves intent-filter resolution and then verify
        // that this intent-filter resolution does not bring the broadcast sender out of
        // force-stop state.
        final Intent stickyIntent = mTargetContext.registerReceiver(null, intentFilter);
        assertNotNull(stickyIntent);

        assertTrue("Package " + pkg + " should still be in the stopped state",
                mPackageManager.isPackageStopped(pkg));
    }

    private PendingIntent triggerPendingIntentCreation(final String packageName) throws Exception {
        final BlockingQueue<PendingIntent> blockingQueue = new LinkedBlockingQueue<>();
        CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                CommandReceiver.COMMAND_CREATE_FGSL_PENDING_INTENT,
                packageName, packageName, Intent.FLAG_RECEIVER_FOREGROUND, null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final PendingIntent pi = getResultExtras(true).getParcelable(
                                CommandReceiver.KEY_PENDING_INTENT, PendingIntent.class);
                        if (pi != null) {
                            blockingQueue.offer(pi);
                        }
                    }
                });
        return blockingQueue.poll(DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private IntentFilter triggerStickyBroadcastDispatch(String packageName) throws Exception {
        final BlockingQueue<IntentFilter> blockingQueue = new LinkedBlockingQueue<>();
        CommandReceiver.sendCommandWithResultReceiver(mTargetContext,
                CommandReceiver.COMMAND_SEND_STICKY_BROADCAST,
                packageName, packageName, Intent.FLAG_RECEIVER_FOREGROUND, null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final IntentFilter intentFilter = getResultExtras(true).getParcelable(
                                CommandReceiver.KEY_STICKY_BROADCAST_FILTER, IntentFilter.class);
                        if (intentFilter != null) {
                            blockingQueue.offer(intentFilter);
                        }
                    }
                });
        return blockingQueue.poll(DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    // The receiver filter needs to be instantiated with the command to filter for before calling
    // startActivity.
    private class ActivityReceiverFilter extends BroadcastReceiver {
        // The activity we want to filter for.
        private String mActivityToFilter;
        private ConditionVariable mBroadcastCondition = new ConditionVariable();

        // Create the filter with the intent to look for.
        ActivityReceiverFilter(String activityToFilter) {
            mActivityToFilter = activityToFilter;
            final IntentFilter filter = new IntentFilter();
            filter.addAction(mActivityToFilter);
            mTargetContext.registerReceiver(this, filter,
                    Context.RECEIVER_EXPORTED);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(mActivityToFilter)) {
                mBroadcastCondition.open();
            }
        }

        public boolean waitForActivity() throws Exception {
            AmUtils.waitForBroadcastBarrier();
            // Wait for the broadcast
            return mBroadcastCondition.block(DELAY_MILLIS);
        }
    }

    private String executeShellCommand(String cmd) throws IOException {
        final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
        return uiDevice.executeShellCommand(cmd).trim();
    }
}
