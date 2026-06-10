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

package android.media.router.cts.required_permissions_app;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.media.cts.MediaRouterTestConstants.FEATURE_SAMPLE;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_1_1;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_2_1;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_2_2;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_3_1;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_3_2;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_3_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_APP_1_ROUTE_1;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_REQUIRES_ANY_PERMISSION_SET;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_REQUIRES_ONE_PERMISSION;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_REQUIRES_ANY_PERMISSION_SET;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_REQUIRES_ONE_PERMISSION;
import static android.media.cts.app.common.MediaRouter2TestUtils.launchScreenOnActivity;
import static android.media.cts.app.common.MediaRouter2TestUtils.waitForAndGetRoutes;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * Device-side test for {@link MediaRouter2} routes that have visibility restricted by permissions
 * held by the app requesting them.
 */
public class MediaRouter2DeviceTestRequiredPermissions {
    private ExecutorService mExecutor;
    private Context mContext;
    private MediaRouter2 mRouter;
    private Activity mScreenOnActivity;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mExecutor = Executors.newSingleThreadExecutor();
        mRouter = MediaRouter2.getInstance(mContext);
    }

    @After
    public void tearDown() {
        if (mScreenOnActivity != null) {
            mScreenOnActivity.finish();
        }
    }

    @Test
    public void requiredPermissions_routeNotVisibleWhenOnePermissionNotHeld()
            throws TimeoutException {
        assertPermissionState(PERMISSION_DENIED, Manifest.permission.POST_NOTIFICATIONS);

        mScreenOnActivity = launchScreenOnActivity(mContext);
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                        List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        Map<String, MediaRoute2Info> routes =
                waitForAndGetRoutes(
                        mRouter,
                        preference,
                        Set.of(ROUTE_ID_APP_1_ROUTE_1),
                        mExecutor);
        assertThat(routes.get(ROUTE_ID_REQUIRES_ONE_PERMISSION)).isNull();
    }

    @Test
    public void requiredPermissions_routeVisibleWhenOnePermissionIsHeld() throws TimeoutException {
        assertPermissionState(PERMISSION_GRANTED, Manifest.permission.POST_NOTIFICATIONS);

        mScreenOnActivity = launchScreenOnActivity(mContext);
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                        List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        Map<String, MediaRoute2Info> routes =
                waitForAndGetRoutes(
                        mRouter,
                        preference,
                        Set.of(ROUTE_ID_REQUIRES_ONE_PERMISSION),
                        mExecutor);
        assertThat(routes.get(ROUTE_ID_REQUIRES_ONE_PERMISSION).getName()).isEqualTo(
                ROUTE_NAME_REQUIRES_ONE_PERMISSION);
    }

    @Test
    public void requiredPermissions_routeNotVisibleWhenNoEntryInAnySetIsHeld()
            throws TimeoutException {
        assertPermissionState(PERMISSION_DENIED, REQUIRED_PERMISSIONS_SET_1_1);
        assertPermissionState(
                PERMISSION_DENIED, REQUIRED_PERMISSIONS_SET_2_1, REQUIRED_PERMISSIONS_SET_2_2);
        assertPermissionState(
                PERMISSION_DENIED,
                REQUIRED_PERMISSIONS_SET_3_1,
                REQUIRED_PERMISSIONS_SET_3_2,
                REQUIRED_PERMISSIONS_SET_3_3);

        mScreenOnActivity = launchScreenOnActivity(mContext);
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                        List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        Map<String, MediaRoute2Info> routes =
                waitForAndGetRoutes(
                        mRouter,
                        preference,
                        Set.of(ROUTE_ID_APP_1_ROUTE_1),
                        mExecutor);
        assertThat(routes.get(ROUTE_ID_REQUIRES_ANY_PERMISSION_SET)).isNull();
    }

    @Test
    public void requiredPermissions_routeVisibleWhenFirstSetInListIsHeld() throws TimeoutException {
        assertPermissionState(PERMISSION_GRANTED, REQUIRED_PERMISSIONS_SET_1_1);
        assertPermissionState(
                PERMISSION_DENIED, REQUIRED_PERMISSIONS_SET_2_1, REQUIRED_PERMISSIONS_SET_2_2);
        assertPermissionState(
                PERMISSION_DENIED,
                REQUIRED_PERMISSIONS_SET_3_1,
                REQUIRED_PERMISSIONS_SET_3_2,
                REQUIRED_PERMISSIONS_SET_3_3);

        mScreenOnActivity = launchScreenOnActivity(mContext);
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                        List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        Map<String, MediaRoute2Info> routes =
                waitForAndGetRoutes(
                        mRouter,
                        preference,
                        Set.of(ROUTE_ID_REQUIRES_ANY_PERMISSION_SET),
                        mExecutor);
        assertThat(routes.get(ROUTE_ID_REQUIRES_ANY_PERMISSION_SET).getName()).isEqualTo(
                ROUTE_NAME_REQUIRES_ANY_PERMISSION_SET);
    }

    @Test
    public void requiredPermissions_routeVisibleWhenSecondSetInListIsHeld()
            throws TimeoutException {
        assertPermissionState(PERMISSION_DENIED, REQUIRED_PERMISSIONS_SET_1_1);
        assertPermissionState(
                PERMISSION_GRANTED, REQUIRED_PERMISSIONS_SET_2_1, REQUIRED_PERMISSIONS_SET_2_2);
        assertPermissionState(
                PERMISSION_DENIED,
                REQUIRED_PERMISSIONS_SET_3_1,
                REQUIRED_PERMISSIONS_SET_3_2,
                REQUIRED_PERMISSIONS_SET_3_3);

        mScreenOnActivity = launchScreenOnActivity(mContext);
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                        List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        Map<String, MediaRoute2Info> routes =
                waitForAndGetRoutes(
                        mRouter,
                        preference,
                        Set.of(ROUTE_ID_REQUIRES_ANY_PERMISSION_SET),
                        mExecutor);
        assertThat(routes.get(ROUTE_ID_REQUIRES_ANY_PERMISSION_SET).getName()).isEqualTo(
                ROUTE_NAME_REQUIRES_ANY_PERMISSION_SET);
    }

    @Test
    public void requiredPermissions_routeNotVisibleWhenSecondOfThirdSetIsNotHeld()
            throws TimeoutException {
        assertPermissionState(PERMISSION_DENIED, REQUIRED_PERMISSIONS_SET_1_1);
        assertPermissionState(
                PERMISSION_DENIED, REQUIRED_PERMISSIONS_SET_2_1, REQUIRED_PERMISSIONS_SET_2_2);
        assertPermissionState(PERMISSION_DENIED, REQUIRED_PERMISSIONS_SET_3_2);
        assertPermissionState(
                PERMISSION_GRANTED, REQUIRED_PERMISSIONS_SET_3_1, REQUIRED_PERMISSIONS_SET_3_3);

        mScreenOnActivity = launchScreenOnActivity(mContext);
        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(
                        List.of(FEATURE_SAMPLE), /* activeScan= */ true)
                        .build();
        Map<String, MediaRoute2Info> routes =
                waitForAndGetRoutes(
                        mRouter,
                        preference,
                        Set.of(ROUTE_ID_APP_1_ROUTE_1),
                        mExecutor);
        assertThat(routes.get(ROUTE_ID_REQUIRES_ANY_PERMISSION_SET)).isNull();
    }

    protected void assertPermissionState(int state, String... permissions) {
        for (String permission : permissions) {
            assertThat(mContext.checkCallingOrSelfPermission(permission)).isEqualTo(state);
        }
    }
}
