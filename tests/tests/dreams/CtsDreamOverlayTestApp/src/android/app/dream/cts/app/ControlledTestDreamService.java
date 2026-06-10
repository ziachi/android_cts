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

package android.app.dream.cts.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.dreams.DreamService;
import android.util.Log;
import android.widget.FrameLayout;

import java.util.Arrays;
import java.util.HashSet;

/**
 * {@link ControlledTestDreamService} is a Dream implementation that is published to the
 * {@link DreamProxyService} so that tests can access and set its behavior.
 */
public class ControlledTestDreamService extends DreamService {
    private static final String TAG = "ControlledTestDream";

    // A list of key codes for keys that should be consumed by this dream's root view.
    private HashSet<Integer> mConsumedKeys = new HashSet<>();

    // The binder to the {@link DreamProxyService} where this dream is published to.
    private IDreamProxy mDreamProxy;

    // A list of lifecycle listeners to inform
    private HashSet<IDreamLifecycleListener> mDreamLifecycleListeners = new HashSet<>();

    // An {@link IControlledDream} implementation associating requests to actions on the dream.
    private IControlledDream mProxy = new IControlledDream.Stub() {
        @Override
        public void setInteractive(boolean interactive) {
            ControlledTestDreamService.this.setInteractive(interactive);
        }

        @Override
        public void setConsumedKeys(int[] keyCodes) {
            // Note that setting the consumed keys list clears any existing set keys.
            mConsumedKeys.clear();
            mConsumedKeys.addAll(Arrays.stream(keyCodes).boxed().toList());
        }

        @Override
        public void registerLifecycleListener(IDreamLifecycleListener listener) {
            mDreamLifecycleListeners.add(listener);
        }

        @Override
        public void unregisterLifecycleListener(IDreamLifecycleListener listener) {
            mDreamLifecycleListeners.remove(listener);
        }
    };

    // Connection to proxy service.
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mDreamProxy = IDreamProxy.Stub.asInterface(service);
            try {
                mDreamProxy.publishDream(mProxy);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to publish dream", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        final Intent intent = new Intent();
        intent.setClass(this, DreamProxyService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mDreamLifecycleListeners.forEach(
                listener -> {
                    try {
                        listener.onFocusChanged(mProxy, hasFocus);
                    } catch (RemoteException e) {
                        Log.e(TAG, "could not inform listeners of onAttachedToWindow", e);
                    }
                });
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mDreamLifecycleListeners.forEach(
                listener -> {
                    try {
                        listener.onAttachedToWindow(mProxy);
                    } catch (RemoteException e) {
                        Log.e(TAG, "could not inform listeners of onAttachedToWindow", e);
                    }
                });

        setFullscreen(true);

        final FrameLayout frameLayout = new FrameLayout(getApplicationContext());
        frameLayout.setBackgroundColor(Color.YELLOW);
        frameLayout.setOnKeyListener((v, keyCode, event) -> mConsumedKeys.contains(keyCode));
        frameLayout.setFocusable(true);
        setContentView(frameLayout);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        mDreamLifecycleListeners.forEach(
                listener -> {
                    try {
                        listener.onDreamingStarted(mProxy);
                    } catch (RemoteException e) {
                        Log.e(TAG, "could not inform listeners of onDreamingStarted", e);
                    }
                });
    }

    @Override
    public void onWakeUp() {
        mDreamLifecycleListeners.forEach(
                listener -> {
                    try {
                        listener.onWakeUp(mProxy);
                    } catch (RemoteException e) {
                        Log.e(TAG, "could not inform listeners of onWakeUp", e);
                    }
                });
        super.onWakeUp();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        mDreamLifecycleListeners.forEach(
                listener -> {
                    try {
                        listener.onDreamingStopped(mProxy);
                    } catch (RemoteException e) {
                        Log.e(TAG, "could not inform listeners of onDreamingStopped", e);
                    }
                });
    }

    @Override
    public void onDestroy() {
        mDreamLifecycleListeners.forEach(
                listener -> {
                    try {
                        listener.onDreamDestroyed(mProxy);
                    } catch (RemoteException e) {
                        Log.e(TAG, "could not inform listeners of onDreamingStopped", e);
                    }
                });
        super.onDestroy();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mDreamLifecycleListeners.forEach(
                listener -> {
                    try {
                        listener.onDetachedFromWindow(mProxy);
                    } catch (RemoteException e) {
                        Log.e(TAG, "could not inform listeners of onDetachedFromWindow", e);
                    }
                });
    }
}
