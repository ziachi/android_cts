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

import static android.media.decoder.cts.DecoderTest.getAssetFileDescriptorFor;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaCodecWrapper;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.MediaTestBase;
import android.media.cts.NdkMediaCodec;
import android.media.cts.SdkMediaCodec;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.Surface;

import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@MediaHeavyPresubmitTest
@AppModeFull(reason = "There should be no instant apps specific behavior related to decoders")
@RunWith(Parameterized.class)
public class DecoderLowLatencyTest extends MediaTestBase {
    private static final String TAG = "DecoderLowLatencyTest";
    private static final String REPORT_LOG_NAME = "CtsMediaDecoderTestCases";

    public String mMediaType;
    public String mTestFile;
    public int mFrameCount;
    public String mDecoderName;

    public DecoderLowLatencyTest(String mediaType, String testFile, int frameCount,
            String decoderName) {
        mMediaType = mediaType;
        mTestFile = testFile;
        mFrameCount = frameCount;
        mDecoderName = decoderName;
    }

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
    }

    private static CodecCapabilities getCodecCapabilitiesForDecoder(String codecName, String mime) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo codecInfo : mcl.getCodecInfos()) {
            if (codecInfo.isEncoder()) {
                continue;
            }
            if (codecName.equals(codecInfo.getName())) {
                return codecInfo.getCapabilitiesForType(mime);
            }
        }
        return null;
    }

    static private List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
        final List<Object[]> argsList = new ArrayList<>();
        int argLength = exhaustiveArgsList.get(0).length;
        for (Object[] arg : exhaustiveArgsList) {
            String mediaType = (String) arg[0];
            String[] decoderNames = MediaUtils.getDecoderNamesForMime(mediaType);

            for (String decoder : decoderNames) {
                Object[] testArgs = new Object[argLength + 1];
                System.arraycopy(arg, 0, testArgs, 0, argLength);
                testArgs[argLength] = decoder;
                argsList.add(testArgs);
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{3}")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                // test video, frame count
                // AVC
                {MediaFormat.MIMETYPE_VIDEO_AVC,
                        "video_1280x720_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                        300},

                // HEVC
                {MediaFormat.MIMETYPE_VIDEO_HEVC,
                        "video_480x360_mp4_hevc_650kbps_30fps_aac_stereo_128kbps_48000hz.mp4", 300},

                // Vp9
                {MediaFormat.MIMETYPE_VIDEO_VP9,
                        "video_1280x720_webm_vp9_csd_309kbps_25fps_vorbis_stereo_128kbps_48000hz"
                                + ".webm", 300},
                {MediaFormat.MIMETYPE_VIDEO_VP9,
                        "bbb_s2_1920x1080_webm_vp9_0p41_10mbps_60fps_vorbis_6ch_384kbps_22050hz"
                                + ".webm", 300},
                {MediaFormat.MIMETYPE_VIDEO_VP9,
                        "bbb_s2_3840x2160_webm_vp9_0p51_20mbps_60fps_vorbis_6ch_384kbps_32000hz"
                                + ".webm", 300},

                // AV1
                {MediaFormat.MIMETYPE_VIDEO_AV1,
                        "video_480x360_webm_av1_400kbps_30fps_vorbis_stereo_128kbps_48000hz.webm"
                        , 300},
                {MediaFormat.MIMETYPE_VIDEO_AV1,
                        "video_1280x720_webm_av1_2000kbps_30fps_vorbis_stereo_128kbps_48000hz"
                                + ".webm", 300},
                {MediaFormat.MIMETYPE_VIDEO_AV1,
                        "video_1920x1080_webm_av1_7000kbps_60fps_vorbis_stereo_128kbps_48000hz"
                                + ".webm", 300},
                {MediaFormat.MIMETYPE_VIDEO_AV1,
                        "video_3840x2160_webm_av1_18000kbps_60fps_vorbis_stereo_128kbps_48000hz"
                                + ".webm", 300},
        }));
        return prepareParamList(exhaustiveArgsList);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_LowLatency",
            "android.media.MediaCodecInfo.CodecCapabilities#isFeatureSupported",
            "android.media.MediaFormat#KEY_LOW_LATENCY"})
    @Test
    public void testLowLatencyVideoSdk() throws Exception {
        MediaCodecWrapper decoder = new SdkMediaCodec(MediaCodec.createByCodecName(mDecoderName));
        testLowLatencyVideo(decoder, false);
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    @ApiTest(apis = {"android.media.MediaCodecInfo.CodecCapabilities#FEATURE_LowLatency",
            "android.media.MediaCodecInfo.CodecCapabilities#isFeatureSupported",
            "android.media.MediaFormat#KEY_LOW_LATENCY"})
    @Test
    public void testLowLatencyVideoNdk() throws Exception {
        MediaCodecWrapper decoder = new NdkMediaCodec(mDecoderName);
        testLowLatencyVideo(decoder, true);
    }

    private void testLowLatencyVideo(MediaCodecWrapper decoder, boolean useNdk) throws Exception {
        CodecCapabilities cap = getCodecCapabilitiesForDecoder(mDecoderName, mMediaType);
        assumeTrue("Low latency feature not supported by " + mDecoderName,
                cap != null && cap.isFeatureSupported(CodecCapabilities.FEATURE_LowLatency));

        AssetFileDescriptor fd = getAssetFileDescriptorFor(mTestFile);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        fd.close();

        MediaFormat format = null;
        int trackIndex = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            format = extractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                trackIndex = i;
                break;
            }
        }

        assertTrue("No video track was found", trackIndex >= 0);
        assumeTrue("Format is not supported", MediaUtils.supports(mDecoderName, format));
        extractor.selectTrack(trackIndex);

        Log.v(TAG, "found decoder " + mDecoderName + " for format: " + format);

        Surface surface = getActivity().getSurfaceHolder().getSurface();

        format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        decoder.configure(format, 0 /* flags */, surface);
        decoder.start();

        if (!useNdk) {
            decoder.getInputBuffers();
        }
        ByteBuffer[] codecOutputBuffers = decoder.getOutputBuffers();
        String decoderOutputFormatString = null;

        // start decoding
        final long kTimeOutUs = 1000000;  // 1 second
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int bufferCounter = 0;
        long[] latencyMs = new long[mFrameCount];
        boolean waitingForOutput = false;
        long startTimeMs = System.currentTimeMillis();
        while (bufferCounter < mFrameCount) {
            if (!waitingForOutput) {
                int inputBufferId = decoder.dequeueInputBuffer(kTimeOutUs);
                if (inputBufferId < 0) {
                    Log.v(TAG, "no input buffer");
                    break;
                }

                ByteBuffer dstBuf = decoder.getInputBuffer(inputBufferId);

                int sampleSize = extractor.readSampleData(dstBuf, 0 /* offset */);
                long presentationTimeUs = 0;
                if (sampleSize < 0) {
                    Log.v(TAG, "had input EOS, early termination at frame " + bufferCounter);
                    break;
                } else {
                    presentationTimeUs = extractor.getSampleTime();
                }

                startTimeMs = System.currentTimeMillis();
                decoder.queueInputBuffer(
                        inputBufferId,
                        0 /* offset */,
                        sampleSize,
                        presentationTimeUs,
                        0 /* flags */);

                extractor.advance();
                waitingForOutput = true;
            }

            int outputBufferId = decoder.dequeueOutputBuffer(info, kTimeOutUs);

            if (outputBufferId >= 0) {
                waitingForOutput = false;
                //Log.d(TAG, "got output, size " + info.size + ", time " + info.presentationTimeUs);
                latencyMs[bufferCounter++] = System.currentTimeMillis() - startTimeMs;
                // TODO: render the frame and find the rendering time to calculate the total delay
                decoder.releaseOutputBuffer(outputBufferId, false /* render */);
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = decoder.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                decoderOutputFormatString = decoder.getOutputFormatString();
                Log.d(TAG, "output format has changed to " + decoderOutputFormatString);
            } else {
                fail("No output buffer returned without frame delay, status " + outputBufferId);
            }
        }

        assertTrue("No INFO_OUTPUT_FORMAT_CHANGED from decoder", decoderOutputFormatString != null);

        long latencyMean = 0;
        long latencyMax = 0;
        int maxIndex = 0;
        for (int i = 0; i < bufferCounter; ++i) {
            latencyMean += latencyMs[i];
            if (latencyMs[i] > latencyMax) {
                latencyMax = latencyMs[i];
                maxIndex = i;
            }
        }
        if (bufferCounter > 0) {
            latencyMean /= bufferCounter;
        }
        Log.d(TAG, "latency average " + latencyMean + " ms, max " + latencyMax +
                " ms at frame " + maxIndex);

        DeviceReportLog log = new DeviceReportLog(REPORT_LOG_NAME, "video_decoder_latency");
        String mime = format.getString(MediaFormat.KEY_MIME);
        int width = format.getInteger(MediaFormat.KEY_WIDTH);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT);
        log.addValue("codec_name", mDecoderName, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("mime_type", mime, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("width", width, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("height", height, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("video_res", mTestFile, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("decode_to", surface == null ? "buffer" : "surface",
                ResultType.NEUTRAL, ResultUnit.NONE);

        log.addValue("average_latency", latencyMean, ResultType.LOWER_BETTER, ResultUnit.MS);
        log.addValue("max_latency", latencyMax, ResultType.LOWER_BETTER, ResultUnit.MS);

        log.submit(getInstrumentation());

        decoder.stop();
        decoder.release();
        extractor.release();
    }
}