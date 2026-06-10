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

package com.android.cts.apimap;

import com.android.cts.apicommon.ApiClass;
import com.android.cts.apicommon.ApiCoverage;
import com.android.cts.apicommon.ApiPackage;
import com.android.cts.apicommon.TestMethodInfo;
import com.android.cts.ctsprofiles.ClassProfile;
import com.android.cts.ctsprofiles.MethodProfile;
import com.android.cts.ctsprofiles.ModuleProfile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/** A class for collecting APIs covered by a CTS module. */
public class CallGraphManager {

    // Cache API calls for each CTS method.
    private final Map<String, CoveredApiCache> mCoveredApiCaches = new HashMap<>();

    private final ModuleProfile mModule;

    /** Cache the covered API list for a CTS method. */
    static class CoveredApiCache {

        private final Map<String, MethodProfile> mApiConstructors = new HashMap<>();

        private final Map<String, MethodProfile> mApiMethods = new HashMap<>();

        public void mergeApis(CoveredApiCache apis) {
            addMethods(apis.getApiMethods());
            addConstructors(apis.getApiConstructors());
        }

        public void addMethods(Map<String, MethodProfile> methods) {
            mApiMethods.putAll(methods);
        }

        public void addConstructors(Map<String, MethodProfile> constructors) {
            mApiConstructors.putAll(constructors);
        }

        public Map<String, MethodProfile> getApiConstructors() {
            return mApiConstructors;
        }

        public Map<String, MethodProfile> getApiMethods() {
            return mApiMethods;
        }
    }

    public CallGraphManager(ModuleProfile moduleProfile) {
        mModule = moduleProfile;
    }

    public ModuleProfile getModule() {
        return mModule;
    }

    /**
     * Maps detected APIs to CTS test methods and marks them as covered by this CTS module.
     */
    public void resolveCoveredApis(ApiCoverage apiCoverage) {
        for (ClassProfile classProfile : mModule.getClasses()) {
            if (!classProfile.isNonAbstractTestClass()) {
                continue;
            }
            for (MethodProfile methodProfile : classProfile.getTestMethods().values()) {
                TarJan tarjan = new TarJan(methodProfile, mCoveredApiCaches.keySet());
                Stack<Integer> stack = new Stack<>();
                Set<Integer> visitedComponents = new HashSet<>();
                String methodSignature = methodProfile.getMethodSignatureWithClass();
                stack.add(tarjan.getComponentID(methodSignature));
                visitedComponents.add(tarjan.getComponentID(methodSignature));
                // Do recursive search for API calls.
                resolveMethodCoveredApis(stack, visitedComponents, tarjan);
                markCoveredApisWithCaller(methodProfile, apiCoverage);
            }
        }
        markCoveredApisWithoutCaller(apiCoverage);
    }

    /** Resolves cases that methods are overriding abstract API methods. */
    public void resolveOverriddenAbstractApiMethods(ApiCoverage apiCoverage) {
        for (ClassProfile classProfile : mModule.getClasses()) {
            classProfile.resolveOverriddenAbstractApiMethods(apiCoverage);
        }
    }

    /** Resolves cases that methods are inherited from super classes. */
    public void resolveInheritedMethods(ApiCoverage apiCoverage) {
        for (ClassProfile classProfile : mModule.getClasses()) {
            classProfile.resolveInheritedMethods(apiCoverage);
        }
    }

    /** Resolve cases that methods are inherited from an API method. */
    public void resolveInheritedApiMethods(ApiCoverage apiCoverage) {
        for (ClassProfile classProfile : mModule.getClasses()) {
            classProfile.resolveInheritedApiMethods(apiCoverage);
        }
    }

