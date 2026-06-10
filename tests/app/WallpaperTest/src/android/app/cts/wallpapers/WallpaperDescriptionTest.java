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
package android.app.cts.wallpapers;

import static android.app.Flags.FLAG_LIVE_WALLPAPER_CONTENT_HANDLING;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.wallpaper.WallpaperDescription;
import android.net.Uri;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/**
 * Tests the public API of {@link WallpaperDescription}. This is a subset of the tests in
 * frameworks/base/core/tests/coretests/src/android/app/wallpaper/WallpaperDescriptionTest.java.
 */
@RunWith(JUnit4.class)
public class WallpaperDescriptionTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_LIVE_WALLPAPER_CONTENT_HANDLING)
    public void equals_ignoresIrrelevantFields() {
        String id = "fakeId";
        WallpaperDescription desc1 =
                new WallpaperDescription.Builder().setId(id).setTitle("fake one").build();
        WallpaperDescription desc2 =
                new WallpaperDescription.Builder().setId(id).setTitle("fake different").build();

        assertThat(desc1).isEqualTo(desc2);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LIVE_WALLPAPER_CONTENT_HANDLING)
    public void hash_ignoresIrrelevantFields() {
        String id = "fakeId";
        WallpaperDescription desc1 =
                new WallpaperDescription.Builder().setId(id).setTitle("fake one").build();
        WallpaperDescription desc2 =
                new WallpaperDescription.Builder().setId(id).setTitle("fake different").build();

        assertThat(desc1.hashCode()).isEqualTo(desc2.hashCode());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LIVE_WALLPAPER_CONTENT_HANDLING)
    public void parcel_roundTripSucceeds() {
        final Uri thumbnail = Uri.parse("http://www.bogus.com/thumbnail");
        final List<CharSequence> description = List.of("line1", "line2");
        final Uri contextUri = Uri.parse("http://www.bogus.com/contextUri");
        final PersistableBundle content = makeDefaultContent();
        WallpaperDescription source =
                new WallpaperDescription.Builder()
                        .setId("fakeId")
                        .setThumbnail(thumbnail)
                        .setTitle("Fake title")
                        .setDescription(description)
                        .setContextUri(contextUri)
                        .setContextDescription("Context description")
                        .setContent(content)
                        .build();

        Parcel parcel = Parcel.obtain();
        source.writeToParcel(parcel, 0);
        // Reset parcel for reading
        parcel.setDataPosition(0);
        WallpaperDescription destination = WallpaperDescription.CREATOR.createFromParcel(parcel);

        assertThat(destination.getComponent()).isEqualTo(source.getComponent());
        assertThat(destination.getId()).isEqualTo(source.getId());
        assertThat(destination.getThumbnail()).isEqualTo(source.getThumbnail());
        assertWithMessage("title mismatch")
                .that(CharSequence.compare(destination.getTitle(), source.getTitle()))
                .isEqualTo(0);
        assertThat(destination.getDescription()).hasSize(source.getDescription().size());
        for (int i = 0; i < destination.getDescription().size(); i++) {
            CharSequence strDest = destination.getDescription().get(i);
            CharSequence strSrc = source.getDescription().get(i);
            assertWithMessage("description string mismatch")
                    .that(CharSequence.compare(strDest, strSrc))
                    .isEqualTo(0);
        }
        assertThat(destination.getContextUri()).isEqualTo(source.getContextUri());
        assertWithMessage("context description mismatch")
                .that(
                        CharSequence.compare(
                                destination.getContextDescription(),
                                source.getContextDescription()))
                .isEqualTo(0);
        assertThat(destination.getContent()).isNotNull();
        assertThat(destination.getContent().getString("ckey"))
                .isEqualTo(source.getContent().getString("ckey"));
        assertThat(destination.getCropHints()).isNotNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LIVE_WALLPAPER_CONTENT_HANDLING)
    public void parcel_roundTripSucceeds_withNulls() {
        WallpaperDescription source = new WallpaperDescription.Builder().build();

        Parcel parcel = Parcel.obtain();
        source.writeToParcel(parcel, 0);
        // Reset parcel for reading
        parcel.setDataPosition(0);
        WallpaperDescription destination = WallpaperDescription.CREATOR.createFromParcel(parcel);

        assertThat(destination.getComponent()).isEqualTo(source.getComponent());
        assertThat(destination.getId()).isEqualTo(source.getId());
        assertThat(destination.getThumbnail()).isEqualTo(source.getThumbnail());
        assertThat(destination.getTitle()).isNull();
        assertThat(destination.getDescription()).hasSize(0);
        assertThat(destination.getContextUri()).isEqualTo(source.getContextUri());
        assertThat(destination.getContextDescription()).isNull();
        assertThat(destination.getContent()).isNotNull();
        assertThat(destination.getContent().keySet()).isEmpty();
        assertThat(destination.getCropHints()).isNotNull();
        assertThat(destination.getCropHints().size()).isEqualTo(0);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LIVE_WALLPAPER_CONTENT_HANDLING)
    public void toBuilder_succeeds() {
        final String sourceId = "sourceId";
        final Uri thumbnail = Uri.parse("http://www.bogus.com/thumbnail");
        final List<CharSequence> description = List.of("line1", "line2");
        final Uri contextUri = Uri.parse("http://www.bogus.com/contextUri");
        final PersistableBundle content = makeDefaultContent();
        final String destinationId = "destinationId";
        WallpaperDescription source =
                new WallpaperDescription.Builder()
                        .setId(sourceId)
                        .setThumbnail(thumbnail)
                        .setTitle("Fake title")
                        .setDescription(description)
                        .setContextUri(contextUri)
                        .setContextDescription("Context description")
                        .setContent(content)
                        .build();

        WallpaperDescription destination = source.toBuilder().setId(destinationId).build();

        assertThat(destination.getComponent()).isEqualTo(source.getComponent());
        assertThat(destination.getId()).isEqualTo(destinationId);
        assertThat(destination.getThumbnail()).isEqualTo(source.getThumbnail());
        assertWithMessage("title mismatch")
                .that(CharSequence.compare(destination.getTitle(), source.getTitle()))
                .isEqualTo(0);
        assertThat(destination.getDescription()).hasSize(source.getDescription().size());
        for (int i = 0; i < destination.getDescription().size(); i++) {
            CharSequence strDest = destination.getDescription().get(i);
            CharSequence strSrc = source.getDescription().get(i);
            assertWithMessage("description string mismatch")
                    .that(CharSequence.compare(strDest, strSrc))
                    .isEqualTo(0);
        }
        assertThat(destination.getContextUri()).isEqualTo(source.getContextUri());
        assertWithMessage("context description mismatch")
                .that(
                        CharSequence.compare(
                                destination.getContextDescription(),
                                source.getContextDescription()))
                .isEqualTo(0);
        assertThat(destination.getContent()).isNotNull();
        assertThat(destination.getContent().getString("ckey"))
                .isEqualTo(source.getContent().getString("ckey"));
    }

    private static PersistableBundle makeDefaultContent() {
        final PersistableBundle content = new PersistableBundle();
        content.putString("ckey", "cvalue");
        return content;
    }
}
