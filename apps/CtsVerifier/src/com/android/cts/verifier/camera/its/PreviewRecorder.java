/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.ConditionVariable;
import android.os.Handler;
import android.util.Size;
import android.view.Surface;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to record a preview like stream. It sets up a SurfaceTexture that the camera can write to,
 * and copies over the camera frames to a MediaRecorder or MediaCodec surface.
 */
class PreviewRecorder implements AutoCloseable {
    private static final String TAG = PreviewRecorder.class.getSimpleName();

    // Frame capture timeout duration in milliseconds.
    private static final int FRAME_CAPTURE_TIMEOUT_MS = 2000; // 2 seconds

    private static final int GREEN_PAINT = 1;
    private static final int NO_PAINT = 0;

    // Simple Vertex Shader that rotates the texture before passing it to Fragment shader.
    private static final String VERTEX_SHADER = String.join(
            "\n",
            "",
            "attribute vec4 vPosition;",
            "uniform mat4 texMatrix;", // provided by SurfaceTexture
            "uniform mat2 texRotMatrix;", // optional rotation matrix, from Sensor Orientation
            "varying vec2 vTextureCoord;",
            "void main() {",
            "    gl_Position = vPosition;",
            "    vec2 texCoords = texRotMatrix * vPosition.xy;", // rotate the coordinates before
                                                                 // applying transform from
                                                                 // SurfaceTexture
            "    texCoords = (texCoords + vec2(1.0, 1.0)) / 2.0;", // Texture coordinates
                                                                   // have range [0, 1]
            "    vTextureCoord = (texMatrix * vec4(texCoords, 0.0, 1.0)).xy;",
            "}",
            ""
    );

    // Simple Fragment Shader that samples the passed texture at a given coordinate.
    private static final String FRAGMENT_SHADER = String.join(
            "\n",
            "",
            "#extension GL_OES_EGL_image_external : require",
            "precision mediump float;",
            "varying vec2 vTextureCoord;",
            "uniform samplerExternalOES sTexture;", // implicitly populated by SurfaceTexture
            "uniform int paintIt;",
            "void main() {",
            "    if (paintIt == 1) {",
            "        gl_FragColor = vec4(0.0, 1.0, 0.0, 1.0);",     // green frame
            "    } else {",
            "        gl_FragColor = texture2D(sTexture, vTextureCoord);",   // copy frame
            "    }",
            "}",
            ""
    );

    // column-major vertices list of a rectangle that fills the entire screen
    private static final float[] FULLSCREEN_VERTICES = {
            -1, -1, // bottom left
            1, -1, // bottom right
            -1,  1, // top left
            1,  1, // top right
    };


    private boolean mRecordingStarted = false; // tracks if the MediaRecorder/MediaCodec instance
                                               // was already used to record a video.

    // Lock to protect reads/writes to the various Surfaces below.
    private final Object mRecordLock = new Object();
    // Tracks if the mMediaRecorder/mMediaCodec is currently recording. Protected by mRecordLock.
    private volatile boolean mIsRecording = false;
    private boolean mIsPaintGreen = false;

    private final Size mPreviewSize;
    private final int mMaxFps;
    private final Handler mHandler;

    private Surface mRecordSurface; // MediaRecorder/MediaCodec source. EGL writes to this surface

    private MediaRecorder mMediaRecorder;

    private MediaCodec mMediaCodec;
    private MediaMuxer mMediaMuxer;
    private Object mMediaCodecCondition;

    private SurfaceTexture mCameraTexture; // Handles writing frames from camera as texture to
                                           // the GLSL program.
    private Surface mCameraSurface; // Surface corresponding to mCameraTexture that the
                                    // Camera HAL writes to

    private int mGLShaderProgram = 0;
    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLRecorderSurface; // EGL Surface corresponding to mRecordSurface

    private int mVPositionLoc;
    private int mTexMatrixLoc;
    private int mTexRotMatrixLoc;
    private int mPaintItLoc;


    private final float[] mTexRotMatrix; // length = 4
    private final float[] mTransformMatrix = new float[16];

