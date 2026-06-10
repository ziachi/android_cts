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

import static org.junit.Assume.assumeFalse;

import android.hardware.display.DisplayManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.cts.MediaTestBase;
import android.view.Display;

import org.junit.After;
import org.junit.Before;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HDRDecoderTestBase extends MediaTestBase {
    private static final String TAG = "HDRDecoderTestBase";
    protected static final String MEDIA_DIR = WorkDir.getMediaDirString();

    protected DisplayManager mDisplayManager;
    protected MediaExtractor mExtractor = null;
    protected MediaCodec mDecoder = null;

    protected static final String VP9_HDR_RES = "video_1280x720_vp9_hdr_static_3mbps.mkv";
    protected static final String VP9_HDR_STATIC_INFO =
            "00 d0 84 80 3e c2 33 c4  86 4c 1d b8 0b 13 3d 42" +
            "40 e8 03 64 00 e8 03 2c  01                     " ;

    protected static final String AV1_HDR_RES = "video_1280x720_av1_hdr_static_3mbps.webm";
    protected static final String AV1_HDR_STATIC_INFO =
            "00 d0 84 80 3e c2 33 c4  86 4c 1d b8 0b 13 3d 42" +
            "40 e8 03 64 00 e8 03 2c  01                     " ;

    // Expected value of MediaFormat.KEY_HDR_STATIC_INFO key.
    // The associated value is a ByteBuffer. This buffer contains the raw contents of the
    // Static Metadata Descriptor (including the descriptor ID) of an HDMI Dynamic Range and
    // Mastering InfoFrame as defined by CTA-861.3.
    // Media frameworks puts the display primaries in RGB order, here we verify the three
    // primaries are indeed in this order and fail otherwise.
    protected static final String H265_HDR10_RES = "video_1280x720_hevc_hdr10_static_3mbps.mp4";
    protected static final String H265_HDR10_STATIC_INFO =
            "00 d0 84 80 3e c2 33 c4  86 4c 1d b8 0b 13 3d 42" +
            "40 e8 03 00 00 e8 03 90  01                     " ;

    protected static final String VP9_HDR10PLUS_RES = "video_bikes_hdr10plus.webm";
    protected static final String VP9_HDR10PLUS_STATIC_INFO =
            "00 4c 1d b8 0b d0 84 80  3e c0 33 c4 86 12 3d 42" +
            "40 e8 03 32 00 e8 03 c8  00                     " ;
    // TODO: Use some manually extracted metadata for now.
    // MediaExtractor currently doesn't have an API for extracting
    // the dynamic metadata. Get the metadata from extractor when
    // it's supported.
    protected static final String[] VP9_HDR10PLUS_DYNAMIC_INFO = new String[]{
            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
            "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
            "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00" ,

            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
            "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
            "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00" ,

            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0e 80 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
            "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
            "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00" ,

            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0e 80 00 24 08 00 00 28  00 00 50 00 28 c8 00 c9" +
            "90 02 aa 58 05 ca d0 0c  0a f8 16 83 18 9c 18 00" +
            "40 78 13 64 d5 7c 2e 2c  c3 59 de 79 6e c3 c2 00" ,
    };

    protected static final String H265_HDR10PLUS_RES = "video_h265_hdr10plus.mp4";
    protected static final String H265_HDR10PLUS_STATIC_INFO =
            "00 4c 1d b8 0b d0 84 80  3e c2 33 c4 86 13 3d 42" +
            "40 e8 03 32 00 e8 03 c8  00                     " ;
    protected static final String[] H265_HDR10PLUS_DYNAMIC_INFO = new String[]{
            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0f 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 a1" +
            "90 03 9a 58 0b 6a d0 23  2a f8 40 8b 18 9c 18 00" +
            "40 78 13 64 cf 78 ed cc  bf 5a de f9 8e c7 c3 00" ,

            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 a1" +
            "90 03 9a 58 0b 6a d0 23  2a f8 40 8b 18 9c 18 00" +
            "40 78 13 64 cf 78 ed cc  bf 5a de f9 8e c7 c3 00" ,

            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0f 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 a1" +
            "90 03 9a 58 0b 6a d0 23  2a f8 40 8b 18 9c 18 00" +
            "40 78 13 64 cf 78 ed cc  bf 5a de f9 8e c7 c3 00" ,

            "b5 00 3c 00 01 04 00 40  00 0c 80 4e 20 27 10 00" +
            "0a 00 00 24 08 00 00 28  00 00 50 00 28 c8 00 a1" +
            "90 03 9a 58 0b 6a d0 23  2a f8 40 8b 18 9c 18 00" +
            "40 78 13 64 cf 78 ed cc  bf 5a de f9 8e c7 c3 00"
    };

    protected static final String AV1_HLG_RES = "cosmat_520x390_24fps_768kbps_av1_10bit.mkv";
    protected static final String H265_HLG_RES = "cosmat_520x390_24fps_crf22_hevc_10bit.mkv";
    protected static final String VP9_HLG_RES = "cosmat_520x390_24fps_crf22_vp9_10bit.mkv";

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        int numberOfSupportedHdrTypes =
                mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY).getHdrCapabilities()
                        .getSupportedHdrTypes().length;
        assumeFalse("Device doesn't support HDR display", numberOfSupportedHdrTypes == 0);

        mExtractor = new MediaExtractor();
    }

    @After
    @Override
    public void tearDown() {
        if (mDecoder != null) {
            mDecoder.release();
        }
        if (mExtractor != null) {
            mExtractor.release();
        }
        super.tearDown();
    }

    // helper to load byte[] from a String
    public byte[] loadByteArrayFromString(final String str) {
        Pattern pattern = Pattern.compile("[0-9a-fA-F]{2}");
        Matcher matcher = pattern.matcher(str);
        // allocate a large enough byte array first
        byte[] tempArray = new byte[str.length() / 2];
        int i = 0;
        while (matcher.find()) {
            tempArray[i++] = (byte) Integer.parseInt(matcher.group(), 16);
        }
        return Arrays.copyOfRange(tempArray, 0, i);
    }
}
