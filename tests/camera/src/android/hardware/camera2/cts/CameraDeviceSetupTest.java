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

package android.hardware.camera2.cts;

import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.JPEG;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.JPEG_R;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.PRIV;
import static android.hardware.camera2.cts.CameraTestUtils.MaxStreamSizes.YUV;
import static android.hardware.camera2.cts.CameraTestUtils.assertNull;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.cts.CameraTestUtils.MockStateCallback;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.ImageReader;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;
import android.util.Range;
import android.util.Size;

import com.android.ex.camera2.blocking.BlockingSessionCallback;
import com.android.ex.camera2.blocking.BlockingStateCallback;
import com.android.internal.camera.flags.Flags;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tests the functionality of {@link CameraDevice.CameraDeviceSetup} APIs.
 * <p>
 * NOTE: Functionality of {@link CameraDevice.CameraDeviceSetup#createCaptureRequest} and
 * {@link CameraDevice.CameraDeviceSetup#isSessionConfigurationSupported} is tested by
 * {@link FeatureCombinationTest}
 */
@RunWith(Parameterized.class)
public class CameraDeviceSetupTest extends Camera2AndroidTestCase {
    private static final String TAG = CameraDeviceSetupTest.class.getSimpleName();
    private static final int CAMERA_STATE_TIMEOUT_MS = 3000;

    @Rule
    public final CheckFlagsRule mFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_DEVICE_SETUP)
    public void testCameraDeviceSetupSupport() throws Exception {
        for (String cameraId : getCameraIdsUnderTest()) {
            CameraCharacteristics chars = mCameraManager.getCameraCharacteristics(cameraId);
            Integer queryVersion = chars.get(
                    CameraCharacteristics.INFO_SESSION_CONFIGURATION_QUERY_VERSION);
            mCollector.expectNotNull(CameraCharacteristics.INFO_SESSION_CONFIGURATION_QUERY_VERSION
                    + " must not be null", queryVersion);
            if (queryVersion == null) {
                continue;
            }
            boolean cameraDeviceSetupSupported = mCameraManager.isCameraDeviceSetupSupported(
                    cameraId);

            // CameraDeviceSetup must be supported for all CameraDevices that report
            // INFO_SESSION_CONFIGURATION_QUERY_VERSION > U, and false for those that don't.
            mCollector.expectEquals(CameraCharacteristics.INFO_SESSION_CONFIGURATION_QUERY_VERSION
                            + " and CameraManager.isCameraDeviceSetupSupported give differing "
                            + "answers for camera id" + cameraId,
                    queryVersion > Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                    cameraDeviceSetupSupported);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_DEVICE_SETUP)
    public void testCameraDeviceSetupCreationSuccessful() throws Exception {
        for (String cameraId : getCameraIdsUnderTest()) {
            if (!mCameraManager.isCameraDeviceSetupSupported(cameraId)) {
                Log.i(TAG, "CameraDeviceSetup not supported for camera id " + cameraId);
                continue;
            }

            CameraDevice.CameraDeviceSetup cameraDeviceSetup =
                    mCameraManager.getCameraDeviceSetup(cameraId);
            mCollector.expectEquals("Camera ID of created object is not the same as that "
                            + "passed to CameraManager.",
                    cameraId, cameraDeviceSetup.getId());
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_DEVICE_SETUP)
    public void testCameraDeviceSetupCreationFailure() throws Exception {
        try {
            mCameraManager.getCameraDeviceSetup(/*cameraId=*/ null);
            Assert.fail("Calling getCameraDeviceSetup with null should have raised an "
                    + "IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // Expected. Don't do anything.
        }

        // Try to get an invalid camera ID by randomly generating 100 integers.
        // NOTE: We don't actually expect to generate more than an int or two.
        // Not being able to find an invalid camera within 100 random ints
        // is astronomical.
        HashSet<String> cameraIds = new HashSet<>(List.of(getCameraIdsUnderTest()));
        Random rng = new Random();
        int invalidId = rng.ints(/*streamSize=*/ 100, /*randomNumberOrigin=*/ 0,
                        /*randomNumberBound=*/ Integer.MAX_VALUE)
                .filter(i -> !cameraIds.contains(String.valueOf(i)))
                .findAny()
                .orElseThrow(() -> new AssertionError(
                        "Could not find an invalid cameraID within 100 randomly generated "
                                + "numbers."));
        String invalidCameraId = String.valueOf(invalidId);
        Log.i(TAG, "Using invalid Camera ID: " + invalidCameraId);
        try {
            mCameraManager.getCameraDeviceSetup(invalidCameraId);
            Assert.fail("Calling getCameraDeviceSetup with a cameraId not in getCameraIdList()"
                    + "should have raised an IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // Expected. Don't do anything.
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_DEVICE_SETUP)
    public void testOpenSuccessful() throws Exception {
        ExecutorService callbackExecutor = Executors.newCachedThreadPool();
        for (String cameraId : getCameraIdsUnderTest()) {
            if (!mCameraManager.isCameraDeviceSetupSupported(cameraId)) {
                Log.i(TAG, "CameraDeviceSetup not supported for camera id " + cameraId);
                continue;
            }

            // mock listener to capture the CameraDevice from callbacks
            MockStateCallback mockListener = MockStateCallback.mock();
            BlockingStateCallback callback = new BlockingStateCallback(mockListener);

            CameraDevice.CameraDeviceSetup cameraDeviceSetup =
                    mCameraManager.getCameraDeviceSetup(cameraId);
            cameraDeviceSetup.openCamera(callbackExecutor, callback);

            callback.waitForState(BlockingStateCallback.STATE_OPENED, CAMERA_STATE_TIMEOUT_MS);
            CameraDevice cameraDevice = CameraTestUtils.verifyCameraStateOpened(
                    cameraId, mockListener);

            mCollector.expectEquals("CameraDeviceSetup and created CameraDevice must have "
                            + "the same ID",
                    cameraDeviceSetup.getId(), cameraDevice.getId());

            cameraDevice.close();
            callback.waitForState(BlockingStateCallback.STATE_CLOSED, CAMERA_STATE_TIMEOUT_MS);
        }
    }

    /**
     * Verify if valid session characteristics can be fetched for a particular camera.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_DEVICE_SETUP)
    public void testSessionCharacteristics() throws Exception {
        String[] cameraIdsUnderTest = getCameraIdsUnderTest();
        for (String cameraId : cameraIdsUnderTest) {
            // Without the following check, mOrderedPreviewSizes will be null.
            StaticMetadata staticChars = new StaticMetadata(
                    mCameraManager.getCameraCharacteristics(cameraId));

            if (!staticChars.isColorOutputSupported()) {
                Log.i(TAG, "Camera " + cameraId + " does not support color outputs, skipping.");
                continue;
            }

            if (!mCameraManager.isCameraDeviceSetupSupported(cameraId)) {
                Log.i(TAG, "CameraDeviceSetup not supported for camera id " + cameraId);
                continue;
            }

            CameraDevice.CameraDeviceSetup cameraDeviceSetup = mCameraManager.getCameraDeviceSetup(
                    cameraId);

            int outputFormat = ImageFormat.YUV_420_888;
            List<Size> orderedPreviewSizes = CameraTestUtils.getSupportedPreviewSizes(cameraId,
                    mCameraManager, CameraTestUtils.PREVIEW_SIZE_BOUND);
            Size outputSize = orderedPreviewSizes.get(0);
            try (ImageReader imageReader = ImageReader.newInstance(outputSize.getWidth(),
                    outputSize.getHeight(), outputFormat, /*maxImages*/3)) {
                CameraCaptureSession.StateCallback sessionListener = new BlockingSessionCallback();

                List<OutputConfiguration> outputs = new ArrayList<>();
                outputs.add(new OutputConfiguration(imageReader.getSurface()));

                SessionConfiguration sessionConfig = new SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR, outputs,
                        new CameraTestUtils.HandlerExecutor(mHandler), sessionListener);

                CaptureRequest.Builder builder = cameraDeviceSetup.createCaptureRequest(
                        CameraDevice.TEMPLATE_STILL_CAPTURE);
                builder.addTarget(imageReader.getSurface());

                CaptureRequest request = builder.build();
                sessionConfig.setSessionParameters(request);

                CameraCharacteristics sessionCharacteristics =
                        cameraDeviceSetup.getSessionCharacteristics(sessionConfig);
                StaticMetadata sessionMetadata = new StaticMetadata(sessionCharacteristics);

                List<CameraCharacteristics.Key<?>> availableSessionCharKeys =
                        staticChars.getCharacteristics().getAvailableSessionCharacteristicsKeys();

                mCollector.expectNotNull("Session Characteristics keys must not be null",
                        availableSessionCharKeys);

                // Ensure every key in availableSessionCharKeys is present in
                // sessionCharacteristics.
                for (CameraCharacteristics.Key<?> key : availableSessionCharKeys) {
                    mCollector.expectNotNull(key.toString()
                                    + " is null in Session Characteristics",
                            sessionCharacteristics.get(key));
                }

                List<CameraCharacteristics.Key<?>> staticKeys =
                        staticChars.getCharacteristics().getKeys();
                List<CameraCharacteristics.Key<?>> sessionCharKeys =
                        sessionCharacteristics.getKeys();

                // Ensure that there are no duplicate keys in session chars
                HashSet<CameraCharacteristics.Key<?>> uniqueSessionCharKeys =
                        new HashSet<>(sessionCharKeys);
                mCollector.expectEquals(
                        "Session Characteristics should not contain duplicate keys.",
                        uniqueSessionCharKeys.size(), sessionCharKeys.size());

                // Ensure all keys in static chars are present in session chars.
                mCollector.expectEquals(
                        "Session Characteristics and Static Characteristics must have the same "
                                + "number of keys.",
                        staticKeys.size(), uniqueSessionCharKeys.size());
                mCollector.expectContainsAll(
                        "Session Characteristics must have all the keys in Static "
                                + "Characteristics",
                        uniqueSessionCharKeys, staticKeys);


                // TODO: Do more thorough testing to make sure the max_digital_zoom and
                //       zoom_ratio_range have valid values.
                sessionMetadata.getAvailableMaxDigitalZoomChecked();
                sessionMetadata.getZoomRatioRangeChecked();

                checkSessionCharacteristicsForNoCallbackConfig(outputs, builder,
                        cameraDeviceSetup, sessionCharacteristics);
            }
        }
    }

    /**
     * Check the session characteristics consistency when queried via a SessionConfiguration
     * without callbacks.
     */
    private void checkSessionCharacteristicsForNoCallbackConfig(List<OutputConfiguration> outputs,
            CaptureRequest.Builder requestBuilder,
            CameraDevice.CameraDeviceSetup deviceSetup,
            CameraCharacteristics sessionCharacteristics) throws Exception {
        // Session configuration with no callbacks
        SessionConfiguration sessionConfigNoCallback = new SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, outputs);
        sessionConfigNoCallback.setSessionParameters(requestBuilder.build());

        CameraCharacteristics sessionCharsWithoutCallbacks = deviceSetup.getSessionCharacteristics(
                sessionConfigNoCallback);
        List<CameraCharacteristics.Key<?>> sessionCharKeysWithoutCallback =
                sessionCharsWithoutCallbacks.getKeys();

        // Ensure that there are no duplicate keys in session chars without callbacks.
        HashSet<CameraCharacteristics.Key<?>> uniqueSessionCharNoCallbackKeys =
                new HashSet<>(sessionCharKeysWithoutCallback);
        mCollector.expectEquals(
                "Session Characteristics without callback should not contain duplicate keys.",
                uniqueSessionCharNoCallbackKeys.size(), sessionCharKeysWithoutCallback.size());

        // Ensure all keys in session chars with callback are present in session chars
        // without callback.
        List<CameraCharacteristics.Key<?>> sessionCharKeys = sessionCharacteristics.getKeys();
        mCollector.expectEquals(
                "Session Characteristics with and without callback must have the same "
                        + "number of keys.",
                sessionCharKeys.size(), uniqueSessionCharNoCallbackKeys.size());
        mCollector.expectContainsAll(
                "Session Characteristics without callback must have the all the keys in Session "
                        + "Characteristics with callback.",
                uniqueSessionCharNoCallbackKeys, sessionCharKeys);

        // setStateCallback works as expected
        CameraCaptureSession.StateCallback sessionListener = new BlockingSessionCallback();
        Executor executor = new CameraTestUtils.HandlerExecutor(mHandler);
        sessionConfigNoCallback.setStateCallback(executor, sessionListener);
        mCollector.expectEquals(
                "CameraCaptureSession.StateCallback set by setStateCallback not reflected in "
                        + "getStateCallback.",
                sessionListener, sessionConfigNoCallback.getStateCallback());
        mCollector.expectEquals("Executor set by setStateCallback not reflect in getExecutor.",
                executor, sessionConfigNoCallback.getExecutor());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_DEVICE_SETUP)
    public void testOutputConfigurationForCameraSetup() throws Exception {
        Size size = new Size(640, 480);
        int fmt = ImageFormat.YUV_420_888;
        int jpegFmt = ImageFormat.JPEG;
        int maxImages = 2;
        int surfaceGroupId = 1;
        ImageReader reader = ImageReader.newInstance(
                size.getWidth(), size.getHeight(), fmt, maxImages);

        // Test new constructors for OutputConfiguration and add Surface later.
        OutputConfiguration config = new OutputConfiguration(fmt, size);
        config.addSurface(reader.getSurface());

        // Adding a mismatched format surface throws an IllegalArgumentException.
        OutputConfiguration configWithGroupId = new OutputConfiguration(
                surfaceGroupId, jpegFmt, size);
        try {
            configWithGroupId.addSurface(reader.getSurface());
            fail("Adding surface with mismatching format must throw an exception!");
        } catch (IllegalArgumentException e) {
        }

        // Adding a format with different dataspace throws an IllegalArgumentException.
        final long usageFlag = HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
                | HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE;
        OutputConfiguration configWithUsageFlag = new OutputConfiguration(
                ImageFormat.JPEG_R, size, usageFlag);
        try {
            configWithUsageFlag.addSurface(reader.getSurface());
            fail("Adding surface with mismatching dataspace must throw an exception!");
        } catch (IllegalArgumentException e) {
        }

        // Create OutputConfiguration with groupdId, format, size, and usage flag
        OutputConfiguration configWithGroupIdAndUsage = new OutputConfiguration(
                surfaceGroupId, fmt, size, usageFlag);
        assertNull(configWithGroupIdAndUsage.getSurface());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_DEVICE_SETUP)
    public void testCameraDeviceSetupTemplates() throws Exception {
        for (String cameraId : getCameraIdsUnderTest()) {
            if (!mCameraManager.isCameraDeviceSetupSupported(cameraId)) {
                Log.i(TAG, "CameraDeviceSetup not supported for camera id " + cameraId);
                continue;
            }

            testCameraDeviceSetupTemplatesByCamera(cameraId);
        }
    }

    private void testCameraDeviceSetupTemplatesByCamera(String cameraId) throws Exception {
        int[] templates = {
                CameraDevice.TEMPLATE_PREVIEW,
                CameraDevice.TEMPLATE_STILL_CAPTURE,
                CameraDevice.TEMPLATE_RECORD,
                CameraDevice.TEMPLATE_VIDEO_SNAPSHOT,
                CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG,
                CameraDevice.TEMPLATE_MANUAL,
        };

        try {
            CameraDevice.CameraDeviceSetup cameraDeviceSetup =
                    mCameraManager.getCameraDeviceSetup(cameraId);
            assertNotNull("Failed to create camera device setup for id " + cameraId,
                    cameraDeviceSetup);

            openDevice(cameraId);
            mCollector.setCameraId(cameraId);

            for (int template : templates) {
                try {
                    CaptureRequest.Builder requestFromSetup =
                            cameraDeviceSetup.createCaptureRequest(template);
                    assertNotNull("CameraDeviceSetup failed to create capture request for camera "
                            + cameraId + " template " + template, requestFromSetup);

                    CaptureRequest.Builder request = mCamera.createCaptureRequest(template);
                    assertNotNull("CameraDevice failed to create capture request for template "
                            + template, request);

                    mCollector.expectEquals("The CaptureRequest created by CameraDeviceSetup "
                            + "and CameraDevice must have the same set of keys",
                            request.build().getKeys(), requestFromSetup.build().getKeys());
                } catch (IllegalArgumentException e) {
                    if (template == CameraDevice.TEMPLATE_MANUAL
                            && !mStaticInfo.isCapabilitySupported(CameraCharacteristics
                                    .REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                        // OK
                    } else if (template == CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG
                            && !mStaticInfo.isCapabilitySupported(CameraCharacteristics
                                    .REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING)) {
                        // OK.
                    } else if (sLegacySkipTemplates.contains(template)
                            && mStaticInfo.isHardwareLevelLegacy()) {
                        // OK
                    } else if (template != CameraDevice.TEMPLATE_PREVIEW
                            && mStaticInfo.isDepthOutputSupported()
                            && !mStaticInfo.isColorOutputSupported()) {
                        // OK, depth-only devices need only support PREVIEW template
                    } else {
                        throw e; // rethrow
                    }
                }
            }
        } finally {
            closeDevice(cameraId);
        }
    }

    /**
     * Verify if valid session characteristics can be fetched for a particular camera.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_DEVICE_SETUP)
    public void testFeatureCombinationQueryConsistency() throws Exception {
        for (String cameraId : getCameraIdsUnderTest()) {
            if (!mCameraManager.isCameraDeviceSetupSupported(cameraId)) {
                Log.i(TAG, "CameraDeviceSetup not supported for camera id " + cameraId);
                continue;
            }

            testFeatureCombinationQueryConsistencyByCamera(cameraId);
        }
    }

    /**
     * Check the feature combination query consistency between different feature combinations.
     */
    private void testFeatureCombinationQueryConsistencyByCamera(String cameraId) throws Exception {
        CameraDevice.CameraDeviceSetup cameraDeviceSetup =
                mCameraManager.getCameraDeviceSetup(cameraId);
        assertNotNull("Failed to create camera device setup for id " + cameraId,
                cameraDeviceSetup);

        StaticMetadata staticInfo = mAllStaticInfo.get(cameraId);
        if (!staticInfo.isColorOutputSupported()) {
            Log.i(TAG, "Camera " + cameraId + " does not support color outputs, skipping");
            return;
        }

        MaxStreamSizes maxStreamSizes = new MaxStreamSizes(staticInfo,
                cameraDeviceSetup.getId(), mContext, /*matchSize*/true);
        Set<Long> dynamicRangeProfiles = staticInfo.getAvailableDynamicRangeProfilesChecked();
        int[] videoStabilizationModes = staticInfo.getAvailableVideoStabilizationModesChecked();
        Range<Integer>[] fpsRanges = staticInfo.getAeAvailableTargetFpsRangesChecked();
        int featureCombinationQueryVersion = staticInfo.getCharacteristics().get(
                CameraCharacteristics.INFO_SESSION_CONFIGURATION_QUERY_VERSION);
        final int kPreviewStabilization =
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION;
        boolean supportPreviewStab = CameraTestUtils.contains(videoStabilizationModes,
                kPreviewStabilization);
        Map<CameraFeatureWrapper, Boolean> featureCombinationSupport = new HashMap<>();
        for (int[] c : maxStreamSizes.getQueryableCombinations()) {
            // Skip combinations not supported by the camera device
            if (featureCombinationQueryVersion < c[0]) continue;
            int[] comb = Arrays.copyOfRange(c, 1, c.length);
            for (Long dynamicProfile : dynamicRangeProfiles) {
                if (dynamicProfile != DynamicRangeProfiles.STANDARD
                        && dynamicProfile != DynamicRangeProfiles.HLG10) {
                    // Only verify HLG10 and STANDARD. Skip for others.
                    continue;
                }
                // Setup outputs
                List<OutputConfiguration> outputConfigs = new ArrayList<>();
                long minFrameDuration = setupConfigurations(staticInfo, comb, maxStreamSizes,
                        outputConfigs, dynamicProfile);
                assertTrue(
                        MaxStreamSizes.combinationToString(comb) + " should be supported!",
                        minFrameDuration > 0);

                for (Range<Integer> fpsRange : fpsRanges) {
                    if ((fpsRange.getUpper() != 60) && (fpsRange.getUpper() != 30)) {
                        // Skip fps ranges that are not 30fps or 60fps.
                        continue;
                    }
                    if (minFrameDuration > 1e9 * 1.01 / fpsRange.getUpper()) {
                        // Skip the fps range because the minFrameDuration cannot meet the
                        // required range
                        continue;
                    }

                    String combinationStr = MaxStreamSizes.combinationToString(comb)
                            + ", dynamicRangeProfile " + dynamicProfile
                            + ", fpsRange " + fpsRange.toString();
                    try {
                        CaptureRequest.Builder builder = cameraDeviceSetup.createCaptureRequest(
                                CameraDevice.TEMPLATE_PREVIEW);
                        builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
                        CaptureRequest request = builder.build();
                        SessionConfiguration sessionConfig = new SessionConfiguration(
                                SessionConfiguration.SESSION_REGULAR, outputConfigs);
                        sessionConfig.setSessionParameters(request);
                        boolean isSupported = cameraDeviceSetup.isSessionConfigurationSupported(
                                sessionConfig);

                        // Make sure isSessionConfigurationSupported and getSessionCharacteristics
                        // behaviors are consistent.
                        try {
                            cameraDeviceSetup.getSessionCharacteristics(sessionConfig);
                            mCollector.expectTrue("getSessionCharacteristics succeeds, but "
                                    + "isSessionConfigurationSupported return false!",
                                    isSupported);
                        } catch (IllegalArgumentException e) {
                            mCollector.expectTrue(
                                    "getSessionCharacteristics throws IllegalArgumentException,"
                                    + "but isSessionConfigurationSupported returns true!",
                                    !isSupported);
                        }

                        // If preview stabilization is supported, the return value of
                        // isSessionConfigurationSupported when stabilization is off should match
                        // the result value when preview stabilization is on.
                        if (supportPreviewStab) {
                            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                    kPreviewStabilization);
                            CaptureRequest requestWithStab = builder.build();
                            SessionConfiguration sessionConfigWithStab = new SessionConfiguration(
                                    SessionConfiguration.SESSION_REGULAR, outputConfigs);
                            sessionConfigWithStab.setSessionParameters(requestWithStab);

                            boolean isSupportedWithStab =
                                    cameraDeviceSetup.isSessionConfigurationSupported(
                                            sessionConfigWithStab);

                            mCollector.expectEquals(
                                    "isSessionCharacteristicsSupported returns " + isSupported
                                    + " with stabilization off, but returns " + isSupportedWithStab
                                    + " with preview stabilization on.", isSupported,
                                    isSupportedWithStab);

                            try {
                                cameraDeviceSetup.getSessionCharacteristics(sessionConfigWithStab);
                                mCollector.expectTrue(
                                        "With stabilization on, getSessionCharacteristics "
                                        + "succeeds, but isSessionConfigurationSupported return "
                                        + "false!", isSupportedWithStab);
                            } catch (IllegalArgumentException e) {
                                mCollector.expectTrue("With stabilization on, "
                                        + "getSessionCharacteristics throws "
                                        + "IllegalArgumentException, but "
                                        + "isSessionConfigurationSupported returns true!",
                                        !isSupportedWithStab);
                            }
                        }

                        featureCombinationSupport.put(
                                new CameraFeatureWrapper(comb, fpsRange, dynamicProfile),
                                isSupported);
                    } catch (Throwable e) {
                        mCollector.addMessage(String.format(
                                "Output combination %s failed due to: %s",
                                combinationStr, e.getMessage()));
                    }
                }
            }
        }

        // Verify that:
        // - If a combination with JPEG_R is supported, replacing JPEG_R with JPEG is
        //   still supported.
        // - If a combination with 10-bit HDR is supported, replacing HDR with SDR is
        //   still supported.
        for (Map.Entry<CameraFeatureWrapper, Boolean> entry
                : featureCombinationSupport.entrySet()) {
            CameraFeatureWrapper features = entry.getKey();
            boolean isSupported = entry.getValue();

            Log.v(TAG, "Features: "
                    + Arrays.toString(features.mStreamCombination) + ", isJpegR "
                    + features.mIsJpegR + ", fpsRange: "
                    + features.mFpsRange + ", dynamicProfile: "
                    + features.mDynamicProfile);
            if (features.mIsJpegR) {
                CameraFeatureWrapper featuresNoJpegR = new CameraFeatureWrapper(features,
                        /*isJpegR*/false);
                assertTrue(featureCombinationSupport.containsKey(featuresNoJpegR));
                boolean isSupportedNoJpegR = featureCombinationSupport.get(featuresNoJpegR);
                mCollector.expectEquals(
                        features.toString() + " (support: " + isSupported
                        + ") doesn't match " + featuresNoJpegR.toString() + " (support: "
                        + isSupportedNoJpegR, isSupported, isSupportedNoJpegR);
            }

            if (features.mDynamicProfile != DynamicRangeProfiles.STANDARD) {
                CameraFeatureWrapper featuresNoHdr = new CameraFeatureWrapper(
                        features, DynamicRangeProfiles.STANDARD);
                assertTrue(featureCombinationSupport.containsKey(featuresNoHdr));
                boolean isSupportedNoHdr = featureCombinationSupport.get(featuresNoHdr);
                mCollector.expectEquals(
                        features.toString() + " (support: " + isSupported
                        + ") doesn't match " + featuresNoHdr.toString() + " (support: "
                        + isSupportedNoHdr, isSupported, isSupportedNoHdr);
            }
        }
    }

    /**
     * A helper class to wrap camera features.
     */
    static class CameraFeatureWrapper {
        CameraFeatureWrapper(int[] streamCombination,
                Range<Integer> fpsRange, long dynamicProfile) {
            mFpsRange = fpsRange;
            mDynamicProfile = dynamicProfile;
            mStreamCombination = Arrays.copyOf(streamCombination, streamCombination.length);

            // Replace JPEG_R format with JPEG, and use a boolean to indicate whether
            // it's JPEG_R or JPEG. This is for easier feature look-up.
            boolean hasJpegR = false;
            for (int i = 0; i < mStreamCombination.length; i += 2) {
                if (mStreamCombination[i] == JPEG_R) {
                    mStreamCombination[i] = JPEG;
                    hasJpegR = true;
                    break;
                }
            }
            mIsJpegR = hasJpegR;
        }

        CameraFeatureWrapper(CameraFeatureWrapper other, boolean isJpegR) {
            this.mStreamCombination = other.mStreamCombination;
            this.mIsJpegR = isJpegR;
            this.mFpsRange = other.mFpsRange;
            this.mDynamicProfile = other.mDynamicProfile;
        }

        CameraFeatureWrapper(CameraFeatureWrapper other, long dynamicRangeProfile) {
            this.mStreamCombination = other.mStreamCombination;
            this.mIsJpegR = other.mIsJpegR;
            this.mFpsRange = other.mFpsRange;
            this.mDynamicProfile = dynamicRangeProfile;
        }

        @Override
        public int hashCode() {
            int result = 17;
            for (int i : mStreamCombination) {
                result = 31 * result + i;
            }
            result = 31 * result + (mIsJpegR ? 1 : 0);
            result = 31 * result + mFpsRange.hashCode();
            result = 31 * result + (int) mDynamicProfile;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            } else if (obj == this) {
                return true;
            } else if (obj instanceof CameraFeatureWrapper) {
                final CameraFeatureWrapper other = (CameraFeatureWrapper) obj;
                return (Arrays.equals(mStreamCombination, other.mStreamCombination)
                        && mIsJpegR == other.mIsJpegR
                        && mFpsRange.equals(other.mFpsRange)
                        && mDynamicProfile == other.mDynamicProfile);
            }

            return false;
        }

        @Override
        public String toString() {
            return "Stream combination: " + Arrays.toString(mStreamCombination)
                    + ", isJpegR: " + mIsJpegR
                    + ", fpsRange: " + mFpsRange
                    + ", dynamicRangeProfile: " + mDynamicProfile;
        }

        public final int[] mStreamCombination;
        public final boolean mIsJpegR;
        public final Range<Integer> mFpsRange;
        public final long mDynamicProfile;
    }

    private long setupConfigurations(StaticMetadata staticInfo, int[] configs,
            MaxStreamSizes maxSizes, List<OutputConfiguration> outputConfigs, Long dynamicProfile) {
        long frameDuration = -1;
        for (int i = 0; i < configs.length; i += 2) {
            int format = configs[i];
            int sizeLimit = configs[i + 1];

            Size targetSize = null;
            switch (format) {
                case PRIV: {
                    targetSize = maxSizes.getOutputSizeForFormat(PRIV, sizeLimit);
                    OutputConfiguration config = new OutputConfiguration(
                            targetSize, SurfaceTexture.class);
                    config.setDynamicRangeProfile(dynamicProfile);
                    outputConfigs.add(config);
                    break;
                }
                case JPEG:
                case JPEG_R: {
                    targetSize = maxSizes.getOutputSizeForFormat(format, sizeLimit);
                    OutputConfiguration config = new OutputConfiguration(format, targetSize);
                    outputConfigs.add(config);
                    break;
                }
                case YUV: {
                    if (dynamicProfile == DynamicRangeProfiles.HLG10) {
                        format = ImageFormat.YCBCR_P010;
                    }
                    targetSize = maxSizes.getOutputSizeForFormat(YUV, sizeLimit);
                    OutputConfiguration config = new OutputConfiguration(format, targetSize);
                    config.setDynamicRangeProfile(dynamicProfile);
                    outputConfigs.add(config);
                    break;
                }
                default:
                    fail("Unknown output format " + format);
            }

            Map<Size, Long> minFrameDurations =
                    staticInfo.getAvailableMinFrameDurationsForFormatChecked(format);
            if (minFrameDurations.containsKey(targetSize)
                    && minFrameDurations.get(targetSize) > frameDuration) {
                frameDuration = minFrameDurations.get(targetSize);
            }
        }

        return frameDuration;
    }
}
