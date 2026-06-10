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

package android.media.drmframework.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_MultipleFrames;
import static android.media.codec.Flags.FLAG_LARGE_AUDIO_FRAME_FINISH;
import static android.media.drmframework.cts.CodecDecoderDrmTest.convert;

import static com.android.media.codec.flags.Flags.FLAG_LARGE_AUDIO_FRAME;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.media.MediaCodec;
import android.media.MediaCryptoException;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.media.cts.TestUtils;
import android.mediav2.common.cts.CodecDecoderDrmTestBase;
import android.mediav2.common.cts.CodecDecoderMultiAccessUnitDrmTestBase;
import android.mediav2.common.cts.OutputManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Test secure mediacodec api, decoders and their interactions in byte buffer mode
 * <p>
 * The test decodes a clear key scheme encrypted clip and stores the result in ByteBuffer in
 * normal mode and large audio buffer mode. The test expects consistent output in both scenarios.
 * <p>
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RequiresFlagsEnabled({FLAG_LARGE_AUDIO_FRAME, FLAG_LARGE_AUDIO_FRAME_FINISH})
@LargeTest
@RunWith(Parameterized.class)
public class CodecDecoderMultiAccessUnitDrmTest extends CodecDecoderMultiAccessUnitDrmTestBase {
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static final UUID CLEAR_KEY_IDENTIFIER =
            new UUID(0x1077efecc0b24d02L, 0xace33c1e52e2fb4bL);
    private static final byte[] DRM_INIT_DATA = convert(new int[]{
            // BMFF box header (4 bytes size + 'pssh')
            0x00, 0x00, 0x00, 0x34, 0x70, 0x73, 0x73, 0x68,
            // Full box header (version = 1 flags = 0)
            0x01, 0x00, 0x00, 0x00,
            // W3C Common PSSH box SystemID
            0x10, 0x77, 0xef, 0xec, 0xc0, 0xb2, 0x4d, 0x02, 0xac, 0xe3, 0x3c,
            0x1e, 0x52, 0xe2, 0xfb, 0x4b,
            // Number of key ids
            0x00, 0x00, 0x00, 0x01,
            // Key id
            0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
            0x30, 0x30, 0x30, 0x30, 0x30,
            // size of data, must be zero
            0x00, 0x00, 0x00, 0x00});
    private static final byte[] CLEAR_KEY_CENC = convert(new int[]{
            // Content key
            0x3f, 0x0a, 0x33, 0xf3, 0x40, 0x98, 0xb9, 0xe2,
            0x2b, 0xc0, 0x78, 0xe0, 0xa1, 0xb5, 0xe8, 0x54});
    private static final int[][] OUT_SIZE_IN_MS = {
            {1000, 250},  // max out size, threshold batch out size
            {1000, 100},
            {500, 20},
            {100, 100},
            {40, 100}
    };

    public CodecDecoderMultiAccessUnitDrmTest(String decoder, String mediaType, String testFile,
            String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = true;
        final boolean needVideo = false;
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_AUDIO_AAC, "bbb_stereo_48kHz_192kbps_aac_cenc.mp4"},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_stereo_48kHz_flac_cenc.mp4"},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_stereo_48kHz_192kbps_mp3_cenc.mp4"},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "bbb_stereo_48kHz_192kbps_opus_cenc.mp4"},
        }));
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo,
                        false /*testingallCodecs*/);
    }

    /**
     * Check description of class {@link CodecDecoderMultiAccessUnitDrmTest}
     */
    @ApiTest(apis = {"android.media.MediaCodec#configure",
            "android.media.MediaCodec#queueSecureInputBuffer",
            "android.media.MediaFormat#KEY_BUFFER_BATCH_MAX_OUTPUT_SIZE",
            "android.media.MediaFormat#KEY_BUFFER_BATCH_THRESHOLD_OUTPUT_SIZE",
            "android.media.MediaCodec#queueSecureInputBuffers",
            "android.media.MediaCodec.Callback#onOutputBuffersAvailable"})
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecode() throws IOException, InterruptedException,
            UnsupportedSchemeException, NotProvisionedException, ResourceBusyException,
            MediaCryptoException {
        assumeTrue(mCodecName + " does not support FEATURE_MultipleFrames",
                isFeatureSupported(mCodecName, mMediaType, FEATURE_MultipleFrames));

        CodecDecoderDrmTestBase cddrmtb =
                new CodecDecoderDrmTestBase(mCodecName, mMediaType, null, mAllTestParams);
        cddrmtb.setUpCrypto(CLEAR_KEY_IDENTIFIER, DRM_INIT_DATA, new byte[][]{CLEAR_KEY_CENC});
        cddrmtb.decodeToMemory(mTestFile, mCodecName, 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC,
                Integer.MAX_VALUE);
        cddrmtb.tearDownCrypto();
        OutputManager ref = cddrmtb.getOutputManager();

        boolean[] boolStates = {true, false};
        mSaveToMem = true;
        OutputManager testA = new OutputManager(ref.getSharedErrorLogs());
        OutputManager testB = new OutputManager(ref.getSharedErrorLogs());
        MediaFormat format = setUpSource(mTestFile);
        int maxSampleSize = getMaxSampleSizeForMediaType(mTestFile, mMediaType);
        mCodec = MediaCodec.createByCodecName(mCodecName);
        for (int[] outSizeInMs : OUT_SIZE_IN_MS) {
            configureKeysForLargeAudioFrameMode(format, maxSampleSize, outSizeInMs[0],
                    outSizeInMs[1]);
            for (boolean eosType : boolStates) {
                mOutputBuff = eosType ? testA : testB;
                mOutputBuff.reset();
                setUpCrypto(CLEAR_KEY_IDENTIFIER, DRM_INIT_DATA, new byte[][]{CLEAR_KEY_CENC});
                configureCodec(format, true, eosType, false);
                mMaxInputLimitMs = outSizeInMs[0];
                mCodec.start();
                mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                mCodec.reset();
                tearDownCrypto();
                if (!ref.equalsByteOutput(mOutputBuff)) {
                    fail("Output of decoder component when fed with multiple access units in "
                            + "single enqueue call differs from output received when each access "
                            + "unit is fed separately. \n"
                            + mTestConfig + mTestEnv + mOutputBuff.getErrMsg());
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
}
