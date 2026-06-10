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

import static android.media.codec.Flags.FLAG_CODEC_AVAILABILITY;
import static android.media.codec.Flags.codecAvailability;
import static android.media.codec.Flags.codecAvailabilitySupport;
import static android.mediav2.cts.AdaptivePlaybackTest.createInputList;
import static android.mediav2.cts.AdaptivePlaybackTest.getSupportedFiles;
import static android.mediav2.cts.CodecResourceUtils.CodecState;
import static android.mediav2.cts.CodecResourceUtils.LHS_RESOURCE_GE;
import static android.mediav2.cts.CodecResourceUtils.RHS_RESOURCE_GE;
import static android.mediav2.cts.CodecResourceUtils.compareResources;
import static android.mediav2.cts.CodecResourceUtils.computeConsumption;
import static android.mediav2.cts.CodecResourceUtils.getCurrentGlobalCodecResources;
import static android.mediav2.cts.CodecResourceUtils.validateGetCodecResources;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecAsyncHandler;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.CodecDynamicTestActivity;
import android.mediav2.common.cts.OutputManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.VsrTest;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Helper class for running mediacodec in asynchronous mode with resource tracking enabled. All
 * mediacodec callback events are registered in this object so that the client can take
 * appropriate action as desired.
 */
@RequiresFlagsEnabled(FLAG_CODEC_AVAILABILITY)
class CodecAsyncHandlerResource extends CodecAsyncHandler {
    private boolean mResourceChangeCbReceived;
    private int mResourceChangeCbCount;

    public CodecAsyncHandlerResource() {
        super();
        mResourceChangeCbReceived = false;
        mResourceChangeCbCount = 0;
    }

    @Override
    public void resetContext() {
        super.resetContext();
        mResourceChangeCbReceived = false;
        mResourceChangeCbCount = 0;
    }

    @Override
    public void onRequiredResourcesChanged(@NonNull MediaCodec codec) {
        mResourceChangeCbReceived = true;
        mResourceChangeCbCount++;
    }

    public boolean hasRequiredResourceChangeCbReceived() {
        return mResourceChangeCbReceived;
    }

    public int getResourceChangeCbCount() {
        return mResourceChangeCbCount;
    }
}

/**
 * This class comprises of tests that validate codec resource availability apis for video decoders
 */
