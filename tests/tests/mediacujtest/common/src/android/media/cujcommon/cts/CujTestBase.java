/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.cujcommon.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.app.ActivityTaskManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.UserManager;
import android.telephony.TelephonyManager;

import androidx.test.core.app.ActivityScenario;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * This class comprises of routines that are generic to all tests.
 */
public class CujTestBase {

  static final String SHORTFORM_PLAYBAK_TEST_APP = "android.media.cujsmalltest.cts";

  protected static final Duration TEST_OVERHEAD = Duration.ofSeconds(30);

  // A delay of about 1 to 2 seconds is observed after each seek on slower devices.
  public static final Duration OVERHEAD_PER_SEEK = Duration.ofSeconds(2);

  static final int[] ORIENTATIONS = {
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
  };
  private static final int AUDIOTRACK_DEFAULT_SAMPLE_RATE = 44100;
  private static final int AUDIOTRACK_DEFAULT_CHANNEL_MASK = AudioFormat.CHANNEL_OUT_STEREO;

  protected MainActivity mActivity;
  protected ScrollTestActivity mScrollActivity;
  protected AudioOffloadTestActivity mAudioOffloadActivity;
  public PlayerListener mListener;

  public CujTestBase(PlayerListener playerListener) {
    if (playerListener.isScrollTest()) {
      ActivityScenario<ScrollTestActivity> scenario = ActivityScenario.launch(
          ScrollTestActivity.class);
      scenario.onActivity(activity -> {
        this.mScrollActivity = activity;
      });
      mListener = playerListener;
      mScrollActivity.addPlayerListener(mListener);
      mListener.setScrollActivity(mScrollActivity);
    } else if (playerListener.isAudioOffloadTest()) {
      ActivityScenario<AudioOffloadTestActivity> scenario = ActivityScenario.launch(
          AudioOffloadTestActivity.class);
      scenario.onActivity(activity -> {
        this.mAudioOffloadActivity = activity;
      });
      mListener = playerListener;
      mAudioOffloadActivity.addPlayerListener(mListener);
      mListener.setAudioOffloadActivity(mAudioOffloadActivity);
    } else {
      ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
      scenario.onActivity(activity -> {
        this.mActivity = activity;
      });
      mListener = playerListener;
      mActivity.addPlayerListener(mListener);
      mListener.setActivity(mActivity);
    }
  }

  /**
   * Whether the device supports orientation request from apps.
   */
  public static boolean supportOrientationRequest(final Activity activity) {
    final PackageManager pm = activity.getPackageManager();
    return pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE)
        && pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT);
  }

  /**
   * Whether the device supports phone call feature.
   */
  public static boolean deviceSupportPhoneCall(final Activity activity) {
    return (((TelephonyManager) activity.getApplicationContext()
        .getSystemService(Context.TELEPHONY_SERVICE)).getPhoneType()
        != TelephonyManager.PHONE_TYPE_NONE);
  }

  /**
   * Whether the device supports picture-in-picture feature.
   */
  public static boolean deviceSupportPipMode(final Activity activity) {
    return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
  }

  /**
   * Whether the device supports split-screen feature.
   */
  public static boolean deviceSupportSplitScreenMode(final Activity activity) {
    return ActivityTaskManager.supportsSplitScreenMultiWindow(activity);
  }

  /**
   * Whether the device supports audio offloading for particular encoding.
   */
  public static boolean deviceSupportAudioOffload(int encoding) {
    AudioFormat audioFormat = new AudioFormat.Builder()
        .setEncoding(encoding)
        .setSampleRate(AUDIOTRACK_DEFAULT_SAMPLE_RATE)
        .setChannelMask(AUDIOTRACK_DEFAULT_CHANNEL_MASK)
        .build();
    AudioAttributes defaultAudioAttributes = new AudioAttributes.Builder().build();
    return AudioManager.isOffloadedPlaybackSupported(audioFormat, defaultAudioAttributes);
  }

  /**
   * Whether the device is a watch.
   */
  public static boolean isWatchDevice(final Activity activity) {
    return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
  }

  /**
   * Whether the device is a television.
   */
  public static boolean isTelevisionDevice(final Activity activity) {
    return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
  }

  /**
   * Whether the given {@code activity} is running as a visible background user.
   */
  public static boolean isVisibleBackgroundNonProfileUser(Activity activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      return false;
    }

    UserManager um = activity.getSystemService(UserManager.class);
    if (!um.isVisibleBackgroundUsersSupported()) {
      return false;
    }

    return um.isUserVisible() && !um.isUserForeground() && !um.isProfile();
  }

  /**
   * Prepare the player, input list and add input list to player's playlist. After that, play for
   * the provided playlist and validate playback time.
   *
   * @param mediaUrls List of mediaurl
   * @param timeout   Timeout for the test
   */
  public void play(List<String> mediaUrls, Duration timeout)
      throws TimeoutException, InterruptedException {
    long startTime = System.currentTimeMillis();
    if (mListener.isScrollTest()) {
      mScrollActivity.runOnUiThread(() -> {
        mScrollActivity.prepareMediaItems(mediaUrls);
      });
    } else if (mListener.isAudioOffloadTest()) {
      mAudioOffloadActivity.runOnUiThread(() -> {
        mAudioOffloadActivity.prepareMediaItems(mediaUrls);
      });
    } else {
      mActivity.runOnUiThread(() -> {
        mActivity.prepareMediaItems(mediaUrls);
      });
    }

    long endTime = System.currentTimeMillis() + timeout.toMillis();
    // Wait for playback to finish
    synchronized (PlayerListener.LISTENER_LOCK) {
      while (!PlayerListener.mPlaybackEnded) {
        PlayerListener.LISTENER_LOCK.wait(timeout.toMillis());
        if (endTime < System.currentTimeMillis()) {
          throw new TimeoutException(
              "playback timed out after " + timeout.toMillis() + " milli seconds.");
        }
      }
      PlayerListener.mPlaybackEnded = false;
    }
    long actualTotalTime = System.currentTimeMillis() - startTime;
    long expectedTotalTime = mListener.getExpectedTotalTime();
    mListener.onTestCompletion();
    assertWithMessage("Test did not complete within expected time").that(actualTotalTime)
        .isWithin(TEST_OVERHEAD.plus(mListener.getTotalSeekOverhead()).toMillis())
        .of(expectedTotalTime);
  }
}
