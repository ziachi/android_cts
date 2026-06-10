/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.server.wm.other;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.PictureInPictureParams;
import android.app.PictureInPictureParams.Builder;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.util.Rational;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Tests the {@link PictureInPictureParams} builder.
 *
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceOther:PictureInPictureParamsBuilderTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PictureInPictureParamsBuilderTest {

    @Test
    public void testBuildParams() throws Exception {
        // Set the params
        Builder builder = new Builder()
                .setAspectRatio(new Rational(1, 2))
                .setActions(new ArrayList<>())
                .setSourceRectHint(new Rect(0, 0, 100, 100));

        PictureInPictureParams params = builder.build();
        assertTrue(Float.compare(0.5f, params.getAspectRatioFloat()) == 0);
        assertTrue(params.getActions().isEmpty());
        assertEquals(new Rect(0, 0, 100, 100), params.getSourceRectHint());

        // Reset the params
        builder.setAspectRatio(null)
                .setActions(null)
                .setSourceRectHint(null);
        params = builder.build();

        assertTrue(Float.compare(0f, params.getAspectRatioFloat()) == 0);
        assertTrue(params.getActions().isEmpty());
        assertNull(params.getSourceRectHint());
    }

    @Test
    public void testBuilderDefaultCtor() {
        // Construct the params with default Builder constructor
        PictureInPictureParams params = new Builder().build();

        // Ensures the PictureInPictureParams constructed has nothing being set
        assertNull(params.getAspectRatio());
        assertNull(params.getSourceRectHint());
        assertNull(params.getExpandedAspectRatio());
        assertTrue(params.getActions().isEmpty());
        assertNull(params.getCloseAction());
        assertNull(params.getTitle());
        assertNull(params.getSubtitle());
    }

    @Test
    public void testBuilderCopyCtor() {
        // Construct a PictureInPictureParams with some parameters being set
        PictureInPictureParams params = new Builder()
                .setAspectRatio(new Rational(1, 2))
                .setActions(new ArrayList<>())
                .setSourceRectHint(new Rect(0, 0, 100, 100))
                .build();

        // Build a new PictureInPictureParams using the copy constructor
        PictureInPictureParams newParams = new Builder(params).build();

        // Ensures the two PictureInPictureParams share the same parameters
        assertEquals(params.getAspectRatio(), newParams.getAspectRatio());
        assertEquals(params.getActions(), params.getActions());
        assertEquals(params.getSourceRectHint(), params.getSourceRectHint());
    }
}
