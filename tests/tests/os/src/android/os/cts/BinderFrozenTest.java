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

package android.os.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.ConditionVariable;
import android.os.Flags;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class BinderFrozenTest {

    private static final int WAIT_TIMEOUT_SECS = 5;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    /**
     * Tests whether onFrozenStateChanged is called
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_FROZEN_STATE_CHANGE_CALLBACK)
    @ApiTest(apis = {"android.os.IBinder#addFrozenStateChangeCallback"})
    public void testOnFrozenStateChangedCalled() throws Exception {
        final FrozenTestHelper helper = new FrozenTestHelper();
        helper.setup();
        ensureUnfrozenCallback(helper.mResults);
        freezeProcess();
        ensureFrozenCallback(helper.mResults);
        unfreezeProcess();
        ensureUnfrozenCallback(helper.mResults);
    }

    /**
     * Tests that onFrozenStateChanged is not called after callback is removed
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_FROZEN_STATE_CHANGE_CALLBACK)
    @ApiTest(apis = {
        "android.os.IBinder#addFrozenStateChangeCallback",
        "android.os.IBinder#removeFrozenStateChangeCallback"
    })
    public void testOnFrozenStateChangedNotCalledAfterCallbackRemoved() throws Exception {
        final FrozenTestHelper helper = new FrozenTestHelper();
        helper.setup();
        ensureUnfrozenCallback(helper.mResults);
        helper.removeCallback();
        freezeProcess();
        unfreezeProcess();
        assertEquals("No more callbacks should be invoked.", 0, helper.mResults.size());
    }

    /**
     * Tests that onFrozenStateChanged is not called after callback is removed
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_FROZEN_STATE_CHANGE_CALLBACK)
    @ApiTest(apis = {
        "android.os.IBinder#addFrozenStateChangeCallback",
        "android.os.IBinder#removeFrozenStateChangeCallback"
    })
    public void testOnFrozenStateChangedUsingExecutor() throws Exception {
        final FrozenTestHelper helper = new FrozenTestHelper();
        CompletableFuture<Runnable> future = new CompletableFuture<>();
        Executor capturingExecutor = r -> future.complete(r);
        helper.setup(capturingExecutor);
        Runnable capturedRunnable = future.get(WAIT_TIMEOUT_SECS, TimeUnit.SECONDS);
        helper.removeCallback();
        assertNotNull(capturedRunnable);
        assertEquals(0, helper.mResults.size());
        capturedRunnable.run();
        assertEquals(1, helper.mResults.size());
    }

    static class FrozenTestHelper {
        IBinder mBinder;
        IBinder.FrozenStateChangeCallback mCallback;
        IEmptyService mService;
        public LinkedBlockingQueue<Boolean> mResults;

        void setup() throws RemoteException {
            setup(InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getMainExecutor());
        }

        void setup(Executor executor) throws RemoteException {
            final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            final ConditionVariable connected = new ConditionVariable();
            mService = null;

            ServiceConnection serviceConnection = new ServiceConnection() {
                public void onServiceConnected(ComponentName className,
                        IBinder binder) {
                    mService = IEmptyService.Stub.asInterface(binder);
                    connected.open();
                }
                public void onServiceDisconnected(ComponentName className) {
                    mService = null;
                }
            };
            // Connect to EmptyService in another process
            final Intent remoteIntent = new Intent(IEmptyService.class.getName());
            remoteIntent.setPackage(context.getPackageName());
            context.bindService(remoteIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            if (!connected.block(WAIT_TIMEOUT_SECS * 1000)) {
                fail("Couldn't connect to EmptyService");
            }
            try {
                mBinder = mService.getToken();
            } catch (RemoteException re) {
                fail("Couldn't get binder: " + re);
            }
            mResults = new LinkedBlockingQueue<>();
            mCallback = (IBinder who, int state) ->
                    mResults.offer(state == IBinder.FrozenStateChangeCallback.STATE_FROZEN);
            mBinder.addFrozenStateChangeCallback(executor, mCallback);
            if (mCallback == null) {
                fail("Unable to add a callback");
            }
            context.unbindService(serviceConnection);
        }

        public void removeCallback() {
            mBinder.removeFrozenStateChangeCallback(mCallback);
        }
    }

    private void ensureFrozenCallback(LinkedBlockingQueue<Boolean> queue)
            throws InterruptedException {
        assertEquals(Boolean.TRUE, queue.poll(WAIT_TIMEOUT_SECS, TimeUnit.SECONDS));
    }

    private void ensureUnfrozenCallback(LinkedBlockingQueue<Boolean> queue)
            throws InterruptedException {
        assertEquals(Boolean.FALSE, queue.poll(WAIT_TIMEOUT_SECS, TimeUnit.SECONDS));
    }

    private String executeShellCommand(String cmd) throws Exception {
        return UiDevice.getInstance(
                InstrumentationRegistry.getInstrumentation()).executeShellCommand(cmd);
    }

    private void freezeProcess() throws Exception {
        executeShellCommand("am freeze android.os.cts:remote");
    }

    private void unfreezeProcess() throws Exception {
        executeShellCommand("am unfreeze android.os.cts:remote");
    }
}
