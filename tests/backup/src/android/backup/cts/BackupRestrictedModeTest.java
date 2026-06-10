/*
 * Copyright 2024 The Android Open Source Project
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

package android.backup.cts;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.Manifest;
import android.app.backup.BackupManager;
import android.app.backup.BackupManagerMonitor;
import android.app.backup.BackupObserver;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.backup.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;

@RunWith(AndroidJUnit4.class)
public class BackupRestrictedModeTest extends BaseBackupCtsTest {
    private static final String RESTRICTED_MODE_OPTED_OUT_APP =
            "android.cts.backup.restrictedmodeoptedoutapp";
    private static final String RESTRICTED_MODE_OPTED_IN_APP =
            "android.cts.backup.restrictedmodeoptedinapp";
    private static final String RESTRICTED_MODE_NO_ACTION_APP_1 =
            "android.cts.backup.restrictedmodenoactionapp1";
    private static final String RESTRICTED_MODE_NO_ACTION_APP_2 =
            "android.cts.backup.restrictedmodenoactionapp2";

    private static final String[] ALL_TEST_PACKAGES =
            new String[]{RESTRICTED_MODE_NO_ACTION_APP_1, RESTRICTED_MODE_NO_ACTION_APP_2,
                    RESTRICTED_MODE_OPTED_IN_APP, RESTRICTED_MODE_OPTED_OUT_APP};

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private BackupManager mBackupManager;
    private TestBackupManagerMonitor mBackupManagerMonitor;
    private TestBackupObserver mBackupObserver;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mBackupManager = new BackupManager(mInstrumentation.getTargetContext());
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.BACKUP);
        mBackupManagerMonitor = new TestBackupManagerMonitor();
        mBackupObserver = new TestBackupObserver();
        unStopAllTestPackages();
    }

    @After
    public void tearDown() throws Exception {
        // Delete any data that our test apps might have written to disk. Also force-stop the
        // apps to kill their process and reset their restricted mode state.
        for (String packageName : ALL_TEST_PACKAGES) {
            clearBackupDataInLocalTransport(packageName);
            forceStopPackage(packageName);
        }
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_RESTRICTED_MODE_CHANGES)
    public void testBackUp_respectsAppsOptingInOrOut() {
        if (!isBackupSupported()) {
            return;
        }

        mBackupManager.requestBackup(
                new String[]{RESTRICTED_MODE_OPTED_OUT_APP, RESTRICTED_MODE_OPTED_IN_APP},
                mBackupObserver, mBackupManagerMonitor,
                /* flags= */ 0);

        waitUntilBackupFinished();
        assertAppWasNotInRestrictedMode(RESTRICTED_MODE_OPTED_OUT_APP);
        assertAppWasInRestrictedMode(RESTRICTED_MODE_OPTED_IN_APP);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_RESTRICTED_MODE_CHANGES)
    public void testBackUp_respectsTransportDecisionForAppsTargeting36() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setTargetSdkGreaterOrEqual36(RESTRICTED_MODE_NO_ACTION_APP_1);
        setLocalTransportParameters(
                "no_restricted_mode_packages=" + RESTRICTED_MODE_NO_ACTION_APP_1 + ";"
                        + RESTRICTED_MODE_NO_ACTION_APP_2);

        mBackupManager.requestBackup(
                new String[]{RESTRICTED_MODE_NO_ACTION_APP_1, RESTRICTED_MODE_NO_ACTION_APP_2},
                mBackupObserver, mBackupManagerMonitor,
                /* flags= */ 0);

        waitUntilBackupFinished();
        // App1 had targetSDK >= 36 so the transport's decision to not put it in restricted mode
        // should be respected. App2 was <36 so it was put in restricted mode regardless.
        assertAppWasNotInRestrictedMode(RESTRICTED_MODE_NO_ACTION_APP_1);
        assertAppWasInRestrictedMode(RESTRICTED_MODE_NO_ACTION_APP_2);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_RESTRICTED_MODE_CHANGES)
    public void testBackUp_appOptInTakesPrecedenceOverTransportDecision() throws Exception {
        if (!isBackupSupported()) {
            return;
        }
        setTargetSdkGreaterOrEqual36(RESTRICTED_MODE_OPTED_IN_APP);
        setTargetSdkGreaterOrEqual36(RESTRICTED_MODE_NO_ACTION_APP_1);
        setLocalTransportParameters(
                "no_restricted_mode_packages=" + RESTRICTED_MODE_OPTED_IN_APP + ";"
                        + RESTRICTED_MODE_NO_ACTION_APP_1);

        mBackupManager.requestBackup(
                new String[]{RESTRICTED_MODE_NO_ACTION_APP_1, RESTRICTED_MODE_OPTED_IN_APP},
                mBackupObserver, mBackupManagerMonitor,
                /* flags= */ 0);

        waitUntilBackupFinished();
        // Even though the transport wanted this app to not be in restricted mode and its
        // targetSdk is >= 36, it opted in explicitly and that gets precedence.
        // If app has not explicitly opted in, the transport's decision is respected.
        assertAppWasNotInRestrictedMode(RESTRICTED_MODE_NO_ACTION_APP_1);
        assertAppWasInRestrictedMode(RESTRICTED_MODE_OPTED_IN_APP);
    }

    private void waitUntilBackupFinished() {
        long startTimeMillis = System.currentTimeMillis();

        // Wait max 10 seconds.
        while (System.currentTimeMillis() - startTimeMillis < 10_000) {
            if (mBackupObserver.isFinished) {
                return;
            }
        }

        fail("Timeout waiting for backup to finish.");
    }

    private void unStopAllTestPackages() throws Exception {
        // Newly installed packages are in the 'stopped' state and ineligible for backup.
        for (String pkg : ALL_TEST_PACKAGES) {
            String cmd = String.format("cmd package unstop --user %d %s",
                    mInstrumentation.getTargetContext().getUserId(), pkg);
            getBackupUtils().executeShellCommandSync(cmd);
        }
    }

    private void setTargetSdkGreaterOrEqual36(String packageName) throws Exception {
        // b/376661510 is the compat change id for OS_DECIDES_BACKUP_RESTRICTED_MODE, which is
        // enabled when targetSdk >= 36 (Baklava).
        String cmd = String.format("am compat enable %d %s", 376661510, packageName);
        getBackupUtils().executeShellCommandSync(cmd);
    }

    private void assertAppWasInRestrictedMode(String packageName) {
        // If the app is in restricted mode we expect it to crash in preflight.
        assertEquals(packageName + " should have crashed in preflight",
                BackupManagerMonitor.LOG_EVENT_ID_ERROR_PREFLIGHT,
                (int) mBackupManagerMonitor.receivedEventIdsPerPackage.get(packageName));

    }

    private void assertAppWasNotInRestrictedMode(String packageName) {
        // If the app is not in restricted mode it should succeed the backup.
        assertTrue(packageName + " should have succeeded",
                mBackupObserver.succeededPackages.contains(packageName));
    }

    private static class TestBackupManagerMonitor extends BackupManagerMonitor {
        public final HashMap<String, Integer> receivedEventIdsPerPackage = new HashMap<>();

        @Override
        public void onEvent(Bundle event) {
            receivedEventIdsPerPackage.put(
                    event.getString(BackupManagerMonitor.EXTRA_LOG_EVENT_PACKAGE_NAME),
                    event.getInt(BackupManagerMonitor.EXTRA_LOG_EVENT_ID));
        }
    }

    private static class TestBackupObserver extends BackupObserver {
        public boolean isFinished;
        public final ArrayList<String> succeededPackages = new ArrayList<>();

        @Override
        public void onResult(String currentBackupPackage, int status) {
            if (status == BackupManager.SUCCESS) {
                succeededPackages.add(currentBackupPackage);
            }
        }

        @Override
        public void backupFinished(int status) {
            isFinished = true;
        }
    }
}
