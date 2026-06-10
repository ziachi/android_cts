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

package android.content.pm.cts;

import static android.Manifest.permission.GET_INTENT_SENDER_INTENT;
import static android.Manifest.permission.OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD;
import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.content.IntentFilter.BLOCK_NULL_ACTION_INTENTS;
import static android.security.Flags.FLAG_BLOCK_NULL_ACTION_INTENTS;
import static android.security.Flags.FLAG_ENABLE_INTENT_MATCHING_FLAGS;
import static android.security.Flags.FLAG_ENFORCE_INTENT_FILTER_MATCH;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.app.AppGlobals;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.app.compat.CompatChanges;
import android.app.compat.PackageOverride;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.StrictMode;
import android.os.strictmode.UnsafeIntentLaunchViolation;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@AppModeFull // TODO(Instant) Figure out which APIs should work.
@RunWith(AndroidJUnit4.class)
public class SaferIntentTest {

    private Context mContext;
    private PackageManager mPackageManager;
    private Instrumentation mInstrumentation;
    private ArrayList<BroadcastReceiver> mRegisteredReceiverList;
    private LinkedBlockingQueue<StrictMode.ViolationInfo> mViolations;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final long IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID = 229362273;

    private static final String ACTIVITY_NAME = "android.content.pm.cts.TestPmActivity";
    private static final String ACTIVITY_THAT_ALLOWS_NULL_ACTION =
            "android.content.pm.cts.TestPmActivityAllowsNullAction";
    private static final String SERVICE_NAME = "android.content.pm.cts.TestPmService";
    private static final String SERVICE_NAME_2 = "android.content.pm.cts.TestAnotherPmService";
    private static final String RECEIVER_NAME = "android.content.pm.cts.PmTestReceiver";
    private static final String RECEIVER_NAME_2 = "android.content.pm.cts.TestAnotherPmReceiver";

    private static final String EXPORTED_ACTION = "android.intent.action.cts.EXPORTED_ACTION";
    private static final String NON_EXPORTED_ACTION =
            "android.intent.action.cts.NON_EXPORTED_ACTION";

    private static final String NON_EXISTENT_ACTION_NAME = "android.intent.action.cts.NON_EXISTENT";

    // An app that does not have any safer intent enforcement
    private static final String RESOLUTION_TEST_PKG_NAME =
            "android.content.cts.IntentResolutionTest";

    // An app that is similar to RESOLUTION_TEST_PKG_NAME but has "enforceIntentFilter" flag at
    // the application level
    private static final String RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME =
            "android.content.cts.CtsIntentResolutionApplicationOverrideTestApp";

    // An app that has "enforceIntentFilter" flag on the application level, but has overrides on
    // the intent-filters
    private static final String RESOLUTION_COMPONENT_OVERRIDE_TEST_PKG_NAME =
            "android.content.cts.CtsIntentResolutionComponentOverrideTestApp";

    private static final String ACTION_RECEIVING_INTENT = "android.content.cts.RECEIVING_INTENT";
    private static final String SELECTOR_ACTION_NAME = "android.intent.action.SELECTORTEST";
    private static final String FILE_PROVIDER_AUTHORITY = "android.content.cts.fileprovider";
    private static final String RESOLUTION_TEST_ACTION_NAME =
            "android.intent.action.RESOLUTION_TEST";

    static class WaitReceiver extends BroadcastReceiver {
        private CountDownLatch mLatch = new CountDownLatch(1);

        void reset() {
            mLatch = new CountDownLatch(1);
        }

        boolean waitOnReceive() throws InterruptedException {
            SystemUtil.waitForBroadcasts();
            return mLatch.await(5, TimeUnit.SECONDS);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mLatch.countDown();
        }
    }

    static class IntentRetriever extends WaitReceiver {
        Intent mIntent;

        @Override
        public void onReceive(Context context, Intent intent) {
            mIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            super.onReceive(context, intent);
        }
    }

    enum LaunchType {
        ACTIVITY, SERVICE, BROADCAST
    }

