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

package android.server.wm.jetpack.embedding;

import android.graphics.Point;
import android.server.wm.ActivityManagerTestBase.VirtualDisplaySession;
import android.server.wm.WindowManagerState;

import androidx.annotation.NonNull;

/**
 * A helper class to manage secondary display for testing.
 */
public class MultiDisplayTestHelper {

    public static final Point SIMULATED_DISPLAY_SIZE = new Point(1920, 1080);

    public static final int SIMULATED_DISPLAY_DENSITY_DP = 160;

    private final VirtualDisplaySession mSession;

    private int mSecondaryDisplayId;

    public MultiDisplayTestHelper(@NonNull VirtualDisplaySession session) {
        mSession = session;
    }

    /**
     * Sets up a secondary display for testing.
     * <p>
     * Note that this method should be put before
     * {@link ActivityEmbeddingTestBase#setUp() super.setUp} so that {@code mReportedDisplayMetrics}
     * could be set correctly.
     */
    public void setUpTestDisplay() {
        final WindowManagerState.DisplayContent display =
                createLandscapeLargeScreenSimulatedDisplay(mSession);
        mSecondaryDisplayId = display.mId;
    }

    /**
     * Release the display instance.
     * <p>
     * This method is usually called in {@code tearDown}.
     */
    public void releaseDisplay() {
        mSession.close();
    }

    /**
     * Returns the display ID of created display.
     */
    public int getSecondaryDisplayId() {
        return mSecondaryDisplayId;
    }

    /**
     * Creates a landscape large screen simulated display to verify AE on multi-display environment.
     */
    public static WindowManagerState.DisplayContent createLandscapeLargeScreenSimulatedDisplay(
            @NonNull VirtualDisplaySession virtualDisplaySession) {
        return virtualDisplaySession
                .setSimulateDisplay(true)
                .setSimulationDisplaySize(SIMULATED_DISPLAY_SIZE.x, SIMULATED_DISPLAY_SIZE.y)
                .setDensityDpi(SIMULATED_DISPLAY_DENSITY_DP)
                .createDisplay();
    }
}
