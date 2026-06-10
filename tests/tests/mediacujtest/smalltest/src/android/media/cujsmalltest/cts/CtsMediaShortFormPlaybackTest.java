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

package android.media.cujsmalltest.cts;

import android.media.AudioFormat;
import android.media.cujcommon.cts.AudioOffloadTestPlayerListener;
import android.media.cujcommon.cts.CujTestBase;
import android.media.cujcommon.cts.CujTestParam;
import android.media.cujcommon.cts.DeviceLockTestPlayerListener;
import android.media.cujcommon.cts.LockPlaybackControllerTestPlayerListener;
import android.media.cujcommon.cts.OrientationTestPlayerListener;
import android.media.cujcommon.cts.PinchToZoomTestPlayerListener;
import android.media.cujcommon.cts.PipModeTestPlayerListener;
import android.media.cujcommon.cts.PlaybackTestPlayerListener;
import android.media.cujcommon.cts.PlayerListener.TestType;
import android.media.cujcommon.cts.ScrollTestPlayerListener;
import android.media.cujcommon.cts.SeekTestPlayerListener;
import android.media.cujcommon.cts.SplitScreenTestPlayerListener;
import android.media.cujcommon.cts.SwitchAudioTrackTestPlayerListener;
import android.media.cujcommon.cts.SwitchSubtitleTrackTestPlayerListener;
import android.platform.test.annotations.PlatinumTest;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@LargeTest
@RunWith(Parameterized.class)
public class CtsMediaShortFormPlaybackTest extends CujTestBase {

  private static final Duration MESSAGE_POSITION_THREE_SEC = Duration.ofSeconds(3);
  private static final Duration MESSAGE_POSITION_FIVE_SEC = Duration.ofSeconds(5);
  private static final Duration CLIP_DURATION_FIVE_SEC = Duration.ofSeconds(5);
  private static final Duration CLIP_DURATION_FIFTEEN_SEC = Duration.ofSeconds(15);
  private static final Duration PLAYLIST_DURATION_TWENTY_SEC = Duration.ofSeconds(20);
  private static final Duration PLAYLIST_DURATION_THREE_HUNDRED_SEC = Duration.ofSeconds(300);
  private static final Duration SEEK_DURATION = Duration.ofSeconds(5);
  private static final int SEEK_ITERATIONS = 1;
  // A delay of about 1 to 2 seconds is observed after each seek on slower devices. Hence, the seek
  // overhead is 1 (Number of seek iterations) * 2 (Overhead for each seek) = 2 seconds.
  private static final Duration SEEK_OVERHEAD_PER_CLIP = OVERHEAD_PER_SEEK.multipliedBy(
      SEEK_ITERATIONS);
  private static final int SCROLL_ITERATIONS = 2;
  private static final int NUMBER_OF_AUDIO_TRACKS = 2;
  private static final int NUMBER_OF_SUBTITLE_TRACKS = 2;
  private static final Duration LOCK_DURATION = Duration.ofSeconds(5);
  private static final String MP3_SINE_ASSET_1KHZ_40DB_LONG_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/sine1khzs40dblong";
  private static final String MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerJoyrides_720p_hevc_15s";
  private static final String MP4_FORBIGGERMELTDOWN_ASSET_720P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerMeltdowns_720p_hevc_15s";
  private static final String MP4_FORBIGGERBLAZEA_ASSET_720P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerBlazes_720p_hevc_15s";
  private static final String MP4_FORBIGGERESCAPES_ASSET_720P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerEscapes_720p_hevc_15s";
  private static final String MP4_FORBIGGERJOYRIDES_ASSET_480P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerJoyrides_480p_hevc_5s";
  private static final String MP4_FORBIGGERMELTDOWN_ASSET_480P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerMeltdowns_480p_hevc_5s";
  private static final String MP4_FORBIGGERBLAZEA_ASSET_480P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerBlazes_480p_hevc_5s";
  private static final String MP4_FORBIGGERESCAPES_ASSET_480P_HEVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerEscapes_480p_hevc_5s";
  private static final String MP4_FORBIGGERMELTDOWN_ASSET_1080P_AVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ForBiggerMeltdowns_1080p_avc_5s";
  private static final String MP4_ELEPHANTSDREAM_ASSET_1080P_AVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ElephantsDream_1080p_avc_5s";
  private static final String MP4_BIGBUCKBUNNY_ASSET_1080P_AVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/BigBuckBunny_1080p_avc_5s";
  private static final String MP4_ELEPHANTSDREAM_ASSET_360x640_AVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ElephantsDream_360x640_avc_5s";
  private static final String MP4_BIGBUCKBUNNY_ASSET_360x640_AVC_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/BigBuckBunny_360x640_avc_5s";
  private static final String MKV_TEARS_OF_STEEL_ASSET_AAC_2CH_44KHZ_AAC_1CH_44KHZ_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/tearsofsteel_aac_2ch_44kHz_aac_1ch_44kHz_5sec";
  private static final String MKV_TEARS_OF_STEEL_ASSET_SRT_SUBTITLES_ENG_FRENCH_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/tearsofsteel_srt_subtitles_eng_fre_5sec";
  private static final String MKV_TEARS_OF_STEEL_ASSET_SSA_SUBTITLES_ENG_FRENCH_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/tearsofsteel_ssa_subtitles_eng_fre_5sec";
  private static final String MP3_ELEPHANTSDREAM_2CH_48KHZ_URI_STRING =
      "android.resource://android.media.cujsmalltest.cts/raw/ElephantsDream_2ch_48Khz_15s";

