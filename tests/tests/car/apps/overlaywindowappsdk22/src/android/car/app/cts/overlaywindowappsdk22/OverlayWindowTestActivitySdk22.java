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

package android.car.app.cts.overlaywindowappsdk22;

import static android.view.Gravity.LEFT;
import static android.view.Gravity.TOP;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Helper activity for MultiUserMultiDisplayWindowSecurityTest. As of API level 23, an application
 * cannot draw an overlay window unless it declares the
 * {@link android.Manifest.permission#SYSTEM_ALERT_WINDOW SYSTEM_ALERT_WINDOW} permission in its
 * manifest, <em>and</em> the user explicitly grant the app this capability. Therefore, it's
 * impossible for the application to create the overlay window in a car environment, as there is
 * no user interface for granting the permission. However, if the application targets API level 22
 * or lower, it suffices to request the permission in the manifest. Hence, API level 22 is selected
 * to verify if the overlay window can be displayed on unassigned displays.
 */
public class OverlayWindowTestActivitySdk22 extends Activity {
    private static final String TAG = "OverlayWindowTestActivitySdk22";

    private static final int OVERLAY_WINDOW_TYPE = TYPE_APPLICATION_OVERLAY;
    private static final String EXTRA_DISPLAY_ID_TO_SHOW_OVERLAY =
            "android.car.app.cts.DISPLAY_ID_TO_SHOW_OVERLAY";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        int displayId = intent.getIntExtra(EXTRA_DISPLAY_ID_TO_SHOW_OVERLAY,
                Display.INVALID_DISPLAY);
        createOverlayWindowOnDisplay(displayId, OVERLAY_WINDOW_TYPE, getPackageName());
    }

    private void createOverlayWindowOnDisplay(int displayId, int windowType, String windowName) {
        DisplayManager displayManager = getSystemService(DisplayManager.class);
        Display display = displayManager.getDisplay(displayId);
        if (display == null) {
            Log.e(TAG, "Unable to create overlay window: invalid display");
            return;
        }
        Context displayContext = createDisplayContext(display);
        WindowManager windowManager = displayContext.getSystemService(WindowManager.class);
        Point size = new Point();
        display.getSize(size);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(windowType);
        params.width = size.x / 3;
        params.height = size.y / 3;
        params.gravity = TOP | LEFT;
        params.setTitle(windowName);

        TextView view = new TextView(this);
        view.setText(windowName);
        view.setBackgroundColor(Color.CYAN);
        windowManager.addView(view, params);
    }
}
