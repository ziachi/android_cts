/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.compilation.cts;

import static android.compilation.cts.Utils.CompilationArtifacts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.compilation.cts.annotation.CtsTestCase;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceParameterizedRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.Pair;

import junitparams.Parameters;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Compilation tests that don't require root access.
 */
@RunWith(DeviceParameterizedRunner.class)
@CtsTestCase
public class CompilationTest extends BaseHostJUnit4Test {
    private static final String STATUS_CHECKER_PKG = "android.compilation.cts.statuscheckerapp";
    private static final String STATUS_CHECKER_CLASS =
            "android.compilation.cts.statuscheckerapp.StatusCheckerAppTest";
    private static final String STATUS_CHECKER_APK_RES = "/StatusCheckerApp.apk";
    private static final String STATUS_CHECKER_PROF_RES = "/StatusCheckerApp.prof";
    private static final String TEST_APP_PKG = "android.compilation.cts";
    private static final String TEST_APP_APK_RES = "/CtsCompilationApp.apk";
    private static final String TEST_APP_PROF_RES = "/CtsCompilationApp.prof";
    private static final String TEST_APP_DM_RES = "/CtsCompilationApp.dm";
    private static final String TEST_APP_WITH_GOOD_PROFILE_RES =
            "/CtsCompilationApp_with_good_profile.apk";
    private static final String TEST_APP_WITH_BAD_PROFILE_RES =
            "/CtsCompilationApp_with_bad_profile.apk";
    private static final String TEST_APP_DEBUGGABLE_RES = "/CtsCompilationApp_debuggable.apk";
    private static final String TEST_APP_2_PKG = "android.compilation.cts.appusedbyotherapp";
    private static final String TEST_APP_2_APK_RES = "/AppUsedByOtherApp.apk";
    private static final String TEST_APP_2_DM_RES = "/AppUsedByOtherApp_1.dm";
    private static final String TEST_APP_2_DISABLE_EMBEDDED_PROFILE_DM_RES =
            "/AppUsedByOtherApp_1_disable_embedded_profile.dm";
    private static final String DISABLE_EMBEDDED_PROFILE_DM_RES = "/disable_embedded_profile.dm";
    private static final String EMPTY_CONFIG_DM_RES = "/empty_config.dm";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    private Utils mUtils;

