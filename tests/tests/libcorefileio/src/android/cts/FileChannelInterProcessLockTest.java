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

import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.test.AndroidTestCase;

import java.io.File;
import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("deprecation")
public class FileChannelInterProcessLockTest extends AndroidTestCase {

    /** The directory where file locks are created */
    final static String DIR_NAME = "CtsFileIOTest";

    /** The name of the file used when acquiring a lock. */
    final static String FILE_NAME = "file";

    /** The position in the lock file used when acquiring a region lock. */
    final static int LOCK_POSITION = 10;

    /** The extent of the lock file locked when acquiring a region lock. */
    final static int LOCK_SIZE = 10;

    /**
     * This is the maximum waiting time in seconds for the test to wait for a response from
     * the service. This provides ample amount of time for the service to receive the request from
     * the test, then act, and respond back.
     */
    final static int MAX_WAIT_TIME = 10;

    @Override
    public void tearDown() throws Exception {
        IpcChannel.unbindService();
        super.tearDown();
    }

    public void test_tryLock_syncChannel() throws Exception {
        doTest_tryLock(ChannelType.SYNC_CHANNEL, ChannelType.SYNC_CHANNEL);
    }

    public void test_tryLock_asyncChannel() throws Exception {
        doTest_tryLock(ChannelType.ASYNC_CHANNEL, ChannelType.ASYNC_CHANNEL);
    }

    public void test_tryLock_differentChannelTypes() throws Exception {
        checkTryLockBehavior(LockType.TRY_LOCK, LockType.TRY_LOCK,
                ChannelType.SYNC_CHANNEL, ChannelType.ASYNC_CHANNEL,
                false /* expectToGetLock */);

        checkTryLockBehavior(LockType.TRY_LOCK, LockType.TRY_LOCK,
                ChannelType.ASYNC_CHANNEL, ChannelType.SYNC_CHANNEL,
                false /* expectToGetLock */);
    }

