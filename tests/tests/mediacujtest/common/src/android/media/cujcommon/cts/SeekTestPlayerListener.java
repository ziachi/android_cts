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

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.Player;

import java.time.Clock;
import java.time.Duration;
import java.util.Random;

public class SeekTestPlayerListener extends PlayerListener {

  private static final String LOG_TAG = SeekTestPlayerListener.class.getSimpleName();

  private final int mNumOfSeekIterationsPerClip;
  private Duration mSeekDuration;
  private final long mSeed;

  public SeekTestPlayerListener(int numOfSeekIterationsPerClip, Duration seekDuration,
      Duration sendMessagePosition) {
    super();
    this.mNumOfSeekIterationsPerClip = numOfSeekIterationsPerClip;
    this.mSeekDuration = seekDuration;
    this.mSeed = getSeed();
    this.mSendMessagePosition = sendMessagePosition;
  }

  /**
   * Returns seed for Seek test.
   */
  private long getSeed() {
    // Truncate time to the nearest day.
    long seed = Clock.tick(Clock.systemUTC(), Duration.ofDays(1)).instant().toEpochMilli();
    Log.d(LOG_TAG, "Random seed = " + seed);
    return seed;
  }

  /**
   * Seek the player.
   */
  private void seek() {
    Random random = new Random(mSeed);
    // In case of small test, number of seek requested (i.e. mNumOfSeekIterationsPerClip) is one
    // per clip. We seek forward and backward alternatively for mSeekDuration for all the clips in
    // the given media list.
    // In case of large test, number of seek requested (i.e. mNumOfSeekIterationsPerClip) is 30 per
    // clip. We seek forward 10 times, backward 10 times or vice-versa (i.e. backward 10 times and
    // forward 10 times) and then randomly backwards or forwards 10 times for mSeekDuration for all
    // the clips in the given media l̥ist.
    for (int i = 0; i < mNumOfSeekIterationsPerClip; i++) {
      mActivity.mPlayer.seekTo(mActivity.mPlayer.getCurrentPosition() + mSeekDuration.toMillis());
      // Update expected total time due to seek
      mExpectedTotalTime -= mSeekDuration.toMillis();
      mTotalSeekOverhead = mTotalSeekOverhead.plus(CujTestBase.OVERHEAD_PER_SEEK);
      if (mNumOfSeekIterationsPerClip == 1 || i == 9) {
        mSeekDuration = mSeekDuration.multipliedBy(-1);
      } else if (i >= 19) {
        mSeekDuration =
            random.nextBoolean() ? mSeekDuration.multipliedBy(-1) : mSeekDuration.multipliedBy(1);
      }
    }
  }

  @Override
  public TestType getTestType() {
    return TestType.SEEK_TEST;
  }

  @Override
  public void onEventsPlaybackStateChanged(@NonNull Player player) {
    if (mExpectedTotalTime == 0 && player.getPlaybackState() == Player.STATE_READY) {
      // At the first media transition player is not ready. So, add duration of
      // first clip when player is ready
      mExpectedTotalTime += player.getDuration();
    }
  }

  @Override
  public void onEventsMediaItemTransition(@NonNull Player player) {
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          seek();
        }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition.toMillis())
        .setDeleteAfterDelivery(true)
        .send();
  }
}
