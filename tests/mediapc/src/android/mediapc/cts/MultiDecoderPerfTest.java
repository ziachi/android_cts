/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.mediapc.cts;

import static android.mediapc.cts.CodecTestBase.codecFilter;
import static android.mediapc.cts.CodecTestBase.codecPrefix;
import static android.mediapc.cts.CodecTestBase.mediaTypePrefix;

import android.media.MediaFormat;
import android.mediapc.cts.common.CodecMetrics;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Requirements;
import android.mediapc.cts.common.Requirements.ConcurrentVideoDecoderSessionsRequirement;
import android.mediapc.cts.common.Requirements.SecureVideoDecoderSessionsRequirement;
import android.mediapc.cts.common.Requirements.VideoDecoderInstancesRequirement;
import android.mediapc.cts.common.Utils;
import android.util.Pair;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The following test class validates the maximum number of concurrent decode sessions that it can
 * support by the hardware decoders calculated via the CodecCapabilities.getMaxSupportedInstances()
 * and VideoCapabilities.getSupportedPerformancePoints() methods. And also ensures that the maximum
 * supported sessions succeed in decoding with meeting the expected frame rate.
 */
@RunWith(Parameterized.class)
public class MultiDecoderPerfTest extends MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiDecoderPerfTest.class.getSimpleName();

    private final String mDecoderName;

    public MultiDecoderPerfTest(String mediaType, String decoderName, boolean isAsync) {
        super(mediaType, null, isAsync);
        mDecoderName = decoderName;
    }

    @Rule
    public final TestName mTestName = new TestName();

    // Returns the params list with the mediaType and corresponding hardware decoders in
    // both sync and async modes.
    // Parameters {0}_{1}_{2} -- MediaType_DecoderName_isAsync
    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}")
    public static Collection<Object[]> inputParams() {
        final List<Object[]> argsList = new ArrayList<>();
        for (String mediaType : mMediaTypeList) {
            if (mediaTypePrefix != null && !mediaType.startsWith(mediaTypePrefix)) {
                continue;
            }
            ArrayList<String> listOfDecoders =
                    getHardwareCodecsForMediaTypes(mediaType, false, true);
            for (String decoder : listOfDecoders) {
                if ((codecPrefix != null && !decoder.startsWith(codecPrefix))
                        || (codecFilter != null && !codecFilter.matcher(decoder).matches())) {
                    continue;
                }
                for (boolean isAsync : boolStates) {
                    argsList.add(new Object[]{mediaType, decoder, isAsync});
                }
            }
        }
        return argsList;
    }

    /**
     * This test validates that the decoder can support at least 6 concurrent 720p 30fps
     * decoder instances. Also ensures that all the concurrent sessions succeed in decoding
     * with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-1", "2.2.7.1/5.1/H-1-2"})
    public void test720p() throws Exception {
        Assume.assumeTrue(Utils.isSPerfClass() || Utils.isRPerfClass() || !Utils.isPerfClass());
        Assume.assumeFalse("Skipping regular performance tests for secure codecs",
                isSecureSupportedCodec(mDecoderName, mMediaType));
        boolean hasVP9 = mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_VP9);
        int requiredMinInstances = getRequiredMinConcurrentInstances720p(hasVP9);
        testCodec(m720pTestFiles, 720, 1280, requiredMinInstances);
    }

    /**
     * This test validates that the decoder can support at least 6 non-secure/2 secure concurrent
     * 1080p 30fps decoder instances. Also ensures that all the concurrent sessions succeed in
     * decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {
            "2.2.7.1/5.1/H-1-1",
            "2.2.7.1/5.1/H-1-2",
            "2.2.7.1/5.1/H-1-9",})
    public void test1080p() throws Exception {
        Assume.assumeTrue(Utils.isTPerfClass() || !Utils.isPerfClass());
        if (isSecureSupportedCodec(mDecoderName, mMediaType)) {
            testCodec(m1080pWidevineTestFiles, 1080, 1920,
                    REQUIRED_MIN_CONCURRENT_SECURE_INSTANCES);
        } else {
            testCodec(m1080pTestFiles, 1080, 1920, REQUIRED_MIN_CONCURRENT_INSTANCES);
        }
    }

    /**
     * This test validates that the decoder can support at least 6 SDR non-secure concurrent
     * instances with 3 sessions at 1080p 30 fps and 3 sessions at 4k 30fps / 2 SDR secure
     * concurrent instances at 1080 30 fps. Also ensures that all the concurrent sessions succeed
     * in decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {
            "2.2.7.1/5.1/H-1-1",
            "2.2.7.1/5.1/H-1-2",
            "2.2.7.1/5.1/H-1-9",})
    public void test4k() throws Exception {
        Assume.assumeTrue(Utils.isUPerfClass() || Utils.isVPerfClass() || !Utils.isPerfClass());

        if (isSecureSupportedCodec(mDecoderName, mMediaType)) {
            testCodec(m1080pWidevineTestFiles, 2160, 3840,
                    REQUIRED_MIN_CONCURRENT_SECURE_INSTANCES);
        } else {
            testCodec(m2160pPc14TestFiles, 2160, 3840, REQUIRED_MIN_CONCURRENT_INSTANCES);
        }
    }

    /**
     * This test validates that the decoder can support at least 2 HDR secure concurrent instances
     * at 1080 30 fps. Also ensures that all the concurrent sessions succeed in decoding with
     * meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-9"})
    public void test1080pHbd() throws Exception {
        Assume.assumeTrue(Utils.isUPerfClass() || Utils.isVPerfClass() || !Utils.isPerfClass());
        Assume.assumeTrue("Skipping regular performance tests for non-secure codecs",
                isSecureSupportedCodec(mDecoderName, mMediaType));
        testCodec(m1080pWidevine10bitTestFiles, 2160, 3840,
                REQUIRED_MIN_CONCURRENT_SECURE_INSTANCES);
    }

    private void testCodec(Map<String, String> testFiles, int height, int width,
            int requiredMinInstances) throws Exception {
        mTestFile = testFiles.get(mMediaType);
        Assume.assumeTrue("Add test vector for mediaType: " + mMediaType, mTestFile != null);
        ArrayList<Pair<String, String>> mediaTypeDecoderPairs = new ArrayList<>();
        mediaTypeDecoderPairs.add(Pair.create(mMediaType, mDecoderName));
        boolean isSecure = isSecureSupportedCodec(mDecoderName, mMediaType);
        int maxInstances = checkAndGetMaxSupportedInstancesForCodecCombinations(height, width,
                mediaTypeDecoderPairs, false, requiredMinInstances);
        double achievedFrameRate = 0.0;
        double frameDropsPerSec = 0.0;
        boolean meetsPreconditions = isSecure ? meetsSecureDecodePreconditions() : true;

        if (meetsPreconditions && maxInstances >= requiredMinInstances) {
            List<Decode> testList = new ArrayList<>();
            if (height > 1080 && !isSecure) {
                int halfMaxInstances = maxInstances / 2;
                String testFile1080p = m1080pTestFiles.get(mMediaType);
                for (int i = 0; i < halfMaxInstances; i++) {
                    testList.add(new Decode(mMediaType, testFile1080p, mDecoderName, mIsAsync,
                            isSecure));
                    testList.add(new Decode(mMediaType, mTestFile, mDecoderName, mIsAsync,
                            isSecure));
                }
            } else {
                for (int i = 0; i < maxInstances; i++) {
                    testList.add(new Decode(mMediaType, mTestFile, mDecoderName, mIsAsync,
                            isSecure));
                }
            }
            CodecMetrics result = invokeWithThread(maxInstances, testList);
            achievedFrameRate = result.fps();
            frameDropsPerSec = result.fdps();
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        if (isSecure) {
            SecureVideoDecoderSessionsRequirement r5_1__H_1_9 = (height > 1080)
                    ? Requirements.addR5_1__H_1_9().withConfigHdr().to(pce)
                    : Requirements.addR5_1__H_1_9().to(pce);
            r5_1__H_1_9.setFrameDropsPerSec(frameDropsPerSec);
            r5_1__H_1_9.setConcurrentFps(achievedFrameRate);
        } else {
            VideoDecoderInstancesRequirement r5_1__H_1_1;
            ConcurrentVideoDecoderSessionsRequirement r5_1__H_1_2;
            if (height > 1080) {
                r5_1__H_1_1 = Requirements.addR5_1__H_1_1().withConfig4K().to(pce);
                r5_1__H_1_2 = Requirements.addR5_1__H_1_2().withConfig4K().to(pce);
                r5_1__H_1_1.setConcurrentSessions(maxInstances);
                r5_1__H_1_2.setConcurrentFps(achievedFrameRate);
                r5_1__H_1_2.setFrameDropsPerSec(frameDropsPerSec);
            } else if (height == 1080) {
                r5_1__H_1_1 = Requirements.addR5_1__H_1_1().withConfig1080P().to(pce);
                r5_1__H_1_2 = Requirements.addR5_1__H_1_2().withConfig1080P().to(pce);
                r5_1__H_1_1.setConcurrentSessions(maxInstances);
                r5_1__H_1_2.setConcurrentFps(achievedFrameRate);
                r5_1__H_1_2.setFrameDropsPerSec(frameDropsPerSec);
            } else {

                if (isMPCCodec(mMediaType)) {
                    if (isRCodec(mMediaType)) {
                        r5_1__H_1_1 = Requirements.addR5_1__H_1_1().withConfig720P().to(pce);
                        r5_1__H_1_2 = Requirements.addR5_1__H_1_2().withConfig720P().to(pce);
                    } else if (isVP9Codec(mMediaType)) {
                        r5_1__H_1_1 = Requirements.addR5_1__H_1_1().withConfig720P()
                                .withVariantVP9().to(pce);
                        r5_1__H_1_2 = Requirements.addR5_1__H_1_2().withConfig720P()
                                .withVariantVP9().to(pce);
                    } else {
                        r5_1__H_1_1 = Requirements.addR5_1__H_1_1().withConfig720P()
                                .withVariantAV1().to(pce);
                        r5_1__H_1_2 = Requirements.addR5_1__H_1_2().withConfig720P()
                                .withVariantAV1().to(pce);
                    }
                    r5_1__H_1_1.setConcurrentSessions(maxInstances);
                    r5_1__H_1_2.setConcurrentFps(achievedFrameRate);
                    r5_1__H_1_2.setFrameDropsPerSec(frameDropsPerSec);
                }
            }
        }
        pce.submitAndCheck();
    }
}
