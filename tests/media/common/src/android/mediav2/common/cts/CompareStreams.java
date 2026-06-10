/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.mediav2.common.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.mediav2.common.cts.DecodeStreamToYuv.findDecoderForFormat;
import static android.mediav2.common.cts.DecodeStreamToYuv.findDecoderForStream;
import static android.mediav2.common.cts.DecodeStreamToYuv.getFormatInStream;
import static android.mediav2.common.cts.DecodeStreamToYuv.getImage;
import static android.mediav2.common.cts.VideoErrorManager.computeMSE;
import static android.mediav2.common.cts.VideoErrorManager.computePSNR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.android.compatibility.common.util.MediaUtils;

import org.junit.Assume;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wrapper class for storing YUV Planes of an image
 */
class YUVImage {
    public ArrayList<byte[]> mData = new ArrayList<>();
}

/**
 * Utility class for video encoder tests to validate the encoded output.
 * <p>
 * The class computes the PSNR between encoders output and input. As the input to an encoder can
 * be raw yuv buffer or the output of a decoder that is connected to the encoder, the test
 * accepts YUV as well as compressed streams for validation.
 * <p>
 * Before validation, the class checks if the input and output have same width, height and bitdepth.
 */
public class CompareStreams extends CodecDecoderTestBase {
    private static final String LOG_TAG = CompareStreams.class.getSimpleName();

    private final RawResource mRefYuv;
    private final MediaFormat mStreamFormat;
    private final ByteBuffer mStreamBuffer;
    private final ArrayList<MediaCodec.BufferInfo> mStreamBufferInfos;
    private final boolean mAllowRefResize;
    private final boolean mAllowRefLoopBack;
    private final Map<Long, List<Rect>> mFrameCropRects;
    private final double[] mGlobalMSE = {0.0, 0.0, 0.0};
    private final double[] mMinimumMSE = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
    private final double[] mGlobalPSNR = new double[3];
    private final double[] mMinimumPSNR = new double[3];
    private final double[] mAvgPSNR = {0.0, 0.0, 0.0};
    private final ArrayList<double[]> mFramesPSNR = new ArrayList<>();
    private final List<List<double[]>> mFramesCropRectPSNR = new ArrayList<>();

    private final ArrayList<String> mTmpFiles = new ArrayList<>();
    private boolean mGenerateStats;
    private int mFileOffset;
    private int mFileSize;
    private int mFrameSize;
    private byte[] mInputData;

    private CompareStreams(RawResource refYuv, MediaFormat testFormat, ByteBuffer testBuffer,
            ArrayList<MediaCodec.BufferInfo> testBufferInfos, boolean allowRefResize,
            boolean allowRefLoopBack) {
        super(findDecoderForFormat(testFormat), testFormat.getString(MediaFormat.KEY_MIME), null,
                LOG_TAG);
        mRefYuv = refYuv;
        mStreamFormat = testFormat;
        mStreamBuffer = testBuffer;
        mStreamBufferInfos = testBufferInfos;
        mAllowRefResize = allowRefResize;
        mAllowRefLoopBack = allowRefLoopBack;
        mFrameCropRects = null;
    }

    public CompareStreams(RawResource refYuv, String testMediaType, String testFile,
            boolean allowRefResize, boolean allowRefLoopBack) throws IOException {
        super(findDecoderForStream(testMediaType, testFile), testMediaType, testFile, LOG_TAG);
        mRefYuv = refYuv;
        mStreamFormat = null;
        mStreamBuffer = null;
        mStreamBufferInfos = null;
        mAllowRefResize = allowRefResize;
        mAllowRefLoopBack = allowRefLoopBack;
        mFrameCropRects = null;
    }

    public CompareStreams(MediaFormat refFormat, ByteBuffer refBuffer,
            ArrayList<MediaCodec.BufferInfo> refBufferInfos, MediaFormat testFormat,
            ByteBuffer testBuffer, ArrayList<MediaCodec.BufferInfo> testBufferInfos,
            boolean allowRefResize, boolean allowRefLoopBack) {
        this(new DecodeStreamToYuv(refFormat, refBuffer, refBufferInfos).getDecodedYuv(),
                testFormat, testBuffer, testBufferInfos, allowRefResize, allowRefLoopBack);
        mTmpFiles.add(mRefYuv.mFileName);
    }

