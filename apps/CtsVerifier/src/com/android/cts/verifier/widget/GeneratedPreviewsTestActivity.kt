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

package com.android.cts.verifier.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
import android.content.ComponentName
import android.os.Bundle
import android.widget.RemoteViews
import com.android.cts.verifier.PassFailButtons
import com.android.cts.verifier.R

class GeneratedPreviewsTestActivity : PassFailButtons.Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppWidgetManager.getInstance(this).setWidgetPreview(
            ComponentName(this, GeneratedPreviewWidgetProvider::class.java),
            WIDGET_CATEGORY_HOME_SCREEN or WIDGET_CATEGORY_KEYGUARD,
            RemoteViews(packageName, R.layout.widget_generated_preview)
        )

        setContentView(R.layout.pass_fail_widget)
        setPassFailButtonClickListeners()
        setInfoResources(
            R.string.widget_generated_previews_test,
            R.string.widget_generated_previews_test_info,
            -1
        )
    }
}

class GeneratedPreviewWidgetProvider : AppWidgetProvider()
