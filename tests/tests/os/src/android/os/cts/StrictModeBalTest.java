/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.os.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNoException;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.StrictMode;
import android.os.UserManager;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.runner.AndroidJUnit4;

import com.android.window.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link StrictMode} */
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class StrictModeBalTest extends StrictModeTestBase {
    private static final String BACKGROUND_ACTIVITY_LAUNCH = "BackgroundActivityLaunch";

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BAL_STRICT_MODE_RO)
    public void testBackgroundBalAborted_ThrowsViolation() throws Exception {
        assumeNotHeadlessSystemUserMode();
        StrictMode.setVmPolicy(
                new StrictMode.VmPolicy.Builder()
                        .detectBlockedBackgroundActivityLaunch()
                        .penaltyLog()
                        .build());
        Context context = getContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED);
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        PendingIntent pi =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        assertViolation(
                BACKGROUND_ACTIVITY_LAUNCH, () -> sendPendingIntentIgnoringErrors(options, pi));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BAL_STRICT_MODE_RO)
    public void testBackgroundBalAborted_IgnoresViolation() throws Exception {
        assumeNotHeadlessSystemUserMode();
        StrictMode.setVmPolicy(
                new StrictMode.VmPolicy.Builder()
                        .detectAll()
                        .ignoreBlockedBackgroundActivityLaunch()
                        .penaltyLog()
                        .build());
        Context context = getContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED);
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        PendingIntent pi =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        assertThat(pi).isNotNull();
        assertNoViolation(() -> sendPendingIntentIgnoringErrors(options, pi));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BAL_STRICT_MODE_RO)
    public void testBackgroundBalAborted_NoViolation() throws Exception {
        assumeNotHeadlessSystemUserMode();
        StrictMode.setVmPolicy(
                new StrictMode.VmPolicy.Builder()
                        .detectBlockedBackgroundActivityLaunch()
                        .penaltyLog()
                        .build());
        Context context = getContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        PendingIntent pi =
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        assertThat(pi).isNotNull();
        assertNoViolation(() -> sendPendingIntentIgnoringErrors(options, pi));
    }

    private static void sendPendingIntentIgnoringErrors(ActivityOptions options, PendingIntent pi) {
        try {
            pi.send(options.toBundle());
        } catch (PendingIntent.CanceledException e) {
            // This typically happens when the Activity for the PendingIntent cannot be resolved.
            assumeNoException("PendingIntent was cancelled", e);
        }
    }

    private static void assumeNotHeadlessSystemUserMode() {
        assumeFalse(
                "Skipping test not supported on HSUM devices.",
                UserManager.isHeadlessSystemUserMode());
    }
}
