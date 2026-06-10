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
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.CTS_INFERENCE_SERVICE_NAME;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.CTS_INTELLIGENCE_SERVICE_NAME;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.CTS_PACKAGE_NAME;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.KEY_SERVICE_ENABLED;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.NAMESPACE_ON_DEVICE_INTELLIGENCE;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.setTestableOnDeviceIntelligenceServiceNames;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertNull;

import static org.junit.Assert.assertThrows;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.Manifest;
import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.app.ondeviceintelligence.OnDeviceIntelligenceManager;
import android.app.ondeviceintelligence.ProcessingCallback;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * Test the OnDeviceIntelligenceManager API. Run with "atest OnDeviceIntelligenceManagerTest"
 * .
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "PM will not recognize OnDeviceIntelligenceManagerService in instantMode.")
public class BundleValidationTest {
    private static final String TAG = "BundleValidationTest";
    private static final Executor EXECUTOR = InstrumentationRegistry.getContext().getMainExecutor();
    public static final String TEST_KEY = "test_key";
    public static final int REQUEST_TYPE_PROCESS_CUSTOM_PARCELABLE_AS_BYTES = 2001;

    private File file;
    private File file2;

    private OnDeviceIntelligenceManager mOnDeviceIntelligenceManager;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final DeviceConfigStateChangerRule mDeviceConfigStateChangerRule =
            new DeviceConfigStateChangerRule(
                    getInstrumentation().getTargetContext(),
                    NAMESPACE_ON_DEVICE_INTELLIGENCE,
                    KEY_SERVICE_ENABLED,
                    "true");

    @Before
    public void setUp() throws Exception {
        file = File.createTempFile("testReadOnly", "bin");
        file2 = File.createTempFile("testReadOnly2", "bin");
        file.createNewFile();
        file2.createNewFile();
        mOnDeviceIntelligenceManager = getInstrumentation().getContext().getSystemService(
                OnDeviceIntelligenceManager.class);
        OnDeviceIntelligenceManagerTest.clearTestableOnDeviceIntelligenceService();
        bindToTestableOnDeviceIntelligenceServices();
    }

