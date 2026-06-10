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

package android.mediav2.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.media.codec.Flags.FLAG_CODEC_AVAILABILITY;
import static android.media.codec.Flags.codecAvailability;
import static android.media.codec.Flags.codecAvailabilitySupport;
import static android.mediav2.common.cts.CodecTestBase.BOARD_FIRST_SDK_IS_AT_LEAST_202504;
import static android.mediav2.common.cts.CodecTestBase.isHardwareAcceleratedCodec;
import static android.mediav2.cts.CodecResourceUtils.CodecState;
import static android.mediav2.cts.CodecResourceUtils.getCurrentGlobalCodecResources;
import static android.mediav2.cts.CodecResourceUtils.validateGetCodecResources;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderSurfaceTestBase;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Pair;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.VsrTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * This class comprises of tests that validate codec resource availability apis for video
 * transcoders
 */
@RunWith(Parameterized.class)
public class VideoTranscoderAvailabilityTest extends CodecEncoderSurfaceTestBase {
    //private static final String LOG_TAG = VideoTranscoderAvailabilityTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static List<CodecResource> GLOBAL_AVBL_RESOURCES;

    public VideoTranscoderAvailabilityTest(String encoder, String mediaType, String decoder,
            String testFileMediaType, String testFile, EncoderConfigParams encCfgParams,
            int colorFormat, boolean isOutputToneMapped, boolean usePersistentSurface,
            @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, decoder, testFileMediaType, MEDIA_DIR + testFile, encCfgParams,
                colorFormat, isOutputToneMapped, usePersistentSurface, allTestParams);
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}_{3}_{9}")
    public static Collection<Object[]> input() throws IOException {
        final List<Object[]> args = new ArrayList<>(Arrays.asList(new Object[][]{
                // mediaType, testFileMediaType, testFile, bitRate, frameRate, toneMap
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
        int[] maxBFrames = {0};
        boolean[] usePersistentSurfaceStates = {false};
        List<Object[]> expandedArgsList =
                prepareParamsList(args, new ArrayList<>() /* highBitDepth params */, maxBFrames,
                        usePersistentSurfaceStates);
        List<Object[]> finalArgsList = new ArrayList<>();
        for (Object[] arg : expandedArgsList) {
            String encoderName = (String) arg[0];
            String decoderName = (String) arg[2];
            int colorFormat = (int) arg[6];
            if (!isHardwareAcceleratedCodec(encoderName) || !isHardwareAcceleratedCodec(
                    decoderName) || colorFormat != COLOR_FormatSurface) continue;
            finalArgsList.add(arg);
        }
        return finalArgsList;
    }

    @Before
    public void prerequisite() {
        Assume.assumeTrue("Skipping! Requires devices with board_first_sdk >= 202504",
                BOARD_FIRST_SDK_IS_AT_LEAST_202504);
        Assume.assumeTrue("requires codec availability api support", codecAvailability());
        Assume.assumeTrue("requires codec availability api implementation",
                codecAvailabilitySupport());
        GLOBAL_AVBL_RESOURCES = getCurrentGlobalCodecResources();
    }

    /**
     * Briefly, this test verifies the functionality of media codec apis getRequiredResources()
     * and onRequiredResourcesChanged() at various codec states.
     * <p>
     * getRequiredResources() is expected to return illegal state exception in uninitialized
     * state and resources required for current codec configuration in executing state. The test
     * tries this api at various codec states and expects,
     * <ul>
     *     <li>Illegal state exception or, </li>
     *     <li>Resources required for current instance </li>
     * </ul>
     * The test verifies if the globally available resources at any state is in agreement with
     * the codec operational consumption resources. In other words, at any given time, current
     * global available resources + current instance codec resources equals global available
     * resources at the start of the test.
     * <p>
     */
    @LargeTest
    @VsrTest(requirements = {"VSR-4.1-002"})
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @RequiresFlagsEnabled(FLAG_CODEC_AVAILABILITY)
    @ApiTest(apis = {"android.media.MediaCodec#getGloballyAvailableResources",
            "android.media.MediaCodec#getRequiredResources",
            "android.media.MediaCodec.Callback#onRequiredResourcesChanged"})
    public void testSimpleDecodeEncodeFromSurface() throws IOException, InterruptedException {
        mOutputBuff = new OutputManager();
        mOutputBuff.reset();
        setUpSource(mTestFile);
        mDecoder = MediaCodec.createByCodecName(mDecoderName);
        validateGetCodecResources(List.of(Pair.create(mDecoder, CodecState.UNINITIALIZED)),
                GLOBAL_AVBL_RESOURCES, String.format(Locale.getDefault(),
                        "getRequiredResources() succeeded in %s state \n", CodecState.UNINITIALIZED)
                        + mTestEnv + mTestConfig);
        mEncoder = MediaCodec.createByCodecName(mEncoderName);
        validateGetCodecResources(List.of(Pair.create(mEncoder, CodecState.UNINITIALIZED),
                        Pair.create(mDecoder, CodecState.UNINITIALIZED)), GLOBAL_AVBL_RESOURCES,
                String.format(Locale.getDefault(),
                        "getRequiredResources() succeeded, encoder in %s state, decoder in %s "
                                + "state\n",
                        CodecState.UNINITIALIZED, CodecState.UNINITIALIZED) + mTestEnv
                        + mTestConfig);
        configureCodec(mDecoderFormat, mEncoderFormat, true, true);
        validateGetCodecResources(List.of(Pair.create(mEncoder, CodecState.CONFIGURED),
                        Pair.create(mDecoder, CodecState.CONFIGURED)), GLOBAL_AVBL_RESOURCES,
                String.format(Locale.getDefault(),
                        "getRequiredResources() failed, encoder in %s state, decoder in %s state\n",
                        CodecState.CONFIGURED, CodecState.CONFIGURED) + mTestEnv + mTestConfig);
        mEncoder.start();
        validateGetCodecResources(List.of(Pair.create(mEncoder, CodecState.RUNNING),
                        Pair.create(mDecoder, CodecState.CONFIGURED)), GLOBAL_AVBL_RESOURCES,
                String.format(Locale.getDefault(),
                        "getRequiredResources() failed, encoder in %s state, decoder in %s state\n",
                        CodecState.RUNNING, CodecState.CONFIGURED) + mTestEnv + mTestConfig);
        mDecoder.start();
        validateGetCodecResources(List.of(Pair.create(mEncoder, CodecState.RUNNING),
                        Pair.create(mDecoder, CodecState.RUNNING)), GLOBAL_AVBL_RESOURCES,
                String.format(Locale.getDefault(),
                        "getRequiredResources() failed, encoder in %s state, decoder in %s state\n",
                        CodecState.RUNNING, CodecState.RUNNING) + mTestEnv + mTestConfig);
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllEncoderOutputs();
        validateGetCodecResources(List.of(Pair.create(mEncoder, CodecState.EOS),
                        Pair.create(mDecoder, CodecState.EOS)), GLOBAL_AVBL_RESOURCES,
                String.format(Locale.getDefault(),
                        "getRequiredResources() failed, encoder in %s state, decoder in %s state\n",
                        CodecState.EOS, CodecState.EOS) + mTestEnv + mTestConfig);
        mDecoder.stop();
        validateGetCodecResources(List.of(Pair.create(mEncoder, CodecState.EOS),
                        Pair.create(mDecoder, CodecState.STOPPED)), GLOBAL_AVBL_RESOURCES,
                String.format(Locale.getDefault(),
                        "getRequiredResources() failed, encoder in %s state, decoder in %s state\n",
                        CodecState.EOS, CodecState.STOPPED) + mTestEnv + mTestConfig);
        mDecoder.reset();
        validateGetCodecResources(List.of(Pair.create(mEncoder, CodecState.EOS),
                        Pair.create(mDecoder, CodecState.UNINITIALIZED)), GLOBAL_AVBL_RESOURCES,
                String.format(Locale.getDefault(),
                        "getRequiredResources() failed, encoder in %s state, decoder in %s state\n",
                        CodecState.EOS, CodecState.UNINITIALIZED) + mTestEnv + mTestConfig);
        mEncoder.stop();
        validateGetCodecResources(List.of(Pair.create(mEncoder, CodecState.STOPPED),
                        Pair.create(mDecoder, CodecState.UNINITIALIZED)), GLOBAL_AVBL_RESOURCES,
                String.format(Locale.getDefault(),
                        "getRequiredResources() failed, encoder in %s state, decoder in %s state\n",
                        CodecState.STOPPED, CodecState.UNINITIALIZED) + mTestEnv + mTestConfig);
        mEncoder.reset();
        validateGetCodecResources(List.of(Pair.create(mEncoder, CodecState.UNINITIALIZED),
                        Pair.create(mDecoder, CodecState.UNINITIALIZED)), GLOBAL_AVBL_RESOURCES,
                String.format(Locale.getDefault(),
                        "getRequiredResources() succeeded, encoder in %s state, decoder in %s "
                                + "state\n",
                        CodecState.UNINITIALIZED, CodecState.UNINITIALIZED) + mTestEnv
                        + mTestConfig);
        mSurface.release();
        mSurface = null;
        mDecoder.release();
        validateGetCodecResources(List.of(Pair.create(mEncoder, CodecState.UNINITIALIZED),
                        Pair.create(mDecoder, CodecState.RELEASED)), GLOBAL_AVBL_RESOURCES,
                String.format(Locale.getDefault(),
                        "getRequiredResources() succeeded, encoder in %s state, decoder in %s "
                                + "state\n",
                        CodecState.UNINITIALIZED, CodecState.RELEASED) + mTestEnv + mTestConfig);
        mEncoder.release();
        validateGetCodecResources(List.of(Pair.create(mEncoder, CodecState.RELEASED),
                        Pair.create(mDecoder, CodecState.RELEASED)), GLOBAL_AVBL_RESOURCES,
                String.format(Locale.getDefault(),
                        "getRequiredResources() succeeded, encoder in %s state, decoder in %s "
                                + "state\n",
                        CodecState.RELEASED, CodecState.RELEASED) + mTestEnv + mTestConfig);
    }
}
