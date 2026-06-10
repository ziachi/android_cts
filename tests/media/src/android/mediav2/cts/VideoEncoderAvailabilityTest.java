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
import static android.mediav2.cts.CodecResourceUtils.CodecState;
import static android.mediav2.cts.CodecResourceUtils.LHS_RESOURCE_GE;
import static android.mediav2.cts.CodecResourceUtils.RHS_RESOURCE_GE;
import static android.mediav2.cts.CodecResourceUtils.compareResources;
import static android.mediav2.cts.CodecResourceUtils.computeConsumption;
import static android.mediav2.cts.CodecResourceUtils.getCurrentGlobalCodecResources;
import static android.mediav2.cts.CodecResourceUtils.validateGetCodecResources;
import static android.mediav2.cts.VideoDecoderAvailabilityTest.MIN_UTILIZATION_THRESHOLD;
import static android.mediav2.cts.VideoDecoderAvailabilityTest.estimateVideoSizeFromPerformancePoint;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.InputSurface;
import android.mediav2.common.cts.OutputManager;
import android.opengl.GLES20;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Surface;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.VsrTest;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Wrapper class for trying and testing encoder components in surface mode.
 */
class CodecEncoderGLSurface extends CodecTestBase {
    private final int[][] mColorBars = new int[][]{
            {66, 133, 244},
            {219, 68, 55},
            {244, 180, 0},
            {15, 157, 88},
            {186, 85, 211},
            {119, 136, 153},
            {225, 179, 120},
            {224, 204, 151},
            {236, 121, 154},
            {159, 2, 81},
            {120, 194, 87}
    };

    protected final EncoderConfigParams mEncCfgParams;

    private Surface mInpSurface;
    private InputSurface mEGLWindowInpSurface;
    private int mLatency;
    private boolean mReviseLatency;

    ArrayList<MediaCodec.BufferInfo> mInfoListEnc;

    private final int mColorBarWidth;

    public CodecEncoderGLSurface(String codecName, String mediaType,
            EncoderConfigParams encCfgParams, String allTestParams) {
        super(codecName, mediaType, allTestParams);
        mEncCfgParams = encCfgParams;
        mLatency = 0;
        mReviseLatency = false;
        mInfoListEnc = new ArrayList<>();
        int barWidth = (mEncCfgParams.mWidth + mColorBars.length - 1) / mColorBars.length;
        // aligning color bands to 2 is done because, during chroma subsampling of rgb24 to yuv420p,
        // simple skipping alternate samples or bilinear filter would have same effect.
        mColorBarWidth = (barWidth % 2 == 0) ? barWidth : barWidth + 1;
        assertTrue(mColorBarWidth >= 16);
    }

    @After
    public void tearDownCodecEncoderGLSurface() {
        if (mEGLWindowInpSurface != null) {
            mEGLWindowInpSurface.release();
            mEGLWindowInpSurface = null;
        }
        if (mInpSurface != null) {
            mInpSurface.release();
            mInpSurface = null;
        }
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mLatency = 0;
        mReviseLatency = false;
        mInfoListEnc.clear();
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (info.size > 0) {
            if (mSaveToMem) {
                ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
                MediaCodec.BufferInfo copy = new MediaCodec.BufferInfo();
                copy.set(mOutputBuff.getOutStreamSize(), info.size, info.presentationTimeUs,
                        info.flags);
                mInfoListEnc.add(copy);
                mOutputBuff.saveToMemory(buf, info);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                mOutputBuff.saveOutPTS(info.presentationTimeUs);
                mOutputCount++;
            }
        }
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }

    @Override
    protected void enqueueInput(int bufferIndex) throws IOException {
        throw new RuntimeException(
                "For encoders in surface mode, queueInputBuffer() is not to be called");
    }

    private long computePresentationTime(int frameIndex) {
        return frameIndex * 1000000L / mEncCfgParams.mFrameRate;
    }

