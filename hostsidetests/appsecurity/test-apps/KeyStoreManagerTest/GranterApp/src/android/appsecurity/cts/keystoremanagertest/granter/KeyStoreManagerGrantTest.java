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

package android.appsecurity.cts.keystoremanagertest.granter;

import static android.appsecurity.cts.keystoremanagertest.IKeyStoreManagerGrantTestService.EC_P256_1_CERT;
import static android.appsecurity.cts.keystoremanagertest.IKeyStoreManagerGrantTestService.EC_P256_2_CERT;
import static android.appsecurity.cts.keystoremanagertest.IKeyStoreManagerGrantTestService.EC_P256_3_CERT;
import static android.appsecurity.cts.keystoremanagertest.IKeyStoreManagerGrantTestService.PROVIDER_NAME;
import static android.appsecurity.cts.keystoremanagertest.IKeyStoreManagerGrantTestService.RESULT_SUCCESS;
import static android.appsecurity.cts.keystoremanagertest.IKeyStoreManagerGrantTestService.TEST_TEXT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.appsecurity.cts.keystoremanagertest.IKeyStoreManagerGrantTestService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyStoreManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Test class to verify KeyStoreManager APIs for granting and revoking access to keys owned by the
 * app function as expected. This app will use the Android KeyStore to generate / import keys, then
 * will grant access to these keys to another app installed as part of the test; this grantee app
 * will expose a service to which this app can connect to verify that accessing granted keys
 * functions as expected based on whether this app has granted or revoked access.
 */
@RunWith(AndroidJUnit4.class)
public class KeyStoreManagerGrantTest {
    /** The package name of the grantee app exposing the test service. */
    private static final String GRANTEE_PKG = "android.appsecurity.cts.keystoremanagertest.grantee";
    /**
     * The class name of the test service within the grantee app that is used to drive the tests.
     */
    private static final String GRANTEE_SERVICE = GRANTEE_PKG + ".KeyStoreManagerGrantTestService";
    /** Alias for the generated secret key used during the grant / revoke tests. */
    private static final String SECRET_KEY_ALIAS = "secret_key";
    /** Alias for the generated private key used during the grant / revoke tests. */
    private static final String PRIVATE_KEY_ALIAS = "private_key";
    /**
     * Alias for the imported key and corresponding certificates used during the grant / revoke
     * tests.
     */
    private static final String IMPORTED_KEY_ALIAS = "imported_key";
    /**
     * Hex representation of the DER encoding of the PKCS8 private key used during the import test.
     */
    private static final String EC_P256_3_PK8 =
            "308187020100301306072a8648ce3d020106082a8648ce3d030107046d306b0201010420e9487e046994c3"
            + "ac7d308d813c447ba42743897e13dfaa2f4d36c72a64df2757a14403420004f31e62430e9db6fc5928d9"
            + "75fc4e47419bacfcb2e07c89299e6cd7e344dd21adfd308d58cb49a1a2a3fecacceea4862069f30be164"
            + "3bcc255040d8089dfb3743";

    private KeyStore mKeyStore;
    private KeyStoreManager mKeyStoreManager;
    private IKeyStoreManagerGrantTestService mService;
    private int mGranteeUid;

