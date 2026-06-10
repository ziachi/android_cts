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
import android.mediapc.cts.common.Requirements.ConcurrentVideoEncoderSessionsRequirement;
import android.mediapc.cts.common.Requirements.VideoEncoderInstancesRequirement;
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

/**
 * The following test class validates the maximum number of concurrent encode sessions that it can
 * support by the hardware encoders calculated via the CodecCapabilities.getMaxSupportedInstances()
 * and VideoCapabilities.getSupportedPerformancePoints() methods. And also ensures that the maximum
 * supported sessions succeed in encoding.
 * Achieved frame rate is not compared as this test runs in byte buffer mode.
 */
@RunWith(Parameterized.class)
public class MultiEncoderPerfTest extends MultiCodecPerfTestBase {
    private static final String LOG_TAG = MultiEncoderPerfTest.class.getSimpleName();

    private final String mEncoderName;

    public MultiEncoderPerfTest(String mediaType, String encoderName, boolean isAsync) {
        super(mediaType, null, isAsync);
        mEncoderName = encoderName;
    }

    @Rule
    public final TestName mTestName = new TestName();

    // Returns the params list with the mediaType and their hardware encoders in
    // both sync and async modes.
    // Parameters {0}_{2}_{3} -- MediaType_EncoderName_isAsync
    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}")
    public static Collection<Object[]> inputParams() {
        final List<Object[]> argsList = new ArrayList<>();
        for (String mediaType : mMediaTypeList) {
            if (mediaTypePrefix != null && !mediaType.startsWith(mediaTypePrefix)) {
                continue;
            }
            ArrayList<String> listOfEncoders = getHardwareCodecsForMediaTypes(mediaType, true);
            for (String encoder : listOfEncoders) {
                if ((codecPrefix != null && !encoder.startsWith(codecPrefix))
                        || (codecFilter != null && !codecFilter.matcher(encoder).matches())) {
                    continue;
                }
                for (boolean isAsync : boolStates) {
                    argsList.add(new Object[]{mediaType, encoder, isAsync});
                }
            }
        }
        return argsList;
    }

    /**
     * This test validates that the encoder can support at least 6 concurrent 720p 30fps
     * encoder instances. Also ensures that all the concurrent sessions succeed in encoding.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-3", "2.2.7.1/5.1/H-1-4"})
    public void test720p() throws Exception {
        Assume.assumeTrue(Utils.isSPerfClass() || Utils.isRPerfClass() || !Utils.isPerfClass());

        boolean hasVP9 = mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_VP9);
        int requiredMinInstances = getRequiredMinConcurrentInstances720p(hasVP9);
        testCodec(720, 1280, 4000000, requiredMinInstances);
    }

    /**
     * This test validates that the encoder can support at least 6 concurrent 1080p 30fps
     * encoder instances. Also ensures that all the concurrent sessions succeed in encoding.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-3", "2.2.7.1/5.1/H-1-4"})
    public void test1080p() throws Exception {
        Assume.assumeTrue(Utils.isTPerfClass() || !Utils.isPerfClass());
        testCodec(1080, 1920, 10000000, REQUIRED_MIN_CONCURRENT_INSTANCES);
    }

    /**
     * This test validates that the encoder can support at least 6 concurrent encoder instances
     * with 4 sessions at 1080p 30 fps and 2 sessions at 4k 30 fps. Also ensures that all the
     * concurrent sessions succeed in encoding.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-3", "2.2.7.1/5.1/H-1-4"})
    public void test4k() throws Exception {
        Assume.assumeTrue(Utils.isUPerfClass() || Utils.isVPerfClass() || !Utils.isPerfClass());
        testCodec(2160, 3840, 30000000, REQUIRED_MIN_CONCURRENT_INSTANCES);
    }

    private void testCodec(int height, int width, int bitrate, int requiredMinInstances)
            throws Exception {
        ArrayList<Pair<String, String>> mediaTypeEncoderPairs = new ArrayList<>();
        mediaTypeEncoderPairs.add(Pair.create(mMediaType, mEncoderName));
        int maxInstances = checkAndGetMaxSupportedInstancesForCodecCombinations(height, width,
                mediaTypeEncoderPairs, true, requiredMinInstances);
        double achievedFrameRate = 0.0;
        double frameDropsPerSec = 0.0;
        boolean hasAV1 = mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_AV1);
        if (maxInstances >= requiredMinInstances) {
            List<Encode> testList = new ArrayList<>();
            if (height > 1080) {
                int instances4k = maxInstances / 3;
                int instances1080p = maxInstances - instances4k;
                for (int i = 0; i < instances4k; i++) {
                    if (hasAV1) {
                        testList.add(new Encode(mMediaType, mEncoderName, mIsAsync, 1080, 1920, 30,
                                10000000));
                    } else {
                        testList.add(new Encode(mMediaType, mEncoderName, mIsAsync, height, width,
                                30, bitrate));
                    }
                }
                for (int i = 0; i < instances1080p; i++) {
                    testList.add(
                            new Encode(mMediaType, mEncoderName, mIsAsync, 1080, 1920, 30,
                                    10000000));
                }
            } else {
                for (int i = 0; i < maxInstances; i++) {
                    testList.add(
                            new Encode(mMediaType, mEncoderName, mIsAsync, height, width, 30,
                                    bitrate));
                }
            }
            CodecMetrics result = invokeWithThread(maxInstances, testList);
            achievedFrameRate = result.fps();
            frameDropsPerSec = result.fdps();
        }
        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        VideoEncoderInstancesRequirement r5_1__H_1_3;
        ConcurrentVideoEncoderSessionsRequirement r5_1__H_1_4;
        // Achieved frame rate is not compared as this test runs in byte buffer mode.
        if (height > 1080) {
            r5_1__H_1_3 = Requirements.addR5_1__H_1_3().withConfig4K().to(pce);
            r5_1__H_1_4 = Requirements.addR5_1__H_1_4().withConfig4K()
                    .withVariantByteBufferMode().to(pce);
            r5_1__H_1_3.setConcurrentSessions(maxInstances);
            r5_1__H_1_4.setConcurrentFps(achievedFrameRate);
            r5_1__H_1_4.setFrameDropsPerSec(frameDropsPerSec);
        } else if (height == 1080) {
            r5_1__H_1_3 = Requirements.addR5_1__H_1_3().withConfig1080P().to(pce);
            r5_1__H_1_4 = Requirements.addR5_1__H_1_4().withConfig1080P()
                    .withVariantByteBufferMode().to(pce);
            r5_1__H_1_3.setConcurrentSessions(maxInstances);
            r5_1__H_1_4.setConcurrentFps(achievedFrameRate);
            r5_1__H_1_4.setFrameDropsPerSec(frameDropsPerSec);
        } else {
            r5_1__H_1_4 = Requirements.addR5_1__H_1_4().withConfig720P()
                    .withVariantByteBufferMode().to(pce);
            r5_1__H_1_4.setConcurrentFps(achievedFrameRate);

            if (isMPCCodec(mMediaType)) {
                if (isRCodec(mMediaType)) {
                    r5_1__H_1_3 = Requirements.addR5_1__H_1_3().withConfig720P().to(pce);
                } else if (isVP9Codec(mMediaType)) {
                    r5_1__H_1_3 = Requirements.addR5_1__H_1_3().withConfig720P()
                            .withVariantVP9().to(pce);
                } else {
                    r5_1__H_1_3 = Requirements.addR5_1__H_1_3().withConfig720P()
                            .withVariantAV1().to(pce);
                }
                r5_1__H_1_3.setConcurrentSessions(maxInstances);
                r5_1__H_1_4.setFrameDropsPerSec(frameDropsPerSec);
            }
        }

        pce.submitAndCheck();
    }
}
