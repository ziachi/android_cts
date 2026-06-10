/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.app.role.RoleManager.ROLE_SYSTEM_DEPENDENCY_INSTALLER;
import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.pm.Checksum.TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256;
import static android.content.pm.Checksum.TYPE_WHOLE_MERKLE_ROOT_4K_SHA256;
import static android.content.pm.Flags.FLAG_SDK_DEPENDENCY_INSTALLER;
import static android.content.pm.Flags.FLAG_SDK_LIB_INDEPENDENCE;
import static android.content.pm.PackageInstaller.DATA_LOADER_TYPE_INCREMENTAL;
import static android.content.pm.PackageInstaller.DATA_LOADER_TYPE_NONE;
import static android.content.pm.PackageInstaller.DATA_LOADER_TYPE_STREAMING;
import static android.content.pm.PackageInstaller.EXTRA_DATA_LOADER_TYPE;
import static android.content.pm.PackageInstaller.EXTRA_SESSION_ID;
import static android.content.pm.PackageInstaller.LOCATION_DATA_APP;
import static android.content.pm.PackageManager.EXTRA_VERIFICATION_ID;
import static android.content.pm.PackageManager.EXTRA_VERIFICATION_ROOT_HASH;
import static android.content.pm.PackageManager.GET_SHARED_LIBRARY_FILES;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
import static android.content.pm.PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES;
import static android.content.pm.PackageManager.VERIFICATION_ALLOW;
import static android.content.pm.PackageManager.VERIFICATION_REJECT;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;

import android.Manifest;
import android.app.UiAutomation;
import android.app.role.RoleManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.ApkChecksum;
import android.content.pm.ApplicationInfo;
import android.content.pm.DataLoaderParams;
import android.content.pm.Flags;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.pm.cts.util.AbandonAllPackageSessionsRule;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.PackageUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.multiuser.annotations.EnsureCanAddUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UserHelper;
import com.android.internal.util.ConcurrentUtils;
import com.android.internal.util.HexDump;

import libcore.util.HexEncoding;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.CertificateEncodingException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
@AppModeFull
@AppModeNonSdkSandbox
public class PackageManagerShellCommandInstallTest {
    static final String TEST_APP_PACKAGE = "com.example.helloworld";
    static final String TEST_VERIFIER_PACKAGE = "com.example.helloverifier";
    static final String TEST_SUFFICIENT_VERIFIER_PACKAGE = "com.example.hellosufficient";

    private static final String TAG = PackageManagerShellCommandInstallTest.class.getSimpleName();
    private static final String CTS_PACKAGE_NAME = "android.content.cts";

    private static final String TEST_APK_PATH = "/data/local/tmp/cts/content/";
    static final String TEST_HW5 = "HelloWorld5.apk";
    static final String TEST_HW_SYSTEM_USER_ONLY = "HelloWorldSystemUserOnly.apk";
    private static final String TEST_HW5_SPLIT0 = "HelloWorld5_hdpi-v4.apk";
    private static final String TEST_HW5_SPLIT1 = "HelloWorld5_mdpi-v4.apk";
    private static final String TEST_HW5_SPLIT2 = "HelloWorld5_xhdpi-v4.apk";
    private static final String TEST_HW5_SPLIT3 = "HelloWorld5_xxhdpi-v4.apk";
    private static final String TEST_HW5_SPLIT4 = "HelloWorld5_xxxhdpi-v4.apk";
    private static final String TEST_HW7 = "HelloWorld7.apk";
    private static final String TEST_HW7_SPLIT0 = "HelloWorld7_hdpi-v4.apk";
    private static final String TEST_HW7_SPLIT1 = "HelloWorld7_mdpi-v4.apk";
    private static final String TEST_HW7_SPLIT2 = "HelloWorld7_xhdpi-v4.apk";
    private static final String TEST_HW7_SPLIT3 = "HelloWorld7_xxhdpi-v4.apk";
    private static final String TEST_HW7_SPLIT4 = "HelloWorld7_xxxhdpi-v4.apk";

    private static final String TEST_SDK1_PACKAGE = "com.test.sdk1_1";
    private static final String TEST_SDK1_MAJOR_VERSION2_PACKAGE = "com.test.sdk1_2";
    private static final String TEST_SDK2_PACKAGE = "com.test.sdk2_2";
    private static final String TEST_SDK3_PACKAGE = "com.test.sdk3_3";
    private static final String TEST_SDK_USER_PACKAGE = "com.test.sdk.user";

    private static final String TEST_SDK1_NAME = "com.test.sdk1";
    private static final String TEST_SDK2_NAME = "com.test.sdk2";
    private static final String TEST_SDK3_NAME = "com.test.sdk3";

    private static final String TEST_SDK1 = "HelloWorldSdk1.apk";
    private static final String TEST_SDK1_UPDATED = "HelloWorldSdk1Updated.apk";
    private static final String TEST_SDK1_MAJOR_VERSION2 = "HelloWorldSdk1MajorVersion2.apk";
    private static final String TEST_SDK1_DIFFERENT_SIGNER = "HelloWorldSdk1DifferentSigner.apk";

    private static final String TEST_SDK2 = "HelloWorldSdk2.apk";
    private static final String TEST_SDK2_UPDATED = "HelloWorldSdk2Updated.apk";

    private static final String TEST_USING_SDK1_OPTIONAL = "HelloWorldUsingSdk1Optional.apk";

    private static final String TEST_USING_SDK1_OPTIONAL_SDK2 =
            "HelloWorldUsingSdk1OptionalSdk2.apk";
    private static final String TEST_USING_SDK1 = "HelloWorldUsingSdk1.apk";
    private static final String TEST_USING_SDK1_AND_SDK2 = "HelloWorldUsingSdk1And2.apk";

    private static final String TEST_SDK3_USING_SDK1 = "HelloWorldSdk3UsingSdk1.apk";
    private static final String TEST_SDK3_USING_SDK1_AND_SDK2 = "HelloWorldSdk3UsingSdk1And2.apk";
    private static final String TEST_USING_SDK3 = "HelloWorldUsingSdk3.apk";

    private static final String TEST_SUFFICIENT = "HelloWorldWithSufficient.apk";

    private static final String TEST_SUFFICIENT_VERIFIER_REJECT =
            "HelloSufficientVerifierReject.apk";

    private static final String TEST_VERIFIER_ALLOW = "HelloVerifierAllow.apk";
    private static final String TEST_VERIFIER_REJECT = "HelloVerifierReject.apk";
    private static final String TEST_VERIFIER_DELAYED_REJECT = "HelloVerifierDelayedReject.apk";
    private static final String TEST_VERIFIER_DISABLED = "HelloVerifierDisabled.apk";

    private static final String TEST_INSTALLER_APP = "HelloInstallerApp.apk";
    private static final String TEST_INSTALLER_APP_UPDATED =
            "HelloInstallerAppUpdated.apk";

    private static final String TEST_INSTALLER_APP_ABSENT = "HelloInstallerAppAbsent.apk";
    private static final String TEST_INSTALLER_APP_ABSENT_UPDATED =
            "HelloInstallerAppAbsentUpdated.apk";

    private static final String PACKAGE_MIME_TYPE = "application/vnd.android.package-archive";

    static final long DEFAULT_STREAMING_VERIFICATION_TIMEOUT_MS = 3 * 1000;
    static final long VERIFICATION_BROADCAST_RECEIVED_TIMEOUT_MS = 10 * 1000;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public AbandonAllPackageSessionsRule mAbandonSessionsRule = new AbandonAllPackageSessionsRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Parameter
    public int mDataLoaderType;

    @Parameters
    public static Iterable<Object> initParameters() {
        return Arrays.asList(DATA_LOADER_TYPE_NONE, DATA_LOADER_TYPE_STREAMING,
                             DATA_LOADER_TYPE_INCREMENTAL);
    }

    private boolean mStreaming = false;
    private boolean mIncremental = false;
    private boolean mVerifierTimeoutTest = false;
    private String mInstall = "";
    private String mDisableDependencyInstall = "";
    private RoleManager mRoleManager;
    private String mPreviousDependencyInstallerRoleHolder;
    private UserHelper mUserHelper;

    private static long sStreamingVerificationTimeoutMs = DEFAULT_STREAMING_VERIFICATION_TIMEOUT_MS;

    private static PackageInstaller getPackageInstaller() {
        return getPackageManager().getPackageInstaller();
    }

    private static PackageManager getPackageManager() {
        return InstrumentationRegistry.getContext().getPackageManager();
    }

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    private static UiAutomation getUiAutomation() {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    /* package */ static String executeShellCommand(String command) throws IOException {
        final ParcelFileDescriptor stdout = getUiAutomation().executeShellCommand(command);
        try (InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            return readFullStream(inputStream);
        }
    }

    private static String executeShellCommand(String command, File input)
            throws IOException {
        return executeShellCommand(command, new File[]{input});
    }

    private static String executeShellCommand(String command, File[] inputs)
            throws IOException {
        final ParcelFileDescriptor[] pfds = getUiAutomation().executeShellCommandRw(command);
        ParcelFileDescriptor stdout = pfds[0];
        ParcelFileDescriptor stdin = pfds[1];
        try (FileOutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(
                stdin)) {
            for (File input : inputs) {
                try (FileInputStream inputStream = new FileInputStream(input)) {
                    writeFullStream(inputStream, outputStream, input.length());
                }
            }
        }
        try (InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            return readFullStream(inputStream);
        }
    }

    private static String readFullStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        writeFullStream(inputStream, result, -1);
        return result.toString("UTF-8");
    }

