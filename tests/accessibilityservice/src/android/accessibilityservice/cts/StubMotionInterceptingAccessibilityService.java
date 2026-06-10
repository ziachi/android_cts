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

package android.accessibilityservice.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.TestUtils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class StubMotionInterceptingAccessibilityService extends InstrumentedAccessibilityService {
    private Consumer<MotionEvent> mMotionEventListener;

    public void setMotionEventSources(int sources) {
        AccessibilityServiceInfo info = getServiceInfo();
        info.setMotionEventSources(sources);
        setServiceInfo(info);
    }

    /** Sets the motion event sources to intercept but not consume events from. */
    public void setObservedMotionEventSources(int sources) {
        AccessibilityServiceInfo info = getServiceInfo();
        info.setObservedMotionEventSources(sources);
        setServiceInfo(info);
    }

    /**
     * Calls {@link AccessibilityServiceInfo#setMotionEventSources} and awaits confirmation
     * that the input filter has been updated.
     *
     * <p>
     * The AccessibilityInputFilter is updated asynchronously after the A11yService
     * requests its list of interested motion event sources. For normal use this brief
     * delay is inconsequential, but for testing we need a way to know when the filter
     * is actually updated so that we can then inject a test event.
     *
     * <p>
     * There are no public APIs to inspect the current InputFilter flags, so instead
     * we send canary event(s) of type {@code canarySource} until we observe at least
     * one. After a canary is observed, we know that the filter is installed, so tests
     * can then safely send and await an event from {@code interestedSource}.
     *
     * @param canarySource     The source that is only used as a canary.
     * @param interestedSource The source (different from canary) that is expected by the test.
     */
    public void setAndAwaitMotionEventSources(UiAutomation uiAutomation, int canarySource,
            int interestedSource, long timeoutMs) {
        assertThat(canarySource).isNotEqualTo(interestedSource);
        final int requestedSources = canarySource | interestedSource;
        AccessibilityServiceInfo info = getServiceInfo();
        info.setMotionEventSources(requestedSources);
        setServiceInfo(info);
        assertThat(getServiceInfo().getMotionEventSources()).isEqualTo(requestedSources);
        final Object waitObject = new Object();
        final AtomicBoolean foundCanaryEvent = new AtomicBoolean(false);
        mMotionEventListener = motionEvent -> {
            synchronized (waitObject) {
                if (motionEvent.getSource() == canarySource) {
                    foundCanaryEvent.set(true);
                }
                waitObject.notifyAll();
            }
        };

        // Wait for the canary to signal that the filter has been updated.
        final int maxAttempts = 3;
        final String errorMessage = "Expected canary event from source " + canarySource;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            uiAutomation.injectInputEventToInputFilter(createMotionEvent(canarySource));
            try {
                TestUtils.waitOn(waitObject, foundCanaryEvent::get,
                        timeoutMs, errorMessage);
                return;
            } catch (AssertionError ignored) {
                // retry
            }
        }
        fail(errorMessage);
    }

    /**
     * Injects an event to the AccessibilityInputFilter then awaits that the event
     * is seen by {@link #onMotionEvent}.
     */
    public void injectAndAwaitMotionEvent(UiAutomation uiAutomation, int source, long timeoutMs) {
        final Object waitObject = new Object();
        AtomicBoolean gotEvent = new AtomicBoolean(false);
        mMotionEventListener = motionEvent -> {
            synchronized (waitObject) {
                if (motionEvent.getSource() == source) {
                    gotEvent.set(true);
                }
                waitObject.notifyAll();
            }
        };
        uiAutomation.injectInputEventToInputFilter(createMotionEvent(source));
        TestUtils.waitOn(waitObject, gotEvent::get, timeoutMs,
                "Expected single event from source " + source);
    }

    public void setOnMotionEventListener(Consumer<MotionEvent> listener) {
        mMotionEventListener = listener;
    }

    @Override
    public void onMotionEvent(@NonNull MotionEvent event) {
        super.onMotionEvent(event);
        mMotionEventListener.accept(event);
    }

    private MotionEvent createMotionEvent(int source) {
        // Only source is used by these tests, so set other properties to valid defaults.
        final long eventTime = SystemClock.uptimeMillis();
        final MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
        props.id = 0;
        return MotionEvent.obtain(eventTime,
                eventTime,
                MotionEvent.ACTION_MOVE,
                1 /* pointerCount */,
                new MotionEvent.PointerProperties[]{props},
                new MotionEvent.PointerCoords[]{new MotionEvent.PointerCoords()},
                0 /* metaState */,
                0 /* buttonState */,
                0 /* xPrecision */,
                0 /* yPrecision */,
                1 /* deviceId */,
                0 /* edgeFlags */,
                source,
                0 /* flags */);
    }
}
