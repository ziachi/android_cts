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

package android.mediav2.common.cts;

import static android.mediav2.common.cts.CodecTestBase.getHeight;
import static android.mediav2.common.cts.CodecTestBase.getWidth;

import static org.junit.Assert.assertTrue;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.PersistableBundle;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Helper class for running mediacodec in asynchronous mode. All mediacodec callback events are
 * registered in this object so that the client can take appropriate action in time.
 * <p>
 * TODO(b/262696149): Calls to getInput(), getOutput(), getWork() return if there is a valid input
 * or output buffer available for client or when the codec is in error state. Have to add support
 * to return from these calls after a timeout. Currently the wait is indefinite.
 */
public class CodecAsyncHandler extends MediaCodec.Callback {
    private static final String LOG_TAG = CodecAsyncHandler.class.getSimpleName();
    protected final Lock mLock = new ReentrantLock();
    protected final Condition mCondition = mLock.newCondition();
    protected final LinkedList<Pair<Integer, MediaCodec.BufferInfo>> mCbInputQueue;
    private final LinkedList<Pair<Integer, MediaCodec.BufferInfo>> mCbOutputQueue;
    private MediaFormat mOutFormat;
    private boolean mSignalledOutFormatChangedSubSession;
    protected volatile boolean mSignalledError;
    private String mErrorMsg;
    private int mMinExpectedMetricsFlushCount;
    private int mActualMetricsFlushCount;

    public CodecAsyncHandler() {
        mCbInputQueue = new LinkedList<>();
        mCbOutputQueue = new LinkedList<>();
        mOutFormat = null;
        mSignalledError = false;
        mSignalledOutFormatChangedSubSession = false;
        mErrorMsg = "";
        mMinExpectedMetricsFlushCount = 0;
        mActualMetricsFlushCount = 0;
    }

    public void clearQueues() {
        mLock.lock();
        try {
            mCbInputQueue.clear();
            mCbOutputQueue.clear();
        } finally {
            mLock.unlock();
        }
    }

    public void resetContext() {
        clearQueues();
        mOutFormat = null;
        mSignalledOutFormatChangedSubSession = false;
        mErrorMsg = "";
        mSignalledError = false;
        mMinExpectedMetricsFlushCount = 0;
        mActualMetricsFlushCount = 0;
    }

