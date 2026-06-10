/*
 * Copyright 2024 The Android Open Source Project
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

package android.photopicker.cts;

import android.app.Activity;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.photopicker.EmbeddedPhotoPickerClient;
import android.widget.photopicker.EmbeddedPhotoPickerFeatureInfo;
import android.widget.photopicker.EmbeddedPhotoPickerProvider;
import android.widget.photopicker.EmbeddedPhotoPickerProviderFactory;
import android.widget.photopicker.EmbeddedPhotoPickerSession;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class EmbeddedTestActivity extends Activity {

    public static final String TAG = "PhotopickerEmbeddedTestActivity";

    /**
     * Default {@link EmbeddedPhotoPickerFeatureInfo} for creating embedded photopicker session.
     */
    private final EmbeddedPhotoPickerFeatureInfo mInfo =
            new EmbeddedPhotoPickerFeatureInfo.Builder().build();

    /**
     * List of selected uris that this test client has permission to access.
     */
    private final List<Uri> mSelectedUris = new ArrayList<>();

    private CountDownLatch mItemsDeselectedClientInvocationLatch;
    private CountDownLatch mItemsSelectedClientInvocationLatch;
    private CountDownLatch mSelectionCompleteClientInvocationLatch;
    private CountDownLatch mSessionOpenedClientInvocationLatch;
    private CountDownLatch mSessionErrorClientInvocationLatch;

    /**
     * Client callbacks that the service will use to interact with the client
     */
    private final EmbeddedPhotoPickerClient mClient = new EmbeddedPhotoPickerClient() {
        @Override
        public void onUriPermissionRevoked(@NonNull List<Uri> uris) {
            mSelectedUris.removeAll(uris);

            if (mItemsDeselectedClientInvocationLatch != null) {
                mItemsDeselectedClientInvocationLatch.countDown();
            }
        }

        @Override
        public void onUriPermissionGranted(@NonNull List<Uri> uris) {
            mSelectedUris.addAll(uris);

            if (mItemsSelectedClientInvocationLatch != null) {
                mItemsSelectedClientInvocationLatch.countDown();
            }
        }

        @Override
        public void onSessionError(@NonNull Throwable cause) {
            mSession = null;
            mSelectedUris.clear();

            if (mSessionErrorClientInvocationLatch != null) {
                mSessionErrorClientInvocationLatch.countDown();
            }
        }

        @Override
        public void onSessionOpened(
                @NonNull EmbeddedPhotoPickerSession session) {
            mSession = session;
            mSurfaceView.setChildSurfacePackage(session.getSurfacePackage());
            if (mSessionOpenedClientInvocationLatch != null) {
                mSessionOpenedClientInvocationLatch.countDown();
            }
        }

        @Override
        public void onSelectionComplete() {
            mSession.close();

            if (mSelectionCompleteClientInvocationLatch != null) {
                mSelectionCompleteClientInvocationLatch.countDown();
            }
        }
    };

    /**
     * {@link SurfaceView} in which the embedded view from the remote service will be displayed.
     */
    private SurfaceView mSurfaceView;

    /**
     * The current session for the embedded picker used by client to pass on information to the
     * service
     */
    private EmbeddedPhotoPickerSession mSession;

    /**
     * Provider that client will use to create a new embedded photopicker session.
     */
    private EmbeddedPhotoPickerProvider mProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        // Create embedded photopicker provider
        mProvider = EmbeddedPhotoPickerProviderFactory.create(getApplicationContext());

        mSurfaceView = findViewById(R.id.embedded_picker_surface);
        mSurfaceView.setZOrderOnTop(true);

        final Button launchEmbeddedPickerButton = findViewById(
                R.id.open_embedded_picker_session_button);
        launchEmbeddedPickerButton.setOnClickListener(this::onLaunchEmbeddedPickerButtonClicked);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mSession != null) {
            mSession.notifyConfigurationChanged(newConfig);
        }
    }

    private void onLaunchEmbeddedPickerButtonClicked(View view) {
        DisplayManager displayManager = getSystemService(DisplayManager.class);
        assert displayManager != null;
        int displayId = displayManager.getDisplays()[0].getDisplayId();

        IBinder hostToken = mSurfaceView.getHostToken();
        assert hostToken != null;

        mProvider.openSession(
                hostToken,
                displayId,
                mSurfaceView.getWidth(),
                mSurfaceView.getHeight(),
                mInfo,
                Executors.newSingleThreadExecutor(),
                mClient);
    }

    /**
     * Returns the current session for the embedded picker.
     */
    public EmbeddedPhotoPickerSession getSession() {
        return mSession;
    }

    /**
     * Returns the surface view for the embedded picker.
     */
    public SurfaceView getSurfaceView() {
        return mSurfaceView;
    }

    /**
     * Returns the list of URIs of the selected medias.
     */
    public List<Uri> getSelectedUris() {
        return mSelectedUris;
    }

    /**
     * Sets a {@link CountDownLatch} that gets counted down during
     * {@link EmbeddedPhotoPickerClient#onItemsSelected(List)} to enable verifying its invocation
     * within a given time frame.
     *
     * @param itemsSelectedClientInvocationLatch the {@link CountDownLatch latch} to
     * {@link CountDownLatch#countDown() countDown} when
     * {@link EmbeddedPhotoPickerClient#onItemsSelected(List)} is invoked.
     */
    public void setCountDownLatchForItemsSelectedClientInvocation(
            @NonNull CountDownLatch itemsSelectedClientInvocationLatch) {
        mItemsSelectedClientInvocationLatch = itemsSelectedClientInvocationLatch;
    }

    /**
     * Sets a {@link CountDownLatch} that gets counted down during
     * {@link EmbeddedPhotoPickerClient#onItemsDeselected(List)} to enable verifying its invocation
     * within a given time frame.
     *
     * @param itemsDeselectedClientInvocationLatch the {@link CountDownLatch latch} to
     * {@link CountDownLatch#countDown() countDown} when
     * {@link EmbeddedPhotoPickerClient#onItemsDeselected(List)} is invoked.
     */
    public void setCountDownLatchForItemsDeselectedClientInvocation(
            @NonNull CountDownLatch itemsDeselectedClientInvocationLatch) {
        mItemsDeselectedClientInvocationLatch = itemsDeselectedClientInvocationLatch;
    }

    /**
     * Sets a {@link CountDownLatch} that gets counted down during
     * {@link EmbeddedPhotoPickerClient#onSelectionComplete()} to enable verifying its invocation
     * within a given time frame.
     *
     * @param selectionCompleteClientInvocationLatch the {@link CountDownLatch latch} to
     * {@link CountDownLatch#countDown() countDown} when
     * {@link EmbeddedPhotoPickerClient#onSelectionComplete()} is invoked.
     */
    public void setCountDownLatchForSelectionCompleteClientInvocation(
            @NonNull CountDownLatch selectionCompleteClientInvocationLatch) {
        mSelectionCompleteClientInvocationLatch = selectionCompleteClientInvocationLatch;
    }

    /**
     * Sets a {@link CountDownLatch} that gets counted down during
     * {@link EmbeddedPhotoPickerClient#onSessionOpened(EmbeddedPhotoPickerSession)} to enable
     * verifying its invocation within a given time frame.
     *
     * @param sessionOpenedClientInvocation the {@link CountDownLatch latch} to
     * {@link CountDownLatch#countDown() countDown} when
     * {@link EmbeddedPhotoPickerClient#onSessionOpened(EmbeddedPhotoPickerSession)} ()} is invoked.
     */
    public void setCountDownLatchForSessionOpenedClientInvocation(
            @NonNull CountDownLatch sessionOpenedClientInvocation) {
        mSessionOpenedClientInvocationLatch = sessionOpenedClientInvocation;
    }

    /**
     * Sets a {@link CountDownLatch} that gets counted down during
     * {@link EmbeddedPhotoPickerClient#onSessionError(Throwable)} to enable verifying its
     * invocation within a given time frame.
     *
     * @param sessionErrorClientInvocationLatch the {@link CountDownLatch latch} to
     * {@link CountDownLatch#countDown() countDown} when
     * {@link EmbeddedPhotoPickerClient#onSessionError(Throwable)} is invoked.
     */
    public void setCountDownLatchForSessionErrorClientInvocation(
            @NonNull CountDownLatch sessionErrorClientInvocationLatch) {
        mSessionErrorClientInvocationLatch = sessionErrorClientInvocationLatch;
    }
}
