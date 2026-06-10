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

package android.server.wm;

import static android.view.Display.DEFAULT_DISPLAY;

import static junit.framework.Assert.assertTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.window.WindowInfosListenerForTest;
import android.window.WindowInfosListenerForTest.DisplayInfo;
import android.window.WindowInfosListenerForTest.WindowInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class CtsWindowInfoUtils {
    private static final int HW_TIMEOUT_MULTIPLIER = SystemProperties.getInt(
            "ro.hw_timeout_multiplier", 1);

    /**
     * Calls the provided predicate each time window information changes.
     *
     * <p>
     * <strong>Note:</strong>The caller must have
     * android.permission.ACCESS_SURFACE_FLINGER permissions.
     * </p>
     *
     * @param predicate The predicate tested each time window infos change.
     * @param timeout   The amount of time to wait for the predicate to be satisfied.
     * @param uiAutomation Pass in a uiAutomation to use. If null is passed in, the default will
     *                     be used. Passing non null is only needed if the test has a custom version
     *                     of uiAutomation since retrieving a uiAutomation could overwrite it.
     * @return True if the provided predicate is true for any invocation before
     * the timeout is reached. False otherwise.
     */
    public static boolean waitForWindowInfos(@NonNull Predicate<List<WindowInfo>> predicate,
            @NonNull Duration timeout, @Nullable UiAutomation uiAutomation)
            throws InterruptedException {
        var latch = new CountDownLatch(1);
        var satisfied = new AtomicBoolean();

        BiConsumer<List<WindowInfo>, List<DisplayInfo>> checkPredicate =
                (windowInfos, displayInfos) -> {
                    if (satisfied.get()) {
                        return;
                    }
                    if (predicate.test(windowInfos)) {
                        satisfied.set(true);
                        latch.countDown();
                    }
                };

        var waitForWindow = new ThrowingRunnable() {
            @Override
            public void run() throws InterruptedException {
                var listener = new WindowInfosListenerForTest();
                try {
                    listener.addWindowInfosListener(checkPredicate);
                    latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } finally {
                    listener.removeWindowInfosListener(checkPredicate);
                }
            }
        };

        if (uiAutomation == null) {
            uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        }
        Set<String> shellPermissions = uiAutomation.getAdoptedShellPermissions();
        if (shellPermissions.isEmpty()) {
            SystemUtil.runWithShellPermissionIdentity(uiAutomation, waitForWindow,
                    Manifest.permission.ACCESS_SURFACE_FLINGER);
        } else if (shellPermissions.contains(Manifest.permission.ACCESS_SURFACE_FLINGER)) {
            waitForWindow.run();
        } else {
            throw new IllegalStateException(
                    "waitForWindowOnTop called with adopted shell permissions that don't include "
                            + "ACCESS_SURFACE_FLINGER");
        }

        return satisfied.get();
    }

    /**
     * Same as {@link #waitForWindowInfos(Predicate, Duration, UiAutomation)}, but passes in
     * a null uiAutomation object. This should be used in most cases unless there's a custom
     * uiAutomation object used in the test.
     *
     * @param predicate The predicate tested each time window infos change.
     * @param timeout   The amount of time to wait for the predicate to be satisfied.
     * @return True if the provided predicate is true for any invocation before
     * the timeout is reached. False otherwise.
     */
    public static boolean waitForWindowInfos(@NonNull Predicate<List<WindowInfo>> predicate,
            @NonNull Duration timeout) throws InterruptedException {
        return waitForWindowInfos(predicate, timeout, null /* uiAutomation */);
    }

    /**
     * Calls the provided predicate each time window information changes if a visible
     * window is found that matches the supplied window token.
     *
     * <p>
     * <strong>Note:</strong>The caller must have the
     * android.permission.ACCESS_SURFACE_FLINGER permissions.
     * </p>
     *
     * @param predicate           The predicate tested each time window infos change.
     * @param timeout             The amount of time to wait for the predicate to be satisfied.
     * @param windowTokenSupplier Supplies the window token for the window to
     *                            call the predicate on. The supplier is called each time window
     *                            info change. If the supplier returns null, the predicate is
     *                            assumed false for the current invocation.
     * @param displayId           The id of the display on which to wait for the window of interest
     * @return True if the provided predicate is true for any invocation before the timeout is
     * reached. False otherwise.
     * @hide
     */
    public static boolean waitForWindowInfo(@NonNull Predicate<WindowInfo> predicate,
            @NonNull Duration timeout, @NonNull Supplier<IBinder> windowTokenSupplier,
            int displayId) throws InterruptedException {
        Predicate<List<WindowInfo>> wrappedPredicate = windowInfos -> {
            IBinder windowToken = windowTokenSupplier.get();
            if (windowToken == null) {
                return false;
            }

            for (var windowInfo : windowInfos) {
                if (!windowInfo.isVisible) {
                    continue;
                }
                // only wait for requested display.
                if (windowInfo.windowToken == windowToken
                        && windowInfo.displayId == displayId) {
                    return predicate.test(windowInfo);
                }
            }

            return false;
        };
        return waitForWindowInfos(wrappedPredicate, timeout);
    }

    /**
     * Waits for the SurfaceView to be invisible.
     */
    public static boolean waitForSurfaceViewInvisible(@NonNull SurfaceView view)
            throws InterruptedException {
        Predicate<List<WindowInfo>> wrappedPredicate = windowInfos -> {
            for (var windowInfo : windowInfos) {
                if (windowInfo.isVisible) {
                    continue;
                }
                if (windowInfo.name.startsWith(getHashCode(view))) {
                    return false;
                }
            }

            return true;
        };

        return waitForWindowInfos(wrappedPredicate, Duration.ofSeconds(HW_TIMEOUT_MULTIPLIER * 5L));
    }

    /**
     * Waits for the SurfaceView to be present.
     */
    public static boolean waitForSurfaceViewVisible(@NonNull SurfaceView view)
            throws InterruptedException {
        // Wait until view is attached to a display
        PollingCheck.waitFor(() -> view.getDisplay() != null, "View not attached to a display");

        Predicate<List<WindowInfo>> wrappedPredicate = windowInfos -> {
            for (var windowInfo : windowInfos) {
                if (!windowInfo.isVisible) {
                    continue;
                }
                if (windowInfo.name.startsWith(getHashCode(view))
                        && windowInfo.displayId == view.getDisplay().getDisplayId()) {
                    return true;
                }
            }

            return false;
        };

        return waitForWindowInfos(wrappedPredicate, Duration.ofSeconds(HW_TIMEOUT_MULTIPLIER * 5L));
    }

    /**
     * Waits for a window to become visible.
     *
     * @param view The view of the window to wait for.
     * @return {@code true} if the window becomes visible within the timeout period, {@code false}
     *         otherwise.
     * @throws InterruptedException If the thread is interrupted while waiting for the window
     *         information.
     */
    public static boolean waitForWindowVisible(@NonNull View view) throws InterruptedException {
        // Wait until view is attached to a display
        PollingCheck.waitFor(() -> view.getDisplay() != null, "View not attached to a display");
        return waitForWindowInfo(windowInfo -> true, Duration.ofSeconds(HW_TIMEOUT_MULTIPLIER * 5L),
                view::getWindowToken, view.getDisplay().getDisplayId());
    }

    /**
     * Waits for a window to become visible.
     *
     * @param windowToken The token of the window to wait for.
     * @return {@code true} if the window becomes visible within the timeout period, {@code false}
     *         otherwise.
     * @throws InterruptedException If the thread is interrupted while waiting for the window
     *         information.
     */
    public static boolean waitForWindowVisible(@NonNull IBinder windowToken)
            throws InterruptedException {
        return waitForWindowVisible(windowToken, DEFAULT_DISPLAY);
    }

    /**
     * Waits for a window to become visible.
     *
     * @param windowTokenSupplier Supplies the window token for the window to wait on. The
     *                            supplier is called each time window infos change. If the
     *                            supplier returns null, the window is assumed not visible
     *                            yet.
     * @return {@code true} if the window becomes visible within the timeout period, {@code false}
     *         otherwise.
     * @throws InterruptedException If the thread is interrupted while waiting for the window
     *         information.
     */
    public static boolean waitForWindowVisible(@NonNull Supplier<IBinder> windowTokenSupplier)
            throws InterruptedException {
        return waitForWindowInfo(windowInfo -> true, Duration.ofSeconds(HW_TIMEOUT_MULTIPLIER * 5L),
                windowTokenSupplier, DEFAULT_DISPLAY);
    }

    /**
     * Waits for a window to become visible.
     *
     * @param windowToken The token of the window to wait for.
     * @param displayId The ID of the display on which to check for the window's visibility.
     * @return {@code true} if the window becomes visible within the timeout period, {@code false}
     *         otherwise.
     * @throws InterruptedException If the thread is interrupted while waiting for the window
     *         information.
     */
    public static boolean waitForWindowVisible(@NonNull IBinder windowToken, int displayId)
            throws InterruptedException {
        return waitForWindowInfo(windowInfo -> true, Duration.ofSeconds(HW_TIMEOUT_MULTIPLIER * 5L),
                () -> windowToken, displayId);
    }

    /**
     * Waits for a window to become invisible.
     *
     * @param windowTokenSupplier Supplies the window token for the window to wait on.
     * @param timeout The amount of time to wait for the window to be invisible.
     * @return {@code true} if the window becomes invisible within the timeout period, {@code false}
     *         otherwise.
     * @throws InterruptedException If the thread is interrupted while waiting for the window
     *         information.
     */
    public static boolean waitForWindowInvisible(@NonNull Supplier<IBinder> windowTokenSupplier,
                                                 @NonNull Duration timeout)
            throws InterruptedException {
        Predicate<List<WindowInfo>> wrappedPredicate = windowInfos -> {
            IBinder windowToken = windowTokenSupplier.get();
            if (windowToken == null) {
                return false;
            }

            for (var windowInfo : windowInfos) {
                if (windowInfo.isVisible
                        && windowInfo.windowToken == windowToken) {
                    return false;
                }
            }

            return true;
        };
        return waitForWindowInfos(wrappedPredicate, timeout);
    }

    /**
     * Waits for a window to become invisible.
     *
     * @param name A name associated with the target window we are waiting for.
     * @param timeout The amount of time to wait for the window to be invisible.
     * @return {@code true} if the window becomes invisible within the timeout period, {@code false}
     *     otherwise.
     * @throws InterruptedException If the thread is interrupted while waiting for the window
     *     information.
     */
    public static boolean waitForWindowInvisible(@NonNull String name, @NonNull Duration timeout)
            throws InterruptedException {
        return CtsWindowInfoUtils.waitForWindowInfos(
                windows -> windows.stream().noneMatch(window -> window.name.contains(name)),
                timeout);
    }

    /**
     * Calls {@link CtsWindowInfoUtils#waitForWindowOnTop(Duration, Supplier)}. Adopts
     * required permissions and waits at least five seconds before timing out.
     *
     * @param window The window to wait on.
     * @return True if the window satisfies the visibility requirements before the timeout is
     * reached. False otherwise.
     */
    public static boolean waitForWindowOnTop(@NonNull Window window) throws InterruptedException {
        return waitForWindowOnTop(Duration.ofSeconds(HW_TIMEOUT_MULTIPLIER * 5L),
                () -> window.getDecorView().getWindowToken());
    }

    /**
     * Waits until the window specified by the predicate is present, not occluded, and hasn't
     * had geometry changes for 200ms.
     *
     * The window is considered occluded if any part of another window is above it, excluding
     * trusted overlays.
     *
     * <p>
     * <strong>Note:</strong>If the caller has any adopted shell permissions, they must include
     * android.permission.ACCESS_SURFACE_FLINGER.
     * </p>
     *
     * @param timeout             The amount of time to wait for the window to be visible.
     * @param predicate           A predicate identifying the target window we are waiting for,
     *                            will be tested each time window infos change.
     * @return True if the window satisfies the visibility requirements before the timeout is
     * reached. False otherwise.
     */
    public static boolean waitForWindowOnTop(@NonNull Duration timeout,
                                             @NonNull Predicate<WindowInfo> predicate)
            throws InterruptedException {
        return waitForNthWindowFromTop(timeout, predicate, 0);
    }

    /**
     * Waits until the window specified by {@code predicate} is present, at the expected level
     * of the composition hierarchy, and hasn't had geometry changes for 200ms.
     *
     * @see #waitForNthWindowFromTop(Duration, Predicate, int, boolean)
     */
    public static boolean waitForNthWindowFromTop(@NonNull Duration timeout,
                                                  @NonNull Predicate<WindowInfo> predicate,
                                                  int expectedOrder)
            throws InterruptedException {
        return waitForNthWindowFromTop(timeout, predicate, expectedOrder, /* stabilize= */ true);
    }

    /**
     * Waits until the window specified by {@code predicate} is present, at the expected level
     * of the composition hierarchy.
     *
     * The window is considered occluded if any part of another window is above it, excluding
     * trusted overlays and bbq.
     *
     * <p>
     * <strong>Note:</strong>If the caller has any adopted shell permissions, they must include
     * android.permission.ACCESS_SURFACE_FLINGER.
     * </p>
     *
     * @param timeout       The amount of time to wait for the window to be visible.
     * @param predicate     A predicate identifying the target window we are waiting, will be
     *                      tested each time window infos change.
     * @param expectedOrder The expected order of the surface control we are looking
     *                      for.
     * @param stabilize     Whether to wait until the window geometry is stable before
     *                      returning. If true, it waits until the window geometry is stable for
     *                      200ms.
     * @return True if the window satisfies the visibility requirements before the timeout is
     * reached. False otherwise.
     */
    private static boolean waitForNthWindowFromTop(@NonNull Duration timeout,
                                                  @NonNull Predicate<WindowInfo> predicate,
                                                  int expectedOrder, boolean stabilize)
            throws InterruptedException {
        var latch = new CountDownLatch(1);
        var satisfied = new AtomicBoolean();

        var windowNotOccluded = new BiConsumer<List<WindowInfo>, List<DisplayInfo>>() {
            private Timer mTimer = new Timer();
            private TimerTask mTask = null;
            private Rect mPreviousBounds = new Rect(0, 0, -1, -1);

            private void resetState() {
                if (mTask != null) {
                    mTask.cancel();
                    mTask = null;
                }
                mPreviousBounds.set(0, 0, -1, -1);
            }

            @Override
            public void accept(List<WindowInfo> windowInfos, List<DisplayInfo> displayInfos) {
                if (satisfied.get()) {
                    return;
                }

                WindowInfo targetWindowInfo = null;
                ArrayList<WindowInfo> aboveWindowInfos = new ArrayList<>();
                for (var windowInfo : windowInfos) {
                    if (predicate.test(windowInfo)) {
                        targetWindowInfo = windowInfo;
                        break;
                    }
                    if (windowInfo.isTrustedOverlay || !windowInfo.isVisible) {
                        continue;
                    }
                    aboveWindowInfos.add(windowInfo);
                }

                if (targetWindowInfo == null) {
                    // The window isn't present. If we have an active timer, we need to cancel it
                    // as it's possible the window was previously present and has since disappeared.
                    resetState();
                    return;
                }

                int currentOrder = 0;
                for (var windowInfo : aboveWindowInfos) {
                    if (targetWindowInfo.displayId == windowInfo.displayId
                            && Rect.intersects(targetWindowInfo.bounds, windowInfo.bounds)) {
                        if (currentOrder < expectedOrder) {
                            currentOrder++;
                            continue;
                        }
                        // The window is occluded. If we have an active timer, we need to cancel it
                        // as it's possible the window was previously not occluded and now is
                        // occluded.
                        resetState();
                        return;
                    }
                }
                if (currentOrder != expectedOrder) {
                    resetState();
                    return;
                }

                if (targetWindowInfo.bounds.equals(mPreviousBounds)) {
                    // The window matches previously found bounds. Let the active timer continue.
                    return;
                }

                // The window is present and not occluded but has different bounds than
                // previously seen or this is the first time we've detected the window. If
                // there's an active timer, cancel it. Schedule a task to toggle the latch in 200ms.
                resetState();
                mPreviousBounds.set(targetWindowInfo.bounds);
                mTask = new TimerTask() {
                    @Override
                    public void run() {
                        satisfied.set(true);
                        latch.countDown();
                    }
                };
                mTimer.schedule(mTask, stabilize ? 200L * HW_TIMEOUT_MULTIPLIER : 0L);
            }
        };

        runWithSurfaceFlingerPermission(() -> {
            var listener = new WindowInfosListenerForTest();
            try {
                listener.addWindowInfosListener(windowNotOccluded);
                latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } finally {
                listener.removeWindowInfosListener(windowNotOccluded);
            }
        });

        return satisfied.get();
    }

    private interface InterruptableRunnable {
        void run() throws InterruptedException;
    };

    private static void runWithSurfaceFlingerPermission(@NonNull InterruptableRunnable runnable)
            throws InterruptedException {
        Set<String> shellPermissions =
                InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .getAdoptedShellPermissions();
        if (shellPermissions.isEmpty()) {
            SystemUtil.runWithShellPermissionIdentity(runnable::run,
                    Manifest.permission.ACCESS_SURFACE_FLINGER);
        } else if (shellPermissions.contains(Manifest.permission.ACCESS_SURFACE_FLINGER)) {
            runnable.run();
        } else {
            throw new IllegalStateException(
                    "waitForWindowOnTop called with adopted shell permissions that don't include "
                            + "ACCESS_SURFACE_FLINGER");
        }
    }

    /**
     * Waits until the window specified by windowTokenSupplier is present, not occluded, and hasn't
     * had geometry changes for 200ms.
     *
     * The window is considered occluded if any part of another window is above it, excluding
     * trusted overlays.
     *
     * <p>
     * <strong>Note:</strong>If the caller has any adopted shell permissions, they must include
     * android.permission.ACCESS_SURFACE_FLINGER.
     * </p>
     *
     * @param timeout             The amount of time to wait for the window to be visible.
     * @param windowTokenSupplier Supplies the window token for the window to wait on. The
     *                            supplier is called each time window infos change. If the
     *                            supplier returns null, the window is assumed not visible
     *                            yet.
     * @return True if the window satisfies the visibility requirements before the timeout is
     * reached. False otherwise.
     */
    public static boolean waitForWindowOnTop(@NonNull Duration timeout,
            @NonNull Supplier<IBinder> windowTokenSupplier)
            throws InterruptedException {
        return waitForWindowOnTop(timeout, windowInfo -> {
            IBinder windowToken = windowTokenSupplier.get();
            return windowToken != null && windowInfo.windowToken == windowToken;
        });
    }

    /**
     * Waits until the given window is present and not occluded.
     *
     * Same as {@link #waitForWindowOnTop(Window)}, but doesn't wait for the window
     * geometry to stabilize.
     */
    public static boolean waitForWindowOnTopImmediate(@NonNull Window window)
            throws InterruptedException {
        return waitForNthWindowFromTop(Duration.ofSeconds(HW_TIMEOUT_MULTIPLIER * 5L),
            windowInfo -> {
                IBinder windowToken = window.getDecorView().getWindowToken();
                return windowToken != null && windowInfo.windowToken == windowToken;
            }, /* expectedOrder= */ 0, /* stabilize= */ false);
    }

    /**
     * Waits until the window specified by {@code predicate} is present, at the expected level
     * of the composition hierarchy, and hasn't had geometry changes for 200ms.
     *
     * The window is considered occluded if any part of another window is above it, excluding
     * trusted overlays and bbq.
     *
     * <p>
     * <strong>Note:</strong>If the caller has any adopted shell permissions, they must include
     * android.permission.ACCESS_SURFACE_FLINGER.
     * </p>
     *
     * @param timeout             The amount of time to wait for the window to be visible.
     * @param windowTokenSupplier Supplies the window token for the window to wait on. The
     *                            supplier is called each time window infos change. If the
     *                            supplier returns null, the window is assumed not visible
     *                            yet.
     * @param expectedOrder       The expected order of the surface control we are looking
     *                            for.
     * @return True if the window satisfies the visibility requirements before the timeout is
     * reached. False otherwise.
     */
    public static boolean waitForNthWindowFromTop(@NonNull Duration timeout,
                                                  @NonNull Supplier<IBinder> windowTokenSupplier,
                                                  int expectedOrder)
            throws InterruptedException {
        return waitForNthWindowFromTop(timeout, windowInfo -> {
            IBinder windowToken = windowTokenSupplier.get();
            return windowToken != null && windowInfo.windowToken == windowToken;
        }, expectedOrder);
    }

    /**
     * Waits until the set of windows and their geometries are unchanged for 200ms.
     *
     * <p>
     * <strong>Note:</strong>If the caller has any adopted shell permissions, they must include
     * android.permission.ACCESS_SURFACE_FLINGER.
     * </p>
     *
     * @param timeout The amount of time to wait for the window to be visible.
     * @return True if window geometry becomes stable before the timeout is reached. False
     * otherwise.
     */
    public static boolean waitForStableWindowGeometry(@NonNull Duration timeout)
            throws InterruptedException {
        var latch = new CountDownLatch(1);
        var satisfied = new AtomicBoolean();

        var timer = new Timer();
        TimerTask[] task = {null};

        var previousBounds = new HashMap<IBinder, Rect>();
        var currentBounds = new HashMap<IBinder, Rect>();

        BiConsumer<List<WindowInfo>, List<DisplayInfo>> consumer =
                (windowInfos, displayInfos) -> {
                    if (satisfied.get()) {
                        return;
                    }

                    currentBounds.clear();
                    for (var windowInfo : windowInfos) {
                        currentBounds.put(windowInfo.windowToken, windowInfo.bounds);
                    }

                    if (currentBounds.equals(previousBounds)) {
                        // No changes detected. Let the previously scheduled timer task continue.
                        return;
                    }

                    previousBounds.clear();
                    previousBounds.putAll(currentBounds);

                    // Something has changed. Cancel the previous timer task and schedule a new task
                    // to countdown the latch in 200ms.
                    if (task[0] != null) {
                        task[0].cancel();
                    }
                    task[0] =
                            new TimerTask() {
                                @Override
                                public void run() {
                                    satisfied.set(true);
                                    latch.countDown();
                                }
                            };
                    timer.schedule(task[0], 200L * HW_TIMEOUT_MULTIPLIER);
                };

        runWithSurfaceFlingerPermission(() -> {
            var listener = new WindowInfosListenerForTest();
            try {
                listener.addWindowInfosListener(consumer);
                latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } finally {
                listener.removeWindowInfosListener(consumer);
            }
        });

        return satisfied.get();
    }

    /**
     * Tap on the center coordinates of the specified window and sends back the coordinates tapped
     * </p>
     *
     * @param instrumentation     Instrumentation object to use for tap.
     * @param windowTokenSupplier Supplies the window token for the window to wait on. The supplier
     *                            is called each time window infos change. If the supplier returns
     *                            null, the window is assumed not visible yet.
     * @param outCoords           If non null, the tapped coordinates will be set in the object.
     * @return true if successfully tapped on the coordinates, false otherwise.
     * @throws InterruptedException if failed to wait for WindowInfo
     */
    public static boolean tapOnWindowCenter(Instrumentation instrumentation,
            @NonNull Supplier<IBinder> windowTokenSupplier, @Nullable Point outCoords)
            throws InterruptedException {
        return tapOnWindowCenter(instrumentation, windowTokenSupplier, outCoords, DEFAULT_DISPLAY);
    }

    /**
     * Tap on the center coordinates of the specified window and sends back the coordinates tapped
     * </p>
     *
     * @param instrumentation     Instrumentation object to use for tap.
     * @param windowTokenSupplier Supplies the window token for the window to wait on. The supplier
     *                            is called each time window infos change. If the supplier returns
     *                            null, the window is assumed not visible yet.
     * @param outCoords           If non null, the tapped coordinates will be set in the object.
     * @param displayId           The ID of the display on which to tap the window center.
     * @return true if successfully tapped on the coordinates, false otherwise.
     * @throws InterruptedException if failed to wait for WindowInfo
     */
    public static boolean tapOnWindowCenter(Instrumentation instrumentation,
            @NonNull Supplier<IBinder> windowTokenSupplier, @Nullable Point outCoords,
            int displayId) throws InterruptedException {
        Rect bounds = getWindowBoundsInDisplaySpace(windowTokenSupplier, displayId);
        if (bounds == null) {
            return false;
        }

        final Point coord = new Point(bounds.left + bounds.width() / 2,
                bounds.top + bounds.height() / 2);
        sendTap(instrumentation, coord);
        if (outCoords != null) {
            outCoords.set(coord.x, coord.y);
        }
        return true;
    }

    /**
     * Tap on the coordinates of the specified window, offset by the value passed in.
     * </p>
     *
     * @param instrumentation     Instrumentation object to use for tap.
     * @param windowTokenSupplier Supplies the window token for the window to wait on. The supplier
     *                            is called each time window infos change. If the supplier returns
     *                            null, the window is assumed not visible yet.
     * @param offset              The offset from 0,0 of the window to tap on. If null, it will be
     *                            ignored and 0,0 will be tapped.
     * @return true if successfully tapped on the coordinates, false otherwise.
     * @throws InterruptedException if failed to wait for WindowInfo
     */
    public static boolean tapOnWindow(Instrumentation instrumentation,
            @NonNull Supplier<IBinder> windowTokenSupplier, @Nullable Point offset)
            throws InterruptedException {
        return tapOnWindow(instrumentation, windowTokenSupplier, offset, DEFAULT_DISPLAY);
    }

    /**
     * Tap on the coordinates of the specified window, offset by the value passed in.
     * </p>
     *
     * @param instrumentation     Instrumentation object to use for tap.
     * @param windowTokenSupplier Supplies the window token for the window to wait on. The supplier
     *                            is called each time window infos change. If the supplier returns
     *                            null, the window is assumed not visible yet.
     * @param offset              The offset from 0,0 of the window to tap on. If null, it will be
     *                            ignored and 0,0 will be tapped.
     * @param displayId           The ID of the display on which to tap the window.
     * @return true if successfully tapped on the coordinates, false otherwise.
     * @throws InterruptedException if failed to wait for WindowInfo
     */
    public static boolean tapOnWindow(Instrumentation instrumentation,
            @NonNull Supplier<IBinder> windowTokenSupplier, @Nullable Point offset,
            int displayId) throws InterruptedException {
        Rect bounds = getWindowBoundsInDisplaySpace(windowTokenSupplier, displayId);
        if (bounds == null) {
            return false;
        }

        final Point coord = new Point(bounds.left + (offset != null ? offset.x : 0),
                bounds.top + (offset != null ? offset.y : 0));
        sendTap(instrumentation, coord);
        return true;
    }

    public static Rect getWindowBoundsInWindowSpace(@NonNull Supplier<IBinder> windowTokenSupplier)
            throws InterruptedException {
        return getWindowBoundsInWindowSpace(windowTokenSupplier, DEFAULT_DISPLAY);
    }

    /**
     * Get the bounds of a window in window space.
     *
     * @param windowTokenSupplier A supplier that provides the window token.
     * @param displayId The ID of the display for which the window bounds are to be retrieved.
     * @return A {@link Rect} representing the bounds of the window in window space,
     *         or null if the window information is not available within the timeout period.
     * @throws InterruptedException If the thread is interrupted while waiting for the window
     *         information.
     */
    public static Rect getWindowBoundsInWindowSpace(@NonNull Supplier<IBinder> windowTokenSupplier,
            int displayId) throws InterruptedException {
        Rect bounds = new Rect();
        Predicate<WindowInfo> predicate = windowInfo -> {
            if (!windowInfo.bounds.isEmpty()) {
                if (!windowInfo.transform.isIdentity()) {
                    RectF rectF = new RectF(windowInfo.bounds);
                    windowInfo.transform.mapRect(rectF);
                    bounds.set((int) rectF.left, (int) rectF.top, (int) rectF.right,
                            (int) rectF.bottom);
                } else {
                    bounds.set(windowInfo.bounds);
                }
                return true;
            }

            return false;
        };

        if (!waitForWindowInfo(predicate, Duration.ofSeconds(5L * HW_TIMEOUT_MULTIPLIER),
                windowTokenSupplier, displayId)) {
            return null;
        }
        return bounds;
    }

    public static Rect getWindowBoundsInDisplaySpace(@NonNull Supplier<IBinder> windowTokenSupplier)
            throws InterruptedException {
        return getWindowBoundsInDisplaySpace(windowTokenSupplier, DEFAULT_DISPLAY);
    }

    /**
     * Get the bounds of a window in display space for a specified display.
     *
     * @param windowTokenSupplier A supplier that provides the window token.
     * @param displayId The ID of the display for which the window bounds are to be retrieved.
     * @return A {@link Rect} representing the bounds of the window in display space, or null
     *         if the window information is not available within the timeout period.
     * @throws InterruptedException If the thread is interrupted while waiting for the
     *         window information.
     */
    public static Rect getWindowBoundsInDisplaySpace(@NonNull Supplier<IBinder> windowTokenSupplier,
             int displayId) throws InterruptedException {
        Rect bounds = new Rect();
        Predicate<WindowInfo> predicate = windowInfo -> {
            if (!windowInfo.bounds.isEmpty()) {
                bounds.set(windowInfo.bounds);
                return true;
            }

            return false;
        };

        if (!waitForWindowInfo(predicate, Duration.ofSeconds(5L * HW_TIMEOUT_MULTIPLIER),
                windowTokenSupplier, displayId)) {
            return null;
        }
        return bounds;
    }

    /**
     * Get the center coordinates of the specified window
     *
     * @param windowTokenSupplier Supplies the window token for the window to wait on. The supplier
     *                            is called each time window infos change. If the supplier returns
     *                            null, the window is assumed not visible yet.
     * @param displayId The ID of the display on which the window is located.
     * @return Point of the window center
     * @throws InterruptedException if failed to wait for WindowInfo
     */
    public static Point getWindowCenter(@NonNull Supplier<IBinder> windowTokenSupplier,
            int displayId) throws InterruptedException {
        final Rect bounds = getWindowBoundsInDisplaySpace(windowTokenSupplier, displayId);
        if (bounds == null) {
            throw new IllegalArgumentException("Could not get the bounds for window");
        }
        return new Point(bounds.left + bounds.width() / 2, bounds.top + bounds.height() / 2);
    }

    /**
     * Sends tap to the specified coordinates.
     * </p>
     *
     * @param instrumentation    Instrumentation object to use for tap.
     * @param coord              The coordinates to tap on in display space.
     * @throws InterruptedException if failed to wait for WindowInfo
     */
    public static void sendTap(Instrumentation instrumentation, Point coord) {
        // Get anchor coordinates on the screen
        final long downTime = SystemClock.uptimeMillis();

        CtsTouchUtils ctsTouchUtils = new CtsTouchUtils(instrumentation.getTargetContext());
        ctsTouchUtils.injectDownEvent(instrumentation, downTime, coord.x, coord.y,
                /* eventInjectionListener= */ null);
        ctsTouchUtils.injectUpEvent(instrumentation, downTime, false, coord.x, coord.y, null);

        instrumentation.waitForIdleSync();
    }

    public static boolean waitForWindowFocus(final View view, boolean hasWindowFocus) {
        final CountDownLatch latch = new CountDownLatch(1);

        view.getHandler().post(() -> {
            if (view.hasWindowFocus() == hasWindowFocus) {
                latch.countDown();
                return;
            }
            view.getViewTreeObserver().addOnWindowFocusChangeListener(
                    new ViewTreeObserver.OnWindowFocusChangeListener() {
                        @Override
                        public void onWindowFocusChanged(boolean newFocusState) {
                            if (hasWindowFocus == newFocusState) {
                                view.getViewTreeObserver()
                                        .removeOnWindowFocusChangeListener(this);
                                latch.countDown();
                            }
                        }
                    });

            view.invalidate();
        });

        try {
            if (!latch.await(HW_TIMEOUT_MULTIPLIER * 10L, TimeUnit.SECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    public static void dumpWindowsOnScreen(String tag, String message)
            throws InterruptedException {
        waitForWindowInfos(windowInfos -> {
            if (windowInfos.isEmpty()) {
                return false;
            }
            Log.d(tag, "Dumping windows on screen: " + message);
            for (var windowInfo : windowInfos) {
                Log.d(tag, "     " + windowInfo);
            }
            return true;
        }, Duration.ofSeconds(5L * HW_TIMEOUT_MULTIPLIER));
    }

    /**
     * Assert the condition and dump the window states if the condition fails.
     */
    public static void assertAndDumpWindowState(String tag, String message, boolean condition)
            throws InterruptedException {
        if (!condition) {
            dumpWindowsOnScreen(tag, message);
        }

        assertTrue(message, condition);
    }

    /**
     * Get the current window and display state.
     */
    public static Pair<List<WindowInfo>, List<DisplayInfo>> getWindowAndDisplayState()
            throws InterruptedException {
        var consumer =
                new BiConsumer<List<WindowInfo>, List<DisplayInfo>>() {
                    private CountDownLatch mLatch = new CountDownLatch(1);
                    private boolean mComplete = false;

                    List<WindowInfo> mWindowInfos;
                    List<DisplayInfo> mDisplayInfos;

                    @Override
                    public void accept(List<WindowInfo> windows, List<DisplayInfo> displays) {
                        if (mComplete || windows.isEmpty() || displays.isEmpty()) {
                            return;
                        }
                        mComplete = true;
                        mWindowInfos = windows;
                        mDisplayInfos = displays;
                        mLatch.countDown();
                    }

                    void await() throws InterruptedException {
                        mLatch.await(5L * HW_TIMEOUT_MULTIPLIER, TimeUnit.SECONDS);
                    }

                    Pair<List<WindowInfo>, List<DisplayInfo>> getState() {
                        return new Pair(mWindowInfos, mDisplayInfos);
                    }
                };

        var waitForState =
                new ThrowingRunnable() {
                    @Override
                    public void run() throws InterruptedException {
                        var listener = new WindowInfosListenerForTest();
                        try {
                            listener.addWindowInfosListener(consumer);
                            consumer.await();
                        } finally {
                            listener.removeWindowInfosListener(consumer);
                        }
                    }
                };

        var uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        Set<String> shellPermissions = uiAutomation.getAdoptedShellPermissions();
        if (shellPermissions.isEmpty()) {
            SystemUtil.runWithShellPermissionIdentity(
                    uiAutomation, waitForState, Manifest.permission.ACCESS_SURFACE_FLINGER);
        } else if (shellPermissions.contains(Manifest.permission.ACCESS_SURFACE_FLINGER)) {
            waitForState.run();
        } else {
            throw new IllegalStateException(
                    "getWindowAndDisplayState called with adopted shell permissions that don't"
                            + " include ACCESS_SURFACE_FLINGER");
        }

        return consumer.getState();
    }

    private static String getHashCode(Object obj) {
        return Integer.toHexString(System.identityHashCode(obj));
    }
}