  CujTestParam mCujTestParam;
  private final String mTestType;

  public CtsMediaShortFormPlaybackTest(CujTestParam cujTestParam, String testType) {
    super(cujTestParam.getPlayerListener());
    mCujTestParam = cujTestParam;
    this.mTestType = testType;
  }

  /**
   * Returns the list of parameters
   */
  @Parameterized.Parameters(name = "{index}_{1}")
  public static Collection<Object[]> input() {
    // CujTestParam, testId
    final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
        {CujTestParam.builder().setMediaUrls(prepareHevc_720p_300secVideoPlaylist())
            .setDuration(PLAYLIST_DURATION_THREE_HUNDRED_SEC).setOverhead(TEST_OVERHEAD)
            .setPlayerListener(new PlaybackTestPlayerListener()).build(),
            "Hevc_720p_15sec_20clips"},
        {CujTestParam.builder().setMediaUrls(prepareHevc_720p_300secVideoPlaylist())
            .setDuration(PLAYLIST_DURATION_THREE_HUNDRED_SEC).setOverhead(TEST_OVERHEAD.plus(
                SEEK_OVERHEAD_PER_CLIP.multipliedBy(prepareHevc_720p_300secVideoPlaylist().size())))
            .setPlayerListener(new SeekTestPlayerListener(SEEK_ITERATIONS, SEEK_DURATION,
                Duration.ofSeconds(9) /* messagePosition */)).build(),
            "Hevc_720p_15sec_20clips_seekTest"},
        {CujTestParam.builder().setMediaUrls(prepareHevc_480p_20secVideoPlaylist())
            .setDuration(PLAYLIST_DURATION_TWENTY_SEC).setOverhead(TEST_OVERHEAD).setPlayerListener(
                new OrientationTestPlayerListener(MESSAGE_POSITION_THREE_SEC)).build(),
            "Hevc_480p_5sec_4clips_OrientationTest"},
        {CujTestParam.builder().setMediaUrls(prepareAvc_1080p_15secVideoPlaylist())
            .setDuration(Duration.ofSeconds(15) /* playlistDuration */).setOverhead(TEST_OVERHEAD)
            .setPlayerListener(
                new OrientationTestPlayerListener(MESSAGE_POSITION_THREE_SEC)).build(),
            "Avc_1080p_5sec_3clips_OrientationTest"},
        {CujTestParam.builder().setMediaUrls(prepareAvc_360x640_20secVideoPlaylist())
            .setDuration(PLAYLIST_DURATION_TWENTY_SEC).setOverhead(TEST_OVERHEAD).setPlayerListener(
                new OrientationTestPlayerListener(MESSAGE_POSITION_THREE_SEC)).build(),
            "Avc_360x640_5sec_4clips_OrientationTest"},
        {CujTestParam.builder().setMediaUrls(prepareHevc_480p_10secVideoPlaylistForScrollTest())
            .setDuration(Duration.ofSeconds(10) /* playlistDuration */).setOverhead(TEST_OVERHEAD)
            .setPlayerListener(new ScrollTestPlayerListener(SCROLL_ITERATIONS,
                Duration.ofSeconds(2) /* messagePosition */)).build(),
            "Avc_360x640_5sec_2clips_ScrollTest"},
        {CujTestParam.builder()
            .setMediaUrls(prepare_Aac_2ch_44khz_Aac_1ch_44khz_5secVideoPlaylist())
            .setDuration(CLIP_DURATION_FIVE_SEC).setOverhead(TEST_OVERHEAD).setPlayerListener(
                new SwitchAudioTrackTestPlayerListener(NUMBER_OF_AUDIO_TRACKS,
                    MESSAGE_POSITION_THREE_SEC)).build(),
            "Aac_2ch_44kHz_Aac_1ch_44kHz_5sec_SwitchAudioTracksTest"},
        {CujTestParam.builder().setMediaUrls(prepare_Srt_Subtitles_Eng_French_5secVideoPlaylist())
            .setDuration(CLIP_DURATION_FIVE_SEC).setOverhead(TEST_OVERHEAD).setPlayerListener(
                new SwitchSubtitleTrackTestPlayerListener(NUMBER_OF_SUBTITLE_TRACKS,
                    MESSAGE_POSITION_THREE_SEC)).build(),
            "Srt_Subtitle_eng_french_5sec_SwitchSubtitleTracksTest"},
        {CujTestParam.builder().setMediaUrls(prepare_Ssa_Subtitles_Eng_French_5secVideoPlaylist())
            .setDuration(CLIP_DURATION_FIVE_SEC).setOverhead(TEST_OVERHEAD).setPlayerListener(
                new SwitchSubtitleTrackTestPlayerListener(NUMBER_OF_SUBTITLE_TRACKS,
                    MESSAGE_POSITION_THREE_SEC)).build(),
            "Ssa_Subtitle_eng_french_5sec_SwitchSubtitleTracksTest"},
        {CujTestParam.builder().setMediaUrls(prepareHevc_720p_15sec_SingleVideoPlaylist())
            .setDuration(CLIP_DURATION_FIFTEEN_SEC).setOverhead(TEST_OVERHEAD).setPlayerListener(
                new PinchToZoomTestPlayerListener(MESSAGE_POSITION_THREE_SEC)).build(),
            "Hevc_720p_15sec_PinchToZoomTest"},
        {CujTestParam.builder().setMediaUrls(prepareHevc_720p_15sec_SingleVideoPlaylist())
            .setDuration(CLIP_DURATION_FIFTEEN_SEC).setOverhead(TEST_OVERHEAD)
            .setPlayerListener(new PipModeTestPlayerListener(MESSAGE_POSITION_FIVE_SEC)).build(),
            "Hevc_720p_15sec_PipModeTest"},
        {CujTestParam.builder().setMediaUrls(prepareHevc_720p_15sec_SingleVideoPlaylist())
            .setDuration(CLIP_DURATION_FIFTEEN_SEC).setOverhead(TEST_OVERHEAD).setPlayerListener(
                new SplitScreenTestPlayerListener(MESSAGE_POSITION_FIVE_SEC)).build(),
            "Hevc_720p_15sec_SplitScreenTest"},
        {CujTestParam.builder().setMediaUrls(prepareHevc_720p_15sec_SingleVideoPlaylist())
            .setDuration(CLIP_DURATION_FIFTEEN_SEC).setOverhead(TEST_OVERHEAD.plus(LOCK_DURATION))
            .setPlayerListener(new DeviceLockTestPlayerListener(MESSAGE_POSITION_THREE_SEC,
                false /* isAudioOnlyClip */)).build(), "Hevc_720p_15sec_DeviceLockTest"},
        {CujTestParam.builder().setMediaUrls(prepareMp3_15secAudioPlaylistForDeviceLockTest())
            .setDuration(CLIP_DURATION_FIFTEEN_SEC).setOverhead(TEST_OVERHEAD).setPlayerListener(
                new DeviceLockTestPlayerListener(MESSAGE_POSITION_THREE_SEC,
                    true /* isAudioOnlyClip */)).build(), "Mp3_15sec_DeviceLockTest"},
        {CujTestParam.builder().setMediaUrls(prepareHevc_720p_15sec_SingleVideoPlaylist())
            .setDuration(CLIP_DURATION_FIFTEEN_SEC).setOverhead(TEST_OVERHEAD.plus(LOCK_DURATION))
            .setPlayerListener(new LockPlaybackControllerTestPlayerListener(
                Duration.ofSeconds(6) /* messagePosition */)).build(),
            "Hevc_720p_15sec_LockPlaybackTest"},
        {CujTestParam.builder().setMediaUrls(prepareSineWave_70secAudioPlaylist())
            .setDuration(Duration.ofSeconds(70) /* clipDuration */).setOverhead(TEST_OVERHEAD)
            .setPlayerListener(new AudioOffloadTestPlayerListener()).build(),
            "Mp3_Sine_AudioOffloadTest"},
    }));
    return exhaustiveArgsList;
  }

  /**
   * Prepare Hevc 720p 300sec video playlist. The playlist has 20 clips each of 15sec.
   */
  public static List<String> prepareHevc_720p_300secVideoPlaylist() {
    List<String> videoInput = Arrays.asList(
        MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERMELTDOWN_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERBLAZEA_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERMELTDOWN_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERBLAZEA_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERMELTDOWN_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERBLAZEA_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERMELTDOWN_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERBLAZEA_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERMELTDOWN_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERBLAZEA_ASSET_720P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_720P_HEVC_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare Hevc 480p 20sec video playlist. The playlist has 4 clips each of 5sec.
   */
  public static List<String> prepareHevc_480p_20secVideoPlaylist() {
    List<String> videoInput = Arrays.asList(
        MP4_FORBIGGERJOYRIDES_ASSET_480P_HEVC_URI_STRING,
        MP4_FORBIGGERMELTDOWN_ASSET_480P_HEVC_URI_STRING,
        MP4_FORBIGGERBLAZEA_ASSET_480P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_480P_HEVC_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare Avc 1080p 15sec video playlist. The playlist has 3 clips each of 5sec.
   */
  public static List<String> prepareAvc_1080p_15secVideoPlaylist() {
    List<String> videoInput = Arrays.asList(
        MP4_FORBIGGERMELTDOWN_ASSET_1080P_AVC_URI_STRING,
        MP4_ELEPHANTSDREAM_ASSET_1080P_AVC_URI_STRING,
        MP4_BIGBUCKBUNNY_ASSET_1080P_AVC_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare Avc 360x640 20sec video playlist. The playlist has 4 clips each of 5sec.
   */
  public static List<String> prepareAvc_360x640_20secVideoPlaylist() {
    List<String> videoInput = Arrays.asList(
        MP4_BIGBUCKBUNNY_ASSET_360x640_AVC_URI_STRING,
        MP4_ELEPHANTSDREAM_ASSET_360x640_AVC_URI_STRING,
        MP4_BIGBUCKBUNNY_ASSET_360x640_AVC_URI_STRING,
        MP4_ELEPHANTSDREAM_ASSET_360x640_AVC_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare Hevc 480p 10sec video playlist for Scroll Test. The playlist has 2 clips each of 5sec.
   */
  public static List<String> prepareHevc_480p_10secVideoPlaylistForScrollTest() {
    List<String> videoInput = Arrays.asList(
        MP4_FORBIGGERBLAZEA_ASSET_480P_HEVC_URI_STRING,
        MP4_FORBIGGERESCAPES_ASSET_480P_HEVC_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare multiple audio tracks 5sec video list.
   */
  public static List<String> prepare_Aac_2ch_44khz_Aac_1ch_44khz_5secVideoPlaylist() {
    List<String> videoInput = Arrays.asList(
        MKV_TEARS_OF_STEEL_ASSET_AAC_2CH_44KHZ_AAC_1CH_44KHZ_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare multiple srt subtitle tracks 5sec video list.
   */
  public static List<String> prepare_Srt_Subtitles_Eng_French_5secVideoPlaylist() {
    List<String> videoInput = Arrays.asList(
        MKV_TEARS_OF_STEEL_ASSET_SRT_SUBTITLES_ENG_FRENCH_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare multiple ssa subtitle tracks 5sec video list.
   */
  public static List<String> prepare_Ssa_Subtitles_Eng_French_5secVideoPlaylist() {
    List<String> videoInput = Arrays.asList(
        MKV_TEARS_OF_STEEL_ASSET_SSA_SUBTITLES_ENG_FRENCH_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare Hevc 720p 15sec single video list.
   */
  public static List<String> prepareHevc_720p_15sec_SingleVideoPlaylist() {
    List<String> videoInput = Arrays.asList(
        MP4_FORBIGGERJOYRIDES_ASSET_720P_HEVC_URI_STRING);
    return videoInput;
  }

  /**
   * Prepare Mp3 15sec audio list for Device Lock Test.
   */
  public static List<String> prepareMp3_15secAudioPlaylistForDeviceLockTest() {
    List<String> audioInput = Arrays.asList(
        MP3_ELEPHANTSDREAM_2CH_48KHZ_URI_STRING);
    return audioInput;
  }

  /**
   * Prepare sine wave 70sec audio list.
   */
  public static List<String> prepareSineWave_70secAudioPlaylist() {
    List<String> audioInput = Arrays.asList(
        MP3_SINE_ASSET_1KHZ_40DB_LONG_URI_STRING);
    return audioInput;
  }


  // Test to Verify video playback with and without seek
  @ApiTest(apis = {"android.media.MediaCodec#configure",
      "android.media.MediaCodec#createByCodecName",
      "android.media.MediaCodecInfo#getName",
      "android.media.MediaCodecInfo#getSupportedTypes",
      "android.media.MediaCodecInfo#isSoftwareOnly"})
  @Test
  @PlatinumTest(focusArea = "media")
  public void testVideoPlayback() throws Exception {
    /* TODO(b/339628718, b/338342633) */
    Assume.assumeFalse("Split screen test is skipped",
        mCujTestParam.getPlayerListener().isSplitScreenTest());
    if (mCujTestParam.getPlayerListener().isOrientationTest()) {
      Assume.assumeTrue("Skipping " + mTestType + " as device doesn't support orientation change.",
          !OrientationTestPlayerListener.getIgnoreOrientationRequest()
              && supportOrientationRequest(mActivity));
    }
    if (mCujTestParam.getPlayerListener().isPinchToZoomTest()) {
      Assume.assumeFalse("Skipping " + mTestType + " as watch doesn't support zoom behaviour yet",
          isWatchDevice(mActivity));
    }
    if (mCujTestParam.getPlayerListener().isPipTest()) {
      Assume.assumeFalse(
          "Skipping " + mTestType + " as watch doesn't support picture-in-picture mode yet",
          isWatchDevice(mActivity));
      Assume.assumeTrue(
          "Skipping " + mTestType + " as device doesn't support picture-in-picture feature",
          deviceSupportPipMode(mActivity));
    }
    if (mCujTestParam.getPlayerListener().isSplitScreenTest()) {
      Assume.assumeTrue("Skipping " + mTestType + " as device doesn't support split screen feature",
          deviceSupportSplitScreenMode(mActivity));
    }
    if (mCujTestParam.getPlayerListener().getTestType()
        .equals(TestType.LOCK_PLAYBACK_CONTROLLER_TEST)) {
      Assume.assumeFalse("Skipping " + mTestType + " on watch", isWatchDevice(mActivity));
    }
    if (mCujTestParam.getPlayerListener().getTestType().equals(TestType.DEVICE_LOCK_TEST)) {
      Assume.assumeFalse("Skipping " + mTestType + " on watch", isWatchDevice(mActivity));
      Assume.assumeFalse("Skipping " + mTestType + " on television", isTelevisionDevice(mActivity));
      // Skipping DEVICE_LOCK_TEST for secondary_user_on_secondary_display, because currently
      // there is no way to send a "press sleep/wake button" event to a secondary display.
      Assume.assumeFalse("Skipping " + mTestType + " for a visible background user"
              + " as individual lock/unlock on a secondary screen is not supported.",
              isVisibleBackgroundNonProfileUser(mActivity));
    }
    if (mCujTestParam.getPlayerListener().isAudioOffloadTest()) {
      Assume.assumeTrue("Skipping " + mTestType + " as device doesn't support audio offloading",
          deviceSupportAudioOffload(AudioFormat.ENCODING_MP3));
    }
    play(mCujTestParam.getMediaUrls(),
        mCujTestParam.getDuration().plus(mCujTestParam.getOverhead()));
  }
}
