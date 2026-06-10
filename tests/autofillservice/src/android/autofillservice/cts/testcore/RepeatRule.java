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

package android.autofillservice.cts.testcore;

import android.util.Log;

import androidx.annotation.Nullable;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Custom JUnit4 rule that reruns tests that are annotated with {@link Repeat} */
public class RepeatRule implements TestRule {

    private static final String TAG = "RepeatRule";

    private final Runnable mCleaner;

    public RepeatRule(@Nullable Runnable cleaner) {
        mCleaner = cleaner;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Repeat r = description.getAnnotation(Repeat.class);

                int retries = 1;

                if (r != null) {
                    retries = r.times();
                }

                for (int i = 1; i <= retries; i++) {
                    Log.d(
                            TAG,
                            "Repeat #"
                                    + retries
                                    + ": for test ["
                                    + description.getDisplayName()
                                    + "]");
                    base.evaluate();
                    if (mCleaner != null) {
                        mCleaner.run();
                    }
                }
            }
        };
    }
}
