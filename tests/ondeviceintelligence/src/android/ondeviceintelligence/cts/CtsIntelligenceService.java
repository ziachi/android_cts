/*
 * Copyright (C) 2021 The Android Open Source Project
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


import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.TEST_CONTENT;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.TEST_FILE_NAME;
import static android.ondeviceintelligence.cts.OnDeviceIntelligenceManagerTest.TEST_KEY;

import android.app.ondeviceintelligence.DownloadCallback;
import android.app.ondeviceintelligence.Feature;
import android.app.ondeviceintelligence.FeatureDetails;
import android.app.ondeviceintelligence.OnDeviceIntelligenceException;
import android.content.Intent;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.service.ondeviceintelligence.OnDeviceIntelligenceService;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public class CtsIntelligenceService extends OnDeviceIntelligenceService {
    public static CountDownLatch sConnectLatch;
    public static CountDownLatch sUnbindLatch;

    // The timeout to wait for async result
    public static final long WAIT_TIMEOUT_IN_MS = 5000;

    private static OnDeviceIntelligenceService sService;
    private final Executor mAsyncRequestExecutor = Executors.newCachedThreadPool();


    @Override
    public void onReady() {
        Log.d(TAG, "onReady()");
        sService = this;
        if (sConnectLatch != null) {
            sConnectLatch.countDown();
        }
    }

    public static OnDeviceIntelligenceService getServiceInstance() {
        return sService;
    }

    public static Feature getSampleFeature(int id) {
        return new Feature.Builder(id).setFeatureParams(PersistableBundle.EMPTY).setModelName(
                "test-model").setName("test-feature").setType(1).setVariant(2).build();
    }

    public static FeatureDetails getSampleFeatureDetails(int featureId) {
        if (featureId == 0) {
            PersistableBundle bundle = new PersistableBundle();
            bundle.putInt(TEST_KEY, 1);
            return new FeatureDetails(/* status */ featureId, bundle);
        } else {
            return new FeatureDetails(/* status */ featureId);
        }
    }

    static final String TAG = "CtsIntelligenceService";

    @Override
    public void onInferenceServiceConnected() {
        mAsyncRequestExecutor.execute(() -> {
            try {
                File testFile = new File(getFilesDir(), TEST_FILE_NAME);
                File testFile2 = new File(TEST_FILE_NAME);
                populateTestContent(testFile);
                populateTestContent(testFile2);
            } catch (IOException e) {
                Log.w(TAG, "Received failure when creating file.", e);
            }
        });
        Log.i(TAG, "Received onInferenceServiceStarted");
    }

    @Override
    public void onInferenceServiceDisconnected() {
        Log.i(TAG, "Received onInferenceServiceDisconnected");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");
        mAsyncRequestExecutor.execute(() -> {
            if (sUnbindLatch != null) {
                sUnbindLatch.countDown();
            }
        });
        return true;
    }

    @Override
    public void onGetReadOnlyFeatureFileDescriptorMap(@NonNull Feature feature,
            @NonNull Consumer<Map<String, ParcelFileDescriptor>> fileDescriptorMapConsumer) {
        mAsyncRequestExecutor.execute(() -> createAndPopulateTestFile(fileDescriptorMapConsumer));
    }

    private void createAndPopulateTestFile(
            @NonNull Consumer<Map<String, ParcelFileDescriptor>> fileDescriptorMapConsumer) {
        try {
            File testFile = new File(getFilesDir(), TEST_FILE_NAME);
            populateTestContent(testFile);
            try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(testFile,
                    ParcelFileDescriptor.MODE_READ_ONLY)) {
                Map<String, ParcelFileDescriptor> fileDescriptorMap = new ArrayMap<>();
                fileDescriptorMap.put(testFile.getName(), pfd);
                fileDescriptorMapConsumer.accept(fileDescriptorMap);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "File Not Found", e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDownloadFeature(int callerUid, @NonNull Feature feature,
            @Nullable CancellationSignal cancellationSignal,
            @NonNull DownloadCallback downloadCallback) {
        Log.w(TAG, "Received onDownloadFeature call from: " + callerUid);
        if (feature.getId() == 3) {
            return; // don't invoke any callback
        }
        if (feature.getId() == 2) {
            downloadCallback.onDownloadFailed(1, "error message", PersistableBundle.EMPTY);
            return;
        }
        downloadCallback.onDownloadStarted(100);
        downloadCallback.onDownloadProgress(100);
        downloadCallback.onDownloadCompleted(PersistableBundle.EMPTY);
    }

    @Override
    public void onGetFeatureDetails(int callerUid, @NonNull Feature feature,
            @NonNull OutcomeReceiver<FeatureDetails, OnDeviceIntelligenceException> featureDetailsCallback) {
        featureDetailsCallback.onResult(getSampleFeatureDetails(feature.getId()));
    }

    @Override
    public void onGetFeature(int callerUid, int featureId,
            @NonNull OutcomeReceiver<Feature, OnDeviceIntelligenceException> featureCallback) {
        if (!isMainThread()) {
            featureCallback.onError(
                    new OnDeviceIntelligenceException(-1, "Not running on app thread."));
        }

        featureCallback.onResult(getSampleFeature(featureId));
    }

    @Override
    public void onListFeatures(int callerUid,
            @NonNull OutcomeReceiver<List<Feature>, OnDeviceIntelligenceException> listFeaturesCallback) {
        List<Feature> featureList = new ArrayList<>();
        featureList.add(getSampleFeature(0));
        listFeaturesCallback.onResult(featureList);
    }

    @Override
    public void onGetVersion(@NonNull LongConsumer versionConsumer) {
        versionConsumer.accept(1);
    }

    /**
     * Init the CountDownLatch that is used to wait for service onReady() and onShutdown().
     */
    public static void initServiceConnectionLatch() {
        sConnectLatch = new CountDownLatch(1);
    }

    /**
     * Wait for service onCreate().
     */
    public static void waitServiceConnect() throws InterruptedException {
        if (sConnectLatch == null) {
            throw new AssertionError("Should init connect CountDownLatch");
        }
        if (!sConnectLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            throw new AssertionError("OnDeviceIntelligenceService doesn't start.");
        }
        sConnectLatch = null;
    }

    /**
     * Init the CountDownLatch that is used to wait for service onReady() and onShutdown().
     */
    public static void initUnbindLatch() {
        sUnbindLatch = new CountDownLatch(1);
    }

    /**
     * Wait for service {@link #onUnbind}.
     */
    public static void waitForUnbind() throws InterruptedException {
        if (sUnbindLatch == null) {
            throw new AssertionError("Should init connect CountDownLatch");
        }
        if (!sUnbindLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            throw new AssertionError("OnDeviceIntelligenceService doesn't get unbinded.");
        }
        sUnbindLatch = null;
    }

    private void populateTestContent(File file) throws IOException {
        if (file.exists()) {
            return;
        }

        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(TEST_CONTENT.getBytes());
        }
    }

    private static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }
}
