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

package android.media.router.cts;

import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_1_1;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_2_1;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_2_2;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_3_1;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_3_2;
import static android.media.cts.MediaRouterTestConstants.REQUIRED_PERMISSIONS_SET_3_3;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_REQUIRES_ANY_PERMISSION_SET;
import static android.media.cts.MediaRouterTestConstants.ROUTE_ID_REQUIRES_ONE_PERMISSION;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_REQUIRES_ANY_PERMISSION_SET;
import static android.media.cts.MediaRouterTestConstants.ROUTE_NAME_REQUIRES_ONE_PERMISSION;

import android.media.MediaRoute2Info;

import com.android.media.flags.Flags;

import java.util.List;
import java.util.Set;

public class PermissionsRequiredRouteProvider extends BaseFakeRouteProviderService {

    public PermissionsRequiredRouteProvider() {
        super(createRoutes());
    }

    static List<MediaRoute2Info> createRoutes() {
        if (Flags.enableRouteVisibilityControlApi()) {
            return List.of(
                    createPermissionsRequiredRoute(ROUTE_ID_REQUIRES_ONE_PERMISSION,
                            ROUTE_NAME_REQUIRES_ONE_PERMISSION,
                            List.of(Set.of("android.permission.POST_NOTIFICATIONS"))),
                    createPermissionsRequiredRoute(ROUTE_ID_REQUIRES_ANY_PERMISSION_SET,
                            ROUTE_NAME_REQUIRES_ANY_PERMISSION_SET,
                            List.of(Set.of(REQUIRED_PERMISSIONS_SET_1_1),
                                    Set.of(
                                            REQUIRED_PERMISSIONS_SET_2_1,
                                            REQUIRED_PERMISSIONS_SET_2_2),
                                    Set.of(
                                            REQUIRED_PERMISSIONS_SET_3_1,
                                            REQUIRED_PERMISSIONS_SET_3_2,
                                            REQUIRED_PERMISSIONS_SET_3_3))));
        } else {
            return List.of();
        }
    }
}
