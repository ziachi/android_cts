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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public final class BluetoothHciVendorSpecificTest {
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final BluetoothAdapter sAdapter = BlockingBluetoothAdapter.getAdapter();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        assertThat(BlockingBluetoothAdapter.enable()).isTrue();
    }

    @RequiresFlagsEnabled(Flags.FLAG_HCI_VENDOR_SPECIFIC_EXTENSION)
    @Test
    public void register() {
        BluetoothAdapter.BluetoothHciVendorSpecificCallback callback =
                mock(BluetoothAdapter.BluetoothHciVendorSpecificCallback.class);

        // Check permission
        assertThrows(
                SecurityException.class,
                () ->
                        sAdapter.registerBluetoothHciVendorSpecificCallback(
                                Set.of(), sContext.getMainExecutor(), callback));

        // Check nullability
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    NullPointerException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    null, sContext.getMainExecutor(), callback));

            assertThrows(
                    NullPointerException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(), null, callback));

            assertThrows(
                    NullPointerException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(), sContext.getMainExecutor(), null));
        }

        // Check event codes
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(-1, 0x51, 0x60, 0xff),
                                    sContext.getMainExecutor(),
                                    callback));

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(0, 0x52, 0x60, 0xff),
                                    sContext.getMainExecutor(),
                                    callback));

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(0, 0x51, 0x5f, 0xff),
                                    sContext.getMainExecutor(),
                                    callback));

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(0, 0x51, 0x60, 0x100),
                                    sContext.getMainExecutor(),
                                    callback));
        }

        // Check multiple registration
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(0, 0x51, 0x60, 0xff), sContext.getMainExecutor(), callback);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.registerBluetoothHciVendorSpecificCallback(
                                    Set.of(0, 0x51, 0x60, 0xff),
                                    sContext.getMainExecutor(),
                                    mock(
                                            BluetoothAdapter.BluetoothHciVendorSpecificCallback
                                                    .class)));

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_HCI_VENDOR_SPECIFIC_EXTENSION)
    @Test
    public void unregister() {
        BluetoothAdapter.BluetoothHciVendorSpecificCallback callback =
                mock(BluetoothAdapter.BluetoothHciVendorSpecificCallback.class);

        // Check permission
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);
        }

        assertThrows(
                SecurityException.class,
                () -> sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback));

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }

        // Check nullability
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);

            assertThrows(
                    NullPointerException.class,
                    () -> sAdapter.unregisterBluetoothHciVendorSpecificCallback(null));

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }

        // Check unknown unregistration
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback));

            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);

            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            sAdapter.unregisterBluetoothHciVendorSpecificCallback(
                                    mock(
                                            BluetoothAdapter.BluetoothHciVendorSpecificCallback
                                                    .class)));

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }

        // Check multiple unregistration
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback));
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_HCI_VENDOR_SPECIFIC_EXTENSION)
    @Test
    public void sendCommand() {
        BluetoothAdapter.BluetoothHciVendorSpecificCallback callback =
                mock(BluetoothAdapter.BluetoothHciVendorSpecificCallback.class);

        // Check permission
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);
        }

        assertThrows(
                SecurityException.class,
                () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0, new byte[] {}));

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }

        // Check nullability
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);

            assertThrows(
                    NullPointerException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0, null));

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }

        // Check unregistered callbacks
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            assertThrows(
                    IllegalStateException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0, new byte[0]));
        }

        // Check ocf values
        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(-1, new byte[0]));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0x150, new byte[0]));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0x15f, new byte[0]));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0x400, new byte[0]));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> sAdapter.sendBluetoothHciVendorSpecificCommand(0, new byte[256]));

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_HCI_VENDOR_SPECIFIC_EXTENSION)
    @Test
    public void getVendorCapabilities() {
        BluetoothAdapter.BluetoothHciVendorSpecificCallback callback =
                mock(BluetoothAdapter.BluetoothHciVendorSpecificCallback.class);

        ArgumentCaptor<byte[]> return_parameters = ArgumentCaptor.forClass(byte[].class);

        try (var p = Permissions.withPermissions(BLUETOOTH_PRIVILEGED)) {
            sAdapter.registerBluetoothHciVendorSpecificCallback(
                    Set.of(), sContext.getMainExecutor(), callback);

            sAdapter.sendBluetoothHciVendorSpecificCommand(0x153, new byte[] {});

            verify(callback, timeout(1_000))
                    .onCommandComplete(eq(0x153), return_parameters.capture());

            int length_until_version_number = 9;
            assertThat(return_parameters.getValue().length).isAtLeast(length_until_version_number);

            int status = return_parameters.getValue()[0];
            assertThat(status).isEqualTo(0);

            sAdapter.unregisterBluetoothHciVendorSpecificCallback(callback);
        }
    }

    // Android doesn't provide method without side-effect and therefore this is not testable in CTS
    // In order to trigger the callbacks, there is no alternative to a direct call on mock
    @Test
    @SuppressWarnings("DirectInvocationOnMock")
    public void fakeCallbackCoverage() {
        BluetoothAdapter.BluetoothHciVendorSpecificCallback callback =
                mock(BluetoothAdapter.BluetoothHciVendorSpecificCallback.class);

        callback.onCommandStatus(0, 0);
        callback.onEvent(0, null);
    }
}
