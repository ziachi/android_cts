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

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.media.codec.Flags.FLAG_NULL_OUTPUT_SURFACE;
import static android.media.codec.Flags.apvSupport;

import static com.android.media.extractor.flags.Flags.extractorMp4EnableApv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.ImageFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.ImageSurface;
import android.mediav2.common.cts.OutputManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Test mediacodec api, video decoders and their interactions in surface mode.
 * <p>
 * When video decoders are configured in surface mode, the getOutputImage() returns null. So
 * there is no way to validate the decoded output frame analytically. The tests in this class
 * however ensures that,
 * <ul>
 *     <li> The number of decoded frames are equal to the number of input frames.</li>
 *     <li> The output timestamp list is same as the input timestamp list.</li>
 * </ul>
 * <p>
 * The test verifies all the above needs by running mediacodec in both sync and async mode.
 */
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public class CodecDecoderDetachedSurfaceTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = CodecDecoderDetachedSurfaceTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    // 1 - 2 frames can be dropped during surface change
    private static final int FRAMES_DROPPED_PER_SWITCH = 2;
    private static final int MAX_ACTIVE_SURFACES = 4;
    private static final int IMAGE_SURFACE_QUEUE_SIZE = 3;
    private static final long WAIT_FOR_IMAGE_TIMEOUT_MS = 5;
    private static final int[] BURST_LENGTHS = new int[]{25, 19, 13, 5};

    private final int mBurstLength;

    private int mOutputCountInBursts;
            // current tests decode in burst mode. This field maintains the number of frames
            // decoded in a single burst session
    private final Lock mLock = new ReentrantLock();
    private final int[] mFramesRendered = new int[MAX_ACTIVE_SURFACES];
            // total frames rendered on to output surface
    private final int[] mFramesRenderedExpected = new int[MAX_ACTIVE_SURFACES];
    private int mTotalSurfaceSwitch;
            // exp number of frames to be rendered on output surface
    private boolean mSurfaceAttached = true;
    private int mAttachedSurfaceId;
            // are display surface and codec configured surface same
    private final ArrayList<ImageSurface> mImageSurfaces = new ArrayList<>();
    private final ArrayList<Surface> mSurfaces = new ArrayList<>();

    public CodecDecoderDetachedSurfaceTest(String decoder, String mediaType, String testFile,
            int burstLength, String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
        mBurstLength = burstLength;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = false;
        final boolean needVideo = true;
        // mediaType, test file
        final List<Object[]> exhaustiveArgsList = new ArrayList<>();
        final List<Object[]> args = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, "bbb_340x280_768kbps_30fps_mpeg2.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_340x280_768kbps_30fps_avc.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_520x390_1mbps_30fps_hevc.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, "bbb_128x96_64kbps_12fps_mpeg4.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_H263, "bbb_176x144_128kbps_15fps_h263.3gp"},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_340x280_768kbps_30fps_vp8.webm"},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_340x280_768kbps_30fps_vp9.webm"},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_340x280_768kbps_30fps_av1.mp4"},
                {MediaFormat.MIMETYPE_VIDEO_AV1,
                        "bikes_qcif_color_bt2020_smpte2086Hlg_bt2020Ncl_fr_av1.mp4"},
        }));
        // P010 support was added in Android T, hence limit the following tests to Android T and
        // above
        if (IS_AT_LEAST_T) {
            args.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_520x390_24fps_crf22_avc_10bit.mkv"},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_520x390_24fps_crf22_hevc_10bit.mkv"},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_520x390_24fps_crf22_vp9_10bit.mkv"},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_520x390_24fps_768kbps_av1_10bit.mkv"},
            }));
        }
        if (IS_AT_LEAST_B && apvSupport() && extractorMp4EnableApv()) {
            args.addAll(
                    Arrays.asList(
                            new Object[][] {
                                {
                                    MediaFormat.MIMETYPE_VIDEO_APV,
                                    "pattern_640x480_30fps_16mbps_apv_10bit.mp4"
                                },
                            }));
        }
        for (Object[] arg : args) {
            for (int burstLength : BURST_LENGTHS) {
                Object[] testArgs = new Object[arg.length + 1];
                System.arraycopy(arg, 0, testArgs, 0, arg.length);
                testArgs[arg.length] = burstLength;
                exhaustiveArgsList.add(testArgs);
            }
        }
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    @Before
    public void setUp() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(format);
        checkFormatSupport(mCodecName, mMediaType, false, formatList, null,
                SupportClass.CODEC_OPTIONAL);
        int width = getWidth(format);
        int height = getHeight(format);
        int colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
        for (int i = 0; i < MAX_ACTIVE_SURFACES; i++) {
            ImageSurface sf = new ImageSurface();
            sf.createSurface(width, height,
                    colorFormat == COLOR_FormatYUVP010 ? ImageFormat.YCBCR_P010 :
                            ImageFormat.YUV_420_888, IMAGE_SURFACE_QUEUE_SIZE, i,
                    this::onFrameReceived);
            mImageSurfaces.add(sf);
            mSurfaces.add(sf.getSurface());
        }
    }

    @After
    public void tearDown() {
        mSurfaces.clear();
        for (ImageSurface imgSurface : mImageSurfaces) {
            assertFalse("All buffers are accounted for, but image surface indicates that"
                            + " some frames were dropped. \n" + mTestConfig + mTestEnv,
                    imgSurface.hasQueueOverflowed());
            imgSurface.release();
        }
        mImageSurfaces.clear();
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mOutputCountInBursts = 0;
        mTotalSurfaceSwitch = 0;
        Arrays.fill(mFramesRendered, 0);
        Arrays.fill(mFramesRenderedExpected, 0);
    }

    @Override
    protected void doWork(int frameLimit) throws InterruptedException, IOException {
        if (mIsCodecInAsyncMode) {
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS
                    && mOutputCountInBursts < frameLimit) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        // <id, info> corresponds to output callback. Handle it accordingly
                        dequeueOutput(bufferID, info);
                    } else {
                        // <id, null> corresponds to input callback. Handle it accordingly
                        enqueueInput(bufferID);
                    }
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!mSawInputEOS && mOutputCountInBursts < frameLimit) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
                int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueInput(inputBufferId);
                }
            }
        }
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
            mOutputCountInBursts++;
            if (mSurfaceAttached) mFramesRenderedExpected[mAttachedSurfaceId]++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, mSurface != null);
        if (info.size > 0) {
            getAllImagesInRenderQueue(mAttachedSurfaceId, 1);
        }
    }

    private boolean onFrameReceived(ImageSurface.ImageAndAttributes obj) {
        if (obj.mImage != null) {
            mLock.lock();
            try {
                mFramesRendered[obj.mImageBoundToSurfaceId] += 1;
            } finally {
                mLock.unlock();
            }
        }
        return true;
    }

    private void getAllImagesInRenderQueue(int surfaceId, int targetFrames) {
        boolean hasImage;
        int framesReceived = 0;
        do {
            try (Image image = mImageSurfaces.get(surfaceId).getImage(WAIT_FOR_IMAGE_TIMEOUT_MS)) {
                onFrameReceived(new ImageSurface.ImageAndAttributes(image, surfaceId));
                if (image != null) {
                    hasImage = true;
                    framesReceived++;
                } else {
                    hasImage = false;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } while (hasImage && framesReceived < targetFrames);
    }

    private void getAllImagesInRenderQueue() {
        for (int i = 0; i < mImageSurfaces.size(); i++) {
            getAllImagesInRenderQueue(i, Integer.MAX_VALUE);
        }
    }

    private void validateTestRun() {
        int totalFramesRenderedExpected = 0;
        int totalFramesRendered = 0;
        for (int i = 0; i < mFramesRendered.length; i++) {
            assertTrue(String.format(Locale.getDefault(),
                            "Number of frames rendered to output surface is exceeding the total "
                                    + "frames released to the surface. Exp / got : %d / %d \n",
                            mFramesRenderedExpected[i], mFramesRendered[i]) + mTestConfig
                            + mTestEnv, mFramesRendered[i] <= mFramesRenderedExpected[i]);
            totalFramesRenderedExpected += mFramesRenderedExpected[i];
            totalFramesRendered += mFramesRendered[i];
        }
        Assume.assumeTrue(String.format(Locale.getDefault(),
                        "Number of frames rendered to output surface is much lesser than the "
                                + "total frames released to the surface. Exp / got : %d / %d \n",
                        totalFramesRenderedExpected, totalFramesRendered) + mTestConfig + mTestEnv,
                totalFramesRenderedExpected - totalFramesRendered
                        <= FRAMES_DROPPED_PER_SWITCH * mTotalSurfaceSwitch);
    }

    /**
     * At the start of the test #MAX_ACTIVE_SURFACES number of surfaces are instantiated. The
     * first surface is used for codec configuration. After decoding/rendering 'n' frames,
     * the output surface associated with codec session is switched using the api
     * MediaCodec#setOutputSurface. This is continued till end of sequence. The test checks if
     * the number of frames rendered to each surface is as expected.
     */
    @ApiTest(apis = {"android.media.MediaCodec#setOutputSurface"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSetOutputSurface() throws IOException, InterruptedException {
        boolean[] boolStates = {true, false};
        final long pts = 0;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        MediaFormat format = setUpSource(mTestFile);
        mCodec = MediaCodec.createByCodecName(mCodecName);
        mOutputBuff = new OutputManager();
        for (boolean isAsync : boolStates) {
            mImageSurface = mImageSurfaces.get(0); // use first surface instance for configuration
            mSurface = mSurfaces.get(0);
            mOutputBuff.reset();
            mExtractor.seekTo(pts, mode);
            configureCodec(format, isAsync, isAsync /* use crypto configure api */,
                    false /* isEncoder */);
            mCodec.start();
            int surfaceId = MAX_ACTIVE_SURFACES - 1;
            while (!mSawInputEOS) {
                mOutputCountInBursts = 0;
                mCodec.setOutputSurface(mSurfaces.get(surfaceId)); // switch surface periodically
                mTotalSurfaceSwitch++;
                mImageSurface = mImageSurfaces.get(surfaceId);
                mSurface = mSurfaces.get(surfaceId);
                mAttachedSurfaceId = surfaceId;
                doWork(mBurstLength);
                getAllImagesInRenderQueue();
                surfaceId += 1;
                surfaceId = surfaceId % MAX_ACTIVE_SURFACES;
            }
            queueEOS();
            waitForAllOutputs();
            endCodecSession(mCodec);
            getAllImagesInRenderQueue();
            validateTestRun();
        }
        mCodec.release();
        mExtractor.release();
    }

    /**
     * At the start of the test #MAX_ACTIVE_SURFACES number of surfaces are instantiated. The
     * codec is configured with flag CONFIGURE_FLAG_DETACHED_SURFACE. At the start of the decode
     * a surface is attached to the component using MediaCodec#setOutputSurface. After
     * decoding/rendering 'n' frames, the output surface is detached using the api
     * MediaCodec#detachSurface. After decoding/rendering 'n' frames, a new surface is attached.
     * This is continued till end of sequence. The test checks if the number of frames rendered
     * to each surface at the end of session is as expected.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName =
            "VanillaIceCream")
    @RequiresFlagsEnabled(FLAG_NULL_OUTPUT_SURFACE)
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_DetachedSurface",
            "android.media.MediaCodec#detachOutputSurface",
            "android.media.MediaCodec#CONFIGURE_FLAG_DETACHED_SURFACE"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testFeatureDetachedSurface() throws IOException, InterruptedException {
        Assume.assumeTrue("codec: " + mCodecName + " does not support FEATURE_DetachedSurface",
                isFeatureSupported(mCodecName, mMediaType,
                        MediaCodecInfo.CodecCapabilities.FEATURE_DetachedSurface));
        boolean[] boolStates = {true, false};
        final long pts = 0;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        MediaFormat format = setUpSource(mTestFile);
        mCodec = MediaCodec.createByCodecName(mCodecName);
        mOutputBuff = new OutputManager();
        for (boolean isAsync : boolStates) {
            mOutputBuff.reset();
            mSurface = null;
            mExtractor.seekTo(pts, mode);
            configureCodec(format, isAsync, isAsync /* use crypto configure api */,
                    false /* isEncoder */, MediaCodec.CONFIGURE_FLAG_DETACHED_SURFACE);
            mCodec.start();
            boolean attachSurface = true;
            int surfaceId = 0;
            while (!mSawInputEOS) {
                mOutputCountInBursts = 0;
                if (attachSurface) {
                    mCodec.setOutputSurface(mSurfaces.get(surfaceId));
                    mTotalSurfaceSwitch++;
                    mImageSurface = mImageSurfaces.get(surfaceId);
                    mSurface = mSurfaces.get(surfaceId);
                    mSurfaceAttached = true;
                    mAttachedSurfaceId = surfaceId;
                    surfaceId += 1;
                    surfaceId = surfaceId % MAX_ACTIVE_SURFACES;
                } else {
                    mCodec.detachOutputSurface();
                    mSurfaceAttached = false;
                }
                attachSurface = !attachSurface;
                doWork(mBurstLength);
                getAllImagesInRenderQueue();
            }
            queueEOS();
            waitForAllOutputs();
            endCodecSession(mCodec);
            getAllImagesInRenderQueue();
            validateTestRun();
        }
        mCodec.release();
        mExtractor.release();
    }

    /**
     * If the component does not support FEATURE_DetachedSurface the test checks if passing the
     * flag CONFIGURE_FLAG_DETACHED_SURFACE during configure throws an exception. Also, in normal
     * running state, call to detachOutputSurface() must throw exception. Vice versa, if the
     * component supports FEATURE_DetachedSurface, flag CONFIGURE_FLAG_DETACHED_SURFACE and
     * detachOutputSurface() must work as documented. Additionally, after detaching output
     * surface, the application releases the surface and expects normal decode functionality.
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName =
            "VanillaIceCream")
    @RequiresFlagsEnabled(FLAG_NULL_OUTPUT_SURFACE)
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_DetachedSurface",
            "android.media.MediaCodec#CONFIGURE_FLAG_DETACHED_SURFACE"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testDetachOutputSurface() throws IOException, InterruptedException {
        boolean hasSupport = isFeatureSupported(mCodecName, mMediaType,
                MediaCodecInfo.CodecCapabilities.FEATURE_DetachedSurface);
        boolean[] boolStates = {true, false};
        final long pts = 0;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        MediaFormat format = setUpSource(mTestFile);
        mCodec = MediaCodec.createByCodecName(mCodecName);
        mOutputBuff = new OutputManager();
        for (boolean isAsync : boolStates) {
            mOutputBuff.reset();
            mSurface = null;
            mExtractor.seekTo(pts, mode);
            if (hasSupport) {
                try {
                    configureCodec(format, isAsync, isAsync /* use crypto configure api */,
                            false /* isEncoder */, MediaCodec.CONFIGURE_FLAG_DETACHED_SURFACE);
                } catch (IllegalArgumentException e) {
                    fail(mCodecName + " advertises support for feature: FEATURE_DetachedSurface but"
                            + " configuration fails with MediaCodec"
                            + ".CONFIGURE_FLAG_DETACHED_SURFACE \n" + mTestConfig + mTestEnv);
                }
                mCodec.start();

                // attach a surface and decode few frames
                int surfaceId = 0;
                mOutputCountInBursts = 0;
                mCodec.setOutputSurface(mSurfaces.get(surfaceId));
                mTotalSurfaceSwitch++;
                mImageSurface = mImageSurfaces.get(surfaceId);
                mSurface = mSurfaces.get(surfaceId);
                mSurfaceAttached = true;
                mAttachedSurfaceId = surfaceId;
                doWork(mBurstLength); // decode
                getAllImagesInRenderQueue();

                // detach surface
                try {
                    mCodec.detachOutputSurface();
                } catch (IllegalStateException e) {
                    fail(mCodecName + " advertises support for feature: FEATURE_DetachedSurface but"
                            + " detachOutputSurface() fails with " + e + "\n" + mTestConfig
                            + mTestEnv);
                }

                // decode few frames without attaching surface
                mOutputCountInBursts = 0;
                mSurfaceAttached = false;
                doWork(mBurstLength);
                getAllImagesInRenderQueue();

                // release surface
                mImageSurfaces.get(surfaceId).release();
                mImageSurfaces.remove(surfaceId);
                mSurfaces.remove(surfaceId);

                // attach new surface and decode few frames
                mOutputCountInBursts = 0;
                mCodec.setOutputSurface(mSurfaces.get(surfaceId));
                mTotalSurfaceSwitch++;
                mImageSurface = mImageSurfaces.get(surfaceId);
                mSurface = mSurfaces.get(surfaceId);
                mSurfaceAttached = true;
                mAttachedSurfaceId = surfaceId;
                doWork(mBurstLength);
                getAllImagesInRenderQueue();
            } else {
                try {
                    configureCodec(format, isAsync, isAsync /* use crypto configure api */,
                            false /* isEncoder */, MediaCodec.CONFIGURE_FLAG_DETACHED_SURFACE);
                    fail(mCodecName + " does not advertise support for feature:"
                            + " FEATURE_DetachedSurface but configuration succeeds with MediaCodec"
                            + ".CONFIGURE_FLAG_DETACHED_SURFACE \n" + mTestConfig + mTestEnv);
                } catch (IllegalArgumentException ignored) {
                }
                mImageSurface = mImageSurfaces.get(0); // use first instance for configuration
                mSurface = mSurfaces.get(0);
                configureCodec(format, isAsync, isAsync /* use crypto configure api */,
                        false /* isEncoder */);

                mCodec.start();
                mOutputCountInBursts = 0;
                doWork(mBurstLength);
                getAllImagesInRenderQueue();
                try {
                    mCodec.detachOutputSurface();
                    fail(mCodecName + " has no support for feature: FEATURE_DetachedSurface but"
                            + " detachOutputSurface() succeeds \n" + mTestConfig + mTestEnv);
                } catch (IllegalStateException ignored) {
                }
            }
            queueEOS();
            waitForAllOutputs();
            endCodecSession(mCodec);
            getAllImagesInRenderQueue();
            validateTestRun();
        }
        mCodec.release();
        mExtractor.release();
    }
}
