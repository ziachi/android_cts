/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.media.audio.cts;

import static android.media.Utils.SYNCHRONIZED_VIBRATION;
import static android.media.Utils.VIBRATION_URI_PARAM;
import static android.media.cts.Utils.getTestVibrationFile;
import static android.media.cts.Utils.RINGTONE_TEST_URI;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.audio.Flags;
import android.media.audiofx.HapticGenerator;
import android.media.cts.Utils;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.SystemUtil;

import java.io.IOException;
import java.util.Objects;

@AppModeFull(reason = "TODO: evaluate and port to instant")
public class RingtoneTest extends InstrumentationTestCase {
    private static final String TAG = "RingtoneTest";
    private static final String PKG = "android.media.audio.cts";

    private Context mContext;
    private Ringtone mRingtone;
    private AudioManager mAudioManager;
    private int mOriginalVolume;
    private int mOriginalRingerMode;
    private int mOriginalStreamType;
    private Uri mDefaultRingUri;

    private static boolean sIsAtLeastS = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.S);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        enableAppOps();
        mContext = getInstrumentation().getContext();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mRingtone = RingtoneManager.getRingtone(mContext, Settings.System.DEFAULT_RINGTONE_URI);
        // backup ringer settings
        mOriginalRingerMode = mAudioManager.getRingerMode();
        mOriginalVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
        mOriginalStreamType = mRingtone.getStreamType();

        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING);

        if (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            mAudioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume / 2,
                    AudioManager.FLAG_ALLOW_RINGER_MODES);
        } else if (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
            mAudioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume / 2,
                    AudioManager.FLAG_ALLOW_RINGER_MODES);
        } else {
            try {
                Utils.toggleNotificationPolicyAccess(
                        mContext.getPackageName(), getInstrumentation(), true);
                // set ringer to a reasonable volume
                mAudioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume / 2,
                        AudioManager.FLAG_ALLOW_RINGER_MODES);
                // make sure that we are not in silent mode
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            } finally {
                Utils.toggleNotificationPolicyAccess(
                        mContext.getPackageName(), getInstrumentation(), false);
            }
        }

        mDefaultRingUri = RingtoneManager.getActualDefaultRingtoneUri(mContext,
                RingtoneManager.TYPE_RINGTONE);
    }

    private void enableAppOps() {
        StringBuilder cmd = new StringBuilder();
        cmd.append("appops set --user ");
        cmd.append(UserHandle.myUserId());
        cmd.append(" ");
        cmd.append(getInstrumentation().getContext().getPackageName());
        cmd.append(" android:write_settings allow");
        getInstrumentation().getUiAutomation().executeShellCommand(cmd.toString());
        try {
            Thread.sleep(2200);
        } catch (InterruptedException e) {
        }
    }

    @Override
    protected void tearDown() throws Exception {
        // restore original settings
        if (mRingtone != null) {
            if (mRingtone.isPlaying()) mRingtone.stop();
            mRingtone.setStreamType(mOriginalStreamType);
        }
        if (mAudioManager != null) {
            try {
                Utils.toggleNotificationPolicyAccess(
                        mContext.getPackageName(), getInstrumentation(), true);
                mAudioManager.setRingerMode(mOriginalRingerMode);
                mAudioManager.setStreamVolume(AudioManager.STREAM_RING, mOriginalVolume,
                        AudioManager.FLAG_ALLOW_RINGER_MODES);
            } finally {
                Utils.toggleNotificationPolicyAccess(
                        mContext.getPackageName(), getInstrumentation(), false);
            }
        }
        RingtoneManager.setActualDefaultRingtoneUri(mContext, RingtoneManager.TYPE_RINGTONE,
                mDefaultRingUri);
        super.tearDown();
    }

    private boolean hasAudioOutput() {
        return getInstrumentation().getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
    }

    private boolean isTV() {
        return getInstrumentation().getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY);
    }

    private boolean hasVibrator() {
        return mContext.getSystemService(Vibrator.class).hasVibrator();
    }

    public void testRingtone() {
        if (isTV()) {
            return;
        }
        if (!hasAudioOutput()) {
            Log.i(TAG, "Skipping testRingtone(): device doesn't have audio output.");
            return;
        }

        assertNotNull(mRingtone.getTitle(mContext));
        assertTrue(mOriginalStreamType >= 0);

        mRingtone.setStreamType(AudioManager.STREAM_MUSIC);
        assertEquals(AudioManager.STREAM_MUSIC, mRingtone.getStreamType());
        mRingtone.setStreamType(AudioManager.STREAM_ALARM);
        assertEquals(AudioManager.STREAM_ALARM, mRingtone.getStreamType());
        // make sure we play on STREAM_RING because we the volume on this stream is not 0
        mRingtone.setStreamType(AudioManager.STREAM_RING);
        assertEquals(AudioManager.STREAM_RING, mRingtone.getStreamType());

        // Some releases, such as Wear R can return a null ringtone when its badly formed, this
        // assumption may change on T+ so if it does, update this test accordingly
        Ringtone badRingtone =
            RingtoneManager.getRingtone(mContext, Uri.parse("content://fakeuri"));
        boolean nullForBadRingtonesSupported = badRingtone == null;
        if (!nullForBadRingtonesSupported) {
            // test the "None" ringtone
            RingtoneManager.setActualDefaultRingtoneUri(
                mContext, RingtoneManager.TYPE_RINGTONE, null);
            mRingtone = RingtoneManager.getRingtone(mContext, Settings.System.DEFAULT_RINGTONE_URI);
            assertTrue(mRingtone.getStreamType() == AudioManager.STREAM_RING);
            mRingtone.play();
            assertFalse(mRingtone.isPlaying());
        }

        // Test an actual ringtone
        Uri uri = RingtoneManager.getValidRingtoneUri(mContext);
        assertNotNull("ringtone was unexpectedly null", uri);
        Uri uriWithUser = ContentProvider.maybeAddUserId(uri, mContext.getUserId());
        RingtoneManager.setActualDefaultRingtoneUri(mContext, RingtoneManager.TYPE_RINGTONE,
                uriWithUser);
        mRingtone = RingtoneManager.getRingtone(mContext, Settings.System.DEFAULT_RINGTONE_URI);
        assertTrue(mRingtone.getStreamType() == AudioManager.STREAM_RING);
        mRingtone.play();
        assertTrue("couldn't play ringtone " + uriWithUser, mRingtone.isPlaying());
        mRingtone.stop();
        assertFalse(mRingtone.isPlaying());
    }

    public void testPlaybackProperties() {
        if (isTV()) {
            return;
        }
        if (!hasAudioOutput()) {
            Log.i(TAG, "Skipping testRingtone(): device doesn't have audio output.");
            return;
        }

        Uri uri = RingtoneManager.getValidRingtoneUri(mContext);
        assertNotNull("ringtone was unexpectedly null", uri);
        RingtoneManager.setActualDefaultRingtoneUri(mContext, RingtoneManager.TYPE_RINGTONE, uri);
        assertNotNull(mRingtone.getTitle(mContext));
        final AudioAttributes ringtoneAa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).
                build();
        mRingtone.setAudioAttributes(ringtoneAa);
        assertEquals(ringtoneAa, mRingtone.getAudioAttributes());
        mRingtone.setLooping(true);
        mRingtone.setVolume(0.5f);
        if (sIsAtLeastS) {
            assertEquals(HapticGenerator.isAvailable(), mRingtone.setHapticGeneratorEnabled(true));
        }
        mRingtone.play();
        assertTrue("couldn't play ringtone " + uri, mRingtone.isPlaying());
        assertTrue(mRingtone.isLooping());
        if (sIsAtLeastS) {
            assertEquals(HapticGenerator.isAvailable(), mRingtone.isHapticGeneratorEnabled());
        }
        assertEquals("invalid ringtone player volume", 0.5f, mRingtone.getVolume());
        mRingtone.stop();
        assertFalse(mRingtone.isPlaying());
    }

    public void testRingtoneVibration() throws IOException {
        if (isTV()) {
            return;
        }
        if (!hasVibrator()) {
            Log.i(TAG, "Skipping testRingtoneVibration(): device doesn't have a vibrator.");
            return;
        }
        if (!hasAudioOutput()) {
            Log.i(TAG, "Skipping testRingtoneVibration(): device doesn't have audio output.");
            return;
        }
        if (!Flags.enableRingtoneHapticsCustomization()) {
            Log.i(TAG, "Skipping testRingtoneVibration(): ringtone vibration isn't enabled.");
            return;
        }
        if (!Utils.isRingtoneVibrationSupported(mContext)) {
            Log.i(TAG, "Skipping testRingtoneVibration(): vibration settings isn't supported.");
            return;
        }

        assertThat(mRingtone.getVibrationEffect()).isNull();

        // Vibration effect was parsed for vibration file
        String vibrationUriString = getTestVibrationFile().toURI().toString();
        final Uri ringtoneUri = RINGTONE_TEST_URI.buildUpon().appendQueryParameter(
                VIBRATION_URI_PARAM, vibrationUriString).build();
        mRingtone = RingtoneManager.getRingtone(mContext, ringtoneUri);

        assertThat(mRingtone.getVibrationEffect()).isInstanceOf(VibrationEffect.class);

        // Do not parse vibration effect for synchronized vibration
        final Uri ringtoneUriWithSynchronized = RINGTONE_TEST_URI.buildUpon().appendQueryParameter(
                VIBRATION_URI_PARAM, SYNCHRONIZED_VIBRATION).build();
        mRingtone = RingtoneManager.getRingtone(mContext, ringtoneUriWithSynchronized);

        assertThat(mRingtone.getVibrationEffect()).isNull();
    }

    public void testRingtoneVibrationPlayback() throws IOException {
        if (isTV()) {
            return;
        }
        if (!hasVibrator()) {
            Log.i(TAG, "Skipping testRingtoneVibrationPlayback(): "
                    + "device doesn't have a vibrator.");
            return;
        }
        if (!hasAudioOutput()) {
            Log.i(TAG, "Skipping testRingtoneVibrationPlayback(): "
                    + "device doesn't have audio output.");
            return;
        }
        if (!Flags.enableRingtoneHapticsCustomization()) {
            Log.i(TAG, "Skipping testRingtoneVibrationPlayback(): "
                    + "ringtone vibration isn't enabled.");
            return;
        }
        if (!Utils.isRingtoneVibrationSupported(mContext)) {
            Log.i(TAG, "Skipping testRingtoneVibrationPlayback(): "
                    + "vibration settings isn't supported.");
            return;
        }

        assertThat(mRingtone.getVibrationEffect()).isNull();

        // Make sure we have vibration uri
        Uri uri = Uri.parse("android.resource://" + PKG + "/" + R.raw.john_cage);
        final Uri ringtoneUri = uri.buildUpon().appendQueryParameter(VIBRATION_URI_PARAM,
                getTestVibrationFile().toURI().toString()).build();
        Ringtone ringtone = RingtoneManager.getRingtone(mContext, ringtoneUri, null,
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setHapticChannelsMuted(true)
                        .build());
        assertThat(ringtone).isNotNull();
        ringtone.play();
        assertThat(ringtone.isPlaying()).isTrue();
        SystemClock.sleep(200);

        final VibratorManager vibratorManager = Objects.requireNonNull(
                mContext.getSystemService(VibratorManager.class));

        int[] vibratorIds = vibratorManager.getVibratorIds();
        boolean isVibrating = false;
        for (int vibratorId : vibratorIds) {
            if (SystemUtil.runWithShellPermissionIdentity(
                    () -> vibratorManager.getVibrator(vibratorId).isVibrating(),
                    Manifest.permission.ACCESS_VIBRATOR_STATE)) {
                isVibrating = true;
            }
        }
        assertTrue(isVibrating);
        ringtone.stop();
        assertFalse(ringtone.isPlaying());
    }
}
