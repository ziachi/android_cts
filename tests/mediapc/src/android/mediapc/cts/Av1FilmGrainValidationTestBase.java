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

package android.mediapc.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.mediav2.common.cts.DecodeStreamToYuv.getImage;
import static android.mediav2.common.cts.DecodeStreamToYuv.unWrapYUVImage;
import static android.mediav2.common.cts.VideoErrorManager.computeFrameVariance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.graphics.ImageFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.ImageSurface;
import android.mediav2.common.cts.OutputManager;
import android.util.Log;
import android.util.Pair;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Av1FilmGrainValidationTestBase extends CodecDecoderTestBase {
    private static final String LOG_TAG = Av1FilmGrainValidationTestBase.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();


    /**
     * **Frame Metadata Structure:**
     * - Frame Index: The index of the frame in the clip.
     * - Variance Without Film-Grain: The variance of the frame when film-grain is disabled.
     * - Variance With Film-Grain: The variance of the frame when film-grain is enabled.
     */
    public static class FrameMetadata {
        public final int mFrameIndex;
        public final double mVarWithoutFilmGrain;
        public final double mVarWithFilmGrain;

        FrameMetadata(int frameIndex, double varWithoutFilmGrain, double varWithFilmGrain) {
            mFrameIndex = frameIndex;
            mVarWithoutFilmGrain = varWithoutFilmGrain;
            mVarWithFilmGrain = varWithFilmGrain;
        }
    }

    /**
     * Please refer to the Av1FilmGrainValidationTest.md for details.
     */
    final Map<Integer, FrameMetadata> mRefFrameVarList = Map.ofEntries(
            Map.entry(0, new FrameMetadata(0, 6000.441895, 6036.660666)),
            Map.entry(2, new FrameMetadata(2, 6023.912926, 6056.566605)),
            Map.entry(4, new FrameMetadata(4, 6038.928583, 6067.471322)),
            Map.entry(6, new FrameMetadata(6, 5998.420908, 6051.48108)),
            Map.entry(8, new FrameMetadata(8, 5891.097766, 5934.600298)),
            Map.entry(10, new FrameMetadata(10, 5837.958102, 5881.128501)),
            Map.entry(12, new FrameMetadata(12, 5818.852211, 5861.067651)),
            Map.entry(14, new FrameMetadata(14, 5738.605461, 5787.236232)),
            Map.entry(16, new FrameMetadata(16, 5862.405073, 5897.406853)),
            Map.entry(18, new FrameMetadata(18, 5884.802443, 5921.534628)),
            Map.entry(20, new FrameMetadata(20, 5909.591605, 5933.699168)),
            Map.entry(22, new FrameMetadata(22, 5953.370089, 5993.686707)),
            Map.entry(24, new FrameMetadata(24, 5967.687195, 6007.651102)),
            Map.entry(26, new FrameMetadata(26, 6013.546157, 6044.971079)),
            Map.entry(28, new FrameMetadata(28, 6059.718453, 6109.048043))
    );
    Map<Integer, Double> mTestFrameVarList = new HashMap<>();

    public Av1FilmGrainValidationTestBase(String decoder, String mediaType, String testFile,
            String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
    }

    private short[] extractLuma(byte[] inputData) {
        int length = inputData.length / 2;
        short[] p010Data = new short[length];
        ByteBuffer.wrap(inputData)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .get(p010Data);

        return p010Data;
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: "
                    + info.size + " timestamp: " + info.presentationTimeUs);
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, mSurface != null);
        if (info.size > 0) {
            try (Image image = mImageSurface.getImage(1000)) {
                assertNotNull("no image received from surface \n" + mTestConfig + mTestEnv, image);
                if (mRefFrameVarList.containsKey(mOutputCount - 1)) {
                    MediaFormat format = getOutputFormat();
                    ArrayList<byte[]> data = unWrapYUVImage(getImage(image));
                    int imgformat = image.getFormat();
                    int colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                    int bytesPerSample = (ImageFormat.getBitsPerPixel(imgformat) * 2) / (8 * 3);
                    assertEquals("received image with incorrect bit depth.",
                            bytesPerSample, colorFormat == COLOR_FormatYUVP010 ? 2 : 1);

                    Pair<Double, Integer> var;
                    if (bytesPerSample == 2) {
                        var = computeFrameVariance(getWidth(format), getHeight(format),
                                extractLuma(data.get(0)));
                    } else {
                        var = computeFrameVariance(getWidth(format), getHeight(format),
                                data.get(0));
                    }
                    double frameVariance = var.first / var.second;
                    mTestFrameVarList.put(mOutputCount - 1, frameVariance);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupted status
                throw new RuntimeException(e);
            }
        }
    }

    public void doDecode() throws Exception {
        MediaFormat format = setUpSource(mTestFile);
        int colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
        mImageSurface = new ImageSurface();
        int imageFormat = colorFormat == COLOR_FormatYUVP010 ? ImageFormat.YCBCR_P010 :
                ImageFormat.YUV_420_888;
        setUpSurface(getWidth(format), getHeight(format), imageFormat, 1, 0, null);
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        configureCodec(format, true, true, false);
        mCodec.start();
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        mExtractor.release();
    }
}
