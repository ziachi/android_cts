/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.app.cts.broadcasts;

import static android.content.Context.RECEIVER_EXPORTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.BroadcastReceiver.PendingResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(BroadcastsTestRunner.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class BroadcastReceiverTest extends BaseBroadcastTest {
    private static final int RESULT_INITIAL_CODE = 1;
    private static final String RESULT_INITIAL_DATA = "initial data";

    private static final int RESULT_INTERNAL_FINAL_CODE = 7;
    private static final String RESULT_INTERNAL_FINAL_DATA = "internal final data";

    private static final String ACTION_BROADCAST_INTERNAL =
            "com.android.cts.broadcasts.BROADCAST_INTERNAL";
    private static final String ACTION_BROADCAST_MOCKTEST =
            "com.android.cts.broadcasts.BROADCAST_MOCKTEST";
    private static final String ACTION_BROADCAST_TESTABORT =
            "com.android.cts.broadcasts.BROADCAST_TESTABORT";
    private static final String ACTION_BROADCAST_DISABLED =
            "com.android.cts.broadcasts.BROADCAST_DISABLED";
    private static final String TEST_PACKAGE_NAME = "com.android.cts.broadcasts";

    private static final String SIGNATURE_PERMISSION =
            "com.android.cts.broadcasts.SIGNATURE_PERMISSION";

    private static final long SEND_BROADCAST_TIMEOUT = 15000;

    private static final ComponentName DISABLEABLE_RECEIVER = new ComponentName(TEST_PACKAGE_NAME,
            "android.app.cts.broadcasts.MockReceiverDisableable");

    @Test
    public void testConstructor() {
        new MockReceiverInternal();
    }

    @Test
    public void testAccessDebugUnregister() {
        MockReceiverInternal mockReceiver = new MockReceiverInternal();
        assertFalse(mockReceiver.getDebugUnregister());

        mockReceiver.setDebugUnregister(true);
        assertTrue(mockReceiver.getDebugUnregister());

        mockReceiver.setDebugUnregister(false);
        assertFalse(mockReceiver.getDebugUnregister());
    }

    @Test
    public void testSetOrderedHint() {
        MockReceiverInternal mockReceiver = new MockReceiverInternal();

        /*
         * Let's just test to make sure the method doesn't fail for this one.
         * It's marked as "for internal use".
         */
        mockReceiver.setOrderedHint(true);
        mockReceiver.setOrderedHint(false);
    }

    private class MockReceiverInternal extends BroadcastReceiver  {
        protected boolean mCalledOnReceive = false;
        private IBinder mIBinder;
        private final Intent mServiceIntentToPeek;

        MockReceiverInternal() {
            this(null);
        }

        MockReceiverInternal(Intent serviceIntentToPeek) {
            mServiceIntentToPeek = serviceIntentToPeek;
        }

        @Override
        public synchronized void onReceive(Context context, Intent intent) {
            mCalledOnReceive = true;
            if (mServiceIntentToPeek != null) {
                mIBinder = peekService(context, mServiceIntentToPeek);
            }
            notifyAll();
        }

        public void reset() {
            mCalledOnReceive = false;
        }

        public synchronized void waitForReceiver(long timeout)
                throws InterruptedException {
            if (!mCalledOnReceive) {
                wait(timeout);
            }
            assertTrue(mCalledOnReceive);
        }

        public synchronized boolean waitForReceiverNoException(long timeout)
                throws InterruptedException {
            if (!mCalledOnReceive) {
                wait(timeout);
            }
            return mCalledOnReceive;
        }

        public IBinder getIBinder() {
            return mIBinder;
        }
    }

    private class MockReceiverInternalOrder extends MockReceiverInternal  {
        @Override
        public synchronized void onReceive(Context context, Intent intent) {
            setResultCode(RESULT_INTERNAL_FINAL_CODE);
            setResultData(RESULT_INTERNAL_FINAL_DATA);

            super.onReceive(context, intent);
        }
    }

    private class MockReceiverInternalVerifyUncalled extends MockReceiverInternal {
        final int mExpectedInitialCode;

        MockReceiverInternalVerifyUncalled(int initialCode) {
            mExpectedInitialCode = initialCode;
        }

        @Override
        public synchronized void onReceive(Context context, Intent intent) {
            // only update to the expected final values if we're still in the
            // initial conditions.  The intermediate receiver would have
            // updated the result code if it [inappropriately] ran.
            if (getResultCode() == mExpectedInitialCode) {
                setResultCode(RESULT_INTERNAL_FINAL_CODE);
            }

            super.onReceive(context, intent);
        }
    }

    public static class MockAsyncReceiver extends BroadcastReceiver {
        public CompletableFuture<PendingResult> pendingResult = new CompletableFuture<>();

        @Override
        public void onReceive(Context context, Intent intent) {
            pendingResult.complete(goAsync());
        }
    }

    @Test
    public void testOnReceive() throws Exception {
        MockReceiverInternal internalReceiver = new MockReceiverInternal();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_BROADCAST_INTERNAL);
        mContext.registerReceiver(internalReceiver, filter, RECEIVER_EXPORTED);
        try {
            assertEquals(0, internalReceiver.getResultCode());
            assertEquals(null, internalReceiver.getResultData());
            assertEquals(null, internalReceiver.getResultExtras(false));

            mContext.sendBroadcast(new Intent(ACTION_BROADCAST_INTERNAL)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
            internalReceiver.waitForReceiver(SEND_BROADCAST_TIMEOUT);
        } finally {
            mContext.unregisterReceiver(internalReceiver);
        }
    }

    @Test
    @AppModeFull
    public void testManifestReceiverPackage() throws Exception {
        MockReceiverInternal internalReceiver = new MockReceiverInternal();

        Bundle map = new Bundle();
        map.putString(MockReceiver.RESULT_EXTRAS_INVARIABLE_KEY,
                MockReceiver.RESULT_EXTRAS_INVARIABLE_VALUE);
        map.putString(MockReceiver.RESULT_EXTRAS_REMOVE_KEY,
                MockReceiver.RESULT_EXTRAS_REMOVE_VALUE);
        mContext.sendOrderedBroadcast(
                new Intent(ACTION_BROADCAST_MOCKTEST)
                        .setPackage(TEST_PACKAGE_NAME).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                null, internalReceiver,
                null, RESULT_INITIAL_CODE, RESULT_INITIAL_DATA, map);
        internalReceiver.waitForReceiver(SEND_BROADCAST_TIMEOUT);

        // These are set by MockReceiver.
        assertEquals(MockReceiver.RESULT_CODE, internalReceiver.getResultCode());
        assertEquals(MockReceiver.RESULT_DATA, internalReceiver.getResultData());

        Bundle resultExtras = internalReceiver.getResultExtras(false);
        assertEquals(MockReceiver.RESULT_EXTRAS_INVARIABLE_VALUE,
                resultExtras.getString(MockReceiver.RESULT_EXTRAS_INVARIABLE_KEY));
        assertEquals(MockReceiver.RESULT_EXTRAS_ADD_VALUE,
                resultExtras.getString(MockReceiver.RESULT_EXTRAS_ADD_KEY));
        assertNull(resultExtras.getString(MockReceiver.RESULT_EXTRAS_REMOVE_KEY));
    }

    @Test
    @AppModeFull
    public void testManifestReceiverComponent() throws Exception {
        MockReceiverInternal internalReceiver = new MockReceiverInternal();

        Bundle map = new Bundle();
        map.putString(MockReceiver.RESULT_EXTRAS_INVARIABLE_KEY,
                MockReceiver.RESULT_EXTRAS_INVARIABLE_VALUE);
        map.putString(MockReceiver.RESULT_EXTRAS_REMOVE_KEY,
                MockReceiver.RESULT_EXTRAS_REMOVE_VALUE);
        mContext.sendOrderedBroadcast(
                new Intent(ACTION_BROADCAST_MOCKTEST)
                        .setClass(mContext, MockReceiver.class)
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                null, internalReceiver,
                null, RESULT_INITIAL_CODE, RESULT_INITIAL_DATA, map);
        internalReceiver.waitForReceiver(SEND_BROADCAST_TIMEOUT);

        // These are set by MockReceiver.
        assertEquals(MockReceiver.RESULT_CODE, internalReceiver.getResultCode());
        assertEquals(MockReceiver.RESULT_DATA, internalReceiver.getResultData());

        Bundle resultExtras = internalReceiver.getResultExtras(false);
        assertEquals(MockReceiver.RESULT_EXTRAS_INVARIABLE_VALUE,
                resultExtras.getString(MockReceiver.RESULT_EXTRAS_INVARIABLE_KEY));
        assertEquals(MockReceiver.RESULT_EXTRAS_ADD_VALUE,
                resultExtras.getString(MockReceiver.RESULT_EXTRAS_ADD_KEY));
        assertNull(resultExtras.getString(MockReceiver.RESULT_EXTRAS_REMOVE_KEY));
    }

    @Test
    @AppModeFull
    public void testManifestReceiverPermission() throws Exception {
        MockReceiverInternal internalReceiver = new MockReceiverInternal();

        Bundle map = new Bundle();
        map.putString(MockReceiver.RESULT_EXTRAS_INVARIABLE_KEY,
                MockReceiver.RESULT_EXTRAS_INVARIABLE_VALUE);
        map.putString(MockReceiver.RESULT_EXTRAS_REMOVE_KEY,
                MockReceiver.RESULT_EXTRAS_REMOVE_VALUE);
        mContext.sendOrderedBroadcast(
                new Intent(ACTION_BROADCAST_MOCKTEST)
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                SIGNATURE_PERMISSION, internalReceiver,
                null, RESULT_INITIAL_CODE, RESULT_INITIAL_DATA, map);
        internalReceiver.waitForReceiver(SEND_BROADCAST_TIMEOUT);

        // These are set by MockReceiver.
        assertEquals(MockReceiver.RESULT_CODE, internalReceiver.getResultCode());
        assertEquals(MockReceiver.RESULT_DATA, internalReceiver.getResultData());

        Bundle resultExtras = internalReceiver.getResultExtras(false);
        assertEquals(MockReceiver.RESULT_EXTRAS_INVARIABLE_VALUE,
                resultExtras.getString(MockReceiver.RESULT_EXTRAS_INVARIABLE_KEY));
        assertEquals(MockReceiver.RESULT_EXTRAS_ADD_VALUE,
                resultExtras.getString(MockReceiver.RESULT_EXTRAS_ADD_KEY));
        assertNull(resultExtras.getString(MockReceiver.RESULT_EXTRAS_REMOVE_KEY));
    }

    @Test
    public void testNoManifestReceiver() throws Exception {
        MockReceiverInternal internalReceiver = new MockReceiverInternal();

        Bundle map = new Bundle();
        map.putString(MockReceiver.RESULT_EXTRAS_INVARIABLE_KEY,
                MockReceiver.RESULT_EXTRAS_INVARIABLE_VALUE);
        map.putString(MockReceiver.RESULT_EXTRAS_REMOVE_KEY,
                MockReceiver.RESULT_EXTRAS_REMOVE_VALUE);
        mContext.sendOrderedBroadcast(
                new Intent(ACTION_BROADCAST_MOCKTEST).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                null, internalReceiver,
                null, RESULT_INITIAL_CODE, RESULT_INITIAL_DATA, map);
        internalReceiver.waitForReceiver(SEND_BROADCAST_TIMEOUT);

        // The MockReceiver should not have run, so we should still have the initial result.
        assertEquals(RESULT_INITIAL_CODE, internalReceiver.getResultCode());
        assertEquals(RESULT_INITIAL_DATA, internalReceiver.getResultData());

        Bundle resultExtras = internalReceiver.getResultExtras(false);
        assertEquals(MockReceiver.RESULT_EXTRAS_INVARIABLE_VALUE,
                resultExtras.getString(MockReceiver.RESULT_EXTRAS_INVARIABLE_KEY));
        assertNull(resultExtras.getString(MockReceiver.RESULT_EXTRAS_ADD_KEY));
        assertEquals(MockReceiver.RESULT_EXTRAS_REMOVE_VALUE,
                resultExtras.getString(MockReceiver.RESULT_EXTRAS_REMOVE_KEY));
    }

    @Test
    @AppModeFull
    public void testAbortBroadcast() throws Exception {
        MockReceiverInternalOrder internalOrderReceiver = new MockReceiverInternalOrder();

        assertEquals(0, internalOrderReceiver.getResultCode());
        assertNull(internalOrderReceiver.getResultData());
        assertNull(internalOrderReceiver.getResultExtras(false));

        Bundle map = new Bundle();
        map.putString(MockReceiver.RESULT_EXTRAS_INVARIABLE_KEY,
                MockReceiver.RESULT_EXTRAS_INVARIABLE_VALUE);
        map.putString(MockReceiver.RESULT_EXTRAS_REMOVE_KEY,
                MockReceiver.RESULT_EXTRAS_REMOVE_VALUE);
        // The order of the receiver is:
        // MockReceiverFirst --> MockReceiverAbort --> MockReceiver --> internalOrderReceiver.
        // And MockReceiver is the receiver which will be aborted.
        mContext.sendOrderedBroadcast(
                new Intent(ACTION_BROADCAST_TESTABORT)
                        .setPackage(TEST_PACKAGE_NAME).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                null, internalOrderReceiver,
                null, RESULT_INITIAL_CODE, RESULT_INITIAL_DATA, map);
        internalOrderReceiver.waitForReceiver(SEND_BROADCAST_TIMEOUT);

        assertEquals(RESULT_INTERNAL_FINAL_CODE, internalOrderReceiver.getResultCode());
        assertEquals(RESULT_INTERNAL_FINAL_DATA, internalOrderReceiver.getResultData());
        Bundle resultExtras = internalOrderReceiver.getResultExtras(false);
        assertEquals(MockReceiver.RESULT_EXTRAS_INVARIABLE_VALUE,
                resultExtras.getString(MockReceiver.RESULT_EXTRAS_INVARIABLE_KEY));
        assertEquals(MockReceiver.RESULT_EXTRAS_REMOVE_VALUE,
                resultExtras.getString(MockReceiver.RESULT_EXTRAS_REMOVE_KEY));
        assertEquals(MockReceiverFirst.RESULT_EXTRAS_FIRST_VALUE,
                resultExtras.getString(MockReceiverFirst.RESULT_EXTRAS_FIRST_KEY));
        assertEquals(MockReceiverAbort.RESULT_EXTRAS_ABORT_VALUE,
                resultExtras.getString(MockReceiverAbort.RESULT_EXTRAS_ABORT_KEY));
    }

    @Test
    public void testDisabledBroadcastReceiver() throws Exception {
        PackageManager pm = mContext.getPackageManager();

        MockReceiverInternalVerifyUncalled lastReceiver =
                new MockReceiverInternalVerifyUncalled(RESULT_INITIAL_CODE);
        assertEquals(0, lastReceiver.getResultCode());

        pm.setComponentEnabledSetting(DISABLEABLE_RECEIVER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);

        mContext.sendOrderedBroadcast(
                new Intent(ACTION_BROADCAST_DISABLED).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                null, lastReceiver,
                null, RESULT_INITIAL_CODE, RESULT_INITIAL_DATA, new Bundle());
        lastReceiver.waitForReceiver(SEND_BROADCAST_TIMEOUT);

        assertEquals(RESULT_INTERNAL_FINAL_CODE, lastReceiver.getResultCode());
    }

    @Test
    public void testPeekService() throws Exception {
        final Intent serviceIntent = new Intent().setComponent(
                new ComponentName(HELPER_PKG1, HELPER_SERVICE));
        MockReceiverInternal internalReceiver = new MockReceiverInternal(serviceIntent);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_BROADCAST_INTERNAL);
        mContext.registerReceiver(internalReceiver, filter, RECEIVER_EXPORTED);
        try {
            mContext.sendBroadcast(new Intent(ACTION_BROADCAST_INTERNAL)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
            internalReceiver.waitForReceiver(SEND_BROADCAST_TIMEOUT);
            assertNull(internalReceiver.getIBinder());

            final TestServiceConnection connection = bindToHelperService(HELPER_PKG1);
            try {
                assertNotNull(connection.getService());
                internalReceiver.reset();
                mContext.sendBroadcast(new Intent(ACTION_BROADCAST_INTERNAL)
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND));
                internalReceiver.waitForReceiver(SEND_BROADCAST_TIMEOUT);
                assertNotNull(internalReceiver.getIBinder());
            } finally {
                connection.unbind();
            }
        } finally {
            mContext.unregisterReceiver(internalReceiver);
        }
    }

    @Test
    public void testAsync() throws Exception {
        final MockAsyncReceiver asyncReceiver = new MockAsyncReceiver();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_BROADCAST_INTERNAL);
        mContext.registerReceiver(asyncReceiver, filter, RECEIVER_EXPORTED);

        final Intent intent = new Intent(ACTION_BROADCAST_INTERNAL)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        final MockReceiverInternal resultReceiver = new MockReceiverInternal();
        mContext.sendOrderedBroadcast(intent, null, resultReceiver, null, 24, null, null);

        final PendingResult res = asyncReceiver.pendingResult.get(SEND_BROADCAST_TIMEOUT,
                TimeUnit.MILLISECONDS);
        res.setResultCode(42);
        res.finish();

        resultReceiver.waitForReceiver(SEND_BROADCAST_TIMEOUT);
        assertEquals(42, resultReceiver.getResultCode());
    }

    @Test
    public void testNewPhotoBroadcast_notReceived() throws Exception {
        MockReceiverInternal internalReceiver = new MockReceiverInternal();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Camera.ACTION_NEW_PICTURE);
        mContext.registerReceiver(internalReceiver, filter, RECEIVER_EXPORTED);
        assertFalse(internalReceiver.waitForReceiverNoException(SEND_BROADCAST_TIMEOUT));
    }

    @Test
    public void testNewVideoBroadcast_notReceived() throws Exception {
        MockReceiverInternal internalReceiver = new MockReceiverInternal();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Camera.ACTION_NEW_VIDEO);
        mContext.registerReceiver(internalReceiver, filter, RECEIVER_EXPORTED);
        assertFalse(internalReceiver.waitForReceiverNoException(SEND_BROADCAST_TIMEOUT));
    }

    private static byte[] executeShellCommand(String cmd) throws Exception {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(cmd);
        try (FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            FileUtils.copy(in, out);
            return out.toByteArray();
        }
    }
}
