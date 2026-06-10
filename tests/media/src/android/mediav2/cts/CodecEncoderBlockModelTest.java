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

import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectELD;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectHE;
import static android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;
import static android.mediav2.cts.AudioEncoderTest.flattenParams;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.media.AudioFormat;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderBlockModelTestBase;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.OutputManager;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Tests audio encoders in single access unit block model mode.
 * <p>
 * The encoding happens in asynchronous mode with a. eos flag signalled with last raw frame and
 * b. eos flag not signalled with last raw frame.
 * <p>
 * The test verifies if the component / framework output is consistent with single access unit
 * normal mode and single access unit block model mode.
 **/
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public class CodecEncoderBlockModelTest extends CodecEncoderBlockModelTestBase {
    private static final String LOG_TAG = CodecEncoderBlockModelTest.class.getSimpleName();

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
        return prepareParamList(argsList, true, true, false, true, ComponentClass.ALL);
    }

    public CodecEncoderBlockModelTest(String encoder, String mediaType,
            EncoderConfigParams cfgParams, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(encoder, mediaType, new EncoderConfigParams[]{cfgParams}, allTestParams);
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
    }

    /**
     * Check description of class {@link CodecEncoderBlockModelTest}
     */
    @ApiTest(apis = {"android.media.MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testSimpleEncode() throws IOException, InterruptedException {
        CodecEncoderTestBase referenceBase = new CodecEncoderTestBase(mCodecName, mMediaType,
                new EncoderConfigParams[]{mActiveEncCfg}, mAllTestParams);
        referenceBase.encodeToMemory(mCodecName, mActiveEncCfg, mActiveRawRes, Integer.MAX_VALUE,
                true, false);
        OutputManager ref = referenceBase.getOutputManager();

        boolean[] boolStates = {true, false};
        CodecEncoderBlockModelTestBase cebmtb = new CodecEncoderBlockModelTestBase(mCodecName,
                mMediaType, new EncoderConfigParams[]{mActiveEncCfg}, mAllTestParams);

        OutputManager testA = new OutputManager(ref.getSharedErrorLogs());
        OutputManager testB = new OutputManager(ref.getSharedErrorLogs());
        for (boolean eosType : boolStates) {
            OutputManager test = eosType ? testA : testB;
            cebmtb.encodeToMemory(mCodecName, mActiveEncCfg, mActiveRawRes, test, Integer.MAX_VALUE,
                    true, false, false, eosType);
            if (!ref.equalsDequeuedOutput(test)) {
                fail("Output in block model mode is not same as output in normal mode.\n"
                        + mTestConfig + mTestEnv + test.getErrMsg());
            }
        }
        if (!testA.equals(testB)) {
            fail("Output of encoder component is not consistent across runs. \n" + mTestConfig
                    + mTestEnv + testB.getErrMsg());
        }
    }
}
