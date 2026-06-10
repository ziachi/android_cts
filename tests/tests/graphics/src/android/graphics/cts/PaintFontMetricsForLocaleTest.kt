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
package android.graphics.cts

import android.graphics.Paint
import android.graphics.Paint.FontMetrics
import android.graphics.Paint.FontMetricsInt
import android.graphics.Typeface
import android.graphics.text.TextRunShaper
import android.os.LocaleList
import android.platform.test.annotations.DisabledOnRavenwood
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@DisabledOnRavenwood(blockedBy = [Typeface::class])
class PaintFontMetricsForLocaleTest {

    companion object {
        val PAINT = Paint().apply {
            textLocales = LocaleList.forLanguageTags("ja-JP")
            textSize = 100f // make 1em = 100px
        }

        val glyphs = TextRunShaper.shapeTextRun("あ", 0, 1, 0, 1, 0f, 0f, false, PAINT)

        val latinExtent = FontMetrics().apply{
            PAINT.getFontMetrics(this)
        }

        // The getFontMetricsForLocale reserves the minimum line height of the Roboto.
        val JP_ASCENT = Math.min(latinExtent.ascent, glyphs.ascent)
        val JP_DESCENT = Math.max(latinExtent.descent, glyphs.descent)
    }

    @Test
    fun testExtentForLocale() {
        val paint = Paint(PAINT).apply {
            textLocales = LocaleList.forLanguageTags("ja-JP")
        }
        val metrics = FontMetrics()
        paint.getFontMetricsForLocale(metrics)
        assertThat(metrics.ascent).isEqualTo(JP_ASCENT)
        assertThat(metrics.descent).isEqualTo(JP_DESCENT)
    }

    @Test
    fun testExtentIntForLocale() {
        val paint = Paint(PAINT).apply {
            textLocales = LocaleList.forLanguageTags("ja-JP")
        }
        val metrics = FontMetricsInt()
        paint.getFontMetricsIntForLocale(metrics)
        assertThat(metrics.ascent).isEqualTo(JP_ASCENT.roundToInt())
        assertThat(metrics.descent).isEqualTo(JP_DESCENT.roundToInt())
    }

    @Test
    fun testExtentForRoboto() {
        val paint = Paint(PAINT).apply {
            textLocales = LocaleList.forLanguageTags("en-US")
        }

        val expectedMetrics = FontMetrics()
        paint.getFontMetrics(expectedMetrics)

        val metrics = FontMetrics()
        paint.getFontMetricsForLocale(metrics)

        assertThat(metrics.ascent).isEqualTo(expectedMetrics.ascent)
        assertThat(metrics.descent).isEqualTo(expectedMetrics.descent)
    }

    @Test
    fun testExtentIntForRoboto() {
        val paint = Paint(PAINT).apply {
            textLocales = LocaleList.forLanguageTags("en-US")
        }

        val expectedMetrics = FontMetricsInt()
        paint.getFontMetricsInt(expectedMetrics)

        val metrics = FontMetricsInt()
        paint.getFontMetricsIntForLocale(metrics)

        assertThat(metrics.ascent).isEqualTo(expectedMetrics.ascent)
        assertThat(metrics.descent).isEqualTo(expectedMetrics.descent)
    }
}
