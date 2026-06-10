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

package android.app.cts.broadcasts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(BroadcastsTestRunner.class)
public class StickyBroadcastsTest extends BaseBroadcastTest {
    @Test
    public void testBroadcastQueries() throws Exception {
        final int batteryStatus = getBatteryStatusFromQueryApi();

        assertThat(batteryStatus).isEqualTo(getBatteryStatusFromBroadcast());
        // Query the status again and verify that the result remains the same.
        assertThat(batteryStatus).isEqualTo(getBatteryStatusFromBroadcast());

        try {
            // Since we are trying to change the status, choose a status that's not the same
            // as the current status.
            int testStatus = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
                    ? BatteryManager.BATTERY_STATUS_DISCHARGING
                    : BatteryManager.BATTERY_STATUS_CHARGING;
            triggerAndWaitForBatteryStatusChange(testStatus);
            // Verify that sticky broadcast query returns the expected status.
            assertThat(testStatus).isEqualTo(getBatteryStatusFromBroadcast());
        } finally {
            runShellCmd("cmd battery reset");
        }
    }

    private int getBatteryStatusFromBroadcast() {
        final IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        final Intent intent = mContext.registerReceiver(null, filter);
        assertThat(intent).isNotNull();
        return intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
    }

    private int getBatteryStatusFromQueryApi() {
        final BatteryManager batteryManager = mContext.getSystemService(BatteryManager.class);
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
    }

    private void triggerAndWaitForBatteryStatusChange(int expectedStatus) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN);
                if (status == expectedStatus) {
                    latch.countDown();
                }
            }
        }, filter);
        runShellCmd("cmd battery set status %s", String.valueOf(expectedStatus));

        if (!latch.await(BROADCAST_RECEIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            fail("Timed out waiting for the battery_changed broadcast");
        }
    }
}
