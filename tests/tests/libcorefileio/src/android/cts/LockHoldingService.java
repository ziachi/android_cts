/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package android.cts;

import static android.cts.FileChannelInterProcessLockTest.ChannelType;
import static android.cts.FileChannelInterProcessLockTest.LockType;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.concurrent.ExecutionException;

/**
 * A Service that listens for commands from the FileChannelInterProcessLockTest to acquire locks of
 * different types. It exists to test the behavior when file locks are acquired/released across
 * multiple processes.
 */
public class LockHoldingService extends Service {

    /**
     *  The key of the Bundle extra used to record a time after a lock is released by the service.
     */
    static final String LOCK_DEFINITELY_RELEASED_TIMESTAMP = "lockReleasedTimestamp";

    /**
     * The key of the Bundle extra used to record just before the lock is released by the service.
     */
    static final String LOCK_NOT_YET_RELEASED_TIMESTAMP = "lockNotReleasedTimestamp";

    /**
     * The key of the Bundle extra used to send general notifications to the test.
     */
    static final String NOTIFICATION_KEY = "notification";

    /**
     * The value for the notification sent to the test after the lock is acquired.
     */
    static final String NOTIFICATION_LOCK_HELD = "lockHeld";

    /**
     * The value for the notification sent to the test after the lock is released
     */
    static final String NOTIFICATION_LOCK_RELEASED = "lockReleased";

    /**
     * The value for the notification sent to the test after the lock is released for shutdown
     */
    static final String NOTIFICATION_READY_FOR_SHUTDOWN = "readyForShutdown";

    /**
     * The key of the Bundle extra used to send time for which the service should wait before
     * releasing the lock.
     */
    static final String TIME_TO_HOLD_LOCK_KEY = "timeToHoldLock";

    /**
     * The key of the Bundle extra used for the type of lock to be held.
     */
    static final String LOCK_TYPE_KEY = "lockType";

    /**
     * The key of the Bundle extra used for the type of the channel that acquires the lock.
     */
    static final String CHANNEL_TYPE_KEY = "channelType";

    /**
     * The message code used to let he service know to release the lock after some time.
     */
    static final int LOCK_BEHAVIOR_RELEASE_AND_NOTIFY = 1;

    /**
     * The message code used to let he service know to lock without releasing.
     */
    static final int LOCK_BEHAVIOUR_ACQUIRE_ONLY_AND_NOTIFY = 2;

    /**
     * The message code used to let the service know to release the lock before test end, if still
     * held.
     */
    static final int PREPARE_FOR_SHUTDOWN = 3;

    final String LOG_MESSAGE_TAG = "CtsLibcoreFileIOTestCases";

    private FileLock fileLock = null;

    private class LockHoldingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            try {
                switch (msg.what) {
                    case LOCK_BEHAVIOR_RELEASE_AND_NOTIFY:
                        acquireLockAndThenWaitThenRelease(msg);
                        break;
                    case LOCK_BEHAVIOUR_ACQUIRE_ONLY_AND_NOTIFY:
                        acquireLock(msg);
                        break;
                    case PREPARE_FOR_SHUTDOWN:
                        prepareForShutdown(msg);
                        break;
                    default:
                        super.handleMessage(msg);
                }
            } catch (Exception e) {
                Log.e(LOG_MESSAGE_TAG, "Exception acquire lock", e);
            }
        }
    }

    private Messenger messenger;

    public IBinder onBind(Intent intent) {
        messenger = new Messenger(new LockHoldingHandler());
        return messenger.getBinder();
    }

    /**
     * Acquires the lock asked by the test indefinitely.
     */
    private void acquireLock(Message msg) throws IOException,
            InterruptedException, ExecutionException, RemoteException {
        Bundle bundle = msg.getData();
        LockType lockType = (LockType) bundle.get(LOCK_TYPE_KEY);
        ChannelType channelType = (ChannelType) bundle.get(CHANNEL_TYPE_KEY);

        // Acquire the lock based on the information contained in the intent received.
        this.fileLock = FileChannelInterProcessLockTest.acquire(this, lockType, channelType);

        notifyLockHeld(msg);
    }

    /**
     * Acquires and holds the lock for a time specified by the test. Sends a response message after
     * releasing the lock.
     */
    private void acquireLockAndThenWaitThenRelease(Message msg)
            throws IOException, InterruptedException, ExecutionException, RemoteException {
        Bundle bundle = msg.getData();
        long lockHoldTimeMillis = bundle.getLong(TIME_TO_HOLD_LOCK_KEY, 0);
        LockType lockType = (LockType) bundle.get(LOCK_TYPE_KEY);
        ChannelType channelType = (ChannelType) bundle.get(CHANNEL_TYPE_KEY);

        // Acquire the lock.
        this.fileLock = FileChannelInterProcessLockTest.acquire(this, lockType, channelType);

        // Signal the lock is now held.
        notifyLockHeld(msg);

        Thread.sleep(lockHoldTimeMillis);

        long lockNotReleasedTimestamp = System.currentTimeMillis();

        // Release the lock
        fileLock.release();

        long lockReleasedTimestamp = System.currentTimeMillis();

        // Signal the lock is released and some information about timing.
        notifyLockReleased(msg, lockNotReleasedTimestamp, lockReleasedTimestamp);
    }

    private void notifyLockHeld(Message msg) throws RemoteException {
        Message rsp = msg.obtain();
        Bundle rspBundle = rsp.getData();
        rspBundle.putBoolean(NOTIFICATION_LOCK_HELD, true);
        msg.replyTo.send(rsp);
    }

    private void notifyLockReleased(Message msg, long lockNotReleasedTimestamp,
            long lockReleasedTimestamp) throws RemoteException {
        Message rsp = msg.obtain();
        Bundle rspBundle = rsp.getData();
        rspBundle.putBoolean(NOTIFICATION_LOCK_RELEASED, true);
        rspBundle.putLong(LOCK_NOT_YET_RELEASED_TIMESTAMP, lockNotReleasedTimestamp);
        rspBundle.putLong(LOCK_DEFINITELY_RELEASED_TIMESTAMP, lockReleasedTimestamp);
        msg.replyTo.send(rsp);
    }

    private void prepareForShutdown(Message msg) throws RemoteException {
        doReleaseLock();
        Message rsp = msg.obtain();
        Bundle rspBundle = rsp.getData();
        rspBundle.putBoolean(NOTIFICATION_READY_FOR_SHUTDOWN, true);
        msg.replyTo.send(rsp);
    }

    private void doReleaseLock() {
        try {
            if (fileLock != null) {
                fileLock.release();
            }
        } catch (IOException e) {
            Log.e(LOG_MESSAGE_TAG, e.getMessage());
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        doReleaseLock();
        return false;
    }
}
