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

package android.media.decoder.cts;

import static android.media.MediaCodecInfo.CodecProfileLevel.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.TestUtils;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.Surface;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.Preconditions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@MediaHeavyPresubmitTest
@AppModeFull(reason = "There should be no instant apps specific behavior related to decoders")
@RunWith(Parameterized.class)
public class HdrToSdrDecoderTest extends HDRDecoderTestBase{
    private static final String TAG = "HdrToSdrDecoderTest";

    private static boolean DEBUG_HDR_TO_SDR_PLAY_VIDEO = false;
    private static final String INVALID_HDR_STATIC_INFO =
            "00 00 00 00 00 00 00 00  00 00 00 00 00 00 00 00" +
            "00 00 00 00 00 00 00 00  00                     " ;

    public static final Map<String, Map<Integer, Long>> PROFILE_HDR_MAP = new HashMap<>();

    static {
        Map<Integer, Long> AV1_PROFILE_MAP = new HashMap<>();
        AV1_PROFILE_MAP.put(AV1ProfileMain10, DynamicRangeProfiles.HLG10);
        AV1_PROFILE_MAP.put(AV1ProfileMain10HDR10, DynamicRangeProfiles.HDR10);
        AV1_PROFILE_MAP.put(AV1ProfileMain10HDR10Plus, DynamicRangeProfiles.HDR10_PLUS);

        Map<Integer, Long> HEVC_PROFILE_MAP = new HashMap<>();
        HEVC_PROFILE_MAP.put(HEVCProfileMain10, DynamicRangeProfiles.HLG10);
        HEVC_PROFILE_MAP.put(HEVCProfileMain10HDR10, DynamicRangeProfiles.HDR10);
        HEVC_PROFILE_MAP.put(HEVCProfileMain10HDR10Plus, DynamicRangeProfiles.HDR10_PLUS);

        Map<Integer, Long> VP9_PROFILE_MAP = new HashMap<>();
        VP9_PROFILE_MAP.put(VP9Profile2, DynamicRangeProfiles.HLG10);
        VP9_PROFILE_MAP.put(VP9Profile2HDR, DynamicRangeProfiles.HDR10);
        VP9_PROFILE_MAP.put(VP9Profile2HDR10Plus, DynamicRangeProfiles.HDR10_PLUS);

        PROFILE_HDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_AV1, AV1_PROFILE_MAP);
        PROFILE_HDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_HEVC, HEVC_PROFILE_MAP);
        PROFILE_HDR_MAP.put(MediaFormat.MIMETYPE_VIDEO_VP9, VP9_PROFILE_MAP);
    }

    @Parameterized.Parameter(0)
    public String mCodecName;

    @Parameterized.Parameter(1)
    public String mMediaType;

    @Parameterized.Parameter(2)
    public String mInputFile;

    @Parameterized.Parameter(3)
    public String mHdrStaticInfo;

    @Parameterized.Parameter(4)
    public String[] mHdrDynamicInfo;

    @Parameterized.Parameter(5)
    public boolean mMetaDataInContainer;

    @Parameterized.Parameter(6)
    public int mProfile;

    private static List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
        final List<Object[]> argsList = new ArrayList<>();
        int argLength = exhaustiveArgsList.get(0).length;
        for (Object[] arg : exhaustiveArgsList) {
            String mediaType = (String) arg[0];
            String[] decoderNames = MediaUtils.getDecoderNamesForMime(mediaType);

            for (String decoder : decoderNames) {
                if (TestUtils.isMainlineCodec(decoder)) {
                    if (!TestUtils.isTestingModules()) {
                        Log.i(TAG, "not testing modules, skip module codec " + decoder);
                        continue;
                    }
                } else {
                    if (TestUtils.isTestingModules()) {
                        Log.i(TAG, "testing modules, skip non-module codec " + decoder);
                        continue;
                    }
                }
                Object[] testArgs = new Object[argLength + 1];
                testArgs[0] = decoder;
                System.arraycopy(arg, 0, testArgs, 1, argLength);
                argsList.add(testArgs);
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AV1, AV1_HDR_RES, AV1_HDR_STATIC_INFO, null, false,
                        AV1ProfileMain10HDR10},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, H265_HDR10_RES, H265_HDR10_STATIC_INFO, null,
                        false, HEVCProfileMain10HDR10},
                {MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HDR_RES, VP9_HDR_STATIC_INFO, null, true,
                        VP9Profile2HDR},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, H265_HDR10PLUS_RES, H265_HDR10PLUS_STATIC_INFO,
                        H265_HDR10PLUS_DYNAMIC_INFO, false, HEVCProfileMain10HDR10},
                {MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HDR10PLUS_RES, VP9_HDR10PLUS_STATIC_INFO,
                        VP9_HDR10PLUS_DYNAMIC_INFO, true, VP9Profile2HDR},
                {MediaFormat.MIMETYPE_VIDEO_AV1, AV1_HLG_RES, null, null, false,
                        AV1ProfileMain10},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, H265_HLG_RES, null, null, false,
                        HEVCProfileMain10},
                {MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HLG_RES, null, null, false,
                        VP9Profile2},
        });

        return prepareParamList(exhaustiveArgsList);
    }

    private static Set<Long> getAvailableHDRCaptureProfiles() throws CameraAccessException {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CameraManager cm = context.getSystemService(CameraManager.class);
        String[] cameraIdList = cm.getCameraIdList();
        for (String cameraId : cameraIdList) {
            CameraCharacteristics ch = cm.getCameraCharacteristics(cameraId);
            int[] caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            if (IntStream.of(caps).anyMatch(x -> x
                    == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT)) {
                Set<Long> profiles =
                        ch.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)
                                .getSupportedProfiles();
                return profiles;
            }
        }
        return null;
    }

    private static boolean isHardwareAcceleratedCodec(String codecName) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo codecInfo : mcl.getCodecInfos()) {
            if (codecName.equals(codecInfo.getName())) {
                return codecInfo.isHardwareAccelerated();
            }
        }
        return false;
    }

    @CddTest(requirements = {"5.12/C-6-6"})
    @Test
    @ApiTest(apis = {"android.media.MediaFormat#KEY_COLOR_TRANSFER_REQUEST"})
    public void testHdrToSdr() throws Exception {
        AssetFileDescriptor infd = null;
        final boolean dynamic = mHdrDynamicInfo != null;

        Preconditions.assertTestFileExists(MEDIA_DIR + mInputFile);
        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(MEDIA_DIR + mInputFile);

        MediaFormat format = null;
        int trackIndex = -1;
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            format = mExtractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                trackIndex = i;
                break;
            }
        }
        assumeTrue("Media format of input file is not supported.",
                MediaUtils.supports(mCodecName, format));

        long requiredHdrProfile = PROFILE_HDR_MAP.get(mMediaType).get(mProfile);
        Set<Long> availableProfiles = getAvailableHDRCaptureProfiles();
        boolean isProfileSupported =
                availableProfiles != null && availableProfiles.contains(requiredHdrProfile);
        assumeTrue("HDR capture is not supported for input file profile.", isProfileSupported);

        // If extractor returns profile, ensure it is as expected.
        int profile = format.getInteger(MediaFormat.KEY_PROFILE, -1);
        if (profile != -1) {
            assertEquals("profile returned by the extractor is invalid.",
                         mProfile, profile);
        }

        mExtractor.selectTrack(trackIndex);
        Log.v(TAG, "format " + format);

        String mime = format.getString(MediaFormat.KEY_MIME);
        format.setInteger(
                MediaFormat.KEY_COLOR_TRANSFER_REQUEST, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);

        final Surface surface = getActivity().getSurfaceHolder().getSurface();

        Log.d(TAG, "Testing candicate decoder " + mCodecName);
        CountDownLatch latch = new CountDownLatch(1);
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

        mDecoder = MediaCodec.createByCodecName(mCodecName);
        mDecoder.setCallback(new MediaCodec.Callback() {
            boolean mInputEOS;
            boolean mOutputReceived;
            int mInputCount;
            int mOutputCount;

            @Override
            public void onOutputBufferAvailable(
                    MediaCodec codec, int index, BufferInfo info) {
                if (mOutputReceived && !DEBUG_HDR_TO_SDR_PLAY_VIDEO) {
                    return;
                }

                MediaFormat bufferFormat = codec.getOutputFormat(index);
                Log.i(TAG, "got output buffer: format " + bufferFormat);

                assertEquals("unexpected color transfer for the buffer",
                        MediaFormat.COLOR_TRANSFER_SDR_VIDEO,
                        bufferFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER, 0));
                ByteBuffer staticInfo = bufferFormat.getByteBuffer(
                        MediaFormat.KEY_HDR_STATIC_INFO, null);
                if (staticInfo != null) {
                    assertTrue(
                            "Buffer should not have a valid static HDR metadata present",
                            Arrays.equals(loadByteArrayFromString(INVALID_HDR_STATIC_INFO),
                                    staticInfo.array()));
                }
                ByteBuffer hdr10PlusInfo = bufferFormat.getByteBuffer(
                        MediaFormat.KEY_HDR10_PLUS_INFO, null);
                if (hdr10PlusInfo != null) {
                    assertEquals(
                            "Buffer should not have a valid dynamic HDR metadata present",
                            0, hdr10PlusInfo.remaining());
                }

                if (!dynamic) {
                    codec.releaseOutputBuffer(index,  true);
                    mOutputReceived = true;
                    latch.countDown();
                } else {
                    codec.releaseOutputBuffer(index,  true);
                    mOutputCount++;
                    if (mOutputCount >= mHdrDynamicInfo.length) {
                        mOutputReceived = true;
                        latch.countDown();
                    }
                }
            }

            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {
                // keep queuing until input EOS, or first output buffer received.
                if (mInputEOS || (mOutputReceived && !DEBUG_HDR_TO_SDR_PLAY_VIDEO)) {
                    return;
                }

                ByteBuffer inputBuffer = codec.getInputBuffer(index);

                if (mExtractor.getSampleTrackIndex() == -1) {
                    codec.queueInputBuffer(
                            index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    mInputEOS = true;
                } else {
                    int size = mExtractor.readSampleData(inputBuffer, 0);
                    long timestamp = mExtractor.getSampleTime();
                    mExtractor.advance();

                    if (dynamic && mMetaDataInContainer) {
                        final Bundle params = new Bundle();
                        // TODO: extractor currently doesn't extract the dynamic metadata.
                        // Send in the test pattern for now to test the metadata propagation.
                        byte[] info = loadByteArrayFromString(mHdrDynamicInfo[mInputCount]);
                        params.putByteArray(MediaFormat.KEY_HDR10_PLUS_INFO, info);
                        codec.setParameters(params);
                        mInputCount++;
                        if (mInputCount >= mHdrDynamicInfo.length) {
                            mInputEOS = true;
                        }
                    }
                    codec.queueInputBuffer(index, 0, size, timestamp, 0);
                }
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                Log.e(TAG, "got codec exception", e);
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                Log.i(TAG, "got output format: " + format);
                ByteBuffer staticInfo = format.getByteBuffer(
                        MediaFormat.KEY_HDR_STATIC_INFO, null);
                if (staticInfo != null) {
                    assertTrue(
                            "output format should not have a valid " +
                                    "static HDR metadata present",
                            Arrays.equals(loadByteArrayFromString(INVALID_HDR_STATIC_INFO),
                                    staticInfo.array()));
                }
            }
        });
        mDecoder.configure(format, surface, null/*crypto*/, 0/*flags*/);
        int transferRequest = mDecoder.getInputFormat().getInteger(
                MediaFormat.KEY_COLOR_TRANSFER_REQUEST, 0);
        if (DecoderTest.isDefaultCodec(mCodecName, mMediaType) && isHardwareAcceleratedCodec(
                mCodecName)) {
            assertFalse(mCodecName + " does not support HDR to SDR tone mapping",
                    transferRequest == 0);
        } else {
            assumeFalse(mCodecName + " does not support HDR to SDR tone mapping",
                    transferRequest == 0);
        }
        assertEquals("unexpected color transfer request value from input format",
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO, transferRequest);
        mDecoder.start();
        try {
            assertTrue(latch.await(2000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail("playback interrupted");
        }
        if (DEBUG_HDR_TO_SDR_PLAY_VIDEO) {
            Thread.sleep(5000);
        }
        mDecoder.stop();
    }
}
