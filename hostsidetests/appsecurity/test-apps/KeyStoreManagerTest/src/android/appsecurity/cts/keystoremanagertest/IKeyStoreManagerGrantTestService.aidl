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

package android.appsecurity.cts.keystoremanagertest;

/**
  * Interface implemented by an app to which key access has been granted to verify that
  * KeyStoreManager APIs to access the granted key (and any corresponding KeyPair and certificates)
  * function as expected.
  */
interface IKeyStoreManagerGrantTestService {
    const int RESULT_SUCCESS = 0;
    const int RESULT_EXCEPTION_CAUGHT_DURING_TEST = 1;
    const int RESULT_DECRYPTED_TEXT_UNEXPECTED_VALUE = 2;
    const int RESULT_NON_NULL_KEY_RETURNED_AFTER_REVOKE = 3;
    const int RESULT_NULL_KEY_RETURNED_AFTER_GRANT = 4;
    const int RESULT_UNEXPECTED_PUBLIC_KEY = 5;
    const int RESULT_NULL_CERTIFICATE_CHAIN_RETURNED_AFTER_GRANT = 6;
    const int RESULT_UNEXPECTED_NUMBER_OF_CERTIFICATES_IN_CHAIN = 7;
    const int RESULT_UNEXPECTED_CERTIFICATE = 8;
    const int RESULT_NULL_KEY_RETURNED_AFTER_REVOKE = 9;

    /** Constant for the provider name of the Android KeyStore. */
    const String PROVIDER_NAME = "AndroidKeyStore";
    /**
      * Test text encrypted by the granter app and decrypted by the grantee to ensure a granted key
      * can be accessed as expected.
      */
    const String TEST_TEXT = "test";
    /** Public key corresponding to the granted imported private key. */
    const String EC_P256_3_PUBLIC_KEY =
            "3059301306072a8648ce3d020106082a8648ce3d03010703420004f31e62430e9db6fc5928d975fc4e4741"
          + "9bacfcb2e07c89299e6cd7e344dd21adfd308d58cb49a1a2a3fecacceea4862069f30be1643bcc255040d8"
          + "089dfb3743";

    /** Root certificate for the imported private key. */
    const String EC_P256_1_CERT =
            "3082016c30820111a003020102020900ca0fb64dfb66e772300a06082a8648ce3d04030230123110300e06"
          + "035504030c0765632d70323536301e170d3136303333313134353830365a170d3433303831373134353830"
          + "365a30123110300e06035504030c0765632d703235363059301306072a8648ce3d020106082a8648ce3d03"
          + "010703420004a65f113d22cb4913908307ac31ee2ba0e9138b785fac6536d14ea2ce90d2b4bfe194b50cdc"
          + "8e169f54a73a991ef0fa76329825be078cc782740703da44b4d7eba350304e301d0603551d0e04160414d4"
          + "133568b95b30158b322071ea8c43ff5b05ccc8301f0603551d23041830168014d4133568b95b30158b3220"
          + "71ea8c43ff5b05ccc8300c0603551d13040530030101ff300a06082a8648ce3d0403020349003046022100"
          + "f504a0866caef029f417142c5cb71354c79ffcd1d640618dfca4f19e16db78d6022100f8eea4829799c06c"
          + "ad08c6d3d2d2ec05e0574154e747ea0fdbb8042cb655aadd";

    /** Intermediate certificate for the imported private key. */
    const String EC_P256_2_CERT =
            "3082016d30820113a0030201020209008855bd1dd2b2b225300a06082a8648ce3d04030230123110300e06"
          + "035504030c0765632d70323536301e170d3138303731333137343135315a170d3238303731303137343135"
          + "315a30143112301006035504030c0965632d703235365f323059301306072a8648ce3d020106082a8648ce"
          + "3d030107034200041d4cca0472ad97ee3cecef0da93d62b450c6788333b36e7553cde9f74ab5df00bbba6b"
          + "a950e68461d70bbc271b62151dad2de2bf6203cd2076801c7a9d4422e1a350304e301d0603551d0e041604"
          + "147991d92b0208fc448bf506d4efc9fff428cb5e5f301f0603551d23041830168014d4133568b95b30158b"
          + "322071ea8c43ff5b05ccc8300c0603551d13040530030101ff300a06082a8648ce3d040302034800304502"
          + "202769abb1b49fc2f53479c4ae92a6631dabfd522c9acb0bba2b43ebeb99c63011022100d260fb1d1f176c"
          + "f9b7fa60098bfd24319f4905a3e5fda100a6fe1a2ab19ff09e";

    /** Certificate corresponding to the granted imported private key. */
    const String EC_P256_3_CERT =
            "3082016e30820115a0030201020209008394f5cad16a89a7300a06082a8648ce3d04030230143112301006"
          + "035504030c0965632d703235365f32301e170d3138303731343030303532365a170d323830373131303030"
          + "3532365a30143112301006035504030c0965632d703235365f333059301306072a8648ce3d020106082a86"
          + "48ce3d03010703420004f31e62430e9db6fc5928d975fc4e47419bacfcb2e07c89299e6cd7e344dd21adfd"
          + "308d58cb49a1a2a3fecacceea4862069f30be1643bcc255040d8089dfb3743a350304e301d0603551d0e04"
          + "1604146f8d0828b13efaf577fc86b0e99fa3e54bcbcff0301f0603551d230418301680147991d92b0208fc"
          + "448bf506d4efc9fff428cb5e5f300c0603551d13040530030101ff300a06082a8648ce3d04030203470030"
          + "440220256bdaa2784c273e4cc291a595a46779dee9de9044dc9f7ab820309567df9fe902201a4ad8c69891"
          + "b5a8c47434fe9540ed1f4979b5fad3483f3fa04d5677355a579e";
    /**
      * Verifies the {@code getGrantedKeyFromId} API returns the secret key that has been granted
      * with the provided {@code keyId} and that it can be used to decrypt the specified {@code
      * cipherText} with the initial {@code iv}.
      */
    int getGrantedKeyFromId_secretKeyAccessGranted_succeeds(long keyId, in byte[] cipherText,
            in byte[] iv);

    /**
      * Verifies the {@code getGrantedKeyFromId} API does not return the secret key for {@code
      * keyId} after the granting app has revoked access.
      */
    int getGrantedKeyFromId_secretKeyAccessRevoked_cannotAccess(long keyId);

    /**
      * Verifies the {@code getGrantedKeyFromId} API returns the private key that has been granted
      * with the provided {@code keyId} and that it can be used to decrypt the specified {@code
      * cipherText}.
      */
    int getGrantedKeyFromId_privateKeyAccessGranted_succeeds(long keyId, in byte[] cipherText);

    /**
      * Verifies the {@code getGrantedKeyFromId} API does not return the private key for {@code
      * keyId} after the granting app has revoked access.
      */
    int getGrantedKeyFromId_privateKeyAccessRevoked_cannotAccess(long keyId);

    /**
      * Verifies the {@code getGrantedKeyPairFromId} returns the KeyPair for the granted {@code
      * keyId} and that the public key from the KeyPair is the expected value.
      */
    int getGrantedKeyPairFromId_privateKeyAccessGranted_succeeds(long keyId);

    /**
      * Verifies the {@code getGrantedCertificateChainFromId} returns the expected certificates
      * for the granted {@code keyId}.
      */
    int getGrantedCertificateChainFromId_privateKeyAccessGranted_succeeds(long keyId);
}