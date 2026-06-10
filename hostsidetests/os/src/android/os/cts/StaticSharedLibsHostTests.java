/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.os.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static junit.framework.Assert.fail;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeInstant;
import android.platform.test.annotations.LargeTest;
import android.platform.test.annotations.Presubmit;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.PollingCheck;
import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Presubmit
@RunWith(DeviceJUnit4ClassRunner.class)
public class StaticSharedLibsHostTests extends BaseHostJUnit4Test implements IBuildReceiver {
    private static final String ANDROID_JUNIT_RUNNER_CLASS =
            "androidx.test.runner.AndroidJUnitRunner";

    private static final String STATIC_LIB_PROVIDER_RECURSIVE_APK =
            "CtsStaticSharedLibProviderRecursive.apk";
    private static final String STATIC_LIB_PROVIDER_RECURSIVE_PKG =
            "android.os.lib.provider.recursive";

    private static final String STATIC_LIB_PROVIDER_RECURSIVE_NAME = "foo.bar.lib.recursive";
    private static final String STATIC_LIB_PROVIDER_NAME = "foo.bar.lib";

    private static final String STATIC_LIB_PROVIDER1_APK = "CtsStaticSharedLibProviderApp1.apk";
    private static final String STATIC_LIB_PROVIDER1_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER2_APK = "CtsStaticSharedLibProviderApp2.apk";
    private static final String STATIC_LIB_PROVIDER2_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER3_APK = "CtsStaticSharedLibProviderApp3.apk";
    private static final String STATIC_LIB_PROVIDER3_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER4_APK = "CtsStaticSharedLibProviderApp4.apk";
    private static final String STATIC_LIB_PROVIDER4_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER5_APK = "CtsStaticSharedLibProviderApp5.apk";
    private static final String STATIC_LIB_PROVIDER5_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER6_APK = "CtsStaticSharedLibProviderApp6.apk";
    private static final String STATIC_LIB_PROVIDER6_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER7_APK = "CtsStaticSharedLibProviderApp7.apk";
    private static final String STATIC_LIB_PROVIDER7_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_NATIVE_PROVIDER_APK =
            "CtsStaticSharedNativeLibProvider.apk";
    private static final String STATIC_LIB_NATIVE_PROVIDER_PKG =
            "android.os.lib.provider";

    private static final String STATIC_LIB_NATIVE_PROVIDER_APK1 =
            "CtsStaticSharedNativeLibProvider1.apk";
    private static final String STATIC_LIB_NATIVE_PROVIDER_PKG1 =
            "android.os.lib.provider";

    private static final String STATIC_LIB_CONSUMER1_APK = "CtsStaticSharedLibConsumerApp1.apk";
    private static final String STATIC_LIB_CONSUMER1_BAD_CERT_DIGEST_APK =
            "CtsStaticSharedLibConsumerApp1BadCertDigest.apk";
    private static final String STATIC_LIB_CONSUMER1_PKG = "android.os.lib.consumer1";

    private static final String STATIC_LIB_CONSUMER2_APK = "CtsStaticSharedLibConsumerApp2.apk";
    private static final String STATIC_LIB_CONSUMER2_PKG = "android.os.lib.consumer2";

    private static final String STATIC_LIB_CONSUMER3_APK = "CtsStaticSharedLibConsumerApp3.apk";
    private static final String STATIC_LIB_CONSUMER3_PKG = "android.os.lib.consumer3";

    private static final String STATIC_LIB_NATIVE_CONSUMER_APK
            = "CtsStaticSharedNativeLibConsumer.apk";
    private static final String STATIC_LIB_NATIVE_CONSUMER_PKG
            = "android.os.lib.consumer";

    private static final String STATIC_LIB_TEST_APP_PKG = "android.os.lib.app";
    private static final String STATIC_LIB_TEST_APP_CLASS_NAME = STATIC_LIB_TEST_APP_PKG
            + ".StaticSharedLibsTests";
    private static final String STATIC_LIB_MULTI_USER_TEST_APP_CLASS_NAME = STATIC_LIB_TEST_APP_PKG
            + ".StaticSharedLibsMultiUserTests";

