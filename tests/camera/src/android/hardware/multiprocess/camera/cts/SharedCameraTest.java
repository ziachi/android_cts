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

package android.hardware.multiprocess.camera.cts;

import static junit.framework.Assert.*;

import static org.mockito.Mockito.*;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.cts.Camera2ParameterizedTestCase;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.helpers.StaticMetadata.CheckLevel;
import android.hardware.camera2.params.SharedSessionConfiguration;
import android.hardware.camera2.params.SharedSessionConfiguration.SharedOutputConfiguration;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.internal.camera.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/** Tests for shared camera access where multiple clients can access a camera. */
@RunWith(Parameterized.class)
public final class SharedCameraTest extends Camera2ParameterizedTestCase {
    private static final String TAG = "SharedCameraTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int SETUP_TIMEOUT = 5000; // Remote camera setup timeout (ms).
    private static final int WAIT_TIME = 3000; // Time to wait for process to launch (ms).
    private static final int EVICTION_TIMEOUT = 1000; // Remote camera eviction timeout (ms).
    private static final long PREVIEW_TIME_MS = 2000;
    private ErrorLoggingService.ErrorServiceConnection mErrorServiceConnection;
    private int mProcessPid = -1;
    private int mProcessPid2 = -1;
    private Context mContext;
    private ActivityManager mActivityManager;
    private String mCameraId;
    private Messenger mRemoteMessenger;
    private Messenger mRemoteMessenger2;
    private CameraResultReceiver mResultReceiver;
    private CameraResultReceiver mResultReceiver2;
    private UiDevice mUiDevice;
    protected HashMap<String, StaticMetadata> mAllStaticInfo;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    /** Load validation jni on initialization. */
    static {
        System.loadLibrary("ctscamera2_jni");
    }

    private class CameraResultReceiver extends ResultReceiver {
        boolean mIsFirstClient = false;

        protected CameraResultReceiver(boolean isFirstClient) {
            super(null);
            mIsFirstClient = isFirstClient;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (resultCode == Activity.RESULT_OK) {
                if (mIsFirstClient) {
                    mRemoteMessenger =
                            (Messenger)
                                    resultData.getParcelable(TestConstants.EXTRA_REMOTE_MESSENGER);
                } else {
                    mRemoteMessenger2 =
                            (Messenger)
                                    resultData.getParcelable(TestConstants.EXTRA_REMOTE_MESSENGER);
                }
            }
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mContext = InstrumentationRegistry.getTargetContext();
        /**
         * Workaround for mockito and JB-MR2 incompatibility
         *
         * <p>Avoid java.lang.IllegalArgumentException: dexcache == null
         * https://code.google.com/p/dexmaker/issues/detail?id=2
         */
        System.setProperty("dexmaker.dexcache", mContext.getCacheDir().toString());
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mUiDevice = UiDevice.getInstance(instrumentation);
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mErrorServiceConnection = new ErrorLoggingService.ErrorServiceConnection(mContext);
        mErrorServiceConnection.start();

        mResultReceiver = new CameraResultReceiver(/* isFirstClient */ true);

        mProcessPid =
                startRemoteProcess(
                        SharedCameraActivity.class, "SharedCameraActivityProcess", mResultReceiver);

        mAllStaticInfo = new HashMap<String, StaticMetadata>();
        List<String> hiddenPhysicalIds = new ArrayList<>();
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        for (String cameraId : cameraIdsUnderTest) {
            CameraCharacteristics props = mCameraManager.getCameraCharacteristics(cameraId);
            StaticMetadata staticMetadata =
                    new StaticMetadata(props, CheckLevel.ASSERT, /*collector*/ null);
            mAllStaticInfo.put(cameraId, staticMetadata);

            for (String physicalId : props.getPhysicalCameraIds()) {
                if (!Arrays.asList(cameraIdsUnderTest).contains(physicalId)
                        && !hiddenPhysicalIds.contains(physicalId)) {
                    hiddenPhysicalIds.add(physicalId);
                    props = mCameraManager.getCameraCharacteristics(physicalId);
                    staticMetadata =
                            new StaticMetadata(
                                    mCameraManager.getCameraCharacteristics(physicalId),
                                    CheckLevel.ASSERT, /*collector*/
                                    null);
                    mAllStaticInfo.put(physicalId, staticMetadata);
                }
            }
        }
    }

