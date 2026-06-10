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

import static android.media.codec.Flags.FLAG_LARGE_AUDIO_FRAME_FINISH;
import static android.mediav2.common.cts.CodecDecoderDrmTestBase.setUpMediaDrmAndCrypto;

import static com.android.media.codec.flags.Flags.FLAG_LARGE_AUDIO_FRAME;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;
import android.util.Pair;

import androidx.test.filters.SdkSuppress;

import org.junit.After;
import org.junit.Assert;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.UUID;

/**
 * Wrapper class for trying and testing mediacodec secure decoder components in large buffer mode
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@RequiresFlagsEnabled({FLAG_LARGE_AUDIO_FRAME, FLAG_LARGE_AUDIO_FRAME_FINISH})
public class CodecDecoderMultiAccessUnitDrmTestBase extends CodecDecoderMultiAccessUnitTestBase {
    private static final String LOG_TAG =
            CodecDecoderMultiAccessUnitDrmTestBase.class.getSimpleName();

    protected MediaDrm mDrm;
    protected MediaCrypto mCrypto;

    public CodecDecoderMultiAccessUnitDrmTestBase(String codecName, String mediaType,
            String testFile, String allTestParams) {
        super(codecName, mediaType, testFile, allTestParams);
    }

    @After
    public void tearDownCodecDecoderMultiAccessUnitDrmTestBase() {
        tearDownCrypto();
    }

    public void setUpCrypto(UUID uuidCryptoScheme, byte[] drmInitData, byte[][] keys)
            throws UnsupportedSchemeException, NotProvisionedException, ResourceBusyException,
            MediaCryptoException {
        Pair<MediaDrm, MediaCrypto> cryptoPair = setUpMediaDrmAndCrypto(uuidCryptoScheme,
                drmInitData, keys);
        assertNotNull("failed to set up crypto session \n" + mTestConfig + mTestEnv, cryptoPair);
        mDrm = cryptoPair.first;
        mCrypto = cryptoPair.second;
    }

    public void tearDownCrypto() {
        if (mCrypto != null) {
            mCrypto.release();
            mCrypto = null;
        }
        if (mDrm != null) {
            mDrm.close();
            mDrm = null;
        }
    }

    @Override
    protected void configureCodec(MediaFormat format, boolean isAsync,
            boolean signalEosWithLastFrame, boolean isEncoder, int flags) {
        configureCodecCommon(format, isAsync, signalEosWithLastFrame, isEncoder, flags);
        mCodec.configure(format, mSurface, mCrypto, flags);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "codec configured");
        }
    }

    protected void enqueueInput(int bufferIndex) {
        Log.v(LOG_TAG, "enqueueInput: id: " + bufferIndex);
        if (mExtractor.getSampleSize() < 0) {
            enqueueEOS(bufferIndex);
        } else {
            ArrayDeque<MediaCodec.BufferInfo> bufferInfos = new ArrayDeque<>();
            ArrayDeque<MediaCodec.CryptoInfo> cryptoInfos = new ArrayDeque<>();
            ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
            Assert.assertNotNull("error, getInputBuffer returned null.\n", inputBuffer);
            int offset = 0;
            int basePts = (int) mExtractor.getSampleTime();
            boolean baseEncrypted =
                    ((mExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0);
            boolean currEncrypted;
            while (true) {
                int size = (int) mExtractor.getSampleSize();
                if (size <= 0) break;
                int deltaPts = (int) mExtractor.getSampleTime() - basePts;
                assertTrue("Difference between basePts: " + basePts + " and current pts: "
                        + mExtractor.getSampleTime() + " should be greater than or equal "
                        + "to zero.\n", deltaPts >= 0);
                if (deltaPts / 1000 > mMaxInputLimitMs) {
                    break;
                }
                if (offset + size <= inputBuffer.capacity()) {
                    mExtractor.readSampleData(inputBuffer, offset);
                } else {
                    if (offset == 0) {
                        throw new RuntimeException(String.format(Locale.getDefault(),
                                "access unit size %d exceeds capacity of the buffer %d, unable to "
                                        + "queue input", size, inputBuffer.capacity()));
                    }
                    break;
                }
                int extractorFlags = mExtractor.getSampleFlags();
                long pts = mExtractor.getSampleTime();
                if (offset != 0) {
                    currEncrypted = (extractorFlags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0;
                    if (baseEncrypted != currEncrypted) break;
                }
                MediaCodec.CryptoInfo cryptoInfo = null;
                if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) {
                    cryptoInfo = new MediaCodec.CryptoInfo();
                    mExtractor.getSampleCryptoInfo(cryptoInfo);
                    cryptoInfos.add(cryptoInfo);
                }
                int codecFlags = 0;
                if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
                }
                if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                    codecFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
                }
                if (!mExtractor.advance()) {
                    codecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    mSawInputEOS = true;
                }
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                bufferInfo.set(offset, size, pts, codecFlags);
                offset += bufferInfo.size;
                bufferInfos.add(bufferInfo);
            }
            if (bufferInfos.size() > 0) {
                if (baseEncrypted) {
                    mCodec.queueSecureInputBuffers(bufferIndex, bufferInfos, cryptoInfos);
                } else {
                    mCodec.queueInputBuffers(bufferIndex, bufferInfos);
                }
                for (MediaCodec.BufferInfo info : bufferInfos) {
                    if (info.size > 0 && (info.flags & (MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                            | MediaCodec.BUFFER_FLAG_PARTIAL_FRAME)) == 0) {
                        mOutputBuff.saveInPTS(info.presentationTimeUs);
                        mInputCount++;
                    }
                    if (ENABLE_LOGS) {
                        Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + info.size
                                + " pts: " + info.presentationTimeUs + " flags: " + info.flags);
                    }
                }
            }
        }
    }
}
