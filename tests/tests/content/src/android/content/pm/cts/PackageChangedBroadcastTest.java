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

import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.pm.Flags.FLAG_REDUCE_BROADCASTS_FOR_COMPONENT_STATE_CHANGES;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;
import static android.os.Process.myUserHandle;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeFull
@AppModeNonSdkSandbox
@RunWith(AndroidJUnit4.class)
public class PackageChangedBroadcastTest {
    private static final String TAG = "PackageChangedBroadcastTest";
    private static final long DEFAULT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);
    private static final long LONG_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final String EXTRA_REMOTE_CALLBACK = "extra_remote_callback";
    private static final String EXTRA_REMOTE_CALLBACK_RESULT = "extra_remote_callback_result";
    private static final String EXTRA_TEST_COMPONENT_NAME = "extra_test_component_name";
    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    private static final String PACKAGE_CHANGED_TEST_APP_APK_PATH =
            SAMPLE_APK_BASE + "CtsPackageChangedTestApp.apk";
    private static final String PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME =
            "android.content.cts.packagechangedtestapp";
    private static final String PACKAGE_CHANGED_TEST_MAIN_ACTIVITY =
            PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME + ".MainActivity";
    private static final String PACKAGE_CHANGED_TEST_APP_NON_EXPORTED_ACTIVITY =
            PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME + ".NonExportedActivity";
    private static final String PACKAGE_CHANGED_TEST_APP_EXPORTED_ACTIVITY =
            PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME + ".ExportedActivity";
    private static final String PACKAGE_CHANGED_SHARED_USER_ID_TEST_APP_APK_PATH =
            SAMPLE_APK_BASE + "CtsPackageChangedSharedUserIdTestApp.apk";
    private static final String PACKAGE_CHANGED_SHARED_USER_ID_TEST_APP_PACKAGE_NAME =
            "android.content.cts.packagechangedtestapp.shareduserid";
    private static final String PACKAGE_CHANGED_SHARED_USER_ID_TEST_MAIN_ACTIVITY =
            PACKAGE_CHANGED_SHARED_USER_ID_TEST_APP_PACKAGE_NAME + ".MainActivity";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private PackageManager mPackageManager;

    private int mUserId;

    @Before
    public void setup() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mPackageManager = mContext.getPackageManager();
        mUserId = myUserHandle().getIdentifier();

        assertFalse(isAppInstalledForUser(PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME, mUserId));
        assertFalse(isAppInstalledForUser(PACKAGE_CHANGED_SHARED_USER_ID_TEST_APP_PACKAGE_NAME,
                mUserId));

        installPackageAsUser(PACKAGE_CHANGED_TEST_APP_APK_PATH, mUserId);
        assertTrue(isAppInstalledForUser(PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME, mUserId));

        installPackageAsUser(PACKAGE_CHANGED_SHARED_USER_ID_TEST_APP_APK_PATH, mUserId);
        assertTrue(isAppInstalledForUser(PACKAGE_CHANGED_SHARED_USER_ID_TEST_APP_PACKAGE_NAME,
                mUserId));
    }

    @After
    public void uninstall() {
        uninstallPackage(PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME);
        assertThat(isAppInstalledForUser(PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME,
                mUserId)).isFalse();

        uninstallPackage(PACKAGE_CHANGED_SHARED_USER_ID_TEST_APP_PACKAGE_NAME);
        assertThat(isAppInstalledForUser(PACKAGE_CHANGED_SHARED_USER_ID_TEST_APP_PACKAGE_NAME,
                mUserId)).isFalse();
    }

    @Test
    public void changeNonExportedComponentState_applicationItself_shouldReceiveBroadcast()
            throws Exception {
        final Intent intent = new Intent(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setComponent(new ComponentName(PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME,
                        PACKAGE_CHANGED_TEST_MAIN_ACTIVITY));
        CompletableFuture<Bundle> future = new CompletableFuture<>();
        final RemoteCallback callback = new RemoteCallback(
                result -> {
                    Log.d(TAG,
                            "Get callback from activity : " + PACKAGE_CHANGED_TEST_MAIN_ACTIVITY);
                    future.complete(result);
                });
        intent.putExtra(EXTRA_REMOTE_CALLBACK, callback);
        intent.putExtra(EXTRA_TEST_COMPONENT_NAME,
                new ComponentName(PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME,
                        PACKAGE_CHANGED_TEST_APP_NON_EXPORTED_ACTIVITY));
        mContext.startActivity(intent);
        Log.d(TAG, "startActivity : " + intent);

        Bundle bundle = future.get(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull(bundle);
        assertThat(bundle.getString(EXTRA_REMOTE_CALLBACK_RESULT, "")).isEqualTo(
                "RECEIVE PACKAGE CHANGED BROADCAST");
    }

    @RequiresFlagsEnabled(FLAG_REDUCE_BROADCASTS_FOR_COMPONENT_STATE_CHANGES)
    @Test
    public void changeNonExportedComponent_otherApplication_shouldNotReceiveBroadcast()
            throws Exception {
        final ComponentName componentName = new ComponentName(PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME,
                PACKAGE_CHANGED_TEST_APP_NON_EXPORTED_ACTIVITY);
        testChangeComponentAndVerifyPackageChangedBroadcast(componentName,
                false /* receiveBroadcast */);
    }

    @RequiresFlagsDisabled(FLAG_REDUCE_BROADCASTS_FOR_COMPONENT_STATE_CHANGES)
    @Test
    public void changeNonExportedComponent_otherApplication_shouldReceiveBroadcast()
            throws Exception {
        final ComponentName componentName = new ComponentName(PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME,
                PACKAGE_CHANGED_TEST_APP_NON_EXPORTED_ACTIVITY);
        testChangeComponentAndVerifyPackageChangedBroadcast(componentName,
                true /* receiveBroadcast */);
    }

    @Test
    public void changeExportedComponent_otherApplication_shouldReceiveBroadcast() throws Exception {
        final ComponentName componentName = new ComponentName(PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME,
                PACKAGE_CHANGED_TEST_APP_EXPORTED_ACTIVITY);
        testChangeComponentAndVerifyPackageChangedBroadcast(componentName,
                true /* receiveBroadcast */);
    }

    @RequiresFlagsEnabled(FLAG_REDUCE_BROADCASTS_FOR_COMPONENT_STATE_CHANGES)
    @Test
    public void changeNonExportedComponentState_shareTheSameUid_shouldReceiveBroadcast()
            throws Exception {
        final Intent intent = new Intent(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setComponent(
                        new ComponentName(PACKAGE_CHANGED_SHARED_USER_ID_TEST_APP_PACKAGE_NAME,
                                PACKAGE_CHANGED_SHARED_USER_ID_TEST_MAIN_ACTIVITY));
        CompletableFuture<Bundle> future = new CompletableFuture<>();
        final RemoteCallback callback = new RemoteCallback(
                result -> {
                    Log.d(TAG, "Get callback from activity : "
                            + PACKAGE_CHANGED_SHARED_USER_ID_TEST_MAIN_ACTIVITY);
                    future.complete(result);
                });
        intent.putExtra(EXTRA_REMOTE_CALLBACK, callback);
        final ComponentName testComponentName = new ComponentName(
                PACKAGE_CHANGED_TEST_APP_PACKAGE_NAME,
                PACKAGE_CHANGED_TEST_APP_NON_EXPORTED_ACTIVITY);
        intent.putExtra(EXTRA_TEST_COMPONENT_NAME, testComponentName);

        mContext.startActivity(intent);
        Log.d(TAG, "startActivity : " + intent);

        Thread.sleep(1000);

        SystemUtil.runWithShellPermissionIdentity(() ->
                mPackageManager.setComponentEnabledSetting(testComponentName,
                        COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP));

        Bundle bundle = future.get(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull(bundle);
        assertThat(bundle.getString(EXTRA_REMOTE_CALLBACK_RESULT, "")).isEqualTo(
                "RECEIVE PACKAGE CHANGED BROADCAST");
    }

    private void testChangeComponentAndVerifyPackageChangedBroadcast(ComponentName componentName,
            boolean receiveBroadcast) throws Exception {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        final CountDownLatch latch = new CountDownLatch(1 /* count */);
        final BroadcastReceiver br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "onReceive action: " + intent.getAction());
                if (TextUtils.equals(intent.getAction(), Intent.ACTION_PACKAGE_CHANGED)) {
                    final Bundle bundle = intent.getExtras();
                    if (bundle != null && TextUtils.equals(
                            bundle.getString(Intent.EXTRA_CHANGED_COMPONENT_NAME),
                            componentName.getClassName())) {
                        Log.d(TAG, "onReceive call latch.countDown()");
                        latch.countDown();
                    }
                }
            }
        };
        mContext.registerReceiver(br, filter, RECEIVER_EXPORTED);

        try {
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mPackageManager.setComponentEnabledSetting(componentName,
                            COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP));
            assertThat(latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isEqualTo(
                    receiveBroadcast);
            assertEquals(COMPONENT_ENABLED_STATE_ENABLED,
                    mPackageManager.getComponentEnabledSetting(componentName));
        } finally {
            mContext.unregisterReceiver(br);
        }
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand(
                String.format("pm uninstall %s", packageName));
    }

    private static void installPackageAsUser(String apkPath, int userId) {
        assertThat(SystemUtil.runShellCommand(
                String.format("pm install -t -g --user %s %s", userId, apkPath)))
                .isEqualTo(String.format("Success\n"));
    }

    private static boolean isAppInstalledForUser(String packageName, int userId) {
        return Arrays.stream(SystemUtil.runShellCommand(
                                String.format("pm list packages --user %s %s", userId, packageName))
                        .split("\\r?\\n"))
                .anyMatch(pkg -> pkg.equals(String.format("package:%s", packageName)));
    }
}
