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

import android.app.admin.ConnectEvent;
import android.app.admin.DnsEvent;
import android.app.admin.SecurityLog.SecurityEvent;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;
import android.security.intrusiondetection.IntrusionDetectionEventTransport;
import android.security.intrusiondetection.IntrusionDetectionEvent;
import android.security.intrusiondetection.IIntrusionDetectionEventTransport;
import android.util.EventLog;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;


@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot access the API")
public class IntrusionDetectionEventTest {

    private SecurityEvent securityEvent;
    private DnsEvent dnsEvent;
    private ConnectEvent connectEvent;

    @Before
    public void setUp() {
        byte[] data = {10, 20, 30, 40, 50};
        securityEvent = new SecurityEvent(123, data);
        dnsEvent = new DnsEvent(
            "www.example.com",
            new String[]{"192.168.1.1", "10.0.0.1"},
            2,
            "com.example.app",
            System.currentTimeMillis()
        );
        connectEvent = new ConnectEvent(
            "www.example.com",
            25,
            "com.example.app",
            System.currentTimeMillis()
        );
    }

    @Test
    public void testGetters_returnCorrectSecurityEvent() {
        IntrusionDetectionEvent event =
                IntrusionDetectionEvent.createForSecurityEvent(securityEvent);

        assertEquals(securityEvent, event.getSecurityEvent());
    }

    @Test
    public void testGetters_returnCorrectDnsEvent() {
        IntrusionDetectionEvent event = IntrusionDetectionEvent.createForDnsEvent(dnsEvent);

        assertEquals(dnsEvent, event.getDnsEvent());
    }

    @Test
    public void testGetters_returnCorrectConnectEvent() {
        IntrusionDetectionEvent event = IntrusionDetectionEvent.createForConnectEvent(connectEvent);

        assertEquals(connectEvent, event.getConnectEvent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetSecurityEvent_wrongType_throwsException() {
        IntrusionDetectionEvent event = IntrusionDetectionEvent.createForDnsEvent(dnsEvent);
        event.getSecurityEvent();
    }

    @Test
    public void getType_returnsCorrectTypes() {
        IntrusionDetectionEvent event1 =
                IntrusionDetectionEvent.createForSecurityEvent(securityEvent);
        IntrusionDetectionEvent event2 = IntrusionDetectionEvent.createForDnsEvent(dnsEvent);
        IntrusionDetectionEvent event3 =
                IntrusionDetectionEvent.createForConnectEvent(connectEvent);

        assertEquals(IntrusionDetectionEvent.SECURITY_EVENT, event1.getType());
        assertEquals(IntrusionDetectionEvent.NETWORK_EVENT_DNS, event2.getType());
        assertEquals(IntrusionDetectionEvent.NETWORK_EVENT_CONNECT, event3.getType());
    }

    @Test
    public void testDescribeContents_returnsZero() {
        IntrusionDetectionEvent event =
                IntrusionDetectionEvent.createForSecurityEvent(securityEvent);
        assertEquals(0, event.describeContents());
    }

    @Test
    public void testToString_returnsExpectedFormat() {
        IntrusionDetectionEvent event =
                IntrusionDetectionEvent.createForSecurityEvent(securityEvent);
        String expectedString = "IntrusionDetectionEvent{mType=0}";
        assertEquals(expectedString, event.toString());
    }

    @Test
    public void testNewArray() {
        IntrusionDetectionEvent[] events = IntrusionDetectionEvent.CREATOR.newArray(5);
        assertEquals(5, events.length);
    }
}