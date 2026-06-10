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

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ;
import static android.mediav2.common.cts.CodecTestBase.MEDIA_CODEC_LIST_ALL;
import static android.mediav2.common.cts.CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS;
import static android.mediav2.common.cts.CodecTestBase.codecFilter;
import static android.mediav2.common.cts.CodecTestBase.codecPrefix;
import static android.mediav2.common.cts.CodecTestBase.getMaxSupportedInstances;
import static android.mediav2.common.cts.CodecTestBase.isFeatureRequired;
import static android.mediav2.common.cts.CodecTestBase.isFeatureSupported;
import static android.mediav2.common.cts.CodecTestBase.isFormatSupported;
import static android.mediav2.common.cts.CodecTestBase.isHardwareAcceleratedCodec;
import static android.mediav2.common.cts.CodecTestBase.isSoftwareCodec;
import static android.mediav2.common.cts.DecodeStreamToYuv.getFormatInStream;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.cts.TestUtils;
import android.util.Range;
import android.util.Size;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * This class validates the NDK api for media codec capabilities. The scope of this test is to only
 * check if the information advertised is ok. If the component is actually capable of supporting the
 * advertised information is beyond the scope of the test.
 */
@SdkSuppress(minSdkVersion = 36)
@SmallTest
@RunWith(Parameterized.class)
public class NativeAMediaCodecInfoTest {
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    // in sync with AMediaCodecType values
    private static final int SOFTWARE_ONLY = 1;
    private static final int HARDWARE_ACCELERATED = 2;
    private static final int SOFTWARE_WITH_DEVICE_ACCESS = 3;
    // alias to MediaCodecInfo.CodecCapabilities.FEATURE_SpecialCodec
    static final String SPECIAL_CODEC = "special-codec";

    private static final int MEDIACODEC_KIND_INVALID = 0;
    private static final int MEDIACODEC_KIND_DECODER = 1;
    private static final int MEDIACODEC_KIND_ENCODER = 2;

    private final String mCodecName;
    private final String mMediaType;
    private final StringBuilder mTestResults = new StringBuilder();

    private static final Size SUBQCIF = new Size(128, 96);
    private static final Size QCIF = new Size(176, 144);
    private static final Size SD144P = new Size(256, 144);
    private static final Size CIFNTSC = new Size(352, 240);
    private static final Size CIF = new Size(352, 288);
    private static final Size QVGA = new Size(320, 240);
    private static final Size SD240P = new Size(426, 240);
    private static final Size SD360P = new Size(640, 360);
    private static final Size VGA = new Size(640, 480);
    private static final Size SDNTSC = new Size(720, 480);
    private static final Size SDPAL = new Size(720, 576);
    private static final Size WVGA = new Size(800, 480);
    private static final Size SD480P = new Size(854, 480);
    private static final Size HD = new Size(1280, 720);
    private static final Size HDPAL = new Size(1440, 1080);
    private static final Size FULLHD = new Size(1920, 1080);
    private static final Size FULLHD_ALT = new Size(1920, 1088);
    private static final Size UHD1440P = new Size(2560, 1440);
    private static final Size UHD = new Size(3840, 2160);
    private static final Size DC4K = new Size(4096, 2160);
    private static final Size UHD8K = new Size(7680, 4320);
    private final double[] STANDARD_FPS =
            {12.0, 15.0, 23.976, 24.0, 25.0, 29.97, 30.0, 48.0, 50.0, 59.94, 60.0};
    private final Size[] STANDARD_RES =
            {SUBQCIF, QCIF, SD144P, CIFNTSC, CIF, QVGA, SD240P, SD360P, VGA, SDNTSC, SDPAL, WVGA,
                    SD480P, HD, HDPAL, FULLHD, FULLHD_ALT, UHD1440P, UHD, DC4K, UHD8K};
    private final Range<Integer> UNSUPPORTED_SIZE = Range.create(-1, -1);
    private final Range<Double> UNSUPPORTED_RATE = Range.create(-1.0, -1.0);
    private final Range<Double> UNKNOWN_RATE = Range.create(-2.0, -2.0);