    @Rule
    public final ServiceTestRule mServiceTestRule = new ServiceTestRule();

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        mKeyStore = KeyStore.getInstance(PROVIDER_NAME);
        mKeyStore.load(null);
        mKeyStoreManager = (KeyStoreManager) context.getSystemService(Context.KEYSTORE_SERVICE);
        // Bind to the service in the grantee app to verify API behavior in a separate UID.
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(GRANTEE_PKG, GRANTEE_SERVICE));
        IBinder binder = mServiceTestRule.bindService(intent);
        mService = IKeyStoreManagerGrantTestService.Stub.asInterface(binder);
        // Obtain the UID of the grantee app to be used during grants / revokes.
        mGranteeUid = context.getPackageManager().getApplicationInfo(
                GRANTEE_PKG, 0).uid;
    }

    @After
    public void tearDown() throws Exception {
        mKeyStore.deleteEntry(SECRET_KEY_ALIAS);
        mKeyStore.deleteEntry(PRIVATE_KEY_ALIAS);
        mKeyStore.deleteEntry(IMPORTED_KEY_ALIAS);
    }

    @Test
    public void grantAndRevokeKeyAccess_generatedSecretKey() throws Exception {
        // Verify that a generated SecretKey can be accessed by the grantee app and that it can be
        // used to successfully decrypt data.
        createSecretKey(SECRET_KEY_ALIAS);
        EncryptedData encryptedData = encryptTextWithSecretKey(TEST_TEXT, SECRET_KEY_ALIAS);
        long keyId = mKeyStoreManager.grantKeyAccess(SECRET_KEY_ALIAS, mGranteeUid);
        int result = mService.getGrantedKeyFromId_secretKeyAccessGranted_succeeds(keyId,
                encryptedData.cipherText, encryptedData.iv);
        assertEquals("Grantee app failed when accessing granted key with result " + result,
                RESULT_SUCCESS, result);

        // Verify that a grantee app can no longer access the SecretKey once access to the key
        // has been revoked.
        mKeyStoreManager.revokeKeyAccess(SECRET_KEY_ALIAS, mGranteeUid);
        result = mService.getGrantedKeyFromId_secretKeyAccessRevoked_cannotAccess(keyId);
        assertEquals(
                "Grantee app returned an error when attempting to access revoked key: " + result,
                RESULT_SUCCESS, result);
    }

    @Test
    public void grantAndRevokeKeyAccess_generatedPrivateKey() throws Exception {
        // Verify that a granted PrivateKey can be accessed by the grantee app and that it can be
        // used to successfully decrypt data (signing data would be a better option, but decrypting
        // allows the test to run in its entirety within the grantee app).
        createPrivateKey(PRIVATE_KEY_ALIAS);
        byte[] cipherText = encryptTextWithPublicKey(TEST_TEXT, PRIVATE_KEY_ALIAS);
        long keyId = mKeyStoreManager.grantKeyAccess(PRIVATE_KEY_ALIAS, mGranteeUid);
        int result = mService.getGrantedKeyFromId_privateKeyAccessGranted_succeeds(keyId,
                cipherText);
        assertEquals("Grantee app failed when accessing granted key with result " + result,
                RESULT_SUCCESS, result);

        // Verify that a grantee app can no longer access the PrivateKey once access to the key
        // has been revoked.
        mKeyStoreManager.revokeKeyAccess(PRIVATE_KEY_ALIAS, mGranteeUid);
        result = mService.getGrantedKeyFromId_privateKeyAccessRevoked_cannotAccess(keyId);
        assertEquals(
                "Grantee app returned an error when attempting to access revoked key: " + result,
                RESULT_SUCCESS, result);
    }

    @Test
    public void grantAndRevokeKeyAccess_importedKeyPair() throws Exception {
        // Verify that an imported private key with a corresponding certificate chain can be
        // accessed by the grantee app. Previous tests verified that a grantee app can access the
        // private key, this test will focus more so on the APIs to obtain the key pair and
        // certificate chain corresponding to the granted key.
        importPrivateKey(IMPORTED_KEY_ALIAS);
        long keyId = mKeyStoreManager.grantKeyAccess(IMPORTED_KEY_ALIAS, mGranteeUid);
        int result = mService.getGrantedKeyPairFromId_privateKeyAccessGranted_succeeds(keyId);
        assertEquals("Grantee app failed when accessing granted key pair with result " + result,
                RESULT_SUCCESS, result);

        // Verify that the API to get the certificate chain for a granted private key returns all
        // of the imported certificates.
        result = mService.getGrantedCertificateChainFromId_privateKeyAccessGranted_succeeds(keyId);
        assertEquals(
                "Grantee app failed when accessing certificate chain for granted key with result "
                        + result, RESULT_SUCCESS, result);

        // Verify that the grantee app can no longer access the PrivateKey once access to the key
        // has been revoked.
        mKeyStoreManager.revokeKeyAccess(IMPORTED_KEY_ALIAS, mGranteeUid);
        result = mService.getGrantedKeyFromId_privateKeyAccessRevoked_cannotAccess(keyId);
        assertEquals(
                "Grantee app returned an error when attempting to access revoked key: " + result,
                RESULT_SUCCESS, result);
    }

    @Test
    public void grantRevokeKeyAccess_aliasDoesNotExist_throwsException() throws Exception {
        // Verify that an attempt to grant or revoke key access to an alias that does not exist
        // in the Android Keystore results in an exception to notify the caller that the action
        // could not be performed.
        final String invalidAlias = "invalid_alias";
        assertThrows(UnrecoverableKeyException.class,
                () -> mKeyStoreManager.grantKeyAccess(invalidAlias, mGranteeUid));

        assertThrows(UnrecoverableKeyException.class,
                () -> mKeyStoreManager.revokeKeyAccess(invalidAlias, mGranteeUid));
    }

    @Test
    public void getGranted_grantIdDoesNotExist_throwsException() throws Exception {
        // If an invalid ID is provided to any of the getGranted accessor methods, then an
        // UnrecoverableKeyException should be thrown to indicate the key for the provided
        // ID could not be obtained.
        final long invalidId = 1234L;
        assertThrows(UnrecoverableKeyException.class,
                () -> mKeyStoreManager.getGrantedKeyFromId(invalidId));

        assertThrows(UnrecoverableKeyException.class,
                () -> mKeyStoreManager.getGrantedKeyPairFromId(invalidId));

        assertThrows(UnrecoverableKeyException.class,
                () -> mKeyStoreManager.getGrantedCertificateChainFromId(invalidId));
    }

    private void createSecretKey(String alias) throws Exception {
        if (mKeyStore.containsAlias(alias)) {
            mKeyStore.deleteEntry(alias);
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, PROVIDER_NAME);
        keyGenerator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        SecretKey secretKey = keyGenerator.generateKey();
        assertNotNull("Received a null key from generateKey", secretKey);
    }

    private void createPrivateKey(String alias) throws Exception {
        if (mKeyStore.containsAlias(alias)) {
            mKeyStore.deleteEntry(alias);
        }

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, PROVIDER_NAME);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setKeySize(2048)
                .build();
        keyPairGenerator.initialize(spec);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        assertNotNull("Received a null key pair from generateKeyPair", keyPair);
    }

    private void importPrivateKey(String alias) throws Exception {
        if (mKeyStore.containsAlias(alias)) {
            mKeyStore.deleteEntry(alias);
        }

        PrivateKey privateKey =
                KeyFactory.getInstance("EC").generatePrivate(
                        new PKCS8EncodedKeySpec(HexFormat.of().parseHex(EC_P256_3_PK8)));
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Certificate[] certificates = new Certificate[3];
        certificates[0] = certificateFactory.generateCertificate(
                new ByteArrayInputStream(HexFormat.of().parseHex(EC_P256_1_CERT)));
        certificates[1] = certificateFactory.generateCertificate(
                new ByteArrayInputStream(HexFormat.of().parseHex(EC_P256_2_CERT)));
        certificates[2] = certificateFactory.generateCertificate(
                new ByteArrayInputStream(HexFormat.of().parseHex(EC_P256_3_CERT)));
        mKeyStore.setKeyEntry(alias, privateKey, null,
                new Certificate[]{certificates[2], certificates[1], certificates[0]});
    }

    private EncryptedData encryptTextWithSecretKey(String text, String alias) throws Exception {
        SecretKey secretKey = (SecretKey) mKeyStore.getKey(alias, null);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        EncryptedData result = new EncryptedData();
        result.cipherText = cipher.doFinal(text.getBytes());
        result.iv = cipher.getIV();
        return result;
    }

    private byte[] encryptTextWithPublicKey(String text, String alias) throws Exception {
        PublicKey publicKey = mKeyStore.getCertificate(alias).getPublicKey();
        // Note, OAEP was previously used but it failed with an OAEP_DECODING_ERROR when run on
        // a cuttlefish device, so using PKCS1 to avoid any potential issues during the test.
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(text.getBytes());
    }

    private static class EncryptedData {
        private byte[] cipherText;
        private byte[] iv;
    }
}
