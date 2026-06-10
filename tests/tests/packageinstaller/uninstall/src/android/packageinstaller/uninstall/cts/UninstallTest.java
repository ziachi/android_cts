/*
 * Copyright (C) 2018 Google Inc.
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
package android.packageinstaller.uninstall.cts;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.graphics.PixelFormat.TRANSLUCENT;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.VersionedPackage;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
@AppModeFull
public class UninstallTest extends UninstallTestBase {
    @Test
    @AsbSecurityTest(cveBugId = 171221302)
    public void overlaysAreSuppressedWhenConfirmingUninstall() throws Exception {
        AppOpsUtils.setOpMode(context.getPackageName(), "SYSTEM_ALERT_WINDOW", MODE_ALLOWED);

        WindowManager windowManager = context.getSystemService(WindowManager.class);
        LayoutParams layoutParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT,
                TYPE_APPLICATION_OVERLAY, 0, TRANSLUCENT);
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;

        View[] overlay = new View[1];
        new Handler(Looper.getMainLooper())
                .post(
                        () -> {
                            overlay[0] =
                                    LayoutInflater.from(context)
                                            .inflate(R.layout.overlay_activity, null);
                            windowManager.addView(overlay[0], layoutParams);
                        });

        try {
            uiDevice.wait(
                    Until.findObject(By.res(context.getPackageName(), "overlay_description")),
                    TIMEOUT_MS);

            startUninstall();

            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < TIMEOUT_MS) {
                try {
                    assertNull(
                            uiDevice.findObject(
                                    By.res(context.getPackageName(), "overlay_description")));
                    return;
                } catch (Throwable e) {
                    Thread.sleep(100);
                }
            }

            fail();
        } finally {
            windowManager.removeView(overlay[0]);
        }
    }

    @Test
    public void testUninstall() throws Exception {
        assertTrue("Package is not installed", isInstalled());

        startUninstall();
        assertUninstallDialogShown(By.textContains("Do you want to uninstall this app?"));
        clickInstallerButton();

        for (int i = 0; i < 30; i++) {
            // We can't detect the confirmation Toast with UiAutomator, so we'll poll
            Thread.sleep(500);
            if (!isInstalled()) {
                break;
            }
        }
        assertFalse("Package wasn't uninstalled.", isInstalled());
        assertTrue(AppOpsUtils.allowedOperationLogged(context.getPackageName(), APP_OP_STR));
    }

    @Test
    public void testUninstallApiConfirmationRequired() throws Exception {
        testUninstallApi(true);
    }

    @Test
    public void testUninstallApiConfirmationNotRequired() throws Exception {
        testUninstallApi(false);
    }

    public void testUninstallApi(boolean needUserConfirmation) throws Exception {
        assertTrue("Package is not installed", isInstalled());

        PackageInstaller pi = packageManager.getPackageInstaller();
        VersionedPackage pkg = new VersionedPackage(TEST_APK_PACKAGE_NAME,
                PackageManager.VERSION_CODE_HIGHEST);

        Intent broadcastIntent = new Intent(RECEIVER_ACTION).setPackage(context.getPackageName());
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        1,
                        broadcastIntent,
                        PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        if (needUserConfirmation) {
            pi.uninstall(pkg, 0, pendingIntent.getIntentSender());
            assertUninstallDialogShown(By.textContains("Do you want to uninstall this app?"));
            clickInstallerButton();
        } else {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                pi.uninstall(pkg, 0, pendingIntent.getIntentSender());
            });
        }

        assertTrue("Package is not uninstalled", latch.await(10, TimeUnit.SECONDS));
    }
}
