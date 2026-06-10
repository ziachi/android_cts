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
package android.app.cts.fgstimeouttesthelper;

import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.EXTRA_MESSAGE;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.HELPER_PACKAGE;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.TAG;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.createNotification;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.sContext;
import static android.app.cts.fgstimeouttesthelper.FgsTimeoutHelper.setMessage;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.util.Objects;

/**
 * This class receives a message from the main test package and run a command.
 */
public class FgsTimeoutMessageReceiver extends BroadcastReceiver {
    /**
     * Send a FgsTimeoutMessage to this receiver.
     */
    public static void sendMessage(FgsTimeoutMessage m) {
        sendMessage(HELPER_PACKAGE, m);
    }

    /**
     * Send a FgsTimeoutMessage to this receiver in a given package.
     */
    public static void sendMessage(String packageName, FgsTimeoutMessage m) {
        Intent i = new Intent();
        i.setComponent(new ComponentName(packageName, FgsTimeoutMessageReceiver.class.getName()));
        i.setAction(Intent.ACTION_VIEW);
        i.putExtra(EXTRA_MESSAGE, m);
        i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        sContext.sendBroadcast(i);
        Log.i(TAG, "FgsTimeoutMessageReceiver.sendMessage: intent=" + i + " message=" + m);
    }

    /**
     * Check the message is sent by the currently running test.
     *
     * We get the "currently running test" information from CallProvider.
     */
    private static boolean isMessageCurrent(FgsTimeoutMessage m) {
        FgsTimeoutMessage testInfo = FgsTimeoutHelper.getCurrentTestInfo();
        if (testInfo.getLastTestEndUptime() > 0) {
            // No test running.
            Log.w(TAG, "FgsTimeoutMessageReceiver: no test running; dropping it.");
            return false;
        }
        if (m.getTimestamp() < testInfo.getLastTestStartUptime()) {
            // Stale message.
            Log.w(TAG, "FgsTimeoutMessageReceiver: Stale message; dropping it.");
            return false;
        }
        return true;
    }

    @Override
    public void onReceive(Context context, Intent i) {
        FgsTimeoutMessage m = Objects.requireNonNull(
                i.getParcelableExtra(EXTRA_MESSAGE, FgsTimeoutMessage.class));
        Log.i(TAG, "FgsTimeoutMessageReceiver.onReceive: intent=" + i + " message=" + m);

        // Every time we receive a message, we ask the main test when the last test started / ended,
        // and ignore "stale" messages.
        if (!isMessageCurrent(m)) {
            return;
        }

        Class<?> expectedException = null;
        try {
            // getExpectedExceptionClass() may throw, so we call it inside the try-cacth.
            expectedException = m.getExpectedExceptionClass();

            // Call Context.startForegroundService()?
            if (m.isDoCallStartForegroundService()) {
                // Call startForegroundService for the component, and also pass along the message
                Log.i(TAG, "isDoCallStartForegroundService: " + m);
                sContext.startForegroundService(
                        setMessage(new Intent().setComponent(m.getComponentName()), m));
                FgsTimeoutHelper.sendBackAckMessage();
                return;
            }

            // Call Context.startService()?
            if (m.isDoCallStartService()) {
                // Call startService for the component, and also pass along the message
                Log.i(TAG, "isDoCallStartService: " + m);
                sContext.startService(
                        setMessage(new Intent().setComponent(m.getComponentName()), m));
                FgsTimeoutHelper.sendBackAckMessage();
                return;
            }

            // Call Service.startForeground()?
            if (m.isDoCallStartForeground()) {
                Log.i(TAG, "isDoCallStartForeground: " + m);
                ServiceBase.getInstanceForClass(m.getComponentName().getClassName())
                        .startForeground(m.getNotificationId(),
                                createNotification(), m.getFgsType());
                FgsTimeoutHelper.sendBackAckMessage();
                return;
            }

            // Call Service.stopForeground()?
            if (m.isDoCallStopForeground()) {
                Log.i(TAG, "isDoCallStopForeground: " + m);
                ServiceBase.getInstanceForClass(m.getComponentName().getClassName())
                        .stopForeground(Service.STOP_FOREGROUND_DETACH);
                FgsTimeoutHelper.sendBackAckMessage();
                return;
            }

            // Call Service.stopSelf()?
            if (m.isDoCallStopSelf()) {
                Log.i(TAG, "isDoCallStopSelf: " + m);
                ServiceBase.getInstanceForClass(m.getComponentName().getClassName())
                        .stopSelf();
                FgsTimeoutHelper.sendBackAckMessage();
                return;
            }

            // Kill self, but we can't kill it until the receiver finishes (otherwise
            // ActivityManager would be unhappy, so we use Handler to run it after the receiver
            // finishes.
            if (m.isDoKillProcess()) {
                new Handler(Looper.getMainLooper()).post(FgsTimeoutMessageReceiver::killSelf);
                FgsTimeoutHelper.sendBackAckMessage();
                return;
            }

            throw new RuntimeException("Unknown message " + m + " received");
        } catch (Throwable th) {
            final boolean isExpected = expectedException != null
                    && expectedException.isAssignableFrom(th.getClass());
            if (isExpected) {
                Log.i(TAG, "Expected Exception received: ", th);
            } else {
                Log.w(TAG, "Unexpected exception received: ", th);
            }
            // Send back a failure message...
            FgsTimeoutMessage reply = new FgsTimeoutMessage();
            reply.setException(th);

            FgsTimeoutHelper.sendBackMessage(reply);
        }
    }

    private static void killSelf() {
        Process.killProcess(Process.myPid());
    }
}
