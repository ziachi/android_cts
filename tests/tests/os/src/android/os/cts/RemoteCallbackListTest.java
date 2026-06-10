/*
 * Copyright (C) 2008 The Android Open Source Project
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


import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.ConditionVariable;
import android.os.Flags;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteCallbackList;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class RemoteCallbackListTest {
    private static final String SERVICE_ACTION = "android.app.REMOTESERVICE";
    private static final int CALLBACK_WAIT_TIMEOUT_SECS = 1;
    private static final int MAX_QUEUE_SIZE = 32;

    // Lock object
    private final Sync mSync = new Sync();
    private ISecondary mSecondaryService = null;
    private Intent mIntent;
    private Context mContext;
    private ServiceConnection mSecondaryConnection;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mSecondaryConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                mSecondaryService = ISecondary.Stub.asInterface(service);
                synchronized (mSync) {
                    mSync.mIsConnected = true;
                    mSync.notify();
                }
            }

            public void onServiceDisconnected(ComponentName className) {
                mSecondaryService = null;
                synchronized (mSync) {
                    mSync.mIsDisConnected = true;
                    mSync.notify();
                }
            }
        };
        mIntent = new Intent(SERVICE_ACTION);
        mIntent.setPackage(mContext.getPackageName());

        Intent secondaryIntent = new Intent(ISecondary.class.getName());
        secondaryIntent.setPackage(mContext.getPackageName());
        assertTrue(mContext.bindService(secondaryIntent, mSecondaryConnection,
                Context.BIND_AUTO_CREATE));

        synchronized (mSync) {
            if (!mSync.mIsConnected) {
                mSync.wait();
            }
        }
    }

    private static class Sync {
        public boolean mIsConnected;
        public boolean mIsDisConnected;
    }

    @After
    public void tearDown() throws Exception {
        if (mSecondaryConnection != null) {
            mContext.unbindService(mSecondaryConnection);
        }
        if (mIntent != null) {
            mContext.stopService(mIntent);
        }
    }

    @Test
    public void testRemoteCallbackList() throws Exception {
        // Test constructor(default one).
        MockRemoteCallbackList<IInterface> rc = new MockRemoteCallbackList<IInterface>();

        try {
            rc.register(null);
            fail("Should throw NullPointerException");
        } catch (NullPointerException e) {
            // excepted
        }

        try {
            rc.unregister(null);
            fail("Should throw NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }

        int servicePid = mSecondaryService.getPid();
        // Test beginBroadcast, register, unregister. There is only one service binded.
        assertTrue(rc.register(mSecondaryService));
        int index = rc.beginBroadcast();
        assertEquals(1, index);
        IInterface actual = rc.getBroadcastItem(index - 1);
        assertNotNull(actual);
        assertSame(mSecondaryService, actual);
        // Test finishBroadcast(Is it valid to use rc.getBroadcastItem after finishBroadcast)
        rc.finishBroadcast();
        assertTrue(rc.unregister(mSecondaryService));

        rc.register(mSecondaryService);
        rc.beginBroadcast();
        // Process killed. No need to call finishBroadcast, unregister
        android.os.Process.killProcess(servicePid);

        synchronized (mSync) {
            if (!mSync.mIsDisConnected) {
                mSync.wait();
            }
        }
        // sleep some time to wait for onCallbackDied called.
        Thread.sleep(1000);
        // Test onCallbackDied
        assertTrue(rc.isOnCallbackDiedCalled);
    }

    @Test
    public void testKill() {
        MockRemoteCallbackList<IInterface> rc = new MockRemoteCallbackList<IInterface>();

        rc.register(mSecondaryService);
        rc.beginBroadcast();
        rc.finishBroadcast();
        rc.kill();
        // kill() should unregister the callback (beginBroadcast()
        // should return 0) and not allow registering the service again.
        assertEquals(0, rc.beginBroadcast());
        assertFalse(rc.register(mSecondaryService));
    }

    private class MockRemoteCallbackList<E extends IInterface> extends RemoteCallbackList<E> {
        public boolean isOnCallbackDiedCalled;

        @Override
        public void onCallbackDied(E callback) {
            isOnCallbackDiedCalled = true;
            super.onCallbackDied(callback);
        }
    }

    private <T extends IInterface> void flush(RemoteCallbackList<T> rc) {
        // Flush pending callbacks by broadcasting a new callback and waiting for it to be invoked.
        // Since callbacks are invoked in order, any previous pending callbacks would also have been
        // called when this flush() method returns.
        ConditionVariable cv = new ConditionVariable();
        rc.broadcast((service) -> cv.open());
        assertTrue(cv.block(CALLBACK_WAIT_TIMEOUT_SECS * 1000));
    }

    private <T> void assertEmpty(LinkedBlockingQueue<T> queue) {
        // Convert to array and compare for a more helpful error message.
        assertArrayEquals(new Object[0], queue.toArray());
    }

    private <T extends IInterface, U> void flushAndAssertEmpty(RemoteCallbackList<T> rc,
                LinkedBlockingQueue<U> queue) {
        flush(rc);
        assertEmpty(queue);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_FROZEN_STATE_CHANGE_CALLBACK)
    public void testGetExecutor() throws Exception {
        Executor executor =
                new Executor() {
                    @Override
                    public void execute(Runnable r) {}
                };
        RemoteCallbackList<IInterface> rc =
                new RemoteCallbackList.Builder<IInterface>(
                                RemoteCallbackList.FROZEN_CALLEE_POLICY_DROP)
                        .setExecutor(executor)
                        .build();
        assertEquals(executor, rc.getExecutor());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_FROZEN_STATE_CHANGE_CALLBACK)
    public void testGetMaxQueueSize() throws Exception {
        RemoteCallbackList<IInterface> rc =
                new RemoteCallbackList.Builder<IInterface>(
                                RemoteCallbackList.FROZEN_CALLEE_POLICY_ENQUEUE_ALL)
                        .setMaxQueueSize(MAX_QUEUE_SIZE)
                        .setExecutor(Runnable::run)
                        .build();
        assertEquals(MAX_QUEUE_SIZE, rc.getMaxQueueSize());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_FROZEN_STATE_CHANGE_CALLBACK)
    public void testGetFrozenCalleePolicy() throws Exception {
        RemoteCallbackList<IInterface> rc =
                new RemoteCallbackList.Builder<IInterface>(
                                RemoteCallbackList.FROZEN_CALLEE_POLICY_DROP)
                        .setExecutor(Runnable::run)
                        .build();
        assertEquals(RemoteCallbackList.FROZEN_CALLEE_POLICY_DROP, rc.getFrozenCalleePolicy());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_FROZEN_STATE_CHANGE_CALLBACK)
    public void testDropCallbacksWhenFrozen() throws Exception {
        RemoteCallbackList<IInterface> rc = new RemoteCallbackList.Builder<IInterface>(
                RemoteCallbackList.FROZEN_CALLEE_POLICY_DROP)
                .setExecutor(Runnable::run).build();
        rc.register(mSecondaryService);
        freezeProcess();
        waitUntilCallbackHalts(rc, CALLBACK_WAIT_TIMEOUT_SECS * 1000);
        rc.broadcast((service) -> fail("this should not have been invoked"));
        unfreezeProcess();
        flush(rc);
        rc.unregister(mSecondaryService);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_FROZEN_STATE_CHANGE_CALLBACK)
    public void testQueueCallbacksWhenFrozen() throws Exception {
        RemoteCallbackList<IInterface> rc = new RemoteCallbackList.Builder<IInterface>(
                RemoteCallbackList.FROZEN_CALLEE_POLICY_ENQUEUE_ALL)
                .setExecutor(Runnable::run).build();
        rc.register(mSecondaryService);
        LinkedBlockingQueue<IInterface> invokedCallbacks = new LinkedBlockingQueue<>();
        rc.broadcast((service) -> invokedCallbacks.add(service));
        assertArrayEquals(new Object[]{ mSecondaryService }, invokedCallbacks.toArray());
        invokedCallbacks.clear();

        freezeProcess();
        waitUntilCallbackHalts(rc, CALLBACK_WAIT_TIMEOUT_SECS * 1000);
        rc.broadcast((service) -> invokedCallbacks.add(service));
        assertEmpty(invokedCallbacks);

        unfreezeProcess();
        flush(rc);
        assertEquals(mSecondaryService,
                invokedCallbacks.poll(CALLBACK_WAIT_TIMEOUT_SECS, TimeUnit.SECONDS));
        flushAndAssertEmpty(rc, invokedCallbacks);
        rc.unregister(mSecondaryService);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_FROZEN_STATE_CHANGE_CALLBACK)
    public void testQueueMostRecentCallbackWhenFrozen() throws Exception {
        RemoteCallbackList<IInterface> rc = new RemoteCallbackList.Builder<IInterface>(
                RemoteCallbackList.FROZEN_CALLEE_POLICY_ENQUEUE_MOST_RECENT)
                .setExecutor(Runnable::run).build();
        rc.register(mSecondaryService);
        freezeProcess();
        waitUntilCallbackHalts(rc, CALLBACK_WAIT_TIMEOUT_SECS * 1000);
        LinkedBlockingQueue<String> invocationRecords = new LinkedBlockingQueue<>();
        rc.broadcast((service) -> invocationRecords.add("first invocation"));
        rc.broadcast((service) -> invocationRecords.add("second invocation"));
        rc.broadcast((service) -> invocationRecords.add("last invocation"));
        assertEmpty(invocationRecords);
        unfreezeProcess();
        flush(rc);
        assertEquals("last invocation",
                invocationRecords.poll(CALLBACK_WAIT_TIMEOUT_SECS, TimeUnit.SECONDS));
        flushAndAssertEmpty(rc, invocationRecords);
        rc.unregister(mSecondaryService);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_FROZEN_STATE_CHANGE_CALLBACK)
    public void testRemoveQueuedCallbacksOnUnregistration() throws Exception {
        RemoteCallbackList<IInterface> rc = new RemoteCallbackList.Builder<IInterface>(
                RemoteCallbackList.FROZEN_CALLEE_POLICY_ENQUEUE_ALL).build();
        rc.register(mSecondaryService);
        freezeProcess();
        waitUntilCallbackHalts(rc, CALLBACK_WAIT_TIMEOUT_SECS * 1000);
        LinkedBlockingQueue<IInterface> invokedCallbacks = new LinkedBlockingQueue<>();
        rc.broadcast((service) -> invokedCallbacks.add(service));
        assertEmpty(invokedCallbacks);
        rc.unregister(mSecondaryService);
        unfreezeProcess();
        assertEmpty(invokedCallbacks);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_FROZEN_STATE_CHANGE_CALLBACK)
    public void testDropCallbacksWhenQueueGrowsTooBig() throws Exception {
        RemoteCallbackList<IInterface> rc = new RemoteCallbackList.Builder<IInterface>(
                RemoteCallbackList.FROZEN_CALLEE_POLICY_ENQUEUE_ALL).setMaxQueueSize(3).build();
        rc.register(mSecondaryService);
        freezeProcess();
        waitUntilCallbackHalts(rc, CALLBACK_WAIT_TIMEOUT_SECS * 1000);
        LinkedBlockingQueue<String> invocationRecords = new LinkedBlockingQueue<>();
        rc.broadcast((service) -> fail("this should not have been invoked"));
        rc.broadcast((service) -> invocationRecords.add("2nd invocation"));
        rc.broadcast((service) -> invocationRecords.add("3rd invocation"));
        rc.broadcast((service) -> invocationRecords.add("4th invocation"));
        unfreezeProcess();
        flush(rc);
        assertEquals("2nd invocation",
                invocationRecords.poll(CALLBACK_WAIT_TIMEOUT_SECS, TimeUnit.SECONDS));
        assertEquals("3rd invocation",
                invocationRecords.poll(CALLBACK_WAIT_TIMEOUT_SECS, TimeUnit.SECONDS));
        assertEquals("4th invocation",
                invocationRecords.poll(CALLBACK_WAIT_TIMEOUT_SECS, TimeUnit.SECONDS));
        flushAndAssertEmpty(rc, invocationRecords);
        rc.unregister(mSecondaryService);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_FROZEN_STATE_CHANGE_CALLBACK)
    public void testInvokeInterfaceDiedCallback() throws Exception {
        LinkedBlockingQueue<IInterface> invocationRecords = new LinkedBlockingQueue<>();
        RemoteCallbackList<IInterface> rc = new RemoteCallbackList.Builder<IInterface>(
                RemoteCallbackList.FROZEN_CALLEE_POLICY_ENQUEUE_ALL).setInterfaceDiedCallback(
                    (rcl, cb, cookie) -> invocationRecords.add(cb)).build();

        ISecondary service = mSecondaryService;
        assertTrue(rc.register(service));
        android.os.Process.killProcess(service.getPid());
        synchronized (mSync) {
            if (!mSync.mIsDisConnected) {
                mSync.wait();
            }
        }
        assertEquals(service, invocationRecords.poll(CALLBACK_WAIT_TIMEOUT_SECS, TimeUnit.SECONDS));
    }

    private <T extends IInterface> void waitUntilCallbackHalts(
            RemoteCallbackList<T> rc, long timeoutMillis)
            throws InterruptedException, TimeoutException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            int[] i = new int[] {0};
            rc.broadcast(
                    (service) -> {
                        i[0] = 1;
                    });
            if (i[0] == 0) {
                return;
            }
            Thread.yield();
        }
        throw new TimeoutException("Timeout waiting for callbacks to halt");
    }

    private void freezeProcess() throws Exception {
        ConditionVariable cv = new ConditionVariable();
        mSecondaryService
                .asBinder()
                .addFrozenStateChangeCallback(
                        Runnable::run,
                        (who, state) -> {
                            if (state == IBinder.FrozenStateChangeCallback.STATE_FROZEN) {
                                cv.open();
                            }
                        });
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand("am freeze android.os.cts:remote");
        assertTrue(cv.block(CALLBACK_WAIT_TIMEOUT_SECS * 1000));
    }

    private void unfreezeProcess() throws Exception {
        ConditionVariable cv = new ConditionVariable();
        mSecondaryService
                .asBinder()
                .addFrozenStateChangeCallback(
                        Runnable::run,
                        (who, state) -> {
                            if (state == IBinder.FrozenStateChangeCallback.STATE_UNFROZEN) {
                                cv.open();
                            }
                        });
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand("am unfreeze android.os.cts:remote");
        assertTrue(cv.block(CALLBACK_WAIT_TIMEOUT_SECS * 1000));
    }
}
