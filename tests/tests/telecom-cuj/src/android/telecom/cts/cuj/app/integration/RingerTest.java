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

package android.telecom.cts.cuj.app.integration;

import static android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE;
import static android.media.Utils.SYNCHRONIZED_VIBRATION;
import static android.media.Utils.VIBRATION_URI_PARAM;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.cts.apps.ShellCommandExecutor.executeShellCommand;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.media.AudioManager;
import android.media.AudioManager.AudioPlaybackCallback;
import android.media.AudioPlaybackConfiguration;
import android.media.RingtoneManager;
import android.media.audio.Flags;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;
import android.telecom.cts.cuj.TestUtils;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This test class should test common ringer and vibrations/haptics scenarios that involve only a
 * single application.
 */
@RunWith(JUnit4.class)
public class RingerTest extends BaseAppVerifier {
    private static final long WAIT_FOR_STATE_CHANGE_TIMEOUT_MS = 10000;
    private static final long WAIT_FOR_NO_RING_TIMEOUT_MS = 1000;
    private static final long WAIT_FOR_VIBRATOR_LOGS_UPDATE_TIMEOUT_MS = 10000;
    private static final int OFF = 0;
    private static final int ON = 1;
    private static final Pattern CURRENT_VIBRATIONS_PATTERN =
            Pattern.compile("CurrentVibration:.*?Recent vibrations:");
    private static final Pattern RINGTONE_VIBRATION_PATTERN =
            Pattern.compile("mUsage=RINGTONE");
    private static final String QUERY_VIBRATOR_MANAGER_COMMAND = "dumpsys vibrator_manager";
    private static final String TAG = "RingerTest";

    /**
     * Test the scenario where a new MANAGED incoming call is created and transitions to RINGING
     * while the ringer is in NORMAL mode and "Vibrations & haptics" are enabled.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. verify that the call rang audibly and {@link AudioPlaybackCallback} was triggered.
     * <p>
     *  3. inspect the vibrator_manager dumpsys to ensure that a current ringtone vibration logged.
     * <p>
     *  4. disconnect the call
     */
    @Ignore // TODO(b/393989489): Diagnose flakiness and re-enable.
    @Test
    public void testIncomingCall_RingAndVibrate() throws Exception {
        assumeTrue(mShouldTestTelecom);
        assumeTrue(mSupportsManagedCalls);
        assumeTrue(hasVibrator());

        // Configure the audio manager and register the audio playback callback:
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        AudioPlaybackCallback callback = createAudioPlaybackCallback(queue);
        AudioManager audioManager =
                configureAudioManager(AudioManager.RINGER_MODE_NORMAL, callback);

        // Configure the "Vibrations & haptics" settings:
        configureVibrationSettings(ON);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addIncomingCallAndVerify(managedApp);

            // Verify that the call rang:
            verifyCallIsInState(mt, STATE_RINGING);
            Boolean ringing = queue.poll(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotNull("Telecom should have played a ringtone, timed out waiting for "
                            + "state change", ringing);
            assertTrue("Telecom should have played a ringtone.", ringing);

            // Verify that the device is vibrating by inspecting the vibrator dumpsys:
            waitForRingtoneVibrationLogOrTimeout();

            // Disconnect the call:
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(managedApp);
            audioManager.unregisterAudioPlaybackCallback(callback);
        }
    }

    /**
     * Test the scenario where a new MANAGED incoming call is created and transitions to RINGING
     * while the ringer is in NORMAL mode and "Vibrations & haptics" are disabled.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. verify that the call rang audibly and {@link AudioPlaybackCallback} was triggered.
     * <p>
     *  3. inspect the vibrator_manager dumpsys to ensure that no current ringtone vibration logged.
     * <p>
     *  4. disconnect the call
     */
    @Ignore // TODO(b/393989489): Diagnose flakiness and re-enable.
    @Test
    public void testIncomingCallVibrationDisabled_RingAndNoVibrate() throws Exception {
        assumeTrue(mShouldTestTelecom);
        assumeTrue(mSupportsManagedCalls);
        assumeTrue(hasVibrator());

        // Configure the audio manager and register the audio playback callback:
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        AudioPlaybackCallback callback = createAudioPlaybackCallback(queue);
        AudioManager audioManager =
                configureAudioManager(AudioManager.RINGER_MODE_NORMAL, callback);

        // Configure the "Vibrations & haptics" settings:
        configureVibrationSettings(OFF);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addIncomingCallAndVerify(managedApp);

            // Verify that the call rang:
            verifyCallIsInState(mt, STATE_RINGING);
            Boolean ringing = queue.poll(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotNull("Telecom should have played a ringtone, timed out waiting for "
                            + "state change", ringing);
            assertTrue("Telecom should have played a ringtone.", ringing);

            // Verify that the device is not vibrating by inspecting the vibrator dumpsys:
            waitToEnsureNoRingtoneVibrationLog();

            // Disconnect the call:
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(managedApp);
            audioManager.unregisterAudioPlaybackCallback(callback);
        }
    }

