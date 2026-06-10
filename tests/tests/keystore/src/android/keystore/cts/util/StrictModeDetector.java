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

package android.keystore.cts.util;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.os.StrictMode;
import android.os.strictmode.Violation;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class StrictModeDetector {
    private BlockingQueue<Violation> mViolations;

    public StrictModeDetector(Context ctx) {
        mViolations = new LinkedBlockingQueue<>();
        StrictMode.setThreadPolicy(
                new StrictMode.ThreadPolicy.Builder()
                        .detectDiskReads()
                        .detectDiskWrites()
                        .detectCustomSlowCalls()
                        .penaltyListener(ctx.getMainExecutor(), (v) -> {
                            mViolations.add(v);
                        })
                        .build());
    }

    /**
     * Clear any pending strict mode violations.
     */
    public void clear() {
        mViolations.clear();
    }

    /**
     * Check that a strict mode violation has been triggered.
     **/
    public void check(String msg) {
        assertWithMessage(msg + " should trigger StrictMode").that(mViolations).isNotEmpty();
    }
}
