/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.waitOnAllHandlers;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.TelecomManager;
import android.telecom.cts.screeningtestapp.CallScreeningServiceControl;
import android.telecom.cts.screeningtestapp.CtsCallScreeningService;
import android.telecom.cts.screeningtestapp.ICallScreeningControl;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for tests of the third-party app {@link android.telecom.CallScreeningService}
 * . This class handles the common setup, teardown, and helper methods required for testing call
 * screening interactions with the Telecom framework.
 *
 * <p>Subclasses should implement specific test cases related to different aspects of call
 * screening, such as permission handling, call rejection, call silencing, and interaction with the
 * call log.
 *
 * <p>This base class manages:
 *
 * <ul>
 *   <li>Binding to and controlling the test {@link android.telecom.CallScreeningService} (located
 *       in a separate APK).
 *   <li>Granting and revoking the {@link android.Manifest.permission#READ_CONTACTS} permission to
 *       the test app.
 *   <li>Managing the {@link android.app.role.RoleManager#ROLE_CALL_SCREENING} role, ensuring the
 *       test app holds the role during tests and restoring the previous role holder afterward.
 *   <li>Providing helper methods for simulating incoming and outgoing calls, and for verifying call
 *       log entries.
 *   <li>Cleaning up resources (unbinding from the service, deleting contacts) after tests.
 * </ul>
 *
 * <p>Subclasses can use the protected methods provided by this class to interact with the test app,
 * simulate calls, check permissions, verify call log data and access the useful tools.
 */
public abstract class BaseThirdPartyCallScreeningServiceTest
        extends BaseTelecomTestWithMockServices {
    public static final String EXTRA_NETWORK_IDENTIFIED_EMERGENCY_CALL = "identifiedEmergencyCall";
    private static final String TAG = BaseThirdPartyCallScreeningServiceTest.class.getSimpleName();
    protected static final String TEST_APP_NAME = "CTSCSTest";
    protected static final String TEST_APP_PACKAGE = "android.telecom.cts.screeningtestapp";
    protected static final String TEST_APP_COMPONENT =
            "android.telecom.cts.screeningtestapp/"
                    + "android.telecom.cts.screeningtestapp.CtsCallScreeningService";
    protected static final int ASYNC_TIMEOUT = 10000;
    public static final String ROLE_CALL_SCREENING = RoleManager.ROLE_CALL_SCREENING;
    protected static final Uri TEST_OUTGOING_NUMBER = Uri.fromParts("tel", "6505551212", null);

    protected ICallScreeningControl mCallScreeningControl;
    protected RoleManager mRoleManager;
    private String mPreviousCallScreeningPackage;
    protected PackageManager mPackageManager;
    protected Uri mContactUri;
    protected ContentResolver mContentResolver;
    protected ServiceConnection mServiceConnection; // Make ServiceConnection accessible

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!mShouldTestTelecom) {
            return;
        }
        mRoleManager = mContext.getSystemService(RoleManager.class);
        mPackageManager = mContext.getPackageManager();
        mContentResolver = getInstrumentation().getTargetContext().getContentResolver();
        rememberPreviousCallScreeningApp();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mShouldTestTelecom) {
            resetAndRestoreCallScreening();
        }
        super.tearDown();
    }

    protected void setupCallScreening() throws Exception {
        mServiceConnection = bindToTestApp();
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        // Ensure CTS app holds the call screening role.
        addRoleHolder(ROLE_CALL_SCREENING, CtsCallScreeningService.class.getPackage().getName());
    }

    protected ServiceConnection bindToTestApp() throws Exception {
        Intent bindIntent = new Intent(CallScreeningServiceControl.CONTROL_INTERFACE_ACTION);
        bindIntent.setComponent(CallScreeningServiceControl.CONTROL_INTERFACE_COMPONENT);
        final CountDownLatch bindLatch = new CountDownLatch(1);

        ServiceConnection serviceConnection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        Log.i(TAG, "onServiceConnected: " + name);
                        mCallScreeningControl = ICallScreeningControl.Stub.asInterface(service);
                        bindLatch.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        // The onServiceDisconnected() callback is not invoked when you explicitly
                        // unbind; it's only called when the connection is unexpectedly lost.
                        Log.i(TAG, "onServiceDisconnected: " + name);
                        mCallScreeningControl = null;
                    }
                };

        boolean success =
                mContext.bindService(
                        bindIntent,
                        serviceConnection,
                        Context.BIND_AUTO_CREATE | Context.BIND_ABOVE_CLIENT);
        if (!success) {
            fail("Failed to get control interface -- bind error");
        }
        boolean completedBeforeTimeout = bindLatch.await(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
        assertTrue(completedBeforeTimeout);
        waitForScreeningControl();
        return serviceConnection;
    }

    protected void waitForAppUnbinding() throws RemoteException {
        if (mServiceConnection == null
                || mCallScreeningControl == null
                || !mCallScreeningControl.isBound()) {
            Log.w(TAG, "waitForAppUnbinding: skipping unbind because service is already unbound");
            return;
        }
        Log.i(TAG, "waitForAppUnbinding: requesting control unbind");
        try {
            mContext.unbindService(mServiceConnection);
        } catch (Exception e) {
            Log.w(TAG, "waitForAppUnbinding: hit an exception when calling unbind. e=[" + e + "]");
        }
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        try {
                            return mCallScreeningControl == null
                                    || !mCallScreeningControl.isBound();
                        } catch (RemoteException re) {
                            Log.e(TAG, "RemoteException checking binding", re);
                            return true;
                        }
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "mCallScreeningControl object which represents binding to the test app is NOT "
                        + "null. This means the app is still bound when it should be unbound.");
        Log.i(TAG, "waitForAppUnbinding: done");
    }

    protected void resetAndRestoreCallScreening() throws Exception {
        if (mCallScreeningControl != null) {
            mCallScreeningControl.reset();
        }
        // Remove the test app from the screening role.
        removeRoleHolder(ROLE_CALL_SCREENING, CtsCallScreeningService.class.getPackage().getName());

        if (!TextUtils.isEmpty(mPreviousCallScreeningPackage)) {
            addRoleHolder(ROLE_CALL_SCREENING, mPreviousCallScreeningPackage);
        }
        waitForAppUnbinding();
    }

    private void rememberPreviousCallScreeningApp() {
        runWithShellPermissionIdentity(
                () -> {
                    List<String> callScreeningApps =
                            mRoleManager.getRoleHolders(ROLE_CALL_SCREENING);
                    if (!callScreeningApps.isEmpty()) {
                        mPreviousCallScreeningPackage = callScreeningApps.get(0);
                    } else {
                        mPreviousCallScreeningPackage = null;
                    }
                });
    }

    protected void addRoleHolder(String roleName, String packageName) throws Exception {
        UserHandle user = Process.myUserHandle();
        Executor executor = mContext.getMainExecutor();
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue(1);

        runWithShellPermissionIdentity(
                () ->
                        mRoleManager.addRoleHolderAsUser(
                                roleName,
                                packageName,
                                RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                                user,
                                executor,
                                successful -> {
                                    try {
                                        queue.put(successful);
                                    } catch (InterruptedException e) {
                                        Log.w(
                                                TAG,
                                                String.format(
                                                        "encountered InterruptedException"
                                                                + " e=[%s]",
                                                        e));
                                    }
                                }));
        boolean result = queue.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
        assertTrue(result);
    }

    protected void removeRoleHolder(String roleName, String packageName) throws Exception {
        UserHandle user = Process.myUserHandle();
        Executor executor = mContext.getMainExecutor();
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue(1);

        runWithShellPermissionIdentity(
                () ->
                        mRoleManager.removeRoleHolderAsUser(
                                roleName,
                                packageName,
                                RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP,
                                user,
                                executor,
                                successful -> {
                                    try {
                                        queue.put(successful);
                                    } catch (InterruptedException e) {
                                        Log.w(
                                                TAG,
                                                String.format(
                                                        "encountered InterruptedException"
                                                                + " e=[%s]",
                                                        e));
                                    }
                                }));
        boolean result = queue.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
        assertTrue(result);
    }

    protected void grantReadContactPermission() {
        runWithShellPermissionIdentity(
                () -> {
                    if (mPackageManager != null) {
                        mPackageManager.grantRuntimePermission(
                                TEST_APP_PACKAGE,
                                Manifest.permission.READ_CONTACTS,
                                mContext.getUser());
                    }
                });
    }

    protected void revokeReadContactPermission() {
        runWithShellPermissionIdentity(
                () -> {
                    if (mPackageManager != null) {
                        mPackageManager.revokeRuntimePermission(
                                TEST_APP_PACKAGE,
                                Manifest.permission.READ_CONTACTS,
                                mContext.getUser());
                    }
                });
    }

    protected void verifyPermission(boolean hasPermission) {
        assertEquals(
                hasPermission,
                mPackageManager.checkPermission(Manifest.permission.READ_CONTACTS, TEST_APP_PACKAGE)
                        == PackageManager.PERMISSION_GRANTED);
    }

    protected void placeOutgoingCall(boolean addContact) throws Exception {
        // Setup content observer to notify us when we call log entry is added.
        CountDownLatch callLogEntryLatch = getCallLogEntryLatch();

        Uri contactUri = null;
        if (addContact) {
            contactUri =
                    TestUtils.insertContact(
                            mContentResolver, TEST_OUTGOING_NUMBER.getSchemeSpecificPart());
        }

        try {
            Bundle extras = new Bundle();
            extras.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, TEST_OUTGOING_NUMBER);
            // Create a new outgoing call.
            placeAndVerifyCall(extras);

            disconnectAllCalls();

            // Wait for it to log.
            boolean completedBeforeTimeout =
                    callLogEntryLatch.await(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
            assertTrue(completedBeforeTimeout);
        } finally {
            if (addContact) {
                assertEquals(1, TestUtils.deleteContact(mContentResolver, contactUri));
            }
        }
    }

    protected Uri addIncoming(
            boolean disconnectImmediately, boolean addContact, boolean skipCallLogLatch)
            throws Exception {
        // Add call through TelecomManager; we can't use the test methods since they assume a call
        // makes it through to the InCallService; this is blocked so it shouldn't.
        Uri testNumber = createRandomTestNumber();
        if (addContact) {
            mContactUri =
                    TestUtils.insertContact(mContentResolver, testNumber.getSchemeSpecificPart());
        }

        // Setup content observer to notify us when we call log entry is added.
        CountDownLatch callLogEntryLatch = null;
        if (!skipCallLogLatch) {
            callLogEntryLatch = getCallLogEntryLatch();
        }

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, testNumber);
        mTelecomManager.addNewIncomingCall(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, extras);

        // Wait until the new incoming call is processed.
        waitOnAllHandlers(getInstrumentation());

        if (disconnectImmediately) {
            // Disconnect the call
            disconnectAllCalls();
        }

        // Wait for the content observer to report that we have gotten a new call log entry.
        if (!skipCallLogLatch) {
            boolean completedBeforeTimeout =
                    callLogEntryLatch.await(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
            assertTrue(completedBeforeTimeout);
        }
        return testNumber;
    }

    protected void addIncomingAndVerifyAllowed(boolean addContact) throws Exception {
        Uri testNumber = addIncoming(true, addContact, false);

        // Query the latest entry into the call log.
        Cursor callsCursor =
                mContentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        null,
                        null,
                        null,
                        CallLog.Calls._ID + " DESC limit 1;");
        int numberIndex = callsCursor.getColumnIndex(CallLog.Calls.NUMBER);
        int callTypeIndex = callsCursor.getColumnIndex(CallLog.Calls.TYPE);
        int blockReasonIndex = callsCursor.getColumnIndex(CallLog.Calls.BLOCK_REASON);
        if (callsCursor.moveToNext()) {
            String number = callsCursor.getString(numberIndex);
            int callType = callsCursor.getInt(callTypeIndex);
            int blockReason = callsCursor.getInt(blockReasonIndex);
            assertEquals(testNumber.getSchemeSpecificPart(), number);
            assertEquals(CallLog.Calls.INCOMING_TYPE, callType);
            assertEquals(CallLog.Calls.BLOCK_REASON_NOT_BLOCKED, blockReason);
        } else {
            fail("Call not logged");
        }

        if (addContact && mContactUri != null) {
            assertEquals(1, TestUtils.deleteContact(mContentResolver, mContactUri));
        }
    }

    protected void addIncomingAndVerifyBlocked(boolean addContact) throws Exception {
        Uri testNumber = addIncoming(false, addContact, false);

        // Query the latest entry into the call log.
        Cursor callsCursor =
                mContentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        null,
                        null,
                        null,
                        CallLog.Calls._ID + " DESC limit 1;");
        int numberIndex = callsCursor.getColumnIndex(CallLog.Calls.NUMBER);
        int callTypeIndex = callsCursor.getColumnIndex(CallLog.Calls.TYPE);
        int blockReasonIndex = callsCursor.getColumnIndex(CallLog.Calls.BLOCK_REASON);
        int callScreeningAppNameIndex =
                callsCursor.getColumnIndex(CallLog.Calls.CALL_SCREENING_APP_NAME);
        int callScreeningCmpNameIndex =
                callsCursor.getColumnIndex(CallLog.Calls.CALL_SCREENING_COMPONENT_NAME);
        if (callsCursor.moveToNext()) {
            String number = callsCursor.getString(numberIndex);
            int callType = callsCursor.getInt(callTypeIndex);
            int blockReason = callsCursor.getInt(blockReasonIndex);
            String screeningAppName = callsCursor.getString(callScreeningAppNameIndex);
            String screeningComponentName = callsCursor.getString(callScreeningCmpNameIndex);
            assertEquals(testNumber.getSchemeSpecificPart(), number);
            assertEquals(CallLog.Calls.BLOCKED_TYPE, callType);
            assertEquals(CallLog.Calls.BLOCK_REASON_CALL_SCREENING_SERVICE, blockReason);
            assertEquals(TEST_APP_NAME, screeningAppName);
            assertEquals(TEST_APP_COMPONENT, screeningComponentName);
        } else {
            fail("Blocked call was not logged.");
        }

        if (addContact && mContactUri != null) {
            assertEquals(1, TestUtils.deleteContact(mContentResolver, mContactUri));
        }
    }

    protected void addIncomingAndVerifyCallExtraForSilence(boolean expectedIsSilentRingingExtraSet)
            throws Exception {
        CountDownLatch callLogEntryLatch = getCallLogEntryLatch();
        addIncoming(false, false, true);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        // Verify that the call extra matches expectation
                        Call call = mInCallCallbacks.getService().getLastCall();
                        return expectedIsSilentRingingExtraSet
                                == call.getDetails()
                                        .getExtras()
                                        .getBoolean(Call.EXTRA_SILENT_RINGING_REQUESTED);
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call extra - verification failed, expected the extra "
                        + "EXTRA_SILENT_RINGING_REQUESTED to be set:"
                        + expectedIsSilentRingingExtraSet);

        // Logging does not get registered until we do explicit disconnection
        disconnectAllCalls();
        boolean completedBeforeTimeout =
                callLogEntryLatch.await(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
        assertTrue(completedBeforeTimeout);
    }

    private void disconnectAllCalls() {
        if (mInCallCallbacks != null && mInCallCallbacks.getService() != null) {
            mInCallCallbacks.getService().disconnectAllCalls();
            assertNumCalls(mInCallCallbacks.getService(), 0);
        }
    }

    /**
     * This helper waits for the call screening process to bind and call onServiceConnected. If the
     * onServiceConnected is never called or called too early, an NPE can be hit while running the
     * test.
     */
    protected void waitForScreeningControl() {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return mCallScreeningControl != null;
                    }
                },
                5000,
                "mCallScreeningControl is null which means onServiceConnected was never" +
                 "called");
    }
}
