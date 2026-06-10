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

package android.view.inputmethod.cts.util;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.WindowInsets.Type.displayCutout;
import static android.view.WindowInsets.Type.systemBars;
import static android.view.WindowInsets.Type.systemGestures;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.ActivityTaskManager;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.server.wm.CtsWindowInfoUtils;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowMetrics;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CommonTestUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.input.UinputStylus;
import com.android.cts.input.UinputTouchDevice;
import com.android.cts.input.UinputTouchScreen;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class TestUtils {
    private static final long TIME_SLICE = 100;  // msec

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    /**
     * Executes a call on the application's main thread, blocking until it is complete.
     *
     * <p>A simple wrapper for {@link Instrumentation#runOnMainSync(Runnable)}.</p>
     *
     * @param task task to be called on the UI thread
     */
    public static void runOnMainSync(@NonNull Runnable task) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
    }

    /**
     * Executes a call on the application's main thread, blocking until it is complete. When a
     * Throwable is thrown in the runnable, the exception is propagated back to the
     * caller's thread. If it is an unchecked throwable, it will be rethrown as is. If it is a
     * checked exception, it will be rethrown as a {@link RuntimeException}.
     *
     * <p>A simple wrapper for {@link Instrumentation#runOnMainSync(Runnable)}.</p>
     *
     * @param task task to be called on the UI thread
     */
    public static void runOnMainSyncWithRethrowing(@NonNull Runnable task) {
        FutureTask<Void> wrapped = new FutureTask<>(task, null);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(wrapped);
        try {
            wrapped.get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    /**
     * Retrieves a value that needs to be obtained on the main thread.
     *
     * <p>A simple utility method that helps to return an object from the UI thread.</p>
     *
     * @param supplier callback to be called on the UI thread to return a value
     * @param <T> Type of the value to be returned
     * @return Value returned from {@code supplier}
     */
    public static <T> T getOnMainSync(@NonNull Supplier<T> supplier) {
        final AtomicReference<T> result = new AtomicReference<>();
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> result.set(supplier.get()));
        return result.get();
    }

    /**
     * Does polling loop on the UI thread to wait until the given condition is met.
     *
     * @param condition Condition to be satisfied. This is guaranteed to run on the UI thread.
     * @param timeout timeout in millisecond
     * @param message message to display when timeout occurs.
     * @throws TimeoutException when the no event is matched to the given condition within
     *                          {@code timeout}
     */
    public static void waitOnMainUntil(
            @NonNull BooleanSupplier condition, long timeout, String message)
            throws TimeoutException {
        final AtomicBoolean result = new AtomicBoolean();

        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        while (!result.get()) {
            if (timeout < 0) {
                throw new TimeoutException(message);
            }
            instrumentation.runOnMainSync(() -> {
                if (condition.getAsBoolean()) {
                    result.set(true);
                }
            });
            try {
                Thread.sleep(TIME_SLICE);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            timeout -= TIME_SLICE;
        }
    }

    /**
     * Does polling loop on the UI thread to wait until the given condition is met.
     *
     * @param condition Condition to be satisfied. This is guaranteed to run on the UI thread.
     * @param timeout timeout in millisecond
     * @throws TimeoutException when the no event is matched to the given condition within
     *                          {@code timeout}
     */
    public static void waitOnMainUntil(@NonNull BooleanSupplier condition, long timeout)
            throws TimeoutException {
        waitOnMainUntil(condition, timeout, "");
    }

    public static boolean isInputMethodPickerShown(@NonNull InputMethodManager imm) {
        return SystemUtil.runWithShellPermissionIdentity(imm::isInputMethodPickerShown);
    }

    /** Returns {@code true} if the default display supports split screen multi-window. */
    public static boolean supportsSplitScreenMultiWindow() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        final Display defaultDisplay = dm.getDisplay(DEFAULT_DISPLAY);
        return ActivityTaskManager.supportsSplitScreenMultiWindow(
                context.createDisplayContext(defaultDisplay));
    }

    /**
     * Call a command to turn screen On.
     *
     * This method will wait until the power state is interactive with {@link
     * PowerManager#isInteractive()}.
     */
    public static void turnScreenOn() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final PowerManager pm = context.getSystemService(PowerManager.class);
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        CommonTestUtils.waitUntil("Device does not wake up after " + TIMEOUT + " seconds", TIMEOUT,
                () -> pm != null && pm.isInteractive());
    }

    /**
     * Call a command to turn screen off.
     *
     * This method will wait until the power state is *NOT* interactive with
     * {@link PowerManager#isInteractive()}.
     * Note that {@link PowerManager#isInteractive()} may not return {@code true} when the device
     * enables Aod mode, recommend to add (@link DisableScreenDozeRule} in the test to disable Aod
     * for making power state reliable.
     */
    public static void turnScreenOff() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final PowerManager pm = context.getSystemService(PowerManager.class);
        runShellCommand("input keyevent KEYCODE_SLEEP");
        CommonTestUtils.waitUntil("Device does not sleep after " + TIMEOUT + " seconds", TIMEOUT,
                () -> pm != null && !pm.isInteractive());
    }

    /**
     * Simulates a {@link KeyEvent#KEYCODE_MENU} event to unlock screen.
     *
     * This method will retry until {@link KeyguardManager#isKeyguardLocked()} return {@code false}
     * in given timeout.
     *
     * Note that {@link KeyguardManager} is not accessible in instant mode due to security concern,
     * so this method always throw exception with instant app.
     */
    public static void unlockScreen() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getContext();
        final KeyguardManager kgm = context.getSystemService(KeyguardManager.class);

        assertFalse("This method is currently not supported in instant apps.",
                context.getPackageManager().isInstantApp());
        CommonTestUtils.waitUntil("Device does not unlock after " + TIMEOUT + " seconds", TIMEOUT,
                () -> {
                    SystemUtil.runWithShellPermissionIdentity(
                            () -> instrumentation.sendKeyDownUpSync((KeyEvent.KEYCODE_MENU)));
                    return kgm != null && !kgm.isKeyguardLocked();
                });
    }

    /**
     * Simulates a {@link KeyEvent} event to app.
     * @param keyCode for the {@link KeyEvent} to inject.
     * @param instrumentation test {@link Instrumentation} used for injection.
     */
    public static void injectKeyEvent(int keyCode, Instrumentation instrumentation)
            throws Exception {
        final long timestamp = SystemClock.uptimeMillis();
        instrumentation.getUiAutomation().injectInputEvent(
                new KeyEvent(timestamp, timestamp, KeyEvent.ACTION_DOWN, keyCode, 0)
                        , true /* sync */);
        instrumentation.getUiAutomation().injectInputEvent(
                new KeyEvent(timestamp, timestamp, KeyEvent.ACTION_UP, keyCode, 0)
                        , true /* sync */);
    }

    /**
     * Waits until the given activity is ready for input, this is only needed when directly
     * injecting input on screen via
     * {@link android.hardware.input.InputManager#injectInputEvent(InputEvent, int)}.
     */
    public static void waitUntilActivityReadyForInputInjection(Activity activity,
            String tag, String windowDumpErrMsg) throws InterruptedException {
        // If we requested an orientation change, just waiting for the window to be visible is not
        // sufficient. We should first wait for the transitions to stop, and the for app's UI thread
        // to process them before making sure the window is visible.
        CtsWindowInfoUtils.waitForStableWindowGeometry(Duration.ofSeconds(5));
        if (activity.getWindow() != null
                && !CtsWindowInfoUtils.waitForWindowOnTop(activity.getWindow())) {
            CtsWindowInfoUtils.dumpWindowsOnScreen(tag, windowDumpErrMsg);
            fail("Activity window did not become visible: " + activity);
        }
    }

    /**
     * Call a command to force stop the given application package.
     *
     * @param pkg The name of the package to be stopped.
     */
    public static void forceStopPackage(@NonNull String pkg) {
        runWithShellPermissionIdentity(() -> {
            runShellCommandOrThrow("am force-stop " + pkg);
        });
    }

    /**
     * Call a command to force stop the given application package.
     *
     * @param pkg The name of the package to be stopped.
     * @param userId The target user ID.
     */
    public static void forceStopPackage(@NonNull String pkg, int userId) {
        runWithShellPermissionIdentity(() -> {
            runShellCommandOrThrow("am force-stop " + pkg + " --user " + userId);
        });
    }

    /**
     * Inject Stylus move on the Display inside view coordinates so that initiation can happen.
     * @param view view on which stylus events should be overlapped.
     */
    public static void injectStylusEvents(@NonNull View view) {
        int offsetX = view.getWidth() / 2;
        int offsetY = view.getHeight() / 2;
        injectStylusEvents(view, offsetX, offsetY);
    }

    /**
     * Inject a stylus ACTION_DOWN event to the screen using given view's coordinates.
     * @param view  view whose coordinates are used to compute the event location.
     * @param x the x coordinates of the stylus event in the view's location coordinates.
     * @param y the y coordinates of the stylus event in the view's location coordinates.
     * @return the injected MotionEvent.
     */
    public static MotionEvent injectStylusDownEvent(@NonNull View view, int x, int y) {
        return injectStylusEvent(view, ACTION_DOWN, x, y);
    }

    /**
     * Inject a stylus ACTION_DOWN event in a multi-touch environment to the screen using given
     * view's coordinates.
     *
     * @param device {@link UinputTouchDevice}  stylus device.
     * @param view   view whose coordinates are used to compute the event location.
     * @param x      the x coordinates of the stylus event in the view's location coordinates.
     * @param y      the y coordinates of the stylus event in the view's location coordinates.
     * @return The object associated with the down pointer, which will later be used to
     * trigger move or lift.
     */
    public static UinputTouchDevice.Pointer injectStylusDownEvent(
            @NonNull UinputTouchDevice device, @NonNull View view, int x, int y) {
        return injectStylusDownEventInternal(device, view, x, y);
    }

    /**
     * Inject a stylus ACTION_DOWN event in a multi-touch environment to the screen using given
     * view's coordinates.
     *
     * @param device {@link UinputTouchDevice}  stylus device.
     * @param x      the x coordinates of the stylus event in display's coordinates.
     * @param y      the y coordinates of the stylus event in display's coordinates.
     * @return The object associated with the down pointer, which will later be used to
     * trigger move or lift.
     */
    public static UinputTouchDevice.Pointer injectStylusDownEvent(
            @NonNull UinputTouchDevice device, int x, int y) {
        return injectStylusDownEventInternal(device, null /* view */, x, y);
    }

    private static UinputTouchDevice.Pointer injectStylusDownEventInternal(
            @NonNull UinputTouchDevice device, @Nullable View view, int x, int y) {
        int[] xy = new int[2];
        if (view != null) {
            view.getLocationOnScreen(xy);
            x += xy[0];
            y += xy[1];
        }

        return device.touchDown(x, y, 255 /* pressure */);
    }

    /**
     * Inject a stylus ACTION_UP event in a multi-touch environment to the screen.
     *
     * @param pointer {@link #injectStylusDownEvent(UinputTouchDevice, View, int, int)}
     * returned Pointer.
     */
    public static void injectStylusUpEvent(@NonNull UinputTouchDevice.Pointer pointer) {
        pointer.lift();
    }

    /**
     * Inject a stylus ACTION_UP event to the screen using given view's coordinates.
     * @param view  view whose coordinates are used to compute the event location.
     * @param x the x coordinates of the stylus event in the view's location coordinates.
     * @param y the y coordinates of the stylus event in the view's location coordinates.
     * @return the injected MotionEvent.
     */
    public static MotionEvent injectStylusUpEvent(@NonNull View view, int x, int y) {
        return injectStylusEvent(view, ACTION_UP, x, y);
    }

    public static void injectStylusHoverEvents(@NonNull View view, int x, int y) {
        injectStylusEvent(view, MotionEvent.ACTION_HOVER_ENTER, x, y);
        injectStylusEvent(view, MotionEvent.ACTION_HOVER_MOVE, x, y);
        injectStylusEvent(view, MotionEvent.ACTION_HOVER_EXIT, x, y);
    }

    private static MotionEvent injectStylusEvent(@NonNull View view, int action, int x, int y) {
        int[] xy = new int[2];
        view.getLocationOnScreen(xy);
        x += xy[0];
        y += xy[1];

        // Inject stylus action
        long eventTime = SystemClock.uptimeMillis();
        final MotionEvent event =
                getMotionEvent(eventTime, eventTime, action, x, y,
                        MotionEvent.TOOL_TYPE_STYLUS);
        injectMotionEvent(event, true /* sync */);
        return event;
    }

    /**
     * Inject a finger tap event in a multi-touch environment to the screen using given
     * view's center coordinates.
     * @param device {@link UinputTouchDevice} touch device.
     * @param view  view whose coordinates are used to compute the event location.
     */
    public static void injectFingerClickOnViewCenter(UinputTouchDevice device, @NonNull View view) {
        final int[] xy = new int[2];
        view.getLocationOnScreen(xy);

        // Inject finger touch event.
        final int x = xy[0] + view.getWidth() / 2;
        final int y = xy[1] + view.getHeight() / 2;

        device.touchDown(x, y).lift();
    }

    /**
     * Inject Stylus ACTION_MOVE events to the screen using the given view's coordinates.
     *
     * @param view  view whose coordinates are used to compute the event location.
     * @param startX the start x coordinates of the stylus event in the view's local coordinates.
     * @param startY the start y coordinates of the stylus event in the view's local coordinates.
     * @param endX the end x coordinates of the stylus event in the view's local coordinates.
     * @param endY the end y coordinates of the stylus event in the view's local coordinates.
     * @param number the number of the motion events injected to the view.
     * @return the injected MotionEvents.
     */
    public static List<MotionEvent> injectStylusMoveEvents(@NonNull View view, int startX,
            int startY, int endX, int endY, int number) {
        int[] xy = new int[2];
        view.getLocationOnScreen(xy);

        final float incrementX = ((float) (endX - startX)) / (number - 1);
        final float incrementY = ((float) (endY - startY)) / (number - 1);

        final List<MotionEvent> injectedEvents = new ArrayList<>(number);
        // Inject stylus ACTION_MOVE
        for (int i = 0; i < number; i++) {
            long time = SystemClock.uptimeMillis();
            float x = startX + incrementX * i + xy[0];
            float y = startY + incrementY * i + xy[1];
            final MotionEvent moveEvent =
                    getMotionEvent(time, time, MotionEvent.ACTION_MOVE, x, y,
                            MotionEvent.TOOL_TYPE_STYLUS);
            injectMotionEvent(moveEvent, true /* sync */);
            injectedEvents.add(moveEvent);
        }
        return injectedEvents;
    }

    /**
     * Inject Stylus ACTION_MOVE events in a multi-device environment tp the screen using the given
     * view's coordinates.
     *
     * @param pointer {@link #injectStylusDownEvent(UinputTouchDevice, View, int, int)} returned
     * Pointer.
     * @param view  view whose coordinates are used to compute the event location.
     * @param startX the start x coordinates of the stylus event in the view's local coordinates.
     * @param startY the start y coordinates of the stylus event in the view's local coordinates.
     * @param endX the end x coordinates of the stylus event in the view's local coordinates.
     * @param endY the end y coordinates of the stylus event in the view's local coordinates.
     * @param number the number of the motion events injected to the view.
     */
    public static void injectStylusMoveEvents(
            @NonNull UinputTouchDevice.Pointer pointer, @NonNull View view, int startX, int startY,
            int endX, int endY, int number) {
        injectStylusMoveEventsInternal(
                pointer, view, startX, startY, endX, endY, number);
    }

    /**
     * Inject Stylus ACTION_MOVE events in a multi-device environment tp the screen using the given
     * view's coordinates.
     *
     * @param pointer {@link #injectStylusDownEvent(UinputTouchDevice, View, int, int)} returned
     * Pointer.
     * @param startX the start x coordinates of the stylus event in the display's coordinates.
     * @param startY the start y coordinates of the stylus event in the display's coordinates.
     * @param endX the end x coordinates of the stylus event in the display's coordinates.
     * @param endY the end y coordinates of the stylus event in the display's coordinates.
     * @param number the number of the motion events injected to the view.
     */
    public static void injectStylusMoveEvents(
            @NonNull UinputTouchDevice.Pointer pointer, int startX, int startY, int endX, int endY,
            int number) {
        injectStylusMoveEventsInternal(
                pointer, null /* view */, startX, startY, endX, endY, number);
    }

    private static void injectStylusMoveEventsInternal(
            @NonNull UinputTouchDevice.Pointer pointer, @Nullable View view, int startX, int startY,
            int endX, int endY, int number) {
        int[] xy = new int[2];
        if (view != null) {
            view.getLocationOnScreen(xy);
        }

        final float incrementX = ((float) (endX - startX)) / (number - 1);
        final float incrementY = ((float) (endY - startY)) / (number - 1);

        // Send stylus ACTION_MOVE.
        for (int i = 0; i < number; i++) {
            int x = (int) (startX + incrementX * i + xy[0]);
            int y = (int) (startY + incrementY * i + xy[1]);
            pointer.moveTo(x, y);
        }
    }

    /**
     * Inject stylus move on the display at the given position defined in the given view's
     * coordinates.
     *
     * @param view view whose coordinates are used to compute the event location.
     * @param x the initial x coordinates of the injected stylus events in the view's
     *          local coordinates.
     * @param y the initial y coordinates of the injected stylus events in the view's
     *          local coordinates.
     */
    public static void injectStylusEvents(@NonNull View view, int x, int y) {
        injectStylusDownEvent(view, x, y);
        // Larger than the touchSlop.
        int endX = x + getTouchSlop(view.getContext()) * 5;
        injectStylusMoveEvents(view, x, y, endX, y, 10);
        injectStylusUpEvent(view, endX, y);

    }

    private static MotionEvent getMotionEvent(long downTime, long eventTime, int action,
            float x, float y, int toolType) {
        return getMotionEvent(downTime, eventTime, action, (int) x, (int) y, 0, toolType);
    }

    private static MotionEvent getMotionEvent(long downTime, long eventTime, int action,
            int x, int y, int displayId, int toolType) {
        // Stylus related properties.
        MotionEvent.PointerProperties[] properties =
                new MotionEvent.PointerProperties[] { new MotionEvent.PointerProperties() };
        properties[0].toolType = toolType;
        properties[0].id = 1;
        MotionEvent.PointerCoords[] coords =
                new MotionEvent.PointerCoords[] { new MotionEvent.PointerCoords() };
        coords[0].x = x;
        coords[0].y = y;
        coords[0].pressure = 1;

        final MotionEvent event = MotionEvent.obtain(downTime, eventTime, action,
                1 /* pointerCount */, properties, coords, 0 /* metaState */,
                0 /* buttonState */, 1 /* xPrecision */, 1 /* yPrecision */, 0 /* deviceId */,
                0 /* edgeFlags */, InputDevice.SOURCE_STYLUS, 0 /* flags */);
        event.setDisplayId(displayId);
        return event;
    }

    private static void injectMotionEvent(MotionEvent event, boolean sync) {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(
                event, sync, false /* waitAnimations */);
    }

    public static void injectAll(List<MotionEvent> events) {
        for (MotionEvent event : events) {
            injectMotionEvent(event, true /* sync */);
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation().syncInputTransactions(false);
    }
    private static int getTouchSlop(Context context) {
        return ViewConfiguration.get(context).getScaledTouchSlop();
    }

    /**
     * Since MotionEvents are batched together based on overall system timings (i.e. vsync), we
     * can't rely on them always showing up batched in the same way. In order to make sure our
     * test results are consistent, we instead split up the batches so they end up in a
     * consistent and reproducible stream.
     *
     * Note, however, that this ignores the problem of resampling, as we still don't know how to
     * distinguish resampled events from real events. Only the latter will be consistent and
     * reproducible.
     *
     * @param event The (potentially) batched MotionEvent
     * @return List of MotionEvents, with each event guaranteed to have zero history size, and
     * should otherwise be equivalent to the original batch MotionEvent.
     */
    public static List<MotionEvent> splitBatchedMotionEvent(MotionEvent event) {
        final List<MotionEvent> events = new ArrayList<>();
        final int historySize = event.getHistorySize();
        final int pointerCount = event.getPointerCount();
        final MotionEvent.PointerProperties[] properties =
                new MotionEvent.PointerProperties[pointerCount];
        final MotionEvent.PointerCoords[] currentCoords =
                new MotionEvent.PointerCoords[pointerCount];
        for (int p = 0; p < pointerCount; p++) {
            properties[p] = new MotionEvent.PointerProperties();
            event.getPointerProperties(p, properties[p]);
            currentCoords[p] = new MotionEvent.PointerCoords();
            event.getPointerCoords(p, currentCoords[p]);
        }
        for (int h = 0; h < historySize; h++) {
            final long eventTime = event.getHistoricalEventTime(h);
            MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointerCount];

            for (int p = 0; p < pointerCount; p++) {
                coords[p] = new MotionEvent.PointerCoords();
                event.getHistoricalPointerCoords(p, h, coords[p]);
            }
            final MotionEvent singleEvent =
                    MotionEvent.obtain(event.getDownTime(), eventTime, event.getAction(),
                            pointerCount, properties, coords,
                            event.getMetaState(), event.getButtonState(),
                            event.getXPrecision(), event.getYPrecision(),
                            event.getDeviceId(), event.getEdgeFlags(),
                            event.getSource(), event.getFlags());
            singleEvent.setActionButton(event.getActionButton());
            events.add(singleEvent);
        }

        final MotionEvent singleEvent =
                MotionEvent.obtain(event.getDownTime(), event.getEventTime(), event.getAction(),
                        pointerCount, properties, currentCoords,
                        event.getMetaState(), event.getButtonState(),
                        event.getXPrecision(), event.getYPrecision(),
                        event.getDeviceId(), event.getEdgeFlags(),
                        event.getSource(), event.getFlags());
        singleEvent.setActionButton(event.getActionButton());
        events.add(singleEvent);
        return events;
    }

    /**
     * Inject Motion Events for swipe up on navbar with stylus.
     * @param activity current test activity.
     * @param toolType of input {@link MotionEvent#getToolType(int)}.
     */
    public static void injectNavBarToHomeGestureEvents(
            @NonNull Activity activity, int toolType) {
        WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();

        var bounds = new Rect(metrics.getBounds());
        bounds.inset(metrics.getWindowInsets().getInsetsIgnoringVisibility(
                displayCutout() | systemBars() | systemGestures()));
        int startY = bounds.bottom;
        int startX = bounds.centerX();
        int endY = bounds.bottom - bounds.height() / 3; // move a third of the screen up
        int endX = startX;
        int steps = 10;

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (UinputTouchDevice device =
                toolType == MotionEvent.TOOL_TYPE_STYLUS
                        ? new UinputStylus(instrumentation, activity.getDisplay())
                        : new UinputTouchScreen(instrumentation, activity.getDisplay())) {
            UinputTouchDevice.Pointer pointer;
            pointer = TestUtils.injectStylusDownEvent(device, startX, startY);
            TestUtils.injectStylusMoveEvents(pointer, startX, startY, endX, endY, steps);
            TestUtils.injectStylusUpEvent(pointer);
        }
    }
}