    @Before
    public void setup() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mPackageManager = mContext.getPackageManager();
        mRegisteredReceiverList = new ArrayList<>();
        mViolations = new LinkedBlockingQueue<>();
        StrictMode.setViolationLogger(mViolations::offer);

        List<String> testApps = List.of(RESOLUTION_TEST_PKG_NAME,
                RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME,
                RESOLUTION_COMPONENT_OVERRIDE_TEST_PKG_NAME);
        for (String testApp : testApps) {
            // Bring test app out of the stopped state so that it can receive broadcasts
            SystemUtil.runWithShellPermissionIdentity(() ->
                            AppGlobals.getPackageManager().setPackageStoppedState(
                                    testApp, false,
                                    Process.myUserHandle().getIdentifier()),
                    Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE
            );
            // Exempt test app so we can start its services
            SystemUtil.runShellCommand("cmd deviceidle whitelist +" + testApp);
        }
    }

    @After
    public void tearDown() throws Exception {
        SystemUtil.runWithShellPermissionIdentity(() ->
                CompatChanges.removePackageOverrides(mContext.getPackageName(),
                        Set.of(IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID,
                                BLOCK_NULL_ACTION_INTENTS)),
                OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD);
        for (BroadcastReceiver receiver : mRegisteredReceiverList) {
            mContext.unregisterReceiver(receiver);
        }
        StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX);
        StrictMode.setViolationLogger(null);
    }

    private void setCompatOverride(long changeId, boolean enable) {
        var override = Map.of(changeId, new PackageOverride.Builder().setEnabled(enable).build());
        SystemUtil.runWithShellPermissionIdentity(
                () -> CompatChanges.putPackageOverrides(mContext.getPackageName(), override),
                OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD);
    }

    private void enableStrictMode() {
        var policy = new StrictMode.VmPolicy.Builder()
                .detectUnsafeIntentLaunch()
                .penaltyLog()
                .build();
        StrictMode.setVmPolicy(policy);
    }

    private void assertViolation(boolean b) throws InterruptedException {
        StrictMode.ViolationInfo v = mViolations.poll(5, TimeUnit.SECONDS);
        // No other violations queued up
        assertTrue(mViolations.isEmpty());
        if (b) {
            assertNotNull(v);
            assertTrue(UnsafeIntentLaunchViolation.class.isAssignableFrom(v.getViolationClass()));
        } else {
            assertNull(v);
        }
    }

    @Test
    public void testStartInternalExportedActivity() {
        setCompatOverride(IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID, true);
        Intent intent = new Intent(EXPORTED_ACTION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    @Test
    public void testStartInternalNonExportedActivity() throws InterruptedException {
        setCompatOverride(IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID, false);
        enableStrictMode();

        Intent intent = new Intent(NON_EXPORTED_ACTION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        // Strict mode should still catch the unsafe usage
        assertViolation(true);

        // Enable the feature
        setCompatOverride(IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID, true);
        assertThrows(ActivityNotFoundException.class, () -> mContext.startActivity(intent));
        assertViolation(true);

        // Switching to explicit should work properly
        intent.setPackage(mContext.getPackageName());
        mContext.startActivity(intent);
        assertViolation(false);
    }

    @Test
    public void testBroadcastInternalExportedRuntimeReceiver()
            throws InterruptedException {
        setCompatOverride(IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID, true);
        var receiver = new WaitReceiver();
        var filter = new IntentFilter(EXPORTED_ACTION);
        mContext.registerReceiver(receiver, filter, RECEIVER_EXPORTED);
        mRegisteredReceiverList.add(receiver);
        mContext.sendBroadcast(new Intent(EXPORTED_ACTION));
        assertTrue(receiver.waitOnReceive());
    }

    @Test
    public void testBroadcastInternalNonExportedRuntimeReceiver()
            throws InterruptedException {
        setCompatOverride(IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID, false);
        enableStrictMode();

        var receiver = new WaitReceiver();
        var filter = new IntentFilter(NON_EXPORTED_ACTION);
        mContext.registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED);
        mRegisteredReceiverList.add(receiver);
        var intent = new Intent(NON_EXPORTED_ACTION);

        receiver.reset();
        mContext.sendBroadcast(intent);
        assertTrue(receiver.waitOnReceive());
        // Strict mode should still catch the unsafe usage
        assertViolation(true);

        // Enable the feature
        setCompatOverride(IMPLICIT_INTENTS_ONLY_MATCH_EXPORTED_COMPONENTS_CHANGEID, true);
        receiver.reset();
        mContext.sendBroadcast(intent);
        assertFalse(receiver.waitOnReceive());
        assertViolation(true);

        // Switching to explicit should work properly
        intent.setPackage(mContext.getPackageName());
        receiver.reset();
        mContext.sendBroadcast(intent);
        assertTrue(receiver.waitOnReceive());
        assertViolation(false);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENFORCE_INTENT_FILTER_MATCH, FLAG_ENABLE_INTENT_MATCHING_FLAGS})
    public void testQueryEnforceIntentFilterMatch() {
        final var emptyFlags = PackageManager.ResolveInfoFlags.of(0);
        final var activityFlags = PackageManager.ResolveInfoFlags.of(
                PackageManager.MATCH_DEFAULT_ONLY);

        Intent intent = new Intent();
        List<ResolveInfo> results;

        /* Non-component intent tests */

        intent.setPackage(RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME);

        // Package intents with matching intent filter
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(2 /* TestPmActivity and TestPmActivityWithSelector */, results.size());
        // MATCH_DEFAULT_ONLY will change the result
        results = mPackageManager.queryIntentActivities(intent, activityFlags);
        assertEquals(1 /* TestPmActivity */, results.size());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(1, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());

        // Package intents with non-matching intent filter
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(0, results.size());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(0, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());

        /* Component intent tests */

        intent = new Intent();
        ComponentName comp;

        // Component intents with matching intent filter
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);
        comp = new ComponentName(RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME, ACTIVITY_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(1, results.size());
        // MATCH_DEFAULT_ONLY shall NOT change the result
        results = mPackageManager.queryIntentActivities(intent, activityFlags);
        assertEquals(1, results.size());
        comp = new ComponentName(RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME, SERVICE_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(1, results.size());
        comp = new ComponentName(RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME, RECEIVER_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());

        // Component intents with non-matching intent filter
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        comp = new ComponentName(RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME, ACTIVITY_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(0, results.size());
        comp = new ComponentName(RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME, SERVICE_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(0, results.size());
        comp = new ComponentName(RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME, RECEIVER_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());

        // More comprehensive intent matching tests
        intent = new Intent();
        comp = new ComponentName(RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME, RECEIVER_NAME);
        intent.setComponent(comp);
        intent.setAction(RESOLUTION_TEST_ACTION_NAME + "2");
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());
        intent.setType("*/*");
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());
        intent.setData(Uri.parse("http://example.com"));
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());
        intent.setDataAndType(Uri.parse("http://example.com"), "*/*");
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());
        File file = new File(mContext.getFilesDir(), "test.txt");
        try {
            file.createNewFile();
        } catch (IOException e) {
            fail(e.getMessage());
        }
        Uri uri = FileProvider.getUriForFile(mContext, FILE_PROVIDER_AUTHORITY, file);
        intent.setData(uri);
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());
        file.delete();
        intent.addCategory(Intent.CATEGORY_APP_BROWSER);
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());

        // Component intents with non-matching intent filter on our own package
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        comp = new ComponentName(mContext.getPackageName(), ACTIVITY_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(1, results.size());
        comp = new ComponentName(mContext.getPackageName(), SERVICE_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(1, results.size());
        comp = new ComponentName(mContext.getPackageName(), RECEIVER_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());

        /* Intent selector tests */

        Intent selector = new Intent();
        selector.setPackage(RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME);
        intent = new Intent();
        intent.setSelector(selector);

        // Matching intent and matching selector
        selector.setAction(SELECTOR_ACTION_NAME);
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(1, results.size());
        // MATCH_DEFAULT_ONLY shall NOT change the result
        results = mPackageManager.queryIntentActivities(intent, activityFlags);
        assertEquals(1, results.size());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(1, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());

        // Matching intent and non-matching selector
        selector.setAction(NON_EXISTENT_ACTION_NAME);
        intent.setAction(RESOLUTION_TEST_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(0, results.size());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(0, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());

        // Non-matching intent and matching selector
        selector.setAction(SELECTOR_ACTION_NAME);
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(0, results.size());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(0, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(0, results.size());
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ENFORCE_INTENT_FILTER_MATCH, FLAG_ENABLE_INTENT_MATCHING_FLAGS})
    public void testQueryEnforcePendingIntentFilterMatch() {
        // Non-matching intent cannot be resolved in our package
        var intent = new Intent(NON_EXISTENT_ACTION_NAME)
                .setClassName(RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME, RECEIVER_NAME);
        List<ResolveInfo> results = mPackageManager.queryBroadcastReceivers(intent, 0);
        assertEquals(0, results.size());

        // Send this intent over to the owner to create PI
        Bundle extras = new Bundle();
        extras.putParcelable(Intent.EXTRA_INTENT, intent);
        var authority = RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME + ".provider";
        Bundle b = mContext.getContentResolver().call(authority, "", null, extras);
        assertNotNull(b);
        PendingIntent pi = b.getParcelable(Intent.EXTRA_INTENT, PendingIntent.class);
        assertNotNull(pi);

        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(GET_INTENT_SENDER_INTENT);
        try {
            // Querying on behalf of the PI creator should work properly
            results = pi.queryIntentComponents(0);
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
        assertEquals(1, results.size());
    }

    @Test
    public void testQueryLegacyIntentFilterMatch() {
        final var emptyFlags = PackageManager.ResolveInfoFlags.of(0);

        Intent intent = new Intent();
        List<ResolveInfo> results;
        ComponentName comp;

        /* Component explicit intent tests */

        // Explicit intents with non-matching intent filter
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        comp = new ComponentName(RESOLUTION_TEST_PKG_NAME, ACTIVITY_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(1, results.size());
        comp = new ComponentName(RESOLUTION_TEST_PKG_NAME, SERVICE_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(1, results.size());
        comp = new ComponentName(RESOLUTION_TEST_PKG_NAME, RECEIVER_NAME);
        intent.setComponent(comp);
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());

        /* Intent selector tests */

        Intent selector = new Intent();
        selector.setPackage(RESOLUTION_TEST_PKG_NAME);
        intent = new Intent();
        intent.setSelector(selector);

        // Non-matching intent and matching selector
        selector.setAction(SELECTOR_ACTION_NAME);
        intent.setAction(NON_EXISTENT_ACTION_NAME);
        results = mPackageManager.queryIntentActivities(intent, emptyFlags);
        assertEquals(1, results.size());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertEquals(1, results.size());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertEquals(1, results.size());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_BLOCK_NULL_ACTION_INTENTS)
    public void testQueryNullActionMatch() {
        final var activityFlags = PackageManager.ResolveInfoFlags.of(
                PackageManager.MATCH_DEFAULT_ONLY);
        final var emptyFlags = PackageManager.ResolveInfoFlags.of(0);

        // Create a package explicit intent with null action
        Intent intent = new Intent();
        intent.setPackage(RESOLUTION_TEST_PKG_NAME);
        List<ResolveInfo> results;

        // Test legacy behavior
        setCompatOverride(BLOCK_NULL_ACTION_INTENTS, false);

        // Null action intent should match
        results = mPackageManager.queryIntentActivities(intent, activityFlags);
        assertFalse(results.isEmpty());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertFalse(results.isEmpty());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertFalse(results.isEmpty());

        // Test new behavior
        setCompatOverride(BLOCK_NULL_ACTION_INTENTS, true);

        // Null action intent should not match
        results = mPackageManager.queryIntentActivities(intent, activityFlags);
        assertTrue(results.isEmpty());
        results = mPackageManager.queryIntentServices(intent, emptyFlags);
        assertTrue(results.isEmpty());
        results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        assertTrue(results.isEmpty());
    }

    private void testComponentMismatchOnEnforcedApp(LaunchType type) throws InterruptedException {
        if (!android.security.Flags.enableIntentMatchingFlags()) {
            return;
        }
        final var retriever = new IntentRetriever();
        final var filter = new IntentFilter(ACTION_RECEIVING_INTENT);
        mContext.registerReceiver(retriever, filter, Context.RECEIVER_EXPORTED);
        mRegisteredReceiverList.add(retriever);
        enableStrictMode();

        // Set up intent with non matching action that targets app that has enforcements
        final var nonMatchingActionTargetEnforced = createComponentIntent(
                NON_EXISTENT_ACTION_NAME,
                type, RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME);

        // Intent should be blocked
        retriever.reset();
        switch (type) {
            case ACTIVITY -> assertThrows(
                    ActivityNotFoundException.class,
                    () -> mContext.startActivity(nonMatchingActionTargetEnforced));
            case SERVICE -> mContext.startService(nonMatchingActionTargetEnforced);
            case BROADCAST -> mContext.sendBroadcast(nonMatchingActionTargetEnforced);
        }
        assertFalse(retriever.waitOnReceive());
        assertViolation(true);

        // Test matching actions
        final var matchingActionTargetEnforced = createComponentIntent(
                RESOLUTION_TEST_ACTION_NAME,
                type, RESOLUTION_APPLICATION_OVERRIDE_TEST_PKG_NAME);

        // Should not be blocked and should not be marked as non-matching
        retriever.reset();
        startIntent(matchingActionTargetEnforced, type);

        assertTrue(retriever.waitOnReceive());
        assertFalse(retriever.mIntent.isMismatchingFilter());
        assertViolation(false);
    }

    private void testComponentMismatchOnUnenforcedApp(LaunchType type) throws InterruptedException {
        final var retriever = new IntentRetriever();
        final var filter = new IntentFilter(ACTION_RECEIVING_INTENT);
        mContext.registerReceiver(retriever, filter, Context.RECEIVER_EXPORTED);
        mRegisteredReceiverList.add(retriever);
        enableStrictMode();

        // Package intents should always match
        var packageIntent = new Intent(RESOLUTION_TEST_ACTION_NAME)
                .setPackage(RESOLUTION_TEST_PKG_NAME);
        if (type.equals(LaunchType.ACTIVITY)) {
            packageIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        retriever.reset();
        startIntent(packageIntent, type);
        assertTrue(retriever.waitOnReceive());
        assertFalse(retriever.mIntent.isMismatchingFilter());

        // Set up intent with non matching action that targets app that does not enforce
        final var nonMatchingActionTargetUnEnforced = createComponentIntent(
                NON_EXISTENT_ACTION_NAME, type, RESOLUTION_TEST_PKG_NAME);

        // Intent should not be blocked, but still marked as non-matching
        retriever.reset();
        startIntent(nonMatchingActionTargetUnEnforced, type);

        assertTrue(retriever.waitOnReceive());
        assertTrue(retriever.mIntent.isMismatchingFilter());
        assertViolation(true);

        // Test matching actions
        final var matchingActionTargetUnEnforced = createComponentIntent(
                RESOLUTION_TEST_ACTION_NAME, type, RESOLUTION_TEST_PKG_NAME);
        // Should not be blocked and should not be marked as non-matching
        retriever.reset();
        startIntent(matchingActionTargetUnEnforced, type);

        assertTrue(retriever.waitOnReceive());
        assertFalse(retriever.mIntent.isMismatchingFilter());
        assertViolation(false);

        // Test whether the flag is cleared when matching package intents
        var packageIntentWithMismatchFlag = new Intent(RESOLUTION_TEST_ACTION_NAME)
                .setPackage(RESOLUTION_TEST_PKG_NAME)
                .addExtendedFlags(Intent.EXTENDED_FLAG_FILTER_MISMATCH);
        if (type.equals(LaunchType.ACTIVITY)) {
            packageIntentWithMismatchFlag.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        retriever.reset();
        startIntent(packageIntentWithMismatchFlag, type);
        assertTrue(retriever.waitOnReceive());
        assertFalse(retriever.mIntent.isMismatchingFilter());
    }

    private Intent createComponentIntent(String action, LaunchType type, String packageName) {
        final var compIntent = new Intent(action);
        switch (type) {
            case ACTIVITY -> compIntent
                    .setClassName(packageName, ACTIVITY_NAME)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            case SERVICE -> compIntent.setClassName(packageName, SERVICE_NAME);
            case BROADCAST -> compIntent.setClassName(packageName, RECEIVER_NAME);
        }
        return compIntent;
    }

    private void startIntent(Intent intent, LaunchType type) {
        switch (type) {
            case ACTIVITY -> mContext.startActivity(intent);
            case SERVICE -> mContext.startService(intent);
            case BROADCAST -> mContext.sendBroadcast(intent);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENFORCE_INTENT_FILTER_MATCH)
    public void testActivityIntentMismatch() throws InterruptedException {
        testComponentMismatchOnEnforcedApp(LaunchType.ACTIVITY);
        testComponentMismatchOnUnenforcedApp(LaunchType.ACTIVITY);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENFORCE_INTENT_FILTER_MATCH)
    public void testServiceIntentMismatch() throws InterruptedException {
        testComponentMismatchOnEnforcedApp(LaunchType.SERVICE);
        testComponentMismatchOnUnenforcedApp(LaunchType.SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENFORCE_INTENT_FILTER_MATCH)
    public void testBroadcastIntentMismatch() throws InterruptedException {
        testComponentMismatchOnEnforcedApp(LaunchType.BROADCAST);
        testComponentMismatchOnUnenforcedApp(LaunchType.BROADCAST);
    }

    private void testComponentNullActionMatch(Intent intent, LaunchType type)
            throws InterruptedException {
        final var receiver = new WaitReceiver();
        final var filter = new IntentFilter(ACTION_RECEIVING_INTENT);
        mContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        mRegisteredReceiverList.add(receiver);
        enableStrictMode();

        // Test legacy behavior
        setCompatOverride(BLOCK_NULL_ACTION_INTENTS, false);
        receiver.reset();
        switch (type) {
            case ACTIVITY -> mContext.startActivity(intent);
            case SERVICE -> mContext.startService(intent);
            case BROADCAST -> mContext.sendBroadcast(intent);
        }
        assertTrue(receiver.waitOnReceive());
        assertViolation(true);

        // Test new behavior
        setCompatOverride(BLOCK_NULL_ACTION_INTENTS, true);
        receiver.reset();
        switch (type) {
            case ACTIVITY -> assertThrows(
                    ActivityNotFoundException.class,
                    () -> mContext.startActivity(intent));
            case SERVICE -> mContext.startService(intent);
            case BROADCAST -> mContext.sendBroadcast(intent);
        }
        assertFalse(receiver.waitOnReceive());
        assertViolation(true);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_BLOCK_NULL_ACTION_INTENTS)
    public void testActivityNullAction() throws InterruptedException {
        final var intent = new Intent()
                .setPackage(RESOLUTION_TEST_PKG_NAME)
                .setData(Uri.parse("https://www.google.com"))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        testComponentNullActionMatch(intent, LaunchType.ACTIVITY);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_BLOCK_NULL_ACTION_INTENTS)
    public void testServiceNullAction() throws InterruptedException {
        final var intent = new Intent().setPackage(RESOLUTION_TEST_PKG_NAME);
        testComponentNullActionMatch(intent, LaunchType.SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_BLOCK_NULL_ACTION_INTENTS)
    public void testStaticBroadcastNullAction() throws InterruptedException {
        var intent = new Intent().setPackage(RESOLUTION_TEST_PKG_NAME);
        testComponentNullActionMatch(intent, LaunchType.BROADCAST);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_BLOCK_NULL_ACTION_INTENTS)
    public void testRuntimeBroadcastNullAction() throws InterruptedException {
        final var targetReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                var broadcast = new Intent(ACTION_RECEIVING_INTENT)
                        .setPackage(mContext.getPackageName());
                context.sendBroadcast(broadcast);
            }
        };
        final var filter = new IntentFilter("action");
        filter.addDataScheme("https");
        mContext.registerReceiver(targetReceiver, filter, RECEIVER_NOT_EXPORTED);
        mRegisteredReceiverList.add(targetReceiver);

        // Create an intent with null action
        final var intent = new Intent()
                .setPackage(mContext.getPackageName())
                .setData(Uri.parse("https://www.google.com"))
                .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);

        testComponentNullActionMatch(intent, LaunchType.BROADCAST);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_INTENT_MATCHING_FLAGS)
    public void testIntentFilterFlagsOverrideApplicationForActivity() {
        testComponentFlagsOverrideApplicationFlags(LaunchType.ACTIVITY);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_INTENT_MATCHING_FLAGS)
    public void testIntentFilterFlagsOverrideApplicationForService() {
        testComponentFlagsOverrideApplicationFlags(LaunchType.SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_INTENT_MATCHING_FLAGS)
    public void testIntentFilterFlagsOverrideApplicationForBroadcast() {
        testComponentFlagsOverrideApplicationFlags(LaunchType.BROADCAST);
    }

    private void testComponentFlagsOverrideApplicationFlags(LaunchType type) {
        final var emptyFlags = PackageManager.ResolveInfoFlags.of(0);

        /* test none flag override */

        List<ResolveInfo> results = null;

        Intent intent = new Intent(NON_EXISTENT_ACTION_NAME);
        switch (type) {
            case ACTIVITY -> {
                intent.setClassName(RESOLUTION_COMPONENT_OVERRIDE_TEST_PKG_NAME, ACTIVITY_NAME);
                results = mPackageManager.queryIntentActivities(intent, emptyFlags);
            }
            case SERVICE -> {
                intent.setClassName(RESOLUTION_COMPONENT_OVERRIDE_TEST_PKG_NAME, SERVICE_NAME);
                results = mPackageManager.queryIntentServices(intent, emptyFlags);
            }
            case BROADCAST -> {
                intent.setClassName(RESOLUTION_COMPONENT_OVERRIDE_TEST_PKG_NAME, RECEIVER_NAME);
                results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
            }
        }
        assertEquals(1, results.size());

        intent.setAction(null);
        switch (type) {
            case ACTIVITY -> results = mPackageManager.queryIntentActivities(intent, emptyFlags);
            case SERVICE -> results = mPackageManager.queryIntentServices(intent, emptyFlags);
            case BROADCAST -> results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        }
        assertEquals(1, results.size());

        /* test allowNullAction flag override (essentially results opt-out for all) */

        intent.setAction(NON_EXISTENT_ACTION_NAME);
        switch (type) {
            case ACTIVITY -> {
                intent.setClassName(RESOLUTION_COMPONENT_OVERRIDE_TEST_PKG_NAME,
                        ACTIVITY_THAT_ALLOWS_NULL_ACTION);
                results = mPackageManager.queryIntentActivities(intent, emptyFlags);
            }
            case SERVICE -> {
                intent.setClassName(RESOLUTION_COMPONENT_OVERRIDE_TEST_PKG_NAME, SERVICE_NAME_2);
                results = mPackageManager.queryIntentServices(intent, emptyFlags);
            }
            case BROADCAST -> {
                intent.setClassName(RESOLUTION_COMPONENT_OVERRIDE_TEST_PKG_NAME, RECEIVER_NAME_2);
                results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
            }
        }
        assertEquals(1, results.size());

        intent.setAction(null);
        switch (type) {
            case ACTIVITY -> results = mPackageManager.queryIntentActivities(intent, emptyFlags);
            case SERVICE -> results = mPackageManager.queryIntentServices(intent, emptyFlags);
            case BROADCAST -> results = mPackageManager.queryBroadcastReceivers(intent, emptyFlags);
        }
        assertEquals(1, results.size());
    }
}
