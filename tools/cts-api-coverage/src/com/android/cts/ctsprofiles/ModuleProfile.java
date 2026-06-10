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

package com.android.cts.ctsprofiles;

import com.android.cts.apicommon.ApiClass;
import com.android.cts.apicommon.ApiCoverage;
import com.android.cts.apicommon.ApiPackage;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Representation of a CTS module. */
public class ModuleProfile {

    private final Map<String, ClassProfile> mClasses = new HashMap<>();

    private final String mName;

    public ModuleProfile(String name) {
        mName = name;
    }

    /** Creates a class packaged in the module. */
    public ClassProfile getOrCreateClass(
            String packageName, String className, ApiCoverage apiCoverage) {
        String classSignature = Utils.getClassSignature(packageName, className);
        ApiPackage apiPackage = apiCoverage.getPackage(packageName);
        ApiClass apiClass = apiPackage != null ? apiPackage.getClass(className) : null;
        if (!mClasses.containsKey(classSignature)) {
            mClasses.put(
                    classSignature,
                    new ClassProfile(mName, packageName, className, apiClass != null)
            );
        }
        return mClasses.get(classSignature);
    }

    public String getModuleName() {
        return mName;
    }

    public Collection<ClassProfile> getClasses() {
        return Collections.unmodifiableCollection(mClasses.values());
    }
}
