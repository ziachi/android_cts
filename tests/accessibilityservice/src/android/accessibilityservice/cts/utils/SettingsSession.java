/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.accessibilityservice.cts.utils;

import android.app.Instrumentation;
import android.app.UiAutomation;

import androidx.annotation.Nullable;
import androidx.core.util.Preconditions;

import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UserSettings;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.io.IOException;

/**
 * A helper class to save, set and restore system settings value easily and reliably.
 *
 * <p>Note: If settings should be enforced in a whole class, consider {@link
 * com.android.compatibility.common.util.SettingsStateChangerRule}.
 */
public class SettingsSession implements AutoCloseable {
    private final UiAutomation mUiAutomation;
    private final int mUserId;
    private final UserSettings.Namespace mNamespace;
    private final String mKey;
    private final String mOriginalValue;

    public SettingsSession(
            Instrumentation instrumentation,
            UserSettings.Namespace namespace,
            String key,
            @Nullable String value)
            throws IOException {
        mUiAutomation =
                instrumentation.getUiAutomation(
                        UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        mUserId = instrumentation.getTargetContext().getUserId();
        mNamespace = namespace;
        mKey = Preconditions.checkNotNull(key);

        mOriginalValue = get();
        if (!mOriginalValue.equals(value)) {
            set(value);
        }
    }

    @Override
    public void close() throws Exception {
        final String currentValue = get();
        if (!currentValue.equals(mOriginalValue)) {
            set(mOriginalValue);
        }
    }

    private String get() throws IOException {
        return runShellCommand("settings get --user %d %s %s", mUserId, mNamespace.get(), mKey);
    }

    private void set(@Nullable String value) throws IOException {
        // Note that an empty string is "set but empty", which is different from null, which is
        // "not set". Also, the get command returns a string "null" if the value is not set.
        if (value == null || value.equals("null")) {
            runShellCommand("settings delete --user %d %s %s", mUserId, mNamespace.get(), mKey);
            return;
        }
        runShellCommand("settings put --user %d %s %s %s", mUserId, mNamespace.get(), mKey, value);
    }

    @FormatMethod
    private String runShellCommand(@FormatString String format, Object... args) throws IOException {
        final String cmd = String.format(format, args);
        return SystemUtil.runShellCommand(mUiAutomation, cmd).strip();
    }
}