    @After
    public void tearDown() throws Exception {
        OnDeviceIntelligenceManagerTest.clearTestableOnDeviceIntelligenceService();
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        file.delete();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void canSendAndReceiveCustomParcelables() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        Feature feature = new Feature.Builder(1).build();
        Bundle request = prepareRequestWithCustomParcelable();

        mOnDeviceIntelligenceManager.processRequest(feature, request,
                REQUEST_TYPE_PROCESS_CUSTOM_PARCELABLE_AS_BYTES, null, null, EXECUTOR,
                new ProcessingCallback() {
                    @Override
                    public void onResult(Bundle result) {
                        Log.i(TAG, "Final Result : " + result);
                        resultFuture.complete(result.getString(TEST_KEY));
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
        assertThat(resultFuture.get()).isEqualTo("abc");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void canSendReadOnlyPfds() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        Feature feature = new Feature.Builder(1).build();
        Bundle request = new Bundle();
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_ONLY);
             ParcelFileDescriptor pfd2 = ParcelFileDescriptor.open(file2,
                     ParcelFileDescriptor.MODE_READ_ONLY);) {
            request.putParcelable("request", pfd);
            request.putParcelableArray("request2", new ParcelFileDescriptor[]{pfd2});
            mOnDeviceIntelligenceManager.processRequest(feature, request, 1, null, null, EXECUTOR,
                    new ProcessingCallback() {
                        @Override
                        public void onResult(Bundle result) {
                            statusLatch.countDown();
                        }

                        @Override
                        public void onError(@NonNull OnDeviceIntelligenceException error) {
                        }
                    });
            assertThat(statusLatch.await(1, SECONDS)).isTrue();
        }

        Bundle request2 = new Bundle();
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_WRITE)) {
            request2.putParcelable("request", pfd);
            assertThrows(BadParcelableException.class,
                    () -> processRequestAndHandleException(feature, request2, 1));
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void canSendPersistableBundleOrPrimitives() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        Feature feature = new Feature.Builder(1).build();
        Bundle request = prepareBundleWithPersistableBundleAndPrimitives();

        mOnDeviceIntelligenceManager.processRequest(feature, request, 1, null, null, EXECUTOR,
                new ProcessingCallback() {
                    @Override
                    public void onResult(Bundle result) {
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }


    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void canSendNestedBundleWithSameConstraints() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        Feature feature = new Feature.Builder(1).build();
        Bundle request = prepareBundleWithPersistableBundleAndPrimitives();

        Bundle higherBundle = new Bundle();
        higherBundle.putBundle("someKey", request);

        mOnDeviceIntelligenceManager.processRequest(feature, higherBundle, 1, null, null, EXECUTOR,
                new ProcessingCallback() {
                    @Override
                    public void onResult(Bundle result) {
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                        Log.e(TAG, "Final Result : ", error);
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void canSendImmutableBitmap() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        CountDownLatch statusLatch = new CountDownLatch(1);
        Feature feature = new Feature.Builder(1).build();
        Bundle request = new Bundle();
        Bitmap immutableBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).copy(
                Bitmap.Config.ARGB_8888, false);
        Bitmap immutableBitmap2 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).copy(
                Bitmap.Config.ARGB_8888, false);
        request.putParcelable("test-bitmap", immutableBitmap);
        request.putParcelableArray("test-bitmaps", new Bitmap[]{immutableBitmap2});

        mOnDeviceIntelligenceManager.processRequest(feature, request, 1, null, null, EXECUTOR,
                new ProcessingCallback() {
                    @Override
                    public void onResult(Bundle result) {
                        statusLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                    }
                });
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void shouldRejectNestedBundlesWithFd() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        Feature feature = new Feature.Builder(1).build();
        Bundle request = new Bundle();

        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_WRITE)) {
            Bundle bundle = new Bundle();
            bundle.putParcelable("test-pfd", pfd);
            request.putParcelable("test-bundle", bundle);
            assertThrows(BadParcelableException.class,
                    () -> processRequestAndHandleException(feature, request, 1));
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_ON_DEVICE_INTELLIGENCE)
    public void shouldIgnoreCustomParcelableWhenRequestHasFds() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.USE_ON_DEVICE_INTELLIGENCE);
        Feature feature = new Feature.Builder(1).build();
        CountDownLatch statusLatch = new CountDownLatch(1);

        Bundle request = new Bundle();
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_ONLY)) {
            request.putParcelable("test-custom-parcelable", new SimpleParcelable("test"));
            request.putParcelable("test-pfd", pfd);
            mOnDeviceIntelligenceManager.processRequest(feature, request, 1, null, null, EXECUTOR,
                    new ProcessingCallback() {
                        @Override
                        public void onResult(Bundle result) {
                            result.setClassLoader(SimpleParcelable.class.getClassLoader());
                            assertNull(result.getParcelable("test-custom-parcelable"));
                            statusLatch.countDown();
                        }

                        @Override
                        public void onError(@NonNull OnDeviceIntelligenceException error) {
                        }
                    });
        }
        assertThat(statusLatch.await(1, SECONDS)).isTrue();
    }

    private void bindToTestableOnDeviceIntelligenceServices() {
        assertThat(getOnDeviceIntelligencePackageName()).isNotEqualTo(CTS_PACKAGE_NAME);
        setTestableOnDeviceIntelligenceServiceNames(
                new String[]{CTS_INTELLIGENCE_SERVICE_NAME, CTS_INFERENCE_SERVICE_NAME});
        assertThat(CTS_INFERENCE_SERVICE_NAME).contains(getOnDeviceIntelligencePackageName());
    }

    private String getOnDeviceIntelligencePackageName() {
        return mOnDeviceIntelligenceManager.getRemoteServicePackageName();
    }

    private Bundle prepareRequestWithCustomParcelable() {
        Bundle request = new Bundle();
        SimpleParcelable simpleParcelable = new SimpleParcelable("abc");
        Parcel parcel = Parcel.obtain();
        simpleParcelable.writeToParcel(parcel, 0);
        byte[] bytes = parcel.marshall();
        request.putByteArray("request", bytes);
        parcel.recycle();
        return request;
    }

    private Bundle prepareBundleWithPersistableBundleAndPrimitives() {
        Bundle request = new Bundle();
        request.putParcelable("test-pers-bundle", PersistableBundle.EMPTY);
        request.putInt("test-int", 1);
        request.putIntArray("test-int", new int[]{1, 2, 3});
        return request;
    }

    private void processRequestAndHandleException(Feature feature, Bundle request,
            int requestType) {
        mOnDeviceIntelligenceManager.processRequest(feature, request, requestType, null, null,
                EXECUTOR, new ProcessingCallback() {
                    @Override
                    public void onResult(Bundle result) {
                    }

                    @Override
                    public void onError(@NonNull OnDeviceIntelligenceException error) {
                    }
                });
    }
}
