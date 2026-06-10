/*
 * Copyright 2024 The Android Open Source Project
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

package android.virtualdevice.cts.camera.util;

import static android.graphics.ImageFormat.JPEG;
import static android.graphics.ImageFormat.YUV_420_888;

import static org.junit.Assert.fail;

import static java.lang.Byte.toUnsignedInt;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.media.Image;

import androidx.annotation.Nullable;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;

import junit.framework.AssertionFailedError;

import java.io.IOException;

/**
 * A Truth Subject to assert some characteristics of {@link Image}.
 */
public class ImageSubject extends Subject {

    private static final float EPSILON = 0.3f;
    private final @Nullable Image mActual;

    protected ImageSubject(FailureMetadata metadata, @Nullable Image actual) {
        super(metadata, actual);
        mActual = actual;
    }

    /**
     * Constructs a new {@link Subject.Factory } for {@link Image}.
     */
    public static Factory<ImageSubject, Image> images() {
        return ImageSubject::new;
    }

    /**
     * Returns a new Truth {@link Subject} for {@link Image}.
     */
    public static ImageSubject assertThat(@Nullable Image image) {
        return Truth.assertAbout(images()).that(image);
    }

    /**
     * Checks that the image contains only the provided color
     *
     * @param color The argb color representation to check against
     */
    public void hasOnlyColor(int color) {
        hasOnlyColor(Color.valueOf(color));
    }

    /**
     * Checks that the image contains only the provided color
     *
     * @param color The color to check against
     */
    public void hasOnlyColor(Color color) {
        Image actual = mActual;
        assertThat(actual).isNotNull();
        assert actual != null;
        try {
            int foundColor = imageHasOnlyColor(actual, color.toArgb());
            Truth.assertThat(Color.valueOf(foundColor)).isEqualTo(color);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Converts YUV to ARGB int representation,
    // using BT601 full-range matrix.
    // See https://www.itu.int/rec/T-REC-T.871-201105-I/en
    private static int yuv2rgb(int y, int u, int v) {
        int r = (int) max(0f, min(255f, round((y + 1.402f * (v - 128f)))));
        int g = (int) max(0f,
                min(255f, round(y - 0.344136f * (u - 128f) - 0.714136f * (v - 128f))));
        int b = (int) max(0f, min(255f, round(y + 1.772f * (u - 128f))));
        return Color.rgb(r, g, b);
    }

    // Compares two ARGB colors and returns true if they are approximately
    // the same color.
    private static boolean areColorsAlmostIdentical(int colorA, int colorB) {
        float a1 = ((colorA >> 24) & 0xff) / 255f;
        float r1 = ((colorA >> 16) & 0xff) / 255f;
        float g1 = ((colorA >> 4) & 0xff) / 255f;
        float b1 = (colorA & 0xff) / 255f;

        float a2 = ((colorB >> 24) & 0xff) / 255f;
        float r2 = ((colorB >> 16) & 0xff) / 255f;
        float g2 = ((colorB >> 4) & 0xff) / 255f;
        float b2 = (colorB & 0xff) / 255f;

        float mse = ((a1 - a2) * (a1 - a2) + (r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2)
                + (b1 - b2) * (b1 - b2)) / 4;

        return mse < EPSILON;
    }

    private static int yuv420ImageHasOnlyColor(Image image, int color) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        for (int j = 0; j < height; ++j) {
            int jChroma = j / 2;
            for (int i = 0; i < width; ++i) {
                int iChroma = i / 2;
                int y = toUnsignedInt(planes[0].getBuffer().get(
                        j * planes[0].getRowStride() + i * planes[0].getPixelStride()));
                int u = toUnsignedInt(planes[1].getBuffer().get(
                        jChroma * planes[1].getRowStride() + iChroma * planes[1].getPixelStride()));
                int v = toUnsignedInt(planes[2].getBuffer().get(
                        jChroma * planes[2].getRowStride() + iChroma * planes[2].getPixelStride()));
                int argb = yuv2rgb(y, u, v);
                if (!areColorsAlmostIdentical(argb, color)) {
                    return argb;
                }
            }
        }
        return color;
    }

    private static int jpegImageHasOnlyColor(Image image, int expectedColor) throws IOException {
        Bitmap bitmap = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(image.getPlanes()[0].getBuffer())).copy(
                Bitmap.Config.ARGB_8888, false);
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        for (int j = 0; j < height; ++j) {
            for (int i = 0; i < width; ++i) {
                int pixel = bitmap.getColor(i, j).toArgb();
                if (!areColorsAlmostIdentical(pixel, expectedColor)) {
                    return pixel;
                }
            }
        }
        return expectedColor;
    }

    private static int imageHasOnlyColor(Image image, int color) throws IOException {
        return switch (image.getFormat()) {
            case YUV_420_888 -> yuv420ImageHasOnlyColor(image, color);
            case JPEG -> jpegImageHasOnlyColor(image, color);
            default -> {
                fail("Encountered unsupported image format: " + image.getFormat());
                throw new AssertionFailedError(
                        "Encountered unsupported image format: " + image.getFormat());
            }
        };
    }
}
