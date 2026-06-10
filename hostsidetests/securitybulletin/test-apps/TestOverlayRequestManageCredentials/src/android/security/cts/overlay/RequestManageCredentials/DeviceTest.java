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

package android.security.cts.overlay_request_manage_credentials;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.security.AppUriAuthenticationPolicy;
import android.security.Credentials;
import android.security.KeyChain;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {
    private static final long TIMEOUT_MS = 10_000L;
    private Exception mException = null;
    private String mVulnActivity;

    private boolean pollOnActivityLaunch(String activityName) throws InterruptedException {
        return poll(() -> checkActivityLaunched(activityName));
    }

    private boolean checkActivityLaunched(String activityName) {
        Pattern resumePattern = Pattern.compile("mResumed=true", Pattern.CASE_INSENSITIVE);
        Pattern visibilityPattern = Pattern.compile("mVisible=true", Pattern.CASE_INSENSITIVE);
        String activityDump = runShellCommand("dumpsys activity -a " + activityName);
        return resumePattern.matcher(activityDump).find()
                && visibilityPattern.matcher(activityDump).find();
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

    @Test
    public void testPocBug_205150380() {
        // Assert that overlay is disallowed by the target activity.
        boolean overlayDisallowed = overlayDisallowedByRequestManageCredentials(false);
        String failMsg =
                "Device is vulnerable to b/205150380 hence any app with "
                        + "SYSTEM_ALERT_WINDOW can overlay the "
                        + mVulnActivity
                        + " screen.";
        assertWithMessage(failMsg).that(overlayDisallowed).isTrue();
    }

    @Test
    public void testPocBug_302431573() {
        // Assert that overlay is disallowed by the target activity.
        boolean overlayDisallowed = overlayDisallowedByRequestManageCredentials(false);
        String failMsg =
                "Device is vulnerable to b/205150380 hence any app with "
                        + "SYSTEM_ALERT_WINDOW can overlay the "
                        + mVulnActivity
                        + " screen. Please note that it might also be vulnerable to b/302431573 "
                        + " since it's a bypass of b/205150380. Hence, please ensure that both the"
                        + " patches for b/205150380 and b/302431573 are applied.";
        assertWithMessage(failMsg).that(overlayDisallowed).isTrue();

        // Assert that overlay is disallowed by the target activity when it's orientation is
        // changed by rotating it this time.
        overlayDisallowed = overlayDisallowedByRequestManageCredentials(true);
        failMsg =
                "Device is vulnerable to b/302431573 hence any app with "
                        + "SYSTEM_ALERT_WINDOW can overlay the "
                        + mVulnActivity
                        + " screen when it's orientation is changed.";
        assertWithMessage(failMsg).that(overlayDisallowed).isTrue();
    }

    private boolean overlayDisallowedByRequestManageCredentials(boolean checkRotated) {
        Context context = null;
        UiDevice device = null;
        boolean overlayDisallowed = true;
        try {
            // Register a receiver to receive any update from other app components.
            // Using the flag 'RECEIVER_NOT_EXPORTED' for API level 33 and above since it's
            // required for API 34 and above and available only from API level 33 onwards and 0
            // otherwise.
            context = getApplicationContext();
            int receiverFlags =
                    Build.VERSION.SDK_INT >= 33 /* TIRAMISU */
                            ? (int) Context.class.getField("RECEIVER_NOT_EXPORTED").get(context)
                            : 0;
            Semaphore waitOnPocActivity = new Semaphore(0);
            context.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            try {
                                String keyException = context.getString(R.string.keyException);
                                if (intent.hasExtra(keyException)) {
                                    mException =
                                            (Exception)
                                                    getSerializableExtra(
                                                            intent, keyException, Exception.class);
                                }
                                if (intent.hasExtra(context.getString(R.string.keyPocActivity))) {
                                    waitOnPocActivity.release();
                                }
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    },
                    new IntentFilter(context.getString(R.string.broadcastAction)),
                    receiverFlags);

            // Start the overlay service
            assume().withMessage("The application cannot draw overlays")
                    .that(Settings.canDrawOverlays(context))
                    .isTrue();
            Intent intent = new Intent(context, PocService.class);
            context.startService(intent);

            // Wait for the overlay window
            Pattern overlayTextPattern =
                    Pattern.compile(
                            context.getString(R.string.overlayBtnTxt), Pattern.CASE_INSENSITIVE);
            device = UiDevice.getInstance(getInstrumentation());
            boolean overlayUiFound =
                    device.wait(Until.hasObject(By.text(overlayTextPattern)), TIMEOUT_MS);
            assume().withMessage("Caught an exception in PocService!").that(mException).isNull();
            assume().withMessage("Overlay UI did not appear on the screen")
                    .that(overlayUiFound)
                    .isTrue();

            intent = new Intent(Credentials.ACTION_MANAGE_CREDENTIALS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            AppUriAuthenticationPolicy policy =
                    new AppUriAuthenticationPolicy.Builder()
                            .addAppAndUriMapping(
                                    context.getPackageName(),
                                    Uri.parse("https://test.com") /* uri */,
                                    "testAlias" /* alias */)
                            .build();
            intent.putExtra(KeyChain.EXTRA_AUTHENTICATION_POLICY, policy);

            // Launch the vulnerable activity
            context.startActivity(intent);
            mVulnActivity = intent.resolveActivity(context.getPackageManager()).flattenToString();
            assume().withMessage(mVulnActivity + " is not currently running on the device")
                    .that(pollOnActivityLaunch(mVulnActivity))
                    .isTrue();

            if (checkRotated) {
                // Launch PocActivity which rotates itself to change the orientation
                intent = new Intent(context, PocActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                assume().withMessage("Broadcast for PocActivity launch not received")
                        .that(
                                waitOnPocActivity.tryAcquire(
                                        10_000L /* timeout */, TimeUnit.MILLISECONDS))
                        .isTrue();
                assume().withMessage("Caught an exception in PocActivity!")
                        .that(mException)
                        .isNull();

                // Waiting for a few more seconds to allow the system to finish the orientation
                // transition
                Thread.sleep(5000);

                // Press back button and wait for vulnerable activity to appear again
                runShellCommand("input keyevent KEYCODE_BACK");
                assume().withMessage(mVulnActivity + " is not currently running on the device")
                        .that(pollOnActivityLaunch(mVulnActivity))
                        .isTrue();
            }

            // Check if the vulnerable activity disallows the overlay
            overlayDisallowed =
                    device.wait(Until.gone(By.pkg(context.getPackageName())), TIMEOUT_MS);

            // Ensure that the vulnerable activity was running and visible while overlay was being
            // shown.
            assume().withMessage(mVulnActivity + " is not currently running on the device")
                    .that(checkActivityLaunched(mVulnActivity))
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        } finally {
            try {
                // Stop PocService to trigger cleanup in onDestroy()
                context.stopService(new Intent(context, PocService.class));

                // Press the home button to ensure that UIAutomator accurately detects the UI for
                // the subsequent test.
                device.pressHome();
            } catch (Exception e) {
                // ignore
            }
        }
        return overlayDisallowed;
    }
}
