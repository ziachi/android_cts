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

import org.junit.After;

import java.util.UUID;

/**
 * Wrapper class for trying and testing secure mediacodec decoder components in block model mode
 */
@RequiresApi(api = Build.VERSION_CODES.R)
public class CodecDecoderBlockModelDrmTestBase extends CodecDecoderBlockModelTestBase {
    private static final String LOG_TAG = CodecDecoderBlockModelDrmTestBase.class.getSimpleName();

    protected MediaDrm mDrm;
    protected MediaCrypto mCrypto;

    public CodecDecoderBlockModelDrmTestBase(String codecName, String mediaType, String testFile,
            String allTestParams) {
        super(codecName, mediaType, testFile, allTestParams);
    }

    @After
    public void tearDownCodecDecoderBlockModelDrmTestBase() {
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
            boolean signalEOSWithLastFrameUnUsed, boolean isEncoder, int flags) {
        if (ENABLE_LOGS) {
            if (!isAsyncUnUsed) {
                Log.d(LOG_TAG, "Ignoring synchronous mode of operation request");
            }
            if (!signalEOSWithLastFrameUnUsed) {
                Log.d(LOG_TAG, "Ignoring signal eos separately request");
            }
        }
        flags |= MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL;
        configureCodecCommon(format, true, true, isEncoder, flags);
        mCodec.configure(format, mSurface, mCrypto, flags);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "codec configured");
        }
    }

    @Override
    protected void enqueueInput(int bufferIndex) {
        int sampleSize = (int) mExtractor.getSampleSize();
        if (sampleSize < 0) {
            enqueueEOS(bufferIndex);
            return;
        }
        if (mLinearInputBlock.getOffset() + sampleSize > mLinearInputBlock.getBufferCapacity()) {
            mLinearInputBlock.allocateBlock(mCodecName, Math.max(sampleSize, 8192));
        }
        long pts = mExtractor.getSampleTime();
        mExtractor.readSampleData(mLinearInputBlock.getBuffer(), mLinearInputBlock.getOffset());
        int extractorFlags = mExtractor.getSampleFlags();
        MediaCodec.CryptoInfo cryptoInfo = null;
        if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) {
            cryptoInfo = new MediaCodec.CryptoInfo();
            mExtractor.getSampleCryptoInfo(cryptoInfo);
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
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + sampleSize + " pts: " + pts
                    + " flags: " + codecFlags);
        }
        MediaCodec.QueueRequest request = mCodec.getQueueRequest(bufferIndex);
        if (cryptoInfo != null) {
            request.setEncryptedLinearBlock(mLinearInputBlock.getBlock(),
                    mLinearInputBlock.getOffset(), sampleSize, cryptoInfo);
        } else {
            request.setLinearBlock(mLinearInputBlock.getBlock(), mLinearInputBlock.getOffset(),
                    sampleSize);
        }
        request.setPresentationTimeUs(pts);
        request.setFlags(codecFlags);
        request.queue();
        if (sampleSize > 0) {
            if ((codecFlags & MediaCodec.BUFFER_FLAG_PARTIAL_FRAME) == 0) {
                mOutputBuff.saveInPTS(pts);
                mInputCount++;
            }
            mLinearInputBlock.setOffset(mLinearInputBlock.getOffset() + sampleSize);
        }
    }
}
