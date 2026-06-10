/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import android.os.Bundle;
import android.util.Log;

import com.android.cts.verifier.audio.audiolib.DisplayUtils;

// MegaAudio imports
import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.JavaPlayer;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.player.sources.SinAudioSourceProvider;

public abstract class USBAudioPeripheralPlayerActivity extends USBAudioPeripheralActivity {
    private static final String TAG = "USBAudioPeripheralPlayerActivity";
    private static final boolean LOG = true;

    // MegaPlayer
    static final int NUM_CHANNELS = 2;
    protected int mSampleRate;
    protected int mNumExchangeFrames;

    JavaPlayer mAudioPlayer;

    protected boolean mIsPlaying = false;

    protected boolean mOverridePlayFlag = true;

    AudioSourceProvider mSourceProvider = new SinAudioSourceProvider();

    public USBAudioPeripheralPlayerActivity(boolean requiresMandatePeripheral) {
        super(requiresMandatePeripheral); // Mandated peripheral is NOT required
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // MegaAudio Initialization
        StreamBase.setup(this);

        mSampleRate = StreamBase.getSystemSampleRate();
        mNumExchangeFrames = StreamBase.getNumBurstFrames(BuilderBase.TYPE_JAVA);

        DisplayUtils.setKeepScreenOn(this, true);
    }

    protected boolean startPlay() {
        boolean result = false;
        int buildResult = StreamBase.ERROR_UNKNOWN;
        int openResult = StreamBase.ERROR_UNKNOWN;
        int startResult = StreamBase.ERROR_UNKNOWN;

        if (LOG) {
            Log.d(TAG, "startPlay()");
        }
        if (mOutputDevInfo != null && !mIsPlaying) {
            try {
                PlayerBuilder builder = new PlayerBuilder();
                builder.setPlayerType(PlayerBuilder.TYPE_JAVA)
                        .setSourceProvider(mSourceProvider)
                        .setChannelCount(NUM_CHANNELS)
                        .setSampleRate(mSampleRate)
                        .setNumExchangeFrames(mNumExchangeFrames);
                mAudioPlayer = (JavaPlayer) builder.allocStream();
                if ((buildResult = mAudioPlayer.build(builder)) == StreamBase.OK
                        && (openResult = mAudioPlayer.open()) == StreamBase.OK
                        && (startResult = mAudioPlayer.start()) == StreamBase.OK) {
                    mIsPlaying = true;
                } else {
                    if (LOG) {
                        if (buildResult != StreamBase.OK) {
                            Log.e(TAG, "  buildResult:" + buildResult);
                        } else if (openResult != StreamBase.OK) {
                            Log.e(TAG, "  openResult:" + openResult);
                        } else if (startResult != StreamBase.OK) {
                            Log.e(TAG, "  startResult:" + startResult);
                        }
                    }
                    mAudioPlayer.unwind();
                    mAudioPlayer = null;
                }
            } catch (BuilderBase.BadStateException ex) {
                Log.e(TAG, "Failed MegaPlayer setup ex:", ex);
                mIsPlaying = false;
                mAudioPlayer = null;
            }
        }
        return mIsPlaying;
    }

    protected void stopPlay() {
        if (mIsPlaying) {
            mAudioPlayer.unwind();
            mIsPlaying = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopPlay();
    }
}
