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

package android.media.decoder.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.TestUtils;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.Surface;

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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediaHeavyPresubmitTest
@AppModeFull(reason = "There should be no instant apps specific behavior related to decoders")
@RunWith(Parameterized.class)
public class HDRDecoderTest extends HDRDecoderTestBase {
    private static final String TAG = "HDRDecoderTest";

    @Parameterized.Parameter(0)
    public String mCodecName;

    @Parameterized.Parameter(1)
    public String mTestId;

    @Parameterized.Parameter(2)
    public String mMediaType;

    @Parameterized.Parameter(3)
    public String mInputFile;

    @Parameterized.Parameter(4)
    public String mHdrStaticInfo;

    @Parameterized.Parameter(5)
    public String[] mHdrDynamicInfo;

    @Parameterized.Parameter(6)
    public boolean mMetaDataInContainer;

    private static int getHdrProfile(String mediaType, boolean dynamic) {
        int profile = 0;
        if (MediaFormat.MIMETYPE_VIDEO_HEVC.equals(mediaType)) {
            profile = dynamic ? MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
                    : MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10;
        } else if (MediaFormat.MIMETYPE_VIDEO_VP9.equals(mediaType)) {
            profile = dynamic ? MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus
                    : MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR;
        } else if (MediaFormat.MIMETYPE_VIDEO_AV1.equals(mediaType)) {
            profile = dynamic ? MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus
                    : MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10;
        } else {
            Log.e(TAG, "Unsupported mediaType " + mediaType);
        }
        return profile;
    }

    private static List<Object[]> prepareParamList(List<Object[]> exhaustiveArgsList) {
        final List<Object[]> argsList = new ArrayList<>();
        int argLength = exhaustiveArgsList.get(0).length;
        for (Object[] arg : exhaustiveArgsList) {
            String mediaType = (String) arg[0];
            boolean dynamic = (String[]) arg[3] != null;

            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, mediaType);
            format.setInteger(MediaFormat.KEY_PROFILE, getHdrProfile(mediaType, dynamic));

            String[] decoderNames = MediaUtils.getDecoderNames(format);

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
                Object[] testArgs = new Object[argLength + 2];
                testArgs[0] = decoder;
                testArgs[1] = dynamic ? "dynamic" : "static";
                System.arraycopy(arg, 0, testArgs, 2, argLength);
                argsList.add(testArgs);
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{2}")
    public static Collection<Object[]> input() {
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AV1, AV1_HDR_RES, AV1_HDR_STATIC_INFO, null, false},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, H265_HDR10_RES, H265_HDR10_STATIC_INFO, null,
                        false},
                {MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HDR_RES, VP9_HDR_STATIC_INFO, null, true},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, H265_HDR10PLUS_RES, H265_HDR10PLUS_STATIC_INFO,
                        H265_HDR10PLUS_DYNAMIC_INFO, false},
                {MediaFormat.MIMETYPE_VIDEO_VP9, VP9_HDR10PLUS_RES, VP9_HDR10PLUS_STATIC_INFO,
                        VP9_HDR10PLUS_DYNAMIC_INFO, true},
        });

        return prepareParamList(exhaustiveArgsList);
    }

    private void verifyHdrStaticInfo(String reason, MediaFormat format, String pattern) {
        ByteBuffer staticMetadataBuffer = format.containsKey("hdr-static-info") ?
                format.getByteBuffer("hdr-static-info") : null;
        assertTrue(reason + ": empty",
                staticMetadataBuffer != null && staticMetadataBuffer.remaining() > 0);
        assertTrue(reason + ": mismatch",
                Arrays.equals(loadByteArrayFromString(pattern), staticMetadataBuffer.array()));
    }

    private void verifyHdrDynamicInfo(String reason, MediaFormat format, String pattern) {
        ByteBuffer hdr10PlusInfoBuffer = format.containsKey(MediaFormat.KEY_HDR10_PLUS_INFO) ?
                format.getByteBuffer(MediaFormat.KEY_HDR10_PLUS_INFO) : null;
        assertTrue(reason + ":empty",
                hdr10PlusInfoBuffer != null && hdr10PlusInfoBuffer.remaining() > 0);
        assertTrue(reason + ": mismatch",
                Arrays.equals(loadByteArrayFromString(pattern), hdr10PlusInfoBuffer.array()));
    }

    @CddTest(requirements = {"5.3.5/C-3-1", "5.3.7/C-4-1", "5.3.9/C-3-1"})
    @ApiTest(apis = {"android.media.MediaFormat#KEY_HDR_STATIC_INFO",
            "android.media.MediaFormat#KEY_HDR10_PLUS_INFO"})
    @Test
    public void testHdrMetadata() throws Exception {
        AssetFileDescriptor infd = null;
        final boolean dynamic = mHdrDynamicInfo != null;

        Preconditions.assertTestFileExists(MEDIA_DIR + mInputFile);

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

        assertTrue("Extractor failed to extract video track",
                format != null && trackIndex >= 0);
        if (mMetaDataInContainer) {
            verifyHdrStaticInfo("Extractor failed to extract static info", format,
                    mHdrStaticInfo);
        }

        mExtractor.selectTrack(trackIndex);
        Log.v(TAG, "format " + format);

        String mime = format.getString(MediaFormat.KEY_MIME);
        format.setInteger(MediaFormat.KEY_PROFILE, getHdrProfile(mime, dynamic));

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
                if (mOutputReceived) {
                    return;
                }

                MediaFormat bufferFormat = codec.getOutputFormat(index);
                Log.i(TAG, "got output buffer: format " + bufferFormat);

                verifyHdrStaticInfo("Output buffer has wrong static info",
                        bufferFormat, mHdrStaticInfo);

                if (!dynamic) {
                    codec.releaseOutputBuffer(index,  true);

                    mOutputReceived = true;
                    latch.countDown();
                } else {
                    ByteBuffer hdr10plus =
                            bufferFormat.containsKey(MediaFormat.KEY_HDR10_PLUS_INFO)
                            ? bufferFormat.getByteBuffer(MediaFormat.KEY_HDR10_PLUS_INFO)
                            : null;

                    verifyHdrDynamicInfo("Output buffer has wrong hdr10+ info",
                            bufferFormat, mHdrDynamicInfo[mOutputCount]);

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
                if (mInputEOS || mOutputReceived) {
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
                verifyHdrStaticInfo("Output format has wrong static info",
                        format, mHdrStaticInfo);
            }
        });
        mDecoder.configure(format, surface, null/*crypto*/, 0/*flags*/);
        mDecoder.start();
        try {
            assertTrue(latch.await(2000, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail("playback interrupted");
        }
        mDecoder.stop();
    }
}
