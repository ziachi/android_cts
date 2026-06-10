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

import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_ALL;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.CodecTestActivity;
import android.mediav2.common.cts.OutputManager;
import android.util.Log;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Checks component and framework behaviour for resolution change in surface mode. The
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
 * identical to the reference. The reference is obtained from the same decoder running in
 * byte buffer mode.
 * <p>
 * The test runs mediacodec in synchronous and asynchronous mode.
 * <p>
 * During reconfiguration, the mode of operation is toggled. That is, if first configure
 * operates the codec in sync mode, then next configure operates the codec in async mode and
 * so on.
 */
@RunWith(Parameterized.class)
public class CodecDecoderSurfaceReconfigureTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = CodecDecoderSurfaceReconfigureTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    private final String mReconfigFile;
    private final SupportClass mSupportRequirements;
    private MediaFormat mFormat;
    private MediaFormat mReconfigFormat;

    public CodecDecoderSurfaceReconfigureTest(String decoder, String mediaType, String testFile,
            String reconfigFile, SupportClass supportRequirements, String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
        mReconfigFile = MEDIA_DIR + reconfigFile;
        mSupportRequirements = supportRequirements;
    }

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
    }

    protected OutputManager decodeAndSavePts(String file, String decoder, long pts, int mode,
            int frameLimit) throws IOException, InterruptedException {
        CodecDecoderTestBase cdtbA = new CodecDecoderTestBase(mCodecName, mMediaType, null,
                mAllTestParams);
        cdtbA.decodeToMemory(file, decoder, new OutputManager(), false, pts, mode, frameLimit,
                false, true);
        return cdtbA.getOutputManager();
    }

    @Rule
    public ActivityScenarioRule<CodecTestActivity> mActivityRule =
            new ActivityScenarioRule<>(CodecTestActivity.class);

    @Before
    public void setUp() throws IOException, InterruptedException {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
        setUpSurface(mActivity);
        mFormat = setUpSource(mTestFile);
        mExtractor.release();
        mReconfigFormat = setUpSource(mReconfigFile);
        mExtractor.release();
        if (IS_Q) {
            Log.i(LOG_TAG, "Android 10: skip checkFormatSupport() for format " + mFormat);
        } else {
            ArrayList<MediaFormat> formatList = new ArrayList<>();
            formatList.add(mFormat);
            formatList.add(mReconfigFormat);
            checkFormatSupport(mCodecName, mMediaType, false, formatList, null,
                    mSupportRequirements);
        }
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = false;
        final boolean needVideo = true;
        // mediaType, test file, reconfig test file, SupportClass
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
            {MediaFormat.MIMETYPE_VIDEO_MPEG2, "bbb_340x280_768kbps_30fps_mpeg2.mp4",
                "bbb_520x390_1mbps_30fps_mpeg2.mp4", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_MPEG2,
                "bbb_512x288_30fps_1mbps_mpeg2_interlaced_nob_2fields.mp4",
                "bbb_520x390_1mbps_30fps_mpeg2.mp4", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_MPEG2,
                "bbb_512x288_30fps_1mbps_mpeg2_interlaced_nob_1field.ts",
                "bbb_520x390_1mbps_30fps_mpeg2.mp4", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_340x280_768kbps_30fps_avc.mp4",
                "bbb_520x390_1mbps_30fps_avc.mp4", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_360x640_768kbps_30fps_avc.mp4",
                "bbb_520x390_1mbps_30fps_avc.mp4", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_160x1024_1500kbps_30fps_avc.mp4",
                "bbb_520x390_1mbps_30fps_avc.mp4", CODEC_OPTIONAL},
            {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_1280x120_1500kbps_30fps_avc.mp4",
                "bbb_340x280_768kbps_30fps_avc.mp4", CODEC_OPTIONAL},
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
            {MediaFormat.MIMETYPE_VIDEO_VP9,
                "bbb_340x280_768kbps_30fps_split_non_display_frame_vp9.webm",
                "bbb_520x390_1mbps_30fps_split_non_display_frame_vp9.webm", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_340x280_768kbps_30fps_av1.mp4",
                "bbb_520x390_1mbps_30fps_av1.mp4", CODEC_ALL},
            {MediaFormat.MIMETYPE_VIDEO_AV1,
                "bikes_qcif_color_bt2020_smpte2086Hlg_bt2020Ncl_fr_av1.mp4",
                "bbb_520x390_1mbps_30fps_av1.mp4", CODEC_ALL},
        }));
        // Framework P010 support added with android T.
        // These codecs are not required to support P010, but if they advertise support,
        // we are going to test it.
        if (IS_AT_LEAST_T) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_340x280_24fps_crf22_avc_10bit.mkv",
                    "cosmat_520x390_24fps_crf22_avc_10bit.mkv", CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_340x280_24fps_crf22_hevc_10bit.mkv",
                    "cosmat_520x390_24fps_crf22_hevc_10bit.mkv", CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_340x280_24fps_crf22_vp9_10bit.mkv",
                    "cosmat_520x390_24fps_crf22_vp9_10bit.mkv", CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_340x280_24fps_crf22_avc_10bit.mkv",
                    "bbb_520x390_1mbps_30fps_avc.mp4", CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_340x280_24fps_crf22_hevc_10bit.mkv",
                    "bbb_520x390_1mbps_30fps_hevc.mp4", CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_340x280_24fps_crf22_vp9_10bit.mkv",
                    "bbb_520x390_1mbps_30fps_vp9.webm", CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_520x390_24fps_crf22_avc_10bit.mkv",
                    "bbb_340x280_768kbps_30fps_avc.mp4", CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_520x390_24fps_crf22_hevc_10bit.mkv",
                    "bbb_340x280_768kbps_30fps_hevc.mp4", CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_520x390_24fps_crf22_vp9_10bit.mkv",
                    "bbb_340x280_768kbps_30fps_vp9.webm", CODEC_OPTIONAL},
            }));
        }
        // These codecs are always required to support P010.
        // AV1 10 bit support was mandated as of Android11 CDD 5.3, but we're unable to verify
        // that with framework before T.
        if (IS_AT_LEAST_T) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_340x280_24fps_512kbps_av1_10bit.mkv",
                    "cosmat_520x390_24fps_768kbps_av1_10bit.mkv", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_340x280_24fps_512kbps_av1_10bit.mkv",
                    "bbb_520x390_1mbps_30fps_av1.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_520x390_24fps_768kbps_av1_10bit.mkv",
                    "bbb_340x280_768kbps_30fps_av1.mp4", CODEC_ALL},
            }));
        }
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, true);
    }

    /**
     * Check description of class {@link CodecDecoderSurfaceReconfigureTest}
     */
    @ApiTest(apis = "android.media.MediaCodec#configure")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testReconfigure() throws IOException, InterruptedException {
        Assume.assumeTrue("Test needs Android 11", IS_AT_LEAST_R);
        final long pts = 500000;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;

        boolean[] boolStates = {true, false};
        OutputManager ref = decodeAndSavePts(mTestFile, mCodecName, pts, mode, Integer.MAX_VALUE);
        OutputManager configRef = decodeAndSavePts(mReconfigFile, mCodecName, pts, mode,
                Integer.MAX_VALUE);
        OutputManager test = new OutputManager(ref.getSharedErrorLogs());
        OutputManager configTest = new OutputManager(configRef.getSharedErrorLogs());
        mCodec = MediaCodec.createByCodecName(mCodecName);
        mActivity.setScreenParams(getWidth(mFormat), getHeight(mFormat), false);
        for (boolean isAsync : boolStates) {
            mOutputBuff = test;
            setUpSource(mTestFile);
            mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            configureCodec(mFormat, isAsync, true, false);

            /* test reconfigure in stopped state */
            reConfigureCodec(mFormat, !isAsync, false, false);
            mCodec.start();

            /* test reconfigure in running state before queuing input */
            reConfigureCodec(mFormat, !isAsync, false, false);
            mCodec.start();
            doWork(23);

            /* test reconfigure codec in running state */
            reConfigureCodec(mFormat, isAsync, true, false);
            mCodec.start();
            test.reset();
            mExtractor.seekTo(pts, mode);
            doWork(Integer.MAX_VALUE);
            queueEOS();
            waitForAllOutputs();
            endCodecSession(mCodec);
            if (!(mIsInterlaced ? ref.equalsDequeuedOutput(test) : ref.equals(test))) {
                fail("Decoder output in surface mode does not match with output in bytebuffer "
                        + "mode \n" + mTestConfig + mTestEnv + test.getErrMsg());
            }

            /* test reconfigure codec at eos state */
            reConfigureCodec(mFormat, !isAsync, false, false);
            mCodec.start();
            test.reset();
            mExtractor.seekTo(pts, mode);
            doWork(Integer.MAX_VALUE);
            queueEOS();
            waitForAllOutputs();
            endCodecSession(mCodec);
            if (!(mIsInterlaced ? ref.equalsDequeuedOutput(test) : ref.equals(test))) {
                fail("Decoder output in surface mode does not match with output in bytebuffer "
                        + "mode \n" + mTestConfig + mTestEnv + test.getErrMsg());
            }
            mExtractor.release();

            /* test reconfigure codec for new file */
            mOutputBuff = configTest;
            setUpSource(mReconfigFile);
            mActivity.setScreenParams(getWidth(mReconfigFormat), getHeight(mReconfigFormat),
                    true);
            reConfigureCodec(mReconfigFormat, isAsync, false, false);
            mCodec.start();
            configTest.reset();
            mExtractor.seekTo(pts, mode);
            doWork(Integer.MAX_VALUE);
            queueEOS();
            waitForAllOutputs();
            endCodecSession(mCodec);
            if (!(mIsInterlaced ? configRef.equalsDequeuedOutput(configTest) :
                    configRef.equals(configTest))) {
                fail("Decoder output in surface mode does not match with output in bytebuffer "
                        + "mode \n" + mTestConfig + mTestEnv + configTest.getErrMsg());
            }
            mExtractor.release();
        }
        mCodec.release();
    }
}
