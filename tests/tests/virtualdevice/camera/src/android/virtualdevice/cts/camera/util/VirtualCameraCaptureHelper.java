/*
 * Copyright 2025 The Android Open Source Project
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

package android.virtualdevice.cts.camera.util;

import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_0;
import static android.graphics.ImageFormat.YUV_420_888;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.createVirtualCameraConfig;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNoException;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.camera.VirtualCamera;
import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.truth.Truth;

import org.junit.Assert;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Helper class for testing capture scenarios with a virtual camera.
 */
public class VirtualCameraCaptureHelper {
    public static final long TIMEOUT_MILLIS = 2000L;
    public static final String CAMERA_NAME = "Virtual camera";
    public static final int CAMERA_WIDTH = 640;
    public static final int CAMERA_HEIGHT = 480;
    public static final int CAMERA_INPUT_FORMAT = PixelFormat.RGBA_8888;
    public static final int CAMERA_MAX_FPS = 30;

    private static final long FAILURE_TIMEOUT = 10000L;
    private static final int IMAGE_READER_MAX_IMAGES = 2;

    private final Handler mImageReaderHandler = VirtualCameraUtils.createHandler(
            "image-reader-callback");
    private final Executor mCameraExecutor = Executors.newSingleThreadExecutor();
    @Mock
    private CameraDevice.StateCallback mCameraStateCallback;
    @Mock
    private CameraCaptureSession.StateCallback mSessionStateCallback;

    private TestCaptureCallback mCaptureCallback;
    @Mock
    private VirtualCameraCallback mVirtualCameraCallback;
    @Captor
    private ArgumentCaptor<CameraDevice> mCameraDeviceCaptor;
    @Captor
    private ArgumentCaptor<CameraCaptureSession> mCameraCaptureSessionCaptor;
    @Captor
    private ArgumentCaptor<Surface> mSurfaceCaptor;

    @Nullable
    private CameraManager mCameraManager = null;
    @Nullable
    private VirtualCamera mVirtualCamera = null;
    @Nullable
    private CameraDevice mCameraDevice = null;
    @Nullable
    private ImageReader mOutputReader = null;
    @Nullable
    private CameraCaptureSession mCaptureSession = null;
    @Nullable
    private VirtualDeviceManager.VirtualDevice mVirtualDevice = null;

    /**
     * Initialize the helper to work with the provided virtualDevice onto which the virtual camera
     * will be created and the context used to open the camera for capture.
     */
    public void setUp(@NonNull VirtualDeviceManager.VirtualDevice virtualDevice,
            @NonNull Context context) {
        mCameraManager = Objects.requireNonNull(context).getSystemService(CameraManager.class);
        mVirtualDevice = Objects.requireNonNull(virtualDevice);
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Clean up resources after the test has been run
     */
    public void tearDown() {
        Mockito.reset(mCameraStateCallback, mSessionStateCallback, mVirtualCameraCallback);
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mVirtualCamera != null) {
            mVirtualCamera.close();
            mVirtualCamera = null;
        }
        if (mOutputReader != null) {
            mOutputReader.close();
            mOutputReader = null;
        }
    }

    /**
     * Create a virtual camera with default values.
     */
    public void createVirtualCamera() {
        createVirtualCamera(VirtualCameraCaptureHelper.CAMERA_WIDTH,
                VirtualCameraCaptureHelper.CAMERA_HEIGHT,
                VirtualCameraCaptureHelper.CAMERA_INPUT_FORMAT);
    }

    /**
     * Create a virtual camera with the provided configuration
     *
     * @param inputWidth  width of the input of this virtual camera
     * @param inputHeight height of the input of this virtual camera
     * @param inputFormat format of the input of this virtual camera
     */
    public void createVirtualCamera(int inputWidth, int inputHeight, int inputFormat) {
        createVirtualCamera(inputWidth, inputHeight, inputFormat,
                VirtualCameraCaptureHelper.CAMERA_MAX_FPS);
    }

