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

package android.companion.cts.multidevice

import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerExecutor
import android.os.HandlerThread
import android.security.attestationverification.AttestationProfile
import android.security.attestationverification.AttestationVerificationManager
import android.security.attestationverification.AttestationVerificationManager.PARAM_CHALLENGE
import android.security.attestationverification.AttestationVerificationManager.PROFILE_PEER_DEVICE
import android.security.attestationverification.AttestationVerificationManager.TYPE_CHALLENGE
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.mobly.snippet.Snippet
import com.google.android.mobly.snippet.rpc.Rpc
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.concurrent.Executor

/**
 * Snippet class that exposes Android APIs in CompanionDeviceManager.
 */
class AttestationVerificationManagerSnippet : Snippet {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()!!
    private val context: Context = instrumentation.targetContext
    private val avm = context.getSystemService(Context.ATTESTATION_VERIFICATION_SERVICE)
            as AttestationVerificationManager

    private val handlerThread = HandlerThread("Snippet-Aware")
    private val handler: Handler
    private val executor: Executor

    init {
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        executor = HandlerExecutor(handler)
    }

    @Rpc(description = "Verify provided attestation")
    fun verifyAttestation(attestation: String): Boolean {
        val callback = CallbackUtils.AttestationVerificationCallback()
        val requirements = Bundle()
        requirements.putByteArray(PARAM_CHALLENGE, DEFAULT_CHALLENGE.toByteArray(Charsets.UTF_8))
        avm.verifyAttestation(
            AttestationProfile(PROFILE_PEER_DEVICE),
            TYPE_CHALLENGE,
            requirements,
            Base64.decode(attestation, Base64.NO_WRAP),
            executor,
            callback
        )
        val result = callback.waitForResult()
        return result
    }

    @Rpc(description = "Fetch attestation")
    @Throws(GeneralSecurityException::class)
    fun generateAttestation(): String {
        val androidKeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val parameterSpec = KeyGenParameterSpec.Builder(
            ATTESTATION_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAttestationChallenge(DEFAULT_CHALLENGE.toByteArray(Charsets.UTF_8))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        keyPairGenerator.initialize(parameterSpec)
        keyPairGenerator.generateKeyPair()
        val certificateChain = androidKeyStore.getCertificateChain(ATTESTATION_ALIAS)
        val buffer = ByteArrayOutputStream()
        for (certificate in certificateChain) {
            buffer.writeBytes(certificate.encoded)
        }
        return Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ATTESTATION_ALIAS = "cdm_avf_test_attestation"
        const val DEFAULT_CHALLENGE = "cdm_avf_challenge"
    }
}
