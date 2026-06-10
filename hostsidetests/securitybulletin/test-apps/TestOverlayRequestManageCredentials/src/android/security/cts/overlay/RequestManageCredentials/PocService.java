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

package android.security.cts.overlay_request_manage_credentials;

import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;

public class PocService extends Service {
    private Button mButton;
    private WindowManager mWindowManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            mWindowManager = getSystemService(WindowManager.class);
            LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            layoutParams.flags =
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            layoutParams.format = PixelFormat.OPAQUE;
            layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
            int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
            int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
            layoutParams.width = screenWidth;
            layoutParams.height = screenHeight;
            layoutParams.x = screenWidth / 2;
            layoutParams.y = screenHeight / 2;

            mButton = new Button(this);
            mButton.setText(getString(R.string.overlayBtnTxt));
            mWindowManager.addView(mButton, layoutParams);
            mButton.setTag(mButton.getVisibility());
            return super.onStartCommand(intent, flags, startId);
        } catch (Exception e) {
            try {
                // Send a broadcast to cause an assumption failure
                sendBroadcast(
                        new Intent(getString(R.string.broadcastAction))
                                .putExtra(getString(R.string.keyException), e)
                                .setPackage(getPackageName()));
            } catch (Exception ex) {
                // ignore
            }
        }
        return 0;
    }

    @Override
    public void onDestroy() {
        try {
            mWindowManager.removeView(mButton);
            super.onDestroy();
        } catch (Exception e) {
            // ignore since this is invoked as a part of cleanup
        }
    }
}
