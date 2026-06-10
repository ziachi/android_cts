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
package android.telephony.cts;

import static org.junit.Assert.assertEquals;

import static java.util.Collections.EMPTY_SET;

import android.telephony.CellIdentity;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityTdscdma;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellLocation;
import android.telephony.gsm.GsmCellLocation;

import org.junit.Test;

/**
 * Test {@link android.telephony.CellIdentity} and its subclasses.
 */
public class CellIdentityTest {
    @Test
    public void testCellIdentityGsm_asCellLocation() {
        int lac = 123;
        int cid = 456;
        CellIdentity cellIdentity =
                new CellIdentityGsm(lac, cid, 0, 0, null, null, null, null, EMPTY_SET);

        CellLocation cellLocation = cellIdentity.asCellLocation();

        GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
        assertEquals(lac, gsmCellLocation.getLac());
        assertEquals(cid, gsmCellLocation.getCid());
        assertEquals(-1, gsmCellLocation.getPsc());
    }

    @Test
    public void testCellIdentityGsm_unavailable_asCellLocation() {
        CellIdentity cellIdentity = new CellIdentityGsm();

        CellLocation cellLocation = cellIdentity.asCellLocation();

        GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
        assertEquals(-1, gsmCellLocation.getLac());
        assertEquals(-1, gsmCellLocation.getCid());
        assertEquals(-1, gsmCellLocation.getPsc());
    }

    @Test
    public void testCellIdentityLte_asCellLocation() {
        int tac = 1;
        int ci = 2;
        CellIdentity cellIdentity = new CellIdentityLte(123, 456, ci, 7, tac);

        CellLocation cellLocation = cellIdentity.asCellLocation();

        GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
        assertEquals(tac, gsmCellLocation.getLac());
        assertEquals(ci, gsmCellLocation.getCid());
        // psc is not supported in LTE so always 0
        assertEquals(0, gsmCellLocation.getPsc());
    }

    @Test
    public void testCellIdentityLte_unavailable_asCellLocation() {
        CellIdentity cellIdentity = new CellIdentityLte();

        CellLocation cellLocation = cellIdentity.asCellLocation();

        GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
        // -1 for unintialized lac and cid
        assertEquals(-1, gsmCellLocation.getLac());
        assertEquals(-1, gsmCellLocation.getCid());
        // psc is not supported in LTE so always 0
        assertEquals(0, gsmCellLocation.getPsc());
    }

    @Test
    public void testCellIdentityNr_asCellLocation() {
        int tac = 1;
        CellIdentity cellIdentity =
                new CellIdentityNr(123, tac, 789, null, null, null, 321, null, null, EMPTY_SET);

        CellLocation cellLocation = cellIdentity.asCellLocation();

        GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
        assertEquals(tac, gsmCellLocation.getLac());
        // NR cid is 36 bits and can't fit into the 32-bit cid in GsmCellLocation, so always -1.
        assertEquals(-1, gsmCellLocation.getCid());
        // psc is not supported in NR so always 0, same as in LTE
        assertEquals(0, gsmCellLocation.getPsc());
    }

    @Test
    public void testCellIdentityTdscdma_asCellLocation() {
        int lac = 123;
        int cid = 456;
        CellIdentity cellIdentity =
                new CellIdentityTdscdma(null, null, lac, cid, 0, 0, null, null, EMPTY_SET, null);

        CellLocation cellLocation = cellIdentity.asCellLocation();

        GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
        assertEquals(lac, gsmCellLocation.getLac());
        assertEquals(cid, gsmCellLocation.getCid());
        assertEquals(-1, gsmCellLocation.getPsc());
    }

    @Test
    public void testCellIdentityTdscdma_unavailable_asCellLocation() {
        CellIdentity cellIdentity = new CellIdentityTdscdma();

        CellLocation cellLocation = cellIdentity.asCellLocation();

        GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
        assertEquals(-1, gsmCellLocation.getLac());
        assertEquals(-1, gsmCellLocation.getCid());
        assertEquals(-1, gsmCellLocation.getPsc());
    }

    @Test
    public void testCellIdentityWcdma_asCellLocation() {
        int lac = 123;
        int cid = 456;
        int psc = 509;
        CellIdentity cellIdentity =
                new CellIdentityWcdma(lac, cid, psc, 0, null, null, null, null, EMPTY_SET, null);

        CellLocation cellLocation = cellIdentity.asCellLocation();

        GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
        assertEquals(lac, gsmCellLocation.getLac());
        assertEquals(cid, gsmCellLocation.getCid());
        assertEquals(psc, gsmCellLocation.getPsc());
    }

    @Test
    public void testCellIdentityWcdma_unavailable_asCellLocation() {
        CellIdentity cellIdentity = new CellIdentityWcdma();

        CellLocation cellLocation = cellIdentity.asCellLocation();

        GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
        assertEquals(-1, gsmCellLocation.getLac());
        assertEquals(-1, gsmCellLocation.getCid());
        assertEquals(-1, gsmCellLocation.getPsc());
    }
}
