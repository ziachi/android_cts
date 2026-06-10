/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.uirendering.cts.testclasses;

import static junit.framework.Assert.assertFalse;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Gainmap;
import android.graphics.HardwareBufferRenderer;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.hardware.HardwareBuffer;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.util.BitmapAsserter;
import android.uirendering.cts.util.BitmapDumper;

import androidx.annotation.ColorLong;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.graphics.hwui.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testng.Assert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class GainmapTests {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();


    private static final ColorSpace BT2020_HLG = ColorSpace.get(ColorSpace.Named.BT2020_HLG);
    private static final ColorSpace BT2020_PQ = ColorSpace.get(ColorSpace.Named.BT2020_PQ);
    private static final ColorSpace BT2020 = ColorSpace.get(ColorSpace.Named.BT2020);
    private static final ColorSpace SRGB = ColorSpace.get(ColorSpace.Named.SRGB);

    // A 10x6 base image with a 5x3 (so 1/2 res) gainmap that boosts the center 3 pixels
    // by 0x40, 0x80, and 0xff respectively
    private static final Bitmap sTestImage;
    static {
        Bitmap base = Bitmap.createBitmap(10, 6, Bitmap.Config.ARGB_8888);
        base.eraseColor(Color.WHITE);

        Bitmap gainmapImage = Bitmap.createBitmap(5, 3, Bitmap.Config.ARGB_8888);
        gainmapImage.eraseColor(0);
        gainmapImage.setPixel(1, 1, 0xFF404040);
        gainmapImage.setPixel(2, 1, 0xFF808080);
        gainmapImage.setPixel(3, 1, 0xFFFFFFFF);

        Gainmap gainmap = new Gainmap(gainmapImage);
        base.setGainmap(gainmap);
        sTestImage = base;
    }

    // A 10x6 HLG base image with a 1x1 gainmap that annihilates the red and blue
    // components in an sRGB working space.
    private static final Bitmap sHLGTestImage;
    static {
        Bitmap base = Bitmap.createBitmap(10, 6, Bitmap.Config.ARGB_8888);
        base.eraseColor(Color.WHITE);
        base.setColorSpace(BT2020_HLG);

        Bitmap gainmapImage = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        gainmapImage.setPixel(0, 0, 0xFFFF00FF);
        Gainmap gainmap = new Gainmap(gainmapImage);
        if (Flags.isoGainmapApis()) {
            gainmap.setGainmapDirection(Gainmap.GAINMAP_DIRECTION_HDR_TO_SDR);
            gainmap.setAlternativeImagePrimaries(SRGB);
        }
        gainmap.setRatioMax(100000000f, 100000000f, 100000000f);
        gainmap.setRatioMin(3.789f, 3.789f, 3.789f);
        base.setGainmap(gainmap);
        sHLGTestImage = base;
    }

    // A 10x6 base image with a 5x3 (so 1/2 res) gainmap that boosts the center 3 pixels
    // by 0x40, 0x80, and 0xff respectively
    private static final Bitmap sTestImageA8;
    static {
        Bitmap base = Bitmap.createBitmap(10, 6, Bitmap.Config.ARGB_8888);
        base.eraseColor(Color.WHITE);

        Bitmap gainmapImage = Bitmap.createBitmap(5, 3, Bitmap.Config.ALPHA_8);
        gainmapImage.eraseColor(0);
        gainmapImage.setPixel(1, 1, 0x40000000);
        gainmapImage.setPixel(2, 1, 0x80000000);
        gainmapImage.setPixel(3, 1, 0xFF000000);

        Gainmap gainmap = new Gainmap(gainmapImage);
        base.setGainmap(gainmap);
        sTestImageA8 = base;
    }

    private static final Picture sTestPicture;
    static {
        sTestPicture = new Picture();
        Canvas canvas = sTestPicture.beginRecording(sTestImage.getWidth(), sTestImage.getHeight());
        canvas.drawBitmap(sTestImage, 0, 0, null);
        sTestPicture.endRecording();
    }

    private static final Picture sHLGTestPicture;
    static {
        sHLGTestPicture = new Picture();
        Canvas canvas = sHLGTestPicture.beginRecording(
                sHLGTestImage.getWidth(), sHLGTestImage.getHeight());
        canvas.drawBitmap(sHLGTestImage, 0, 0, null);
        sHLGTestPicture.endRecording();
    }

    private static final Gainmap sNoOpGainmap;
    static {
        sNoOpGainmap = new Gainmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8));
        sNoOpGainmap.setRatioMin(1f, 1f, 1f);
        sNoOpGainmap.setRatioMax(1f, 1f, 1f);
    }

    private static void assertChannels(Color result, @ColorLong long expected, float delta) {
        ColorSpace.Connector connector = ColorSpace.connect(Color.colorSpace(expected),
                result.getColorSpace());
        float[] mapped = connector.transform(Color.red(expected), Color.green(expected),
                Color.blue(expected));
        Assert.assertEquals(result.red(), mapped[0], delta, "red channel mismatch");
        Assert.assertEquals(result.green(), mapped[1], delta, "green channel mismatch");
        Assert.assertEquals(result.blue(), mapped[2], delta, "blue channel mismatch");
    }

    @ColorLong
    private static long mapWhiteWithGain(Gainmap gainmap, double gain) {
        double logRatioMin = Math.log(gainmap.getRatioMin()[0]);
        double logRatioMax = Math.log(gainmap.getRatioMax()[0]);
        double epsilonSdr = gainmap.getEpsilonSdr()[0];
        double epsilonHdr = gainmap.getEpsilonHdr()[0];
        double L = (logRatioMin * (1 - gain)) + (logRatioMax * gain);
        float D = (float) ((1.0 + epsilonSdr) * Math.exp(L) - epsilonHdr);
        return Color.pack(D, D, D, 1.f, ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB));
    }

    private void assertTestImageResult(Bitmap result) {
        assertTestImageResult(result, sTestImage.getGainmap());
    }

    private static float toleranceForResult(Bitmap result) {
        // 8888 precision ain't so great
        if (result.getConfig() == Bitmap.Config.ARGB_8888) {
            // PQ math on GLES2.0 is very poor
            if (result.getColorSpace().getId() == ColorSpace.Named.BT2020_PQ.ordinal()
                    || result.getColorSpace().getId() == ColorSpace.Named.BT2020.ordinal()) {
                return 0.06f;
            }
            return 0.02f;
        }
        return 0.004f;
    }

    private void assertTestImageResult(Bitmap result, Gainmap gainmap) {
        try {
            // 8888 precision ain't so great
            final float delta = toleranceForResult(result);
            assertChannels(result.getColor(0, 0), Color.pack(Color.WHITE), delta);
            assertChannels(result.getColor(2, 2),
                    mapWhiteWithGain(gainmap, 0x40 / 255.f), delta);
            assertChannels(result.getColor(4, 2),
                    mapWhiteWithGain(gainmap, 0x80 / 255.f), delta);
            assertChannels(result.getColor(6, 2),
                    mapWhiteWithGain(gainmap, 0xFF / 255.f), delta);
        } catch (Throwable t) {
            BitmapDumper.dumpBitmap(result);
            throw t;
        }
    }

    private void assertFullyAppliedHLGTestImageResult(Bitmap result) {
        try {
            final float delta = toleranceForResult(result);
            // Note: maps a BT. 2020 color to an sRGB Green
            assertChannels(result.getColor(0, 0), Color.pack(Color.GREEN), delta);
            assertChannels(result.getColor(2, 2), Color.pack(Color.GREEN), delta);
            assertChannels(result.getColor(4, 2), Color.pack(Color.GREEN), delta);
            assertChannels(result.getColor(6, 2), Color.pack(Color.GREEN), delta);
        } catch (Throwable t) {
            BitmapDumper.dumpBitmap(result);
            throw t;
        }
    }

    private static Bitmap renderTestImageWithHardware(ColorSpace dest) {
        return renderTestImageWithHardware(dest, false);
    }

    private static Bitmap renderWithHardware(ColorSpace dest, Consumer<RecordingCanvas> func) {
        HardwareBuffer buffer = HardwareBuffer.create(sTestImage.getWidth(), sTestImage.getHeight(),
                HardwareBuffer.RGBA_8888,
                1, HardwareBuffer.USAGE_GPU_COLOR_OUTPUT | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
        HardwareBufferRenderer renderer = new HardwareBufferRenderer(buffer);
        RenderNode content = new RenderNode("gainmap");
        content.setPosition(0, 0, sTestImage.getWidth(), sTestImage.getHeight());
        RecordingCanvas canvas = content.beginRecording();
        func.accept(canvas);
        content.endRecording();
        renderer.setContentRoot(content);
        CountDownLatch latch = new CountDownLatch(1);
        renderer.obtainRenderRequest().setColorSpace(dest).draw(Runnable::run, result -> {
            result.getFence().awaitForever();
            latch.countDown();
        });
        try {
            Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
            Assert.fail(ex.getMessage());
        }
        return Bitmap.wrapHardwareBuffer(buffer, dest).copy(Bitmap.Config.ARGB_8888, false);
    }

    private static Bitmap renderTestImageWithHardware(Bitmap bitmap, ColorSpace dest) {
        return renderWithHardware(dest, canvas -> {
            canvas.drawBitmap(bitmap, 0, 0, null);
        });
    }

    private static Bitmap renderTestPictureWithHardware(Picture picture, ColorSpace dest) {
        return renderWithHardware(dest, canvas -> {
            canvas.drawPicture(picture);
        });
    }

    private static Bitmap renderTestImageWithHardware(ColorSpace dest, boolean usePicture) {
        if (usePicture) {
            return renderTestPictureWithHardware(sTestPicture, dest);
        } else {
            return renderTestImageWithHardware(sTestImage, dest);
        }
    }

    @Test
    public void gainmapToHlgSoftware() {
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, BT2020_HLG);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(sTestImage, 0f, 0f, null);
        assertTestImageResult(result);
        canvas.drawBitmap(sTestImageA8, 0f, 0f, null);
        assertTestImageResult(result);
    }

    @Test
    public void gainmapToPqSoftware() {
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, BT2020_PQ);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(sTestImage, 0f, 0f, null);
        assertTestImageResult(result);
        canvas.drawBitmap(sTestImageA8, 0f, 0f, null);
        assertTestImageResult(result);
    }

    @Test
    public void gainmapToSrgbSoftware() {
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, SRGB);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(sTestImage, 0f, 0f, null);
        assertTestImageResult(result, sNoOpGainmap);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ISO_GAINMAP_APIS)
    @Test
    public void hlgGainmapToBt2020Software() {
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, BT2020);
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(sHLGTestImage, 0f, 0f, null);
        assertFullyAppliedHLGTestImageResult(result);
    }

    @Test
    public void gainmapToHlgPictureSoftware() {
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, BT2020_HLG);
        Canvas canvas = new Canvas(result);
        canvas.drawPicture(sTestPicture);
        assertTestImageResult(result);
    }

    @Test
    public void gainmapToPqPictureSoftware() {
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, BT2020_HLG);
        Canvas canvas = new Canvas(result);
        canvas.drawPicture(sTestPicture);
        assertTestImageResult(result);
    }

    @Test
    public void gainmapToSrgbPictureSoftware() {
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, SRGB);
        Canvas canvas = new Canvas(result);
        canvas.drawPicture(sTestPicture);
        assertTestImageResult(result, sNoOpGainmap);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ISO_GAINMAP_APIS)
    @Test
    public void hlgGainmapToBt2020PictureSoftware() {
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, BT2020);
        Canvas canvas = new Canvas(result);
        canvas.drawPicture(sHLGTestPicture);
        assertFullyAppliedHLGTestImageResult(result);
    }

    @Test
    public void gainmapToHlgHardware() throws Exception {
        Bitmap result = renderTestImageWithHardware(BT2020_HLG);
        assertTestImageResult(result);
    }

    @Test
    public void gainmapToPqHardware() {
        Bitmap result = renderTestImageWithHardware(BT2020_PQ);
        assertTestImageResult(result);
    }

    @Test
    public void gainmapToSrgbHardware() {
        Bitmap result = renderTestImageWithHardware(SRGB);
        assertTestImageResult(result, sNoOpGainmap);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ISO_GAINMAP_APIS)
    @Test
    public void hlgGainmapToBt2020Hardware() {
        Bitmap result = renderTestImageWithHardware(sHLGTestImage, BT2020);
        assertFullyAppliedHLGTestImageResult(result);
    }

    @Test
    public void gainmapToHlgPictureHardware() throws Exception {
        Bitmap result = renderTestImageWithHardware(BT2020_HLG, true);
        assertTestImageResult(result);
    }

    @Test
    public void gainmapToPqPictureHardware() {
        Bitmap result = renderTestImageWithHardware(BT2020_PQ, true);
        assertTestImageResult(result);
    }

    @Test
    public void gainmapToSrgbPictureHardware() {
        Bitmap result = renderTestImageWithHardware(SRGB, true);
        assertTestImageResult(result, sNoOpGainmap);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ISO_GAINMAP_APIS)
    @Test
    public void hlgGainmapToBt2020PictureHardware() {
        Bitmap result = renderTestPictureWithHardware(sHLGTestPicture, BT2020);
        assertFullyAppliedHLGTestImageResult(result);
    }

    @Test
    public void bitmapShaderSupportHLG() {
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, BT2020_HLG);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setFlags(0);
        BitmapShader shader = new BitmapShader(sTestImage, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
        paint.setShader(shader);
        canvas.drawPaint(paint);
        assertTestImageResult(result);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ISO_GAINMAP_APIS)
    @Test
    public void bitmapShaderSupportHLGGainmaps() {
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, BT2020);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setFlags(0);
        BitmapShader shader = new BitmapShader(sHLGTestImage, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
        paint.setShader(shader);
        canvas.drawPaint(paint);
        assertFullyAppliedHLGTestImageResult(result);
    }

    @Test
    public void bitmapShaderSupportHLGHardware() {
        Bitmap result = renderWithHardware(BT2020_HLG, canvas -> {
            Paint paint = new Paint();
            paint.setFlags(0);
            BitmapShader shader = new BitmapShader(sTestImage, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
            paint.setShader(shader);
            canvas.drawPaint(paint);
        });
        assertTestImageResult(result);
    }

    @Test
    public void bitmapShaderSupportSrgbHardware() {
        Bitmap result = renderWithHardware(SRGB, canvas -> {
            Paint paint = new Paint();
            paint.setFlags(0);
            BitmapShader shader = new BitmapShader(sTestImage, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
            paint.setShader(shader);
            canvas.drawPaint(paint);
        });
        assertTestImageResult(result, sNoOpGainmap);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ISO_GAINMAP_APIS)
    @Test
    public void bitmapShaderSupportHLGGainmapsHardware() {
        Bitmap result = renderWithHardware(BT2020, canvas -> {
            Paint paint = new Paint();
            paint.setFlags(0);
            BitmapShader shader = new BitmapShader(sHLGTestImage, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
            paint.setShader(shader);
            canvas.drawPaint(paint);
        });
        assertFullyAppliedHLGTestImageResult(result);
    }


    @RequiresFlagsEnabled(Flags.FLAG_GAINMAP_ANIMATIONS)
    @Test
    public void bitmapShaderOverrideGainmapToNoOpHLG() {
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, BT2020_HLG);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setFlags(0);
        BitmapShader shader = new BitmapShader(sTestImage, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
        shader.setOverrideGainmap(sNoOpGainmap);
        paint.setShader(shader);
        canvas.drawPaint(paint);
        BitmapAsserter.assertBitmapIsVerified(result, new ColorVerifier(Color.WHITE, 3),
                "");
    }

    @RequiresFlagsEnabled(Flags.FLAG_GAINMAP_ANIMATIONS)
    @Test
    public void bitmapShaderOverrideGainmapTo4xHLG() {
        Gainmap override = new Gainmap(sTestImage.getGainmap().getGainmapContents());
        override.setRatioMax(4.0f, 4.0f, 4.0f);
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, BT2020_HLG);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setFlags(0);
        BitmapShader shader = new BitmapShader(sTestImage, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
        shader.setOverrideGainmap(override);
        paint.setShader(shader);
        canvas.drawPaint(paint);
        assertTestImageResult(result, override);
    }

    @RequiresFlagsEnabled(Flags.FLAG_GAINMAP_ANIMATIONS)
    @Test
    public void bitmapShaderOverrideGainmapModifyAfterSetHLG() {
        Gainmap override = new Gainmap(sTestImage.getGainmap().getGainmapContents());
        override.setRatioMax(4.0f, 4.0f, 4.0f);
        Gainmap initialOverride = new Gainmap(override, override.getGainmapContents());
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, BT2020_HLG);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setFlags(0);
        BitmapShader shader = new BitmapShader(sTestImage, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
        shader.setOverrideGainmap(override);
        override.setRatioMax(1f, 1f, 1f);
        paint.setShader(shader);
        canvas.drawPaint(paint);
        assertTestImageResult(result, initialOverride);
    }

    @RequiresFlagsEnabled(Flags.FLAG_GAINMAP_ANIMATIONS)
    @Test
    public void bitmapShaderOverrideGainmapPaintObservesUpdatesHLG() {
        Gainmap override = new Gainmap(sTestImage.getGainmap().getGainmapContents());
        Bitmap result = Bitmap.createBitmap(10, 6, Bitmap.Config.RGBA_F16, false, BT2020_HLG);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setFlags(0);
        BitmapShader shader = new BitmapShader(sTestImage, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
        shader.setOverrideGainmap(override);
        paint.setShader(shader);
        canvas.drawPaint(paint);
        override.setRatioMax(1, 1, 1);
        shader.setOverrideGainmap(override);
        canvas.drawPaint(paint);
        BitmapAsserter.assertBitmapIsVerified(result, new ColorVerifier(Color.WHITE, 3),
                "");
    }

    @Test
    public void createScaledBitmap() {
        Bitmap result = Bitmap.createScaledBitmap(sTestImage, 20, 12, false);
        assertEquals(result.getWidth(), 20);
        assertEquals(result.getHeight(), 12);
        assertTrue(result.hasGainmap());
        Bitmap gainmapContents = result.getGainmap().getGainmapContents();
        assertEquals(gainmapContents.getWidth(), 10);
        assertEquals(gainmapContents.getHeight(), 6);

        assertChannels(gainmapContents.getColor(0, 0), Color.pack(Color.BLACK), 0f);
        assertChannels(gainmapContents.getColor(1, 1), Color.pack(Color.BLACK), 0f);

        assertChannels(gainmapContents.getColor(2, 2), Color.pack(0xFF404040), 0f);
        assertChannels(gainmapContents.getColor(3, 3), Color.pack(0xFF404040), 0f);

        assertChannels(gainmapContents.getColor(4, 2), Color.pack(0xFF808080), 0f);
        assertChannels(gainmapContents.getColor(5, 3), Color.pack(0xFF808080), 0f);

        assertChannels(gainmapContents.getColor(6, 2), Color.pack(0xFFFFFFFF), 0f);
        assertChannels(gainmapContents.getColor(7, 3), Color.pack(0xFFFFFFFF), 0f);

        assertChannels(gainmapContents.getColor(8, 4), Color.pack(Color.BLACK), 0f);
        assertChannels(gainmapContents.getColor(9, 5), Color.pack(Color.BLACK), 0f);
    }

    @Test
    public void applyRotation180Matrix() {
        Matrix m = new Matrix();
        m.setRotate(180.0f, 5.f, 3.f);
        Bitmap result = Bitmap.createBitmap(sTestImage, 0, 0, 10, 6, m, false);
        assertEquals(result.getWidth(), 10);
        assertEquals(result.getHeight(), 6);
        assertTrue(result.hasGainmap());
        Bitmap gainmapContents = result.getGainmap().getGainmapContents();
        assertEquals(gainmapContents.getWidth(), 5);
        assertEquals(gainmapContents.getHeight(), 3);
        assertChannels(gainmapContents.getColor(0, 0), Color.pack(Color.BLACK), 0f);
        assertChannels(gainmapContents.getColor(0, 1), Color.pack(Color.BLACK), 0f);
        assertChannels(gainmapContents.getColor(1, 1), Color.pack(0xFFFFFFFF), 0f);
        assertChannels(gainmapContents.getColor(2, 1), Color.pack(0xFF808080), 0f);
        assertChannels(gainmapContents.getColor(3, 1), Color.pack(0xFF404040), 0f);
        assertChannels(gainmapContents.getColor(4, 1), Color.pack(Color.BLACK), 0f);
        assertChannels(gainmapContents.getColor(4, 2), Color.pack(Color.BLACK), 0f);
    }

    @Test
    public void applyRotation90Matrix() {
        Matrix m = new Matrix();
        m.setRotate(90.0f, 5.f, 3.f);
        Bitmap result = Bitmap.createBitmap(sTestImage, 0, 0, 10, 6, m, false);
        assertEquals(result.getWidth(), 6);
        assertEquals(result.getHeight(), 10);
        assertTrue(result.hasGainmap());
        Bitmap gainmapContents = result.getGainmap().getGainmapContents();
        assertEquals(gainmapContents.getWidth(), 3);
        assertEquals(gainmapContents.getHeight(), 5);
        assertChannels(gainmapContents.getColor(0, 0), Color.pack(Color.BLACK), 0f);
        assertChannels(gainmapContents.getColor(1, 0), Color.pack(Color.BLACK), 0f);
        assertChannels(gainmapContents.getColor(1, 1), Color.pack(0xFF404040), 0f);
        assertChannels(gainmapContents.getColor(1, 2), Color.pack(0xFF808080), 0f);
        assertChannels(gainmapContents.getColor(1, 3), Color.pack(0xFFFFFFFF), 0f);
        assertChannels(gainmapContents.getColor(1, 4), Color.pack(Color.BLACK), 0f);
        assertChannels(gainmapContents.getColor(2, 4), Color.pack(Color.BLACK), 0f);
    }

    @Test
    public void testRenderingDropsGainmap() {
        Bitmap dest = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        Gainmap gainmap = new Gainmap(Bitmap.createBitmap(5, 5, Bitmap.Config.ALPHA_8));
        dest.setGainmap(gainmap);
        assertTrue(dest.hasGainmap());
        Canvas canvas = new Canvas(dest);
        assertFalse(dest.hasGainmap());
        canvas.setBitmap(null);
        dest.setGainmap(gainmap);
        assertTrue(dest.hasGainmap());
        canvas.setBitmap(dest);
        assertFalse(dest.hasGainmap());
    }

    @Test
    public void testHardwareHandlingHLG() {
        Bitmap testImage = sTestImage.copy(Bitmap.Config.HARDWARE, false);
        assertTrue(testImage.hasGainmap());
        assertEquals(testImage.getGainmap().getGainmapContents().getConfig(),
                Bitmap.Config.HARDWARE);
        Bitmap result = renderWithHardware(BT2020_HLG, canvas -> {
            canvas.drawBitmap(testImage, 0, 0, null);
        });
        assertTestImageResult(result);
    }

    @Test
    public void testHardwareHandlingPQ() {
        Bitmap testImage = sTestImage.copy(Bitmap.Config.HARDWARE, false);
        assertTrue(testImage.hasGainmap());
        assertEquals(testImage.getGainmap().getGainmapContents().getConfig(),
                Bitmap.Config.HARDWARE);
        Bitmap result = renderWithHardware(BT2020_PQ, canvas -> {
            canvas.drawBitmap(testImage, 0, 0, null);
        });
        assertTestImageResult(result);
    }

    @Test
    public void testHardwareA8HandlingHLG() {
        Bitmap testImage = sTestImageA8.copy(Bitmap.Config.HARDWARE, false);
        assertTrue(testImage.hasGainmap());
        assertEquals(testImage.getGainmap().getGainmapContents().getConfig(),
                Bitmap.Config.HARDWARE);
        Bitmap result = renderWithHardware(BT2020_HLG, canvas -> {
            canvas.drawBitmap(testImage, 0, 0, null);
        });
        assertTestImageResult(result);
    }

    @Test
    public void testHardwareA8HandlingPQ() {
        Bitmap testImage = sTestImageA8.copy(Bitmap.Config.HARDWARE, false);
        assertTrue(testImage.hasGainmap());
        assertEquals(testImage.getGainmap().getGainmapContents().getConfig(),
                Bitmap.Config.HARDWARE);
        Bitmap result = renderWithHardware(BT2020_PQ, canvas -> {
            canvas.drawBitmap(testImage, 0, 0, null);
        });
        assertTestImageResult(result);
    }
}
