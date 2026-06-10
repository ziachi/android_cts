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
import android.graphics.pdf.component.FreeTextAnnotation;
import android.graphics.pdf.component.PdfAnnotationType;
import android.graphics.pdf.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_TEXT_ANNOTATIONS)
public class FreeTextAnnotationTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TEXT_CONTENT = "Hello World";
    private static final RectF BOUNDS = new RectF(1, 1, 1, 1);
    private static final RectF NEW_BOUNDS = new RectF(2, 2, 2, 2);
    private static final int DEFAULT_TEXT_COLOR = Color.BLACK;
    private static final int DEFAULT_BACKGROUND_COLOR = Color.WHITE;
    private static final String NEW_TEXT_CONTENT = "World Hello";
    private static final int NEW_TEXT_COLOR = Color.GREEN;
    private static final int NEW_BACKGROUND_COLOR = Color.RED;

    @Test
    public void pageFreeTextAnnotationContent_getTest() {
        FreeTextAnnotation pageFreeTextAnnotationContent =
                new FreeTextAnnotation(BOUNDS, TEXT_CONTENT);
        assertThat(pageFreeTextAnnotationContent.getPdfAnnotationType())
                .isEqualTo(PdfAnnotationType.FREETEXT);
        assertThat(pageFreeTextAnnotationContent.getBounds()).isEqualTo(BOUNDS);
        assertThat(pageFreeTextAnnotationContent.getTextContent()).isEqualTo(TEXT_CONTENT);
        assertThat(pageFreeTextAnnotationContent.getTextColor()).isEqualTo(DEFAULT_TEXT_COLOR);
        assertThat(pageFreeTextAnnotationContent.getBackgroundColor())
                .isEqualTo(DEFAULT_BACKGROUND_COLOR);
    }

    @Test
    public void pageFreeTextAnnotationContent_setTest() {
        FreeTextAnnotation pageFreeTextAnnotationContent =
                new FreeTextAnnotation(BOUNDS, TEXT_CONTENT);
        pageFreeTextAnnotationContent.setTextContent(NEW_TEXT_CONTENT);
        pageFreeTextAnnotationContent.setTextColor(NEW_TEXT_COLOR);
        pageFreeTextAnnotationContent.setBackgroundColor(NEW_BACKGROUND_COLOR);
        pageFreeTextAnnotationContent.setBounds(NEW_BOUNDS);
        assertThat(pageFreeTextAnnotationContent.getTextContent()).isEqualTo(NEW_TEXT_CONTENT);
        assertThat(pageFreeTextAnnotationContent.getTextColor()).isEqualTo(NEW_TEXT_COLOR);
        assertThat(pageFreeTextAnnotationContent.getBackgroundColor())
                .isEqualTo(NEW_BACKGROUND_COLOR);
        assertThat(pageFreeTextAnnotationContent.getBounds()).isEqualTo(NEW_BOUNDS);
    }
}
