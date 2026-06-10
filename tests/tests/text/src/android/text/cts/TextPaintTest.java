/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.text.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Test {@link TextPaint}. */
@RunWith(AndroidJUnit4.class)
public class TextPaintTest {
    private static final int DEFAULT_PAINT_FLAGS = TextPaint.DEV_KERN_TEXT_FLAG
            | TextPaint.EMBEDDED_BITMAP_TEXT_FLAG | TextPaint.FILTER_BITMAP_FLAG;

    @Test
    public void testConstructor() {
        TextPaint textPaint;

        textPaint = new TextPaint();
        assertEquals(DEFAULT_PAINT_FLAGS | TextPaint.ANTI_ALIAS_FLAG,
                textPaint.getFlags());

        textPaint = new TextPaint(TextPaint.DITHER_FLAG);
        assertEquals((TextPaint.DITHER_FLAG | DEFAULT_PAINT_FLAGS),
                textPaint.getFlags());

        final Paint paint = new Paint();
        paint.setTextSize(42f);
        textPaint = new TextPaint(paint);
        assertEquals(paint.getTextSize(), textPaint.getTextSize(), 0.0f);
    }

    @Test
    public void testSet() {
        TextPaint textPaintSrc = new TextPaint(TextPaint.DITHER_FLAG);
        int[] drawableState = new int[] { 0, 1 };
        textPaintSrc.bgColor = Color.GREEN;
        textPaintSrc.baselineShift = 10;
        textPaintSrc.linkColor = Color.BLUE;
        textPaintSrc.drawableState = drawableState;
        textPaintSrc.setTypeface(Typeface.DEFAULT_BOLD);
        textPaintSrc.underlineColor = 0x00112233;
        textPaintSrc.underlineThickness = 12.0f;

        TextPaint textPaint = new TextPaint();
        assertEquals(0, textPaint.bgColor);
        assertEquals(0, textPaint.baselineShift);
        assertEquals(0, textPaint.linkColor);
        assertNull(textPaint.drawableState);
        assertNull(textPaint.getTypeface());
        assertEquals(0, textPaint.underlineColor);
        assertEquals(0.0f, textPaint.underlineThickness, 0.0f);

        textPaint.set(textPaintSrc);
        assertEquals(textPaintSrc.bgColor, textPaint.bgColor);
        assertEquals(textPaintSrc.baselineShift, textPaint.baselineShift);
        assertEquals(textPaintSrc.linkColor, textPaint.linkColor);
        assertArrayEquals(textPaintSrc.drawableState, textPaint.drawableState);
        assertEquals(textPaintSrc.getTypeface(), textPaint.getTypeface());
        assertEquals(textPaintSrc.getFlags(), textPaint.getFlags());
        assertEquals(0x00112233, textPaint.underlineColor);
        assertEquals(12.0f, textPaint.underlineThickness, 0.0f);

        try {
            textPaint.set(null);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
        }
    }

    @Test
    // b/169080922
    public void testInfinityTextSize_doesntCrash() {
        Paint paint = new Paint();
        paint.setTextSize(Float.POSITIVE_INFINITY);

        // Making sure following measureText is not crashing
        paint.measureText("Hello \uD83D\uDC4B");  // Latin characters and emoji
    }
}
