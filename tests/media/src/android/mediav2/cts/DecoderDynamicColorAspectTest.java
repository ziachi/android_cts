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

import static android.media.codec.Flags.FLAG_DYNAMIC_COLOR_ASPECTS;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.OutputManager;
import android.os.Build;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Range;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * Test video decoders support for feature FEATURE_DynamicColorAspects.
 * <p>
 * The test decodes multiple clips that are configured with different color space attributes
 * (color-primaries, color-standard and color-transfer), serially. The test seeks for output
 * format at every dequeueOutput() call. The test expects the primaries, standard, transfer
 * characteristics received shall match the clips attributes.
 * <p>
 * Certain media types cannot hold color aspects information in the bitstream (vp8/vp9/...). For
 * these media types this information shall be configured via setParameters(Bundle). The test
 * expects the component to relay this information correctly in the output format of each buffer
 * to the client.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@RequiresFlagsEnabled(FLAG_DYNAMIC_COLOR_ASPECTS)
@RunWith(Parameterized.class)
@AppModeFull(reason = "Instant apps cannot access the SD card")
public class DecoderDynamicColorAspectTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = DecoderDynamicColorAspectTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();

    private static class MediaAndColorSpaceAttrib {
        public final String mTestFile;
        public final int mColorRange;
        public final int mColorStandard;
        public final int mColorTransfer;

        MediaAndColorSpaceAttrib(String testFile, int colorRange, int colorStandard,
                int colorTransfer) {
            mTestFile = testFile;
            mColorRange = colorRange;
            mColorStandard = colorStandard;
            mColorTransfer = colorTransfer;
        }
    }

    private final MediaAndColorSpaceAttrib mIncorrectColorSpaceAttrib =
            new MediaAndColorSpaceAttrib(null, MediaFormat.COLOR_RANGE_FULL,
                    MediaFormat.COLOR_STANDARD_BT601_NTSC, MediaFormat.COLOR_TRANSFER_LINEAR);
    private final ArrayList<MediaAndColorSpaceAttrib> mMediaAndColorSpaceAttribList;
    private final HashMap<Range<Integer>, MediaAndColorSpaceAttrib> mRangeMediaColorSpaceMap =
            new HashMap<>();
    private long mMaxPts = 0;

    public DecoderDynamicColorAspectTest(String decoder, String mediaType,
            ArrayList<MediaAndColorSpaceAttrib> mediaAndColorSpaceAttrib, String allTestParams) {
        super(decoder, mediaType, null, allTestParams);
        mMediaAndColorSpaceAttribList = mediaAndColorSpaceAttrib;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AVC, new ArrayList<>(Arrays.asList(
                        new MediaAndColorSpaceAttrib(
                                "bbb_qcif_color_bt709_lr_sdr_avc.mp4",
                                MediaFormat.COLOR_RANGE_LIMITED,
                                MediaFormat.COLOR_STANDARD_BT709,
                                MediaFormat.COLOR_TRANSFER_SDR_VIDEO),
                        new MediaAndColorSpaceAttrib(
                                "bbb_qcif_color_bt601_625_fr_gamma22_avc.mp4",
                                MediaFormat.COLOR_RANGE_FULL,
                                MediaFormat.COLOR_STANDARD_BT601_PAL,
                                /* MediaFormat.COLOR_TRANSFER_GAMMA2_2 */ 4)))},
                {MediaFormat.MIMETYPE_VIDEO_AVC, new ArrayList<>(Arrays.asList(
                        new MediaAndColorSpaceAttrib(
                                "bikes_qcif_color_bt2020_smpte2084_bt2020Ncl_lr_avc.mp4",
                                MediaFormat.COLOR_RANGE_LIMITED,
                                MediaFormat.COLOR_STANDARD_BT2020,
                                MediaFormat.COLOR_TRANSFER_ST2084),
                        new MediaAndColorSpaceAttrib(
                                "bikes_qcif_color_bt2020_smpte2086Hlg_bt2020Ncl_fr_avc.mp4",
                                MediaFormat.COLOR_RANGE_FULL,
                                MediaFormat.COLOR_STANDARD_BT2020,
                                MediaFormat.COLOR_TRANSFER_HLG)))},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, new ArrayList<>(Arrays.asList(
                        new MediaAndColorSpaceAttrib(
                                "bbb_qcif_color_bt709_lr_sdr_hevc.mp4",
                                MediaFormat.COLOR_RANGE_LIMITED,
                                MediaFormat.COLOR_STANDARD_BT709,
                                MediaFormat.COLOR_TRANSFER_SDR_VIDEO),
                        new MediaAndColorSpaceAttrib(
                                "bbb_qcif_color_bt601_625_fr_gamma22_hevc.mp4",
                                MediaFormat.COLOR_RANGE_FULL,
                                MediaFormat.COLOR_STANDARD_BT601_PAL,
                                /* MediaFormat.COLOR_TRANSFER_GAMMA2_2 */ 4)))},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, new ArrayList<>(Arrays.asList(
                        new MediaAndColorSpaceAttrib(
                                "bikes_qcif_color_bt2020_smpte2084_bt2020Ncl_lr_hevc.mp4",
                                MediaFormat.COLOR_RANGE_LIMITED,
                                MediaFormat.COLOR_STANDARD_BT2020,
                                MediaFormat.COLOR_TRANSFER_ST2084),
                        new MediaAndColorSpaceAttrib(
                                "bikes_qcif_color_bt2020_smpte2086Hlg_bt2020Ncl_fr_hevc.mp4",
                                MediaFormat.COLOR_RANGE_FULL,
                                MediaFormat.COLOR_STANDARD_BT2020,
                                MediaFormat.COLOR_TRANSFER_HLG)))},
                {MediaFormat.MIMETYPE_VIDEO_AV1, new ArrayList<>(Arrays.asList(
                        new MediaAndColorSpaceAttrib(
                                "bbb_qcif_color_bt709_lr_sdr_av1.mp4",
                                MediaFormat.COLOR_RANGE_LIMITED,
                                MediaFormat.COLOR_STANDARD_BT709,
                                MediaFormat.COLOR_TRANSFER_SDR_VIDEO),
                        new MediaAndColorSpaceAttrib(
                                "bbb_qcif_color_bt601_625_fr_gamma22_av1.mp4",
                                MediaFormat.COLOR_RANGE_FULL,
                                MediaFormat.COLOR_STANDARD_BT601_PAL,
                                /* MediaFormat.COLOR_TRANSFER_GAMMA2_2 */ 4)))},
                {MediaFormat.MIMETYPE_VIDEO_AV1, new ArrayList<>(Arrays.asList(
                        new MediaAndColorSpaceAttrib(
                                "bikes_qcif_color_bt2020_smpte2084_bt2020Ncl_lr_av1.mp4",
                                MediaFormat.COLOR_RANGE_LIMITED,
                                MediaFormat.COLOR_STANDARD_BT2020,
                                MediaFormat.COLOR_TRANSFER_ST2084),
                        new MediaAndColorSpaceAttrib(
                                "bikes_qcif_color_bt2020_smpte2086Hlg_bt2020Ncl_fr_av1.mp4",
                                MediaFormat.COLOR_RANGE_FULL,
                                MediaFormat.COLOR_STANDARD_BT2020,
                                MediaFormat.COLOR_TRANSFER_HLG)))},
                {MediaFormat.MIMETYPE_VIDEO_VP8, new ArrayList<>(Arrays.asList(
                        new MediaAndColorSpaceAttrib(
                                "bbb_qcif_color_bt709_lr_sdr_vp8.webm",
                                MediaFormat.COLOR_RANGE_LIMITED,
                                MediaFormat.COLOR_STANDARD_BT709,
                                MediaFormat.COLOR_TRANSFER_SDR_VIDEO),
                        new MediaAndColorSpaceAttrib(
                                "bbb_qcif_color_bt601_625_fr_gamma22_vp8.mkv",
                                MediaFormat.COLOR_RANGE_FULL,
                                MediaFormat.COLOR_STANDARD_BT601_PAL,
                                /* MediaFormat.COLOR_TRANSFER_GAMMA2_2 */ 4)))},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new ArrayList<>(Arrays.asList(
                        new MediaAndColorSpaceAttrib(
                                "bbb_qcif_color_bt709_lr_sdr_vp9.webm",
                                MediaFormat.COLOR_RANGE_LIMITED,
                                MediaFormat.COLOR_STANDARD_BT709,
                                MediaFormat.COLOR_TRANSFER_SDR_VIDEO),
                        new MediaAndColorSpaceAttrib(
                                "bbb_qcif_color_bt601_625_fr_gamma22_vp9.mkv",
                                MediaFormat.COLOR_RANGE_FULL,
                                MediaFormat.COLOR_STANDARD_BT601_PAL,
                                /* MediaFormat.COLOR_TRANSFER_GAMMA2_2 */ 4)))},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new ArrayList<>(Arrays.asList(
                        new MediaAndColorSpaceAttrib(
                                "bikes_qcif_color_bt2020_smpte2084_bt2020Ncl_lr_vp9.mkv",
                                MediaFormat.COLOR_RANGE_LIMITED,
                                MediaFormat.COLOR_STANDARD_BT2020,
                                MediaFormat.COLOR_TRANSFER_ST2084),
                        new MediaAndColorSpaceAttrib(
                                "bikes_qcif_color_bt2020_smpte2086Hlg_bt2020Ncl_fr_vp9.mkv",
                                MediaFormat.COLOR_RANGE_FULL,
                                MediaFormat.COLOR_STANDARD_BT2020,
                                MediaFormat.COLOR_TRANSFER_HLG)))},
        });
        return CodecTestBase.prepareParamList(exhaustiveArgsList, false, false, true, false);
    }

    @Before
    public void setUp() throws IOException {
        Assume.assumeTrue("codec: " + mCodecName + " does not support"
                + " FEATURE_DynamicColorAspects", isFeatureSupported(mCodecName, mMediaType,
                MediaCodecInfo.CodecCapabilities.FEATURE_DynamicColorAspects));
        ArrayList<MediaFormat> formats = new ArrayList<>();
        for (MediaAndColorSpaceAttrib mediaAndColorSpaceAttrib : mMediaAndColorSpaceAttribList) {
            formats.add(setUpSource(MEDIA_DIR + mediaAndColorSpaceAttrib.mTestFile));
            mExtractor.release();
        }
        checkFormatSupport(mCodecName, mMediaType, false, formats, null, CODEC_OPTIONAL);
    }

    private MediaFormat createInputList(MediaFormat format, ByteBuffer buffer,
            ArrayList<MediaCodec.BufferInfo> list, int offset, long ptsOffset,
            ArrayList<Long> inpPtsList) {
        if (hasCSD(format)) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.offset = offset;
            bufferInfo.size = 0;
            // For some devices with VNDK versions till Android U, sending a zero
            // timestamp for CSD results in out of order timestamps at the output.
            // For devices with VNDK versions > Android U, codecs are expected to
            // handle CSD buffers with timestamp set to zero.
            bufferInfo.presentationTimeUs = VNDK_IS_AT_MOST_U ? ptsOffset : 0;
            bufferInfo.flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
            for (int i = 0; ; i++) {
                String csdKey = "csd-" + i;
                if (format.containsKey(csdKey)) {
                    ByteBuffer csdBuffer = format.getByteBuffer(csdKey);
                    bufferInfo.size += csdBuffer.limit();
                    buffer.put(csdBuffer);
                    format.removeKey(csdKey);
                } else break;
            }
            list.add(bufferInfo);
            offset += bufferInfo.size;
        }
        while (true) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.size = mExtractor.readSampleData(buffer, offset);
            if (bufferInfo.size < 0) break;
            bufferInfo.offset = offset;
            bufferInfo.presentationTimeUs = ptsOffset + mExtractor.getSampleTime();
            mMaxPts = Math.max(mMaxPts, bufferInfo.presentationTimeUs);
            int flags = mExtractor.getSampleFlags();
            bufferInfo.flags = 0;
            if ((flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                bufferInfo.flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            if (((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0)
                    && ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_PARTIAL_FRAME) == 0)) {
                if (!inpPtsList.contains(bufferInfo.presentationTimeUs)) {
                    inpPtsList.add(bufferInfo.presentationTimeUs);
                }
            }
            list.add(bufferInfo);
            mExtractor.advance();
            offset += bufferInfo.size;
        }
        buffer.clear();
        buffer.position(offset);
        return format;
    }

    @Override
    protected void enqueueInput(int bufferIndex, ByteBuffer buffer, MediaCodec.BufferInfo info) {
        for (Range range : mRangeMediaColorSpaceMap.keySet()) {
            if (mInputCount == (int) range.getLower()) {
                final Bundle colorAspectUpdate = new Bundle();
                MediaAndColorSpaceAttrib mediaAndColorSpaceAttrib;
                if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_VP9)
                        || mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_VP8)) {
                    mediaAndColorSpaceAttrib = mRangeMediaColorSpaceMap.get(range);
                } else {
                    mediaAndColorSpaceAttrib = mIncorrectColorSpaceAttrib;
                }
                colorAspectUpdate.putInt(MediaFormat.KEY_COLOR_RANGE,
                        mediaAndColorSpaceAttrib.mColorRange);
                colorAspectUpdate.putInt(MediaFormat.KEY_COLOR_STANDARD,
                        mediaAndColorSpaceAttrib.mColorStandard);
                colorAspectUpdate.putInt(MediaFormat.KEY_COLOR_TRANSFER,
                        mediaAndColorSpaceAttrib.mColorTransfer);
                mCodec.setParameters(colorAspectUpdate);
                break;
            }
        }
        super.enqueueInput(bufferIndex, buffer, info);
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            boolean validateAttrib = false;
            for (Range<Integer> range : mRangeMediaColorSpaceMap.keySet()) {
                if (range.contains(mOutputCount)) {
                    MediaAndColorSpaceAttrib mediaAndColorSpaceAttrib =
                            mRangeMediaColorSpaceMap.get(range);
                    validateColorAspects(mCodec.getOutputFormat(bufferIndex),
                            mediaAndColorSpaceAttrib.mColorRange,
                            mediaAndColorSpaceAttrib.mColorStandard,
                            mediaAndColorSpaceAttrib.mColorTransfer);
                    validateAttrib = true;
                    break;
                }
            }
            assertTrue("unable to validate color space attributes for output frame id : "
                    + mOutputCount + " \n" + mTestConfig + mTestEnv, validateAttrib);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, mSurface != null);
    }

    /**
     * Check description of class {@link DecoderDynamicColorAspectTest}
     */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            codeName = "VanillaIceCream")
    @ApiTest(apis = {"android.media.MediaFormat#KEY_COLOR_RANGE",
            "android.media.MediaFormat#KEY_COLOR_STANDARD",
            "android.media.MediaFormat#KEY_COLOR_TRANSFER",
            "android.media.MediaCodecInfo.CodecCapabilities#FEATURE_DynamicColorAspects"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testDynamicColorAspect() throws IOException, InterruptedException {
        int totalSize = 0;
        for (MediaAndColorSpaceAttrib mediaAndColorSpaceAttrib : mMediaAndColorSpaceAttribList) {
            File file = new File(MEDIA_DIR + mediaAndColorSpaceAttrib.mTestFile);
            totalSize += (int) file.length();
        }
        long ptsOffset = 0;
        int buffOffset = 0;
        ArrayList<MediaFormat> formats = new ArrayList<>();
        ArrayList<MediaCodec.BufferInfo> list = new ArrayList<>();
        ArrayList<Long> inpPtsList = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        for (MediaAndColorSpaceAttrib mediaAndColorSpaceAttrib : mMediaAndColorSpaceAttribList) {
            int lower = inpPtsList.size();
            formats.add(createInputList(setUpSource(MEDIA_DIR + mediaAndColorSpaceAttrib.mTestFile),
                    buffer, list, buffOffset, ptsOffset, inpPtsList));
            mExtractor.release();
            ptsOffset = mMaxPts + 1000000L;
            buffOffset = (list.get(list.size() - 1).offset) + (list.get(list.size() - 1).size);
            mRangeMediaColorSpaceMap.put(new Range<>(lower, inpPtsList.size() - 1),
                    mediaAndColorSpaceAttrib);
        }
        mSaveToMem = false;
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(mCodecName);
        configureCodec(formats.get(0), false, true, false);
        mCodec.start();
        doWork(buffer, list);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
    }
}