    static {
        System.loadLibrary("ctsmediav2codecinfo_jni");
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final List<Object[]> argsList = new ArrayList<>();
        for (MediaCodecInfo codecInfo : MEDIA_CODEC_LIST_ALL.getCodecInfos()) {
            if (codecInfo.isAlias()) continue;
            String codecName = codecInfo.getName();
            if (!TestUtils.isTestableCodecInCurrentMode(codecName)) {
                continue;
            }
            if (codecPrefix != null && !codecName.startsWith(codecPrefix)
                    || (codecFilter != null && !codecFilter.matcher(codecName).matches())) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            // For codecs supporting multiple media types, the ndk codec names are different from
            // the sdk names. Skip these codecs as a workaround.
            if (types.length > 1) {
                continue;
            }
            for (String type : types) {
                if (codecInfo.getCapabilitiesForType(type).isFeatureSupported(SPECIAL_CODEC)) {
                    continue;
                }
                argsList.add(new Object[]{codecName, type});
            }
        }
        return argsList;
    }

    public NativeAMediaCodecInfoTest(String codecName, String mediaType) {
        mCodecName = codecName;
        mMediaType = mediaType;
    }

    public static MediaCodecInfo getCodecInfo(String codecName) {
        for (MediaCodecInfo info : MEDIA_CODEC_LIST_ALL.getCodecInfos()) {
            if (info.getName().equals(codecName)) {
                return info;
            }
        }
        return null;
    }

    private static int getExpectedCodecType(String codecName) {
        if (isSoftwareCodec(codecName)) {
            return SOFTWARE_ONLY;
        } else if (isHardwareAcceleratedCodec(codecName)) {
            return HARDWARE_ACCELERATED;
        } else {
            return SOFTWARE_WITH_DEVICE_ACCESS;
        }
    }

    private static int getExpectedCodecKind(boolean isEncoder) {
        return isEncoder ? MEDIACODEC_KIND_ENCODER : MEDIACODEC_KIND_DECODER;
    }

    private Range<Integer> getSupportedWidthsForNoExcep(MediaCodecInfo.VideoCapabilities videoCaps,
            int height) {
        try {
            return videoCaps.getSupportedWidthsFor(height);
        } catch (IllegalArgumentException ignored) {
            return UNSUPPORTED_SIZE;
        }
    }

    private Range<Integer> getSupportedHeightsForNoExcep(MediaCodecInfo.VideoCapabilities videoCaps,
            int width) {
        try {
            return videoCaps.getSupportedHeightsFor(width);
        } catch (IllegalArgumentException ignored) {
            return UNSUPPORTED_SIZE;
        }
    }

    private Range<Double> getSupportedFrameRatesForNoExcep(
            MediaCodecInfo.VideoCapabilities videoCaps, int width, int height) {
        try {
            return videoCaps.getSupportedFrameRatesFor(width, height);
        } catch (IllegalArgumentException ignored) {
            return UNSUPPORTED_RATE;
        }
    }

    private Range<Double> getAchievableFrameRatesForNoExcep(
            MediaCodecInfo.VideoCapabilities videoCaps, int width, int height) {
        try {
            Range<Double> rates = videoCaps.getAchievableFrameRatesFor(width, height);
            return rates == null ? UNKNOWN_RATE : rates;
        } catch (IllegalArgumentException ignored) {
            return UNSUPPORTED_RATE;
        }
    }