    private static void writeFullStream(InputStream inputStream, OutputStream outputStream,
            long expected)
            throws IOException {
        byte[] buffer = new byte[1024];
        long total = 0;
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
            total += length;
        }
        if (expected > 0) {
            assertEquals(expected, total);
        }
    }

    private static void writeFileToSession(PackageInstaller.Session session, String name,
            String apk) throws IOException {
        File file = new File(createApkPath(apk));
        try (OutputStream os = session.openWrite(name, 0, file.length());
             InputStream is = new FileInputStream(file)) {
            writeFullStream(is, os, file.length());
        }
    }

    @BeforeClass
    public static void onBeforeClass() throws Exception {
        try {
            sStreamingVerificationTimeoutMs = Long.parseUnsignedLong(
                    executeShellCommand("settings get global streaming_verifier_timeout"));
        } catch (NumberFormatException ignore) {
        }
    }

    @Before
    public void onBefore() throws Exception {
        // Check if Incremental is allowed and skip incremental tests otherwise.
        assumeFalse(mDataLoaderType == DATA_LOADER_TYPE_INCREMENTAL
                && !checkIncrementalDeliveryFeature());

        mStreaming = mDataLoaderType != DATA_LOADER_TYPE_NONE;
        mIncremental = mDataLoaderType == DATA_LOADER_TYPE_INCREMENTAL;
        // Don't repeat verifier timeout tests for non-Incremental installs.
        mVerifierTimeoutTest = !mStreaming || mIncremental;

        mInstall = mDataLoaderType == DATA_LOADER_TYPE_NONE ? " install " :
                mDataLoaderType == DATA_LOADER_TYPE_STREAMING ? " install-streaming " :
                        " install-incremental ";

        if (Flags.sdkDependencyInstaller()) {
            mDisableDependencyInstall += "--disable-auto-install-dependencies ";
        }

        mUserHelper = new UserHelper(getContext());

        uninstallPackageSilently(TEST_APP_PACKAGE);

        mRoleManager = (RoleManager) getContext().getSystemService(Context.ROLE_SERVICE);

        executeShellCommand("settings put global verifier_verify_adb_installs 0");
    }

    private void onBeforeSdkTests() throws Exception {
        assumeFalse(mStreaming);

        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);
        uninstallPackageSilently(TEST_SDK3_PACKAGE);
        uninstallPackageSilently(TEST_SDK2_PACKAGE);
        uninstallPackageSilently(TEST_SDK1_PACKAGE);
        uninstallPackageSilently(TEST_SDK1_MAJOR_VERSION2_PACKAGE);

        setSystemProperty("debug.pm.uses_sdk_library_default_cert_digest", "invalid");
        setSystemProperty("debug.pm.prune_unused_shared_libraries_delay", "invalid");

        getDefaultSharedPreferences().edit().clear().commit();
    }

    @AfterClass
    public static void onAfterClass() throws Exception {
        uninstallPackageSilently(TEST_APP_PACKAGE);

        uninstallPackageSilently(TEST_VERIFIER_PACKAGE);
        uninstallPackageSilently(TEST_SUFFICIENT_VERIFIER_PACKAGE);

        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);
        uninstallPackageSilently(TEST_SDK3_PACKAGE);
        uninstallPackageSilently(TEST_SDK2_PACKAGE);
        uninstallPackageSilently(TEST_SDK1_PACKAGE);
        uninstallPackageSilently(TEST_SDK1_MAJOR_VERSION2_PACKAGE);

        // Set the test override to invalid.
        setSystemProperty("debug.pm.uses_sdk_library_default_cert_digest", "invalid");
        setSystemProperty("debug.pm.prune_unused_shared_libraries_delay", "invalid");
        setSystemProperty("debug.pm.adb_verifier_override_packages", "invalid");
    }

    private boolean checkIncrementalDeliveryFeature() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        return context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INCREMENTAL_DELIVERY);
    }

    @Test
    public void testAppInstall() throws Exception {
        installPackage(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppInstallInvalidUser() throws Exception {
        File file = new File(createApkPath(TEST_HW5));
        // MAX_USER_ID = UserHandle.MAX_SECONDARY_USER_ID
        // ideally the test environment will not reach 999
        String result = executeShellCommand(
                "pm " + mInstall + " --user 999" + " -t -g " + file.getPath());
        assertThat(result).isEqualTo("Failure [user 999 doesn't exist]\n");
    }

    @Test
    public void testAppUnInstallInvalidUser() throws Exception {
        // MAX_USER_ID = UserHandle.MAX_SECONDARY_USER_ID
        // ideally the test environment will not reach 999
        String result = executeShellCommand("pm uninstall --user 999 " + TEST_APP_PACKAGE);
        assertThat(result).isEqualTo("Failure [user 999 doesn't exist]\n");
    }

    @Test
    public void testAppInstallErr() throws Exception {
        assumeTrue(mStreaming);
        File file = new File(createApkPath(TEST_HW5));
        String command = "pm " + mInstall + " -t -g " + file.getPath() + (new Random()).nextLong();
        String commandResult = executeShellCommand(command);
        assertEquals("Failure [failed to add file(s)]\n", commandResult);
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppInstallStdIn() throws Exception {
        installPackageStdIn(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppInstallStdInErr() throws Exception {
        File file = new File(createApkPath(TEST_HW5));
        String commandResult = executeShellCommand("pm " + mInstall + " -t -g -S " + file.length(),
                new File[]{});
        if (mIncremental) {
            assertTrue(commandResult, commandResult.startsWith("Failure ["));
        } else {
            assertTrue(commandResult,
                    commandResult.startsWith("Failure [INSTALL_PARSE_FAILED_NOT_APK"));
        }
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    @RequiresFlagsEnabled(android.content.pm.Flags.FLAG_GET_PACKAGE_STORAGE_STATS)
    public void testGetPackageStorageStats() throws Exception {
        installPackage(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));

        StorageStatsManager storageStatsManager =
            getContext().getSystemService(StorageStatsManager.class);
        StorageStats stats =
            storageStatsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT,
                TEST_APP_PACKAGE, android.os.Process.myUserHandle());

        String commandResult =
            executeShellCommand("pm get-package-storage-stats " + TEST_APP_PACKAGE);
        String[] lines = commandResult.split("\n");
        for (String line : lines) {
            String dataType = line.split(": ")[0];
            String value = line.split(": ")[1];
            switch (dataType) {
                case "code":
                    assertEquals(value, getDataSizeDisplay(stats.getAppBytes()));
                    break;
                case "data":
                    assertEquals(value, getDataSizeDisplay(stats.getDataBytes()));
                    break;
                case "cache":
                    assertEquals(value, getDataSizeDisplay(stats.getCacheBytes()));
                    break;
                case "apk":
                    assertEquals(value, getDataSizeDisplay(stats.getAppBytesByDataType(
                        StorageStats.APP_DATA_TYPE_FILE_TYPE_APK)));
                    break;
                case "lib":
                    assertEquals(value, getDataSizeDisplay(stats.getAppBytesByDataType(
                        StorageStats.APP_DATA_TYPE_LIB)));
                    break;
                case "dm":
                    assertEquals(value, getDataSizeDisplay(stats.getAppBytesByDataType(
                        StorageStats.APP_DATA_TYPE_FILE_TYPE_DM)));
                    break;
                case "dexopt artifacts":
                    assertEquals(value, getDataSizeDisplay(stats.getAppBytesByDataType(
                        StorageStats.APP_DATA_TYPE_FILE_TYPE_DEXOPT_ARTIFACT)));
                    break;
                case "current profile":
                    assertEquals(value, getDataSizeDisplay(stats.getAppBytesByDataType(
                        StorageStats.APP_DATA_TYPE_FILE_TYPE_CURRENT_PROFILE)));
                    break;
                case "reference profile":
                    assertEquals(value, getDataSizeDisplay(stats.getAppBytesByDataType(
                        StorageStats.APP_DATA_TYPE_FILE_TYPE_REFERENCE_PROFILE)));
                    break;
                case "external cache":
                    assertEquals(value, getDataSizeDisplay(stats.getExternalCacheBytes()));
                    break;
            }
        }
    }

    @Test
    public void testAppUpdate() throws Exception {
        installPackage(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        updatePackage(TEST_APP_PACKAGE, TEST_HW7);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppUpdateSameApk() throws Exception {
        installPackage(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        updatePackage(TEST_APP_PACKAGE, TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppUpdateStdIn() throws Exception {
        installPackageStdIn(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        updatePackageStdIn(TEST_APP_PACKAGE, TEST_HW7);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppUpdateStdInSameApk() throws Exception {
        installPackageStdIn(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        updatePackageStdIn(TEST_APP_PACKAGE, TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppUpdateSkipEnable() throws Exception {
        assumeFalse(mStreaming);
        installPackage(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                getPackageManager().getApplicationEnabledSetting(TEST_APP_PACKAGE));
        disablePackage(TEST_APP_PACKAGE);
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                getPackageManager().getApplicationEnabledSetting(TEST_APP_PACKAGE));
        updatePackage(TEST_APP_PACKAGE, TEST_HW5);
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                getPackageManager().getApplicationEnabledSetting(TEST_APP_PACKAGE));
        disablePackage(TEST_APP_PACKAGE);
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                getPackageManager().getApplicationEnabledSetting(TEST_APP_PACKAGE));
        updatePackageSkipEnable(TEST_APP_PACKAGE, TEST_HW5);
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                getPackageManager().getApplicationEnabledSetting(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsInstall() throws Exception {
        assumeFalse(mStreaming); // Tested in testSplitsBatchInstall.
        installSplits(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsInstallStdIn() throws Exception {
        installSplitsStdIn(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4}, "");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsInstallDash() throws Exception {
        installSplitsStdIn(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4}, "-");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsBatchInstall() throws Exception {
        installSplitsBatch(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsUpdate() throws Exception {
        assumeFalse(mStreaming); // Tested in testSplitsBatchUpdate.
        installSplits(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        updateSplits(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }


    @Test
    public void testSplitsAdd() throws Exception {
        assumeFalse(mStreaming); // Tested in testSplitsBatchAdd.
        installSplits(new String[]{TEST_HW5});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base", getSplits(TEST_APP_PACKAGE));

        updateSplits(new String[]{TEST_HW5_SPLIT0});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi", getSplits(TEST_APP_PACKAGE));

        updateSplits(new String[]{TEST_HW5_SPLIT1});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi", getSplits(TEST_APP_PACKAGE));

        updateSplits(new String[]{TEST_HW5_SPLIT2});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi",
                getSplits(TEST_APP_PACKAGE));

        updateSplits(new String[]{TEST_HW5_SPLIT3});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi",
                getSplits(TEST_APP_PACKAGE));

        updateSplits(new String[]{TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsUpdateStdIn() throws Exception {
        installSplitsStdIn(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4}, "");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        installSplitsStdIn(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4}, "");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsUpdateDash() throws Exception {
        installSplitsStdIn(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4}, "-");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        installSplitsStdIn(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4}, "-");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsBatchUpdate() throws Exception {
        installSplitsBatch(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        updateSplitsBatch(
                new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                        TEST_HW7_SPLIT3, TEST_HW7_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsBatchAdd() throws Exception {
        installSplitsBatch(new String[]{TEST_HW5});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base", getSplits(TEST_APP_PACKAGE));

        updateSplitsBatch(new String[]{TEST_HW5_SPLIT0, TEST_HW5_SPLIT1});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi", getSplits(TEST_APP_PACKAGE));

        updateSplitsBatch(new String[]{TEST_HW5_SPLIT2, TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsUninstall() throws Exception {
        assumeFalse(mStreaming); // Tested in testSplitsBatchUninstall.
        installSplits(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        uninstallSplits(TEST_APP_PACKAGE, new String[]{"config.hdpi"});
        assertEquals("base, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        uninstallSplits(TEST_APP_PACKAGE, new String[]{"config.xxxhdpi", "config.xhdpi"});
        assertEquals("base, config.mdpi, config.xxhdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsBatchUninstall() throws Exception {
        installSplitsBatch(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        uninstallSplitsBatch(TEST_APP_PACKAGE, new String[]{"config.hdpi"});
        assertEquals("base, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        uninstallSplitsBatch(TEST_APP_PACKAGE, new String[]{"config.xxxhdpi", "config.xhdpi"});
        assertEquals("base, config.mdpi, config.xxhdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsRemove() throws Exception {
        assumeFalse(mStreaming); // Tested in testSplitsBatchRemove.
        installSplits(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));

        String sessionId = createUpdateSession(TEST_APP_PACKAGE);
        removeSplits(sessionId, new String[]{"config.hdpi"});
        commitSession(sessionId);
        assertEquals("base, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));

        sessionId = createUpdateSession(TEST_APP_PACKAGE);
        removeSplits(sessionId, new String[]{"config.xxxhdpi", "config.xhdpi"});
        commitSession(sessionId);
        assertEquals("base, config.mdpi, config.xxhdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsBatchRemove() throws Exception {
        installSplitsBatch(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));

        String sessionId = createUpdateSession(TEST_APP_PACKAGE);
        removeSplitsBatch(sessionId, new String[]{"config.hdpi"});
        commitSession(sessionId);
        assertEquals("base, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));

        sessionId = createUpdateSession(TEST_APP_PACKAGE);
        removeSplitsBatch(sessionId, new String[]{"config.xxxhdpi", "config.xhdpi"});
        commitSession(sessionId);
        assertEquals("base, config.mdpi, config.xxhdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppInstallErrDuplicate() throws Exception {
        assumeTrue(mStreaming);
        String split = createApkPath(TEST_HW5);
        String commandResult = executeShellCommand(
                "pm " + mInstall + " -t -g " + split + " " + split);
        assertEquals("Failure [failed to add file(s)]\n", commandResult);
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testDontKillWithSplit() throws Exception {
        assumeFalse(mStreaming);
        installPackage(TEST_HW5);
        installDontKillSplit();
    }

    private void installDontKillSplit() throws Exception {
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_INHERIT_EXISTING);
            params.installFlags |= PackageManager.INSTALL_REPLACE_EXISTING;
            params.setAppPackageName(TEST_APP_PACKAGE);
            params.setDontKillApp(true);

            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            assertTrue((session.getInstallFlags() & PackageManager.INSTALL_DONT_KILL_APP) != 0);

            writeFileToSession(session, "hw5_split0", TEST_HW5_SPLIT0);

            final CompletableFuture<Boolean> result = new CompletableFuture<>();
            final CompletableFuture<Integer> status = new CompletableFuture<>();
            final CompletableFuture<String> statusMessage = new CompletableFuture<>();
            session.commit(new IntentSender((IIntentSender) new IIntentSender.Stub() {
                @Override
                public void send(int code, Intent intent, String resolvedType,
                                 IBinder whitelistToken, IIntentReceiver finishedReceiver,
                                 String requiredPermission, Bundle options) throws RemoteException {
                    boolean dontKillApp =
                            (session.getInstallFlags() & PackageManager.INSTALL_DONT_KILL_APP) != 0;
                    status.complete(
                            intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE));
                    statusMessage.complete(
                            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                    result.complete(dontKillApp);
                }
            }));

            // We are adding split. OK to have the flag.
            assertTrue(result.get());
            // Verify that the return status is set
            assertEquals(statusMessage.get(), PackageInstaller.STATUS_SUCCESS, (int) status.get());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_IMPROVE_INSTALL_DONT_KILL)
    @Test
    public void testDontKillOldPathsArePreserved() throws Exception {
        assumeFalse(mStreaming);
        installPackage(TEST_HW5);
        String oldPath =
                getPackageManager().getApplicationInfo(TEST_APP_PACKAGE, 0).publicSourceDir;
        installDontKillSplit();

        List<String> oldPaths = getOldCodePaths(TEST_APP_PACKAGE);
        assertNotNull(oldPaths);
        assertEquals(1, oldPaths.size());
        // publicSourceDir contains the full path to the APK
        // oldPaths only contains paths to directory
        assertTrue(oldPath.startsWith(oldPaths.get(0)));
    }

    @Test
    public void testDontKillRemovedWithBaseApkFullInstall() throws Exception {
        assumeFalse(mStreaming);
        installPackage(TEST_HW5);

        getUiAutomation().adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(TEST_APP_PACKAGE);
            params.setDontKillApp(true);

            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            assertTrue((session.getInstallFlags() & PackageManager.INSTALL_DONT_KILL_APP) != 0);

            writeFileToSession(session, "hw7", TEST_HW7);

            final CompletableFuture<Boolean> result = new CompletableFuture<>();
            session.commit(new IntentSender((IIntentSender) new IIntentSender.Stub() {
                @Override
                public void send(int code, Intent intent, String resolvedType,
                        IBinder whitelistToken, IIntentReceiver finishedReceiver,
                        String requiredPermission, Bundle options) throws RemoteException {
                    boolean dontKillApp =
                            (session.getInstallFlags() & PackageManager.INSTALL_DONT_KILL_APP) != 0;
                    result.complete(dontKillApp);
                }
            }));

            // We are updating base.apk. Flag to be removed.
            assertFalse(result.get());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testDontKillRemovedWithBaseApk() throws Exception {
        assumeFalse(mStreaming);
        installPackage(TEST_HW5);

        getUiAutomation().adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_INHERIT_EXISTING);
            params.setAppPackageName(TEST_APP_PACKAGE);
            params.setDontKillApp(true);

            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            assertTrue((session.getInstallFlags() & PackageManager.INSTALL_DONT_KILL_APP) != 0);

            writeFileToSession(session, "hw7", TEST_HW7);

            final CompletableFuture<Boolean> result = new CompletableFuture<>();
            session.commit(new IntentSender((IIntentSender) new IIntentSender.Stub() {
                @Override
                public void send(int code, Intent intent, String resolvedType,
                        IBinder whitelistToken, IIntentReceiver finishedReceiver,
                        String requiredPermission, Bundle options) throws RemoteException {
                    boolean dontKillApp =
                            (session.getInstallFlags() & PackageManager.INSTALL_DONT_KILL_APP) != 0;
                    result.complete(dontKillApp);
                }
            }));

            // We are updating base.apk. Flag to be removed.
            assertFalse(result.get());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testDontKillRemovedWithRemovedSplit() throws Exception {
        assumeFalse(mStreaming);
        installSplitsBatch(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi", getSplits(TEST_APP_PACKAGE));

        getUiAutomation().adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_INHERIT_EXISTING);
            params.setAppPackageName(TEST_APP_PACKAGE);
            params.setDontKillApp(true);

            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            assertTrue((session.getInstallFlags() & PackageManager.INSTALL_DONT_KILL_APP) != 0);
            session.removeSplit("config.mdpi");

            final CompletableFuture<Boolean> result = new CompletableFuture<>();
            session.commit(new IntentSender((IIntentSender) new IIntentSender.Stub() {
                @Override
                public void send(int code, Intent intent, String resolvedType,
                                 IBinder whitelistToken, IIntentReceiver finishedReceiver,
                                 String requiredPermission, Bundle options) throws RemoteException {
                    boolean dontKillApp =
                            (session.getInstallFlags() & PackageManager.INSTALL_DONT_KILL_APP) != 0;
                    result.complete(dontKillApp);
                }
            }));

            // We are removing a split apk. Flag should be removed.
            assertFalse(result.get());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testDontKillRemovedWithReplacedSplit() throws Exception {
        assumeFalse(mStreaming);
        installSplitsBatch(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi", getSplits(TEST_APP_PACKAGE));

        getUiAutomation().adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_INHERIT_EXISTING);
            params.setAppPackageName(TEST_APP_PACKAGE);
            params.setDontKillApp(true);

            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            assertTrue((session.getInstallFlags() & PackageManager.INSTALL_DONT_KILL_APP) != 0);

            writeFileToSession(session, "split_config.mdpi", TEST_HW7_SPLIT1);

            final CompletableFuture<Boolean> result = new CompletableFuture<>();
            session.commit(new IntentSender((IIntentSender) new IIntentSender.Stub() {
                @Override
                public void send(int code, Intent intent, String resolvedType,
                                 IBinder whitelistToken, IIntentReceiver finishedReceiver,
                                 String requiredPermission, Bundle options) throws RemoteException {
                    boolean dontKillApp =
                            (session.getInstallFlags() & PackageManager.INSTALL_DONT_KILL_APP) != 0;
                    result.complete(dontKillApp);
                }
            }));

            // We are replacing an existing split apk. Flag should be removed.
            assertFalse(result.get());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testDataLoaderParamsApiV1() throws Exception {
        assumeTrue(mStreaming);

        getUiAutomation().adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();

            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);

            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);

            assertEquals(null, session.getDataLoaderParams());

            installer.abandonSession(sessionId);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testDataLoaderParamsApiV2() throws Exception {
        assumeTrue(mStreaming);

        getUiAutomation().adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();

            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            final ComponentName componentName = new ComponentName("foo", "bar");
            final String args = "args";
            params.setDataLoaderParams(
                    mIncremental ? DataLoaderParams.forIncremental(componentName, args)
                            : DataLoaderParams.forStreaming(componentName, args));

            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);

            DataLoaderParams dataLoaderParams = session.getDataLoaderParams();
            assertEquals(mIncremental ? DATA_LOADER_TYPE_INCREMENTAL : DATA_LOADER_TYPE_STREAMING,
                    dataLoaderParams.getType());
            assertEquals("foo", dataLoaderParams.getComponentName().getPackageName());
            assertEquals("bar", dataLoaderParams.getComponentName().getClassName());
            assertEquals("args", dataLoaderParams.getArguments());

            installer.abandonSession(sessionId);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testRemoveFileApiV2() throws Exception {
        assumeTrue(mStreaming);

        getUiAutomation().adoptShellPermissionIdentity();
        try {
            final PackageInstaller installer = getPackageInstaller();

            final SessionParams params = new SessionParams(SessionParams.MODE_INHERIT_EXISTING);
            params.setAppPackageName("com.package.name");
            final ComponentName componentName = new ComponentName("foo", "bar");
            final String args = "args";
            params.setDataLoaderParams(
                    mIncremental ? DataLoaderParams.forIncremental(componentName, args)
                            : DataLoaderParams.forStreaming(componentName, args));

            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);

            session.addFile(LOCATION_DATA_APP, "base.apk", 123, "123".getBytes(), null);
            String[] files = session.getNames();
            assertEquals(1, files.length);
            assertEquals("base.apk", files[0]);

            session.removeFile(LOCATION_DATA_APP, "base.apk");
            files = session.getNames();
            assertEquals(2, files.length);
            assertEquals("base.apk", files[0]);
            assertEquals("base.apk.removed", files[1]);

            installer.abandonSession(sessionId);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_DISALLOW_SDK_LIBS_TO_BE_APPS)
    public void testSdkInstallAndUpdate_sdkLibsBeAppsHasAppId() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        SystemUtil.runWithShellPermissionIdentity(() -> {
            // No uid is assigned for SDK package
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(TEST_SDK1_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(
                            MATCH_STATIC_SHARED_AND_SDK_LIBRARIES));
            assertThat(appInfo.uid).isGreaterThan(Process.INVALID_UID);
        });

        // Same APK.
        installPackage(TEST_SDK1);

        // Updated APK.
        installPackage(TEST_SDK1_UPDATED);

        // Reverted APK.
        installPackage(TEST_SDK1);

        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISALLOW_SDK_LIBS_TO_BE_APPS)
    public void testSdkInstallAndUpdate_blockSdkLibsBeAppsNoAppId() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        SystemUtil.runWithShellPermissionIdentity(() -> {
            // No uid is assigned for SDK package
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(TEST_SDK1_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(
                            MATCH_STATIC_SHARED_AND_SDK_LIBRARIES));
            assertThat(appInfo.uid).isEqualTo(Process.INVALID_UID);
        });

        // Same APK.
        installPackage(TEST_SDK1);

        // Updated APK.
        installPackage(TEST_SDK1_UPDATED);

        // Reverted APK.
        installPackage(TEST_SDK1);

        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
    }

    @Test
    public void testGetPackageInfoForSdk_notSystemOrShell() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Normal access
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> getPackageManager().getPackageInfo(TEST_SDK1_PACKAGE,
                        PackageManager.PackageInfoFlags.of(MATCH_STATIC_SHARED_AND_SDK_LIBRARIES)));
    }

    @Test
    public void testGetApplicationInfoForSdk_notSystemOrShell() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Normal access
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> getPackageManager().getApplicationInfo(TEST_SDK1_PACKAGE,
                        PackageManager.ApplicationInfoFlags.of(
                                MATCH_STATIC_SHARED_AND_SDK_LIBRARIES)));
    }

    @Test
    public void testSharedLibraryAccess_notSystemOrShell() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Normal access
        List<SharedLibraryInfo> shareLibs = getPackageManager().getSharedLibraries(/*flags=*/ 0);
        SharedLibraryInfo sdk = findLibrary(shareLibs, TEST_SDK1_NAME, 1);
        assertThat(sdk).isNull();
    }

    @Test
    public void testGetPackageInfoForSdk_systemOrShell() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Access as a shell
        SystemUtil.runWithShellPermissionIdentity(() -> {
            PackageInfo info = getPackageManager().getPackageInfo(TEST_SDK1_PACKAGE,
                    PackageManager.PackageInfoFlags.of(MATCH_STATIC_SHARED_AND_SDK_LIBRARIES));

            assertThat(info).isNotNull();
            assertThat(info.packageName).isEqualTo(TEST_SDK1_PACKAGE);
        });
    }

    @Test
    public void testGetApplicationInfoForSdk_systemOrShell() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Access as a shell
        SystemUtil.runWithShellPermissionIdentity(() -> {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(TEST_SDK1_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(
                            MATCH_STATIC_SHARED_AND_SDK_LIBRARIES));

            assertThat(appInfo).isNotNull();
            assertThat(appInfo.icon).isGreaterThan(0);

            assertThat(appInfo.targetSdkVersion).isEqualTo(34);
            assertThat(appInfo.minSdkVersion).isEqualTo(30);
        });
    }

    @Test
    public void testSharedLibraryAccess_systemOrShell() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Access as a shell
        SystemUtil.runWithShellPermissionIdentity(() -> {
            List<SharedLibraryInfo> shareLibs = getPackageManager().getSharedLibraries(/*flags=*/
                    0);
            SharedLibraryInfo sdk = findLibrary(shareLibs, TEST_SDK1_NAME, 1);
            assertThat(sdk).isNotNull();
        });
    }

    @Test
    public void testGetProperty() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        PackageManager.Property property =
                getPackageManager().getProperty("com.test.sdk1_1.TEST_PROPERTY",
                        TEST_SDK1_PACKAGE);
        assertThat(property).isNotNull();
        assertThat(property.getName()).isEqualTo("com.test.sdk1_1.TEST_PROPERTY");
        assertThat(property.getString()).isEqualTo("com.test.sdk1_1.testp1");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISALLOW_SDK_LIBS_TO_BE_APPS)
    public void testSdkBlockSdkLibsBeAppsNoComponentRegistered() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Check Activity
        Intent intent1 = new Intent();
        intent1.setClassName(TEST_SDK1_PACKAGE, TEST_SDK1_PACKAGE + ".MainActivity");
        List<ResolveInfo> resolveInfoList1 =
                getPackageManager().queryIntentActivities(intent1, 0);

        assertThat(resolveInfoList1).isEmpty();

        // Check Service
        Intent intent2 = new Intent();
        intent2.setClassName(TEST_SDK1_PACKAGE, TEST_SDK1_PACKAGE + ".FakeService");
        List<ResolveInfo> resolveInfoList2 =
                getPackageManager().queryIntentServices(intent2, 0);

        assertThat(resolveInfoList2).isEmpty();

        // Check Receiver
        Intent intent3 = new Intent();
        intent3.setClassName(TEST_SDK1_PACKAGE, TEST_SDK1_PACKAGE + ".FakeReceiver");
        List<ResolveInfo> resolveInfoList3 =
                getPackageManager().queryBroadcastReceivers(intent3, 0);

        assertThat(resolveInfoList3).isEmpty();

        // Check Provider
        Intent intent4 = new Intent();
        intent4.setClassName(TEST_SDK1_PACKAGE, TEST_SDK1_PACKAGE + ".FakeProvider");
        List<ResolveInfo> resolveInfoList4 =
                getPackageManager().queryIntentContentProviders(intent4, 0);

        assertThat(resolveInfoList4).isEmpty();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_DISALLOW_SDK_LIBS_TO_BE_APPS)
    public void testSdkLibsBeAppsComponentRegistered() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Check Activity
        Intent intent1 = new Intent();
        intent1.setClassName(TEST_SDK1_PACKAGE, TEST_SDK1_PACKAGE + ".MainActivity");
        List<ResolveInfo> resolveInfoList1 =
                getPackageManager().queryIntentActivities(intent1, 0);

        assertThat(resolveInfoList1).isNotEmpty();
        assertThat(resolveInfoList1.size()).isEqualTo(1);

        // Check Service
        Intent intent2 = new Intent();
        intent2.setClassName(TEST_SDK1_PACKAGE, TEST_SDK1_PACKAGE + ".FakeService");
        List<ResolveInfo> resolveInfoList2 =
                getPackageManager().queryIntentServices(intent2, 0);

        assertThat(resolveInfoList2).isNotEmpty();
        assertThat(resolveInfoList2.size()).isEqualTo(1);

        // Check Receiver
        Intent intent3 = new Intent();
        intent3.setClassName(TEST_SDK1_PACKAGE, TEST_SDK1_PACKAGE + ".FakeReceiver");
        List<ResolveInfo> resolveInfoList3 =
                getPackageManager().queryBroadcastReceivers(intent3, 0);

        assertThat(resolveInfoList3).isNotEmpty();
        assertThat(resolveInfoList3.size()).isEqualTo(1);

        // Check Provider
        Intent intent4 = new Intent();
        intent4.setClassName(TEST_SDK1_PACKAGE, TEST_SDK1_PACKAGE + ".FakeProvider");
        List<ResolveInfo> resolveInfoList4 =
                getPackageManager().queryIntentContentProviders(intent4, 0);

        assertThat(resolveInfoList4).isNotEmpty();
        assertThat(resolveInfoList4.size()).isEqualTo(1);
    }

    @Test
    public void testSdkInstallMultipleMajorVersions() throws Exception {
        onBeforeSdkTests();

        // Major version 1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Major version 2.
        installPackage(TEST_SDK1_MAJOR_VERSION2);

        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 2));
    }

    @Test
    public void testSdkInstallMultipleMinorVersionsWrongSignature() throws Exception {
        onBeforeSdkTests();

        // Major version 1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Major version 1, different signer.
        installPackage(TEST_SDK1_DIFFERENT_SIGNER,
                "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.test.sdk1_1 "
                        + "signatures do not match newer version");
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
    }

    @Test
    public void testSdkInstallMultipleMajorVersionsWrongSignature() throws Exception {
        onBeforeSdkTests();

        // Major version 1.
        installPackage(TEST_SDK1_DIFFERENT_SIGNER);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Major version 2.
        installPackage(TEST_SDK1_MAJOR_VERSION2,
                "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.test.sdk1_1 "
                        + "signatures do not match newer version");

        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
    }

    @Test
    public void testSdkInstallAndUpdateTwoMajorVersions() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        installPackage(TEST_SDK2);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        // Same APK.
        installPackage(TEST_SDK1);
        installPackage(TEST_SDK2);

        // Updated APK.
        installPackage(TEST_SDK1_UPDATED);
        installPackage(TEST_SDK2_UPDATED);

        // Reverted APK.
        installPackage(TEST_SDK1);
        installPackage(TEST_SDK2);

        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_LIB_INDEPENDENCE)
    @RequiresFlagsDisabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testAppUsingSdkOptionalInstallInstall_allowAppInstallWithoutSDK()
            throws Exception {
        onBeforeSdkTests();

        // Try to install without required SDK1.
        installPackage(TEST_USING_SDK1_OPTIONAL);
        assertTrue(isAppInstalled(TEST_SDK_USER_PACKAGE));
    }

    @Test
    @RequiresFlagsDisabled(FLAG_SDK_LIB_INDEPENDENCE)
    public void testAppUsingSdkOptionalInstall_blockAppInstallWithoutSDK() throws Exception {
        onBeforeSdkTests();

        // Try to install without required SDK1.
        installPackage(
                TEST_USING_SDK1_OPTIONAL, /*disableAutoInstallDependencies=*/true,
                "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
        assertFalse(isAppInstalled(TEST_SDK_USER_PACKAGE));
    }

    private SharedPreferences getDefaultSharedPreferences() {
        final Context appContext = getContext().getApplicationContext();
        return PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    private void setDependencyInstallerRunMethod(String methodName) {
        getDefaultSharedPreferences().edit().putString(
                TestDependencyInstallerService.METHOD_NAME, methodName).commit();
    }

    private void assertNoErrorInDependencyInstallerService() throws Exception {
        String msg = getDefaultSharedPreferences().getString(
                TestDependencyInstallerService.ERROR_MESSAGE, "");

        assertWithMessage("Expected no error in DependencyInstallerService").that(msg).isEmpty();
    }

    private void assertErrorInDependencyInstallerService(String expected) throws Exception {
        String msg = getDefaultSharedPreferences().getString(
                TestDependencyInstallerService.ERROR_MESSAGE, "");

        assertWithMessage("Expected error in DependencyInstallerService")
                .that(msg).contains(expected);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testAppWithMissingDependency_dependencyInstallerDisabledShellCommand()
            throws Exception {
        onBeforeSdkTests();

        String errorMsg = installPackage(TEST_USING_SDK1, /*disableAutoInstallDependencies*/true);
        assertThat(errorMsg).contains("Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
        assertThat(errorMsg).contains("Reconcile failed");
        assertNoErrorInDependencyInstallerService();
    }

    @Test
    @EnsureCanAddUser
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testAppWithMissingDependency_dependencyInstallerRoleHolderMissing()
            throws Exception {
        onBeforeSdkTests();

        // The system might have bound to the Dependency Installer Service (DIS) previously for the
        // existing user from a previous test. Create a new user to ensure that no DIS is bound.
        try (UserReference secondaryUser = TestApis.users().createUser().createAndStart()) {
            TestApis.packages().instrumented().installExisting(secondaryUser);
            clearCurrentDependencyInstallerRoleHolder(secondaryUser.id());
            try {
                String errorMsg =
                        installPackageAsUser(
                                TEST_USING_SDK1,
                                secondaryUser.id(), /*disableAutoInstallDependencies*/
                                false);
                assertThat(errorMsg).contains("Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
                assertThat(errorMsg).contains("Dependency Installer Service not found");
            } finally {
                resetDependencyInstallerRoleHolder(secondaryUser.id());
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testAppWithMissingDependency_failDependencyResolution() throws Exception {
        onBeforeSdkTests();

        setDependencyInstallerRoleHolder();
        try {
            // Dependency Installer Service cannot resolve SDK3
            setDependencyInstallerRunMethod(TestDependencyInstallerService.METHOD_INSTALL_SYNC);
            String errorMsg = installPackageAsUser(TEST_USING_SDK3, mUserHelper.getUserId());
            assertThat(errorMsg).contains("Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
            assertThat(errorMsg).contains("Failed to resolve all dependencies automatically");
            assertErrorInDependencyInstallerService("Unsupported SDK found: " + TEST_SDK3_NAME);
        } finally {
            resetDependencyInstallerRoleHolder();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testAppWithMissingDependency_resolveSdk1_sync() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));
        uninstallPackageSilently(TEST_SDK1_PACKAGE);

        setDependencyInstallerRoleHolder();
        try {
            // Dependency Installer Service should resolve missing SDK1
            setDependencyInstallerRunMethod(TestDependencyInstallerService.METHOD_INSTALL_SYNC);
            installPackageAsUser(TEST_USING_SDK1, mUserHelper.getUserId());
            assertNoErrorInDependencyInstallerService();
        } finally {
            resetDependencyInstallerRoleHolder();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testAppWithMissingDependency_resolveSdk1_wrongCertDigest() throws Exception {
        onBeforeSdkTests();

        overrideUsesSdkLibraryCertificateDigest("RANDOMCERT");

        setDependencyInstallerRoleHolder();
        try {
            // Dependency Installer Service should try to resolve dependency but fail
            setDependencyInstallerRunMethod(TestDependencyInstallerService.METHOD_INSTALL_SYNC);
            String errorMsg = installPackageAsUser(TEST_USING_SDK1, mUserHelper.getUserId());
            assertThat(errorMsg).contains("Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
            assertThat(errorMsg).contains("Failed to resolve all dependencies automatically");
            assertErrorInDependencyInstallerService("Unsupported SDK found: " + TEST_SDK1_NAME);
        } finally {
            resetDependencyInstallerRoleHolder();
        }
    }


    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testAppWithMissingDependency_resolveSdk2_async() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK2);
        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK2_PACKAGE));
        uninstallPackageSilently(TEST_SDK2_PACKAGE);

        setDependencyInstallerRoleHolder();
        try {
            setDependencyInstallerRunMethod(TestDependencyInstallerService.METHOD_INSTALL_ASYNC);
            installPackageAsUser(TEST_USING_SDK1_AND_SDK2, mUserHelper.getUserId(), "Success");
            assertNoErrorInDependencyInstallerService();
        } finally {
            resetDependencyInstallerRoleHolder();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testDependencyInstallerService_sendsInvalidSessionId() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));
        uninstallPackageSilently(TEST_SDK1_PACKAGE);

        setDependencyInstallerRoleHolder();
        try {
            setDependencyInstallerRunMethod(
                    TestDependencyInstallerService.METHOD_INVALID_SESSION_ID);
            String msg = installPackageAsUser(TEST_USING_SDK1, mUserHelper.getUserId());
            assertThat(msg).contains("Failed to resolve all dependencies automatically");
            assertNoErrorInDependencyInstallerService();
        } finally {
            resetDependencyInstallerRoleHolder();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testDependencyInstallerService_sendsAbandonedSessionId() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));
        uninstallPackageSilently(TEST_SDK1_PACKAGE);

        setDependencyInstallerRoleHolder();
        try {
            setDependencyInstallerRunMethod(
                    TestDependencyInstallerService.METHOD_ABANDONED_SESSION_ID);
            String msg = installPackageAsUser(TEST_USING_SDK1, mUserHelper.getUserId());
            assertThat(msg).contains("Failed to resolve all dependencies automatically");
            assertNoErrorInDependencyInstallerService();
        } finally {
            resetDependencyInstallerRoleHolder();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testDependencyInstallerService_abandonSession_resumeOnFailure() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));
        uninstallPackageSilently(TEST_SDK1_PACKAGE);

        setDependencyInstallerRoleHolder();
        try {
            setDependencyInstallerRunMethod(
                    TestDependencyInstallerService.METHOD_ABANDON_SESSION_DURING_INSTALL);
            installPackageAsUser(TEST_USING_SDK1, mUserHelper.getUserId(), "Success");
            assertNoErrorInDependencyInstallerService();
        } finally {
            resetDependencyInstallerRoleHolder();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testDependencyInstallerService_resumeOnFailure_failsInstall() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));
        uninstallPackageSilently(TEST_SDK1_PACKAGE);

        setDependencyInstallerRoleHolder();
        try {
            setDependencyInstallerRunMethod(
                    TestDependencyInstallerService.METHOD_RESUME_ON_FAILURE_FAIL_INSTALL);
            String errorMsg = installPackageAsUser(TEST_USING_SDK1, mUserHelper.getUserId());
            assertThat(errorMsg).contains("Reconcile failed");
            assertNoErrorInDependencyInstallerService();
        } finally {
            resetDependencyInstallerRoleHolder();
        }
    }

    @Test
    @EnsureCanAddUser
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testDependencyInstallerService_multiUser() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));
        uninstallPackageSilently(TEST_SDK1_PACKAGE);

        // Verify we bind to current user's DIS and packages get installed on current user.
        setDependencyInstallerRoleHolder();
        try {
            setDependencyInstallerRunMethod(
                    TestDependencyInstallerService.METHOD_VERIFY_USER_ID);
            installPackageAsUser(TEST_USING_SDK1, mUserHelper.getUserId(), "Success");
            assertThat(isPackageInstalledForUser(
                        TEST_SDK_USER_PACKAGE, mUserHelper.getUserId())).isTrue();
            assertThat(isPackageInstalledForUser(
                        TEST_SDK1_PACKAGE, mUserHelper.getUserId())).isTrue();
        } finally {
            resetDependencyInstallerRoleHolder();
        }

        // Uninstall package for current user
        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);
        uninstallPackageSilently(TEST_SDK1_PACKAGE);
        assertThat(isPackageInstalledForUser(
                    TEST_SDK_USER_PACKAGE, mUserHelper.getUserId())).isFalse();
        assertThat(isPackageInstalledForUser(
                    TEST_SDK1_PACKAGE, mUserHelper.getUserId())).isFalse();

        // Now install the package again for another user
        try (UserReference secondaryUser = TestApis.users().createUser().createAndStart()) {
            // Install the test app on the new user first, otherwise system won't be able to bind
            // to DIS for new user.
            TestApis.packages().instrumented().installExisting(secondaryUser);

            setDependencyInstallerRoleHolder(secondaryUser.id());
            try {
                setDependencyInstallerRunMethod(
                        TestDependencyInstallerService.METHOD_VERIFY_USER_ID);
                installPackageAsUser(TEST_USING_SDK1, secondaryUser.id(), "Success");
                // Verify that package is installed on secondary user only
                assertThat(isPackageInstalledForUser(
                            TEST_SDK_USER_PACKAGE, secondaryUser.id())).isTrue();
                assertThat(isPackageInstalledForUser(
                            TEST_SDK1_PACKAGE, secondaryUser.id())).isTrue();
                assertThat(isPackageInstalledForUser(
                            TEST_SDK_USER_PACKAGE, mUserHelper.getUserId())).isFalse();
                assertThat(isPackageInstalledForUser(
                            TEST_SDK1_PACKAGE, mUserHelper.getUserId())).isFalse();
            } finally {
                resetDependencyInstallerRoleHolder(secondaryUser.id());
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testAppWithMissingDependency_multiSessionFailsLater() throws Exception {
        onBeforeSdkTests();

        // Create a multi session without all the dependencies
        // Parent session
        String parentSessionId = createSession("--multi-package");

        // Required SDK1.
        String sdkSessionId = createSession("");
        addSplits(sdkSessionId, new String[] { createApkPath(TEST_SDK1) });

        // The app.
        String appSessionId = createSession("");
        addSplits(appSessionId, new String[] { createApkPath(TEST_USING_SDK1_AND_SDK2) });

        // Add both child sessions to the primary session and commit.
        assertEquals("Success\n", executeShellCommand(
                "pm install-add-session " + parentSessionId + " " + sdkSessionId + " "
                        + appSessionId));

        // Installation should fail.
        String msg = executeShellCommand("pm install-commit " + parentSessionId);
        assertThat(msg).contains("Reconcile failed");
        assertNoErrorInDependencyInstallerService();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testInstallAppWithDependantSdk_dependencyInstallerDisabled_succeeds()
            throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));

        getUiAutomation().adoptShellPermissionIdentity();
        try {
            commitApk(
                    TEST_SDK_USER_PACKAGE,
                    TEST_USING_SDK1,
                    /*enableAutoInstallDependencies=*/false,
                    /*expectedStatus=*/PackageInstaller.STATUS_SUCCESS,
                    "INSTALL_SUCCEEDED");
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testInstallAppWithoutDependantSdk_dependencyInstallerDisabled_failsLater()
            throws Exception {
        onBeforeSdkTests();

        getUiAutomation().adoptShellPermissionIdentity();
        try {
            commitApk(
                    TEST_SDK_USER_PACKAGE,
                    TEST_USING_SDK1,
                    /*enableAutoInstallDependencies=*/false,
                    /*expectedStatus=*/null,
                    "Reconcile failed");
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testInstallAppWithoutDependantSdk_dependencyInstallerEnabled_succeeds()
            throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));

        getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGES);
        try {
            commitApk(
                    TEST_SDK_USER_PACKAGE,
                    TEST_USING_SDK1,
                    /*enableAutoInstallDependencies=*/true,
                    /*expectedStatus=*/PackageInstaller.STATUS_SUCCESS,
                    "INSTALL_SUCCEEDED");
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testAppUsingSdkRequiredInstallAndUpdate() throws Exception {
        onBeforeSdkTests();
        // Try to install without required SDK1.
        installPackage(
                TEST_USING_SDK1, /*disableAutoInstallDependencies=*/true,
                "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
        assertFalse(isAppInstalled(TEST_SDK_USER_PACKAGE));

        // Now install the required SDK1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));

        // Install and uninstall.
        installPackage(TEST_USING_SDK1);
        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);

        // Update SDK1.
        installPackage(TEST_SDK1_UPDATED);

        // Install again.
        installPackage(TEST_USING_SDK1);

        // Check resolution API.
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(TEST_SDK_USER_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(GET_SHARED_LIBRARY_FILES));
            assertEquals(1, appInfo.sharedLibraryInfos.size());
            SharedLibraryInfo libInfo = appInfo.sharedLibraryInfos.get(0);
            assertEquals("com.test.sdk1", libInfo.getName());
            assertEquals(1, libInfo.getLongVersion());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }

        // Try to install without required SDK2.
        installPackage(TEST_USING_SDK1_AND_SDK2, /*disableAutoInstallDependencies=*/true,
                "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");

        // Now install the required SDK2.
        installPackage(TEST_SDK2);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        // Install and uninstall.
        installPackage(TEST_USING_SDK1_AND_SDK2);
        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);

        // Update both SDKs.
        installPackage(TEST_SDK1_UPDATED);
        installPackage(TEST_SDK2_UPDATED);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        // Install again.
        installPackage(TEST_USING_SDK1_AND_SDK2);

        // Check resolution API.
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(TEST_SDK_USER_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(GET_SHARED_LIBRARY_FILES));
            assertEquals(2, appInfo.sharedLibraryInfos.size());
            assertEquals("com.test.sdk1", appInfo.sharedLibraryInfos.get(0).getName());
            assertEquals(1, appInfo.sharedLibraryInfos.get(0).getLongVersion());
            assertEquals("com.test.sdk2", appInfo.sharedLibraryInfos.get(1).getName());
            assertEquals(2, appInfo.sharedLibraryInfos.get(1).getLongVersion());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testAppUsingSdkRequiredInstallGroupInstall() throws Exception {
        onBeforeSdkTests();

        // Install/uninstall the sdk to grab its certDigest.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        String sdkCertDigest = getPackageCertDigest(TEST_SDK1_PACKAGE);
        uninstallPackageSilently(TEST_SDK1_PACKAGE);

        // Try to install without required SDK1.
        installPackage(
                TEST_USING_SDK1, /*disableAutoInstallDependencies=*/true,
                "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
        assertFalse(isAppInstalled(TEST_SDK_USER_PACKAGE));

        // Parent session
        String parentSessionId = createSession("--multi-package");

        // Required SDK1.
        String sdkSessionId = createSession("");
        addSplits(sdkSessionId, new String[] { createApkPath(TEST_SDK1) });

        // The app.
        String appSessionId = createSession("");
        addSplits(appSessionId, new String[] { createApkPath(TEST_USING_SDK1) });

        overrideUsesSdkLibraryCertificateDigest(sdkCertDigest);

        // Add both child sessions to the primary session and commit.
        assertEquals("Success\n", executeShellCommand(
                "pm install-add-session " + parentSessionId + " " + sdkSessionId + " "
                        + appSessionId));
        commitSession(parentSessionId);

        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isAppInstalled(TEST_SDK_USER_PACKAGE));

        // Check resolution API.
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(TEST_SDK_USER_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(GET_SHARED_LIBRARY_FILES));
            assertEquals(1, appInfo.sharedLibraryInfos.size());
            SharedLibraryInfo libInfo = appInfo.sharedLibraryInfos.get(0);
            assertEquals("com.test.sdk1", libInfo.getName());
            assertEquals(1, libInfo.getLongVersion());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testInstallSdkFailsMismatchingCertificate() throws Exception {
        onBeforeSdkTests();

        // Install the required SDK1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        // Try to install the package with empty digest.
        installPackage(
                TEST_USING_SDK1, /*disableAutoInstallDependencies=*/true,
                "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
    }

    @Test
    public void testUninstallSdkRequiredWhileAppUsing_blockUninstall() throws Exception {
        onBeforeSdkTests();

        // Install the required SDK1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));

        // Install the package.
        installPackage(TEST_USING_SDK1);

        uninstallPackage(TEST_SDK1_PACKAGE, "Failure [DELETE_FAILED_USED_SHARED_LIBRARY]");

        // The SDK is still installed
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_LIB_INDEPENDENCE)
    public void testUninstallSdkOptionalWhileAppUsing_allowUninstall() throws Exception {
        onBeforeSdkTests();

        // Install the required SDK1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));

        // Install the package optional using sdk1
        installPackage(TEST_USING_SDK1_OPTIONAL);

        uninstallPackage(TEST_SDK1_PACKAGE, "Success");
        assertThat(isSdkInstalled(TEST_SDK1_NAME, 1)).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_LIB_INDEPENDENCE)
    public void testSdkOptionalEnabledGetSharedLibraries() throws Exception {
        onBeforeSdkTests();

        // Install the SDK1.
        installPackage(TEST_SDK1);
        // Install the SDK2.
        installPackage(TEST_SDK2);

        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNotNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNotNull(sdk2);
        }
        {
            overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));

            installPackage(TEST_USING_SDK1_OPTIONAL_SDK2);

            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNotNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNotNull(sdk2);

            // SDK1 optional
            assertEquals(TEST_SDK_USER_PACKAGE,
                    sdk1.getDependentPackages().get(0).getPackageName());
            assertEquals(TEST_SDK_USER_PACKAGE,
                    sdk1.getOptionalDependentPackages().get(0).getPackageName());

            // SDK2 required
            assertEquals(TEST_SDK_USER_PACKAGE,
                    sdk2.getDependentPackages().get(0).getPackageName());
            assertThat(sdk2.getOptionalDependentPackages()).isEmpty();

            getUiAutomation().adoptShellPermissionIdentity();
            try {
                ApplicationInfo appInfo = getPackageManager().getApplicationInfo(
                        TEST_SDK_USER_PACKAGE,
                        PackageManager.ApplicationInfoFlags.of(GET_SHARED_LIBRARY_FILES));

                // feature is enabled. Two, one is optional, one is required
                assertThat(appInfo.sharedLibraryInfos).isNotNull();
                assertThat(appInfo.optionalSharedLibraryInfos).isNotNull();
                assertThat(appInfo.sharedLibraryInfos.size()).isEqualTo(2);
                assertThat(appInfo.optionalSharedLibraryInfos.size()).isEqualTo(1);

                assertThat(appInfo.optionalSharedLibraryInfos.get(0).getName()).isEqualTo(
                        "com.test.sdk1");
                assertThat(appInfo.optionalSharedLibraryInfos.get(0).getLongVersion()).isEqualTo(1);

                assertThat(appInfo.sharedLibraryInfos.get(0).getName()).isEqualTo(
                        "com.test.sdk1");
                assertThat(appInfo.sharedLibraryInfos.get(0).getLongVersion()).isEqualTo(1);
                assertThat(appInfo.sharedLibraryInfos.get(1).getName()).isEqualTo(
                        "com.test.sdk2");
                assertThat(appInfo.sharedLibraryInfos.get(1).getLongVersion()).isEqualTo(2);
            } finally {
                getUiAutomation().dropShellPermissionIdentity();
            }
            uninstallPackageSilently(TEST_SDK_USER_PACKAGE);
        }
    }

    @Test
    @RequiresFlagsDisabled(FLAG_SDK_LIB_INDEPENDENCE)
    public void testSdkOptionalDisabledGetSharedLibraries() throws Exception {
        onBeforeSdkTests();

        // Install the SDK1.
        installPackage(TEST_SDK1);
        // Install the SDK2.
        installPackage(TEST_SDK2);

        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNotNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNotNull(sdk2);
        }

        {
            overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));

            installPackage(TEST_USING_SDK1_OPTIONAL_SDK2);

            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNotNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNotNull(sdk2);

            assertEquals(TEST_SDK_USER_PACKAGE,
                    sdk1.getDependentPackages().get(0).getPackageName());
            assertEquals(TEST_SDK_USER_PACKAGE,
                    sdk2.getDependentPackages().get(0).getPackageName());
            assertThat(sdk1.getOptionalDependentPackages()).isEmpty();
            assertThat(sdk2.getOptionalDependentPackages()).isEmpty();


            getUiAutomation().adoptShellPermissionIdentity();
            try {
                ApplicationInfo appInfo = getPackageManager().getApplicationInfo(
                        TEST_SDK_USER_PACKAGE,
                        PackageManager.ApplicationInfoFlags.of(GET_SHARED_LIBRARY_FILES));

                // Two, one is optional, one is required but feature is disabled
                assertThat(appInfo.sharedLibraryInfos).isNotNull();
                assertThat(appInfo.optionalSharedLibraryInfos).isNull();
                assertThat(appInfo.sharedLibraryInfos.size()).isEqualTo(2);

                assertThat(appInfo.sharedLibraryInfos.get(0).getName()).isEqualTo(
                        "com.test.sdk1");
                assertThat(appInfo.sharedLibraryInfos.get(0).getLongVersion()).isEqualTo(1);

                assertThat(appInfo.sharedLibraryInfos.get(1).getName()).isEqualTo(
                        "com.test.sdk2");
                assertThat(appInfo.sharedLibraryInfos.get(1).getLongVersion()).isEqualTo(2);
            } finally {
                getUiAutomation().dropShellPermissionIdentity();
            }
            uninstallPackageSilently(TEST_SDK_USER_PACKAGE);
        }
    }

    @Test
    public void testGetSharedLibraries() throws Exception {
        onBeforeSdkTests();

        // Install the SDK1.
        installPackage(TEST_SDK1);
        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNotNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNull(sdk2);
        }

        // Install the SDK2.
        installPackage(TEST_SDK2);
        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNotNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNotNull(sdk2);
        }

        // Install and uninstall the user package.
        {
            overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));

            installPackage(TEST_USING_SDK1_AND_SDK2);

            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNotNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNotNull(sdk2);

            assertEquals(TEST_SDK_USER_PACKAGE,
                    sdk1.getDependentPackages().get(0).getPackageName());
            assertEquals(TEST_SDK_USER_PACKAGE,
                    sdk2.getDependentPackages().get(0).getPackageName());
            assertThat(sdk1.getOptionalDependentPackages()).isEmpty();
            assertThat(sdk2.getOptionalDependentPackages()).isEmpty();

            uninstallPackageSilently(TEST_SDK_USER_PACKAGE);
        }

        // Uninstall the SDK1.
        uninstallPackageSilently(TEST_SDK1_PACKAGE);
        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNotNull(sdk2);
        }

        // Uninstall the SDK2.
        uninstallPackageSilently(TEST_SDK2_PACKAGE);
        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk1 = findLibrary(libs, "com.test.sdk1", 1);
            assertNull(sdk1);
            SharedLibraryInfo sdk2 = findLibrary(libs, "com.test.sdk2", 2);
            assertNull(sdk2);
        }
    }

    @Test
    public void testUninstallUnusedSdks() throws Exception {
        onBeforeSdkTests();

        installPackage(TEST_SDK1);
        installPackage(TEST_SDK2);

        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));
        installPackage(TEST_USING_SDK1_AND_SDK2);

        setSystemProperty("debug.pm.prune_unused_shared_libraries_delay", "0");
        executeShellCommand("settings put global unused_static_shared_lib_min_cache_period 0");
        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);

        // Wait for 3secs max.
        for (int i = 0; i < 30; ++i) {
            if (!isSdkInstalled(TEST_SDK1_NAME, 1) && !isSdkInstalled(TEST_SDK2_NAME, 2)) {
                break;
            }
            final int beforeRetryDelayMs = 100;
            Thread.currentThread().sleep(beforeRetryDelayMs);
        }
        assertFalse(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertFalse(isSdkInstalled(TEST_SDK2_NAME, 2));
    }

    @Test
    public void testAppUsingSdkRequiredUsingSdkInstallAndUpdate() throws Exception {
        onBeforeSdkTests();

        // Try to install without required SDK1.
        installPackage(
                TEST_USING_SDK3, /*disableAutoInstallDependencies=*/true,
                "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
        assertFalse(isAppInstalled(TEST_SDK_USER_PACKAGE));

        // Try to install SDK3 without required SDK1.
        installPackage(
                TEST_SDK3_USING_SDK1, /*disableAutoInstallDependencies=*/true,
                "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
        assertFalse(isSdkInstalled(TEST_SDK3_NAME, 3));

        // Now install the required SDK1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));

        // Now install the required SDK3.
        installPackage(TEST_SDK3_USING_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK3_NAME, 3));

        // Install and uninstall.
        installPackage(TEST_USING_SDK3);
        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);

        // Update SDK1.
        installPackage(TEST_SDK1_UPDATED);

        // Install again.
        installPackage(TEST_USING_SDK3);

        // Check resolution API.
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(TEST_SDK_USER_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(GET_SHARED_LIBRARY_FILES));
            assertEquals(1, appInfo.sharedLibraryInfos.size());
            SharedLibraryInfo libInfo = appInfo.sharedLibraryInfos.get(0);
            assertEquals("com.test.sdk3", libInfo.getName());
            assertEquals(3, libInfo.getLongVersion());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }

        // Try to install updated SDK3 without required SDK2.
        installPackage(TEST_SDK3_USING_SDK1_AND_SDK2, /*disableAutoInstallDependencies=*/true,
                "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");

        // Now install the required SDK2.
        installPackage(TEST_SDK2);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        installPackage(TEST_SDK3_USING_SDK1_AND_SDK2);
        assertTrue(isSdkInstalled(TEST_SDK3_NAME, 3));

        // Install and uninstall.
        installPackage(TEST_USING_SDK3);
        uninstallPackageSilently(TEST_SDK_USER_PACKAGE);

        // Update both SDKs.
        installPackage(TEST_SDK1_UPDATED);
        installPackage(TEST_SDK2_UPDATED);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        // Install again.
        installPackage(TEST_USING_SDK3);

        // Check resolution API.
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(TEST_SDK_USER_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(GET_SHARED_LIBRARY_FILES));
            assertEquals(1, appInfo.sharedLibraryInfos.size());
            assertEquals("com.test.sdk3", appInfo.sharedLibraryInfos.get(0).getName());
            assertEquals(3, appInfo.sharedLibraryInfos.get(0).getLongVersion());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testSdkUsingSdkRequiredInstallAndUpdate() throws Exception {
        onBeforeSdkTests();

        // Try to install without required SDK1.
        installPackage(
                TEST_SDK3_USING_SDK1, /*disableAutoInstallDependencies=*/true,
                "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");
        assertFalse(isSdkInstalled(TEST_SDK3_NAME, 3));

        // Now install the required SDK1.
        installPackage(TEST_SDK1);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));

        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));

        // Install and uninstall.
        installPackage(TEST_SDK3_USING_SDK1);
        uninstallPackageSilently(TEST_SDK3_PACKAGE);

        // Update SDK1.
        installPackage(TEST_SDK1_UPDATED);

        // Install again.
        installPackage(TEST_SDK3_USING_SDK1);

        // Check resolution API.
        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk3 = findLibrary(libs, "com.test.sdk3", 3);
            assertNotNull(sdk3);
            List<SharedLibraryInfo> deps = sdk3.getDependencies();
            assertEquals(1, deps.size());
            SharedLibraryInfo libInfo = deps.get(0);
            assertEquals("com.test.sdk1", libInfo.getName());
            assertEquals(1, libInfo.getLongVersion());
        }

        // Try to install without required SDK2.
        installPackage(TEST_SDK3_USING_SDK1_AND_SDK2, /*disableAutoInstallDependencies=*/true,
                "Failure [INSTALL_FAILED_MISSING_SHARED_LIBRARY");

        // Now install the required SDK2.
        installPackage(TEST_SDK2);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        // Install and uninstall.
        installPackage(TEST_SDK3_USING_SDK1_AND_SDK2);
        uninstallPackageSilently(TEST_SDK3_PACKAGE);

        // Update both SDKs.
        installPackage(TEST_SDK1_UPDATED);
        installPackage(TEST_SDK2_UPDATED);
        assertTrue(isSdkInstalled(TEST_SDK1_NAME, 1));
        assertTrue(isSdkInstalled(TEST_SDK2_NAME, 2));

        // Install again.
        installPackage(TEST_SDK3_USING_SDK1_AND_SDK2);

        // Check resolution API.
        {
            List<SharedLibraryInfo> libs = getSharedLibraries();
            SharedLibraryInfo sdk3 = findLibrary(libs, "com.test.sdk3", 3);
            assertNotNull(sdk3);
            List<SharedLibraryInfo> deps = sdk3.getDependencies();
            assertEquals(2, deps.size());
            assertEquals("com.test.sdk1", deps.get(0).getName());
            assertEquals(1, deps.get(0).getLongVersion());
            assertEquals("com.test.sdk2", deps.get(1).getName());
            assertEquals(2, deps.get(1).getLongVersion());
        }
    }

    private void runPackageVerifierTest(BiConsumer<Context, Intent> onBroadcast)
            throws Exception {
        runPackageVerifierTest(TEST_HW5, TEST_HW7, "Success", onBroadcast);
    }

    private void runPackageVerifierTest(String expectedResultStartsWith,
            BiConsumer<Context, Intent> onBroadcast) throws Exception {
        runPackageVerifierTest(TEST_HW5, TEST_HW7, expectedResultStartsWith, onBroadcast);
    }

    private void runPackageVerifierTest(String baseName, String updatedName,
            String expectedResultStartsWith, BiConsumer<Context, Intent> onBroadcast)
            throws Exception {
        AtomicReference<Thread> onBroadcastThread = new AtomicReference<>();

        runPackageVerifierTestSync(baseName, updatedName, expectedResultStartsWith,
                (context, intent) -> {
                    Thread thread = new Thread(() -> onBroadcast.accept(context, intent));
                    thread.start();
                    onBroadcastThread.set(thread);
                });

        final Thread thread = onBroadcastThread.get();
        if (thread != null) {
            thread.join();
        }
    }

    private void runPackageVerifierTestSync(String baseName, String updatedName,
            String expectedResultStartsWith, BiConsumer<Context, Intent> onBroadcast)
            throws Exception {
        // Install a package.
        if (mUserHelper.isVisibleBackgroundUser()) {
            installPackageAsUser(baseName, mUserHelper.getUserId(), "Success\n");
        } else {
            installPackage(baseName);
        }
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));

        getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.PACKAGE_VERIFICATION_AGENT,
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        final CompletableFuture<Boolean> broadcastReceived = new CompletableFuture<>();

        // Create a single-use broadcast receiver
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                onBroadcast.accept(context, intent);
                broadcastReceived.complete(true);
            }
        };
        // Create an intent-filter and register the receiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_NEEDS_VERIFICATION);
        intentFilter.addDataType(PACKAGE_MIME_TYPE);
        // The broadcast is sent for user 0, so we need to request it for all users.
        // TODO(b/232317379) Fix this in proper way
        getContext().registerReceiverForAllUsers(broadcastReceiver, intentFilter, null, null,
                RECEIVER_EXPORTED);

        // Enable verification.
        executeShellCommand("settings put global verifier_verify_adb_installs 1");
        // Override verifier for updates of debuggable apps.
        setSystemProperty("debug.pm.adb_verifier_override_packages",
                CTS_PACKAGE_NAME + ";" + TEST_VERIFIER_PACKAGE);

        final int settingValue = Integer.parseInt(
                executeShellCommand("settings get global verifier_verify_adb_installs").trim());
        final String sysPropertyValue = getSystemProperty(
                "debug.pm.adb_verifier_override_packages").trim();
        // Make sure the setting and property are set
        assertEquals("verifier_verify_adb_installs is " + settingValue + " expecting 1",
                1, settingValue);
        assertEquals("debug.pm.adb_verifier_override_packages is " + sysPropertyValue,
                CTS_PACKAGE_NAME + ";" + TEST_VERIFIER_PACKAGE, sysPropertyValue);

        // Update the package, should trigger verifier override.
        if (mUserHelper.isVisibleBackgroundUser()) {
            installPackageAsUser(updatedName, mUserHelper.getUserId(), expectedResultStartsWith);
        } else {
            installPackage(updatedName, expectedResultStartsWith);
        }

        // Wait for broadcast.
        broadcastReceived.get(VERIFICATION_BROADCAST_RECEIVED_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test
    @LargeTest
    public void testPackageVerifierAllow() throws Exception {
        assumeTrue(!mStreaming);
        uninstallPackageSilently(TEST_VERIFIER_PACKAGE);

        AtomicInteger dataLoaderType = new AtomicInteger(-1);

        runPackageVerifierTest((context, intent) -> {
            int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
            assertNotEquals(-1, verificationId);

            dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
            int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
            assertNotEquals(-1, sessionId);

            getPackageManager().verifyPendingInstall(verificationId, VERIFICATION_ALLOW);
        });

        assertEquals(mDataLoaderType, dataLoaderType.get());
    }

    @Test
    @LargeTest
    public void testPackageVerifierAllowTwoVerifiers() throws Exception {
        assumeTrue(!mStreaming);
        uninstallPackageSilently(TEST_VERIFIER_PACKAGE);

        installPackage(TEST_VERIFIER_ALLOW);
        assertTrue(isAppInstalled(TEST_VERIFIER_PACKAGE));

        AtomicInteger dataLoaderType = new AtomicInteger(-1);

        runPackageVerifierTest((context, intent) -> {
            int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
            assertNotEquals(-1, verificationId);

            dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
            int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
            assertNotEquals(-1, sessionId);

            getPackageManager().verifyPendingInstall(verificationId, VERIFICATION_ALLOW);
        });

        assertEquals(mDataLoaderType, dataLoaderType.get());
    }

    @Test
    @LargeTest
    public void testPackageVerifierReject() throws Exception {
        assumeTrue(!mStreaming);
        uninstallPackageSilently(TEST_VERIFIER_PACKAGE);

        AtomicInteger dataLoaderType = new AtomicInteger(-1);

        runPackageVerifierTest("Failure [INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed",
                (context, intent) -> {
                    int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
                    assertNotEquals(-1, verificationId);

                    dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
                    int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
                    assertNotEquals(-1, sessionId);

                    getPackageManager().verifyPendingInstall(verificationId, VERIFICATION_REJECT);
                });

        assertEquals(mDataLoaderType, dataLoaderType.get());
    }

    @Test
    @LargeTest
    public void testPackageSufficientVerifierReject() throws Exception {
        assumeTrue(!mStreaming);
        uninstallPackageSilently(TEST_SUFFICIENT_VERIFIER_PACKAGE);

        // TEST_SUFFICIENT configured to have hellosufficient as sufficient verifier.
        installPackage(TEST_SUFFICIENT_VERIFIER_REJECT);
        assertTrue(isAppInstalled(TEST_SUFFICIENT_VERIFIER_PACKAGE));

        // PackageManager.verifyPendingInstall() call only works with user 0 as verifier is expected
        // to be user 0. So skip the test if it is not user 0.
        // TODO(b/232317379) Fix this in proper way
        assumeTrue(getContext().getUserId() == UserHandle.USER_SYSTEM);
        AtomicInteger dataLoaderType = new AtomicInteger(-1);

        runPackageVerifierTest(TEST_HW5, TEST_SUFFICIENT,
                "Failure [INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed",
                (context, intent) -> {
                    int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
                    assertNotEquals(-1, verificationId);

                    dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
                    int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
                    assertNotEquals(-1, sessionId);

                    // This is a required verifier. The installation should fail, even though the
                    // required verifier allows installation.
                    getPackageManager().verifyPendingInstall(verificationId, VERIFICATION_ALLOW);
                });

        assertEquals(mDataLoaderType, dataLoaderType.get());
    }

    @Test
    @LargeTest
    public void testPackageVerifierRejectTwoVerifiersBothReject() throws Exception {
        assumeTrue(!mStreaming);
        uninstallPackageSilently(TEST_VERIFIER_PACKAGE);

        installPackage(TEST_VERIFIER_REJECT);
        assertTrue(isAppInstalled(TEST_VERIFIER_PACKAGE));

        AtomicInteger dataLoaderType = new AtomicInteger(-1);

        runPackageVerifierTest("Failure [INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed",
                (context, intent) -> {
                    int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
                    assertNotEquals(-1, verificationId);

                    dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
                    int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
                    assertNotEquals(-1, sessionId);

                    getPackageManager().verifyPendingInstall(verificationId, VERIFICATION_REJECT);
                });

        assertEquals(mDataLoaderType, dataLoaderType.get());
    }

    @Test
    @LargeTest
    public void testPackageVerifierRejectTwoVerifiersOnlyOneRejects() throws Exception {
        assumeTrue(!mStreaming);
        uninstallPackageSilently(TEST_VERIFIER_PACKAGE);

        installPackage(TEST_VERIFIER_REJECT);
        assertTrue(isAppInstalled(TEST_VERIFIER_PACKAGE));

        AtomicInteger dataLoaderType = new AtomicInteger(-1);

        runPackageVerifierTest("Failure [INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed",
                (context, intent) -> {
                    int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
                    assertNotEquals(-1, verificationId);

                    dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
                    int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
                    assertNotEquals(-1, sessionId);

                    // This one allows, the other one rejects.
                    getPackageManager().verifyPendingInstall(verificationId, VERIFICATION_ALLOW);
                });

        assertEquals(mDataLoaderType, dataLoaderType.get());
    }

    @Test
    @LargeTest
    public void testPackageVerifierRejectTwoVerifiersOnlyOneDelayedRejects() throws Exception {
        assumeTrue(!mStreaming);
        uninstallPackageSilently(TEST_VERIFIER_PACKAGE);

        installPackage(TEST_VERIFIER_DELAYED_REJECT);
        assertTrue(isAppInstalled(TEST_VERIFIER_PACKAGE));

        AtomicInteger dataLoaderType = new AtomicInteger(-1);

        runPackageVerifierTest("Failure [INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed",
                (context, intent) -> {
                    int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
                    assertNotEquals(-1, verificationId);

                    dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
                    int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
                    assertNotEquals(-1, sessionId);

                    // This one allows, the other one rejects.
                    getPackageManager().verifyPendingInstall(verificationId, VERIFICATION_ALLOW);
                });

        assertEquals(mDataLoaderType, dataLoaderType.get());
    }

    @Test
    @LargeTest
    public void testPackageVerifierRejectAfterTimeout() throws Exception {
        assumeTrue(mVerifierTimeoutTest);
        uninstallPackageSilently(TEST_VERIFIER_PACKAGE);

        AtomicInteger dataLoaderType = new AtomicInteger(-1);

        runPackageVerifierTestSync(TEST_HW5, TEST_HW7, "Success", (context, intent) -> {
            int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
            assertNotEquals(-1, verificationId);

            dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
            int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
            assertNotEquals(-1, sessionId);

            try {
                if (mDataLoaderType == DATA_LOADER_TYPE_INCREMENTAL) {
                    // For streaming installations, the timeout is fixed at 3secs and always
                    // allow the install. Try to extend the timeout and then reject after
                    // much shorter time.
                    getPackageManager().extendVerificationTimeout(verificationId,
                            VERIFICATION_REJECT, sStreamingVerificationTimeoutMs * 3);
                    Thread.sleep(sStreamingVerificationTimeoutMs * 2);
                    getPackageManager().verifyPendingInstall(verificationId,
                            VERIFICATION_REJECT);
                } else {
                    getPackageManager().verifyPendingInstall(verificationId,
                            VERIFICATION_ALLOW);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(mDataLoaderType, dataLoaderType.get());
    }

    @Test
    @LargeTest
    public void testPackageVerifierWithExtensionAndTimeout() throws Exception {
        assumeTrue(mVerifierTimeoutTest);
        AtomicInteger dataLoaderType = new AtomicInteger(-1);

        runPackageVerifierTest((context, intent) -> {
            int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
            assertNotEquals(-1, verificationId);

            dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
            int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
            assertNotEquals(-1, sessionId);

            try {
                if (mDataLoaderType == DATA_LOADER_TYPE_INCREMENTAL) {
                    // For streaming installations, the timeout is fixed at 3secs and always
                    // allow the install. Try to extend the timeout and then reject after
                    // much shorter time.
                    getPackageManager().extendVerificationTimeout(verificationId,
                            VERIFICATION_REJECT, sStreamingVerificationTimeoutMs * 3);
                    Thread.sleep(sStreamingVerificationTimeoutMs * 2);
                    getPackageManager().verifyPendingInstall(verificationId,
                            VERIFICATION_REJECT);
                } else {
                    getPackageManager().verifyPendingInstall(verificationId,
                            VERIFICATION_ALLOW);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(mDataLoaderType, dataLoaderType.get());
    }

    @Test
    public void testPackageVerifierWithChecksums() throws Exception {
        assumeTrue(mVerifierTimeoutTest);
        uninstallPackageSilently(TEST_VERIFIER_PACKAGE);

        AtomicInteger dataLoaderType = new AtomicInteger(-1);
        List<ApkChecksum> checksums = new ArrayList<>();
        StringBuilder rootHash = new StringBuilder();

        runPackageVerifierTest((context, intent) -> {
            int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
            assertNotEquals(-1, verificationId);

            dataLoaderType.set(intent.getIntExtra(EXTRA_DATA_LOADER_TYPE, -1));
            int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
            assertNotEquals(-1, sessionId);

            try {
                PackageInstaller.Session session = getPackageInstaller().openSession(sessionId);
                assertNotNull(session);

                rootHash.append(intent.getStringExtra(EXTRA_VERIFICATION_ROOT_HASH));

                String[] names = session.getNames();
                assertEquals(1, names.length);
                session.requestChecksums(names[0], 0, PackageManager.TRUST_ALL,
                        ConcurrentUtils.DIRECT_EXECUTOR,
                        result -> checksums.addAll(result));
            } catch (IOException | CertificateEncodingException e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals(mDataLoaderType, dataLoaderType.get());

        assertEquals(1, checksums.size());

        if (mDataLoaderType == DATA_LOADER_TYPE_INCREMENTAL) {
            assertEquals(TYPE_WHOLE_MERKLE_ROOT_4K_SHA256, checksums.get(0).getType());
            assertEquals(rootHash.toString(),
                    "base.apk:" + HexDump.toHexString(checksums.get(0).getValue()));
        } else {
            assertEquals(TYPE_PARTIAL_MERKLE_ROOT_1M_SHA256, checksums.get(0).getType());
        }
    }

    @Test
    public void testPackageVerifierWithOneVerifierDisabledAtRunTime() throws Exception {
        assumeTrue(!mStreaming);
        uninstallPackageSilently(TEST_VERIFIER_PACKAGE);

        installPackage(TEST_VERIFIER_REJECT);
        assertTrue(isAppInstalled(TEST_VERIFIER_PACKAGE));
        runPackageVerifierTest("Failure [INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed",
                (context, intent) -> {
                    int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
                    assertNotEquals(-1, verificationId);

                    int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
                    assertNotEquals(-1, sessionId);

                    // This one allows, the other one rejects.
                    getPackageManager().verifyPendingInstall(verificationId, VERIFICATION_ALLOW);
                });

        // We can't disable the test package, but we can disable the second verifier package
        disablePackage(TEST_VERIFIER_PACKAGE);
        // Expect the installation to success, even though the second verifier would reject it
        // if the verifier is enabled
        runPackageVerifierTest(
                (context, intent) -> {
                    int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
                    assertNotEquals(-1, verificationId);

                    int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
                    assertNotEquals(-1, sessionId);

                    getPackageManager().verifyPendingInstall(verificationId, VERIFICATION_ALLOW);
                });

    }

    @Test
    public void testPackageVerifierWithOneVerifierDisabledAtManifest() throws Exception {
        assumeTrue(!mStreaming);
        uninstallPackageSilently(TEST_VERIFIER_PACKAGE);

        // The second verifier package is disabled in its manifest
        installPackage(TEST_VERIFIER_REJECT);
        assertTrue(isAppInstalled(TEST_VERIFIER_PACKAGE));
        runPackageVerifierTest("Failure [INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed",
                (context, intent) -> {
                    int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
                    assertNotEquals(-1, verificationId);

                    int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
                    assertNotEquals(-1, sessionId);

                    // This one allows, the other one rejects.
                    getPackageManager().verifyPendingInstall(verificationId, VERIFICATION_ALLOW);
                });
        // Uninstall the second verifier first to allow for the new verifier installation
        uninstallPackageSilently(TEST_VERIFIER_PACKAGE);
        installPackage(TEST_VERIFIER_DISABLED);
        assertTrue(isAppInstalled(TEST_VERIFIER_PACKAGE));
        // Expect the installation to success, even though the second verifier would reject it
        // if the verifier is enabled
        runPackageVerifierTest(
                (context, intent) -> {
                    int verificationId = intent.getIntExtra(EXTRA_VERIFICATION_ID, -1);
                    assertNotEquals(-1, verificationId);

                    int sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1);
                    assertNotEquals(-1, sessionId);

                    getPackageManager().verifyPendingInstall(verificationId, VERIFICATION_ALLOW);
                });

    }

    @Test
    public void testEmergencyInstallerNoAttribute() throws Exception {
        installPackage(TEST_INSTALLER_APP_ABSENT);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));

        getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.EMERGENCY_INSTALL_PACKAGES);
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(TEST_APP_PACKAGE);
            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);

            writeFileToSession(session, "installer_app_absent_updated",
                    TEST_INSTALLER_APP_ABSENT_UPDATED);

            final CompletableFuture<Integer> status = new CompletableFuture<>();
            final CompletableFuture<String> statusMessage = new CompletableFuture<>();
            session.commit(new IntentSender((IIntentSender) new IIntentSender.Stub() {
                @Override
                public void send(int code, Intent intent, String resolvedType,
                        IBinder whitelistToken, IIntentReceiver finishedReceiver,
                        String requiredPermission, Bundle options) throws RemoteException {
                    status.complete(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                            Integer.MIN_VALUE));
                    statusMessage.complete(
                            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                }
            }));

            assertEquals(statusMessage.get(), PackageInstaller.STATUS_PENDING_USER_ACTION,
                    (int) status.get());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testEmergencyInstallerNoPermission() throws Exception {
        installPackage(TEST_INSTALLER_APP);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));

        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(TEST_APP_PACKAGE);
            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);

            writeFileToSession(session, "installer_app_updated", TEST_INSTALLER_APP_UPDATED);

            final CompletableFuture<Integer> status = new CompletableFuture<>();
            final CompletableFuture<String> statusMessage = new CompletableFuture<>();
            session.commit(new IntentSender((IIntentSender) new IIntentSender.Stub() {
                @Override
                public void send(int code, Intent intent, String resolvedType,
                        IBinder whitelistToken, IIntentReceiver finishedReceiver,
                        String requiredPermission, Bundle options) throws RemoteException {
                    status.complete(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                            Integer.MIN_VALUE));
                    statusMessage.complete(
                            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                }
            }));

            assertEquals(statusMessage.get(), PackageInstaller.STATUS_PENDING_USER_ACTION,
                    (int) status.get());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    // We can't test updating a system app with INSTALL_PACKAGES in CTS tests; this positive test
    // will be in GTS tests instead.
    @Test
    public void testEmergencyInstallerNonSystemApp() throws Exception {
        installPackage(TEST_INSTALLER_APP);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));

        getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.EMERGENCY_INSTALL_PACKAGES);
        try {
            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(TEST_APP_PACKAGE);
            final int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);

            writeFileToSession(session, "installer_app_updated", TEST_INSTALLER_APP_UPDATED);

            final CompletableFuture<Integer> status = new CompletableFuture<>();
            final CompletableFuture<String> statusMessage = new CompletableFuture<>();
            session.commit(new IntentSender((IIntentSender) new IIntentSender.Stub() {
                @Override
                public void send(int code, Intent intent, String resolvedType,
                        IBinder whitelistToken, IIntentReceiver finishedReceiver,
                        String requiredPermission, Bundle options) throws RemoteException {
                    status.complete(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                            Integer.MIN_VALUE));
                    statusMessage.complete(
                            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                }
            }));

            assertEquals(statusMessage.get(), PackageInstaller.STATUS_PENDING_USER_ACTION,
                    (int) status.get());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testEmergencyInstallerUninstallNoAttribiute() throws Exception {
        assumeTrue(mDataLoaderType == DATA_LOADER_TYPE_NONE);
        getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.EMERGENCY_INSTALL_PACKAGES,
                Manifest.permission.REQUEST_DELETE_PACKAGES,
                Manifest.permission.INSTALL_PACKAGES);

        try {
            installPackage(TEST_INSTALLER_APP_ABSENT);
            assertTrue(isAppInstalled(TEST_APP_PACKAGE));

            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(TEST_APP_PACKAGE);
            final int sessionId = installer.createSession(params);
            installer.openSession(sessionId);

            final CompletableFuture<Integer> status = new CompletableFuture<>();
            final CompletableFuture<String> statusMessage = new CompletableFuture<>();

            installer.uninstall(TEST_APP_PACKAGE,
                    new IntentSender((IIntentSender) new IIntentSender.Stub() {
                        @Override
                        public void send(int code, Intent intent, String resolvedType,
                                IBinder whitelistToken, IIntentReceiver finishedReceiver,
                                String requiredPermission, Bundle options) throws RemoteException {
                            status.complete(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                                    Integer.MIN_VALUE));
                            statusMessage.complete(
                                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                        }
                    }));

            assertEquals(statusMessage.get(), PackageInstaller.STATUS_PENDING_USER_ACTION,
                    (int) status.get());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testEmergencyInstallerUninstallNoPermission() throws Exception {
        assumeTrue(mDataLoaderType == DATA_LOADER_TYPE_NONE);

        getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.REQUEST_DELETE_PACKAGES,
                Manifest.permission.INSTALL_PACKAGES);

        try {
            installPackage(TEST_INSTALLER_APP);
            assertTrue(isAppInstalled(TEST_APP_PACKAGE));

            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(TEST_APP_PACKAGE);
            final int sessionId = installer.createSession(params);
            installer.openSession(sessionId);

            final CompletableFuture<Integer> status = new CompletableFuture<>();
            final CompletableFuture<String> statusMessage = new CompletableFuture<>();

            installer.uninstall(TEST_APP_PACKAGE,
                    new IntentSender((IIntentSender) new IIntentSender.Stub() {
                        @Override
                        public void send(int code, Intent intent, String resolvedType,
                                IBinder whitelistToken, IIntentReceiver finishedReceiver,
                                String requiredPermission, Bundle options) throws RemoteException {
                            status.complete(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                                    Integer.MIN_VALUE));
                            statusMessage.complete(
                                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                        }
                    }));

            assertEquals(statusMessage.get(), PackageInstaller.STATUS_PENDING_USER_ACTION,
                    (int) status.get());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    // We can't test uninstalling a system app in CTS tests; this positive test will be in GTS tests
    // instead.
    @Test
    public void testEmergencyInstallerUninstallNonSystemApp() throws Exception {
        assumeTrue(mDataLoaderType == DATA_LOADER_TYPE_NONE);
        getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.EMERGENCY_INSTALL_PACKAGES,
                Manifest.permission.REQUEST_DELETE_PACKAGES,
                Manifest.permission.INSTALL_PACKAGES);

        try {
            installPackage(TEST_INSTALLER_APP);
            assertTrue(isAppInstalled(TEST_APP_PACKAGE));

            final PackageInstaller installer = getPackageInstaller();
            final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(TEST_APP_PACKAGE);
            final int sessionId = installer.createSession(params);
            installer.openSession(sessionId);

            final CompletableFuture<Integer> status = new CompletableFuture<>();
            final CompletableFuture<String> statusMessage = new CompletableFuture<>();

            installer.uninstall(TEST_APP_PACKAGE,
                    new IntentSender((IIntentSender) new IIntentSender.Stub() {
                        @Override
                        public void send(int code, Intent intent, String resolvedType,
                                IBinder whitelistToken, IIntentReceiver finishedReceiver,
                                String requiredPermission, Bundle options) throws RemoteException {
                            status.complete(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                                    Integer.MIN_VALUE));
                            statusMessage.complete(
                                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                        }
                    }));

            assertEquals(statusMessage.get(), PackageInstaller.STATUS_PENDING_USER_ACTION,
                    (int) status.get());
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testInstallSdk_withoutInstallDependencyPackagePermission() throws Exception {
        onBeforeSdkTests();

        commitApk(
                TEST_SDK1_PACKAGE,
                TEST_SDK1,
                /*enableAutoInstallDependencies=*/false,
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                /*expectedMsg=*/null);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testInstallSdk_withInstallDependencyPackagePermission() throws Exception {
        onBeforeSdkTests();

        getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.INSTALL_DEPENDENCY_SHARED_LIBRARIES);
        try {
            commitApk(
                    TEST_SDK1_PACKAGE,
                    TEST_SDK1,
                    /*enableAutoInstallDependencies=*/false,
                    PackageInstaller.STATUS_SUCCESS,
                    /*expectedMsg=*/null);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SDK_DEPENDENCY_INSTALLER)
    public void testInstallNonDependency_withInstallDependencyPackagePermission_fails()
            throws Exception {
        onBeforeSdkTests();
        installPackage(TEST_SDK1);
        overrideUsesSdkLibraryCertificateDigest(getPackageCertDigest(TEST_SDK1_PACKAGE));

        getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.INSTALL_DEPENDENCY_SHARED_LIBRARIES);
        try {
            commitApk(
                    TEST_SDK_USER_PACKAGE,
                    TEST_USING_SDK1,
                    /*enableAutoInstallDependencies=*/false,
                    PackageInstaller.STATUS_PENDING_USER_ACTION,
                    /*expectedMsg=*/null);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private void commitApk(
            String packageName, String apk, boolean enableAutoInstallDependencies,
            Integer expectedStatus, String expectedMsg)
            throws Exception {
        final PackageInstaller installer = getPackageInstaller();
        final SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(packageName);
        params.setAutoInstallDependenciesEnabled(enableAutoInstallDependencies);

        final int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);

        writeFileToSession(session, "test", apk);

        final CompletableFuture<String> statusMessage = new CompletableFuture<>();
        final CompletableFuture<Integer> status = new CompletableFuture<>();
        session.commit(new IntentSender((IIntentSender) new IIntentSender.Stub() {
            @Override
            public void send(int code, Intent intent, String resolvedType,
                    IBinder allowlistToken, IIntentReceiver finishedReceiver,
                    String requiredPermission, Bundle options) {
                status.complete(
                        intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE));
                statusMessage.complete(
                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            }
        }));

        if (expectedStatus != null) {
            assertEquals(statusMessage.get(), expectedStatus, status.get());
        }
        if (expectedMsg != null) {
            assertThat(statusMessage.get()).contains(expectedMsg);
        }
    }

    private List<SharedLibraryInfo> getSharedLibraries() {
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            return getPackageManager().getSharedLibraries(PackageManager.PackageInfoFlags.of(0));
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private SharedLibraryInfo findLibrary(List<SharedLibraryInfo> libs, String name, long version) {
        for (int i = 0, size = libs.size(); i < size; ++i) {
            SharedLibraryInfo lib = libs.get(i);
            if (name.equals(lib.getName()) && version == lib.getLongVersion()) {
                return lib;
            }
        }
        return null;
    }

    private String createUpdateSession(String packageName) throws IOException {
        return createSession("-p " + packageName);
    }

    private String createSession(String arg) throws IOException {
        final String prefix = "Success: created install session [";
        final String suffix = "]\n";
        final String commandResult = executeShellCommand("pm install-create " + arg);
        assertTrue(commandResult, commandResult.startsWith(prefix));
        assertTrue(commandResult, commandResult.endsWith(suffix));
        return commandResult.substring(prefix.length(), commandResult.length() - suffix.length());
    }

    private void addSplits(String sessionId, String[] splitNames) throws IOException {
        for (String splitName : splitNames) {
            File file = new File(splitName);
            assertEquals(
                    "Success: streamed " + file.length() + " bytes\n",
                    executeShellCommand("pm install-write " + sessionId + " " + file.getName() + " "
                            + splitName));
        }
    }

    private void addSplitsStdIn(String sessionId, String[] splitNames, String args)
            throws IOException {
        for (String splitName : splitNames) {
            File file = new File(splitName);
            assertEquals("Success: streamed " + file.length() + " bytes\n", executeShellCommand(
                    "pm install-write -S " + file.length() + " " + sessionId + " " + file.getName()
                            + " " + args, file));
        }
    }

    private void removeSplits(String sessionId, String[] splitNames) throws IOException {
        for (String splitName : splitNames) {
            assertEquals("Success\n",
                    executeShellCommand("pm install-remove " + sessionId + " " + splitName));
        }
    }

    private void removeSplitsBatch(String sessionId, String[] splitNames) throws IOException {
        assertEquals("Success\n", executeShellCommand(
                "pm install-remove " + sessionId + " " + String.join(" ", splitNames)));
    }

    private void commitSession(String sessionId) throws IOException {
        assertEquals("Success\n", executeShellCommand("pm install-commit " + sessionId));
    }

    static boolean isAppInstalled(String packageName) throws IOException {
        final String commandResult = executeShellCommand("pm list packages");
        final int prefixLength = "package:".length();
        return Arrays.stream(commandResult.split("\\r?\\n")).anyMatch(
                line -> line.length() > prefixLength && line.substring(prefixLength).equals(
                        packageName));
    }

    private boolean isSdkInstalled(String name, int versionMajor) throws IOException {
        final String sdkString = name + ":" + versionMajor;
        final String commandResult = executeShellCommand("pm list sdks");
        final int prefixLength = "sdk:".length();
        return Arrays.stream(commandResult.split("\\r?\\n"))
                .anyMatch(line -> line.length() > prefixLength && line.substring(
                        prefixLength).equals(sdkString));
    }

    private String getPackageCertDigest(String packageName) throws Exception {
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            PackageInfo sdkPackageInfo = getPackageManager().getPackageInfo(packageName,
                    PackageManager.PackageInfoFlags.of(
                            GET_SIGNING_CERTIFICATES | MATCH_STATIC_SHARED_AND_SDK_LIBRARIES));
            SigningInfo signingInfo = sdkPackageInfo.signingInfo;
            Signature[] signatures =
                    signingInfo != null ? signingInfo.getSigningCertificateHistory() : null;
            byte[] digest = PackageUtils.computeSha256DigestBytes(signatures[0].toByteArray());
            return new String(HexEncoding.encode(digest));
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private boolean isPackageInstalledForUser(String packageName, int userId) {
        Context userContext = getContext().createContextAsUser(UserHandle.of(userId), 0);
        try {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                userContext.getPackageManager().getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(
                            MATCH_STATIC_SHARED_AND_SDK_LIBRARIES));
            }, Manifest.permission.INSTALL_PACKAGES);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * SDK package is signed by build system. In theory we could try to extract the signature,
     * and patch the app manifest. This property allows us to override in runtime, which is much
     * easier.
     */
    private void overrideUsesSdkLibraryCertificateDigest(String sdkCertDigest) throws Exception {
        setSystemProperty("debug.pm.uses_sdk_library_default_cert_digest", sdkCertDigest);
    }

    static String getSplits(String packageName) throws IOException {
        final String commandResult = executeShellCommand("pm dump " + packageName);
        final String prefix = "    splits=[";
        final int prefixLength = prefix.length();
        Optional<String> maybeSplits = Arrays.stream(commandResult.split("\\r?\\n"))
                .filter(line -> line.startsWith(prefix)).findFirst();
        if (!maybeSplits.isPresent()) {
            return null;
        }
        String splits = maybeSplits.get();
        return splits.substring(prefixLength, splits.length() - 1);
    }

    static String createApkPath(String baseName) {
        return TEST_APK_PATH + baseName;
    }

    /* Install for all the users */
    private void installPackage(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertThat(file.exists()).isTrue();
        assertEquals("Success\n", executeShellCommand(
                "pm " + mInstall + " -t -g " + file.getPath()));
    }

    private void installPackage(String baseName, String expectedResultStartsWith)
            throws IOException {
        installPackage(
                baseName, /*disableAutoInstallDependencies=*/false, expectedResultStartsWith);
    }

    private String installPackage(String baseName, boolean disableAutoInstallDependencies)
            throws IOException {
        File file = new File(createApkPath(baseName));
        String disableDependencyInstall = "";
        if (disableAutoInstallDependencies) {
            disableDependencyInstall = mDisableDependencyInstall;
        }
        String result = executeShellCommand(
                "pm " + mInstall + " -t -g " + disableDependencyInstall + file.getPath());
        return result;
    }

    private void installPackage(
            String baseName, boolean disableAutoInstallDependencies,
            String expectedResultStartsWith)
            throws IOException {
        String result = installPackage(baseName, disableAutoInstallDependencies);
        assertThat(result).startsWith(expectedResultStartsWith);
    }

    private void installPackageAsUser(String baseName, int userId, String expectedResultStartsWith)
            throws IOException {
        String result = installPackageAsUser(baseName, userId);
        assertThat(result).startsWith(expectedResultStartsWith);
    }

    private String installPackageAsUser(String baseName, int userId) throws IOException {
        return installPackageAsUser(baseName, userId,
                /*disableAutoInstallDependencies=*/false);
    }

    private String installPackageAsUser(
            String baseName, int userId,
            boolean disableAutoInstallDependencies)
            throws IOException {
        File file = new File(createApkPath(baseName));
        String disableDependencyInstall = "";
        if (disableAutoInstallDependencies) {
            disableDependencyInstall = mDisableDependencyInstall;
        }
        String result = executeShellCommand(
                "pm " + mInstall + " -t -g " + " --user " + userId + " "
                + disableDependencyInstall + file.getPath());
        return result;
    }

    private void updatePackage(String packageName, String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n", executeShellCommand(
                "pm " + mInstall + " -t -p " + packageName + " -g " + file.getPath()));
    }

    private void updatePackageSkipEnable(String packageName, String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n", executeShellCommand(
                "pm " + mInstall + " --skip-enable -t -p " + packageName + " -g " + file.getPath()
        ));
    }

    private void installPackageStdIn(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n",
                executeShellCommand("pm " + mInstall + " -t -g -S " + file.length(), file));
    }

    private void updatePackageStdIn(String packageName, String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n", executeShellCommand(
                "pm " + mInstall + " -t -p " + packageName + " -g -S " + file.length(), file));
    }

    private void installSplits(String[] baseNames) throws IOException {
        if (mStreaming) {
            installSplitsBatch(baseNames);
            return;
        }
        String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        String sessionId = createSession(TEST_APP_PACKAGE);
        addSplits(sessionId, splits);
        commitSession(sessionId);
    }

    private void updateSplits(String[] baseNames) throws IOException {
        if (mStreaming) {
            updateSplitsBatch(baseNames);
            return;
        }
        String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        String sessionId = createSession("-p " + TEST_APP_PACKAGE);
        addSplits(sessionId, splits);
        commitSession(sessionId);
    }

    private void installSplitsStdInStreaming(String[] splits) throws IOException {
        File[] files = Arrays.stream(splits).map(split -> new File(split)).toArray(File[]::new);
        String param = Arrays.stream(files).map(
                file -> file.getName() + ":" + file.length()).collect(Collectors.joining(" "));
        assertEquals("Success\n", executeShellCommand("pm" + mInstall + param, files));
    }

    private void installSplitsStdIn(String[] baseNames, String args) throws IOException {
        String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        if (mStreaming) {
            installSplitsStdInStreaming(splits);
            return;
        }
        String sessionId = createSession(TEST_APP_PACKAGE);
        addSplitsStdIn(sessionId, splits, args);
        commitSession(sessionId);
    }

    private void installSplitsBatch(String[] baseNames) throws IOException {
        final String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        assertEquals("Success\n",
                executeShellCommand("pm " + mInstall + " -t -g " + String.join(" ", splits)));
    }

    private void updateSplitsBatch(String[] baseNames) throws IOException {
        final String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        assertEquals("Success\n", executeShellCommand(
                "pm " + mInstall + " -p " + TEST_APP_PACKAGE + " -t -g " + String.join(" ",
                        splits)));
    }

    private void uninstallPackage(String packageName, String expectedResultStartsWith)
            throws IOException {
        String result = uninstallPackageSilently(packageName);
        assertTrue(result, result.startsWith(expectedResultStartsWith));
    }

    private static String uninstallPackageSilently(String packageName) throws IOException {
        return executeShellCommand("pm uninstall " + packageName);
    }

    private void uninstallSplits(String packageName, String[] splitNames) throws IOException {
        for (String splitName : splitNames) {
            assertEquals("Success\n",
                    executeShellCommand("pm uninstall " + packageName + " " + splitName));
        }
    }

    private void uninstallSplitsBatch(String packageName, String[] splitNames) throws IOException {
        assertEquals("Success\n", executeShellCommand(
                "pm uninstall " + packageName + " " + String.join(" ", splitNames)));
    }

    public static void setSystemProperty(String name, String value) throws Exception {
        assertEquals("", executeShellCommand("setprop " + name + " " + value));
    }

    public static String getSystemProperty(String prop) throws Exception {
        return executeShellCommand("getprop " + prop).replace("\n", "");
    }

    private void disablePackage(String packageName) {
        getUiAutomation().adoptShellPermissionIdentity();
        try {
            getPackageManager().setApplicationEnabledSetting(packageName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private List<String> getOldCodePaths(String packageName) throws IOException {
        final String commandResult = executeShellCommand("dumpsys package " + packageName);
        final String prefix = "      oldCodePath=";
        return Arrays.stream(commandResult.split("\\r?\\n"))
                .filter(line -> line.startsWith(prefix))
                .map(s -> s.substring(prefix.length()))
                .toList();
    }

    private String getFormattedBytes(long size) {
        double k = size/1024.0;
        double m = size/1048576.0;
        double g = size/1073741824.0;

        DecimalFormat dec = new DecimalFormat("0.00");
        if (g > 1) {
            return dec.format(g).concat(" Gb");
        } else if (m > 1) {
            return dec.format(m).concat(" Mb");
        } else if (k > 1) {
            return dec.format(k).concat(" Kb");
        }
        return "";
    }

    /**
     * Return the string that displays the data size.
     */
    private String getDataSizeDisplay(long size) {
        String formattedOutput = getFormattedBytes(size);
        if (!formattedOutput.isEmpty()) {
           formattedOutput = " (" + formattedOutput + ")";
        }
        return Long.toString(size) + " bytes" + formattedOutput;
    }

    private void clearCurrentDependencyInstallerRoleHolder(int userId) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                        Manifest.permission.MANAGE_ROLE_HOLDERS);
        try {
            assertThat(mPreviousDependencyInstallerRoleHolder).isNull();
            mPreviousDependencyInstallerRoleHolder = getDependencyInstallerRoleHolder(userId);
            mRoleManager.clearRoleHoldersAsUser(
                    ROLE_SYSTEM_DEPENDENCY_INSTALLER,
                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                    UserHandle.of(userId),
                    getContext().getMainExecutor(),
                    success -> {
                        if (success) {
                            latch.countDown();
                        }
                    });

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertWithMessage("Failed to clear dependency installer role holder")
                    .that(getDependencyInstallerRoleHolder(userId))
                    .isNull();
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private String getDependencyInstallerRoleHolder() throws Exception {
        return getDependencyInstallerRoleHolder(mUserHelper.getUserId());
    }

    private String getDependencyInstallerRoleHolder(int userId) throws Exception {
        return mRoleManager
            .getRoleHoldersAsUser(
                    ROLE_SYSTEM_DEPENDENCY_INSTALLER, UserHandle.of(userId))
            .stream().findFirst().orElse(null);
    }

    private void setDependencyInstallerRoleHolder() throws Exception {
        setDependencyInstallerRoleHolder(mUserHelper.getUserId());
    }

    private void setDependencyInstallerRoleHolder(int userId) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                Manifest.permission.MANAGE_ROLE_HOLDERS,
                Manifest.permission.BYPASS_ROLE_QUALIFICATION
        );
        try {
            assertThat(mPreviousDependencyInstallerRoleHolder).isNull();
            mPreviousDependencyInstallerRoleHolder = getDependencyInstallerRoleHolder();
            mRoleManager.setBypassingRoleQualification(true);
            mRoleManager.addRoleHolderAsUser(
                    ROLE_SYSTEM_DEPENDENCY_INSTALLER, CTS_PACKAGE_NAME,
                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, UserHandle.of(userId),
                    getContext().getMainExecutor(),
                    success -> {
                        if (success) {
                            latch.countDown();
                        }
                    });

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertWithMessage("Failed to set dependency installer role holder")
                    .that(getDependencyInstallerRoleHolder(userId)).isEqualTo(CTS_PACKAGE_NAME);
            Log.d(TAG, "Dependency Installer Role updated to " + CTS_PACKAGE_NAME + " for user: "
                    + userId);
        } finally {
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private void resetDependencyInstallerRoleHolder() throws Exception {
        resetDependencyInstallerRoleHolder(mUserHelper.getUserId());
    }

    private void resetDependencyInstallerRoleHolder(int userId) throws Exception {
        getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                Manifest.permission.MANAGE_ROLE_HOLDERS,
                Manifest.permission.BYPASS_ROLE_QUALIFICATION
        );
        CountDownLatch removeLatch = new CountDownLatch(1);
        CountDownLatch addLatch = new CountDownLatch(1);
        try {
            mRoleManager.setBypassingRoleQualification(true);

            mRoleManager.removeRoleHolderAsUser(
                    ROLE_SYSTEM_DEPENDENCY_INSTALLER,
                    CTS_PACKAGE_NAME,
                    RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                    UserHandle.of(userId),
                    getContext().getMainExecutor(),
                    success -> {
                        if (success) {
                            removeLatch.countDown();
                        }
                    });
            assertThat(removeLatch.await(5, TimeUnit.SECONDS)).isTrue();

            if (mPreviousDependencyInstallerRoleHolder != null) {
                mRoleManager.addRoleHolderAsUser(
                        ROLE_SYSTEM_DEPENDENCY_INSTALLER,
                        mPreviousDependencyInstallerRoleHolder,
                        RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                        UserHandle.of(userId),
                        getContext().getMainExecutor(),
                        success -> {
                            if (success) {
                                addLatch.countDown();
                            }
                        });
                assertThat(addLatch.await(5, TimeUnit.SECONDS)).isTrue();
                assertWithMessage("Failed to set dependency installer role holder")
                        .that(getDependencyInstallerRoleHolder(userId))
                        .isEqualTo(mPreviousDependencyInstallerRoleHolder);
                Log.d(TAG, "Dependency Installer Role updated to "
                        + mPreviousDependencyInstallerRoleHolder + " for user: " + userId);
                mPreviousDependencyInstallerRoleHolder = null;
            }
        } finally {
            mRoleManager.setBypassingRoleQualification(false);
            getUiAutomation().dropShellPermissionIdentity();
        }
    }

    static class PackageBroadcastReceiver extends BroadcastReceiver {
        private final String mTargetPackage;
        private final int mTargetUserId;
        private CompletableFuture<Intent> mUserReceivedBroadcast = new CompletableFuture();
        private final String mAction;
        PackageBroadcastReceiver(String packageName, int targetUserId, String action) {
            mTargetPackage = packageName;
            mTargetUserId = targetUserId;
            mAction = action;
            reset();
        }
        @Override
        public void onReceive(Context context, Intent intent) {
            final String packageName = intent.getData() == null
                    ? null : intent.getData().getEncodedSchemeSpecificPart();
            final int userId = context.getUserId();
            if (intent.getAction().equals(mAction) && userId == mTargetUserId
                    && (packageName == null || packageName.equals(mTargetPackage))) {
                // Only check packageName if it is included in the intent
                mUserReceivedBroadcast.complete(intent);
            }
        }
        public void assertBroadcastReceived() throws Exception {
            // Make sure broadcast has been sent from PackageManager
            executeShellCommand("pm wait-for-handler --timeout 2000");
            // Make sure broadcast has been dispatched from the queue
            executeShellCommand(String.format(
                    "am wait-for-broadcast-dispatch -a %s -d package:%s", mAction, mTargetPackage));
            // Checks that broadcast is delivered here
            assertNotNull(mUserReceivedBroadcast.get(6000, TimeUnit.MILLISECONDS));
        }
        public void assertBroadcastNotReceived() throws Exception {
            // Make sure broadcast has been sent from PackageManager
            executeShellCommand("pm wait-for-handler --timeout 2000");
            executeShellCommand(String.format(
                    "am wait-for-broadcast-dispatch -a %s -d package:%s", mAction, mTargetPackage));
            expectThrows(TimeoutException.class,
                    () -> mUserReceivedBroadcast.get(500, TimeUnit.MILLISECONDS));
        }

        public Intent getBroadcastResult() {
            return mUserReceivedBroadcast.getNow(null);
        }

        public void reset() {
            mUserReceivedBroadcast = new CompletableFuture();
        }
    }
}

