/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.media.router.cts.StubMediaRoute2ProviderService.FEATURE_SAMPLE;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID1;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_ID2;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME1;
import static android.media.router.cts.StubMediaRoute2ProviderService.ROUTE_NAME2;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.compatibility.common.util.UiAutomatorUtils2;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
@AppModeFull(reason = "The system should be able to bind to StubMediaRoute2ProviderService")
@LargeTest
@FrameworkSpecificTest
@RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE) // TODO(b/397522231)
@RequireDoesNotHaveFeature(PackageManager.FEATURE_LEANBACK) // TODO(b/397521067)
@RequireDoesNotHaveFeature(PackageManager.FEATURE_WATCH) // TODO(b/397520196)
public class OutputSwitcherTest {
    private static final String TAG = "OutputSwitcherTest";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final int TIMEOUT_MS = 5000;

    // Required by Bedstead.
    @ClassRule @Rule public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public StubMediaRoute2ProviderService.Setup mProviderSetup =
            new StubMediaRoute2ProviderService.Setup();

    private Context mContext;
    private Executor mExecutor;
    private MediaRouter2 mRouter2;
    private StubMediaRoute2ProviderService mService;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mExecutor = Executors.newSingleThreadExecutor();
        mRouter2 = MediaRouter2.getInstance(mContext);
        MediaRouter2TestActivity.startActivity(mContext);
        mService = mProviderSetup.setupAndGetService(mContext);
    }

    @After
    public void tearDown() {
        MediaRouter2TestActivity.finishActivity();
        // Dismiss any system output switcher dialogs.
        InstrumentationRegistry.getInstrumentation()
                .getContext()
                .sendBroadcast(
                        new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
    }

    @Test
    public void showSystemOutputSwitcher_preferredRoutesAppear() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID2));

        MediaRouter2.RouteCallback routeCallback = new MediaRouter2.RouteCallback() {};
        mRouter2.registerRouteCallback(
                mExecutor,
                routeCallback,
                new RouteDiscoveryPreference.Builder(List.of(FEATURE_SAMPLE), true).build());

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();

        UiAutomatorUtils2.waitFindObject(By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        UiAutomatorUtils2.waitFindObject(By.text(ROUTE_NAME2).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);

        mRouter2.unregisterRouteCallback(routeCallback);
    }

    @Test
    public void showSystemOutputSwitcher_clickOnRoute_sessionIsCreated() throws Exception {
        mService.removeAllRoutesExcept(List.of(ROUTE_ID1, ROUTE_ID2));

        MediaRouter2.RouteCallback routeCallback = new MediaRouter2.RouteCallback() {};
        mRouter2.registerRouteCallback(
                mExecutor,
                routeCallback,
                new RouteDiscoveryPreference.Builder(List.of(FEATURE_SAMPLE), true).build());

        assertThat(mRouter2.showSystemOutputSwitcher()).isTrue();

        CountDownLatch onCreateSessionLatch = new CountDownLatch(1);
        final String[] onCreateSessionRouteId = {""};

        mService.setProxy(
                new StubMediaRoute2ProviderService.Proxy() {
                    public void onCreateSession(
                            long requestId,
                            @NonNull String packageName,
                            @NonNull String routeId,
                            @Nullable Bundle sessionHints) {
                        onCreateSessionRouteId[0] = routeId;
                        onCreateSessionLatch.countDown();
                    }
                });

        UiObject2 route1 =
                UiAutomatorUtils2.waitFindObject(
                        By.text(ROUTE_NAME1).pkg(SYSTEM_UI_PACKAGE), TIMEOUT_MS);
        route1.click();

        assertThat(onCreateSessionLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(onCreateSessionRouteId[0]).isEqualTo(ROUTE_ID1);

        mRouter2.unregisterRouteCallback(routeCallback);
    }
}