    @ApiTest(apis = {"AMediaCodecInfo_getKind", "AMediaCodecInfo_isVendor",
            "AMediaCodecInfo_getCanonicalName", "AMediaCodecInfo_getMaxSupportedInstances",
            "AMediaCodecInfo_getMediaCodecInfoType", "AMediaCodecInfo_getMediaType",
            "AMediaCodecInfo_isFeatureSupported", "AMediaCodecInfo_isFeatureRequired",
            "AMediaCodecInfo_isFormatSupported", "AMediaCodecInfo_getAudioCapabilities",
            "AMediaCodecInfo_getVideoCapabilities", "AMediaCodecInfo_getEncoderCapabilities"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testAMediaCodecInfoNative() throws IOException {
        MediaCodecInfo codecInfo = getCodecInfo(mCodecName);
        Assert.assertNotNull("received null codecInfo for component: " + mCodecName, codecInfo);
        String[] features = codecInfo.getCapabilitiesForType(mMediaType).validFeatures();
        Assert.assertNotNull("received null features for component: " + mCodecName, features);
        Assert.assertTrue("received 0 features for component: " + mCodecName, features.length > 0);
        boolean isEncoder = codecInfo.isEncoder();
        int expectedKind = getExpectedCodecKind(isEncoder);
        int featureSupportMap = 0;
        int featureRequiredMap = 0;
        for (int i = 0; i < features.length; i++) {
            if (isFeatureSupported(mCodecName, mMediaType, features[i])) {
                featureSupportMap |= (1 << i);
            }
            if (isFeatureRequired(mCodecName, mMediaType, features[i])) {
                featureRequiredMap |= (1 << i);
            }
        }
        ArrayList<String> fileList = new ArrayList<>();
        ArrayList<MediaFormat> formatList = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get(MEDIA_DIR))) {
            paths.forEach(path -> {
                if (Files.isRegularFile(path)) {
                    try {
                        MediaFormat format = getFormatInStream(mMediaType, path.toString());
                        fileList.add(path.toString());
                        formatList.add(format);
                    } catch (IOException | IllegalArgumentException ignored) {
                    }
                }
            });
        }
        String[] files = fileList.toArray(new String[0]);
        boolean[] isFormatSupportedArray = new boolean[formatList.size()];
        for (int i = 0; i < formatList.size(); i++) {
            isFormatSupportedArray[i] =
                    isFormatSupported(mCodecName, mMediaType, formatList.get(i));
        }
        boolean isPass = nativeTestAMediaCodecInfo(mCodecName, codecInfo.isEncoder(), expectedKind,
                codecInfo.isVendor(), codecInfo.getCanonicalName(),
                getMaxSupportedInstances(mCodecName, mMediaType), getExpectedCodecType(mCodecName),
                mMediaType, features, featureSupportMap, featureRequiredMap, files,
                isFormatSupportedArray, mTestResults);
        Assert.assertTrue(mTestResults.toString(), isPass);
    }

    private native boolean nativeTestAMediaCodecInfo(String codecName, boolean isEncoder,
            int codecKind, boolean isVendor, String canonicalName, int maxSupportedInstances,
            int codecType, String mediaType, String[] features, int featureSupportMap,
            int featureRequiredMap, String[] files, boolean[] isFormatSupported,
            StringBuilder retMsg);

