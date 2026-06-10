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

package android.security.cts;


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.Manifest;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;
import android.security.intrusiondetection.IntrusionDetectionManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Slog;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_AFL_API)
public class IntrusionDetectionManagerTest {
    private Context mContext;
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private IntrusionDetectionManager mIntrusionDetectionManager;
    private static final String PRODUCTION_BUILD = "user";
    private static final String PROPERTY_BUILD_TYPE = "ro.build.type";
    private static final String TAG = "IntrusionDetectionManagerTest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() throws InterruptedException {
        mContext = mInstrumentation.getContext();
        assumeTrue(isTestableHardware(mContext));
        mIntrusionDetectionManager = mContext.getSystemService(IntrusionDetectionManager.class);
        assertNotNull(mIntrusionDetectionManager);
        reset();
    }

    private static boolean isTestableHardware(Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return false;
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return false;
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            return false;
        }
        if (pm.hasSystemFeature(PackageManager.FEATURE_PC)) {
            return false;
        }
        return true;
    }

    @After
    public void teardown() throws InterruptedException {
        // Only perform teardown if the hardware is testable.
        if (!isTestableHardware(mContext)) {
          return;
        }
        reset();
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    private void reset() throws InterruptedException {
        // TODO: b/399717716 [AIL] Drop permissions in CTS tests
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.READ_INTRUSION_DETECTION_STATE,
                Manifest.permission.MANAGE_INTRUSION_DETECTION_STATE);
        var commandLatch = new CountDownLatch(1);

        var executor = newSingleThreadExecutor();
        mIntrusionDetectionManager.disable(executor,
                new IntrusionDetectionManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertTrue(commandLatch.await(1, SECONDS));

        var stateLatch = new CountDownLatch(1);
        mIntrusionDetectionManager.addStateCallback(executor,
                state -> {
                    if (stateLatch.getCount() > 0) {
                        stateLatch.countDown();
                        assertEquals(IntrusionDetectionManager.STATE_DISABLED, state.intValue());
                    }
                });
        assertTrue(stateLatch.await(1, SECONDS));
        executor.close();
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    private static String getSystemPropertyValue(String propertyName) {
        String commandString = "getprop " + propertyName;
        try {
            Process process = Runtime.getRuntime().exec(commandString);
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));
            String propertyValue = reader.readLine();
            reader.close();
            return propertyValue;
        } catch (IOException e) {
            Slog.e(TAG, "Failed to get system property value:", e);
            return null;
        }
    }

    private static String getBuildType() {
        return getSystemPropertyValue(PROPERTY_BUILD_TYPE);
    }

    private static boolean shouldTestIntrusionDetectionEventTransportConfig() {
        return !getBuildType().equals(PRODUCTION_BUILD);
    }

    @Test
    public void testAddStateCallback_NoPermission() {
        var executor = newSingleThreadExecutor();
        assertThrows(SecurityException.class, () -> mIntrusionDetectionManager.addStateCallback(
                executor, state -> {}));
        executor.close();
    }

    @Test
    public void testRemoveStateCallback_NoPermission() {
        // TODO: b/399717716 [AIL] Drop permissions in CTS tests
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.READ_INTRUSION_DETECTION_STATE);
        var executor = newSingleThreadExecutor();
        Consumer<Integer> scb = state -> {};
        mIntrusionDetectionManager.addStateCallback(executor, scb);

        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        assertThrows(SecurityException.class,
                () -> mIntrusionDetectionManager.removeStateCallback(scb));
        executor.close();
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testEnable_NoPermission() {
        var executor = newSingleThreadExecutor();
        assertThrows(SecurityException.class, () -> mIntrusionDetectionManager.enable(
                executor, new IntrusionDetectionManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        fail("onSuccess shall not be called");
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                }));
        executor.close();
    }

    @Test
    public void testDisable_NoPermission() {
        var executor = newSingleThreadExecutor();
        assertThrows(SecurityException.class, () -> mIntrusionDetectionManager.disable(
                executor, new IntrusionDetectionManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        fail("onSuccess shall not be called");
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                }));
        executor.close();
    }

    @Test
    public void testRemoveStateCallback() throws InterruptedException {
        assumeTrue(shouldTestIntrusionDetectionEventTransportConfig());
        // TODO: b/399717716 [AIL] Drop permissions in CTS tests
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.BIND_INTRUSION_DETECTION_EVENT_TRANSPORT_SERVICE,
                        Manifest.permission.READ_INTRUSION_DETECTION_STATE,
                        Manifest.permission.MANAGE_INTRUSION_DETECTION_STATE);

        var executor = newSingleThreadExecutor();

        var scb0Latch0 = new CountDownLatch(1);
        var scb0Latch1 = new CountDownLatch(1);
        var scb0Latch2 = new CountDownLatch(1);
        AtomicInteger scb0Counter = new AtomicInteger();
        scb0Counter.set(0);
        Consumer<Integer> scb0 = state -> {
            if (scb0Counter.get() == 0) {
                assertEquals(IntrusionDetectionManager.STATE_DISABLED, state.intValue());
                scb0Latch0.countDown();
                scb0Counter.getAndIncrement();
            } else if (scb0Counter.get() == 1) {
                assertEquals(IntrusionDetectionManager.STATE_ENABLED, state.intValue());
                scb0Latch1.countDown();
                scb0Counter.getAndIncrement();
            } else if (scb0Counter.get() == 2) {
                assertEquals(IntrusionDetectionManager.STATE_DISABLED, state.intValue());
                scb0Latch2.countDown();
                scb0Counter.getAndIncrement();
            } else {
                fail("state callback (scb0) can only be called three times!");
            }
        };

        var scb1Latch0 = new CountDownLatch(1);
        var scb1Latch1 = new CountDownLatch(1);
        AtomicInteger scb1Counter = new AtomicInteger();
        scb1Counter.set(0);
        Consumer<Integer> scb1 = state -> {
            if (scb1Counter.get() == 0) {
                assertEquals(IntrusionDetectionManager.STATE_DISABLED, state.intValue());
                scb1Latch0.countDown();
                scb1Counter.getAndIncrement();
            } else if (scb1Counter.get() == 1) {
                assertEquals(IntrusionDetectionManager.STATE_ENABLED, state.intValue());
                scb1Latch1.countDown();
                scb1Counter.getAndIncrement();
            } else {
                fail("state callback (scb1) can only be called twice!");
            }
        };

        mIntrusionDetectionManager.addStateCallback(executor, scb0);
        mIntrusionDetectionManager.addStateCallback(executor, scb1);

        var commandLatch0 = new CountDownLatch(1);
        mIntrusionDetectionManager.enable(executor,
                new IntrusionDetectionManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch0.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertThat(scb0Latch0.await(1, SECONDS)).isTrue();
        assertThat(scb1Latch0.await(1, SECONDS)).isTrue();
        assertThat(commandLatch0.await(1, SECONDS)).isTrue();
        assertThat(scb0Latch1.await(1, SECONDS)).isTrue();
        assertThat(scb1Latch1.await(1, SECONDS)).isTrue();

        mIntrusionDetectionManager.removeStateCallback(scb1);
        var commandLatch1 = new CountDownLatch(1);
        mIntrusionDetectionManager.disable(executor,
                new IntrusionDetectionManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch1.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertThat(commandLatch1.await(1, SECONDS)).isTrue();
        assertThat(scb0Latch2.await(1, SECONDS)).isTrue();
        executor.close();
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testDisable_FromDisable() throws InterruptedException {
        // TODO: b/399717716 [AIL] Drop permissions in CTS tests
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.READ_INTRUSION_DETECTION_STATE,
                Manifest.permission.MANAGE_INTRUSION_DETECTION_STATE);

        var executor = newSingleThreadExecutor();

        var stateLatch0 = new CountDownLatch(1);
        AtomicInteger stateCounter = new AtomicInteger();
        stateCounter.set(0);
        mIntrusionDetectionManager.addStateCallback(executor,
                state -> {
                    if (stateCounter.get() == 0) {
                        assertEquals(IntrusionDetectionManager.STATE_DISABLED, state.intValue());
                        stateLatch0.countDown();
                        stateCounter.getAndIncrement();
                    } else {
                        fail("state callback can be called only once!");
                    }
                });

        var commandLatch0 = new CountDownLatch(1);
        mIntrusionDetectionManager.disable(executor,
                new IntrusionDetectionManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch0.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertThat(stateLatch0.await(1, SECONDS)).isTrue();
        assertThat(commandLatch0.await(1, SECONDS)).isTrue();

        executor.close();
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testEnable_FromEnable() throws InterruptedException {
        assumeTrue(shouldTestIntrusionDetectionEventTransportConfig());
        // TODO: b/399717716 [AIL] Drop permissions in CTS tests
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.BIND_INTRUSION_DETECTION_EVENT_TRANSPORT_SERVICE,
                        Manifest.permission.READ_INTRUSION_DETECTION_STATE,
                        Manifest.permission.MANAGE_INTRUSION_DETECTION_STATE);

        var executor = newSingleThreadExecutor();

        var stateLatch0 = new CountDownLatch(1);
        var stateLatch1 = new CountDownLatch(1);
        AtomicInteger stateCounter = new AtomicInteger();
        stateCounter.set(0);
        mIntrusionDetectionManager.addStateCallback(executor,
                state -> {
                    if (stateCounter.get() == 0) {
                        assertEquals(IntrusionDetectionManager.STATE_DISABLED, state.intValue());
                        stateLatch0.countDown();
                        stateCounter.getAndIncrement();
                    } else if (stateCounter.get() == 1) {
                        assertEquals(IntrusionDetectionManager.STATE_ENABLED, state.intValue());
                        stateLatch1.countDown();
                        stateCounter.getAndIncrement();
                    } else {
                        fail("state callback can only be called twice!");
                    }
                });

        var commandLatch0 = new CountDownLatch(1);
        mIntrusionDetectionManager.enable(executor,
                new IntrusionDetectionManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch0.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertThat(stateLatch0.await(1, SECONDS)).isTrue();
        assertThat(commandLatch0.await(1, SECONDS)).isTrue();
        assertThat(stateLatch1.await(1, SECONDS)).isTrue();

        var commandLatch1 = new CountDownLatch(1);
        mIntrusionDetectionManager.enable(executor,
                new IntrusionDetectionManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch1.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertThat(commandLatch1.await(1, SECONDS)).isTrue();
        executor.close();
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testEnable_verifyDataSources() throws Exception {
        assumeTrue(shouldTestIntrusionDetectionEventTransportConfig());
        // TODO: b/399717716 [AIL] Drop permissions in CTS tests
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.BIND_INTRUSION_DETECTION_EVENT_TRANSPORT_SERVICE,
                        Manifest.permission.READ_INTRUSION_DETECTION_STATE,
                        Manifest.permission.MANAGE_INTRUSION_DETECTION_STATE);

        var executor = newSingleThreadExecutor();
        String securityEventTag = "test_security_event_tag";

        // Register receiver to detect when security event was received by the test app.
        CountDownLatch securityEventLatch = new CountDownLatch(1);
        IntrusionDetectionBroadcastReceiver securityEventReceiver =
                new IntrusionDetectionBroadcastReceiver(securityEventLatch);
        IntentFilter filter =
                new IntentFilter(
                        "com.android.coretests.apps.testapp.ACTION_SECURITY_EVENT_RECEIVED");
        mContext.registerReceiver(securityEventReceiver, filter, Context.RECEIVER_EXPORTED);

        AtomicInteger stateCounter = new AtomicInteger();
        stateCounter.set(0);
        mIntrusionDetectionManager.addStateCallback(
                executor,
                state -> {
                    if (stateCounter.get() == 0) {
                        assertEquals(IntrusionDetectionManager.STATE_DISABLED, state.intValue());
                        stateCounter.getAndIncrement();
                    } else if (stateCounter.get() == 1) {
                        assertEquals(IntrusionDetectionManager.STATE_ENABLED, state.intValue());
                        stateCounter.getAndIncrement();
                    } else {
                        fail("state callback can only be called twice!");
                    }
                });

        var commandLatch0 = new CountDownLatch(1);
        mIntrusionDetectionManager.enable(
                executor,
                new IntrusionDetectionManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch0.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertThat(commandLatch0.await(1, SECONDS)).isTrue();

        var commandLatch1 = new CountDownLatch(1);
        mIntrusionDetectionManager.enable(
                executor,
                new IntrusionDetectionManager.CommandCallback() {
                    @Override
                    public void onSuccess() {
                        commandLatch1.countDown();
                    }

                    @Override
                    public void onFailure(int error) {
                        fail("onFailure shall not be called");
                    }
                });

        assertThat(commandLatch1.await(1, SECONDS)).isTrue();

        generateSecurityEvent(securityEventTag);

        // Security logs are batched by the DevicePolicyManager. Force the
        // log callback to be sent to SecurityLogSource.
        UiDevice.getInstance(mInstrumentation).executeShellCommand("dpm force-security-logs");

        assertThat(securityEventLatch.await(1, SECONDS)).isTrue();

        mContext.unregisterReceiver(securityEventReceiver);
        executor.close();
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    /** Emits a given string into security log (if enabled). */
    private void generateSecurityEvent(String eventString)
            throws IllegalArgumentException, GeneralSecurityException, IOException {
        if (eventString == null || eventString.isEmpty()) {
            throw new IllegalArgumentException(
                    "Error generating security event: eventString must not be empty");
        }

        final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        keyGen.initialize(
                new KeyGenParameterSpec.Builder(eventString, KeyProperties.PURPOSE_SIGN).build());
        // Emit key generation event.
        final KeyPair keyPair = keyGen.generateKeyPair();
        assertNotNull(keyPair);

        final KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        // Emit key destruction event.
        ks.deleteEntry(eventString);
    }

    public static class IntrusionDetectionBroadcastReceiver extends BroadcastReceiver {

        CountDownLatch mBroadcastLatch;

        public IntrusionDetectionBroadcastReceiver(CountDownLatch broadcastLatch) {
            mBroadcastLatch = broadcastLatch;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mBroadcastLatch.countDown();
        }
    }
}
