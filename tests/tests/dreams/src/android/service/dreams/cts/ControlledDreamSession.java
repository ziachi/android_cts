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

package android.service.dreams.cts;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.IntDef;
import android.app.dream.cts.app.IControlledDream;
import android.app.dream.cts.app.IDreamLifecycleListener;
import android.app.dream.cts.app.IDreamListener;
import android.app.dream.cts.app.IDreamProxy;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.server.wm.DreamCoordinator;
import android.util.ArraySet;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * {@link ControlledDreamSession} manages connecting and accessing a controlled dream instance that
 * is published through a dream proxy.
 */
public class ControlledDreamSession {
    private static final String TAG = "ControlledDreamSession";

    // Timeout that is used for waiting on various steps to complete, such as connecting to the
    // proxy service and starting the dream.
    private static final int TIMEOUT_SECONDS = 2;

    // The test app's proxy service component.
    private static final String DREAM_CONTROL_COMPONENT =
            "android.app.dream.cts.app/.DreamProxyService";


    public static final int DREAM_LIFECYCLE_UNKNOWN = 0;
    public static final int DREAM_LIFECYCLE_ON_ATTACHED_TO_WINDOW = 1;
    public static final int DREAM_LIFECYCLE_ON_DREAMING_STARTED = 2;
    public static final int DREAM_LIFECYCLE_ON_FOCUS_GAINED = 3;
    public static final int DREAM_LIFECYCLE_ON_FOCUS_LOST = 4;
    public static final int DREAM_LIFECYCLE_ON_WAKEUP = 5;
    public static final int DREAM_LIFECYCLE_ON_DREAMING_STOPPED = 6;
    public static final int DREAM_LIFECYCLE_ON_DETACHED_FROM_WINDOW = 7;
    public static final int DREAM_LIFECYCLE_ON_DESTROYED = 8;

