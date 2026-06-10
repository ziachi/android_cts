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

import android.graphics.RectF;
import android.graphics.pdf.content.PdfPageTextContent;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PdfPageTextContentTest {
    private static final RectF BOUND_ONE = new RectF(0, 0, 0, 0);

    private static final RectF BOUND_TWO = new RectF(1, 1, 1, 1);

    private static final List<RectF> MOCK_BOUNDS = List.of(BOUND_ONE, BOUND_TWO);

    private static final String MOCK_TEXT = "mockText";

    @Test
    public void textContent_unboundedText_parcelTest() {
        PdfPageTextContent content = new PdfPageTextContent(MOCK_TEXT);
        PdfPageTextContent unparceled = Utils.writeAndReadFromParcel(content,
                PdfPageTextContent.CREATOR);

        assertThat(unparceled.getText()).isEqualTo(MOCK_TEXT);
        assertThat(unparceled.getBounds().isEmpty()).isTrue();
        assertThat(unparceled.describeContents()).isEqualTo(0);
    }

    @Test
    public void textContent_boundedText_parcelTest() {
        PdfPageTextContent content = new PdfPageTextContent(MOCK_TEXT, MOCK_BOUNDS);
        PdfPageTextContent unparceled = Utils.writeAndReadFromParcel(content,
                PdfPageTextContent.CREATOR);

        assertThat(unparceled.getText()).isEqualTo(MOCK_TEXT);
        assertThat(unparceled.getBounds()).isEqualTo(MOCK_BOUNDS);
        assertThat(unparceled.describeContents()).isEqualTo(0);
    }
}
