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

package android.ondeviceintelligence.cts;

import static android.app.ondeviceintelligence.flags.Flags.FLAG_ENABLE_ON_DEVICE_INTELLIGENCE;
import static android.app.ondeviceintelligence.flags.Flags.FLAG_ENABLE_ON_DEVICE_INTELLIGENCE_MODULE;
import static android.content.Context.RECEIVER_EXPORTED;
import static android.ondeviceintelligence.cts.CtsIsolatedInferenceService.constructException;
import static android.ondeviceintelligence.cts.CtsIsolatedInferenceService.constructTokenInfo;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.Manifest;
import android.app.ondeviceintelligence.DownloadCallback;
import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.InferenceInfo;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.app.ondeviceintelligence.OnDeviceIntelligenceManager;
import android.app.ondeviceintelligence.ProcessingCallback;
import android.app.ondeviceintelligence.ProcessingSignal;
import android.app.ondeviceintelligence.StreamingProcessingCallback;
import android.app.ondeviceintelligence.TokenInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.service.ondeviceintelligence.OnDeviceIntelligenceService;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Test the OnDeviceIntelligenceManager API. Run with "atest OnDeviceIntelligenceManagerTest"
 * .
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "PM will not recognize OnDeviceIntelligenceManagerService in instantMode.")
public class OnDeviceIntelligenceManagerTest {
    public static final String TEST_FILE_NAME = "test_file.txt";
    public static final String TEST_KEY = "test_key";
    public static final String TEST_CONTENT = "test_content";
    public static final String TEST_AUGMENT_KEY = "test_augment_key";
    public static final String TEST_AUGMENT_CONTENT = "test_augment_content";
    public static final String EXCEPTION_MESSAGE_KEY = "message_key";
    public static final String EXCEPTION_STATUS_CODE_KEY = "code_key";
    public static final String EXCEPTION_PARAMS_KEY = "params_key";
    public static final String TOKEN_INFO_COUNT_KEY = "tokenInfo_count_key";
    public static final String TOKEN_INFO_PARAMS_KEY = "tokenInfo_params_key";
    public static final String TEST_OD_NAMESPACE = "test_od_namespace";


    private static final String TAG = OnDeviceIntelligenceManagerTest.class.getSimpleName();
    public static final String CTS_PACKAGE_NAME =
            android.ondeviceintelligence.cts.CtsIntelligenceService.class.getPackageName();
    public static final String CTS_INTELLIGENCE_SERVICE_NAME =
            CTS_PACKAGE_NAME + "/"
                    + android.ondeviceintelligence.cts.CtsIntelligenceService.class.getCanonicalName();
    public static final String CTS_INFERENCE_SERVICE_NAME =
            CTS_PACKAGE_NAME + "/"
                    + android.ondeviceintelligence.cts.CtsIsolatedInferenceService.class.getCanonicalName();
    private static final int TEMPORARY_SERVICE_DURATION = 20000;
    public static final String NAMESPACE_ON_DEVICE_INTELLIGENCE = "ondeviceintelligence";
    public static final String KEY_SERVICE_ENABLED = "service_enabled";

    public static final int REQUEST_TYPE_GET_PACKAGE_NAME = 1000;

    public static final int REQUEST_TYPE_GET_FILE_FROM_MAP = 1001;
    public static final int REQUEST_TYPE_GET_FILE_FROM_STREAM = 1002;
    public static final int REQUEST_TYPE_GET_FILE_FROM_PFD = 1003;
    public static final int REQUEST_TYPE_GET_AUGMENTED_DATA = 1004;
    public static final int REQUEST_TYPE_GET_CALLER_UID = 1005;
    public static final int REQUEST_TYPE_GET_UPDATED_DEVICE_CONFIG = 1006;
    public static final int REQUEST_TYPE_GET_FILE_FROM_NON_FILES_DIRECTORY = 1007;
    public static final int REQUEST_TYPE_POPULATE_INFERENCE_INFO = 1008;

    private static final Executor EXECUTOR = InstrumentationRegistry.getContext().getMainExecutor();
    private static final String MODEL_LOADED_BROADCAST_ACTION =
            "android.service.ondeviceintelligence.MODEL_LOADED";

    private Context mContext;
    public OnDeviceIntelligenceManager mOnDeviceIntelligenceManager;
    private final Executor mExecutor = Executors.newCachedThreadPool();

