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

package android.media.cujlargetest.cts;

import android.media.cujcommon.cts.AdaptivePlaybackTestPlayerListener;
import android.media.cujcommon.cts.CujTestBase;
import android.media.cujcommon.cts.CujTestParam;
import android.media.cujcommon.cts.PlaybackTestPlayerListener;
import android.media.cujcommon.cts.SeekTestPlayerListener;
import android.media.cujcommon.cts.SpeedChangeTestPlayerListener;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.PlatinumTest;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@LargeTest
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(Parameterized.class)
public class CtsMediaLargeFormPlaybackTest extends CujTestBase {

  private static final Duration PLAYLIST_DURATION_FIFTEEN_MIN = Duration.ofMinutes(15);
  private static final Duration PLAYLIST_DURATION_THIRTY_MIN = Duration.ofMinutes(30);
  private static final Duration SEEK_DURATION = Duration.ofSeconds(10);
  private static final Duration SEEK_MESSAGE_POSITION = Duration.ofSeconds(150);
  private static final int SEEK_ITERATIONS = 30;
  // A delay of about 1 to 2 seconds is observed after each seek on slower devices. Hence, the seek
  // overhead is 30 (Number of seek iterations) * 2 (Overhead for each seek) = 60 seconds.
  private static final Duration SEEK_OVERHEAD_PER_CLIP = OVERHEAD_PER_SEEK.multipliedBy(
      SEEK_ITERATIONS);
  private static final int RANDOM_SEEK_ITERATIONS = 10;
  // Number of seek iterations is 30 and the seek duration is 10 seconds. We seek forward 10 times,
  // backward 10 times and then randomly backwards or forwards 10 times on each media item. While
  // performing 10 random seeks, if all 10 seeks are in backward direction then we need an extra
  // overhead of 10 (Number of random seek iterations) * 10 (Duration of each seek) = 100 seconds.
  private static final Duration RANDOM_SEEK_OVERHEAD_PER_CLIP = SEEK_DURATION.multipliedBy(
      RANDOM_SEEK_ITERATIONS);
  private static final int NUMBER_OF_VIDEO_TRACKS = 6;
  private static final String MEDIA_DIR = WorkDir.getMediaDirString();
  private static final String MKA_ELEPHANTDREAM_OPUS_2CH_48Khz_5MIN_URI_STRING =
      MEDIA_DIR + "ElephantsDream_opus_2ch_48Khz_5min.mka";
  private static final String MP4_TEARSOFSTEEL_AAC_2CH_48Khz_5MIN_URI_STRING =
      MEDIA_DIR + "TearsOfSteel_aac_2ch_48Khz_5min.mp4";
  private static final String MKA_BIGBUCKBUNNY_VORBIS_2CH_48Khz_5MIN_URI_STRING =
      MEDIA_DIR + "BigBuckBunny_vorbis_2ch_48Khz_5min.mka";
  private static final String MP3_ELEPHANTSDREAM_BIGBUCKBUNNY_CONCAT_2CH_44Khz_30MIN_URI_STRING =
      MEDIA_DIR + "ElephantsDream_BigBuckBunny_concat_2ch_44Khz_30min.mp3";
  private static final String WEBM_ELEPHANTDREAM_640x480_VP9_5MIN_URI_STRING =
      MEDIA_DIR + "ElephantsDream_640x480_vp9_5min.webm";
  private static final String WEBM_TEARSOFSTEEL_640X480_VP9_5MIN_URI_STRING =
      MEDIA_DIR + "TearsOfSteel_640x480_vp9_5min.webm";
  private static final String WEBM_BIGBUCKBUNNY_640X480_VP9_5MIN_URI_STRING =
      MEDIA_DIR + "BigBuckBunny_640x480_vp9_5min.webm";
  private static final String MP4_ELEPHANTSDREAM_BIGBUCKBUNNY_CONCAT_1080P_AVC_30MIN_URI_STRING =
      MEDIA_DIR + "ElephantsDream_BigBuckBunny_concat_1080p_avc_30min.mp4";
  private static final String WEBM_ELEPHANTSDREAM_DASH_3MIN_URI_STRING =
      MEDIA_DIR + "ElephantsDream.mpd";
  private static final String WEBM_TEARSOFSTEEL_640x480_VP9_30S_URI_STRING =
      MEDIA_DIR + "TearsOfSteel_640x480_vp9_30sec.webm";

  CujTestParam mCujTestParam;

  public CtsMediaLargeFormPlaybackTest(CujTestParam cujTestParam, String testType) {
    super(cujTestParam.getPlayerListener());
    mCujTestParam = cujTestParam;
  }

