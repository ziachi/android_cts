/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.accessibilityservice.cts.utils;


import android.content.Context;
import android.content.pm.PackageManager;

import java.util.Optional;
import java.util.function.Consumer;

public class CtsTestUtils {
    private CtsTestUtils() {}

    /** The timeout in milliseconds between two events to consider the device idle. */
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 500L;

    /** The maximal global timeout in milliseconds in which to wait for an idle state. */
    public static final long DEFAULT_GLOBAL_TIMEOUT_MS = 5000L;

    public static Throwable assertThrows(Runnable action) {
        return assertThrows(Throwable.class, action);
    }

    public static <E extends Throwable> E assertThrows(Class<E> exceptionClass, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected an exception");
        } catch (Throwable e) {
            if (exceptionClass.isInstance(e)) {
                return (E) e;
            }
            throw new RuntimeException(e);
        }
    }

    public static <T> void runIfNotNull(T callee, Consumer<T> action) {
        Optional.ofNullable(callee).ifPresent(action);
    }

    /** Returns {@code true} if run on an Automotive device */
    public static boolean isAutomotive(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm != null && pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }
}
