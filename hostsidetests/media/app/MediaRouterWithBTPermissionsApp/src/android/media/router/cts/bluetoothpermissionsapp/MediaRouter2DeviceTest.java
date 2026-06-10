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

package android.media.router.cts.bluetoothpermissionsapp;

import static android.media.MediaRoute2Info.FEATURE_LIVE_AUDIO;
import static android.media.MediaRoute2Info.FEATURE_LIVE_VIDEO;
import static android.media.cts.MediaRouterTestConstants.FEATURE_SAMPLE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_2_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_3_PACKAGE;
import static android.media.cts.MediaRouterTestConstants.ROUTE_DEDUPLICATION_ID_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_DEDUPLICATION_ID_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_DEDUPLICATION_ID_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_1_ROUTE_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_1_ROUTE_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_1_ROUTE_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_1_ROUTE_4;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_2_ROUTE_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_2_ROUTE_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_2_ROUTE_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_2_ROUTE_4;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_3_ROUTE_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_3_ROUTE_2;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_3_ROUTE_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_3_ROUTE_4;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_3_ROUTE_5;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_SELF_SCAN_ONLY;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_4;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_5;
import static android.media.cts.app.common.MediaRouter2TestUtils.ROUTE_UPDATE_MAX_WAIT_MS;
import static android.media.cts.app.common.MediaRouter2TestUtils.waitForAndGetRoutes;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRoute2Info;
import android.media.MediaRoute2ProviderService;
import android.media.MediaRouter2;
import android.media.MediaRouter2.ScanRequest;
import android.media.MediaRouter2.ScanToken;
import android.media.MediaRouter2Manager;
import android.media.RouteDiscoveryPreference;
import android.media.RouteListingPreference;
import android.media.cts.app.common.MediaRouter2TestUtils;
import android.media.cts.app.common.PlaceholderSelfScanMediaRoute2ProviderService;
import android.os.ConditionVariable;
import android.os.PowerManager;
import android.os.SystemClock;
import android.platform.test.annotations.LargeTest;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.media.flags.Flags;

import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Device-side tests for regular {@link MediaRouter2} functionality.
 *
 * <p>These tests must run as host-side tests to ensure test cases are isolated and no {@link
 * MediaRouter2} state is shared across tests. Host-side tests create a new process for each test
 * method. See {@code MediaRouter2HostSideTest} for more information.
 */
@LargeTest
public class MediaRouter2DeviceTest {

    /** {@link RouteDiscoveryPreference} for system routes only. */
    private static final RouteDiscoveryPreference SYSTEM_ROUTE_DISCOVERY_PREFERENCE =
            new RouteDiscoveryPreference.Builder(
                            List.of(FEATURE_LIVE_AUDIO, FEATURE_LIVE_VIDEO),
                            /* activeScan= */ false)
                    .build();

    private static final Correspondence<MediaRoute2Info, String> ROUTE_HAS_ORIGINAL_ID =
            Correspondence.transforming(MediaRoute2Info::getOriginalId, "has original id");

    // TODO: b/316864909 - Stop relying on route ids once we can control system routing in CTS.
    private static final String ROUTE_ID_BUILTIN_SPEAKER =
            Flags.enableAudioPoliciesDeviceAndBluetoothController()
                    ? "ROUTE_ID_BUILTIN_SPEAKER"
                    : MediaRoute2Info.ROUTE_ID_DEVICE;
    private static final int TIMEOUT_MS = 5000;

