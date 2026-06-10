/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.keystore.cts;

import static android.keystore.cts.util.TestUtils.KmType;
import static android.keystore.cts.util.TestUtils.assumeKmSupport;
import static android.keystore.cts.util.TestUtils.isStrongboxKeyMint;
import static android.keystore.cts.util.TestUtils.assumeLockScreenSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.keystore.cts.util.StrictModeDetector;
import android.keystore.cts.util.TestUtils;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.test.MoreAsserts;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.collect.ObjectArrays;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.Provider.Service;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

@RunWith(JUnitParamsRunner.class)
public class KeyGeneratorTest {
    private static final String EXPECTED_PROVIDER_NAME = TestUtils.EXPECTED_PROVIDER_NAME;
    private static final String TAG = KeyGeneratorTest.class.getSimpleName();

    static String[] EXPECTED_ALGORITHMS = {
        "AES",
        "HmacSHA1",
        "HmacSHA224",
        "HmacSHA256",
        "HmacSHA384",
        "HmacSHA512",
    };

    {
        if (TestUtils.supports3DES()) {
            EXPECTED_ALGORITHMS = ObjectArrays.concat(EXPECTED_ALGORITHMS, "DESede");
        }
    }

    private static final Map<String, Integer> DEFAULT_KEY_SIZES =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    static {
        DEFAULT_KEY_SIZES.put("AES", 128);
        DEFAULT_KEY_SIZES.put("DESede", 168);
        DEFAULT_KEY_SIZES.put("HmacSHA1", 160);
        DEFAULT_KEY_SIZES.put("HmacSHA224", 224);
        DEFAULT_KEY_SIZES.put("HmacSHA256", 256);
        DEFAULT_KEY_SIZES.put("HmacSHA384", 384);
        DEFAULT_KEY_SIZES.put("HmacSHA512", 512);
    }

    static final int[] AES_SUPPORTED_KEY_SIZES = new int[] {128, 192, 256};
    static final int[] AES_STRONGBOX_SUPPORTED_KEY_SIZES = new int[] {128, 256};
    static final int[] DES_SUPPORTED_KEY_SIZES = new int[] {168};

    private static KmType[] kmTypes() {
        return new KmType[] {KmType.SB, KmType.TEE};
    }

    private static Object[] kmTypes_x_algorithms() {
        var permutations = new ArrayList<>(List.of(new Object[][] {
            {KmType.SB, "AES"},
            {KmType.SB, "HmacSHA256"},

            {KmType.TEE, "AES"},
            {KmType.TEE, "HmacSHA1"},
            {KmType.TEE, "HmacSHA224"},
            {KmType.TEE, "HmacSHA256"},
            {KmType.TEE, "HmacSHA384"},
            {KmType.TEE, "HmacSHA512"}
        }));
        if (TestUtils.supports3DES()) {
            permutations.add(new Object[] {KmType.TEE, "DESede"});
        }
        return permutations.toArray();
    }

    private static Object[] kmTypes_x_hmacAlgorithms() {
        var permutations = new ArrayList<>(List.of(new Object[][] {
            {KmType.SB, "HmacSHA256"},

            {KmType.TEE, "HmacSHA1"},
            {KmType.TEE, "HmacSHA224"},
            {KmType.TEE, "HmacSHA256"},
            {KmType.TEE, "HmacSHA384"},
            {KmType.TEE, "HmacSHA512"}
        }));
        return permutations.toArray();
    }

    private static Object[] kmTypes_x_signingAlgorithms() {
        var permutations = new ArrayList<>(Arrays.asList(kmTypes_x_hmacAlgorithms()));
        if (TestUtils.supports3DES()) {
            permutations.add(new Object[] {KmType.TEE, "DESede"});
        }
        return permutations.toArray();
    }

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testAlgorithmList() {
        // Assert that Android Keystore Provider exposes exactly the expected KeyGenerator
        // algorithms. We don't care whether the algorithms are exposed via aliases, as long as
        // canonical names of algorithms are accepted. If the Provider exposes extraneous
        // algorithms, it'll be caught because it'll have to expose at least one Service for such an
        // algorithm, and this Service's algorithm will not be in the expected set.

        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        Set<Service> services = provider.getServices();
        Set<String> actualAlgsLowerCase = new HashSet<String>();
        Set<String> expectedAlgsLowerCase = new HashSet<String>(
                Arrays.asList(TestUtils.toLowerCase(EXPECTED_ALGORITHMS)));
        for (Service service : services) {
            if ("KeyGenerator".equalsIgnoreCase(service.getType())) {
                String algLowerCase = service.getAlgorithm().toLowerCase(Locale.US);
                actualAlgsLowerCase.add(algLowerCase);
            }
        }

        TestUtils.assertContentsInAnyOrder(actualAlgsLowerCase,
                expectedAlgsLowerCase.toArray(new String[0]));
    }

