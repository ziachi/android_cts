/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.cts.apicommon;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Representation of the entire API containing packages. */
public class ApiCoverage {

    private final Map<String, ApiPackage> mPackages = new ConcurrentHashMap<>();

    public void addPackage(ApiPackage pkg) {
        mPackages.putIfAbsent(pkg.getName(), pkg);
    }

    public ApiPackage getPackage(String name) {
        return mPackages.getOrDefault(name, null);
    }

    /** Finds the given API class. */
    public ApiClass getClass(String packageName, String className) {
        ApiPackage apiPackage = getPackage(packageName);
        return apiPackage == null ? null : apiPackage.getClass(className);
    }

    /** Finds the given API method. */
    public ApiMethod getMethod(
            String packageName, String className, String methodName, List<String> methodParams) {
        ApiClass apiClass = getClass(packageName, className);
        if (apiClass == null) {
            return null;
        }
        Optional<ApiMethod> apiMethod = apiClass.getMethod(methodName, methodParams);
        return apiMethod.orElse(null);
    }

    public Collection<ApiPackage> getPackages() {
        return Collections.unmodifiableCollection(mPackages.values());
    }

    /** Iterate through all packages and update all classes to include its superclass */
    public void resolveSuperClasses() {
        for (Map.Entry<String, ApiPackage> entry : mPackages.entrySet()) {
            ApiPackage pkg = entry.getValue();
            pkg.resolveSuperClasses(mPackages);
        }
    }
}
