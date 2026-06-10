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

package android.security.cts.BUG_283103220;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.util.Rational;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class PipActivity extends Activity {

    @Override
    protected void onResume() {
        try {
            super.onResume();

            // Check vulnerability for 'setPictureInPictureParams()' method
            final PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
            final List<String> vulnerableMethodList = new ArrayList<String>();
            if (isVulnerable(builder, this::setPictureInPictureParams)) {
                vulnerableMethodList.add("'setPictureInPictureParams()'");
            }

            // Check vulnerability for 'enterPictureInPictureMode()' method
            if (isVulnerable(builder, this::enterPictureInPictureMode)) {
                vulnerableMethodList.add("'enterPictureInPictureMode()'");
            }

            // Send broadcast to pass/fail test
            final String vulnerableMethods =
                    vulnerableMethodList.isEmpty() ? null : String.join(",", vulnerableMethodList);
            sendBroadcastResult(vulnerableMethods, null);
        } catch (Exception exception) {
            try {
                sendBroadcastResult(null, exception);
            } catch (Exception ignore) {
                // Ignore unintended exceptions
            }
        }
    }

    private boolean isVulnerable(
            PictureInPictureParams.Builder builder,
            Consumer<PictureInPictureParams> methodInvoker) {
        try {
            // Without fix, app can request aspect ratio change via 'PictureInPictureParams',
            // which results in flood of PiP resizing requests, causing PiP window to freeze
            for (int idx = 1; idx < 1000; ++idx) {
                builder.setAspectRatio(
                        new Rational(idx /* numerator */, idx + 1 /* denominator */));
                methodInvoker.accept(builder.build());
            }
        } catch (IllegalStateException illegalStateException) {
            // With fix, after too many PiP aspect ratio change followed by
            // 'setPictureInPictureParams()' or 'enterPictureInPictureMode()' result in
            // 'IllegalStateException' with unique message
            final Pattern pattern =
                    Pattern.compile(
                            "Too many PiP aspect ratio change requests", Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(illegalStateException.getMessage()).find()) {
                return false;
            } else {
                throw illegalStateException;
            }
        }
        return true;
    }

    private void sendBroadcastResult(String vulnerableMethods, Exception exception) {
        sendBroadcast(
                new Intent(BUG_283103220.BUG_283103220_ACTION)
                        .putExtra(BUG_283103220.BUG_283103220_EXCEPTION, exception)
                        .putExtra(
                                BUG_283103220.BUG_283103220_VULNERABLE_METHODS, vulnerableMethods));
    }
}