    @Test
    public void testGenerateWithoutInitThrowsIllegalStateException() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                try {
                    keyGenerator.generateKey();
                    fail();
                } catch (IllegalStateException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testInitWithKeySizeThrowsUnsupportedOperationException() throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                int keySizeBits = DEFAULT_KEY_SIZES.get(algorithm);
                try {
                    keyGenerator.init(keySizeBits);
                    fail();
                } catch (UnsupportedOperationException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testInitWithKeySizeAndSecureRandomThrowsUnsupportedOperationException()
            throws Exception {
        SecureRandom rng = new SecureRandom();
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                int keySizeBits = DEFAULT_KEY_SIZES.get(algorithm);
                try {
                    keyGenerator.init(keySizeBits, rng);
                    fail();
                } catch (UnsupportedOperationException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testInitWithNullAlgParamsThrowsInvalidAlgorithmParameterException()
            throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                try {
                    keyGenerator.init((AlgorithmParameterSpec) null);
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    public void testInitWithNullAlgParamsAndSecureRandomThrowsInvalidAlgorithmParameterException()
            throws Exception {
        SecureRandom rng = new SecureRandom();
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                try {
                    keyGenerator.init((AlgorithmParameterSpec) null, rng);
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_algorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testInitWithAlgParamsAndNullSecureRandom(KmType kmType, String algorithm)
            throws Exception {
        assumeKmSupport(kmType);
        try {
            KeyGenerator keyGenerator = getKeyGenerator(algorithm);
            keyGenerator.init(getWorkingSpec()
                    .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                    .build(),
                    (SecureRandom) null);
            // Check that generateKey doesn't fail either, just in case null SecureRandom
            // causes trouble there.
            keyGenerator.generateKey();
        } catch (Throwable e) {
            throw new RuntimeException("Failed for " + algorithm, e);
        }
    }

    @Test
    public void testInitWithUnsupportedAlgParamsTypeThrowsInvalidAlgorithmParameterException()
            throws Exception {
        for (String algorithm : EXPECTED_ALGORITHMS) {
            try {
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                try {
                    keyGenerator.init(new ECGenParameterSpec("secp256r1"));
                    fail();
                } catch (InvalidAlgorithmParameterException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + algorithm, e);
            }
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_algorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testDefaultKeySize(KmType kmType, String algorithm) throws Exception {
        assumeKmSupport(kmType);
        StrictModeDetector strict = new StrictModeDetector(getContext());
        try {
            int expectedSizeBits = DEFAULT_KEY_SIZES.get(algorithm);
            KeyGenerator keyGenerator = getKeyGenerator(algorithm);
            keyGenerator.init(getWorkingSpec().build());
            SecretKey key = keyGenerator.generateKey();
            assertEquals(expectedSizeBits, TestUtils.getKeyInfo(key).getKeySize());
        } catch (Throwable e) {
            throw new RuntimeException("Failed for " + algorithm, e);
        }
        strict.check(algorithm + " key generation on " + kmType);
    }

    @Test
    @Parameters(method = "kmTypes")
    @TestCaseName(value = "{method}_{0}")
    public void testAesKeySupportedSizes(KmType kmType) throws Exception {
        assumeKmSupport(kmType);
        boolean useStrongbox = isStrongboxKeyMint(kmType);
        KeyGenerator keyGenerator = getKeyGenerator("AES");
        KeyGenParameterSpec.Builder goodSpec = getWorkingSpec();
        CountingSecureRandom rng = new CountingSecureRandom();
        for (int i = -16; i <= 512; i++) {
            try {
                rng.resetCounters();
                KeyGenParameterSpec spec;
                if (i >= 0) {
                    spec = TestUtils.buildUpon(
                        goodSpec.setKeySize(i)).setIsStrongBoxBacked(isStrongboxKeyMint(kmType)).build();
                } else {
                    try {
                        spec = TestUtils.buildUpon(
                            goodSpec.setKeySize(i)).setIsStrongBoxBacked(isStrongboxKeyMint(kmType)).build();
                        fail();
                    } catch (IllegalArgumentException expected) {
                        continue;
                    }
                }
                rng.resetCounters();
                if (TestUtils.contains(useStrongbox ?
                        AES_STRONGBOX_SUPPORTED_KEY_SIZES : AES_SUPPORTED_KEY_SIZES, i)) {
                    keyGenerator.init(spec, rng);
                    SecretKey key = keyGenerator.generateKey();
                    assertEquals(i, TestUtils.getKeyInfo(key).getKeySize());
                    assertEquals((i + 7) / 8, rng.getOutputSizeBytes());
                } else {
                    try {
                        if (useStrongbox && (i == 192))
                            throw new InvalidAlgorithmParameterException("Strongbox does not"
                                    + " support key size 192.");
                        keyGenerator.init(spec, rng);
                        fail();
                    } catch (InvalidAlgorithmParameterException expected) {}
                    assertEquals(0, rng.getOutputSizeBytes());
                }
            } catch (Throwable e) {
                throw new RuntimeException("Failed for key size " + i, e);
            }
        }
    }

    @Test
    public void testDESKeySupportedSizes() throws Exception {
        if (!TestUtils.supports3DES()) {
            return;
        }
        KeyGenerator keyGenerator = getKeyGenerator("DESede");
        KeyGenParameterSpec.Builder goodSpec = getWorkingSpec();
        CountingSecureRandom rng = new CountingSecureRandom();
        for (int i = -16; i <= 168; i++) {
            try {
                rng.resetCounters();
                KeyGenParameterSpec spec;
                if (i >= 0) {
                    spec = TestUtils.buildUpon(goodSpec.setKeySize(i)).build();
                } else {
                    try {
                        spec = TestUtils.buildUpon(goodSpec.setKeySize(i)).build();
                        fail();
                    } catch (IllegalArgumentException expected) {
                        continue;
                    }
                }
                rng.resetCounters();
                if (TestUtils.contains(DES_SUPPORTED_KEY_SIZES, i)) {
                    keyGenerator.init(spec, rng);
                    SecretKey key = keyGenerator.generateKey();
                    assertEquals(i, TestUtils.getKeyInfo(key).getKeySize());
                } else {
                    try {
                        keyGenerator.init(spec, rng);
                        fail();
                    } catch (InvalidAlgorithmParameterException expected) {}
                    assertEquals(0, rng.getOutputSizeBytes());
                }
            } catch (Throwable e) {
                throw new RuntimeException("Failed for key size " + i, e);
            }
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_hmacAlgorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testHmacKeySupportedSizes(KmType kmType, String algorithm) throws Exception {
        assumeKmSupport(kmType);
        CountingSecureRandom rng = new CountingSecureRandom();

        for (int i = -16; i <= 1024; i++) {
            try {
                rng.resetCounters();
                KeyGenerator keyGenerator = getKeyGenerator(algorithm);
                KeyGenParameterSpec spec;
                if (i >= 0) {
                    spec = getWorkingSpec()
                           .setKeySize(i)
                            .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                            .build();
                } else {
                    try {
                        spec = getWorkingSpec()
                                .setKeySize(i)
                                .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                                .build();
                        fail();
                    } catch (IllegalArgumentException expected) {
                        continue;
                    }
                }
                if (i > 512) {
                    try {
                        keyGenerator.init(spec, rng);
                        fail();
                    } catch (InvalidAlgorithmParameterException expected) {
                        assertEquals(0, rng.getOutputSizeBytes());
                    }
                } else if ((i >= 64) && ((i % 8) == 0)) {
                    keyGenerator.init(spec, rng);
                    SecretKey key = keyGenerator.generateKey();
                    assertEquals(i, TestUtils.getKeyInfo(key).getKeySize());
                    assertEquals((i + 7) / 8, rng.getOutputSizeBytes());
                } else if (i >= 64) {
                    try {
                        keyGenerator.init(spec, rng);
                        fail();
                    } catch (InvalidAlgorithmParameterException expected) {}
                    assertEquals(0, rng.getOutputSizeBytes());
                }
            } catch (Throwable e) {
                throw new RuntimeException(
                        "Failed for " + algorithm + " with key size " + i, e);
            }
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_hmacAlgorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testHmacKeyOnlyOneDigestCanBeAuthorized(KmType kmType, String algorithm)
            throws Exception {
        assumeKmSupport(kmType);

        try {
            String digest = TestUtils.getHmacAlgorithmDigest(algorithm);
            assertNotNull(digest);

            KeyGenParameterSpec.Builder goodSpec =
                    new KeyGenParameterSpec.Builder("test1", KeyProperties.PURPOSE_SIGN);

            KeyGenerator keyGenerator = getKeyGenerator(algorithm);

            // Digests authorization not specified in algorithm parameters
            assertFalse(goodSpec.setIsStrongBoxBacked(isStrongboxKeyMint(kmType)).build().isDigestsSpecified());
            keyGenerator.init(goodSpec.setIsStrongBoxBacked(isStrongboxKeyMint(kmType)).build());
            SecretKey key = keyGenerator.generateKey();
            TestUtils.assertContentsInAnyOrder(
                    Arrays.asList(TestUtils.getKeyInfo(key).getDigests()), digest);

            // The same digest is specified in algorithm parameters
            keyGenerator.init(TestUtils.buildUpon(goodSpec)
                    .setDigests(digest)
                    .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                    .build());
            key = keyGenerator.generateKey();
            TestUtils.assertContentsInAnyOrder(
                    Arrays.asList(TestUtils.getKeyInfo(key).getDigests()), digest);

            // No digests specified in algorithm parameters
            try {
                keyGenerator.init(TestUtils.buildUpon(goodSpec)
                        .setDigests()
                        .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                        .build());
                fail();
            } catch (InvalidAlgorithmParameterException expected) {}

            // A different digest specified in algorithm parameters
            String anotherDigest = "SHA-256".equalsIgnoreCase(digest) ? "SHA-384" : "SHA-256";
            try {
                keyGenerator.init(TestUtils.buildUpon(goodSpec)
                        .setDigests(anotherDigest)
                        .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                        .build());
                fail();
            } catch (InvalidAlgorithmParameterException expected) {}
            try {
                keyGenerator.init(TestUtils.buildUpon(goodSpec)
                        .setDigests(digest, anotherDigest)
                        .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                        .build());
                fail();
            } catch (InvalidAlgorithmParameterException expected) {}
        } catch (Throwable e) {
            throw new RuntimeException("Failed for " + algorithm, e);
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_algorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testInitWithUnknownBlockModeFails(KmType kmType, String algorithm) {
        assumeKmSupport(kmType);
        try {
            KeyGenerator keyGenerator = getKeyGenerator(algorithm);
            try {
                keyGenerator.init(
                        getWorkingSpec()
                                .setBlockModes("weird")
                                .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                                .build());
                fail();
            } catch (InvalidAlgorithmParameterException expected) {}
        } catch (Throwable e) {
            throw new RuntimeException("Failed for " + algorithm, e);
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_algorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testInitWithUnknownEncryptionPaddingFails(KmType kmType, String algorithm) {
        assumeKmSupport(kmType);
        try {
            KeyGenerator keyGenerator = getKeyGenerator(algorithm);
            try {
                keyGenerator.init(
                        getWorkingSpec()
                                .setEncryptionPaddings("weird")
                                .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                                .build());
                fail();
            } catch (InvalidAlgorithmParameterException expected) {}
        } catch (Throwable e) {
            throw new RuntimeException("Failed for " + algorithm, e);
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_algorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testInitWithSignaturePaddingFails(KmType kmType, String algorithm) {
        assumeKmSupport(kmType);
        try {
            KeyGenerator keyGenerator = getKeyGenerator(algorithm);
            try {
                keyGenerator.init(getWorkingSpec()
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                        .build());
                fail();
            } catch (InvalidAlgorithmParameterException expected) {}
        } catch (Throwable e) {
            throw new RuntimeException("Failed for " + algorithm, e);
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_algorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testInitWithUnknownDigestFails(KmType kmType, String algorithm) {
        assumeKmSupport(kmType);
        try {
            KeyGenerator keyGenerator = getKeyGenerator(algorithm);
            try {
                String[] digests;
                if (TestUtils.isHmacAlgorithm(algorithm)) {
                    // The digest from HMAC key algorithm must be specified in the list of
                    // authorized digests (if the list if provided).
                    digests = new String[] {algorithm, "weird"};
                } else {
                    digests = new String[] {"weird"};
                }
                keyGenerator.init(
                        getWorkingSpec()
                                .setDigests(digests)
                                .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                                .build());
                fail();
            } catch (InvalidAlgorithmParameterException expected) {}
        } catch (Throwable e) {
            throw new RuntimeException("Failed for " + algorithm, e);
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_hmacAlgorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testInitWithKeyAlgorithmDigestMissingFromAuthorizedDigestFails(
            KmType kmType, String algorithm) {
        assumeKmSupport(kmType);
        try {
            KeyGenerator keyGenerator = getKeyGenerator(algorithm);

            // Authorized for digest(s) none of which is the one implied by key algorithm.
            try {
                String digest = TestUtils.getHmacAlgorithmDigest(algorithm);
                String anotherDigest = KeyProperties.DIGEST_SHA256.equalsIgnoreCase(digest)
                        ? KeyProperties.DIGEST_SHA512 : KeyProperties.DIGEST_SHA256;
                keyGenerator.init(
                        getWorkingSpec()
                        .setDigests(anotherDigest)
                        .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                        .build());
                fail();
            } catch (InvalidAlgorithmParameterException expected) {}

            // Authorized for empty set of digests
            try {
                keyGenerator.init(
                        getWorkingSpec()
                        .setDigests()
                        .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                        .build());
                fail();
            } catch (InvalidAlgorithmParameterException expected) {}
        } catch (Throwable e) {
            throw new RuntimeException("Failed for " + algorithm, e);
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_algorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testInitRandomizedEncryptionRequiredButViolatedFails(
            KmType kmType, String algorithm) throws Exception {
        assumeKmSupport(kmType);
        try {
            KeyGenerator keyGenerator = getKeyGenerator(algorithm);
            try {
                keyGenerator.init(getWorkingSpec(KeyProperties.PURPOSE_ENCRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                        .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                        .build());
                fail();
            } catch (InvalidAlgorithmParameterException expected) {}
        } catch (Throwable e) {
            throw new RuntimeException("Failed for " + algorithm, e);
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_algorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testGenerateHonorsRequestedAuthorizations(KmType kmType, String algorithm)
            throws Exception {
        assumeKmSupport(kmType);
        Date keyValidityStart = new Date(System.currentTimeMillis() - TestUtils.DAY_IN_MILLIS);
        Date keyValidityForOriginationEnd =
                new Date(System.currentTimeMillis() + TestUtils.DAY_IN_MILLIS);
        Date keyValidityForConsumptionEnd =
                new Date(System.currentTimeMillis() + 3 * TestUtils.DAY_IN_MILLIS);
        try {
            String[] blockModes =
                    new String[] {KeyProperties.BLOCK_MODE_GCM, KeyProperties.BLOCK_MODE_CBC};
            String[] encryptionPaddings =
                    new String[] {KeyProperties.ENCRYPTION_PADDING_PKCS7,
                            KeyProperties.ENCRYPTION_PADDING_NONE};
            String[] digests;
            @KeyProperties.PurposeEnum int purposes;
            if (TestUtils.isHmacAlgorithm(algorithm)) {
                // HMAC key can only be authorized for one digest, the one implied by the key's
                // JCA algorithm name.
                digests = new String[] {TestUtils.getHmacAlgorithmDigest(algorithm)};
                purposes = KeyProperties.PURPOSE_SIGN;
            } else {
                digests = new String[] {KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA1};
                purposes = KeyProperties.PURPOSE_DECRYPT;
            }
            KeyGenerator keyGenerator = getKeyGenerator(algorithm);
            keyGenerator.init(getWorkingSpec(purposes)
                    .setBlockModes(blockModes)
                    .setEncryptionPaddings(encryptionPaddings)
                    .setDigests(digests)
                    .setKeyValidityStart(keyValidityStart)
                    .setKeyValidityForOriginationEnd(keyValidityForOriginationEnd)
                    .setKeyValidityForConsumptionEnd(keyValidityForConsumptionEnd)
                    .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                    .build());
            SecretKey key = keyGenerator.generateKey();
            assertEquals(algorithm, key.getAlgorithm());

            KeyInfo keyInfo = TestUtils.getKeyInfo(key);
            assertEquals(purposes, keyInfo.getPurposes());
            TestUtils.assertContentsInAnyOrder(
                    Arrays.asList(blockModes), keyInfo.getBlockModes());
            TestUtils.assertContentsInAnyOrder(
                    Arrays.asList(encryptionPaddings), keyInfo.getEncryptionPaddings());
            TestUtils.assertContentsInAnyOrder(Arrays.asList(digests), keyInfo.getDigests());
            MoreAsserts.assertEmpty(Arrays.asList(keyInfo.getSignaturePaddings()));
            assertEquals(keyValidityStart, keyInfo.getKeyValidityStart());
            assertEquals(keyValidityForOriginationEnd,
                    keyInfo.getKeyValidityForOriginationEnd());
            assertEquals(keyValidityForConsumptionEnd,
                    keyInfo.getKeyValidityForConsumptionEnd());
            assertFalse(keyInfo.isUserAuthenticationRequired());
            assertFalse(keyInfo.isUserAuthenticationRequirementEnforcedBySecureHardware());
        } catch (Throwable e) {
            throw new RuntimeException("Failed for " + algorithm, e);
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_algorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testLimitedUseKey(KmType kmType, String algorithm) throws Exception {
        assumeKmSupport(kmType);
        int maxUsageCount = 1;
        try {
            int expectedSizeBits = DEFAULT_KEY_SIZES.get(algorithm);
            KeyGenerator keyGenerator = getKeyGenerator(algorithm);
            keyGenerator.init(getWorkingSpec().setMaxUsageCount(maxUsageCount).build());
            SecretKey key = keyGenerator.generateKey();
            assertEquals(expectedSizeBits, TestUtils.getKeyInfo(key).getKeySize());
            assertEquals(maxUsageCount, TestUtils.getKeyInfo(key).getRemainingUsageCount());
        } catch (Throwable e) {
            throw new RuntimeException("Failed for " + algorithm, e);
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_signingAlgorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testGenerateAuthBoundKey_Lskf(KmType kmType, String algorithm)
            throws Exception {
        assumeLockScreenSupport();
        assumeKmSupport(kmType);
        try (var dl = new DeviceLockSession(InstrumentationRegistry.getInstrumentation())) {
            KeyGenerator keyGenerator = getKeyGenerator(algorithm);
            keyGenerator.init(getWorkingSpec(
                        KeyProperties.PURPOSE_SIGN)
                    .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(0 /* seconds */,
                            KeyProperties.AUTH_DEVICE_CREDENTIAL)
                    .build());
            keyGenerator.generateKey();
        }
    }

    @Test
    @Parameters(method = "kmTypes_x_signingAlgorithms")
    @TestCaseName(value = "{method}_{0}_{1}")
    public void testGenerateAuthBoundKey_LskfOrStrongBiometric(KmType kmType, String algorithm)
            throws Exception {
        assumeLockScreenSupport();
        assumeKmSupport(kmType);
        try (var dl = new DeviceLockSession(InstrumentationRegistry.getInstrumentation())) {
            KeyGenerator keyGenerator = getKeyGenerator(algorithm);
            keyGenerator.init(getWorkingSpec(
                        KeyProperties.PURPOSE_SIGN)
                    .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(0 /* seconds */,
                            KeyProperties.AUTH_BIOMETRIC_STRONG
                            | KeyProperties.AUTH_DEVICE_CREDENTIAL)
                    .build());
            keyGenerator.generateKey();
        }
    }

    @Test
    @Parameters(method = "kmTypes")
    @TestCaseName(value = "{method}_{0}")
    public void testUniquenessOfAesKeys(KmType kmType) throws Exception {
        assumeKmSupport(kmType);
        assertUniqueAesEncryptionForNKeys("AES/ECB/NoPadding", isStrongboxKeyMint(kmType));
        assertUniqueAesEncryptionForNKeys("AES/CBC/NoPadding", isStrongboxKeyMint(kmType));
    }

    private void assertUniqueAesEncryptionForNKeys(String algoTransform, boolean isStrongboxKeyMint)
            throws Exception {
        byte[] randomMsg = new byte[16];
        SecureRandom.getInstance("SHA1PRNG").nextBytes(randomMsg);
        byte[][] msgArr = new byte[][]{
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
                "16 char message.".getBytes(StandardCharsets.UTF_8),
                randomMsg
        };
        for (byte[] msg : msgArr) {
            int numberOfKeysToTest = 10;
            Set results = new HashSet();
            boolean isCbcMode = algoTransform.contains("CBC");
            byte[] iv = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
            for (int i = 0; i < numberOfKeysToTest; i++) {
                KeyGenerator keyGenerator = getKeyGenerator("AES");
                keyGenerator.init(getWorkingSpec(KeyProperties.PURPOSE_ENCRYPT)
                        .setBlockModes(isCbcMode
                                ? KeyProperties.BLOCK_MODE_CBC : KeyProperties.BLOCK_MODE_ECB)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(false)
                        .setIsStrongBoxBacked(isStrongboxKeyMint)
                        .build());
                SecretKey key = keyGenerator.generateKey();
                Cipher cipher = Cipher.getInstance(algoTransform,
                        TestUtils.EXPECTED_CRYPTO_OP_PROVIDER_NAME);
                if (isCbcMode) {
                    cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
                } else {
                    cipher.init(Cipher.ENCRYPT_MODE, key);
                }
                byte[] cipherText = msg == null ? cipher.doFinal() : cipher.doFinal(msg);
                // Add generated cipher text to HashSet so that only unique cipher text will be
                // counted.
                results.add(new String(cipherText));
            }
            // Verify unique cipher text is generated for all different keys
            assertEquals(
                    TextUtils.formatSimple("%d different cipher text should have been"
                                    + " generated for %d different keys. Failed for message |%s|.",
                            numberOfKeysToTest, numberOfKeysToTest, HexEncoding.encode(msg)),
                    numberOfKeysToTest, results.size());
        }
    }

    @Test
    @Parameters(method = "kmTypes")
    @TestCaseName(value = "{method}_{0}")
    public void testUniquenessOfHmacKeys(KmType kmType) throws Exception {
        assumeKmSupport(kmType);
        int numberOfKeysToTest = 10;
        byte[] randomMsg = new byte[16];
        SecureRandom.getInstance("SHA1PRNG").nextBytes(randomMsg);
        byte[][] msgArr = new byte[][]{
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
                {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
                "dummymessage1234".getBytes(StandardCharsets.UTF_8),
                randomMsg,
                {},
                null
        };
        for (byte[] msg : msgArr) {
            Set results = new HashSet();
            for (int i = 0; i < numberOfKeysToTest; i++) {
                KeyGenerator keyGenerator = getKeyGenerator("HmacSHA256");
                keyGenerator.init(getWorkingSpec(KeyProperties.PURPOSE_SIGN)
                        .setIsStrongBoxBacked(isStrongboxKeyMint(kmType))
                        .build());
                SecretKey key = keyGenerator.generateKey();
                Mac mac = Mac.getInstance("HMACSHA256",
                        TestUtils.EXPECTED_CRYPTO_OP_PROVIDER_NAME);
                mac.init(key);
                byte[] macSign = mac.doFinal(msg);
                // Add generated mac signature to HashSet so that unique signatures will be counted
                results.add(new String(macSign));
            }

            if ((msg == null || msg.length == 0)
                    && TestUtils.getVendorApiLevel() <= Build.VERSION_CODES.P) {
                // Skip empty and null inputs on older devices as HAL is unable to handle them.
                Log.d(TAG, "Skipping test for unsupported input on pre-Q launch device.");
                continue;
            }

            // Verify unique MAC is generated for all different keys
            assertEquals(TextUtils.formatSimple("%d different MACs should have been generated for "
                                         + "%d different keys over message |%s|",
                                 numberOfKeysToTest, numberOfKeysToTest, HexEncoding.encode(msg)),
                    numberOfKeysToTest, results.size());
        }
    }

    private static KeyGenParameterSpec.Builder getWorkingSpec() {
        return getWorkingSpec(0);
    }

    private static KeyGenParameterSpec.Builder getWorkingSpec(
            @KeyProperties.PurposeEnum int purposes) {
        return new KeyGenParameterSpec.Builder("test1", purposes);
    }

    private static KeyGenerator getKeyGenerator(String algorithm) throws NoSuchAlgorithmException,
            NoSuchProviderException {
        return KeyGenerator.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
    }
}
