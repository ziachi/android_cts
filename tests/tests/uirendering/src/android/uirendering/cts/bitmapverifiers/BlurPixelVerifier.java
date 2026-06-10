/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.uirendering.cts.bitmapverifiers;

import android.graphics.Color;
import android.graphics.Rect;
import androidx.annotation.ColorInt;

public class BlurPixelVerifier extends PerPixelBitmapVerifier {
    // The background color that the blurred rectangle is blended against.
    private final Color mDstColor;
    // The original color of the rectangle before it was blurred.
    private final Color mSrcColor;

    // The original geometry of the rectangle before it was blurred.
    private final Rect mSrcRect;
    // The blur radius applied to the rectangle.
    private final int mBlurRadius;
    private final int mKernelWidth;

    // Pre-computed 2D Gaussian integral based on mBlurRadius. For x,y in [-radius, radius],
    // mKernelWeights[(y+mBlurRadius)*mKernelWidth + x + mBlurRadius] is integral from of the
    // Gaussian equation from [-x, +radius]x[-y, +radius].
    private final float[] mKernelWeights;

    /**
     * Create a BitmapVerifier that compares pixel values assuming that a solid color
     * rectangle of `srcColor` has been blurred with a blur radius of `blurRadius` and blended
     * against a background of `dstColor`.
     */
    public BlurPixelVerifier(@ColorInt int srcColor, @ColorInt int dstColor,
                             Rect srcRect, int blurRadius) {
        // Use a 15% spatial tolerance and default color tolerance to account for reasonable
        // approximations in the blur rendering.
        super(DEFAULT_THRESHOLD, 0.15f);

        mSrcColor = Color.valueOf(srcColor);
        mDstColor = Color.valueOf(dstColor);
        mSrcRect = srcRect;
        mBlurRadius = blurRadius;
        mKernelWidth = mBlurRadius * 2 + 1;

        // The sigma equation comes from Blur::convertRadiusToSigma() in native code.
        float sigma = mBlurRadius > 0 ? 0.57735f * mBlurRadius + 0.5f : 0.f;
        float sigmaDenom = mBlurRadius > 0 ? 1.0f / (2.0f * sigma * sigma) : 1.f;

        // Calculate Gaussian weights for the full 2D kernel.
        mKernelWeights = new float[mKernelWidth * mKernelWidth];
        for (int y = -mBlurRadius; y < mBlurRadius; ++y) {
            for (int x = -mBlurRadius; x < mBlurRadius; ++x) {
                int i = (y + mBlurRadius) * mKernelWidth + (x + mBlurRadius);
                mKernelWeights[i] = (float) Math.exp(-(x*x + y*y)*sigmaDenom);
            }
        }

        // Summed area table, do base horizontal and vertical edge first.
        for (int x = 1; x < mKernelWidth; ++x) {
            mKernelWeights[x] += mKernelWeights[x - 1];
        }
        for (int y = 1; y < mKernelWidth; ++y) {
            mKernelWeights[y * mKernelWidth] += mKernelWeights[(y - 1) * mKernelWidth];
        }
        // Fill in 2D portion based on adjacent cells that are already summed up
        for (int y = 1; y < mKernelWidth; ++y) {
            for (int x = 1; x < mKernelWidth; ++x) {
                int a = (y-1) * mKernelWidth + x - 1;
                int b = (y-1) * mKernelWidth + x;
                int c = y * mKernelWidth + x - 1;
                int d = y * mKernelWidth + x;
                mKernelWeights[d] += mKernelWeights[b] + mKernelWeights[c] - mKernelWeights[a];
            }
        }

        // Normalize
        float norm = 1.f / mKernelWeights[mKernelWeights.length - 1];
        for (int i = 0; i < mKernelWidth*mKernelWidth; ++i) {
            mKernelWeights[i] *= norm;
        }
    }

    @Override @ColorInt
    protected int getExpectedColor(int x, int y) {
        // Calculate minimum distance between the pixel location and the
        // boundary of the unblurred rectangle. Negative values indicate the pixel
        // is outside the unblurred rectangle.
        int minX = Math.min(x - mSrcRect.left, mSrcRect.right - x - 1);
        int minY = Math.min(y - mSrcRect.top, mSrcRect.bottom - y - 1);

        // The blur radius is the maximum distance that the srcColor or the dstColor
        // can influence another pixel. That means if minDistance <= -mBlurRadius,
        // the pixel color should be mDstColor. If minDistance >= mBlurRadius, the pixel
        // color should be mSrcColor. Otherwise, the pixel color should be a Gaussian-weighted
        // interpolation between the two.
        float weight;
        if (minX < -mBlurRadius || minY < -mBlurRadius) {
            weight = 0.f;
        } else if (minX > mBlurRadius && minY > mBlurRadius) {
            weight = 1.f;
        } else {
            minX = Math.clamp(minX, -mBlurRadius, mBlurRadius);
            minY = Math.clamp(minY, -mBlurRadius, mBlurRadius);
            weight = mKernelWeights[(minY + mBlurRadius)*mKernelWidth + minX + mBlurRadius];
        }

        // Calculate the expected color of the pixel.
        float expectedRed = weight * mSrcColor.red() + (1.0f - weight) * mDstColor.red();
        float expectedGreen = weight * mSrcColor.green() + (1.0f - weight) * mDstColor.green();
        float expectedBlue = weight * mSrcColor.blue() + (1.0f - weight) * mDstColor.blue();
        return Color.rgb(expectedRed, expectedGreen, expectedBlue);
    }
}
