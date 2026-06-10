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

import static org.junit.Assert.fail;

import android.media.MediaCryptoException;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.media.cts.TestUtils;
import android.mediav2.common.cts.CodecDecoderBlockModelDrmTestBase;
import android.mediav2.common.cts.CodecDecoderDrmTestBase;
import android.mediav2.common.cts.OutputManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;

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
 * normal mode and block model mode. The test expects consistent output in both scenarios.
 * <p>
 */
// This test is limited to V and above as it doesn't work as intended on the older versions
// due to issues in block model buffer handling. Refer: b/331921194, b/325512893 and b/329767811.
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public class CodecDecoderDrmTest extends CodecDecoderDrmTestBase {
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

    public CodecDecoderDrmTest(String decoder, String mediaType, String testFile,
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

    static byte[] convert(int[] intArray) {
        byte[] byteArray = new byte[intArray.length];
        for (int i = 0; i < intArray.length; ++i) {
            byteArray[i] = (byte) intArray[i];
        }
        return byteArray;
    }

    /**
     * Check description of class {@link CodecDecoderDrmTest}
     */
    @ApiTest(apis = {"android.media.MediaCodec#configure",
            "android.media.MediaCodec#queueSecureInputBuffer",
            "android.media.MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL",
            "android.media.MediaCodec.Request#setEncryptedLinearBlock"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecode() throws IOException, InterruptedException,
            UnsupportedSchemeException, NotProvisionedException, ResourceBusyException,
            MediaCryptoException {
        CodecDecoderDrmTestBase decoder =
                new CodecDecoderDrmTestBase(mCodecName, mMediaType, null, mAllTestParams);
        decoder.setUpCrypto(CLEAR_KEY_IDENTIFIER, DRM_INIT_DATA, new byte[][]{CLEAR_KEY_CENC});
        decoder.decodeToMemory(mTestFile, mCodecName, 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC,
                Integer.MAX_VALUE);
        decoder.tearDownCrypto();

        if (IS_AT_LEAST_R) {
            OutputManager ref = decoder.getOutputManager();
            CodecDecoderBlockModelDrmTestBase decoderBlockModel =
                    new CodecDecoderBlockModelDrmTestBase(mCodecName, mMediaType, null,
                    mAllTestParams);
            OutputManager test = new OutputManager(ref.getSharedErrorLogs());
            decoderBlockModel.setUpCrypto(CLEAR_KEY_IDENTIFIER, DRM_INIT_DATA,
                    new byte[][]{CLEAR_KEY_CENC});
            decoderBlockModel.decodeToMemory(mTestFile, mCodecName, test, 0,
                    MediaExtractor.SEEK_TO_CLOSEST_SYNC, Integer.MAX_VALUE);
            decoderBlockModel.tearDownCrypto();
            if (!ref.equals(test)) {
                fail("Output in block model mode is not same as output in normal mode. \n"
                        + mTestConfig + mTestEnv + test.getErrMsg());
            }
        }
    }
}
