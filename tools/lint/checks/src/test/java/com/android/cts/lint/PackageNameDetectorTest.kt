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

package com.android.cts.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class PackageNameDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = PackageNameDetector()

    override fun getIssues(): List<Issue> = listOf(PackageNameDetector.ISSUE)

    @Test
    fun testPackageNameIsInvalid() {
        lint()
            .files(
                xml(
                        "AndroidManifest.xml",
                        """
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="random.android">
</manifest>
"""
                    )
                    .indented()
            )
            .allowMissingSdk()
            .run()
            .expect(
                """
AndroidManifest.xml:2: Warning: random.android does not follow the recommendation for package names in CTS. It should match (com.)?android..*.cts. [InvalidPackageName]
        package="random.android">
                 ~~~~~~~~~~~~~~
0 errors, 1 warnings
"""
                    .trimIndent()
            )
    }

    @Test
    fun testPackageNameIsValid() {
        lint()
            .files(
                xml(
                        "AndroidManifest.xml",
                        """
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="android.my_feature.cts">
</manifest>
"""
                    )
                    .indented()
            )
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun testPackageNameWithComIsValid() {
        lint()
            .files(
                xml(
                        "AndroidManifest.xml",
                        """
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.android.my_other_feature.cts">
</manifest>
"""
                    )
                    .indented()
            )
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun testPackageNameIsMissing() {
        lint()
            .files(
                xml(
                        "AndroidManifest.xml",
                        """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
</manifest>
"""
                    )
                    .indented()
            )
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
