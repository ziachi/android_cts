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
import android.mediapc.cts.common.Requirements.VideoDecoderSessionsRequirement;
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
 * The following test class calculates the maximum number of concurrent decode sessions that it can
 * support by the two hardware (mediaType - decoder) pair calculated via the
 * CodecCapabilities.getMaxSupportedInstances() and
 * VideoCapabilities.getSupportedPerformancePoints() methods. Splits the maximum supported instances
 * between the two pairs and ensures that all the supported sessions succeed in decoding
 * with meeting the expected frame rate.
 */
@RunWith(Parameterized.class)
public class MultiDecoderPairPerfTest extends MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiDecoderPairPerfTest.class.getSimpleName();
    private static final int REQUIRED_CONCURRENT_NON_SECURE_INSTANCES_WITH_SECURE = 3;

    private final Pair<String, String> mFirstPair;
    private final Pair<String, String> mSecondPair;

    public MultiDecoderPairPerfTest(Pair<String, String> firstPair, Pair<String, String> secondPair,
            boolean isAsync) {
        super(null, null, isAsync);
        mFirstPair = firstPair;
        mSecondPair = secondPair;
    }

    @Rule
    public final TestName mTestName = new TestName();

    // Returns the list of params with two hardware (mediaType - decoder) pairs in both
    // sync and async modes.
    // Parameters {0}_{1}_{2} -- Pair(MediaType DecoderName)_Pair(MediaType DecoderName)_isAsync
    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}")
    public static Collection<Object[]> inputParams() {
        final List<Object[]> argsList = new ArrayList<>();
        ArrayList<Pair<String, String>> mediaTypeDecoderPairs = new ArrayList<>();
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
                mediaTypeDecoderPairs.add(Pair.create(mediaType, decoder));
            }
        }
        for (int i = 0; i < mediaTypeDecoderPairs.size(); i++) {
            for (int j = i + 1; j < mediaTypeDecoderPairs.size(); j++) {
                Pair<String, String> pair1 = mediaTypeDecoderPairs.get(i);
                Pair<String, String> pair2 = mediaTypeDecoderPairs.get(j);
                for (boolean isAsync : boolStates) {
                    argsList.add(new Object[]{pair1, pair2, isAsync});
                }
            }
        }
        return argsList;
    }

    /**
     * This test calculates the number of 720p 30 fps decoder instances that the given two
     * (mediaType - decoder) pairs can support. Assigns the same number of instances to the two
     * pairs (if max instances are even), or one more to one pair (if odd) and ensures that all the
     * concurrent sessions succeed in decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-1", "2.2.7.1/5.1/H-1-2"})
    public void test720p() throws Exception {
        Assume.assumeTrue(Utils.isSPerfClass() || Utils.isRPerfClass() || !Utils.isPerfClass());
        Assume.assumeFalse("Skipping regular performance tests for secure codecs",
                isSecureSupportedCodec(mFirstPair.second, mFirstPair.first) ||
                        isSecureSupportedCodec(mSecondPair.second, mSecondPair.first));

        boolean hasVP9 = mFirstPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9) ||
                mSecondPair.first.equals(MediaFormat.MIMETYPE_VIDEO_VP9);
        int requiredMinInstances = getRequiredMinConcurrentInstances720p(hasVP9);
        testCodec(m720pTestFiles, null, 720, 1280, requiredMinInstances);
    }

    /**
     * This test calculates the number of 1080p 30 fps decoder instances that the given two
     * (mediaType - decoder) pairs can support. Assigns the same number of instances to the two
     * pairs (if max instances are even), or one more to one pair (if odd) and ensures that all the
     * concurrent sessions succeed in decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {
            "2.2.7.1/5.1/H-1-1",
            "2.2.7.1/5.1/H-1-2",
            "2.2.7.1/5.1/H-1-9",
            "2.2.7.1/5.1/H-1-10",})
    public void test1080p() throws Exception {
        Assume.assumeTrue(Utils.isTPerfClass() || !Utils.isPerfClass());
        boolean isFirstSecure = isSecureSupportedCodec(mFirstPair.second, mFirstPair.first);
        boolean isSecondSecure = isSecureSupportedCodec(mSecondPair.second, mSecondPair.first);
        boolean onlyOneSecure = isFirstSecure ^ isSecondSecure;
        boolean bothSecure = isFirstSecure & isSecondSecure;

        if (bothSecure) {
            testCodec(null, m1080pWidevineTestFiles, 1080, 1920,
                    REQUIRED_MIN_CONCURRENT_SECURE_INSTANCES);
        } else if (onlyOneSecure) {
            testCodec(m1080pTestFiles, m1080pWidevineTestFiles, 1080, 1920,
                    REQUIRED_CONCURRENT_NON_SECURE_INSTANCES_WITH_SECURE + 1);
        } else {
            testCodec(m1080pTestFiles, null, 1080, 1920, REQUIRED_MIN_CONCURRENT_INSTANCES);
        }
    }

    /**
     * This test calculates the number of 4k 30 fps SDR decoder instances that the given two
     * (mediaType - decoder) pairs can support. Assigns the same number of instances to the two
     * pairs (if max instances are even), or one more to one pair (if odd) and ensures that all the
     * concurrent sessions succeed in decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {
            "2.2.7.1/5.1/H-1-1",
            "2.2.7.1/5.1/H-1-2",
            "2.2.7.1/5.1/H-1-9",
            "2.2.7.1/5.1/H-1-10"})
    public void test4k() throws Exception {
        Assume.assumeTrue(Utils.isUPerfClass() || Utils.isVPerfClass() || !Utils.isPerfClass());
        boolean isFirstSecure = isSecureSupportedCodec(mFirstPair.second, mFirstPair.first);
        boolean isSecondSecure = isSecureSupportedCodec(mSecondPair.second, mSecondPair.first);
        boolean onlyOneSecure = isFirstSecure ^ isSecondSecure;
        boolean bothSecure = isFirstSecure & isSecondSecure;

        if (bothSecure) {
            testCodec(null, m1080pWidevineTestFiles, 2160, 3840,
                    REQUIRED_MIN_CONCURRENT_SECURE_INSTANCES);
        } else if (onlyOneSecure) {
            testCodec(m2160pPc14TestFiles, m2160pPc14WidevineTestFiles, 2160, 3840,
                    REQUIRED_CONCURRENT_NON_SECURE_INSTANCES_WITH_SECURE + 1);
        } else {
            testCodec(m2160pPc14TestFiles, null, 2160, 3840, REQUIRED_MIN_CONCURRENT_INSTANCES);
        }
    }

    /**
     * This test calculates the number of 4k 30 fps HDR decoder instances that the given two
     * (mediaType - decoder) pairs can support. Assigns the same number of instances to the two
     * pairs (if max instances are even), or one more to one pair (if odd) and ensures that all the
     * concurrent sessions succeed in decoding with meeting the expected frame rate.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {
            "2.2.7.1/5.1/H-1-9",
            "2.2.7.1/5.1/H-1-10"})
    public void test4kHbd() throws Exception {
        Assume.assumeTrue(Utils.isUPerfClass() || Utils.isVPerfClass() || !Utils.isPerfClass());
        boolean isFirstSecure = isSecureSupportedCodec(mFirstPair.second, mFirstPair.first);
        boolean isSecondSecure = isSecureSupportedCodec(mSecondPair.second, mSecondPair.first);
        boolean onlyOneSecure = isFirstSecure ^ isSecondSecure;
        boolean bothSecure = isFirstSecure & isSecondSecure;

        if (bothSecure) {
            testCodec(null, m1080pWidevine10bitTestFiles, 2160, 3840,
                    REQUIRED_MIN_CONCURRENT_SECURE_INSTANCES);
        } else if (onlyOneSecure) {
            // 2 non-secure 4k HDR, 1 secure 4k SDR , 1 non-secure 1080p SDR
            testCodec(m2160pPc1410bitTestFiles, m2160pPc14WidevineTestFiles, 2160, 3840,
                    REQUIRED_CONCURRENT_NON_SECURE_INSTANCES_WITH_SECURE + 1);
        } else {
            Assume.assumeFalse("Skipping regular performance tests for pair of non-secure decoders",
                true);
        }
    }

    private void testCodec(Map<String, String> testFiles, Map<String, String> widevineTestFiles,
            int height, int width, int requiredMinInstances) throws Exception {
        mTestFiles = testFiles;
        ArrayList<Pair<String, String>> mediaTypeDecoderPairs = new ArrayList<>();
        mediaTypeDecoderPairs.add(mFirstPair);
        mediaTypeDecoderPairs.add(mSecondPair);
        boolean isFirstSecure = isSecureSupportedCodec(mFirstPair.second, mFirstPair.first);
        boolean isSecondSecure = isSecureSupportedCodec(mSecondPair.second, mSecondPair.first);
        boolean secureWithUnsecure = isFirstSecure ^ isSecondSecure;
        boolean bothSecure = isFirstSecure & isSecondSecure;
        boolean noneSecure = !(isFirstSecure || isSecondSecure);
        int maxInstances = checkAndGetMaxSupportedInstancesForCodecCombinations(height, width,
                mediaTypeDecoderPairs, false, requiredMinInstances);
        double achievedFrameRate = 0.0;
        double frameDropsPerSec = 0.0;
        boolean meetsPreconditions = (isFirstSecure || isSecondSecure) ?
                meetsSecureDecodePreconditions() : true;
        // secure test should not reach this point if secure codec doesn't support PP
        if (meetsPreconditions && (maxInstances >= requiredMinInstances || secureWithUnsecure)) {
            int secondPairInstances = maxInstances / 2;
            int firstPairInstances = maxInstances - secondPairInstances;
            if (secureWithUnsecure) {
                firstPairInstances =
                        isSecureSupportedCodec(mFirstPair.second, mFirstPair.first) ? 1 : 3;
                secondPairInstances = requiredMinInstances - firstPairInstances;
                maxInstances = requiredMinInstances;
            }
            List<Decode> testList = new ArrayList<>();

            if (height > 1080 && secureWithUnsecure) {
                // Add 1 1080p instance for H-1-9
                if (isFirstSecure) {
                    String testFile = m1080pTestFiles.get(mSecondPair.first);
                    testList.add(new Decode(mSecondPair.first, testFile, mSecondPair.second,
                            mIsAsync, false));
                    secondPairInstances--;
                } else {
                    String testFile = m1080pTestFiles.get(mFirstPair.first);
                    testList.add(new Decode(mFirstPair.first, testFile, mFirstPair.second,
                            mIsAsync, false));
                    firstPairInstances--;
                }
            }

            for (int i = 0; i < firstPairInstances; i++) {
                boolean isSecure = isFirstSecure;
                String testFile = isSecure ? widevineTestFiles.get(mFirstPair.first) :
                        mTestFiles.get(mFirstPair.first);
                Assume.assumeTrue("Add " + (isSecure ? "secure" : "") + " test vector for"
                        + "mediaType: " + mFirstPair.first, testFile != null);
                testList.add(new Decode(mFirstPair.first, testFile, mFirstPair.second, mIsAsync,
                        isSecure));
            }
            for (int i = 0; i < secondPairInstances; i++) {
                boolean isSecure = isSecondSecure;
                String testFile = isSecure ? widevineTestFiles.get(mSecondPair.first) :
                        mTestFiles.get(mSecondPair.first);
                if (height > 1080 && noneSecure) testFile = m1080pTestFiles.get(mSecondPair.first);
                Assume.assumeTrue("Add " + (isSecure ? "secure" : "") + " test vector for"
                        + "mediaType: " + mSecondPair.first, testFile != null);
                testList.add(new Decode(mSecondPair.first, testFile, mSecondPair.second,
                        mIsAsync, isSecure));
            }
            CodecMetrics result = invokeWithThread(maxInstances, testList);
            achievedFrameRate = result.fps();
            frameDropsPerSec = result.fdps();
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        if (secureWithUnsecure) {
            VideoDecoderSessionsRequirement r5_1__H_1_10 = (height > 1080)
                    ? Requirements.addR5_1__H_1_10().withConfig4K().to(pce)
                    : Requirements.addR5_1__H_1_10().withConfig1080P().to(pce);
            r5_1__H_1_10.setConcurrentFps(achievedFrameRate);
            r5_1__H_1_10.setFrameDropsPerSec(frameDropsPerSec);
        } else if (bothSecure) {
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

                if (isMPCCodec(mFirstPair.first, mSecondPair.first)) {
                    if (isRCodec(mFirstPair.first, mSecondPair.first)) {
                        r5_1__H_1_1 = Requirements.addR5_1__H_1_1().withConfig720P().to(pce);
                        r5_1__H_1_2 = Requirements.addR5_1__H_1_2().withConfig720P().to(pce);
                    } else if (isVP9Codec(mFirstPair.first, mSecondPair.first)) {
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
