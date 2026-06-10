/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net.wifi.nl80211.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.net.wifi.WifiMigration;
import android.net.wifi.flags.Flags;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WifiMigrationTest {
    private static final String TEST_SSID_UNQUOTED = "testSsid1";
    private static final int TEST_WAIT_DURATION_MS = 10_000;

    private Context mContext;
    private HandlerThread mHandlerThread = new HandlerThread("WifiMigrationTest");
    private Executor mExecutor;
    {
        mHandlerThread.start();
        mExecutor = new HandlerExecutor(new Handler(mHandlerThread.getLooper()));
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        assumeTrue(WifiFeature.isWifiSupported(mContext));
    }

    @After
    public void tearDown() throws Exception {
        assumeTrue(WifiFeature.isWifiSupported(mContext));
    }

    /**
     * Tests {@link android.net.wifi.WifiMigration.SettingsMigrationData.Builder} class.
     */
    @Test
    public void testWifiMigrationSettingsDataBuilder() throws Exception {
        if (!WifiFeature.isWifiSupported(mContext)) {
            // skip the test if WiFi is not supported
            return;
        }
        WifiMigration.SettingsMigrationData migrationData =
                new WifiMigration.SettingsMigrationData.Builder()
                        .setScanAlwaysAvailable(true)
                        .setP2pFactoryResetPending(true)
                        .setScanThrottleEnabled(true)
                        .setSoftApTimeoutEnabled(true)
                        .setWakeUpEnabled(true)
                        .setVerboseLoggingEnabled(true)
                        .setP2pDeviceName(TEST_SSID_UNQUOTED)
                        .build();

        assertNotNull(migrationData);
        assertTrue(migrationData.isScanAlwaysAvailable());
        assertTrue(migrationData.isP2pFactoryResetPending());
        assertTrue(migrationData.isScanThrottleEnabled());
        assertTrue(migrationData.isSoftApTimeoutEnabled());
        assertTrue(migrationData.isWakeUpEnabled());
        assertTrue(migrationData.isVerboseLoggingEnabled());
        assertEquals(TEST_SSID_UNQUOTED, migrationData.getP2pDeviceName());
    }

    /**
     * Tests {@link android.net.wifi.WifiMigration.SettingsMigrationData} class.
     */
    @Test
    public void testWifiMigrationSettings() throws Exception {
        try {
            WifiMigration.loadFromSettings(mContext);
        } catch (Exception ignore) {
        }
    }

    /**
     * Tests {@link WifiMigration#convertAndRetrieveSharedConfigStoreFile(int)},
     * {@link WifiMigration#convertAndRetrieveUserConfigStoreFile(int, UserHandle)},
     * {@link WifiMigration#removeSharedConfigStoreFile(int)} and
     * {@link WifiMigration#removeUserConfigStoreFile(int, UserHandle)}.
     */
    @Test
    public void testWifiMigrationConfigStore() throws Exception {
        try {
            WifiMigration.convertAndRetrieveSharedConfigStoreFile(
                    WifiMigration.STORE_FILE_SHARED_GENERAL);
        } catch (Exception ignore) {
        }
        try {
            WifiMigration.convertAndRetrieveSharedConfigStoreFile(
                    WifiMigration.STORE_FILE_SHARED_SOFTAP);
        } catch (Exception ignore) {
        }
        try {
            WifiMigration.convertAndRetrieveUserConfigStoreFile(
                    WifiMigration.STORE_FILE_USER_GENERAL,
                    UserHandle.of(ActivityManager.getCurrentUser()));
        } catch (Exception ignore) {
        }
        try {
            WifiMigration.convertAndRetrieveUserConfigStoreFile(
                    WifiMigration.STORE_FILE_USER_NETWORK_SUGGESTIONS,
                    UserHandle.of(ActivityManager.getCurrentUser()));
        } catch (Exception ignore) {
        }
        try {
            WifiMigration.removeSharedConfigStoreFile(
                    WifiMigration.STORE_FILE_SHARED_GENERAL);
        } catch (Exception ignore) {
        }
        try {
            WifiMigration.removeSharedConfigStoreFile(
                    WifiMigration.STORE_FILE_SHARED_SOFTAP);
        } catch (Exception ignore) {
        }
        try {
            WifiMigration.removeUserConfigStoreFile(
                    WifiMigration.STORE_FILE_USER_GENERAL,
                    UserHandle.of(ActivityManager.getCurrentUser()));
        } catch (Exception ignore) {
        }
        try {
            WifiMigration.removeUserConfigStoreFile(
                    WifiMigration.STORE_FILE_USER_NETWORK_SUGGESTIONS,
                    UserHandle.of(ActivityManager.getCurrentUser()));
        } catch (Exception ignore) {
        }
    }

    /**
     * Test that {@link WifiMigration#migrateLegacyKeystoreToWifiBlobstore()}
     * can be called successfully.
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_LEGACY_KEYSTORE_TO_WIFI_BLOBSTORE_MIGRATION_READ_ONLY)
    public void testMigrateLegacyKeystoreToWifiBlobstore() throws Exception {
        AtomicInteger statusCode = new AtomicInteger(-1);
        CountDownLatch latch = new CountDownLatch(1);

        class StatusCodeConsumer implements IntConsumer {
            @Override
            public void accept(int status) {
                statusCode.set(status);
                latch.countDown();
            }
        }

        WifiMigration.migrateLegacyKeystoreToWifiBlobstore(mExecutor, new StatusCodeConsumer());

        // Wait until the status code arrives or the time expires
        latch.await(TEST_WAIT_DURATION_MS, TimeUnit.MILLISECONDS);
        assertNotEquals(-1, statusCode.get());
    }
}
