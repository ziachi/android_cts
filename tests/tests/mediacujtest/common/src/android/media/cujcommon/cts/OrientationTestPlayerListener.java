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

import static android.media.cujcommon.cts.CujTestBase.ORIENTATIONS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.media3.common.Player;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import java.io.IOException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrientationTestPlayerListener extends PlayerListener {
  private static final String WM_GET_IGNORE_ORIENTATION_REQUEST =
          "wm get-ignore-orientation-request";
  private static final Pattern IGNORE_ORIENTATION_REQUEST_PATTERN =
          Pattern.compile("ignoreOrientationRequest (true|false) for displayId=\\d+");

  private boolean mOrientationChangeRequested;

  public OrientationTestPlayerListener(Duration sendMessagePosition) {
    super();
    this.mSendMessagePosition = sendMessagePosition;
  }

  /**
   * Returns true if IgnoreOrientationRequest is set.
   */
  public static boolean getIgnoreOrientationRequest() {
    Matcher matcher = IGNORE_ORIENTATION_REQUEST_PATTERN.matcher(
        executeShellCommand(WM_GET_IGNORE_ORIENTATION_REQUEST));
    assertTrue("get-ignore-orientation-request should match pattern", matcher.find());
    return Boolean.parseBoolean(matcher.group(1));
  }

  /**
   * Execute shell command.
   */
  private static String executeShellCommand(String command) {
    try {
      return SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
              command);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Change the Orientation of the device.
   */
  private void changeOrientation() {
    mPreviousOrientation = ORIENTATIONS[mOrientationIndex];
    mOrientationIndex = (mOrientationIndex + 1) % ORIENTATIONS.length;
    mActivity.setRequestedOrientation(ORIENTATIONS[mOrientationIndex]);
    mOrientationChangeRequested = true;
  }

  /**
   * Verify Orientation change.
   */
  private void verifyOrientationChange() {
    int configuredOrientation = ORIENTATIONS[mOrientationIndex];
    int currentDeviceOrientation = getDeviceOrientation(mActivity);
    assertEquals(configuredOrientation, currentDeviceOrientation);
    assertNotEquals(mPreviousOrientation, currentDeviceOrientation);
    mOrientationChangeRequested = false;
  }

  @Override
  public TestType getTestType() {
    return TestType.ORIENTATION_TEST;
  }

  @Override
  public void onEventsPlaybackStateChanged(@NonNull Player player) {
    if (mExpectedTotalTime == 0 && player.getPlaybackState() == Player.STATE_READY) {
      // update to latest cl which fixes orientation bug
      // At the first media transition player is not ready. So, add duration of
      // first clip when player is ready
      mExpectedTotalTime += player.getDuration();
    } else if (mOrientationChangeRequested && player.getPlaybackState() == Player.STATE_ENDED) {
      verifyOrientationChange();
    }
  }

  @Override
  public void onEventsMediaItemTransition(@NonNull Player player) {
    if (mOrientationChangeRequested) {
      verifyOrientationChange();
    }
    mActivity.mPlayer.createMessage((messageType, payload) -> {
          changeOrientation();
        }).setLooper(Looper.getMainLooper()).setPosition(mSendMessagePosition.toMillis())
        .setDeleteAfterDelivery(true)
        .send();
  }
}
