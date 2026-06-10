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

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.pdf.component.PdfAnnotationType;
import android.graphics.pdf.component.PdfPageImageObject;
import android.graphics.pdf.component.PdfPageObject;
import android.graphics.pdf.component.StampAnnotation;
import android.graphics.pdf.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_STAMP_ANNOTATIONS)
public class StampAnnotationTest {
    private static final RectF BOUNDS = new RectF(10, 20, 30, 40);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testStampAnnotation() {
        StampAnnotation stampAnnotation = new StampAnnotation(BOUNDS);

        assertThat(stampAnnotation.getPdfAnnotationType()).isEqualTo(PdfAnnotationType.STAMP);
        assertThat(stampAnnotation.getBounds()).isEqualTo(BOUNDS);

        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        PdfPageImageObject pdfPageImageObject = new PdfPageImageObject(bitmap);

        Bitmap bitmap2 = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
        PdfPageImageObject pdfPageImageObject2 = new PdfPageImageObject(bitmap2);

        stampAnnotation.addObject(pdfPageImageObject);
        stampAnnotation.addObject(pdfPageImageObject2);

        List<PdfPageObject> pdfPageObjects = stampAnnotation.getObjects();
        assertThat(pdfPageObjects).hasSize(2);
        assertThat(pdfPageObjects)
                .isEqualTo(new ArrayList<>(Arrays.asList(pdfPageImageObject, pdfPageImageObject2)));

        stampAnnotation.removeObject(0);
        PdfPageImageObject pageImageObject =
                (PdfPageImageObject) stampAnnotation.getObjects().get(0);
        assertThat(pageImageObject.getBitmap().getWidth()).isEqualTo(20);
        assertThat(pageImageObject.getBitmap().getHeight()).isEqualTo(20);
    }
}