    @ApiTest(apis = {"AMediaCodecInfo_getVideoCapabilities",
            "ACodecVideoCapabilities_getBitrateRange",
            "ACodecVideoCapabilities_getSupportedWidths",
            "ACodecVideoCapabilities_getSupportedHeights",
            "ACodecVideoCapabilities_getSupportedFrameRates",
            "ACodecVideoCapabilities_getWidthAlignment",
            "ACodecVideoCapabilities_getHeightAlignment",
            "ACodecEncoderCapabilities_isBitrateModeSupported",
            "ACodecEncoderCapabilities_getComplexityRange",
            "ACodecEncoderCapabilities_getQualityRange"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testAMediaCodecInfoGetVideoCapabilitiesNative() {
        Assume.assumeTrue("Test is applicable for video codecs",
                mMediaType.startsWith("video/") || mMediaType.equalsIgnoreCase(
                        MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC));
        MediaCodecInfo codecInfo = getCodecInfo(mCodecName);
        Assert.assertNotNull("received null codecInfo for component: " + mCodecName, codecInfo);
        MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(mMediaType);
        MediaCodecInfo.VideoCapabilities videoCaps = caps.getVideoCapabilities();
        Assert.assertNotNull("received null for video capabilities", videoCaps);
        boolean isPass = nativeTestAMediaCodecInfoVideoCaps(mCodecName,
                videoCaps.getBitrateRange().getLower(), videoCaps.getBitrateRange().getUpper(),
                videoCaps.getSupportedWidths().getLower(),
                videoCaps.getSupportedWidths().getUpper(),
                videoCaps.getSupportedHeights().getLower(),
                videoCaps.getSupportedHeights().getUpper(),
                videoCaps.getSupportedFrameRates().getLower(),
                videoCaps.getSupportedFrameRates().getUpper(), videoCaps.getWidthAlignment(),
                videoCaps.getHeightAlignment(),
                mTestResults);
        Assert.assertTrue(mTestResults.toString(), isPass);
        MediaCodecInfo.EncoderCapabilities encCaps = caps.getEncoderCapabilities();
        if (codecInfo.isEncoder()) {
            Assert.assertNotNull("received null for encoder capabilities", encCaps);
            int bitrateModeSupportMap = 0;
            for (int i = 0; i < 4; i++) {
                if (encCaps.isBitrateModeSupported(i)) {
                    bitrateModeSupportMap |= (1 << i);
                }
            }
            isPass = nativeTestAMediaCodecInfoGetEncoderCapabilities(mCodecName,
                    encCaps.getComplexityRange().getLower(),
                    encCaps.getComplexityRange().getUpper(),
                    encCaps.getQualityRange().getLower(), encCaps.getQualityRange().getUpper(),
                    bitrateModeSupportMap,
                    mTestResults);
            Assert.assertTrue(mTestResults.toString(), isPass);
        }
    }

    private native boolean nativeTestAMediaCodecInfoVideoCaps(String codecName,
            int bitRateRangeLower, int bitRateRangeUpper, int supportedWidthLower,
            int supportedWidthUpper, int supportHeightLower, int supportedHeightUpper,
            int supportedFrameRateLower, int supportedFrameRateUpper, int widthAlignment,
            int heightAlignment, StringBuilder retMsg);

    private native boolean nativeTestAMediaCodecInfoGetEncoderCapabilities(String codecName,
            int complexityRangeLower, int complexityRangeUpper, int qualityRangeLower,
            int qualityRangeUpper, int bitrateModeSupportMap, StringBuilder retMsg);

    @ApiTest(apis = {"ACodecVideoCapabilities_getSupportedWidthsFor",
            "ACodecVideoCapabilities_getSupportedHeightsFor",
            "ACodecVideoCapabilities_getSupportedFrameRatesFor",
            "ACodecVideoCapabilities_getAchievableFrameRatesFor",
            "ACodecVideoCapabilities_areSizeAndRateSupported",
            "ACodecVideoCapabilities_isSizeSupported"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testAMediaCodecInfoGetVideoCapsGetSupportForNative() {
        Assume.assumeTrue("Test is applicable for video codecs",
                mMediaType.startsWith("video/") || mMediaType.equalsIgnoreCase(
                        MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC));
        MediaCodecInfo codecInfo = getCodecInfo(mCodecName);
        Assert.assertNotNull("received null codecInfo for component: " + mCodecName, codecInfo);
        MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(mMediaType);
        MediaCodecInfo.VideoCapabilities videoCaps = caps.getVideoCapabilities();
        Assert.assertNotNull("received null for video capabilities", videoCaps);
        for (double frameRate : STANDARD_FPS) {
            for (Size res : STANDARD_RES) {
                for (boolean isLandscape : new boolean[]{true, false}) {
                    int width = isLandscape ? res.getWidth() : res.getHeight();
                    int height = isLandscape ? res.getHeight() : res.getWidth();
                    Range<Integer> widths = getSupportedWidthsForNoExcep(videoCaps, height);
                    Range<Integer> heights = getSupportedHeightsForNoExcep(videoCaps, width);
                    Range<Double> supportedRates =
                            getSupportedFrameRatesForNoExcep(videoCaps, width, height);
                    Range<Double> achievableRates =
                            getAchievableFrameRatesForNoExcep(videoCaps, width, height);
                    boolean isPass =
                            nativeTestAMediaCodecInfoVideoCapsGetSupportFor(mCodecName, width,
                                    height, frameRate, widths.getLower(), widths.getUpper(),
                                    heights.getLower(), heights.getUpper(),
                                    supportedRates.getLower(), supportedRates.getUpper(),
                                    achievableRates.getLower(), achievableRates.getUpper(),
                                    videoCaps.isSizeSupported(width, height),
                                    videoCaps.areSizeAndRateSupported(width, height, frameRate),
                                    mTestResults);
                    Assert.assertTrue(mTestResults.toString(), isPass);
                }
            }
        }
    }

    private native boolean nativeTestAMediaCodecInfoVideoCapsGetSupportFor(String codecName,
            int testWidth, int testHeight, double frameRate, int supportedWidthLowerForHeight,
            int supportedWidthUpperForHeight, int supportedHeightLowerForWidth,
            int supportedHeightUpperForWidth, double supportedFrameRateLower,
            double supportedFrameRateUpper, double achievedFrameRateLower,
            double achievedFrameRateUpper, boolean isSizeSupported, boolean areSizeAndRateSupported,
            StringBuilder retMsg);

    @ApiTest(apis = {"ACodecPerformancePoint_create",
            "ACodecPerformancePoint_coversFormat",
            "ACodecPerformancePoint_covers",
            "ACodecPerformancePoint_equals"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testAMediaCodecInfoGetPerformancePointNative() {
        Assume.assumeTrue("Test is applicable for video codecs",
                mMediaType.startsWith("video/") || mMediaType.equalsIgnoreCase(
                        MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC));
        MediaCodecInfo codecInfo = getCodecInfo(mCodecName);
        Assert.assertNotNull("received null codecInfo for component: " + mCodecName, codecInfo);
        MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(mMediaType);
        MediaCodecInfo.VideoCapabilities videoCaps = caps.getVideoCapabilities();
        Assert.assertNotNull("received null for video capabilities", videoCaps);
        List<MediaCodecInfo.VideoCapabilities.PerformancePoint> ppList =
                videoCaps.getSupportedPerformancePoints();
        int listSize = ppList != null ? ppList.size() : 0;
        for (double frameRate : STANDARD_FPS) {
            for (Size res : STANDARD_RES) {
                int width = res.getWidth();
                int height = res.getHeight();
                int coversFormat = 0;
                int coversPoint = 0;
                int equalsPoint = 0;
                if (listSize != 0) {
                    MediaFormat format = MediaFormat.createVideoFormat(mMediaType, width, height);
                    format.setFloat(MediaFormat.KEY_FRAME_RATE, (float) frameRate);
                    MediaCodecInfo.VideoCapabilities.PerformancePoint point =
                            new MediaCodecInfo.VideoCapabilities.PerformancePoint(width, height,
                                    (int) frameRate);
                    for (int i = 0; i < listSize; i++) {
                        MediaCodecInfo.VideoCapabilities.PerformancePoint pp = ppList.get(i);
                        if (pp.covers(format)) {
                            coversFormat |= (1 << i);
                        }
                        if (pp.covers(point)) {
                            coversPoint |= (1 << i);
                        }
                        if (pp.equals(point)) {
                            equalsPoint |= (1 << i);
                        }
                    }
                }
                boolean isPass =
                        nativeTestACodecPerformancePoint(mCodecName, width, height, frameRate,
                                coversFormat, coversPoint, equalsPoint, listSize, mTestResults);
                Assert.assertTrue(mTestResults.toString(), isPass);
                if (listSize == 0) return;
            }
        }
    }

    private native boolean nativeTestACodecPerformancePoint(String codecName, int width, int height,
            double frameRate, int coversFormat, int coversPoint, int equalsPoint, int ppSize,
            StringBuilder retMsg);

    @ApiTest(apis = {"ACodecAudioCapabilities_getBitrateRange",
            "ACodecAudioCapabilities_getMaxInputChannelCount",
            "ACodecAudioCapabilities_getMinInputChannelCount",
            "ACodecAudioCapabilities_getSupportedSampleRates",
            "ACodecAudioCapabilities_getSupportedSampleRateRanges",
            "ACodecAudioCapabilities_getInputChannelCountRanges",
            "ACodecEncoderCapabilities_isBitrateModeSupported",
            "ACodecEncoderCapabilities_getComplexityRange",
            "ACodecEncoderCapabilities_getQualityRange"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testAMediaCodecInfoGetAudioCapabilitiesNative() {
        Assume.assumeTrue("Test is applicable for audio codecs", mMediaType.startsWith("audio/"));
        MediaCodecInfo codecInfo = getCodecInfo(mCodecName);
        Assert.assertNotNull("received null codecInfo for component: " + mCodecName, codecInfo);
        MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(mMediaType);
        MediaCodecInfo.AudioCapabilities audioCaps = caps.getAudioCapabilities();
        Assert.assertNotNull("received null for audio capabilities", audioCaps);
        int[] sampleRates = audioCaps.getSupportedSampleRates();
        int[] sampleRateRanges = new int[audioCaps.getSupportedSampleRateRanges().length * 2];
        int i = 0;
        for (Range<Integer> range : audioCaps.getSupportedSampleRateRanges()) {
            sampleRateRanges[i++] = range.getLower();
            sampleRateRanges[i++] = range.getUpper();
        }
        Range<Integer>[] channelRanges = audioCaps.getInputChannelCountRanges();
        int[] inputChannelRanges = new int[channelRanges.length * 2];
        int j = 0;
        for (Range<Integer> range : channelRanges) {
            inputChannelRanges[j++] = range.getLower();
            inputChannelRanges[j++] = range.getUpper();
        }
        int[] standardSampleRates =
                new int[]{8000, 11025, 12000, 16000, 22050, 24000, 44100, 48000, 88200, 96000,
                        192000};
        int standardSampleRatesSupportMap = 0;
        for (i = 0; i < standardSampleRates.length; i++) {
            if (audioCaps.isSampleRateSupported(standardSampleRates[i])) {
                standardSampleRatesSupportMap |= (1 << i);
            }
        }
        boolean isPass = nativeTestAMediaCodecInfoGetAudioCapabilities(mCodecName,
                audioCaps.getBitrateRange().getLower(), audioCaps.getBitrateRange().getUpper(),
                audioCaps.getMaxInputChannelCount(), audioCaps.getMinInputChannelCount(),
                sampleRates, sampleRateRanges, inputChannelRanges, standardSampleRates,
                standardSampleRatesSupportMap, mTestResults);
        Assert.assertTrue(mTestResults.toString(), isPass);

        MediaCodecInfo.EncoderCapabilities encCaps = caps.getEncoderCapabilities();
        if (codecInfo.isEncoder()) {
            Assert.assertNotNull("received null for encoder capabilities", encCaps);
            int bitrateModeSupportMap = 0;
            for (i = BITRATE_MODE_CQ; i <= BITRATE_MODE_CBR_FD; i++) {
                if (encCaps.isBitrateModeSupported(i)) {
                    bitrateModeSupportMap |= (1 << i);
                }
            }
            isPass = nativeTestAMediaCodecInfoGetEncoderCapabilities(mCodecName,
                    encCaps.getComplexityRange().getLower(),
                    encCaps.getComplexityRange().getUpper(),
                    encCaps.getQualityRange().getLower(), encCaps.getQualityRange().getUpper(),
                    bitrateModeSupportMap,
                    mTestResults);
            Assert.assertTrue(mTestResults.toString(), isPass);
        }
    }

    private native boolean nativeTestAMediaCodecInfoGetAudioCapabilities(String codecName,
            int mLower, int mUpper, int maxInputChannelCount, int minInputChannelCount,
            int[] sampleRates, int[] sampleRateRanges, int[] inputChannelRanges,
            int[] standardSampleRates, int standardSampleRatesSupportMap, StringBuilder retMsg);
}
