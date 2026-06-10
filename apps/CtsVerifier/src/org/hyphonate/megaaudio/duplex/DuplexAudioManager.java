/*
 * Copyright 2020 The Android Open Source Project
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
package org.hyphonate.megaaudio.duplex;

import android.media.AudioDeviceInfo;
import android.util.Log;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.player.AudioSource;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.Player;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.Recorder;
import org.hyphonate.megaaudio.recorder.RecorderBuilder;

public class DuplexAudioManager {
    @SuppressWarnings("unused")
    private static final String TAG = DuplexAudioManager.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = true;

    // Player
    //TODO - explain these constants
    private int mNumPlayerChannels = 2;
    private int mPlayerChannelMask = 0;

    private int mPlayerSampleRate = 48000;
    private int mNumPlayerBurstFrames;

    // see Performance Mode Constants in BuilderBase.java
    private int mPlayerPerformanceMode = BuilderBase.PERFORMANCE_MODE_LOWLATENCY;
    private int mRecorderPerformanceMode = BuilderBase.PERFORMANCE_MODE_LOWLATENCY;

    private Player mPlayer;
    private AudioSourceProvider mSourceProvider;
    private AudioDeviceInfo mPlayerSelectedDevice;

    // Recorder
    private int mNumRecorderChannels = 2;
    private int mRecorderSampleRate = 48000;
    private int mNumRecorderBufferFrames;

    private Recorder mRecorder;
    private AudioSinkProvider mSinkProvider;
    private AudioDeviceInfo mRecorderSelectedDevice;
    private int mInputPreset = Recorder.INPUT_PRESET_NONE;

    private int mPlayerSharingMode = BuilderBase.SHARING_MODE_SHARED;
    private int mRecorderSharingMode = BuilderBase.SHARING_MODE_SHARED;

    public DuplexAudioManager(AudioSourceProvider sourceProvider, AudioSinkProvider sinkProvider) {
        setSources(sourceProvider, sinkProvider);
    }

    /**
     * Specify the source providers for the source and sink.
     * @param sourceProvider The AudioSourceProvider for the output stream
     * @param sinkProvider The AudioSinkProvider for the input stream.
     */
    public void setSources(AudioSourceProvider sourceProvider, AudioSinkProvider sinkProvider) {
        mSourceProvider = sourceProvider;
        mSinkProvider = sinkProvider;

        mPlayerSampleRate =  StreamBase.getSystemSampleRate();
        mRecorderSampleRate = StreamBase.getSystemSampleRate();
    }

    //
    // Be careful using these, they will change after setupStreams is called.
    //
    public Player getPlayer() {
        return mPlayer;
    }
    public Recorder getRecorder() {
        return mRecorder;
    }

    public void setPlayerSampleRate(int sampleRate) {
        mPlayerSampleRate = sampleRate;
    }

    public void setRecordererSampleRate(int sampleRate) {
        mPlayerSampleRate = sampleRate;
    }

    public void setPlayerRouteDevice(AudioDeviceInfo deviceInfo) {
        mPlayerSelectedDevice = deviceInfo;
    }

    public void setRecorderRouteDevice(AudioDeviceInfo deviceInfo) {
        mRecorderSelectedDevice = deviceInfo;
    }

    /**
     * Specifies the number of player (index) channels.
     * @param numChannels The number of index channels for the player.
     */
    public void setNumPlayerChannels(int numChannels) {
        mNumPlayerChannels = numChannels;
        mPlayerChannelMask = 0;
    }

    /**
     * Specifies the positional-mask for the player.
     * @param mask - An AudioFormat position mask.
     */
    public void setPlayerChannelMask(int mask) {
        mPlayerChannelMask = mask;
        mNumPlayerChannels = 0;
    }

    public void setNumRecorderChannels(int numChannels) {
        mNumRecorderChannels = numChannels;
    }
    public void setRecorderSampleRate(int sampleRate) {
        mRecorderSampleRate = sampleRate;
    }

    public void setPlayerSharingMode(int mode) {
        mPlayerSharingMode = mode;
    }

    public void setRecorderSharingMode(int mode) {
        mRecorderSharingMode = mode;
    }

    public int getPlayerChannelCount() {
        return mPlayer != null ? mPlayer.getChannelCount() : -1;
    }

    public int getRecorderChannelCount() {
        return mRecorder != null ? mRecorder.getChannelCount() : -1;
    }

    /**
     * Specifies the Performance Mode.
     */
    public void setPlayerPerformanceMode(int performanceMode) {
        mPlayerPerformanceMode = performanceMode;
    }

    public int getPlayerPerformanceMode() {
        return mPlayerPerformanceMode;
    }

    /**
     * Specifies the Performance Mode.
     */
    public void setRecorderPerformanceMode(int performanceMode) {
        mRecorderPerformanceMode = performanceMode;
    }

    public int getRecorderPerformanceMode() {
        return mRecorderPerformanceMode;
    }

    /**
     * Specifies the input preset to use for the recorder.
     * @param preset
     */
    public void setInputPreset(int preset) {
        mInputPreset = preset;
    }

    // Which component of the duplex has the error
    public static final int DUPLEX_STREAM_ID    = 0x00030000;
    public static final int DUPLEX_RECORDER     = 0x00010000;
    public static final int DUPLEX_PLAYER       = 0x00020000;

    // Which part of the process has the error
    public static final int DUPLEX_ERROR_CODE   = 0xFFFC0000;
    public static final int DUPLEX_ERROR_NONE   = 0x00000000;
    public static final int DUPLEX_ERR_BUILD    = 0x00040000;
    public static final int DUPLEX_ERR_OPEN     = 0x00080000;
    public static final int DUPLEX_ERR_START    = 0x00100000;

    // The MegaAudio error (success) code
    public static final int DUPLEX_MEGAAUDIO_CODE = 0x0000FFFF;

    // Handy status code for success building/opening both paths
    public static final int DUPLEX_SUCCESS = DUPLEX_RECORDER | DUPLEX_PLAYER | StreamBase.OK;

    /**
     * Initializes (but does not start) the player and recorder streams.
     * @param playerType    The API constant for the player
     * @param recorderType  The API constant for the recorder
     * @return A set of the above constants indicating success/failure, for which stream,
     *          what part of the process and the MegaAudio error code in the bottom 16-bits
     */
    public int buildStreams(int playerType, int recorderType) {
        if (LOG) {
            Log.d(TAG, "buildStreams()");
        }
        // Recorder
        if ((recorderType & BuilderBase.TYPE_MASK) != BuilderBase.TYPE_NONE) {
            int buildResult = StreamBase.ERROR_UNKNOWN;
            int openResult = StreamBase.ERROR_UNKNOWN;
            try {
                Log.d(TAG, "  Recorder...");
                mNumRecorderBufferFrames = StreamBase.getNumBurstFrames(BuilderBase.TYPE_NONE);
                RecorderBuilder builder = (RecorderBuilder) new RecorderBuilder()
                        .setRecorderType(recorderType)
                        .setAudioSinkProvider(mSinkProvider)
                        .setInputPreset(mInputPreset)
                        .setSharingMode(mRecorderSharingMode)
                        .setRouteDevice(mRecorderSelectedDevice)
                        .setSampleRate(mRecorderSampleRate)
                        .setChannelCount(mNumRecorderChannels)
                        .setNumExchangeFrames(mNumRecorderBufferFrames)
                        .setPerformanceMode(mRecorderPerformanceMode);
                mRecorder = builder.allocStream();
                if ((buildResult = mRecorder.build(builder)) == StreamBase.OK
                        && (openResult = mRecorder.open()) == StreamBase.OK) {
                    Log.d(TAG, "  Recorder - Success!");
                } else {
                    Log.d(TAG, "  Recorder - buildResult:" + buildResult
                            + " openResult:" + openResult);
                    if (buildResult != StreamBase.OK) {
                        // build() failed
                        return DUPLEX_RECORDER | DUPLEX_ERR_BUILD | buildResult;
                    } else {
                        // open() failed
                        return DUPLEX_RECORDER | DUPLEX_ERR_OPEN | buildResult;
                    }
                }
            } catch (RecorderBuilder.BadStateException ex) {
                Log.e(TAG, "Recorder - BadStateException" + ex);
                return ex.getStatusCode();
            } catch (Exception ex) {
                Log.e(TAG, "Unexpected Error in Recorder Setup for DuplexAudioManager ex:" + ex);
                return StreamBase.ERROR_UNKNOWN;
            }
        }

        // Player
        if ((playerType & BuilderBase.TYPE_MASK) != BuilderBase.TYPE_NONE) {
            Log.d(TAG, "  Player...");
            try {
                int buildResult = StreamBase.ERROR_UNKNOWN;
                int openResult = StreamBase.ERROR_UNKNOWN;
                mNumPlayerBurstFrames = StreamBase.getNumBurstFrames(playerType);
                PlayerBuilder builder = (PlayerBuilder) new PlayerBuilder()
                        .setPlayerType(playerType)
                        .setSourceProvider(mSourceProvider)
                        .setSampleRate(mPlayerSampleRate)
                        .setChannelCount(mNumPlayerChannels)
                        .setSharingMode(mPlayerSharingMode)
                        .setRouteDevice(mPlayerSelectedDevice)
                        .setNumExchangeFrames(mNumPlayerBurstFrames)
                        .setPerformanceMode(mPlayerPerformanceMode);
                if (mNumPlayerChannels == 0) {
                    builder.setChannelMask(mPlayerChannelMask);
                } else {
                    builder.setChannelCount(mNumPlayerChannels);
                }
                mPlayer = builder.allocStream();
                if ((buildResult = mPlayer.build(builder)) == StreamBase.OK
                        && (openResult = mPlayer.open()) == StreamBase.OK) {
                    Log.d(TAG, "  Player - Success!");
                } else {
                    Log.d(TAG, "  Player - buildResult:" + buildResult
                            + " openResult:" + openResult);
                    if (buildResult != StreamBase.OK) {
                        // build() failed
                        return DUPLEX_PLAYER | DUPLEX_ERR_BUILD | buildResult;
                    } else {
                        // open() failed
                        return DUPLEX_PLAYER | DUPLEX_ERR_OPEN | buildResult;
                    }
                }
            } catch (PlayerBuilder.BadStateException ex) {
                Log.e(TAG, "Player - BadStateException" + ex);
                return ex.getStatusCode();
            } catch (Exception ex) {
                Log.e(TAG, "Unexpected Error in Player Setup for DuplexAudioManager ex:" + ex);
                return StreamBase.ERROR_UNKNOWN;
            }
        }

        return DUPLEX_RECORDER | DUPLEX_PLAYER | StreamBase.OK;
    }

    public int start() {
        if (LOG) {
            Log.d(TAG, "start()...");
        }

        int result = StreamBase.OK;
        if (mPlayer != null && (result = mPlayer.start()) != StreamBase.OK) {
            if (LOG) {
                Log.d(TAG, "  player fails result:" + result);
            }
            return DUPLEX_PLAYER | DUPLEX_ERR_START | result;
        }

        if (mRecorder != null && (result = mRecorder.start()) != StreamBase.OK) {
            if (LOG) {
                Log.d(TAG, "  recorder fails result:" + result);
            }

            return DUPLEX_RECORDER | DUPLEX_ERR_START | result;
        }

        if (LOG) {
            Log.d(TAG, "  result:" + result);
        }
        return DUPLEX_PLAYER | DUPLEX_RECORDER | DUPLEX_ERROR_NONE | result;
    }


    /**
     * Stops and tearsdown both streams.
     * It's not clear we can return a useful error code, so just let StreamBase.unwind()
     * do the work.
     */
    public void stop() {
        if (LOG) {
            Log.d(TAG, "stop()");
        }
        unwind();
    }

    /**
     * Unwinds both Player and Recorder (as appropriate)
     */
    public void unwind() {
        if (mPlayer != null) {
            mPlayer.unwind();
            mPlayer = null;
        }
        if (mRecorder != null) {
            mRecorder.unwind();
            mRecorder = null;
        }
    }

    public int getNumPlayerBufferFrames() {
        return mPlayer != null ? mPlayer.getSystemBurstFrames() : 0;
    }

    public int getNumRecorderBufferFrames() {
        return mRecorder != null ? mRecorder.getSystemBurstFrames() : 0;
    }

    public AudioSource getAudioSource() {
        return mPlayer != null ? mPlayer.getAudioSource() : null;
    }

    /**
     * Don't call this until the streams are started
     * @return true if both player and recorder are routed to the devices specified
     * with setRecorderRouteDevice() and setPlayerRouteDevice().
     */
    public boolean validateRouting() {
        if (mPlayerSelectedDevice == null && mRecorderSelectedDevice == null) {
            return true;
        }

        if (mPlayer == null || !mPlayer.isPlaying()
                || mRecorder == null || !mRecorder.isRecording()) {
            return false;
        }

        if (mPlayerSelectedDevice != null
                && mPlayer.getRoutedDeviceId() != mPlayerSelectedDevice.getId()) {
            return false;
        }

        if (mRecorderSelectedDevice != null
                && mRecorder.getRoutedDeviceId() != mRecorderSelectedDevice.getId()) {
            return false;
        }

        // Everything checks out OK.
        return true;
    }

    /**
     * Don't call this until the streams are started
     * @return true if the player is using the specified sharing mode set with
     * setPlayerSharingMode().
     */
    public boolean isSpecifiedPlayerSharingMode() {
        boolean playerOK = false;
        if (mPlayer != null) {
            int sharingMode = mPlayer.getSharingMode();
            playerOK = sharingMode == mPlayerSharingMode
                    || sharingMode == BuilderBase.SHARING_MODE_NOTSUPPORTED;
        }
        return playerOK;
    }

    /**
     * Don't call this until the streams are started
     * @return true if the recorder is using the specified sharing mode set with
     * setRecorderSharingMode().
     */
    public boolean isSpecifiedRecorderSharingMode() {
        boolean recorderOK = false;
        if (mRecorder != null) {
            int sharingMode = mRecorder.getSharingMode();
            recorderOK = sharingMode == mRecorderSharingMode
                    || sharingMode == BuilderBase.SHARING_MODE_NOTSUPPORTED;
        }
        return recorderOK;
    }

    /**
     * Don't call this until the streams are started
     * @return true if the player is using MMAP.
     */
    public boolean isPlayerStreamMMap() {
        return mPlayer.isMMap();
    }

    /**
     * Don't call this until the streams are started
     * @return true if the recorders is using MMAP.
     */
    public boolean isRecorderStreamMMap() {
        return mRecorder.isMMap();
    }
}
