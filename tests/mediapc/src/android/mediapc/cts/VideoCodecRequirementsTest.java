/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_Format32bitABGR2101010;
import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_DynamicColorAspects;
import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_HlgEditing;
import static android.media.MediaCodecInfo.CodecProfileLevel.AV1Level51;
import static android.media.MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10;
import static android.media.MediaCodecInfo.CodecProfileLevel.AV1ProfileMain8;
import static android.media.MediaFormat.MIMETYPE_VIDEO_AV1;
import static android.mediapc.cts.CodecTestBase.SELECT_HARDWARE;
import static android.mediapc.cts.CodecTestBase.SELECT_VIDEO;
import static android.mediapc.cts.CodecTestBase.getCodecCapabilities;
import static android.mediapc.cts.CodecTestBase.getCodecInfo;
import static android.mediapc.cts.CodecTestBase.getMediaTypesOfAvailableCodecs;
import static android.mediapc.cts.CodecTestBase.selectCodecs;
import static android.mediapc.cts.CodecTestBase.selectHardwareCodecs;
import static android.mediav2.common.cts.CodecTestBase.isDefaultCodec;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static java.lang.Math.max;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities.PerformancePoint;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.codec.Flags;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Requirements;
import android.mediapc.cts.common.Utils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;
import android.util.Range;
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public class VideoCodecRequirementsTest {
    private static final String LOG_TAG = VideoCodecRequirementsTest.class.getSimpleName();
    private static final String INPUT_FILE = "bbb_3840x2160_AVIF.avif";

    @Rule
    public final TestName mTestName = new TestName();

    @Before
    public void isPerformanceClassCandidate() {
        Utils.assumeDeviceMeetsPerformanceClassPreconditions();
    }

    private boolean decodeAVIF(File inputfile) throws IOException {
        ImageDecoder.Source src = ImageDecoder.createSource(inputfile);
        Bitmap bm = ImageDecoder.decodeBitmap(src);
        return true;
    }

    private Set<String> get4k60HwCodecSet(boolean isEncoder) throws IOException {
        Set<String> codecSet = new HashSet<>();
        Set<String> codecMediaTypes = getMediaTypesOfAvailableCodecs(SELECT_VIDEO, SELECT_HARDWARE);
        PerformancePoint PP4k60 = new PerformancePoint(3840, 2160, 60);
        for (String codecMediaType : codecMediaTypes) {
            ArrayList<String> hwVideoCodecs =
                    selectHardwareCodecs(codecMediaType, null, null, isEncoder);
            for (String hwVideoCodec : hwVideoCodecs) {
                CodecCapabilities capabilities = getCodecCapabilities(hwVideoCodec, codecMediaType);
                assertNotNull("did not receive capabilities for codec: " + hwVideoCodec
                        + ", media type: " + codecMediaType + "\n", capabilities);
                List<PerformancePoint> pps =
                        capabilities.getVideoCapabilities().getSupportedPerformancePoints();
                assertTrue(hwVideoCodec + " doesn't advertise performance points", pps.size() > 0);
                for (PerformancePoint pp : pps) {
                    if (pp.covers(PP4k60)) {
                        codecSet.add(hwVideoCodec);
                        Log.d(LOG_TAG,
                                "Performance point 4k60 supported by codec: " + hwVideoCodec);
                        break;
                    }
                }
            }
        }
        return codecSet;
    }

    @Nullable
    private static Size getMaxSupportedRecordingSize() throws CameraAccessException {
        if (!MediaUtils.hasCamera()) return null;

        Context context = getInstrumentation().getTargetContext();
        CameraManager cm = context.getSystemService(CameraManager.class);
        String[] cameraIdList = cm.getCameraIdList();

        for (String cameraId : cameraIdList) {
            CameraCharacteristics characteristics = cm.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                return Arrays.stream(map.getOutputSizes(MediaRecorder.class))
                        .max(Comparator.comparingInt(size -> size.getWidth() * size.getHeight()))
                        .orElse(null);
            }
        }
        return null;
    }

    /**
     * Validates if a hardware decoder that supports 4k60 is present
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-15"})
    public void test4k60Decoder() throws IOException {
        Set<String> decoderSet = get4k60HwCodecSet(false);

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        Requirements.HardwareDecoder4K60Requirement r4k60HwDecoder =
                Requirements.addR5_1__H_1_15().to(pce);
        r4k60HwDecoder.setNumber4KHwDecoders(decoderSet.size());

        pce.submitAndCheck();
    }

    /**
     * Validates if a hardware encoder that supports 4k60 is present
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-16"})
    public void test4k60Encoder() throws IOException {
        Set<String> encoderSet = get4k60HwCodecSet(true);

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        Requirements.HardwareEncoder4K60Requirement r4k60HwEncoder =
                Requirements.addR5_1__H_1_16().to(pce);
        r4k60HwEncoder.setNumber4KHwEncoders(encoderSet.size());

        pce.submitAndCheck();
    }

    /**
     * MUST have at least 1 hardware image decoder supporting AVIF Baseline Profile.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirements = {"5.1/H-1-17"})
    public void testAVIFHwDecoderRequirements() throws Exception {
        int[] profiles = {AV1ProfileMain8, AV1ProfileMain10};
        ArrayList<MediaFormat> formats = new ArrayList<>();
        for (int profile : profiles) {
            MediaFormat format = MediaFormat.createVideoFormat(MIMETYPE_VIDEO_AV1, 3840, 2160);
            format.setInteger(MediaFormat.KEY_PROFILE, profile);
            format.setInteger(MediaFormat.KEY_LEVEL, AV1Level51);
            formats.add(format);
        }
        ArrayList<String> av1HwDecoders =
                selectHardwareCodecs(MIMETYPE_VIDEO_AV1, formats, null, false);
        boolean isDecoded = false;
        if (av1HwDecoders.size() != 0) {
            isDecoded = decodeAVIF(new File(WorkDir.getMediaDirString() + INPUT_FILE));
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        Requirements.AVIFBaselineProfileRequirement rAVIFDecoderReq =
                Requirements.addR5_1__H_1_17().to(pce);
        rAVIFDecoderReq.setAvifImageDecoderBoolean(isDecoded);

        pce.submitAndCheck();
    }

    /**
     * MUST support AV1 encoder which can encode up to 480p resolution
     * at 30fps and 1Mbps.
     */
    @SmallTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"2.2.7.1/5.1/H-1-18"})
    public void testAV1EncoderRequirements() throws Exception {
        int width = 720;
        int height = 480;
        String mediaType = MIMETYPE_VIDEO_AV1;
        int requiredFps = 30;
        MediaFormat format = MediaFormat.createVideoFormat(mediaType, width, height);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        ArrayList<String> av1Encoders = selectCodecs(mediaType, formats, null, true);
        boolean found = false;
        double fps = 0;
        for (String codecName : av1Encoders) {
            MediaCodecInfo info = getCodecInfo(codecName);
            MediaCodecInfo.VideoCapabilities videoCaps =
                            info.getCapabilitiesForType(mediaType).getVideoCapabilities();
            List<PerformancePoint> pps = videoCaps.getSupportedPerformancePoints();
            if (pps != null && pps.size() > 0) {
                PerformancePoint PPRes = new PerformancePoint(width, height, requiredFps);
                for (PerformancePoint pp : pps) {
                    if (pp.covers(PPRes)) {
                        fps = max(fps, pp.getMaxFrameRate());
                        found = true;
                    }
                }
                // found encoder advertising required performance point
                if (found) {
                    break;
                }
            }
            // For non-HW accelerated (SW) encoders we have to rely on their published
            // achievable rates as they do not advertise performance points.
            // The test relies on getLower() as that is the best approximation for what
            // can be achieved.
            Range<Double> reported = videoCaps.getAchievableFrameRatesFor(width, height);
            if (reported != null && reported.getLower() >= requiredFps) {
                fps = reported.getLower();
                found = true;
            }
            if (found) {
                break;
            }
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        Requirements.AV1EncoderRequirement rAV1EncoderReq = Requirements.addR5_1__H_1_18().to(pce);
        rAV1EncoderReq.setAv1EncoderFps(fps);
        pce.submitAndCheck();
    }

    /**
     * MUST support the Feature_HlgEditing feature for default hardware AV1 and HEVC
     * encoders present on the device at 4K resolution or the largest Camera-supported
     * resolution, whichever is less.
     */
    @SmallTest
    @RequiresFlagsEnabled(Flags.FLAG_HLG_EDITING)
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"5.1/H-1-20"})
    public void testHlgEditingSupport() throws CameraAccessException, IOException {
        final String[] mediaTypes =
                {MediaFormat.MIMETYPE_VIDEO_HEVC, MIMETYPE_VIDEO_AV1};

        boolean isFeatureSupported = true;
        Size size4k = new Size(3840, 2160);
        int frameSize4k = size4k.getWidth() * size4k.getHeight();
        Size maxRecordingSize = getMaxSupportedRecordingSize();
        if (maxRecordingSize == null) {
            maxRecordingSize = size4k;
        } else {
            int frameSize = maxRecordingSize.getWidth() * maxRecordingSize.getHeight();
            maxRecordingSize = frameSize < frameSize4k ? maxRecordingSize : size4k;
        }

        outerloop:
        for (String mediaType : mediaTypes) {
            ArrayList<String> hwEncoders = selectHardwareCodecs(mediaType, null, null, true);
            for (String encoder : hwEncoders) {
                if (!isDefaultCodec(encoder, mediaType, true)) {
                    continue;
                }
                MediaFormat format =
                        MediaFormat.createVideoFormat(mediaType, maxRecordingSize.getWidth(),
                                maxRecordingSize.getHeight());
                format.setFeatureEnabled(FEATURE_HlgEditing, true);
                if (!MediaUtils.supports(encoder, format)) {
                    isFeatureSupported = false;
                    break outerloop;
                }
            }
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        Requirements.VideoCodecHlgEditingRequirement hlgEditingSupportReq =
                Requirements.addR5_1__H_1_20().to(pce);
        hlgEditingSupportReq.setHlgEditing(isFeatureSupported);

        pce.submitAndCheck();
    }

    /**
     * [5.1/H-1-21] MUST support FEATURE_DynamicColorAspects for all hardware video decoders
     *  (AVC, HEVC, VP9, AV1 or later)
     */
    @SmallTest
    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_COLOR_ASPECTS)
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"5.1/H-1-21"})
    public void testDynamicColorAspectFeature() {
        final String[] mediaTypes =
                {MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
                 MediaFormat.MIMETYPE_VIDEO_VP9, MediaFormat.MIMETYPE_VIDEO_AV1};

        boolean isSupported = true;
        for (String mediaType : mediaTypes) {
            isSupported = selectHardwareCodecs(mediaType, null, null, false).stream()
                    .allMatch(decoder -> {
                        CodecCapabilities caps =
                                getCodecInfo(decoder).getCapabilitiesForType(mediaType);
                        return caps != null && caps.isFeatureSupported(FEATURE_DynamicColorAspects);
                    });
            if (!isSupported) {
                break;
            }
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        Requirements.VideoCodecDynamicColorAspectRequirement dynamicColorAspectsReq =
                Requirements.addR5_1__H_1_21().to(pce);
        dynamicColorAspectsReq.setDynamicColorAspects(isSupported);

        pce.submitAndCheck();
    }

    /**
     * MUST support portrait resolution for all hardware codecs that support landscape. AV1 codecs
     * are limited to only 1080p resolution while others should support 4k or camera preferred
     * resolution (whichever is less)
     */
    @SmallTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"5.1/H-1-22"})
    public void testPortraitResolutionSupport() throws CameraAccessException {
        final String[] mediaTypes =
                {MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
                 MediaFormat.MIMETYPE_VIDEO_AV1, MediaFormat.MIMETYPE_VIDEO_VP9};

        boolean isSupported = true;
        Size requiredSize, maxRequiredSize, maxRecordingSize;

        outerloop:
        for (String mediaType : mediaTypes) {
            maxRequiredSize = mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AV1)
                                    ? new Size(1920, 1080) : new Size(3840, 2160);
            maxRecordingSize = getMaxSupportedRecordingSize();
            if (maxRecordingSize == null) {
                requiredSize = maxRequiredSize;
            } else {
                int maxRequiredFrameSize = maxRequiredSize.getWidth() * maxRequiredSize.getHeight();
                int maxRecFrameSize = maxRecordingSize.getWidth() * maxRecordingSize.getHeight();
                requiredSize = maxRequiredFrameSize < maxRecFrameSize
                                    ? maxRequiredSize : maxRecordingSize;
            }
            for (boolean isEncoder : new boolean[] {true, false}) {
                Size finalRequiredSize = requiredSize;
                Size rotatedSize = new Size(requiredSize.getHeight(), requiredSize.getWidth());
                isSupported = selectHardwareCodecs(mediaType, null, null, isEncoder).stream()
                        .filter(codec -> MediaUtils.supports(codec, mediaType, finalRequiredSize))
                        .allMatch(codec -> MediaUtils.supports(codec, mediaType, rotatedSize));
                if (!isSupported) {
                    break outerloop;
                }
            }
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        Requirements.VideoCodecPortraitResolutionRequirement portraitResolutionSupportReq =
                Requirements.addR5_1__H_1_22().to(pce);
        portraitResolutionSupportReq.setPortraitResolution(isSupported);

        pce.submitAndCheck();
    }

    /**
     * MUST support RGBA_1010102 color format for all hardware AV1 and HEVC encoders present on
     * the device.
     */
    @SmallTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS)
    @CddTest(requirements = {"5.12/H-1-2"})
    public void testColorFormatSupport() throws IOException {
        final String[] mediaTypes =
                {MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AV1};

        boolean isSupported = true;
        outerloop:
        for (String mediaType : mediaTypes) {
            ArrayList<String> hwEncoders = selectHardwareCodecs(mediaType, null, null, true);
            for (String encoder : hwEncoders) {
                CodecCapabilities caps = getCodecInfo(encoder).getCapabilitiesForType(mediaType);
                if (IntStream.of(caps.colorFormats)
                        .noneMatch(x -> x == COLOR_Format32bitABGR2101010)) {
                    isSupported = false;
                    break outerloop;
                }
            }
        }

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        Requirements.RGBA1010102ColorFormatRequirement colorFormatSupportReq =
                Requirements.addR5_12__H_1_2().to(pce);
        colorFormatSupportReq.setRgba1010102ColorFormat(isSupported);

        pce.submitAndCheck();
    }
}
