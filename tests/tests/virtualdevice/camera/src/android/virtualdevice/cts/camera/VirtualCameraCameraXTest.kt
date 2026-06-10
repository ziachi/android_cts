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

package android.virtualdevice.cts.camera

import android.Manifest
import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceParams
import android.companion.virtual.camera.VirtualCamera
import android.companion.virtual.camera.VirtualCameraCallback
import android.companion.virtual.camera.VirtualCameraConfig
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.view.Surface
import android.virtualdevice.cts.camera.util.VirtualCameraUtils
import android.virtualdevice.cts.camera.util.VirtualCameraUtils.BACK_CAMERA_ID
import android.virtualdevice.cts.camera.util.VirtualCameraUtils.INFO_DEVICE_ID
import android.virtualdevice.cts.camera.util.VirtualCameraUtils.assertImagesSimilar
import android.virtualdevice.cts.camera.util.VirtualCameraUtils.loadBitmapFromRaw
import android.virtualdevice.cts.common.VirtualDeviceRule
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.FLASH_MODE_OFF
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.RetryPolicy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import junit.framework.Assert.fail
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val VIRTUAL_CAMERA_WIDTH = 460
private const val VIRTUAL_CAMERA_HEIGHT = 260

@RunWith(AndroidJUnit4::class)
class VirtualCameraCameraXTest {

    private var activity: AppCompatActivity? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var virtualDevice: VirtualDeviceManager.VirtualDevice? = null
    private var vdContext: Context? = null

    private val sameThreadExecutor: (Runnable) -> Unit = Runnable::run

    @get:Rule
    val virtualDeviceRule: VirtualDeviceRule = VirtualDeviceRule.withAdditionalPermissions(
        Manifest.permission.GRANT_RUNTIME_PERMISSIONS
    )

    @Before
    fun setUp() {
        val deviceParams = VirtualDeviceParams.Builder()
            .setDevicePolicy(
                VirtualDeviceParams.POLICY_TYPE_CAMERA,
                VirtualDeviceParams.DEVICE_POLICY_CUSTOM
            )
            .build()

        val virtualDevice = virtualDeviceRule.createManagedVirtualDevice(deviceParams)
        this.virtualDevice = virtualDevice
        VirtualCameraUtils.grantCameraPermission(virtualDevice.deviceId)

        val virtualDisplay = virtualDeviceRule.createManagedVirtualDisplay(
            virtualDevice,
            VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder()
        )!!

        val activity = virtualDeviceRule.startActivityOnDisplaySync(
            virtualDisplay,
            AppCompatActivity::class.java
        )
        this.activity = activity

        val vdContext = activity.createDeviceContext(virtualDevice.deviceId)
        this.vdContext = vdContext
    }

    private fun initCameraXProvider(context: Context) {
        val cameraXConfig = CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setCameraProviderInitRetryPolicy(RetryPolicy.NEVER)
            .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
            .build()
        ProcessCameraProvider.configureInstance(cameraXConfig)
        cameraProvider = ProcessCameraProvider.getInstance(context).get(10, TimeUnit.SECONDS)!!
    }

    @After
    fun tearDown() {
        runBlocking {
            withContext(Dispatchers.Main) {
                activity?.finish()
                cameraProvider?.unbindAll()

                // If we don't shutdown the camera provider, the metadata are
                // cached and the device id is stall
                cameraProvider?.shutdownAsync()?.await()
            }
        }
    }

    @Test
    fun virtualDeviceContext_takePicture() {
        val golden = loadBitmapFromRaw(R.raw.golden_camerax_virtual_camera)

        createVirtualCamera(
            lensFacing = CameraMetadata.LENS_FACING_BACK
        ) { surface ->
            val canvas: Canvas = surface.lockCanvas(null)
            canvas.drawBitmap(golden, 0f, 0f, null)
            surface.unlockCanvasAndPost(canvas)
        }

        initCameraXProvider(vdContext!!)

        val imageCapture = ImageCapture.Builder()
            .setFlashMode(FLASH_MODE_OFF)
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val imageFile = takeAndSavePicture(cameraSelector, imageCapture)
        assertThat(imageFile.exists()).isTrue()
        val bitmap = BitmapFactory.decodeFile(imageFile.path)

        assertImagesSimilar(
            bitmap,
            golden,
            "camerax_virtual_camera",
            10.0
        )
    }

