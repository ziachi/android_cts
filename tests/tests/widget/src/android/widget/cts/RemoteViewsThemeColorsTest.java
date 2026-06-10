/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.widget.cts;

import static org.junit.Assert.assertEquals;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Resources;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.cts.util.RemoteViewsUtil;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@MediumTest
@RunWith(Parameterized.class)
public class RemoteViewsThemeColorsTest {
    private static final String PACKAGE_NAME = "android.widget.cts";

    private static final List<Integer> ALL_COLORS = generateColorList();

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<RemoteViewsCtsActivity> mActivityRule =
            new ActivityTestRule<>(RemoteViewsCtsActivity.class);

    @Rule
    public ExpectedException mExpectedException = ExpectedException.none();

    @Parameterized.Parameters(name = "isProtoTest={0}")
    public static Object[] parameters() {
        return new Object[] {false, true};
    }

    /**
     * When this parameter is true, the test serializes and deserializes the RemoteViews to/from
     * proto before applying. This ensures that proto serialization does not cause a change in the
     * structure or function of RemoteViews, apart from PendingIntent based APIs.
     */
    @Parameterized.Parameter(0)
    public boolean isProtoTest;

    private Instrumentation mInstrumentation;

    private Context mContext;

    private RemoteViews mRemoteViews;

    @Before
    public void setUp() {
        RemoteViewsUtil.checkRemoteViewsProtoFlag(isProtoTest);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mRemoteViews = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_good);
    }

    @Test
    public void apply_setNoThemeColors_shouldNotChangeColors() throws Throwable {
        View result = setUpView(new SparseIntArray());

        Context resultContext = result.getContext();
        for (int color : ALL_COLORS) {
            assertEquals(mContext.getColor(color), resultContext.getColor(color));
        }
    }

    @Test
    public void apply_setAllColorsInTheme_shouldAllChange() throws Throwable {
        SparseIntArray theme = new SparseIntArray(ALL_COLORS.size());
        for (int i = 0; i < ALL_COLORS.size(); i++) {
            theme.put(ALL_COLORS.get(i), 0xffffff00 + i);
        }

        View result = setUpView(theme);

        Context resultContext = result.getContext();
        for (int i = 0; i < ALL_COLORS.size(); i++) {
            assertEquals(0xffffff00 + i, resultContext.getColor(ALL_COLORS.get(i)));
        }
    }

    @Test
    public void apply_setSomeColorsInTheme_shouldChangeThoseColorsOnly() throws Throwable {
        List<Integer> changedColors = List.of(ALL_COLORS.get(10), ALL_COLORS.get(11),
                ALL_COLORS.get(17), ALL_COLORS.get(8), ALL_COLORS.get(1), ALL_COLORS.get(101));
        SparseIntArray theme = new SparseIntArray();
        for (int i = 0; i < changedColors.size(); i++) {
            theme.put(changedColors.get(i), 0xffffff00 + i);
        }

        View result = setUpView(theme);

        Context resultContext = result.getContext();
        for (int color : ALL_COLORS) {
            if (changedColors.contains(color)) {
                assertEquals(theme.get(color), resultContext.getColor(color));
            } else {
                assertEquals(mContext.getColor(color), resultContext.getColor(color));
            }
        }
    }

    @Test
    public void apply_setNonThemeColors_shouldNotChangeContext() throws Throwable {
        SparseIntArray theme = new SparseIntArray(3);
        theme.put(android.R.dimen.app_icon_size, 12);
        theme.put(android.R.integer.config_longAnimTime, 5);
        theme.put(android.R.color.darker_gray, 0xff00ffff);

        View result = setUpView(theme);

        Resources res = mContext.getResources();
        Resources resultRes = result.getContext().getResources();
        assertEquals(res.getDimensionPixelSize(android.R.dimen.app_icon_size),
                resultRes.getDimensionPixelSize(android.R.dimen.app_icon_size));
        assertEquals(res.getInteger(android.R.integer.config_longAnimTime),
                resultRes.getInteger(android.R.integer.config_longAnimTime));
        assertEquals(res.getColor(android.R.color.darker_gray),
                resultRes.getColor(android.R.color.darker_gray));
    }

    private View setUpView(SparseIntArray colorResources) throws Throwable {
        AtomicReference<View> view = new AtomicReference<>();
        mActivityRule.runOnUiThread(() -> view.set(setUpViewInternal(colorResources)));
        return view.get();
    }

    private View setUpViewInternal(SparseIntArray colorResources) {
        View result = null;
        try {
            result =
                    RemoteViewsUtil.applyRemoteViews(
                            mActivityRule,
                            mContext,
                            mRemoteViews,
                            isProtoTest,
                            null,
                            RemoteViews.ColorResources.create(mContext, colorResources));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        // Add our host view to the activity behind this test. This is similar to how launchers
        // add widgets to the on-screen UI.
        ViewGroup root = mActivityRule.getActivity().findViewById(R.id.remoteView_host);
        FrameLayout.MarginLayoutParams lp = new FrameLayout.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        result.setLayoutParams(lp);

        root.addView(result);
        return result;
    }

    private static List<Integer> generateColorList() {
        List<Integer> colors = new ArrayList<>();
        for (int color = android.R.color.system_neutral1_0;
                color <= android.R.color.system_error_1000; color++) {
            colors.add(color);
        }
        return colors;
    }
}
