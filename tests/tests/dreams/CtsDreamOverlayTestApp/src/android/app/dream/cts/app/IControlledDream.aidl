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

import android.app.dream.cts.app.IDreamLifecycleListener;

/**
* The {@link IControlledDream} interface allows for various characteristics and behaviors of a dream
* to be changed in real-time.
*/
interface IControlledDream {
    /**
     * Sets whether the dream is interactive and should accept interaction input.
     */
    void setInteractive(boolean interactive);

    /**
     * Sets the key codes of keys that the dream should consume. The provided list will replace any
     * previously set list.
     */
    void setConsumedKeys(in int[] keyCodes);

    /**
     * Registers a lifecycle listener.
     */
    void registerLifecycleListener(in IDreamLifecycleListener listener);

    /**
     * Unregisters a lifecycle listener.
     */
    void unregisterLifecycleListener(in IDreamLifecycleListener listener);


}