    // An offset applied to convert from camera timestamp to codec timestamp, due to potentially
    // different time bases (elapsedRealtime vs uptime).
    private long mEncoderTimestampOffset;
    private List<Long> mFrameTimeStamps = new ArrayList();
    /**
     * Initializes MediaRecorder/MediaCodec and EGL context. The result of recorded video will
     * be stored in {@code outputFile}.
     */
    PreviewRecorder(int cameraId, Size previewSize, int maxFps, int sensorOrientation,
            String outputFile, Handler handler, boolean hlg10Enabled, long encoderTimestampOffset,
            Context context) throws ItsException {
        // Ensure that we can record the given size
        int maxSupportedResolution = ItsUtils.RESOLUTION_TO_CAMCORDER_PROFILE
                                        .stream()
                                        .map(p -> p.first)
                                        .max(Integer::compareTo)
                                        .orElse(0);
        int currentResolution = previewSize.getHeight() * previewSize.getWidth();
        if (currentResolution > maxSupportedResolution) {
            throw new ItsException("Requested preview size is greater than maximum "
                    + "supported preview size.");
        }

        mHandler = handler;
        mPreviewSize = previewSize;
        mMaxFps = maxFps;
        // rotate the texture as needed by the sensor orientation
        mTexRotMatrix = getRotationMatrix(sensorOrientation);
        mEncoderTimestampOffset = encoderTimestampOffset;

        ConditionVariable cv = new ConditionVariable();
        cv.close();

        // Init fields in the passed handler to bind egl context to the handler thread.
        mHandler.post(() -> {
            try {
                initPreviewRecorder(cameraId, outputFile, hlg10Enabled, context);
            } catch (ItsException e) {
                Logt.e(TAG, "Failed to init preview recorder", e);
                throw new ItsRuntimeException("Failed to init preview recorder", e);
            } finally {
                cv.open();
            }
        });
        // Wait for up to 1s for handler to finish initializing
        if (!cv.block(1000)) {
            throw new ItsException("Preview recorder did not initialize in 1000ms");
        }

    }

    private void initPreviewRecorder(int cameraId, String outputFile,
            boolean hlg10Enabled, Context context) throws ItsException {

        // order of initialization is important
        if (hlg10Enabled) {
            Logt.i(TAG, "HLG10 Enabled, using MediaCodec");
            setupMediaCodec(cameraId, outputFile, context);
        } else {
            Logt.i(TAG, "HLG10 Disabled, using MediaRecorder");
            setupMediaRecorder(cameraId, outputFile, context);
        }

        initEGL(hlg10Enabled); // requires recording surfaces to be set up
        compileShaders(); // requires EGL context to be set up
        setupCameraTexture(); // requires EGL context to be set up


        mCameraTexture.setOnFrameAvailableListener(surfaceTexture -> {
            // Synchronized on mRecordLock to ensure that all surface are valid while encoding
            // frames. All surfaces should be valid for as long as mIsRecording is true.
            synchronized (mRecordLock) {
                if (surfaceTexture.isReleased()) {
                    return; // surface texture already cleaned up, do nothing.
                }

                // Bind EGL context to the current thread (just in case the
                // executing thread changes)
                EGL14.eglMakeCurrent(mEGLDisplay, mEGLRecorderSurface,
                        mEGLRecorderSurface, mEGLContext);
                surfaceTexture.updateTexImage(); // update texture to the latest frame

                // Only update the frame if the recorder is currently recording.
                if (!mIsRecording) {
                    return;
                }
                try {
                    // Set EGL presentation time to camera timestamp
                    long timestamp = surfaceTexture.getTimestamp();
                    EGLExt.eglPresentationTimeANDROID(
                            mEGLDisplay, mEGLRecorderSurface,
                            timestamp + mEncoderTimestampOffset);
                    copyFrameToRecordSurface();
                    // Capture results are not collected for padded green frames
                    if (mIsPaintGreen) {
                        Logt.v(TAG, "Recorded frame# " + mFrameTimeStamps.size()
                                + " timestamp = " + timestamp
                                + " with color. mIsPaintGreen = " + mIsPaintGreen);
                    } else {
                        mFrameTimeStamps.add(timestamp);
                        Logt.v(TAG, "Recorded frame# " + mFrameTimeStamps.size()
                                + " timestamp = " + timestamp);
                    }
                } catch (ItsException e) {
                    Logt.e(TAG, "Failed to copy texture to recorder.", e);
                    throw new ItsRuntimeException("Failed to copy texture to recorder.", e);
                }
            }
        }, mHandler);
    }

