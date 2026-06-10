/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.hardware.multiprocess.camera.cts;

import static android.hardware.camera2.cts.CameraTestUtils.*;

import static org.mockito.Mockito.*;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraExtensionSession;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraOfflineSession;
import android.hardware.camera2.CameraSharedCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.cts.Camera2SurfaceViewCtsActivity;
import android.hardware.camera2.cts.CameraTestUtils;
import android.hardware.camera2.cts.CameraTestUtils.HandlerExecutor;
import android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import android.hardware.camera2.params.ExtensionSessionConfiguration;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.SharedSessionConfiguration;
import android.hardware.camera2.params.SharedSessionConfiguration.SharedOutputConfiguration;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.ResultReceiver;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.android.ex.camera2.blocking.BlockingExtensionSessionCallback;
import com.android.ex.camera2.blocking.BlockingSessionCallback;
import com.android.ex.camera2.utils.StateWaiter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Activity implementing basic access of the shared camera API.
 *
 * <p>This will log all errors to {@link
 * android.hardware.multiprocess.camera.cts.ErrorLoggingService}.
 */
public class SharedCameraActivity extends Camera2SurfaceViewCtsActivity {
    private static final String TAG = "SharedCameraActivity";
    private static final int CAPTURE_RESULT_TIMEOUT_MS = 3000;
    private static final int NUM_MAX_IMAGES = 10;
    ErrorLoggingService.ErrorServiceConnection mErrorServiceConnection;
    CameraManager mCameraManager;
    StateCallback mStateCallback;
    SessionCallback mSessionCallback;
    Handler mCameraHandler;
    HandlerThread mCameraHandlerThread;
    String mCameraId;
    Messenger mMessenger;
    CameraDevice mCameraDevice;
    CameraSharedCaptureSession mSession;
    BlockingSessionCallback mSessionMockListener;
    CaptureRequest.Builder mCaptureRequestBuilder;
    Surface mPreviewSurface;
    int mCaptureSequenceId;
    SimpleCaptureCallback mCaptureListener;
    SimpleImageReaderListener mImageListener;
    SharedSessionConfiguration mSharedSessionConfig;
    ImageReader mReader;
    Surface mReaderSurface;
    boolean mIsSurfaceViewPresent = false;
    boolean mIsImageReaderPresent = false;
    Size mImageReaderSize;
    int mImageReaderFormat;
    Size mSurfaceViewSize;
    boolean mIsPrimary = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called: uid " + Process.myUid() + ".");
        super.onCreate(savedInstanceState);

        mMessenger = new Messenger(new IncomingHandler());
        ResultReceiver resultReceiver = getIntent().getParcelableExtra(
                TestConstants.EXTRA_RESULT_RECEIVER);
        Bundle resultData = new Bundle();
        resultData.putParcelable(TestConstants.EXTRA_REMOTE_MESSENGER, mMessenger);
        resultReceiver.send(RESULT_OK, resultData);
        mCameraHandlerThread = new HandlerThread("CameraHandlerThread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());
        mErrorServiceConnection = new ErrorLoggingService.ErrorServiceConnection(this);
        mErrorServiceConnection.start();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume called.");
        super.onResume();
        mCameraManager = getSystemService(CameraManager.class);
        if (mCameraManager == null) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG
                    + " could not connect camera service");
            return;
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called.");
        super.onDestroy();

        if (mSession != null) {
            mSession.close();
            mSession = null;
        }
        mCameraHandlerThread.quitSafely();

