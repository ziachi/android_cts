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

package android.mediav2.common.cts;

import static org.junit.Assert.assertNotNull;

import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Vector;

/**
 * Wrapper class for trying and testing mediacodec decoder components in secure mode.
 */
public class CodecDecoderDrmTestBase extends CodecDecoderTestBase {
    private static final String LOG_TAG = CodecDecoderDrmTestBase.class.getSimpleName();

    protected MediaDrm mDrm;
    protected MediaCrypto mCrypto;

    public CodecDecoderDrmTestBase(String codecName, String mediaType, String testFile,
            String allTestParams) {
        super(codecName, mediaType, testFile, allTestParams);
    }

    @After
    public void tearDownCodecDecoderDrmTestBase() {
        tearDownCrypto();
    }

    private static int getKeyIds(byte[] keyRequestBlob, Vector<String> keyIds) {
        if (0 == keyRequestBlob.length || keyIds == null) {
            return 0;
        }

        String jsonLicenseRequest = new String(keyRequestBlob);
        keyIds.clear();

        try {
            JSONObject license = new JSONObject(jsonLicenseRequest);
            final JSONArray ids = license.getJSONArray("kids");
            for (int i = 0; i < ids.length(); ++i) {
                keyIds.add(ids.getString(i));
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Invalid JSON license = " + jsonLicenseRequest);
            return 0;
        }
        return keyIds.size();
    }

    private static String createJsonWebKeySet(Vector<String> keyIds, Vector<String> keys,
            int keyType) {
        StringBuilder jwkSet = new StringBuilder("{\"keys\":[");
        for (int i = 0; i < keyIds.size(); ++i) {
            String id = new String(keyIds.get(i).getBytes(StandardCharsets.UTF_8));
            String key = new String(keys.get(i).getBytes(StandardCharsets.UTF_8));
            jwkSet.append("{\"kty\":\"oct\",\"kid\":\"").append(id).append("\",\"k\":\"")
                    .append(key).append("\"}");
        }
        jwkSet.append("], \"type\":");
        if (keyType == MediaDrm.KEY_TYPE_OFFLINE || keyType == MediaDrm.KEY_TYPE_RELEASE) {
            jwkSet.append("\"persistent-license\" }");
        } else {
            jwkSet.append("\"temporary\" }");
        }
        return jwkSet.toString();
    }

    private static byte[] retrieveKeys(MediaDrm drm, String initDataType, byte[] sessionId,
            byte[] drmInitData, int keyType, byte[][] clearKeyIds) {
        MediaDrm.KeyRequest drmRequest = null;
        try {
            drmRequest = drm.getKeyRequest(sessionId, drmInitData, initDataType, keyType, null);
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(LOG_TAG, "Failed to get key request: " + e);
        }
        if (drmRequest == null) {
            Log.e(LOG_TAG, "Failed getKeyRequest");
            return null;
        }

        Vector<String> keyIds = new Vector<>();
        if (0 == getKeyIds(drmRequest.getData(), keyIds)) {
            Log.e(LOG_TAG, "No key ids found in initData");
            return null;
        }

        if (clearKeyIds.length != keyIds.size()) {
            Log.e(LOG_TAG, "Mismatch number of key ids and keys: ids=" + keyIds.size() + ", keys="
                    + clearKeyIds.length);
            return null;
        }

        // Base64 encodes clearkeys. Keys are known to the application.
        Vector<String> keys = new Vector<>();
        for (byte[] clearKeyId : clearKeyIds) {
            String clearKey = Base64.encodeToString(clearKeyId, Base64.NO_PADDING | Base64.NO_WRAP);
            keys.add(clearKey);
        }

        String jwkSet = createJsonWebKeySet(keyIds, keys, keyType);
        byte[] jsonResponse = jwkSet.getBytes(StandardCharsets.UTF_8);

        try {
            try {
                return drm.provideKeyResponse(sessionId, jsonResponse);
            } catch (IllegalStateException e) {
                Log.e(LOG_TAG, "Failed to provide key response: " + e);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "Failed to provide key response: " + e);
        }
        return null;
    }

    static Pair<MediaDrm, MediaCrypto> setUpMediaDrmAndCrypto(UUID uuidCryptoScheme,
            byte[] drmInitData, byte[][] keys) throws UnsupportedSchemeException,
            NotProvisionedException, ResourceBusyException, MediaCryptoException {
        MediaDrm drm = new MediaDrm(uuidCryptoScheme);
        drm.setOnEventListener(
                (MediaDrm mediaDrm, byte[] sessionId, int event, int extra, byte[] data) -> {
                    if (event == MediaDrm.EVENT_KEY_REQUIRED
                            || event == MediaDrm.EVENT_KEY_EXPIRED) {
                        retrieveKeys(mediaDrm, "cenc", sessionId, drmInitData,
                                MediaDrm.KEY_TYPE_STREAMING, keys);
                    }
                });
        byte[] sessionId = drm.openSession();
        retrieveKeys(drm, "cenc", sessionId, drmInitData, MediaDrm.KEY_TYPE_STREAMING, keys);
        if (sessionId != null) {
            return Pair.create(drm, new MediaCrypto(uuidCryptoScheme, sessionId));
        }
        return null;
    }

    public void setUpCrypto(UUID uuidCryptoScheme, byte[] drmInitData, byte[][] keys)
            throws UnsupportedSchemeException, NotProvisionedException, ResourceBusyException,
            MediaCryptoException {
        Pair<MediaDrm, MediaCrypto> cryptoPair = setUpMediaDrmAndCrypto(uuidCryptoScheme,
                drmInitData, keys);
        assertNotNull("failed to set up crypto session \n" + mTestConfig + mTestEnv, cryptoPair);
        mDrm = cryptoPair.first;
        mCrypto = cryptoPair.second;
    }

    public void tearDownCrypto() {
        if (mCrypto != null) {
            mCrypto.release();
            mCrypto = null;
        }
        if (mDrm != null) {
            mDrm.close();
            mDrm = null;
        }
    }

    @Override
    protected void configureCodec(MediaFormat format, boolean isAsync,
            boolean signalEosWithLastFrame, boolean isEncoder, int flags) {
        configureCodecCommon(format, isAsync, signalEosWithLastFrame, isEncoder, flags);
        mCodec.configure(format, mSurface, mCrypto, flags);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "codec configured");
        }
    }

    protected void enqueueInput(int bufferIndex) {
        if (mExtractor.getSampleSize() < 0) {
            enqueueEOS(bufferIndex);
        } else {
            ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
            mExtractor.readSampleData(inputBuffer, 0);
            int size = (int) mExtractor.getSampleSize();
            long pts = mExtractor.getSampleTime();
            int extractorFlags = mExtractor.getSampleFlags();
            MediaCodec.CryptoInfo cryptoInfo = null;
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) {
                cryptoInfo = new MediaCodec.CryptoInfo();
                mExtractor.getSampleCryptoInfo(cryptoInfo);
            }
            int codecFlags = 0;
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
            }
            if (!mExtractor.advance() && mSignalEOSWithLastFrame) {
                codecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mSawInputEOS = true;
            }
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + size + " pts: " + pts
                        + " flags: " + codecFlags);
            }
            if (cryptoInfo != null) {
                mCodec.queueSecureInputBuffer(bufferIndex, 0, cryptoInfo, pts, codecFlags);
            } else {
                mCodec.queueInputBuffer(bufferIndex, 0, size, pts, codecFlags);
            }
            if (size > 0 && (codecFlags & (MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    | MediaCodec.BUFFER_FLAG_PARTIAL_FRAME)) == 0) {
                mOutputBuff.saveInPTS(pts);
                mInputCount++;
            }
        }
    }
}
