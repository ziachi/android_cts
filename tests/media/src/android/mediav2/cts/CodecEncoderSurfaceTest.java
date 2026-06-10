/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderSurfaceTestBase;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

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
 * Test mediacodec api, video encoders and their interactions in surface mode.
 * <p>
 * The test decodes an input clip to surface. This decoded output is fed as input to encoder.
 * Assuming no frame drops, the test expects,
 * <ul>
 *     <li>The number of encoded frames to be identical to number of frames present in input clip
 *     .</li>
 *     <li>As encoders are expected to give consistent output for a given input and configuration
 *     parameters, the test checks for consistency across runs. For now, this attribute is not
 *     strictly enforced in this test.</li>
 *     <li>The encoder output timestamps list should be identical to decoder input timestamp list
 *     .</li>
 * </ul>
 * <p>
 * The output of encoder is further verified by computing PSNR to check for obvious visual
 * artifacts.
 * <p>
 * The test runs mediacodec in synchronous and asynchronous mode.
 */
@RunWith(Parameterized.class)
public class CodecEncoderSurfaceTest extends CodecEncoderSurfaceTestBase {
    private static final String LOG_TAG = CodecEncoderSurfaceTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private final int mFrameLimit;

    private final ArrayList<String> mTmpFiles = new ArrayList<>();

    static {
        System.loadLibrary("ctsmediav2codecencsurface_jni");

        android.os.Bundle args = InstrumentationRegistry.getArguments();
        CodecTestBase.mediaTypeSelKeys = args.getString(CodecTestBase.MEDIA_TYPE_SEL_KEY);
    }

