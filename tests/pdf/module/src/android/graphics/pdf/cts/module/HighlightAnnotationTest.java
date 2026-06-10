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
import android.graphics.RectF;
import android.graphics.pdf.component.HighlightAnnotation;
import android.graphics.pdf.component.PdfAnnotationType;
import android.graphics.pdf.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_ANNOTATIONS)
public class HighlightAnnotationTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final List<RectF> BOUNDS = List.of(new RectF(1, 2, 3, 4));
    private static final List<RectF> NEW_BOUNDS = List.of(new RectF(10, 20, 30, 40));

    private static final int DEFAULT_TEXT_COLOR = Color.YELLOW;
    private static final int NEW_TEXT_COLOR = Color.RED;

    @Test
    public void pageHighlightAnnotationContent_getTest() {
        HighlightAnnotation pdfPageHighlightAnnotationContent = new HighlightAnnotation(BOUNDS);
        assertThat(pdfPageHighlightAnnotationContent.getPdfAnnotationType())
                .isEqualTo(PdfAnnotationType.HIGHLIGHT);
        assertThat(pdfPageHighlightAnnotationContent.getBounds()).isEqualTo(BOUNDS);
        assertThat(pdfPageHighlightAnnotationContent.getColor()).isEqualTo(DEFAULT_TEXT_COLOR);
    }

    @Test
    public void pageFreeHighlightAnnotationContent_setTest() {
        HighlightAnnotation pdfPageHighlightAnnotationContent = new HighlightAnnotation(BOUNDS);
        pdfPageHighlightAnnotationContent.setBounds(NEW_BOUNDS);
        pdfPageHighlightAnnotationContent.setColor(NEW_TEXT_COLOR);
        assertThat(pdfPageHighlightAnnotationContent.getBounds()).isEqualTo(NEW_BOUNDS);
        assertThat(pdfPageHighlightAnnotationContent.getColor()).isEqualTo(NEW_TEXT_COLOR);
    }
}