    @Before
    public void setUp() throws Exception {
        mUtils = new Utils(getTestInformation());
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_APP_PKG);
        getDevice().uninstallPackage(TEST_APP_2_PKG);
        getDevice().uninstallPackage(STATUS_CHECKER_PKG);
    }

    @Test
    public void testCompile() throws Exception {
        mUtils.installFromResources(getAbi(), STATUS_CHECKER_APK_RES);

        var options = new DeviceTestRunOptions(STATUS_CHECKER_PKG)
                              .setTestClassName(STATUS_CHECKER_CLASS)
                              .setTestMethodName("checkStatus")
                              .setDisableHiddenApiCheck(true);

        mUtils.assertCommandSucceeds("pm compile -m speed -f " + STATUS_CHECKER_PKG);
        options.addInstrumentationArg("compiler-filter", "speed")
                .addInstrumentationArg("compilation-reason", "cmdline")
                .addInstrumentationArg("is-verified", "true")
                .addInstrumentationArg("is-optimized", "true")
                .addInstrumentationArg("is-fully-compiled", "true");
        assertThat(runDeviceTests(options)).isTrue();

        mUtils.assertCommandSucceeds("pm compile -m verify -f " + STATUS_CHECKER_PKG);
        options.addInstrumentationArg("compiler-filter", "verify")
                .addInstrumentationArg("compilation-reason", "cmdline")
                .addInstrumentationArg("is-verified", "true")
                .addInstrumentationArg("is-optimized", "false")
                .addInstrumentationArg("is-fully-compiled", "false");
        assertThat(runDeviceTests(options)).isTrue();

        mUtils.assertCommandSucceeds("pm delete-dexopt " + STATUS_CHECKER_PKG);
        options.addInstrumentationArg("compiler-filter", "run-from-apk")
                .addInstrumentationArg("compilation-reason", "unknown")
                .addInstrumentationArg("is-verified", "false")
                .addInstrumentationArg("is-optimized", "false")
                .addInstrumentationArg("is-fully-compiled", "false");
        assertThat(runDeviceTests(options)).isTrue();
    }

    // TODO(b/258223472): Remove this test once ART Service is the only dexopt implementation.
    @Test
    public void testArtService() throws Exception {
        assertThat(getDevice().getProperty("dalvik.vm.useartservice")).isEqualTo("true");

        mUtils.installFromResources(getAbi(), TEST_APP_APK_RES, TEST_APP_DM_RES);

        // Clear all user data, including the profile.
        mUtils.assertCommandSucceeds("pm clear " + TEST_APP_PKG);

        // Overwrite the artifacts compiled with the profile.
        mUtils.assertCommandSucceeds("pm compile -m verify -f " + TEST_APP_PKG);

        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        assertThat(dump).contains("[status=verify]");
        assertThat(dump).doesNotContain("[status=speed-profile]");

        dump = mUtils.assertCommandSucceeds("dumpsys package " + TEST_APP_PKG);
        assertThat(dump).contains("[status=verify]");
        assertThat(dump).doesNotContain("[status=speed-profile]");

        // Compile the app. It should use the profile in the DM file.
        mUtils.assertCommandSucceeds("pm compile -m speed-profile " + TEST_APP_PKG);

        dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        assertThat(dump).doesNotContain("[status=verify]");
        assertThat(dump).contains("[status=speed-profile]");

        dump = mUtils.assertCommandSucceeds("dumpsys package " + TEST_APP_PKG);
        assertThat(dump).doesNotContain("[status=verify]");
        assertThat(dump).contains("[status=speed-profile]");
    }

    @Test
    @Parameters({"secondary.jar", "secondary"})
    public void testCompileSecondaryDex(String filename) throws Exception {
        mUtils.installFromResources(getAbi(), STATUS_CHECKER_APK_RES);

        var options = new DeviceTestRunOptions(STATUS_CHECKER_PKG)
                              .setTestClassName(STATUS_CHECKER_CLASS)
                              .setTestMethodName("createAndLoadSecondaryDex")
                              .addInstrumentationArg("secondary-dex-filename", filename);
        assertThat(runDeviceTests(options)).isTrue();

        // Verify that the secondary dex file is recorded.
        String dump = mUtils.assertCommandSucceeds("dumpsys package " + STATUS_CHECKER_PKG);
        checkDexoptStatus(dump, Pattern.quote(filename), ".*?");

        mUtils.assertCommandSucceeds(
                "pm compile --secondary-dex -m speed -f " + STATUS_CHECKER_PKG);
        dump = mUtils.assertCommandSucceeds("dumpsys package " + STATUS_CHECKER_PKG);
        checkDexoptStatus(dump, Pattern.quote(filename), "speed");

        mUtils.assertCommandSucceeds(
                "pm compile --secondary-dex -m verify -f " + STATUS_CHECKER_PKG);
        dump = mUtils.assertCommandSucceeds("dumpsys package " + STATUS_CHECKER_PKG);
        checkDexoptStatus(dump, Pattern.quote(filename), "verify");

        mUtils.assertCommandSucceeds("pm delete-dexopt " + STATUS_CHECKER_PKG);
        dump = mUtils.assertCommandSucceeds("dumpsys package " + STATUS_CHECKER_PKG);
        checkDexoptStatus(dump, Pattern.quote(filename), "run-from-apk");
    }

    @Test
    public void testCompileSecondaryDexUnsupportedClassLoader() throws Exception {
        mUtils.installFromResources(getAbi(), STATUS_CHECKER_APK_RES);

        String filename = "secondary-unsupported-clc.jar";
        var options = new DeviceTestRunOptions(STATUS_CHECKER_PKG)
                              .setTestClassName(STATUS_CHECKER_CLASS)
                              .setTestMethodName("createAndLoadSecondaryDexUnsupportedClassLoader")
                              .addInstrumentationArg("secondary-dex-filename", filename);
        assertThat(runDeviceTests(options)).isTrue();

        // "speed" should be downgraded to "verify" because the CLC is unsupported.
        mUtils.assertCommandSucceeds(
                "pm compile --secondary-dex -m speed -f " + STATUS_CHECKER_PKG);
        String dump = mUtils.assertCommandSucceeds("dumpsys package " + STATUS_CHECKER_PKG);
        checkDexoptStatus(dump, Pattern.quote(filename), "verify");
    }

    @Test
    public void testSecondaryDexReporting() throws Exception {
        mUtils.installFromResources(getAbi(), STATUS_CHECKER_APK_RES);

        var options = new DeviceTestRunOptions(STATUS_CHECKER_PKG)
                              .setTestClassName(STATUS_CHECKER_CLASS)
                              .setTestMethodName("testSecondaryDexReporting")
                              .setDisableHiddenApiCheck(true);
        assertThat(runDeviceTests(options)).isTrue();

        String dump = mUtils.assertCommandSucceeds("dumpsys package " + STATUS_CHECKER_PKG);
        Utils.dumpDoesNotContainDexFile(dump, "reported_bad_1.apk");
        Utils.dumpDoesNotContainDexFile(dump, "reported_bad_2.apk");
        Utils.dumpDoesNotContainDexFile(dump, "reported_bad_3.apk");
        Utils.dumpDoesNotContainDexFile(dump, "reported_bad_4.apk");
        Utils.dumpContainsDexFile(dump, "reported_good_1.apk");
        Utils.dumpContainsDexFile(dump, "reported_good_2.apk");
        Utils.dumpContainsDexFile(dump, "reported_good_3.apk");

        // Check that ART Service doesn't crash on various operations after invalid dex paths and
        // class loader contexts are reported.
        mUtils.assertCommandSucceeds(
                "pm compile --secondary-dex -m verify -f " + STATUS_CHECKER_PKG);
        mUtils.assertCommandSucceeds("pm art clear-app-profiles " + STATUS_CHECKER_PKG);
        mUtils.assertCommandSucceeds("pm art cleanup");
    }

    @Test
    public void testGetDexFileOutputPaths() throws Exception {
        mUtils.installFromResources(getAbi(), STATUS_CHECKER_APK_RES);

        mUtils.assertCommandSucceeds("pm compile -m verify -f " + STATUS_CHECKER_PKG);

        var options = new DeviceTestRunOptions(STATUS_CHECKER_PKG)
                              .setTestClassName(STATUS_CHECKER_CLASS)
                              .setTestMethodName("testGetDexFileOutputPaths")
                              .setDisableHiddenApiCheck(true);
        assertThat(runDeviceTests(options)).isTrue();
    }

    @Test
    public void testExternalProfileValidationOk() throws Exception {
        mUtils.installFromResources(getAbi(), TEST_APP_APK_RES, TEST_APP_DM_RES);
    }

    /** Verifies that adb install-multiple fails when the APK and the DM file don't match. */
    @Test
    public void testExternalProfileValidationFailed() throws Exception {
        Throwable throwable = assertThrows(Throwable.class, () -> {
            mUtils.installFromResources(getAbi(), TEST_APP_APK_RES, TEST_APP_2_DM_RES);
        });
        assertThat(throwable).hasMessageThat().contains(
                "Error occurred during dexopt when processing external profiles:");
    }

    @Test
    public void testExternalProfileValidationMultiPackageOk() throws Exception {
        mUtils.installFromResourcesMultiPackage(getAbi(),
                List.of(List.of(Pair.create(TEST_APP_APK_RES, TEST_APP_DM_RES)),
                        List.of(Pair.create(TEST_APP_2_APK_RES, TEST_APP_2_DM_RES))));
    }

    /**
     * Verifies that adb install-multi-package fails when the mismatch happens on one of the APK-DM
     * pairs.
     */
    @Test
    public void testExternalProfileValidationMultiPackageFailed() throws Exception {
        Throwable throwable = assertThrows(Throwable.class, () -> {
            mUtils.installFromResourcesMultiPackage(getAbi(),
                    List.of(List.of(Pair.create(TEST_APP_APK_RES, TEST_APP_DM_RES)),
                            List.of(Pair.create(TEST_APP_2_APK_RES, TEST_APP_DM_RES))));
        });

        assertThat(Utils.countSubstringOccurrence(throwable.getMessage(),
                           "Error occurred during dexopt when processing external profiles:"))
                .isEqualTo(1);
    }

    @Test
    public void testEmbeddedProfileOk() throws Exception {
        mUtils.installFromResources(getAbi(), TEST_APP_WITH_GOOD_PROFILE_RES);
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "speed-profile");
    }

    @Test
    public void testEmbeddedProfileFailed() throws Exception {
        Throwable throwable = assertThrows(Throwable.class,
                () -> { mUtils.installFromResources(getAbi(), TEST_APP_WITH_BAD_PROFILE_RES); });
        assertThat(throwable).hasMessageThat().contains(
                "Error occurred during dexopt when processing external profiles:");
    }

    @Test
    public void testEmbeddedProfileEmptyConfig() throws Exception {
        // A DM with a config file is provided, but it's empty, so it should have no impact on the
        // embedded profile.
        mUtils.installFromResources(getAbi(), TEST_APP_WITH_GOOD_PROFILE_RES, EMPTY_CONFIG_DM_RES);
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "speed-profile");
    }

    @Test
    public void testEmbeddedProfileConfigDisabledByConfig() throws Exception {
        // A DM with a config file is provided, and it disables embedded profile.
        mUtils.installFromResources(
                getAbi(), TEST_APP_WITH_GOOD_PROFILE_RES, DISABLE_EMBEDDED_PROFILE_DM_RES);
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "verify");
    }

    /**
     * Verifies that adb install-multi-package fails with multiple error messages when multiple
     * APK-DM mismatches happen.
     */
    @Test
    public void testExternalProfileValidationMultiPackageFailedMultipleErrors() throws Exception {
        Throwable throwable = assertThrows(Throwable.class, () -> {
            mUtils.installFromResourcesMultiPackage(getAbi(),
                    List.of(List.of(Pair.create(TEST_APP_APK_RES, TEST_APP_2_DM_RES)),
                            List.of(Pair.create(TEST_APP_2_APK_RES, TEST_APP_DM_RES))));
        });

        assertThat(Utils.countSubstringOccurrence(throwable.getMessage(),
                           "Error occurred during dexopt when processing external profiles:"))
                .isEqualTo(2);
    }

    @Test
    public void testIgnoreDexoptProfile() throws Exception {
        // Both the APK and the DM have a good profile, but ART Service should use none of them.
        mUtils.installFromResourcesWithArgs(getAbi(), List.of("--ignore-dexopt-profile"),
                List.of(Pair.create(TEST_APP_WITH_GOOD_PROFILE_RES, TEST_APP_DM_RES)));
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "verify");
    }

    @Test
    public void testIgnoreDexoptProfileNoValidation() throws Exception {
        // Both the APK and the DM have a bad profile, but ART Service should not complain.
        mUtils.installFromResourcesWithArgs(getAbi(), List.of("--ignore-dexopt-profile"),
                List.of(Pair.create(TEST_APP_WITH_BAD_PROFILE_RES, TEST_APP_2_DM_RES)));
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "verify");
    }

    @Test
    public void testFallBackToEmbeddedProfile() throws Exception {
        // The DM has a bad profile, so ART Service should fall back to the embedded profile.
        assertThrows(Throwable.class, () -> {
            mUtils.installFromResources(
                    getAbi(), TEST_APP_WITH_GOOD_PROFILE_RES, TEST_APP_2_DM_RES);
        });
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "speed-profile");
    }

    @Test
    public void testNoFallBackToEmbeddedProfile() throws Exception {
        // The DM has a bad profile, but it also has a config that disables embedded profile, so ART
        // Service should not fall back to the embedded profile.
        assertThrows(Throwable.class, () -> {
            mUtils.installFromResources(getAbi(), TEST_APP_WITH_GOOD_PROFILE_RES,
                    TEST_APP_2_DISABLE_EMBEDDED_PROFILE_DM_RES);
        });
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "verify");
    }

    @Test
    public void testInstallCompilerFilterDefault() throws Exception {
        assumeTrue(getDevice().getProperty("pm.dexopt.install").equals("speed-profile"));

        mUtils.installFromResources(getAbi(), TEST_APP_WITH_GOOD_PROFILE_RES);
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "speed-profile");
    }

    @Test
    public void testInstallCompilerFilterDebuggable() throws Exception {
        mUtils.installFromResources(getAbi(), TEST_APP_DEBUGGABLE_RES);
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "run-from-apk");
    }

    @Test
    public void testInstallCompilerFilterOverride() throws Exception {
        mUtils.installFromResourcesWithArgs(getAbi(), List.of("--dexopt-compiler-filter", "verify"),
                List.of(Pair.create(TEST_APP_DEBUGGABLE_RES, null)));
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "verify");
    }

    @Test
    public void testInstallCompilerFilterOverrideSkip() throws Exception {
        mUtils.installFromResourcesWithArgs(getAbi(), List.of("--dexopt-compiler-filter", "skip"),
                List.of(Pair.create(TEST_APP_WITH_GOOD_PROFILE_RES, null)));
        String dump = mUtils.assertCommandSucceeds("pm art dump " + TEST_APP_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "run-from-apk");
    }

    @Test
    public void testInstallCompilerFilterOverrideInvalid() throws Exception {
        Throwable throwable = assertThrows(Throwable.class, () -> {
            mUtils.installFromResourcesWithArgs(getAbi(),
                    List.of("--dexopt-compiler-filter", "bogus"),
                    List.of(Pair.create(TEST_APP_WITH_GOOD_PROFILE_RES, null)));
        });

        assertThat(throwable.getMessage()).contains("Invalid compiler filter");
    }

    @Test
    @RequiresFlagsEnabled({android.content.pm.Flags.FLAG_CLOUD_COMPILATION_PM,
            com.android.art.flags.Flags.FLAG_ART_SERVICE_V3})
    public void testSdmOk() throws Exception {
        CompilationArtifacts artifacts = generateStatusCheckerCompilationArtifacts();
        File dmFile = mUtils.createDm(STATUS_CHECKER_PROF_RES, artifacts.vdexFile());
        File sdmFile = mUtils.createSdm(artifacts.odexFile(), artifacts.artFile(), "testkey");

        mUtils.installFromResourcesWithSdm(getAbi(), STATUS_CHECKER_APK_RES, dmFile, sdmFile);

        String dump = mUtils.assertCommandSucceeds("pm art dump " + STATUS_CHECKER_PKG);
        checkDexoptStatus(dump, Pattern.quote("base.apk"), "speed-profile", "cloud");

        var checkStatusOptions = new DeviceTestRunOptions(STATUS_CHECKER_PKG)
                                         .setTestClassName(STATUS_CHECKER_CLASS)
                                         .setTestMethodName("checkStatus")
                                         .setDisableHiddenApiCheck(true)
                                         .addInstrumentationArg("compiler-filter", "speed-profile")
                                         .addInstrumentationArg("compilation-reason", "cloud");
        assertThat(runDeviceTests(checkStatusOptions)).isTrue();

        // Further make sure that the native code is from the SDM file.
        var checkExecutableMethodFileOffsetsOptions =
                new DeviceTestRunOptions(STATUS_CHECKER_PKG)
                        .setTestClassName(STATUS_CHECKER_CLASS)
                        .setTestMethodName("checkExecutableMethodFileOffsets")
                        .setDisableHiddenApiCheck(true)
                        .addInstrumentationArg("container-path-pattern", "\\.sdm!/primary\\.odex$");
        assertThat(runDeviceTests(checkExecutableMethodFileOffsetsOptions)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled({android.content.pm.Flags.FLAG_CLOUD_COMPILATION_VERIFICATION})
    public void testSdmInvalidSignature() throws Exception {
        CompilationArtifacts artifacts = generateStatusCheckerCompilationArtifacts();
        File dmFile = mUtils.createDm(STATUS_CHECKER_PROF_RES, artifacts.vdexFile());
        File sdmFile = mUtils.createSdm(artifacts.odexFile(), artifacts.artFile(), "testkey2");

        Throwable throwable = assertThrows(Throwable.class, () -> {
            mUtils.installFromResourcesWithSdm(getAbi(), STATUS_CHECKER_APK_RES, dmFile, sdmFile);
        });
        assertThat(throwable).hasMessageThat().contains("SDM signatures are inconsistent with APK");
    }

    @Test
    @RequiresFlagsEnabled({android.content.pm.Flags.FLAG_CLOUD_COMPILATION_VERIFICATION})
    public void testSdmNoSignature() throws Exception {
        CompilationArtifacts artifacts = generateStatusCheckerCompilationArtifacts();
        File dmFile = mUtils.createDm(STATUS_CHECKER_PROF_RES, artifacts.vdexFile());
        File sdmFile =
                mUtils.createSdm(artifacts.odexFile(), artifacts.artFile(), null /* keyName */);

        Throwable throwable = assertThrows(Throwable.class, () -> {
            mUtils.installFromResourcesWithSdm(getAbi(), STATUS_CHECKER_APK_RES, dmFile, sdmFile);
        });
        assertThat(throwable).hasMessageThat().contains("Failed to verify SDM signatures");
    }

    private void checkDexoptStatus(String dump, String dexfilePattern, String statusPattern) {
        checkDexoptStatus(dump, dexfilePattern, statusPattern, "[-a-z]*");
    }

    private void checkDexoptStatus(
            String dump, String dexfilePattern, String statusPattern, String reasonPattern) {
        // Matches the dump output typically being:
        //     /data/user/0/android.compilation.cts.statuscheckerapp/secondary.jar
        //       x86_64: [status=speed] [reason=cmdline] [primary-abi]
        // The pattern is intentionally minimized to be as forward compatible as possible.
        // TODO(b/283447251): Use a machine-readable format.
        assertThat(dump).containsMatch(Pattern.compile(String.format(
                "[\\s/](%s)(\\s[^\\n]*)?\\n[^\\n]*\\[status=(%s)\\][^\\n]*\\[reason=(%s)\\]",
                dexfilePattern, statusPattern, reasonPattern)));
    }

    private CompilationArtifacts generateStatusCheckerCompilationArtifacts() throws Exception {
        // Keep the class loader context in sync with `libs` of `StatusCheckerApp` in
        // `cts/hostsidetests/compilation/status_checker_app/Android.bp`.
        String classLoaderContext = "PCL[]{PCL[/system/framework/android.test.base.jar]}";
        return mUtils.generateCompilationArtifacts(
                STATUS_CHECKER_APK_RES, STATUS_CHECKER_PROF_RES, getAbi(), classLoaderContext);
    }
}
