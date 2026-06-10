/**
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

package android.media.cujcommon.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Looper;
import android.server.wm.UiDeviceUtils;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.media3.common.Player;

import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;

public class DeviceLockTestPlayerListener extends PlayerListener {

  private static final Duration LOCK_DURATION = Duration.ofSeconds(5);
  private static final Duration DELAY = Duration.ofSeconds(2);

  private final boolean mIsAudioOnlyClip;

  private Display mDisplay;
  private boolean mIsPlayerPlaying;

  public DeviceLockTestPlayerListener(Duration sendMessagePosition, boolean isAudioOnlyClip) {
    super();
    this.mSendMessagePosition = sendMessagePosition;
    this.mIsAudioOnlyClip = isAudioOnlyClip;
  }

  @Override
  public void onIsPlayingChanged(boolean isPlaying) {
    super.onIsPlayingChanged(isPlaying);
    mIsPlayerPlaying = isPlaying;
  }

  @Override
  public TestType getTestType() {
    return TestType.DEVICE_LOCK_TEST;
  }

  @Override
  public void onEventsPlaybackStateChanged(@NonNull Player player) {
    if (mExpectedTotalTime == 0 && player.getPlaybackState() == Player.STATE_READY) {
      // At the first media transition player is not ready. So, add duration of
      // first clip when player is ready
      mExpectedTotalTime += player.getDuration() + LOCK_DURATION.toMillis();
      // Register the screen receiver to listen for screen on and off events
      mDisplay = mActivity.getDisplay();
    }
  }

  @Override
  public void onEventsMediaItemTransition(@NonNull Player player) {
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          // Lock the device
          UiDeviceUtils.pressSleepButton();
          // Unlock the device after LOCK_DURATION
          Timer timer = new Timer();
          timer.schedule(new Task(), LOCK_DURATION.toMillis());
        }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition.toMillis())
        .setDeleteAfterDelivery(true)
        .send();
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          // Verify that the screen is on and player is playing while device is in unlocked state
          assertTrue(isDisplayOn());
          assertTrue(mIsPlayerPlaying);
        }).setLooper(Looper.getMainLooper())
        .setPosition(
            mIsAudioOnlyClip ? (mSendMessagePosition.plus(LOCK_DURATION).plus(DELAY).toMillis())
                : (mSendMessagePosition.plus(DELAY).toMillis()))
        .setDeleteAfterDelivery(true)
        .send();
  }

  private boolean isDisplayOn() {
    return mDisplay != null && mDisplay.getState() == Display.STATE_ON;
  }

  class Task extends TimerTask {

    @Override
    public void run() {
      unlockPhone();
    }

    private void unlockPhone() {
      // Verify that the screen is off when device is in locked state
      assertFalse(isDisplayOn());
      if (mIsAudioOnlyClip) {
        // In case of audio only clip, verify that the player is playing while device is in
        // locked state
        assertTrue(mIsPlayerPlaying);
      } else {
        // Otherwise verify that the player is not playing while device is in locked state
        assertFalse(mIsPlayerPlaying);
      }
      // Unlock the device
      UiDeviceUtils.pressWakeupButton();
      UiDeviceUtils.pressUnlockButton();
    }
  }
}
