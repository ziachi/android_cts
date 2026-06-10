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

package android.security.cts.camera.open

import android.app.Service
import android.content.Intent
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.IBinder
import android.security.cts.camera.open.lib.IntentKeys
import android.util.Log
import android.view.Surface
import java.nio.IntBuffer
import java.util.concurrent.Executors

class CameraOpenerService : Service() {

  private lateinit var keys: IntentKeys
  private lateinit var cameraOpener: CameraOpener

  private lateinit var display: EGLDisplay
  private lateinit var config: EGLConfig
  private lateinit var context: EGLContext
  private lateinit var placeholderSurface: EGLSurface

  private var cameraTexId: Int = -1
  private var cameraSurface: Surface? = null
  private var cameraSurfaceTexture: SurfaceTexture? = null

  private val renderExecutor = Executors.newSingleThreadExecutor()

  override fun onCreate() {
    Log.v(TAG, "onCreate")
    super.onCreate()
    keys = IntentKeys(packageName)

    display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    checkEglError("eglGetDisplay")

    val version = intArrayOf(0, 0)
    if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
      throw RuntimeException("Unable to initialize EGL14")
    }
    checkEglError("eglInitialize")

    val rgbaBits = 8
    val configAttribList =
        intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE,
            EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,
            EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE,
            rgbaBits,
            EGL14.EGL_GREEN_SIZE,
            rgbaBits,
            EGL14.EGL_BLUE_SIZE,
            rgbaBits,
            EGL14.EGL_ALPHA_SIZE,
            rgbaBits,
            EGL14.EGL_DEPTH_SIZE,
            16,
            EGL14.EGL_NONE,
        )

    val configs = arrayOfNulls<EGLConfig>(1)
    val numConfigs = intArrayOf(1)
    EGL14.eglChooseConfig(display, configAttribList, 0, configs, 0, configs.size, numConfigs, 0)
    config = configs[0]!!

    val requestedVersion = 2
    val contextAttribList =
        intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, requestedVersion, EGL14.EGL_NONE)

    context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribList, 0)
    if (context == EGL14.EGL_NO_CONTEXT) {
      throw RuntimeException("Failed to create EGL context")
    }

    val placeholderSurfaceAttribs =
        intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
    placeholderSurface =
        EGL14.eglCreatePbufferSurface(display, config, placeholderSurfaceAttribs, /*offset*/ 0)

    EGL14.eglMakeCurrent(display, placeholderSurface, placeholderSurface, context)

    cameraTexId = createTexture()
    cameraSurfaceTexture = SurfaceTexture(cameraTexId)
    cameraSurface = Surface(cameraSurfaceTexture)

    cameraOpener =
        CameraOpener(
            context = this, keys, textureView = null, surfaceTexture = cameraSurfaceTexture)
  }

  override fun onDestroy() {
    Log.v(TAG, "onDestroy")
    super.onDestroy()
    cameraOpener.release()

    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    EGL14.eglDestroySurface(display, placeholderSurface)
    placeholderSurface = EGL14.EGL_NO_SURFACE
    EGL14.eglDestroyContext(display, context)
    context = EGL14.EGL_NO_CONTEXT
    EGL14.eglReleaseThread()
    EGL14.eglTerminate(display)
    display = EGL14.EGL_NO_DISPLAY
  }

  override fun onBind(intent: Intent): IBinder {
    Log.v(TAG, "onBind")
    return cameraOpener.aidlInterface
  }

  private fun checkEglError(op: String) {
    val eglError = EGL14.eglGetError()
    if (eglError != EGL14.EGL_SUCCESS) {
      val msg = op + ": eglError 0x" + Integer.toHexString(eglError)
      throw IllegalStateException(msg)
    }
  }

  private fun checkGlError(op: String) {
    val error = GLES30.glGetError()
    if (error != GLES30.GL_NO_ERROR) {
      val msg = op + ": glError " + glErrorStr(error)
      throw IllegalStateException(msg)
    }
  }

  private fun glErrorStr(error: Int) =
      when (error) {
        GLES30.GL_INVALID_ENUM -> "GL_INVALID_ENUM"
        GLES30.GL_INVALID_VALUE -> "GL_INVALID_VALUE"
        GLES30.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
        GLES30.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
        else -> "0x" + Integer.toHexString(error)
      }

  private fun createTexture(): Int {
    val buffer = IntBuffer.allocate(1)
    GLES30.glGenTextures(1, buffer)
    checkGlError("glGenTextures")
    val texId = buffer.get(0)
    val target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
    GLES30.glBindTexture(target, /* texture= */ texId)
    checkGlError("glBindTexture")

    GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
    GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
    GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
    GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    return texId
  }

  private companion object {
    private val TAG = CameraOpenerService::class.java.simpleName
  }
}