    /**
     * Test the scenario where a new MANAGED incoming call is created and transitions to RINGING
     * while "Vibrations & haptics" are disabled but the ringer is in SILENT mode.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. verify that the call did not rang audibly and {@link AudioPlaybackCallback} was not
     *  triggered.
     * <p>
     *  3. inspect the vibrator_manager dumpsys to ensure that no current ringtone vibration logged.
     * <p>
     *  4. disconnect the call
     */
    @Test
    public void testIncomingCallSilentMode_NoRingAndNoVibrate() throws Exception {
        assumeTrue(mShouldTestTelecom);
        assumeTrue(mSupportsManagedCalls);
        assumeTrue(hasVibrator());

        // Configure the audio manager and register the audio playback callback:
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        AudioPlaybackCallback callback = createAudioPlaybackCallback(queue);
        AudioManager audioManager =
                configureAudioManager(AudioManager.RINGER_MODE_SILENT, callback);

        // Configure the "Vibrations & haptics" settings:
        configureVibrationSettings(ON);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addIncomingCallAndVerify(managedApp);

            // Verify that the call did not audibly ring:
            verifyCallIsInState(mt, STATE_RINGING);
            Boolean ringing = queue.poll(WAIT_FOR_NO_RING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNull("Telecom should not have played a ringtone since ringer is "
                            + "in SILENT mode", ringing);

            // Verify that the device is not vibrating by inspecting the vibrator dumpsys:
            waitToEnsureNoRingtoneVibrationLog();

            // Disconnect the call:
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(managedApp);
            audioManager.unregisterAudioPlaybackCallback(callback);
        }
    }

    /**
     * Test the scenario where a new MANAGED incoming call is created and transitions to RINGING
     * while the ringer is in VIBRATE mode and "Vibrations & haptics" are enabled.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. verify that the call did not ring audibly and that {@link AudioPlaybackCallback} was not
     *  triggered.
     * <p>
     *  3. inspect the vibrator_manager dumpsys to ensure that a current ringtone vibration logged.
     * <p>
     *  4. disconnect the call
     */
    @Ignore // TODO(b/393989489): Diagnose flakiness and re-enable.
    @Test
    public void testIncomingCallVibrateMode_VibrateAndNoRing() throws Exception {
        assumeTrue(mShouldTestTelecom);
        assumeTrue(mSupportsManagedCalls);
        assumeTrue(hasVibrator());
        assumeFalse(TestUtils.isRingtoneVibrationSupported(mContext));

        // Configure the audio manager and register the audio playback callback:
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        AudioPlaybackCallback callback = createAudioPlaybackCallback(queue);
        AudioManager audioManager =
                configureAudioManager(AudioManager.RINGER_MODE_VIBRATE, callback);

        // Configure the "Vibrations & haptics" settings:
        configureVibrationSettings(ON);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addIncomingCallAndVerify(managedApp);

            // Verify that the call did not ring:
            verifyCallIsInState(mt, STATE_RINGING);
            Boolean ringing = queue.poll(WAIT_FOR_NO_RING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNull("Telecom should not have played a ringtone since ringer is in "
                            + "VIBRATE mode", ringing);

            // Verify that the device is vibrating by inspecting the vibrator dumpsys:
            waitForRingtoneVibrationLogOrTimeout();

            // Disconnect the call:
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(managedApp);
            audioManager.unregisterAudioPlaybackCallback(callback);
        }
    }

