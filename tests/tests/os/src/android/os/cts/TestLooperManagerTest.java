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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Flags;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.os.TestLooperManager;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class TestLooperManagerTest {
    private static final String TAG = "TestLooperManagerTest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MESSAGE_QUEUE_TESTABILITY)
    public void testMainThread() throws Exception {
        doTestSharedMessageQueue(Looper.getMainLooper());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MESSAGE_QUEUE_TESTABILITY)
    public void testCustomThread() throws Exception {
        final HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        doTest(thread.getLooper());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MESSAGE_QUEUE_TESTABILITY)
    public void testOnMainThread() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            try {
                                doTestSharedMessageQueue(Looper.myLooper());
                            } catch (Exception e) {
                                throw new RuntimeException("Unhandled exception", e);
                            }
                        });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MESSAGE_QUEUE_TESTABILITY)
    public void peekWhen_future() {
        final HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        final Looper looper = thread.getLooper();
        final TestLooperManager tlm =
                InstrumentationRegistry.getInstrumentation()
                        .acquireLooperManager(looper);
        try {
            assertNull("Queue is expected to be empty", tlm.poll());
            long now = SystemClock.uptimeMillis();
            final Handler handler = new Handler(looper);
            handler.postDelayed(() -> {}, 10000);

            final Long when = tlm.peekWhen();
            assertNotNull(when);
            Log.d(TAG, "when=" + when + " now=" + now + " now+10000=" + (now + 10000));

            assertTrue(when >= (now + 10000));
        } finally {
            tlm.release();
            thread.quit();
        }
    }

    private void doTest(Looper looper) throws Exception {
        final TestLooperManager tlm =
                InstrumentationRegistry.getInstrumentation().acquireLooperManager(looper);

        final Handler handler = new Handler(looper);
        final CountDownLatch latch = new CountDownLatch(1);

        assertFalse(tlm.hasMessages(handler, null, 42));
        assertFalse(tlm.isBlockedOnSyncBarrier());
        handler.sendEmptyMessage(42);
        handler.post(() -> {
            latch.countDown();
        });
        assertTrue(tlm.hasMessages(handler, null, 42));
        assertFalse(latch.await(100, TimeUnit.MILLISECONDS));

        final Long firstWhen = tlm.peekWhen();
        assertNotNull(firstWhen);
        final Message first = tlm.poll();
        assertEquals(42, first.what);
        assertNull("Test expected a null callback: " + first, first.getCallback());
        assertEquals("Test expected first.when=" + (long) firstWhen + " but first.when=" +
                first.getWhen() + " message: " + first, (long) firstWhen, first.getWhen());
        tlm.execute(first);
        assertFalse(tlm.hasMessages(handler, null, 42));
        assertFalse(latch.await(100, TimeUnit.MILLISECONDS));
        tlm.recycle(first);

        final Long secondWhen = tlm.peekWhen();
        assertNotNull(secondWhen);
        final Message second = tlm.poll();
        assertNotNull(second);
        assertNotNull(second.getCallback());
        assertEquals("Test expected second.when=" + (long) secondWhen + " but second.when="
                + second.getWhen() + " message: " + second, (long) secondWhen, second.getWhen());
        tlm.execute(second);
        assertFalse(tlm.hasMessages(handler, null, 42));
        assertTrue(latch.await(100, TimeUnit.MILLISECONDS));
        tlm.recycle(second);

        assertNull("Test expected a null message", tlm.poll());

        MessageQueue mQueue = looper.getQueue();
        int token = mQueue.postSyncBarrier();
        if (!tlm.isBlockedOnSyncBarrier()) {
            Message tmp = tlm.poll();
            fail("Expected to be blocked on sync barrier. Message: "
                    + (tmp != null ? tmp : "null"));
        }
        handler.sendEmptyMessage(42);
        if (!tlm.isBlockedOnSyncBarrier()) {
            Message tmp = tlm.poll();
            fail("Expected to be blocked on sync barrier. Message: "
                    + (tmp != null ? tmp : "null"));
        }
        mQueue.removeSyncBarrier(token);
        assertFalse(tlm.isBlockedOnSyncBarrier());

        tlm.release();
    }

    private void doTestSharedMessageQueue(Looper looper) throws Exception {
        final TestLooperManager tlm =
                InstrumentationRegistry.getInstrumentation().acquireLooperManager(looper);
        final Handler handler = new Handler(looper);

        Message first = Message.obtain(handler);
        first.what = 42;
        handler.sendMessageDelayed(first, 10000);

        boolean found = false;
        List<Message> otherMessages = new ArrayList<>();
        while (true) {
            Message m = tlm.poll();
            if (m == null) {
                break;
            }
            /* if an outside task has queued up a message, simply execute it and move on */
            tlm.execute(m);
            tlm.recycle(m);
            otherMessages.add(m);
            if (m == first) {
                found = true;
                break;
            }
        }
        assertTrue("Expected message not found but saw " + otherMessages, found);
        tlm.release();
    }
}
