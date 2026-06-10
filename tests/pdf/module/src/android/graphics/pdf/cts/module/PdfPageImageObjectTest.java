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
import android.graphics.Matrix;
import android.graphics.pdf.component.PdfPageImageObject;
import android.graphics.pdf.component.PdfPageObjectType;
import android.graphics.pdf.flags.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
public class PdfPageImageObjectTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final Bitmap BITMAP = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
    private static final Bitmap NEW_BITMAP = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
    private static final Matrix MATRIX = new Matrix();

    @Test
    public void testPdfPageImageObject() {
        PdfPageImageObject pdfPageImageObject = new PdfPageImageObject(BITMAP);
        assertThat(pdfPageImageObject.getBitmap()).isEqualTo(BITMAP);
        assertThat(pdfPageImageObject.getPdfObjectType()).isEqualTo(PdfPageObjectType.IMAGE);

        pdfPageImageObject.setBitmap(NEW_BITMAP);
        assertThat(pdfPageImageObject.getBitmap()).isEqualTo(NEW_BITMAP);

        assertThat(pdfPageImageObject.getMatrix())
                .isEqualTo(new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1});

        float[] expectedMatrixArray = {1, 2, 3, 4, 5, 6, 0, 0, 1};
        MATRIX.setValues(expectedMatrixArray);
        pdfPageImageObject.setMatrix(MATRIX);

        float[] receivedArray = pdfPageImageObject.getMatrix();
        assertThat(receivedArray.length).isEqualTo(expectedMatrixArray.length);

        for (int i = 0; i < receivedArray.length; i++) {
            assertThat(receivedArray[i]).isEqualTo(expectedMatrixArray[i]);
        }
    }

    @Test
    public void testPageObjectTransform() {
        PdfPageImageObject pdfPageImageObject = new PdfPageImageObject(BITMAP);

        float a = 0.0F, b = 1.0F, c = 2.0F, d = 3.0F, e = 4.0F, f = 5.0F;
        float[] expectedMatrix = {a, e, d, c, b, f, 0, 0, 1};
        pdfPageImageObject.transform(a, b, c, d, e, f);

        float[] transformedMatrix = pdfPageImageObject.getMatrix();
        assertThat(expectedMatrix.length).isEqualTo(transformedMatrix.length);
        for (int i = 0; i < expectedMatrix.length; i++) {
            assertThat(expectedMatrix[i]).isEqualTo(transformedMatrix[i]);
        }
    }
}
