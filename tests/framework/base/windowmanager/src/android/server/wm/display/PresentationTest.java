/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm.display;

import static android.view.WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.MultiDisplayTestBase;
import android.server.wm.WindowManagerState;
import android.server.wm.app.Components;
import android.view.Display;

import com.android.compatibility.common.util.ApiTest;
import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceDisplay:PresentationTest
 */
@Presubmit
public class PresentationTest extends MultiDisplayTestBase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private VirtualDisplaySession mVirtualDisplaySession;

    // WindowManager.LayoutParams.TYPE_PRESENTATION
    private static final int TYPE_PRESENTATION = 2037;

    @Before
    @Override
    public void setUp() throws Exception {
        assumeTrue(supportsMultiDisplay());
        super.setUp();
        mVirtualDisplaySession = createManagedVirtualDisplaySession();
    }

    /**
     * Asserts the legacy presentation flag policy in the real device display setting. The actual
     * set of tested displays is different depending on the environment the test is running in.
     * Critical policies are tested in other test cases using virtual/simulated displays, so this
     * test case serves as additional checks on top of them to detect any physical display errors.
     * Note that this is based on the legacy policy where whether a presentation is allowed or not
     * only depends on the display flag.
     */
    @ApiTest(apis = {"android.app.Presentation#show"})
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS)
    @Test
    public void testPresentationFollowsDisplayFlag() {
        for (Display display : mDm.getDisplays()) {
            launchPresentationActivity(getMainDisplayId(), display.getDisplayId());
            if ((display.getFlags() & Display.FLAG_PRESENTATION) != Display.FLAG_PRESENTATION) {
                assertNoPresentationDisplayed();
            } else {
                assertPresentationOnDisplayAndMatchesDisplayMetrics(display.getDisplayId());
            }
        }
    }

    /**
     * Asserts that a presentation can be created on a presentation display, which is the most basic
     * scenario the API is intended for.
     */
    @ApiTest(apis = {"android.app.Presentation#show"})
    @Test
    public void testPresentationAllowedOnPresentationDisplay() {
        final WindowManagerState.DisplayContent displayForActivity = createDisplayForActivity();
        final WindowManagerState.DisplayContent presentationDisplay = createPresentationDisplay();
        launchPresentationActivity(displayForActivity.mId, presentationDisplay.mId);
        assertPresentationOnDisplayAndMatchesDisplayMetrics(presentationDisplay.mId);
    }

    /**
     * Asserts that a presentation is disallowed from being shown over the activity that's created
     * the presentation, even if it's a presentation display.
     */
    @ApiTest(apis = {"android.app.Presentation#show"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS)
    @Test
    public void testPresentationBlockedOverHostActivity() {
        final WindowManagerState.DisplayContent displayForActivity = createDisplayForActivity();
        launchPresentationActivity(displayForActivity.mId, displayForActivity.mId);
        assertNoPresentationDisplayed();
    }

    /**
     * Asserts that a presentation is allowed even on a non-presentation display as long as the
     * activity that's created the presentation is globally focused on another display.
     */
    @ApiTest(apis = {"android.app.Presentation#show"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS)
    @Test
    public void
            testPresentationAllowedOnNonPresentationDisplayWithFocusedHostTaskOnAnotherDisplay() {
        final WindowManagerState.DisplayContent displayForActivity = createDisplayForActivity();
        final WindowManagerState.DisplayContent nonPresentationDisplay =
                createNonPresentationDisplay();
        launchPresentationActivity(displayForActivity.mId, nonPresentationDisplay.mId);
        assertPresentationOnDisplayAndMatchesDisplayMetrics(nonPresentationDisplay.mId);
    }

    /** Asserts that a presentation isn't dismissed with display resize. */
    @ApiTest(apis = {"android.app.Presentation#show"})
    @Test
    public void testPresentationNotDismissAfterResizeDisplay() {
        final WindowManagerState.DisplayContent displayForActivity = createDisplayForActivity();
        final WindowManagerState.DisplayContent display =
                createDisplay(
                        false /* isSimulated */,
                        true /* isPresentation */,
                        false /* resizeDisplay */,
                        true /* isPublic */);

        assertThat(display.getFlags() & Display.FLAG_PRESENTATION)
                .isEqualTo(Display.FLAG_PRESENTATION);

        launchPresentationActivity(displayForActivity.mId, display.mId);
        assertPresentationOnDisplayAndMatchesDisplayMetrics(display.mId);

        mVirtualDisplaySession.resizeDisplay();

        assertTrue("Presentation must not dismiss on external public display even if"
                + "display resize", mWmState.waitForWithAmState(
                state -> isPresentationOnDisplay(state, display.mId),
                "Presentation window still shows"));
    }

    /**
     * Asserts that a presentation is blocked on a non-presentation display. Note that this is based
     * on the legacy policy where whether a presentation is allowed or not only depends on the
     * display flag.
     */
    @ApiTest(apis = {"android.app.Presentation#show"})
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS)
    @Test
    public void testPresentationBlockedOnNonPresentationDisplay() {
        final WindowManagerState.DisplayContent displayForActivity = createDisplayForActivity();
        final WindowManagerState.DisplayContent nonPresentationDisplay =
                createNonPresentationDisplay();

        launchPresentationActivity(displayForActivity.mId, nonPresentationDisplay.mId);
        assertNoPresentationDisplayed();
    }

    /** Asserts that a private presentation is created on a private presentation display. */
    @ApiTest(apis = {"android.app.Presentation#show"})
    @Test
    public void testPrivatePresentationCreatedOnPrivatePresentationDisplay() {
        final WindowManagerState.DisplayContent displayForActivity = createDisplayForActivity();
        final WindowManagerState.DisplayContent privatePresentationDisplay =
                createPrivatePresentationDisplay();
        launchPresentationActivity(displayForActivity.mId, privatePresentationDisplay.mId);
        assertPresentationOnDisplayAndMatchesDisplayMetrics(privatePresentationDisplay.mId);
    }

    private boolean isPresentationOnDisplay(WindowManagerState windowManagerState, int displayId) {
        final List<WindowManagerState.WindowState> states =
                windowManagerState.getMatchingWindowType(TYPE_PRESENTATION);
        for (WindowManagerState.WindowState ws : states) {
            if (ws.getDisplayId() == displayId) return true;
        }
        return false;
    }

    private void assertNoPresentationDisplayed() {
        final List<WindowManagerState.WindowState> presentationWindows =
                mWmState.getWindowsByPackageName(
                        Components.PRESENTATION_ACTIVITY.getPackageName(), TYPE_PRESENTATION);
        assertThat(presentationWindows).isEmpty();
        final List<WindowManagerState.WindowState> privatePresentationWindows =
                mWmState.getWindowsByPackageName(
                        Components.PRESENTATION_ACTIVITY.getPackageName(),
                        TYPE_PRIVATE_PRESENTATION);
        assertThat(privatePresentationWindows).isEmpty();
    }

    private void assertPresentationOnDisplayAndMatchesDisplayMetrics(int displayId) {
        waitForOrFail(
                "Presentation that matches display metrics didn't show up",
                () -> {
                    mWmState.computeState();
                    WindowManagerState.DisplayContent display = mWmState.getDisplay(displayId);
                    final boolean isPrivate =
                            (display.getFlags() & Display.FLAG_PRIVATE) == Display.FLAG_PRIVATE;
                    final List<WindowManagerState.WindowState> presentationWindows =
                            mWmState.getWindowsByPackageName(
                                    Components.PRESENTATION_ACTIVITY.getPackageName(),
                                    isPrivate ? TYPE_PRIVATE_PRESENTATION : TYPE_PRESENTATION);
                    if (presentationWindows.isEmpty()) return false;
                    WindowManagerState.WindowState presentationWindowState =
                            presentationWindows.get(0);
                    if (presentationWindowState.getDisplayId() != display.mId) return false;

                    return display.getDisplayRect()
                            .equals(
                                    presentationWindowState
                                            .getFullConfiguration()
                                            .windowConfiguration
                                            .getBounds());
                });
    }

    private WindowManagerState.DisplayContent createDisplayForActivity() {
        return createDisplay(
                true /* isSimulated */,
                false /* isPresentation */,
                true /* resizeDisplay */,
                true /* isPublic */);
    }

    private WindowManagerState.DisplayContent createPresentationDisplay() {
        return createDisplay(
                false /* isSimulated */,
                true /* isPresentation */,
                true /* resizeDisplay */,
                true /* isPublic */);
    }

    private WindowManagerState.DisplayContent createNonPresentationDisplay() {
        return createDisplay(
                false /* isSimulated */,
                false /* isPresentation */,
                true /* resizeDisplay */,
                true /* isPublic */);
    }

    private WindowManagerState.DisplayContent createPrivatePresentationDisplay() {
        return createDisplay(
                false /* isSimulated */,
                true /* isPresentation */,
                true /* resizeDisplay */,
                false /* isPublic */);
    }

    private WindowManagerState.DisplayContent createDisplay(
            boolean isSimulated, boolean isPresentation, boolean resizeDisplay, boolean isPublic) {
        // TODO(b/399505380): Use setPublicDisplay() and add a test with it.
        final WindowManagerState.DisplayContent display =
                mVirtualDisplaySession
                        .setSimulateDisplay(isSimulated)
                        .setPresentationDisplay(isPresentation)
                        .setPublicDisplay(isPublic)
                        .setResizeDisplay(resizeDisplay)
                        .createDisplay();
        assertThat((display.getFlags() & Display.FLAG_PRESENTATION) == Display.FLAG_PRESENTATION)
                .isEqualTo(isSimulated || isPresentation);
        assertThat((display.getFlags() & Display.FLAG_PRIVATE) == Display.FLAG_PRIVATE)
                .isEqualTo(!isPublic);
        assertThat((display.getFlags() & Display.FLAG_TRUSTED) == Display.FLAG_TRUSTED)
                .isEqualTo(isSimulated);
        return display;
    }

    private void launchPresentationActivity(
            int displayIdForActivity, int displayIdForPresentation) {
        Intent intent = new Intent();
        intent.setComponent(Components.PRESENTATION_ACTIVITY);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Components.PresentationActivity.KEY_DISPLAY_ID, displayIdForPresentation);
        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchDisplayId(displayIdForActivity);
        final Bundle bundle = launchOptions.toBundle();
        mContext.startActivity(intent, bundle);
        waitAndAssertResumedAndFocusedActivityOnDisplay(
                Components.PRESENTATION_ACTIVITY,
                displayIdForActivity,
                "Launched activity must be on top");
    }
}
