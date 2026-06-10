/**
 * Copyright (c) 2024, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.app.dream.cts.app;

import android.app.dream.cts.app.IControlledDream;
import android.app.dream.cts.app.IDreamListener;

/**
 * {@link IDreamProxy} defines the interface of a hub for proxying controllable dreams to listeners.
 */
interface IDreamProxy {
    /**
     * Informs any registered listeners that the supplied {@link IControlledDream} is ready for use.
     */
    void publishDream(IControlledDream dream);

    /**
     * Registers a listener to be notified of any future published {@link IControlledDream}.
     */
    void registerListener(IDreamListener listener);

    /**
     * Removes a listener from receiving any future notifications about published
     * {@link IControlledDream}.
     */
    void unregisterListener(IDreamListener listener);
}