    private void generateSurfaceFrame() {
        GLES20.glViewport(0, 0, mEncCfgParams.mWidth, mEncCfgParams.mHeight);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        for (int i = 0; i < mColorBars.length; i++) {
            int startX = i * mColorBarWidth;
            int endX = Math.min(startX + mColorBarWidth, mEncCfgParams.mWidth);
            GLES20.glScissor(startX, 0, endX, mEncCfgParams.mHeight);
            GLES20.glClearColor(mColorBars[i][0] / 255.0f, mColorBars[i][1] / 255.0f,
                    mColorBars[i][2] / 255.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }
    }

    private void tryEncoderOutput() throws InterruptedException {
        if (!mAsyncHandle.hasSeenError() && !mSawOutputEOS) {
            while (mReviseLatency) {
                mAsyncHandle.waitOnFormatChange();
                mReviseLatency = false;
                int actualLatency = mAsyncHandle.getOutputFormat()
                        .getInteger(MediaFormat.KEY_LATENCY, mLatency);
                if (mLatency < actualLatency) {
                    mLatency = actualLatency;
                    return;
                }
            }
            Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getOutput();
            if (element != null) {
                dequeueOutput(element.first, element.second);
            }
        }
    }

    @Override
    protected void configureCodec(MediaFormat format, boolean isAsync,
            boolean cryptoCallAndSignalEosWithLastFrame, boolean isEncoder) {
        if (!isAsync) {
            throw new RuntimeException("CodecEncoderGLSurface methods are written for asynchronous"
                    + " mode of operation, update the implementation or use async mode");
        }
        if (cryptoCallAndSignalEosWithLastFrame) {
            throw new RuntimeException(
                    "For encoder surface tests, eos called is made separately using the api "
                            + "MediaCodec.signalEndOfInputStream()");
        }
        if (!isEncoder) {
            throw new RuntimeException("expects isEncoder parameter to be true, received false");
        }
        configureCodecCommon(format, true, false, true, 0);
        mCodec.configure(format, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
        if (mCodec.getInputFormat().containsKey(MediaFormat.KEY_LATENCY)) {
            mReviseLatency = true;
            mLatency = mCodec.getInputFormat().getInteger(MediaFormat.KEY_LATENCY);
        }
        mInpSurface = mCodec.createInputSurface();
        assertTrue("Surface is not valid \n", mInpSurface.isValid());
        mEGLWindowInpSurface = new InputSurface(mInpSurface, false, false);
    }

    @Override
    protected void validateTestState() {
        super.validateTestState();
        if (!mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts)) {
            fail("Output timestamps are not strictly increasing \n" + mTestConfig + mTestEnv
                    + mOutputBuff.getErrMsg());
        }
        if (!mOutputBuff.isOutPtsListIdenticalToInpPtsList(true)) {
            fail("Input pts list and Output pts list are not identical \n" + mTestConfig
                    + mTestEnv + mOutputBuff.getErrMsg());
        }
    }

    @Override
    protected void waitForAllOutputs() throws InterruptedException {
        while (!mAsyncHandle.hasSeenError() && !mSawOutputEOS) {
            tryEncoderOutput();
        }
        validateTestState();
    }

    @Override
    protected void queueEOS() {
        if (!mAsyncHandle.hasSeenError() && !mSawInputEOS) {
            mCodec.signalEndOfInputStream();
            mSawInputEOS = true;
        }
    }

    @Override
    protected void doWork(int frameLimit) throws InterruptedException {
        while (!mAsyncHandle.hasSeenError() && !mSawInputEOS &&
                mInputCount < frameLimit) {
            if (mInputCount - mOutputCount > mLatency) {
                tryEncoderOutput();
            }
            long pts = computePresentationTime(mInputCount);
            mEGLWindowInpSurface.makeCurrent();
            generateSurfaceFrame();
            mEGLWindowInpSurface.setPresentationTime(pts * 1000);
            mEGLWindowInpSurface.swapBuffers();
            mOutputBuff.saveInPTS(pts);
            mInputCount++;
        }
    }

    public void launchInstance() throws IOException {
        CodecAsyncHandlerResource asyncHandleResource = new CodecAsyncHandlerResource();
        mAsyncHandle = asyncHandleResource;
        mCodec = MediaCodec.createByCodecName(mCodecName);
        mOutputBuff = new OutputManager();
        MediaFormat format = mEncCfgParams.getFormat();
        format.setInteger(MediaFormat.KEY_PRIORITY, 0);
        configureCodec(format, true, false, true);
        mCodec.start();
    }

    public void stopInstance() {
        mCodec.stop();
    }

    public void releaseInstance() {
        mCodec.release();
        tearDownCodecEncoderGLSurface();
    }
}

/**
 * This class comprises of tests that validate codec resource availability apis for video encoders
 */
@RunWith(Parameterized.class)
public class VideoEncoderAvailabilityTest extends CodecEncoderGLSurface {
    private static final String LOG_TAG = VideoEncoderAvailabilityTest.class.getSimpleName();
    private static List<CodecResource> GLOBAL_AVBL_RESOURCES;

    public VideoEncoderAvailabilityTest(String encoder, String mediaType,
            EncoderConfigParams cfgParams, String allTestParams) {
        super(encoder, mediaType, cfgParams, allTestParams);
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

    private static EncoderConfigParams getVideoEncoderCfgParam(String mediaType, int width,
            int height, int frameRate, int bitRate) {
        return new EncoderConfigParams.Builder(mediaType).setWidth(width).setHeight(height)
                .setFrameRate(frameRate).setBitRate(bitRate).setColorFormat(COLOR_FormatSurface)
                .build();
    }

    private static EncoderConfigParams getH263CfgParams() {
        return getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_H263, 176, 144, 12, 64000);
    }

