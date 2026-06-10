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

import static android.mediav2.common.cts.CodecTestBase.MEDIA_CODEC_LIST_ALL;
import static android.mediav2.common.cts.CodecTestBase.PER_TEST_TIMEOUT_SMALL_TEST_MS;
import static android.mediav2.cts.NativeAMediaCodecInfoTest.SPECIAL_CODEC;

import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.EncoderConfigParams;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.Preconditions;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * This class validates the NDK api for media codec store. The scope of this test is to only
 * check if the information advertised is ok. If the component is actually capable of supporting the
 * advertised information is beyond the scope of the test.
 */
@SdkSuppress(minSdkVersion = 36)
@SmallTest
@RunWith(AndroidJUnit4.class)
public class NativeAMediaCodecStoreTest {
    private static final String LOG_TAG = NativeAMediaCodecStoreTest.class.getSimpleName();
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    // in sync with AMediaCodecType values
    private static final int FLAG_DECODER = 1;
    private static final int FLAG_ENCODER = 2;

    private final StringBuilder mTestResults = new StringBuilder();

    static {
        System.loadLibrary("ctsmediav2codecinfo_jni");
    }

    @ApiTest(apis = {"AMediaCodecStore_getSupportedMediaTypes"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testGetSupportedMediaTypes() {
        Map<String, Integer> mediaTypeModes = new HashMap<>();
        MediaCodecInfo[] codecInfos = MEDIA_CODEC_LIST_ALL.getCodecInfos();
        for (MediaCodecInfo codecInfo : codecInfos) {
            if (codecInfo.isAlias()) continue;
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                MediaCodecInfo.CodecCapabilities codecCapabilities =
                        codecInfo.getCapabilitiesForType(type);
                if (codecCapabilities.isFeatureSupported(SPECIAL_CODEC)) continue;
                Integer val;
                if (mediaTypeModes.containsKey(type)) {
                    val = mediaTypeModes.get(type);
                    val |= codecInfo.isEncoder() ? FLAG_ENCODER : FLAG_DECODER;
                } else {
                    val = codecInfo.isEncoder() ? FLAG_ENCODER : FLAG_DECODER;
                }
                mediaTypeModes.put(type, val);
            }
        }
        String[] mediaTypes = new String[mediaTypeModes.size()];
        int[] modes = new int[mediaTypeModes.size()];
        int i = 0;
        for (Map.Entry<String, Integer> entry : mediaTypeModes.entrySet()) {
            mediaTypes[i] = entry.getKey();
            modes[i] = entry.getValue();
            i++;
        }
        boolean isPass =
                nativeTestAMediaCodecStoreGetSupportedTypes(mediaTypes, modes, mTestResults);
        Assert.assertTrue(mTestResults.toString(), isPass);
    }

    private static List<String> getAllCodecs(String mediaType, List<MediaFormat> formats,
            boolean isEncoder) {
        MediaCodecInfo[] codecInfos = MEDIA_CODEC_LIST_ALL.getCodecInfos();
        List<String> listOfCodecs = new ArrayList<>();
        for (MediaCodecInfo codecInfo : codecInfos) {
            if (codecInfo.isEncoder() != isEncoder) continue;
            if (codecInfo.isAlias()) continue;
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                MediaCodecInfo.CodecCapabilities codecCapabilities =
                        codecInfo.getCapabilitiesForType(type);
                if (codecCapabilities.isFeatureSupported(SPECIAL_CODEC)) continue;
                if (mediaType != null) {
                    if (type.equalsIgnoreCase(mediaType)) {
                        boolean isOk = true;
                        if (formats != null) {
                            for (MediaFormat format : formats) {
                                if (!codecCapabilities.isFormatSupported(format)) {
                                    isOk = false;
                                    break;
                                }
                            }
                        }
                        if (isOk) listOfCodecs.add(codecInfo.getName());
                    }
                } else {
                    listOfCodecs.add(codecInfo.getName());
                }
            }
        }
        return listOfCodecs;
    }

    private native boolean nativeTestAMediaCodecStoreGetSupportedTypes(String[] mediaTypes,
            int[] modes, StringBuilder retMsg);

    @ApiTest(apis = {"AMediaCodecStore_findNextDecoderForFormat",
            "AMediaCodecStore_findNextEncoderForFormat"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testGetNextCodecForFormat() throws IOException {
        ArrayList<MediaFormat> formats = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get(MEDIA_DIR))) {
            paths.forEach(path -> {
                if (Files.isRegularFile(path)) {
                    MediaExtractor extractor = new MediaExtractor();
                    try {
                        extractor.setDataSource(path.toString());
                        for (int i = 0; i < extractor.getTrackCount(); i++) {
                            MediaFormat format = extractor.getTrackFormat(i);
                            formats.clear();
                            formats.add(format);
                            String serialize = EncoderConfigParams.serializeMediaFormat(format);
                            String mediaType = format.getString(MediaFormat.KEY_MIME);
                            String[] codecs =
                                    getAllCodecs(mediaType, formats, true).toArray(new String[0]);
                            boolean isPass =
                                    nativeTestAMediaCodecStoreGetNextCodecsForFormat(serialize,
                                            EncoderConfigParams.TOKEN_SEPARATOR, codecs, true,
                                            mTestResults);
                            Assert.assertTrue(mTestResults.toString(), isPass);
                            codecs = getAllCodecs(mediaType, formats, false).toArray(new String[0]);
                            isPass = nativeTestAMediaCodecStoreGetNextCodecsForFormat(serialize,
                                    EncoderConfigParams.TOKEN_SEPARATOR, codecs, false,
                                    mTestResults);
                            Assert.assertTrue(mTestResults.toString(), isPass);
                        }
                        extractor.release();
                    } catch (IOException ignored) {
                        Preconditions.assertTestFileExists(path.toString());
                        Log.d(LOG_TAG, "encountered IOException for resource " + path);
                    } finally {
                        extractor.release();
                    }
                }
            });
        }
        List<String> encoders = getAllCodecs(null, null, true);
        List<String> decoders = getAllCodecs(null, null, false);
        boolean isPass;
        isPass = nativeTestAMediaCodecStoreGetNextCodecsForFormat(null, null,
                encoders.toArray(new String[0]), true, mTestResults);
        Assert.assertTrue(mTestResults.toString(), isPass);
        isPass = nativeTestAMediaCodecStoreGetNextCodecsForFormat(null, null,
                decoders.toArray(new String[0]), false, mTestResults);
        Assert.assertTrue(mTestResults.toString(), isPass);
    }

    private native boolean nativeTestAMediaCodecStoreGetNextCodecsForFormat(String formatString,
            String formatSeparator, String[] encoders, boolean isEncoder, StringBuilder retMsg);

    @ApiTest(apis = {"AMediaCodecStore_getCodecInfo"})
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testGetCodecInfo() {
        boolean isPass = nativeTestAMediaCodecStoreGetCodecInfo(mTestResults);
        Assert.assertTrue(mTestResults.toString(), isPass);
    }

    private native boolean nativeTestAMediaCodecStoreGetCodecInfo(StringBuilder retMsg);
}
