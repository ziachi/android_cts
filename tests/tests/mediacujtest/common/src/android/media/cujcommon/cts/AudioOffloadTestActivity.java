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

import static org.junit.Assert.assertEquals;

import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.ExoPlayer.AudioOffloadListener;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AudioOffloadTestActivity extends AppCompatActivity {

  protected PlayerView mExoplayerView;
  protected ExoPlayer mPlayer;
  protected static List<String> sVideoUrls = new ArrayList<>();
  protected PlayerListener mPlayerListener;
  protected boolean mIsSleepingForAudioOffloadEnabled;
  protected boolean mIsAudioOffloadEnabled;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main_audio_offload);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    buildPlayer();
  }

  /**
   * Get audio offload listener
   */
  private AudioOffloadListener getAudioOffloadListener() {
    return new AudioOffloadListener() {
      @Override
      public void onSleepingForOffloadChanged(boolean isSleepingForOffload) {
        mIsSleepingForAudioOffloadEnabled = true;
      }

      @Override
      public void onOffloadedPlayback(boolean isOffloadedPlayback) {
        if (!mIsAudioOffloadEnabled && isOffloadedPlayback) {
          mIsAudioOffloadEnabled = true;
        }
        if (mIsAudioOffloadEnabled && !isOffloadedPlayback) {
          assertEquals("Audio offload is disabled before the playback is finished. "
                  + "ClipDuration= " + mPlayer.getDuration() + " currentPosition= "
                  + mPlayer.getCurrentPosition(), (float) mPlayer.getDuration(),
              (float) mPlayer.getCurrentPosition(), 5000);
        }
      }
    };
  }

  /**
   * Build the player
   */
  protected void buildPlayer() {
    AudioOffloadListener listener = getAudioOffloadListener();
    CustomRenderersFactory customRenderersFactory = new CustomRenderersFactory(this, listener);
    mPlayer = new ExoPlayer.Builder(this, customRenderersFactory).build();
    mPlayer.addAudioOffloadListener(listener);
    // Enable audio offloading
    AudioOffloadPreferences audioOffloadPreferences = new AudioOffloadPreferences.Builder()
        .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
        .build();
    TrackSelectionParameters currentParameters = mPlayer.getTrackSelectionParameters();
    TrackSelectionParameters newParameters = currentParameters.buildUpon()
        .setAudioOffloadPreferences(audioOffloadPreferences).build();
    mPlayer.setTrackSelectionParameters(newParameters);
    mExoplayerView = findViewById(R.id.audioOffloadExoplayer);
    mExoplayerView.setPlayer(mPlayer);
  }

  /**
   * Prepare input list and add it to player's playlist.
   */
  public void prepareMediaItems(List<String> urls) {
    sVideoUrls = urls != null ? Collections.unmodifiableList(urls) : null;
    if (sVideoUrls == null) {
      return;
    }
    for (String videoUrl : sVideoUrls) {
      MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
      mPlayer.addMediaItem(mediaItem);
    }
  }

  /**
   * Prepare and play the player.
   */
  @Override
  protected void onStart() {
    super.onStart();
    mPlayer.prepare();
    mPlayer.play();
  }

  /**
   * Stop the player.
   */
  @Override
  protected void onStop() {
    mPlayer.stop();
    super.onStop();
  }

  /**
   * Release the player and destroy the activity
   */
  @Override
  protected void onDestroy() {
    super.onDestroy();
    mPlayer.release();
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  /**
   * Register a listener to receive events from the player.
   *
   * <p>This method can be called from any thread.
   *
   * @param listener The listener to register.
   */
  public void addPlayerListener(PlayerListener listener) {
    mPlayer.addListener(listener);
    this.mPlayerListener = listener;
  }

  /**
   * Unregister a listener registered through addPlayerListener(Listener). The listener will no
   * longer receive events.
   */
  public void removePlayerListener() {
    mPlayer.removeListener(this.mPlayerListener);
  }
}
