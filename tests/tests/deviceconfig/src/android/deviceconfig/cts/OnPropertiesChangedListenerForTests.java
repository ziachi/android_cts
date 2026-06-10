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

package android.deviceconfig.cts;

import android.provider.DeviceConfig;
import android.provider.DeviceConfig.OnPropertiesChangedListener;
import android.provider.DeviceConfig.Properties;
import android.util.Log;

import com.google.errorprone.annotations.concurrent.GuardedBy;

import org.junit.rules.TestName;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for {@link OnPropertiesChangedListener} implementations on tests.
 *
 * <p>Tests that use it should call {@link #unregisterAfter(TestName)} in a {@code @After} method.
 */
abstract class OnPropertiesChangedListenerForTests implements OnPropertiesChangedListener {

    protected static final String TAG = OnPropertiesChangedListenerForTests.class.getSimpleName();

    private static int sNextId;

    // Used to automatically unregister listeners at the end of the test - ideally each test should
    // unregister manually, but given that removeOnPropertiesChangedListener() doesn't check if the
    // listener was register beforehand, it's safer to just "over-unregister" (hence it doesn't even
    // need to be a Set)
    @GuardedBy("sRegisteredListeners")
    private static final List<OnPropertiesChangedListenerForTests> sRegisteredListeners =
            new ArrayList<>();

    private final int mId = ++sNextId;
    private final String mTest;

    OnPropertiesChangedListenerForTests(TestName testName) {
        mTest = testName.getMethodName();
        sRegisteredListeners.add(this);
    }

    @Override
    public void onPropertiesChanged(Properties properties) {
        Log.d(TAG, "onPropertiesChanged() on " + this);
    }

    /**
     * Optional method that adds additional info to the string returned by {@link #toString()}.
     */
    protected void decorateToString(StringBuilder toString) {
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder(getClass().getSimpleName()).append("[test=")
                .append(mTest).append(", id=")
                .append(mId);
        decorateToString(string);
        return string.append("]").toString();
    }

    /**
     * Unregisters all listeners.
     */
    static void unregisterAfter(Class<?> clazz, TestName testName) {
        synchronized (sRegisteredListeners) {
            if (!sRegisteredListeners.isEmpty()) {
                Log.d(TAG, "Unregistering " + sRegisteredListeners.size() + " listeners after "
                        + clazz.getSimpleName() + "#" + testName.getMethodName());
                for (var listener : sRegisteredListeners) {
                    Log.v(TAG, "Unregistering " + listener);
                    DeviceConfig.removeOnPropertiesChangedListener(listener);
                }
                sRegisteredListeners.clear();
            }
        }
    }
}