    /**
     * Test the scenario where a new MANAGED incoming call is created and transitions to RINGING
     * while the ringer is in VIBRATE mode and "Vibrations & haptics" are enabled. While the MT
     * call is still RINGING, change the ringer mode to NORMAL mode.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. verify that the call did not ring audibly and that {@link AudioPlaybackCallback} was not
     *  triggered.
     * <p>
     *  3. inspect the vibrator_manager dumpsys to ensure that a current ringtone vibration logged.
     * <p>
     *  4. while the MT call is still RINGING, change the ringer mode to NORMAL mode.
     * <p>
     *  5. verify that the call is now ringing audibly, {@link AudioPlaybackCallback} was
     *  triggered, and a current ringtone vibration is still logged in the dumpsys.
     *  <p>
     *  6. disconnect the call
     */
    @Ignore // TODO(b/393989489): Diagnose flakiness and re-enable.
    @Test
    public void testIncomingCallVibrateModeEnableRinger_VibrateAndRing() throws Exception {
        assumeTrue(mShouldTestTelecom);
        assumeTrue(mSupportsManagedCalls);
        assumeTrue(hasVibrator());

        // Configure the audio manager and register the audio playback callback:
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        AudioPlaybackCallback callback = createAudioPlaybackCallback(queue);
        AudioManager audioManager =
                configureAudioManager(AudioManager.RINGER_MODE_VIBRATE, callback);

        // Configure the "Vibrations & haptics" settings:
        configureVibrationSettings(ON);

        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addIncomingCallAndVerify(managedApp);

            // Verify that the call is not ringing audibly:
            verifyCallIsInState(mt, STATE_RINGING);
            Boolean originalRingingState =
                    queue.poll(WAIT_FOR_NO_RING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!TestUtils.isRingtoneVibrationSupported(mContext)) {
                assertNull("Telecom should not have played a ringtone since ringer is in "
                        + "VIBRATE mode", originalRingingState);
            }
            // Verify that the device is vibrating by inspecting the vibrator dumpsys:
            waitForRingtoneVibrationLogOrTimeout();

            // While the MT call is still ringing, change the ringer mode to “normal” mode:
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(audioManager,
                    am -> am.setRingerMode(AudioManager.RINGER_MODE_NORMAL));

            // Verify that the call is now audibly ringing:
            Boolean updatedRingingState =
                    queue.poll(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotNull("Telecom should have played a ringtone, timed out waiting for "
                    + "state change", updatedRingingState);
            assertTrue("Telecom should have played a ringtone.", updatedRingingState);

            // Verify that the device is still vibrating by inspecting the vibrator dumpsys:
            waitForRingtoneVibrationLogOrTimeout();

            // Disconnect the call:
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(managedApp);
            audioManager.unregisterAudioPlaybackCallback(callback);
        }
    }

