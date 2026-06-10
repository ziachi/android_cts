/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeBroadcastReceiveStateTest {
    private static final int TEST_SOURCE_ID = 42;
    private static final int TEST_SOURCE_ADDRESS_TYPE = BluetoothDevice.ADDRESS_TYPE_RANDOM;
    private static final String TEST_MAC_ADDRESS = "00:11:22:33:44:55";
    private static final int TEST_ADVERTISER_SID = 1234;
    private static final int TEST_BROADCAST_ID = 45;
    private static final int TEST_PA_SYNC_STATE =
            BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_SYNCHRONIZED;
    private static final int TEST_BIG_ENCRYPTION_STATE =
            BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED;
    private static final int TEST_NUM_SUBGROUPS = 1;

    private Context mContext;
    private BluetoothAdapter mAdapter;
    private boolean mIsBroadcastSourceSupported;
    private boolean mIsBroadcastAssistantSupported;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();

        mIsBroadcastAssistantSupported =
                mAdapter.isLeAudioBroadcastAssistantSupported() == FEATURE_SUPPORTED;
        if (mIsBroadcastAssistantSupported) {
            assertThat(TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT))
                    .isTrue();
        }

        mIsBroadcastSourceSupported =
                mAdapter.isLeAudioBroadcastSourceSupported() == FEATURE_SUPPORTED;
        if (mIsBroadcastSourceSupported) {
            assertThat(TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST)).isTrue();
        }

        Assume.assumeTrue(mIsBroadcastAssistantSupported || mIsBroadcastSourceSupported);
    }

    @After
    public void tearDown() {
        mAdapter = null;
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2", "7.4.3/C-9-1"})
    @Test
    public void createBroadcastReceiveState() {
        final Long bisSyncState = 1L;
        final BluetoothLeAudioContentMetadata[] contentMetadata = {null};
        BluetoothDevice testDevice =
                mAdapter.getRemoteLeDevice(TEST_MAC_ADDRESS, TEST_SOURCE_ADDRESS_TYPE);
        BluetoothLeBroadcastReceiveState state =
                createBroadcastReceiveStateForTest(
                        TEST_SOURCE_ID,
                        TEST_SOURCE_ADDRESS_TYPE,
                        testDevice,
                        TEST_ADVERTISER_SID,
                        TEST_BROADCAST_ID,
                        TEST_PA_SYNC_STATE,
                        TEST_BIG_ENCRYPTION_STATE,
                        null /* badCode */,
                        TEST_NUM_SUBGROUPS,
                        List.of(bisSyncState),
                        Arrays.asList(contentMetadata));
        assertThat(state.getSourceId()).isEqualTo(TEST_SOURCE_ID);
        assertThat(state.getSourceAddressType()).isEqualTo(TEST_SOURCE_ADDRESS_TYPE);
        assertThat(state.getSourceDevice()).isEqualTo(testDevice);
        assertThat(state.getSourceAdvertisingSid()).isEqualTo(TEST_ADVERTISER_SID);
        assertThat(state.getBroadcastId()).isEqualTo(TEST_BROADCAST_ID);
        assertThat(state.getPaSyncState()).isEqualTo(TEST_PA_SYNC_STATE);
        assertThat(state.getBigEncryptionState()).isEqualTo(TEST_BIG_ENCRYPTION_STATE);
        assertThat(state.getBadCode()).isNull();
        assertThat(state.getNumSubgroups()).isEqualTo(TEST_NUM_SUBGROUPS);
        assertThat(state.getBisSyncState()).containsExactly(bisSyncState);
        assertThat(state.getSubgroupMetadata()).containsExactly(contentMetadata);
    }

    static BluetoothLeBroadcastReceiveState createBroadcastReceiveStateForTest(
            int sourceId,
            int sourceAddressType,
            BluetoothDevice sourceDevice,
            int sourceAdvertisingSid,
            int broadcastId,
            int paSyncState,
            int bigEncryptionState,
            byte[] badCode,
            int numSubgroups,
            List<Long> bisSyncState,
            List<BluetoothLeAudioContentMetadata> subgroupMetadata) {
        Parcel out = Parcel.obtain();
        out.writeInt(sourceId);
        out.writeInt(sourceAddressType);
        out.writeTypedObject(sourceDevice, 0);
        out.writeInt(sourceAdvertisingSid);
        out.writeInt(broadcastId);
        out.writeInt(paSyncState);
        out.writeInt(bigEncryptionState);
        out.writeByteArray(badCode);
        out.writeInt(numSubgroups);
        out.writeList(bisSyncState);
        out.writeTypedList(subgroupMetadata);
        out.setDataPosition(0); // reset position of parcel before passing to constructor
        return BluetoothLeBroadcastReceiveState.CREATOR.createFromParcel(out);
    }
}
