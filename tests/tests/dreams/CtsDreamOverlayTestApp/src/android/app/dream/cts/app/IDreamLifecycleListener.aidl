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

/**
 * {@link IDreamLifecycleListener} is an interface for being informed when a controllable dream's
 * lifecycle changes.
 */
interface IDreamLifecycleListener {
    /**
     * invoked when the dream is attached.
     */
    void onAttachedToWindow(IControlledDream dream);

    /**
     * invoked when dreaming starts.
     */
    void onDreamingStarted(IControlledDream dream);

    /**
     * invoked when focus changes.
     */
    void onFocusChanged(IControlledDream dream, boolean hasFocus);

    /**
     * invoked when dreaming stops.
     */
    void onDreamingStopped(IControlledDream dream);

    /**
     * invoked when waking up.
     */
    void onWakeUp(IControlledDream dream);

    /**
     * invoked when the dream is destroyed.
     */
    void onDreamDestroyed(IControlledDream dream);

    /**
     * invoked when the dream is detached.
     */
    void onDetachedFromWindow(IControlledDream dream);
}