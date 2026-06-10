/*
 * Copyright 2022 The Android Open Source Project
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

package android.media.router.cts;

import static android.media.cts.MediaRouterTestConstants.DEVICE_SIDE_TEST_CLASS;
import static android.media.cts.MediaRouterTestConstants.DEVICE_SIDE_TEST_CLASS_WITH_MODIFY_AUDIO_ROUTING;
import static android.media.cts.MediaRouterTestConstants.DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_APK;
import static android.media.cts.MediaRouterTestConstants.DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_CLASS;
import static android.media.cts.MediaRouterTestConstants.DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_2_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_2_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_3_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_3_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_REQUIRES_PERMISSIONS_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_REQUIRES_PERMISSIONS_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_SELF_SCAN_ONLY_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_SELF_SCAN_ONLY_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_TEST_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_TEST_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_APK;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_1_1;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_2_1;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_2_2;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_3_1;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_3_3;

import static com.android.tradefed.targetprep.UserHelper.getRunTestsAsUser;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.ApiTest;
import com.android.media.flags.Flags;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

/** Installs route provider apps and runs tests in {@link MediaRouter2DeviceTest}. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class MediaRouter2HostSideTest extends BaseHostJUnit4Test {

    private static final String[] ROUTE_PROVIDER_PACKAGES = {
        MEDIA_ROUTER_PROVIDER_1_PACKAGE,
        MEDIA_ROUTER_PROVIDER_2_PACKAGE,
        MEDIA_ROUTER_PROVIDER_3_PACKAGE,
        MEDIA_ROUTER_PROVIDER_SELF_SCAN_ONLY_PACKAGE
    };

    /** The maximum period of time to wait for a scan request to take effect, in milliseconds. */
    private static final long WAIT_MS_SCAN_PROPAGATION = 3000;

    @Rule public final Expect expect = Expect.create();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    private int mUserId;

    @BeforeClassWithInfo
    public static void installApps(TestInformation testInfo)
            throws DeviceNotAvailableException, FileNotFoundException {
        installTestApp(testInfo, MEDIA_ROUTER_PROVIDER_1_APK);
        installTestApp(testInfo, MEDIA_ROUTER_PROVIDER_2_APK);
        installTestApp(testInfo, MEDIA_ROUTER_PROVIDER_3_APK);
        installTestApp(testInfo, MEDIA_ROUTER_PROVIDER_SELF_SCAN_ONLY_APK);
        installTestApp(testInfo, MEDIA_ROUTER_PROVIDER_REQUIRES_PERMISSIONS_APK);
        installTestApp(testInfo, MEDIA_ROUTER_TEST_APK);
        installTestApp(testInfo, MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_APK);
        installTestApp(testInfo, DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_APK);
    }

    @AfterClassWithInfo
    public static void uninstallApps(TestInformation testInfo) throws DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        device.uninstallPackage(MEDIA_ROUTER_PROVIDER_1_PACKAGE);
        device.uninstallPackage(MEDIA_ROUTER_PROVIDER_2_PACKAGE);
        device.uninstallPackage(MEDIA_ROUTER_PROVIDER_3_PACKAGE);
        device.uninstallPackage(MEDIA_ROUTER_PROVIDER_SELF_SCAN_ONLY_PACKAGE);
        device.uninstallPackage(MEDIA_ROUTER_PROVIDER_REQUIRES_PERMISSIONS_PACKAGE);
        device.uninstallPackage(MEDIA_ROUTER_TEST_PACKAGE);
        device.uninstallPackage(MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_PACKAGE);
        device.uninstallPackage(DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE);
    }

    @Before
    public void setUp() throws Throwable {
        // We must kill previously bound route providers to avoid unrelated scan requests
        // interfering with tests.
        forceStopAllRouteProviders();

        // Set the userId that the tests are running as. Fall back to the current user if not set.
        TestInformation testInfo = getTestInformation();
        mUserId = testInfo == null ? getDevice().getCurrentUser() : getRunTestsAsUser(testInfo);
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getSystemController_withModifyAudioRouting_returnsDeviceRoute() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_PACKAGE,
                DEVICE_SIDE_TEST_CLASS_WITH_MODIFY_AUDIO_ROUTING,
                "getSystemController_withModifyAudioRouting_returnsDeviceRoute");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void requestScan_withOffScreenScan_triggersScanning() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "requestScan_withOffScreenScan_triggersScanning");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void requestScan_withOnScreenScan_triggersScanning() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "requestScan_withOnScreenScan_triggersScanning");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void requestScan_withOnScreenScan_withScreenOff_doesNotScan() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "requestScan_withOnScreenScan_withScreenOff_doesNotScan");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void getRoutes_withModifyAudioRouting_returnsDeviceRoute() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_WITH_MODIFY_AUDIO_ROUTING_PACKAGE,
                DEVICE_SIDE_TEST_CLASS_WITH_MODIFY_AUDIO_ROUTING,
                "getRoutes_withModifyAudioRouting_returnsDeviceRoute");
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void testDeduplicationIds_propagateAcrossApps() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "deduplicationIds_propagateAcrossApps");
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void testDeviceType_propagatesAcrossApps() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "deviceType_propagatesAcrossApps");
    }

    @ApiTest(apis = {"android.media.RouteListingPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void testSetRouteListingPreference_propagatesToManager() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "setRouteListingPreference_propagatesToManager");
    }

    @AppModeFull
    @RequiresDevice
    @Test
    public void testSetInstance_findsExternalPackage() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "getInstance_findsExternalPackage");
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void testVisibilityAndAllowedPackages_propagateAcrossApps() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "visibilityAndAllowedPackages_propagateAcrossApps");
    }

    @ApiTest(apis = {"android.media.MediaRoute2Info.Builder#setRequiredPermissions(Set)"})
    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ROUTE_VISIBILITY_CONTROL_API)
    @Test
    public void testRequiredPermissions_routeVisibleWhenOnePermissionIsHeld() throws Exception {
        revokeAllPermissions(DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE);
        setPermissionEnabled(
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE,
                "android.permission.POST_NOTIFICATIONS",
                true,
                mUserId);
        runDeviceTests(
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE,
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_CLASS,
                "requiredPermissions_routeVisibleWhenOnePermissionIsHeld");
    }

    @ApiTest(apis = {"android.media.MediaRoute2Info.Builder#setRequiredPermissions(Set)"})
    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ROUTE_VISIBILITY_CONTROL_API)
    @Test
    public void testRequiredPermissions_routeNotVisibleWhenOnePermissionNotHeld()
            throws Exception {
        revokeAllPermissions(DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE);
        runDeviceTests(
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE,
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_CLASS,
                "requiredPermissions_routeNotVisibleWhenOnePermissionNotHeld");
    }

    @ApiTest(apis = {"android.media.MediaRoute2Info.Builder#setRequiredPermissions(Set)"})
    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ROUTE_VISIBILITY_CONTROL_API)
    @Test
    public void testRequiredPermissions_routeNotVisibleWhenNoEntryInAnySetIsHeld()
            throws Exception {
        revokeAllPermissions(DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE);
        runDeviceTests(
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE,
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_CLASS,
                "requiredPermissions_routeNotVisibleWhenNoEntryInAnySetIsHeld");
    }

    @ApiTest(apis = {"android.media.MediaRoute2Info.Builder#setRequiredPermissions(Set)"})
    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ROUTE_VISIBILITY_CONTROL_API)
    @Test
    public void testRequiredPermissions_routeVisibleWhenFirstSetInListIsHeld() throws Exception {
        revokeAllPermissions(DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE);
        setPermissionEnabled(
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE,
                REQUIRED_PERMISSIONS_SET_1_1,
                true,
                mUserId);
        runDeviceTests(
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE,
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_CLASS,
                "requiredPermissions_routeVisibleWhenFirstSetInListIsHeld");
    }

    @ApiTest(apis = {"android.media.MediaRoute2Info.Builder#setRequiredPermissions(Set)"})
    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ROUTE_VISIBILITY_CONTROL_API)
    @Test
    public void testRequiredPermissions_routeVisibleWhenSecondSetInListIsHeld() throws Exception {
        revokeAllPermissions(DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE);
        setPermissionEnabled(
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE,
                REQUIRED_PERMISSIONS_SET_2_1,
                true,
                mUserId);
        setPermissionEnabled(
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE,
                REQUIRED_PERMISSIONS_SET_2_2,
                true,
                mUserId);
        runDeviceTests(
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE,
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_CLASS,
                "requiredPermissions_routeVisibleWhenSecondSetInListIsHeld");
    }

    @ApiTest(apis = {"android.media.MediaRoute2Info.Builder#setRequiredPermissions(Set)"})
    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ROUTE_VISIBILITY_CONTROL_API)
    @Test
    public void testRequiredPermissions_routeNotVisibleWhenSecondOfThirdSetIsNotHeld()
            throws Exception {
        revokeAllPermissions(DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE);
        setPermissionEnabled(
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE,
                REQUIRED_PERMISSIONS_SET_3_1,
                true,
                mUserId);
        setPermissionEnabled(
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE,
                REQUIRED_PERMISSIONS_SET_3_3,
                true,
                mUserId);
        runDeviceTests(
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_PACKAGE,
                DEVICE_SIDE_TEST_REQUIRED_PERMISSIONS_CLASS,
                "requiredPermissions_routeNotVisibleWhenSecondOfThirdSetIsNotHeld");
    }

    @ApiTest(apis = {"android.media.MediaRoute2Info#getProviderPackageName"})
    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MEDIA_ROUTE_2_INFO_PROVIDER_PACKAGE_NAME)
    @Test
    public void testGetProviderPackageName_propagatesCorrectlyFromProvider() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "getProviderPackageName_propagatesCorrectlyFromProvider");
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void setRouteListingPreference_withCustomDisableReason_propagatesCorrectly()
            throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "setRouteListingPreference_withCustomDisableReason_propagatesCorrectly");
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void newRouteListingPreference_withInvalidCustomSubtext_throws() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "newRouteListingPreference_withInvalidCustomSubtext_throws");
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void getRoutes_returnsExpectedSystemRoutes_dependingOnPermissions() throws Exception {
        // Bluetooth permissions must be manually granted.
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_SCAN",
                /* enabled= */ true,
                mUserId);
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_CONNECT",
                /* enabled= */ true,
                mUserId);
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "getRoutes_withBTPermissions_returnsDeviceRoute");
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "getSystemController_withBTPermissions_returnsDeviceRoute");
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_SCAN",
                /* enabled= */ false,
                mUserId);
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_CONNECT",
                /* enabled= */ false,
                mUserId);
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "getRoutes_withoutBTPermissions_returnsDefaultRoute");
    }

    @AppModeFull
    @RequiresDevice
    @Test
    public void getSystemController_withBTPermissions_returnsDeviceRoute() throws Exception {
        // Bluetooth permissions must be manually granted.
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_SCAN",
                /* enabled= */ true,
                mUserId);
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_CONNECT",
                /* enabled= */ true,
                mUserId);
        try {
            runDeviceTests(
                    MEDIA_ROUTER_TEST_PACKAGE,
                    DEVICE_SIDE_TEST_CLASS,
                    "getSystemController_withBTPermissions_returnsDeviceRoute");
        } finally {
            setPermissionEnabled(
                    MEDIA_ROUTER_TEST_PACKAGE,
                    "android.permission.BLUETOOTH_SCAN",
                    /* enabled= */ false,
                    mUserId);
            setPermissionEnabled(
                    MEDIA_ROUTER_TEST_PACKAGE,
                    "android.permission.BLUETOOTH_CONNECT",
                    /* enabled= */ false,
                    mUserId);
        }
    }

    @AppModeFull
    @RequiresDevice
    @Test
    public void getSystemController_withoutBTPermissions_returnsDefaultRoute() throws Exception {
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_SCAN",
                /* enabled= */ false,
                mUserId);
        setPermissionEnabled(
                MEDIA_ROUTER_TEST_PACKAGE,
                "android.permission.BLUETOOTH_CONNECT",
                /* enabled= */ false,
                mUserId);
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "getSystemController_withoutBTPermissions_returnsDefaultRoute");
    }

    @ApiTest(apis = {"android.media.MediaRouter2"})
    @AppModeFull
    @RequiresDevice
    @Test
    public void selfScanOnlyProvider_notScannedByAnotherApp() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "selfScanOnlyProvider_notScannedByAnotherApp");
    }

    @ApiTest(apis = {"android.media.MediaRouter2ProviderService#onBind"})
    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PREVENTION_OF_KEEP_ALIVE_ROUTE_PROVIDERS)
    @Test
    public void providerService_doesNotAutoBindAfterCrashing() throws Throwable {
        try {
            // We should make sure that the route provider isn't running to avoid race conditions
            // with the provider process's lifecycle.
            assertWithMessage("Setup failed. Provider must not be running before test is run.")
                    .that(forceStopAndWaitForRunningStatus(MEDIA_ROUTER_PROVIDER_1_PACKAGE))
                    .isTrue();

            String startActivityCommand =
                    "am start %s/.ScanningActivity".formatted(MEDIA_ROUTER_TEST_PACKAGE);
            getDevice().executeShellCommand(startActivityCommand);

            boolean providerStarted =
                    waitForPackageRunningStatus(
                            MEDIA_ROUTER_PROVIDER_1_PACKAGE, /* isPackageExpectedToRun= */ true);
            assertWithMessage("Provider did not start after starting the scanning activity.")
                    .that(providerStarted)
                    .isTrue();

            getDevice().executeShellCommand("am force-stop " + MEDIA_ROUTER_PROVIDER_1_PACKAGE);

            boolean providerStopped =
                    waitForPackageRunningStatus(
                            MEDIA_ROUTER_PROVIDER_1_PACKAGE, /* isPackageExpectedToRun= */ false);
            assertWithMessage("Provider did not stop after force-stopping it.")
                    .that(providerStopped)
                    .isTrue();

            boolean providerRestarted =
                    waitForPackageRunningStatus(
                            MEDIA_ROUTER_PROVIDER_1_PACKAGE, /* isPackageExpectedToRun= */ true);
            assertWithMessage("Provider restarted after force-stopping it.")
                    .that(providerRestarted)
                    .isFalse();
        } finally {
            expect.that(forceStopAndWaitForRunningStatus(MEDIA_ROUTER_TEST_PACKAGE)).isTrue();
        }
    }

    @ApiTest(apis = {"android.media.MediaRouter2ProviderService#onBind"})
    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PREVENTION_OF_KEEP_ALIVE_ROUTE_PROVIDERS)
    @Test
    public void packageManagerSpammingProviderService_doesNotAutoBindAfterCrashing()
            throws Throwable {
        // Note that this apk is not installed with the other apks in this test, and this should not
        // change. This apk messes up with the proxy watcher by spamming PACKAGE_CHANGED events, so
        // we avoid having it installed while running other tests.
        installTestApp(getTestInformation(), MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_APK);

        try {
            String startActivityCommand =
                    "am start %s/.ScanningActivity".formatted(MEDIA_ROUTER_TEST_PACKAGE);
            getDevice().executeShellCommand(startActivityCommand);

            boolean providerStarted =
                    waitForPackageRunningStatus(
                            MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_PACKAGE,
                            /* isPackageExpectedToRun= */ true);
            assertWithMessage("Provider did not start after starting the scanning activity.")
                    .that(providerStarted)
                    .isTrue();

            getDevice()
                    .executeShellCommand(
                            "am force-stop "
                                    + MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_PACKAGE);

            boolean providerStopped =
                    waitForPackageRunningStatus(
                            MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_PACKAGE,
                            /* isPackageExpectedToRun= */ false);
            assertWithMessage("Provider did not stop after force-stopping it.")
                    .that(providerStopped)
                    .isTrue();

            boolean providerRestarted =
                    waitForPackageRunningStatus(
                            MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_PACKAGE,
                            /* isPackageExpectedToRun= */ true);
            assertWithMessage("Provider restarted after force-stopping it.")
                    .that(providerRestarted)
                    .isFalse();
        } finally {
            expect.that(uninstallPackage(MEDIA_ROUTER_PROVIDER_WITH_PACKAGE_MANAGER_SPAM_PACKAGE))
                    .isNull();
            expect.that(forceStopAndWaitForRunningStatus(MEDIA_ROUTER_TEST_PACKAGE)).isTrue();
        }
    }

    @AppModeFull
    @RequiresDevice
    @Test
    public void activeScanRouteDiscoveryPreference_scansOnSelfScanProvider() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "activeScanRouteDiscoveryPreference_scansOnSelfScanProvider");
    }

    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PREVENTION_OF_MANAGER_SCANS_WHEN_NO_APPS_SCAN)
    @Test
    public void managerScan_withNoAppsScanning_doesNotWakeUpProvider() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "managerScan_withNoAppsScanning_doesNotWakeUpProvider");
    }

    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING,
        Flags.FLAG_ENABLE_FULL_SCAN_WITH_MEDIA_CONTENT_CONTROL
    })
    @Test
    public void screenOffScan_onLocalRouter_allowedWithMediaContentControl() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "screenOffScan_onLocalRouter_allowedWithMediaContentControl");
    }

    @AppModeFull
    @RequiresDevice
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING,
        Flags.FLAG_ENABLE_FULL_SCAN_WITH_MEDIA_CONTENT_CONTROL
    })
    @Test
    public void screenOffScan_onProxyRouter_allowedWithMediaContentControl() throws Exception {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "screenOffScan_onProxyRouter_allowedWithMediaContentControl");
    }

    @Test
    @AppModeFull
    @RequiresDevice
    public void requestScan_screenOff_withoutMediaRoutingControl_throwsSecurityException()
            throws DeviceNotAvailableException {
        runDeviceTests(
                MEDIA_ROUTER_TEST_PACKAGE,
                DEVICE_SIDE_TEST_CLASS,
                "requestScan_screenOff_withoutMediaRoutingControl_throwsSecurityException");
    }

    private void revokeAllPermissions(String packageName) throws DeviceNotAvailableException {
        setPermissionEnabled(
                packageName,
                /* permission= */ null,
                /* enabled= */ false,
                mUserId,
                /* allPermissions= */ true);
    }

    private void setPermissionEnabled(
            String packageName, String permission, boolean enabled, int userId)
            throws DeviceNotAvailableException {
        setPermissionEnabled(packageName, permission, enabled, userId, /* allPermissions= */ false);
    }

    private void setPermissionEnabled(
            String packageName,
            String permission,
            boolean enabled,
            int userId,
            boolean allPermissions)
            throws DeviceNotAvailableException {
        String action = enabled ? "grant" : "revoke";
        String allPermissionsFlag = "";
        if (permission == null) {
            if (!allPermissions) {
                throw new IllegalArgumentException(
                        "permission should not be null unless intending to operate on all "
                                + "permissions");
            } else {
                allPermissionsFlag = "--all-permissions ";
            }
            permission = "";
        }

        String result =
                getDevice()
                        .executeShellCommand(
                                "pm %s --user %d %s%s %s"
                                        .formatted(
                                                action,
                                                userId,
                                                allPermissionsFlag,
                                                packageName,
                                                permission));
        if (!result.isEmpty()) {
            assertWithMessage("Setting permission %s failed: %s".formatted(permission, result))
                    .fail();
        }
    }

    private void forceStopAllRouteProviders() throws Throwable {
        for (String providerPackage : ROUTE_PROVIDER_PACKAGES) {
            assertThat(forceStopAndWaitForRunningStatus(providerPackage)).isTrue();
        }
    }

    private boolean forceStopAndWaitForRunningStatus(String packageName) throws Throwable {
        getDevice().executeShellCommand("am force-stop " + packageName);
        return waitForPackageRunningStatus(
                MEDIA_ROUTER_TEST_PACKAGE, /* isPackageExpectedToRun= */ false);
    }

    /**
     * Blocks execution until the package with the given name has the given running status.
     *
     * @param packageName The name of the package to check the running status for.
     * @param isPackageExpectedToRun True if the expected running status is "running", and false if
     *     the expected running status is "not running".
     */
    private boolean waitForPackageRunningStatus(String packageName, boolean isPackageExpectedToRun)
            throws Throwable {
        long start = System.currentTimeMillis();
        while (isPackageRunning(packageName) != isPackageExpectedToRun) {
            if (System.currentTimeMillis() - start > WAIT_MS_SCAN_PROPAGATION) {
                return false;
            }
            Thread.sleep(/* millis= */ 200); // Wait a bit before we call adb again.
        }
        return true;
    }

    private boolean isPackageRunning(String packageName) throws DeviceNotAvailableException {
        return !getDevice().executeShellCommand("pidof " + packageName).isEmpty();
    }

    private static void installTestApp(TestInformation testInfo, String apkName)
            throws FileNotFoundException, DeviceNotAvailableException {
        LogUtil.CLog.d("Installing app " + apkName);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(
                testInfo.getBuildInfo());
        final String result = testInfo.getDevice().installPackage(
                buildHelper.getTestFile(apkName), /*reinstall=*/true, /*grantPermissions=*/true,
                /*allow test apps*/"-t");
        assertWithMessage("Failed to install " + apkName + ": " + result).that(result).isNull();
    }
}
