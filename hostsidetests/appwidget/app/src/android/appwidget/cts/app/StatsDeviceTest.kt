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

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val manager = AppWidgetManager.getInstance(context)

    @Before
    fun before() {
        assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS))
        uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
        TestAppWidgetProvider.updates.value = 0
    }

    @After
    fun after() {
        uiAutomation.dropShellPermissionIdentity()
    }

    @Test
    fun bindWidget() = runBlocking<Unit> {
        val host = AppWidgetHost(context, 0)
        host.deleteHost()
        host.startListening()

        val views = RemoteViews(context.packageName, android.R.layout.simple_gallery_item)
        views.setImageViewBitmap(
            android.R.id.empty,
            Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        )
        TestAppWidgetProvider.views.set(views)

        val appWidgetId = host.allocateAppWidgetId()
        assertWithMessage("Failed to bind widget")
            .that(
                manager.bindAppWidgetIdIfAllowed(
                    appWidgetId,
                    ComponentName(context, TestAppWidgetProvider::class.java)
                )
            ).isTrue()

        withTimeout(10.seconds) {
            TestAppWidgetProvider.updates.first { it > 0 }
        }
    }
}
