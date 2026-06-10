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

import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;
import static android.mediav2.cts.CodecDecoderMultiAccessUnitTest.exhaustiveArgsList;

import static org.junit.Assert.fail;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderBlockModelTestBase;
import android.mediav2.common.cts.CodecDecoderTestBase;
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
import java.util.Collection;

/**
 * Tests audio decoders in single access unit block model mode.
 * <p>
 * The decoding happens in asynchronous mode with a. eos flag signalled with last compressed frame
 * and b. eos flag not signalled with last compressed frame.
 * <p>
 * The test verifies if the component / framework output is consistent with single access unit
 * normal mode and single access unit block model mode.
 **/
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public class CodecDecoderBlockModelTest extends CodecDecoderBlockModelTestBase {
    private static final String LOG_TAG = CodecDecoderBlockModelTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        return prepareParamList(exhaustiveArgsList, false, true, false, true, ComponentClass.ALL);
    }

    public CodecDecoderBlockModelTest(String decoder, String mediaType,
            String testFile, String allTestParams) {
        super(decoder, mediaType, MEDIA_DIR + testFile, allTestParams);
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
     * Check description of class {@link CodecDecoderBlockModelTest}
     */
    @ApiTest(apis = {"android.media.MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testSimpleDecode() throws IOException, InterruptedException {
        CodecDecoderTestBase cdtb = new CodecDecoderTestBase(mCodecName, mMediaType, null,
                mAllTestParams);
        cdtb.decodeToMemory(mTestFile, mCodecName, 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC,
                Integer.MAX_VALUE);
        OutputManager ref = cdtb.getOutputManager();

        boolean[] boolStates = {true, false};
        CodecDecoderBlockModelTestBase cdbmtb = new CodecDecoderBlockModelTestBase(
                mCodecName, mMediaType, null, mAllTestParams);
        for (boolean signalEosWithLastFrame : boolStates) {
            OutputManager test = new OutputManager(ref.getSharedErrorLogs());
            cdbmtb.decodeToMemory(mTestFile, mCodecName, test, 0,
                    MediaExtractor.SEEK_TO_CLOSEST_SYNC, Integer.MAX_VALUE, false,
                    signalEosWithLastFrame);
            if (!ref.equals(test)) {
                fail("Output in block model mode is not same as output in normal mode. \n"
                        + mTestConfig + mTestEnv + test.getErrMsg());
            }
        }
    }
}