    @Override
    public void onInputBufferAvailable(@NonNull MediaCodec codec, int bufferIndex) {
        assertTrue(bufferIndex >= 0);
        mLock.lock();
        try {
            mCbInputQueue.add(new Pair<>(bufferIndex, null));
            mCondition.signalAll();
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void onOutputBufferAvailable(@NonNull MediaCodec codec, int bufferIndex,
            @NonNull MediaCodec.BufferInfo info) {
        assertTrue(bufferIndex >= 0);
        mLock.lock();
        try {
            mCbOutputQueue.add(new Pair<>(bufferIndex, info));
            mCondition.signalAll();
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void onError(@NonNull MediaCodec codec, MediaCodec.CodecException e) {
        mErrorMsg = "###################  Async Error Details  #####################\n";
        mErrorMsg += e.toString() + "\n";
        mLock.lock();
        try {
            mSignalledError = true;
            mCondition.signalAll();
        } finally {
            mLock.unlock();
        }
        Log.e(LOG_TAG, "received media codec error : " + e.getMessage());
    }

    @Override
    public void onCryptoError(@NonNull MediaCodec codec, @NonNull MediaCodec.CryptoException e) {
        mErrorMsg = "###################  Crypto Error Details  #####################\n";
        mErrorMsg += e.getMessage() + "\n";
        mLock.lock();
        try {
            mSignalledError = true;
            mCondition.signalAll();
        } finally {
            mLock.unlock();
        }
        Log.e(LOG_TAG, "received media codec crypto error : " + e.getMessage());
    }

    @Override
    public void onMetricsFlushed(@NonNull MediaCodec codec, @NonNull PersistableBundle metrics) {
        mLock.lock();
        try {
            mActualMetricsFlushCount++;
            mCondition.signalAll();
        } finally {
            mLock.unlock();
        }
        Log.i(LOG_TAG, "final metrics for the previous subsession: " + metrics);
    }

    @Override
    public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
        mLock.lock();
        try {
            if (mOutFormat != null
                    && mOutFormat.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                if (getWidth(mOutFormat) != getWidth(format)
                        || getHeight(mOutFormat) != getHeight(format)) {
                    mMinExpectedMetricsFlushCount++;
                }
            }
            mOutFormat = format;
            mSignalledOutFormatChangedSubSession = true;
            mCondition.signalAll();
        } finally {
            mLock.unlock();
        }
        Log.i(LOG_TAG, "Output format changed: " + format);
    }

    public void setCallBack(MediaCodec codec, boolean isCodecInAsyncMode) {
        if (isCodecInAsyncMode) {
            codec.setCallback(this);
        } else {
            codec.setCallback(null);
        }
    }

    public void waitOnFormatChange() throws InterruptedException {
        int retry = CodecTestBase.RETRY_LIMIT;
        mLock.lock();
        try {
            while (!mSignalledError) {
                if (mSignalledOutFormatChangedSubSession || retry == 0) break;
                if (!mCondition.await(CodecTestBase.Q_DEQ_TIMEOUT_US, TimeUnit.MICROSECONDS)) {
                    retry--;
                }
            }
        } finally {
            mLock.unlock();
        }
        if (!mSignalledError) {
            assertTrue(
                    "taking too long to receive onOutputFormatChanged callback",
                    mSignalledOutFormatChangedSubSession);
        }
    }

    public Pair<Integer, MediaCodec.BufferInfo> getInput() throws InterruptedException {
        Pair<Integer, MediaCodec.BufferInfo> element = null;
        mLock.lock();
        try {
            while (!mSignalledError) {
                if (mCbInputQueue.isEmpty()) {
                    mCondition.await();
                } else {
                    element = mCbInputQueue.remove(0);
                    break;
                }
            }
        } finally {
            mLock.unlock();
        }
        return element;
    }

    public Pair<Integer, MediaCodec.BufferInfo> getOutput() throws InterruptedException {
        Pair<Integer, MediaCodec.BufferInfo> element = null;
        mLock.lock();
        try {
            while (!mSignalledError) {
                if (mCbOutputQueue.isEmpty()) {
                    mCondition.await();
                } else {
                    element = mCbOutputQueue.remove(0);
                    break;
                }
            }
        } finally {
            mLock.unlock();
        }
        return element;
    }

    public Pair<Integer, MediaCodec.BufferInfo> getWork() throws InterruptedException {
        Pair<Integer, MediaCodec.BufferInfo> element = null;
        mLock.lock();
        try {
            while (!mSignalledError) {
                if (mCbInputQueue.isEmpty() && mCbOutputQueue.isEmpty()) {
                    mCondition.await();
                } else {
                    if (!mCbOutputQueue.isEmpty()) {
                        element = mCbOutputQueue.remove(0);
                    } else {
                        element = mCbInputQueue.remove(0);
                    }
                    break;
                }
            }
        } finally {
            mLock.unlock();
        }
        return element;
    }

    public boolean isInputQueueEmpty() {
        boolean isEmpty = true;
        mLock.lock();
        try {
            isEmpty = mCbInputQueue.isEmpty();
        } finally {
            mLock.unlock();
        }
        return isEmpty;
    }

    public boolean hasSeenError() {
        return mSignalledError;
    }

    public String getErrMsg() {
        return mErrorMsg;
    }

    public boolean hasOutputFormatChanged() {
        return mSignalledOutFormatChangedSubSession;
    }

    public int getMinExpectedMetricsFlushCount() {
        return mMinExpectedMetricsFlushCount;
    }

    public int getActualMetricsFlushCount() {
        return mActualMetricsFlushCount;
    }

    public MediaFormat getOutputFormat() {
        return mOutFormat;
    }
}
