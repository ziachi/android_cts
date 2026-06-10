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

import static android.media.codec.Flags.apvSupport;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_ALL;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static com.android.media.extractor.flags.Flags.extractorMp4EnableApv;

import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.OutputManager;
import android.util.Log;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;

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

/**
 * Verifies component and framework behaviour for resolution change in bytebuffer mode. The
 * resolution change is not seamless (AdaptivePlayback) but done via reconfigure.
 * <p>
 * The reconfiguring of media codec component happens at various points :-
 * <ul>
 *     <li>After initial configuration (stopped state).</li>
 *     <li>In running state, before queueing any input.</li>
 *     <li>In running state, after queuing n frames.</li>
 *     <li>In eos state.</li>
 * </ul>
 * In eos state,
 * <ul>
 *     <li>reconfigure with same clip.</li>
 *     <li>reconfigure with different clip (different resolution).</li>
 * </ul>
 * <p>
 * In all situations (pre-reconfigure or post-reconfigure), the test expects the output
 * timestamps to be strictly increasing. The reconfigure call makes the output received
 * non-deterministic even for a given input. Hence, besides timestamp checks, no additional
 * validation is done for outputs received before reconfigure. Post reconfigure, the decode
 * begins from a sync frame. So the test expects consistent output and this needs to be
 * identical to the reference.
 * <p>
 * The test runs mediacodec in synchronous and asynchronous mode.
 * <p>
 * During reconfiguration, the mode of operation is toggled. That is, if first configure
 * operates the codec in sync mode, then next configure operates the codec in async mode and
 * so on.
 */
