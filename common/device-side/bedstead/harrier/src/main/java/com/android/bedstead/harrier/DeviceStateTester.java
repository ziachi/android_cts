/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.bedstead.harrier;

import com.android.bedstead.nene.exceptions.NeneException;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * A helper class to finely test behavior of {@code DeviceState} components.
 */
public final class DeviceStateTester implements AutoCloseable {

    private final DeviceState mDeviceState = new DeviceState();

    /** This value can be overridden using {@code stepName()}. */
    private String mStepName = "deviceStateInternalTest";

    public DeviceState getDeviceState() {
        return mDeviceState;
    }

    /**
     * Apply bedstead {@code annotations} to a dynamically generated test.
     * <p>
     * Use {@code runnable} to execute post-processing step(s) for e.g. assertions or
     * getting/setting a state.
     */
    public void apply(List<Annotation> annotations, Runnable runnable) {
        setup(annotations);
        try {
            apply(annotations);
            runnable.run();
        } catch (Throwable exception) {
            mDeviceState.onTestFailed(exception);
            throw exception;
        }
    }

    /**
     * Apply bedstead {@code annotations} to a dynamically generated test.
     */
    public void apply(List<Annotation> annotations) {
        setup(annotations);
    }

    /**
     * Use this method to give a name to your test which would help in analyzing the logs.
     */
    public DeviceStateTester stepName(String name) {
        mStepName = name;
        return this;
    }

    /**
     * Teardown all shareable and non-shareable states.
     */
    public void tearDown() {
        mDeviceState.teardown();
    }
    private void setup(List<Annotation> annotations) {
        try {
            Description description =
                    Description.createTestDescription(this.getClass(), mStepName,
                            annotations.toArray(new Annotation[0]));

            statement(description).evaluate();
        } catch (Throwable e) {
            throw new NeneException("Unable to setup DeviceStateTester", e);
        }
    }

    private Statement statement(Description description) {
        return new Statement() {
            @Override
            public void evaluate() {
                mDeviceState.prepareTestState(description);
            }
        };
    }

    @Override
    public void close() throws Exception {
        tearDown();
    }
}