    /** Collects covered APIs for a test method via memorized search. */
    private CoveredApiCache resolveMethodCoveredApis(
            Stack<Integer> stack,
            Set<Integer> visitedComponents,
            TarJan tarjan) {
        List<MethodProfile> methods = tarjan.getComponent(stack.peek());
        String methodSignature = methods.getFirst().getMethodSignatureWithClass();
        CoveredApiCache coveredApis = mCoveredApiCaches.get(methodSignature);
        if (coveredApis != null) {
            return coveredApis;
        }
        coveredApis = new CoveredApiCache();
        for (MethodProfile method: methods) {
            coveredApis.addMethods(method.getApiMethodCalls());
            coveredApis.addConstructors(method.getApiConstructorCalls());
            coveredApis.addMethods(method.getOverriddenApiMethods());
        }
        for (MethodProfile method: methods) {
            for (MethodProfile methodCall : method.getCommonMethodCalls().values()) {
                String methodCallSignature = methodCall.getMethodSignatureWithClass();
                int componentID = tarjan.getComponentID(methodCallSignature);
                if (visitedComponents.contains(componentID)) {
                    continue;
                }
                visitedComponents.add(componentID);
                stack.add(componentID);
                CoveredApiCache apis = resolveMethodCoveredApis(stack, visitedComponents, tarjan);
                coveredApis.mergeApis(apis);
                stack.pop();
            }
        }
        for (MethodProfile method: methods) {
            mCoveredApiCaches.put(method.getMethodSignatureWithClass(), coveredApis);
        }
        return coveredApis;
    }

    /** Searches for the API class based on the given package name and class name. */
    private ApiClass getApiClass(
            String packageName, String className, ApiCoverage apiCoverage) {
        ApiPackage apiPackage = apiCoverage.getPackage(packageName);
        if (apiPackage != null) {
            return apiPackage.getClass(className);
        }
        return null;
    }

    /** Marks that APIs are covered by this CTS module. */
    private void markCoveredApisWithoutCaller(ApiCoverage apiCoverage) {
        for (ClassProfile classProfile: mModule.getClasses()) {
            if (!classProfile.isApiClass()) {
                continue;
            }
            ApiClass apiClass = getApiClass(
                    classProfile.getPackageName(),
                    classProfile.getClassName(),
                    apiCoverage
            );
            if (apiClass == null) {
                continue;
            }
            for (MethodProfile methodProfile: classProfile.getMethods().values()) {
                if (methodProfile.getMethodName().equals("<init>")) {
                    apiClass.markConstructorCovered(
                            methodProfile.getMethodParams(),
                            mModule.getModuleName()
                    );
                } else {
                    apiClass.markMethodCovered(
                            methodProfile.getMethodName(),
                            methodProfile.getMethodParams(),
                            mModule.getModuleName()
                    );
                }
            }
        }
    }

    /** Marks that APIs are called by the given CTS test method. */
    private void markCoveredApisWithCaller(MethodProfile methodProfile, ApiCoverage apiCoverage) {
        String methodSignature = methodProfile.getMethodSignatureWithClass();
        TestMethodInfo testMethodInfo = new TestMethodInfo(
                mModule.getModuleName(),
                methodProfile.getPackageName(),
                methodProfile.getClassName(),
                methodProfile.getMethodName(),
                String.format("[%s] %s", mModule.getModuleName(), methodSignature)
        );
        CoveredApiCache apiCache = mCoveredApiCaches.get(methodSignature);
        if (apiCache == null) {
            return;
        }
        for (MethodProfile apiConstructor : apiCache.getApiConstructors().values()) {
            ApiClass apiClass = getApiClass(
                    apiConstructor.getPackageName(),
                    apiConstructor.getClassName(),
                    apiCoverage
            );
            if (apiClass != null) {
                apiClass.markConstructorCoveredTest(
                        apiConstructor.getMethodParams(),
                        testMethodInfo
                );
            }
        }
        for (MethodProfile apiMethod : apiCache.getApiMethods().values()) {
            ApiClass apiClass = getApiClass(
                    apiMethod.getPackageName(), apiMethod.getClassName(), apiCoverage);
            if (apiClass != null) {
                apiClass.markMethodCoveredTest(
                        apiMethod.getMethodName(),
                        apiMethod.getMethodParams(),
                        testMethodInfo
                );
            }
        }
    }
}
