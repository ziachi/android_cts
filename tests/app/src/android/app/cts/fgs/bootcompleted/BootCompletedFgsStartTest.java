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

package android.app.cts.fgs.bootcompleted;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.app.stubs.BootCompletedFgs.ACTION_BOOT_COMPLETED_FGS_RESULT;
import static android.app.stubs.BootCompletedFgs.ACTION_FAKE_BOOT_COMPLETED;
import static android.app.stubs.BootCompletedFgs.EXTRA_FGS_TYPES;
import static android.app.stubs.BootCompletedFgs.RESULT_CODE_FAILURE;
import static android.app.stubs.BootCompletedFgs.RESULT_CODE_SUCCESS;
import static android.app.stubs.BootCompletedFgs.RESULT_CODE_UNKNOWN;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertEquals;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.cts.CtsAppTestUtils;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AmUtils;
import com.android.server.am.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class BootCompletedFgsStartTest {

    private Context mTargetContext;
    private Instrumentation mInstrumentation;

    private static final String TARGET_APP_CURRENT = "com.android.app1";
    private static final String TARGET_APP_34 = "com.android.app.api34";
    private static final String TARGET_APP_STUBS = "android.app.stubs";

    public static final String ALLOW_FGS_START_CMD =
            "am broadcast --user %d -a " + ACTION_FAKE_BOOT_COMPLETED
            + " --allow-fgs-start-reason 200"
            + " --include-stopped-packages"
            + " -p ";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private CountDownLatch mBroadcastLatch = new CountDownLatch(1);
    private int[] mResult = new int[1];

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mResult[0] = intent.getIntExtra(Intent.EXTRA_RETURN_RESULT, RESULT_CODE_UNKNOWN);
            mBroadcastLatch.countDown();
        }
    };

    private void enableFgsRestriction(boolean enable, String packageName)
            throws Exception {
        final String action = enable ? "enable" : "disable";
        CtsAppTestUtils.executeShellCmd(mInstrumentation, "am compat " + action
                + " --no-kill FGS_BOOT_COMPLETED_RESTRICTIONS " + packageName);
    }

    private void startFgsBootCompleted(String pkg, String extra) throws Exception {
        CtsAppTestUtils.executeShellCmd(mInstrumentation,
                String.format(ALLOW_FGS_START_CMD, mTargetContext.getUserId()) + pkg + extra);
    }

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mTargetContext = mInstrumentation.getTargetContext();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_BOOT_COMPLETED_FGS_RESULT);
        mTargetContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
        resetFgsRestrictionEnabled(mTargetContext.getPackageName());

        AmUtils.waitForBroadcastBarrier();
    }

    @After
    public void tearDown() throws Exception {
        mTargetContext.unregisterReceiver(mReceiver);
        final ActivityManager am = mTargetContext.getSystemService(ActivityManager.class);
        runWithShellPermissionIdentity(() -> {
            am.forceStopPackage(TARGET_APP_CURRENT);
            am.forceStopPackage(TARGET_APP_34);
        });
    }

    private void runTestOnce(String testPkg, boolean enableCompat, String fgsType,
            int expectedResult) throws Exception {
        mResult[0] = RESULT_CODE_UNKNOWN;
        enableFgsRestriction(enableCompat, testPkg);
        startFgsBootCompleted(testPkg, fgsType);

        mBroadcastLatch.await(10000, TimeUnit.MILLISECONDS);

        assertEquals(expectedResult, mResult[0]);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FGS_BOOT_COMPLETED)
    public void fgsTypeNotAllowedStartTest() throws Exception {
        runTestOnce(TARGET_APP_CURRENT, true, "", RESULT_CODE_FAILURE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FGS_BOOT_COMPLETED)
    public void fgsTypeAllowedStartTestApi34() throws Exception {
        runTestOnce(TARGET_APP_34, true, "", RESULT_CODE_SUCCESS);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_FGS_BOOT_COMPLETED)
    public void fgsTypeAllowedStartTest_changesDisabled() throws Exception {
        runTestOnce(TARGET_APP_CURRENT, false, "", RESULT_CODE_SUCCESS);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_FGS_BOOT_COMPLETED)
    public void fgsTypeAllowedStartTestApi34_changesDisabled() throws Exception {
        runTestOnce(TARGET_APP_34, false, "", RESULT_CODE_SUCCESS);
    }

    private static String fgsTypeToString(int types) {
        return " --ei " + EXTRA_FGS_TYPES + " " + types;
    }

    @Test
    public void fgsTypeAllowedStartTest() throws Exception {
        runTestOnce(TARGET_APP_CURRENT, true, fgsTypeToString(FOREGROUND_SERVICE_TYPE_SPECIAL_USE),
                RESULT_CODE_SUCCESS);
    }

    @Test
    public void fgsTypeLocationAllowedStartTest() throws Exception {
        try {
            mInstrumentation.getUiAutomation().grantRuntimePermission(TARGET_APP_CURRENT,
                    ACCESS_FINE_LOCATION);
            mInstrumentation.getUiAutomation().grantRuntimePermission(TARGET_APP_CURRENT,
                    ACCESS_BACKGROUND_LOCATION);
            // Sleep a second to make sure the permission change works.
            SystemClock.sleep(1000);
            runTestOnce(TARGET_APP_CURRENT, true, fgsTypeToString(FOREGROUND_SERVICE_TYPE_LOCATION),
                    RESULT_CODE_SUCCESS);
        } finally {
            mInstrumentation.getUiAutomation().revokeRuntimePermission(TARGET_APP_CURRENT,
                    ACCESS_BACKGROUND_LOCATION);
            mInstrumentation.getUiAutomation().revokeRuntimePermission(TARGET_APP_CURRENT,
                    ACCESS_FINE_LOCATION);
        }
    }

    @Test
    public void fgsTypeLocationAllowedStartTest_noPermission() throws Exception {
        try {
            mInstrumentation.getUiAutomation().revokeRuntimePermission(TARGET_APP_CURRENT,
                    ACCESS_BACKGROUND_LOCATION);
            mInstrumentation.getUiAutomation().grantRuntimePermission(TARGET_APP_CURRENT,
                    ACCESS_FINE_LOCATION);
            // Sleep a second to make sure the permission change works.
            SystemClock.sleep(1000);
            runTestOnce(TARGET_APP_CURRENT, true, fgsTypeToString(FOREGROUND_SERVICE_TYPE_LOCATION),
                    RESULT_CODE_FAILURE);
        } finally {
            mInstrumentation.getUiAutomation().revokeRuntimePermission(TARGET_APP_CURRENT,
                    ACCESS_FINE_LOCATION);
        }
    }

    @Test
    public void fgsTypeHealthAllowedStartTest() throws Exception {
        runTestOnce(TARGET_APP_CURRENT, true, fgsTypeToString(FOREGROUND_SERVICE_TYPE_HEALTH),
                RESULT_CODE_SUCCESS);
    }

    @Test
    public void fgsTypeRemoteMessagingAllowedStartTest() throws Exception {
        runTestOnce(TARGET_APP_CURRENT, true,
                fgsTypeToString(FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING), RESULT_CODE_SUCCESS);
    }

    @Test
    public void fgsTypeMixedAllowedStartTest() throws Exception {
        runTestOnce(TARGET_APP_CURRENT, true,
                fgsTypeToString(FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        | FOREGROUND_SERVICE_TYPE_SPECIAL_USE), RESULT_CODE_SUCCESS);
    }

    @Test
    public void fgsTypeWithOtherExemptionsAllowedStartTest() throws Exception {
        runTestOnce(TARGET_APP_STUBS, true, "", RESULT_CODE_SUCCESS);
    }

    private void resetFgsRestrictionEnabled(String packageName) {
        mInstrumentation.getUiAutomation().executeShellCommand(
                "am compat reset --no-kill FGS_BOOT_COMPLETED_RESTRICTIONS " + packageName);
    }
}
