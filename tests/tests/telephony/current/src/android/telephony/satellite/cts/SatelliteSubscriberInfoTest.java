/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.telephony.satellite.cts;

import static android.telephony.satellite.SatelliteSubscriberInfo.SUBSCRIBER_ID_TYPE_ICCID;
import static android.telephony.satellite.SatelliteSubscriberInfo.SUBSCRIBER_ID_TYPE_IMSI_MSISDN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.telephony.satellite.SatelliteSubscriberInfo;

import org.junit.Test;

public class SatelliteSubscriberInfoTest {
    private static final String TEST_ICC_ID = "09876543";
    private static final String TEST_ICC_ID2 = "098765432";
    private static final String TEST_APN = "abcd";
    private static final String TEST_APN2 = "abcde";
    private static final int TEST_CARRIER_ID = 1839;
    private static final int TEST_CARRIER_ID2 = 18391;
    private static final int TEST_SUB_ID = 5;
    private static final int TEST_SUB_ID2 = 6;
    private static final int TEST_SUB_ID_TYPE = SUBSCRIBER_ID_TYPE_IMSI_MSISDN;
    private static final int TEST_SUB_ID_TYPE2 = SUBSCRIBER_ID_TYPE_ICCID;

    @Test
    public void testConstructorAndGetters() {
        SatelliteSubscriberInfo satelliteSubscriberInfo =
                new SatelliteSubscriberInfo.Builder()
                        .setSubscriberId(TEST_ICC_ID)
                        .setCarrierId(TEST_CARRIER_ID)
                        .setNiddApn(TEST_APN)
                        .setSubscriptionId(TEST_SUB_ID)
                        .setSubscriberIdType(TEST_SUB_ID_TYPE)
                        .build();

        assertEquals(TEST_ICC_ID, satelliteSubscriberInfo.getSubscriberId());

        assertEquals(TEST_CARRIER_ID, satelliteSubscriberInfo.getCarrierId());

        assertEquals(TEST_APN, satelliteSubscriberInfo.getNiddApn());

        assertEquals(TEST_SUB_ID, satelliteSubscriberInfo.getSubscriptionId());

        assertEquals(TEST_SUB_ID_TYPE, satelliteSubscriberInfo.getSubscriberIdType());
    }

    @Test
    public void testEquals() {
        SatelliteSubscriberInfo satelliteSubscriberInfo1 =
                new SatelliteSubscriberInfo.Builder()
                        .setSubscriberId(TEST_ICC_ID)
                        .setCarrierId(TEST_CARRIER_ID)
                        .setNiddApn(TEST_APN)
                        .setSubscriptionId(TEST_SUB_ID)
                        .setSubscriberIdType(TEST_SUB_ID_TYPE)
                        .build();

        SatelliteSubscriberInfo satelliteSubscriberInfo2 =
                new SatelliteSubscriberInfo.Builder()
                        .setSubscriberId(TEST_ICC_ID)
                        .setCarrierId(TEST_CARRIER_ID)
                        .setNiddApn(TEST_APN)
                        .setSubscriptionId(TEST_SUB_ID)
                        .setSubscriberIdType(TEST_SUB_ID_TYPE)
                        .build();

        assertTrue(satelliteSubscriberInfo1.equals(satelliteSubscriberInfo2));
    }

    @Test
    public void testNotEquals() {
        SatelliteSubscriberInfo satelliteSubscriberInfo1 =
                new SatelliteSubscriberInfo.Builder()
                        .setSubscriberId(TEST_ICC_ID)
                        .setCarrierId(TEST_CARRIER_ID)
                        .setNiddApn(TEST_APN)
                        .setSubscriptionId(TEST_SUB_ID)
                        .setSubscriberIdType(TEST_SUB_ID_TYPE)
                        .build();

        SatelliteSubscriberInfo satelliteSubscriberInfo2 =
                new SatelliteSubscriberInfo.Builder()
                        .setSubscriberId(TEST_ICC_ID2)
                        .setCarrierId(TEST_CARRIER_ID)
                        .setNiddApn(TEST_APN)
                        .setSubscriptionId(TEST_SUB_ID)
                        .setSubscriberIdType(TEST_SUB_ID_TYPE)
                        .build();
        assertFalse(satelliteSubscriberInfo1.equals(satelliteSubscriberInfo2));

        satelliteSubscriberInfo2 =
                new SatelliteSubscriberInfo.Builder()
                        .setSubscriberId(TEST_ICC_ID)
                        .setCarrierId(TEST_CARRIER_ID2)
                        .setNiddApn(TEST_APN)
                        .setSubscriptionId(TEST_SUB_ID)
                        .setSubscriberIdType(TEST_SUB_ID_TYPE)
                        .build();
        assertFalse(satelliteSubscriberInfo1.equals(satelliteSubscriberInfo2));

        satelliteSubscriberInfo2 =
                new SatelliteSubscriberInfo.Builder()
                        .setSubscriberId(TEST_ICC_ID)
                        .setCarrierId(TEST_CARRIER_ID)
                        .setNiddApn(TEST_APN2)
                        .setSubscriptionId(TEST_SUB_ID)
                        .setSubscriberIdType(TEST_SUB_ID_TYPE)
                        .build();
        assertFalse(satelliteSubscriberInfo1.equals(satelliteSubscriberInfo2));

        satelliteSubscriberInfo2 =
                new SatelliteSubscriberInfo.Builder()
                        .setSubscriberId(TEST_ICC_ID)
                        .setCarrierId(TEST_CARRIER_ID)
                        .setNiddApn(TEST_APN)
                        .setSubscriptionId(TEST_SUB_ID2)
                        .setSubscriberIdType(TEST_SUB_ID_TYPE)
                        .build();
        assertFalse(satelliteSubscriberInfo1.equals(satelliteSubscriberInfo2));

        satelliteSubscriberInfo2 =
                new SatelliteSubscriberInfo.Builder()
                        .setSubscriberId(TEST_ICC_ID)
                        .setCarrierId(TEST_CARRIER_ID)
                        .setNiddApn(TEST_APN)
                        .setSubscriptionId(TEST_SUB_ID)
                        .setSubscriberIdType(TEST_SUB_ID_TYPE2)
                        .build();
        assertFalse(satelliteSubscriberInfo1.equals(satelliteSubscriberInfo2));

        satelliteSubscriberInfo2 =
                new SatelliteSubscriberInfo.Builder()
                        .setSubscriberId(TEST_ICC_ID)
                        .setNiddApn(TEST_APN)
                        .setSubscriptionId(TEST_SUB_ID)
                        .setSubscriberIdType(TEST_SUB_ID_TYPE)
                        .build();
        assertFalse(satelliteSubscriberInfo1.equals(satelliteSubscriberInfo2));
    }

    @Test
    public void testParcel() {
        SatelliteSubscriberInfo satelliteSubscriberInfo1 =
                new SatelliteSubscriberInfo.Builder()
                        .setSubscriberId(TEST_ICC_ID)
                        .setCarrierId(TEST_CARRIER_ID)
                        .setNiddApn(TEST_APN)
                        .setSubscriptionId(TEST_SUB_ID)
                        .setSubscriberIdType(TEST_SUB_ID_TYPE)
                        .build();

        Parcel parcel = Parcel.obtain();
        satelliteSubscriberInfo1.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        SatelliteSubscriberInfo satelliteSubscriberInfo2 =
                SatelliteSubscriberInfo.CREATOR.createFromParcel(parcel);

        assertTrue(satelliteSubscriberInfo1.equals(satelliteSubscriberInfo2));
    }
}
