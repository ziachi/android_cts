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

package android.media.router.cts;

import static androidx.test.ext.truth.os.BundleSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.media.MediaRoute2Info;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.media.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Tests {@link MediaRoute2Info} and its {@link MediaRoute2Info.Builder builder}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
@FrameworkSpecificTest
public class MediaRoute2InfoTest {

    public static final String TEST_ID = "test_id";
    public static final String TEST_NAME = "test_name";
    public static final String TEST_ROUTE_TYPE_0 = "test_route_type_0";
    public static final String TEST_ROUTE_TYPE_1 = "test_route_type_1";
    public static final Set<String> TEST_DEDUPLICATION_IDS = Set.of("test_deduplication_id");
    public static final Uri TEST_ICON_URI = Uri.parse("https://developer.android.com");
    public static final String TEST_DESCRIPTION = "test_description";
    public static final int TEST_CONNECTION_STATE = MediaRoute2Info.CONNECTION_STATE_CONNECTING;
    public static final String TEST_CLIENT_PACKAGE_NAME = "com.test.client.package.name";
    public static final int TEST_VOLUME_HANDLING = MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE;
    public static final int TEST_VOLUME_MAX = 100;
    public static final int TEST_VOLUME = 65;
    public static final Set<String> TEST_ALLOWED_PACKAGES =
            Set.of("com.android.systemui", "com.android.settings");
    public static final List<Set<String>> TEST_REQUIRED_PERMISSIONS =
            List.of(Set.of("some.permission.one"),
                    Set.of("some.permission.two", "some.permission.three"));

    public static final String TEST_KEY = "test_key";
    public static final String TEST_VALUE = "test_value";

    @Test
    public void testBuilderConstructorWithInvalidValues() {
        final String nullId = null;
        final String nullName = null;
        final String emptyId = "";
        final String emptyName = "";
        final String validId = "valid_id";
        final String validName = "valid_name";

        // ID is invalid
        assertThrows(IllegalArgumentException.class,
                () -> new MediaRoute2Info.Builder(nullId, validName));
        assertThrows(IllegalArgumentException.class,
                () -> new MediaRoute2Info.Builder(emptyId, validName));

        // name is invalid
        assertThrows(IllegalArgumentException.class,
                () -> new MediaRoute2Info.Builder(validId, nullName));
        assertThrows(IllegalArgumentException.class,
                () -> new MediaRoute2Info.Builder(validId, emptyName));

        // Both are invalid
        assertThrows(IllegalArgumentException.class,
                () -> new MediaRoute2Info.Builder(nullId, nullName));
        assertThrows(IllegalArgumentException.class,
                () -> new MediaRoute2Info.Builder(nullId, emptyName));
        assertThrows(IllegalArgumentException.class,
                () -> new MediaRoute2Info.Builder(emptyId, nullName));
        assertThrows(IllegalArgumentException.class,
                () -> new MediaRoute2Info.Builder(emptyId, emptyName));


        // Null RouteInfo (1-argument constructor)
        final MediaRoute2Info nullRouteInfo = null;
        assertThrows(NullPointerException.class,
                () -> new MediaRoute2Info.Builder(nullRouteInfo));
    }

