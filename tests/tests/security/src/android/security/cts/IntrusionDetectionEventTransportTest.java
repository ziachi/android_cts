/**
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

package android.security.cts;

import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;
import android.security.intrusiondetection.IntrusionDetectionEventTransport;
import android.security.intrusiondetection.IntrusionDetectionEvent;
import android.security.intrusiondetection.IIntrusionDetectionEventTransport;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;


@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot access the API")
public class IntrusionDetectionEventTransportTest {

    private IntrusionDetectionEventTransport intrusionDetectionEventTransport;

    @Before
    public void setUp() throws Exception {
        intrusionDetectionEventTransport = new IntrusionDetectionEventTransport();
    }

    @Test
    public void testGetBinder_returnsNonNullBinder() {
        assertNotNull(intrusionDetectionEventTransport.getBinder());
    }

    @Test
    public void testInitialize_returnsFalse() {
        boolean result = intrusionDetectionEventTransport.initialize();
        assertFalse(result);
    }

    @Test
    public void testAddData_returnsFalse() {
        List<IntrusionDetectionEvent> events = new ArrayList<>();

        boolean result = intrusionDetectionEventTransport.addData(events);
        assertFalse(result);
    }

    @Test
    public void testRelease_returnsFalse() {
        boolean result = intrusionDetectionEventTransport.release();
        assertFalse(result);
    }
}