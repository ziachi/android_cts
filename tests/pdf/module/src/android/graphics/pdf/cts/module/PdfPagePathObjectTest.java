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

package android.graphics.pdf.cts.module;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Color;
import android.graphics.Path;
import android.graphics.pdf.component.PdfPageObjectRenderMode;
import android.graphics.pdf.component.PdfPageObjectType;
import android.graphics.pdf.component.PdfPagePathObject;
import android.graphics.pdf.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
public class PdfPagePathObjectTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final Path PATH = new Path();
    private static final int FILL_COLOR = Color.WHITE;
    private static final int STROKE_COLOR = Color.GREEN;
    private static final float STROKE_WIDTH = 5.0f;
    private static final int RENDER_MODE = PdfPageObjectRenderMode.FILL_STROKE;
    private PdfPagePathObject mPdfPagePathObject;

    @Before
    public void setup() {
        PATH.moveTo(0f, 800f);
        PATH.lineTo(100f, 650f);
        PATH.lineTo(150f, 650f);
        PATH.lineTo(0f, 800f);
        mPdfPagePathObject = new PdfPagePathObject(PATH);
    }

    @Test
    public void testPathPageObjectSetters() {
        assertThat(mPdfPagePathObject.getPdfObjectType()).isEqualTo(PdfPageObjectType.PATH);
        assertThat(mPdfPagePathObject.getRenderMode()).isEqualTo(PdfPageObjectRenderMode.FILL);

        // Path coordinates in the format [x0, y0, x1, y1,...]
        float[] expectedCoordinates = {0.0f, 800f, 100f, 650f, 150f, 650f, 0f, 800f};
        // Path segments in the format [fraction, x0, y0, fraction, x1, y1...]
        float[] obtainedSegments = mPdfPagePathObject.toPath().approximate(0.5f);

        for (int i = 0; i < obtainedSegments.length / 3; i++) {
            // Compare x-coordinates
            assertThat(obtainedSegments[3 * i + 1]).isEqualTo(expectedCoordinates[2 * i]);
            // Compare y-coordinates
            assertThat(obtainedSegments[3 * i + 2]).isEqualTo(expectedCoordinates[2 * i + 1]);
        }

        mPdfPagePathObject.setStrokeColor(STROKE_COLOR);
        assertThat(mPdfPagePathObject.getStrokeColor()).isEqualTo(STROKE_COLOR);

        mPdfPagePathObject.setStrokeWidth(STROKE_WIDTH);
        assertThat(mPdfPagePathObject.getStrokeWidth()).isEqualTo(STROKE_WIDTH);

        mPdfPagePathObject.setFillColor(FILL_COLOR);
        assertThat(mPdfPagePathObject.getFillColor()).isEqualTo(FILL_COLOR);

        mPdfPagePathObject.setRenderMode(RENDER_MODE);
        assertThat(mPdfPagePathObject.getRenderMode()).isEqualTo(RENDER_MODE);
    }
}
