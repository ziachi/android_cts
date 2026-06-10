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

package com.android.interactive.testrules;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.android.bedstead.nene.TestApis;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A {@link TestRule} that helps save the name of a test case in the context during test execution.
 */
public class TestNameSaver implements TestRule {

    private static final String LOG_TAG = "Interactive.TestNameSaver";

    public static final String INTERACTIVE_TEST_NAME = "INTERACTIVE_TEST_NAME";

    // packageName + className of a test class.
    private final String mPackageClass;

    // Whether clearing the test name from the context after the execution.
    private final boolean mClearTestName;

    public TestNameSaver(Object testInstance) {
        this(testInstance.getClass(), /* clearTestName= */ true);
    }

    public TestNameSaver(Class<?> testClass, boolean clearTestName) {
        mPackageClass = testClass.getCanonicalName();
        mClearTestName = clearTestName;
    }

    @Override
    public Statement apply(Statement base, Description description) {

        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                SharedPreferences sharedPref = getSharedPreferences();
                String testName = mPackageClass + "#" + description.getMethodName();

                boolean testNameUpdated = setTestName(sharedPref, testName);

                assertTrue(
                        "Failed to save the test name into the testing context.", testNameUpdated);

                try {
                    base.evaluate();
                } finally {
                    if (mClearTestName) {
                        boolean testNameCleared = clearTestName(sharedPref);
                        Log.i(
                                LOG_TAG,
                                "Cleard test name: " + testName + ", result: " + testNameCleared);
                    } else {
                        Log.i(LOG_TAG, "Skip clearing the test name: " + testName);
                    }
                }
            }
        };
    }

    private static SharedPreferences getSharedPreferences() {
        return TestApis.context()
                .instrumentedContext()
                .getSharedPreferences(INTERACTIVE_TEST_NAME, Context.MODE_PRIVATE);
    }

    /** Sets the test name within the context, returns true if success. */
    private static boolean setTestName(SharedPreferences sharedPref, String testName) {
        return sharedPref.edit().putString(INTERACTIVE_TEST_NAME, testName).commit();
    }

    /** Clears the test name within the context, returns true if success. */
    private static boolean clearTestName(SharedPreferences sharedPref) {
        return sharedPref.edit().remove(INTERACTIVE_TEST_NAME).commit();
    }
}
