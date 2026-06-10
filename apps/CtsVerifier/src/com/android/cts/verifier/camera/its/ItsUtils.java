/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.EncoderProfiles;
import android.media.Image;
import android.media.Image.Plane;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;
import android.util.Size;

import androidx.annotation.ChecksSdkIntAtLeast;

import com.android.ex.camera2.blocking.BlockingCameraManager;
import com.android.ex.camera2.blocking.BlockingStateCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

public class ItsUtils {
    public static final String TAG = ItsUtils.class.getSimpleName();
    // The tokenizer must be the same as CAMERA_ID_TOKENIZER in device.py
    public static final String CAMERA_ID_TOKENIZER = ".";

    public static ByteBuffer jsonToByteBuffer(JSONObject jsonObj) {
        return ByteBuffer.wrap(jsonObj.toString().getBytes(Charset.defaultCharset()));
    }

    public static MeteringRectangle[] getJsonWeightedRectsFromArray(
            JSONArray a, boolean normalized, int width, int height)
            throws ItsException {
        try {
            // Returns [x0,y0,x1,y1,wgt,  x0,y0,x1,y1,wgt,  x0,y0,x1,y1,wgt,  ...]
            assert(a.length() % 5 == 0);
            MeteringRectangle[] ma = new MeteringRectangle[a.length() / 5];
            for (int i = 0; i < a.length(); i += 5) {
                int x,y,w,h;
                if (normalized) {
                    x = (int)Math.floor(a.getDouble(i+0) * width + 0.5f);
                    y = (int)Math.floor(a.getDouble(i+1) * height + 0.5f);
                    w = (int)Math.floor(a.getDouble(i+2) * width + 0.5f);
                    h = (int)Math.floor(a.getDouble(i+3) * height + 0.5f);
                } else {
                    x = a.getInt(i+0);
                    y = a.getInt(i+1);
                    w = a.getInt(i+2);
                    h = a.getInt(i+3);
                }
                x = Math.max(x, 0);
                y = Math.max(y, 0);
                w = Math.min(w, width-x);
                h = Math.min(h, height-y);
                int wgt = a.getInt(i+4);
                ma[i/5] = new MeteringRectangle(x,y,w,h,wgt);
            }
            return ma;
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        }
    }

    public static JSONArray getOutputSpecs(JSONObject jsonObjTop)
            throws ItsException {
        try {
            if (jsonObjTop.has("outputSurfaces")) {
                return jsonObjTop.getJSONArray("outputSurfaces");
            }
            return null;
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        }
    }

    public static Size[] getRaw16OutputSizes(CameraCharacteristics ccs)
            throws ItsException {
        return getOutputSizes(ccs, ImageFormat.RAW_SENSOR, false);
    }

    public static Size[] getRaw16MaxResulolutionOutputSizes(CameraCharacteristics ccs)
        throws ItsException {
        return getOutputSizes(ccs, ImageFormat.RAW_SENSOR, true);
    }

    public static Size[] getRaw10OutputSizes(CameraCharacteristics ccs)
        throws ItsException {
        return getOutputSizes(ccs, ImageFormat.RAW10, false);
    }

    public static Size[] getRaw10MaxResulolutionOutputSizes(CameraCharacteristics ccs)
        throws ItsException {
        return getOutputSizes(ccs, ImageFormat.RAW10, true);
    }

    public static Size[] getRaw12OutputSizes(CameraCharacteristics ccs)
            throws ItsException {
        return getOutputSizes(ccs, ImageFormat.RAW12, false);
    }

    public static Size[] getJpegOutputSizes(CameraCharacteristics ccs)
            throws ItsException {
        return getOutputSizes(ccs, ImageFormat.JPEG, false);
    }

    public static Size[] getPrivOutputSizes(CameraCharacteristics ccs)
            throws ItsException {
        return getOutputSizes(ccs, ImageFormat.PRIVATE, false);
    }

    public static Size[] getHeicUltraHdrOutputSizes(CameraCharacteristics ccs)
            throws ItsException {
        return getOutputSizes(ccs, ImageFormat.HEIC_ULTRAHDR, false);
    }

