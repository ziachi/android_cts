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
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;
import static android.mediav2.cts.CodecDecoderMultiAccessUnitTest.RECONFIG_FILE_MEDIA_TYPE_MAP;
import static android.mediav2.cts.CodecDecoderMultiAccessUnitTest.exhaustiveArgsList;

import static com.android.media.codec.flags.Flags.FLAG_LARGE_AUDIO_FRAME;

import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderBlockModelMultiAccessUnitTestBase;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.OutputManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Tests audio decoders support for feature MultipleFrames in block model mode.
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
public class CodecDecoderBlockModelMultiAccessUnitTest
        extends CodecDecoderBlockModelMultiAccessUnitTestBase {
    private static final String LOG_TAG =
            CodecDecoderBlockModelMultiAccessUnitTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static final int[][] OUT_SIZE_IN_MS = {
            {1000, 250},  // max out size, threshold batch out size
            {1000, 100},
            {500, 20},
            {100, 100},
            {40, 100}
    };

    private final String mReconfigFile;

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        return prepareParamList(exhaustiveArgsList, false, true, false, false, ComponentClass.ALL,
                new String[]{FEATURE_MultipleFrames});
    }

    public CodecDecoderBlockModelMultiAccessUnitTest(String decoder, String mediaType,
            String testFile, String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
        mReconfigFile = MEDIA_DIR + RECONFIG_FILE_MEDIA_TYPE_MAP.get(mediaType);
    }

    @Before
    public void setUp() throws IOException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(format);
        checkFormatSupport(mCodecName, mMediaType, false, formatList, null, CODEC_OPTIONAL);
    }

    /**
     * Verifies if the component under test can decode the test file correctly in multiple frame
     * block model mode. The decoding happens in asynchronous mode with a. eos flag signalled with
     * last compressed frame and b. eos flag not signalled with last compressed frame.
     * <p>
     * Check description of class {@link CodecDecoderBlockModelMultiAccessUnitTest}
     */
    @ApiTest(apis = {"android.media.MediaFormat#KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE",
            "android.media.MediaFormat#KEY_BUFFER_BATCH_THRESHOLD_OUTPUT_SIZE",
            "android.media.MediaCodec.Callback#onOutputBuffersAvailable",
            "android.media.MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecode() throws IOException, InterruptedException {
        CodecDecoderTestBase cdtb = new CodecDecoderTestBase(mCodecName, mMediaType, null,
                mAllTestParams);
        cdtb.decodeToMemory(mTestFile, mCodecName, 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC,
                Integer.MAX_VALUE);
        OutputManager ref = cdtb.getOutputManager();

        boolean[] boolStates = {true, false};
        OutputManager testA = new OutputManager(ref.getSharedErrorLogs());
        OutputManager testB = new OutputManager(ref.getSharedErrorLogs());
        mSaveToMem = true;
        MediaFormat format = setUpSource(mTestFile);
        int maxSampleSize = getMaxSampleSizeForMediaType(mTestFile, mMediaType);
        mCodec = MediaCodec.createByCodecName(mCodecName);
        for (int[] outSizeInMs : OUT_SIZE_IN_MS) {
            configureKeysForLargeAudioBlockModelFrameMode(format, maxSampleSize, outSizeInMs[0],
                    outSizeInMs[1]);
            for (boolean signalEosWithLastFrame : boolStates) {
                mOutputBuff = signalEosWithLastFrame ? testA : testB;
                mOutputBuff.reset();
                configureCodec(format, true, signalEosWithLastFrame, false);
                mMaxInputLimitMs = outSizeInMs[0];
                mCodec.start();
                mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                mCodec.reset();
                if (!ref.equalsByteOutput(mOutputBuff)) {
                    fail("Output of decoder component when fed with multiple access units in single"
                            + " enqueue call differs from output received when each access unit is"
                            + "fed separately. \n" + mTestConfig + mTestEnv
                            + mOutputBuff.getErrMsg());
                }
            }
            if (!testA.equals(testB)) {
                fail("Output of decoder component is not consistent across runs. \n" + mTestConfig
                        + mTestEnv + testB.getErrMsg());
            }
        }
        mCodec.release();
        mExtractor.release();
    }

    /**
     * Verifies component and framework behaviour to flush API when the codec is operating in
     * multiple frame block model mode. The test verifies if the component / framework output
     * is consistent with single access unit normal mode.
     * <p>
     * While the component is decoding the test clip, mediacodec flush() is called. The flush API
     * is called at various points :-
     * <ul>
     *     <li>In running state, after queueing n frames.</li>
     *     <li>In eos state.</li>
     * </ul>
     * <p>
     * In all situations (pre-flush or post-flush), the test expects the output timestamps to be
     * strictly increasing. The flush call makes the output received non-deterministic even for a
     * given input. Hence, besides timestamp checks, no additional validation is done for outputs
     * received before flush. Post flush, the decode begins from a sync frame. So the test
     * expects consistent output and this needs to be identical to the reference
     * (single access unit mode)
     * <p>
     */
    @ApiTest(apis = {"android.media.MediaFormat#KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE",
            "android.media.MediaFormat#KEY_BUFFER_BATCH_THRESHOLD_OUTPUT_SIZE",
            "android.media.MediaCodec.Callback#onOutputBuffersAvailable",
            "android.media.MediaCodec#flush"})
    @LargeTest
    @Ignore("TODO(b/147576107)")
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testFlush() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        final long pts = 250000;
        mExtractor.release();
        OutputManager ref = null, test;
        if (isMediaTypeOutputUnAffectedBySeek(mMediaType)) {
            CodecDecoderTestBase cdtb = new CodecDecoderTestBase(mCodecName, mMediaType, null,
                    mAllTestParams);
            cdtb.decodeToMemory(mTestFile, mCodecName, pts, MediaExtractor.SEEK_TO_CLOSEST_SYNC,
                    Integer.MAX_VALUE);
            ref = cdtb.getOutputManager();
            test = new OutputManager(ref.getSharedErrorLogs());
        } else {
            test = new OutputManager();
        }

        mOutputBuff = test;
        setUpSource(mTestFile);
        int maxSampleSize = getMaxSampleSizeForMediaType(mTestFile, mMediaType);
        configureKeysForLargeAudioBlockModelFrameMode(format, maxSampleSize, OUT_SIZE_IN_MS[0][0],
                OUT_SIZE_IN_MS[0][1]);
        mMaxInputLimitMs = OUT_SIZE_IN_MS[0][0];
        mCodec = MediaCodec.createByCodecName(mCodecName);
        test.reset();
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        configureCodec(format, true, true, false);

        mCodec.start();
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        test.reset();
        doWork(23);
        if (!test.isPtsStrictlyIncreasing(mPrevOutputPts)) {
            fail("Output timestamps are not strictly increasing \n" + mTestConfig + mTestEnv
                    + test.getErrMsg());
        }

        /* test flush in running state */
        flushCodec();
        mCodec.start();
        mSaveToMem = true;
        test.reset();
        mExtractor.seekTo(pts, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        if (ref != null && !ref.equalsByteOutput(test)) {
            fail("Decoder output is not consistent across runs \n" + mTestConfig + mTestEnv
                    + test.getErrMsg());
        }

        /* test flush in eos state */
        flushCodec();
        mCodec.start();
        test.reset();
        mExtractor.seekTo(pts, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        if (ref != null && !ref.equalsByteOutput(test)) {
            fail("Decoder output is not consistent across runs \n" + mTestConfig + mTestEnv
                    + test.getErrMsg());
        }

        mSaveToMem = false;
        mCodec.release();
        mExtractor.release();
    }

    /**
     * Verifies component and framework behaviour for format change in multiple frame block model
     * mode. The format change is not seamless (AdaptivePlayback) but done via reconfigure.
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
     * identical to the reference (single access unit mode).
     * <p>
     */
    @ApiTest(apis = {"android.media.MediaFormat#KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE",
            "android.media.MediaFormat#KEY_BUFFER_BATCH_THRESHOLD_OUTPUT_SIZE",
            "android.media.MediaCodec.Callback#onOutputBuffersAvailable",
            "android.media.MediaCodec#configure"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testReconfigure() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        MediaFormat newFormat = setUpSource(mReconfigFile);
        mExtractor.release();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(newFormat);
        checkFormatSupport(mCodecName, mMediaType, false, formatList, null, CODEC_OPTIONAL);

        CodecDecoderTestBase cdtbA = new CodecDecoderTestBase(mCodecName, mMediaType, null,
                mAllTestParams);
        cdtbA.decodeToMemory(mTestFile, mCodecName, 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC,
                Integer.MAX_VALUE);
        OutputManager ref = cdtbA.getOutputManager();
        OutputManager test = new OutputManager(ref.getSharedErrorLogs());

        CodecDecoderTestBase cdtbB = new CodecDecoderTestBase(mCodecName, mMediaType, null,
                mAllTestParams);
        cdtbB.decodeToMemory(mReconfigFile, mCodecName, 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC,
                Integer.MAX_VALUE);
        OutputManager configRef = cdtbB.getOutputManager();
        OutputManager configTest = new OutputManager(configRef.getSharedErrorLogs());

        int maxSampleSize = getMaxSampleSizeForMediaType(mTestFile, mMediaType);
        configureKeysForLargeAudioBlockModelFrameMode(format, maxSampleSize, OUT_SIZE_IN_MS[0][0],
                OUT_SIZE_IN_MS[0][1]);
        mMaxInputLimitMs = OUT_SIZE_IN_MS[0][0];
        mCodec = MediaCodec.createByCodecName(mCodecName);
        mOutputBuff = test;
        setUpSource(mTestFile);
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        configureCodec(format, true, true, false);

        /* test reconfigure in stopped state */
        reConfigureCodec(format, true, false, false);
        mCodec.start();

        /* test reconfigure in running state before queuing input */
        reConfigureCodec(format, true, false, false);
        mCodec.start();
        doWork(23);

        /* test reconfigure codec in running state */
        reConfigureCodec(format, true, true, false);
        mCodec.start();
        mSaveToMem = true;
        test.reset();
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        if (!ref.equalsByteOutput(test)) {
            fail("Decoder output is not consistent across runs \n" + mTestConfig + mTestEnv
                    + test.getErrMsg());
        }

        /* test reconfigure codec at eos state */
        reConfigureCodec(format, true, false, false);
        mCodec.start();
        test.reset();
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        if (!ref.equalsByteOutput(test)) {
            fail("Decoder output is not consistent across runs \n" + mTestConfig + mTestEnv
                    + test.getErrMsg());
        }
        mExtractor.release();

        /* test reconfigure codec for new file */
        maxSampleSize = getMaxSampleSizeForMediaType(mReconfigFile, mMediaType);
        configureKeysForLargeAudioBlockModelFrameMode(newFormat, maxSampleSize,
                OUT_SIZE_IN_MS[0][0], OUT_SIZE_IN_MS[0][1]);
        mOutputBuff = configTest;
        setUpSource(mReconfigFile);
        reConfigureCodec(newFormat, true, false, false);
        mCodec.start();
        configTest.reset();
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        if (!configRef.equalsByteOutput(configTest)) {
            fail("Decoder output is not consistent across runs \n" + mTestConfig + mTestEnv
                    + configTest.getErrMsg());
        }
        mSaveToMem = false;
        mExtractor.release();
        mCodec.release();
    }
}
