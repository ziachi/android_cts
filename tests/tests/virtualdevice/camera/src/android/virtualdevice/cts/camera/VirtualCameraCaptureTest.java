/*
 * Copyright 2024 The Android Open Source Project
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

package android.virtualdevice.cts.camera;

import static android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA;
import static android.graphics.ImageFormat.JPEG;
import static android.graphics.ImageFormat.YUV_420_888;
import static android.virtualdevice.cts.camera.util.ImageSubject.assertThat;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.jpegImageToBitmap;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.loadBitmapFromRaw;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.paintSurface;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.toFormat;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageWriter;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.Surface;
import android.virtualdevice.cts.camera.util.ImageSubject;
import android.virtualdevice.cts.camera.util.VirtualCameraCaptureHelper;
import android.virtualdevice.cts.camera.util.VirtualCameraCaptureHelper.CaptureConfiguration;
import android.virtualdevice.cts.camera.util.VirtualCameraUtils;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.google.common.collect.Range;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@RunWith(JUnitParamsRunner.class)
public class VirtualCameraCaptureTest {
    private static final int SECOND_TO_NANOS = 1_000_000_000;
    private static final int MILLISECOND_TO_NANOS = 1_000_000;

    private final VirtualCameraCaptureHelper mCaptureHelper = new VirtualCameraCaptureHelper();

    @Rule
    public VirtualDeviceRule mRule =
            VirtualDeviceRule.withAdditionalPermissions(GRANT_RUNTIME_PERMISSIONS);

    @Before
    public void setUp() {
        VirtualDeviceManager.VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                        .build());
        Context virtualDeviceContext = getApplicationContext().createDeviceContext(
                virtualDevice.getDeviceId());
        mCaptureHelper.setUp(virtualDevice, virtualDeviceContext);
        VirtualCameraUtils.grantCameraPermission(virtualDevice.getDeviceId());
    }

    @After
    public void tearDown() {
        mCaptureHelper.tearDown();
    }

    @Test
    public void captureImage_inputBufferDoesNotBlock() {
        mCaptureHelper.createVirtualCamera();
        CaptureConfiguration captureConfiguration = new CaptureConfiguration()
                .setInputSurfaceConsumer((Surface surface) -> {
                    // Submit 100 RED-colored buffers to virtual camera input surface.
                    // This should not block, the buffers should be consumed immediately
                    // although there are no incoming capture requests.
                    for (int i = 0; i < 100; i++) {
                        paintSurface(surface, Color.RED);
                    }
                    // Submit green buffer, expect this one will be visible.
                    paintSurface(surface, Color.GREEN);
                });
        Image image = mCaptureHelper.captureImages(captureConfiguration);
        assertThat(image.getFormat()).isEqualTo(YUV_420_888);
        assertThat(image.getWidth()).isEqualTo(VirtualCameraCaptureHelper.CAMERA_WIDTH);
        assertThat(image.getHeight()).isEqualTo(VirtualCameraCaptureHelper.CAMERA_HEIGHT);
        assertThat(image).hasOnlyColor(Color.GREEN);
    }

    @Parameters(method = "getOutputPixelFormats")
    @TestCaseName("{method}_{params}")
    @Test
    public void captureImage_withInput_succeeds(String format) {
        int outputPixelFormat = toFormat(format);

        mCaptureHelper.createVirtualCamera();
        CaptureConfiguration captureConfiguration = new CaptureConfiguration()
                .setOutputFormat(outputPixelFormat)
                .setInputSurfaceConsumer(VirtualCameraUtils::paintSurfaceRed);
        Image image = mCaptureHelper.captureImages(captureConfiguration);
        assertThat(image.getFormat()).isEqualTo(outputPixelFormat);
        assertThat(image.getWidth()).isEqualTo(VirtualCameraCaptureHelper.CAMERA_WIDTH);
        assertThat(image.getHeight()).isEqualTo(VirtualCameraCaptureHelper.CAMERA_HEIGHT);
        assertThat(image).hasOnlyColor(Color.RED);
    }

    @Parameters(method = "getOutputPixelFormats")
    @TestCaseName("{method}_{params}")
    @Test
    public void captureImage_withoutInput_fails(String format) {
        int outputPixelFormat = toFormat(format);
        mCaptureHelper.createVirtualCamera();

        // Take a fist image, but don't write anything on the input surface.
        // We should have a failed capture after the time expires.
        CaptureConfiguration config =
                new CaptureConfiguration()
                        .setOutputFormat(outputPixelFormat)
                        .setVerifyCaptureComplete(false)
                        .setFailOnCaptureError(false);

        Image image = mCaptureHelper.captureImages(config);
        mCaptureHelper.verifyCaptureFailed();
        ImageSubject.assertThat(image).isNull();
    }

    @Test
    public void capture_withZeroOutput_doesNotTriggerOnStreamConfigured() throws Exception {
        mCaptureHelper.createVirtualCamera();

        CameraDevice cameraDevice = mCaptureHelper.getOrOpenCameraDevice();
        cameraDevice.createCaptureSession(
                new SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        Collections.emptyList(),
                        ApplicationProvider.getApplicationContext().getMainExecutor(),
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            }
                        }));

        verify(mCaptureHelper.getVirtualCameraCallback(), after(2000L).never())
                .onStreamConfigured(anyInt(), any(), anyInt(), anyInt(), anyInt());
    }

    @Test
    public void captureImage_blocksUntilFirstFrame() {
        int width = 460;
        int height = 260;
        mCaptureHelper.createVirtualCamera(width, height, YUV_420_888, 30);

        // Take a fist image, but don't write anything on the input surface.
        // We should have a failed capture after the time expires.
        CaptureConfiguration config = new CaptureConfiguration()
                .setVerifyCaptureComplete(false)
                .setFailOnCaptureError(false);


        mCaptureHelper.captureImages(config);
        mCaptureHelper.verifyCaptureFailed();

        // Now capture again, but write something on the surface. The capture must be
        // successful.
        config
                .setVerifyCaptureComplete(true)
                .setFailOnCaptureError(true)
                .setInputSurfaceConsumer((surface) -> {
                    Canvas canvas = surface.lockCanvas(null);
                    canvas.drawColor(Color.RED);
                    surface.unlockCanvasAndPost(canvas);
                });
        Image image = mCaptureHelper.captureImages(config);

        assertThat(image).isNotNull();
    }

    @Parameters(method = "getOutputPixelFormats")
    @TestCaseName("{method}_{params}")
    @Test
    public void captureDownscaledImage_succeeds(String format) {
        int outputPixelFormat = toFormat(format);
        int halfWidth = VirtualCameraCaptureHelper.CAMERA_WIDTH / 2;
        int halfHeight = VirtualCameraCaptureHelper.CAMERA_HEIGHT / 2;

        mCaptureHelper.createVirtualCamera();
        CaptureConfiguration config = new CaptureConfiguration()
                .setHeight(halfHeight)
                .setWidth(halfWidth)
                .setOutputFormat(outputPixelFormat)
                .setInputSurfaceConsumer(VirtualCameraUtils::paintSurfaceRed);
        Image image = mCaptureHelper.captureImages(config);
        assertThat(image.getFormat()).isEqualTo(outputPixelFormat);
        assertThat(image.getWidth()).isEqualTo(halfWidth);
        assertThat(image.getHeight()).isEqualTo(halfHeight);
        assertThat(image).hasOnlyColor(Color.RED);
    }

    /**
     * Test that when the input of virtual camera comes from an ImageReader, the output of virtual
     * camera is similar to the output of the image reader.
     */
    @Test
    public void captureImage_withMediaCodec_hasOutputSimilarToImageReader() throws Exception {
        // This must match the test video size to avoid down scaling the bitmap for the comparison
        // and limit at best the diff value.
        int width = 1280;
        int height = 720;
        double maxImageDiff = 20;

        mCaptureHelper.createVirtualCamera(width, height, YUV_420_888);
        CaptureConfiguration captureConfiguration = new CaptureConfiguration()
                .setOutputFormat(JPEG)
                .setWidth(width)
                .setHeight(height);

        VirtualCameraUtils.VideoRenderer videoRenderer =
                new VirtualCameraUtils.VideoRenderer(R.raw.test_video);
        captureConfiguration.setInputSurfaceConsumer(videoRenderer);
        Image imageFromCamera = mCaptureHelper.captureImages(captureConfiguration);
        Bitmap bitmapFromVideo = videoRenderer.getGoldenBitmap();
        Bitmap bitmapFromCamera = jpegImageToBitmap(imageFromCamera);
        VirtualCameraUtils.assertImagesSimilar(
                bitmapFromCamera, bitmapFromVideo, "renderFromMediaCodec", maxImageDiff);
    }

    /**
     * Test that when the input of virtual camera comes from an ImageReader, the output of virtual
     * camera is similar to a golden file generated on a real device.
     */
    @Test
    public void captureImage_withMediaCodec_hasOutputSimilarToGolden() throws Exception {
        int width = 460;
        int height = 260;
        double maxImageDiff = 20;
        mCaptureHelper.createVirtualCamera(width, height, YUV_420_888);
        CaptureConfiguration captureConfiguration = new CaptureConfiguration()
                .setOutputFormat(JPEG)
                .setWidth(width)
                .setHeight(height)
                .setInputSurfaceConsumer(new VirtualCameraUtils.VideoRenderer(
                        R.raw.test_video));
        Image imageFromCamera = mCaptureHelper.captureImages(captureConfiguration);
        Bitmap bitmapFromCamera = jpegImageToBitmap(imageFromCamera);
        Bitmap golden = loadBitmapFromRaw(R.raw.golden_test_video);
        VirtualCameraUtils.assertImagesSimilar(
                bitmapFromCamera,
                golden,
                "renderFromMediaCodec_golden_from_pixel",
                maxImageDiff);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_CAMERA_TIMESTAMP_FROM_SURFACE)
    public void captureImage_withoutCustomTimestamp_withImageWriter() {
        int width = 460;
        int height = 260;
        long renderedTimestamp = 1;

        mCaptureHelper.createVirtualCamera(width, height, YUV_420_888);
        CaptureConfiguration captureConfiguration = new CaptureConfiguration()
                .setOutputFormat(YUV_420_888)
                .setWidth(width)
                .setHeight(height)
                .setInputSurfaceConsumer(surface -> {
                    ImageWriter imageWriter = ImageWriter.newInstance(surface, 1,
                            YUV_420_888);
                    Image image = imageWriter.dequeueInputImage();
                    image.setTimestamp(renderedTimestamp);
                    imageWriter.queueInputImage(image);
                    imageWriter.close();
                });

        Image imageFromCamera = mCaptureHelper.captureImages(captureConfiguration);
        Long captureTimestamp = mCaptureHelper.getLastResult().get(
                TotalCaptureResult.SENSOR_TIMESTAMP);

        // Check that the provided timestamp was not written to the image
        assertThat(imageFromCamera.getTimestamp()).isNotEqualTo(renderedTimestamp);

        // Check that the capture result has a timestamp greater than 10 seconds.
        // This basically checks that the timestamp the actual capture time, was not
        // computed from our provided seed timestamp.
        assertThat(captureTimestamp).isGreaterThan(TimeUnit.SECONDS.toNanos(10));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_TIMESTAMP_FROM_SURFACE)
    public void captureImage_withCustomTimestamp_withImageWriter() {
        int width = 460;
        int height = 260;
        long renderedTimestamp = 123456L;

        mCaptureHelper.createVirtualCamera(width, height, YUV_420_888);
        CaptureConfiguration captureConfiguration = new CaptureConfiguration()
                .setOutputFormat(YUV_420_888)
                .setWidth(width)
                .setHeight(height)
                .setInputSurfaceConsumer(surface -> {
                    ImageWriter imageWriter = ImageWriter.newInstance(surface, 1, YUV_420_888);
                    Image image = imageWriter.dequeueInputImage();
                    image.setTimestamp(renderedTimestamp);
                    imageWriter.queueInputImage(image);
                    imageWriter.close();
                });
        Image imageFromCamera = mCaptureHelper.captureImages(captureConfiguration);
        Long captureTimestamp = mCaptureHelper.getLastResult().get(
                TotalCaptureResult.SENSOR_TIMESTAMP);
        assertThat(imageFromCamera.getTimestamp()).isEqualTo(renderedTimestamp);
        assertThat(captureTimestamp).isEqualTo(renderedTimestamp);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_TIMESTAMP_FROM_SURFACE)
    public void captureMultipleImages_withCustomTimestamp_withImageWriter() {
        int width = 460;
        int height = 260;
        long renderedTimestampNanos = 123456L;
        int imageCount = 10;
        long expectedTimeNanos = (long) (Math.pow(10, 9) / VirtualCameraCaptureHelper.CAMERA_MAX_FPS
                * imageCount
                + renderedTimestampNanos);
        int toleranceNanos = 50_000_000; // 50 millis

        mCaptureHelper.createVirtualCamera(width, height, YUV_420_888);
        CaptureConfiguration captureConfiguration = new CaptureConfiguration()
                .setOutputFormat(YUV_420_888)
                .setWidth(width)
                .setHeight(height)
                .setImageCount(imageCount)
                .setInputSurfaceConsumer(surface -> {
                    ImageWriter imageWriter = ImageWriter.newInstance(surface, 1,
                            YUV_420_888);
                    Image image = imageWriter.dequeueInputImage();
                    image.setTimestamp(renderedTimestampNanos);
                    image.getPlanes()[0].getBuffer().putInt(1);
                    imageWriter.queueInputImage(image);
                    imageWriter.close();
                });
        Image imageFromCamera = mCaptureHelper.captureImages(captureConfiguration);
        Long captureTimestamp = mCaptureHelper.getLastResult().get(
                TotalCaptureResult.SENSOR_TIMESTAMP);
        assertThat(imageFromCamera.getTimestamp()).isWithin(toleranceNanos).of(
                expectedTimeNanos);
        assertThat(captureTimestamp).isWithin(toleranceNanos).of(expectedTimeNanos);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_TIMESTAMP_FROM_SURFACE)
    public void inputRate_LowerThanMaxFps_allFulfilled() {
        // Render slower than the max fps to be sure that no input frame will be skipped
        android.util.Range<Integer> requestFPSRange = android.util.Range.create(1,
                VirtualCameraCaptureHelper.CAMERA_MAX_FPS);
        testRenderingRate(requestFPSRange, VirtualCameraCaptureHelper.CAMERA_MAX_FPS - 10);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_TIMESTAMP_FROM_SURFACE)
    @Ignore("b/406965817")
    public void inputRate_HigherThanMaxFps_allFulfilled() {
        // We render faster than the max fps to be sure that no input frame will be skipped
        android.util.Range<Integer> requestFPSRange = android.util.Range.create(1,
                VirtualCameraCaptureHelper.CAMERA_MAX_FPS);
        testRenderingRate(requestFPSRange, VirtualCameraCaptureHelper.CAMERA_MAX_FPS + 10);
    }

    private void testRenderingRate(android.util.Range<Integer> requestFPSRange, int inputFps) {
        long initialRenderedTimestampNanos = 1000;
        int width = 460;
        int height = 260;
        int imageCount = 25;

        int outputPeriodMillis = Math.round(1000f / Math.min(inputFps, requestFPSRange.getUpper()));
        // Time it takes to get all the captures
        long outputCaptureLastTimestamp = initialRenderedTimestampNanos
                + (long) (imageCount) * outputPeriodMillis * MILLISECOND_TO_NANOS;

        // The number of frame we allow to be out of sync between the input and output
        int toleranceFrameNumber = 5;
        int toleranceNanos = toleranceFrameNumber * outputPeriodMillis
                * MILLISECOND_TO_NANOS; // 1 Frame tolerance

        mCaptureHelper.createVirtualCamera(width, height, YUV_420_888);

        FixedRateImageWriter fixedRateImageWriter = new FixedRateImageWriter(
                initialRenderedTimestampNanos, inputFps);

        CaptureConfiguration config = new CaptureConfiguration()
                .setWidth(width)
                .setHeight(height)
                .setImageCount(imageCount)
                .setVerifyCaptureComplete(true)
                .setRequestBuilderModifier((request) -> request.set(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, requestFPSRange))
                .setInputSurfaceConsumer(fixedRateImageWriter);
        mCaptureHelper.captureImages(config).close();
        List<TotalCaptureResult> captureResults = mCaptureHelper.getCaptureResults();
        assertThat(captureResults).hasSize(imageCount);

        double averageCaptureTime = captureResults.stream().mapToLong(
                (current) -> current.get(
                        TotalCaptureResult.SENSOR_TIMESTAMP)).average().orElse(0);

        assertWithMessage("Request took more time than requested min FPS in average").that(
                averageCaptureTime).isLessThan(
                1.0 * SECOND_TO_NANOS / requestFPSRange.getLower());
        assertWithMessage("Request took less time than requested max FPS ").that(
                averageCaptureTime).isGreaterThan(
                1.0 * SECOND_TO_NANOS / requestFPSRange.getUpper());

        Long lastCaptureTimestamp = mCaptureHelper.getLastResult().get(
                TotalCaptureResult.SENSOR_TIMESTAMP);

        assertThat(lastCaptureTimestamp).isWithin(toleranceNanos).of(
                outputCaptureLastTimestamp);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_TIMESTAMP_FROM_SURFACE)
    public void captureMultipleImages_withCustomTimestamp_withMediaCodec() {
        int width = 1280;
        int height = 720;
        long renderTimestamp = 100L;
        int fps = 5; // Low FPS to keep up with our codec

        CaptureConfiguration captureConfiguration = new CaptureConfiguration()
                .setImageCount(3)
                .setOutputFormat(YUV_420_888)
                .setWidth(width)
                .setHeight(height);
        mCaptureHelper.createVirtualCamera(width, height, YUV_420_888, fps);
        try (SteadyTimestampCodec steadyTimestampCodec = new SteadyTimestampCodec(width, height,
                renderTimestamp)) {
            captureConfiguration.setInputSurfaceConsumer(steadyTimestampCodec::setSurfaceAndStart);
            long startTime = SystemClock.uptimeMillis();
            Image image = mCaptureHelper.captureImages(captureConfiguration);
            long endTimestamp = renderTimestamp + (SystemClock.uptimeMillis() - startTime);
            Range<Long> timestampRange = Range.closed(renderTimestamp, endTimestamp);
            assertThat(mCaptureHelper.getLastResult()
                    .get(CaptureResult.SENSOR_TIMESTAMP)).isIn(timestampRange);
            assertThat(image.getTimestamp()).isIn(timestampRange);
        }
    }

    @SuppressWarnings("unused") // Parameter for parametrized tests
    private static String[] getOutputPixelFormats() {
        return new String[]{"YUV_420_888", "JPEG"};
    }
}