    @Rule
    public final DeviceConfigStateChangerRule mDeviceConfigStateChangerRule =
            new DeviceConfigStateChangerRule(
                    getInstrumentation().getTargetContext(),
                    NAMESPACE_ON_DEVICE_INTELLIGENCE,
                    KEY_SERVICE_ENABLED,
                    "true");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        mOnDeviceIntelligenceManager = mContext.getSystemService(OnDeviceIntelligenceManager.class);
        bindToTestableOnDeviceIntelligenceServices();
        setTestableDeviceConfigNamespace(TEST_OD_NAMESPACE);
    }

    @After
    public void tearDown() throws Exception {
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    @SkipSetupAndTeardown
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void cannotBindToIsolatedComputeAppEvenFromSamePackage() {
        assertThrows(
                "Cannot bind to isolated_compute_app process from same package",
                SecurityException.class,
                () -> getInstrumentation().getContext().bindService(
                        new Intent().setComponent(new ComponentName(CTS_PACKAGE_NAME,
                                CtsIsolatedInferenceService.class.getCanonicalName())),
                        new ServiceConnection() {
                            @Override
                            public void onServiceConnected(ComponentName name,
                                    IBinder service) {
                                Log.i(TAG, "Service connected");
                            }

                            @Override
                            public void onServiceDisconnected(ComponentName name) {
                                Log.i(TAG, "Service disconnected");
                            }
                        },
                        Context.BIND_AUTO_CREATE));
    }

//=====================Tests for Access Denied without Permission on all Manager Methods=========

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenAttemptingGetFeature() {
        assertEquals(PackageManager.PERMISSION_DENIED, mContext.checkCallingOrSelfPermission(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));

        // Test non system app throws SecurityException
        assertThrows("no access to getFeature from non system component",
                SecurityException.class,
                () -> mOnDeviceIntelligenceManager.getFeature(1, EXECUTOR,
                        result -> {
                            Log.i(TAG, "Feature : =" + result);
                        }));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenAttemptingGetFeatureDetails() {
        assertEquals(PackageManager.PERMISSION_DENIED, mContext.checkCallingOrSelfPermission(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));
        Feature feature = new Feature.Builder(1).build();

        // Test non system app throws SecurityException
        assertThrows("no access to getFeature from non system component",
                SecurityException.class,
                () -> mOnDeviceIntelligenceManager.getFeatureDetails(feature,
                        EXECUTOR,
                        result -> Log.i(TAG, "Feature details : =" + result)));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenAttemptingGetVersion() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));

        // Test non system app throws SecurityException
        assertThrows(
                "no access to getVersion from non system component",
                SecurityException.class,
                () ->
                        mOnDeviceIntelligenceManager.getVersion(EXECUTOR,
                                result -> {
                                    Log.i(TAG, "Version : =" + result);
                                }));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenAttemptingRequestFeatureDownload() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));

        Feature feature = new Feature.Builder(1).build();

        // Test non system app throws SecurityException
        assertThrows(
                "no access to requestFeatureDownload from non system component",
                SecurityException.class,
                () ->
                        mOnDeviceIntelligenceManager.requestFeatureDownload(feature, null, EXECUTOR,
                                new DownloadCallback() {
                                    @Override
                                    public void onDownloadFailed(int failureStatus,
                                            @Nullable String errorMessage,
                                            @NonNull PersistableBundle errorParams) {
                                        Log.e(TAG, "Got Error", new RuntimeException(errorMessage));
                                    }

                                    @Override
                                    public void onDownloadCompleted(
                                            @NonNull PersistableBundle downloadParams) {
                                        Log.i(TAG, "Response : =" + downloadParams.toString());
                                    }
                                }));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenRequestTokenInfo() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));


        Feature feature = new Feature.Builder(1).build();
        // Test non system app throws SecurityException
        assertThrows(
                "no access to requestTokenInfo from non system component",
                SecurityException.class,
                () ->
                        mOnDeviceIntelligenceManager.requestTokenInfo(feature,
                                new Bundle(), null,
                                EXECUTOR,
                                result -> {
                                    Log.i(TAG, "Response : =" + result.getCount());
                                }));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenAttemptingProcessRequest() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));

        Feature feature = new Feature.Builder(1).build();
        // Test non system app throws SecurityException
        assertThrows(
                "no access to processRequest from non system component",
                SecurityException.class,
                () -> mOnDeviceIntelligenceManager.processRequest(feature,
                        new Bundle(), 1, null,
                        null, EXECUTOR, new ProcessingCallback() {
                            @Override
                            public void onResult(@NonNull Bundle result) {
                                Log.i(TAG, "Final Result : " + result);
                            }

                            @Override
                            public void onError(@NonNull OnDeviceIntelligenceException error) {
                                Log.e(TAG, "Error Occurred", error);
                            }
                        }));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void noAccessWhenAttemptingProcessRequestStreaming() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE));

        Feature feature = new Feature.Builder(1).build();
        // Test non system app throws SecurityException
        assertThrows(
                "no access to processRequestStreaming from non system component",
                SecurityException.class,
                () -> mOnDeviceIntelligenceManager.processRequestStreaming(feature,
                        new Bundle(), 1,
                        null, null, EXECUTOR,
                        new StreamingProcessingCallback() {
                            @Override
                            public void onPartialResult(@NonNull Bundle partialResult) {
                                Log.i(TAG, "New Content : " + partialResult);
                            }

                            @Override
                            public void onResult(Bundle result) {
                                Log.i(TAG, "Final Result : " + result);
                            }

                            @Override
                            public void onError(@NonNull OnDeviceIntelligenceException error) {
                                Log.e(TAG, "Final Result : ", error);
                            }
                        }));
    }

    @Test
    public void noAccessWhenAttemptingGetLatestInferenceInfo() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.DUMP));

        Feature feature = new Feature.Builder(1).build();
        // Test non system app throws SecurityException
        assertThrows(
                "no access to getLatestInferenceInfo when missing permission.",
                SecurityException.class,
                () -> mOnDeviceIntelligenceManager.getLatestInferenceInfo(0));
    }