    public CodecEncoderSurfaceTest(String encoder, String mediaType, String decoder,
            String testFileMediaType, String testFile, EncoderConfigParams encCfgParams,
            int colorFormat, boolean isOutputToneMapped, boolean usePersistentSurface,
            @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, decoder, testFileMediaType, MEDIA_DIR + testFile, encCfgParams,
                colorFormat, isOutputToneMapped, usePersistentSurface, allTestParams);
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
        final List<Object[]> args = new ArrayList<>(Arrays.asList(new Object[][]{
                // mediaType, testFileMediaType, testFile, bitRate, frameRate, toneMap
                {MediaFormat.MIMETYPE_VIDEO_H263, MediaFormat.MIMETYPE_VIDEO_H263,
                        "bbb_176x144_128kbps_15fps_h263.3gp", 128000, 15, false},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, MediaFormat.MIMETYPE_VIDEO_MPEG4,
                        "bbb_128x96_64kbps_12fps_mpeg4.mp4", 64000, 12, false},
                {MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_AVC,
                        "bbb_cif_768kbps_30fps_avc.mp4", 512000, 30, false},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC,
                        "bbb_cif_768kbps_30fps_avc.mp4", 512000, 30, false},
                {MediaFormat.MIMETYPE_VIDEO_VP8, MediaFormat.MIMETYPE_VIDEO_AVC,
                        "bbb_cif_768kbps_30fps_avc.mp4", 512000, 30, false},
                {MediaFormat.MIMETYPE_VIDEO_VP9, MediaFormat.MIMETYPE_VIDEO_AVC,
                        "bbb_cif_768kbps_30fps_avc.mp4", 512000, 30, false},
                {MediaFormat.MIMETYPE_VIDEO_AV1, MediaFormat.MIMETYPE_VIDEO_AVC,
                        "bbb_cif_768kbps_30fps_avc.mp4", 512000, 30, false},
        }));

        final List<Object[]> argsHighBitDepth = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_AVC,
                        "cosmat_520x390_24fps_crf22_avc_10bit.mkv", 512000, 30, false},
                {MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_AVC,
                        "cosmat_520x390_24fps_crf22_avc_10bit.mkv", 512000, 30, true},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
                        "cosmat_520x390_24fps_crf22_hevc_10bit.mkv", 512000, 30, false},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
                        "cosmat_520x390_24fps_crf22_hevc_10bit.mkv", 512000, 30, true},
                {MediaFormat.MIMETYPE_VIDEO_VP9, MediaFormat.MIMETYPE_VIDEO_VP9,
                        "cosmat_520x390_24fps_crf22_vp9_10bit.mkv", 512000, 30, false},
                {MediaFormat.MIMETYPE_VIDEO_VP9, MediaFormat.MIMETYPE_VIDEO_VP9,
                        "cosmat_520x390_24fps_crf22_vp9_10bit.mkv", 512000, 30, true},
                {MediaFormat.MIMETYPE_VIDEO_AV1, MediaFormat.MIMETYPE_VIDEO_AV1,
                        "cosmat_520x390_24fps_768kbps_av1_10bit.mkv", 512000, 30, false},
                {MediaFormat.MIMETYPE_VIDEO_AV1, MediaFormat.MIMETYPE_VIDEO_AV1,
                        "cosmat_520x390_24fps_768kbps_av1_10bit.mkv", 512000, 30, true},
        }));

        if (IS_AT_LEAST_B && apvSupport() && muxerMp4EnableApv() && extractorMp4EnableApv()) {
            argsHighBitDepth.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_APV, MediaFormat.MIMETYPE_VIDEO_APV,
                            "pattern_640x480_30fps_16mbps_apv_10bit.mp4", 10000000, 30, false},
            }));
        }

        int[] maxBFrames = {0, 2};
        boolean[] usePersistentSurfaceStates = {true, false};
        return prepareParamsList(args, argsHighBitDepth, maxBFrames, usePersistentSurfaceStates);
    }

    /**
     * Checks if the component under test can encode from surface properly. The test runs
     * mediacodec in both synchronous and asynchronous mode. The test feeds the encoder input
     * surface with output of decoder. Assuming no frame drops, the number of output frames from
     * encoder should be identical to number of input frames to decoder. Also the timestamps
     * should be identical. As encoder output is deterministic, the test expects consistent
     * output in all runs. The output is written to a file using muxer. This file is validated
     * for PSNR to check if the encoding happened successfully with out any obvious artifacts.
     */
    @CddTest(requirements = {"2.2.2", "2.3.2", "2.5.2"})
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatSurface",
            "android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUVP010",
            "android.media.MediaFormat#KEY_COLOR_TRANSFER_REQUEST"})
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleEncodeFromSurface() throws IOException, InterruptedException {
        boolean muxOutput = true;
        if (mEncMediaType.equals(MediaFormat.MIMETYPE_VIDEO_AV1) && CodecTestBase.IS_BEFORE_U) {
            muxOutput = false;
        }
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager(ref.getSharedErrorLogs());
        boolean[] boolStates = {true, false};
        int count = 0;
        String tmpPath = null;
        boolean saveToMem = false; /* TODO(b/149027258) */
        for (boolean isAsync : boolStates) {
            if (count == 0) {
                tmpPath = getTempFilePath(mEncCfgParams.mInputBitDepth > 8 ? "10bit" : "");
                mTmpFiles.add(tmpPath);
            }
            encodeToMemory(isAsync, false, saveToMem, (count == 0 ? ref : test), muxOutput,
                    tmpPath, mFrameLimit);
            // TODO:(b/149027258) Remove false once output is validated across runs
            if (false) {
                if (count != 0 && !ref.equals(test)) {
                    fail("Encoder output is not consistent across runs \n" + mTestConfig + mTestEnv
                            + test.getErrMsg());
                }
            }
            count++;
        }
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

    private native boolean nativeTestSimpleEncode(String encoder, String decoder, String mediaType,
            String testFile, String testFileMediaType, String muxFile, int colorFormat,
            boolean usePersistentSurface, String cfgParams, String separator, StringBuilder retMsg,
            int frameLimit);

    /**
     * Test is similar to {@link #testSimpleEncodeFromSurface()} but uses ndk api
     */
    @CddTest(requirements = {"2.2.2", "2.3.2", "2.5.2"})
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatSurface",
            "android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUVP010"})
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleEncodeFromSurfaceNative() throws IOException, InterruptedException {
        // TODO(b/281661171) Update native tests to encode for tone mapped output
        assumeFalse("tone mapping tests are skipped in native mode", mIsOutputToneMapped);
        String tmpPath = null;
        if (!mEncMediaType.equals(MediaFormat.MIMETYPE_VIDEO_AV1) || CodecTestBase.IS_AT_LEAST_U) {
            tmpPath = getTempFilePath(mEncCfgParams.mInputBitDepth > 8 ? "10bit" : "");
            mTmpFiles.add(tmpPath);
        }
        int colorFormat = mDecoderFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT, -1);
        boolean isPass = nativeTestSimpleEncode(mEncoderName, mDecoderName, mEncMediaType,
                mTestFile, mTestFileMediaType, tmpPath, colorFormat, mUsePersistentSurface,
                EncoderConfigParams.serializeMediaFormat(mEncoderFormat),
                EncoderConfigParams.TOKEN_SEPARATOR, mTestConfig, mFrameLimit);
        assertTrue(mTestConfig.toString(), isPass);
        if (tmpPath != null) {
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
