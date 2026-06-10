/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.test.AndroidTestCase;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * End to end version of org.conscrypt.CertBlocklistTest that tests the platform default
 * {@link X509TrustManager}.
 *
 * The test blocklisted CA's private key can be found in
 * external/conscrypt/src/test/resources/blocklist_ca_key.pem
 */
public class CertBlocklistTest extends AndroidTestCase {

    private static final int BLOCKLIST_CA = R.raw.test_blocklist_ca;
    private static final int BLOCKLIST_DIGINOTAR = R.raw.blocklist_diginotar;
    private static final int BLOCKLISTED_CHAIN = R.raw.blocklist_test_chain;
    private static final int BLOCKLIST_FALLBACK_VALID_CA = R.raw.blocklist_test_valid_ca;
    private static final int BLOCKLISTED_VALID_CHAIN = R.raw.blocklist_test_valid_chain;

    /**
     * Checks that the blocklisted CA is rejected even if it used as a root of trust
     */
    public void testBlocklistedCaUntrusted() throws Exception {
        X509Certificate blocklistedCa = loadCertificate(BLOCKLIST_CA);
        assertUntrusted(new X509Certificate[] {blocklistedCa}, getTrustManager(blocklistedCa));
    }

    /**
     * Checks that a known compromised CA certificate is blocked.
     */
    public void testBlocklistedDiginotar() throws Exception {
        // Public Key SHA1 = 410f36363258f30b347d12ce4863e433437806a8
        X509Certificate blocklistedCa = loadCertificate(BLOCKLIST_DIGINOTAR);
        assertUntrusted(new X509Certificate[] {blocklistedCa}, getTrustManager(blocklistedCa));
    }

    /**
     * Checks that a chain that is rooted in a blocklisted trusted CA is rejected.
     */
    public void testBlocklistedRootOfTrust() throws Exception {
        // Chain is leaf -> blocklisted
        X509Certificate[] chain = loadCertificates(BLOCKLISTED_CHAIN);
        X509Certificate blocklistedCa = loadCertificate(BLOCKLIST_CA);
        assertUntrusted(chain, getTrustManager(blocklistedCa));
    }

    /**
     * Tests that the path building correctly routes around a blocklisted cert where there are
     * other valid paths available. This prevents breakage where a cert was cross signed by a
     * blocklisted CA but is still valid due to also being cross signed by CAs that remain trusted.
     * Path:
     *
     * leaf -> intermediate -> blocklisted_ca
     *               \
     *                -------> trusted_ca
     */
    public void testBlocklistedIntermediateFallback() throws Exception {
        X509Certificate[] chain = loadCertificates(BLOCKLISTED_VALID_CHAIN);
        X509Certificate blocklistedCa = loadCertificate(BLOCKLIST_CA);
        X509Certificate validCa = loadCertificate(BLOCKLIST_FALLBACK_VALID_CA);
        assertTrusted(chain, getTrustManager(blocklistedCa, validCa));
        // Check that without the trusted_ca the chain is invalid (since it only chains to a
        // blocklisted ca)
        assertUntrusted(chain, getTrustManager(blocklistedCa));
    }

    private X509Certificate loadCertificate(int resId) throws Exception {
        return loadCertificates(resId)[0];
    }

    private X509Certificate[] loadCertificates(int resId) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (InputStream is = getContext().getResources().openRawResource(resId)) {
            Collection<? extends Certificate> collection = factory.generateCertificates(is);
            X509Certificate[] certs = new X509Certificate[collection.size()];
            int i = 0;
            for (Certificate cert : collection) {
                certs[i++] = (X509Certificate) cert;
            }
            return certs;
        }
    }

    private static X509TrustManager getTrustManager(X509Certificate... trustedCas)
            throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        int i = 0;
        for (X509Certificate ca : trustedCas) {
            ks.setCertificateEntry(String.valueOf(i++), ca);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ks);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        fail("Could not find default X509TrustManager");
        return null;
    }

    private static void assertTrusted(X509Certificate[] certs, X509TrustManager tm)
            throws Exception {
        tm.checkServerTrusted(certs, "RSA");
    }

    private static void assertUntrusted(X509Certificate[] certs, X509TrustManager tm) {
        try {
            tm.checkServerTrusted(certs, "RSA");
            fail();
        } catch (CertificateException expected) {
        }
    }
}
