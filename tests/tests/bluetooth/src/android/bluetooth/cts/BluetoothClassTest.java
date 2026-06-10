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

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothClass;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test cases for {@link BluetoothClass}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothClassTest {

    private BluetoothClass mBluetoothClassHeadphones;
    private BluetoothClass mBluetoothClassPhone;
    private BluetoothClass mBluetoothClassService;

    private BluetoothClass createBtClass(int deviceClass) {
        Parcel p = Parcel.obtain();
        p.writeInt(deviceClass);
        p.setDataPosition(0); // reset position of parcel before passing to constructor

        BluetoothClass bluetoothClass = BluetoothClass.CREATOR.createFromParcel(p);
        p.recycle();
        return bluetoothClass;
    }

    @Before
    public void setUp() {
        mBluetoothClassHeadphones = createBtClass(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES);
        mBluetoothClassPhone = createBtClass(BluetoothClass.Device.Major.PHONE);
        mBluetoothClassService = createBtClass(BluetoothClass.Service.NETWORKING);
    }

    @Test
    public void hasService() {
        assertThat(mBluetoothClassService.hasService(BluetoothClass.Service.NETWORKING)).isTrue();
        assertThat(mBluetoothClassService.hasService(BluetoothClass.Service.TELEPHONY)).isFalse();
    }

    @Test
    public void getMajorDeviceClass() {
        assertThat(mBluetoothClassHeadphones.getMajorDeviceClass())
                .isEqualTo(BluetoothClass.Device.Major.AUDIO_VIDEO);
        assertThat(mBluetoothClassPhone.getMajorDeviceClass())
                .isEqualTo(BluetoothClass.Device.Major.PHONE);
    }

    @Test
    public void getDeviceClass() {
        assertThat(mBluetoothClassHeadphones.getDeviceClass())
                .isEqualTo(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES);
        assertThat(mBluetoothClassPhone.getDeviceClass())
                .isEqualTo(BluetoothClass.Device.PHONE_UNCATEGORIZED);
    }

    @Test
    public void getClassOfDevice() {
        assertThat(mBluetoothClassHeadphones.getDeviceClass())
                .isEqualTo(BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES);
        assertThat(mBluetoothClassPhone.getMajorDeviceClass())
                .isEqualTo(BluetoothClass.Device.Major.PHONE);
    }

    @Test
    public void doesClassMatch() {
        assertThat(mBluetoothClassHeadphones.doesClassMatch(BluetoothClass.PROFILE_A2DP)).isTrue();
        assertThat(mBluetoothClassHeadphones.doesClassMatch(BluetoothClass.PROFILE_HEADSET))
                .isFalse();

        assertThat(mBluetoothClassPhone.doesClassMatch(BluetoothClass.PROFILE_OPP)).isTrue();
        assertThat(mBluetoothClassPhone.doesClassMatch(BluetoothClass.PROFILE_HEADSET)).isFalse();

        assertThat(mBluetoothClassService.doesClassMatch(BluetoothClass.PROFILE_PANU)).isTrue();
        assertThat(mBluetoothClassService.doesClassMatch(BluetoothClass.PROFILE_OPP)).isFalse();
    }

    @Test
    public void innerClasses() {
        // Just instantiate static inner classes for exposing constants
        // to make test coverage tool happy.
        BluetoothClass.Device device = new BluetoothClass.Device();
        BluetoothClass.Device.Major major = new BluetoothClass.Device.Major();
        BluetoothClass.Service service = new BluetoothClass.Service();
    }
}
