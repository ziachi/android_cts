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

package android.mediav2.cts;

import static android.media.codec.Flags.apvSupport;
import static android.mediav2.common.cts.CodecEncoderTestBase.ACCEPTABLE_WIRELESS_TX_QUALITY;
import static android.mediav2.common.cts.CodecTestBase.BOARD_SDK_IS_AT_LEAST_T;
import static android.mediav2.common.cts.CodecTestBase.FIRST_SDK_IS_AT_LEAST_T;
import static android.mediav2.common.cts.CodecTestBase.IS_AT_LEAST_B;
import static android.mediav2.common.cts.MuxerUtils.getTempFilePath;

import static com.android.media.editing.flags.Flags.muxerMp4EnableApv;
import static com.android.media.extractor.flags.Flags.extractorMp4EnableApv;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderSurfaceTestBase;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Test transcoding using media codec api.
 * <p>
 * The test decodes an input clip to surface. This decoded output is fed as input to encoder.
 * Assuming no frame drops, the test expects,
 * <ul>
 *     <li>The number of encoded frames to be identical to number of frames present in input clip
 *     .</li>
 *     <li>The encoder output timestamps list should be identical to decoder input timestamp list
 *     .</li>
 * </ul>
 * <p>
 * The output of encoder is further verified by computing PSNR to check for obvious visual
 * artifacts.
 */
@RunWith(Parameterized.class)
public class VideoTranscoderTest extends CodecEncoderSurfaceTestBase {
    private static final String LOG_TAG = VideoTranscoderTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private final int mFrameLimit;

    private final ArrayList<String> mTmpFiles = new ArrayList<>();

    public VideoTranscoderTest(String encoder, String mediaType, String decoder,
            String testFileMediaType, String testFile, EncoderConfigParams encCfgParams,
            int decColorFormat, boolean isOutputToneMapped, boolean usePersistentSurface,
            @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, decoder, testFileMediaType, MEDIA_DIR + testFile, encCfgParams,
                decColorFormat, isOutputToneMapped, usePersistentSurface, allTestParams);
        mFrameLimit = Math.max(encCfgParams.mFrameRate, 30);
    }

    @After
    public void tearDown() {
        for (String tmpFile : mTmpFiles) {
            File tmp = new File(tmpFile);
            if (tmp.exists()) assertTrue("unable to delete file " + tmpFile, tmp.delete());
        }
        mTmpFiles.clear();
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}_{3}_{9}")
    public static Collection<Object[]> input() throws IOException {
        String[] mediaTypes = {MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
                MediaFormat.MIMETYPE_VIDEO_VP8, MediaFormat.MIMETYPE_VIDEO_VP9,
                MediaFormat.MIMETYPE_VIDEO_AV1};

        final List<Object[]> args = new ArrayList<>(Arrays.asList(new Object[][]{
                // mediaType, testFileMediaType, testFile, bitRate, frameRate, toneMap
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_340x280_768kbps_30fps_avc.mp4", 512000,
                        30, false},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_340x280_768kbps_30fps_hevc.mp4", 512000,
                        30, false},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_340x280_768kbps_30fps_vp8.webm", 512000,
                        30, false},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_340x280_768kbps_30fps_vp9.webm", 512000,
                        30, false},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_340x280_768kbps_30fps_av1.mp4", 512000,
                        30, false},
        }));

        List<Object[]> newArgs = new ArrayList<>();
        for(String mediaType : mediaTypes) {
            for (Object[] arg : args) {
                Object[] newArg = new Object[arg.length + 1];
                newArg[0] = mediaType;
                System.arraycopy(arg, 0, newArg, 1, arg.length);
                // force higher bitrate for apv encoder
                if (mediaType.equals(MediaFormat.MIMETYPE_VIDEO_APV)) newArg[3] = 8000000;
                newArgs.add(newArg);
            }
        }

        List<String> mediaTypesHighBitDepth = new ArrayList<>(Arrays.asList(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC,
                MediaFormat.MIMETYPE_VIDEO_VP9,
                MediaFormat.MIMETYPE_VIDEO_AV1
        ));

        final List<Object[]> argsHighBitDepth = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_520x390_24fps_crf22_avc_10bit.mkv",
                        512000, 30, false},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_520x390_24fps_crf22_avc_10bit.mkv",
                        512000, 30, true},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_520x390_24fps_crf22_hevc_10bit.mkv",
                        512000, 30, false},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_520x390_24fps_crf22_hevc_10bit.mkv",
                        512000, 30, true},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_520x390_24fps_crf22_vp9_10bit.mkv",
                        512000, 30, false},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_520x390_24fps_crf22_vp9_10bit.mkv",
                        512000, 30, true},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_520x390_24fps_768kbps_av1_10bit.mkv",
                        512000, 30, false},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_520x390_24fps_768kbps_av1_10bit.mkv",
                        512000, 30, true},
        }));

        if (IS_AT_LEAST_B && apvSupport() && muxerMp4EnableApv() && extractorMp4EnableApv()) {
            mediaTypesHighBitDepth.add(MediaFormat.MIMETYPE_VIDEO_APV);
            argsHighBitDepth.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_APV, "pattern_640x480_30fps_16mbps_apv_10bit.mp4",
                            512000, 30, false},
            }));
        }

        List<Object[]> newArgsHighBitDepth = new ArrayList<>();
        for(String mediaType : mediaTypesHighBitDepth) {
            for (Object[] arg : argsHighBitDepth) {
                Object[] newArg = new Object[arg.length + 1];
                newArg[0] = mediaType;
                System.arraycopy(arg, 0, newArg, 1, arg.length);
                // force higher bitrate for apv encoder
                if (mediaType.equals(MediaFormat.MIMETYPE_VIDEO_APV)) newArg[3] = 8000000;
                newArgsHighBitDepth.add(newArg);
            }
        }

        int[] maxBFrames = {0};
        boolean[] usePersistentSurfaceStates = {false};
        return prepareParamsList(newArgs, newArgsHighBitDepth, maxBFrames,
                usePersistentSurfaceStates);
    }

    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatSurface",
            "android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUVP010",
            "android.media.MediaFormat#KEY_COLOR_TRANSFER_REQUEST"})
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testTranscode() throws IOException, InterruptedException {
        boolean muxOutput = true;
        if (mEncMediaType.equals(MediaFormat.MIMETYPE_VIDEO_AV1) && CodecTestBase.IS_BEFORE_U) {
            muxOutput = false;
        }
        if (mEncMediaType.equals(MediaFormat.MIMETYPE_VIDEO_APV)) {
            assumeFalse(
                    "skip tonemap tests for APV encoders as they don't support 8-bit output",
                    mIsOutputToneMapped);
        }
        String tmpPath = getTempFilePath(mEncCfgParams.mInputBitDepth > 8 ? "10bit" : "");
        mTmpFiles.add(tmpPath);
        encodeToMemory(false, false, false, new OutputManager(), muxOutput, tmpPath, mFrameLimit);
        // Skip stream validation as there is no reference for tone mapped input
        if (muxOutput && !mIsOutputToneMapped) {
            // HDR support introduced with T, with pieces in both system+vendor
            if (mEncCfgParams.mInputBitDepth > 8) {
                if (!FIRST_SDK_IS_AT_LEAST_T) return;
                if (!BOARD_SDK_IS_AT_LEAST_T) return;
            }
            CodecEncoderTestBase.validateEncodedPSNR(mTestFileMediaType, mTestFile, mEncMediaType,
                    tmpPath, false, false, ACCEPTABLE_WIRELESS_TX_QUALITY);
        }
    }
}