    private static EncoderConfigParams getMpeg4CfgParams() {
        return getVideoEncoderCfgParam(MediaFormat.MIMETYPE_VIDEO_MPEG4, 176, 144, 12, 64000);
    }

    private static EncoderConfigParams getCif30fps1MbpsCfgParams(String mediaType) {
        return getVideoEncoderCfgParam(mediaType, 352, 288, 30, 1000000);
    }

    private static EncoderConfigParams getAvcCfgParams() {
        return getCif30fps1MbpsCfgParams(MediaFormat.MIMETYPE_VIDEO_AVC);
    }

    private static EncoderConfigParams getHevcCfgParams() {
        return getCif30fps1MbpsCfgParams(MediaFormat.MIMETYPE_VIDEO_HEVC);
    }

    private static EncoderConfigParams getVp8CfgParams() {
        return getCif30fps1MbpsCfgParams(MediaFormat.MIMETYPE_VIDEO_VP8);
    }

    private static EncoderConfigParams getVp9CfgParams() {
        return getCif30fps1MbpsCfgParams(MediaFormat.MIMETYPE_VIDEO_VP9);
    }

    private static EncoderConfigParams getAv1CfgParams() {
        return getCif30fps1MbpsCfgParams(MediaFormat.MIMETYPE_VIDEO_AV1);
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(
                new Object[][]{
                        {MediaFormat.MIMETYPE_VIDEO_H263, getH263CfgParams()},
                        {MediaFormat.MIMETYPE_VIDEO_MPEG4, getMpeg4CfgParams()},
                        {MediaFormat.MIMETYPE_VIDEO_AVC, getAvcCfgParams()},
                        {MediaFormat.MIMETYPE_VIDEO_HEVC, getHevcCfgParams()},
                        {MediaFormat.MIMETYPE_VIDEO_VP8, getVp8CfgParams()},
                        {MediaFormat.MIMETYPE_VIDEO_VP9, getVp9CfgParams()},
                        {MediaFormat.MIMETYPE_VIDEO_AV1, getAv1CfgParams()},
                }));
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false,
                ComponentClass.HARDWARE);
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
    public void testSimpleEncode() throws IOException, InterruptedException {
        CodecAsyncHandlerResource asyncHandleResource = new CodecAsyncHandlerResource();
        mAsyncHandle = asyncHandleResource;
        mCodec = MediaCodec.createByCodecName(mCodecName);
        validateGetCodecResources(List.of(Pair.create(mCodec, CodecState.UNINITIALIZED)),
                GLOBAL_AVBL_RESOURCES, String.format(Locale.getDefault(),
                        "getRequiredResources() succeeded in %s state \n", CodecState.UNINITIALIZED)
                        + mTestEnv + mTestConfig);
        MediaFormat format = mEncCfgParams.getFormat();
        List<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        Assume.assumeTrue("Codec: " + mCodecName + " doesn't support format: " + format,
                areFormatsSupported(mCodecName, mMediaType, formats));
        mOutputBuff = new OutputManager();
        configureCodec(format, true, false, true);
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

    private List<EncoderConfigParams> getCfgsCoveringMaxFrameRate(String mediaType, int width,
            int height, int frameRate, int maxFrameRate) {
        List<EncoderConfigParams> cfgs = new ArrayList<>();
        int frameRateOffset = 0;
        do {
            int currFrameRate = Math.min(frameRate, maxFrameRate - frameRateOffset);
            EncoderConfigParams param =
                    new EncoderConfigParams.Builder(mediaType).setWidth(width).setHeight(height)
                            .setFrameRate(frameRate).setColorFormat(COLOR_FormatSurface).build();
            frameRateOffset += currFrameRate;
            cfgs.add(param);
        } while (frameRateOffset < maxFrameRate);
        return cfgs;
    }

    private List<List<EncoderConfigParams>> getCodecTestFormatList(String codecName,
            String mediaType) {
        return VideoDecoderAvailabilityTest.getCodecTestFormatList(codecName, mediaType,
                (videoSize, maxFrameRate) -> getCfgsCoveringMaxFrameRate(mediaType,
                        videoSize.getWidth(), videoSize.getHeight(), 30, maxFrameRate));
    }

    private List<List<EncoderConfigParams>> getCodecTestFormatListNoPerfPoints(String codecName,
            String mediaType) {
        return VideoDecoderAvailabilityTest.getCodecTestFormatListNoPerfPoints(codecName, mediaType,
                config -> getCfgsCoveringMaxFrameRate(mediaType, config.mWidth, config.mHeight, 30,
                        config.mMaxFrameRate));
    }

    private void validateMaxInstances(String codecName, String mediaType) {
        List<List<EncoderConfigParams>> testableParams =
                getCodecTestFormatList(codecName, mediaType);
        if (testableParams == null) {
            testableParams = getCodecTestFormatListNoPerfPoints(codecName, mediaType);
        }
        Assume.assumeNotNull("formats to configure codec unavailable", testableParams);
        List<CodecEncoderGLSurface> codecs = new ArrayList<>();
        CodecEncoderGLSurface codec = null;
        int numInstances;
        List<CodecResource> lastGlobalResources;
        List<CodecResource> currentGlobalResources;
        MediaFormat lastFormat = null;
        MediaFormat currentFormat = null;
        List<CodecResource> lastGlobalResourcesForFormat = null;
        List<CodecResource> currentGlobalResourcesForFormat = null;
        StringBuilder testLogs = new StringBuilder();
        int maxInstances = getMaxSupportedInstances(codecName, mediaType);
        for (List<EncoderConfigParams> params : testableParams) {
            numInstances = 0;
            lastGlobalResources = getCurrentGlobalCodecResources();
            while (numInstances < maxInstances) {
                try {
                    EncoderConfigParams configParam =
                            numInstances < params.size() ? params.get(numInstances) : params.get(0);
                    codec = new CodecEncoderGLSurface(codecName, mediaType, configParam,
                            mAllTestParams);
                    codec.launchInstance();
                    codecs.add(codec);
                    numInstances++;
                    codec = null;
                    currentGlobalResources = getCurrentGlobalCodecResources();
                    int result = compareResources(lastGlobalResources, currentGlobalResources,
                            testLogs);
                    Assert.assertEquals("creating new instance did not reduce resources"
                                    + " available \n" + testLogs + mTestEnv + mTestConfig,
                            LHS_RESOURCE_GE, result);
                    lastGlobalResources = currentGlobalResources;
                    if (numInstances == 1) {
                        currentGlobalResourcesForFormat = currentGlobalResources;
                        currentFormat = configParam.getFormat();
                    }
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
                        codec.releaseInstance();
                        codec = null;
                    }
                }
            }
            for (int i = 0; i < codecs.size(); ++i) {
                Log.d(LOG_TAG, "release codec #" + i);
                lastGlobalResources = getCurrentGlobalCodecResources();
                codecs.get(i).stopInstance();
                codecs.get(i).releaseInstance();
                List<CodecResource> currGlobalResources = getCurrentGlobalCodecResources();
                int result = compareResources(lastGlobalResources, currGlobalResources, testLogs);
                Assert.assertEquals("releasing a codec instance did not increase resources"
                                + " available \n" + testLogs + mTestEnv + mTestConfig,
                        RHS_RESOURCE_GE, result);
            }
            codecs.clear();
            if (lastGlobalResourcesForFormat != null) {
                int result = compareResources(lastGlobalResourcesForFormat,
                        currentGlobalResourcesForFormat, testLogs);
                Assert.assertEquals("format : " + (lastFormat == null ? "empty" : lastFormat)
                        + " is expected to consume more resources than format : " + currentFormat
                        + testLogs + mTestEnv + mTestConfig, RHS_RESOURCE_GE, result);
            }
            lastGlobalResourcesForFormat = currentGlobalResourcesForFormat;
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
        validateMaxInstances(mCodecName, mMediaType);
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
        List<MediaCodecInfo.VideoCapabilities.PerformancePoint> pps =
                vcaps.getSupportedPerformancePoints();
        Assume.assumeFalse(mCodecName + " codec did not advertise any performance points",
                pps == null || pps.isEmpty());
        for (MediaCodecInfo.VideoCapabilities.PerformancePoint pp : pps) {
            Size videoSize = estimateVideoSizeFromPerformancePoint(pp);
            EncoderConfigParams configParam =
                    new EncoderConfigParams.Builder(mMediaType).setWidth(videoSize.getWidth())
                            .setHeight(videoSize.getHeight()).setFrameRate(pp.getMaxFrameRate())
                            .setColorFormat(COLOR_FormatSurface).build();
            CodecEncoderGLSurface codec =
                    new CodecEncoderGLSurface(mCodecName, mMediaType, configParam, mAllTestParams);
            codec.launchInstance();
            List<CodecResource> usedResources = getCurrentGlobalCodecResources();
            double consumption = computeConsumption(globalResources, usedResources);
            codec.stopInstance();
            codec.releaseInstance();
            if (consumption < MIN_UTILIZATION_THRESHOLD) {
                Assert.fail("For performance point " + pp + " and codec : " + mCodecName
                        + " max resources consumed is expected to be at least "
                        + MIN_UTILIZATION_THRESHOLD + "% but got " + consumption + "%");
            }
        }
    }
}
