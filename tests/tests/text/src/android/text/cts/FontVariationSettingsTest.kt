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
package android.text.cts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.graphics.fonts.SystemFonts
import android.graphics.text.TextRunShaper
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.text.Layout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.text.flags.Flags
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.math.ceil
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class FontVariationSettingsTest {

    @Rule
    @JvmField
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    fun assumeDefaultFontHasVariableFont() {
        val paint = Paint()
        val glyphs = TextRunShaper.shapeTextRun("a", 0, 1, 0, 1, 0f, 0f, false, paint)
        val font = glyphs.getFont(0)
        assumeTrue(FontFileTestUtil.hasTable(font.file, font.ttcIndex, "fvar"))
    }

    fun drawText(text: String, paint: TextPaint): Bitmap {
        val w = ceil(Layout.getDesiredWidth(text, paint)).toInt()
        val layout = Layout.Builder(text, 0, text.length, paint, w).build()
        val bmp = Bitmap.createBitmap(w, layout.height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, 0, text.length, 0f, -layout.getLineAscent(0).toFloat(), paint)
        return bmp
    }

    fun findRobotoFlexFont(): File {
        val font = SystemFonts.getAvailableFonts().find {
            it.file!!.name == "RobotoFlex-Regular.ttf"
        }
        assumeNotNull(font)
        return font!!.file!!
    }

    @Test
    fun testFontVariationSettingsEffective() {
        assumeDefaultFontHasVariableFont()
        val paint = TextPaint().apply {
            textSize = 100f
        }

        val expect = drawText("a", paint)

        paint.fontVariationSettings = "'wght' 900"
        val actual = drawText("a", paint)

        assertThat(actual.sameAs(expect)).isFalse()
    }

    @RequiresFlagsEnabled(Flags.FLAG_TYPEFACE_REDESIGN_READONLY)
    @Test
    fun testFontVariationSettingsEffectiveMerge() {
        val robotoFlex = findRobotoFlexFont()

        val actual = drawText("a", TextPaint().apply {
            textSize = 100f
            typeface = Typeface.CustomFallbackBuilder(
                FontFamily.Builder(
                    Font.Builder(robotoFlex)
                        .setFontVariationSettings("'wght' 500, 'slnt' -10")
                        .build()
                ).build()
            ).build()
            fontVariationOverride = "'wght' 700, 'opsz' 64"
        })

        val expect = drawText("a", TextPaint().apply {
            textSize = 100f
            typeface = Typeface.CustomFallbackBuilder(
                FontFamily.Builder(
                    Font.Builder(robotoFlex)
                        .setFontVariationSettings("'wght' 700, 'slnt' -10, 'opsz' 64")
                        .build()
                ).build()
            ).build()
        })

        assertThat(actual.sameAs(expect)).isTrue()
    }
}