@RunWith(Parameterized.class)
public class VideoDecoderAvailabilityTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = VideoDecoderAvailabilityTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    // Minimum threshold for resource consumption of a codec for a given performance point.
    // The test expects the resource consumption to be at least this value.
    static final int MIN_UTILIZATION_THRESHOLD = 60;
    private static List<CodecResource> GLOBAL_AVBL_RESOURCES;

    private final String[] mSrcFiles;

    private CodecDynamicTestActivity mDynamicActivity;

    @Rule
    public ActivityScenarioRule<CodecDynamicTestActivity> mActivityRule =
            new ActivityScenarioRule<>(CodecDynamicTestActivity.class);

    @After
    public void VideoDecoderAvailabilityTestTearDown() {
        if (mDynamicActivity != null) {
            mDynamicActivity.finish();
            mDynamicActivity = null;
        }
    }

    public VideoDecoderAvailabilityTest(String decoder, String mediaType, String[] srcFiles,
            String allTestParams) {
        super(decoder, mediaType, null, allTestParams);
        mSrcFiles = new String[srcFiles.length];
        for (int i = 0; i < srcFiles.length; i++) {
            mSrcFiles[i] = MEDIA_DIR + srcFiles[i];
        }
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = false;
        final boolean needVideo = true;
        // mediaType, testClip
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(
                new Object[][]{
                        {MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                                "bbb_800x640_768kbps_30fps_avc_2b.mp4",
                                "bbb_800x640_768kbps_30fps_avc_nob.mp4",
                                "bbb_1280x720_1mbps_30fps_avc_2b.mp4",
                                "bbb_640x360_512kbps_30fps_avc_nob.mp4",
                                "bbb_1280x720_1mbps_30fps_avc_nob.mp4",
                                "bbb_640x360_512kbps_30fps_avc_2b.mp4",
                                "bbb_1280x720_1mbps_30fps_avc_nob.mp4",
                                "bbb_640x360_512kbps_30fps_avc_nob.mp4",
                                "bbb_640x360_512kbps_30fps_avc_2b.mp4"}},
                        {MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{
                                "bbb_800x640_768kbps_30fps_hevc_2b.mp4",
                                "bbb_800x640_768kbps_30fps_hevc_nob.mp4",
                                "bbb_1280x720_1mbps_30fps_hevc_2b.mp4",
                                "bbb_640x360_512kbps_30fps_hevc_nob.mp4",
                                "bbb_1280x720_1mbps_30fps_hevc_nob.mp4",
                                "bbb_640x360_512kbps_30fps_hevc_2b.mp4",
                                "bbb_1280x720_1mbps_30fps_hevc_nob.mp4",
                                "bbb_640x360_512kbps_30fps_hevc_nob.mp4",
                                "bbb_640x360_512kbps_30fps_hevc_2b.mp4"}},
                        {MediaFormat.MIMETYPE_VIDEO_VP8, new String[]{
                                "bbb_800x640_768kbps_30fps_vp8.webm",
                                "bbb_1280x720_1mbps_30fps_vp8.webm",
                                "bbb_640x360_512kbps_30fps_vp8.webm"}},
                        {MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{
                                "bbb_800x640_768kbps_30fps_vp9.webm",
                                "bbb_1280x720_1mbps_30fps_vp9.webm",
                                "bbb_640x360_512kbps_30fps_vp9.webm"}},
                        {MediaFormat.MIMETYPE_VIDEO_MPEG4, new String[]{
                                "bbb_128x96_64kbps_12fps_mpeg4.mp4",
                                "bbb_176x144_192kbps_15fps_mpeg4.mp4",
                                "bbb_128x96_64kbps_12fps_mpeg4.mp4"}},
                        {MediaFormat.MIMETYPE_VIDEO_AV1, new String[]{
                                "bbb_800x640_768kbps_30fps_av1.webm",
                                "bbb_1280x720_1mbps_30fps_av1.webm",
                                "bbb_640x360_512kbps_30fps_av1.webm"}},
                        {MediaFormat.MIMETYPE_VIDEO_MPEG2, new String[]{
                                "bbb_800x640_768kbps_30fps_mpeg2_2b.mp4",
                                "bbb_800x640_768kbps_30fps_mpeg2_nob.mp4",
                                "bbb_1280x720_1mbps_30fps_mpeg2_2b.mp4",
                                "bbb_640x360_512kbps_30fps_mpeg2_nob.mp4",
                                "bbb_1280x720_1mbps_30fps_mpeg2_nob.mp4",
                                "bbb_640x360_512kbps_30fps_mpeg2_2b.mp4",
                                "bbb_1280x720_1mbps_30fps_mpeg2_nob.mp4",
                                "bbb_640x360_512kbps_30fps_mpeg2_nob.mp4",
                                "bbb_640x360_512kbps_30fps_mpeg2_2b.mp4"}},
                }));
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false,
                ComponentClass.HARDWARE);
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

    @Override
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
     * In the executing state, the codec shall update the required resources status via
     * callback onRequiredResourcesChanged(). This is also verified.
     */
    @LargeTest
    @VsrTest(requirements = {"VSR-4.1-002"})
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @RequiresFlagsEnabled(FLAG_CODEC_AVAILABILITY)
    @ApiTest(apis = {"android.media.MediaCodec#getGloballyAvailableResources",
            "android.media.MediaCodec#getRequiredResources",
            "android.media.MediaCodec.Callback#onRequiredResourcesChanged"})
    public void testSimpleDecode() throws IOException, InterruptedException {
        CodecAsyncHandlerResource asyncHandleResource = new CodecAsyncHandlerResource();
        mAsyncHandle = asyncHandleResource;
        mCodec = MediaCodec.createByCodecName(mCodecName);
        validateGetCodecResources(List.of(Pair.create(mCodec, CodecState.UNINITIALIZED)),
                GLOBAL_AVBL_RESOURCES, String.format(Locale.getDefault(),
                        "getRequiredResources() succeeded in %s state \n", CodecState.UNINITIALIZED)
                        + mTestEnv + mTestConfig);
        MediaFormat format = setUpSource(mSrcFiles[0]);
        List<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        Assume.assumeTrue("Codec: " + mCodecName + " doesn't support format: " + format,
                areFormatsSupported(mCodecName, mMediaType, formats));
        mOutputBuff = new OutputManager();
        configureCodec(format, true, true, false);
        validateGetCodecResources(List.of(Pair.create(mCodec, CodecState.CONFIGURED)),
                GLOBAL_AVBL_RESOURCES, String.format(Locale.getDefault(),
                        "getRequiredResources() failed in %s state \n", CodecState.CONFIGURED)
                        + mTestEnv + mTestConfig);
        mCodec.start();
        validateGetCodecResources(List.of(Pair.create(mCodec, CodecState.RUNNING)),
                GLOBAL_AVBL_RESOURCES, String.format(Locale.getDefault(),
                        "getRequiredResources() failed in %s state \n", CodecState.RUNNING)
                        + mTestEnv + mTestConfig);
        doWork(10);
        queueEOS();
        waitForAllOutputs();
        validateGetCodecResources(List.of(Pair.create(mCodec, CodecState.EOS)),
                GLOBAL_AVBL_RESOURCES, String.format(Locale.getDefault(),
                        "getRequiredResources() failed in %s state \n", CodecState.EOS)
                        + mTestEnv + mTestConfig);
        mCodec.flush();
        validateGetCodecResources(List.of(Pair.create(mCodec, CodecState.FLUSHED)),
                GLOBAL_AVBL_RESOURCES, String.format(Locale.getDefault(),
                        "getRequiredResources() failed in %s state \n", CodecState.FLUSHED)
                        + mTestEnv + mTestConfig);
        mCodec.start();
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        validateGetCodecResources(List.of(Pair.create(mCodec, CodecState.RUNNING)),
                GLOBAL_AVBL_RESOURCES, String.format(Locale.getDefault(),
                        "getRequiredResources() failed in %s state \n", CodecState.RUNNING)
                        + mTestEnv + mTestConfig);
        doWork(5);
        queueEOS();
        waitForAllOutputs();
        Assert.assertTrue("did not receive callback onRequiredResourcesChanged() from"
                        + " codec\n" + mTestEnv + mTestConfig,
                asyncHandleResource.hasRequiredResourceChangeCbReceived());
        mCodec.stop();
        validateGetCodecResources(List.of(Pair.create(mCodec, CodecState.STOPPED)),
                GLOBAL_AVBL_RESOURCES, String.format(Locale.getDefault(),
                        "getRequiredResources() succeeded in %s state \n", CodecState.STOPPED)
                        + mTestEnv + mTestConfig);
        mCodec.reset();
        validateGetCodecResources(List.of(Pair.create(mCodec, CodecState.UNINITIALIZED)),
                GLOBAL_AVBL_RESOURCES, String.format(Locale.getDefault(),
                        "getRequiredResources() succeeded in %s state \n", CodecState.UNINITIALIZED)
                        + mTestEnv + mTestConfig);
        mCodec.release();
        validateGetCodecResources(List.of(Pair.create(mCodec, CodecState.RELEASED)),
                GLOBAL_AVBL_RESOURCES, String.format(Locale.getDefault(),
                        "getRequiredResources() succeeded in %s state \n", CodecState.RELEASED)
                        + mTestEnv + mTestConfig);
    }

    static Size estimateVideoSizeFromPerformancePoint(PerformancePoint pp) {
        final Size SUBQCIF = new Size(128, 96);
        final Size QCIF = new Size(176, 144);
        final Size SD144P = new Size(256, 144);
        final Size CIFNTSC = new Size(352, 240);
        final Size CIF = new Size(352, 288);
        final Size QVGA = new Size(320, 240);
        final Size SD240P = new Size(426, 240);
        final Size SD360P = new Size(640, 360);
        final Size VGA = new Size(640, 480);
        final Size SDNTSC = new Size(720, 480);
        final Size SDPAL = new Size(720, 576);
        final Size WVGA = new Size(800, 480);
        final Size SD480P = new Size(854, 480);
        final Size HD = new Size(1280, 720);
        final Size HDPAL = new Size(1440, 1080);
        final Size FULLHD = new Size(1920, 1080);
        final Size FULLHD_ALT = new Size(1920, 1088);
        final Size UHD1440P = new Size(2560, 1440);
        final Size UHD = new Size(3840, 2160);
        final Size DC4K = new Size(4096, 2160);
        final Size UHD8K = new Size(7680, 4320);
        final Size[] STANDARD_RES =
                {SUBQCIF, QCIF, SD144P, CIFNTSC, CIF, QVGA, SD240P, SD360P, VGA, SDNTSC, SDPAL,
                        WVGA, SD480P, HD, HDPAL, FULLHD, FULLHD_ALT, UHD1440P, UHD, DC4K, UHD8K};
        Size maxSupportedSize = null;
        long maxResolution = 0;
        for (Size size : STANDARD_RES) {
            if (pp.covers(new PerformancePoint(size.getWidth(), size.getHeight(),
                    pp.getMaxFrameRate()))) {
                long resolution = (long) size.getWidth() * size.getHeight();
                if (resolution > maxResolution) {
                    maxResolution = resolution;
                    maxSupportedSize = size;
                }
            }
        }
        if (maxSupportedSize != null) {
            return maxSupportedSize;
        }
        // if look up is not successful, rely on string parsing to get the desired info
        String info = pp.toString();
        Scanner scanner = new Scanner(info);
        scanner.useDelimiter("[\\(x@]");
        scanner.next(); // skip "PerformancePoint(" part
        int width = scanner.nextInt();
        int height = scanner.nextInt();
        return new Size(width, height);
    }

    static class VideoConfig {
        int mWidth;
        int mHeight;
        int mMaxFrameRate;

        VideoConfig(int width, int height, int maxFrameRate) {
            mWidth = width;
            mHeight = height;
            mMaxFrameRate = maxFrameRate;
        }
    }

    private List<MediaFormat> getFormatsCoveringMaxFrameRate(String mediaType, int width,
            int height, int frameRate, int maxFrameRate) {
        List<MediaFormat> formats = new ArrayList<>();
        int frameRateOffset = 0;
        do {
            int currFrameRate = Math.min(frameRate, maxFrameRate - frameRateOffset);
            MediaFormat format = MediaFormat.createVideoFormat(mediaType, width, height);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, currFrameRate);
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);
            frameRateOffset += currFrameRate;
            formats.add(format);
        } while (frameRateOffset < maxFrameRate);
        return formats;
    }

    public static <T> List<List<T>> getCodecTestFormatList(String codecName, String mediaType,
            BiFunction<Size, Integer, List<T>> formatGenerator) {
        MediaCodecInfo.CodecCapabilities caps = getCodecCapabilities(codecName, mediaType);
        Assert.assertNotNull("received null capabilities for codec : " + codecName, caps);
        MediaCodecInfo.VideoCapabilities vcaps = caps.getVideoCapabilities();
        Assert.assertNotNull("received null video capabilities for codec : " + codecName, vcaps);
        List<PerformancePoint> pps = vcaps.getSupportedPerformancePoints();
        if (pps == null || pps.isEmpty()) {
            Log.d(LOG_TAG, codecName + " did not advertise any performance points");
            return null;
        }
        List<List<T>> testableFormats = new ArrayList<>();
        for (PerformancePoint pp : pps) {
            Size videoSize = estimateVideoSizeFromPerformancePoint(pp);
            int maxFrameRate = pp.getMaxFrameRate();
            testableFormats.add(formatGenerator.apply(videoSize, maxFrameRate));
        }
        return testableFormats;
    }

    public static <T> List<List<T>> getCodecTestFormatListNoPerfPoints(String codecName,
            String mediaType, Function<VideoConfig, List<T>> formatGenerator) {
        MediaCodecInfo.CodecCapabilities caps = getCodecCapabilities(codecName, mediaType);
        Assert.assertNotNull("received null capabilities for codec : " + codecName, caps);
        MediaCodecInfo.VideoCapabilities vcaps = caps.getVideoCapabilities();
        Assert.assertNotNull("received null video capabilities for codec : " + codecName, vcaps);
        int width = vcaps.getSupportedWidths().getUpper();
        int height = vcaps.getSupportedHeightsFor(width).getUpper();
        Range<Double> frameRates = vcaps.getAchievableFrameRatesFor(width, height);
        if (frameRates == null) {
            Log.d(LOG_TAG, String.format(Locale.getDefault(),
                    "%s did not advertise any achievable frame rates for %dx%d", codecName, width,
                    height));
            return null;
        }
        List<List<T>> testableFormats = new ArrayList<>();
        int maxFrameRate = (int) Math.floor(frameRates.getUpper());
        testableFormats.add(formatGenerator.apply(new VideoConfig(width, height, maxFrameRate)));
        return testableFormats;
    }

    private List<List<MediaFormat>> getCodecTestFormatList(String codecName, String mediaType) {
        return getCodecTestFormatList(codecName, mediaType,
                (videoSize, maxFrameRate) -> getFormatsCoveringMaxFrameRate(mediaType,
                        videoSize.getWidth(), videoSize.getHeight(), 30, maxFrameRate));
    }

    private List<List<MediaFormat>> getCodecTestFormatListNoPerfPoints(String codecName,
            String mediaType) {
        return getCodecTestFormatListNoPerfPoints(codecName, mediaType,
                config -> getFormatsCoveringMaxFrameRate(mediaType, config.mWidth, config.mHeight,
                        30, config.mMaxFrameRate));
    }

    private void validateMaxInstances(String codecName, String mediaType) {
        List<List<MediaFormat>> testableFormats =
                getCodecTestFormatList(codecName, mediaType);
        if (testableFormats == null) {
            testableFormats = getCodecTestFormatListNoPerfPoints(codecName, mediaType);
        }
        Assume.assumeNotNull("formats to configure codec unavailable", testableFormats);
        List<MediaCodec> codecs = new ArrayList<>();
        List<Pair<Integer, Surface>> surfaces = new ArrayList<>();
        MediaCodec codec = null;
        int numInstances;
        List<CodecResource> lastGlobalResources;
        List<CodecResource> currentGlobalResources;
        MediaFormat lastFormat = null;
        MediaFormat currentFormat = null;
        List<CodecResource> lastGlobalResourceForFormat = null;
        List<CodecResource> currentGlobalResourcesForFormat = null;
        StringBuilder testLogs = new StringBuilder();
        int maxInstances = getMaxCodecInstances(codecName, mediaType);
        for (List<MediaFormat> formats : testableFormats) {
            numInstances = 0;
            lastGlobalResources = getCurrentGlobalCodecResources();
            while (numInstances < maxInstances) {
                Pair<Integer, Surface> obj = null;
                try {
                    obj = mDynamicActivity.getSurface();
                    if (obj == null) {
                        int index = mDynamicActivity.addSurfaceView();
                        mDynamicActivity.waitTillSurfaceIsCreated(index);
                        obj = mDynamicActivity.getSurface();
                    }
                    codec = MediaCodec.createByCodecName(codecName);
                    MediaFormat configFormat =
                            numInstances < formats.size() ? formats.get(numInstances) :
                                    formats.get(0);
                    codec.configure(configFormat, obj.second, null, 0);
                    codec.start();
                    codecs.add(codec);
                    surfaces.add(obj);
                    numInstances++;
                    codec = null;
                    obj = null;
                    currentGlobalResources = getCurrentGlobalCodecResources();
                    int result = compareResources(lastGlobalResources, currentGlobalResources,
                            testLogs);
                    Assert.assertEquals("creating new instance did not reduce resources"
                                    + " available \n" + testLogs + mTestEnv + mTestConfig,
                            LHS_RESOURCE_GE, result);
                    lastGlobalResources = currentGlobalResources;
                    if (numInstances == 1) {
                        currentGlobalResourcesForFormat = currentGlobalResources;
                        currentFormat = configFormat;
                    }
                } catch (InterruptedException e) {
                    Assert.fail("Got unexpected InterruptedException " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    Assert.fail("Got unexpected IllegalArgumentException " + e.getMessage());
                } catch (IOException e) {
                    Assert.fail("Got unexpected IOException " + e.getMessage());
                } catch (MediaCodec.CodecException e) {
                    // ERROR_INSUFFICIENT_RESOURCE is expected as the test keep creating codecs.
                    // But other exception should be treated as failure.
                    if (e.getErrorCode() == MediaCodec.CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                        Log.d(LOG_TAG, "Got CodecException with ERROR_INSUFFICIENT_RESOURCE.");
                        break;
                    } else {
                        Assert.fail("Unexpected CodecException " + e.getDiagnosticInfo());
                    }
                } finally {
                    if (codec != null) {
                        Log.d(LOG_TAG, "release codec");
                        codec.release();
                        codec = null;
                    }
                    if (obj != null) {
                        mDynamicActivity.markSurface(obj.first, true);
                    }
                }
            }
            for (int i = 0; i < codecs.size(); ++i) {
                Log.d(LOG_TAG, "release codec #" + i);
                lastGlobalResources = getCurrentGlobalCodecResources();
                codecs.get(i).stop();
                codecs.get(i).release();
                List<CodecResource> currGlobalResources = getCurrentGlobalCodecResources();
                int result = compareResources(lastGlobalResources, currGlobalResources, testLogs);
                Assert.assertEquals("releasing a codec instance did not increase resources"
                                + " available \n" + testLogs + mTestEnv + mTestConfig,
                        RHS_RESOURCE_GE, result);
            }
            for (int i = 0; i < surfaces.size(); ++i) {
                Log.d(LOG_TAG, "mark surface usable #" + i);
                mDynamicActivity.markSurface(surfaces.get(i).first, true);
            }
            surfaces.clear();
            codecs.clear();
            if (lastGlobalResourceForFormat != null) {
                int result = compareResources(lastGlobalResourceForFormat,
                        currentGlobalResourcesForFormat, testLogs);
                Assert.assertEquals("format : " + (lastFormat == null ? "empty" : lastFormat)
                        + " is expected to consume more resources than format : " + currentFormat
                        + testLogs + mTestEnv + mTestConfig, RHS_RESOURCE_GE, result);
            }
            lastGlobalResourceForFormat = currentGlobalResourcesForFormat;
            lastFormat = currentFormat;
        }
    }

    /**
     * For a given codec name and media type, the current test sequentially instantiates
     * component instances until the codec exception ERROR_INSUFFICIENT_RESOURCE is raised. Prior
     * to each instantiation, the test records the current global resources. After a successful
     * instantiation, the test again records the global resources and compares the new value with
     * the previous one. The expectation is that the current value MUST be less than or equal to
     * the previous value.
     * <p>
     * During the teardown phase, the test releases components one by one and expects the current
     * global resources to be greater than or equal to the last recorded reference value.
     */
    @LargeTest
    @VsrTest(requirements = {"VSR-4.1-002"})
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @RequiresFlagsEnabled(FLAG_CODEC_AVAILABILITY)
    @ApiTest(apis = {"android.media.MediaCodec#getGloballyAvailableResources",
            "android.media.MediaCodec#getRequiredResources",
            "android.media.MediaCodec.Callback#onRequiredResourcesChanged"})
    public void testConcurrentMaxInstances() {
        mActivityRule.getScenario().onActivity(activity -> mDynamicActivity = activity);
        validateMaxInstances(mCodecName, mMediaType);
    }

    /**
     * During adaptive playback, as the resolution changes, the resources required/consumed will
     * be different. The test expects if the updated resource requirements are indicated to
     * client via callbacks.
     */
    @LargeTest
    @VsrTest(requirements = {"VSR-4.1-002"})
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @Ignore("Skipped for 25Q2 release")
    @RequiresFlagsEnabled(FLAG_CODEC_AVAILABILITY)
    @ApiTest(apis = {"android.media.MediaCodec#getGloballyAvailableResources",
            "android.media.MediaCodec#getRequiredResources",
            "android.media.MediaCodec.Callback#onRequiredResourcesChanged"})
    public void testAdaptivePlayback() throws IOException, InterruptedException {
        boolean hasSupport = isFeatureSupported(mCodecName, mMediaType,
                MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback);
        Assume.assumeTrue("codec: " + mCodecName + " does not support FEATURE_AdaptivePlayback",
                hasSupport);
        List<String> resFiles = getSupportedFiles(mSrcFiles, mCodecName, mMediaType);
        Assume.assumeTrue("none of the given test clips are supported by the codec: "
                + mCodecName, !resFiles.isEmpty());
        mActivityRule.getScenario().onActivity(activity -> mDynamicActivity = activity);
        Pair<Integer, Surface> obj = mDynamicActivity.getSurface();
        if (obj == null) {
            int index = mDynamicActivity.addSurfaceView();
            mDynamicActivity.waitTillSurfaceIsCreated(index);
            obj = mDynamicActivity.getSurface();
            mSurface = obj.second;
        }
        CodecAsyncHandlerResource asyncHandleResource = new CodecAsyncHandlerResource();
        mAsyncHandle = asyncHandleResource;
        ArrayList<MediaFormat> formats = new ArrayList<>();
        int totalSize = 0;
        for (String resFile : resFiles) {
            File file = new File(resFile);
            totalSize += (int) file.length();
        }
        long ptsOffset = 0;
        int buffOffset = 0;
        ArrayList<MediaCodec.BufferInfo> list = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        for (String file : resFiles) {
            Pair<MediaFormat, Long> metadata =
                    createInputList(file, mMediaType, buffer, list, buffOffset, ptsOffset);
            formats.add(metadata.first);
            ptsOffset = metadata.second + 1000000L;
            buffOffset = (list.get(list.size() - 1).offset) + (list.get(list.size() - 1).size);
        }
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        MediaFormat format = formats.get(0);
        mOutputBuff.reset();
        configureCodec(format, true, false, false);
        mCodec.start();
        doWork(buffer, list);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        if (obj != null) {
            mDynamicActivity.markSurface(obj.first, true);
        }
        if (asyncHandleResource.getResourceChangeCbCount() < resFiles.size()) {
            Assert.fail(String.format("number of resource change callbacks received is less than"
                            + " number of files tried in apb test. exp >= %d, got %d \n",
                    resFiles.size(),
                    asyncHandleResource.getResourceChangeCbCount()) + mTestEnv + mTestConfig);
        }
    }

    /**
     * Tests the resource consumption of a codec for various advertised performance points.
     * This test iterates through the supported performance points of a given codec,
     * configures the codec with a video format corresponding to each performance point,
     * starts the codec, and measures the resource consumption. It checks if the consumption
     * meets a minimum threshold.
     */
    @LargeTest
    @VsrTest(requirements = {"VSR-4.1-002"})
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @RequiresFlagsEnabled(FLAG_CODEC_AVAILABILITY)
    @ApiTest(apis = {"android.media.MediaCodec#getGloballyAvailableResources",
            "android.media.MediaCodec#getRequiredResources",
            "android.media.MediaCodec.Callback#onRequiredResourcesChanged"})
    public void testResourceConsumptionForPerfPoints() throws IOException, InterruptedException {
        List<CodecResource> globalResources = getCurrentGlobalCodecResources();
        MediaCodecInfo.CodecCapabilities caps = getCodecCapabilities(mCodecName, mMediaType);
        Assert.assertNotNull("received null capabilities for codec : " + mCodecName, caps);
        MediaCodecInfo.VideoCapabilities vcaps = caps.getVideoCapabilities();
        Assert.assertNotNull("received null video capabilities for codec : " + mCodecName, vcaps);
        List<PerformancePoint> pps = vcaps.getSupportedPerformancePoints();
        Assume.assumeFalse(mCodecName + " did not advertise any performance points",
                pps == null || pps.isEmpty());
        mActivityRule.getScenario().onActivity(activity -> mDynamicActivity = activity);
        for (PerformancePoint pp : pps) {
            Pair<Integer, Surface> obj = mDynamicActivity.getSurface();
            if (obj == null) {
                int index = mDynamicActivity.addSurfaceView();
                mDynamicActivity.waitTillSurfaceIsCreated(index);
                obj = mDynamicActivity.getSurface();
            }
            MediaCodec codec = MediaCodec.createByCodecName(mCodecName);
            Size videoSize = estimateVideoSizeFromPerformancePoint(pp);
            MediaFormat format = MediaFormat.createVideoFormat(mMediaType, videoSize.getWidth(),
                    videoSize.getHeight());
            format.setInteger(MediaFormat.KEY_FRAME_RATE, pp.getMaxFrameRate());
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);
            codec.configure(format, obj.second, null, 0);
            codec.start();
            List<CodecResource> usedResources = getCurrentGlobalCodecResources();
            double consumption = computeConsumption(globalResources, usedResources);
            codec.stop();
            codec.release();
            mDynamicActivity.markSurface(obj.first, true);
            if (consumption < MIN_UTILIZATION_THRESHOLD) {
                Assert.fail("For performance point " + pp + " and codec : " + mCodecName
                        + " max resources consumed is expected to be at least "
                        + MIN_UTILIZATION_THRESHOLD + "% but got " + consumption + "%");
            }
        }
    }
}
