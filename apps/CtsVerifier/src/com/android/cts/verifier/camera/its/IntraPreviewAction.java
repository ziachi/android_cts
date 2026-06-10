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

package com.android.cts.verifier.camera.its;

import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.ConditionVariable;
import android.os.Handler;

import androidx.annotation.NonNull;

import org.json.JSONArray;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.Locale;

/**
 * An action to be executed during preview recordings, controlling
 * a {@link CameraCaptureSession} if needed.
 */
abstract class IntraPreviewAction {
    /**
     * Time to sleep after a preview recording action that sends new {@link CaptureRequest}s.
     */
    static final long PREVIEW_RECORDING_FINAL_SLEEP_MS = 200;

    /**
     * Time to wait for {@link CameraCaptureSession} initialization.
     */
    static final long SESSION_INITIALIZATION_WAIT_MS = 500;

    /**
     * Time to wait for {@link CaptureRequest.Builder} initialization.
     */
    static final long CAPTURE_REQUEST_BUILDER_INITIALIZATION_WAIT_MS = 500;

    /**
     * Initialized after {@link ItsService} configures and creates the session.
     */
    volatile CameraCaptureSession mSession;

    /**
     * {@link ConditionVariable} that tracks when the {@link CameraCaptureSession}
     * has been initialized.
     */
    ConditionVariable mSessionInitialized = new ConditionVariable();

    /**
     * Initialized after {@link ItsService} configures and creates the session.
     */
    volatile CaptureRequest.Builder mCaptureRequestBuilder;

    /**
     * {@link ConditionVariable} that tracks when the {@link CaptureRequest.Builder}
     * has been initialized.
     */
    ConditionVariable mCaptureRequestBuilderInitialized = new ConditionVariable();

    /**
     * {@link CameraCharacteristics} that are initialized when the camera is opened.
     */
    CameraCharacteristics mCameraCharacteristics;

    /**
     * The {@link android.os.Handler} on which the listener should be invoked.
     */
    Handler mCameraHandler;

    /**
     * The {@link com.android.cts.verifier.camera.its.ItsService.RecordingResultListener} that
     * tracks certain {@link android.hardware.camera2.TotalCaptureResult} values received
     * during recording.
     */
    ItsService.RecordingResultListener mRecordingResultListener;

    protected IntraPreviewAction(
            CameraCharacteristics cameraCharacteristics,
            Handler handler,
            ItsService.RecordingResultListener recordingResultListener) {
        mCameraCharacteristics = cameraCharacteristics;
        mCameraHandler = handler;
        mRecordingResultListener = recordingResultListener;
    }

    /**
     * Sets the value for the current {@link CameraCaptureSession}.
     */
    void setSession(@NonNull CameraCaptureSession session) {
        mSession = session;
        mSessionInitialized.open();
    }

    /**
     * Sets the value for the current {@link CaptureRequest.Builder}, so that
     * {@link IntraPreviewAction} can update the same {@link CaptureRequest} used during
     * configuration.
     */
    void setCaptureRequestBuilder(@NonNull CaptureRequest.Builder builder) {
        mCaptureRequestBuilder = builder;
        mCaptureRequestBuilderInitialized.open();
    }

    /**
     * Gets the value for the current
     * {@link com.android.cts.verifier.camera.its.ItsService.RecordingResultListener}.
     */
    ItsService.RecordingResultListener getRecordingResultListener() {
        return mRecordingResultListener;
    }

    /**
     * Perform actions between {@link PreviewRecorder#startRecording()} and
     * {@link CameraCaptureSession#stopRepeating()}. The execute() method can be used to define the
     * duration of the recording, using {@link Thread#sleep(long)}. The method can also call
     * {@link CameraCaptureSession#setRepeatingRequest(CaptureRequest, CameraCaptureSession.CaptureCallback, Handler)}
     * to change the requests during the recording.
     *
     * @throws InterruptedException if {@link Thread#sleep(long)} was interrupted.
     * @throws CameraAccessException if a camera device could not be opened to set requests.
     * @throws ItsException if parsing a JSONObject or JSONArray was unsuccessful.
     */
    public abstract void execute() throws
            InterruptedException, CameraAccessException, ItsException;
}

/**
 * A simple action that sleeps for a given duration during a preview recording.
 */
class PreviewSleepAction extends IntraPreviewAction {
    long mRecordingDuration;

    PreviewSleepAction(
            CameraCharacteristics cameraCharacteristics,
            Handler handler,
            ItsService.RecordingResultListener recordingResultListener,
            long recordingDuration) {
        super(cameraCharacteristics, handler, recordingResultListener);
        mRecordingDuration = recordingDuration;
    }

    @Override
    public void execute() throws InterruptedException {
        Thread.sleep(mRecordingDuration);
    }
}

/**
 * An action that sets new repeating {@link CaptureRequest}s to change zoom ratios during recording.
 */
