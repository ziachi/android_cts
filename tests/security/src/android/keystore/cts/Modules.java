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

package android.keystore.cts;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Set;

import java.io.IOException;
import java.security.cert.CertificateParsingException;
import java.util.ArrayList;
import java.util.List;

public class Modules {
    // The ASN.1 schema for a module matches that of `AttestationPackageInfo`, so re-use the class.
    private final List<AttestationPackageInfo> mModules;

    public Modules(byte[] bytes) throws CertificateParsingException {
        try (ASN1InputStream asn1InputStream = new ASN1InputStream(bytes)) {
            ASN1Encodable asn1Encodable = asn1InputStream.readObject();
            if (!(asn1Encodable instanceof ASN1Set)) {
                throw new CertificateParsingException(
                        "Expected set for Modules, found " + asn1Encodable.getClass().getName());
            }
            ASN1Set set = (ASN1Set) asn1Encodable;
            mModules = new ArrayList<AttestationPackageInfo>();
            for (ASN1Encodable e : set) {
                mModules.add(new AttestationPackageInfo(e));
            }
        } catch (IOException e) {
            throw new CertificateParsingException("Failed to parse SET OF Module", e);
        }
    }

    public List<AttestationPackageInfo> getModules() {
        return mModules;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Modules:");
        int noOfModules = mModules.size();
        int i = 1;
        for (AttestationPackageInfo module : mModules) {
            sb.append("\n### Module " + i + "/" + noOfModules + " ###\n");
            sb.append(module);
            i += 1;
        }
        return sb.toString();
    }
}