    @Test
    fun virtualDeviceContext_availableCameraInfos_returnsVirtualCameras() {
        createVirtualCamera(
            lensFacing = CameraMetadata.LENS_FACING_BACK
        )
        initCameraXProvider(vdContext!!)
        runBlockingWithTimeout {
            withContext(Dispatchers.Main) {
                cameraProvider!!.bindToLifecycle(
                    activity!!,
                    CameraSelector.DEFAULT_BACK_CAMERA
                )
            }
        }

        val camera2Infos = cameraProvider!!.availableCameraInfos
            .map(Camera2CameraInfo::from)

        val ids: List<String> = camera2Infos
            .map { it.cameraId }

        val cameraManager = vdContext!!.getSystemService(CameraManager::class.java)
        val cameraIdList: Array<String> =
            cameraManager!!.cameraIdList
        assertThat(ids).containsExactlyElementsIn(cameraIdList.asList())
        assertThat(ids).containsExactly(BACK_CAMERA_ID)
        assertThat(
            cameraManager.getCameraCharacteristics(BACK_CAMERA_ID)
                .get(INFO_DEVICE_ID)
        ).isEqualTo(virtualDevice!!.deviceId)
        assertThat(camera2Infos[0].getCameraCharacteristic(INFO_DEVICE_ID))
            .isEqualTo(virtualDevice!!.deviceId)
    }

    private fun takeAndSavePicture(
        cameraSelector: CameraSelector,
        imageCapture: ImageCapture
    ): File {
        val imageFile = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "test_image.jpg"
        )
        runBlockingWithTimeout {
            withContext(Dispatchers.Main) {
                cameraProvider!!.bindToLifecycle(
                    activity!!,
                    cameraSelector,
                    imageCapture
                )
            }
            suspendCoroutine { cont ->
                imageCapture.takePicture(
                    OutputFileOptions.Builder(imageFile).build(),
                    ContextCompat.getMainExecutor(vdContext!!),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(
                            outputFileResults: ImageCapture.OutputFileResults
                        ) {
                            cont.resumeWith(Result.success(outputFileResults))
                        }

                        override fun onError(exception: ImageCaptureException) {
                            fail(exception.stackTrace.joinToString("\n") { it.toString() })
                        }
                    }
                )
            }
        }
        return imageFile
    }

    private fun createVirtualCamera(
        inputWidth: Int = VIRTUAL_CAMERA_WIDTH,
        inputHeight: Int = VIRTUAL_CAMERA_HEIGHT,
        inputFormat: Int = ImageFormat.YUV_420_888,
        lensFacing: Int = CameraMetadata.LENS_FACING_BACK,
        surfaceWriter: (Surface) -> Unit = {}
    ): VirtualCamera? {
        val cameraCallBack = object : VirtualCameraCallback {

            private var inputSurface: Surface? = null

            override fun onStreamConfigured(
                streamId: Int,
                surface: Surface,
                width: Int,
                height: Int,
                format: Int
            ) {
                inputSurface = surface
                surfaceWriter(inputSurface!!)
            }

            override fun onStreamClosed(streamId: Int) = Unit
        }
        val config = VirtualCameraConfig.Builder("CameraXVirtualCamera")
            .addStreamConfig(inputWidth, inputHeight, inputFormat, 30)
            .setVirtualCameraCallback(sameThreadExecutor, cameraCallBack)
            .setSensorOrientation(VirtualCameraConfig.SENSOR_ORIENTATION_0)
            .setLensFacing(lensFacing)
            .build()
        try {
            return virtualDevice!!.createVirtualCamera(config)
        } catch (e: UnsupportedOperationException) {
            Assume.assumeNoException("Virtual camera is not available on this device", e)
            return null
        }
    }
}

private fun <T> runBlockingWithTimeout(block: suspend CoroutineScope.() -> T) {
    var exception: Throwable? = null
    runBlocking {
        try {
            withTimeout(2000) {
                block()
            }
        } catch (ex: kotlinx.coroutines.TimeoutCancellationException) {
            exception = ex
        }
    }
    // Rethrow from outside the coroutine to get the stacktrace
    exception?.let { throw TimeoutException() }
}