class PreviewDynamicZoomAction extends IntraPreviewAction {
    double mZoomStart;
    double mZoomEnd;
    double mStepSize;
    long mStepDuration;
    PreviewDynamicZoomAction(
            CameraCharacteristics cameraCharacteristics,
            Handler handler,
            ItsService.RecordingResultListener recordingResultListener,
            double zoomStart, double zoomEnd, double stepSize, long stepDuration) {
        super(cameraCharacteristics, handler,  recordingResultListener);
        mZoomStart = zoomStart;
        mZoomEnd = zoomEnd;
        mStepSize = stepSize;
        mStepDuration = stepDuration;
    }

    @Override
    public void execute() throws ItsException, InterruptedException, CameraAccessException {
        mSessionInitialized.block(SESSION_INITIALIZATION_WAIT_MS);
        mCaptureRequestBuilderInitialized.block(CAPTURE_REQUEST_BUILDER_INITIALIZATION_WAIT_MS);
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        mSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                mRecordingResultListener, mCameraHandler);
        // Wait for autofocus to converge
        if (!ItsUtils.isFixedFocusLens(mCameraCharacteristics) &&
                    !mRecordingResultListener.waitForAfConvergence()) {
            throw new ItsException(
                    "AF failed to converge before dynamic zoom requests sent.");
        }
        for (double z = mZoomStart; z <= mZoomEnd; z += mStepSize) {
            Logt.i(ItsService.TAG, String.format(
                    Locale.getDefault(),
                    "zoomRatio set to %.4f during preview recording.", z));
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, (float) z);
            mSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                    mRecordingResultListener, mCameraHandler);
            Logt.i(ItsService.TAG, String.format(
                    Locale.getDefault(),
                    "Sleeping %d ms during video recording", mStepDuration));
            Thread.sleep(mStepDuration);
        }
        Thread.sleep(PREVIEW_RECORDING_FINAL_SLEEP_MS);
    }
}

/**
 * An action that sets new repeating {@link CaptureRequest}s to change metering regions during
 * recording.
 */
class PreviewDynamicMeteringAction extends IntraPreviewAction {
    JSONArray mAeAwbRegionOne;
    JSONArray mAeAwbRegionTwo;
    JSONArray mAeAwbRegionThree;
    JSONArray mAeAwbRegionFour;
    long mAeAwbRegionDuration;

    PreviewDynamicMeteringAction(
            CameraCharacteristics cameraCharacteristics,
            Handler handler,
            ItsService.RecordingResultListener recordingResultListener,
            JSONArray aeAwbRegionOne,
            JSONArray aeAwbRegionTwo,
            JSONArray aeAwbRegionThree,
            JSONArray aeAwbRegionFour,
            long aeAwbRegionDuration) {
        super(cameraCharacteristics, handler, recordingResultListener);
        mAeAwbRegionOne = aeAwbRegionOne;
        mAeAwbRegionTwo = aeAwbRegionTwo;
        mAeAwbRegionThree = aeAwbRegionThree;
        mAeAwbRegionFour = aeAwbRegionFour;
        mAeAwbRegionDuration = aeAwbRegionDuration;
    }

    @Override
    public void execute() throws ItsException, InterruptedException, CameraAccessException {
        mSessionInitialized.block(SESSION_INITIALIZATION_WAIT_MS);
        mCaptureRequestBuilderInitialized.block(CAPTURE_REQUEST_BUILDER_INITIALIZATION_WAIT_MS);
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        mSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                mRecordingResultListener, mCameraHandler);
        // Wait for autofocus to converge
        if (!ItsUtils.isFixedFocusLens(mCameraCharacteristics) &&
                    !mRecordingResultListener.waitForAfConvergence()) {
            throw new ItsException(
                    "AF failed to converge before dynamic metering requests sent.");
        }
        Rect activeArray = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        assert activeArray != null;
        int aaWidth = activeArray.right - activeArray.left;
        int aaHeight = activeArray.bottom - activeArray.top;
        JSONArray[] aeAwbRegionRoutine = {
                mAeAwbRegionOne, mAeAwbRegionTwo, mAeAwbRegionThree, mAeAwbRegionFour};
        for (JSONArray aeAwbRegion : aeAwbRegionRoutine) {
            MeteringRectangle[] region = ItsUtils.getJsonWeightedRectsFromArray(
                    aeAwbRegion, /*normalized=*/true, aaWidth, aaHeight);
            Logt.i(ItsService.TAG, String.format(
                    Locale.getDefault(),
                    "AE/AWB region set to %s during preview recording.",
                    Arrays.toString(region)));
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, region);
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AWB_REGIONS, region);
            mSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                    mRecordingResultListener, mCameraHandler);
            Logt.i(ItsService.TAG, String.format(
                    Locale.getDefault(),
                    "Sleeping %d ms during recording", mAeAwbRegionDuration));
            Thread.sleep(mAeAwbRegionDuration);
        }
        Thread.sleep(PREVIEW_RECORDING_FINAL_SLEEP_MS);
    }
}
