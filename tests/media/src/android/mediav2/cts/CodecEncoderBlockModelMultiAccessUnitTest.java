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

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_MultipleFrames;
import static android.media.codec.Flags.FLAG_LARGE_AUDIO_FRAME_FINISH;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectELD;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectHE;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;
import static android.mediav2.common.cts.MuxerUtils.getMuxerFormatForMediaType;
import static android.mediav2.common.cts.MuxerUtils.getTempFilePath;
import static android.mediav2.cts.AudioEncoderTest.flattenParams;
import static android.mediav2.cts.CodecDecoderMultiAccessUnitTest.getCompressionRatio;

import static com.android.media.codec.flags.Flags.FLAG_LARGE_AUDIO_FRAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.mediav2.common.cts.CodecAsyncHandlerMultiAccessUnits;
import android.mediav2.common.cts.CodecEncoderBlockModelTestBase;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;
import android.util.Pair;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Tests audio encoders support for feature MultipleFrames in block model mode.
 * <p>
 * MultipleFrames feature is optional and is not required to support by all components. If a
 * component supports this feature, then multiple access units are grouped together (demarcated
 * with access unit offsets and timestamps) are sent as input to the component. The components
 * processes the input sent and returns output in a large enough buffer (demarcated with access
 * unit offsets and timestamps). The number of access units that can be grouped is dependent on
 * format keys, KEY_MAX_INPUT_SIZE, KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE.
 * <p>
 * The test runs the component in MultipleFrames block model mode and normal mode and expects same
 * output for a given input.
 **/
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RequiresFlagsEnabled({FLAG_LARGE_AUDIO_FRAME, FLAG_LARGE_AUDIO_FRAME_FINISH})
@RunWith(Parameterized.class)
public class CodecEncoderBlockModelMultiAccessUnitTest extends CodecEncoderBlockModelTestBase {
    private static final String LOG_TAG =
            CodecEncoderBlockModelMultiAccessUnitTest.class.getSimpleName();
    private static final int[][] OUT_SIZE_IN_MS = {
            {1000, 250},  // max out size, threshold batch out size
            {1000, 100},
            {500, 20},
            {100, 100},
            {40, 100}
    };

    private CodecAsyncHandlerMultiAccessUnits mAsyncHandleMultiAccessUnits;
    private int mMaxOutputSizeBytes;

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        List<Object[]> defArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                // mediaType, arrays of bit-rates, sample rate, channel counts, pcm encoding,
                // profile