    /**
     * Create a virtual camera with the provided configuration
     *
     * @param inputWidth  width of the input of this virtual camera
     * @param inputHeight height of the input of this virtual camera
     * @param inputFormat format of the input of this virtual camera
     * @param fps         fps of the input of this virtual camera
     */
    public void createVirtualCamera(int inputWidth, int inputHeight, int inputFormat,
            int fps) {
        Objects.requireNonNull(mVirtualDevice,
                "mVirtualDevice must not be null when calling #createVirtualCamera()");
        VirtualCameraConfig config = createVirtualCameraConfig(inputWidth, inputHeight,
                inputFormat, fps, SENSOR_ORIENTATION_0, LENS_FACING_FRONT,
                VirtualCameraCaptureHelper.CAMERA_NAME, mCameraExecutor,
                mVirtualCameraCallback);
        try {
            mVirtualCamera = mVirtualDevice.createVirtualCamera(config);
        } catch (UnsupportedOperationException e) {
            assumeNoException("Virtual camera is not available on this device", e);
        }
    }

    /**
     * Capture images using the provided {@link CaptureConfiguration}
     * <p>
     * The camera device and session will be automatically created if needed.
     *
     * @return The latest captured image.
     */
    public Image captureImages(CaptureConfiguration config) {
        AtomicReference<Image> latestImageRef = new AtomicReference<>(null);

        mCaptureCallback = new TestCaptureCallback();
        mCaptureCallback.mFailOnFailedCapture = config.mFailOnCaptureError;

        try {
            ImageReader reader = getOrCreateOutputReader(config);
            CameraCaptureSession cameraCaptureSession = getOrCreateCaptureSession(reader);
            CameraDevice cameraDevice = cameraCaptureSession.getDevice();
            config.mInputSurfaceConsumer.accept(getInputSurface());

            CaptureRequest.Builder request = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW);
            config.mRequestBuilderModifier.accept(request);
            request.addTarget(reader.getSurface());

            CountDownLatch imageReaderLatch = new CountDownLatch(config.mImageCount);
            reader.setOnImageAvailableListener(imageReader -> {
                Image latestImage = latestImageRef.get();
                if (latestImage != null) {
                    latestImage.close();
                }
                latestImageRef.set(imageReader.acquireLatestImage());
                imageReaderLatch.countDown();
            }, mImageReaderHandler);

            for (int i = 0; i < config.mImageCount; i++) {
                cameraCaptureSession.captureSingleRequest(request.build(), mCameraExecutor,
                        mCaptureCallback);
            }

            if (!config.mVerifyCaptureComplete) {
                return reader.acquireLatestImage();
            }

            verifyCaptureComplete(config.mImageCount);
            Truth.assertWithMessage("Timeout waiting for image reader result").that(
                    imageReaderLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            Image image = latestImageRef.getAndSet(null);
            ImageSubject.assertThat(image).isNotNull();
            return image;
        } catch (CameraAccessException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            Image image = latestImageRef.getAndSet(null);
            if (image != null) {
                image.close();
            }
        }
    }

