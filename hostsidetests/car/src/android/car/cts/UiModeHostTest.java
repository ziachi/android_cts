/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.car.cts;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

/**
 * Check car config consistency across day night mode switching.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class UiModeHostTest extends CarHostJUnit4TestCase {

    private static final Pattern NIGHT_MODE_REGEX = Pattern.compile("Night mode: (yes|no)");

    /**
     * Test day/night mode consistency across user switching. Day/night mode config should be
     * persistent across user switching.
     */
    @Test
    public void testUserSwitchingConfigConsistency() throws Exception {
        requiresExtraUsers(1);

        int originalUserId = getCurrentUserId();
        int newUserId = createFullUser("UiModeHostTest");

        // start current user in day mode
        setDayMode();
        waitUntilDayMode();

        // set to night mode
        setNightMode();
        waitUntilNightMode();

        // switch to new user and verify night mode
        switchUser(newUserId);
        waitForUserSwitchCompleted(newUserId);
        waitUntilNightMode();

        // set to day mode
        setDayMode();
        waitUntilDayMode();

        // switch back to initial user and verify day mode
        switchUser(originalUserId);
        waitForUserSwitchCompleted(originalUserId);
        waitUntilDayMode();
    }

    /**
     * Sets the UI mode to day mode.
     */
    private void setDayMode() throws Exception {
        executeCommand("cmd car_service day-night-mode day");
    }

    /**
     * Sets the UI mode to night mode.
     */
    private void setNightMode() throws Exception {
        executeCommand("cmd car_service day-night-mode night");
    }

    /**
     * Returns true if the current UI mode is night mode, false otherwise.
     */
    private boolean isNightMode() throws Exception {
        return executeAndParseCommand(NIGHT_MODE_REGEX,
                "get night mode status failed",
                matcher -> matcher.group(1).equals("yes"),
                "cmd uimode night");
    }

    private void waitUntilNightMode() throws Exception {
        waitUntil(() -> isNightMode(), "the night mode");
    }

    private void waitUntilDayMode() throws Exception {
        waitUntil(() -> !isNightMode(), "the day mode");
    }
}
