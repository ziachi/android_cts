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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The CDD requires devices to support certain combinations of resolution+framerate for video
 * processing. Some of these requirements vary based on the presence of a hardware codec.
 * <p>
 * This test verifies that a device claims to support the required combinations. The test does
 * not verify that the device actually delivers the claimed performance; such testing is done in
 * other tests that verify whether the codecs perform at the level they claim. The combination of
 * "does the device claim to meet..." and "do all codecs meet their individual claims..." allows
 * us to determine whether the device does deliver the required performance.
 * <p>
 * The tests that measure actual performance include:
 * {@link android.video.cts.CodecEncoderPerformanceTest}
 * {@link android.video.cts.CodecDecoderPerformanceTest}
 * {@link android.video.cts.VideoEncoderDecoderTest}
 * {@link android.media.decoder.cts.VideoDecoderPerfTest}
 */
@RunWith(Parameterized.class)
public class VideoCodecClaimsPerformanceTest extends VideoCodecClaimsPerformanceTestBase {
    public VideoCodecClaimsPerformanceTest(String mediaType, int width, int height, int fps,
            boolean isEncoder, ComponentClass componentClass, String allTestParams) {
        super(mediaType, width, height, fps, isEncoder, componentClass, allTestParams);
    }

    @Rule
    public TestName mTestName = new TestName();

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}_{3}_{4}")
    public static Collection<Object[]> input() {
        final boolean isTv = MediaUtils.isTv();
        final boolean isDispHtAtleastFHD = CodecTestBase.MAX_DISPLAY_HEIGHT_LAND >= 1080;
        final boolean isDispHtAtleastHD = CodecTestBase.MAX_DISPLAY_HEIGHT_LAND >= 720;

        // mediaType, width, height, fps, isEncoder, componentClass
        final List<Object[]> argsList = new ArrayList<>();
        // TODO(b/275171549) Add tests as per TV multimedia requirements in 2.3.2

        // Video Encoder Requirements
        // avc
        // 5.2.2/C-1-2
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_AVC, 320, 240, 20, true,
                        ComponentClass.ALL});
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_AVC, 720, 480, 30, true,
                        ComponentClass.ALL});

        // vp8
        // 5.2.3/C-1-1
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_VP8, 320, 180, 30, true,
                        ComponentClass.ALL});
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_VP8, 640, 360, 30, true,
                        ComponentClass.ALL});

        // av1
        // 5.2.6/C-2-1
        if (CodecTestBase.IS_AT_LEAST_U) {
            argsList.add(
                    new Object[]{MediaFormat.MIMETYPE_VIDEO_AV1, 720, 480, 30, true,
                            ComponentClass.HARDWARE});
            argsList.add(
                    new Object[]{MediaFormat.MIMETYPE_VIDEO_AV1, 1280, 720, 30, true,
                            ComponentClass.HARDWARE});
            argsList.add(
                    new Object[]{MediaFormat.MIMETYPE_VIDEO_AV1, 1920, 1080, 30, true,
                            ComponentClass.HARDWARE});
        }

        // Video Decoder Requirements
        // avc
        // 5.3.4/C-1-2
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_AVC, 320, 240, 30, false,
                        ComponentClass.ALL});
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_AVC, 720, 480, 30, false,
                        ComponentClass.ALL});
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720, 30, false,
                        ComponentClass.ALL});
        if (isDispHtAtleastHD) {
            // 5.3.4/C-2-1
            argsList.add(
                    new Object[]{MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720, 60, false,
                            ComponentClass.ALL});
        }
        if (isDispHtAtleastFHD) {
            // 5.3.4/C-2-2
            argsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080,
                    isTv ? 60 : 30, false, ComponentClass.ALL});
        }

        // hevc
        // 5.3.5/C-1-1
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_HEVC, 352, 288, 30, false,
                        ComponentClass.ALL});
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_HEVC, 720, 480, 30, false,
                        ComponentClass.ALL});
        // 5.3.5/C-1-2
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_HEVC, 1280, 720, 30, false,
                        ComponentClass.HARDWARE});
        argsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080,
                isTv ? 60 : 30, false, ComponentClass.HARDWARE});

        // vp8
        // 5.3.6/C-1-1
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_VP8, 320, 180, 30, false,
                        ComponentClass.ALL});
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_VP8, 640, 360, 30, false,
                        ComponentClass.ALL});

        // vp9
        // 5.3.7/C-1-1
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_VP9, 320, 180, 30, false,
                        ComponentClass.ALL});
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_VP9, 640, 360, 30, false,
                        ComponentClass.ALL});
        // 5.3.7/C-2-1
        argsList.add(
                new Object[]{MediaFormat.MIMETYPE_VIDEO_VP9, 1280, 720, 30, false,
                        ComponentClass.HARDWARE});
        argsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_VP9, 1920, 1080,
                isTv ? 60 : 30, false, ComponentClass.HARDWARE});

        // av1
        if (CodecTestBase.IS_AT_LEAST_U) {
            argsList.add(
                    new Object[]{MediaFormat.MIMETYPE_VIDEO_AV1, 720, 480, 30, false,
                            ComponentClass.ALL});
            // 5.3.9/C-2-1
            if (isDispHtAtleastHD) {
                argsList.add(
                        new Object[]{MediaFormat.MIMETYPE_VIDEO_AV1, 1280, 720, 30, false,
                                ComponentClass.HARDWARE});
            }
            // 5.3.9/C-2-2
            if (isDispHtAtleastFHD) {
                argsList.add(
                        new Object[]{MediaFormat.MIMETYPE_VIDEO_AV1, 1920, 1080, 30, false,
                                ComponentClass.HARDWARE});
            }
        }

        int argLength = argsList.get(0).length;
        final List<Object[]> updatedArgsList = new ArrayList<>();
        for (Object[] arg : argsList) {
            Object[] argUpdate = new Object[argLength + 1];
            System.arraycopy(arg, 0, argUpdate, 0, argLength);
            argUpdate[argLength] = CodecTestBase.paramToString(argUpdate);
            updatedArgsList.add(argUpdate);
        }
        return updatedArgsList;
    }

    /**
     * Check description of class {@link VideoCodecPerfCapabilityTest}
     */
    @CddTest(requirements = {"5.2.2/C-1-2", "5.2.3/C-1-1", "5.2.6/C-2-1", "5.3.4/C-1-2",
            "5.3.4/C-2-1", "5.3.4/C-2-2", "5.3.5/C-1-1", "5.3.5/C-1-2", "5.3.6/C-1-1",
            "5.3.6/C-2-1", "5.3.6/C-2-2", "5.3.7/C-1-1", "5.3.7/C-2-1", "5.3.9/C-1-1",
            "5.3.9/C-2-1", "5.3.9/C-2-2"})
    @SmallTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testDeviceClaimsPerformanceSupported() {
        boolean result = deviceClaimsPerformanceSupported();
        assertTrue(mTestConfig.toString(), result);
    }
}
