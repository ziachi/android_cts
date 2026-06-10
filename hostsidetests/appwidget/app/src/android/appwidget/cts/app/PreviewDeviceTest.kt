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
package android.appwidget.cts.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Process
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assume.assumeTrue
import org.junit.Before;
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val manager = AppWidgetManager.getInstance(context)

    @Before
    fun setUp() {
        assumeTrue(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS))
    }

    @Test
    fun setPreview() {
        val success = manager.setWidgetPreview(
            ComponentName(context, TestAppWidgetProvider::class.java),
            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
            RemoteViews(context.packageName, android.R.layout.simple_list_item_1)
        )
        assertWithMessage("Failed to set widget preview").that(success).isTrue()
    }

    @Test
    fun checkPreview() {
        val preview = manager.getWidgetPreview(
            ComponentName(context, TestAppWidgetProvider::class.java),
            Process.myUserHandle(),
            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
        )
        assertWithMessage("Returned preview is null")
            .that(preview).isNotNull()
        assertWithMessage("Preview has unexpected layout ID")
            .that(preview?.layoutId).isEqualTo(android.R.layout.simple_list_item_1)
    }
}
