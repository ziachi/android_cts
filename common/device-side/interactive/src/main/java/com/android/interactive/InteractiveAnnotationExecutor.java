/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.interactive;

import static com.android.bedstead.harrier.AnnotationExecutorUtil.checkFailOrSkip;

import androidx.annotation.NonNull;

import com.android.bedstead.adb.Adb;
import com.android.bedstead.harrier.AnnotationExecutor;
import com.android.bedstead.harrier.DeviceStateComponent;
import com.android.bedstead.harrier.annotations.FailureMode;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.usb.Usb;
import com.android.interactive.annotations.Interactive;
import com.android.interactive.annotations.UntetheredTest;
import com.android.interactive.steps.ConnectViaAdbToHostStep;
import com.android.interactive.steps.UntetherDeviceStep;

import java.lang.annotation.Annotation;

/**
 * Implementation of {@link AnnotationExecutor} for use with Interactive.
 */
@SuppressWarnings("unused")
public final class InteractiveAnnotationExecutor
        implements AnnotationExecutor, DeviceStateComponent {

    // There is a complicated implementation of @UntetheredTest here to make it work with Bedstead
    // with minimal changes to Bedstead. The important things to know are:
    // The weight of @Interactive causes it to always be processed AFTER @UntetheredTest. This means
    // by the time applyAnnotation is called with @Interactive, we know for sure whether the current
    // test is @UntetheredTest or not.
    // So what we do is at this point we force the switch to tethered or not - to ensure that we
    // keep the device untethered as long a its needed but not longer (so it can begin passing
    // results back to the host as soon as possible).

    private boolean mRequiresUntethered = false;

    @Override
    public void applyAnnotation(@NonNull Annotation annotation) {
        if (annotation instanceof RequireBooleanStepResult requireBooleanStepResultAnnotation) {
            requireBooleanStepResult(requireBooleanStepResultAnnotation.step(),
                    requireBooleanStepResultAnnotation.expectedResult(),
                    requireBooleanStepResultAnnotation.reason(),
                    requireBooleanStepResultAnnotation.failureMode());
        } else if (annotation instanceof UntetheredTest) {
            mRequiresUntethered = true;
        } else if (annotation instanceof Interactive) {
            if (mRequiresUntethered) {
                if (!Usb.INSTANCE.isConnected()) {
                    return;
                }

                if (!Adb.INSTANCE.isEnabledOverWifi() &&
                        !TestApis.instrumentation().arguments().getBoolean("CAN_UNTETHER")) {
                    throw new AssertionError("This test requires a device under test "
                            + "untethered from a host. Use adb-over-wifi to achieve this.");
                }

                try {
                    Step.execute(UntetherDeviceStep.class);
                } catch (Exception e) {
                    throw new RuntimeException("Error untethering", e);
                }
            } else {
                try {
                    Step.execute(ConnectViaAdbToHostStep.class);
                } catch (Exception e) {
                    throw new RuntimeException("Error connecting to host", e);
                }
            }
        }
    }

    @Override
    public void teardownShareableState() {
        try {
            Step.execute(ConnectViaAdbToHostStep.class);
        } catch (Exception e) {
            throw new RuntimeException("Error connecting to host", e);
        }
    }

    @Override
    public void teardownNonShareableState() {
        mRequiresUntethered = false;
    }

    private void requireBooleanStepResult(
            Class<? extends Step<Boolean>> stepClass,
            boolean expectedResult, String reason, FailureMode failureMode) {
        boolean result;
        try {
            result = Step.execute(stepClass);
        } catch (Exception e) {
            throw new RuntimeException("Error executing step " + stepClass, e);
        }

        checkFailOrSkip(reason, result == expectedResult, failureMode);
    }
}
