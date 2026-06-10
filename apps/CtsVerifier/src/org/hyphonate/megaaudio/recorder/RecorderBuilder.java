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
package org.hyphonate.megaaudio.recorder;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamState;

/**
 * Class to construct contrete Recorder objects.
 */
public class RecorderBuilder extends BuilderBase {
    @SuppressWarnings("unused")
    private static final String TAG = RecorderBuilder.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = false;

    /**
     * Consumes recorded audio.
     */
    private AudioSinkProvider mSinkProvider;

    /**
     * Specified the input preset for the constructed stream.
     */
    private int mInputPreset = Recorder.INPUT_PRESET_NONE;

    public RecorderBuilder() {
    }

    //
    // Recorder-Specific Attributes
    //
    /**
     * Specifies the recorder type
     * @param type Composed from API Types & API subtypes (defined in BuilderBase)
     * @return this RecorderBuilder (for cascaded calls)
     */
    public RecorderBuilder setRecorderType(int type) {
        mType = type;
        return this;
    }

    /**
     * Specifies the AudioSinkProvider which will allocate an AudioSink subclass object
     * to consume audio data for this stream.
     * @param sinkProvider Allocates the AudioSink to receive data from the created stream.
     * @return this RecorderBuilder (for cascaded calls)
     */
    public RecorderBuilder setAudioSinkProvider(AudioSinkProvider sinkProvider) {
        mSinkProvider = sinkProvider;
        return this;
    }

    /**
     *
     * @param inputPreset The input preset for the created stream. See Recorder.INPUT_PRESET_
     * constants.
     * @return this RecorderBuilder (for cascaded calls)
     */
    public RecorderBuilder setInputPreset(int inputPreset) {
        mInputPreset = inputPreset;
        return this;
    }

    /**
     * @return the input preset ID for the created Recorder.
     */
    public int getInputPreset() {
        return mInputPreset;
    }

    /**
     * Allocates and initializes an API-specific recorder stream.
     * @return The allocated player or null in case of error or if a recorder type of TYPE_NONE
     * is specified.
     * @throws BadStateException if an invalid API has been specified.
     */
    public Recorder allocStream() throws BadStateException {
        if (mSinkProvider == null) {
            throw new BadStateException("No SinkProvider Specified.", StreamState.UNINITIALIZED);
        }

        Recorder recorder = null;
        int recorderType = mType & TYPE_MASK;
        switch (recorderType) {
            case TYPE_NONE:
                // NOP
                break;

            case TYPE_JAVA:
                recorder = new JavaRecorder(mSinkProvider);
                break;

            case TYPE_OBOE: {
                int recorderSubType = mType & SUB_TYPE_MASK;
                recorder = new OboeRecorder(mSinkProvider, recorderSubType);
            }
            break;

            default:
                throw new BuilderBase.BadStateException(
                        "Invalid Audio API Specified [" + recorderType + "]",
                        StreamState.UNKNOWN);
        }

        return recorder;
    }

}
