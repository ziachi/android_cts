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
import static android.content.pm.PackageManager.MATCH_ARCHIVED_PACKAGES;

import static com.android.bedstead.enterprise.EnterpriseDeviceStateExtensionsKt.workProfile;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.cts.PackageManagerShellCommandInstallTest.PackageBroadcastReceiver;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@AppModeFull
@AppModeNonSdkSandbox
@EnsureHasWorkProfile
@RunWith(AndroidJUnit4.class)
@Ignore("Disable archive feature for multi-user b/379962833")
public class PackageInstallerArchiveMultiUserTest {
    private static final String PACKAGE_NAME = "android.content.cts.mocklauncherapp";
    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    private static final String APK_PATH = SAMPLE_APK_BASE + "CtsContentMockLauncherTestApp.apk";

    private static final int TIMEOUT_SECONDS = 5;
    private static final int TIMEOUT_MILLISECONDS = 10;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private Context mContext;
    private ArchiveIntentSender mArchiveIntentSender;
    private UserReference mPrimaryUser;
    private UserReference mSecondaryUser;

    private HandlerThread mBackgroundThread =
            new HandlerThread("PackageInstallerArchiveMultiUserTest");

    @Before
    public void setup() throws Exception {
        assumeTrue("Form factor is not supported", isFormFactorSupported());
        mPrimaryUser = sDeviceState.initialUser();
        mSecondaryUser = workProfile(sDeviceState);
        assumeTrue(UserManager.supportsMultipleUsers());
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mArchiveIntentSender = new ArchiveIntentSender();
    }

    @After
    public void uninstall() {
        uninstallPackage(PACKAGE_NAME);
    }