  /**
   * Returns the list of parameters
   */
  @Parameterized.Parameters(name = "{index}_{1}")
  public static Collection<Object[]> input() {
    // CujTestParam, testId
    final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
        {CujTestParam.builder().setMediaUrls(prepare_15minAudioPlaylist())
            .setDuration(PLAYLIST_DURATION_FIFTEEN_MIN).setOverhead(TEST_OVERHEAD)
            .setPlayerListener(new PlaybackTestPlayerListener()).build(),
            "Audio_5min_3clips_PlaybackTest"},
        {CujTestParam.builder().setMediaUrls(prepareVP9_640x480_15minVideoPlaylist())
            .setDuration(PLAYLIST_DURATION_FIFTEEN_MIN).setOverhead(TEST_OVERHEAD)
            .setPlayerListener(new PlaybackTestPlayerListener()).build(),
            "VP9_640x480_5min_3clips_PlaybackTest"},
        {CujTestParam.builder().setMediaUrls(prepareVP9_640x480_15minVideoPlaylist())
            .setDuration(PLAYLIST_DURATION_FIFTEEN_MIN).setOverhead(TEST_OVERHEAD.plus(
                (SEEK_OVERHEAD_PER_CLIP.plus(RANDOM_SEEK_OVERHEAD_PER_CLIP)).multipliedBy(
                    prepareVP9_640x480_15minVideoPlaylist().size()))).setPlayerListener(
                new SeekTestPlayerListener(SEEK_ITERATIONS, SEEK_DURATION,
                    SEEK_MESSAGE_POSITION)).build(), "VP9_640x480_5min_3clips_seekTest"},
        {CujTestParam.builder().setMediaUrls(prepare_30minAudioPlaylist())
            .setDuration(PLAYLIST_DURATION_THIRTY_MIN).setOverhead(TEST_OVERHEAD)
            .setPlayerListener(new PlaybackTestPlayerListener()).build(),
            "Audio_30min_PlaybackTest"},
        {CujTestParam.builder().setMediaUrls(prepareAvc_1080p_30minVideoPlaylist())
            .setDuration(PLAYLIST_DURATION_THIRTY_MIN).setOverhead(TEST_OVERHEAD)
            .setPlayerListener(new PlaybackTestPlayerListener()).build(), "Avc_1080p_30min"},
        {CujTestParam.builder().setMediaUrls(prepareAvc_1080p_30minVideoPlaylist())
            .setDuration(PLAYLIST_DURATION_THIRTY_MIN).setOverhead(
                TEST_OVERHEAD.plus(SEEK_OVERHEAD_PER_CLIP).plus(RANDOM_SEEK_OVERHEAD_PER_CLIP))
            .setPlayerListener(new SeekTestPlayerListener(SEEK_ITERATIONS, SEEK_DURATION,
                SEEK_MESSAGE_POSITION)).build(), "Avc_1080p_30min_seekTest"},
        {CujTestParam.builder().setMediaUrls(prepareVp9_Local_DASH_3minVideoPlaylist())
            .setDuration(Duration.ofMinutes(3) /* clipDuration */).setOverhead(TEST_OVERHEAD)
            .setPlayerListener(new AdaptivePlaybackTestPlayerListener(NUMBER_OF_VIDEO_TRACKS,
                Duration.ofSeconds(15))).build(), "Vp9_DASH_3min_adaptivePlaybackTest"},
        {CujTestParam.builder()
            .setMediaUrls(prepareHevc_720p_30secVideoPlaylistForSpeedChangeTest())
            .setDuration(Duration.ofSeconds(30) /* clipDuration */).setOverhead(TEST_OVERHEAD)
            .setPlayerListener(new SpeedChangeTestPlayerListener()).build(),
            "Hevc_720p_30sec_SpeedChangeTest"},
    }));
    return exhaustiveArgsList;
  }

  /**
   * Prepare 15min audio playlist. The playlist has 3 clips each of 5min.
   */
  public static List<String> prepare_15minAudioPlaylist() {
    List<String> videoInput = Arrays.asList(
        MKA_ELEPHANTDREAM_OPUS_2CH_48Khz_5MIN_URI_STRING,
        MP4_TEARSOFSTEEL_AAC_2CH_48Khz_5MIN_URI_STRING,
        MKA_BIGBUCKBUNNY_VORBIS_2CH_48Khz_5MIN_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare Vp9 640x480 15min video playlist. The playlist has 3 clips each of 5min.
   */
  public static List<String> prepareVP9_640x480_15minVideoPlaylist() {
    List<String> videoInput = Arrays.asList(
        WEBM_ELEPHANTDREAM_640x480_VP9_5MIN_URI_STRING,
        WEBM_TEARSOFSTEEL_640X480_VP9_5MIN_URI_STRING,
        WEBM_BIGBUCKBUNNY_640X480_VP9_5MIN_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare 30min audio list.
   */
  public static List<String> prepare_30minAudioPlaylist() {
    List<String> audioInput = Arrays.asList(
        MP3_ELEPHANTSDREAM_BIGBUCKBUNNY_CONCAT_2CH_44Khz_30MIN_URI_STRING);
    return audioInput;
  }

  /**
   * Prepare Avc 1080p 30min video list.
   */
  public static List<String> prepareAvc_1080p_30minVideoPlaylist() {
    List<String> videoInput = Arrays.asList(
        MP4_ELEPHANTSDREAM_BIGBUCKBUNNY_CONCAT_1080P_AVC_30MIN_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare Vp9 DASH 3min video list.
   */
  public static List<String> prepareVp9_Local_DASH_3minVideoPlaylist() {
    List<String> videoInput = Arrays.asList(
        WEBM_ELEPHANTSDREAM_DASH_3MIN_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare Hevc 720p 30sec video list for Speed change test
   */
  public static List<String> prepareHevc_720p_30secVideoPlaylistForSpeedChangeTest() {
    List<String> videoInput = Arrays.asList(
        WEBM_TEARSOFSTEEL_640x480_VP9_30S_URI_STRING);
    return videoInput;
  }

  // Test to Verify video playback with and without seek
  @ApiTest(apis = {"android.media.MediaCodec#configure",
      "android.media.MediaCodec#createByCodecName",
      "android.media.MediaCodecInfo#getName",
      "android.media.MediaCodecInfo#getSupportedTypes",
      "android.media.MediaCodecInfo#isSoftwareOnly"})
  @PlatinumTest(focusArea = "media")
  @Test
  public void testVideoPlayback() throws Exception {
    play(mCujTestParam.getMediaUrls(),
        mCujTestParam.getDuration().plus(mCujTestParam.getOverhead()));
  }
}
