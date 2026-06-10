/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.secure_element.cts;

import static com.google.common.truth.Truth.assertThat;

import android.se.omapi.SeFrameworkInitializer;
import android.se.omapi.SeServiceManager;
import android.test.AndroidTestCase;

public class SeFrameworkInitializerTest extends AndroidTestCase {
    public void test_SetSeServiceManager() {
        assertThrows(IllegalStateException.class,
                () -> SeFrameworkInitializer.setSeServiceManager(
                    new SeServiceManager()));
    }

    // org.junit.Assume.assertThrows is not available until JUnit 4.13
    private static void assertThrows(Class<? extends Exception> exceptionClass, Runnable r) {
        try {
            r.run();
            fail("Expected " + exceptionClass + " to be thrown.");
        } catch (Exception exception) {
            assertThat(exception).isInstanceOf(exceptionClass);
        }
    }
}