    private ExecutorService mExecutor;
    private Context mContext;
    private ComponentName mPlaceholderComponentName;
    private Activity mScreenOnActivity;
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mExecutor = Executors.newSingleThreadExecutor();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mPlaceholderComponentName = new ComponentName(mContext, PlaceholderActivity.class);
    }

    private void launchScreenOnActivity() {
        mScreenOnActivity = MediaRouter2TestUtils.launchScreenOnActivity(mContext);
    }

    @After
    public void tearDown() {
        mUiAutomation.dropShellPermissionIdentity();

        // mScreenOnActivity may be null if we failed to launch the activity. The NPE would not
        // change the outcome of the test, but it would misdirect attention, away from the root
        // cause of the failure.
        if (mScreenOnActivity != null) {
            mScreenOnActivity.finish();
        }

        mExecutor.shutdown();
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @Test
    public void deduplicationIds_propagateAcrossApps() throws TimeoutException {
        launchScreenOnActivity();
        MediaRouter2 router = MediaRouter2.getInstance(mContext);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        // Each app exposes all its routes in a single operation, so if we find one route per app,
        // we know the update should contain all the routes from each helper app.
        Map<String, MediaRoute2Info> routes =
                waitForAndGetRoutes(
                        router,
                        preference,
                        Set.of(
                                ROUTE_ID_APP_1_ROUTE_1,
                                ROUTE_ID_APP_2_ROUTE_1,
                                ROUTE_ID_APP_3_ROUTE_1),
                        mExecutor);

        Truth.assertThat(routes.get(ROUTE_ID_APP_1_ROUTE_1).getDeduplicationIds()).isEmpty();
        Truth.assertThat(routes.get(ROUTE_ID_APP_1_ROUTE_2).getDeduplicationIds())
                .isEqualTo(Set.of(ROUTE_DEDUPLICATION_ID_1));
        Truth.assertThat(routes.get(ROUTE_ID_APP_1_ROUTE_3).getDeduplicationIds())
                .isEqualTo(Set.of(ROUTE_DEDUPLICATION_ID_2));
        Truth.assertThat(routes.get(ROUTE_ID_APP_2_ROUTE_1).getDeduplicationIds()).isEmpty();
        Truth.assertThat(routes.get(ROUTE_ID_APP_2_ROUTE_2).getDeduplicationIds())
                .isEqualTo(Set.of(ROUTE_DEDUPLICATION_ID_1));
        Truth.assertThat(routes.get(ROUTE_ID_APP_2_ROUTE_3).getDeduplicationIds())
                .isEqualTo(Set.of(ROUTE_DEDUPLICATION_ID_3));
        Truth.assertThat(routes.get(ROUTE_ID_APP_3_ROUTE_1).getDeduplicationIds()).isEmpty();
        Truth.assertThat(routes.get(ROUTE_ID_APP_3_ROUTE_2).getDeduplicationIds())
                .isEqualTo(Set.of(ROUTE_DEDUPLICATION_ID_2));
        Truth.assertThat(routes.get(ROUTE_ID_APP_3_ROUTE_3).getDeduplicationIds())
                .isEqualTo(
                        Set.of(
                                ROUTE_DEDUPLICATION_ID_1,
                                ROUTE_DEDUPLICATION_ID_2,
                                ROUTE_DEDUPLICATION_ID_3));
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @Test
    public void deviceType_propagatesAcrossApps() throws TimeoutException {
        launchScreenOnActivity();
        MediaRouter2 router = MediaRouter2.getInstance(mContext);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        Map<String, MediaRoute2Info> routes =
                waitForAndGetRoutes(
                        router,
                        preference,
                        Set.of(
                                ROUTE_ID_APP_1_ROUTE_1,
                                ROUTE_ID_APP_2_ROUTE_1,
                                ROUTE_ID_APP_3_ROUTE_1),
                        mExecutor);
        Truth.assertThat(routes.get(ROUTE_ID_APP_1_ROUTE_1).getType())
                .isEqualTo(MediaRoute2Info.TYPE_REMOTE_TV);
        Truth.assertThat(routes.get(ROUTE_ID_APP_1_ROUTE_2).getType())
                .isEqualTo(MediaRoute2Info.TYPE_UNKNOWN);
        Truth.assertThat(routes.get(ROUTE_ID_APP_2_ROUTE_1).getType())
                .isEqualTo(MediaRoute2Info.TYPE_REMOTE_SPEAKER);
        Truth.assertThat(routes.get(ROUTE_ID_APP_3_ROUTE_1).getType())
                .isEqualTo(MediaRoute2Info.TYPE_REMOTE_AUDIO_VIDEO_RECEIVER);
        // Verify the default value is TYPE_UNKNOWN:
        Truth.assertThat(routes.get(ROUTE_ID_APP_3_ROUTE_5).getType())
                .isEqualTo(MediaRoute2Info.TYPE_UNKNOWN);
    }

    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING,
        Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL
    })
    @Test
    public void requestScan_withOffScreenScan_triggersScanning() throws InterruptedException {
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_ROUTING_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);

        CountDownLatch latch = new CountDownLatch(1);
        MediaRouter2.RouteCallback routeCallback =
                new MediaRouter2.RouteCallback() {
                    @Override
                    public void onRoutesUpdated(@NonNull List<MediaRoute2Info> routes) {
                        if (routes.stream()
                                .anyMatch(r -> r.getFeatures().contains(FEATURE_SAMPLE))) {
                            latch.countDown();
                        }
                    }
                };

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ false)
                        .build();

        MediaRouter2 instance = MediaRouter2.getInstance(mContext);
        instance.registerRouteCallback(mExecutor, routeCallback, preference);

        ScanToken token =
                instance.requestScan(new ScanRequest.Builder().setScreenOffScan(true).build());
        try {
            assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            instance.cancelScanRequest(token);
            instance.unregisterRouteCallback(routeCallback);
        }
    }

    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING})
    @Test
    public void requestScan_withOnScreenScan_triggersScanning() throws InterruptedException {
        launchScreenOnActivity();
        CountDownLatch latch = new CountDownLatch(1);
        MediaRouter2.RouteCallback routeCallback =
                new MediaRouter2.RouteCallback() {
                    @Override
                    public void onRoutesUpdated(@NonNull List<MediaRoute2Info> routes) {
                        if (routes.stream()
                                .anyMatch(r -> r.getFeatures().contains(FEATURE_SAMPLE))) {
                            latch.countDown();
                        }
                    }
                };

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ false)
                        .build();

        MediaRouter2 instance = MediaRouter2.getInstance(mContext);
        instance.registerRouteCallback(mExecutor, routeCallback, preference);

        ScanToken token = instance.requestScan(new ScanRequest.Builder().build());
        try {
            assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            instance.cancelScanRequest(token);
            instance.unregisterRouteCallback(routeCallback);
        }
    }

    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING})
    @Test
    public void requestScan_withOnScreenScan_withScreenOff_doesNotScan()
            throws InterruptedException {
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.DEVICE_POWER);

        PowerManager pm = mContext.getSystemService(PowerManager.class);
        pm.goToSleep(SystemClock.uptimeMillis());
        assertThat(pm.isInteractive()).isFalse();

        CountDownLatch latch = new CountDownLatch(1);
        MediaRouter2.RouteCallback routeCallback =
                new MediaRouter2.RouteCallback() {
                    @Override
                    public void onRoutesUpdated(@NonNull List<MediaRoute2Info> routes) {
                        if (routes.stream()
                                .anyMatch(r -> r.getFeatures().contains(FEATURE_SAMPLE))) {
                            latch.countDown();
                        }
                    }
                };

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ false)
                        .build();

        MediaRouter2 instance = MediaRouter2.getInstance(mContext);
        instance.registerRouteCallback(mExecutor, routeCallback, preference);

        ScanToken token = instance.requestScan(new ScanRequest.Builder().build());
        try {
            assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            instance.cancelScanRequest(token);
            instance.unregisterRouteCallback(routeCallback);
        }
    }

    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING,
        Flags.FLAG_ENABLE_FULL_SCAN_WITH_MEDIA_CONTENT_CONTROL
    })
    @Test
    public void screenOffScan_onLocalRouter_allowedWithMediaContentControl()
            throws InterruptedException {
        mUiAutomation.adoptShellPermissionIdentity(
                Manifest.permission.DEVICE_POWER, Manifest.permission.MEDIA_CONTENT_CONTROL);

        PowerManager pm = mContext.getSystemService(PowerManager.class);
        pm.goToSleep(SystemClock.uptimeMillis());
        assertThat(pm.isInteractive()).isFalse();

        CountDownLatch latch = new CountDownLatch(1);
        MediaRouter2.RouteCallback routeCallback =
                new MediaRouter2.RouteCallback() {
                    @Override
                    public void onRoutesUpdated(@NonNull List<MediaRoute2Info> routes) {
                        if (routes.stream()
                                .anyMatch(r -> r.getFeatures().contains(FEATURE_SAMPLE))) {
                            latch.countDown();
                        }
                    }
                };

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ false)
                        .build();

        MediaRouter2 localRouter = MediaRouter2.getInstance(mContext);
        localRouter.registerRouteCallback(mExecutor, routeCallback, preference);

        ScanToken token =
                localRouter.requestScan(new ScanRequest.Builder().setScreenOffScan(true).build());
        try {
            assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            localRouter.cancelScanRequest(token);
            localRouter.unregisterRouteCallback(routeCallback);
        }
    }

    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING,
        Flags.FLAG_ENABLE_FULL_SCAN_WITH_MEDIA_CONTENT_CONTROL
    })
    @Test
    public void screenOffScan_onProxyRouter_allowedWithMediaContentControl()
            throws InterruptedException {
        mUiAutomation.adoptShellPermissionIdentity(
                Manifest.permission.DEVICE_POWER, Manifest.permission.MEDIA_CONTENT_CONTROL);

        PowerManager pm = mContext.getSystemService(PowerManager.class);
        pm.goToSleep(SystemClock.uptimeMillis());
        assertThat(pm.isInteractive()).isFalse();

        CountDownLatch latch = new CountDownLatch(1);
        MediaRouter2.RouteCallback routeCallback =
                new MediaRouter2.RouteCallback() {
                    @Override
                    public void onRoutesUpdated(@NonNull List<MediaRoute2Info> routes) {
                        if (routes.stream()
                                .anyMatch(r -> r.getFeatures().contains(FEATURE_SAMPLE))) {
                            latch.countDown();
                        }
                    }
                };

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ false)
                        .build();

        MediaRouter2 localRouter = MediaRouter2.getInstance(mContext);
        localRouter.registerRouteCallback(mExecutor, routeCallback, preference);

        // MEDIA_CONTENT_CONTROL is available via adoptShellPermissionIdentity.
        @SuppressLint("MissingPermission")
        MediaRouter2 proxyRouter = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        ScanToken token =
                proxyRouter.requestScan(new ScanRequest.Builder().setScreenOffScan(true).build());
        try {
            assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            proxyRouter.cancelScanRequest(token);
            localRouter.unregisterRouteCallback(routeCallback);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING)
    @Test
    public void requestScan_screenOff_withoutMediaRoutingControl_throwsSecurityException() {
        MediaRouter2 localRouter = MediaRouter2.getInstance(mContext);
        assertThat(localRouter).isNotNull();
        assertThrows(
                SecurityException.class,
                () ->
                        localRouter.requestScan(
                                new ScanRequest.Builder().setScreenOffScan(true).build()));
    }

    @ApiTest(apis = {"android.media.RouteListingPreference, android.media.MediaRouter2"})
    @Test
    public void setRouteListingPreference_propagatesToManager() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL);

        MediaRouter2 router = MediaRouter2.getInstance(mContext);
        MediaRouter2Manager manager = MediaRouter2Manager.getInstance(mContext);

        List<RouteListingPreference.Item> items =
                List.of(
                        new RouteListingPreference.Item.Builder(ROUTE_ID_APP_1_ROUTE_1)
                                .setFlags(RouteListingPreference.Item.FLAG_ONGOING_SESSION)
                                .setSubText(RouteListingPreference.Item.SUBTEXT_NONE)
                                .build(),
                        new RouteListingPreference.Item.Builder(ROUTE_ID_APP_2_ROUTE_2)
                                .setFlags(0)
                                .setSubText(RouteListingPreference.Item.SUBTEXT_NONE)
                                .setSelectionBehavior(
                                        RouteListingPreference.Item.SELECTION_BEHAVIOR_NONE)
                                .build(),
                        new RouteListingPreference.Item.Builder(ROUTE_ID_APP_3_ROUTE_3)
                                .setFlags(0)
                                .setSubText(
                                        RouteListingPreference.Item.SUBTEXT_SUBSCRIPTION_REQUIRED)
                                .build());
        RouteListingPreference routeListingPreference =
                new RouteListingPreference.Builder()
                        .setItems(items)
                        .setUseSystemOrdering(false)
                        .setLinkedItemComponentName(mPlaceholderComponentName)
                        .build();
        MediaRouter2ManagerCallbackImpl mediaRouter2ManagerCallback =
                new MediaRouter2ManagerCallbackImpl();
        manager.registerCallback(Runnable::run, mediaRouter2ManagerCallback);
        router.setRouteListingPreference(routeListingPreference);
        mediaRouter2ManagerCallback.waitForRouteListingPreferenceUpdateOnManager();
        RouteListingPreference receivedRouteListingPreference =
                mediaRouter2ManagerCallback.mRouteListingPreference;
        Truth.assertThat(receivedRouteListingPreference).isEqualTo(routeListingPreference);
        Truth.assertThat(receivedRouteListingPreference)
                .isNotSameInstanceAs(routeListingPreference);
        Truth.assertThat(receivedRouteListingPreference)
                .isSameInstanceAs(manager.getRouteListingPreference(mContext.getPackageName()));
        Truth.assertThat(receivedRouteListingPreference.getUseSystemOrdering()).isFalse();
        Truth.assertThat(receivedRouteListingPreference.getItems().get(0).getFlags())
                .isEqualTo(RouteListingPreference.Item.FLAG_ONGOING_SESSION);
        RouteListingPreference.Item secondItem = receivedRouteListingPreference.getItems().get(1);
        Truth.assertThat(secondItem.getSubText())
                .isEqualTo(RouteListingPreference.Item.SUBTEXT_NONE);
        Truth.assertThat(secondItem.getSelectionBehavior())
                .isEqualTo(RouteListingPreference.Item.SELECTION_BEHAVIOR_NONE);
        Truth.assertThat(receivedRouteListingPreference.getItems().get(2).getSubText())
                .isEqualTo(RouteListingPreference.Item.SUBTEXT_SUBSCRIPTION_REQUIRED);
        Truth.assertThat(receivedRouteListingPreference.getLinkedItemComponentName())
                .isEqualTo(mPlaceholderComponentName);

        // Check that null is also propagated correctly.
        mediaRouter2ManagerCallback.closeRouteListingPreferenceWaitingCondition();
        router.setRouteListingPreference(null);
        mediaRouter2ManagerCallback.waitForRouteListingPreferenceUpdateOnManager();
        Truth.assertThat(mediaRouter2ManagerCallback.mRouteListingPreference).isNull();
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_MEDIA_ROUTE_2_INFO_PROVIDER_PACKAGE_NAME)
    @Test
    public void getProviderPackageName_propagatesCorrectlyFromProvider() throws TimeoutException {
        launchScreenOnActivity();
        MediaRouter2 router = MediaRouter2.getInstance(mContext);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        Map<String, MediaRoute2Info> routes =
                waitForAndGetRoutes(
                        router,
                        preference,
                        Set.of(
                                ROUTE_ID_APP_1_ROUTE_1,
                                ROUTE_ID_APP_2_ROUTE_1,
                                ROUTE_ID_APP_3_ROUTE_1),
                        mExecutor);
        Truth.assertThat(routes.get(ROUTE_ID_APP_1_ROUTE_1).getProviderPackageName())
                .isEqualTo(MEDIA_ROUTER_PROVIDER_1_PACKAGE);
        Truth.assertThat(routes.get(ROUTE_ID_APP_2_ROUTE_1).getProviderPackageName())
                .isEqualTo(MEDIA_ROUTER_PROVIDER_2_PACKAGE);
        Truth.assertThat(routes.get(ROUTE_ID_APP_3_ROUTE_1).getProviderPackageName())
                .isEqualTo(MEDIA_ROUTER_PROVIDER_3_PACKAGE);
    }

    @ApiTest(apis = {"android.media.RouteListingPreference, android.media.MediaRouter2"})
    @Test
    public void setRouteListingPreference_withCustomDisableReason_propagatesCorrectly() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL);

        MediaRouter2 router = MediaRouter2.getInstance(mContext);
        MediaRouter2Manager manager = MediaRouter2Manager.getInstance(mContext);

        List<RouteListingPreference.Item> item =
                List.of(
                        new RouteListingPreference.Item.Builder(ROUTE_ID_APP_1_ROUTE_1)
                                .setSubText(RouteListingPreference.Item.SUBTEXT_CUSTOM)
                                .setCustomSubtextMessage("Fake disable reason message")
                                .build());
        RouteListingPreference routeListingPreference =
                new RouteListingPreference.Builder().setItems(item).build();
        MediaRouter2ManagerCallbackImpl mediaRouter2ManagerCallback =
                new MediaRouter2ManagerCallbackImpl();
        manager.registerCallback(Runnable::run, mediaRouter2ManagerCallback);

        router.setRouteListingPreference(routeListingPreference);
        mediaRouter2ManagerCallback.waitForRouteListingPreferenceUpdateOnManager();
        RouteListingPreference receivedRouteListingPreference =
                mediaRouter2ManagerCallback.mRouteListingPreference;
        Truth.assertThat(receivedRouteListingPreference)
                .isNotSameInstanceAs(routeListingPreference);
        Truth.assertThat(receivedRouteListingPreference).isEqualTo(routeListingPreference);
        Truth.assertThat(receivedRouteListingPreference.getItems()).hasSize(1);

        RouteListingPreference.Item receivedItem = receivedRouteListingPreference.getItems().get(0);
        Truth.assertThat(receivedItem.getSubText())
                .isEqualTo(RouteListingPreference.Item.SUBTEXT_CUSTOM);
        Truth.assertThat(receivedItem.getCustomSubtextMessage())
                .isEqualTo("Fake disable reason message");
    }

    @ApiTest(apis = {"android.media.RouteListingPreference"})
    @Test
    public void newRouteListingPreference_withInvalidCustomSubtext_throws() {
        RouteListingPreference.Item.Builder builder =
                new RouteListingPreference.Item.Builder("fake_route_id")
                        .setSubText(RouteListingPreference.Item.SUBTEXT_CUSTOM);
        // Check that the builder throws if DISABLE_REASON_CUSTOM is used, but no disable reason
        // message is provided.
        assertThrows(IllegalArgumentException.class, builder::build);

        // Check that the builder does not throw if we provide a message.
        builder.setCustomSubtextMessage("Fake disable reason message").build();
    }

    @Test
    public void getInstance_findsExternalPackage() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_CONTENT_CONTROL);

        MediaRouter2 systemRouter =
                MediaRouter2.getInstance(mContext, MEDIA_ROUTER_PROVIDER_1_PACKAGE);
        Truth.assertThat(systemRouter).isNotNull();
    }

    @ApiTest(apis = {"android.media.RouteDiscoveryPreference, android.media.MediaRouter2"})
    @Test
    public void visibilityAndAllowedPackages_propagateAcrossApps() throws TimeoutException {
        launchScreenOnActivity();
        MediaRouter2 router = MediaRouter2.getInstance(mContext);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        // Each app exposes all its routes in a single operation, so if we find one route per app,
        // we know the update should contain all the routes from each helper app.
        Map<String, MediaRoute2Info> routes =
                waitForAndGetRoutes(
                        router,
                        preference,
                        Set.of(
                                ROUTE_ID_APP_1_ROUTE_1,
                                ROUTE_ID_APP_2_ROUTE_1,
                                ROUTE_ID_APP_3_ROUTE_1),
                        mExecutor);

        // public route
        Truth.assertThat(routes.get(ROUTE_ID_APP_1_ROUTE_4).getName()).isEqualTo(ROUTE_NAME_4);

        // private route
        Truth.assertThat(routes.get(ROUTE_ID_APP_2_ROUTE_4)).isNull();

        // restricted route with allowed packages not containing app's package
        Truth.assertThat(routes.get(ROUTE_ID_APP_3_ROUTE_4)).isNull();

        // restricted route with allowed packages containing app's package
        Truth.assertThat(routes.get(ROUTE_ID_APP_3_ROUTE_5).getName()).isEqualTo(ROUTE_NAME_5);
    }

    @Test
    public void getRoutes_withoutBTPermissions_returnsDefaultRoute() throws TimeoutException {
        MediaRouter2 router = MediaRouter2.getInstance(mContext);

        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MODIFY_AUDIO_ROUTING))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_SCAN))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_CONNECT))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        assertThat(
                        waitForAndGetRoutes(
                                        router,
                                        SYSTEM_ROUTE_DISCOVERY_PREFERENCE,
                                        /* expectedRouteIds= */ Set.of(
                                                MediaRoute2Info.ROUTE_ID_DEFAULT),
                                        mExecutor)
                                .keySet())
                .containsExactly(MediaRoute2Info.ROUTE_ID_DEFAULT);
    }

    @Test
    public void getRoutes_withBTPermissions_returnsDeviceRoute() throws TimeoutException {
        MediaRouter2 router = MediaRouter2.getInstance(mContext);

        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MODIFY_AUDIO_ROUTING))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_SCAN))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_CONNECT))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);

        assertThat(
                        waitForAndGetRoutes(
                                        router,
                                        SYSTEM_ROUTE_DISCOVERY_PREFERENCE,
                                        /* expectedRouteIds= */ Set.of(ROUTE_ID_BUILTIN_SPEAKER),
                                        mExecutor)
                                .keySet())
                .containsExactly(ROUTE_ID_BUILTIN_SPEAKER);
    }

    @Test
    public void getSystemController_withBTPermissions_returnsDeviceRoute() {
        MediaRouter2 router = MediaRouter2.getInstance(mContext);

        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MODIFY_AUDIO_ROUTING))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_SCAN))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_CONNECT))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);

        MediaRouter2.RoutingController systemController = router.getSystemController();
        assertThat(systemController.getSelectedRoutes())
                .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                .doesNotContain(MediaRoute2Info.ROUTE_ID_DEFAULT);
    }

    @Test
    public void getSystemController_withoutBTPermissions_returnsDefaultRoute() {
        MediaRouter2 router = MediaRouter2.getInstance(mContext);

        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MODIFY_AUDIO_ROUTING))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_SCAN))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.BLUETOOTH_CONNECT))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        MediaRouter2.RoutingController systemController = router.getSystemController();
        assertThat(systemController.getSelectedRoutes())
                .comparingElementsUsing(ROUTE_HAS_ORIGINAL_ID)
                .containsExactly(MediaRoute2Info.ROUTE_ID_DEFAULT);
    }

    @Test
    public void activeScanRouteDiscoveryPreference_scansOnSelfScanProvider() {
        launchScreenOnActivity();
        MediaRouter2 router = MediaRouter2.getInstance(mContext);

        RouteDiscoveryPreference activeScanRouteDiscoveryPreference =
                new RouteDiscoveryPreference.Builder(
                                List.of("placeholder_feature"), /* activeScan= */ true)
                        .build();
        MediaRouter2.RouteCallback routeCallback = new MediaRouter2.RouteCallback() {};

        ConditionVariable onBindConditionVariable = new ConditionVariable();
        ConditionVariable onUnbindConditionVariable = new ConditionVariable();

        PlaceholderSelfScanMediaRoute2ProviderService.setOnBindCallback(
                action -> {
                    if (MediaRoute2ProviderService.SERVICE_INTERFACE.equals(action)) {
                        onBindConditionVariable.open();
                    }
                });
        PlaceholderSelfScanMediaRoute2ProviderService.setOnUnbindCallback(
                action -> {
                    if (MediaRoute2ProviderService.SERVICE_INTERFACE.equals(action)) {
                        onUnbindConditionVariable.open();
                    }
                });
        try {
            router.registerRouteCallback(
                    Runnable::run, routeCallback, activeScanRouteDiscoveryPreference);
            assertThat(onBindConditionVariable.block(TIMEOUT_MS)).isTrue();

            router.unregisterRouteCallback(routeCallback);
            assertThat(onUnbindConditionVariable.block(TIMEOUT_MS)).isTrue();
        } finally {
            PlaceholderSelfScanMediaRoute2ProviderService.setOnBindCallback(action -> {});
            PlaceholderSelfScanMediaRoute2ProviderService.setOnUnbindCallback(action -> {});
            router.unregisterRouteCallback(routeCallback);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PREVENTION_OF_MANAGER_SCANS_WHEN_NO_APPS_SCAN)
    @Test
    public void managerScan_withNoAppsScanning_doesNotWakeUpProvider() {
        // Permission required for the proxy router scan (also known as manager scan).
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);

        // We use self-pointing proxy router to trigger a manager scan.
        MediaRouter2 proxyRouter = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        ConditionVariable conditionVariable = new ConditionVariable();
        PlaceholderSelfScanMediaRoute2ProviderService.setOnBindCallback(
                action -> {
                    if (MediaRoute2ProviderService.SERVICE_INTERFACE.equals(action)) {
                        conditionVariable.open();
                    }
                });
        proxyRouter.startScan();
        try {
            assertThat(conditionVariable.block(TIMEOUT_MS)).isFalse();
        } finally {
            proxyRouter.stopScan();
        }
    }

    @ApiTest(apis = {"android.media.MediaRouter2"})
    @Test
    public void selfScanOnlyProvider_notScannedByAnotherApp() {
        launchScreenOnActivity();
        MediaRouter2 router = MediaRouter2.getInstance(mContext);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                                List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();

        assertThrows(
                TimeoutException.class,
                () ->
                        waitForAndGetRoutes(
                                router,
                                preference,
                                /* expectedRouteIds= */ Set.of(ROUTE_ID_SELF_SCAN_ONLY),
                                mExecutor));
    }

    private class MediaRouter2ManagerCallbackImpl implements MediaRouter2Manager.Callback {

        private final ConditionVariable mConditionVariable = new ConditionVariable();
        private RouteListingPreference mRouteListingPreference;

        public void closeRouteListingPreferenceWaitingCondition() {
            mConditionVariable.close();
        }

        public void waitForRouteListingPreferenceUpdateOnManager() {
            Truth.assertThat(mConditionVariable.block(ROUTE_UPDATE_MAX_WAIT_MS)).isTrue();
        }

        @Override
        public void onRouteListingPreferenceUpdated(
                String packageName, RouteListingPreference routeListingPreference) {
            if (packageName.equals(mContext.getPackageName())) {
                mRouteListingPreference = routeListingPreference;
                mConditionVariable.open();
            }
        }
    }
}