    private void setupMediaRecorder(int cameraId, String outputFile, Context context)
            throws ItsException {
        mRecordSurface = MediaCodec.createPersistentInputSurface();

        mMediaRecorder = new MediaRecorder(context);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);

        mMediaRecorder.setVideoSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mMediaRecorder.setVideoEncodingBitRate(
                ItsUtils.calculateBitrate(cameraId, mPreviewSize, mMaxFps));
        mMediaRecorder.setInputSurface(mRecordSurface);
        mMediaRecorder.setVideoFrameRate(mMaxFps);
        mMediaRecorder.setOutputFile(outputFile);

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            throw new ItsException("Error preparing MediaRecorder", e);
        }
    }

    private void setupMediaCodec(int cameraId, String outputFilePath, Context context)
            throws ItsException {
        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        int videoBitRate = ItsUtils.calculateBitrate(cameraId, mPreviewSize, mMaxFps);
        MediaFormat format = ItsUtils.initializeHLG10Format(mPreviewSize, videoBitRate, mMaxFps);
        String codecName = list.findEncoderForFormat(format);
        assert (codecName != null);

        try {
            mMediaMuxer = new MediaMuxer(outputFilePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            throw new ItsException("Error preparing the MediaMuxer.");
        }

        try {
            mMediaCodec = MediaCodec.createByCodecName(codecName);
        } catch (IOException e) {
            throw new ItsException("Error preparing the MediaCodec.");
        }

        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodecCondition = new Object();
        mMediaCodec.setCallback(
                new ItsUtils.MediaCodecListener(mMediaMuxer, mMediaCodecCondition), mHandler);

        mRecordSurface = mMediaCodec.createInputSurface();
        assert (mRecordSurface != null);
    }

    private void initEGL(boolean hlg10Enabled) throws ItsException {
        // set up EGL Display
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new ItsException("Unable to get EGL display");
        }

        int[] version = {0, 0};
        if (!EGL14.eglInitialize(mEGLDisplay, version, /* majorOffset= */0,
                version, /* minorOffset= */1)) {
            mEGLDisplay = null;
            throw new ItsException("unable to initialize EGL14");
        }

        int colorBits = 8;
        int alphaBits = 8;
        if (hlg10Enabled) {
            colorBits = 10;
            alphaBits = 2;
        }
        int[] configAttribList = {
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, colorBits,
                EGL14.EGL_GREEN_SIZE, colorBits,
                EGL14.EGL_BLUE_SIZE, colorBits,
                EGL14.EGL_ALPHA_SIZE, alphaBits,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE
        };

        // set up EGL Config
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = {1};
        EGL14.eglChooseConfig(mEGLDisplay, configAttribList, 0, configs,
                0, configs.length, numConfigs, 0);
        if (configs[0] == null) {
            throw new ItsException("Unable to initialize EGL config");
        }

        EGLConfig EGLConfig = configs[0];

        int[] contextAttribList = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE };

        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, EGLConfig, EGL14.EGL_NO_CONTEXT,
                contextAttribList, 0);
        if (mEGLContext == EGL14.EGL_NO_CONTEXT) {
            throw new ItsException("Failed to create EGL context");
        }

        int[] clientVersion = {0};
        EGL14.eglQueryContext(mEGLDisplay, mEGLContext, EGL14.EGL_CONTEXT_CLIENT_VERSION,
                clientVersion, /* offset= */0);
        Logt.i(TAG, "EGLContext created, client version " + clientVersion[0]);

        // Create EGL Surface to write to the recording Surface.
        int[] surfaceAttribs = {EGL14.EGL_NONE};
        mEGLRecorderSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, EGLConfig, mRecordSurface,
                surfaceAttribs, /* offset= */0);
        if (mEGLRecorderSurface == EGL14.EGL_NO_SURFACE) {
            throw new ItsException("Failed to create EGL recorder surface");
        }

        // Bind EGL context to the current (handler) thread.
        EGL14.eglMakeCurrent(mEGLDisplay, mEGLRecorderSurface, mEGLRecorderSurface, mEGLContext);
    }

    private void setupCameraTexture() throws ItsException {
        mCameraTexture = new SurfaceTexture(createTexture());
        mCameraTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        mCameraSurface = new Surface(mCameraTexture);
    }

    /**
     * Compiles the vertex and fragment shader into a shader program, and sets up the location
     * fields that will be written to later.
     */
    private void compileShaders() throws ItsException {
        int vertexShader = createShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = createShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

        mGLShaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mGLShaderProgram, vertexShader);
        GLES20.glAttachShader(mGLShaderProgram, fragmentShader);
        GLES20.glLinkProgram(mGLShaderProgram);

        int[] linkStatus = {0};
        GLES20.glGetProgramiv(mGLShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            String msg = "Could not link program: " + GLES20.glGetProgramInfoLog(mGLShaderProgram);
            GLES20.glDeleteProgram(mGLShaderProgram);
            throw new ItsException(msg);
        }

        mVPositionLoc = GLES20.glGetAttribLocation(mGLShaderProgram, "vPosition");
        mTexMatrixLoc = GLES20.glGetUniformLocation(mGLShaderProgram, "texMatrix");
        mTexRotMatrixLoc = GLES20.glGetUniformLocation(mGLShaderProgram, "texRotMatrix");
        mPaintItLoc = GLES20.glGetUniformLocation(mGLShaderProgram, "paintIt");

        GLES20.glUseProgram(mGLShaderProgram);
        assertNoGLError("glUseProgram");
    }

    /**
     * Creates a new GLSL texture that can be populated by {@link SurfaceTexture} and returns the
     * corresponding ID. Throws {@link ItsException} if there is an error creating the textures.
     */
    private int createTexture() throws ItsException {
        IntBuffer buffer = IntBuffer.allocate(1);
        GLES20.glGenTextures(1, buffer);
        int texId = buffer.get(0);

        // This flags the texture to be implicitly populated by SurfaceTexture
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);

        boolean isTexture = GLES20.glIsTexture(texId);
        if (!isTexture) {
            throw new ItsException("Failed to create texture id. Returned texture id: " + texId);
        }

        return texId;
    }

    /**
     * Compiles the gives {@code source} as a shader of the provided {@code type}. Throws an
     * {@link ItsException} if there are errors while compiling the shader.
     */
    private int createShader(int type, String source) throws ItsException {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[]{0};
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == GLES20.GL_FALSE) {
            String msg = "Could not compile shader " + type + ": "
                    + GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new ItsException(msg);
        }

        return shader;
    }

    /**
     * Throws an {@link ItsException} if the previous GL call resulted in an error. No-op otherwise.
     */
    private void assertNoGLError(String op) throws ItsException {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String msg = op + ": glError 0x" + Integer.toHexString(error);
            throw new ItsException(msg);
        }
    }



    /**
     * Copies a frame encoded as a texture by {@code mCameraTexture} to
     * {@code mRecordSurface} by running our simple shader program for one frame that draws
     * to {@code mEGLRecorderSurface}.
     */
    private void copyFrameToRecordSurface() throws ItsException {
        // Clear color buffer
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        assertNoGLError("glClearColor");
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        assertNoGLError("glClear");

        // read texture transformation matrix from SurfaceTexture and write it to GLSL program.
        mCameraTexture.getTransformMatrix(mTransformMatrix);
        GLES20.glUniformMatrix4fv(mTexMatrixLoc, /* count= */1, /* transpose= */false,
                mTransformMatrix, /* offset= */0);
        assertNoGLError("glUniformMatrix4fv");

        // write texture rotation matrix to GLSL program
        GLES20.glUniformMatrix2fv(mTexRotMatrixLoc, /* count= */1, /* transpose= */false,
                mTexRotMatrix, /* offset= */0);
        assertNoGLError("glUniformMatrix2fv");

        GLES20.glUniform1i(mPaintItLoc, mIsPaintGreen ? GREEN_PAINT : NO_PAINT);
        assertNoGLError("glUniform1i");

        // write vertices of the full-screen rectangle to the GLSL program
        ByteBuffer nativeBuffer = ByteBuffer.allocateDirect(
                  FULLSCREEN_VERTICES.length * Float.BYTES);
        nativeBuffer.order(ByteOrder.nativeOrder());
        FloatBuffer vertexBuffer = nativeBuffer.asFloatBuffer();
        vertexBuffer.put(FULLSCREEN_VERTICES);
        nativeBuffer.position(0);
        vertexBuffer.position(0);

        GLES20.glEnableVertexAttribArray(mVPositionLoc);
        assertNoGLError("glEnableVertexAttribArray");
        GLES20.glVertexAttribPointer(mVPositionLoc, /* size= */ 2, GLES20.GL_FLOAT,
                /* normalized= */ false, /* stride= */ 8, vertexBuffer);
        assertNoGLError("glVertexAttribPointer");


        // viewport size should match the frame dimensions to prevent stretching/cropping
        GLES20.glViewport(0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());
        assertNoGLError("glViewport");

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */0, /* count= */4);
        assertNoGLError("glDrawArrays");

        if (!EGL14.eglSwapBuffers(mEGLDisplay, mEGLRecorderSurface)) {
            throw new ItsException("EglSwapBuffers failed to copy buffer to recording surface");
        }
    }

    /**
     * Returns column major 2D rotation matrix that can be fed directly to GLSL.
     * This matrix rotates around the origin.
     */
    private static float[] getRotationMatrix(int orientationDegrees) {
        double rads = orientationDegrees * Math.PI / 180;
        // Rotate clockwise because sensor orientation assumes clockwise rotation
        return new float[] {
                (float) Math.cos(rads), (float) -Math.sin(rads),
                (float) Math.sin(rads), (float) Math.cos(rads)
        };
    }

    Surface getCameraSurface() {
        return mCameraSurface;
    }

    /**
     * Copies a frame encoded as a texture by {@code mCameraTexture} to a Bitmap by running our
     * simple shader program for one frame and then convert the frame to a JPEG and write to
     * the JPEG bytes to the {@code outputStream}.
     *
     * This method should not be called while recording.
     *
     * @param outputStream The stream to which the captured JPEG image bytes are written to
     */
    void getFrame(OutputStream outputStream) throws ItsException {
        synchronized (mRecordLock) {
            if (mIsRecording) {
                throw new ItsException("Attempting to get frame while recording is active is an "
                        + "invalid combination.");
            }

            ConditionVariable cv = new ConditionVariable();
            cv.close();
            // GL copy texture to JPEG should happen on the thread EGL Context was bound to
            mHandler.post(() -> {
                try {
                    copyFrameToRecordSurface();

                    ByteBuffer frameBuffer = ByteBuffer.allocateDirect(
                            mPreviewSize.getWidth() * mPreviewSize.getHeight() * 4);
                    frameBuffer.order(ByteOrder.nativeOrder());

                    GLES20.glReadPixels(
                            0,
                            0,
                            mPreviewSize.getWidth(),
                            mPreviewSize.getHeight(),
                            GLES20.GL_RGBA,
                            GLES20.GL_UNSIGNED_BYTE,
                            frameBuffer);
                    Bitmap frame = Bitmap.createBitmap(
                            mPreviewSize.getWidth(),
                            mPreviewSize.getHeight(),
                            Bitmap.Config.ARGB_8888);
                    frame.copyPixelsFromBuffer(frameBuffer);
                    frame.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                } catch (ItsException e) {
                    Logt.e(TAG, "Could not get frame from texture", e);
                    throw new ItsRuntimeException("Failed to get frame from texture", e);
                } finally {
                    cv.open();
                }
            });

            // Wait for up to two seconds for jpeg frame capture.
            if (!cv.block(FRAME_CAPTURE_TIMEOUT_MS)) {
                throw new ItsException("Frame capture timed out");
            }
        }
    }

    /**
     * Starts recording frames from mCameraSurface. This method should
     * only be called once. Throws {@link ItsException} on subsequent calls.
     */
    void startRecording() throws ItsException {
        if (mRecordingStarted) {
            throw new ItsException("Attempting to record on a stale PreviewRecorder. "
                    + "Create a new instance instead.");
        }
        mRecordingStarted = true;
        Logt.i(TAG, "Starting Preview Recording.");
        synchronized (mRecordLock) {
            mIsRecording = true;
            if (mMediaRecorder != null) {
                mMediaRecorder.start();
            } else {
                mMediaCodec.start();
            }
        }
    }

    /**
     * Override camera frames with green frames, if recordGreenFrames
     * parameter is true. Record Green frames as buffer to workaround
     * MediaRecorder issue of missing frames at the end of recording.
     */
    void overrideCameraFrames(boolean recordGreenFrames) throws ItsException {
        Logt.i(TAG, "Recording Camera frames. recordGreenFrames = " + recordGreenFrames);
        synchronized (mRecordLock) {
            mIsPaintGreen = recordGreenFrames;
        }
    }

    /**
     * Stops recording frames.
     */
    void stopRecording() throws ItsException {
        Logt.i(TAG, "Stopping Preview Recording.");
        synchronized (mRecordLock) {
            stopRecordingLocked();
        }
    }

    private void stopRecordingLocked() throws ItsException {
        mIsRecording = false;
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
        } else {
            mMediaCodec.signalEndOfInputStream();

            synchronized (mMediaCodecCondition) {
                try {
                    mMediaCodecCondition.wait(ItsUtils.SESSION_CLOSE_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    throw new ItsException("Unexpected InterruptedException: ", e);
                }
            }

            mMediaMuxer.stop();
            mMediaCodec.stop();
        }
    }

    @Override
    public void close() throws ItsException {
        // synchronized to prevent reads and writes to surfaces while they are being released.
        synchronized (mRecordLock) {
            if (mIsRecording) {
                Logt.e(TAG, "Preview recording was not stopped before closing.");
                stopRecordingLocked();
            }
            mCameraSurface.release();
            mCameraTexture.release();
            if (mMediaRecorder != null) {
                mMediaRecorder.release();
            }
            if (mMediaCodec != null) {
                mMediaCodec.release();
            }
            if (mMediaMuxer != null) {
                mMediaMuxer.release();
            }
            mRecordSurface.release();

            ConditionVariable cv = new ConditionVariable();
            cv.close();
            // GL Cleanup should happen on the thread EGL Context was bound to
            mHandler.post(() -> {
                try {
                    cleanupEgl();
                } finally {
                    cv.open();
                }
            });

            // Wait for up to a second for egl to clean up.
            // Since this is clean up, do nothing if the handler takes longer than 1s.
            cv.block(/*timeoutMs=*/ 1000);
        }
    }

    private void cleanupEgl() {
        if (mGLShaderProgram == 0) {
            // egl program was never set up, no cleanup needed
            return;
        }

        Logt.i(TAG, "Cleaning up EGL Context");
        GLES20.glDeleteProgram(mGLShaderProgram);
        // Release the egl surfaces and context from the handler
        EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(mEGLDisplay, mEGLRecorderSurface);
        EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);

        EGL14.eglTerminate(mEGLDisplay);
    }

    /**
     * Returns Camera frame's timestamps only after recording completes.
     */
    public List<Long> getFrameTimeStamps() throws IllegalStateException {
        synchronized (mRecordLock) {
            if (mIsRecording) {
                throw new IllegalStateException("Can't return timestamps during recording.");
            }
            return mFrameTimeStamps;
        }
    }
}
