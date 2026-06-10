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

package android.view.cts.util;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.window.InputTransferToken;

public class ASurfaceControlInputReceiverTestUtils {
    static {
        System.loadLibrary("ctssurfacecontrol_jni");
    }

    public interface InputReceiver {
        /**
         * Invoked when the surface control received a motion event.
         */
        boolean onMotionEvent(MotionEvent motionEvent);
        /**
         * Invoked when the surface control received a key event.
         */
        boolean onKeyEvent(KeyEvent keyEvent);
    }

    /**
     * Create a native input receiver for a SurfaceControl
     */
    public static native long nCreateInputReceiver(boolean batched, InputTransferToken hostToken,
            long aSurfaceControl, InputReceiver inputReceiver);

    /**
     * Deletes the native input receiver
     */
    public static native void nDeleteInputReceiver(long aInputReceiver);

    /**
     * Get the input transfer token for the registered native receiver
     */
    public static native InputTransferToken nGetInputTransferToken(long aInputReceiver);
}
