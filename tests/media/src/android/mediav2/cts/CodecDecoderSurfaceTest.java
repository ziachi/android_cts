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
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_ALL;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static com.android.media.extractor.flags.Flags.extractorMp4EnableApv;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.codec.Flags;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.CodecTestActivity;
import android.mediav2.common.cts.OutputManager;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;
import android.view.Surface;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

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
 * Test mediacodec api, video decoders and their interactions in surface mode.
 * <p>
 * When video decoders are configured in surface mode, the getOutputImage() returns null. So
 * there is no way to validate the decoded output frame analytically. The tests in this class
 * however ensures that,
 * <ul>
 *     <li> The number of decoded frames are equal to the number of input frames.</li>
 *     <li> The output timestamp list is same as the input timestamp list.</li>
 *     <li> The timestamp information obtained is consistent with results seen in byte buffer
 *     mode.</li>
 * </ul>
 * <p>
 * The test verifies all the above needs by running mediacodec in both sync and async mode.
 */
@RunWith(Parameterized.class)
public class CodecDecoderSurfaceTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = CodecDecoderSurfaceTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    private final SupportClass mSupportRequirements;

    static {
        System.loadLibrary("ctsmediav2codecdecsurface_jni");
    }

    public CodecDecoderSurfaceTest(String decoder, String mediaType, String testFile,
            SupportClass supportRequirements, String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
        mSupportRequirements = supportRequirements;
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: " +
                    info.size + " timestamp: " + info.presentationTimeUs);
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, mSurface != null);
    }

    private OutputManager decodeAndSavePts(String file, String decoder, long pts, int mode,
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
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        if (IS_Q) {
            Log.i(LOG_TAG, "Android 10: skip checkFormatSupport() for format " + format);
        } else {
            ArrayList<MediaFormat> formatList = new ArrayList<>();
            formatList.add(format);
            checkFormatSupport(mCodecName, mMediaType, false, formatList, null,
                    mSupportRequirements);
        }
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
        setUpSurface(mActivity);
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = false;
        final boolean needVideo = true;
        // mediaType, test file, SupportClass
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, "bbb_340x280_768kbps_30fps_mpeg2.mp4",
                        CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2,
                        "bbb_512x288_30fps_1mbps_mpeg2_interlaced_nob_2fields.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2,
                        "bbb_512x288_30fps_1mbps_mpeg2_interlaced_nob_1field.ts", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_340x280_768kbps_30fps_avc.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_360x640_768kbps_30fps_avc.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_160x1024_1500kbps_30fps_avc.mp4",
                        CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_1280x120_1500kbps_30fps_avc.mp4",
                        CODEC_OPTIONAL},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_520x390_1mbps_30fps_hevc.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, "bbb_128x96_64kbps_12fps_mpeg4.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_H263, "bbb_176x144_128kbps_15fps_h263.3gp", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_340x280_768kbps_30fps_vp8.webm", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_340x280_768kbps_30fps_vp9.webm", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9,
                        "bbb_340x280_768kbps_30fps_split_non_display_frame_vp9.webm", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_340x280_768kbps_30fps_av1.mp4", CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AV1,
                        "bikes_qcif_color_bt2020_smpte2086Hlg_bt2020Ncl_fr_av1.mp4", CODEC_ALL},
        }));
        // Framework P010 support added with android T.
        // These codecs are not required to support P010, but if they advertise support,
        // we are going to test it.
        if (IS_AT_LEAST_T) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_340x280_24fps_crf22_avc_10bit.mkv",
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_340x280_24fps_crf22_hevc_10bit.mkv",
                            CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_340x280_24fps_crf22_vp9_10bit.mkv",
                            CODEC_OPTIONAL},
            }));
        }
        // These codecs are always required to support P010.
        // AV1 10 bit support was mandated as of Android11 CDD 5.3, but we're unable to verify
        // that with framework before T.
        if (IS_AT_LEAST_T) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_340x280_24fps_512kbps_av1_10bit.mkv",
                            CODEC_ALL},
            }));
        }
        if (IS_AT_LEAST_B && apvSupport() && extractorMp4EnableApv()) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][] {
                    {MediaFormat.MIMETYPE_VIDEO_APV, "pattern_640x480_30fps_16mbps_apv_10bit.mp4",
                                    CODEC_OPTIONAL},
            }));
        }
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, true);
    }

    /**
     * Checks if the component under test can decode the test file to surface. The test runs
     * mediacodec in both synchronous and asynchronous mode. It expects consistent output
     * timestamp list in all runs and this list to be identical to the reference list. The
     * reference list is obtained from the same decoder running in byte buffer mode
     */
    @CddTest(requirements = {"2.2.2", "2.3.2", "2.5.2", "5.1.7/C-4-1"})
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatSurface"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecodeToSurface() throws IOException, InterruptedException {
        boolean[] boolStates = {true, false};
        final long pts = 0;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        {
            OutputManager ref = decodeAndSavePts(mTestFile, mCodecName, pts, mode,
                    Integer.MAX_VALUE);
            OutputManager test = new OutputManager(ref.getSharedErrorLogs());
            MediaFormat format = setUpSource(mTestFile);
            format.removeKey(MediaFormat.KEY_COLOR_FORMAT);
            mCodec = MediaCodec.createByCodecName(mCodecName);
            mOutputBuff = test;
            mActivity.setScreenParams(getWidth(format), getHeight(format), true);
            for (boolean isAsync : boolStates) {
                mOutputBuff.reset();
                mExtractor.seekTo(pts, mode);
                configureCodec(format, isAsync, true, false);
                mCodec.start();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                endCodecSession(mCodec);
                if (!(mIsInterlaced ? ref.equalsDequeuedOutput(test) : ref.equals(test))) {
                    fail("Decoder output in surface mode does not match with output in bytebuffer "
                            + "mode \n" + mTestConfig + mTestEnv + test.getErrMsg());
                }
                int colorFormat = getOutputFormat().getInteger(MediaFormat.KEY_COLOR_FORMAT);
                assertTrue("In surface mode, components MUST default to the color format"
                        + " optimized for hardware display", colorFormat == COLOR_FormatSurface);
            }
            mCodec.release();
            mExtractor.release();
        }
    }

    /**
     * Checks if the component under test can decode the test file to surface, while that
     * surface is repeatedly detached and reattached. The test runs mediacodec in both
     * synchronous and asynchronous mode.
     *
     * TODO: actually verify that the correct buffers are rendered to the surface
     * TODO: expand this test to use 2 output surfaces
     */
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_DetachedSurface",
                     "android.media.MediaCodec#detachOutputSurface",
                     "android.media.MediaCodec#CONFIGURE_FLAG_DETACHED_SURFACE"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @RequiresFlagsEnabled(Flags.FLAG_NULL_OUTPUT_SURFACE)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    public void testDetachAndReattachSurface() throws IOException, InterruptedException {
        boolean[] boolStates = {true, false};
        final long pts = 0;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        {
            mOutputBuff = new OutputManager();
            MediaFormat format = setUpSource(mTestFile);
            mCodec = MediaCodec.createByCodecName(mCodecName);
            CodecCapabilities caps = mCodec.getCodecInfo()
                    .getCapabilitiesForType(format.getString(MediaFormat.KEY_MIME));
            boolean detachable = caps.isFeatureSupported(CodecCapabilities.FEATURE_DetachedSurface);

            mActivity.setScreenParams(getWidth(format), getHeight(format), true);
            for (boolean isAsync : boolStates) {
                for (boolean startDetached : boolStates) {
                    mOutputBuff.reset();
                    mExtractor.seekTo(pts, mode);
                    if (startDetached) {
                        try {
                            configureCodecInDetachedMode(
                                    format, isAsync,
                                    isAsync /* cryptoCallAndSignalEosWithLastFrame */);
                            if (!detachable) {
                                fail("configure with CONFIGURE_FLAG_DETACHED_SURFACE did not throw "
                                        + "even though FEATURE_DetachedSurface is not advertised\n"
                                        + mTestConfig + mTestEnv);
                            }
                        } catch (IllegalArgumentException e) {
                            if (!detachable) {
                                // we got the exception that we expected and we can end this run
                                continue;
                            }
                            // we got the exception that we expected and we can and this run
                            fail("configure with CONFIGURE_FLAG_DETACHED_SURFACE failed even "
                                    + "though FEATURE_DetachedSurface is advertised\n"
                                    + "Exception is" + e
                                    + mTestConfig + mTestEnv);
                        }
                    } else {
                        configureCodec(
                                format, isAsync, isAsync /* cryptoCallAndSignalEosWithLastFrame */,
                                false /* isEncoder */);
                    }
                    mCodec.start();

                    // TODO: test various burst size of frame outputs on and off the surface
                    // TODO: switch surface based on number of frames output vs input
                    final int toggleSequenceLength = 30; // number of frames before surface change
                    while (!mSawInputEOS) {
                        doWork(toggleSequenceLength);
                        try {
                            mCodec.detachOutputSurface();
                            if (!detachable) {
                                fail("detachOutputSurface() did not throw even though "
                                        + "FEATURE_DetachedSurface is not advertised\n"
                                        + mTestConfig + mTestEnv);
                            }
                        } catch (IllegalStateException e) {
                            if (!detachable) {
                                // we got the exception that we expected and we can end this run
                                break;
                            }
                            fail("detachOutputSurface() failed even though "
                                    + "FEATURE_DetachedSurface is advertised\n"
                                    + "Exception is" + e
                                    + mTestConfig + mTestEnv);
                        }
                        if (!mSawInputEOS) {
                            doWork(toggleSequenceLength);
                        }
                        mCodec.setOutputSurface(mSurface);
                    }
                    queueEOS();
                    waitForAllOutputs();
                    endCodecSession(mCodec);
                }
            }
            mCodec.release();
            mExtractor.release();
        }
    }

    /**
     * Checks component and framework behaviour to flush API when the codec is operating in
     * surface mode.
     * <p>
     * While the component is decoding the test clip to surface, mediacodec flush() is called.
     * The flush API is called at various points :-
     * <ul>
     *     <li>In running state but before queueing any input (might have to resubmit csd as they
     *     may not have been processed).</li>
     *     <li>In running state, after queueing 1 frame.</li>
     *     <li>In running state, after queueing n frames.</li>
     *     <li>In eos state.</li>
     * </ul>
     * <p>
     * In all situations (pre-flush or post-flush), the test expects the output timestamps to be
     * strictly increasing. The flush call makes the output received non-deterministic even for a
     * given input. Hence, besides timestamp checks, no additional validation is done for outputs
     * received before flush. Post flush, the decode begins from a sync frame. So the test
     * expects consistent output and this needs to be identical to the reference. The reference
     * is obtained from the same decoder running in byte buffer mode.
     * <p>
     * The test runs mediacodec in synchronous and asynchronous mode.
     */
    @ApiTest(apis = {"android.media.MediaCodec#flush"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testFlush() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        mCsdBuffers.clear();
        for (int i = 0; ; i++) {
            String csdKey = "csd-" + i;
            if (format.containsKey(csdKey)) {
                mCsdBuffers.add(format.getByteBuffer(csdKey));
            } else break;
        }
        final long pts = 500000;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        boolean[] boolStates = {true, false};
        {
            OutputManager ref = decodeAndSavePts(mTestFile, mCodecName, pts, mode,
                    Integer.MAX_VALUE);
            OutputManager test = new OutputManager(ref.getSharedErrorLogs());
            mOutputBuff = test;
            setUpSource(mTestFile);
            mCodec = MediaCodec.createByCodecName(mCodecName);
            mActivity.setScreenParams(getWidth(format), getHeight(format), false);
            for (boolean isAsync : boolStates) {
                if (isAsync) continue;  // TODO(b/147576107)
                mExtractor.seekTo(0, mode);
                configureCodec(format, isAsync, true, false);
                mCodec.start();

                /* test flush in running state before queuing input */
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                queueCodecConfig(); /* flushed codec too soon after start, resubmit csd */

                doWork(1);
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                queueCodecConfig(); /* flushed codec too soon after start, resubmit csd */

                mExtractor.seekTo(0, mode);
                test.reset();
                doWork(23);
                if (!test.isPtsStrictlyIncreasing(mPrevOutputPts)) {
                    fail("Output timestamps are not strictly increasing \n" + mTestConfig + mTestEnv
                            + test.getErrMsg());
                }

                /* test flush in running state */
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                test.reset();
                mExtractor.seekTo(pts, mode);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                if (!(mIsInterlaced ? ref.equalsDequeuedOutput(test) : ref.equals(test))) {
                    fail("Decoder output in surface mode does not match with output in bytebuffer "
                            + "mode \n" + mTestConfig + mTestEnv + test.getErrMsg());
                }

                /* test flush in eos state */
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
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
            }
            mCodec.release();
            mExtractor.release();
        }
    }

    private native boolean nativeTestSimpleDecode(String decoder, Surface surface, String mediaType,
            String testFile, String refFile, int colorFormat, float rmsError, long checksum,
            StringBuilder retMsg);

    /**
     * Tests is similar to {@link #testSimpleDecodeToSurface()} but uses ndk api
     */
    @CddTest(requirements = {"2.2.2", "2.3.2", "2.5.2", "5.1.7/C-4-1"})
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatSurface"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecodeToSurfaceNative() throws IOException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        mActivity.setScreenParams(getWidth(format), getHeight(format), false);
        boolean isPass = nativeTestSimpleDecode(mCodecName, mSurface, mMediaType, mTestFile,
                null, format.getInteger(MediaFormat.KEY_COLOR_FORMAT), -1.0f, 0L,
                mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }

    private native boolean nativeTestFlush(String decoder, Surface surface, String mediaType,
            String testFile, int colorFormat, StringBuilder retMsg);

    /**
     * Test is similar to {@link #testFlush()} but uses ndk api
     */
    @ApiTest(apis = {"android.media.MediaCodec#flush"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testFlushNative() throws IOException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        mActivity.setScreenParams(getWidth(format), getHeight(format), true);
        boolean isPass = nativeTestFlush(mCodecName, mSurface, mMediaType, mTestFile,
                format.getInteger(MediaFormat.KEY_COLOR_FORMAT), mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }
}