    public static Size[] getYuvOutputSizes(CameraCharacteristics ccs)
            throws ItsException {
        return getOutputSizes(ccs, ImageFormat.YUV_420_888, false);
    }

    public static Size[] getY8OutputSizes(CameraCharacteristics ccs)
            throws ItsException {
        return getOutputSizes(ccs, ImageFormat.Y8, false);
    }

    public static Size getMaxOutputSize(CameraCharacteristics ccs, int format)
            throws ItsException {
        return getMaxSize(getOutputSizes(ccs, format, false));
    }

    public static Rect getActiveArrayCropRegion(CameraCharacteristics ccs,
        boolean isMaximumResolution) {
        Rect cropRegion = null;
        if (isMaximumResolution) {
            cropRegion = ccs.get(
                CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION);
        } else {
            cropRegion = ccs.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        }
        return cropRegion;
    }

    private static Size[] getOutputSizes(CameraCharacteristics ccs, int format,
        boolean isMaximumResolution) throws ItsException {
        StreamConfigurationMap configMap = null;
        if (isMaximumResolution) {
            configMap = ccs.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION);
        } else {
            configMap = ccs.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        }

        if (configMap == null) {
            throw new ItsException("Failed to get stream config");
        }
        Size[] normalSizes = configMap.getOutputSizes(format);
        Size[] slowSizes = configMap.getHighResolutionOutputSizes(format);
        Size[] allSizes = null;
        if (normalSizes != null && slowSizes != null) {
            allSizes = new Size[normalSizes.length + slowSizes.length];
            System.arraycopy(normalSizes, 0, allSizes, 0, normalSizes.length);
            System.arraycopy(slowSizes, 0, allSizes, normalSizes.length, slowSizes.length);
        } else if (normalSizes != null) {
            allSizes = normalSizes;
        } else if (slowSizes != null) {
            allSizes = slowSizes;
        }
        return allSizes;
    }

    public static boolean isFixedFocusLens(CameraCharacteristics c) {
        Float minFocusDistance = c.get(
                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        return (minFocusDistance != null) && (minFocusDistance == 0.0);
    }

    public static Size getMaxSize(Size[] sizes) {
        if (sizes == null || sizes.length == 0) {
            throw new IllegalArgumentException("sizes was empty");
        }

        Size maxSize = sizes[0];
        int maxArea = maxSize.getWidth() * maxSize.getHeight();
        for (int i = 1; i < sizes.length; i++) {
            int area = sizes[i].getWidth() * sizes[i].getHeight();
            if (area > maxArea ||
                    (area == maxArea && sizes[i].getWidth() > maxSize.getWidth())) {
                maxSize = sizes[i];
                maxArea = area;
            }
        }

        return maxSize;
    }

    public static byte[] getDataFromImage(Image image, Semaphore quota)
            throws ItsException {
        int format = image.getFormat();
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] data = null;

        // Read image data
        Plane[] planes = image.getPlanes();

        // Check image validity
        if (!checkAndroidImageFormat(image)) {
            throw new ItsException(
                    "Invalid image format passed to getDataFromImage: " + image.getFormat());
        }

        if ((format == ImageFormat.JPEG) || (format == ImageFormat.JPEG_R)) {
            // JPEG doesn't have pixelstride and rowstride, treat it as 1D buffer.
            ByteBuffer buffer = planes[0].getBuffer();
            if (quota != null) {
                try {
                    Logt.i(TAG, "Start waiting for quota Semaphore");
                    quota.acquire(buffer.capacity());
                    Logt.i(TAG, "Acquired quota Semaphore. Start reading image");
                } catch (java.lang.InterruptedException e) {
                    Logt.e(TAG, "getDataFromImage error acquiring memory quota. Interrupted", e);
                }
            }
            data = new byte[buffer.capacity()];
            buffer.get(data);
            Logt.i(TAG, "Done reading jpeg image");
            return data;
        } else if (format == ImageFormat.HEIC_ULTRAHDR) {
            // HEIC doesn't have pixelstride and rowstride, treat it as 1D buffer.
            ByteBuffer buffer = planes[0].getBuffer();
            // The ITS host scripts are not typically able to support HEIC/HEIF images.
            // We are also only interested in checking the actual captured pixels and not
            // the format itself so transcode to the more common JPEG.
            byte[] heicData = new byte[buffer.capacity()];
            buffer.get(heicData);
            Bitmap bmp = BitmapFactory.decodeByteArray(heicData, 0, heicData.length);
            if (bmp == null) {
                throw new ItsException("Invalid HEIC image passed to getDataFromImage");
            }
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, 100 /*quality*/, byteStream)) {
                throw new ItsException("Failed transcoding HEIC");
            }
            if (quota != null) {
                try {
                    Logt.i(TAG, "Start waiting for quota Semaphore");
                    quota.acquire(byteStream.size());
                    Logt.i(TAG, "Acquired quota Semaphore. Start reading image");
                } catch (java.lang.InterruptedException e) {
                    Logt.e(TAG, "getDataFromImage error acquiring memory quota. Interrupted", e);
                }
            }
            data = byteStream.toByteArray();
            Logt.i(TAG, "Done reading heic image");
            return data;
        } else if (format == ImageFormat.YUV_420_888 || format == ImageFormat.RAW_SENSOR
                || format == ImageFormat.RAW10 || format == ImageFormat.RAW12
                || format == ImageFormat.Y8) {
            int offset = 0;
            int dataSize = width * height * ImageFormat.getBitsPerPixel(format) / 8;
            if (quota != null) {
                try {
                    Logt.i(TAG, "Start waiting for quota Semaphore");
                    quota.acquire(dataSize);
                    Logt.i(TAG, "Acquired quota Semaphore. Start reading image");
                } catch (java.lang.InterruptedException e) {
                    Logt.e(TAG, "getDataFromImage error acquiring memory quota. Interrupted", e);
                }
            }
            data = new byte[dataSize];
            int maxRowSize = planes[0].getRowStride();
            for (int i = 0; i < planes.length; i++) {
                if (maxRowSize < planes[i].getRowStride()) {
                    maxRowSize = planes[i].getRowStride();
                }
            }
            byte[] rowData = new byte[maxRowSize];
            for (int i = 0; i < planes.length; i++) {
                ByteBuffer buffer = planes[i].getBuffer();
                int rowStride = planes[i].getRowStride();
                int pixelStride = planes[i].getPixelStride();
                int bytesPerPixel = ImageFormat.getBitsPerPixel(format) / 8;
                Logt.i(TAG, String.format(
                        "Reading image: fmt %d, plane %d, w %d, h %d," +
                        "rowStride %d, pixStride %d, bytesPerPixel %d",
                        format, i, width, height, rowStride, pixelStride, bytesPerPixel));
                // For multi-planar yuv images, assuming yuv420 with 2x2 chroma subsampling.
                int w = (i == 0) ? width : width / 2;
                int h = (i == 0) ? height : height / 2;
                for (int row = 0; row < h; row++) {
                    if (pixelStride == bytesPerPixel) {
                        // Special case: optimized read of the entire row
                        int length = w * bytesPerPixel;
                        buffer.get(data, offset, length);
                        // Advance buffer the remainder of the row stride
                        if (row < h - 1) {
                            buffer.position(buffer.position() + rowStride - length);
                        }
                        offset += length;
                    } else {
                        // Generic case: should work for any pixelStride but slower.
                        // Use intermediate buffer to avoid read byte-by-byte from
                        // DirectByteBuffer, which is very bad for performance.
                        // Also need avoid access out of bound by only reading the available
                        // bytes in the bytebuffer.
                        int readSize = rowStride;
                        if (buffer.remaining() < readSize) {
                            readSize = buffer.remaining();
                        }
                        buffer.get(rowData, 0, readSize);
                        if (pixelStride >= 1) {
                            for (int col = 0; col < w; col++) {
                                data[offset++] = rowData[col * pixelStride];
                            }
                        } else {
                            // PixelStride of 0 can mean pixel isn't a multiple of 8 bits, for
                            // example with RAW10. Just copy the buffer, dropping any padding at
                            // the end of the row.
                            int length = (w * ImageFormat.getBitsPerPixel(format)) / 8;
                            System.arraycopy(rowData,0,data,offset,length);
                            offset += length;
                        }
                    }
                }
            }
            Logt.i(TAG, String.format("Done reading image, format %d", format));
            return data;
        } else {
            throw new ItsException("Unsupported image format: " + format);
        }
    }

    private static boolean checkAndroidImageFormat(Image image) {
        int format = image.getFormat();
        Plane[] planes = image.getPlanes();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return 3 == planes.length;
            case ImageFormat.RAW_SENSOR:
            case ImageFormat.RAW10:
            case ImageFormat.RAW12:
            case ImageFormat.JPEG:
            case ImageFormat.JPEG_R:
            case ImageFormat.HEIC_ULTRAHDR:
            case ImageFormat.Y8:
                return 1 == planes.length;
            default:
                return false;
        }
    }

    public static class ItsCameraIdList {
        // Short form camera Ids (including both CameraIdList and hidden physical cameras
        public List<String> mCameraIds;
        // Camera Id combos (ids from CameraIdList, and hidden physical camera Ids
        // in the form of [logical camera id]:[hidden physical camera id]
        public List<String> mCameraIdCombos;
        // Primary rear and front camera Ids (as defined in MPC)
        public String mPrimaryRearCameraId;
        public String mPrimaryFrontCameraId;
    }

    public static ItsCameraIdList getItsCompatibleCameraIds(CameraManager manager)
            throws ItsException {
        if (manager == null) {
            throw new IllegalArgumentException("CameraManager is null");
        }

        ItsCameraIdList outList = new ItsCameraIdList();
        outList.mCameraIds = new ArrayList<String>();
        outList.mCameraIdCombos = new ArrayList<String>();
        try {
            String[] cameraIds = manager.getCameraIdList();
            for (String id : cameraIds) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                int[] actualCapabilities = characteristics.get(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                boolean haveBC = false;
                boolean isMultiCamera = false;
                final int BACKWARD_COMPAT =
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE;
                final int LOGICAL_MULTI_CAMERA =
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA;

                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    if (facing == CameraMetadata.LENS_FACING_BACK
                            && outList.mPrimaryRearCameraId == null) {
                        outList.mPrimaryRearCameraId = id;
                    } else if (facing == CameraMetadata.LENS_FACING_FRONT
                            && outList.mPrimaryFrontCameraId == null) {
                        outList.mPrimaryFrontCameraId = id;
                    }
                }

                for (int capability : actualCapabilities) {
                    if (capability == BACKWARD_COMPAT) {
                        haveBC = true;
                    }
                    if (capability == LOGICAL_MULTI_CAMERA) {
                        isMultiCamera = true;
                    }
                }

                // Skip devices that does not support BACKWARD_COMPATIBLE capability
                if (!haveBC) continue;

                int hwLevel = characteristics.get(
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                if (hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY ||
                        hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL) {
                    // Skip LEGACY and EXTERNAL devices
                    continue;
                }
                outList.mCameraIds.add(id);
                outList.mCameraIdCombos.add(id);

                // Only add hidden physical cameras for multi-camera.
                if (!isMultiCamera) continue;

                float defaultFocalLength = getLogicalCameraDefaultFocalLength(manager, id);
                Set<String> physicalIds = characteristics.getPhysicalCameraIds();
                for (String physicalId : physicalIds) {
                    if (Arrays.asList(cameraIds).contains(physicalId)) continue;

                    CameraCharacteristics physicalChar =
                            manager.getCameraCharacteristics(physicalId);
                    hwLevel = physicalChar.get(
                            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    if (hwLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY ||
                            hwLevel ==
                            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL) {
                        // Skip LEGACY and EXTERNAL devices
                        continue;
                    }

                    int[] physicalActualCapabilities = physicalChar.get(
                            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                    boolean physicalHaveBC = false;
                    for (int capability : physicalActualCapabilities) {
                        if (capability == BACKWARD_COMPAT) {
                            physicalHaveBC = true;
                            break;
                        }
                    }
                    if (!physicalHaveBC) {
                        continue;
                    }
                    // To reduce duplicate tests, only additionally test hidden physical cameras
                    // with different focal length compared to the default focal length of the
                    // logical camera.
                    float[] physicalFocalLengths = physicalChar.get(
                            CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (defaultFocalLength != physicalFocalLengths[0]) {
                        outList.mCameraIds.add(physicalId);
                        outList.mCameraIdCombos.add(id + CAMERA_ID_TOKENIZER + physicalId);
                    }
                }

            }
        } catch (CameraAccessException e) {
            Logt.e(TAG,
                    "Received error from camera service while checking device capabilities: " + e);
            throw new ItsException("Failed to get device ID list", e);
        }
        return outList;
    }

    public static float getLogicalCameraDefaultFocalLength(CameraManager manager,
            String cameraId) throws ItsException {
        BlockingCameraManager blockingManager = new BlockingCameraManager(manager);
        BlockingStateCallback listener = new BlockingStateCallback();
        HandlerThread cameraThread = new HandlerThread("ItsUtilThread");
        cameraThread.start();
        Handler cameraHandler = new Handler(cameraThread.getLooper());
        CameraDevice camera = null;
        float defaultFocalLength = 0.0f;

        try {
            camera = blockingManager.openCamera(cameraId, listener, cameraHandler);
            CaptureRequest.Builder previewBuilder =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            defaultFocalLength = previewBuilder.get(CaptureRequest.LENS_FOCAL_LENGTH);
        } catch (Exception e) {
            throw new ItsException("Failed to query default focal length for logical camera", e);
        } finally {
            if (camera != null) {
                camera.close();
            }
            if (cameraThread != null) {
                cameraThread.quitSafely();
            }
        }
        return defaultFocalLength;
    }

    public static class MediaCodecListener extends MediaCodec.Callback {
        private final MediaMuxer mMediaMuxer;
        private final Object mCondition;
        private int mTrackId = -1;
        private boolean mEndOfStream = false;

        public MediaCodecListener(MediaMuxer mediaMuxer, Object condition) {
            mMediaMuxer = mediaMuxer;
            mCondition = condition;
        }

        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            Log.e(TAG, "Unexpected input buffer available callback!");
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index,
                MediaCodec.BufferInfo info) {
            synchronized (mCondition) {
                if (mTrackId < 0) {
                    return;
                }

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    mEndOfStream = true;
                    mCondition.notifyAll();
                }

                if (!mEndOfStream) {
                    mMediaMuxer.writeSampleData(mTrackId, codec.getOutputBuffer(index), info);
                    codec.releaseOutputBuffer(index, false);
                }
            }
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            Log.e(TAG, "Codec error: " + e.getDiagnosticInfo());
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            synchronized (mCondition) {
                mTrackId = mMediaMuxer.addTrack(format);
                mMediaMuxer.start();
            }
        }
    }

    public static final long SESSION_CLOSE_TIMEOUT_MS  = 3000;

    // used to find a good-enough recording bitrate for a given resolution. "Good enough" for the
    // ITS test to run its calculations and still be supported by the HAL.
    // NOTE: Keep sorted for convenience
    public static final List<Pair<Integer, Integer>> RESOLUTION_TO_CAMCORDER_PROFILE = List.of(
            Pair.create(176  * 144,  CamcorderProfile.QUALITY_QCIF),
            Pair.create(320  * 240,  CamcorderProfile.QUALITY_QVGA),
            Pair.create(352  * 288,  CamcorderProfile.QUALITY_CIF),
            Pair.create(640  * 480,  CamcorderProfile.QUALITY_VGA),
            Pair.create(720  * 480,  CamcorderProfile.QUALITY_480P),
            Pair.create(1280 * 720,  CamcorderProfile.QUALITY_720P),
            Pair.create(1920 * 1080, CamcorderProfile.QUALITY_1080P),
            Pair.create(2048 * 1080, CamcorderProfile.QUALITY_2K),
            Pair.create(2560 * 1440, CamcorderProfile.QUALITY_QHD),
            Pair.create(3840 * 2160, CamcorderProfile.QUALITY_2160P),
            Pair.create(4096 * 2160, CamcorderProfile.QUALITY_4KDCI)
            // should be safe to assume that we don't have previews over 4k
    );

    /**
     * Initialize a HLG10 MediaFormat instance with size, bitrate, and videoFrameRate.
     */
    public static MediaFormat initializeHLG10Format(Size videoSize, int videoBitRate,
            int videoFrameRate) {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC,
                videoSize.getWidth(), videoSize.getHeight());
        format.setInteger(MediaFormat.KEY_PROFILE, HEVCProfileMain10);
        format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, videoFrameRate);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020);
        format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL);
        format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_HLG);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        return format;
    }

    // Default bitrate to use for recordings when querying CamcorderProfile fails.
    private static final int DEFAULT_RECORDING_BITRATE = 25_000_000; // 25 Mbps

    /**
     * Looks up a reasonable recording bitrate from {@link CamcorderProfile} for the given
     * {@code previewSize} and {@code maxFps}. This is not the most optimal bitrate, but should be
     * good enough for ITS tests to run their analyses.
     */
    public static int calculateBitrate(int cameraId, Size previewSize, int maxFps)
            throws ItsException {
        int previewResolution = previewSize.getHeight() * previewSize.getWidth();

        List<Pair<Integer, Integer>> resToProfile =
                new ArrayList<>(RESOLUTION_TO_CAMCORDER_PROFILE);
        // ensure that the list is sorted in ascending order of resolution
        resToProfile.sort(Comparator.comparingInt(a -> a.first));

        // Choose the first available resolution that is >= the requested preview size.
        for (Pair<Integer, Integer> entry : resToProfile) {
            if (previewResolution > entry.first) continue;
            if (!CamcorderProfile.hasProfile(cameraId, entry.second)) continue;

            EncoderProfiles profiles = CamcorderProfile.getAll(
                    String.valueOf(cameraId), entry.second);
            if (profiles == null) continue;

            List<EncoderProfiles.VideoProfile> videoProfiles = profiles.getVideoProfiles();

            // Find a profile which can achieve the requested max frame rate
            for (EncoderProfiles.VideoProfile profile : videoProfiles) {
                if (profile == null) continue;
                if (profile.getFrameRate() >= maxFps) {
                    Logt.i(TAG, "Recording bitrate: " + profile.getBitrate()
                            + ", fps " + profile.getFrameRate());
                    return  profile.getBitrate();
                }
            }
        }

        // TODO(b/223439995): There is a bug where some devices might populate result of
        //                    CamcorderProfile.getAll with nulls even when a given quality is
        //                    supported. Until this bug is fixed, fall back to the "deprecated"
        //                    CamcorderProfile.get call to get the video bitrate. This logic can be
        //                    removed once the bug is fixed.
        Logt.i(TAG, "No matching EncoderProfile found. Falling back to CamcorderProfiles");
        // Mimic logic from above, but use CamcorderProfiles instead
        for (Pair<Integer, Integer> entry : resToProfile) {
            if (previewResolution > entry.first) continue;
            if (!CamcorderProfile.hasProfile(cameraId, entry.second)) continue;

            CamcorderProfile profile = CamcorderProfile.get(cameraId, entry.second);
            if (profile == null) continue;

            int profileFrameRate = profile.videoFrameRate;
            float bitRateScale = (profileFrameRate < maxFps)
                    ? 1.0f * maxFps / profileFrameRate : 1.0f;
            Logt.i(TAG, "Recording bitrate: " + profile.videoBitRate + " * " + bitRateScale);
            return (int) (profile.videoBitRate * bitRateScale);
        }

        // Ideally, we should always find a Camcorder/Encoder Profile corresponding
        // to the preview size.
        Logt.w(TAG, "Could not find bitrate for any resolution >= " + previewSize
                + " for cameraId " + cameraId + ". Using default bitrate");
        return DEFAULT_RECORDING_BITRATE;
    }

    /**
     * Check if the device is running on at least Android V.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public static boolean isAtLeastV() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM;
    }
}
