/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.media.projection.cts;

import static android.media.projection.MediaProjectionConfig.DEFAULT_PROJECTION_SOURCES;
import static android.media.projection.MediaProjectionConfig.PROJECTION_SOURCE_APP;
import static android.media.projection.MediaProjectionConfig.PROJECTION_SOURCE_DISPLAY;
import static android.media.projection.MediaProjectionConfig.PROJECTION_SOURCE_DISPLAY_REGION;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.media.projection.MediaProjectionConfig;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.media.projection.flags.Flags;

import org.junit.Rule;
import org.junit.Test;

/**
 * Test {@link MediaProjectionConfig}.
 *
 * <p>Run with: atest CtsMediaProjectionTestCases:MediaProjectionConfigTest
 */
@FrameworkSpecificTest
public class MediaProjectionConfigTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @ApiTest(apis = "android.media.projection.MediaProjectionConfig#createConfigForDefaultDisplay")
    @Test
    public void testCreateConfigForDefaultDisplay() {
        assertThat(MediaProjectionConfig.createConfigForDefaultDisplay()).isNotNull();
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionConfig#createConfigForUserChoice")
    @Test
    public void testCreateConfigForUserChoice() {
        assertThat(MediaProjectionConfig.createConfigForUserChoice()).isNotNull();
    }

    @Test
    @ApiTest(apis = "android.media.projection.MediaProjectionConfig.Builder#build")
    @RequiresFlagsEnabled(Flags.FLAG_APP_CONTENT_SHARING)
    public void builder_defaultsAreValid() {
        MediaProjectionConfig config = new MediaProjectionConfig.Builder().build();
        assertThat(config.isSourceEnabled(PROJECTION_SOURCE_DISPLAY)).isTrue();
        assertThat(config.isSourceEnabled(PROJECTION_SOURCE_DISPLAY_REGION)).isFalse();
        assertThat(config.isSourceEnabled(PROJECTION_SOURCE_APP)).isTrue();
        assertThat(config.getDisplayToCapture()).isEqualTo(DEFAULT_DISPLAY);
        assertThat(config.getRequesterHint()).isNull();
        assertThat(config.getProjectionSources()).isEqualTo(DEFAULT_PROJECTION_SOURCES);
        assertThat(config.getInitiallySelectedSource()).isEqualTo(0);
    }

    @Test
    @ApiTest(
            apis =
                    "android.media.projection.MediaProjectionConfig"
                            + ".Builder#setChoiceDisplayEnabled")
    @RequiresFlagsEnabled(Flags.FLAG_APP_CONTENT_SHARING)
    public void builder_setAndUnset_choice() {
        MediaProjectionConfig config =
                new MediaProjectionConfig.Builder()
                        .setSourceEnabled(PROJECTION_SOURCE_APP, false)
                        .setSourceEnabled(PROJECTION_SOURCE_APP, true)
                        .setSourceEnabled(PROJECTION_SOURCE_DISPLAY, true)
                        .setSourceEnabled(PROJECTION_SOURCE_DISPLAY, false)
                        .build();
        assertThat(config.isSourceEnabled(PROJECTION_SOURCE_DISPLAY)).isFalse();
        assertThat(config.isSourceEnabled(PROJECTION_SOURCE_APP)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_CONTENT_SHARING)
    public void builder_setAllOptions() {
        MediaProjectionConfig config =
                new MediaProjectionConfig.Builder()
                        .setSourceEnabled(PROJECTION_SOURCE_APP, true)
                        .setSourceEnabled(PROJECTION_SOURCE_DISPLAY, true)
                        .setSourceEnabled(PROJECTION_SOURCE_DISPLAY_REGION, true)
                        .setRequesterHint("requesterHint")
                        .setInitiallySelectedSource(PROJECTION_SOURCE_APP)
                        .build();
        assertThat(config.isSourceEnabled(PROJECTION_SOURCE_DISPLAY)).isTrue();
        assertThat(config.isSourceEnabled(PROJECTION_SOURCE_DISPLAY_REGION)).isTrue();
        assertThat(config.isSourceEnabled(PROJECTION_SOURCE_APP)).isTrue();
        assertThat(config.getRequesterHint()).isEqualTo("requesterHint");
        assertThat(config.getInitiallySelectedSource()).isEqualTo(PROJECTION_SOURCE_APP);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_APP_CONTENT_SHARING)
    public void builder_setSourceWithBitmask() {
        MediaProjectionConfig config =
                new MediaProjectionConfig.Builder()
                        .setSourceEnabled(
                                PROJECTION_SOURCE_APP
                                        | PROJECTION_SOURCE_DISPLAY
                                        | PROJECTION_SOURCE_DISPLAY_REGION,
                                true)
                        .setSourceEnabled(PROJECTION_SOURCE_DISPLAY, true)
                        .setSourceEnabled(PROJECTION_SOURCE_DISPLAY_REGION, true)
                        .build();
        assertThat(config.isSourceEnabled(PROJECTION_SOURCE_DISPLAY)).isTrue();
        assertThat(config.isSourceEnabled(PROJECTION_SOURCE_DISPLAY_REGION)).isTrue();
        assertThat(config.isSourceEnabled(PROJECTION_SOURCE_APP)).isTrue();
        assertThat(
                        config.isSourceEnabled(
                                PROJECTION_SOURCE_APP
                                        | PROJECTION_SOURCE_DISPLAY
                                        | PROJECTION_SOURCE_DISPLAY_REGION))
                .isTrue();
    }

    @Test
    @ApiTest(apis = "android.media.projection.MediaProjectionConfig.Builder#setInitialSelection")
    @RequiresFlagsEnabled(Flags.FLAG_APP_CONTENT_SHARING)
    public void builder_validateInitialSelection() {

        try {
            //noinspection ResultOfMethodCallIgnored
            new MediaProjectionConfig.Builder()
                    .setInitiallySelectedSource(PROJECTION_SOURCE_APP | PROJECTION_SOURCE_DISPLAY)
                    .build();
        } catch (IllegalArgumentException ex) {
            // Test Passes
            return;
        }
        fail(
                "MediaProjectionConfig.Builder()#setInitialSelection() should throw on invalid "
                        + "projection type");
    }
}