    /**
     * Returns the {@link CameraDevice} corresponding to the virtual camera.
     */
    public CameraDevice getOrOpenCameraDevice() {
        try {
            if (mCameraDevice != null) {
                return mCameraDevice;
            }
            Objects.requireNonNull(mVirtualCamera,
                    "mVirtualCamera must not be null when calling this method.");
            Objects.requireNonNull(mCameraManager,
                    "mCameraManager must not be null when calling this method.");
            mCameraManager.openCamera(getVirtualCameraId(mVirtualCamera), mCameraExecutor,
                    mCameraStateCallback);
            Mockito.verify(mCameraStateCallback, Mockito.timeout(TIMEOUT_MILLIS)).onOpened(
                    mCameraDeviceCaptor.capture());
            mCameraDevice = mCameraDeviceCaptor.getValue();
            return mCameraDevice;
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Surface getInputSurface() {
        Surface surface = mSurfaceCaptor.getValue();
        assertThat(surface.isValid()).isTrue();
        return surface;
    }

    private CameraCaptureSession getOrCreateCaptureSession(ImageReader reader)
            throws CameraAccessException {
        if (mCaptureSession != null) {
            return mCaptureSession;
        }
        CameraDevice cameraDevice = getOrOpenCameraDevice();
        OutputConfiguration outputConfiguration = new OutputConfiguration(reader.getSurface());
        cameraDevice.createCaptureSession(
                new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                        List.of(outputConfiguration), mCameraExecutor, mSessionStateCallback));
        Mockito.verify(mSessionStateCallback, Mockito.timeout(TIMEOUT_MILLIS)).onConfigured(
                mCameraCaptureSessionCaptor.capture());
        Mockito.verify(mVirtualCameraCallback,
                Mockito.timeout(TIMEOUT_MILLIS)).onStreamConfigured(ArgumentMatchers.anyInt(),
                mSurfaceCaptor.capture(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt());
        mCaptureSession = mCameraCaptureSessionCaptor.getValue();
        return mCaptureSession;
    }

    private ImageReader getOrCreateOutputReader(CaptureConfiguration config) {
        if (mOutputReader != null && (config.mOutputFormat != mOutputReader.getImageFormat()
                || config.mHeight != mOutputReader.getHeight()
                || mOutputReader.getWidth() != config.mWidth)) {
            mOutputReader.close();
            mOutputReader = null;
        }

        if (mOutputReader == null) {
            mOutputReader = ImageReader.newInstance(config.mWidth, config.mHeight,
                    config.mOutputFormat, IMAGE_READER_MAX_IMAGES);
        }
        return mOutputReader;
    }

    private void verifyCaptureComplete(int imageCount) {
        Mockito.verify(mVirtualCameraCallback,
                Mockito.timeout(TIMEOUT_MILLIS).atLeast(imageCount)).onProcessCaptureRequest(
                ArgumentMatchers.anyInt(), ArgumentMatchers.anyLong());
        mCaptureCallback.waitForCaptures(imageCount, TIMEOUT_MILLIS);
    }

    /**
     * Check that the capture has failed at least one time and never succeeded.
     */
    public void verifyCaptureFailed() {
        mCaptureCallback.waitForCaptures(1, FAILURE_TIMEOUT);
        assertThat(mCaptureCallback.getFailedCaptureCount()).isEqualTo(1);
        assertThat(mCaptureCallback.getCaptureResults()).isEmpty();
    }

    private static String getVirtualCameraId(VirtualCamera virtualCamera) {
        return switch (virtualCamera.getConfig().getLensFacing()) {
            case LENS_FACING_FRONT -> VirtualCameraUtils.FRONT_CAMERA_ID;
            case CameraMetadata.LENS_FACING_BACK -> VirtualCameraUtils.BACK_CAMERA_ID;
            default -> virtualCamera.getId();
        };
    }

    /**
     * Returns a {@link Mock} of {@link CaptureCallback}
     */
    public CaptureCallback getCaptureCallback() {
        return mCaptureCallback;
    }

    /**
     * Returns a {@link Mock} of {@link VirtualCameraCallback}
     */
    public VirtualCameraCallback getVirtualCameraCallback() {
        return mVirtualCameraCallback;
    }

    public List<TotalCaptureResult> getCaptureResults() {
        return mCaptureCallback.getCaptureResults();
    }

    public TotalCaptureResult getLastResult() {
        if (mCaptureCallback.getCaptureResults().isEmpty()) {
            return null;
        }
        return mCaptureCallback.getCaptureResults().getLast();
    }

    /**
     * Holds the configuration used for {@link #captureImages(CaptureConfiguration)}.
     * <p>
     * The default configuration can be used as is, all setters are optional.
     */
    public static final class CaptureConfiguration {

        private int mImageCount = 1;
        public boolean mFailOnCaptureError = true;
        private boolean mVerifyCaptureComplete = true;
        private Consumer<Surface> mInputSurfaceConsumer = (surface) -> {
        };
        private Consumer<CaptureRequest.Builder> mRequestBuilderModifier = (request) -> {
        };
        private int mWidth = CAMERA_WIDTH;
        private int mHeight = CAMERA_HEIGHT;
        private int mOutputFormat = YUV_420_888;

        /**
         * Set the number of image to capture
         * <p>
         * Default is 1.
         */
        public CaptureConfiguration setImageCount(int imageCount) {
            mImageCount = imageCount;
            return this;
        }

        /**
         * Set the whether the successful completion of the capture should be checked
         * <p>
         * Default is true.
         */
        public CaptureConfiguration setVerifyCaptureComplete(boolean verifyCaptureComplete) {
            mVerifyCaptureComplete = verifyCaptureComplete;
            return this;
        }

        /**
         * Set the whether we should fail as soon as we get a capture error.
         * <p>
         * Default is true.
         *
         * @see CaptureCallback#onCaptureFailed(CameraCaptureSession, CaptureRequest,
         * CaptureFailure)
         */
        public CaptureConfiguration setFailOnCaptureError(boolean failOnCaptureError) {
            mFailOnCaptureError = failOnCaptureError;
            return this;
        }

        /**
         * Set a consumer to write onto the input surface of the {@link VirtualCamera}
         * <p>
         * Default is no-op.
         */
        public CaptureConfiguration setInputSurfaceConsumer(
                Consumer<Surface> inputSurfaceConsumer) {
            mInputSurfaceConsumer = inputSurfaceConsumer;
            return this;
        }

        /**
         * Set the consumer that accepts a {@link CaptureRequest.Builder} and which can modify that
         * request.
         * <p>
         * Default is no-op.
         */
        public CaptureConfiguration setRequestBuilderModifier(
                @Nullable Consumer<CaptureRequest.Builder> requestBuilderModifier) {
            mRequestBuilderModifier = requestBuilderModifier;
            return this;
        }

        /**
         * Set the width of the capture surface and result
         * <p>
         * Default is {@link #CAMERA_WIDTH}.
         */
        public CaptureConfiguration setWidth(int width) {
            mWidth = width;
            return this;
        }

        /**
         * Set the height of the capture surface and result
         * <p>
         * Default is {@link #CAMERA_WIDTH}.
         */
        public CaptureConfiguration setHeight(int height) {
            mHeight = height;
            return this;
        }

        /**
         * Set the output format  of the capture surface and result
         * <p>
         * Default is {@link ImageFormat#YUV_420_888}.
         */
        public CaptureConfiguration setOutputFormat(int outputFormat) {
            mOutputFormat = outputFormat;
            return this;
        }
    }

    private static class TestCaptureCallback extends CaptureCallback {

        private final ArrayList<TotalCaptureResult> mCaptureResults = new ArrayList<>();
        private CountDownLatch mCaptureAndErrorLatch = new CountDownLatch(0);
        private int mFailedCaptureCount = 0;
        private boolean mFailOnFailedCapture = true;

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session,
                @NonNull CaptureRequest request,
                @NonNull CaptureFailure failure) {
            mFailedCaptureCount++;
            mCaptureAndErrorLatch.countDown();
            if (!mFailOnFailedCapture) {
                return;
            }
            synchronized (mCaptureResults) {
                Assert.fail(
                        ("Unexpected capture failure for request %s. Failure frame: %d , reason "
                                + "%s, imageCaptured: %s. Before this error we received %d "
                                + "successful frames")
                                .formatted(request, failure.getFrameNumber(),
                                        failureToString(failure),
                                        failure.wasImageCaptured(), mCaptureResults.size()));
            }
        }

        @NonNull
        private static Object failureToString(@NonNull CaptureFailure failure) {
            return switch (failure.getReason()) {
                case CaptureFailure.REASON_ERROR -> "REASON_ERROR";
                case CaptureFailure.REASON_FLUSHED -> "REASON_FLUSHED";
                default -> failure.getReason();
            };
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                @NonNull CaptureRequest request,
                @NonNull TotalCaptureResult result) {
            mCaptureResults.add(result);
            mCaptureAndErrorLatch.countDown();
        }

        public List<TotalCaptureResult> getCaptureResults() {
            synchronized (mCaptureResults) {
                return List.copyOf(mCaptureResults);
            }
        }

        public int getFailedCaptureCount() {
            return mFailedCaptureCount;
        }

        public void waitForCaptures(int expectedCaptureNumber, long timeoutMillis) {
            int captureAndErrorCount;
            synchronized (mCaptureResults) {
                captureAndErrorCount = mCaptureResults.size() + mFailedCaptureCount;
            }
            if (captureAndErrorCount >= expectedCaptureNumber) {
                return;
            }
            int missingCaptureCount = expectedCaptureNumber - captureAndErrorCount;
            mCaptureAndErrorLatch = new CountDownLatch(missingCaptureCount);
            try {
                if (!mCaptureAndErrorLatch.await(timeoutMillis * missingCaptureCount,
                        TimeUnit.MILLISECONDS)) {
                    synchronized (mCaptureResults) {
                        captureAndErrorCount = mCaptureResults.size();
                    }
                    Assert.fail(
                            ("Timed out waiting for capture. Expected: %d, received: %d "
                                    + "successful captures and %d errors")
                                    .formatted(expectedCaptureNumber, captureAndErrorCount,
                                            mFailedCaptureCount));
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting for capture", e);
            }
        }
    }
}
