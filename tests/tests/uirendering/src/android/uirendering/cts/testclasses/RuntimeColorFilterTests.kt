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

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RuntimeColorFilter
import android.graphics.RuntimeXfermode
import android.graphics.Shader
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.uirendering.cts.bitmapverifiers.RectVerifier
import android.uirendering.cts.testinfrastructure.ActivityTestBase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.android.graphics.hwui.flags.Flags
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RequiresFlagsEnabled(Flags.FLAG_RUNTIME_COLOR_FILTERS_BLENDERS)
@MediumTest
@RunWith(AndroidJUnit4::class)
class RuntimeColorFilterTests : ActivityTestBase() {
    val redFilter = """
        vec4 main(half4 inColor) {
          return vec4(1.0, 0.0, 0.0, 1.0);
        }"""

    private val simpleColorInputFilter = """
       layout(color) uniform vec4 inputColor;
       uniform vec4 inputNonColor;
       vec4 main(half4 color) {
          return inputColor;
       }"""

    private val simpleColorFilter = """
       uniform float inputFloat;
       uniform int inputInt;
       vec4 main(half4 color) {
          float alpha = float(100 - inputInt) / 100.0;
          float blue = clamp(inputFloat, 0.0, 255.0) / 255.0;
          return vec4(color.r, color.g, blue, alpha);
       }"""

    val samplingInputColorFilter = """
        uniform colorFilter inputFilter;
        vec4 main(half4 c) {
          half4 color = half4(1.0, 1.0, 1.0, 1.0);
          return inputFilter.eval(color).rgba;
        }"""

    val samplingInputXfermode = """
        uniform blender inputBlender;
        vec4 main(half4 c) {
          half4 color = half4(1.0, 1.0, 1.0, 1.0);
          return inputBlender.eval(color, color).rgba;
        }"""

    private val colorFilter = """
       uniform vec4 inputColor;
       vec4 main(half4 color) {
          return inputColor;
       }"""

    private val intColorFilter = """
       uniform int4 inputColor;
       vec4 main(half4 color) {
          return vec4(inputColor);
       }"""

    private val bitmapShader = BitmapShader(
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
        Shader.TileMode.CLAMP,
        Shader.TileMode.CLAMP
    )

    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun createWithNullInput() {
        assertThrows(java.lang.NullPointerException::class.java) {
            RuntimeColorFilter(Nulls.type())
        }
    }

