package com.android.compatibility.common.util;
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


import static org.junit.Assert.assertNotNull;

import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class UiAutomatorUtils2 {
    private UiAutomatorUtils2() {}

    private static final String LOG_TAG = "UiAutomatorUtils2";

    // A flaky test can enable logging of ui dump when searching the item on screen.
    public static final boolean DEBUG_UI_DUMP = false;

    /** Default swipe deadzone percentage. See {@link UiScrollable}. */
    private static final double DEFAULT_SWIPE_DEADZONE_PCT_TV       = 0.1f;
    private static final double DEFAULT_SWIPE_DEADZONE_PCT_ALL      = 0.25f;
    /**
     * On Wear, some cts tests like CtsPermissionUiTestCases that run on
     * low performance device. Keep 0.05 to have better matching.
     */
    private static final double DEFAULT_SWIPE_DEADZONE_PCT_WEAR     = 0.05f;

    /** Minimum view height accepted (before needing to scroll more). */
    private static final float MIN_VIEW_HEIGHT_DP = 8;

    private static Pattern sCollapsingToolbarResPattern =
            Pattern.compile(".*:id/collapsing_toolbar");

    private static final UserHelper USER_HELPER = new UserHelper();

    public static UiDevice getUiDevice() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    private static int convertDpToPx(float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                ApplicationProvider.getApplicationContext().getResources().getDisplayMetrics()));
    }

    private static double getSwipeDeadZonePct() {
        if (FeatureUtil.isTV()) {
            return DEFAULT_SWIPE_DEADZONE_PCT_TV;
        } else if (FeatureUtil.isWatch()) {
            return DEFAULT_SWIPE_DEADZONE_PCT_WEAR;
        } else {
            return DEFAULT_SWIPE_DEADZONE_PCT_ALL;
        }
    }

    public static void waitUntilObjectGone(BySelector selector) {
        waitUntilObjectGone(selector, 20_000);
    }

    public static void waitUntilObjectGone(BySelector selector, long timeoutMs) {
        try {
            if (getUiDevice().wait(Until.gone(selector), timeoutMs)) {
                return;
            }
        } catch (StaleObjectException exception) {
            // UiDevice.wait() may cause StaleObjectException if the {@link View} attached to
            // UiObject2 is no longer in the view tree.
            return;
        }

        throw new RuntimeException("view " + selector + " is still visible after " + timeoutMs
                + "ms");
    }

    // Will wrap any asserting exceptions thrown by the parameter with a UI dump
    public static void assertWithUiDump(ThrowingRunnable assertion) {
        ExceptionUtils.wrappingExceptions(UiDumpUtils::wrapWithUiDump, assertion);
    }

    public static UiObject2 waitFindObject(BySelector selector) throws UiObjectNotFoundException {
        return waitFindObject(selector, 20_000);
    }

    public static UiObject2 waitFindObject(BySelector selector, long timeoutMs)
            throws UiObjectNotFoundException {
        final UiObject2 view = waitFindObjectOrNull(selector, timeoutMs);
        ExceptionUtils.wrappingExceptions(UiDumpUtils::wrapWithUiDump, () -> {
            assertNotNull("View not found after waiting for " + timeoutMs + "ms: " + selector,
                    view);
        });
        return view;
    }

    public static UiObject2 waitFindObjectOrNull(BySelector selector)
            throws UiObjectNotFoundException {
        return waitFindObjectOrNull(selector, 20_000);
    }

    public static UiObject2 waitFindObjectOrNull(BySelector selector, long timeoutMs)
            throws UiObjectNotFoundException {
        // If the target user is a visible background user, find the object on the main display
        // assigned to the user. This is because UiScrollable does not support multi-display,
        // so any scroll actions from UiScrollable will be performed on the default display,
        // regardless of which display the test is running on.
        // This is specifically for the tests that support secondary_user_on_secondary_display.
        if (USER_HELPER.isVisibleBackgroundUser()) {
            return waitFindObjectOrNullOnDisplay(
                    selector, timeoutMs, USER_HELPER.getMainDisplayId());
        }
        UiObject2 view = null;
        long start = System.currentTimeMillis();

        boolean isAtEnd = false;
        boolean wasScrolledUpAlready = false;
        boolean scrolledPastCollapsibleToolbar = false;

        final int minViewHeightPx = convertDpToPx(MIN_VIEW_HEIGHT_DP);

        int viewHeight = -1;
        while (view == null && start + timeoutMs > System.currentTimeMillis()) {
            try {
                view = getUiDevice().wait(Until.findObject(selector), 1000);
                if (view != null) {
                    viewHeight = view.getVisibleBounds().height();
                }
            } catch (StaleObjectException exception) {
                // UiDevice.wait() or view.getVisibleBounds() may cause StaleObjectException if
                // the {@link View} attached to UiObject2 is no longer in the view tree.
                Log.v(LOG_TAG, "UiObject2 view is no longer in the view tree.", exception);
                view = null;
                getUiDevice().waitForIdle();
                continue;
            }
            if (DEBUG_UI_DUMP) {
                StringBuilder sb = new StringBuilder();
                UiDumpUtils.dumpNodes(sb);
                Log.d(LOG_TAG, selector + " " + (view == null ? "not found" : "found")
                        + " on the screen. \n" + sb);
            }
            if (view == null || viewHeight < minViewHeightPx) {
                if (view == null) {
                    Log.v(LOG_TAG, selector + " not found on the screen, try scrolling.");
                }
                final double deadZone = getSwipeDeadZonePct();
                UiScrollable scrollable = new UiScrollable(new UiSelector().scrollable(true));
                scrollable.setSwipeDeadZonePercentage(deadZone);
                if (scrollable.exists()) {
                    if (!scrolledPastCollapsibleToolbar) {
                        scrollPastCollapsibleToolbar(scrollable, deadZone);
                        scrolledPastCollapsibleToolbar = true;
                        continue;
                    }
                    if (isAtEnd) {
                        if (wasScrolledUpAlready) {
                            return null;
                        }
                        scrollable.scrollToBeginning(Integer.MAX_VALUE);
                        isAtEnd = false;
                        wasScrolledUpAlready = true;
                        scrolledPastCollapsibleToolbar = false;
                    } else {
                        Rect boundsBeforeScroll = scrollable.getBounds();
                        boolean scrollAtStartOrEnd;
                        boolean isWearCompose = FeatureUtil.isWatch() && Objects.equals(
                                scrollable.getPackageName(),
                                InstrumentationRegistry.getInstrumentation().getContext()
                                        .getPackageManager().getPermissionControllerPackageName());
                        if (isWearCompose) {
                            // TODO(b/306483780): Removed the condition once the scrollForward is
                            //  fixed.
                            if (!wasScrolledUpAlready) {
                                // TODO(b/306483780): scrollForward() always returns false. Thus
                                // `isAtEnd` will never be false for Wear Compose, because
                                // `scrollAtStartOrEnd` is set to false, and the value of `isAtEnd`
                                // is an && combination of that value. To avoid skipping Views
                                // that exist above the start-point of the search, we will first
                                // scroll up before doing a downward search and scroll.
                                scrollable.scrollToBeginning(Integer.MAX_VALUE);
                                wasScrolledUpAlready = true;
                                continue;
                            }
                            scrollable.scrollForward();
                            scrollAtStartOrEnd = false;
                        } else {
                            Log.v(LOG_TAG, "Scrolling boundsBeforeScroll: " + boundsBeforeScroll);
                            scrollAtStartOrEnd = !scrollable.scrollForward();
                        }
                        // The scrollable view may no longer be scrollable after the toolbar is
                        // collapsed.
                        if (scrollable.exists()) {
                            Rect boundsAfterScroll = scrollable.getBounds();
                            isAtEnd = scrollAtStartOrEnd && boundsBeforeScroll.equals(
                                    boundsAfterScroll);
                            Log.v(LOG_TAG, "Scrolling done, boundsAfterScroll: "
                                    + boundsAfterScroll);
                        } else {
                            isAtEnd = scrollAtStartOrEnd;
                        }
                        Log.v(LOG_TAG, "Scrolling done, scrollAtStartOrEnd=" + scrollAtStartOrEnd
                                + ", isAtEnd=" + isAtEnd);
                    }
                } else {
                    Log.v(LOG_TAG, "There might be a collapsing toolbar, but no scrollable view."
                            + " Try to collapse");
                    // There might be a collapsing toolbar, but no scrollable view. Try to collapse
                    scrollPastCollapsibleToolbar(null, deadZone);
                }
            }
        }
        return view;
    }

    /**
     * Finds the object on the given display.
     *
     * @param selector The selector to match.
     * @param timeoutMs The timeout in milliseconds.
     * @param displayId The display to search on.
     * @return The object that matches the selector, or null if not found.
     */
    public static UiObject2 waitFindObjectOrNullOnDisplay(
            BySelector selector, long timeoutMs, int displayId) throws UiObjectNotFoundException {
        // Only supported in API level 30 or higher versions.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }

        UiObject2 view = null;
        long start = System.currentTimeMillis();

        while (view == null && start + timeoutMs > System.currentTimeMillis()) {
            view = getUiDevice().wait(Until.findObject(selector), 1000);
            if (view != null) {
                break;
            }

            List<UiObject2> scrollableViews = getUiDevice().findObjects(
                    By.displayId(displayId).scrollable(true));
            if (scrollableViews != null && !scrollableViews.isEmpty()) {
                for (int i = 0; i < scrollableViews.size(); i++) {
                    UiObject2 scrollableView = scrollableViews.get(i);
                    // Swipe far away from the edges to avoid triggering navigation gestures
                    scrollableView.setGestureMarginPercentage((float) getSwipeDeadZonePct());
                    // Scroll from the top to the bottom until the view object is found.
                    scrollableView.scroll(Direction.UP, 1.0f);
                    scrollableView.scrollUntil(Direction.DOWN, Until.findObject(selector));
                    view = getUiDevice().findObject(selector);
                    if (view != null) {
                        break;
                    }
                }
            } else {
                // There might be a collapsing toolbar, but no scrollable view. Try to collapse
                final double deadZone = getSwipeDeadZonePct();
                scrollPastCollapsibleToolbar(null, deadZone);
            }
        }
        return view;
    }

    private static void scrollPastCollapsibleToolbar(UiScrollable scrollable, double deadZone)
            throws UiObjectNotFoundException {
        final UiObject2 collapsingToolbar = getUiDevice().findObject(
                By.res(sCollapsingToolbarResPattern));
        if (collapsingToolbar == null) {
            Log.v(LOG_TAG, "collapsingToolbar is null, return");
            return;
        }

        final int steps = 55; // == UiScrollable.SCROLL_STEPS
        if (scrollable != null && scrollable.exists()) {
            final Rect scrollableBounds = scrollable.getVisibleBounds();
            final int distanceToSwipe = collapsingToolbar.getVisibleBounds().height() / 2;
            boolean result = getUiDevice().swipe(scrollableBounds.centerX(),
                    scrollableBounds.centerY(),
                    scrollableBounds.centerX(), scrollableBounds.centerY() - distanceToSwipe,
                    steps);
            Log.v(LOG_TAG, "scrollPastCollapsibleToolbar swipe successful = " + result);
        } else {
            // There might be a collapsing toolbar, but no scrollable view. Try to collapse
            int maxY = getUiDevice().getDisplayHeight();
            int minY = (int) (deadZone * maxY);
            maxY -= minY;
            boolean result = getUiDevice().drag(0, maxY, 0, minY, steps);
            Log.v(LOG_TAG, "scrollPastCollapsibleToolbar drag successful = " + result);
        }
    }
}
