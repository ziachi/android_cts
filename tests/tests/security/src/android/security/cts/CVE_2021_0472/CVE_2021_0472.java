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

package android.security.cts.CVE_2021_0472;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.sts.common.SystemUtil.poll;
import static com.android.sts.common.SystemUtil.withSetting;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.LockSettingsUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CVE_2021_0472 extends StsExtraBusinessLogicTestCase {
    static final String CVE_2021_0472_ACTION = "cve_2021_0472_action";
    static final String TASK_ID = "cve_2021_0472_taskId";
    static final String EXCEPTION_MSG_KEY = "cve_2021_0472_exceptionMsgKey";

    private int mTaskId = -1;

    @AsbSecurityTest(cveBugId = 176801033)
    @Test
    public void testPocCVE_2021_0472() {
        Instrumentation instrumentation = null;
        Context context = null;
        try {
            final int pollFreqMs = 100;
            final int pollTimeoutMs = 5000;
            instrumentation = getInstrumentation();
            context = instrumentation.getContext();
            KeyguardManager keyguardManager = context.getSystemService(KeyguardManager.class);
            final ActivityManager activityManager = context.getSystemService(ActivityManager.class);

            // Fetch and add the flag 'RECEIVER_EXPORTED' for 'TIRAMISU' and above versions to
            // keep the code consistent
            final int requiredFlag =
                    Build.VERSION.SDK_INT >= 33 /* TIRAMISU */
                            ? (int) Context.class.getField("RECEIVER_EXPORTED").get(context)
                            : 0;

            // Register broadcast receiver to receive broadcasts from PocActivity
            CompletableFuture<Exception> exceptionMessage = new CompletableFuture<>();

            BroadcastReceiver broadcastReceiver =
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            try {
                                mTaskId = intent.getIntExtra(TASK_ID, -1);
                                exceptionMessage.complete(
                                        (Exception)
                                                getSerializableExtra(
                                                        intent,
                                                        EXCEPTION_MSG_KEY,
                                                        Exception.class));
                            } catch (Exception ignored) {
                                // Ignoring unintended exceptions
                            }
                        }
                    };
            context.registerReceiver(
                    broadcastReceiver, new IntentFilter(CVE_2021_0472_ACTION), requiredFlag);

            // Launch PocActivity and get its mTaskId
            context.startActivity(
                    new Intent(context, PocActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            // Blocking while the complete is called on variable exceptionMessage
            assume().that(exceptionMessage.get(5, TimeUnit.SECONDS)).isNull();
            assume().that(mTaskId != -1).isTrue();

            try (AutoCloseable withLockScreenCloseable =
                            new LockSettingsUtil(context).withLockScreen();
                    AutoCloseable withSettingAutoCloseable =
                            withSetting(
                                    instrumentation, "secure", "lock_to_app_exit_locked", "1")) {
                // Pin PocActivity using retrieved mTaskId
                SystemUtil.runShellCommand(instrumentation, "am task lock " + mTaskId);

                // Wait for app to get pinned
                assume().that(
                                poll(
                                        () ->
                                                activityManager.getLockTaskModeState()
                                                        == ActivityManager.LOCK_TASK_MODE_PINNED,
                                        pollFreqMs,
                                        pollTimeoutMs))
                        .isTrue();

                // Delete lock_to_app_exit_locked settings to catch an exception in
                // shouldLockKeyguard() of lockTaskController.java. This will execute the
                // vulnerable code while unpinning.
                SystemUtil.runShellCommand(
                        instrumentation, "settings delete secure lock_to_app_exit_locked");

                // Unpin the previously pinned app
                SystemUtil.runShellCommand(instrumentation, "am task lock stop");

                // Wait for keyguard to get locked
                boolean isLocked =
                        poll(() -> keyguardManager.isKeyguardLocked(), pollFreqMs, pollTimeoutMs);

                boolean isPinned =
                        activityManager.getLockTaskModeState()
                                == ActivityManager.LOCK_TASK_MODE_PINNED;

                // Check if unpin is successful
                assume().that(isPinned).isFalse();

                // Check if keyguard is locked
                assertWithMessage("Vulnerable to b/176801033 !!").that(isLocked).isTrue();
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        } finally {
            try {
                // Revert the side-effects of command "am task lock"
                if (context.getSystemService(ActivityManager.class).getLockTaskModeState()
                        != ActivityManager.LOCK_TASK_MODE_PINNED) {
                    SystemUtil.runShellCommand(instrumentation, "am task lock stop");
                }

                // Goto HOME from lockscreen
                SystemUtil.runShellCommand(instrumentation, "input keyevent KEYCODE_HOME");
                SystemUtil.runShellCommand(instrumentation, "input keyevent KEYCODE_WAKEUP");
                SystemUtil.runShellCommand(instrumentation, "wm dismiss-keyguard");
                SystemUtil.runShellCommand(instrumentation, "input keyevent KEYCODE_POWER");
            } catch (Exception e) {
                // Ignore unintended exceptions here
            }
        }
    }

    private Object getSerializableExtra(Intent intent, String key, Class valueClass)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        if (Build.VERSION.SDK_INT >= 33 /* TIRAMISU */) {
            return Intent.class
                    .getDeclaredMethod("getSerializableExtra", String.class, Class.class)
                    .invoke(intent, key, valueClass);
        }
        return Intent.class
                .getDeclaredMethod("getSerializableExtra", String.class)
                .invoke(intent, key);
    }
}