                // mono testing @ common sample rates, pcm encoding
                {MediaFormat.MIMETYPE_AUDIO_AAC, new int[]{64000}, new int[]{8000, 16000},
                        new int[]{1}, AudioFormat.ENCODING_PCM_16BIT, AACObjectLC},
                {MediaFormat.MIMETYPE_AUDIO_AAC, new int[]{64000}, new int[]{16000},
                        new int[]{1}, AudioFormat.ENCODING_PCM_16BIT, AACObjectHE},
                {MediaFormat.MIMETYPE_AUDIO_AAC, new int[]{64000}, new int[]{16000},
                        new int[]{1}, AudioFormat.ENCODING_PCM_16BIT, AACObjectELD},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, new int[]{64000}, new int[]{8000, 16000},
                        new int[]{1}, AudioFormat.ENCODING_PCM_16BIT, -1},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, new int[]{4750, 12200}, new int[]{8000},
                        new int[]{1}, AudioFormat.ENCODING_PCM_16BIT, -1},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, new int[]{6600, 23850}, new int[]{16000},
                        new int[]{1}, AudioFormat.ENCODING_PCM_16BIT, -1},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, new int[]{0, 7}, new int[]{8000, 16000},
                        new int[]{1}, AudioFormat.ENCODING_PCM_16BIT, -1},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, new int[]{0, 7}, new int[]{8000, 16000},
                        new int[]{1}, AudioFormat.ENCODING_PCM_FLOAT, -1},

                // stereo testing @ common sample rates, pcm encoding
                {MediaFormat.MIMETYPE_AUDIO_AAC, new int[]{128000}, new int[]{44100, 48000},
                        new int[]{2}, AudioFormat.ENCODING_PCM_16BIT, AACObjectLC},
                {MediaFormat.MIMETYPE_AUDIO_AAC, new int[]{128000}, new int[]{44100, 48000},
                        new int[]{2}, AudioFormat.ENCODING_PCM_16BIT, AACObjectHE},
                {MediaFormat.MIMETYPE_AUDIO_AAC, new int[]{128000}, new int[]{44100, 48000},
                        new int[]{2}, AudioFormat.ENCODING_PCM_16BIT, AACObjectELD},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, new int[]{128000}, new int[]{48000},
                        new int[]{2}, AudioFormat.ENCODING_PCM_16BIT, -1},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, new int[]{0, 7}, new int[]{48000, 192000},
                        new int[]{2}, AudioFormat.ENCODING_PCM_16BIT, -1},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, new int[]{0, 7}, new int[]{48000, 192000},
                        new int[]{2}, AudioFormat.ENCODING_PCM_FLOAT, -1},
        }));
        List<Object[]> argsList = flattenParams(defArgsList);
        return prepareParamList(argsList, true, true, false, false, ComponentClass.ALL,
                new String[]{FEATURE_MultipleFrames});
    }

    public CodecEncoderBlockModelMultiAccessUnitTest(String encoder, String mediaType,
            EncoderConfigParams cfgParams, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(encoder, mediaType, new EncoderConfigParams[]{cfgParams}, allTestParams);
        mAsyncHandle = new CodecAsyncHandlerMultiAccessUnits();
    }

    @Before
    public void setUp() throws IOException {
        mActiveEncCfg = mEncCfgParams[0];
        MediaFormat format = mActiveEncCfg.getFormat();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(format);
        checkFormatSupport(mCodecName, mMediaType, true, formatList, null, CODEC_OPTIONAL);
        mActiveRawRes = EncoderInput.getRawResource(mActiveEncCfg);
        assertNotNull("no raw resource found for testing config : " + mActiveEncCfg + mTestConfig
                + mTestEnv, mActiveRawRes);
        Object asyncHandle = mAsyncHandle;
        assertTrue("async handle shall be an instance of CodecAsyncHandlerMultiAccessUnits"
                        + " while testing Feature_MultipleFrames" + mTestConfig + mTestEnv,
                asyncHandle instanceof CodecAsyncHandlerMultiAccessUnits);
        mAsyncHandleMultiAccessUnits = (CodecAsyncHandlerMultiAccessUnits) asyncHandle;
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mMaxOutputSizeBytes = 0;
    }

    private void validateOutputFormat(MediaFormat outFormat) {
        Assert.assertTrue("Output format " + outFormat + " does not contain key "
                        + MediaFormat.KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE + ". \n"
                        + mTestConfig + mTestEnv,
                outFormat.containsKey(MediaFormat.KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE));
        mMaxOutputSizeBytes = outFormat.getInteger(MediaFormat.KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE);
    }

    private void dequeueOutputs(int bufferIndex, ArrayDeque<MediaCodec.BufferInfo> infos) {
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex);
        }
        validateOutputFormat(mCodec.getOutputFormat(bufferIndex));
        MediaCodec.OutputFrame frame = mCodec.getOutputFrame(bufferIndex);
        ByteBuffer buf = frame.getLinearBlock() != null ? frame.getLinearBlock().map() : null;
        MediaCodec.BufferInfo[] frameInfos =
                frame.getBufferInfos().toArray(new MediaCodec.BufferInfo[0]);
        MediaCodec.BufferInfo[] callBackInfos = infos.toArray(new MediaCodec.BufferInfo[0]);
        assertEquals("Lengths of frameInfos received via callback and getBufferInfos api are not "
                        + "identical. \n" + mTestConfig + mTestEnv, frameInfos.length,
                callBackInfos.length);

        int totalSize = 0;
        for (int i = 0; i < frameInfos.length; ++i) {
            MediaCodec.BufferInfo frameInfo = frameInfos[i];
            MediaCodec.BufferInfo callBackInfo = callBackInfos[i];
            Assert.assertNotNull("received null entry in dequeueOutput frame infos list. \n"
                    + mTestConfig + mTestEnv, frameInfo);
            Assert.assertNotNull("received null entry in dequeueOutput callback infos list. \n"
                    + mTestConfig + mTestEnv, callBackInfo);

            long framePts = frameInfo.presentationTimeUs;
            long infoPts = callBackInfo.presentationTimeUs;
            int frameFlags = frameInfo.flags;
            int infoFlags = callBackInfo.flags;
            assertEquals("presentation timestamps from OutputFrame does not match with the value "
                    + "obtained from callback: framePts=" + framePts + ", infoPts=" + infoPts
                    + "\n" + mTestConfig + mTestEnv, framePts, infoPts);
            assertEquals("Flags from OutputFrame does not match with the value obtained from "
                    + "callback: frameFlags=" + frameFlags + ", infoFlags=" + infoFlags + "\n"
                    + mTestConfig + mTestEnv, frameFlags, infoFlags);
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + infoFlags + " size: "
                        + callBackInfo.size + " timestamp: " + infoPts);
            }
            if ((infoFlags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                mSawOutputEOS = true;
            }
            if (callBackInfo.size > 0) {
                if (buf != null) {
                    if (mSaveToMem) {
                        MediaCodec.BufferInfo copy = new MediaCodec.BufferInfo();
                        copy.set(mOutputBuff.getOutStreamSize(), callBackInfo.size, infoPts,
                                infoFlags);
                        mInfoList.add(copy);

                        mOutputBuff.checksum(buf, callBackInfo);
                        mOutputBuff.saveToMemory(buf, callBackInfo);
                    }
                    if (mMuxer != null) {
                        if (mTrackID == -1) {
                            mTrackID = mMuxer.addTrack(mCodec.getOutputFormat());
                            mMuxer.start();
                        }
                        mMuxer.writeSampleData(mTrackID, buf, callBackInfo);
                    }
                }
                if ((infoFlags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    mOutputBuff.saveOutPTS(infoPts);
                    mOutputCount++;
                }
            }
            totalSize += callBackInfo.size;
        }
        if (frame.getLinearBlock() != null) {
            frame.getLinearBlock().recycle();
        }
        assertTrue("Sum of all info sizes: " + totalSize + " exceeds max output size: "
                        + mMaxOutputSizeBytes + " \n" + mTestConfig + mTestEnv,
                totalSize <= mMaxOutputSizeBytes);
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }

    @Override
    protected void doWork(int frameLimit) throws InterruptedException, IOException {
        mLoopBackFrameLimit = frameLimit;
        if (mMuxOutput) {
            int muxerFormat = getMuxerFormatForMediaType(mMediaType);
            mMuxedOutputFile = getTempFilePath("");
            mMuxer = new MediaMuxer(mMuxedOutputFile, muxerFormat);
        }
        // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
        while (!mAsyncHandleMultiAccessUnits.hasSeenError() && !mSawInputEOS
                && mInputCount < frameLimit) {
            Pair<Integer, ArrayDeque<MediaCodec.BufferInfo>> element =
                    mAsyncHandleMultiAccessUnits.getWorkList();
            if (element != null) {
                int bufferID = element.first;
                ArrayDeque<MediaCodec.BufferInfo> infos = element.second;
                if (infos != null) {
                    // <id, infos> corresponds to output callback. Handle it accordingly
                    dequeueOutputs(bufferID, infos);
                } else {
                    // <id, null> corresponds to input callback. Handle it accordingly
                    enqueueInput(bufferID);
                }
            }
        }
    }

    @Override
    protected void waitForAllOutputs() throws InterruptedException {
        while (!mAsyncHandleMultiAccessUnits.hasSeenError() && !mSawOutputEOS) {
            Pair<Integer, ArrayDeque<MediaCodec.BufferInfo>> element =
                    mAsyncHandleMultiAccessUnits.getOutputs();
            if (element != null) {
                dequeueOutputs(element.first, element.second);
            }
        }
        if (mMuxOutput) {
            if (mTrackID != -1) {
                mMuxer.stop();
                mTrackID = -1;
            }
            if (mMuxer != null) {
                mMuxer.release();
                mMuxer = null;
            }
        }
        validateTestState();
    }

    /**
     * Verifies if the component under test can encode the test file correctly in multiple frame
     * block model mode. The encoding happens in asynchronous mode with a. eos flag signalled with
     * last raw frame and b. eos flag not signalled with last raw frame.
     * <p>
     * Check description of class {@link CodecEncoderBlockModelMultiAccessUnitTest}
     */
    @ApiTest(apis = {"android.media.MediaFormat#KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE",
            "android.media.MediaFormat#KEY_BUFFER_BATCH_THRESHOLD_OUTPUT_SIZE",
            "android.media.MediaCodec.Callback#onOutputBuffersAvailable",
            "android.media.MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleEncode() throws IOException, InterruptedException {
        CodecEncoderTestBase referenceBase = new CodecEncoderTestBase(mCodecName, mMediaType,
                new EncoderConfigParams[]{mActiveEncCfg}, mAllTestParams);
        referenceBase.encodeToMemory(mCodecName, mActiveEncCfg, mActiveRawRes, Integer.MAX_VALUE,
                true, false);
        OutputManager ref = referenceBase.getOutputManager();

        OutputManager testA = new OutputManager(ref.getSharedErrorLogs());
        OutputManager testB = new OutputManager(ref.getSharedErrorLogs());
        mSaveToMem = true;
        mMuxOutput = false;
        setUpSource(mActiveRawRes.mFileName);
        mCodec = MediaCodec.createByCodecName(mCodecName);
        MediaFormat format = mActiveEncCfg.getFormat();
        for (int[] outSizeInMs : OUT_SIZE_IN_MS) {
            int frameSize = mActiveRawRes.mBytesPerSample * mActiveEncCfg.mChannelCount;
            int maxOutputSize = (outSizeInMs[0] * frameSize * mActiveEncCfg.mSampleRate) / 1000;
            int thresholdOutputSize =
                    (outSizeInMs[1] * frameSize * mActiveEncCfg.mSampleRate) / 1000;
            format.setInteger(MediaFormat.KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE, maxOutputSize);
            format.setInteger(MediaFormat.KEY_BUFFER_BATCH_THRESHOLD_OUTPUT_SIZE,
                    thresholdOutputSize);
            boolean[] boolStates = {true, false};
            for (boolean eosType : boolStates) {
                mOutputBuff = eosType ? testA : testB;
                mOutputBuff.reset();
                configureCodec(format, true, eosType, true);
                mMaxInputSizeInMs = (outSizeInMs[0] / getCompressionRatio(mMediaType));
                mInfoList.clear();
                mCodec.start();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                mCodec.reset();
                if (!ref.equalsDequeuedOutput(mOutputBuff)) {
                    fail("Output of encoder in MultipleFrames mode differs from single access unit "
                            + "mode.\n" + mTestConfig + mTestEnv + mOutputBuff.getErrMsg());
                }
            }
            if (!testA.equals(testB)) {
                fail("Output of encoder component is not consistent across runs. \n" + mTestConfig
                        + mTestEnv + testB.getErrMsg());
            }
        }
        mCodec.release();
    }
}
