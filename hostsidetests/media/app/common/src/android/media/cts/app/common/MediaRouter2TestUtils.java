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

package android.media.cts.app.common;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.RouteDiscoveryPreference;
import android.os.ConditionVariable;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MediaRouter2TestUtils {
    /**
     * The maximum amount of time to wait for an expected {@link
     * MediaRouter2.RouteCallback#onRoutesUpdated} call, in milliseconds.
     */
    public static final int ROUTE_UPDATE_MAX_WAIT_MS = 10_000;

    /**
     * Launches an activity to keep the screen on. Remember to call finish() on the result
     * before when you are finished with it - perhaps in an @After method.
     */
    public static Activity launchScreenOnActivity(Context context) {
        // Launch ScreenOnActivity while tests are running for scanning to work. MediaRouter2 blocks
        // app scan requests while the screen is off for resource saving.
        Intent intent = new Intent(/* context= */ context, ScreenOnActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
    }

    /**
     * Returns the next route list received via {@link MediaRouter2.RouteCallback#onRoutesUpdated}
     * that includes all the given {@code expectedRouteIds}.
     *
     * <p>Will only wait for up to {@link #ROUTE_UPDATE_MAX_WAIT_MS}.
     */
    public static Map<String, MediaRoute2Info> waitForAndGetRoutes(
            MediaRouter2 router,
            RouteDiscoveryPreference preference,
            Set<String> expectedRouteIds,
            Executor executor)
            throws TimeoutException {
        ConditionVariable condition = new ConditionVariable();
        MediaRouter2.RouteCallback routeCallback =
                new MediaRouter2.RouteCallback() {
                    @Override
                    public void onRoutesUpdated(List<MediaRoute2Info> routes) {
                        Set<String> receivedRouteIds =
                                routes.stream()
                                        .map(MediaRoute2Info::getOriginalId)
                                        .collect(Collectors.toSet());
                        if (receivedRouteIds.containsAll(expectedRouteIds)) {
                            condition.open();
                        }
                    }
                };

        router.registerRouteCallback(executor, routeCallback, preference);
        Set<String> currentRoutes =
                router.getRoutes().stream()
                        .map(MediaRoute2Info::getOriginalId)
                        .collect(Collectors.toSet());
        try {
            if (!currentRoutes.containsAll(expectedRouteIds)
                    && !condition.block(ROUTE_UPDATE_MAX_WAIT_MS)) {
                throw new TimeoutException(
                        "Failed to get expected routes after "
                                + ROUTE_UPDATE_MAX_WAIT_MS
                                + " milliseconds.");
            }
            return router.getRoutes().stream()
                    .collect(Collectors.toMap(MediaRoute2Info::getOriginalId, Function.identity()));
        } finally {
            router.unregisterRouteCallback(routeCallback);
        }
    }
}
