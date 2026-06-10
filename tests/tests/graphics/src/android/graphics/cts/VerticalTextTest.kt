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
package android.graphics.cts

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.text.TextRunShaper
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.text.TextPaint
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.text.flags.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VerticalTextTest {

    @Rule
    @JvmField
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    // Shorthand of shaping given whole text in LTR context.
    private fun shape(text: String, paint: Paint) =
        TextRunShaper.shapeTextRun(text, 0, text.length, 0, text.length, 0f, 0f, false, paint)

    @RequiresFlagsEnabled(Flags.FLAG_VERTICAL_TEXT_LAYOUT)
    @Test
    fun testVerticalAdvance() {
        val text = "lllllll"
        val hAdvances = FloatArray(text.length)
        val vAdvances = FloatArray(text.length)
        val hPaint = TextPaint().apply {
            textSize = 100f // make 1em = 100px
        }

        val vPaint = TextPaint().apply {
            textSize = 100f // make 1em = 100px
            flags = flags or Paint.VERTICAL_TEXT_FLAG
        }

        val hAdvance = hPaint.getTextRunAdvances(
            text.toCharArray(),
            0,
            text.length,
            0,
            text.length,
            false,
            hAdvances,
            0
        )
        val vAdvance = vPaint.getTextRunAdvances(
            text.toCharArray(),
            0,
            text.length,
            0,
            text.length,
            false,
            vAdvances,
            0
        )

        // The actual advances are depending on the font file but likely the letter "l" has small
        // horizontal advances compared to the line spacing, therefore, we can expect that the
        // vertical advances are larger than the horizontal advances here if the vertical text
        // layout is supported.
        assertThat(vAdvance).isGreaterThan(hAdvance)

        vAdvances.zip(hAdvances) { vCharAdvance, hCharAdvance ->
            assertThat(vCharAdvance).isGreaterThan(hCharAdvance)
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_VERTICAL_TEXT_LAYOUT)
    @Test
    fun testVerticalShaping() {
        val text = "Android Android Android"
        val vPaint = TextPaint().apply {
            textSize = 100f // make 1em = 100px
            flags = flags or Paint.VERTICAL_TEXT_FLAG
        }

        val vGlyphs = shape(text, vPaint)

        val lastY = vGlyphs.getGlyphY(vGlyphs.glyphCount() - 1)
        val lastX = vGlyphs.getGlyphX(vGlyphs.glyphCount() - 1)

        // The drawing origin should be moved to bottom, i.e. the Y offset should be larger than X
        // offset.
        assertThat(lastY).isGreaterThan(lastX)
    }

    @RequiresFlagsEnabled(Flags.FLAG_VERTICAL_TEXT_LAYOUT)
    @Test
    fun testVerticalBoundingBox() {
        val text = "Android Android Android"
        val vPaint = TextPaint().apply {
            textSize = 100f // make 1em = 100px
            flags = flags or Paint.VERTICAL_TEXT_FLAG
        }

        val r = Rect()
        vPaint.getTextBounds(text, 0, text.length, r)

        assertThat(r.height()).isGreaterThan(r.width())
    }
}
