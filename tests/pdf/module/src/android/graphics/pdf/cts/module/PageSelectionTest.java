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

import android.graphics.pdf.content.PdfPageTextContent;
import android.graphics.pdf.models.selection.PageSelection;
import android.graphics.pdf.models.selection.SelectionBoundary;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PageSelectionTest {
    private static final SelectionBoundary MOCK_START = new SelectionBoundary(1);

    private static final SelectionBoundary MOCK_STOP = new SelectionBoundary(100);

    private static final String MOCK_TEXT = "mockText";

    private static final PdfPageTextContent MOCK_TEXT_CONTENT = new PdfPageTextContent(MOCK_TEXT);

    private static final List<PdfPageTextContent> MOCK_TEXT_CONTENT_LIST = List.of(
            MOCK_TEXT_CONTENT);

    @Test
    public void pageSelection_parcelTest() {
        PageSelection pageSelection = new PageSelection(/* page = */ 1, MOCK_START, MOCK_STOP,
                MOCK_TEXT_CONTENT_LIST);
        PageSelection unparceled = Utils.writeAndReadFromParcel(pageSelection,
                PageSelection.CREATOR);

        assertThat(unparceled.getPage()).isEqualTo(1);
        assertThat(unparceled.getStart().getIndex()).isEqualTo(MOCK_START.getIndex());
        assertThat(unparceled.getStop().getIndex()).isEqualTo(MOCK_STOP.getIndex());
        assertThat(unparceled.getSelectedTextContents().isEmpty()).isFalse();
        assertThat(unparceled.getSelectedTextContents().get(0).getText()).isEqualTo(MOCK_TEXT);
        assertThat(unparceled.describeContents()).isEqualTo(0);
    }
}
