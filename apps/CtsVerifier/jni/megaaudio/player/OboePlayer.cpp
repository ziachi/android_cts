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
#include <android/log.h>

#include "OboePlayer.h"

#include "WaveTableSource.h"

#include "AudioSource.h"

static const char * const TAG = "OboePlayer(native)";
static const bool LOG = true;

using namespace oboe;

constexpr int32_t kBufferSizeInBursts = 2; // Use 2 bursts as the buffer size (double buffer)

ChannelMask OboePlayer::javaChannelMaskToOboeChannelMask(int32_t javaMask) {
    return (ChannelMask) (javaMask >> 2);
}

int32_t OboePlayer::javaChannelMaskToChannelCount(int32_t javaMask) {
    // return the count of 1 bits
    return __builtin_popcount(static_cast<uint32_t>(javaMask));
}

OboePlayer::OboePlayer(JNIEnv *env, AudioSource* source, int subtype)
 : Player(source, subtype)
{
    env->GetJavaVM(&mJvm);

    jclass clsAudioTimestamp = env->FindClass("android/media/AudioTimestamp");

    mFidFramePosition = env->GetFieldID(clsAudioTimestamp, "framePosition", "J");
    mFidNanoTime = env->GetFieldID(clsAudioTimestamp, "nanoTime", "J");
}

DataCallbackResult OboePlayer::onAudioReady(AudioStream *oboeStream, void *audioData,
                                            int32_t numFrames) {
    StreamState streamState = oboeStream->getState();
    if (streamState != StreamState::Open && streamState != StreamState::Started) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "  streamState:%d",
                static_cast<int>(streamState));
    }
    if (streamState == StreamState::Disconnected) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "  streamState::Disconnected");
    }

    // Pull the data here!
    int numFramesRead = mAudioSource->pull((float*)audioData, numFrames, mNumExchangeChannels);
    // may need to handle 0-filling if numFramesRead < numFrames

    return numFramesRead != 0 ? DataCallbackResult::Continue : DataCallbackResult::Stop;
}

void OboePlayer::onErrorAfterClose(AudioStream *oboeStream, oboe::Result error) {
}

void OboePlayer::onErrorBeforeClose(AudioStream *, oboe::Result error) {
}

StreamBase::Result OboePlayer::buildStream(int32_t channelCount, int32_t channelMask,
                    int32_t sampleRate, int32_t performanceMode, int32_t sharingMode,
                    int32_t routeDeviceId) {

    if (LOG) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s()", __FUNCTION__);
    }

    oboe::Result result = oboe::Result::ErrorInternal;
    if (mAudioStream != nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "ERROR_INVALID_STATE - Stream Already Open.");
        return ERROR_INVALID_STATE;
    } else {
        std::lock_guard<std::mutex> lock(mStreamLock);

        mChannelCount = channelCount;
        mChannelMask = channelMask;

        mSampleRate = sampleRate;
        mRouteDeviceId = routeDeviceId;

        // Create an audio stream
        if (mChannelCount != 0) {
            mBuilder.setChannelCount(mChannelCount);
            mNumExchangeChannels = mChannelCount;
        } else {
            mBuilder.setChannelMask(javaChannelMaskToOboeChannelMask(mChannelMask));
            mNumExchangeChannels = javaChannelMaskToChannelCount(mChannelMask);
        }
        mBuilder.setSampleRate(mSampleRate);
        mBuilder.setCallback(this);

        mBuilder.setSampleRateConversionQuality(SampleRateConversionQuality::None);
        mBuilder.setDirection(Direction::Output);
        switch (mSubtype) {
        case SUB_TYPE_OBOE_AAUDIO:
            mBuilder.setAudioApi(AudioApi::AAudio);
            break;

        case SUB_TYPE_OBOE_OPENSL_ES:
            mBuilder.setAudioApi(AudioApi::OpenSLES);
            break;
        }

        mBuilder.setPerformanceMode((PerformanceMode) performanceMode);
        mBuilder.setSharingMode((SharingMode) sharingMode);

        if (mRouteDeviceId != ROUTING_DEVICE_NONE) {
            mBuilder.setDeviceId(mRouteDeviceId);
        }

        result = oboe::Result::OK;
    }

    if (LOG) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s() return:%d",
                            __FUNCTION__ , OboeErrorToMegaAudioError(result));
    }

    return OboeErrorToMegaAudioError(result);
}

StreamBase::Result OboePlayer::openStream() {
    if (LOG) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s()", __FUNCTION__);
    }
    oboe::Result result = mBuilder.openStream(mAudioStream);
    if (result != oboe::Result::OK) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                TAG,
                "openStream() failed. Error: %s",convertToText(result));
    } else {
        // Reduce stream latency by setting the buffer size to a multiple of the burst size
        // Note: this will fail with ErrorUnimplemented if we are using a callback with
        //  OpenSL ES. See oboe::AudioStreamBuffered::setBufferSizeInFrames
        // This doesn't affect the success of opening the stream.
        int32_t desiredSize = mAudioStream->getFramesPerBurst() * kBufferSizeInBursts;
        mAudioStream->setBufferSizeInFrames(desiredSize);

        mAudioSource->init(desiredSize, mNumExchangeChannels);
    }

    if (LOG) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s() return:%d",
                            __FUNCTION__, OboeErrorToMegaAudioError(result));
    }
    return OboeErrorToMegaAudioError(result);
}

