/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.bluetooth.BluetoothDevice.ACCESS_ALLOWED;
import static android.bluetooth.BluetoothDevice.ACCESS_REJECTED;
import static android.bluetooth.BluetoothDevice.ACCESS_UNKNOWN;
import static android.bluetooth.BluetoothDevice.TRANSPORT_AUTO;
import static android.bluetooth.BluetoothDevice.TRANSPORT_BREDR;
import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevice.BluetoothAddress;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothSocketException;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.OobData;
import android.bluetooth.test_utils.Permissions;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.CddTest;

import org.hamcrest.Matcher;
import org.hamcrest.core.AllOf;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.hamcrest.MockitoHamcrest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothDeviceTest {

    private Context mContext;
    private boolean mHasBluetooth;
    private boolean mHasCompanionDevice;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;

    private final String mFakeDeviceAddress = "00:11:22:AA:BB:CC";
    private BluetoothDevice mFakeDevice;
    private int mFakePsm = 100;
    private UUID mFakeUuid = UUID.fromString("0000111E-0000-1000-8000-00805F9B34FB");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mHasBluetooth =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);

        mHasCompanionDevice =
                mContext.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP);

        if (mHasBluetooth && mHasCompanionDevice) {
            BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
            mAdapter = manager.getAdapter();
            mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
            assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();
            mFakeDevice = mAdapter.getRemoteDevice(mFakeDeviceAddress);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mHasBluetooth && mHasCompanionDevice) {
            mAdapter = null;
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void setAlias_getAlias() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        int userId = mContext.getUser().getIdentifier();
        String packageName = mContext.getOpPackageName();

        AttributionSource source = AttributionSource.myAttributionSource();
        assertThat(source.getPackageName()).isEqualTo("android.bluetooth.cts");

        // Verifies that when there is no alias, we return the device name
        assertThat(mFakeDevice.getAlias()).isNull();

        assertThrows(IllegalArgumentException.class, () -> mFakeDevice.setAlias(""));

        String testDeviceAlias = "Test Device Alias";

        // This should throw a SecurityException because there is no CDM association
        assertThrows(
                "BluetoothDevice.setAlias without"
                        + " a CDM association or BLUETOOTH_PRIVILEGED permission",
                SecurityException.class,
                () -> mFakeDevice.setAlias(testDeviceAlias));

        runShellCommand(
                String.format(
                        "cmd companiondevice associate %d %s %s",
                        userId, packageName, mFakeDeviceAddress));
        String output = runShellCommand("dumpsys companiondevice");
        assertThat(output).contains(packageName);
        assertThat(output.toLowerCase()).contains(mFakeDeviceAddress.toLowerCase());

        // Takes time to update the CDM cache, so sleep to ensure the association is cached
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*
         * Device properties don't exist for non-existent BluetoothDevice, so calling setAlias with
         * permissions should return false
         */
        assertThat(mFakeDevice.setAlias(testDeviceAlias))
                .isEqualTo(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED);
        runShellCommand(
                String.format(
                        "cmd companiondevice disassociate %d %s %s",
                        userId, packageName, mFakeDeviceAddress));

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mFakeDevice.getAlias()).isNull();
        assertThat(mFakeDevice.setAlias(testDeviceAlias))
                .isEqualTo(BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED);
    }

    @Test
    public void getAddressType() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.getAddressType()).isEqualTo(BluetoothDevice.ADDRESS_TYPE_PUBLIC);
    }

    @Test
    public void getIdentityAddress() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                "No BLUETOOTH_PRIVILEGED permission",
                SecurityException.class,
                () -> mFakeDevice.getIdentityAddress());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_IDENTITY_ADDRESS_TYPE_API)
    public void getIdentityAddressWithType() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                "No BLUETOOTH_PRIVILEGED permission",
                SecurityException.class,
                () -> mFakeDevice.getIdentityAddressWithType());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_IDENTITY_ADDRESS_TYPE_API)
    public void testBluetoothAddress() {
        int addressType = BluetoothDevice.ADDRESS_TYPE_PUBLIC;
        BluetoothAddress bluetoothAddress = new BluetoothAddress(mFakeDeviceAddress, addressType);

        assertThat(bluetoothAddress.getAddress()).isEqualTo(mFakeDeviceAddress);
        assertThat(bluetoothAddress.getAddressType()).isEqualTo(addressType);
    }

    @Test
    public void getConnectionHandle() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                "No BLUETOOTH_PRIVILEGED permission",
                SecurityException.class,
                () -> mFakeDevice.getConnectionHandle(TRANSPORT_LE));

        // but it should work after we get the permission
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertThat(mFakeDevice.getConnectionHandle(TRANSPORT_LE)).isEqualTo(BluetoothDevice.ERROR);
    }

    @Test
    public void getAnonymizedAddress() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.getAnonymizedAddress()).isEqualTo("XX:XX:XX:XX:BB:CC");
    }

    @Test
    public void getBatteryLevel() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.getBatteryLevel()).isEqualTo(BluetoothDevice.BATTERY_LEVEL_UNKNOWN);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.getBatteryLevel());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mFakeDevice.getBatteryLevel())
                .isEqualTo(BluetoothDevice.BATTERY_LEVEL_BLUETOOTH_OFF);
    }

    @Test
    public void isBondingInitiatedLocally() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.isBondingInitiatedLocally()).isFalse();

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.isBondingInitiatedLocally());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mFakeDevice.isBondingInitiatedLocally()).isFalse();
    }

    @Test
    public void prepareToEnterProcess() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        mFakeDevice.prepareToEnterProcess(null);
    }

    @Test
    public void setPin() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.setPin((String) null)).isFalse();
        assertThat(mFakeDevice.setPin("12345678901234567")).isFalse(); // check PIN too big

        assertThat(mFakeDevice.setPin("123456")).isFalse(); // device is not bonding

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.setPin("123456"));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mFakeDevice.setPin("123456")).isFalse();
    }

    @Test
    public void connect_disconnect() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice.connect());
        assertThrows(SecurityException.class, () -> mFakeDevice.disconnect());
    }

    @Test
    public void cancelBondProcess() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.cancelBondProcess());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mFakeDevice.cancelBondProcess()).isFalse();
    }

    @Test
    public void createBond() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.createBond(TRANSPORT_AUTO));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mFakeDevice.createBond(TRANSPORT_AUTO)).isFalse();
    }

    @Test
    public void createBondOutOfBand() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        OobData data = new OobData.ClassicBuilder(new byte[16], new byte[2], new byte[7]).build();

        assertThrows(
                IllegalArgumentException.class,
                () -> mFakeDevice.createBondOutOfBand(TRANSPORT_AUTO, null, null));

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.createBondOutOfBand(TRANSPORT_AUTO, data, null));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    @Test
    public void getUuids() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.getUuids()).isNull();
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.getUuids());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mFakeDevice.getUuids()).isNull();
    }

    @Test
    public void isEncrypted() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // Device is not connected
        assertThat(mFakeDevice.isEncrypted()).isFalse();

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.isEncrypted());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mFakeDevice.isEncrypted()).isFalse();
    }

    @Test
    public void removeBond() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // Device is not bonded
        assertThat(mFakeDevice.removeBond()).isFalse();

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mFakeDevice.removeBond());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mFakeDevice.removeBond()).isFalse();
    }

    @Test
    public void setPinByteArray() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThrows(NullPointerException.class, () -> mFakeDevice.setPin((byte[]) null));

        // check PIN too big
        assertThat(mFakeDevice.setPin(convertPinToBytes("12345678901234567"))).isFalse();
        assertThat(mFakeDevice.setPin(convertPinToBytes("123456")))
                .isFalse(); // device is not bonding

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(
                SecurityException.class, () -> mFakeDevice.setPin(convertPinToBytes("123456")));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mFakeDevice.setPin(convertPinToBytes("123456"))).isFalse();
    }

    @Test
    public void connectGatt() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThrows(
                NullPointerException.class,
                () ->
                        mFakeDevice.connectGatt(
                                mContext,
                                false,
                                null,
                                TRANSPORT_AUTO,
                                BluetoothDevice.PHY_LE_1M_MASK));

        assertThrows(
                NullPointerException.class,
                () ->
                        mFakeDevice.connectGatt(
                                mContext,
                                false,
                                null,
                                TRANSPORT_AUTO,
                                BluetoothDevice.PHY_LE_1M_MASK,
                                null));
    }

    @Test
    public void fetchUuidsWithSdp() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThat(mFakeDevice.fetchUuidsWithSdp()).isTrue();

        // TRANSPORT_AUTO doesn't need BLUETOOTH_PRIVILEGED permission
        assertThat(mFakeDevice.fetchUuidsWithSdp(TRANSPORT_AUTO)).isTrue();

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mFakeDevice.fetchUuidsWithSdp(TRANSPORT_BREDR));
        assertThrows(SecurityException.class, () -> mFakeDevice.fetchUuidsWithSdp(TRANSPORT_LE));

        assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mFakeDevice.fetchUuidsWithSdp(TRANSPORT_AUTO)).isFalse();
    }

    @Test
    public void messageAccessPermission() {
        // Skip the test if bluetooth or companion device are not present
        // or if MAP is not enabled.
        assumeTrue(
                mHasBluetooth
                        && mHasCompanionDevice
                        && TestUtils.isProfileEnabled(BluetoothProfile.MAP));

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.setMessageAccessPermission(ACCESS_ALLOWED));
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.setMessageAccessPermission(ACCESS_UNKNOWN));
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.setMessageAccessPermission(ACCESS_REJECTED));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Should be able to set permissions after adopting the BLUETOOTH_PRIVILEGED permission
        assertThat(mFakeDevice.setMessageAccessPermission(ACCESS_UNKNOWN)).isTrue();
        assertThat(mFakeDevice.getMessageAccessPermission()).isEqualTo(ACCESS_UNKNOWN);
        assertThat(mFakeDevice.setMessageAccessPermission(ACCESS_ALLOWED)).isTrue();
        assertThat(mFakeDevice.getMessageAccessPermission()).isEqualTo(ACCESS_ALLOWED);
        assertThat(mFakeDevice.setMessageAccessPermission(ACCESS_REJECTED)).isTrue();
        assertThat(mFakeDevice.getMessageAccessPermission()).isEqualTo(ACCESS_REJECTED);
    }

    @Test
    public void phonebookAccessPermission() {
        // Skip the test if bluetooth or companion device are not present
        // or if PBAP is not enabled.
        assumeTrue(
                mHasBluetooth
                        && mHasCompanionDevice
                        && TestUtils.isProfileEnabled(BluetoothProfile.PBAP));

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.setPhonebookAccessPermission(ACCESS_ALLOWED));
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.setPhonebookAccessPermission(ACCESS_UNKNOWN));
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.setPhonebookAccessPermission(ACCESS_REJECTED));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Should be able to set permissions after adopting the BLUETOOTH_PRIVILEGED permission
        assertThat(mFakeDevice.setPhonebookAccessPermission(ACCESS_UNKNOWN)).isTrue();
        assertThat(mFakeDevice.getPhonebookAccessPermission()).isEqualTo(ACCESS_UNKNOWN);
        assertThat(mFakeDevice.setPhonebookAccessPermission(ACCESS_ALLOWED)).isTrue();
        assertThat(mFakeDevice.getPhonebookAccessPermission()).isEqualTo(ACCESS_ALLOWED);
        assertThat(mFakeDevice.setPhonebookAccessPermission(ACCESS_REJECTED)).isTrue();
        assertThat(mFakeDevice.getPhonebookAccessPermission()).isEqualTo(ACCESS_REJECTED);
    }

    @Test
    public void simAccessPermission() {
        // Skip the test if bluetooth or companion device are not present
        // or if SAP is not enabled.
        assumeTrue(
                mHasBluetooth
                        && mHasCompanionDevice
                        && TestUtils.isProfileEnabled(BluetoothProfile.SAP));

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                SecurityException.class, () -> mFakeDevice.setSimAccessPermission(ACCESS_ALLOWED));
        assertThrows(
                SecurityException.class, () -> mFakeDevice.setSimAccessPermission(ACCESS_UNKNOWN));
        assertThrows(
                SecurityException.class, () -> mFakeDevice.setSimAccessPermission(ACCESS_REJECTED));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Should be able to set permissions after adopting the BLUETOOTH_PRIVILEGED permission
        assertThat(mFakeDevice.setSimAccessPermission(ACCESS_UNKNOWN)).isTrue();
        assertThat(mFakeDevice.getSimAccessPermission()).isEqualTo(ACCESS_UNKNOWN);
        assertThat(mFakeDevice.setSimAccessPermission(ACCESS_ALLOWED)).isTrue();
        assertThat(mFakeDevice.getSimAccessPermission()).isEqualTo(ACCESS_ALLOWED);
        assertThat(mFakeDevice.setSimAccessPermission(ACCESS_REJECTED)).isTrue();
        assertThat(mFakeDevice.getSimAccessPermission()).isEqualTo(ACCESS_REJECTED);
    }

    @Test
    public void isRequestAudioPolicyAsSinkSupported() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        assertThrows(
                SecurityException.class, () -> mFakeDevice.isRequestAudioPolicyAsSinkSupported());

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(mFakeDevice.isRequestAudioPolicyAsSinkSupported())
                .isEqualTo(BluetoothStatusCodes.FEATURE_NOT_CONFIGURED);
    }

    @Test
    public void setGetAudioPolicy() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        BluetoothSinkAudioPolicy demoAudioPolicy = new BluetoothSinkAudioPolicy.Builder().build();

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(
                SecurityException.class,
                () -> mFakeDevice.requestAudioPolicyAsSink(demoAudioPolicy));
        assertThrows(SecurityException.class, () -> mFakeDevice.getRequestedAudioPolicyAsSink());

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(mFakeDevice.requestAudioPolicyAsSink(demoAudioPolicy))
                .isEqualTo(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED);
        assertThat(mFakeDevice.getRequestedAudioPolicyAsSink()).isNull();

        BluetoothSinkAudioPolicy newPolicy =
                new BluetoothSinkAudioPolicy.Builder(demoAudioPolicy)
                        .setCallEstablishPolicy(BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                        .setActiveDevicePolicyAfterConnection(
                                BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED)
                        .setInBandRingtonePolicy(BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                        .build();

        assertThat(mFakeDevice.requestAudioPolicyAsSink(newPolicy))
                .isEqualTo(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED);
        assertThat(mFakeDevice.getRequestedAudioPolicyAsSink()).isNull();

        assertThat(newPolicy.getCallEstablishPolicy())
                .isEqualTo(BluetoothSinkAudioPolicy.POLICY_ALLOWED);
        assertThat(newPolicy.getActiveDevicePolicyAfterConnection())
                .isEqualTo(BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED);
        assertThat(newPolicy.getInBandRingtonePolicy())
                .isEqualTo(BluetoothSinkAudioPolicy.POLICY_ALLOWED);
    }

    private byte[] convertPinToBytes(String pin) {
        if (pin == null) {
            return null;
        }
        byte[] pinBytes;
        try {
            pinBytes = pin.getBytes("UTF-8");
        } catch (UnsupportedEncodingException uee) {
            return null;
        }
        return pinBytes;
    }

    @Test
    public void getPackageNameOfBondingApplication() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        BroadcastReceiver mockReceiver = mock(BroadcastReceiver.class);
        mContext.registerReceiver(mockReceiver, filter);

        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(
                SecurityException.class, () -> mFakeDevice.getPackageNameOfBondingApplication());
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        assertThrows(
                SecurityException.class, () -> mFakeDevice.getPackageNameOfBondingApplication());

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT);
        // Since no application actually start bonding with this device, this should return null
        assertThat(mFakeDevice.getPackageNameOfBondingApplication()).isNull();

        mFakeDevice.createBond();
        assertThat(mFakeDevice.getPackageNameOfBondingApplication())
                .isEqualTo(mContext.getPackageName());
        verifyIntentReceived(
                mockReceiver,
                Duration.ofSeconds(5),
                hasAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
                hasExtra(BluetoothDevice.EXTRA_DEVICE, mFakeDevice),
                hasExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDING));

        // Clean up create bond
        // Either cancel the bonding process or remove bond
        mFakeDevice.cancelBondProcess();
        mFakeDevice.removeBond();
        verifyIntentReceived(
                mockReceiver,
                Duration.ofSeconds(5),
                hasAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
                hasExtra(BluetoothDevice.EXTRA_DEVICE, mFakeDevice),
                hasExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE));
    }

    @Test
    public void setActiveAudioDevicePolicy_getActiveAudioDevicePolicy() {
        if (!mHasBluetooth || !mHasCompanionDevice) {
            // Skip the test if bluetooth or companion device are not present.
            return;
        }
        String deviceAddress = "00:11:22:AA:AA:AA";
        BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress);

        // This should throw a SecurityException because no BLUETOOTH_CONNECT permission
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED);
        assertThrows(
                SecurityException.class,
                () ->
                        device.setActiveAudioDevicePolicy(
                                BluetoothDevice
                                        .ACTIVE_AUDIO_DEVICE_POLICY_ALL_PROFILES_INACTIVE_UPON_CONNECTION));
        assertThrows(SecurityException.class, () -> device.getActiveAudioDevicePolicy());

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        assertThrows(
                SecurityException.class,
                () ->
                        device.setActiveAudioDevicePolicy(
                                BluetoothDevice
                                        .ACTIVE_AUDIO_DEVICE_POLICY_ALL_PROFILES_INACTIVE_UPON_CONNECTION));
        assertThrows(SecurityException.class, () -> device.getActiveAudioDevicePolicy());

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertThat(device.getActiveAudioDevicePolicy())
                .isEqualTo(BluetoothDevice.ACTIVE_AUDIO_DEVICE_POLICY_DEFAULT);
        assertThat(
                        device.setActiveAudioDevicePolicy(
                                BluetoothDevice
                                        .ACTIVE_AUDIO_DEVICE_POLICY_ALL_PROFILES_INACTIVE_UPON_CONNECTION))
                .isEqualTo(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED);
    }

    @RequiresFlagsEnabled(Flags.FLAG_BT_SOCKET_API_L2CAP_CID)
    @Test
    public void getL2capChannel() throws IOException {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        BluetoothSocket l2capSocket = mFakeDevice.createInsecureL2capChannel(mFakePsm);
        BluetoothSocket rfcommSocket =
                mFakeDevice.createInsecureRfcommSocketToServiceRecord(mFakeUuid);

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // This should throw a BluetoothSocketException because it is not L2CAP socket
        assertThrows(
                "Unknown L2CAP socket",
                BluetoothSocketException.class,
                () -> rfcommSocket.getL2capLocalChannelId());
        assertThrows(
                "Unknown L2CAP socket",
                BluetoothSocketException.class,
                () -> rfcommSocket.getL2capRemoteChannelId());

        // This should throw a BluetoothSocketException because L2CAP socket is not connected
        assertThrows(
                "Socket closed",
                BluetoothSocketException.class,
                () -> l2capSocket.getL2capLocalChannelId());
        assertThrows(
                "Socket closed",
                BluetoothSocketException.class,
                () -> l2capSocket.getL2capRemoteChannelId());
    }

    @RequiresFlagsEnabled(Flags.FLAG_METADATA_API_MICROPHONE_FOR_CALL_ENABLED)
    @Test
    public void setMicrophonePreferredForCalls_isMicrophonePreferredForCalls() {
        // Skip the test if bluetooth or companion device are not present.
        assumeTrue(mHasBluetooth && mHasCompanionDevice);

        // Use alternate address to prevent another test from having unwanted consequences here
        mFakeDevice = mAdapter.getRemoteDevice("AB:11:22:AA:BB:CC");
        Permissions.enforceEachPermissions(
                () -> mFakeDevice.setMicrophonePreferredForCalls(false),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));
        Permissions.enforceEachPermissions(
                () -> mFakeDevice.isMicrophonePreferredForCalls(),
                List.of(BLUETOOTH_PRIVILEGED, BLUETOOTH_CONNECT));

        // default value should be true
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED)) {
            assertThat(mFakeDevice.isMicrophonePreferredForCalls()).isTrue();
            assertThat(mFakeDevice.setMicrophonePreferredForCalls(true))
                    .isEqualTo(BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED);
        }
    }

    private void verifyIntentReceived(
            BroadcastReceiver receiver, Duration timeout, Matcher<Intent>... matchers) {
        verify(receiver, timeout(timeout.toMillis()))
                .onReceive(any(), MockitoHamcrest.argThat(AllOf.allOf(matchers)));
    }
}
