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

package android.server.wm.other;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowManagerTestBase;
import android.util.Rational;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the {@link PictureInPictureParams} class.
 *
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceOther:PictureInPictureParamsTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PictureInPictureParamsTest extends WindowManagerTestBase {

    /**
     * Tests that we get the same values back from the public PictureInPicture params getters that
     * were set via the PictureInPictureParams.Builder.
     */
    @Test
    public void testPictureInPictureParamsGetters() {
        ArrayList<RemoteAction> actions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            actions.add(createRemoteAction(0));
        }

        assertPictureInPictureParamsGettersMatchValues(
                actions,
                createRemoteAction(1),
                new Rational(1, 2),
                new Rational(100, 1),
                "Title",
                "Subtitle",
                new Rect(0, 0, 100, 100),
                true,
                true);
    }

    @Test
    public void testPictureInPictureParamsGettersNullValues() {
        assertPictureInPictureParamsGettersMatchValues(null, null, null, null, null, null, null,
                false, false);
    }

    @Test
    public void testPictureInPictureParams_autoEnterEnabledDefaultFalse() {
        PictureInPictureParams params = new PictureInPictureParams.Builder().build();

        assertFalse(params.isAutoEnterEnabled());
    }

    @Test
    public void testPictureInPictureParams_seamlessResizeEnabledDefaultFalse() {
        PictureInPictureParams params = new PictureInPictureParams.Builder().build();

        assertFalse(params.isSeamlessResizeEnabled());
    }

    @Test
    public void testIsSameAspectRatio_exactMatch_returnTrue() {
        final Rect bounds = new Rect(0, 0, 100, 200);
        final Rational aspectRatio = new Rational(1, 2);

        assertTrue(PictureInPictureParams.isSameAspectRatio(bounds, aspectRatio));
    }

    @Test
    public void testIsSameAspectRatio_width1PixelShorter_returnTrue() {
        final Rect bounds = new Rect(0, 0, 99, 200);
        final Rational aspectRatio = new Rational(1, 2);

        assertTrue(PictureInPictureParams.isSameAspectRatio(bounds, aspectRatio));
    }

    @Test
    public void testIsSameAspectRatio_width1PixelLonger_returnTrue() {
        final Rect bounds = new Rect(0, 0, 101, 200);
        final Rational aspectRatio = new Rational(1, 2);

        assertTrue(PictureInPictureParams.isSameAspectRatio(bounds, aspectRatio));
    }

    @Test
    public void testIsSameAspectRatio_height1PixelShorter_returnTrue() {
        final Rect bounds = new Rect(0, 0, 100, 199);
        final Rational aspectRatio = new Rational(1, 2);

        assertTrue(PictureInPictureParams.isSameAspectRatio(bounds, aspectRatio));
    }

    @Test
    public void testIsSameAspectRatio_height1PixelLonger_returnTrue() {
        final Rect bounds = new Rect(0, 0, 100, 201);
        final Rational aspectRatio = new Rational(1, 2);

        assertTrue(PictureInPictureParams.isSameAspectRatio(bounds, aspectRatio));
    }

    @Test
    public void testIsSameAspectRatio_width5PixelShorter_returnFalse() {
        final Rect bounds = new Rect(0, 0, 95, 200);
        final Rational aspectRatio = new Rational(1, 2);

        assertFalse(PictureInPictureParams.isSameAspectRatio(bounds, aspectRatio));
    }

    @Test
    public void testIsSameAspectRatio_width5PixelLonger_returnFalse() {
        final Rect bounds = new Rect(0, 0, 105, 200);
        final Rational aspectRatio = new Rational(1, 2);

        assertFalse(PictureInPictureParams.isSameAspectRatio(bounds, aspectRatio));
    }

    @Test
    public void testIsSameAspectRatio_height5PixelShorter_returnFalse() {
        final Rect bounds = new Rect(0, 0, 100, 195);
        final Rational aspectRatio = new Rational(1, 2);

        assertFalse(PictureInPictureParams.isSameAspectRatio(bounds, aspectRatio));
    }

    @Test
    public void testIsSameAspectRatio_height5PixelLonger_returnFalse() {
        final Rect bounds = new Rect(0, 0, 100, 205);
        final Rational aspectRatio = new Rational(1, 2);

        assertFalse(PictureInPictureParams.isSameAspectRatio(bounds, aspectRatio));
    }

    private void assertPictureInPictureParamsGettersMatchValues(List<RemoteAction> actions,
            RemoteAction closeAction, Rational aspectRatio, Rational expandedAspectRatio,
            String title, String subtitle, Rect sourceRectHint, boolean isAutoEnterEnabled,
            boolean isSeamlessResizeEnabled) {

        PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setActions(actions)
                .setCloseAction(closeAction)
                .setAspectRatio(aspectRatio)
                .setExpandedAspectRatio(expandedAspectRatio)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setSourceRectHint(sourceRectHint)
                .setAutoEnterEnabled(isAutoEnterEnabled)
                .setSeamlessResizeEnabled(isSeamlessResizeEnabled)
                .build();

        if (actions == null) {
            assertEquals(new ArrayList<>(), params.getActions());
        } else {
            assertEquals(actions, params.getActions());
        }
        assertEquals(closeAction, params.getCloseAction());
        assertEquals(aspectRatio, params.getAspectRatio());
        assertEquals(expandedAspectRatio, params.getExpandedAspectRatio());
        assertEquals(title, params.getTitle() == null ? null : params.getTitle().toString());
        assertEquals(subtitle,
                params.getSubtitle() == null ? null : params.getSubtitle().toString());
        assertEquals(sourceRectHint, params.getSourceRectHint());
        assertEquals(isAutoEnterEnabled, params.isAutoEnterEnabled());
        assertEquals(isSeamlessResizeEnabled, params.isSeamlessResizeEnabled());
    }

    /** @return {@link RemoteAction} instance titled after a given index */
    private RemoteAction createRemoteAction(int index) {
        return new RemoteAction(
                Icon.createWithBitmap(Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)),
                "action " + index,
                "contentDescription " + index,
                PendingIntent.getBroadcast(mContext, 0, new Intent(),
                        PendingIntent.FLAG_IMMUTABLE));
    }
}
