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

import static android.graphics.pdf.cts.module.Utils.createRenderer;
import static android.graphics.pdf.cts.module.Utils.assertScreenshotsAreEqual;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.RenderParams;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.DrawableRes;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class PdfAnnotationsRenderScreenshotTests {
    private static final String TAG = "PdfAnnotationsRenderScreenshotTests";
    private static final String LOCAL_DIRECTORY =
            Environment.getExternalStorageDirectory() + "/PdfAnnotationsRenderScreenShotTests";

    private static final int PDF_WITH_TEXT_AND_HIGHLIGHT_ANNOTATIONS = R.raw.pdf_with_annotations;

    private static final int BOTH_TEXT_AND_HIGHLIGHT_ANNOT_RENDER_FLAGS_SET_GOLDEN =
            R.drawable.bothTextAndHighlightFlagsSet_golden;
    private static final int TEXT_ANNOT_RENDER_FLAG_SET_GOLDEN =
            R.drawable.TextAnnotFlagSetOnly_golden;
    private static final int HIGHLIGHT_ANNOT_RENDER_FLAG_SET_GOLDEN =
            R.drawable.HighlightAnnotFlagSetOnly_golden;
    private static final int NEITHER_TEXT_NOR_HIGHLIGHT_ANNOT_RENDER_FLAGS_SET_GOLDEN =
            R.drawable.NeitherTextNorHighlightRenderFlagsSet_golden;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final int mPdfRes;
    private final int mGoldenRes;
    private final boolean mSetTextAnnotFlag;
    private final boolean mSetHighlightAnnotFlag;
    private final String mTestName;

    public PdfAnnotationsRenderScreenshotTests(boolean setTextAnnotFlag,
            boolean setHighlightAnnotFlag,
            @DrawableRes int goldenRes, String testName) {
        mPdfRes = PDF_WITH_TEXT_AND_HIGHLIGHT_ANNOTATIONS;
        mGoldenRes = goldenRes;
        mSetTextAnnotFlag = setTextAnnotFlag;
        mSetHighlightAnnotFlag = setHighlightAnnotFlag;
        mTestName = testName;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> getParameters() {
        List<Object[]> parameters = new ArrayList<>();

        parameters.add(
                new Object[]{true, true, BOTH_TEXT_AND_HIGHLIGHT_ANNOT_RENDER_FLAGS_SET_GOLDEN,
                        "bothTextAndHighlightRenderFlagsSet"});
        parameters.add(new Object[]{true, false, TEXT_ANNOT_RENDER_FLAG_SET_GOLDEN,
                "TextAnnotFlagSetOnly"});
        parameters.add(new Object[]{false, true, HIGHLIGHT_ANNOT_RENDER_FLAG_SET_GOLDEN,
                "HighlightAnnotFlagSetOnly"});
        parameters.add(
                new Object[]{false, false, NEITHER_TEXT_NOR_HIGHLIGHT_ANNOT_RENDER_FLAGS_SET_GOLDEN,
                        "NeitherTextNorHighlightRenderFlagsSet"});
        return parameters;
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName =
            "VanillaIceCream")
    public void renderAnnotationsTypeWhenCorrespondingFlagSet() throws Exception {
        renderAndCompare(mTestName, mGoldenRes);
    }



    private void renderAndCompare(String testName, int goldenRes) throws IOException {
        try (PdfRenderer renderer = createRenderer(mPdfRes, mContext)) {
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inScaled = false;
                Bitmap golden = BitmapFactory.decodeResource(mContext.getResources(), goldenRes,
                        options);
                Bitmap output = Bitmap.createBitmap(page.getWidth(), page.getHeight(),
                        Bitmap.Config.ARGB_8888);
                page.render(output, null, null, createRenderParams());
                assertScreenshotsAreEqual(golden, output, testName, LOCAL_DIRECTORY);
            }
        }
    }

    private RenderParams createRenderParams() {
        RenderParams.Builder renderParamsBuilder = new RenderParams.Builder(
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        if (mSetTextAnnotFlag && mSetHighlightAnnotFlag) {
            renderParamsBuilder.setRenderFlags(
                    RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS
                            | RenderParams.FLAG_RENDER_HIGHLIGHT_ANNOTATIONS);
        } else if (mSetTextAnnotFlag) {
            renderParamsBuilder.setRenderFlags(
                    RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS);
        } else if (mSetHighlightAnnotFlag) {
            renderParamsBuilder.setRenderFlags(
                    RenderParams.FLAG_RENDER_HIGHLIGHT_ANNOTATIONS);
        }

        return renderParamsBuilder.build();
    }
}
