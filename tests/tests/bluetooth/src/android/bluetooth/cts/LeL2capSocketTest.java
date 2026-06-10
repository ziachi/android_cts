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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.bluetooth.test_utils.TestUtils.isBleSupported;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.bluetooth.test_utils.Permissions;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class LeL2capSocketTest {
    private static final BluetoothAdapter sAdapter = BlockingBluetoothAdapter.getAdapter();

    @Before
    public void setUp() throws Exception {
        assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        assumeTrue(isBleSupported(InstrumentationRegistry.getInstrumentation().getContext()));

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void openInsecureLeL2capServerSocket() throws IOException {
        assertThrows(SecurityException.class, () -> sAdapter.listenUsingInsecureL2capChannel());
        final BluetoothServerSocket serverSocket;
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            serverSocket = sAdapter.listenUsingInsecureL2capChannel();
        }
        assertThat(serverSocket).isNotNull();
        serverSocket.close();
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void openSecureLeL2capServerSocket() throws IOException {
        assertThrows(SecurityException.class, () -> sAdapter.listenUsingL2capChannel());
        final BluetoothServerSocket serverSocket;
        try (var p = Permissions.withPermissions(BLUETOOTH_CONNECT)) {
            serverSocket = sAdapter.listenUsingL2capChannel();
        }
        assertThat(serverSocket).isNotNull();
        serverSocket.close();
    }
}
