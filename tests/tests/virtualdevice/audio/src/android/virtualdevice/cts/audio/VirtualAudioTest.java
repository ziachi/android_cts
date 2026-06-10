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
package android.virtualdevice.cts.audio;

import static android.Manifest.permission.CAPTURE_AUDIO_OUTPUT;
import static android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS;
import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.CHANNEL_OUT_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.AudioRecord.RECORDSTATE_RECORDING;
import static android.media.AudioRecord.RECORDSTATE_STOPPED;
import static android.media.AudioTrack.PLAYSTATE_PLAYING;
import static android.media.AudioTrack.PLAYSTATE_STOPPED;
import static android.media.AudioTrack.WRITE_BLOCKING;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.audio.AudioCapture;
import android.companion.virtual.audio.AudioInjection;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.companion.virtual.audio.VirtualAudioDevice.AudioConfigurationChangeCallback;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioMixingRule;
import android.media.audiopolicy.AudioPolicy;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.FeatureUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Set;

/**
 * Tests for injection and capturing of audio from streamed apps
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualAudioTest {

    public static final int FREQUENCY = 264;
    public static final int SAMPLE_RATE = 44100;
    private static final Duration TIMEOUT = Duration.ofMillis(5000);
    private static final int AUDIO_PERMISSIONS_PROPAGATION_TIME_MS = 80;

    private static final AudioFormat CAPTURE_FORMAT = new AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(CHANNEL_IN_MONO)
            .build();
    private static final AudioFormat INJECTION_FORMAT = new AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(CHANNEL_IN_MONO)
            .build();

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.withAdditionalPermissions(
            MODIFY_AUDIO_ROUTING, CAPTURE_AUDIO_OUTPUT, GRANT_RUNTIME_PERMISSIONS);

    private VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;
    private VirtualAudioDevice mVirtualAudioDevice;

    @Mock
    private AudioConfigurationChangeCallback mAudioConfigurationChangeCallback;
    @Mock
    private SignalObserver.SignalChangeListener mSignalChangeListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        VirtualDeviceParams params = new VirtualDeviceParams.Builder().setDevicePolicy(
                VirtualDeviceParams.POLICY_TYPE_AUDIO,
                VirtualDeviceParams.DEVICE_POLICY_CUSTOM).build();

        mVirtualDevice = mVirtualDeviceRule.createManagedVirtualDevice(params);
        mVirtualDisplay = mVirtualDeviceRule.createManagedVirtualDisplay(
                mVirtualDevice, VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, Runnable::run, mAudioConfigurationChangeCallback);
        grantRecordAudioPermission(mVirtualDevice.getDeviceId());

        // TODO - b/383048413 - use PermissionUpdateBarrierRule
        // Account for the intentional delay until the audio permissions are propagated
        SystemClock.sleep(AUDIO_PERMISSIONS_PROPAGATION_TIME_MS);
    }

    @Test
    @RequiresFlagsEnabled({
            android.media.audiopolicy.Flags.FLAG_AUDIO_MIX_TEST_API,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION})
    public void virtualDevice_hasAudioInput_withoutMicrophoneAndCustomPolicy() {
        // mVirtualDevice is created with CUSTOM policy
        android.companion.virtual.VirtualDevice virtualDevice = mVirtualDeviceRule.getVirtualDevice(
                mVirtualDevice.getDeviceId());

        assertThat(virtualDevice.hasCustomAudioInputSupport()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled({
            android.media.audiopolicy.Flags.FLAG_AUDIO_MIX_TEST_API,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION})
    public void virtualDevice_hasAudioInput_withMicrophone_isTrue() {
        mVirtualAudioDevice.startAudioInjection(INJECTION_FORMAT);

        // Start an Activity on the display to trigger the VirtualAudioDevice to register policies.
        startAudioActivity();

        android.companion.virtual.VirtualDevice virtualDevice = mVirtualDeviceRule.getVirtualDevice(
                mVirtualDevice.getDeviceId());

        assertThat(virtualDevice.hasCustomAudioInputSupport()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled({
            android.media.audiopolicy.Flags.FLAG_AUDIO_MIX_TEST_API,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION})
    public void multipleVirtualDevices_hasAudioInput_microphoneCapabilitiesOrCustomPolicy() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder().setDevicePolicy(
                VirtualDeviceParams.POLICY_TYPE_AUDIO,
                VirtualDeviceParams.DEVICE_POLICY_CUSTOM).build();

        VirtualDevice secondDevice = mVirtualDeviceRule.createManagedVirtualDevice(params);
        VirtualDisplay secondDisplay = mVirtualDeviceRule.createManagedVirtualDisplay(secondDevice,
                VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
        VirtualAudioDevice virtualAudioDevice = secondDevice.createVirtualAudioDevice(secondDisplay,
                Runnable::run, mAudioConfigurationChangeCallback);
        virtualAudioDevice.startAudioInjection(INJECTION_FORMAT);

        // First device does not have a microphone policy registered but CUSTOM audio device policy.
        mVirtualDeviceRule.startActivityOnDisplaySync(mVirtualDisplay, AudioActivity.class);
        android.companion.virtual.VirtualDevice deviceOne = mVirtualDeviceRule.getVirtualDevice(
                mVirtualDevice.getDeviceId());
        assertThat(deviceOne.hasCustomAudioInputSupport()).isTrue();

        mVirtualDeviceRule.startActivityOnDisplaySync(secondDisplay, AudioActivity.class);
        android.companion.virtual.VirtualDevice deviceTwo = mVirtualDeviceRule.getVirtualDevice(
                secondDevice.getDeviceId());
        assertThat(deviceTwo.hasCustomAudioInputSupport()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled({
            android.media.audiopolicy.Flags.FLAG_AUDIO_MIX_TEST_API,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION})
    public void virtualDevice_hasAudioInput_withDefaultAudioPolicy_manualAudioPolicy() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder().setDevicePolicy(
                VirtualDeviceParams.POLICY_TYPE_AUDIO,
                VirtualDeviceParams.DEVICE_POLICY_DEFAULT).build();

        VirtualDevice secondDevice = mVirtualDeviceRule.createManagedVirtualDevice(params);

        Context deviceContext = getInstrumentation().getTargetContext()
                .createDeviceContext(secondDevice.getDeviceId());

        AudioManager audioManager = deviceContext.getSystemService(AudioManager.class);
        assumeNotNull(audioManager);
        AudioMixingRule mixingRule = new AudioMixingRule.Builder()
                .setTargetMixRole(AudioMixingRule.MIX_ROLE_INJECTOR)
                .addMixRule(AudioMixingRule.RULE_MATCH_UID, 99999)
                .build();
        AudioMix audioMix = new android.media.audiopolicy.AudioMix.Builder(mixingRule)
                .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK)
                .setFormat(INJECTION_FORMAT)
                .build();
        AudioPolicy audioPolicy = new AudioPolicy.Builder(deviceContext).addMix(
                audioMix).build();
        try {
            int res = audioManager.registerAudioPolicy(audioPolicy);
            assertThat(res).isEqualTo(AudioManager.SUCCESS);

            android.companion.virtual.VirtualDevice deviceTwo = mVirtualDeviceRule.getVirtualDevice(
                    secondDevice.getDeviceId());
            assertThat(deviceTwo.hasCustomAudioInputSupport()).isTrue();
        } finally {
            audioManager.unregisterAudioPolicy(audioPolicy);
        }
    }

    @Test
    @RequiresFlagsEnabled({
            android.media.audiopolicy.Flags.FLAG_AUDIO_MIX_TEST_API,
            android.media.audiopolicy.Flags.FLAG_RECORD_AUDIO_DEVICE_AWARE_PERMISSION})
    public void virtualDevice_noAudioInput_withDefaultAudioPolicy_isFalse() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder().setDevicePolicy(
                VirtualDeviceParams.POLICY_TYPE_AUDIO,
                VirtualDeviceParams.DEVICE_POLICY_DEFAULT).build();

        VirtualDevice secondDevice = mVirtualDeviceRule.createManagedVirtualDevice(params);

        android.companion.virtual.VirtualDevice deviceTwo = mVirtualDeviceRule.getVirtualDevice(
                secondDevice.getDeviceId());
        assertThat(deviceTwo.hasCustomAudioInputSupport()).isFalse();
    }

    @Test
    public void audioCapture_createCorrectly() {
        AudioCapture audioCapture = mVirtualAudioDevice.startAudioCapture(CAPTURE_FORMAT);
        assertThat(audioCapture).isNotNull();
        assertThat(audioCapture.getFormat()).isEqualTo(CAPTURE_FORMAT);
        assertThat(mVirtualAudioDevice.getAudioCapture()).isEqualTo(audioCapture);

        audioCapture.startRecording();
        assertThat(audioCapture.getRecordingState()).isEqualTo(RECORDSTATE_RECORDING);
        audioCapture.stop();
        assertThat(audioCapture.getRecordingState()).isEqualTo(RECORDSTATE_STOPPED);
    }

    @Test
    public void audioInjection_createCorrectly() {
        AudioInjection audioInjection = mVirtualAudioDevice.startAudioInjection(INJECTION_FORMAT);
        assertThat(audioInjection).isNotNull();
        assertThat(audioInjection.getFormat()).isEqualTo(INJECTION_FORMAT);
        assertThat(mVirtualAudioDevice.getAudioInjection()).isEqualTo(audioInjection);

        audioInjection.play();
        assertThat(audioInjection.getPlayState()).isEqualTo(PLAYSTATE_PLAYING);
        audioInjection.stop();
        assertThat(audioInjection.getPlayState()).isEqualTo(PLAYSTATE_STOPPED);
    }

    @Test
    public void audioInjection_createWithNull() {
        assertThrows(NullPointerException.class, () -> mVirtualDevice.createVirtualAudioDevice(
                null, /* executor= */ null, /* callback= */ null));
    }

    @Test
    public void audioCapture_receivesAudioConfigurationChangeCallback() {
        AudioCapture capture = mVirtualAudioDevice.startAudioCapture(CAPTURE_FORMAT);

        AudioActivity activity = startAudioActivity();
        AudioTrack audioTrack = activity.playAudio();
        verify(mAudioConfigurationChangeCallback, timeout(5000).atLeastOnce())
                .onPlaybackConfigChanged(any());

        audioTrack.stop();
        activity.finish();
        capture.stop();
    }

    @Test
    public void audioInjection_receivesAudioConfigurationChangeCallback() throws Exception {
        ByteBuffer byteBuffer = AudioInjector.createAudioData();
        try (AudioInjector injector = new AudioInjector(byteBuffer, mVirtualAudioDevice)) {
            injector.startInjection();

            AudioActivity audioActivity = startAudioActivity();
            audioActivity.recordAudio(mSignalChangeListener);

            verify(mAudioConfigurationChangeCallback, timeout(5000).atLeastOnce())
                    .onRecordingConfigChanged(any());
            audioActivity.finish();
        }
    }

    @Test
    public void audioCapture_capturesAppPlaybackFrequency() {
        // Automotive has its own audio policies that don't play well with the VDM-created ones.
        assumeFalse(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

        AudioCapture audioCapture = mVirtualAudioDevice.startAudioCapture(CAPTURE_FORMAT);

        try (SignalObserver signalObserver = new SignalObserver(audioCapture, Set.of(FREQUENCY))) {
            signalObserver.registerSignalChangeListener(mSignalChangeListener);

            AudioActivity activity = startAudioActivity();
            AudioTrack audioTrack = activity.playAudio();

            verify(mSignalChangeListener, timeout(TIMEOUT.toMillis()).atLeastOnce()).onSignalChange(
                    Set.of(FREQUENCY));

            audioTrack.stop();
            activity.finish();
            audioCapture.stop();
        }
    }


    @Test
    public void audioInjection_appShouldRecordInjectedFrequency() throws Exception {
        assumeFalse(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

        ByteBuffer byteBuffer = AudioInjector.createAudioData();
        try (AudioInjector injector = new AudioInjector(byteBuffer, mVirtualAudioDevice)) {
            injector.startInjection();

            AudioActivity audioActivity = startAudioActivity();
            audioActivity.recordAudio(mSignalChangeListener);

            verify(mSignalChangeListener, timeout(TIMEOUT.toMillis()).atLeastOnce()).onSignalChange(
                    Set.of(FREQUENCY));
        }
    }

    private AudioActivity startAudioActivity() {
        return mVirtualDeviceRule.startActivityOnDisplaySync(
                mVirtualDisplay, AudioActivity.class);
    }

    private void grantRecordAudioPermission(int deviceId) {
        Context deviceContext = getInstrumentation().getTargetContext()
                .createDeviceContext(deviceId);
        deviceContext.getPackageManager().grantRuntimePermission("android.virtualdevice.cts.audio",
                RECORD_AUDIO, UserHandle.of(deviceContext.getUserId()));
    }

    public static class AudioActivity extends Activity {

        private SignalObserver mSignalObserver;

        AudioTrack playAudio() {

            ByteBuffer audioData = AudioInjector.createAudioData();
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                    CHANNEL_OUT_MONO, ENCODING_PCM_16BIT, audioData.capacity(),
                    AudioTrack.MODE_STATIC);
            audioTrack.write(audioData, audioData.capacity(), WRITE_BLOCKING);
            audioTrack.play();

            return audioTrack;
        }

        void recordAudio(SignalObserver.SignalChangeListener signalChangeListener) {
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, ENCODING_PCM_16BIT,
                    AudioInjector.BUFFER_SIZE_IN_BYTES);
            if (mSignalObserver != null) {
                mSignalObserver.close();
            }

            mSignalObserver = new SignalObserver(audioRecord, Set.of(FREQUENCY));
            mSignalObserver.registerSignalChangeListener(signalChangeListener);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();

            if (mSignalObserver != null) {
                mSignalObserver.close();
            }
        }
    }
}