@RunWith(Parameterized.class)
public class CodecDecoderReconfigureTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = CodecDecoderReconfigureTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    private final String mReconfigFile;
    private final SupportClass mSupportRequirements;
    private MediaFormat mFormat;
    private MediaFormat mReconfigFormat;

    public CodecDecoderReconfigureTest(String decoder, String mediaType, String testFile,
            String reconfigFile, SupportClass supportRequirements, String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
        mReconfigFile = MEDIA_DIR + reconfigFile;
        mSupportRequirements = supportRequirements;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = true;
        final boolean needVideo = true;
        // mediaType, testClip, reconfigureTestClip, SupportClass
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
            {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_1ch_8kHz_lame_cbr.mp3",
                "bbb_2ch_44kHz_lame_vbr.mp3", CODEC_ALL},
            {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_2ch_44kHz_lame_cbr.mp3",
                "bbb_1ch_16kHz_lame_vbr.mp3", CODEC_ALL},
            {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "bbb_1ch_16kHz_16kbps_amrwb.3gp",
                "bbb_1ch_16kHz_23kbps_amrwb.3gp", CODEC_ALL},
            {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "bbb_1ch_8kHz_10kbps_amrnb.3gp",
                "bbb_1ch_8kHz_8kbps_amrnb.3gp", CODEC_ALL},
            {MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_1ch_16kHz_flac.mka", "bbb_2ch_44kHz_flac.mka",
                CODEC_ALL},
            {MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_2ch_44kHz_flac.mka", "bbb_1ch_16kHz_flac.mka",
                CODEC_ALL},
            {MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_1ch_16kHz.wav", "bbb_2ch_44kHz.wav", CODEC_ALL},
            {MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_2ch_44kHz.wav", "bbb_1ch_16kHz.wav", CODEC_ALL},
            {MediaFormat.MIMETYPE_AUDIO_G711_ALAW, "bbb_1ch_8kHz_alaw.wav", "bbb_2ch_8kHz_alaw.wav",
                CODEC_ALL},
            {MediaFormat.MIMETYPE_AUDIO_G711_MLAW, "bbb_1ch_8kHz_mulaw.wav",
                "bbb_2ch_8kHz_mulaw.wav", CODEC_ALL},
            {MediaFormat.MIMETYPE_AUDIO_MSGSM, "bbb_1ch_8kHz_gsm.wav", "bbb_1ch_8kHz_gsm.wav",
                CODEC_ALL},
            {MediaFormat.MIMETYPE_AUDIO_VORBIS, "bbb_1ch_16kHz_vorbis.mka",
                "bbb_2ch_44kHz_vorbis.mka", CODEC_ALL},
            {MediaFormat.MIMETYPE_AUDIO_OPUS, "bbb_2ch_48kHz_opus.mka", "bbb_1ch_48kHz_opus.mka",
                CODEC_ALL},
            {MediaFormat.MIMETYPE_AUDIO_AAC, "bbb_1ch_16kHz_aac.mp4", "bbb_2ch_44kHz_aac.mp4",
                CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_MPEG2, "bbb_340x280_768kbps_30fps_mpeg2.mp4",
                "bbb_520x390_1mbps_30fps_mpeg2.mp4", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_340x280_768kbps_30fps_avc.mp4",
                "bbb_520x390_1mbps_30fps_avc.mp4", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_520x390_1mbps_30fps_hevc.mp4",
                "bbb_340x280_768kbps_30fps_hevc.mp4", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_MPEG4, "bbb_128x96_64kbps_12fps_mpeg4.mp4",
                "bbb_176x144_192kbps_15fps_mpeg4.mp4", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_H263, "bbb_176x144_128kbps_15fps_h263.3gp",
                "bbb_176x144_192kbps_10fps_h263.3gp", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_340x280_768kbps_30fps_vp8.webm",
                "bbb_520x390_1mbps_30fps_vp8.webm", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_340x280_768kbps_30fps_vp9.webm",
                "bbb_520x390_1mbps_30fps_vp9.webm", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_340x280_768kbps_30fps_av1.mp4",
                "bbb_520x390_1mbps_30fps_av1.mp4", CODEC_ALL},
        }));
        // Framework P010 support added with android T.
        // These codecs are not required to support P010, but if they advertise support,
        // we are going to test it.
        if (IS_AT_LEAST_T) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_340x280_24fps_crf22_avc_10bit.mkv",
                    "cosmat_520x390_24fps_crf22_avc_10bit.mkv",
                    CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_340x280_24fps_crf22_hevc_10bit.mkv",
                    "cosmat_520x390_24fps_crf22_hevc_10bit.mkv",
                    CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_340x280_24fps_crf22_vp9_10bit.mkv",
                    "cosmat_520x390_24fps_crf22_vp9_10bit.mkv",
                    CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_340x280_24fps_crf22_avc_10bit.mkv",
                    "bbb_520x390_1mbps_30fps_avc.mp4",
                    CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_340x280_24fps_crf22_hevc_10bit.mkv",
                    "bbb_520x390_1mbps_30fps_hevc.mp4",
                    CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_340x280_24fps_crf22_vp9_10bit.mkv",
                    "bbb_520x390_1mbps_30fps_vp9.webm",
                    CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_520x390_24fps_crf22_avc_10bit.mkv",
                    "bbb_340x280_768kbps_30fps_avc.mp4",
                    CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_520x390_24fps_crf22_hevc_10bit.mkv",
                    "bbb_340x280_768kbps_30fps_hevc.mp4", CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_520x390_24fps_crf22_vp9_10bit.mkv",
                    "bbb_340x280_768kbps_30fps_vp9.webm",
                    CODEC_OPTIONAL},
            }));
        }
        // These codecs are always required to support P010.
        // AV1 10 bit support was mandated as of Android11 CDD 5.3, but we're unable to verify
        // that with framework before T.
        if (IS_AT_LEAST_T) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_340x280_24fps_512kbps_av1_10bit.mkv",
                    "cosmat_520x390_24fps_768kbps_av1_10bit.mkv",
                    CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_340x280_24fps_512kbps_av1_10bit.mkv",
                    "bbb_520x390_1mbps_30fps_av1.mp4",
                    CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_520x390_24fps_768kbps_av1_10bit.mkv",
                    "bbb_340x280_768kbps_30fps_av1.mp4",
                    CODEC_ALL},
            }));
        }
        if (IS_AT_LEAST_B && apvSupport() && extractorMp4EnableApv()) {
            exhaustiveArgsList.addAll(
                    Arrays.asList(
                            new Object[][] {
                                {
                                    MediaFormat.MIMETYPE_VIDEO_APV,
                                    "pattern_640x480_30fps_16mbps_apv_10bit.mp4",
                                    "pattern_1280x720_30fps_30mbps_apv_10bit.mp4",
                                    CODEC_ALL
                                },
                            }));
        }
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, true);
    }

    @Before
    public void setUp() throws IOException {
        mFormat = setUpSource(mTestFile);
        mExtractor.release();
        mReconfigFormat = setUpSource(mReconfigFile);
        mExtractor.release();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(mFormat);
        formatList.add(mReconfigFormat);
        checkFormatSupport(mCodecName, mMediaType, false, formatList, null, mSupportRequirements);
    }

    /**
     * Check description of class {@link CodecDecoderReconfigureTest}
     */
    @ApiTest(apis = "android.media.MediaCodec#configure")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testReconfigure() throws IOException, InterruptedException {
        Assume.assumeTrue("Test needs Android 11", IS_AT_LEAST_R);

        final long startTs = 0;
        final long seekTs = 500000;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        boolean[] boolStates = {true, false};
        decodeToMemory(mTestFile, mCodecName, startTs, mode, Integer.MAX_VALUE);
        OutputManager ref = mOutputBuff;
        decodeToMemory(mReconfigFile, mCodecName, seekTs, mode, Integer.MAX_VALUE);
        OutputManager configRef = mOutputBuff;
        OutputManager test = new OutputManager(ref.getSharedErrorLogs());
        OutputManager configTest = new OutputManager(configRef.getSharedErrorLogs());
        mCodec = MediaCodec.createByCodecName(mCodecName);
        for (boolean isAsync : boolStates) {
            mOutputBuff = test;
            setUpSource(mTestFile);
            mExtractor.seekTo(startTs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            configureCodec(mFormat, isAsync, true, false);
            MediaFormat defFormat = mCodec.getOutputFormat();
            boolean validateFormat = true;
            if (isFormatSimilar(mFormat, defFormat)) {
                if (ENABLE_LOGS) {
                    Log.d("Input format is same as default for format for %s", mCodecName);
                }
                validateFormat = false;
            }

            /* test reconfigure in stopped state */
            reConfigureCodec(mFormat, !isAsync, false, false);
            mCodec.start();

            /* test reconfigure in running state before queuing input */
            reConfigureCodec(mFormat, !isAsync, false, false);
            mCodec.start();
            doWork(23);

            if (mOutputCount != 0) {
                if (validateFormat) {
                    doOutputFormatChecks(defFormat, mFormat);
                }
                validateMetrics(mCodecName, mFormat);
            }

            /* test reconfigure codec in running state */
            reConfigureCodec(mFormat, isAsync, true, false);
            mCodec.start();
            mSaveToMem = true;
            test.reset();
            mExtractor.seekTo(startTs, mode);
            doWork(Integer.MAX_VALUE);
            queueEOS();
            waitForAllOutputs();
            endCodecSession(mCodec);
            if (!ref.equals(test)) {
                fail("Decoder output is not consistent across runs \n" + mTestConfig + mTestEnv
                        + test.getErrMsg());
            }
            if (validateFormat) {
                doOutputFormatChecks(defFormat, mFormat);
            }

            /* test reconfigure codec at eos state */
            reConfigureCodec(mFormat, !isAsync, false, false);
            mCodec.start();
            test.reset();
            mExtractor.seekTo(startTs, mode);
            doWork(Integer.MAX_VALUE);
            queueEOS();
            waitForAllOutputs();
            endCodecSession(mCodec);
            if (!ref.equals(test)) {
                fail("Decoder output is not consistent across runs \n" + mTestConfig + mTestEnv
                        + test.getErrMsg());
            }
            if (validateFormat) {
                doOutputFormatChecks(defFormat, mFormat);
            }
            mExtractor.release();

            /* test reconfigure codec for new file */
            mOutputBuff = configTest;
            setUpSource(mReconfigFile);
            reConfigureCodec(mReconfigFormat, isAsync, false, false);
            if (isFormatSimilar(mReconfigFormat, defFormat)) {
                if (ENABLE_LOGS) {
                    Log.d("Input format is same as default for format for %s", mCodecName);
                }
                validateFormat = false;
            }
            mCodec.start();
            configTest.reset();
            mExtractor.seekTo(seekTs, mode);
            doWork(Integer.MAX_VALUE);
            queueEOS();
            waitForAllOutputs();
            validateMetrics(mCodecName, mReconfigFormat);
            endCodecSession(mCodec);
            if (!configRef.equals(configTest)) {
                fail("Decoder output is not consistent across runs \n" + mTestConfig + mTestEnv
                        + configTest.getErrMsg());
            }
            if (validateFormat) {
                doOutputFormatChecks(defFormat, mReconfigFormat);
            }
            mSaveToMem = false;
            mExtractor.release();
        }
        mCodec.release();
    }
}
