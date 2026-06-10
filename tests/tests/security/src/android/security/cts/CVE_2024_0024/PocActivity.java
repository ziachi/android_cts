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

package android.security.cts.CVE_2024_0024;

import android.app.Activity;
import android.content.Intent;
import android.os.UserManager;
import android.security.cts.R;

import java.util.Random;

public class PocActivity extends Activity {
    private static final int MAX_UNSIGNED_SHORT = (1 << 16) - 1;

    @Override
    public void onResume() {
        String exception = null;
        try {
            super.onResume();

            // Fill random characters from A to Z in 'largeAccountTypeString'
            String largeAccountTypeString =
                    new Random()
                            .ints('A' /* randomNumberOrigin */, 'Z' + 1 /* randomNumberBound */)
                            .limit(MAX_UNSIGNED_SHORT /* maxSize */)
                            .collect(
                                    StringBuilder::new /* supplier */,
                                    StringBuilder::appendCodePoint /* accumulator */,
                                    StringBuilder::append /* combiner */)
                            .toString();

            // Launch activity to reproduce vulnerability
            // User creation intent must be launched with 'startActivityForResult'
            startActivityForResult(
                    UserManager.createUserCreationIntent(
                            "CVE_2024_0024_user" /* userName */,
                            "CVE_2024_0024_account" /* accountName */,
                            largeAccountTypeString /* accountType */,
                            null /* accountOptions */),
                    1 /* requestCode */);
        } catch (Exception e) {
            exception = e.getMessage();
        } finally {
            try {
                // Send broadcast to 'DeviceTest' along with exception message as a string extra
                sendBroadcast(
                        new Intent(getString(R.string.CVE_2024_0024_action))
                                .putExtra(getString(R.string.CVE_2024_0024_exception), exception)
                                .setPackage(getPackageName()));
            } catch (Exception ignore) {
                // Ignore unintended exceptions
            }
        }
    }
}
