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

package android.video.cts;

import static org.junit.Assert.assertTrue;

import android.media.MediaFormat;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.CodecTestBase.ComponentClass;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The CDD requires devices to support certain combinations of resolution+framerate for video
 * processing. Some of these requirements vary based on the presence of a hardware codec.
 * <p>
 * This test is an extension to {@link VideoCodecClaimsPerformanceTest} which checks whether the
 * device claims to support at least one of H.265 or VP9 decoding of 720, 1080 and UHD profiles.
 */
@RunWith(Parameterized.class)
public class HevcVp9ClaimsPerformanceTest {
    private final List<VideoCodecClaimsPerformanceTestBase> mBaseInstances = new ArrayList<>();

    public HevcVp9ClaimsPerformanceTest(List<String> mediaTypes, int width, int height,
            int fps, boolean isEncoder, ComponentClass componentClass, String allTestParams) {
        for (String mediaType : mediaTypes) {
            mBaseInstances.add(
                    new VideoCodecClaimsPerformanceTestBase(mediaType, width, height, fps,
                            isEncoder, componentClass, allTestParams));
        }
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}_{3}_{4}")
    public static Collection<Object[]> input() {
        final boolean isDispHtAtleastUHD = CodecTestBase.MAX_DISPLAY_HEIGHT_LAND >= 2160;
        final boolean isDispHtAtleastFHD = CodecTestBase.MAX_DISPLAY_HEIGHT_LAND >= 1080;
        final boolean isDispHtAtleastHD = CodecTestBase.MAX_DISPLAY_HEIGHT_LAND >= 720;

        // mediaTypes, width, height, fps, isEncoder, componentClass
        final List<Object[]> argsList = new ArrayList<>();

        // Video Decoder Requirements
        // hevc, vp9
        // 5.3.5/C-2-1, 5.3.7/C-3-1
        if (isDispHtAtleastHD) {
            argsList.add(new Object[]{new ArrayList<>(Arrays.asList(MediaFormat.MIMETYPE_VIDEO_HEVC,
                    MediaFormat.MIMETYPE_VIDEO_VP9)), 1280, 720, 30, false, ComponentClass.ALL});
        }
        if (isDispHtAtleastFHD) {
            argsList.add(new Object[]{new ArrayList<>(Arrays.asList(MediaFormat.MIMETYPE_VIDEO_HEVC,
                    MediaFormat.MIMETYPE_VIDEO_VP9)), 1920, 1080, 30, false, ComponentClass.ALL});
            if (MediaUtils.isTv()) {
                argsList.add(new Object[]{new ArrayList<>(
                        Arrays.asList(MediaFormat.MIMETYPE_VIDEO_HEVC,
                                MediaFormat.MIMETYPE_VIDEO_VP9)), 1920, 1080, 60, false,
                        ComponentClass.HARDWARE});
            }
        }
        if (isDispHtAtleastUHD) {
            argsList.add(new Object[]{new ArrayList<>(Arrays.asList(MediaFormat.MIMETYPE_VIDEO_HEVC,
                    MediaFormat.MIMETYPE_VIDEO_VP9)), 3840, 2160, 60, false, ComponentClass.ALL});
        }

        final List<Object[]> updatedArgsList = new ArrayList<>();
        for (Object[] arg : argsList) {
            int argLength = arg.length;
            Object[] argUpdate = new Object[argLength + 1];
            System.arraycopy(arg, 0, argUpdate, 0, argLength);
            argUpdate[argLength] = CodecTestBase.paramToString(argUpdate);
            updatedArgsList.add(argUpdate);
        }
        return updatedArgsList;
    }

    /**
     * Check description of class {@link HevcVp9ClaimsPerformanceTest}
     */
    @CddTest(requirements = {"5.3.5/C-2-1", "5.3.7/C-3-1"})
    @SmallTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testDeviceClaimsPerformanceSupported() {
        boolean result = false;
        StringBuilder testConfig = new StringBuilder();
        for (VideoCodecClaimsPerformanceTestBase baseInstance : mBaseInstances) {
            result |= baseInstance.deviceClaimsPerformanceSupported();
            testConfig.append(baseInstance.mTestConfig);
        }
        assertTrue(testConfig.toString(), result);
    }
}