    public CompareStreams(String refMediaType, String refFile, String testMediaType,
            String testFile, boolean allowRefResize, boolean allowRefLoopBack) throws IOException {
        this(new DecodeStreamToYuv(refMediaType, refFile).getDecodedYuv(), testMediaType, testFile,
                allowRefResize, allowRefLoopBack);
        mTmpFiles.add(mRefYuv.mFileName);
    }

    public CompareStreams(RawResource refYuv, String testMediaType, String testFile,
            Map<Long, List<Rect>> frameCropRects, boolean allowRefResize, boolean allowRefLoopBack)
            throws IOException {
        super(findDecoderForStream(testMediaType, testFile), testMediaType, testFile, LOG_TAG);
        mRefYuv = refYuv;
        mStreamFormat = null;
        mStreamBuffer = null;
        mStreamBufferInfos = null;
        mAllowRefResize = allowRefResize;
        mAllowRefLoopBack = allowRefLoopBack;
        mFrameCropRects = frameCropRects;
    }

    static YUVImage fillByteArray(int tgtFrameWidth, int tgtFrameHeight,
            int bytesPerSample, int inpFrameWidth, int inpFrameHeight, byte[] inputData) {
        YUVImage yuvImage = new YUVImage();
        int inOffset = 0;
        for (int plane = 0; plane < 3; plane++) {
            int width, height, tileWidth, tileHeight;
            if (plane != 0) {
                width = tgtFrameWidth / 2;
                height = tgtFrameHeight / 2;
                tileWidth = inpFrameWidth / 2;
                tileHeight = inpFrameHeight / 2;
            } else {
                width = tgtFrameWidth;
                height = tgtFrameHeight;
                tileWidth = inpFrameWidth;
                tileHeight = inpFrameHeight;
            }
            byte[] outputData = new byte[width * height * bytesPerSample];
            for (int k = 0; k < height; k += tileHeight) {
                int rowsToCopy = Math.min(height - k, tileHeight);
                for (int j = 0; j < rowsToCopy; j++) {
                    for (int i = 0; i < width; i += tileWidth) {
                        int colsToCopy = Math.min(width - i, tileWidth);
                        System.arraycopy(inputData,
                                inOffset + j * tileWidth * bytesPerSample,
                                outputData,
                                (k + j) * width * bytesPerSample + i * bytesPerSample,
                                colsToCopy * bytesPerSample);
                    }
                }
            }
            inOffset += tileWidth * tileHeight * bytesPerSample;
            yuvImage.mData.add(outputData);
        }
        return yuvImage;
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0) {
            Image img = mCodec.getOutputImage(bufferIndex);
            assertNotNull(img);
            YUVImage yuvImage = getImage(img);
            MediaFormat format = mCodec.getOutputFormat();
            int width = getWidth(format);
            int height = getHeight(format);
            if (mOutputCount == 0) {
                int imgFormat = img.getFormat();
                int bytesPerSample = (ImageFormat.getBitsPerPixel(imgFormat) * 2) / (8 * 3);
                if (mRefYuv.mBytesPerSample != bytesPerSample) {
                    String msg = String.format(
                            "Reference file bytesPerSample and Test file bytesPerSample are not "
                                    + "same. Reference bytesPerSample : %d, Test bytesPerSample :"
                                    + " %d", mRefYuv.mBytesPerSample, bytesPerSample);
                    throw new IllegalArgumentException(msg);
                }
                if (!mAllowRefResize && (mRefYuv.mWidth != width || mRefYuv.mHeight != height)) {
                    String msg = String.format(
                            "Reference file attributes and Test file attributes are not same. "
                                    + "Reference width : %d, height : %d, bytesPerSample : %d, "
                                    + "Test width : %d, height : %d, bytesPerSample : %d",
                            mRefYuv.mWidth, mRefYuv.mHeight, mRefYuv.mBytesPerSample, width,
                            height, bytesPerSample);
                    throw new IllegalArgumentException(msg);
                }
                mFileOffset = 0;
                mFileSize = (int) new File(mRefYuv.mFileName).length();
                mFrameSize = mRefYuv.mWidth * mRefYuv.mHeight * mRefYuv.mBytesPerSample * 3 / 2;
                mInputData = new byte[mFrameSize];
            }
            try (FileInputStream fInp = new FileInputStream(mRefYuv.mFileName)) {
                assertEquals(mFileOffset, fInp.skip(mFileOffset));
                assertEquals(mFrameSize, fInp.read(mInputData));
                mFileOffset += mFrameSize;
                if (mAllowRefLoopBack && mFileOffset == mFileSize) mFileOffset = 0;
                YUVImage yuvRefImage = fillByteArray(width, height, mRefYuv.mBytesPerSample,
                        mRefYuv.mWidth, mRefYuv.mHeight, mInputData);
                List<Rect> frameCropRects =
                        mFrameCropRects != null ? mFrameCropRects.get(info.presentationTimeUs) :
                                null;
                updateErrorStats(yuvRefImage.mData.get(0), yuvRefImage.mData.get(1),
                        yuvRefImage.mData.get(2), yuvImage.mData.get(0), yuvImage.mData.get(1),
                        yuvImage.mData.get(2), width, height, frameCropRects);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            mOutputCount++;
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
            mGenerateStats = true;
            finalizerErrorStats();
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private void updateErrorStats(byte[] yRef, byte[] uRef, byte[] vRef, byte[] yTest,
            byte[] uTest, byte[] vTest, int imgWidth, int imgHeight, List<Rect> cropRectList) {
        if (cropRectList == null || cropRectList.isEmpty()) {
            cropRectList = new ArrayList<>();
            cropRectList.add(new Rect(0, 0, imgWidth, imgHeight));
        }
        double sumYMSE = 0;
        double sumUMSE = 0;
        double sumVMSE = 0;
        Rect frameRect = new Rect(0, 0, imgWidth, imgHeight);
        ArrayList<double[]> frameCropRectPSNR = new ArrayList<>();

        for (int i = 0; i < cropRectList.size(); i++) {
            Rect cropRect = new Rect(cropRectList.get(i));
            cropRect.left = clamp(cropRect.left, 0, imgWidth);
            cropRect.top = clamp(cropRect.top, 0, imgHeight);
            cropRect.right = clamp(cropRect.right, 0, imgWidth);
            cropRect.bottom = clamp(cropRect.bottom, 0, imgHeight);
            assertTrue("invalid cropRect, " + cropRect,
                    IS_AT_LEAST_T ? cropRect.isValid()
                            : cropRect.left <= cropRect.right && cropRect.top <= cropRect.bottom);
            assertTrue(String.format("cropRect %s exceeds frameRect %s", cropRect, frameRect),
                    frameRect.contains(cropRect));
            double curYMSE = computeMSE(yRef, yTest, mRefYuv.mBytesPerSample, imgWidth, imgHeight,
                    cropRect);
            sumYMSE += curYMSE;

            cropRect.left = cropRect.left / 2;   // for uv
            cropRect.top = cropRect.top / 2;
            cropRect.right = cropRect.right / 2;
            cropRect.bottom = cropRect.bottom / 2;

            double curUMSE = computeMSE(uRef, uTest, mRefYuv.mBytesPerSample, imgWidth / 2,
                    imgHeight / 2, cropRect);
            sumUMSE += curUMSE;

            double curVMSE = computeMSE(vRef, vTest, mRefYuv.mBytesPerSample, imgWidth / 2,
                    imgHeight / 2, cropRect);
            sumVMSE += curVMSE;

            double yCurrCropRectPSNR = computePSNR(curYMSE, mRefYuv.mBytesPerSample);
            double uCurrCropRectPSNR = computePSNR(curUMSE, mRefYuv.mBytesPerSample);
            double vCurrCropRectPSNR = computePSNR(curVMSE, mRefYuv.mBytesPerSample);

            frameCropRectPSNR.add(new double[]{yCurrCropRectPSNR, uCurrCropRectPSNR,
                    vCurrCropRectPSNR});
        }
        mFramesCropRectPSNR.add(frameCropRectPSNR);
        mGlobalMSE[0] += sumYMSE;
        mGlobalMSE[1] += sumUMSE;
        mGlobalMSE[2] += sumVMSE;
        mMinimumMSE[0] = Math.min(mMinimumMSE[0], sumYMSE);
        mMinimumMSE[1] = Math.min(mMinimumMSE[1], sumUMSE);
        mMinimumMSE[2] = Math.min(mMinimumMSE[2], sumVMSE);
        double yFramePSNR = computePSNR(sumYMSE, mRefYuv.mBytesPerSample);
        double uFramePSNR = computePSNR(sumUMSE, mRefYuv.mBytesPerSample);
        double vFramePSNR = computePSNR(sumVMSE, mRefYuv.mBytesPerSample);
        mAvgPSNR[0] += yFramePSNR;
        mAvgPSNR[1] += uFramePSNR;
        mAvgPSNR[2] += vFramePSNR;
        mFramesPSNR.add(new double[]{yFramePSNR, uFramePSNR, vFramePSNR});
    }

    private void finalizerErrorStats() {
        for (int i = 0; i < mGlobalPSNR.length; i++) {
            mGlobalMSE[i] /= mFramesPSNR.size();
            mGlobalPSNR[i] = computePSNR(mGlobalMSE[i], mRefYuv.mBytesPerSample);
            mMinimumPSNR[i] = computePSNR(mMinimumMSE[i], mRefYuv.mBytesPerSample);
            mAvgPSNR[i] /= mFramesPSNR.size();
        }
        if (ENABLE_LOGS) {
            String msg = String.format(
                    "global_psnr_y:%.2f, global_psnr_u:%.2f, global_psnr_v:%.2f, min_psnr_y:%"
                            + ".2f, min_psnr_u:%.2f, min_psnr_v:%.2f avg_psnr_y:%.2f, "
                            + "avg_psnr_u:%.2f, avg_psnr_v:%.2f",
                    mGlobalPSNR[0], mGlobalPSNR[1], mGlobalPSNR[2], mMinimumPSNR[0],
                    mMinimumPSNR[1], mMinimumPSNR[2], mAvgPSNR[0], mAvgPSNR[1], mAvgPSNR[2]);
            Log.v(LOG_TAG, msg);
        }
    }

    private void generateErrorStats() throws IOException, InterruptedException {
        if (!mGenerateStats) {
            if (MediaUtils.isTv()) {
                // Some TV devices support HDR10 display with VO instead of GPU. In this case,
                // COLOR_FormatYUVP010 may not be supported.
                MediaFormat format = mStreamFormat != null ? mStreamFormat :
                        getFormatInStream(mMediaType, mTestFile);
                ArrayList<MediaFormat> formatList = new ArrayList<>();
                formatList.add(format);
                boolean isHBD = doesAnyFormatHaveHDRProfile(mMediaType, formatList);
                if (isHBD || mTestFile.contains("10bit")) {
                    if (!hasSupportForColorFormat(mCodecName, mMediaType, COLOR_FormatYUVP010)) {
                        Assume.assumeTrue("Could not validate the encoded output as"
                                + " COLOR_FormatYUVP010 is not supported by the decoder", false);
                    }
                }
            }
            if (mStreamFormat != null) {
                decodeToMemory(mStreamBuffer, mStreamBufferInfos, mStreamFormat, mCodecName);
            } else {
                decodeToMemory(mTestFile, mCodecName, 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC,
                        Integer.MAX_VALUE);
            }
        }
    }

    /**
     * @see VideoErrorManager#getGlobalPSNR()
     */
    public double[] getGlobalPSNR() throws IOException, InterruptedException {
        generateErrorStats();
        return mGlobalPSNR;
    }

    /**
     * @see VideoErrorManager#getMinimumPSNR()
     */
    public double[] getMinimumPSNR() throws IOException, InterruptedException {
        generateErrorStats();
        return mMinimumPSNR;
    }

    /**
     * @see VideoErrorManager#getFramesPSNR()
     */
    public ArrayList<double[]> getFramesPSNR() throws IOException, InterruptedException {
        generateErrorStats();
        return mFramesPSNR;
    }

    /**
     * @see VideoErrorManager#getAvgPSNR()
     */
    public double[] getAvgPSNR() throws IOException, InterruptedException {
        generateErrorStats();
        return mAvgPSNR;
    }

    public List<List<double[]>> getFramesPSNRForRect() throws IOException, InterruptedException {
        generateErrorStats();
        return mFramesCropRectPSNR;
    }

    public void cleanUp() {
        for (String tmpFile : mTmpFiles) {
            File tmp = new File(tmpFile);
            if (tmp.exists()) tmp.delete();
        }
        mTmpFiles.clear();
    }
}
