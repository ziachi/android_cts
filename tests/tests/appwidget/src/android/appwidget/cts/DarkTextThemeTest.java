/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.appwidget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.appwidget.cts.activity.EmptyActivity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.platform.test.annotations.AppModeFull;
import android.util.ArrayMap;
import android.view.View;
import android.widget.RemoteViews;

import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;

/**
 * Test AppWidgets dark text theme
 */
@LargeTest
@AppModeFull
@RunWith(AndroidJUnit4.class)
public class DarkTextThemeTest extends AppWidgetTestCase {

    @Rule
    public ActivityTestRule<EmptyActivity> mActivityRule =
            new ActivityTestRule<>(EmptyActivity.class);

    private boolean mHasAppWidgets;

    private EmptyActivity mActivity;

    private AppWidgetHost mAppWidgetHost;

    private MyHostView mAppWidgetHostView;
    private int mAppWidgetId;

    @Before
    public void setup() throws Throwable {
        mHasAppWidgets = hasAppWidgets();
        if (!mHasAppWidgets) {
            return;
        }
        // We want to bind widgets - run a shell command to grant bind permission to our
        // package.
        grantBindAppWidgetPermission();

        mActivity = mActivityRule.getActivity();
        mActivityRule.runOnUiThread(this::bindNewWidget);
    }

    @After
    public void teardown() throws Exception {
        if (!mHasAppWidgets) {
            return;
        }
        mAppWidgetHost.deleteHost();
        revokeBindAppWidgetPermission();
    }

    private void bindNewWidget() {
        mAppWidgetHost = new AppWidgetHost(mActivity, 0) {
            @Override
            protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
                    AppWidgetProviderInfo appWidget) {
                return new MyHostView(context);
            }
        };
        mAppWidgetHost.deleteHost();
        mAppWidgetHost.startListening();

        // Allocate a widget id to bind
        mAppWidgetId = mAppWidgetHost.allocateAppWidgetId();

        // Bind the app widget
        final AppWidgetProviderInfo providerInfo = getProviderInfo(getFirstWidgetComponent());
        boolean isBinding = getAppWidgetManager().bindAppWidgetIdIfAllowed(mAppWidgetId,
                providerInfo.getProfile(), providerInfo.provider, null);
        assertTrue(isBinding);

        // Create host view
        mAppWidgetHostView = (MyHostView) mAppWidgetHost
                .createView(mActivity, mAppWidgetId, providerInfo);
        mActivity.setContentView(mAppWidgetHostView);
    }

    @Test
    public void testWidget_light() throws Throwable {
        if (!mHasAppWidgets) {
            return;
        }
        // Push update
        RemoteViews views = getViewsForResponse();
        getAppWidgetManager().updateAppWidget(new int[] {mAppWidgetId}, views);

        // Await until update
        CountDownLatch updateLatch = new CountDownLatch(1);
        mAppWidgetHostView.mCommands.put(
                (v) -> v.findViewById(R.id.hello) != null, updateLatch::countDown);
        updateLatch.await();

        // Perform click
        verifyColor(mAppWidgetHostView, Color.WHITE);
    }

    @Test
    public void testWidget_dark() throws Throwable {
        if (!mHasAppWidgets) {
            return;
        }
        mActivity.runOnUiThread(() -> mAppWidgetHostView.setOnLightBackground(true));

        // Push update
        RemoteViews views = getViewsForResponse();
        getAppWidgetManager().updateAppWidget(new int[] {mAppWidgetId}, views);

        // Await until update
        CountDownLatch updateLatch = new CountDownLatch(1);
        mAppWidgetHostView.mCommands.put(
                (v) -> v.findViewById(R.id.hello) != null, updateLatch::countDown);
        updateLatch.await();

        // Perform click
        verifyColor(mAppWidgetHostView, Color.BLACK);
    }

    private RemoteViews getViewsForResponse() {
        RemoteViews views = new RemoteViews(mActivity.getPackageName(),
                R.layout.simple_white_layout);
        views.setLightBackgroundLayoutId(R.layout.simple_black_layout);
        return views;
    }

    private void verifyColor(View parent, int color) {
        Drawable bg = parent.findViewById(R.id.hello).getBackground();
        assertTrue(bg instanceof ColorDrawable);
        assertEquals(color, ((ColorDrawable) bg).getColor());
    }

    /**
     * Host view which supports waiting for a update to happen.
     */
    private static class MyHostView extends AppWidgetHostView {

        final ArrayMap<Predicate<MyHostView>, Runnable> mCommands = new ArrayMap<>();

        MyHostView(Context context) {
            super(context);
        }

        @Override
        public void updateAppWidget(RemoteViews remoteViews) {
            super.updateAppWidget(remoteViews);

            for (int i = mCommands.size() - 1; i >= 0; i--) {
                if (mCommands.keyAt(i).test(this)) {
                    Runnable action = mCommands.valueAt(i);
                    mCommands.removeAt(i);
                    action.run();
                }
            }
        }
    }
}
