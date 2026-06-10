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
 * limitations under the License
 */

package android.server.wm.apptv;

import static android.server.wm.app.Components.PipActivity.ACTION_SET_ON_PAUSE_REMOTE_CALLBACK;
import static android.server.wm.app.Components.PipActivity.EXTRA_ALLOW_AUTO_PIP;
import static android.server.wm.app.Components.PipActivity.EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP;
import static android.server.wm.app.Components.PipActivity.EXTRA_PIP_ON_PAUSE_CALLBACK;
import static android.server.wm.app.Components.PipActivity.EXTRA_SET_PIP_CALLBACK;
import static android.server.wm.app.Components.PipActivity.IS_IN_PIP_MODE_RESULT;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.util.Log;
import android.util.Rational;

import androidx.annotation.Nullable;

public class PipActivity extends Activity {
    private static final String TAG = PipActivity.class.getSimpleName();

    private boolean mEnteredPictureInPicture;

    private RemoteCallback mOnPauseCallback;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null) {
                if (intent.getAction().equals(ACTION_SET_ON_PAUSE_REMOTE_CALLBACK)) {
                    mOnPauseCallback = intent.getParcelableExtra(
                            EXTRA_PIP_ON_PAUSE_CALLBACK, RemoteCallback.class);
                    // Signals the caller that we have received the mOnPauseCallback
                    final RemoteCallback setCallback = intent.getParcelableExtra(
                            EXTRA_SET_PIP_CALLBACK, RemoteCallback.class);
                    setCallback.sendResult(Bundle.EMPTY);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SET_ON_PAUSE_REMOTE_CALLBACK);
        registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);

        if (parseBooleanExtra(EXTRA_ENTER_PIP)) {
            enterPictureInPictureMode(getPipParams());
        } else {
            setPictureInPictureParams(getPipParams());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOnPauseCallback != null) {
            Bundle res = new Bundle(1);
            res.putBoolean(IS_IN_PIP_MODE_RESULT, isInPictureInPictureMode());
            mOnPauseCallback.sendResult(res);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (parseBooleanExtra(EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP) && !mEnteredPictureInPicture) {
            Log.w(TAG, "Unexpected onStop() called before entering picture-in-picture");
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
            Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);

        // Fail early if the activity state does not match the dispatched state
        if (isInPictureInPictureMode() != isInPictureInPictureMode) {
            Log.w(TAG, "Received onPictureInPictureModeChanged mode="
                    + isInPictureInPictureMode + " activityState=" + isInPictureInPictureMode());
            finish();
        }

        // Mark that we've entered picture-in-picture so that we can stop checking for
        // EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP
        if (isInPictureInPictureMode) {
            mEnteredPictureInPicture = true;
        }
    }

    private PictureInPictureParams getPipParams() {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(1, 1))
                .setAutoEnterEnabled(parseBooleanExtra(EXTRA_ALLOW_AUTO_PIP));
        return builder.build();
    }

    /**
     * Launches a new instance of the PipActivity in the same task that will automatically enter
     * PiP.
     */
    static void launchEnterPipActivity(Activity caller, @Nullable Bundle overrides) {
        final Intent intent = new Intent(caller, PipActivity.class);
        intent.putExtra(EXTRA_ENTER_PIP, "true");
        intent.putExtra(EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP, "true");
        if (overrides != null) {
            intent.putExtras(overrides);
        }
        caller.startActivity(intent);
    }

    private boolean parseBooleanExtra(String key) {
        return getIntent().hasExtra(key) && Boolean.parseBoolean(getIntent().getStringExtra(key));
    }

}