    @Test
    public void testBuilderBuildWithEmptyRouteTypesShouldThrowIAE() {
        MediaRoute2Info.Builder builder = new MediaRoute2Info.Builder(TEST_ID, TEST_NAME);
        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testBuilderAndGettersOfMediaRoute2Info() {
        Bundle extras = new Bundle();
        extras.putString(TEST_KEY, TEST_VALUE);

        MediaRoute2Info routeInfo =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .addFeature(TEST_ROUTE_TYPE_1)
                        .setIconUri(TEST_ICON_URI)
                        .setDescription(TEST_DESCRIPTION)
                        .setConnectionState(TEST_CONNECTION_STATE)
                        .setClientPackageName(TEST_CLIENT_PACKAGE_NAME)
                        .setVolumeHandling(TEST_VOLUME_HANDLING)
                        .setVolumeMax(TEST_VOLUME_MAX)
                        .setVolume(TEST_VOLUME)
                        .setDeduplicationIds(TEST_DEDUPLICATION_IDS)
                        .setExtras(extras)
                        .build();

        assertThat(routeInfo.getId()).isEqualTo(TEST_ID);
        assertThat(routeInfo.getName()).isEqualTo(TEST_NAME);

        assertThat(routeInfo.getFeatures())
                .containsExactly(TEST_ROUTE_TYPE_0, TEST_ROUTE_TYPE_1).inOrder();

        assertThat(routeInfo.getIconUri()).isEqualTo(TEST_ICON_URI);
        assertThat(routeInfo.getDescription()).isEqualTo(TEST_DESCRIPTION);
        assertThat(routeInfo.getConnectionState()).isEqualTo(TEST_CONNECTION_STATE);
        assertThat(routeInfo.getClientPackageName()).isEqualTo(TEST_CLIENT_PACKAGE_NAME);
        assertThat(routeInfo.getVolumeHandling()).isEqualTo(TEST_VOLUME_HANDLING);
        assertThat(routeInfo.getVolumeMax()).isEqualTo(TEST_VOLUME_MAX);
        assertThat(routeInfo.getVolume()).isEqualTo(TEST_VOLUME);
        assertThat(routeInfo.getDeduplicationIds()).isEqualTo(TEST_DEDUPLICATION_IDS);

        Bundle extrasOut = routeInfo.getExtras();
        assertThat(extrasOut).isNotNull();
        assertThat(extrasOut).containsKey(TEST_KEY);
        assertThat(extrasOut).string(TEST_KEY).isEqualTo(TEST_VALUE);
    }

    @Test
    public void testBuilderSetExtrasWithNull() {
        MediaRoute2Info routeInfo = new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                .addFeature(TEST_ROUTE_TYPE_0)
                .setExtras(null)
                .build();

        assertThat(routeInfo.getExtras()).isNull();
    }

    @Test
    public void testBuilderaddFeatures() {
        List<String> routeTypes = new ArrayList<>();
        routeTypes.add(TEST_ROUTE_TYPE_0);
        routeTypes.add(TEST_ROUTE_TYPE_1);

        MediaRoute2Info routeInfo = new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                .addFeatures(routeTypes)
                .build();

        assertThat(routeInfo.getFeatures()).isEqualTo(routeTypes);
    }

    @Test
    public void testBuilderclearFeatures() {
        MediaRoute2Info routeInfo = new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                .addFeature(TEST_ROUTE_TYPE_0)
                .addFeature(TEST_ROUTE_TYPE_1)
                // clearFeatures should clear the route types.
                .clearFeatures()
                .addFeature(TEST_ROUTE_TYPE_1)
                .build();

        assertThat(routeInfo.getFeatures()).containsExactly(TEST_ROUTE_TYPE_1);
    }

    @Test
    public void testBuilderCreatePublicRouteInfoByDefault() {
        MediaRoute2Info routeInfo =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .build();
        assertThat(routeInfo.isVisibleTo("com.android.example.app")).isEqualTo(true);
    }

    @Test
    public void testRouteInfoSeenByItsCreatorPackage() {
        String creatorPackageName = ApplicationProvider.getApplicationContext().getPackageName();
        MediaRoute2Info routeInfo =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .setProviderPackageName(creatorPackageName)
                        .setVisibilityRestricted(Set.of())
                        .build();
        assertThat(routeInfo.isVisibleTo(creatorPackageName)).isEqualTo(true);
    }

    @Test
    public void testRouteInfoSeenOnlyByItsAllowedPackages() {
        String creatorPackageName = ApplicationProvider.getApplicationContext().getPackageName();
        MediaRoute2Info routeInfo =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .setProviderPackageName(creatorPackageName)
                        .setVisibilityRestricted(TEST_ALLOWED_PACKAGES)
                        .build();
        assertThat(routeInfo.isVisibleTo("com.android.settings")).isEqualTo(true);
        assertThat(routeInfo.isVisibleTo("com.android.example.app")).isEqualTo(false);
    }

    @Test
    public void testEqualsCreatedWithSameArguments() {
        Bundle extras = new Bundle();
        extras.putString(TEST_KEY, TEST_VALUE);

        MediaRoute2Info routeInfo1 =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .addFeature(TEST_ROUTE_TYPE_1)
                        .setIconUri(TEST_ICON_URI)
                        .setDescription(TEST_DESCRIPTION)
                        .setConnectionState(TEST_CONNECTION_STATE)
                        .setClientPackageName(TEST_CLIENT_PACKAGE_NAME)
                        .setVolumeHandling(TEST_VOLUME_HANDLING)
                        .setVolumeMax(TEST_VOLUME_MAX)
                        .setVolume(TEST_VOLUME)
                        .setDeduplicationIds(TEST_DEDUPLICATION_IDS)
                        .setExtras(extras)
                        .setVisibilityRestricted(TEST_ALLOWED_PACKAGES)
                        .build();

        MediaRoute2Info routeInfo2 =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .addFeature(TEST_ROUTE_TYPE_1)
                        .setIconUri(TEST_ICON_URI)
                        .setDescription(TEST_DESCRIPTION)
                        .setConnectionState(TEST_CONNECTION_STATE)
                        .setClientPackageName(TEST_CLIENT_PACKAGE_NAME)
                        .setVolumeHandling(TEST_VOLUME_HANDLING)
                        .setVolumeMax(TEST_VOLUME_MAX)
                        .setVolume(TEST_VOLUME)
                        .setDeduplicationIds(TEST_DEDUPLICATION_IDS)
                        .setExtras(extras)
                        .setVisibilityRestricted(TEST_ALLOWED_PACKAGES)
                        .build();

        assertThat(routeInfo1).isEqualTo(routeInfo2);
        assertThat(routeInfo1.hashCode()).isEqualTo(routeInfo2.hashCode());
    }

    @Test
    public void testEqualsCreatedWithBuilderCopyConstructor() {
        Bundle extras = new Bundle();
        extras.putString(TEST_KEY, TEST_VALUE);

        MediaRoute2Info routeInfo1 =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .addFeature(TEST_ROUTE_TYPE_1)
                        .setIconUri(TEST_ICON_URI)
                        .setDescription(TEST_DESCRIPTION)
                        .setConnectionState(TEST_CONNECTION_STATE)
                        .setClientPackageName(TEST_CLIENT_PACKAGE_NAME)
                        .setVolumeHandling(TEST_VOLUME_HANDLING)
                        .setVolumeMax(TEST_VOLUME_MAX)
                        .setVolume(TEST_VOLUME)
                        .setDeduplicationIds(TEST_DEDUPLICATION_IDS)
                        .setExtras(extras)
                        .build();

        MediaRoute2Info routeInfo2 = new MediaRoute2Info.Builder(routeInfo1).build();

        assertThat(routeInfo2).isEqualTo(routeInfo1);
        assertThat(routeInfo2.hashCode()).isEqualTo(routeInfo1.hashCode());
    }

    @Test
    public void testEqualsReturnFalse() {
        Bundle extras = new Bundle();
        extras.putString(TEST_KEY, TEST_VALUE);

        MediaRoute2Info routeInfo =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .addFeature(TEST_ROUTE_TYPE_1)
                        .setIconUri(TEST_ICON_URI)
                        .setDescription(TEST_DESCRIPTION)
                        .setConnectionState(TEST_CONNECTION_STATE)
                        .setClientPackageName(TEST_CLIENT_PACKAGE_NAME)
                        .setVolumeHandling(TEST_VOLUME_HANDLING)
                        .setVolumeMax(TEST_VOLUME_MAX)
                        .setVolume(TEST_VOLUME)
                        .setDeduplicationIds(TEST_DEDUPLICATION_IDS)
                        .setExtras(extras)
                        .build();

        // Now, we will use copy constructor
        assertThat(new MediaRoute2Info.Builder(routeInfo)
                .addFeature("randomRouteType")
                .build()).isNotEqualTo(routeInfo);
        assertThat(new MediaRoute2Info.Builder(routeInfo)
                .setIconUri(Uri.parse("randomUri"))
                .build()).isNotEqualTo(routeInfo);
        assertThat(new MediaRoute2Info.Builder(routeInfo)
                .setDescription("randomDescription")
                .build()).isNotEqualTo(routeInfo);
        assertThat(new MediaRoute2Info.Builder(routeInfo)
                .setConnectionState(TEST_CONNECTION_STATE + 1)
                .build()).isNotEqualTo(routeInfo);
        assertThat(new MediaRoute2Info.Builder(routeInfo)
                .setClientPackageName("randomPackageName")
                .build()).isNotEqualTo(routeInfo);
        assertThat(new MediaRoute2Info.Builder(routeInfo)
                .setVolumeHandling(TEST_VOLUME_HANDLING + 1)
                .build()).isNotEqualTo(routeInfo);
        assertThat(new MediaRoute2Info.Builder(routeInfo)
                .setVolumeMax(TEST_VOLUME_MAX + 100)
                .build()).isNotEqualTo(routeInfo);
        assertThat(new MediaRoute2Info.Builder(routeInfo)
                .setVolume(TEST_VOLUME + 10)
                .build()).isNotEqualTo(routeInfo);
        assertThat(new MediaRoute2Info.Builder(routeInfo)
                .setDeduplicationIds(Set.of("randomDeduplicationId"))
                .build()).isNotEqualTo(routeInfo);
        // Note: Extras will not affect the equals.
    }

    @Test
    public void testParcelingAndUnParceling() {
        Bundle extras = new Bundle();
        extras.putString(TEST_KEY, TEST_VALUE);

        MediaRoute2Info routeInfo =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .addFeature(TEST_ROUTE_TYPE_1)
                        .setIconUri(TEST_ICON_URI)
                        .setDescription(TEST_DESCRIPTION)
                        .setConnectionState(TEST_CONNECTION_STATE)
                        .setClientPackageName(TEST_CLIENT_PACKAGE_NAME)
                        .setVolumeHandling(TEST_VOLUME_HANDLING)
                        .setVolumeMax(TEST_VOLUME_MAX)
                        .setVolume(TEST_VOLUME)
                        .setDeduplicationIds(TEST_DEDUPLICATION_IDS)
                        .setExtras(extras)
                        .setProviderPackageName(
                                ApplicationProvider.getApplicationContext().getPackageName())
                        .setVisibilityRestricted(TEST_ALLOWED_PACKAGES)
                        .setRequiredPermissions(TEST_REQUIRED_PERMISSIONS)
                        .build();

        Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(routeInfo, 0);
        parcel.setDataPosition(0);

        MediaRoute2Info routeInfoFromParcel = parcel.readParcelable(null);
        assertThat(routeInfoFromParcel).isEqualTo(routeInfo);
        assertThat(routeInfoFromParcel.hashCode()).isEqualTo(routeInfo.hashCode());

        // Check extras
        Bundle extrasOut = routeInfoFromParcel.getExtras();
        assertThat(extrasOut).isNotNull();
        assertThat(extrasOut).containsKey(TEST_KEY);
        assertThat(extrasOut).string(TEST_KEY).isEqualTo(TEST_VALUE);
        parcel.recycle();

        // Check visibility restrictions
        for (String pkg : TEST_ALLOWED_PACKAGES) {
            assertThat(routeInfoFromParcel.isVisibleTo(pkg)).isEqualTo(true);
        }
        assertThat(routeInfoFromParcel.isVisibleTo("com.android.example.app")).isEqualTo(false);
        assertThat(routeInfoFromParcel.getRequiredPermissions()).isEqualTo(
                TEST_REQUIRED_PERMISSIONS);

        // In order to mark writeToParcel as tested, we let's just call it directly.
        Parcel dummyParcel = Parcel.obtain();
        routeInfo.writeToParcel(dummyParcel, 0);
        dummyParcel.recycle();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ROUTE_VISIBILITY_CONTROL_API)
    public void testSetRequiredPermissionsWithOnePermissionSet() {
        MediaRoute2Info routeInfo =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .setRequiredPermissions(Set.of("some.named.permission"))
                        .build();
        assertThat(routeInfo.getRequiredPermissions()).hasSize(1);
        assertThat(routeInfo.getRequiredPermissions()).containsExactly(
                Set.of("some.named.permission"));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ROUTE_VISIBILITY_CONTROL_API)
    public void testSetRequiredPermissionsWithMultiplePermissionSets() {
        MediaRoute2Info routeInfo =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .setRequiredPermissions(TEST_REQUIRED_PERMISSIONS)
                        .build();
        assertThat(routeInfo.getRequiredPermissions()).hasSize(TEST_REQUIRED_PERMISSIONS.size());
        assertThat(routeInfo.getRequiredPermissions()).containsExactlyElementsIn(
                TEST_REQUIRED_PERMISSIONS);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ROUTE_VISIBILITY_CONTROL_API)
    public void testEqualsAndHashCodeWithSetRequiredPermissions() {
        MediaRoute2Info routeWithRequiredPermissions =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .setRequiredPermissions(TEST_REQUIRED_PERMISSIONS)
                        .build();
        MediaRoute2Info routeWithoutRequiredPermissions =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .build();
        assertThat(routeWithRequiredPermissions).isNotEqualTo(routeWithoutRequiredPermissions);
        assertThat(routeWithRequiredPermissions.hashCode()).isNotEqualTo(
                routeWithoutRequiredPermissions.hashCode());
    }

    @Test
    public void testDescribeContents() {
        MediaRoute2Info routeInfo =
                new MediaRoute2Info.Builder(TEST_ID, TEST_NAME)
                        .addFeature(TEST_ROUTE_TYPE_0)
                        .addFeature(TEST_ROUTE_TYPE_1)
                        .setIconUri(TEST_ICON_URI)
                        .setDescription(TEST_DESCRIPTION)
                        .setConnectionState(TEST_CONNECTION_STATE)
                        .setClientPackageName(TEST_CLIENT_PACKAGE_NAME)
                        .setVolumeHandling(TEST_VOLUME_HANDLING)
                        .setVolumeMax(TEST_VOLUME_MAX)
                        .setVolume(TEST_VOLUME)
                        .setDeduplicationIds(TEST_DEDUPLICATION_IDS)
                        .build();
        assertThat(routeInfo.describeContents()).isEqualTo(0);
    }
}