    @IntDef(prefix = { "DREAM_LIFECYCLE_" }, value = {
            DREAM_LIFECYCLE_UNKNOWN,
            DREAM_LIFECYCLE_ON_ATTACHED_TO_WINDOW,
            DREAM_LIFECYCLE_ON_DREAMING_STARTED,
            DREAM_LIFECYCLE_ON_FOCUS_GAINED,
            DREAM_LIFECYCLE_ON_WAKEUP,
            DREAM_LIFECYCLE_ON_DREAMING_STOPPED,
            DREAM_LIFECYCLE_ON_DETACHED_FROM_WINDOW,
            DREAM_LIFECYCLE_ON_DESTROYED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Dreamlifecycle{}

    /**
     * Returns a string description for the lifecycle.
     */
    public static String lifecycleToString(@Dreamlifecycle int lifecycle) {
        return switch (lifecycle) {
            case DREAM_LIFECYCLE_UNKNOWN -> "unknown";
            case DREAM_LIFECYCLE_ON_ATTACHED_TO_WINDOW -> "attached_to_window";
            case DREAM_LIFECYCLE_ON_DREAMING_STARTED -> "on_dream_started";
            case DREAM_LIFECYCLE_ON_FOCUS_GAINED -> "on_focus_gained";
            case DREAM_LIFECYCLE_ON_WAKEUP -> "on_wake_up";
            case DREAM_LIFECYCLE_ON_DREAMING_STOPPED -> "on_dreaming_stopped";
            case DREAM_LIFECYCLE_ON_DETACHED_FROM_WINDOW -> "on_detached_from_window";
            case DREAM_LIFECYCLE_ON_DESTROYED -> "on_destroyed";
            default -> "not found";
        };
    }


    // Connection for accessing the dream proxy.
    private static final class ProxyServiceConnection implements ServiceConnection {
        private final CountDownLatch mLatch;
        private final IBinder.DeathRecipient mDeathRecipient;
        private IDreamProxy mProxy;

        ProxyServiceConnection(CountDownLatch latch, IBinder.DeathRecipient deathRecipient) {
            mLatch = latch;
            mDeathRecipient = deathRecipient;
        }

        public IDreamProxy getProxy() {
            return mProxy;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mProxy = IDreamProxy.Stub.asInterface(service);
            try {
                service.linkToDeath(mDeathRecipient, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "could not link to death", e);
            }
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mProxy = null;
        }
    }

    private final Context mContext;
    private final ComponentName mDreamComponent;
    private final DreamCoordinator mDreamCoordinator;

    private ProxyServiceConnection mServiceConnection;

    private IControlledDream mControlledDream;

    private ArrayList<Integer> mSeenLifecycles = new ArrayList<>();

    private final Set<Consumer<Integer>> mLifecycleConsumers = new ArraySet<>();

    public ControlledDreamSession(Context context, ComponentName dreamComponent,
            DreamCoordinator coordinator) {
        mContext = context;
        mDreamComponent = dreamComponent;
        mDreamCoordinator = coordinator;
    }

    private IDreamLifecycleListener mLifecycleListener = new IDreamLifecycleListener.Stub() {
        public void onAttachedToWindow(IControlledDream dream) {
            pushLifecycle(DREAM_LIFECYCLE_ON_ATTACHED_TO_WINDOW);
        }

        public void onDreamingStarted(IControlledDream dream) {
            pushLifecycle(DREAM_LIFECYCLE_ON_DREAMING_STARTED);
        }

        public void onFocusChanged(IControlledDream dream, boolean hasFocus) {
            pushLifecycle(
                    hasFocus ? DREAM_LIFECYCLE_ON_FOCUS_GAINED : DREAM_LIFECYCLE_ON_FOCUS_LOST);
        }

        public void onDreamingStopped(IControlledDream dream) {
            pushLifecycle(DREAM_LIFECYCLE_ON_DREAMING_STOPPED);
        }

        public void onWakeUp(IControlledDream dream) {
            pushLifecycle(DREAM_LIFECYCLE_ON_WAKEUP);
        }

        public void onDetachedFromWindow(IControlledDream dream) {
            pushLifecycle(DREAM_LIFECYCLE_ON_DETACHED_FROM_WINDOW);
        }

        public void onDreamDestroyed(IControlledDream dream) {
            pushLifecycle(DREAM_LIFECYCLE_ON_DESTROYED);
        }
    };

    private void pushLifecycle(@Dreamlifecycle int lifecycle) {
        mSeenLifecycles.add(lifecycle);
        // Make a copy of the set to prevent concurrent modification.
        final Set<Consumer<Integer>> consumers = new ArraySet<>();
        consumers.addAll(mLifecycleConsumers);
        consumers.forEach(consumer -> consumer.accept(lifecycle));
    }

    /**
     * Sets the dream component specified at construction as the active dream and subsequently
     * starts said dream.
     * @return An {@link IControlledDream} for accessing the currently running dream.
     */
    public IControlledDream start() throws InterruptedException, RemoteException {
        if (mServiceConnection != null) {
            Log.e(TAG, "session already started");
            return null;
        }

        // Connect to dream controller
        final ComponentName controllerService =
                ComponentName.unflattenFromString(DREAM_CONTROL_COMPONENT);
        final Intent intent = new Intent();
        intent.setComponent(controllerService);
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        mServiceConnection  = new ProxyServiceConnection(countDownLatch,
                new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        cleanup(true);
                    }
                });
        mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        assertThat(countDownLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

        final CountDownLatch dreamConnectLatch = new CountDownLatch(1);
        final IDreamListener dreamConnectListener = new IDreamListener.Stub() {
            @Override
            public void onDreamPublished(IControlledDream dream) {
                mControlledDream = dream;
                dreamConnectLatch.countDown();
            }
        };

        mServiceConnection.getProxy().registerListener(dreamConnectListener);

        // Start Dream
        mDreamCoordinator.setActiveDream(mDreamComponent);
        mDreamCoordinator.startDream();

        // Wait for dream to connect to the DreamController
        assertThat(dreamConnectLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        mControlledDream.registerLifecycleListener(mLifecycleListener);
        mServiceConnection.getProxy().unregisterListener(dreamConnectListener);

        return mControlledDream;
    }


    /**
     * Returns the dream published during start.
     */
    public IControlledDream getControlledDream() {
        return mControlledDream;
    }

    /**
     * Stops the current dream.
     */
    public void stop() {
        cleanup(false);
    }

    private void cleanup(boolean dead) {
        if (mServiceConnection == null) {
            Log.e(TAG, "session not started");
            return;
        }

        if (!dead && mControlledDream != null) {
            try {
                mControlledDream.unregisterLifecycleListener(mLifecycleListener);
            } catch (RemoteException e) {
                Log.e(TAG, "could not unregister lifecycle listener", e);
            }
        }

        mControlledDream = null;

        mContext.unbindService(mServiceConnection);
        mServiceConnection = null;
    }

    /**
     * Waits for a lifecycle to be reached, timing out if never reached.
     */
    public void awaitLifecycle(@Dreamlifecycle int targetLifecycle) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final Consumer<Integer> consumer = lifecycle -> {
            if (lifecycle == targetLifecycle) {
                latch.countDown();
            }
        };

        try {
            mLifecycleConsumers.add(consumer);

            if (mSeenLifecycles.contains(targetLifecycle)) {
                return;
            }

            assertThat(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } finally {
            mLifecycleConsumers.remove(consumer);
        }
    }
}