        if (mErrorServiceConnection != null) {
            mErrorServiceConnection.stop();
            mErrorServiceConnection = null;
        }
    }

    private class StateCallback extends CameraDevice.StateCallback {
        String mChosenCameraId;

        StateCallback(String camId) {
            mChosenCameraId = camId;
        }

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_CONNECT, mChosenCameraId);
            Log.i(TAG, "Camera " + mChosenCameraId + " is opened");
        }

        @Override
        public void onOpenedInSharedMode(CameraDevice cameraDevice, boolean isPrimary) {
            mCameraDevice = cameraDevice;
            mIsPrimary = isPrimary;
            if (isPrimary) {
                mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_CONNECT_SHARED_PRIMARY,
                        mChosenCameraId);
            } else {
                mErrorServiceConnection.logAsync(
                        TestConstants.EVENT_CAMERA_CONNECT_SHARED_SECONDARY, mChosenCameraId);
            }
            Log.i(
                    TAG,
                    "Camera " + mChosenCameraId + " is opened in shared mode primary=" + isPrimary);
        }

        @Override
        public void onClosed(CameraDevice cameraDevice) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_CLOSED, mChosenCameraId);
            Log.i(TAG, "Camera " + mChosenCameraId + " is closed");
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mErrorServiceConnection.logAsync(
                    TestConstants.EVENT_CAMERA_DISCONNECTED, mChosenCameraId);
            Log.i(TAG, "Camera " + mChosenCameraId + " is disconnected");
        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            mErrorServiceConnection.logAsync(
                    TestConstants.EVENT_CAMERA_ERROR,
                    TAG + " Camera " + mChosenCameraId + " experienced error " + i);
            Log.e(TAG, "Camera " + mChosenCameraId + " onError called with error " + i);
        }

        @Override
        public void onClientSharedAccessPriorityChanged(CameraDevice camera, boolean isPrimary) {
            mIsPrimary = isPrimary;
            if (isPrimary) {
                mErrorServiceConnection.logAsync(
                        TestConstants.EVENT_CLIENT_ACCESS_PRIORITIES_CHANGED_TO_PRIMARY,
                        mChosenCameraId);
            } else {
                mErrorServiceConnection.logAsync(
                        TestConstants.EVENT_CLIENT_ACCESS_PRIORITIES_CHANGED_TO_SECONDARY,
                        mChosenCameraId);
            }
            Log.i(
                    TAG,
                    "Camera "
                            + mChosenCameraId
                            + " onClientSharedAccessPriorityChanged primary="
                            + isPrimary);
        }
    }

    private class SessionCallback extends CameraCaptureSession.StateCallback {
        String mChosenCameraId;

        SessionCallback(String camId) {
            mChosenCameraId = camId;
        }

        @Override
        public void onClosed(CameraCaptureSession session) {
            mSession = null;
            mErrorServiceConnection.logAsync(
                    TestConstants.EVENT_CAMERA_SESSION_CLOSED, mChosenCameraId);
            Log.i(TAG, "Camera capture session for camera " + mChosenCameraId + " is closed");
        }

        @Override
        public void onConfigured(CameraCaptureSession session) {
            mSession = (CameraSharedCaptureSession) session;
            mErrorServiceConnection.logAsync(
                    TestConstants.EVENT_CAMERA_SESSION_CONFIGURED, mChosenCameraId);
            Log.i(TAG, "Camera capture session for camera " + mChosenCameraId + " is configured");
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            mSession = null;
            mErrorServiceConnection.logAsync(
                    TestConstants.EVENT_CAMERA_SESSION_CONFIGURE_FAILED, mChosenCameraId);
            Log.i(TAG, "Camera capture session creation for camera " + mChosenCameraId + " failed");
        }
    }

    private static class ExtensionSessionCallback extends CameraExtensionSession.StateCallback {

        @Override
        public void onClosed(CameraExtensionSession session) {
            // do nothing
        }

        @Override
        public void onConfigured(CameraExtensionSession session) {
            // do nothing
        }

        @Override
        public void onConfigureFailed(CameraExtensionSession session) {
            // do nothing
        }
    }

    private static class OfflineSessionCallback
            extends CameraOfflineSession.CameraOfflineSessionCallback {

        @Override
        public void onReady(CameraOfflineSession session) {
            // do nothing
        }

        @Override
        public void onSwitchFailed(CameraOfflineSession session) {
            // do nothing
        }

        @Override
        public void onIdle(CameraOfflineSession session) {
            // do nothing
        }

        @Override
        public void onError(CameraOfflineSession session, int status) {
            // do nothing
        }

        @Override
        public void onClosed(CameraOfflineSession session) {
            // do nothing
        }
    }

    private void updatePreviewSurface(Size sz) {
        final SurfaceHolder holder = getSurfaceView().getHolder();
        mPreviewSurface = holder.getSurface();
        holder.setFixedSize(sz.getWidth(), sz.getHeight());
    }

    class IncomingHandler extends Handler {

        private void createImageReader(Size sz, int format) {
            mImageListener = new SimpleImageReaderListener(/*asyncMode*/true, NUM_MAX_IMAGES);
            mReader = makeImageReader(sz, format, NUM_MAX_IMAGES, mImageListener, mCameraHandler);
            mReaderSurface = mReader.getSurface();
        }

        private void closeImageReader() {
            CameraTestUtils.closeImageReader(mReader);
            if (mImageListener != null) {
                mImageListener.drain();
                mImageListener = null;
            }
            mReader = null;
            mReaderSurface = null;
        }

        private void checkValidSurfaceIsPresent() {
            for (SharedOutputConfiguration sharedStreamInfo :
                    mSharedSessionConfig.getOutputStreamsInformation()) {
                if (sharedStreamInfo.getSurfaceType() == TestConstants.SURFACE_TYPE_SURFACE_VIEW) {
                    mIsSurfaceViewPresent = true;
                    mSurfaceViewSize = sharedStreamInfo.getSize();
                }
                if (sharedStreamInfo.getSurfaceType() == TestConstants.SURFACE_TYPE_IMAGE_READER) {
                    mIsImageReaderPresent = true;
                    mImageReaderSize = sharedStreamInfo.getSize();
                    mImageReaderFormat = sharedStreamInfo.getFormat();
                }
            }
        }

        @Override
        public void handleMessage(Message msg) {
            final Executor executor = new HandlerExecutor(mCameraHandler);

            switch (msg.what) {
                case TestConstants.OP_OPEN_CAMERA:
                    mCameraId = msg.getData().getString(TestConstants.EXTRA_CAMERA_ID);
                    try {
                        if (mStateCallback == null || mStateCallback.mChosenCameraId != mCameraId) {
                            mStateCallback = new StateCallback(mCameraId);
                            mCameraManager.openCamera(mCameraId, executor, mStateCallback);
                        }
                    } catch (CameraAccessException e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " camera exception during connection: " + e);
                        Log.e(TAG, "Access exception: " + e);
                    }
                    break;

                case TestConstants.OP_OPEN_CAMERA_SHARED:
                    mCameraId = msg.getData().getString(TestConstants.EXTRA_CAMERA_ID);
                    try {
                        boolean sharingEnabled =
                                mCameraManager.isCameraDeviceSharingSupported(mCameraId);
                        if (!sharingEnabled) {
                            mErrorServiceConnection.logAsync(
                                    TestConstants.EVENT_CAMERA_ERROR,
                                    TAG + " camera device does not support shared mode");
                            Log.e(TAG, "camera device does not support shared mode");
                            return;
                        }
                        if (mStateCallback == null || mStateCallback.mChosenCameraId != mCameraId) {
                            mStateCallback = new StateCallback(mCameraId);
                            mCameraManager.openSharedCamera(mCameraId, executor, mStateCallback);
                            CameraCharacteristics props =
                                    mCameraManager.getCameraCharacteristics(mCameraId);
                            mSharedSessionConfig = props.get(
                                    CameraCharacteristics.SHARED_SESSION_CONFIGURATION);
                            if (mSharedSessionConfig == null) {
                                mErrorServiceConnection.logAsync(
                                        TestConstants.EVENT_CAMERA_ERROR,
                                        TAG + " shared session config is null");
                                Log.e(TAG, "shared session config is null");
                                return;
                            }
                            checkValidSurfaceIsPresent();
                            if (mIsSurfaceViewPresent) {
                                updatePreviewSurface(mSurfaceViewSize);
                            }
                            if (mIsImageReaderPresent) {
                                createImageReader(mImageReaderSize, mImageReaderFormat);
                            }
                        }
                    } catch (CameraAccessException e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " camera exception during connection: " + e);
                        Log.e(TAG, "Access exception: " + e);
                    }
                    break;

                case TestConstants.OP_CLOSE_CAMERA:
                    if (mSession != null) {
                        mSession.close();
                        mSession = null;
                        mSessionMockListener = null;
                        mCaptureListener = null;
                        mSessionCallback = null;
                    }
                    closeImageReader();
                    if (mCameraDevice != null) {
                        mCameraDevice.close();
                        mCameraDevice = null;
                        mStateCallback = null;
                    }
                    break;

                case TestConstants.OP_CREATE_SHARED_SESSION:
                    if (mCameraDevice == null) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_ERROR, TAG + "camera device is null");
                        Log.e(TAG, "camera device is null");
                        return;
                    }
                    List<Integer> sharedStreamArray =
                            msg.getData()
                                    .getIntegerArrayList(TestConstants.EXTRA_SHARED_STREAM_ARRAY);
                    if (sharedStreamArray == null) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " shared stream index array is null");
                        Log.e(TAG, "shared stream index array is null");
                        return;
                    }
                    try {
                        List<OutputConfiguration> outputs = new ArrayList<>();
                        if (mIsPrimary) {
                            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(
                                    CameraDevice.TEMPLATE_PREVIEW);
                        }
                        for (int i = 0; i < sharedStreamArray.size(); i++) {
                            Integer surfaceType = sharedStreamArray.get(i);
                            if (surfaceType == TestConstants.SURFACE_TYPE_SURFACE_VIEW) {
                                outputs.add(new OutputConfiguration(mPreviewSurface));
                            }
                            if (surfaceType == TestConstants.SURFACE_TYPE_IMAGE_READER) {
                                outputs.add(new OutputConfiguration(mReaderSurface));
                            }
                        }
                        if (outputs.isEmpty()) {
                            mErrorServiceConnection.logAsync(
                                    TestConstants.EVENT_CAMERA_ERROR,
                                    TAG + " shared output configuration is empty");
                            Log.e(TAG, "shared output configuration is empty");
                            return;
                        }
                        if (mSessionCallback == null
                                || mSessionCallback.mChosenCameraId != mCameraId) {
                            mSessionCallback = new SessionCallback(mCameraId);
                        }
                        mSessionMockListener = spy(new BlockingSessionCallback(mSessionCallback));
                        StateWaiter sessionWaiter = mSessionMockListener.getStateWaiter();
                        SessionConfiguration sessionConfig =
                                new SessionConfiguration(
                                        SessionConfiguration.SESSION_SHARED,
                                        outputs,
                                        executor,
                                        mSessionMockListener);
                        if (mIsPrimary) {
                            sessionConfig.setSessionParameters(mCaptureRequestBuilder.build());
                        }
                        mCameraDevice.createCaptureSession(sessionConfig);
                        sessionWaiter.waitForState(
                                BlockingSessionCallback.SESSION_CONFIGURED,
                                SESSION_CONFIGURE_TIMEOUT_MS);
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " exception during creating shared session: " + e);
                        Log.e(TAG, "exception during creating shared session: " + e);
                    }
                    break;

                case TestConstants.OP_CREATE_SHARED_SESSION_INVALID_CONFIGS:
                    if (mCameraDevice == null) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_ERROR, TAG + "camera device is null");
                        Log.e(TAG, "camera device is null");
                        return;
                    }
                    if (!mIsSurfaceViewPresent && !mIsImageReaderPresent) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " shared output configuration is empty");
                        Log.e(TAG, "shared output configuration is empty");
                        return;
                    }
                    if (mSessionCallback == null || mSessionCallback.mChosenCameraId != mCameraId) {
                        mSessionCallback = new SessionCallback(mCameraId);
                    }
                    mSessionMockListener = spy(new BlockingSessionCallback(mSessionCallback));

                    // tests that using enableSurfaceSharing() returns an error
                    try {
                        StateWaiter sessionWaiter = mSessionMockListener.getStateWaiter();
                        List<OutputConfiguration> outputs = new ArrayList<>();
                        if (mIsImageReaderPresent) {
                            OutputConfiguration imageReaderOutputConfig =
                                    new OutputConfiguration(mReaderSurface);
                            imageReaderOutputConfig.enableSurfaceSharing();
                            outputs.add(imageReaderOutputConfig);
                        } else if (mIsSurfaceViewPresent) {
                            OutputConfiguration surfaceViewOutputConfig =
                                    new OutputConfiguration(mPreviewSurface);
                            surfaceViewOutputConfig.enableSurfaceSharing();
                            outputs.add(surfaceViewOutputConfig);
                        }

                        SessionConfiguration sessionConfig =
                                new SessionConfiguration(
                                        SessionConfiguration.SESSION_SHARED,
                                        outputs,
                                        executor,
                                        mSessionMockListener);
                        mCameraDevice.createCaptureSession(sessionConfig);
                        sessionWaiter.waitForState(
                                BlockingSessionCallback.SESSION_CONFIGURED,
                                SESSION_CONFIGURE_TIMEOUT_MS);
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_SESSION_CONFIGURE_FAILED,
                                TAG
                                        + " Expected exception when calling"
                                        + " enableSurfaceSharing(): "
                                        + e);
                    }

                    // tests that using setInputConfiguration() returns an error
                    try {
                        StateWaiter sessionWaiter = mSessionMockListener.getStateWaiter();
                        List<OutputConfiguration> outputs = new ArrayList<>();
                        if (mIsImageReaderPresent) {
                            outputs.add(new OutputConfiguration(mReaderSurface));
                        } else if (mIsSurfaceViewPresent) {
                            outputs.add(new OutputConfiguration(mPreviewSurface));
                        }

                        SessionConfiguration sessionConfig =
                                new SessionConfiguration(
                                        SessionConfiguration.SESSION_SHARED,
                                        outputs,
                                        executor,
                                        mSessionMockListener);
                        sessionConfig.setInputConfiguration(
                                new InputConfiguration(
                                        /* width */ 7680,
                                        /* height */ 4320,
                                        /* format */ ImageFormat.PRIVATE));
                        mCameraDevice.createCaptureSession(sessionConfig);
                        sessionWaiter.waitForState(
                                BlockingSessionCallback.SESSION_CONFIGURED,
                                SESSION_CONFIGURE_TIMEOUT_MS);
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_SESSION_CONFIGURE_FAILED,
                                TAG
                                        + " Expected exception when calling"
                                        + " setInputConfiguration(): "
                                        + e);
                    }

                    // tests that using an image reader surface with a different size than the one
                    // provided in the shared session config returns an error
                    if (mIsImageReaderPresent) {
                        ImageReader newReader =  null;
                        try {
                            StateWaiter sessionWaiter = mSessionMockListener.getStateWaiter();
                            newReader = ImageReader.newInstance(
                                    mReader.getWidth() + 1,
                                    mReader.getHeight() + 1, /* format */
                                    ImageFormat.YUV_420_888,
                                    /* maxImages */ 2);
                            List<OutputConfiguration> outputs = new ArrayList<>();
                            outputs.add(new OutputConfiguration(newReader.getSurface()));
                            SessionConfiguration sessionConfig =
                                    new SessionConfiguration(
                                            SessionConfiguration.SESSION_SHARED,
                                            outputs,
                                            executor,
                                            mSessionMockListener);
                            mCameraDevice.createCaptureSession(sessionConfig);
                            sessionWaiter.waitForState(
                                    BlockingSessionCallback.SESSION_CONFIGURED,
                                    SESSION_CONFIGURE_TIMEOUT_MS);

                            return;
                        } catch (Exception e) {
                            mErrorServiceConnection.logAsync(
                                    TestConstants.EVENT_CAMERA_SESSION_CONFIGURE_FAILED,
                                    TAG
                                            + " Expected exception when using different size and"
                                            + " format than shared session config: "
                                            + e);
                        } finally {
                            CameraTestUtils.closeImageReader(newReader);
                        }
                    }
                    break;

                case TestConstants.OP_PERFORM_UNSUPPORTED_COMMANDS:
                    if (mCameraDevice == null) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_ERROR, TAG + "camera device is null");
                        Log.e(TAG, "camera device is null");
                        return;
                    }
                    if (!mIsSurfaceViewPresent && !mIsImageReaderPresent) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " shared output configuration is empty");
                        Log.e(TAG, "shared output configuration is empty");
                        return;
                    }

                    if (mSessionCallback == null || mSessionCallback.mChosenCameraId != mCameraId) {
                        mSessionCallback = new SessionCallback(mCameraId);
                    }
                    mSessionMockListener = spy(new BlockingSessionCallback(mSessionCallback));

                    StateWaiter sessionWaiterUnsupportedOps = mSessionMockListener.getStateWaiter();
                    List<OutputConfiguration> outputsConfigs = new ArrayList<>();
                    if (mIsImageReaderPresent) {
                        outputsConfigs.add(new OutputConfiguration(mReaderSurface));
                    } else if (mIsSurfaceViewPresent) {
                        outputsConfigs.add(new OutputConfiguration(mPreviewSurface));
                    }
                    try {
                        SessionConfiguration sessionConfig =
                                new SessionConfiguration(
                                        SessionConfiguration.SESSION_HIGH_SPEED,
                                        outputsConfigs,
                                        executor,
                                        mSessionMockListener);
                        mCameraDevice.createCaptureSession(sessionConfig);
                        sessionWaiterUnsupportedOps.waitForState(
                                BlockingSessionCallback.SESSION_CONFIGURED,
                                SESSION_CONFIGURE_TIMEOUT_MS);
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_SESSION_CONFIGURE_FAILED,
                                TAG
                                        + " Expected exception from creating SESSION_HIGH_SPEED"
                                        + " session: "
                                        + e);
                    }

                    try {
                        SessionConfiguration sessionConfig =
                                new SessionConfiguration(
                                        SessionConfiguration.SESSION_REGULAR,
                                        outputsConfigs,
                                        executor,
                                        mSessionMockListener);
                        mCameraDevice.createCaptureSession(sessionConfig);
                        sessionWaiterUnsupportedOps.waitForState(
                                BlockingSessionCallback.SESSION_CONFIGURED,
                                SESSION_CONFIGURE_TIMEOUT_MS);
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_SESSION_CONFIGURE_FAILED,
                                TAG
                                        + " Expected exception from creating SESSION_REGULAR"
                                        + " session: "
                                        + e);
                    }

                    try {
                        BlockingExtensionSessionCallback extensionSessionMockListener =
                                spy(
                                        new BlockingExtensionSessionCallback(
                                                new ExtensionSessionCallback()));
                        StateWaiter extensionSessionWaiter =
                                extensionSessionMockListener.getStateWaiter();
                        ExtensionSessionConfiguration extensionSessionConfig =
                                new ExtensionSessionConfiguration(
                                        CameraExtensionCharacteristics.EXTENSION_AUTOMATIC,
                                        outputsConfigs,
                                        executor,
                                        extensionSessionMockListener);
                        mCameraDevice.createExtensionSession(extensionSessionConfig);
                        extensionSessionWaiter.waitForState(
                                BlockingExtensionSessionCallback.SESSION_CONFIGURED,
                                SESSION_CONFIGURE_TIMEOUT_MS);
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_SESSION_CONFIGURE_FAILED,
                                TAG
                                        + " Expected exception from running"
                                        + " createExtensionSession"
                                        + e);
                    }

                    try {
                        List<Surface> surfaces = new ArrayList<>();
                        if (mIsImageReaderPresent) {
                            surfaces.add(mReaderSurface);
                        } else if (mIsSurfaceViewPresent) {
                            surfaces.add(mPreviewSurface);
                        }
                        InputConfiguration inputConfig =
                                new InputConfiguration(
                                        /* width */ 7680,
                                        /* height */ 4320,
                                        /* format */ ImageFormat.YUV_420_888);
                        mCameraDevice.createReprocessableCaptureSession(
                                inputConfig, surfaces, mSessionMockListener, mCameraHandler);
                        sessionWaiterUnsupportedOps.waitForState(
                                BlockingSessionCallback.SESSION_CONFIGURED,
                                SESSION_CONFIGURE_TIMEOUT_MS);
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_SESSION_CONFIGURE_FAILED,
                                TAG
                                        + " Expected exception from running"
                                        + " createReprocessableCaptureSession"
                                        + e);
                    }

                    try {
                        List<Surface> surfaces = new ArrayList<>();
                        if (mIsImageReaderPresent) {
                            surfaces.add(mReaderSurface);
                        } else if (mIsSurfaceViewPresent) {
                            surfaces.add(mPreviewSurface);
                        }
                        mCameraDevice.createConstrainedHighSpeedCaptureSession(
                                surfaces, mSessionMockListener, mCameraHandler);
                        sessionWaiterUnsupportedOps.waitForState(
                                BlockingSessionCallback.SESSION_CONFIGURED,
                                SESSION_CONFIGURE_TIMEOUT_MS);
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_SESSION_CONFIGURE_FAILED,
                                TAG
                                        + " Expected exception from running"
                                        + " createConstrainedHighSpeedCaptureSession"
                                        + e);
                    }
                    break;

                case TestConstants.OP_PERFORM_UNSUPPORTED_CAPTURE_SESSION_COMMANDS:
                    if (mCameraDevice == null || mSession == null) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_ERROR,
                                TAG + "No active camera device or session is present");
                        Log.e(TAG, "No active camera device or session is present");
                        return;
                    }
                    List<CaptureRequest> captureRequests = new ArrayList<>();
                    captureRequests.add(mCaptureRequestBuilder.build());
                    mCaptureListener = new SimpleCaptureCallback();
                    try {
                        mCaptureSequenceId =
                                mSession.captureBurst(
                                        captureRequests, mCaptureListener, mCameraHandler);
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_STARTED,
                                TAG + " Activity started.");
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_FAILED,
                                TAG + " Expected exception from running captureBurst: " + e);
                    }

                    try {
                        mCaptureSequenceId =
                                mSession.captureBurstRequests(
                                        captureRequests, executor, mCaptureListener);
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_STARTED,
                                TAG + " Activity started.");
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_FAILED,
                                TAG
                                        + " Expected exception from running"
                                        + " captureBurstRequest: "
                                        + e);
                    }

                    try {
                        mCaptureSequenceId =
                                mSession.setRepeatingBurst(
                                        captureRequests, mCaptureListener, mCameraHandler);
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_STARTED,
                                TAG + " Activity started.");
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_FAILED,
                                TAG + " Expected exception from running setRepeatingBurst: " + e);
                    }

                    try {
                        mCaptureSequenceId =
                                mSession.setRepeatingBurstRequests(
                                        captureRequests, executor, mCaptureListener);
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_STARTED,
                                TAG + " Activity started.");
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_FAILED,
                                TAG
                                        + " Expected exception from running"
                                        + " setRepeatingBurstRequests: "
                                        + e);
                    }

                    if (mSession.supportsOfflineProcessing(mPreviewSurface)) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_STARTED,
                                TAG + " Activity started.");
                        return;
                    } else {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_FAILED,
                                TAG + " Expected false from running supportsOfflineProcessing");
                    }

                    try {
                        List<Surface> surfaces = new ArrayList<>();
                        surfaces.add(mPreviewSurface);
                        mSession.switchToOffline(surfaces, executor, new OfflineSessionCallback());
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_STARTED,
                                TAG + " Activity started.");
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_FAILED,
                                TAG
                                        + " Expected exception from running"
                                        + " supportsOfflineProcessing: "
                                        + e);
                    }

                    try {
                        OutputConfiguration outputConfig = new OutputConfiguration(mPreviewSurface);
                        mSession.updateOutputConfiguration(outputConfig);
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_STARTED,
                                TAG + " Activity started.");
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_FAILED,
                                TAG
                                        + " Expected exception from running"
                                        + " updateOutputConfiguration: "
                                        + e);
                    }

                    try {
                        List<OutputConfiguration> outputConfigs = new ArrayList<>();
                        outputConfigs.add(new OutputConfiguration(mPreviewSurface));
                        mSession.finalizeOutputConfigurations(outputConfigs);
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_STARTED,
                                TAG + " Activity started.");
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_FAILED,
                                TAG
                                        + " Expected exception from running"
                                        + " finalizeOutputConfigurations: "
                                        + e);
                    }

                    if (mSession.isReprocessable()) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_STARTED,
                                TAG + " Activity started.");
                        return;
                    } else {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_FAILED,
                                TAG + " Expected false from running isReprocessable");
                    }

                    if (mSession.getInputSurface() != null) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_STARTED,
                                TAG + " Activity started.");
                        return;
                    } else {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_FAILED,
                                TAG + " Expected null from running getInputSurface");
                    }

                    try {
                        mSession.prepare(mPreviewSurface);
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_STARTED,
                                TAG + " Activity started.");
                        return;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_FAILED,
                                TAG + " Expected exception from running" + " prepare: " + e);
                    }
                    break;

                case TestConstants.OP_START_PREVIEW:
                    if (mCameraDevice == null || mSession == null) {
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " No active camera device or session is present");
                        Log.e(TAG, "No active camera device or session is present");
                        return;
                    }
                    try {
                        mCaptureListener = new SimpleCaptureCallback();
                        List<Integer> previewStreamArray = msg.getData().getIntegerArrayList(
                                TestConstants.EXTRA_SHARED_STREAM_ARRAY);
                        if (previewStreamArray == null) {
                            mErrorServiceConnection.logAsync(
                                    TestConstants.EVENT_CAMERA_ERROR,
                                    TAG + " preview stream index array is null");
                            Log.e(TAG, "preview stream index array is null");
                            return;
                        }
                        List<Surface> surfaces = new ArrayList<>();
                        boolean imageReaderUsed = false;
                        if (mIsPrimary && (mCaptureRequestBuilder == null)) {
                            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(
                                    CameraDevice.TEMPLATE_PREVIEW);
                        }
                        for (int i = 0; i < previewStreamArray.size(); i++) {
                            Integer surfaceType = previewStreamArray.get(i);
                            if (surfaceType == TestConstants.SURFACE_TYPE_SURFACE_VIEW) {
                                if (mIsPrimary) {
                                    mCaptureRequestBuilder.addTarget(mPreviewSurface);
                                } else {
                                    surfaces.add(mPreviewSurface);
                                }
                            }
                            if (surfaceType == TestConstants.SURFACE_TYPE_IMAGE_READER) {
                                imageReaderUsed = true;
                                if (mIsPrimary) {
                                    mCaptureRequestBuilder.addTarget(mReaderSurface);
                                } else {
                                    surfaces.add(mReaderSurface);
                                }
                            }
                        }
                        if (mIsPrimary) {
                            mCaptureSequenceId =
                                    mSession.setRepeatingRequest(
                                            mCaptureRequestBuilder.build(),
                                            mCaptureListener,
                                            mCameraHandler);
                        } else {
                            mCaptureSequenceId =
                                    mSession.startStreaming(surfaces, executor, mCaptureListener);
                        }
                        mCaptureListener.getCaptureResult(CAPTURE_RESULT_TIMEOUT_MS);
                        if (imageReaderUsed) {
                            mImageListener.getImage(CAPTURE_RESULT_TIMEOUT_MS).close();
                        }

                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_PREVIEW_STARTED,
                                Integer.toString(mCaptureSequenceId));
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " exception during start preview: " + e);
                        Log.e(TAG, "exception during start preview: " + e);
                    }
                    break;

                case TestConstants.OP_STOP_PREVIEW:
                    if (mCameraDevice == null || mSession == null) {
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " No active camera device or session is present");
                        Log.e(TAG, "No active camera device or session is present");
                        return;
                    }
                    try {
                        if (mIsPrimary) {
                            mSession.stopRepeating();
                        } else {
                            mSession.stopStreaming();
                        }
                        mCaptureListener.getCaptureSequenceLastFrameNumber(
                                mCaptureSequenceId, CAPTURE_RESULT_TIMEOUT_MS);
                        mCaptureListener.drain();
                        mImageListener.drain();
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_PREVIEW_COMPLETED,
                                Integer.toString(mCaptureSequenceId));
                        mCaptureSequenceId = -1;
                    } catch (Exception e) {
                        mErrorServiceConnection.logAsync(
                                TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " exception during stop preview: " + e);
                        Log.e(TAG, "exception during stop preview: " + e);
                    }
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }
}
