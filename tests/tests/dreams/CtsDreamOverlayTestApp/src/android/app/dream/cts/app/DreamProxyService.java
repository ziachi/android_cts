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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.HashSet;

/**
 * {@link DreamProxyService} provides a proxy to access running {@link ControlledTestDreamService}
 * instances from tests via the {@link IDreamProxy} interface.
 */
public class DreamProxyService extends Service {
    private static final String TAG = "DreamProxyService";

    // A list of active listeners who should be informed when a dream is published.
    private HashSet<IDreamListener> mListeners = new HashSet<IDreamListener>();
    private IDreamProxy mDreamControllerImpl = new IDreamProxy.Stub() {
        @Override
        public void publishDream(IControlledDream dream) {
            for (IDreamListener listener : mListeners) {
                try {
                    listener.onDreamPublished(dream);
                } catch (RemoteException e) {
                    Log.e(TAG, "could not publish dream", e);
                }
            }
        }

        public void registerListener(IDreamListener listener) {
            mListeners.add(listener);
        }

        public void unregisterListener(IDreamListener listener) {
            mListeners.remove(listener);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mDreamControllerImpl.asBinder();
    }
}
