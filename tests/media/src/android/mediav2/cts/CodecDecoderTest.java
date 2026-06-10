/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
import static android.media.codec.Flags.apvSupport;
import static android.media.tv.flags.Flags.FLAG_APPLY_PICTURE_PROFILES;
import static android.media.tv.flags.Flags.FLAG_MEDIA_QUALITY_FW;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_ALL;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static com.android.media.extractor.flags.Flags.extractorMp4EnableApv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.quality.PictureProfile;
import android.media.quality.PictureProfileHandle;
import android.mediav2.common.cts.CodecDecoderBlockModelTestBase;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.OutputManager;
import android.os.Build;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;
import android.view.Surface;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.Preconditions;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Test mediacodec api, decoders and their interactions in byte buffer mode
 * <p>
 * The test decodes a compressed frame and stores the result in ByteBuffer. This allows
 * validating the decoded output. Hence wherever possible we check if the decoded output is
 * compliant.
 * <ul>
 *     <li>For Avc, Hevc, Vpx and Av1, the test expects the decoded output to be identical to
 *     reference decoded output. The reference decoded output is represented by its CRC32
 *     checksum and is sent to the test as a parameter along with the test clip.</li>
 *     <li>For others codecs (mpeg2, mpeg4, h263, ...) the decoded output is checked for
 *     consistency (doesn't change across runs). No crc32 verification is done because idct for
 *     these standards are non-normative.</li>
 *     <li>For lossless audio media types, the test verifies if the rms error between input and
 *     output is 0.</li>
 *     <li>For lossy audio media types, the test verifies if the rms error is within 5% of
 *     reference rms error. The reference value is computed using reference decoder and is sent
 *     to the test as a parameter along with the test clip.</li>
 *     <li>For all video components, the test expects the output timestamp list to be identical to
 *     input timestamp list.</li>
 *     <li>For all audio components, the test expects the output timestamps to be strictly
 *     increasing.</li>
 *     <li>The test also verifies if the component reports a format change if the test clip does
 *     not use the default format.</li>
 * </ul>
 * <p>
 * The test runs mediacodec in synchronous and asynchronous mode.
 */
@RunWith(Parameterized.class)
public class CodecDecoderTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = CodecDecoderTest.class.getSimpleName();
    private static final float RMS_ERROR_TOLERANCE = 1.05f;        // 5%
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    private final String mRefFile;
    private final float mRmsError;
    private final long mRefCRC;
    private final SupportClass mSupportRequirements;

    static {
        System.loadLibrary("ctsmediav2codecdec_jni");
    }

    public CodecDecoderTest(String decoder, String mediaType, String testFile, String refFile,
            float rmsError, long refCRC, SupportClass supportRequirements, String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
        mRefFile = MEDIA_DIR + refFile;
        mRmsError = rmsError;
        mRefCRC = refCRC;
        mSupportRequirements = supportRequirements;
    }

    static ByteBuffer readAudioReferenceFile(String file) throws IOException {
        Preconditions.assertTestFileExists(file);
        File refFile = new File(file);
        ByteBuffer refBuffer;
        try (FileInputStream refStream = new FileInputStream(refFile)) {
            FileChannel fileChannel = refStream.getChannel();
            int length = (int) refFile.length();
            refBuffer = ByteBuffer.allocate(length);
            refBuffer.order(ByteOrder.LITTLE_ENDIAN);
            fileChannel.read(refBuffer);
        }
        return refBuffer;
    }

    private ArrayList<MediaCodec.BufferInfo> createSubFrames(ByteBuffer buffer, int sfCount) {
        int size = (int) mExtractor.getSampleSize();
        if (size < 0) return null;
        mExtractor.readSampleData(buffer, 0);
        long pts = mExtractor.getSampleTime();
        int flags = mExtractor.getSampleFlags();
        if (size < sfCount) sfCount = size;
        ArrayList<MediaCodec.BufferInfo> list = new ArrayList<>();
        int offset = 0;
        for (int i = 0; i < sfCount; i++) {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            info.offset = offset;
            info.presentationTimeUs = pts;
            info.flags = 0;
            if ((flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                info.flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            if ((flags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                info.flags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
            }
            if (i != sfCount - 1) {
                info.size = size / sfCount;
                info.flags |= MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME;
            } else {
                info.size = size - offset;
            }
            list.add(info);
            offset += info.size;
        }
        return list;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = true;
        final boolean needVideo = true;
        // mediaType, testClip, referenceClip, refRmsError, refCRC32, SupportClass
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_1ch_8kHz_lame_cbr.mp3",
                        "bbb_1ch_8kHz_s16le.raw", 91.026749f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_2ch_44kHz_lame_cbr.mp3",
                        "bbb_2ch_44kHz_s16le.raw", 103.603081f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "bbb_1ch_16kHz_16kbps_amrwb.3gp",
                        "bbb_1ch_16kHz_s16le.raw", 2393.5979f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "bbb_1ch_8kHz_10kbps_amrnb.3gp",
                        "bbb_1ch_8kHz_s16le.raw", -1.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_1ch_16kHz_flac.mka",
                        "bbb_1ch_16kHz_s16le.raw", 0.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_2ch_44kHz_flac.mka",
                        "bbb_2ch_44kHz_s16le.raw", 0.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_1ch_16kHz.wav", "bbb_1ch_16kHz_s16le.raw",
                        0.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_2ch_44kHz.wav", "bbb_2ch_44kHz_s16le.raw",
                        0.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_G711_ALAW, "bbb_1ch_8kHz_alaw.wav",
                        "bbb_1ch_8kHz_s16le.raw", 23.087402f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_G711_MLAW, "bbb_1ch_8kHz_mulaw.wav",
                        "bbb_1ch_8kHz_s16le.raw", 24.413954f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_MSGSM, "bbb_1ch_8kHz_gsm.wav",
                        "bbb_1ch_8kHz_s16le.raw", 946.026978f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "bbb_1ch_16kHz_vorbis.mka",
                        "bbb_1ch_8kHz_s16le.raw", -1.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "bbb_2ch_48kHz_opus.mka",
                        "bbb_2ch_48kHz_s16le.raw", -1.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "bbb_1ch_16kHz_aac.mp4",
                        "bbb_1ch_16kHz_s16le.raw", -1.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, "bbb_340x280_768kbps_30fps_mpeg2.mp4", null,
                        -1.0f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_340x280_768kbps_30fps_avc.mp4", null, -1.0f,
                        1746312400L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_520x390_1mbps_30fps_hevc.mp4", null, -1.0f,
                        3061322606L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, "bbb_128x96_64kbps_12fps_mpeg4.mp4", null, -1.0f,
                        -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_H263, "bbb_176x144_128kbps_15fps_h263.3gp", null, -1.0f,
                        -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_340x280_768kbps_30fps_vp8.webm", null, -1.0f,
                        2030620796L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_340x280_768kbps_30fps_vp9.webm", null, -1.0f,
                        4122701060L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_340x280_768kbps_30fps_av1.mp4", null, -1.0f,
                        400672933L, CODEC_ALL},
        }));
        // Framework P010 support added with android T.
        // These codecs are not required to support P010, but if they advertise support,
        // we are going to test it.
        if (IS_AT_LEAST_T) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_340x280_24fps_crf22_avc_10bit.mkv",
                            null, -1.0f, 1462636611L, CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_340x280_24fps_crf22_hevc_10bit.mkv",
                            null, -1.0f, 2611796790L, CODEC_OPTIONAL},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_340x280_24fps_crf22_vp9_10bit.mkv",
                            null, -1.0f, 2419292938L, CODEC_OPTIONAL},
            }));
        }
        // These codecs are always required to support P010.
        // AV1 10 bit support was mandated as of Android11 CDD 5.3, but we're unable to verify
        // that with framework before T.
        if (IS_AT_LEAST_T) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_340x280_24fps_512kbps_av1_10bit.mkv",
                            null, -1.0f, 1021109556L, CODEC_ALL},
            }));
        }
        if (IS_AT_LEAST_B && apvSupport() && extractorMp4EnableApv()) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_APV,
                            "pattern_640x480_30fps_16mbps_apv_10bit.mp4", null, -1.0f, -1L,
                             CODEC_ALL},
            }));
        }
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, true);
    }

    static void verify(OutputManager outBuff, String refFile, float rmsError, int audioFormat,
            long refCRC, String msg) throws IOException {
        if (rmsError >= 0) {
            int bytesPerSample = AudioFormat.getBytesPerSample(audioFormat);
            ByteBuffer bb = readAudioReferenceFile(refFile);
            bb.position(0);
            int bufferSize = bb.limit();
            assertEquals("error, reference audio buffer contains partial samples\n" + msg, 0,
                    bufferSize % bytesPerSample);
            Object refObject = null;
            int refObjectLen = bufferSize / bytesPerSample;
            switch (audioFormat) {
                case AudioFormat.ENCODING_PCM_8BIT:
                    refObject = new byte[refObjectLen];
                    bb.get((byte[]) refObject);
                    break;
                case AudioFormat.ENCODING_PCM_16BIT:
                    refObject = new short[refObjectLen];
                    bb.asShortBuffer().get((short[]) refObject);
                    break;
                case AudioFormat.ENCODING_PCM_24BIT_PACKED:
                    refObject = new int[refObjectLen];
                    int[] refArray = (int[]) refObject;
                    for (int i = 0, j = 0; i < bufferSize; i += 3, j++) {
                        int byte1 = (bb.get() & 0xff);
                        int byte2 = (bb.get() & 0xff);
                        int byte3 = (bb.get() & 0xff);
                        refArray[j] = byte1 | (byte2 << 8) | (byte3 << 16);
                    }
                    break;
                case AudioFormat.ENCODING_PCM_32BIT:
                    refObject = new int[refObjectLen];
                    bb.asIntBuffer().get((int[]) refObject);
                    break;
                case AudioFormat.ENCODING_PCM_FLOAT:
                    refObject = new float[refObjectLen];
                    bb.asFloatBuffer().get((float[]) refObject);
                    break;
                default:
                    fail("unrecognized audio encoding type :- " + audioFormat + "\n" + msg);
            }
            float currError = outBuff.getRmsError(refObject, audioFormat);
            float errMargin = rmsError * RMS_ERROR_TOLERANCE;
            assertTrue(String.format("%s rms error too high ref/exp/got %f/%f/%f \n", refFile,
                    rmsError, errMargin, currError) + msg, currError <= errMargin);
        } else if (refCRC >= 0) {
            assertEquals("checksum mismatch \n" + msg, refCRC, outBuff.getCheckSumImage());
        }
    }

    @Before
    public void setUp() throws IOException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        formatList.add(format);
        checkFormatSupport(mCodecName, mMediaType, false, formatList, null, mSupportRequirements);
    }

    private native boolean nativeTestSimpleDecode(String decoder, Surface surface, String mediaType,
            String testFile, String refFile, int colorFormat, float rmsError, long checksum,
            StringBuilder retMsg);

    /**
     * Verifies if the component under test can decode the test file correctly. The decoding
     * happens in synchronous, asynchronous mode, eos flag signalled with last compressed frame and
     * eos flag signalled separately after sending all compressed frames. It expects consistent
     * output in all these runs. That is, the ByteBuffer info and output timestamp list has to be
     * same in all the runs. Further for audio, the output timestamp has to be strictly
     * increasing, for lossless audio codec the rms error has to be 0 and for lossy audio codecs,
     * the rms error has to be with in tolerance limit. For video the output timestamp list has
     * to be same as input timestamp list (no frame drops) and for completely normative codecs,
     * the output checksum has to be identical to reference checksum. For non-normative codecs,
     * the output has to be consistent. The test also verifies if the component / framework
     * behavior is consistent between SDK and NDK. The test also verifies if the
     * component / framework behavior is consistent between block model mode and normal mode.
     */
    @CddTest(requirements = {"2.2.2", "2.3.2", "2.5.2", "5.1.2"})
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "android.media.MediaCodecInfo.CodecCapabilities#COLOR_FormatYUVP010",
            "android.media.AudioFormat#ENCODING_PCM_16BIT",
            "android.media.MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecode() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        boolean[] boolStates = {true, false};
        mSaveToMem = true;
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager(ref.getSharedErrorLogs());
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            assertEquals("codec name act/got: " + mCodec.getName() + '/' + mCodecName,
                    mCodecName, mCodec.getName());
            assertTrue("error! codec canonical name is null or empty",
                    mCodec.getCanonicalName() != null && !mCodec.getCanonicalName().isEmpty());
            validateMetrics(mCodecName);
            int loopCounter = 0;
            for (boolean eosType : boolStates) {
                for (boolean isAsync : boolStates) {
                    boolean validateFormat = true;
                    mOutputBuff = loopCounter == 0 ? ref : test;
                    mOutputBuff.reset();
                    mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    configureCodec(format, isAsync, eosType, false);
                    MediaFormat defFormat = mCodec.getOutputFormat();
                    if (isFormatSimilar(format, defFormat)) {
                        if (ENABLE_LOGS) {
                            Log.d("Input format is same as default for format for %s", mCodecName);
                        }
                        validateFormat = false;
                    }
                    mCodec.start();
                    doWork(Integer.MAX_VALUE);
                    queueEOS();
                    waitForAllOutputs();
                    validateMetrics(mCodecName, format);
                    endCodecSession(mCodec);
                    if (loopCounter != 0 && !ref.equals(test)) {
                        fail("Decoder output is not consistent across runs \n" + mTestConfig
                                + mTestEnv + test.getErrMsg());
                    }
                    if (validateFormat) {
                        doOutputFormatChecks(defFormat, format);
                    }
                    loopCounter++;
                }
            }
            mCodec.release();
            mExtractor.release();
            // The following part of the test was added after Android U and is not guaranteed
            // to work on versions before that, hence limit it to Android V and above.
            if (IS_AT_LEAST_V && mSaveToMem && mIsAudio) {
                test.reset();
                CodecDecoderBlockModelTestBase cdbmtb = new CodecDecoderBlockModelTestBase(
                        mCodecName, mMediaType, null, mAllTestParams);
                cdbmtb.decodeToMemory(mTestFile, mCodecName, test, 0,
                        MediaExtractor.SEEK_TO_CLOSEST_SYNC, Integer.MAX_VALUE, true, false);
                if (!ref.equals(test)) {
                    fail("Output in block model mode is not same as output in normal mode. \n"
                            + mTestConfig + mTestEnv + test.getErrMsg());
                }
            }
            int colorFormat = mIsAudio ? 0 : format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
            boolean isPass = nativeTestSimpleDecode(mCodecName, null, mMediaType, mTestFile,
                    mRefFile, colorFormat, mRmsError, ref.getCheckSumBuffer(), mTestConfig);
            assertTrue(mTestConfig.toString(), isPass);
            if (mSaveToMem) {
                int audioEncoding = mIsAudio ? format.getInteger(MediaFormat.KEY_PCM_ENCODING,
                        AudioFormat.ENCODING_PCM_16BIT) : AudioFormat.ENCODING_INVALID;
                Assume.assumeFalse("skip checksum due to tone mapping", mSkipChecksumVerification);
                verify(mOutputBuff, mRefFile, mRmsError, audioEncoding, mRefCRC,
                        mTestConfig.toString() + mTestEnv.toString());
            }
        }
    }

    /**
     * Verifies component and framework behaviour to flush API when the codec is operating in
     * byte buffer mode.
     * <p>
     * While the component is decoding the test clip, mediacodec flush() is called. The flush API
     * is called at various points :-
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
     * expects consistent output and this needs to be identical to the reference.
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
            decodeToMemory(mTestFile, mCodecName, pts, mode, Integer.MAX_VALUE);
            OutputManager ref = mOutputBuff;
            OutputManager test = new OutputManager(ref.getSharedErrorLogs());
            mOutputBuff = test;
            setUpSource(mTestFile);
            mCodec = MediaCodec.createByCodecName(mCodecName);
            for (boolean isAsync : boolStates) {
                if (isAsync) continue;  // TODO(b/147576107)
                mExtractor.seekTo(0, mode);
                configureCodec(format, isAsync, true, false);
                MediaFormat defFormat = mCodec.getOutputFormat();
                boolean validateFormat = true;
                if (isFormatSimilar(format, defFormat)) {
                    if (ENABLE_LOGS) {
                        Log.d("Input format is same as default for format for %s", mCodecName);
                    }
                    validateFormat = false;
                }
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

                boolean checkMetrics = (mOutputCount != 0);

                /* test flush in running state */
                flushCodec();
                if (checkMetrics) validateMetrics(mCodecName, format);
                if (mIsCodecInAsyncMode) mCodec.start();
                mSaveToMem = true;
                test.reset();
                mExtractor.seekTo(pts, mode);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                if (isMediaTypeOutputUnAffectedBySeek(mMediaType) && (!ref.equals(test))) {
                    fail("Decoder output is not consistent across runs \n" + mTestConfig + mTestEnv
                            + test.getErrMsg());
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
                if (isMediaTypeOutputUnAffectedBySeek(mMediaType) && (!ref.equals(test))) {
                    fail("Decoder output is not consistent across runs \n" + mTestConfig + mTestEnv
                            + test.getErrMsg());
                }
                if (validateFormat) {
                    doOutputFormatChecks(defFormat, format);
                }
                mSaveToMem = false;
            }
            mCodec.release();
            mExtractor.release();
        }
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
        int colorFormat = 0;
        if (mIsVideo) {
            MediaFormat format = setUpSource(mTestFile);
            mExtractor.release();
            colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
        }
        boolean isPass = nativeTestFlush(mCodecName, null, mMediaType, mTestFile,
                colorFormat, mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }

    /**
     * Test decoder for EOS only input. As BUFFER_FLAG_END_OF_STREAM is queued with an input buffer
     * of size 0, during dequeue the test expects to receive BUFFER_FLAG_END_OF_STREAM with an
     * output buffer of size 0. No input is given, so no output shall be received.
     */
    @ApiTest(apis = "android.media.MediaCodec#BUFFER_FLAG_END_OF_STREAM")
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testOnlyEos() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        boolean[] boolStates = {true, false};
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager(ref.getSharedErrorLogs());
        mSaveToMem = true;
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            int loopCounter = 0;
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, false, false);
                mOutputBuff = loopCounter == 0 ? ref : test;
                mOutputBuff.reset();
                mCodec.start();
                queueEOS();
                waitForAllOutputs();
                endCodecSession(mCodec);
                if (loopCounter != 0 && !ref.equals(test)) {
                    fail("Decoder output is not consistent across runs \n" + mTestConfig + mTestEnv
                            + test.getErrMsg());
                }
                loopCounter++;
            }
            mCodec.release();
        }
        mExtractor.release();
    }

    private native boolean nativeTestOnlyEos(String decoder, String mediaType, String testFile,
            int colorFormat, StringBuilder retMsg);

    /**
     * Test is similar to {@link #testOnlyEos()} but uses ndk api
     */
    @ApiTest(apis = "android.media.MediaCodec#BUFFER_FLAG_END_OF_STREAM")
    @SmallTest
    @Test
    public void testOnlyEosNative() throws IOException {
        int colorFormat = 0;
        if (mIsVideo) {
            MediaFormat format = setUpSource(mTestFile);
            mExtractor.release();
            colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
        }
        boolean isPass = nativeTestOnlyEos(mCodecName, mMediaType, mTestFile, colorFormat,
                mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }

    /**
     * CSD buffers can be queued at configuration or can be queued separately as the first buffer(s)
     * sent to the codec. This test ensures that both mechanisms function and that they are
     * semantically the same.
     */
    @ApiTest(apis = "android.media.MediaCodec#BUFFER_FLAG_CODEC_CONFIG")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecodeQueueCSD() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        if (!hasCSD(format)) {
            mExtractor.release();
            return;
        }
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        formats.add(new MediaFormat(format));
        for (int i = 0; ; i++) {
            String csdKey = "csd-" + i;
            if (format.containsKey(csdKey)) {
                mCsdBuffers.add(format.getByteBuffer(csdKey).duplicate());
                format.removeKey(csdKey);
            } else break;
        }
        boolean[] boolStates = {true, false};
        mSaveToMem = true;
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager(ref.getSharedErrorLogs());
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            int loopCounter = 0;
            for (int i = 0; i < formats.size(); i++) {
                MediaFormat fmt = formats.get(i);
                for (boolean eosMode : boolStates) {
                    for (boolean isAsync : boolStates) {
                        boolean validateFormat = true;
                        mOutputBuff = loopCounter == 0 ? ref : test;
                        mOutputBuff.reset();
                        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        configureCodec(fmt, isAsync, eosMode, false);
                        MediaFormat defFormat = mCodec.getOutputFormat();
                        if (isFormatSimilar(defFormat, format)) {
                            if (ENABLE_LOGS) {
                                Log.d("Input format is same as default for format for %s",
                                        mCodecName);
                            }
                            validateFormat = false;
                        }
                        mCodec.start();
                        if (i == 0) queueCodecConfig();
                        doWork(Integer.MAX_VALUE);
                        queueEOS();
                        waitForAllOutputs();
                        validateMetrics(mCodecName);
                        endCodecSession(mCodec);
                        if (loopCounter != 0 && !ref.equals(test)) {
                            fail("Decoder output is not consistent across runs \n" + mTestConfig
                                    + mTestEnv + test.getErrMsg());
                        }
                        if (validateFormat) {
                            doOutputFormatChecks(defFormat, format);
                        }
                        loopCounter++;
                    }
                }
            }
            mCodec.release();
        }
        mExtractor.release();
    }

    private native boolean nativeTestSimpleDecodeQueueCSD(String decoder, String mediaType,
            String testFile, int colorFormat, StringBuilder retMsg);

    /**
     * Test is similar to {@link #testSimpleDecodeQueueCSD()} but uses ndk api
     */
    @ApiTest(apis = "android.media.MediaCodec#BUFFER_FLAG_CODEC_CONFIG")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecodeQueueCSDNative() throws IOException {
        MediaFormat format = setUpSource(mTestFile);
        if (!hasCSD(format)) {
            mExtractor.release();
            return;
        }
        mExtractor.release();
        int colorFormat = mIsAudio ? 0 : format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
        boolean isPass = nativeTestSimpleDecodeQueueCSD(mCodecName, mMediaType, mTestFile,
                colorFormat, mTestConfig);
        assertTrue(mTestConfig.toString(), isPass);
    }

    /**
     * Test decoder for partial frame inputs. The test expects decoder to give same output for a
     * regular sequence and when any frames of that sequence are delivered in parts using the
     * PARTIAL_FRAME flag.
     */
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_PartialFrame",
            "android.media.MediaCodec#BUFFER_FLAG_PARTIAL_FRAME"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testDecodePartialFrame() throws IOException, InterruptedException {
        Assume.assumeTrue("codec: " + mCodecName + " does not advertise FEATURE_PartialFrame",
                isFeatureSupported(mCodecName, mMediaType,
                        MediaCodecInfo.CodecCapabilities.FEATURE_PartialFrame));
        MediaFormat format = setUpSource(mTestFile);
        boolean[] boolStates = {true, false};
        int frameLimit = 10;
        ByteBuffer buffer = ByteBuffer.allocate(4 * 1024 * 1024);
        {
            decodeToMemory(mTestFile, mCodecName, 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC,
                    frameLimit);
            mCodec = MediaCodec.createByCodecName(mCodecName);
            OutputManager ref = mOutputBuff;
            mSaveToMem = true;
            OutputManager test = new OutputManager(ref.getSharedErrorLogs());
            mOutputBuff = test;
            for (boolean isAsync : boolStates) {
                mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                test.reset();
                configureCodec(format, isAsync, true, false);
                mCodec.start();
                doWork(frameLimit - 1);
                ArrayList<MediaCodec.BufferInfo> list = createSubFrames(buffer, 4);
                assertTrue("no sub frames in list received for " + mTestFile,
                        list != null && list.size() > 0);
                doWork(buffer, list);
                queueEOS();
                waitForAllOutputs();
                endCodecSession(mCodec);
                if (!ref.equals(test)) {
                    fail("Decoder output of a compressed stream segmented at frame/access unit "
                            + "boundaries is different from a compressed stream segmented at "
                            + "arbitrary byte boundary \n"
                            + mTestConfig + mTestEnv + test.getErrMsg());
                }
            }
            mCodec.release();
        }
        mExtractor.release();
    }

    /**
     * Test if decoder outputs 8-bit data for 8-bit as well as 10-bit content by default. The
     * test removes the key "KEY_COLOR_FORMAT" from the input format to the decoder and verifies if
     * the component decodes to an 8 bit color format by default.
     * <p>
     * The test is applicable for video components only.
     */
    @CddTest(requirements = {"5.1.7/C-1-5", "5.1.7/C-4-2"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testDefaultOutputColorFormat() throws IOException, InterruptedException {
        Assume.assumeTrue("Test needs Android 13", IS_AT_LEAST_T);
        Assume.assumeTrue("Test is applicable for video decoders", mIsVideo);

        MediaFormat format = setUpSource(mTestFile);
        format.removeKey(MediaFormat.KEY_COLOR_FORMAT);

        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        configureCodec(format, true, true, false);
        mCodec.start();
        doWork(1);
        queueEOS();
        waitForAllOutputs();
        MediaFormat outputFormat = mCodec.getOutputFormat();
        endCodecSession(mCodec);
        mCodec.release();

        assertTrue("Output format from decoder does not contain KEY_COLOR_FORMAT \n" + mTestConfig
                + mTestEnv, outputFormat.containsKey(MediaFormat.KEY_COLOR_FORMAT));
        // 8-bit color formats
        int[] defaultOutputColorFormatList =
                new int[]{COLOR_FormatYUV420Flexible, COLOR_FormatYUV420Planar,
                        COLOR_FormatYUV420PackedPlanar, COLOR_FormatYUV420SemiPlanar};
        int outputColorFormat = outputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
        assertTrue(String.format("Unexpected output color format %x \n", outputColorFormat)
                        + mTestConfig + mTestEnv,
                IntStream.of(defaultOutputColorFormatList).anyMatch(x -> x == outputColorFormat));
    }

    /**
     * Test setting PictureProfile instance as a parameter using MediaCodec.setParameter().
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    @RequiresFlagsEnabled({FLAG_APPLY_PICTURE_PROFILES, FLAG_MEDIA_QUALITY_FW})
    @ApiTest(apis = {"android.media.MediaFormat#KEY_PICTURE_PROFILE_INSTANCE"})
    @Test
    public void testSetPictureProfileInstanceAsParameter()
            throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        PictureProfile testPictureProfile =
                new PictureProfile.Builder("test")
                        .setHandle(new PictureProfileHandle(1))
                        .build();
        configureCodec(format, true, true, false);
        mCodec.start();
        Bundle bundle = new Bundle();
        bundle.putObject(MediaFormat.KEY_PICTURE_PROFILE_INSTANCE, testPictureProfile);
        mCodec.setParameters(bundle);
        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();

        // TODO: verify picture profile handle value by
        // checking output format using mCodec.getOutputFormat()

        endCodecSession(mCodec);
        mCodec.release();
    }
}
