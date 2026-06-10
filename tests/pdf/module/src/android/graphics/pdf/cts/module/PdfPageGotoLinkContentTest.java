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
import android.graphics.pdf.content.PdfPageGotoLinkContent;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PdfPageGotoLinkContentTest {
    private static final RectF BOUND_ONE = new RectF(0, 0, 0, 0);

    private static final RectF BOUND_TWO = new RectF(1, 1, 1, 1);

    @Test
    public void goToLinkContent_parcelTest() {
        PdfPageGotoLinkContent content = buildPdfPageGotoLinkContent();
        PdfPageGotoLinkContent unparceled = Utils.writeAndReadFromParcel(content,
                PdfPageGotoLinkContent.CREATOR);

        assertThat(unparceled.getBounds()).isEqualTo(List.of(BOUND_ONE, BOUND_TWO));
        assertThat(unparceled.getDestination().getPageNumber()).isEqualTo(0);
        assertThat(unparceled.getDestination().getXCoordinate()).isEqualTo(0);
        assertThat(unparceled.getDestination().getYCoordinate()).isEqualTo(0);
        assertThat(unparceled.getDestination().getZoom()).isEqualTo(0);
        assertThat(unparceled.describeContents()).isEqualTo(0);
    }

    @Test
    public void destination_parcelTest() {
        PdfPageGotoLinkContent.Destination destination = buildDestination();
        PdfPageGotoLinkContent.Destination unparceled = Utils.writeAndReadFromParcel(destination,
                PdfPageGotoLinkContent.Destination.CREATOR);

        assertThat(unparceled.getPageNumber()).isEqualTo(0);
        assertThat(unparceled.getXCoordinate()).isEqualTo(0);
        assertThat(unparceled.getYCoordinate()).isEqualTo(0);
        assertThat(unparceled.getZoom()).isEqualTo(0);
        assertThat(unparceled.describeContents()).isEqualTo(0);
    }

    private PdfPageGotoLinkContent buildPdfPageGotoLinkContent() {
        List<RectF> mockBoundList = List.of(BOUND_ONE, BOUND_TWO);
        PdfPageGotoLinkContent.Destination mockDestination = buildDestination();

        return new PdfPageGotoLinkContent(mockBoundList, mockDestination);
    }

    private PdfPageGotoLinkContent.Destination buildDestination() {
        return new PdfPageGotoLinkContent.Destination(/* pageNumber = */ 0, /* xCoordinate = */
                0, /* yCoordinate = */ 0, /* zoom = */ 0);
    }
}