    /**
     * java.nio.channels.[Asynchronouse]FileChannel#tryLock()
     *
     * Obtains a remote lock, then attempts to acquire a local lock on the same file,
     * and checks the behavior.
     * checkTryLockBehavior(localLockType, remoteLockType, expectedLocalLockResult)
     * expectedLockLockResult: {@code true} if the returned lock should be valid,
     * {@code false} otherwise.
     */
    private void doTest_tryLock(ChannelType localChannelType, ChannelType remoteChannelType)
            throws Exception {

        checkTryLockBehavior(LockType.TRY_LOCK, LockType.TRY_LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.TRY_LOCK, LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.TRY_LOCK, LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.TRY_LOCK, LockType.LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.TRY_LOCK, LockType.LOCK_ON_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.TRY_LOCK, LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
    }

    public void test_tryLockJJZ_Exclusive_syncChannel() throws Exception {
        doTest_tryLockJJZ_Exclusive(ChannelType.SYNC_CHANNEL, ChannelType.SYNC_CHANNEL);
    }

    public void test_tryLockJJZ_Exclusive_asyncChannel() throws Exception {
        doTest_tryLockJJZ_Exclusive(ChannelType.ASYNC_CHANNEL, ChannelType.ASYNC_CHANNEL);
    }

    public void test_tryLockJJZ_Exclusive_differentChannelTypes() throws Exception {
        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK, LockType.TRY_LOCK,
                ChannelType.SYNC_CHANNEL, ChannelType.ASYNC_CHANNEL, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK, LockType.TRY_LOCK,
                ChannelType.ASYNC_CHANNEL, ChannelType.SYNC_CHANNEL, false /* expectToGetLock */);

        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,
                ChannelType.SYNC_CHANNEL, ChannelType.ASYNC_CHANNEL, true /* expectToGetLock */);
        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,
                ChannelType.ASYNC_CHANNEL, ChannelType.SYNC_CHANNEL, true /* expectToGetLock */);
    }

    /**
     * java.nio.channels.[Asynchronous]FileChannel#tryLock(long, long, boolean)
     *
     * Obtains a remote lock, then attempts to acquire a local lock on the same file,
     * and checks the behavior.
     * checkTryLockBehavior(localLockType, remoteLockType, expectedLocalLockResult)
     * expectedLockLockResult: {@code true} if the returned lock should be valid,
     * {@code false} otherwise.
     */
    private void doTest_tryLockJJZ_Exclusive(ChannelType localChannelType,
            ChannelType remoteChannelType) throws Exception {
        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK, LockType.TRY_LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, true /* expectToGetLock */);
        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, true /* expectToGetLock */);

        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK, LockType.LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.LOCK_ON_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, true /* expectToGetLock */);
        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, true /* expectToGetLock */);
    }

    public void test_tryLockJJZ_Shared_syncChannel() throws Exception {
        doTest_tryLockJJZ_Shared(ChannelType.SYNC_CHANNEL, ChannelType.SYNC_CHANNEL);
    }

    public void test_tryLockJJZ_Shared_asyncChannel() throws Exception {
        doTest_tryLockJJZ_Shared(ChannelType.ASYNC_CHANNEL, ChannelType.ASYNC_CHANNEL);
    }

    public void test_tryLockJJZ_Shared_differentChannelTypes() throws Exception {
        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK, LockType.TRY_LOCK,
                ChannelType.SYNC_CHANNEL, ChannelType.ASYNC_CHANNEL, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK, LockType.TRY_LOCK,
                ChannelType.ASYNC_CHANNEL, ChannelType.SYNC_CHANNEL, false /* expectToGetLock */);

        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,
                ChannelType.SYNC_CHANNEL, ChannelType.ASYNC_CHANNEL, true /* expectToGetLock */);
        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,
                ChannelType.ASYNC_CHANNEL, ChannelType.SYNC_CHANNEL, true /* expectToGetLock */);
    }

    /**
     * java.nio.channels.[Asynchronous]FileChannel#tryLock(long, long, boolean)
     *
     * Obtains a remote lock, then attempts to acquire a local lock on the same file,
     * and checks the behavior.
     * checkTryLockBehavior(localLockType, remoteLockType, expectedLocalLockResult)
     * expectedLockLockResult: {@code true} if the returned lock should be valid,
     * {@code false} otherwise.
     */
    private void doTest_tryLockJJZ_Shared(ChannelType localChannelType,
            ChannelType remoteChannelType) throws Exception {
        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK, LockType.TRY_LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, true /* expectToGetLock */);
        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, true /* expectToGetLock */);
        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, true /* expectToGetLock */);

        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK, LockType.LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.LOCK_ON_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, false /* expectToGetLock */);
        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, true /* expectToGetLock */);
        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, true /* expectToGetLock */);
        checkTryLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                LockType.SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, true /* expectToGetLock */);
    }

    public void test_lock_syncChannel() throws Exception {
        doTest_lock(ChannelType.SYNC_CHANNEL, ChannelType.SYNC_CHANNEL);
    }

    public void test_lock_asyncChannel() throws Exception {
        doTest_lock(ChannelType.ASYNC_CHANNEL, ChannelType.ASYNC_CHANNEL);
    }

    public void test_lock_differentChannelTypes() throws Exception {
        checkLockBehavior(LockType.LOCK, LockType.LOCK,
                ChannelType.SYNC_CHANNEL, ChannelType.ASYNC_CHANNEL, true /* expectToWait */);
        checkLockBehavior(LockType.LOCK, LockType.LOCK,
                ChannelType.ASYNC_CHANNEL, ChannelType.SYNC_CHANNEL, true /* expectToWait */);

        checkLockBehavior(LockType.LOCK, LockType.TRY_LOCK,
                ChannelType.SYNC_CHANNEL, ChannelType.ASYNC_CHANNEL, true /* expectToWait */);
        checkLockBehavior(LockType.LOCK, LockType.TRY_LOCK,
                ChannelType.ASYNC_CHANNEL, ChannelType.SYNC_CHANNEL, true /* expectToWait */);
    }

    /**
     * java.nio.channels.[Asynchronous]FileChannel#lock()
     *
     * Obtains a remote lock, then attempts to acquire a local lock on the same file,
     * and checks the behavior.
     * checkTryLockBehavior(localLockType, remoteLockType, expectedLocalLockResult)
     * expectedLockLockResult: {@code true} if it blocks the local thread, {@code false} otherwise.
     */
    private void doTest_lock(ChannelType localChannelType, ChannelType remoteChannelType)
            throws Exception {
        checkLockBehavior(LockType.LOCK, LockType.LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.LOCK, LockType.LOCK_ON_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.LOCK, LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);

        checkLockBehavior(LockType.LOCK, LockType.TRY_LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.LOCK, LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.LOCK, LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
    }

    public void test_lockJJZ_Exclusive_syncChannel() throws Exception {
        doTest_lockJJZ_Exclusive(ChannelType.SYNC_CHANNEL, ChannelType.SYNC_CHANNEL);
    }

    public void test_lockJJZ_Exclusive_asyncChannel() throws Exception {
        doTest_lockJJZ_Exclusive(ChannelType.ASYNC_CHANNEL, ChannelType.ASYNC_CHANNEL);
    }

    public void test_lockJJZ_Exclusive_differentChannelTypes() throws Exception {
        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK, LockType.LOCK,
                ChannelType.SYNC_CHANNEL, ChannelType.ASYNC_CHANNEL, true /* expectToWait */);
        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK, LockType.LOCK,
                ChannelType.ASYNC_CHANNEL, ChannelType.SYNC_CHANNEL, true /* expectToWait */);

        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,
                ChannelType.SYNC_CHANNEL, ChannelType.ASYNC_CHANNEL, false /* expectToWait */);
        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,
                ChannelType.ASYNC_CHANNEL, ChannelType.SYNC_CHANNEL, false /* expectToWait */);
    }

    /**
     * java.nio.channels.[Asynchronous]FileChannel#lock(long, long, boolean)
     *
     * Obtains a remote lock, then attempts to acquire a local lock on the same file,
     * and checks the behavior.
     * checkTryLockBehavior(localLockType, remoteLockType, expectedLocalLockResult)
     * expectedLockLockResult: {@code true} if blocks the local thread, {@code false} otherwise.
     */
    private void doTest_lockJJZ_Exclusive(ChannelType localChannelType,
            ChannelType remoteChannelType) throws Exception {
        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK, LockType.LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK, LockType.LOCK_ON_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK,
                LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, false /* expectToWait */);
        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK,
                LockType.SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, false /* expectToWait */);

        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK, LockType.TRY_LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK, LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK,
                LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, false /* expectToWait */);
        checkLockBehavior(LockType.LOCK_ON_REGION_WITH_LOCK,
                LockType.SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, false /* expectToWait */);
    }

    public void test_lockJJZ_Shared_syncChannel() throws Exception {
        doTest_lockJJZ_Shared(ChannelType.SYNC_CHANNEL, ChannelType.SYNC_CHANNEL);
    }

    public void test_lockJJZ_Shared_asyncChannel() throws Exception {
        doTest_lockJJZ_Shared(ChannelType.ASYNC_CHANNEL, ChannelType.ASYNC_CHANNEL);
    }

    public void test_lockJJZ_Shared_differentChannelTypes() throws Exception {
        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK, LockType.LOCK,
                ChannelType.SYNC_CHANNEL, ChannelType.ASYNC_CHANNEL, true /* expectToWait */);
        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK, LockType.LOCK,
                ChannelType.ASYNC_CHANNEL, ChannelType.SYNC_CHANNEL, true /* expectToWait */);

        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,
                ChannelType.SYNC_CHANNEL, ChannelType.ASYNC_CHANNEL, false /* expectToWait */);
        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,
                ChannelType.ASYNC_CHANNEL, ChannelType.SYNC_CHANNEL, false /* expectToWait */);
    }

    /**
     * java.nio.channels.[Asynchronous]FileChannel#lock(long, long, boolean)
     *
     * Obtains a remote lock, then attempts to acquire a local lock on the same file,
     * and checks the behavior.
     * checkTryLockBehavior(localLockType, remoteLockType, expectedLocalLockResult)
     * expectedLockLockResult: {@code true} if blocks the local thread, {@code false} otherwise.
     */
    private void doTest_lockJJZ_Shared(ChannelType localChannelType,
            ChannelType remoteChannelType) throws Exception {
        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK, LockType.LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                LockType.LOCK_ON_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, false /* expectToWait */);
        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, false /* expectToWait */);
        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                LockType.SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,
                localChannelType, remoteChannelType, false /* expectToWait */);

        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK, LockType.TRY_LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                LockType.LOCK_ON_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, true /* expectToWait */);
        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                LockType.SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, false /* expectToWait */);
        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                LockType.LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, false /* expectToWait */);
        checkLockBehavior(LockType.SHARED_LOCK_ON_REGION_WITH_LOCK,
                LockType.SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,
                localChannelType, remoteChannelType, false /* expectToWait */);
    }

    /**
     * Checks the behavior of java.nio.Channels.[Asynchronous]FileChannel#tryLock()
     * and #tryLock(J, J, Z)
     *
     * @param localLockType the type of lock to be acquired by the test.
     * @param remoteLockType the type of lock to be acquired by the remote service.
     * @param localChannelType the type of the channel that acquires the lock locally.
     * @param remoteChannelType the type of channel that acquires the lock remotely.
     * @param expectToGetLock {@code true}, if the lock should be acquired even when the
     *         service holds a {@code remoteLockType} lock, false otherwise.
     */
    private void checkTryLockBehavior(LockType localLockType, LockType remoteLockType,
            ChannelType localChannelType, ChannelType remoteChannelType,
            boolean expectToGetLock) throws Exception {
        try {
            // Request that the remote lock be obtained.
            IpcChannel.bindService(getContext());
            IpcChannel.requestRemoteLock(remoteLockType, remoteChannelType);

            // Wait for a signal that the remote lock is definitely held.
            assertTrue(IpcChannel.lockHeldLatch.await(MAX_WAIT_TIME, SECONDS));

            // Try to acquire the local lock in all cases and check whether it could be acquired or
            // not as expected.
            if (expectToGetLock) {
                FileLock fileLock = acquire(getContext(), localLockType, localChannelType);
                assertNotNull(fileLock);
                assertTrue(fileLock.isValid());
            } else {
                assertNull(acquire(getContext(), localLockType, localChannelType));
            }
            // Release the remote lock.
        } finally {
            IpcChannel.unbindService();
        }
    }

    /**
     * Checks the java.nio.channels.[Asynchronous]FileChannel.lock()/lock(J, J, Z) behavior.
     *
     * @param localLockType type of lock to be acquired by the tes.t
     * @param remoteLockType type of lock to be acquired by the remote service..
     * @param localChannelType the type of the channel that acquires the lock locally.
     * @param remoteChannelType the type of channel that acquires the lock remotely.
     * @param expectToWait {@code true}, if the local thread must wait for the remote
     *         service to release the lock, {@code false} otherwise.
     */
    private void checkLockBehavior(LockType localLockType, LockType remoteLockType,
            ChannelType localChannelType, ChannelType remoteChannelType,
            boolean expectToWait) throws Exception {
        try {
            // The amount of time the remote service should hold lock.
            long remoteLockHoldTimeMillis = 5000;

            // The amount of time test should get to try to acquire the lock.
            long sufficientOverlappingTimeInMillis = 2000;

            // This is the allowable delta in the time between the time recorded after the service
            // released the lock and the time recorded after the test obtained the lock.
            long lockReleasedAndReacquiredTimeDeltaInMillis = 500;

            // Tell the service to acquire a remote lock.
            IpcChannel.bindService(getContext());
            IpcChannel.requestRemoteLockAndRelease(remoteLockType,
                    remoteChannelType, remoteLockHoldTimeMillis);

            // Wait for the service to hold the lock and notify for the same.
            assertTrue("No remote lock held notification",
                    IpcChannel.lockHeldLatch.await(MAX_WAIT_TIME, SECONDS));

            long localLockNotObtainedTime = System.currentTimeMillis();

            // Acquire the lock locally.
            FileLock fileLock = acquire(getContext(), localLockType, localChannelType);
            long localLockObtainedTime = System.currentTimeMillis();

            // Wait until the remote lock has definitely been released.
            assertTrue("No remote lock release notification",
                    IpcChannel.lockReleasedLatch.await(MAX_WAIT_TIME, SECONDS));

            Bundle remoteLockReleasedBundle = IpcChannel.lockReleasedBundle;
            long remoteLockNotReleasedTime =
                    remoteLockReleasedBundle.getLong(LockHoldingService.LOCK_NOT_YET_RELEASED_TIMESTAMP);
            long remoteLockReleasedTime =
                    remoteLockReleasedBundle.getLong(LockHoldingService.LOCK_DEFINITELY_RELEASED_TIMESTAMP);

            // We want the test to be notified well before the service releases the lock, so that
            // we can be sure that it tried obtaining the lock before the service actually released it.
            // Therefore, a two seconds time interval provides the test to get prepare and try to obtain
            // the lock. If this fails, it doesn't mean they definitely didn't overlap
            // but we can't be sure and the test may not be valid. This is why we hold the lock
            // remotely for a long time compared to the delays we expect for intents to propagate
            // between processes.
            assertTrue(String.format("Remote lock release start " +
                        "too soon (%d ms) after lock notification time. " +
                        "Need at least %d ms",
                        remoteLockNotReleasedTime - localLockNotObtainedTime,
                        sufficientOverlappingTimeInMillis
                    ),
                    remoteLockNotReleasedTime - localLockNotObtainedTime >
                    sufficientOverlappingTimeInMillis);

            if (expectToWait) {

                // The remoteLockReleaseTime is captured after the lock was released by the
                // service. The localLockObtainedTime is captured after the lock was obtained by this
                // thread. Therefore, there is a degree of slop inherent in the two times. We assert
                // that they are "close" to each other, but we cannot assert any ordering.
                assertTrue(String.format("Local lock obtained too long (%d ms) " +
                            "from remote lock release time. " +
                            "Expected at most %d ms.",
                            localLockObtainedTime - remoteLockReleasedTime,
                            lockReleasedAndReacquiredTimeDeltaInMillis),
                    Math.abs(localLockObtainedTime - remoteLockReleasedTime) <
                        lockReleasedAndReacquiredTimeDeltaInMillis);
            } else {
                // The remoteLockNotReleaseTime is captured before the lock was released by the
                // service. The localLockObtainedTime is captured after the lock was obtained by this
                // thread. The local thread should be able to get the lock before the remote thread
                // definitely release it. If this test fails it may not indicate a problem, but it
                // indicates we cannot be sure the test was successful the local lock attempt and the
                // remote lock attempt did not overlap.
                assertTrue(String.format("Local lock obtained (%d) after " +
                            "remote lock release start (%d)",
                            localLockObtainedTime, remoteLockNotReleasedTime),
                    localLockObtainedTime < remoteLockNotReleasedTime);
            }

            // Asserting if the fileLock is valid.
            assertTrue(fileLock.isValid());
        } finally {
            IpcChannel.unbindService();
        }
    }

    static enum LockType {

        /** Equivalent to {@code tryLock()} */
        TRY_LOCK,

        /** Equivalent to  {@code tryLock({@link #LOCK_POSITION}, {@link #LOCK_SIZE}, false)} */
        LOCK_ON_REGION_WITH_TRY_LOCK,

        /**
         * Equivalent to  {@code tryLock({@code {@link #LOCK_POSITION} + {@link #LOCK_SIZE}},
         * {@link #LOCK_SIZE}, false)}
         */
        LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,

        /** Equivalent to  {@code tryLock({@link #LOCK_POSITION}, {@link #LOCK_SIZE}, true)} */
        SHARED_LOCK_ON_REGION_WITH_TRY_LOCK,

        /**
         * Equivalent to  {@code tryLock({@code {@link #LOCK_POSITION} + {@link #LOCK_SIZE}},
         * {@link #LOCK_SIZE}, true)}
         */
        SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK,

        /** Equivalent to  {@code lock()} */
        LOCK,

        /** Equivalent to  {code lock({@link #LOCK_POSITION}, {@link #LOCK_SIZE}, false)} */
        LOCK_ON_REGION_WITH_LOCK,

        /**
         * Equivalent to  {@code lock({@code {@link #LOCK_POSITION} + {@link #LOCK_SIZE}},
         * {@link #LOCK_SIZE}, false)}
         */
        LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,

        /** Equivalent to  {@code lock({@link #LOCK_POSITION}, {@link #LOCK_SIZE}, true)} */
        SHARED_LOCK_ON_REGION_WITH_LOCK,

        /**
         * Equivalent to {@code lock({@code {@link #LOCK_POSITION} + {@link #LOCK_SIZE}},
         * {@link #LOCK_SIZE}, true)}
         */
        SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK,
    }

    static enum ChannelType {
        /** Represents an {@code java.nio.channels.FileChannel} */
        SYNC_CHANNEL,
        /** Represents an {@code java.nio.channels.AsynchronousFileChannel} */
        ASYNC_CHANNEL,
    }

    /**
     * Tries to acquire a lock of {@code lockType} on the file returned by
     * {@link #createFileInDir()} method.
     *
     * @param lockType a {@link LockType} enum.
     *         Permitted lock types:
     *         {@link LockType#TRY_LOCK}
     *         {@link LockType#LOCK_ON_REGION_WITH_TRY_LOCK}
     *         {@link LockType#LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK}
     *         {@link LockType#SHARED_LOCK_ON_REGION_WITH_TRY_LOCK}
     *         {@link LockType#SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK}
     *         {@link LockType#LOCK}
     *         {@link LockType#LOCK_ON_REGION_WITH_LOCK}
     *         {@link LockType#LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK}
     *         {@link LockType#SHARED_LOCK_ON_REGION_WITH_LOCK}
     *         {@link LockType#SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK}
     * @param channelType the type of channel used to acquire the lock.
     * @return the lock returned by the lock method.
     * @throws UnsupportedOperationException
     *         If the {@code lockType} is of non recognized type.
     */
    static FileLock acquire(Context context, LockType lockType, ChannelType channelType) throws
            IOException, InterruptedException, ExecutionException {
        File file = createFileInDir(context);
        file.createNewFile();

        FileChannel fc = null;
        AsynchronousFileChannel afc = null;
        if (channelType == ChannelType.SYNC_CHANNEL) {
            fc = FileChannel.open(file.toPath(),
                    StandardOpenOption.WRITE, StandardOpenOption.READ);
        } else if (channelType == ChannelType.ASYNC_CHANNEL) {
            afc = AsynchronousFileChannel.open(file.toPath(),
                StandardOpenOption.WRITE, StandardOpenOption.READ);
        }

        switch (lockType) {
            case TRY_LOCK:
                if (fc != null) {
                    return fc.tryLock();
                } else {
                    return afc.tryLock();
                }
            case LOCK_ON_REGION_WITH_TRY_LOCK:
                if (fc != null) {
                    return fc.tryLock(LOCK_POSITION, LOCK_SIZE, false /*isShared*/);
                } else {
                    return afc.tryLock(LOCK_POSITION, LOCK_SIZE, false /*isShared*/);
                }
            case LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK:
                if (fc != null) {
                    return fc.tryLock(LOCK_POSITION + LOCK_SIZE, LOCK_SIZE, false /*isShared*/);
                } else {
                    return afc.tryLock(LOCK_POSITION + LOCK_SIZE, LOCK_SIZE, false /*isShared*/);
                }
            case SHARED_LOCK_ON_REGION_WITH_TRY_LOCK:
                if (fc != null) {
                    return fc.tryLock(LOCK_POSITION, LOCK_SIZE, true /*isShared*/);
                } else {
                    return afc.tryLock(LOCK_POSITION, LOCK_SIZE, true /*isShared*/);
                }
            case SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_TRY_LOCK:
                if (fc != null) {
                    return fc.tryLock(LOCK_POSITION + LOCK_SIZE, LOCK_SIZE, true /*isShared*/);
                } else {
                    return afc.tryLock(LOCK_POSITION + LOCK_SIZE, LOCK_SIZE, true /*isShared*/);
                }
            case LOCK:
                if (fc != null) {
                    return fc.lock();
                } else {
                    return afc.lock().get();
                }
            case LOCK_ON_REGION_WITH_LOCK:
                if (fc != null) {
                    return fc.lock(LOCK_POSITION, LOCK_SIZE, false /*isShared*/);
                } else {
                    return afc.lock(LOCK_POSITION, LOCK_SIZE, false /*isShared*/).get();
                }
            case LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK:
                if (fc != null) {
                    return fc.lock(LOCK_POSITION + LOCK_SIZE, LOCK_SIZE, false /*isShared*/);
                } else {
                    return afc.lock(LOCK_POSITION + LOCK_SIZE, LOCK_SIZE, false /*isShared*/).get();
                }
            case SHARED_LOCK_ON_REGION_WITH_LOCK:
                if (fc != null) {
                    return fc.lock(LOCK_POSITION, LOCK_SIZE, true /*isShared*/);
                } else {
                    return afc.lock(LOCK_POSITION, LOCK_SIZE, true /*isShared*/).get();
                }
            case SHARED_LOCK_ON_NON_OVERLAPPING_REGION_WITH_LOCK:
                if (fc != null) {
                    return fc.lock(LOCK_POSITION + LOCK_SIZE, LOCK_SIZE, true /*isShared*/);
                } else {
                    return afc.lock(LOCK_POSITION + LOCK_SIZE, LOCK_SIZE, true /*isShared*/).get();
                }
            default:
                throw new UnsupportedOperationException("Unknown lock type");
        }
    }

    /**
     * Creates a file named {@link #FILE_NAME} inside a directory named {@link #DIR_NAME} on
     * the external storage directory.
     */
    static File createFileInDir(Context context) throws IOException {
        File dir = new File(context.getExternalFilesDir(null), DIR_NAME);
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            throw new IOException("External storage is not mounted");
        } else if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot create directory for device info files");
        } else {
            return new File(dir, FILE_NAME);
        }
    }

    /**
     * Deletes the folder {@link #DIR_NAME} on the external storage directory along with all the
     * files inside it.
     */
    static void deleteDir(Context context) {
        File dir = new File(context.getExternalFilesDir(null), DIR_NAME);
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String child : children) {
                new File(dir, child).delete();
            }
            dir.delete();
        }
    }

    /**
     * Handles the comms with the LockHoldingService.
     *
     * Binds to the service and sends requests and receives callbacks from it.
     *
     * It records information / provides latches so the test code can synchronize until it is
     * informed the service has acted on requests it has sent.
     */
    public static class IpcChannel {

        static Context context;

        static CountDownLatch onBindLatch;
        static CountDownLatch onUnbindLatch;
        static CountDownLatch lockHeldLatch;
        static volatile Bundle lockHeldBundle;
        static CountDownLatch lockReleasedLatch;
        static volatile Bundle lockReleasedBundle;

        static volatile boolean bound = false;
        static Messenger messenger;
        static Messenger responseMessenger;
        static ServiceConnection serviceConnection;

        static Handler responseMessageHandler;

        private static Context getContext() {
            return context;
        }

        /**
         * Reset the IpcChannel for a new test. Assumes no intents will be received from prior
         * tests.
         */
        static synchronized void resetReceiverState(Context testContext) {
            context = testContext;
            bound = false;
            onBindLatch = new CountDownLatch(1);
            onUnbindLatch = new CountDownLatch(1);
            lockHeldLatch = new CountDownLatch(1);
            lockReleasedLatch = new CountDownLatch(1);
            lockHeldBundle = null;
            lockReleasedBundle = null;

            responseMessageHandler = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    Bundle bundle = msg.getData();
                    if (bundle.getBoolean(LockHoldingService.NOTIFICATION_LOCK_HELD)) {
                        lockHeldBundle = bundle;
                        lockHeldLatch.countDown();
                    } else if (bundle.getBoolean(LockHoldingService.NOTIFICATION_LOCK_RELEASED)) {
                        lockReleasedBundle = bundle;
                        lockReleasedLatch.countDown();
                    } else if (bundle.getBoolean(LockHoldingService.NOTIFICATION_READY_FOR_SHUTDOWN)) {
                        onUnbindLatch.countDown();
                    } else {
                        super.handleMessage(msg);
                    }
                }
            };
            responseMessenger = new Messenger(responseMessageHandler);

            serviceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName className, IBinder service) {
                    messenger = new Messenger(service);
                    bound = true;
                    onBindLatch.countDown();
                }

                @Override
                public void onBindingDied(ComponentName className) {
                    getContext().unbindService(this);
                    bound = false;
                }

                @Override
                public void onServiceDisconnected(ComponentName className) {
                    getContext().unbindService(this);
                    bound = false;
                }
            };
        }

        static void bindService(Context testContext) throws Exception {
            resetReceiverState(testContext);
            getContext().bindService(new Intent(getContext(), LockHoldingService.class),
                    serviceConnection, Context.BIND_AUTO_CREATE);
            assertTrue(onBindLatch.await(MAX_WAIT_TIME, SECONDS));
        }

        static void requestRemoteLock(LockType lockType, ChannelType channelType) throws Exception {
            Message msg = Message.obtain(null,
                    LockHoldingService.LOCK_BEHAVIOUR_ACQUIRE_ONLY_AND_NOTIFY, 0, 0);
            Bundle bundle = msg.getData();
            bundle.putSerializable(LockHoldingService.LOCK_TYPE_KEY, lockType);
            bundle.putSerializable(LockHoldingService.CHANNEL_TYPE_KEY, channelType);
            msg.replyTo = responseMessenger;
            messenger.send(msg);
        }

        static void requestRemoteLockAndRelease(LockType lockType, ChannelType channelType,
                long lockHoldTimeMillis) throws Exception {
            Message msg = Message.obtain(null,
                    LockHoldingService.LOCK_BEHAVIOR_RELEASE_AND_NOTIFY, 0, 0);
            Bundle bundle = msg.getData();
            bundle.putSerializable(LockHoldingService.LOCK_TYPE_KEY, lockType);
            bundle.putSerializable(LockHoldingService.CHANNEL_TYPE_KEY, channelType);
            bundle.putLong(LockHoldingService.TIME_TO_HOLD_LOCK_KEY, lockHoldTimeMillis);
            msg.replyTo = responseMessenger;
            messenger.send(msg);
        }

        /**
         * Requests and waits for the service to stop
         */
        static void unbindService() throws Exception {
            if (bound) {
                Message msg = Message.obtain(null, LockHoldingService.PREPARE_FOR_SHUTDOWN, 0, 0);
                msg.replyTo = responseMessenger;
                messenger.send(msg);

                assertTrue(onUnbindLatch.await(MAX_WAIT_TIME, SECONDS));
                getContext().unbindService(serviceConnection);
                bound = false;
            }
            messenger = null;
            deleteDir(getContext());
        }
    }

}

