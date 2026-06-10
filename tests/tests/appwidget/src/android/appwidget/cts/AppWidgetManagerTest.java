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

package android.appwidget.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireAutomotive;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class AppWidgetManagerTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    /**
     * Test that app can get a non-null AppWidgetManager instance on AAOS no matter
     * FEATURE_APP_WIDGETS is supported or not.
     */
    @Test
    @ApiTest(apis = {"android.appwidget.AppWidgetManager#getInstance"})
    @RequireAutomotive(reason = "Non-null AppWidgetManager is required on AAOS")
    public void testAppWidgetManagerNotNull() {
        Context context = getInstrumentation().getTargetContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(context);

        Assert.assertNotNull(manager);
    }

    /**
     * Test that app can get a non-null AppWidgetManager instance on AAOS no matter
     * FEATURE_APP_WIDGETS is supported or not.
     */
    @Test
    @ApiTest(apis = {"android.content.Context#getSystemService"})
    @RequireAutomotive(reason = "Non-null AppWidgetManager is required on AAOS")
    public void testAppWidgetManagerNotNullFromContext() {
        Context context = getInstrumentation().getTargetContext();
        AppWidgetManager manager = context.getSystemService(AppWidgetManager.class);

        Assert.assertNotNull(manager);
    }
}