    private static final String SETTING_UNUSED_STATIC_SHARED_LIB_MIN_CACHE_PERIOD =
            "unused_static_shared_lib_min_cache_period";

    private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private CompatibilityBuildHelper mBuildHelper;
    private boolean mInstantMode = false;

    @Before
    @After
    public void cleanUp() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER3_PKG);
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_NATIVE_CONSUMER_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER7_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER6_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER5_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER3_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    @AppModeInstant
    @Test
    public void testInstallSharedLibraryInstantMode() throws Exception {
        mInstantMode = true;
        doTestInstallSharedLibrary();
    }

    @AppModeFull
    @Test
    public void testInstallSharedLibraryFullMode() throws Exception {
        doTestInstallSharedLibrary();
    }

    private void doTestInstallSharedLibrary() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install version 1
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Install version 2
            assertNull(install(STATIC_LIB_PROVIDER2_APK));
            // Uninstall version 1
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG));
            // Uninstall version 2
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG));
            // Uninstall dependency
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @AppModeInstant
    @Test
    public void testCannotInstallSharedLibraryWithMissingDependencyInstantMode() throws Exception {
        mInstantMode = true;
        doTestCannotInstallSharedLibraryWithMissingDependency();
    }

    @AppModeFull
    @Test
    public void testCannotInstallSharedLibraryWithMissingDependencyFullMode() throws Exception {
        doTestCannotInstallSharedLibraryWithMissingDependency();
    }

    private void doTestCannotInstallSharedLibraryWithMissingDependency() throws Exception {
        try {
            // Install version 1 - should fail - no dependency
            assertNotNull(install(STATIC_LIB_PROVIDER1_APK));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        }
    }

    @Test
    public void testLoadCodeAndResourcesFromSharedLibraryRecursively() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install the library
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Install the client
            assertNull(install(STATIC_LIB_CONSUMER1_APK));
            // Try to load code and resources
            runDeviceTests(STATIC_LIB_CONSUMER1_PKG,
                    "android.os.lib.consumer1.UseSharedLibraryTest",
                    "testLoadCodeAndResources");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @Test
    public void testLoadCodeAndResourcesFromSharedLibraryRecursivelyUpdate() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install the library
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Install the client
            assertNull(install(STATIC_LIB_CONSUMER1_APK));
            // Try to load code and resources
            runDeviceTests(STATIC_LIB_CONSUMER1_PKG,
                    "android.os.lib.consumer1.UseSharedLibraryTest",
                    "testLoadCodeAndResources");
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK, true));
            // Try to load code and resources
            runDeviceTests(STATIC_LIB_CONSUMER1_PKG,
                    "android.os.lib.consumer1.UseSharedLibraryTest",
                    "testLoadCodeAndResources");
            // Install the library
            assertNull(install(STATIC_LIB_PROVIDER1_APK, true));
            // Try to load code and resources
            runDeviceTests(STATIC_LIB_CONSUMER1_PKG,
                    "android.os.lib.consumer1.UseSharedLibraryTest",
                    "testLoadCodeAndResources");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @AppModeInstant
    @Test
    public void testCannotUninstallUsedSharedLibrary1InstantMode() throws Exception {
        mInstantMode = true;
        doTestCannotUninstallUsedSharedLibrary1();
    }

    @AppModeFull
    @Test
    public void testCannotUninstallUsedSharedLibrary1FullMode() throws Exception {
        doTestCannotUninstallUsedSharedLibrary1();
    }

    private void doTestCannotUninstallUsedSharedLibrary1() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install the library
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // The library dependency cannot be uninstalled
            assertNotNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG));
            // Now the library dependency can be uninstalled
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG));
            // Uninstall dependency
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @AppModeInstant
    @Test
    public void testCannotUninstallUsedSharedLibrary2InstantMode() throws Exception {
        mInstantMode = true;
        doTestCannotUninstallUsedSharedLibrary2();
    }

    @AppModeFull
    @Test
    public void testCannotUninstallUsedSharedLibrary2FullMode() throws Exception {
        doTestCannotUninstallUsedSharedLibrary2();
    }

    private void doTestCannotUninstallUsedSharedLibrary2() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install the library
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Install the client
            assertNull(install(STATIC_LIB_CONSUMER1_APK));
            // The library cannot be uninstalled
            assertNotNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG));
            // Uninstall the client
            assertNull(getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG));
            // Now the library can be uninstalled
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG));
            // Uninstall dependency
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @AppModeInstant
    @Test
    public void testLibraryVersionsAndVersionCodesSameOrderInstantMode() throws Exception {
        mInstantMode = true;
        doTestLibraryVersionsAndVersionCodesSameOrder();
    }

    @AppModeFull
    @Test
    public void testLibraryVersionsAndVersionCodesSameOrderFullMode() throws Exception {
        doTestLibraryVersionsAndVersionCodesSameOrder();
    }

    private void doTestLibraryVersionsAndVersionCodesSameOrder() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install library version 1 with version code 1
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Install library version 2 with version code 4
            assertNull(install(STATIC_LIB_PROVIDER2_APK));
            // Shouldn't be able to install library version 3 with version code 3
            assertNotNull(install(STATIC_LIB_PROVIDER3_APK));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER3_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @AppModeInstant
    @Test
    public void testCannotInstallAppWithMissingLibraryInstantMode() throws Exception {
        mInstantMode = true;
        doTestCannotInstallAppWithMissingLibrary();
    }

    @AppModeFull
    @Test
    public void testCannotInstallAppWithMissingLibraryFullMode() throws Exception {
        doTestCannotInstallAppWithMissingLibrary();
    }

    private void doTestCannotInstallAppWithMissingLibrary() throws Exception {
        try {
            // Shouldn't be able to install an app if a dependency lib is missing
            assertNotNull(install(STATIC_LIB_CONSUMER1_APK));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        }
    }

    @AppModeFull
    @Test
    public void testCanReplaceLibraryIfVersionAndVersionCodeSame() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install a library
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Can reinstall the library if version and version code same
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @AppModeInstant
    @Test
    public void testUninstallSpecificLibraryVersionInstantMode() throws Exception {
        mInstantMode = true;
        doTestUninstallSpecificLibraryVersion();
    }

    @AppModeFull
    @Test
    public void testUninstallSpecificLibraryVersionFullMode() throws Exception {
        doTestUninstallSpecificLibraryVersion();
    }

    private void doTestUninstallSpecificLibraryVersion() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install library version 1 with version code 1
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Install library version 2 with version code 4
            assertNull(install(STATIC_LIB_PROVIDER2_APK));
            // Uninstall the library package with version code 4 (version 2)
            assertThat(getDevice().executeShellCommand("pm uninstall --versionCode 4 "
                    + STATIC_LIB_PROVIDER1_PKG).startsWith("Success")).isTrue();
            // Uninstall the library package with version code 1 (version 1)
            assertThat(getDevice().executeShellCommand("pm uninstall "
                    + STATIC_LIB_PROVIDER1_PKG).startsWith("Success")).isTrue();
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @AppModeInstant
    @Test
    public void testKeyRotationInstantMode() throws Exception {
        mInstantMode = true;
        doTestKeyRotation();
    }

    @AppModeFull
    @Test
    public void testKeyRotationFullMode() throws Exception {
        doTestKeyRotation();
    }

    private void doTestKeyRotation() throws Exception {
        try {
            // Install a library version specifying an upgrade key set
            assertNull(install(STATIC_LIB_PROVIDER2_APK));
            // Install a newer library signed with the upgrade key set
            assertNull(install(STATIC_LIB_PROVIDER4_APK));
            // Install a client that depends on the upgraded key set
            assertNull(install(STATIC_LIB_CONSUMER2_APK));
            // Ensure code and resources can be loaded
            runDeviceTests(STATIC_LIB_CONSUMER2_PKG,
                    "android.os.lib.consumer2.UseSharedLibraryTest",
                    "testLoadCodeAndResources");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
        }
    }

    @AppModeInstant
    @Test
    public void testCannotInstallIncorrectlySignedLibraryInstantMode() throws Exception {
        mInstantMode = true;
        doTestCannotInstallIncorrectlySignedLibrary();
    }

    @AppModeFull
    @Test
    public void testCannotInstallIncorrectlySignedLibraryFullMode() throws Exception {
        doTestCannotInstallIncorrectlySignedLibrary();
    }

    private void doTestCannotInstallIncorrectlySignedLibrary() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install a library version not specifying an upgrade key set
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Shouldn't be able to install a newer version signed differently
            assertNotNull(install(STATIC_LIB_PROVIDER4_APK));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @AppModeInstant
    @Test
    public void testLibraryAndPackageNameCanMatchInstantMode() throws Exception {
        mInstantMode = true;
        doTestLibraryAndPackageNameCanMatch();
    }

    @AppModeFull
    @Test
    public void testLibraryAndPackageNameCanMatchFullMode() throws Exception {
        doTestLibraryAndPackageNameCanMatch();
    }

    private void doTestLibraryAndPackageNameCanMatch() throws Exception {
        try {
            // Install a library with same name as package should work.
            assertNull(install(STATIC_LIB_PROVIDER5_APK));
            // Install a library with same name as package should work.
            assertNull(install(STATIC_LIB_PROVIDER6_APK));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER5_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER6_PKG);
        }
    }

    @AppModeInstant
    @Test
    public void testGetSharedLibrariesInstantMode() throws Exception {
        mInstantMode = true;
        doTestGetSharedLibraries();
    }

    @AppModeFull
    @Test
    public void testGetSharedLibrariesFullMode() throws Exception {
        doTestGetSharedLibraries();
    }

    private void doTestGetSharedLibraries() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install the first library
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Install the second library
            assertNull(install(STATIC_LIB_PROVIDER2_APK));
            // Install the third library
            assertNull(install(STATIC_LIB_PROVIDER4_APK));
            // Install the first client
            assertNull(install(STATIC_LIB_CONSUMER1_APK));
            // Install the second client
            assertNull(install(STATIC_LIB_CONSUMER2_APK));
            // Ensure the first library has the REQUEST_INSTALL_PACKAGES app op
            getDevice().executeShellV2Command("appops set "
                    + STATIC_LIB_CONSUMER1_PKG
                    + " REQUEST_INSTALL_PACKAGES allow");
            // Ensure libraries are properly reported
            runDeviceTests(STATIC_LIB_CONSUMER1_PKG,
                    "android.os.lib.consumer1.UseSharedLibraryTest",
                    "testSharedLibrariesProperlyReported");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @AppModeFull(
            reason = "getDeclaredSharedLibraries() requires ACCESS_SHARED_LIBRARIES permission")
    @Test
    public void testGetDeclaredSharedLibraries() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install the first library
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Install the second library
            assertNull(install(STATIC_LIB_PROVIDER2_APK));
            // Install the third library
            assertNull(install(STATIC_LIB_PROVIDER4_APK));
            // Install the first client
            assertNull(install(STATIC_LIB_CONSUMER1_APK));
            // Install the second client
            assertNull(install(STATIC_LIB_CONSUMER2_APK));
            // Ensure declared libraries are properly reported
            runDeviceTests(STATIC_LIB_CONSUMER1_PKG,
                    "android.os.lib.consumer1.UseSharedLibraryTest",
                    "testDeclaredSharedLibrariesProperlyReported");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @AppModeInstant
    @Test
    public void testAppCanSeeOnlyLibrariesItDependOnInstantMode() throws Exception {
        mInstantMode = true;
        doTestAppCanSeeOnlyLibrariesItDependOn();
    }

    @AppModeFull
    @Test
    public void testAppCanSeeOnlyLibrariesItDependOnFullMode() throws Exception {
        doTestAppCanSeeOnlyLibrariesItDependOn();
    }

    private void doTestAppCanSeeOnlyLibrariesItDependOn() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install the first library
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Install the second library
            assertNull(install(STATIC_LIB_PROVIDER2_APK));
            // Install the client
            assertNull(install(STATIC_LIB_CONSUMER1_APK));
            // Ensure the client can see only the lib it depends on
            runDeviceTests(STATIC_LIB_CONSUMER1_PKG,
                    "android.os.lib.consumer1.UseSharedLibraryTest",
                    "testAppCanSeeOnlyLibrariesItDependOn");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @AppModeInstant
    @Test
    public void testLoadCodeFromNativeLibInstantMode() throws Exception {
        mInstantMode = true;
        doTestLoadCodeFromNativeLib();
    }

    @AppModeFull
    @Test
    public void testLoadCodeFromNativeLibFullMode() throws Exception {
        doTestLoadCodeFromNativeLib();
    }

    private void doTestLoadCodeFromNativeLib() throws Exception {
        try {
            // Install library
            assertThat(installPackageWithCurrentAbi(STATIC_LIB_NATIVE_PROVIDER_APK)).isNull();
            // Install the library client
            final String result = installPackageWithCurrentAbi(STATIC_LIB_NATIVE_CONSUMER_APK);
            if (result != null) {
                assumeFalse("The test consumer APK does not include the matched ABI library",
                        result.contains("INSTALL_FAILED_NO_MATCHING_ABIS"));
                // The test app can't be installed with the other errors
                fail("The test consumer APK can not be installed on the device: " + result);
            }
            // Ensure the client can load native code from the library
            runDeviceTests(STATIC_LIB_NATIVE_CONSUMER_PKG,
                    "android.os.lib.consumer.UseSharedLibraryTest",
                    "testLoadNativeCode");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_NATIVE_CONSUMER_PKG);
            getDevice().uninstallPackage(STATIC_LIB_NATIVE_PROVIDER_PKG);
        }
    }

    private String installPackageWithCurrentAbi(String apkName) throws Exception {
        return getDevice().installPackage(
                mBuildHelper.getTestFile(apkName),
                /* reinstall= */ false, /* grantPermissions= */ false,
                /* extraArgs= */ "--abi " + getAbi().getName());
    }

    @AppModeInstant
    @Test
    public void testLoadCodeFromNativeLibMultiArchViolationInstantMode() throws Exception {
        mInstantMode = true;
        doTestLoadCodeFromNativeLibMultiArchViolation();
    }

    @AppModeFull
    @Test
    public void testLoadCodeFromNativeLibMultiArchViolationFullMode() throws Exception {
        doTestLoadCodeFromNativeLibMultiArchViolation();
    }

    private void doTestLoadCodeFromNativeLibMultiArchViolation() throws Exception {
        try {
            // Cannot install the library with native code if not multi-arch
            assertNotNull(install(STATIC_LIB_NATIVE_PROVIDER_APK1));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_NATIVE_PROVIDER_PKG1);
        }
    }

    @AppModeInstant
    @Test
    public void testLoadCodeAndResourcesFromSharedLibrarySignedWithTwoCertsInstantMode() throws Exception {
        mInstantMode = true;
        doTestLoadCodeAndResourcesFromSharedLibrarySignedWithTwoCerts();
    }

    @AppModeFull
    @Test
    public void testLoadCodeAndResourcesFromSharedLibrarySignedWithTwoCertsFullMode() throws Exception {
        doTestLoadCodeAndResourcesFromSharedLibrarySignedWithTwoCerts();
    }

    private void doTestLoadCodeAndResourcesFromSharedLibrarySignedWithTwoCerts()
            throws Exception {
        try {
            // Install the library
            assertNull(install(STATIC_LIB_PROVIDER7_APK));
            // Install the client
            assertNull(install(STATIC_LIB_CONSUMER3_APK));
            // Try to load code and resources
            runDeviceTests(STATIC_LIB_CONSUMER3_PKG,
                    "android.os.lib.consumer3.UseSharedLibraryTest",
                    "testLoadCodeAndResources");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER3_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER7_PKG);
        }
    }

    @Test
    public void testSamegradeStaticSharedLibByAdb() throws Exception {
        try {
            assertNull(install(STATIC_LIB_PROVIDER5_APK));
            assertNull(install(STATIC_LIB_PROVIDER5_APK, true /*reinstall*/));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER5_PKG);
        }
    }

    @AppModeFull(reason = "Instant app cannot get package installer service")
    @Test
    public void testCannotSamegradeStaticSharedLibByInstaller() throws Exception {
        runDeviceTests(STATIC_LIB_TEST_APP_PKG, STATIC_LIB_TEST_APP_CLASS_NAME,
                "testSamegradeStaticSharedLibFail");
    }

    @LargeTest
    @AppModeFull
    @Test
    public void testPruneUnusedStaticSharedLibrariesWithMultiUser_reboot_fullMode()
            throws Exception {
        final int maxUserCount = getDevice().getMaxNumberOfUsersSupported();
        assumeTrue("The device does not support multi-user", maxUserCount > 1);

        boolean shouldCreateSecondUser = true;
        // Check whether the current user count on the device is not less than the max user count or
        // not. If yes, don't create the other user.
        final int currentUserCount = getDevice().listUsers().size();
        if (currentUserCount >= maxUserCount) {
            String message = String.format("Current user count %s is not less than the max user"
                    + " count %s, don't create the other user.", currentUserCount, maxUserCount);
            LogUtil.CLog.logAndDisplay(Log.LogLevel.INFO, message);
            shouldCreateSecondUser = false;
        }


        doTestPruneUnusedStaticSharedLibrariesWithMultiUser_reboot(shouldCreateSecondUser);
    }

    @LargeTest
    @AppModeInstant
    @Test
    public void testPruneUnusedStaticSharedLibrariesWithMultiUser_reboot_instantMode()
            throws Exception {
        // This really should be a assumeTrue(getDevice().getMaxNumberOfUsersSupported() > 1), but
        // JUnit3 doesn't support assumptions framework.
        // TODO: change to assumeTrue after migrating tests to JUnit4.
        final int maxUserCount = getDevice().getMaxNumberOfUsersSupported();
        if (!(maxUserCount > 1)) {
            LogUtil.CLog.logAndDisplay(Log.LogLevel.INFO, "The device does not support multi-user");
            return;
        }

        boolean shouldCreateSecondUser = true;
        // Check whether the current user count on the device is not less than the max user count or
        // not. If yes, don't create the other user.
        final int currentUserCount = getDevice().listUsers().size();
        if (currentUserCount >= maxUserCount) {
            String message = String.format("Current user count %s is not less than the max user"
                    + " count %s, don't create the other user.", currentUserCount, maxUserCount);
            LogUtil.CLog.logAndDisplay(Log.LogLevel.INFO, message);
            shouldCreateSecondUser = false;
        }

        mInstantMode = true;
        doTestPruneUnusedStaticSharedLibrariesWithMultiUser_reboot(shouldCreateSecondUser);
    }

    private void doTestPruneUnusedStaticSharedLibrariesWithMultiUser_reboot(
            boolean shouldCreateSecondUser) throws Exception {
        int userId = -1;
        try {
            if (shouldCreateSecondUser) {
                userId = createAndStartSecondUser();
                assertThat(userId).isNotEqualTo(-1);
            }
            doTestPruneUnusedStaticSharedLibraries_reboot();
        } finally {
            if (shouldCreateSecondUser) {
                stopAndRemoveUser(userId);
            }
        }
    }

    @LargeTest
    @AppModeFull
    @Test
    public void testPruneUnusedStaticSharedLibraries_reboot_fullMode()
            throws Exception {
        doTestPruneUnusedStaticSharedLibraries_reboot();
    }

    @LargeTest
    @AppModeInstant
    @Test
    public void testPruneUnusedStaticSharedLibraries_reboot_instantMode()
            throws Exception {
        mInstantMode = true;
        doTestPruneUnusedStaticSharedLibraries_reboot();
    }

    private void doTestPruneUnusedStaticSharedLibraries_reboot()
            throws Exception {
        try {
            // Install an unused library
            assertThat(install(STATIC_LIB_PROVIDER_RECURSIVE_APK)).isNull();
            assertThat(checkLibrary(STATIC_LIB_PROVIDER_RECURSIVE_NAME)).isTrue();

            // Install the client and the corresponding library
            assertThat(install(STATIC_LIB_PROVIDER7_APK)).isNull();
            assertThat(install(STATIC_LIB_CONSUMER3_APK)).isNull();
            assertThat(checkLibrary(STATIC_LIB_PROVIDER_NAME)).isTrue();

            // Disallow to cache static shared library
            setGlobalSetting(SETTING_UNUSED_STATIC_SHARED_LIB_MIN_CACHE_PERIOD,
                    Integer.toString(0));

            // TODO(205779832): There's a maximum two-seconds-delay before SettingsProvider persists
            //  the settings. Waits for 3 seconds before reboot the device to ensure the setting is
            //  persisted.
            RunUtil.getDefault().sleep(3_000);
            getDevice().reboot();

            // Waits for the uninstallation of the unused library to ensure the job has be executed
            // correctly.
            PollingCheck.check("Library " + STATIC_LIB_PROVIDER_RECURSIVE_NAME
                            + " should be uninstalled", DEFAULT_TIMEOUT_MILLIS,
                    () -> !checkLibrary(STATIC_LIB_PROVIDER_RECURSIVE_NAME));
            assertWithMessage(
                    "Library " + STATIC_LIB_PROVIDER_NAME + " should not be uninstalled")
                    .that(checkLibrary(STATIC_LIB_PROVIDER_NAME)).isTrue();
        } finally {
            setGlobalSetting(SETTING_UNUSED_STATIC_SHARED_LIB_MIN_CACHE_PERIOD, null);
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER3_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER7_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @LargeTest
    @AppModeFull
    @Test
    public void testInstallStaticSharedLib_notKillDependentApp() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install the first library
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Install the client
            assertNull(install(STATIC_LIB_CONSUMER1_APK));

            // Bind the service in consumer1 app to verify that the app should not be killed when
            // a new version static shared library installed.
            runDeviceTests(STATIC_LIB_TEST_APP_PKG, STATIC_LIB_TEST_APP_CLASS_NAME,
                    "testInstallStaticSharedLib_notKillDependentApp");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @AppModeFull
    @Test
    public void testSamegradeStaticSharedLib_killDependentApp() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install the first library
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Install the client
            assertNull(install(STATIC_LIB_CONSUMER1_APK));

            // Bind the service in consumer1 app to verify that the app should be killed when
            // the static shared library is re-installed.
            runDeviceTests(STATIC_LIB_TEST_APP_PKG, STATIC_LIB_TEST_APP_CLASS_NAME,
                    "testSamegradeStaticSharedLib_killDependentApp");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    @AppModeFull
    @Test
    public void testStaticSharedLibInstall_broadcastReceived() throws Exception {
        // Install library dependency
        assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
        runDeviceTests(STATIC_LIB_TEST_APP_PKG, STATIC_LIB_TEST_APP_CLASS_NAME,
                    "testStaticSharedLibInstall_broadcastReceived");
    }

    @AppModeFull
    @Test
    public void testStaticSharedLibInstall_incorrectInstallerPkgName_broadcastNotReceived()
            throws Exception {
        // Install library dependency
        assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
        runDeviceTests(STATIC_LIB_TEST_APP_PKG, STATIC_LIB_TEST_APP_CLASS_NAME,
                "testStaticSharedLibInstall_incorrectInstallerPkgName_broadcastNotReceived");
    }

    @AppModeFull
    @Test
    public void testStaticSharedLibUninstall_broadcastReceived()
            throws Exception {
        // Install library dependency
        assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
        runDeviceTests(STATIC_LIB_TEST_APP_PKG, STATIC_LIB_TEST_APP_CLASS_NAME,
                "testStaticSharedLibUninstall_broadcastReceived");
    }

    @AppModeFull
    @Test
    public void testStaticSharedLibUninstall_incorrectInstallerPkgName_broadcastNotReceived()
            throws Exception {
        // Install library dependency
        assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
        runDeviceTests(STATIC_LIB_TEST_APP_PKG, STATIC_LIB_TEST_APP_CLASS_NAME,
                "testStaticSharedLibUninstall_incorrectInstallerPkgName_broadcastNotReceived");
    }

    @AppModeFull
    @Test
    public void testStaticSharedLibInstallOnSecondaryUser_broadcastReceivedByAllUsers()
            throws Exception {

        runDeviceTests(STATIC_LIB_TEST_APP_PKG, STATIC_LIB_MULTI_USER_TEST_APP_CLASS_NAME,
                "testStaticSharedLibInstallOnSecondaryUser_broadcastReceivedByAllUsers");
    }

    @AppModeFull
    @Test
    public void testStaticSharedLibUninstallOnAllUsers_broadcastReceivedByAllUsers()
            throws Exception {

        runDeviceTests(STATIC_LIB_TEST_APP_PKG, STATIC_LIB_MULTI_USER_TEST_APP_CLASS_NAME,
                "testStaticSharedLibUninstallOnAllUsers_broadcastReceivedByAllUsers");
    }

    @AppModeFull
    @Test
    public void testCannotInstallAppWithBadCertDigestDeclared() throws Exception {
        try {
            // Install library dependency
            assertNull(install(STATIC_LIB_PROVIDER_RECURSIVE_APK));
            // Install the first library
            assertNull(install(STATIC_LIB_PROVIDER1_APK));
            // Failed to install app with bad certificate digest
            assertThat(install(STATIC_LIB_CONSUMER1_BAD_CERT_DIGEST_APK))
                    .contains("INSTALL_FAILED_SHARED_LIBRARY_BAD_CERTIFICATE_DIGEST");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    private String install(String apk) throws DeviceNotAvailableException, FileNotFoundException {
        return install(apk, false);
    }
    private String install(String apk, boolean reinstall)
            throws DeviceNotAvailableException, FileNotFoundException {
        return getDevice().installPackage(mBuildHelper.getTestFile(apk), reinstall, false,
                apk.contains("consumer") && mInstantMode ? "--instant" : "");
    }

    private void assertNull(Object object) {
        assertThat(object).isNull();
    }

    private void assertNotNull(Object object) {
        assertThat(object).isNotNull();
    }

    private boolean checkLibrary(String libName) throws DeviceNotAvailableException {
        final CommandResult result = getDevice().executeShellV2Command("pm list libraries");
        if (result.getStatus() != CommandStatus.SUCCESS) {
            fail("Failed to execute shell command: pm list libraries");
        }
        return Arrays.stream(result.getStdout().split("\n"))
                .map(line -> line.split(":")[1])
                .collect(Collectors.toList()).contains(libName);
    }

    private void setGlobalSetting(String key, String value) throws DeviceNotAvailableException {
        final boolean deleteKey = (value == null);
        final StringBuilder cmd = new StringBuilder("settings ");
        if (deleteKey) {
            cmd.append("delete ");
        } else {
            cmd.append("put ");
        }
        cmd.append("global ").append(key);
        if (!deleteKey) {
            cmd.append(" ").append(value);
        }
        final CommandResult res = getDevice().executeShellV2Command(cmd.toString());
        if (res.getStatus() != CommandStatus.SUCCESS) {
            fail("Failed to execute shell command: " + cmd);
        }
    }

    private int createAndStartSecondUser() throws Exception {
        String output = getDevice().executeShellCommand("pm create-user SecondUser");
        assertThat(output.startsWith("Success")).isTrue();
        int userId = Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
        output = getDevice().executeShellCommand("am start-user -w " + userId);
        assertThat(output.startsWith("Error")).isFalse();
        output = getDevice().executeShellCommand("am get-started-user-state " + userId);
        assertThat(output.contains("RUNNING_UNLOCKED")).isTrue();
        return userId;
    }

    private void stopAndRemoveUser(int userId) throws Exception {
        getDevice().executeShellCommand("am stop-user -w -f " + userId);
        getDevice().executeShellCommand("pm remove-user " + userId);
    }
}
