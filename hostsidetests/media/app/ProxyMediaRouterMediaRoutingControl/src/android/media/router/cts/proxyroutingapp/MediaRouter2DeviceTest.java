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

package android.media.router.cts.proxyroutingapp;

import static android.media.RoutingSessionInfo.TRANSFER_REASON_APP;
import static android.media.RoutingSessionInfo.TRANSFER_REASON_SYSTEM_REQUEST;
import static android.media.cts.MediaRouterTestConstants.FEATURE_SAMPLE;
import static android.media.cts.MediaRouterTestConstants.MEDIA_ROUTER_PROVIDER_1_PACKAGE;

import static com.android.media.flags.Flags.FLAG_ENABLE_BUILT_IN_SPEAKER_ROUTE_SUITABILITY_STATUSES;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRoute2Info;
import android.media.MediaRoute2ProviderService;
import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;
import android.media.RoutingSessionInfo;
import android.media.cts.app.common.PlaceholderSelfScanMediaRoute2ProviderService;
import android.media.cts.app.common.ScreenOnActivity;
import android.os.ConditionVariable;
import android.platform.test.annotations.LargeTest;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.media.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Device-side test for privileged {@link MediaRouter2} functionality. */
@LargeTest
public class MediaRouter2DeviceTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int TIMEOUT_MS = 5000;

    private Instrumentation mInstrumentation;
    private Context mContext;
    private Executor mExecutor;
    private Activity mScreenOnActivity;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();

        if (mScreenOnActivity != null) {
            mScreenOnActivity.finish();
        }
    }

    private void loadScreenOnActivity() {
        // Launch ScreenOnActivity while tests are running for scanning to work. MediaRouter2 blocks
        // app scan requests while the screen is off for resource saving.
        Intent intent = new Intent(/* context= */ mContext, ScreenOnActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mScreenOnActivity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
    }

    @SuppressLint("MissingPermission")
    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL)
    public void getInstance_withMediaRoutingControl_flagDisabled_throwsSecurityException() {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);
        try {
            assertThrows(
                    SecurityException.class,
                    () -> MediaRouter2.getInstance(mContext, MEDIA_ROUTER_PROVIDER_1_PACKAGE));
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @SuppressLint("MissingPermission")
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL)
    public void getInstance_withMediaRoutingControl_flagEnabled_doesNotThrow() {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);
        try {
            MediaRouter2.getInstance(mContext, MEDIA_ROUTER_PROVIDER_1_PACKAGE);
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @SuppressLint("MissingPermission")
    @Test
    public void getInstance_withoutMediaRoutingControl_throwsSecurityException() {
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_ROUTING_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        assertThrows(
                SecurityException.class,
                () -> MediaRouter2.getInstance(mContext, MEDIA_ROUTER_PROVIDER_1_PACKAGE));
    }

    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_CROSS_USER_ROUTING_IN_MEDIA_ROUTER2,
        Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL
    })
    @Test
    public void getInstance_withinUser_withMediaRoutingControl_flagEnabled_returnsInstance() {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);
        try {
            assertThat(
                            MediaRouter2.getInstance(
                                    mContext,
                                    mContext.getPackageName(),
                                    mContext.getUser()))
                    .isNotNull();
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_CROSS_USER_ROUTING_IN_MEDIA_ROUTER2,
        Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL
    })
    @Test
    public void getInstance_withinUser_withoutMediaRoutingControl_throwsSecurityException() {
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_ROUTING_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_CONTENT_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        assertThrows(
                SecurityException.class,
                () ->
                        MediaRouter2.getInstance(
                                mContext,
                                mContext.getPackageName(),
                                mContext.getUser()));
    }

    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING,
        Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL
    })
    @Test
    public void requestScan_withScreenOff_triggersScanning() throws InterruptedException {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);

        MediaRouter2 localInstance = MediaRouter2.getInstance(mContext);
        MediaRouter2.RouteCallback placeholderCallback = new MediaRouter2.RouteCallback() {};
        localInstance.registerRouteCallback(
                mExecutor,
                placeholderCallback,
                new RouteDiscoveryPreference.Builder(List.of(FEATURE_SAMPLE), false).build());

        MediaRouter2 instance = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        assertThat(instance).isNotNull();
        CountDownLatch latch = new CountDownLatch(1);

        MediaRouter2.RouteCallback onRoutesUpdated =
                new MediaRouter2.RouteCallback() {
                    @Override
                    public void onRoutesUpdated(@NonNull List<MediaRoute2Info> routes) {
                        if (routes.stream()
                                .anyMatch(r -> r.getFeatures().contains(FEATURE_SAMPLE))) {
                            latch.countDown();
                        }
                    }
                };

        instance.registerRouteCallback(mExecutor, onRoutesUpdated, RouteDiscoveryPreference.EMPTY);

        MediaRouter2.ScanToken token =
                instance.requestScan(
                        new MediaRouter2.ScanRequest.Builder().setScreenOffScan(true).build());
        try {
            assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            instance.cancelScanRequest(token);
            localInstance.unregisterRouteCallback(placeholderCallback);
            instance.unregisterRouteCallback(onRoutesUpdated);
        }
    }

    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING,
        Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL
    })
    @Test
    public void cancelScanRequest_screenOffScanning_unbindsSelfScanProvider() {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);

        MediaRouter2 localInstance = MediaRouter2.getInstance(mContext);
        MediaRouter2.RouteCallback placeholderCallback = new MediaRouter2.RouteCallback() {};
        localInstance.registerRouteCallback(
                mExecutor,
                placeholderCallback,
                new RouteDiscoveryPreference.Builder(List.of(FEATURE_SAMPLE), false).build());

        MediaRouter2 instance = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        assertThat(instance).isNotNull();

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

        MediaRouter2.ScanToken token =
                instance.requestScan(
                        new MediaRouter2.ScanRequest.Builder().setScreenOffScan(true).build());
        assertThat(onBindConditionVariable.block(TIMEOUT_MS)).isTrue();

        instance.cancelScanRequest(token);
        assertThat(onUnbindConditionVariable.block(TIMEOUT_MS)).isTrue();
    }

    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_SCREEN_OFF_SCANNING,
        Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL
    })
    @Test
    public void cancelScanRequest_multipleTypes_unbindsSelfScanProvider() {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);

        loadScreenOnActivity();

        MediaRouter2 localInstance = MediaRouter2.getInstance(mContext);
        MediaRouter2.RouteCallback placeholderCallback = new MediaRouter2.RouteCallback() {};
        localInstance.registerRouteCallback(
                mExecutor,
                placeholderCallback,
                new RouteDiscoveryPreference.Builder(List.of(FEATURE_SAMPLE), false).build());

        MediaRouter2 instance = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        assertThat(instance).isNotNull();

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

        MediaRouter2.ScanToken screenOffToken =
                instance.requestScan(
                        new MediaRouter2.ScanRequest.Builder().setScreenOffScan(true).build());
        MediaRouter2.ScanToken screenOnToken =
                instance.requestScan(new MediaRouter2.ScanRequest.Builder().build());
        assertThat(onBindConditionVariable.block(TIMEOUT_MS)).isTrue();

        instance.cancelScanRequest(screenOffToken);
        assertThat(onUnbindConditionVariable.block(TIMEOUT_MS)).isFalse();

        instance.cancelScanRequest(screenOnToken);
        assertThat(onUnbindConditionVariable.block(TIMEOUT_MS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_BUILT_IN_SPEAKER_ROUTE_SUITABILITY_STATUSES)
    public void transferToSelectedSystemRoute_updatesTransferReason()
            throws Exception {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);

        CountDownLatch localControllerUpdateLatch = new CountDownLatch(1);
        MediaRouter2.ControllerCallback localControllerCallback =
                new MediaRouter2.ControllerCallback() {
                    @Override
                    public void onControllerUpdated(MediaRouter2.RoutingController controller) {
                        RoutingSessionInfo systemSessionInfo = controller.getRoutingSessionInfo();

                        if (controller.wasTransferInitiatedBySelf()
                                && systemSessionInfo.getTransferReason() == TRANSFER_REASON_APP) {
                            localControllerUpdateLatch.countDown();
                        }
                    }
                };

        CountDownLatch proxyControllerUpdateLatch = new CountDownLatch(1);
        MediaRouter2.ControllerCallback proxyControllerCallback =
                new MediaRouter2.ControllerCallback() {
                    @Override
                    public void onControllerUpdated(MediaRouter2.RoutingController controller) {
                        RoutingSessionInfo systemSessionInfo = controller.getRoutingSessionInfo();

                        if (controller.wasTransferInitiatedBySelf()
                                && systemSessionInfo.getTransferReason()
                                        == RoutingSessionInfo.TRANSFER_REASON_SYSTEM_REQUEST) {
                            proxyControllerUpdateLatch.countDown();
                        }
                    }
                };
        MediaRouter2 localRouter = MediaRouter2.getInstance(mContext);
        // The route callback is necessary for the local router to be registered in the routing
        // service. If there's no callback, the transfer request is ignored due to absence of a
        // router record.
        MediaRouter2.RouteCallback routeCallback = new MediaRouter2.RouteCallback() {};
        localRouter.registerRouteCallback(mExecutor, routeCallback, RouteDiscoveryPreference.EMPTY);

        localRouter.registerControllerCallback(mExecutor, localControllerCallback);
        MediaRouter2.RoutingController localSystemController = localRouter.getSystemController();

        MediaRouter2 proxyRouter = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        proxyRouter.registerControllerCallback(mExecutor, proxyControllerCallback);
        MediaRouter2.RoutingController proxySystemController = proxyRouter.getSystemController();

        try {
            localRouter.transferTo(localSystemController.getSelectedRoutes().get(0));
            // We cannot assert this await because we don't know that the previous state was (the
            // event could be swallowed because the transfer didn't introduce any routing session
            // changes).
            localControllerUpdateLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(localSystemController.wasTransferInitiatedBySelf()).isTrue();
            assertThat(localSystemController.getRoutingSessionInfo().getTransferReason())
                    .isEqualTo(TRANSFER_REASON_APP);

            while (proxySystemController.getSelectedRoutes().isEmpty()) {
                // TODO b/339583417 - Remove this busy wait once we fix the underlying bug in proxy
                // routers.
                Thread.sleep(/* millis= */ 500);
            }
            proxyRouter.transfer(
                    proxySystemController, proxySystemController.getSelectedRoutes().get(0));
            // Now we can assert that the controller is called because we know we are coming from an
            // app transfer (triggered by the local router).
            assertThat(proxyControllerUpdateLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isTrue();
            assertThat(proxySystemController.wasTransferInitiatedBySelf()).isTrue();
            assertThat(proxySystemController.getRoutingSessionInfo().getTransferReason())
                    .isEqualTo(TRANSFER_REASON_SYSTEM_REQUEST);

            assertThat(proxyControllerUpdateLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .isTrue();
        } finally {
            localRouter.unregisterRouteCallback(routeCallback);
            localRouter.unregisterControllerCallback(localControllerCallback);
            proxyRouter.unregisterControllerCallback(proxyControllerCallback);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_BUILT_IN_SPEAKER_ROUTE_SUITABILITY_STATUSES)
    public void getTransferReason_afterAppRestart_returnsPreviouslySelectedTransferReason() {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MEDIA_ROUTING_CONTROL);

        MediaRouter2 proxyRouter2 = MediaRouter2.getInstance(mContext, mContext.getPackageName());
        MediaRouter2.RoutingController controller = proxyRouter2.getSystemController();
        RoutingSessionInfo systemSessionInfo = controller.getRoutingSessionInfo();
        assertThat(systemSessionInfo.getTransferReason())
                .isEqualTo(RoutingSessionInfo.TRANSFER_REASON_SYSTEM_REQUEST);
    }

    @SuppressLint("MissingPermission")
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL})
    @Test
    public void revokingMediaRoutingControl_onAppOpsManager_revokesProxyRouterAccess()
            throws InterruptedException {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_APP_OPS_MODES);

        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_ROUTING_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_CONTENT_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        AppOpsManager appOpsManager = mContext.getSystemService(AppOpsManager.class);
        appOpsManager.setMode(
                AppOpsManager.OP_MEDIA_ROUTING_CONTROL,
                mContext.getApplicationInfo().uid,
                mContext.getPackageName(),
                AppOpsManager.MODE_ALLOWED);

        CountDownLatch latch = new CountDownLatch(1);

        MediaRouter2.getInstance(
                mContext, mContext.getPackageName(), mExecutor, () -> latch.countDown());

        appOpsManager.setMode(
                AppOpsManager.OP_MEDIA_ROUTING_CONTROL,
                mContext.getApplicationInfo().uid,
                mContext.getPackageName(),
                AppOpsManager.MODE_DEFAULT);

        assertThat(latch.await(5000, TimeUnit.MILLISECONDS)).isTrue();

        assertThrows(
                SecurityException.class,
                () -> MediaRouter2.getInstance(mContext, mContext.getPackageName()));
    }

    @SuppressLint("MissingPermission")
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL})
    @Test
    public void revokeMediaRoutingControl_callsAllOnInstanceInvalidatedListeners()
            throws InterruptedException {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_APP_OPS_MODES);

        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_ROUTING_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_CONTENT_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        AppOpsManager appOpsManager = mContext.getSystemService(AppOpsManager.class);
        appOpsManager.setMode(
                AppOpsManager.OP_MEDIA_ROUTING_CONTROL,
                mContext.getApplicationInfo().uid,
                mContext.getPackageName(),
                AppOpsManager.MODE_ALLOWED);

        CountDownLatch latch = new CountDownLatch(5);

        for (int i = 0; i < 5; i++) {
            MediaRouter2.getInstance(
                    mContext, mContext.getPackageName(), mExecutor, () -> latch.countDown());
        }

        appOpsManager.setMode(
                AppOpsManager.OP_MEDIA_ROUTING_CONTROL,
                mContext.getApplicationInfo().uid,
                mContext.getPackageName(),
                AppOpsManager.MODE_ERRORED);

        assertThat(latch.await(5000, TimeUnit.MILLISECONDS)).isTrue();

        assertThrows(
                SecurityException.class,
                () -> MediaRouter2.getInstance(mContext, mContext.getPackageName()));
    }

    @SuppressLint("MissingPermission")
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL})
    @Test
    public void getInstance_withRevocableMediaRoutingControl_throwsWithNoCallback()
            throws InterruptedException {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_APP_OPS_MODES);

        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_ROUTING_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_CONTENT_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        AppOpsManager appOpsManager = mContext.getSystemService(AppOpsManager.class);
        appOpsManager.setMode(
                AppOpsManager.OP_MEDIA_ROUTING_CONTROL,
                mContext.getApplicationInfo().uid,
                mContext.getPackageName(),
                AppOpsManager.MODE_ALLOWED);
        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> MediaRouter2.getInstance(mContext, mContext.getPackageName()));
        } finally {
            appOpsManager.setMode(
                    AppOpsManager.OP_MEDIA_ROUTING_CONTROL,
                    mContext.getApplicationInfo().uid,
                    mContext.getPackageName(),
                    AppOpsManager.MODE_DEFAULT);
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_PRIVILEGED_ROUTING_FOR_MEDIA_ROUTING_CONTROL})
    @Test
    public void revokeMediaRoutingControl_invalidatesAllInstancesAcrossTargetPackageNames()
            throws InterruptedException {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_APP_OPS_MODES);

        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_ROUTING_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(mContext.checkCallingOrSelfPermission(Manifest.permission.MEDIA_CONTENT_CONTROL))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        AppOpsManager appOpsManager = mContext.getSystemService(AppOpsManager.class);
        appOpsManager.setMode(
                AppOpsManager.OP_MEDIA_ROUTING_CONTROL,
                mContext.getApplicationInfo().uid,
                mContext.getPackageName(),
                AppOpsManager.MODE_ALLOWED);

        CountDownLatch latch = new CountDownLatch(2);

        MediaRouter2.getInstance(
                mContext, mContext.getPackageName(), mExecutor, () -> latch.countDown());

        MediaRouter2.getInstance(
                mContext, MEDIA_ROUTER_PROVIDER_1_PACKAGE, mExecutor, () -> latch.countDown());

        appOpsManager.setMode(
                AppOpsManager.OP_MEDIA_ROUTING_CONTROL,
                mContext.getApplicationInfo().uid,
                mContext.getPackageName(),
                AppOpsManager.MODE_ERRORED);

        assertThat(latch.await(5000, TimeUnit.MILLISECONDS)).isTrue();

        assertThrows(
                SecurityException.class,
                () -> MediaRouter2.getInstance(mContext, mContext.getPackageName()));

        assertThrows(
                SecurityException.class,
                () -> MediaRouter2.getInstance(mContext, MEDIA_ROUTER_PROVIDER_1_PACKAGE));
    }
}
