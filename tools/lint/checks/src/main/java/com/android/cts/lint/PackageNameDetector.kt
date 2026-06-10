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

/** Lint Detector that package names for CTS test apps. */
package com.android.cts.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class PackageNameDetector : Detector(), XmlScanner {

    override fun getApplicableElements() = listOf("manifest")

    override fun visitElement(context: XmlContext, element: Element) {
        val packageNode = element.getAttributeNode("package")
        val packageName = packageNode?.value
        if (packageName != null && !PACKAGE_NAME_REGEX.matches(packageName)) {
            val location = context.getValueLocation(packageNode)
            val incident =
                Incident(context, ISSUE)
                    .location(location)
                    .message(
                        "$packageName does not follow the recommendation for package names " +
                        "in CTS. It should match ${PACKAGE_NAME_REGEX.pattern}."
                    )
            context.report(incident)
        }
    }

    companion object {
        val PACKAGE_NAME_REGEX = Regex("(com\\.)?android\\..*\\.cts")
        @JvmField
        val ISSUE: Issue =
            Issue.create(
                id = "InvalidPackageName",
                briefDescription =
                    "The package name for a CTS app should match ${PACKAGE_NAME_REGEX.pattern}",
                explanation =
                    """
         All new apps in CTS should have a package name that matches ${PACKAGE_NAME_REGEX.pattern}. For instance:
             android.my_feature.cts
             android.my_feature.other_package.cts
             com.android.my_feature.another_subpackage.cts
                    """
                        .trimIndent(),
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        PackageNameDetector::class.java,
                        Scope.MANIFEST_SCOPE
                    ),
            )
    }
}
