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

import static android.mediav2.common.cts.CodecDecoderDrmTestBase.setUpMediaDrmAndCrypto;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.annotation.RequiresApi;
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
import android.util.Log;
import android.util.Pair;

import androidx.test.filters.SdkSuppress;

import org.junit.After;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.UUID;

/**
 * Wrapper class for trying and testing secure mediacodec decoder components in block model large
 * audio buffer mode
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@RequiresApi(api = Build.VERSION_CODES.R)
public class CodecDecoderBlockModelMultiAccessUnitDrmTestBase
        extends CodecDecoderBlockModelMultiAccessUnitTestBase {
    private static final String LOG_TAG =
            CodecDecoderBlockModelMultiAccessUnitDrmTestBase.class.getSimpleName();

    protected MediaDrm mDrm;
    protected MediaCrypto mCrypto;

    public CodecDecoderBlockModelMultiAccessUnitDrmTestBase(String codecName, String mediaType,
            String testFile, String allTestParams) {
        super(codecName, mediaType, testFile, allTestParams);
    }

    @After
    public void tearDownCodecDecoderBlockModelMultiAccessUnitDrmTestBase() {
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
    protected void configureCodec(MediaFormat format, boolean isAsyncUnUsed,
            boolean signalEOSWithLastFrame, boolean isEncoder, int flags) {
        if (ENABLE_LOGS) {
            if (!isAsyncUnUsed) {
                Log.d(LOG_TAG, "Ignoring synchronous mode of operation request");
            }
        }
        flags |= MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL;
        configureCodecCommon(format, true, signalEOSWithLastFrame, isEncoder, flags);
        mCodec.configure(format, mSurface, mCrypto, flags);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "codec configured");
        }
    }

    @Override
    protected void enqueueInput(int bufferIndex) {
        if (mExtractor.getSampleSize() < 0) {
            enqueueEOS(bufferIndex);
            return;
        }
        ArrayDeque<MediaCodec.BufferInfo> bufferInfos = new ArrayDeque<>();
        ArrayDeque<MediaCodec.CryptoInfo> cryptoInfos = new ArrayDeque<>();
        mLinearInputBlock.allocateBlock(mCodecName, mMaxInputSize);
        int basePts = (int) mExtractor.getSampleTime();
        boolean baseEncrypted =
                ((mExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0);
        while (true) {
            int size = (int) mExtractor.getSampleSize();
            if (size <= 0) break;
            int deltaPts = (int) mExtractor.getSampleTime() - basePts;
            assertTrue("Difference between basePts: " + basePts + " and current pts: "
                    + mExtractor.getSampleTime() + " should be greater than or equal "
                    + "to zero.\n" + mTestConfig + mTestEnv, deltaPts >= 0);
            if (deltaPts / 1000 > mMaxInputLimitMs) {
                break;
            }
            if (mLinearInputBlock.getOffset() + size <= mLinearInputBlock.getBufferCapacity()) {
                mExtractor.readSampleData(mLinearInputBlock.getBuffer(),
                        mLinearInputBlock.getOffset());
            } else {
                if (mLinearInputBlock.getOffset() == 0) {
                    throw new RuntimeException(String.format(Locale.getDefault(),
                            "access unit size %d exceeds capacity of the buffer %d, unable to "
                                    + "queue input", size, mLinearInputBlock.getBufferCapacity()));
                }
                break;
            }
            int extractorFlags = mExtractor.getSampleFlags();
            long pts = mExtractor.getSampleTime();
            boolean currEncrypted = (extractorFlags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0;
            if (mLinearInputBlock.getOffset() != 0) {
                if (baseEncrypted != currEncrypted) break;
            }
            if (currEncrypted) {
                MediaCodec.CryptoInfo cryptoInfo = new MediaCodec.CryptoInfo();
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
            if (!mExtractor.advance() && mSignalEOSWithLastFrame) {
                codecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mSawInputEOS = true;
            }
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            bufferInfo.set(mLinearInputBlock.getOffset(), size, pts, codecFlags);
            mLinearInputBlock.setOffset(mLinearInputBlock.getOffset() + bufferInfo.size);
            bufferInfos.add(bufferInfo);
        }
        if (bufferInfos.size() > 0) {
            MediaCodec.QueueRequest request = mCodec.getQueueRequest(bufferIndex);
            if (baseEncrypted) {
                request.setMultiFrameEncryptedLinearBlock(mLinearInputBlock.getBlock(), bufferInfos,
                        cryptoInfos);
            } else {
                request.setMultiFrameLinearBlock(mLinearInputBlock.getBlock(), bufferInfos);
            }
            request.queue();
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