    /**
     * Test the scenario where a new MANAGED incoming call is created while the ringer is in
     * VIBRATE mode and "Vibrations & haptics" are enabled if device supports ringtone vibration
     * settings.
     * <p>
     *
     * <h3> Test Steps: </h3>
     *  1. create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *  via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     * <p>
     *  2. verify that the silent call rang with vibration and that {@link AudioPlaybackCallback}
     *  was triggered.
     * <p>
     *  3. disconnect the call
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_RINGTONE_HAPTICS_CUSTOMIZATION)
    public void testIncomingCallVibrateMode_vibrationSettingsSupported_VibrateAndRing()
            throws Exception {
        Uri defaultRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(mContext,
                RingtoneManager.TYPE_RINGTONE);
        assumeNotNull(defaultRingtoneUri);
        Uri testRingtoneUri = defaultRingtoneUri.buildUpon().appendQueryParameter(
                VIBRATION_URI_PARAM, SYNCHRONIZED_VIBRATION).build();

        assumeTrue(mShouldTestTelecom);
        assumeTrue(mSupportsManagedCalls);
        assumeTrue(hasVibrator());
        assumeTrue(TestUtils.isRingtoneVibrationSupported(mContext));

        // Configure the audio manager and register the audio playback callback:
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        AudioPlaybackCallback callback = createAudioPlaybackCallback(queue);
        AudioManager audioManager =
                configureAudioManager(AudioManager.RINGER_MODE_VIBRATE, callback);

        // Configure the "Vibrations & haptics" settings:
        configureVibrationSettings(ON);

        AppControlWrapper managedApp = null;
        try {
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> RingtoneManager.setActualDefaultRingtoneUri(mContext,
                            RingtoneManager.TYPE_RINGTONE, testRingtoneUri));
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mt = addIncomingCallAndVerify(managedApp);

            // Verify that the call rang:
            verifyCallIsInState(mt, STATE_RINGING);
            Boolean ringing = queue.poll(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertNotNull("Telecom should have played a ringtone, timed out waiting for "
                    + "state change", ringing);
            assertTrue("Telecom should have played a ringtone.", ringing);

            // Verify that the device is vibrating by inspecting the vibrator dumpsys:
            waitForRingtoneVibrationLogOrTimeout();

            // Disconnect the call:
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            setCallStateAndVerify(managedApp, mt, STATE_DISCONNECTED);
        } finally {
            // restore the default ringtone
            ShellIdentityUtils.invokeWithShellPermissions(
                    () -> RingtoneManager.setActualDefaultRingtoneUri(mContext,
                            RingtoneManager.TYPE_RINGTONE, defaultRingtoneUri));
            tearDownApp(managedApp);
            audioManager.unregisterAudioPlaybackCallback(callback);
        }
    }

    private static Boolean isCurrentVibrationInDumpsys() throws Exception {
        String result =
                executeShellCommand(
                        InstrumentationRegistry.getInstrumentation(),
                        QUERY_VIBRATOR_MANAGER_COMMAND);
        String currentVibrations = null;

        Matcher currentVibrationsMatcher = CURRENT_VIBRATIONS_PATTERN.matcher(result);

        while (currentVibrationsMatcher.find()) {
            currentVibrations = currentVibrationsMatcher.group(0);
        }

        if (currentVibrations != null) {
            Log.d(TAG, "isCurrentVibrationInDumpsys: currentVibrations=" + currentVibrations);
            Matcher ringtoneMatcher = RINGTONE_VIBRATION_PATTERN.matcher(currentVibrations);
            if (ringtoneMatcher.find()) {
                Log.d(TAG, "isCurrentVibrationInDumpsys: true");
                return true;
            }
        }
        Log.d(TAG, "isCurrentVibrationInDumpsys: false");
        return false;
    }

    private AudioManager configureAudioManager(int ringerMode, AudioPlaybackCallback callback) {
        AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        assertNotNull("AudioManager should not be null", audioManager);

        // Configure the ringer mode:
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(audioManager,
                am -> am.setRingerMode(ringerMode));

        audioManager.registerAudioPlaybackCallback(callback, new Handler(Looper.getMainLooper()));

        return audioManager;
    }

    private AudioPlaybackCallback createAudioPlaybackCallback(LinkedBlockingQueue<Boolean> queue) {
        return new AudioPlaybackCallback() {
            @Override
            public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                super.onPlaybackConfigChanged(configs);
                boolean isPlayingRingtone = configs.stream()
                        .anyMatch(c -> c.getAudioAttributes().getUsage()
                                == USAGE_NOTIFICATION_RINGTONE);
                if (isPlayingRingtone && queue.isEmpty()) {
                    queue.add(isPlayingRingtone);
                }
            }
        };
    }

    void configureVibrationSettings (int vibrationSetting) {
        Settings settings = mContext.getSystemService(Settings.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(settings,
                s -> Settings.System.putInt(mContext.getContentResolver(),
                        Settings.System.VIBRATE_ON, vibrationSetting));
    }

    private boolean hasVibrator() {
        return mContext.getSystemService(Vibrator.class).hasVibrator();
    }

    void waitToEnsureNoRingtoneVibrationLog() throws Exception {
        sleep(WAIT_FOR_NO_RING_TIMEOUT_MS);
        assertFalse("Current vibration should not be in dumpsys",
                isCurrentVibrationInDumpsys());
    }

    void waitForRingtoneVibrationLogOrTimeout() throws Exception {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual () throws Exception {
                        return isCurrentVibrationInDumpsys();
                    }
                },
                WAIT_FOR_VIBRATOR_LOGS_UPDATE_TIMEOUT_MS,
                "Current ringtone vibration never logged to dumpsys before timeout."
        );
    }

    void waitUntilConditionIsTrueOrTimeout(Condition condition, long timeout,
            String description) throws Exception {
        final long start = System.currentTimeMillis();
        while (!Objects.equals(condition.expected(), condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    protected interface Condition {
        Object expected();
        Object actual() throws Exception;
    }

    void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }
}
