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

package android.security.cts.certblocklisttestapp;

import android.annotation.RequiresNoPermission;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.security.cts.ICertCheckService;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class CertTestService extends Service {
    private static final String TAG = "CertTestService";

    private ICertCheckService.Stub mBinder;

    @Override
    public void onCreate() {
        mBinder = new Stub();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        return mBinder;
    }

    private static X509TrustManager getTrustManager(
            X509Certificate... trustedCas) throws Exception {
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
        return null;
    }

    private static class Stub extends ICertCheckService.Stub {

        /* Validate that a certificate is blocked by the TrustManager.
         *
         * Add the supplied certificate to the list of trusted KeyStore and
         * then try to check its trust. If the certificate has been added to
         * the conscrypt blocklist, it is expected to fail.
         */
        @Override
        @RequiresNoPermission
        public boolean isBlocked(String certificate) {
            Certificate cert;
            try {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                cert = factory.generateCertificate(
                        new ByteArrayInputStream(certificate.getBytes()));
            } catch (CertificateException e) {
                Log.e(TAG, "Error reading certificate", e);
                throw new SecurityException(e);
            }
            X509Certificate[] certs = new X509Certificate[] { (X509Certificate) cert };
            X509TrustManager tm;
            try {
                tm = getTrustManager(certs);
            } catch (Exception e) {
                Log.e(TAG, "Error getting TrustManager", e);
                throw new SecurityException(e);
            }
            try {
                tm.checkServerTrusted(certs, "RSA");
                return false;
            } catch (CertificateException expected) {
                return true;
            }
        }
    }
}