StreamBase::Result OboePlayer::startStream() {
    if (LOG) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s()", __FUNCTION__);
    }
    StreamBase::Result result = Player::startStream();
    if (LOG) {
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s() return:%d",
                            __FUNCTION__, result);
    }
    return result;
}

bool OboePlayer::getJavaTimestamp(jobject timestampObj) {
    oboe::FrameTimestamp nativeStamp;
    StreamBase::Result result = Player::getTimeStamp(&nativeStamp);
    if (result == OK) {
        JNIEnv* env;
        mJvm->AttachCurrentThread(&env, NULL);

        env->SetLongField(timestampObj, mFidFramePosition, nativeStamp.position);
        env->SetLongField(timestampObj, mFidNanoTime, nativeStamp.timestamp);
    }

    return result == OK;
}

int OboePlayer::getLastErrorCallbackResult() {
    return (int)(mAudioStream->getLastErrorCallbackResult());
}

//
// JNI functions
//
#include <jni.h>

extern "C" {
JNIEXPORT JNICALL jlong
Java_org_hyphonate_megaaudio_player_OboePlayer_allocNativePlayer(
    JNIEnv *env, jobject thiz, jlong native_audio_source, jint playerSubtype) {

    return (jlong)new OboePlayer(env, (AudioSource*)native_audio_source, playerSubtype);
}

JNIEXPORT jint JNICALL Java_org_hyphonate_megaaudio_player_OboePlayer_buildStreamN(
        JNIEnv *env, jobject thiz, jlong native_player,
        jint channel_count, jint channel_mask, jint sample_rate, jint performanceMode,
        jint sharingMode, jint routeDeviceId) {

    OboePlayer* player = (OboePlayer*)native_player;
    return player->buildStream(channel_count, channel_mask, sample_rate, performanceMode,
                                sharingMode, routeDeviceId);
}

JNIEXPORT jint JNICALL Java_org_hyphonate_megaaudio_player_OboePlayer_openStreamN(
        JNIEnv *env, jobject thiz, jlong native_player) {

    OboePlayer* player = (OboePlayer*)native_player;
    return player->openStream();
}

JNIEXPORT int JNICALL Java_org_hyphonate_megaaudio_player_OboePlayer_teardownStreamN(
        JNIEnv *env, jobject thiz, jlong native_player) {

    OboePlayer* player = (OboePlayer*)native_player;
    return player->teardownStream();
}

JNIEXPORT JNICALL jint Java_org_hyphonate_megaaudio_player_OboePlayer_startStreamN(
        JNIEnv *env, jobject thiz, jlong native_player, jint playerSubtype) {

    return ((OboePlayer*)(native_player))->startStream();
}

JNIEXPORT JNICALL jint
Java_org_hyphonate_megaaudio_player_OboePlayer_stopStreamN(JNIEnv *env, jobject thiz,
            jlong native_player) {

   return ((OboePlayer*)(native_player))->stopStream();
}

JNIEXPORT JNICALL jint
        Java_org_hyphonate_megaaudio_player_OboePlayer_closeStreamN(JNIEnv *env, jobject thiz,
            jlong native_player) {

    return ((OboePlayer*)(native_player))->closeStream();
}

JNIEXPORT jint JNICALL
Java_org_hyphonate_megaaudio_player_OboePlayer_getBufferFrameCountN(JNIEnv *env, jobject thiz,
            jlong native_player) {
    return ((OboePlayer*)(native_player))->getNumBufferFrames();
}

JNIEXPORT jint JNICALL Java_org_hyphonate_megaaudio_player_OboePlayer_getRoutedDeviceIdN(
            JNIEnv *env, jobject thiz, jlong native_player) {
    return ((OboePlayer*)(native_player))->getRoutedDeviceId();
}

JNIEXPORT jint JNICALL Java_org_hyphonate_megaaudio_player_OboePlayer_getSharingModeN(
            JNIEnv *env, jobject thiz, jlong native_player) {
    return ((OboePlayer*)(native_player))->getSharingMode();
}

JNIEXPORT jint JNICALL Java_org_hyphonate_megaaudio_player_OboePlayer_getChannelCountN(
            JNIEnv *env, jobject thiz, jlong native_player) {
    return ((OboePlayer*)(native_player))->getChannelCount();
}

JNIEXPORT jboolean JNICALL Java_org_hyphonate_megaaudio_player_OboePlayer_isMMapN(
            JNIEnv *env, jobject thiz, jlong native_player) {
    return ((OboePlayer*)(native_player))->isMMap();
}

JNIEXPORT jboolean JNICALL Java_org_hyphonate_megaaudio_player_OboePlayer_getTimestampN(
            JNIEnv *env, jobject thiz, jlong native_player, jobject timestamp) {
    return ((OboePlayer*)native_player)->getJavaTimestamp(timestamp);
}

JNIEXPORT jint JNICALL Java_org_hyphonate_megaaudio_player_OboePlayer_getStreamStateN(
            JNIEnv *env, jobject thiz, jlong native_player) {
    return (int)((OboePlayer*)(native_player))->getState();
}

JNIEXPORT jint JNICALL Java_org_hyphonate_megaaudio_player_OboePlayer_getLastErrorCallbackResultN(
            JNIEnv *env, jobject thiz, jlong native_player) {
    return (int)((OboePlayer*)(native_player))->getLastErrorCallbackResult();
}

} // extern "C"
