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
import static android.bluetooth.BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSap;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.content.Context;
import android.sysprop.BluetoothProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class BluetoothSapTest {
    private static final String TAG = BluetoothSapTest.class.getSimpleName();

    @Mock private BluetoothProfile.ServiceListener mListener;

    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofMillis(500);

    private final BluetoothAdapter mAdapter = BlockingBluetoothAdapter.getAdapter();
    private final BluetoothDevice mDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    private BluetoothSap mService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        assumeTrue(mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH));
        assumeTrue(BluetoothProperties.isProfileSapServerEnabled().orElse(false));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();

        assertThat(mAdapter.getProfileProxy(mContext, mListener, BluetoothProfile.SAP)).isTrue();

        ArgumentCaptor<BluetoothProfile> captor = ArgumentCaptor.forClass(BluetoothProfile.class);
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(BluetoothProfile.SAP), captor.capture());
        mService = (BluetoothSap) captor.getValue();
        assertThat(mService).isNotNull();

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    @After
    public void tearDown() {
        mAdapter.closeProfileProxy(BluetoothProfile.SAP, mService);
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void closeProfileProxy() {
        mService.close();
        verify(mListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceDisconnected(eq(BluetoothProfile.SAP));
    }

    @Test
    @MediumTest
    public void getConnectedDevices() {
        assertThat(mService.getConnectedDevices()).isEmpty();

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mService.getConnectedDevices());
    }

    @Test
    @MediumTest
    public void getDevicesMatchingConnectionStates() {
        int[] connectionState = new int[] {STATE_CONNECTED};

        assertThat(mService.getDevicesMatchingConnectionStates(connectionState)).isEmpty();

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(
                SecurityException.class,
                () -> mService.getDevicesMatchingConnectionStates(connectionState));
    }

    @Test
    @MediumTest
    public void getConnectionState() {
        assertThat(mService.getConnectionState(mDevice)).isEqualTo(STATE_DISCONNECTED);

        mUiAutomation.dropShellPermissionIdentity();
        assertThat(mService.getConnectionState(mDevice)).isEqualTo(STATE_DISCONNECTED);
    }

    @Test
    @MediumTest
    public void setGetConnectionPolicy() {
        assertThrows(NullPointerException.class, () -> mService.setConnectionPolicy(null, 0));
        assertThrows(NullPointerException.class, () -> mService.getConnectionPolicy(null));

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(
                SecurityException.class,
                () -> mService.setConnectionPolicy(mDevice, CONNECTION_POLICY_FORBIDDEN));
        assertThrows(SecurityException.class, () -> mService.getConnectionPolicy(mDevice));
    }
}
