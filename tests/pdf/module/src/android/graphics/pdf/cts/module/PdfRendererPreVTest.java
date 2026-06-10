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

import static android.graphics.pdf.PdfRendererPreV.DOCUMENT_LINEARIZED_TYPE_LINEARIZED;
import static android.graphics.pdf.PdfRendererPreV.DOCUMENT_LINEARIZED_TYPE_NON_LINEARIZED;
import static android.graphics.pdf.cts.module.Utils.A4_HEIGHT_PTS;
import static android.graphics.pdf.cts.module.Utils.A4_PORTRAIT;
import static android.graphics.pdf.cts.module.Utils.A4_WIDTH_PTS;
import static android.graphics.pdf.cts.module.Utils.EMPTY_PDF;
import static android.graphics.pdf.cts.module.Utils.INCORRECT_LOAD_PARAMS;
import static android.graphics.pdf.cts.module.Utils.LOAD_PARAMS;
import static android.graphics.pdf.cts.module.Utils.ONE_IMAGE_PAGE_OBJECT;
import static android.graphics.pdf.cts.module.Utils.ONE_PATH_ONE_IMAGE_PAGE_OBJECT;
import static android.graphics.pdf.cts.module.Utils.ONE_PATH_PAGE_OBJECT;
import static android.graphics.pdf.cts.module.Utils.ONE_STAMP_ANNOTATION;
import static android.graphics.pdf.cts.module.Utils.PROTECTED_PDF;
import static android.graphics.pdf.cts.module.Utils.SAMPLE_IMAGE;
import static android.graphics.pdf.cts.module.Utils.SAMPLE_PDF;
import static android.graphics.pdf.cts.module.Utils.assertSelectionBoundary;
import static android.graphics.pdf.cts.module.Utils.calculateArea;
import static android.graphics.pdf.cts.module.Utils.createPreVRenderer;
import static android.graphics.pdf.cts.module.Utils.getFile;
import static android.graphics.pdf.cts.module.Utils.getParcelFileDescriptorFromResourceId;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.pdf.PdfRendererPreV;
import android.graphics.pdf.RenderParams;
import android.graphics.pdf.component.FreeTextAnnotation;
import android.graphics.pdf.component.HighlightAnnotation;
import android.graphics.pdf.component.PdfAnnotation;
import android.graphics.pdf.component.PdfAnnotationType;
import android.graphics.pdf.component.PdfPageImageObject;
import android.graphics.pdf.component.PdfPageObject;
import android.graphics.pdf.component.PdfPageObjectType;
import android.graphics.pdf.component.PdfPagePathObject;
import android.graphics.pdf.component.StampAnnotation;
import android.graphics.pdf.content.PdfPageGotoLinkContent;
import android.graphics.pdf.flags.Flags;
import android.graphics.pdf.models.PageMatchBounds;
import android.graphics.pdf.models.selection.PageSelection;
import android.graphics.pdf.models.selection.SelectionBoundary;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Pair;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class PdfRendererPreVTest {

    private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\\R");
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void constructRenderer_fromNull_throwsException() throws Exception {
        assertThrows(NullPointerException.class, () -> new PdfRendererPreV(null, LOAD_PARAMS));
    }

    @Test
    public void constructRenderer_fromNullWithoutLoadParams_throwsException() throws Exception {
        assertThrows(NullPointerException.class, () -> new PdfRendererPreV(null));
    }

    @Test
    public void constructRenderer_withoutLoadParams_throwsException() throws Exception {
        assertThrows(NullPointerException.class, () -> new PdfRendererPreV(
                getParcelFileDescriptorFromResourceId(SAMPLE_PDF, mContext), null));
    }

    @Test
    public void constructRenderer_fromNonPDF_throwsException() throws Exception {
        assertThrows(IOException.class,
                () -> createPreVRenderer(R.raw.testimage, mContext, LOAD_PARAMS));
    }

    @Test
    public void constructRenderer_fromNonPDF_withoutLoadParams() throws Exception {
        assertThrows(IOException.class, () -> createPreVRenderer(R.raw.testimage, mContext, null));
    }

    @Test
    public void constructRenderer_protectedPdfWithWrongPassword_throwsException() throws Exception {
        assertThrows(SecurityException.class,
                () -> createPreVRenderer(PROTECTED_PDF, mContext, INCORRECT_LOAD_PARAMS));
    }

    @Test
    public void rendererRecoversAfterFailure() throws Exception {
        assertThrows(SecurityException.class,
                () -> createPreVRenderer(PROTECTED_PDF, mContext, INCORRECT_LOAD_PARAMS));
        // We can create new renderers after we failed to create one
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        renderer.close();
    }

    @Test
    public void useRenderer_afterRendererClose_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        renderer.close();

        assertThrows(IllegalStateException.class, renderer::close);
        assertThrows(IllegalStateException.class, renderer::getPageCount);
        assertThrows(IllegalStateException.class, renderer::getDocumentLinearizationType);
        assertThrows(IllegalStateException.class, () -> renderer.write(null, true));
        assertThrows(IllegalStateException.class, () -> renderer.openPage(0));
    }

    @Test
    public void usePage_afterRendererClose_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page page = renderer.openPage(0);
        renderer.close();

        assertPageException(page);
    }

    @Test
    public void usePage_afterPageClose_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page page = renderer.openPage(0);
        page.close();

        assertPageException(page);
        renderer.close();
    }

    @Test
    public void useRenderer_afterPageClose() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page page = renderer.openPage(0);
        page.close();

        assertThat(renderer.openPage(0)).isNotNull();
        renderer.close();
    }

    @Test
    public void getPdfPageCount() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        assertThat(renderer.getPageCount()).isEqualTo(3);
        renderer.close();
    }

    @Test
    public void getDocumentType_withNonLinearizedPdf() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, LOAD_PARAMS);
        assertThat(renderer.getDocumentLinearizationType()).isEqualTo(
                DOCUMENT_LINEARIZED_TYPE_NON_LINEARIZED);
        renderer.close();
    }

    @Test
    public void getDocumentType_withLinearizedPdf() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        assertThat(renderer.getDocumentLinearizationType()).isEqualTo(
                DOCUMENT_LINEARIZED_TYPE_LINEARIZED);
        renderer.close();
    }

    @Test
    public void getPageDimension() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(A4_PORTRAIT, mContext, null);
        PdfRendererPreV.Page page = renderer.openPage(0);

        assertThat(page.getWidth()).isEqualTo(A4_WIDTH_PTS);
        assertThat(page.getHeight()).isEqualTo(A4_HEIGHT_PTS);

        page.close();
        renderer.close();
    }

    @Test
    public void getPdfPageTextContents_pdfWithText() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.getTextContents().size()).isEqualTo(1);
        assertThat(firstPage.getTextContents().get(0).getText()).isEqualTo(
                "A Simple PDF File, which will be used for testing.");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getOpenPage_pageOutOfBounds_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);

        assertThrows(IllegalArgumentException.class, () -> renderer.openPage(-1));
        PdfRendererPreV.Page page0 = renderer.openPage(0);
        page0.close();
        PdfRendererPreV.Page page1 = renderer.openPage(1);
        page1.close();
        assertThrows(IllegalArgumentException.class, () -> renderer.openPage(3));
    }

    @Test
    public void getPageIndex() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page page = renderer.openPage(2);

        assertThat(page.getIndex()).isEqualTo(2);

        page.close();
        renderer.close();

    }

    @Test
    public void getPdfPageTextContents_pdfWithTextAndImages() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(R.raw.alt_text, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.getTextContents().size()).isEqualTo(1);
        List<String> textContents = LINE_BREAK_PATTERN.splitAsStream(
                firstPage.getTextContents().get(0).getText()).collect(Collectors.toList());

        assertThat(textContents.size()).isEqualTo(2);
        assertThat(textContents.get(0)).isEqualTo("Social Security Administration Guide:");
        assertThat(textContents.get(1)).isEqualTo("Alternate text for images");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getPdfPageImageContents_pdfWithText() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.getImageContents().size()).isEqualTo(0);

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getPdfPageImageContents_pdfWithAltText() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(R.raw.alt_text, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.getImageContents().size()).isEqualTo(1);
        assertThat(firstPage.getImageContents().get(0).getAltText()).isEqualTo(
                "Social Security Administration Logo");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getPageLinks_pdfWithoutLink() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(R.raw.sample_links, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        assertThat(firstPage.getLinkContents().size()).isEqualTo(0);

        firstPage.close();
        renderer.close();
    }

    @Test
    public void getPageLinks_pdfWithLink() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(R.raw.sample_links, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.getLinkContents().size()).isEqualTo(1);
        assertThat(firstPage.getLinkContents().get(0).getBounds().size()).isEqualTo(1);
        assertThat(firstPage.getLinkContents().get(0).getUri().getScheme()).isEqualTo("http");
        assertThat(firstPage.getLinkContents().get(0).getUri().getSchemeSpecificPart()).isEqualTo(
                "//www.antennahouse.com/purchase.htm");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void searchPageText() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);
        int firstPageWidth = firstPage.getWidth();
        int firstPageHeight = firstPage.getHeight();
        // Second page
        PdfRendererPreV.Page secondPage = renderer.openPage(1);
        int secondPageWidth = secondPage.getWidth();
        int secondPageHeight = secondPage.getHeight();
        // Third page,
        PdfRendererPreV.Page thirdPage = renderer.openPage(2);

        // First query, First page only contains single "simple"
        List<PageMatchBounds> firstPageRects = firstPage.searchText("simple");

        assertThat(firstPageRects.size()).isEqualTo(1);
        assertThat(firstPageRects.get(0).getBounds().size()).isEqualTo(1);
        assertThat(firstPageRects.get(0).getTextStartIndex()).isEqualTo(2);
        // the rects area should be less than the page area
        assertThat(calculateArea(firstPageRects)).isLessThan(firstPageHeight * firstPageWidth);
        firstPage.close();

        // second query
        List<PageMatchBounds> secondPageRects = secondPage.searchText("more");

        assertThat(secondPageRects.size()).isEqualTo(28);
        // assert that size of all rects are 1 as more does not extend to other lines
        for (PageMatchBounds rect : secondPageRects) {
            assertThat(rect.getBounds().size()).isEqualTo(1);
        }
        // the rects area should be less than the page area
        assertThat(calculateArea(secondPageRects)).isLessThan(secondPageHeight * secondPageWidth);

        //third query assert Heading, the area should be less than the page area
        List<PageMatchBounds> thirdPageRects = thirdPage.searchText("Simple PDF File 2");

        assertThat(thirdPageRects.size()).isEqualTo(1);
        assertThat(thirdPageRects.get(0).getBounds().size()).isEqualTo(1);
        int thirdPageWidth = thirdPage.getWidth();
        int thirdPageHeight = thirdPage.getHeight();
        assertThat(calculateArea(thirdPageRects)).isLessThan(thirdPageHeight * thirdPageWidth);

        thirdPage.close();
        renderer.close();
    }

    @Test
    public void searchPageText_querryTextInMultipleLines() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page openPage = renderer.openPage(1);
        int secondPageWidth = openPage.getWidth();
        int secondPageHeight = openPage.getHeight();

        List<PageMatchBounds> secondPageRects = openPage.searchText("more text");

        assertThat(secondPageRects.size()).isEqualTo(27);
        // assert that size of all rects are less than 3
        int count = 0;
        for (PageMatchBounds rect : secondPageRects) {
            count += rect.getBounds().size();
            assertThat(rect.getBounds().size()).isLessThan(3);
        }
        // 3 of the "More Text" are split via lines so overall count for rects should be 30
        assertThat(count).isEqualTo(30);
        // the rects area should be less than the page area
        assertThat(calculateArea(secondPageRects)).isLessThan(secondPageHeight * secondPageWidth);

        openPage.close();
        renderer.close();
    }

    @Test
    public void searchPageText_returnsEmptySearchResult() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);

        assertThat(firstPage.searchText("z").isEmpty()).isTrue();

        firstPage.close();
        renderer.close();
    }

    @Test
    public void searchPageText_withNullQuerry_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page openPage = renderer.openPage(1);

        assertThrows(NullPointerException.class, () -> openPage.searchText(null));
    }

    @Test
    public void write_withNullDest_throwsException() throws Exception {
        PdfRendererPreV expectedRenderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        assertThrows(NullPointerException.class, () -> expectedRenderer.write(null, true));
    }

    //TODO: Update the test to assert pdfs.
    @Test
    public void write_protectedPdf_withSecurity() throws Exception {
        PdfRendererPreV expectedRenderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        File filePath = getFile(mContext, "ProtectedPreV.pdf");
        String absolutePath = filePath.getAbsolutePath();
        ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(filePath,
                ParcelFileDescriptor.MODE_READ_WRITE);

        assert descriptor != null;
        expectedRenderer.write(descriptor, false);

        // get the file descriptor for the saved as file
        File saveAsFile = new File(absolutePath);
        ParcelFileDescriptor saveAsDescp = ParcelFileDescriptor.open(saveAsFile,
                ParcelFileDescriptor.MODE_READ_ONLY);

        // create a renderer with password for this file descriptor
        assertThat(saveAsDescp).isNotNull();
        PdfRendererPreV renderer = new PdfRendererPreV(saveAsDescp, LOAD_PARAMS);
        assertThat(renderer).isNotNull();

        renderer.close();
    }

    @Test
    public void write_withUnprotected() throws Exception {
        PdfRendererPreV expectedRenderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        File filePath = getFile(mContext, "UnprotectedPreV.pdf");
        String absolutePath = filePath.getAbsolutePath();
        ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(filePath,
                ParcelFileDescriptor.MODE_READ_WRITE);

        assert descriptor != null;
        expectedRenderer.write(descriptor, true);

        // get the file descriptor for the saved as file
        File saveAsFile = new File(absolutePath);
        ParcelFileDescriptor saveAsDescp = ParcelFileDescriptor.open(saveAsFile,
                ParcelFileDescriptor.MODE_READ_ONLY);

        // create a renderer without password for this file descriptor
        assertThat(saveAsDescp).isNotNull();
        PdfRendererPreV renderer = new PdfRendererPreV(saveAsDescp);
        assertSamplePdf(renderer, expectedRenderer);
    }

    @Test
    public void selectPageText() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        Point point = new Point(77, 104);
        SelectionBoundary start = new SelectionBoundary(point);
        SelectionBoundary stop = new SelectionBoundary(point);
        // first query
        PageSelection firstTextSelection = firstPage.selectContent(start, stop);
        // second query
        Point leftPoint = new Point(93, 139);
        Point rightPoint = new Point(147, 139);
        PageSelection secondTextSelection = firstPage.selectContent(
                new SelectionBoundary(leftPoint), new SelectionBoundary(rightPoint));

        // first selected text is: "this"
        assertThat(firstTextSelection.getPage()).isEqualTo(1);
        assertSelectionBoundary(firstTextSelection.getStart(), -1, new Point(72, 103));
        assertSelectionBoundary(firstTextSelection.getStop(), -1, new Point(91, 103));
        assertPageSelection(firstTextSelection, 1, 1);
        assertThat(firstTextSelection.getSelectedTextContents().get(0).getText()).isEqualTo("This");
        // assert second selected content
        assertPageSelection(secondTextSelection, 1, 1);
        assertThat(secondTextSelection.getSelectedTextContents().get(0).getText()).isEqualTo(
                "And more te");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void selectPageText_textSpreadAcrossMultipleLines() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        Point leftPoint = new Point(93, 139);
        Point rightPoint = new Point(135, 168);
        PageSelection textSelection = firstPage.selectContent(new SelectionBoundary(leftPoint),
                new SelectionBoundary(rightPoint));

        assertThat(textSelection.getPage()).isEqualTo(1);
        assertSelectionBoundary(textSelection.getStart(), -1, new Point(93, 139));
        assertSelectionBoundary(textSelection.getStop(), -1, new Point(135, 163));
        assertPageSelection(textSelection, 2, 1);

        List<String> selectedText = LINE_BREAK_PATTERN.splitAsStream(
                textSelection.getSelectedTextContents().get(0).getText()).collect(
                Collectors.toList());

        assertThat(selectedText.size()).isEqualTo(2);
        assertThat(selectedText.get(0)).isEqualTo("And more text. And more text. And more text. ");
        assertThat(selectedText.get(1)).isEqualTo(" And more text");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void selectPageText_rightToLeft() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        Point leftPoint = new Point(275, 163);
        Point rightPoint = new Point(65, 125);
        PageSelection fourthTextSelection = firstPage.selectContent(
                new SelectionBoundary(leftPoint), new SelectionBoundary(rightPoint));

        assertThat(fourthTextSelection.getPage()).isEqualTo(1);
        assertSelectionBoundary(fourthTextSelection.getStart(), -1, new Point(71, 127));
        assertSelectionBoundary(fourthTextSelection.getStop(), -1, new Point(275, 163));
        assertPageSelection(fourthTextSelection, 3, 1);

        List<String> selectedText = LINE_BREAK_PATTERN.splitAsStream(
                fourthTextSelection.getSelectedTextContents().get(0).getText()).collect(
                Collectors.toList());

        assertThat(selectedText.get(0)).isEqualTo(
                "just for use in the Virtual Mechanics tutorials. More text. And more ");
        assertThat(selectedText.get(1)).isEqualTo(
                " text. And more text. And more text. And more text. ");
        assertThat(selectedText.get(2)).isEqualTo(" And more text. And more text. And more text. ");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void selectPageText_withOnlyCharIndex() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);

        Point rightPoint = new Point(225, 168);
        PageSelection pageSelection = firstPage.selectContent(new SelectionBoundary(2),
                new SelectionBoundary(rightPoint));

        assertThat(pageSelection.getPage()).isEqualTo(0);
        assertSelectionBoundary(pageSelection.getStart(), -1, new Point(71, 52));
        assertSelectionBoundary(pageSelection.getStop(), -1, new Point(225, 52));

        assertThat(pageSelection.getSelectedTextContents().get(0).getText()).isEqualTo(
                "Simple PDF File, which will be ");

        firstPage.close();
        renderer.close();
    }

    @Test
    public void selectPageText_emptySpace() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        Point leftPoint = new Point(157, 330);
        Point rightPoint = new Point(157, 330);
        PageSelection pageSelection = firstPage.selectContent(new SelectionBoundary(leftPoint),
                new SelectionBoundary(rightPoint));

        assertThat(pageSelection).isNull();

        firstPage.close();
        renderer.close();
    }

    @Test
    public void selectPageText_withoutLeftBoundary_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        Point rightPoint = new Point(157, 330);
        assertThrows(NullPointerException.class,
                () -> firstPage.selectContent(null, new SelectionBoundary(rightPoint)));

    }

    @Test
    public void selectPageText_withoutRightBoundary_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        Point point = new Point(157, 330);
        assertThrows(NullPointerException.class,
                () -> firstPage.selectContent(new SelectionBoundary(point), null));
    }

    @Test
    public void selectPageText_withNegativeIndex_throwsException() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(PROTECTED_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        assertThrows(IllegalArgumentException.class,
                () -> firstPage.selectContent(new SelectionBoundary(-1), null));
    }

    @Test
    public void selectPageText_rightPointMovingTowardsLeft_returnsMultipleSelectedObjects()
            throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, LOAD_PARAMS);
        PdfRendererPreV.Page firstPage = renderer.openPage(1);

        Point leftPoint = new Point(244, 70);
        int rightPointXCoordinate = 284;
        int rightAndLeftPointXCoordinateDifference = 40;
        int emptyAreaXCoordinateDifferenceFromRightCoordinate = 35;

        // We are moving the right point towards the left point by a margin of 5.
        // Note: Tha margin can be any whole number except 0.
        for (int i = 0; i <= rightAndLeftPointXCoordinateDifference; i += 5) {
            Point nextRightPoint = new Point(rightPointXCoordinate - i, 70);

            PageSelection pageSelection = firstPage.selectContent(new SelectionBoundary(leftPoint),
                    new SelectionBoundary(nextRightPoint));

            if (i == emptyAreaXCoordinateDifferenceFromRightCoordinate) {
                // This is the point where the selected area between the boundaries is empty.
                assertThat(pageSelection).isNull();
            } else {
                assertThat(pageSelection).isNotNull();
            }
        }

        firstPage.close();
        renderer.close();
    }

    @Test
    public void renderPage_withNullParams_throwsException() throws Exception {
        try (PdfRendererPreV renderer = createPreVRenderer(A4_PORTRAIT, mContext, null);
             PdfRendererPreV.Page page = renderer.openPage(0)) {
            assertThrows(NullPointerException.class, () -> page.render(
                    Bitmap.createBitmap(A4_WIDTH_PTS, A4_HEIGHT_PTS, Bitmap.Config.ARGB_8888), null,
                    null, null));
        }
    }

    @Test
    public void getPageGotoLinks_pageWithoutGotoLink() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(SAMPLE_PDF, mContext, null);
        PdfRendererPreV.Page page = renderer.openPage(0);

        assertThat(page.getGotoLinks()).isEmpty();

        page.close();
        renderer.close();
    }

    @Test
    public void getPageGotoLinks_pageWithGotoLink() throws Exception {
        PdfRendererPreV renderer = createPreVRenderer(R.raw.sample_links, mContext, null);
        PdfRendererPreV.Page page = renderer.openPage(0);

        assertThat(page.getGotoLinks().size()).isEqualTo(1);
        //assert destination
        PdfPageGotoLinkContent.Destination destination = page.getGotoLinks().get(
                0).getDestination();
        assertThat(destination.getPageNumber()).isEqualTo(1);
        assertThat(destination.getXCoordinate()).isEqualTo((float) 0.0);
        assertThat(destination.getYCoordinate()).isEqualTo((float) 85.0);
        assertThat(destination.getZoom()).isEqualTo((float) 0.0);

        //assert coordinates
        assertThat(page.getGotoLinks().get(0).getBounds()).hasSize(1);
        RectF rect = page.getGotoLinks().get(0).getBounds().get(0);
        assertThat(rect.left).isEqualTo(91);
        assertThat(rect.top).isEqualTo(246);
        assertThat(rect.right).isEqualTo(235);
        assertThat(rect.bottom).isEqualTo(262);

        page.close();
        renderer.close();
    }

    @Test
    public void testGetPdfPageAnnotations_pdfWithNoAnnotation() throws Exception {
        try (PdfRendererPreV renderer = createPreVRenderer(EMPTY_PDF, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            assertThat(firstPage.getPageAnnotations().size()).isEqualTo(0);
        }
    }

    @Test
    public void testGetPdfPageAnnotations_pdfWithStampAnnotation() throws Exception {
        try (PdfRendererPreV renderer = createPreVRenderer(ONE_STAMP_ANNOTATION, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            List<Pair<Integer, PdfAnnotation>> annotations = firstPage.getPageAnnotations();
            assertThat(annotations.size()).isEqualTo(1);
            assertThat(annotations.get(0).second).isNotNull();
            assertThat(annotations.get(0).second.getPdfAnnotationType())
                    .isEqualTo(PdfAnnotationType.STAMP);
            StampAnnotation stampAnnotation = (StampAnnotation) annotations.get(0).second;
            List<PdfPageObject> pageObjects = stampAnnotation.getObjects();
            assertThat(pageObjects.size()).isEqualTo(2);
            assertThat(pageObjects.get(0).getPdfObjectType()).isEqualTo(PdfPageObjectType.IMAGE);
            assertThat(pageObjects.get(1).getPdfObjectType()).isEqualTo(PdfPageObjectType.PATH);
        }
    }

    @Test
    public void testGetPdfPageAnnotations_pdfWithHighlightAnnotation() throws Exception {
        try (PdfRendererPreV renderer =
                        createPreVRenderer(R.raw.one_highlight_annotation, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            List<Pair<Integer, PdfAnnotation>> annotations = firstPage.getPageAnnotations();
            assertThat(annotations.size()).isEqualTo(1);
            assertThat(annotations.get(0).second).isNotNull();
            assertThat(annotations.get(0).second.getPdfAnnotationType())
                    .isEqualTo(PdfAnnotationType.HIGHLIGHT);
        }
    }

    @Test
    public void getFreeTextAnnotation() throws IOException {
        try (PdfRendererPreV renderer =
                        createPreVRenderer(R.raw.free_text_annotation, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {
            List<Pair<Integer, PdfAnnotation>> pdfAnnotations = firstPage.getPageAnnotations();
            assertThat(pdfAnnotations).hasSize(1);
            assertThat(pdfAnnotations.get(0).first).isEqualTo(0);
            FreeTextAnnotation freeTextAnnotation =
                    (FreeTextAnnotation) pdfAnnotations.get(0).second;
            assertThat(freeTextAnnotation.getTextContent()).isEqualTo("Third annotation こんにちは");
            assertThat(freeTextAnnotation.getTextColor()).isEqualTo(Color.RED);
            assertThat(freeTextAnnotation.getBackgroundColor()).isEqualTo(Color.GREEN);
        }
    }

    @Test
    public void testAddPdfHighlightAnnotation() throws Exception {
        try (PdfRendererPreV renderer = createPreVRenderer(EMPTY_PDF, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            List<RectF> bounds = new ArrayList<>();
            bounds.add(new RectF(10, 20, 30, 40));
            HighlightAnnotation highlightAnnotation = new HighlightAnnotation(bounds);
            highlightAnnotation.setColor(Color.GREEN);

            int id = firstPage.addPageAnnotation(highlightAnnotation);
            assertThat(id).isEqualTo(0);

            List<Pair<Integer, PdfAnnotation>> annotations = firstPage.getPageAnnotations();
            assertThat(annotations.size()).isEqualTo(1);
            assertThat(annotations.get(0).second).isNotNull();
            assertThat(annotations.get(0).second.getPdfAnnotationType())
                    .isEqualTo(PdfAnnotationType.HIGHLIGHT);
            HighlightAnnotation addedHighlightAnnotation =
                    (HighlightAnnotation) annotations.get(0).second;
            assertThat(addedHighlightAnnotation.getBounds()).isEqualTo(bounds);
            assertThat(highlightAnnotation.getColor()).isEqualTo(Color.GREEN);
        }
    }

    @Test
    public void testAddFreeTextAnnotation() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(EMPTY_PDF, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {
            // Create a FreeTextAnnotation Instance and set the properties.
            RectF bounds = new RectF(10, 20, 30, 40);
            String textContent = "FreeText Annotation Test こんにちは";
            FreeTextAnnotation freeTextAnnotation = new FreeTextAnnotation(bounds, textContent);
            freeTextAnnotation.setTextColor(Color.GRAY);
            freeTextAnnotation.setBackgroundColor(Color.BLUE);
            // Add FreeTextAnnotation
            firstPage.addPageAnnotation(freeTextAnnotation);
            assertThat(firstPage.getPageAnnotations()).hasSize(1);
            // Assert properties of added annotation.
            FreeTextAnnotation addedFreeTextAnnotation =
                    (FreeTextAnnotation) firstPage.getPageAnnotations().get(0).second;
            assertThat(addedFreeTextAnnotation.getTextContent()).isEqualTo(textContent);
            assertThat(addedFreeTextAnnotation.getTextColor()).isEqualTo(Color.GRAY);
            assertThat(addedFreeTextAnnotation.getBackgroundColor()).isEqualTo(Color.BLUE);
        }
    }

    @Test
    public void testUpdateFreeTextAnnotation() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(EMPTY_PDF, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {
            RectF bounds = new RectF(10, 20, 30, 40);
            String textContent = "FreeText Annotation Test こんにちは";
            FreeTextAnnotation freeTextAnnotation = new FreeTextAnnotation(bounds, textContent);
            freeTextAnnotation.setTextColor(Color.GRAY);
            freeTextAnnotation.setBackgroundColor(Color.DKGRAY);

            firstPage.addPageAnnotation(freeTextAnnotation);
            assertThat(firstPage.getPageAnnotations()).hasSize(1);
            FreeTextAnnotation addedFreeTextAnnotation =
                    (FreeTextAnnotation) firstPage.getPageAnnotations().get(0).second;

            int oldId = firstPage.getPageAnnotations().get(0).first;
            String newTextContent = "Bye World.";
            addedFreeTextAnnotation.setTextContent(newTextContent);
            addedFreeTextAnnotation.setTextColor(Color.RED);
            addedFreeTextAnnotation.setBackgroundColor(Color.BLUE);
            boolean result = firstPage.updatePageAnnotation(oldId, addedFreeTextAnnotation);
            assertThat(result).isTrue();

            FreeTextAnnotation updatedFreeTextAnnotation =
                    (FreeTextAnnotation) firstPage.getPageAnnotations().get(0).second;
            assertThat(updatedFreeTextAnnotation.getTextContent()).isEqualTo(newTextContent);
            assertThat(updatedFreeTextAnnotation.getTextColor()).isEqualTo(Color.RED);
            assertThat(updatedFreeTextAnnotation.getBackgroundColor()).isEqualTo(Color.BLUE);
        }
    }

    @Test
    public void testRemoveHighlightAnnotation_oneHighlightAnnotation() throws IOException {
        try (PdfRendererPreV renderer =
                        createPreVRenderer(R.raw.one_highlight_annotation, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            List<Pair<Integer, PdfAnnotation>> annotations = firstPage.getPageAnnotations();
            assertThat(annotations.size()).isEqualTo(1);
            firstPage.removePageAnnotation(annotations.get(0).first);
            assertThat(firstPage.getPageAnnotations().size()).isEqualTo(0);
        }
    }

    @Test
    public void testRemoveHighlightAnnotationAfterAdding() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(EMPTY_PDF, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            List<RectF> bounds = new ArrayList<>();
            bounds.add(new RectF(10, 20, 30, 40));
            HighlightAnnotation highlightAnnotation = new HighlightAnnotation(bounds);
            highlightAnnotation.setColor(Color.GREEN);

            int id = firstPage.addPageAnnotation(highlightAnnotation);
            assertThat(id).isEqualTo(0);

            List<Pair<Integer, PdfAnnotation>> annotations = firstPage.getPageAnnotations();
            assertThat(annotations.size()).isEqualTo(1);
            assertThat(annotations.get(0).first).isEqualTo(0);

            firstPage.removePageAnnotation(annotations.get(0).first);
            assertThat(firstPage.getPageAnnotations().size()).isEqualTo(0);
        }
    }

    @Test
    public void testAddStampAnnotation() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(EMPTY_PDF, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            RectF bounds = new RectF(0, 0, 200, 200);
            StampAnnotation stampAnnotation = new StampAnnotation(bounds);
            // Create a ImagePageObject
            Bitmap bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888);
            PdfPageImageObject imagePageObject = new PdfPageImageObject(bitmap);
            stampAnnotation.addObject(imagePageObject);
            stampAnnotation.addObject(createSamplePdfPagePathObject());

            int id = firstPage.addPageAnnotation(stampAnnotation);
            assertThat(id).isEqualTo(0);

            List<Pair<Integer, PdfAnnotation>> annotations = firstPage.getPageAnnotations();
            assertThat(annotations.size()).isEqualTo(1);
            assertThat(annotations.get(0).second.getPdfAnnotationType())
                    .isEqualTo(PdfAnnotationType.STAMP);

            StampAnnotation addedStampAnnotation = (StampAnnotation) annotations.get(0).second;
            assertThat(addedStampAnnotation.getBounds()).isEqualTo(bounds);

            List<PdfPageObject> stampAnnotationObjects = addedStampAnnotation.getObjects();
            assertThat(stampAnnotationObjects.size()).isEqualTo(2);
            assertThat(stampAnnotationObjects.get(0).getPdfObjectType())
                    .isEqualTo(PdfPageObjectType.IMAGE);
            assertThat(stampAnnotationObjects.get(1).getPdfObjectType())
                    .isEqualTo(PdfPageObjectType.PATH);
        }
    }

    @Test
    public void testRemoveStampAnnotation() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(ONE_STAMP_ANNOTATION, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {
            List<Pair<Integer, PdfAnnotation>> annotations = firstPage.getPageAnnotations();
            assertThat(annotations.size()).isEqualTo(1);
            int stampAnnotationId = annotations.get(0).first;
            firstPage.removePageAnnotation(stampAnnotationId);
            assertThat(firstPage.getPageAnnotations().size()).isEqualTo(0);
        }
    }

    @Test
    public void testUpdateStampAnnotation() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(ONE_STAMP_ANNOTATION, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {
            List<Pair<Integer, PdfAnnotation>> annotations = firstPage.getPageAnnotations();
            int stampAnnotationId = annotations.get(0).first;
            StampAnnotation stampAnnotation = (StampAnnotation) annotations.get(0).second;
            List<PdfPageObject> pageObjects = stampAnnotation.getObjects();
            assertThat(pageObjects).hasSize(2);

            assertThat(pageObjects.get(0).getPdfObjectType()).isEqualTo(PdfPageObjectType.IMAGE);
            assertThat(pageObjects.get(1).getPdfObjectType()).isEqualTo(PdfPageObjectType.PATH);

            // Remove 1st object i.e. image object
            stampAnnotation.removeObject(0);

            // Update the path object
            PdfPagePathObject pathObject = (PdfPagePathObject) stampAnnotation.getObjects().get(0);
            pathObject.setStrokeColor(Color.RED);

            // Remove the older path object and the updated one
            stampAnnotation.removeObject(0);
            stampAnnotation.addObject(pathObject);

            firstPage.updatePageAnnotation(stampAnnotationId, stampAnnotation);

            annotations = firstPage.getPageAnnotations();
            StampAnnotation updatedStampAnnotation = (StampAnnotation) annotations.get(0).second;

            // check that the updated stamp annotation has one object which is a path object with
            // stroke color blue
            assertThat(updatedStampAnnotation.getObjects()).hasSize(1);
            assertThat(updatedStampAnnotation.getObjects().get(0).getPdfObjectType())
                    .isEqualTo(PdfPageObjectType.PATH);
            PdfPagePathObject updatedPathObject =
                    (PdfPagePathObject) stampAnnotation.getObjects().get(0);
            assertThat(updatedPathObject.getStrokeColor()).isEqualTo(Color.RED);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testGetPdfPageObject_pdfWithNoPageObject() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(EMPTY_PDF, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            assertThat(firstPage.getPageObjects().size()).isEqualTo(0);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testGetPdfPageObject_pdfWithOnePathAndOneImagePageObject() throws IOException {
        try (PdfRendererPreV renderer =
                        createPreVRenderer(ONE_PATH_ONE_IMAGE_PAGE_OBJECT, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            List<Pair<Integer, PdfPageObject>> pdfPageObjects = firstPage.getPageObjects();
            assertThat(pdfPageObjects.size()).isEqualTo(2);

            for (int i = 0; i < pdfPageObjects.size(); i++) {
                // When you do a get() call initially, the allocated Id's are equal to the
                // index in the pdfPageObject list.
                assertThat(pdfPageObjects.get(i).first).isEqualTo(i);
            }

            int imagePageObjectCount =
                    getPageObjectTypeCount(pdfPageObjects, PdfPageObjectType.IMAGE);
            int pathPageObjectCount =
                    getPageObjectTypeCount(pdfPageObjects, PdfPageObjectType.PATH);

            assertThat(pathPageObjectCount).isEqualTo(1);
            assertThat(imagePageObjectCount).isEqualTo(1);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testPdfPathObjectSegments() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(EMPTY_PDF, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {
            assertThat(firstPage.getPageObjects().size()).isEqualTo(0);
            Path path = new Path();
            path.moveTo(0f, 800f);
            path.lineTo(100f, 650f);
            path.lineTo(150f, 650f);
            path.lineTo(0f, 800f);
            PdfPagePathObject pathObject = new PdfPagePathObject(path);
            assertThat(pathObject.getFillColor()).isNotEqualTo(Color.BLUE);
            pathObject.setFillColor(Color.BLUE);

            int id = firstPage.addPageObject(pathObject);
            assertThat(id).isEqualTo(0);

            List<Pair<Integer, PdfPageObject>> pageObjects = firstPage.getPageObjects();
            assertThat(pageObjects.size()).isEqualTo(1);
            PdfPagePathObject addedPathObject =
                    (PdfPagePathObject) firstPage.getPageObjects().get(0).second;
            assertThat(addedPathObject.getFillColor()).isEqualTo(Color.BLUE);
            Path addedPath = addedPathObject.toPath();

            // Path coordinates in the format [x0, y0, x1, y1,...]
            float[] expectedCoordinates = {0.0f, 800f, 100f, 650f, 150f, 650f, 0f, 800f};
            // Path segments in the format [fraction, x0, y0, fraction, x1, y1...]
            float[] obtainedSegments = addedPath.approximate(0.5f);

            for (int i = 0; i < obtainedSegments.length / 3; i++) {
                // Compare x-coordinates
                assertThat(obtainedSegments[3 * i + 1]).isEqualTo(expectedCoordinates[2 * i]);
                // Compare y-coordinates
                assertThat(obtainedSegments[3 * i + 2]).isEqualTo(expectedCoordinates[2 * i + 1]);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testGetAddRemovePageObject() throws IOException {
        try (PdfRendererPreV renderer =
                        createPreVRenderer(ONE_PATH_ONE_IMAGE_PAGE_OBJECT, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {
            List<Pair<Integer, PdfPageObject>> pdfPageObjects = firstPage.getPageObjects();
            for (int i = 0; i < pdfPageObjects.size(); i++) {
                // When we do the first get() call, the allocated Id's are equal to the index.
                assertThat(pdfPageObjects.get(i).first).isEqualTo(i);
            }
            int id = firstPage.addPageObject(createSamplePdfPageImageObject());
            assertThat(id).isEqualTo(2);
            // Remove pageObject with id 1
            firstPage.removePageObject(1);
            // Since allocated Id are unique, the new allocated Id is 3
            int newId = firstPage.addPageObject(createSamplePdfPageImageObject());
            assertThat(newId).isEqualTo(3);

            int[] newExpectedIds = {0, 2, 3}; // pageObject with Id 1 was deleted
            pdfPageObjects = firstPage.getPageObjects();
            for (int i = 0; i < pdfPageObjects.size(); i++) {
                assertThat(pdfPageObjects.get(i).first).isEqualTo(newExpectedIds[i]);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testAddPathPageObject() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(ONE_PATH_PAGE_OBJECT, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            assertThat(firstPage.getPageObjects().size()).isEqualTo(1);

            // Create PdfPathPageObject
            Path path = new Path();
            path.lineTo(10f, 10f);
            PdfPagePathObject pdfPagePathObject = new PdfPagePathObject(path);
            pdfPagePathObject.setStrokeColor(Color.BLACK);

            // Add PdfPathPageObject
            int id = firstPage.addPageObject(pdfPagePathObject);
            assertThat(id).isEqualTo(1);

            List<Pair<Integer, PdfPageObject>> pageObjects = firstPage.getPageObjects();
            assertThat(pageObjects.size()).isEqualTo(2);
            assertThat(pageObjects.get(0).second.getPdfObjectType())
                    .isEqualTo(PdfPageObjectType.PATH);
            PdfPagePathObject pathObject = (PdfPagePathObject) pageObjects.get(0).second;
            assertThat(pathObject.getStrokeColor()).isEqualTo(Color.BLACK);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testAddImagePageObject() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(EMPTY_PDF, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {
            assertThat(firstPage.getPageObjects().size()).isEqualTo(0);

            int id1 = firstPage.addPageObject(createSamplePdfPageImageObject());
            assertThat(id1).isEqualTo(0);

            int id2 = firstPage.addPageObject(createSamplePdfPageImageObject());
            assertThat(id2).isEqualTo(1);

            assertThat(firstPage.getPageObjects().size()).isEqualTo(2);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testRemovePdfPathObject() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(ONE_PATH_PAGE_OBJECT, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            List<Pair<Integer, PdfPageObject>> pageObjects = firstPage.getPageObjects();
            assertThat(pageObjects.size()).isEqualTo(1);

            firstPage.removePageObject(pageObjects.get(0).first);

            assertThat(firstPage.getPageObjects().size()).isEqualTo(0);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testRemoveImagePageObject() throws IOException {
        try (PdfRendererPreV renderer =
                        createPreVRenderer(ONE_PATH_ONE_IMAGE_PAGE_OBJECT, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            List<Pair<Integer, PdfPageObject>> pdfPageObjects = firstPage.getPageObjects();
            assertThat(firstPage.getPageObjects().size()).isEqualTo(2);

            int imagePageObjectId = -1;
            for (int i = 0; i < pdfPageObjects.size(); i++) {
                if (pdfPageObjects.get(i).second.getPdfObjectType() == PdfPageObjectType.IMAGE) {
                    imagePageObjectId = pdfPageObjects.get(i).first;
                }
            }
            assertThat(imagePageObjectId).isGreaterThan(-1);

            firstPage.removePageObject(imagePageObjectId);

            pdfPageObjects = firstPage.getPageObjects();
            assertThat(firstPage.getPageObjects().size()).isEqualTo(1);
            assertThat(pdfPageObjects.get(0).second.getPdfObjectType())
                    .isEqualTo(PdfPageObjectType.PATH);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testGetAndRemovePageObjectInPdfWithUnsupportedPageObjects() throws IOException {
        try (PdfRendererPreV renderer =
                        createPreVRenderer(R.raw.text_path_image_page_objects, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {
            /*
             * The Pdf contains one each of Text, Path and Image PageObject. TextPathObject is
             * currently unsupported so get() call should return a list of size 2.
             */
            List<Pair<Integer, PdfPageObject>> pdfPageObjects = firstPage.getPageObjects();
            assertThat(pdfPageObjects).hasSize(2);
            assertThat(pdfPageObjects.get(0).second.getPdfObjectType())
                    .isEqualTo(PdfPageObjectType.IMAGE);
            // Remove ImagePageObject
            firstPage.removePageObject(pdfPageObjects.get(0).first);
            // The updated size should be 1 and the remaining PageObject should be of type Path.
            pdfPageObjects = firstPage.getPageObjects();
            assertThat(pdfPageObjects).hasSize(1);
            assertThat(pdfPageObjects.get(0).second.getPdfObjectType())
                    .isEqualTo(PdfPageObjectType.PATH);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testUpdatePdfPathPageObject() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(ONE_PATH_PAGE_OBJECT, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            PdfPagePathObject pathObject =
                    (PdfPagePathObject) firstPage.getPageObjects().get(0).second;
            pathObject.setStrokeColor(Color.BLUE);

            firstPage.updatePageObject(firstPage.getPageObjects().get(0).first, pathObject);

            PdfPagePathObject updatedPathObject =
                    (PdfPagePathObject) firstPage.getPageObjects().get(0).second;
            assertThat(updatedPathObject.getStrokeColor()).isEqualTo(Color.BLUE);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testUpdateImagePageObject() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(ONE_IMAGE_PAGE_OBJECT, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {

            List<Pair<Integer, PdfPageObject>> pdfPageObjects = firstPage.getPageObjects();
            assertThat(firstPage.getPageObjects().size()).isEqualTo(1);
            assertThat(pdfPageObjects.get(0).first).isEqualTo(0); // check id

            assertThat(pdfPageObjects.get(0).second.getPdfObjectType())
                    .isEqualTo(PdfPageObjectType.IMAGE);
            PdfPageImageObject pdfPageImageObject =
                    (PdfPageImageObject) pdfPageObjects.get(0).second;
            /*
             * The original matrix values were obtained from the script used to generate the PDF
             * used in this test.
             */
            float[] originalMatrixValues = {150.0f, 0f, 225.0f, 0f, 100.0f, 350.0f, 0f, 0f, 1.0f};
            assertThat(pdfPageImageObject.getMatrix()).isEqualTo(originalMatrixValues);
            float[] newMatrixValues = {2, 1, 4, 7, 3, 5, 0, 0, 1};
            Matrix newMatrix = new Matrix();
            newMatrix.setValues(newMatrixValues);
            assertThat(newMatrix.isAffine()).isTrue();
            pdfPageImageObject.setMatrix(newMatrix);

            boolean result = firstPage.updatePageObject(0, pdfPageImageObject);
            assertThat(result).isTrue();
            float[] posUpdateMatrixValues = firstPage.getPageObjects().get(0).second.getMatrix();
            assertThat(posUpdateMatrixValues).isEqualTo(newMatrixValues);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testUpdateImagePageObject_updateBitmap() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(EMPTY_PDF, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {
            Bitmap bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888);
            PdfPageImageObject pageImageObject = new PdfPageImageObject(bitmap);

            int id = firstPage.addPageObject(pageImageObject);
            assertThat(id).isEqualTo(0);

            PdfPageImageObject addedImagePageObject =
                    (PdfPageImageObject) firstPage.getPageObjects().get(0).second;
            // getMatrix for the addedImagePageObject should return a Identity Matrix
            assertThat(addedImagePageObject.getMatrix())
                    .isEqualTo(new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1});
            Bitmap addedBitmap = addedImagePageObject.getBitmap();
            assertThat(addedBitmap.getHeight()).isEqualTo(100);
            assertThat(addedBitmap.getWidth()).isEqualTo(200);

            addedBitmap.setHeight(50);
            addedBitmap.setWidth(50);
            addedImagePageObject.setBitmap(addedBitmap);

            firstPage.updatePageObject(0, addedImagePageObject);
            PdfPageImageObject updatedImagePageObject =
                    (PdfPageImageObject) firstPage.getPageObjects().get(0).second;
            assertThat(updatedImagePageObject.getBitmap().getHeight()).isEqualTo(50);
            assertThat(updatedImagePageObject.getBitmap().getWidth()).isEqualTo(50);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testRemovePageObject_WithInvalidId() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(ONE_IMAGE_PAGE_OBJECT, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {
            assertThat(firstPage.getPageObjects().size()).isEqualTo(1);
            assertThrows(IllegalArgumentException.class, () -> firstPage.removePageObject(1));
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testUpdatePageObject_WithInvalidId() throws IOException {
        try (PdfRendererPreV renderer = createPreVRenderer(ONE_IMAGE_PAGE_OBJECT, mContext, null);
                PdfRendererPreV.Page firstPage = renderer.openPage(0)) {
            List<Pair<Integer, PdfPageObject>> pageObjects = firstPage.getPageObjects();
            assertThat(pageObjects.size()).isEqualTo(1);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            firstPage.updatePageObject(
                                    pageObjects.get(0).first + 1, pageObjects.get(0).second));
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_EDIT_PDF_PAGE_OBJECTS)
    public void testPageObjectApiWhenPageClosed() throws IOException {
        PdfRendererPreV renderer = createPreVRenderer(ONE_IMAGE_PAGE_OBJECT, mContext, null);
        PdfRendererPreV.Page firstPage = renderer.openPage(0);
        firstPage.close();
        assertThrows(IllegalStateException.class, firstPage::getPageObjects);
        renderer.close();
    }

    private PdfPageImageObject createSamplePdfPageImageObject() {
        Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(), SAMPLE_IMAGE);
        return new PdfPageImageObject(bitmap);
    }

    private PdfPagePathObject createSamplePdfPagePathObject() {
        Path path = new Path();
        path.moveTo(0f, 800f);
        path.lineTo(100f, 650f);
        path.lineTo(150f, 650f);
        path.lineTo(0f, 800f);
        PdfPagePathObject pathObject = new PdfPagePathObject(path);
        pathObject.setFillColor(Color.RED);
        pathObject.setStrokeColor(Color.BLUE);
        return pathObject;
    }

    private int getPageObjectTypeCount(
            List<Pair<Integer, PdfPageObject>> pdfPageObjects, int type) {
        int count = 0;
        for (Pair<Integer, PdfPageObject> pageObject : pdfPageObjects) {
            if (pageObject.second.getPdfObjectType() == type) { // Correct comparison
                count++;
            }
        }
        return count;
    }

    private void assertSamplePdf(PdfRendererPreV renderer, PdfRendererPreV expectedRenderer) {
        assertThat(renderer.getDocumentLinearizationType()).isEqualTo(
                expectedRenderer.getDocumentLinearizationType());
        assertThat(renderer.getPageCount()).isEqualTo(expectedRenderer.getPageCount());

        PdfRendererPreV.Page firstPage = renderer.openPage(0);
        PdfRendererPreV.Page expectedFirstPage = expectedRenderer.openPage(0);
        assertSamplePdfPage(firstPage, expectedFirstPage);
        assertThat(firstPage.searchText("A Simple PDF file").size()).isEqualTo(
                firstPage.searchText("A Simple PDF file").size());
        assertThat(firstPage.searchText("A Simple PDF file").size()).isEqualTo(1);

        firstPage.close();
        expectedFirstPage.close();

        PdfRendererPreV.Page secondPage = renderer.openPage(1);
        PdfRendererPreV.Page expectedSecondPage = expectedRenderer.openPage(1);
        assertSamplePdfPage(secondPage, expectedSecondPage);
        assertThat(secondPage.searchText("Simple PDF file 2").size()).isEqualTo(
                secondPage.searchText("Simple PDF file 2").size());
        assertThat(secondPage.searchText("more").size()).isEqualTo(28);

        secondPage.close();
        expectedSecondPage.close();

        PdfRendererPreV.Page thirdPage = renderer.openPage(2);
        PdfRendererPreV.Page expectedThirdPage = expectedRenderer.openPage(2);
        assertSamplePdfPage(thirdPage, expectedThirdPage);
        assertThat(thirdPage.searchText("Simple PDF file 2").size()).isEqualTo(
                thirdPage.searchText("Simple PDF file 2").size());

        thirdPage.close();
        expectedThirdPage.close();
    }

    private void assertSamplePdfPage(PdfRendererPreV.Page page, PdfRendererPreV.Page expectedPage) {
        assertThat(page.getHeight()).isEqualTo(expectedPage.getHeight());
        assertThat(page.getWidth()).isEqualTo(expectedPage.getWidth());
    }

    private void assertPageSelection(PageSelection pageSelection, int expectedRectsSize,
            int expectedPageNumber) {
        assertThat(pageSelection.getPage()).isEqualTo(expectedPageNumber);
        assertThat(pageSelection.getSelectedTextContents()).isNotEmpty();
        assertThat(pageSelection.getSelectedTextContents().get(0).getBounds().size()).isEqualTo(
                expectedRectsSize);
    }

    private void assertPageException(PdfRendererPreV.Page page) {
        Point leftPoint = new Point(275, 163);
        Point rightPoint = new Point(65, 125);

        assertThrows(IllegalStateException.class, page::close);
        assertThrows(IllegalStateException.class, page::getHeight);
        assertThrows(IllegalStateException.class, page::getWidth);
        assertThrows(IllegalStateException.class, page::getGotoLinks);
        assertThrows(IllegalStateException.class, page::getLinkContents);
        assertThrows(IllegalStateException.class, page::getTextContents);
        assertThrows(IllegalStateException.class, page::getImageContents);
        assertThrows(IllegalStateException.class, () -> page.searchText("more"));
        assertThrows(IllegalStateException.class,
                () -> page.selectContent(new SelectionBoundary(leftPoint),
                        new SelectionBoundary(rightPoint)));
        assertThrows(IllegalStateException.class, () -> page.render(
                Bitmap.createBitmap(A4_WIDTH_PTS, A4_HEIGHT_PTS, Bitmap.Config.ARGB_8888), null,
                null, new RenderParams.Builder(1).build()));
    }
}