//===================== Tests for Result callback invoked on all Manager Methods ==================

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenAttemptingGetFeature() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        Feature expectedFeature = CtsIntelligenceService.getSampleFeature(1);
        mOnDeviceIntelligenceManager.getFeature(1,
                EXECUTOR,
                result -> {
                    Log.i(TAG, "Feature : =" + result);
                    assertEquals(result.getFeatureParams().size(),
                            expectedFeature.getFeatureParams().size());
                    assertEquals(result.getId(), expectedFeature.getId());
                    assertEquals(result.getName(), expectedFeature.getName());
                    assertEquals(result.getModelName(), expectedFeature.getModelName());
                    assertEquals(result.getType(), expectedFeature.getType());
                    assertEquals(result.getVariant(), expectedFeature.getVariant());
                    statusLatch.countDown();
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenAttemptingGetFeatureDetails() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);

        // Test coverage for response with params
        mOnDeviceIntelligenceManager.getFeatureDetails(CtsIntelligenceService.getSampleFeature(0),
                EXECUTOR,
                result -> {
                    Log.i(TAG, "Feature details : =" + result);
                    assertEquals(result.getFeatureStatus(), 0);
                    assertEquals(result.getFeatureDetailParams().getInt(TEST_KEY), 1);
                    statusLatch.countDown();
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();

        // Test coverage for response withOut params
        mOnDeviceIntelligenceManager.getFeatureDetails(CtsIntelligenceService.getSampleFeature(1),
                EXECUTOR,
                result -> {
                    Log.i(TAG, "Feature details : =" + result);
                    assertEquals(result.getFeatureStatus(), 1);
                    assertEquals(result.getFeatureDetailParams().size(), 0);
                    statusLatch.countDown();
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenAttemptingGetVersion() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);

        mOnDeviceIntelligenceManager.getVersion(EXECUTOR,
                result -> {
                    Log.i(TAG, "Version : =" + result);
                    statusLatch.countDown();
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenAttemptingRequestFeatureDownload() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        Feature feature = new Feature.Builder(1).build();
        CountDownLatch statusLatch = new CountDownLatch(3);

        mOnDeviceIntelligenceManager.requestFeatureDownload(feature, null, EXECUTOR,
                new DownloadCallback() {
                    @Override
                    public void onDownloadFailed(int failureStatus,
                            @Nullable String errorMessage,
                            @NonNull PersistableBundle errorParams) {
                        Log.e(TAG, "Got Error", new RuntimeException(errorMessage));
                    }

                    @Override
                    public void onDownloadProgress(long bytesDownloaded) {
                        statusLatch.countDown();
                    }

                    @Override
                    public void onDownloadStarted(long bytesDownloaded) {
                        statusLatch.countDown();
                    }

                    @Override
                    public void onDownloadCompleted(
                            @NonNull PersistableBundle downloadParams) {
                        Log.i(TAG, "Response : =" + downloadParams);
                        statusLatch.countDown();
                    }
                });
        assertThat(statusLatch.await(2, SECONDS)).isTrue();

        // test download failed
        Feature feature2 = new Feature.Builder(2).build();
        CountDownLatch statusLatch2 = new CountDownLatch(1);

        mOnDeviceIntelligenceManager.requestFeatureDownload(feature2, null, EXECUTOR,
                new DownloadCallback() {
                    @Override
                    public void onDownloadFailed(int failureStatus,
                            @Nullable String errorMessage,
                            @NonNull PersistableBundle errorParams) {
                        Log.e(TAG, "Got Error", new RuntimeException(errorMessage));
                        statusLatch2.countDown();
                    }

                    @Override
                    public void onDownloadCompleted(
                            @NonNull PersistableBundle downloadParams) {
                        Log.i(TAG, "Response : =" + downloadParams);
                    }
                });
        assertThat(statusLatch2.await(2, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenRequestTokenInfo() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);

        Feature feature = new Feature.Builder(1).build();
        Bundle request = new Bundle();
        request.putInt(TOKEN_INFO_COUNT_KEY, 0);
        TokenInfo expectedTokenInfo = constructTokenInfo(0, null);
        mOnDeviceIntelligenceManager.requestTokenInfo(feature, request
                , null,
                EXECUTOR,
                result -> {
                    Log.i(TAG, "Response : =" + result.getCount());
                    assertEquals(expectedTokenInfo.getCount(), result.getCount());
                    statusLatch.countDown();
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();


        PersistableBundle params = new PersistableBundle();
        params.putInt("abc", 1);
        request.putParcelable(TOKEN_INFO_PARAMS_KEY, params);
        TokenInfo expectedTokenInfo2 = constructTokenInfo(0, params);
        mOnDeviceIntelligenceManager.requestTokenInfo(feature, request
                , null,
                EXECUTOR,
                result -> {
                    Log.i(TAG, "Response : =" + result.getCount());
                    assertEquals(expectedTokenInfo2.getCount(), result.getCount());
                    assertEquals(expectedTokenInfo2.getInfoParams().containsKey("abc"),
                            result.getInfoParams().containsKey("abc"));
                    statusLatch.countDown();
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenAttemptingProcessRequest() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        Feature feature = new Feature.Builder(1).build();
        mOnDeviceIntelligenceManager.processRequest(feature,
                new Bundle(), 1, null,
                null, EXECUTOR, new ProcessingCallback() {
                    @Override
                    public void onResult(@NonNull Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Error Occurred", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void resultPopulatedWhenAttemptingProcessRequestStreaming() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);

        Feature feature = new Feature.Builder(1).build();
        mOnDeviceIntelligenceManager.processRequestStreaming(feature,
                new Bundle(), 1,
                null, null, EXECUTOR,
                new StreamingProcessingCallback() {
                    @Override
                    public void onPartialResult(@NonNull Bundle partialResult) {
                        Log.i(TAG, "New Content : " + partialResult);
                    }

                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }


//===================== Tests Exception populated ==================

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void exceptionPopulatedWhenAttemptingProcessRequest() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        Feature feature = new Feature.Builder(1).build();
        Bundle bundle = new Bundle();
        bundle.putInt(EXCEPTION_STATUS_CODE_KEY, 1);
        OnDeviceIntelligenceException expectedException = constructException(bundle);
        mOnDeviceIntelligenceManager.processRequest(feature, bundle, 1, null,
                null, EXECUTOR, new ProcessingCallback() {
                    @Override
                    public void onResult(@NonNull Bundle result) {
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Error Occurred", error);
                        assertEquals(error.getErrorCode(), expectedException.getErrorCode());
                        statusLatch.countDown();
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();


        bundle.putString(EXCEPTION_MESSAGE_KEY, "test message");
        OnDeviceIntelligenceException expectedException2 = constructException(bundle);
        mOnDeviceIntelligenceManager.processRequest(feature, bundle, 1, null,
                null, EXECUTOR, new ProcessingCallback() {
                    @Override
                    public void onResult(@NonNull Bundle result) {
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Error Occurred", error);
                        assertEquals(error.getErrorCode(), expectedException2.getErrorCode());
                        assertEquals(error.getMessage(), expectedException2.getMessage());
                        statusLatch.countDown();
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();


        PersistableBundle params = new PersistableBundle();
        params.putInt("abc", 1);
        bundle.putParcelable(EXCEPTION_PARAMS_KEY, params);
        OnDeviceIntelligenceException expectedException3 = constructException(bundle);
        mOnDeviceIntelligenceManager.processRequest(feature, bundle, 1, null,
                null, EXECUTOR, new ProcessingCallback() {
                    @Override
                    public void onResult(@NonNull Bundle result) {
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Error Occurred", error);
                        assertEquals(error.getErrorCode(), expectedException3.getErrorCode());
                        assertEquals(error.getMessage(), expectedException3.getMessage());
                        assertEquals(error.getErrorParams().containsKey("abc"),
                                expectedException3.getErrorParams().containsKey("abc"));
                        statusLatch.countDown();
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

//===================== Tests for Processing and Cancellation signals  ==========================

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void cancellationPropagatedWhenInvokedDuringRequest() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(2);
        CancellationSignal cancellationSignal = new CancellationSignal();
        Feature feature = new Feature.Builder(1).build();
        CompletableFuture<Bundle> resultBundle = new CompletableFuture<>();
        mOnDeviceIntelligenceManager.processRequestStreaming(feature,
                new Bundle(), 1, cancellationSignal,
                null, EXECUTOR, new StreamingProcessingCallback() {
                    @Override
                    public void onPartialResult(@NonNull Bundle partialResult) {
                        Log.i(TAG, "New Content : " + partialResult);
                        cancellationSignal.cancel(); //cancel
                        statusLatch.countDown();
                    }

                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        resultBundle.complete(result);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        assertThat(statusLatch.await(2, SECONDS)).isTrue();
        assertThat(resultBundle.get()).isNotNull();
        assertThat(resultBundle.get().containsKey("test_key")).isTrue();
        assertThat(resultBundle.get().getBoolean(TEST_KEY)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void cancellationPropagatedWhenInvokedBeforeMakingRequest() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        CancellationSignal cancellationSignal = new CancellationSignal();
        cancellationSignal.cancel(); //cancel
        Feature feature = new Feature.Builder(1).build();
        CompletableFuture<Bundle> resultBundle = new CompletableFuture<>();
        mOnDeviceIntelligenceManager.processRequestStreaming(feature,
                new Bundle(), 1, cancellationSignal,
                null, EXECUTOR, new StreamingProcessingCallback() {
                    @Override
                    public void onPartialResult(@NonNull Bundle partialResult) {
                        Log.i(TAG, "New Content : " + partialResult);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        resultBundle.complete(result);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
        assertThat(resultBundle.get()).isNotNull();
        assertThat(
                resultBundle.get().isEmpty()).isTrue(); // When cancelled before sending request,
        // we simulate empty response.
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void signalPropagatedWhenSignalIsInvokedBeforeAndDuringRequest() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(4);
        ProcessingSignal processingSignal = new ProcessingSignal();
        processingSignal.sendSignal(PersistableBundle.EMPTY);
        processingSignal.sendSignal(PersistableBundle.EMPTY);
        Feature feature = new Feature.Builder(1).build();
        mOnDeviceIntelligenceManager.processRequestStreaming(feature,
                new Bundle(), 1, null,
                processingSignal, EXECUTOR, new StreamingProcessingCallback() {
                    @Override
                    public void onPartialResult(@NonNull Bundle partialResult) {
                        Log.i(TAG, "New Content : " + partialResult);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        processingSignal.sendSignal(PersistableBundle.EMPTY);
        assertThat(statusLatch.await(2, SECONDS)).isTrue();
    }

    //===================== Tests for Manager Methods When No Service is Configured =============

    @Test
    @SkipSetupAndTeardown
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void exceptionWhenAttemptingGetVersionWithoutServiceConfigured() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        assumeFalse("Service is already configured as part of the device overlay config.",
                isServiceOverlayConfigured());
        mOnDeviceIntelligenceManager =
                (OnDeviceIntelligenceManager)
                        mContext.getSystemService(Context.ON_DEVICE_INTELLIGENCE_SERVICE);
        clearTestableOnDeviceIntelligenceService();
        // Test throws IllegalStateException
        assertThrows("no service configured to perform getVersion",
                IllegalStateException.class,
                () -> mOnDeviceIntelligenceManager.getVersion(EXECUTOR,
                        result -> Log.i(TAG, "Feature : =" + result)));
    }

    @Test
    @SkipSetupAndTeardown
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void exceptionWhenAttemptingProcessRequestWithoutServiceConfigured() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        assumeFalse("Service is already configured as part of the device overlay config.",
                isServiceOverlayConfigured());
        mOnDeviceIntelligenceManager =
                (OnDeviceIntelligenceManager)
                        mContext.getSystemService(Context.ON_DEVICE_INTELLIGENCE_SERVICE);
        clearTestableOnDeviceIntelligenceService();
        Feature feature = new Feature.Builder(1).build();
        // Test throws IllegalStateException
        assertThrows(
                "no service configured for processRequestStreaming",
                IllegalStateException.class,
                () -> mOnDeviceIntelligenceManager.processRequestStreaming(feature,
                        new Bundle(), 1,
                        null, null, EXECUTOR,
                        new StreamingProcessingCallback() {
                            @Override
                            public void onPartialResult(@NonNull Bundle partialResult) {
                                Log.i(TAG, "New Content : " + partialResult);
                            }

                            @Override
                            public void onResult(Bundle result) {
                                Log.i(TAG, "Final Result : " + result);
                            }

                            @Override
                            public void onError(@NonNull OnDeviceIntelligenceException error) {
                                Log.e(TAG, "Final Result : ", error);
                            }
                        }));
    }

    // ========= Test package manager returns parent process package name for isolated_compute_app
    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void inferenceServiceShouldReturnParentPackageName() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        Feature feature = new Feature.Builder(1).build();
        CompletableFuture<String> packageNameFuture = new CompletableFuture<>();
        mOnDeviceIntelligenceManager.processRequest(feature,
                Bundle.EMPTY, REQUEST_TYPE_GET_PACKAGE_NAME, null,
                null, EXECUTOR, new ProcessingCallback() {
                    @Override
                    public void onResult(@NonNull Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        packageNameFuture.complete(result.getString(TEST_KEY));
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Error Occurred", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
        assertThat(packageNameFuture.get()).isEqualTo(CTS_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void callerUidReceivedIsOriginalCallerUid() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        Feature feature = new Feature.Builder(1).build();
        mOnDeviceIntelligenceManager.processRequest(feature,
                Bundle.EMPTY, REQUEST_TYPE_GET_CALLER_UID, null,
                null, EXECUTOR, new ProcessingCallback() {
                    @Override
                    public void onResult(@NonNull Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        assertThat(result.getInt(TEST_KEY)).isEqualTo(Process.myUid());
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Error Occurred", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }


    //===================== Tests for accessing file from isolated process via non-isolated =======
    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void canAccessFilesInIsolated() throws Exception {
        int[] requestTypes =
                new int[]{REQUEST_TYPE_GET_FILE_FROM_MAP, REQUEST_TYPE_GET_FILE_FROM_STREAM,
                        REQUEST_TYPE_GET_FILE_FROM_PFD,
                        REQUEST_TYPE_GET_FILE_FROM_NON_FILES_DIRECTORY};
        for (int requestType : requestTypes) {
            sendRequestToReadTestFile(requestType);
        }
    }

    private void sendRequestToReadTestFile(int requestType)
            throws InterruptedException, ExecutionException {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        Feature feature = new Feature.Builder(1).build();
        CountDownLatch statusLatch = new CountDownLatch(1);
        CompletableFuture<String> fileContents = new CompletableFuture<>();
        mOnDeviceIntelligenceManager.processRequest(feature,
                Bundle.EMPTY, requestType, null,
                null, EXECUTOR, new StreamingProcessingCallback() {
                    @Override
                    public void onPartialResult(@NonNull Bundle partialResult) {
                        Log.i(TAG, "New Content : " + partialResult);
                    }

                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        fileContents.complete(result.getString(TEST_KEY));
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
        assertThat(fileContents.get()).isEqualTo(TEST_CONTENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void updateProcessingStateReturnsSuccessfully() throws Exception {
        // When targets run as a different user than 0, it is not possible to get service
        // instance from user 0 in this test.
        assumeTrue(isSystemUser());
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        // init the intelligence service
        CtsIntelligenceService.initServiceConnectionLatch();
        mOnDeviceIntelligenceManager.getVersion(EXECUTOR, unused -> statusLatch.countDown());
        statusLatch.await(1, SECONDS);

        // call update state on the service instance
        CtsIntelligenceService.waitServiceConnect();
        OnDeviceIntelligenceService onDeviceIntelligenceService =
                CtsIntelligenceService.getServiceInstance();
        CountDownLatch statusLatch2 = new CountDownLatch(1);
        onDeviceIntelligenceService.updateProcessingState(Bundle.EMPTY, EXECUTOR, result -> {
            assertThat(result.isEmpty()).isTrue();
            statusLatch2.countDown();
        });

        assertThat(statusLatch2.await(1, SECONDS)).isTrue();
    }


    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE_MODULE)
    public void getLatestInferenceInfoReturnSuccessfully() throws Exception {
        // When targets run as a different user than 0, it is not possible to get service
        // instance from user 0 in this test.
        assumeTrue(isSystemUser());
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.DUMP,
                        Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        mOnDeviceIntelligenceManager.processRequest(new Feature.Builder(1).build(),
                Bundle.EMPTY, REQUEST_TYPE_POPULATE_INFERENCE_INFO, null,
                null, EXECUTOR, new ProcessingCallback() {
                    @Override
                    public void onResult(@NonNull Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Error Occurred", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
        List<InferenceInfo> inferenceInfoList = mOnDeviceIntelligenceManager.getLatestInferenceInfo(
                0);
        assertThat(inferenceInfoList).isNotEmpty();
    }

    //===================== Tests data augmentation while processing request =====================
    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void dataAugmentationReturnsDataToInference() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        Feature feature = new Feature.Builder(1).build();
        CountDownLatch statusLatch = new CountDownLatch(1);
        CompletableFuture<String> augmentedContent = new CompletableFuture<>();
        mOnDeviceIntelligenceManager.processRequest(feature,
                Bundle.EMPTY, REQUEST_TYPE_GET_AUGMENTED_DATA, null,
                null, EXECUTOR, new StreamingProcessingCallback() {
                    @Override
                    public void onPartialResult(@NonNull Bundle partialResult) {
                        Log.i(TAG, "New Content : " + partialResult);
                    }

                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        augmentedContent.complete(result.getString(TEST_AUGMENT_KEY));
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }

                    @Override
                    public void onDataAugmentRequest(Bundle processedContent,
                            Consumer<Bundle> contentConsumer) {
                        Bundle bundle = new Bundle();
                        bundle.putString(TEST_AUGMENT_KEY, TEST_AUGMENT_CONTENT);
                        contentConsumer.accept(bundle);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
        assertThat(augmentedContent.get()).isEqualTo(TEST_AUGMENT_CONTENT);
    }

    //===================== Tests broadcasts are sent for model updates =========================
    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void broadcastsMustBeSentOnModelUpdates() throws Exception {
        assumeTrue(isSystemUser());
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        setTestableBroadcastKeys(new String[]{MODEL_LOADED_BROADCAST_ACTION, "blah"},
                mContext.getPackageName());
        Feature feature = new Feature.Builder(1).build();
        CountDownLatch statusLatch = new CountDownLatch(2);
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action != null) {
                    Log.d(TAG, "Received broadcast with action: " + action);
                    if (action == MODEL_LOADED_BROADCAST_ACTION) {
                        statusLatch.countDown();
                    }
                }
            }
        };
        mContext.registerReceiver(broadcastReceiver,
                new IntentFilter(MODEL_LOADED_BROADCAST_ACTION), RECEIVER_EXPORTED);
        mOnDeviceIntelligenceManager.processRequest(feature,
                Bundle.EMPTY, 1, null,
                null, EXECUTOR, new StreamingProcessingCallback() {
                    @Override
                    public void onPartialResult(@NonNull Bundle partialResult) {
                        Log.i(TAG, "New Content : " + partialResult);
                    }

                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        assertThat(statusLatch.await(5, SECONDS)).isTrue();
    }

    //===================== Tests unbind based on timeout settings are invoked ====================

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void serviceUnbindsWhenCallbackIsNotPopulatedAfterIdleTimeout() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE,
                        Manifest.permission.WRITE_SECURE_SETTINGS);
        assumeTrue(isSystemUser());
        updateSecureSettings();
        // Feature Id to ensure no callbacks are invoked
        Feature feature = new Feature.Builder(3).build();
        CtsIntelligenceService.initServiceConnectionLatch();
        CtsIntelligenceService.initUnbindLatch();
        mOnDeviceIntelligenceManager.requestFeatureDownload(feature, null, EXECUTOR,
                new DownloadCallback() {
                    @Override
                    public void onDownloadFailed(int failureStatus,
                            @Nullable String errorMessage,
                            @NonNull PersistableBundle errorParams) {
                        Log.e(TAG, "Got Error", new RuntimeException(errorMessage));
                    }

                    @Override
                    public void onDownloadProgress(long bytesDownloaded) {
                    }

                    @Override
                    public void onDownloadStarted(long bytesDownloaded) {

                    }

                    @Override
                    public void onDownloadCompleted(
                            @NonNull PersistableBundle downloadParams) {
                        Log.i(TAG, "Response : =" + downloadParams);
                    }
                });
        CtsIntelligenceService.waitServiceConnect();
        CtsIntelligenceService.waitForUnbind();
        resetSecureSettings();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void serviceUnbindsWhenCallbackIsPopulatedAfterIdleTimeout() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE,
                        Manifest.permission.WRITE_SECURE_SETTINGS);
        assumeTrue(isSystemUser());
        updateSecureSettings();
        CtsIntelligenceService.initServiceConnectionLatch();
        CtsIntelligenceService.initUnbindLatch();
        CountDownLatch statusLatch = new CountDownLatch(1);

        mOnDeviceIntelligenceManager.getVersion(EXECUTOR,
                result -> {
                    Log.i(TAG, "Version : =" + result);
                    statusLatch.countDown();
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
        CtsIntelligenceService.waitServiceConnect();
        CtsIntelligenceService.waitForUnbind();
        resetSecureSettings();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void deviceConfigUpdateMustBeSentOnInferenceServiceConnected() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.USE_ON_DEVICE_INTELLIGENCE,
                        "android.permission.WRITE_DEVICE_CONFIG",
                        "android.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG",
                        "android.permission.READ_DEVICE_CONFIG",
                        "android.permission.MONITOR_DEVICE_CONFIG_ACCESS");
        Feature feature = new Feature.Builder(1).build();
        CountDownLatch statusLatch = new CountDownLatch(1);
        String currentVal = DeviceConfig.getProperty(TEST_OD_NAMESPACE, "key1");
        if (currentVal == null) {
            currentVal = "val1";
        }
        String modifiedVal = currentVal + "_new";
        mOnDeviceIntelligenceManager.processRequest(feature,
                Bundle.EMPTY, REQUEST_TYPE_GET_UPDATED_DEVICE_CONFIG, null,
                null, EXECUTOR, new ProcessingCallback() {
                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        PersistableBundle receivedConfig = result.getParcelable(TEST_KEY,
                                PersistableBundle.class);
                        assertThat(receivedConfig.containsKey("key1")).isTrue();
                        assertThat(receivedConfig.getString("key1")).isEqualTo(modifiedVal);

                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        Executors.newScheduledThreadPool(1).schedule(
                () -> {
                    DeviceConfig.setProperty(TEST_OD_NAMESPACE, "key1", modifiedVal, false);
                    Log.i(TAG, "Finished writing property to device config.");
                }, 2L,
                SECONDS);
        assertThat(statusLatch.await(10, SECONDS)).isTrue();
        DeviceConfig.deleteProperty(TEST_OD_NAMESPACE, "key1");
    }


    public static void clearTestableOnDeviceIntelligenceService() {
        runShellCommand("cmd on_device_intelligence set-temporary-services");
    }

    public void bindToTestableOnDeviceIntelligenceServices() {
        setTestableOnDeviceIntelligenceServiceNames(
                new String[]{CTS_INTELLIGENCE_SERVICE_NAME, CTS_INFERENCE_SERVICE_NAME});
        assertThat(CTS_INFERENCE_SERVICE_NAME).contains(getOnDeviceIntelligencePackageName());
    }

    private void updateSecureSettings() {
        Settings.Secure.putLong(mContext.getContentResolver(),
                Settings.Secure.ON_DEVICE_INTELLIGENCE_UNBIND_TIMEOUT_MS, SECONDS.toMillis(1));
        Settings.Secure.putLong(mContext.getContentResolver(),
                Settings.Secure.ON_DEVICE_INTELLIGENCE_IDLE_TIMEOUT_MS, SECONDS.toMillis(1));
    }

    private void resetSecureSettings() {
        Settings.Secure.putLong(mContext.getContentResolver(),
                Settings.Secure.ON_DEVICE_INTELLIGENCE_UNBIND_TIMEOUT_MS, -1);
        Settings.Secure.putLong(mContext.getContentResolver(),
                Settings.Secure.ON_DEVICE_INTELLIGENCE_IDLE_TIMEOUT_MS, HOURS.toMillis(1));
    }

    private String getOnDeviceIntelligencePackageName() {
        return mOnDeviceIntelligenceManager.getRemoteServicePackageName();
    }

    private boolean isServiceOverlayConfigured() {
        String sanboxedServiceComponentName = mContext.getResources()
                        .getString(
                                mContext.getResources()
                                        .getIdentifier(
                                                "config_defaultOnDeviceSandboxedInferenceService",
                                                "string",
                                                "android"));
        String intelligenceServiceComponentName = mContext.getResources()
                        .getString(
                                mContext.getResources()
                                        .getIdentifier(
                                                "config_defaultOnDeviceIntelligenceService",
                                                "string",
                                                "android"));

        return !TextUtils.isEmpty(sanboxedServiceComponentName) || !TextUtils.isEmpty(
                intelligenceServiceComponentName);
    }

    private static boolean isSystemUser() {
        return Process.myUserHandle().equals(UserHandle.SYSTEM);
    }

    public static void setTestableBroadcastKeys(String[] broadcastKeys, String packageName) {
        runShellCommand(
                "cmd on_device_intelligence set-model-broadcasts %s %s %s %d",
                broadcastKeys[0], broadcastKeys[1], packageName, TEMPORARY_SERVICE_DURATION);
    }


    public static void setTestableDeviceConfigNamespace(String configNamespace) {
        runShellCommand(
                "cmd on_device_intelligence set-deviceconfig-namespace %s %d", configNamespace,
                TEMPORARY_SERVICE_DURATION);
    }

    public static void setTestableOnDeviceIntelligenceServiceNames(String[] serviceNames) {
        runShellCommand(
                "cmd on_device_intelligence set-temporary-services %s %s %d",
                serviceNames[0], serviceNames[1], TEMPORARY_SERVICE_DURATION);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface SkipSetupAndTeardown {
    }

    @Rule
    public TestRule skipSetupAndTeardownRule = (base, description) -> new Statement() {
        @Override
        public void evaluate() throws Throwable {
            if (description.getAnnotation(SkipSetupAndTeardown.class) != null) {
                // Skip setup and teardown for annotated tests
                base.evaluate();
            } else {
                // Run setup and teardown for other tests
                setUp();
                try {
                    base.evaluate();
                } finally {
                    tearDown();
                }
            }
        }
    };


}
