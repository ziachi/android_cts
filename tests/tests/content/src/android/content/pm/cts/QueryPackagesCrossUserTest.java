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

import static android.content.pm.Flags.FLAG_REMOVE_CROSS_USER_PERMISSION_HACK;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.RemoteCallback;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@AppModeFull
@AppModeNonSdkSandbox
@EnsureHasWorkProfile
@RunWith(AndroidJUnit4.class)
public class QueryPackagesCrossUserTest {
    private static final String TAG = "QueryPackagesCrossUserTest";
    private static final long DEFAULT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);
    private static final String EXTRA_REMOTE_CALLBACK = "extra_remote_callback";
    private static final String EXTRA_REMOTE_CALLBACK_RESULT = "extra_remote_callback_result";
    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    private static final String EMPTY_APP_APK_PATH = SAMPLE_APK_BASE + "CtsContentEmptyTestApp.apk";
    private static final String EMPTY_APP_PACKAGE_NAME = "android.content.cts.emptytestapp";
    private static final String QUERY_PACKAGES_TEST_APP_APK_PATH =
            SAMPLE_APK_BASE + "CtsQueryPackagesTestApp.apk";
    private static final String QUERY_PACKAGES_TEST_APP_PACKAGE_NAME =
            "android.content.cts.querypackagestestapp";
    private static final String QUERY_PACKAGES_TEST_APP_ACTIVITY =
            QUERY_PACKAGES_TEST_APP_PACKAGE_NAME + ".MainActivity";

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private UserReference mPrimaryUser;
    private UserReference mSecondaryUser;

    @Before
    public void setup() throws Exception {
        assumeTrue("Device is not supported", isDeviceSupported());
        mPrimaryUser = sDeviceState.initialUser();
        mSecondaryUser = workProfile(sDeviceState);
        assumeTrue(UserManager.supportsMultipleUsers());
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @After
    public void uninstall() {
        uninstallPackage(EMPTY_APP_PACKAGE_NAME);
        uninstallPackage(QUERY_PACKAGES_TEST_APP_PACKAGE_NAME);
    }

    @RequiresFlagsEnabled(FLAG_REMOVE_CROSS_USER_PERMISSION_HACK)
    @Test
    public void queryPackagesCrossUser_hasWorkProfile_withoutCrossUserPermission()
            throws Exception {
        Bundle bundle = queryPackagesCrossUserHasWorkProfileWithoutCrossUserPermission();

        assertNotNull(bundle);
        assertThat(bundle.getString(EXTRA_REMOTE_CALLBACK_RESULT, "")).isEqualTo("NOT FOUND");
    }

    @RequiresFlagsDisabled(FLAG_REMOVE_CROSS_USER_PERMISSION_HACK)
    @Test
    public void queryPackagesCrossUser_hasWorkProfile_withoutCrossUserPermission_useHackCode()
            throws Exception {
        Bundle bundle = queryPackagesCrossUserHasWorkProfileWithoutCrossUserPermission();

        assertNotNull(bundle);
        assertThat(bundle.getString(EXTRA_REMOTE_CALLBACK_RESULT, "")).isEqualTo(
                EMPTY_APP_PACKAGE_NAME);
    }

    private Bundle queryPackagesCrossUserHasWorkProfileWithoutCrossUserPermission()
            throws Exception {
        assertFalse(isAppInstalledForUser(QUERY_PACKAGES_TEST_APP_PACKAGE_NAME, mPrimaryUser));
        assertFalse(isAppInstalledForUser(EMPTY_APP_PACKAGE_NAME, mPrimaryUser));
        assertFalse(isAppInstalledForUser(QUERY_PACKAGES_TEST_APP_PACKAGE_NAME, mSecondaryUser));
        assertFalse(isAppInstalledForUser(EMPTY_APP_PACKAGE_NAME, mSecondaryUser));

        installPackageAsUser(EMPTY_APP_APK_PATH, mSecondaryUser);
        assertTrue(isAppInstalledForUser(EMPTY_APP_PACKAGE_NAME, mSecondaryUser));
        assertFalse(isAppInstalledForUser(EMPTY_APP_PACKAGE_NAME, mPrimaryUser));

        installPackageAsUser(QUERY_PACKAGES_TEST_APP_APK_PATH, mPrimaryUser);
        assertTrue(isAppInstalledForUser(QUERY_PACKAGES_TEST_APP_PACKAGE_NAME, mPrimaryUser));
        assertFalse(isAppInstalledForUser(QUERY_PACKAGES_TEST_APP_PACKAGE_NAME, mSecondaryUser));

        // Launch the query packages test app and check if the empty app is not installed in the
        // work profile.
        final Intent intent = new Intent(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setComponent(new ComponentName(QUERY_PACKAGES_TEST_APP_PACKAGE_NAME,
                        QUERY_PACKAGES_TEST_APP_ACTIVITY));
        final ConditionVariable latch = new ConditionVariable();
        final AtomicReference<Bundle> resultReference = new AtomicReference<>();
        final RemoteCallback callback = new RemoteCallback(
                result -> {
                    Log.d(TAG, "Get callback from activity : " + QUERY_PACKAGES_TEST_APP_ACTIVITY);
                    resultReference.set(result);
                    latch.open();
                });
        intent.putExtra(EXTRA_REMOTE_CALLBACK, callback);
        mContext.startActivity(intent);
        Log.d(TAG, "startActivity : " + intent);

        if (!latch.block(DEFAULT_TIMEOUT_MS)) {
            throw new TimeoutException(
                    "Latch timed out while awaiting a response from "
                            + QUERY_PACKAGES_TEST_APP_PACKAGE_NAME + " after " + DEFAULT_TIMEOUT_MS
                            + "ms");
        }

        return resultReference.get();
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand(
                String.format("pm uninstall %s", packageName));
    }

    private static void installPackageAsUser(String apkPath, UserReference user) {
        int userId = user.id();
        assertThat(SystemUtil.runShellCommand(
                String.format("pm install -t -g --user %s %s", userId, apkPath)))
                .isEqualTo(String.format("Success\n"));
    }

    private static boolean isAppInstalledForUser(String packageName, UserReference user) {
        return Arrays.stream(SystemUtil.runShellCommand(
                        String.format("pm list packages --user %s %s", user.id(), packageName))
                .split("\\r?\\n"))
                .anyMatch(pkg -> pkg.equals(String.format("package:%s", packageName)));
    }

    private static boolean isDeviceSupported() {
        return !FeatureUtil.isArc()
                && !FeatureUtil.isAutomotive()
                && !FeatureUtil.isTV()
                && !FeatureUtil.isWatch()
                && !FeatureUtil.isVrHeadset();
    }
}