    @Test
    public void archiveApp_broadcast_multiuser() throws PackageManager.NameNotFoundException {
        if (!mBackgroundThread.isAlive()) {
            mBackgroundThread.start();
        }
        final Handler backgroundHandler = new Handler(mBackgroundThread.getLooper());
        installExistingPackageAsUser(mContext.getPackageName(), mSecondaryUser);
        installPackage(APK_PATH);
        assertTrue(isAppInstalledForUser(mContext.getPackageName(), mPrimaryUser));
        assertTrue(isAppInstalledForUser(mContext.getPackageName(), mSecondaryUser));
        assertTrue(isAppInstalledForUser(PACKAGE_NAME, mPrimaryUser));
        assertTrue(isAppInstalledForUser(PACKAGE_NAME, mSecondaryUser));

        final PackageBroadcastReceiver removedBroadcastReceiverForPrimaryUser =
                new PackageBroadcastReceiver(
                        PACKAGE_NAME, mPrimaryUser.id(), Intent.ACTION_PACKAGE_REMOVED);
        final PackageBroadcastReceiver removedBroadcastReceiverForSecondaryUser =
                new PackageBroadcastReceiver(
                        PACKAGE_NAME, mSecondaryUser.id(), Intent.ACTION_PACKAGE_REMOVED);
        final PackageBroadcastReceiver fullyRemovedBroadcastReceiverForPrimaryUser =
                new PackageBroadcastReceiver(
                        PACKAGE_NAME, mPrimaryUser.id(), Intent.ACTION_PACKAGE_FULLY_REMOVED);
        final PackageBroadcastReceiver fullyRemovedBroadcastReceiverForSecondaryUser =
                new PackageBroadcastReceiver(
                        PACKAGE_NAME, mSecondaryUser.id(), Intent.ACTION_PACKAGE_FULLY_REMOVED);
        final PackageBroadcastReceiver uidRemovedBroadcastReceiverForPrimaryUser =
                new PackageBroadcastReceiver(
                        PACKAGE_NAME, mPrimaryUser.id(), Intent.ACTION_UID_REMOVED);
        final PackageBroadcastReceiver uidRemovedBroadcastReceiverForSecondaryUser =
                new PackageBroadcastReceiver(
                        PACKAGE_NAME, mSecondaryUser.id(), Intent.ACTION_UID_REMOVED);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        intentFilter.addDataScheme("package");
        final IntentFilter intentFilterForUidRemoved = new IntentFilter(Intent.ACTION_UID_REMOVED);
        runWithShellPermissionIdentity(() -> {
            final Context contextPrimaryUser = mContext.createContextAsUser(
                    mPrimaryUser.userHandle(), 0);
            final Context contextSecondaryUser = mContext.createContextAsUser(
                    mSecondaryUser.userHandle(), 0);
            try {
                contextPrimaryUser.registerReceiver(removedBroadcastReceiverForPrimaryUser,
                        intentFilter, /* broadcastPermission= */ null, backgroundHandler,
                        RECEIVER_EXPORTED);
                contextPrimaryUser.registerReceiver(fullyRemovedBroadcastReceiverForPrimaryUser,
                        intentFilter, /* broadcastPermission= */ null, backgroundHandler,
                        RECEIVER_EXPORTED);
                contextPrimaryUser.registerReceiver(uidRemovedBroadcastReceiverForPrimaryUser,
                        intentFilterForUidRemoved, /* broadcastPermission= */ null,
                        backgroundHandler, RECEIVER_EXPORTED);
                contextSecondaryUser.registerReceiver(removedBroadcastReceiverForSecondaryUser,
                        intentFilter, /* broadcastPermission= */ null, backgroundHandler,
                        RECEIVER_EXPORTED);
                contextSecondaryUser.registerReceiver(fullyRemovedBroadcastReceiverForSecondaryUser,
                        intentFilter, /* broadcastPermission= */ null, backgroundHandler,
                        RECEIVER_EXPORTED);
                contextSecondaryUser.registerReceiver(uidRemovedBroadcastReceiverForSecondaryUser,
                        intentFilterForUidRemoved, /* broadcastPermission= */ null,
                        backgroundHandler, RECEIVER_EXPORTED);

                final int uidPrimaryUser = contextPrimaryUser.getPackageManager().getPackageUid(
                        PACKAGE_NAME, /* flags= */ 0);
                final int uidSecondaryUser = contextSecondaryUser.getPackageManager().getPackageUid(
                        PACKAGE_NAME, /* flags= */ 0);
                assertThat(uidPrimaryUser).isNotEqualTo(uidSecondaryUser);

                // Archive the app in the primary user, send the REMOVED broadcast but not the
                // FULLY_REMOVED and UID_REMOVED. Only the targeted user will get the broadcast
                contextPrimaryUser.getPackageManager().getPackageInstaller().requestArchive(
                        PACKAGE_NAME, new IntentSender((IIntentSender) mArchiveIntentSender));
                assertThat(mArchiveIntentSender.mPackage.get(TIMEOUT_SECONDS,
                        TimeUnit.SECONDS)).isEqualTo(PACKAGE_NAME);
                assertThat(mArchiveIntentSender.mStatus.get(TIMEOUT_MILLISECONDS,
                        TimeUnit.MILLISECONDS)).isEqualTo(PackageInstaller.STATUS_SUCCESS);
                removedBroadcastReceiverForPrimaryUser.assertBroadcastReceived();
                Intent result = removedBroadcastReceiverForPrimaryUser.getBroadcastResult();
                assertThat(result.getIntExtra(Intent.EXTRA_UID, 0))
                        .isEqualTo(uidPrimaryUser);
                fullyRemovedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived();
                uidRemovedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived();
                removedBroadcastReceiverForSecondaryUser.assertBroadcastNotReceived();
                fullyRemovedBroadcastReceiverForSecondaryUser.assertBroadcastNotReceived();
                uidRemovedBroadcastReceiverForSecondaryUser.assertBroadcastNotReceived();
                removedBroadcastReceiverForPrimaryUser.reset();

                // Can't call requestArchive on the secondary user directly, because the test case
                // is running on the primary user. There is a check in PackageArchiver#verifyCaller.
                // Use the shell command to archive the app on the secondary user.
                archivePackageWithShellCommand(PACKAGE_NAME, mSecondaryUser.id());

                // When Archive the app in the secondary user, send the REMOVED broadcast but not
                // the FULLY_REMOVED and UID_REMOVED. Only the targeted user will get the broadcast
                removedBroadcastReceiverForSecondaryUser.assertBroadcastReceived();
                result = removedBroadcastReceiverForSecondaryUser.getBroadcastResult();
                assertThat(result.getIntExtra(Intent.EXTRA_UID, 0))
                        .isEqualTo(uidSecondaryUser);
                removedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived();
                fullyRemovedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived();
                uidRemovedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived();
                fullyRemovedBroadcastReceiverForSecondaryUser.assertBroadcastNotReceived();
                uidRemovedBroadcastReceiverForSecondaryUser.assertBroadcastNotReceived();
            } finally {
                // Clean up
                contextPrimaryUser.unregisterReceiver(removedBroadcastReceiverForPrimaryUser);
                contextPrimaryUser.unregisterReceiver(fullyRemovedBroadcastReceiverForPrimaryUser);
                contextPrimaryUser.unregisterReceiver(uidRemovedBroadcastReceiverForPrimaryUser);
                contextSecondaryUser.unregisterReceiver(removedBroadcastReceiverForSecondaryUser);
                contextSecondaryUser.unregisterReceiver(
                        fullyRemovedBroadcastReceiverForSecondaryUser);
                contextSecondaryUser.unregisterReceiver(
                        uidRemovedBroadcastReceiverForSecondaryUser);
                mBackgroundThread.interrupt();
            }
        });
    }