    @Test
    fun createWithEmptyString() {
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            RuntimeColorFilter("")
        }
    }

    @Test
    fun setNullUniformName() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.NullPointerException::class.java) {
            filter.setFloatUniform(Nulls.type<String>(), 0.0f)
        }
    }

    @Test
    fun setEmptyUniformName() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            filter.setFloatUniform("", 0.0f)
        }
    }

    @Test
    fun setInvalidUniformName() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            filter.setFloatUniform("invalid", 0.0f)
        }
    }

    @Test
    fun setInvalidUniformType() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            filter.setFloatUniform("inputInt", 1.0f)
        }
    }

    @Test
    fun setInvalidUniformLength() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            filter.setFloatUniform("inputNonColor", 1.0f, 1.0f, 1.0f)
        }
    }

    @Test
    fun setNullIntUniformName() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.NullPointerException::class.java) {
            filter.setIntUniform(Nulls.type<String>(), 0)
        }
    }

    @Test
    fun setEmptyIntUniformName() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            filter.setIntUniform("", 0)
        }
    }

    @Test
    fun setInvalidIntUniformName() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            filter.setIntUniform("invalid", 0)
        }
    }

    @Test
    fun setInvalidIntUniformType() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            filter.setIntUniform("inputFloat", 1)
        }
    }

    @Test
    fun setInvalidIntUniformLength() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            filter.setIntUniform("inputInt", 1, 2)
        }
    }

    @Test
    fun setNullColorName() {
        val filter = RuntimeColorFilter(simpleColorInputFilter)
        assertThrows(java.lang.NullPointerException::class.java) {
            filter.setColorUniform(Nulls.type<String>(), 0)
        }
    }

    @Test
    fun setEmptyColorName() {
        val filter = RuntimeColorFilter(simpleColorInputFilter)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            filter.setColorUniform("", 0)
        }
    }

    @Test
    fun setInvalidColorName() {
        val filter = RuntimeColorFilter(simpleColorInputFilter)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            filter.setColorUniform("invalid", 0)
        }
    }

    @Test
    fun setNullColorValue() {
        val filter = RuntimeColorFilter(simpleColorInputFilter)
        assertThrows(java.lang.NullPointerException::class.java) {
            filter.setColorUniform("inputColor", Nulls.type<Color>())
        }
    }

    @Test
    fun setColorValueNonColorUniform() {
        val filter = RuntimeColorFilter(simpleColorInputFilter)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            filter.setColorUniform("inputNonColor", Color.BLUE)
        }
    }

    @Test
    fun setNullShaderName() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.NullPointerException::class.java) {
            filter.setInputShader(Nulls.type<String>(), bitmapShader)
        }
    }

    @Test
    fun setEmptyShaderName() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            filter.setInputShader("", bitmapShader)
        }
    }

    @Test
    fun setInvalidShaderName() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            filter.setInputShader("invalid", bitmapShader)
        }
    }

    @Test
    fun setNullShaderValue() {
        val filter = RuntimeColorFilter(simpleColorFilter)
        assertThrows(java.lang.NullPointerException::class.java) {
            filter.setInputShader("inputShader", Nulls.type<Shader>())
        }
    }

    @Test
    fun testDefaultUniform() {
        val cf = RuntimeColorFilter(simpleColorFilter)

        val paint = Paint()
        paint.colorFilter = cf

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
                true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.BLACK, rect))
    }

    @Test
    fun testDefaultColorUniform() {
        val cf = RuntimeColorFilter(simpleColorInputFilter)

        val paint = Paint()
        paint.colorFilter = cf
        paint.blendMode = BlendMode.SRC

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.TRANSPARENT, rect))
    }

    @Test
    fun testSimpleColorUniform() {
        val cf = RuntimeColorFilter(redFilter)

        val paint = Paint()
        paint.colorFilter = cf
        paint.blendMode = BlendMode.SRC

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testChildColorFilter() {
        val redFilter = """
            vec4 main(half4 inColor) {
              return vec4(1.0, 0.0, 0.0, 1.0);
            }"""
        val redInputColorFilter = RuntimeColorFilter(redFilter)

        val colorFilter = RuntimeColorFilter(samplingInputColorFilter)
        colorFilter.setInputColorFilter("inputFilter", redInputColorFilter)
        val paint = Paint()
        paint.colorFilter = colorFilter

        val rect = Rect(10, 10, 80, 80)
        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testChildXfermode() {
        val redXfermode = """
            vec4 main(half4 src, half4 dst) {
              return vec4(1.0, 0.0, 0.0, 1.0);
            }"""
        val redInputXfermode = RuntimeXfermode(redXfermode)
        val rect = Rect(10, 10, 80, 80)

        val paint = Paint()
        val colorFilter = RuntimeColorFilter(samplingInputXfermode)
        colorFilter.setInputXfermode("inputBlender", redInputXfermode)
        paint.colorFilter = colorFilter

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testSetFloatsUniform() {
        val cf = RuntimeColorFilter(colorFilter)

        val color = Color.valueOf(Color.RED)
        cf.setFloatUniform(
            "inputColor",
            color.red(),
            color.blue(),
            color.green(),
            color.alpha()
        )
        val paint = Paint()
        paint.colorFilter = cf
        paint.blendMode = BlendMode.SRC

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testSetFloatArrayUniform() {
        val cf = RuntimeColorFilter(colorFilter)

        val color = Color.valueOf(Color.RED)
        cf.setFloatUniform("inputColor", color.components)
        val paint = Paint()
        paint.colorFilter = cf
        paint.blendMode = BlendMode.SRC

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testSetColorUniform() {
        val cf = RuntimeColorFilter(simpleColorInputFilter)

        val color = Color.valueOf(Color.RED)
        cf.setColorUniform("inputColor", color.pack())
        val paint = Paint()
        paint.colorFilter = cf
        paint.blendMode = BlendMode.SRC

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testSetIntUniform() {
        val cf = RuntimeColorFilter(intColorFilter)

        val color = Color.valueOf(Color.RED)
        cf.setIntUniform(
            "inputColor",
            color.red().toInt(),
            color.green().toInt(),
            color.blue().toInt(),
            color.alpha().toInt()
        )
        val paint = Paint()
        paint.colorFilter = cf
        paint.blendMode = BlendMode.SRC

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testSetIntArrayUniform() {
        val cf = RuntimeColorFilter(intColorFilter)

        val color = Color.valueOf(Color.RED)
        val cVals: IntArray = intArrayOf(
            color.red().toInt(),
            color.green().toInt(),
            color.blue().toInt(),
            color.alpha().toInt()
        )
        cf.setIntUniform("inputColor", cVals)
        val paint = Paint()
        paint.colorFilter = cf
        paint.blendMode = BlendMode.SRC

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }
}
