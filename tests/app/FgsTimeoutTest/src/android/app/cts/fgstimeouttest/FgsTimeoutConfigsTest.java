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
package android.app.cts.fgstimeouttest;

import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.TAG;

import android.app.Flags;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Containing a test that ensures the default timeouts used for certain time-limited fgs types are
 * larger than the intended values.
 *
 * It's not in the main test class ({@link FgsTimeoutTest}), because that changes the
 * device config settings.
 *
 * Note, this test can fail if `activity_manager` device_config has been changed already on the
 * device.
 */
@Presubmit
public class FgsTimeoutConfigsTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @BeforeClass
    public static void setUpClass() throws Exception {
        ShellUtils.runShellCommand("cmd device_config reset trusted_defaults activity_manager");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_INTRODUCE_NEW_SERVICE_ONTIMEOUT_CALLBACK)
    public void testDefaultTimeouts() {
        // When the main test class resets the device config values, it's propagated asynchronously
        // to ActivityManagerConstants, so we'll just retry up to this many seconds.
        final int timeoutSecond = 30;

        int sleep = 125;
        final long timeout = SystemClock.uptimeMillis() + timeoutSecond * 1000;
        while (true) {
            final Map<String, Long> keyValues = extractFgsTimeoutSettings(
                    "fgs_crash_extra_wait_duration",
                    "data_sync_fgs_timeout_duration",
                    "media_processing_fgs_timeout_duration");
            try {
                assertConfigAtLeast(keyValues, "fgs_crash_extra_wait_duration", 10_000);
                assertConfigAtLeast(keyValues,
                        "data_sync_fgs_timeout_duration", 6 * 60 * 60_000);
                assertConfigAtLeast(keyValues,
                        "media_processing_fgs_timeout_duration", 6 * 60 * 60_000);
                return;
            } catch (Throwable th) {
                if (SystemClock.uptimeMillis() >= timeout) {
                    throw th;
                }
            }

            SystemClock.sleep(sleep);
            sleep *= 5;
            sleep = Math.min(2000, sleep);
        }
    }

    /**
     * Extract the specified keys from `dumpsys activity settings` and return them as a map.
     */
    private Map<String, Long> extractFgsTimeoutSettings(String... keys) {
        final String dumpsys = ShellUtils.runShellCommand("dumpsys activity settings");
        final Map<String, Long> matches = new HashMap<>();

        final Set<Pattern> patterns = new HashSet<>();
        for (String key : keys) {
            patterns.add(Pattern.compile("^\\s*(" + key + ")=(\\d+)"));
        }

        for (String line : dumpsys.split("\\n", -1)) {
            try {
                for (Pattern pattern : patterns) {
                    final Matcher m = pattern.matcher(line);
                    if (!m.matches()) {
                        continue;
                    }
                    final String key = m.group(1);
                    final long value = Long.parseLong(m.group(2)); // Should always succeed.
                    Log.d(TAG, "Found setting: " + key + " = " + value);
                    matches.put(key, value);
                    break;
                }
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to parse a setting line. Last line read: " + line, e);
            }
        }
        return matches;
    }

    private void assertConfigAtLeast(Map<String, Long> configs, String config, long minimum) {
        final Long value = configs.get(config);
        if (value == null) {
            Assert.fail("Unable to find config \"" + config + "\" from dumpsys activity settings");
        }
        if (value < minimum) {
            Assert.fail("Config \"" + config + "\" expected to be >= " + minimum + ", but was: "
                    + value);
        }
    }
}
