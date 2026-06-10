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

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.UidTraffic;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class UidTrafficTest {

    private UidTraffic mUidTraffic;

    @Before
    public void setUp() {
        final Parcel uidTrafficParcel = Parcel.obtain();
        uidTrafficParcel.writeInt(1000);
        uidTrafficParcel.writeLong(2000);
        uidTrafficParcel.writeLong(3000);
        uidTrafficParcel.setDataPosition(0);
        mUidTraffic = UidTraffic.CREATOR.createFromParcel(uidTrafficParcel);
        assertThat(mUidTraffic).isNotNull();
        uidTrafficParcel.recycle();
    }

    @Test
    public void cloneMethod() {
        UidTraffic clonedUidTraffic = mUidTraffic.clone();
        assertThat(clonedUidTraffic).isNotNull();
        assertThat(clonedUidTraffic.getUid()).isEqualTo(mUidTraffic.getUid());
        assertThat(clonedUidTraffic.getRxBytes()).isEqualTo(mUidTraffic.getRxBytes());
        assertThat(clonedUidTraffic.getTxBytes()).isEqualTo(mUidTraffic.getTxBytes());
    }

    @Test
    public void getMethod() {
        assertThat(mUidTraffic.getUid()).isEqualTo(1000);
        assertThat(mUidTraffic.getRxBytes()).isEqualTo(2000);
        assertThat(mUidTraffic.getTxBytes()).isEqualTo(3000);
    }
}
