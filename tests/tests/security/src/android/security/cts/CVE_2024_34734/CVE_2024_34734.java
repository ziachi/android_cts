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

package android.security.cts.CVE_2024_34734;

import static android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.platform.test.annotations.AsbSecurityTest;
import android.security.cts.R;
import android.service.notification.StatusBarNotification;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.view.KeyEvent;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.LockSettingsUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_34734 extends StsExtraBusinessLogicTestCase {
    final long mTimeout = 5_000L;
    UiDevice mUiDevice;

    @Test
    @AsbSecurityTest(cveBugId = 304772709)
    public void testPocCVE_2024_34734() {
        try {
            Instrumentation instrumentation = getInstrumentation();
            Context context = instrumentation.getContext();
            final int sdkInt = Build.VERSION.SDK_INT;
            final int tiramisuSdkInt = 33;

            // Register a broadcast receiver for catching exceptions from PocService
            final CompletableFuture<Exception> pocServiceException =
                    new CompletableFuture<Exception>();
            context.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            try {
                                pocServiceException.complete(
                                        (Exception)
                                                intent.getSerializableExtra(
                                                        context.getString(
                                                                R.string.CVE_2024_34734_key)));
                            } catch (Exception e) {
                                // Ignore exceptions here
                            }
                        }
                    },
                    new IntentFilter(context.getString(R.string.CVE_2024_34734_action)),
                    sdkInt >= tiramisuSdkInt
                            ? (int) Context.class.getField("RECEIVER_EXPORTED").get(context)
                            : 0);

            // Enable screen lock and grant FOREGROUND_SERVICE_MICROPHONE permission
            try (AutoCloseable withLockScreenCloseable =
                            new LockSettingsUtil(context).withLockScreen();
                    AutoCloseable withPermission =
                            withForegroundPermission(instrumentation.getUiAutomation())) {
                // Screen lock the device and verify
                mUiDevice = UiDevice.getInstance(instrumentation);
                mUiDevice.pressKeyCode(KeyEvent.KEYCODE_SLEEP);
                mUiDevice.pressKeyCode(KeyEvent.KEYCODE_WAKEUP);
                KeyguardManager keyguardManager = context.getSystemService(KeyguardManager.class);
                assume().withMessage("Unable to screen lock the device")
                        .that(
                                poll(
                                        () -> {
                                            return keyguardManager.isDeviceLocked();
                                        }))
                        .isTrue();

                // Launch PocService and check for any exceptions
                context.startService(new Intent(context, PocService.class));
                assume().that(pocServiceException.get(mTimeout, TimeUnit.MILLISECONDS))
                        .isNull();

                // Wait for PocService's notification
                final NotificationManager notificationManager =
                        context.getSystemService(NotificationManager.class);
                final String selfPackageName = context.getPackageName();
                assume().withMessage("PocService's notification was not posted")
                        .that(
                                poll(
                                        () -> {
                                            StatusBarNotification[] activeNotifications =
                                                    notificationManager.getActiveNotifications();
                                            for (StatusBarNotification notification :
                                                    activeNotifications) {
                                                if (notification
                                                        .getPackageName()
                                                        .equals(selfPackageName)) {
                                                    return true;
                                                }
                                            }
                                            return false;
                                        }))
                        .isTrue();

                // Retrieve package name of systemUI app
                final ComponentName componentName =
                        new Intent(Intent.ACTION_SHOW_BRIGHTNESS_DIALOG)
                                .resolveActivity(context.getPackageManager());
                final String systemUiPackageName =
                        componentName != null
                                ? componentName.getPackageName()
                                : "com.android.systemui";

                // Launch quick settings of statusbar and launch foreground service manager dialog
                // (Active apps)
                assume().withMessage("Unable to open quick settings panel of statusbar")
                        .that(mUiDevice.openQuickSettings())
                        .isTrue();
                clickUiObject(
                        mUiDevice,
                        By.res(
                                systemUiPackageName,
                                sdkInt > tiramisuSdkInt ? "chevron_icon" : "footer_icon"));

                // Fail the test if the foreground service manager dialog (Active apps) gets
                // launched from lock screen.
                // Without fix, the dialog appears within ~500 ms. keeping a timeout of 5 seconds to
                // support slower devices.
                assertWithMessage(
                                "Device is vulnerable to b/304772709 !! Foreground services can be"
                                        + " stopped from the lock screen.")
                        .that(
                                mUiDevice.wait(
                                        Until.findObject(
                                                By.text(
                                                                Pattern.compile(
                                                                        selfPackageName,
                                                                        Pattern.CASE_INSENSITIVE))
                                                        .res(
                                                                systemUiPackageName,
                                                                "fgs_manager_app_item_label")),
                                        mTimeout))
                        .isNull();
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        } finally {
            try {
                mUiDevice.pressBack();
            } catch (Exception ignore) {
                // Ignore exceptions while cleanup
            }
        }
    }

    private boolean clickUiObject(UiDevice mUiDevice, BySelector selector) throws Exception {
        final UiObject2 uiobject = mUiDevice.wait(Until.findObject(selector), mTimeout);
        if (uiobject != null) {
            assume().withMessage("UI Object: " + selector.toString() + " was not enabled")
                    .that(poll(() -> (uiobject.isEnabled())))
                    .isTrue();
            uiobject.click();
        }
        return (uiobject != null);
    }

    private AutoCloseable withForegroundPermission(UiAutomation uiAutomation) throws Exception {
        uiAutomation.adoptShellPermissionIdentity(FOREGROUND_SERVICE_MICROPHONE);
        return () -> uiAutomation.dropShellPermissionIdentity();
    }
}
