/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.cts;

import static android.bluetooth.BluetoothProfile.A2DP_SINK;
import static android.car.cts.utils.ShellPermissionUtils.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.car.Car;
import android.car.CarProjectionManager;
import android.car.feature.Flags;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.multiuser.annotations.RequireRunNotOnVisibleBackgroundNonProfileUser;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FeatureUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;

/**
 * Contains the tests to prove compliance with android automotive specific projection requirements.
 */
@RequireRunNotOnVisibleBackgroundNonProfileUser(reason = "No Bluetooth support on visible"
            + " background users currently, so skipping tests for"
            + " secondary_user_on_secondary_display.")
@SmallTest
@RequiresDevice
@AppModeFull(reason = "Instant Apps cannot get Bluetooth related permissions")
public final class CarProjectionManagerTest extends AbstractCarTestCase {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock BluetoothProfile.ServiceListener mServiceListener;

    private static final String TAG = "CarProjectionMgrTest";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private static final String BT_DEVICE_ADDRESS = "00:11:22:33:AA:BB";
    private static final Duration PROXY_CONNECTION_TIMEOUT = Duration.ofSeconds(3);
    private final CarProjectionManager.CarProjectionListener mListener = (fromLongPress) -> {};

    // Bluetooth Core objects
    private final BluetoothAdapter mBluetoothAdapter = BlockingBluetoothAdapter.getAdapter();
    private final BluetoothDevice mBluetoothDevice =
            mBluetoothAdapter.getRemoteDevice(BT_DEVICE_ADDRESS);

    private CarProjectionManager mCarProjectionManager;

    @Before
    public void setUp() throws Exception {
        if (DBG) {
            Log.d(
                    TAG,
                    "Setting up Automotive Bluetooth test. Device is "
                            + (FeatureUtil.isAutomotive() ? "" : "not ")
                            + "automotive");
        }

        mCarProjectionManager =
                (CarProjectionManager) getCar().getCarManager(Car.PROJECTION_SERVICE);
        assertThat(mCarProjectionManager).isNotNull();

        assertThat(BlockingBluetoothAdapter.enable()).isTrue();
        assertThat(mBluetoothAdapter.getProfileProxy(mContext, mServiceListener, A2DP_SINK))
                .isTrue();
        verify(mServiceListener, timeout(PROXY_CONNECTION_TIMEOUT.toMillis()))
                .onServiceConnected(eq(A2DP_SINK), any());
    }

    @After
    public void tearDown() {
        if (mCarProjectionManager == null) {
            Log.w(TAG, "mCarProjectionManager is null, skipping tearDown");
            return;
        }
        runWithShellPermissionIdentity(
                () ->
                        mCarProjectionManager.releaseBluetoothProfileInhibit(
                                mBluetoothDevice, A2DP_SINK));
    }

    @ApiTest(apis = {"android.car.CarProjectionManager#isBluetoothProfileInhibited"})
    @RequiresFlagsEnabled(Flags.FLAG_PROJECTION_QUERY_BT_PROFILE_INHIBIT)
    @Test
    public void testIsBluetoothProfileInhibited_default_isNotInhibited() {
        runWithShellPermissionIdentity(() ->
                assertThat(mCarProjectionManager.isBluetoothProfileInhibited(mBluetoothDevice,
                        A2DP_SINK)).isFalse());
    }

    @ApiTest(
            apis = {
                "android.car.CarProjectionManager#requestBluetoothProfileInhibit",
                "android.car.CarProjectionManager#isBluetoothProfileInhibited"
            })
    @RequiresFlagsEnabled(Flags.FLAG_PROJECTION_QUERY_BT_PROFILE_INHIBIT)
    @Test
    public void testIsBluetoothProfileInhibited_inhibitRequested_isInhibited() {
        runWithShellPermissionIdentity(() -> {
            assertThat(mCarProjectionManager.requestBluetoothProfileInhibit(mBluetoothDevice,
                    A2DP_SINK)).isTrue();

            assertThat(mCarProjectionManager.isBluetoothProfileInhibited(mBluetoothDevice,
                    A2DP_SINK)).isTrue();
        });
    }

    @ApiTest(
            apis = {
                "android.car.CarProjectionManager#requestBluetoothProfileInhibit",
                "android.car.CarProjectionManager#releaseBluetoothProfileInhibit",
                "android.car.CarProjectionManager#isBluetoothProfileInhibited"
            })
    @RequiresFlagsEnabled(Flags.FLAG_PROJECTION_QUERY_BT_PROFILE_INHIBIT)
    @Test
    public void testIsBluetoothProfileInhibited_inhibitReleased_isNotInhibited() {
        runWithShellPermissionIdentity(() -> {
            assertThat(mCarProjectionManager.requestBluetoothProfileInhibit(mBluetoothDevice,
                    A2DP_SINK)).isTrue();
            assertThat(mCarProjectionManager.releaseBluetoothProfileInhibit(mBluetoothDevice,
                    A2DP_SINK)).isTrue();

            assertThat(mCarProjectionManager.isBluetoothProfileInhibited(mBluetoothDevice,
                    A2DP_SINK)).isFalse();
        });
    }

    @ApiTest(
            apis = {
                "android.car.CarProjectionManager#registerProjectionListener",
                "android.car.CarProjectionManager#unregisterProjectionListener"
            })
    @EnsureHasPermission(Car.PERMISSION_CAR_PROJECTION)
    @Test
    public void testSetUnsetListeners() throws Exception {
        mCarProjectionManager.registerProjectionListener(
                mListener, CarProjectionManager.PROJECTION_VOICE_SEARCH);
        mCarProjectionManager.unregisterProjectionListener();
    }

    @ApiTest(apis = {"android.car.CarProjectionManager#registerProjectionListener"})
    @EnsureHasPermission(Car.PERMISSION_CAR_PROJECTION)
    @Test
    public void testRegisterListenersHandleBadInput() throws Exception {
        assertThrows(
                NullPointerException.class,
                () ->
                        mCarProjectionManager.registerProjectionListener(
                                null, CarProjectionManager.PROJECTION_VOICE_SEARCH));
    }
}
