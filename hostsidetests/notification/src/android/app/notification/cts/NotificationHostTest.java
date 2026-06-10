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

package android.app.notification.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentNameProto;
import android.host.multiuser.BaseMultiUserTest;
import android.service.notification.ManagedServiceInfoProto;
import android.service.notification.ManagedServicesProto;
import android.service.notification.NotificationServiceDumpProto;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.PollingCheck;
import com.android.tradefed.device.CollectingByteOutputReceiver;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class NotificationHostTest extends BaseMultiUserTest {

    private static final String APP_APK = "CtsNotificationListenerApp.apk";
    private static final String APP_PKG = "com.android.cts.notificationlistener";
    private static final String SERVICE_CLASS = APP_PKG + ".TestNotificationListenerService";

    private static final long CONNECTION_TIMEOUT_MS = 30_000;

    @Rule
    public final SupportsMultiUserRule mSupportsMultiUserRule = new SupportsMultiUserRule(this);

    @Test
    public void testNotificationListenerServiceDuringUserSwitch_forPreviousUser() throws Exception {
        int userId = getDevice().createUser(
                "TestUser_" + System.currentTimeMillis() /* name */,
                false /* guest */,
                false /* ephemeral */);

        executeCommand("cmd notification allow_listener %s/%s %d", APP_PKG, SERVICE_CLASS,
                mInitialUserId);

        assertNotificationListenerEnabled(mInitialUserId);

        assertSwitchToUser(userId);

        assertNotificationListenerDisabled(mInitialUserId);

        executeCommand("cmd notification disallow_listener %s/%s %d", APP_PKG, SERVICE_CLASS,
                mInitialUserId);
    }

    @Test
    public void testNotificationListenerServiceDuringUserSwitch_forNewUser() throws Exception {
        int userId = getDevice().createUser(
                "TestUser_" + System.currentTimeMillis() /* name */,
                false /* guest */,
                false /* ephemeral */);

        // Install test notification listener apk for new user
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        assertThat(getDevice().installPackageForUser(buildHelper.getTestFile(APP_APK),
                false, userId)).isNull();

        executeCommand("cmd notification allow_listener %s/%s %d", APP_PKG, SERVICE_CLASS, userId);

        assertNotificationListenerDisabled(userId);

        assertSwitchToUser(userId);

        assertNotificationListenerEnabled(userId);

        executeCommand("cmd notification disallow_listener %s/%s %d", APP_PKG, SERVICE_CLASS,
                userId);
    }

    private void assertNotificationListenerEnabled(int userId) throws Exception {
        PollingCheck.check("Expected notification listener to be enabled, but was disabled",
                CONNECTION_TIMEOUT_MS,
                () -> {
                    return hasNotificationListenerEnabled(userId);
                });
    }

    private void assertNotificationListenerDisabled(int userId) throws Exception {
        PollingCheck.check("Expected notification listener to be disabled, but was enabled",
                CONNECTION_TIMEOUT_MS,
                () -> {
                    return !hasNotificationListenerEnabled(userId);
                });
    }

    private boolean hasNotificationListenerEnabled(int userId) throws Exception {
        CollectingByteOutputReceiver receiver = new CollectingByteOutputReceiver();
        getDevice().executeShellCommand("dumpsys notification --proto", receiver);

        NotificationServiceDumpProto dump = NotificationServiceDumpProto.parser()
                .parseFrom(receiver.getOutput());

        if (dump.hasNotificationListeners()) {
            ManagedServicesProto managedServices = dump.getNotificationListeners();
            for (ManagedServiceInfoProto info : managedServices.getLiveServicesList()) {
                ComponentNameProto component = info.getComponent();
                if (component.getPackageName().equals(APP_PKG)
                        && component.getClassName().contains(SERVICE_CLASS)
                        && userId == info.getUserId()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Executes the shell command and returns the output.
     */
    protected String executeCommand(String command, Object... args) throws Exception {
        String fullCommand = String.format(command, args);
        return getDevice().executeShellCommand(fullCommand);
    }
}
