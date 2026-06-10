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

package com.android.compatibility.common.util.concurrentuser;

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.INTERNAL_SYSTEM_WINDOW;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Provides utility methods to interact with {@link ConcurrentUserActivityBase}. */
public final class ConcurrentUserActivityUtils {
    private ConcurrentUserActivityUtils() {
    }

    static final String BROADCAST_ACTION_TRIGGER = "broadcast_action_trigger";
    static final String KEY_USER_ID = "key_user_id";
    static final String KEY_CALLBACK = "key_callback";
    static final String KEY_BUNDLE = "key_bundle";

    private static final long LAUNCH_ACTIVITY_TIMEOUT_MS = 3000;
    private static final long SEND_BROADCAST_TIMEOUT_MS = 3000;

    /**
     * Gets the user that responds to the test. See {@link ConcurrentUserActivityBase}.
     *
     * @throws AssertionError if such user doesn't exist
     */
    public static int getResponderUserId() {
        UserManager userManager =
                getInstrumentation().getTargetContext().getSystemService(UserManager.class);
        int initiatorUserId = getInstrumentation().getTargetContext().getUserId();
        int[] responderUserId = new int[]{UserHandle.USER_NULL};
        runWithShellPermissionIdentity(
                () -> {
                    List<UserInfo> users = userManager.getAliveUsers();
                    for (UserInfo info : users) {
                        if (info.id != initiatorUserId && info.isFull()) {
                            responderUserId[0] = info.id;
                            break;
                        }
                    }
                }, CREATE_USERS);

        assertNotEquals("Failed to find the responder user on the device",
                UserHandle.USER_NULL, responderUserId[0]);
        return responderUserId[0];
    }

    /**
     * Launches the given activity as the given {@code userId} and waits for it to be launched.
     *
     * @param activityName the ComponentName of the Activity, which must be a subclass of
     *                     {@link ConcurrentUserActivityBase}
     * @param userId       the user ID
     */
    public static void launchActivityAsUserSync(ComponentName activityName, int userId) {
        CountDownLatch latch = new CountDownLatch(1);
        RemoteCallback callback = new RemoteCallback(bundle -> {
            int actualUserId = bundle.getInt(KEY_USER_ID);
            assertThat(actualUserId).isEqualTo(userId);
            latch.countDown();
        });

        Context context = getInstrumentation().getContext();
        UserHandle userHandle = UserHandle.of(userId);
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .setClassName(activityName.getPackageName(), activityName.getClassName())
                .putExtra(KEY_CALLBACK, callback)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK
                        // Remove activity animation to avoid flakiness.
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        runWithShellPermissionIdentity(
                () -> context.startActivityAsUser(intent, userHandle),
                // INTERNAL_SYSTEM_WINDOW is needed to launch the activity on secondary display.
                INTERACT_ACROSS_USERS_FULL, INTERNAL_SYSTEM_WINDOW);
        try {
            if (!latch.await(LAUNCH_ACTIVITY_TIMEOUT_MS, MILLISECONDS)) {
                fail(String.format("Failed to launch activity %s as user %d", activityName,
                        userId));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * Sends a message to the testing app running as the given user, and waits for its reply.
     *
     * @param packageName    the package name of the receiver app, which has a
     *                       {@link ConcurrentUserActivityBase}
     * @param receiverUserId the user ID of the receiver app
     * @param bundleToSend   the Bundle to be sent to the receiver app
     * @return the Bundle sent from the receiver app
     */
    @Nullable
    public static Bundle sendBundleAndWaitForReply(String packageName, int receiverUserId,
            Bundle bundleToSend) {
        Bundle[] receivedBundle = new Bundle[1];
        CountDownLatch latch = new CountDownLatch(1);
        RemoteCallback callback = new RemoteCallback(bundle -> {
            receivedBundle[0] = bundle;
            latch.countDown();
        });

        Intent intent = new Intent(BROADCAST_ACTION_TRIGGER)
                .setPackage(packageName)
                .putExtra(KEY_BUNDLE, bundleToSend)
                .putExtra(KEY_CALLBACK, callback);
        runWithShellPermissionIdentity(
                () -> getInstrumentation().getContext().sendBroadcastAsUser(
                        intent, UserHandle.of(receiverUserId)),
                INTERACT_ACROSS_USERS_FULL);
        try {
            if (!latch.await(SEND_BROADCAST_TIMEOUT_MS, MILLISECONDS)) {
                fail(String.format("Failed to send %s to %s (user %d)", bundleToSend,
                        packageName, receiverUserId));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return receivedBundle[0];
    }
}
