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
package android.app.appsearch.testutil;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;

import java.security.MessageDigest;

/**
 * Class to hold utilities for accessing packages.
 */
public class PackageUtil {

    private PackageUtil() {}

    /**
     * Returns the SHA-256 signing certificate of this CTS package.
     *
     * <p>CTS apps are not always signed with the same key, and so we need to obtain it in runtime.
     */
    public static byte[] getSelfPackageSha256Cert(Context context) throws Exception {
        PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        SigningInfo signingInfo = packageInfo.signingInfo;
        if (signingInfo == null || signingInfo.getSigningCertificateHistory() == null) {
            throw new IllegalStateException("Failed to get the signing certificate");
        }
        MessageDigest md = MessageDigest.getInstance("SHA256");
        md.update(signingInfo.getSigningCertificateHistory()[0].toByteArray());
        return md.digest();
    }
}
