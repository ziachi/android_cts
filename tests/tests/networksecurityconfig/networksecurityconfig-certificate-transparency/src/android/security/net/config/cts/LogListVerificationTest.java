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

package android.security.net.config.cts;

import static android.security.net.config.cts.CertificateTransparencyTestUtils.HTTP_OK_RESPONSE_CODE;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.SCT_PROVIDED_DOMAIN;
import static android.security.net.config.cts.CertificateTransparencyTestUtils.isLogListFilePresent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.net.http.X509TrustManagerExtensions;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.Flags;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.X509TrustManager;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled({
    Flags.FLAG_CERTIFICATE_TRANSPARENCY_CONFIGURATION,
    com.android.org.conscrypt.flags.Flags.FLAG_CERTIFICATE_TRANSPARENCY_PLATFORM
})
public class LogListVerificationTest extends BaseTestCase {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testCTVerification_whenLogListPresent_sctDomain_connectionSucceeds()
            throws IOException {
        assumeTrue(isLogListFilePresent());
        URL url = new URL(SCT_PROVIDED_DOMAIN);

        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        urlConnection.connect();

        assertEquals(urlConnection.getResponseCode(), HTTP_OK_RESPONSE_CODE);
        urlConnection.disconnect();
    }

    @Test
    public void testCTVerification_whenLogListAbsent_sctDomain_failsOpen() throws IOException {
        assumeFalse(isLogListFilePresent());
        URL url = new URL(SCT_PROVIDED_DOMAIN);

        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        urlConnection.connect();

        assertEquals(urlConnection.getResponseCode(), HTTP_OK_RESPONSE_CODE);
        urlConnection.disconnect();
    }

    @Test
    public void testX509TrustManagerExtensions_whenLogListPresent_sctDomain_connectionSucceeds()
            throws Exception {
        assumeTrue(isLogListFilePresent());
        URL url = new URL(SCT_PROVIDED_DOMAIN);
        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        // While the connection here already verifies the SCTs, we establish it
        // to collect the certificates and manually call checkServerTrusted.
        urlConnection.connect();
        Certificate[] certs = urlConnection.getServerCertificates();
        X509Certificate leaf = (X509Certificate) certs[0];
        X509TrustManager tm = TestUtils.getDefaultTrustManager();
        X509TrustManagerExtensions tme = new X509TrustManagerExtensions(tm);

        tme.checkServerTrusted(
                new X509Certificate[] {leaf},
                /* ocspData */ null,
                /* tlsSctData */ null,
                "RSA",
                url.getHost());

        urlConnection.disconnect();
    }
}
