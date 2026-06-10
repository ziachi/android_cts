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

import android.content.AttributionSource
import android.content.Context
import android.content.ContextParams
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.security.cts.camera.open.lib.ICameraOpener
import android.security.cts.camera.open.lib.IntentKeys
import android.util.Log
import android.view.Surface
import android.view.TextureView
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine

class CameraOpener(
    private val context: Context,
    private val keys: IntentKeys,
    private val textureView: TextureView?,
    private var surfaceTexture: SurfaceTexture?
) {
  private lateinit var cameraManager: CameraManager
  private var handlerThread: HandlerThread? = null
  private val cameraExecutor = Executors.newSingleThreadExecutor()

  var onStopRepeating: () -> Unit = {}

  val aidlInterface =
      object : ICameraOpener.Stub() {
        override fun openCamera1(
            attributionSource: AttributionSource,
            shouldStream: Boolean,
            shouldRepeat: Boolean
        ): Intent = runBlocking {
          Log.v(TAG, "AIDL openCamera1")
          openCamera1Async(shouldStream, shouldRepeat, attributionSource)
        }

        override fun openCamera2(
            attributionSource: AttributionSource,
            shouldStream: Boolean,
            shouldRepeat: Boolean
        ): Intent = runBlocking {
          Log.v(TAG, "AIDL openCamera2")
          openCamera2Async(shouldStream, shouldRepeat, attributionSource)
        }

        override fun openCameraNdk(
            attributionSource: AttributionSource,
            shouldStream: Boolean,
            shouldRepeat: Boolean
        ): Intent = runBlocking {
          Log.v(TAG, "AIDL openCameraNdk")
          openCameraNdkAsync(shouldStream, shouldRepeat, attributionSource)
        }

        override fun stopRepeating() = onStopRepeating()
      }

  init {
    System.loadLibrary("opencameraapp_jni")
  }

  suspend fun openCamera1Async(
      shouldStream: Boolean,
      shouldRepeat: Boolean,
      attributionSource: AttributionSource? = null,
  ): Intent {
    val result = Intent().apply { putExtra(keys.attributionSource, attributionSource.toString()) }
    try {
      if (Camera.getNumberOfCameras() > 0) {
        val camera = Camera.open(0)
        result.putExtra(keys.cameraOpened1, true)
        if (shouldStream) {
          return openCamera1Stream(camera, shouldRepeat, result)
        } else {
          camera.release()
          return result
        }
      } else {
        return result.apply { putExtra(keys.noCamera, true) }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Received exception: ${e.message}")
      return result.apply { putException(keys, e) }
    }
  }

  suspend fun openCamera2Async(
      shouldStream: Boolean,
      shouldRepeat: Boolean,
      attributionSource: AttributionSource? = null,
  ): Intent = suspendCancellableCoroutine { continuation ->
    val result = Intent().apply { putExtra(keys.attributionSource, attributionSource.toString()) }
    continuation.tryOrResume(keys, result, "openCamera2Async") {
      var customContext = context
      attributionSource?.let {
        val contextParams = ContextParams.Builder().setNextAttributionSource(it).build()
        customContext = context.createContext(contextParams)
      }
      cameraManager = customContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      if (cameraManager.getCameraIdList().isEmpty()) {
        continuation.resume(result.apply { putExtra(keys.noCamera, true) })
      }

      val cameraId = cameraManager.getCameraIdList()[0]
      cameraManager.openCamera(
          cameraId,
          cameraExecutor,
          object : CameraDevice.StateCallback() {
            override fun onOpened(cameraDevice: CameraDevice) {
              Log.v(TAG, "onOpened")

              result.putExtra(keys.cameraOpened2, true)
              if (shouldStream) {
                continuation.tryOrResume(keys, result, "openCamera2Async/onOpened") {
                  openCamera2Stream(cameraDevice, result, continuation, shouldRepeat)
                }
              } else {
                cameraDevice.close()
                continuation.resume(result)
              }
            }

            override fun onDisconnected(cameraDevice: CameraDevice) {
              Log.v(TAG, "onDisconnected")
              continuation.tryOrResume(keys, result, "openCamera2Async/onDisconnected") {
                cameraDevice.close()
              }
            }

            override fun onError(cameraDevice: CameraDevice, error: Int) {
              Log.v(TAG, "onError: " + error)

              try {
                cameraDevice.close()
              } catch (e: Exception) {
                Log.e(
                    TAG,
                    "openCamera2Async/onDisconnected: Received exception: ${e.exceptionString}")
                result.putException(keys, e)
              }

              if (continuation.isActive) { // continuation may already have been resumed
                continuation.resume(result.apply { putExtra(keys.error, error) })
              }
            }
          })
    }
  }

  suspend fun openCameraNdkAsync(
      shouldStream: Boolean,
      shouldRepeat: Boolean,
      attributionSource: AttributionSource? = null
  ): Intent = suspendCancellableCoroutine { continuation ->
    Log.v(TAG, "openCameraNdkAsync: shouldStream ${shouldStream} shouldRepeat ${shouldRepeat}")
    var result = Intent().apply { putExtra(keys.attributionSource, attributionSource.toString()) }

    // Avoid blocking the main thread
    cameraExecutor.execute {
      nativeInit()

      if (!nativeHasCamera()) {
        continuation.resume(result.apply { putExtra(keys.noCamera, true) })
        return@execute
      }

      val openCameraResult = nativeOpenCamera()
      if (openCameraResult != 0) {
        Log.e(TAG, "Failed to open camera: ${openCameraResult}")
        continuation.resume(result.apply { putExtra(keys.error, openCameraResult) })
        return@execute
      }

      result.putExtra(keys.cameraOpenedNdk, true)
      if (shouldStream) {
        openCameraNdkStream(result, continuation, shouldRepeat)
      } else {
        continuation.resume(result)
      }
    }

    // Run cleanup sequentially after the above
    cameraExecutor.execute {
      nativeCleanup()
      if (continuation.isActive) {
        continuation.resume(result)
      }
    }
  }

  fun release() {
    handlerThread?.quitSafely()
  }

  private suspend fun openCamera1Stream(
      camera: Camera,
      shouldRepeat: Boolean,
      result: Intent
  ): Intent = suspendCancellableCoroutine { continuation ->
    val params = camera.getParameters()
    val previewSize = params.getSupportedPreviewSizes()[0]
    params.setPreviewSize(previewSize.width, previewSize.height)
    camera.setParameters(params)

    if (textureView?.surfaceTexture != null) {
      surfaceTexture = textureView.surfaceTexture
    }

    if (surfaceTexture != null) {
      captureCamera1(camera, result, continuation, shouldRepeat)
    } else if (textureView != null) {
      textureView.setSurfaceTextureListener(
          object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
              surfaceTexture.let {
                this@CameraOpener.surfaceTexture = it
                captureCamera1(camera, result, continuation, shouldRepeat)
              }
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
              Log.v(TAG, "openCamera1Stream/onSurfaceTextureDestroyed")
              return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
          })
    }
  }

  private fun captureCamera1(
      camera: Camera,
      result: Intent,
      continuation: CancellableContinuation<Intent>,
      shouldRepeat: Boolean,
  ) {
    camera.setPreviewTexture(surfaceTexture)

    val cleanup: () -> Unit = {
      Log.v(TAG, "captureCamera1/cleanup")
      continuation.tryOrResume(keys, result, "captureCamera1/cleanup") {
        camera.stopPreview()
        camera.setPreviewTexture(null)
        camera.release()
      }
    }

    camera.setErrorCallback(
        object : Camera.ErrorCallback {
          override fun onError(error: Int, camera: Camera) {
            Log.v(TAG, "captureCamera1/onError: " + error)

            try {
              camera.release()
            } catch (e: Exception) {
              Log.e(TAG, "captureCamera1/onError: Received exception: ${e.exceptionString}")
              result.putException(keys, e)
            }

            if (continuation.isActive) { // continuation may already have been resumed
              continuation.resume(result.apply { putExtra(keys.error, error) })
            }
          }
        })

    camera.setPreviewCallback(
        object : Camera.PreviewCallback {
          private var firstCaptureCompleted = false

          override fun onPreviewFrame(data: ByteArray, camera: Camera) {
            if (!firstCaptureCompleted) {
              Log.v(TAG, "Camera.PreviewCallback.onPreviewFrame() (first)")
              onStopRepeating = {
                Log.v(TAG, "onStopRepeating")
                cleanup()

                if (continuation.isActive) {
                  continuation.resume(result.apply { putExtra(keys.stoppedRepeating, true) })
                }
              }

              signalStreamOpened(true, result)
              firstCaptureCompleted = true

              if (!shouldRepeat) {
                cleanup()
                continuation.resume(result)
              }
            } else {
              Log.v(TAG, "Camera.PreviewCallback.onPreviewFrame()")
            }
          }
        })

    camera.startPreview()
  }

  private fun openCamera2Stream(
      cameraDevice: CameraDevice,
      result: Intent,
      continuation: CancellableContinuation<Intent>,
      shouldRepeat: Boolean
  ) {
    handlerThread = HandlerThread("${context.packageName}.CAPTURE").apply { start() }
    val captureHandler = Handler(handlerThread!!.getLooper())

    val characteristics = cameraManager.getCameraCharacteristics(cameraDevice.id)
    val config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
    val jpegSize = config.getOutputSizes(ImageFormat.JPEG)[0]
    val imageReader =
        ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 1).apply {
          setOnImageAvailableListener(
              object : ImageReader.OnImageAvailableListener {
                override fun onImageAvailable(imageReader: ImageReader) {
                  imageReader.acquireNextImage().close()
                }
              },
              captureHandler)
        }
    val outputConfiguration = OutputConfiguration(imageReader.surface)
    val sessionConfiguration =
        SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            listOf(outputConfiguration),
            cameraExecutor,
            object : CameraCaptureSession.StateCallback() {
              override fun onConfigured(session: CameraCaptureSession) {
                Log.v(TAG, "CaptureCaptureSession.StateCallback.onConfigured()")
                continuation.tryOrResume(keys, result, "openCamera2Stream/onConfigured") {
                  captureCamera2(
                      cameraDevice,
                      session,
                      result,
                      continuation,
                      imageReader.surface,
                      shouldRepeat,
                      captureHandler)
                }
              }

              override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.v(TAG, "CaptureCaptureSession.StateCallback.onConfigureFailed()")
                session.close()
                cameraDevice.close()
                continuation.resume(signalStreamOpened(false, result))
              }

              override fun onReady(session: CameraCaptureSession) {
                Log.v(TAG, "CaptureCaptureSession.StateCallback.onReady()")
              }
            })
    cameraDevice.createCaptureSession(sessionConfiguration)
  }

  private fun captureCamera2(
      cameraDevice: CameraDevice,
      session: CameraCaptureSession,
      result: Intent,
      continuation: CancellableContinuation<Intent>,
      target: Surface,
      shouldRepeat: Boolean,
      captureHandler: Handler
  ) {
    val cleanup: () -> Unit = {
      Log.v(TAG, "captureCamera2/cleanup")
      continuation.tryOrResume(keys, result, "captureCamera2/cleanup") {
        session.stopRepeating()
        session.close()
        cameraDevice.close()
      }
    }

    val captureRequest =
        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).let {
          it.addTarget(target)
          it.build()
        }

    val captureCallback =
        object : CameraCaptureSession.CaptureCallback() {
          private var firstCaptureCompleted = false

          override fun onCaptureCompleted(
              session: CameraCaptureSession,
              request: CaptureRequest,
              captureResult: TotalCaptureResult,
          ) {
            if (!firstCaptureCompleted) {
              Log.v(TAG, "CameraCaptureSession.CaptureCallback.onCaptureCompleted() (first)")
              onStopRepeating = {
                Log.v(TAG, "onStopRepeating")
                cleanup()

                if (continuation.isActive) {
                  continuation.resume(result.apply { putExtra(keys.stoppedRepeating, true) })
                }
              }

              signalStreamOpened(true, result)
              firstCaptureCompleted = true
            } else {
              Log.v(TAG, "CameraCaptureSession.CaptureCallback.onCaptureCompleted()")
            }

            if (!shouldRepeat) {
              cleanup()
              continuation.resume(result)
            }
          }

          override fun onCaptureFailed(
              session: CameraCaptureSession,
              request: CaptureRequest,
              failure: CaptureFailure
          ) {
            Log.v(TAG, "CameraCaptureSession.CaptureCallback.onCaptureFailed()")
          }
        }

    if (shouldRepeat) {
      Log.v(TAG, "captureCamera2: setRepeatingRequest")
      session.setRepeatingRequest(captureRequest, captureCallback, captureHandler)
    } else {
      Log.v(TAG, "captureCamera2: capture")
      session.capture(captureRequest, captureCallback, captureHandler)
    }
  }

  private fun openCameraNdkStream(
      result: Intent,
      continuation: CancellableContinuation<Intent>,
      shouldRepeat: Boolean
  ) {
    Log.v(TAG, "openCameraNdkStream: shouldRepeat ${shouldRepeat}")
    val openCameraStreamResult = nativeOpenCameraStream(shouldRepeat)
    if (openCameraStreamResult != 0) {
      signalStreamOpened(false, result)
      Log.e(TAG, "Failed to open camera stream: ${openCameraStreamResult}")
      continuation.resume(result.apply { putExtra(keys.error, openCameraStreamResult) })
      return
    }

    signalStreamOpened(true, result)
    if (shouldRepeat) {
      onStopRepeating = {
        Log.v(TAG, "onStopRepeating")
        result.putExtra(keys.stoppedRepeating, true)
        nativeStopRepeating()
      }

      val stopRepeatingResult = nativeWaitStopRepeating()
      result.putExtra(keys.error, stopRepeatingResult)
    }
    continuation.resume(result)
  }

  private fun signalStreamOpened(streamOpened: Boolean, result: Intent): Intent {
    val streamOpenedIntent =
        Intent(keys.streamOpened).apply { putExtra(keys.streamOpened, streamOpened) }
    streamOpenedIntent.setPackage("android.security.cts")
    context.sendBroadcast(streamOpenedIntent)
    return result.apply { putExtra(keys.streamOpened, streamOpened) }
  }

  protected external fun nativeInit()

  protected external fun nativeCleanup()

  protected external fun nativeHasCamera(): Boolean

  protected external fun nativeOpenCamera(): Int

  protected external fun nativeOpenCameraStream(shouldRepeat: Boolean): Int

  protected external fun nativeWaitStopRepeating(): Int

  protected external fun nativeStopRepeating(): Int

  private companion object {
    val TAG = CameraOpener::class.java.simpleName

    const val CAMERA_PROXY_APP_PACKAGE_NAME = "android.security.cts.camera.proxy"
    const val CAMERA_PROXY_ACTIVITY = "$CAMERA_PROXY_APP_PACKAGE_NAME.CameraProxyActivity"
  }
}
