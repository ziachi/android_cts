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
class RuntimeXfermodeTests : ActivityTestBase() {
    private val simpleColorInputXfermode = """
       layout(color) uniform vec4 inputColor;
       uniform vec4 inputNonColor;
       vec4 main(half4 src, half4 dst) {
          return inputColor;
       }"""

    private val simpleXfermode = """
       uniform float inputFloat;
       uniform int inputInt;
       vec4 main(half4 src, half4 dst) {
          float alpha = float(100 - inputInt) / 100.0;
          float blue = clamp(inputFloat, 0.0, 255.0) / 255.0;
          return vec4(src.r, dst.g, blue, alpha);
       }"""

    private val samplingInputColorFilter = """
        uniform colorFilter inputFilter;
        vec4 main(half4 src, half4 dst) {
          half4 color = half4(1.0, 1.0, 1.0, 1.0);
          return inputFilter.eval(color).rgba;
        }"""

    private val samplingInputXfermode = """
        uniform blender inputBlender;
        vec4 main(half4 src, half4 dst) {
          half4 color = half4(1.0, 1.0, 1.0, 1.0);
          return inputBlender.eval(color, color).rgba;
        }"""

    private val returnsInputXfermode = """
       uniform vec4 inputColor;
       vec4 main(half4 src, half4 dst) {
          return inputColor;
       }"""

    private val returnsIntInputXfermode = """
       uniform int4 inputColor;
       vec4 main(half4 src, half4 dst) {
          return vec4(inputColor);
       }"""

    @get:Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val bitmapShader = BitmapShader(
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
        Shader.TileMode.CLAMP,
        Shader.TileMode.CLAMP
    )

    @Test
    fun createWithNullInput() {
        assertThrows(java.lang.NullPointerException::class.java) {
            RuntimeXfermode(Nulls.type())
        }
    }

