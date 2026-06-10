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

import static org.junit.Assert.assertEquals;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.junit.After;

import java.nio.ByteBuffer;

/**
 * Wrapper class for trying and testing mediacodec encoder components in block model mode.
 */
@RequiresApi(api = Build.VERSION_CODES.R)
public class CodecEncoderBlockModelTestBase extends CodecEncoderTestBase {
    private static final String LOG_TAG = CodecEncoderBlockModelTestBase.class.getSimpleName();
    private static final int MAX_INPUT_SIZE_MS = 10;

    private final CodecDecoderBlockModelTestBase.LinearBlockWrapper
            mLinearInputBlock = new CodecDecoderBlockModelTestBase.LinearBlockWrapper();

    // this is made public so that independent instances can configure it on demand
    public float mMaxInputSizeInMs = MAX_INPUT_SIZE_MS;

    public CodecEncoderBlockModelTestBase(String encoder, String mediaType,
            EncoderConfigParams[] encCfgParams, String allTestParams) {
        super(encoder, mediaType, encCfgParams, allTestParams);
    }

    @After
    public void tearDownCodecEncoderBlockModelTestBase() {
        mLinearInputBlock.recycle();
    }

    @Override
    protected void configureCodec(MediaFormat format, boolean isAsyncUnUsed,
            boolean signalEOSWithLastFrame, boolean isEncoder) {
        if (ENABLE_LOGS) {
            if (!isAsyncUnUsed) {
                Log.d(LOG_TAG, "Ignoring synchronous mode of operation request");
            }
        }
        configureCodec(format, true, signalEOSWithLastFrame, isEncoder,
                MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL);
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        mLinearInputBlock.recycle();
        mMaxInputSizeInMs = MAX_INPUT_SIZE_MS;
        super.resetContext(isAsync, signalEOSWithLastFrame);
    }

    @Override
    protected void enqueueEOS(int bufferIndex) {
        if (!mSawInputEOS) {
            MediaCodec.QueueRequest request = mCodec.getQueueRequest(bufferIndex);
            mLinearInputBlock.allocateBlock(mCodecName, 64);
            request.setLinearBlock(mLinearInputBlock.getBlock(), mLinearInputBlock.getOffset(), 0);
            request.setPresentationTimeUs(0);
            request.setFlags(MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            request.queue();
            mSawInputEOS = true;
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "Queued End of Stream");
            }
        }
    }

    @Override
    protected void enqueueInput(int bufferIndex) {
        if (mIsLoopBack && mInputBufferReadOffset >= mInputData.length) {
            mInputBufferReadOffset = 0;
        }
        if (mInputBufferReadOffset >= mInputData.length) {
            enqueueEOS(bufferIndex);
            return;
        }
        int frameSize = mActiveRawRes.mBytesPerSample * mActiveEncCfg.mChannelCount;
        int maxInputSize =
                (int) ((frameSize * mActiveEncCfg.mSampleRate) * mMaxInputSizeInMs / 1000);
        maxInputSize = ((maxInputSize + (frameSize - 1)) / frameSize) * frameSize;

        int flags = 0;
        long pts = mInputOffsetPts;
        pts += mNumBytesSubmitted * 1000000L / ((long) mActiveRawRes.mBytesPerSample
                * mActiveEncCfg.mChannelCount * mActiveEncCfg.mSampleRate);
        int size = Math.min(maxInputSize, mInputData.length - mInputBufferReadOffset);
        assertEquals("Input size selected for queuing equates to partial audio sample \n"
                        + mTestConfig + mTestEnv, 0,
                size % ((long) mActiveRawRes.mBytesPerSample * mActiveEncCfg.mChannelCount));
        mLinearInputBlock.allocateBlock(mCodecName, size);
        mLinearInputBlock.getBuffer().put(mInputData, mInputBufferReadOffset, size);
        if (mSignalEOSWithLastFrame) {
            if (mIsLoopBack ? (mInputCount + 1 >= mLoopBackFrameLimit) :
                    (mInputBufferReadOffset + size >= mInputData.length)) {
                flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mSawInputEOS = true;
            }
        }
        mInputBufferReadOffset += size;
        mNumBytesSubmitted += size;
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + size + " pts: " + pts
                    + " flags: " + flags);
        }
        MediaCodec.QueueRequest request = mCodec.getQueueRequest(bufferIndex);
        request.setLinearBlock(mLinearInputBlock.getBlock(), mLinearInputBlock.getOffset(), size);
        request.setPresentationTimeUs(pts);
        request.setFlags(flags);
        request.queue();
        mLinearInputBlock.setOffset(mLinearInputBlock.getOffset() + size);
        mOutputBuff.saveInPTS(pts);
        mInputCount++;
    }

    @Override
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        MediaCodec.OutputFrame frame = mCodec.getOutputFrame(bufferIndex);
        long framePts = frame.getPresentationTimeUs();
        long infoPts = info.presentationTimeUs;
        int frameFlags = frame.getFlags();
        int infoFlags = info.flags;
        assertEquals("presentation timestamps from OutputFrame does not match with the value "
                + "obtained from callback: framePts=" + framePts + ", infoPts=" + infoPts + "\n"
                + mTestConfig + mTestEnv, framePts, infoPts);
        assertEquals("Flags from OutputFrame does not match with the value obtained from "
                + "callback: frameFlags=" + frameFlags + ", infoFlags=" + infoFlags + "\n"
                + mTestConfig + mTestEnv, frameFlags, infoFlags);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: "
                    + info.size + " timestamp: " + info.presentationTimeUs);
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (info.size > 0) {
            if (frame.getLinearBlock() != null) {
                ByteBuffer buf = frame.getLinearBlock().map();
                if (mSaveToMem) {
                    MediaCodec.BufferInfo copy = new MediaCodec.BufferInfo();
                    copy.set(mOutputBuff.getOutStreamSize(), info.size, info.presentationTimeUs,
                            info.flags);
                    mInfoList.add(copy);

                    mOutputBuff.checksum(buf, info);
                    mOutputBuff.saveToMemory(buf, info);
                }
                if (mMuxer != null) {
                    if (mTrackID == -1) {
                        mTrackID = mMuxer.addTrack(mCodec.getOutputFormat());
                        mMuxer.start();
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        mMuxer.writeSampleData(mTrackID, buf, info);
                    }
                }
                frame.getLinearBlock().recycle();
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                mOutputBuff.saveOutPTS(info.presentationTimeUs);
                mOutputCount++;
            }
        }
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }
}
