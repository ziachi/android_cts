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

import com.android.cts.ctsprofiles.ClassProfile;
import com.android.cts.ctsprofiles.MethodProfile;
import com.android.cts.ctsprofiles.ModuleProfile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Map;
import java.util.Set;

/**
 * A class to generate an XML element containing xTS annotations marked by xTS tests.
 *
 * <p>The element structure is:
 * <xts-annotation>
 *     <test-module name="ModuleA">
 *         <test-class name="ClassA" package="android.cts.module1">
 *             <test-method name="testMethodA">
 *                 <annotation name="ApiTest:apis" values="api_value"/>
 *             </test-method>
 *         </test-class>
 *     </test-module>
 *     <test-module name="ModuleB">
 *         <test-class name="ClassB" package="android.cts.module2">
 *             <test-method name="testMethodB">
 *                 <annotation name="CddTest:requirements" values="cdd_value"/>
 *             </test-method>
 *         </test-class>
 *     </test-module>
 * </xts-annotation>
 */
public final class XtsAnnotationXmlGenerator extends XtsXmlGenerator {

    private static final String TOP_ELEMENT_NAME = "xts-annotation";

    XtsAnnotationXmlGenerator(Document doc) {
        super(doc);
        addTopElement(TOP_ELEMENT_NAME);
    }

    @Override
    public void generateData(ModuleProfile module) {
        Element moduleElement =
                createElement("test-module", Map.of("name", module.getModuleName()));
        for (ClassProfile classProfile : module.getClasses()) {
            if (!classProfile.isNonAbstractTestClass()) {
                continue;
            }
            Element classElement = createTestClassElement(classProfile);
            if (classElement.hasChildNodes()) {
                moduleElement.appendChild(classElement);
            }
        }
        if (moduleElement.hasChildNodes()) {
            getTopElement(TOP_ELEMENT_NAME).appendChild(moduleElement);
        }
    }

    private Element createTestClassElement(ClassProfile classProfile) {
        Element classElement =
                createElement(
                        "test-class",
                        Map.of(
                                "package", classProfile.getPackageName(),
                                "name", classProfile.getClassName()));
        for (Map.Entry<String, Set<String>> xtsAnnotation :
                classProfile.annotationManagement.getTestMetadata().entrySet()) {
            classElement.appendChild(
                    createXtsAnnotationElement(xtsAnnotation.getKey(), xtsAnnotation.getValue()));
        }
        for (MethodProfile testMethod : classProfile.getTestMethods().values()) {
            Element methodElement = createTestMethodElement(testMethod);
            if (methodElement.hasChildNodes()) {
                classElement.appendChild(methodElement);
            }
        }
        return classElement;
    }

    private Element createTestMethodElement(MethodProfile methodProfile) {
        Element methodElement =
                createElement("test-method", Map.of("name", methodProfile.getMethodName()));
        for (Map.Entry<String, Set<String>> xtsAnnotation :
                methodProfile.annotationManagement.getTestMetadata().entrySet()) {
            methodElement.appendChild(
                    createXtsAnnotationElement(xtsAnnotation.getKey(), xtsAnnotation.getValue()));
        }
        return methodElement;
    }

    private Element createXtsAnnotationElement(String name, Set<String> values) {
        return createElement(
                "annotation", Map.of("name", name, "values", String.join(";", values)));
    }
}