    @Override
    public void tearDown() throws Exception {
        if (mProcessPid2 != -1) {
            android.os.Process.killProcess(mProcessPid2);
            mProcessPid2 = -1;
            Thread.sleep(WAIT_TIME);
        }
        if (mProcessPid != -1) {
            android.os.Process.killProcess(mProcessPid);
            mProcessPid = -1;
            Thread.sleep(WAIT_TIME);
        }
        if (mErrorServiceConnection != null) {
            mErrorServiceConnection.stop();
            mErrorServiceConnection = null;
        }
        mContext = null;
        mActivityManager = null;
        super.tearDown();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testOpenMixedMode() throws Exception {
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        if (VERBOSE) Log.v(TAG, "CameraManager ids: " + Arrays.toString(cameraIdsUnderTest));
        for (int i = 0; i < cameraIdsUnderTest.length; i++) {
            mCameraId = cameraIdsUnderTest[i];
            if (!mAllStaticInfo.get(mCameraId).sharedSessionConfigurationPresent()) {
                Log.i(TAG, "Camera " + mCameraId + " does not support camera sharing, skipping");
                continue;
            }
            long nativeSharedTest =
                    openSharedCameraNativeClient(mCameraId, /*isPrimaryClient*/true);
            openCameraJavaClient(mCameraId);
            assertCameraEvictedNativeClient(nativeSharedTest);
            closeNativeClient(nativeSharedTest);
            closeCameraJavaClient();
            openSharedCameraJavaClient(mCameraId, /*isPrimaryClient*/true);
            nativeSharedTest = openCameraNativeClient(mCameraId, /*expectFail*/true);
            closeCameraJavaClient();
            closeNativeClient(nativeSharedTest, /*expectCameraAlreadyClosed*/true);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testSharedSessionCreationInvalidConfig() throws Exception {
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        if (VERBOSE) Log.v(TAG, "CameraManager ids: " + Arrays.toString(cameraIdsUnderTest));
        for (int i = 0; i < cameraIdsUnderTest.length; i++) {
            mCameraId = cameraIdsUnderTest[i];
            if (!mCameraManager.isCameraDeviceSharingSupported(mCameraId)) {
                Log.i(TAG, "Camera " + mCameraId + " does not support camera sharing, skipping");
                continue;
            }
            SharedSessionConfiguration sharedSessionConfig =
                    mAllStaticInfo.get(mCameraId).getSharedSessionConfiguration();
            assertNotNull("Shared session configuration is null", sharedSessionConfig);
            int surfaceViewIdx = getSurfaceViewStreamIdx(sharedSessionConfig);
            int imageReaderIdx = getImageReaderStreamIdx(sharedSessionConfig);
            if ((surfaceViewIdx == -1) && (imageReaderIdx == -1)) {
                Log.i(
                        TAG,
                        "Camera "
                                + mCameraId
                                + " does not have any streams supporting either surface"
                                + " view or ImageReader for shared session, skipping");
                continue;
            }
            try {
                openSharedCameraJavaClient(mCameraId, /*isPrimaryClient*/ true);
                performCreateSharedSessionInvalidConfigsJavaClient();
            } finally {
                closeCameraJavaClient();
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testSharedSessionUnsupportedOperations() throws Exception {
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        if (VERBOSE) Log.v(TAG, "CameraManager ids: " + Arrays.toString(cameraIdsUnderTest));
        for (int i = 0; i < cameraIdsUnderTest.length; i++) {
            mCameraId = cameraIdsUnderTest[i];
            if (!mCameraManager.isCameraDeviceSharingSupported(mCameraId)) {
                Log.i(TAG, "Camera " + mCameraId + " does not support camera sharing, skipping");
                continue;
            }
            SharedSessionConfiguration sharedSessionConfig =
                    mAllStaticInfo.get(mCameraId).getSharedSessionConfiguration();
            assertNotNull("Shared session configuration is null", sharedSessionConfig);
            int surfaceViewIdx = getSurfaceViewStreamIdx(sharedSessionConfig);
            int imageReaderIdx = getImageReaderStreamIdx(sharedSessionConfig);
            if ((surfaceViewIdx == -1) && (imageReaderIdx == -1)) {
                Log.i(
                        TAG,
                        "Camera "
                                + mCameraId
                                + " does not have any stream supporting either surface view or"
                                + " image reader, skipping");
                continue;
            }
            long nativeSharedTest = 0;
            try {
                openSharedCameraJavaClient(mCameraId, /*isPrimaryClient*/ true);
                performCreateSharedSessionUnsupportedOperationsJavaClient();
                ArrayList<Integer> sharedStreamArray = new ArrayList<>();
                if (imageReaderIdx != -1) {
                    sharedStreamArray.add(TestConstants.SURFACE_TYPE_IMAGE_READER);
                } else if (surfaceViewIdx != -1) {
                    sharedStreamArray.add(TestConstants.SURFACE_TYPE_SURFACE_VIEW);
                }
                createSharedSessionJavaClient(sharedStreamArray);
                performUnsupportedCaptureSessionCommandsJavaClient();
                if (imageReaderIdx != -1) {
                    SharedOutputConfiguration imgReaderConfig =
                            sharedSessionConfig.getOutputStreamsInformation().get(imageReaderIdx);
                    int imgWidth = imgReaderConfig.getSize().getWidth();
                    int imgHeight = imgReaderConfig.getSize().getHeight();
                    int imgFormat = imgReaderConfig.getFormat();
                    assertCameraDeviceSharingSupportedNativeClient(mCameraId,
                            /*sharingSupported*/true);
                    nativeSharedTest = openSharedCameraNativeClient(mCameraId,
                            /*isPrimaryClient*/false);
                    testPerformUnsupportedOperationsNative(nativeSharedTest, imgWidth, imgHeight,
                            imgFormat);
                    createCaptureSessionNative(nativeSharedTest, imgWidth, imgHeight, imgFormat);
                    testUnsupportedCaptureSessionCommandsNative(nativeSharedTest);
                }
            } finally {
                try {
                    closeCameraJavaClient();
                } finally {
                    if (nativeSharedTest != 0) {
                        closeNativeClient(nativeSharedTest);
                    }
                }
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testSharedSessionTwoJavaClients() throws Exception {
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        if (VERBOSE) Log.v(TAG, "CameraManager ids: " + Arrays.toString(cameraIdsUnderTest));
        // initialize client 2 process
        mResultReceiver2 = new CameraResultReceiver(/* isFirstClient */ false);
        mProcessPid2 =
                startRemoteProcess(
                        SharedCameraActivitySecondaryClient.class,
                        "SharedCameraActivityProcess2",
                        mResultReceiver2);
        for (int i = 0; i < cameraIdsUnderTest.length; i++) {
            mCameraId = cameraIdsUnderTest[i];
            if (!mAllStaticInfo.get(mCameraId).sharedSessionConfigurationPresent()) {
                Log.i(TAG, "Camera " + mCameraId + " does not support camera sharing, skipping");
                continue;
            }
            SharedSessionConfiguration sharedSessionConfig =
                    mAllStaticInfo.get(mCameraId).getSharedSessionConfiguration();
            assertNotNull("Shared session configuration is null", sharedSessionConfig);
            int imageReaderIdx = getImageReaderStreamIdx(sharedSessionConfig);
            if (imageReaderIdx == -1) {
                Log.i(
                        TAG,
                        "Camera "
                                + mCameraId
                                + " does not have a stream supporting image reader and for shared"
                                + " session, skipping");
                continue;
            }
            assertCameraDeviceSharingSupportedJavaClient(mCameraId, /*sharingSupported*/ true);
            try {
                openSharedCameraJavaClient(mCameraId, /* isPrimaryClient */ true);
                // starting new client with same priority replaces the old client as the primary
                openSharedCameraJavaClient(
                        mCameraId, /* isPrimaryClient */
                        true,
                        /*expectedFail*/ false,
                        mRemoteMessenger2);
                ArrayList<Integer> sharedStreamArray = new ArrayList<>();
                sharedStreamArray.add(TestConstants.SURFACE_TYPE_IMAGE_READER);
                createSharedSessionJavaClient(sharedStreamArray, mRemoteMessenger2);
                startPreviewJavaClient(sharedStreamArray, mRemoteMessenger2);
                createSharedSessionJavaClient(sharedStreamArray);
                startPreviewJavaClient(sharedStreamArray);
                SystemClock.sleep(PREVIEW_TIME_MS);
                stopPreviewJavaClient();
                stopPreviewJavaClient(mRemoteMessenger2);
            } finally {
                closeCameraJavaClient();
                closeCameraJavaClient(mRemoteMessenger2);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testCameraDeviceSharingSupported() throws Exception {
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        if (VERBOSE) Log.v(TAG, "CameraManager ids: " + Arrays.toString(cameraIdsUnderTest));
        for (int i = 0; i < cameraIdsUnderTest.length; i++) {
            mCameraId = cameraIdsUnderTest[i];
            if (!mAllStaticInfo.get(mCameraId).sharedSessionConfigurationPresent()) {
                Log.i(TAG, "Camera " + mCameraId + " does not support camera sharing, skipping");
                continue;
            }
            assertTrue(
                    "Camera device sharing is only supported for system cameras. Camera "
                            + mCameraId
                            + " is not a system camera.",
                    mAdoptShellPerm);
            assertCameraDeviceSharingSupportedJavaClient(mCameraId, /*sharingSupported*/true);
            long nativeSharedTest = 0;
            try {
                openSharedCameraJavaClient(mCameraId, /*isPrimaryClient*/true);
                assertCameraDeviceSharingSupportedNativeClient(mCameraId, /*sharingSupported*/true);
                nativeSharedTest =
                        openSharedCameraNativeClient(mCameraId, /*isPrimaryClient*/false);
                // Verify that attempting to open the camera didn't cause anything weird to happen
                // in the other process.
                List<ErrorLoggingService.LogEvent> eventList2 = null;
                boolean timeoutExceptionHit = false;
                try {
                    eventList2 = mErrorServiceConnection.getLog(EVICTION_TIMEOUT);
                } catch (TimeoutException e) {
                    timeoutExceptionHit = true;
                }
                TestUtils.assertNone("SharedCameraActivity received unexpected events: ",
                        eventList2);
                assertTrue("SharedCameraActivity error service exited early", timeoutExceptionHit);
            } finally {
                try {
                    closeCameraJavaClient();
                } finally {
                    if (nativeSharedTest != 0) {
                        closeNativeClient(nativeSharedTest);
                    }
                }
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testCameraDeviceSharingNotSupported() throws Exception {
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        if (VERBOSE) Log.v(TAG, "CameraManager ids: " + Arrays.toString(cameraIdsUnderTest));
        for (int i = 0; i < cameraIdsUnderTest.length; i++) {
            mCameraId = cameraIdsUnderTest[i];
            if (mAllStaticInfo.get(mCameraId).sharedSessionConfigurationPresent()) {
                Log.i(TAG, "Camera " + mCameraId + " does support camera sharing, skipping");
                continue;
            }
            assertCameraDeviceSharingSupportedJavaClient(mCameraId, /*sharingSupported*/false);
            openSharedCameraJavaClient(mCameraId, /*isPrimaryClient*/true, /*expectedFail*/true);
            assertCameraDeviceSharingSupportedNativeClient(mCameraId, /*sharingSupported*/false);
            long nativeSharedTest =
                    openSharedCameraNativeClient(
                            mCameraId, /*isPrimaryClient*/ false, /*expectedFail*/ true);
            closeNativeClient(nativeSharedTest, /*expectCameraAlreadyClosed*/ true);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testClientSharedAccessPriorityChanged() throws Exception {
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        if (VERBOSE) Log.v(TAG, "CameraManager ids: " + Arrays.toString(cameraIdsUnderTest));
        for (int i = 0; i < cameraIdsUnderTest.length; i++) {
            mCameraId = cameraIdsUnderTest[i];
            if (!mCameraManager.isCameraDeviceSharingSupported(mCameraId)) {
                Log.i(TAG, "Camera " + mCameraId + " does not support camera sharing, skipping");
                continue;
            }
            long nativeSharedTest =
                    openSharedCameraNativeClient(mCameraId, /*isPrimaryClient*/ true);
            openSharedCameraJavaClient(mCameraId, /*isPrimaryClient*/ true);
            assertClientAccessPriorityChangedNative(nativeSharedTest, /*isPrimaryClient*/ false);
            mUiDevice.pressHome();
            SystemClock.sleep(WAIT_TIME);
            assertClientAccessPriorityChangedNative(nativeSharedTest, /*isPrimaryClient*/ true);
            assertClientAccessPriorityChangedJava(/*isPrimaryClient*/ false);
            forceCtsActivityToTop();
            assertClientAccessPriorityChangedNative(nativeSharedTest, /*isPrimaryClient*/ false);
            assertClientAccessPriorityChangedJava(/*isPrimaryClient*/ true);
            closeCameraJavaClient();
            assertClientAccessPriorityChangedNative(nativeSharedTest, /*isPrimaryClient*/ true);
            closeNativeClient(nativeSharedTest);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testSharedSessionCreationDifferentStreams() throws Exception {
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        if (VERBOSE) Log.v(TAG, "CameraManager ids: " + Arrays.toString(cameraIdsUnderTest));
        for (int i = 0; i < cameraIdsUnderTest.length; i++) {
            mCameraId = cameraIdsUnderTest[i];
            if (!mCameraManager.isCameraDeviceSharingSupported(mCameraId)) {
                Log.i(TAG, "Camera " + mCameraId + " does not support camera sharing, skipping");
                continue;
            }
            SharedSessionConfiguration sharedSessionConfig =
                    mAllStaticInfo.get(mCameraId).getSharedSessionConfiguration();
            assertNotNull("Shared session configuration is null", sharedSessionConfig);
            if (sharedSessionConfig.getOutputStreamsInformation().size() < 2) {
                Log.i(
                        TAG,
                        "Camera "
                                + mCameraId
                                + " has less than two streams for "
                                + "shared session, skipping");
                continue;
            }
            int surfaceViewIdx = getSurfaceViewStreamIdx(sharedSessionConfig);
            int imageReaderIdx = getImageReaderStreamIdx(sharedSessionConfig);
            if ((surfaceViewIdx == -1) || (imageReaderIdx == -1)) {
                Log.i(
                        TAG,
                        "Camera "
                                + mCameraId
                                + " does not have two different streams supporting surface view and"
                                + " ImageReader for shared session, skipping");
                continue;
            }
            int imgWidth, imgHeight, imgFormat;
            SharedOutputConfiguration imgReaderConfig =
                    sharedSessionConfig.getOutputStreamsInformation().get(imageReaderIdx);
            imgWidth = imgReaderConfig.getSize().getWidth();
            imgHeight = imgReaderConfig.getSize().getHeight();
            imgFormat = imgReaderConfig.getFormat();
            long nativeSharedTest = 0;
            try {
                openSharedCameraJavaClient(mCameraId, /*isPrimaryClient*/ true);
                nativeSharedTest =
                        openSharedCameraNativeClient(mCameraId, /*isPrimaryClient*/ false);
                ArrayList<Integer> sharedStreamArray = new ArrayList<>();
                sharedStreamArray.add(TestConstants.SURFACE_TYPE_SURFACE_VIEW);
                createSharedSessionJavaClient(sharedStreamArray);
                startPreviewJavaClient(sharedStreamArray);
                SystemClock.sleep(PREVIEW_TIME_MS);
                createCaptureSessionNative(nativeSharedTest, imgWidth, imgHeight, imgFormat);
                startSharedStreamingNative(nativeSharedTest);
                SystemClock.sleep(PREVIEW_TIME_MS);
                stopSharedStreamingNative(nativeSharedTest);
                closeSessionNative(nativeSharedTest);
                stopPreviewJavaClient();
            } finally {
                try {
                    closeCameraJavaClient();
                } finally {
                    if (nativeSharedTest != 0) {
                        closeNativeClient(nativeSharedTest);
                    }
                }
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testSharedSessionCreationSameStreams() throws Exception {
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        if (VERBOSE) Log.v(TAG, "CameraManager ids: " + Arrays.toString(cameraIdsUnderTest));
        for (int i = 0; i < cameraIdsUnderTest.length; i++) {
            mCameraId = cameraIdsUnderTest[i];
            if (!mCameraManager.isCameraDeviceSharingSupported(mCameraId)) {
                Log.i(TAG, "Camera " + mCameraId + " does not support camera sharing, skipping");
                continue;
            }
            SharedSessionConfiguration sharedSessionConfig =
                    mAllStaticInfo.get(mCameraId).getSharedSessionConfiguration();
            assertNotNull("Shared session configuration is null", sharedSessionConfig);
            int surfaceViewIdx = getSurfaceViewStreamIdx(sharedSessionConfig);
            int imageReaderIdx = getImageReaderStreamIdx(sharedSessionConfig);
            ArrayList<Integer> sharedStreamArray = new ArrayList<>();
            if (surfaceViewIdx != -1) {
                sharedStreamArray.add(TestConstants.SURFACE_TYPE_SURFACE_VIEW);
            }
            int imgWidth = -1;
            int imgHeight = -1;
            int imgFormat = -1;
            if (imageReaderIdx != -1) {
                sharedStreamArray.add(TestConstants.SURFACE_TYPE_IMAGE_READER);
                SharedOutputConfiguration imgReaderConfig =
                        sharedSessionConfig.getOutputStreamsInformation().get(imageReaderIdx);
                imgWidth = imgReaderConfig.getSize().getWidth();
                imgHeight = imgReaderConfig.getSize().getHeight();
                imgFormat = imgReaderConfig.getFormat();
            }
            long nativeSharedTest = 0;
            try {
                openSharedCameraJavaClient(mCameraId, /*isPrimaryClient*/true);
                nativeSharedTest = openSharedCameraNativeClient(mCameraId,
                        /*isPrimaryClient*/false);
                createSharedSessionJavaClient(sharedStreamArray);
                startPreviewJavaClient(sharedStreamArray);
                SystemClock.sleep(PREVIEW_TIME_MS);
                createCaptureSessionNative(nativeSharedTest, imgWidth, imgHeight, imgFormat);
                startSharedStreamingNative(nativeSharedTest);
                SystemClock.sleep(PREVIEW_TIME_MS);
                stopSharedStreamingNative(nativeSharedTest);
                closeSessionNative(nativeSharedTest);
                stopPreviewJavaClient();
            } finally {
                try {
                    closeCameraJavaClient();
                } finally {
                    if (nativeSharedTest != 0) {
                        closeNativeClient(nativeSharedTest);
                    }
                }
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testSecondaryClientStreamingBeforePrimary() throws Exception {
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        if (VERBOSE) Log.v(TAG, "CameraManager ids: " + Arrays.toString(cameraIdsUnderTest));
        for (int i = 0; i < cameraIdsUnderTest.length; i++) {
            mCameraId = cameraIdsUnderTest[i];
            if (!mCameraManager.isCameraDeviceSharingSupported(mCameraId)) {
                Log.i(TAG, "Camera " + mCameraId + " does not support camera sharing, skipping");
                continue;
            }
            SharedSessionConfiguration sharedSessionConfig =
                    mAllStaticInfo.get(mCameraId).getSharedSessionConfiguration();
            assertNotNull("Shared session configuration is null", sharedSessionConfig);
            int surfaceViewIdx = getSurfaceViewStreamIdx(sharedSessionConfig);
            int imageReaderIdx = getImageReaderStreamIdx(sharedSessionConfig);
            ArrayList<Integer> sharedStreamArray = new ArrayList<>();
            if (surfaceViewIdx != -1) {
                sharedStreamArray.add(TestConstants.SURFACE_TYPE_SURFACE_VIEW);
            }
            int imgWidth = -1;
            int imgHeight = -1;
            int imgFormat = -1;
            if (imageReaderIdx != -1) {
                SharedOutputConfiguration imgReaderConfig =
                        sharedSessionConfig.getOutputStreamsInformation().get(imageReaderIdx);
                imgWidth = imgReaderConfig.getSize().getWidth();
                imgHeight = imgReaderConfig.getSize().getHeight();
                imgFormat = imgReaderConfig.getFormat();
            }
            long nativeSharedTest = 0;
            try {
                openSharedCameraJavaClient(mCameraId, /*isPrimaryClient*/true);
                nativeSharedTest = openSharedCameraNativeClient(mCameraId,
                        /*isPrimaryClient*/false);
                createCaptureSessionNative(nativeSharedTest, imgWidth, imgHeight, imgFormat);
                startSharedStreamingNative(nativeSharedTest);
                SystemClock.sleep(PREVIEW_TIME_MS);
                stopSharedStreamingNative(nativeSharedTest);
                createSharedSessionJavaClient(sharedStreamArray);
                startPreviewJavaClient(sharedStreamArray);
                startSharedStreamingNative(nativeSharedTest);
                SystemClock.sleep(PREVIEW_TIME_MS);
                stopSharedStreamingNative(nativeSharedTest);
                closeSessionNative(nativeSharedTest);
                stopPreviewJavaClient();
            } finally {
                try {
                    closeCameraJavaClient();
                } finally {
                    if (nativeSharedTest != 0) {
                        closeNativeClient(nativeSharedTest);
                    }
                }
            }
        }
    }

    private int getSurfaceViewStreamIdx(SharedSessionConfiguration sharedSessionConfig) {
        int surfaceViewIdx = -1;
        List<SharedOutputConfiguration> sharedConfigs =
                sharedSessionConfig.getOutputStreamsInformation();
        for (int i = 0; i < sharedConfigs.size(); i++) {
            SharedOutputConfiguration outputStream = sharedConfigs.get(i);
            if (outputStream.getSurfaceType() == TestConstants.SURFACE_TYPE_SURFACE_VIEW) {
                surfaceViewIdx = i;
                break;
            }
        }
        return surfaceViewIdx;
    }

    private int getImageReaderStreamIdx(SharedSessionConfiguration sharedSessionConfig) {
        int imageReaderIdx = -1;
        List<SharedOutputConfiguration> sharedConfigs =
                sharedSessionConfig.getOutputStreamsInformation();
        for (int i = 0; i < sharedConfigs.size(); i++) {
            SharedOutputConfiguration outputStream = sharedConfigs.get(i);
            if (outputStream.getSurfaceType() == TestConstants.SURFACE_TYPE_IMAGE_READER) {
                imageReaderIdx = i;
                break;
            }
        }
        return imageReaderIdx;
    }

    private void assertCameraDeviceSharingSupportedJavaClient(String cameraId, boolean expectedTrue)
            throws Exception {
        assertEquals(
                "isCameraDeviceSharingSupported expected to return " + expectedTrue
                + " for camera id " + cameraId,
                expectedTrue, mCameraManager.isCameraDeviceSharingSupported(cameraId));
    }

    private void assertCameraDeviceSharingSupportedNativeClient(String cameraId,
            boolean expectedTrue) throws Exception {
        boolean[] outBoolean = new boolean[1];
        assertTrue(
                "testIsCameraDeviceSharingSupportedNative fail, see log for details",
                testIsCameraDeviceSharingSupportedNative(cameraId, outBoolean));
        assertEquals(
                "isCameraDeviceSharingSupported expected to return " + expectedTrue
                + " for camera id " + cameraId,
                expectedTrue, outBoolean[0]);
    }

    private void openSharedCameraJavaClient(String cameraId, boolean isPrimaryClient)
            throws Exception {
        openSharedCameraJavaClient(
                cameraId, isPrimaryClient, /*expectFail*/ false, mRemoteMessenger);
    }

    private void openSharedCameraJavaClient(
            String cameraId, boolean isPrimaryClient, boolean expectFail) throws Exception {
        openSharedCameraJavaClient(cameraId, isPrimaryClient, expectFail, mRemoteMessenger);
    }

    private void openSharedCameraJavaClient(
            String cameraId, boolean isPrimaryClient, boolean expectFail, Messenger remoteMessenger)
            throws Exception {
        Message msg = Message.obtain(null, TestConstants.OP_OPEN_CAMERA_SHARED);
        msg.getData().putString(TestConstants.EXTRA_CAMERA_ID, cameraId);
        boolean remoteExceptionHit = false;
        try {
            remoteMessenger.send(msg);
        } catch (RemoteException e) {
            remoteExceptionHit = true;
        }
        assertFalse(
                "Error in sending open camera command to SharedCameraActivity", remoteExceptionHit);
        int expectedEvent;
        if (expectFail) {
            expectedEvent = TestConstants.EVENT_CAMERA_ERROR;
        } else {
            expectedEvent =
                    isPrimaryClient
                            ? TestConstants.EVENT_CAMERA_CONNECT_SHARED_PRIMARY
                            : TestConstants.EVENT_CAMERA_CONNECT_SHARED_SECONDARY;
        }
        List<ErrorLoggingService.LogEvent> events =
                mErrorServiceConnection.getLog(SETUP_TIMEOUT, expectedEvent);
        assertNotNull(
                "Did not receive any events from the camera device in remote process!", events);
        Map<Integer, Integer> eventTagCountMap = TestUtils.getEventTagCountMap(events);
        assertTrue(eventTagCountMap.containsKey(expectedEvent));
    }

    private void startPreviewJavaClient(ArrayList<Integer> sharedStreamArray) throws Exception {
        startPreviewJavaClient(sharedStreamArray, mRemoteMessenger);
    }

    private void startPreviewJavaClient(ArrayList<Integer> sharedStreamArray,
            Messenger remoteMessenger) throws Exception {
        Message msg = Message.obtain(null, TestConstants.OP_START_PREVIEW);
        msg.getData()
                .putIntegerArrayList(TestConstants.EXTRA_SHARED_STREAM_ARRAY, sharedStreamArray);
        boolean remoteExceptionHit = false;
        try {
            remoteMessenger.send(msg);
        } catch (RemoteException e) {
            remoteExceptionHit = true;
        }
        assertFalse(
                "Error in sending startPreview command to SharedCameraActivity",
                remoteExceptionHit);
        List<ErrorLoggingService.LogEvent> events =
                mErrorServiceConnection.getLog(
                        SETUP_TIMEOUT, TestConstants.EVENT_CAMERA_PREVIEW_STARTED);
        assertNotNull(
                "Did not receive any events from the camera device in remote process!", events);
        Map<Integer, Integer> eventTagCountMap = TestUtils.getEventTagCountMap(events);
        assertTrue(eventTagCountMap.containsKey(TestConstants.EVENT_CAMERA_PREVIEW_STARTED));
    }

    private void stopPreviewJavaClient() throws Exception {
        stopPreviewJavaClient(mRemoteMessenger);
    }

    private void stopPreviewJavaClient(Messenger remoteMessenger) throws Exception {
        Message msg = Message.obtain(null, TestConstants.OP_STOP_PREVIEW);
        boolean remoteExceptionHit = false;
        try {
            remoteMessenger.send(msg);
        } catch (RemoteException e) {
            remoteExceptionHit = true;
        }
        assertFalse(
                "Error in sending stopPreview command to SharedCameraActivity", remoteExceptionHit);
        List<ErrorLoggingService.LogEvent> events =
                mErrorServiceConnection.getLog(
                        SETUP_TIMEOUT, TestConstants.EVENT_CAMERA_PREVIEW_COMPLETED);
        assertNotNull(
                "Did not receive any events from the camera device in remote process!", events);
        Map<Integer, Integer> eventTagCountMap = TestUtils.getEventTagCountMap(events);
        assertTrue(eventTagCountMap.containsKey(TestConstants.EVENT_CAMERA_PREVIEW_COMPLETED));
    }

    private void createSharedSessionJavaClient(ArrayList<Integer> sharedStreamArray)
            throws Exception {
        createSharedSessionJavaClient(sharedStreamArray, mRemoteMessenger);
    }

    private void createSharedSessionJavaClient(
            ArrayList<Integer> sharedStreamArray, Messenger remoteMessenger) throws Exception {
        Message msg = Message.obtain(null, TestConstants.OP_CREATE_SHARED_SESSION);
        msg.getData()
                .putIntegerArrayList(TestConstants.EXTRA_SHARED_STREAM_ARRAY, sharedStreamArray);
        boolean remoteExceptionHit = false;
        try {
            remoteMessenger.send(msg);
        } catch (RemoteException e) {
            remoteExceptionHit = true;
        }
        assertFalse(
                "Error in sending createSharedSession command to SharedCameraActivity",
                remoteExceptionHit);
        List<ErrorLoggingService.LogEvent> events =
                mErrorServiceConnection.getLog(
                        SETUP_TIMEOUT, TestConstants.EVENT_CAMERA_SESSION_CONFIGURED);
        assertNotNull(
                "Did not receive any events from the camera device in remote process!", events);
        Map<Integer, Integer> eventTagCountMap = TestUtils.getEventTagCountMap(events);
        assertTrue(eventTagCountMap.containsKey(TestConstants.EVENT_CAMERA_SESSION_CONFIGURED));
    }

    private void performCreateSharedSessionInvalidConfigsJavaClient() throws Exception {
        Message msg = Message.obtain(null, TestConstants.OP_CREATE_SHARED_SESSION_INVALID_CONFIGS);
        boolean remoteExceptionHit = false;
        try {
            mRemoteMessenger.send(msg);
        } catch (RemoteException e) {
            remoteExceptionHit = true;
        }
        assertFalse(
                "Error in sending createSharedSession command to SharedCameraActivity",
                remoteExceptionHit);
        List<ErrorLoggingService.LogEvent> events = mErrorServiceConnection.getLog(SETUP_TIMEOUT);
        assertNotNull(
                "Did not receive any events from the camera device in remote process!", events);
        Map<Integer, Integer> eventTagCountMap = TestUtils.getEventTagCountMap(events);
        assertFalse(eventTagCountMap.containsKey(TestConstants.EVENT_CAMERA_SESSION_CONFIGURED));
        assertTrue(
                eventTagCountMap.containsKey(TestConstants.EVENT_CAMERA_SESSION_CONFIGURE_FAILED));
    }

    private void performCreateSharedSessionUnsupportedOperationsJavaClient() throws Exception {
        Message msg = Message.obtain(null, TestConstants.OP_PERFORM_UNSUPPORTED_COMMANDS);
        boolean remoteExceptionHit = false;
        try {
            mRemoteMessenger.send(msg);
        } catch (RemoteException e) {
            remoteExceptionHit = true;
        }
        assertFalse(
                "Error in sending OP_PERFORM_UNSUPPORTED_COMMANDS command to SharedCameraActivity",
                remoteExceptionHit);
        List<ErrorLoggingService.LogEvent> events = mErrorServiceConnection.getLog(SETUP_TIMEOUT);
        assertNotNull(
                "Did not receive any events from the camera device in remote process!", events);
        Map<Integer, Integer> eventTagCountMap = TestUtils.getEventTagCountMap(events);
        assertFalse(eventTagCountMap.containsKey(TestConstants.EVENT_CAMERA_SESSION_CONFIGURED));
        assertTrue(
                eventTagCountMap.containsKey(TestConstants.EVENT_CAMERA_SESSION_CONFIGURE_FAILED));
    }

    private void performUnsupportedCaptureSessionCommandsJavaClient() throws Exception {
        Message msg =
                Message.obtain(null, TestConstants.OP_PERFORM_UNSUPPORTED_CAPTURE_SESSION_COMMANDS);
        boolean remoteExceptionHit = false;
        try {
            mRemoteMessenger.send(msg);
        } catch (RemoteException e) {
            remoteExceptionHit = true;
        }
        assertFalse(
                "Error in sending OP_PERFORM_UNSUPPORTED_CAPTURE_SESSION_COMMANDS command to"
                        + " SharedCameraActivity",
                remoteExceptionHit);
        List<ErrorLoggingService.LogEvent> events = mErrorServiceConnection.getLog(SETUP_TIMEOUT);
        assertNotNull(
                "Did not receive any events from the camera device in remote process!", events);
        Map<Integer, Integer> eventTagCountMap = TestUtils.getEventTagCountMap(events);
        assertFalse(
                eventTagCountMap.containsKey(
                        TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_STARTED));
        assertTrue(
                eventTagCountMap.containsKey(
                        TestConstants.EVENT_CAMERA_UNSUPPORTED_ACTIVITY_FAILED));
    }

    private long openSharedCameraNativeClient(String cameraId, boolean isPrimaryClient)
            throws Exception {
        return testInitAndOpenSharedCameraNative(cameraId, isPrimaryClient, /*expectFail*/false);
    }

    private long openSharedCameraNativeClient(String cameraId, boolean isPrimaryClient,
            boolean expectFail) throws Exception {
        long nativeSharedTest = testInitAndOpenSharedCameraNative(cameraId, isPrimaryClient,
                expectFail);
        assertTrue(
                "testInitAndOpenSharedCameraNative fail, see log for details",
                nativeSharedTest != 0);
        return nativeSharedTest;
    }

    private void openCameraJavaClient(String cameraId) throws Exception {
        Message msg = Message.obtain(null, TestConstants.OP_OPEN_CAMERA);
        msg.getData().putString(TestConstants.EXTRA_CAMERA_ID, cameraId);
        boolean remoteExceptionHit = false;
        try {
            mRemoteMessenger.send(msg);
        } catch (RemoteException e) {
            remoteExceptionHit = true;
        }
        assertFalse(
                "Error in sending open camera command to SharedCameraActivity", remoteExceptionHit);
        List<ErrorLoggingService.LogEvent> events =
                mErrorServiceConnection.getLog(SETUP_TIMEOUT, TestConstants.EVENT_CAMERA_CONNECT);
        assertNotNull("Camera device not connected in remote process!", events);
        TestUtils.assertOnly(TestConstants.EVENT_CAMERA_CONNECT, events);
    }

    private long openCameraNativeClient(String cameraId) throws Exception {
        return openCameraNativeClient(cameraId, /* expectFail */ false);
    }

    private long openCameraNativeClient(String cameraId, boolean expectFail) throws Exception {
        long nativeSharedTest = testInitAndOpenCameraNative(cameraId, expectFail);
        assertTrue("testInitAndOpenCameraNative fail, see log for details", nativeSharedTest != 0);
        return nativeSharedTest;
    }

    private void closeCameraJavaClient() throws Exception {
        closeCameraJavaClient(mRemoteMessenger);
    }

    private void closeCameraJavaClient(Messenger remoteMessenger) throws Exception {
        Message msg = Message.obtain(null, TestConstants.OP_CLOSE_CAMERA);
        boolean remoteExceptionHit = false;
        try {
            remoteMessenger.send(msg);
        } catch (RemoteException e) {
            remoteExceptionHit = true;
        }
        assertFalse(
                "Error in sending close camera command to SharedCameraActivity",
                remoteExceptionHit);
        List<ErrorLoggingService.LogEvent> events =
                mErrorServiceConnection.getLog(SETUP_TIMEOUT, TestConstants.EVENT_CAMERA_CLOSED);
        assertNotNull(
                "Did not receive any events from the camera device in remote process!", events);
        Map<Integer, Integer> eventTagCountMap = TestUtils.getEventTagCountMap(events);
        assertTrue(eventTagCountMap.containsKey(TestConstants.EVENT_CAMERA_CLOSED));
    }

    private void assertClientAccessPriorityChangedJava(boolean isPrimaryClient) throws Exception {
        int expectedEvent;
        expectedEvent = isPrimaryClient
                ? TestConstants.EVENT_CLIENT_ACCESS_PRIORITIES_CHANGED_TO_PRIMARY :
                TestConstants.EVENT_CLIENT_ACCESS_PRIORITIES_CHANGED_TO_SECONDARY;
        List<ErrorLoggingService.LogEvent> events =
                mErrorServiceConnection.getLog(SETUP_TIMEOUT, expectedEvent);
        assertNotNull(
                "Did not receive any events from the camera device in remote process!", events);
        Map<Integer, Integer> eventTagCountMap = TestUtils.getEventTagCountMap(events);
        assertTrue(eventTagCountMap.containsKey(expectedEvent));
    }

    private void assertClientAccessPriorityChangedNative(
            long nativeSharedTest, boolean isPrimaryClient) throws Exception {
        assertTrue(
                "testClientAccessPriorityChangedNative fail, see log for details",
                testClientAccessPriorityChangedNative(nativeSharedTest, isPrimaryClient));
    }

    private void closeNativeClient(long nativeSharedTest) throws Exception {
        closeNativeClient(nativeSharedTest, /* expectCameraAlreadyClosed */ false);
    }

    private void closeNativeClient(long nativeSharedTest, boolean expectCameraAlreadyClosed)
            throws Exception {
        assertTrue(
                "testDeInitAndCloseSharedCameraNative fail, see log for details",
                testDeInitAndCloseSharedCameraNative(nativeSharedTest, expectCameraAlreadyClosed));
    }

    private void assertCameraEvictedNativeClient(long nativeSharedTest) throws Exception {
        assertTrue(
                "testIsCameraEvictedNative fail, see log for details",
                testIsCameraEvictedNative(nativeSharedTest));
    }

    private void createCaptureSessionNative(long nativeSharedTest, int imgWidth, int imgHeight,
            int imgFormat) {
        assertTrue(
                "testCreateCaptureSessionNative fail, see log for details",
                testCreateCaptureSessionNative(nativeSharedTest, imgWidth, imgHeight, imgFormat));
    }

    private void startSharedStreamingNative(long nativeSharedTest) {
        assertTrue(
                "testStartStreamingNative fail, see log for details",
                testStartSharedStreamingNative(nativeSharedTest));
    }

    private void stopSharedStreamingNative(long nativeSharedTest) {
        assertTrue(
                "testStopStreamingNative fail, see log for details",
                testStopSharedStreamingNative(nativeSharedTest));
    }

    private void closeSessionNative(long nativeSharedTest) {
        assertTrue(
                "testCloseSessionNative fail, see log for details",
                 testCloseSessionNative(nativeSharedTest));
    }

    /**
     * Start an activity of the given class running in a remote process with the given name.
     *
     * @param klass the class of the {@link android.app.Activity} to start.
     * @param processName the remote activity name.
     * @param resultReceiver.
     * @throws InterruptedException
     */
    public int startRemoteProcess(
            java.lang.Class<?> klass, String processName, ResultReceiver resultReceiver)
            throws InterruptedException {
        String cameraActivityName = mContext.getPackageName() + ":" + processName;
        // Ensure no running activity process with same name
        List<ActivityManager.RunningAppProcessInfo> list =
                mActivityManager.getRunningAppProcesses();
        assertEquals("Activity " + cameraActivityName + " already running.", -1,
                TestUtils.getPid(cameraActivityName, list));
        Intent activityIntent = new Intent(mContext, klass);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.putExtra(TestConstants.EXTRA_RESULT_RECEIVER, resultReceiver);
        mContext.startActivity(activityIntent);
        Thread.sleep(WAIT_TIME);

        // Fail if activity isn't running
        list = mActivityManager.getRunningAppProcesses();
        int processPid = TestUtils.getPid(cameraActivityName, list);
        assertTrue(
                "Activity "
                        + cameraActivityName
                        + " not found in list of running app"
                        + " processes.",
                -1 != processPid);
        return processPid;
    }

    /**
     * Ensure the CTS activity becomes foreground again instead of launcher.
     */
    private void forceCtsActivityToTop() throws InterruptedException {
        Intent bringToTopIntent = new Intent(mContext, SharedCameraActivity.class);
        bringToTopIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        mContext.startActivity(bringToTopIntent);
        Thread.sleep(WAIT_TIME);
    }

    private static native boolean testIsCameraDeviceSharingSupportedNative(String cameraId,
            boolean[] outResult);
    private static native long testInitAndOpenSharedCameraNative(String cameraId,
            boolean primaryClient, boolean expectFail);
    private static native long testInitAndOpenCameraNative(String cameraId, boolean expectFail);
    private static native boolean testIsCameraEvictedNative(long sharedTestContext);
    private static native boolean testDeInitAndCloseSharedCameraNative(
            long sharedTestContext, boolean expectCameraAlreadyClosed);
    private static native boolean testCreateCaptureSessionNative(long sharedTestContext, int width,
            int height, int format);
    private static native boolean testCloseSessionNative(long sharedTestContext);
    private static native boolean testStartSharedStreamingNative(long sharedTestContext);
    private static native boolean testStopSharedStreamingNative(long sharedTestContext);
    private static native boolean testPerformUnsupportedOperationsNative(long sharedTestContext,
            int width, int height, int format);
    private static native boolean testUnsupportedCaptureSessionCommandsNative(
            long sharedTestContext);
    private static native boolean testClientAccessPriorityChangedNative(long sharedTestContext,
            boolean primaryClient);
}
