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

package android.appsecurity.cts.keystoremanagertest.grantee;

import android.app.Service;
import android.appsecurity.cts.keystoremanagertest.IKeyStoreManagerGrantTestService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.security.keystore.KeyStoreManager;
import android.util.Log;

import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Service class running in a separate app to verify {@link KeyStoreManager} grant related APIs
 * function as expected; a test app will generate / import keys, grant / deny access to the keys,
 * then call corresponding service methods in this class to verify the APIs only allow access when
 * it has been granted by the test app.
 */
public class KeyStoreManagerGrantTestService extends Service {
    private static final String TAG = "KeyStoreManagerTestSvc"; // Tag is limited to 23 characters

    private KeyStoreManagerGrantTestServiceImpl mService;

    @Override
    public void onCreate() {
        super.onCreate();
        mService = new KeyStoreManagerGrantTestServiceImpl(getApplicationContext());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mService;
    }

    private static class KeyStoreManagerGrantTestServiceImpl extends
            IKeyStoreManagerGrantTestService.Stub {
        private Context mContext;

        private KeyStoreManagerGrantTestServiceImpl(Context context) {
            mContext = context;
        }

        @Override
        public int getGrantedKeyFromId_secretKeyAccessGranted_succeeds(long keyId,
                byte[] cipherText, byte[] iv) {
            // When access to a key has been granted from the test app, the getGrantedKeyFromId
            // API should return the granted SecretKey, and it should be able to decrypt the
            // provided cipherText.
            try {
                String decryptedText = decryptTextWithSecretKey(keyId, cipherText, iv);
                if (!decryptedText.equals(TEST_TEXT)) {
                    Log.e(TAG, "Decrypted text does not equal expected value: " + decryptedText);
                    return RESULT_DECRYPTED_TEXT_UNEXPECTED_VALUE;
                }
                return RESULT_SUCCESS;
            } catch (Exception e) {
                Log.e(TAG, "Caught an exception invoking the test: ", e);
                return RESULT_EXCEPTION_CAUGHT_DURING_TEST;
            }

        }

        @Override
        public int getGrantedKeyFromId_secretKeyAccessRevoked_cannotAccess(long keyId) {
            // When access to a key has been revoked, the getGrantedKeyFromId API should throw
            // an UnrecoverableKeyException.
            try {
                KeyStoreManager keyStoreManager = (KeyStoreManager) mContext.getSystemService(
                        Context.KEYSTORE_SERVICE);
                SecretKey secretKey = (SecretKey) keyStoreManager.getGrantedKeyFromId(keyId);
                if (secretKey != null) {
                    Log.e(TAG, "Received a non-null result for the revoked key: " + secretKey);
                    return RESULT_NON_NULL_KEY_RETURNED_AFTER_REVOKE;
                }
                Log.e(TAG, "Received a null result for the revoked key, exception expected");
                return RESULT_NULL_KEY_RETURNED_AFTER_REVOKE;
            } catch (UnrecoverableKeyException expected) {
                return RESULT_SUCCESS;
            } catch (Exception e) {
                Log.e(TAG, "Caught an exception invoking the test: ", e);
                return RESULT_EXCEPTION_CAUGHT_DURING_TEST;
            }
        }

        @Override
        public int getGrantedKeyFromId_privateKeyAccessGranted_succeeds(long keyId,
                byte[] cipherText) {
            // When access to a key has been granted from the test app, the getGrantedKeyFromId
            // API should return the granted PrivateKey, and it should be able to decrypt the
            // provided cipherText.
            try {
                String decryptedText = decryptTextWithPrivateKey(keyId, cipherText);
                if (!decryptedText.equals(TEST_TEXT)) {
                    Log.e(TAG, "Decrypted text does not equal expected value: " + decryptedText);
                    return RESULT_DECRYPTED_TEXT_UNEXPECTED_VALUE;
                }
                return RESULT_SUCCESS;
            } catch (Exception e) {
                Log.e(TAG, "Caught an exception invoking the test: ", e);
                return RESULT_EXCEPTION_CAUGHT_DURING_TEST;
            }
        }

        @Override
        public int getGrantedKeyFromId_privateKeyAccessRevoked_cannotAccess(long keyId) {
            try {
                KeyStoreManager keyStoreManager = (KeyStoreManager) mContext.getSystemService(
                        Context.KEYSTORE_SERVICE);
                PrivateKey privateKey = (PrivateKey) keyStoreManager.getGrantedKeyFromId(keyId);
                if (privateKey != null) {
                    Log.e(TAG, "Received a non-null result for the revoked key: " + privateKey);
                    return RESULT_NON_NULL_KEY_RETURNED_AFTER_REVOKE;
                }
                Log.e(TAG, "Received a null result for the revoked key, exception expected");
                return RESULT_NULL_KEY_RETURNED_AFTER_REVOKE;
            } catch (UnrecoverableKeyException expected) {
                return RESULT_SUCCESS;
            } catch (Exception e) {
                Log.e(TAG, "Caught an exception invoking the test: ", e);
                return RESULT_EXCEPTION_CAUGHT_DURING_TEST;
            }
        }

        @Override
        public int getGrantedKeyPairFromId_privateKeyAccessGranted_succeeds(long keyId) {
            // When access to a private key has been granted, getGrantedKeyPairFromId should return
            // a KeyPair for the granted key. This method verifies the public key returned from the
            // API matches the expected public key for the granted private key.
            try {
                KeyStoreManager keyStoreManager = (KeyStoreManager) mContext.getSystemService(
                        Context.KEYSTORE_SERVICE);
                KeyPair keyPair = keyStoreManager.getGrantedKeyPairFromId(keyId);
                if (keyPair == null || keyPair.getPublic() == null) {
                    return RESULT_NULL_KEY_RETURNED_AFTER_GRANT;
                }
                byte[] expectedPublicKey = HexFormat.of().parseHex(EC_P256_3_PUBLIC_KEY);
                byte[] actualPublicKey = keyPair.getPublic().getEncoded();
                if (!Arrays.equals(expectedPublicKey, actualPublicKey)) {
                    Log.e(TAG, "Received an unexpected public key: " + HexFormat.of().formatHex(
                            actualPublicKey));
                    return RESULT_UNEXPECTED_PUBLIC_KEY;
                }
                return RESULT_SUCCESS;
            } catch (Exception e) {
                Log.e(TAG, "Caught an exception invoking the test: ", e);
                return RESULT_EXCEPTION_CAUGHT_DURING_TEST;
            }
        }

        @Override
        public int getGrantedCertificateChainFromId_privateKeyAccessGranted_succeeds(long keyId) {
            // When access to a private key has been granted, getGrantedCertificateChainFromId
            // should return the certificate chain for the granted key. This method verifies all
            // of the certificates from an imported key are returned in the correct order by this
            // API.
            try {
                KeyStoreManager keyStoreManager = (KeyStoreManager) mContext.getSystemService(
                        Context.KEYSTORE_SERVICE);
                List<X509Certificate> certificates =
                        keyStoreManager.getGrantedCertificateChainFromId(keyId);
                if (certificates == null) {
                    return RESULT_NULL_CERTIFICATE_CHAIN_RETURNED_AFTER_GRANT;
                }
                if (certificates.size() != 3) {
                    Log.e(TAG, "Expected 3 certificates in the chain, but found "
                            + certificates.size());
                    return RESULT_UNEXPECTED_NUMBER_OF_CERTIFICATES_IN_CHAIN;
                }
                // The certificate chain should be returned in the same order as the import with the
                // certificate corresponding to the private key at index 0 and the root certificate
                // as the last element.
                String[] expectedCerts =
                        new String[]{EC_P256_3_CERT, EC_P256_2_CERT, EC_P256_1_CERT};
                for (int i = 0; i < certificates.size(); i++) {
                    byte[] actualCert = certificates.get(i).getEncoded();
                    if (!Arrays.equals(HexFormat.of().parseHex(expectedCerts[i]), actualCert)) {
                        Log.e(TAG, "Found an unexpected certificate at index " + i + ": "
                                + HexFormat.of().formatHex(actualCert));
                        return RESULT_UNEXPECTED_CERTIFICATE;
                    }
                }
                return RESULT_SUCCESS;
            } catch (Exception e) {
                Log.e(TAG, "Caught an exception invoking the test: ", e);
                return RESULT_EXCEPTION_CAUGHT_DURING_TEST;
            }
        }

        private String decryptTextWithSecretKey(long keyId, byte[] cipherText, byte[] iv)
                throws Exception {
            KeyStore keyStore = KeyStore.getInstance(PROVIDER_NAME);
            keyStore.load(null);
            KeyStoreManager keyStoreManager = (KeyStoreManager) mContext.getSystemService(
                    Context.KEYSTORE_SERVICE);
            SecretKey secretKey = (SecretKey) keyStoreManager.getGrantedKeyFromId(keyId);
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            byte[] decrypted = cipher.doFinal(cipherText);
            return new String(decrypted);
        }

        private String decryptTextWithPrivateKey(long keyId, byte[] cipherText) throws Exception {
            KeyStore keyStore = KeyStore.getInstance(PROVIDER_NAME);
            keyStore.load(null);
            KeyStoreManager keyStoreManager = (KeyStoreManager) mContext.getSystemService(
                    Context.KEYSTORE_SERVICE);
            PrivateKey privateKey = (PrivateKey) keyStoreManager.getGrantedKeyFromId(keyId);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decrypted = cipher.doFinal(cipherText);
            return new String(decrypted);
        }
    }
}
