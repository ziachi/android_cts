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

package android.mediav2.common.cts;

import static android.media.codec.Flags.apvSupport;
import static android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_FIRST;
import static android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_LAST;

import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper class for trying and testing muxers.
 */
public class MuxerUtils {
    private static final String LOG_TAG = MuxerUtils.class.getSimpleName();

    private static final List<String> MEDIATYPE_LIST_FOR_TYPE_MP4 = new ArrayList<>(
            Arrays.asList(MediaFormat.MIMETYPE_VIDEO_MPEG4, MediaFormat.MIMETYPE_VIDEO_H263,
                    MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC,
                    MediaFormat.MIMETYPE_AUDIO_AAC));
    static {
        if (CodecTestBase.IS_AT_LEAST_U) {
            MEDIATYPE_LIST_FOR_TYPE_MP4.add(MediaFormat.MIMETYPE_VIDEO_AV1);
        }
        if (CodecTestBase.IS_AT_LEAST_B && apvSupport()) {
            MEDIATYPE_LIST_FOR_TYPE_MP4.add(MediaFormat.MIMETYPE_VIDEO_APV);
        }
    }
    private static final List<String> MEDIATYPE_LIST_FOR_TYPE_WEBM =
            Arrays.asList(MediaFormat.MIMETYPE_VIDEO_VP8, MediaFormat.MIMETYPE_VIDEO_VP9,
                    MediaFormat.MIMETYPE_AUDIO_VORBIS, MediaFormat.MIMETYPE_AUDIO_OPUS);
    private static final List<String> MEDIATYPE_LIST_FOR_TYPE_3GP =
            Arrays.asList(MediaFormat.MIMETYPE_VIDEO_MPEG4, MediaFormat.MIMETYPE_VIDEO_H263,
                    MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_AUDIO_AAC,
                    MediaFormat.MIMETYPE_AUDIO_AMR_NB, MediaFormat.MIMETYPE_AUDIO_AMR_WB);
    private static final List<String> MEDIATYPE_LIST_FOR_TYPE_OGG =
            Collections.singletonList(MediaFormat.MIMETYPE_AUDIO_OPUS);

    public static void muxOutput(String filePath, int muxerFormat, MediaFormat format,
            ByteBuffer buffer, ArrayList<MediaCodec.BufferInfo> infos) throws IOException {
        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(filePath, muxerFormat);
            int trackID = muxer.addTrack(format);
            muxer.start();
            for (MediaCodec.BufferInfo info : infos) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    muxer.writeSampleData(trackID, buffer, info);
                }
            }
            muxer.stop();
        } finally {
            if (muxer != null) muxer.release();
        }
    }

    public static boolean isMediaTypeContainerPairValid(String mediaType, int format) {
        boolean result = false;
        if (format == MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4) {
            result = MEDIATYPE_LIST_FOR_TYPE_MP4.contains(mediaType)
                    || mediaType.startsWith("application/");
        } else if (format == MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM) {
            result = MEDIATYPE_LIST_FOR_TYPE_WEBM.contains(mediaType);
        } else if (format == MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP) {
            result = MEDIATYPE_LIST_FOR_TYPE_3GP.contains(mediaType);
        } else if (format == MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG) {
            result = MEDIATYPE_LIST_FOR_TYPE_OGG.contains(mediaType);
        }
        return result;
    }

    public static int getMuxerFormatForMediaType(String mediaType) {
        for (int muxFormat = MUXER_OUTPUT_FIRST; muxFormat <= MUXER_OUTPUT_LAST; muxFormat++) {
            if (isMediaTypeContainerPairValid(mediaType, muxFormat)) {
                return muxFormat;
            }
        }
        fail("no configured muxer support for " + mediaType);
        return MUXER_OUTPUT_LAST;
    }

    public static String getTempFilePath(String infix) throws IOException {
        return File.createTempFile("tmp" + infix, ".bin").getAbsolutePath();
    }

}