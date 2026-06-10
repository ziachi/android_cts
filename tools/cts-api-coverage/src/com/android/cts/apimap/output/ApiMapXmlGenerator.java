/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.cts.apimap.output;

import com.android.cts.apicommon.ApiClass;
import com.android.cts.apicommon.ApiConstructor;
import com.android.cts.apicommon.ApiCoverage;
import com.android.cts.apicommon.ApiMethod;
import com.android.cts.apicommon.ApiPackage;
import com.android.cts.apicommon.CoverageComparator;
import com.android.cts.apicommon.TestMethodInfo;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A class to generate API map data and API coverage statistics.
 *
 * <p>The element structure is:
 * <api>
 *     <package coveragePercentage="100" name="android.opengl" numCovered="1" numTotal="1">
 *         <class coveragePercentage="100" deprecated="false" name="GLES20"
 *                numCovered="1" numTotal="1">
 *             <method abstract="false" covered="true" deprecated="false" final="false"
 *                     name="glActiveTexture" returnType="void" static="true" visibility="public"
 *                     with="CtsModuleA">
 *                 <parameter type="int"/>
 *                 <covered-by class="ClassA" module="CtsModuleA" package="android.module.cts"
 *                             test="testMethodA"/>
 *             </method>
 *         </class>
 *     </package>
 * </api>
 * <total coveragePercentage="100" numCovered="1" numTotal="1"/>
 */
public final class ApiMapXmlGenerator extends ApiXmlGenerator {

    private static final String TOP_ELEMENT_NAME = "api";
    private static final String STATISTICS_ELEMENT_NAME = "total";

    private final ApiStatistics mStatistics = new ApiStatistics();

    ApiMapXmlGenerator(Document doc) {
        super(doc);
        addTopElement(TOP_ELEMENT_NAME);
        addTopElement(STATISTICS_ELEMENT_NAME);
    }

    @Override
    public void generateData(ApiCoverage apiCoverage) {
        List<ApiPackage> packages = new ArrayList<>(apiCoverage.getPackages());
        packages.sort(new CoverageComparator());
        for (ApiPackage pkg : packages) {
            if (pkg.getTotalMethods() > 0) {
                getTopElement(TOP_ELEMENT_NAME).appendChild(createApiPackageElement(pkg));
            }
        }
        generateApiTotalElement();
    }

    private Element createApiConstructorElement(ApiConstructor constructor) {
        removeDeprecatedApiFromStatistics(constructor.isDeprecated(), constructor.isCovered());
        Element element =
                createElement(
                        "constructor",
                        Map.of(
                                "name", constructor.getName(),
                                "deprecated", constructor.isDeprecated(),
                                "covered", constructor.isCovered(),
                                "with", convertCoveredWithsToString(constructor.getCoveredWith())));
        addParameterTypes(constructor.getParameterTypes(), element);
        addCoveredByElement(constructor.getCoveredTests(), element);
        return element;
    }

    private Element createApiMethodElement(ApiMethod method) {
        removeDeprecatedApiFromStatistics(method.isDeprecated(), method.isCovered());
        Element element =
                createElement(
                        "method",
                        Map.of(
                                "name", method.getName(),
                                "returnType", method.getReturnType(),
                                "deprecated", method.isDeprecated(),
                                "static", method.isStaticMethod(),
                                "final", method.isFinalMethod(),
                                "visibility", method.getVisibility(),
                                "abstract", method.isAbstractMethod(),
                                "covered", method.isCovered(),
                                "with", convertCoveredWithsToString(method.getCoveredWith())));
        addParameterTypes(method.getParameterTypes(), element);
        addCoveredByElement(method.getCoveredTests(), element);
        return element;
    }

    private Element createApiPackageElement(ApiPackage pkg) {
        mStatistics.mTotalMethods += pkg.getTotalMethods();
        mStatistics.mTotalCoveredMethods += pkg.getNumCoveredMethods();
        Element element =
                createElement(
                        "package",
                        Map.of(
                                "name", pkg.getName(),
                                "numCovered", pkg.getNumCoveredMethods(),
                                "numTotal", pkg.getTotalMethods(),
                                "coveragePercentage", Math.round(pkg.getCoveragePercentage())));
        List<ApiClass> classes = new ArrayList<>(pkg.getClasses());
        classes.sort(new CoverageComparator());
        for (ApiClass apiClass : classes) {
            if (apiClass.getTotalMethods() > 0) {
                element.appendChild(createApiClassElement(apiClass));
            }
        }
        return element;
    }

    private Element createApiClassElement(ApiClass apiClass) {
        Element element =
                createElement(
                        "class",
                        Map.of(
                                "name", apiClass.getName(),
                                "numCovered", apiClass.getNumCoveredMethods(),
                                "numTotal", apiClass.getTotalMethods(),
                                "deprecated", apiClass.isDeprecated(),
                                "coveragePercentage",
                                        Math.round(apiClass.getCoveragePercentage())));
        for (ApiConstructor constructor : apiClass.getConstructors()) {
            element.appendChild(createApiConstructorElement(constructor));
        }
        for (ApiMethod method : apiClass.getMethods()) {
            element.appendChild(createApiMethodElement(method));
        }
        return element;
    }

    private void generateApiTotalElement() {
        Element total = getTopElement(STATISTICS_ELEMENT_NAME);
        total.setAttribute("numCovered", String.valueOf(mStatistics.mTotalCoveredMethods));
        total.setAttribute("numTotal", String.valueOf(mStatistics.mTotalMethods));
        total.setAttribute(
                "coveragePercentage",
                String.valueOf(
                        Math.round(
                                (float) mStatistics.mTotalCoveredMethods
                                        / mStatistics.mTotalMethods
                                        * 100.0f)));
    }

    private void addParameterTypes(List<String> parameterTypes, Element parent) {
        for (String parameterType : parameterTypes) {
            parent.appendChild(createElement("parameter", Map.of("type", parameterType)));
        }
    }

    private void addCoveredByElement(List<TestMethodInfo> tests, Element parent) {
        for (TestMethodInfo test : tests) {
            parent.appendChild(
                    createElement(
                            "covered-by",
                            Map.of(
                                    "module", test.moduleName(),
                                    "package", test.packageName(),
                                    "class", test.className(),
                                    "test", test.testName())));
        }
    }

    private String convertCoveredWithsToString(Collection<String> coveredModules) {
        List<String> coveredWiths = new ArrayList<>(coveredModules);
        Collections.sort(coveredWiths);
        return String.join(",", coveredWiths);
    }

    private void removeDeprecatedApiFromStatistics(boolean deprecated, boolean covered) {
        if (deprecated) {
            mStatistics.mTotalMethods--;
            if (covered) {
                mStatistics.mTotalCoveredMethods--;
            }
        }
    }

    static final class ApiStatistics {
        int mTotalMethods = 0;
        int mTotalCoveredMethods = 0;
    }
}
