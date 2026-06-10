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
package android.uirendering.cts.testclasses

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.Settings
import android.text.Editable
import android.text.Selection
import android.text.Spannable
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.uirendering.cts.R
import android.uirendering.cts.bitmapcomparers.MSSIMComparer
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier
import android.uirendering.cts.testinfrastructure.ActivityTestBase
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import androidx.test.filters.MediumTest
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.PollingCheck
import com.android.graphics.hwui.flags.Flags
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_HIGH_CONTRAST_TEXT_SMALL_TEXT_RECT)
class TextViewHighContrastTextTests : ActivityTestBase() {

    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val typeface: Typeface by lazy {
        var typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        val paint = Paint()
        paint.typeface = typeface
        val fontFile = TypefaceTestUtil.getFirstFont(TEST_STRING1, paint)
        if (!fontFile.name.startsWith("Roboto")) {
           typeface = TypefaceTestUtil.getRobotoTypeface(
                    typeface.weight,
                    typeface.isItalic
                )
        }

        typeface
    }

    @After
    fun teardown() {
        // Turn off just in case, since resetToDefaults() doesn't seem to totally work.
        setHighContrastTextEnabled(false)
        invokeWithShellPermissions {
            Settings.Secure.resetToDefaults(instrumentation.context.contentResolver, TAG)
        }
    }

    private fun fontTestBody(
        id: Int,
        useHardware: Boolean = false,
        text: CharSequence,
        onViewInitialized: ((view: TextView) -> Unit)? = null
    ) {
        createTest()
                .addLayout(
                    R.layout.high_contrast_textview,
                    {
                        val textView = it.findViewById<TextView>(R.id.label)!!
                        textView.setText(text, TextView.BufferType.SPANNABLE)
                        textView.typeface = typeface

                        onViewInitialized?.invoke(textView)
                    },
                    useHardware
                )
                .runWithVerifier(createGoldenVerifier(id))
    }

    private fun createGoldenVerifier(id: Int): GoldenImageVerifier {
        val goldenBitmap = BitmapFactory.decodeResource(getActivity().resources, id)
        return GoldenImageVerifier(goldenBitmap, MSSIMComparer(REGULAR_THRESHOLD))
    }

    @Test
    fun highContrastTextDisabled_spans() {
        fontTestBody(
            R.drawable.golden_hct_disabled_spans,
            text = generateAllSpanExamples()
        )
    }

    @Test
    fun highContrastTextEnabled_spans() {
        setHighContrastTextEnabled(true)

        fontTestBody(
            R.drawable.golden_hct_enabled_spans,
            text = generateAllSpanExamples()
        )
    }

    @Test
    fun highContrastTextDisabled_selectionHighlight() {
        fontTestBody(
            R.drawable.golden_hct_disabled_selection_highlight,
            text = generateAllSpanExamples()
        ) {
                Selection.setSelection(
                    it.text as Spannable,
                    /* start= */
                    7,
                    /* stop= */
                    13
                )
                it.requestFocus()
            }
    }

    @Test
    fun highContrastTextEnabled_selectionHighlight() {
        setHighContrastTextEnabled(true)

        fontTestBody(
            R.drawable.golden_hct_enabled_selection_highlight,
            text = generateAllSpanExamples()
        ) {
                Selection.setSelection(
                    it.text as Spannable,
                        /* start= */
                    7,
                    /* stop= */
                    13
                )
                it.requestFocus()
            }
    }

    @Test
    fun highContrastTextDisabled_canvasDrawText() {
        createTest()
                .addCanvasClient { canvas: Canvas, _: Int, _: Int ->
                    val p = Paint()
                    p.isAntiAlias = true
                    p.color = Color.DKGRAY
                    p.textSize = 26f
                    p.typeface = typeface

                    canvas.drawColor(Color.GREEN)
                    canvas.drawText(TEST_STRING1, 1f, 20f, p)
                    canvas.drawText(TEST_STRING2, 1f, 50f, p)
                    canvas.drawText(TEST_STRING3, 1f, 80f, p)
                }
                .runWithVerifier(
                    createGoldenVerifier(R.drawable.golden_hct_disabled_canvas_draw_text)
                )
    }

    @Test
    fun highContrastTextEnabled_canvasDrawText() {
        setHighContrastTextEnabled(true)

        createTest()
            .addCanvasClient { canvas: Canvas, _: Int, _: Int ->
                val p = Paint()
                p.isAntiAlias = true
                p.color = Color.DKGRAY
                p.textSize = 26f
                p.typeface = typeface

                canvas.drawColor(Color.GREEN)
                canvas.drawText(TEST_STRING1, 1f, 20f, p)
                canvas.drawText(TEST_STRING2, 1f, 50f, p)
                canvas.drawText(TEST_STRING3, 1f, 80f, p)
            }
            .runWithVerifier(createGoldenVerifier(R.drawable.golden_hct_enabled_canvas_draw_text))
    }

    private fun setHighContrastTextEnabled(isEnabled: Boolean) {
        invokeWithShellPermissions {
            Settings.Secure.putInt(
                instrumentation.context.contentResolver,
                ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED,
                if (isEnabled) 1 else 0
            )
        }
        PollingCheck.waitFor(5000) {
            val am = activity.getSystemService(AccessibilityManager::class.java)!!
            am.isHighContrastTextEnabled == isEnabled
        }
    }

    /**
     * Run an arbitrary piece of code while holding shell permissions.
     *
     * @param runnable an expression that performs the desired operation with shell permissions
     * @return the return value of the expression
     */
    private fun invokeWithShellPermissions(runnable: Runnable) {
        val uiAutomation = instrumentation.uiAutomation
        try {
            uiAutomation.adoptShellPermissionIdentity()
            runnable.run()
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    companion object {
        // Thresholds are barely loose enough for differences between sw and hw renderers.
        private const val REGULAR_THRESHOLD = 0.92

        // Representative characters including some from Unicode 7
        private const val TEST_STRING1 = "Hambu"
        private const val TEST_STRING2 = "🤪 \u20bd"
        private const val TEST_STRING3 = "\u20b9\u0186\u0254\u1e24\u1e43"

        private const val ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED = "high_text_contrast_enabled"
    }
}

private fun generateAllSpanExamples(): Editable {
    val spannableString = Editable.Factory.getInstance().newEditable(
        "Hi, this is the interface for text to which markup objects can be attached and detached."
    )

    val textStart = TextUtils.indexOf(spannableString, "text")
    val textEnd = textStart + 4
    // Apply different types of spans
    spannableString.setSpan(
        ForegroundColorSpan(Color.DKGRAY),
        0,
        2,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    spannableString.setSpan(
        BackgroundColorSpan(Color.CYAN),
        6,
        10,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    spannableString.setSpan(
        UnderlineSpan(),
        11,
        15,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    spannableString.setSpan(
        StrikethroughSpan(),
        16,
        20,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    spannableString.setSpan(
        StyleSpan(Typeface.BOLD),
        textStart,
        textEnd,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    spannableString.setSpan(
        object : ClickableSpan() {
            override fun onClick(view: View) {
            }
        },
        20,
        24,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    spannableString.setSpan(
        TypefaceSpan("serif"),
        textEnd + 1,
        textEnd + 5,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
    spannableString.setSpan(
        RelativeSizeSpan(1.5f),
        26,
        30,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )

    return spannableString
}
