/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.compatibility.common.deviceinfo;

import static android.security.keystore.KeyProperties.DIGEST_SHA256;
import static android.security.keystore.KeyProperties.KEY_ALGORITHM_EC;
import static android.security.keystore.KeyProperties.PURPOSE_SIGN;

import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_DEVICE_ID_ATTESTATION;

import static com.google.android.attestation.ParsedAttestationRecord.createParsedAttestationRecord;

import android.content.pm.PackageManager;
import android.security.keystore.KeyGenParameterSpec;
import android.util.Log;

import com.android.compatibility.common.util.DeviceInfoStore;

import com.google.android.attestation.AuthorizationList;
import com.google.android.attestation.RootOfTrust;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Key attestation collector. Collects a subset of the information attested by the device's
 * trusted execution environment (TEE) and by its StrongBox chip (if it has one).
 */
public final class KeystoreAttestationDeviceInfo extends DeviceInfo {
    // Max log tag length is 23 characters, so we can't use the full class name.
    private static final String TAG = "AttestationDeviceInfo";
    private static final String TEST_ALIAS_TEE = "testTeeKeyAlias";
    private static final String TEST_ALIAS_STRONGBOX = "testStrongBoxKeyAlias";
    private static final byte[] TEST_CHALLENGE = "challenge".getBytes();

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        collectAttestation(
                store, "keymint_key_attestation", TEST_ALIAS_TEE,
                /* strongBoxBacked= */ false);
        if (getContext()
                .getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
            collectAttestation(
                    store, "strong_box_key_attestation",
                    TEST_ALIAS_STRONGBOX, /* strongBoxBacked= */ true);
        } else {
            Log.i(TAG, "StrongBox-backed Keystore not supported");
        }
    }

    private void collectAttestation(
            DeviceInfoStore store,
            String resultGroupName,
            String keyAlias,
            boolean strongBoxBacked)
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        // If this collector ran on this device before, there might already be a key
        // with this alias, so delete it before trying to generate a fresh key with
        // the same alias.
        keyStore.deleteEntry(keyAlias);

        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(keyAlias, PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                        .setDigests(DIGEST_SHA256)
                        .setDevicePropertiesAttestationIncluded(
                                getContext()
                                        .getPackageManager()
                                        .hasSystemFeature(FEATURE_DEVICE_ID_ATTESTATION))
                        .setAttestationChallenge(TEST_CHALLENGE)
                        .setIsStrongBoxBacked(strongBoxBacked)
                        .build();

        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance(KEY_ALGORITHM_EC, "AndroidKeyStore");
        keyPairGenerator.initialize(spec);

        try {
            KeyPair unusedKeyPair = keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            String keystoreType = strongBoxBacked ? "StrongBox" : "TEE";
            Log.w(TAG, "Key pair generation failed with " + keystoreType + "-backed Keystore", e);
            return;
        }

        List<X509Certificate> x509Certificates = new ArrayList<>();
        for (Certificate certificate : keyStore.getCertificateChain(keyAlias)) {
            if (certificate instanceof X509Certificate) {
                x509Certificates.add((X509Certificate) certificate);
            }
        }
        if (x509Certificates.isEmpty()) {
            Log.w(TAG, "Certificate chain is empty, so no attestation could be extracted");
            return;
        }

        AuthorizationList authorizationList;

        try {
            authorizationList = createParsedAttestationRecord(
                    x509Certificates).teeEnforced;
        } catch (IOException e) {
            Log.w(TAG, "Failed to parse the attestation extension", e);
            return;
        }

        store.startGroup(resultGroupName);
        if (authorizationList.osVersion.isPresent()) {
            store.addResult("os_version", authorizationList.osVersion.get());
        }
        if (authorizationList.osPatchLevel.isPresent()) {
            store.addResult("os_patch_level", authorizationList.osPatchLevel.get());
        }
        if (authorizationList.attestationIdBrand.isPresent()) {
            store.addBytesResult(
                    "attestation_id_brand",
                    authorizationList.attestationIdBrand.get());
        }
        if (authorizationList.attestationIdDevice.isPresent()) {
            store.addBytesResult(
                    "attestation_id_device",
                    authorizationList.attestationIdDevice.get());
        }
        if (authorizationList.attestationIdProduct.isPresent()) {
            store.addBytesResult(
                    "attestation_id_product",
                    authorizationList.attestationIdProduct.get());
        }
        if (authorizationList.attestationIdManufacturer.isPresent()) {
            store.addBytesResult(
                    "attestation_id_manufacturer",
                    authorizationList.attestationIdManufacturer.get());
        }
        if (authorizationList.attestationIdModel.isPresent()) {
            store.addBytesResult(
                    "attestation_id_model",
                    authorizationList.attestationIdModel.get());
        }
        if (authorizationList.vendorPatchLevel.isPresent()) {
            store.addResult("vendor_patch_level", authorizationList.vendorPatchLevel.get());
        }
        if (authorizationList.bootPatchLevel.isPresent()) {
            store.addResult("boot_patch_level", authorizationList.bootPatchLevel.get());
        }
        if (authorizationList.rootOfTrust.isPresent()) {
            RootOfTrust rootOfTrust = authorizationList.rootOfTrust.get();
            store.startGroup("root_of_trust");
            store.addBytesResult(
                    "verified_boot_key",
                    rootOfTrust.verifiedBootKey);
            store.addResult("device_locked", rootOfTrust.deviceLocked);
            store.addResult("verified_boot_state", rootOfTrust.verifiedBootState.name());
            store.addBytesResult(
                    "verified_boot_hash",
                    rootOfTrust.verifiedBootHash);
            store.endGroup();
        }
        store.endGroup();
    }
}
