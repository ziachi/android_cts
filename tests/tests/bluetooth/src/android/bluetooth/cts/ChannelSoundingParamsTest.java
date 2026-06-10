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

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;
import static android.bluetooth.le.ChannelSoundingParams.CS_SECURITY_LEVEL_ONE;
import static android.bluetooth.le.ChannelSoundingParams.CS_SECURITY_LEVEL_TWO;
import static android.bluetooth.le.ChannelSoundingParams.LOCATION_TYPE_OUTDOOR;
import static android.bluetooth.le.ChannelSoundingParams.LOCATION_TYPE_UNKNOWN;
import static android.bluetooth.le.ChannelSoundingParams.SIGHT_TYPE_LINE_OF_SIGHT;
import static android.bluetooth.le.ChannelSoundingParams.SIGHT_TYPE_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ChannelSoundingParams;
import android.content.Context;
import android.os.Parcel;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ChannelSoundingParamsTest {
    private Context mContext;
    private BluetoothAdapter mAdapter;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        Assume.assumeTrue(SdkLevel.isAtLeastV());
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();
        enforceConnectAndPrivileged(() -> mAdapter.isDistanceMeasurementSupported());
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        Assume.assumeTrue(mAdapter.isDistanceMeasurementSupported() == FEATURE_SUPPORTED);
    }

    @After
    public void tearDown() {
        TestUtils.dropPermissionAsShellUid();
        mAdapter = null;
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createFromParcel() {
        final Parcel parcel = Parcel.obtain();
        try {
            ChannelSoundingParams params =
                    new ChannelSoundingParams.Builder()
                            .setSightType(SIGHT_TYPE_LINE_OF_SIGHT)
                            .setLocationType(LOCATION_TYPE_OUTDOOR)
                            .setCsSecurityLevel(CS_SECURITY_LEVEL_TWO)
                            .build();
            params.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            ChannelSoundingParams paramsFromParcel =
                    ChannelSoundingParams.CREATOR.createFromParcel(parcel);
            assertParamsEquals(params, paramsFromParcel);
        } finally {
            parcel.recycle();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void defaultParameters() {
        ChannelSoundingParams params = new ChannelSoundingParams.Builder().build();
        assertThat(params.getSightType()).isEqualTo(SIGHT_TYPE_UNKNOWN);
        assertThat(params.getLocationType()).isEqualTo(LOCATION_TYPE_UNKNOWN);
        assertThat(params.getCsSecurityLevel()).isEqualTo(CS_SECURITY_LEVEL_ONE);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetSightType() {
        ChannelSoundingParams.Builder builder = new ChannelSoundingParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setSightType(-1));
        ChannelSoundingParams params = builder.setSightType(SIGHT_TYPE_LINE_OF_SIGHT).build();
        assertThat(params.getSightType()).isEqualTo(SIGHT_TYPE_LINE_OF_SIGHT);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetLocationType() {
        ChannelSoundingParams.Builder builder = new ChannelSoundingParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setLocationType(-1));
        ChannelSoundingParams params = builder.setLocationType(LOCATION_TYPE_OUTDOOR).build();
        assertThat(params.getLocationType()).isEqualTo(LOCATION_TYPE_OUTDOOR);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setGetCsSecurityLevel() {
        ChannelSoundingParams.Builder builder = new ChannelSoundingParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setCsSecurityLevel(-1));
        ChannelSoundingParams params = builder.setCsSecurityLevel(CS_SECURITY_LEVEL_TWO).build();
        assertThat(params.getCsSecurityLevel()).isEqualTo(CS_SECURITY_LEVEL_TWO);
    }

    private void assertParamsEquals(ChannelSoundingParams p, ChannelSoundingParams other) {
        assertThat(p).isNotNull();
        assertThat(other).isNotNull();

        assertThat(p.getSightType()).isEqualTo(other.getSightType());
        assertThat(p.getLocationType()).isEqualTo(other.getLocationType());
        assertThat(p.getCsSecurityLevel()).isEqualTo(other.getCsSecurityLevel());
    }

    private void enforceConnectAndPrivileged(ThrowingRunnable runnable) {
        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
        assertThrows(SecurityException.class, runnable);

        // Verify throws SecurityException without permission.BLUETOOTH_CONNECT
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_PRIVILEGED);
        assertThrows(SecurityException.class, runnable);
    }
}