    @Test
    fun createWithEmptyString() {
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            RuntimeXfermode("")
        }
    }

    @Test
    fun setNullUniformName() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.NullPointerException::class.java) {
            xfermode.setFloatUniform(Nulls.type<String>(), 0.0f)
        }
    }

    @Test
    fun setEmptyUniformName() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            xfermode.setFloatUniform("", 0.0f)
        }
    }

    @Test
    fun setInvalidUniformName() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            xfermode.setFloatUniform("invalid", 0.0f)
        }
    }

    @Test
    fun setInvalidUniformType() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            xfermode.setFloatUniform("inputInt", 1.0f)
        }
    }

    @Test
    fun setInvalidUniformLength() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            xfermode.setFloatUniform("inputNonColor", 1.0f, 1.0f, 1.0f)
        }
    }

    @Test
    fun setNullIntUniformName() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.NullPointerException::class.java) {
            xfermode.setIntUniform(Nulls.type<String>(), 0)
        }
    }

    @Test
    fun setEmptyIntUniformName() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            xfermode.setIntUniform("", 0)
        }
    }

    @Test
    fun setInvalidIntUniformName() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            xfermode.setIntUniform("invalid", 0)
        }
    }

    @Test
    fun setInvalidIntUniformType() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            xfermode.setIntUniform("inputFloat", 1)
        }
    }

    @Test
    fun setInvalidIntUniformLength() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            xfermode.setIntUniform("inputInt", 1, 2)
        }
    }

    @Test
    fun setNullColorName() {
        val xfermode = RuntimeXfermode(simpleColorInputXfermode)
        assertThrows(java.lang.NullPointerException::class.java) {
            xfermode.setColorUniform(Nulls.type<String>(), 0)
        }
    }

    @Test
    fun setEmptyColorName() {
        val xfermode = RuntimeXfermode(simpleColorInputXfermode)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            xfermode.setColorUniform("", 0)
        }
    }

    @Test
    fun setInvalidColorName() {
        val xfermode = RuntimeXfermode(simpleColorInputXfermode)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            xfermode.setColorUniform("invalid", 0)
        }
    }

    @Test
    fun setNullColorValue() {
        val xfermode = RuntimeXfermode(simpleColorInputXfermode)
        assertThrows(java.lang.NullPointerException::class.java) {
            xfermode.setColorUniform("inputColor", Nulls.type<Color>())
        }
    }

    @Test
    fun setColorValueNonColorUniform() {
        val xfermode = RuntimeXfermode(simpleColorInputXfermode)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            xfermode.setColorUniform("inputNonColor", Color.BLUE)
        }
    }

    @Test
    fun setNullShaderName() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.NullPointerException::class.java) {
            xfermode.setInputShader(Nulls.type<String>(), bitmapShader)
        }
    }

    @Test
    fun setEmptyShaderName() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            xfermode.setInputShader("", bitmapShader)
        }
    }

    @Test
    fun setInvalidShaderName() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.IllegalArgumentException::class.java) {
            xfermode.setInputShader("invalid", bitmapShader)
        }
    }

    @Test
    fun setNullShaderValue() {
        val xfermode = RuntimeXfermode(simpleXfermode)
        assertThrows(java.lang.NullPointerException::class.java) {
            xfermode.setInputShader("inputShader", Nulls.type<Shader>())
        }
    }

    @Test
    fun testDefaultColorUniform() {
        val xfermode = RuntimeXfermode(simpleColorInputXfermode)

        val paint = Paint()
        paint.xfermode = xfermode

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.TRANSPARENT, rect))
    }

    @Test
    fun testChildColorFilter() {
        val redFilter = """
            vec4 main(half4 inColor) {
              return vec4(1.0, 0.0, 0.0, 1.0);
            }"""
        val redInputColorFilter = RuntimeColorFilter(redFilter)

        val xfermode = RuntimeXfermode(samplingInputColorFilter)
        xfermode.setInputColorFilter("inputFilter", redInputColorFilter)
        val paint = Paint()
        paint.xfermode = xfermode

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
        val xfermode = RuntimeXfermode(samplingInputXfermode)
        xfermode.setInputXfermode("inputBlender", redInputXfermode)
        paint.xfermode = xfermode

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testSetFloatsUniform() {
        val xfermode = RuntimeXfermode(returnsInputXfermode)

        val color = Color.valueOf(Color.RED)
        xfermode.setFloatUniform(
            "inputColor",
            color.red(),
            color.blue(),
            color.green(),
            color.alpha()
        )
        val paint = Paint()
        paint.xfermode = xfermode

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testSetFloatArrayUniform() {
        val xfermode = RuntimeXfermode(returnsInputXfermode)

        val color = Color.valueOf(Color.RED)
        xfermode.setFloatUniform("inputColor", color.components)
        val paint = Paint()
        paint.xfermode = xfermode

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testSetColorUniform() {
        val xfermode = RuntimeXfermode(simpleColorInputXfermode)

        val color = Color.valueOf(Color.RED)
        xfermode.setColorUniform("inputColor", color.pack())
        val paint = Paint()
        paint.xfermode = xfermode

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testSetIntUniform() {
        val xfermode = RuntimeXfermode(returnsIntInputXfermode)

        val color = Color.valueOf(Color.RED)
        xfermode.setIntUniform(
            "inputColor",
            color.red().toInt(),
            color.green().toInt(),
            color.blue().toInt(),
            color.alpha().toInt()
        )
        val paint = Paint()
        paint.xfermode = xfermode

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }

    @Test
    fun testSetIntArrayUniform() {
        val xfermode = RuntimeXfermode(returnsIntInputXfermode)

        val color = Color.valueOf(Color.RED)
        val cVals: IntArray = intArrayOf(
            color.red().toInt(),
            color.green().toInt(),
            color.blue().toInt(),
            color.alpha().toInt()
        )
        xfermode.setIntUniform("inputColor", cVals)
        val paint = Paint()
        paint.xfermode = xfermode

        val rect = Rect(10, 10, 80, 80)

        createTest().addCanvasClient(
            { canvas: Canvas, _: Int, _: Int -> canvas.drawRect(rect, paint) },
            true
        ).runWithVerifier(RectVerifier(Color.WHITE, Color.RED, rect))
    }
}