    @Test
    public void archiveApp_onlyForOneUser_multiuser() throws PackageManager.NameNotFoundException {
        installExistingPackageAsUser(mContext.getPackageName(), mSecondaryUser);
        installPackage(APK_PATH);
        Context contextPrimaryUser = mContext.createContextAsUser(mPrimaryUser.userHandle(), 0);
        Context contextSecondaryUser = mContext.createContextAsUser(mSecondaryUser.userHandle(), 0);
        assertTrue(isAppInstalledForUser(PACKAGE_NAME, mPrimaryUser));
        assertTrue(isAppInstalledForUser(PACKAGE_NAME, mSecondaryUser));

        runWithShellPermissionIdentity(
                () -> {
                    contextPrimaryUser.getPackageManager().getPackageInstaller().requestArchive(
                            PACKAGE_NAME,
                            new IntentSender((IIntentSender) mArchiveIntentSender));
                    assertThat(mArchiveIntentSender.mPackage.get(TIMEOUT_SECONDS,
                            TimeUnit.SECONDS)).isEqualTo(
                            PACKAGE_NAME);
                    assertThat(
                            mArchiveIntentSender.mStatus.get(TIMEOUT_MILLISECONDS,
                                    TimeUnit.MILLISECONDS)).isEqualTo(
                            PackageInstaller.STATUS_SUCCESS);
                },
                Manifest.permission.DELETE_PACKAGES);

        assertTrue(Objects.requireNonNull(
                contextPrimaryUser.getPackageManager().getPackageInfo(PACKAGE_NAME,
                        PackageManager.PackageInfoFlags.of(
                                MATCH_ARCHIVED_PACKAGES)).applicationInfo).isArchived);
        assertFalse(Objects.requireNonNull(
                contextSecondaryUser.getPackageManager().getPackageInfo(PACKAGE_NAME,
                        PackageManager.PackageInfoFlags.of(
                                MATCH_ARCHIVED_PACKAGES)).applicationInfo).isArchived);
    }

    private static void archivePackageWithShellCommand(@NonNull String packageName, int userId) {
        final String command = String.format("pm archive --user %d %s", userId, packageName);
        assertThat(SystemUtil.runShellCommand(command)).isEqualTo("Success\n");
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand(
                String.format("pm uninstall %s", packageName));
    }

    private void installPackage(String path) {
        assertEquals("Success\n", SystemUtil.runShellCommand(
                String.format("pm install -r -i %s -t -g %s", mContext.getPackageName(),
                        path)));
    }

    private static void installExistingPackageAsUser(String packageName, UserReference user) {
        int userId = user.id();
        assertThat(SystemUtil.runShellCommand(
                String.format("pm install-existing --user %s %s", userId, packageName)))
                .isEqualTo(
                        String.format("Package %s installed for user: %s\n", packageName, userId));
    }

    private static boolean isAppInstalledForUser(String packageName, UserReference user) {
        return Arrays.stream(SystemUtil.runShellCommand(
                        String.format("pm list packages --user %s %s", user.id(), packageName))
                .split("\\r?\\n"))
                .anyMatch(pkg -> pkg.equals(String.format("package:%s", packageName)));
    }

    private static boolean isFormFactorSupported() {
        return !FeatureUtil.isArc()
                && !FeatureUtil.isAutomotive()
                && !FeatureUtil.isTV()
                && !FeatureUtil.isWatch()
                && !FeatureUtil.isVrHeadset();
    }

    static class ArchiveIntentSender extends IIntentSender.Stub {

        final CompletableFuture<String> mPackage = new CompletableFuture<>();
        final CompletableFuture<Integer> mStatus = new CompletableFuture<>();
        final CompletableFuture<String> mMessage = new CompletableFuture<>();
        final CompletableFuture<Intent> mExtraIntent = new CompletableFuture<>();

        @Override
        public void send(int code, Intent intent, String resolvedType,
                IBinder whitelistToken, IIntentReceiver finishedReceiver,
                String requiredPermission, Bundle options) throws RemoteException {
            mPackage.complete(intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME));
            mStatus.complete(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -100));
            mMessage.complete(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            mExtraIntent.complete(intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class));
        }
    }
}
